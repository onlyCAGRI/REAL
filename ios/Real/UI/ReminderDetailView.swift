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
    @State private var showClipboardTray = false
    @State private var trayBusy = false
    @State private var secondaryExpanded = false
    @State private var dockCollapsed = false
    @State private var showMusicSearch = false
    @State private var voiceRecorderOpen = false
    /// "Hava durumunu gizle" on this card (kept by CardWeatherRepository).
    @State private var weatherHidden = false
    @State private var weatherUnavailable = false
    @State private var textToolsKey: String?
    @State private var showMediaSearch = false
    @State private var showGameSearch = false
    @State private var showCardShare = false
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
                // Kontrol ettir: opening the card puts its light out for this viewer.
                .task(id: store.reminder(reminderId)?.checkRequest?.at) { if let r = store.reminder(reminderId) { CheckRequests.shared.markSeen(r) } }
            if let r = shown {
                ScrollView {
                    VStack(spacing: 12) {
                        let order = blockOrder(r)
                        let heroKey = editing ? nil : order.first.flatMap { isHero(r.contentBlockLayout[$0]) ? $0 : nil }
                        if let heroKey {
                            // The cover reaches the screen edges and up under the status bar, like a category's header;
                            // the column's side margins and top padding are undone here.
                            let hero = (r.contentBlockLayout[heroKey] ?? CardBlockPlacement()).normalized
                            let inset = windowTopInset + 10
                            block(heroKey, r, heroInset: inset)
                                .environment(\.cardBlockHeight, CGFloat(hero.minHeight) + inset)
                                .frame(maxWidth: .infinity, minHeight: CGFloat(hero.minHeight) + inset, alignment: .top)
                                .overlay(alignment: .top) { header(r).padding(.horizontal, 22).padding(.top, inset - 4) }
                                .padding(.horizontal, -16)
                                .padding(.top, -inset)
                                .padding(.bottom, 2)
                        } else if !editing { header(r) } else { Color.clear.frame(height: 4) }
                        CardBlockGrid(keys: heroKey == nil ? order : Array(order.dropFirst()),
                                      layout: heroKey.map { heroLiftedLayout(r.contentBlockLayout, hero: $0) } ?? r.contentBlockLayout,
                                      editing: editing,
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
        .fullScreenCover(isPresented: $showLocationEditor) {
            LocationEditor(initial: draft?.location) { draft?.location = $0 }
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
        .sheet(isPresented: $showCardShare) {
            if let r = store.reminder(reminderId) { CardShareSheet(card: r) }
        }
        .sheet(isPresented: $showGameSearch) {
            GameSearchSheet { g in
                withAnimation(.spring(duration: 0.3)) {
                    if editing { if draft?.gameBlocks.contains(where: { $0.providerId == g.providerId }) != true { draft?.gameBlocks.append(g) } }
                    else { store.mutate(reminderId) { r in if !r.gameBlocks.contains(where: { $0.providerId == g.providerId }) { r.gameBlocks.append(g) } } }
                }
            }
        }
        .fullScreenCover(isPresented: $showMoodPicker) {
            MoodAddSheet(onSaved: { mood, _ in
                if editing { draft?.mood = mood } else { store.mutate(reminderId) { $0.mood = mood } }
            })
            .liftedTopSafeArea()
        }
        .sheet(isPresented: $reordering) {
            if let r = shown {
                BlockReorderSheet(keys: blockOrder(r), item: r, hero: blockOrder(r).first.map { isHero(r.contentBlockLayout[$0]) } ?? false) { keys, heroOn in
                    guard var d = draft else { return }
                    let oldHero = blockOrder(d).first { isHero(d.contentBlockLayout[$0]) }
                    d.contentBlockOrder = keys
                    // A new order lets the blocks flow again (Android finishDrag clears the free tops).
                    for (k, var v) in d.contentBlockLayout { v.top = nil; d.contentBlockLayout[k] = v }
                    if let oldHero, var v = d.contentBlockLayout[oldHero] { v.minHeight = 0; d.contentBlockLayout[oldHero] = v.normalized }
                    if heroOn, let top = keys.first {
                        var v = (d.contentBlockLayout[top] ?? CardBlockPlacement()).normalized
                        v.span = CardBlockPlacement.units; v.column = 0; v.minHeight = max(v.minHeight, detailHeroMinHeight)
                        d.contentBlockLayout[top] = v.normalized
                    }
                    draft = d
                }
            }
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

    /// "En üstteki içeriği büyüt" is stored in the existing layout fields: full width + at least 360pt tall.
    private func isHero(_ p: CardBlockPlacement?) -> Bool {
        guard let p = p?.normalized else { return false }
        return p.span == CardBlockPlacement.units && p.minHeight >= detailHeroMinHeight
    }

    /// Blocks placed freely below the hero counted its slot in the grid; with the hero drawn above the grid they
    /// move up by that slot so no empty band opens.
    private func heroLiftedLayout(_ layout: [String: CardBlockPlacement], hero: String) -> [String: CardBlockPlacement] {
        let h = (layout[hero] ?? CardBlockPlacement()).normalized
        let slot = (h.top ?? 0) + h.minHeight + h.gapBefore
        return layout.mapValues { p in
            var q = p
            if let t = q.top { q.top = max(t - slot, 0) }
            return q
        }
    }

    private func blockOrder(_ r: ReminderItem) -> [String] {
        let active = r.cardContentKeys(includeEmptyChecklist: editing && checklistOpen, includeVoiceRecorder: editing && voiceRecorderOpen, includeJournal: false)
            .filter { $0 != "weather" || (!weatherHidden && !weatherUnavailable && CardWeatherRepository.mayShow(r)) }
        return r.resolvedBlockOrder(active)
    }

    @ViewBuilder
    private func block(_ key: String, _ r: ReminderItem, heroInset: CGFloat = 0) -> some View {
        let kind = key.split(separator: ":").first.map(String.init) ?? key
        let id = key.contains(":") ? String(key.split(separator: ":", maxSplits: 1)[1]) : ""
        switch kind {
        case "text": textBlock(r, heroInset: heroInset)
        case "card_stack":
            if let stack = r.cardStack {
                if heroInset > 0 && !editing {
                    // As the card's cover the stack sits below the status bar on a full-bleed dark ground.
                    CardStackDeck(stack: stack, editing: editing, onEdit: { editingStackCard = $0 }, onAdd: { editingStackCard = CardStackCard() })
                        .padding(.horizontal, 16).padding(.top, heroInset + 8).padding(.bottom, 16)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                        .background(LinearGradient(colors: [Color(argb: 0xFF16161A), Color(argb: 0xFF0D0D0F)], startPoint: .top, endPoint: .bottom))
                        .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 28, bottomTrailingRadius: 28, style: .continuous))
                } else {
                    CardStackDeck(stack: stack, editing: editing, onEdit: { editingStackCard = $0 }, onAdd: { editingStackCard = CardStackCard() })
                }
            }
        case "mood": if let mood = r.mood { moodBlock(mood) }
        case "checklist": checklistBlock(r)
        case "photos": if let a = r.attachments.first(where: { $0.id == id }) { photoBlock(a) }
        case "files":
            if id == "reference", let url = r.referenceUrl { linkBlock(url) }
            else if let a = r.attachments.first(where: { $0.id == id }) {
                if a.sourceTitle == "REAL_TEXT_BLOCK" { textBoxBlock(a, r) }
                else if a.type == .textSnippet {
                    SnippetBlockView(attachment: a, editing: editing, onSave: { updateSnippet(a.id, $0) }, onRemove: { removeAttachment(a) })
                } else { fileBlock(a) }
            }
        case "location": if let l = r.location { locationBlock(l, card: r) }
        case "journal": if let j = r.journal { journalBlock(j) }
        case "voice":
            VoiceRecordingBlock(recordings: r.voiceRecordings, editing: editing,
                                onRecorded: { rec in draft?.voiceRecordings.append(rec) },
                                onRemove: { rec in draft?.voiceRecordings.removeAll { $0.id == rec.id } },
                                onClose: { voiceRecorderOpen = false; draft?.voiceRecordings = [] })
        case "music":
            if let m = r.musicBlocks.first(where: { $0.id == id }) {
                MusicBlockView(block: m, editing: editing, onRemove: { draft?.musicBlocks.removeAll { $0.id == id } }, heroInset: heroInset)
            }
        case "media":
            if let m = r.mediaBlocks.first(where: { $0.id == id }) {
                MediaBlockView(block: m, editing: editing, onRemove: { draft?.mediaBlocks.removeAll { $0.id == id } }, heroInset: heroInset)
            }
        case "weather":
            // A half-width or short slot ("Sırala") gets the compact layout.
            let p = (r.contentBlockLayout["weather"] ?? CardBlockPlacement()).normalized
            CardWeatherBlock(reminder: r, compact: p.span < 6 || (p.minHeight > 0 && p.minHeight < 150), interactive: !editing,
                             onHide: { weatherHidden = true }, onUnavailable: { weatherUnavailable = true })
        case "game":
            if let g = r.gameBlocks.first(where: { $0.id == id }) {
                GameBlockView(block: g, editing: editing, onRemove: { draft?.gameBlocks.removeAll { $0.id == id } }, heroInset: heroInset)
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
            Spacer(minLength: 8)
            if CalendarCardPublisher.shared.hasExternalTarget(r.id) {
                let done = CalendarCardPublisher.shared.isPublished(r)
                Button { CalendarCardPublisher.shared.offer(r.id) } label: {
                    HStack(spacing: 5) {
                        RealSymbol(done ? "checkmark" : "arrow.triangle.2.circlepath", size: 11)
                        Text("SYNC").font(RealFont.inter(11, .semibold)).tracking(0.8)
                    }
                    .foregroundStyle(.white.opacity(done ? 0.55 : 0.9))
                    .padding(.horizontal, 12).frame(height: 32)
                    .liquidGlass(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Takvime web bağlantısı ekle")
                Spacer(minLength: 8)
            }
            // Something copied in the last 30 s: the pill sits in the middle of the header (Android: SpaceBetween row).
            FreshPastePill { providers in paste(providers) }
            Spacer(minLength: 8)
            HStack(spacing: 0) {
                // Yapıştır (Android: in the Now Bar button's place). The system PasteButton: no "Allow paste?" prompt.
                // It refuses a see-through tint (it then falls back to its own light fill), so it gets an opaque dark one.
                // Long press: "Panodan ekle", collect several copies and add them one by one (as on Android).
                PasteButton(supportedContentTypes: CardPaste.contentTypes) { providers in paste(providers) }
                    .labelStyle(.iconOnly)
                    .buttonBorderShape(.circle)
                    .tint(Color(argb: 0xFF26262B))
                    .frame(width: 48, height: 40)
                    .simultaneousGesture(LongPressGesture(minimumDuration: 0.45).onEnded { _ in
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        showClipboardTray = true
                    })
                    .accessibilityAction(named: "Panodan ekle") { showClipboardTray = true }
                    .sheet(isPresented: $showClipboardTray) {
                        ClipboardTraySheet(busy: trayBusy) { item in addFromTray(item) }
                    }
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
            // Yapıştır 48 + divider 1 + star 48 (Android: 97 dp).
            .frame(width: 97, height: 40)
            .liquidGlass(RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }

    // MARK: Text block (the card itself)

    /// heroInset > 0: the text is the cover — centred in the block, below the status bar.
    private func textBlock(_ r: ReminderItem, heroInset: CGFloat = 0) -> some View {
        let cover = heroInset > 0 && !editing
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
        let accent = reminderAccentGradient(start: r.accentStart, end: r.accentEnd, completed: r.isCompleted,
                                            blend: r.gradientBlendFraction, solidArgb: r.customSolidColorArgb)
        let dark = r.autoDarkText
        let struck = r.isCompleted && r.journal == nil
        let ink: Color = dark ? (struck ? Color(argb: 0xFF55555A) : Color(argb: 0xFF101010)) : (struck ? Color(argb: 0xFF9999A2) : Color(argb: 0xFFF4F4F6))
        return VStack(alignment: cover ? .center : .leading, spacing: 0) {
            if editing {
                TextField("Hatırlatıcı metni", text: Binding(get: { draft?.text ?? "" }, set: { draft?.text = $0 }), axis: .vertical)
                    .font(RealFont.inter(21))
                    .lineSpacing(10)
                    .foregroundStyle(dark ? Color(argb: 0xFF101010) : .white)
                    .tint(dark ? Color(argb: 0xFF101010) : Color(argb: 0xFF82B3FF))
                    .focused($titleFocused)
                    .frame(minHeight: 96, alignment: .topLeading)
                    .onChange(of: titleFocused) { _, f in if f { secondaryExpanded = false } }
                if !r.isCompleted, r.alertAt != nil || r.plannedDateEpochDay != nil { scheduleChips(r) }
            } else {
                Text(r.text.isEmpty ? "Boş not" : r.text)
                    .font(RealFont.inter(21))
                    .lineSpacing(10)
                    .foregroundStyle(ink)
                    .strikethrough(struck, color: ink)
                    .textSelection(.enabled)
                    .multilineTextAlignment(cover ? .center : .leading)
                    .frame(maxWidth: .infinity, alignment: cover ? .center : .leading)
                Rectangle().fill(dark ? Color(argb: 0xFF101010).opacity(0.2) : Color(argb: 0xFF38383F)).frame(height: 1)
                    .padding(.top, 24).padding(.bottom, 18)
                statusArea(r)
            }
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 25)
        .padding(.top, cover ? heroInset : 0)
        .frame(maxWidth: .infinity, maxHeight: cover ? .infinity : nil, alignment: cover ? .center : .leading)
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
                            timingLines(r)
                        } else if let day = r.plannedDateEpochDay {
                            HStack(spacing: 6) {
                                Text(status).font(RealFont.inter(14, .medium)).foregroundStyle(Color(argb: 0xFFD3D3D9))
                                Text("•").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF6B6B75))
                                Text(RealDates.planned(day)).font(RealFont.inter(14, .medium)).foregroundStyle(RealColors.accentBlue)
                            }
                            timingLines(r)
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
                    // Varınca / Ayrılınca: the place and how close counts (same arrow as on Home).
                    if let trigger = r.locationTrigger, !r.isCompleted {
                        HStack(spacing: 6) {
                            PlaceTriggerGlyph(onExit: trigger.onExit).frame(width: 12, height: 12)
                            Text((trigger.onExit ? "Ayrılınca · " : "Varınca · ") + trigger.placeName
                                 + (trigger.onExit ? " · \(trigger.radiusMeters) m dışına çıkınca" : " · \(trigger.radiusMeters) m içinde"))
                                .font(RealFont.inter(12)).lineLimit(1)
                                .foregroundStyle(PlaceTriggerGlyph.tone(trigger.onExit))
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
        if r.alertAt == nil && r.plannedDateEpochDay == nil { return hasCategory ? nil : "No alert" }
        return "Planned"
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

    /// "Bitiş: 15 Eki 18:00" and "Erken uyarı: 15 dk önce" under the status.
    @ViewBuilder
    private func timingLines(_ r: ReminderItem) -> some View {
        if let end = r.endAt {
            Text("Bitiş: " + Self.shortDateTime(end)).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA5A5B0)).padding(.top, 1)
        }
        if let early = r.earlyAlertMinutes {
            Text("Erken uyarı: " + EarlyAlertScreen.label(early) + " önce").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA5A5B0)).padding(.top, 1)
        }
    }

    static func shortDateTime(_ millis: EpochMillis) -> String {
        let d = millis.date
        return "\(Calendar.real.component(.day, from: d)) " + d.formatted(.dateTime.month(.abbreviated).locale(Locale(identifier: "tr_TR"))) + " " + RealDates.time(d)
    }

    /// Edit-mode schedule chips under the text: date, repeat, early warning, duration, end, clear. A day without a time
    /// (e.g. moved to 24 hour) gets the date chip and the clear button too.
    private func scheduleChips(_ r: ReminderItem) -> some View {
        HStack(spacing: 6) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    if let alert = r.alertAt {
                        chip("calendar", RealDates.reminder(alert), selected: true) { openSchedule(r) }
                    } else if let day = r.plannedDateEpochDay {
                        chip("calendar", RealDates.planned(day), selected: true) { openSchedule(r) }
                    }
                    if r.hasRepeat {
                        chip("repeat", Recurrence.description(r.repeatRule, alert: r.anchorAlertAt ?? r.alertAt), selected: false) { openSchedule(r) }
                    }
                    if let early = r.earlyAlertMinutes {
                        chip("bell", EarlyAlertScreen.label(early) + " önce", selected: false) { openSchedule(r) }
                    }
                    if r.alertAt != nil {
                        chip("note.text", r.durationMinutes.map { "Süre: \($0) min" } ?? "Süre ekle", selected: false) {
                            let options = [nil, 15, 30, 45, 60, 90, 120]
                            let i = options.firstIndex(of: r.durationMinutes) ?? 0
                            draft?.durationMinutes = options[(i + 1) % options.count]
                        }
                    }
                    HStack(spacing: 4) {
                        chip("calendar", r.endAt.map { "Bitiş: " + Self.shortDateTime($0) } ?? "+ Bitiş", selected: false) { openSchedule(r) }
                        if r.endAt != nil {
                            Button { draft?.endAt = nil } label: {
                                RealSymbol("xmark", size: 11).foregroundStyle(Color(argb: 0xFFA7A7AF)).frame(width: 24, height: 24)
                            }
                            .accessibilityLabel("Bitişi kaldır")
                        }
                    }
                }
            }
            Button {
                draft?.alertAt = nil
                draft?.anchorAlertAt = nil
                draft?.plannedDateEpochDay = nil
                draft?.quick24HourUntilAt = nil
                draft?.repeatRule = .none
                draft?.durationMinutes = nil
                draft?.endAt = nil
                draft?.earlyAlertMinutes = nil
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
        let ink = r.autoDarkText ? Color(argb: 0xFF101010) : Color(argb: 0xFFF4F4F6)
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

    private func locationBlock(_ l: ReminderLocation, card: ReminderItem) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if let lat = l.latitude, let lon = l.longitude {
                // Yol: within three hours of the card's time, the driving route from here (taller map).
                CardRouteMap(location: l, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                             routeWanted: !editing && !card.isCompleted && card.alertAt.map { (0...3 * 3_600_000).contains($0 - EpochMillis.now) } == true)
                .overlay(alignment: .bottomTrailing) {
                    if let label = reminderLocationLabel(l) {
                        Text(label).font(RealFont.inter(11, .medium)).foregroundStyle(.white).lineLimit(1)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.black.opacity(0.7)))
                            .frame(maxWidth: 200, alignment: .trailing)
                            .padding(8)
                    }
                }
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
            if !editing, l.latitude != nil, l.longitude != nil { PlaceDetails(location: l).padding(.horizontal, 14).padding(.bottom, 14) }
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
                Group {
                    if dockCollapsed {
                        // Lowered dock: a slim glass bar that still holds the handle.
                        dockHandle
                            .padding(.horizontal, 14).padding(.vertical, 6)
                            .liquidGlass(RoundedRectangle(cornerRadius: 26, style: .continuous))
                            .transition(.opacity)
                    } else {
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
                    dockCollapsed = false   // every edit starts with the dock raised
                    draft = r
                    categorySelection = r.customCategoryId
                    checklistOpen = !r.checklistItems.isEmpty
                }
                Button { showCardShare = true } label: {
                    VStack(spacing: 1) {
                        RealSymbol("square.and.arrow.up", size: 20.7)
                        Text("Share").font(RealFont.inter(10))
                    }
                    .foregroundStyle(Color(argb: 0xFFF0F0F4))
                    .frame(maxWidth: .infinity, minHeight: 56)
                }
                .buttonStyle(.plain)
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
                .font(RealFont.inter(17, .semibold)).lineSpacing(6).foregroundStyle(Color(argb: 0xFFF5F5F8))
            Text(scoped ? "Yalnızca sıradaki tekrarı ya da sıradaki tekrardan itibaren tüm tekrarları silebilirsin. Önceki tekrarlar korunur. Bu işlem geri alınamaz."
                 : "Bu not listenden ve bildirim panelinden kaldırılacak. Bu işlem geri alınamaz.")
                .font(RealFont.inter(13)).lineSpacing(6).foregroundStyle(Color(argb: 0xFFBEBEC8))
            // DeleteReminderPrompt.kt: 46pt pill buttons — red tint for deleting, a light one for Vazgeç.
            if scoped, let occurrence {
                VStack(spacing: 8) {
                    deletePill("Sadece sıradaki tekrarı sil", destructive: true) { deleting = false; store.deleteOccurrence(reminderId, occurrence: occurrence) }
                    deletePill("Bu ve sonraki tüm tekrarları sil", destructive: true) { deleting = false; store.deleteThisAndFuture(reminderId, occurrence: occurrence) }
                    deletePill("Vazgeç", destructive: false) { deleting = false }
                }
                .padding(.top, 4)
            } else {
                HStack(spacing: 10) {
                    deletePill("Vazgeç", destructive: false) { deleting = false }
                    deletePill("Sil", destructive: true) { deleting = false; store.delete([reminderId]) }
                }
                .padding(.top, 4)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .liquidGlass(RoundedRectangle(cornerRadius: 26, style: .continuous), role: .sheet)
    }

    private func deletePill(_ title: String, destructive: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(RealFont.inter(14, destructive ? .semibold : .regular))
                .foregroundStyle(destructive ? Color(argb: 0xFFFFA4AA) : Color(argb: 0xFFE9E9EF))
                .frame(maxWidth: .infinity, minHeight: 46)
                .background(Capsule().fill(destructive ? Color(argb: 0xFFFF858D).opacity(0.14) : Color.white.opacity(0.08)))
                .contentShape(Capsule())
        }
        .buttonStyle(PressScaleStyle(scale: 0.97))
    }

    /// Edit dock: "Renk ve kategori" toggle + sort, then the one-by-one add buttons.
    /// DetailEditDock handle, inside the dock: drag down (>24pt) or tap to lower it, drag up to raise it.
    private var dockHandle: some View {
        Capsule().fill(.white.opacity(0.42)).frame(width: 36, height: 4)
            .frame(maxWidth: .infinity).frame(height: 20).contentShape(Rectangle())
            .onTapGesture { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.22)) { dockCollapsed.toggle() } }
            .gesture(DragGesture(minimumDistance: 4).onEnded { v in
                let next = v.translation.height >= 24 ? true : (v.translation.height <= -24 ? false : dockCollapsed)
                if next != dockCollapsed { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.22)) { dockCollapsed = next } }
            })
            .accessibilityLabel("Düzenleme dock'u")
            .accessibilityValue(dockCollapsed ? "Küçültülmüş" : "Açık")
            .accessibilityAddTraits(.isButton)
    }

    private var editDock: some View {
        VStack(alignment: .leading, spacing: 0) {
            dockHandle.padding(.top, -4)
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
                    // Most used first, least used last (Android order).
                    QuickAction(symbol: "calendar", label: "Tarih / Saat", active: d?.alertAt != nil || d?.plannedDateEpochDay != nil) {
                        if let d { openSchedule(d) }
                    }
                    QuickAction(symbol: "photo", label: "Fotoğraf ekle", active: false,
                                count: d?.attachments.filter { $0.type == .image }.count) { showPhotoPicker = true }
                    QuickAction(symbol: "camera", label: "Kamera ile fotoğraf ekle", active: false) { showCamera = true }
                    QuickAction(symbol: "checklist", label: "Checklist", active: checklistOpen || !(d?.checklistItems.isEmpty ?? true),
                                count: d?.checklistItems.count) {
                        checklistOpen = true
                        draft?.isChecklist = true
                    }
                    QuickAction(symbol: "rectangle.stack", label: "Kart Yığını", active: d?.cardStack != nil, count: d?.cardStack?.cards.count) {
                        editingStackCard = CardStackCard()
                    }
                    QuickAction(symbol: "text.alignleft", label: "Metin kutusu ekle", active: d?.attachments.contains { $0.sourceTitle == "REAL_TEXT_BLOCK" } ?? false) {
                        addAttachment(ReminderAttachment(id: UUID().uuidString, type: .textSnippet, text: "", sourceTitle: "REAL_TEXT_BLOCK"))
                    }
                    QuickAction(symbol: "mappin", label: "Konum", active: d?.location != nil) { withoutAnimation { showLocationGuide = true } }
                    QuickAction(symbol: "waveform", label: "Ses kaydı", active: voiceRecorderOpen || !(d?.voiceRecordings.isEmpty ?? true),
                                count: d?.voiceRecordings.count) { withAnimation(.spring(duration: 0.3)) { voiceRecorderOpen = true } }
                    QuickAction(symbol: "music.note", label: "Müzik", active: !(d?.musicBlocks.isEmpty ?? true),
                                count: d?.musicBlocks.count) { showMusicSearch = true }
                    QuickAction(symbol: "play.rectangle", label: "Film & Dizi", active: !(d?.mediaBlocks.isEmpty ?? true),
                                count: d?.mediaBlocks.count) { showMediaSearch = true }
                    QuickAction(symbol: "gamecontroller", label: "Oyun", active: !(d?.gameBlocks.isEmpty ?? true),
                                count: d?.gameBlocks.count) { showGameSearch = true }
                    QuickAction(symbol: "doc", label: "Dosya ekle", active: false,
                                count: d?.attachments.filter { $0.type == .file }.count) { showFileImporter = true }
                    QuickAction(symbol: "face.smiling", label: "Mood", active: d?.mood != nil) { showMoodPicker = true }
                }
                .padding(.leading, 2).padding(.trailing, 12)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .liquidGlass(RoundedRectangle(cornerRadius: 26, style: .continuous))
        // Adding a block folds the colour / category panels down so the new content is in view (Android).
        .onChange(of: draft.map { blockOrder($0).count } ?? 0) { old, new in
            if new > old, secondaryExpanded { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.22)) { secondaryExpanded = false } }
        }
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

    /// "+" in Panodan ekle: the row becomes blocks on this card and stays in the tray for other cards.
    private func addFromTray(_ item: TrayItem) {
        guard !trayBusy else { return }
        trayBusy = true
        Task { @MainActor in
            defer { trayBusy = false }
            guard let result = await ClipboardTrayStore.result(for: item) else { RealToast.shared.show("Yapıştırılacak bir şey bulunamadı"); return }
            if editing, var d = draft { CardPaste.apply(result, to: &d); draft = d }
            else { store.mutate(reminderId) { CardPaste.apply(result, to: &$0) } }
            RealToast.shared.show("Eklendi · \(result.label)")
        }
    }

    /// Yapıştır: the copied item becomes blocks on this card (the draft while editing, saved right away otherwise).
    private func paste(_ providers: [NSItemProvider]) {
        Task { @MainActor in
            guard let result = await CardPaste.read(providers) else { RealToast.shared.show("Yapıştırılacak bir şey bulunamadı"); return }
            if editing, var d = draft { CardPaste.apply(result, to: &d); draft = d }
            else { store.mutate(reminderId) { CardPaste.apply(result, to: &$0) } }
            ClipboardWatcher.shared.consume()
            RealToast.shared.show("Yapıştırıldı · \(result.label)")
        }
    }

    private func updateSnippet(_ id: String, _ text: String) {
        if editing, let i = draft?.attachments.firstIndex(where: { $0.id == id }) { draft?.attachments[i].text = text }
        else { store.mutate(reminderId) { r in if let i = r.attachments.firstIndex(where: { $0.id == id }) { r.attachments[i].text = text; r.updatedAt = .now } } }
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
                        ForEach(pickerColorChoices) { choice in
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

/// Konum: search a place (MKLocalSearch), use where you are now (one GPS fix), or move/tap the map under the pin.
/// Liquid glass chrome over a full-screen map (LocationPickerSheet.kt): back, the picked place (tap to search), locate;
/// a see-through dark panel below. With `onRemind` it also offers "Varınca / Ayrılınca hatırlat", saved in one tap.
struct LocationEditor: View {
    @Environment(\.dismiss) private var dismiss
    let initial: ReminderLocation?
    let onSave: (ReminderLocation?) -> Void
    var initialTrigger: LocationTrigger? = nil
    var onRemind: ((ReminderLocation, LocationTrigger) -> Void)? = nil
    @State private var query = ""
    @State private var searching = false
    @State private var results: [MKMapItem] = []
    @State private var camera: MapCameraPosition = .automatic
    @State private var pin: CLLocationCoordinate2D?
    @State private var pinName = ""
    @State private var pinAddress = ""
    @State private var locating = false
    @State private var locateError: String?
    @State private var fix = OneShotLocation()
    @State private var userMoving = false
    @State private var leaving = false
    @FocusState private var searchFocused: Bool

    init(initial: ReminderLocation?, initialTrigger: LocationTrigger? = nil,
         onRemind: ((ReminderLocation, LocationTrigger) -> Void)? = nil, onSave: @escaping (ReminderLocation?) -> Void) {
        self.initial = initial
        self.initialTrigger = initialTrigger
        self.onRemind = onRemind
        self.onSave = onSave
    }

    private static let ink = Color(argb: 0xFF0B0B0D)
    private static let muted = Color(argb: 0xFF9A9AA4)

    var body: some View {
        ZStack {
            MapReader { proxy in
                Map(position: $camera) {
                    UserAnnotation()
                }
                .mapStyle(.standard(pointsOfInterest: .excludingAll))
                .onTapGesture { point in
                    searchFocused = false
                    if let c = proxy.convert(point, from: .local) { drop(c, name: "", address: "") }
                }
                .simultaneousGesture(DragGesture(minimumDistance: 4).onChanged { _ in userMoving = true })
                .onMapCameraChange(frequency: .onEnd) { context in
                    guard userMoving else { return }
                    userMoving = false
                    drop(context.region.center, name: "", address: "", moveCamera: false)
                }
            }
            .ignoresSafeArea()
            // The pin marks the map centre; the map moves under it.
            Image(systemName: "mappin")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(pin == nil ? Color.white.opacity(0.6) : Color.white)
                .shadow(color: .black.opacity(0.45), radius: 4, y: 2)
                .offset(y: -17)
                .allowsHitTesting(false)
            VStack(spacing: 10) {
                topBar
                if !results.isEmpty { resultList }
                Spacer()
                bottomPanel
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            leaving = initialTrigger?.onExit ?? false
            if let lat = initial?.latitude, let lon = initial?.longitude {
                let c = CLLocationCoordinate2D(latitude: lat, longitude: lon)
                pin = c
                pinName = initial?.placeName ?? ""
                pinAddress = initial?.address ?? ""
                camera = .region(MKCoordinateRegion(center: c, latitudinalMeters: 900, longitudinalMeters: 900))
            } else if let name = initial?.placeName {
                query = name
            }
        }
    }

    // MARK: Chrome

    private var topBar: some View {
        HStack(spacing: 10) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left").font(.system(size: 17, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: 44, height: 44).liquidGlass(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Vazgeç")
            // The picked place lives here; tapping it searches instead.
            Group {
                if searching || pin == nil {
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass").font(.system(size: 13, weight: .semibold)).foregroundStyle(Self.muted)
                        TextField("", text: $query, prompt: Text("Yer ara ya da haritaya dokun").foregroundStyle(Self.muted))
                            .font(RealFont.inter(13)).foregroundStyle(.white)
                            .submitLabel(.search).onSubmit(search).focused($searchFocused)
                            // Suggestions while typing (cafés, addresses, malls), a moment after the last key.
                            .task(id: query) {
                                guard searchFocused, query.trimmingCharacters(in: .whitespaces).count >= 2 else { return }
                                try? await Task.sleep(for: .milliseconds(300))
                                guard !Task.isCancelled else { return }
                                search()
                            }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                } else if let pin {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(pinName.isEmpty ? "Seçilen yer" : pinName).font(RealFont.inter(13, .semibold))
                            .foregroundStyle(Color(argb: 0xFFF3F1F4)).lineLimit(1)
                        Text(pinAddress.isEmpty ? String(format: "%.5f, %.5f", pin.latitude, pin.longitude) : pinAddress)
                            .font(RealFont.inter(11)).foregroundStyle(Self.muted).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture { searching = true; query = ""; searchFocused = true }
                }
            }
            .padding(.horizontal, 16).frame(height: 44)
            .liquidGlass(Capsule())
            Button(action: locate) {
                Group {
                    if locating { ProgressView().tint(.white) }
                    else { Image(systemName: "location").font(.system(size: 16, weight: .semibold)).foregroundStyle(.white) }
                }
                .frame(width: 44, height: 44).liquidGlass(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Şu anki konumum")
        }
        .padding(.horizontal, 12).padding(.top, 8)
    }

    private var resultList: some View {
        ScrollView {
            VStack(spacing: 0) {
                ForEach(results, id: \.self) { item in
                    Button {
                        drop(item.placemark.coordinate, name: item.name ?? query, address: item.placemark.title ?? "")
                        results = []
                        searching = false
                        searchFocused = false
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.name ?? "").font(RealFont.inter(14, .medium)).foregroundStyle(.white)
                            Text(item.placemark.title ?? "").font(RealFont.inter(11)).foregroundStyle(Self.muted).lineLimit(1)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .frame(maxHeight: 260)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Color(argb: 0xD908080A)))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.white.opacity(0.08), lineWidth: 0.75))
        .padding(.horizontal, 12)
    }

    private var bottomPanel: some View {
        VStack(spacing: 10) {
            if let locateError {
                Text(locateError).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFE6BCB1))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if onRemind != nil {
                remindPill
                whitePill("Konum ekle", enabled: pin != nil) { savePlain() }
            } else {
                whitePill("Bu konumu kaydet", enabled: pin != nil) { savePlain() }
            }
            HStack {
                if !query.isEmpty && pin == nil {
                    Button("“\(query)” olarak kaydet") { onSave(ReminderLocation(placeName: query)); dismiss() }
                        .font(RealFont.inter(13, .medium)).foregroundStyle(Self.muted)
                }
                Spacer()
                if initial != nil {
                    Button("Konumu kaldır", role: .destructive) { onSave(nil); dismiss() }.font(RealFont.inter(13, .medium))
                }
            }
        }
        .padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 8)
        .background {
            // Dark but see-through: the map stays visible behind the panel.
            UnevenRoundedRectangle(topLeadingRadius: 30, topTrailingRadius: 30, style: .continuous)
                .fill(LinearGradient(colors: [Color(argb: 0x8C08080A), Color(argb: 0xD908080A)], startPoint: .top, endPoint: .bottom))
                .overlay(UnevenRoundedRectangle(topLeadingRadius: 30, topTrailingRadius: 30, style: .continuous)
                    .strokeBorder(LinearGradient(colors: [LiquidGlassTokens.contentEdgeTop, LiquidGlassTokens.contentEdgeBottom], startPoint: .top, endPoint: .bottom), lineWidth: 0.75))
                .ignoresSafeArea(edges: .bottom)
        }
    }

    /// "Varınca hatırlat" with a small "Ayrılınca" at its right end; tapping the small one swaps them ("Ayrılınca
    /// hatırlat" slides to the middle, "Varınca" moves to the left end). The middle saves.
    private var remindPill: some View {
        let enabled = pin != nil
        return ZStack {
            Capsule().fill(.white.opacity(enabled ? 1 : 0.4))
            Text(leaving ? "Ayrılınca hatırlat" : "Varınca hatırlat")
                .font(RealFont.inter(15, .semibold)).foregroundStyle(Self.ink)
                .id(leaving)
                .transition(.asymmetric(insertion: .move(edge: leaving ? .trailing : .leading).combined(with: .opacity),
                                        removal: .move(edge: leaving ? .leading : .trailing).combined(with: .opacity)))
            HStack {
                if !leaving { Spacer() }
                Button { withAnimation(.spring(response: 0.34, dampingFraction: 0.85)) { leaving.toggle() } } label: {
                    Text(leaving ? "Varınca" : "Ayrılınca").font(RealFont.inter(12, .medium)).foregroundStyle(Self.ink.opacity(0.5))
                        .padding(.horizontal, 12).padding(.vertical, 9)
                        .background(Capsule().fill(Self.ink.opacity(0.06)))
                }
                .buttonStyle(.plain)
                .disabled(!enabled)
                if leaving { Spacer() }
            }
            .padding(.horizontal, 6)
        }
        .frame(height: 54)
        .clipShape(Capsule())
        .contentShape(Capsule())
        .onTapGesture {
            guard enabled, let pin else { return }
            let place = pickedPlace(pin)
            onRemind?(place, LocationTrigger(placeName: place.placeName, latitude: pin.latitude, longitude: pin.longitude,
                                             radiusMeters: LocationTrigger.preciseRadius, onExit: leaving))
            dismiss()
        }
    }

    private func whitePill(_ label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(RealFont.inter(15, .semibold)).foregroundStyle(Self.ink)
                .frame(maxWidth: .infinity).frame(height: 54)
                .background(Capsule().fill(.white.opacity(enabled ? 1 : 0.4)))
        }
        .buttonStyle(PressScaleStyle(scale: 0.97))
        .disabled(!enabled)
    }

    private func pickedPlace(_ pin: CLLocationCoordinate2D) -> ReminderLocation {
        let name = pinName.isEmpty ? (pinAddress.isEmpty ? "Seçilen yer" : pinAddress) : pinName
        return ReminderLocation(placeName: name, address: pinAddress, latitude: pin.latitude, longitude: pin.longitude)
    }

    private func savePlain() {
        guard let pin else { return }
        onSave(pickedPlace(pin))
        dismiss()
    }

    private func search() {
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        request.resultTypes = [.pointOfInterest, .address]
        // Near the place on the map (or where the phone is), so "kahve" finds the cafés around.
        if let c = pin ?? CLLocationManager().location?.coordinate {
            request.region = MKCoordinateRegion(center: c, latitudinalMeters: 30_000, longitudinalMeters: 30_000)
        }
        MKLocalSearch(request: request).start { response, _ in
            results = response?.mapItems ?? []
        }
    }

    /// Puts the pin on the map; without a name it is looked up from the coordinate.
    private func drop(_ c: CLLocationCoordinate2D, name: String, address: String, moveCamera: Bool = true) {
        pin = c
        pinName = name
        pinAddress = address
        locateError = nil
        if moveCamera {
            withAnimation(.easeInOut(duration: 0.35)) {
                camera = .region(MKCoordinateRegion(center: c, latitudinalMeters: 700, longitudinalMeters: 700))
            }
        }
        guard name.isEmpty else { return }
        CLGeocoder().reverseGeocodeLocation(CLLocation(latitude: c.latitude, longitude: c.longitude)) { marks, _ in
            guard let m = marks?.first, pin?.latitude == c.latitude, pin?.longitude == c.longitude else { return }
            pinName = m.name ?? m.thoroughfare ?? ""
            pinAddress = [m.thoroughfare, m.subLocality, m.locality].compactMap { $0 }.joined(separator: ", ")
        }
    }

    private func locate() {
        guard !locating else { return }
        locating = true
        locateError = nil
        fix.request { result in
            locating = false
            switch result {
            case .success(let c): drop(c, name: "", address: "")
            case .failure(let message): locateError = message
            }
        }
    }
}

/// One location fix for "Şu anki konumum" (when-in-use permission, asked the first time).
@Observable
final class OneShotLocation: NSObject, CLLocationManagerDelegate {
    enum Result { case success(CLLocationCoordinate2D), failure(String) }
    @ObservationIgnored private let manager = CLLocationManager()
    @ObservationIgnored private var completion: ((Result) -> Void)?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
    }

    func request(_ done: @escaping (Result) -> Void) {
        completion = done
        switch manager.authorizationStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .denied, .restricted: finish(.failure("Konum izni kapalı. Ayarlar > REAL > Konum bölümünden açabilirsin."))
        default: manager.requestLocation()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard completion != nil else { return }
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: manager.requestLocation()
        case .denied, .restricted: finish(.failure("Konum izni verilmedi."))
        default: break
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let c = locations.last?.coordinate { finish(.success(c)) }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finish(.failure("Konum alınamadı. Biraz sonra tekrar dene."))
    }

    private func finish(_ result: Result) {
        let done = completion
        completion = nil
        DispatchQueue.main.async { done?(result) }
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

/// İçerik Sıralaması: drag blocks into a new order.
private struct BlockReorderSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State var keys: [String]
    let item: ReminderItem
    @State var hero: Bool
    let onSave: ([String], Bool) -> Void

    var body: some View {
        NavigationStack {
            List {
                // "En üstteki içeriği büyüt": the block at the top of the list becomes a full-width cover.
                Section {
                    Button { withAnimation(.spring(duration: 0.25)) { hero.toggle() } } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle().fill(Color(argb: 0xFF91B9FF).opacity(hero ? 1 : 0))
                                Circle().strokeBorder(hero ? Color(argb: 0xFF91B9FF) : Color(argb: 0xFF6E6E78), lineWidth: 1.5)
                                if hero { RealSymbol("checkmark", size: 12).foregroundStyle(Color(argb: 0xFF0B0B0D)) }
                            }
                            .frame(width: 22, height: 22)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("En üstteki içeriği büyüt").font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFFE8E8EE))
                                Text("Listenin başındaki içerik kartın tepesinde tam genişlikte, büyük görünür.")
                                    .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8E8E98))
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityValue(hero ? "Açık" : "Kapalı")
                }
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
                ToolbarItem(placement: .confirmationAction) { Button("Bitti") { onSave(keys, hero); dismiss() } }
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
        case "game": return ("Oyun", item.gameBlocks.first { key.hasSuffix($0.id) }?.title ?? "", "gamecontroller")
        case "weather": return ("Hava durumu", "Apple Weather", "cloud.sun")
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

/// DETAIL_HERO_MIN_HEIGHT_DP: the hero block's minimum height.
let detailHeroMinHeight = 360

/// Short name for the map's corner: the place's name, or the street when an empty spot was picked.
func reminderLocationLabel(_ l: ReminderLocation) -> String? {
    let name = l.placeName.trimmingCharacters(in: .whitespaces)
    let street = l.address.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.first { !$0.isEmpty }
    if !name.isEmpty, name != "Seçilen yer", name != l.address.trimmingCharacters(in: .whitespaces), name.contains(where: \.isLetter) { return name }
    if let street { return street }
    return name.isEmpty ? nil : name
}
