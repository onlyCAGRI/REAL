import SwiftUI
import Observation

enum MediaType: String, Codable { case MOVIE, TV }

/// MediaBlock.kt: catalog-verified film / series attached to a card ("media:<id>" detail block).
struct MediaBlock: Codable, Hashable, Identifiable {
    var id: String = UUID().uuidString
    var type: MediaType
    var providerId: String?
    var title: String
    var subtitle: String?
    var year: Int?
    var genre: String?
    var description: String?
    var artworkUrl: String?
    var provider: String = "Apple"
    var providerUrl: String
    var durationMillis: Int64?
    var createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
}

/// DiscoveryFilter: genre (group 0), theme (group 1, paged by themeGroup) and feeling (group 2) keys, shared with the Worker.
struct DiscoveryFilter: Hashable, Identifiable {
    let key: String
    let label: String
    var genre: Bool = true
    var aliases: [String] = []
    var themeGroup: String?
    var id: String { key }
    var selectionGroup: Int { genre ? 0 : (themeGroup != nil ? 1 : 2) }

    static let maxPerGroup = 3

    static let film: [DiscoveryFilter] = {
        func g(_ k: String, _ l: String, _ a: [String] = []) -> DiscoveryFilter { DiscoveryFilter(key: k, label: l, aliases: a.isEmpty ? [k] : a) }
        func f(_ k: String, _ l: String) -> DiscoveryFilter { DiscoveryFilter(key: k, label: l, genre: false) }
        func t(_ k: String, _ l: String, _ group: String) -> DiscoveryFilter { DiscoveryFilter(key: k, label: l, genre: false, themeGroup: group) }
        let future = "Gelecek ve bilim kurgu", mystery = "Gizem ve mücadele", human = "İnsan ve yaşam", fantasy = "Fantastik ve doğaüstü"
        return [
            g("action", "Aksiyon"), g("adventure", "Macera"), g("comedy", "Komedi"), g("drama", "Dram"), g("romance", "Romantik"),
            g("science-fiction", "Bilim kurgu", ["science fiction", "sci-fi", "science-fiction"]), g("fantasy", "Fantastik"),
            g("thriller", "Gerilim"), g("mystery", "Gizem"), g("crime", "Suç"), g("horror", "Korku"), g("animation", "Animasyon"),
            g("documentary", "Belgesel"), g("family", "Aile"), g("history", "Tarih", ["history", "historical"]), g("war", "Savaş"),
            f("feel-good", "İyi hissettiren"), f("mind-bending", "Zihin büken"), f("slow-burn", "Yavaş gelişen"), f("fast-paced", "Tempolu"),
            f("scary", "Korkutan"), f("suspenseful", "Gergin tutan"), f("emotional", "Duygulandıran"), f("heartbreaking", "Hüzünlendiren"),
            f("intriguing", "Merak uyandıran"), f("plot-twist", "Ters köşe yapan"), f("thought-provoking", "Düşündüren"), f("inspiring", "İlham veren"),
            f("funny", "Güldüren"), f("relaxing", "Rahatlatan"), f("dark-tone", "Karanlık atmosferli"), f("nostalgic", "Nostaljik"),
            t("dystopia", "Distopya", future), t("post-apocalyptic", "Kıyamet sonrası", future), t("time-travel", "Zaman yolculuğu", future),
            t("time-loop", "Zaman döngüsü", future), t("parallel-universe", "Paralel evren", future), t("space", "Uzay", future),
            t("aliens", "Uzaylılar", future), t("artificial-intelligence", "Yapay zekâ", future), t("cyberpunk", "Siberpunk", future),
            t("simulation", "Simülasyon", future),
            t("survival", "Hayatta kalma", mystery), t("isolation", "İzolasyon", mystery), t("conspiracy", "Komplo", mystery),
            t("detective", "Dedektiflik", mystery), t("espionage", "Casusluk", mystery), t("heist", "Soygun", mystery),
            t("true-story", "Gerçek hikâye", human), t("coming-of-age", "Büyüme hikâyesi", human), t("friendship", "Dostluk", human),
            t("family-ties", "Aile bağları", human),
            t("mythology", "Mitoloji", fantasy), t("supernatural", "Doğaüstü", fantasy), t("zombies", "Zombiler", fantasy), t("magic", "Büyü", fantasy),
        ]
    }()

    /// GameFilter: genre and feeling groups (IGDB-verified on the Worker).
    static let game: [DiscoveryFilter] = {
        func g(_ k: String, _ l: String) -> DiscoveryFilter { DiscoveryFilter(key: k, label: l, aliases: [k]) }
        func f(_ k: String, _ l: String) -> DiscoveryFilter { DiscoveryFilter(key: k, label: l, genre: false) }
        return [
            g("rpg", "RPG"), g("strategy", "Strateji"), g("action", "Aksiyon"), g("platformer", "Platform"), g("racing", "Yarış"),
            g("puzzle", "Bulmaca"), g("adventure", "Macera"), g("shooter", "Nişancı"), g("fighting", "Dövüş"), g("sports", "Spor"),
            g("simulation", "Simülasyon"), g("indie", "Bağımsız"), g("roguelike", "Roguelike"), g("open-world", "Açık dünya"),
            g("metroidvania", "Metroidvania"), g("tactics", "Taktik"),
            f("relaxing", "Rahatlatan"), f("challenging", "Zorlayan"), f("story-driven", "Hikâyeye sürükleyen"), f("scary", "Korkutan"),
            f("cozy", "Sıcacık"), f("fast-paced", "Tempolu"), f("competitive", "Rekabetçi"), f("atmospheric", "Atmosferik"),
            f("thought-provoking", "Düşündüren"), f("nostalgic", "Nostaljik"), f("co-op", "Birlikte oynanan"), f("short-session", "Kısa seanslık"),
        ]
    }()
}

/// GamePlatformGroup
struct GamePlatformGroup: Hashable, Identifiable {
    let key: String
    let label: String
    var id: String { key }
    static let all = [GamePlatformGroup(key: "pc", label: "PC"), GamePlatformGroup(key: "current", label: "Güncel konsollar"),
                      GamePlatformGroup(key: "retro", label: "Retro cihazlar")]
}

/// One poster in the deck: film/series (media) or game. Tags are AI-inferred filter keys.
struct DiscoveryCard: Codable, Hashable, Identifiable {
    var media: MediaBlock
    var tags: [String]
    var reason: String
    var platforms: [String] = []
    var id: String { "\(media.type.rawValue):\(media.provider):\(media.providerId ?? media.providerUrl)" }
}

enum DiscoveryKind: String { case film, game }

enum DiscoveryAction { case skip, addToList, removeFromList, saveDiscovery, saveList, browseList }

/// MediaDiscoveryRepository / GameDiscoveryRepository: local, per-device deck + watchlist + excluded titles.
@Observable
final class DiscoveryStore {
    struct Snapshot: Codable {
        var type = "BOTH"
        var filters: [String] = []
        var cards: [DiscoveryCard] = []
        var later: [DiscoveryCard] = []
        var excluded: [String] = []
    }

    private(set) var film = Snapshot()
    private(set) var game = Snapshot()
    @ObservationIgnored private let persist: Bool
    @ObservationIgnored let service: DiscoveryService

    init(persist: Bool = true, service: DiscoveryService = BackendDiscoveryService()) {
        self.persist = persist
        self.service = service
        if persist {
            film = Self.load("real_media_discovery") ?? Snapshot()
            game = Self.load("real_game_discovery") ?? Snapshot()
        }
    }

    func snapshot(_ kind: DiscoveryKind) -> Snapshot { kind == .film ? film : game }

    private static func load(_ key: String) -> Snapshot? {
        guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(Snapshot.self, from: data)
    }

    private func set(_ kind: DiscoveryKind, _ s: Snapshot) {
        var s = s
        s.cards = Array(s.cards.prefix(40)); s.later = Array(s.later.prefix(40)); s.excluded = Array(s.excluded.suffix(40))
        if kind == .film { film = s } else { game = s }
        guard persist, let data = try? JSONEncoder().encode(s) else { return }
        UserDefaults.standard.set(data, forKey: kind == .film ? "real_media_discovery" : "real_game_discovery")
    }

    func fetch(_ kind: DiscoveryKind, type: String, filters: Set<String>) async throws {
        let prior = snapshot(kind)
        let cards = try await service.recommend(kind: kind, type: type, filters: filters, exclude: Array(prior.excluded.suffix(40)))
        guard !cards.isEmpty else { throw BookError.message("Kataloglarda doğrulanmış bir sonuç bulunamadı. Tekrar deneyebilirsin.") }
        var s = snapshot(kind)
        let excluded = Set(s.excluded)
        let laterKeys = Set(s.later.map(\.id))
        var seen = Set<String>()
        let fresh = cards.filter { !excluded.contains($0.media.title) && !laterKeys.contains($0.id) && seen.insert($0.id).inserted }
        s.type = type
        s.filters = Array(filters)
        s.cards = fresh.sorted { DiscoveryRanking.match($0, filters, kind: kind).count > DiscoveryRanking.match($1, filters, kind: kind).count }
        set(kind, s)
    }

    /// DiscoveryQueueTransitions.apply
    func act(_ kind: DiscoveryKind, _ card: DiscoveryCard, _ action: DiscoveryAction) throws {
        var s = snapshot(kind)
        let remainder = s.cards.filter { $0.id != card.id }
        func rotated() -> [DiscoveryCard] { s.later.contains { $0.id == card.id } ? s.later.filter { $0.id != card.id } + [card] : s.later }
        switch action {
        case .browseList, .saveList: s.later = rotated()
        case .removeFromList: s.later = s.later.filter { $0.id != card.id }
        case .skip, .saveDiscovery:
            s.cards = remainder
            if !s.excluded.contains(card.media.title) { s.excluded.append(card.media.title) }
        case .addToList:
            guard s.later.count < 40 || s.later.contains(where: { $0.id == card.id }) else {
                throw BookError.message(kind == .film ? "İzlenecek listen dolu. Önce bir afiş çıkar." : "Oynanacak listen dolu. Önce bir oyun çıkar.")
            }
            s.cards = remainder
            if !s.later.contains(where: { $0.id == card.id }) { s.later.append(card) }
            if !s.excluded.contains(card.media.title) { s.excluded.append(card.media.title) }
        }
        set(kind, s)
    }

    func seedDemo() {
        func m(_ t: String, _ y: Int, _ g: String, _ type: MediaType = .MOVIE, _ d: String) -> MediaBlock {
            MediaBlock(type: type, providerId: nil, title: t, year: y, genre: g, description: d, provider: "TheTVDB", providerUrl: "https://thetvdb.com/search?query=\(t)")
        }
        film.cards = [
            DiscoveryCard(media: m("Groundhog Day", 1993, "Comedy", .MOVIE, "Bir hava durumu sunucusu aynı günü tekrar tekrar yaşamaya başlar."), tags: ["time-loop", "funny"], reason: "Zaman döngüsü ve hafif bir mizah"),
            DiscoveryCard(media: m("Edge of Tomorrow", 2014, "Action", .MOVIE, "Bir asker, öldükçe aynı savaşın başına geri döner."), tags: ["time-loop"], reason: "Tempolu bir zaman döngüsü"),
            DiscoveryCard(media: m("Palm Springs", 2020, "Comedy", .MOVIE, "Bir düğünde tanışan iki kişi aynı güne sıkışır."), tags: ["time-loop", "feel-good"], reason: "İyi hissettiren bir döngü"),
        ]
        film.filters = ["comedy", "time-loop", "funny"]
        film.later = [film.cards[2]]
    }
}

/// DiscoveryRanking.match: genre keys match catalog genres; theme/feeling keys come from AI tags (marked " · AI").
enum DiscoveryRanking {
    static func normalized(_ s: String) -> String {
        s.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .init(identifier: "en"))
            .replacingOccurrences(of: "[^a-z0-9]+", with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces)
    }

    static func match(_ card: DiscoveryCard, _ selected: Set<String>, kind: DiscoveryKind) -> (count: Int, inferred: Bool) {
        let catalog = kind == .film ? DiscoveryFilter.film : DiscoveryFilter.game
        let genres = (card.media.genre ?? "").split(whereSeparator: { ",/|".contains($0) }).map { normalized(String($0)) }.filter { !$0.isEmpty }
        var count = 0
        var inferred = false
        for key in selected {
            guard let f = catalog.first(where: { $0.key == key }) else { continue }
            if f.genre && !genres.isEmpty {
                if f.aliases.contains(where: { genres.contains(normalized($0)) }) { count += 1 }
            } else if card.tags.contains(key) { count += 1; inferred = true }
        }
        return (count, inferred)
    }

    static func displayGenre(_ genre: String?) -> String? {
        guard let first = genre?.split(whereSeparator: { ",/|".contains($0) }).map({ $0.trimmingCharacters(in: .whitespaces) }).first(where: { !$0.isEmpty }) else { return nil }
        let n = normalized(first)
        return DiscoveryFilter.film.first { $0.genre && $0.aliases.contains { normalized($0) == n } }?.label ?? first
    }
}

// MARK: Services

protocol DiscoveryService {
    func recommend(kind: DiscoveryKind, type: String, filters: Set<String>, exclude: [String]) async throws -> [DiscoveryCard]
    var available: Bool { get }
}

/// /api/media-recommendations and /api/game-recommendations need a REAL (Firebase) sign-in + App Check token.
/// iOS sign-in is not configured yet, so this reports the same message Android shows when no user is signed in.
struct BackendDiscoveryService: DiscoveryService {
    static let origin = "https://real-backend-staging.cagrigul1907.workers.dev"
    var idToken: () async -> String? = { nil }

    var available: Bool { false }

    func recommend(kind: DiscoveryKind, type: String, filters: Set<String>, exclude: [String]) async throws -> [DiscoveryCard] {
        guard let token = await idToken() else { throw BookError.message("Öneriler için REAL'e giriş yap.") }
        var request = URLRequest(url: URL(string: Self.origin + (kind == .film ? "/api/media-recommendations" : "/api/game-recommendations"))!)
        request.httpMethod = "POST"
        request.timeoutInterval = 25
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["type": type, "filters": Array(filters), "exclude": exclude])
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) == true else {
            throw BookError.message("Öneriler şu anda alınamadı. Biraz sonra tekrar dene.")
        }
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let list = (json?["candidates"] as? [[String: Any]]) ?? []
        return list.prefix(12).compactMap { item in
            guard let title = (item["title"] as? String)?.trimmingCharacters(in: .whitespaces), !title.isEmpty else { return nil }
            let t = MediaType(rawValue: ((item["type"] as? String) ?? "MOVIE").uppercased()) ?? .MOVIE
            let media = MediaBlock(type: t, providerId: item["providerId"] as? String, title: title, year: item["year"] as? Int,
                                   genre: item["genre"] as? String, description: item["description"] as? String,
                                   artworkUrl: item["artworkUrl"] as? String, provider: (item["provider"] as? String) ?? "TheTVDB",
                                   providerUrl: (item["providerUrl"] as? String) ?? "https://thetvdb.com")
            return DiscoveryCard(media: media, tags: (item["tags"] as? [String]) ?? [], reason: String(((item["reason"] as? String) ?? "").prefix(220)))
        }
    }
}

/// Screenshot / demo mode: returns the seeded deck without the network.
struct DemoDiscoveryService: DiscoveryService {
    var available: Bool { true }
    func recommend(kind: DiscoveryKind, type: String, filters: Set<String>, exclude: [String]) async throws -> [DiscoveryCard] {
        try await Task.sleep(nanoseconds: 1_200_000_000)
        let store = DiscoveryStore(persist: false, service: self)
        store.seedDemo()
        return store.film.cards
    }
}

/// MediaDetailRepository: TVmaze cast + summary (public API, no sign-in needed).
struct MediaCastMember: Identifiable, Hashable {
    let id: String
    let name: String
    let role: String?
    let imageUrl: URL?
}

struct MediaDetail {
    var description: String?
    var durationMillis: Int64?
    var cast: [MediaCastMember]
}

enum MediaDetailRepository {
    static func load(_ media: MediaBlock) async throws -> MediaDetail {
        guard media.type == .TV, media.provider.lowercased() == "tvmaze", let id = media.providerId,
              id.range(of: "^[1-9]\\d{0,9}$", options: .regularExpression) != nil,
              let url = URL(string: "https://api.tvmaze.com/shows/\(id)?embed=cast") else {
            throw BookError.message("Bu kaynağın oyuncu bilgisi desteklenmiyor.")
        }
        var request = URLRequest(url: url)
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, _) = try await URLSession.shared.data(for: request)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw BookError.message("Oyuncular şu anda alınamadı.") }
        let rows = ((json["_embedded"] as? [String: Any])?["cast"] as? [[String: Any]]) ?? []
        var seen = Set<String>()
        let cast: [MediaCastMember] = rows.prefix(100).compactMap { row in
            guard let person = row["person"] as? [String: Any], let name = person["name"] as? String else { return nil }
            let pid = (person["id"] as? Int).map(String.init) ?? name
            guard seen.insert(pid).inserted else { return nil }
            let img = (person["image"] as? [String: Any]).flatMap { ($0["medium"] as? String) ?? ($0["original"] as? String) }
            let safe = img.flatMap(URL.init(string:)).flatMap { u in u.scheme == "https" && (u.host == "static.tvmaze.com") ? u : nil }
            return MediaCastMember(id: pid, name: name, role: (row["character"] as? [String: Any])?["name"] as? String, imageUrl: safe)
        }
        let runtime = (json["averageRuntime"] as? Int) ?? (json["runtime"] as? Int)
        let summary = (json["summary"] as? String).map { BookDocuments.decodeEntities($0.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)) }
        return MediaDetail(description: summary, durationMillis: runtime.map { Int64($0) * 60_000 }, cast: Array(cast.prefix(24)))
    }
}

func mediaDurationLabel(_ millis: Int64?, series: Bool) -> String? {
    guard let v = millis, (60_000...86_400_000).contains(v) else { return nil }
    let minutes = Int(v / 60_000)
    let text = minutes >= 60 ? "\(minutes / 60) sa\(minutes % 60 > 0 ? " \(minutes % 60) dk" : "")" : "\(minutes) dk"
    return series ? "\(text) / bölüm" : text
}
