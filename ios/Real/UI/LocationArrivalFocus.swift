import SwiftUI
import MapKit
import CoreLocation

/// What a Varınca / Ayrılınca notification tap (or the developer test) opens: the card and the place it fired for.
/// The place travels in the notification itself, since the card's trigger is cleared once it fired.
struct ArrivalFocusContext: Identifiable, Equatable {
    var id: Int64
    var placeName: String
    var onExit: Bool
    var latitude: Double
    var longitude: Double
    /// Developer test without a suitable card: a sample list, nothing is written.
    var sample = false

    init(id: Int64, placeName: String, onExit: Bool, latitude: Double, longitude: Double, sample: Bool = false) {
        self.id = id; self.placeName = placeName; self.onExit = onExit
        self.latitude = latitude; self.longitude = longitude; self.sample = sample
    }

    init?(userInfo: [AnyHashable: Any]) {
        guard let id = userInfo["reminderId"] as? Int64,
              let lat = userInfo["placeLat"] as? Double, let lng = userInfo["placeLng"] as? Double else { return nil }
        self.init(id: id, placeName: userInfo["placeName"] as? String ?? "", onExit: userInfo["placeOnExit"] as? Bool ?? false,
                  latitude: lat, longitude: lng, sample: userInfo["arrivalSample"] as? Bool ?? false)
    }

    var userInfo: [AnyHashable: Any] {
        ["reminderId": id, "placeName": placeName, "placeOnExit": onExit, "placeLat": latitude, "placeLng": longitude, "arrivalSample": sample]
    }
}

private enum FocusPhase { case intro, list, finale }

/// LocationArrivalFocusScreen.kt:
/// 1. Vardın anı: an Apple Maps snapshot fills the screen, the route (when the phone came from far, e.g. the test)
///    draws in, the place pulses and its name rises. A tap skips it; the map then shrinks into a strip at the top.
/// 2. The checklist one item at a time, Tinder-style: swipe right (or Tamamla) completes, left (or Geç) skips; the next
///    items wait behind, smaller and blurred. A card without a checklist shows its text as one item (Tamam).
/// 3. Finale: ring, tick and burst, then the screen shrinks into a dot and closes by itself.
struct LocationArrivalFocusScreen: View {
    let context: ArrivalFocusContext
    let onOpenCard: () -> Void
    let onClose: () -> Void
    @Environment(ReminderStore.self) private var store

    @State private var phase: FocusPhase = .intro
    /// true = completed in this session, false = skipped ("Geç": stays open on the card).
    @State private var status: [Int64: Bool] = [:]
    @State private var history: [Int64] = []
    @State private var drag: CGFloat = 0
    @State private var busy = false
    @State private var armed = false
    @State private var initiallyDone: Set<Int64>?
    @State private var map: ArrivalMapSnapshot?
    @State private var mapResolved = false
    @State private var mapDrawn = false
    @State private var introText = false
    @State private var closing = false

    private static let background = Color(argb: 0xFF07080A)
    private static let glass = Color(argb: 0xFF14161A).opacity(0.7)
    private static let threshold: CGFloat = 90
    private static let exitX: CGFloat = 640
    private static let stripH: CGFloat = 230
    private static let sampleItems = [ChecklistItem(id: 1, text: "Süt, yarım yağlı"), ChecklistItem(id: 2, text: "Tam buğday ekmek"),
                                      ChecklistItem(id: 3, text: "Kahve filtresi"), ChecklistItem(id: 4, text: "AA pil, 4’lü")]

    private struct Item: Identifiable { let id: Int64; let text: String }

    private var accent: Color { context.onExit ? Color(argb: 0xFFFFB86F) : Color(argb: 0xFF5DCAA5) }
    private var reminder: ReminderItem? { context.sample ? nil : store.reminder(context.id) }
    private var checklist: [ChecklistItem] {
        if context.sample { return Self.sampleItems }
        guard let r = reminder, r.isChecklist else { return [] }
        return r.checklistItems
    }
    private var noteMode: Bool { checklist.isEmpty }
    private var noteText: String? {
        reminder?.cardStack?.cards.lazy.map { $0.body.trimmingCharacters(in: .whitespacesAndNewlines) }.first { !$0.isEmpty }
    }
    private var placeLabel: String { context.placeName.isEmpty ? t("Seçilen Konum") : context.placeName }
    private var title: String {
        if context.sample { return "Market alışverişi" }
        if noteMode && noteText == nil { return placeLabel }
        return reminder?.text ?? placeLabel
    }
    private var doneAtOpen: Set<Int64> { initiallyDone ?? Set(checklist.filter(\.isCompleted).map(\.id)) }
    private var deck: [Item] {
        if noteMode { return [Item(id: -1, text: noteText ?? reminder?.text ?? placeLabel)] }
        return checklist.filter { !doneAtOpen.contains($0.id) }.map { Item(id: $0.id, text: $0.text) }
    }
    private var queue: [Item] { deck.filter { status[$0.id] == nil } }
    private var eyebrow: String { context.onExit ? t("AYRILDIN") : t("VARDIN") }
    private var categoryColor: Color? {
        store.category(reminder?.customCategoryId).map { Color(argbInt: $0.customSolidColorArgb ?? $0.accentStart) }
    }
    private var extraSummary: String? {
        guard let r = reminder else { return context.sample ? t("Kartta {0} var", t("{0} fotoğraf", 1)) : nil }
        var parts: [String] = []
        if !r.attachments.isEmpty { parts.append(t("{0} fotoğraf", r.attachments.count)) }
        if !r.voiceRecordings.isEmpty { parts.append(t("{0} ses kaydı", r.voiceRecordings.count)) }
        if !noteMode, let stack = r.cardStack, !stack.cards.isEmpty { parts.append(t("{0} alt kart", stack.cards.count)) }
        if !(r.referenceUrl ?? "").isEmpty { parts.append(t("bağlantı")) }
        return parts.isEmpty ? nil : t("Kartta {0} var", parts.joined(separator: " · "))
    }

    var body: some View {
        GeometryReader { geo in
            content(size: geo.size, insets: geo.safeAreaInsets)
                .task(id: geo.size) { await loadMap(size: geo.size) }
        }
        .ignoresSafeArea()
        .scaleEffect(closing ? 0.02 : 1, anchor: UnitPoint(x: 0.5, y: 0.62))
        .opacity(closing ? 0 : 1)
        .clipShape(RoundedRectangle(cornerRadius: closing ? 400 : 0, style: .continuous))
        .animation(.timingCurve(0.6, 0, 0.4, 1, duration: 0.5), value: closing)
        .onAppear {
            if initiallyDone == nil { initiallyDone = doneAtOpen }
            withAnimation(.timingCurve(0.2, 0.9, 0.25, 1, duration: 0.52).delay(0.75)) { introText = true }
        }
        .task { await runIntro() }
    }

    @ViewBuilder
    private func content(size: CGSize, insets: EdgeInsets) -> some View {
        let intro = phase == .intro
        let destY = map?.destination.y ?? size.height / 2
        let shift = intro ? 0 : min(0, max(Self.stripH - size.height, Self.stripH / 2 - destY))
        ZStack(alignment: .top) {
            Self.background
            if let categoryColor {
                RadialGradient(colors: [categoryColor.opacity(0.22), .clear], center: UnitPoint(x: 0.5, y: 1.08),
                               startRadius: 0, endRadius: size.width * 1.05)
            }

            // Map: full screen in the intro, then a strip at the top.
            ZStack(alignment: .top) {
                ArrivalMapLayer(map: map, accent: accent, drawn: mapDrawn, size: size)
                    .frame(width: size.width, height: size.height)
                    .offset(y: shift)
                LinearGradient(stops: [.init(color: Self.background.opacity(0.55), location: 0), .init(color: .clear, location: 0.26),
                                       .init(color: .clear, location: 0.55), .init(color: Self.background, location: 1)],
                               startPoint: .top, endPoint: .bottom)
            }
            .frame(width: size.width, height: intro ? size.height : Self.stripH, alignment: .top)
            .clipped()
            .animation(.timingCurve(0.65, 0, 0.25, 1, duration: 0.75), value: intro)

            // Intro words over the full map.
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Circle().fill(accent).frame(width: 8, height: 8)
                    Text(eyebrow).font(RealFont.inter(13, .bold)).tracking(1.6).foregroundStyle(accent)
                }
                Text(placeLabel).font(RealFont.inter(40, .bold)).foregroundStyle(.white).lineLimit(2)
                Text(noteMode ? title : t("{0} · {1} madde seni bekliyor", title, deck.count))
                    .font(RealFont.inter(16)).foregroundStyle(.white.opacity(0.7)).lineLimit(2)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
            .padding(.horizontal, 28)
            .padding(.bottom, 110 + insets.bottom)
            .opacity(intro && introText ? 1 : 0)
            .offset(y: intro ? (introText ? 0 : 18) : -30)
            .animation(.easeOut(duration: 0.25), value: intro)
            .allowsHitTesting(false)

            VStack(spacing: 0) {
                titleBlock.riseIn(!intro, 1)
                deckArea.frame(maxHeight: .infinity).padding(.horizontal, 20).riseIn(!intro, 2)
                if phase != .finale { actions.padding(.horizontal, 20).riseIn(!intro, 3) }
            }
            .padding(.top, 196)
            .padding(.bottom, 28 + insets.bottom)

            header.padding(.horizontal, 16).padding(.top, insets.top + 18).riseIn(!intro, 0)

            // During the arrival moment a tap anywhere goes straight to the list.
            if intro {
                Color.clear.contentShape(Rectangle())
                    .onTapGesture { collapse() }
                    .accessibilityLabel(t("Listeye geç"))
                    .accessibilityAddTraits(.isButton)
            }
        }
        .frame(width: size.width, height: size.height)
    }

    private var header: some View {
        HStack(spacing: 12) {
            HStack(spacing: 8) {
                ActionIcon(.pin, color: accent, size: 16)
                Text(context.onExit ? t("Ayrıldın · {0}", placeLabel) : t("Vardın · {0}", placeLabel))
                    .font(RealFont.inter(13, .semibold)).foregroundStyle(.white).lineLimit(1)
            }
            .padding(.leading, 10).padding(.trailing, 14).frame(height: 36)
            .background(Capsule().fill(Self.glass))
            .overlay(Capsule().strokeBorder(.white.opacity(0.14), lineWidth: 1))
            Spacer(minLength: 0)
            Button { close(then: onClose) } label: {
                ActionIcon(.close, color: .white, size: 16)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(Self.glass))
                    .overlay(Circle().strokeBorder(.white.opacity(0.14), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(t("Kapat"))
        }
    }

    private var titleBlock: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(eyebrow).font(RealFont.inter(12, .bold)).tracking(1.4).foregroundStyle(accent)
            Text(title).font(RealFont.inter(28, .bold)).foregroundStyle(.white).lineLimit(2).padding(.top, 6)
            if !noteMode {
                let frontId = queue.first?.id
                let done = checklist.filter { $0.isCompleted || status[$0.id] == true }.count
                let skipped = checklist.filter { status[$0.id] == false }.count
                HStack(spacing: 12) {
                    HStack(spacing: 6) {
                        ForEach(checklist) { item in
                            Capsule()
                                .fill(item.isCompleted || status[item.id] == true ? Color.white
                                      : status[item.id] == false ? Color.white.opacity(0.3)
                                      : item.id == frontId ? accent : Color.white.opacity(0.14))
                                .frame(height: 4)
                                .animation(.easeOut(duration: 0.3), value: status)
                        }
                    }
                    Text("\(queue.isEmpty ? checklist.count : min(checklist.count, done + skipped + 1))/\(checklist.count)")
                        .font(RealFont.inter(13, .bold)).foregroundStyle(.white.opacity(0.62)).monospacedDigit()
                }
                .padding(.top, 14)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 24)
    }

    @ViewBuilder
    private var deckArea: some View {
        if phase == .finale {
            let skipped = status.values.filter { !$0 }.count
            ArrivalFinale(
                accent: accent,
                title: skipped > 0 ? t("Liste bitti") : t("Hepsi tamam"),
                text: skipped > 0 ? t("{0} madde işaretlenmeden kartta bekliyor.", skipped)
                    : noteMode ? t("Hatırlatma kapandı.") : t("{0} listesi bitti.", placeLabel)
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ZStack(alignment: .top) {
                ForEach(Array(deck.enumerated()), id: \.element.id) { index, item in
                    deckCard(item, index: index)
                }
            }
            .padding(.top, 18)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }

    private func deckCard(_ item: Item, index: Int) -> some View {
        let st = status[item.id]
        let pos = queue.firstIndex { $0.id == item.id } ?? -1
        let front = pos == 0 && phase == .list
        let slot = CGFloat(max(pos, 0))
        let a = min(max(slot, 0), 1), b = min(max(slot - 1, 0), 1), c = min(max(slot - 2, 0), 1)
        let x: CGFloat = st.map { $0 ? Self.exitX : -Self.exitX } ?? (pos == 0 ? drag : 0)
        let k = front ? min(max(drag / Self.threshold, -1), 1) : 0
        return ArrivalDeckCard(
            text: item.text,
            foot: noteMode ? (context.onExit ? t("Ayrılınca hatırlat") : t("Varınca hatırlat")) : t("{0}. madde", index + 1),
            k: k, front: front
        )
        .frame(height: 220)
        .scaleEffect(1 - 0.08 * a - 0.08 * b - 0.06 * c, anchor: .top)
        .blur(radius: 2.5 * a + 2.5 * b)
        .opacity(st != nil ? 0 : max(0, 1 - 0.4 * a - 0.3 * b - 0.3 * c))
        .rotationEffect(.degrees(Double(x / 22)))
        .offset(x: x, y: 56 - (26 * a + 22 * b + 12 * c))
        .zIndex(pos < 0 ? 0 : Double(30 - pos))
        .allowsHitTesting(front)
        .gesture(dragGesture)
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                guard phase == .list, !busy else { return }
                let w = value.translation.width
                let next = noteMode && w < 0 ? w * 0.25 : w
                let nowArmed = abs(next) >= Self.threshold && (next > 0 || !noteMode)
                if nowArmed != armed { armed = nowArmed; UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                drag = next
            }
            .onEnded { _ in
                guard phase == .list, !busy else { return }
                if drag > Self.threshold { commit(true) }
                else if drag < -Self.threshold && !noteMode { commit(false) }
                else {
                    armed = false
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.6)) { drag = 0 }
                }
            }
    }

    private var actions: some View {
        VStack(spacing: 12) {
            if let extraSummary {
                Button { close(then: onOpenCard) } label: {
                    HStack(spacing: 8) {
                        ActionIcon(.photo, color: .white.opacity(0.8), size: 16)
                        Text(extraSummary).font(RealFont.inter(13, .semibold)).foregroundStyle(.white.opacity(0.8)).lineLimit(1)
                        Spacer(minLength: 8)
                        Text(t("Kartı gör")).font(RealFont.inter(13, .semibold)).foregroundStyle(.white)
                    }
                    .padding(.horizontal, 16).frame(minHeight: 48)
                    .background(Capsule().fill(.white.opacity(0.06)))
                    .overlay(Capsule().strokeBorder(.white.opacity(0.12), lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
            HStack(spacing: 10) {
                Button { if noteMode { close(then: onOpenCard) } else { commit(false) } } label: {
                    HStack(spacing: 8) {
                        if !noteMode { ActionIcon(.chevronRight, color: .white, size: 18) }
                        Text(noteMode ? t("Kartı aç") : t("Geç")).font(RealFont.inter(16, .semibold)).foregroundStyle(.white)
                    }
                    .padding(.horizontal, 22).frame(height: 60)
                    .background(Capsule().fill(.white.opacity(0.08)))
                    .overlay(Capsule().strokeBorder(.white.opacity(0.16), lineWidth: 1))
                }
                .buttonStyle(.plain)
                Button { commit(true) } label: {
                    HStack(spacing: 8) {
                        ActionIcon(.check, color: Self.background, size: 20)
                        Text(noteMode ? t("Tamam") : t("Tamamla")).font(RealFont.inter(17, .bold)).foregroundStyle(Self.background)
                    }
                    .frame(maxWidth: .infinity).frame(height: 60)
                    .background(Capsule().fill(.white))
                }
                .buttonStyle(.plain)
            }
            HStack {
                if !history.isEmpty {
                    Button(action: undo) {
                        HStack(spacing: 6) {
                            ActionIcon(.undo, color: .white.opacity(0.72), size: 16)
                            Text(t("Geri al")).font(RealFont.inter(14, .semibold)).foregroundStyle(.white.opacity(0.72))
                        }
                        .padding(.horizontal, 6).frame(height: 44)
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
                if !noteMode {
                    Button { close(then: onOpenCard) } label: {
                        Text(t("Kartı aç")).font(RealFont.inter(14, .semibold)).foregroundStyle(.white.opacity(0.72))
                            .padding(.horizontal, 6).frame(height: 44)
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(height: 44)
        }
    }

    // MARK: - Actions

    private func collapse() {
        guard phase == .intro else { return }
        phase = .list
    }

    private func commit(_ done: Bool) {
        guard !busy, phase == .list, let front = queue.first else { return }
        busy = true
        armed = false
        if done { UINotificationFeedbackGenerator().notificationOccurred(.success) }
        else { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        withAnimation(.timingCurve(0.5, 0, 0.75, 0.6, duration: 0.32)) {
            drag = done ? Self.exitX : -Self.exitX
        } completion: {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.62)) {
                status[front.id] = done
                history.append(front.id)
                drag = 0
            }
            if done && !noteMode && !context.sample { store.toggleChecklistItem(context.id, itemId: front.id) }
            busy = false
            if deck.allSatisfy({ status[$0.id] != nil }) {
                Task {
                    try? await Task.sleep(for: .milliseconds(180))
                    withAnimation(.easeOut(duration: 0.3)) { phase = .finale }
                    try? await Task.sleep(for: .milliseconds(2_300))
                    close(then: onClose)
                }
            }
        }
    }

    private func undo() {
        guard !busy, phase == .list, let last = history.popLast() else { return }
        var wasDone = false
        withAnimation(.spring(response: 0.45, dampingFraction: 0.72)) { wasDone = status.removeValue(forKey: last) == true }
        if wasDone && !noteMode && !context.sample { store.toggleChecklistItem(context.id, itemId: last) }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private func close(then action: @escaping () -> Void) {
        guard !closing else { return }
        closing = true
        Task {
            try? await Task.sleep(for: .milliseconds(520))
            action()
        }
    }

    // MARK: - Map

    private func loadMap(size: CGSize) async {
        guard size.width > 0, size.height > 0, map == nil else { return }
        let loaded = await ArrivalMapLoader.load(context, size: size)
        map = loaded
        mapResolved = true
        guard loaded != nil else { return }
        try? await Task.sleep(for: .milliseconds(60))
        mapDrawn = true
    }

    /// Waits up to 1.2 s for the map, then gives it time to draw; without one the moment is short.
    private func runIntro() async {
        let start = Date()
        while map == nil && !mapResolved && Date().timeIntervalSince(start) < 1.2 {
            try? await Task.sleep(for: .milliseconds(50))
        }
        try? await Task.sleep(for: .milliseconds(map != nil ? 2_150 : 1_400))
        collapse()
    }
}

private extension View {
    /// Fades and lifts a block in after the arrival moment, one after another by [index].
    func riseIn(_ visible: Bool, _ index: Int) -> some View {
        opacity(visible ? 1 : 0)
            .offset(y: visible ? 0 : 28)
            .animation(visible ? .timingCurve(0.2, 0.9, 0.25, 1, duration: 0.52).delay(0.35 + Double(index) * 0.07)
                               : .easeOut(duration: 0.15), value: visible)
            .allowsHitTesting(visible)
    }
}

/// One item of the deck: [k] is the swipe toward Tamamla (+1) or Geç (-1); past the line a white tick springs in.
private struct ArrivalDeckCard: View {
    let text: String
    let foot: String
    let k: CGFloat
    let front: Bool

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 30, style: .continuous)
        let border: Color = front && k > 0.2 ? .white : front && k < -0.2 ? .white.opacity(0.4) : .white.opacity(0.14)
        let stamp = front && k > 0.85
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(t("GEÇ")).font(RealFont.inter(11, .bold)).tracking(1.4).foregroundStyle(.white.opacity(0.7))
                    .opacity(front ? Double(max(0, -k)) : 0)
                Spacer()
                Text(t("TAMAMLA")).font(RealFont.inter(11, .bold)).tracking(1.4).foregroundStyle(.white)
                    .opacity(front ? Double(max(0, k)) : 0)
            }
            .frame(height: 22)
            Text(text).font(RealFont.inter(27, .bold)).foregroundStyle(.white).lineLimit(3).padding(.top, 10)
            Spacer(minLength: 0)
            Text(foot).font(RealFont.inter(12, .medium)).foregroundStyle(.white.opacity(0.55))
        }
        .padding(EdgeInsets(top: 20, leading: 24, bottom: 22, trailing: 24))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(shape.fill(front && k > 0.2 ? Color(argb: 0xFF22242A) : Color(argb: 0xFF181A1F)))
        .overlay(shape.strokeBorder(border, lineWidth: 1.5))
        .overlay(alignment: .bottomTrailing) {
            Circle().fill(.white).frame(width: 52, height: 52)
                .overlay(ActionIcon(.check, color: Color(argb: 0xFF07080A), size: 28))
                .scaleEffect(stamp ? 1 : 0.001)
                .animation(.spring(response: 0.3, dampingFraction: 0.5), value: stamp)
                .padding(18)
        }
        .shadow(color: .black.opacity(0.45), radius: 25, y: 18)
    }
}

/// All handled: a white ring draws, the tick follows, dots burst out, then the words rise.
private struct ArrivalFinale: View {
    let accent: Color
    let title: String
    let text: String
    @State private var ring: CGFloat = 0
    @State private var tick: CGFloat = 0
    @State private var burstOut = false
    @State private var burstShown = false
    @State private var words = false

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Circle().stroke(.white.opacity(0.1), lineWidth: 5).frame(width: 96, height: 96)
                Circle().trim(from: 0, to: ring)
                    .stroke(.white, style: StrokeStyle(lineWidth: 5, lineCap: .round))
                    .rotationEffect(.degrees(-90)).frame(width: 96, height: 96)
                FinaleTick().trim(from: 0, to: tick)
                    .stroke(.white, style: StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round))
                    .frame(width: 96, height: 96)
                ForEach(0..<8, id: \.self) { n in
                    let angle = Double(n) * .pi / 4
                    let r: CGFloat = n % 2 == 0 ? 94 : 74
                    Circle().fill(n % 2 == 0 ? accent : .white).frame(width: 8, height: 8)
                        .scaleEffect(burstOut ? 0.4 : 1)
                        .offset(x: burstOut ? CGFloat(cos(angle)) * r : 0, y: burstOut ? CGFloat(sin(angle)) * r : 0)
                        .opacity(burstShown ? 1 : 0)
                }
            }
            .frame(width: 200, height: 200)
            VStack(spacing: 8) {
                Text(title).font(RealFont.inter(30, .bold)).foregroundStyle(.white)
                Text(text).font(RealFont.inter(15)).foregroundStyle(.white.opacity(0.66))
                    .multilineTextAlignment(.center).frame(maxWidth: 280)
            }
            .opacity(words ? 1 : 0)
            .offset(y: words ? 0 : 14)
        }
        .task {
            withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.7)) { ring = 1 }
            try? await Task.sleep(for: .milliseconds(500))
            withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.35)) { tick = 1 }
            try? await Task.sleep(for: .milliseconds(50))
            burstShown = true
            withAnimation(.timingCurve(0.2, 0.8, 0.3, 1, duration: 0.9)) { burstOut = true }
            withAnimation(.timingCurve(0.2, 0.9, 0.25, 1, duration: 0.5)) { words = true }
            try? await Task.sleep(for: .milliseconds(250))
            withAnimation(.easeIn(duration: 0.65)) { burstShown = false }
        }
    }
}

private struct FinaleTick: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 96
        var p = Path()
        p.move(to: CGPoint(x: rect.midX - 20 * s, y: rect.midY + 1 * s))
        p.addLine(to: CGPoint(x: rect.midX - 6 * s, y: rect.midY + 15 * s))
        p.addLine(to: CGPoint(x: rect.midX + 21 * s, y: rect.midY - 14 * s))
        return p
    }
}

// MARK: - Map snapshot

struct ArrivalMapSnapshot {
    let image: UIImage
    /// Route in snapshot points (empty without one).
    let route: [CGPoint]
    let destination: CGPoint
}

enum ArrivalMapLoader {
    /// Apple Maps snapshot at the screen size. Varınca with the phone still far (the developer test): the driving
    /// route from there; otherwise just the place.
    @MainActor
    static func load(_ context: ArrivalFocusContext, size: CGSize) async -> ArrivalMapSnapshot? {
        let place = CLLocationCoordinate2D(latitude: context.latitude, longitude: context.longitude)
        var polyline: MKPolyline?
        if !context.onExit, let here = await CurrentLocation.fix(),
           here.distance(from: CLLocation(latitude: place.latitude, longitude: place.longitude)) > 1500 {
            let request = MKDirections.Request()
            request.source = MKMapItem(placemark: MKPlacemark(coordinate: here.coordinate))
            request.destination = MKMapItem(placemark: MKPlacemark(coordinate: place))
            request.transportType = .automobile
            polyline = try? await MKDirections(request: request).calculate().routes.first?.polyline
        }
        let options = MKMapSnapshotter.Options()
        options.size = size
        options.traitCollection = UITraitCollection(userInterfaceStyle: .dark)
        options.pointOfInterestFilter = .excludingAll
        if let polyline {
            var rect = polyline.boundingMapRect.union(MKMapRect(origin: MKMapPoint(place), size: MKMapSize(width: 1, height: 1)))
            let pad = max(rect.width, rect.height) * 0.3
            rect = rect.insetBy(dx: -pad, dy: -pad)
            options.mapRect = rect
        } else {
            options.region = MKCoordinateRegion(center: place, latitudinalMeters: 1800, longitudinalMeters: 1800)
        }
        guard let snap = try? await MKMapSnapshotter(options: options).start() else { return nil }
        var route: [CGPoint] = []
        if let polyline, polyline.pointCount > 1 {
            var coords = [CLLocationCoordinate2D](repeating: kCLLocationCoordinate2DInvalid, count: polyline.pointCount)
            polyline.getCoordinates(&coords, range: NSRange(location: 0, length: polyline.pointCount))
            route = coords.map { snap.point(for: $0) }
        }
        return ArrivalMapSnapshot(image: snap.image, route: route, destination: snap.point(for: place))
    }
}

/// The snapshot in REAL's monochrome, the route drawing in (accent glow) and the place pin dropping with pulses.
private struct ArrivalMapLayer: View {
    let map: ArrivalMapSnapshot?
    let accent: Color
    let drawn: Bool
    let size: CGSize
    @State private var pulse = false
    @State private var pinPulse = false

    var body: some View {
        ZStack(alignment: .topLeading) {
            if let map {
                Image(uiImage: map.image).resizable().frame(width: size.width, height: size.height)
                    .saturation(0).brightness(0.03)
                    .opacity(drawn ? 1 : 0)
                    .scaleEffect(drawn ? 1 : 1.12, anchor: UnitPoint(x: map.destination.x / max(size.width, 1), y: map.destination.y / max(size.height, 1)))
                    .animation(.timingCurve(0.2, 0.7, 0.2, 1, duration: 2.0), value: drawn)
                let hasRoute = map.route.count > 1
                if hasRoute {
                    ArrivalRoute(points: map.route).trim(from: 0, to: drawn ? 1 : 0)
                        .stroke(accent.opacity(0.4), style: StrokeStyle(lineWidth: 12, lineCap: .round, lineJoin: .round))
                        .blur(radius: 5)
                        .animation(.timingCurve(0.45, 0, 0.2, 1, duration: 1.25).delay(0.25), value: drawn)
                    ArrivalRoute(points: map.route).trim(from: 0, to: drawn ? 1 : 0)
                        .stroke(accent, style: StrokeStyle(lineWidth: 4.5, lineCap: .round, lineJoin: .round))
                        .animation(.timingCurve(0.45, 0, 0.2, 1, duration: 1.25).delay(0.25), value: drawn)
                }
                ZStack {
                    ForEach(0..<2, id: \.self) { i in
                        Circle().fill(accent.opacity(0.4)).frame(width: 24, height: 24)
                            .scaleEffect(pinPulse ? 3 : 0.4).opacity(pinPulse ? 0 : 0.9)
                            .animation(.easeOut(duration: 1.9).repeatForever(autoreverses: false).delay(Double(i) * 0.95), value: pinPulse)
                    }
                    Circle().fill(.white).frame(width: 22, height: 22)
                        .overlay(Circle().fill(accent).frame(width: 12, height: 12))
                        .shadow(color: .black.opacity(0.5), radius: 6)
                        .scaleEffect(drawn ? 1 : 0.001)
                        .animation(.spring(response: 0.4, dampingFraction: 0.5).delay(hasRoute ? 1.35 : 0.3), value: drawn)
                }
                .position(map.destination)
                .task(id: drawn) {
                    guard drawn else { return }
                    try? await Task.sleep(for: .milliseconds(hasRoute ? 1_450 : 400))
                    pinPulse = true
                }
            } else {
                ZStack {
                    ForEach(0..<2, id: \.self) { i in
                        Circle().strokeBorder(.white.opacity(0.55), lineWidth: 1.4).frame(width: 16, height: 16)
                            .scaleEffect(pulse ? 5 : 1).opacity(pulse ? 0 : 1)
                            .animation(.easeOut(duration: 1.4).repeatForever(autoreverses: false).delay(Double(i) * 0.7), value: pulse)
                    }
                    Circle().fill(.white).frame(width: 10, height: 10)
                }
                .position(x: size.width / 2, y: size.height / 2)
                .onAppear { pulse = true }
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

private struct ArrivalRoute: Shape {
    let points: [CGPoint]
    func path(in rect: CGRect) -> Path {
        var p = Path()
        guard let first = points.first else { return p }
        p.move(to: first)
        points.dropFirst().forEach { p.addLine(to: $0) }
        return p
    }
}
