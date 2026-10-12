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

/// cardContentRows: a block joins the previous row only if it starts at or after the previous block's end column.
func cardContentRows(_ keys: [String], _ layout: [String: CardBlockPlacement]) -> [[String]] {
    var rows: [[String]] = []
    var seen = Set<String>()
    for key in keys where seen.insert(key).inserted {
        let p = (layout[key] ?? CardBlockPlacement()).normalized
        if var prev = rows.last, let lastKey = prev.last, prev.count < CardBlockPlacement.maxPerRow {
            let last = (layout[lastKey] ?? CardBlockPlacement()).normalized
            if last.column + last.span <= p.column {
                prev.append(key)
                rows[rows.count - 1] = prev
                continue
            }
        }
        rows.append([key])
    }
    return rows
}

enum CardDropZone { case before, after, left, right }

/// applyCardDrop: Left/Right joins the target's row (1 target → halves, 2 targets → thirds); a full row (3) or
/// Before/After places the block as its own row before/after the target's row. Nothing overlaps or hides.
func applyCardDrop(_ keys: [String], _ layout: [String: CardBlockPlacement], moving: String, target: String,
                   zone: CardDropZone) -> ([String], [String: CardBlockPlacement]) {
    guard moving != target, keys.contains(moving), keys.contains(target) else { return (keys, layout) }
    let rowKeys = (cardContentRows(keys, layout).first { $0.contains(target) } ?? [target]).filter { $0 != moving }
    let side = zone == .left || zone == .right
    let without = keys.filter { $0 != moving }
    if side && rowKeys.count < CardBlockPlacement.maxPerRow {
        var order = without
        let at = (order.firstIndex(of: target) ?? order.count) + (zone == .right ? 1 : 0)
        order.insert(moving, at: at)
        let newRow = order.filter { $0 == moving || rowKeys.contains($0) }
        let span = CardBlockPlacement.units / newRow.count
        var next = layout
        for (i, k) in newRow.enumerated() {
            var p = (layout[k] ?? CardBlockPlacement()).normalized
            p.span = span; p.column = i * span
            next[k] = p.normalized
        }
        return (order, next)
    }
    let before = zone == .before || zone == .left
    var order = without
    let at = before ? (order.firstIndex(of: rowKeys.first ?? target) ?? 0) : ((order.firstIndex(of: rowKeys.last ?? target) ?? order.count - 1) + 1)
    order.insert(moving, at: min(at, order.count))
    var next = layout
    var base = (layout[moving] ?? CardBlockPlacement()).normalized
    base.column = 0
    next[moving] = base.normalized
    return (order, next)
}

/// Block height chosen with the bottom handle (LocalCardBlockHeight).
private struct CardBlockHeightKey: EnvironmentKey { static let defaultValue: CGFloat = 0 }
extension EnvironmentValues {
    var cardBlockHeight: CGFloat {
        get { self[CardBlockHeightKey.self] }
        set { self[CardBlockHeightKey.self] = newValue }
    }
}

private struct CellFrameKey: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue()) { _, n in n } }
}

/// CardCompositionGrid: rows from cardContentRows, blocks placed at their grid column. While editing every block gets
/// a right-edge width handle (1/6 steps), a bottom height handle (80–800pt) and long-press-to-move with Android drop zones.
struct CardBlockGrid<Block: View>: View {
    let keys: [String]
    let layout: [String: CardBlockPlacement]
    let editing: Bool
    let onChange: (String, CardBlockPlacement) -> Void
    var onDrop: ([String], [String: CardBlockPlacement]) -> Void = { _, _ in }
    @ViewBuilder let block: (String) -> Block

    @State private var selected: String?
    @State private var dragStart: CardBlockPlacement?
    @State private var dragStartHeight: CGFloat = 0
    @State private var frames: [String: CGRect] = [:]
    @State private var moving: String?
    @State private var moveOffset: CGSize = .zero
    @State private var totalHeight: CGFloat = 200
    private let gutter: CGFloat = 12

    var body: some View {
        GeometryReader { geo in
            let unit = (geo.size.width + gutter) / CGFloat(CardBlockPlacement.units)
            VStack(alignment: .leading, spacing: gutter) {
                ForEach(cardContentRows(keys, layout), id: \.self) { row in
                    ZStack(alignment: .topLeading) {
                        ForEach(row, id: \.self) { key in
                            cell(key, unit: unit)
                                .padding(.leading, CGFloat(placement(key).column) * unit)
                                .zIndex(moving == key ? 5 : 0)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .zIndex(row.contains(moving ?? "") ? 5 : 0)
                }
            }
            .coordinateSpace(name: "cardGrid")
            .onPreferenceChange(CellFrameKey.self) { frames = $0 }
            .background(GeometryReader { g in Color.clear.preference(key: GridHeightKey.self, value: g.size.height) })
        }
        .frame(height: totalHeight)
        .onPreferenceChange(GridHeightKey.self) { totalHeight = $0 }
        .animation(.spring(response: 0.3, dampingFraction: 0.86), value: keys)
    }

    private func placement(_ key: String) -> CardBlockPlacement { (layout[key] ?? CardBlockPlacement()).normalized }

    @ViewBuilder
    private func cell(_ key: String, unit: CGFloat) -> some View {
        let p = placement(key)
        let width = max(1, CGFloat(p.span) * unit - gutter)
        let minH = CGFloat(p.minHeight)
        let isMoving = moving == key
        block(key)
            .environment(\.cardBlockHeight, minH)
            .frame(width: width)
            .frame(minHeight: minH > 0 ? minH : nil, alignment: .top)
            .background(GeometryReader { g in
                Color.clear.preference(key: CellFrameKey.self, value: [key: g.frame(in: .named("cardGrid"))])
            })
            .overlay {
                if editing && selected == key {
                    RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.white.opacity(0.42), lineWidth: 1).allowsHitTesting(false)
                }
            }
            .overlay(alignment: .trailing) { if editing && !isMoving { widthHandle(key, unit: unit) } }
            .overlay(alignment: .bottom) { if editing && !isMoving { heightHandle(key) } }
            .opacity(isMoving ? 0.88 : 1)
            .scaleEffect(isMoving ? 1.02 : 1)
            .offset(isMoving ? moveOffset : .zero)
            .simultaneousGesture(moveGesture(key), including: editing ? .all : .subviews)
    }

    /// One owner per drag: long-press lifts the block, the drag moves it, release applies applyCardDrop.
    private func moveGesture(_ key: String) -> some Gesture {
        LongPressGesture(minimumDuration: 0.4)
            .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .named("cardGrid")))
            .onChanged { value in
                switch value {
                case .first(true):
                    if moving == nil {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        withAnimation(.easeOut(duration: 0.15)) { moving = key; selected = key }
                    }
                case .second(true, let drag?):
                    moving = key
                    moveOffset = drag.translation
                default: break
                }
            }
            .onEnded { value in
                defer {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.86)) { moving = nil; moveOffset = .zero }
                }
                guard case .second(true, let drag?) = value, let origin = frames[key] else { return }
                let point = CGPoint(x: origin.midX + drag.translation.width, y: origin.midY + drag.translation.height)
                guard let hit = frames.first(where: { $0.key != key && $0.value.contains(point) }) else { return }
                let target = hit.key, rect = hit.value
                let fx = (point.x - rect.minX) / max(1, rect.width)
                let zone: CardDropZone = fx < 0.25 ? .left : (fx > 0.75 ? .right : (point.y < rect.midY ? .before : .after))
                let (k, l) = applyCardDrop(keys, layout, moving: key, target: target, zone: zone)
                withAnimation(.spring(response: 0.3, dampingFraction: 0.86)) { onDrop(k, l) }
            }
    }

    private func widthHandle(_ key: String, unit: CGFloat) -> some View {
        handle(horizontal: true, highlighted: selected == key)
            .frame(width: 30)
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .highPriorityGesture(DragGesture(minimumDistance: 2).onChanged { v in
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
            .highPriorityGesture(DragGesture(minimumDistance: 2).onChanged { v in
                if dragStart == nil { dragStart = placement(key); dragStartHeight = frames[key]?.height ?? 120; selected = key }
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
