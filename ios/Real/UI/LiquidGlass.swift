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
            // System glass for refraction, plus the Android tint + bright rim so it reads on REAL's black backgrounds
            // (plain clear glass is nearly invisible over black).
            self
                .background {
                    ZStack {
                        shape.fill(LinearGradient(colors: role == .dock
                                                  ? [LiquidGlassTokens.dockTintTop, LiquidGlassTokens.dockTintBottom]
                                                  : (role == .sheet
                                                     // LiquidGlassTokens.sheetTint: panels stay readable over cards.
                                                     ? [Color(argb: 0xD10C0C0E), Color(argb: 0xD10C0C0E)]
                                                     : [Color(argb: 0x66202022), Color(argb: 0x880D0D0F)]),
                                                  startPoint: .top, endPoint: .bottom))
                        if dim > 0 { shape.fill(Color.black.opacity(dim)) }
                    }
                }
                .glassEffect(role == .sheet ? Glass.regular : Glass.regular.interactive(), in: shape)
                .overlay { shape.strokeBorder(Self.rim(role), lineWidth: role == .dock ? LiquidGlassTokens.dockEdgeWidth : 0.65).allowsHitTesting(false) }
        } else {
            fallbackGlass(shape, role: role, dim: dim)
        }
        #else
        fallbackGlass(shape, role: role, dim: dim)
        #endif
    }

    /// liquidRimBrush / liquidDockRimBrush
    static func rim(_ role: LiquidGlassRole) -> LinearGradient { liquidRim(role) }

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
                    ZStack {
                        // Specular sheen: keeps the glass legible where blur is unavailable (e.g. software-rendered simulators).
                        shape.fill(LinearGradient(colors: [.white.opacity(0.10), .white.opacity(0.02), .clear], startPoint: .top, endPoint: .center))
                        shape.strokeBorder(Self.rim(role), lineWidth: role == .dock ? LiquidGlassTokens.dockEdgeWidth : 0.65)
                    }
                    .allowsHitTesting(false)
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

/// Shared-element style navigation (Android sharedBounds): the tapped tile/card zooms into its screen and back.
struct NavNamespaceKey: EnvironmentKey { static let defaultValue: Namespace.ID? = nil }

extension EnvironmentValues {
    var navNamespace: Namespace.ID? {
        get { self[NavNamespaceKey.self] }
        set { self[NavNamespaceKey.self] = newValue }
    }
}

extension View {
    @ViewBuilder
    func zoomSource(_ id: String, in ns: Namespace.ID?) -> some View {
        if #available(iOS 18.0, *), let ns { self.matchedTransitionSource(id: id, in: ns) } else { self }
    }

    @ViewBuilder
    func zoomDestination(_ id: String, in ns: Namespace.ID?) -> some View {
        if #available(iOS 18.0, *), let ns { self.navigationTransition(.zoom(sourceID: id, in: ns)) } else { self }
    }
}


/// ReminderScreen.kt BottomFade: cards dissolve into black under the dock (height = dock + bottom gap + 46dp).
struct BottomFade: View {
    var body: some View {
        LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .black.opacity(0.2), location: 0.6),
                               .init(color: .black.opacity(0.92), location: 1)], startPoint: .top, endPoint: .bottom)
            .frame(height: 114)
            .frame(maxWidth: .infinity)
            .ignoresSafeArea(edges: .bottom)
            .allowsHitTesting(false)
    }
}

extension View {
    /// iOS 26 adds a scroll-edge dim/blur behind bottom insets; Android has none (it draws BottomFade instead).
    @ViewBuilder
    func hideBottomScrollEdgeEffect() -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            self.scrollEdgeEffectHidden(true, for: .bottom)
        } else {
            self
        }
        #else
        self
        #endif
    }
}

/// liquidRimBrush / liquidDockRimBrush
func liquidRim(_ role: LiquidGlassRole) -> LinearGradient {
    role == .dock
        ? LinearGradient(stops: [.init(color: LiquidGlassTokens.dockEdgeTop, location: 0), .init(color: Color(argb: 0x26FFFFFF), location: 0.25),
                                 .init(color: Color(argb: 0x14FFFFFF), location: 0.55), .init(color: Color(argb: 0x4DFFFFFF), location: 0.8),
                                 .init(color: LiquidGlassTokens.dockEdgeBottom, location: 1)], startPoint: .topLeading, endPoint: .bottomTrailing)
        : LinearGradient(stops: [.init(color: LiquidGlassTokens.edgeTop, location: 0), .init(color: Color(argb: 0x1AFFFFFF), location: 0.22),
                                 .init(color: LiquidGlassTokens.edgeMiddle, location: 0.52), .init(color: LiquidGlassTokens.edgeBottom, location: 0.82),
                                 .init(color: Color(argb: 0x38FFFFFF), location: 1)], startPoint: .topLeading, endPoint: .bottomTrailing)
}


/// Android's status bar is ~24dp; the Dynamic Island safe area is ~62pt, which pushed every iOS header visibly lower.
/// This lifts content 14pt into the top safe area (never closer than 48pt) on notch / Dynamic Island devices.
/// SwiftUI safe-area modifiers are ignored by NavigationStack / presented covers, so the hosting controller's
/// additionalSafeAreaInsets is adjusted instead (set, not added, so applying it twice is harmless).
struct LiftedTopSafeArea: ViewModifier {
    static let lift: CGFloat = 14
    func body(content: Content) -> some View { content.background(SafeAreaLifter(lift: Self.lift).frame(width: 0, height: 0)) }
}

private struct SafeAreaLifter: UIViewControllerRepresentable {
    let lift: CGFloat
    func makeUIViewController(context: Context) -> LifterController { LifterController(lift: lift) }
    func updateUIViewController(_ controller: LifterController, context: Context) { controller.apply() }

    final class LifterController: UIViewController {
        let lift: CGFloat
        init(lift: CGFloat) { self.lift = lift; super.init(nibName: nil, bundle: nil) }
        required init?(coder: NSCoder) { fatalError() }
        override func didMove(toParent parent: UIViewController?) { super.didMove(toParent: parent); apply() }
        override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); apply() }

        func apply() {
            var host: UIViewController = self
            while let p = host.parent { host = p }
            guard host !== self else { return }
            let windowTop = view.window?.safeAreaInsets.top ?? host.view.window?.safeAreaInsets.top ?? 0
            let target: CGFloat = windowTop >= 44 ? -lift : 0
            if host.additionalSafeAreaInsets.top != target { host.additionalSafeAreaInsets.top = target }
        }
    }
}

extension View {
    func liftedTopSafeArea() -> some View { modifier(LiftedTopSafeArea()) }
}
