import SwiftUI
import UIKit

// YasamGlow.kt + YasamTiles.kt + YasamDiscover.kt: Me's tiles (draft C: dark surface, the colour only on the label),
// the hold-to-edit grid and the small visuals inside the tiles.

enum YasamTileColors {
    static let surface = Color(argb: 0xFF141416)
    static let line = Color(argb: 0xFF222226)
    static let button = Color(argb: 0xFF232327)
    static let sub = Color(argb: 0xFF9A9AA3)
    static let empty = Color(argb: 0xFF6E6E76)
}

/// One Me tile: label top-left, a small visual, the big number bottom-left, an optional "+" bottom-right.
/// Half and full width share the layout; wide tiles get room for a richer visual on the right.
struct YasamGlowTile<Visual: View, Side: View>: View {
    let label: String
    let tone: YasamGlowTone
    let value: String
    var unit: String? = nil
    var sub: String? = nil
    var wide = false
    var onAdd: (() -> Void)? = nil
    var addLabel = ""
    /// Makes the small line under the number its own button (Keşfet: open the saved list).
    var onSub: (() -> Void)? = nil
    /// Keşfet: a soft wash of the tile's colour from the top-left; Bugün tiles stay plain dark.
    var wash = false
    @ViewBuilder var visual: () -> Visual
    @ViewBuilder var side: () -> Side

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        let ink = tone.light.lerp(.white, 0.35)
        ZStack(alignment: .bottomTrailing) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(label).font(RealFont.inter(14, .semibold)).foregroundStyle(ink)
                    visual()
                }
                .padding(.trailing, wide && Side.self != EmptyView.self ? 120 : 0)
                Spacer(minLength: 10)
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .lastTextBaseline, spacing: 0) {
                        Text(value).font(RealFont.inter(26, .semibold)).foregroundStyle(value == "—" ? YasamTileColors.empty : tone.text)
                            .lineLimit(1).minimumScaleFactor(0.6)
                        if let unit { Text(" " + unit).font(RealFont.inter(13)).foregroundStyle(YasamTileColors.sub).lineLimit(1) }
                    }
                    if let sub, let onSub {
                        // A tappable line is a small pill with an arrow, so it reads as a button.
                        Button(action: onSub) {
                            HStack(spacing: 0) {
                                Text(sub).font(RealFont.inter(12, .medium)).lineLimit(1).truncationMode(.tail)
                                Text("  ›").font(RealFont.inter(13, .semibold))
                            }
                            .foregroundStyle(tone.text)
                            .padding(.leading, 11).padding(.trailing, 9).padding(.vertical, 5)
                            .background(Capsule().fill(.white.opacity(0.10)))
                            .overlay(Capsule().strokeBorder(.white.opacity(0.14), lineWidth: 0.75))
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 4)
                        .padding(.trailing, onAdd != nil ? 48 : 0)
                    } else if let sub {
                        Text(sub).font(RealFont.inter(12)).foregroundStyle(YasamTileColors.sub).lineLimit(1)
                            .padding(.top, 2).padding(.trailing, onAdd != nil ? 48 : 0)
                    }
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, minHeight: 112, maxHeight: .infinity, alignment: .topLeading)
            if wide && Side.self != EmptyView.self {
                side().padding(.trailing, 18).frame(maxHeight: .infinity, alignment: .center)
            }
            if let onAdd {
                Button(action: onAdd) {
                    Text("+").font(RealFont.inter(22, .semibold)).foregroundStyle(.white)
                        .frame(width: 44, height: 44).background(Circle().fill(YasamTileColors.button))
                }
                .buttonStyle(.plain)
                .padding(10)
                .accessibilityLabel(addLabel)
            }
        }
        .background {
            ZStack {
                YasamTileColors.surface
                if wash { LinearGradient(stops: [.init(color: tone.light.opacity(0.40), location: 0), .init(color: .clear, location: 0.8)],
                                         startPoint: .topLeading, endPoint: .bottomTrailing) }
            }
        }
        .clipShape(shape)
        .overlay(shape.strokeBorder(YasamTileColors.line, lineWidth: 1))
        .contentShape(shape)
    }
}

extension YasamGlowTile where Side == EmptyView {
    init(label: String, tone: YasamGlowTone, value: String, unit: String? = nil, sub: String? = nil, wide: Bool = false,
         onAdd: (() -> Void)? = nil, addLabel: String = "", onSub: (() -> Void)? = nil, wash: Bool = false,
         @ViewBuilder visual: @escaping () -> Visual) {
        self.init(label: label, tone: tone, value: value, unit: unit, sub: sub, wide: wide, onAdd: onAdd, addLabel: addLabel,
                  onSub: onSub, wash: wash, visual: visual, side: { EmptyView() })
    }
}

extension YasamGlowTile where Side == EmptyView, Visual == EmptyView {
    init(label: String, tone: YasamGlowTone, value: String, unit: String? = nil, sub: String? = nil, wide: Bool = false,
         onAdd: (() -> Void)? = nil, addLabel: String = "", onSub: (() -> Void)? = nil, wash: Bool = false) {
        self.init(label: label, tone: tone, value: value, unit: unit, sub: sub, wide: wide, onAdd: onAdd, addLabel: addLabel,
                  onSub: onSub, wash: wash, visual: { EmptyView() }, side: { EmptyView() })
    }
}

// MARK: Grid

/// Tiles in rows: a wide tile takes a row, half tiles pair up. Hold a tile to lift it: the grid enters edit mode and
/// the tile follows the finger; the tile under it is marked, and on release the two change places and every tile
/// slides to its new spot. Nothing moves while the finger is down. In edit mode each tile shows "–" and Tam / Yarım.
struct YasamTileGrid<Tile: View>: View {
    let parts: [YasamPart]
    let wide: Set<YasamPart>
    @Binding var editing: Bool
    var onTap: (YasamPart) -> Void
    @ViewBuilder var tile: (YasamPart, Bool) -> Tile

    @Namespace private var ns
    @State private var frames: [YasamPart: CGRect] = [:]
    @State private var dragged: YasamPart?
    @State private var translation: CGSize = .zero
    @State private var hover: YasamPart?
    @State private var wiggle = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var rows: [[YasamPart]] {
        var rows: [[YasamPart]] = []
        var pending: YasamPart?
        for p in parts {
            if wide.contains(p) {
                if let q = pending { rows.append([q]); pending = nil }
                rows.append([p])
            } else if let q = pending {
                rows.append([q, p]); pending = nil
            } else { pending = p }
        }
        if let q = pending { rows.append([q]) }
        return rows
    }

    var body: some View {
        VStack(spacing: 10) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: 10) {
                    ForEach(row) { part in cell(part) }
                    // A lone half fills the row, except while editing (so it can be paired again).
                    if row.count == 1, !wide.contains(row[0]), editing { Color.clear.frame(maxWidth: .infinity) }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .coordinateSpace(.named("yasamTiles"))
        .animation(reduceMotion ? nil : .spring(response: 0.42, dampingFraction: 0.82), value: parts)
        .animation(reduceMotion ? nil : .spring(response: 0.42, dampingFraction: 0.82), value: wide)
        .animation(reduceMotion ? nil : .spring(response: 0.35, dampingFraction: 0.85), value: editing)
        .onChange(of: editing, initial: true) { _, on in
            if on && !reduceMotion {
                withAnimation(.easeInOut(duration: 0.14).repeatForever(autoreverses: true)) { wiggle = true }
            } else {
                withAnimation(.linear(duration: 0.1)) { wiggle = false }
            }
        }
    }

    private func cell(_ part: YasamPart) -> some View {
        let isWide = wide.contains(part)
        let lifted = dragged == part
        let target = hover == part
        return tile(part, isWide)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay(alignment: .topTrailing) {
                if editing {
                    Button { YasamLayout.shared.setWide(part, !isWide) } label: {
                        Text(isWide ? t("Yarım") : t("Tam")).font(RealFont.inter(12, .semibold)).foregroundStyle(.black)
                            .padding(.horizontal, 12).padding(.vertical, 6).background(Capsule().fill(.white.opacity(0.92)))
                    }
                    .buttonStyle(.plain).padding(10)
                    .transition(.scale.combined(with: .opacity))
                }
            }
            .overlay(alignment: .topLeading) {
                if editing {
                    Button { YasamLayout.shared.setHidden(part, true) } label: {
                        Text("–").font(RealFont.inter(16, .bold)).foregroundStyle(.black)
                            .frame(width: 26, height: 26).background(Circle().fill(.white.opacity(0.92)))
                    }
                    .buttonStyle(.plain).padding(8)
                    .accessibilityLabel(t("Gizle"))
                    .transition(.scale.combined(with: .opacity))
                }
            }
            .onGeometryChange(for: CGRect.self) { $0.frame(in: .named("yasamTiles")) } action: { frames[part] = $0 }
            .matchedGeometryEffect(id: part, in: ns)
            .rotationEffect(.degrees(lifted ? 0 : (wiggle ? 1 : -1) * (isWide ? 0.24 : 0.6)))
            .scaleEffect(lifted ? 1.04 : (target ? 0.94 : 1))
            .opacity(target ? 0.7 : 1)
            .offset(lifted ? translation : .zero)
            .shadow(color: .black.opacity(lifted ? 0.5 : 0), radius: lifted ? 18 : 0, y: lifted ? 8 : 0)
            .zIndex(lifted ? 2 : 0)
            .contentShape(Rectangle())
            .onTapGesture { if !editing { onTap(part) } }
            .gesture(
                LongPressGesture(minimumDuration: 0.4)
                    .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .named("yasamTiles")))
                    .onChanged { value in
                        switch value {
                        case .second(true, let drag):
                            if dragged == nil {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                editing = true
                                hover = nil
                                dragged = part
                            }
                            guard let drag else { return }
                            translation = drag.translation
                            let over = parts.first { $0 != part && (frames[$0]?.contains(drag.location) ?? false) }
                            if over != hover {
                                hover = over
                                if over != nil { UISelectionFeedbackGenerator().selectionChanged() }
                            }
                        default: break
                        }
                    }
                    .onEnded { _ in
                        if let h = hover, let d = dragged {
                            withAnimation(reduceMotion ? nil : .spring(response: 0.42, dampingFraction: 0.82)) {
                                YasamLayout.shared.move(d, to: h)
                                translation = .zero
                            }
                        } else {
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) { translation = .zero }
                        }
                        hover = nil
                        dragged = nil
                    }
            )
            .accessibilityAction(named: isWide ? t("Yarım yap") : t("Tam yap")) { YasamLayout.shared.setWide(part, !isWide) }
            .accessibilityAction(named: t("Gizle")) { YasamLayout.shared.setHidden(part, true) }
            .accessibilityAction(named: t("Yukarı taşı")) {
                if let i = parts.firstIndex(of: part), i > 0 { YasamLayout.shared.move(part, to: parts[i - 1]) }
            }
            .accessibilityAction(named: t("Aşağı taşı")) {
                if let i = parts.firstIndex(of: part), i < parts.count - 1 { YasamLayout.shared.move(part, to: parts[i + 1]) }
            }
    }
}

/// While editing: the hint, "Bitti", and the hidden tiles as "+ name" to bring back.
struct YasamEditBar: View {
    let hidden: [YasamPart]
    var onDone: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(t("Sürükle · – gizle · Tam / Yarım")).font(RealFont.inter(13)).foregroundStyle(YasamTone.muted)
                Spacer()
                Button(action: onDone) {
                    Text(t("Bitti")).font(RealFont.inter(14, .semibold)).foregroundStyle(.black)
                        .padding(.horizontal, 18).padding(.vertical, 9).background(Capsule().fill(.white))
                }
                .buttonStyle(.plain)
            }
            .frame(minHeight: 48)
            if !hidden.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(hidden) { p in
                            Button { withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) { YasamLayout.shared.setHidden(p, false) } } label: {
                                Text("+ " + p.chipLabel).font(RealFont.inter(13)).foregroundStyle(.white).lineLimit(1)
                                    .padding(.horizontal, 14).padding(.vertical, 8).background(Capsule().fill(.white.opacity(0.08)))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(.bottom, 12)
    }
}

// MARK: Visuals

enum YasamVisuals {
    /// Thin progress line; width 0 = half the tile.
    static func progress(_ p: Double, width: CGFloat) -> some View {
        GeometryReader { g in
            let w = width == 0 ? g.size.width * 0.5 : width
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.22)).frame(width: w, height: 4)
                Capsule().fill(.white).frame(width: w * min(max(p, 0), 1), height: 4)
            }
        }
        .frame(width: width == 0 ? nil : width, height: 4)
    }

    /// The last days as small bars, today brightest.
    static func bars(_ values: [Int], gapRatio: CGFloat = 0.55) -> some View {
        Canvas { ctx, size in
            guard !values.isEmpty else { return }
            let maxV = CGFloat(max(values.max() ?? 1, 1))
            let gap = size.width / CGFloat(values.count)
            for (i, v) in values.enumerated() {
                let h = size.height * (0.08 + 0.92 * CGFloat(v) / maxV)
                let x = gap * CGFloat(i) + gap / 2
                var path = Path()
                path.move(to: CGPoint(x: x, y: size.height))
                path.addLine(to: CGPoint(x: x, y: size.height - h))
                ctx.stroke(path, with: .color(.white.opacity(i == values.count - 1 ? 1 : 0.45)),
                           style: StrokeStyle(lineWidth: gap * gapRatio, lineCap: .round))
            }
        }
        .frame(width: 110, height: 56)
    }

    /// Sleep against 10 hours: a soft bar with a small marker where tonight landed.
    static func sleepRange(_ minutes: Int, width: CGFloat) -> some View {
        ZStack(alignment: .leading) {
            Capsule().fill(.white.opacity(0.3)).frame(width: width, height: 4)
            if minutes > 0 {
                Circle().fill(Color(argb: 0xFFF5E86A)).frame(width: 10, height: 10)
                    .padding(.leading, min(width * min(CGFloat(minutes) / 600, 1), width - 10))
            }
        }
        .frame(width: width, height: 14, alignment: .leading)
    }

    /// Coffee beans: filled for each cup today, empty up to [slots].
    static func beans(_ count: Int, slots: Int) -> some View {
        HStack(spacing: 5) {
            ForEach(0..<min(max(slots, count), 12), id: \.self) { i in
                Capsule().fill(i < count ? Color(argb: 0xFFF3D2A8) : .white.opacity(0.22))
                    .frame(width: 9, height: 13).rotationEffect(.degrees(25))
            }
        }
    }

    /// One dot per drink this week.
    static func dots(_ count: Int, slots: Int) -> some View {
        HStack(spacing: 5) {
            ForEach(0..<min(max(slots, count), 21), id: \.self) { i in
                Circle().fill(.white.opacity(i < count ? 1 : 0.22)).frame(width: 7, height: 7)
            }
        }
    }

    /// Fanned covers, sized to the fan so a stack at the tile's right edge is not cut off.
    static func posterStack(_ urls: [String], round: Bool, w: CGFloat, h: CGFloat) -> some View {
        let n = CGFloat(min(max(urls.count, 1), 3))
        return ZStack(alignment: .leading) {
            ForEach(Array(urls.prefix(3).enumerated()), id: \.offset) { i, url in
                AsyncImage(url: URL(string: url)) { img in img.resizable().scaledToFill() } placeholder: { Color(argb: 0xFF26262C) }
                    .frame(width: w, height: h)
                    .clipShape(RoundedRectangle(cornerRadius: round ? w / 2 : (w > 30 ? 7 : 5), style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: round ? w / 2 : (w > 30 ? 7 : 5), style: .continuous).strokeBorder(.black, lineWidth: 1.5))
                    .offset(x: CGFloat(i) * (w - 7))
            }
        }
        .frame(width: w + (n - 1) * (w - 7), height: h, alignment: .leading)
    }
}

/// Son mood puanlarından mini çizgi; 2'den az veri varsa yalnız sönük bir taban çizgisi.
struct YasamSpark: View {
    let points: [Double]
    let tone: Color

    var body: some View {
        Canvas { ctx, size in
            let pad: CGFloat = 3
            if points.count < 2 {
                var p = Path()
                p.move(to: CGPoint(x: 2, y: size.height / 2)); p.addLine(to: CGPoint(x: size.width - 2, y: size.height / 2))
                ctx.stroke(p, with: .color(tone.opacity(0.25)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                return
            }
            let stepX = (size.width - 4) / CGFloat(points.count - 1)
            let xs = points.indices.map { 2 + CGFloat($0) * stepX }
            let ys = points.map { pad + (1 - CGFloat(($0 - 1) / 4)) * (size.height - 2 * pad) }
            var path = Path()
            path.move(to: CGPoint(x: xs[0], y: ys[0]))
            for i in 1..<points.count {
                let mid = (xs[i - 1] + xs[i]) / 2
                path.addCurve(to: CGPoint(x: xs[i], y: ys[i]), control1: CGPoint(x: mid, y: ys[i - 1]), control2: CGPoint(x: mid, y: ys[i]))
            }
            ctx.stroke(path, with: .color(tone), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        }
        .frame(height: 34)
    }
}

// MARK: Number sheet

/// A small bottom sheet to type a number with − / + and quick chips (steps: thousands; sleep: hours).
struct YasamNumberSheet: View {
    let title: String
    let initial: Int
    let step: Int
    let max: Int
    let quick: [(String, Int)]
    let format: (Int) -> String
    let onSave: (Int) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var value: Int?

    var body: some View {
        let v = value ?? initial
        VStack(spacing: 0) {
            Text(title).font(RealFont.inter(20, .semibold)).foregroundStyle(.white).padding(.top, 22)
            HStack(spacing: 22) {
                stepButton("−") { value = Swift.max(v - step, 0) }
                Text(format(v)).font(.system(size: 48, weight: .light)).foregroundStyle(.white).monospacedDigit()
                    .contentTransition(.numericText()).animation(.snappy, value: v)
                stepButton("+") { value = Swift.min(v + step, max) }
            }
            .padding(.vertical, 18)
            HStack(spacing: 8) {
                ForEach(quick, id: \.1) { label, q in
                    Button { value = q } label: {
                        Text(label).font(RealFont.inter(13, .semibold)).foregroundStyle(q == v ? .black : .white)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(Capsule().fill(q == v ? Color.white : Color.white.opacity(0.10)))
                    }
                    .buttonStyle(.plain)
                }
            }
            Text(t("Şimdilik elle. Saat bağlanınca kendiliğinden gelecek.")).font(RealFont.inter(12)).foregroundStyle(YasamTone.muted)
                .multilineTextAlignment(.center).padding(.top, 16)
            Button {
                onSave(v)
                dismiss()
            } label: {
                Text(t("Kaydet")).font(RealFont.inter(16, .bold)).foregroundStyle(.black)
                    .frame(maxWidth: .infinity).frame(height: 52).background(Capsule().fill(.white))
            }
            .buttonStyle(.plain)
            .padding(.top, 16)
        }
        .padding(.horizontal, 20).padding(.bottom, 8)
        .presentationDetents([.height(380)])
        .presentationBackground(Color(argb: 0xFF101012))
        .presentationDragIndicator(.visible)
    }

    private func stepButton(_ label: String, _ action: @escaping () -> Void) -> some View {
        Button {
            action()
            UISelectionFeedbackGenerator().selectionChanged()
        } label: {
            Text(label).font(.system(size: 24)).foregroundStyle(.white)
                .frame(width: 48, height: 48).background(Circle().fill(.white.opacity(0.10)))
        }
        .buttonStyle(.plain)
        .buttonRepeatBehavior(.enabled)
    }
}

// MARK: Header

/// Me's top (the approved draft): back, the name with today's date in grey, then the day in one sentence — grey words,
/// the numbers in white ("İyi akşamlar. Bugün 6.240 adım, 2 kahve ve 7s 20d uyku."). With nothing logged the sentence
/// is [fallback].
struct YasamMeHeader: View {
    let greeting: String
    let bits: [String]
    let fallback: String
    var onBack: () -> Void

    private var date: String {
        let f = DateFormatter()
        f.locale = I18n.locale
        f.setLocalizedDateFormatFromTemplate("EEEEd")
        let s = f.string(from: Date())
        return s.prefix(1).uppercased(with: I18n.locale) + s.dropFirst()
    }

    private var sentence: AttributedString {
        var out = AttributedString(greeting.lowercased(with: I18n.locale).prefix(1).uppercased(with: I18n.locale)
                                   + String(greeting.lowercased(with: I18n.locale).dropFirst()) + ". ")
        func white(_ s: String) -> AttributedString { var a = AttributedString(s); a.foregroundColor = .white; return a }
        if bits.isEmpty {
            out += white(fallback)
        } else {
            out += AttributedString(t("Bugün") + " ")
            for (i, b) in bits.enumerated() {
                if i > 0 { out += AttributedString(i == bits.count - 1 ? t(" ve ") : ", ") }
                out += white(b)
            }
            out += AttributedString(".")
        }
        return out
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Button(action: onBack) {
                    RealSymbol("chevron.left", size: 19.5).foregroundStyle(.white)
                        .frame(width: 40, height: 40).liquidGlass(Circle(), role: .chrome)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(t("Geri"))
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(YasamLayout.shared.name).font(RealFont.inter(30, .bold)).kerning(-0.6).foregroundStyle(.white)
                    Text(date).font(RealFont.inter(22, .semibold)).foregroundStyle(Color(argb: 0xFF5D5C66)).lineLimit(1)
                }
            }
            Text(sentence).font(RealFont.inter(25, .semibold)).foregroundStyle(Color(argb: 0xFF6C6B76))
                .lineSpacing(4).padding(.top, 18).padding(.leading, 2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 18)
    }
}

/// Keşfet's top (also Günlük, Moods, Puanla): the tile's own glow colours, edge to edge and up under the status bar,
/// rounded only at the bottom. The title rises in like the tile it came from.
struct YasamDiscoveryHero<Trailing: View>: View {
    let title: String
    let tone: YasamGlowTone
    var caption: String? = nil
    var subtitle: String? = nil
    var backLabel = t("Ben ekranına dön")
    var height: CGFloat = 150
    var onBack: () -> Void
    @ViewBuilder var trailing: () -> Trailing
    @State private var shown = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .bottomLeading) {
                GeometryReader { g in
                    ZStack {
                        LinearGradient(stops: [.init(color: tone.base, location: 0), .init(color: tone.base.opacity(0.85), location: 0.55),
                                               .init(color: tone.deep.opacity(0.55), location: 1)], startPoint: .top, endPoint: .bottom)
                        RadialGradient(colors: [tone.light.opacity(0.55), .clear], center: UnitPoint(x: 0.82, y: 0.2),
                                       startRadius: 0, endRadius: g.size.width * 0.55)
                    }
                    .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 34, bottomTrailingRadius: 34, style: .continuous))
                    .padding(.top, -200)
                }
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).font(RealFont.inter(34, .bold)).kerning(-0.6).foregroundStyle(tone.text)
                    if let caption { Text(caption).font(RealFont.inter(13, .medium)).foregroundStyle(tone.text.opacity(0.7)) }
                }
                .padding(.leading, 22).padding(.bottom, 18)
                .offset(y: shown ? 0 : 12).opacity(shown ? 1 : 0)
                HStack {
                    Spacer()
                    trailing()
                }
                .padding(.trailing, 22).padding(.bottom, 18)
            }
            .frame(height: height)
            .overlay(alignment: .topLeading) {
                Button(action: onBack) {
                    RealSymbol("chevron.left", size: 19.5).foregroundStyle(.white)
                        .frame(width: 40, height: 40).liquidGlass(Circle(), role: .chrome)
                }
                .buttonStyle(.plain)
                .padding(.leading, 16).padding(.top, 8)
                .accessibilityLabel(backLabel)
            }
            if let subtitle {
                Text(subtitle).font(RealFont.inter(15)).foregroundStyle(YasamTone.muted)
                    .padding(.leading, 22).padding(.top, 12).padding(.bottom, 8)
            }
        }
        .onAppear { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.46).delay(0.06)) { shown = true } }
    }
}

extension YasamDiscoveryHero where Trailing == EmptyView {
    init(title: String, tone: YasamGlowTone, caption: String? = nil, subtitle: String? = nil, backLabel: String = t("Ben ekranına dön"),
         height: CGFloat = 150, onBack: @escaping () -> Void) {
        self.init(title: title, tone: tone, caption: caption, subtitle: subtitle, backLabel: backLabel, height: height,
                  onBack: onBack, trailing: { EmptyView() })
    }
}

/// "Kayıtlar" pill used on Kahve, Alkol and Su.
struct YasamHistoryButton: View {
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(t("Kayıtlar")).font(RealFont.inter(14, .semibold)).foregroundStyle(.white)
                .padding(.horizontal, 16).padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: 22).fill(.white.opacity(0.06)))
                .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(.white.opacity(0.16), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Me's own round back button (glass), used by its sub-screens.
struct YasamBackButton: View {
    var label = t("Geri")
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            RealSymbol("chevron.left", size: 19.5).foregroundStyle(.white)
                .frame(width: 40, height: 40).liquidGlass(Circle(), role: .chrome)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}
