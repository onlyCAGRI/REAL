import SwiftUI
import AVFoundation

/// MusicDiscovery.kt: Apple Music top songs per genre and country (no account, no key; covers, 30 s previews).
struct MusicGenre: Hashable, Identifiable {
    let key: String, label: String, itunesId: Int
    var id: String { key }
    static let all: [MusicGenre] = [
        .init(key: "pop", label: "Pop", itunesId: 14), .init(key: "rock", label: "Rock", itunesId: 21),
        .init(key: "hiphop", label: "Hip-Hop / Rap", itunesId: 18), .init(key: "electronic", label: "Elektronik", itunesId: 7),
        .init(key: "dance", label: "Dans / House", itunesId: 17), .init(key: "alternative", label: "Alternatif", itunesId: 20),
        .init(key: "rnb", label: "R&B / Soul", itunesId: 15), .init(key: "jazz", label: "Caz", itunesId: 11),
        .init(key: "classical", label: "Klasik", itunesId: 5), .init(key: "soundtrack", label: "Film Müzikleri", itunesId: 16),
        .init(key: "metal", label: "Metal", itunesId: 1153), .init(key: "latin", label: "Latin", itunesId: 12),
        .init(key: "folk", label: "Folk", itunesId: 10), .init(key: "reggae", label: "Reggae", itunesId: 24),
        .init(key: "blues", label: "Blues", itunesId: 2), .init(key: "country", label: "Country", itunesId: 6),
    ]
    static func of(_ key: String) -> MusicGenre? { all.first { $0.key == key } }
}

/// "Nasıl hissettirsin?": a mood is a mix of genres.
struct MusicMood: Hashable, Identifiable {
    let key: String, label: String, genres: [String]
    var id: String { key }
    static let all: [MusicMood] = [
        .init(key: "energetic", label: "Enerjik", genres: ["dance", "electronic", "hiphop", "pop"]),
        .init(key: "calm", label: "Sakin", genres: ["jazz", "classical", "rnb", "soundtrack"]),
        .init(key: "focus", label: "Odak", genres: ["classical", "soundtrack", "electronic"]),
        .init(key: "sad", label: "Hüzünlü", genres: ["alternative", "rnb", "blues", "folk"]),
        .init(key: "party", label: "Parti", genres: ["dance", "pop", "latin", "hiphop"]),
    ]
}

struct MusicCard: Codable, Hashable, Identifiable {
    var music: MusicBlock
    var genre: String
    /// Release year when known (artist songs, charts); used for "Dönem".
    var year: Int? = nil
    var id: String { "iTunes:\(music.providerTrackId ?? music.providerUrl)" }
}

/// Local, per device. Right swipe saves to "Müzik listem", left passes; passed and saved songs never come back.
@MainActor
@Observable
final class MusicDiscoveryStore {
    static let shared = MusicDiscoveryStore()
    struct Snapshot: Codable {
        var selection: [String] = []
        var cards: [MusicCard] = []
        var list: [MusicCard] = []
        var excluded: [String] = []
    }
    enum Action { case save, skip, keep, remove }

    private(set) var snapshot: Snapshot

    private init() {
        snapshot = (UserDefaults.standard.data(forKey: "real_music_discovery")).flatMap { try? JSONDecoder().decode(Snapshot.self, from: $0) } ?? Snapshot()
    }

    private func set(_ s: Snapshot) {
        var s = s
        s.cards = Array(s.cards.prefix(120)); s.list = Array(s.list.suffix(500)); s.excluded = Array(s.excluded.suffix(3000))
        snapshot = s
        if let d = try? JSONEncoder().encode(s) { UserDefaults.standard.set(d, forKey: "real_music_discovery") }
    }

    func genres(_ selection: Set<String>) -> [MusicGenre] {
        var keys = selection.compactMap { MusicGenre.of($0)?.key }
        for m in MusicMood.all where selection.contains(m.key) { keys += m.genres }
        var seen = Set<String>()
        return keys.filter { seen.insert($0).inserted }.compactMap(MusicGenre.of)
    }

    /// Settings "Önerileri sıfırla": passed songs can come back and the deck starts over; "Müzik listem" stays.
    func resetSeen() {
        var s = snapshot
        s.cards = []; s.excluded = []
        set(s)
        MusicSources.clearCaches()
    }

    func fetch(_ selection: Set<String>) async throws {
        let list = Array((genres(selection).isEmpty ? [MusicGenre.all[0]] : genres(selection)).prefix(6))
        let era = MusicEra.of(selection)
        let country = MusicStorefront.code.lowercased()
        let known = Set(snapshot.excluded + snapshot.list.map(\.id) + snapshot.cards.map(\.id))
        let budget = MusicLookupBudget(12)
        var perGenre: [[MusicCard]] = []
        await withTaskGroup(of: (Int, [MusicCard]).self) { group in
            // The country's chart and the US chart woven in, plus songs of the genre's artists (MusicSources); an
            // older era is built from its artists alone (the weekly charts only fit today's music).
            let withCharts = era == nil || era == .now
            for (i, g) in list.enumerated() {
                group.addTask {
                    async let local = Self.chart(country, g, when: withCharts)
                    async let us = Self.chart("us", g, when: withCharts && country != "us")
                    async let seeds = MusicSources.seedSongs(country: country, genre: g, known: known, budget: budget, era: era)
                    let charts = Self.windows(Self.interleave(await local, await us).filter { !known.contains($0.id) && MusicSources.inEra($0, era) })
                    return (i, MusicSources.weave(await seeds, charts))
                }
            }
            var results = Array(repeating: [MusicCard](), count: list.count)
            for await (i, cards) in group { results[i] = cards }
            perGenre = results
        }
        if perGenre.allSatisfy(\.isEmpty) { throw BookError.message("Müzik listeleri alınamadı. Bağlantını kontrol edip yeniden dene.") }
        var queues: [[MusicCard]] = perGenre.map { $0.filter { !known.contains($0.id) } }
        var fresh: [MusicCard] = []
        var seen = Set<String>()
        while fresh.count < 40, queues.contains(where: { !$0.isEmpty }) {
            for i in queues.indices where !queues[i].isEmpty {
                let next = queues[i].removeFirst()
                if seen.insert(next.id).inserted { fresh.append(next) }
            }
        }
        guard !fresh.isEmpty else {
            throw BookError.message(era != nil ? "Bu dönemde yeni şarkı bulunamadı. Biraz sonra tekrar dene ya da başka bir tür seç."
                                    : "Bu seçimlerde yeni şarkı kalmadı. Başka bir tür dene.")
        }
        var s = snapshot
        let same = Set(s.selection) == selection
        s.selection = Array(selection)
        s.cards = (same ? s.cards : []) + fresh
        set(s)
    }

    func act(_ card: MusicCard, _ action: Action) {
        guard AppUpdateGate.writable() else { return }
        var s = snapshot
        let rest = s.cards.filter { $0.id != card.id }
        switch action {
        case .save: s.cards = rest; s.list = s.list.filter { $0.id != card.id } + [card]; if !s.excluded.contains(card.id) { s.excluded.append(card.id) }
        case .skip: s.cards = rest; if !s.excluded.contains(card.id) { s.excluded.append(card.id) }
        case .keep: s.list = s.list.filter { $0.id != card.id } + [card]
        case .remove: s.list = s.list.filter { $0.id != card.id }
        }
        set(s)
    }

    /// Apple's ranking (most played first) kept, only mixed inside windows of six; previews first in a window.
    nonisolated static func windows(_ list: [MusicCard]) -> [MusicCard] {
        stride(from: 0, to: list.count, by: 6).flatMap { start in
            list[start..<min(start + 6, list.count)].shuffled().sorted { ($0.music.previewUrl != nil ? 0 : 1) < ($1.music.previewUrl != nil ? 0 : 1) }
        }
    }

    nonisolated static func chart(_ country: String, _ genre: MusicGenre, when: Bool) async -> [MusicCard] {
        when ? await chart(country, genre) : []
    }

    nonisolated static func interleave(_ a: [MusicCard], _ b: [MusicCard]) -> [MusicCard] {
        var seen = Set<String>(), out: [MusicCard] = []
        for i in 0..<max(a.count, b.count) {
            if i < a.count, seen.insert(a[i].id).inserted { out.append(a[i]) }
            if i < b.count, seen.insert(b[i].id).inserted { out.append(b[i]) }
        }
        return out
    }

    /// Apple Music top songs of one genre in one country (iTunes RSS).
    nonisolated static func chart(_ country: String, _ genre: MusicGenre) async -> [MusicCard] {
        guard let url = URL(string: "https://itunes.apple.com/\(country)/rss/topsongs/limit=100/genre=\(genre.itunesId)/json"),
              let (data, resp) = try? await URLSession.shared.data(from: url),
              (resp as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) == true,
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any], let feed = json["feed"] as? [String: Any] else { return [] }
        let entries = (feed["entry"] as? [[String: Any]]) ?? ((feed["entry"] as? [String: Any]).map { [$0] } ?? [])
        func label(_ o: Any?, _ k: String) -> String? { ((o as? [String: Any])?[k] as? [String: Any])?["label"] as? String }
        return entries.compactMap { e in
            guard let title = label(e, "im:name") else { return nil }
            let id = ((e["id"] as? [String: Any])?["attributes"] as? [String: Any])?["im:id"] as? String
            let links = (e["link"] as? [[String: Any]]) ?? ((e["link"] as? [String: Any]).map { [$0] } ?? [])
            var page: String?, preview: String?
            for l in links {
                guard let a = l["attributes"] as? [String: Any], let href = a["href"] as? String, href.hasPrefix("https://") else { continue }
                if (a["im:assetType"] as? String) == "preview" || ((a["type"] as? String) ?? "").hasPrefix("audio") { preview = href }
                else if (a["type"] as? String) == "text/html" { page = href }
            }
            guard let page else { return nil }
            let art = ((e["im:image"] as? [[String: Any]])?.last?["label"] as? String)?
                .replacingOccurrences(of: #"/\d+x\d+bb\."#, with: "/600x600bb.", options: .regularExpression)
            let album = label(e["im:collection"], "im:name")
            return MusicCard(music: MusicBlock(title: String(title.prefix(300)), artist: label(e, "im:artist") ?? "", album: album,
                                               artworkUrl: art, provider: "iTunes", providerTrackId: id, providerUrl: page,
                                               previewUrl: preview, previewDurationMillis: preview == nil ? nil : 30_000), genre: genre.key,
                             year: label(e, "im:releaseDate").flatMap { Int($0.prefix(4)) })
        }
    }
}

/// Müzik keşfi: genres / a mood → a deck of song covers. The top song's preview plays (tap pauses), the cover's own
/// colours behind the deck pulse softly. Right = Müzik listem · left = pass · up = open in your music app.
struct MusicDiscoveryScreen: View {
    var startInList = false
    let onClose: () -> Void
    @Environment(ReminderStore.self) private var store
    @State private var music = MusicDiscoveryStore.shared
    @State private var list = false
    @State private var selection: Set<String> = []
    @State private var deck = false
    @State private var pickerStep = 0
    @State private var loading = false
    @State private var error: String?
    @State private var dragOffset: CGSize = .zero

    private var cards: [MusicCard] { list ? music.snapshot.list : music.snapshot.cards }

    var body: some View {
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()
            if deck || list { deckPage } else { filterPage }
        }
        .onAppear {
            // "Nasıl hissettirsin" is gone; old mood keys are dropped.
            selection = Set(music.snapshot.selection.filter { k in !MusicMood.all.contains { $0.key == k } })
            if startInList { list = true }
        }
        .edgeSwipeBack { back() }
    }

    private func back() {
        // Inside the picker: the previous question. From the deck: straight back to Yaşam (the picker was done).
        if !deck && !list && pickerStep > 0 { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.32)) { pickerStep -= 1 }; return }
        if startInList || deck || !list { onClose() } else { withAnimation(.easeOut(duration: 0.25)) { deck = false; list = false } }
    }

    private var filterPage: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                YasamHeroSentence(greeting: "", sentence: "Ne dinlesek", tone: musicTone,
                                  hour: Calendar.real.component(.hour, from: Date()), revealed: true, compact: true)
                    .padding(.top, 4)
                BackCircle(action: back).padding(.leading, 22).padding(.top, 12).accessibilityLabel("Yaşam'a dön")
            }
            if let error { errorLine(error) }
            // One question at a time: 2 genres at most, then (optional) one era; "Hepsi" = every era.
            let allEras = "era_all"
            DiscoveryStepper(steps: [
                DiscoveryStep(title: "Hangi türde müzik?", subtitle: "En fazla 2 tür", max: 2, optional: false,
                              groups: [(nil, MusicGenre.all.map { StepOption(key: $0.key, label: $0.label) })]),
                DiscoveryStep(title: "Hangi dönemden?", subtitle: "İstersen bir dönem · Apple Music \(MusicStorefront.name) listeleri", max: 1, optional: true,
                              groups: [(nil, [StepOption(key: allEras, label: "Hepsi")] + MusicEra.allCases.map { StepOption(key: $0.rawValue, label: $0.label) })]),
            ], selection: Binding(
                get: { MusicEra.of(selection) == nil ? selection.union([allEras]) : selection },
                set: { selection = $0.subtracting([allEras]) }),
                accent: musicTone, loading: loading, enabled: true, doneLabel: "şarkıları getir", step: $pickerStep,
                rare: { chosen, key in DiscoveryRarePairs.rare(DiscoveryRarePairs.music, chosen, key) },
                onDone: fetch)
        }
    }

    private func chip(_ key: String, _ label: String) -> some View {
        let on = selection.contains(key)
        let genreCount = selection.filter { MusicGenre.of($0) != nil }.count
        return DiscoveryChip(label: label, selected: on, enabled: !loading && (on || genreCount < 6), accent: musicTone) {
            if on { selection.remove(key) } else { selection.insert(key) }
        }
    }

    private func fetch() {
        guard !loading else { return }
        loading = true; error = nil
        Task {
            do { try await music.fetch(selection); withAnimation(.easeOut(duration: 0.3)) { deck = true } }
            catch let e { error = e.localizedDescription }
            loading = false
        }
    }

    private func errorLine(_ text: String) -> some View {
        Text(text).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFE6BCB1))
            .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 20).padding(.vertical, 10)
    }

    private var deckPage: some View {
        ZStack(alignment: .top) {
            PosterAmbient(url: cards.first?.music.artworkUrl)
            SwipeEdgeGlow(offset: dragOffset, listMode: list, upColor: musicTone).ignoresSafeArea()
            VStack(spacing: 0) {
                HStack { BackCircle(action: back); Spacer() }.padding(.horizontal, 20).padding(.vertical, 10)
                DiscoveryHeadline(text: list ? "MÜZİK LİSTEM" : "SANA GÖRE", small: list).padding(.top, 2).padding(.bottom, 6)
                if !cards.isEmpty {
                    MusicDeck(cards: cards, list: list, offset: $dragOffset, onDecision: { music.act($0, $1) },
                              onAdd: addToReal)
                        .padding(.horizontal, 14)
                } else {
                    VStack(spacing: 16) {
                        Text(list ? "Müzik listen henüz boş. Sağa kaydırdığın şarkılar buraya gelir." : (loading ? "Şarkılar hazırlanıyor…" : "Bu destenin sonuna geldin."))
                            .font(RealFont.inter(15)).foregroundStyle(.white).multilineTextAlignment(.center)
                        if !list && !loading { DiscoveryChip(label: "Daha fazla getir", selected: false, enabled: true, accent: musicTone) { fetch() } }
                    }
                    .padding(.top, 36).padding(.horizontal, 24)
                    Spacer()
                }
                if let error { errorLine(error) }
            }
            if !cards.isEmpty {
                if list { SwipeTutorial(kind: "music_list", rightLabel: "Kaldır", leftLabel: "Sona at", upLabel: "Uygulamada aç") }
                else { SwipeTutorial(kind: "music", rightLabel: "Kaydet", leftLabel: "Geç", upLabel: "Uygulamada aç") }
            }
        }
        .onChange(of: cards.count, initial: true) { _, count in
            if deck && !list && !loading && error == nil && count <= 3 && !music.snapshot.selection.isEmpty { selection = Set(music.snapshot.selection); fetch() }
        }
    }

    private func addToReal(_ m: MusicBlock) {
        if store.reminders.contains(where: { $0.musicBlocks.contains { $0.providerUrl == m.providerUrl } }) { RealToast.shared.show("Bu şarkı zaten bir kartta"); return }
        _ = store.addReminder(text: [m.title, m.artist].filter { !$0.isEmpty }.joined(separator: " – ")) { r in r.musicBlocks = [m] }
        RealToast.shared.show("REAL'e eklendi")
    }
}

private struct MusicDeck: View {
    let cards: [MusicCard]
    let list: Bool
    @Binding var offset: CGSize
    let onDecision: (MusicCard, MusicDiscoveryStore.Action) -> Void
    let onAdd: (MusicBlock) -> Void

    @State private var busy = false
    @State private var paused = false
    @State private var player: AVPlayer?
    @State private var playingId: String?
    /// Next previews already loading, so a swipe starts the sound at once.
    @State private var prepared: [String: AVPlayer] = [:]
    /// The covers behind wait until the front one is on screen.
    @State private var frontReady = false
    @State private var edgeColors: [Color] = []
    @Environment(\.scenePhase) private var scenePhase
    private let threshold: CGFloat = 85

    var body: some View {
        GeometryReader { geo in
            // Same place as the film / game dock: the button sits at the bottom, the cover centred above it.
            let avail = max(1, geo.size.height - 34 - 76)
            let side = min(geo.size.width * 0.96, avail)
            let top = max(0, (avail - side) / 2)
            let visible = Array(cards.prefix(4))
            ZStack(alignment: .top) {
                // PosterEdgeGlow.kt: six lights from the cover's left / right regions; while the preview plays they grow
                // and brighten with the beat (nothing is drawn over the cover).
                TimelineView(.animation(paused: playingId == nil)) { t in
                    let beat = playingId == nil ? 0 : Self.beat(t.date.timeIntervalSinceReferenceDate)
                    Canvas { ctx, size in
                        let radius = size.width * (0.47 + 0.14 * beat)
                        let boost = 1 + 1.3 * beat
                        for (region, color) in edgeColors.enumerated() {
                            let center = CGPoint(x: size.width * (region % 2 == 0 ? 0.15 : 0.85), y: size.height * (0.16 + CGFloat(region / 2) * 0.34))
                            let g = Gradient(stops: [.init(color: color.opacity(min(0.8, 0.32 * boost)), location: 0),
                                                     .init(color: color.opacity(min(0.4, 0.12 * boost)), location: 0.5),
                                                     .init(color: .clear, location: 1)])
                            ctx.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)),
                                     with: .radialGradient(g, center: center, startRadius: 0, endRadius: radius))
                        }
                    }
                    .frame(width: geo.size.width + 28, height: side)
                }
                .offset(x: -14, y: top)
                .allowsHitTesting(false)
                ForEach(Array(visible.enumerated()).reversed(), id: \.element.id) { depth, item in
                    cover(item, depth: depth, side: side).offset(y: top)
                }
                bar.offset(y: avail + 12)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 34)
        }
        .task(id: "\(cards.first?.id ?? "")|\(paused)|\(scenePhase == .active)|\(busy)") { await play() }
        .task(id: cards.first?.music.artworkUrl) {
            defer {
                frontReady = true
                // The next covers are fetched ahead so a swipe never shows an empty cover.
                let next = cards.dropFirst().prefix(6).compactMap { $0.music.artworkUrl.flatMap(URL.init(string:)) }
                Task { for u in next { _ = try? await URLSession.shared.data(from: u) } }
            }
            guard let s = cards.first?.music.artworkUrl, let u = URL(string: s), let (d, _) = try? await URLSession.shared.data(from: u),
                  let img = UIImage(data: d), let c = CoverPalette.edgeRegions(img) else { edgeColors = []; return }
            withAnimation(.easeOut(duration: 0.38)) { edgeColors = c }
        }
        .onDisappear { stop() }
    }

    private func play() async {
        stop()
        guard let first = cards.first, !paused, !busy, scenePhase == .active,
              let s = first.music.previewUrl, let url = URL(string: s) else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        let p = prepared[first.id] ?? AVPlayer(url: url)
        await p.seek(to: .zero)
        p.play()
        prepareNext()
        player = p
        playingId = first.id
        NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: p.currentItem, queue: .main) { _ in
            p.seek(to: .zero); p.play()
        }
    }

    private func stop() { player?.pause(); player = nil; playingId = nil }

    /// MusicDiscoveryScreen.kt's sound pulse: a quick beat on a slow swell, 0…1.
    private static func beat(_ t: Double) -> Double {
        let b = pow(sin(t * 7) * 0.5 + 0.5, 2)
        return min(1, max(0, 0.25 + 0.45 * b + 0.10 * sin(t * 2.3)))
    }

    /// Keeps players for the next three previews; the rest are dropped.
    private func prepareNext() {
        let next = cards.dropFirst().prefix(3)
        var keep: [String: AVPlayer] = [:]
        for c in next {
            guard let s = c.music.previewUrl, let u = URL(string: s) else { continue }
            let p = prepared[c.id] ?? AVPlayer(url: u)
            p.automaticallyWaitsToMinimizeStalling = false
            keep[c.id] = p
        }
        prepared = keep
    }

    private func cover(_ item: MusicCard, depth: Int, side: CGFloat) -> some View {
        let d = CGFloat(depth)
        let front = depth == 0
        return ZStack(alignment: .bottomLeading) {
            MusicArtwork(url: front || frontReady ? item.music.artworkUrl : nil)
            if front {
                LinearGradient(colors: [.clear, .black.opacity(0.82)], startPoint: .center, endPoint: .bottom)
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.music.title).font(RealFont.inter(22, .bold)).foregroundStyle(.white).lineLimit(2)
                    Text(item.music.artist).font(RealFont.inter(15)).foregroundStyle(.white.opacity(0.75)).lineLimit(1)
                }
                .padding(18)
            }
        }
        .frame(width: side, height: side)
        .overlay(alignment: .topTrailing) { if front { MusicSourceLogo(provider: musicOpensIn(item.music), size: 20).padding(14) } }
        .overlay(alignment: .topLeading) {
            if front && item.music.previewUrl == nil {
                Text("Önizleme yok").font(RealFont.inter(11)).foregroundStyle(.white.opacity(0.7))
                    .padding(.horizontal, 9).padding(.vertical, 4).background(Capsule().fill(.black.opacity(0.7))).padding(14)
            }
        }
        .overlay { if front && paused && item.music.previewUrl != nil { RealSymbol("play.fill", size: 40).foregroundStyle(.white.opacity(0.9)) } }
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .scaleEffect(1 - d * 0.045)
        .offset(y: -d * (10 + 0.045 * side / 2))
        .opacity(1 - Double(d) * 0.1)
        .offset(front ? offset : .zero)
        .rotationEffect(.degrees(front ? Double(max(-24, min(24, offset.width / threshold * 7))) : 0))
        .zIndex(Double(10 - depth))
        .animation(.spring(response: 0.42, dampingFraction: 0.85), value: depth)
        .gesture(drag(item), including: front ? .all : .subviews)
        .onTapGesture { if front && !busy { paused.toggle() } }
    }

    private func drag(_ item: MusicCard) -> some Gesture {
        DragGesture()
            .onChanged { v in if !busy { offset = v.translation } }
            .onEnded { _ in
                guard !busy else { return }
                if -offset.height > threshold && -offset.height > abs(offset.width) {
                    withAnimation(.spring()) { offset = .zero }
                    paused = true
                    openInPreferredMusicApp(item.music)
                } else if abs(offset.height) > abs(offset.width) { withAnimation(.spring()) { offset = .zero } }
                // In Müzik listem: right removes, left sends the song to the back.
                else if offset.width > threshold { decide(item, list ? .remove : .save, fly: CGSize(width: threshold * 6, height: 0)) }
                else if offset.width < -threshold { decide(item, list ? .keep : .skip, fly: CGSize(width: -threshold * 6, height: 0)) }
                else { withAnimation(.spring()) { offset = .zero } }
            }
    }

    private func decide(_ item: MusicCard, _ action: MusicDiscoveryStore.Action, fly: CGSize) {
        guard !busy else { return }
        busy = true
        withAnimation(.easeIn(duration: 0.24)) { offset = fly }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.24) {
            onDecision(item, action)
            var t = Transaction(); t.disablesAnimations = true; withTransaction(t) { offset = .zero }
            paused = false
            busy = false
        }
    }

    private var bar: some View {
        Button { if let first = cards.first { onAdd(first.music) } } label: {
            HStack(spacing: 7) {
                RealSymbol("plus", size: 18.4)
                Text("REAL'e ekle").font(RealFont.inter(13, .medium)).lineLimit(1)
            }
            .foregroundStyle(busy ? Color(argb: 0xFF66666C) : .white)
            .padding(.horizontal, 16).frame(minHeight: 48)
            .background(musicTone.opacity(0.9), in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(busy)
        .padding(.horizontal, 8).padding(.vertical, 8)
        .background(Color.black.opacity(0.55), in: Capsule())
        .liquidGlass(Capsule(), role: .chrome)
    }
}
