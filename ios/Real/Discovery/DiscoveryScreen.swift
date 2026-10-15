import SwiftUI

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
        .alert("Öneriyi bildir", isPresented: $reportNotice) { Button("Tamam", role: .cancel) {} } message: {
            Text("Bildirim göndermek için REAL'e giriş yapman gerekiyor.")
        }
    }

    private func back() {
        if detail != nil { detail = nil; return }
        if page == .filters || (startInList && page == .list) { onClose() }
        else { withAnimation(.easeOut(duration: 0.25)) { page = .filters }; error = nil }
    }

    // MARK: Filters

    private var filterPage: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ZStack(alignment: .topLeading) {
                        YasamHeroSentence(greeting: "", sentence: kind == .film ? "Ne izlesek" : "Ne oynasak", tone: accent,
                                          hour: Calendar.real.component(.hour, from: Date()), revealed: true, compact: true)
                            .padding(.top, 4)
                        BackCircle(action: back).padding(.leading, 22).padding(.top, 12)
                            .accessibilityLabel("Yaşam'a dön")
                    }
                    Text("Birlikte uyanlar önce gelir.").font(RealFont.inter(15)).foregroundStyle(YasamTone.muted)
                        .padding(.leading, 22).padding(.top, 12).padding(.bottom, 8)
                    filterBody
                        .padding(.horizontal, 20).padding(.top, 12)
                        .opacity(entered ? 1 : 0).offset(y: entered ? 0 : 16)
                }
            }
            .scrollIndicators(.hidden)
            if let error { errorLine(error) }
            DiscoveryLoadingButton(label: "Afişleri getir", loading: loading, enabled: discovery.service.available, accent: accent, action: fetch)
                .padding(.horizontal, 20).padding(.vertical, 14)
        }
    }

    @ViewBuilder
    private var filterBody: some View {
        VStack(alignment: .leading, spacing: 14) {
            if kind == .film {
                ChipFlow(spacing: 8) {
                    ForEach([("BOTH", "Film + Dizi"), ("MOVIE", "Film"), ("TV", "Dizi")], id: \.0) { key, label in
                        DiscoveryChip(label: label, selected: type == key, enabled: !loading, accent: accent) { type = key }
                    }
                }
            } else {
                Text("Platform").font(RealFont.inter(17, .medium)).foregroundStyle(.white.opacity(0.8))
                ChipFlow(spacing: 8) {
                    ForEach(GamePlatformGroup.all) { p in
                        let selected = platforms.contains(p.key)
                        DiscoveryChip(label: p.label, selected: selected, enabled: !loading, accent: accent) {
                            if selected { platforms.remove(p.key) } else { platforms.insert(p.key) }
                        }
                    }
                }
            }
            Text("Tür").font(RealFont.inter(17, .medium)).foregroundStyle(.white.opacity(0.8))
            let genrePages = self.genrePages
            DiscoverySelectionPager(titles: genrePages.map(\.0), selectedCounts: genrePages.map { $0.1.filter { filters.contains($0.key) }.count },
                                    accent: accent) { i in chips(genrePages[i].1) }
            Text("Nasıl hissettirsin?").font(RealFont.inter(17, .medium)).foregroundStyle(.white.opacity(0.8))
            let feelings = feelingPages
            DiscoverySelectionPager(titles: feelings.indices.map { $0 == 0 ? "Hisler" : "Diğer hisler" },
                                    selectedCounts: feelings.map { $0.filter { filters.contains($0.key) }.count }, accent: accent) { i in chips(feelings[i]) }
            // Catalog credit only, small and quiet.
            Text(kind == .film ? "TVmaze · TheTVDB" : "IGDB")
                .font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFF5E5E66))
                .padding(.bottom, 12)
        }
    }

    private var genrePages: [(String, [DiscoveryFilter])] {
        let genres = catalog.filter(\.genre)
        if kind == .game { return stride(from: 0, to: genres.count, by: 8).map { ("Türler", Array(genres[$0..<min($0 + 8, genres.count)])) } }
        var pages: [(String, [DiscoveryFilter])] = [("Türler", genres)]
        var order: [String] = []
        for f in catalog where f.themeGroup != nil { if !order.contains(f.themeGroup!) { order.append(f.themeGroup!) } }
        pages += order.map { g in (g, catalog.filter { $0.themeGroup == g }) }
        return pages
    }

    private var feelingPages: [[DiscoveryFilter]] {
        let list = catalog.filter { !$0.genre && $0.themeGroup == nil }
        return stride(from: 0, to: list.count, by: kind == .film ? 4 : 6).map { Array(list[$0..<min($0 + (kind == .film ? 4 : 6), list.count)]) }
    }

    private func chips(_ options: [DiscoveryFilter]) -> some View {
        ChipFlow(spacing: 8) {
            ForEach(options) { f in
                let selected = filters.contains(f.key)
                let groupCount = filters.compactMap { k in catalog.first { $0.key == k } }.filter { $0.selectionGroup == f.selectionGroup }.count
                DiscoveryChip(label: f.label, selected: selected, enabled: !loading && (selected || groupCount < DiscoveryFilter.maxPerGroup), accent: accent) {
                    if selected { filters.remove(f.key) } else { filters.insert(f.key) }
                }
            }
        }
    }

    private func fetch() {
        guard !loading else { return }
        loading = true
        error = nil
        Task {
            do {
                try await discovery.fetch(kind, type: kind == .film ? type : "GAME", filters: filters, platforms: kind == .game ? platforms : [])
                withAnimation(.easeOut(duration: 0.3)) { page = .discover }
            } catch let e {
                error = e.localizedDescription
            }
            loading = false
        }
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
                DiscoveryHeadline(text: isList ? (kind == .film ? "İZLENECEK LİSTEM" : "OYNANACAK LİSTEM") : "SANA GÖRE", small: isList)
                    .padding(.top, 2).padding(.bottom, 6)
                if let first = cards.first {
                    Button { reportNotice = true } label: {
                        Text("Öneriyi bildir").font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.7)).padding(6)
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
                        Text(isList ? (kind == .film ? "İzlenecek listen henüz boş." : "Oynanacak listen henüz boş.") : "Bu destenin sonuna geldin.")
                            .font(RealFont.inter(15)).foregroundStyle(.white)
                        if !isList {
                            DiscoveryChip(label: loading ? "Hazırlanıyor…" : "Daha fazla öner", selected: false, enabled: !loading, accent: accent) {
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
            if !cards.isEmpty { SwipeTutorial(kind: kind == .film ? "film" : "game", rightLabel: "Kaydet", leftLabel: "Geç") }
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
            guard !store.reminders.contains(where: { r in r.gameBlocks.contains { $0.providerId == id } }) else { RealToast.shared.show("Bu oyun zaten bir kartta"); return }
            let block = CardGameBlock(providerId: id, title: m.title, year: m.year,
                                      genres: (m.genre ?? "").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty },
                                      platforms: card.platforms, artworkUrl: m.artworkUrl, description: m.description, providerUrl: m.providerUrl)
            _ = store.addReminder(text: m.title) { r in r.gameBlocks = [block] }
            RealToast.shared.show("REAL'e eklendi")
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
        _ = store.addReminder(text: media.title) { r in r.mediaBlocks = [media] }
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
                        .accessibilityLabel("\(titles[i]), \(i + 1). sayfa, \(count) seçili")
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
            Text(loading ? "Hazırlanıyor…" : label).font(RealFont.inter(16, .semibold)).foregroundStyle(.white).lineLimit(1)
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

/// PosterAmbient: the top poster's colour as a blurred light at the top of the screen.
struct PosterAmbient: View {
    let url: String?
    var body: some View {
        ZStack {
            if let url, let u = URL(string: url) {
                AsyncImage(url: u) { img in img.resizable().scaledToFill() } placeholder: { Color.clear }
                    .blur(radius: 48).opacity(0.85)
            }
            LinearGradient(colors: [.clear, .black], startPoint: .top, endPoint: .bottom)
        }
        .frame(height: 210).frame(maxWidth: .infinity).clipped()
        .ignoresSafeArea(edges: .top)
        .animation(.easeInOut(duration: 0.45), value: url)
        .allowsHitTesting(false)
    }
}

/// Poster artwork with the Android fallback (video glyph + title) when there is no image.
struct PosterArtwork: View {
    let media: MediaBlock
    var titleSize: CGFloat = 20
    var body: some View {
        ZStack {
            Color(argb: 0xFF1B1B20)
            VStack(spacing: 14) {
                RealSymbol("film", size: 50.6).foregroundStyle(Color(argb: 0xFFDDD4C6))
                Text(media.title).font(RealFont.inter(titleSize)).foregroundStyle(.white).multilineTextAlignment(.center).lineLimit(4)
            }
            .padding(28)
            if let s = media.artworkUrl, let u = URL(string: s) {
                AsyncImage(url: u) { phase in
                    if let img = phase.image { img.resizable().scaledToFill() }
                }
            }
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
    private let threshold: CGFloat = 85

    var body: some View {
        GeometryReader { geo in
            let width = min(geo.size.width * 0.96, max(1, geo.size.height - 76 - 42) * posterAspect)
            let height = width / posterAspect
            let visible = Array(cards.prefix(4))
            ZStack(alignment: .top) {
                // No blurred copy of the front poster here: its unclipped blur left a rectangle behind the deck, and
                // it went black while the next poster's copy loaded. The screen's PosterAmbient already gives the glow.
                ForEach(Array(visible.enumerated()).reversed(), id: \.element.id) { depth, item in
                    poster(item, depth: depth, width: width, height: height)
                }
                actionBar.offset(y: height + 12)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 34)
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
        PosterArtwork(media: item.media)
            .frame(width: width, height: height)
            .overlay(alignment: .topLeading) { if front { leftLabels(item) } }
            .overlay(alignment: .topTrailing) { if front { rightLabels(item) } }
            .overlay { if front && busy { ProgressView().tint(.white) } }
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .accessibilityElement()
            .accessibilityLabel(item.media.title)
            .accessibilityAddTraits(front ? .isButton : [])
    }

    private func leftLabels(_ item: DiscoveryCard) -> some View {
        let series = item.media.type == .TV
        return VStack(alignment: .leading, spacing: 6) {
            PosterLabel(text: mediaDurationLabel(item.media.durationMillis, series: series) ?? item.media.year.map(String.init) ?? (series ? "Dizi" : "Film"))
            if let g = DiscoveryRanking.displayGenre(item.media.genre) { PosterLabel(text: g) }
        }
        .padding(14)
    }

    private func rightLabels(_ item: DiscoveryCard) -> some View {
        let m = DiscoveryRanking.match(item, filters, kind: kind)
        return VStack(alignment: .trailing, spacing: 6) {
            PosterLabel(text: kind == .game ? "Oyun" : (item.media.type == .TV ? "Dizi" : "Film"))
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
                else if offset.width > threshold { decide(item, later ? .browseList : .addToList, fly: CGSize(width: threshold * 6, height: 0)) }
                else if offset.width < -threshold { decide(item, later ? .removeFromList : .skip, fly: CGSize(width: -threshold * 6, height: 0)) }
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
                    HStack(spacing: 7) {
                        RealSymbol("plus", size: 18.4)
                        Text("REAL'e ekle").font(RealFont.inter(13, .medium)).lineLimit(1)
                    }
                    .foregroundStyle(busy ? Color(argb: 0xFF66666C) : .white)
                    .padding(.horizontal, 16).frame(minHeight: 48)
                    .background(accent.opacity(0.9), in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(busy)
                Button { decide(first, .watched, fly: CGSize(width: 0, height: -threshold * 9)) } label: {
                    HStack(spacing: 7) {
                        RealSymbol("checkmark", size: 17)
                        Text(kind == .game ? "Oynadım" : "İzledim").font(RealFont.inter(13, .medium)).lineLimit(1)
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

    var body: some View {
        GeometryReader { geo in
            let heroWidth = max(1, geo.size.width - 40)
            let finalHeight = min(heroWidth * 0.88, 300)
            let initialHeight = min(heroWidth / posterAspect, max(1, geo.size.height - 88))
            ZStack(alignment: .top) {
                Color.black.opacity(0.94).ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        Color.clear.frame(height: 60)
                        PosterArtwork(media: card.media, titleSize: 18)
                            .frame(width: heroWidth, height: arrived && !leaving ? finalHeight : initialHeight)
                            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            .padding(.horizontal, 20)
                        content.opacity(leaving ? 0 : 1)
                    }
                }
                .scrollIndicators(.hidden)
                header.opacity(leaving ? 0 : 1)
            }
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
            BackCircle(action: close).accessibilityLabel("Afişlere dön")
            Spacer()
            capsule(kind == .game ? "Oyun" : (card.media.type == .TV ? "Dizi" : "Film"))
            capsule(mediaDurationLabel(detail?.durationMillis ?? card.media.durationMillis, series: card.media.type == .TV))
            capsule(nil, rating: true)
        }
        .padding(.horizontal, 20).padding(.vertical, 10)
    }

    private func capsule(_ label: String?, rating: Bool = false) -> some View {
        Text(label ?? " ").font(RealFont.inter(11)).foregroundStyle(.white).lineLimit(1)
            .padding(.horizontal, 10).frame(minWidth: rating ? 54 : 48, minHeight: 30)
            .liquidGlass(Capsule(), role: .chrome)
            .accessibilityLabel(label ?? (rating ? "Puan henüz mevcut değil" : "Süre bilgisi mevcut değil"))
    }

    private var content: some View {
        let description = detail?.description.flatMap { $0.isEmpty ? nil : $0 } ?? card.media.description
        return VStack(alignment: .leading, spacing: 0) {
            Text(card.media.title).font(RealFont.inter(26, .medium)).foregroundStyle(.white).padding(.horizontal, 20).padding(.top, 20)
            Text(description ?? "Bu yapımın konusu henüz bulunamadı.")
                .font(RealFont.inter(14)).lineSpacing(7).foregroundStyle(.white.opacity(0.72))
                .lineLimit(readMore ? nil : 4)
                .mask(LinearGradient(stops: [.init(color: .black, location: 0), .init(color: .black, location: readMore ? 1 : 0.75),
                                             .init(color: .black.opacity(readMore ? 1 : 0), location: 1)], startPoint: .top, endPoint: .bottom))
                .padding(.horizontal, 20).padding(.top, 12)
                .animation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.38), value: readMore)
            if description?.isEmpty == false {
                Button(readMore ? "Daha az" : "Devamını oku") { readMore.toggle() }
                    .font(RealFont.inter(12, .semibold)).foregroundStyle(.white).padding(8).padding(.horizontal, 12)
            }
            Text("Cast").font(RealFont.inter(19, .medium)).foregroundStyle(.white).padding(.leading, 20).padding(.top, 22).padding(.bottom, 12)
            castRow
            Link("\(card.media.provider) · Kaynak", destination: sourceUrl)
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
                Text("Oyuncular yükleniyor…").font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.55))
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        } else if failed || detail?.cast.isEmpty != false {
            Text(failed ? "Oyuncular şu anda alınamadı." : "Oyuncu bilgisi bulunamadı.")
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

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(block.type == .MOVIE ? "Film" : "Dizi").font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFA5A5B0))
                Spacer()
                if editing {
                    Button(action: onRemove) { RealSymbol("xmark", size: 14.9).foregroundStyle(Color(argb: 0xFFB8B8C2)).frame(width: 40, height: 40) }
                        .accessibilityLabel("İçeriği kaldır")
                }
            }
            HStack(alignment: .top, spacing: 12) {
                PosterArtwork(media: block, titleSize: 11).frame(width: 96, height: 150).clipShape(RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 4) {
                    Text(block.title).font(RealFont.inter(18, .medium)).foregroundStyle(Color(argb: 0xFFF1F1F4)).lineLimit(2)
                    Text([block.year.map(String.init), block.type == .MOVIE ? "Film" : "Dizi", block.genre].compactMap { $0 }.joined(separator: " • "))
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
        case "tvmaze": return "TVmaze'de aç ↗"
        case "thetvdb": return "TheTVDB'de aç ↗"
        case "apple": return "Apple TV'de aç ↗"
        default: return "\(block.provider)'de aç ↗"
        }
    }
}
