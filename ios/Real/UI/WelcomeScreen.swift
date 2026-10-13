import SwiftUI

/// WelcomeScreen.kt (REAL_Welcome design): the word story ("Every idea. Captured. … Not someday. Today. … Make it REAL.") on black →
/// "Welcome to" rises into place, REAL rises and scales in with a haptic and a shine → the group lifts and the
/// name field + Google / Apple / e-posta buttons come in → "Tanıştığımıza memnun oldum, <ad>." → onComplete.
/// A tap during the word story skips to "Welcome to REAL".
struct WelcomeScreen: View {
    let onComplete: (String) -> Void

    private enum Phase { case intro, namePrompt, greeting }
    @State private var phase: Phase = .intro
    @State private var name = ""
    @FocusState private var focused: Bool
    @State private var welcomeAlpha: Double = 0
    @State private var welcomeOffset: CGFloat = 39
    @State private var welcomeKerning: CGFloat = 0.5
    @State private var logoIn: CGFloat = 0
    @State private var wordsDone = false
    @State private var wordIndex = 0
    @State private var wordProgress: CGFloat = 0
    @State private var introTask: Task<Void, Never>?
    @State private var shimmer: CGFloat = -1
    @State private var groupShift: CGFloat = 0
    @State private var questionAlpha: Double = 0
    @State private var questionOffset: CGFloat = 12
    @State private var inputAlpha: Double = 0
    @State private var inputOffset: CGFloat = 12
    @State private var greetingAlpha: Double = 0
    @State private var showDataUse = false
    @Environment(\.openURL) private var openURL
    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life
    @State private var auth = RealAuth.shared
    @State private var authError: String?
    @State private var sound = WelcomeSound()
    @Environment(\.scenePhase) private var scenePhase

    private let privacyOrigin = "https://real-privacy-staging.realapp.workers.dev"

    var body: some View {
        GeometryReader { geo in
            let headerHeight: CGFloat = 129
            let initialTop = (geo.size.height - headerHeight) / 2
            let finalShift: CGFloat = 54 + 24
            let promptTop = initialTop - finalShift + headerHeight + 52
            ZStack(alignment: .top) {
                // Plain black throughout (REAL_Welcome design), no ambient glow.
                Color.black.ignoresSafeArea()
                VStack(spacing: 14) {
                    // Same typeface and colour as the word story (23pt, #F3F1F4, regular).
                    Text("Welcome to").font(RealFont.inter(23)).kerning(welcomeKerning - 1).foregroundStyle(Color(argb: 0xFFF3F1F4))
                        .opacity(welcomeAlpha).offset(y: welcomeOffset)
                    WelcomeWordmark(reveal: 1, shimmer: shimmer).frame(width: 210, height: 91)
                        .opacity(logoIn).scaleEffect(0.97 + 0.03 * logoIn).offset(y: 6 * (1 - logoIn))
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

                if !wordsDone { wordStory }
            }
        }
        .onAppear(perform: startStory)
        .onDisappear { introTask?.cancel(); sound.fadeOut(0.6) }   // soften out, don't cut
        .onChange(of: scenePhase) { _, newPhase in if newPhase == .background { sound.stop() } }
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
                googleButton
                if let authError {
                    Text(authError).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFFF9A9A))
                        .multilineTextAlignment(.center).frame(width: 300).padding(.top, 8)
                }
                Color.clear.frame(height: 9)
                // Shown with the sign-in set but not available yet: it cannot be pressed.
                authButton("Apple ile devam et", emphasized: false)
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

    /// WelcomeAuthButton (emphasized): Google → Firebase, then the account's cards, categories and moods are restored
    /// and onboarding finishes with the typed name or the Google first name (finishAfterAuthentication).
    @ViewBuilder
    private var googleButton: some View {
        if auth.isAvailable {
            Button {
                focused = false
                authError = nil
                Task { @MainActor in
                    guard let account = await auth.signInWithGoogle() else {
                        authError = auth.lastError
                        return
                    }
                    do {
                        _ = try await RealCloudRestore.run(store: store, life: life)
                    } catch {
                        authError = "Giriş yapıldı ama kartlar alınamadı. Hesap menüsünden yeniden deneyebilirsin."
                    }
                    let typed = name.trimmingCharacters(in: .whitespaces)
                    name = typed.isEmpty ? (account.firstName ?? "") : typed
                    phase = .greeting
                    withAnimation(.easeOut(duration: 0.3)) { greetingAlpha = 1 }
                    try? await Task.sleep(for: .seconds(1.1))
                    onComplete(name)
                }
            } label: {
                Text(auth.busy ? "Google açılıyor…" : "Google ile devam et")
                    .font(RealFont.inter(15, .semibold)).foregroundStyle(.black)
                    .frame(width: 300, height: 50)
                    .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(.white))
            }
            .buttonStyle(.plain)
            .disabled(auth.busy)
        } else {
            authButton("Google ile devam et", emphasized: true)
        }
    }

    /// WelcomeAuthButton in its disabled state (sign-in not available in this build, or Apple for now).
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

    // MARK: Word story

    /// One word every two beats of the welcome music (120 BPM), so each word lands on the sound.
    private static let wordSeconds = 1.0
    private static let words: [(String, Double)] = [
        "Every idea.", "Captured.", "Every plan.", "On time.", "Every moment.", "Kept.",
        "Not someday.", "Today.", "Make it happen.", "Make it REAL."
    ].map { ($0, wordSeconds) }

    /// Each word: in over the first 18% (7pt up, 0.97 → 1), held, out over the last 18% (4pt further, to 1.015).
    private var wordStory: some View {
        WordStoryText(text: Self.words[wordIndex].0, progress: wordProgress)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black.ignoresSafeArea())
            .contentShape(Rectangle())
            .onTapGesture { skipStory() }
            .accessibilityLabel(Self.words.map(\.0).joined(separator: " "))
    }

    private func startStory() {
        introTask = Task { @MainActor in
            sound.prepare()
            try? await Task.sleep(for: .milliseconds(350))
            sound.start()                                   // the music's first note is the first word
            // Each word is placed against the start time, so the words never drift off the music.
            let clock = ContinuousClock(), start = clock.now
            var elapsed = 0.0
            for (index, word) in Self.words.enumerated() {
                if Task.isCancelled || wordsDone { return }
                wordIndex = index
                wordProgress = 0
                withAnimation(.timingCurve(0, 0, 0.58, 1, duration: word.1)) { wordProgress = 1 } // CSS ease-out
                elapsed += word.1
                try? await clock.sleep(until: start.advanced(by: .seconds(elapsed)))
            }
            if !Task.isCancelled && !wordsDone { wordsDone = true; runIntro() }
        }
    }

    private func skipStory() {
        guard !wordsDone else { return }
        introTask?.cancel()
        sound.fadeOut()
        wordsDone = true
        runIntro()
    }

    // MARK: Welcome to REAL → sign-in (timeline after the last word, ms:
    // 0 "Welcome to" fades in 39pt low · 650 it rises · 750 REAL rises/scales in · 830 haptic · 980 shine · 1450 sign-in)

    private func runIntro() {
        introTask = Task { @MainActor in
            let emphasized = { (d: Double) in Animation.timingCurve(0.16, 1, 0.3, 1, duration: d) }
            withAnimation(.easeOut(duration: 0.16)) { welcomeAlpha = 1 }
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(650))
                withAnimation(emphasized(0.4)) { welcomeOffset = 0 }
            }
            try? await Task.sleep(for: .milliseconds(750))
            withAnimation(emphasized(0.55)) { logoIn = 1 }
            try? await Task.sleep(for: .milliseconds(80))
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            try? await Task.sleep(for: .milliseconds(150))
            shimmer = 0
            withAnimation(.easeInOut(duration: 1.05)) { shimmer = 1 }
            try? await Task.sleep(for: .milliseconds(470))
            phase = .namePrompt
            withAnimation(emphasized(0.42)) { groupShift = 1 }
            try? await Task.sleep(for: .milliseconds(70))
            withAnimation(emphasized(0.3)) { questionOffset = 0 }
            withAnimation(.easeOut(duration: 0.3)) { questionAlpha = 1 }
            try? await Task.sleep(for: .milliseconds(60))
            withAnimation(emphasized(0.3)) { inputOffset = 0 }
            withAnimation(.easeOut(duration: 0.3)) { inputAlpha = 1 }
            try? await Task.sleep(for: .milliseconds(350))
            focused = true
        }
    }
}

/// One word of the story; [progress] 0…1 is animated, so the keyframes are evaluated every frame.
private struct WordStoryText: View, Animatable {
    let text: String
    var progress: CGFloat
    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        let p = progress
        let alpha: Double
        let dy: CGFloat
        let scale: CGFloat
        if p < 0.18 {
            let t = p / 0.18
            alpha = Double(t); dy = 7 * (1 - t); scale = 0.97 + 0.03 * t
        } else if p <= 0.82 {
            alpha = 1; dy = 0; scale = 1
        } else {
            let t = (p - 0.82) / 0.18
            alpha = Double(1 - t); dy = -4 * t; scale = 1 + 0.015 * t
        }
        return Text(text)
            .font(RealFont.inter(31)).kerning(-1.1).foregroundStyle(Color(argb: 0xFFF3F1F4))
            .lineLimit(1).minimumScaleFactor(0.8).padding(.horizontal, 20)
            .opacity(alpha).scaleEffect(scale).offset(y: dy)
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
