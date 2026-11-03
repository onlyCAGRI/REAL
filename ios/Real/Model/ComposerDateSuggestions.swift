import Foundation

/// One way to read a date typed into the composer ("yarın 9'da" → Yarın 09:00 or Yarın 21:00).
/// Picking it puts the date, time and repeat on the card and leaves [text], the typed words without the date phrase.
struct ComposerDateSuggestion: Hashable {
    var label: String
    var text: String
    var alertAt: Date?
    var repeatRule: RecurrenceRule = .none
    var dateHint: Date? = nil
    var timeOfDay: ComposerTimeOfDay? = nil
}

private let trLocale = Locale(identifier: "tr_TR")

/// The voice parser run on typed text. A bare hour (1–11 without sabah/akşam, or a 12-hour reading of 13–23) gives
/// both readings, morning first; a written 24-hour time, a daypart or "… sonra" gives one. Suggestions already on
/// the card ([alertAt]/[dateHint]) are left out.
func composerDateSuggestions(
    _ text: String,
    alertAt: Date? = nil,
    dateHint: Date? = nil,
    now: Date = Date(),
    calendar: Calendar = .real
) -> [ComposerDateSuggestion] {
    if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return [] }
    let trParsed = TypedDateParser.parse(text, now: now, calendar: calendar)
    let parsed = (trParsed.alertAt != nil || trParsed.dateHint != nil)
        ? trParsed
        : EnglishDateParser.parse(text, now: now, calendar: calendar)
    if parsed.alertAt == nil && parsed.dateHint == nil { return [] }

    // A checklist split rewrites the text; then only the date is taken and the words stay as typed.
    // Nothing left but the date: the field empties (the parser's "Hatırlatıcı" stand-in is for voice); Enter then
    // asks for the card's words.
    let trimmedParsedText = parsed.text.trimmingCharacters(in: .whitespacesAndNewlines)
    let parsedText: String
    if trimmedParsedText == "Hatırlatıcı" && text.range(of: "Hatırlatıcı", options: .caseInsensitive) == nil {
        parsedText = ""
    } else {
        parsedText = trimmedParsedText
    }
    let rest = parsed.isChecklist ? text.trimmingCharacters(in: .whitespacesAndNewlines) : parsedText
    let lower = text.lowercased(with: trLocale)
    let lowerEn = text.lowercased(with: Locale(identifier: "en_US_POSIX"))

    let today = calendar.startOfDay(for: now)
    var out: [ComposerDateSuggestion] = []

    if let at = parsed.alertAt {
        let hour = calendar.component(.hour, from: at)
        let relative = regexContainsMatch(in: lower, pattern: #"\bsonra\b"#)
            || regexContainsMatch(in: lowerEn, pattern: #"\bin\s+(?:a\s+half|half\s+an|\d+|an|a|one)\s+(?:hours?|hrs?|minutes?|mins?)\b"#)
        let written24 = hour >= 13 && regexContainsMatch(in: lower, pattern: "(?<!\\d)\(hour)(?!\\d)")
        let writtenMorning = (1...9).contains(hour) && regexContainsMatch(in: lower, pattern: "(?<!\\d)0\(hour)(?!\\d)")
        let ambiguous = parsed.timeOfDay == nil && !relative && !written24 && !writtenMorning && hour != 0 && hour != 12

        var readings: [Date] = [at]
        if ambiguous {
            var comps = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second, .nanosecond], from: at)
            comps.hour = (hour + 12) % 24
            var other = calendar.date(from: comps) ?? at
            if !(other > now) {
                switch parsed.repeatRule {
                case .daily:
                    other = calendar.date(byAdding: .day, value: 1, to: other) ?? other
                case .weekly:
                    other = calendar.date(byAdding: .day, value: 7, to: other) ?? other
                case .monthly:
                    other = calendar.date(byAdding: .month, value: 1, to: other) ?? other
                case .yearly:
                    other = calendar.date(byAdding: .year, value: 1, to: other) ?? other
                case .none:
                    break
                }
            }
            if other > now {
                readings.append(other)
            }
        }

        let sortedReadings = readings.enumerated().sorted { lhs, rhs in
            let lh = calendar.component(.hour, from: lhs.element)
            let rh = calendar.component(.hour, from: rhs.element)
            return lh != rh ? lh < rh : lhs.offset < rhs.offset
        }.map(\.element)

        for reading in sortedReadings {
            if reading.epochMillis != alertAt?.epochMillis {
                let rHour = calendar.component(.hour, from: reading)
                let rMinute = calendar.component(.minute, from: reading)
                let timeStr = String(format: "%02d:%02d", rHour, rMinute)
                let readingDay = calendar.startOfDay(for: reading)
                out.append(
                    ComposerDateSuggestion(
                        label: scheduleLabel(readingDay, parsed.repeatRule, today, timeStr, calendar: calendar),
                        text: rest,
                        alertAt: reading,
                        repeatRule: parsed.repeatRule
                    )
                )
            }
        }
    } else if let rawDay = parsed.dateHint {
        let day = calendar.startOfDay(for: rawDay)
        let currentHintDay = dateHint.map { calendar.startOfDay(for: $0) }
        if day != currentHintDay || alertAt != nil {
            let daypart: String? = switch parsed.timeOfDay {
            case .morning: t("sabah")
            case .noon: t("öğlen")
            case .evening: t("akşam")
            case .night: t("gece")
            case nil: nil
            }
            out.append(
                ComposerDateSuggestion(
                    label: scheduleLabel(day, parsed.repeatRule, today, daypart, calendar: calendar),
                    text: rest,
                    alertAt: nil,
                    repeatRule: parsed.repeatRule,
                    dateHint: day,
                    timeOfDay: parsed.timeOfDay
                )
            )
        }
    }

    return out
}

private func weekdayName(_ day: Weekday) -> String {
    let key: String = switch day {
    case .monday: "Pazartesi"
    case .tuesday: "Salı"
    case .wednesday: "Çarşamba"
    case .thursday: "Perşembe"
    case .friday: "Cuma"
    case .saturday: "Cumartesi"
    case .sunday: "Pazar"
    }
    return t(key)
}

/// "Yarın 09:00", "Cumartesi 18:00", "Her Cumartesi 06:00", "Her gün 21:00", "12 Kas akşam".
private func scheduleLabel(
    _ day: Date,
    _ repeatRule: RecurrenceRule,
    _ today: Date,
    _ time: String?,
    calendar: Calendar
) -> String {
    let dayPart: String
    switch repeatRule {
    case .daily:
        dayPart = t("Her gün")
    case .weekly:
        dayPart = t("Her {0}", weekdayName(calendar.isoWeekday(of: day)))
    case .monthly:
        dayPart = t("Her ay")
    case .yearly:
        dayPart = t("Her yıl")
    case .none:
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: today).map { calendar.startOfDay(for: $0) } ?? today
        let plus7 = calendar.date(byAdding: .day, value: 7, to: today).map { calendar.startOfDay(for: $0) } ?? today
        if day == today {
            dayPart = t("Bugün")
        } else if day == tomorrow {
            dayPart = t("Yarın")
        } else if day > today && day < plus7 {
            dayPart = weekdayName(calendar.isoWeekday(of: day))
        } else {
            let df = DateFormatter()
            df.locale = I18n.locale
            df.timeZone = calendar.timeZone
            df.dateFormat = "d MMM"
            dayPart = df.string(from: day)
        }
    }
    return time == nil ? dayPart : "\(dayPart) \(time!)"
}
