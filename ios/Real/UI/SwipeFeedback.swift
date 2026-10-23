import SwiftUI

let swipeSave = Color(argb: 0xFF3BE3A3)
let swipePass = Color(argb: 0xFFDCE3F0)
let swipeNext = Color(argb: 0xFF4D8DFF)
let swipeRemove = Color(argb: 0xFFFF4D6D)
let musicTone = Color(argb: 0xFFE0457B)

/// SwipeFeedback.kt: while a poster is dragged, light rises from the screen edge it is heading to — the full height,
/// a soft wide glow plus a bright thin line on the very edge (right: save / next, left: pass / remove, top: open —
/// music only). It grows with the drag and fades back on release.
struct SwipeEdgeGlow: View {
    let offset: CGSize
    var listMode = false
    var upColor: Color? = nil
    var threshold: CGFloat = 85
    /// The last drag position, kept while the light fades out after release.
    @State private var shown: CGSize = .zero

    var body: some View {
        Canvas { ctx, size in
            drawEdgeGlow(&ctx, size: size, offset: offset == .zero ? shown : offset, threshold: threshold,
                         right: listMode ? swipeRemove : swipeSave, left: listMode ? swipeNext : swipePass, up: upColor)
        }
        .opacity(offset == .zero ? 0 : 1)
        .animation(.easeOut(duration: 0.3), value: offset == .zero)
        .allowsHitTesting(false)
        .onChange(of: offset) { _, new in if new != .zero { shown = new } }
    }
}

private func smooth(_ x: CGFloat) -> CGFloat { let t = min(max(x, 0), 1); return t * t * (3 - 2 * t) }

func drawEdgeGlow(_ ctx: inout GraphicsContext, size: CGSize, offset o: CGSize, threshold: CGFloat, right: Color, left: Color, up: Color?) {
    let w = size.width, h = size.height
    if abs(o.width) >= abs(o.height), o.width != 0 {
        let s = smooth(abs(o.width) / (threshold * 1.6))
        guard s > 0.01 else { return }
        let color = o.width > 0 ? right : left
        let edgeX: CGFloat = o.width > 0 ? w : 0
        let radius = h * (0.55 + 0.2 * s)
        var glow = ctx
        glow.translateBy(x: edgeX, y: h / 2)
        glow.scaleBy(x: 0.42 + 0.18 * s, y: 1)
        let rect = CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2)
        glow.fill(Path(ellipseIn: rect), with: .radialGradient(Gradient(stops: [
            .init(color: color.opacity(0.55 * s), location: 0), .init(color: color.opacity(0.26 * s), location: 0.35),
            .init(color: color.opacity(0.07 * s), location: 0.7), .init(color: .clear, location: 1)]),
            center: .zero, startRadius: 0, endRadius: radius))
        let line: CGFloat = 3
        ctx.fill(Path(CGRect(x: o.width > 0 ? w - line : 0, y: 0, width: line, height: h)), with: .linearGradient(Gradient(stops: [
            .init(color: .clear, location: 0), .init(color: color.opacity(0.7 * s), location: 0.25), .init(color: .white.opacity(0.85 * s), location: 0.5),
            .init(color: color.opacity(0.7 * s), location: 0.75), .init(color: .clear, location: 1)]),
            startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: h)))
    } else if let up, o.height < 0 {
        let s = smooth(-o.height / (threshold * 1.6))
        guard s > 0.01 else { return }
        let radius = w * (0.75 + 0.25 * s)
        var glow = ctx
        glow.translateBy(x: w / 2, y: 0)
        glow.scaleBy(x: 1, y: 0.5 + 0.2 * s)
        glow.fill(Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2)), with: .radialGradient(Gradient(stops: [
            .init(color: up.opacity(0.55 * s), location: 0), .init(color: up.opacity(0.22 * s), location: 0.4), .init(color: .clear, location: 1)]),
            center: .zero, startRadius: 0, endRadius: radius))
        ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: 3)), with: .linearGradient(Gradient(stops: [
            .init(color: .clear, location: 0), .init(color: up.opacity(0.7 * s), location: 0.3), .init(color: .white.opacity(0.85 * s), location: 0.5),
            .init(color: up.opacity(0.7 * s), location: 0.7), .init(color: .clear, location: 1)]),
            startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: w, y: 0)))
    }
}

/// First visit of a deck: a ghost poster shows the swipes with the same edge light. Tap closes it for now;
/// "Bir daha gösterme" closes it for good (per deck).
struct SwipeTutorial: View {
    let kind: String
    let rightLabel: String
    let leftLabel: String
    var upLabel: String? = nil
    @State private var visible: Bool

    init(kind: String, rightLabel: String, leftLabel: String, upLabel: String? = nil) {
        self.kind = kind; self.rightLabel = rightLabel; self.leftLabel = leftLabel; self.upLabel = upLabel
        _visible = State(initialValue: !UserDefaults.standard.bool(forKey: "swipe_tutorial_\(kind)"))
    }

    var body: some View {
        if visible {
            TimelineView(.animation) { tl in
                let phases = upLabel == nil ? 2.0 : 3.0
                let time = tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: phases * 1.5) / 1.5
                let phase = min(Int(time), Int(phases) - 1)
                let frac = CGFloat(time - Double(phase))
                let travel: CGFloat = frac < 0.55 ? 1 - pow(1 - frac / 0.55, 2) : (frac < 0.8 ? 1 : 1 - (frac - 0.8) / 0.2)
                let ghost: CGSize = phase == 0 ? CGSize(width: 120 * travel, height: 0) : (phase == 1 ? CGSize(width: -120 * travel, height: 0) : CGSize(width: 0, height: -120 * travel))
                ZStack {
                    Color.black.opacity(0.62)
                    Canvas { ctx, size in
                        drawEdgeGlow(&ctx, size: size, offset: ghost, threshold: 68, right: swipeSave, left: swipePass, up: upLabel == nil ? nil : musicTone)
                    }
                    VStack(spacing: 0) {
                        RoundedRectangle(cornerRadius: 16).fill(.white.opacity(0.10))
                            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(.white.opacity(0.7), lineWidth: 1.5))
                            .frame(width: 120, height: 170)
                            .rotationEffect(.degrees(Double(max(-24, min(24, ghost.width / 85 * 7)))))
                            .offset(ghost)
                        Text(phase == 0 ? t("Sağa kaydır") : (phase == 1 ? t("Sola kaydır") : t("Yukarı kaydır")))
                            .font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.7)).padding(.top, 28)
                        Text(phase == 0 ? rightLabel : (phase == 1 ? leftLabel : (upLabel ?? "")))
                            .font(RealFont.inter(24, .bold)).foregroundStyle(phase == 0 ? swipeSave : (phase == 1 ? swipePass : musicTone))
                    }
                    VStack {
                        Spacer()
                        Button {
                            UserDefaults.standard.set(true, forKey: "swipe_tutorial_\(kind)")
                            withAnimation(.easeOut(duration: 0.2)) { visible = false }
                        } label: {
                            Text(t("Bir daha gösterme")).font(RealFont.inter(14, .medium)).foregroundStyle(.white)
                                .padding(.horizontal, 18).padding(.vertical, 11).background(Capsule().fill(.white.opacity(0.12)))
                        }
                        .buttonStyle(.plain)
                        .padding(.bottom, 28)
                    }
                }
            }
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .onTapGesture { withAnimation(.easeOut(duration: 0.2)) { visible = false } }
            .transition(.opacity)
            .zIndex(10)
        }
    }
}
