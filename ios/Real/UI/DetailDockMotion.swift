import SwiftUI

/// The REAL blue band that travels right → left through the glass while the mic window is thinking
/// (RealThinkingSweep.kt). The card detail dock sends it through once when Kaydet is tapped. `progress` 0…1 carries
/// the band across; `strength` scales it (the one-shot pass fades it in and out with it).
struct RealSweepBand: View {
    let progress: Double
    var strength: Double = 1

    private let blue = Color(argb: 0xFF69ACFF)
    private let blueDeep = Color(argb: 0xFF2D6FD6)

    var body: some View {
        GeometryReader { g in
            let cx = g.size.width * (1.30 - progress * 1.60)
            LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: blueDeep.opacity(0.09 * strength), location: 0.22),
                                   .init(color: blue.opacity(0.23 * strength), location: 0.46), .init(color: blue.opacity(0.28 * strength), location: 0.62),
                                   .init(color: blueDeep.opacity(0.09 * strength), location: 0.82), .init(color: .clear, location: 1)],
                           startPoint: UnitPoint(x: (cx + g.size.width * 0.72) / max(g.size.width, 1), y: 0.12),
                           endPoint: UnitPoint(x: (cx - g.size.width * 0.72) / max(g.size.width, 1), y: 0.88))
        }
        .allowsHitTesting(false)
    }
}

/// DetailDockMotion.kt: when the card detail dock switches between Vazgeç · Kaydet and Tamamla · Düzenle · Share · Sil
/// the labels blur out and back in, and glass drops flow from the old buttons' places to the new ones (shown only
/// during the change). After Kaydet the new buttons appear as the sweep passes over them.
enum DetailDockMotion {
    static let duration = 0.45
    static let itemOut = 0.18
    static let itemIn = 0.22
    static let itemInDelay = 0.16
    static let labelBlur: CGFloat = 9
    static let curve = (0.3, 0.9, 0.25, 1.0)

    /// When the sweep reaches the middle of button `index` of `count`, from the Kaydet tap.
    static func waveDelay(_ index: Int, of count: Int) -> Double {
        let center = (Double(index) + 0.5) / Double(count)
        return max(0, duration * (1.30 - center) / 1.60)
    }

    /// Enter delay of a new button: staggered by the sweep while it still crosses the glass, else one short pause.
    static func enterDelay(_ index: Int, of count: Int, sweepStart: Date?) -> Double {
        guard let start = sweepStart else { return itemInDelay }
        let elapsed = Date().timeIntervalSince(start)
        guard elapsed < duration else { return itemInDelay }
        return max(0, waveDelay(index, of: count) - elapsed)
    }

    static func timing(_ seconds: Double) -> Animation {
        .timingCurve(curve.0, curve.1, curve.2, curve.3, duration: seconds)
    }
}

/// Blur, fade and scale of one dock button while its set of actions comes in or leaves.
private struct DetailDockItemMotion: ViewModifier {
    let visible: Bool
    let enterDelay: Double
    let reduceMotion: Bool

    func body(content: Content) -> some View {
        content
            .opacity(visible ? 1 : 0)
            .blur(radius: visible || reduceMotion ? 0 : DetailDockMotion.labelBlur)
            .scaleEffect(visible || reduceMotion ? 1 : 1.12)
            .allowsHitTesting(visible)
            .accessibilityHidden(!visible)
            .animation(reduceMotion ? nil : visible
                       ? DetailDockMotion.timing(DetailDockMotion.itemIn).delay(enterDelay)
                       : DetailDockMotion.timing(DetailDockMotion.itemOut), value: visible)
    }
}

extension View {
    func detailDockItem(visible: Bool, enterDelay: Double, reduceMotion: Bool) -> some View {
        modifier(DetailDockItemMotion(visible: visible, enterDelay: enterDelay, reduceMotion: reduceMotion))
    }
}

/// Four glass drops behind the dock buttons, drawn only while the dock changes: each narrows, travels to its new
/// slot and widens again, and a blur + alpha threshold lets neighbours merge and split like liquid.
struct DetailDockDrops: View {
    /// 2 while editing, 4 otherwise.
    let slots: Int
    let sweepStart: Date?

    @State private var fromSlots = 4
    @State private var toSlots = 4
    @State private var start = Date.distantPast
    @State private var delays = [Double](repeating: 0, count: 4)
    @State private var travel = 0.36
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var total: Double { travel + (delays.max() ?? 0) }

    var body: some View {
        TimelineView(.animation(paused: Date().timeIntervalSince(start) > total)) { tl in
            let elapsed = tl.date.timeIntervalSince(start)
            if elapsed < total {
                Canvas { ctx, size in
                    ctx.addFilter(.alphaThreshold(min: 0.5, color: .white))
                    ctx.addFilter(.blur(radius: 6))
                    ctx.drawLayer { layer in
                        for i in 0..<4 { layer.fill(drop(i, elapsed: elapsed, size: size), with: .color(.white)) }
                    }
                }
                // Rise out of the glass, flow, sink back.
                .opacity(0.14 * sin(min(1, elapsed / total) * .pi))
            }
        }
        .allowsHitTesting(false)
        .onAppear { fromSlots = slots; toSlots = slots }
        .onChange(of: slots) { _, new in
            fromSlots = toSlots
            toSlots = new
            guard !reduceMotion else { return }
            let elapsed = sweepStart.map { Date().timeIntervalSince($0) } ?? .infinity
            let wave = new == 4 && elapsed < DetailDockMotion.duration
            travel = wave ? 0.25 : 0.36
            delays = (0..<4).map { i in wave ? max(0, DetailDockMotion.waveDelay(slot(i, new), of: 4) - elapsed - 0.05) : 0 }
            start = Date()
        }
    }

    private func slot(_ index: Int, _ count: Int) -> Int { index * count / 4 }

    private func frame(_ index: Int, _ count: Int, _ size: CGSize) -> (x: CGFloat, w: CGFloat) {
        let pad: CGFloat = 6, gap: CGFloat = 6
        let width = (size.width - pad * 2) / CGFloat(count)
        return (pad + CGFloat(slot(index, count)) * width + gap / 2, width - gap)
    }

    private func drop(_ i: Int, elapsed: Double, size: CGSize) -> Path {
        let a = frame(i, fromSlots, size), b = frame(i, toSlots, size)
        let raw = min(1, max(0, (elapsed - delays[i]) / travel))
        let t = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.3, y: 0.9), endControlPoint: UnitPoint(x: 0.25, y: 1)).value(at: raw)
        let midX = (a.x + b.x) / 2, midW = max(a.w, b.w) * 0.7
        let full: CGFloat = 48, mid: CGFloat = 40
        let x: CGFloat, w: CGFloat, h: CGFloat
        if t < 0.45 {
            let k = CGFloat(t / 0.45)
            x = a.x + (midX - a.x) * k; w = a.w + (midW - a.w) * k; h = full + (mid - full) * k
        } else {
            let k = CGFloat((t - 0.45) / 0.55)
            x = midX + (b.x - midX) * k; w = midW + (b.w - midW) * k; h = mid + (full - mid) * k
        }
        return Path(roundedRect: CGRect(x: x, y: (size.height - h) / 2, width: w, height: h), cornerRadius: h / 2)
    }
}

/// Kaydet's one-shot sweep: fades in at the right edge, crosses the dock and fades out at the left.
struct DetailDockSweep: View {
    let start: Date?

    var body: some View {
        TimelineView(.animation(paused: start.map { Date().timeIntervalSince($0) > DetailDockMotion.duration } ?? true)) { tl in
            if let start {
                let p = tl.date.timeIntervalSince(start) / DetailDockMotion.duration
                if p > 0 && p < 1 {
                    let fade = p < 0.12 ? p / 0.12 : p > 0.85 ? (1 - p) / 0.15 : 1
                    RealSweepBand(progress: p, strength: fade)
                }
            }
        }
        .allowsHitTesting(false)
    }
}
