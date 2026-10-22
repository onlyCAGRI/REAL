import SwiftUI

/// WelcomeScreen.kt (REAL_Welcome design): the word story ("Every idea. Captured. … Not someday. Today. … Make it REAL.") on black →
/// "Welcome to" rises into place, REAL rises and scales in with a haptic and a shine → the group lifts and the
/// name field + Google / Apple / e-posta buttons come in → "Tanıştığımıza memnun oldum, <ad>." → onComplete.
/// A tap during the word story skips to "Welcome to REAL"; when it plays out, REAL flies from the sentence into the wordmark.
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
    // word story (see startStory)
    @State private var storyText = ""
    @State private var storyAlpha: Double = 0
    @State private var storyBlur: CGFloat = 0
    @State private var storyDX: CGFloat = 0
    @State private var storyScale: CGFloat = 1
    @State private var sentence = false
    @State private var sentenceAlpha: Double = 0
    @State private var sentenceBlur: CGFloat = 6
    @State private var keepAlpha: Double = 1
    @State private var slotWord = "happen."
    @State private var slotShift: CGFloat = 0
    @State private var slotAlpha: Double = 1
    @State private var realFrame: CGRect?
    // REAL flying from the sentence into the wordmark
    @State private var logoFrame: CGRect = .zero
    @State private var flightOffset: CGSize = .zero
    @State private var flightScale: CGFloat = 1
    @State private var promptHeight: CGFloat = 0
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
    @State private var showEmailAuth = false
    @State private var sound = WelcomeSound()
    @Environment(\.scenePhase) private var scenePhase

    private let privacyOrigin = "https://real-privacy-staging.realapp.workers.dev"

    var body: some View {
        GeometryReader { geo in
            let headerHeight: CGFloat = 129
            let initialTop = (geo.size.height - headerHeight) / 2
            // If the name field + buttons + links are taller than the room under the logo, the group lifts further
            // so the links stay on screen (keeping 12pt above the header).
            let baseShift: CGFloat = 54 + 24
            let overflow = promptHeight > 0
                ? min(max(0, initialTop - baseShift + headerHeight + 52 + promptHeight + 12 - geo.size.height), max(0, initialTop - baseShift - 12))
                : 0
            let finalShift = baseShift + overflow
            let promptTop = initialTop - finalShift + headerHeight + 52
            ZStack(alignment: .top) {
                // Plain black throughout (REAL_Welcome design), no ambient glow.
                Color.black.ignoresSafeArea()
                VStack(spacing: 14) {
                    // Same typeface and colour as the word story (23pt, #F3F1F4, regular).
                    Text("Welcome to").font(RealFont.inter(23)).kerning(welcomeKerning - 1).foregroundStyle(Color(argb: 0xFFF3F1F4))
                        .opacity(welcomeAlpha).offset(y: welcomeOffset)
                    WelcomeWordmark(reveal: 1, shimmer: shimmer).frame(width: 210, height: 91)
                        .background(GeometryReader { g in
                            Color.clear
                                .onAppear { logoFrame = g.frame(in: .global) }
                                .onChange(of: g.frame(in: .global)) { _, f in if flightScale == 1, flightOffset == .zero { logoFrame = f } }
                        })
                        .opacity(logoIn).scaleEffect(0.97 + 0.03 * logoIn).offset(y: 6 * (1 - logoIn))
                        .scaleEffect(flightScale).offset(flightOffset)
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
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { promptHeight = $0 }
                .offset(y: promptTop)
                .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.3), value: overflow)

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
                if auth.isAvailable {
                    Button { focused = false; authError = nil; showEmailAuth = true } label: {
                        Text("E-posta ile devam et").font(RealFont.inter(15, .semibold)).foregroundStyle(.white)
                            .frame(width: 300, height: 50)
                            .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(.white.opacity(0.08)))
                            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(.white.opacity(0.14)))
                    }
                    .buttonStyle(.plain)
                    .disabled(auth.busy)
                    .sheet(isPresented: $showEmailAuth) {
                        AuthChoiceSheet(startWithEmail: true) { account in Task { @MainActor in await finishAfterAuthentication(account) } }
                    }
                } else {
                    authButton("E-posta ile devam et", emphasized: false)
                }
                Color.clear.frame(height: 22)
                // Kept quiet on purpose: the three documents in one small row. The longer explanations live behind
                // "Veri kullanımı" and AI asks for its own consent before it is first used.
                HStack(spacing: 0) {
                    link("Gizlilik politikası") { if let u = URL(string: privacyOrigin + "/privacy?lang=tr") { openURL(u) } }
                    linkDot
                    link("KVKK aydınlatma") { if let u = URL(string: privacyOrigin + "/kvkk?lang=tr") { openURL(u) } }
                    linkDot
                    link("Veri kullanımı") { focused = false; showDataUse = true }
                }
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
                    await finishAfterAuthentication(account)
                }
            } label: {
                HStack(spacing: 10) {
                    Image("GoogleG").resizable().frame(width: 18, height: 18)
                    Text(auth.busy ? "Google açılıyor…" : "Google ile devam et")
                }
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

    /// After Google or e-posta: the account's cards, categories and moods come back, then onboarding finishes with the
    /// typed name or the account's first name.
    private func finishAfterAuthentication(_ account: RealAuth.Account) async {
        do {
            RealCloudSync.shared.reset()
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

    /// WelcomeAuthButton in its disabled state (sign-in not available in this build, or Apple for now).
    private func authButton(_ label: String, emphasized: Bool) -> some View {
        HStack(spacing: 10) {
            // The Google / Apple mark before the label, dimmed with it.
            if label.hasPrefix("Google") { Image("GoogleG").resizable().frame(width: 18, height: 18).opacity(0.5) }
            if label.hasPrefix("Apple") { Image(systemName: "apple.logo").font(.system(size: 17)) }
            Text(label)
        }
        .font(RealFont.inter(15, .semibold)).foregroundStyle(Color(argb: 0xFF66666E))
            .frame(width: 300, height: 50)
            .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(.white.opacity(0.05)))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(.white.opacity(0.06), lineWidth: 1))
            .accessibilityLabel(label + ", şu an kullanılamıyor")
    }

    private var linkDot: some View {
        Text("·").font(RealFont.inter(11)).foregroundStyle(.white.opacity(0.3)).padding(.horizontal, 7)
    }

    private func link(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(RealFont.inter(11)).foregroundStyle(.white.opacity(0.45)).lineLimit(1).padding(.vertical, 6)
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
    // On the welcome music's beat grid (120 BPM, 0.5 s a beat), same timeline as WelcomeScreen.kt and the REAL_Welcome
    // demo: three pairs (each word two beats), "Not someday." blurred, "Today." snaps sharp, "Make it happen." rolls
    // to "Make it REAL.", and on beat 20 REAL flies into the wordmark while the music opens up.

    private static let beat = 0.5
    private static let pairs = [("Every idea.", "Captured."), ("Every plan.", "On time."), ("Every moment.", "Kept.")]
    private static let someday = "Not someday."
    private static let allWords = pairs.flatMap { [$0.0, $0.1] } + [someday, "Today.", "Make it happen.", "Make it REAL."]
    private static let storyEase = { (d: Double) in Animation.timingCurve(0.16, 1, 0.3, 1, duration: d) }
    private static let storyOut = Animation.timingCurve(0.4, 0, 1, 1, duration: 0.2)

    private var storyStyle: some ShapeStyle { Color(argb: 0xFFF3F1F4) }

    private var wordStory: some View {
        ZStack {
            if !sentence {
                Text(storyText)
                    .font(RealFont.inter(31)).kerning(-1.1).foregroundStyle(storyStyle)
                    .lineLimit(1).minimumScaleFactor(0.8).padding(.horizontal, 20)
                    .scaleEffect(storyScale).offset(x: storyDX)
                    .blur(radius: storyBlur).opacity(storyAlpha)
            } else {
                HStack(spacing: 8) {
                    Text("Make it").opacity(keepAlpha)
                    Text(slotWord)
                        .offset(y: slotShift * 38).opacity(slotAlpha)
                        .background(GeometryReader { g in
                            Color.clear
                                .onAppear { realFrame = g.frame(in: .global) }
                                .onChange(of: g.frame(in: .global)) { _, f in realFrame = f }
                        })
                        .frame(height: 38).clipped()
                }
                .font(RealFont.inter(31)).kerning(-1.1).foregroundStyle(storyStyle).lineLimit(1)
                .blur(radius: sentenceBlur).opacity(sentenceAlpha)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture { skipStory() }
        .accessibilityLabel(Self.allWords.joined(separator: " "))
    }

    private func startStory() {
        introTask = Task { @MainActor in
            sound.prepare()
            try? await Task.sleep(for: .milliseconds(350))
            sound.start()                                   // the music's first note is the first word
            // Every step is placed against the start time, so the motion never drifts off the music.
            let clock = ContinuousClock(), start = clock.now
            func at(_ beat: Double) async -> Bool {
                try? await clock.sleep(until: start.advanced(by: .seconds(beat * Self.beat)))
                return !Task.isCancelled && !wordsDone
            }
            func wordIn(_ word: String, dx: CGFloat, blur: CGFloat = 6, scale: CGFloat = 0.97, duration: Double = 0.45, dim: Double = 1) {
                storyText = word; storyAlpha = 0; storyBlur = blur; storyDX = dx; storyScale = scale
                withAnimation(Self.storyEase(duration)) { storyAlpha = dim; storyBlur = 0; storyDX = 0; storyScale = 1 }
            }
            func wordOut(dx: CGFloat = 0) {
                withAnimation(Self.storyOut) { storyAlpha = 0; storyBlur = 4; storyDX = dx }
            }
            for (p, pair) in Self.pairs.enumerated() {
                let dir = CGFloat(p - 1), base = Double(p * 4)  // past-ish ← · present · future-ish →
                guard await at(base) else { return }; wordIn(pair.0, dx: dir * -18)
                guard await at(base + 1.6) else { return }; wordOut(dx: dir * 14)
                guard await at(base + 2) else { return }; wordIn(pair.1, dx: dir * -18)
                guard await at(base + 3.6) else { return }; wordOut(dx: dir * 14)
            }
            guard await at(12) else { return }
            wordIn(Self.someday, dx: 0, blur: 14, scale: 0.94, duration: 0.9, dim: 0.55)
            guard await at(14) else { return }              // "Today." snaps into focus
            storyText = "Today."; storyAlpha = 0.5; storyBlur = 8; storyDX = 0; storyScale = 1.06
            withAnimation(Self.storyEase(0.29)) { storyAlpha = 1; storyBlur = 0; storyScale = 0.985 }
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(290))
                withAnimation(Self.storyEase(0.23)) { storyScale = 1 }
            }
            guard await at(15.6) else { return }; wordOut()
            guard await at(16) else { return }              // "Make it happen."
            storyAlpha = 0; sentence = true
            withAnimation(Self.storyEase(0.48)) { sentenceAlpha = 1; sentenceBlur = 0 }
            guard await at(18) else { return }              // happen. rolls up, REAL. rolls in
            withAnimation(Self.storyOut) { slotShift = -1.1; slotAlpha = 0 }
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(200))
                slotWord = "REAL."; slotShift = 1.1
                withAnimation(Self.storyEase(0.38)) { slotShift = 0; slotAlpha = 1 }
            }
            guard await at(19.5) else { return }
            withAnimation(.timingCurve(0.4, 0, 1, 1, duration: 0.25)) { keepAlpha = 0 }
            guard await at(20) else { return }
            wordsDone = true
            runIntro(flyingFrom: realFrame)
        }
    }

    private func skipStory() {
        guard !wordsDone else { return }
        introTask?.cancel()
        sound.fadeOut()
        wordsDone = true
        runIntro(flyingFrom: nil)
    }

    // MARK: Welcome to REAL → sign-in
    // After the story ends on its own (beat 20): 0 REAL flies from the sentence into the wordmark (0.9 s) with a haptic ·
    // 0.5 s "Welcome to" rises in · 0.6 s the shine · 2.0 s the name prompt and sign-in.
    // After a tap-skip: 0 "Welcome to" fades in 39pt low · 650 it rises · 750 REAL rises/scales in · 830 haptic ·
    // 980 shine · 1450 sign-in.

    private func runIntro(flyingFrom from: CGRect?) {
        introTask = Task { @MainActor in
            let emphasized = { (d: Double) in Animation.timingCurve(0.16, 1, 0.3, 1, duration: d) }
            if let from, logoFrame.width > 0 {
                flightOffset = CGSize(width: from.midX - logoFrame.midX, height: from.midY - logoFrame.midY)
                flightScale = (from.width * 0.86) / logoFrame.width   // "REAL" without the dot vs the wordmark
                welcomeOffset = 10
                logoIn = 1
                withAnimation(emphasized(0.9)) { flightOffset = .zero; flightScale = 1 }
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(500))
                    withAnimation(emphasized(0.6)) { welcomeAlpha = 1; welcomeOffset = 0 }
                }
                try? await Task.sleep(for: .milliseconds(600))
                shimmer = 0
                withAnimation(.easeInOut(duration: 1.05)) { shimmer = 1 }
                try? await Task.sleep(for: .milliseconds(1400))
            } else {
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
            }
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
