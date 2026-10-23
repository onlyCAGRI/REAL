import SwiftUI

/// StartupLoadingScreen.kt: on a cold start, the home screen's shape (avatar, category cards, card list) as soft
/// placeholders with a light band sweeping across, then a feathered left-to-right wipe uncovers the real home.
struct StartupSkeletonReveal: View {
    let onDone: () -> Void
    @AppStorage("home_category_preview_count_v1") private var categoryCount = 4
    @AppStorage("reduce_animations") private var reduceAnimations = false
    @State private var wipeStart: Date?
    private let shown = 0.5       // startupSkeletonPreviewMs
    private let wipe = 0.6

    var body: some View {
        TimelineView(.animation(paused: reduceAnimations && wipeStart != nil)) { timeline in
            let progress = wipeStart.map { min(1, max(0, timeline.date.timeIntervalSince($0) / wipe)) } ?? 0
            GeometryReader { geo in
                ZStack {
                    Color.black
                    StartupSkeleton(categoryCount: categoryCount, phase: reduceAnimations ? -1 : shimmerPhase(timeline.date),
                                    topInset: geo.safeAreaInsets.top)
                }
                // The feathered edge moves left to right; everything left of it is already the real screen.
                .mask {
                    let width = max(geo.size.width, 1)
                    let feather = width * 0.18
                    let edge = -feather + easeInOut(progress) * (width + feather * 2)
                    LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .black, location: 1)],
                                   startPoint: UnitPoint(x: (edge - feather) / width, y: 0.5),
                                   endPoint: UnitPoint(x: (edge + feather) / width, y: 0.5))
                }
            }
            .ignoresSafeArea()
        }
        .contentShape(Rectangle())                          // taps do not reach the covered home controls
        .accessibilityLabel(t("Kartlar yükleniyor"))
        .task {
            try? await Task.sleep(for: .seconds(shown))
            if reduceAnimations { onDone(); return }
            wipeStart = Date()
            try? await Task.sleep(for: .seconds(wipe))
            onDone()
        }
    }

    /// One sweep every 1.5 s, from left of the screen to right of it.
    private func shimmerPhase(_ date: Date) -> Double {
        let t = date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.5) / 1.5
        return -0.65 + t * 2.3
    }

    private func easeInOut(_ x: Double) -> Double { x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2 }
}

/// The placeholders, in the same places as the home screen (header, overlapping category cards, the list).
private struct StartupSkeleton: View {
    let categoryCount: Int
    let phase: Double
    let topInset: CGFloat

    var body: some View {
        Canvas { context, size in
            var cards = Path()
            var bones = Path()
            let w = size.width - 32
            func rect(_ x: CGFloat, _ y: CGFloat, _ width: CGFloat, _ height: CGFloat, _ radius: CGFloat, into path: inout Path) {
                path.addRoundedRect(in: CGRect(x: 16 + x, y: topInset + y, width: width, height: height),
                                    cornerSize: CGSize(width: radius, height: radius), style: .continuous)
            }
            rect(w - 48, 20, 48, 48, 24, into: &bones)
            let count = min(max(categoryCount, 1), 12)
            for i in 0..<count {
                let y = 84 + CGFloat(i) * 100
                rect(0, y, w, i == count - 1 ? 140 : 180, 24, into: &cards)
                rect(32, y + 20, w * 0.52, 28, 7, into: &bones)
                rect(w - 46, y + 24, 16, 16, 6, into: &bones)
                rect(22, y + 66, 64, 22, 11, into: &bones)
            }
            let categoriesBottom = 84 + CGFloat(count - 1) * 100 + 140
            rect(w / 2 - 46, categoriesBottom + 18, 92, 12, 5, into: &bones)
            let headingY = categoriesBottom + 72
            rect(2, headingY, 98, 18, 5, into: &bones)
            rect(w - 28, headingY, 24, 18, 5, into: &bones)
            for i in 0..<3 {
                let y = headingY + 38 + CGFloat(i) * 126
                rect(0, y, w, 112, 22, into: &cards)
                rect(18, y + 20, w * 0.58, 16, 5, into: &bones)
                rect(18, y + 50, w - 48, 10, 4, into: &bones)
                rect(18, y + 70, w * 0.62, 10, 4, into: &bones)
            }
            context.fill(cards, with: .color(.white.opacity(0.025)))
            let center = phase * size.width
            let band = size.width * 0.55
            let base = Color.white.opacity(0.06)
            context.fill(bones, with: .linearGradient(Gradient(colors: [base, .white.opacity(0.12), base]),
                                                      startPoint: CGPoint(x: center - band, y: 0), endPoint: CGPoint(x: center + band, y: 0)))
        }
    }
}
