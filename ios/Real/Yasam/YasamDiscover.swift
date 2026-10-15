import SwiftUI

/// YasamDiscover.kt: "KEŞFET", four verbs in a 2×2 grid. The tile opens suggestions; its small line
/// ("Listende 12 ›") opens the saved list. Puanla opens Puanlarım and shows the latest scores as coloured squares.
struct YasamDiscoverGrid: View {
    let watchCount: Int
    let playCount: Int
    let listenCount: Int
    let watchArt: [String]
    let playArt: [String]
    let listenArt: [String]
    let ratings: [RatingEntry]
    /// kind: 0 izle, 1 oyna, 2 dinle, 3 puanla; list = the saved list instead of suggestions.
    let open: (_ kind: Int, _ list: Bool) -> Void

    private struct Tone { let top: Color; let bottom: Color; let text: Color }
    private let watch = Tone(top: Color(argb: 0xFF16264D), bottom: Color(argb: 0xFF0A0F1D), text: Color(argb: 0xFF8EA2D6))
    private let play = Tone(top: Color(argb: 0xFF4D2510), bottom: Color(argb: 0xFF1D0E06), text: Color(argb: 0xFFD6A48E))
    private let listen = Tone(top: Color(argb: 0xFF3B1640), bottom: Color(argb: 0xFF140816), text: Color(argb: 0xFFC99AD1))
    private let rate = Tone(top: Color(argb: 0xFF2B3A0E), bottom: Color(argb: 0xFF0E1405), text: Color(argb: 0xFFB9D47A))

    var body: some View {
        let values = ratings.compactMap(\.value)
        let avg = values.isEmpty ? nil : (values.reduce(0, +) / Double(values.count) * 10).rounded() / 10
        let recent = ratings.sorted { ($0.addedAt ?? .min, $0.order) > ($1.addedAt ?? .min, $1.order) }.compactMap(\.value).prefix(6)
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .lastTextBaseline) {
                Text("KEŞFET").font(.system(size: 12, design: .monospaced)).kerning(1.2).foregroundStyle(YasamTone.muted)
                Spacer()
                Text("bugün ne yapsak").font(.system(size: 12, weight: .light)).foregroundStyle(.white.opacity(0.4))
            }
            .padding(.bottom, 2)
            HStack(spacing: 10) {
                tile("İzle", watch, line(watchCount), kind: 0) { stack(watchArt, circle: false) }
                tile("Oyna", play, line(playCount), kind: 1) { stack(playArt, circle: false) }
            }
            HStack(spacing: 10) {
                tile("Dinle", listen, line(listenCount), kind: 2) { stack(listenArt, circle: true) }
                tile("Puanla", rate, avg.map { "ort. \(scoreText($0))" } ?? "Henüz yok", kind: 3, hasList: false) {
                    HStack(spacing: 3) {
                        ForEach(Array(recent.enumerated()), id: \.offset) { _, v in
                            RoundedRectangle(cornerRadius: 3).fill(ratingColors(v).bg).frame(width: 10, height: 10)
                        }
                    }
                }
            }
        }
        .padding(.top, 22).padding(.bottom, 8)
    }

    private func line(_ n: Int) -> String { n == 0 ? "Listen boş" : "Listende \(n)" }

    private func tile<V: View>(_ verb: String, _ tone: Tone, _ line: String, kind: Int, hasList: Bool = true,
                               @ViewBuilder visual: () -> V) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(verb).font(.system(size: 24, weight: .light)).foregroundStyle(.white)
            Text(line + (hasList ? "  ›" : "")).font(.system(size: 11)).foregroundStyle(tone.text)
                .padding(.vertical, 4).contentShape(Rectangle())
                .onTapGesture { open(kind, hasList) }
                .padding(.top, 2)
            Spacer(minLength: 10)
            visual()
        }
        .padding(14)
        .frame(maxWidth: .infinity, minHeight: 124, alignment: .topLeading)
        .background(LinearGradient(colors: [tone.top, tone.bottom], startPoint: .topLeading, endPoint: .bottomTrailing))
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(.white.opacity(0.05), lineWidth: 1))
        .contentShape(RoundedRectangle(cornerRadius: 22))
        .onTapGesture { open(kind, false) }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }

    private func stack(_ urls: [String], circle: Bool) -> some View {
        let w: CGFloat = circle ? 26 : 22, h: CGFloat = circle ? 26 : 32
        return ZStack(alignment: .leading) {
            ForEach(Array(urls.prefix(3).enumerated()), id: \.offset) { i, u in
                AsyncImage(url: URL(string: u)) { p in if let img = p.image { img.resizable().scaledToFill() } else { Color(argb: 0xFF26262C) } }
                    .frame(width: w, height: h)
                    .clipShape(RoundedRectangle(cornerRadius: circle ? w / 2 : 5))
                    .overlay(RoundedRectangle(cornerRadius: circle ? w / 2 : 5).stroke(.black, lineWidth: 1.5))
                    .offset(x: CGFloat(i) * (w - 7))
            }
        }
        .frame(height: h, alignment: .leading)
    }
}
