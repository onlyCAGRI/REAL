import SwiftUI

/// CardContentLayout.kt: 6-unit grid placement of a detail block (full = 6, half = 3, third = 2, two thirds = 4).
struct CardBlockPlacement: Codable, Hashable {
    var span: Int = CardBlockPlacement.units
    var column: Int = 0
    var gapBefore: Int = 12
    var minHeight: Int = 0
    var top: Int?
    var hideActions: Bool = false
    var textAlignment: String = "start"
    var textBold: Bool = false
    var textItalic: Bool = false

    static let units = 6
    static let maxPerRow = 3

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        span = try c.decodeIfPresent(Int.self, forKey: .span) ?? Self.units
        column = try c.decodeIfPresent(Int.self, forKey: .column) ?? 0
        gapBefore = try c.decodeIfPresent(Int.self, forKey: .gapBefore) ?? 12
        minHeight = try c.decodeIfPresent(Int.self, forKey: .minHeight) ?? 0
        top = try c.decodeIfPresent(Int.self, forKey: .top)
        hideActions = try c.decodeIfPresent(Bool.self, forKey: .hideActions) ?? false
        textAlignment = try c.decodeIfPresent(String.self, forKey: .textAlignment) ?? "start"
        textBold = try c.decodeIfPresent(Bool.self, forKey: .textBold) ?? false
        textItalic = try c.decodeIfPresent(Bool.self, forKey: .textItalic) ?? false
    }

    var normalized: CardBlockPlacement {
        var p = self
        p.span = min(max(span, 1), Self.units)
        p.column = min(max(column, 0), Self.units - p.span)
        p.gapBefore = min(max(gapBefore, 0), 160)
        p.minHeight = min(max(minHeight, 0), 800)
        return p
    }
}

/// Block height chosen with the bottom handle (LocalCardBlockHeight).
private struct CardBlockHeightKey: EnvironmentKey { static let defaultValue: CGFloat = 0 }
extension EnvironmentValues {
    var cardBlockHeight: CGFloat {
        get { self[CardBlockHeightKey.self] }
        set { self[CardBlockHeightKey.self] = newValue }
    }
}

/// CardCompositionGrid (flowing variant): blocks are packed into rows by span in saved order; while editing every block
/// gets a right-edge width handle (1/6 steps) and a bottom height handle (80–800pt), like the Android detail editor.
struct CardBlockGrid<Block: View>: View {
    let keys: [String]
    let layout: [String: CardBlockPlacement]
    let editing: Bool
    let onChange: (String, CardBlockPlacement) -> Void
    @ViewBuilder let block: (String) -> Block

    @State private var selected: String?
    @State private var dragStart: CardBlockPlacement?
    @State private var dragStartHeight: CGFloat = 0
    @State private var heights: [String: CGFloat] = [:]
    private let gutter: CGFloat = 12

    var body: some View {
        GeometryReader { geo in
            let unit = (geo.size.width + gutter) / CGFloat(CardBlockPlacement.units)
            VStack(alignment: .leading, spacing: gutter) {
                ForEach(rows, id: \.self) { row in
                    HStack(alignment: .top, spacing: gutter) {
                        ForEach(row, id: \.self) { key in
                            cell(key, unit: unit)
                        }
                    }
                }
            }
            .background(GeometryReader { g in Color.clear.preference(key: GridHeightKey.self, value: g.size.height) })
        }
        .frame(height: totalHeight)
        .onPreferenceChange(GridHeightKey.self) { totalHeight = $0 }
    }

    @State private var totalHeight: CGFloat = 200

    private func placement(_ key: String) -> CardBlockPlacement { (layout[key] ?? CardBlockPlacement()).normalized }

    private var rows: [[String]] {
        var out: [[String]] = []
        var current: [String] = []
        var used = 0
        for k in keys {
            let s = placement(k).span
            if !current.isEmpty && (used + s > CardBlockPlacement.units || current.count >= CardBlockPlacement.maxPerRow) {
                out.append(current); current = []; used = 0
            }
            current.append(k); used += s
        }
        if !current.isEmpty { out.append(current) }
        return out
    }

    @ViewBuilder
    private func cell(_ key: String, unit: CGFloat) -> some View {
        let p = placement(key)
        let width = max(1, CGFloat(p.span) * unit - gutter)
        let minH = CGFloat(p.minHeight)
        block(key)
            .environment(\.cardBlockHeight, minH)
            .frame(width: width)
            .frame(minHeight: minH > 0 ? minH : nil, alignment: .top)
            .background(GeometryReader { g in Color.clear.onAppear { heights[key] = g.size.height }.onChange(of: g.size.height) { _, h in heights[key] = h } })
            .overlay {
                if editing && selected == key {
                    RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.white.opacity(0.42), lineWidth: 1).allowsHitTesting(false)
                }
            }
            .overlay(alignment: .trailing) { if editing { widthHandle(key, unit: unit) } }
            .overlay(alignment: .bottom) { if editing { heightHandle(key) } }
    }

    private func widthHandle(_ key: String, unit: CGFloat) -> some View {
        handle(horizontal: true, highlighted: selected == key)
            .frame(width: 30)
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 2).onChanged { v in
                if dragStart == nil { dragStart = placement(key); selected = key }
                guard let start = dragStart else { return }
                let end = Int((CGFloat(start.column + start.span) + v.translation.width / max(unit, 1)).rounded())
                let span = min(max(end - start.column, min(2, CardBlockPlacement.units - start.column)), CardBlockPlacement.units - start.column)
                if span != placement(key).span {
                    var next = placement(key); next.span = span
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) { onChange(key, next.normalized) }
                }
            }.onEnded { _ in dragStart = nil })
            .accessibilityLabel("Genişliği değiştir")
    }

    private func heightHandle(_ key: String) -> some View {
        handle(horizontal: false, highlighted: selected == key)
            .frame(height: 30)
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 2).onChanged { v in
                if dragStart == nil { dragStart = placement(key); dragStartHeight = heights[key] ?? 120; selected = key }
                let h = Int(min(max(dragStartHeight + v.translation.height, 80), 800).rounded())
                if h != placement(key).minHeight {
                    var next = placement(key); next.minHeight = h
                    onChange(key, next.normalized)
                }
            }.onEnded { _ in dragStart = nil })
            .accessibilityLabel("Yüksekliği değiştir")
    }

    private func handle(horizontal: Bool, highlighted: Bool) -> some View {
        Capsule().fill(.white.opacity(highlighted ? 0.85 : 0.45))
            .frame(width: horizontal ? 4 : 36, height: horizontal ? 36 : 4)
            .padding(horizontal ? 5 : 4)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: horizontal ? .trailing : .bottom)
    }
}

private struct GridHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}
