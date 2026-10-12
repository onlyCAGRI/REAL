import SwiftUI

/// LiquidGlassTokens.kt values.
enum LiquidGlassTokens {
    static let contentColor = Color(argb: 0xFF111113)
    static let dockTintTop = Color(argb: 0x330C0C0E)
    static let dockTintBottom = Color(argb: 0x480C0C0E)
    static let dockEdgeTop = Color(argb: 0xA6FFFFFF)
    static let dockEdgeBottom = Color(argb: 0x66FFFFFF)
    static let dockEdgeWidth: CGFloat = 0.9
    static let edgeTop = Color(argb: 0x62FFFFFF)
    static let edgeMiddle = Color(argb: 0x0AFFFFFF)
    static let edgeBottom = Color(argb: 0x2AFFFFFF)
    static let contentEdgeTop = Color(argb: 0x26FFFFFF)
    static let contentEdgeBottom = Color(argb: 0x0AFFFFFF)
    static let dockSelectedPill = Color(argb: 0x73000000)
}

enum LiquidGlassRole { case dock, chrome, sheet }

extension View {
    /// LiquidGlassSurface: the real Liquid Glass material on iOS 26, a tinted blur with the Android edge stroke before.
    @ViewBuilder
    func liquidGlass<S: InsettableShape>(_ shape: S, role: LiquidGlassRole = .dock, dim: Double = 0) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            self
                .background { if dim > 0 { shape.fill(Color.black.opacity(dim)) } }
                .glassEffect(role == .sheet ? Glass.regular : Glass.clear.interactive(), in: shape)
        } else {
            fallbackGlass(shape, role: role, dim: dim)
        }
        #else
        fallbackGlass(shape, role: role, dim: dim)
        #endif
    }

    @ViewBuilder
    private func fallbackGlass<S: InsettableShape>(_ shape: S, role: LiquidGlassRole, dim: Double) -> some View {
            self
                .background {
                    ZStack {
                        shape.fill(.ultraThinMaterial)
                        shape.fill(LinearGradient(colors: role == .dock
                                                  ? [LiquidGlassTokens.dockTintTop, LiquidGlassTokens.dockTintBottom]
                                                  : [Color(argb: 0xB5202022), Color(argb: 0xCC0D0D0F)],
                                                  startPoint: .top, endPoint: .bottom))
                        if dim > 0 { shape.fill(Color.black.opacity(dim)) }
                    }
                }
                .overlay {
                    shape.strokeBorder(
                        LinearGradient(colors: role == .dock
                                       ? [LiquidGlassTokens.dockEdgeTop, LiquidGlassTokens.dockEdgeBottom.opacity(0.4), LiquidGlassTokens.dockEdgeBottom]
                                       : [LiquidGlassTokens.edgeTop, LiquidGlassTokens.edgeMiddle, LiquidGlassTokens.edgeBottom],
                                       startPoint: .top, endPoint: .bottom),
                        lineWidth: role == .dock ? LiquidGlassTokens.dockEdgeWidth : 0.65)
                }
                .shadow(color: .black.opacity(0.35), radius: 9, y: 3)
    }

    /// cardContentChrome: the quiet dark surface content blocks sit on, with a faint top edge.
    func cardContentChrome(corners: CGFloat = 22) -> some View {
        let shape = RoundedRectangle(cornerRadius: corners, style: .continuous)
        return background(shape.fill(LiquidGlassTokens.contentColor))
            .overlay(shape.strokeBorder(LinearGradient(colors: [LiquidGlassTokens.contentEdgeTop, LiquidGlassTokens.contentEdgeBottom],
                                                       startPoint: .top, endPoint: .bottom), lineWidth: 0.7))
    }
}

/// Shimmer band sweeping across the compact "Add to REAL" placeholder (rememberComposerShimmer).
struct ShimmerText: View {
    let text: String
    var font: Font = RealFont.inter(16)
    var base = Color(argb: 0xFF8E8E96)
    @State private var phase: CGFloat = -1

    var body: some View {
        Text(text)
            .font(font)
            .foregroundStyle(base)
            .overlay {
                GeometryReader { geo in
                    let w = geo.size.width
                    Text(text)
                        .font(font)
                        .foregroundStyle(.white)
                        .mask {
                            LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .white.opacity(0.2), location: 0.18),
                                                   .init(color: .white.opacity(0.55), location: 0.35), .init(color: .white.opacity(0.82), location: 0.5),
                                                   .init(color: .white.opacity(0.55), location: 0.65), .init(color: .white.opacity(0.2), location: 0.82),
                                                   .init(color: .clear, location: 1)],
                                           startPoint: .leading, endPoint: .trailing)
                                .frame(width: w * 0.65)
                                .offset(x: -w * 0.65 + (phase + 1) / 2 * (w * 1.65))
                        }
                }
            }
            .onAppear {
                withAnimation(.easeInOut(duration: 1.6).delay(2.4).repeatForever(autoreverses: false)) { phase = 1 }
            }
    }
}
