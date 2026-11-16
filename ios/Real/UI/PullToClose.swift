import SwiftUI

/// Pull-down-to-close for full screen covers that act as sheets (Ayarlar, Pano), like the poster detail: at the top of
/// the page, pulling further shrinks the page a little with rounded corners and past [PullToClose.threshold] it closes.
/// The page's scroll view carries `.pullToCloseSource()` (iOS 18+: it reports how far it is pulled past its top) and,
/// for iOS 17, a [PullToCloseMarker] first in its content; the cover applies `.pullToClose { … }`.
/// Pages that slide in from the right (Me and its screens) close with the back button and a left-edge swipe instead.
struct PullToCloseMarker: View {
    var body: some View {
        Color.clear.frame(height: 0)
            .background(GeometryReader { g in
                Color.clear.preference(key: PullToCloseKey.self, value: g.frame(in: .named(PullToClose.space)).minY)
            })
    }
}

/// iOS 17: the marker's top in the page (rest is its first value).
struct PullToCloseKey: PreferenceKey {
    static var defaultValue: CGFloat?
    static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) { value = nextValue() ?? value }
}

/// iOS 18+: how far the scroll view is pulled down past its top (0 when it is not). Read from the scroll view itself:
/// the marker's position moved with the page while it opened, so its resting point (and the pull) was unreliable.
struct PullToCloseOverscrollKey: PreferenceKey {
    static var defaultValue: CGFloat?
    static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) { value = nextValue() ?? value }
}

private struct PullToCloseSource: ViewModifier {
    @State private var overscroll: CGFloat = 0

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content
                .onScrollGeometryChange(for: CGFloat.self) { geo in
                    max(0, -(geo.contentOffset.y + geo.contentInsets.top))
                } action: { _, value in
                    overscroll = value
                }
                .preference(key: PullToCloseOverscrollKey.self, value: overscroll)
        } else {
            content
        }
    }
}

struct PullToClose: ViewModifier {
    static let space = "pullToClose"
    static let threshold: CGFloat = 110
    let onClose: () -> Void
    @State private var pull: CGFloat = 0
    @State private var restTop: CGFloat?
    @State private var scrollReports = false
    @State private var closing = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        let p = min(pull / Self.threshold, 1)
        content
            .coordinateSpace(name: Self.space)
            // A mask that reaches into the safe areas: clipShape cut the page at the home-indicator inset, so a cover
            // with a clear background showed the screen underneath through a strip at the bottom.
            .mask { RoundedRectangle(cornerRadius: 44 * min(1, pull / 40), style: .continuous).ignoresSafeArea() }
            .scaleEffect(reduceMotion ? 1 : 1 - p * 0.06, anchor: .bottom)
            .onPreferenceChange(PullToCloseOverscrollKey.self) { value in
                guard let value else { return }
                scrollReports = true
                pull = value
                if pull >= Self.threshold && !closing { close() }
            }
            .onPreferenceChange(PullToCloseKey.self) { y in
                guard !scrollReports, let y else { return }
                let rest = restTop ?? y
                if restTop == nil { restTop = y }
                pull = max(0, y - rest)
                if pull >= Self.threshold && !closing { close() }
            }
    }

    private func close() {
        closing = true
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        onClose()
    }
}

extension View {
    func pullToClose(_ onClose: @escaping () -> Void) -> some View { modifier(PullToClose(onClose: onClose)) }
    /// On the page's scroll view: reports how far it is pulled past its top to the enclosing `.pullToClose`.
    func pullToCloseSource() -> some View { modifier(PullToCloseSource()) }
}
