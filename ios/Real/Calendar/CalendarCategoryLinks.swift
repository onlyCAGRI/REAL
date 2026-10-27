import SwiftUI
import Observation

/// "Kategorize et" for phone calendars: a whole device calendar (e.g. Formula 1) or a single event is shown in a REAL
/// category's colour, and as read-only cards inside that category. Events stay in their calendar; nothing is copied
/// into REAL. Calendar and event ids belong to this phone, so the links live on this device only.
@Observable
final class CalendarCategoryLinks {
    static let shared = CalendarCategoryLinks()
    private(set) var revision = 0
    @ObservationIgnored private let key = "calendar_category_links"
    private var map: [String: Int64]

    init() {
        map = (UserDefaults.standard.dictionary(forKey: "calendar_category_links") as? [String: NSNumber])?.mapValues { $0.int64Value } ?? [:]
    }

    /// The event's own id (an occurrence id is "<eventIdentifier>@<start>").
    static func eventKey(_ o: CalendarOccurrence) -> String { String(o.id.split(separator: "@").first ?? Substring(o.id)) }

    /// The event's own link first, then its calendar's.
    func categoryFor(_ o: CalendarOccurrence) -> Int64? {
        _ = revision
        return map["event_" + Self.eventKey(o)] ?? map["calendar_" + o.calendarId]
    }
    func isWholeCalendar(_ calendarId: String) -> Bool { map["calendar_" + calendarId] != nil }
    func hasCategory(_ id: Int64) -> Bool { map.values.contains(id) }

    /// Links (or, with nil, unlinks) the event alone or its whole calendar.
    func link(_ o: CalendarOccurrence, categoryId: Int64?, wholeCalendar: Bool) {
        map.removeValue(forKey: "event_" + Self.eventKey(o))
        if wholeCalendar || categoryId == nil { map.removeValue(forKey: "calendar_" + o.calendarId) }
        if let categoryId { map[(wholeCalendar ? "calendar_" + o.calendarId : "event_" + Self.eventKey(o))] = categoryId }
        UserDefaults.standard.set(map.mapValues { NSNumber(value: $0) }, forKey: key)
        revision += 1
    }
}

/// Calendar events swiped off once they ended ("Gizle"), by occurrence. Only today's can be swiped, so keys of
/// earlier days are dropped on the next write.
@Observable
final class CalendarPastDismissals {
    static let shared = CalendarPastDismissals()
    private(set) var revision = 0
    @ObservationIgnored private let key = "calendar_past_dismissed"

    func isDismissed(_ o: CalendarOccurrence) -> Bool {
        (UserDefaults.standard.dictionary(forKey: key) as? [String: Double])?[o.id] != nil
    }

    func dismiss(_ o: CalendarOccurrence) {
        let yesterday = Calendar.real.startOfDay(for: Date()).timeIntervalSince1970 - 86_400
        var kept = ((UserDefaults.standard.dictionary(forKey: key) as? [String: Double]) ?? [:]).filter { $0.value >= yesterday }
        kept[o.id] = o.start.timeIntervalSince1970
        UserDefaults.standard.set(kept, forKey: key)
        revision += 1
    }
}

/// Phone-calendar events as read-only cards: inside their category (the coming year) and, with Ayarlar → "Ana ekranda
/// takvim", on Home for the next 7 / 14 days. A card's id is negative; it opens the same event window as the calendar.
@MainActor
enum CalendarCards {
    static let homeDaysKey = "calendar_home_days"
    private static var cache: (stamp: String, items: [CalendarOccurrence])?

    /// The nearest occurrence of every upcoming event in the next [days] days, plus today's events that already
    /// ended: those stay, faded ([isPast]), until midnight or a "Gizle" swipe.
    static func upcoming(days: Int) -> [CalendarOccurrence] {
        let connection = CalendarConnection.shared
        let links = CalendarCategoryLinks.shared
        let dismissals = CalendarPastDismissals.shared
        let today = Calendar.real.startOfDay(for: Date())
        let stamp = "\(connection.revision)/\(links.revision)/\(dismissals.revision)/\(today.timeIntervalSince1970)/\(days)"
        if let cache, cache.stamp == stamp { return cache.items }
        let end = Calendar.real.date(byAdding: .day, value: days, to: today)!
        var seen = Set<String>()
        let items = connection.occurrences(from: today, to: end)
            .filter { $0.end > today && ($0.end > Date() || !dismissals.isDismissed($0)) }
            .filter { seen.insert(CalendarCategoryLinks.eventKey($0)).inserted }
        cache = (stamp, items)
        return items
    }

    /// Ended today: shown faded; a left swipe hides it.
    static func isPast(_ o: CalendarOccurrence) -> Bool { o.end <= Date() }

    static func card(_ o: CalendarOccurrence) -> ReminderItem { PlannerScreen.externalCard(o) }

    /// Linked to [categoryId], the coming year.
    static func inCategory(_ categoryId: Int64) -> [CalendarOccurrence] {
        upcoming(days: 366).filter { CalendarCategoryLinks.shared.categoryFor($0) == categoryId }
    }
}
