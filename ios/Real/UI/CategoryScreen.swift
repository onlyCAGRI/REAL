import SwiftUI
import UIKit

/// Category screen (ReminderScreen category mode + CategoryFullHero.kt): the category colour as a full-width header
/// from under the status bar (search, ••• → edit / colour filter, big name, counts, progress ring, sort arrow, the
/// progress opening inside it), a thin bar in the category colour once it has scrolled away, then the cards.
struct CategoryScreen: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.navNamespace) private var nav
    let builtIn: ReminderCategory?
    let customId: Int64?
    @Binding var path: [Route]

    @AppStorage(HomeCardStyle.plainKey) private var plainSetting = false
    @AppStorage("category_sort_order") private var sortRaw = ReminderSortOrder.recentlyAdded.rawValue
    @State private var colorFilter: Int?
    @State private var filterMode = false
    @State private var showProgress = false
    @State private var showEditor = false
    @State private var showColorSheet = false
    @State private var selectedIds: Set<Int64> = []
    /// Kontrol ettir: the cards it is for and the people to pick from (nil = sheet closed).
    @State private var checkIds: Set<Int64> = []
    @State private var checkTargets: [CheckTarget]?
    @State private var query = ""
    @State private var heroBottom: CGFloat = 1000
    @State private var showPeople = false
    @State private var externalDetail: CalendarOccurrence?
    @State private var dividerPrompt = false
    @State private var dividerTitle = ""
    @State private var dividerEditing: ReminderItem?
    /// Owner and full members write Ayraçlar; Sınırlı members only see them.
    private var canEditDividers: Bool { custom != nil && custom?.isReadOnlyShared != true }
    private var keyboard: KeyboardWatcher { KeyboardWatcher.shared }
    /// Phone-calendar events filed under this category ("Kategorize et"), the coming year, as read-only cards.
    private var calendarItems: [CalendarOccurrence] { customId.map { CalendarCards.inCategory($0) } ?? [] }

    private var sort: ReminderSortOrder { ReminderSortOrder(rawValue: sortRaw) ?? .recentlyAdded }

    /// Ayraç in the list: the title in the middle with a thin line on each side; long press → Başlığı düzenle / Sil.
    private func dividerRow(_ d: ReminderItem) -> some View {
        HStack(spacing: 12) {
            Rectangle().fill(Color.white.opacity(0.16)).frame(height: 0.75)
            if !d.text.isEmpty {
                Text(d.text).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFB7B7C0)).lineLimit(1)
                    .frame(maxWidth: 240)
                Rectangle().fill(Color.white.opacity(0.16)).frame(height: 0.75)
            }
        }
        .padding(.horizontal, 28).padding(.vertical, 14)
        .contentShape(Rectangle())
        .contextMenu {
            if canEditDividers {
                Button(t("Başlığı düzenle"), systemImage: "pencil") { dividerEditing = d; dividerTitle = d.text; dividerPrompt = true }
                Button(t("Sil"), systemImage: "trash", role: .destructive) { withAnimation { store.delete([d.id]) } }
            }
        }
        .accessibilityLabel(d.text.isEmpty ? t("Ayraç") : t("Ayraç: {0}", d.text))
        .transition(.opacity)
    }

    private func saveDivider() {
        let title = String(dividerTitle.trimmingCharacters(in: .whitespaces).prefix(40))
        if let d = dividerEditing { store.mutate(d.id) { $0.text = title; $0.updatedAt = .now } }
        else if let customId { withAnimation { store.addDivider(categoryId: customId, title: title) } }
        dividerEditing = nil
    }
    private var custom: CustomCategory? { store.category(customId) }

    var body: some View {
        let lists = projection
        let stats = store.cards.categoryProgress(builtIn: builtIn, customId: customId)
        GeometryReader { outer in
            // A parent can hand this reader a zero top inset; the window's own inset keeps the hero buttons below
            // the status bar either way.
            let topInset = max(outer.safeAreaInsets.top, windowTopInset)
            ScrollView {
                LazyVStack(spacing: 0) {
                    if let visual {
                        CategoryHero(
                            visual: visual, description: custom?.description ?? "",
                            count: lists.allCount + calendarItems.count,
                            detail: categoryHeaderDetail(lists.active + calendarItems.map(CalendarCards.card)),
                            showIcon: custom.map { $0.icon != .noIcon } ?? true, topInset: topInset,
                            query: $query, hasFilter: colorFilter != nil,
                            editLabel: custom != nil ? t("Kategoriyi düzenle") : t("Kategori rengini düzenle"),
                            stats: stats, showProgress: $showProgress, sort: sort, showSort: builtIn != .journalEntries,
                            onBack: { dismiss() },
                            onEdit: {
                                // Members get the editor too, limited to the description (read only) and the hide switch.
                                if custom != nil { showEditor = true } else { showColorSheet = true }
                            },
                            onFilter: { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.2)) { filterMode = true } },
                            onSort: { changeSort() },
                            onGoals: customId.map { id in { path.append(.goal(id)) } },
                            onPeople: custom.map { _ in { showPeople = true } },
                            shared: custom?.isShared == true,
                            onAddDivider: canEditDividers ? { dividerTitle = ""; dividerEditing = nil; dividerPrompt = true } : nil
                        )
                        .opacity(selectedIds.isEmpty ? 1 : 0.35)
                        .background(GeometryReader { g in
                            Color.clear.preference(key: HeroBottomKey.self, value: g.frame(in: .named("categoryScroll")).maxY)
                        })
                        .zIndex(2)
                    }
                    if filterMode {
                        colorSelectorHeader.padding(.top, 12).transition(.opacity.combined(with: .move(edge: .top)))
                    }
                    if let sortHint {
                        // What the arrow now does, on the right under the header; it fades after a moment.
                        Text(sortHint.text).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFB8B9C2))
                            .frame(maxWidth: .infinity, alignment: .trailing)
                            .padding(.horizontal, 24).padding(.top, 10)
                            .transition(.opacity)
                            .task(id: sortHint.id) {
                                try? await Task.sleep(for: .seconds(1.6))
                                withAnimation(.easeOut(duration: 0.3)) { self.sortHint = nil }
                            }
                    }
                    Color.clear.frame(height: 18)
                    if lists.active.isEmpty && lists.completed.isEmpty {
                        Text(query.isEmpty ? emptyMessage : t("\"{0}\" bu kategoride yok", query))
                            .font(RealFont.inter(14))
                            .foregroundStyle(Color(argb: 0xFF888890))
                            .frame(height: 104)
                            .padding(.horizontal, 24)
                    }
                    if builtIn == .journalEntries {
                        journalList(lists.active)
                    } else {
                        // Ayraçlar sit between the cards at the moment they were added (added-time orders only).
                        let slots = (customId != nil && query.isEmpty) ? dividerSlots(lists.active, store.dividers(in: customId!), sort: sort) : [:]
                        ForEach(Array(lists.active.enumerated()), id: \.element.id) { index, reminder in
                            ForEach(slots[index] ?? []) { dividerRow($0) }
                            card(reminder)
                        }
                        ForEach(slots[lists.active.count] ?? []) { dividerRow($0) }
                        ForEach(calendarItems.filter { query.isEmpty || $0.title.localizedCaseInsensitiveContains(query) }) { o in
                            // A phone-calendar event: opens the event window; no completing or selecting. Once it
                            // ended today it is faded, and a left swipe hides it.
                            let past = CalendarCards.isPast(o)
                            ReminderCardView(reminder: CalendarCards.card(o), category: custom, showCategoryStripes: false,
                                             onOpen: { if !keyboard.consumeTap() { externalDetail = o } },
                                             onSwipeDelete: past ? { CalendarPastDismissals.shared.dismiss(o) } : nil,
                                             swipeLeftHides: past)
                                .opacity(past ? 0.45 : 1)
                                .padding(.horizontal, 16).padding(.bottom, 8)
                        }
                    }
                    if !lists.completed.isEmpty {
                        SectionTitle(text: t("Tamamlandı")).padding(.top, 18)
                        // Same as Home: 2 + a 3rd under glass until "Daha fazla" opens 10, then 10 more each time.
                        // "Tamamlananlar" itself lists everything.
                        let limit = builtIn != .completed
                        let shown = limit ? Array(lists.completed.prefix(completedShown)) : lists.completed
                        let more = limit && lists.completed.count > completedShown
                        ForEach(Array(shown.enumerated()), id: \.element.id) { index, reminder in
                            if more && index == shown.count - 1 {
                                card(reminder)
                                    .blur(radius: 6).opacity(0.5).allowsHitTesting(false)
                                    .padding(.top, 4)
                                    .overlay(alignment: .top) {
                                        Button {
                                            withAnimation(.spring(duration: 0.35)) {
                                                completedShown = completedShown < 11 ? 11 : completedShown + 10
                                            }
                                        } label: {
                                            Text(t("Daha fazla")).font(RealFont.inter(14, .medium)).foregroundStyle(.white)
                                                .padding(.horizontal, 18).padding(.vertical, 9)
                                                .liquidGlass(Capsule(), role: .chrome)
                                        }
                                        .buttonStyle(.plain)
                                        .offset(y: -6)
                                    }
                                    .padding(.bottom, -84)   // behind the dock
                            } else {
                                card(reminder)
                            }
                        }
                    }
                    if !(builtIn != .completed && lists.completed.count > completedShown) { Color.clear.frame(height: 140) }
                }
                .animation(.spring(duration: 0.35), value: lists.active.map(\.id))
                .animation(.spring(duration: 0.3), value: showProgress)
            }
            .coordinateSpace(name: "categoryScroll")
            .onPreferenceChange(HeroBottomKey.self) { heroBottom = $0 }
            .ignoresSafeArea(edges: .top)
            .overlay(alignment: .top) {
                if let visual, heroBottom < topInset + 64, selectedIds.isEmpty {
                    CategoryCompactBar(visual: visual, topInset: topInset, sort: sort, showSort: builtIn != .journalEntries,
                                       onBack: { dismiss() }, onSort: { changeSort() })
                        .ignoresSafeArea(edges: .top)
                        .transition(.move(edge: .top))
                }
            }
            .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.26), value: heroBottom < topInset + 64)
        }
        .hideBottomScrollEdgeEffect()
        .background(RealColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .safeAreaInset(edge: .bottom) {
            if !selectedIds.isEmpty {
                SelectionBar(count: selectedIds.count,
                             onStar: { selectedIds.forEach { id in store.mutate(id) { $0.isImportant.toggle() } }; selectedIds = [] },
                             onComplete: { selectedIds.forEach { store.toggleCompleted($0) }; selectedIds = [] },
                             // As on Android: delete at once, "N kart silindi · Geri al" brings them back.
                             onDelete: { let ids = selectedIds; selectedIds = []; withAnimation(.spring(duration: 0.3)) { store.deleteWithUndo(ids) } },
                             onCancel: { selectedIds = [] },
                             onCheck: {
                                 let ids = selectedIds
                                 Task { checkTargets = await store.checkTargets(for: ids); checkIds = ids; selectedIds = [] }
                             })
            } else if builtIn != .completed && builtIn != .journalEntries && custom?.isReadOnlyShared != true {
                ComposerBar(defaultCategoryId: customId, defaultImportant: builtIn == .important,
                            defaultPlannedToday: builtIn == .today || builtIn == .planned)
                    .background(alignment: .bottom) { BottomFade() }
            }
        }
        .sheet(isPresented: $showEditor) { CategoryEditorSheet(existing: custom) }
        .sheet(item: $externalDetail) { ExternalCalendarEventDetail(event: $0) }
        .realOverlay(isPresented: $showPeople) {
            if let custom { SharedCategorySheet(category: custom, calendarLinked: CalendarCategoryLinks.shared.hasCategory(custom.id)) }
        }
        .sheet(isPresented: $showColorSheet) { if let builtIn { BuiltInCategoryColorSheet(category: builtIn) } }
        .onChange(of: custom == nil && customId != nil) { _, gone in if gone { dismiss() } }
        .sheet(isPresented: Binding(get: { checkTargets != nil }, set: { if !$0 { checkTargets = nil } })) {
            CheckRequestSheet(targets: checkTargets ?? [], canClear: checkIds.contains { store.reminder($0)?.checkRequest != nil },
                              onSave: { target, day, color in store.setCheckRequest(checkIds, target: target, startEpochDay: day, color: color) },
                              onClear: { store.setCheckRequest(checkIds, target: nil) })
        }
        .alert(dividerEditing == nil ? t("Ayraç ekle") : t("Ayracı düzenle"), isPresented: $dividerPrompt) {
            TextField(t("Ör. 1. hafta bitti"), text: $dividerTitle)
            Button(dividerEditing == nil ? t("Ekle") : t("Kaydet")) { saveDivider() }
            Button(t("Vazgeç"), role: .cancel) { dividerEditing = nil }
        } message: { Text(t("Kartlarının arasına bir çizgi ve kısa bir başlık. Boş bırakırsan sadece çizgi olur.")) }
    }

    private var nextSort: ReminderSortOrder {
        sort == .recentlyAdded ? .oldestFirst : (sort == .oldestFirst ? .nearestDate : .recentlyAdded)
    }

    /// The arrow's new meaning, shown briefly under the header (id restarts the fade on every press).
    @State private var sortHint: (id: Int, text: String)?
    @State private var completedShown = 3

    private func changeSort() {
        let next = nextSort
        sortRaw = next.rawValue
        let text: String
        switch next {
        case .oldestFirst: text = t("Son eklenen altta")
        case .nearestDate, .reminderTime: text = t("Tarihi en yakın üstte")
        case .recentlyAdded: text = t("Son eklenen üstte")
        }
        withAnimation(.easeOut(duration: 0.2)) { sortHint = ((sortHint?.id ?? 0) + 1, text) }
    }

    private var visual: CategoryVisual? {
        if let custom { return custom.visual }
        return builtIn.map { $0.visual(override: store.builtInColors[$0]) }
    }

    private var emptyMessage: String {
        if builtIn == .journalEntries { return t("Henüz günlük eklenmemiş") }
        if colorFilter != nil { return t("Bu renkte hatırlatıcı yok") }
        if custom != nil { return t("Bu kategoride henüz hatırlatıcı yok") }
        return builtIn.map { t($0.emptyMessage) } ?? t("Henüz hatırlatıcı yok")
    }

    /// "Bu kategoride ara": title or a checklist line contains the typed text.
    private func matches(_ r: ReminderItem) -> Bool {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return true }
        let tr = Locale(identifier: "tr_TR")
        let needle = q.lowercased(with: tr)
        return r.text.lowercased(with: tr).contains(needle) || r.checklistItems.contains { $0.text.lowercased(with: tr).contains(needle) }
    }

    private var projection: (active: [ReminderItem], completed: [ReminderItem], allCount: Int, todayCount: Int) {
        let base = baseProjection
        let today = Calendar.real.epochDay(of: Date())
        let todayCount = base.active.filter { r in
            r.alertAt.map { Calendar.real.epochDay(ofMillis: $0) == today } ?? (r.plannedDateEpochDay == today)
        }.count
        return (base.active.filter(matches), base.completed.filter(matches), base.active.count + base.completed.count, todayCount)
    }

    private var baseProjection: (active: [ReminderItem], completed: [ReminderItem]) {
        if let customId {
            let mine = store.cards.filter { $0.customCategoryId == customId }
            let active = filterAndSortReminders(mine.filter { !$0.isCompleted }, colorFilter: colorFilter, sort: sort)
            let completed = mine.filter(\.isCompleted).sorted { ($0.completedAt ?? 0) > ($1.completedAt ?? 0) }
            return (active, completed)
        }
        guard let builtIn else { return ([], []) }
        let items = store.items(in: builtIn)
        switch builtIn {
        case .journalEntries: return (items, [])
        case .today, .completed:
            // These keep their own time order; only the colour filter applies.
            return (filterAndSortReminders(items, colorFilter: colorFilter, sort: .recentlyAdded).sorted {
                (items.firstIndex(of: $0) ?? 0) < (items.firstIndex(of: $1) ?? 0)
            }, [])
        default:
            return (filterAndSortReminders(items, colorFilter: colorFilter, sort: sort), [])
        }
    }

    /// CategoryColorSelectorHeader: close + every palette colour, tap one to filter.
    private var colorSelectorHeader: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                Button { withAnimation { colorFilter = nil; filterMode = false } } label: {
                    RealSymbol("xmark", size: 13.8).foregroundStyle(Color(argb: 0xFFC8C8D0))
                        .frame(width: 34, height: 34).liquidGlass(Circle())
                }
                .accessibilityLabel(t("Filtreyi temizle / Tümünü göster"))
                Button { withAnimation { colorFilter = -1; filterMode = false } } label: {
                    Circle().fill(RealColors.cardSurface).frame(width: 34, height: 34)
                        .overlay(Circle().strokeBorder(.white.opacity(colorFilter == -1 ? 0.9 : 0.25), lineWidth: colorFilter == -1 ? 2 : 1))
                }
                .accessibilityLabel(t("Renksiz kartlar"))
                ForEach(pickerColorChoices) { choice in
                    Button { withAnimation { colorFilter = choice.index; filterMode = false } } label: {
                        Circle().fill(choice.color).frame(width: 34, height: 34)
                            .overlay(Circle().strokeBorder(.white, lineWidth: colorFilter == choice.index ? 2 : 0))
                    }
                    .accessibilityLabel(t("{0} rengine göre filtrele", t(choice.label)))
                }
            }
            .padding(.horizontal, 16)
        }
        .frame(height: 44)
    }

    // MARK: Lists

    @ViewBuilder
    private func journalList(_ items: [ReminderItem]) -> some View {
        let groups = Dictionary(grouping: items) { Calendar.real.startOfDay(for: ($0.journal?.createdAt ?? $0.createdAt).date) }
        ForEach(groups.keys.sorted(by: >), id: \.self) { day in
            Text(RealDates.longDay(day))
                .font(RealFont.inter(13, .medium))
                .foregroundStyle(RealColors.subtext)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 22)
                .padding(.vertical, 8)
            ForEach(groups[day] ?? []) { reminder in
                ReminderCardView(reminder: reminder, category: store.category(reminder.customCategoryId), showCategoryStripes: false,
                                 journalListMode: true, onOpen: { path.append(.detail(reminder.id)) })
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
            }
        }
    }

    private func card(_ reminder: ReminderItem) -> some View {
        let category = store.category(reminder.customCategoryId)
        // Sınırlı access to a shared category: the card is only looked at.
        let viewOnly = category?.isReadOnlyShared == true
        // Sade kartlar (Ayarlar) here too, not in the journal list: a right swipe completes (or undoes).
        let plainCards = plainSetting && builtIn != .journalEntries
        let rightAction: (() -> Void)? = viewOnly ? nil
            : plainCards ? { withAnimation(.spring(duration: 0.35)) { store.toggleCompleted(reminder.id) } }
            : builtIn == .today ? nil : { withAnimation { store.moveTo24Hour(reminder.id) } }
        let deleteAction: (() -> Void)? = viewOnly ? nil : { withAnimation(.spring(duration: 0.3)) { store.deleteWithUndo([reminder.id]) } }
        return ReminderCardView(
            reminder: reminder,
            category: category,
            showCategoryStripes: false,
            isSelectionMode: !selectedIds.isEmpty,
            isSelected: selectedIds.contains(reminder.id),
            onToggle: { if !viewOnly && !keyboard.consumeTap() { withAnimation(.spring(duration: 0.35)) { store.toggleCompleted(reminder.id) } } },
            onOpen: {
                if keyboard.consumeTap() { return }
                if selectedIds.isEmpty { path.append(.detail(reminder.id)) }
                else if viewOnly { return }
                else if selectedIds.contains(reminder.id) { selectedIds.remove(reminder.id) } else { selectedIds.insert(reminder.id) }
            },
            onLongPress: { if !viewOnly { selectedIds.insert(reminder.id) } },
            onSwipeTo24Hour: rightAction,
            onSwipeDelete: deleteAction,
            swipeRightLabel: plainCards ? (reminder.isCompleted ? t("Geri al") : t("Tamamla")) : "Move 24 hour",
            swipeRightKeepsCard: !plainCards || (reminder.hasRepeat && !reminder.isCompleted),
            plainStyle: plainCards
        )
        .zoomSource("card-\(reminder.id)", in: nav)
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
}

private struct HeroBottomKey: PreferenceKey {
    static var defaultValue: CGFloat = 1000
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

private func heroGlass(_ fg: Color) -> Color { fg == .white ? .white.opacity(0.18) : .white.opacity(0.36) }
private func heroRim(_ fg: Color) -> Color { fg == .white ? .white.opacity(0.28) : .black.opacity(0.12) }

private struct HeroRound<Label: View>: View {
    let fg: Color
    var size: CGFloat = 40
    let label: String
    let action: () -> Void
    @ViewBuilder let content: () -> Label
    var body: some View {
        Button(action: action) {
            content().frame(width: size, height: size)
                .background(Circle().fill(heroGlass(fg)))
                .overlay(Circle().strokeBorder(heroRim(fg), lineWidth: 0.8))
        }
        .buttonStyle(PressScaleStyle(scale: 0.94))
        .accessibilityLabel(label)
    }
}

/// The sort arrow (CategoryHeroActions): up = newest first, down = newest last, right = nearest date.
struct SortArrow: View {
    let sort: ReminderSortOrder
    let fg: Color
    var body: some View {
        Image(systemName: "arrow.up").font(.system(size: 20, weight: .light)).foregroundStyle(fg)
            .rotationEffect(.degrees(sort == .oldestFirst ? 180 : (sort == .nearestDate || sort == .reminderTime ? 90 : 0)))
            .animation(.easeOut(duration: 0.22), value: sort)
    }
}

/// CategoryFullHero.kt: the category colour runs full width from under the status bar with rounded bottom corners.
private struct CategoryHero: View {
    let visual: CategoryVisual
    let description: String
    let count: Int
    /// "2 gecikmiş" (warning) or "1 tanesi bugün" after the count.
    let detail: (text: String, alert: Bool)?
    /// A category without an icon starts its description where the icon would be.
    var showIcon = true
    let topInset: CGFloat
    @Binding var query: String
    let hasFilter: Bool
    let editLabel: String
    let stats: CategoryProgressStats?
    @Binding var showProgress: Bool
    let sort: ReminderSortOrder
    let showSort: Bool
    let onBack: () -> Void
    let onEdit: () -> Void
    let onFilter: () -> Void
    let onSort: () -> Void
    /// The category's goals (planning) screen; only custom categories have one.
    var onGoals: (() -> Void)? = nil
    /// Ortak kategori: the people in this category (friend button in the ••• menu).
    var onPeople: (() -> Void)? = nil
    /// Ortak kategori: a friend mark left of the progress ring (tap opens Kişiler).
    var shared = false
    /// Ayraç ekle (custom categories you may write in; nil hides it).
    var onAddDivider: (() -> Void)? = nil
    @State private var menuOpen = false
    @FocusState private var searching: Bool

    var body: some View {
        let fg = visual.foreground
        let shape = UnevenRoundedRectangle(bottomLeadingRadius: 34, bottomTrailingRadius: 34, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                HeroRound(fg: fg, label: t("Ana sayfaya dön"), action: onBack) { RealSymbol("chevron.left", size: 18).foregroundStyle(fg) }
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass").font(.system(size: 14, weight: .medium)).foregroundStyle(fg.opacity(0.8))
                    TextField("", text: $query, prompt: Text(t("Bu kategoride ara")).foregroundStyle(fg.opacity(0.72)))
                        .font(RealFont.inter(14, .medium)).foregroundStyle(fg).tint(fg)
                        .focused($searching).submitLabel(.search).onSubmit { searching = false }
                    if !query.isEmpty {
                        Button { query = "" } label: { RealSymbol("xmark", size: 12).foregroundStyle(fg).frame(width: 26, height: 26) }
                            .accessibilityLabel(t("Aramayı temizle"))
                    }
                }
                .padding(.horizontal, 14).frame(height: 40)
                .background(Capsule().fill(heroGlass(fg)))
                .overlay(Capsule().strokeBorder(heroRim(fg), lineWidth: 0.8))
                HeroRound(fg: fg, label: menuOpen ? t("Seçenekleri kapat") : t("Seçenekler"), action: {
                    withAnimation(.spring(duration: 0.3, bounce: 0.25)) { menuOpen.toggle() }
                }) {
                    ZStack(alignment: .topTrailing) {
                        Text("•••").font(.system(size: 11, weight: .bold)).foregroundStyle(fg).frame(maxWidth: .infinity, maxHeight: .infinity)
                        if hasFilter { Circle().fill(fg).frame(width: 7, height: 7).padding(7) }
                    }
                }
                // ••• drops a round pencil and a round filter button straight under it.
                .overlay(alignment: .top) {
                    if menuOpen {
                        VStack(spacing: 8) {
                            HeroRound(fg: fg, label: editLabel, action: { menuOpen = false; onEdit() }) {
                                Image(systemName: "pencil").font(.system(size: 16, weight: .medium)).foregroundStyle(fg)
                            }
                            .transition(.scale(scale: 0.6).combined(with: .opacity))
                            HeroRound(fg: fg, label: hasFilter ? t("Renk filtresi (açık)") : t("Renge göre filtrele"), action: { menuOpen = false; onFilter() }) {
                                Image(systemName: hasFilter ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease")
                                    .font(.system(size: 16, weight: .medium)).foregroundStyle(fg)
                            }
                            .transition(.scale(scale: 0.6).combined(with: .opacity))
                            if let onGoals {
                                HeroRound(fg: fg, label: t("Hedefler"), action: { menuOpen = false; onGoals() }) {
                                    Image(systemName: "target").font(.system(size: 16, weight: .medium)).foregroundStyle(fg)
                                }
                                .transition(.scale(scale: 0.6).combined(with: .opacity))
                            }
                            if let onPeople {
                                HeroRound(fg: fg, label: t("Kişiler"), action: { menuOpen = false; onPeople() }) {
                                    CategoryIconGlyph(icon: .friends, color: fg, size: 18)
                                }
                                .transition(.scale(scale: 0.6).combined(with: .opacity))
                            }
                            if let onAddDivider {
                                // Ayraç: a line broken by a short title mark (CategoryFullHero.kt).
                                HeroRound(fg: fg, label: t("Ayraç ekle"), action: { menuOpen = false; onAddDivider() }) {
                                    HStack(spacing: 2.5) {
                                        Capsule().fill(fg).frame(width: 5, height: 1.6)
                                        Capsule().fill(fg).frame(width: 4, height: 3.4)
                                        Capsule().fill(fg).frame(width: 5, height: 1.6)
                                    }
                                }
                                .transition(.scale(scale: 0.6).combined(with: .opacity))
                            }
                        }
                        .background(Capsule().fill(visual.stripe.0).padding(-4).shadow(color: .black.opacity(0.35), radius: 10, y: 4))
                        .offset(y: 48)
                    }
                }
                .zIndex(3)
            }
            .zIndex(3)
            HStack(spacing: 8) {
                if showIcon { RealSymbol(visual.symbol, size: 15).foregroundStyle(fg.opacity(0.85)) }
                Text(description.isEmpty ? t("Kategori") : description).font(RealFont.inter(13, .medium)).foregroundStyle(fg.opacity(0.8)).lineLimit(1)
            }
            .padding(.top, 30)
            Text(visual.title).font(RealFont.inter(40, .bold)).kerning(-1.4).foregroundStyle(fg).lineLimit(2)
                .minimumScaleFactor(0.7).padding(.top, 4)
            HStack(spacing: 4) {
                // Overdue reads in a soft red that still shows on light and dark category colours.
                let alertColor = fg == .white ? Color(argb: 0xFFFFB4AB) : Color(argb: 0xFF9C1C1C)
                (Text(t("{0} kart", count)) + (detail.map { d in Text("  •  ") + Text(d.text).foregroundColor(d.alert ? alertColor : fg.opacity(0.9)) } ?? Text("")))
                    .font(RealFont.inter(13, .semibold)).foregroundStyle(fg.opacity(0.9)).lineLimit(1)
                Spacer()
                if shared {
                    Button { onPeople?() } label: {
                        CategoryIconGlyph(icon: .friends, color: fg, size: 19).frame(width: 34, height: 34).contentShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(t("Ortak kategori · Kişiler"))
                }
                if let stats {
                    Button { withAnimation(.spring(duration: 0.35)) { showProgress.toggle() } } label: {
                        CategoryProgressDisc(fraction: stats.fraction, active: showProgress, tint: fg).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(showProgress ? t("İlerlemeyi gizle") : t("Kategori ilerlemesini göster"))
                }
                if showSort {
                    Button(action: onSort) { SortArrow(sort: sort, fg: fg).frame(width: 44, height: 44) }
                        .accessibilityLabel(t("Sıralamayı değiştir"))
                }
            }
            .padding(.top, 10)
            if let stats, showProgress {
                HeroProgress(stats: stats, fg: fg).padding(.top, 14)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(.top, topInset + 10).padding(.horizontal, 18).padding(.bottom, 22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            ZStack {
                shape.fill(visual.gradient)
                shape.fill(RadialGradient(colors: [.white.opacity(0.2), .clear], center: UnitPoint(x: 0.85, y: 0), startRadius: 0, endRadius: 360))
            }
        }
    }
}

/// The category's progress inside its header: big percentage, a glass track filling in the text colour, three counters.
private struct HeroProgress: View {
    let stats: CategoryProgressStats
    let fg: Color
    @State private var fill: Double = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .lastTextBaseline, spacing: 2) {
                Text("\(Int((fill * 100).rounded()))").font(RealFont.inter(46, .bold)).kerning(-1.5).foregroundStyle(fg)
                    .contentTransition(.numericText())
                Text("%").font(RealFont.inter(20, .bold)).foregroundStyle(fg.opacity(0.7))
                Spacer()
                Text(stats.total == 0 ? t("Kayıt yok") : t("{0} / {1} tamamlandı", stats.done, stats.total))
                    .font(RealFont.inter(13, .semibold)).foregroundStyle(fg.opacity(0.85))
            }
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(heroGlass(fg))
                    Capsule().fill(fg.opacity(0.9)).frame(width: g.size.width * min(max(fill, 0), 1))
                }
            }
            .frame(height: 12)
            HStack(spacing: 8) {
                chip(t("Tamamlanan"), stats.done)
                chip(t("Kalan"), max(stats.total - stats.done, 0))
                chip(t("Bugün"), stats.doneToday)
            }
        }
        .onAppear { withAnimation(.easeInOut(duration: 0.85).delay(0.08)) { fill = stats.fraction } }
        .onChange(of: stats.fraction) { _, f in withAnimation(.easeInOut(duration: 0.85)) { fill = f } }
    }

    private func chip(_ label: String, _ value: Int) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(RealFont.inter(11.5, .medium)).foregroundStyle(fg.opacity(0.75)).lineLimit(1)
            Text("\(value)").font(RealFont.inter(20, .bold)).foregroundStyle(fg)
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(heroGlass(fg)))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(heroRim(fg), lineWidth: 0.8))
    }
}

/// The thin bar in the category colour that takes the header's place once it has scrolled away.
private struct CategoryCompactBar: View {
    let visual: CategoryVisual
    let topInset: CGFloat
    let sort: ReminderSortOrder
    let showSort: Bool
    let onBack: () -> Void
    let onSort: () -> Void

    var body: some View {
        let fg = visual.foreground
        HStack(spacing: 8) {
            HeroRound(fg: fg, size: 36, label: t("Ana sayfaya dön"), action: onBack) { RealSymbol("chevron.left", size: 16).foregroundStyle(fg) }
            Text(visual.title).font(RealFont.inter(18, .bold)).foregroundStyle(fg).lineLimit(1).padding(.leading, 4)
            Spacer()
            if showSort {
                Button(action: onSort) { SortArrow(sort: sort, fg: fg).frame(width: 40, height: 40) }
                    .accessibilityLabel(t("Sıralamayı değiştir"))
            }
        }
        .padding(.top, topInset + 6).padding(.horizontal, 12).padding(.bottom, 8)
        .background(UnevenRoundedRectangle(bottomLeadingRadius: 22, bottomTrailingRadius: 22, style: .continuous).fill(visual.gradient)
            .shadow(color: .black.opacity(0.4), radius: 14, y: 6))
    }
}

/// The hero's progress button: a plain ring in the header's text colour, like the sort arrow beside it.
struct CategoryProgressDisc: View {
    let fraction: Double
    let active: Bool
    let tint: Color
    var body: some View {
        ZStack {
            Circle().stroke(tint.opacity(active ? 0.34 : 0.24), lineWidth: 2.6)
            Circle().trim(from: 0, to: max(0, min(fraction, 1)))
                .stroke(tint, style: StrokeStyle(lineWidth: 2.6, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .frame(width: 22, height: 22)
    }
}

/// "İlerleme" glow card (CategoryProgressPanel + WidgetGlowCard + GlowProgressBar).
struct CategoryProgressPanel: View {
    let title: String
    let stats: CategoryProgressStats
    let accent: Color
    @State private var fill: Double = 0

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 32, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(t("İlerleme")).font(RealFont.inter(18)).foregroundStyle(.white)
                Spacer()
                Text(title).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFB4B9C4))
            }
            HStack(alignment: .lastTextBaseline, spacing: 0) {
                Text("\(Int((fill * 100).rounded()))").font(RealFont.inter(44)).foregroundStyle(.white).contentTransition(.numericText())
                Text("%").font(.system(size: 44, weight: .light)).foregroundStyle(accent.opacity(0.85))
            }
            .padding(.top, 6)
            GlowProgressBar(fraction: fill, label: stats.total == 0 ? t("Kayıt yok") : "\(stats.done) / \(stats.total)", accent: accent)
                .frame(height: 46)
                .padding(.top, 8)
            HStack(spacing: 10) {
                statChip(t("Tamamlanan"), stats.done, accent: accent)
                statChip(t("Kalan"), max(stats.total - stats.done, 0), accent: nil)
                statChip(t("Bugün"), stats.doneToday, accent: nil)
            }
            .padding(.top, 14).padding(.bottom, 14)
        }
        .padding(.horizontal, 20).padding(.top, 18)
        .background {
            ZStack {
                shape.fill(LinearGradient(colors: [Color(argb: 0xFF0D1119), Color(argb: 0xFF05070B)], startPoint: .top, endPoint: .bottom))
                shape.fill(LinearGradient(stops: [.init(color: .clear, location: 0.55), .init(color: accent.opacity(0.4 * 0.30), location: 0.9),
                                                  .init(color: accent.opacity(0.4 * 0.85), location: 1)], startPoint: .top, endPoint: .bottom))
            }
        }
        .overlay(shape.strokeBorder(.white.opacity(0.10), lineWidth: 0.8))
        .onAppear { withAnimation(.easeInOut(duration: 0.85).delay(0.08)) { fill = stats.fraction } }
        .onChange(of: stats.fraction) { _, f in withAnimation(.easeInOut(duration: 0.85)) { fill = f } }
    }

    private func statChip(_ label: String, _ value: Int, accent: Color?) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(RealFont.inter(11.5)).kerning(-0.2).foregroundStyle(Color(argb: 0xFFB4B9C4)).lineLimit(1)
            Text("\(value)").font(RealFont.inter(22)).foregroundStyle(accent != nil && value > 0 ? accent! : .white)
        }
        .padding(.horizontal, 10).padding(.vertical, 9)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(.white.opacity(0.06)))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(accent?.opacity(0.28) ?? .white.opacity(0.10), lineWidth: 0.8))
    }
}

struct GlowProgressBar: View {
    let fraction: Double
    let label: String
    let accent: Color

    var body: some View {
        GeometryReader { geo in
            let h = geo.size.height
            let w = fraction > 0 ? max(geo.size.width * min(fraction, 1), h * 0.7) : 0
            let start = accent.lerp(.white, 0.28)
            let (r, g, b, _) = accent.rgba
            let light = 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.45
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.10))
                if w > 0 {
                    ForEach(1...5, id: \.self) { i in
                        let gap = CGFloat(i) * 4.5
                        Capsule().fill(accent.opacity(0.05)).frame(width: w + gap * 0.8, height: h + gap * 2).offset(x: -gap * 0.4)
                    }
                    Capsule().fill(LinearGradient(colors: [start, accent], startPoint: .leading, endPoint: .trailing)).frame(width: w)
                }
                Text(label).font(RealFont.inter(18, .medium))
                    .foregroundStyle(fraction > 0.72 ? (light ? Color(argb: 0xFF0E1014) : .white) : .white.opacity(0.88))
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.trailing, 22)
            }
        }
    }
}

/// The key window's top safe-area inset (status bar / Dynamic Island).
@MainActor
var windowTopInset: CGFloat {
    UIApplication.shared.connectedScenes
        .compactMap { ($0 as? UIWindowScene)?.keyWindow?.safeAreaInsets.top }
        .first ?? 0
}
