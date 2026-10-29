import SwiftUI
import UIKit

private enum DiscoveryPage { case filters, discover, list }

private let discoveryBlue = Color(argb: 0xFF2F6BFF)
private let discoveryOrange = Color(argb: 0xFFF26A1B)
private let posterAspect: CGFloat = 0.667

/// MediaDiscoveryScreen / GameDiscoveryScreen as opened from Yaşam: filter page with the Yaşam hero header,
/// "SANA GÖRE" poster deck, watch/play list and the catalog detail.
struct DiscoveryScreen: View {
    let kind: DiscoveryKind
    var startInList = false
    var startInDeck = false
    let onClose: () -> Void

    @Environment(DiscoveryStore.self) private var discovery
    @State private var rateWatched: MediaBlock?
    @Environment(ReminderStore.self) private var store
    @State private var page: DiscoveryPage = .filters
    @State private var type = "BOTH"
    @State private var filters: Set<String> = []
    @State private var platforms: Set<String> = []
    @State private var loading = false
    @State private var error: String?
    @State private var detail: DiscoveryCard?
    @State private var entered = false
    @State private var reportNotice = false
    @State private var dragOffset: CGSize = .zero
    @State private var pickerStep = 0

    private var accent: Color { kind == .film ? discoveryBlue : discoveryOrange }
    private var catalog: [DiscoveryFilter] { kind == .film ? DiscoveryFilter.film : DiscoveryFilter.game }
    private var cards: [DiscoveryCard] {
        let s = discovery.snapshot(kind)
        return page == .list ? s.later : s.cards
    }

    var body: some View {
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()
            switch page {
            case .filters: filterPage
            case .discover, .list: deckPage
            }
            // Shaking the phone brings the last swiped poster back on top.
            Color.clear.frame(width: 0, height: 0).onShake { if page == .discover && discovery.undo(kind) { error = nil } }
            if let detail {
                MediaDiscoveryDetail(card: detail, kind: kind) { withAnimation(.easeOut(duration: 0.12)) { self.detail = nil } }
                    .transition(.opacity.combined(with: .offset(y: 120)))
                    .zIndex(3)
            }
        }
        .onAppear {
            if startInList { page = .list } else if startInDeck { page = .discover }
            withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.5).delay(0.3)) { entered = true }
        }
        .edgeSwipeBack { back() }
        .alert(t("Öneriyi bildir"), isPresented: $reportNotice) { Button(t("Tamam"), role: .cancel) {} } message: {
            Text(t("Bildirim göndermek için REAL'e giriş yapman gerekiyor."))
        }
        .sheet(item: $rateWatched) { media in
            FilmRatingSheet(media: media, meta: [media.year.map(String.init), t("İzledim")].compactMap { $0 }.joined(separator: " · "), initial: nil) {
                MediaRatingStore.shared.rate(media, $0, past: true)
            }
        }
    }

    private func back() {
        if detail != nil { withAnimation(.easeOut(duration: 0.12)) { detail = nil }; return }
        // Inside the picker: the previous question. From the deck: straight back to Yaşam (the picker was done).
        if page == .filters && pickerStep > 0 { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.32)) { pickerStep -= 1 }; return }
        if page == .filters || page == .discover || (startInList && page == .list) { onClose() }
        else { withAnimation(.easeOut(duration: 0.25)) { page = .filters }; error = nil }
    }

    // MARK: Filters

    private var filterPage: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                YasamHeroSentence(greeting: "", sentence: kind == .film ? t("Ne izlesek") : t("Ne oynasak"), tone: accent,
                                  hour: Calendar.real.component(.hour, from: Date()), revealed: true, compact: true)
                    .padding(.top, 4)
                BackCircle(action: back).padding(.leading, 22).padding(.top, 12)
                    .accessibilityLabel(t("Yaşam'a dön"))
            }
            if let error { errorLine(error) }
            // One question at a time (DiscoveryStepper): 2 genres at most, then optional extras.
            DiscoveryStepper(steps: kind == .film ? filmSteps : gameSteps, selection: Binding(
                get: { filters.union(platforms) },
                set: { all in
                    let platformKeys = Set(GamePlatformGroup.all.map(\.key))
                    filters = all.subtracting(platformKeys); platforms = all.intersection(platformKeys)
                }),
                accent: accent, loading: loading, enabled: discovery.service.available,
                doneLabel: kind == .film ? t("afişleri getir") : t("oyunları getir"), step: $pickerStep,
                rare: { chosen, key in DiscoveryRarePairs.rare(kind == .film ? DiscoveryRarePairs.film : DiscoveryRarePairs.game, chosen, key) },
                onDone: fetch)
                .opacity(entered ? 1 : 0).offset(y: entered ? 0 : 16)
        }
    }

    private var filmSteps: [DiscoveryStep] {
        let film = DiscoveryFilter.film
        var themeOrder: [String] = []
        for f in film where f.themeGroup != nil { if !themeOrder.contains(f.themeGroup!) { themeOrder.append(f.themeGroup!) } }
        let typeRow = AnyView(ChipFlow(spacing: 8) {
            ForEach([("BOTH", "Film + Dizi"), ("MOVIE", "Film"), ("TV", "Dizi")], id: \.0) { key, label in
                DiscoveryChip(label: t(label), selected: type == key, enabled: !loading, accent: accent) { type = key }
            }
        })
        return [
            DiscoveryStep(title: t("Hangi türde bir şey olsun?"), subtitle: t("En fazla 2 tür"), max: 2, optional: false,
                          groups: [(nil, film.filter(\.genre).map { StepOption(key: $0.key, label: filterLabel($0.label)) })], top: typeRow),
            DiscoveryStep(title: t("Konusu ne olsun?"), subtitle: t("İstersen bir tane"), max: 1, optional: true,
                          groups: themeOrder.map { g in (t(g), film.filter { $0.themeGroup == g }.map { StepOption(key: $0.key, label: filterLabel($0.label)) }) }),
            DiscoveryStep(title: t("Nasıl hissettirsin?"), subtitle: t("İstersen bir tane"), max: 1, optional: true,
                          groups: [(nil, film.filter { !$0.genre && $0.themeGroup == nil }.map { StepOption(key: $0.key, label: filterLabel($0.label)) })]),
        ]
    }

    private var gameSteps: [DiscoveryStep] {
        let game = DiscoveryFilter.game
        return [
            DiscoveryStep(title: t("Hangi türde bir oyun?"), subtitle: t("En fazla 2 tür"), max: 2, optional: false,
                          groups: [(nil, game.filter(\.genre).map { StepOption(key: $0.key, label: filterLabel($0.label)) })]),
            DiscoveryStep(title: t("Nasıl hissettirsin?"), subtitle: t("İstersen bir tane"), max: 1, optional: true,
                          groups: [(nil, game.filter { !$0.genre }.map { StepOption(key: $0.key, label: filterLabel($0.label)) })]),
            DiscoveryStep(title: t("Nerede oynayacaksın?"), subtitle: t("İstersen bir platform"), max: 1, optional: true,
                          groups: [(nil, GamePlatformGroup.all.map { StepOption(key: $0.key, label: t($0.label)) })]),
        ]
    }

    /// "Tarih" and "Spor" mean a genre here, not a date or exercise.
    private func filterLabel(_ label: String) -> String {
        label == "Tarih" || label == "Spor" ? tIn("genre", label) : t(label)
    }

    private func fetch() {
        guard !loading else { return }
        loading = true
        error = nil
        Task {
            do {
                try await fetchWidening()
                withAnimation(.easeOut(duration: 0.3)) { page = .discover }
            } catch let e {
                error = e.localizedDescription
            }
            loading = false
        }
    }

    /// The exact choice first; when it finds fewer than six titles the request widens quietly into the same deck:
    /// feeling / theme go, then the platform, then each genre on its own.
    private func fetchWidening() async throws {
        let t = kind == .film ? type : "GAME"
        let p = kind == .game ? platforms : []
        let genres = Set(filters.filter { k in catalog.first { $0.key == k }?.genre == true })
        var firstError: Error?
        do { try await discovery.fetch(kind, type: t, filters: filters, platforms: p) } catch { firstError = error }
        var wider: [(Set<String>, Set<String>)] = []
        if !genres.isEmpty && genres.count < filters.count { wider.append((genres, p)) }
        if !p.isEmpty && !genres.isEmpty { wider.append((genres, [])) }
        if genres.count > 1 { wider += genres.map { ([$0], []) } }
        for (f, plat) in wider where discovery.snapshot(kind).cards.count < 6 {
            do { try await discovery.fetch(kind, type: t, filters: f, platforms: plat, deckFilters: filters); firstError = nil } catch {}
        }
        if let firstError, discovery.snapshot(kind).cards.isEmpty { throw firstError }
    }

    private func errorLine(_ text: String) -> some View {
        Text(text).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFE6BCB1))
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20).padding(.vertical, 10)
    }

    // MARK: Deck

    private var deckPage: some View {
        let isList = page == .list
        return ZStack(alignment: .top) {
            PosterAmbient(url: cards.first?.media.artworkUrl)
            SwipeEdgeGlow(offset: dragOffset, listMode: isList).ignoresSafeArea()
            VStack(spacing: 0) {
                HStack { BackCircle(action: back); Spacer() }.padding(.horizontal, 20).padding(.vertical, 10)
                DiscoveryHeadline(text: isList ? (kind == .film ? t("İZLENECEK LİSTEM") : t("OYNANACAK LİSTEM")) : t("SANA GÖRE"), small: isList)
                    .padding(.top, 2).padding(.bottom, 6)
                if let first = cards.first {
                    Button { reportNotice = true } label: {
                        Text(t("Öneriyi bildir")).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.7)).padding(6)
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint(first.media.title)
                    PosterDeck(cards: cards, filters: Set(discovery.snapshot(kind).filters), kind: kind, later: isList, accent: accent,
                               offset: $dragOffset,
                               onOpenDetails: { c in withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.38)) { detail = c } },
                               onDecision: decide, onAddToReal: { c in addToReal(c) })
                        .padding(.horizontal, 14)
                } else {
                    VStack(spacing: 16) {
                        Text(isList ? (kind == .film ? t("İzlenecek listen henüz boş.") : t("Oynanacak listen henüz boş.")) : t("Bu destenin sonuna geldin."))
                            .font(RealFont.inter(15)).foregroundStyle(.white)
                        if !isList {
                            DiscoveryChip(label: loading ? t("Hazırlanıyor…") : t("Daha fazla öner"), selected: false, enabled: !loading, accent: accent) {
                                let s = discovery.snapshot(kind)
                                type = s.type; filters = Set(s.filters)
                                fetch()
                            }
                        }
                    }
                    .padding(.top, 36)
                    Spacer()
                }
                if let error { errorLine(error) }
            }
            if !cards.isEmpty {
                if isList { SwipeTutorial(kind: kind == .film ? "film_list" : "game_list", rightLabel: t("Kaldır"), leftLabel: t("Sona at")) }
                else { SwipeTutorial(kind: kind == .film ? "film" : "game", rightLabel: t("Kaydet"), leftLabel: t("Geç")) }
            }
        }
        // Keep the deck stocked: with a couple of posters left the next ones load in the background.
        .onChange(of: cards.count, initial: true) { _, count in
            guard !isList, !loading, error == nil, count <= 2, page == .discover else { return }
            let s = discovery.snapshot(kind)
            guard !s.cards.isEmpty || !s.excluded.isEmpty else { return }
            type = s.type; filters = Set(s.filters)
            fetch()
        }
    }

    /// Returns false when the poster should spring back (add failed).
    private func decide(_ card: DiscoveryCard, _ action: DiscoveryAction) -> Bool {
        error = nil
        do {
            if action == .saveDiscovery || action == .saveList { addToReal(card) }
            try discovery.act(kind, card, action)
            // "İzledim" on a film asks for its score right away (optional); it goes to Puanlarım as the past.
            if action == .watched, kind == .film {
                MediaRatingStore.shared.importWatched([card])
                if card.media.type == .MOVIE { rateWatched = card.media }
            }
            return true
        } catch let e {
            error = e.localizedDescription
            return false
        }
    }

    /// "REAL'e ekle": films / series as a media block, games as a game block (a game stays in the deck).
    private func addToReal(_ card: DiscoveryCard) {
        if kind == .game {
            let m = card.media
            let id = m.providerId ?? m.providerUrl
            guard !store.reminders.contains(where: { r in r.gameBlocks.contains { $0.providerId == id } }) else { RealToast.shared.show(t("Bu oyun zaten bir kartta")); return }
            let block = CardGameBlock(providerId: id, title: m.title, year: m.year,
                                      genres: (m.genre ?? "").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty },
                                      platforms: card.platforms, artworkUrl: m.artworkUrl, description: m.description, providerUrl: m.providerUrl)
            _ = store.addReminder(text: m.title) { r in r.gameBlocks = [block]; r.sendImmediateNotification = false }
            RealToast.shared.show(t("REAL'e eklendi"))
            return
        }
        addToReal(card.media)
    }

    private func addToReal(_ media: MediaBlock) {
        let exists = store.reminders.contains { r in
            r.mediaBlocks.contains { $0.type == media.type && $0.provider == media.provider &&
                (media.providerId != nil ? $0.providerId == media.providerId : $0.providerUrl == media.providerUrl) }
        }
        guard !exists else { return }
        _ = store.addReminder(text: media.title) { r in r.mediaBlocks = [media]; r.sendImmediateNotification = false }
    }
}

// MARK: Pieces

struct BackCircle: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            RealSymbol("chevron.left", size: 19.5).foregroundStyle(.white)
                .frame(width: 40, height: 40).liquidGlass(Circle(), role: .chrome)
        }
        .buttonStyle(.plain)
    }
}

/// DiscoveryButton: liquid chrome capsule, selected = accent tint + accent border.
struct DiscoveryChip: View {
    let label: String
    let selected: Bool
    var enabled = true
    var accent: Color = discoveryBlue
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label).font(RealFont.inter(13, selected ? .semibold : .regular))
                .foregroundStyle(enabled ? .white : Color(argb: 0xFF66666C))
                .padding(.horizontal, 14).frame(minHeight: 44)
                .background {
                    ZStack {
                        Capsule().fill(Color.white.opacity(0.06))
                        Capsule().strokeBorder(LinearGradient(colors: [.white.opacity(0.22), .white.opacity(0.05)], startPoint: .top, endPoint: .bottom), lineWidth: 0.7)
                        if selected {
                            Capsule().fill(accent.opacity(0.28))
                            Capsule().strokeBorder(accent.opacity(0.9), lineWidth: 1)
                        }
                    }
                }
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// DiscoverySelectionPager: extra choices stay in the same horizontal area; height follows the current page.
struct DiscoverySelectionPager<Page: View>: View {
    let titles: [String]
    let selectedCounts: [Int]
    var accent: Color = discoveryBlue
    @ViewBuilder let page: (Int) -> Page

    @State private var current: Int? = 0
    @State private var heights: [Int: CGFloat] = [:]

    var body: some View {
        let index = current ?? 0
        VStack(spacing: 10) {
            HStack {
                Text(titles[min(index, titles.count - 1)]).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.55))
                Spacer()
                if titles.count > 1 { Text("\(index + 1)/\(titles.count)  ↔").font(RealFont.inter(11)).foregroundStyle(.white.opacity(0.4)) }
            }
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: 0) {
                    ForEach(titles.indices, id: \.self) { i in
                        page(i)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .fixedSize(horizontal: false, vertical: true)
                            .background(GeometryReader { g in Color.clear.preference(key: PagerHeightKey.self, value: [i: g.size.height]) })
                            .containerRelativeFrame(.horizontal)
                            .id(i)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $current)
            .scrollIndicators(.hidden)
            .frame(height: heights[index] ?? 120, alignment: .top)
            .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.22), value: heights[index])
            .onPreferenceChange(PagerHeightKey.self) { heights.merge($0) { _, new in new } }
            if titles.count > 1 {
                HStack(spacing: 0) {
                    ForEach(titles.indices, id: \.self) { i in
                        let active = index == i
                        let count = i < selectedCounts.count ? selectedCounts[i] : 0
                        Button { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.36)) { current = i } } label: {
                            Circle().fill(active || count > 0 ? accent : .white.opacity(0.2))
                                .frame(width: active ? 7 : 5, height: active ? 7 : 5)
                                .frame(width: 32, height: 32)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(t("{0}, {1}. sayfa, {2} seçili", titles[i], i + 1, count))
                    }
                }
            }
        }
    }
}

private struct PagerHeightKey: PreferenceKey {
    static var defaultValue: [Int: CGFloat] = [:]
    static func reduce(value: inout [Int: CGFloat], nextValue: () -> [Int: CGFloat]) { value.merge(nextValue()) { _, new in new } }
}

/// DiscoveryLoadingButton: black pill with fixed coloured corners; while loading a white beam orbits the edge.
struct DiscoveryLoadingButton: View {
    let label: String
    let loading: Bool
    let enabled: Bool
    let accent: Color
    let action: () -> Void
    @State private var spin = false

    var body: some View {
        let mid = accent == discoveryOrange ? Color(argb: 0xFFFFA11F) : Color(argb: 0xFF7BACFF)
        let strength: Double = enabled || loading ? 1 : 0.4
        let fixed = AngularGradient(stops: [
            .init(color: accent.opacity(0.12), location: 0), .init(color: accent.opacity(0.85), location: 0.12),
            .init(color: mid, location: 0.23), .init(color: accent.opacity(0.18), location: 0.34),
            .init(color: accent.opacity(0.12), location: 0.5), .init(color: accent.opacity(0.85), location: 0.62),
            .init(color: mid, location: 0.73), .init(color: accent.opacity(0.18), location: 0.84),
            .init(color: accent.opacity(0.12), location: 1)], center: .center)
        let beam = AngularGradient(stops: [
            .init(color: .clear, location: 0), .init(color: .clear, location: 0.55),
            .init(color: accent.opacity(0.22), location: 0.65), .init(color: accent.blend(.white, by: 0.5).opacity(0.45), location: 0.73),
            .init(color: accent.blend(.white, by: 0.8), location: 0.81), .init(color: .white, location: 0.86),
            .init(color: .white, location: 0.9), .init(color: accent.opacity(0.45), location: 0.94),
            .init(color: .clear, location: 1)], center: .center, angle: .degrees(spin ? 360 : 0))
        Button(action: action) {
            Text(loading ? t("Hazırlanıyor…") : label).font(RealFont.inter(16, .semibold)).foregroundStyle(.white).lineLimit(1)
                .frame(maxWidth: .infinity).frame(height: 56)
                .background(Color.black, in: Capsule())
                .overlay {
                    ZStack {
                        Capsule().strokeBorder(.white.opacity(enabled || loading ? 0.12 : 0.07), lineWidth: 1)
                        Capsule().strokeBorder(fixed, lineWidth: 1.2).opacity(0.85 * strength)
                        Capsule().stroke(fixed, lineWidth: 10).blur(radius: 6).opacity(0.12 * strength)
                        if loading {
                            Capsule().strokeBorder(beam, lineWidth: 1.4)
                            Capsule().stroke(beam, lineWidth: 8).blur(radius: 5).opacity(0.35)
                        }
                    }
                }
        }
        .buttonStyle(.plain)
        .disabled(!enabled || loading)
        .padding(.horizontal, 28)
        .onChange(of: loading) { _, l in
            if l { spin = false; withAnimation(.linear(duration: 2.6).repeatForever(autoreverses: false)) { spin = true } }
            else { withAnimation(.linear(duration: 0)) { spin = false } }
        }
    }
}

struct DiscoveryHeadline: View {
    let text: String
    var small = false
    var body: some View {
        Text(text)
            .font(.system(size: small ? 30 : 46, weight: .heavy))
            .kerning(-1.5).lineLimit(1).minimumScaleFactor(0.6)
            .foregroundStyle(LinearGradient(colors: [.white, Color(argb: 0xFFB4B4B9)], startPoint: .top, endPoint: .bottom))
            .frame(maxWidth: .infinity)
    }
}

/// PosterEdgeGlow.kt: six lights from the cover's own left / right regions behind the deck (nothing over the cover).
/// `playing` makes them grow and brighten with the music preview's beat. A SwiftUI Canvas clips to its frame
/// (Compose's does not), so the lights get a wide margin and fade out softly instead of ending in a rectangle.
struct PosterEdgeGlow: View {
    let url: String?
    var playing: Bool = false
    @State private var colors: [Color] = []

    /// MusicDiscoveryScreen.kt's sound pulse: a quick beat on a slow swell, 0…1.
    static func beat(_ t: Double) -> Double {
        let b = pow(sin(t * 7) * 0.5 + 0.5, 2)
        return min(1, max(0, 0.25 + 0.45 * b + 0.10 * sin(t * 2.3)))
    }

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            let pad = max(w, h) * 0.8
            TimelineView(.animation(paused: !playing)) { t in
                let beat = playing ? Self.beat(t.date.timeIntervalSinceReferenceDate) : 0
                Canvas { ctx, _ in
                    let radius = w * (0.47 + 0.14 * beat)
                    let boost = 1 + 1.3 * beat
                    for (region, color) in colors.enumerated() {
                        let center = CGPoint(x: pad + w * (region % 2 == 0 ? 0.15 : 0.85), y: pad + h * (0.16 + CGFloat(region / 2) * 0.34))
                        let g = Gradient(stops: [.init(color: color.opacity(min(0.8, 0.32 * boost)), location: 0),
                                                 .init(color: color.opacity(min(0.4, 0.12 * boost)), location: 0.5),
                                                 .init(color: .clear, location: 1)])
                        ctx.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)),
                                 with: .radialGradient(g, center: center, startRadius: 0, endRadius: radius))
                    }
                }
                .frame(width: w + pad * 2, height: h + pad * 2)
            }
            .frame(width: w, height: h)
        }
        .allowsHitTesting(false)
        .task(id: url) {
            guard let url, let img = await PosterImageCache.load(url), let c = CoverPalette.edgeRegions(img) else {
                withAnimation(.easeOut(duration: 0.38)) { colors = [] }; return
            }
            withAnimation(.easeOut(duration: 0.38)) { colors = c }
        }
    }
}

/// PosterAmbient: the top poster's colour as a blurred light at the top of the screen.
struct PosterAmbient: View {
    let url: String?
    var body: some View {
        // The poster goes in an overlay of a plain clear box: scaledToFill reports the poster's full (taller) size, which
        // stretched the black fade over ~600pt, so the 210pt window cut it off halfway and left a hard dark rectangle.
        Color.clear
            .overlay {
                if let url, let u = URL(string: url) {
                    AsyncImage(url: u) { img in img.resizable().scaledToFill() } placeholder: { Color.clear }
                        .blur(radius: 48).opacity(0.85)
                }
            }
            .overlay { LinearGradient(colors: [.clear, .black], startPoint: .top, endPoint: .bottom) }
            .frame(height: 210).frame(maxWidth: .infinity).clipped()
        .ignoresSafeArea(edges: .top)
        .animation(.easeInOut(duration: 0.45), value: url)
        .allowsHitTesting(false)
    }
}

/// Posters kept in memory (Android: Coil's cache). AsyncImage starts empty on every new view, so each swipe showed
/// the dark poster face under the deck until the image came back; a cached poster now shows at once.
@MainActor
/// "REAL'e ekle" in the poster's dominant colour; very dark posters are lifted so the button still reads on black,
/// light ones get dark text. Until the poster is read the screen's own accent is used.
struct PosterAddLabel: View {
    let url: String?
    let fallback: Color
    let busy: Bool
    @State private var poster: Color?

    var body: some View {
        let tone: Color = poster.map { $0.luminance < 0.03 ? Self.lift($0, 0.22) : $0 } ?? fallback
        let ink: Color = busy ? Color(argb: 0xFF66666C) : (tone.luminance > 0.5 ? Color(argb: 0xFF111111) : .white)
        HStack(spacing: 7) {
            RealSymbol("plus", size: 18.4)
            Text("REAL'e ekle").font(RealFont.inter(13, .medium)).lineLimit(1)
        }
        .foregroundStyle(ink)
        .padding(.horizontal, 16).frame(minHeight: 48)
        .background(tone.opacity(0.92), in: Capsule())
        .animation(.easeOut(duration: 0.22), value: poster)
        .task(id: url) {
            guard let url, !url.isEmpty, let img = await PosterImageCache.load(url) else { poster = nil; return }
            poster = CoverPalette.dominant(img)
        }
    }

    /// Blends towards white (Color.mix needs iOS 18).
    private static func lift(_ c: Color, _ t: CGFloat) -> Color {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(c).getRed(&r, green: &g, blue: &b, alpha: &a)
        return Color(red: r + (1 - r) * t, green: g + (1 - g) * t, blue: b + (1 - b) * t)
    }
}

enum PosterImageCache {
    private static let cache: NSCache<NSString, UIImage> = { let c = NSCache<NSString, UIImage>(); c.countLimit = 80; return c }()

    static func image(_ url: String) -> UIImage? { cache.object(forKey: url as NSString) }

    static func load(_ url: String) async -> UIImage? {
        if let hit = image(url) { return hit }
        guard let u = URL(string: url), let fetched = try? await URLSession.shared.data(from: u),
              let img = UIImage(data: fetched.0) else { return nil }
        cache.setObject(img, forKey: url as NSString)
        return img
    }
}

/// Poster artwork. Like Android, the video glyph + title show only when there is no image (none, or it failed);
/// while it loads the face stays plain.
struct PosterArtwork: View {
    let media: MediaBlock
    var titleSize: CGFloat = 20
    /// false: the image is not requested yet (a deck poster waits for the front one).
    var load = true
    @State private var image: UIImage?
    @State private var failed = false

    private var url: String? { media.artworkUrl.flatMap { $0.isEmpty ? nil : $0 } }

    var body: some View {
        let shown = image ?? url.flatMap { PosterImageCache.image($0) }
        ZStack {
            Color(argb: 0xFF1B1B20)
            if let shown {
                // Sized by the container so the fill never grows the poster (or its touch area) past its frame.
                Color.clear.overlay(Image(uiImage: shown).resizable().scaledToFill()).clipped()
            } else if url == nil || failed {
                VStack(spacing: 14) {
                    RealSymbol("film", size: 50.6).foregroundStyle(Color(argb: 0xFFDDD4C6))
                    Text(media.title).font(RealFont.inter(titleSize)).foregroundStyle(.white).multilineTextAlignment(.center).lineLimit(4)
                }
                .padding(28)
            }
        }
        .task(id: load ? url : nil) {
            guard load, let url, image == nil else { return }
            if let img = await PosterImageCache.load(url) { image = img } else { failed = true }
        }
    }
}

private struct PosterLabel: View {
    let text: String
    var body: some View {
        Text(text).font(RealFont.inter(11, .medium)).foregroundStyle(.white).lineLimit(1)
            .padding(.horizontal, 9).padding(.vertical, 4)
            .background(Color.black.opacity(0.85), in: Capsule())
            .overlay(Capsule().strokeBorder(.white.opacity(0.12), lineWidth: 0.5))
    }
}

/// PosterDeck: up to four stacked posters; the front one is dragged away (swipe = skip / browse) or tapped for details.
private struct PosterDeck: View {
    let cards: [DiscoveryCard]
    let filters: Set<String>
    let kind: DiscoveryKind
    let later: Bool
    let accent: Color
    @Binding var offset: CGSize
    let onOpenDetails: (DiscoveryCard) -> Void
    let onDecision: (DiscoveryCard, DiscoveryAction) -> Bool
    let onAddToReal: (DiscoveryCard) -> Void

    @State private var busy = false
    /// The posters behind wait until the front one is on screen.
    @State private var frontReady = false
    private let threshold: CGFloat = 85

    var body: some View {
        GeometryReader { geo in
            let width = min(geo.size.width * 0.96, max(1, geo.size.height - 76 - 42) * posterAspect)
            let height = width / posterAspect
            let visible = Array(cards.prefix(4))
            ZStack(alignment: .top) {
                // No blurred copy of the front poster here: its unclipped blur left a rectangle behind the deck, and
                // it went black while the next poster's copy loaded. The screen's PosterAmbient gives the top light and
                // PosterEdgeGlow the side lights (as on Android).
                PosterEdgeGlow(url: cards.first?.media.artworkUrl)
                    .frame(width: geo.size.width + 28, height: height)
                    .frame(width: geo.size.width)
                ForEach(Array(visible.enumerated()).reversed(), id: \.element.id) { depth, item in
                    poster(item, depth: depth, width: width, height: height)
                }
                actionBar.offset(y: height + 12)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 34)
        }
        // The deck's first front poster loads alone; after that the posters behind always load, and the next ones
        // are fetched ahead so a swipe never shows an empty poster.
        .task(id: cards.first?.id) {
            if !frontReady {
                if let s = cards.first?.media.artworkUrl { _ = await PosterImageCache.load(s) }
                frontReady = true
            }
            for c in cards.dropFirst().prefix(6) {
                if let s = c.media.artworkUrl { _ = await PosterImageCache.load(s) }
            }
        }
    }

    private func poster(_ item: DiscoveryCard, depth: Int, width: CGFloat, height: CGFloat) -> some View {
        let d = CGFloat(depth)
        let front = depth == 0
        let scale: CGFloat = 1 - d * 0.045
        let lift: CGFloat = -d * (10 + 0.045 * height / 2)
        let fade: Double = later && front ? 1 : 1 - Double(d) * 0.1
        let raw: CGFloat = offset.width / threshold * 7 + offset.height / threshold * 2
        let angle: Double = front ? Double(max(-24, min(24, raw))) : 0
        let shift: CGSize = front ? offset : .zero
        return posterFace(item, front: front, width: width, height: height)
            .scaleEffect(scale)
            .offset(y: lift)
            .opacity(fade)
            .offset(shift)
            .rotationEffect(.degrees(angle))
            .zIndex(Double(10 - depth))
            .animation(.spring(response: 0.42, dampingFraction: 0.85), value: depth)
            .gesture(drag(item), including: front ? .all : .subviews)
            .onTapGesture { if front && !busy { onOpenDetails(item) } }
    }

    private func posterFace(_ item: DiscoveryCard, front: Bool, width: CGFloat, height: CGFloat) -> some View {
        PosterArtwork(media: item.media, load: front || frontReady)
            .frame(width: width, height: height)
            .overlay(alignment: .topLeading) { if front { leftLabels(item) } }
            .overlay(alignment: .topTrailing) { if front { rightLabels(item) } }
            .overlay { if front && busy { ProgressView().tint(.white) } }
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .accessibilityElement()
            .accessibilityLabel(item.media.title)
            .accessibilityAddTraits(front ? .isButton : [])
    }

    private func leftLabels(_ item: DiscoveryCard) -> some View {
        let series = item.media.type == .TV
        return VStack(alignment: .leading, spacing: 6) {
            PosterLabel(text: mediaDurationLabel(item.media.durationMillis, series: series) ?? item.media.year.map(String.init) ?? (series ? t("Dizi") : t("Film")))
            if let g = DiscoveryRanking.displayGenre(item.media.genre) { PosterLabel(text: g) }
        }
        .padding(14)
    }

    private func rightLabels(_ item: DiscoveryCard) -> some View {
        let m = DiscoveryRanking.match(item, filters, kind: kind)
        return VStack(alignment: .trailing, spacing: 6) {
            PosterLabel(text: kind == .game ? t("Oyun") : (item.media.type == .TV ? t("Dizi") : t("Film")))
            if !filters.isEmpty { PosterLabel(text: "\(m.count)/\(filters.count)\(m.inferred ? " · AI" : "")") }
        }
        .padding(14)
    }

    private func drag(_ item: DiscoveryCard) -> some Gesture {
        DragGesture()
            .onChanged { v in if !busy { offset = v.translation } }
            .onEnded { _ in
                guard !busy else { return }
                // Right = save (list: keep, next) · left = pass (list: remove) · up / down: nothing yet.
                if abs(offset.height) > abs(offset.width) { withAnimation(.spring()) { offset = .zero } }
                // In the saved list: right removes, left sends the poster to the back.
                else if offset.width > threshold { decide(item, later ? .removeFromList : .addToList, fly: CGSize(width: threshold * 6, height: 0)) }
                else if offset.width < -threshold { decide(item, later ? .browseList : .skip, fly: CGSize(width: -threshold * 6, height: 0)) }
                else { withAnimation(.spring()) { offset = .zero } }
            }
    }

    private func decide(_ item: DiscoveryCard, _ action: DiscoveryAction, fly: CGSize) {
        guard !busy else { return }
        busy = true
        withAnimation(.easeIn(duration: 0.24)) { offset = fly }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.24) {
            if onDecision(item, action) { var t = Transaction(); t.disablesAnimations = true; withTransaction(t) { offset = .zero } }
            else { withAnimation(.spring()) { offset = .zero } }
            busy = false
        }
    }

    /// Under the poster: "REAL'e ekle" and "İzledim" / "Oynadım" (swipes save and pass).
    private var actionBar: some View {
        HStack(spacing: 6) {
            if let first = cards.first {
                Button {
                    if kind == .game { onAddToReal(first) }
                    else { decide(first, later ? .saveList : .saveDiscovery, fly: CGSize(width: threshold * 6, height: 0)) }
                } label: {
                    PosterAddLabel(url: first.media.artworkUrl, fallback: accent, busy: busy)
                }
                .buttonStyle(.plain)
                .disabled(busy)
                Button { decide(first, .watched, fly: CGSize(width: 0, height: -threshold * 9)) } label: {
                    HStack(spacing: 7) {
                        RealSymbol("checkmark", size: 17)
                        Text(kind == .game ? t("Oynadım") : t("İzledim")).font(RealFont.inter(13, .medium)).lineLimit(1)
                    }
                    .foregroundStyle(busy ? Color(argb: 0xFF66666C) : .white)
                    .padding(.horizontal, 16).frame(minHeight: 48)
                    .background(Color.white.opacity(0.10), in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(busy)
            }
        }
        .padding(.horizontal, 8).padding(.vertical, 8)
        .background(Color.black.opacity(0.55), in: Capsule())
        .liquidGlass(Capsule(), role: .chrome)
    }

    private func barIcon(_ symbol: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            RealSymbol(symbol, size: 21.8).foregroundStyle(busy ? Color(argb: 0xFF66666C) : .white.opacity(0.85))
                .frame(width: 48, height: 48)
        }
        .buttonStyle(.plain)
        .disabled(busy)
        .accessibilityLabel(label)
    }
}

private struct DetailPullKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

/// MediaDiscoveryDetail: poster shrinks into a header image, then title, overview with "Devamını oku", cast and source.
struct MediaDiscoveryDetail: View {
    let card: DiscoveryCard
    let kind: DiscoveryKind
    let onBack: () -> Void

    @State private var detail: MediaDetail?
    @State private var loading = true
    @State private var failed = false
    @State private var readMore = false
    @State private var arrived = false
    @State private var leaving = false
    /// Pull-down from the top closes the detail (like a sheet): the page shrinks a little and the posters show through.
    @State private var pull: CGFloat = 0
    @State private var restTop: CGFloat?

    private var pullProgress: CGFloat { min(pull / Self.pullToClose, 1) }
    private static let pullToClose: CGFloat = 110

    var body: some View {
        GeometryReader { geo in
            let heroWidth = max(1, geo.size.width - 40)
            let finalHeight = min(heroWidth * 0.88, 300)
            let initialHeight = min(heroWidth / posterAspect, max(1, geo.size.height - 88))
            ZStack(alignment: .top) {
                Color.black.opacity(0.94 * (1 - Double(pullProgress) * 0.45)).ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        Color.clear.frame(height: 60)
                            .background(GeometryReader { g in
                                Color.clear.preference(key: DetailPullKey.self, value: g.frame(in: .named("detailScroll")).minY)
                            })
                        PosterArtwork(media: card.media, titleSize: 18)
                            .frame(width: heroWidth, height: arrived && !leaving ? finalHeight : initialHeight)
                            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            .overlay(alignment: .bottomLeading) {
                                // "▶ Fragman": the film's YouTube trailer (TheTVDB), in the YouTube app or the browser.
                                if let trailer = detail?.trailerUrl, !leaving {
                                    Button { UIApplication.shared.open(trailer) } label: {
                                        HStack(spacing: 6) {
                                            RealSymbol("play.fill", size: 11)
                                            Text(t("Fragman")).font(RealFont.inter(13, .semibold))
                                        }
                                        .foregroundStyle(.white).padding(.horizontal, 14).frame(height: 36)
                                        .liquidGlass(Capsule(), role: .chrome)
                                    }
                                    .buttonStyle(.plain).padding(12).accessibilityLabel(t("Fragmanı aç"))
                                }
                            }
                            .padding(.horizontal, 20)
                        content.opacity(leaving ? 0 : 1)
                    }
                }
                .scrollIndicators(.hidden)
                .coordinateSpace(name: "detailScroll")
                .onPreferenceChange(DetailPullKey.self) { y in
                    let rest = restTop ?? y
                    if restTop == nil { restTop = y }
                    pull = max(0, y - rest)
                    if pull >= Self.pullToClose && !leaving {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        close()
                    }
                }
                // No glass behind the header: content only fades out softly at the very top (films, series, games).
                LinearGradient(colors: [.black, .black.opacity(0.85), .clear], startPoint: .top, endPoint: .bottom)
                    .frame(height: 64).ignoresSafeArea(edges: .top).allowsHitTesting(false)
                header.opacity(leaving ? 0 : 1 - pullProgress)
            }
            .scaleEffect(1 - pullProgress * 0.06, anchor: .bottom)
        }
        .onAppear { withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.38)) { arrived = true } }
        .task(id: card.id) {
            loading = true; failed = false
            do { detail = try await MediaDetailRepository.load(card.media) } catch { failed = true }
            loading = false
        }
    }

    private func close() {
        withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.38)) { leaving = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.38) { onBack() }
    }

    private var header: some View {
        HStack(spacing: 6) {
            BackCircle(action: close).accessibilityLabel(t("Afişlere dön"))
            Spacer()
            capsule(kind == .game ? t("Oyun") : (card.media.type == .TV ? t("Dizi") : t("Film")))
            capsule(mediaDurationLabel(detail?.durationMillis ?? card.media.durationMillis, series: card.media.type == .TV))
            capsule(nil, rating: true)
        }
        .padding(.horizontal, 20).padding(.vertical, 10)
    }

    private func capsule(_ label: String?, rating: Bool = false) -> some View {
        Text(label ?? " ").font(RealFont.inter(11)).foregroundStyle(.white).lineLimit(1)
            .padding(.horizontal, 10).frame(minWidth: rating ? 54 : 48, minHeight: 30)
            .liquidGlass(Capsule(), role: .chrome)
            .accessibilityLabel(label ?? (rating ? t("Puan henüz mevcut değil") : t("Süre bilgisi mevcut değil")))
    }

    private var content: some View {
        let description = detail?.description.flatMap { $0.isEmpty ? nil : $0 } ?? card.media.description
        return VStack(alignment: .leading, spacing: 0) {
            Text(card.media.title).font(RealFont.inter(26, .medium)).foregroundStyle(.white).padding(.horizontal, 20).padding(.top, 20)
            Text(description ?? t("Bu yapımın konusu henüz bulunamadı."))
                .font(RealFont.inter(14)).lineSpacing(7).foregroundStyle(.white.opacity(0.72))
                .lineLimit(readMore ? nil : 4)
                .mask(LinearGradient(stops: [.init(color: .black, location: 0), .init(color: .black, location: readMore ? 1 : 0.75),
                                             .init(color: .black.opacity(readMore ? 1 : 0), location: 1)], startPoint: .top, endPoint: .bottom))
                .padding(.horizontal, 20).padding(.top, 12)
                .animation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.38), value: readMore)
            if description?.isEmpty == false {
                Button(readMore ? t("Daha az") : t("Devamını oku")) { readMore.toggle() }
                    .font(RealFont.inter(12, .semibold)).foregroundStyle(.white).padding(8).padding(.horizontal, 12)
            }
            if kind == .game, let id = card.media.providerId {
                GameMediaSection(providerId: id, accent: discoveryOrange).padding(.horizontal, 20).padding(.top, 14)
            }
            Text("Cast").font(RealFont.inter(19, .medium)).foregroundStyle(.white).padding(.leading, 20).padding(.top, 22).padding(.bottom, 12)
            castRow
            Link(t("{0} · Kaynak", card.media.provider), destination: sourceUrl)
                .font(RealFont.inter(11)).foregroundStyle(.white.opacity(0.4)).padding(8).padding(.leading, 12).padding(.top, 18)
            Color.clear.frame(height: 24)
        }
    }

    private var sourceUrl: URL {
        if card.media.provider.lowercased() == "tvmaze", let id = card.media.providerId { return URL(string: "https://www.tvmaze.com/shows/\(id)")! }
        return URL(string: card.media.providerUrl) ?? URL(string: "https://thetvdb.com")!
    }

    @ViewBuilder
    private var castRow: some View {
        if loading {
            HStack(spacing: 10) {
                ProgressView().tint(.white.opacity(0.65)).scaleEffect(0.8)
                Text(t("Oyuncular yükleniyor…")).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.55))
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        } else if failed || detail?.cast.isEmpty != false {
            Text(failed ? t("Oyuncular şu anda alınamadı.") : t("Oyuncu bilgisi bulunamadı."))
                .font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.55)).padding(.horizontal, 20)
        } else if let detail {
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: 12) {
                    ForEach(detail.cast) { person in
                        VStack(alignment: .leading, spacing: 0) {
                            ZStack {
                                Color(argb: 0xFF202025)
                                Text(String(person.name.prefix(1))).font(RealFont.inter(28)).foregroundStyle(.white.opacity(0.5))
                                if let u = person.imageUrl {
                                    AsyncImage(url: u) { p in if let i = p.image { i.resizable().scaledToFill() } }
                                }
                            }
                            .frame(width: 94, height: 124).clipShape(RoundedRectangle(cornerRadius: 12))
                            Text(person.name).font(RealFont.inter(12, .semibold)).foregroundStyle(.white).lineLimit(2).padding(.top, 7)
                            if let role = person.role, !role.isEmpty {
                                Text(role).font(RealFont.inter(10)).foregroundStyle(.white.opacity(0.45)).lineLimit(2).padding(.top, 2)
                            }
                        }
                        .frame(width: 94)
                    }
                }
                .padding(.horizontal, 20)
            }
            .scrollIndicators(.hidden)
        }
    }
}

/// MediaContentSection: "Film / Dizi" block on a card's detail page.
struct MediaBlockView: View {
    let block: MediaBlock
    var editing = false
    var onRemove: () -> Void = {}
    /// > 0: drawn as the card's cover (CatalogHeroCover) under the status bar.
    var heroInset: CGFloat = 0

    var body: some View {
        if heroInset > 0 && !editing {
            CatalogHeroCover(imageURL: block.artworkUrl.flatMap(URL.init(string:)), posterAspect: 2.0 / 3.0, title: block.title,
                             subtitle: [block.year.map(String.init), block.type == .MOVIE ? t("Film") : t("Dizi"), block.genre].compactMap { $0 }.joined(separator: " • "),
                             description: block.description, placeholderSymbol: "film", inset: heroInset) {
                if let url = URL(string: block.providerUrl), ["http", "https"].contains(url.scheme ?? "") {
                    HeroOpenRow(label: openLabel, action: { UIApplication.shared.open(url) }) {
                        Text(providerName).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8E8E98))
                    }
                }
            }
        } else {
            compact
        }
    }

    private var providerName: String {
        switch block.provider.trimmingCharacters(in: .whitespaces).lowercased() {
        case "tvmaze": return "TVmaze"
        case "thetvdb": return "TheTVDB"
        case "apple": return "Apple TV"
        default: return block.provider
        }
    }

    private var compact: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(block.type == .MOVIE ? t("Film") : t("Dizi")).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFA5A5B0))
                Spacer()
                if editing {
                    Button(action: onRemove) { RealSymbol("xmark", size: 14.9).foregroundStyle(Color(argb: 0xFFB8B8C2)).frame(width: 40, height: 40) }
                        .accessibilityLabel(t("İçeriği kaldır"))
                }
            }
            HStack(alignment: .top, spacing: 12) {
                PosterArtwork(media: block, titleSize: 11).frame(width: 96, height: 150).clipShape(RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 4) {
                    Text(block.title).font(RealFont.inter(18, .medium)).foregroundStyle(Color(argb: 0xFFF1F1F4)).lineLimit(2)
                    Text([block.year.map(String.init), block.type == .MOVIE ? t("Film") : t("Dizi"), block.genre].compactMap { $0 }.joined(separator: " • "))
                        .font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFB1B1BC)).lineLimit(1)
                    if let d = block.description, !d.isEmpty {
                        Text(d).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF868690)).lineLimit(5)
                    }
                }
            }
            if let url = URL(string: block.providerUrl), ["http", "https"].contains(url.scheme ?? "") {
                Link(destination: url) {
                    Text(openLabel).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFD0D0D8))
                        .frame(maxWidth: .infinity).frame(height: 40)
                        .background(Color.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 24))
                }
                .padding(.top, 2)
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Color(argb: 0xFF17171B)))
    }

    private var openLabel: String {
        switch block.provider.trimmingCharacters(in: .whitespaces).lowercased() {
        case "tvmaze": return t("TVmaze'de aç ↗")
        case "thetvdb": return t("TheTVDB'de aç ↗")
        case "apple": return t("Apple TV'de aç ↗")
        default: return t("{0}'de aç ↗", block.provider)
        }
    }
}

// MARK: Shake

extension Notification.Name {
    static let realDeviceShake = Notification.Name("REAL.deviceShake")
}

/// UIKit reports a shake to the key window; it is passed on to the SwiftUI screens that listen (.onShake).
extension UIWindow {
    open override func motionEnded(_ motion: UIEvent.EventSubtype, with event: UIEvent?) {
        if motion == .motionShake { NotificationCenter.default.post(name: .realDeviceShake, object: nil) }
        super.motionEnded(motion, with: event)
    }
}

extension View {
    /// Runs `action` when the phone is shaken while this view is on screen.
    func onShake(perform action: @escaping () -> Void) -> some View {
        onReceive(NotificationCenter.default.publisher(for: .realDeviceShake)) { _ in action() }
    }
}
