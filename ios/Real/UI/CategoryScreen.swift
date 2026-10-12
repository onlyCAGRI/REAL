import SwiftUI

/// Built-in or custom category screen: hero tile, progress, colour filter, sort and the card list.
struct CategoryScreen: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let builtIn: ReminderCategory?
    let customId: Int64?
    @Binding var path: [Route]

    @State private var sort: ReminderSortOrder = .recentlyAdded
    @State private var colorFilter: Int?
    @State private var showEditor = false
    @State private var showColorSheet = false
    @State private var selectedIds: Set<Int64> = []
    @State private var confirmDelete = false

    private var custom: CustomCategory? { store.category(customId) }

    var body: some View {
        let lists = projection
        ScrollView {
            LazyVStack(spacing: 0) {
                topBar.padding(.horizontal, 12).padding(.top, 8)
                if let visual {
                    CategoryTileView(visual: visual, count: lists.active.count + (custom != nil ? lists.completed.count : 0),
                                     description: custom?.description ?? "", hero: true)
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                }
                if let stats = store.reminders.categoryProgress(builtIn: builtIn, customId: customId), stats.total > 0 {
                    ProgressStrip(stats: stats).padding(.horizontal, 16).padding(.top, 14)
                }
                if !usedColors.isEmpty { colorFilterBar.padding(.top, 14) }
                if lists.active.isEmpty && lists.completed.isEmpty {
                    Text(builtIn?.emptyMessage ?? "Bu kategoride henüz kart yok")
                        .font(RealType.cardTitle)
                        .foregroundStyle(RealColors.textMuted)
                        .multilineTextAlignment(.center)
                        .padding(.top, 48)
                        .padding(.horizontal, 32)
                }
                Color.clear.frame(height: 14)
                if builtIn == .journalEntries {
                    journalList(lists.active)
                } else {
                    ForEach(lists.active) { card($0) }
                }
                if !lists.completed.isEmpty {
                    SectionTitle(text: "Tamamlandı").padding(.top, 18)
                    ForEach(lists.completed) { card($0) }
                }
                Color.clear.frame(height: 140)
            }
            .animation(.spring(duration: 0.35), value: lists.active.map(\.id))
        }
        .background(RealColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .safeAreaInset(edge: .bottom) {
            if !selectedIds.isEmpty {
                SelectionBar(count: selectedIds.count,
                             onStar: { selectedIds.forEach { id in store.mutate(id) { $0.isImportant.toggle() } }; selectedIds = [] },
                             onComplete: { selectedIds.forEach(store.toggleCompleted); selectedIds = [] },
                             onDelete: { confirmDelete = true },
                             onCancel: { selectedIds = [] })
            } else if builtIn != .completed && builtIn != .journalEntries {
                ComposerBar(defaultCategoryId: customId, defaultImportant: builtIn == .important,
                            defaultPlannedToday: builtIn == .today || builtIn == .planned,
                            onOpenPlanner: { path.append(.planner) })
            }
        }
        .sheet(isPresented: $showEditor) { CategoryEditorSheet(existing: custom) }
        .sheet(isPresented: $showColorSheet) { if let builtIn { BuiltInCategoryColorSheet(category: builtIn) } }
        .confirmationDialog("\(selectedIds.count) kart silinsin mi?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Sil", role: .destructive) { store.delete(selectedIds); selectedIds = [] }
        }
        .onChange(of: custom == nil && customId != nil) { _, gone in if gone { dismiss() } }
    }

    private var visual: CategoryVisual? {
        if let custom { return custom.visual }
        return builtIn.map { $0.visual(override: store.builtInColors[$0]) }
    }

    private var usedColors: [Int] {
        let source = custom != nil ? store.reminders.filter { $0.customCategoryId == customId } : builtIn.map { store.items(in: $0) } ?? []
        return Array(Set(source.compactMap(\.accentStart))).sorted()
    }

    private var projection: (active: [ReminderItem], completed: [ReminderItem]) {
        if let customId {
            let mine = store.reminders.filter { $0.customCategoryId == customId }
            let active = filterAndSortReminders(mine.filter { !$0.isCompleted }, colorFilter: colorFilter, sort: sort)
            let completed = mine.filter(\.isCompleted).sorted { ($0.completedAt ?? 0) > ($1.completedAt ?? 0) }
            return (active, completed)
        }
        guard let builtIn else { return ([], []) }
        let items = store.items(in: builtIn)
        if builtIn == .completed || builtIn == .journalEntries || builtIn == .today { return (items, []) }
        return (filterAndSortReminders(items, colorFilter: colorFilter, sort: sort), [])
    }

    private var topBar: some View {
        HStack(spacing: 10) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left").font(.system(size: 17, weight: .semibold))
                    .frame(width: 44, height: 44)
                    .background(.ultraThinMaterial, in: Circle())
            }
            .accessibilityLabel("Geri")
            Spacer()
            if builtIn != .journalEntries && builtIn != .completed && builtIn != .today {
                Menu {
                    Picker("Sırala", selection: $sort) {
                        ForEach(ReminderSortOrder.allCases, id: \.self) { Text($0.label).tag($0) }
                    }
                } label: {
                    Image(systemName: "arrow.up.arrow.down").frame(width: 44, height: 44).background(.ultraThinMaterial, in: Circle())
                }
                .accessibilityLabel("Sırala")
            }
            Button {
                if custom != nil { showEditor = true } else { showColorSheet = true }
            } label: {
                Image(systemName: custom != nil ? "slider.horizontal.3" : "paintpalette")
                    .frame(width: 44, height: 44).background(.ultraThinMaterial, in: Circle())
            }
            .accessibilityLabel(custom != nil ? "Kategoriyi düzenle" : "Kategori rengini düzenle")
        }
        .foregroundStyle(.white)
    }

    private var colorFilterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                filterChip(nil, label: "Tümü")
                ForEach(usedColors, id: \.self) { index in filterChip(index, label: nil) }
            }
            .padding(.horizontal, 16)
        }
    }

    private func filterChip(_ index: Int?, label: String?) -> some View {
        let selected = colorFilter == index
        return Button { withAnimation { colorFilter = index } } label: {
            Group {
                if let label {
                    Text(label).font(RealFont.inter(13, .medium)).padding(.horizontal, 14)
                } else {
                    Circle().fill(paletteColor(index) ?? .gray).frame(width: 20, height: 20).padding(.horizontal, 6)
                }
            }
            .frame(height: 34)
            .background(Capsule().fill(selected ? Color.white.opacity(0.16) : Color.white.opacity(0.05)))
            .overlay(Capsule().strokeBorder(selected ? Color.white.opacity(0.6) : Color.white.opacity(0.1), lineWidth: 0.75))
        }
        .foregroundStyle(.white)
    }

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
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(reminder.text).font(RealType.cardTitle).foregroundStyle(RealColors.text)
                        Spacer()
                        if let mood = reminder.journal?.mood { Text("\(mood.emoji) \(mood.label)").font(RealType.cardMetaPrimary).foregroundStyle(RealColors.subtext) }
                    }
                    if let text = reminder.journal?.text, !text.isEmpty {
                        Text(text).font(RealFont.inter(14)).foregroundStyle(RealColors.subtext).lineLimit(4)
                    }
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(RealColors.cardSurface))
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
                .onTapGesture { path.append(.detail(reminder.id)) }
            }
        }
    }

    private func card(_ reminder: ReminderItem) -> some View {
        ReminderCardView(
            reminder: reminder,
            category: store.category(reminder.customCategoryId),
            showCategoryStripes: false,
            isSelectionMode: !selectedIds.isEmpty,
            isSelected: selectedIds.contains(reminder.id),
            onToggle: { withAnimation(.spring(duration: 0.35)) { store.toggleCompleted(reminder.id) } },
            onOpen: {
                if selectedIds.isEmpty { path.append(.detail(reminder.id)) }
                else if selectedIds.contains(reminder.id) { selectedIds.remove(reminder.id) } else { selectedIds.insert(reminder.id) }
            },
            onLongPress: { selectedIds.insert(reminder.id) }
        )
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
}

struct ProgressStrip: View {
    let stats: CategoryProgressStats
    var body: some View {
        HStack(spacing: 10) {
            stat("Tamamlanan", "\(stats.done)/\(stats.total)")
            stat("Bugün", "\(stats.doneToday)")
            stat("Kalan", "\(max(stats.total - stats.done, 0))")
        }
        .overlay(alignment: .bottom) {
            GeometryReader { geo in
                Capsule().fill(.white.opacity(0.08)).frame(height: 4)
                    .overlay(alignment: .leading) {
                        Capsule().fill(RealColors.accentBlue).frame(width: geo.size.width * stats.fraction, height: 4)
                    }
            }
            .frame(height: 4)
            .offset(y: 12)
        }
        .padding(.bottom, 12)
    }

    private func stat(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(RealFont.inter(11.5)).foregroundStyle(Color(argb: 0xFFB4B9C4))
            Text(value).font(RealFont.inter(18, .semibold)).foregroundStyle(.white)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 9)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(.white.opacity(0.06)))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(.white.opacity(0.1), lineWidth: 0.8))
    }
}
