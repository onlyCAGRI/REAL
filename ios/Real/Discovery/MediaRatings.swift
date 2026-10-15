import Foundation
import Observation

/// Same key as `DiscoveryCard.id`, so "İzledim" posters and ratings meet.
func ratingKey(_ media: MediaBlock) -> String { "\(media.type.rawValue):\(media.provider):\(media.providerId ?? media.providerUrl)" }

/// One episode; `summary` is the short reminder shown while rating. `translated`: name / summary are already Turkish.
struct SeriesEpisode: Codable, Hashable, Identifiable {
    var season: Int
    var number: Int
    var name: String
    var summary: String?
    var translated = false
    var key: String { "\(season):\(number)" }
    var id: String { key }
}

/// MediaRatings.kt: a film or series in "Puanlarım". Films carry their own `score`; a series has no score of its own,
/// it is the average of its rated `episodes` ("season:number" → score). `addedAt` nil = from the past ("eskiden").
struct RatingEntry: Codable, Hashable, Identifiable {
    var key: String
    var media: MediaBlock
    var score: Double?
    var episodes: [String: Double] = [:]
    var addedAt: Int64?
    var order: Int64
    var id: String { key }
    var series: Bool { media.type == .TV }
    var value: Double? {
        guard series else { return score }
        guard !episodes.isEmpty else { return nil }
        return (episodes.values.reduce(0, +) / Double(episodes.count) * 10).rounded() / 10
    }
}

/// MediaRatingRepository: local. Watched posters ("İzledim") come in as the past; removed titles do not come back.
@MainActor
@Observable
final class MediaRatingStore {
    static let shared = MediaRatingStore()
    private(set) var entries: [RatingEntry] = []
    @ObservationIgnored private var removed: Set<String> = []
    private struct State: Codable { var entries: [RatingEntry]; var removed: [String] }
    private static let key = "real_media_ratings"
    private static let cachePrefixes = ["real_episodes_", "real_tr_", "real_film_tr_"]

    private init() {
        if let data = UserDefaults.standard.data(forKey: Self.key), let s = try? JSONDecoder().decode(State.self, from: data) {
            entries = s.entries; removed = Set(s.removed)
        }
    }

    private func save() {
        let s = State(entries: entries, removed: Array(removed.suffix(2000)))
        if let data = try? JSONEncoder().encode(s) { UserDefaults.standard.set(data, forKey: Self.key) }
    }

    private static var now: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    /// "İzledim" posters not yet in the list (a suggested title marked watched was usually seen long ago).
    func importWatched(_ watched: [DiscoveryCard]) {
        let known = Set(entries.map(\.key)).union(removed)
        let fresh = watched.filter { !known.contains($0.id) }
        guard !fresh.isEmpty else { return }
        let now = Self.now
        entries += fresh.enumerated().map { i, c in RatingEntry(key: c.id, media: c.media, score: nil, addedAt: nil, order: now + Int64(i)) }
        save()
    }

    private func upsert(_ media: MediaBlock, past: Bool, _ change: (inout RatingEntry) -> Void) {
        let key = ratingKey(media)
        var e = entries.first { $0.key == key } ?? RatingEntry(key: key, media: media, score: nil, addedAt: past ? nil : Self.now, order: Self.now)
        change(&e)
        entries = entries.filter { $0.key != key } + [e]
        removed.remove(key)
        save()
    }

    func rate(_ media: MediaBlock, _ score: Double, past: Bool = false) { upsert(media, past: past) { $0.score = score } }
    func add(_ media: MediaBlock, past: Bool) { upsert(media, past: past) { _ in } }
    func rateEpisodes(_ media: MediaBlock, _ scores: [String: Double?]) {
        upsert(media, past: false) { e in for (k, v) in scores { e.episodes[k] = v } }
    }
    func remove(_ key: String) { entries.removeAll { $0.key == key }; removed.insert(key); save() }

    /// Settings "Puanlarımı sıfırla": empty list; earlier "İzledim" posters do not come back, new ones do.
    func reset(watched: [DiscoveryCard]) {
        entries = []; removed = Set(watched.map(\.id)); save()
        for k in UserDefaults.standard.dictionaryRepresentation().keys where Self.cachePrefixes.contains(where: { k.hasPrefix($0) }) {
            UserDefaults.standard.removeObject(forKey: k)
        }
    }

    // MARK: Network

    private struct EpisodeCache: Codable { var at: Double; var list: [SeriesEpisode] }

    private static func getJSON(_ url: URL) async throws -> Any {
        var request = URLRequest(url: url, timeoutInterval: 15)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard ((response as? HTTPURLResponse)?.statusCode ?? 0) / 100 == 2 else { throw URLError(.badServerResponse) }
        return try JSONSerialization.jsonObject(with: data)
    }

    private static func plain(_ html: String?) -> String? {
        guard let html, !html.isEmpty else { return nil }
        let t = BookDocuments.decodeEntities(html.replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression))
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces)
        return t.isEmpty ? nil : t
    }

    /// Episodes from TVmaze, names and summaries in Turkish from TheTVDB where it has them (REAL server). A week's cache.
    func episodes(_ media: MediaBlock) async throws -> [SeriesEpisode] {
        let cacheKey = "real_episodes_" + ratingKey(media)
        let cached = UserDefaults.standard.data(forKey: cacheKey).flatMap { try? JSONDecoder().decode(EpisodeCache.self, from: $0) }
        if let cached, Date().timeIntervalSince1970 - cached.at < 7 * 86_400 { return cached.list }
        let show: [String: Any]
        do {
            let url: URL?
            if media.provider.lowercased() == "tvmaze", let id = media.providerId, id.allSatisfy(\.isNumber) {
                url = URL(string: "https://api.tvmaze.com/shows/\(id)?embed=episodes")
            } else {
                url = URL(string: "https://api.tvmaze.com/singlesearch/shows?embed=episodes&q=" +
                          (media.title.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""))
            }
            guard let url, let json = try await Self.getJSON(url) as? [String: Any] else { throw URLError(.badURL) }
            show = json
        } catch {
            if let cached { return cached.list }
            throw BookError.message("Bölümler alınamadı. Bağlantını kontrol edip tekrar dene.")
        }
        var seen = Set<String>()
        var list: [SeriesEpisode] = (((show["_embedded"] as? [String: Any])?["episodes"] as? [[String: Any]]) ?? []).compactMap { o in
            guard let s = o["season"] as? Int, s > 0, let n = o["number"] as? Int, n > 0 else { return nil } // Specials have no number.
            let ep = SeriesEpisode(season: s, number: n, name: (o["name"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "Bölüm \(n)",
                                   summary: Self.plain(o["summary"] as? String))
            return seen.insert(ep.key).inserted ? ep : nil
        }.sorted { ($0.season, $0.number) < ($1.season, $1.number) }
        let externals = show["externals"] as? [String: Any]
        let tvdb = (externals?["thetvdb"] as? Int).map(String.init) ?? (externals?["thetvdb"] as? String)
        if let tvdb, tvdb != "0", let turkish = await Self.tvdbTurkish(tvdb) {
            list = list.map { ep in
                guard let tr = turkish[ep.key] else { return ep }
                var e = ep
                if let n = tr.name { e.name = n }
                if let s = tr.summary { e.summary = s }
                e.translated = tr.summary != nil || ep.summary == nil
                return e
            }
        }
        if let data = try? JSONEncoder().encode(EpisodeCache(at: Date().timeIntervalSince1970, list: list)) {
            UserDefaults.standard.set(data, forKey: cacheKey)
        }
        return list
    }

    private static func tvdbTurkish(_ tvdbId: String) async -> [String: (name: String?, summary: String?)]? {
        guard let r = try? await RealBackend.post("/api/media-episodes", ["tvdbId": tvdbId, "lang": "tur"], timeout: 20),
              let rows = r["episodes"] as? [[String: Any]] else { return nil }
        var out: [String: (name: String?, summary: String?)] = [:]
        for o in rows {
            guard let s = o["season"] as? Int, let n = o["number"] as? Int else { continue }
            out["\(s):\(n)"] = ((o["name"] as? String).flatMap { $0.isEmpty ? nil : $0 }, (o["overview"] as? String).flatMap { $0.isEmpty ? nil : $0 })
        }
        return out
    }

    /// REAL server AI translation into Turkish; nil when it is unavailable.
    private static func aiTranslate(_ texts: [String]) async -> [String]? {
        guard let r = try? await RealBackend.post("/api/media-translate", ["texts": texts, "lang": "tr"], timeout: 25),
              let out = r["translations"] as? [String], out.count == texts.count else { return nil }
        return out
    }

    /// One episode's name and summary translated by AI when TheTVDB has no Turkish; kept on the phone.
    func translate(_ media: MediaBlock, _ episode: SeriesEpisode) async -> SeriesEpisode? {
        if episode.translated { return episode }
        let key = "real_tr_\(ratingKey(media))_\(episode.key)"
        if let saved = UserDefaults.standard.dictionary(forKey: key) as? [String: String] {
            var e = episode; e.name = saved["name"].flatMap { $0.isEmpty ? nil : $0 } ?? e.name
            e.summary = saved["summary"].flatMap { $0.isEmpty ? nil : $0 } ?? e.summary; e.translated = true
            return e
        }
        let texts = [episode.name] + (episode.summary.map { [String($0.prefix(1500))] } ?? [])
        guard let out = await Self.aiTranslate(texts) else { return nil }
        var e = episode
        e.name = out[0].isEmpty ? episode.name : out[0]
        if episode.summary != nil, out.count > 1 { e.summary = out[1].isEmpty ? episode.summary : out[1] }
        e.translated = true
        UserDefaults.standard.set(["name": e.name, "summary": e.summary ?? ""], forKey: key)
        return e
    }

    /// A film's reminder in Turkish: TheTVDB's own Turkish first, otherwise AI once (kept). Nil = no description.
    func filmSummary(_ media: MediaBlock) async -> String? {
        let key = "real_film_tr_" + ratingKey(media)
        if let saved = UserDefaults.standard.string(forKey: key) { return saved.isEmpty ? nil : saved }
        var text = media.description.flatMap { $0.isEmpty ? nil : $0 }
        var turkish = false
        if media.type == .MOVIE, media.provider.lowercased() == "thetvdb", let id = media.providerId,
           id.range(of: "^[1-9]\\d{0,9}$", options: .regularExpression) != nil,
           let r = try? await RealBackend.post("/api/media-details", ["provider": "TheTVDB", "type": "MOVIE", "providerId": id], timeout: 15),
           let d = r["details"] as? [String: Any] {
            if let desc = d["description"] as? String, !desc.isEmpty { text = desc }
            turkish = (d["language"] as? String) == "tur"
        }
        guard let source = text else { UserDefaults.standard.set("", forKey: key); return nil }
        if turkish { UserDefaults.standard.set(source, forKey: key); return source }
        guard let out = await Self.aiTranslate([String(source.prefix(1500))])?.first, !out.isEmpty else { return source } // Not saved: retry later.
        UserDefaults.standard.set(out, forKey: key)
        return out
    }
}
