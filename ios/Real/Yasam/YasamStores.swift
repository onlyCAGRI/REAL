import SwiftUI
import UIKit

// Me's on-device logs (YasamManual.kt, YasamCoffee.kt, YasamDrinks.kt). Health and drink data never leave the phone
// (the drink photo goes to the recognizer only, shrunk, and is not stored there).

/// Steps and sleep typed by hand until HealthKit is wired: per day, on this phone only.
enum YasamManual {
    private static var d: UserDefaults { .standard }
    private static func today() -> EpochDay { Calendar.real.epochDay(of: Date()) }

    static func steps(_ day: EpochDay = today()) -> Int { d.integer(forKey: "yasam_manual_steps_\(day)") }
    static func setSteps(_ value: Int, day: EpochDay = today()) { d.set(min(max(value, 0), 200_000), forKey: "yasam_manual_steps_\(day)") }
    /// The last [count] days, oldest first, today last.
    static func stepsDays(_ count: Int) -> [Int] { let t = today(); return (0..<count).reversed().map { steps(t - EpochDay($0)) } }
    static func stepsWeek() -> [Int] { stepsDays(7) }
    /// Minutes slept, stored on the day you woke up.
    static func sleep(_ day: EpochDay = today()) -> Int { d.integer(forKey: "yasam_manual_sleep_\(day)") }
    static func setSleep(_ minutes: Int, day: EpochDay = today()) { d.set(min(max(minutes, 0), 24 * 60), forKey: "yasam_manual_sleep_\(day)") }

    static func sleepText(_ minutes: Int) -> String { minutes <= 0 ? "—" : t("{0}s {1}d", minutes / 60, minutes % 60) }
    static func stepsText(_ steps: Int) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.locale = I18n.locale
        return f.string(from: NSNumber(value: steps)) ?? "\(steps)"
    }
}

func yasamOneDecimal(_ v: Double) -> String {
    let r = (v * 10).rounded() / 10
    let s = r.truncatingRemainder(dividingBy: 1) == 0 ? "\(Int(r))" : "\(r)"
    return I18n.isEnglish ? s : s.replacingOccurrences(of: ".", with: ",")
}

// MARK: Coffee

/// Coffee kinds with a rough caffeine per cup (mg) and the colour of their block. The key is stored.
enum CoffeeKind: String, CaseIterable, Identifiable, Codable {
    // Keys are stored: never rename one, only add. Order = the 3×3 grid on the Kahve screen.
    case espresso = "Espresso", americano = "Americano", latte = "Latte", cappuccino = "Cappuccino",
         flatWhite = "Flat white", filter = "Filtre", cold = "Soğuk", turkish = "Türk", instant = "Hazır"

    var id: String { rawValue }
    static func of(_ key: String?) -> CoffeeKind { CoffeeKind(rawValue: key ?? "") ?? .filter }

    var label: String {
        switch self {
        case .filter: return t("Filtre kahve")
        case .cold: return t("Cold brew")
        case .turkish: return t("Türk kahvesi")
        case .instant: return t("Hazır kahve")
        default: return t(rawValue)
        }
    }
    /// The short name on the 3×3 grid.
    var short: String { self == .cold ? t("Cold brew") : t(rawValue) }

    var mg: Int {
        switch self {
        case .espresso: return 65
        case .americano, .filter: return 95
        case .latte, .cappuccino: return 75
        case .flatWhite: return 130
        case .cold: return 155
        case .turkish, .instant: return 60
        }
    }

    var color: Color {
        switch self {
        case .espresso: return Color(argb: 0xFF4A2E1E)
        case .americano: return Color(argb: 0xFF7A4A28)
        case .latte: return Color(argb: 0xFFC9A27C)
        case .cappuccino: return Color(argb: 0xFFB88B62)
        case .flatWhite: return Color(argb: 0xFFA6764F)
        case .filter: return Color(argb: 0xFF8A5530)
        case .cold: return Color(argb: 0xFF5E3B26)
        case .turkish: return Color(argb: 0xFF3B2416)
        case .instant: return Color(argb: 0xFF9C6B45)
        }
    }
}

struct CoffeeLogEntry: Codable, Identifiable, Hashable {
    let id: String
    let at: EpochMillis
    let kind: String
    var coffee: CoffeeKind { .of(kind) }
}

/// Me > Kahve log, on this phone only (like the drink log). Coffees from the old drinks screen still count on Me.
enum YasamCoffeeLog {
    private static let key = "yasam_coffee_v1"
    private static let keep = 3000

    static func load() -> [CoffeeLogEntry] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([CoffeeLogEntry].self, from: data)) ?? []
    }
    private static func write(_ list: [CoffeeLogEntry]) {
        UserDefaults.standard.set(try? JSONEncoder().encode(list), forKey: key)
    }
    static func add(_ kind: CoffeeKind) {
        write(Array((load() + [CoffeeLogEntry(id: UUID().uuidString, at: .now, kind: kind.rawValue)]).sorted { $0.at < $1.at }.suffix(keep)))
    }
    static func remove(_ id: String) { write(load().filter { $0.id != id }) }
    static func today(_ entries: [CoffeeLogEntry]) -> [CoffeeLogEntry] {
        entries.filter { Calendar.real.isDateInToday($0.at.date) }
    }
}

// MARK: Alcohol

/// Kinds on the alcohol screen; the stored key is the Turkish word (the server answers with the same words).
enum DrinkKind: String, CaseIterable, Identifiable {
    // Keys are stored and are what the server answers with: never rename one, only add.
    case beer = "Bira", wine = "Şarap", sparkling = "Köpüklü", cocktail = "Kokteyl", whisky = "Viski", vodka = "Votka",
         gin = "Cin", rum = "Rom", tequila = "Tekila", cider = "Cider", sake = "Sake", raki = "Rakı", other = "Diğer"

    var id: String { rawValue }
    static func of(_ key: String?) -> DrinkKind { DrinkKind(rawValue: key ?? "") ?? .other }

    var label: String { self == .sparkling ? t("Köpüklü şarap") : t(rawValue) }

    var liquid: Color {
        switch self {
        case .beer: return Color(argb: 0xFFE8A83A)
        case .wine: return Color(argb: 0xFF7A1832)
        case .sparkling: return Color(argb: 0xFFF2D98A)
        case .cocktail: return Color(argb: 0xFFFF6A8A)
        case .whisky: return Color(argb: 0xFFB5651D)
        case .vodka: return Color(argb: 0xFFDDE6EE)
        case .gin: return Color(argb: 0xFFBFE3D2)
        case .rum: return Color(argb: 0xFF9A5A2A)
        case .tequila: return Color(argb: 0xFFE9D27A)
        case .cider: return Color(argb: 0xFFE2B13C)
        case .sake: return Color(argb: 0xFFF1EEE2)
        case .raki: return Color(argb: 0xFFE9E6DF)
        case .other: return Color(argb: 0xFFB08A5A)
        }
    }

    var portions: [(String, Int)] {
        switch self {
        case .beer, .cider: return [("33 cl", 330), ("50 cl", 500), ("Pint", 570)]
        case .wine: return [("Kadeh", 150), ("Büyük kadeh", 250), ("Yarım şişe", 375)]
        case .sparkling: return [("Kadeh", 125), ("Büyük kadeh", 200)]
        case .cocktail: return [("1 kokteyl", 200)]
        case .whisky, .gin, .rum: return [("Tek", 40), ("Duble", 80)]
        case .vodka, .tequila: return [("Shot", 40), ("Duble", 80)]
        case .sake: return [("Bardak", 180), ("Şişe", 300)]
        case .raki: return [("Tek", 35), ("Duble", 70)]
        case .other: return Self.unknownPortions
        }
    }

    /// Spirits: a shot-sized liquid scale.
    var spirit: Bool { [.whisky, .vodka, .gin, .rum, .tequila, .raki].contains(self) }
    /// Before the photo is read: neutral portions.
    static let unknownPortions: [(String, Int)] = [("Kadeh", 150), ("Kutu", 330), ("Bardak", 500)]
}

struct DrinkLogEntry: Codable, Identifiable, Hashable {
    let id: String
    let at: EpochMillis
    let kind: String
    let name: String
    let ml: Int
    /// File name inside Application Support/drinks.
    let photo: String?
    var drink: DrinkKind { .of(kind) }
    var photoURL: URL? { photo.map { YasamDrinkLog.dir.appendingPathComponent($0) } }
}

/// Me > Alkol log: on this phone only (photos live in Application Support/drinks). Kept apart from the water tracker so
/// it can become its own app later.
enum YasamDrinkLog {
    private static let key = "yasam_drinks_v1"
    private static let keep = 600

    static var dir: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("drinks", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }

    static func load() -> [DrinkLogEntry] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([DrinkLogEntry].self, from: data)) ?? []
    }

    static func add(_ entry: DrinkLogEntry) {
        let all = (load() + [entry]).sorted { $0.at < $1.at }
        let kept = Array(all.suffix(keep))
        write(kept, dropped: all.filter { e in !kept.contains(e) })
    }

    static func remove(_ id: String) {
        let all = load()
        write(all.filter { $0.id != id }, dropped: all.filter { $0.id == id })
    }

    /// "Yine aynısı" shares the photo file, so a photo goes only when no kept entry still uses it.
    private static func write(_ kept: [DrinkLogEntry], dropped: [DrinkLogEntry]) {
        let used = Set(kept.compactMap(\.photo))
        for name in dropped.compactMap(\.photo) where !used.contains(name) {
            try? FileManager.default.removeItem(at: dir.appendingPathComponent(name))
        }
        UserDefaults.standard.set(try? JSONEncoder().encode(kept), forKey: key)
    }

    static func today(_ entries: [DrinkLogEntry]) -> [DrinkLogEntry] { entries.filter { Calendar.real.isDateInToday($0.at.date) } }

    static func week(_ entries: [DrinkLogEntry]) -> Int {
        let from = Calendar.real.date(byAdding: .day, value: -6, to: Calendar.real.startOfDay(for: Date()))!
        return entries.filter { $0.at.date >= from }.count
    }

    static func newEntry(_ kind: DrinkKind, name: String, ml: Int, photo: String?) -> DrinkLogEntry {
        DrinkLogEntry(id: UUID().uuidString, at: .now, kind: kind.rawValue, name: name, ml: ml, photo: photo)
    }

    /// Keeps a 1080 px JPEG of the camera photo; returns its file name.
    static func keepPhoto(_ image: UIImage) -> String? {
        let name = UUID().uuidString + ".jpg"
        guard let data = image.yasamScaled(maxSide: 1080).jpegData(compressionQuality: 0.82) else { return nil }
        do { try data.write(to: dir.appendingPathComponent(name)); return name } catch { return nil }
    }
}

extension UIImage {
    /// Upright and at most [maxSide] px on its long side.
    func yasamScaled(maxSide: CGFloat) -> UIImage {
        let scale = min(1, maxSide / max(size.width, size.height))
        let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in draw(in: CGRect(origin: .zero, size: target)) }
    }
}

/// What the server read from the photo; nil fields when it could not tell.
struct DrinkGuess {
    let recognized: Bool
    let kind: DrinkKind?
    let names: [String]
    let portionMl: Int
}

/// POST /api/drink-recognize behind the AI consent gate (drinks). Failure = ask the kind instead.
enum DrinkRecognizer {
    @MainActor
    static func recognize(_ image: UIImage) async throws -> DrinkGuess {
        try await AiConsent.require(.drinks)
        guard let data = image.yasamScaled(maxSide: 384).jpegData(compressionQuality: 0.72) else { throw URLError(.cannotDecodeContentData) }
        let json = try await RealBackend.post("/api/drink-recognize",
                                              ["image": data.base64EncodedString(), "language": I18n.isEnglish ? "en" : "tr"], timeout: 15)
        guard json["ok"] as? Bool == true else { throw RealBackend.Failure(status: -1) }
        let recognized = json["recognized"] as? Bool ?? false
        let names = (json["names"] as? [String] ?? []).filter { !$0.isEmpty }
        return DrinkGuess(recognized: recognized, kind: recognized ? DrinkKind.of(json["type"] as? String) : nil,
                          names: names, portionMl: json["portionMl"] as? Int ?? 0)
    }
}
