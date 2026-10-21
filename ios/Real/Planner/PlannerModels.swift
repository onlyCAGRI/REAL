import SwiftUI

/// One occurrence of a card on the calendar (PlannerEvent).
struct PlannerEvent: Identifiable, Hashable {
    let reminder: ReminderItem
    let startMillis: EpochMillis
    let isDateOnly: Bool
    /// Bitiş: when this occurrence ends, possibly days later. nil = it lives on its start day only.
    var spanEndMillis: EpochMillis? = nil

    var id: String { "\(reminder.id):\(startMillis)" }
    var endMillis: EpochMillis? {
        isDateOnly ? nil : reminder.durationMinutes.map { startMillis + EpochMillis($0) * 60_000 }
    }
    func startDay(_ calendar: Calendar = .real) -> Date { calendar.startOfDay(for: startMillis.date) }
    /// The last day this occurrence covers (an end at midnight belongs to the day before).
    func lastDay(_ calendar: Calendar = .real) -> Date {
        guard let end = spanEndMillis else { return startDay(calendar) }
        return max(startDay(calendar), calendar.startOfDay(for: (end - 1).date))
    }
    /// Spans more than one day: the month view joins its dots with a line.
    func isMultiDay(_ calendar: Calendar = .real) -> Bool { lastDay(calendar) > startDay(calendar) }
}

enum Planner {
    /// plannerEvents: expands date-only, timed and repeating cards inside [rangeStart, rangeEnd).
    static func events(_ reminders: [ReminderItem], rangeStart: EpochMillis, rangeEnd: EpochMillis,
                       calendar: Calendar = .real) -> [PlannerEvent] {
        var out: [PlannerEvent] = []
        for reminder in reminders {
            if let day = reminder.plannedDateEpochDay {
                let start = calendar.startOfDayMillis(epochDay: day)
                let spanEnd = reminder.endAt.flatMap { $0 > start ? $0 : nil }
                if start < rangeEnd && (spanEnd ?? (start + 1)) > rangeStart {
                    out.append(PlannerEvent(reminder: reminder, startMillis: start, isDateOnly: true, spanEndMillis: spanEnd))
                }
                continue
            }
            guard let current = reminder.alertAt else { continue }
            if reminder.repeatRule == .none {
                let spanEnd = reminder.endAt.flatMap { $0 > current ? $0 : nil }
                let end = max(reminder.durationMinutes.map { current + EpochMillis($0) * 60_000 } ?? current, spanEnd ?? 0)
                if current < rangeEnd && end >= rangeStart {
                    out.append(PlannerEvent(reminder: reminder, startMillis: current, isDateOnly: false, spanEndMillis: spanEnd))
                }
                continue
            }
            // A repeating event with an end spans the same length each time (its first start → Bitiş).
            let repeatSpan = reminder.endAt.map { $0 - (reminder.anchorAlertAt ?? current) }.flatMap { $0 > 0 ? $0 : nil }
            let lookBack = repeatSpan ?? 0
            let anchor = reminder.anchorAlertAt ?? current
            let skipped = reminder.repeatSkippedEpochDays
            let until = reminder.repeatUntilEpochDay
            func next(_ after: EpochMillis) -> EpochMillis? {
                Recurrence.nextRepeat(base: anchor, rule: reminder.repeatRule, weekdaysMask: reminder.repeatWeekdaysMask,
                                      untilEpochDay: until, after: after, skipped: skipped, calendar: calendar)
            }
            let anchorDay = calendar.epochDay(ofMillis: anchor)
            let anchorAlive = !skipped.contains(anchorDay) && (until.map { anchorDay <= $0 } ?? true)
            var occurrence: EpochMillis? = anchor >= rangeStart - lookBack ? (anchorAlive ? anchor : next(anchor)) : next(rangeStart - lookBack - 1)
            var safety = 0
            while let o = occurrence, o < rangeEnd, safety < 5000 {
                if o + lookBack >= rangeStart {
                    out.append(PlannerEvent(reminder: reminder, startMillis: o, isDateOnly: false, spanEndMillis: repeatSpan.map { o + $0 }))
                }
                occurrence = next(o)
                safety += 1
            }
        }
        return out.sorted { $0.startMillis < $1.startMillis }
    }

    static func day(of event: PlannerEvent, calendar: Calendar = .real) -> Date { calendar.startOfDay(for: event.startMillis.date) }

    /// Events by every day they cover (a 4-day event is on all 4 days).
    static func byDay(_ events: [PlannerEvent], calendar: Calendar = .real) -> [Date: [PlannerEvent]] {
        var out: [Date: [PlannerEvent]] = [:]
        for e in events {
            var d = e.startDay(calendar)
            let last = e.lastDay(calendar)
            var guardCount = 0
            while d <= last && guardCount < 400 {
                out[d, default: []].append(e)
                d = calendar.date(byAdding: .day, value: 1, to: d)!
                guardCount += 1
            }
        }
        return out
    }

    /// plannerFocusEvents: starred cards of the selected day.
    static func focusEvents(_ events: [PlannerEvent], day: Date, calendar: Calendar = .real) -> [PlannerEvent] {
        var seen = Set<Int64>()
        return events.filter { e in
            e.reminder.isImportant && (e.reminder.plannedDateEpochDay != nil || e.reminder.alertAt != nil)
                && calendar.isDate(e.startMillis.date, inSameDayAs: day) && seen.insert(e.reminder.id).inserted
        }.sorted { $0.startMillis < $1.startMillis }
    }

    static func focusHeading(day: Date, today: Date, count: Int, calendar: Calendar = .real) -> String {
        let label: String
        if calendar.isDate(day, inSameDayAs: today) {
            label = "Today’s"
        } else {
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US")
            f.dateFormat = "dd MMM"
            label = f.string(from: day)
        }
        return "\(label)\nFocus" + (count > 0 ? " (\(count))" : "")
    }

    static func timeLabel(_ event: PlannerEvent) -> String {
        if event.isDateOnly { return "All day" }
        let start = RealDates.time(event.startMillis.date)
        return event.endMillis.map { "\(start) – \(RealDates.time($0.date))" } ?? start
    }

    static func durationLabel(minutes: Int64) -> String {
        let h = minutes / 60, m = minutes % 60
        if h > 0 && m > 0 { return "\(h)h \(m)m" }
        return h > 0 ? "\(h)h" : "\(m)m"
    }

    /// "Çağrı Gül" -> "Çağrı"; with three or more words everything but the last one is kept.
    static func firstName(_ full: String) -> String? {
        let parts = full.split(whereSeparator: \.isWhitespace).map(String.init)
        if parts.isEmpty { return nil }
        return parts.count >= 3 ? parts.dropLast().joined(separator: " ") : parts[0]
    }

    static func initials(_ name: String) -> String {
        name.split(whereSeparator: \.isWhitespace).prefix(2).compactMap { $0.first.map { String($0).uppercased() } }.joined()
    }
}

// MARK: - Calendar pastels (PlannerCalendarColors.kt)

let realCalendarPastels: [Color] = ([
    0xFFF7E1E6, 0xFFFFD6A5, 0xFFE6D6F7, 0xFFD6EAD4, 0xFFFEF3B0,
    0xFFE6D6E7, 0xFFFFC1B6, 0xFFD0F0ED, 0xFFEAD8B0, 0xFFDCE6F7,
] as [UInt32]).map { Color(argb: $0) }

private func hsl(_ c: Color) -> (h: Double, s: Double, l: Double) {
    let (r, g, b, _) = c.rgba
    let mx = max(r, g, b), mn = min(r, g, b), d = mx - mn
    let l = (mx + mn) / 2
    let s = d == 0 ? 0 : (l <= 0.5 ? d / (mx + mn) : d / (2 - mx - mn))
    var h = 0.0
    if d != 0 {
        if mx == r { h = ((g - b) / d + (g < b ? 6 : 0)) * 60 }
        else if mx == g { h = ((b - r) / d + 2) * 60 }
        else { h = ((r - g) / d + 4) * 60 }
    }
    return (h, s, l)
}

private let pastelHsl = realCalendarPastels.map(hsl)

func nearestCalendarPastel(_ raw: Color) -> Color {
    let x = hsl(raw)
    if x.s < 0.08 { return Color(argb: 0xFFEAD8B0) }
    var best = 0
    var bestDistance = Double.greatestFiniteMagnitude
    for (i, p) in pastelHsl.enumerated() {
        let dh = abs(x.h - p.h)
        let distance = min(dh, 360 - dh) + abs(x.s - p.s) * 20
        if distance < bestDistance { bestDistance = distance; best = i }
    }
    return realCalendarPastels[best]
}

/// REAL palette, a touch creamier so cards read as pastel on AMOLED black.
func plannerSoftPastel(_ base: Color) -> Color { base.lerp(Color(argb: 0xFFFFFBF4), 0.24) }

/// Kotlin Long.hashCode(): (value xor (value ushr 32)).toInt()
private func kotlinHash(_ value: Int64) -> Int {
    let u = UInt64(bitPattern: value)
    return Int(Int32(truncatingIfNeeded: u ^ (u >> 32)))
}

func uncategorizedPastel(_ id: Int64) -> Color {
    let n = realCalendarPastels.count
    return realCalendarPastels[((kotlinHash(id) % n) + n) % n]
}

/// resolveNext24CardColor: the category's own colour, else the card's, else a REAL palette colour picked from its id.
func plannerCardColor(_ reminder: ReminderItem, categories: [CustomCategory]) -> Color {
    if let i = reminder.plannerColorIndex, realCalendarPastels.indices.contains(i) { return realCalendarPastels[i] }
    if let id = reminder.customCategoryId, let cat = categories.first(where: { $0.id == id }), !cat.name.isEmpty {
        return cat.visual.stripe.0
    }
    if let solid = reminder.customSolidColorArgb { return Color(argbInt: solid) }
    if let raw = paletteColor(reminder.accentStart) { return raw }
    let order = reminderColorDisplayOrder.filter { $0 != 1 }   // the 20, without the near-black
    let n = order.count
    return reminderColorChoices[order[((kotlinHash(reminder.id) % n) + n) % n]].color
}

/// Text on a planner card: dark on light colours, white on dark ones.
func plannerInk(on surface: Color) -> Color {
    categoryForeground(start: surface, end: surface) == .white ? .white : RealColors.plannerInk
}

// MARK: - Timeline geometry (PlannerTimelineGeometry.kt)

struct PlannerTimeInterval: Hashable {
    var key: String
    var startMinute: Double
    var endMinute: Double
    var small = false
}

struct PlannerTimePlacement: Hashable {
    var interval: PlannerTimeInterval
    var lane: Int
}

/// Piecewise-linear clock: hours with a task expand, empty hours collapse. Values are points.
struct PlannerTimelineGeometry {
    let placements: [PlannerTimePlacement]
    let hourHeights: [Double]
    let boundaries: [Double]
    let dayMinutes: Int
    let startHour = 0
    let endHour: Int

    var height: Double { boundaries.last ?? 0 }
    var laneCount: Int { (placements.map(\.lane).max() ?? -1) + 1 }

    init(intervals: [PlannerTimeInterval], dayMinutes: Int = 1440, compactHour: Double = 12,
         expandedHour: Double = 72, smallHour: Double? = nil) {
        self.dayMinutes = dayMinutes
        endHour = Int((Double(dayMinutes) / 60).rounded(.up))
        var laneEnds: [Double] = []
        let clipped = intervals.compactMap { i -> PlannerTimeInterval? in
            var c = i
            c.startMinute = min(max(i.startMinute, 0), Double(dayMinutes))
            c.endMinute = min(max(i.endMinute, 0), Double(dayMinutes))
            return c.endMinute <= c.startMinute ? nil : c
        }.sorted { ($0.startMinute, $0.endMinute, $0.key) < ($1.startMinute, $1.endMinute, $1.key) }
        placements = clipped.map { interval in
            if let free = laneEnds.firstIndex(where: { $0 <= interval.startMinute }) {
                laneEnds[free] = interval.endMinute
                return PlannerTimePlacement(interval: interval, lane: free)
            }
            laneEnds.append(interval.endMinute)
            return PlannerTimePlacement(interval: interval, lane: laneEnds.count - 1)
        }
        let small = smallHour ?? expandedHour
        let placementsCopy = placements
        hourHeights = (0..<endHour).map { hour in
            func touches(_ s: Bool) -> Bool {
                placementsCopy.contains { $0.interval.small == s && $0.interval.startMinute < Double((hour + 1) * 60) && $0.interval.endMinute > Double(hour * 60) }
            }
            let h = touches(false) ? expandedHour : (touches(true) ? small : compactHour)
            let fraction = Double(max(min(60, dayMinutes - hour * 60), 0)) / 60
            return h * (fraction > 0 ? fraction : 1)
        }
        var acc = 0.0
        boundaries = [0] + hourHeights.map { acc += $0; return acc }
    }

    func y(_ minute: Double) -> Double {
        let time = min(max(minute, Double(startHour * 60)), Double(endHour * 60))
        let offset = min(max(Int(time / 60) - startHour, 0), hourHeights.count - 1)
        let hour = startHour + offset
        let minutesInHour = Double(max(min(60, dayMinutes - hour * 60), 1))
        return boundaries[offset] + hourHeights[offset] * (time - Double(hour * 60)) / minutesInHour
    }

    /// plannerDailyGeometry: same 20-minute fallback, completed events claim a short slot.
    static func daily(_ events: [PlannerEvent], day: Date, calendar: Calendar = .real) -> PlannerTimelineGeometry {
        let start = calendar.startOfDay(for: day).epochMillis
        let end = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: day))!.epochMillis
        let intervals = events.filter { !$0.isDateOnly }.map { e -> PlannerTimeInterval in
            let done = e.reminder.isCompleted
            let eventEnd = done ? e.startMillis + 50 * 60_000 : (e.endMillis ?? e.startMillis + 20 * 60_000)
            return PlannerTimeInterval(key: e.id, startMinute: Double(max(e.startMillis, start) - start) / 60_000,
                                       endMinute: Double(min(eventEnd, end) - start) / 60_000, small: done)
        }
        return PlannerTimelineGeometry(intervals: intervals, dayMinutes: Int((end - start) / 60_000),
                                       compactHour: 2, expandedHour: 96, smallHour: 46)
    }
}
