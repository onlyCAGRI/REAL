import SwiftUI

/// WelcomeScreen.kt: "Welcome to" → wordmark wipe + shimmer → group lifts → "Size nasıl hitap edelim?" name field,
/// Google / e-posta buttons (disabled until REAL sign-in exists on iOS, as Android does without an auth controller),
/// privacy links → "Tanıştığımıza memnun oldum, <ad>." → onComplete.
struct WelcomeScreen: View {
    let onComplete: (String) -> Void

    private enum Phase { case intro, namePrompt, greeting }
    @State private var phase: Phase = .intro
    @State private var name = ""
    @FocusState private var focused: Bool
    @State private var welcomeAlpha: Double = 0
    @State private var welcomeOffset: CGFloat = 8
    @State private var welcomeKerning: CGFloat = 1.5
    @State private var logoOffset: CGFloat = 10
    @State private var reveal: CGFloat = 0
    @State private var shimmer: CGFloat = -1
    @State private var groupShift: CGFloat = 0
    @State private var questionAlpha: Double = 0
    @State private var questionOffset: CGFloat = 12
    @State private var inputAlpha: Double = 0
    @State private var inputOffset: CGFloat = 12
    @State private var greetingAlpha: Double = 0
    @State private var showDataUse = false
    @Environment(\.openURL) private var openURL

    private let privacyOrigin = "https://real-privacy-staging.realapp.workers.dev"

    var body: some View {
        GeometryReader { geo in
            let headerHeight: CGFloat = 129
            let initialTop = (geo.size.height - headerHeight) / 2
            let finalShift: CGFloat = 54 + 24
            let promptTop = initialTop - finalShift + headerHeight + 52
            ZStack(alignment: .top) {
                Color.black.ignoresSafeArea()
                WelcomeAmbient(intensity: reveal).ignoresSafeArea()
                VStack(spacing: 14) {
                    Text("Welcome to").font(RealFont.inter(20)).kerning(welcomeKerning).foregroundStyle(Color(argb: 0xFFA4A4AD))
                        .opacity(welcomeAlpha).offset(y: welcomeOffset)
                    WelcomeWordmark(reveal: reveal, shimmer: shimmer).frame(width: 210, height: 91).offset(y: logoOffset)
                }
                .frame(maxWidth: .infinity)
                .offset(y: initialTop - finalShift * groupShift)

                Group {
                    if phase == .greeting {
                        Text("Tanıştığımıza memnun oldum,\n\(name.trimmingCharacters(in: .whitespaces)).")
                            .font(RealFont.inter(22, .semibold)).kerning(-0.3).lineSpacing(8).multilineTextAlignment(.center)
                            .foregroundStyle(.white)
                            .opacity(greetingAlpha).offset(y: (1 - greetingAlpha) * 12)
                    } else {
                        prompt
                    }
                }
                .padding(.horizontal, 28)
                .frame(maxWidth: .infinity)
                .offset(y: promptTop)
            }
        }
        .onAppear(perform: runIntro)
        .alert("Veri kullanımı", isPresented: $showDataUse) { Button("Tamam", role: .cancel) {} } message: {
            Text("iOS sürümünde notların ve kayıtların yalnızca bu cihazda saklanıyor. AI özellikleri isteğe bağlıdır; kullanmadan önce ayrıca izin istenir.")
        }
    }

    private var prompt: some View {
        VStack(spacing: 0) {
            Text("Size nasıl hitap edelim?").font(RealFont.inter(21, .semibold)).kerning(-0.3).foregroundStyle(Color(argb: 0xFFF7F7F8))
                .opacity(questionAlpha).offset(y: questionOffset)
            Color.clear.frame(height: 20)
            HStack {
                ZStack(alignment: .leading) {
                    if name.isEmpty { Text("Adınız").font(RealFont.inter(17)).foregroundStyle(Color(argb: 0xFF5A5A62)) }
                    TextField("", text: $name).font(RealFont.inter(17, .medium)).foregroundStyle(.white).tint(.white)
                        .textInputAutocapitalization(.words).submitLabel(.done).focused($focused)
                        .onSubmit(confirm)
                }
                let enabled = !name.trimmingCharacters(in: .whitespaces).isEmpty
                Button(action: confirm) {
                    ActionIcon(.check, color: enabled ? .black : Color(argb: 0xFF4A4A52), size: 18)
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(enabled ? .white : .white.opacity(0.06)))
                }
                .buttonStyle(.plain).disabled(!enabled)
                .accessibilityLabel("Devam et")
            }
            .padding(.leading, 22).padding(.trailing, 8)
            .frame(width: 300, height: 56)
            .background(Capsule().fill(Color(argb: 0xFF141416)))
            .overlay(Capsule().strokeBorder(.white.opacity(focused ? 0.40 : 0.12), lineWidth: 1))
            .opacity(inputAlpha).offset(y: inputOffset)
            Color.clear.frame(height: 14)
            Group {
                authButton("Google ile devam et", emphasized: true)
                Color.clear.frame(height: 9)
                authButton("E-posta ile devam et", emphasized: false)
                Color.clear.frame(height: 18)
                Text("Hesap ve veri kullanımını aşağıdaki metinlerden inceleyebilirsin. AI için ayrıca seçim yaparsın.")
                    .font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.5)).multilineTextAlignment(.center).frame(width: 300)
                VStack(spacing: 6) {
                    link("Gizlilik politikası") { if let u = URL(string: privacyOrigin + "/privacy?lang=tr") { openURL(u) } }
                    link("KVKK aydınlatma") { if let u = URL(string: privacyOrigin + "/kvkk?lang=tr") { openURL(u) } }
                    link("Veri kullanımı") { focused = false; showDataUse = true }
                }
                .padding(.top, 6)
                Text("AI özellikleri isteğe bağlıdır; kullanmadan önce ayrıca izin istenir.")
                    .font(RealFont.inter(11)).foregroundStyle(.white.opacity(0.38)).multilineTextAlignment(.center).frame(width: 300).padding(.top, 6)
            }
            .opacity(inputAlpha)
        }
    }

    /// WelcomeAuthButton in its disabled state: REAL sign-in is not configured on iOS yet.
    private func authButton(_ label: String, emphasized: Bool) -> some View {
        Text(label).font(RealFont.inter(15, .semibold)).foregroundStyle(Color(argb: 0xFF66666E))
            .frame(width: 300, height: 50)
            .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(.white.opacity(0.05)))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(.white.opacity(0.06), lineWidth: 1))
            .accessibilityLabel(label + ", şu an kullanılamıyor")
    }

    private func link(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(RealFont.inter(12)).underline().foregroundStyle(.white.opacity(0.78)).padding(.vertical, 6)
        }
        .buttonStyle(.plain)
    }

    private func confirm() {
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty, phase == .namePrompt else { return }
        focused = false
        phase = .greeting
        withAnimation(.easeOut(duration: 0.3)) { greetingAlpha = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) { onComplete(name.trimmingCharacters(in: .whitespaces)) }
    }

    private func runIntro() {
        let ease = Animation.timingCurve(0.16, 1, 0.3, 1, duration: 0.28)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.28) {
            withAnimation(ease) { welcomeOffset = 0; welcomeKerning = 0.5 }
            withAnimation(.easeOut(duration: 0.28)) { welcomeAlpha = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.28) {
                withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.5)) { logoOffset = 0 }
                withAnimation(.timingCurve(0.2, 0, 0.2, 1, duration: 0.5)) { reveal = 1 }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    shimmer = 0
                    withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 1.3)) { shimmer = 1 }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) {
                        phase = .namePrompt
                        withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.42)) { groupShift = 1 }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.07) {
                            withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.3)) { questionOffset = 0 }
                            withAnimation(.easeOut(duration: 0.3)) { questionAlpha = 1 }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) {
                                withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.3)) { inputOffset = 0 }
                                withAnimation(.easeOut(duration: 0.3)) { inputAlpha = 1 }
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { focused = true }
                            }
                        }
                    }
                }
            }
        }
    }
}

/// WelcomeWordmark: left-to-right feathered wipe while revealing, then a single light sweep.
private struct WelcomeWordmark: View, Animatable {
    var reveal: CGFloat
    var shimmer: CGFloat
    var animatableData: AnimatablePair<CGFloat, CGFloat> {
        get { AnimatablePair(reveal, shimmer) }
        set { reveal = newValue.first; shimmer = newValue.second }
    }

    var body: some View {
        GeometryReader { g in
            let w = g.size.width
            let feather = w * 0.18
            let head = reveal * (w + feather)
            Image("RealWordmark").resizable().scaledToFit()
                .overlay {
                    if (0...1).contains(shimmer) {
                        let band = w * 0.22
                        let cx = -band + shimmer * (w + band * 2)
                        LinearGradient(colors: [.clear, .white.opacity(0.55), .clear],
                                       startPoint: UnitPoint(x: (cx - band) / w, y: 0), endPoint: UnitPoint(x: (cx + band) / w, y: 0.35))
                            .mask(Image("RealWordmark").resizable().scaledToFit())
                    }
                }
                .mask {
                    LinearGradient(stops: [.init(color: .white, location: max(0, (head - feather) / w)), .init(color: .clear, location: max(0.0001, head / w))],
                                   startPoint: .leading, endPoint: .trailing)
                }
                .opacity(reveal > 0 ? 1 : 0)
        }
        .accessibilityLabel("Real")
    }
}

/// WelcomeAmbient: two slow, faint lights that give the black background depth.
private struct WelcomeAmbient: View {
    let intensity: CGFloat
    var body: some View {
        TimelineView(.animation) { tl in
            let raw = (tl.date.timeIntervalSinceReferenceDate / 14).truncatingRemainder(dividingBy: 2)
            let t = raw < 1 ? raw : 2 - raw
            Canvas { ctx, size in
                let a = min(max(intensity, 0), 1)
                let w = size.width, h = size.height
                let c1 = CGPoint(x: w * (0.25 + 0.35 * t), y: h * (0.22 + 0.06 * t))
                ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .radialGradient(
                    Gradient(colors: [Color(argb: 0xFF9FB4FF).opacity(0.16 * a), .clear]), center: c1, startRadius: 0, endRadius: w * 0.85))
                let c2 = CGPoint(x: w * (0.8 - 0.35 * t), y: h * (0.78 - 0.06 * t))
                ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .radialGradient(
                    Gradient(colors: [Color(argb: 0xFFC9A6FF).opacity(0.12 * a), .clear]), center: c2, startRadius: 0, endRadius: w * 0.9))
            }
        }
        .allowsHitTesting(false)
    }
}
