import Foundation

enum RecurrenceRule: String, Codable, CaseIterable, Identifiable {
    case none, daily, weekly, monthly, yearly

    var id: String { rawValue }
    var displayName: String {
        switch self {
        case .none: return t("Tekrarlama")
        case .daily: return t("Her gün")
        case .weekly: return t("Her hafta")
        case .monthly: return t("Her ay")
        case .yearly: return t("Her yıl")
        }
    }

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = RecurrenceRule(rawValue: raw.lowercased()) ?? .none
    }
}

/// ISO weekday used by the Android mask: 1 = Monday ... 7 = Sunday.
enum Weekday: Int, CaseIterable, Identifiable {
    case monday = 1, tuesday, wednesday, thursday, friday, saturday, sunday
    var id: Int { rawValue }
    var bit: Int { 1 << (rawValue - 1) }
    var short: String { [t("Pzt"), t("Sal"), t("Çar"), t("Per"), t("Cum"), t("Cmt"), t("Paz")][rawValue - 1] }
    var full: String { [t("Pazartesi"), t("Salı"), t("Çarşamba"), t("Perşembe"), t("Cuma"), t("Cumartesi"), t("Pazar")][rawValue - 1] }
    var english2: String { ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"][rawValue - 1] }

    /// Foundation weekday (1 = Sunday) -> ISO.
    init(foundation value: Int) { self = Weekday(rawValue: value == 1 ? 7 : value - 1)! }
}

/// Computed so the month names follow a runtime language change.
var turkishMonths: [String] { [t("Ocak"), t("Şubat"), t("Mart"), t("Nisan"), t("Mayıs"), t("Haziran"),
                               t("Temmuz"), t("Ağustos"), t("Eylül"), t("Ekim"), t("Kasım"), t("Aralık")] }

extension Calendar {
    /// Gregorian, device time zone, Monday-first: the same assumptions java.time makes on Android.
    static let real: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        calendar.firstWeekday = 2
        calendar.locale = Locale(identifier: "tr_TR")
        return calendar
    }()

    func epochDay(of date: Date) -> EpochDay {
        let comps = dateComponents([.year, .month, .day], from: date)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let local = utc.date(from: comps)!
        return EpochDay((local.timeIntervalSince1970 / 86_400).rounded(.down))
    }

    func epochDay(ofMillis millis: EpochMillis) -> EpochDay { epochDay(of: millis.date) }

    /// Local midnight of an epoch day.
    func startOfDay(epochDay: EpochDay) -> Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let utcDate = Date(timeIntervalSince1970: TimeInterval(epochDay) * 86_400)
        let comps = utc.dateComponents([.year, .month, .day], from: utcDate)
        return date(from: comps)!
    }

    func startOfDayMillis(epochDay: EpochDay) -> EpochMillis { startOfDay(epochDay: epochDay).epochMillis }

    func isoWeekday(of date: Date) -> Weekday { Weekday(foundation: component(.weekday, from: date)) }

    /// Date on [day] at the clock time of [time].
    func combine(day: Date, time: Date) -> Date {
        var comps = dateComponents([.year, .month, .day], from: day)
        let t = dateComponents([.hour, .minute, .second], from: time)
        comps.hour = t.hour; comps.minute = t.minute; comps.second = t.second
        return date(from: comps)!
    }
}

/// Port of RecurrenceRule.kt (computeNextOccurrence / computeNextRepeat and labels).
enum Recurrence {
    private static let maxSkipHops = 800

    static func nextOccurrence(base: EpochMillis, rule: RecurrenceRule, after: EpochMillis,
                               calendar: Calendar = .real) -> EpochMillis? {
        let component: Calendar.Component
        switch rule {
        case .none: return nil
        case .daily: component = .day
        case .weekly: component = .weekOfYear
        case .monthly: component = .month
        case .yearly: component = .year
        }
        let baseDate = base.date
        let baseDay = calendar.startOfDay(for: baseDate)
        let afterDay = calendar.startOfDay(for: after.date)
        let minStep = calendar.dateComponents([component], from: baseDay, to: afterDay).value(for: component) ?? 0
        var step = max(1, minStep)
        for _ in 0..<1000 {
            // Adding to the base date keeps month-end clamping identical to LocalDate.plusMonths.
            guard let next = calendar.date(byAdding: component, value: step, to: baseDate) else { return nil }
            let millis = next.epochMillis
            if millis > after { return millis }
            step += 1
        }
        return nil
    }

    static func firstPatternOccurrence(from day: Date, timeOf base: Date, mask: Int, after: EpochMillis,
                                       calendar: Calendar = .real) -> EpochMillis? {
        guard mask & 0x7F != 0 else { return nil }
        for offset in 0...400 {
            guard let d = calendar.date(byAdding: .day, value: offset, to: day) else { continue }
            guard mask & calendar.isoWeekday(of: d).bit != 0 else { continue }
            let millis = calendar.combine(day: d, time: base).epochMillis
            if millis > after { return millis }
        }
        return nil
    }

    static func nextRepeat(base: EpochMillis, rule: RecurrenceRule, weekdaysMask: Int, untilEpochDay: EpochDay?,
                           after: EpochMillis, skipped: [EpochDay] = [], calendar: Calendar = .real) -> EpochMillis? {
        guard rule != .none else { return nil }
        var cursor = after
        for _ in 0..<maxSkipHops {
            let candidate: EpochMillis?
            if rule == .weekly && weekdaysMask & 0x7F != 0 {
                let baseDay = calendar.startOfDay(for: base.date)
                let afterDay = calendar.startOfDay(for: cursor.date)
                let start = afterDay > baseDay ? afterDay : baseDay
                candidate = firstPatternOccurrence(from: start, timeOf: base.date, mask: weekdaysMask,
                                                   after: cursor, calendar: calendar)
            } else {
                candidate = nextOccurrence(base: base, rule: rule, after: cursor, calendar: calendar)
            }
            guard let next = candidate else { return nil }
            let nextDay = calendar.epochDay(ofMillis: next)
            if let untilEpochDay, nextDay > untilEpochDay { return nil }
            if !skipped.contains(nextDay) { return next }
            cursor = next
        }
        return nil
    }

    /// nextDeletableOccurrence: the occurrence a "sadece bu gün" delete targets from Home / category.
    static func nextDeletableOccurrence(_ item: ReminderItem, now: EpochMillis, calendar: Calendar = .real) -> EpochMillis? {
        guard item.hasRepeat, let anchor = item.anchorAlertAt ?? item.alertAt else { return nil }
        let anchorDay = calendar.epochDay(ofMillis: anchor)
        let alive = !item.repeatSkippedEpochDays.contains(anchorDay) && (item.repeatUntilEpochDay.map { anchorDay <= $0 } ?? true)
        if anchor >= now && alive { return anchor }
        return nextRepeat(base: anchor, rule: item.repeatRule, weekdaysMask: item.repeatWeekdaysMask,
                          untilEpochDay: item.repeatUntilEpochDay, after: max(now - 1, anchor),
                          skipped: item.repeatSkippedEpochDays, calendar: calendar)
    }

    static func description(_ rule: RecurrenceRule, alert: EpochMillis?, calendar: Calendar = .real) -> String {
        guard let alert else { return t("Yok") }
        let date = alert.date
        switch rule {
        case .none: return t("Yok")
        case .daily: return t("Her gün")
        case .weekly: return t("Her {0}", calendar.isoWeekday(of: date).full)
        case .monthly: return t("Her ayın {0}'i", calendar.component(.day, from: date))
        case .yearly:
            return t("Her yıl {0} {1}", calendar.component(.day, from: date), turkishMonths[calendar.component(.month, from: date) - 1])
        }
    }

    /// "Her Pzt, Çar, Cum · 29 Ekim'e kadar"
    static func patternLabel(_ item: ReminderItem, calendar: Calendar = .real) -> String? {
        guard item.repeatRule != .none else { return nil }
        let hasDays = item.repeatWeekdaysMask & 0x7F != 0
        guard hasDays || item.repeatUntilEpochDay != nil else { return nil }
        let days = hasDays
            ? t("Her {0}", Weekday.allCases.filter { item.repeatWeekdaysMask & $0.bit != 0 }.map(\.short).joined(separator: ", "))
            : description(item.repeatRule, alert: item.anchorAlertAt ?? item.alertAt, calendar: calendar)
        var until = ""
        if let end = item.repeatUntilEpochDay {
            let d = calendar.startOfDay(epochDay: end)
            until = t(" · {0} {1}'e kadar", calendar.component(.day, from: d), turkishMonths[calendar.component(.month, from: d) - 1])
        }
        return days + until
    }
}
