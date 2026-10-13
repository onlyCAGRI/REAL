import SwiftUI
import UIKit
import PhotosUI
import MapKit
import UniformTypeIdentifiers

/// Card detail (ReminderDetailScreen.kt). View mode: the card itself as the top block (text, divider,
/// status / schedule / category), the content blocks under it in their saved order, and the glass bottom bar
/// (Tamamla · Düzenle · Share · Sil). Edit mode: everything edits a draft; the edit dock adds blocks one by one
/// (Metin, Tarih, Konum, Checklist, Fotoğraf, Kamera, Dosya, Kart Yığını, Mood) and "Renk ve kategori" opens
/// the colour picker + category row. Kaydet commits the draft, Vazgeç drops it.
struct ReminderDetailView: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let reminderId: Int64

    @State private var draft: ReminderItem?
    @State private var secondaryExpanded = false
    @State private var dockCollapsed = false
    @State private var showMusicSearch = false
    @State private var voiceRecorderOpen = false
    @State private var textToolsKey: String?
    @State private var showMediaSearch = false
    @State private var deleting = false
    @State private var reordering = false
    @State private var newChecklistText = ""
    @State private var checklistOpen = false
    @State private var showDateSheet = false
    @State private var schedule = ScheduleDraft()
    @State private var showCategoryPicker = false
    @State private var categorySelection: Int64?
    @State private var showPhotoPicker = false
    @State private var photoItems: [PhotosPickerItem] = []
    @State private var showCamera = false
    @State private var showFileImporter = false
    @State private var showLocationEditor = false
    @State private var showLocationGuide = false
    @State private var showLinkEditor = false
    @State private var editingStackCard: CardStackCard?
    @State private var showMoodPicker = false
    @State private var previewPhoto: ReminderAttachment?
    @FocusState private var titleFocused: Bool

    private var stored: ReminderItem? { store.reminder(reminderId) }
    private var editing: Bool { draft != nil }
    /// What the screen shows: the draft while editing, the stored card otherwise.
    private var shown: ReminderItem? { draft ?? stored }

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.ignoresSafeArea()
            if let r = shown {
                ScrollView {
                    VStack(spacing: 12) {
                        if !editing { header(r) } else { Color.clear.frame(height: 4) }
                        CardBlockGrid(keys: blockOrder(r), layout: r.contentBlockLayout, editing: editing,
                                      onChange: { key, p in draft?.contentBlockLayout[key] = p },
                                      onDrop: { keys, layout in draft?.contentBlockOrder = keys; draft?.contentBlockLayout = layout }) { key in
                            block(key, r).transition(.opacity.combined(with: .scale(scale: 0.97)))
                        }
                        Text(createdLine(r))
                            .font(RealFont.inter(14))
                            .foregroundStyle(Color(argb: 0xFF8C8C95))
                            .frame(maxWidth: .infinity, alignment: .trailing)
                            .padding(.top, 4).padding(.trailing, 6)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                    .padding(.bottom, editing ? (secondaryExpanded ? 520 : 300) : 130)
                    .animation(.spring(duration: 0.3), value: blockOrder(r))
                }
                .scrollDismissesKeyboard(.interactively)
                bottomColumn(r)
            } else {
                Color.clear.onAppear { dismiss() }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .realOverlay(isPresented: $showDateSheet) { ScheduleSheet(draft: $schedule) }
        .onChange(of: schedule) { _, new in
            if editing { new.apply(to: &draft!) } else { store.mutate(reminderId) { new.apply(to: &$0) } }
        }
        .realOverlay(isPresented: $showCategoryPicker) { CategoryPickerSheet(selected: $categorySelection) }
        .onChange(of: categorySelection) { _, new in draft?.customCategoryId = new }
        .photosPicker(isPresented: $showPhotoPicker, selection: $photoItems, maxSelectionCount: 10, matching: .images)
        .onChange(of: photoItems) { _, items in importPhotos(items) }
        .fullScreenCover(isPresented: $showCamera) { CameraPicker { data in addAttachment(AttachmentStorage.save(data)) }.ignoresSafeArea() }
        .fileImporter(isPresented: $showFileImporter, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
            if case .success(let urls) = result { urls.forEach { addAttachment(AttachmentStorage.saveFile($0)) } }
        }
        .realOverlay(isPresented: $showLocationGuide) {
            // LocationGuideSheet: Android hands off to Google Maps → Share → REAL; iOS searches in-app (a share
            // extension would be needed for the Maps hand-off).
            PermissionSheet(title: "Konum ekle", description: "Haritada istediğin yeri ara ve seç. Seçtiğin konum karta eklenir.",
                            primaryLabel: "Haritada ara", secondaryLabel: "Vazgeç", glyph: .pin,
                            onPrimary: { withoutAnimation { showLocationGuide = false }; DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { showLocationEditor = true } },
                            onSecondary: { withoutAnimation { showLocationGuide = false } })
        }
        .sheet(isPresented: $showLocationEditor) {
            LocationEditor(initial: draft?.location) { draft?.location = $0 }.presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showLinkEditor) {
            LinkEditor(initial: draft?.referenceUrl ?? "") { draft?.referenceUrl = $0 }.presentationDetents([.height(220)])
        }
        .sheet(item: $editingStackCard) { card in
            CardStackEditor(card: card, isNew: shown?.cardStack?.cards.contains { $0.id == card.id } != true) { updated in
                updateStack { stack in
                    if let updated {
                        if let i = stack.cards.firstIndex(where: { $0.id == updated.id }) { stack.cards[i] = updated } else { stack.cards.append(updated) }
                    } else {
                        stack.cards.removeAll { $0.id == card.id }
                    }
                }
            }
        }
        .sheet(item: Binding(get: { textToolsKey.map(IdentifiedKey.init) }, set: { textToolsKey = $0?.id })) { k in
            CardTextTools(placement: (draft?.contentBlockLayout[k.id] ?? CardBlockPlacement()).normalized) { p in
                draft?.contentBlockLayout[k.id] = p
            }
            .presentationDetents([.height(230)])
            .presentationBackground(.clear)
        }
        .sheet(isPresented: $showMusicSearch) {
            MusicSearchSheet { m in withAnimation(.spring(duration: 0.3)) { draft?.musicBlocks.append(m) } }
        }
        .sheet(isPresented: $showMediaSearch) {
            MediaSearchSheet { m in withAnimation(.spring(duration: 0.3)) { draft?.mediaBlocks.append(m) } }
        }
        .fullScreenCover(isPresented: $showMoodPicker) {
            MoodAddSheet(onSaved: { mood, _ in
                if editing { draft?.mood = mood } else { store.mutate(reminderId) { $0.mood = mood } }
            })
        }
        .sheet(isPresented: $reordering) {
            if let r = shown { BlockReorderSheet(keys: blockOrder(r), item: r) { draft?.contentBlockOrder = $0 } }
        }
        .fullScreenCover(item: $previewPhoto) { a in PhotoPreview(attachment: a) }
    }

    /// "Oluşturuldu · Pazartesi • 12 Eki 2026, 18:21"
    private func createdLine(_ r: ReminderItem) -> String {
        let cal = Calendar.real
        let d = r.createdAt.date
        let c = cal.dateComponents([.year, .month, .day], from: d)
        let months = ["Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"]
        return "Oluşturuldu · \(cal.isoWeekday(of: d).full) • \(c.day ?? 1) \(months[(c.month ?? 1) - 1]) \(c.year ?? 2026), \(RealDates.time(d))"
    }

    // MARK: Block order

    private func blockOrder(_ r: ReminderItem) -> [String] {
        let active = r.cardContentKeys(includeEmptyChecklist: editing && checklistOpen, includeVoiceRecorder: editing && voiceRecorderOpen, includeJournal: false)
        return r.resolvedBlockOrder(active)
    }

    @ViewBuilder
    private func block(_ key: String, _ r: ReminderItem) -> some View {
        let kind = key.split(separator: ":").first.map(String.init) ?? key
        let id = key.contains(":") ? String(key.split(separator: ":", maxSplits: 1)[1]) : ""
        switch kind {
        case "text": textBlock(r)
        case "card_stack": if let stack = r.cardStack { CardStackDeck(stack: stack, editing: editing, onEdit: { editingStackCard = $0 },
                                                                       onAdd: { editingStackCard = CardStackCard() }) }
        case "mood": if let mood = r.mood { moodBlock(mood) }
        case "checklist": checklistBlock(r)
        case "photos": if let a = r.attachments.first(where: { $0.id == id }) { photoBlock(a) }
        case "files":
            if id == "reference", let url = r.referenceUrl { linkBlock(url) }
            else if let a = r.attachments.first(where: { $0.id == id }) {
                if a.sourceTitle == "REAL_TEXT_BLOCK" { textBoxBlock(a, r) } else { fileBlock(a) }
            }
        case "location": if let l = r.location { locationBlock(l) }
        case "journal": if let j = r.journal { journalBlock(j) }
        case "voice":
            VoiceRecordingBlock(recordings: r.voiceRecordings, editing: editing,
                                onRecorded: { rec in draft?.voiceRecordings.append(rec) },
                                onRemove: { rec in draft?.voiceRecordings.removeAll { $0.id == rec.id } },
                                onClose: { voiceRecorderOpen = false; draft?.voiceRecordings = [] })
        case "music":
            if let m = r.musicBlocks.first(where: { $0.id == id }) {
                MusicBlockView(block: m, editing: editing) { draft?.musicBlocks.removeAll { $0.id == id } }
            }
        case "media":
            if let m = r.mediaBlocks.first(where: { $0.id == id }) {
                MediaBlockView(block: m, editing: editing) { draft?.mediaBlocks.removeAll { $0.id == id } }
            }
        default: EmptyView()
        }
    }

    // MARK: Header

    private func header(_ r: ReminderItem) -> some View {
        HStack {
            Button { dismiss() } label: {
                RealSymbol("chevron.left", size: 20.7).foregroundStyle(.white)
                    .frame(width: 40, height: 40).liquidGlass(Circle())
                    .frame(width: 48, height: 48)
            }
            .accessibilityLabel("Geri")
            Spacer()
            HStack(spacing: 0) {
                // Now Bar has no iOS counterpart yet; shown disabled like Android when unavailable.
                ActionIcon(.nowBar, color: Color(argb: 0xFF66666F), size: 20)
                    .frame(width: 48, height: 40)
                    .accessibilityLabel("Now Bar kullanılamıyor")
                Rectangle().fill(.white.opacity(0.13)).frame(width: 1, height: 18)
                Button {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    store.mutate(reminderId) { $0.isImportant.toggle() }
                } label: {
                    ActionIcon(.star, color: r.isImportant ? Color(argb: 0xFFEACB75) : Color(argb: 0xFFC9CBD3), filled: r.isImportant, size: 21)
                        .frame(width: 48, height: 40)
                        .background(r.isImportant ? Color(argb: 0xFFEACB75).opacity(0.08) : .clear)
                        .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.16), value: r.isImportant)
                }
                .accessibilityLabel(r.isImportant ? "Önemli işaretini kaldır" : "Önemli olarak işaretle")
            }
            .frame(width: 97, height: 40)
            .liquidGlass(RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }

    // MARK: Text block (the card itself)

    private func textBlock(_ r: ReminderItem) -> some View {
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
        let accent = reminderAccentGradient(start: r.accentStart, end: r.accentEnd, completed: r.isCompleted,
                                            blend: r.gradientBlendFraction, solidArgb: r.customSolidColorArgb)
        let dark = r.useDarkText
        let struck = r.isCompleted && r.journal == nil
        let ink: Color = dark ? (struck ? Color(argb: 0xFF55555A) : Color(argb: 0xFF101010)) : (struck ? Color(argb: 0xFF9999A2) : Color(argb: 0xFFF4F4F6))
        return VStack(alignment: .leading, spacing: 0) {
            if editing {
                TextField("Hatırlatıcı metni", text: Binding(get: { draft?.text ?? "" }, set: { draft?.text = $0 }), axis: .vertical)
                    .font(RealFont.inter(21))
                    .lineSpacing(10)
                    .foregroundStyle(dark ? Color(argb: 0xFF101010) : .white)
                    .tint(dark ? Color(argb: 0xFF101010) : Color(argb: 0xFF82B3FF))
                    .focused($titleFocused)
                    .frame(minHeight: 96, alignment: .topLeading)
                    .onChange(of: titleFocused) { _, f in if f { secondaryExpanded = false } }
                if !r.isCompleted, r.alertAt != nil { scheduleChips(r) }
            } else {
                Text(r.text.isEmpty ? "Boş not" : r.text)
                    .font(RealFont.inter(21))
                    .lineSpacing(10)
                    .foregroundStyle(ink)
                    .strikethrough(struck, color: ink)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Rectangle().fill(dark ? Color(argb: 0xFF101010).opacity(0.2) : Color(argb: 0xFF38383F)).frame(height: 1)
                    .padding(.top, 24).padding(.bottom, 18)
                statusArea(r)
            }
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 25)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            if let accent { shape.fill(accent) }
            else { shape.fill(LinearGradient(colors: [Color(argb: 0xFF18181A), LiquidGlassTokens.contentColor], startPoint: .top, endPoint: .bottom)) }
        }
        .overlay { if r.isImportant { shape.strokeBorder(Color.white.opacity(0.85), lineWidth: 1.25) } }
    }

    /// Status line under the divider: "Planlı • Bugün, 10:00", duration / end, repeat summary, category.
    @ViewBuilder
    private func statusArea(_ r: ReminderItem) -> some View {
        let category = store.category(r.customCategoryId)
        let standaloneCategory = !r.isCompleted && r.alertAt == nil && r.plannedDateEpochDay == nil && !r.isQuick24HourActive() && category != nil
        if standaloneCategory, let category {
            HStack(spacing: 10) {
                categoryBadge(category, size: 32)
                Text(category.name).font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFFE2E2E8))
            }
        } else {
            HStack(alignment: .top, spacing: 12) {
                ActionIcon(r.isCompleted ? .check : .note, size: 20)
                    .foregroundStyle(RealColors.accentBlue)
                    .padding(.top, 2)
                VStack(alignment: .leading, spacing: 3) {
                    let status = statusText(r, hasCategory: category != nil)
                    if let status {
                        if r.isCompleted {
                            Text(status).font(RealFont.inter(14, .medium)).foregroundStyle(Color(argb: 0xFFD3D3D9))
                        } else if let alert = r.alertAt {
                            HStack(spacing: 6) {
                                Text(status).font(RealFont.inter(14, .medium)).foregroundStyle(Color(argb: 0xFFD3D3D9))
                                Text("•").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF6B6B75))
                                Text(RealDates.reminder(alert)).font(RealFont.inter(14, .medium))
                                    .foregroundStyle(alert < EpochMillis.now ? RealColors.overdue : RealColors.accentBlue)
                            }
                            if let dur = r.durationMinutes {
                                HStack(spacing: 6) {
                                    Text("Süre: \(dur) dk")
                                    Text("•").foregroundStyle(Color(argb: 0xFF6B6B75))
                                    Text("Bitiş: \(RealDates.time((alert + Int64(dur) * 60_000).date))")
                                }
                                .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA5A5B0)).padding(.top, 2)
                            }
                            if let repeatText = repeatSummary(r) {
                                Text(repeatText).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA5A5B0)).padding(.top, 1)
                            }
                        } else if let day = r.plannedDateEpochDay {
                            HStack(spacing: 6) {
                                Text(status).font(RealFont.inter(14, .medium)).foregroundStyle(Color(argb: 0xFFD3D3D9))
                                Text("•").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF6B6B75))
                                Text(RealDates.planned(day)).font(RealFont.inter(14, .medium)).foregroundStyle(RealColors.accentBlue)
                            }
                        } else {
                            Text(status).font(RealFont.inter(14, .medium)).foregroundStyle(Color(argb: 0xFFD3D3D9))
                        }
                    }
                    if let category {
                        HStack(spacing: 6) {
                            categoryBadge(category, size: 16)
                            Text(category.name).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFC4C4CE))
                        }
                        .padding(.top, 1)
                    }
                }
            }
        }
    }

    private func statusText(_ r: ReminderItem, hasCategory: Bool) -> String? {
        if r.isCompleted { return "Tamamlandı" }
        if r.isQuick24HourActive() { return "24 hour" }
        if r.alertAt == nil && r.plannedDateEpochDay == nil { return hasCategory ? nil : "Uyarı yok" }
        return "Planlı"
    }

    /// RecurrenceRule.repeatSummary ("Her Pazartesi tekrarlanacak").
    private func repeatSummary(_ r: ReminderItem) -> String? {
        guard r.repeatRule != .none, let base = r.anchorAlertAt ?? r.alertAt else { return nil }
        if let pattern = Recurrence.patternLabel(r) { return pattern }
        let cal = Calendar.real
        let d = base.date
        switch r.repeatRule {
        case .daily: return "Her gün tekrarlanacak"
        case .weekly: return "Her \(cal.isoWeekday(of: d).full) tekrarlanacak"
        case .monthly:
            let day = cal.component(.day, from: d)
            let suffix: String
            switch day {
            case 2, 7, 12, 17, 20, 22, 27: suffix = "'si"
            case 3, 4, 13, 14, 23, 24: suffix = "'ü"
            case 9, 10, 19, 29, 30: suffix = "'u"
            case 6, 16, 26: suffix = "'sı"
            default: suffix = "'i"
            }
            return "Her ayın \(day)\(suffix) tekrarlanacak"
        case .yearly: return "Her yıl \(cal.component(.day, from: d)) \(turkishMonths[cal.component(.month, from: d) - 1]) tekrarlanacak"
        case .none: return nil
        }
    }

    private func categoryBadge(_ c: CustomCategory, size: CGFloat) -> some View {
        let start = paletteColor(c.accentStart) ?? Color(argb: 0xFF233F73)
        let end = paletteColor(c.accentEnd) ?? start
        return RealSymbol(c.icon.symbol, size: size * 0.55)
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(Circle().fill(LinearGradient(colors: [start, end], startPoint: .topLeading, endPoint: .bottomTrailing)))
    }

    /// Edit-mode schedule chips under the text: date, repeat, duration, end, clear.
    private func scheduleChips(_ r: ReminderItem) -> some View {
        HStack(spacing: 6) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    chip("calendar", RealDates.reminder(r.alertAt!), selected: true) { openSchedule(r) }
                    if r.hasRepeat {
                        chip("repeat", Recurrence.description(r.repeatRule, alert: r.anchorAlertAt ?? r.alertAt), selected: false) { openSchedule(r) }
                    }
                    chip("note.text", r.durationMinutes.map { "Süre: \($0) min" } ?? "Süre ekle", selected: false) {
                        let options = [nil, 15, 30, 45, 60, 90, 120]
                        let i = options.firstIndex(of: r.durationMinutes) ?? 0
                        draft?.durationMinutes = options[(i + 1) % options.count]
                    }
                    let end = r.durationMinutes.map { RealDates.time((r.alertAt! + Int64($0) * 60_000).date) } ?? "—"
                    chip("calendar", "Bitiş: \(end)", selected: false) { openSchedule(r) }
                }
            }
            Button {
                draft?.alertAt = nil
                draft?.anchorAlertAt = nil
                draft?.repeatRule = .none
                draft?.durationMinutes = nil
            } label: { RealSymbol("xmark", size: 16.1).foregroundStyle(.white).frame(width: 40, height: 40) }
            .accessibilityLabel("Tarih ve saati kaldır")
        }
        .padding(.top, 12)
    }

    private func chip(_ symbol: String, _ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 5) {
                RealSymbol(symbol, size: 20).foregroundStyle(RealColors.accentBlue)
                Text(label).font(RealFont.inter(selected ? 13 : 12, .medium)).foregroundStyle(selected ? Color(argb: 0xFFE2E8F0) : Color(argb: 0xFFD2D2DA))
            }
            .padding(.horizontal, selected ? 10 : 9).padding(.vertical, 7)
            .liquidGlass(RoundedRectangle(cornerRadius: 12, style: .continuous), role: .chrome)
        }
    }

    private func openSchedule(_ r: ReminderItem) {
        schedule = ScheduleDraft(from: r)
        withoutAnimation { showDateSheet = true }
    }

    // MARK: Content blocks

    private func checklistBlock(_ r: ReminderItem) -> some View {
        let items = r.checklistItems
        let done = items.filter(\.isCompleted).count
        let summary = items.isEmpty ? "" : (done > 0 ? "\(items.count) madde • \(done) tamamlandı" : "\(items.count) madde")
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("Checklist").font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFB7B7C0))
                if !summary.isEmpty { Text("  •  \(summary)").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA5A5B5)).lineLimit(1) }
                Spacer()
                if editing {
                    Button {
                        draft?.checklistItems = []
                        draft?.isChecklist = false
                        checklistOpen = false
                    } label: { RealSymbol("xmark", size: 13.8).foregroundStyle(Color(argb: 0xFF8E8E98)).frame(width: 28, height: 28) }
                    .accessibilityLabel("Checklist kutusunu kaldır")
                }
            }
            .padding(.horizontal, 4).padding(.vertical, 2)
            ForEach(items) { item in
                HStack(spacing: 12) {
                    Button {
                        UISelectionFeedbackGenerator().selectionChanged()
                        if editing {
                            draft?.checklistItems = items.map { $0.id == item.id ? $0.toggled() : $0 }
                        } else {
                            withAnimation(.easeOut(duration: 0.18)) { store.toggleChecklistItem(reminderId, itemId: item.id) }
                        }
                    } label: {
                        ZStack {
                            Circle().fill(item.isCompleted ? Color(argb: 0xFF686873) : .clear)
                            Circle().strokeBorder(item.isCompleted ? .clear : Color(argb: 0xFF5E5E68), lineWidth: 1.5)
                            if item.isCompleted { RealSymbol("checkmark", size: 11.5).foregroundStyle(Color(argb: 0xFF1B1B1F)) }
                        }
                        .frame(width: 20, height: 20)
                        .frame(width: 32, height: 32)
                    }
                    if editing {
                        TextField("", text: Binding(get: { item.text }, set: { v in
                            draft?.checklistItems = (draft?.checklistItems ?? []).map { $0.id == item.id ? ChecklistItem(id: $0.id, text: v, completedAt: $0.completedAt) : $0 }
                        }))
                        .font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFF1F1F5))
                        Button { draft?.checklistItems.removeAll { $0.id == item.id } } label: {
                            RealSymbol("xmark", size: 12.6).foregroundStyle(Color(argb: 0xFF7A7A85)).frame(width: 28, height: 28)
                        }
                    } else {
                        Text(item.text).font(RealFont.inter(15))
                            .foregroundStyle(item.isCompleted ? Color(argb: 0xFF6E6E77) : Color(argb: 0xFFF1F1F5))
                            .strikethrough(item.isCompleted, color: Color(argb: 0xFF6E6E77))
                            .opacity(item.isCompleted ? 0.65 : 1)
                        Spacer(minLength: 0)
                    }
                }
            }
            if editing {
                HStack(spacing: 12) {
                    RealSymbol("plus", size: 14.9).foregroundStyle(RealColors.accentBlue).frame(width: 32, height: 32)
                    TextField("", text: $newChecklistText, prompt: Text("Madde ekle").foregroundStyle(Color(argb: 0xFF6C6C76)))
                        .font(RealFont.inter(15))
                        .submitLabel(.next)
                        .onSubmit {
                            let t = newChecklistText.trimmingCharacters(in: .whitespaces)
                            guard !t.isEmpty else { return }
                            let next = max(EpochMillis.now, (draft?.checklistItems.map(\.id).max() ?? 0) + 1)
                            draft?.checklistItems.append(ChecklistItem(id: next, text: t))
                            draft?.isChecklist = true
                            newChecklistText = ""
                        }
                }
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardContentChrome(corners: 22)
    }

    private func photoBlock(_ a: ReminderAttachment) -> some View {
        PhotoBlockBody(attachment: a, editing: editing, onTap: { previewPhoto = a }, remove: { removeAttachment(a) })
    }

    private func legacyPhotoBlock(_ a: ReminderAttachment) -> some View {
        let image = AttachmentStorage.image(a.localPath)
        let ratio = image.map { max(0.5, min(2, $0.size.width / max($0.size.height, 1))) } ?? 1
        return ZStack(alignment: .topTrailing) {
            Group {
                if let image { Image(uiImage: image).resizable().scaledToFill() } else { RealColors.field }
            }
            .frame(maxWidth: .infinity)
            .aspectRatio(ratio, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .contentShape(Rectangle())
            .onTapGesture { previewPhoto = a }
            if editing { removeButton { removeAttachment(a) } }
        }
    }

    private func fileBlock(_ a: ReminderAttachment) -> some View {
        HStack(spacing: 12) {
            RealSymbol("doc", size: 20.7).foregroundStyle(RealColors.accentBlue)
                .frame(width: 40, height: 40).background(RoundedRectangle(cornerRadius: 12).fill(.white.opacity(0.06)))
            VStack(alignment: .leading, spacing: 2) {
                Text(a.displayName ?? "Dosya").font(RealFont.inter(14, .medium)).foregroundStyle(.white).lineLimit(1)
                if let size = a.sizeBytes { Text(ByteCountFormatter.string(fromByteCount: size, countStyle: .file)).font(RealFont.inter(12)).foregroundStyle(RealColors.subtext) }
            }
            Spacer()
            if editing { removeButton { removeAttachment(a) } } else {
                ShareLink(item: AttachmentStorage.directory.appendingPathComponent(a.localPath)) {
                    ActionIcon(.share, color: RealColors.subtext, size: 18)
                }
            }
        }
        .padding(14)
        .cardContentChrome(corners: 22)
    }

    private func linkBlock(_ url: String) -> some View {
        Button {
            if !editing, let u = URL(string: url.contains("://") ? url : "https://\(url)") { openURL(u) } else if editing { showLinkEditor = true }
        } label: {
            HStack(spacing: 12) {
                RealSymbol("link", size: 19.5).foregroundStyle(RealColors.accentBlue)
                    .frame(width: 40, height: 40).background(RoundedRectangle(cornerRadius: 12).fill(.white.opacity(0.06)))
                VStack(alignment: .leading, spacing: 2) {
                    Text(URL(string: url)?.host?.replacingOccurrences(of: "www.", with: "") ?? url).font(RealFont.inter(14, .medium)).foregroundStyle(.white).lineLimit(1)
                    Text(url).font(RealFont.inter(12)).foregroundStyle(RealColors.subtext).lineLimit(1)
                }
                Spacer()
                if editing { removeButton { draft?.referenceUrl = nil } } else {
                    RealSymbol("arrow.up.right", size: 14.9).foregroundStyle(RealColors.subtext)
                }
            }
            .padding(14)
            .cardContentChrome(corners: 22)
        }
        .buttonStyle(.plain)
    }

    /// CardStandaloneTextBox: a free text box that carries the card colour.
    /// CardStandaloneTextBox: 28pt surface, 21/31 text, "Metin yaz…", detached gear/× chip and the "Metin biçimi" tools.
    private func textBoxBlock(_ a: ReminderAttachment, _ r: ReminderItem) -> some View {
        let key = "files:\(a.id)"
        let placement = (r.contentBlockLayout[key] ?? CardBlockPlacement()).normalized
        let accent = reminderAccentGradient(start: r.accentStart, end: r.accentEnd, completed: false, blend: r.gradientBlendFraction, solidArgb: r.customSolidColorArgb)
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
        let compact = (1..<200).contains(placement.minHeight)
        let ink = r.useDarkText ? Color(argb: 0xFF101010) : Color(argb: 0xFFF4F4F6)
        let align: TextAlignment = placement.textAlignment == "center" ? .center : (placement.textAlignment == "end" ? .trailing : .leading)
        let frameAlign: Alignment = placement.textAlignment == "center" ? .top : (placement.textAlignment == "end" ? .topTrailing : .topLeading)
        let font = RealFont.inter(21, placement.textBold ? .bold : .regular)
        return ZStack(alignment: .topTrailing) {
            Group {
                if editing {
                    TextField("", text: Binding(get: { a.text ?? "" }, set: { v in
                        draft?.attachments = (draft?.attachments ?? []).map { item in
                            var copy = item
                            if item.id == a.id { copy.text = v }
                            return copy
                        }
                    }), prompt: Text("Metin yaz…").foregroundStyle(ink.opacity(0.4)), axis: .vertical)
                    .tint(ink)
                    .frame(minHeight: placement.minHeight > 0 ? 0 : 96, alignment: .topLeading)
                } else {
                    Text(a.text ?? "").textSelection(.enabled)
                }
            }
            .font(font).italic(placement.textItalic).lineSpacing(10)
            .multilineTextAlignment(align)
            .foregroundStyle(ink)
            .frame(maxWidth: .infinity, alignment: frameAlign)
            .padding(.horizontal, 22).padding(.vertical, compact ? 8 : 25)
            .background {
                if let accent { shape.fill(accent) }
                else { shape.fill(LinearGradient(colors: [Color(argb: 0xFF18181A), LiquidGlassTokens.contentColor], startPoint: .top, endPoint: .bottom)) }
            }
            .clipShape(shape)
            if editing {
                HStack(spacing: 0) {
                    Button { textToolsKey = key } label: { ActionIcon(.settings, color: Color(argb: 0xFFB7B7C0), size: 15).frame(width: 28, height: 28) }
                        .accessibilityLabel("Metin araçları")
                    Button { removeAttachment(a) } label: { ActionIcon(.close, color: Color(argb: 0xFFB7B7C0), size: 15).frame(width: 28, height: 28) }
                        .accessibilityLabel("Metin kutusunu kaldır")
                }
                .buttonStyle(.plain)
                .liquidGlass(RoundedRectangle(cornerRadius: 14, style: .continuous), role: .chrome)
                .offset(y: -32)
            }
        }
    }

    private func locationBlock(_ l: ReminderLocation) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if let lat = l.latitude, let lon = l.longitude {
                let coord = CLLocationCoordinate2D(latitude: lat, longitude: lon)
                Map(initialPosition: .region(MKCoordinateRegion(center: coord, latitudinalMeters: 900, longitudinalMeters: 900)), interactionModes: []) {
                    Marker(l.placeName, coordinate: coord)
                }
                .environment(\.colorScheme, .dark)
                .frame(height: 150)
                .allowsHitTesting(false)
            }
            HStack(spacing: 12) {
                RealSymbol("mappin.and.ellipse", size: 18.4).foregroundStyle(Color(argb: 0xFF8ED7C4))
                VStack(alignment: .leading, spacing: 2) {
                    Text(l.placeName).font(RealFont.inter(15, .medium)).foregroundStyle(.white).lineLimit(1)
                    if !l.address.isEmpty { Text(l.address).font(RealFont.inter(12)).foregroundStyle(RealColors.subtext).lineLimit(2) }
                }
                Spacer()
                if editing {
                    removeButton { draft?.location = nil }
                } else {
                    Button("Haritada aç") { openInMaps(l) }.font(RealFont.inter(13, .medium)).foregroundStyle(RealColors.accentBlue)
                }
            }
            .padding(14)
        }
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .cardContentChrome(corners: 22)
        .onTapGesture { if editing { showLocationEditor = true } }
    }

    private func journalBlock(_ j: ReminderJournal) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Günlük").font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFB7B7C0))
                Spacer()
                if let mood = j.mood { Text("\(mood.emoji) \(mood.label)").font(RealFont.inter(12)).foregroundStyle(RealColors.subtext) }
            }
            if editing {
                TextField("Günlük notu", text: Binding(get: { draft?.journal?.text ?? "" }, set: { draft?.journal?.text = $0; draft?.journal?.updatedAt = .now }), axis: .vertical)
                    .font(RealFont.inter(15)).lineLimit(2...12)
            } else {
                Text(j.text.isEmpty ? "Günlük notu" : j.text).font(RealFont.inter(15)).foregroundStyle(j.text.isEmpty ? RealColors.textMuted : Color(argb: 0xFFE9E9EF))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            Text(RealDates.reminder(j.createdAt)).font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFF6F6F78))
        }
        .padding(18)
        .cardContentChrome(corners: 22)
    }

    private func moodBlock(_ mood: JournalMood) -> some View {
        let tint = MoodV2.tint(mood)
        return HStack(spacing: 14) {
            MoodFace(mood: mood, tint: tint).frame(width: 72, height: 72)
            VStack(alignment: .leading, spacing: 2) {
                Text("Mood").font(RealFont.inter(12)).foregroundStyle(tint)
                Text(MoodV2.families[MoodV2.familyIndex(mood)].title).font(RealFont.inter(21, .semibold)).foregroundStyle(Color(argb: 0xFFF7F7F9))
            }
            Spacer()
            if editing { removeButton { draft?.mood = nil } }
        }
        .padding(20)
        .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(LinearGradient(colors: [tint.opacity(0.30), Color(argb: 0xFF121214)], startPoint: .top, endPoint: .bottom)))
        .onTapGesture { if editing { showMoodPicker = true } }
    }

    private func moodTint(_ mood: JournalMood) -> Color {
        switch mood {
        case .veryGood, .happy, .excited: return Color(argb: 0xFFF2C94C)
        case .good, .calm: return Color(argb: 0xFF8ED9B6)
        case .neutral, .bored: return Color(argb: 0xFF9AA8FF)
        case .tired, .low: return Color(argb: 0xFFB4A7D6)
        case .hard, .stressed, .sad: return Color(argb: 0xFF78A9F2)
        case .bad, .angry, .fuckOff: return Color(argb: 0xFFEE6E5E)
        }
    }

    private func removeButton(_ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            RealSymbol("xmark", size: 11.5).foregroundStyle(.white)
                .frame(width: 26, height: 26).background(Circle().fill(Color(argb: 0xE61E1E24)))
                .overlay(Circle().strokeBorder(.white.opacity(0.3), lineWidth: 0.5))
        }
        .padding(8)
        .accessibilityLabel("Kaldır")
    }

    // MARK: Bottom column

    private func bottomColumn(_ r: ReminderItem) -> some View {
        VStack(spacing: 0) {
            if deleting { deletePrompt(r).padding(.bottom, 12).transition(.move(edge: .bottom).combined(with: .opacity)) }
            if editing {
                if secondaryExpanded { secondaryPanels.padding(.bottom, 12).transition(.opacity.combined(with: .scale(scale: 0.97))) }
                VStack(spacing: 0) {
                    // DetailEditDock handle: drag down (>24pt) or tap to collapse, drag up to reopen.
                    Capsule().fill(.white.opacity(0.55)).frame(width: 40, height: 4)
                        .frame(maxWidth: .infinity).frame(height: 28).contentShape(Rectangle())
                        .onTapGesture { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.22)) { dockCollapsed.toggle() } }
                        .gesture(DragGesture(minimumDistance: 4).onEnded { v in
                            let next = v.translation.height >= 24 ? true : (v.translation.height <= -24 ? false : dockCollapsed)
                            if next != dockCollapsed { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.22)) { dockCollapsed = next } }
                        })
                        .accessibilityLabel("Düzenleme dock'u")
                        .accessibilityValue(dockCollapsed ? "Küçültülmüş" : "Açık")
                        .accessibilityAddTraits(.isButton)
                    if !dockCollapsed {
                        editDock.transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }
                .padding(.bottom, 10)
            } else if r.isCompleted && r.journal == nil && !(r.cardStack?.cards.contains { $0.title == "Nasıl geçti?" } ?? false) {
                Button(action: addCompletionJournal) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Günlük ekle").font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFFD3D3D9))
                            Text("Nasıl geçti? Aklında kalanı yaz.").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8E8E98))
                        }
                        Spacer()
                        RealSymbol("plus", size: 16.1).foregroundStyle(RealColors.accentBlue)
                    }
                    .padding(.horizontal, 20).padding(.vertical, 14)
                    .liquidGlass(RoundedRectangle(cornerRadius: 22, style: .continuous), role: .chrome)
                }
                .padding(.bottom, 12)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            actionBar(r)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 2) // Android: bar bottom ~36dp above the screen edge
        .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.24), value: editing)
        .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.24), value: secondaryExpanded)
        .animation(.easeOut(duration: 0.2), value: deleting)
    }

    private func actionBar(_ r: ReminderItem) -> some View {
        HStack(spacing: 0) {
            if editing {
                DetailAction(label: "Vazgeç", symbol: "xmark") { draft = nil; secondaryExpanded = false; checklistOpen = false }
                DetailAction(label: "Kaydet", symbol: "checkmark", color: Color(argb: 0xFF91B9FF),
                             enabled: !(draft?.text.trimmingCharacters(in: .whitespaces).isEmpty ?? true)) { saveDraft() }
            } else {
                DetailAction(label: r.isCompleted ? "Geri al" : "Tamamla", symbol: r.isCompleted ? "arrow.uturn.backward" : "checkmark", enabled: !deleting) {
                    if !r.isCompleted { UINotificationFeedbackGenerator().notificationOccurred(.success) }
                    withAnimation { store.toggleCompleted(reminderId) }
                }
                DetailAction(label: "Düzenle", symbol: "pencil", enabled: !deleting) {
                    draft = r
                    categorySelection = r.customCategoryId
                    checklistOpen = !r.checklistItems.isEmpty
                }
                ShareLink(item: r.shareText) {
                    VStack(spacing: 1) {
                        RealSymbol("square.and.arrow.up", size: 20.7)
                        Text("Share").font(RealFont.inter(10))
                    }
                    .foregroundStyle(Color(argb: 0xFFF0F0F4))
                    .frame(maxWidth: .infinity, minHeight: 56)
                }
                .disabled(deleting)
                DetailAction(label: "Sil", symbol: "trash") { deleting.toggle() }
            }
        }
        .padding(.horizontal, 6)
        .liquidGlass(RoundedRectangle(cornerRadius: 28, style: .continuous))
    }

    /// DeleteReminderPrompt: plain delete, or "only this / this and future" for a repeating card.
    private func deletePrompt(_ r: ReminderItem) -> some View {
        let occurrence = Recurrence.nextDeletableOccurrence(r, now: .now)
        let scoped = r.hasRepeat && occurrence != nil
        return VStack(alignment: .leading, spacing: 10) {
            Text(scoped ? "Tekrarlayan etkinlik silinsin mi?" : "Hatırlatıcı silinsin mi?")
                .font(RealFont.inter(16, .semibold)).foregroundStyle(Color(argb: 0xFFF5F5F8))
            Text(scoped ? "Yalnızca sıradaki tekrarı ya da sıradaki tekrardan itibaren tüm tekrarları silebilirsin. Önceki tekrarlar korunur. Bu işlem geri alınamaz."
                 : "Bu not listenden ve bildirim panelinden kaldırılacak. Bu işlem geri alınamaz.")
                .font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFB4B4BE))
            if scoped, let occurrence {
                Button("Sadece sıradaki tekrarı sil") { deleting = false; store.deleteOccurrence(reminderId, occurrence: occurrence) }
                    .font(RealFont.inter(14, .semibold)).foregroundStyle(Color(argb: 0xFFFFA4AA))
                Button("Bu ve sonraki tüm tekrarları sil") { deleting = false; store.deleteThisAndFuture(reminderId, occurrence: occurrence) }
                    .font(RealFont.inter(14, .semibold)).foregroundStyle(Color(argb: 0xFFFFA4AA))
                Button("Vazgeç") { deleting = false }.font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFFE9E9EF))
            } else {
                HStack {
                    Button("Vazgeç") { deleting = false }.font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFFE9E9EF))
                    Spacer()
                    Button("Sil") { deleting = false; store.delete([reminderId]) }.font(RealFont.inter(14, .semibold)).foregroundStyle(Color(argb: 0xFFFFA4AA))
                }
                .padding(.top, 4)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .liquidGlass(RoundedRectangle(cornerRadius: 26, style: .continuous), role: .sheet)
    }

    /// Edit dock: "Renk ve kategori" toggle + sort, then the one-by-one add buttons.
    private var editDock: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button { titleFocused = false; secondaryExpanded.toggle() } label: {
                    HStack(spacing: 6) {
                        Text("Renk ve kategori").font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFB7B7C0))
                        RealSymbol("chevron.right", size: 12.6).foregroundStyle(Color(argb: 0xFF8E8E98))
                            .rotationEffect(.degrees(secondaryExpanded ? 90 : 0))
                    }
                    .padding(.horizontal, 4).padding(.vertical, 8)
                }
                Spacer()
                Button { reordering = true } label: {
                    RealSymbol("arrow.up.arrow.down", size: 19.5).foregroundStyle(Color(argb: 0xFFD2D2DA)).frame(width: 40, height: 40)
                }
                .accessibilityLabel("Sırala")
            }
            .padding(.horizontal, 2)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 4) {
                    let d = draft
                    QuickAction(symbol: "text.alignleft", label: "Metin kutusu ekle", active: d?.attachments.contains { $0.sourceTitle == "REAL_TEXT_BLOCK" } ?? false) {
                        addAttachment(ReminderAttachment(id: UUID().uuidString, type: .textSnippet, text: "", sourceTitle: "REAL_TEXT_BLOCK"))
                    }
                    QuickAction(symbol: "calendar", label: "Tarih / Saat", active: d?.alertAt != nil || d?.plannedDateEpochDay != nil) {
                        if let d { openSchedule(d) }
                    }
                    QuickAction(symbol: "mappin", label: "Konum", active: d?.location != nil) { withoutAnimation { showLocationGuide = true } }
                    QuickAction(symbol: "checklist", label: "Checklist", active: checklistOpen || !(d?.checklistItems.isEmpty ?? true),
                                count: d?.checklistItems.count) {
                        checklistOpen = true
                        draft?.isChecklist = true
                    }
                    QuickAction(symbol: "photo", label: "Fotoğraf ekle", active: false,
                                count: d?.attachments.filter { $0.type == .image }.count) { showPhotoPicker = true }
                    QuickAction(symbol: "camera", label: "Kamera ile fotoğraf ekle", active: false) { showCamera = true }
                    QuickAction(symbol: "doc", label: "Dosya ekle", active: false,
                                count: d?.attachments.filter { $0.type == .file }.count) { showFileImporter = true }
                    QuickAction(symbol: "music.note", label: "Müzik", active: !(d?.musicBlocks.isEmpty ?? true),
                                count: d?.musicBlocks.count) { showMusicSearch = true }
                    QuickAction(symbol: "play.rectangle", label: "Film & Dizi", active: !(d?.mediaBlocks.isEmpty ?? true),
                                count: d?.mediaBlocks.count) { showMediaSearch = true }
                    QuickAction(symbol: "waveform", label: "Ses kaydı", active: voiceRecorderOpen || !(d?.voiceRecordings.isEmpty ?? true),
                                count: d?.voiceRecordings.count) { withAnimation(.spring(duration: 0.3)) { voiceRecorderOpen = true } }
                    QuickAction(symbol: "rectangle.stack", label: "Kart Yığını", active: d?.cardStack != nil, count: d?.cardStack?.cards.count) {
                        editingStackCard = CardStackCard()
                    }
                    QuickAction(symbol: "face.smiling", label: "Mood", active: d?.mood != nil) { showMoodPicker = true }
                }
                .padding(.leading, 2).padding(.trailing, 12)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .liquidGlass(RoundedRectangle(cornerRadius: 26, style: .continuous))
    }

    private var secondaryPanels: some View {
        VStack(spacing: 8) {
            CardColorsPanel(
                start: Binding(get: { draft?.accentStart }, set: { draft?.accentStart = $0; draft?.customSolidColorArgb = nil }),
                end: Binding(get: { draft?.accentEnd }, set: { draft?.accentEnd = $0; draft?.customSolidColorArgb = nil }),
                blend: Binding(get: { draft?.gradientBlendResolved ?? 0.5 }, set: { draft?.gradientBlendFraction = $0 }),
                darkText: Binding(get: { draft?.useDarkText ?? false }, set: { draft?.useDarkText = $0 }),
                isStarred: draft?.isImportant ?? false)
                .liquidGlass(RoundedRectangle(cornerRadius: 28, style: .continuous), role: .sheet)
            Button { titleFocused = false; withoutAnimation { showCategoryPicker = true } } label: {
                HStack {
                    Text("Kategori").font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFB7B7C0))
                    Spacer()
                    if let c = store.category(draft?.customCategoryId) {
                        categoryBadge(c, size: 18)
                        Text(c.name).font(RealType.categoryPill).foregroundStyle(.white)
                    } else {
                        Text("Kategori yok").font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF8E8E98))
                    }
                    RealSymbol("chevron.right", size: 13.8).foregroundStyle(Color(argb: 0xFF8E8E98))
                }
                .padding(.horizontal, 16).padding(.vertical, 13)
                .liquidGlass(RoundedRectangle(cornerRadius: 22, style: .continuous), role: .sheet)
            }
        }
    }

    // MARK: Actions

    private func saveDraft() {
        guard var d = draft else { return }
        if !d.isChecklist && !d.checklistItems.isEmpty { d.isChecklist = true }
        if d.checklistItems.isEmpty { d.isChecklist = false }
        if let j = d.journal, j.text.trimmingCharacters(in: .whitespaces).isEmpty, j.mood == nil { d.journal = nil }
        d.contentBlockOrder = d.resolvedBlockOrder(d.cardContentKeys())
        store.update(d)
        draft = nil
        secondaryExpanded = false
        checklistOpen = false
    }

    private func addAttachment(_ a: ReminderAttachment?) {
        guard let a else { return }
        if editing { draft?.attachments.append(a) } else { store.mutate(reminderId) { $0.attachments.append(a) } }
    }

    private func removeAttachment(_ a: ReminderAttachment) {
        draft?.attachments.removeAll { $0.id == a.id }
    }

    private func updateStack(_ change: (inout CardStackBlock) -> Void) {
        var stack = shown?.cardStack ?? CardStackBlock()
        change(&stack)
        let value: CardStackBlock? = stack.cards.isEmpty ? nil : stack
        if editing { draft?.cardStack = value } else { store.mutate(reminderId) { $0.cardStack = value } }
    }

    /// Completed card: "Nasıl geçti?" card added to the stack and opened for writing.
    private func addCompletionJournal() {
        let card = CardStackCard(id: "completed_journal_\(EpochMillis.now)", title: "Nasıl geçti?", body: "")
        editingStackCard = card
    }

    private func importPhotos(_ items: [PhotosPickerItem]) {
        guard !items.isEmpty else { return }
        Task {
            var added: [ReminderAttachment] = []
            for item in items {
                if let data = try? await item.loadTransferable(type: Data.self), let a = AttachmentStorage.save(data) { added.append(a) }
            }
            await MainActor.run {
                added.forEach { addAttachment($0) }
                photoItems = []
            }
        }
    }

    private func openInMaps(_ l: ReminderLocation) {
        if let lat = l.latitude, let lon = l.longitude {
            let item = MKMapItem(placemark: MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon)))
            item.name = l.placeName
            item.openInMaps()
        } else {
            let q = "\(l.placeName) \(l.address)".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            if let url = URL(string: "http://maps.apple.com/?q=\(q)") { openURL(url) }
        }
    }
}

// MARK: - Building blocks

/// Bottom bar action: icon over a 10pt label (DetailAction).
private struct DetailAction: View {
    let label: String
    let symbol: String
    var color = Color(argb: 0xFFF0F0F4)
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 1) {
                RealSymbol(symbol, size: 20)
                Text(label).font(RealFont.inter(10)).lineLimit(1)
            }
            .foregroundStyle(enabled ? color : color.opacity(0.35))
            .frame(maxWidth: .infinity, minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScaleStyle(scale: 0.96))
        .disabled(!enabled)
    }
}

/// Edit dock round icon with an optional count (DetailQuickActionButton).
private struct QuickAction: View {
    let symbol: String
    let label: String
    var active = false
    var count: Int? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            RealSymbol(symbol, size: 20)
                .foregroundStyle(active ? Color(argb: 0xFF91B9FF) : Color(argb: 0xFFBABAC3))
                .frame(width: 40, height: 40)
                .overlay(alignment: .topTrailing) {
                    if let count, count > 0 {
                        Text("\(count)").font(.system(size: 10, weight: .semibold)).foregroundStyle(Color(argb: 0xFF91B9FF)).offset(x: -3, y: 3)
                    }
                }
        }
        .buttonStyle(PressScaleStyle(scale: 0.96))
        .accessibilityLabel(label)
    }
}

/// Production card stack deck: active card in front, neighbours peeking above and below; tap a peek to bring it forward.
struct CardStackDeck: View {
    let stack: CardStackBlock
    var editing = false
    var onEdit: (CardStackCard) -> Void = { _ in }
    var onAdd: () -> Void = {}
    @State private var active = 0

    var body: some View {
        let cards = stack.cards
        let current = min(active, max(cards.count - 1, 0))
        VStack(spacing: -318) {
            if current > 0 {
                card(cards[current - 1], index: current - 1).scaleEffect(0.94).opacity(0.75)
                    .onTapGesture { withAnimation(.easeInOut(duration: 0.39)) { active = current - 1 } }
                    .zIndex(0)
            }
            if !cards.isEmpty {
                card(cards[current], index: current)
                    .shadow(color: .black.opacity(0.4), radius: 16, y: 6)
                    .onTapGesture { onEdit(cards[current]) }
                    .zIndex(2)
            }
            if current + 1 < cards.count {
                card(cards[current + 1], index: current + 1).scaleEffect(0.94).opacity(0.75)
                    .onTapGesture { withAnimation(.easeInOut(duration: 0.39)) { active = current + 1 } }
                    .zIndex(1)
            }
        }
        .overlay(alignment: .bottom) {
            if editing {
                Button(action: onAdd) {
                    Text("+ Kart ekle").font(RealFont.inter(12, .medium)).foregroundStyle(.white.opacity(0.8))
                        .padding(.horizontal, 14).padding(.vertical, 8).liquidGlass(Capsule(), role: .chrome)
                }
                .offset(y: 18)
            }
        }
        .padding(.bottom, editing ? 22 : 0)
    }

    private func card(_ c: CardStackCard, index: Int) -> some View {
        let color = c.colorArgb.map { Color(argbInt: $0) } ?? Color(argb: 0xFF292B2D)
        let hasImage = c.imageUri != nil
        let fg: Color = hasImage || luminance(color) < 0.48 ? .white : Color(argb: 0xFF161616)
        let shape = RoundedRectangle(cornerRadius: 30, style: .continuous)
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("KART 0\(index + 1)")
                    .font(RealFont.inter(10)).kerning(1.2).foregroundStyle(fg.opacity(0.9))
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(Capsule().fill(hasImage ? Color.black.opacity(0.36) : .clear))
                    .overlay(Capsule().strokeBorder(fg.opacity(0.55), lineWidth: 1))
                Spacer()
                if stack.cards.count > 1 {
                    Text("\(index + 1)/\(stack.cards.count)").font(RealFont.inter(11)).foregroundStyle(fg.opacity(0.6))
                }
            }
            Spacer(minLength: 0)
            if !c.title.isEmpty { Text(c.title).font(RealFont.inter(28)).kerning(-0.6).foregroundStyle(fg).lineLimit(3) }
            if !c.body.isEmpty { Text(c.body).font(RealFont.inter(14)).lineSpacing(6).foregroundStyle(fg.opacity(0.9)).lineLimit(10) }
        }
        .padding(.horizontal, 24).padding(.vertical, 22)
        .frame(maxWidth: .infinity, minHeight: 390, alignment: .topLeading)
        .background {
            ZStack {
                shape.fill(color)
                if let path = c.imageUri, let img = AttachmentStorage.image(path) {
                    Image(uiImage: img).resizable().scaledToFill()
                    LinearGradient(colors: [.black.opacity(0.06), .black.opacity(0.22), .black.opacity(0.7)], startPoint: .top, endPoint: .bottom)
                }
            }
            .clipShape(shape)
        }
        .overlay(shape.strokeBorder(.white.opacity(0.24), lineWidth: 0.9))
    }

    private func luminance(_ c: Color) -> Double {
        let (r, g, b, _) = c.rgba
        func lin(_ v: Double) -> Double { v <= 0.03928 ? v / 12.92 : pow((v + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }
}

struct CardStackEditor: View {
    @Environment(\.dismiss) private var dismiss
    @State var card: CardStackCard
    let isNew: Bool
    /// nil = delete the card.
    let onSave: (CardStackCard?) -> Void
    @State private var photo: PhotosPickerItem?

    var body: some View {
        NavigationStack {
            Form {
                TextField("Başlık", text: $card.title)
                TextField("Metin", text: $card.body, axis: .vertical).lineLimit(3...12)
                Section("Renk") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 8), spacing: 10) {
                        ForEach(reminderColorChoices) { choice in
                            let argb = choice.color.argbInt
                            Circle().fill(choice.color).frame(height: 28)
                                .overlay(Circle().strokeBorder(.white, lineWidth: card.colorArgb == argb ? 2 : 0))
                                .onTapGesture { card.colorArgb = card.colorArgb == argb ? nil : argb }
                        }
                    }
                }
                Section("Görsel") {
                    PhotosPicker(selection: $photo, matching: .images) { Label(card.imageUri == nil ? "Görsel ekle" : "Görseli değiştir", systemImage: "photo") }
                    if card.imageUri != nil { Button("Görseli kaldır", role: .destructive) { card.imageUri = nil } }
                }
                if !isNew {
                    Button("Kartı sil", role: .destructive) { onSave(nil); dismiss() }
                }
            }
            .navigationTitle(isNew ? "Yeni kart" : "Kart")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { onSave(card); dismiss() }
                        .disabled(card.title.trimmingCharacters(in: .whitespaces).isEmpty && card.body.trimmingCharacters(in: .whitespaces).isEmpty && card.imageUri == nil)
                }
            }
            .onChange(of: photo) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self), let a = AttachmentStorage.save(data) {
                        await MainActor.run { card.imageUri = a.localPath }
                    }
                }
            }
        }
    }
}

/// Place search (MKLocalSearch) or a typed name/address.
struct LocationEditor: View {
    @Environment(\.dismiss) private var dismiss
    let initial: ReminderLocation?
    let onSave: (ReminderLocation?) -> Void
    @State private var query = ""
    @State private var results: [MKMapItem] = []

    var body: some View {
        NavigationStack {
            List {
                Section {
                    TextField("Yer ara", text: $query)
                        .submitLabel(.search)
                        .onSubmit(search)
                }
                ForEach(results, id: \.self) { item in
                    Button {
                        let c = item.placemark.coordinate
                        onSave(ReminderLocation(placeName: item.name ?? query, address: item.placemark.title ?? "",
                                                latitude: c.latitude, longitude: c.longitude))
                        dismiss()
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.name ?? "").foregroundStyle(.white)
                            Text(item.placemark.title ?? "").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                if !query.isEmpty {
                    Button("“\(query)” olarak kaydet") { onSave(ReminderLocation(placeName: query)); dismiss() }
                }
                if initial != nil {
                    Button("Konumu kaldır", role: .destructive) { onSave(nil); dismiss() }
                }
            }
            .navigationTitle("Konum")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } } }
            .onAppear { query = initial?.placeName ?? "" }
        }
    }

    private func search() {
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        MKLocalSearch(request: request).start { response, _ in
            results = response?.mapItems ?? []
        }
    }
}

private struct LinkEditor: View {
    @Environment(\.dismiss) private var dismiss
    let initial: String
    let onSave: (String?) -> Void
    @State private var text = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("https://", text: $text).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
            }
            .navigationTitle("Bağlantı")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        let t = text.trimmingCharacters(in: .whitespaces)
                        onSave(t.isEmpty ? nil : t)
                        dismiss()
                    }
                }
            }
            .onAppear { text = initial.isEmpty ? (UIPasteboard.general.url?.absoluteString ?? "") : initial }
        }
    }
}

private struct MoodPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let selected: JournalMood?
    let onPick: (JournalMood?) -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 12) {
                    ForEach(JournalMood.allCases) { mood in
                        Button { onPick(mood); dismiss() } label: {
                            VStack(spacing: 6) {
                                Text(mood.emoji).font(.system(size: 34))
                                Text(mood.label).font(RealFont.inter(12)).foregroundStyle(.white)
                            }
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(RoundedRectangle(cornerRadius: 18).fill(selected == mood ? RealColors.accentBlue.opacity(0.25) : .white.opacity(0.05)))
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle("Mood")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                if selected != nil { ToolbarItem(placement: .destructiveAction) { Button("Kaldır") { onPick(nil); dismiss() } } }
            }
        }
    }
}

/// İçerik Sıralaması: drag blocks into a new order.
private struct BlockReorderSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State var keys: [String]
    let item: ReminderItem
    let onSave: ([String]) -> Void

    var body: some View {
        NavigationStack {
            List {
                ForEach(keys, id: \.self) { key in
                    let d = describe(key)
                    HStack(spacing: 12) {
                        RealSymbol(d.2, size: 20).foregroundStyle(RealColors.accentBlue).frame(width: 28)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(d.0).foregroundStyle(.white)
                            Text(d.1).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                    }
                }
                .onMove { keys.move(fromOffsets: $0, toOffset: $1) }
            }
            .environment(\.editMode, .constant(.active))
            .navigationTitle("İçerik Sıralaması")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Bitti") { onSave(keys); dismiss() } }
            }
        }
    }

    private func describe(_ key: String) -> (String, String, String) {
        let kind = key.split(separator: ":").first.map(String.init) ?? key
        switch kind {
        case "text": return ("Not Metni", item.text.isEmpty ? "Boş not" : item.text, "note.text")
        case "card_stack": return ("Kart Yığını", "\(item.cardStack?.cards.count ?? 0) kart", "rectangle.stack")
        case "checklist": return ("Checklist", "\(item.checklistItems.count) madde", "checklist")
        case "photos": return ("Fotoğraf", "", "photo")
        case "files": return (key == "files:reference" ? "Bağlantı" : "Dosya", "", "doc")
        case "location": return ("Konum", item.location?.placeName ?? "Konum", "mappin")
        case "mood": return ("Mood", item.mood?.label ?? "Mood", "face.smiling")
        case "journal": return ("Günlük", "Günlük notu", "book.closed")
        case "voice": return ("Ses", "\(item.voiceRecordings.count) kayıt", "waveform")
        case "music": return ("Müzik", item.musicBlocks.first { key.hasSuffix($0.id) }?.title ?? "", "music.note")
        case "media": return ("Film / Dizi", item.mediaBlocks.first { key.hasSuffix($0.id) }?.title ?? "", "film")
        default: return (key, "", "square")
        }
    }
}

/// Photo block: natural aspect, or the height chosen with the resize handle (cropped like ContentScale.Crop).
private struct PhotoBlockBody: View {
    let attachment: ReminderAttachment
    let editing: Bool
    let onTap: () -> Void
    let remove: () -> Void
    @Environment(\.cardBlockHeight) private var fixedHeight

    var body: some View {
        let image = AttachmentStorage.image(attachment.localPath)
        let ratio = image.map { max(0.5, min(2, $0.size.width / max($0.size.height, 1))) } ?? 1
        ZStack(alignment: .topTrailing) {
            Group {
                if fixedHeight > 0 { Color.clear.frame(maxWidth: .infinity).frame(height: fixedHeight) }
                else { Color.clear.frame(maxWidth: .infinity).aspectRatio(ratio, contentMode: .fit) }
            }
                .overlay {
                    if let image { Image(uiImage: image).resizable().scaledToFill() } else { RealColors.field }
                }
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                .contentShape(Rectangle())
                .onTapGesture(perform: onTap)
            if editing {
                Button(action: remove) {
                    RealSymbol("xmark", size: 11.5).foregroundStyle(.white)
                        .frame(width: 22, height: 22).background(Circle().fill(.black.opacity(0.6)))
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Kaldır")
            }
        }
    }
}

private struct PhotoPreview: View {
    @Environment(\.dismiss) private var dismiss
    let attachment: ReminderAttachment
    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            if let img = AttachmentStorage.image(attachment.localPath) {
                Image(uiImage: img).resizable().scaledToFit().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            Button { dismiss() } label: {
                RealSymbol("xmark", size: 17.2).foregroundStyle(.white)
                    .frame(width: 44, height: 44).liquidGlass(Circle())
            }
            .padding(16)
        }
    }
}

/// UIImagePickerController camera wrapper (CardCameraSheet).
struct CameraPicker: UIViewControllerRepresentable {
    let onImage: (Data) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage, let data = image.jpegData(compressionQuality: 0.9) { parent.onImage(data) }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { parent.dismiss() }
    }
}

/// Photos and files are copied into Application Support/attachments (AttachmentStorage.kt).
enum AttachmentStorage {
    static var directory: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("attachments")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func save(_ data: Data) -> ReminderAttachment? {
        guard let image = UIImage(data: data) else { return nil }
        // Optimise like PhotoOptimizer: long edge 2048, JPEG 0.85.
        let maxEdge: CGFloat = 2048
        let scale = min(1, maxEdge / max(image.size.width, image.size.height))
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let resized = UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        guard let jpeg = resized.jpegData(compressionQuality: 0.85) else { return nil }
        let id = UUID().uuidString
        let name = "\(id).jpg"
        do { try jpeg.write(to: directory.appendingPathComponent(name)) } catch { return nil }
        return ReminderAttachment(id: id, localPath: name, type: .image, mimeType: "image/jpeg", displayName: name, sizeBytes: Int64(jpeg.count))
    }

    static func saveFile(_ url: URL) -> ReminderAttachment? {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let id = UUID().uuidString
        let name = "\(id)-\(url.lastPathComponent)"
        let target = directory.appendingPathComponent(name)
        do { try FileManager.default.copyItem(at: url, to: target) } catch { return nil }
        let size = (try? FileManager.default.attributesOfItem(atPath: target.path))?[.size] as? Int64
        let mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType
        let isImage = UTType(filenameExtension: url.pathExtension)?.conforms(to: .image) ?? false
        return ReminderAttachment(id: id, localPath: name, type: isImage ? .image : .file, mimeType: mime,
                                  displayName: url.lastPathComponent, sizeBytes: size)
    }

    static func image(_ path: String) -> UIImage? {
        guard !path.isEmpty else { return nil }
        return UIImage(contentsOfFile: directory.appendingPathComponent(path).path)
    }

    static func remove(_ attachment: ReminderAttachment) {
        guard !attachment.localPath.isEmpty else { return }
        try? FileManager.default.removeItem(at: directory.appendingPathComponent(attachment.localPath))
    }
}

/// Shared by journal list cells.
struct AttachmentThumb: View {
    let attachment: ReminderAttachment
    var body: some View {
        Group {
            if let image = AttachmentStorage.image(attachment.localPath) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                RealSymbol("doc", size: 27.6).foregroundStyle(RealColors.subtext)
            }
        }
        .frame(width: 96, height: 96)
        .background(RealColors.field)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct IdentifiedKey: Identifiable { let id: String }

/// CardTitleTools: "Metin biçimi" — Sola / Ortala / Sağa, Kalın, İtalik.
struct CardTextTools: View {
    let placement: CardBlockPlacement
    let onChange: (CardBlockPlacement) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var current: CardBlockPlacement

    init(placement: CardBlockPlacement, onChange: @escaping (CardBlockPlacement) -> Void) {
        self.placement = placement
        self.onChange = onChange
        _current = State(initialValue: placement)
    }

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Text("Metin biçimi").font(RealFont.inter(16)).foregroundStyle(.white)
                Spacer()
                Button("Bitti") { dismiss() }.foregroundStyle(.white)
            }
            HStack(spacing: 6) {
                ForEach([("start", "Sola"), ("center", "Ortala"), ("end", "Sağa")], id: \.0) { key, label in
                    tool(label, active: current.textAlignment == key) { current.textAlignment = key }
                }
            }
            HStack(spacing: 6) {
                tool("Kalın", active: current.textBold, bold: true) { current.textBold.toggle() }
                tool("İtalik", active: current.textItalic, italic: true) { current.textItalic.toggle() }
            }
        }
        .padding(16)
        .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous), role: .sheet)
        .padding(.horizontal, 16)
        .onChange(of: current) { _, p in onChange(p) }
    }

    private func tool(_ label: String, active: Bool, bold: Bool = false, italic: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: { withAnimation(.easeOut(duration: 0.16)) { action() } }) {
            Text(label).font(RealFont.inter(13, bold ? .bold : .regular)).italic(italic)
                .foregroundStyle(active ? .white : Color(argb: 0xFFB7B7C0))
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(RoundedRectangle(cornerRadius: 14).fill(.white.opacity(active ? 0.18 : 0.04)))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(active ? .isSelected : [])
    }
}
