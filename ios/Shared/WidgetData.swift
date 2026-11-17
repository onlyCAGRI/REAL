import Foundation

/// REAL's home-screen widgets. A widget cannot read the app's own storage, so the app leaves a light summary of its
/// cards, categories and water in the App Group container (`widgets.json`), and a widget button (tick, +1 bardak)
/// changes that summary at once and leaves the change in `widget-actions.json`; REAL applies it the next time it is
/// active. Colours are 0xAARRGGBB ints; times are epoch milliseconds; days are local epoch days (Calendar.real).
enum WidgetData {
    static let groupId = "group.com.cagri.real"
    /// One glass on the widgets (the app's own quick amount).
    static let glassMl = 250
    /// A card ticked on a widget stays, struck through, this long before the next card takes its place.
    static let doneLingerMs: Int64 = 60_000
    /// A day picked on the calendar widget goes back to today after this long.
    static let pickedDayMs: Int64 = 10 * 60_000

    struct Card: Codable, Hashable, Identifiable {
        var id: Int64
        var title: String
        var done = false
        /// Set by the widget's tick, so the card lingers ([doneLingerMs]) instead of vanishing under the finger.
        var doneAt: Int64? = nil
        var alertAt: Int64? = nil
        /// The day the card belongs to on the calendar (its alert's day, else its planned day).
        var day: Int? = nil
        /// Devam eden etkinlik: the occurrence's start and end.
        var spanStart: Int64? = nil
        var spanEnd: Int64? = nil
        var category: String? = nil
        var stripeA: Int? = nil
        var stripeB: Int? = nil
    }

    struct Category: Codable, Hashable, Identifiable {
        /// OrderedCategory.orderKey ("custom:12").
        var id: String
        var title: String
        var start: Int
        var end: Int
        /// Open cards in the category (the summary itself carries only its first few).
        var open = 0
    }

    struct Water: Codable, Hashable {
        var day = 0
        var todayMl = 0
        var goalMl = 2_000
        /// The six days before [day], oldest first: that day's water over that day's goal.
        var past: [Double] = []
    }

    struct Snapshot: Codable {
        /// Open cards in the order Kartlarım shows them on the widget (running, today, then newest).
        var cards: [Card] = []
        var categories: [Category] = []
        var water = Water()
        var english = false
        /// This month as the in-app calendar has it (optional: older summaries have none).
        var month: Month? = nil
        /// Phone-calendar events filed under a category ("Kategorize et"), the next few of each, soonest first.
        var categoryEvents: [Card]? = nil
    }

    /// The month's days [first, last] as the in-app calendar draws them: done cards too, a repeat on each of its days,
    /// an event on every day it covers, and the phone's calendars REAL reads (their events have negative ids).
    /// One Card per card and day, with [Card.day] set to that day.
    struct Month: Codable {
        var first: Int
        var last: Int
        var cards: [Card] = []
    }

    struct Action: Codable {
        /// "done" / "undone" (card [id]) or "water" ([ml]).
        var kind: String
        var id: Int64? = nil
        var ml: Int? = nil
        var at: Int64
    }

    static var container: URL? { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupId) }
    private static var file: URL? { container?.appendingPathComponent("widgets.json") }
    private static var actionsFile: URL? { container?.appendingPathComponent("widget-actions.json") }

    static func read() -> Snapshot {
        guard let file, let data = try? Data(contentsOf: file), let s = try? JSONDecoder().decode(Snapshot.self, from: data) else { return Snapshot() }
        return s
    }

    static func write(_ s: Snapshot) {
        guard let file, let data = try? JSONEncoder().encode(s) else { return }
        try? data.write(to: file, options: .atomic)
    }

    static func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    /// Local epoch day, as Calendar.real.epochDay(of:) counts it.
    static func epochDay(_ date: Date = Date()) -> Int {
        var local = Calendar(identifier: .gregorian)
        local.timeZone = .current
        let comps = local.dateComponents([.year, .month, .day], from: date)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        guard let day = utc.date(from: comps) else { return 0 }
        return Int((day.timeIntervalSince1970 / 86_400).rounded(.down))
    }

    static func date(ofEpochDay day: Int) -> Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let comps = utc.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: Double(day) * 86_400))
        var local = Calendar(identifier: .gregorian)
        local.timeZone = .current
        return local.date(from: comps) ?? Date()
    }

    /// The water numbers as of [today]: a summary written yesterday rolls over (yesterday joins the past days).
    static func water(_ w: Water, today: Int = epochDay()) -> Water {
        guard w.day != 0, today > w.day else { return w }
        var out = w
        let gap = today - w.day
        var days = w.past + [w.goalMl > 0 ? Double(w.todayMl) / Double(w.goalMl) : 0]
        if gap > 1 { days += Array(repeating: 0, count: min(gap - 1, 6)) }
        out.past = Array(days.suffix(6))
        out.todayMl = 0
        out.day = today
        return out
    }

    // MARK: What a widget button does

    private static func queue(_ action: Action) {
        guard let actionsFile else { return }
        var list = (try? Data(contentsOf: actionsFile)).flatMap { try? JSONDecoder().decode([Action].self, from: $0) } ?? []
        list.append(action)
        if let data = try? JSONEncoder().encode(list) { try? data.write(to: actionsFile, options: .atomic) }
    }

    /// The app: the changes widgets made since it last looked, oldest first.
    static func takeActions() -> [Action] {
        guard let actionsFile, let data = try? Data(contentsOf: actionsFile) else { return [] }
        try? FileManager.default.removeItem(at: actionsFile)
        return (try? JSONDecoder().decode([Action].self, from: data)) ?? []
    }

    /// Tick on a widget: done now (and lingering), or open again if it was ticked a moment ago.
    static func toggleCard(_ id: Int64) {
        var s = read()
        guard let i = s.cards.firstIndex(where: { $0.id == id }) else { return }
        let now = nowMs()
        s.cards[i].done.toggle()
        s.cards[i].doneAt = s.cards[i].done ? now : nil
        write(s)
        queue(Action(kind: s.cards[i].done ? "done" : "undone", id: id, at: now))
    }

    static func addGlass(_ ml: Int = glassMl) {
        var s = read()
        s.water = water(s.water)
        if s.water.day == 0 { s.water.day = epochDay() }
        s.water.todayMl += ml
        write(s)
        queue(Action(kind: "water", ml: ml, at: nowMs()))
    }

    // MARK: Calendar widget: the picked day

    private static var defaults: UserDefaults? { UserDefaults(suiteName: groupId) }

    static func pickDay(_ day: Int) {
        defaults?.set(day, forKey: "widget_cal_day")
        defaults?.set(Double(nowMs()), forKey: "widget_cal_day_at")
    }

    /// The picked day and when the pick runs out, or nil (today).
    static func pickedDay(now: Int64 = nowMs()) -> (day: Int, until: Int64)? {
        guard let d = defaults, d.object(forKey: "widget_cal_day") != nil else { return nil }
        let until = Int64(d.double(forKey: "widget_cal_day_at")) + pickedDayMs
        return now < until ? (d.integer(forKey: "widget_cal_day"), until) : nil
    }
}
