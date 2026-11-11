import SwiftUI
import UIKit
import UserNotifications
import CoreLocation

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
    static let eventStartCategory = "REAL_EVENT_START"
    static let eventEndCategory = "REAL_EVENT_END"
    static let completeAction = "REAL_COMPLETE"
    static let snoozeAction = "REAL_SNOOZE_10"
    static let extendAction = "REAL_EXTEND_15"
    static let snoozeMillis: EpochMillis = 10 * 60 * 1000
    private static let repeatsAhead = 8

    /// Category names/colours/icons for titles and attachments (kept in sync by ReminderStore).
    static var categories: [CustomCategory] = []

    static func registerCategories() {
        let complete = UNNotificationAction(identifier: completeAction, title: t("Tamamla"), options: [])
        let completeEvent = UNNotificationAction(identifier: completeAction, title: t("Tamamladım"), options: [])
        let snooze = UNNotificationAction(identifier: snoozeAction, title: t("10 dk ertele"), options: [])
        let extend = UNNotificationAction(identifier: extendAction, title: t("+15 dk"), options: [])
        let due = UNNotificationCategory(identifier: dueCategory, actions: [complete, snooze], intentIdentifiers: [], options: [])
        let immediate = UNNotificationCategory(identifier: immediateCategory, actions: [complete], intentIdentifiers: [], options: [])
        // Started: only "10 dk ertele"; finishing is asked again at the end. Ended: "Tamamlandı" / "+15 dk uzat".
        let eventStart = UNNotificationCategory(identifier: eventStartCategory, actions: [snooze], intentIdentifiers: [], options: [])
        let eventEnd = UNNotificationCategory(identifier: eventEndCategory, actions: [completeEvent, extend], intentIdentifiers: [], options: [])
        UNUserNotificationCenter.current().setNotificationCategories([due, immediate, eventStart, eventEnd])
    }

    /// Off while REAL re-adds everything on its own (launch, coming to the front): only a card being set asks.
    private static var asking = true
    /// "Daha sonra" / "Vazgeç" was tapped: no more sheets until the next launch.
    private static var answeredThisLaunch = false
    /// A sheet is up: a second call in the same moment (schedule + the composer both ask) waits it out instead of
    /// counting as "Daha sonra".
    private static var prompting = false

    /// Asks once; requests added before the answer are refused by iOS, so a grant re-adds every card's notifications.
    /// Refused earlier: iOS never asks again, so the sheet sends the person to REAL's notification settings.
    static func requestAuthorization() {
        guard enabled, asking, !answeredThisLaunch else { return }
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            let status = settings.authorizationStatus
            guard status == .notDetermined || status == .denied else { return }
            Task { @MainActor in
                guard !answeredThisLaunch, !prompting else { return }
                prompting = true
                defer { prompting = false }
                let ok = await PermissionPrompt.ask(status == .denied ? .notificationsDenied : .notifications)
                guard ok else { answeredThisLaunch = true; return }
                if status == .denied {
                    PermissionPrompt.openNotificationSettings()
                    return
                }
                await askSystem()
            }
        }
    }

    /// Settings "İzni aç": the system question when it was never asked, otherwise REAL's page in iOS Settings.
    @MainActor
    static func allowFromSettings() async {
        guard enabled else { return }
        if await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .notDetermined {
            await askSystem()
        } else {
            PermissionPrompt.openNotificationSettings()
        }
    }

    /// iOS's own question; a grant re-adds everything and posts the "Hemen bildir" that was refused a moment ago.
    @MainActor
    private static func askSystem() async {
        guard (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) == true,
              let store = ReminderStore.current else { return }
        reschedule(store.reminders)
        store.reminders.filter { EpochMillis.now - $0.createdAt < 5 * 60_000 }.forEach(postImmediateIfNeeded)
    }

    private static func content(for item: ReminderItem, alerting: Bool) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        let category = item.customCategoryId.flatMap { id in categories.first { $0.id == id } }
        let text = item.text.trimmingCharacters(in: .whitespacesAndNewlines)
        if item.isChecklist {
            let open = item.checklistItems.filter { !$0.isCompleted }.count
            if text.isEmpty {
                content.title = category?.name ?? "REAL"
                content.body = t("{0} madde kaldı", open)
            } else if hasShortText(item) {
                content.title = text
                content.body = t("{0} madde kaldı", open)
            } else {
                content.title = category?.name ?? ""
                content.body = t("{0} · {1} madde kaldı", item.text, open)
            }
        } else {
            if text.isEmpty {
                content.title = category?.name ?? "REAL"
                content.body = ""
            } else if hasShortText(item) {
                content.title = text
                content.body = category?.name ?? ""
            } else {
                content.title = category?.name ?? ""
                content.body = item.text
            }
        }
        if item.isImportant { content.subtitle = t("Önemli") }
        else if item.hasRepeat { content.subtitle = item.repeatRule.displayName }
        content.sound = alerting ? .default : nil
        content.userInfo = ["reminderId": item.id]
        content.categoryIdentifier = alerting ? dueCategory : immediateCategory
        content.threadIdentifier = category.map { "category-\($0.id)" } ?? "real"
        content.interruptionLevel = item.isImportant ? .timeSensitive : .active
        content.attachments = notificationAttachments(for: item, category: category)
        return content
    }

    private static func hasShortText(_ item: ReminderItem) -> Bool {
        let text = item.text.trimmingCharacters(in: .whitespacesAndNewlines)
        return !text.isEmpty && text.count <= 45 && !text.contains(where: { $0.isNewline })
    }

    static func schedule(_ item: ReminderItem) {
        guard enabled else { return }
        // An edited alarm card keeps its snooze; a completed one, or one that is no longer an alarm, drops it.
        cancel(item.id, alarms: touchingAlarms, keepSnooze: item.isAlarm && !item.isCompleted && item.alertAt != nil)
        DepartureReminder.plan(item)
        if !item.isCompleted, item.alertAt != nil || item.locationTrigger != nil { requestAuthorization() }
        if !item.isCompleted, let place = item.locationTrigger { schedulePlace(item, place) }
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
        if asking {
            let clock = DateFormatter()
            clock.dateFormat = "dd.MM HH:mm"
            lastPlan = "Son kart: \(times.count) zaman" + (times.first.map { " · " + clock.string(from: $0.date) } ?? "")
                + (item.isAlarm ? " · alarm" : "")
        }
        // Alarm card: a system alarm (iOS 26+, allowed) replaces the due notification; otherwise it is time-sensitive.
        var ringsAsAlarm = false
        if item.isAlarm, #available(iOS 26.0, *), RealAlarmKit.authorized {
            if touchingAlarms { RealAlarmKit.schedule(item, times: times, categories: categories) }
            ringsAsAlarm = true
        }
        for (i, time) in times.enumerated() {
            if !ringsAsAlarm {
                // Fresh content per occurrence: iOS moves an attachment's file away on add, so a shared one fails after the first.
                let content = content(for: item, alerting: true)
                if item.isAlarm { content.interruptionLevel = .timeSensitive }
                // An event with an end that just started: "Başladı", and only "10 dk ertele"; finishing is asked at the end.
                if item.eventEndAt != nil {
                    content.categoryIdentifier = eventStartCategory
                    content.subtitle = t("Başladı")
                }
                post(UNNotificationRequest(identifier: identifier(item.id, i), content: content, trigger: calendarTrigger(time.date)))
            }
            // "Erken uyarı": a heads-up before each occurrence ("15 dk sonra · …").
            if let minutes = item.earlyAlertMinutes, (1...ReminderItem.maxEarlyAlertMinutes).contains(minutes) {
                let early = time - EpochMillis(minutes) * 60_000
                guard early > now else { continue }
                let heads = self.content(for: item, alerting: false)
                heads.body = earlyLead(minutes) + " · " + heads.body
                heads.subtitle = t("Erken uyarı")
                heads.sound = .default
                post(UNNotificationRequest(identifier: earlyIdentifier(item.id, i), content: heads, trigger: calendarTrigger(early.date)))
            }
        }
        // "Bitti mi?": once when the event's end arrives, with "Tamamlandı" / "+15 dk uzat".
        if let end = item.eventEndAt, end > now {
            let content = content(for: item, alerting: true)
            content.body = t("Süresi doldu")
            content.categoryIdentifier = eventEndCategory
            post(UNNotificationRequest(identifier: endIdentifier(item.id), content: content, trigger: calendarTrigger(end.date)))
        }
    }

    /// What iOS refused last and what the last card set by hand planned, for "Bildirim testi".
    private static var lastError: String?
    private static var lastPlan: String?

    /// Every request goes through here so a refusal is not lost silently.
    private static func post(_ request: UNNotificationRequest) {
        UNUserNotificationCenter.current().add(request) { error in
            if let error { lastError = "\(request.identifier): \(error.localizedDescription)" }
        }
    }

    /// Settings "Bildirim testi": one notification in 10 s, and what iOS holds right now ("İzin açık · 12 bekliyor").
    static func sendTest() async -> String {
        let center = UNUserNotificationCenter.current()
        let status = await center.notificationSettings().authorizationStatus
        guard status == .authorized || status == .provisional else {
            return status == .denied ? t("İzin kapalı") : t("İzin sorulmadı")
        }
        let content = UNMutableNotificationContent()
        content.title = "REAL"
        content.body = t("Deneme bildirimi")
        content.sound = .default
        do {
            try await center.add(UNNotificationRequest(identifier: "real-test", content: content,
                                                       trigger: UNTimeIntervalNotificationTrigger(timeInterval: 10, repeats: false)))
        } catch {
            return t("Kurulamadı: {0}", error.localizedDescription)
        }
        let requests = await center.pendingNotificationRequests().filter { $0.identifier != "real-test" }
        let pending = requests.count
        // When iOS itself will fire the soonest one, in REAL's clock: shows a time-zone mismatch at a glance.
        let clock = DateFormatter()
        clock.dateFormat = "dd.MM HH:mm"
        let next = requests.compactMap { $0.trigger as? UNCalendarNotificationTrigger }.compactMap { $0.nextTriggerDate() }.min()
            .map { "iOS: " + clock.string(from: $0) }
        return ([t("İzin açık · {0} bekliyor", pending)] + [next, lastPlan, lastError.map { "Hata: " + $0 }].compactMap { $0 })
            .joined(separator: "\n")
    }

    /// The wall-clock time carries REAL's time zone: without it iOS reads "23:54" in the system's zone, which can
    /// differ from the app's (Appetize gives the app the viewer's zone, the simulator keeps its own: 2 hours late).
    private static func calendarTrigger(_ date: Date) -> UNCalendarNotificationTrigger {
        var comps = Calendar.real.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        comps.calendar = Calendar.real
        comps.timeZone = Calendar.real.timeZone
        return UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
    }

    /// "15 dk sonra", "1 saat sonra", "2 gün sonra".
    static func earlyLead(_ minutes: Int) -> String {
        if minutes >= 1440 && minutes % 1440 == 0 { return t("{0} gün sonra", minutes / 1440) }
        if minutes >= 60 && minutes % 60 == 0 { return t("{0} saat sonra", minutes / 60) }
        return t("{0} dk sonra", minutes)
    }
    private static func earlyIdentifier(_ id: Int64, _ index: Int) -> String { "reminder-\(id)-early-\(index)" }

    /// "Konuma varınca hatırlat": iOS watches the region itself (only "while using" permission needed) and posts once.
    /// REAL never reads the position; the trigger is cleared when the notification is seen (clearFiredPlaces).
    private static func schedulePlace(_ item: ReminderItem, _ place: LocationTrigger) {
        let region = CLCircularRegion(center: CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude),
                                      radius: CLLocationDistance(place.radiusMeters), identifier: placeIdentifier(item.id))
        region.notifyOnEntry = !place.onExit
        region.notifyOnExit = place.onExit
        let content = content(for: item, alerting: true)
        content.subtitle = (place.onExit ? t("Ayrıldın · ") : t("Vardın · ")) + place.placeName
        // The route on the arrival screen starts where the phone is now.
        if !place.onExit { ArrivalOrigins.remember(item.id, placeLat: place.latitude, placeLng: place.longitude) }
        // The trigger is cleared once it fires, so the tap reads the place from here (LocationArrivalFocusScreen).
        content.userInfo = ArrivalFocusContext(id: item.id, placeName: place.placeName, onExit: place.onExit,
                                               latitude: place.latitude, longitude: place.longitude).userInfo
        post(UNNotificationRequest(identifier: placeIdentifier(item.id), content: content,
            trigger: UNLocationNotificationTrigger(region: region, repeats: false)))
    }
    static func placeIdentifier(_ id: Int64) -> String { "reminder-\(id)-place" }

    /// Place notifications that already fired: their cards drop the trigger (it is one-time, like Android's geofence).
    /// Shown ones are in Notification Center; one swiped away there is only missing from the pending list.
    static func firedPlaceIds(_ items: [ReminderItem]) async -> Set<Int64> {
        guard enabled else { return [] }
        let center = UNUserNotificationCenter.current()
        var fired = Set(await center.deliveredNotifications().compactMap { n in
            n.request.identifier.hasSuffix("-place") ? n.request.content.userInfo["reminderId"] as? Int64 : nil
        })
        guard await center.notificationSettings().authorizationStatus == .authorized,
              CLLocationManager().authorizationStatus != .denied else { return fired }
        let pending = Set(await center.pendingNotificationRequests().map(\.identifier))
        for item in items where item.locationTrigger != nil && !item.isCompleted && !pending.contains(placeIdentifier(item.id)) {
            fired.insert(item.id)
        }
        return fired
    }

    /// showImmediate: a fresh card with no date posts right away when "Hemen bildir" is on.
    /// A place reminder waits for the place instead.
    static func postImmediateIfNeeded(_ item: ReminderItem) {
        guard enabled, !item.isCompleted, item.alertAt == nil, item.plannedDateEpochDay == nil, item.locationTrigger == nil,
              item.sendImmediateNotification, !item.isChecklist else { return }
        requestAuthorization()
        post(UNNotificationRequest(identifier: "reminder-\(item.id)-now",
                                                                    content: content(for: item, alerting: false), trigger: nil))
    }

    private static func identifier(_ id: Int64, _ index: Int) -> String { index == 0 ? "reminder-\(id)" : "reminder-\(id)-\(index)" }
    private static func endIdentifier(_ id: Int64) -> String { "reminder-\(id)-end" }

    /// Alarm snoozed on REAL's page before iOS 26 (no AlarmKit): one time-sensitive notification.
    static func scheduleAlarmFallback(_ alarm: RingingAlarm) {
        guard enabled else { return }
        let content = UNMutableNotificationContent()
        content.title = alarm.subtitle
        content.body = alarm.title
        content.sound = .default
        content.interruptionLevel = .timeSensitive
        content.userInfo = ["reminderId": alarm.id]
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, alarm.at.date.timeIntervalSinceNow), repeats: false)
        post(UNNotificationRequest(identifier: "reminder-\(alarm.id)-snooze", content: content, trigger: trigger))
    }

    static func cancel(_ id: Int64, alarms: Bool = true, keepSnooze: Bool = false) {
        guard enabled else { return }
        if alarms, #available(iOS 26.0, *) { RealAlarmKit.cancel(id, keepSnooze: keepSnooze) }
        let ids = (0..<repeatsAhead).map { identifier(id, $0) } + (0..<repeatsAhead).map { earlyIdentifier(id, $0) }
            + [placeIdentifier(id), DepartureReminder.identifier(id), endIdentifier(id)]
            + (keepSnooze ? [] : ["reminder-\(id)-snooze"])
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ids)
    }

    /// Delivered banners of a completed / deleted card are removed too (ReminderNotifications.cancel).
    static func removeDelivered(_ id: Int64) {
        guard enabled else { return }
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: (0..<repeatsAhead).map { identifier(id, $0) } + (0..<repeatsAhead).map { earlyIdentifier(id, $0) }
            + ["reminder-\(id)-now", placeIdentifier(id), "reminder-\(id)-snooze", endIdentifier(id)])
    }

    /// Off while refilling notifications only: system alarms stay as they are (ringing, snoozed, scheduled).
    private static var touchingAlarms = true

    /// alarms: false = refill notifications only (REAL coming to the front); AlarmKit keeps its own alarms.
    static func reschedule(_ items: [ReminderItem], alarms: Bool = true) {
        guard enabled else { return }
        if alarms, #available(iOS 26.0, *) { RealAlarmKit.cancelAll() }
        // An alarm snoozed on REAL's page (pre-iOS 26) is not part of any card's schedule, so it stays.
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { pending in
            center.removePendingNotificationRequests(withIdentifiers: pending.map(\.identifier).filter { !$0.hasSuffix("-snooze") })
            DispatchQueue.main.async {
                asking = false
                touchingAlarms = alarms
                // The cards as they are now: one added while iOS answered is not lost to the earlier snapshot.
                (ReminderStore.current?.reminders ?? items).forEach(schedule)
                touchingAlarms = true
                asking = true
            }
        }
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

    // MARK: Notification picture

    /// A card photo, else the category icon, else the first person's initial; any failure falls through to the next.
    private static func notificationAttachments(for item: ReminderItem, category: CustomCategory?) -> [UNNotificationAttachment] {
        if let photo = item.attachments.first(where: { $0.type == .image }),
           let attachment = photoNotificationAttachment(photo) {
            return [attachment]
        }
        if let category, let attachment = categoryAttachment(category) { return [attachment] }
        if let person = item.cardPeople.first, let attachment = personNotificationAttachment(person) {
            return [attachment]
        }
        return []
    }

    private static func photoNotificationAttachment(_ attachment: ReminderAttachment) -> UNNotificationAttachment? {
        guard !attachment.id.isEmpty, let dir = notificationPictureDirectory() else { return nil }
        let cached = dir.appendingPathComponent("photo-\(notificationFileName(attachment.id)).jpg")
        if !FileManager.default.fileExists(atPath: cached.path) {
            guard let image = AttachmentStorage.image(attachment.localPath),
                  let data = notificationPhotoData(image),
                  (try? data.write(to: cached, options: .atomic)) != nil else { return nil }
        }
        return copyNotificationAttachment(cached)
    }

    private static func personNotificationAttachment(_ person: CardPerson) -> UNNotificationAttachment? {
        let name = person.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let first = name.first else { return nil }
        let letter = String(first).uppercased()
        guard !letter.isEmpty, let dir = notificationPictureDirectory() else { return nil }
        let cached = dir.appendingPathComponent("person-\(notificationFileName(letter)).png")
        if !FileManager.default.fileExists(atPath: cached.path) {
            guard let data = notificationPersonData(letter),
                  (try? data.write(to: cached, options: .atomic)) != nil else { return nil }
        }
        return copyNotificationAttachment(cached)
    }

    private static func notificationPictureDirectory() -> URL? {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("notification-pictures", isDirectory: true)
        return (try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)) != nil ? dir : nil
    }

    /// Attachments are moved into the notification store, so iOS always gets a fresh copy, never the cache or original.
    private static func copyNotificationAttachment(_ source: URL) -> UNNotificationAttachment? {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("notification-attachments", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let ext = source.pathExtension.isEmpty ? "png" : source.pathExtension
        let copy = dir.appendingPathComponent(UUID().uuidString + "." + ext)
        guard (try? FileManager.default.copyItem(at: source, to: copy)) != nil else { return nil }
        return try? UNNotificationAttachment(identifier: "picture", url: copy, options: nil)
    }

    private static func notificationPhotoData(_ image: UIImage) -> Data? {
        let maxEdge: CGFloat = 512
        let longest = max(image.size.width, image.size.height)
        guard longest > 0 else { return nil }
        let factor = min(1, maxEdge / longest)
        let size = CGSize(width: max(1, image.size.width * factor), height: max(1, image.size.height * factor))
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let resized = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
        return resized.jpegData(compressionQuality: 0.85)
    }

    private static func notificationPersonData(_ letter: String) -> Data? {
        let size = CGSize(width: 128, height: 128)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            UIColor(argb: 0xFF2F5D50).setFill()
            UIBezierPath(ovalIn: CGRect(origin: .zero, size: size)).fill()
            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = .center
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 64, weight: .semibold),
                .foregroundColor: UIColor.white,
                .paragraphStyle: paragraph,
            ]
            let text = letter as NSString
            let textSize = text.size(withAttributes: attributes)
            text.draw(in: CGRect(x: 0, y: (size.height - textSize.height) / 2,
                                 width: size.width, height: textSize.height), withAttributes: attributes)
        }
        return image.pngData()
    }

    private static func notificationFileName(_ value: String) -> String {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-_"))
        let name = value.unicodeScalars.map { allowed.contains($0) ? String($0) : "_" }.joined()
        return name.isEmpty ? "x" : String(name.prefix(80))
    }
}

extension Notification.Name {
    /// Notification tap → open that card's detail.
    static let realOpenReminder = Notification.Name("REALOpenReminder")
}
