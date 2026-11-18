import SwiftUI

/// CardNews.kt: cards someone else just added to a shared category, or sent you. "1 yeni" on the category's tile at
/// home, a red categories dot under the deck and a red breathing light on the card. A card stops being new when it is
/// opened or when its category is left. Device-local (card id → its category, -1 for none).
@MainActor
@Observable
final class CardNews {
    static let shared = CardNews()
    static let color = Color(argb: 0xFFFF4D6A)
    private static let key = "real_card_news"
    private static let keep = 200

    private(set) var items: [Int64: Int64] = {
        var out: [Int64: Int64] = [:]
        for e in UserDefaults.standard.stringArray(forKey: CardNews.key) ?? [] {
            let p = e.split(separator: ":")
            if p.count == 2, let a = Int64(p[0]), let b = Int64(p[1]) { out[a] = b }
        }
        return out
    }()

    private func save(_ next: [Int64: Int64]) {
        let kept = Dictionary(uniqueKeysWithValues: next.sorted { $0.key > $1.key }.prefix(Self.keep).map { ($0.key, $0.value) })
        guard kept != items else { return }
        items = kept
        UserDefaults.standard.set(kept.map { "\($0.key):\($0.value)" }, forKey: Self.key)
    }

    func add(_ cardId: Int64, category: Int64?) { var n = items; n[cardId] = category ?? -1; save(n) }
    func seen(_ cardId: Int64) { if items[cardId] != nil { var n = items; n[cardId] = nil; save(n) } }
    /// Leaving a category: what was new in it has been seen.
    func seenCategory(_ categoryId: Int64) { save(items.filter { $0.value != categoryId }) }
    /// Cards that no longer exist (deleted, or the account changed) stop counting.
    func prune(existing: Set<Int64>) { save(items.filter { existing.contains($0.key) }) }

    func count(in categoryId: Int64) -> Int { items.values.filter { $0 == categoryId }.count }
    var anyInCategories: Bool { items.values.contains { $0 != -1 } }
    func glow(_ r: ReminderItem) -> Color? { !r.isCompleted && !r.isDivider && items[r.id] != nil ? Self.color : nil }
}

/// "1 yeni": comes in like a bubble (overshooting scale while the row opens, so the description slides right) with a
/// ring that leaves it as it lands.
struct NewCardsBubble: View {
    let count: Int
    @State private var shown = false
    @State private var ring: CGFloat = 0
    @State private var width: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Text(t("{0} yeni", max(count, 1)))
            .font(RealFont.inter(13, .bold))
            .foregroundStyle(.white)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Capsule().fill(CardNews.color))
            .overlay(Capsule().strokeBorder(CardNews.color, lineWidth: 1))
            .background(GeometryReader { g in
                Color.clear.onAppear {
                    width = g.size.width
                    if reduceMotion { shown = true; ring = 1; return }
                    // The row opens (the description slides right) while the bubble overshoots into place.
                    withAnimation(.spring(response: 0.42, dampingFraction: 0.45)) { shown = true }
                    withAnimation(.easeOut(duration: 0.75).delay(0.16)) { ring = 1 }
                }
            })
            .background(
                Capsule().stroke(CardNews.color.opacity(shown ? 0.7 * (1 - ring) : 0), lineWidth: 2)
                    .padding(.horizontal, -12 * ring).padding(.vertical, -7 * ring)
            )
            .scaleEffect(shown ? 1 : 0.2)
            .opacity(shown ? 1 : 0)
            .frame(width: shown ? width : 0, alignment: .leading)
            .accessibilityLabel(t("{0} yeni kart", max(count, 1)))
    }
}
