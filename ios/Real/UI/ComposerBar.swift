import SwiftUI
import Combine
import UIKit
import PhotosUI

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
        guard let date else { return "Tarih" }
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
    var label: String { self == .today ? "Bugün'e ekle" : "Yarına ekle" }

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
    @State private var categoryId: Int64?
    @State private var important = false
    @State private var sendImmediate = false
    @State private var checklistMode = false
    @State private var checklistItems: [String] = [""]
    @State private var attachments: [ReminderAttachment] = []
    @State private var photoItems: [PhotosPickerItem] = []
    @State private var showDateSheet = false
    @State private var showCategoryPicker = false
    @State private var showPhotoPicker = false
    @State private var navExpanded = false
    @State private var voice = VoiceInput()
    @Namespace private var dockNS
    @FocusState private var focus: Field?

    private enum Field: Hashable { case title, item(Int) }

    private var expanded: Bool { focus != nil || !text.isEmpty || checklistMode || voice.phase != .idle }
    private var showChrome: Bool { isHome && !expanded }

    var body: some View {
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
                        .accessibilityLabel("Takvim")
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
        // Dock bottom edge ~20pt above the screen edge (34pt safe area − 14), just above the home indicator.
        .padding(.bottom, expanded ? 8 : -14)
        .animation(.spring(response: 0.32, dampingFraction: 0.85), value: expanded)
        .animation(.spring(response: 0.34, dampingFraction: 0.85), value: navExpanded)
        .highPriorityGesture(
            DragGesture(minimumDistance: 20).onEnded { value in
                guard showChrome else { return }
                if value.translation.height < -28 { navExpanded = true } else if value.translation.height > 28 { navExpanded = false }
            }
        )
        .onChange(of: expanded) { _, e in if e { navExpanded = false } }
        .onAppear(perform: reset)
        .onReceive(NotificationCenter.default.publisher(for: .realStartVoice)) { _ in if isHome { startVoiceIfIdle() } }
        .realOverlay(isPresented: $showDateSheet) { ScheduleSheet(draft: $schedule) }
        .realOverlay(isPresented: $showCategoryPicker) { CategoryPickerSheet(selected: $categoryId) }
        .photosPicker(isPresented: $showPhotoPicker, selection: $photoItems, maxSelectionCount: 10, matching: .images)
        .onChange(of: photoItems) { _, items in importPhotos(items) }
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
        dockCircle(symbol: "mic", label: "Sesle REAL oluştur") { startVoice() }
    }

    /// HomeDockExpandedNavigation: Yaşam · Pano · Takvim, Takvim selected.
    private var navigationBar: some View {
        HStack(spacing: 0) {
            navItem("Yaşam", "rectangle.stack", selected: false) { navExpanded = false; onOpenYasam?() }
            navItem("Pano", "square.grid.2x2", selected: false) { navExpanded = false; onOpenPano?() }
            navItem("Takvim", "calendar", selected: true) { navExpanded = false; onOpenPlanner?() }
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
        .accessibilityValue(selected ? "Seçili" : "")
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
    }

    private var inputRow: some View {
        HStack(alignment: expanded ? .bottom : .center, spacing: 6) {
            ZStack(alignment: .leading) {
                if text.isEmpty {
                    if voice.phase != .idle {
                        Text(voice.phase == .finishing ? "REAL düşünüyor…" : "Dinleniyor…")
                            .font(RealFont.inter(16)).foregroundStyle(Color(argb: 0xFFA2C7FF))
                    } else if checklistMode {
                        Text("Başlık").font(RealFont.inter(16, .semibold)).foregroundStyle(Color(argb: 0xFFB8B8BF))
                    } else if expanded {
                        Text("Aklında ne var?").font(RealFont.inter(16)).foregroundStyle(Color(argb: 0xFF8E8E96))
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
                        if new.contains("\n") {
                            text = new.replacingOccurrences(of: "\n", with: "")
                            if checklistMode { focus = .item(0) } else { submit() }
                        }
                    }
                    .accessibilityLabel(checklistMode ? "Başlık" : "Hatırlatıcı metni")
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
                .accessibilityLabel("Listeyi kaydet")
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
                .accessibilityLabel(voice.phase == .listening ? "Dinlemeyi bitir" : "Sesle REAL oluştur")
            } else if isHome && voice.phase != .idle {
                Button(action: startVoice) {
                    RealSymbol("mic.fill", size: 23).foregroundStyle(Color(argb: 0xFF69ACFF))
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(Color(argb: 0xFF2D6099).opacity(0.32 + Double(voice.level) * 0.12)))
                        .frame(width: 48, height: 48)
                }
                .accessibilityLabel("Dinlemeyi bitir")
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
        HStack(spacing: 4) {
            if !schedule.isEmpty {
                Button { withoutAnimation { showDateSheet = true } } label: {
                    HStack(spacing: 6) {
                        RealSymbol("calendar", size: 16.1).foregroundStyle(RealColors.accentBlue)
                        Text(schedule.summary).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFE2E8F0))
                    }
                    .padding(.horizontal, 9).padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 12).fill(RealColors.accentBlue.opacity(0.14)))
                }
                Button { schedule = ScheduleDraft() } label: {
                    RealSymbol("xmark", size: 12.6).foregroundStyle(Color(argb: 0xFFAAAAB4)).frame(width: 36, height: 40)
                }
                .accessibilityLabel("Tarihi kaldır")
            } else {
                toolButton("calendar", "Tarih seç", active: false) { withoutAnimation { showDateSheet = true } }
            }
            let cat = store.category(categoryId)
            toolButton(cat?.icon.symbol ?? "square.grid.2x2", "Kategori seç", active: cat != nil,
                       tint: cat.map { paletteColor($0.accentStart) ?? RealColors.accentBlue }) { withoutAnimation { showCategoryPicker = true } }
            ZStack(alignment: .topTrailing) {
                toolButton("plus", "Fotoğraf ekle", active: !attachments.isEmpty) { showPhotoPicker = true }
                if !attachments.isEmpty {
                    Text("\(attachments.count)").font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                        .frame(width: 14, height: 14).background(Circle().fill(Color(argb: 0xFF3B82F6))).offset(x: -4, y: 4)
                }
            }
            toolButton("checklist", "Checklist", active: checklistMode) {
                checklistMode.toggle()
                if checklistMode { checklistItems = [""]; focus = text.isEmpty ? .title : .item(0) }
            }
            if schedule.alertAt == nil {
                toolButton(sendImmediate ? "bell" : "bell.slash", "Bildirim", active: sendImmediate) { sendImmediate.toggle() }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14).padding(.top, 2).padding(.bottom, 4)
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
                              prompt: Text("Madde \(index + 1)").foregroundStyle(Color(argb: 0xFF6C6C76)))
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
                Label("Madde ekle", systemImage: "plus").font(RealFont.inter(13, .medium)).foregroundStyle(RealColors.accentBlue)
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

    private func startVoice() {
        focus = nil
        let base = text
        // A spoken command into an empty composer goes to the AI (signed in, with consent); it either creates the
        // cards, fills the composer for a last look (a clock to pick), or leaves the transcript as typed text.
        let interpret: ((String, @escaping () -> Void) -> Void)? = base.isEmpty && RealAuth.shared.account != nil ? { spoken, done in
            Task { @MainActor in
                switch await VoiceAI.interpret(spoken, store: store) {
                case .created:
                    reset()
                    focus = nil
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                case .draft(let title, let draft, let category, let items):
                    text = title
                    schedule = draft
                    if let category { categoryId = category }
                    if !items.isEmpty { checklistMode = true; checklistItems = items }
                case .none:
                    break
                }
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
        store.addReminder(text: title, alertAt: draft.alertAt, plannedDay: draft.plannedDay, repeatRule: draft.repeatRule,
                          categoryId: categoryId, isImportant: important, checklist: items) { item in
            draft.apply(to: &item)
            item.attachments = files
            item.sendImmediateNotification = notify
        }
        if draft.alertAt != nil { NotificationScheduler.requestAuthorization() }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        reset()
        focus = nil
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
                    Text("Kategori Seç").font(RealFont.inter(20, .semibold)).foregroundStyle(.white)
                    Spacer()
                    Button(action: close) {
                        RealSymbol("xmark", size: 17.2).foregroundStyle(Color(argb: 0xFFB8B8BE)).frame(width: 40, height: 40)
                    }
                    .accessibilityLabel("Kapat")
                }
                .padding(.bottom, 10)
                ScrollView {
                    VStack(spacing: 4) {
                        row(isSelected: selected == nil, highlight: selected == nil) {
                            RealSymbol("xmark", size: 13.8).foregroundStyle(Color(argb: 0xFFC8C8D0))
                                .frame(width: 34, height: 34).background(Circle().fill(Color(argb: 0xFF2A2A30)))
                        } title: { Text("Kategori yok").font(RealFont.inter(17, .semibold)) } action: { selected = nil; close() }
                        row(isSelected: false, highlight: false) {
                            RealSymbol("plus", size: 14.9).foregroundStyle(RealColors.accentBlue)
                                .frame(width: 34, height: 34).background(Circle().fill(RealColors.accentBlue.opacity(0.14)))
                                .overlay(Circle().strokeBorder(RealColors.accentBlue.opacity(0.35), lineWidth: 0.8))
                        } title: { Text("Yeni kategori ekle...").font(RealFont.inter(17)) } action: { showNew = true }
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
