import SwiftUI
import Combine
import UIKit

enum Route: Hashable {
    case category(ReminderCategory)
    case customCategory(Int64)
    case detail(Int64)
    case planner
    case goal(Int64)
    case module(String)
}

struct RootView: View {
    @Environment(ReminderStore.self) private var store
    @State private var path: [Route]
    @Namespace private var nav
    @State private var showWelcome = false
    /// Cold start: the skeleton with its shimmer, then a wipe to the real home (StartupSkeletonReveal).
    @State private var startupShown = !ProcessInfo.processInfo.arguments.contains("-REALNoStartup")

    init(initialPath: [Route] = []) {
        _path = State(initialValue: initialPath)
    }

    var body: some View {
        NavigationStack(path: $path) {
            HomeView(path: $path)
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .category(let c): CategoryScreen(builtIn: c, customId: nil, path: $path).zoomDestination("cat-builtin:\(c.rawValue)", in: nav)
                    case .customCategory(let id): CategoryScreen(builtIn: nil, customId: id, path: $path).zoomDestination("cat-custom:\(id)", in: nav)
                    case .detail(let id): ReminderDetailView(reminderId: id).zoomDestination("card-\(id)", in: nav)
                    case .planner: PlannerScreen(path: $path)
                    case .goal(let id): PlannerGoalScreen(categoryId: id, path: $path).zoomDestination("goal-\(id)", in: nav)
                    case .module(let name):
                        if name == "Yaşam" {
                            YasamScreen(onOpenCard: { id in path.append(.detail(id)) }).toolbar(.hidden, for: .navigationBar)
                        } else if name == "Pano" {
                            PanoScreen()
                        } else if name == "Film" || name == "FilmDeck" || name == "Oyun" {
                            DiscoveryScreen(kind: name == "Oyun" ? .game : .film, startInDeck: name == "FilmDeck", onClose: {})
                                .toolbar(.hidden, for: .navigationBar)
                        } else if name == "Ayarlar" {
                            SettingsScreen(onBack: {}).toolbar(.hidden, for: .navigationBar)
                        } else if name == "Moods" {
                            MoodHistoryScreen(onAdd: {}).toolbar(.hidden, for: .navigationBar)
                        } else if name == "Kitaplar" {
                            BooksModuleScreen().toolbar(.hidden, for: .navigationBar)
                        } else if name == "Okuyucu" {
                            BooksModuleScreen(resumeBookId: "demo_txt").toolbar(.hidden, for: .navigationBar)
                        } else if name == "Su" {
                            WaterTrackerScreen().toolbar(.hidden, for: .navigationBar)
                        } else {
                            ModulePlaceholderScreen(name: name)
                        }
                    }
                }
        }
        .liftedTopSafeArea()
        .environment(\.navNamespace, nav)
        .tint(RealColors.accentBlue)
        .preferredColorScheme(.dark)
        .overlay {
            if startupShown && !showWelcome {
                StartupSkeletonReveal { startupShown = false }
            }
        }
        .overlay {
            // WelcomeScreen on first launch (no name yet), like MainActivity.
            if showWelcome {
                WelcomeScreen { name in
                    store.setOwnerName(name)
                    withAnimation(.easeOut(duration: 0.35)) { showWelcome = false }
                }
                .transition(.opacity)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .realOpenReminder)) { note in
            if let id = note.userInfo?["reminderId"] as? Int64, store.reminder(id) != nil { path.append(.detail(id)) }
        }
        .onAppear { ReminderStore.current = store }
        .onAppear { if store.ownerName.trimmingCharacters(in: .whitespaces).isEmpty || ProcessInfo.processInfo.arguments.contains("-REALWelcome") { showWelcome = true } }
    }
}

struct HomeView: View {
    @Environment(ReminderStore.self) private var store
    @Environment(BookStore.self) private var books
    @Environment(\.navNamespace) private var nav
    @Binding var path: [Route]
    @State private var expanded = false
    /// Home "Tamamlandı": 3 (the 3rd under glass) until "Daha fazla" opens 10, then 10 more each time.
    @State private var completedShown = 4
    @Environment(\.scenePhase) private var scenePhase
    @State private var selectedIds: Set<Int64> = []
    /// Kontrol ettir: the cards it is for and the people to pick from (nil = sheet closed).
    @State private var checkIds: Set<Int64> = []
    @State private var checkTargets: [CheckTarget]?
    @State private var editingBuiltIn: ReminderCategory?
    @State private var showNewCategory = false
    @State private var showSettings = false
    @State private var showAccountMenu = false
    @State private var showCategoryOrder = false
    @State private var showYasam = false
    /// Custom categories picked with a long press on the deck (Android: selectedCategoryIds).
    @State private var selectedCategoryIds: Set<Int64> = []
    @State private var pressedTile: String?
    @State private var showPano = false
    @State private var contextTarget: ContextCardType?
    @AppStorage("sort_order") private var homeSort = ReminderSortOrder.recentlyAdded.rawValue
    /// What the sort arrow now does, under it on the right; it fades after a moment.
    @State private var sortHint: (id: Int, text: String)?
    @AppStorage(HomeCardStyle.plainKey) private var plainCards = false
    @AppStorage(CalendarCards.homeDaysKey) private var calendarDays = 0
    @State private var externalDetail: CalendarOccurrence?
    /// "Kartlarım" swiped left: search the cards (hidden-from-Home categories too); the categories step aside meanwhile.
    @State private var searchOpen = false
    @State private var searchQuery = ""
    private var keyboard: KeyboardWatcher { KeyboardWatcher.shared }

    /// "Ana sayfada göster" in Kategori düzeni (Android ui/home_category_preview_count_v1).
    @AppStorage("home_category_preview_count_v1") private var previewCount = 4

    var body: some View {
        let search = searchOpen ? searchQuery.trimmingCharacters(in: .whitespaces).lowercased(with: Locale(identifier: "tr_TR")) : ""
        let searching = !search.isEmpty
        let matches: (ReminderItem) -> Bool = { r in
            let tr = Locale(identifier: "tr_TR")
            return r.text.lowercased(with: tr).contains(search) || r.checklistItems.contains { $0.text.lowercased(with: tr).contains(search) } ||
                (store.category(r.customCategoryId)?.name.lowercased(with: tr).contains(search) ?? false)
        }
        let active = searching
            ? filterAndSortReminders(store.cards.filter { !$0.isCompleted && matches($0) }, sort: ReminderSortOrder(rawValue: homeSort) ?? .recentlyAdded)
            : filterAndSortReminders(store.homeActive(), sort: ReminderSortOrder(rawValue: homeSort) ?? .recentlyAdded)
        let completed = searching ? store.cards.inCategory(.completed).filter(matches) : store.homeCompleted()
        // Ayarlar → "Ana ekranda takvim": the phone calendar's next 7 / 14 days as cards too (categorized ones carry
        // their category stripe; hidden-from-home categories stay out).
        let hidden = Set(store.categories.filter(\.hideFromGeneralLists).map(\.id))
        let calendarItems = calendarDays > 0 ? CalendarCards.upcoming(days: calendarDays).filter { o in
            searching ? matches(CalendarCards.card(o)) : !(CalendarCategoryLinks.shared.categoryFor(o).map(hidden.contains) ?? false)
        } : []
        ScrollView {
            LazyVStack(spacing: 0) {
                header
                    .padding(.horizontal, 16)
                    .padding(.top, 20)
                    .padding(.bottom, 22)
                    .opacity(selectedIds.isEmpty ? 1 : 0)
                if !searchOpen { categoryDeck.padding(.horizontal, 16).zIndex(1).transition(.opacity) }
                // "Kartlarım" (swipe left to search) + the category screen's sort arrow: newest on top → newest at the
                // bottom → nearest date.
                HomeCardsSearchTitle(open: $searchOpen.animation(.timingCurve(0.2, 0, 0, 1, duration: 0.3)), query: $searchQuery) {
                    Button { changeHomeSort() } label: {
                        SortArrow(sort: ReminderSortOrder(rawValue: homeSort) ?? .recentlyAdded, fg: Color(argb: 0xFFF2F2F4))
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Sıralamayı değiştir")
                }
                .padding(.leading, 18).padding(.trailing, 6).padding(.top, 12).padding(.bottom, 12)
                if let sortHint {
                    Text(sortHint.text).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFB8B9C2))
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .padding(.horizontal, 24).padding(.bottom, 12)
                        .transition(.opacity)
                        .task(id: sortHint.id) {
                            try? await Task.sleep(for: .seconds(1.6))
                            withAnimation(.easeOut(duration: 0.3)) { self.sortHint = nil }
                        }
                }
                if active.isEmpty && completed.isEmpty && calendarItems.isEmpty && searching {
                    Text("Bununla eşleşen kart yok").font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFF888890)).padding(.top, 40)
                } else if active.isEmpty && completed.isEmpty {
                    Text("Bir REAL yaz, gerisini biz hatırlayalım.")
                        .font(RealType.cardTitle)
                        .foregroundStyle(RealColors.textMuted)
                        .padding(.top, 40)
                }
                if selectedIds.isEmpty && !searching { HomeContextCard(onOpen: openContext) }
                ForEach(active) { reminder in
                    card(reminder, swipe: true, label: searching)
                }
                ForEach(calendarItems) { o in
                    // A phone-calendar event: opens the event window; no completing, swiping or selecting.
                    ReminderCardView(reminder: CalendarCards.card(o), category: store.category(CalendarCategoryLinks.shared.categoryFor(o)),
                                     onOpen: { if !keyboard.consumeTap() { externalDetail = o } })
                        .padding(.horizontal, 16).padding(.bottom, 8)
                }
                if !completed.isEmpty {
                    SectionTitle(text: "Tamamlandı").padding(.top, 18)
                    let shown = Array(completed.prefix(completedShown))
                    let more = completed.count > completedShown
                    ForEach(Array(shown.enumerated()), id: \.element.id) { index, reminder in
                        if more && index == shown.count - 1 {
                            // The last card shown, while more remain, sits under glass with "Daha fazla" on it.
                            card(reminder, swipe: false)
                                .blur(radius: 6)
                                .opacity(0.5)
                                .allowsHitTesting(false)
                                // "Daha fazla" in the middle of the card (it used to sit on its top edge, against the dock).
                                .overlay {
                                    Button {
                                        withAnimation(.spring(duration: 0.35)) {
                                            completedShown = completedShown < 11 ? 11 : completedShown + 10
                                        }
                                    } label: {
                                        Text("Daha fazla").font(RealFont.inter(14, .medium)).foregroundStyle(.white)
                                            .padding(.horizontal, 18).padding(.vertical, 9)
                                            .liquidGlass(Capsule(), role: .chrome)
                                    }
                                    .buttonStyle(.plain)
                                    .padding(.bottom, 8)   // the card's own bottom gap
                                }
                        } else {
                            card(reminder, swipe: false)
                        }
                    }
                }
                Color.clear.frame(height: 140)
            }
            .animation(.spring(duration: 0.35), value: active.map(\.id))
        }
        .scrollDismissesKeyboard(.interactively)
        .hideBottomScrollEdgeEffect()
        .background(RealColors.background.ignoresSafeArea())
        .safeAreaInset(edge: .bottom) {
            if !selectedCategoryIds.isEmpty {
                // Categories picked with a long press (Android): delete them, their cards stay without a category.
                SelectionBar(count: selectedCategoryIds.count, onStar: nil, onComplete: nil,
                             onDelete: {
                                 let ids = selectedCategoryIds
                                 selectedCategoryIds = []
                                 withAnimation(.spring(duration: 0.35)) { ids.forEach { store.deleteCategory($0, deleteReminders: false) } }
                             },
                             onCancel: { selectedCategoryIds = [] })
            } else if selectedIds.isEmpty {
                ComposerBar(defaultCategoryId: nil, isHome: true,
                            onOpenPlanner: { path.append(.planner) },
                            onOpenYasam: { withoutAnimation { showYasam = true } },
                            onOpenPano: { showPano = true })
                    // The fade belongs to the dock: drawn above every card (stacked category cards included) and
                    // below the composer, from above the dock down to the screen edge.
                    .background(alignment: .bottom) { BottomFade() }
            } else {
                SelectionBar(count: selectedIds.count,
                             onStar: {
                                 let allStarred = selectedIds.allSatisfy { store.reminder($0)?.isImportant == true }
                                 selectedIds.forEach { id in store.mutate(id) { $0.isImportant = !allStarred } }
                                 selectedIds = []
                             },
                             onComplete: { selectedIds.forEach(store.toggleCompleted); selectedIds = [] },
                             // As on Android: delete at once, "N kart silindi · Geri al" brings them back.
                             onDelete: { let ids = selectedIds; selectedIds = []; withAnimation(.spring(duration: 0.3)) { store.deleteWithUndo(ids) } },
                             onCancel: { selectedIds = [] },
                             onCheck: {
                                 let ids = selectedIds
                                 Task { checkTargets = await store.checkTargets(for: ids); checkIds = ids; selectedIds = [] }
                             })
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .onChange(of: path) { _, now in
            if now.contains(where: { if case .detail = $0 { return false } else { return true } }) { completedShown = 4 }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { completedShown = 4 }
            // Profile picture for connected people (only uploads when it, or the photo-sharing switch, changed).
            if phase == .active { Task { await ProfilePictures.push() } }
        }
        .task { await ProfilePictures.push() }
        .sheet(item: $editingBuiltIn) { category in
            BuiltInCategoryColorSheet(category: category)
        }
        .sheet(isPresented: $showNewCategory) { CategoryEditorSheet(existing: nil) }
        .realOverlay(isPresented: $showAccountMenu) {
            AccountMenuOverlay(onSettings: { showSettings = true }, onCategoryOrder: { showCategoryOrder = true })
        }
        .fullScreenCover(isPresented: $showSettings) { SettingsScreen { showSettings = false }.liftedTopSafeArea() }
        .sheet(isPresented: $showCategoryOrder) { CategoryOrderSheet() }
        .fullScreenCover(isPresented: $showPano) { PanoScreen().liftedTopSafeArea().presentationBackground(.black) }
        .fullScreenCover(item: $contextTarget) { target in
            Group {
                switch target {
                case .water: WaterTrackerScreen().presentationBackground(.black)
                case .mood: MoodAddSheet()
                case .book: BooksModuleScreen(resumeBookId: books.books.last { books.page($0) > 0 }?.id)
                case .media: DiscoveryScreen(kind: .film) { contextTarget = nil }
                case .watch: RatingsScreen { contextTarget = nil }
                case .listen: MusicDiscoveryScreen(startInList: false) { contextTarget = nil }
                case .play: DiscoveryScreen(kind: .game) { contextTarget = nil }
                case .journal:
                    YasamJournalSheet { text in
                        _ = store.addReminder(text: YasamJournalSheet.title()) { r in
                            r.journal = ReminderJournal(mood: nil, text: text, createdAt: .now, updatedAt: .now)
                        }
                        return true
                    }
                }
            }
            .liftedTopSafeArea()
        }
        .fullScreenCover(isPresented: $showYasam) {
            YasamScreen(onOpenCard: { id in
                withoutAnimation { showYasam = false }
                path.append(.detail(id))
            })
            .liftedTopSafeArea()
        }
        .sheet(item: $externalDetail) { ExternalCalendarEventDetail(event: $0) }
        .sheet(isPresented: Binding(get: { checkTargets != nil }, set: { if !$0 { checkTargets = nil } })) {
            CheckRequestSheet(targets: checkTargets ?? [], canClear: checkIds.contains { store.reminder($0)?.checkRequest != nil },
                              onSave: { target, day, color in store.setCheckRequest(checkIds, target: target, startEpochDay: day, color: color) },
                              onClear: { store.setCheckRequest(checkIds, target: nil) })
        }
    }

    private func changeHomeSort() {
        let current = ReminderSortOrder(rawValue: homeSort) ?? .recentlyAdded
        let next: ReminderSortOrder = current == .recentlyAdded ? .oldestFirst : (current == .oldestFirst ? .reminderTime : .recentlyAdded)
        homeSort = next.rawValue
        let text: String
        switch next {
        case .oldestFirst: text = "Son eklenen altta"
        case .reminderTime, .nearestDate: text = "Tarihi en yakın üstte"
        case .recentlyAdded: text = "Son eklenen üstte"
        }
        withAnimation(.easeOut(duration: 0.2)) { sortHint = ((sortHint?.id ?? 0) + 1, text) }
    }

    private func openContext(_ type: ContextCardType) { contextTarget = type }

    private var header: some View {
        HStack {
            // The welcome screen's wordmark at header size.
            Image("RealWordmark").resizable().scaledToFit()
                .frame(width: 69, height: 30, alignment: .leading)
                .accessibilityLabel("REAL")
            Spacer()
            Button { withoutAnimation { showAccountMenu = true } } label: {
                ProfileAvatar(name: store.ownerName, size: 44)
            }
            .accessibilityLabel("Hesap menüsünü aç")
        }
    }

    private var categoryDeck: some View {
        let order = store.homeCategoryOrder
        let selecting = !selectedCategoryIds.isEmpty
        let visible = expanded || selecting ? order : Array(order.prefix(previewCount))
        return VStack(spacing: 0) {
            VStack(spacing: -72) {
                ForEach(Array(visible.enumerated()), id: \.element.id) { index, item in
                    let customId: Int64? = { if case .custom(let c) = item { return c.id } else { return nil } }()
                    CategoryTileView(visual: visual(item), count: store.count(for: item) + calendarCount(item), description: description(item),
                                     extendUnderNext: index < visible.count - 1,
                                     isSelected: customId.map(selectedCategoryIds.contains) ?? false,
                                     selectionMode: selecting && customId != nil)
                        .scaleEffect(pressedTile == item.orderKey ? 0.985 : 1)
                        .contentShape(Rectangle())
                        // CollectionCategoryCard: tap opens (or picks while picking); long press edits a built-in
                        // category's colour, or starts picking custom categories.
                        .onTapGesture {
                            if keyboard.consumeTap() { return }
                            if selecting { if let customId { toggleCategory(customId) } } else { open(item) }
                        }
                        .onLongPressGesture(minimumDuration: 0.4, perform: {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            if let customId { selectedIds = []; toggleCategory(customId) }
                            else if !selecting, case .builtIn(let c) = item { editingBuiltIn = c }
                        }, onPressingChanged: { pressing in
                            withAnimation(.spring(duration: 0.2)) { pressedTile = pressing ? item.orderKey : nil }
                        })
                        .accessibilityAddTraits(.isButton)
                        .accessibilityAction(named: customId != nil ? "Kategorileri seç" : "Kategori rengini düzenle") {
                            if let customId { toggleCategory(customId) } else if case .builtIn(let c) = item { editingBuiltIn = c }
                        }
                        .zoomSource("cat-\(item.orderKey)", in: nav)
                        .zIndex(Double(index))
                        .modifier(StackPin(index: index, count: visible.count))
                }
            }
            if expanded && !selecting {
                Button { showNewCategory = true } label: {
                    Text("＋  Yeni kategori")
                        .font(RealFont.inter(15))
                        .foregroundStyle(Color(argb: 0xFFCCCCD3))
                        .frame(maxWidth: .infinity)
                        .padding(14)
                        .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(Color(argb: 0xFF35353A), lineWidth: 0.75))
                }
                .padding(.top, 16)
                .padding(.bottom, 8)
            }
            if !selecting && (order.count > previewCount || !expanded) && !store.hiddenHomeKeys.contains(ReminderStore.showAllKey) {
                Button(expanded ? "Daha az göster" : "Tümünü göster") {
                    withAnimation(.spring(duration: 0.4)) { expanded.toggle() }
                }
                .font(RealFont.inter(14, .medium))
                .foregroundStyle(Color(argb: 0xFFB7B7C0))
                .padding(.vertical, 10)
            }
        }
        .padding(.bottom, 12)
    }

    /// Upcoming phone-calendar events filed under a category count with its cards.
    private func calendarCount(_ item: OrderedCategory) -> Int {
        if case .custom(let c) = item { return CalendarCards.inCategory(c.id).count }
        return 0
    }

    private func card(_ reminder: ReminderItem, swipe: Bool, label: Bool = false) -> some View {
        let category = store.category(reminder.customCategoryId)
        // Sınırlı access to a shared category: the card is only looked at.
        let viewOnly = category?.isReadOnlyShared == true
        // Sade kartlar: a right swipe completes (or, on a completed card, undoes); otherwise it moves to 24 hour.
        var rightAction: (() -> Void)? = nil
        if !viewOnly {
            if plainCards { rightAction = { withAnimation(.spring(duration: 0.35)) { store.toggleCompleted(reminder.id) } } }
            else if swipe { rightAction = { withAnimation { store.moveTo24Hour(reminder.id) } } }
        }
        let deleteAction: (() -> Void)? = viewOnly ? nil : { withAnimation(.spring(duration: 0.3)) { store.deleteWithUndo([reminder.id]) } }
        return ReminderCardView(
            reminder: reminder,
            category: category,
            isSelectionMode: !selectedIds.isEmpty,
            isSelected: selectedIds.contains(reminder.id),
            onToggle: { if !viewOnly && !keyboard.consumeTap() { withAnimation(.spring(duration: 0.35)) { store.toggleCompleted(reminder.id) } } },
            onOpen: {
                if keyboard.consumeTap() { return }
                if selectedIds.isEmpty { path.append(.detail(reminder.id)) } else if !viewOnly { toggleSelection(reminder.id) }
            },
            onLongPress: { if !viewOnly { toggleSelection(reminder.id) } },
            onSwipeTo24Hour: rightAction,
            onSwipeDelete: deleteAction,
            swipeRightLabel: plainCards ? (reminder.isCompleted ? "Geri al" : "Tamamla") : "Move 24 hour",
            swipeRightKeepsCard: !plainCards || (reminder.hasRepeat && !reminder.isCompleted),
            plainStyle: plainCards,
            categoryLabel: label
        )
        .zoomSource("card-\(reminder.id)", in: nav)
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
        .transition(.opacity.combined(with: .move(edge: .bottom)))
    }

    private func toggleCategory(_ id: Int64) {
        if selectedCategoryIds.contains(id) { selectedCategoryIds.remove(id) } else { selectedCategoryIds.insert(id) }
    }

    private func toggleSelection(_ id: Int64) {
        if selectedIds.contains(id) { selectedIds.remove(id) } else { selectedIds.insert(id) }
    }

    private func visual(_ item: OrderedCategory) -> CategoryVisual {
        switch item {
        case .builtIn(let c): return c.visual(override: store.builtInColors[c])
        case .custom(let c): return c.visual
        }
    }

    private func description(_ item: OrderedCategory) -> String {
        if case .custom(let c) = item { return c.description }
        return ""
    }

    private func open(_ item: OrderedCategory) {
        switch item {
        case .builtIn(let c): path.append(.category(c))
        case .custom(let c): path.append(.customCategory(c.id))
        }
    }
}

struct SectionTitle: View {
    let text: String
    var body: some View {
        Text(text)
            .font(RealType.sectionTitle)
            .foregroundStyle(RealColors.text)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.bottom, 10)
    }
}

struct PressScaleStyle: ButtonStyle {
    var scale: CGFloat = 0.985
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.spring(duration: 0.2), value: configuration.isPressed)
    }
}

/// ConnectionAvatar (ConnectionUi.kt): someone else's avatar — their initial on a soft colour picked from their uid
/// (same colour as on Android). The profile photo is this phone's own and never shown for other people.
struct ConnectionAvatar: View {
    let name: String
    let key: String
    var size: CGFloat = 44
    /// Their shared profile picture (avatar or photo); shown instead of the initial once it has loaded.
    var pictureVersion: Int64? = nil
    @State private var picture: UIImage?

    private static let tones: [UInt32] = [0xFF6FD6FF, 0xFFFFB86F, 0xFFB59BFF, 0xFF7BE0A4, 0xFFFF8FB1, 0xFFF2E27A]

    /// Java/Kotlin String.hashCode over UTF-16, so a person gets the same colour on both platforms.
    private var tone: Color {
        var h: Int32 = 0
        for unit in key.utf16 { h = h &* 31 &+ Int32(unit) }
        return Color(argb: Self.tones[Int(Int64(h) & 0x7fffffff) % Self.tones.count])
    }

    var body: some View {
        let initial = name.trimmingCharacters(in: .whitespaces).first.map { String($0).uppercased(with: Locale(identifier: "tr_TR")) } ?? "R"
        Circle()
            .fill(LinearGradient(colors: [tone.opacity(0.38), tone.opacity(0.14)], startPoint: .topLeading, endPoint: .bottomTrailing))
            .overlay(Circle().strokeBorder(tone.opacity(0.45), lineWidth: 1))
            .overlay(Text(initial).font(RealFont.inter(size * 0.42, .semibold)).foregroundStyle(.white))
            .overlay { if let picture { Image(uiImage: picture).resizable().scaledToFill() } }
            .frame(width: size, height: size)
            .clipShape(Circle())
            .accessibilityHidden(true)
            .task(id: pictureVersion) {
                picture = nil
                if let pictureVersion { picture = await ProfilePictures.load(uid: key, version: pictureVersion) }
            }
    }
}

/// This phone's own avatar (its profile photo, or initials). Only for the signed-in person; others get ConnectionAvatar.
struct ProfileAvatar: View {
    let name: String
    var size: CGFloat = 44
    @AppStorage("profile_photo_revision") private var photoRevision: Double = 0
    var body: some View {
        if photoRevision > 0, let photo = ProfilePhotoStore.load() {
            Image(uiImage: photo).resizable().scaledToFill()
                .frame(width: size, height: size)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .id(photoRevision)
        } else {
            initialsAvatar
        }
    }

    private var initialsAvatar: some View {
        ZStack {
            Circle().fill(LinearGradient(colors: [Color(argb: 0xFF2A2B33), Color(argb: 0xFF15161B)], startPoint: .top, endPoint: .bottom))
            Circle().strokeBorder(.white.opacity(0.14), lineWidth: 0.75)
            let initials = Planner.initials(name)
            if initials.isEmpty {
                ActionIcon(.person, color: Color(argb: 0xFFB8B8BE), size: size * 0.48)
            } else {
                Text(initials).font(RealFont.inter(size * 0.36, .medium)).foregroundStyle(.white)
            }
        }
        .frame(width: size, height: size)
    }
}

/// Multi-select actions (SelectionActionButtons.kt).
struct SelectionBar: View {
    let count: Int
    /// nil while categories are selected: only delete (Android shows the same bar).
    let onStar: (() -> Void)?
    let onComplete: (() -> Void)?
    let onDelete: () -> Void
    let onCancel: () -> Void
    /// Kontrol ettir for the selected cards (nil hides it).
    var onCheck: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: 18) {
            Button(action: onCancel) { ActionIcon(.close, size: 21) }
            Text("\(count) seçili").font(RealFont.inter(15, .medium))
            Spacer()
            if let onStar { Button(action: onStar) { ActionIcon(.star, size: 21) } }
            if let onComplete { Button(action: onComplete) { ActionIcon(.check, size: 21) } }
            if let onCheck {
                Button(action: onCheck) {
                    Text("Kontrol").font(RealFont.inter(14, .semibold)).foregroundStyle(CheckRequests.otherColor)
                        .padding(.horizontal, 12).frame(height: 34)
                        .background(Capsule().fill(CheckRequests.otherColor.opacity(0.14)))
                }
                .accessibilityLabel("Kontrol ettir")
            }
            Button(action: onDelete) { ActionIcon(.delete, size: 21) }.tint(RealColors.overdue)
        }
        .font(.system(size: 18))
        .foregroundStyle(.white)
        .padding(.horizontal, 22)
        .frame(height: 58)
        .liquidGlass(Capsule())
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
}

/// Yaşam and Pano are not part of the iOS scope yet; the dock keeps their entries like Android.
struct ModulePlaceholderScreen: View {
    @Environment(\.dismiss) private var dismiss
    let name: String
    var body: some View {
        VStack(spacing: 14) {
            Text(name).font(RealType.editorialTitle).kerning(-2.2).foregroundStyle(Color(argb: 0xFFF3F1F4))
            Text("iOS sürümünde yakında").font(RealType.editorialSubtitle).foregroundStyle(Color(argb: 0xFF8C8C95))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        .overlay(alignment: .topLeading) {
            Button { dismiss() } label: {
                RealSymbol("chevron.left", size: 19.5).foregroundStyle(.white)
                    .frame(width: 44, height: 44).liquidGlass(Circle())
            }
            .padding(16)
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}

/// Stacked-card pinning of the home deck (CollectionCategoryCard graphicsLayer): while the list scrolls, each tile
/// sticks at topInset + index * 60 until the whole stack is compressed, so the deck reads as a stack of titles.
struct StackPin: ViewModifier {
    let index: Int
    let count: Int

    func body(content: Content) -> some View {
        content.visualEffect { view, proxy in
            let frame = proxy.frame(in: .scrollView)
            let natural = frame.minY
            let stride = max(frame.height - 72, 0)
            let remaining = CGFloat(count - 1 - index)
            let lastNatural = natural + remaining * stride
            let pinned = max(natural, 8 + CGFloat(index) * 60)
            let final = min(pinned, lastNatural - remaining * 60)
            return view.offset(y: max(final - natural, 0))
        }
    }
}
