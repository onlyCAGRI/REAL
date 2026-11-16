import SwiftUI
import UIKit

/// Android's system back for screens with no navigation bar (full-screen covers, overlays): a swipe from the left
/// screen edge runs the screen's own back step. A UIScreenEdgePanGestureRecognizer on the hosting view, so taps,
/// scrolling and the back button keep working; it is removed with the screen.
struct EdgeSwipeBack: UIViewRepresentable {
    var enabled = true
    /// Interactive use: the finger's distance from the edge while it moves (0 when it lets go).
    var onDrag: ((CGFloat) -> Void)? = nil
    /// Interactive use: the swipe ended (commit = far or fast enough), with its speed and the page width.
    var onEnd: ((_ commit: Bool, _ velocity: CGFloat, _ width: CGFloat) -> Void)? = nil
    let action: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MarkerView {
        let view = MarkerView()
        view.isUserInteractionEnabled = false
        view.coordinator = context.coordinator
        return view
    }

    func updateUIView(_ view: MarkerView, context: Context) {
        context.coordinator.action = action
        context.coordinator.enabled = enabled
        context.coordinator.onDrag = onDrag
        context.coordinator.onEnd = onEnd
    }

    static func dismantleUIView(_ view: MarkerView, coordinator: Coordinator) { coordinator.detach() }

    final class MarkerView: UIView {
        weak var coordinator: Coordinator?
        override func didMoveToWindow() {
            super.didMoveToWindow()
            if window == nil { coordinator?.detach() } else { coordinator?.attach(near: self) }
        }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var action: () -> Void = {}
        var enabled = true
        var onDrag: ((CGFloat) -> Void)?
        var onEnd: ((Bool, CGFloat, CGFloat) -> Void)?
        private weak var host: UIView?
        private lazy var recognizer: UIScreenEdgePanGestureRecognizer = {
            let r = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(pan(_:)))
            r.edges = .left
            r.delegate = self
            return r
        }()

        /// The nearest view controller's root view: the presented cover, not the screen below it.
        func attach(near view: UIView) {
            var responder: UIResponder? = view
            while let r = responder, !(r is UIViewController) { responder = r.next }
            guard let target = (responder as? UIViewController)?.view ?? view.window, target !== host else { return }
            detach()
            target.addGestureRecognizer(recognizer)
            host = target
        }

        func detach() {
            host?.removeGestureRecognizer(recognizer)
            host = nil
        }

        @objc private func pan(_ r: UIScreenEdgePanGestureRecognizer) {
            guard let v = r.view else { return }
            let t = r.translation(in: v), speed = r.velocity(in: v).x, width = max(1, v.bounds.width)
            switch r.state {
            case .began, .changed:
                guard enabled else { return }
                onDrag?(max(0, t.x))
            case .ended, .cancelled, .failed:
                // Interactive: past a third of the page, or a flick to the right. Trigger only: the old short swipe.
                let commit = enabled && r.state == .ended && (onEnd != nil
                    ? (t.x > width * 0.33 && speed > -200) || (t.x > 24 && speed > 500)
                    : t.x > 70 || (t.x > 24 && speed > 600))
                if commit { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                if let onEnd { onEnd(commit, speed, width) } else if commit { action() }
            default: break
            }
        }

        func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool { enabled }
        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { false }
        /// A paging scroll view (the horizontal book reader) otherwise takes the edge swipe as a page turn.
        func gestureRecognizer(_ g: UIGestureRecognizer, shouldBeRequiredToFailBy other: UIGestureRecognizer) -> Bool {
            other.view is UIScrollView && other is UIPanGestureRecognizer
        }
    }
}

/// iOS 27 back swipe: the page follows the finger from the left edge, the screen it reveals is dimmed less and less,
/// and letting go either carries the page off with the finger's speed (then `action` closes it) or settles it back.
/// For pages that are removed by `action` (a cover, an overlay screen); the page stays off screen until it goes.
struct InteractiveEdgeBack: ViewModifier {
    var enabled: Bool
    let action: () -> Void
    @State private var drag: CGFloat = 0
    @State private var width: CGFloat = 400
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        let progress = min(1, drag / max(1, width))
        content
            // A soft shadow on the page's left edge while it is pulled, so it reads as a sheet above the one below.
            .background(alignment: .leading) {
                LinearGradient(colors: [.clear, .black.opacity(0.4)], startPoint: .leading, endPoint: .trailing)
                    .frame(width: 28).offset(x: -28).opacity(drag > 0 ? 1 - progress : 0).ignoresSafeArea()
                    .allowsHitTesting(false)
            }
            .offset(x: drag)
            .background { Color.black.opacity(drag > 0 ? 0.32 * (1 - progress) : 0).ignoresSafeArea().allowsHitTesting(false) }
            .background(EdgeSwipeBack(enabled: enabled, onDrag: { x in
                var t = Transaction(); t.disablesAnimations = true
                withTransaction(t) { drag = reduceMotion ? 0 : x }
            }, onEnd: end, action: action).frame(width: 0, height: 0))
    }

    private func end(commit: Bool, velocity: CGFloat, pageWidth: CGFloat) {
        width = pageWidth
        guard commit else {
            withAnimation(RealMotion.settle) { drag = 0 }
            return
        }
        guard !reduceMotion, drag > 0 else { action(); return }
        // The finger's speed carries into the spring, relative to the distance left (like UIKit's back swipe).
        let left = max(1, pageWidth - drag)
        withAnimation(.interpolatingSpring(duration: 0.3, bounce: 0, initialVelocity: Double(max(0, velocity) / left))) {
            drag = pageWidth
        } completion: {
            withoutAnimation { action() }
            // Still here after it should have gone (an editing step, a page inside it): bring it back.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { withoutAnimation { drag = 0 } }
        }
    }
}

extension View {
    /// Swipe from the left edge = `action` (the same step as the screen's back button).
    func edgeSwipeBack(enabled: Bool = true, _ action: @escaping () -> Void) -> some View {
        background(EdgeSwipeBack(enabled: enabled, action: action).frame(width: 0, height: 0))
    }

    /// The page follows the swipe from the left edge; `action` removes it (see [InteractiveEdgeBack]).
    func interactiveEdgeBack(enabled: Bool = true, _ action: @escaping () -> Void) -> some View {
        modifier(InteractiveEdgeBack(enabled: enabled, action: action))
    }
}

/// A full-screen cover that moves like a pushed page (iOS 27 Settings, Me): in from the right, out to the right, and
/// the back swipe follows the finger. Present it without the system animation (`withoutAnimation`); `onClosed` takes
/// it away the same way once it has slid off.
struct PushPage<Content: View>: View {
    let onClosed: () -> Void
    @ViewBuilder let content: (_ close: @escaping () -> Void) -> Content
    @State private var shown = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    static var openAnim: Animation { RealMotion.enter }
    static var closeAnim: Animation { RealMotion.exit }

    var body: some View {
        GeometryReader { geo in
            content(close)
                .offset(x: reduceMotion || shown ? 0 : geo.size.width)
                .opacity(reduceMotion && !shown ? 0 : 1)
        }
        .ignoresSafeArea(.keyboard)
        .presentationBackground(.clear)
        .onAppear { withAnimation(reduceMotion ? .easeOut(duration: 0.2) : Self.openAnim) { shown = true } }
    }

    private func close() {
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : Self.closeAnim) { shown = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + (reduceMotion ? 0.2 : 0.3)) { withoutAnimation { onClosed() } }
    }
}
