import SwiftUI

// Me > Alkol (YasamAlcohol.kt): camera first; while the photo is read the screen fills with a grey liquid you drag up or
// down for the amount, which turns the drink's colour once it is known; then a name step with suggestions and kinds.

private enum AlcoholStage { case idle, pour, name }

func yasamMlText(_ ml: Int) -> String { ml >= 1000 ? "1 L" : "\(ml) ml" }

struct YasamAlcoholScreen: View {
    var onClose: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var entries = YasamDrinkLog.load()
    @State private var stage: AlcoholStage = .idle
    @State private var camera = false
    @State private var photo: String?
    @State private var armedId: String?
    @State private var history = false
    @State private var swipeOpen: String?
    @State private var scanning = false
    @State private var guess: DrinkGuess?
    @State private var kind: DrinkKind?
    @State private var ml = 330
    @State private var mlTouched = false
    @State private var name = ""
    @FocusState private var nameFocused: Bool

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            switch stage {
            case .idle: idle.transition(.opacity)
            case .pour:
                AlcoholPourStage(photo: photo.map { YasamDrinkLog.dir.appendingPathComponent($0) }, scanning: scanning, guess: guess,
                                 kind: kind, ml: ml, reduceMotion: reduceMotion,
                                 onMl: { ml = $0; mlTouched = true }, onBack: { reset(dropPhoto: true) },
                                 onNext: { withAnimation(.easeOut(duration: 0.25)) { stage = .name } })
                .transition(.opacity)
            case .name: nameStage.transition(.opacity)
            }
            if history {
                YasamDrinkHistory(
                    title: t("Alkol kaydı"),
                    items: entries.map { HistoryItem(at: $0.at, color: $0.drink.liquid, photo: $0.photoURL, group: $0.name, amount: $0.ml) },
                    stats: { period, _ in
                        let cal = Calendar.real
                        let drinkDays = Set(period.map { cal.epochDay(of: $0.at.date) }).count
                        let since = entries.last.map { cal.epochDay(of: Date()) - cal.epochDay(of: $0.at.date) }
                        return [
                            HistoryStat(big: "\(period.count)", small: t("içki · {0} günde", drinkDays)),
                            since.map { $0 > 0 } == true
                                ? HistoryStat(big: "\(since!)", small: t("gündür içmeden"), tint: Color(argb: 0xFF78C8A0))
                                : HistoryStat(big: "\(drinkDays)", small: t("içtiğin gün"))
                        ]
                    },
                    onClose: { withAnimation(YasamScreen.closeAnim) { history = false } }
                )
                .transition(YasamScreen.meTransition(false))
                .zIndex(2)
            }
        }
        .fullScreenCover(isPresented: $camera) {
            CameraPicker { data in took(data) }.ignoresSafeArea()
        }
    }

    // MARK: Idle

    private var idle: some View {
        let today = YasamDrinkLog.today(entries)
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 12) {
                    YasamBackButton(action: onClose)
                    Text(t("Alkol")).font(RealFont.inter(28, .bold)).foregroundStyle(.white)
                    Spacer()
                    YasamHistoryButton { withAnimation(YasamScreen.openAnim) { history = true } }
                }
                Button { camera = true } label: {
                    VStack(spacing: 0) {
                        RealSymbol("camera", size: 28).foregroundStyle(.black).frame(width: 64, height: 64).background(Circle().fill(.white))
                        Text(t("Ne içiyorsun?")).font(RealFont.inter(17, .medium)).foregroundStyle(.white).padding(.top, 12)
                        Text(t("Fotoğrafını çek, adını biz önerelim")).font(RealFont.inter(13)).foregroundStyle(YasamTone.muted).padding(.top, 4)
                    }
                    .frame(maxWidth: .infinity)
                    .aspectRatio(1 / 1.05, contentMode: .fit)
                    .background(RoundedRectangle(cornerRadius: 30, style: .continuous).fill(Color(argb: 0xFF0D0D10)))
                    .overlay(RoundedRectangle(cornerRadius: 30, style: .continuous).strokeBorder(.white.opacity(0.18), lineWidth: 1))
                }
                .buttonStyle(.plain)
                .padding(.top, 16)
                if let last = entries.last {
                    Button {
                        let keep = last.photoURL.map { FileManager.default.fileExists(atPath: $0.path) } == true ? last.photo : nil
                        YasamDrinkLog.add(YasamDrinkLog.newEntry(last.drink, name: last.name, ml: last.ml, photo: keep))
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        entries = YasamDrinkLog.load()
                    } label: {
                        HStack(spacing: 12) {
                            DrinkThumb(entry: last, size: 44)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(t("Yine aynısı")).font(RealFont.inter(15, .semibold)).foregroundStyle(.white)
                                Text("\(last.name) · \(yasamMlText(last.ml))").font(RealFont.inter(12)).foregroundStyle(YasamTone.muted).lineLimit(1)
                            }
                            Spacer()
                            Text("+").font(RealFont.inter(20, .bold)).foregroundStyle(.black).frame(width: 34, height: 34).background(Circle().fill(.white))
                        }
                        .padding(8)
                        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(.white.opacity(0.06)))
                        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.white.opacity(0.18), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 10)
                }
                Button {
                    photo = nil; guess = nil; kind = nil; ml = 330; mlTouched = false; name = ""
                    withAnimation(.easeOut(duration: 0.25)) { stage = .name }
                } label: {
                    Text(t("Fotoğrafsız ekle")).font(RealFont.inter(13, .semibold)).foregroundStyle(YasamTone.muted).padding(12)
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)
                .padding(.top, 6)
                if !today.isEmpty {
                    YasamCaps(text: t("BUGÜN")).padding(.top, 18).padding(.bottom, 8)
                    VStack(spacing: 6) { ForEach(today.reversed()) { e in row(e) } }
                }
                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 16).padding(.top, 8)
            .animation(.snappy, value: entries)
        }
        .scrollIndicators(.hidden)
        .onTapGesture { armedId = nil }
    }

    private func row(_ e: DrinkLogEntry) -> some View {
        let armed = armedId == e.id
        return SwipeDeleteRow(id: e.id, openId: $swipeOpen, radius: 20, onDelete: { remove(e.id) }) {
            HStack(spacing: 12) {
                DrinkThumb(entry: e, size: 46)
                VStack(alignment: .leading, spacing: 1) {
                    Text("\(e.name) · \(yasamMlText(e.ml))").font(RealFont.inter(15, .semibold)).foregroundStyle(.white).lineLimit(1)
                    Text("\(YasamLogText.time(e.at)) · \(e.drink.label)").font(RealFont.inter(12)).foregroundStyle(YasamTone.muted)
                }
                Spacer()
                if armed {
                    Button { remove(e.id) } label: {
                        Text(t("Sil")).font(RealFont.inter(14, .semibold)).foregroundStyle(.white)
                            .padding(.horizontal, 14).padding(.vertical, 8).background(RoundedRectangle(cornerRadius: 14).fill(Color(argb: 0xFFE5484D)))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.white.opacity(armed ? 0.1 : 0.05)))
            .contentShape(Rectangle())
            .onTapGesture { armedId = nil }
            .onLongPressGesture { armedId = e.id; UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
        }
    }

    // MARK: Name

    private var nameStage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 12) {
                    YasamBackButton { if photo != nil { withAnimation { stage = .pour } } else { reset(dropPhoto: true) } }
                    Text(t("Alkol")).font(RealFont.inter(28, .bold)).foregroundStyle(.white)
                }
                if let url = photo.map({ YasamDrinkLog.dir.appendingPathComponent($0) }), let image = UIImage(contentsOfFile: url.path) {
                    Color.clear.frame(height: 220)
                        .overlay { Image(uiImage: image).resizable().scaledToFill() }
                        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                        .overlay(alignment: .bottomTrailing) {
                            Text(yasamMlText(ml)).font(RealFont.inter(12, .semibold)).foregroundStyle(.white)
                                .padding(.horizontal, 10).padding(.vertical, 5)
                                .background(RoundedRectangle(cornerRadius: 12).fill(.black.opacity(0.55))).padding(12)
                        }
                        .padding(.top, 14)
                }
                Text(question).font(RealFont.inter(19, .semibold)).foregroundStyle(.white).lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 16).padding(.bottom, 10)
                let suggestions = (guess?.names ?? []).isEmpty
                    ? (photo == nil ? Array(NSOrderedSet(array: entries.reversed().map(\.name)).array.compactMap { $0 as? String }.prefix(3)) : [])
                    : guess!.names
                if !suggestions.isEmpty {
                    ChipFlow(spacing: 6, lineSpacing: 6) {
                        ForEach(suggestions, id: \.self) { s in DrinkChip(label: s, on: s == name) { name = s } }
                    }
                }
                ChipFlow(spacing: 6, lineSpacing: 6) {
                    ForEach(DrinkKind.allCases) { k in
                        DrinkChip(label: k.label, on: k == kind) {
                            kind = k
                            if !mlTouched { ml = k.portions.first!.1 }
                        }
                    }
                }
                .padding(.top, 10)
                if photo == nil {
                    ChipFlow(spacing: 6, lineSpacing: 6) {
                        ForEach(kind?.portions ?? DrinkKind.unknownPortions, id: \.1) { label, v in
                            DrinkChip(label: t(label), on: v == ml) { ml = v; mlTouched = true }
                        }
                    }
                    .padding(.top, 10)
                }
                ZStack(alignment: .leading) {
                    if name.isEmpty { Text(t("Adı (isteğe bağlı)")).font(RealFont.inter(16)).foregroundStyle(Color(argb: 0xFF6F6F78)) }
                    TextField("", text: $name).font(RealFont.inter(16)).foregroundStyle(.white).tint(.white)
                        .focused($nameFocused).submitLabel(.done)
                        .onChange(of: name) { _, v in if v.count > 32 { name = String(v.prefix(32)) } }
                }
                .padding(.horizontal, 16).frame(minHeight: 50)
                .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color(argb: 0xFF0D0D10)))
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Color(argb: 0xFF2A2A31), lineWidth: 1))
                .padding(.top, 14)
                let ready = kind != nil || !name.trimmingCharacters(in: .whitespaces).isEmpty
                Button { save() } label: {
                    Text(t("Kaydet · bugün içtim")).font(RealFont.inter(16, .bold)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity).frame(height: 54).background(Capsule().fill(.white.opacity(ready ? 1 : 0.35)))
                }
                .buttonStyle(.plain).disabled(!ready)
                .padding(.top, 14)
                Button { reset(dropPhoto: true) } label: {
                    Text(t("Vazgeç")).font(RealFont.inter(13, .semibold)).foregroundStyle(YasamTone.muted).padding(12)
                }
                .buttonStyle(.plain).frame(maxWidth: .infinity).padding(.top, 6)
            }
            .padding(.horizontal, 16).padding(.top, 8)
        }
        .scrollIndicators(.hidden)
        .scrollDismissesKeyboard(.interactively)
    }

    private var question: String {
        if let g = guess, g.recognized, let k = g.kind { return t("Bu bir {0} gibi. Adı ne olsun?", k.label.lowercased(with: I18n.locale)) }
        if photo != nil { return t("Ne olduğunu çıkaramadım. Türünü seç ya da adını yaz.") }
        return t("Ne içtin? Türünü seç, istersen ad yaz.")
    }

    // MARK: Actions

    private func took(_ data: Data) {
        guard let image = UIImage(data: data), let kept = YasamDrinkLog.keepPhoto(image) else { return }
        photo = kept; guess = nil; kind = nil; name = ""; ml = 330; mlTouched = false
        scanning = true
        withAnimation(.easeOut(duration: 0.25)) { stage = .pour }
        Task { @MainActor in
            // Read in the background while the liquid asks for the amount.
            let g = try? await DrinkRecognizer.recognize(image)
            guard photo == kept else { return }
            scanning = false
            guess = g
            if let g, g.recognized, let k = g.kind {
                kind = k
                if !mlTouched { ml = g.portionMl > 0 ? g.portionMl : k.portions.first!.1 }
                name = g.names.first ?? ""
            }
        }
    }

    private func reset(dropPhoto: Bool) {
        if dropPhoto, let p = photo { try? FileManager.default.removeItem(at: YasamDrinkLog.dir.appendingPathComponent(p)) }
        photo = nil; guess = nil; kind = nil; name = ""; ml = 330; mlTouched = false; scanning = false
        nameFocused = false
        withAnimation(.easeOut(duration: 0.25)) { stage = .idle }
    }

    private func save() {
        let k = kind ?? .other
        let typed = name.trimmingCharacters(in: .whitespaces)
        YasamDrinkLog.add(YasamDrinkLog.newEntry(k, name: typed.isEmpty ? k.label : typed, ml: ml, photo: photo))
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        entries = YasamDrinkLog.load()
        photo = nil
        reset(dropPhoto: false)
    }

    private func remove(_ id: String) {
        YasamDrinkLog.remove(id)
        armedId = nil
        entries = YasamDrinkLog.load()
    }
}

/// The liquid: grey while the photo is read, the drink's colour after. Drag up / down for the amount.
private struct AlcoholPourStage: View {
    let photo: URL?
    let scanning: Bool
    let guess: DrinkGuess?
    let kind: DrinkKind?
    let ml: Int
    let reduceMotion: Bool
    var onMl: (Int) -> Void
    var onBack: () -> Void
    var onNext: () -> Void
    @State private var startMl: Int?

    private var maxMl: Int {
        guard let kind else { return 700 }
        if kind.spirit { return 140 }
        return [.wine, .sparkling, .sake].contains(kind) ? 450 : 700
    }

    private var level: Double { 0.1 + 0.72 * min(max(Double(ml) / Double(maxMl), 0), 1) }
    private var liquidColor: Color { scanning || kind == nil ? Color(argb: 0xFF8A8A96) : kind!.liquid }
    private var foam: Bool { (kind == .beer || kind == .cider) && !scanning }
    private var portions: [(String, Int)] { scanning ? DrinkKind.unknownPortions : kind?.portions ?? DrinkKind.unknownPortions }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                liquid
                controls
            }
            .contentShape(Rectangle())
            .gesture(drag(height: geo.size.height))
        }
    }

    private func drag(height: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { g in
                if startMl == nil { startMl = ml }
                let up = Double(-g.translation.height)
                let raw = Double(startMl ?? ml) + up / Double(max(height, 1)) * Double(maxMl) * 1.3
                let clamped = min(max(Int(raw / 10) * 10, 20), 1000)
                if clamped != ml { onMl(clamped); UISelectionFeedbackGenerator().selectionChanged() }
            }
            .onEnded { _ in startMl = nil }
    }

    private var liquid: some View {
        let level = self.level
        let foam = self.foam
        let color = liquidColor
        return TimelineView(.animation(paused: reduceMotion)) { tl in
            let phase: Double = reduceMotion ? 0 : tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 3.2) / 3.2 * 2 * Double.pi
            ZStack {
                PourLiquid(level: level, phase: phase, foam: foam).fill(color)
                if foam { PourFoam(level: level, phase: phase).fill(Color(argb: 0xFFFFF3D6)) }
            }
        }
        .ignoresSafeArea()
        .animation(reduceMotion ? nil : .easeOut(duration: 0.25), value: level)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.6), value: color)
    }

    private var thumb: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color(argb: 0xFF1B1B21))
            if let photo, let image = UIImage(contentsOfFile: photo.path) {
                Image(uiImage: image).resizable().scaledToFill()
            }
            if scanning { ProgressView().tint(.white) }
        }
        .frame(width: 60, height: 60).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var subtitle: String {
        scanning ? t("Bu arada ne kadar içtin?") : kind != nil ? t("Ne kadar içtin?") : t("Miktarı seç, türünü sonra soracağız")
    }

    private var controls: some View {
        VStack(alignment: .leading, spacing: 0) {
            YasamBackButton(action: onBack)
            HStack(spacing: 12) {
                thumb
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(RealFont.inter(19, .bold)).foregroundStyle(.white)
                    Text(subtitle).font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.75))
                }
            }
            .padding(.top, 14)
            VStack(spacing: 2) {
                Text(yasamMlText(ml)).font(.system(size: 68, weight: .thin)).foregroundStyle(.white)
                    .contentTransition(.numericText()).animation(.snappy, value: ml)
                Text(t("yukarı aşağı sürükle")).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.7))
            }
            .frame(maxWidth: .infinity).padding(.top, 40)
            Spacer()
            ChipFlow(spacing: 6, lineSpacing: 6) {
                ForEach(portions, id: \.1) { item in
                    DrinkChip(label: t(item.0), on: item.1 == ml, onDark: true) { onMl(item.1) }
                }
            }
            .frame(maxWidth: .infinity)
            Button(action: onNext) {
                Text(scanning ? t("Tanınıyor…") : t("Devam")).font(RealFont.inter(16, .bold)).foregroundStyle(.black)
                    .frame(maxWidth: .infinity).frame(height: 54).background(Capsule().fill(.white.opacity(scanning ? 0.55 : 1)))
            }
            .buttonStyle(.plain).disabled(scanning)
            .padding(.top, 14).padding(.bottom, 10)
        }
        .padding(.horizontal, 16).padding(.top, 8)
    }

    private var title: String {
        if scanning { return t("Tanınıyor…") }
        if let guess, guess.recognized, let kind { return guess.names.first ?? kind.label }
        return t("Tanıyamadım")
    }
}

private struct PourLiquid: Shape {
    var level: Double
    var phase: Double
    var foam: Bool
    var animatableData: Double {
        get { level }
        set { level = newValue }
    }
    func path(in rect: CGRect) -> Path {
        let top = rect.height * (1 - level)
        let amp: CGFloat = 9
        var p = Path()
        p.move(to: CGPoint(x: 0, y: top))
        var x: CGFloat = 0
        while x <= rect.width {
            p.addLine(to: CGPoint(x: x, y: top + amp * sin(x / rect.width * 2 * .pi * 1.4 + phase)))
            x += 6
        }
        p.addLine(to: CGPoint(x: rect.width, y: rect.height))
        p.addLine(to: CGPoint(x: 0, y: rect.height))
        p.closeSubpath()
        return p
    }
}

/// Beer and cider: a foam band riding the wave.
private struct PourFoam: Shape {
    var level: Double
    var phase: Double
    var animatableData: Double {
        get { level }
        set { level = newValue }
    }
    func path(in rect: CGRect) -> Path {
        let top = rect.height * (1 - level)
        let amp: CGFloat = 9
        var p = Path()
        var x: CGFloat = 0
        p.move(to: CGPoint(x: 0, y: top - amp + amp * sin(phase)))
        while x <= rect.width {
            p.addLine(to: CGPoint(x: x, y: top - amp * 0.6 + amp * sin(x / rect.width * 2 * .pi * 1.4 + phase)))
            x += 6
        }
        x = rect.width
        while x >= 0 {
            p.addLine(to: CGPoint(x: x, y: top + amp * 1.4 + amp * sin(x / rect.width * 2 * .pi * 1.4 + phase)))
            x -= 6
        }
        p.closeSubpath()
        return p
    }
}

struct DrinkChip: View {
    let label: String
    let on: Bool
    var onDark = false
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(RealFont.inter(13, .semibold)).foregroundStyle(on ? .black : .white)
                .padding(.horizontal, 12).padding(.vertical, 7)
                .background(RoundedRectangle(cornerRadius: 16).fill(on ? Color.white : onDark ? Color.black.opacity(0.35) : Color.white.opacity(0.06)))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(on ? Color.white : Color.white.opacity(0.25), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

private struct DrinkThumb: View {
    let entry: DrinkLogEntry
    let size: CGFloat
    var body: some View {
        ZStack {
            entry.drink.liquid.opacity(0.55)
            if let url = entry.photoURL, let image = UIImage(contentsOfFile: url.path) {
                Image(uiImage: image).resizable().scaledToFill()
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
