import SwiftUI

/// Pull-down-to-close for full screen covers (Ayarlar, Pano, Yaşam), like the poster detail: at the top of the page,
/// pulling further shrinks the page a little with rounded corners and past [PullToClose.threshold] it closes.
/// The screen puts a [PullToCloseMarker] first in its scroll content; the cover applies `.pullToClose { … }`.
struct PullToCloseMarker: View {
    var body: some View {
        Color.clear.frame(height: 0)
            .background(GeometryReader { g in
                Color.clear.preference(key: PullToCloseKey.self, value: g.frame(in: .named(PullToClose.space)).minY)
            })
    }
}

struct PullToCloseKey: PreferenceKey {
    static var defaultValue: CGFloat?
    static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) { value = nextValue() ?? value }
}

struct PullToClose: ViewModifier {
    static let space = "pullToClose"
    static let threshold: CGFloat = 110
    let onClose: () -> Void
    @State private var pull: CGFloat = 0
    @State private var restTop: CGFloat?
    @State private var closing = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        let p = min(pull / Self.threshold, 1)
        content
            .coordinateSpace(name: Self.space)
            .clipShape(RoundedRectangle(cornerRadius: 44 * min(1, pull / 40), style: .continuous))
            .scaleEffect(reduceMotion ? 1 : 1 - p * 0.06, anchor: .bottom)
            .onPreferenceChange(PullToCloseKey.self) { y in
                guard let y else { return }
                let rest = restTop ?? y
                if restTop == nil { restTop = y }
                pull = max(0, y - rest)
                if pull >= Self.threshold && !closing {
                    closing = true
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    onClose()
                }
            }
    }
}

extension View {
    func pullToClose(_ onClose: @escaping () -> Void) -> some View { modifier(PullToClose(onClose: onClose)) }
}
