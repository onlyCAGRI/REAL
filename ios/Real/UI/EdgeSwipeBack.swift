import SwiftUI
import UIKit

/// Android's system back for screens with no navigation bar (full-screen covers, overlays): a swipe from the left
/// screen edge runs the screen's own back step. A UIScreenEdgePanGestureRecognizer on the hosting view, so taps,
/// scrolling and the back button keep working; it is removed with the screen.
struct EdgeSwipeBack: UIViewRepresentable {
    var enabled = true
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
            guard r.state == .ended, enabled, let v = r.view else { return }
            let t = r.translation(in: v), speed = r.velocity(in: v).x
            if t.x > 70 || (t.x > 24 && speed > 600) {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                action()
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

extension View {
    /// Swipe from the left edge = `action` (the same step as the screen's back button).
    func edgeSwipeBack(enabled: Bool = true, _ action: @escaping () -> Void) -> some View {
        background(EdgeSwipeBack(enabled: enabled, action: action).frame(width: 0, height: 0))
    }
}
