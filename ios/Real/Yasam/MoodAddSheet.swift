import SwiftUI
import UIKit

/// Mood V2 (MoodV2Selector.kt): five families from Kötü to Çok iyi, each with a tint and a drawn face.
struct MoodFamily {
    let title: String
    let tint: Color
    let defaultMood: JournalMood
    let moods: [JournalMood]
}

enum MoodV2 {
    static let families: [MoodFamily] = [
        MoodFamily(title: "Kötü", tint: Color(argb: 0xFFF05B5B), defaultMood: .bad, moods: [.bad, .sad, .angry]),
        MoodFamily(title: "Zor", tint: Color(argb: 0xFFF1A13D), defaultMood: .hard, moods: [.hard, .tired, .stressed, .low]),
        MoodFamily(title: "Normal", tint: Color(argb: 0xFFAEB8CC), defaultMood: .neutral, moods: [.neutral, .bored]),
        MoodFamily(title: "İyi", tint: Color(argb: 0xFF55D3B4), defaultMood: .good, moods: [.good, .calm]),
        MoodFamily(title: "Çok iyi", tint: Color(argb: 0xFFB8E85C), defaultMood: .veryGood, moods: [.veryGood, .happy, .excited]),
    ]

    static let descriptors: [[(String, String)]] = [
        [("sad", "Üzgün"), ("angry", "Sinirli"), ("anxious", "Kaygılı"), ("scared", "Korkmuş"), ("overwhelmed", "Bunalmış"),
         ("ashamed", "Utanmış"), ("disgusted", "İğrenmiş"), ("frustrated", "Hayal kırıklığı"), ("lonely", "Yalnız"),
         ("hopeless", "Umutsuz"), ("guilty", "Suçlu"), ("drained", "Bitkin"), ("stressed", "Stresli"), ("fuck_off", "F*ck off")],
        [("tired", "Yorgun"), ("stressed", "Stresli"), ("low", "Keyifsiz"), ("tense", "Gergin"), ("worried", "Endişeli"),
         ("pressured", "Baskı altında"), ("restless", "Huzursuz"), ("impatient", "Sabırsız"), ("distracted", "Dalgın"),
         ("overwhelmed", "Bunalmış"), ("anxious", "Kaygılı"), ("drained", "Bitkin"), ("sad", "Üzgün"), ("uncertain", "Kararsız")],
        [("neutral", "Normal"), ("bored", "Sıkılmış"), ("balanced", "Dengeli"), ("blank", "Boş"), ("distracted", "Dalgın"),
         ("steady", "Sakin"), ("uncertain", "Kararsız"), ("tired", "Yorgun"), ("low", "Keyifsiz"), ("focused", "Odaklı"), ("content", "Memnun")],
        [("calm", "Sakin"), ("relieved", "Rahatlamış"), ("hopeful", "Umutlu"), ("content", "Memnun"), ("focused", "Odaklı"),
         ("safe", "Güvende"), ("grateful", "Minnettar"), ("energized", "Enerjik"), ("happy", "Mutlu"), ("motivated", "Motive"),
         ("excited", "Heyecanlı"), ("balanced", "Dengeli")],
        [("happy", "Mutlu"), ("excited", "Heyecanlı"), ("joyful", "Neşeli"), ("proud", "Gururlu"), ("motivated", "Motive"),
         ("grateful", "Minnettar"), ("loved", "Sevgi dolu"), ("energetic", "Enerjik"), ("hopeful", "Umutlu"), ("relieved", "Rahatlamış")],
    ]

    static let factorGroups: [(String, [(String, String)])] = [
        ("Kendim", [("health", "Sağlık"), ("sleep", "Uyku"), ("fitness", "Spor"), ("self_care", "Öz bakım"), ("hobbies", "Hobiler"),
                    ("goals", "Hedefler"), ("appearance", "Görünüş"), ("spirituality", "Maneviyat")]),
        ("İnsanlar", [("family", "Aile"), ("friends", "Arkadaşlar"), ("partner", "Partner"), ("dating", "Flört"),
                      ("communication", "İletişim"), ("conflict", "Tartışma"), ("closeness", "Yakınlık"), ("crowds", "Kalabalık")]),
        ("Hayat", [("work", "İş"), ("education", "Eğitim"), ("money", "Para"), ("tasks", "Görevler"), ("weather", "Hava"),
                   ("travel", "Seyahat"), ("current_events", "Güncel olaylar"), ("routine", "Rutin"), ("future", "Gelecek")]),
    ]

    static func familyIndex(_ m: JournalMood) -> Int {
        if m == .fuckOff { return 0 }
        return families.firstIndex { $0.moods.contains(m) } ?? 2
    }

    static func tint(_ m: JournalMood) -> Color { families[familyIndex(m)].tint }

    static func descriptorLabel(_ key: String) -> String {
        descriptors.flatMap { $0 }.first { $0.0 == key }?.1 ?? key
    }

    static func lerpColor(_ progress: Double) -> Color {
        let p = min(max(progress, 0), 4)
        let left = min(max(Int(p.rounded(.down)), 0), 4)
        let right = min(left + 1, 4)
        return families[left].tint.lerp(families[right].tint, p - Double(left))
    }
}

/// FaceV2Params: eyes, brows and mouth morph continuously across the five anchors.
struct MoodFaceParams {
    var eyeWidth, eyeHeight, eyeY, eyeSpacing, mouthWidth, mouthY, mouthCurve, mouthStroke, brow: Double

    static let anchors: [MoodFaceParams] = [
        MoodFaceParams(eyeWidth: 0.105, eyeHeight: 0.105, eyeY: 0.385, eyeSpacing: 0.225, mouthWidth: 0.54, mouthY: 0.690, mouthCurve: -0.94, mouthStroke: 1.72, brow: 0.08),
        MoodFaceParams(eyeWidth: 0.19, eyeHeight: 0.064, eyeY: 0.400, eyeSpacing: 0.245, mouthWidth: 0.36, mouthY: 0.660, mouthCurve: -0.38, mouthStroke: 1.18, brow: 0.18),
        MoodFaceParams(eyeWidth: 0.25, eyeHeight: 0.052, eyeY: 0.390, eyeSpacing: 0.260, mouthWidth: 0.30, mouthY: 0.640, mouthCurve: 0.00, mouthStroke: 1.00, brow: 0.00),
        MoodFaceParams(eyeWidth: 0.185, eyeHeight: 0.185, eyeY: 0.365, eyeSpacing: 0.270, mouthWidth: 0.39, mouthY: 0.615, mouthCurve: 0.56, mouthStroke: 1.00, brow: 0.00),
        MoodFaceParams(eyeWidth: 0.235, eyeHeight: 0.235, eyeY: 0.350, eyeSpacing: 0.285, mouthWidth: 0.46, mouthY: 0.600, mouthCurve: 0.90, mouthStroke: 1.00, brow: 0.00),
    ]

    static func at(_ progress: Double, mood: JournalMood?) -> MoodFaceParams {
        let p = min(max(progress, 0), 4)
        let l = min(max(Int(p.rounded(.down)), 0), 4)
        let r = min(l + 1, 4)
        let t = p - Double(l)
        let a = anchors[l], b = anchors[r]
        func mix(_ x: Double, _ y: Double) -> Double { x + (y - x) * t }
        var f = MoodFaceParams(eyeWidth: mix(a.eyeWidth, b.eyeWidth), eyeHeight: mix(a.eyeHeight, b.eyeHeight), eyeY: mix(a.eyeY, b.eyeY),
                               eyeSpacing: mix(a.eyeSpacing, b.eyeSpacing), mouthWidth: mix(a.mouthWidth, b.mouthWidth),
                               mouthY: mix(a.mouthY, b.mouthY), mouthCurve: mix(a.mouthCurve, b.mouthCurve),
                               mouthStroke: mix(a.mouthStroke, b.mouthStroke), brow: mix(a.brow, b.brow))
        switch mood {
        case .excited?: f.eyeWidth *= 1.10; f.eyeHeight *= 1.14; f.mouthCurve = 0.95
        case .happy?: f.mouthWidth *= 1.08; f.mouthCurve = max(f.mouthCurve, 0.88)
        case .calm?: f.eyeHeight = min(f.eyeHeight, 0.055); f.mouthCurve = 0.28
        case .tired?: f.eyeHeight = 0.040; f.eyeY += 0.025; f.mouthCurve = -0.16
        case .bored?: f.eyeHeight = 0.045; f.mouthCurve = -0.04
        case .stressed?: f.eyeHeight = 0.055; f.brow = 0.45; f.mouthCurve = -0.50
        case .angry?: f.eyeHeight = 0.075; f.brow = 1.0; f.mouthCurve = -0.48
        case .sad?: f.eyeY += 0.015; f.mouthCurve = -0.90; f.brow = 0.25
        case .low?: f.eyeHeight = 0.050; f.mouthCurve = -0.32
        case .fuckOff?: f.eyeHeight = 0.052; f.brow = 1.0; f.mouthCurve = -0.12; f.mouthWidth = 0.28
        default: break
        }
        return f
    }
}

/// MoodV2Face / MoodV2FaceCanvas.
struct MoodFace: View {
    let params: MoodFaceParams
    var ink = Color(argb: 0xFFF5F5F7)
    var browInk: Color? = nil

    init(mood: JournalMood, tint: Color = Color(argb: 0xFFF5F5F7)) {
        params = MoodFaceParams.at(Double(MoodV2.familyIndex(mood)), mood: mood)
        ink = tint
    }

    init(params: MoodFaceParams, browInk: Color) {
        self.params = params
        self.browInk = browInk
    }

    var body: some View {
        Canvas { ctx, size in Self.draw(&ctx, size: size, f: params, ink: ink, brow: browInk ?? ink) }
    }

    private static func draw(_ ctx: inout GraphicsContext, size: CGSize, f: MoodFaceParams, ink: Color, brow: Color) {
        let w = Double(size.width), h = Double(size.height)
        let stroke = w * 0.052
        let eyeW = w * f.eyeWidth, eyeH = h * f.eyeHeight, eyeY = h * f.eyeY
        let spacing = w * f.eyeSpacing
        let cx = w / 2
        for side in [-1.0, 1.0] {
            let ex = cx + side * spacing / 2
            let rect = CGRect(x: ex - eyeW / 2, y: eyeY - eyeH / 2, width: eyeW, height: eyeH)
            ctx.fill(Path(roundedRect: rect, cornerRadius: eyeH / 2), with: .color(ink))
        }
        if f.brow > 0.05 {
            let browY = eyeY - eyeH * 1.15
            let half = eyeW * 0.52
            let tilt = eyeH * 0.75 * f.brow
            var left = Path()
            left.move(to: CGPoint(x: cx - spacing / 2 - half, y: browY - tilt))
            left.addLine(to: CGPoint(x: cx - spacing / 2 + half, y: browY + tilt))
            var right = Path()
            right.move(to: CGPoint(x: cx + spacing / 2 - half, y: browY + tilt))
            right.addLine(to: CGPoint(x: cx + spacing / 2 + half, y: browY - tilt))
            ctx.stroke(left, with: .color(brow), lineWidth: stroke * 0.55)
            ctx.stroke(right, with: .color(brow), lineWidth: stroke * 0.55)
        }
        let my = h * f.mouthY
        let mh = w * f.mouthWidth / 2
        let curve = h * 0.12 * f.mouthCurve
        var mouth = Path()
        mouth.move(to: CGPoint(x: cx - mh, y: my))
        mouth.addCurve(to: CGPoint(x: cx + mh, y: my), control1: CGPoint(x: cx - mh * 0.45, y: my + curve), control2: CGPoint(x: cx + mh * 0.45, y: my + curve))
        ctx.stroke(mouth, with: .color(ink), style: StrokeStyle(lineWidth: stroke * f.mouthStroke, lineCap: .round))
    }
}

/// Moods V3 (MoodV3Screen.kt): the whole screen takes the mood's colour; `ink` is the face, text and controls on it.
enum MoodV3 {
    static let tones: [(paper: Color, ink: Color)] = [
        (Color(argb: 0xFFFF8A7A), Color(argb: 0xFF5A1712)),   // Kötü: salmon
        (Color(argb: 0xFFFFB45A), Color(argb: 0xFF5A2E05)),   // Zor: orange
        (Color(argb: 0xFFF2D96B), Color(argb: 0xFF4A3C08)),   // Normal: sand
        (Color(argb: 0xFFC8E86A), Color(argb: 0xFF2E3D07)),   // İyi: lime
        (Color(argb: 0xFF8EE6A0), Color(argb: 0xFF0E3B1C)),   // Çok iyi: mint
    ]

    static func tone(_ progress: Double) -> (paper: Color, ink: Color) {
        let p = min(max(progress, 0), 4)
        let l = min(max(Int(p.rounded(.down)), 0), 4)
        let r = min(l + 1, 4)
        let t = p - Double(l)
        return (tones[l].paper.lerp(tones[r].paper, t), tones[l].ink.lerp(tones[r].ink, t))
    }
}

/// The V2 face drawn chunkier and in ink: bigger eyes kept apart, a heavier mouth.
struct MoodV3Face: View {
    let params: MoodFaceParams
    let ink: Color

    var body: some View {
        Canvas { ctx, size in
            let f = params
            let w = Double(size.width), h = Double(size.height)
            let stroke = w * 0.075
            let eyeW = w * f.eyeWidth * 1.25, eyeH = h * f.eyeHeight * 1.25, eyeY = h * f.eyeY
            // Wide flat eyes (Normal, Zor) never touch: a clear gap instead of one long brow.
            let spacing = max(w * f.eyeSpacing * 1.12, eyeW * 1.45)
            let cx = w / 2
            for side in [-1.0, 1.0] {
                let ex = cx + side * spacing / 2
                ctx.fill(Path(roundedRect: CGRect(x: ex - eyeW / 2, y: eyeY - eyeH / 2, width: eyeW, height: eyeH), cornerRadius: eyeH / 2), with: .color(ink))
            }
            if f.brow > 0.05 {
                let browY = eyeY - eyeH * 1.1, half = eyeW * 0.5, tilt = eyeH * 0.7 * f.brow
                var left = Path()
                left.move(to: CGPoint(x: cx - spacing / 2 - half, y: browY - tilt))
                left.addLine(to: CGPoint(x: cx - spacing / 2 + half, y: browY + tilt))
                var right = Path()
                right.move(to: CGPoint(x: cx + spacing / 2 - half, y: browY + tilt))
                right.addLine(to: CGPoint(x: cx + spacing / 2 + half, y: browY - tilt))
                let style = StrokeStyle(lineWidth: stroke * 0.6, lineCap: .round)
                ctx.stroke(left, with: .color(ink), style: style)
                ctx.stroke(right, with: .color(ink), style: style)
            }
            let my = h * f.mouthY, mh = w * f.mouthWidth / 2, curve = h * 0.13 * f.mouthCurve
            var mouth = Path()
            mouth.move(to: CGPoint(x: cx - mh, y: my))
            mouth.addCurve(to: CGPoint(x: cx + mh, y: my), control1: CGPoint(x: cx - mh * 0.45, y: my + curve), control2: CGPoint(x: cx + mh * 0.45, y: my + curve))
            ctx.stroke(mouth, with: .color(ink), style: StrokeStyle(lineWidth: stroke * f.mouthStroke, lineCap: .round))
        }
    }
}

/// Moods V3 (same data and save as V2): one full-colour screen — a big face that morphs as the slider moves, the
/// feeling in large letters and "Kaydet" right there. Details (what describes it, what caused it) are one optional
/// step away under "Ayrıntı ekle"; "Şu an / Bugün" is a small switch instead of a first screen.
struct MoodAddSheet: View {
    @Environment(LifeStore.self) private var life
    @Environment(\.dismiss) private var dismiss
    var onSaved: ((JournalMood, MoodDetails) -> Void)? = nil

    @State private var details = false
    @State private var scope: MoodScope = .moment
    @State private var progress: Double = 2
    @State private var dragging = false
    @State private var mood: JournalMood = .neutral
    @State private var descriptorKeys: Set<String> = []
    @State private var factorKeys: Set<String> = []
    @State private var lastTick = 2
    @State private var shownFamily = 2
    @State private var titleForward = true
    @State private var motion: Task<Void, Never>?
    /// Set after saving (signed in): the sheet turns into the AI suggestions for that mood (MoodSuggestionsScreen).
    @State private var suggestFor: (mood: JournalMood, details: MoodDetails)?

    private var family: Int { min(max(Int(progress.rounded()), 0), 4) }
    private var reminderFlow: Bool { onSaved != nil }

    var body: some View {
        if let s = suggestFor {
            MoodSuggestionsView(mood: s.mood, details: s.details) { dismiss() }
                .transition(.opacity)
        } else {
            entry
        }
    }

    private var entry: some View {
        let tone = MoodV3.tone(progress)
        return VStack(spacing: 0) {
            // Top: close (or back from the details) on the left, the Şu an / Bugün switch on the right.
            HStack {
                Button { if details { withAnimation(.easeOut(duration: 0.25)) { details = false } } else { dismiss() } } label: {
                    Text(details ? "‹" : "×").font(.system(size: 30)).foregroundStyle(tone.ink).frame(width: 44, height: 44)
                }
                .accessibilityLabel(details ? "Geri" : "Kapat")
                Spacer()
                if !reminderFlow {
                    HStack(spacing: 0) {
                        ForEach([(MoodScope.moment, "Şu an"), (MoodScope.daily, "Bugün")], id: \.1) { value, label in
                            let on = scope == value
                            Button {
                                scope = value
                                UISelectionFeedbackGenerator().selectionChanged()
                            } label: {
                                Text(label).font(RealFont.inter(13, .medium)).foregroundStyle(on ? tone.paper : tone.ink.opacity(0.75))
                                    .padding(.horizontal, 14).padding(.vertical, 7)
                                    .background(Capsule().fill(on ? tone.ink : .clear))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(3)
                    .background(Capsule().fill(tone.ink.opacity(0.10)))
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 6)

            Group {
                if details { detailStep(tone).transition(.opacity.combined(with: .offset(y: 30))) }
                else { faceStep(tone).transition(.opacity) }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            // Bottom: "Ayrıntı ekle" (optional) and "Kaydet", always one tap away.
            HStack(spacing: 10) {
                if !details {
                    let count = descriptorKeys.count + factorKeys.count
                    Button { withAnimation(.easeOut(duration: 0.3)) { details = true } } label: {
                        Text(count > 0 ? "Ayrıntılar · \(count)" : "Ayrıntı ekle").font(RealFont.inter(15, .medium)).foregroundStyle(tone.ink)
                            .frame(maxWidth: .infinity, minHeight: 52)
                            .overlay(Capsule().strokeBorder(tone.ink.opacity(0.5), lineWidth: 1.5))
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
                Button(action: save) {
                    Text("Kaydet").font(RealFont.inter(15, .semibold)).foregroundStyle(tone.paper)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(Capsule().fill(tone.ink))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20).padding(.vertical, 14)
        }
        .background(tone.paper.ignoresSafeArea())
        .onChange(of: family) { old, new in
            titleForward = new > old
            withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.32)) { shownFamily = new }
        }
        .onDisappear { motion?.cancel() }
    }

    private func faceStep(_ tone: (paper: Color, ink: Color)) -> some View {
        let showHand = family == 0 && descriptorKeys.contains("fuck_off")
        return VStack(spacing: 0) {
            Text(reminderFlow ? "Bu REAL nasıl hissettirdi?" : scope == .moment ? "Şu an nasılsın?" : "Bugün genel olarak nasıldı?")
                .font(RealFont.inter(20, .medium)).foregroundStyle(tone.ink).multilineTextAlignment(.center)
                .padding(.top, 18)
            Spacer(minLength: 8)
            Group {
                if showHand { Text("🖕").font(.system(size: 170)) }
                else { MoodV3Face(params: MoodFaceParams.at(progress, mood: dragging ? nil : mood), ink: tone.ink) }
            }
            .frame(width: 300, height: 300)
            .accessibilityLabel("\(MoodV2.families[family].title) mood yüzü")
            Spacer(minLength: 8)
            // The feeling in big letters; a new word slides in from the side the mood moved to.
            ZStack {
                Text(MoodV2.families[shownFamily].title.uppercased(with: Locale(identifier: "tr_TR")))
                    .font(RealFont.inter(64, .black)).foregroundStyle(tone.ink.opacity(0.82))
                    .lineLimit(1).minimumScaleFactor(0.6)
                    .id(shownFamily)
                    .transition(.asymmetric(
                        insertion: .move(edge: titleForward ? .trailing : .leading).combined(with: .opacity),
                        removal: .move(edge: titleForward ? .leading : .trailing).combined(with: .opacity)))
            }
            .frame(maxWidth: .infinity)
            .clipped()
            slider(tone).padding(.top, 26)
            Color.clear.frame(height: 30)
        }
        .padding(.horizontal, 24)
    }

    private func slider(_ tone: (paper: Color, ink: Color)) -> some View {
        VStack(spacing: 8) {
            GeometryReader { geo in
                let knob: CGFloat = 44
                let usable = max(geo.size.width - knob, 1)
                let at: (CGFloat) -> Double = { x in min(max(Double((x - knob / 2) / usable) * 4, 0), 4) }
                ZStack(alignment: .leading) {
                    Capsule().fill(tone.ink.opacity(0.22)).frame(height: 4).padding(.horizontal, knob / 2)
                    HStack {
                        ForEach(0..<5, id: \.self) { i in
                            Circle().fill(tone.ink.opacity(0.35)).frame(width: 6, height: 6)
                            if i < 4 { Spacer() }
                        }
                    }
                    .padding(.horizontal, knob / 2 - 3)
                    Circle().fill(tone.ink).frame(width: knob, height: knob)
                        .overlay(MoodV3Face(params: MoodFaceParams.at(progress, mood: nil), ink: tone.paper).frame(width: 30, height: 30))
                        .offset(x: usable * progress / 4)
                }
                .frame(maxHeight: .infinity)
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 4)
                    .onChanged { v in
                        motion?.cancel()
                        dragging = true
                        progress = at(v.location.x)
                        tick()
                    }
                    .onEnded { _ in
                        dragging = false
                        settle()
                    })
                .simultaneousGesture(SpatialTapGesture().onEnded { v in glide(to: Int(at(v.location.x).rounded())) })
            }
            .frame(height: 44)
            HStack {
                Text("Kötü"); Spacer(); Text("Normal"); Spacer(); Text("Çok iyi")
            }
            .font(RealFont.inter(12)).foregroundStyle(tone.ink.opacity(0.6))
        }
    }

    private func tick() {
        if family != lastTick {
            lastTick = family
            UISelectionFeedbackGenerator().selectionChanged()
        }
    }

    /// Moves `progress` frame by frame (the face is drawn from it, so it has to change continuously).
    private func move(to target: Double, duration: Double, then: (() -> Void)? = nil) {
        motion?.cancel()
        let start = progress
        motion = Task { @MainActor in
            let t0 = Date()
            while !Task.isCancelled {
                let t = min(Date().timeIntervalSince(t0) / duration, 1)
                let e = t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
                progress = start + (target - start) * e
                tick()
                if t >= 1 { break }
                try? await Task.sleep(for: .milliseconds(16))
            }
            if !Task.isCancelled { then?() }
        }
    }

    /// Kötü → Çok iyi in one tap: glide through the faces in between, ticking at each stop.
    private func glide(to anchor: Int) {
        let target = Double(min(max(anchor, 0), 4))
        dragging = true
        move(to: target, duration: 0.26 + abs(target - progress) * 0.17) {
            dragging = false
            settle()
        }
    }

    private func settle() {
        let anchor = family
        if abs(progress - Double(anchor)) > 0.001 { move(to: Double(anchor), duration: 0.2) }
        let valid = Set(MoodV2.descriptors[anchor].map(\.0))
        descriptorKeys = descriptorKeys.intersection(valid)
        let target = MoodV2.families[anchor]
        if !target.moods.contains(mood) { mood = target.defaultMood }
    }

    private func save() {
        let saved: JournalMood = mood == .fuckOff ? .bad : mood
        let details = MoodDetails(scope: scope, descriptors: Array(descriptorKeys), energy: nil, factors: Array(factorKeys), loggedAt: .now)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        if let onSaved {
            onSaved(saved, details)
            dismiss()
        } else {
            life.addMood(saved, details: details)
            // The mood is already saved; signed-in accounts get ideas for it (consent is asked there).
            if RealAuth.shared.account != nil {
                withAnimation(.easeOut(duration: 0.25)) { suggestFor = (saved, details) }
            } else {
                dismiss()
            }
        }
    }

    private func detailStep(_ tone: (paper: Color, ink: Color)) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 12) {
                    MoodV3Face(params: MoodFaceParams.at(Double(family), mood: mood), ink: tone.ink).frame(width: 54, height: 54)
                    Text(MoodV2.families[family].title).font(RealFont.inter(30, .black)).foregroundStyle(tone.ink)
                }
                .padding(.top, 8)
                Text("Bunu en iyi ne anlatır?").font(RealFont.inter(20, .semibold)).foregroundStyle(tone.ink).padding(.top, 26)
                Text("En fazla 3 tane").font(RealFont.inter(12)).foregroundStyle(tone.ink.opacity(0.6)).padding(.top, 4).padding(.bottom, 12)
                ChipFlow(spacing: 8, lineSpacing: 8) {
                    ForEach(MoodV2.descriptors[family], id: \.0) { option in
                        chip(option.1, selected: descriptorKeys.contains(option.0), tone) {
                            if descriptorKeys.contains(option.0) { descriptorKeys.remove(option.0) }
                            else if descriptorKeys.count < 3 { descriptorKeys.insert(option.0) }
                        }
                    }
                }
                Text("Ne etkiledi?").font(RealFont.inter(20, .semibold)).foregroundStyle(tone.ink).padding(.top, 30)
                Text("Hissin kaynağı; birden fazla olabilir").font(RealFont.inter(12)).foregroundStyle(tone.ink.opacity(0.6)).padding(.top, 4)
                ForEach(Array(MoodV2.factorGroups.enumerated()), id: \.offset) { _, group in
                    Text(group.0).font(RealFont.inter(12, .medium)).foregroundStyle(tone.ink.opacity(0.55)).padding(.top, 16).padding(.bottom, 8)
                    ChipFlow(spacing: 8, lineSpacing: 8) {
                        ForEach(group.1, id: \.0) { option in
                            chip(option.1, selected: factorKeys.contains(option.0), tone) {
                                if factorKeys.contains(option.0) { factorKeys.remove(option.0) } else { factorKeys.insert(option.0) }
                            }
                        }
                    }
                }
                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollIndicators(.hidden)
    }

    private func chip(_ label: String, selected: Bool, _ tone: (paper: Color, ink: Color), action: @escaping () -> Void) -> some View {
        Button {
            action()
            UISelectionFeedbackGenerator().selectionChanged()
        } label: {
            Text(label).font(RealFont.inter(14, selected ? .semibold : .regular))
                .foregroundStyle(selected ? tone.paper : tone.ink)
                .padding(.horizontal, 14).padding(.vertical, 9)
                .background(Capsule().fill(selected ? tone.ink : .clear))
                .overlay(Capsule().strokeBorder(selected ? .clear : tone.ink.opacity(0.35), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Left-aligned flow layout for chips (FlowRow).
struct ChipFlow: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 9

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, maxW: CGFloat = 0
        for sub in subviews {
            let s = sub.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > width { y += rowH + lineSpacing; x = 0; rowH = 0 }
            x += s.width + spacing
            rowH = max(rowH, s.height)
            maxW = max(maxW, x - spacing)
        }
        return CGSize(width: proposal.width ?? maxW, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for sub in subviews {
            let s = sub.sizeThatFits(.unspecified)
            if x > bounds.minX && x + s.width > bounds.maxX { y += rowH + lineSpacing; x = bounds.minX; rowH = 0 }
            sub.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            x += s.width + spacing
            rowH = max(rowH, s.height)
        }
    }
}
