import Foundation

/// "Dönem" (MusicEra, Android): one era at a time (none = every era). Songs are kept by their release year; Deezer's
/// decade playlists give the era's artists, and the weekly charts only join for 2020+ and every era.
enum MusicEra: String, CaseIterable, Identifiable, Sendable {
    case now = "era_2020", tens = "era_2010", noughties = "era_2000", before = "era_old"
    var id: String { rawValue }
    var label: String {
        switch self { case .now: return "2020+"; case .tens: return "2010–2020"; case .noughties: return "2000–2010"; case .before: return t("2000 öncesi") }
    }
    var years: ClosedRange<Int> {
        switch self { case .now: return 2020...9999; case .tens: return 2010...2019; case .noughties: return 2000...2009; case .before: return 0...1999 }
    }
    /// Words put in front of the genre search ("2000s rap").
    var terms: [String] {
        switch self { case .now: return []; case .tens: return ["2010s", "10s"]; case .noughties: return ["2000s"]; case .before: return ["90s", "80s"] }
    }
    /// A playlist title that belongs to this decade.
    var titlePattern: String? {
        switch self {
        case .now: return nil
        case .tens: return #"(20)?10'?s|2010"#
        case .noughties: return #"2000'?s|\b00'?s|2000"#
        case .before: return #"[6-9]0'?s|19\d\d"#
        }
    }
    static func of(_ selection: Set<String>) -> MusicEra? { allCases.first { selection.contains($0.rawValue) } }
}

/// At most this many new artist lookups per round (iTunes rate limit); the rest come from a week's cache.
actor MusicLookupBudget {
    private var left: Int
    init(_ n: Int) { left = n }
    func take() -> Bool { guard left > 0 else { return false }; left -= 1; return true }
}

/// MusicSeeds (Android): well-known artists per genre plus the ones in Deezer editors' playlists now, so a genre's big
/// names come in even when this week's chart misses them. Only names come from Deezer; the songs come from Apple.
/// Temporary until the Apple Music API (per-genre charts and playlists) is available.
enum MusicSources {
    /// Global (mostly English) and Türkiye are separate pools, so one long local playlist cannot fill the deck.
    static let deezerGlobal: [String: [String]] = [
        "pop": ["pop hits", "today's hits"], "rock": ["rock hits", "rock"], "hiphop": ["us rap", "hip hop hits"],
        "electronic": ["electronic", "electro"], "dance": ["dance hits", "house"], "alternative": ["indie", "alternative"],
        "rnb": ["r&b hits", "soul"], "jazz": ["jazz"], "classical": ["classical"], "soundtrack": ["soundtrack", "film score"],
        "metal": ["metal"], "latin": ["latin hits", "reggaeton"], "folk": ["folk"], "reggae": ["reggae"], "blues": ["blues"], "country": ["country"],
    ]
    static let deezerLocal: [String: [String]] = [
        "pop": ["türkçe pop"], "rock": ["türkçe rock"], "hiphop": ["türkçe rap"], "folk": ["türk halk"],
    ]

    /// Hand-picked names that always belong to the genre.
    static let artists: [String: [String]] = [
        "pop": ["Taylor Swift", "The Weeknd", "Dua Lipa", "Billie Eilish", "Sabrina Carpenter", "Ariana Grande", "Harry Styles",
                "Olivia Rodrigo", "Bruno Mars", "Ed Sheeran", "Lady Gaga", "Tarkan", "Sezen Aksu", "Mabel Matiz", "Hadise", "Edis"],
        "rock": ["Arctic Monkeys", "Queen", "Nirvana", "Foo Fighters", "Red Hot Chili Peppers", "The Killers", "Imagine Dragons",
                 "Linkin Park", "Guns N' Roses", "AC/DC", "Duman", "maNga", "Teoman", "Barış Manço", "Cem Karaca"],
        "hiphop": ["Travis Scott", "Metro Boomin", "Kendrick Lamar", "Drake", "Future", "21 Savage", "Playboi Carti", "Don Toliver",
                   "Kanye West", "J. Cole", "Tyler, The Creator", "A$AP Rocky", "Eminem", "Lil Uzi Vert", "Ezhel", "UZI", "Lvbel C5", "BLOK3",
                   "Ceza", "Sagopa Kajmer", "Ben Fero"],
        "electronic": ["Daft Punk", "Fred again..", "Disclosure", "Calvin Harris", "Avicii", "Flume", "ODESZA", "Justice",
                       "Kaytranada", "Bonobo", "Peggy Gou", "Mahmut Orhan"],
        "dance": ["David Guetta", "Calvin Harris", "Swedish House Mafia", "Martin Garrix", "Tiësto", "Fisher", "Chris Lake",
                  "Dom Dolla", "John Summit", "Black Coffee", "Kungs", "Mahmut Orhan"],
        "alternative": ["Radiohead", "Tame Impala", "Arctic Monkeys", "The Strokes", "Lana Del Rey", "Cigarettes After Sex",
                        "Glass Animals", "Mac DeMarco", "Phoebe Bridgers", "Gorillaz", "Gaye Su Akyol", "Altın Gün", "Adamlar", "Dolu Kadehi Ters Tut"],
        "rnb": ["SZA", "Frank Ocean", "The Weeknd", "Daniel Caesar", "Brent Faiyaz", "H.E.R.", "Summer Walker", "Giveon",
                "Beyoncé", "Usher", "Jhené Aiko", "Bruno Mars"],
        "jazz": ["Miles Davis", "John Coltrane", "Bill Evans", "Chet Baker", "Ella Fitzgerald", "Herbie Hancock", "Norah Jones",
                 "Kamasi Washington", "Robert Glasper", "Nina Simone", "Kerem Görsev", "İlhan Erşahin"],
        "classical": ["Ludovico Einaudi", "Max Richter", "Johann Sebastian Bach", "Wolfgang Amadeus Mozart", "Ludwig van Beethoven",
                      "Frédéric Chopin", "Claude Debussy", "Ólafur Arnalds", "Yiruma", "Fazıl Say"],
        "soundtrack": ["Hans Zimmer", "John Williams", "Ludwig Göransson", "Ennio Morricone", "Howard Shore", "Ramin Djawadi",
                       "Joe Hisaishi", "Trent Reznor", "Jóhann Jóhannsson", "Toygar Işıklı"],
        "metal": ["Metallica", "Iron Maiden", "System of a Down", "Slipknot", "Tool", "Gojira", "Avenged Sevenfold",
                  "Bring Me The Horizon", "Ghost", "Black Sabbath", "Pentagram"],
        "latin": ["Bad Bunny", "Karol G", "J Balvin", "Rosalía", "Peso Pluma", "Feid", "Rauw Alejandro", "Shakira", "Daddy Yankee", "Ozuna"],
        "folk": ["Bon Iver", "Fleet Foxes", "Mumford & Sons", "Hozier", "Iron & Wine", "Simon & Garfunkel", "Bob Dylan",
                 "Neşet Ertaş", "Kazım Koyuncu", "Fikret Kızılok"],
        "reggae": ["Bob Marley & The Wailers", "Damian Marley", "Peter Tosh", "Chronixx", "Protoje", "Toots & The Maytals", "UB40", "Steel Pulse"],
        "blues": ["B.B. King", "Muddy Waters", "Gary Clark Jr.", "Stevie Ray Vaughan", "John Lee Hooker", "Etta James",
                  "Joe Bonamassa", "Eric Clapton"],
        "country": ["Morgan Wallen", "Zach Bryan", "Luke Combs", "Chris Stapleton", "Kacey Musgraves", "Johnny Cash",
                    "Dolly Parton", "Tyler Childers", "Post Malone"],
    ]

    private static let notCurrent = #"\d0'?s|19\d\d|20[01]\d|old ?school|classic|klasik|throwback|nostalji|legend|efsane"#
    private static let background = #"lo-?fi|chill|sleep|study|kids|çocuk|skate"#
    private static let trendPrefix = "real_music_trend_"
    private static let artistPrefix = "real_music_artist_"

    private static func matches(_ text: String, _ pattern: String) -> Bool {
        text.range(of: pattern, options: [.regularExpression, .caseInsensitive]) != nil
    }

    static func inEra(_ card: MusicCard, _ era: MusicEra?) -> Bool {
        guard let era else { return true }
        guard let y = card.year else { return false }
        return era.years.contains(y)
    }

    /// Two artist songs, then one chart song, and so on.
    static func weave(_ seeds: [MusicCard], _ charts: [MusicCard]) -> [MusicCard] {
        var out: [MusicCard] = [], seen = Set<String>(), s = 0, c = 0
        while s < seeds.count || c < charts.count {
            for _ in 0..<2 where s < seeds.count { if seen.insert(seeds[s].id).inserted { out.append(seeds[s]) }; s += 1 }
            if c < charts.count { if seen.insert(charts[c].id).inserted { out.append(charts[c]) }; c += 1 }
        }
        return out
    }

    /// Three charting now worldwide, one barely known, one from Türkiye, the rest hand-picked; cached ones first.
    static func seedSongs(country: String, genre: MusicGenre, known: Set<String>, budget: MusicLookupBudget, era: MusicEra?) async -> [MusicCard] {
        let global = await trending(genre.key, era: era, local: false)
        let local = (era == nil || era == .now) ? await trending(genre.key, era: era, local: true) : []
        let taken = Set((global + local).map { $0.lowercased() })
        let curated = (artists[genre.key] ?? []).filter { !taken.contains($0.lowercased()) }
        func pick(_ list: [String], _ n: Int, not: [String] = []) -> [String] {
            Array(list.filter { !not.contains($0) }.shuffled().sorted { (artistCache(country, $0) != nil ? 0 : 1) < (artistCache(country, $1) != nil ? 0 : 1) }.prefix(n))
        }
        let fromTrend = pick(Array(global.prefix(15)), 3) + pick(Array(global.dropFirst(15)), 1)
        let fromLocal = pick(Array(local.prefix(10)), 1, not: fromTrend)
        let picked = fromTrend + fromLocal + pick(curated, max(0, 6 - fromTrend.count - fromLocal.count))
        return await withTaskGroup(of: [MusicCard].self) { group in
            for name in picked {
                group.addTask {
                    var songs = artistCache(country, name)
                    if songs == nil, await budget.take(), let fetched = try? await artistSongs(country: country, name: name, genre: genre.key) {
                        saveArtist(country, name, fetched); songs = fetched
                    }
                    let usable = (songs ?? []).filter { !known.contains($0.id) && inEra($0, era) }.shuffled()
                    return Array(usable.sorted { ($0.music.previewUrl != nil ? 0 : 1) < ($1.music.previewUrl != nil ? 0 : 1) }.prefix(3))
                }
            }
            var all: [MusicCard] = []
            for await part in group { all += part }
            return all.shuffled()
        }
    }

    /// Artists in Deezer's editor playlists for the genre (and era) now: in more playlists first, then more songs.
    /// Refreshed once a day, so a new name that starts charting comes in by itself.
    static func trending(_ genre: String, era: MusicEra?, local: Bool) async -> [String] {
        let key = "\(trendPrefix)\(genre)_\(era?.rawValue ?? "all")_\(local ? "tr" : "world")"
        if let saved = UserDefaults.standard.dictionary(forKey: key), let at = saved["at"] as? Double,
           Date().timeIntervalSince1970 - at < 86_400, let names = saved["artists"] as? [String] { return names }
        let base = (local ? deezerLocal : deezerGlobal)[genre] ?? []
        let decade = era?.titlePattern
        let queries = decade == nil ? base : base.prefix(2).flatMap { q in (era?.terms ?? []).map { "\($0) \(q)" } }
        var counts: [String: Int] = [:], lists: [String: Set<Int>] = [:], seenLists = Set<Int>()
        for query in queries {
            guard let url = URL(string: "https://api.deezer.com/search/playlist?limit=40&q=" + (query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")),
                  let found = (try? await getJSON(url))?["data"] as? [[String: Any]] else { continue }
            let editors = found.filter { p in
                let title = p["title"] as? String ?? ""
                let user = (p["user"] as? [String: Any])?["name"] as? String ?? ""
                guard user.localizedCaseInsensitiveContains("deezer"), !matches(title, background) else { return false }
                if let decade { return matches(title, decade) }
                return !matches(title, notCurrent)
            }.prefix(2)
            for p in editors {
                guard let id = p["id"] as? Int, seenLists.insert(id).inserted,
                      let tracksURL = URL(string: "https://api.deezer.com/playlist/\(id)/tracks?limit=80"),
                      let tracks = (try? await getJSON(tracksURL))?["data"] as? [[String: Any]] else { continue }
                for t in tracks {
                    guard let name = ((t["artist"] as? [String: Any])?["name"] as? String)?.trimmingCharacters(in: .whitespaces), !name.isEmpty else { continue }
                    counts[name, default: 0] += 1
                    lists[name, default: []].insert(id)
                }
            }
        }
        let names = Array(counts.keys.sorted {
            let a = lists[$0]?.count ?? 0, b = lists[$1]?.count ?? 0
            return a != b ? a > b : (counts[$0] ?? 0) > (counts[$1] ?? 0)
        }.prefix(60))
        if !names.isEmpty { UserDefaults.standard.set(["at": Date().timeIntervalSince1970, "artists": names], forKey: key) }
        return names
    }

    private struct ArtistCache: Codable { var at: Double; var songs: [MusicCard] }

    static func artistCache(_ country: String, _ name: String) -> [MusicCard]? {
        guard let data = UserDefaults.standard.data(forKey: "\(artistPrefix)\(country)_\(name)"),
              let c = try? JSONDecoder().decode(ArtistCache.self, from: data),
              Date().timeIntervalSince1970 - c.at < 7 * 86_400 else { return nil }
        return c.songs
    }

    static func saveArtist(_ country: String, _ name: String, _ songs: [MusicCard]) {
        if let data = try? JSONEncoder().encode(ArtistCache(at: Date().timeIntervalSince1970, songs: Array(songs.prefix(25)))) {
            UserDefaults.standard.set(data, forKey: "\(artistPrefix)\(country)_\(name)")
        }
    }

    /// The artist's own songs in this country's iTunes catalog (search by artist; songs of other artists are left out).
    static func artistSongs(country: String, name: String, genre: String) async throws -> [MusicCard] {
        guard let url = URL(string: "https://itunes.apple.com/search?entity=song&attribute=artistTerm&limit=25&country=\(country)&term=" +
                            (name.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "")) else { return [] }
        let results = (try await getJSON(url))["results"] as? [[String: Any]] ?? []
        var seen = Set<String>()
        return results.compactMap { o in
            guard let artist = o["artistName"] as? String, artist.localizedCaseInsensitiveContains(name),
                  let page = o["trackViewUrl"] as? String, page.hasPrefix("https://"), let title = o["trackName"] as? String else { return nil }
            let preview = (o["previewUrl"] as? String).flatMap { $0.hasPrefix("https://") ? $0 : nil }
            let art = (o["artworkUrl100"] as? String)?.replacingOccurrences(of: #"/\d+x\d+bb\."#, with: "/600x600bb.", options: .regularExpression)
            let id = (o["trackId"] as? Int).map(String.init)
            let card = MusicCard(music: MusicBlock(title: String(title.prefix(300)), artist: String(artist.prefix(200)),
                                                   album: (o["collectionName"] as? String).map { String($0.prefix(200)) },
                                                   artworkUrl: art, provider: "iTunes", providerTrackId: id, providerUrl: page,
                                                   previewUrl: preview, previewDurationMillis: preview == nil ? nil : 30_000),
                                 genre: genre, year: (o["releaseDate"] as? String).flatMap { Int($0.prefix(4)) })
            return seen.insert(card.id).inserted ? card : nil
        }
    }

    /// Settings "Önerileri sıfırla": the Deezer names and artist songs are fetched fresh next time.
    static func clearCaches() {
        for k in UserDefaults.standard.dictionaryRepresentation().keys where k.hasPrefix(trendPrefix) || k.hasPrefix(artistPrefix) {
            UserDefaults.standard.removeObject(forKey: k)
        }
    }

    private static func getJSON(_ url: URL) async throws -> [String: Any] {
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard ((response as? HTTPURLResponse)?.statusCode ?? 0) / 100 == 2,
              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw URLError(.badServerResponse) }
        return json
    }
}
