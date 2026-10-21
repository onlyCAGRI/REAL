import Foundation

/// Milliseconds since 1970, same unit the Android app stores, so payloads stay interchangeable.
typealias EpochMillis = Int64
/// Days since 1970-01-01 (java.time.LocalDate.toEpochDay).
typealias EpochDay = Int64

enum AttachmentType: String, Codable { case image = "IMAGE", file = "FILE", textSnippet = "TEXT_SNIPPET" }

struct ReminderAttachment: Codable, Identifiable, Hashable {
    var id: String
    var localPath: String = ""
    var type: AttachmentType = .image
    var mimeType: String? = nil
    var displayName: String? = nil
    var sizeBytes: Int64? = nil
    var text: String? = nil
    /// "REAL_TEXT_BLOCK" marks a standalone text box added from the edit dock.
    var sourceTitle: String? = nil
    /// Text snippet origin (Android SnippetSource): "WHATSAPP_TEXT", "EMAIL_TEXT", "PLAIN_TEXT". Kept so an edit here
    /// never drops what Android set.
    var source: String? = nil
}

enum JournalMood: String, Codable, CaseIterable, Identifiable {
    case veryGood = "very_good", good, neutral, hard, bad, happy, excited, calm, tired, bored, stressed, angry, sad, low
    case fuckOff = "fuck_off"

    var id: String { rawValue }
    var label: String {
        switch self {
        case .veryGood: return "Çok iyi"
        case .good: return "İyi"
        case .neutral: return "Normal"
        case .hard: return "Zor"
        case .bad: return "Kötü"
        case .happy: return "Mutlu"
        case .excited: return "Heyecanlı"
        case .calm: return "Sakin"
        case .tired: return "Yorgun"
        case .bored: return "Sıkılmış"
        case .stressed: return "Stresli"
        case .angry: return "Sinirli"
        case .sad: return "Üzgün"
        case .low: return "Keyifsiz"
        case .fuckOff: return "F*ck off"
        }
    }
    var emoji: String {
        switch self {
        case .veryGood: return "😄"
        case .good: return "🙂"
        case .neutral: return "😐"
        case .hard: return "😣"
        case .bad: return "☹️"
        case .happy: return "😊"
        case .excited: return "🤩"
        case .calm: return "😌"
        case .tired: return "🥱"
        case .bored: return "😑"
        case .stressed: return "😰"
        case .angry: return "😠"
        case .sad: return "😢"
        case .low: return "😔"
        case .fuckOff: return "🖕"
        }
    }
}

struct ReminderJournal: Codable, Hashable {
    var mood: JournalMood?
    var text: String
    var createdAt: EpochMillis
    var updatedAt: EpochMillis
}

struct ChecklistItem: Codable, Identifiable, Hashable {
    var id: Int64
    var text: String
    var completedAt: EpochMillis? = nil

    var isCompleted: Bool { completedAt != nil }

    func toggled(now: EpochMillis = .now) -> ChecklistItem {
        var copy = self
        copy.completedAt = isCompleted ? nil : now
        return copy
    }
}

struct ReminderLocation: Codable, Hashable {
    var placeName: String
    var address: String = ""
    var latitude: Double? = nil
    var longitude: Double? = nil
    var mapsUrl: String? = nil
}

/// "Konuma varınca hatırlat" (LocationTrigger.kt): fires once on arriving at / leaving the place, then is cleared.
struct LocationTrigger: Codable, Hashable {
    var placeName: String
    var latitude: Double
    var longitude: Double
    var radiusMeters: Int = LocationTrigger.defaultRadius
    var onExit: Bool = false

    /// "Normal"; the composer map uses `preciseRadius` ("Hassas").
    static let defaultRadius = 250
    static let preciseRadius = 100
    static let radiusChoices = [100, 200, 500]

    var label: String { (onExit ? "Ayrılınca · " : "Varınca · ") + placeName }
}

/// One card inside a Card Stack (CardStackBlock.kt).
struct CardStackCard: Codable, Identifiable, Hashable {
    var id: String = UUID().uuidString
    var title: String = ""
    var body: String = ""
    var colorArgb: Int? = nil
    var imageUri: String? = nil
    var stackGroupId: String? = nil
}

struct CardStackBlock: Codable, Hashable {
    var id: String = UUID().uuidString
    var prompt: String? = nil
    var cards: [CardStackCard] = []
    var createdAt: EpochMillis = .now
}

/// The REAL card. Field names and semantics mirror ReminderItem.kt.
struct ReminderItem: Codable, Identifiable, Hashable {
    var id: Int64
    var text: String
    var createdAt: EpochMillis
    var updatedAt: EpochMillis
    var completedAt: EpochMillis? = nil
    var isImportant: Bool = false
    var alertAt: EpochMillis? = nil
    /// Date-only planning. Keeps a calendar day without inventing a clock time.
    var plannedDateEpochDay: EpochDay? = nil
    /// Temporary rolling 24-hour focus bucket.
    var quick24HourUntilAt: EpochMillis? = nil
    var notifiedAlertAt: EpochMillis? = nil
    var accentStart: Int? = nil
    var accentEnd: Int? = nil
    var repeatRule: RecurrenceRule = .none
    var anchorAlertAt: EpochMillis? = nil
    var customCategoryId: Int64? = nil
    var isChecklist: Bool = false
    var checklistItems: [ChecklistItem] = []
    var sendImmediateNotification: Bool = true
    var referenceUrl: String? = nil
    var gradientBlendFraction: Double? = nil
    var customSolidColorArgb: Int? = nil
    var useDarkText: Bool = false
    var attachments: [ReminderAttachment] = []
    var journal: ReminderJournal? = nil
    var mood: JournalMood? = nil
    var location: ReminderLocation? = nil
    var cardStack: CardStackBlock? = nil
    var voiceRecordings: [ReminderVoiceRecording] = []
    var musicBlocks: [MusicBlock] = []
    var mediaBlocks: [MediaBlock] = []
    /// Oyun bloğu (own field; see CardGameBlock).
    var gameBlocks: [CardGameBlock] = []
    var contentBlockLayout: [String: CardBlockPlacement] = [:]
    /// PlannerEventDetails.colorIndex (Takvim card pastel).
    var plannerColorIndex: Int?
    /// Weekday set for a WEEKLY repeat (bit0 = Monday ... bit6 = Sunday). 0 = plain single-day repeat.
    var repeatWeekdaysMask: Int = 0
    /// Inclusive last day of a repeat series. nil = repeats forever.
    var repeatUntilEpochDay: EpochDay? = nil
    /// Local epoch days of single occurrences deleted from a repeating series.
    var repeatSkippedEpochDays: [EpochDay] = []
    /// Planner metadata (PlannerEventDetails): duration and people.
    var durationMinutes: Int? = nil
    var people: [String] = []
    /// Detail block order (contentBlockOrder): keys like "text", "checklist", "photos:<id>".
    var contentBlockOrder: [String] = []
    /// When the event ends ("Bitiş"), possibly days later. Independent of [durationMinutes]: a 4-day course of 1 hour a
    /// day keeps "60 dk" and this end.
    var endAt: EpochMillis? = nil
    /// "Erken uyarı": a heads-up this many minutes before [alertAt] (5 dk … 30 gün). nil = none.
    var earlyAlertMinutes: Int? = nil
    /// Alarm: at [alertAt] the card rings as an alarm (AlarmKit on iOS 26+) instead of a plain notification.
    var isAlarm = false
    /// Konuma varınca hatırlat: the card notifies at a place instead of (or as well as) a time.
    var locationTrigger: LocationTrigger? = nil
    /// Ortak kategori: the card's server id when it lives in a shared category (never part of personal sync).
    var sharedCardId: String? = nil
    /// Who added it; shown bottom-right when it is someone else.
    var sharedAuthorUid: String? = nil
    var sharedAuthorName: String? = nil
    /// Ayraç: not a card but a divider line in its category's list; `text` is its title (may be empty). It sits at its
    /// `createdAt` among the cards and stays out of every other list, count, alert and share.
    var isDivider = false
    /// Tamamlama notu: a short line left when the card was completed; shown under "Tamamlandı".
    var completionNote: String? = nil
    /// Kontrol ettir: someone (or you) asked for this card to be looked at; it glows for them until they open it.
    var checkRequest: CheckRequest? = nil

    init(id: Int64, text: String, createdAt: EpochMillis, updatedAt: EpochMillis? = nil) {
        self.id = id
        self.text = text
        self.createdAt = createdAt
        self.updatedAt = updatedAt ?? createdAt
    }

    var isCompleted: Bool { completedAt != nil }
    var hasRepeat: Bool { repeatRule != .none }

    /// When the early warning for the current alert fires, or nil without one.
    var earlyAlertAt: EpochMillis? {
        guard let alertAt, let minutes = earlyAlertMinutes, (1...ReminderItem.maxEarlyAlertMinutes).contains(minutes) else { return nil }
        return alertAt - EpochMillis(minutes) * 60_000
    }
    static let maxEarlyAlertMinutes = 30 * 24 * 60

    func isNoAlert(now: EpochMillis = .now) -> Bool {
        CategoryRules.isNoAlert(completed: isCompleted, alertAt: alertAt, plannedDay: plannedDateEpochDay,
                                quickUntil: quick24HourUntilAt, categoryId: customCategoryId, now: now)
    }

    func isQuick24HourActive(now: EpochMillis = .now) -> Bool {
        CategoryRules.isQuick24HourActive(completed: isCompleted, until: quick24HourUntilAt, now: now)
    }

    func isIn24HourWindow(now: EpochMillis = .now, calendar: Calendar = .real) -> Bool {
        let today = calendar.epochDay(ofMillis: now)
        return CategoryRules.isIn24HourWindow(completed: isCompleted, alertAt: alertAt, plannedDay: plannedDateEpochDay,
                                              quickUntil: quick24HourUntilAt, now: now, todayEpochDay: today)
    }

    /// ReminderItem.toggleCompleted: repeating cards roll to their next occurrence instead of completing.
    func toggledCompleted(now: EpochMillis, calendar: Calendar = .real) -> ReminderItem {
        var copy = self
        copy.updatedAt = now
        if isCompleted {
            copy.completedAt = nil
            copy.notifiedAlertAt = nil
            return copy
        }
        if hasRepeat, let alertAt {
            if notifiedAlertAt != nil {
                copy.notifiedAlertAt = nil
                return copy
            }
            let anchor = anchorAlertAt ?? alertAt
            if let next = Recurrence.nextRepeat(base: anchor, rule: repeatRule, weekdaysMask: repeatWeekdaysMask,
                                                untilEpochDay: repeatUntilEpochDay, after: max(now, alertAt),
                                                skipped: repeatSkippedEpochDays, calendar: calendar) {
                copy.alertAt = next
                copy.anchorAlertAt = anchor
                copy.notifiedAlertAt = nil
                return copy
            }
        }
        copy.completedAt = now
        return copy
    }

    /// cardContentKeys: which detail blocks this card has, in natural order.
    func cardContentKeys(includeEmptyChecklist: Bool = false, includeVoiceRecorder: Bool = false, includeJournal: Bool = false) -> [String] {
        var keys = ["text"]
        if let stack = cardStack, !stack.cards.isEmpty { keys.append("card_stack") }
        if mood != nil { keys.append("mood") }
        if !checklistItems.isEmpty || includeEmptyChecklist { keys.append("checklist") }
        keys += attachments.filter { $0.type == .image }.map { "photos:\($0.id)" }
        keys += attachments.filter { $0.type != .image }.map { "files:\($0.id)" }
        if referenceUrl != nil { keys.append("files:reference") }
        if !voiceRecordings.isEmpty || includeVoiceRecorder { keys.append("voice") }
        keys += musicBlocks.map { "music:\($0.id)" }
        keys += mediaBlocks.map { "media:\($0.id)" }
        keys += gameBlocks.map { "game:\($0.id)" }
        if location != nil { keys.append("location") }
        // Hava durumu: added by itself when the card has a time (or a day) and a place with coordinates.
        if alertAt != nil || plannedDateEpochDay != nil, location?.latitude != nil, location?.longitude != nil { keys.append("weather") }
        if journal != nil || includeJournal { keys.append("journal") }
        return keys
    }

    /// resolveCardContentOrder: saved order first, new blocks appended.
    func resolvedBlockOrder(_ active: [String]) -> [String] {
        var ordered: [String] = []
        for key in contentBlockOrder {
            if active.contains(key) { if !ordered.contains(key) { ordered.append(key) } }
            else if !key.contains(":") { active.filter { $0.split(separator: ":").first.map(String.init) == key && !ordered.contains($0) }.forEach { ordered.append($0) } }
        }
        return ordered + active.filter { !ordered.contains($0) }
    }

    var gradientBlendResolved: Double { min(max(gradientBlendFraction ?? 0.5, 0), 1) }

    /// buildReminderShareText
    var shareText: String {
        guard isChecklist else { return text }
        let items = checklistItems.filter { !$0.text.trimmingCharacters(in: .whitespaces).isEmpty }
        guard !items.isEmpty else { return text }
        let lines = items.map { ($0.isCompleted ? "☑ " : "☐ ") + $0.text.trimmingCharacters(in: .whitespaces) }
        return text.trimmingCharacters(in: .whitespaces) + "\n\n" + lines.joined(separator: "\n")
    }
}

extension ReminderItem {
    // Codable that tolerates missing keys so older JSON (and Android exports) decode cleanly.
    enum CodingKeys: String, CodingKey {
        case id, text, createdAt, updatedAt, completedAt, isImportant, alertAt, plannedDateEpochDay,
             quick24HourUntilAt, notifiedAlertAt, accentStart, accentEnd, repeatRule, anchorAlertAt,
             customCategoryId, isChecklist, checklistItems, sendImmediateNotification, referenceUrl,
             gradientBlendFraction, customSolidColorArgb, useDarkText, attachments, journal, mood, location,
             cardStack, voiceRecordings, musicBlocks, mediaBlocks, gameBlocks, repeatWeekdaysMask, repeatUntilEpochDay, repeatSkippedEpochDays, durationMinutes, people, contentBlockOrder, contentBlockLayout, plannerColorIndex,
             endAt, earlyAlertMinutes, isAlarm, locationTrigger, sharedCardId, sharedAuthorUid, sharedAuthorName, isDivider, completionNote, checkRequest
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        text = try c.decodeIfPresent(String.self, forKey: .text) ?? ""
        createdAt = try c.decodeIfPresent(EpochMillis.self, forKey: .createdAt) ?? .now
        updatedAt = try c.decodeIfPresent(EpochMillis.self, forKey: .updatedAt) ?? createdAt
        completedAt = try c.decodeIfPresent(EpochMillis.self, forKey: .completedAt)
        isImportant = try c.decodeIfPresent(Bool.self, forKey: .isImportant) ?? false
        alertAt = try c.decodeIfPresent(EpochMillis.self, forKey: .alertAt)
        plannedDateEpochDay = try c.decodeIfPresent(EpochDay.self, forKey: .plannedDateEpochDay)
        quick24HourUntilAt = try c.decodeIfPresent(EpochMillis.self, forKey: .quick24HourUntilAt)
        notifiedAlertAt = try c.decodeIfPresent(EpochMillis.self, forKey: .notifiedAlertAt)
        accentStart = try c.decodeIfPresent(Int.self, forKey: .accentStart)
        accentEnd = try c.decodeIfPresent(Int.self, forKey: .accentEnd)
        repeatRule = try c.decodeIfPresent(RecurrenceRule.self, forKey: .repeatRule) ?? .none
        anchorAlertAt = try c.decodeIfPresent(EpochMillis.self, forKey: .anchorAlertAt) ?? alertAt
        customCategoryId = try c.decodeIfPresent(Int64.self, forKey: .customCategoryId)
        isChecklist = try c.decodeIfPresent(Bool.self, forKey: .isChecklist) ?? false
        checklistItems = try c.decodeIfPresent([ChecklistItem].self, forKey: .checklistItems) ?? []
        sendImmediateNotification = try c.decodeIfPresent(Bool.self, forKey: .sendImmediateNotification) ?? true
        referenceUrl = try c.decodeIfPresent(String.self, forKey: .referenceUrl)
        gradientBlendFraction = try c.decodeIfPresent(Double.self, forKey: .gradientBlendFraction)
        customSolidColorArgb = try c.decodeIfPresent(Int.self, forKey: .customSolidColorArgb)
        useDarkText = try c.decodeIfPresent(Bool.self, forKey: .useDarkText) ?? false
        attachments = try c.decodeIfPresent([ReminderAttachment].self, forKey: .attachments) ?? []
        journal = try c.decodeIfPresent(ReminderJournal.self, forKey: .journal)
        mood = try c.decodeIfPresent(JournalMood.self, forKey: .mood)
        location = try c.decodeIfPresent(ReminderLocation.self, forKey: .location)
        cardStack = try c.decodeIfPresent(CardStackBlock.self, forKey: .cardStack)
        voiceRecordings = try c.decodeIfPresent([ReminderVoiceRecording].self, forKey: .voiceRecordings) ?? []
        musicBlocks = try c.decodeIfPresent([MusicBlock].self, forKey: .musicBlocks) ?? []
        mediaBlocks = try c.decodeIfPresent([MediaBlock].self, forKey: .mediaBlocks) ?? []
        gameBlocks = (try? c.decodeIfPresent([CardGameBlock].self, forKey: .gameBlocks)) ?? []
        repeatWeekdaysMask = try c.decodeIfPresent(Int.self, forKey: .repeatWeekdaysMask) ?? 0
        repeatUntilEpochDay = try c.decodeIfPresent(EpochDay.self, forKey: .repeatUntilEpochDay)
        repeatSkippedEpochDays = try c.decodeIfPresent([EpochDay].self, forKey: .repeatSkippedEpochDays) ?? []
        durationMinutes = try c.decodeIfPresent(Int.self, forKey: .durationMinutes)
        people = try c.decodeIfPresent([String].self, forKey: .people) ?? []
        contentBlockOrder = try c.decodeIfPresent([String].self, forKey: .contentBlockOrder) ?? []
        contentBlockLayout = try c.decodeIfPresent([String: CardBlockPlacement].self, forKey: .contentBlockLayout) ?? [:]
        plannerColorIndex = try c.decodeIfPresent(Int.self, forKey: .plannerColorIndex)
        endAt = try c.decodeIfPresent(EpochMillis.self, forKey: .endAt)
        earlyAlertMinutes = (try c.decodeIfPresent(Int.self, forKey: .earlyAlertMinutes)).flatMap { (1...ReminderItem.maxEarlyAlertMinutes).contains($0) ? $0 : nil }
        isAlarm = try c.decodeIfPresent(Bool.self, forKey: .isAlarm) ?? false
        locationTrigger = try? c.decodeIfPresent(LocationTrigger.self, forKey: .locationTrigger)
        sharedCardId = try c.decodeIfPresent(String.self, forKey: .sharedCardId)
        sharedAuthorUid = try c.decodeIfPresent(String.self, forKey: .sharedAuthorUid)
        sharedAuthorName = try c.decodeIfPresent(String.self, forKey: .sharedAuthorName)
        isDivider = try c.decodeIfPresent(Bool.self, forKey: .isDivider) ?? false
        completionNote = (try c.decodeIfPresent(String.self, forKey: .completionNote)).flatMap { $0.isEmpty ? nil : $0 }
        checkRequest = try? c.decodeIfPresent(CheckRequest.self, forKey: .checkRequest)
    }
}

/// Kontrol ettir (CheckRequest in ReminderItem.kt). `to`: uids in a shared category, "me" for your own cards, "*" for all
/// members but `from`. `at` identifies the request; `startEpochDay`: it glows from that day; `color`: nil = automatic.
struct CheckRequest: Codable, Hashable {
    static let me = "me"
    static let everyone = "*"
    var to: [String]
    var from: String?
    var at: EpochMillis
    var startEpochDay: Int64? = nil
    var color: Int? = nil
}

/// Where each Ayraç sits in a category's sorted `cards`: index → dividers drawn before cards[index] (index == count: after
/// the last card). Only the added-time orders show them (dividerSlots in ReminderItem.kt).
func dividerSlots(_ cards: [ReminderItem], _ dividers: [ReminderItem], sort: ReminderSortOrder) -> [Int: [ReminderItem]] {
    guard !dividers.isEmpty, sort == .recentlyAdded || sort == .oldestFirst else { return [:] }
    let newestFirst = sort == .recentlyAdded
    var out: [Int: [ReminderItem]] = [:]
    for d in dividers.sorted(by: { newestFirst ? $0.createdAt > $1.createdAt : $0.createdAt < $1.createdAt }) {
        let index = cards.firstIndex { c in c.id >= 0 && (newestFirst ? c.createdAt < d.createdAt : c.createdAt > d.createdAt) } ?? cards.count
        out[index, default: []].append(d)
    }
    return out
}

enum ReminderSortOrder: String, CaseIterable, Codable {
    case recentlyAdded, reminderTime, oldestFirst, nearestDate

    var label: String {
        switch self {
        case .recentlyAdded: return "Son eklenen"
        case .reminderTime: return "Hatırlatma saati"
        case .oldestFirst: return "İlk eklenen"
        case .nearestDate: return "En yakın tarih"
        }
    }
}

/// filterAndSortReminders
func filterAndSortReminders(_ reminders: [ReminderItem], colorFilter: Int? = nil, sort: ReminderSortOrder,
                            now: EpochMillis = .now, calendar: Calendar = .real) -> [ReminderItem] {
    var result = reminders
    if let colorFilter {
        result = colorFilter == -1
            ? result.filter { $0.accentStart == nil }
            : result.filter { $0.accentStart == colorFilter || $0.accentEnd == colorFilter }
    }
    switch sort {
    case .recentlyAdded:
        result.sort { $0.createdAt > $1.createdAt }
    case .oldestFirst:
        result.sort { ($0.createdAt, $0.id) < ($1.createdAt, $1.id) }
    case .nearestDate:
        func due(_ r: ReminderItem) -> EpochMillis? {
            r.alertAt ?? r.plannedDateEpochDay.map { calendar.startOfDayMillis(epochDay: $0) }
        }
        result.sort { a, b in
            let da = due(a), db = due(b)
            if (da == nil) != (db == nil) { return da != nil }
            let ka = da.map { abs($0 - now) } ?? 0, kb = db.map { abs($0 - now) } ?? 0
            if ka != kb { return ka < kb }
            return a.createdAt > b.createdAt
        }
    case .reminderTime:
        result.sort { a, b in
            if (a.alertAt == nil) != (b.alertAt == nil) { return a.alertAt != nil }
            if let x = a.alertAt, let y = b.alertAt, x != y { return x < y }
            return a.createdAt > b.createdAt
        }
    }
    return result
}

extension EpochMillis {
    static var now: EpochMillis { EpochMillis(Date().timeIntervalSince1970 * 1000) }
    var date: Date { Date(timeIntervalSince1970: TimeInterval(self) / 1000) }
}

extension Date {
    var epochMillis: EpochMillis { EpochMillis(timeIntervalSince1970 * 1000) }
}
