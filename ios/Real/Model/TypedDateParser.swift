import Foundation

enum ComposerTimeOfDay: String, Codable, Hashable {
    case morning
    case noon
    case evening
    case night
}

/// Maps a spoken Turkish hour to 0..23 using the bare-hour rule when no daypart is given.
func interpretHour(_ hour: Int, _ timeOfDay: ComposerTimeOfDay? = nil) -> Int {
    if let timeOfDay {
        switch timeOfDay {
        case .morning:
            return hour == 12 ? 0 : hour
        case .noon:
            return (1...6).contains(hour) ? hour + 12 : hour
        case .evening:
            return (1...11).contains(hour) ? hour + 12 : hour
        case .night:
            if (8...11).contains(hour) { return hour + 12 }
            if hour == 12 { return 0 }
            return hour
        }
    }

    if (1...7).contains(hour) { return hour + 12 }
    if (8...11).contains(hour) { return hour }
    if hour == 12 { return 12 }
    return hour
}

struct ExtractedChecklist: Hashable {
    var title: String
    var items: [String]
}

struct ParsedTypedReminder: Hashable {
    var originalText: String
    var text: String
    var alertAt: Date?
    var repeatRule: RecurrenceRule = .none
    var requiresTimeSelection: Bool = false
    var dateHint: Date? = nil
    var timeOfDay: ComposerTimeOfDay? = nil
    var isChecklist: Bool = false
    var checklistItems: [String] = []
}

private let trLocale = Locale(identifier: "tr_TR")

private let wordNumbers: [String: Int] = [
    "bir": 1, "iki": 2, "üç": 3, "uc": 3, "dört": 4, "dort": 4,
    "beş": 5, "bes": 5, "altı": 6, "alti": 6, "yedi": 7, "sekiz": 8,
    "dokuz": 9, "on": 10, "on bir": 11, "on iki": 12
]

private let months: [String: Int] = [
    "ocak": 1, "şubat": 2, "subat": 2, "mart": 3, "nisan": 4,
    "mayıs": 5, "mayis": 5, "haziran": 6, "temmuz": 7, "ağustos": 8, "agustos": 8,
    "eylül": 9, "eylul": 9, "ekim": 10, "kasım": 11, "kasim": 11, "aralık": 12, "aralik": 12
]

private let weekdays: [String: Weekday] = [
    "pazartesi": .monday,
    "salı": .tuesday,
    "sali": .tuesday,
    "çarşamba": .wednesday,
    "carsamba": .wednesday,
    "perşembe": .thursday,
    "persembe": .thursday,
    "cuma": .friday,
    "cumartesi": .saturday,
    "pazar": .sunday
]

private let verbNormalizations: [String: String] = [
    "arıcam": "ara",
    "arayacağım": "ara",
    "arayacam": "ara",
    "alıcam": "al",
    "alacağım": "al",
    "alacam": "al",
    "gidicem": "git",
    "gideceğim": "git",
    "gidecem": "git",
    "yapıcam": "yap",
    "yapacağım": "yap",
    "yapacam": "yap",
    "atıcam": "at",
    "atacağım": "at",
    "atacam": "at",
    "bakıcam": "bak",
    "bakacağım": "bak",
    "bakacam": "bak",
    "çıkıcam": "çık",
    "çıkacağım": "çık",
    "çıkacam": "çık",
    "kapatıcam": "kapat",
    "kapatacağım": "kapat",
    "kapatacam": "kapat",
    "göndericem": "gönder",
    "göndereceğim": "gönder",
    "gönderecem": "gönder",
    "yiycem": "ye",
    "yiyeceğim": "ye",
    "yiyecem": "ye",
    "içicem": "iç",
    "içeceğim": "iç",
    "içecem": "iç",
    "ödiycem": "öde",
    "ödeyeceğim": "öde",
    "ödeyecem": "öde",
    "temizliycem": "temizle",
    "temizleyeceğim": "temizle",
    "konuşucam": "konuş",
    "konuşacağım": "konuş"
]

func normalizeVerb(_ word: String) -> String {
    let clean = word.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(with: trLocale)
    return verbNormalizations[clean] ?? word
}

func stripBoundaryFillerYa(_ text: String) -> String {
    var result = text.trimmingCharacters(in: .whitespacesAndNewlines)
    if result.isEmpty { return "" }

    let leadingYaPattern = #"(?i)^\s*[,\s;:!?.…—–-]*ya(?!\p{L})(?!\s+(?:da|de)(?!\p{L}))[,\s;:!?.…—–-]*"#
    result = regexReplace(in: result, pattern: leadingYaPattern, with: "")

    let trailingYaPattern = #"(?i)(?<!\p{L})ya[,\s;:!?.…—–-]*$"#
    result = regexReplace(in: result, pattern: trailingYaPattern, with: "")

    return result.trimmingCharacters(in: .whitespacesAndNewlines)
}

private func turkishTitlecase(_ text: String) -> String {
    guard let first = text.first else { return "" }
    if first.isLowercase {
        return String(first).uppercased(with: trLocale) + text.dropFirst()
    }
    return text
}

func formatChecklistTitle(_ prefix: String?) -> String {
    var cleanPrefix = prefix?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

    let leadingArticlePattern = #"(?i)^(?:yeni\s+)?bir\s+"#
    cleanPrefix = regexReplace(in: cleanPrefix, pattern: leadingArticlePattern, with: "")
        .trimmingCharacters(in: .whitespacesAndNewlines)

    if cleanPrefix.isEmpty {
        return "Liste"
    }

    let lower = cleanPrefix.lowercased(with: trLocale)
    let title: String
    if lower.hasSuffix("listesi") {
        title = cleanPrefix
    } else if lower.hasSuffix("listem") {
        title = String(cleanPrefix.dropLast(1)) + "si"
    } else if lower.hasSuffix("liste") {
        title = cleanPrefix + "si"
    } else {
        title = "\(cleanPrefix) listesi"
    }

    return turkishTitlecase(title)
}

func splitChecklistItems(_ raw: String) -> [String] {
    let initialSegments = regexSplit(in: raw, pattern: #"[,;]+"#)
        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty }

    if initialSegments.isEmpty { return [] }

    var result: [String] = []

    for i in initialSegments.indices {
        var seg = initialSegments[i].trimmingCharacters(in: .whitespacesAndNewlines)
        if seg.lowercased(with: trLocale).hasPrefix("ve ") {
            seg = String(seg.dropFirst(3)).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if seg.isEmpty { continue }

        if i == initialSegments.count - 1 {
            let subItems = regexSplit(in: seg, pattern: #"(?i)\s+ve\s+"#)
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            result.append(contentsOf: subItems)
        } else {
            result.append(seg)
        }
    }

    let bulletChars: Set<Character> = ["-", "*", "•"]
    return result.compactMap { item in
        var trimmed = item.trimmingCharacters(in: .whitespacesAndNewlines)
        while let first = trimmed.first, bulletChars.contains(first) {
            trimmed.removeFirst()
        }
        trimmed = trimmed.trimmingCharacters(in: .whitespacesAndNewlines)
        trimmed = stripBoundaryFillerYa(trimmed)
        if trimmed.isEmpty { return nil }
        return turkishTitlecase(trimmed)
    }
}

func extractChecklist(_ text: String) -> ExtractedChecklist? {
    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty { return nil }

    let pattern1 = #"(?i)^(?:(.*?)\s+)?(listesi|listem|liste)\s+(oluştur|olustur|yap|hazırla|hazirla|kur)\b[:,\s\-]*(.+)$"#
    if let match = regexFirstMatch(in: trimmed, pattern: pattern1) {
        let prefix = regexGroup(1, in: trimmed, match: match)
        let rawItems = regexGroup(4, in: trimmed, match: match)
        let items = splitChecklistItems(rawItems)
        if !items.isEmpty {
            return ExtractedChecklist(title: formatChecklistTitle(prefix), items: items)
        }
    }

    let patternVerbFirst = #"(?i)^(oluştur|olustur|yap|hazırla|hazirla|kur)\s+(?:bir\s+)?(?:(.*?)\s+)?(listesi|listem|liste)\b[:,\s\-]*(.+)$"#
    if let match = regexFirstMatch(in: trimmed, pattern: patternVerbFirst) {
        let prefix = regexGroup(2, in: trimmed, match: match)
        let rawItems = regexGroup(4, in: trimmed, match: match)
        let items = splitChecklistItems(rawItems)
        if !items.isEmpty {
            return ExtractedChecklist(title: formatChecklistTitle(prefix), items: items)
        }
    }

    let patternAddA = #"(?i)^(?:(.*?)\s+)?(?:listeme|listesine)\s+(?:ekle|yaz)\b[:,\s\-]*(.+)$"#
    if let match = regexFirstMatch(in: trimmed, pattern: patternAddA) {
        let prefix = regexGroup(1, in: trimmed, match: match)
        let rawItems = regexGroup(2, in: trimmed, match: match)
        let items = splitChecklistItems(rawItems)
        if !items.isEmpty {
            return ExtractedChecklist(title: formatChecklistTitle(prefix), items: items)
        }
    }

    let patternAddB = #"(?i)^(?:(.*?)\s+)?(?:listeme|listesine)\s+(.+?)\s+(?:ekle|yaz)$"#
    if let match = regexFirstMatch(in: trimmed, pattern: patternAddB) {
        let prefix = regexGroup(1, in: trimmed, match: match)
        let rawItems = regexGroup(2, in: trimmed, match: match)
        let items = splitChecklistItems(rawItems)
        if !items.isEmpty {
            return ExtractedChecklist(title: formatChecklistTitle(prefix), items: items)
        }
    }

    let patternColon = #"(?i)^(?:(.*?)\s+)?(listesi|listem)\s*:\s*(.+)$"#
    if let match = regexFirstMatch(in: trimmed, pattern: patternColon) {
        let prefix = regexGroup(1, in: trimmed, match: match)
        let rawItems = regexGroup(3, in: trimmed, match: match)
        let items = splitChecklistItems(rawItems)
        if !items.isEmpty {
            return ExtractedChecklist(title: formatChecklistTitle(prefix), items: items)
        }
    }

    return nil
}

func applyChecklistExtraction(_ base: ParsedTypedReminder) -> ParsedTypedReminder {
    guard let extracted = extractChecklist(base.text) else { return base }
    var copy = base
    copy.text = extracted.title
    copy.isChecklist = true
    copy.checklistItems = extracted.items
    return copy
}

enum TypedDateParser {

    static func stripBoundaryFillerYa(_ text: String) -> String {
        REAL.stripBoundaryFillerYa(text)
    }

    static func extractChecklist(_ text: String) -> ExtractedChecklist? {
        REAL.extractChecklist(text)
    }

    static func formatChecklistTitle(_ prefix: String?) -> String {
        REAL.formatChecklistTitle(prefix)
    }

    static func splitChecklistItems(_ raw: String) -> [String] {
        REAL.splitChecklistItems(raw)
    }

    static func parse(
        _ input: String,
        now: Date = Date(),
        calendar: Calendar = .real
    ) -> ParsedTypedReminder {
        let normalizedInput = stripBoundaryFillerYa(input)
        let trimmedInput = normalizedInput.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedInput.isEmpty {
            return ParsedTypedReminder(originalText: input, text: "", alertAt: nil)
        }

        let lower = trimmedInput.lowercased(with: trLocale)

        let baseParsed = parseRelativeTime(trimmedInput, lower, now, calendar: calendar)
            ?? parseCalendarTime(trimmedInput, lower, now, calendar: calendar)
            ?? {
                let cleaned = cleanReminderText(trimmedInput, [])
                return ParsedTypedReminder(
                    originalText: trimmedInput,
                    text: cleaned.isEmpty ? trimmedInput : cleaned,
                    alertAt: nil
                )
            }()

        return applyChecklistExtraction(baseParsed)
    }

    private static func parseRelativeTime(
        _ original: String,
        _ lower: String,
        _ now: Date,
        calendar: Calendar
    ) -> ParsedTypedReminder? {
        let halfHourPattern = #"\b(?:bir\s+)?(?:yarım|yarim|buçuk|bucuk)\s+saat\s+sonra\b"#
        if let match = regexFirstMatch(in: lower, pattern: halfHourPattern) {
            let added = calendar.date(byAdding: .minute, value: 30, to: now) ?? now
            let target = truncateToMinutes(added, calendar: calendar)
            let cleaned = cleanReminderText(original, [match.range])
            return ParsedTypedReminder(originalText: original, text: cleaned, alertAt: target)
        }

        let oneAndHalfPattern = #"\bbir\s+(?:buçuk|bucuk)\s+saat\s+sonra\b"#
        if let match = regexFirstMatch(in: lower, pattern: oneAndHalfPattern) {
            let added = calendar.date(byAdding: .minute, value: 90, to: now) ?? now
            let target = truncateToMinutes(added, calendar: calendar)
            let cleaned = cleanReminderText(original, [match.range])
            return ParsedTypedReminder(originalText: original, text: cleaned, alertAt: target)
        }

        let hoursAndMinutesPattern = #"\b(\d+)\s+saat\s+(\d+)\s+(?:dakika|dk)\s+sonra\b"#
        if let match = regexFirstMatch(in: lower, pattern: hoursAndMinutesPattern) {
            let hours = Int(regexGroup(1, in: lower, match: match)) ?? 0
            let minutes = Int(regexGroup(2, in: lower, match: match)) ?? 0
            let withHours = calendar.date(byAdding: .hour, value: hours, to: now) ?? now
            let withMinutes = calendar.date(byAdding: .minute, value: minutes, to: withHours) ?? withHours
            let target = truncateToMinutes(withMinutes, calendar: calendar)
            let cleaned = cleanReminderText(original, [match.range])
            return ParsedTypedReminder(originalText: original, text: cleaned, alertAt: target)
        }

        let hoursPattern = #"\b(\d+|bir)\s+saat\s+sonra\b"#
        if let match = regexFirstMatch(in: lower, pattern: hoursPattern) {
            let grp = regexGroup(1, in: lower, match: match)
            let hours = grp == "bir" ? 1 : (Int(grp) ?? 1)
            let added = calendar.date(byAdding: .hour, value: hours, to: now) ?? now
            let target = truncateToMinutes(added, calendar: calendar)
            let cleaned = cleanReminderText(original, [match.range])
            return ParsedTypedReminder(originalText: original, text: cleaned, alertAt: target)
        }

        let minutesPattern = #"\b(\d+|bir)\s+(?:dakika|dk)\s+sonra\b"#
        if let match = regexFirstMatch(in: lower, pattern: minutesPattern) {
            let grp = regexGroup(1, in: lower, match: match)
            let minutes = grp == "bir" ? 1 : (Int(grp) ?? 1)
            let added = calendar.date(byAdding: .minute, value: minutes, to: now) ?? now
            let target = truncateToMinutes(added, calendar: calendar)
            let cleaned = cleanReminderText(original, [match.range])
            return ParsedTypedReminder(originalText: original, text: cleaned, alertAt: target)
        }

        return nil
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

        let dailyDaypartPattern = #"\bher\s+(sabahleyin|sabah|öğleden sonra|ogleden sonra|öğleyin|ogleyin|öğlen|oglen|öğle|ogle|akşamleyin|aksamleyin|akşam|aksam|geceleyin|gece)\b"#
        if let dailyDaypartMatch = regexFirstMatch(in: masked, pattern: dailyDaypartPattern) {
            repeatRule = .daily
            let word = regexGroup(1, in: masked, match: dailyDaypartMatch)
            timeOfDay = daypartFromWord(word)
            rangesToRemove.append(dailyDaypartMatch.range)
            masked = maskRange(masked, dailyDaypartMatch.range)
        }

        if repeatRule == .none {
            let recurrenceSignal = #"(?:(?:düzenli\s+)?(?:olarak\s+)?(?:tekrar\s+eden|tekrarlayan|tekrarlanan|tekrar\s+etsin|tekrarla|tekrar))"#
            let dailyPattern = "\\b(?:(?:her\\s+gün|hergun)(?:\\s+\(recurrenceSignal))?|(?:günlük|gunluk)\\s+\(recurrenceSignal))\\b"
            if let m = regexFirstMatch(in: masked, pattern: dailyPattern) {
                repeatRule = .daily
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        if repeatRule == .none {
            let weeklyPattern = #"\bher\s+hafta\b"#
            if let m = regexFirstMatch(in: masked, pattern: weeklyPattern) {
                repeatRule = .weekly
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        var targetWeekday: Weekday? = nil
        if repeatRule == .none {
            let recurringWeekdayPattern = #"\bher\s+(pazartesi|salı|sali|çarşamba|carsamba|perşembe|persembe|cuma|cumartesi|pazar)(?:\s+(?:günü|gunu))?\b"#
            if let m = regexFirstMatch(in: masked, pattern: recurringWeekdayPattern) {
                repeatRule = .weekly
                let word = regexGroup(1, in: masked, match: m)
                targetWeekday = weekdays[word]
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        var targetDayOfMonth: Int? = nil
        if repeatRule == .none {
            let monthlyDayPattern = #"\bher\s+ayın\s+(\d{1,2})(?:['’\`]?\s*(?:i|si|ü|u|ı|sı|e|a|de|da|te|ta|inde|ında|unde|ünde))?\b"#
            if let mMatch = regexFirstMatch(in: masked, pattern: monthlyDayPattern) {
                repeatRule = .monthly
                targetDayOfMonth = Int(regexGroup(1, in: masked, match: mMatch))
                rangesToRemove.append(mMatch.range)
                masked = maskRange(masked, mMatch.range)
            } else {
                let bareMonthlyPattern = #"\bher\s+ay\b"#
                if let m = regexFirstMatch(in: masked, pattern: bareMonthlyPattern) {
                    repeatRule = .monthly
                    rangesToRemove.append(m.range)
                    masked = maskRange(masked, m.range)
                }
            }
        }

        if repeatRule == .none {
            let yearlyPattern = #"\bher\s+(?:yıl|yil)\b"#
            if let m = regexFirstMatch(in: masked, pattern: yearlyPattern) {
                repeatRule = .yearly
                rangesToRemove.append(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        let today = calendar.startOfDay(for: now)
        let nowYear = calendar.component(.year, from: now)
        let nowMonth = calendar.component(.month, from: now)
        let nowDay = calendar.component(.day, from: now)

        var targetDate: Date? = nil
        var explicitYear: Int? = nil
        var useCurrentTime = false

        let explicitYearPattern = #"\b(20\d{2})(?:\s+(?:yılında|yilinda)|\s+(?:yılı|yili|yıl|yil)(?:\s*['’`]?\s*(?:da|de|ta|te))?|['’`]?\s*(?:da|de|ta|te))\b"#
        if let match = regexFirstMatch(in: masked, pattern: explicitYearPattern) {
            explicitYear = Int(regexGroup(1, in: masked, match: match))
            rangesToRemove.append(match.range)
            masked = maskRange(masked, match.range)
        }

        let specificDatePattern = #"\b(\d{1,2})\s*(ocak|şubat|subat|mart|nisan|mayıs|mayis|haziran|temmuz|ağustos|agustos|eylül|eylul|ekim|kasım|kasim|aralık|aralik)(?:['’`]?\s*(?:da|de|ta|te))?(?:\s+(?:günü|gunu|tarihinde))?\b"#
        let specificDateMatch = regexFirstMatch(in: masked, pattern: specificDatePattern)
        if let specificDateMatch {
            let day = Int(regexGroup(1, in: masked, match: specificDateMatch)) ?? 1
            let monthStr = regexGroup(2, in: masked, match: specificDateMatch)
            let month = months[monthStr] ?? 1
            let year = explicitYear ?? nowYear
            if let testDate = makeValidDate(year: year, month: month, day: day, calendar: calendar) {
                targetDate = testDate
                rangesToRemove.append(specificDateMatch.range)
                masked = maskRange(masked, specificDateMatch.range)
            }
        }

        if targetDate == nil {
            let sameTimeTomorrowPattern = #"\b(?:yarın|yarin)\s+bu\s+saatlerde\b"#
            if let match = regexFirstMatch(in: masked, pattern: sameTimeTomorrowPattern) {
                targetDate = plusDays(today, 1, calendar: calendar)
                useCurrentTime = true
                rangesToRemove.append(match.range)
                masked = maskRange(masked, match.range)
            }
        }

        if targetDate == nil {
            let relativeDayPattern = #"\b(bugün|bugun|yarın|yarin)\b"#
            if let relMatch = regexFirstMatch(in: masked, pattern: relativeDayPattern) {
                let word = regexGroup(1, in: masked, match: relMatch)
                targetDate = word.hasPrefix("yar") ? plusDays(today, 1, calendar: calendar) : today
                rangesToRemove.append(relMatch.range)
                masked = maskRange(masked, relMatch.range)
            }
        }

        if targetDate == nil, let year = explicitYear {
            let maxDay = lengthOfMonth(year: year, month: nowMonth, calendar: calendar)
            let day = min(nowDay, maxDay)
            targetDate = makeValidDate(year: year, month: nowMonth, day: day, calendar: calendar)
        }

        if targetDate == nil && targetWeekday == nil {
            let weekdayPattern = #"\b(pazartesi|salı|sali|çarşamba|carsamba|perşembe|persembe|cuma|cumartesi|pazar)(?:\s+(?:günü|gunu))?\b"#
            if let weekdayMatch = regexFirstMatch(in: masked, pattern: weekdayPattern) {
                let word = regexGroup(1, in: masked, match: weekdayMatch)
                targetWeekday = weekdays[word]
                rangesToRemove.append(weekdayMatch.range)
                masked = maskRange(masked, weekdayMatch.range)
            }
        }

        if timeOfDay == nil {
            let daypartPattern = #"\b(öğleden sonra|ogleden sonra|öğleyin|ogleyin|öğlen|oglen|öğle|ogle|sabahleyin|sabah|akşamleyin|aksamleyin|akşam|aksam|geceleyin|gece)\b"#
            if let daypartMatch = regexFirstMatch(in: masked, pattern: daypartPattern) {
                let word = regexGroup(1, in: masked, match: daypartMatch)
                timeOfDay = daypartFromWord(word)
                rangesToRemove.append(daypartMatch.range)
                masked = maskRange(masked, daypartMatch.range)
            }
        }

        var rawHour: Int? = useCurrentTime ? calendar.component(.hour, from: now) : nil
        var rawMinute: Int? = useCurrentTime ? calendar.component(.minute, from: now) : nil
        var isExplicit24h = useCurrentTime

        let colonTimePattern = #"\b(?:saat\s+)?(\d{1,2})[:.](\d{2})(?:\s*['’\`]?\s*(?:da|de|ta|te))?\b"#
        if let colonMatch = regexFirstMatch(in: masked, pattern: colonTimePattern) {
            let h = Int(regexGroup(1, in: masked, match: colonMatch))
            let m = Int(regexGroup(2, in: masked, match: colonMatch))
            if let h, (0...23).contains(h), let m, (0...59).contains(m) {
                rawHour = h
                rawMinute = m
                isExplicit24h = (h >= 13)
                rangesToRemove.append(colonMatch.range)
                masked = maskRange(masked, colonMatch.range)
            }
        }

        if rawHour == nil {
            let spaceTimePattern = #"\b(?:saat\s+)?(\d{1,2})\s+(\d{2})(?:\s*['’\`]?\s*(?:da|de|ta|te))\b"#
            if let spaceMatch = regexFirstMatch(in: masked, pattern: spaceTimePattern) {
                let h = Int(regexGroup(1, in: masked, match: spaceMatch))
                let m = Int(regexGroup(2, in: masked, match: spaceMatch))
                if let h, (0...23).contains(h), let m, (0...59).contains(m) {
                    rawHour = h
                    rawMinute = m
                    isExplicit24h = (h >= 13)
                    rangesToRemove.append(spaceMatch.range)
                    masked = maskRange(masked, spaceMatch.range)
                }
            }
        }

        if rawHour == nil {
            let bucukPattern = #"\b(?:saat\s+)?(\d{1,2})\s+(?:buçuk|bucuk)(?:['’\`]?\s*(?:ta|te|da|de))?\b"#
            if let bucukMatch = regexFirstMatch(in: masked, pattern: bucukPattern) {
                if let h = Int(regexGroup(1, in: masked, match: bucukMatch)), (0...23).contains(h) {
                    rawHour = h
                    rawMinute = 30
                    rangesToRemove.append(bucukMatch.range)
                    masked = maskRange(masked, bucukMatch.range)
                }
            }
        }

        if rawHour == nil {
            let saatHourPattern = #"\bsaat\s+(\d{1,2})(?:['’\`]?\s*(?:da|de|ta|te)|\s+(?:da|de|ta|te))?\b"#
            if let saatMatch = regexFirstMatch(in: masked, pattern: saatHourPattern) {
                if let h = Int(regexGroup(1, in: masked, match: saatMatch)), (0...23).contains(h) {
                    rawHour = h
                    rawMinute = 0
                    isExplicit24h = (h >= 13)
                    rangesToRemove.append(saatMatch.range)
                    masked = maskRange(masked, saatMatch.range)
                }
            }
        }

        if rawHour == nil {
            let suffixedHourPattern = #"\b(\d{1,2})(?:['’\`]\s*(?:da|de|ta|te)|(?:ta|te|da|de)|\s+(?:da|de|ta|te))\b"#
            if let suffixedMatch = regexFirstMatch(in: masked, pattern: suffixedHourPattern) {
                if let h = Int(regexGroup(1, in: masked, match: suffixedMatch)), (0...23).contains(h) {
                    rawHour = h
                    rawMinute = 0
                    isExplicit24h = (h >= 13)
                    rangesToRemove.append(suffixedMatch.range)
                    masked = maskRange(masked, suffixedMatch.range)
                }
            }
        }

        if rawHour == nil {
            let wordHourPattern = #"\b(?:saat\s+)?(bir|iki|üç|uc|dört|dort|beş|bes|altı|alti|yedi|sekiz|dokuz|on|on bir|on iki)(?:['’\`]?\s*(?:da|de|ta|te))?\b"#
            if let wordMatch = regexFirstMatch(in: masked, pattern: wordHourPattern) {
                let matchedStr = (masked as NSString).substring(with: wordMatch.range)
                if matchedStr.contains("saat") || regexContainsMatch(in: matchedStr, pattern: #"(?:da|de|ta|te)"#) {
                    let word = regexGroup(1, in: masked, match: wordMatch)
                    rawHour = wordNumbers[word]
                    rawMinute = 0
                    rangesToRemove.append(wordMatch.range)
                    masked = maskRange(masked, wordMatch.range)
                }
            }
        }

        if rawHour == nil && timeOfDay != nil {
            let bareWordPattern = #"\b(bir|iki|üç|uc|dört|dort|beş|bes|altı|alti|yedi|sekiz|dokuz|on|on bir|on iki)\b"#
            if let bareWordMatch = regexFirstMatch(in: masked, pattern: bareWordPattern) {
                let word = regexGroup(1, in: masked, match: bareWordMatch)
                rawHour = wordNumbers[word]
                rawMinute = 0
                rangesToRemove.append(bareWordMatch.range)
                masked = maskRange(masked, bareWordMatch.range)
            } else {
                let bareDigitPattern = #"\b([1-9]|1[0-2])\b"#
                if let bareDigitMatch = regexFirstMatch(in: masked, pattern: bareDigitPattern) {
                    rawHour = Int(regexGroup(1, in: masked, match: bareDigitMatch))
                    rawMinute = 0
                    rangesToRemove.append(bareDigitMatch.range)
                    masked = maskRange(masked, bareDigitMatch.range)
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
                    d = specificDateMatch != nil
                        ? plusYears(d, 1, calendar: calendar)
                        : plusDays(d, 1, calendar: calendar)
                }
            } else {
                if d < today {
                    d = specificDateMatch != nil
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
            var daysAhead = (weekday.rawValue - nowWeekday.rawValue + 7) % 7
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

        let cleaned = cleanReminderText(original, rangesToRemove)
        let finalText = cleaned.isEmpty ? "Hatırlatıcı" : cleaned

        return ParsedTypedReminder(
            originalText: original,
            text: finalText,
            alertAt: alertAt,
            repeatRule: repeatRule,
            requiresTimeSelection: requiresTimeSelection,
            dateHint: dateHint,
            timeOfDay: timeOfDay
        )
    }

    private static func daypartFromWord(_ word: String) -> ComposerTimeOfDay? {
        if word.hasPrefix("sabah") { return .morning }
        if word.hasPrefix("öğl") || word.hasPrefix("ogl") { return .noon }
        if word.hasPrefix("akş") || word.hasPrefix("aks") { return .evening }
        if word.hasPrefix("gece") { return .night }
        return nil
    }

    private static func maskRange(_ input: String, _ range: NSRange) -> String {
        guard range.location != NSNotFound, range.length > 0 else { return input }
        let ns = input as NSString
        guard range.location + range.length <= ns.length else { return input }
        let spaces = String(repeating: " ", count: range.length)
        return ns.replacingCharacters(in: range, with: spaces)
    }

    static func cleanReminderText(_ original: String, _ ranges: [NSRange]) -> String {
        if ranges.isEmpty {
            return formatText(original)
        }

        let sorted = ranges
            .filter { $0.location != NSNotFound && $0.length > 0 }
            .sorted { $0.location < $1.location }

        var merged: [NSRange] = []
        for r in sorted {
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
        let totalLength = nsOriginal.length
        var sb = ""
        var lastIdx = 0
        for r in merged {
            if r.location > lastIdx {
                let endIdx = min(r.location, totalLength)
                sb += nsOriginal.substring(with: NSRange(location: lastIdx, length: endIdx - lastIdx))
            }
            lastIdx = max(lastIdx, r.location + r.length)
        }
        if lastIdx < totalLength {
            sb += nsOriginal.substring(with: NSRange(location: lastIdx, length: totalLength - lastIdx))
        }

        return formatText(sb)
    }

    private static let trimPunctuationSet = CharacterSet(charactersIn: ",.;:-’'")

    private static func trimEdgePunctuationAndSpaces(_ s: String) -> String {
        s.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: trimPunctuationSet)
    }

    private static func formatText(_ raw: String) -> String {
        var clean = regexReplace(
            in: trimEdgePunctuationAndSpaces(raw),
            pattern: #"\s+"#,
            with: " "
        ).trimmingCharacters(in: .whitespacesAndNewlines)

        clean = stripBoundaryFillerYa(clean)

        let hasCoordinatedClause = regexContainsMatch(
            in: clean,
            pattern: #"(?i)(?:\b(?:ve|veya|ya\s+da|ile)\b|[,;])\s+.*?\b(?:hatırlat|hatirlat)\s*[.!]?$"#
        )
        if !hasCoordinatedClause {
            clean = trimEdgePunctuationAndSpaces(
                regexReplace(
                    in: clean,
                    pattern: #"(?i)\s*(?:bana\s+)?(?:hatırlat|hatirlat)\s*[.!]?$"#,
                    with: ""
                )
            )
        } else {
            clean = trimEdgePunctuationAndSpaces(
                regexReplace(
                    in: clean,
                    pattern: #"(?i)\s+\bbana\s+(?:hatırlat|hatirlat)\s*[.!]?$"#,
                    with: ""
                )
            )
        }

        clean = stripBoundaryFillerYa(clean)

        clean = trimEdgePunctuationAndSpaces(
            regexReplace(in: clean, pattern: #"(?i)\bher\b"#, with: "")
        )

        clean = regexReplace(in: clean, pattern: #"\s+"#, with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        let words = clean.split(separator: " ")
            .map(String.init)
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        if let lastWord = words.last {
            let normalized = normalizeVerb(lastWord)
            if normalized != lastWord {
                clean = (words.dropLast() + [normalized]).joined(separator: " ")
            }
        }

        return clean.isEmpty ? "" : turkishTitlecase(clean)
    }

    static func formatPendingScheduleLabel(
        _ dateHint: Date?,
        _ repeatRule: RecurrenceRule,
        _ timeOfDay: ComposerTimeOfDay? = nil,
        now: Date = Date(),
        calendar: Calendar = .real
    ) -> String {
        REAL.formatPendingScheduleLabel(dateHint, repeatRule, timeOfDay, now: now, calendar: calendar)
    }

    static func formatComposerScheduleLabel(
        _ alertAt: Date,
        _ repeatRule: RecurrenceRule,
        now: Date = Date(),
        calendar: Calendar = .real
    ) -> String {
        REAL.formatComposerScheduleLabel(alertAt, repeatRule, now: now, calendar: calendar)
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

    private static func lengthOfMonth(year: Int, month: Int, calendar: Calendar) -> Int {
        guard let firstOfMonth = calendar.date(from: DateComponents(year: year, month: month, day: 1)),
              let range = calendar.range(of: .day, in: .month, for: firstOfMonth) else {
            return 30
        }
        return range.count
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

func formatPendingScheduleLabel(
    _ dateHint: Date?,
    _ repeatRule: RecurrenceRule,
    _ timeOfDay: ComposerTimeOfDay? = nil,
    now: Date = Date(),
    calendar: Calendar = .real
) -> String {
    let today = calendar.startOfDay(for: now)
    let tomorrow = calendar.date(byAdding: .day, value: 1, to: today).map { calendar.startOfDay(for: $0) } ?? today
    let hintDay = dateHint.map { calendar.startOfDay(for: $0) }

    let daypartStr: String? = switch timeOfDay {
    case .morning: "sabah"
    case .noon: "öğlen"
    case .evening: "akşam"
    case .night: "gece"
    case nil: nil
    }

    let schedulePart: String
    if repeatRule == .daily {
        schedulePart = daypartStr != nil ? "Her \(daypartStr!)" : "Her gün"
    } else if repeatRule == .weekly, let hintDay {
        let weekdayName = turkishWeekdayName(calendar.isoWeekday(of: hintDay))
        schedulePart = daypartStr != nil ? "\(weekdayName) \(daypartStr!)" : weekdayName
    } else if repeatRule == .weekly {
        return "Her hafta · Tarih ve saat seç"
    } else if repeatRule == .monthly, let hintDay {
        let dom = calendar.component(.day, from: hintDay)
        schedulePart = daypartStr != nil ? "Her ayın \(dom)'i \(daypartStr!)" : "Her ayın \(dom)'i"
    } else if repeatRule == .monthly {
        return "Her ay · Tarih ve saat seç"
    } else if let hintDay {
        let dateLabel: String
        if hintDay == today {
            dateLabel = "Bugün"
        } else if hintDay == tomorrow {
            dateLabel = "Yarın"
        } else {
            let days = calendar.dateComponents([.day], from: today, to: hintDay).day ?? 0
            if (2...6).contains(days) {
                dateLabel = turkishWeekdayName(calendar.isoWeekday(of: hintDay))
            } else {
                let df = DateFormatter()
                df.locale = trLocale
                df.timeZone = calendar.timeZone
                df.dateFormat = "d MMMM"
                dateLabel = df.string(from: hintDay)
            }
        }
        schedulePart = daypartStr != nil ? "\(dateLabel) \(daypartStr!)" : dateLabel
    } else {
        schedulePart = ""
    }

    if !schedulePart.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        return "\(schedulePart) · Saat seç"
    } else {
        return "Saat seç"
    }
}

func formatComposerScheduleLabel(
    _ alertAt: Date,
    _ repeatRule: RecurrenceRule,
    now: Date = Date(),
    calendar: Calendar = .real
) -> String {
    let today = calendar.startOfDay(for: now)
    let tomorrow = calendar.date(byAdding: .day, value: 1, to: today).map { calendar.startOfDay(for: $0) } ?? today
    let alertDay = calendar.startOfDay(for: alertAt)
    let hour = calendar.component(.hour, from: alertAt)
    let minute = calendar.component(.minute, from: alertAt)
    let timeStr = String(format: "%02d:%02d", hour, minute)

    let dateStr: String
    if alertDay == today {
        dateStr = "Bugün"
    } else if alertDay == tomorrow {
        dateStr = "Yarın"
    } else if repeatRule == .weekly {
        dateStr = turkishWeekdayName(calendar.isoWeekday(of: alertAt))
    } else {
        let df = DateFormatter()
        df.locale = trLocale
        df.timeZone = calendar.timeZone
        df.dateFormat = "d MMM"
        dateStr = df.string(from: alertAt)
    }

    switch repeatRule {
    case .none: return "\(dateStr) \(timeStr)"
    case .daily: return "\(dateStr) \(timeStr) · Her gün"
    case .weekly: return "\(dateStr) \(timeStr) · Her hafta"
    case .monthly: return "\(dateStr) \(timeStr) · Her ay"
    case .yearly: return "\(dateStr) \(timeStr) · Her yıl"
    }
}

private func turkishWeekdayName(_ weekday: Weekday) -> String {
    switch weekday {
    case .monday: return "Pazartesi"
    case .tuesday: return "Salı"
    case .wednesday: return "Çarşamba"
    case .thursday: return "Perşembe"
    case .friday: return "Cuma"
    case .saturday: return "Cumartesi"
    case .sunday: return "Pazar"
    }
}

// MARK: - NSRegularExpression UTF-16 helpers

func regexFirstMatch(in text: String, pattern: String) -> NSTextCheckingResult? {
    guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
    let ns = text as NSString
    return regex.firstMatch(in: text, range: NSRange(location: 0, length: ns.length))
}

func regexContainsMatch(in text: String, pattern: String) -> Bool {
    regexFirstMatch(in: text, pattern: pattern) != nil
}

func regexGroup(_ index: Int, in text: String, match: NSTextCheckingResult) -> String {
    guard index < match.numberOfRanges else { return "" }
    let range = match.range(at: index)
    guard range.location != NSNotFound else { return "" }
    return (text as NSString).substring(with: range)
}

func regexReplace(in text: String, pattern: String, with template: String) -> String {
    guard let regex = try? NSRegularExpression(pattern: pattern) else { return text }
    let ns = text as NSString
    return regex.stringByReplacingMatches(in: text, range: NSRange(location: 0, length: ns.length), withTemplate: template)
}

func regexSplit(in text: String, pattern: String) -> [String] {
    guard let regex = try? NSRegularExpression(pattern: pattern) else { return [text] }
    let ns = text as NSString
    let matches = regex.matches(in: text, range: NSRange(location: 0, length: ns.length))
    if matches.isEmpty { return [text] }

    var parts: [String] = []
    var lastIdx = 0
    for m in matches {
        let r = m.range
        if r.location >= lastIdx {
            parts.append(ns.substring(with: NSRange(location: lastIdx, length: r.location - lastIdx)))
            lastIdx = r.location + r.length
        }
    }
    if lastIdx <= ns.length {
        parts.append(ns.substring(with: NSRange(location: lastIdx, length: ns.length - lastIdx)))
    }
    return parts
}
