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
    /// Feelings that only repeated a genre (removed); saved selections read as that genre.
    static let merged = ["scary": "horror", "funny": "comedy", "suspenseful": "thriller", "intriguing": "mystery"]

    static let film: [DiscoveryFilter] = {
        func g(_ k: String, _ l: String, _ a: [String] = []) -> DiscoveryFilter { DiscoveryFilter(key: k, label: l, aliases: a.isEmpty ? [k] : a) }
        func f(_ k: String, _ l: String) -> DiscoveryFilter { DiscoveryFilter(key: k, label: l, genre: false) }
        func t(_ k: String, _ l: String, _ group: String) -> DiscoveryFilter { DiscoveryFilter(key: k, label: l, genre: false, themeGroup: group) }
        let future = "Gelecek ve bilim kurgu", mystery = "Gizem ve mücadele", human = "İnsan ve yaşam", fantasy = "Fantastik ve doğaüstü"
        return [
            g("action", "Aksiyon"), g("adventure", "Macera"), g("comedy", "Komedi"), g("drama", "Dram"), g("romance", "Romantik"),
            g("science-fiction", "Bilim kurgu", ["science fiction", "sci-fi", "science-fiction"]), g("fantasy", "Fantastik"),
            g("thriller", "Gerilim"), g("mystery", "Gizem"), g("crime", "Suç"), g("horror", "Korku"), g("animation", "Animasyon"),
            g("anime", "Anime"), g("documentary", "Belgesel"), g("family", "Aile"), g("history", "Tarih", ["history", "historical"]), g("war", "Savaş"),
            f("feel-good", "İyi hissettiren"), f("mind-bending", "Zihin büken"), f("slow-burn", "Yavaş gelişen"), f("fast-paced", "Tempolu"),
            f("emotional", "Duygulandıran"), f("heartbreaking", "Hüzünlendiren"),
            f("plot-twist", "Ters köşe yapan"), f("thought-provoking", "Düşündüren"), f("inspiring", "İlham veren"),
            f("relaxing", "Rahatlatan"), f("dark-tone", "Karanlık atmosferli"), f("nostalgic", "Nostaljik"),
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
            g("horror", "Korku"), g("survival", "Hayatta kalma"), g("souls-like", "Souls-like"), g("sandbox", "Sandbox"),
            g("stealth", "Gizlilik"), g("card", "Kart / deste"), g("visual-novel", "Görsel roman"), g("rhythm", "Ritim / müzik"),
            g("management", "Şehir kurma / yönetim"), g("hack-and-slash", "Hack and slash"),
            f("relaxing", "Rahatlatan"), f("challenging", "Zorlayan"), f("story-driven", "Hikâyeye sürükleyen"),
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
    static let all = [GamePlatformGroup(key: "pc", label: "PC"), GamePlatformGroup(key: "playstation", label: "PlayStation"),
                      GamePlatformGroup(key: "xbox", label: "Xbox"), GamePlatformGroup(key: "switch", label: "Switch"),
                      GamePlatformGroup(key: "mobile", label: "Mobil"), GamePlatformGroup(key: "retro", label: "Retro")]

    /// What a server from before the split understands: consoles as one group, no mobile.
    static func legacy(_ keys: Set<String>) -> Set<String> {
        Set(keys.compactMap { k -> String? in
            switch k { case "playstation", "xbox", "switch": return "current"; case "mobile": return nil; default: return k }
        })
    }
}

/// One poster in the deck: film/series (media) or game. Tags are AI-inferred filter keys.
struct DiscoveryCard: Codable, Hashable, Identifiable {
    var media: MediaBlock
    var tags: [String]
    var reason: String
    var platforms: [String] = []
    /// The title the recommender used; the catalog title can differ, so both count as seen.
    var aiTitle: String?
    var id: String { "\(media.type.rawValue):\(media.provider):\(media.providerId ?? media.providerUrl)" }
    var seenTitles: [String] {
        var out = [media.title]
        if let aiTitle, !aiTitle.isEmpty, aiTitle != media.title { out.append(aiTitle) }
        return out
    }
}

/// Seen titles kept locally; the service takes only the latest `discoveryExcludeSent`.
let discoverySeenLimit = 400
let discoveryExcludeSent = 40

enum DiscoveryKind: String { case film, game }

/// Right swipe saves to the list (list: keep, next), left passes (list: remove); "İzledim / Oynadım" = done, never
/// suggested again (kept for the coming rating list).
enum DiscoveryAction { case skip, addToList, removeFromList, saveDiscovery, saveList, browseList, watched }

/// MediaDiscoveryRepository / GameDiscoveryRepository: local, per-device deck + watchlist + excluded titles.
@Observable
final class DiscoveryStore {
    struct Snapshot: Codable {
        var type = "BOTH"
        var filters: [String] = []
        var cards: [DiscoveryCard] = []
        var later: [DiscoveryCard] = []
        var excluded: [String] = []
        /// "İzledim / Oynadım": filtered here in the app (the guarantee); only the latest 10 go into a request.
        var watched: [DiscoveryCard] = []

        init() {}
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            type = (try? c.decodeIfPresent(String.self, forKey: .type)) ?? "BOTH"
            filters = (try? c.decodeIfPresent([String].self, forKey: .filters)) ?? []
            cards = (try? c.decodeIfPresent([DiscoveryCard].self, forKey: .cards)) ?? []
            later = (try? c.decodeIfPresent([DiscoveryCard].self, forKey: .later)) ?? []
            excluded = (try? c.decodeIfPresent([String].self, forKey: .excluded)) ?? []
            watched = (try? c.decodeIfPresent([DiscoveryCard].self, forKey: .watched)) ?? []
        }
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
        guard let data = UserDefaults.standard.data(forKey: key), var s = try? JSONDecoder().decode(Snapshot.self, from: data) else { return nil }
        let merged = key == "real_game_discovery" ? ["scary": "horror"] : DiscoveryFilter.merged
        var seen = Set<String>()
        s.filters = s.filters.map { merged[$0] ?? $0 }.filter { seen.insert($0).inserted }
        return s
    }

    private func set(_ kind: DiscoveryKind, _ s: Snapshot) {
        var s = s
        s.cards = Array(s.cards.prefix(60)); s.later = Array(s.later.prefix(40)); s.excluded = Array(s.excluded.suffix(discoverySeenLimit))
        s.watched = Array(s.watched.suffix(2000))
        if kind == .film { film = s } else { game = s }
        guard persist, let data = try? JSONEncoder().encode(s) else { return }
        UserDefaults.standard.set(data, forKey: kind == .film ? "real_media_discovery" : "real_game_discovery")
    }

    /// Adds fresh posters behind the ones still in the deck. Seen, saved and queued titles are dropped here too (the
    /// service only takes the latest 40), and a round that brings back mostly known titles is followed by another with
    /// those excluded, so a repeating recommender never leaves the deck empty.
    /// `deckFilters`: the person's choice the deck belongs to; a widened request (fewer keys, when the exact choice
    /// found too few titles) adds to that same deck.
    func fetch(_ kind: DiscoveryKind, type: String, filters: Set<String>, platforms: Set<String> = [], deckFilters: Set<String>? = nil) async throws {
        let prior = snapshot(kind)
        let deck = deckFilters ?? filters
        let sameDeck = prior.type == type && Set(prior.filters) == deck
        let keep = sameDeck ? prior.cards : []
        func norm(_ t: String) -> String { t.trimmingCharacters(in: .whitespaces).lowercased() }
        var known = Set((prior.excluded + prior.later.flatMap(\.seenTitles) + keep.flatMap(\.seenTitles) + prior.watched.flatMap(\.seenTitles)).map(norm))
        var knownKeys = Set((prior.later + keep + prior.watched).map(\.id))
        var asked: [String] = []
        var fresh: [DiscoveryCard] = []
        var lastError: Error?
        let catalog = kind == .film ? DiscoveryFilter.film : DiscoveryFilter.game
        let genreKeys = Set(filters.filter { k in catalog.first { $0.key == k }?.genre == true })
        for _ in 0..<(kind == .film ? 3 : 2) where fresh.count < 8 {
            var exclude = prior.watched.suffix(10).flatMap(\.seenTitles) + prior.excluded + keep.flatMap(\.seenTitles) + asked
            var dedup = Set<String>()
            exclude = Array(exclude.reversed().filter { dedup.insert($0).inserted }.reversed())
            let cards: [DiscoveryCard]
            do {
                cards = try await service.recommend(kind: kind, type: type, filters: filters, platforms: platforms,
                                                    exclude: Array(exclude.suffix(discoveryExcludeSent)))
            } catch {
                if fresh.isEmpty && keep.isEmpty { throw error }
                lastError = error
                break
            }
            asked += cards.flatMap(\.seenTitles)
            for card in cards where !knownKeys.contains(card.id) && !card.seenTitles.contains(where: { known.contains(norm($0)) }) {
                knownKeys.insert(card.id)
                card.seenTitles.forEach { known.insert(norm($0)) }
                // Games are confirmed against IGDB on the server; films / series here against their catalog genres.
                if kind == .film, !genreKeys.isEmpty, DiscoveryRanking.match(card, genreKeys, kind: kind).count == 0 { continue }
                fresh.append(card)
            }
        }
        // Nothing new also ends an automatic refill, so it can never loop the request.
        guard !fresh.isEmpty else {
            throw lastError ?? BookError.message("Bu seçimlerle yeni afiş kalmadı. Bir iki seçimi kaldırıp tekrar dene.")
        }
        var s = snapshot(kind)
        let ranked = fresh.sorted { DiscoveryRanking.match($0, filters, kind: kind).count > DiscoveryRanking.match($1, filters, kind: kind).count }
        s.cards = (sameDeck ? s.cards : []) + ranked
        s.type = type
        s.filters = Array(deck)
        set(kind, s)
    }

    /// Settings "Önerileri sıfırla": passed titles can come back and the decks start over; saved lists and
    /// "İzledim / Oynadım" stay.
    func resetSeen() {
        for kind in [DiscoveryKind.film, .game] {
            var s = snapshot(kind)
            s.cards = []; s.excluded = []
            set(kind, s)
        }
    }

    /// DiscoveryQueueTransitions.apply
    func act(_ kind: DiscoveryKind, _ card: DiscoveryCard, _ action: DiscoveryAction) throws {
        guard AppUpdateGate.writable() else { throw BookError.message(AppUpdateGate.blockedMessage) }
        var s = snapshot(kind)
        let remainder = s.cards.filter { $0.id != card.id }
        func rotated() -> [DiscoveryCard] { s.later.contains { $0.id == card.id } ? s.later.filter { $0.id != card.id } + [card] : s.later }
        switch action {
        case .browseList, .saveList: s.later = rotated()
        case .removeFromList: s.later = s.later.filter { $0.id != card.id }
        case .watched:
            s.cards = remainder
            s.later = s.later.filter { $0.id != card.id }
            s.watched = s.watched.filter { $0.id != card.id } + [card]
            for t in card.seenTitles where !s.excluded.contains(t) { s.excluded.append(t) }
        case .skip, .saveDiscovery:
            s.cards = remainder
            for t in card.seenTitles where !s.excluded.contains(t) { s.excluded.append(t) }
        case .addToList:
            guard s.later.count < 40 || s.later.contains(where: { $0.id == card.id }) else {
                throw BookError.message(kind == .film ? "İzlenecek listen dolu. Önce bir afiş çıkar." : "Oynanacak listen dolu. Önce bir oyun çıkar.")
            }
            s.cards = remainder
            if !s.later.contains(where: { $0.id == card.id }) { s.later.append(card) }
            for t in card.seenTitles where !s.excluded.contains(t) { s.excluded.append(t) }
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
    func recommend(kind: DiscoveryKind, type: String, filters: Set<String>, platforms: Set<String>, exclude: [String]) async throws -> [DiscoveryCard]
    @MainActor var available: Bool { get }
}

/// MediaDiscoveryRepository / GameDiscoveryRepository (Android): /api/media-recommendations and /api/game-recommendations
/// with the REAL sign-in + App Check headers. Film / series candidates come back as titles and are verified against the
/// catalogs (TheTVDB movies, TVmaze series) before they reach the deck; games arrive already IGDB-verified.
struct BackendDiscoveryService: DiscoveryService {
    @MainActor var available: Bool { RealAuth.shared.account != nil }

    func recommend(kind: DiscoveryKind, type: String, filters: Set<String>, platforms: Set<String>, exclude: [String]) async throws -> [DiscoveryCard] {
        do {
            try await AiConsent.require(.discovery)
            return kind == .film
                ? try await films(type: type, filters: filters, exclude: exclude)
                : try await games(filters: filters, platforms: platforms, exclude: exclude)
        } catch let e as BookError {
            throw e
        } catch let declined as AiConsent.Declined {
            throw BookError.message(declined.localizedDescription)
        } catch {
            throw BookError.message(RealBackend.message(for: error))
        }
    }

    private func films(type: String, filters: Set<String>, exclude: [String]) async throws -> [DiscoveryCard] {
        let json: [String: Any]
        do {
            json = try await RealBackend.post("/api/media-recommendations",
                                              ["type": type, "filters": Array(filters), "exclude": Array(exclude.suffix(40))], timeout: 25)
        } catch let failure as RealBackend.Failure where failure.status == 400 && filters.contains("anime") {
            // A server from before "anime" does not know it: ask without it (the genre check still applies here).
            json = try await RealBackend.post("/api/media-recommendations",
                                              ["type": type, "filters": Array(filters.subtracting(["anime"])), "exclude": Array(exclude.suffix(40))], timeout: 25)
        }
        guard let list = json["candidates"] as? [[String: Any]] else { throw BookError.message("Öneri yanıtı geçersiz.") }
        let valid = Set(DiscoveryFilter.film.map(\.key))
        // Verify the candidates against the catalogs (skipping titles already seen), a few at a time, keeping the AI's order.
        let skip = Set(exclude.map { $0.trimmingCharacters(in: .whitespaces).lowercased() })
        let unseen = list.filter { !skip.contains((($0["title"] as? String) ?? "").trimmingCharacters(in: .whitespaces).lowercased()) }
        return await withTaskGroup(of: (Int, DiscoveryCard?).self) { group in
            var results: [(Int, DiscoveryCard?)] = []
            for (index, item) in unseen.prefix(16).enumerated() {
                if index >= 4, let done = await group.next() { results.append(done) }
                group.addTask {
                    guard let title = (item["title"] as? String)?.trimmingCharacters(in: .whitespaces), !title.isEmpty,
                          let kind = MediaType(rawValue: ((item["type"] as? String) ?? "").uppercased()),
                          type == "BOTH" || kind.rawValue == type else { return (index, nil) }
                    let year = (item["year"] as? Int).flatMap { $0 > 0 ? $0 : nil }
                    guard let media = await MediaCatalog.resolve(title: String(title.prefix(180)), year: year, type: kind) else { return (index, nil) }
                    let tags = ((item["tags"] as? [String]) ?? []).filter { valid.contains($0) }
                    return (index, DiscoveryCard(media: media, tags: tags, reason: String(((item["reason"] as? String) ?? "").prefix(220)),
                                                 aiTitle: String(title.prefix(180))))
                }
            }
            for await done in group { results.append(done) }
            return results.sorted { $0.0 < $1.0 }.compactMap { $0.1 }
        }
    }

    private func games(filters: Set<String>, platforms: Set<String>, exclude: [String]) async throws -> [DiscoveryCard] {
        let json: [String: Any]
        do {
            do {
                json = try await RealBackend.post("/api/game-recommendations",
                                                  ["filters": Array(filters), "platforms": Array(platforms), "exclude": Array(exclude.suffix(40))], timeout: 40)
            } catch let failure as RealBackend.Failure where failure.status == 400 {
                // A server from before the new genres / platforms: ask once more in its terms.
                let old: Set<String> = ["rpg", "strategy", "action", "platformer", "racing", "puzzle", "adventure", "shooter", "fighting",
                                        "sports", "simulation", "indie", "roguelike", "open-world", "metroidvania", "tactics"]
                let legacyFilters = filters.compactMap { k -> String? in
                    if k == "horror" { return "scary" }
                    return (DiscoveryFilter.game.first { $0.key == k }?.genre == false || old.contains(k)) ? k : nil
                }
                json = try await RealBackend.post("/api/game-recommendations",
                                                  ["filters": legacyFilters, "platforms": Array(GamePlatformGroup.legacy(platforms)),
                                                   "exclude": Array(exclude.suffix(40))], timeout: 40)
            }
        } catch let failure as RealBackend.Failure where failure.status == 503 {
            throw BookError.message("Oyun kataloğu şu anda hazır değil.")
        }
        guard let list = json["games"] as? [[String: Any]] else { throw BookError.message("Öneri yanıtı geçersiz.") }
        let valid = Set(DiscoveryFilter.game.map(\.key))
        let cards: [DiscoveryCard] = list.compactMap { item in
            guard let title = (item["title"] as? String)?.trimmingCharacters(in: .whitespaces), !title.isEmpty else { return nil }
            let genres = (item["genres"] as? [Any])?.compactMap { ($0 as? String) ?? (($0 as? [String: Any])?["name"] as? String) } ?? []
            let shorts = (item["platforms"] as? [Any])?.compactMap { ($0 as? String) ?? (($0 as? [String: Any])?["short"] as? String) } ?? []
            let art = (item["artworkUrl"] as? String).flatMap { $0.hasPrefix("https://") ? $0 : nil }
            let media = MediaBlock(type: .MOVIE, providerId: (item["providerId"]).map { "\($0)" }, title: title,
                                   year: (item["year"] as? Int).flatMap { $0 > 0 ? $0 : nil },
                                   genre: genres.isEmpty ? nil : genres.joined(separator: ", "),
                                   description: item["description"] as? String, artworkUrl: art, provider: "IGDB",
                                   providerUrl: (item["providerUrl"] as? String) ?? "https://www.igdb.com")
            return DiscoveryCard(media: media, tags: ((item["tags"] as? [String]) ?? []).filter { valid.contains($0) },
                                 reason: String(((item["reason"] as? String) ?? "").prefix(220)), platforms: shorts)
        }
        if cards.isEmpty { throw BookError.message("IGDB'de doğrulanmış bir oyun bulunamadı. Tekrar deneyebilirsin.") }
        return cards
    }
}

/// Screenshot / demo mode: returns the seeded deck without the network.
struct DemoDiscoveryService: DiscoveryService {
    @MainActor var available: Bool { true }
    func recommend(kind: DiscoveryKind, type: String, filters: Set<String>, platforms: Set<String>, exclude: [String]) async throws -> [DiscoveryCard] {
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
    /// A YouTube trailer (TheTVDB movies), opened in the YouTube app or the browser.
    var trailerUrl: URL? = nil
}

enum MediaDetailRepository {
    static func load(_ media: MediaBlock) async throws -> MediaDetail {
        if media.type == .MOVIE, media.provider.lowercased() == "thetvdb", let id = media.providerId,
           id.range(of: "^[1-9]\\d{0,9}$", options: .regularExpression) != nil {
            return try await movie(id)
        }
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

    /// MediaDetailRepository.kt parseMovie: TheTVDB movie details (cast, length, trailer) through the REAL Worker.
    private static func movie(_ id: String) async throws -> MediaDetail {
        let json = try await RealBackend.post("/api/media-details", ["provider": "TheTVDB", "type": "MOVIE", "providerId": id], timeout: 18)
        guard json["ok"] as? Bool == true, let d = json["details"] as? [String: Any],
              d["provider"] as? String == "TheTVDB", d["providerId"] as? String == id else { throw BookError.message("Oyuncular şu anda alınamadı.") }
        var seen = Set<String>()
        let cast: [MediaCastMember] = ((d["cast"] as? [[String: Any]]) ?? []).prefix(24).compactMap { row in
            guard let name = (row["name"] as? String).map({ String($0.prefix(120)) }), !name.isEmpty else { return nil }
            let pid = (row["id"] as? String) ?? name
            guard seen.insert(pid).inserted else { return nil }
            let img = (row["imageUrl"] as? String).flatMap(URL.init(string:)).flatMap { $0.scheme == "https" ? $0 : nil }
            return MediaCastMember(id: pid, name: name, role: (row["role"] as? String).map { String($0.prefix(120)) }, imageUrl: img)
        }
        let duration = ((d["durationMillis"] as? NSNumber)?.int64Value).flatMap { (60_000...86_400_000).contains($0) ? $0 : nil }
        let trailer = (d["trailerUrl"] as? String).flatMap(URL.init(string:)).flatMap { u -> URL? in
            let h = (u.host ?? "").lowercased()
            return u.scheme == "https" && (h == "youtube.com" || h.hasSuffix(".youtube.com") || h == "youtu.be") ? u : nil
        }
        return MediaDetail(description: (d["description"] as? String).map { String($0.prefix(6000)) }, durationMillis: duration, cast: cast, trailerUrl: trailer)
    }
}

func mediaDurationLabel(_ millis: Int64?, series: Bool) -> String? {
    guard let v = millis, (60_000...86_400_000).contains(v) else { return nil }
    let minutes = Int(v / 60_000)
    let text = minutes >= 60 ? "\(minutes / 60) sa\(minutes % 60 > 0 ? " \(minutes % 60) dk" : "")" : "\(minutes) dk"
    return series ? "\(text) / bölüm" : text
}
