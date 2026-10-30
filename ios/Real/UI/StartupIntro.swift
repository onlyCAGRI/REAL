import SwiftUI

/// StartupIntro.kt: on a cold start the launch screen's white "Real" waits in the middle, then slides into the home
/// header while the black fades and the home rows rise one after another (the avatar last). Replaces the skeleton.
@Observable final class StartupIntro {
    static let shared = StartupIntro()
    /// false while the rows wait under the cover; flipping it to true plays their staggered rise.
    var revealed = true
    /// false while the intro's own copy of the wordmark is still sliding into the header.
    var landed = true
    /// The header wordmark's frame in global coordinates (HomeView reports it).
    var headerFrame: CGRect = .zero

    func arm() { revealed = false; landed = false }
    func settle() { revealed = true; landed = true }

    static let step = 0.055
    static let riseStart = 0.12
    static let avatarAt = 0.62
}

/// Matches the launch screen's LaunchWordmark image (132 pt wide, centered on the whole screen).
private let splashWordmarkWidth: CGFloat = 132
private let headerWordmarkSize = CGSize(width: 73, height: 30)

private struct StartupRise: ViewModifier {
    let order: Int
    private var intro: StartupIntro { .shared }

    func body(content: Content) -> some View {
        content
            .opacity(intro.revealed ? 1 : 0)
            .offset(y: intro.revealed ? 0 : 18)
            .animation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.44)
                .delay(intro.revealed ? StartupIntro.riseStart + Double(min(order, 8)) * StartupIntro.step : 0),
                       value: intro.revealed)
    }
}

private struct StartupAvatar: ViewModifier {
    private var intro: StartupIntro { .shared }

    func body(content: Content) -> some View {
        content
            .opacity(intro.revealed ? 1 : 0)
            .scaleEffect(intro.revealed ? 1 : 0.9)
            .animation(.easeOut(duration: 0.32).delay(intro.revealed ? StartupIntro.avatarAt : 0), value: intro.revealed)
    }
}

extension View {
    /// Row `order` of the cold-start rise (0 = categories, then "Kartlarım", the cards, the composer).
    func startupRise(_ order: Int) -> some View { modifier(StartupRise(order: order)) }
    /// The avatar comes in after the wordmark has landed.
    func startupAvatar() -> some View { modifier(StartupAvatar()) }
}

/// Covers the home with the launch screen's look, then plays the landing and calls [onDone].
struct StartupIntroOverlay: View {
    let onDone: () -> Void
    @AppStorage("reduce_animations") private var reduceAnimations = false
    @State private var moved = false
    @State private var backdropGone = false
    private var intro: StartupIntro { .shared }

    var body: some View {
        GeometryReader { geo in
            let origin = geo.frame(in: .global).origin
            let header = intro.headerFrame.offsetBy(dx: -origin.x, dy: -origin.y)
            let startWidth = splashWordmarkWidth
            let startHeight = startWidth * headerWordmarkSize.height / headerWordmarkSize.width
            let target = moved && header != .zero
            ZStack(alignment: .topLeading) {
                Color.black.opacity(backdropGone ? 0 : 1)
                Image("RealWordmark").resizable().scaledToFit()
                    .frame(width: target ? headerWordmarkSize.width : startWidth,
                           height: target ? headerWordmarkSize.height : startHeight)
                    .position(target ? CGPoint(x: header.midX, y: header.midY)
                                     : CGPoint(x: geo.size.width / 2, y: geo.size.height / 2))
            }
        }
        .ignoresSafeArea()
        .contentShape(Rectangle())                          // taps do not reach the covered home controls
        .accessibilityHidden(true)
        .onAppear { if !reduceAnimations { intro.arm() } }
        .onDisappear { intro.settle() }                     // left early (welcome): never leave the home hidden
        .task {
            if reduceAnimations {
                withAnimation(.easeOut(duration: 0.18)) { backdropGone = true }
                try? await Task.sleep(for: .seconds(0.2))
                onDone(); return
            }
            // The home's first layout and draw happen under the cover; start once the header has reported where
            // the wordmark lands (or after a short wait, e.g. if a category screen opened first).
            try? await Task.sleep(for: .seconds(0.35))
            for _ in 0..<16 where intro.headerFrame == .zero { try? await Task.sleep(for: .seconds(0.05)) }
            intro.revealed = true
            withAnimation(.timingCurve(0.2, 0, 0, 1, duration: 0.56)) { moved = true }
            withAnimation(.linear(duration: 0.32).delay(0.1)) { backdropGone = true }
            try? await Task.sleep(for: .seconds(0.56))
            intro.landed = true
            onDone()
        }
    }
}
