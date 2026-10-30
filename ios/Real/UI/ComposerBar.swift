import SwiftUI
import Combine
import UIKit
import PhotosUI
import UniformTypeIdentifiers

/// Draft schedule chosen in the composer or the detail screen (ReminderDateSheet).
struct ScheduleDraft: Equatable {
    var date: Date? = nil
    var hasTime = true
    var repeatRule: RecurrenceRule = .none
    var weekdaysMask = 0
    var until: Date? = nil
    var durationMinutes: Int? = nil
    /// "Erken uyarı" (minutes before the alert) and "Bitiş" (any day and time after the start).
    var earlyAlertMinutes: Int? = nil
    var endAt: Date? = nil

    var isEmpty: Bool { date == nil }

    var alertAt: EpochMillis? { hasTime ? date?.epochMillis : nil }
    var plannedDay: EpochDay? { hasTime ? nil : date.map { Calendar.real.epochDay(of: $0) } }
    var untilEpochDay: EpochDay? { until.map { Calendar.real.epochDay(of: $0) } }

    var summary: String {
        guard let date else { return t("Tarih") }
        var s = hasTime ? RealDates.reminder(date.epochMillis) : RealDates.dayLabel(date)
        if hasTime && repeatRule != .none { s += " • " + repeatRule.displayName }
        return s
    }

    init() {}

    init(from item: ReminderItem) {
        if let alert = item.alertAt {
            date = alert.date
            hasTime = true
        } else if let day = item.plannedDateEpochDay {
            date = Calendar.real.startOfDay(epochDay: day)
            hasTime = false
        }
        repeatRule = item.repeatRule
        weekdaysMask = item.repeatWeekdaysMask
        until = item.repeatUntilEpochDay.map { Calendar.real.startOfDay(epochDay: $0) }
        durationMinutes = item.durationMinutes
        earlyAlertMinutes = item.earlyAlertMinutes
        endAt = item.endAt?.date
    }

    func apply(to item: inout ReminderItem) {
        let oldAlert = item.alertAt
        item.alertAt = alertAt
        item.plannedDateEpochDay = plannedDay
        item.repeatRule = alertAt == nil ? .none : repeatRule
        item.repeatWeekdaysMask = item.repeatRule == .weekly ? weekdaysMask : 0
        item.repeatUntilEpochDay = item.repeatRule == .none ? nil : untilEpochDay
        item.durationMinutes = alertAt == nil ? nil : durationMinutes
        if alertAt != oldAlert { item.anchorAlertAt = alertAt; item.repeatSkippedEpochDays = [] }
        if alertAt != nil || plannedDay != nil { item.quick24HourUntilAt = nil }
        item.earlyAlertMinutes = alertAt == nil ? nil : earlyAlertMinutes
        let start = alertAt ?? plannedDay.map { Calendar.real.startOfDayMillis(epochDay: $0) }
        item.endAt = endAt.map(\.epochMillis).flatMap { end in start.flatMap { end > $0 ? end : nil } }
    }
}

/// "bugün / yarın" in the draft offers a one-tap date chip (ComposerDateSuggestions.kt).
enum DateSuggestion {
    case today, tomorrow
    var label: String { self == .today ? t("Bugün'e ekle") : t("Yarına ekle") }

    static func detect(_ text: String) -> DateSuggestion? {
        let t = " " + text.lowercased(with: Locale(identifier: "tr_TR")) + " "
        func has(_ words: [String]) -> Bool {
            words.contains { w in
                guard let r = t.range(of: w) else { return false }
                let before = t[t.index(before: r.lowerBound)], after = t[r.upperBound]
                return !before.isLetter && !before.isNumber && !after.isLetter && !after.isNumber
            }
        }
        if has(["yarın", "yarin"]) { return .tomorrow }
        if has(["bugün", "bugun", "bu akşam", "bu aksam", "bu gece"]) { return .today }
        return nil
    }
}

/// HomeLiquidDock + GlassComposer. Collapsed: calendar circle · "Add to REAL" capsule · mic circle.
/// Pull up: Yaşam / Pano / Takvim bar. Tap: full-width glass panel with the tool row.
struct ComposerBar: View {
    @Environment(ReminderStore.self) private var store
    var defaultCategoryId: Int64?
    var defaultImportant = false
    var defaultPlannedToday = false
    /// Home shows the dock chrome (calendar + mic circles, navigation); other screens keep the mic inside the capsule.
    var isHome = false
    var onOpenPlanner: (() -> Void)?
    var onOpenYasam: (() -> Void)?
    var onOpenPano: (() -> Void)?

    @State private var text = ""
    @State private var schedule = ScheduleDraft()
    @State private var voiceConfirm: VoiceAI.Pending?
    @State private var categoryId: Int64?
    @State private var important = false
    @State private var sendImmediate = false
    @State private var alarm = false
    @State private var checklistMode = false
    @State private var checklistItems: [String] = [""]
    @State private var attachments: [ReminderAttachment] = []
    @State private var photoItems: [PhotosPickerItem] = []
    @State private var showDateSheet = false
    @State private var showCategoryPicker = false
    @State private var showPhotoPicker = false
    @State private var showLocationEditor = false
    @State private var location: ReminderLocation?
    /// Pin → "Konum ekle" or "Konuma varınca hatırlat"; the trigger rides on the draft like the place.
    @State private var pickForTrigger = false
    @State private var placeTrigger: LocationTrigger?
    @State private var placePermission = PlaceReminderPermission()
    @State private var navExpanded = false
    @State private var hintLift: CGFloat = 0
    /// Full screen (pushed up on the open panel): the detail rows, a note under the title, the tray of everything added.
    @State var fullScreen = false
    @State var fsPage: FullScreenPage?
    @State var fsOpenRow: String?
    @State var noteText = ""
    @State var voiceNotes: [ReminderVoiceRecording] = []
    @State var musicPicks: [MusicBlock] = []
    @State var mediaPicks: [MediaBlock] = []
    @State var gamePicks: [CardGameBlock] = []
    @State var fsRecorder = VoiceRecorderEngine()
    @State var showCamera = false
    @State var showFileImporter = false
    @State var showNewCategory = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var voice = VoiceInput()
    @Namespace private var dockNS
    @FocusState var focus: Field?

    enum Field: Hashable { case title, item(Int), fsTitle, fsNote, fsItem(Int) }

    @AppStorage("reduce_animations") private var reduceAnimations = false
    /// The panel as shown. Opened by focus alone, the bar and the panel do not morph into each other riding the
    /// keyboard (ReminderScreen.kt composerUp): the bar fades out, then the panel fades and slides in on its own
    /// slower curve; closing mirrors it. The layout jumps while both are hidden.
    @State private var expanded = false
    @State private var swapOut: Double = 0
    @State private var swapIn: Double = 1
    @State private var swapTask: Task<Void, Never>?

    private var otherExpanded: Bool { !text.isEmpty || checklistMode || voice.phase != .idle }
    private var wantsExpanded: Bool { focus != nil || otherExpanded }
    private var showChrome: Bool { isHome && !expanded }

    var body: some View {
        withComposerSheets(dockBody, active: !fullScreen)
            .onChange(of: photoItems) { _, items in importPhotos(items) }
            .fullScreenCover(isPresented: $fullScreen) { fullScreenPage }
    }

    private var dockBody: some View {
        VStack(spacing: 10) {
            ZStack {
                Group {
                    // VoiceListeningWindow replaces the composer on Home while dictating (Android).
                    if isHome && voice.phase != .idle { VoiceListeningWindow(voice: voice, onMic: startVoice) }
                    else { composerPanel }
                }
                .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.24), value: voice.phase)
                    .padding(.horizontal, showChrome && !navExpanded ? 56 : 0)
                if showChrome && !navExpanded {
                    HStack {
                        Button { onOpenPlanner?() } label: {
                            RealSymbol("calendar", size: 21.8).foregroundStyle(.white)
                                .frame(width: 44, height: 44)
                                .background { Color.clear.liquidGlass(Capsule()).matchedGeometryEffect(id: "dockNav", in: dockNS) }
                                .frame(width: 48, height: 48)
                                .contentShape(Circle())
                        }
                        .accessibilityLabel(t("Takvim"))
                        Spacer()
                        micCircle
                    }
                    .transition(.opacity)
                }
            }
            if showChrome && navExpanded {
                HStack(spacing: 12) {
                    navigationBar
                    micCircle
                }
                .transition(.opacity)
            }
        }
        .padding(.horizontal, 16)
        .opacity((1 - swapOut) * swapIn)
        .scaleEffect((1 - 0.06 * swapOut) * (0.95 + 0.05 * swapIn), anchor: .bottom)
        .offset(y: (1 - swapIn) * (expanded ? 26 : 10) + swapOut * (expanded ? 18 : 0))
        // Dock bottom edge on the safe area (~34pt above the screen edge), like Android's nav-bar inset + 12dp: a swipe
        // starting on the dock stays clear of the home-indicator gesture, so it no longer sends people to the launcher.
        .padding(.bottom, expanded ? 8 : 0)
        .animation(.spring(response: 0.34, dampingFraction: 0.85), value: navExpanded)
        .offset(y: hintLift)
        // Pull-up hint (Android, every time home shows): after a quiet moment the dock lifts a little, twice; motion only.
        .task(id: showChrome && !navExpanded) {
            hintLift = 0
            guard showChrome, !navExpanded, !reduceMotion else { return }
            try? await Task.sleep(for: .milliseconds(2500))
            for _ in 0..<2 {
                guard !Task.isCancelled else { break }
                withAnimation(.timingCurve(0.2, 0, 0, 1, duration: 0.28)) { hintLift = -14 }
                try? await Task.sleep(for: .milliseconds(280))
                withAnimation(.spring(response: 0.45, dampingFraction: 0.42)) { hintLift = 0 }
                try? await Task.sleep(for: .milliseconds(900))
            }
            hintLift = 0
        }
        .highPriorityGesture(
            DragGesture(minimumDistance: 20).onEnded { value in
                guard showChrome else { return }
                if value.translation.height < -28 { navExpanded = true } else if value.translation.height > 28 { navExpanded = false }
            }
        )
        .onChange(of: wantsExpanded) { _, want in
            swapTask?.cancel()
            swapTask = Task { @MainActor in await swapPanel(to: want) }
        }
        .onChange(of: expanded) { _, e in if e { navExpanded = false } }
        .onAppear(perform: reset)
        .onReceive(NotificationCenter.default.publisher(for: .realStartVoice)) { _ in if isHome { startVoiceIfIdle() } }
        // "Doğru mu anladım?": nothing was saved yet.
        .sheet(item: $voiceConfirm) { pending in
            VoiceConfirmSheet(pending: pending, onConfirm: { place in
                Task { @MainActor in handleVoice(await VoiceAI.commit(pending, place: place, store: store)) }
            }, onCorrect: {
                text = pending.transcript
                focus = .title
            })
        }
    }

    @MainActor
    private func swapPanel(to want: Bool) async {
        // Voice, a checklist or typed text open it with the old morph; reduced motion jumps.
        if reduceAnimations || otherExpanded {
            withAnimation(reduceAnimations ? nil : .spring(response: 0.32, dampingFraction: 0.85)) {
                expanded = want; swapOut = 0; swapIn = 1
            }
            return
        }
        if expanded == want {
            // Turned back mid-way: bring whatever is showing back to rest.
            withAnimation(.easeOut(duration: 0.15)) { swapOut = 0; swapIn = 1 }
            return
        }
        let outTime = want ? 0.12 : 0.17
        withAnimation(.easeOut(duration: outTime)) { swapOut = 1 }
        try? await Task.sleep(for: .seconds(outTime))
        guard !Task.isCancelled else { return }
        var jump = Transaction()
        jump.disablesAnimations = true
        withTransaction(jump) { expanded = want; swapOut = 0; swapIn = 0 }
        withAnimation(.timingCurve(0.2, 0, 0, 1, duration: want ? 0.34 : 0.26)) { swapIn = 1 }
    }

    // MARK: Dock chrome

    private func dockCircle(symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            RealSymbol(symbol, size: 21.8).foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .liquidGlass(Circle())
                .frame(width: 48, height: 48)
                .contentShape(Circle())
        }
        .accessibilityLabel(label)
    }

    private var micCircle: some View {
        dockCircle(symbol: "mic", label: t("Sesle REAL oluştur")) { startVoice() }
    }

    /// HomeDockExpandedNavigation: Yaşam · Pano · Takvim, Takvim selected.
    private var navigationBar: some View {
        HStack(spacing: 0) {
            navItem(t("Yaşam"), "rectangle.stack", selected: false) { navExpanded = false; onOpenYasam?() }
            navItem(t("Pano"), "square.grid.2x2", selected: false) { navExpanded = false; onOpenPano?() }
            navItem(t("Takvim"), "calendar", selected: true) { navExpanded = false; onOpenPlanner?() }
        }
        .padding(.horizontal, 5)
        .frame(height: 60)
        .background { Color.clear.liquidGlass(Capsule()).matchedGeometryEffect(id: "dockNav", in: dockNS) }
    }

    private func navItem(_ label: String, _ symbol: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 1) {
                RealSymbol(symbol, size: 21.8)
                Text(label).font(RealFont.inter(12, selected ? .semibold : .medium)).lineLimit(1)
            }
            .foregroundStyle(selected ? Color.white : Color(argb: 0xFFB8B8BE))
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background { if selected { Capsule().fill(LiquidGlassTokens.dockSelectedPill) } }
            .padding(.vertical, 5)
        }
        .accessibilityValue(selected ? t("Seçili") : "")
    }

    // MARK: Composer panel

    private var composerPanel: some View {
        let shape = RoundedRectangle(cornerRadius: expanded ? 26 : 30, style: .continuous)
        return VStack(alignment: .leading, spacing: 0) {
            inputRow
            if expanded {
                if let s = DateSuggestion.detect(text), suggestionNeeded(s) {
                    Button { applySuggestion(s) } label: {
                        Label(s.label, systemImage: "calendar.badge.plus")
                            .font(RealFont.inter(12, .medium))
                            .foregroundStyle(Color(argb: 0xFFDCE8FF))
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(Capsule().fill(RealColors.accentBlue.opacity(0.16)))
                    }
                    .padding(.horizontal, 14).padding(.bottom, 4)
                }
                toolRow
                if schedule.alertAt != nil { durationRow }
                if !attachments.isEmpty { attachmentRow }
                if checklistMode { checklistEditor }
            }
        }
        .padding(.top, expanded ? 12 : 0)
        .padding(.bottom, expanded ? 6 : 0)
        .frame(minHeight: expanded ? 118 : 54)
        .liquidGlass(shape, dim: expanded ? 0.26 : 0)
        // Only the open panel grows: a swipe up on the closed bar still belongs to the home (Yaşam).
        .simultaneousGesture(DragGesture(minimumDistance: 24).onEnded { v in
            guard expanded, voice.phase == .idle, v.translation.height < -40,
                  abs(v.translation.height) > abs(v.translation.width) * 1.5 else { return }
            openFullScreen()
        })
    }

    private var inputRow: some View {
        HStack(alignment: expanded ? .bottom : .center, spacing: 6) {
            ZStack(alignment: .leading) {
                if text.isEmpty {
                    if voice.phase != .idle {
                        Text(voice.phase == .finishing ? t("REAL düşünüyor…") : t("Dinleniyor…"))
                            .font(RealFont.inter(16)).foregroundStyle(Color(argb: 0xFFA2C7FF))
                    } else if checklistMode {
                        Text(t("Başlık")).font(RealFont.inter(16, .semibold)).foregroundStyle(Color(argb: 0xFFB8B8BF))
                    } else if expanded {
                        Text(t("Aklında ne var?")).font(RealFont.inter(16)).foregroundStyle(Color(argb: 0xFF8E8E96))
                    } else {
                        ShimmerText(text: "Add to REAL")
                    }
                }
                TextField("", text: $text, axis: .vertical)
                    .font(checklistMode ? RealFont.inter(16, .semibold) : RealType.composer)
                    .foregroundStyle(voice.phase == .listening ? Color(argb: 0xFF91B9FF) : Color(argb: 0xFFF7F7F8))
                    .tint(Color(argb: 0xFF9AB9FF))
                    .lineLimit(1...4)
                    .focused($focus, equals: .title)
                    .submitLabel(checklistMode ? .next : .done)
                    .onChange(of: text) { _, new in
                        // Return submits (the Android field strips newlines and submits on Done).
                        if !fullScreen, new.contains("\n") {
                            text = new.replacingOccurrences(of: "\n", with: "")
                            if checklistMode { focus = .item(0) } else { submit() }
                        }
                    }
                    .accessibilityLabel(checklistMode ? t("Başlık") : t("Hatırlatıcı metni"))
            }
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            if checklistMode {
                let can = !text.trimmingCharacters(in: .whitespaces).isEmpty && checklistItems.contains { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
                Button(action: submit) {
                    RealSymbol("checkmark", size: 14.9)
                        .foregroundStyle(can ? .white : Color(argb: 0xFF6B6B74))
                        .frame(width: 30, height: 30)
                        .background(Circle().fill(can ? Color(argb: 0xFF295799) : Color(argb: 0xFF1E1E24)))
                        .frame(width: 36, height: 36)
                }
                .disabled(!can)
                .accessibilityLabel(t("Listeyi kaydet"))
            }
            if !isHome {
                Button(action: startVoice) {
                    RealSymbol("mic", size: 23)
                        .foregroundStyle(voice.phase != .idle ? Color(argb: 0xFF69ACFF) : Color(argb: 0xFFE4E4EA))
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(voice.phase == .listening ? Color(argb: 0xFF2D6099).opacity(0.32 + Double(voice.level) * 0.12) : .clear))
                        .scaleEffect(voice.phase == .listening ? 1 + CGFloat(voice.level) * 0.05 : 1)
                        .frame(width: 48, height: 48)
                }
                .accessibilityLabel(voice.phase == .listening ? t("Dinlemeyi bitir") : t("Sesle REAL oluştur"))
            } else if isHome && voice.phase != .idle {
                Button(action: startVoice) {
                    RealSymbol("mic.fill", size: 23).foregroundStyle(Color(argb: 0xFF69ACFF))
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(Color(argb: 0xFF2D6099).opacity(0.32 + Double(voice.level) * 0.12)))
                        .frame(width: 48, height: 48)
                }
                .accessibilityLabel(t("Dinlemeyi bitir"))
            }
        }
        .padding(.leading, 22)
        .padding(.trailing, 9)
        .frame(minHeight: expanded ? 48 : 54)
        .contentShape(Rectangle())
        .onTapGesture { if focus == nil { focus = .title } }
    }

    /// Calendar / category / attachment / checklist / bell, Android icon order.
    private var toolRow: some View {
        // Tools scroll sideways when they do not fit (a long date chip); the bell is the last of them.
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) { tools; bellButton }
        }
        .padding(.horizontal, 14).padding(.top, 2).padding(.bottom, 4)
    }

    /// Notification on/off (an undated card notifies right away). The alarm is picked in the date sheet
    /// (Saat pushed right), so the bell has no alarm state.
    private var bellButton: some View {
        let on = schedule.alertAt != nil || sendImmediate
        return Button { sendImmediate.toggle() } label: {
            RealSymbol(on ? "bell" : "bell.slash", size: 20)
                .foregroundStyle(on ? RealColors.accentBlue : Color(argb: 0xFFBABAC3))
                .frame(width: 40, height: 40)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(on ? t("Bildirim açık") : t("Bildirim kapalı"))
    }

    static let alarmTone = Color(argb: 0xFFFF8A5B)

    @ViewBuilder private var tools: some View {
            if !schedule.isEmpty {
                Button { withoutAnimation { showDateSheet = true } } label: {
                    HStack(spacing: 6) {
                        // An alarm card shows the alarm clock instead of the calendar.
                        RealSymbol(alarm ? "alarm" : "calendar", size: 16.1).foregroundStyle(RealColors.accentBlue)
                        Text(schedule.summary).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFE2E8F0))
                    }
                    .padding(.horizontal, 9).padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 12).fill(RealColors.accentBlue.opacity(0.14)))
                }
                Button { schedule = ScheduleDraft(); alarm = false } label: {
                    RealSymbol("xmark", size: 12.6).foregroundStyle(Color(argb: 0xFFAAAAB4)).frame(width: 36, height: 40)
                }
                .accessibilityLabel(t("Tarihi kaldır"))
            } else {
                toolButton("calendar", t("Tarih seç"), active: false) { withoutAnimation { showDateSheet = true } }
            }
            let cat = store.category(categoryId)
            toolButton(cat?.icon.symbol ?? "square.grid.2x2", t("Kategori seç"), active: cat != nil,
                       tint: cat.map { paletteColor($0.accentStart) ?? RealColors.accentBlue }) { withoutAnimation { showCategoryPicker = true } }
            ZStack(alignment: .topTrailing) {
                toolButton("plus", t("Fotoğraf ekle"), active: !attachments.isEmpty) { showPhotoPicker = true }
                if !attachments.isEmpty {
                    Text("\(attachments.count)").font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                        .frame(width: 14, height: 14).background(Circle().fill(Color(argb: 0xFF3B82F6))).offset(x: -4, y: 4)
                }
            }
            toolButton("checklist", t("Checklist"), active: checklistMode) {
                checklistMode.toggle()
                if checklistMode { checklistItems = [""]; focus = text.isEmpty ? .title : .item(0) }
            }
            // Konum: the card detail's map editor, or a reminder at the place; both are saved with the new card.
            // The green dot: some card is waiting for its place (iOS watches it).
            toolButton("mappin", placeTrigger.map(\.label) ?? location.map { t("Konum: {0}", $0.placeName) } ?? t("Konum ekle"),
                       active: location != nil || placeTrigger != nil) {
                // Straight to the map; "Her zaman" is asked only when a place reminder is set there.
                openPlace()
            }
            .overlay(alignment: .topTrailing) {
                if store.reminders.contains(where: { $0.locationTrigger != nil && !$0.isCompleted }) {
                    Circle().fill(Color(argb: 0xFF55D3B4)).frame(width: 6, height: 6).offset(x: -8, y: 8)
                }
            }
    }

    private func toolButton(_ symbol: String, _ label: String, active: Bool, tint: Color? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            RealSymbol(symbol, size: 20)
                .foregroundStyle(tint ?? (active ? RealColors.accentBlue : Color(argb: 0xFFBABAC3)))
                .frame(width: 40, height: 40)
        }
        .accessibilityLabel(label)
    }

    private var durationRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach([30, 45, 60, 90, 120, 150, 180], id: \.self) { mins in
                    let selected = schedule.durationMinutes == mins
                    Button { schedule.durationMinutes = selected ? nil : mins } label: {
                        Text("\(mins) min")
                            .font(RealFont.inter(10.5, selected ? .semibold : .regular))
                            .foregroundStyle(selected ? Color(argb: 0xFFDCE8FF) : Color(argb: 0xFFB0B0B8))
                            .padding(.horizontal, 7).padding(.vertical, 3)
                            .background(RoundedRectangle(cornerRadius: 10).fill(selected ? RealColors.accentBlue.opacity(0.24) : .white.opacity(0.06)))
                            .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(selected ? RealColors.accentBlue.opacity(0.5) : .white.opacity(0.08), lineWidth: 0.5))
                    }
                }
            }
            .padding(.horizontal, 14)
        }
        .padding(.bottom, 4)
    }

    private var attachmentRow: some View {
        HStack(spacing: 8) {
            ForEach(Array(attachments.enumerated()), id: \.element.id) { index, a in
                ZStack(alignment: .topTrailing) {
                    Group {
                        if let img = AttachmentStorage.image(a.localPath) { Image(uiImage: img).resizable().scaledToFill() } else { Color.gray }
                    }
                    .frame(width: 36, height: 36)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(Color(argb: 0x33FFFFFF), lineWidth: 0.8))
                    .frame(width: 40, height: 40, alignment: .bottomLeading)
                    Button {
                        AttachmentStorage.remove(a)
                        attachments.remove(at: index)
                    } label: {
                        RealSymbol("xmark", size: 8).foregroundStyle(Color(argb: 0xFFEEEEF2))
                            .frame(width: 16, height: 16).background(Circle().fill(Color(argb: 0xE61E1E24)))
                    }
                }
            }
        }
        .padding(.horizontal, 14).padding(.bottom, 4)
    }

    private var checklistEditor: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(checklistItems.indices, id: \.self) { index in
                HStack(spacing: 10) {
                    Circle().strokeBorder(Color(argb: 0xFF5A5A66), lineWidth: 1.2).frame(width: 16, height: 16)
                    TextField("", text: Binding(get: { checklistItems.indices.contains(index) ? checklistItems[index] : "" },
                                                set: { if checklistItems.indices.contains(index) { checklistItems[index] = $0 } }),
                              prompt: Text(t("Madde {0}", index + 1)).foregroundStyle(Color(argb: 0xFF6C6C76)))
                        .font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFEEEEF2))
                        .focused($focus, equals: .item(index))
                        .submitLabel(.next)
                        .onSubmit { checklistEnter(index) }
                    if checklistItems.count > 1 {
                        Button { checklistItems.remove(at: index) } label: {
                            RealSymbol("xmark", size: 11.5).foregroundStyle(Color(argb: 0xFF7A7A85)).frame(width: 24, height: 24)
                        }
                    }
                }
                .padding(.horizontal, 18).padding(.vertical, 2)
            }
            Button {
                if checklistItems.last?.trimmingCharacters(in: .whitespaces).isEmpty == false || checklistItems.isEmpty { checklistItems.append("") }
                focus = .item(checklistItems.count - 1)
            } label: {
                Label(t("Madde ekle"), systemImage: "plus").font(RealFont.inter(13, .medium)).foregroundStyle(RealColors.accentBlue)
            }
            .padding(.horizontal, 20).padding(.vertical, 6)
        }
        .padding(.bottom, 6)
    }

    private func checklistEnter(_ index: Int) {
        let current = checklistItems.indices.contains(index) ? checklistItems[index] : ""
        if !current.trimmingCharacters(in: .whitespaces).isEmpty && index == checklistItems.count - 1 { checklistItems.append("") }
        if index + 1 < checklistItems.count { focus = .item(index + 1) }
    }

    // MARK: Actions

    private func suggestionNeeded(_ s: DateSuggestion) -> Bool {
        let cal = Calendar.real
        let target = cal.date(byAdding: .day, value: s == .today ? 0 : 1, to: cal.startOfDay(for: Date()))!
        return !(schedule.date.map { cal.isDate($0, inSameDayAs: target) } ?? false)
    }

    private func applySuggestion(_ s: DateSuggestion) {
        let cal = Calendar.real
        schedule = ScheduleDraft()
        schedule.date = cal.date(byAdding: .day, value: s == .today ? 0 : 1, to: cal.startOfDay(for: Date()))
        schedule.hasTime = false
    }

    private func startVoiceIfIdle() { if voice.phase == .idle { startVoice() } }

    /// Applies what the AI returned (also after "Doğru mu anladım?").
    private func handleVoice(_ outcome: VoiceAI.Outcome) {
        switch outcome {
        case .created:
            reset()
            focus = nil
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        case .draft(let title, let draft, let category, let items):
            text = title
            schedule = draft
            if let category { categoryId = category }
            if !items.isEmpty { checklistMode = true; checklistItems = items }
        case .confirm(let pending):
            voiceConfirm = pending
        case .none:
            break
        }
    }

    private func startVoice() {
        focus = nil
        // Names the recognizer should expect: categories and short open card titles (where foreign titles live).
        VoiceInput.hints = store.categories.map(\.name) + store.cards
            .filter { !$0.isCompleted }
            .map { $0.text.split(separator: "\n").first.map(String.init) ?? $0.text }
            .filter { $0.split(separator: " ").count <= 4 }
            .prefix(60)
        let base = text
        // A spoken command into an empty composer goes to the AI (signed in, with consent); it either creates the
        // cards, fills the composer for a last look (a clock to pick), or leaves the transcript as typed text.
        let interpret: ((String, @escaping () -> Void) -> Void)? = base.isEmpty && RealAuth.shared.account != nil ? { spoken, done in
            Task { @MainActor in
                handleVoice(await VoiceAI.interpret(spoken, store: store))
                done()
            }
        } : nil
        voice.toggle(onText: { spoken in
            text = base.isEmpty ? spoken : base + " " + spoken
        }, onFinished: interpret)
    }

    private func importPhotos(_ items: [PhotosPickerItem]) {
        guard !items.isEmpty else { return }
        Task {
            var added: [ReminderAttachment] = []
            for item in items {
                if let data = try? await item.loadTransferable(type: Data.self), let a = AttachmentStorage.save(data) { added.append(a) }
            }
            await MainActor.run { attachments.append(contentsOf: added); photoItems = []; focus = .title }
        }
    }

    private func submit() {
        if voice.phase != .idle { voice.finish(); return }
        let title = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let items = checklistMode ? checklistItems.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty } : []
        guard !title.isEmpty, !checklistMode || !items.isEmpty else { return }
        let draft = schedule
        let files = attachments
        let notify = sendImmediate
        let place = location
        let trigger = placeTrigger
        let rings = alarm && draft.alertAt != nil
        let note = noteText.trimmingCharacters(in: .whitespacesAndNewlines)
        let voices = voiceNotes, songs = musicPicks, titles = mediaPicks, games = gamePicks
        store.addReminder(text: title, alertAt: draft.alertAt, plannedDay: draft.plannedDay, repeatRule: draft.repeatRule,
                          categoryId: categoryId, isImportant: important, checklist: items) { item in
            draft.apply(to: &item)
            item.attachments = files
            item.sendImmediateNotification = notify
            if let place { item.location = place }
            item.isAlarm = rings
            item.locationTrigger = trigger
            if !note.isEmpty { item.cardStack = CardStackBlock(cards: [CardStackCard(body: note)]) }
            item.voiceRecordings = voices
            item.musicBlocks = songs
            item.mediaBlocks = titles
            item.gameBlocks = games
            ComposerBar.applyHeroDefault(&item)
        }
        if draft.alertAt != nil || trigger != nil { NotificationScheduler.requestAuthorization() }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        reset()
        focus = nil
        fullScreen = false
    }

    private func reset() {
        text = ""
        schedule = ScheduleDraft()
        if defaultPlannedToday {
            schedule.date = Calendar.real.startOfDay(for: Date())
            schedule.hasTime = false
        }
        categoryId = defaultCategoryId
        important = defaultImportant
        checklistMode = false
        checklistItems = [""]
        attachments = []
        sendImmediate = false
        alarm = false
        location = nil
        placeTrigger = nil
        noteText = ""
        voiceNotes = []
        musicPicks = []
        mediaPicks = []
        gamePicks = []
        fsPage = nil
        fsOpenRow = nil
    }
}

// MARK: Full screen (GlassComposer fullScreen)

/// Full-screen composer: pushed up on the open panel. Title, a note, the card-detail rows as soft blocks
/// (Tarih ve saat, Kategori, Konum, Liste, Ekler, Kamera, Ses kaydı, Medya) and everything added as tiles at the bottom.
extension ComposerBar {
    enum FullScreenPage { case checklist, media }

    func gate(_ binding: Binding<Bool>, _ active: Bool) -> Binding<Bool> {
        Binding(get: { active && binding.wrappedValue }, set: { binding.wrappedValue = $0 })
    }

    /// The composer's sheets, presented from the dock or (while it is open) from the full-screen page above it.
    func withComposerSheets<V: View>(_ content: V, active: Bool) -> some View {
        content
            .realOverlay(isPresented: gate($showDateSheet, active)) {
                ScheduleSheet(draft: $schedule, alarm: Binding(get: { alarm }, set: { on in
                    if on && !alarm, #available(iOS 26.0, *), !RealAlarmKit.authorized {
                        // First alarm: the system's permission prompt; once allowed, alarm cards move to AlarmKit.
                        Task { if await RealAlarmKit.requestAuthorization() { NotificationScheduler.reschedule(store.reminders) } }
                    }
                    alarm = on
                }))
            }
            .realOverlay(isPresented: gate($showCategoryPicker, active)) { CategoryPickerSheet(selected: $categoryId) }
            // The pin goes straight to the map; "Her zaman" is asked only once a place reminder is set on it.
            .fullScreenCover(isPresented: gate($showLocationEditor, active), onDismiss: {
                if !fullScreen { DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { focus = .title } }
            }) {
                LocationEditor(initial: location, initialTrigger: placeTrigger,
                               onRemind: { place, trigger in
                                   location = place
                                   placeTrigger = trigger
                                   if !placePermission.granted { placePermission.request { _ in } }
                               }) { picked in
                    location = picked
                    placeTrigger = nil
                }
            }
            .photosPicker(isPresented: gate($showPhotoPicker, active), selection: $photoItems, maxSelectionCount: 10, matching: .images)
            .fullScreenCover(isPresented: gate($showCamera, active)) {
                CameraPicker { data in if let a = AttachmentStorage.save(data) { attachments.append(a) } }.ignoresSafeArea()
            }
            .fileImporter(isPresented: gate($showFileImporter, active), allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
                if case .success(let urls) = result { attachments += urls.compactMap { AttachmentStorage.saveFile($0) } }
            }
            .sheet(isPresented: gate($showNewCategory, active)) { CategoryEditorSheet(existing: nil) { categoryId = $0.id } }
    }

    func openFullScreen() {
        focus = nil
        fsPage = nil
        fsOpenRow = nil
        fullScreen = true
    }

    func closeFullScreen() {
        if fsRecorder.recording { if let r = fsRecorder.stop() { voiceNotes.append(r) } }
        fsRecorder.stopPlayback()
        focus = nil
        fsPage = nil
        fullScreen = false
    }

    var fullScreenPage: some View {
        withComposerSheets(
            VStack(spacing: 0) {
                fsTopBar
                Group {
                    switch fsPage {
                    case .checklist: fsChecklistPage
                    case .media:
                        MediaSearchAll(music: musicPicks, media: mediaPicks, games: gamePicks,
                                       onAddMusic: { musicPicks.append($0) },
                                       onAddMedia: { mediaPicks.append($0) },
                                       onAddGame: { g in if !gamePicks.contains(where: { $0.providerId == g.providerId }) { gamePicks.append(g) } },
                                       onRemoveMusic: { m in musicPicks.removeAll { $0.id == m.id } },
                                       onRemoveMedia: { m in mediaPicks.removeAll { $0.id == m.id } },
                                       onRemoveGame: { g in gamePicks.removeAll { $0.id == g.id } })
                            .padding(.horizontal, 14).padding(.top, 8)
                    case nil: fsMain
                    }
                }
                .frame(maxHeight: .infinity, alignment: .top)
                if fsPage == nil && trayCount > 0 { fsTray }
            }
            .background(Color.black.ignoresSafeArea())
            .presentationBackground(.black),
            active: true)
    }

    private var fsTopBar: some View {
        VStack(spacing: 4) {
            Capsule().fill(Color.white.opacity(0.28)).frame(width: 36, height: 4)
                .frame(maxWidth: .infinity).frame(height: 18).contentShape(Rectangle())
                .onTapGesture { closeFullScreen() }
                .accessibilityLabel(t("Küçült"))
            HStack(spacing: 6) {
                if let cat = store.category(categoryId) {
                    Circle().fill(paletteColor(cat.accentStart) ?? RealColors.accentBlue).frame(width: 7, height: 7)
                    Text(cat.name).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFE4E4EA).opacity(0.8)).lineLimit(1)
                }
                Spacer()
                if fsPage != nil {
                    Button(t("Tamam")) { if !fsRecorder.recording { focus = nil; fsPage = nil } }
                        .font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFF91B9FF))
                        .padding(.horizontal, 10).frame(height: 40)
                } else {
                    bellButton
                    Button { important.toggle() } label: {
                        RealSymbol(important ? "star.fill" : "star", size: 18)
                            .foregroundStyle(important ? Color(argb: 0xFFEACB75) : Color(argb: 0xFFBABAC3))
                            .frame(width: 40, height: 40)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(important ? t("Önemli işaretini kaldır") : t("Önemli olarak işaretle"))
                }
            }
            .padding(.leading, 22).padding(.trailing, 10)
        }
        .padding(.top, 4)
        .contentShape(Rectangle())
        // Pulled down on the top bar: back to the dock.
        .gesture(DragGesture(minimumDistance: 20).onEnded { v in if v.translation.height > 50 { closeFullScreen() } })
    }

    private func fsTitleChanged(_ new: String) {
        guard new.contains("\n") else { return }
        text = new.replacingOccurrences(of: "\n", with: "")
        if fsPage == .checklist { focus = .fsItem(0) } else { submit() }
    }

    private var fsMain: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top, spacing: 10) {
                    TextField("", text: $text, prompt: Text(t("Aklında ne var?")).foregroundStyle(Color(argb: 0xFF8E8E96)), axis: .vertical)
                        .font(RealFont.inter(30)).foregroundStyle(Color(argb: 0xFFF7F7F8)).tint(Color(argb: 0xFF9AB9FF))
                        .lineLimit(1...5)
                        .focused($focus, equals: .fsTitle)
                        .submitLabel(.done)
                        .onChange(of: text) { _, new in fsTitleChanged(new) }
                        .accessibilityLabel(t("Hatırlatıcı metni"))
                    // Small blue done key (keyboard enter): only there once something is written.
                    if !text.trimmingCharacters(in: .whitespaces).isEmpty {
                        Button(action: submit) {
                            RealSymbol("checkmark", size: 13).foregroundStyle(.white)
                                .frame(width: 26, height: 26).background(Circle().fill(Color(argb: 0xFF3A7BFF)))
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 9)
                        .accessibilityLabel(t("Kaydet"))
                        .transition(.opacity)
                    }
                }
                .animation(.easeOut(duration: 0.16), value: text.isEmpty)
                .padding(.leading, 22).padding(.trailing, 20).padding(.top, 12)

                TextField("", text: $noteText, prompt: Text(t("Not ekle")).foregroundStyle(Color(argb: 0xFF6E6E78)), axis: .vertical)
                    .font(RealFont.inter(17)).foregroundStyle(Color(argb: 0xFFD9D9E0)).tint(.white)
                    .lineLimit(3...12)
                    .focused($focus, equals: .fsNote)
                    .padding(.horizontal, 22).padding(.top, 8).padding(.bottom, 26)

                VStack(spacing: 8) {
                    dateBlock
                    categoryBlock
                    fsBlock {
                        fsRow("mappin", t("Konum"), placeTrigger.map(\.label) ?? location?.placeName ?? t("Ekle"),
                              active: location != nil || placeTrigger != nil) { openPlace() }
                    }
                    fsBlock {
                        let filled = checklistItems.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.count
                        fsRow("checklist", t("Liste"), checklistMode && filled > 0 ? t("{0} madde", filled) : t("Ekle"),
                              active: checklistMode && filled > 0) {
                            if !checklistMode { checklistMode = true; checklistItems = [""] }
                            fsPage = .checklist
                        }
                    }
                    // Ekler: photos and any file; camera, voice and media sit on their own rows.
                    fsBlock {
                        Menu {
                            Button(t("Fotoğraf")) { showPhotoPicker = true }
                            Button(t("Dosya")) { showFileImporter = true }
                        } label: {
                            fsRowLabel("plus", t("Ekler"), attachments.isEmpty ? t("Ekle") : t("{0} ek", attachments.count), active: !attachments.isEmpty)
                        }
                        .buttonStyle(.plain)
                    }
                    fsBlock { fsRow("camera", t("Kamera"), t("Çek"), active: false) { focus = nil; Task { if await PermissionPrompt.cameraReady() { showCamera = true } } } }
                    voiceBlock
                    fsBlock {
                        let count = musicPicks.count + mediaPicks.count + gamePicks.count
                        fsRow("music.note", t("Medya"), count == 0 ? t("Şarkı, film, dizi") : t("{0} ek", count), active: count > 0) {
                            focus = nil
                            fsPage = .media
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 16)
            }
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private var dateBlock: some View {
        fsBlock {
            fsRow("calendar", t("Tarih ve saat"), schedule.isEmpty ? t("Yok") : schedule.summary, active: !schedule.isEmpty) { toggleRow("date") }
            if fsOpenRow == "date" {
                fsChips {
                    fsChip(t("Bugün"), on: isDay(0)) { applySuggestion(.today) }
                    fsChip(t("Yarın"), on: isDay(1)) { applySuggestion(.tomorrow) }
                    fsChip(t("Seç…")) { fsOpenRow = nil; withoutAnimation { showDateSheet = true } }
                    if !schedule.isEmpty { fsChip(t("Kaldır")) { fsOpenRow = nil; schedule = ScheduleDraft(); alarm = false } }
                }
                // A day without a time: a few likely times for that day.
                if let day = schedule.date, !schedule.hasTime {
                    let hours = quickHours(day)
                    if !hours.isEmpty {
                        fsChips {
                            ForEach(hours, id: \.self) { h in
                                fsChip(String(format: "%02d:00", h)) {
                                    schedule.date = Calendar.real.date(bySettingHour: h, minute: 0, second: 0, of: day)
                                    schedule.hasTime = true
                                    fsOpenRow = nil
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private var categoryBlock: some View {
        fsBlock {
            let cat = store.category(categoryId)
            fsRow("square.grid.2x2", t("Kategori"), cat?.name ?? t("Yok"), dot: cat.flatMap { paletteColor($0.accentStart) },
                  active: cat != nil) { toggleRow("category") }
            if fsOpenRow == "category" {
                fsChips {
                    fsChip(t("Yok"), on: categoryId == nil) { categoryId = nil; fsOpenRow = nil }
                    ForEach(store.categories) { c in
                        fsChip(c.name, dot: paletteColor(c.accentStart), on: c.id == categoryId) { categoryId = c.id; fsOpenRow = nil }
                    }
                    fsChip(t("Yeni…")) { fsOpenRow = nil; showNewCategory = true }
                }
            }
        }
    }

    /// Kaydet starts recording right on the row; a blue circle breathes with the voice level.
    private var voiceBlock: some View {
        fsBlock {
            if fsRecorder.recording {
                Button { if let r = fsRecorder.stop() { voiceNotes.append(r) } } label: {
                    HStack(spacing: 10) {
                        ZStack {
                            Circle().fill(Color(argb: 0xFF4F8DFF)).frame(width: 28, height: 28)
                                .scaleEffect(CGFloat(0.55 + 0.45 * fsRecorder.level))
                                .opacity(0.25 + 0.35 * fsRecorder.level)
                            Circle().fill(Color(argb: 0xFF91B9FF)).frame(width: 12, height: 12)
                        }
                        .frame(width: 28, height: 28)
                        .animation(.easeOut(duration: 0.11), value: fsRecorder.level)
                        Text(t("Dinliyorum…")).font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFE9E9EE))
                        Spacer()
                        Text(formatVoiceDuration(fsRecorder.elapsed)).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFB9CFFF))
                        RoundedRectangle(cornerRadius: 3).fill(Color(argb: 0xFFE9E9EE)).frame(width: 13, height: 13).padding(.leading, 8)
                    }
                    .padding(.horizontal, 10).padding(.vertical, 11)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(t("Ses kaydını durdur"))
            } else {
                fsRow("mic", t("Ses kaydı"), voiceNotes.isEmpty ? t("Kaydet") : t("{0} kayıt", voiceNotes.count),
                      active: !voiceNotes.isEmpty) { focus = nil; fsRecorder.start() }
            }
            if let error = fsRecorder.error {
                Text(error).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFE0B5A8))
                    .padding(.leading, 50).padding(.trailing, 12).padding(.bottom, 10)
            }
            ForEach(voiceNotes) { r in
                HStack(spacing: 10) {
                    Button { fsRecorder.togglePlayback(r) } label: {
                        RealSymbol(fsRecorder.playingId == r.id ? "pause.fill" : "play.fill", size: 13)
                            .foregroundStyle(.white).frame(width: 30, height: 30)
                            .background(Circle().fill(Color.white.opacity(0.10)))
                    }
                    .buttonStyle(.plain)
                    Text(formatVoiceDuration(r.durationMillis)).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFD4D4DA))
                    Spacer()
                    Button { removeVoice(r) } label: {
                        RealSymbol("xmark", size: 11).foregroundStyle(Color(argb: 0xFF9A9AA4)).frame(width: 30, height: 30)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(t("Kaldır"))
                }
                .padding(.leading, 46).padding(.trailing, 10).padding(.bottom, 8)
            }
        }
    }

    private var fsChecklistPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                TextField("", text: $text, prompt: Text(t("Başlık")).foregroundStyle(Color(argb: 0xFF8E8E96)), axis: .vertical)
                    .font(RealFont.inter(30, .semibold)).foregroundStyle(.white).tint(Color(argb: 0xFF9AB9FF))
                    .lineLimit(1...4)
                    .focused($focus, equals: .fsTitle)
                    .onChange(of: text) { _, new in fsTitleChanged(new) }
                    .padding(.horizontal, 22).padding(.top, 12).padding(.bottom, 12)
                ForEach(checklistItems.indices, id: \.self) { index in
                    HStack(spacing: 12) {
                        Circle().strokeBorder(Color(argb: 0xFF5A5A66), lineWidth: 1.4).frame(width: 20, height: 20)
                        TextField("", text: Binding(get: { checklistItems.indices.contains(index) ? checklistItems[index] : "" },
                                                    set: { if checklistItems.indices.contains(index) { checklistItems[index] = $0 } }),
                                  prompt: Text(t("Madde {0}", index + 1)).foregroundStyle(Color(argb: 0xFF6C6C76)))
                            .font(RealFont.inter(18)).foregroundStyle(Color(argb: 0xFFEEEEF2))
                            .focused($focus, equals: .fsItem(index))
                            .submitLabel(.next)
                            .onSubmit {
                                let current = checklistItems.indices.contains(index) ? checklistItems[index] : ""
                                if !current.trimmingCharacters(in: .whitespaces).isEmpty && index == checklistItems.count - 1 { checklistItems.append("") }
                                if index + 1 < checklistItems.count { focus = .fsItem(index + 1) }
                            }
                        if checklistItems.count > 1 {
                            Button { checklistItems.remove(at: index) } label: {
                                RealSymbol("xmark", size: 11.5).foregroundStyle(Color(argb: 0xFF7A7A85)).frame(width: 28, height: 28)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 22).padding(.vertical, 6)
                }
                Button {
                    if checklistItems.last?.trimmingCharacters(in: .whitespaces).isEmpty == false || checklistItems.isEmpty { checklistItems.append("") }
                    focus = .fsItem(checklistItems.count - 1)
                } label: {
                    Label(t("Madde ekle"), systemImage: "plus").font(RealFont.inter(14, .medium)).foregroundStyle(RealColors.accentBlue)
                }
                .padding(.horizontal, 24).padding(.vertical, 10)
            }
        }
        .scrollDismissesKeyboard(.interactively)
    }

    var trayCount: Int { attachments.count + voiceNotes.count + musicPicks.count + mediaPicks.count + gamePicks.count }

    /// Everything added sits here as square tiles: photos, files, voice notes, songs, films, series and games.
    private var fsTray: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(attachments) { a in
                    trayTile({ AttachmentStorage.remove(a); attachments.removeAll { $0.id == a.id } }) {
                        if a.type == .image, let img = AttachmentStorage.image(a.localPath) { Image(uiImage: img).resizable().scaledToFill() }
                        else { RealSymbol("doc", size: 22).foregroundStyle(Color(argb: 0xFFD4D4DA)) }
                    }
                }
                ForEach(voiceNotes) { r in
                    trayTile({ removeVoice(r) }) { RealSymbol("waveform", size: 22).foregroundStyle(Color(argb: 0xFF91B9FF)) }
                }
                ForEach(musicPicks) { m in trayTile({ musicPicks.removeAll { $0.id == m.id } }) { MusicArtwork(url: m.artworkUrl) } }
                ForEach(mediaPicks) { m in trayTile({ mediaPicks.removeAll { $0.id == m.id } }) { PosterArtwork(media: m, titleSize: 6) } }
                ForEach(gamePicks) { g in trayTile({ gamePicks.removeAll { $0.id == g.id } }) { GameCover(block: g) } }
            }
            .padding(.horizontal, 14).padding(.top, 6)
        }
        .padding(.bottom, 10)
    }

    private func trayTile<C: View>(_ remove: @escaping () -> Void, @ViewBuilder _ content: () -> C) -> some View {
        ZStack(alignment: .topTrailing) {
            ZStack { Color.white.opacity(0.08); content() }
                .frame(width: 58, height: 58)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(Color(argb: 0x33FFFFFF), lineWidth: 0.8))
                .frame(width: 62, height: 62, alignment: .bottomLeading)
            Button(action: remove) {
                RealSymbol("xmark", size: 9).foregroundStyle(Color(argb: 0xFFEEEEF2))
                    .frame(width: 20, height: 20).background(Circle().fill(Color(argb: 0xE61E1E24)))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(t("Kaldır"))
        }
    }

    // Row building blocks: each row its own soft block, choices open under it as chips.

    private func fsBlock<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        VStack(spacing: 0, content: content)
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color.white.opacity(0.055)))
    }

    private func fsRowLabel(_ symbol: String, _ label: String, _ value: String?, dot: Color? = nil, active: Bool) -> some View {
        HStack(spacing: 14) {
            RealSymbol(symbol, size: 19).foregroundStyle(active ? Color(argb: 0xFF91B9FF) : Color(argb: 0xFFBABAC3)).frame(width: 22)
            Text(label).font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFE9E9EE))
            Spacer()
            if let dot { Circle().fill(dot).frame(width: 7, height: 7) }
            if let value {
                Text(value).font(RealFont.inter(13)).foregroundStyle(active ? Color(argb: 0xFFB9CFFF) : Color(argb: 0xFF7C7C86))
                    .lineLimit(1).frame(maxWidth: 170, alignment: .trailing)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 15)
        .contentShape(Rectangle())
    }

    private func fsRow(_ symbol: String, _ label: String, _ value: String?, dot: Color? = nil, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { fsRowLabel(symbol, label, value, dot: dot, active: active) }
            .buttonStyle(.plain)
            .disabled(fsRecorder.recording)
    }

    private func fsChips<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6, content: content)
        }
        .padding(.leading, 50).padding(.trailing, 12).padding(.bottom, 12)
    }

    private func fsChip(_ label: String, dot: Color? = nil, on: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let dot { Circle().fill(dot).frame(width: 7, height: 7) }
                Text(label).font(RealFont.inter(13))
            }
            .foregroundStyle(on ? Color.white : Color(argb: 0xFFD4D4DA))
            .padding(.horizontal, 11).padding(.vertical, 6)
            .background(Capsule().fill(on ? Color.white.opacity(0.10) : Color.clear))
            .overlay(Capsule().strokeBorder(Color.white.opacity(on ? 0.7 : 0.22), lineWidth: 0.5))
        }
        .buttonStyle(.plain)
    }

    private func toggleRow(_ row: String) {
        withAnimation(.easeOut(duration: 0.18)) { fsOpenRow = fsOpenRow == row ? nil : row }
    }

    private func isDay(_ offset: Int) -> Bool {
        let cal = Calendar.real
        guard let target = cal.date(byAdding: .day, value: offset, to: cal.startOfDay(for: Date())) else { return false }
        return schedule.date.map { cal.isDate($0, inSameDayAs: target) } ?? false
    }

    private func quickHours(_ day: Date) -> [Int] {
        let cal = Calendar.real
        let nowHour = cal.component(.hour, from: Date())
        return Array([9, 12, 15, 18, 21].filter { !cal.isDateInToday(day) || $0 > nowHour }.prefix(4))
    }

    func openPlace() {
        focus = nil
        pickForTrigger = true
        showLocationEditor = true
    }

    func removeVoice(_ r: ReminderVoiceRecording) {
        if fsRecorder.playingId == r.id { fsRecorder.stopPlayback() }
        try? FileManager.default.removeItem(at: AttachmentStorage.directory.appendingPathComponent(r.localPath))
        voiceNotes.removeAll { $0.id == r.id }
    }

    /// A new card with a few blocks opens with its most visual one on top, big ("En üstteki içeriği büyüt" ticked).
    static func applyHeroDefault(_ item: inout ReminderItem) {
        let blocks = item.cardContentKeys().filter { $0 != "text" && $0 != "weather" }
        guard blocks.count >= 2, item.contentBlockOrder.isEmpty, item.contentBlockLayout.isEmpty else { return }
        let hero = ["photos:", "media:", "game:", "music:"].lazy.compactMap { p in blocks.first { $0.hasPrefix(p) } }.first ?? blocks[0]
        item.contentBlockOrder = [hero] + item.cardContentKeys().filter { $0 != hero }
        var placement = CardBlockPlacement()
        placement.span = CardBlockPlacement.units
        placement.column = 0
        placement.minHeight = detailHeroMinHeight
        item.contentBlockLayout = [hero: placement.normalized]
    }
}

/// "Kategori Seç" glass panel (CategorySelectionOverlay.kt): Kategori yok, Yeni kategori ekle…, then every category.
struct CategoryPickerSheet: View {
    @Environment(ReminderStore.self) private var store
    @Binding var selected: Int64?
    @State private var showNew = false

    var body: some View {
        OverlayPanel { close in
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(t("Kategori Seç")).font(RealFont.inter(20, .semibold)).foregroundStyle(.white)
                    Spacer()
                    Button(action: close) {
                        RealSymbol("xmark", size: 17.2).foregroundStyle(Color(argb: 0xFFB8B8BE)).frame(width: 40, height: 40)
                    }
                    .accessibilityLabel(t("Kapat"))
                }
                .padding(.bottom, 10)
                ScrollView {
                    VStack(spacing: 4) {
                        row(isSelected: selected == nil, highlight: selected == nil) {
                            RealSymbol("xmark", size: 13.8).foregroundStyle(Color(argb: 0xFFC8C8D0))
                                .frame(width: 34, height: 34).background(Circle().fill(Color(argb: 0xFF2A2A30)))
                        } title: { Text(t("Kategori yok")).font(RealFont.inter(17, .semibold)) } action: { selected = nil; close() }
                        row(isSelected: false, highlight: false) {
                            RealSymbol("plus", size: 14.9).foregroundStyle(RealColors.accentBlue)
                                .frame(width: 34, height: 34).background(Circle().fill(RealColors.accentBlue.opacity(0.14)))
                                .overlay(Circle().strokeBorder(RealColors.accentBlue.opacity(0.35), lineWidth: 0.8))
                        } title: { Text(t("Yeni kategori ekle...")).font(RealFont.inter(17)) } action: { showNew = true }
                        Rectangle().fill(.white.opacity(0.10)).frame(height: 1).padding(.vertical, 6).padding(.horizontal, 4)
                        ForEach(store.categories) { category in
                            row(isSelected: selected == category.id, highlight: selected == category.id) {
                                let start = paletteColor(category.accentStart) ?? Color(argb: 0xFF233F73)
                                let end = paletteColor(category.accentEnd) ?? start
                                RealSymbol(category.icon.symbol, size: 14.9).foregroundStyle(.white)
                                    .frame(width: 34, height: 34)
                                    .background(Circle().fill(LinearGradient(colors: [start, end], startPoint: .topLeading, endPoint: .bottomTrailing)))
                            } title: { Text(category.name).font(RealFont.inter(17)) } action: { selected = category.id; close() }
                        }
                    }
                }
                .frame(maxHeight: 420)
                .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 22).padding(.top, 18).padding(.bottom, 16)
        }
        .sheet(isPresented: $showNew) { CategoryEditorSheet(existing: nil) { selected = $0.id } }
    }

    private func row<Icon: View, Title: View>(isSelected: Bool, highlight: Bool, @ViewBuilder icon: () -> Icon,
                                              @ViewBuilder title: () -> Title, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                icon()
                title().foregroundStyle(.white).lineLimit(1)
                Spacer()
                if isSelected { RealSymbol("checkmark", size: 17.2).foregroundStyle(.white) }
            }
            .padding(.horizontal, 10).frame(minHeight: 56)
            .background { if highlight { RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.white.opacity(0.05)) } }
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScaleStyle(scale: 0.98))
    }
}

extension Notification.Name {
    /// Yaşam mic → Home dock voice capture.
    static let realStartVoice = Notification.Name("REALStartVoice")
}
