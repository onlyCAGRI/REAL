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
        if let t = top { p.top = min(max(t, 0), cardMaxTop) }
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

// MARK: - Free placement geometry (CardFreePlacement.kt + CardDetailSizing.kt)

let cardVerticalGrid: CGFloat = 8
let cardMaxTop = 16000

private func snapUp(_ v: CGFloat) -> CGFloat { (v / cardVerticalGrid).rounded(.up) * cardVerticalGrid }
private func snapNearest(_ v: CGFloat) -> CGFloat { (v / cardVerticalGrid).rounded() * cardVerticalGrid }

/// cardGridMeasuredBoxes: placed blocks (with a saved top) first, then the flowing rows below them.
func cardGridMeasuredBoxes(_ keys: [String], _ layout: [String: CardBlockPlacement], sizes: [String: CGSize], unit: CGFloat) -> [String: CGRect] {
    var result: [String: CGRect] = [:]
    var bottom: CGFloat = 0
    func place(_ key: String, _ top: CGFloat) {
        let p = (layout[key] ?? CardBlockPlacement()).normalized
        let size = sizes[key] ?? .zero
        result[key] = CGRect(x: CGFloat(p.column) * unit, y: top, width: size.width, height: size.height)
    }
    for key in keys where layout[key]?.top != nil {
        let top = CGFloat((layout[key] ?? CardBlockPlacement()).normalized.top ?? 0)
        place(key, top)
        bottom = max(bottom, top + (sizes[key]?.height ?? 0))
    }
    var anchor = snapUp(bottom)
    for row in cardContentRows(keys.filter { layout[$0]?.top == nil }, layout) {
        var rowBottom = anchor
        for key in row {
            let top = anchor + CGFloat((layout[key] ?? CardBlockPlacement()).normalized.gapBefore)
            place(key, top)
            rowBottom = max(rowBottom, top + (sizes[key]?.height ?? 0))
        }
        anchor = snapUp(rowBottom)
    }
    return result
}

private func intersects(_ a: CGRect, _ b: CGRect) -> Bool {
    a.minX < b.maxX - 0.5 && a.maxX > b.minX + 0.5 && a.minY < b.maxY - 0.5 && a.maxY > b.minY + 0.5
}

/// cardDetailNonOverlappingBoxes: grown text or old layouts never overlap; later blocks move down in visual order.
func cardDetailNonOverlappingBoxes(_ boxes: [String: CGRect]) -> [String: CGRect] {
    var result: [String: CGRect] = [:]
    for (key, initial) in boxes.sorted(by: { $0.value.minY < $1.value.minY }) {
        var box = initial
        while true {
            let collisions = result.values.filter { intersects(box, $0) }
            guard let lowest = collisions.map(\.maxY).max() else { break }
            box.origin.y = snapUp(lowest)
        }
        result[key] = box
    }
    return result
}

/// cardDetailBoxFits: a candidate rectangle inside the card and clear of every neighbour.
func cardDetailBoxFits(_ key: String, _ candidate: CGRect, _ boxes: [String: CGRect]) -> Bool {
    guard candidate.minX >= -0.5, candidate.minY >= 0, candidate.width > 0, candidate.height > 0 else { return false }
    return !boxes.contains { $0.key != key && intersects(candidate, $0.value) }
}

/// cardDetailMinimumSpan: text-like blocks keep a readable width.
func cardDetailMinimumSpan(_ key: String, width: CGFloat) -> Int {
    let kind = key.split(separator: ":").first.map(String.init) ?? key
    let minimum: CGFloat = ["text", "checklist", "journal", "voice", "files", "location"].contains(kind) ? 224 : (kind == "card_stack" ? 156 : 88)
    let unit = max((width + 12) / CGFloat(CardBlockPlacement.units), 1)
    return min(max(Int(((minimum + 12) / unit).rounded(.up)), 2), CardBlockPlacement.units)
}

/// placeCardInFreeSpace: drop into any empty spot without moving the neighbours (their flowing tops are frozen).
func placeCardInFreeSpace(_ keys: [String], _ layout: [String: CardBlockPlacement], _ boxes: [String: CGRect],
                          moving: String, left: CGFloat, top: CGFloat, unit: CGFloat) -> [String: CardBlockPlacement]? {
    guard keys.contains(moving), keys.allSatisfy({ boxes[$0] != nil }), unit > 0, left.isFinite, top.isFinite,
          let box = boxes[moving] else { return nil }
    let p = (layout[moving] ?? CardBlockPlacement()).normalized
    let column = min(max(Int((left / unit).rounded()), 0), CardBlockPlacement.units - p.span)
    let snappedTop = snapNearest(min(max(top, 0), CGFloat(cardMaxTop)))
    let candidate = CGRect(x: CGFloat(column) * unit, y: snappedTop, width: box.width, height: box.height)
    guard cardDetailBoxFits(moving, candidate, boxes) else { return nil }
    var next: [String: CardBlockPlacement] = [:]
    for key in keys {
        var old = (layout[key] ?? CardBlockPlacement()).normalized
        if key == moving { old.column = column; old.top = Int(snappedTop) }
        else if old.top == nil { old.top = min(max(Int((boxes[key]?.minY ?? 0).rounded()), 0), cardMaxTop) }
        next[key] = old
    }
    return next
}

/// cardTopEdgeResize: the bottom edge stays, the top moves (8pt grid); neighbours are frozen like a free move.
func cardTopEdgeResize(_ keys: [String], _ layout: [String: CardBlockPlacement], _ boxes: [String: CGRect],
                       moving: String, requestedTop: CGFloat, minHeight: CGFloat) -> [String: CardBlockPlacement]? {
    guard let box = boxes[moving], keys.contains(moving), requestedTop.isFinite else { return nil }
    let bottom = box.maxY
    let top = min(max(snapNearest(requestedTop), 0), max(bottom - minHeight, 0))
    let candidate = CGRect(x: box.minX, y: top, width: box.width, height: bottom - top)
    guard cardDetailBoxFits(moving, candidate, boxes) else { return nil }
    var next: [String: CardBlockPlacement] = [:]
    for key in keys {
        var old = (layout[key] ?? CardBlockPlacement()).normalized
        if key == moving { old.top = Int(top); old.minHeight = Int((bottom - top).rounded()) }
        else if old.top == nil { old.top = min(max(Int((boxes[key]?.minY ?? 0).rounded()), 0), cardMaxTop) }
        next[key] = old
    }
    return next
}

private func isCatalog(_ key: String) -> Bool { key.hasPrefix("music:") || key.hasPrefix("media:") || key == "music" || key == "media" }

// MARK: - Layout

private struct BlockKeyValue: LayoutValueKey { static let defaultValue = "" }

/// CardCompositionGrid's measure pass: each block at its grid column / saved top, never overlapping.
private struct CardFreeLayout: Layout {
    let layout: [String: CardBlockPlacement]
    let gutter: CGFloat

    private func boxes(_ width: CGFloat, _ subviews: Subviews) -> [String: CGRect] {
        let unit = (width + gutter) / CGFloat(CardBlockPlacement.units)
        var sizes: [String: CGSize] = [:]
        var keys: [String] = []
        for s in subviews {
            let key = s[BlockKeyValue.self]
            let p = (layout[key] ?? CardBlockPlacement()).normalized
            let w = max(1, CGFloat(p.span) * unit - gutter)
            sizes[key] = CGSize(width: w, height: s.sizeThatFits(ProposedViewSize(width: w, height: nil)).height)
            keys.append(key)
        }
        return cardDetailNonOverlappingBoxes(cardGridMeasuredBoxes(keys, layout, sizes: sizes, unit: unit))
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 360
        let bottom = boxes(width, subviews).values.map(\.maxY).max() ?? 0
        return CGSize(width: width, height: snapUp(bottom))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let geometry = boxes(bounds.width, subviews)
        for s in subviews {
            guard let box = geometry[s[BlockKeyValue.self]] else { continue }
            s.place(at: CGPoint(x: bounds.minX + box.minX, y: bounds.minY + box.minY),
                    proposal: ProposedViewSize(width: box.width, height: box.height))
        }
    }
}

private struct CellFrameKey: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue()) { _, n in n } }
}

// MARK: - Grid view

/// CardCompositionGrid (Android): free placement while editing. Long-press a block and drag it to any free spot
/// (columns of the 6-unit grid, 8pt rows); it never pushes or overlaps its neighbours. Four edge handles: right and
/// left change the width (1/6 steps), bottom the height, top moves the top edge with the bottom fixed. Music / film
/// cards keep their catalog sizing (right and bottom only). Tap a block to select it.
struct CardBlockGrid<Block: View>: View {
    let keys: [String]
    let layout: [String: CardBlockPlacement]
    let editing: Bool
    let onChange: (String, CardBlockPlacement) -> Void
    var onDrop: ([String], [String: CardBlockPlacement]) -> Void = { _, _ in }
    @ViewBuilder let block: (String) -> Block

    @State private var selected: String?
    @State private var frames: [String: CGRect] = [:]
    @State private var moving: String?
    @State private var moveOffset: CGSize = .zero
    @State private var gridWidth: CGFloat = 0
    @State private var handleStart: (placement: CardBlockPlacement, frames: [String: CGRect], layout: [String: CardBlockPlacement])?
    private let gutter: CGFloat = 12

    private var unit: CGFloat { max((gridWidth + gutter) / CGFloat(CardBlockPlacement.units), 1) }

    var body: some View {
        CardFreeLayout(layout: layout, gutter: gutter) {
            ForEach(keys, id: \.self) { key in
                cell(key).zIndex(moving == key ? 5 : 0).layoutValue(key: BlockKeyValue.self, value: key)
            }
        }
        .coordinateSpace(name: "cardGrid")
        .onPreferenceChange(CellFrameKey.self) { frames = $0 }
        .background(GeometryReader { g in
            Color.clear.onAppear { gridWidth = g.size.width }.onChange(of: g.size.width) { _, w in gridWidth = w }
        })
        .background(alignment: .topLeading) { if moving != nil { guides } }
        // Room to drop below the current bottom while a block is lifted.
        .padding(.bottom, moving.flatMap { frames[$0] }.map { max(0, $0.maxY + moveOffset.height - (frames.values.map(\.maxY).max() ?? 0)) } ?? 0)
        .animation(.spring(response: 0.3, dampingFraction: 0.9), value: layout)
        .animation(.spring(response: 0.3, dampingFraction: 0.9), value: keys)
        .onChange(of: editing) { _, on in if !on { selected = nil; moving = nil } }
    }

    private func placement(_ key: String) -> CardBlockPlacement { (layout[key] ?? CardBlockPlacement()).normalized }

    /// Faint 8pt rows and grid columns, only while a block is being moved.
    private var guides: some View {
        Canvas { ctx, size in
            var path = Path()
            var y: CGFloat = 0
            while y <= size.height { path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: size.width, y: y)); y += cardVerticalGrid }
            for c in 1..<CardBlockPlacement.units {
                let x = CGFloat(c) * unit
                path.move(to: CGPoint(x: x, y: 0)); path.addLine(to: CGPoint(x: x, y: size.height))
            }
            ctx.stroke(path, with: .color(.white.opacity(0.045)), lineWidth: 1)
        }
        .allowsHitTesting(false)
    }

    /// The lifted block snaps to the grid as it moves (column steps, 8pt rows), like Android.
    private func snappedOffset(_ key: String) -> CGSize {
        guard let origin = frames[key] else { return moveOffset }
        let p = placement(key)
        let column = min(max(Int(((CGFloat(p.column) * unit + moveOffset.width) / unit).rounded()), 0), CardBlockPlacement.units - p.span)
        let top = snapNearest(max(origin.minY + moveOffset.height, 0))
        return CGSize(width: CGFloat(column - p.column) * unit, height: top - origin.minY)
    }

    @ViewBuilder
    private func cell(_ key: String) -> some View {
        let p = placement(key)
        let minH = isCatalog(key) ? 0 : CGFloat(p.minHeight)
        let isMoving = moving == key
        block(key)
            .environment(\.cardBlockHeight, minH)
            .frame(maxWidth: .infinity, minHeight: minH > 0 ? minH : nil, alignment: .top)
            .background(GeometryReader { g in
                Color.clear.preference(key: CellFrameKey.self, value: [key: g.frame(in: .named("cardGrid"))])
            })
            .overlay {
                if editing && selected == key {
                    RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.white.opacity(0.42), lineWidth: 1).allowsHitTesting(false)
                }
            }
            .overlay(alignment: .trailing) { if editing && !isMoving { rightHandle(key) } }
            .overlay(alignment: .bottom) { if editing && !isMoving { bottomHandle(key) } }
            .overlay(alignment: .leading) { if editing && !isMoving && !isCatalog(key) { leftHandle(key) } }
            .overlay(alignment: .top) { if editing && !isMoving && !isCatalog(key) { topHandle(key) } }
            .opacity(isMoving ? 0.88 : 1)
            .scaleEffect(isMoving ? 1.025 : 1)
            .offset(isMoving ? snappedOffset(key) : .zero)
            .simultaneousGesture(TapGesture().onEnded { if editing { selected = key } }, including: editing ? .all : .subviews)
            .simultaneousGesture(moveGesture(key), including: editing ? .all : .subviews)
    }

    /// One owner per drag: long-press lifts the block, the drag moves it, release drops it in free space.
    private func moveGesture(_ key: String) -> some Gesture {
        LongPressGesture(minimumDuration: 0.35)
            .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .named("cardGrid")))
            .onChanged { value in
                guard editing else { return }
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
                defer { withAnimation(.spring(response: 0.3, dampingFraction: 0.9)) { moving = nil; moveOffset = .zero } }
                guard editing, case .second(true, let drag?) = value, let origin = frames[key],
                      abs(drag.translation.width) + abs(drag.translation.height) >= 1 else { return }
                if let next = placeCardInFreeSpace(keys, layout, frames, moving: key,
                                                   left: origin.minX + drag.translation.width, top: origin.minY + drag.translation.height, unit: unit) {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.9)) { onDrop(keys, next) }
                }
            }
    }

    private func beginHandle(_ key: String) {
        if handleStart == nil { handleStart = (placement(key), frames, layout); selected = key }
    }

    /// Right edge: width in 1/6 steps, growing to the right (catalog cards snap to 2 / 3 / 4 / 6).
    private func rightHandle(_ key: String) -> some View {
        handlePill(horizontal: true, highlighted: selected == key, alignment: .trailing)
            .frame(width: 30).frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .highPriorityGesture(DragGesture(minimumDistance: 2).onChanged { v in
                beginHandle(key)
                guard let start = handleStart else { return }
                let s = start.placement
                let minSpan = cardDetailMinimumSpan(key, width: gridWidth)
                var end = Int((CGFloat(s.column + s.span) + v.translation.width / unit).rounded())
                end = min(max(end, s.column + min(minSpan, CardBlockPlacement.units - s.column)), CardBlockPlacement.units)
                var span = end - s.column
                if isCatalog(key) { span = min([2, 3, 4, 6].min { abs($0 - span) < abs($1 - span) } ?? span, CardBlockPlacement.units - s.column) }
                var now = placement(key)
                guard span != now.span, let box = frames[key] else { return }
                let candidate = CGRect(x: box.minX, y: box.minY, width: max(CGFloat(span) * unit - gutter, 1), height: box.height)
                guard cardDetailBoxFits(key, candidate, frames) else { return }
                now.span = span
                onChange(key, now.normalized)
            }.onEnded { _ in handleStart = nil })
            .accessibilityLabel("Genişliği değiştir")
    }

    /// Left edge: the right edge stays, the block widens / narrows to the left.
    private func leftHandle(_ key: String) -> some View {
        handlePill(horizontal: true, highlighted: selected == key, alignment: .leading)
            .frame(width: 26, height: 96)
            .contentShape(Rectangle())
            .highPriorityGesture(DragGesture(minimumDistance: 2).onChanged { v in
                beginHandle(key)
                guard let start = handleStart, let box = frames[key] else { return }
                let s = start.placement
                let end = s.column + s.span
                let minSpan = cardDetailMinimumSpan(key, width: gridWidth)
                let column = min(max(Int((CGFloat(s.column) + v.translation.width / unit).rounded()), 0), max(end - minSpan, 0))
                var now = placement(key)
                guard column != now.column || end - column != now.span else { return }
                let candidate = CGRect(x: CGFloat(column) * unit, y: box.minY, width: max(CGFloat(end - column) * unit - gutter, 1), height: box.height)
                guard cardDetailBoxFits(key, candidate, frames) else { return }
                now.column = column; now.span = end - column
                onChange(key, now.normalized)
            }.onEnded { _ in handleStart = nil })
            .accessibilityLabel("Sola genişlet")
    }

    /// Bottom edge: height 80–800pt (catalog cards: drag up hides their buttons, down shows them).
    private func bottomHandle(_ key: String) -> some View {
        handlePill(horizontal: false, highlighted: selected == key, alignment: .bottom)
            .frame(height: 30).frame(maxWidth: .infinity)
            .contentShape(Rectangle())
            .highPriorityGesture(DragGesture(minimumDistance: 2).onChanged { v in
                beginHandle(key)
                guard let start = handleStart, let startBox = start.frames[key] else { return }
                var now = placement(key)
                if isCatalog(key) {
                    let hide = v.translation.height <= -8 ? true : (v.translation.height >= 8 ? false : start.placement.hideActions)
                    if hide != now.hideActions { now.hideActions = hide; onChange(key, now.normalized) }
                    return
                }
                let h = Int(min(max(startBox.height + v.translation.height, 80), 800).rounded())
                guard h != now.minHeight, let box = frames[key] else { return }
                let candidate = CGRect(x: box.minX, y: box.minY, width: box.width, height: CGFloat(h))
                guard cardDetailBoxFits(key, candidate, frames) else { return }
                now.minHeight = h
                onChange(key, now.normalized)
            }.onEnded { _ in handleStart = nil })
            .accessibilityLabel("Yüksekliği değiştir")
    }

    /// Top edge: the bottom edge stays where it is, the top moves up / down (neighbours frozen, never overlapped).
    private func topHandle(_ key: String) -> some View {
        handlePill(horizontal: false, highlighted: selected == key, alignment: .top)
            .frame(width: 120, height: 26)
            .contentShape(Rectangle())
            .highPriorityGesture(DragGesture(minimumDistance: 2).onChanged { v in
                beginHandle(key)
                guard let start = handleStart, let startBox = start.frames[key] else { return }
                guard let next = cardTopEdgeResize(keys, start.layout, start.frames, moving: key,
                                                   requestedTop: startBox.minY + v.translation.height, minHeight: 80),
                      next != layout else { return }
                onDrop(keys, next)
            }.onEnded { _ in handleStart = nil })
            .accessibilityLabel("Üst kenarı taşı")
    }

    private func handlePill(horizontal: Bool, highlighted: Bool, alignment: Alignment) -> some View {
        Capsule().fill(.white.opacity(highlighted ? 0.85 : 0.45))
            .frame(width: horizontal ? 4 : 36, height: horizontal ? 36 : 4)
            .padding(horizontal ? 5 : 4)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: alignment)
    }
}
