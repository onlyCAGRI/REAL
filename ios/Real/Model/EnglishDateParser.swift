import Foundation

private let enPosixLocale = Locale(identifier: "en_US_POSIX")

private let enMonths: [String: Int] = [
    "january": 1, "jan": 1,
    "february": 2, "feb": 2,
    "march": 3, "mar": 3,
    "april": 4, "apr": 4,
    "may": 5,
    "june": 6, "jun": 6,
    "july": 7, "jul": 7,
    "august": 8, "aug": 8,
    "september": 9, "sept": 9, "sep": 9,
    "october": 10, "oct": 10,
    "november": 11, "nov": 11,
    "december": 12, "dec": 12
]

private let enWeekdays: [String: Weekday] = [
    "monday": .monday,
    "tuesday": .tuesday,
    "wednesday": .wednesday,
    "thursday": .thursday,
    "friday": .friday,
    "saturday": .saturday,
    "sunday": .sunday
]

private let monthNamesPattern =
    "january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sept|sep|october|oct|november|nov|december|dec"

private let weekdayNamesPattern =
    "monday|tuesday|wednesday|thursday|friday|saturday|sunday"

enum EnglishDateParser {

    static func parse(
        _ input: String,
        now: Date = Date(),
        calendar: Calendar = .real
    ) -> ParsedTypedReminder {
        let trimmedInput = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedInput.isEmpty {
            return ParsedTypedReminder(originalText: input, text: "", alertAt: nil)
        }

        let lower = preserveLengthLower(trimmedInput)

        return parseRelativeTime(trimmedInput, lower, now, calendar: calendar)
            ?? parseCalendarTime(trimmedInput, lower, now, calendar: calendar)
            ?? {
                let formatted = formatText(trimmedInput)
                return ParsedTypedReminder(
                    originalText: trimmedInput,
                    text: formatted.isEmpty ? trimmedInput : formatted,
                    alertAt: nil
                )
            }()
    }

    private static func parseRelativeTime(
        _ original: String,
        _ lower: String,
        _ now: Date,
        calendar: Calendar
    ) -> ParsedTypedReminder? {
        let halfHourPattern = #"\bin\s+(?:a\s+half|half\s+an)\s+(?:hours?|hrs?)\b"#
        if let match = regexFirstMatch(in: lower, pattern: halfHourPattern) {
            let added = calendar.date(byAdding: .minute, value: 30, to: now) ?? now
            let target = truncateToMinutes(added, calendar: calendar)
            let cleaned = cleanReminderText(original, lower, [match.range])
            return ParsedTypedReminder(originalText: original, text: cleaned, alertAt: target)
        }

        let hoursAndMinutesPattern = #"\bin\s+(\d+|an|a|one)\s+(?:hours?|hrs?)\s+(?:and\s+)?(\d+|an|a|one)\s+(?:minutes?|mins?)\b"#
        if let match = regexFirstMatch(in: lower, pattern: hoursAndMinutesPattern) {
            let hours = parseCount(regexGroup(1, in: lower, match: match))
            let minutes = parseCount(regexGroup(2, in: lower, match: match))
            let withHours = calendar.date(byAdding: .hour, value: hours, to: now) ?? now
            let withMinutes = calendar.date(byAdding: .minute, value: minutes, to: withHours) ?? withHours
            let target = truncateToMinutes(withMinutes, calendar: calendar)
            let cleaned = cleanReminderText(original, lower, [match.range])
            return ParsedTypedReminder(originalText: original, text: cleaned, alertAt: target)
        }

        let hoursPattern = #"\bin\s+(\d+|an|a|one)\s+(?:hours?|hrs?)\b"#
        if let match = regexFirstMatch(in: lower, pattern: hoursPattern) {
            let hours = parseCount(regexGroup(1, in: lower, match: match))
            let added = calendar.date(byAdding: .hour, value: hours, to: now) ?? now
            let target = truncateToMinutes(added, calendar: calendar)
            let cleaned = cleanReminderText(original, lower, [match.range])
            return ParsedTypedReminder(originalText: original, text: cleaned, alertAt: target)
        }

        let minutesPattern = #"\bin\s+(\d+|an|a|one)\s+(?:minutes?|mins?)\b"#
        if let match = regexFirstMatch(in: lower, pattern: minutesPattern) {
            let minutes = parseCount(regexGroup(1, in: lower, match: match))
            let added = calendar.date(byAdding: .minute, value: minutes, to: now) ?? now
            let target = truncateToMinutes(added, calendar: calendar)
            let cleaned = cleanReminderText(original, lower, [match.range])
            return ParsedTypedReminder(originalText: original, text: cleaned, alertAt: target)
        }

        return nil
    }

    private static func parseCount(_ token: String) -> Int {
        switch token {
        case "a", "an", "one": return 1
        default: return Int(token) ?? 1
        }
    }

    private static func parseCalendarTime(
        _ original: String,
        _ lower: String,
        _ now: Date,
        calendar: Calendar
    ) -> ParsedTypedReminder? {
        var rangesToRemove: [NSRange] = []
        var masked = lower

        var repeatRule: RecurrenceRule = .none
        var timeOfDay: ComposerTimeOfDay? = nil
        var targetWeekday: Weekday? = nil
        var forceNextWeek = false
        var targetDayOfMonth: Int? = nil

        let dailyDaypartPattern = #"\bevery\s+(morning|afternoon|evening|night|noon)\b"#
        if let m = regexFirstMatch(in: masked, pattern: dailyDaypartPattern) {
            repeatRule = .daily
            timeOfDay = daypartFromWord(regexGroup(1, in: masked, match: m))
            rangesToRemove.append(m.range)
            masked = maskRange(masked, m.range)
        }

        if repeatRule == .none {
            let dailyPattern = #"\b(?:every\s+day|everyday|daily)\b"#
            if let m = regexFirstMatch(in: masked, pattern: dailyPattern) {
                repeatRule = .daily
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        if repeatRule == .none {
            let recurringWeekdayPattern = "\\bevery\\s+(\(weekdayNamesPattern))\\b"
            if let m = regexFirstMatch(in: masked, pattern: recurringWeekdayPattern) {
                repeatRule = .weekly
                targetWeekday = enWeekdays[regexGroup(1, in: masked, match: m)]
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        if repeatRule == .none {
            let weeklyPattern = #"\b(?:every\s+week|weekly)\b"#
            if let m = regexFirstMatch(in: masked, pattern: weeklyPattern) {
                repeatRule = .weekly
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        if repeatRule == .none {
            let monthlyDayPattern = #"\b(?:every\s+month|monthly)\s+(?:on\s+)?(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\b"#
            if let mMatch = regexFirstMatch(in: masked, pattern: monthlyDayPattern),
               let dom = Int(regexGroup(1, in: masked, match: mMatch)),
               (1...31).contains(dom) {
                repeatRule = .monthly
                targetDayOfMonth = dom
                rangesToRemove.append(mMatch.range)
                masked = maskRange(masked, mMatch.range)
            } else {
                let bareMonthlyPattern = #"\b(?:every\s+month|monthly)\b"#
                if let m = regexFirstMatch(in: masked, pattern: bareMonthlyPattern) {
                    repeatRule = .monthly
                    rangesToRemove.append(m.range)
                    masked = maskRange(masked, m.range)
                }
            }
        }

        if repeatRule == .none {
            let yearlyPattern = #"\b(?:every\s+year|yearly)\b"#
            if let m = regexFirstMatch(in: masked, pattern: yearlyPattern) {
                repeatRule = .yearly
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        let today = calendar.startOfDay(for: now)
        let nowYear = calendar.component(.year, from: now)
        let nowMonth = calendar.component(.month, from: now)

        var targetDate: Date? = nil
        var hasSpecificDate = false
        var hasExplicitYear = false

        let monthFirstPattern = "\\b(?:on\\s+)?(\(monthNamesPattern))\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s*,?\\s*(20\\d{2}))?\\b"
        let dayFirstPattern = "\\b(?:on\\s+)?(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(\(monthNamesPattern))(?:\\s*,?\\s*(20\\d{2}))?\\b"

        let monthFirstMatch = regexFirstMatch(in: masked, pattern: monthFirstPattern)
        let dayFirstMatch = monthFirstMatch == nil ? regexFirstMatch(in: masked, pattern: dayFirstPattern) : nil
        if let specificMatch = monthFirstMatch ?? dayFirstMatch {
            let monthStr = monthFirstMatch != nil
                ? regexGroup(1, in: masked, match: specificMatch)
                : regexGroup(2, in: masked, match: specificMatch)
            let dayStr = monthFirstMatch != nil
                ? regexGroup(2, in: masked, match: specificMatch)
                : regexGroup(1, in: masked, match: specificMatch)
            let yearStr = regexGroup(3, in: masked, match: specificMatch)
            let month = enMonths[monthStr] ?? 1
            let day = Int(dayStr) ?? 1
            let year = Int(yearStr) ?? nowYear
            if let testDate = makeValidDate(year: year, month: month, day: day, calendar: calendar) {
                targetDate = testDate
                hasSpecificDate = true
                hasExplicitYear = !yearStr.isEmpty
                rangesToRemove.append(specificMatch.range)
                masked = maskRange(masked, specificMatch.range)
            }
        }

        if targetDate == nil {
            let thisDaypartPattern = #"\bthis\s+(morning|afternoon|evening|night)\b"#
            if let m = regexFirstMatch(in: masked, pattern: thisDaypartPattern) {
                targetDate = today
                if timeOfDay == nil { timeOfDay = daypartFromWord(regexGroup(1, in: masked, match: m)) }
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        if targetDate == nil {
            let relDayPattern = #"\b(today|tomorrow|tonight)\b"#
            if let m = regexFirstMatch(in: masked, pattern: relDayPattern) {
                let word = regexGroup(1, in: masked, match: m)
                switch word {
                case "today":
                    targetDate = today
                case "tomorrow":
                    targetDate = plusDays(today, 1, calendar: calendar)
                case "tonight":
                    targetDate = today
                    if timeOfDay == nil { timeOfDay = .night }
                default:
                    break
                }
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        if targetDate == nil {
            let nextWeekPattern = #"\bnext\s+week\b"#
            if let m = regexFirstMatch(in: masked, pattern: nextWeekPattern) {
                targetDate = plusDays(today, 7, calendar: calendar)
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        if targetDate == nil && targetWeekday == nil {
            let weekdayPattern = "\\b(?:(next)\\s+|on\\s+(?:(next)\\s+)?)?(\(weekdayNamesPattern))\\b"
            if let m = regexFirstMatch(in: masked, pattern: weekdayPattern) {
                targetWeekday = enWeekdays[regexGroup(3, in: masked, match: m)]
                forceNextWeek = !regexGroup(1, in: masked, match: m).isEmpty || !regexGroup(2, in: masked, match: m).isEmpty
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        let hasDayOrRepeat = targetDate != nil || targetWeekday != nil || targetDayOfMonth != nil || repeatRule != .none

        if timeOfDay == nil && hasDayOrRepeat {
            let daypartPattern = #"\b(morning|afternoon|evening|night)\b"#
            if let m = regexFirstMatch(in: masked, pattern: daypartPattern) {
                timeOfDay = daypartFromWord(regexGroup(1, in: masked, match: m))
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        var rawHour: Int? = nil
        var rawMinute: Int? = nil
        var isExplicit24h = false

        let noonMidnightPattern = hasDayOrRepeat ? #"\b(?:at\s+)?(noon|midnight)\b"# : #"\bat\s+(noon|midnight)\b"#
        if let m = regexFirstMatch(in: masked, pattern: noonMidnightPattern) {
            if regexGroup(1, in: masked, match: m) == "noon" {
                rawHour = 12
                rawMinute = 0
                isExplicit24h = true
                if timeOfDay == nil { timeOfDay = .noon }
            } else {
                rawHour = 0
                rawMinute = 0
                isExplicit24h = true
                if timeOfDay == nil { timeOfDay = .night }
            }
            rangesToRemove.append(m.range)
            masked = maskRange(masked, m.range)
        }

        if rawHour == nil {
            let amPmPattern = #"\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(a\.m\.|p\.m\.|a\.m|p\.m|am|pm)(?![\p{L}\d])"#
            if let m = regexFirstMatch(in: masked, pattern: amPmPattern) {
                let h = Int(regexGroup(1, in: masked, match: m))
                let minStr = regexGroup(2, in: masked, match: m)
                let min = minStr.isEmpty ? 0 : Int(minStr)
                let isPm = regexGroup(3, in: masked, match: m).hasPrefix("p")
                if let h, (1...12).contains(h), let min, (0...59).contains(min) {
                    let hour24: Int
                    if isPm {
                        hour24 = h == 12 ? 12 : h + 12
                    } else {
                        hour24 = h == 12 ? 0 : h
                    }
                    rawHour = hour24
                    rawMinute = min
                    isExplicit24h = true
                    if timeOfDay == nil {
                        if !isPm {
                            timeOfDay = h == 12 ? .night : .morning
                        } else if (12...16).contains(hour24) {
                            timeOfDay = .noon
                        } else if (17...20).contains(hour24) {
                            timeOfDay = .evening
                        } else {
                            timeOfDay = .night
                        }
                    }
                    rangesToRemove.append(m.range)
                    masked = maskRange(masked, m.range)
                }
            }
        }

        if rawHour == nil {
            let colonPattern = hasDayOrRepeat ? #"\b(?:at\s+)?(\d{1,2}):(\d{2})\b"# : #"\bat\s+(\d{1,2}):(\d{2})\b"#
            if let m = regexFirstMatch(in: masked, pattern: colonPattern) {
                let hStr = regexGroup(1, in: masked, match: m)
                let h = Int(hStr)
                let min = Int(regexGroup(2, in: masked, match: m))
                if let h, (0...23).contains(h), let min, (0...59).contains(min) {
                    rawHour = h
                    rawMinute = min
                    isExplicit24h = (h >= 13 || hStr.hasPrefix("0"))
                    rangesToRemove.append(m.range)
                    masked = maskRange(masked, m.range)
                }
            }
        }

        if rawHour == nil && hasDayOrRepeat {
            let atBareHourPattern = #"\bat\s+(\d{1,2})\b"#
            if let m = regexFirstMatch(in: masked, pattern: atBareHourPattern) {
                let hStr = regexGroup(1, in: masked, match: m)
                if let h = Int(hStr), (0...23).contains(h) {
                    rawHour = h
                    rawMinute = 0
                    isExplicit24h = (h >= 13 || hStr.hasPrefix("0"))
                    rangesToRemove.append(m.range)
                    masked = maskRange(masked, m.range)
                }
            }
        }

        if rawHour == nil && targetDate == nil && targetWeekday == nil && targetDayOfMonth == nil && repeatRule == .none {
            return nil
        }

        let finalHour: Int
        if let rawHour {
            finalHour = isExplicit24h ? rawHour : interpretHour(rawHour, timeOfDay)
        } else {
            finalHour = 0
        }
        let finalMinute = rawMinute ?? 0

        let resolvedDate: Date?
        if let targetDate {
            var d = targetDate
            if rawHour != nil {
                let candidate = atTime(d, hour: finalHour, minute: finalMinute, calendar: calendar)
                if !(candidate > now) {
                    d = (hasSpecificDate && !hasExplicitYear)
                        ? plusYears(d, 1, calendar: calendar)
                        : plusDays(d, 1, calendar: calendar)
                }
            } else {
                if d < today {
                    d = (hasSpecificDate && !hasExplicitYear)
                        ? plusYears(d, 1, calendar: calendar)
                        : plusDays(d, 1, calendar: calendar)
                }
            }
            resolvedDate = d
        } else if let targetDayOfMonth {
            var d = makeValidDate(year: nowYear, month: nowMonth, day: targetDayOfMonth, calendar: calendar) ?? today
            if rawHour != nil {
                let candidate = atTime(d, hour: finalHour, minute: finalMinute, calendar: calendar)
                if !(candidate > now) {
                    d = plusMonths(d, 1, calendar: calendar)
                }
            } else {
                if d < today {
                    d = plusMonths(d, 1, calendar: calendar)
                }
            }
            resolvedDate = d
        } else if let weekday = targetWeekday {
            let nowWeekday = calendar.isoWeekday(of: now)
            var daysAhead: Int
            if forceNextWeek {
                daysAhead = (8 - nowWeekday.rawValue) + (weekday.rawValue - 1)
            } else {
                daysAhead = (weekday.rawValue - nowWeekday.rawValue + 7) % 7
            }
            if daysAhead == 0 {
                if rawHour != nil {
                    let candidateToday = atTime(today, hour: finalHour, minute: finalMinute, calendar: calendar)
                    if !(candidateToday > now) {
                        daysAhead = 7
                    }
                } else {
                    let nowComps = calendar.dateComponents([.hour, .minute, .second, .nanosecond], from: now)
                    let h = nowComps.hour ?? 0
                    let m = nowComps.minute ?? 0
                    let s = nowComps.second ?? 0
                    let ns = nowComps.nanosecond ?? 0
                    if h > 22 || (h == 22 && (m > 0 || s > 0 || ns > 0)) {
                        daysAhead = 7
                    }
                }
            }
            resolvedDate = plusDays(today, daysAhead, calendar: calendar)
        } else if repeatRule == .daily {
            if rawHour != nil {
                let candidateToday = atTime(today, hour: finalHour, minute: finalMinute, calendar: calendar)
                resolvedDate = candidateToday > now ? today : plusDays(today, 1, calendar: calendar)
            } else {
                resolvedDate = today
            }
        } else if repeatRule == .weekly {
            resolvedDate = nil
        } else {
            if rawHour != nil {
                let candidateToday = atTime(today, hour: finalHour, minute: finalMinute, calendar: calendar)
                resolvedDate = candidateToday > now ? today : plusDays(today, 1, calendar: calendar)
            } else {
                resolvedDate = nil
            }
        }

        let alertAt: Date?
        let requiresTimeSelection: Bool
        let dateHint: Date?

        if rawHour != nil, let resolvedDate {
            alertAt = atTime(resolvedDate, hour: finalHour, minute: finalMinute, calendar: calendar)
            requiresTimeSelection = false
            dateHint = resolvedDate
        } else {
            alertAt = nil
            requiresTimeSelection = true
            dateHint = resolvedDate
        }

        let cleaned = cleanReminderText(original, lower, rangesToRemove)

        return ParsedTypedReminder(
            originalText: original,
            text: cleaned,
            alertAt: alertAt,
            repeatRule: repeatRule,
            requiresTimeSelection: requiresTimeSelection,
            dateHint: dateHint,
            timeOfDay: timeOfDay
        )
    }

    private static func preserveLengthLower(_ s: String) -> String {
        let ns = s as NSString
        var units = [unichar](repeating: 0, count: ns.length)
        ns.getCharacters(&units, range: NSRange(location: 0, length: ns.length))
        for i in units.indices {
            let u = units[i]
            if u == 0x0130 {
                units[i] = 0x0069
            } else if u >= 0x0041 && u <= 0x005A {
                units[i] = u + 32
            } else if let scalar = UnicodeScalar(u) {
                let l = String(scalar).lowercased(with: enPosixLocale) as NSString
                if l.length == 1 {
                    units[i] = l.character(at: 0)
                }
            }
        }
        return String(utf16CodeUnits: units, count: units.count)
    }

    private static func daypartFromWord(_ word: String) -> ComposerTimeOfDay? {
        switch word {
        case "morning": return .morning
        case "noon", "afternoon": return .noon
        case "evening": return .evening
        case "night": return .night
        default: return nil
        }
    }

    private static func maskRange(_ input: String, _ range: NSRange) -> String {
        guard range.location != NSNotFound, range.length > 0 else { return input }
        let ns = input as NSString
        guard range.location + range.length <= ns.length else { return input }
        let spaces = String(repeating: " ", count: range.length)
        return ns.replacingCharacters(in: range, with: spaces)
    }

    private static let leadingConnectorPattern = #"(?:\b(?:at|on)\s+)+$"#
    private static let trailingConnectorPattern = #"^(?:\s+(?:at|on)\b)+"#

    private static func cleanReminderText(_ original: String, _ lower: String, _ ranges: [NSRange]) -> String {
        if ranges.isEmpty { return formatText(original) }

        let nsLower = lower as NSString
        let totalLen = nsLower.length

        let expanded: [NSRange] = ranges
            .filter { $0.location != NSNotFound && $0.length > 0 }
            .map { r in
                var start = r.location
                var end = r.location + r.length
                if start > 0 {
                    let before = nsLower.substring(with: NSRange(location: 0, length: start))
                    if let m = regexFirstMatch(in: before, pattern: leadingConnectorPattern) {
                        start = m.range.location
                    }
                }
                if end < totalLen {
                    let after = nsLower.substring(with: NSRange(location: end, length: totalLen - end))
                    if let m = regexFirstMatch(in: after, pattern: trailingConnectorPattern) {
                        end = end + m.range.location + m.range.length
                    }
                }
                return NSRange(location: start, length: max(0, end - start))
            }
            .sorted { $0.location < $1.location }

        var merged: [NSRange] = []
        for r in expanded {
            if merged.isEmpty {
                merged.append(r)
            } else {
                let last = merged[merged.count - 1]
                let lastEnd = last.location + last.length
                let rEnd = r.location + r.length
                if r.location <= lastEnd {
                    merged[merged.count - 1] = NSRange(location: last.location, length: max(lastEnd, rEnd) - last.location)
                } else {
                    merged.append(r)
                }
            }
        }

        let nsOriginal = original as NSString
        let origLen = nsOriginal.length
        var sb = ""
        var lastIdx = 0
        for r in merged {
            if r.location > lastIdx {
                let endIdx = min(r.location, origLen)
                sb += nsOriginal.substring(with: NSRange(location: lastIdx, length: endIdx - lastIdx))
            }
            lastIdx = max(lastIdx, r.location + r.length)
        }
        if lastIdx < origLen {
            sb += nsOriginal.substring(with: NSRange(location: lastIdx, length: origLen - lastIdx))
        }

        return formatText(sb)
    }

    private static let trimPunctuationSet = CharacterSet(charactersIn: ",.;:-’'")

    private static func formatText(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: trimPunctuationSet)
        let clean = regexReplace(in: trimmed, pattern: #"\s+"#, with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard let first = clean.first else { return "" }
        if first.isLowercase {
            return String(first).uppercased(with: enPosixLocale) + clean.dropFirst()
        }
        return clean
    }

    // MARK: - Calendar helpers

    private static func truncateToMinutes(_ date: Date, calendar: Calendar) -> Date {
        var comps = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        comps.second = 0
        comps.nanosecond = 0
        return calendar.date(from: comps) ?? date
    }

    private static func makeValidDate(year: Int, month: Int, day: Int, calendar: Calendar) -> Date? {
        guard (1...12).contains(month), (1...31).contains(day) else { return nil }
        guard let date = calendar.date(from: DateComponents(year: year, month: month, day: day)) else { return nil }
        let check = calendar.dateComponents([.year, .month, .day], from: date)
        guard check.year == year, check.month == month, check.day == day else { return nil }
        return calendar.startOfDay(for: date)
    }

    private static func atTime(_ day: Date, hour: Int, minute: Int, calendar: Calendar) -> Date {
        var comps = calendar.dateComponents([.year, .month, .day], from: day)
        comps.hour = hour
        comps.minute = minute
        comps.second = 0
        comps.nanosecond = 0
        return calendar.date(from: comps) ?? day
    }

    private static func plusDays(_ day: Date, _ days: Int, calendar: Calendar) -> Date {
        let d = calendar.date(byAdding: .day, value: days, to: day) ?? day
        return calendar.startOfDay(for: d)
    }

    private static func plusMonths(_ day: Date, _ months: Int, calendar: Calendar) -> Date {
        let d = calendar.date(byAdding: .month, value: months, to: day) ?? day
        return calendar.startOfDay(for: d)
    }

    private static func plusYears(_ day: Date, _ years: Int, calendar: Calendar) -> Date {
        let d = calendar.date(byAdding: .year, value: years, to: day) ?? day
        return calendar.startOfDay(for: d)
    }
}
