import SwiftUI
import UIKit

/// Category screen (ReminderScreen category mode + CategoryHeaderGlass.kt): dock-glass header buttons,
/// hero tile with progress ring + sort arrow, collapsible "İlerleme" panel, colour filter row, card list.
struct CategoryScreen: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let builtIn: ReminderCategory?
    let customId: Int64?
    @Binding var path: [Route]

    @AppStorage("category_sort_order") private var sortRaw = ReminderSortOrder.recentlyAdded.rawValue
    @State private var colorFilter: Int?
    @State private var filterMode = false
    @State private var showProgress = false
    @State private var showEditor = false
    @State private var showColorSheet = false
    @State private var selectedIds: Set<Int64> = []
    @State private var confirmDelete = false

    private var sort: ReminderSortOrder { ReminderSortOrder(rawValue: sortRaw) ?? .recentlyAdded }
    private var custom: CustomCategory? { store.category(customId) }

    var body: some View {
        let lists = projection
        let stats = store.reminders.categoryProgress(builtIn: builtIn, customId: customId)
        ScrollView {
            LazyVStack(spacing: 0) {
                header(stats: stats)
                    .padding(.top, 10).padding(.leading, 4).padding(.trailing, 16)
                    .opacity(selectedIds.isEmpty ? 1 : 0)
                Color.clear.frame(height: 22)
                if let visual {
                    heroTile(visual, count: lists.active.count + lists.completed.count, stats: stats)
                        .padding(.horizontal, 16).padding(.bottom, 20)
                }
                if let stats, showProgress {
                    CategoryProgressPanel(title: visual?.title ?? "", stats: stats, accent: progressAccent)
                        .padding(.horizontal, 16).padding(.bottom, 20)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }
                if lists.active.isEmpty && lists.completed.isEmpty {
                    Text(emptyMessage)
                        .font(RealFont.inter(14))
                        .foregroundStyle(Color(argb: 0xFF888890))
                        .frame(height: 104)
                        .padding(.horizontal, 24)
                }
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
            .animation(.spring(duration: 0.3), value: showProgress)
        }
        .background(RealColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .safeAreaInset(edge: .bottom) {
            if !selectedIds.isEmpty {
                SelectionBar(count: selectedIds.count,
                             onStar: { selectedIds.forEach { id in store.mutate(id) { $0.isImportant.toggle() } }; selectedIds = [] },
                             onComplete: { selectedIds.forEach { store.toggleCompleted($0) }; selectedIds = [] },
                             onDelete: { confirmDelete = true },
                             onCancel: { selectedIds = [] })
            } else if builtIn != .completed && builtIn != .journalEntries {
                ComposerBar(defaultCategoryId: customId, defaultImportant: builtIn == .important,
                            defaultPlannedToday: builtIn == .today || builtIn == .planned)
                    .background(alignment: .bottom) {
                        LinearGradient(colors: [.clear, .black.opacity(0.85), .black], startPoint: .top, endPoint: .bottom)
                            .frame(height: 130).ignoresSafeArea().allowsHitTesting(false)
                    }
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

    private var progressAccent: Color {
        if let custom { return custom.customSolidColorArgb.map { Color(argbInt: $0) } ?? paletteColor(custom.accentStart) ?? RealColors.accentBlue }
        guard let builtIn else { return Color(argb: 0xFF62F5DC) }
        let o = store.builtInColors[builtIn]
        return o?.customSolidColorArgb.map { Color(argbInt: $0) } ?? paletteColor(o?.start) ?? Color(argb: builtIn.defaultColors.0)
    }

    private var emptyMessage: String {
        if builtIn == .journalEntries { return "Henüz günlük eklenmemiş" }
        if colorFilter != nil { return "Bu renkte hatırlatıcı yok" }
        if custom != nil { return "Bu kategoride henüz hatırlatıcı yok" }
        return builtIn?.emptyMessage ?? "Henüz hatırlatıcı yok"
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

    // MARK: Header

    @ViewBuilder
    private func header(stats: CategoryProgressStats?) -> some View {
        if filterMode {
            colorSelectorHeader
        } else {
            HStack(spacing: 8) {
                glassCircle(symbol: "chevron.left", label: "Ana sayfaya dön", size: 36) { dismiss() }
                Spacer()
                if stats == nil && builtIn != .journalEntries {
                    Menu {
                        Picker("Sıralama", selection: $sortRaw) {
                            Label("Son eklenen üstte", systemImage: "text.alignleft").tag(ReminderSortOrder.recentlyAdded.rawValue)
                            Label("Son eklenen en sonda", systemImage: "text.alignright").tag(ReminderSortOrder.oldestFirst.rawValue)
                            Label("Tarihi en yakın", systemImage: "calendar").tag(ReminderSortOrder.nearestDate.rawValue)
                        }
                    } label: {
                        sortGlyph.frame(width: 36, height: 36).liquidGlass(Circle())
                    }
                    .accessibilityLabel("Sıralama")
                }
                Button { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.2)) { filterMode = true } } label: {
                    ZStack {
                        if let c = colorFilter.flatMap(paletteColor) {
                            Circle().fill(c).frame(width: 16, height: 16)
                        } else {
                            Image(systemName: "circle.lefthalf.filled").font(.system(size: 16)).foregroundStyle(Color(argb: 0xFFC9CBD3))
                        }
                    }
                    .frame(width: 36, height: 36).liquidGlass(Circle())
                }
                .accessibilityLabel("Renge göre filtrele")
                glassCircle(symbol: custom != nil ? "slider.horizontal.3" : "paintpalette",
                            label: custom != nil ? "Kategoriyi düzenle" : "Kategori rengini düzenle", size: 36) {
                    if custom != nil { showEditor = true } else { showColorSheet = true }
                }
            }
        }
    }

    private var sortGlyph: some View {
        Group {
            switch sort {
            case .nearestDate, .reminderTime: Image(systemName: "calendar")
            case .oldestFirst: Image(systemName: "text.alignright")
            case .recentlyAdded: Image(systemName: "text.alignleft")
            }
        }
        .font(.system(size: 15))
        .foregroundStyle(Color(argb: 0xFFC9CBD3))
    }

    private func glassCircle(symbol: String, label: String, size: CGFloat, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: size * 0.44, weight: .medium)).foregroundStyle(Color(argb: 0xFFC9CBD3))
                .frame(width: size, height: size).liquidGlass(Circle())
                .frame(width: 44, height: 44)
        }
        .buttonStyle(PressScaleStyle(scale: 0.94))
        .accessibilityLabel(label)
    }

    /// CategoryColorSelectorHeader: close + every palette colour, tap one to filter.
    private var colorSelectorHeader: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                Button { withAnimation { colorFilter = nil; filterMode = false } } label: {
                    Image(systemName: "xmark").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color(argb: 0xFFC8C8D0))
                        .frame(width: 34, height: 34).liquidGlass(Circle())
                }
                .accessibilityLabel("Filtreyi temizle / Tümünü göster")
                Button { withAnimation { colorFilter = -1; filterMode = false } } label: {
                    Circle().fill(RealColors.cardSurface).frame(width: 34, height: 34)
                        .overlay(Circle().strokeBorder(.white.opacity(colorFilter == -1 ? 0.9 : 0.25), lineWidth: colorFilter == -1 ? 2 : 1))
                }
                .accessibilityLabel("Renksiz kartlar")
                ForEach(reminderColorChoices) { choice in
                    Button { withAnimation { colorFilter = choice.index; filterMode = false } } label: {
                        Circle().fill(choice.color).frame(width: 34, height: 34)
                            .overlay(Circle().strokeBorder(.white, lineWidth: colorFilter == choice.index ? 2 : 0))
                    }
                    .accessibilityLabel("\(choice.label) rengine göre filtrele")
                }
            }
            .padding(.horizontal, 16)
        }
        .frame(height: 44)
    }

    // MARK: Hero

    private func heroTile(_ visual: CategoryVisual, count: Int, stats: CategoryProgressStats?) -> some View {
        CategoryTileView(visual: visual, count: count, description: custom?.description ?? "", hero: true)
            .overlay(alignment: .bottomTrailing) {
                if let stats {
                    HStack(spacing: 0) {
                        Button { withAnimation { showProgress.toggle() } } label: {
                            ProgressRing(fraction: stats.fraction, active: showProgress).frame(width: 48, height: 48)
                        }
                        .accessibilityLabel(showProgress ? "İlerlemeyi gizle" : "Kategori ilerlemesini göster")
                        Button {
                            let next: ReminderSortOrder = sort == .recentlyAdded ? .oldestFirst : (sort == .oldestFirst ? .nearestDate : .recentlyAdded)
                            sortRaw = next.rawValue
                        } label: {
                            Image(systemName: "arrow.up")
                                .font(.system(size: 19, weight: .regular))
                                .foregroundStyle(visual.foreground)
                                .rotationEffect(.degrees(sort == .oldestFirst ? 180 : (sort == .nearestDate || sort == .reminderTime ? 90 : 0)))
                                .animation(.easeOut(duration: 0.22), value: sortRaw)
                                .frame(width: 48, height: 48)
                        }
                        .accessibilityLabel("Sıralamayı değiştir")
                    }
                    .padding(.trailing, 14).padding(.bottom, 26)
                }
            }
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

/// Ring that fills with completion, lime → aqua (ProgressRingIcon).
struct ProgressRing: View {
    let fraction: Double
    let active: Bool
    var body: some View {
        ZStack {
            Circle().stroke(Color.white.opacity(active ? 0.34 : 0.22), lineWidth: 2.2)
            Circle().trim(from: 0, to: max(0, min(fraction, 1)))
                .stroke(AngularGradient(colors: [Color(argb: 0xFFC4FF6A), Color(argb: 0xFF62F5DC), Color(argb: 0xFFC4FF6A)], center: .center),
                        style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .frame(width: 20, height: 20)
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
                Text("İlerleme").font(RealFont.inter(18)).foregroundStyle(.white)
                Spacer()
                Text(title).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFB4B9C4))
            }
            HStack(alignment: .lastTextBaseline, spacing: 0) {
                Text("\(Int((fill * 100).rounded()))").font(RealFont.inter(44)).foregroundStyle(.white).contentTransition(.numericText())
                Text("%").font(.system(size: 44, weight: .light)).foregroundStyle(accent.opacity(0.85))
            }
            .padding(.top, 6)
            GlowProgressBar(fraction: fill, label: stats.total == 0 ? "Kayıt yok" : "\(stats.done) / \(stats.total)", accent: accent)
                .frame(height: 46)
                .padding(.top, 8)
            HStack(spacing: 10) {
                statChip("Tamamlanan", stats.done, accent: accent)
                statChip("Kalan", max(stats.total - stats.done, 0), accent: nil)
                statChip("Bugün", stats.doneToday, accent: nil)
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
