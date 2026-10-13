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
    @State private var selectedIds: Set<Int64> = []
    @State private var editingBuiltIn: ReminderCategory?
    @State private var showNewCategory = false
    @State private var showSettings = false
    @State private var showAccountMenu = false
    @State private var showCategoryOrder = false
    @State private var confirmDelete = false
    @State private var showYasam = false
    @State private var showPano = false
    @State private var contextTarget: ContextCardType?
    @AppStorage("sort_order") private var homeSort = ReminderSortOrder.recentlyAdded.rawValue

    /// "Ana sayfada göster" in Kategori düzeni (Android ui/home_category_preview_count_v1).
    @AppStorage("home_category_preview_count_v1") private var previewCount = 4

    var body: some View {
        let active = filterAndSortReminders(store.homeActive(), sort: ReminderSortOrder(rawValue: homeSort) ?? .recentlyAdded)
        let completed = store.homeCompleted()
        ScrollView {
            LazyVStack(spacing: 0) {
                header
                    .padding(.horizontal, 16)
                    .padding(.top, 20)
                    .padding(.bottom, 22)
                    .opacity(selectedIds.isEmpty ? 1 : 0)
                categoryDeck.padding(.horizontal, 16).zIndex(1)
                // "Kartlarım" + HomeSortControl (son eklenen ↔ bildirim zamanı).
                HStack {
                    Text("Kartlarım").font(RealFont.inter(20, .semibold)).foregroundStyle(Color(argb: 0xFFF2F2F4))
                    Spacer()
                    Button {
                        homeSort = homeSort == ReminderSortOrder.reminderTime.rawValue ? ReminderSortOrder.recentlyAdded.rawValue : ReminderSortOrder.reminderTime.rawValue
                    } label: {
                        Image(systemName: homeSort == ReminderSortOrder.reminderTime.rawValue ? "clock" : "line.3.horizontal.decrease")
                            .font(.system(size: 16)).foregroundStyle(Color(argb: 0xFFC9CBD3))
                            .contentTransition(.symbolEffect(.replace))
                            .frame(width: 44, height: 44)
                            .liquidGlass(Circle())
                    }
                    .accessibilityLabel(homeSort == ReminderSortOrder.reminderTime.rawValue
                                        ? "Bildirim zamanına göre sıralanıyor. Son eklenene göre sırala."
                                        : "Son eklenene göre sıralanıyor. Bildirim zamanına göre sırala.")
                }
                .padding(.leading, 18).padding(.trailing, 6).padding(.top, 12).padding(.bottom, 12)
                if active.isEmpty && completed.isEmpty {
                    Text("Bir REAL yaz, gerisini biz hatırlayalım.")
                        .font(RealType.cardTitle)
                        .foregroundStyle(RealColors.textMuted)
                        .padding(.top, 40)
                }
                ForEach(active) { reminder in
                    card(reminder, swipe: true)
                }
                if !completed.isEmpty {
                    SectionTitle(text: "Tamamlandı").padding(.top, 18)
                    ForEach(completed.prefix(8)) { reminder in
                        card(reminder, swipe: false)
                    }
                    if completed.count > 8 {
                        Button("Tümünü gör (\(completed.count))") { path.append(.category(.completed)) }
                            .font(RealFont.inter(14, .medium))
                            .foregroundStyle(Color(argb: 0xFFB7B7C0))
                            .padding(.vertical, 12)
                    }
                }
                if selectedIds.isEmpty { HomeContextCard(onOpen: openContext) }
                Color.clear.frame(height: 140)
            }
            .animation(.spring(duration: 0.35), value: active.map(\.id))
        }
        .scrollDismissesKeyboard(.interactively)
        .hideBottomScrollEdgeEffect()
        .background(RealColors.background.ignoresSafeArea())
        .safeAreaInset(edge: .bottom) {
            if selectedIds.isEmpty {
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
                             onDelete: { confirmDelete = true },
                             onCancel: { selectedIds = [] })
            }
        }
        .toolbar(.hidden, for: .navigationBar)
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
        .confirmationDialog("\(selectedIds.count) kart silinsin mi?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Sil", role: .destructive) { store.delete(selectedIds); selectedIds = [] }
            Button("Vazgeç", role: .cancel) {}
        }
    }

    private func openContext(_ type: ContextCardType) { contextTarget = type }

    private var header: some View {
        HStack {
            Text("REAL")
                .font(RealFont.inter(30, .medium))
                .kerning(-0.8)
                .foregroundStyle(Color(argb: 0xFFF3F2F5))
            Spacer()
            Button { withoutAnimation { showAccountMenu = true } } label: {
                ProfileAvatar(name: store.ownerName, size: 44)
            }
            .accessibilityLabel("Hesap menüsünü aç")
        }
    }

    private var categoryDeck: some View {
        let order = store.homeCategoryOrder
        let visible = expanded ? order : Array(order.prefix(previewCount))
        return VStack(spacing: 0) {
            VStack(spacing: -72) {
                ForEach(Array(visible.enumerated()), id: \.element.id) { index, item in
                    Button { open(item) } label: {
                        CategoryTileView(visual: visual(item), count: store.count(for: item), description: description(item),
                                         extendUnderNext: index < visible.count - 1)
                    }
                    .buttonStyle(PressScaleStyle())
                    .zoomSource("cat-\(item.orderKey)", in: nav)
                    .zIndex(Double(index))
                    .modifier(StackPin(index: index, count: visible.count))
                    .contextMenu {
                        if case .builtIn(let c) = item {
                            Button("Kategori rengini düzenle", systemImage: "paintpalette") { editingBuiltIn = c }
                        }
                    }
                }
            }
            if expanded {
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
            if (order.count > previewCount || !expanded) && !store.hiddenHomeKeys.contains(ReminderStore.showAllKey) {
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

    private func card(_ reminder: ReminderItem, swipe: Bool) -> some View {
        ReminderCardView(
            reminder: reminder,
            category: store.category(reminder.customCategoryId),
            isSelectionMode: !selectedIds.isEmpty,
            isSelected: selectedIds.contains(reminder.id),
            onToggle: { withAnimation(.spring(duration: 0.35)) { store.toggleCompleted(reminder.id) } },
            onOpen: {
                if selectedIds.isEmpty { path.append(.detail(reminder.id)) } else { toggleSelection(reminder.id) }
            },
            onLongPress: { toggleSelection(reminder.id) },
            onSwipeTo24Hour: swipe ? { withAnimation { store.moveTo24Hour(reminder.id) } } : nil
        )
        .zoomSource("card-\(reminder.id)", in: nav)
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
        .transition(.opacity.combined(with: .move(edge: .bottom)))
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
    let onStar: () -> Void
    let onComplete: () -> Void
    let onDelete: () -> Void
    let onCancel: () -> Void

    var body: some View {
        HStack(spacing: 18) {
            Button(action: onCancel) { ActionIcon(.close, size: 21) }
            Text("\(count) seçili").font(RealFont.inter(15, .medium))
            Spacer()
            Button(action: onStar) { ActionIcon(.star, size: 21) }
            Button(action: onComplete) { ActionIcon(.check, size: 21) }
            Button(action: onDelete) { ActionIcon(.delete, size: 21) }.tint(RealColors.overdue)
        }
        .font(.system(size: 18))
        .foregroundStyle(.white)
        .padding(.horizontal, 22)
        .frame(height: 58)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(Capsule().strokeBorder(.white.opacity(0.12), lineWidth: 0.75))
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
