import Foundation
import Observation
import UserNotifications

/// Local-first store for cards and categories (ReminderRepository + CategoryRepository).
/// Everything is persisted as one JSON document in Application Support.
@Observable
final class ReminderStore {
    private(set) var reminders: [ReminderItem] = []
    private(set) var categories: [CustomCategory] = []
    private(set) var builtInColors: [ReminderCategory: CategoryColorOverride] = [:]
    var categoryOrderKeys: [String] = []
    var ownerName: String = ""
    /// Planner goal screen state (UI-only prefs on Android): goal order, pastel, card shape, link.
    private(set) var goalOrder: [Int64: [Int64]] = [:]
    private(set) var categoryPastel: [Int64: Int] = [:]
    private(set) var categoryCardShape: [Int64: String] = [:]
    private(set) var categoryLinks: [Int64: String] = [:]

    private let fileURL: URL
    private let persist: Bool
    let calendar = Calendar.real

    private struct Snapshot: Codable {
        var version = 1
        var reminders: [ReminderItem]
        var categories: [CustomCategory]
        var builtInColors: [String: CategoryColorOverride]
        var categoryOrderKeys: [String]
        var ownerName: String
        var goalOrder: [Int64: [Int64]]? = nil
        var categoryPastel: [Int64: Int]? = nil
        var categoryCardShape: [Int64: String]? = nil
        var categoryLinks: [Int64: String]? = nil
    }

    /// The live store, for notification actions handled by the app delegate.
    static weak var current: ReminderStore?

    init(fileURL: URL? = nil, persist: Bool = true) {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        self.fileURL = fileURL ?? dir.appendingPathComponent("real-store.json")
        self.persist = persist
        load()
    }

    // MARK: Persistence

    private func load() {
        guard persist, let data = try? Data(contentsOf: fileURL),
              let snap = try? JSONDecoder().decode(Snapshot.self, from: data) else { return }
        reminders = snap.reminders
        categories = snap.categories
        builtInColors = Dictionary(uniqueKeysWithValues: snap.builtInColors.compactMap { key, value in
            ReminderCategory(rawValue: key).map { ($0, value) }
        })
        categoryOrderKeys = snap.categoryOrderKeys
        ownerName = snap.ownerName
        goalOrder = snap.goalOrder ?? [:]
        categoryPastel = snap.categoryPastel ?? [:]
        categoryCardShape = snap.categoryCardShape ?? [:]
        categoryLinks = snap.categoryLinks ?? [:]
    }

    /// Called after every save (the app points it at SharedCategorySync so shared cards go up shortly).
    @ObservationIgnored var onSaved: (() -> Void)?
    /// True while SharedCategorySync writes, so its own writes do not schedule another round.
    @ObservationIgnored var syncWriting = false

    private func save() {
        NotificationScheduler.categories = categories
        if !syncWriting { onSaved?() }
        guard persist else { return }
        let snap = Snapshot(reminders: reminders, categories: categories,
                            builtInColors: Dictionary(uniqueKeysWithValues: builtInColors.map { ($0.key.rawValue, $0.value) }),
                            categoryOrderKeys: categoryOrderKeys, ownerName: ownerName,
                            goalOrder: goalOrder, categoryPastel: categoryPastel,
                            categoryCardShape: categoryCardShape, categoryLinks: categoryLinks)
        guard let data = try? JSONEncoder().encode(snap) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }

    func replaceAll(reminders: [ReminderItem], categories: [CustomCategory], ownerName: String) {
        // Ortak kategori cards and other people's categories are not in the personal cloud: keep them, and keep the
        // sharing bookkeeping of this person's own shared categories.
        let restoredIds = Set(reminders.map(\.id))
        let shared = self.reminders.filter { $0.sharedCardId != nil && !restoredIds.contains($0.id) }
        let mirrors = self.categories.filter(\.isSharedMirror)
        let ownShared = Dictionary(self.categories.filter { $0.sharedRole == "owner" }.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        self.reminders = reminders + shared
        self.categories = categories.map { c in
            guard let old = ownShared[c.id] else { return c }
            var c = c; c.sharedId = old.sharedId; c.sharedRole = old.sharedRole; return c
        } + mirrors.filter { m in !categories.contains { $0.id == m.id } }
        self.ownerName = ownerName
        save()
        NotificationScheduler.reschedule(reminders)
    }

    func setOwnerName(_ name: String) {
        ownerName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        save()
    }

    // MARK: Lookups

    func reminder(_ id: Int64) -> ReminderItem? { reminders.first { $0.id == id } }
    func category(_ id: Int64?) -> CustomCategory? { id.flatMap { id in categories.first { $0.id == id } } }
    private var hiddenCategoryIds: Set<Int64> { Set(categories.filter(\.hideFromGeneralLists).map(\.id)) }

    /// Android's key for the home "Tümünü göster" button in the order list; "hidden:<key>" hides a category or it.
    static let showAllKey = "action:show_all"
    static let hiddenPrefix = "hidden:"

    var hiddenHomeKeys: Set<String> {
        Set(categoryOrderKeys.filter { $0.hasPrefix(Self.hiddenPrefix) }.map { String($0.dropFirst(Self.hiddenPrefix.count)) })
    }

    /// Every deck category in the saved order, hidden ones included (for the order editor).
    var allHomeCategories: [OrderedCategory] {
        let builtIns: [OrderedCategory] = [ReminderCategory.planned, .important, .noAlert, .completed].map { OrderedCategory.builtIn($0) }
        let all = builtIns + categories.map { OrderedCategory.custom($0) }
        let byKey = Dictionary(uniqueKeysWithValues: all.map { ($0.orderKey, $0) })
        let ordered = categoryOrderKeys.compactMap { byKey[$0] }
        return ordered + all.filter { item in !ordered.contains { $0.orderKey == item.orderKey } }
    }

    /// categoryOrderWithVisibility: the order without metadata, then the hidden keys.
    func setCategoryOrder(_ order: [String], hidden: Set<String>) {
        let clean = order.filter { !$0.hasPrefix(Self.hiddenPrefix) && $0 != Self.showAllKey }
        var seen = Set<String>()
        setCategoryOrder(clean.filter { seen.insert($0).inserted } + hidden.sorted().map { Self.hiddenPrefix + $0 })
    }

    /// Home deck order (resolveHomeCategoryOrder): 24 hour and Günlüklerim are not part of the deck; hidden ones are left out.
    var homeCategoryOrder: [OrderedCategory] {
        let hidden = hiddenHomeKeys
        return homeCategoryOrderAll.filter { !hidden.contains($0.orderKey) }
    }

    private var homeCategoryOrderAll: [OrderedCategory] {
        let builtIns: [OrderedCategory] = [ReminderCategory.planned, .important, .noAlert, .completed].map { OrderedCategory.builtIn($0) }
        let customs: [OrderedCategory] = categories.map { OrderedCategory.custom($0) }
        let all = builtIns + customs
        guard !categoryOrderKeys.isEmpty else { return all }
        let byKey = Dictionary(uniqueKeysWithValues: all.map { ($0.orderKey, $0) })
        let ordered = categoryOrderKeys.compactMap { byKey[$0] }
        let rest = all.filter { !categoryOrderKeys.contains($0.orderKey) }
        return ordered + rest
    }

    /// Home list: active cards outside hidden categories. Cards moved into 24 hour stay on Home too.
    func homeActive(now: EpochMillis = .now) -> [ReminderItem] {
        let hidden = hiddenCategoryIds
        return reminders.filter { r in
            !r.isCompleted && !(r.customCategoryId.map(hidden.contains) ?? false)
        }.sorted { $0.createdAt > $1.createdAt }
    }

    func homeCompleted() -> [ReminderItem] {
        let hidden = hiddenCategoryIds
        return reminders.inCategory(.completed).filter { !($0.customCategoryId.map(hidden.contains) ?? false) }
    }

    func items(in category: ReminderCategory, now: EpochMillis = .now) -> [ReminderItem] {
        let hidden = hiddenCategoryIds
        let list = reminders.inCategory(category, now: now, calendar: calendar)
        return category == .journalEntries ? list : list.filter { !($0.customCategoryId.map(hidden.contains) ?? false) }
    }

    func count(for item: OrderedCategory) -> Int {
        switch item {
        case .builtIn(let c): return items(in: c).count
        case .custom(let c): return reminders.filter { $0.customCategoryId == c.id }.count
        }
    }

    // MARK: Card mutations

    @discardableResult
    func addReminder(text: String, alertAt: EpochMillis? = nil, plannedDay: EpochDay? = nil, repeatRule: RecurrenceRule = .none,
                     categoryId: Int64? = nil, isImportant: Bool = false, checklist: [String] = [],
                     configure: ((inout ReminderItem) -> Void)? = nil) -> ReminderItem {
        let now = EpochMillis.now
        var item = ReminderItem(id: CategoryRules.nextId(now: now, existing: reminders.map(\.id)),
                                text: text.trimmingCharacters(in: .whitespacesAndNewlines), createdAt: now)
        item.alertAt = alertAt
        item.anchorAlertAt = alertAt
        item.plannedDateEpochDay = alertAt == nil ? plannedDay : nil
        item.repeatRule = alertAt == nil ? .none : repeatRule
        item.customCategoryId = categoryId
        item.isImportant = isImportant
        if !checklist.isEmpty {
            item.isChecklist = true
            item.checklistItems = checklist.enumerated().map { ChecklistItem(id: now + Int64($0.offset), text: $0.element) }
        }
        configure?(&item)
        // An app version below the minimum may only look: the card is not kept.
        guard AppUpdateGate.writable() else { return item }
        reminders.append(item)
        save()
        NotificationScheduler.categories = categories
        NotificationScheduler.schedule(item)
        NotificationScheduler.postImmediateIfNeeded(item)
        return item
    }

    func update(_ item: ReminderItem) {
        guard AppUpdateGate.writable() else { return }
        guard let index = reminders.firstIndex(where: { $0.id == item.id }) else { return }
        var updated = item
        let old = reminders[index]
        if updated.alertAt != old.alertAt && updated.repeatRule != .none && old.anchorAlertAt == old.alertAt {
            updated.anchorAlertAt = updated.alertAt
        }
        if updated.repeatRule == .none { updated.anchorAlertAt = updated.alertAt }
        if updated.alertAt != old.alertAt { updated.notifiedAlertAt = nil }
        // Removing the date removes its end and early warning; a new start keeps an end that is still after it.
        if updated.alertAt == nil { updated.earlyAlertMinutes = nil }
        if updated.alertAt == nil && updated.plannedDateEpochDay == nil { updated.endAt = nil }
        if let start = updated.alertAt ?? updated.plannedDateEpochDay.map({ calendar.startOfDayMillis(epochDay: $0) }),
           let end = updated.endAt, end <= start { updated.endAt = nil }
        guard updated != old else { return }
        updated.updatedAt = .now
        reminders[index] = updated
        save()
        NotificationScheduler.schedule(updated)
    }

    func mutate(_ id: Int64, _ change: (inout ReminderItem) -> Void) {
        guard AppUpdateGate.writable() else { return }
        guard var item = reminder(id) else { return }
        change(&item)
        update(item)
    }

    /// Restored files put back on their card (cloud restore): not a person's change, so never stopped by AppUpdateGate.
    func mutateRestored(_ id: Int64, _ change: (inout ReminderItem) -> Void) {
        guard var item = reminder(id) else { return }
        change(&item)
        putFromSync(item)
    }

    func toggleCompleted(_ id: Int64) {
        guard AppUpdateGate.writable() else { return }
        guard let index = reminders.firstIndex(where: { $0.id == id }) else { return }
        reminders[index] = reminders[index].toggledCompleted(now: .now, calendar: calendar)
        save()
        NotificationScheduler.schedule(reminders[index])
        if reminders[index].isCompleted { NotificationScheduler.removeDelivered(id) }
    }

    func toggleChecklistItem(_ reminderId: Int64, itemId: Int64) {
        guard AppUpdateGate.writable() else { return }
        mutate(reminderId) { r in
            r.checklistItems = r.checklistItems.map { $0.id == itemId ? $0.toggled() : $0 }
        }
    }

    /// Swipe left on Home (ReminderRepository.moveTo24Hour): into the rolling 24 hour view without a clock time.
    /// The card becomes today's, its alarm and repeat are cleared and a pending notification is cancelled.
    func moveTo24Hour(_ id: Int64) {
        guard AppUpdateGate.writable() else { return }
        let now = EpochMillis.now
        let today = calendar.epochDay(ofMillis: now)
        mutate(id) {
            $0.alertAt = nil
            $0.plannedDateEpochDay = today
            $0.quick24HourUntilAt = now + CategoryRules.rolling24HoursMillis
            $0.notifiedAlertAt = nil
            $0.repeatRule = .none
            $0.repeatWeekdaysMask = 0
            $0.repeatUntilEpochDay = nil
            $0.repeatSkippedEpochDays = []
            $0.anchorAlertAt = nil
            $0.endAt = nil
            $0.earlyAlertMinutes = nil
        }
        NotificationScheduler.cancel(id)
    }

    /// Delete with "Geri al" at the top (MainActivity.requestDelete): the cards go now and come back untouched,
    /// in their old places and with their alarms, if the action is tapped.
    @MainActor
    func deleteWithUndo(_ ids: Set<Int64>) {
        guard AppUpdateGate.writable() else { return }
        let removed = reminders.enumerated().filter { ids.contains($0.element.id) }
        guard !removed.isEmpty else { return }
        delete(ids)
        let label = removed.count == 1 ? "Kart silindi" : "\(removed.count) kart silindi"
        RealToast.shared.showAction(label, "Geri al") { [weak self] in
            guard let self else { return }
            for (index, item) in removed where !self.reminders.contains(where: { $0.id == item.id }) {
                self.reminders.insert(item, at: min(index, self.reminders.count))
            }
            self.save()
            removed.forEach { NotificationScheduler.schedule($0.element) }
        }
    }

    func delete(_ ids: Set<Int64>) {
        guard AppUpdateGate.writable() else { return }
        reminders.removeAll { ids.contains($0.id) }
        save()
        ids.forEach(NotificationScheduler.cancel)
        ids.forEach(NotificationScheduler.removeDelivered)
    }

    /// NotificationActionReceiver ACTION_SNOOZE_10: move the alert 10 minutes ahead, keeping the series anchor.
    func snooze(_ id: Int64, millis: EpochMillis = NotificationScheduler.snoozeMillis) {
        mutate(id) { r in
            guard !r.isCompleted else { return }
            r.anchorAlertAt = r.anchorAlertAt ?? r.alertAt
            r.alertAt = .now + millis
            r.notifiedAlertAt = nil
        }
    }

    /// "Sadece bu gün": skip one occurrence of a repeating series, the series itself stays.
    func deleteOccurrence(_ id: Int64, occurrence: EpochMillis) {
        guard AppUpdateGate.writable() else { return }
        mutate(id) { r in
            let day = calendar.epochDay(ofMillis: occurrence)
            if !r.repeatSkippedEpochDays.contains(day) { r.repeatSkippedEpochDays.append(day) }
            if let alert = r.alertAt, calendar.epochDay(ofMillis: alert) == day,
               let next = Recurrence.nextRepeat(base: r.anchorAlertAt ?? alert, rule: r.repeatRule, weekdaysMask: r.repeatWeekdaysMask,
                                                untilEpochDay: r.repeatUntilEpochDay, after: alert,
                                                skipped: r.repeatSkippedEpochDays, calendar: calendar) {
                r.alertAt = next
            }
        }
    }

    /// "Bu ve sonraki tüm tekrarları sil": the series ends the day before the chosen occurrence.
    func deleteThisAndFuture(_ id: Int64, occurrence: EpochMillis) {
        guard AppUpdateGate.writable() else { return }
        guard let item = reminder(id) else { return }
        let day = calendar.epochDay(ofMillis: occurrence)
        let anchorDay = calendar.epochDay(ofMillis: item.anchorAlertAt ?? item.alertAt ?? occurrence)
        if day <= anchorDay { delete([id]); return }
        mutate(id) { $0.repeatUntilEpochDay = day - 1 }
    }

    // MARK: Ortak kategori (SharedCategorySync writes)

    /// Puts a shared card in as the server has it (no updatedAt bump; its reminder is scheduled).
    func putFromSync(_ item: ReminderItem) {
        syncWriting = true; defer { syncWriting = false }
        if let i = reminders.firstIndex(where: { $0.id == item.id }) { reminders[i] = item } else { reminders.append(item) }
        save()
        NotificationScheduler.schedule(item)
    }

    func removeFromSync(_ id: Int64) {
        syncWriting = true; defer { syncWriting = false }
        reminders.removeAll { $0.id == id }
        save()
        NotificationScheduler.cancel(id)
        NotificationScheduler.removeDelivered(id)
    }

    func putCategoryFromSync(_ category: CustomCategory) {
        syncWriting = true; defer { syncWriting = false }
        if let i = categories.firstIndex(where: { $0.id == category.id }) { categories[i] = category } else { categories.append(category) }
        save()
    }

    func removeCategoryFromSync(_ id: Int64) {
        syncWriting = true; defer { syncWriting = false }
        let ids = Set(reminders.filter { $0.customCategoryId == id && $0.sharedCardId != nil }.map(\.id))
        reminders.removeAll { ids.contains($0.id) }
        categories.removeAll { $0.id == id }
        categoryOrderKeys.removeAll { $0 == "custom:\(id)" }
        save()
        ids.forEach(NotificationScheduler.cancel)
    }

    /// A category deleted in the cloud (its cards arrive on their own).
    func removeCategoryOnlyFromSync(_ id: Int64) {
        syncWriting = true; defer { syncWriting = false }
        categories.removeAll { $0.id == id }
        categoryOrderKeys.removeAll { $0 == "custom:\(id)" }
        save()
    }

    func setCategoryOrderFromSync(_ keys: [String]) {
        guard keys != categoryOrderKeys else { return }
        syncWriting = true; defer { syncWriting = false }
        categoryOrderKeys = keys
        save()
    }

    /// Rewrites cards in one go (e.g. a category coming home: its shared cards become personal).
    func rewriteFromSync(_ change: (inout [ReminderItem]) -> Void) {
        syncWriting = true; defer { syncWriting = false }
        change(&reminders)
        save()
    }

    // MARK: Planner goal screen prefs

    func setGoalOrder(_ categoryId: Int64, _ ids: [Int64]) { goalOrder[categoryId] = ids; save() }
    func setCategoryPastel(_ categoryId: Int64, _ index: Int?) { categoryPastel[categoryId] = index; save() }
    func setCategoryCardShape(_ categoryId: Int64, _ shape: String) { categoryCardShape[categoryId] = shape; save() }
    func setCategoryLink(_ categoryId: Int64, _ link: String?) { categoryLinks[categoryId] = link; save() }

    // MARK: Category mutations

    @discardableResult
    func addCategory(name: String, icon: CategoryIcon, accentStart: Int, accentEnd: Int?, hideFromGeneralLists: Bool = false,
                     useDarkText: Bool = false, blend: Double? = nil, description: String = "") -> CustomCategory {
        let now = EpochMillis.now
        let category = CustomCategory(id: CategoryRules.nextId(now: now, existing: categories.map(\.id)),
                                      name: name.trimmingCharacters(in: .whitespacesAndNewlines), iconKey: icon.rawValue,
                                      accentStart: accentStart, accentEnd: accentEnd, hideFromGeneralLists: hideFromGeneralLists,
                                      useDarkText: useDarkText, gradientBlendFraction: blend, description: description, createdAt: now)
        guard AppUpdateGate.writable() else { return category }
        categories.append(category)
        save()
        return category
    }

    func updateCategory(_ category: CustomCategory) {
        guard AppUpdateGate.writable() else { return }
        guard let index = categories.firstIndex(where: { $0.id == category.id }) else { return }
        categories[index] = category
        save()
    }

    /// deleteCategoryWithRollback: either delete the cards too or keep them uncategorised.
    func deleteCategory(_ id: Int64, deleteReminders: Bool) {
        guard AppUpdateGate.writable() else { return }
        if deleteReminders {
            let ids = Set(reminders.filter { $0.customCategoryId == id }.map(\.id))
            reminders.removeAll { ids.contains($0.id) }
            ids.forEach(NotificationScheduler.cancel)
        } else {
            reminders = reminders.map { r in
                var r = r
                if r.customCategoryId == id { r.customCategoryId = nil; r.updatedAt = .now }
                return r
            }
        }
        categories.removeAll { $0.id == id }
        categoryOrderKeys.removeAll { $0 == "custom:\(id)" }
        save()
    }

    func setBuiltInColor(_ category: ReminderCategory, _ override: CategoryColorOverride?) {
        builtInColors[category] = override
        save()
    }

    func setCategoryOrder(_ keys: [String]) {
        categoryOrderKeys = keys
        save()
    }
}

