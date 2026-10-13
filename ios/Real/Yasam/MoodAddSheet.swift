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

/// MoodV2FullScreen: step 0 scope, step 1 face + slider + descriptors (max 3), step 2 factors. Back / × / İleri–Kaydet.
struct MoodAddSheet: View {
    @Environment(LifeStore.self) private var life
    @Environment(\.dismiss) private var dismiss
    var onSaved: ((JournalMood, MoodDetails) -> Void)? = nil

    @State private var step = 0
    @State private var scope: MoodScope?
    @State private var progress: Double = 2
    @State private var dragging = false
    @State private var mood: JournalMood = .neutral
    @State private var descriptorKeys: Set<String> = []
    @State private var factorKeys: Set<String> = []
    @State private var expandedGroups: Set<String> = []
    @State private var lastHaptic = 2
    /// Set after saving (signed in): the sheet turns into the AI suggestions for that mood (MoodSuggestionsScreen).
    @State private var suggestFor: (mood: JournalMood, details: MoodDetails)?

    private var nearest: Int { min(max(Int(progress.rounded()), 0), 4) }
    private var ambient: Color { MoodV2.lerpColor(progress) }

    var body: some View {
        if let s = suggestFor {
            MoodSuggestionsView(mood: s.mood, details: s.details) { dismiss() }
                .transition(.opacity)
        } else {
            entry
        }
    }

    private var entry: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            LinearGradient(colors: [Color.black.lerp(ambient, 0.42), Color(argb: 0xFF0B0B0D), .black], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
                .animation(.easeOut(duration: 0.12), value: nearest)
            ScrollView {
                VStack(spacing: 0) {
                    Color.clear.frame(height: 52)
                    switch step {
                    case 0: scopeStep
                    case 1: faceStep
                    default: factorStep
                    }
                    Color.clear.frame(height: 108)
                }
                .padding(.horizontal, 22)
            }
            .scrollIndicators(.hidden)
        }
        .overlay(alignment: .topLeading) {
            Button { if step == 0 { dismiss() } else { step -= 1 } } label: {
                RealSymbol("chevron.left", size: 20.7).foregroundStyle(.white)
                    .frame(width: 40, height: 40).liquidGlass(Circle(), role: .chrome)
            }
            .padding(.leading, 8).padding(.top, 2)
            .accessibilityLabel("Geri")
        }
        .overlay(alignment: .topTrailing) {
            Button { dismiss() } label: { Text("×").font(.system(size: 28)).foregroundStyle(Color(argb: 0xFFE0E0E5)).frame(width: 44, height: 44) }
                .padding(.trailing, 8).padding(.top, 2)
        }
        .overlay(alignment: .bottomTrailing) {
            let enabled = step != 0 || scope != nil
            Button(action: advance) {
                Text(step >= 2 ? "Kaydet" : "İleri").font(RealFont.inter(14, .semibold))
                    .foregroundStyle(enabled ? Color(argb: 0xFF111114) : Color(argb: 0xFF9A9AA2))
                    .padding(.horizontal, 20).frame(height: 42)
                    .background(RoundedRectangle(cornerRadius: 22).fill(enabled ? Color(argb: 0xFFF3F3F5) : Color(argb: 0xFF4A4A50)))
            }
            .disabled(!enabled)
            .padding(.trailing, 20).padding(.bottom, 18)
        }
        .animation(.timingCurve(0.2, 0, 0, 1, duration: 0.22), value: step)
    }

    private func advance() {
        switch step {
        case 0: if scope != nil { step = 1 }
        case 1:
            let valid = Set(MoodV2.descriptors[nearest].map(\.0))
            descriptorKeys = descriptorKeys.intersection(valid)
            step = 2
        default:
            let saved: JournalMood = mood == .fuckOff ? .bad : mood
            let details = MoodDetails(scope: scope ?? .daily, descriptors: Array(descriptorKeys), energy: nil, factors: Array(factorKeys), loggedAt: .now)
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
    }

    private var scopeStep: some View {
        VStack(spacing: 0) {
            Color.clear.frame(height: 20)
            Text("Bir iz bırak").font(RealFont.inter(30, .semibold)).foregroundStyle(Color(argb: 0xFFF4F4F6))
            Text("Neyi kaydetmek istiyorsun?").font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFF9A9AA3)).padding(.top, 8)
            Color.clear.frame(height: 34)
            scopeCard(.moment, "Anlık his", "Şu anda ya da belirli bir anda nasıl hissettiğin")
            Color.clear.frame(height: 12)
            scopeCard(.daily, "Günün havası", "Bugünün genel hissi")
        }
    }

    private func scopeCard(_ value: MoodScope, _ title: String, _ subtitle: String) -> some View {
        let on = scope == value
        return Button {
            scope = value
            UISelectionFeedbackGenerator().selectionChanged()
        } label: {
            HStack(spacing: 14) {
                ZStack {
                    Circle().strokeBorder(on ? ambient : Color(argb: 0xFF777780), lineWidth: 1.5)
                    if on { Circle().fill(ambient).padding(4) }
                }
                .frame(width: 18, height: 18)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(RealFont.inter(17, .semibold)).foregroundStyle(Color(argb: 0xFFF4F4F6))
                    Text(subtitle).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF92929B)).multilineTextAlignment(.leading)
                }
                Spacer()
            }
            .padding(18)
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(on ? ambient.opacity(0.22) : Color(argb: 0xFF18181C)))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(on ? ambient.opacity(0.72) : Color(argb: 0xFF303036), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var faceStep: some View {
        let family = MoodV2.families[nearest]
        let face = MoodFaceParams.at(progress, mood: dragging ? nil : mood)
        let showHand = nearest == 0 && descriptorKeys.contains("fuck_off")
        return VStack(spacing: 0) {
            Text(scope == .moment ? "Şu an içeride hava nasıl?" : "Bugünün havası nasıldı?")
                .font(RealFont.inter(18, .medium)).foregroundStyle(Color(argb: 0xFFE0E0E5))
            Group {
                if showHand {
                    Text("🖕").font(.system(size: 130)).frame(width: 216, height: 216)
                } else {
                    MoodFace(params: face, browInk: ambient.opacity(0.96)).frame(width: 216, height: 216)
                }
            }
            .padding(.top, 22)
            Text(family.title.uppercased(with: Locale(identifier: "tr_TR"))).font(RealFont.inter(30, .semibold)).kerning(0.6).foregroundStyle(ambient)
                .padding(.top, 14)
            slider.padding(.top, 24)
            VStack(alignment: .leading, spacing: 5) {
                Text("Biraz daha netleştir").font(RealFont.inter(16, .medium)).foregroundStyle(Color(argb: 0xFFE6E6EB))
                Text("Bu hissi en iyi anlatanları seç. En fazla 3 tane.").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF85858E))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 28)
            ChipFlow(spacing: 8, lineSpacing: 9) {
                ForEach(MoodV2.descriptors[nearest], id: \.0) { option in
                    chip(option.1, selected: descriptorKeys.contains(option.0)) {
                        if descriptorKeys.contains(option.0) { descriptorKeys.remove(option.0) }
                        else if descriptorKeys.count < 3 { descriptorKeys.insert(option.0) }
                    }
                }
            }
            .padding(.top, 13)
            if !descriptorKeys.isEmpty {
                Text(descriptorKeys.prefix(3).map(MoodV2.descriptorLabel).joined(separator: " · "))
                    .font(RealFont.inter(12)).foregroundStyle(ambient.opacity(0.88))
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.top, 13)
            }
        }
    }

    private var slider: some View {
        GeometryReader { geo in
            let thumb: CGFloat = 28
            let usable = geo.size.width - thumb
            ZStack(alignment: .leading) {
                Capsule().fill(LinearGradient(colors: MoodV2.families.map { $0.tint.opacity(0.68) }, startPoint: .leading, endPoint: .trailing))
                    .frame(height: 6)
                HStack {
                    ForEach(0..<5, id: \.self) { i in
                        Circle().fill(i == nearest ? Color.white : MoodV2.families[i].tint.opacity(0.72))
                            .frame(width: i == nearest ? 8 : 5, height: i == nearest ? 8 : 5)
                        if i < 4 { Spacer() }
                    }
                }
                Circle().fill(Color(argb: 0xFFF4F4F6)).frame(width: thumb, height: thumb)
                    .overlay(Circle().strokeBorder(ambient.opacity(0.8), lineWidth: 3))
                    .offset(x: usable * progress / 4)
            }
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { v in
                    dragging = true
                    setProgress(Double(v.location.x / max(geo.size.width, 1)) * 4, finish: false)
                }
                .onEnded { v in
                    dragging = false
                    setProgress(Double(v.location.x / max(geo.size.width, 1)) * 4, finish: true)
                })
        }
        .frame(height: 58)
    }

    private func setProgress(_ value: Double, finish: Bool) {
        progress = min(max(value, 0), 4)
        let anchor = min(max(Int(progress.rounded()), 0), 4)
        if anchor != lastHaptic {
            lastHaptic = anchor
            UISelectionFeedbackGenerator().selectionChanged()
        }
        if finish {
            withAnimation(.easeOut(duration: 0.19)) { progress = Double(anchor) }
            let family = MoodV2.families[anchor]
            let valid = Set(MoodV2.descriptors[anchor].map(\.0))
            descriptorKeys = descriptorKeys.intersection(valid)
            if !family.moods.contains(mood) { mood = family.defaultMood }
        }
    }

    private var factorStep: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 14) {
                if descriptorKeys.contains("fuck_off") { Text("🖕").font(.system(size: 56)) }
                else { MoodFace(mood: mood, tint: ambient).frame(width: 78, height: 78) }
                VStack(alignment: .leading, spacing: 2) {
                    Text(scope?.label ?? "Mood").font(RealFont.inter(12, .medium)).foregroundStyle(Color(argb: 0xFF85858E))
                    Text(MoodV2.families[MoodV2.familyIndex(mood)].title).font(RealFont.inter(24, .semibold)).foregroundStyle(Color(argb: 0xFFF5F5F7))
                    if !descriptorKeys.isEmpty {
                        Text(descriptorKeys.prefix(3).map(MoodV2.descriptorLabel).joined(separator: " · ")).font(RealFont.inter(13)).foregroundStyle(ambient.opacity(0.92))
                    }
                }
            }
            Text("Bunu en çok ne etkiledi?").font(RealFont.inter(22, .semibold)).foregroundStyle(Color(argb: 0xFFF1F1F4)).padding(.top, 22)
            Text("Hissi değil, kaynağını seç. Birden fazla olabilir.").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF85858E)).padding(.top, 7)
            ForEach(Array(MoodV2.factorGroups.enumerated()), id: \.offset) { index, group in
                Text(group.0).font(RealFont.inter(12, .medium)).foregroundStyle(Color(argb: 0xFF777780)).padding(.top, index == 0 ? 18 : 20)
                let expanded = expandedGroups.contains(group.0)
                let base = Array(group.1.prefix(9))
                let extras = group.1.dropFirst(9).filter { factorKeys.contains($0.0) }
                let visible = expanded ? group.1 : base + extras
                ChipFlow(spacing: 8, lineSpacing: 9) {
                    ForEach(visible, id: \.0) { option in
                        chip(option.1, selected: factorKeys.contains(option.0)) {
                            if factorKeys.contains(option.0) { factorKeys.remove(option.0) } else { factorKeys.insert(option.0) }
                        }
                    }
                    if group.1.count > 9 {
                        Button {
                            if expanded { expandedGroups.remove(group.0) } else { expandedGroups.insert(group.0) }
                        } label: {
                            Text(expanded ? "Daha az" : "+ \(group.1.count - 9) daha").font(RealFont.inter(12, .medium)).foregroundStyle(ambient.opacity(0.9))
                                .padding(.horizontal, 13).padding(.vertical, 10)
                                .background(Capsule().fill(Color(argb: 0xFF151519)))
                                .overlay(Capsule().strokeBorder(ambient.opacity(0.28), lineWidth: 0.8))
                        }
                    }
                }
                .padding(.top, 8)
            }
        }
    }

    private func chip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button {
            action()
            UISelectionFeedbackGenerator().selectionChanged()
        } label: {
            Text(label).font(RealFont.inter(13, selected ? .medium : .regular))
                .foregroundStyle(selected ? Color(argb: 0xFFF5F5F7) : Color(argb: 0xFFB1B1BA))
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(Capsule().fill(selected ? ambient.opacity(0.24) : Color(argb: 0xFF1D1D21)))
                .overlay(Capsule().strokeBorder(selected ? ambient.opacity(0.72) : Color(argb: 0xFF303037), lineWidth: 0.8))
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
