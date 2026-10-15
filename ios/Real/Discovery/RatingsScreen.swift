import SwiftUI

/// Score colours: bordo (bad) → orange → olive → lime. Background + readable text.
func ratingColors(_ v: Double?) -> (bg: Color, fg: Color) {
    guard let v else { return (Color(argb: 0xFF1E1E1E), Color(argb: 0xFF555555)) }
    switch v {
    case ...5: return (Color(argb: 0xFF5A1A1F), Color(argb: 0xFFF3B4B8))
    case ...6: return (Color(argb: 0xFF8A3A1C), Color(argb: 0xFFFFD2BD))
    case ...7: return (Color(argb: 0xFF9A6A1A), Color(argb: 0xFFFFE6B0))
    case ...8: return (Color(argb: 0xFF5D7A22), Color(argb: 0xFFE6F5C8))
    case ...9: return (Color(argb: 0xFF9CC63A), Color(argb: 0xFF1C2A05))
    default: return (Color(argb: 0xFFD9F27A), Color(argb: 0xFF1C2A05))
    }
}
private let ratingScale: [Color] = [0xFF5A1A1F, 0xFF8A3A1C, 0xFF9A6A1A, 0xFF5D7A22, 0xFF9CC63A, 0xFFD9F27A].map { Color(argb: $0) }
private let faded = Color(argb: 0xFF5C5C5C)
private let screenBg = Color(argb: 0xFF0C0C0C)

func scoreText(_ v: Double) -> String {
    v.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(v)) : String(format: "%.1f", v).replacingOccurrences(of: ".", with: ",")
}
private func round1(_ v: Double) -> Double { (v * 10).rounded() / 10 }
private func verdict(_ v: Double) -> String { v >= 9 ? "Efsane" : v >= 7 ? "İyi" : v >= 5 ? "İdare eder" : "Beğenmedim" }

private struct Period { let short: String; let long: String; let days: Int? }
private let periods = [Period(short: "7", long: "Son 7 gün", days: 7), Period(short: "30", long: "Son 30 gün", days: 30),
                       Period(short: "3 ay", long: "Son 3 ay", days: 90), Period(short: "6 ay", long: "Son 6 ay", days: 180),
                       Period(short: "1 yıl", long: "Son 1 yıl", days: 365), Period(short: "Tümü", long: "Tümü", days: nil)]

private func genresOf(_ e: RatingEntry) -> [String] {
    var seen = Set<String>()
    return (e.media.genre ?? "").split(whereSeparator: { ",/|".contains($0) })
        .compactMap { DiscoveryRanking.displayGenre(String($0)) }.filter { seen.insert($0).inserted }
}

private enum RateTarget: Identifiable {
    case film(MediaBlock, initial: Double?, past: Bool, inList: Bool)
    case episode(MediaBlock, SeriesEpisode, initial: Double?)
    case season(MediaBlock, Int, [SeriesEpisode])
    var id: String {
        switch self {
        case let .film(m, _, _, _): return "f" + ratingKey(m)
        case let .episode(m, e, _): return "e" + ratingKey(m) + e.key
        case let .season(m, s, _): return "s" + ratingKey(m) + "\(s)"
        }
    }
}

/// "Puanlarım": watched films and series in one list, newest first, each against the person's own average.
struct RatingsScreen: View {
    let onClose: () -> Void
    @Environment(DiscoveryStore.self) private var discovery
    @State private var ratings = MediaRatingStore.shared
    @State private var period = periods.count - 1
    @State private var genre: String?
    @State private var seriesKey: String?
    @State private var target: RateTarget?
    @State private var searching = false

    var body: some View {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let days = periods[period].days
        let inPeriod = ratings.entries.filter { e in
            guard let days else { return true }
            guard let at = e.addedAt else { return false }
            return now - at <= Int64(days) * 86_400_000
        }.sorted { ($0.addedAt ?? .min, $0.order) > ($1.addedAt ?? .min, $1.order) }
        var counts: [String: Int] = [:]
        for g in inPeriod.flatMap(genresOf) { counts[g, default: 0] += 1 }
        var genres = counts.sorted { $0.value > $1.value }.map(\.key)
        if let genre, !genres.contains(genre) { genres.append(genre) }
        let shown = genre.map { g in inPeriod.filter { genresOf($0).contains(g) } } ?? inPeriod
        let values = shown.compactMap(\.value)
        let avg = values.isEmpty ? nil : values.reduce(0, +) / Double(values.count)
        return ZStack(alignment: .topLeading) {
            screenBg.ignoresSafeArea()
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    PeriodPicker(selected: $period).padding(.top, 10)
                    Text(avg.map { scoreText(round1($0)) } ?? "–").font(RealFont.inter(56, .medium)).foregroundStyle(.white)
                        .contentTransition(.numericText()).frame(maxWidth: .infinity).padding(.top, 6)
                    let films = shown.filter { !$0.series }.count, dizi = shown.count - films
                    Text(shown.isEmpty ? "Henüz puan yok" : (genre.map { "\($0) ortalaman · " } ?? "ortalaman · ") +
                         [films > 0 ? "\(films) film" : nil, dizi > 0 ? "\(dizi) dizi" : nil].compactMap { $0 }.joined(separator: ", "))
                        .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF888888)).frame(maxWidth: .infinity)
                        .padding(.top, 4).padding(.bottom, 14)
                    if genres.count > 1 || genre != nil { GenreRow(genres: genres, selected: $genre).padding(.bottom, 16) }
                    if !shown.isEmpty {
                        HStack { ForEach(["altında", "ortalama", "üstünde"], id: \.self) { Text($0); if $0 != "üstünde" { Spacer() } } }
                            .font(RealFont.inter(10)).foregroundStyle(Color(argb: 0xFF555555)).padding(.leading, 46).padding(.bottom, 2)
                    } else {
                        Text(days == nil ? "Keşifte İzledim dediğin film ve diziler burada puanlanır. Eskiden izlediklerini aşağıdan ekleyebilirsin."
                             : "Bu aralıkta eklenen yok.")
                            .font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFF777777)).padding(.vertical, 20)
                    }
                    ForEach(shown) { e in
                        RatingRow(entry: e, avg: avg, now: now) {
                            if e.series { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.38)) { seriesKey = e.key } }
                            else { target = .film(e.media, initial: e.score, past: false, inList: true) }
                        }
                    }
                    if let line = tasteLine(inPeriod) {
                        Text(line).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A8A)).padding(.top, 18)
                    }
                    Button { searching = true } label: {
                        HStack(spacing: 8) { RealSymbol("plus", size: 15); Text("Geçmişten ekle").font(RealFont.inter(14)) }
                            .foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 48)
                            .overlay(RoundedRectangle(cornerRadius: 22).stroke(Color(argb: 0xFF2E2E2E), lineWidth: 1))
                    }
                    .buttonStyle(.plain).padding(.top, 14).padding(.bottom, 40)
                }
                .padding(.horizontal, 20)
                .animation(.easeOut(duration: 0.25), value: genre)
                .animation(.easeOut(duration: 0.25), value: period)
            }
            .scrollIndicators(.hidden)
            BackCircle(action: onClose).padding(.leading, 20).padding(.top, 10)
            if let s = ratings.entries.first(where: { $0.key == seriesKey }) {
                SeriesGrid(entry: s, onBack: { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.32)) { seriesKey = nil } },
                           onRate: { target = $0 })
                    .transition(.move(edge: .trailing).combined(with: .opacity)).zIndex(2)
            }
        }
        .onAppear { ratings.importWatched(discovery.film.watched) }
        .sheet(isPresented: $searching) {
            MediaSearchSheet { media in
                if media.type == .TV { ratings.add(media, past: true); seriesKey = ratingKey(media) }
                else {
                    let existing = ratings.entries.first { $0.key == ratingKey(media) }?.score
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { target = .film(media, initial: existing, past: true, inList: false) }
                }
            }
        }
        .sheet(item: $target) { t in sheet(for: t) }
    }

    @ViewBuilder private func sheet(for t: RateTarget) -> some View {
        switch t {
        case let .film(media, initial, past, inList):
            FilmRatingSheet(media: media, meta: [media.year.map(String.init), "Film"].compactMap { $0 }.joined(separator: " · "), initial: initial,
                            onRemove: inList ? { ratings.remove(ratingKey(media)) } : nil, removeLabel: "Listeden çıkar") { ratings.rate(media, $0, past: past) }
        case let .episode(media, ep, initial):
            EpisodeRatingSheet(media: media, episode: ep, initial: initial,
                               onRemove: initial != nil ? { ratings.rateEpisodes(media, [ep.key: nil]) } : nil) { ratings.rateEpisodes(media, [ep.key: $0]) }
        case let .season(media, season, eps):
            RatingSheet(title: "\(season). sezonun tamamı", meta: media.title,
                        reminder: "Bütün bölümlere aynı puan verilir. Sonra istediğin bölümü tek tek değiştirebilirsin.", initial: nil) { v in
                ratings.rateEpisodes(media, Dictionary(uniqueKeysWithValues: eps.map { ($0.key, Optional(v)) }))
            }
        }
    }

    /// "En sevdiğin tür …": genres with at least two scores; nil until there are two such genres.
    private func tasteLine(_ list: [RatingEntry]) -> String? {
        var byGenre: [String: [Double]] = [:]
        for e in list { if let v = e.value { for g in genresOf(e) { byGenre[g, default: []].append(v) } } }
        let avgs = byGenre.filter { $0.value.count >= 2 }.mapValues { $0.reduce(0, +) / Double($0.count) }
        guard avgs.count >= 2, let best = avgs.max(by: { $0.value < $1.value }), let worst = avgs.min(by: { $0.value < $1.value }) else { return nil }
        return "En sevdiğin tür \(best.key) (\(scoreText(round1(best.value)))), en az sevdiğin \(worst.key) (\(scoreText(round1(worst.value))))."
    }
}

/// Faded options right-aligned on the top line; the chosen one slides to the centre as a title.
private struct PeriodPicker: View {
    @Binding var selected: Int
    private func width(_ s: String, _ size: CGFloat) -> CGFloat {
        (s as NSString).size(withAttributes: [.font: UIFont(name: "Inter-Regular", size: size) ?? .systemFont(ofSize: size)]).width
    }
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let rest = periods.indices.filter { $0 != selected }
            let xs: [Int: CGFloat] = {
                var x = w; var map: [Int: CGFloat] = [:]
                for i in rest.reversed() { x -= width(periods[i].short, 12); map[i] = x; x -= 14 }
                map[selected] = (w - width(periods[selected].long, 16)) / 2
                return map
            }()
            ZStack(alignment: .topLeading) {
                ForEach(periods.indices, id: \.self) { i in
                    let on = i == selected
                    Text(on ? periods[i].long : periods[i].short)
                        .font(RealFont.inter(on ? 16 : 12)).foregroundStyle(on ? .white : faded).fixedSize()
                        .offset(x: xs[i] ?? 0, y: on ? 26 : 0)
                        .onTapGesture { if !on { withAnimation(.spring(response: 0.45, dampingFraction: 0.78)) { selected = i } } }
                }
            }
        }
        .frame(height: 50)
    }
}

/// Genre filter: "Hepsi" sits at the left edge with nothing to its right; the genres wait off screen to its left (most
/// common nearest). Pulling the row right brings them in from the left; whichever word reaches the left edge is chosen
/// while scrolling, and the row snaps to it.
private struct GenreRow: View {
    let genres: [String]
    @Binding var selected: String?
    @State private var position: String?
    private static let all = "*hepsi*"
    var body: some View {
        GeometryReader { geo in
            ScrollView(.horizontal) {
                HStack(spacing: 16) {
                    ForEach(genres.reversed() + [Self.all], id: \.self) { g in
                        let on = (position ?? Self.all) == g
                        Text(g == Self.all ? "Hepsi" : g).font(RealFont.inter(on ? 14 : 12)).foregroundStyle(on ? .white : faded)
                            .padding(.vertical, 6).contentShape(Rectangle())
                            .onTapGesture { withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { position = g } }
                            .id(g)
                    }
                }
                .scrollTargetLayout()
            }
            .contentMargins(.trailing, geo.size.width, for: .scrollContent)
            .scrollTargetBehavior(.viewAligned)
            .scrollPosition(id: $position)
            .scrollIndicators(.hidden)
        }
        .frame(height: 30)
        .onAppear { position = selected ?? Self.all }
        .onChange(of: genres) { _, _ in position = selected ?? Self.all }
        .onChange(of: position) { _, p in
            let next = p == nil || p == Self.all ? nil : p
            if next != selected { withAnimation(.easeOut(duration: 0.2)) { selected = next } }
        }
    }
}

private struct RatingRow: View {
    let entry: RatingEntry
    let avg: Double?
    let now: Int64
    let onTap: () -> Void
    var body: some View {
        let v = entry.value
        let tone = ratingColors(v).bg
        let diff = v.flatMap { v in avg.map { v - $0 } }
        Button(action: onTap) {
            HStack(spacing: 10) {
                PosterArtwork(media: entry.media, titleSize: 6).frame(width: 36, height: 52).clipShape(RoundedRectangle(cornerRadius: 8))
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 6) {
                        Text(entry.media.title).font(RealFont.inter(14)).foregroundStyle(.white).lineLimit(1)
                        if entry.series {
                            Text("dizi").font(RealFont.inter(10)).foregroundStyle(Color(argb: 0xFF9A9A9A)).padding(.horizontal, 5).padding(.vertical, 1)
                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color(argb: 0xFF333333), lineWidth: 1))
                        }
                        Spacer(minLength: 4)
                        Text(v.map(scoreText) ?? (entry.series ? "Bölümleri puanla" : "Puan ver"))
                            .font(RealFont.inter(v != nil ? 15 : 12, .medium)).foregroundStyle(v != nil ? tone : Color(argb: 0xFF9A9A9A))
                    }
                    GeometryReader { geo in
                        let half = geo.size.width / 2
                        ZStack(alignment: .leading) {
                            Capsule().fill(Color(argb: 0xFF161616))
                            if let diff {
                                let w = half * min(abs(diff) / 4.6, 1)
                                Capsule().fill(tone).frame(width: w).offset(x: diff >= 0 ? half : half - w)
                            }
                            Rectangle().fill(Color(argb: 0xFF444444)).frame(width: 1).offset(x: half)
                        }
                    }
                    .frame(height: 6).padding(.top, 7)
                    let when: String = entry.addedAt.map { at in
                        let d = Int((now - at) / 86_400_000)
                        return d == 0 ? "bugün" : d == 1 ? "dün" : "\(d) gün önce"
                    } ?? "eskiden"
                    Text([diff.map { ($0 >= 0 ? "+" : "−") + scoreText(round1(abs($0))) }, when].compactMap { $0 }.joined(separator: " · "))
                        .font(RealFont.inter(10)).foregroundStyle(Color(argb: 0xFF666666)).padding(.top, 3)
                }
            }
            .padding(.vertical, 7).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Series page: big poster, the poster's light behind, then seasons × episodes. Tap an episode to rate it, a season
/// label to rate all of it.
private struct SeriesGrid: View {
    let entry: RatingEntry
    let onBack: () -> Void
    let onRate: (RateTarget) -> Void
    @State private var episodes: [SeriesEpisode]?
    @State private var error: String?

    var body: some View {
        ZStack(alignment: .topLeading) {
            screenBg.ignoresSafeArea()
            if let u = entry.media.artworkUrl.flatMap(URL.init(string:)) {
                AsyncImage(url: u) { p in if let i = p.image { i.resizable().scaledToFill() } }
                    .frame(maxWidth: .infinity).frame(height: 420).clipped().blur(radius: 60).opacity(0.45)
                    .overlay(LinearGradient(colors: [.clear, screenBg], startPoint: .top, endPoint: .bottom))
                    .ignoresSafeArea()
            }
            ScrollView {
                VStack(spacing: 0) {
                    PosterArtwork(media: entry.media, titleSize: 12).frame(width: 132, height: 192)
                        .clipShape(RoundedRectangle(cornerRadius: 14)).padding(.top, 58)
                    Text(entry.media.title).font(RealFont.inter(24)).foregroundStyle(.white).multilineTextAlignment(.center).lineLimit(2).padding(.top, 16)
                    Text([entry.media.year.map(String.init), episodes.map { "\(Set($0.map(\.season)).count) sezon · \($0.count) bölüm" } ?? "Bölümler yükleniyor…"]
                        .compactMap { $0 }.joined(separator: " · "))
                        .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF999999)).padding(.top, 4)
                    HStack(alignment: .lastTextBaseline, spacing: 0) {
                        Text(entry.value.map(scoreText) ?? "–").font(RealFont.inter(44, .medium))
                            .foregroundStyle(entry.value != nil ? ratingColors(entry.value).bg : Color(argb: 0xFF555555))
                        Text(entry.value != nil ? "  bölüm ortalaman" : "  henüz bölüm puanı yok").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF888888))
                    }
                    .padding(.top, 14).padding(.bottom, 22)
                    if let error { Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFB4AB)).padding(.bottom, 12) }
                    let bySeason = Dictionary(grouping: episodes ?? [], by: \.season).sorted { $0.key < $1.key }
                    ForEach(bySeason, id: \.key) { pair in
                        let season = pair.key, eps = pair.value
                        let scores = eps.compactMap { entry.episodes[$0.key] }
                        let sAvg = scores.isEmpty ? nil : scores.reduce(0, +) / Double(scores.count)
                        HStack(alignment: .top, spacing: 0) {
                            Text("S\(season)").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF888888))
                                .frame(width: 30, height: 30, alignment: .leading).contentShape(Rectangle())
                                .onTapGesture { onRate(.season(entry.media, season, eps)) }
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 30, maximum: 30), spacing: 5)], alignment: .leading, spacing: 5) {
                                ForEach(eps) { ep in
                                    let score = entry.episodes[ep.key]
                                    let c = ratingColors(score)
                                    Text(score.map { String(Int($0)) } ?? "·").font(RealFont.inter(11, .medium)).foregroundStyle(c.fg)
                                        .frame(width: 30, height: 30).background(RoundedRectangle(cornerRadius: 8).fill(c.bg))
                                        .onTapGesture { onRate(.episode(entry.media, ep, initial: score)) }
                                }
                            }
                            Text(sAvg.map { scoreText(round1($0)) } ?? "–").font(RealFont.inter(13, .medium))
                                .foregroundStyle(sAvg != nil ? ratingColors(sAvg).bg : Color(argb: 0xFF555555))
                                .frame(width: 38, height: 30, alignment: .trailing)
                        }
                        .padding(.bottom, 8)
                    }
                    if !(episodes ?? []).isEmpty {
                        HStack(spacing: 8) {
                            Text("Kötü"); Capsule().fill(LinearGradient(colors: ratingScale, startPoint: .leading, endPoint: .trailing)).frame(height: 6); Text("Efsane")
                        }
                        .font(RealFont.inter(10)).foregroundStyle(Color(argb: 0xFF777777)).padding(.top, 10)
                        Color.clear.frame(height: 40)
                    }
                }
                .padding(.horizontal, 20)
            }
            .scrollIndicators(.hidden)
            BackCircle(action: onBack).padding(.leading, 20).padding(.top, 10)
        }
        .task(id: entry.key) {
            do { episodes = try await MediaRatingStore.shared.episodes(entry.media) } catch { self.error = error.localizedDescription; episodes = [] }
        }
    }
}

// MARK: Rating sheets

/// Film: the Turkish reminder (TheTVDB, else AI once); a tap on the placeholder shows the original text right away.
struct FilmRatingSheet: View {
    let media: MediaBlock
    let meta: String
    let initial: Double?
    var onRemove: (() -> Void)?
    var removeLabel = "Puanı kaldır"
    let onSave: (Double) -> Void
    @State private var text: String?
    @State private var loading = true
    var body: some View {
        RatingSheet(title: media.title, meta: meta, reminder: text, initial: initial, onRemove: onRemove, removeLabel: removeLabel,
                    reminderLoading: loading, onSkipLoading: { if loading { text = media.description; loading = false } }, onSave: onSave)
            .task(id: media.id) {
                let result = await withTimeout(8) { await MediaRatingStore.shared.filmSummary(media) } ?? media.description
                if loading { text = result; loading = false }
            }
    }
}

private struct EpisodeRatingSheet: View {
    let media: MediaBlock
    let episode: SeriesEpisode
    let initial: Double?
    var onRemove: (() -> Void)?
    let onSave: (Double) -> Void
    @State private var shown: SeriesEpisode?
    @State private var loading = true
    var body: some View {
        let ep = shown ?? episode
        RatingSheet(title: ep.name, meta: "S\(episode.season) · B\(episode.number) · \(media.title)", reminder: ep.summary, initial: initial,
                    onRemove: onRemove, reminderLoading: loading, titleLoading: loading,
                    onSkipLoading: { loading = false }, onSave: onSave)
            .task(id: episode.key) {
                guard !episode.translated else { loading = false; return }
                let result = await withTimeout(8) { await MediaRatingStore.shared.translate(media, episode) } ?? nil
                if loading { shown = result; loading = false }
            }
    }
}

/// Runs `work`, nil after `seconds`.
func withTimeout<T: Sendable>(_ seconds: Double, _ work: @escaping @Sendable () async -> T) async -> T? {
    await withTaskGroup(of: T?.self) { group in
        group.addTask { await work() }
        group.addTask { try? await Task.sleep(for: .seconds(seconds)); return nil }
        let first = await group.next() ?? nil
        group.cancelAll()
        return first
    }
}

/// Rating sheet: big number, verdict, a short reminder and 20 bars (0,5 steps) to drag or tap.
struct RatingSheet: View {
    let title: String
    let meta: String
    let reminder: String?
    let initial: Double?
    var onRemove: (() -> Void)?
    var removeLabel = "Puanı kaldır"
    var reminderLoading = false
    var titleLoading = false
    var onSkipLoading: (() -> Void)?
    let onSave: (Double) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var value: Double = 7
    @State private var expanded = false
    @State private var overflows = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(meta).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF888888)).lineLimit(1)
            if titleLoading { SkeletonLine(fraction: 0.55, height: 18).padding(.vertical, 6).onTapGesture { onSkipLoading?() } }
            else { Text(title).font(RealFont.inter(22)).foregroundStyle(.white).lineLimit(2).padding(.top, 2) }
            if reminderLoading {
                VStack(alignment: .leading, spacing: 7) {
                    SkeletonLine(fraction: 1, height: 11); SkeletonLine(fraction: 0.92, height: 11); SkeletonLine(fraction: 0.6, height: 11)
                }
                .padding(.top, 10).contentShape(Rectangle()).onTapGesture { onSkipLoading?() }
            } else if let reminder, !reminder.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    if expanded {
                        ScrollView { Text(reminder).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFB0B0B0)).frame(maxWidth: .infinity, alignment: .leading) }
                            .frame(maxHeight: 220)
                    } else {
                        Text(reminder).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF9A9A9A)).lineLimit(3)
                            .background(GeometryReader { g in
                                Text(reminder).font(RealFont.inter(13)).fixedSize(horizontal: false, vertical: true).frame(width: g.size.width)
                                    .hidden().background(GeometryReader { full in Color.clear.onAppear { overflows = full.size.height > g.size.height + 1 } })
                            })
                    }
                    if overflows || expanded {
                        Text(expanded ? "Daha az" : "Devamı").font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFF6A6A6A))
                    }
                }
                .padding(.top, 8).contentShape(Rectangle())
                .onTapGesture { if overflows || expanded { withAnimation(.easeInOut(duration: 0.32)) { expanded.toggle() } } }
            }
            HStack(alignment: .lastTextBaseline) {
                Text(scoreText(value)).font(RealFont.inter(64, .medium)).foregroundStyle(.white).contentTransition(.numericText())
                Text(" / 10").font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF888888))
                Spacer()
                Text(verdict(value)).font(RealFont.inter(15)).foregroundStyle(value <= 7 ? Color(argb: 0xFFBBBBBB) : ratingColors(value).bg)
            }
            .padding(.top, 22)
            GeometryReader { geo in
                HStack(alignment: .bottom, spacing: 4) {
                    ForEach(1...20, id: \.self) { i in
                        RoundedRectangle(cornerRadius: 2).fill(Double(i) / 2 <= value ? ratingColors(value).bg : Color(argb: 0xFF2A2A2A))
                            .frame(height: geo.size.height * (0.25 + Double(i) * 0.0375))
                    }
                }
                .frame(maxHeight: .infinity, alignment: .bottom)
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0).onChanged { g in
                    let next = min(max(Double(ceil(max(g.location.x, 1) / geo.size.width * 20)) / 2, 0.5), 10)
                    if next != value { value = next; UISelectionFeedbackGenerator().selectionChanged() }
                })
            }
            .frame(height: 72).padding(.top, 16)
            Text("Sürükle veya bas").font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFF666666)).padding(.top, 8)
            HStack(spacing: 10) {
                Button { onRemove?(); dismiss() } label: {
                    Text(onRemove != nil ? removeLabel : "Vazgeç").font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFBBBBBB))
                        .frame(maxWidth: .infinity, minHeight: 48).overlay(Capsule().stroke(Color(argb: 0xFF3A3A3A), lineWidth: 1))
                }
                .buttonStyle(.plain)
                Button { onSave(value); dismiss() } label: {
                    Text("Kaydet").font(RealFont.inter(15, .medium)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity, minHeight: 48).background(Capsule().fill(.white))
                }
                .buttonStyle(.plain).layoutPriority(1)
            }
            .padding(.top, 22)
        }
        .padding(.horizontal, 24).padding(.top, 24).padding(.bottom, 16)
        .frame(maxHeight: .infinity, alignment: .top)
        .presentationDetents([.large])
        .presentationBackground(Color(argb: 0xFF0B0B0D))
        .presentationDragIndicator(.visible)
        .onAppear { value = initial ?? 7 }
    }
}

/// Quiet placeholder line that breathes while a translation loads.
private struct SkeletonLine: View {
    let fraction: CGFloat
    let height: CGFloat
    @State private var on = false
    var body: some View {
        GeometryReader { g in
            RoundedRectangle(cornerRadius: 4).fill(.white.opacity(on ? 0.12 : 0.05)).frame(width: g.size.width * fraction)
        }
        .frame(height: height)
        .onAppear { withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) { on = true } }
    }
}
