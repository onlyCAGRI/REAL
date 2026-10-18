import Foundation
import UIKit

/// GeminiVoiceIntentClient.kt (first iOS step): a spoken composer command goes to /api/voice-intent (Groq via the Worker)
/// and comes back as one or more cards with date, time, repeat, category, checklist and a verified song / title.
/// Spoken edits and bulk actions ("onu 8'e al", "bugünküleri sil") are not applied on iOS yet: those keep the
/// transcript in the composer, as before.
@MainActor
enum VoiceAI {
    enum Outcome {
        /// Cards were created.
        case created(Int)
        /// One card needs a person's look first (a clock to pick, or a time already past): fill the composer.
        case draft(title: String, schedule: ScheduleDraft, categoryId: Int64?, checklist: [String])
        /// Nothing usable (not signed in, no consent, offline, an edit command…): keep the transcript.
        case none(String?)
        /// "Doğru mu anladım?": nothing is saved until the person confirms (VoiceConfirmSheet), then commit(...).
        case confirm(Pending)
    }

    /// A parsed command waiting for "Doğru mu anladım?".
    struct Pending: Identifiable {
        let id = UUID()
        fileprivate let intents: [Intent]
        let transcript: String
        /// The sentence shown, and the words underlined in it.
        let display: String
        let underlined: [String]
        /// The cards' place, when there is one ("Yeri yaz").
        let place: String?
    }

    fileprivate struct Intent {
        var title: String
        var date: Date?
        var hour: Int?
        var minute: Int?
        var recurrence: RecurrenceRule
        var weekdaysMask: Int
        var until: Date?
        var requiresTime: Bool
        var timeOfDay: String
        var durationMinutes: Int?
        var categoryName: String?
        var checklist: [String]
        var music: MoodSuggestion.Music?
        var media: (MoodSuggestion.Media, MediaType)?
        var locationName: String?
    }

    static func interpret(_ transcript: String, store: ReminderStore) async -> Outcome {
        let spoken = transcript.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !spoken.isEmpty, RealAuth.shared.account != nil else { return .none(nil) }
        do {
            try await AiConsent.require(.voice)
        } catch {
            return .none(nil)
        }
        let intents: [Intent]
        let heard: VoiceHeard?
        do {
            let fetched = try await fetch(spoken, categories: store.categories.map(\.name), store: store)
            intents = fetched.0
            heard = fetched.1
        } catch {
            return .none(RealBackend.message(for: error))
        }
        guard !intents.isEmpty else { return .none(nil) }
        if let pending = check(spoken, intents: intents, heard: heard) { return .confirm(pending) }
        return await create(intents, store: store)
    }

    /// "Doğru": save what was understood. [place]: the place the person typed instead ("Yeri yaz").
    static func commit(_ pending: Pending, place: String?, store: ReminderStore) async -> Outcome {
        var intents = pending.intents
        if let place, !place.isEmpty {
            let oldName = (pending.place ?? "").split(separator: ",").first.map { $0.trimmingCharacters(in: .whitespaces) } ?? ""
            let newName = place.split(separator: ",").first.map { $0.trimmingCharacters(in: .whitespaces) } ?? place
            intents = intents.map { intent in
                guard intent.locationName?.isEmpty == false else { return intent }
                var fixed = intent
                fixed.locationName = place
                if oldName.count >= 2 { fixed.title = fixed.title.replacingOccurrences(of: oldName, with: newName, options: .caseInsensitive) }
                return fixed
            }
        }
        return await create(intents, store: store)
    }

    /// Ask when the AI was unsure, or when a card names something that was never said.
    private static func check(_ transcript: String, intents: [Intent], heard: VoiceHeard?) -> Pending? {
        let place = intents.compactMap(\.locationName).first { !$0.isEmpty }
        if let heard, !heard.confident {
            var display = transcript
            for word in heard.unsure { if let meant = word.meant { display = display.replacingOccurrences(of: word.heard, with: meant, options: .caseInsensitive) } }
            return Pending(intents: intents, transcript: transcript, display: display,
                           underlined: heard.unsure.map { $0.meant ?? $0.heard }, place: place)
        }
        let names = VoiceSubstitutions.names(transcript: transcript, titles: intents.map(\.title),
                                             places: intents.compactMap(\.locationName),
                                             content: intents.flatMap { [$0.media?.0.title, $0.music?.artist, $0.music?.track].compactMap { $0 } })
        guard !names.isEmpty else { return nil }
        let understood = heard?.understood ?? ""
        let display = !understood.isEmpty && names.allSatisfy({ understood.localizedCaseInsensitiveContains($0) }) ? understood
            : intents.map { [$0.title, $0.locationName].compactMap { $0 }.joined(separator: " — ") }.joined(separator: " · ")
        return Pending(intents: intents, transcript: transcript, display: display, underlined: names, place: place)
    }

    private static func create(_ intents: [Intent], store: ReminderStore) async -> Outcome {

        // A single card that still needs a clock, or whose one-off time has already passed, goes to the composer.
        if intents.count == 1, let only = intents.first {
            let schedule = scheduleDraft(for: only)
            let past = schedule.alertAt.map { $0 < EpochMillis.now } ?? false
            if only.requiresTime || (past && schedule.repeatRule == .none) {
                return .draft(title: only.title, schedule: schedule, categoryId: category(named: only.categoryName, in: store),
                              checklist: only.checklist)
            }
        }

        var created = 0
        for intent in intents.prefix(20) {
            let schedule = scheduleDraft(for: intent)
            var music: MusicBlock?
            var media: MediaBlock?
            if let candidate = intent.music { music = await MoodSuggestionClient.resolveMusic(candidate) }
            if let wanted = intent.media {
                let candidate = wanted.0
                media = await MediaCatalog.resolve(title: candidate.title.isEmpty ? candidate.searchQuery : candidate.title,
                                                   year: candidate.year, type: wanted.1)
            }
            // The place with its address and map position, found right away.
            var location: ReminderLocation?
            if let place = intent.locationName, !place.isEmpty { location = await VoicePlaceLookup.resolve(place) }
            store.addReminder(text: intent.title, alertAt: schedule.alertAt, plannedDay: schedule.plannedDay,
                              repeatRule: schedule.repeatRule, categoryId: category(named: intent.categoryName, in: store),
                              checklist: intent.checklist) { item in
                schedule.apply(to: &item)
                if let music { item.musicBlocks = [music] }
                if let media { item.mediaBlocks = [media] }
                if let location { item.location = location }
            }
            created += 1
        }
        if intents.contains(where: { $0.hour != nil }) { NotificationScheduler.requestAuthorization() }
        return .created(created)
    }

    // MARK: Request

    private static func fetch(_ transcript: String, categories: [String], store: ReminderStore) async throws -> ([Intent], VoiceHeard?) {
        let now = Date()
        let iso = ISO8601DateFormatter()
        iso.timeZone = .current
        iso.formatOptions = [.withInternetDateTime]
        let body: [String: Any] = [
            "source": "voice_composer",
            "maxCreationItems": 20,
            "transcript": String(transcript.prefix(2000)),
            "transcriptAlternatives": [String](),
            "now": iso.string(from: now),
            "timezone": TimeZone.current.identifier,
            "locale": Locale.current.identifier.replacingOccurrences(of: "_", with: "-"),
            "categories": Array(categories.prefix(50)),
        ]
        let json = try await RealBackend.post("/api/voice-intent", body, timeout: 15)
        guard json["ok"] as? Bool == true else { throw RealBackend.Failure(status: -1) }
        let list = (json["intents"] as? [[String: Any]]) ?? ((json["intent"] as? [String: Any]).map { [$0] } ?? [])
        return (list.prefix(20).map { parse($0, fallback: transcript, today: now) },
                VoiceHeard.parse(json["heard"] as? [String: Any], transcript: transcript))
    }

    private static func parse(_ o: [String: Any], fallback: String, today: Date) -> Intent {
        let cal = Calendar.real
        func string(_ key: String) -> String { ((o[key] as? String) ?? "").trimmingCharacters(in: .whitespaces) }
        func day(_ raw: String) -> Date? {
            let parts = raw.split(separator: "-").compactMap { Int($0) }
            guard parts.count == 3 else { return nil }
            return cal.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
        }
        var mask = 0
        for d in (o["weekdays"] as? [String]) ?? [] {
            if let bit = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"].firstIndex(of: d.uppercased()) { mask |= 1 << bit }
        }
        let clock = string("time").split(separator: ":").compactMap { Int($0) }
        var recurrence: RecurrenceRule = mask != 0 ? .weekly : {
            switch string("recurrence").uppercased() {
            case "DAILY": return .daily
            case "WEEKLY": return .weekly
            case "MONTHLY": return .monthly
            case "YEARLY": return .yearly
            default: return .none
            }
        }()
        let date = day(string("date")) ?? (mask != 0 ? cal.startOfDay(for: today) : nil)
        var until = day(string("repeatUntil"))
        // repeatPeriod {count, unit}: a bounded series ("sadece 1 ay") ends the day before the period is over.
        if until == nil, let period = o["repeatPeriod"] as? [String: Any], let count = period["count"] as? Int, count > 0 {
            let start = date ?? cal.startOfDay(for: today)
            let unit: Calendar.Component? = ["DAY": .day, "WEEK": .weekOfYear, "MONTH": .month, "YEAR": .year][(period["unit"] as? String ?? "").uppercased()]
            if let unit, let end = cal.date(byAdding: unit, value: count, to: start) { until = cal.date(byAdding: .day, value: -1, to: end) }
        }
        if recurrence == .none { until = nil }
        let items = ((o["checklistItems"] as? [String]) ?? []).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        let attach = string("attachKind").uppercased()
        var music: MoodSuggestion.Music?
        if attach == "MUSIC", let m = o["music"] as? [String: Any] {
            let artist = ((m["artist"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            let track = ((m["track"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            var q = ((m["searchQuery"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            if q.isEmpty { q = [artist, track].filter { !$0.isEmpty }.joined(separator: " ") }
            if !q.isEmpty { music = .init(artist: artist, track: track, searchQuery: q) }
        }
        var media: (MoodSuggestion.Media, MediaType)?
        if attach == "MOVIE" || attach == "TV", let m = o["media"] as? [String: Any] {
            let t = ((m["title"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            let y = (m["year"] as? Int).flatMap { $0 > 0 ? $0 : nil }
            let q = ((m["searchQuery"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            if !t.isEmpty || !q.isEmpty { media = (.init(title: t, year: y, searchQuery: q.isEmpty ? t : q), attach == "TV" ? .TV : .MOVIE) }
        }
        let title = string("title")
        let checklistOK = (o["isChecklist"] as? Bool) == true && !items.isEmpty
        if clock.count != 2 && recurrence != .none && date == nil { recurrence = .none }
        return Intent(title: title.isEmpty ? fallback : String(title.prefix(300)), date: date,
                      hour: clock.count == 2 ? clock[0] : nil, minute: clock.count == 2 ? clock[1] : nil,
                      recurrence: recurrence, weekdaysMask: mask, until: until,
                      requiresTime: (o["requiresTimeSelection"] as? Bool) == true, timeOfDay: string("timeOfDay").uppercased(),
                      durationMinutes: (o["durationMinutes"] as? Int).flatMap { (1...1440).contains($0) ? $0 : nil },
                      categoryName: string("categoryName").isEmpty ? nil : string("categoryName"),
                      checklist: checklistOK ? Array(items.prefix(30)) : [], music: music, media: media,
                      locationName: string("locationName").isEmpty ? nil : String(string("locationName").prefix(180)))
    }

    // MARK: Mapping

    private static func scheduleDraft(for intent: Intent) -> ScheduleDraft {
        let cal = Calendar.real
        var draft = ScheduleDraft()
        if let hour = intent.hour, let minute = intent.minute {
            let base = intent.date ?? cal.startOfDay(for: Date())
            draft.date = cal.date(bySettingHour: hour, minute: minute, second: 0, of: base)
            draft.hasTime = true
        } else if intent.requiresTime {
            // A daypart without a clock: a sensible starting point the person adjusts in the composer.
            let hour = ["MORNING": 9, "NOON": 12, "EVENING": 19, "NIGHT": 21][intent.timeOfDay] ?? 9
            draft.date = cal.date(bySettingHour: hour, minute: 0, second: 0, of: intent.date ?? cal.startOfDay(for: Date()))
            draft.hasTime = true
        } else if let date = intent.date {
            draft.date = cal.startOfDay(for: date)
            draft.hasTime = false
        }
        draft.repeatRule = draft.hasTime && draft.date != nil ? intent.recurrence : .none
        draft.weekdaysMask = draft.repeatRule == .weekly ? intent.weekdaysMask : 0
        draft.until = draft.repeatRule == .none ? nil : intent.until
        draft.durationMinutes = draft.hasTime ? intent.durationMinutes : nil
        return draft
    }

    private static func category(named name: String?, in store: ReminderStore) -> Int64? {
        guard let name, !name.isEmpty else { return nil }
        let key = name.lowercased(with: Locale(identifier: "tr_TR"))
        return store.categories.first { $0.name.lowercased(with: Locale(identifier: "tr_TR")) == key }?.id
    }
}
