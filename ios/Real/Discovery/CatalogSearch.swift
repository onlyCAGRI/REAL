import SwiftUI
import AVFoundation

private func secureUrl(_ value: String?) -> String? {
    guard let v = value, !v.isEmpty else { return nil }
    if v.hasPrefix("https://") { return v }
    if v.hasPrefix("http://") { return "https://" + v.dropFirst(7) }
    return nil
}

private func getJSON(_ url: URL) async throws -> Any {
    var request = URLRequest(url: url)
    request.timeoutInterval = 10
    request.setValue("application/json", forHTTPHeaderField: "Accept")
    let (data, response) = try await URLSession.shared.data(for: request)
    guard (response as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) == true else { throw URLError(.badServerResponse) }
    return try JSONSerialization.jsonObject(with: data)
}

private func encoded(_ q: String) -> String {
    String(q.trimmingCharacters(in: .whitespaces).prefix(200)).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
}

/// MusicCatalog.kt: the Apple Music catalog of the person's country (MusicStorefront) through the REAL Worker
/// (/api/music-search: explicit originals too); the public iTunes catalog if the Worker fails or is not set up.
/// The iTunes Search API only returns the "cleaned" version of explicit songs.
enum MusicCatalog {
    static func search(_ query: String) async throws -> [MusicBlock] {
        if let songs = try? await appleMusic(query), !songs.isEmpty { return songs }
        return try await itunes(query)
    }

    private static func appleMusic(_ query: String) async throws -> [MusicBlock] {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return [] }
        let json = try await RealBackend.post("/api/music-search",
                                              ["query": String(trimmed.prefix(200)), "storefront": MusicStorefront.code], timeout: 12)
        return ((json["results"] as? [[String: Any]]) ?? []).compactMap { item in
            guard let title = item["title"] as? String, !title.isEmpty, let link = secureUrl(item["providerUrl"] as? String) else { return nil }
            return MusicBlock(title: title, artist: (item["artist"] as? String) ?? "", album: item["album"] as? String,
                              artworkUrl: secureUrl(item["artworkUrl"] as? String), provider: "iTunes",
                              providerTrackId: item["providerTrackId"] as? String, providerUrl: link,
                              previewUrl: secureUrl(item["previewUrl"] as? String),
                              explicit: (item["explicit"] as? Bool) == true ? true : nil)
        }
    }

    private static func itunes(_ query: String) async throws -> [MusicBlock] {
        let url = URL(string: "https://itunes.apple.com/search?term=\(encoded(query))&entity=song&media=music&country=\(MusicStorefront.code)&limit=20")!
        let json = try await getJSON(url) as? [String: Any]
        return ((json?["results"] as? [[String: Any]]) ?? []).compactMap { item in
            guard let title = item["trackName"] as? String, !title.isEmpty, let link = secureUrl(item["trackViewUrl"] as? String) else { return nil }
            return MusicBlock(title: title, artist: (item["artistName"] as? String) ?? "", album: item["collectionName"] as? String,
                              artworkUrl: secureUrl(item["artworkUrl100"] as? String), provider: "iTunes",
                              providerTrackId: (item["trackId"] as? Int).map(String.init), providerUrl: link,
                              previewUrl: secureUrl(item["previewUrl"] as? String))
        }
    }
}

/// MediaCatalog.search: TV from TVmaze (public). Movies from TheTVDB through the REAL Worker (/api/media-search,
/// RealMediaCatalogClient on Android) when signed in; the public iTunes movie catalog otherwise or if the Worker fails.
enum MediaCatalog {
    static func search(_ query: String, type: MediaType) async throws -> [MediaBlock] {
        if type == .TV { return try await tvmaze(query) }
        if await MainActor.run(body: { RealAuth.shared.account != nil }),
           let movies = try? await theTVDBMovies(query), !movies.isEmpty {
            return movies
        }
        return try await itunesMovies(query)
    }

    @MainActor static func attribution(_ type: MediaType) -> String {
        type == .TV ? "TVmaze" : (RealAuth.shared.account != nil ? "TheTVDB" : "iTunes • Türkiye kataloğu")
    }

    /// RealMediaCatalogClient.searchMovies: TheTVDB V4 via the Worker (no TheTVDB key on the device).
    static func theTVDBMovies(_ query: String, title: String? = nil, year: Int? = nil) async throws -> [MediaBlock] {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return [] }
        var body: [String: Any] = ["type": "movie", "query": String(trimmed.prefix(200))]
        if let title, !title.isEmpty { body["title"] = title }
        if let year, year > 0 { body["year"] = year }
        let json = try await RealBackend.post("/api/media-search", body, timeout: 15)
        var seen = Set<String>()
        return ((json["results"] as? [[String: Any]]) ?? []).compactMap { item in
            guard let name = (item["title"] as? String)?.trimmingCharacters(in: .whitespaces), !name.isEmpty else { return nil }
            let providerId = (item["providerId"] ?? item["id"]).map { "\($0)" }
            let link = secureUrl(item["providerUrl"] as? String) ?? "https://thetvdb.com"
            guard seen.insert(providerId ?? link + name.lowercased()).inserted else { return nil }
            let year = (item["year"] as? Int).flatMap { $0 > 0 ? $0 : nil }
            return MediaBlock(type: .MOVIE, providerId: providerId, title: name, subtitle: year.map { "\($0) • Film" } ?? "Film",
                              year: year, genre: (item["genre"] as? String).flatMap { $0.isEmpty ? nil : $0 },
                              description: (item["description"] as? String).flatMap { $0.isEmpty ? nil : $0 },
                              artworkUrl: secureUrl(item["artworkUrl"] as? String),
                              provider: (item["provider"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "TheTVDB", providerUrl: link)
        }
    }

    /// MediaCatalog.resolveMovie / resolveTv: the catalog entry for an AI-suggested title (exact title, year ±1 when known).
    static func resolve(title: String, year: Int?, type: MediaType) async -> MediaBlock? {
        let results: [MediaBlock]
        if type == .TV {
            results = (try? await tvmaze(title)) ?? []
        } else {
            results = (try? await theTVDBMovies(title, title: title, year: year)) ?? []
        }
        let target = DiscoveryRanking.normalized(title)
        guard !target.isEmpty else { return nil }
        let exact = results.filter { DiscoveryRanking.normalized($0.title) == target }
        if let year, year > 0 {
            if let hit = exact.first(where: { $0.year.map { abs($0 - year) <= 1 } ?? false }) { return hit }
            if exact.count == 1, exact[0].year == nil { return exact[0] }
            let close = results.filter { r in
                let n = DiscoveryRanking.normalized(r.title)
                return !n.isEmpty && (n.contains(target) || target.contains(n)) && (r.year.map { abs($0 - year) <= 1 } ?? false)
            }
            return close.count == 1 ? close[0] : nil
        }
        return exact.first
    }

    private static func tvmaze(_ query: String) async throws -> [MediaBlock] {
        let json = try await getJSON(URL(string: "https://api.tvmaze.com/search/shows?q=\(encoded(query))")!) as? [[String: Any]] ?? []
        return json.compactMap { item in
            guard let show = item["show"] as? [String: Any], let id = show["id"] as? Int,
                  let name = (show["name"] as? String)?.trimmingCharacters(in: .whitespaces), !name.isEmpty else { return nil }
            let year = (show["premiered"] as? String).flatMap { Int($0.prefix(4)) }
            let genres = (show["genres"] as? [String])?.joined(separator: ", ")
            let runtime = (show["averageRuntime"] as? Int) ?? (show["runtime"] as? Int)
            let image = show["image"] as? [String: Any]
            let summary = (show["summary"] as? String).map { BookDocuments.decodeEntities($0.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)) }
            return MediaBlock(type: .TV, providerId: String(id), title: name, year: year, genre: genres?.isEmpty == false ? genres : nil,
                              description: summary, artworkUrl: secureUrl((image?["original"] as? String) ?? (image?["medium"] as? String)),
                              provider: "TVmaze", providerUrl: secureUrl(show["url"] as? String) ?? "https://www.tvmaze.com/shows/\(id)",
                              durationMillis: runtime.map { Int64($0) * 60_000 })
        }
    }

    private static func itunesMovies(_ query: String) async throws -> [MediaBlock] {
        let url = URL(string: "https://itunes.apple.com/search?term=\(encoded(query))&media=movie&entity=movie&country=TR&limit=20")!
        let json = try await getJSON(url) as? [String: Any]
        return ((json?["results"] as? [[String: Any]]) ?? []).compactMap { item in
            guard let title = item["trackName"] as? String, !title.isEmpty, let link = secureUrl(item["trackViewUrl"] as? String) else { return nil }
            let art = secureUrl(item["artworkUrl100"] as? String)?.replacingOccurrences(of: "100x100bb", with: "600x900bb")
            return MediaBlock(type: .MOVIE, providerId: (item["trackId"] as? Int).map(String.init), title: title,
                              year: (item["releaseDate"] as? String).flatMap { Int($0.prefix(4)) }, genre: item["primaryGenreName"] as? String,
                              description: (item["longDescription"] as? String) ?? (item["shortDescription"] as? String),
                              artworkUrl: art, provider: "Apple", providerUrl: link,
                              durationMillis: (item["trackTimeMillis"] as? Int).map(Int64.init))
        }
    }
}

// MARK: Search sheets

/// MediaSearchSheet (Android MediaContent.kt): one "Film & Dizi" search that runs while typing. Films (TheTVDB via the
/// Worker, iTunes when signed out) and series (TVmaze) are searched together; titles that start with the query first,
/// then the rest alternating film / series.
struct MediaSearchSheet: View {
    let onSelect: (MediaBlock) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: [MediaBlock] = []
    @State private var searching = false
    @State private var message: String?

    var body: some View {
        CatalogPanel(title: "Film & Dizi", placeholder: "Film veya dizi ara", query: $query, searching: searching, message: message,
                     attribution: "Filmler \(MediaCatalog.attribution(.MOVIE)) • Diziler TVmaze", onClose: { dismiss() }) {
            ForEach(results) { m in
                Button { onSelect(m); dismiss() } label: {
                    HStack(spacing: 12) {
                        PosterArtwork(media: m, titleSize: 6).frame(width: 38, height: 54).clipShape(RoundedRectangle(cornerRadius: 8))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(m.title).font(RealFont.inter(15)).foregroundStyle(.white).lineLimit(1)
                            Text([m.year.map(String.init), m.type == .MOVIE ? "Film" : "Dizi", m.genre].compactMap { $0 }.joined(separator: " • "))
                                .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA5A5B0)).lineLimit(1)
                        }
                        Spacer()
                        RealSymbol("plus", size: 17.2).foregroundStyle(.white)
                    }
                    .padding(8).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .task(id: query) { await search(query) }
    }

    private func search(_ raw: String) async {
        let q = raw.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { results = []; message = nil; searching = false; return }
        try? await Task.sleep(for: .milliseconds(350))      // a short pause after the last keystroke
        guard !Task.isCancelled else { return }
        searching = true
        message = nil
        async let movies = try? MediaCatalog.search(q, type: .MOVIE)
        async let series = try? MediaCatalog.search(q, type: .TV)
        let (m, t) = await (movies, series)
        guard !Task.isCancelled else { return }
        searching = false
        if m == nil && t == nil { results = []; message = "İçerik kataloğuna şu anda ulaşılamadı."; return }
        results = Self.merge(q, m ?? [], t ?? [])
        if results.isEmpty { message = "Sonuç bulunamadı." }
    }

    private static func merge(_ query: String, _ movies: [MediaBlock], _ series: [MediaBlock]) -> [MediaBlock] {
        var alternating: [MediaBlock] = []
        for i in 0..<max(movies.count, series.count) {
            if i < movies.count { alternating.append(movies[i]) }
            if i < series.count { alternating.append(series[i]) }
        }
        let q = query.lowercased()
        let close = alternating.filter { $0.title.lowercased().hasPrefix(q) }
        let rest = alternating.filter { !$0.title.lowercased().hasPrefix(q) }
        var seen = Set<String>()
        return (close + rest).filter { seen.insert("\($0.type.rawValue):\($0.providerId ?? $0.title)").inserted }.prefix(30).map { $0 }
    }
}

/// MusicSearchSheet: "Müzik" over the iTunes Türkiye catalog, searching while typing.
struct MusicSearchSheet: View {
    let onSelect: (MusicBlock) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: [MusicBlock] = []
    @State private var searching = false
    @State private var message: String?
    @State private var source = MusicSource.saved

    var body: some View {
        CatalogPanel(title: "Müzik", placeholder: source == .apple ? "Şarkı veya sanatçı ara" : "Şarkı ara veya Spotify bağlantısı yapıştır",
                     query: $query, searching: searching, message: message,
                     attribution: "Apple Music • \(MusicStorefront.name) kataloğu",
                     onClose: { dismiss() }, header: AnyView(sourceHeader)) {
            ForEach(results) { m in
                Button { onSelect(m); dismiss() } label: {
                    HStack(spacing: 12) {
                        MusicArtwork(url: m.artworkUrl).frame(width: 44, height: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(m.title).font(RealFont.inter(15)).foregroundStyle(.white).lineLimit(1)
                            MusicArtistLine(block: m, color: Color(argb: 0xFFA5A5B0), size: 12)
                        }
                        Spacer()
                        RealSymbol("plus", size: 17.2).foregroundStyle(.white)
                    }
                    .padding(8).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .task(id: "\(source.rawValue)|\(query)") { await search(query) }
    }

    /// Logos only (remembered): where the song opens. Songs always come from the Apple Music catalog; with Spotify
    /// chosen the card opens the song in Spotify (searched by title and artist). A pasted Spotify link works too.
    private var sourceHeader: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 0) {
                ForEach(MusicSource.allCases, id: \.self) { s in
                    Button { source = s; MusicSource.saved = s; results = []; message = nil } label: {
                        MusicSourceLogo(provider: s == .spotify ? "Spotify" : "iTunes", size: 24).opacity(source == s ? 1 : 0.45)
                            .frame(maxWidth: .infinity).frame(height: 40)
                            .background(Capsule().fill(source == s ? Color.white.opacity(0.16) : .clear))
                    }
                    .buttonStyle(.plain).accessibilityLabel(s.label)
                }
            }
            .padding(4).background(Capsule().fill(Color.white.opacity(0.06)))
        }
    }

    private func search(_ raw: String) async {
        let q = raw.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { results = []; message = nil; searching = false; return }
        try? await Task.sleep(for: .milliseconds(350))
        guard !Task.isCancelled else { return }
        searching = true
        message = nil
        if let r = q.range(of: #"https://(open\.spotify\.com/(intl-[a-z-]+/)?track/[A-Za-z0-9]{22}|spotify\.link/[A-Za-z0-9]+)"#, options: .regularExpression) {
            let found = await LinkResolver.resolve(String(q[r])).music
            guard !Task.isCancelled else { return }
            searching = false
            if let found, found.provider == "Spotify" { results = [found] } else { results = []; message = "Bu bağlantıdan şarkı okunamadı." }
            return
        }
        do {
            let found = try await MusicCatalog.search(q)
            guard !Task.isCancelled else { return }
            results = found
            message = found.isEmpty ? "Sonuç bulunamadı. Başka bir şarkı veya sanatçı dene." : nil
        } catch {
            guard !Task.isCancelled else { return }
            message = "Müzik araması yapılamadı. Bağlantını kontrol edip yeniden dene."
        }
        searching = false
    }
}

/// GameSearchSheet (GameContent.kt): "Oyun" — IGDB through the REAL server, searching while typing.
struct GameSearchSheet: View {
    let onSelect: (CardGameBlock) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: [CardGameBlock] = []
    @State private var searching = false
    @State private var message: String?

    var body: some View {
        CatalogPanel(title: "Oyun", placeholder: "Oyun ara", query: $query, searching: searching, message: message,
                     attribution: "Kaynak: IGDB", onClose: { dismiss() }) {
            ForEach(results, id: \.providerId) { g in
                Button { var picked = g; picked.id = UUID().uuidString.lowercased(); onSelect(picked); dismiss() } label: {
                    HStack(spacing: 12) {
                        GameCover(block: g).frame(width: 40, height: 53)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(g.title).font(RealFont.inter(15)).foregroundStyle(.white).lineLimit(1)
                            Text([g.year.map(String.init), g.platforms.prefix(3).joined(separator: " · ").nilIfEmpty].compactMap { $0 }.joined(separator: " · "))
                                .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA5A5B0)).lineLimit(1)
                        }
                        Spacer()
                        RealSymbol("plus", size: 17.2).foregroundStyle(.white)
                    }
                    .padding(8).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .task(id: query) { await search(query) }
    }

    private func search(_ raw: String) async {
        let q = raw.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { results = []; message = nil; searching = false; return }
        try? await Task.sleep(for: .milliseconds(400))
        guard !Task.isCancelled else { return }
        searching = true
        message = nil
        do {
            let found = try await GameSearch.search(q)
            guard !Task.isCancelled else { return }
            results = found
            message = found.isEmpty ? "Sonuç bulunamadı. Oyunun adını farklı yazmayı dene." : nil
        } catch {
            guard !Task.isCancelled else { return }
            message = (error as? LocalizedError)?.errorDescription ?? "Oyun araması yapılamadı. Bağlantını kontrol edip yeniden dene."
        }
        searching = false
    }
}

/// The shared search panel: title, the field (results follow the typing), "Aranıyor…" and Kapat, the rows, the source.
private struct CatalogPanel<Rows: View>: View {
    let title: String
    let placeholder: String
    @Binding var query: String
    let searching: Bool
    let message: String?
    let attribution: String
    let onClose: () -> Void
    var header: AnyView? = nil
    @ViewBuilder let rows: () -> Rows
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(RealFont.inter(19, .semibold)).foregroundStyle(.white)
            if let header { header }
            TextField("", text: $query, prompt: Text(placeholder).foregroundStyle(Color(argb: 0xFF8E8E98)))
                .font(RealFont.inter(16)).foregroundStyle(.white).tint(.white)
                .focused($focused).submitLabel(.search)
                .onSubmit { focused = false }
                .padding(.horizontal, 18).frame(height: 52)
                .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(focused ? Color(argb: 0xFF85858D) : Color(argb: 0xFF424248), lineWidth: 1))
            HStack {
                Text(searching ? "Aranıyor…" : "").font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFA5A5B0))
                Spacer()
                Button("Kapat", action: onClose).foregroundStyle(Color(argb: 0xFFBCBCC5)).font(RealFont.inter(15, .medium))
            }
            .padding(.vertical, 4)
            if let message { Text(message).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFBCBCC5)) }
            ScrollView {
                LazyVStack(spacing: 6) { rows() }
            }
            .frame(maxHeight: 340)
            .fixedSize(horizontal: false, vertical: true)
            Text(attribution).font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFF95959F))
        }
        .padding(12)
        .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous), role: .sheet)
        .padding(.horizontal, 16).padding(.top, 20)
        .frame(maxHeight: .infinity, alignment: .top)
        .presentationDetents([.large])
        .presentationBackground(Color(argb: 0xFF101012))
        .presentationDragIndicator(.visible)
        .onAppear { focused = true }
    }
}

struct MusicArtwork: View {
    let url: String?
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10).fill(Color(argb: 0xFF26262C))
            RealSymbol("music.note", size: 18.4).foregroundStyle(Color(argb: 0xFFB8B8C2))
            if let s = url, let u = URL(string: s.replacingOccurrences(of: "100x100bb", with: "300x300bb")) {
                AsyncImage(url: u) { p in if let i = p.image { i.resizable().scaledToFill() } }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: Music block

/// One shared preview player: starting a preview stops any other.
@Observable
final class MusicPreviewPlayer {
    static let shared = MusicPreviewPlayer()
    private(set) var playingId: String?
    private(set) var loading = false
    private(set) var elapsed: Double = 0
    @ObservationIgnored private var player: AVPlayer?
    @ObservationIgnored private var observer: Any?
    @ObservationIgnored private var endObserver: NSObjectProtocol?

    func toggle(_ block: MusicBlock) {
        if playingId == block.id { stop(); return }
        stop()
        guard let s = block.previewUrl, let url = URL(string: s) else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
        let item = AVPlayerItem(url: url)
        let p = AVPlayer(playerItem: item)
        player = p
        playingId = block.id
        loading = true
        elapsed = 0
        observer = p.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.25, preferredTimescale: 600), queue: .main) { [weak self] t in
            guard let self else { return }
            self.elapsed = t.seconds
            if p.timeControlStatus == .playing { self.loading = false }
        }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in self?.stop() }
        p.play()
    }

    func stop() {
        if let observer, let player { player.removeTimeObserver(observer) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        observer = nil; endObserver = nil
        player?.pause(); player = nil
        playingId = nil; loading = false; elapsed = 0
    }
}

/// Apple Music's "E" for explicit songs (MusicContent.kt ExplicitBadge).
struct ExplicitBadge: View {
    var color: Color
    var size: CGFloat = 12
    var body: some View {
        Text("E").font(RealFont.inter(size * 0.72, .bold)).foregroundStyle(Color.black.opacity(0.85))
            .frame(width: size + 2, height: size + 2)
            .background(color, in: RoundedRectangle(cornerRadius: 3, style: .continuous))
            .accessibilityLabel("Müstehcen")
    }
}

/// The artist, with the "E" badge in front of it when the song is explicit.
struct MusicArtistLine: View {
    let block: MusicBlock
    var color: Color
    var size: CGFloat
    var body: some View {
        HStack(spacing: 5) {
            if block.explicit == true { ExplicitBadge(color: color, size: size) }
            Text(block.artist).font(RealFont.inter(size)).foregroundStyle(color).lineLimit(1)
        }
    }
}

/// MusicTrackCard on a card's detail page: artwork-tinted surface, preview play/pause, "iTunes'da aç".
struct MusicBlockView: View {
    let block: MusicBlock
    var editing = false
    var onRemove: () -> Void = {}
    /// > 0: drawn as the card's cover (CatalogHeroCover) under the status bar.
    var heroInset: CGFloat = 0
    @State private var ambient: Color = Color(argb: 0xFF3A3530)
    @State private var flipped = false
    private var preview: MusicPreviewPlayer { .shared }
    private var spotifyCodeId: String? {
        guard block.provider == "Spotify", let id = block.providerTrackId, id.count == 22 else { return nil }
        return id
    }

    var body: some View {
        let playing = preview.playingId == block.id
        ZStack {
            Group {
                if heroInset > 0 && !editing { heroFront(playing: playing) } else { front(playing: playing) }
            }
            .opacity(flipped ? 0 : 1)
            if flipped, let id = spotifyCodeId { back(id) }
        }
        .rotation3DEffect(.degrees(flipped ? 180 : 0), axis: (x: 0, y: 1, z: 0), perspective: 0.6)
        .animation(.easeInOut(duration: 0.45), value: flipped)
        .background(
            TimelineView(.animation(paused: !playing)) { t in
                // While the preview plays, the cover colour washes through the whole block, softly pulsing.
                let wave = playing ? (sin(t.date.timeIntervalSinceReferenceDate * 3.2) * 0.5 + 0.5) : 0
                LinearGradient(colors: [ambient.opacity(0.55 + 0.25 * wave), Color(argb: 0xFF17171B)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    .overlay(RadialGradient(colors: [ambient.opacity(playing ? 0.18 + 0.22 * wave : 0), .clear],
                                            center: .bottomTrailing, startRadius: 0, endRadius: 320))
            }
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        )
        .task(id: block.artworkUrl) {
            guard let s = block.artworkUrl, let u = URL(string: s),
                  let result = try? await URLSession.shared.data(from: u), let img = UIImage(data: result.0),
                  let c = CoverPalette.dominant(img) else { return }
            withAnimation(.easeOut(duration: 0.3)) { ambient = c }
        }
    }

    private func front(playing: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            if editing {
                HStack {
                    Spacer()
                    Button(action: onRemove) { RealSymbol("xmark", size: 14.9).foregroundStyle(Color(argb: 0xFFB8B8C2)).frame(width: 36, height: 36) }
                        .accessibilityLabel("İçeriği kaldır")
                }
            }
            HStack(alignment: .top, spacing: 12) {
                MusicArtwork(url: block.artworkUrl).frame(width: 64, height: 64)
                VStack(alignment: .leading, spacing: 2) {
                    Text(block.title).font(RealFont.inter(18, .semibold)).foregroundStyle(Color(argb: 0xFFF1F1F4)).lineLimit(2)
                    MusicArtistLine(block: block, color: Color(argb: 0xFFB1B1BC), size: 13)
                }
                Spacer(minLength: 4)
                // Source logo top-right; on Spotify it turns the card over to its Spotify Code.
                if spotifyCodeId != nil {
                    Button { flipped = true } label: { MusicSourceLogo(provider: block.provider, size: 18).frame(width: 30, height: 30) }
                        .buttonStyle(.plain).accessibilityLabel("Spotify kodunu göster")
                } else {
                    MusicSourceLogo(provider: musicOpensIn(block), size: 18)
                }
            }
            if block.previewUrl != nil {
                HStack {
                    Button { preview.toggle(block) } label: {
                        Group {
                            if playing && preview.loading { ProgressView().tint(.white).scaleEffect(0.8) }
                            else { RealSymbol(playing ? "pause.fill" : "play.fill", size: 14.9) }
                        }
                        .foregroundStyle(.white).frame(width: 36, height: 36)
                    }
                    .accessibilityLabel(playing ? "Önizlemeyi durdur" : "Önizlemeyi çal")
                    Spacer()
                    Text(playing && !preview.loading ? String(format: "0:%02d", Int(preview.elapsed)) : "Önizleme")
                        .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFB1B1BC)).monospacedDigit()
                }
            }
            Button { if playing { preview.toggle(block) }; openMusicBlock(block) } label: {
                Text("\(musicProviderLabel(musicOpensIn(block)))'\(musicOpensIn(block) == "Spotify" ? "da" : "te") aç ↗").font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFD0D0D8))
                    .frame(maxWidth: .infinity).frame(height: 40)
                    .background(Color.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 24))
            }
            .buttonStyle(.plain)
            if block.provider == "iTunes", block.previewUrl != nil {
                Text("Preview provided courtesy of iTunes").font(RealFont.inter(10)).foregroundStyle(Color(argb: 0xFF868690))
            }
        }
        .padding(16)
    }

    /// The card's cover: large artwork, title, preview, "…'da aç" with the source logo at the bottom right (on Spotify
    /// the logo turns the cover over to its Spotify Code).
    private func heroFront(playing: Bool) -> some View {
        let opensIn = musicOpensIn(block)
        return CatalogHeroCover(imageURL: block.artworkUrl.flatMap { URL(string: $0.replacingOccurrences(of: "100x100bb", with: "600x600bb")) },
                                posterAspect: 1, title: block.title,
                                subtitle: [block.artist, block.album ?? ""].filter { !$0.isEmpty }.joined(separator: " · "),
                                placeholderSymbol: "music.note", inset: heroInset) {
            if block.previewUrl != nil {
                HStack(spacing: 10) {
                    Button { preview.toggle(block) } label: {
                        Group {
                            if playing && preview.loading { ProgressView().tint(.white).scaleEffect(0.8) }
                            else { RealSymbol(playing ? "pause.fill" : "play.fill", size: 16) }
                        }
                        .foregroundStyle(.white).frame(width: 48, height: 48)
                        .background(Circle().fill(Color.white.opacity(0.12)))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(playing ? "Önizlemeyi durdur" : "Önizlemeyi çal")
                    Text(playing && !preview.loading ? String(format: "0:%02d", Int(preview.elapsed)) : "Önizleme")
                        .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFB1B1BC)).monospacedDigit()
                }
            }
            HeroOpenRow(label: "\(musicProviderLabel(opensIn))'\(opensIn == "Spotify" ? "da" : "te") aç ↗",
                        action: { if playing { preview.toggle(block) }; openMusicBlock(block) }) {
                if spotifyCodeId != nil {
                    Button { flipped = true } label: { MusicSourceLogo(provider: opensIn, size: 22).frame(width: 38, height: 38) }
                        .buttonStyle(.plain).accessibilityLabel("Spotify kodunu göster")
                } else {
                    MusicSourceLogo(provider: opensIn, size: 22).padding(8)
                }
            }
            if block.provider == "iTunes", block.previewUrl != nil {
                Text("Preview provided courtesy of iTunes").font(RealFont.inter(10)).foregroundStyle(Color(argb: 0xFF868690))
            }
        }
    }

    private func back(_ id: String) -> some View {
        AsyncImage(url: URL(string: "https://scannables.scdn.co/uri/plain/png/000000/white/640/spotify:track:\(id)")) { phase in
            if let image = phase.image { image.resizable().scaledToFit() } else { ProgressView().tint(.white) }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0))
        .contentShape(Rectangle())
        .onTapGesture { flipped = false }
        .accessibilityLabel("Spotify kodu. Geri dönmek için dokun.")
    }
}
