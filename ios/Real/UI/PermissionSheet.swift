import SwiftUI

/// NotificationPermissionSheet.kt PermissionSheet: dim scrim, bottom sheet with a pulsing glass hero, title, body,
/// white primary pill and a quiet secondary action; every element staggers in.
struct PermissionSheet: View {
    let title: String
    let description: String
    var primaryLabel = "İzin ver"
    var secondaryLabel = "Daha sonra"
    var glyph: ActionGlyph = .pin
    let onPrimary: () -> Void
    let onSecondary: () -> Void
    /// Scrim tap; set when the secondary button is a real choice, not "later".
    var onDismiss: (() -> Void)? = nil

    @State private var shown = false
    @State private var entered = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(shown ? 0.68 : 0).ignoresSafeArea()
                .onTapGesture { close(onDismiss ?? onSecondary) }
            VStack(spacing: 0) {
                Capsule().fill(.white.opacity(0.22)).frame(width: 38, height: 4)
                Color.clear.frame(height: 18)
                PermissionHero(glyph: glyph).stagger(entered, 0)
                Color.clear.frame(height: 6)
                Text(title).font(RealFont.inter(28, .medium)).kerning(-0.9).foregroundStyle(Color(argb: 0xFFF3F1F4))
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity).stagger(entered, 0.08)
                Color.clear.frame(height: 12)
                Text(description).font(RealFont.inter(16)).foregroundStyle(Color(argb: 0xFF9B9BA4)).lineSpacing(4)
                    .multilineTextAlignment(.center).padding(.horizontal, 6).frame(maxWidth: .infinity).stagger(entered, 0.15)
                Color.clear.frame(height: 30)
                Button { close(onPrimary) } label: {
                    Text(primaryLabel).font(RealFont.inter(15, .semibold)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity).frame(height: 54)
                        .background(Capsule().fill(.white))
                }
                .buttonStyle(PressScaleStyle(scale: 0.97)).stagger(entered, 0.22)
                Color.clear.frame(height: 6)
                Button { close(onSecondary) } label: {
                    Text(secondaryLabel).font(RealFont.inter(14, .semibold)).foregroundStyle(Color(argb: 0xFF9B9BA4))
                        .frame(maxWidth: .infinity).frame(height: 46).contentShape(Capsule())
                }
                .buttonStyle(PressScaleStyle(scale: 0.97)).stagger(entered, 0.28)
                Color.clear.frame(height: 8)
            }
            .padding(.horizontal, 24).padding(.top, 12)
            .background {
                UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous)
                    .fill(LiquidGlassTokens.contentColor)
                    .overlay {
                        GeometryReader { g in
                            RadialGradient(colors: [.white.opacity(0.07), .clear], center: .top, startRadius: 0, endRadius: g.size.width * 0.85)
                        }
                        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous))
                    }
                    .overlay(UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous)
                        .strokeBorder(LinearGradient(colors: [LiquidGlassTokens.contentEdgeTop, LiquidGlassTokens.contentEdgeBottom], startPoint: .top, endPoint: .bottom), lineWidth: 0.75))
                    .ignoresSafeArea(edges: .bottom)
            }
            .offset(y: shown ? 0 : 520)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.3)) { shown = true }
            entered = true
        }
    }

    private func close(_ then: @escaping () -> Void) {
        withAnimation(.timingCurve(0.4, 0, 1, 1, duration: 0.3)) { shown = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { then() }
    }
}

/// PermissionHero: 136pt glow, three expanding rings and an 88pt liquid disc holding a breathing glyph.
struct PermissionHero: View {
    let glyph: ActionGlyph
    @State private var breathe = false

    var body: some View {
        ZStack {
            Circle().fill(RadialGradient(colors: [.white.opacity(0.07), .clear], center: .center, startRadius: 0, endRadius: 68))
            TimelineView(.animation) { tl in
                let t = tl.date.timeIntervalSinceReferenceDate
                ZStack {
                    ForEach(0..<3, id: \.self) { i in
                        let p = ((t / 2.7) + Double(i) / 3).truncatingRemainder(dividingBy: 1)
                        let eased = 1 - pow(1 - p, 2)
                        Circle().stroke(.white.opacity(0.20 * (1 - eased)), lineWidth: 1)
                            .frame(width: 88 * (1 + 0.5 * eased), height: 88 * (1 + 0.5 * eased))
                    }
                }
            }
            ActionIcon(glyph, color: .white.opacity(0.96), size: 38)
                .scaleEffect(breathe ? 1.06 : 1)
                .frame(width: 88, height: 88)
                .liquidGlass(Circle(), role: .chrome)
        }
        .frame(width: 136, height: 136)
        .onAppear { withAnimation(.easeInOut(duration: 1.8).repeatForever(autoreverses: true)) { breathe = true } }
    }
}

private extension View {
    /// staggerIn: fade + 18pt rise, 440ms, per-element delay.
    func stagger(_ entered: Bool, _ delay: Double) -> some View {
        opacity(entered ? 1 : 0)
            .offset(y: entered ? 0 : 18)
            .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.44).delay(delay), value: entered)
    }
}
