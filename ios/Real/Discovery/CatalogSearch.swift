import SwiftUI
import AVFoundation

/// MusicBlock.kt: an iTunes track attached to a card ("music:<id>" detail block). No audio is downloaded.
struct MusicBlock: Codable, Hashable, Identifiable {
    var id: String = UUID().uuidString
    var title: String
    var artist: String
    var album: String?
    var artworkUrl: String?
    var provider: String
    var providerTrackId: String?
    var providerUrl: String
    var previewUrl: String?
    var previewDurationMillis: Int64?
    var createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
}

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

/// MusicCatalog.kt: public iTunes catalog (Türkiye storefront).
enum MusicCatalog {
    static func search(_ query: String) async throws -> [MusicBlock] {
        let url = URL(string: "https://itunes.apple.com/search?term=\(encoded(query))&entity=song&media=music&country=TR&limit=20")!
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

    var body: some View {
        CatalogPanel(title: "Müzik", placeholder: "Şarkı veya sanatçı ara", query: $query, searching: searching, message: message,
                     attribution: "iTunes • Türkiye kataloğu", onClose: { dismiss() }) {
            ForEach(results) { m in
                Button { onSelect(m); dismiss() } label: {
                    HStack(spacing: 12) {
                        MusicArtwork(url: m.artworkUrl).frame(width: 44, height: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(m.title).font(RealFont.inter(15)).foregroundStyle(.white).lineLimit(1)
                            Text(m.artist).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA5A5B0)).lineLimit(1)
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
        try? await Task.sleep(for: .milliseconds(350))
        guard !Task.isCancelled else { return }
        searching = true
        message = nil
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

/// The shared search panel: title, the field (results follow the typing), "Aranıyor…" and Kapat, the rows, the source.
private struct CatalogPanel<Rows: View>: View {
    let title: String
    let placeholder: String
    @Binding var query: String
    let searching: Bool
    let message: String?
    let attribution: String
    let onClose: () -> Void
    @ViewBuilder let rows: () -> Rows
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(RealFont.inter(19, .semibold)).foregroundStyle(.white)
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

/// MusicTrackCard on a card's detail page: artwork-tinted surface, preview play/pause, "iTunes'da aç".
struct MusicBlockView: View {
    let block: MusicBlock
    var editing = false
    var onRemove: () -> Void = {}
    @State private var ambient: Color = Color(argb: 0xFF3A3530)
    private var preview: MusicPreviewPlayer { .shared }

    var body: some View {
        let playing = preview.playingId == block.id
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Müzik").font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFA5A5B0))
                Spacer()
                if editing {
                    Button(action: onRemove) { RealSymbol("xmark", size: 14.9).foregroundStyle(Color(argb: 0xFFB8B8C2)).frame(width: 36, height: 36) }
                        .accessibilityLabel("İçeriği kaldır")
                }
            }
            HStack(spacing: 12) {
                MusicArtwork(url: block.artworkUrl).frame(width: 64, height: 64)
                VStack(alignment: .leading, spacing: 2) {
                    Text(block.title).font(RealFont.inter(18, .semibold)).foregroundStyle(Color(argb: 0xFFF1F1F4)).lineLimit(2)
                    Text(block.artist).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFB1B1BC)).lineLimit(1)
                    Text(block.provider).font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFF868690))
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
            if let url = URL(string: block.providerUrl), url.scheme == "https" {
                Link(destination: url) {
                    Text("\(block.provider == "iTunes" ? "iTunes" : block.provider)'da aç ↗").font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFD0D0D8))
                        .frame(maxWidth: .infinity).frame(height: 40)
                        .background(Color.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 24))
                }
            }
            if block.provider == "iTunes" {
                Text("Preview provided courtesy of iTunes").font(RealFont.inter(10)).foregroundStyle(Color(argb: 0xFF868690))
            }
        }
        .padding(16)
        .background(LinearGradient(colors: [ambient.opacity(0.55), Color(argb: 0xFF17171B)], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .task(id: block.artworkUrl) {
            guard let s = block.artworkUrl, let u = URL(string: s),
                  let result = try? await URLSession.shared.data(from: u), let img = UIImage(data: result.0),
                  let c = CoverPalette.dominant(img) else { return }
            withAnimation(.easeOut(duration: 0.3)) { ambient = c }
        }
    }
}
