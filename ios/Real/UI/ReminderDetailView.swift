import SwiftUI
import UIKit
import PhotosUI
import MapKit
import UniformTypeIdentifiers
import Lottie

private extension View {
    /// Ends a SwiftUI type here. A phone's main thread has about 1 MB of stack and the runtime walks a view's whole
    /// generic type on it when the view first appears; this screen's type grew deep enough to overflow it, so opening
    /// any card crashed (build 189). Simulators have 8 MB and never show it: CI's "stackcheck" mode tests a small stack.
    func erased() -> AnyView { AnyView(self) }
}

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
    /// Opened from home: the bar starts as home's capsule and takes the dock's shape.
    var fromHome = false
    @State private var dockFormed = false

    @State private var draft: ReminderItem?
    @State private var showClipboardTray = false
    @State private var trayBusy = false
    @State private var secondaryExpanded = false
    @State private var dockCollapsed = false
    /// Height of the cover title on screen, and where a drag on its handle started: (extra height, most it may reach).
    @State private var coverTitleHeight: CGFloat = 0
    @State private var coverDragStart: (extra: CGFloat, limit: CGFloat)?
    /// A block is lifted in the grid: the bottom docks slide away until the finger lifts.
    @State private var blockMoving = false
    @State private var showMusicSearch = false
    @State private var voiceRecorderOpen = false
    /// "Hava durumunu gizle" on this card (kept by CardWeatherRepository).
    @State private var weatherHidden = false
    @State private var weatherUnavailable = false
    @State private var showMediaSearch = false
    @State private var showGameSearch = false
    @State private var showCardShare = false
    @State private var shareSession = CardDetailShareSession()
    @State private var deleting = false
    /// When Kaydet sent the sweep through the dock; the normal dock's buttons appear as it passes them.
    @State private var saveSweepStart: Date?
    /// Tamamla's sweep: Kaydet's, mirrored from the left.
    @State private var completeSweepStart: Date?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var iconsPeek: CGFloat = 0
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
    @State private var showMapPoster = false
    @State private var showSkyPoster = false
    @State private var showLinkEditor = false
    @State private var editingStackCard: CardStackCard?
    /// Tapped a stack while reading: its text is typed right on the cards, "Bitti" (or leaving) stores it.
    @State private var inlineStack: CardStackBlock?
    @State private var showMoodPicker = false
    @State private var previewPhoto: ReminderAttachment?
    @FocusState private var titleFocused: Bool
    /// Checklist editing: one focus per item id, plus the "add item" row at the bottom.
    private enum ChecklistFocus: Hashable { case item(Int64), add }
    @FocusState private var checklistFocus: ChecklistFocus?
    /// A just-added first item asks for focus once its row is on screen.
    @State private var pendingChecklistFocus: ChecklistFocus?
    /// The cover title comes back through Yaşam's blur whenever editing toggles or it changes size.
    @State private var titleShown = true
    @State private var showTitleTools = false

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
                        let heroKey = order.first.flatMap { isHero(r.contentBlockLayout[$0]) ? $0 : nil }
                        if let heroKey {
                            // The cover reaches the screen edges and up under the status bar, like a category's header;
                            // the column's side margins and top padding are undone here.
                            let hero = (r.contentBlockLayout[heroKey] ?? CardBlockPlacement()).normalized
                            let inset = windowTopInset + 10
                            let isTextHero = heroKey == "text"
                            let nonTextHeight = CGFloat(max(hero.minHeight, detailHeroMinHeight)) + inset
                            block(heroKey, r, heroInset: inset)
                                .environment(\.cardBlockHeight, isTextHero ? 0 : nonTextHeight)
                                .frame(maxWidth: .infinity, minHeight: isTextHero ? nil : nonTextHeight, alignment: .top)
                                .overlay(alignment: .top) { header(r).padding(.horizontal, 22).padding(.top, inset - 4).erased() }
                                .padding(.horizontal, -16)
                                .padding(.top, -inset)
                                .padding(.bottom, 2)
                        } else if !editing { header(r).erased() } else { Color.clear.frame(height: 4) }
                        CardBlockGrid(keys: heroKey == nil ? order : Array(order.dropFirst()),
                                      layout: heroKey.map { heroLiftedLayout(r.contentBlockLayout, hero: $0) } ?? r.contentBlockLayout,
                                      editing: editing,
                                      onChange: { key, p in
                                          guard key != heroKey else { return }
                                          var q = p
                                          if let heroKey, let t = q.top {
                                              q.top = min(t + heroSlot(r.contentBlockLayout, hero: heroKey), cardMaxTop)
                                          }
                                          draft?.contentBlockLayout[key] = q.normalized
                                      },
                                      onDrop: { keys, layout in
                                          if let heroKey {
                                              let slot = heroSlot(r.contentBlockLayout, hero: heroKey)
                                              let storedHero = draft?.contentBlockLayout[heroKey]
                                              var restored: [String: CardBlockPlacement] = [:]
                                              for (k, p) in layout where k != heroKey {
                                                  var q = p
                                                  if let t = q.top { q.top = min(t + slot, cardMaxTop) }
                                                  restored[k] = q.normalized
                                              }
                                              if let storedHero { restored[heroKey] = storedHero }
                                              draft?.contentBlockOrder = [heroKey] + keys.filter { $0 != heroKey }
                                              draft?.contentBlockLayout = restored
                                          } else {
                                              draft?.contentBlockOrder = keys
                                              draft?.contentBlockLayout = layout
                                          }
                                      },
                                      onResizeBegan: lowerEditDock,
                                      onMovingChange: { lifted in
                                          withAnimation(reduceMotion ? nil : .timingCurve(0.2, 0, 0, 1, duration: lifted ? 0.2 : 0.26)) { blockMoving = lifted }
                                      }) { key in
                            block(key, r).transition(.opacity.combined(with: .scale(scale: 0.97)))
                        }
                        Text(createdLine(r))
                            .font(RealFont.inter(14))
                            .foregroundStyle(Color(argb: 0xFF8C8C95))
                            .frame(maxWidth: .infinity, alignment: .trailing)
                            .padding(.top, heroKey == "text" && order.count == 1 ? 0 : 8).padding(.trailing, 6)
                        if editing, !r.isCompleted, r.alertAt != nil || r.plannedDateEpochDay != nil {
                            scheduleChips(r).padding(.top, 12).erased()
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                    .padding(.bottom, editing ? (secondaryExpanded ? 520 : 300) : (inlineStack != nil ? 300 : 130))
                    .background {
                        // isCardDetailBackgroundTap: while editing, a tap on empty space (not on a block, which
                        // takes its own taps first) saves and closes the editor quietly, without Kaydet's sweep.
                        if editing {
                            Color.clear.contentShape(Rectangle()).onTapGesture { saveFromBackgroundTap() }
                        } else if inlineStack != nil {
                            Color.clear.contentShape(Rectangle()).onTapGesture { finishInlineStack() }
                        }
                    }
                    .animation(.spring(duration: 0.3), value: blockOrder(r))
                }
                .scrollDismissesKeyboard(.interactively)
                // A lifted block owns the finger: the page does not scroll under it.
                .scrollDisabled(blockMoving)
                .erased()
                bottomColumn(r).erased()
            } else {
                Color.clear.onAppear { dismiss() }
            }
        }
        .erased()
        .toolbar(.hidden, for: .navigationBar)
        .task {
            // CI stack check: the edit mode's views too.
            guard ProcessInfo.processInfo.arguments.contains("-REALDetailEdit"), let stored else { return }
            draft = stored
            secondaryExpanded = true
        }
        .realOverlay(isPresented: $showDateSheet) { ScheduleSheet(draft: $schedule) }
        .onChange(of: schedule) { _, new in
            if editing { new.apply(to: &draft!) } else { store.mutate(reminderId) { new.apply(to: &$0) } }
        }
        .realOverlay(isPresented: $showCategoryPicker) { CategoryPickerSheet(selected: $categorySelection) }
        .onChange(of: categorySelection) { _, new in draft?.customCategoryId = new }
        .photosPicker(isPresented: $showPhotoPicker, selection: $photoItems, maxSelectionCount: 10, matching: .images)
        .onChange(of: photoItems) { _, items in importPhotos(items) }
        .fullScreenCover(isPresented: $showCamera) { CameraPicker { data in addAttachment(AttachmentStorage.save(data)) }.ignoresSafeArea() }
        // Real Labs posters: the card's place as a styled map, or its sky at the card's moment; "Karta ekle" adds a photo.
        .fullScreenCover(isPresented: $showMapPoster) {
            if let l = draft?.location ?? shown?.location, let lat = l.latitude, let lng = l.longitude {
                MapPosterScreen(latitude: lat, longitude: lng, placeName: l.placeName,
                                onAttach: { image in addPoster(image) }, onClose: { showMapPoster = false })
            }
        }
        .fullScreenCover(isPresented: $showSkyPoster) {
            if let l = draft?.location ?? shown?.location, let lat = l.latitude, let lng = l.longitude {
                SkyPosterScreen(latitude: lat, longitude: lng, placeName: l.placeName, momentMillis: posterMoment,
                                onAttach: { image in addPoster(image) }, onClose: { showSkyPoster = false })
            }
        }
        .erased()
        .fileImporter(isPresented: $showFileImporter, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
            if case .success(let urls) = result { urls.forEach { addAttachment(AttachmentStorage.saveFile($0)) } }
        }
        .realOverlay(isPresented: $showLocationGuide) {
            // LocationGuideSheet: Android hands off to Google Maps → Share → REAL; iOS searches in-app (a share
            // extension would be needed for the Maps hand-off).
            PermissionSheet(title: t("Konum ekle"), description: t("Haritada istediğin yeri ara ve seç. Seçtiğin konum karta eklenir."),
                            primaryLabel: t("Haritada ara"), secondaryLabel: t("Vazgeç"), glyph: .pin,
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
            CardStackEditor(card: seededStackCard(card), isNew: (inlineStack ?? shown?.cardStack)?.cards.contains { $0.id == card.id } != true) { updated in
                updateStack { stack in
                    if let updated {
                        if let i = stack.cards.firstIndex(where: { $0.id == updated.id }) { stack.cards[i] = updated } else { stack.cards.append(updated) }
                        // Writing settings belong to the whole stack.
                        for i in stack.cards.indices { stack.cards[i].bodyFormat = updated.bodyFormat }
                    } else {
                        stack.cards.removeAll { $0.id == card.id }
                    }
                }
            }
        }
        // Medya: one search for songs, films, series and games (the full-screen composer's page, Android MediaSearchAllSheet).
        .erased()
        .sheet(isPresented: $showMusicSearch) {
            let d = draft ?? store.reminder(reminderId)
            MediaSearchAllSheet(search: MediaSearchAll(
                music: d?.musicBlocks ?? [], media: d?.mediaBlocks ?? [], games: d?.gameBlocks ?? [],
                onAddMusic: { m in withAnimation(.spring(duration: 0.3)) { draft?.musicBlocks.append(m) } },
                onAddMedia: { m in withAnimation(.spring(duration: 0.3)) { draft?.mediaBlocks.append(m) } },
                onAddGame: { g in
                    withAnimation(.spring(duration: 0.3)) {
                        if editing { if draft?.gameBlocks.contains(where: { $0.providerId == g.providerId }) != true { draft?.gameBlocks.append(g) } }
                        else { store.mutate(reminderId) { r in if !r.gameBlocks.contains(where: { $0.providerId == g.providerId }) { r.gameBlocks.append(g) } } }
                    }
                },
                onRemoveMusic: { m in draft?.musicBlocks.removeAll { $0.id == m.id } },
                onRemoveMedia: { m in draft?.mediaBlocks.removeAll { $0.id == m.id } },
                onRemoveGame: { g in
                    if editing { draft?.gameBlocks.removeAll { $0.id == g.id } }
                    else { store.mutate(reminderId) { r in r.gameBlocks.removeAll { $0.id == g.id } } }
                }))
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
        .erased()
        .sheet(isPresented: $reordering) {
            if let r = shown {
                BlockReorderSheet(keys: blockOrder(r), item: r, hero: blockOrder(r).first.map { isHero(r.contentBlockLayout[$0]) } ?? true) { keys, heroOn in
                    guard var d = draft else { return }
                    let oldHero = blockOrder(d).first.flatMap { isHero(d.contentBlockLayout[$0]) ? $0 : nil }
                    d.contentBlockOrder = keys
                    // A new order lets the blocks flow again (Android finishDrag clears the free tops).
                    for (k, var v) in d.contentBlockLayout { v.top = nil; d.contentBlockLayout[k] = v }
                    if let oldHero, var v = d.contentBlockLayout[oldHero] { v.minHeight = 0; d.contentBlockLayout[oldHero] = v.normalized }
                    if let top = keys.first {
                        var v = (d.contentBlockLayout[top] ?? CardBlockPlacement()).normalized
                        if heroOn {
                            v.span = CardBlockPlacement.units; v.column = 0; v.minHeight = max(v.minHeight, detailHeroMinHeight)
                        } else {
                            v.minHeight = 0
                        }
                        d.contentBlockLayout[top] = v.normalized
                    }
                    draft = d
                }
            }
        }
        .fullScreenCover(item: $previewPhoto) { a in PhotoPreview(attachment: a) }
    }

    /// A new stack card starts with the stack's writing settings ("Kart ekle" on Android copies them).
    private func stackDeck(_ stack: CardStackBlock) -> some View {
        CardStackDeck(stack: stack, editing: editing, inline: inlineStack != nil,
                      onEdit: { editingStackCard = $0 },
                      onAdd: addStackCard,
                      onDelete: { card in
                          withAnimation(.spring(duration: 0.3)) { updateStack { $0.cards.removeAll { $0.id == card.id } } }
                      },
                      onChange: { id, change in
                          updateStack { s in
                              if let i = s.cards.firstIndex(where: { $0.id == id }) { change(&s.cards[i]) }
                          }
                      },
                      onStartTyping: { if !editing && inlineStack == nil { inlineStack = stored?.cardStack } })
    }

    /// The first card is an empty one on the deck, ready to type on; one left empty is dropped when stored.
    /// With cards already there the new one opens in the card editor: appended blank to a deck it showed
    /// nothing you could see (it went off screen, or the keyboard closing dropped it as empty).
    private func addStackCard() {
        let card = seededStackCard(CardStackCard())
        // Düzenle: the card joins the deck and comes to the front for typing, as on Android (one left blank is
        // dropped on save). Without Düzenle the keyboard closing would drop a blank one, so that path keeps the sheet.
        if editing {
            withAnimation(.spring(duration: 0.3)) { updateStack { $0.cards.append(card) } }
        } else if let existing = inlineStack ?? shown?.cardStack, !existing.cards.isEmpty {
            editingStackCard = card
        } else {
            updateStack { $0.cards.append(card) }
        }
    }

    private func finishInlineStack() {
        guard var stack = inlineStack else { return }
        stack.cards.removeAll(where: \.isBlank)
        inlineStack = nil
        store.mutate(reminderId) { $0.cardStack = stack.cards.isEmpty ? nil : stack }
    }

    private func seededStackCard(_ card: CardStackCard) -> CardStackCard {
        guard let stack = inlineStack ?? shown?.cardStack, !stack.cards.contains(where: { $0.id == card.id }), let format = stack.cards.first?.bodyFormat else { return card }
        var seeded = card
        seeded.bodyFormat = format
        return seeded
    }

    /// "Oluşturuldu · Pazartesi • 12 Eki 2026, 18:21"
    private func createdLine(_ r: ReminderItem) -> String {
        let cal = Calendar.real
        let d = r.createdAt.date
        let c = cal.dateComponents([.year, .month, .day], from: d)
        let month = d.formatted(.dateTime.month(.abbreviated).locale(I18n.locale))
        return t("Oluşturuldu · {0} • {1} {2} {3}, {4}", t(cal.isoWeekday(of: d).full), c.day ?? 1, month, c.year ?? 2026, RealDates.time(d))
    }

    // MARK: Block order

    /// "En üstteki içeriği büyüt": a block with no stored placement counts as hero; a stored placement needs full width + at least 360pt tall.
    private func isHero(_ p: CardBlockPlacement?) -> Bool {
        guard let p = p?.normalized else { return true }
        return p.span == CardBlockPlacement.units && p.minHeight >= detailHeroMinHeight
    }

    private func heroSlot(_ layout: [String: CardBlockPlacement], hero: String) -> Int {
        let h = (layout[hero] ?? CardBlockPlacement()).normalized
        // A taller cover title only pushes the grid down; the blocks' tops never counted that part.
        return (h.top ?? 0) + (hero == "text" ? min(h.minHeight, detailHeroMinHeight) : h.minHeight) + h.gapBefore
    }

    /// Blocks placed freely below the hero counted its slot in the grid; with the hero drawn above the grid they
    /// move up by that slot so no empty band opens.
    private func heroLiftedLayout(_ layout: [String: CardBlockPlacement], hero: String) -> [String: CardBlockPlacement] {
        let slot = heroSlot(layout, hero: hero)
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

    /// Erased: see `erased()`.
    private func block(_ key: String, _ r: ReminderItem, heroInset: CGFloat = 0) -> AnyView {
        blockContent(key, r, heroInset: heroInset).erased()
    }

    @ViewBuilder
    private func blockContent(_ key: String, _ r: ReminderItem, heroInset: CGFloat) -> some View {
        let kind = key.split(separator: ":").first.map(String.init) ?? key
        let id = key.contains(":") ? String(key.split(separator: ":", maxSplits: 1)[1]) : ""
        switch kind {
        case "text": textBlock(r, heroInset: heroInset).erased()
        case "card_stack":
            if let stack = inlineStack ?? r.cardStack {
                if heroInset > 0 {
                    // As the card's cover the stack sits below the status bar on a full-bleed dark ground.
                    stackDeck(stack)
                        .padding(.horizontal, 16).padding(.top, heroInset + 8).padding(.bottom, 16)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                        .background(LinearGradient(colors: [Color(argb: 0xFF16161A), Color(argb: 0xFF0D0D0F)], startPoint: .top, endPoint: .bottom))
                        .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 28, bottomTrailingRadius: 28, style: .continuous))
                        .erased()
                } else {
                    stackDeck(stack).erased()
                }
            }
        case "mood": if let mood = r.mood { moodBlock(mood).erased() }
        case "checklist": checklistBlock(r).erased()
        case "photos": if let a = r.attachments.first(where: { $0.id == id }) { photoBlock(a, heroInset: heroInset).erased() }
        case "files":
            if id == "reference", let url = r.referenceUrl { linkBlock(url).erased() }
            else if let a = r.attachments.first(where: { $0.id == id }) {
                if a.type == .textSnippet {
                    SnippetBlockView(attachment: a, editing: editing, onSave: { updateSnippet(a.id, $0) }, onRemove: { removeAttachment(a) })
                } else { fileBlock(a).erased() }
            }
        case "location": if let l = r.location { locationBlock(l, card: r).erased() }
        case "journal": if let j = r.journal { journalBlock(j).erased() }
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
            Button {
                if fromHome && !reduceMotion {
                    withAnimation(.timingCurve(0.2, 0, 0, 1, duration: 0.26)) { dockFormed = false }
                }
                dismiss()
            } label: {
                RealSymbol("chevron.left", size: 20.7).foregroundStyle(.white)
                    .frame(width: 40, height: 40).liquidGlass(Circle())
                    .frame(width: 48, height: 48)
            }
            .accessibilityLabel(t("Geri"))
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
                .accessibilityLabel(t("Takvime web bağlantısı ekle"))
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
                    .accessibilityAction(named: Text(t("Panodan ekle"))) { showClipboardTray = true }
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
                .accessibilityLabel(r.isImportant ? t("Önemli işaretini kaldır") : t("Önemli olarak işaretle"))
            }
            // Yapıştır 48 + divider 1 + star 48 (Android: 97 dp).
            .frame(width: 97, height: 40)
            .liquidGlass(RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }

    // MARK: Text block (the card itself)

    /// heroInset > 0: the text is the cover — below the status bar.
    private func textBlock(_ r: ReminderItem, heroInset: CGFloat = 0) -> some View {
        let cover = heroInset > 0
        let alone = blockOrder(r).count == 1
        let storedText = r.contentBlockLayout["text"]?.normalized
        let compact = !cover && (storedText.map { $0.minHeight > 0 && $0.minHeight < 200 } ?? false)
        // Height dragged onto the cover title, stored above the cover's mark so a plain cover stays at 360.
        let coverExtra = cover ? CGFloat(max((storedText?.minHeight ?? 0) - detailHeroMinHeight, 0)) : 0
        let alignKey = storedText?.textAlignment ?? (cover ? "center" : "start")
        let hAlign: HorizontalAlignment = alignKey == "center" ? .center : (alignKey == "end" ? .trailing : .leading)
        let frameAlign: Alignment = alignKey == "center" ? .center : (alignKey == "end" ? .trailing : .leading)
        let textAlign: TextAlignment = alignKey == "center" ? .center : (alignKey == "end" ? .trailing : .leading)
        let italic = storedText?.textItalic == true
        let fontSize: CGFloat = (cover && alone) ? 36 : 28
        let titleFont = storedText?.textBold == true ? Font.custom("InterTight-Bold", size: fontSize) : RealFont.inter(fontSize)
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
        let accent = reminderAccentGradient(start: r.accentStart, end: r.accentEnd, completed: r.isCompleted,
                                            blend: r.gradientBlendFraction, solidArgb: r.customSolidColorArgb)
        let dark = r.autoDarkText
        let struck = r.isCompleted && !r.hasCompletionNote
        let ink: Color = dark ? (struck ? Color(argb: 0xFF55555A) : Color(argb: 0xFF101010)) : (struck ? Color(argb: 0xFF9999A2) : Color(argb: 0xFFF4F4F6))
        return VStack(alignment: hAlign, spacing: 0) {
            if editing {
                TextField(t("Hatırlatıcı metni"), text: Binding(get: { draft?.text ?? "" }, set: { draft?.text = $0 }), axis: .vertical)
                    .font(titleFont)
                    .italic(italic)
                    .lineSpacing(0)
                    .foregroundStyle(dark ? Color(argb: 0xFF101010) : Color(argb: 0xFFF4F4F6))
                    .tint(dark ? Color(argb: 0xFF101010) : Color(argb: 0xFF82B3FF))
                    .multilineTextAlignment(textAlign)
                    .focused($titleFocused)
                    .frame(maxWidth: .infinity, alignment: frameAlign)
                    .modifier(DetailTextReveal(shown: !cover || titleShown))
                    .onChange(of: titleFocused) { _, f in if f { secondaryExpanded = false } }
            } else {
                Text(r.text.isEmpty ? t("Boş not") : r.text)
                    .font(titleFont)
                    .italic(italic)
                    .lineSpacing(0)
                    .foregroundStyle(ink)
                    .strikethrough(struck, color: ink)
                    .textSelection(.enabled)
                    .multilineTextAlignment(textAlign)
                    .frame(maxWidth: .infinity, alignment: frameAlign)
                    .modifier(DetailTextReveal(shown: !cover || titleShown))
            }
            if !compact {
                statusArea(r, hAlign: hAlign, frameAlign: frameAlign, textAlign: textAlign)
                    .modifier(DetailTextReveal(shown: !editing))
                    .animation(reduceMotion ? nil : .timingCurve(0.16, 1, 0.3, 1, duration: editing ? 0.32 : 0.5), value: editing)
                    .allowsHitTesting(!editing)
            }
        }
        .padding(.horizontal, 22)
        .padding(.top, (cover ? heroInset + 44 : 25) + coverExtra / 2)
        .padding(.bottom, (cover && !alone ? 47 : 25) + coverExtra / 2)
        .frame(maxWidth: .infinity,
               minHeight: (cover && alone) ? UIScreen.main.bounds.height * 0.66 : nil,
               alignment: Alignment(horizontal: hAlign, vertical: (cover && alone) ? .center : .top))
        .animation(reduceMotion ? nil : .timingCurve(0.2, 0, 0, 1, duration: 0.28), value: alone)
        .background {
            if let accent { shape.fill(accent) }
            else { shape.fill(LinearGradient(colors: [Color(argb: 0xFF18181A), LiquidGlassTokens.contentColor], startPoint: .top, endPoint: .bottom)) }
        }
        .overlay { if r.isImportant { shape.strokeBorder(Color.white.opacity(0.85), lineWidth: 1.25) } }
        .background { if cover { GeometryReader { g in Color.clear.onAppear { coverTitleHeight = g.size.height }.onChange(of: g.size.height) { _, h in coverTitleHeight = h } } } }
        .overlay(alignment: .bottom) { if editing && cover && !alone { coverTitleHandle(dark: dark, extra: coverExtra) } }
        .overlay(alignment: .bottomTrailing) { titleTools(stored: storedText, alignment: alignKey, cover: cover) }
        .onChange(of: editing) { _, on in
            if !on { showTitleTools = false }
            replayTitle()
        }
        .onChange(of: alone) { _, _ in replayTitle() }
    }

    /// The cover sits outside the grid, so it has its own handle: from its natural height up to a lone title's.
    private func coverTitleHandle(dark: Bool, extra: CGFloat) -> some View {
        Capsule().fill(dark ? Color.black.opacity(0.85) : Color.white.opacity(0.85))
            .frame(width: 36, height: 4)
            .padding(4)
            .frame(maxWidth: .infinity, alignment: .bottom)
            .frame(height: 30, alignment: .bottom)
            .contentShape(Rectangle())
            .padding(.horizontal, 56)
            .highPriorityGesture(DragGesture(minimumDistance: 2, coordinateSpace: .global).onChanged { v in
                if coverDragStart == nil {
                    lowerEditDock()
                    let lone = UIScreen.main.bounds.height * 0.66
                    coverDragStart = (extra, max(lone - (coverTitleHeight - extra), 0))
                }
                guard let start = coverDragStart else { return }
                let height = detailHeroMinHeight + Int(min(max(start.extra + v.translation.height, 0), start.limit))
                var p = (draft?.contentBlockLayout["text"] ?? CardBlockPlacement()).normalized
                guard p.minHeight != height else { return }
                p.span = CardBlockPlacement.units
                p.column = 0
                p.minHeight = height
                draft?.contentBlockLayout["text"] = p.normalized
            }.onEnded { _ in coverDragStart = nil })
            .accessibilityLabel(t("Yüksekliği değiştir"))
    }

    /// Resizing needs the whole card in view: the dock and "Renk ve kategori" step aside.
    private func lowerEditDock() {
        withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.22)) {
            secondaryExpanded = false
            dockCollapsed = true
        }
    }

    /// Blank at once, then the way Yaşam's sentence opens; the size change happens while nothing shows.
    private func replayTitle() {
        guard !reduceMotion else { return }
        var instant = Transaction()
        instant.disablesAnimations = true
        withTransaction(instant) { titleShown = false }
        DispatchQueue.main.async {
            withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.5)) { titleShown = true }
        }
    }

    /// Aa at the foot of the title block while editing; its pills float there and a tap elsewhere on the block closes them.
    @ViewBuilder
    private func titleTools(stored: CardBlockPlacement?, alignment: String, cover: Bool) -> some View {
        if editing {
            if showTitleTools {
                let row = HStack(spacing: 4) {
                    titleTool(t("Sola"), active: alignment == "start") { setTitlePlacement(cover: cover) { $0.textAlignment = "start" } }
                    titleTool(t("Ortala"), active: alignment == "center") { setTitlePlacement(cover: cover) { $0.textAlignment = "center" } }
                    titleTool(t("Sağa"), active: alignment == "end") { setTitlePlacement(cover: cover) { $0.textAlignment = "end" } }
                    titleTool(t("Kalın"), active: stored?.textBold == true, bold: true) { setTitlePlacement(cover: cover) { $0.textBold.toggle() } }
                    titleTool(t("İtalik"), active: stored?.textItalic == true, italic: true) { setTitlePlacement(cover: cover) { $0.textItalic.toggle() } }
                }
                .padding(4)
                .background(Capsule().fill(Color(argb: 0xFF171719)))
                .overlay(Capsule().strokeBorder(Color.white.opacity(0.12), lineWidth: 0.8))
                ZStack(alignment: .bottom) {
                    Color.clear.contentShape(Rectangle()).onTapGesture { showTitleTools = false }
                    ViewThatFits(in: .horizontal) {
                        row
                        ScrollView(.horizontal, showsIndicators: false) { row }
                    }
                    .padding(.horizontal, 8)
                    .padding(.bottom, 10)
                }
            } else {
                Button { showTitleTools = true } label: {
                    Text("Aa").font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                        .frame(width: 36, height: 28)
                        .background(Capsule().fill(Color(argb: 0xFF343439)))
                        .overlay(Capsule().strokeBorder(Color.white.opacity(0.25), lineWidth: 0.8))
                        .frame(width: 48, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(t("Metin araçları"))
                .padding(.bottom, 4)
                .padding(.trailing, 4)
            }
        }
    }

    private func titleTool(_ label: String, active: Bool, bold: Bool = false, italic: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(RealFont.inter(12, bold ? .bold : (active ? .semibold : .regular)))
                .italic(italic)
                .foregroundStyle(active ? Color.white : Color(argb: 0xFFA5A5AD))
                .padding(.horizontal, 11)
                .frame(height: 36)
                .background(Capsule().fill(Color(argb: active ? 0xFF343439 : 0xFF171719)))
                .overlay(Capsule().strokeBorder(Color.white.opacity(active ? 0.35 : 0.12), lineWidth: 0.8))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(active ? .isSelected : [])
    }

    /// A title nobody has placed stays the cover when its first style is stored.
    private func setTitlePlacement(cover: Bool, _ change: (inout CardBlockPlacement) -> Void) {
        var placement = draft?.contentBlockLayout["text"] ?? { () -> CardBlockPlacement in
            var fresh = CardBlockPlacement()
            if cover {
                fresh.minHeight = detailHeroMinHeight
                fresh.textAlignment = "center"
            }
            return fresh
        }()
        change(&placement)
        draft?.contentBlockLayout["text"] = placement.normalized
    }

    /// Status view for every non-compact title block in reading mode (kept with opacity 0 while editing).
    @ViewBuilder
    private func statusArea(_ r: ReminderItem, hAlign: HorizontalAlignment, frameAlign: Alignment, textAlign: TextAlignment) -> some View {
        let category = store.category(r.customCategoryId)
        let dark = r.autoDarkText
        let grayColor = dark ? Color(argb: 0xFF101010).opacity(0.68) : Color(argb: 0xFFA5A5B0)
        let blueColor = dark ? Color(argb: 0xFF101010) : Color(argb: 0xFF94B8F7)
        let overdueColor = Color(argb: 0xFFF87171)
        let dividerColor = dark ? Color(argb: 0xFF101010).opacity(0.20) : Color(argb: 0xFF38383F)
        VStack(alignment: hAlign, spacing: 0) {
            if !r.isCompleted, let alert = r.alertAt {
                let d = alert.date
                let lead = "\(t(Calendar.real.isoWeekday(of: d).full)) \(detailTime(d))"
                let leadColor = alert < EpochMillis.now ? overdueColor : blueColor
                let tail = detailPlannedTail(r, date: d)
                (Text(lead).font(RealFont.inter(15, .medium)).foregroundColor(leadColor)
                 + Text(" · " + tail).font(RealFont.inter(15)).foregroundColor(grayColor))
                    .multilineTextAlignment(textAlign)
                    .frame(maxWidth: .infinity, alignment: frameAlign)
                    .padding(.top, 10)
                // Çakışma: on the card only an amber edge and time; here it says with what.
                DetailConflictLine(reminder: r, others: store.reminders + CalendarCards.upcoming(days: 30).map(CalendarCards.card))
                    .frame(maxWidth: .infinity, alignment: frameAlign)
            } else if !r.isCompleted, let epochDay = r.plannedDateEpochDay {
                let d = Calendar.real.startOfDay(epochDay: epochDay)
                let weekday = t(Calendar.real.isoWeekday(of: d).full)
                let day = detailDay(d)
                (Text(weekday).font(RealFont.inter(15, .medium)).foregroundColor(blueColor)
                 + Text(" · " + day).font(RealFont.inter(15)).foregroundColor(grayColor))
                    .multilineTextAlignment(textAlign)
                    .frame(maxWidth: .infinity, alignment: frameAlign)
                    .padding(.top, 10)
            } else if let status = statusText(r, hasCategory: category != nil) {
                Text(status)
                    .font(RealFont.inter(13))
                    .foregroundStyle(grayColor)
                    .lineLimit(2)
                    .multilineTextAlignment(textAlign)
                    .frame(maxWidth: .infinity, alignment: frameAlign)
                    .padding(.top, 4)
            }
            if let trigger = r.locationTrigger, !r.isCompleted {
                let tone = trigger.onExit ? Color(argb: 0xFFFFB86F) : Color(argb: 0xFF5DCAA5)
                HStack(spacing: 6) {
                    PlaceTriggerGlyph(onExit: trigger.onExit).frame(width: 12, height: 12)
                    Text((trigger.onExit ? t("Ayrılınca · ") : t("Varınca · ")) + trigger.placeName)
                        .font(RealFont.inter(13, .medium))
                        .lineLimit(1)
                        .foregroundStyle(tone)
                }
                .frame(maxWidth: .infinity, alignment: frameAlign)
                .padding(.top, 8)
            }
            if !r.cardPeople.isEmpty || category != nil {
                Rectangle()
                    .fill(dividerColor)
                    .frame(height: 1)
                    .padding(.top, 14)
                    .padding(.bottom, 10)
                HStack(alignment: .center, spacing: 10) {
                    Group {
                        if !r.cardPeople.isEmpty {
                            CardDetailPeople(reminder: r, color: dark ? Color(argb: 0xFF101010) : Color(argb: 0xFFF4F4F6), session: shareSession)
                        } else {
                            Color.clear.frame(height: 1)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    if let category {
                        HStack(spacing: 6) {
                            categoryBadge(category, size: 16)
                            Text(category.name)
                                .font(RealFont.inter(13, .medium))
                                .foregroundStyle(dark ? Color(argb: 0xFF101010) : Color(argb: 0xFFE2E2E8))
                                .lineLimit(1)
                        }
                        .frame(maxWidth: 170, alignment: .trailing)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: frameAlign)
    }

    private func detailTime(_ d: Date) -> String {
        if I18n.isEnglish {
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.calendar = .real
            f.dateFormat = "h:mm a"
            return f.string(from: d).lowercased()
        }
        return RealDates.time(d)
    }

    private func detailDay(_ d: Date) -> String {
        let cal = Calendar.real
        let today = cal.epochDay(of: Date())
        let target = cal.epochDay(of: d)
        if target == today { return t("Bugün") }
        if target == today + 1 { return t("Yarın") }
        let dayNum = cal.component(.day, from: d)
        let monthFormatter = DateFormatter()
        monthFormatter.calendar = .real
        monthFormatter.timeZone = Calendar.real.timeZone
        monthFormatter.locale = I18n.locale
        monthFormatter.setLocalizedDateFormatFromTemplate("MMM")
        let month = monthFormatter.string(from: d)
        let year = cal.component(.year, from: d)
        let currentYear = cal.component(.year, from: Date())
        return year == currentYear ? "\(dayNum) \(month)" : "\(dayNum) \(month) \(year)"
    }

    private func detailPlannedTail(_ r: ReminderItem, date: Date) -> String {
        var parts = [detailDay(date)]
        if let dur = r.durationMinutes, dur > 0 {
            parts.append(t("{0} dk", dur))
        } else if let end = r.endAt {
            parts.append(t("Bitiş: {0}", detailTime(end.date)))
        }
        if let rep = repeatSummary(r) {
            parts.append(rep)
        }
        return parts.joined(separator: " · ")
    }

    private func statusText(_ r: ReminderItem, hasCategory: Bool) -> String? {
        if r.isCompleted { return t("Tamamlandı") }
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
        case .daily: return t("Her gün tekrarlanacak")
        case .weekly: return t("Her {0} tekrarlanacak", t(cal.isoWeekday(of: d).full))
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
            return t("Her ayın {0}{1} tekrarlanacak", day, suffix)
        case .yearly: return t("Her yıl {0} {1} tekrarlanacak", cal.component(.day, from: d), t(turkishMonths[cal.component(.month, from: d) - 1]))
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

    static func shortDateTime(_ millis: EpochMillis) -> String {
        let d = millis.date
        return "\(Calendar.real.component(.day, from: d)) " + d.formatted(.dateTime.month(.abbreviated).locale(I18n.locale)) + " " + RealDates.time(d)
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
                        chip("bell", t("{0} önce", EarlyAlertScreen.label(early)), selected: false) { openSchedule(r) }
                    }
                    if r.alertAt != nil {
                        chip("note.text", r.durationMinutes.map { t("Süre: {0} min", $0) } ?? t("Süre ekle"), selected: false) {
                            let options = [nil, 15, 30, 45, 60, 90, 120]
                            let i = options.firstIndex(of: r.durationMinutes) ?? 0
                            draft?.durationMinutes = options[(i + 1) % options.count]
                        }
                    }
                    HStack(spacing: 4) {
                        chip("calendar", r.endAt.map { t("Bitiş: {0}", Self.shortDateTime($0)) } ?? t("+ Bitiş"), selected: false) { openSchedule(r) }
                        if r.endAt != nil {
                            Button { draft?.endAt = nil } label: {
                                RealSymbol("xmark", size: 11).foregroundStyle(Color(argb: 0xFFA7A7AF)).frame(width: 24, height: 24)
                            }
                            .accessibilityLabel(t("Bitişi kaldır"))
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
            .accessibilityLabel(t("Tarih ve saati kaldır"))
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
        let summary = items.isEmpty ? "" : (done > 0 ? t("{0} madde • {1} tamamlandı", items.count, done) : t("{0} madde", items.count))
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(t("Checklist")).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFB7B7C0))
                if !summary.isEmpty { Text("  •  \(summary)").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA5A5B5)).lineLimit(1) }
                Spacer()
                if editing {
                    Button {
                        draft?.checklistItems = []
                        draft?.isChecklist = false
                        checklistOpen = false
                        checklistFocus = nil
                        pendingChecklistFocus = nil
                    } label: { RealSymbol("xmark", size: 13.8).foregroundStyle(Color(argb: 0xFF8E8E98)).frame(width: 28, height: 28) }
                    .accessibilityLabel(t("Checklist kutusunu kaldır"))
                }
            }
            .padding(.horizontal, 4).padding(.vertical, 2)
            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
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
                        }), prompt: Text(t("Madde {0}", index + 1)).foregroundStyle(Color(argb: 0xFF6C6C76)))
                        .font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFF1F1F5))
                        .focused($checklistFocus, equals: .item(item.id))
                        .submitLabel(.next)
                        .onSubmit { focusNextChecklist(after: item.id) }
                        .onAppear { focusPendingChecklistIfNeeded(item.id) }
                        Button {
                            draft?.checklistItems.removeAll { $0.id == item.id }
                            if pendingChecklistFocus == .item(item.id) { pendingChecklistFocus = nil }
                        } label: {
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
                    TextField("", text: $newChecklistText, prompt: Text(t("Madde ekle")).foregroundStyle(Color(argb: 0xFF6C6C76)))
                        .font(RealFont.inter(15))
                        .focused($checklistFocus, equals: .add)
                        .submitLabel(.next)
                        .onSubmit {
                            let text = newChecklistText.trimmingCharacters(in: .whitespaces)
                            guard !text.isEmpty else { return }
                            draft?.checklistItems.append(ChecklistItem(id: nextChecklistItemId(), text: text))
                            draft?.isChecklist = true
                            newChecklistText = ""
                            checklistFocus = .add
                        }
                }
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardContentChrome(corners: 22)
    }

    private func nextChecklistItemId() -> Int64 {
        max(EpochMillis.now, (draft?.checklistItems.map(\.id).max() ?? 0) + 1)
    }

    /// Keyboard "next" in an item: the next row, or the "add item" row after the last one.
    private func focusNextChecklist(after id: Int64) {
        guard let items = draft?.checklistItems,
              let index = items.firstIndex(where: { $0.id == id }) else { return }
        checklistFocus = index + 1 < items.count ? .item(items[index + 1].id) : .add
    }

    /// A first item created by the Checklist button is focused once SwiftUI has put its row on screen, so typing
    /// goes into the item instead of staying in the card title.
    private func focusPendingChecklistIfNeeded(_ id: Int64) {
        guard pendingChecklistFocus == .item(id) else { return }
        pendingChecklistFocus = nil
        DispatchQueue.main.async { checklistFocus = .item(id) }
    }

    private func photoBlock(_ a: ReminderAttachment, heroInset: CGFloat = 0) -> some View {
        PhotoBlockBody(attachment: a, editing: editing, heroInset: heroInset, onTap: { previewPhoto = a }, remove: { removeAttachment(a) },
                       onFrame: { scale, x, y in
            if editing {
                guard let i = draft?.attachments.firstIndex(where: { $0.id == a.id }) else { return }
                draft?.attachments[i].photoScale = scale
                draft?.attachments[i].photoX = x
                draft?.attachments[i].photoY = y
            } else {
                guard var card = store.reminder(reminderId),
                      let i = card.attachments.firstIndex(where: { $0.id == a.id }) else { return }
                card.attachments[i].photoScale = scale
                card.attachments[i].photoX = x
                card.attachments[i].photoY = y
                card.updatedAt = .now
                store.update(card)
            }
        })
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
                Text(a.displayName ?? t("Dosya")).font(RealFont.inter(14, .medium)).foregroundStyle(.white).lineLimit(1)
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

    private func locationBlock(_ l: ReminderLocation, card: ReminderItem) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if let lat = l.latitude, let lon = l.longitude {
                // Yol: within an hour of the card's time, the driving route from here (taller map).
                CardRouteMap(location: l, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                             routeWanted: !editing && !card.isCompleted && card.alertAt.map { (0...3_600_000).contains($0 - EpochMillis.now) } == true)
                // While editing the map does not pan, so a hold on it lifts the block.
                .allowsHitTesting(!editing)
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
                    Button(t("Haritada aç")) { openInMaps(l) }.font(RealFont.inter(13, .medium)).foregroundStyle(RealColors.accentBlue)
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
                Text(t("Günlük")).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFB7B7C0))
                Spacer()
                if let mood = j.mood { Text("\(mood.emoji) \(mood.label)").font(RealFont.inter(12)).foregroundStyle(RealColors.subtext) }
            }
            if editing {
                TextField(t("Günlük notu"), text: Binding(get: { draft?.journal?.text ?? "" }, set: { v in
                    guard let old = draft?.journal else { return }
                    // No format bar here: the styles just follow the edit.
                    draft?.journal?.styles = BodySpans.applyEdit(old.styles, old: old.text, new: v, typingMarks: [])
                    draft?.journal?.text = v; draft?.journal?.updatedAt = .now
                }), axis: .vertical)
                    .font(RealFont.inter(15)).lineLimit(2...12)
            } else if j.text.isEmpty {
                Text(t("Günlük notu")).font(RealFont.inter(15)).foregroundStyle(RealColors.textMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                StyledBodyText(text: j.text, styles: j.styles, foreground: Color(argb: 0xFFE9E9EF), size: 15)
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
                Text(t("Mood")).font(RealFont.inter(12)).foregroundStyle(tint)
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
        .accessibilityLabel(t("Kaldır"))
    }

    // MARK: Bottom column

    private func bottomColumn(_ r: ReminderItem) -> some View {
        VStack(spacing: 0) {
            if deleting { deletePrompt(r).erased().padding(.bottom, 12).transition(.move(edge: .bottom).combined(with: .opacity)) }
            if editing {
                if secondaryExpanded { secondaryPanels.erased().padding(.bottom, 12).transition(.opacity.combined(with: .scale(scale: 0.97))) }
                Group {
                    if dockCollapsed {
                        // Lowered dock: a slim glass bar that still holds the handle.
                        dockHandle
                            .padding(.horizontal, 14).padding(.vertical, 6)
                            .liquidGlass(RoundedRectangle(cornerRadius: 26, style: .continuous))
                            .transition(.opacity)
                    } else {
                        editDock.erased().transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }
                .padding(.bottom, 10)
            } else if r.isCompleted && !r.hasCompletionNote {
                // The Günlük ekle panel, now with Not ekle under it; both open a new card at the end of the stack.
                VStack(spacing: 0) {
                    completionRow(t("Günlük ekle"), t("Nasıl geçti? Aklında kalanı yaz.")) { addCompletionJournal(t("Nasıl geçti?")) }
                    Rectangle().fill(.white.opacity(0.08)).frame(height: 0.7).padding(.horizontal, 20)
                    completionRow(t("Not ekle"), t("Sonuç, öğrendiğin, sıradaki adım…")) { addCompletionJournal(t("Not")) }
                }
                .liquidGlass(RoundedRectangle(cornerRadius: 22, style: .continuous), role: .chrome)
                .padding(.bottom, 12)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            actionBar(r)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 2) // Android: bar bottom ~36dp above the screen edge
        .offset(y: blockMoving ? 220 : 0)
        .opacity(blockMoving ? 0 : 1)
        .allowsHitTesting(!blockMoving)
        .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.24), value: editing)
        .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.24), value: secondaryExpanded)
        .animation(.easeOut(duration: 0.2), value: deleting)
    }

    /// Tamamla · Düzenle · Share · Sil, or Geri al · … once completed. After Tamamla the Geri al set comes in as the
    /// mirrored sweep passes (from the left); after Kaydet as Kaydet's sweep passes (from the right).
    private func mainActions(_ r: ReminderItem, done: Bool) -> some View {
        let visible = !editing && inlineStack == nil && r.isCompleted == done && dockShown
        let fromLeft = done && completeSweepStart.map { Date().timeIntervalSince($0) < DetailDockMotion.duration } == true
        let start = fromLeft ? completeSweepStart : saveSweepStart
        func delay(_ i: Int) -> Double { DetailDockMotion.enterDelay(i, of: 4, sweepStart: start, fromLeft: fromLeft) }
        return HStack(spacing: 0) {
            if done {
                DetailAction(label: t("Geri al"), symbol: "arrow.uturn.backward", enabled: !deleting) {
                    withAnimation { store.toggleCompleted(reminderId) }
                }
                .detailDockItem(visible: visible, enterDelay: delay(0), reduceMotion: reduceMotion)
            } else {
                DetailAction(label: t("Tamamla"), symbol: "checkmark", enabled: !deleting) {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    if !reduceMotion { completeSweepStart = Date() }
                    withAnimation { store.toggleCompleted(reminderId) }
                }
                .detailDockItem(visible: visible, enterDelay: delay(0), reduceMotion: reduceMotion)
            }
            DetailAction(label: t("Düzenle"), symbol: "pencil", enabled: !deleting) {
                dockCollapsed = false   // every edit starts with the dock raised
                draft = r
                RealTips.shared.show("detail_edit_dock")
                categorySelection = r.customCategoryId
                checklistOpen = !r.checklistItems.isEmpty
            }
            .detailDockItem(visible: visible, enterDelay: delay(1), reduceMotion: reduceMotion)
            Button { RealTips.shared.gate("detail_share") { showCardShare = true } } label: {
                VStack(spacing: 1) {
                    RealSymbol("square.and.arrow.up", size: 20.7)
                    Text(t("Paylaş")).font(RealFont.inter(10))
                }
                .foregroundStyle(Color(argb: 0xFFF0F0F4))
                .frame(maxWidth: .infinity, minHeight: 56)
            }
            .buttonStyle(.plain)
            .disabled(deleting)
            .detailDockItem(visible: visible, enterDelay: delay(2), reduceMotion: reduceMotion)
            DetailAction(label: t("Sil"), symbol: "trash") { deleting.toggle() }
                .detailDockItem(visible: visible, enterDelay: delay(3), reduceMotion: reduceMotion)
        }
    }

    private var dockShown: Bool { dockFormed || !fromHome || reduceMotion }

    /// Both sets of actions stay laid out on top of each other; DetailDockMotion blurs one out and the other in.
    private func actionBar(_ r: ReminderItem) -> some View {
        ZStack {
            HStack(spacing: 0) {
                DetailAction(label: t("Vazgeç"), symbol: "xmark") { draft = nil; secondaryExpanded = false; checklistOpen = false }
                    .detailDockItem(visible: editing && dockShown, enterDelay: DetailDockMotion.itemInDelay, reduceMotion: reduceMotion)
                DetailAction(label: t("Kaydet"), symbol: "checkmark", color: Color(argb: 0xFF91B9FF),
                             enabled: !(draft?.text.trimmingCharacters(in: .whitespaces).isEmpty ?? true)) {
                    if !reduceMotion { saveSweepStart = Date() }
                    saveDraft()
                }
                .detailDockItem(visible: editing && dockShown, enterDelay: DetailDockMotion.itemInDelay, reduceMotion: reduceMotion)
            }
            DetailAction(label: t("Bitti"), symbol: "checkmark", color: Color(argb: 0xFF91B9FF)) { finishInlineStack() }
                .detailDockItem(visible: inlineStack != nil && dockShown, enterDelay: DetailDockMotion.itemInDelay, reduceMotion: reduceMotion)
            // Tamamla · … and Geri al · …: two sets on top of each other, so Tamamla swaps the whole dock like Kaydet.
            mainActions(r, done: false)
            mainActions(r, done: true)
        }
        .padding(.horizontal, 6)
        .background {
            ZStack {
                DetailDockDrops(slots: inlineStack != nil ? 1 : editing ? 2 : 4, sweepStart: saveSweepStart, completeStart: completeSweepStart)
                DetailDockSweep(start: saveSweepStart)
                DetailDockSweep(start: completeSweepStart, fromLeft: true)
            }
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        }
        // The capsule sits between home's two side circles; a beat after the card opens it widens, the buttons blur in.
        .background {
            Color.clear
                .liquidGlass(RoundedRectangle(cornerRadius: 28, style: .continuous))
                .padding(.horizontal, dockShown ? 0 : 48)
                .padding(.vertical, dockShown ? 0 : 5)
        }
        .task {
            guard !dockShown else { return }
            try? await Task.sleep(for: .milliseconds(160))
            withAnimation(.timingCurve(0.2, 0, 0, 1, duration: 0.34)) { dockFormed = true }
        }
        .onDisappear { finishInlineStack() }
    }

    /// DeleteReminderPrompt: plain delete, or "only this / this and future" for a repeating card.
    private func deletePrompt(_ r: ReminderItem) -> some View {
        let occurrence = Recurrence.nextDeletableOccurrence(r, now: .now)
        let scoped = r.hasRepeat && occurrence != nil
        return VStack(alignment: .leading, spacing: 10) {
            Text(scoped ? t("Tekrarlayan etkinlik silinsin mi?") : t("Hatırlatıcı silinsin mi?"))
                .font(RealFont.inter(17, .semibold)).lineSpacing(6).foregroundStyle(Color(argb: 0xFFF5F5F8))
            Text(scoped ? t("Yalnızca sıradaki tekrarı ya da sıradaki tekrardan itibaren tüm tekrarları silebilirsin. Önceki tekrarlar korunur. Bu işlem geri alınamaz.")
                 : t("Bu not listenden ve bildirim panelinden kaldırılacak. Bu işlem geri alınamaz."))
                .font(RealFont.inter(13)).lineSpacing(6).foregroundStyle(Color(argb: 0xFFBEBEC8))
            // DeleteReminderPrompt.kt: 46pt pill buttons — red tint for deleting, a light one for Vazgeç.
            if scoped, let occurrence {
                VStack(spacing: 8) {
                    deletePill(t("Sadece sıradaki tekrarı sil"), destructive: true) { deleting = false; store.deleteOccurrence(reminderId, occurrence: occurrence) }
                    deletePill(t("Bu ve sonraki tüm tekrarları sil"), destructive: true) { deleting = false; store.deleteThisAndFuture(reminderId, occurrence: occurrence) }
                    deletePill(t("Vazgeç"), destructive: false) { deleting = false }
                }
                .padding(.top, 4)
            } else {
                HStack(spacing: 10) {
                    deletePill(t("Vazgeç"), destructive: false) { deleting = false }
                    deletePill(t("Sil"), destructive: true) { deleting = false; store.delete([reminderId]) }
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
            .accessibilityLabel(t("Düzenleme dock'u"))
            .accessibilityValue(dockCollapsed ? t("Küçültülmüş") : t("Açık"))
            .accessibilityAddTraits(.isButton)
    }

    private var editDock: some View {
        VStack(alignment: .leading, spacing: 0) {
            dockHandle.padding(.top, -4)
            HStack {
                Button { titleFocused = false; secondaryExpanded.toggle() } label: {
                    HStack(spacing: 6) {
                        Text(t("Renk ve kategori")).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFB7B7C0))
                        RealSymbol("chevron.right", size: 12.6).foregroundStyle(Color(argb: 0xFF8E8E98))
                            .rotationEffect(.degrees(secondaryExpanded ? 90 : 0))
                    }
                    .padding(.horizontal, 4).padding(.vertical, 8)
                }
                Spacer()
                Button { reordering = true } label: {
                    RealSymbol("arrow.up.arrow.down", size: 19.5).foregroundStyle(Color(argb: 0xFFD2D2DA)).frame(width: 40, height: 40)
                }
                .accessibilityLabel(t("Sırala"))
            }
            .padding(.horizontal, 2)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 4) {
                    let d = draft
                    // Most used first, least used last (Android order).
                    QuickAction(symbol: "calendar", label: t("Tarih / Saat"), active: d?.alertAt != nil || d?.plannedDateEpochDay != nil) {
                        if let d { openSchedule(d) }
                    }
                    QuickAction(symbol: "photo", label: t("Fotoğraf ekle"), active: false,
                                count: d?.attachments.filter { $0.type == .image }.count) { showPhotoPicker = true }
                    QuickAction(symbol: "camera", label: t("Kamera ile fotoğraf ekle"), active: false) { Task { if await PermissionPrompt.cameraReady() { showCamera = true } } }
                    QuickAction(symbol: "checklist", label: t("Checklist"), active: checklistOpen || !(d?.checklistItems.isEmpty ?? true),
                                count: d?.checklistItems.count) {
                        titleFocused = false
                        checklistOpen = true
                        draft?.isChecklist = true
                        let items = draft?.checklistItems ?? []
                        if items.isEmpty {
                            let id = nextChecklistItemId()
                            draft?.checklistItems = [ChecklistItem(id: id, text: "")]
                            pendingChecklistFocus = .item(id)
                        } else if let first = items.first {
                            checklistFocus = .item(first.id)
                        }
                    }
                    QuickAction(symbol: "rectangle.stack", label: t("Kart Yığını"), active: d?.cardStack != nil, count: d?.cardStack?.cards.count, tip: "detail_stack") {
                        addStackCard()
                        RealTips.shared.show("detail_stack_use")
                    }
                    QuickAction(symbol: "mappin", label: t("Konum"), active: d?.location != nil, tip: "detail_location") { withoutAnimation { showLocationGuide = true } }
                    QuickAction(symbol: "waveform", label: t("Ses kaydı"), active: voiceRecorderOpen || !(d?.voiceRecordings.isEmpty ?? true),
                                count: d?.voiceRecordings.count) { withAnimation(.spring(duration: 0.3)) { voiceRecorderOpen = true } }
                    let mediaTotal = (d?.musicBlocks.count ?? 0) + (d?.mediaBlocks.count ?? 0) + (d?.gameBlocks.count ?? 0)
                    QuickAction(symbol: "play.rectangle", label: t("Medya"), active: mediaTotal > 0,
                                count: mediaTotal > 0 ? mediaTotal : nil, tip: "detail_media") { showMusicSearch = true }
                    QuickAction(symbol: "doc", label: t("Dosya ekle"), active: false,
                                count: d?.attachments.filter { $0.type == .file }.count) { showFileImporter = true }
                    QuickAction(symbol: "face.smiling", label: t("Mood"), active: d?.mood != nil) { showMoodPicker = true }
                    // Real Labs: experimental extras after a divider, as on Android.
                    Rectangle().fill(.white.opacity(0.14)).frame(width: 1, height: 22).padding(.horizontal, 6)
                    Text("Real Labs").font(RealFont.inter(11)).foregroundStyle(.white.opacity(0.45)).fixedSize().padding(.trailing, 2)
                    QuickAction(symbol: "map", label: t("Harita posteri"), active: false) { openPoster { showMapPoster = true } }
                    QuickAction(symbol: "sparkles", label: t("Gökyüzü posteri"), active: false) { openPoster { showSkyPoster = true } }
                }
                .padding(.leading, 2).padding(.trailing, 12)
                .offset(x: iconsPeek)
            }
            // Hint (Android): shortly after the icons appear they slide a little left and back, twice.
            .task {
                guard !reduceMotion else { return }
                try? await Task.sleep(for: .milliseconds(650))
                for _ in 0..<2 {
                    withAnimation(.timingCurve(0.2, 0, 0, 1, duration: 0.42)) { iconsPeek = -36 }
                    try? await Task.sleep(for: .milliseconds(420))
                    withAnimation(.timingCurve(0.2, 0, 0, 1, duration: 0.48)) { iconsPeek = 0 }
                    try? await Task.sleep(for: .milliseconds(700))
                }
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
                    Text(t("Kategori")).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFB7B7C0))
                    Spacer()
                    if let c = store.category(draft?.customCategoryId) {
                        categoryBadge(c, size: 18)
                        Text(c.name).font(RealType.categoryPill).foregroundStyle(.white)
                    } else {
                        Text(t("Kategori yok")).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF8E8E98))
                    }
                    RealSymbol("chevron.right", size: 13.8).foregroundStyle(Color(argb: 0xFF8E8E98))
                }
                .padding(.horizontal, 16).padding(.vertical, 13)
                .liquidGlass(RoundedRectangle(cornerRadius: 22, style: .continuous), role: .sheet)
            }
        }
    }

    // MARK: Actions

    /// Same check as Kaydet: an empty text keeps the editor open.
    private func saveFromBackgroundTap() {
        guard editing, !(draft?.text.trimmingCharacters(in: .whitespaces).isEmpty ?? true) else { return }
        saveDraft()
    }

    private func saveDraft() {
        guard var d = draft else { return }
        if !d.isChecklist && !d.checklistItems.isEmpty { d.isChecklist = true }
        if d.checklistItems.isEmpty { d.isChecklist = false }
        if let j = d.journal, j.text.trimmingCharacters(in: .whitespaces).isEmpty, j.mood == nil { d.journal = nil }
        if var stack = d.cardStack {
            stack.cards.removeAll(where: \.isBlank)
            d.cardStack = stack.cards.isEmpty ? nil : stack
        }
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
            guard let result = await ClipboardTrayStore.result(for: item) else { RealToast.shared.show(t("Yapıştırılacak bir şey bulunamadı")); return }
            if editing, var d = draft { CardPaste.apply(result, to: &d); draft = d }
            else { store.mutate(reminderId) { CardPaste.apply(result, to: &$0) } }
            RealToast.shared.show(t("Eklendi · {0}", result.label))
        }
    }

    /// Yapıştır: the copied item becomes blocks on this card (the draft while editing, saved right away otherwise).
    private func paste(_ providers: [NSItemProvider]) {
        Task { @MainActor in
            guard let result = await CardPaste.read(providers) else { RealToast.shared.show(t("Yapıştırılacak bir şey bulunamadı")); return }
            if editing, var d = draft { CardPaste.apply(result, to: &d); draft = d }
            else { store.mutate(reminderId) { CardPaste.apply(result, to: &$0) } }
            ClipboardWatcher.shared.consume()
            RealToast.shared.show(t("Yapıştırıldı · {0}", result.label))
        }
    }

    private func updateSnippet(_ id: String, _ text: String) {
        if editing, let i = draft?.attachments.firstIndex(where: { $0.id == id }) { draft?.attachments[i].text = text }
        else { store.mutate(reminderId) { r in if let i = r.attachments.firstIndex(where: { $0.id == id }) { r.attachments[i].text = text; r.updatedAt = .now } } }
    }

    /// The posters need the card's place; without one, the location guide opens first.
    private func openPoster(_ open: () -> Void) {
        if let l = draft?.location, l.latitude != nil, l.longitude != nil { open() }
        else {
            RealToast.shared.show(t("Poster için önce bir konum seç"))
            withoutAnimation { showLocationGuide = true }
        }
    }

    private func addPoster(_ image: UIImage) {
        guard let data = image.jpegData(compressionQuality: 0.94) else { return }
        addAttachment(AttachmentStorage.save(data))
    }

    /// The sky poster's moment: the card's alert, else its planned day at 21:00, else when it was made.
    private var posterMoment: Int64 {
        let d = draft ?? shown
        if let at = d?.alertAt { return at }
        if let day = d?.plannedDateEpochDay {
            var utc = Calendar(identifier: .gregorian)
            utc.timeZone = TimeZone(identifier: "UTC")!
            let parts = utc.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: Double(day) * 86_400))
            var local = DateComponents(year: parts.year, month: parts.month, day: parts.day, hour: 21)
            local.calendar = Calendar.current
            if let date = local.date { return Int64(date.timeIntervalSince1970 * 1000) }
        }
        return d?.createdAt ?? Int64(Date().timeIntervalSince1970 * 1000)
    }

    private func addAttachment(_ a: ReminderAttachment?) {
        guard let a else { return }
        if editing { draft?.attachments.append(a) } else { store.mutate(reminderId) { $0.attachments.append(a) } }
    }

    private func removeAttachment(_ a: ReminderAttachment) {
        draft?.attachments.removeAll { $0.id == a.id }
    }

    private func updateStack(_ change: (inout CardStackBlock) -> Void) {
        var stack = inlineStack ?? shown?.cardStack ?? CardStackBlock()
        change(&stack)
        let value: CardStackBlock? = stack.cards.isEmpty ? nil : stack
        if editing {
            draft?.cardStack = value
        } else if inlineStack != nil, value != nil {
            inlineStack = value
        } else {
            inlineStack = nil
            store.mutate(reminderId) { $0.cardStack = value }
        }
    }

    /// Completed card: "Nasıl geçti?" card added to the stack and opened for writing.
    private func addCompletionJournal(_ title: String) {
        let card = CardStackCard(id: "completed_journal_\(EpochMillis.now)", title: title, body: "")
        editingStackCard = card
    }

    private func completionRow(_ title: String, _ subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFFD3D3D9))
                    Text(subtitle).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8E8E98))
                }
                Spacer()
                RealSymbol("plus", size: 16.1).foregroundStyle(RealColors.accentBlue)
            }
            .padding(.horizontal, 20).padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
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
    var tip: String? = nil
    let action: () -> Void

    var body: some View { content.realTipAnchor(tip) }

    private var content: some View {
        Button(action: RealTips.gated(tip, action)) {
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
private extension CardStackCard {
    var isBlank: Bool {
        title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && imageUri == nil
    }
}

struct CardStackDeck: View {
    let stack: CardStackBlock
    var editing = false
    /// Typing on the cards without Düzenle.
    var inline = false
    var onEdit: (CardStackCard) -> Void = { _ in }
    var onAdd: () -> Void = {}
    /// The ✕ on the front card while writing (Android's delete on the active card); the last one removes the stack.
    var onDelete: (CardStackCard) -> Void = { _ in }
    /// Changes the latest copy of a card: the text editor stores its text and styles one right after the other.
    var onChange: (String, (inout CardStackCard) -> Void) -> Void = { _, _ in }
    var onStartTyping: () -> Void = {}
    @State private var active = 0
    @State private var selection = NSRange(location: 0, length: 0)
    @State private var pending: Set<BodyMark>?
    /// The card just added: its text opens with the keyboard.
    @State private var addedCard: String?

    private var typing: Bool { editing || inline }
    /// Pinch on the front card's photo (Android CardPhotoZoomState): only the pixels move inside the card, never
    /// the card itself, and nothing is saved; it resets when another card comes to the front.
    @State private var photoScale: CGFloat = 1
    @State private var photoOffset: CGSize = .zero
    @State private var pinchStart: (scale: CGFloat, offset: CGSize)?
    @State private var cardSize: CGSize = .zero

    var body: some View {
        let cards = stack.cards
        let current = min(active, max(cards.count - 1, 0))
        VStack(spacing: Self.peek - deckHeight) {
            if current > 0 {
                card(cards[current - 1], index: current - 1).scaleEffect(0.94).opacity(0.75)
                    .onTapGesture { withAnimation(.easeInOut(duration: 0.39)) { active = current - 1 } }
                    .zIndex(0)
            }
            if !cards.isEmpty {
                card(cards[current], index: current, front: true)
                    .shadow(color: .black.opacity(0.4), radius: 16, y: 6)
                    .onTapGesture { if !typing { onStartTyping() } }
                    .onGeometryChange(for: CGSize.self) { $0.size } action: { cardSize = $0 }
                    .simultaneousGesture(photoPinch, including: cards[current].imageUri != nil ? .all : .subviews)
                    .zIndex(2)
            }
            if current + 1 < cards.count {
                card(cards[current + 1], index: current + 1).scaleEffect(0.94).opacity(0.75)
                    .onTapGesture { withAnimation(.easeInOut(duration: 0.39)) { active = current + 1 } }
                    .zIndex(1)
            }
        }
        .overlay(alignment: .bottom) {
            if typing {
                Button(action: onAdd) {
                    Text(t("+ Kart ekle")).font(RealFont.inter(12, .medium)).foregroundStyle(.white.opacity(0.8))
                        .padding(.horizontal, 14).padding(.vertical, 8).liquidGlass(Capsule(), role: .chrome)
                }
                // Inside the card: below it the grid's resize handle would take the tap.
                .offset(y: -10)
            }
        }
        .padding(.bottom, editing ? 22 : 0)
        .animation(.timingCurve(0.2, 0, 0, 1, duration: 0.28), value: deckHeight)
        .onChange(of: active) { _, _ in
            photoScale = 1; photoOffset = .zero; pinchStart = nil
            selection = NSRange(location: 0, length: 0); pending = nil
        }
        .onChange(of: stack.cards.count) { old, new in
            guard new > old, let last = stack.cards.last else { return }
            addedCard = last.id
            withAnimation(.easeInOut(duration: 0.39)) { active = new - 1 }
        }
    }

    private static let peek: CGFloat = 72

    /// Small until the text needs more; a picture keeps the full size. Every card shares it so the deck stays aligned.
    private var deckHeight: CGFloat {
        let tallest = stack.cards.map { c -> CGFloat in
            if c.imageUri != nil { return 390 }
            let room: CGFloat = typing ? 60 : 0
            if c.body.isEmpty { return 150 + room }
            let lines = c.body.split(separator: "\n", omittingEmptySubsequences: false)
                .reduce(0) { $0 + max(1, Int((Double($1.count) / 34).rounded(.up))) }
            return 150 + room + CGFloat(lines) * c.bodyFormat.size.points * 1.4
        }.max() ?? 190
        return min(max(tallest, 190), 780)
    }

    /// Two fingers zoom around the pinch point (1×–4×); one finger stays free to scroll or lift the block.
    private var photoPinch: some Gesture {
        MagnifyGesture()
            .onChanged { v in
                let start = pinchStart ?? (scale: photoScale, offset: photoOffset)
                if pinchStart == nil { pinchStart = start }
                let scale = min(max(start.scale * v.magnification, 1), 4)
                let k = scale / start.scale
                // Keep the photo point under the fingers still: offset' = d + (offset − d)·k, d = anchor − centre.
                let dx = (v.startAnchor.x - 0.5) * cardSize.width, dy = (v.startAnchor.y - 0.5) * cardSize.height
                let maxX = cardSize.width * (scale - 1) / 2, maxY = cardSize.height * (scale - 1) / 2
                photoScale = scale
                photoOffset = CGSize(width: min(max(dx + (start.offset.width - dx) * k, -maxX), maxX),
                                     height: min(max(dy + (start.offset.height - dy) * k, -maxY), maxY))
            }
            .onEnded { _ in pinchStart = nil }
    }

    private func card(_ c: CardStackCard, index: Int, front: Bool = false) -> some View {
        let color = c.colorArgb.map { Color(argbInt: $0) } ?? Color(argb: 0xFF292B2D)
        let hasImage = c.imageUri != nil
        let dark = hasImage || luminance(color) < 0.48
        let fg: Color = dark ? .white : Color(argb: 0xFF161616)
        let shape = RoundedRectangle(cornerRadius: 30, style: .continuous)
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(t("KART 0{0}", index + 1))
                    .font(RealFont.inter(10)).kerning(1.2).foregroundStyle(fg.opacity(0.9))
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(Capsule().fill(hasImage ? Color.black.opacity(0.36) : .clear))
                    .overlay(Capsule().strokeBorder(fg.opacity(0.55), lineWidth: 1))
                Spacer()
                if stack.cards.count > 1 {
                    Text("\(index + 1)/\(stack.cards.count)").font(RealFont.inter(11)).foregroundStyle(fg.opacity(0.6))
                }
                // Colour, picture, writing settings and removing the card stay in the sheet.
                if front && typing {
                    Button { onEdit(c) } label: {
                        Text("Aa").font(RealFont.inter(13, .medium)).foregroundStyle(fg)
                            .frame(width: 36, height: 28)
                            .overlay(Capsule().strokeBorder(fg.opacity(0.4), lineWidth: 0.8))
                            .frame(width: 44, height: 40)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(t("Metin araçları"))
                    Button { onDelete(c) } label: {
                        RealSymbol("xmark", size: 12.6).foregroundStyle(fg.opacity(0.85))
                            .frame(width: 28, height: 28)
                            .overlay(Circle().strokeBorder(fg.opacity(0.4), lineWidth: 0.8))
                            .frame(width: 40, height: 40)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(t("Kartı sil"))
                }
            }
            Spacer(minLength: 0)
            if front && typing {
                TextField("", text: cardField(c, \.title), prompt: Text(t("Başlık")).foregroundStyle(fg.opacity(0.4)), axis: .vertical)
                    .font(RealFont.inter(28)).kerning(-0.6).foregroundStyle(fg).tint(fg)
                    .lineLimit(1...3)
                ZStack(alignment: .topLeading) {
                    if c.body.isEmpty {
                        Text(t("Metin")).font(RealFont.inter(c.bodyFormat.size.points)).foregroundStyle(fg.opacity(0.4)).allowsHitTesting(false)
                    }
                    RichBodyEditor(text: cardField(c, \.body), styles: cardField(c, \.bodyStyles), selection: $selection, pending: $pending,
                                   format: c.bodyFormat, foreground: fg.opacity(0.9), onDark: dark, minHeight: 44,
                                   autoFocus: inline || addedCard == c.id)
                        .id(c.id)
                }
            } else if !c.title.isEmpty { Text(c.title).font(RealFont.inter(28)).kerning(-0.6).foregroundStyle(fg).lineLimit(3) }
            if !(front && typing) && !c.body.isEmpty {
                StyledBodyText(text: c.body, styles: c.bodyStyles, format: c.bodyFormat, foreground: fg.opacity(0.9), onDark: dark, lineLimit: 10)
            }
        }
        .padding(.horizontal, 24).padding(.top, 22)
        .padding(.bottom, front && typing ? 46 : 22)
        .frame(maxWidth: .infinity, minHeight: deckHeight, alignment: .topLeading)
        .background {
            ZStack {
                shape.fill(color)
                if let path = c.imageUri, let img = AttachmentStorage.image(path) {
                    Image(uiImage: img).resizable().scaledToFill()
                        .scaleEffect(front ? photoScale : 1).offset(front ? photoOffset : .zero)
                    LinearGradient(colors: [.black.opacity(0.06), .black.opacity(0.22), .black.opacity(0.7)], startPoint: .top, endPoint: .bottom)
                }
            }
            .clipShape(shape)
        }
        .overlay(shape.strokeBorder(.white.opacity(0.24), lineWidth: 0.9))
    }

    private func cardField<Value>(_ c: CardStackCard, _ path: WritableKeyPath<CardStackCard, Value>) -> Binding<Value> {
        Binding(get: { c[keyPath: path] }, set: { value in onChange(c.id) { $0[keyPath: path] = value } })
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
    @State private var selection = NSRange(location: 0, length: 0)
    @State private var pending: Set<BodyMark>?
    @State private var formatOpen = false
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        NavigationStack {
            Form {
                TextField(t("Başlık"), text: $card.title)
                VStack(alignment: .leading, spacing: 10) {
                    ZStack(alignment: .topLeading) {
                        if card.body.isEmpty {
                            Text(t("Metin")).font(RealFont.inter(card.bodyFormat.size.points)).foregroundStyle(.tertiary).allowsHitTesting(false)
                        }
                        RichBodyEditor(text: $card.body, styles: $card.bodyStyles, selection: $selection, pending: $pending,
                                       format: card.bodyFormat, foreground: .primary, onDark: scheme == .dark, minHeight: 66,
                                       dismissOnOutsideTap: true)
                    }
                    BodyFormatControls(text: card.body, selection: selection, styles: $card.bodyStyles, pending: $pending,
                                       open: $formatOpen, foreground: .primary, onDark: scheme == .dark)
                }
                .padding(.vertical, 4)
                // A tap outside the text (settings, colours, picture) puts the keyboard down; the control still acts.
                Section(t("Kart yığını")) { CardBodyFormatSettings(format: $card.bodyFormat) }
                    .simultaneousGesture(TapGesture().onEnded { _ = KeyboardWatcher.shared.consumeTap() })
                Section(t("Renk")) {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 8), spacing: 10) {
                        ForEach(pickerColorChoices) { choice in
                            let argb = choice.color.argbInt
                            Circle().fill(choice.color).frame(height: 28)
                                .overlay(Circle().strokeBorder(.white, lineWidth: card.colorArgb == argb ? 2 : 0))
                                .onTapGesture { card.colorArgb = card.colorArgb == argb ? nil : argb }
                        }
                    }
                    .padding(.vertical, 4)
                    .contentShape(Rectangle())
                }
                .simultaneousGesture(TapGesture().onEnded { _ = KeyboardWatcher.shared.consumeTap() })
                Section(t("Görsel")) {
                    PhotosPicker(selection: $photo, matching: .images) { Label(card.imageUri == nil ? t("Görsel ekle") : t("Görseli değiştir"), systemImage: "photo") }
                    if card.imageUri != nil { Button(t("Görseli kaldır"), role: .destructive) { card.imageUri = nil } }
                }
                .simultaneousGesture(TapGesture().onEnded { _ = KeyboardWatcher.shared.consumeTap() })
                if !isNew {
                    Button(t("Kartı sil"), role: .destructive) { onSave(nil); dismiss() }
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(isNew ? t("Yeni kart") : t("Kart"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(t("Vazgeç")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(t("Kaydet")) { onSave(card); dismiss() }
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
    @AppStorage(MapAppearance.darkKey) private var mapDark = false
    @AppStorage(MapAppearance.plainKey) private var mapPlain = false

    /// The nearest named point of interest within ~40 m of a tap, or nil.
    private static func placeNear(_ c: CLLocationCoordinate2D) async -> (coordinate: CLLocationCoordinate2D, name: String, address: String)? {
        let request = MKLocalPointsOfInterestRequest(center: c, radius: 40)
        guard let items = try? await MKLocalSearch(request: request).start().mapItems else { return nil }
        let here = CLLocation(latitude: c.latitude, longitude: c.longitude)
        guard let best = items.filter({ !($0.name ?? "").isEmpty }).min(by: {
            here.distance(from: CLLocation(latitude: $0.placemark.coordinate.latitude, longitude: $0.placemark.coordinate.longitude)) <
            here.distance(from: CLLocation(latitude: $1.placemark.coordinate.latitude, longitude: $1.placemark.coordinate.longitude))
        }) else { return nil }
        let m = best.placemark
        let address = [m.thoroughfare, m.subLocality, m.locality].compactMap { $0 }.joined(separator: ", ")
        return (m.coordinate, best.name ?? "", address)
    }

    var body: some View {
        ZStack {
            MapReader { proxy in
                Map(position: $camera) {
                    UserAnnotation()
                }
                .mapStyle(MapAppearance.style(plain: mapPlain))
                .environment(\.colorScheme, MapAppearance.scheme(dark: mapDark))
                .onTapGesture { point in
                    searchFocused = false
                    guard let c = proxy.convert(point, from: .local) else { return }
                    // A tap on a named place (restaurant, shop…) takes its name, as on Android; elsewhere the geocoder names it.
                    if mapPlain { drop(c, name: "", address: ""); return }
                    Task {
                        if let place = await Self.placeNear(c) {
                            drop(place.coordinate, name: place.name, address: place.address)
                        } else {
                            drop(c, name: "", address: "")
                        }
                    }
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
            .accessibilityLabel(t("Vazgeç"))
            // The picked place lives here; tapping it searches instead.
            Group {
                if searching || pin == nil {
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass").font(.system(size: 13, weight: .semibold)).foregroundStyle(Self.muted)
                        TextField("", text: $query, prompt: Text(t("Yer ara ya da haritaya dokun")).foregroundStyle(Self.muted))
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
                        Text(pinName.isEmpty ? t("Seçilen yer") : pinName).font(RealFont.inter(13, .semibold))
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
            .accessibilityLabel(t("Şu anki konumum"))
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
                // Adding the place comes first; reminding on arrival / departure is the second choice.
                whitePill(t("Konum ekle"), enabled: pin != nil) { savePlain() }
                remindPill
            } else {
                whitePill(t("Bu konumu kaydet"), enabled: pin != nil) { savePlain() }
            }
            HStack {
                if !query.isEmpty && pin == nil {
                    Button(t("“{0}” olarak kaydet", query)) { onSave(ReminderLocation(placeName: query)); dismiss() }
                        .font(RealFont.inter(13, .medium)).foregroundStyle(Self.muted)
                }
                Spacer()
                if initial != nil {
                    Button(t("Konumu kaldır"), role: .destructive) { onSave(nil); dismiss() }.font(RealFont.inter(13, .medium))
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
            Text(leaving ? t("Ayrılınca hatırlat") : t("Varınca hatırlat"))
                .font(RealFont.inter(15, .semibold)).foregroundStyle(Self.ink)
                .id(leaving)
                .transition(.asymmetric(insertion: .move(edge: leaving ? .trailing : .leading).combined(with: .opacity),
                                        removal: .move(edge: leaving ? .leading : .trailing).combined(with: .opacity)))
            HStack {
                if !leaving { Spacer() }
                Button { withAnimation(.spring(response: 0.34, dampingFraction: 0.85)) { leaving.toggle() } } label: {
                    Text(leaving ? t("Varınca") : t("Ayrılınca")).font(RealFont.inter(12, .medium)).foregroundStyle(Self.ink.opacity(0.5))
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
        let name = pinName.isEmpty ? (pinAddress.isEmpty ? t("Seçilen yer") : pinAddress) : pinName
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
        case .denied, .restricted: finish(.failure(t("Konum izni kapalı. Ayarlar > REAL > Konum bölümünden açabilirsin.")))
        default: manager.requestLocation()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard completion != nil else { return }
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: manager.requestLocation()
        case .denied, .restricted: finish(.failure(t("Konum izni verilmedi.")))
        default: break
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let c = locations.last?.coordinate { finish(.success(c)) }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finish(.failure(t("Konum alınamadı. Biraz sonra tekrar dene.")))
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
            .navigationTitle(t("Bağlantı"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(t("Vazgeç")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(t("Kaydet")) {
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
                                Text(t("En üstteki içeriği büyüt")).font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFFE8E8EE))
                                Text(t("Listenin başındaki içerik kartın tepesinde tam genişlikte, büyük görünür."))
                                    .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8E8E98))
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityValue(hero ? t("Açık") : t("Kapalı"))
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
            .navigationTitle(t("İçerik Sıralaması"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button(t("Bitti")) { onSave(keys, hero); dismiss() } }
            }
        }
    }

    private func describe(_ key: String) -> (String, String, String) {
        let kind = key.split(separator: ":").first.map(String.init) ?? key
        switch kind {
        case "text": return (t("Not Metni"), item.text.isEmpty ? t("Boş not") : item.text, "note.text")
        case "card_stack": return (t("Kart Yığını"), cardCountText(item.cardStack?.cards.count ?? 0), "rectangle.stack")
        case "checklist": return (t("Checklist"), t("{0} madde", item.checklistItems.count), "checklist")
        case "photos": return (t("Fotoğraf"), "", "photo")
        case "files": return (key == "files:reference" ? t("Bağlantı") : t("Dosya"), "", "doc")
        case "location": return (t("Konum"), item.location?.placeName ?? t("Konum"), "mappin")
        case "mood": return (t("Mood"), item.mood.map { t($0.label) } ?? t("Mood"), "face.smiling")
        case "journal": return (t("Günlük"), t("Günlük notu"), "book.closed")
        case "voice": return (t("Ses"), t("{0} kayıt", item.voiceRecordings.count), "waveform")
        case "music": return (t("Müzik"), item.musicBlocks.first { key.hasSuffix($0.id) }?.title ?? "", "music.note")
        case "media": return (t("Film / Dizi"), item.mediaBlocks.first { key.hasSuffix($0.id) }?.title ?? "", "film")
        case "game": return (t("Oyun"), item.gameBlocks.first { key.hasSuffix($0.id) }?.title ?? "", "gamecontroller")
        case "weather": return (t("Hava durumu"), "Apple Weather", "cloud.sun")
        default: return (key, "", "square")
        }
    }
}

/// Yaşam's sentence reveal (blur to sharp, a small rise, a fade) for text that comes and goes on the card.
private struct DetailTextReveal: ViewModifier {
    let shown: Bool

    func body(content: Content) -> some View {
        content
            .blur(radius: shown ? 0 : 8)
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : 6)
    }
}

/// Photo block: natural aspect, or the height chosen with the resize handle (cropped like ContentScale.Crop).
private struct PhotoBlockBody: View {
    let attachment: ReminderAttachment
    let editing: Bool
    var heroInset: CGFloat = 0
    let onTap: () -> Void
    let remove: () -> Void
    var onFrame: (Double?, Double?, Double?) -> Void
    @Environment(\.cardBlockHeight) private var fixedHeight
    @State private var scale: CGFloat
    @State private var x: CGFloat
    @State private var y: CGFloat

    init(attachment: ReminderAttachment, editing: Bool, heroInset: CGFloat = 0,
         onTap: @escaping () -> Void, remove: @escaping () -> Void,
         onFrame: @escaping (Double?, Double?, Double?) -> Void) {
        self.attachment = attachment
        self.editing = editing
        self.heroInset = heroInset
        self.onTap = onTap
        self.remove = remove
        self.onFrame = onFrame
        let scale = Self.clampScale(attachment.photoScale)
        _scale = State(initialValue: scale)
        _x = State(initialValue: Self.clampShift(attachment.photoX, scale: scale))
        _y = State(initialValue: Self.clampShift(attachment.photoY, scale: scale))
    }

    var body: some View {
        let image = AttachmentStorage.image(attachment.localPath)
        let ratio = image.map { max(0.5, min(2, $0.size.width / max($0.size.height, 1))) } ?? 1
        ZStack(alignment: .topTrailing) {
            Group {
                if fixedHeight > 0 { Color.clear.frame(maxWidth: .infinity).frame(height: fixedHeight) }
                else { Color.clear.frame(maxWidth: .infinity).aspectRatio(ratio, contentMode: .fit) }
            }
                .overlay {
                    GeometryReader { geo in
                        ZStack {
                            if let image {
                                Image(uiImage: image)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: geo.size.width, height: geo.size.height)
                                    .clipped()
                                    .scaleEffect(scale)
                                    .offset(x: x * geo.size.width, y: y * geo.size.height)
                            } else {
                                RealColors.field
                            }
                            // While editing the grid needs these touches to drag and resize the block.
                            PhotoGestureOverlay(scale: $scale, x: $x, y: $y, onTap: onTap, onFrame: onFrame)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .allowsHitTesting(!editing)
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                .contentShape(Rectangle())
                .onTapGesture { if editing { onTap() } }
            if editing {
                Button(action: remove) {
                    RealSymbol("xmark", size: 11.5).foregroundStyle(.white)
                        .frame(width: 22, height: 22).background(Circle().fill(.black.opacity(0.6)))
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(t("Kaldır"))
                .padding(.top, heroInset > 0 ? heroInset + 48 : 0)
                .padding(.trailing, heroInset > 0 ? 12 : 0)
            }
        }
        .onChange(of: attachment.photoScale) { _, _ in syncFromAttachment() }
        .onChange(of: attachment.photoX) { _, _ in syncFromAttachment() }
        .onChange(of: attachment.photoY) { _, _ in syncFromAttachment() }
    }

    private func syncFromAttachment() {
        let scale = Self.clampScale(attachment.photoScale)
        self.scale = scale
        x = Self.clampShift(attachment.photoX, scale: scale)
        y = Self.clampShift(attachment.photoY, scale: scale)
    }

    private static func clampScale(_ value: Double?) -> CGFloat {
        min(max(CGFloat(value ?? 1), 1), 5)
    }

    private static func clampShift(_ value: Double?, scale: CGFloat) -> CGFloat {
        let limit = max(0, (scale - 1) / 2)
        return min(max(CGFloat(value ?? 0), -limit), limit)
    }
}

/// UIKit gestures over the photo: SwiftUI gestures here would fight the page's scroll. One-finger drags stay free.
private struct PhotoGestureOverlay: UIViewRepresentable {
    @Binding var scale: CGFloat
    @Binding var x: CGFloat
    @Binding var y: CGFloat
    let onTap: () -> Void
    let onFrame: (Double?, Double?, Double?) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isOpaque = false

        let pinch = UIPinchGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.pinch(_:)))
        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.pan(_:)))
        pan.minimumNumberOfTouches = 2
        pan.maximumNumberOfTouches = 2
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tap))
        for gesture in [pinch, pan, tap] {
            gesture.cancelsTouchesInView = false
            gesture.delegate = context.coordinator
            view.addGestureRecognizer(gesture)
        }
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.parent = self
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: PhotoGestureOverlay
        private var startScale: CGFloat = 1
        private var startX: CGFloat = 0
        private var startY: CGFloat = 0
        private var pinching = false
        private var panning = false

        init(_ parent: PhotoGestureOverlay) { self.parent = parent }

        @objc func tap() { parent.onTap() }

        @objc func pinch(_ gesture: UIPinchGestureRecognizer) {
            switch gesture.state {
            case .began:
                startScale = parent.scale
                pinching = true
            case .changed:
                parent.scale = Self.clampScale(startScale * gesture.scale)
                clampShift()
            case .ended, .cancelled, .failed:
                parent.scale = Self.clampScale(startScale * gesture.scale)
                clampShift()
                let wasPinching = pinching
                pinching = false
                if wasPinching { finish() }
            default:
                break
            }
        }

        @objc func pan(_ gesture: UIPanGestureRecognizer) {
            guard let view = gesture.view, view.bounds.width > 0, view.bounds.height > 0 else { return }
            switch gesture.state {
            case .began:
                startX = parent.x
                startY = parent.y
                panning = true
            case .changed, .ended, .cancelled, .failed:
                let translation = gesture.translation(in: view)
                parent.x = startX + translation.x / view.bounds.width
                parent.y = startY + translation.y / view.bounds.height
                clampShift()
                if gesture.state != .changed {
                    let wasPanning = panning
                    panning = false
                    if wasPanning { finish() }
                }
            default:
                break
            }
        }

        private func clampShift() {
            let limit = max(0, (parent.scale - 1) / 2)
            parent.x = min(max(parent.x, -limit), limit)
            parent.y = min(max(parent.y, -limit), limit)
            if parent.scale <= 1 {
                parent.scale = 1
                parent.x = 0
                parent.y = 0
            }
        }

        private func finish() {
            guard !pinching, !panning else { return }
            if parent.scale <= 1 {
                parent.scale = 1
                parent.x = 0
                parent.y = 0
                parent.onFrame(nil, nil, nil)
            } else {
                parent.onFrame(Double(parent.scale), Double(parent.x), Double(parent.y))
            }
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
            let ours = { (g: UIGestureRecognizer) in g is UIPinchGestureRecognizer || g is UIPanGestureRecognizer }
            return ours(gestureRecognizer) && ours(otherGestureRecognizer)
        }

        private static func clampScale(_ value: CGFloat) -> CGFloat {
            min(max(value, 1), 5)
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

/// DETAIL_HERO_MIN_HEIGHT_DP: the hero block's minimum height.
let detailHeroMinHeight = 360

/// Short name for the map's corner: the place's name, or the street when an empty spot was picked.
func reminderLocationLabel(_ l: ReminderLocation) -> String? {
    let name = l.placeName.trimmingCharacters(in: .whitespaces)
    let street = l.address.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.first { !$0.isEmpty }
    if !name.isEmpty, name != "Seçilen yer", name != t("Seçilen yer"), name != l.address.trimmingCharacters(in: .whitespaces), name.contains(where: \.isLetter) { return name }
    if let street { return street }
    return name.isEmpty ? nil : name
}


/// The paper plane flies off and a check lands (SendCheckMark.kt). The check only comes with `done`, so it never claims too early.
struct SendCheckMark: View {
    let done: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        SendCheckMarkRepresentable(done: done, reduceMotion: reduceMotion)
    }
}

private struct SendCheckMarkRepresentable: UIViewRepresentable {
    private static let beforeCheck: CGFloat = 0.58
    let done: Bool
    let reduceMotion: Bool

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> UIView {
        let container = UIView()
        container.backgroundColor = .clear
        let lottie = LottieAnimationView(animation: LottieAnimation.named("send_check"))
        lottie.translatesAutoresizingMaskIntoConstraints = false
        lottie.contentMode = .scaleAspectFit
        lottie.loopMode = .playOnce
        lottie.backgroundBehavior = .pauseAndRestore
        lottie.currentProgress = 0
        container.addSubview(lottie)
        NSLayoutConstraint.activate([
            lottie.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            lottie.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            lottie.topAnchor.constraint(equalTo: container.topAnchor),
            lottie.bottomAnchor.constraint(equalTo: container.bottomAnchor)
        ])
        context.coordinator.animationView = lottie
        context.coordinator.update(done: done, reduceMotion: reduceMotion)
        return container
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.update(done: done, reduceMotion: reduceMotion)
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.stop()
    }

    final class Coordinator: NSObject {
        weak var animationView: LottieAnimationView?
        private var displayLink: CADisplayLink?
        private var currentProgress: CGFloat = 0
        private var startProgress: CGFloat = 0
        private var targetProgress: CGFloat = 0
        private var startTime: CFTimeInterval = 0
        private var duration: CFTimeInterval = 0
        private var lastDone: Bool?

        func update(done: Bool, reduceMotion: Bool) {
            guard lastDone != done else { return }
            lastDone = done
            let target: CGFloat = done ? 1.0 : SendCheckMarkRepresentable.beforeCheck
            if reduceMotion {
                stop()
                currentProgress = target
                animationView?.currentProgress = target
                return
            }
            startProgress = currentProgress
            targetProgress = target
            duration = done ? 1.0 : 1.3
            startTime = CACurrentMediaTime()
            if displayLink == nil {
                let link = CADisplayLink(target: self, selector: #selector(step))
                link.add(to: .main, forMode: .common)
                displayLink = link
            }
        }

        @objc private func step() {
            let elapsed = CACurrentMediaTime() - startTime
            let t = duration > 0 ? min(1.0, max(0.0, elapsed / duration)) : 1.0
            currentProgress = startProgress + (targetProgress - startProgress) * CGFloat(t)
            animationView?.currentProgress = currentProgress
            if t >= 1.0 { stop() }
        }

        func stop() {
            displayLink?.invalidate()
            displayLink = nil
        }

        deinit {
            displayLink?.invalidate()
        }
    }
}

/// Reuses one share link per detail screen for repeated sends across people on the card.
@MainActor
final class CardDetailShareSession {
    private var cachedLink: CardShareClient.Link?
    private var creatingTask: Task<CardShareClient.Link, Error>?

    func link(for card: ReminderItem, options: CardShareClient.Options, store: ReminderStore, life: LifeStore) async throws -> CardShareClient.Link {
        if let cachedLink { return cachedLink }
        if let creatingTask { return try await creatingTask.value }
        let task = Task { @MainActor in
            try await CardShareClient.create(card, options: options, store: store, life: life)
        }
        creatingTask = task
        defer { creatingTask = nil }
        let created = try await task.value
        cachedLink = created
        return created
    }
}

/// In the card detail (CardDetailPeople.kt): who the card is with. Tapping the avatar of someone in Kişilerim brings out
/// a send button; someone who is only a name on the card has none.
private struct CardDetailPeople: View {
    let reminder: ReminderItem
    let color: Color
    let session: CardDetailShareSession

    var body: some View {
        let known = composerKnownPeople()
        VStack(alignment: .leading, spacing: 2) {
            ForEach(Array(reminder.cardPeople.enumerated()), id: \.offset) { _, person in
                CardDetailPerson(reminder: reminder, person: person, connection: connectionFor(person: person, known: known), color: color, session: session)
            }
        }
    }
}

private struct CardDetailPerson: View {
    let reminder: ReminderItem
    let person: CardPerson
    let connection: ComposerKnownPerson?
    let color: Color
    let session: CardDetailShareSession

    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var open = false
    @State private var sending = false
    @State private var sent = false

    var body: some View {
        let uid = connection?.uid
        let avatarKey = uid ?? person.uid ?? person.name
        let avatarPic = connection?.pictureVersion ?? person.pictureVersion
        HStack(spacing: 8) {
            if uid != nil {
                Button {
                    guard !sending && !sent else { return }
                    withAnimation(reduceMotion ? .easeInOut(duration: 0.12) : .spring(response: 0.3, dampingFraction: 0.6)) {
                        open.toggle()
                    }
                } label: {
                    ConnectionAvatar(name: person.name, key: avatarKey, size: 28, pictureVersion: avatarPic)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(t("{0} kişisine gönder", person.name))
            } else {
                ConnectionAvatar(name: person.name, key: avatarKey, size: 28, pictureVersion: avatarPic)
            }
            Text(t("{0} ile", person.name))
                .font(RealFont.inter(14))
                .foregroundStyle(color)
                .lineLimit(1)
            if let uid, open || sending || sent {
                Group {
                    if sending || sent {
                        SendCheckMark(done: sent)
                            .frame(width: 36, height: 36)
                    } else {
                        Button {
                            send(to: uid)
                        } label: {
                            Circle()
                                .fill(Color.white)
                                .frame(width: 32, height: 32)
                                .overlay {
                                    ActionIcon(.share, color: Color(argb: 0xFF0B0B0D), size: 16)
                                }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(t("{0} kişisine gönder", person.name))
                    }
                }
                .transition(reduceMotion ? .opacity : .scale(scale: 0.5).combined(with: .opacity))
            }
        }
        .frame(minHeight: 40)
    }

    private func send(to uid: String) {
        guard !sending && !sent else { return }
        sending = true
        Task {
            do {
                let link = try await session.link(for: reminder, options: shareOptionsFor(reminder), store: store, life: life)
                try await CardShareClient.sendToConnection(link.id, uid: uid)
                sent = true
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            } catch is CancellationError {
            } catch {
                let msg = (error as? LocalizedError)?.errorDescription ?? RealBackend.message(for: error)
                RealToast.shared.show(msg)
            }
            sending = false
        }
    }

    private func shareOptionsFor(_ reminder: ReminderItem) -> CardShareClient.Options {
        CardShareClient.Options(
            attachments: reminder.attachments.contains { $0.type != .textSnippet } || !(reminder.cardStack?.cards.isEmpty ?? true),
            location: reminder.location != nil,
            voice: !reminder.voiceRecordings.isEmpty,
            journal: reminder.journal != nil || reminder.mood != nil
        )
    }
}
