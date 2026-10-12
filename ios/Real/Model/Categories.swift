import Foundation

/// All time inputs are supplied by the caller (CategoryRules.kt).
enum CategoryRules {
    static let rolling24HoursMillis: EpochMillis = 24 * 60 * 60 * 1000

    static func isQuick24HourActive(completed: Bool, until: EpochMillis?, now: EpochMillis) -> Bool {
        !completed && (until.map { $0 > now } ?? false)
    }

    static func isNoAlert(completed: Bool, alertAt: EpochMillis?, plannedDay: EpochDay?, quickUntil: EpochMillis?,
                          categoryId: Int64?, now: EpochMillis) -> Bool {
        !completed && alertAt == nil && plannedDay == nil && !(quickUntil.map { $0 > now } ?? false) && categoryId == nil
    }

    static func isIn24HourWindow(completed: Bool, alertAt: EpochMillis?, plannedDay: EpochDay?, quickUntil: EpochMillis?,
                                 now: EpochMillis, todayEpochDay: EpochDay) -> Bool {
        if completed { return false }
        if isQuick24HourActive(completed: completed, until: quickUntil, now: now) { return true }
        if let alertAt, alertAt >= now, alertAt <= now + rolling24HoursMillis { return true }
        return plannedDay != nil && plannedDay == todayEpochDay
    }

    static func nextId(now: Int64, existing: [Int64]) -> Int64 { max(now, (existing.max() ?? 0) + 1) }
}

/// Built-in categories (ReminderCategory.kt).
enum ReminderCategory: String, CaseIterable, Codable, Identifiable {
    case today = "Today", planned = "Planned", important = "Important", noAlert = "NoAlert",
         completed = "Completed", journalEntries = "JournalEntries"

    var id: String { rawValue }
    var title: String {
        switch self {
        case .today: return "24 hour"
        case .planned: return "Planlı"
        case .important: return "Önemli"
        case .noAlert: return "Uyarı yok"
        case .completed: return "Tamamlananlar"
        case .journalEntries: return "Günlüklerim"
        }
    }
    var emptyMessage: String {
        switch self {
        case .today: return "Önündeki 24 saatte bir REAL yok"
        case .planned: return "Henüz planlanan hatırlatıcı yok"
        case .important: return "Yıldız eklediğin hatırlatıcılar burada görünür"
        case .noAlert: return "Henüz hatırlatıcı yok"
        case .completed: return "Tamamladığın hatırlatıcılar burada görünür"
        case .journalEntries: return "Henüz günlük eklenmemiş"
        }
    }
    var symbol: String {
        switch self {
        case .today: return "calendar"
        case .planned: return "clock"
        case .important: return "star.fill"
        case .noAlert: return "note.text"
        case .completed: return "checkmark.circle"
        case .journalEntries: return "book.closed"
        }
    }
    /// CategoryStyle.defaultStyle colours: (upper, lower, darkText).
    var defaultColors: (UInt32, UInt32, Bool) {
        switch self {
        case .today: return (0xFF23457D, 0xFF863D59, false)
        case .planned: return (0xFFE0F37D, 0xFFE0F37D, true)
        case .important: return (0xFF4658C8, 0xFF4658C8, false)
        case .noAlert: return (0xFFDAB7F3, 0xFFDAB7F3, true)
        case .completed: return (0xFFEE5E36, 0xFFEE5E36, false)
        case .journalEntries: return (0xFF4E648D, 0xFF7E5B6D, false)
        }
    }
}

/// Built-in category colour override (CategoryColorOverride).
struct CategoryColorOverride: Codable, Hashable {
    var start: Int?
    var end: Int? = nil
    var useDarkText: Bool = false
    var gradientBlendFraction: Double? = nil
    var customSolidColorArgb: Int? = nil
}

enum CategoryIcon: String, CaseIterable, Codable, Identifiable {
    case work = "WORK", home = "HOME", shopping = "SHOPPING", health = "HEALTH", sport = "SPORT", money = "MONEY",
         school = "SCHOOL", travel = "TRAVEL", family = "FAMILY", pet = "PET", food = "FOOD", idea = "IDEA",
         heart = "HEART", star = "STAR", file = "FILE", location = "LOCATION", bookmark = "BOOKMARK", gift = "GIFT"

    var id: String { rawValue }
    var label: String {
        switch self {
        case .work: return "İş"
        case .home: return "Ev"
        case .shopping: return "Alışveriş"
        case .health: return "Sağlık"
        case .sport: return "Spor"
        case .money: return "Finans"
        case .school: return "Eğitim"
        case .travel: return "Seyahat"
        case .family: return "Aile"
        case .pet: return "Evcil hayvan"
        case .food: return "Yemek"
        case .idea: return "Fikir"
        case .heart: return "Favori"
        case .star: return "Yıldız"
        case .file: return "Belge"
        case .location: return "Konum"
        case .bookmark: return "Kaydedilen"
        case .gift: return "Hediye"
        }
    }
    /// RealSymbol key: draws the Android CategoryIcon glyph.
    var symbol: String { "cat." + rawValue }

    var sfSymbol: String {
        switch self {
        case .work: return "briefcase"
        case .home: return "house"
        case .shopping: return "cart"
        case .health: return "cross"
        case .sport: return "dumbbell"
        case .money: return "dollarsign"
        case .school: return "graduationcap"
        case .travel: return "airplane"
        case .family: return "person.2"
        case .pet: return "pawprint"
        case .food: return "fork.knife"
        case .idea: return "lightbulb"
        case .heart: return "heart"
        case .star: return "star"
        case .file: return "doc"
        case .location: return "mappin.and.ellipse"
        case .bookmark: return "bookmark"
        case .gift: return "gift"
        }
    }

    static func from(key: String) -> CategoryIcon {
        if key.caseInsensitiveCompare("CAR") == .orderedSame { return .pet }
        if key.caseInsensitiveCompare("GAME") == .orderedSame { return .gift }
        return CategoryIcon(rawValue: key.uppercased()) ?? .work
    }
}

struct CustomCategory: Codable, Identifiable, Hashable {
    var id: Int64
    var name: String
    var iconKey: String
    var accentStart: Int
    var accentEnd: Int? = nil
    var hideFromGeneralLists: Bool = false
    var useDarkText: Bool = false
    var gradientBlendFraction: Double? = nil
    var customSolidColorArgb: Int? = nil
    var description: String = ""
    var createdAt: EpochMillis = .now
    /// "REAL takviminde göster"; nil (older data) means shown.
    var showInCalendar: Bool? = nil

    var icon: CategoryIcon { CategoryIcon.from(key: iconKey) }
    var isShownInCalendar: Bool { showInCalendar ?? true }
}

/// Home deck entries in their saved order (OrderedCategoryItem).
enum OrderedCategory: Hashable, Identifiable {
    case builtIn(ReminderCategory)
    case custom(CustomCategory)

    var id: String { orderKey }
    var orderKey: String {
        switch self {
        case .builtIn(let c): return "builtin:\(c.rawValue)"
        case .custom(let c): return "custom:\(c.id)"
        }
    }
}

struct CategoryProgressStats: Hashable {
    var done: Int
    var total: Int
    var doneToday: Int
    var fraction: Double { total == 0 ? 0 : Double(done) / Double(total) }
}

extension Array where Element == ReminderItem {
    /// inCategory (ReminderCategory.kt)
    func inCategory(_ category: ReminderCategory, now: EpochMillis = .now, calendar: Calendar = .real) -> [ReminderItem] {
        let filtered = filter { item in
            switch category {
            case .completed: return item.isCompleted
            case .noAlert: return item.isNoAlert(now: now)
            case .important: return !item.isCompleted && item.isImportant
            case .planned: return !item.isCompleted && (item.alertAt != nil || item.plannedDateEpochDay != nil)
            case .today: return item.isIn24HourWindow(now: now, calendar: calendar)
            case .journalEntries: return item.journal != nil
            }
        }
        switch category {
        case .completed:
            return filtered.sorted { ($0.completedAt ?? 0) > ($1.completedAt ?? 0) }
        case .journalEntries:
            return filtered.sorted { ($0.journal?.createdAt ?? $0.createdAt) > ($1.journal?.createdAt ?? $1.createdAt) }
        case .today:
            func key(_ item: ReminderItem) -> EpochMillis {
                if let a = item.alertAt { return a }
                if let q = item.quick24HourUntilAt { return q - CategoryRules.rolling24HoursMillis }
                return .max
            }
            return filtered.sorted { key($0) != key($1) ? key($0) < key($1) : $0.createdAt > $1.createdAt }
        default:
            return filtered.sorted { $0.createdAt > $1.createdAt }
        }
    }

    /// categoryProgress (CategoryHeaderGlass.kt)
    func categoryProgress(builtIn: ReminderCategory?, customId: Int64?, calendar: Calendar = .real) -> CategoryProgressStats? {
        let tasks = filter { $0.journal == nil && $0.mood == nil }
        let members: [ReminderItem]
        if let customId {
            members = tasks.filter { $0.customCategoryId == customId }
        } else {
            switch builtIn {
            case .planned: members = tasks.filter { $0.alertAt != nil || $0.plannedDateEpochDay != nil }
            case .important: members = tasks.filter(\.isImportant)
            case .noAlert: members = tasks.filter { $0.alertAt == nil && $0.plannedDateEpochDay == nil && $0.customCategoryId == nil }
            default: return nil
            }
        }
        let today = calendar.epochDay(of: Date())
        let done = members.filter(\.isCompleted).count
        let doneToday = members.filter { $0.completedAt.map { calendar.epochDay(ofMillis: $0) == today } ?? false }.count
        return CategoryProgressStats(done: done, total: members.count, doneToday: doneToday)
    }
}
