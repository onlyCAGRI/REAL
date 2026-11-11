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

func scoreText(_ v: Double) -> String {
    let s = v.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(v)) : String(format: "%.1f", v)
    return I18n.isEnglish ? s : s.replacingOccurrences(of: ".", with: ",")
}
private func round1(_ v: Double) -> Double { (v * 10).rounded() / 10 }
private func f1(_ v: Double) -> String { scoreText(round1(v)) }
private func verdict(_ v: Double) -> String { v >= 9 ? t("Efsane") : v >= 7 ? t("İyi") : v >= 5 ? t("İdare eder") : t("Beğenmedim") }
private let rateCard = Color.white.opacity(0.05)
private func mean(_ values: [Double]) -> Double? { values.isEmpty ? nil : values.reduce(0, +) / Double(values.count) }

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

/// Puanla (RatingsScreen.kt + RatingsParts.kt): the glow hero with the average, "N tanesi puan bekliyor", category
/// chips, Zevkin and Zirve, then every title grouped by month against the person's own average.
struct RatingsScreen: View {
    let onClose: () -> Void
    @Environment(DiscoveryStore.self) private var discovery
    @State private var ratings = MediaRatingStore.shared
    @State private var genre: String?
    @State private var seriesKey: String?
    @State private var target: RateTarget?
    @State private var searching = false
    @State private var quick = false

    var body: some View {
        let all = ratings.entries.sorted { ($0.addedAt ?? .min, $0.order) > ($1.addedAt ?? .min, $1.order) }
        var counts: [String: Int] = [:]
        for g in all.flatMap(genresOf) { counts[g, default: 0] += 1 }
        var genres = counts.sorted { $0.value != $1.value ? $0.value > $1.value : $0.key < $1.key }.map(\.key)
        if let genre, !genres.contains(genre) { genres.append(genre) }
        let shown = genre.map { g in all.filter { genresOf($0).contains(g) } } ?? all
        let rated = shown.filter { $0.value != nil }
        let avg = mean(rated.compactMap(\.value))
        let waiting = all.filter { $0.value == nil && !$0.series }
        let months = Self.months(shown)
        return ZStack(alignment: .topLeading) {
            Color.black.ignoresSafeArea()
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    YasamDiscoveryHero(title: t("Puanla"), tone: YasamPart.rate.glow, caption: t("{0} puan", rated.count), onBack: onClose) {
                        if let avg {
                            VStack(alignment: .trailing, spacing: 0) {
                                Text(f1(avg)).font(RealFont.inter(40, .heavy)).foregroundStyle(.white).contentTransition(.numericText())
                                Text(t("ortalaman")).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.7))
                            }
                            .animation(.easeOut(duration: 0.5), value: avg)
                        }
                    }
                    VStack(alignment: .leading, spacing: 14) {
                        if !waiting.isEmpty { RatingsWaitingCard(waiting: waiting) { quick = true } }
                        // Category only: Hepsi + the genres of the list, most common first.
                        if !genres.isEmpty {
                            ScrollView(.horizontal) {
                                HStack(spacing: 6) {
                                    RateChip(label: t("Hepsi"), on: genre == nil) { genre = nil }
                                    ForEach(genres, id: \.self) { g in RateChip(label: g, on: genre == g) { genre = genre == g ? nil : g } }
                                }
                            }
                            .scrollIndicators(.hidden)
                        }
                        if rated.count >= 3 {
                            RatingsTasteCard(rated: rated)
                            RatingsTopThree(top: Array(rated.sorted { ($0.value ?? 0) > ($1.value ?? 0) }.prefix(3)), onOpen: open)
                        }
                    }
                    .padding(.horizontal, 16).padding(.top, 16)
                    if shown.isEmpty {
                        Text(t("Keşifte İzledim dediğin film ve diziler burada puanlanır. İzlediklerini aşağıdan da ekleyebilirsin."))
                            .font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFF777777)).lineSpacing(4)
                            .padding(.horizontal, 20).padding(.vertical, 20)
                    }
                    ForEach(months, id: \.key) { month in
                        YasamCaps(text: month.label).padding(.leading, 20).padding(.top, 22).padding(.bottom, 8)
                        ForEach(month.list) { e in
                            RatingCardRow(entry: e, avg: avg) { open(e) }.padding(.horizontal, 16).padding(.vertical, 4)
                        }
                    }
                    Button { searching = true } label: {
                        HStack(spacing: 8) {
                            RealSymbol("plus", size: 16)
                            Text(t("Film ya da dizi ekle")).font(RealFont.inter(14))
                        }
                        .foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 48)
                        .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(Color(argb: 0xFF2E2E2E), lineWidth: 1))
                        .contentShape(RoundedRectangle(cornerRadius: 22))
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 16).padding(.top, 18).padding(.bottom, 40)
                }
                .animation(.easeOut(duration: 0.25), value: genre)
            }
            .scrollIndicators(.hidden)
            if let s = ratings.entries.first(where: { $0.key == seriesKey }) {
                SeriesPage(entry: s, onBack: { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.32)) { seriesKey = nil } },
                           onRate: { target = $0 },
                           onRemove: { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.32)) { seriesKey = nil }; ratings.remove(s.key) })
                    .transition(.move(edge: .trailing).combined(with: .opacity)).zIndex(2)
            }
        }
        .onAppear { ratings.importWatched(discovery.film.watched) }
        .sheet(isPresented: $searching) {
            RatingsAddSheet { picked in
                // Everything picked joins the list unscored; films then go straight to quick rating, one series alone
                // opens its episodes.
                picked.forEach { ratings.add($0, past: false) }
                if picked.contains(where: { $0.type != .TV }) {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { quick = true }
                } else if picked.count == 1 {
                    withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.38)) { seriesKey = ratingKey(picked[0]) }
                }
            }
        }
        .fullScreenCover(item: $target) { rater(for: $0) }
        .fullScreenCover(isPresented: $quick) {
            QuickRateScreen(waiting: ratings.entries.filter { $0.value == nil && !$0.series }.sorted { ($0.addedAt ?? .min, $0.order) > ($1.addedAt ?? .min, $1.order) },
                            avg: mean(ratings.entries.compactMap(\.value))) { quick = false }
        }
    }

    private func open(_ e: RatingEntry) {
        if e.series { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.38)) { seriesKey = e.key } }
        else { target = .film(e.media, initial: e.score, past: false, inList: true) }
    }

    private struct Month { let key: String; let label: String; let list: [RatingEntry] }

    private static func months(_ list: [RatingEntry]) -> [Month] {
        let cal = Calendar.real
        let f = DateFormatter()
        f.locale = I18n.locale
        f.dateFormat = "LLLL yyyy"
        var out: [Month] = []
        for e in list {
            let key: String
            let label: String
            if let at = e.addedAt {
                let c = cal.dateComponents([.year, .month], from: at.date)
                key = "\(c.year ?? 0)-\(c.month ?? 0)"
                label = f.string(from: at.date).uppercased(with: I18n.locale)
            } else {
                key = "old"
                label = t("Eskiden").uppercased(with: I18n.locale)
            }
            if let last = out.last, last.key == key { out[out.count - 1] = Month(key: key, label: label, list: last.list + [e]) }
            else { out.append(Month(key: key, label: label, list: [e])) }
        }
        return out
    }

    @ViewBuilder private func rater(for target: RateTarget) -> some View {
        let close = { self.target = nil }
        switch target {
        case let .film(media, initial, past, inList):
            RateFullScreen(media: media, meta: [media.year.map(String.init), t("Film")].compactMap { $0 }.joined(separator: " · "),
                           initial: initial, avg: mean(ratings.entries.compactMap(\.value)), progress: nil,
                           skipLabel: inList ? t("Listeden çıkar") : t("Vazgeç"),
                           onSkip: { close(); if inList { ratings.remove(ratingKey(media)) } },
                           onSave: { v in close(); ratings.rate(media, v, past: past) }, onClose: close, loadSummary: true)
        case let .episode(media, ep, initial):
            // Episodes and whole seasons use the same full-screen rater as films (the series poster on top).
            RateFullScreen(media: media, meta: "S\(ep.season) · B\(ep.number)" + (ep.name.isEmpty ? "" : " · \(ep.name)"),
                           initial: initial, avg: nil, progress: media.title,
                           skipLabel: initial != nil ? t("Puanı kaldır") : t("Vazgeç"),
                           onSkip: { close(); if initial != nil { ratings.rateEpisodes(media, [ep.key: nil]) } },
                           onSave: { v in close(); ratings.rateEpisodes(media, [ep.key: v]) }, onClose: close, summary: ep.summary)
        case let .season(media, season, eps):
            RateFullScreen(media: media, meta: t("{0}. sezonun tamamı · bütün bölümlere aynı puan", season),
                           initial: nil, avg: nil, progress: media.title, skipLabel: t("Vazgeç"),
                           onSkip: close,
                           onSave: { v in close(); ratings.rateEpisodes(media, Dictionary(uniqueKeysWithValues: eps.map { ($0.key, Optional(v)) })) },
                           onClose: close)
        }
    }
}

// MARK: Parts

struct RateChip: View {
    let label: String
    let on: Bool
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(RealFont.inter(13, on ? .semibold : .regular)).foregroundStyle(on ? .black : .white).lineLimit(1).fixedSize()
                .padding(.horizontal, 14).frame(height: 34)
                .background(Capsule().fill(on ? Color.white : .clear))
                .overlay(Capsule().strokeBorder(on ? .clear : .white.opacity(0.18), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// "N tanesi puan bekliyor": watched films with no score yet, three covers fanned out.
private struct RatingsWaitingCard: View {
    let waiting: [RatingEntry]
    var action: () -> Void
    var body: some View {
        let tone = YasamPart.rate.glow
        let n = min(waiting.count, 3)
        Button(action: action) {
            HStack(spacing: 12) {
                ZStack(alignment: .leading) {
                    ForEach(Array(waiting.prefix(3).enumerated()), id: \.offset) { i, e in
                        PosterArtwork(media: e.media, titleSize: 6).frame(width: 40, height: 56)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(Color(argb: 0xFF0D0D0D), lineWidth: 2))
                            .offset(x: CGFloat(26 * i))
                    }
                }
                .frame(width: CGFloat(40 + 26 * (n - 1)), height: 56, alignment: .leading)
                VStack(alignment: .leading, spacing: 2) {
                    Text(t("{0} tanesi puan bekliyor", waiting.count)).font(RealFont.inter(17, .semibold)).foregroundStyle(.white)
                    Text(t("İzledim dediklerin · hızlıca puanla")).font(RealFont.inter(13)).foregroundStyle(YasamTone.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text("›").font(RealFont.inter(24, .bold)).foregroundStyle(.black).frame(width: 40, height: 40).background(Circle().fill(.white))
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(rateCard))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(tone.light.opacity(0.35), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// "ZEVKİN": how the scores spread over 1–10, the best genre and where the person is most generous.
private struct RatingsTasteCard: View {
    let rated: [RatingEntry]
    var body: some View {
        let values = rated.compactMap(\.value)
        let avg = mean(values) ?? 0
        let counts = (1...10).map { n in values.filter { min(max(Int($0.rounded()), 1), 10) == n }.count }
        let maxC = max(counts.max() ?? 1, 1)
        let mine = min(max(Int(avg.rounded()), 1), 10)
        var byGenre: [String: [Double]] = [:]
        for e in rated { if let v = e.value { for g in genresOf(e) { byGenre[g, default: []].append(v) } } }
        let best = byGenre.filter { $0.value.count >= 2 }.mapValues { $0.reduce(0, +) / Double($0.count) }.max { $0.value < $1.value }
        let films = rated.filter { !$0.series }.compactMap(\.value)
        let series = rated.filter(\.series).compactMap(\.value)
        let generous: String? = {
            guard let fa = mean(films), let sa = mean(series) else { return nil }
            let d = sa - fa
            return d >= 0 ? t("Diziler") + " · +" + f1(d) : t("Filmler") + " · +" + f1(-d)
        }()
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .lastTextBaseline) {
                YasamCaps(text: t("ZEVKİN"))
                Spacer()
                Text(t("puan dağılımı")).font(RealFont.inter(12)).foregroundStyle(YasamTone.muted)
            }
            HStack(alignment: .bottom, spacing: 5) {
                ForEach(0..<10, id: \.self) { i in
                    RoundedRectangle(cornerRadius: 6, style: .continuous).fill(ratingColors(Double(i + 1)).bg)
                        .frame(height: 4 + 88 * CGFloat(counts[i]) / CGFloat(maxC)).frame(maxWidth: .infinity)
                }
            }
            .frame(height: 96, alignment: .bottom).padding(.top, 12)
            HStack(spacing: 5) {
                ForEach(1...10, id: \.self) { n in
                    Text("\(n)").font(RealFont.inter(11, n == mine ? .bold : .regular)).foregroundStyle(n == mine ? .white : YasamTone.muted)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.top, 6)
            if best != nil || generous != nil {
                HStack(spacing: 8) {
                    if let best { fact(t("En sevdiğin tür"), "\(best.key) · \(f1(best.value))") }
                    if let generous { fact(t("En cömert olduğun"), generous) }
                }
                .padding(.top, 14)
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(rateCard))
    }

    private func fact(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(RealFont.inter(11)).foregroundStyle(YasamTone.muted)
            Text(value).font(RealFont.inter(15, .semibold)).foregroundStyle(.white).lineLimit(1)
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(rateCard))
    }
}

/// "ZİRVE": the three highest scores, the first one in the middle and taller.
private struct RatingsTopThree: View {
    let top: [RatingEntry]
    var onOpen: (RatingEntry) -> Void
    var body: some View {
        let order = top.count == 3 ? [top[1], top[0], top[2]] : top
        VStack(alignment: .leading, spacing: 0) {
            YasamCaps(text: t("ZİRVE")).padding(.leading, 4).padding(.bottom, 8)
            HStack(alignment: .bottom, spacing: 10) {
                ForEach(order) { e in
                    let first = e.key == top.first?.key
                    let c = ratingColors(e.value)
                    Button { onOpen(e) } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            Color.clear.frame(height: first ? 180 : 150)
                                .overlay { PosterArtwork(media: e.media, titleSize: 8) }
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay(alignment: .bottomLeading) {
                                    Text(e.value.map(scoreText) ?? "").font(RealFont.inter(13, .heavy)).foregroundStyle(c.fg)
                                        .padding(.horizontal, 8).padding(.vertical, 3)
                                        .background(RoundedRectangle(cornerRadius: 10).fill(c.bg)).padding(8)
                                }
                            Text(e.media.title).font(RealFont.inter(12, .semibold)).foregroundStyle(.white).lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }
}

/// One title in the list: poster, title and when, how it stands against the person's average, and the score.
private struct RatingCardRow: View {
    let entry: RatingEntry
    let avg: Double?
    var action: () -> Void
    var body: some View {
        let v = entry.value
        let c = ratingColors(v)
        let when: String = entry.addedAt.map { at in
            let f = DateFormatter()
            f.locale = I18n.locale
            f.dateFormat = "d MMMM"
            return f.string(from: at.date)
        } ?? t("eskiden")
        Button(action: action) {
            HStack(spacing: 0) {
                PosterArtwork(media: entry.media, titleSize: 6).frame(width: 44, height: 62).clipShape(RoundedRectangle(cornerRadius: 10))
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.media.title).font(RealFont.inter(15, .semibold)).foregroundStyle(.white).lineLimit(1)
                    Text((entry.series ? t("Dizi") : t("Film")) + " · " + when).font(RealFont.inter(12)).foregroundStyle(YasamTone.muted)
                    if let v, let avg {
                        let diff = round1(v - avg)
                        Text((diff >= 0 ? "+" : "−") + scoreText(abs(diff)) + " " + t("ortalamana göre")).font(RealFont.inter(12))
                            .foregroundStyle(diff >= 0 ? Color(argb: 0xFFC9E27A) : Color(argb: 0xFFF3A7AB)).padding(.top, 2)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                if let v {
                    Text(scoreText(v)).font(RealFont.inter(20, .heavy)).foregroundStyle(c.fg)
                        .frame(width: 54, height: 44).background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(c.bg))
                } else {
                    Text(entry.series ? t("Bölümleri puanla") : t("Puan ver")).font(RealFont.inter(12)).foregroundStyle(YasamTone.muted)
                }
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(rateCard))
            .contentShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
    }
}

/// Hızlı puanla: one waiting title at a time on the full-screen rater. Atla goes to the next, Kaydet saves.
private struct QuickRateScreen: View {
    let waiting: [RatingEntry]
    let avg: Double?
    var onClose: () -> Void
    @State private var skipped: Set<String> = []
    @State private var done: Set<String> = []

    var body: some View {
        let queue = waiting.filter { !skipped.contains($0.key) && !done.contains($0.key) }
        ZStack {
            Color.black.ignoresSafeArea()
            if let current = queue.first {
                RateFullScreen(media: current.media, meta: [current.media.year.map(String.init), t("Film")].compactMap { $0 }.joined(separator: " · "),
                               initial: nil, avg: avg, progress: t("{0} kaldı", queue.count), skipLabel: t("Atla"),
                               onSkip: { withAnimation(.easeOut(duration: 0.25)) { _ = skipped.insert(current.key) } },
                               onSave: { v in
                                   MediaRatingStore.shared.rate(current.media, v, past: current.addedAt == nil)
                                   withAnimation(.easeOut(duration: 0.25)) { _ = done.insert(current.key) }
                               },
                               onClose: onClose, loadSummary: true)
                    .id(current.key)
                    .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .move(edge: .leading).combined(with: .opacity)))
            } else {
                Color.clear.onAppear { onClose() }
            }
        }
    }
}

/// The full-screen rater: big cover (tap it for the description, as it comes), the score and its verdict, a sliding
/// ruler of 0,1 steps in the score colours and a background that takes the score's colour. Used by Hızlı puanla,
/// "İzledim" in Keşfet and a title tapped in Puanla. [onSkip] is the left button (Atla / Vazgeç / Puanı kaldır).
struct RateFullScreen: View {
    let media: MediaBlock
    let meta: String
    let initial: Double?
    let avg: Double?
    let progress: String?
    let skipLabel: String
    var onSkip: () -> Void
    var onSave: (Double) -> Void
    var onClose: () -> Void
    var summary: String? = nil
    var loadSummary = false
    @State private var score: Double?
    @State private var reading = false
    @State private var loaded: String?

    var body: some View {
        let s = score ?? initial ?? 8
        let c = ratingColors(s)
        let about = (loaded ?? summary).flatMap { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : $0 }
        let numberColor: Color = s >= 9 ? Color(argb: 0xFFD9F27A) : s > 8 ? Color(argb: 0xFFC9E27A) : c.fg
        ZStack {
            Color.black.ignoresSafeArea()
            RadialGradient(colors: [c.bg.opacity(0.8), .black], center: .center, startRadius: 0, endRadius: 560)
                .ignoresSafeArea().animation(.easeOut(duration: 0.22), value: s)
            VStack(spacing: 0) {
                HStack {
                    YasamBackButton(label: t("Kapat"), action: onClose)
                    Spacer()
                    if let progress { Text(progress).font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.7)).lineLimit(1) }
                    Spacer()
                    Color.clear.frame(width: 40, height: 40)
                }
                .padding(.horizontal, 20)
                // The poster takes all the height that is left (2 : 3), so tall phones get a big cover.
                ZStack(alignment: .bottomLeading) {
                    PosterArtwork(media: media, titleSize: 16)
                    LinearGradient(stops: [.init(color: .clear, location: 0.6), .init(color: .black.opacity(0.85), location: 1)], startPoint: .top, endPoint: .bottom)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(media.title).font(RealFont.inter(24, .bold)).foregroundStyle(.white).lineLimit(2)
                        Text(meta).font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.75)).lineLimit(2)
                    }
                    .padding(16)
                    if about != nil && !reading {
                        Text("i").font(RealFont.inter(14, .bold)).foregroundStyle(.white).frame(width: 28, height: 28)
                            .background(Circle().fill(.black.opacity(0.45))).padding(12)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                            .accessibilityLabel(t("Açıklama"))
                    }
                    if reading, let about {
                        ScrollView {
                            Text(about).font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.92)).lineSpacing(4)
                                .frame(maxWidth: .infinity, alignment: .leading).padding(18)
                        }
                        .background(Color.black.opacity(0.86))
                        .transition(.opacity)
                    }
                }
                .aspectRatio(2 / 3, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                .shadow(color: .black.opacity(0.5), radius: 24, y: 10)
                .contentShape(RoundedRectangle(cornerRadius: 26))
                .onTapGesture { if about != nil { withAnimation(.easeInOut(duration: 0.18)) { reading.toggle() } } }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.horizontal, 28).padding(.vertical, 12)
                HStack(alignment: .lastTextBaseline, spacing: 0) {
                    Text(scoreText(s)).font(RealFont.inter(64, .heavy)).foregroundStyle(numberColor).contentTransition(.numericText())
                    Text("  " + verdict(s)).font(RealFont.inter(16, .semibold)).foregroundStyle(.white.opacity(0.85))
                }
                .animation(.snappy(duration: 0.2), value: s)
                ScoreRuler(score: Binding(get: { s }, set: { score = $0 })).padding(.top, 10)
                HStack {
                    Text(t("Beğenmedim")).frame(maxWidth: .infinity, alignment: .leading)
                    if let avg { Text(t("Ortalaman {0}", f1(avg))) }
                    Text(t("Efsane")).frame(maxWidth: .infinity, alignment: .trailing)
                }
                .font(RealFont.inter(11)).foregroundStyle(.white.opacity(0.6))
                .padding(.horizontal, 20).padding(.top, 8)
                HStack(spacing: 10) {
                    Button(action: onSkip) {
                        Text(skipLabel).font(RealFont.inter(15, .semibold)).foregroundStyle(.white)
                            .frame(maxWidth: .infinity).frame(height: 52)
                            .background(Capsule().fill(.black.opacity(0.25)))
                            .overlay(Capsule().strokeBorder(.white.opacity(0.25), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    Button {
                        UINotificationFeedbackGenerator().notificationOccurred(.success)
                        onSave(s)
                    } label: {
                        Text(t("Kaydet")).font(RealFont.inter(15, .bold)).foregroundStyle(.black)
                            .frame(maxWidth: .infinity).frame(height: 52).background(Capsule().fill(.white))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 8)
            }
            .padding(.vertical, 8)
        }
        .task(id: media.id) {
            guard loadSummary, media.type == .MOVIE else { return }
            loaded = await MediaRatingStore.shared.filmSummary(media)
        }
    }
}

/// The score as a ruler, like the sky poster's clock: 1–10 slides under a fixed centre line, a tick every 0,1, taller on
/// the halves and tallest with a number on the whole points, each tick in its score colour. Ticks swell near the
/// centre; a fling glides and lands on the nearest 0,1, and every 0,1 passed ticks under the thumb.
struct ScoreRuler: View {
    @Binding var score: Double
    @State private var position: Double?
    @State private var dragStart: Double?
    private let pxPerTenth: CGFloat = 11

    var body: some View {
        let pos = position ?? score * 10
        RulerTicks(position: pos, pxPerTenth: pxPerTenth)
            .frame(maxWidth: .infinity).frame(height: 86)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 2)
                    .onChanged { g in
                        if dragStart == nil { dragStart = pos }
                        let next = min(max(dragStart! - Double(g.translation.width / pxPerTenth), 10), 100)
                        position = next
                        report(next)
                    }
                    .onEnded { g in
                        let fling = Double((g.predictedEndTranslation.width - g.translation.width) / pxPerTenth) * 0.6
                        let target = min(max((pos - fling).rounded(), 10), 100)
                        dragStart = nil
                        withAnimation(.spring(response: 0.45, dampingFraction: 0.9)) { position = target }
                        report(target)
                    }
            )
            .onTapGesture(coordinateSpace: .local) { p in
                // A tap jumps to the tick under the finger.
                let width = UIScreen.main.bounds.width
                let target = min(max((pos + Double((p.x - width / 2) / pxPerTenth)).rounded(), 10), 100)
                withAnimation(.spring(response: 0.4, dampingFraction: 0.9)) { position = target }
                report(target)
            }
            .onChange(of: score) { _, v in
                if dragStart == nil, abs((position ?? v * 10) - v * 10) > 0.5 { position = v * 10 }
            }
            .accessibilityElement()
            .accessibilityLabel(t("Puan {0}", scoreText(score)))
            .accessibilityAdjustableAction { dir in
                let next = min(max(score * 10 + (dir == .increment ? 1 : -1), 10), 100)
                position = next
                score = next / 10
            }
    }

    private func report(_ v: Double) {
        let tenth = min(max(Int(v.rounded()), 10), 100)
        if tenth != Int((score * 10).rounded()) {
            UISelectionFeedbackGenerator().selectionChanged()
            score = Double(tenth) / 10
        }
    }
}

/// The ruler's drawing; Animatable so a fling's glide is drawn frame by frame.
private struct RulerTicks: View, Animatable {
    var position: Double
    let pxPerTenth: CGFloat
    var animatableData: Double {
        get { position }
        set { position = newValue }
    }

    var body: some View {
        Canvas { ctx, size in
            let cx = size.width / 2
            let half = size.width / 2
            let base = size.height * 0.58
            let visible = Int(half / pxPerTenth) + 2
            var k = max(Int(position) - visible, 10)
            while k <= min(Int(position) + visible, 100) {
                let x = cx + CGFloat(Double(k) - position) * pxPerTenth
                let near = 1 - min(max(abs(x - cx) / half, 0), 1)
                let whole = k % 10 == 0
                let halfPt = k % 5 == 0
                let v = Double(k) / 10
                let tone = v <= 5 ? Color(argb: 0xFFC0505A) : ratingColors(v).bg
                let len = (whole ? 34 : halfPt ? 22 : 13) * (0.7 + 0.5 * near)
                var line = Path()
                line.move(to: CGPoint(x: x, y: base - len))
                line.addLine(to: CGPoint(x: x, y: base))
                ctx.stroke(line, with: .color(tone.opacity((0.25 + 0.75 * near) * (whole ? 1 : 0.8))),
                           style: StrokeStyle(lineWidth: whole ? 3 : 1.6, lineCap: .round))
                if whole {
                    ctx.draw(Text("\(k / 10)").font(RealFont.inter(13, .bold)).foregroundStyle(.white.opacity(0.35 + 0.65 * near)),
                             at: CGPoint(x: x, y: base + 20))
                }
                k += 1
            }
            // The fixed centre, where the score above is read.
            var centre = Path()
            centre.move(to: CGPoint(x: cx, y: base - 46))
            centre.addLine(to: CGPoint(x: cx, y: base + 4))
            ctx.stroke(centre, with: .color(.white), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            ctx.fill(Path(ellipseIn: CGRect(x: cx - 4, y: base - 54, width: 8, height: 8)), with: .color(.white))
        }
    }
}

/// Series page: the poster fills a rounded top box with the title, how many episodes are scored and the series score;
/// season chips; 5-wide episode tiles in the score colours (a "+" outline when not scored); the best episode and how
/// the scores move; a quiet "Listeden çıkar" with a confirm sheet.
private struct SeriesPage: View {
    let entry: RatingEntry
    let onBack: () -> Void
    let onRate: (RateTarget) -> Void
    let onRemove: () -> Void
    @State private var confirmRemove = false
    @State private var episodes: [SeriesEpisode]?
    @State private var error: String?
    @State private var season: Int?

    var body: some View {
        let all = episodes ?? []
        let bySeason = Dictionary(grouping: all, by: \.season).sorted { $0.key < $1.key }
        let scored = all.filter { entry.episodes[$0.key] != nil }
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    hero(seasons: bySeason.count, scored: scored.count, total: all.count)
                    if let error { Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFB4AB)).padding(16) }
                    if bySeason.count > 1 {
                        ScrollView(.horizontal) {
                            HStack(spacing: 6) {
                                RateChip(label: t("Hepsi"), on: season == nil) { season = nil }
                                ForEach(bySeason, id: \.key) { pair in
                                    let sAvg = mean(pair.value.compactMap { entry.episodes[$0.key] })
                                    RateChip(label: t("{0}. sezon", pair.key) + (sAvg.map { " · " + f1($0) } ?? ""), on: season == pair.key) { season = pair.key }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                        .scrollIndicators(.hidden)
                        .padding(.top, 16)
                    }
                    grid(bySeason)
                    if scored.count >= 2 { facts(all) }
                    Button { confirmRemove = true } label: {
                        Text(t("Listeden çıkar")).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFFF8A80))
                            .padding(.horizontal, 16).padding(.vertical, 9)
                            .background(Capsule().fill(Color(argb: 0xFFFF8A80).opacity(0.08)))
                    }
                    .buttonStyle(.plain)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 20).padding(.bottom, 36)
                }
                .animation(.easeOut(duration: 0.25), value: season)
            }
            .scrollIndicators(.hidden)
            .ignoresSafeArea(edges: .top)
        }
        .overlay {
            if confirmRemove {
                ConfirmSheet(title: t("Diziyi listeden çıkarmak istediğine emin misin?"),
                             message: t("{0} ve bölüm puanları Puanlarım'dan silinir.", entry.media.title),
                             confirmLabel: t("Çıkar"), onDismiss: { confirmRemove = false }, onConfirm: onRemove)
            }
        }
        .task(id: entry.key) {
            do { episodes = try await MediaRatingStore.shared.episodes(entry.media) } catch { self.error = error.localizedDescription; episodes = [] }
        }
    }

    private func hero(seasons: Int, scored: Int, total: Int) -> some View {
        GeometryReader { g in
            let top = g.safeAreaInsets.top
            ZStack(alignment: .bottomLeading) {
                PosterArtwork(media: entry.media, titleSize: 16)
                LinearGradient(stops: [.init(color: .black.opacity(0.15), location: 0.3), .init(color: .black.opacity(0.88), location: 1)],
                               startPoint: .top, endPoint: .bottom)
                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(entry.media.title).font(RealFont.inter(30, .bold)).foregroundStyle(.white).lineLimit(2)
                        Text(episodes == nil ? t("Bölümler yükleniyor…") : t("Dizi · {0} sezon · {1} / {2} bölüm puanlı", seasons, scored, total))
                            .font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.75))
                        if let at = entry.addedAt {
                            Text(t("Eklendi: {0}", Self.longDate(at))).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.6))
                        }
                    }
                    Spacer(minLength: 10)
                    if let v = entry.value {
                        let c = ratingColors(v)
                        Text(f1(v)).font(RealFont.inter(26, .heavy)).foregroundStyle(c.fg)
                            .padding(.horizontal, 12).padding(.vertical, 4).background(RoundedRectangle(cornerRadius: 14).fill(c.bg))
                    }
                }
                .padding(.horizontal, 22).padding(.bottom, 18)
            }
            .frame(width: g.size.width, height: 250 + top)
            .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 34, bottomTrailingRadius: 34, style: .continuous))
            .overlay(alignment: .topLeading) {
                YasamBackButton(label: t("Puanla'ya dön"), action: onBack).padding(.leading, 16).padding(.top, top + 8)
            }
        }
        .frame(height: 250 + topInset)
    }

    private var topInset: CGFloat {
        (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.windows.first?.safeAreaInsets.top ?? 47
    }

    private static func longDate(_ at: EpochMillis) -> String {
        let f = DateFormatter()
        f.locale = I18n.locale
        f.dateFormat = "d MMMM yyyy"
        return f.string(from: at.date)
    }

    private func grid(_ bySeason: [(key: Int, value: [SeriesEpisode])]) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(bySeason.filter { season == nil || $0.key == season }, id: \.key) { pair in
                HStack {
                    YasamCaps(text: t("{0}. SEZON", pair.key))
                    Spacer()
                    Button { onRate(.season(entry.media, pair.key, pair.value)) } label: {
                        Text(t("Sezonu puanla")).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.7)).padding(.horizontal, 8).padding(.vertical, 4)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.bottom, 8)
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 5), spacing: 6) {
                    ForEach(pair.value) { ep in tile(ep) }
                }
                .padding(.bottom, 12)
            }
            Text(episodes == nil ? t("Bölümler yükleniyor…") : t("Boş kutuya dokun: o bölümü puanla.")).font(RealFont.inter(12)).foregroundStyle(YasamTone.muted)
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(rateCard))
        .padding(16)
    }

    private func tile(_ ep: SeriesEpisode) -> some View {
        let v = entry.episodes[ep.key]
        let c = ratingColors(v)
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        return Button { onRate(.episode(entry.media, ep, initial: v)) } label: {
            VStack(spacing: 0) {
                Text("B\(ep.number)").font(RealFont.inter(10)).foregroundStyle(v != nil ? c.fg.opacity(0.8) : YasamTone.muted)
                Text(v.map(scoreText) ?? "+").font(RealFont.inter(16, .heavy)).foregroundStyle(v != nil ? c.fg : YasamTone.muted)
            }
            .frame(maxWidth: .infinity).frame(height: 52)
            .background(shape.fill(v != nil ? c.bg : .clear))
            .overlay(shape.strokeBorder(v != nil ? .clear : .white.opacity(0.25), lineWidth: 1))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    private func facts(_ all: [SeriesEpisode]) -> some View {
        let ordered = all.compactMap { ep in entry.episodes[ep.key].map { (ep, $0) } }
        let best = ordered.max { $0.1 < $1.1 }!
        let half = ordered.count / 2
        let trend = (mean(ordered.dropFirst(half).map(\.1)) ?? 0) - (mean(ordered.prefix(half).map(\.1)) ?? 0)
        return HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(t("En iyi bölüm")).font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFFC9E27A))
                Text("S\(best.0.season) · B\(best.0.number) · \(scoreText(best.1))").font(RealFont.inter(16, .bold)).foregroundStyle(.white).lineLimit(1)
            }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color(argb: 0xFFD9F27A).opacity(0.1)))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Color(argb: 0xFFD9F27A).opacity(0.3), lineWidth: 1))
            VStack(alignment: .leading, spacing: 2) {
                Text(t("Gidişat")).font(RealFont.inter(11)).foregroundStyle(YasamTone.muted)
                Text(trend > 0.3 ? t("Yükseliyor ↗") : trend < -0.3 ? t("Düşüyor ↘") : t("Sabit →")).font(RealFont.inter(16, .bold)).foregroundStyle(.white)
            }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(rateCard))
        }
        .padding(.horizontal, 16)
    }
}

// MARK: Rating sheets

/// Film: the Turkish reminder (TheTVDB, else AI once); a tap on the placeholder shows the original text right away.
struct FilmRatingSheet: View {
    let media: MediaBlock
    let meta: String
    let initial: Double?
    var onRemove: (() -> Void)?
    var removeLabel = t("Puanı kaldır")
    let onSave: (Double) -> Void
    @State private var text: String?
    @State private var loading = true
    var body: some View {
        RatingSheet(title: media.title, meta: meta, reminder: text, initial: initial, onRemove: onRemove, removeLabel: removeLabel,
                    reminderLoading: loading, onSkipLoading: { if loading { text = media.description; loading = false } }, onSave: onSave)
            .task(id: media.id) {
                let result = await awaitAtMost(translationWait) { await MediaRatingStore.shared.filmSummary(media) } ?? media.description
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
                let result = await awaitAtMost(translationWait) { await MediaRatingStore.shared.translate(media, episode) }
                if loading { shown = result; loading = false }
            }
    }
}

/// A translation that is not back within this time gives way to the original text (Android: TRANSLATION_WAIT_MS).
let translationWait: Double = 3

/// Starts `work` on its own (a slow translation still finishes and is kept for next time) and waits at most `seconds`.
func awaitAtMost<T: Sendable>(_ seconds: Double, _ work: @escaping @Sendable () async -> T?) async -> T? {
    let job = Task { await work() }
    return await withTimeout(seconds) { await job.value } ?? nil
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
    var removeLabel = t("Puanı kaldır")
    var reminderLoading = false
    var titleLoading = false
    var onSkipLoading: (() -> Void)?
    let onSave: (Double) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var value: Double = 7
    @State private var expanded = false
    @State private var overflows = false
    /// The sheet is as tall as its content, like Android's bottom sheet (it used to open full height).
    @State private var contentHeight: CGFloat = 540

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
                        Text(expanded ? t("Daha az") : t("Devamı")).font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFF6A6A6A))
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
            Text(t("Sürükle veya bas")).font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFF666666)).padding(.top, 8)
            HStack(spacing: 10) {
                Button { onRemove?(); dismiss() } label: {
                    Text(onRemove != nil ? removeLabel : t("Vazgeç")).font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFBBBBBB))
                        .frame(maxWidth: .infinity, minHeight: 48).overlay(Capsule().stroke(Color(argb: 0xFF3A3A3A), lineWidth: 1))
                }
                .buttonStyle(.plain)
                Button { onSave(value); dismiss() } label: {
                    Text(t("Kaydet")).font(RealFont.inter(15, .medium)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity, minHeight: 48).background(Capsule().fill(.white))
                }
                .buttonStyle(.plain).layoutPriority(1)
            }
            .padding(.top, 22)
        }
        .padding(.horizontal, 24).padding(.top, 24).padding(.bottom, 16)
        .fixedSize(horizontal: false, vertical: true)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { h in
            withAnimation(.timingCurve(0.2, 0, 0, 1, duration: 0.32)) { contentHeight = h }
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .presentationDetents([.height(contentHeight)])
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
