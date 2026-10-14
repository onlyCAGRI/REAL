import SwiftUI
import UserNotifications

/// ReminderNotifications.kt + ReminderScheduler.kt on iOS.
/// - Title = custom category name (else "REAL"), body = the card text, subtitle = "Önemli" / repeat name.
/// - Due notifications carry "Tamamla" and "10 dk ertele"; the immediate one only "Tamamla".
/// - A card without date/time and with "Hemen bildir" on posts immediately (canShowImmediateNotification).
/// - Repeating cards pre-schedule their next occurrences (iOS cannot re-arm in the background like Android alarms).
/// - Category notifications show the Android category glyph on its accent colour as the attachment (large icon).
enum NotificationScheduler {
    static var enabled = true
    static let dueCategory = "REAL_REMINDER_DUE"
    static let immediateCategory = "REAL_REMINDER"
    static let completeAction = "REAL_COMPLETE"
    static let snoozeAction = "REAL_SNOOZE_10"
    static let snoozeMillis: EpochMillis = 10 * 60 * 1000
    private static let repeatsAhead = 8

    /// Category names/colours/icons for titles and attachments (kept in sync by ReminderStore).
    static var categories: [CustomCategory] = []

    static func registerCategories() {
        let complete = UNNotificationAction(identifier: completeAction, title: "Tamamla", options: [])
        let snooze = UNNotificationAction(identifier: snoozeAction, title: "10 dk ertele", options: [])
        let due = UNNotificationCategory(identifier: dueCategory, actions: [complete, snooze], intentIdentifiers: [], options: [])
        let immediate = UNNotificationCategory(identifier: immediateCategory, actions: [complete], intentIdentifiers: [], options: [])
        UNUserNotificationCenter.current().setNotificationCategories([due, immediate])
    }

    static func requestAuthorization() {
        guard enabled else { return }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    private static func content(for item: ReminderItem, alerting: Bool) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        let category = item.customCategoryId.flatMap { id in categories.first { $0.id == id } }
        content.title = category?.name ?? "REAL"
        if item.isChecklist {
            let open = item.checklistItems.filter { !$0.isCompleted }.count
            content.body = item.text.isEmpty ? "\(open) madde kaldı" : item.text + " · \(open) madde kaldı"
        } else {
            content.body = item.text
        }
        if item.isImportant { content.subtitle = "Önemli" }
        else if item.hasRepeat { content.subtitle = item.repeatRule.displayName }
        content.sound = alerting ? .default : nil
        content.userInfo = ["reminderId": item.id]
        content.categoryIdentifier = alerting ? dueCategory : immediateCategory
        content.threadIdentifier = category.map { "category-\($0.id)" } ?? "real"
        content.interruptionLevel = item.isImportant ? .timeSensitive : .active
        if let category, let attachment = categoryAttachment(category) { content.attachments = [attachment] }
        return content
    }

    static func schedule(_ item: ReminderItem) {
        guard enabled else { return }
        cancel(item.id)
        guard !item.isCompleted, let alert = item.alertAt else { return }
        let now = EpochMillis.now
        var times: [EpochMillis] = []
        if alert > now { times.append(alert) }
        if item.hasRepeat {
            var cursor = max(alert, now)
            for _ in 0..<repeatsAhead where times.count < repeatsAhead {
                guard let next = Recurrence.nextRepeat(base: item.anchorAlertAt ?? alert, rule: item.repeatRule,
                                                       weekdaysMask: item.repeatWeekdaysMask, untilEpochDay: item.repeatUntilEpochDay,
                                                       after: cursor, skipped: item.repeatSkippedEpochDays) else { break }
                if next > now && !times.contains(next) { times.append(next) }
                cursor = next
            }
        }
        let content = content(for: item, alerting: true)
        for (i, time) in times.enumerated() {
            let comps = Calendar.real.dateComponents([.year, .month, .day, .hour, .minute], from: time.date)
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
            UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: identifier(item.id, i), content: content, trigger: trigger))
            // "Erken uyarı": a heads-up before each occurrence ("15 dk sonra · …").
            if let minutes = item.earlyAlertMinutes, (1...ReminderItem.maxEarlyAlertMinutes).contains(minutes) {
                let early = time - EpochMillis(minutes) * 60_000
                guard early > now else { continue }
                let heads = self.content(for: item, alerting: false)
                heads.body = earlyLead(minutes) + " · " + heads.body
                heads.subtitle = "Erken uyarı"
                heads.sound = .default
                let c = Calendar.real.dateComponents([.year, .month, .day, .hour, .minute], from: early.date)
                UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: earlyIdentifier(item.id, i), content: heads,
                    trigger: UNCalendarNotificationTrigger(dateMatching: c, repeats: false)))
            }
        }
    }

    /// "15 dk sonra", "1 saat sonra", "2 gün sonra".
    static func earlyLead(_ minutes: Int) -> String {
        if minutes >= 1440 && minutes % 1440 == 0 { return "\(minutes / 1440) gün sonra" }
        if minutes >= 60 && minutes % 60 == 0 { return "\(minutes / 60) saat sonra" }
        return "\(minutes) dk sonra"
    }
    private static func earlyIdentifier(_ id: Int64, _ index: Int) -> String { "reminder-\(id)-early-\(index)" }

    /// showImmediate: a fresh card with no date posts right away when "Hemen bildir" is on.
    static func postImmediateIfNeeded(_ item: ReminderItem) {
        guard enabled, !item.isCompleted, item.alertAt == nil, item.plannedDateEpochDay == nil,
              item.sendImmediateNotification, !item.isChecklist else { return }
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "reminder-\(item.id)-now",
                                                                    content: content(for: item, alerting: false), trigger: nil))
    }

    private static func identifier(_ id: Int64, _ index: Int) -> String { index == 0 ? "reminder-\(id)" : "reminder-\(id)-\(index)" }

    static func cancel(_ id: Int64) {
        guard enabled else { return }
        let ids = (0..<repeatsAhead).map { identifier(id, $0) } + (0..<repeatsAhead).map { earlyIdentifier(id, $0) }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ids)
    }

    /// Delivered banners of a completed / deleted card are removed too (ReminderNotifications.cancel).
    static func removeDelivered(_ id: Int64) {
        guard enabled else { return }
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: (0..<repeatsAhead).map { identifier(id, $0) } + ["reminder-\(id)-now"])
    }

    static func reschedule(_ items: [ReminderItem]) {
        guard enabled else { return }
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
        items.forEach(schedule)
    }

    // MARK: Category large icon

    private static func categoryAttachment(_ category: CustomCategory) -> UNNotificationAttachment? {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("notification-icons", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let url = dir.appendingPathComponent("cat-\(category.id)-\(category.iconKey)-\(category.accentStart).png")
        if !FileManager.default.fileExists(atPath: url.path) {
            guard let data = renderIcon(category) else { return nil }
            try? data.write(to: url)
        }
        // Attachments are moved into the notification store, so hand iOS a copy.
        let copy = dir.appendingPathComponent(UUID().uuidString + ".png")
        guard (try? FileManager.default.copyItem(at: url, to: copy)) != nil else { return nil }
        return try? UNNotificationAttachment(identifier: "category", url: copy, options: nil)
    }

    private static func renderIcon(_ category: CustomCategory) -> Data? {
        if Thread.isMainThread { return MainActor.assumeIsolated { renderIconOnMain(category) } }
        return DispatchQueue.main.sync { MainActor.assumeIsolated { renderIconOnMain(category) } }
    }

    @MainActor
    private static func renderIconOnMain(_ category: CustomCategory) -> Data? {
        let accent = paletteColor(category.accentStart) ?? Color(argb: 0xFF4558C8)
        let dark = accent.luminance > 0.55
        let view = ZStack {
            Circle().fill(accent)
            CategoryIconGlyph(icon: category.icon, color: dark ? Color(argb: 0xFF111111) : .white, size: 56)
        }
        .frame(width: 96, height: 96)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 2
        return renderer.uiImage?.pngData()
    }
}

extension Notification.Name {
    /// Notification tap → open that card's detail.
    static let realOpenReminder = Notification.Name("REALOpenReminder")
}
