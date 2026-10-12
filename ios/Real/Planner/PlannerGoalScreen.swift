import SwiftUI
import UIKit

/// Planner category detail ("Planning" / goal screen, PlannerCategoryDetailScreen.kt):
/// title + date, progress tile with notched corner, swipe card (Next up / Link / Members),
/// weekly activity pager, and the numbered Goals sheet with progress rails.
struct PlannerGoalScreen: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let categoryId: Int64
    @Binding var path: [Route]

    @State private var intro = false
    @State private var composing = false
    @State private var reorderMode = false
    @State private var stylePopup = false
    @State private var swipePage = 0
    @State private var weekPage = 0
    @State private var showPasteLink = false
    @State private var dragId: Int64?
    @State private var dragOrder: [Int64]?
    @State private var dragStartIndex = 0

    private let calendar = Calendar.real
    private static let sheet = Color(argb: 0xFF121216)
    private static let muted = Color(argb: 0xFF8C8C95)
    private static let faint = Color(argb: 0xFF55555D)
    private static let line = Color(argb: 0xFF2A2A31)
    private static let black = Color(argb: 0xFF0B0B0D)

    private var category: CustomCategory? { store.category(categoryId) }

    /// plannerCategoryDetailAccent (or the pastel picked in the style popup).
    private var accent: Color {
        if let i = store.categoryPastel[categoryId], realCalendarPastels.indices.contains(i) { return plannerSoftPastel(realCalendarPastels[i]) }
        guard let c = category else { return plannerSoftPastel(realCalendarPastels[9]) }
        let raw = c.customSolidColorArgb.map { Color(argbInt: $0) } ?? paletteColor(c.accentStart) ?? Color(argb: 0xFF4558C8)
        return plannerSoftPastel(nearestCalendarPastel(raw))
    }

    private var members: [ReminderItem] {
        let order = store.goalOrder[categoryId] ?? []
        let position = Dictionary(uniqueKeysWithValues: order.enumerated().map { ($0.element, $0.offset) })
        return store.reminders
            .filter { $0.customCategoryId == categoryId && $0.journal == nil && $0.mood == nil }
            .sorted { a, b in
                let pa = position[a.id] ?? .max, pb = position[b.id] ?? .max
                if pa != pb { return pa < pb }
                return (a.createdAt, a.id) < (b.createdAt, b.id)
            }
    }

    private struct Goal: Identifiable {
        let id: Int64
        let title: String
        let caption: String
        let done: Bool
        let progress: Double?
    }

    private var goals: [Goal] {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US")
        f.dateFormat = "MMM d"
        var list = members.map { r -> Goal in
            let due = r.alertAt.map { $0.date } ?? r.plannedDateEpochDay.map { calendar.startOfDay(epochDay: $0) }
            let items = r.isChecklist ? r.checklistItems.filter { !$0.text.trimmingCharacters(in: .whitespaces).isEmpty } : []
            let checked = items.filter(\.isCompleted).count
            let caption = [due.map { "Due \(f.string(from: $0))" }, items.isEmpty ? nil : "\(checked)/\(items.count)"]
                .compactMap { $0 }.joined(separator: "  ·  ")
            return Goal(id: r.id, title: r.text.trimmingCharacters(in: .whitespaces).isEmpty ? "Untitled" : r.text, caption: caption,
                        done: r.isCompleted,
                        progress: (!r.isCompleted && !items.isEmpty && checked > 0) ? Double(checked) / Double(items.count) : nil)
        }
        if let live = dragOrder {
            let byId = Dictionary(uniqueKeysWithValues: list.map { ($0.id, $0) })
            list = live.compactMap { byId[$0] } + list.filter { !live.contains($0.id) }
        }
        return list
    }

    private struct Week: Identifiable {
        let id: String
        let title: String
        let activity: [Int]
        let todayIndex: Int
        var done: Int { activity.reduce(0, +) }
    }

    /// PlannerWeekHistoryStore: every week from the first activity (at least two weeks back) up to this week.
    private var weeks: [Week] {
        let today = calendar.startOfDay(for: Date())
        let weekday = calendar.isoWeekday(of: today).rawValue
        let monday = calendar.date(byAdding: .day, value: -(weekday - 1), to: today)!
        func mondayOf(_ millis: EpochMillis) -> Date {
            let d = calendar.startOfDay(for: millis.date)
            return calendar.date(byAdding: .day, value: -(calendar.isoWeekday(of: d).rawValue - 1), to: d)!
        }
        let candidates = members.flatMap { r in [r.completedAt.map(mondayOf), mondayOf(r.createdAt)].compactMap { $0 } }
            + [category.map { mondayOf($0.createdAt) }, calendar.date(byAdding: .weekOfYear, value: -2, to: monday)].compactMap { $0 }
        let earliest = candidates.filter { $0 <= monday }.min() ?? monday
        var result: [Week] = []
        var cursor = earliest
        while cursor <= monday {
            let isCurrent = calendar.isDate(cursor, inSameDayAs: monday)
            let activity = (0..<7).map { offset -> Int in
                let day = calendar.date(byAdding: .day, value: offset, to: cursor)!
                return members.filter { $0.completedAt.map { calendar.isDate($0.date, inSameDayAs: day) } ?? false }.count
            }
            result.append(Week(id: "\(cursor.timeIntervalSince1970)", title: weekTitle(cursor, current: monday), activity: activity,
                               todayIndex: isCurrent ? weekday - 1 : -1))
            cursor = calendar.date(byAdding: .weekOfYear, value: 1, to: cursor)!
        }
        return result
    }

    private func weekTitle(_ monday: Date, current: Date) -> String {
        if calendar.isDate(monday, inSameDayAs: current) { return "This week" }
        if calendar.isDate(monday, inSameDayAs: calendar.date(byAdding: .weekOfYear, value: -1, to: current)!) { return "Last week" }
        let sunday = calendar.date(byAdding: .day, value: 6, to: monday)!
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US")
        let sameMonth = calendar.component(.month, from: monday) == calendar.component(.month, from: sunday)
        f.dateFormat = "MMM d"
        return sameMonth ? "\(f.string(from: monday)) – \(calendar.component(.day, from: sunday))" : "\(f.string(from: monday)) – \(f.string(from: sunday))"
    }

    var body: some View {
        let goals = self.goals
        let total = goals.count
        let doneCount = goals.filter(\.done).count
        let activeIndex = goals.firstIndex { !$0.done }
        let overall = total == 0 ? 0 : Double(doneCount) / Double(total)
        ZStack(alignment: .bottom) {
            Color.black.ignoresSafeArea()
            LinearGradient(colors: [accent.opacity(0.22), .clear], startPoint: .top, endPoint: .bottom)
                .frame(height: 480).frame(maxHeight: .infinity, alignment: .top).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    topBar.intro(intro, delay: 0)
                    titleBlock.intro(intro, delay: 0.06, y: 14)
                    tiles(doneCount: doneCount, total: total, next: activeIndex.map { goals[$0] })
                    weekSection.intro(intro, delay: 0.38, y: 16)
                    goalsSheet(goals: goals, total: total, activeIndex: activeIndex, overall: overall)
                        .offset(y: intro ? 0 : 140)
                        .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.6).delay(0.2), value: intro)
                }
            }
            .scrollIndicators(.hidden)
            if composing { goalComposer }
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            weekPage = max(weeks.count - 1, 0)
            withAnimation { intro = true }
        }
        .sheet(isPresented: $stylePopup) {
            CategoryStylePopup(categoryId: categoryId, onReorder: { stylePopup = false; reorderMode = true })
                .presentationDetents([.height(420)])
                .presentationBackground(Color(argb: 0xFF1B1B1F))
        }
        .sheet(isPresented: $showPasteLink) { PasteLinkSheet(categoryId: categoryId).presentationDetents([.height(380)]) }
    }

    private var topBar: some View {
        HStack {
            Button { dismiss() } label: {
                RealSymbol("chevron.left", size: 21.8).foregroundStyle(.white)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Geri")
            Spacer()
            Button("Done") { dismiss() }
                .font(RealFont.inter(15))
                .foregroundStyle(.white)
                .padding(.horizontal, 12).padding(.vertical, 10)
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
    }

    private var titleBlock: some View {
        let name = category?.name ?? ""
        let size: CGFloat = name.count > 24 ? 36 : (name.count > 14 ? 44 : 54)
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US")
        f.dateFormat = "EEEE, MMMM d  ·  yyyy"
        return VStack(alignment: .leading, spacing: 8) {
            Text(name)
                .font(RealFont.inter(size))
                .kerning(name.count > 24 ? -1.2 : (name.count > 14 ? -1.8 : -2.2))
                .foregroundStyle(Color(argb: 0xFFF3F1F4))
                .lineLimit(3)
            Text(f.string(from: Date())).font(RealType.editorialSubtitle).foregroundStyle(Self.muted)
        }
        .padding(.horizontal, 24)
        .padding(.top, 26)
    }

    private func tiles(doneCount: Int, total: Int, next: Goal?) -> some View {
        VStack(spacing: 12) {
            GeometryReader { geo in
                let w1 = (geo.size.width - 10) / 2.9
                HStack(spacing: 10) {
                    progressTile(doneCount: doneCount, total: total)
                        .frame(width: w1)
                        .scaleEffect(intro ? 1 : 0.6).opacity(intro ? 1 : 0)
                        .animation(.spring(duration: 0.52, bounce: 0.3).delay(0.22), value: intro)
                    swipeCard(next: next)
                        .scaleEffect(intro ? 1 : 0.6).opacity(intro ? 1 : 0)
                        .animation(.spring(duration: 0.52, bounce: 0.3).delay(0.3), value: intro)
                }
            }
            .frame(height: 120)
            GeometryReader { geo in
                let w1 = (geo.size.width - 10) / 2.9
                PagerDots(current: swipePage, count: 3, accent: accent)
                    .frame(width: geo.size.width - w1 - 10)
                    .offset(x: w1 + 10)
            }
            .frame(height: 6)
        }
        .padding(.horizontal, 20)
        .padding(.top, 26)
    }

    private func progressTile(doneCount: Int, total: Int) -> some View {
        ZStack(alignment: .topLeading) {
            NotchedTileShape().fill(accent)
            RealSymbol(category?.icon.symbol ?? "star.fill", size: 20)
                .font(.system(size: 14))
                .foregroundStyle(.white)
                .frame(width: 34, height: 34)
                .background(Circle().fill(Self.black))
                .padding(14)
            VStack(alignment: .leading, spacing: 0) {
                Spacer()
                Text("\(doneCount)/\(total)").font(RealFont.inter(34)).kerning(-1.2).foregroundStyle(RealColors.plannerInk)
                Text("goals done").font(RealFont.inter(12)).foregroundStyle(RealColors.plannerInk.opacity(0.6))
            }
            .padding(.leading, 16).padding(.bottom, 12)
            Button {
                if reorderMode { reorderMode = false } else { stylePopup = true }
            } label: {
                ZStack {
                    // The badge sits in the black notch, so it is drawn in the tile colour.
                    Circle().fill(reorderMode ? accent : .clear)
                    Circle().strokeBorder(accent.opacity(0.55), lineWidth: 1)
                    Image(systemName: reorderMode ? "checkmark" : "pencil")
                        .font(.system(size: reorderMode ? 13 : 12, weight: .semibold))
                        .foregroundStyle(reorderMode ? RealColors.plannerInk : accent)
                }
                .frame(width: 30, height: 30)
            }
            .frame(maxWidth: .infinity, alignment: .topTrailing)
            .padding(5)
            .accessibilityLabel(reorderMode ? "Finish reordering goals" : "Edit category style")
        }
    }

    private func swipeCard(next: Goal?) -> some View {
        let link = store.categoryLinks[categoryId] ?? ""
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
        return TabView(selection: $swipePage) {
            HStack(spacing: 0) {
                VStack(alignment: .leading) {
                    Text("Next up").font(RealFont.inter(12)).foregroundStyle(Self.muted)
                    Spacer()
                    Text(next?.title ?? "Everything is done").font(RealFont.inter(15)).foregroundStyle(.white).lineLimit(3)
                }
                .padding(.leading, 16).padding(.vertical, 14).padding(.trailing, 6)
                .frame(maxWidth: .infinity, alignment: .leading)
                PagerActionButton(label: "Start", symbol: "chevron.right", accent: accent, enabled: next != nil) {
                    if let next { path.append(.detail(next.id)) }
                }
            }
            .tag(0)
            HStack(spacing: 0) {
                VStack(alignment: .leading) {
                    Label("Link", systemImage: "link").font(RealFont.inter(12)).foregroundStyle(Self.muted)
                    Spacer()
                    Text(link.isEmpty ? "Link yapıştır" : (URL(string: link)?.host?.replacingOccurrences(of: "www.", with: "") ?? link))
                        .font(RealFont.inter(17, .medium)).foregroundStyle(link.isEmpty ? Self.muted : .white).lineLimit(2)
                        .onTapGesture { showPasteLink = true }
                }
                .padding(.leading, 18).padding(.vertical, 14).padding(.trailing, 6)
                .frame(maxWidth: .infinity, alignment: .leading)
                PagerActionButton(label: link.isEmpty ? "Yapıştır" : "Aç", symbol: "chevron.right", accent: accent, enabled: true) {
                    if let url = URL(string: link), !link.isEmpty { openURL(url) } else { showPasteLink = true }
                }
            }
            .tag(1)
            HStack(spacing: 0) {
                VStack(alignment: .leading) {
                    Text("Members (1)").font(RealFont.inter(12)).foregroundStyle(Self.muted)
                    Spacer()
                    HStack(spacing: -10) {
                        ProfileAvatar(name: store.ownerName, size: 36).overlay(Circle().strokeBorder(Self.black, lineWidth: 2))
                        RealSymbol("plus", size: 14.9).foregroundStyle(.white)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(Self.black)).overlay(Circle().strokeBorder(Self.faint, lineWidth: 1))
                    }
                }
                .padding(.leading, 18).padding(.vertical, 14).padding(.trailing, 6)
                .frame(maxWidth: .infinity, alignment: .leading)
                PagerActionButton(label: "Manage", symbol: "person", accent: accent, enabled: true) {}
            }
            .tag(2)
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .clipShape(shape)
        .overlay(shape.strokeBorder(Self.line, lineWidth: 1))
    }

    private var weekSection: some View {
        let weeks = self.weeks
        return VStack(spacing: 0) {
            TabView(selection: $weekPage) {
                ForEach(Array(weeks.enumerated()), id: \.element.id) { index, week in
                    VStack(spacing: 0) {
                        HStack {
                            Text(week.title).font(RealType.editorialItem).kerning(-1).foregroundStyle(.white)
                            Spacer()
                            Text("\(week.done) done").font(RealFont.inter(13)).foregroundStyle(Self.muted)
                        }
                        .padding(.horizontal, 4)
                        WeekActivityView(activity: week.activity, todayIndex: week.todayIndex, accent: accent)
                    }
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .frame(height: 154)
            Button {
                path.append(.customCategory(categoryId))
            } label: {
                HStack {
                    Text("Add a reminder to \(category?.name ?? "")").font(RealFont.inter(14)).foregroundStyle(.white).lineLimit(1)
                    Spacer()
                    RealSymbol("chevron.right", size: 13.8).foregroundStyle(Self.muted)
                }
                .padding(.horizontal, 20)
                .frame(height: 52)
                .overlay(RoundedRectangle(cornerRadius: 26).strokeBorder(Self.line, lineWidth: 1))
            }
            .padding(.top, 12)
        }
        .padding(.horizontal, 20)
        .padding(.top, 30)
    }

    private func goalsSheet(goals: [Goal], total: Int, activeIndex: Int?, overall: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    Text("Goals (\(total))").font(RealType.editorialItem).kerning(-1).foregroundStyle(.white)
                    Spacer()
                    Button { composing = true } label: {
                        RealSymbol("plus", size: 17.2).foregroundStyle(.white)
                            .frame(width: 40, height: 40).overlay(Circle().strokeBorder(Self.faint, lineWidth: 1))
                    }
                    .accessibilityLabel("Add goal")
                }
                HStack(spacing: 12) {
                    Capsule().fill(.white.opacity(0.09)).frame(height: 5)
                        .overlay(alignment: .leading) {
                            GeometryReader { g in Capsule().fill(accent).frame(width: g.size.width * overall) }
                        }
                    Text("\(Int(overall * 100))%").font(RealFont.inter(12)).foregroundStyle(Self.muted)
                }
            }
            .padding(.leading, 24).padding(.trailing, 20).padding(.top, 26).padding(.bottom, 22)
            ForEach(Array(goals.enumerated()), id: \.element.id) { index, goal in
                goalRow(goal, number: index + 1, active: index == activeIndex, isLast: index == goals.count - 1)
                    .opacity(intro ? 1 : 0)
                    .animation(.easeOut(duration: 0.38).delay(0.52 + Double(min(index, 6)) * 0.055), value: intro)
            }
            Button { composing = true } label: {
                HStack(spacing: 10) {
                    RealSymbol("plus", size: 16.1)
                    Text("Add goal").font(RealFont.inter(14))
                }
                .foregroundStyle(accent)
                .frame(maxWidth: .infinity)
                .frame(height: 60)
                .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(Color(argb: 0xFF3A3A42), style: StrokeStyle(lineWidth: 1.5, dash: [7, 6])))
            }
            .padding(.leading, 24).padding(.trailing, 20).padding(.top, 6).padding(.bottom, 8)
            Color.clear.frame(height: 140)
        }
        .background(UnevenRoundedRectangle(topLeadingRadius: 36, topTrailingRadius: 36, style: .continuous).fill(Self.sheet).ignoresSafeArea(edges: .bottom))
        .padding(.top, 30)
    }

    private func goalRow(_ goal: Goal, number: Int, active: Bool, isLast: Bool) -> some View {
        let state: Int = goal.done ? 0 : (active ? 1 : 2) // Done, Active, Locked
        let rail: Double = state == 0 ? 1 : (state == 1 ? (goal.progress ?? 0) : 0)
        let caption = goal.done ? "Done" : (!goal.caption.isEmpty ? goal.caption : (state == 1 ? "In progress" : "Up next"))
        return HStack(alignment: .top, spacing: 14) {
            VStack(spacing: 0) {
                ZStack {
                    if state == 1 { PulseCircle(color: accent) }
                    Button { withAnimation(.spring(duration: 0.35)) { store.toggleCompleted(goal.id) } } label: {
                        ZStack {
                            switch state {
                            case 0: Circle().fill(accent)
                            case 1: Circle().fill(accent.opacity(0.16)); Circle().strokeBorder(accent, lineWidth: 2)
                            default: Circle().strokeBorder(Color(argb: 0xFF3A3A42), lineWidth: 1)
                            }
                            if state == 0 {
                                RealSymbol("checkmark", size: 17.2).foregroundStyle(RealColors.plannerInk)
                            } else {
                                Text("\(number)").font(RealFont.inter(16)).foregroundStyle(state == 1 ? accent : Color(argb: 0xFF7C7C85))
                            }
                        }
                        .frame(width: 42, height: 42)
                    }
                    .accessibilityLabel("Goal \(number), \(goal.done ? "done" : "not done")")
                }
                .frame(width: 52, height: 52)
                if !isLast {
                    GeometryReader { g in
                        ZStack(alignment: .top) {
                            Capsule().fill(.white.opacity(0.10))
                            Capsule().fill(accent).frame(height: g.size.height * rail)
                        }
                    }
                    .frame(width: 2)
                    .padding(.vertical, 2)
                    .animation(.easeOut(duration: 0.65), value: rail)
                }
            }
            .frame(width: 52)
            VStack(alignment: .leading, spacing: 0) {
                Text(caption).font(RealFont.inter(11)).kerning(0.5).foregroundStyle(state == 1 ? accent : Self.muted)
                Text(goal.title)
                    .font(RealType.editorialItem).kerning(-1)
                    .foregroundStyle(goal.done ? Self.muted : (state == 1 ? .white : Color(argb: 0xFFC9C9D0)))
                    .strikethrough(goal.done, color: Self.muted)
                    .padding(.top, 4)
                    .onTapGesture { path.append(.detail(goal.id)) }
                if let p = goal.progress, !goal.done {
                    HStack(spacing: 10) {
                        Capsule().fill(.white.opacity(0.10)).frame(width: 120, height: 4)
                            .overlay(alignment: .leading) { Capsule().fill(accent).frame(width: 120 * p, height: 4) }
                        Text("\(Int(p * 100))%").font(RealFont.inter(12)).foregroundStyle(Self.muted)
                    }
                    .padding(.top, 12)
                }
                HStack(spacing: 2) {
                    HStack(spacing: 5) {
                        Circle().frame(width: 6, height: 6)
                        Circle().frame(width: 6, height: 6)
                    }
                    .foregroundStyle(reorderMode || dragId == goal.id ? accent : Self.faint)
                    .frame(width: 44, height: 36, alignment: .leading)
                    .contentShape(Rectangle())
                    .gesture(reorderGesture(goal.id), including: reorderMode ? .all : .none)
                    Button { path.append(.detail(goal.id)) } label: {
                        RealSymbol("pencil", size: 17.2).foregroundStyle(Self.muted).frame(width: 32, height: 32)
                    }
                    .accessibilityLabel("Edit goal")
                    Spacer()
                    Button { withAnimation(.spring(duration: 0.35)) { store.toggleCompleted(goal.id) } } label: {
                        Text(goal.done ? "Undo" : "Mark done")
                            .font(RealFont.inter(12))
                            .foregroundStyle(goal.done ? Self.muted : .white)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .overlay(Capsule().strokeBorder(Self.faint, lineWidth: 1))
                    }
                }
                .padding(.top, 14)
            }
            .padding(.top, 2)
            .padding(.bottom, isLast ? 14 : 30)
        }
        .padding(.leading, 18).padding(.trailing, 20)
        .background(dragId == goal.id ? Color(argb: 0xFF1B1B21) : Self.sheet)
        .scaleEffect(dragId == goal.id ? 1.02 : 1)
        .zIndex(dragId == goal.id ? 1 : 0)
    }

    /// Reorder mode: drag the dots; every ~110pt of travel swaps with the neighbour.
    private func reorderGesture(_ id: Int64) -> some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { value in
                if dragId == nil {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    dragId = id
                    dragOrder = goals.map(\.id)
                    dragStartIndex = dragOrder?.firstIndex(of: id) ?? 0
                }
                guard var order = dragOrder, let from = order.firstIndex(of: id) else { return }
                let steps = Int((value.translation.height / 110).rounded(.towardZero))
                let target = min(max(dragStartIndex + steps, 0), order.count - 1)
                if target != from {
                    order.remove(at: from)
                    order.insert(id, at: target)
                    withAnimation(.spring(duration: 0.25)) { dragOrder = order }
                }
            }
            .onEnded { _ in
                if let order = dragOrder { store.setGoalOrder(categoryId, order) }
                dragId = nil
                dragOrder = nil
            }
    }

    private var goalComposer: some View {
        GoalComposerWindow(accent: accent, categoryName: category?.name ?? "", onDismiss: { composing = false }) { title in
            composing = false
            store.addReminder(text: title, categoryId: categoryId)
        }
    }
}

/// Rounded tile with a concave notch top-right where the pencil badge sits.
struct NotchedTileShape: Shape {
    func path(in rect: CGRect) -> Path {
        let body = Path(roundedRect: rect, cornerRadius: 28, style: .continuous)
        let hole = Path(ellipseIn: CGRect(x: rect.maxX - 24 - 31, y: 24 - 31, width: 62, height: 62))
        return body.subtracting(hole)
    }
}

private struct PulseCircle: View {
    let color: Color
    @State private var on = false
    var body: some View {
        Circle().fill(color.opacity(on ? 0.26 : 0.08))
            .frame(width: 52, height: 52)
            .onAppear { withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) { on = true } }
    }
}

private struct PagerActionButton: View {
    let label: String
    let symbol: String
    let accent: Color
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                RealSymbol(symbol, size: 14.9)
                    .foregroundStyle(RealColors.plannerInk)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(enabled ? accent : accent.opacity(0.3)))
                Text(label).font(RealFont.inter(12)).foregroundStyle(.white).lineLimit(1)
            }
            .frame(width: 76)
            .frame(maxHeight: .infinity)
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(accent.opacity(enabled ? 0.8 : 0.3), lineWidth: 1))
            .padding(6)
        }
        .disabled(!enabled)
    }
}

struct PagerDots: View {
    let current: Int
    let count: Int
    let accent: Color
    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<count, id: \.self) { i in
                Capsule().fill(i == current ? accent : Color(argb: 0xFF3A3A42))
                    .frame(width: i == current ? 18 : 6, height: 6)
            }
        }
        .animation(.easeOut(duration: 0.2), value: current)
    }
}

private struct WeekActivityView: View {
    let activity: [Int]
    let todayIndex: Int
    let accent: Color
    private let labels = ["M", "T", "W", "T", "F", "S", "S"]

    var body: some View {
        let maxValue = max(1, activity.max() ?? 1)
        HStack(alignment: .bottom, spacing: 10) {
            ForEach(0..<7, id: \.self) { i in
                let value = activity.indices.contains(i) ? activity[i] : 0
                VStack(spacing: 6) {
                    Spacer(minLength: 0)
                    GeometryReader { g in
                        RoundedRectangle(cornerRadius: 8)
                            .fill(value == 0 ? Color.white.opacity(0.08) : accent.opacity(i == todayIndex ? 1 : 0.55))
                            .frame(width: g.size.width * 0.62, height: 6 + 40 * CGFloat(value) / CGFloat(maxValue))
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    }
                    .frame(height: 46)
                    Text(labels[i]).font(RealFont.inter(10)).foregroundStyle(i == todayIndex ? .white : Color(argb: 0xFF8C8C95))
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 18).padding(.vertical, 14)
        .frame(height: 104)
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(Color(argb: 0xFF2A2A31), lineWidth: 1))
        .padding(.top, 14)
    }
}

/// "Add to REAL" window over the goal screen: dim scrim, glass input pill above the keyboard.
private struct GoalComposerWindow: View {
    let accent: Color
    let categoryName: String
    let onDismiss: () -> Void
    let onSubmit: (String) -> Void
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.55).ignoresSafeArea().onTapGesture(perform: onDismiss)
            VStack(alignment: .leading, spacing: 6) {
                Text("New goal in \(categoryName)").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8C8C95)).lineLimit(1)
                HStack {
                    TextField("", text: $text, prompt: Text("Add to REAL").foregroundStyle(Color(argb: 0xFF7C7C85)))
                        .font(RealFont.inter(18)).foregroundStyle(.white).tint(accent)
                        .focused($focused).submitLabel(.done).onSubmit(submit)
                        .frame(minHeight: 44)
                    Button(action: submit) {
                        RealSymbol("plus", size: 17.2)
                            .foregroundStyle(text.isEmpty ? Color(argb: 0xFF7C7C85) : RealColors.plannerInk)
                            .frame(width: 44, height: 44)
                            .background(Circle().fill(text.isEmpty ? Color.white.opacity(0.10) : accent))
                    }
                    .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
                    .accessibilityLabel("Add goal")
                }
            }
            .padding(.leading, 20).padding(.trailing, 10).padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 30, style: .continuous).fill(Color(argb: 0xFF121216)))
            .liquidGlass(RoundedRectangle(cornerRadius: 30, style: .continuous), role: .chrome)
            .padding(.horizontal, 14).padding(.vertical, 12)
        }
        .onAppear { focused = true }
    }

    private func submit() {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        onSubmit(t)
    }
}

/// Pencil on the progress tile: pastel, card shape, reorder (CategoryStylePopup).
private struct CategoryStylePopup: View {
    @Environment(ReminderStore.self) private var store
    let categoryId: Int64
    let onReorder: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Renk").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8C8C95))
            ForEach(0..<2, id: \.self) { row in
                HStack {
                    ForEach(0..<5, id: \.self) { col in
                        let i = row * 5 + col
                        let selected = store.categoryPastel[categoryId] == i
                        Button { store.setCategoryPastel(categoryId, i) } label: {
                            Circle().fill(realCalendarPastels[i]).frame(width: 40, height: 40)
                                .overlay(Circle().strokeBorder(.white, lineWidth: selected ? 2 : 0))
                                .overlay { if selected { RealSymbol("checkmark", size: 14.9).foregroundStyle(RealColors.plannerInk) } }
                        }
                        if col < 4 { Spacer() }
                    }
                }
            }
            Text("Kart şekli").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8C8C95))
            HStack(spacing: 10) {
                ForEach(["square", "rectangle"], id: \.self) { shape in
                    let selected = (store.categoryCardShape[categoryId] ?? "square") == shape
                    Button { store.setCategoryCardShape(categoryId, shape) } label: {
                        VStack(spacing: 8) {
                            RoundedRectangle(cornerRadius: 7).strokeBorder(selected ? .white : Color(argb: 0xFF8C8C95), lineWidth: 1.8)
                                .frame(width: shape == "square" ? 26 : 42, height: 26)
                            Text(shape == "square" ? "Kare" : "Dikdörtgen").font(RealFont.inter(13))
                                .foregroundStyle(selected ? .white : Color(argb: 0xFF8C8C95))
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(RoundedRectangle(cornerRadius: 18).fill(.white.opacity(selected ? 0.14 : 0.05)))
                        .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(.white.opacity(selected ? 0.7 : 0), lineWidth: 1))
                    }
                }
            }
            Button(action: onReorder) {
                Text("Hedefleri sırala").font(RealFont.inter(14)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.vertical, 13)
                    .background(RoundedRectangle(cornerRadius: 16).fill(.white.opacity(0.05)))
            }
        }
        .padding(18)
    }
}

private struct PasteLinkSheet: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let categoryId: Int64
    @State private var error = false

    var body: some View {
        VStack(spacing: 14) {
            RealSymbol("link", size: 25.3).foregroundStyle(.white)
                .frame(width: 58, height: 58)
                .background(Circle().fill(LinearGradient(colors: [Color(argb: 0xFF6FD6FF).opacity(0.38), Color(argb: 0xFF6FD6FF).opacity(0.12)],
                                                         startPoint: .topLeading, endPoint: .bottomTrailing)))
                .overlay(Circle().strokeBorder(Color(argb: 0xFF6FD6FF).opacity(0.45), lineWidth: 1))
            Text("Kategori linki").font(RealFont.inter(20, .semibold)).foregroundStyle(.white)
            Text(error ? "Geçerli bir web bağlantısı kopyala." : "Meet, Zoom veya başka bir web bağlantısını kopyala; klavye açmadan buradan yapıştır.")
                .font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFF9A9AA2)).multilineTextAlignment(.center)
            Button {
                let value = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if let url = URL(string: value), let scheme = url.scheme, scheme.hasPrefix("http"), url.host != nil {
                    store.setCategoryLink(categoryId, value)
                    dismiss()
                } else { error = true }
            } label: {
                Text("Panodan yapıştır").font(RealFont.inter(16, .semibold)).foregroundStyle(.black)
                    .frame(maxWidth: .infinity).frame(height: 52).background(Capsule().fill(.white))
            }
            Button("Vazgeç") { dismiss() }.font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFF9A9AA2)).frame(height: 44)
        }
        .padding(22)
        .presentationBackground(Color(argb: 0xFF111115))
    }
}

private extension View {
    func intro(_ visible: Bool, delay: Double, y: CGFloat = 0) -> some View {
        opacity(visible ? 1 : 0)
            .offset(y: visible ? 0 : y)
            .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.5).delay(delay), value: visible)
    }
}
