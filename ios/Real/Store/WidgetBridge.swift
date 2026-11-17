import Foundation
import SwiftUI
import WidgetKit

/// The app's side of the home-screen widgets (Shared/WidgetData.swift): it leaves the summary the widgets draw from,
/// applies what a widget button changed while REAL was closed, and opens what a widget was tapped on.
@MainActor
enum WidgetBridge {
    private static var pending: Task<Void, Never>?

    /// After a save: the summary is rewritten shortly (saves come in bursts) and the widgets redraw.
    static func request(_ store: ReminderStore, _ life: LifeStore) {
        pending?.cancel()
        pending = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(600))
            guard !Task.isCancelled else { return }
            write(store, life)
        }
    }

    /// REAL became active: first what the widgets changed, then a fresh summary.
    static func sync(_ store: ReminderStore, _ life: LifeStore) {
        for action in WidgetData.takeActions() {
            switch action.kind {
            case "done", "undone":
                guard let id = action.id, let card = store.reminder(id), card.isCompleted != (action.kind == "done") else { continue }
                store.toggleCompleted(id)
            case "water":
                life.addDrink(action.ml ?? WidgetData.glassMl)
            default: break
            }
        }
        write(store, life)
    }

    static func write(_ store: ReminderStore, _ life: LifeStore) {
        let now = EpochMillis.now
        let calendar = Calendar.real
        let today = calendar.epochDay(of: Date())
        let categories = Dictionary(store.categories.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })

        func day(_ r: ReminderItem) -> EpochDay? { r.alertAt.map { calendar.epochDay(ofMillis: $0) } ?? r.plannedDateEpochDay }
        func span(_ r: ReminderItem) -> (start: EpochMillis, end: EpochMillis)? {
            if let running = r.runningSpan(at: now) { return running }
            guard let start = r.alertAt, start > now else { return nil }
            if let end = r.eventEndAt { return (start, end) }
            // A repeating event repeats its first occurrence's length.
            if r.hasRepeat, let first = r.anchorAlertAt ?? r.alertAt, let end = r.endAt, end > first, end - first <= 24 * 60 * 60_000 {
                return (start, start + (end - first))
            }
            return nil
        }
        func summary(_ r: ReminderItem) -> WidgetData.Card {
            let category = r.customCategoryId.flatMap { categories[$0] }
            let stripe = category?.visual.stripe
            let s = span(r)
            return WidgetData.Card(id: r.id, title: String((r.text.isEmpty ? t("Boş not") : r.text).prefix(120)),
                                   alertAt: r.alertAt, day: day(r).map { Int($0) },
                                   spanStart: s?.start, spanEnd: s?.end,
                                   category: category.map { "custom:\($0.id)" },
                                   stripeA: stripe?.0.argbInt, stripeB: stripe?.1.argbInt)
        }

        let open = store.cards.filter { !$0.isCompleted && $0.mood == nil && $0.journal == nil }
        // Kartlarım on the widget: what is running, then today's by time, then the newest.
        let running = open.filter { $0.runningSpan(at: now) != nil }.sorted { ($0.runningSpan(at: now)?.end ?? 0) < ($1.runningSpan(at: now)?.end ?? 0) }
        let runningIds = Set(running.map(\.id))
        let todays = open.filter { !runningIds.contains($0.id) && day($0) == today }
            .sorted { ($0.alertAt ?? .max) < ($1.alertAt ?? .max) }
        let todayIds = Set(todays.map(\.id))
        let rest = open.filter { !runningIds.contains($0.id) && !todayIds.contains($0.id) }
        // Then what is overdue (the latest first), then the rest, newest first.
        let overdue = rest.filter { r in day(r).map { $0 < today } ?? false }.sorted { (day($0) ?? 0) > (day($1) ?? 0) }
        let overdueIds = Set(overdue.map(\.id))
        let others = rest.filter { !overdueIds.contains($0.id) }.sorted { $0.createdAt > $1.createdAt }
        var listed = Array((running + todays + overdue + others).prefix(40))
        // The calendar widget's dots: every dated open card around this month.
        var seen = Set(listed.map(\.id))
        listed += open.filter { r in
            guard let d = day(r), d >= today - 40, d <= today + 70 else { return false }
            return seen.insert(r.id).inserted
        }.prefix(300)
        // Each category widget's own cards.
        for c in store.categories {
            listed += open.filter { $0.customCategoryId == c.id }.prefix(4).filter { seen.insert($0.id).inserted }
        }

        var water = WidgetData.Water(day: Int(today), todayMl: life.total(on: Date()), goalMl: life.dailyGoals[today] ?? life.goalMl)
        water.past = (1...6).reversed().map { back in
            let date = calendar.date(byAdding: .day, value: -back, to: Date()) ?? Date()
            let goal = life.dailyGoals[today - EpochDay(back)] ?? life.goalMl
            return goal > 0 ? Double(life.total(on: date)) / Double(goal) : 0
        }

        let snapshot = WidgetData.Snapshot(
            cards: listed.map(summary),
            categories: store.categories.map { c in
                let start = c.customSolidColorArgb.map { Color(argbInt: $0) } ?? paletteColor(c.accentStart) ?? Color(argb: 0xFF233F73)
                let end = c.customSolidColorArgb.map { Color(argbInt: $0) } ?? paletteColor(c.accentEnd) ?? start
                return WidgetData.Category(id: "custom:\(c.id)", title: c.name, start: start.argbInt, end: end.argbInt,
                                           open: open.filter { $0.customCategoryId == c.id }.count)
            },
            water: water, english: I18n.isEnglish)
        WidgetData.write(snapshot)
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// real://widget/card/<id> opens the card; the other widget links just bring REAL forward.
    static func handle(_ url: URL) -> Bool {
        guard url.scheme == "real", url.host == "widget" else { return false }
        let parts = url.pathComponents.filter { $0 != "/" }
        if parts.count == 2, parts[0] == "card", let id = Int64(parts[1]) {
            NotificationCenter.default.post(name: .realOpenReminder, object: nil, userInfo: ["reminderId": id])
        }
        return true
    }
}
