import Foundation
import Observation

enum BeverageType: String, Codable, CaseIterable, Identifiable {
    case water, coffee, beer
    var id: String { rawValue }
    var label: String { self == .water ? "Su" : (self == .coffee ? "Coffee" : "Beer") }
    var quickAmounts: [Int] { self == .water ? [150, 250] : (self == .coffee ? [100, 200] : [330, 500]) }
}

struct WaterIntakeEntry: Codable, Identifiable, Hashable {
    var id: String
    var amountMl: Int
    var createdAt: EpochMillis
    var beverage: BeverageType = .water
}

enum MoodScope: String, Codable { case moment, daily
    var label: String { self == .moment ? "Anlık his" : "Günün havası" }
}

enum MoodEnergy: String, Codable, CaseIterable { case low, balanced, high
    var label: String { self == .low ? "Düşük" : (self == .balanced ? "Dengeli" : "Yüksek") }
}

struct MoodDetails: Codable, Hashable {
    var scope: MoodScope = .daily
    var descriptors: [String] = []
    var energy: MoodEnergy? = nil
    var factors: [String] = []
    var loggedAt: EpochMillis
}

struct MoodTrackerEntry: Codable, Identifiable, Hashable {
    var id: Int64
    var mood: JournalMood
    var details: MoodDetails
}

/// Yaşam data (WaterTrackerRepository + MoodTrackerRepository): drinks with 30-day retention and frozen daily
/// goals, mood entries capped at 730. Stored in its own JSON so reminders are never rewritten by a drink.
@Observable
final class LifeStore {
    private(set) var goalMl = 2_000
    private(set) var entries: [WaterIntakeEntry] = []
    private(set) var dailyGoals: [EpochDay: Int] = [:]
    private(set) var moods: [MoodTrackerEntry] = []

    private let fileURL: URL
    private let persist: Bool
    let calendar = Calendar.real

    private struct Snapshot: Codable {
        var goalMl: Int
        var entries: [WaterIntakeEntry]
        var dailyGoals: [EpochDay: Int]
        var moods: [MoodTrackerEntry]
    }

    init(persist: Bool = true) {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appendingPathComponent("real-life.json")
        self.persist = persist
        if persist, let data = try? Data(contentsOf: fileURL), let snap = try? JSONDecoder().decode(Snapshot.self, from: data) {
            goalMl = snap.goalMl
            entries = snap.entries
            dailyGoals = snap.dailyGoals
            moods = snap.moods
        }
        retain()
    }

    private func save() {
        guard persist else { return }
        let snap = Snapshot(goalMl: goalMl, entries: entries, dailyGoals: dailyGoals, moods: moods)
        if let data = try? JSONEncoder().encode(snap) { try? data.write(to: fileURL, options: .atomic) }
    }

    /// Today and the previous 29 days; each known day's goal is frozen.
    private func retain() {
        let today = calendar.epochDay(of: Date())
        entries = entries.filter { calendar.epochDay(ofMillis: $0.createdAt) >= today - 29 }
        if let last = dailyGoals.keys.filter({ $0 <= today }).max(), last < today {
            for day in max(last + 1, today - 29)...today where dailyGoals[day] == nil { dailyGoals[day] = goalMl }
        }
        if dailyGoals[today] == nil { dailyGoals[today] = goalMl }
        dailyGoals = dailyGoals.filter { $0.key >= today - 29 }
    }

    // MARK: Drinks

    func addDrink(_ amountMl: Int, beverage: BeverageType = .water) {
        guard (1...5_000).contains(amountMl) else { return }
        entries.append(WaterIntakeEntry(id: UUID().uuidString, amountMl: amountMl, createdAt: .now, beverage: beverage))
        retain()
        save()
    }

    func removeDrink(_ id: String) {
        entries.removeAll { $0.id == id }
        save()
    }

    func setGoal(_ ml: Int) {
        guard (100...10_000).contains(ml) else { return }
        goalMl = ml
        dailyGoals[calendar.epochDay(of: Date())] = ml
        save()
    }

    func total(on day: Date, beverage: BeverageType = .water) -> Int {
        entries.filter { $0.beverage == beverage && calendar.isDate($0.createdAt.date, inSameDayAs: day) }.reduce(0) { $0 + $1.amountMl }
    }

    func entries(on day: Date, beverage: BeverageType? = nil) -> [WaterIntakeEntry] {
        entries.filter { (beverage == nil || $0.beverage == beverage) && calendar.isDate($0.createdAt.date, inSameDayAs: day) }
            .sorted { $0.createdAt > $1.createdAt }
    }

    func goal(on day: Date) -> Int { dailyGoals[calendar.epochDay(of: day)] ?? goalMl }

    // MARK: Moods

    func addMood(_ mood: JournalMood, details: MoodDetails) {
        let now = EpochMillis.now
        let loggedAt = details.loggedAt > 0 ? details.loggedAt : now
        var d = details
        d.loggedAt = loggedAt
        let id = max(now, (moods.map(\.id).max() ?? 0) + 1)
        moods = ([MoodTrackerEntry(id: id, mood: mood, details: d)] + moods)
            .sorted { $0.details.loggedAt > $1.details.loggedAt }
            .prefix(730).map { $0 }
        save()
    }

    func clearAllMoods() {
        moods = []
        save()
    }

    func replaceAll(goal: Int, entries: [WaterIntakeEntry], moods: [MoodTrackerEntry]) {
        goalMl = goal
        self.entries = entries
        self.moods = moods
        retain()
        save()
    }
}
