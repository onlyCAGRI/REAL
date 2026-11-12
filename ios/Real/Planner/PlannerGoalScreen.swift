import SwiftUI
import UIKit

/// A category's goals (Amaç). Drawn as a continuation of the category screen: its coloured header with the count,
/// one segment per goal and the week's days; the next goal as the highlighted card; link and people as two pills;
/// then the goals as REAL card rows (check, title, date, checklist line, completion note) and "Hedef ekle".
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
    @State private var weekPage = 0
    @State private var showPasteLink = false
    @State private var dragId: Int64?
    @State private var dragOrder: [Int64]?
    @State private var dragStartIndex = 0
    /// Ortak kategori people (owner first), loaded for Manage; empty for a private category.
    @State private var people: [SharedMember] = []
    @State private var showPeople = false
    /// Pencil on a completed goal: its Tamamlama notu is written right in the row, with the keyboard.
    @State private var noteGoalId: Int64?
    @State private var noteText = ""
    @FocusState private var noteFocused: Bool

    private let calendar = Calendar.real
    private static let sheet = Color(argb: 0xFF121216)
    private static let muted = Color(argb: 0xFF8C8C95)
    private static let faint = Color(argb: 0xFF55555D)
    private static let line = Color(argb: 0xFF2A2A31)
    private static let black = Color(argb: 0xFF0B0B0D)

    private var category: CustomCategory? { store.category(categoryId) }

    /// plannerCategoryDetailAccent: the category's own colour (as on its home card and in the monthly calendar).
    private var accent: Color {
        guard let c = category else { return Color(argb: 0xFF4558C8) }
        return c.visual.stripe.0
    }

    /// Text / marks drawn on the accent: dark on light colours, white on dark ones.
    private var onAccent: Color {
        categoryForeground(start: accent, end: accent) == .white ? .white : RealColors.plannerInk
    }

    private var members: [ReminderItem] {
        let order = store.goalOrder[categoryId] ?? []
        let position = Dictionary(uniqueKeysWithValues: order.enumerated().map { ($0.element, $0.offset) })
        return store.cards
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
        var note: String? = nil
    }

    private var goals: [Goal] {
        let f = DateFormatter()
        f.locale = I18n.locale
        f.setLocalizedDateFormatFromTemplate("dMMM")
        var list = members.map { r -> Goal in
            let due = r.alertAt.map { $0.date } ?? r.plannedDateEpochDay.map { calendar.startOfDay(epochDay: $0) }
            let items = r.isChecklist ? r.checklistItems.filter { !$0.text.trimmingCharacters(in: .whitespaces).isEmpty } : []
            let checked = items.filter(\.isCompleted).count
            let caption = [due.map { f.string(from: $0) }, items.isEmpty ? nil : "\(checked)/\(items.count)"]
                .compactMap { $0 }.joined(separator: "  ·  ")
            return Goal(id: r.id, title: r.text.trimmingCharacters(in: .whitespaces).isEmpty ? t("Başlıksız") : r.text, caption: caption,
                        done: r.isCompleted,
                        progress: (!r.isCompleted && !items.isEmpty && checked > 0) ? Double(checked) / Double(items.count) : nil,
                        note: r.completionNote)
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
        if calendar.isDate(monday, inSameDayAs: current) { return t("Bu hafta") }
        if calendar.isDate(monday, inSameDayAs: calendar.date(byAdding: .weekOfYear, value: -1, to: current)!) { return t("Geçen hafta") }
        let sunday = calendar.date(byAdding: .day, value: 6, to: monday)!
        let f = DateFormatter()
        f.locale = I18n.locale
        f.setLocalizedDateFormatFromTemplate("dMMM")
        return "\(f.string(from: monday)) – \(f.string(from: sunday))"
    }

    var body: some View {
        let goals = self.goals
        let total = goals.count
        let doneCount = goals.filter(\.done).count
        let next = goals.first { !$0.done }
        ZStack(alignment: .bottom) {
            Color.black.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header(goals: goals, doneCount: doneCount, total: total)
                    VStack(alignment: .leading, spacing: 0) {
                        if let next { nextCard(next).intro(intro, delay: 0.10, y: 14).padding(.bottom, 10) }
                        extras.intro(intro, delay: 0.16, y: 14)
                        goalList(goals: goals, nextId: next?.id)
                    }
                    .padding(.horizontal, 16).padding(.top, 14)
                    Color.clear.frame(height: 120)
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
        .realOverlay(isPresented: $showPeople) {
            if let category { SharedCategorySheet(category: category, calendarLinked: CalendarCategoryLinks.shared.hasCategory(category.id)) }
        }
        .task(id: "\(category?.sharedId ?? "")/\(showPeople)") {
            guard !showPeople, let category, category.isShared else { people = []; return }
            people = (try? await SharedCategorySync.shared.members(category)) ?? people
        }
    }

    /// The category's own header, as on its card list: back and ⋯ in glass rounds, then what the goals add up to.
    private func header(goals: [Goal], doneCount: Int, total: Int) -> some View {
        let fg = category?.visual.foreground ?? .white
        let shape = UnevenRoundedRectangle(bottomLeadingRadius: 34, bottomTrailingRadius: 34, style: .continuous)
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                headerRound("chevron.left", label: t("Geri"), fg: fg) { dismiss() }
                Spacer()
                headerRound("ellipsis", label: t("Görünüm ve sıralama"), fg: fg) { stylePopup = true }
            }
            HStack(spacing: 8) {
                if let symbol = category?.icon.symbol, !symbol.isEmpty { RealSymbol(symbol, size: 14) }
                Text((category?.name ?? "") + "  ·  " + t("Amaç")).font(RealFont.inter(13, .medium)).lineLimit(1)
            }
            .foregroundStyle(fg.opacity(0.8))
            .padding(.top, 22)
            if total == 0 {
                Text(t("Henüz hedef yok")).font(RealFont.inter(30, .medium)).kerning(-0.8).foregroundStyle(fg).padding(.top, 6)
            } else {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("\(doneCount)/\(total)").font(RealFont.inter(44, .medium)).kerning(-1.4).foregroundStyle(fg)
                        .contentTransition(.numericText())
                    Text(t("hedef tamamlandı")).font(RealFont.inter(14)).foregroundStyle(fg.opacity(0.8))
                }
                .padding(.top, 4)
                segments(goals, fg: fg).padding(.top, 14)
            }
            weekStrip(fg: fg).padding(.top, 18)
        }
        .padding(.horizontal, 18).padding(.top, 8).padding(.bottom, 20)
        // Runs up behind the status bar, and stays filled when the list is pulled down.
        .background(shape.fill(category?.visual.gradient ?? LinearGradient(colors: [accent, accent], startPoint: .top, endPoint: .bottom))
            .padding(.top, -900))
        .intro(intro, delay: 0)
    }

    private func headerRound(_ symbol: String, label: String, fg: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            RealSymbol(symbol, size: 18).foregroundStyle(fg)
                .frame(width: 40, height: 40)
                .background(Circle().fill(fg.opacity(0.16)))
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    /// One segment per goal, in the goals' order; a long list becomes one bar.
    @ViewBuilder
    private func segments(_ goals: [Goal], fg: Color) -> some View {
        if goals.count <= 12 {
            HStack(spacing: 5) {
                ForEach(goals) { goal in
                    Capsule().fill(fg.opacity(goal.done ? 1 : 0.28)).frame(height: 6)
                }
            }
            .animation(.easeOut(duration: 0.3), value: goals.map(\.done))
        } else {
            let part = Double(goals.filter(\.done).count) / Double(goals.count)
            Capsule().fill(fg.opacity(0.28)).frame(height: 6)
                .overlay(alignment: .leading) { GeometryReader { g in Capsule().fill(fg).frame(width: g.size.width * part) } }
        }
    }

    /// The week's seven days as dots (filled: something was completed that day), with earlier weeks one tap back.
    private func weekStrip(fg: Color) -> some View {
        let weeks = self.weeks
        let index = min(max(weekPage, 0), max(weeks.count - 1, 0))
        let week = weeks.indices.contains(index) ? weeks[index] : nil
        let symbols = { () -> [String] in
            var c = calendar
            c.locale = I18n.locale
            let s = c.veryShortStandaloneWeekdaySymbols
            return (0..<7).map { s[($0 + 1) % 7] }   // Monday first
        }()
        return HStack(spacing: 2) {
            Button { withAnimation(.easeOut(duration: 0.2)) { weekPage = index - 1 } } label: {
                RealSymbol("chevron.left", size: 11).frame(width: 26, height: 36).contentShape(Rectangle())
            }
            .buttonStyle(.plain).disabled(index == 0).opacity(index == 0 ? 0.3 : 0.85)
            .accessibilityLabel(t("Önceki hafta"))
            Text("\(week?.title ?? "")  ·  \(week?.done ?? 0)").font(RealFont.inter(12, .medium)).lineLimit(1)
            if index < weeks.count - 1 {
                Button { withAnimation(.easeOut(duration: 0.2)) { weekPage = index + 1 } } label: {
                    RealSymbol("chevron.right", size: 11).frame(width: 26, height: 36).contentShape(Rectangle())
                }
                .buttonStyle(.plain).opacity(0.85)
                .accessibilityLabel(t("Sonraki hafta"))
            }
            Spacer(minLength: 8)
            HStack(spacing: 11) {
                ForEach(0..<7, id: \.self) { i in
                    let value = (week?.activity.indices.contains(i) ?? false) ? (week?.activity[i] ?? 0) : 0
                    let today = week?.todayIndex == i
                    VStack(spacing: 5) {
                        Text(symbols[i]).font(today ? RealFont.inter(11, .semibold) : RealFont.inter(11)).opacity(today ? 1 : 0.65)
                        Circle().fill(fg.opacity(value > 0 ? 1 : 0.28)).frame(width: 8, height: 8)
                            .overlay(Circle().strokeBorder(fg, lineWidth: today && value == 0 ? 1 : 0))
                    }
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(t("{0} tamamlandı", week?.done ?? 0))
        }
        .foregroundStyle(fg)
        .padding(.leading, -8)
    }

    private func complete(_ id: Int64) {
        withAnimation(.spring(duration: 0.35)) { store.toggleCompleted(id) }
    }

    private func progressLine(_ part: Double, track: Color, fill: Color) -> some View {
        Capsule().fill(track).frame(height: 3)
            .overlay(alignment: .leading) { GeometryReader { g in Capsule().fill(fill).frame(width: g.size.width * part) } }
    }

    /// The first goal that is not done, in the category's colour, with its own "done" button.
    private func nextCard(_ goal: Goal) -> some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 0) {
                Text(t("Sıradaki")).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.7))
                Text(goal.title).font(RealFont.inter(18)).foregroundStyle(.white).lineLimit(2).padding(.top, 2)
                if !goal.caption.isEmpty {
                    Text(goal.caption).font(RealType.cardMetaPrimary).foregroundStyle(.white.opacity(0.78)).padding(.top, 3)
                }
                if let part = goal.progress { progressLine(part, track: .white.opacity(0.22), fill: .white).padding(.top, 10) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture { path.append(.detail(goal.id)) }
            Button { complete(goal.id) } label: {
                RealSymbol("checkmark", size: 18).foregroundStyle(Self.black)
                    .frame(width: 44, height: 44).background(Circle().fill(.white))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(t("Tamamlandı olarak işaretle"))
        }
        .padding(.leading, 18).padding(.trailing, 14).padding(.vertical, 14)
        .background {
            ZStack {
                shape.fill(RealColors.cardSurface)
                shape.fill(accent.opacity(0.45))
            }
        }
        .overlay(shape.strokeBorder(accent.lerp(.white, 0.55), lineWidth: 1.5))
    }

    private func pill<Content: View>(label: String, action: @escaping () -> Void, @ViewBuilder content: () -> Content) -> some View {
        Button(action: action) {
            HStack(spacing: 8) { content() }
                .font(RealFont.inter(13)).foregroundStyle(RealColors.subtext)
                .padding(.horizontal, 14)
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                .background(Capsule().fill(RealColors.cardSurface))
                .overlay(Capsule().strokeBorder(RealColors.cardBorder, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    /// The category's link (a tap opens it, or asks for one) and its people.
    private var extras: some View {
        let link = store.categoryLinks[categoryId] ?? ""
        let host = URL(string: link)?.host?.replacingOccurrences(of: "www.", with: "") ?? link
        let count = max(people.count, 1)
        return HStack(spacing: 8) {
            pill(label: link.isEmpty ? t("Link yapıştır") : host, action: {
                if let url = URL(string: link), !link.isEmpty { openURL(url) } else { showPasteLink = true }
            }) {
                RealSymbol("link", size: 14)
                Text(link.isEmpty ? t("Link yapıştır") : host).lineLimit(1)
            }
            .contextMenu {
                if !link.isEmpty { Button(t("Linki değiştir")) { showPasteLink = true } }
            }
            pill(label: people.isEmpty ? t("Kişi ekle") : t("{0} kişi", count), action: { showPeople = true }) {
                HStack(spacing: -6) {
                    if people.isEmpty {
                        ProfileAvatar(name: store.ownerName, size: 22)
                    } else {
                        ForEach(people.prefix(3)) { p in
                            ConnectionAvatar(name: p.name, key: p.uid, size: 22, pictureVersion: p.pictureVersion)
                                .overlay(Circle().strokeBorder(RealColors.cardSurface, lineWidth: 1.5))
                        }
                    }
                }
                Text(people.isEmpty ? t("Kişi ekle") : t("{0} kişi", count)).lineLimit(1)
            }
            .disabled(category == nil)
        }
    }

    private func goalList(goals: [Goal], nextId: Int64?) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text(t("Hedefler")).font(RealType.sectionTitle).foregroundStyle(.white)
                Spacer()
                if goals.count > 1 {
                    Button(reorderMode ? t("Bitti") : t("Sırala")) { withAnimation(.easeOut(duration: 0.2)) { reorderMode.toggle() } }
                        .buttonStyle(.plain)
                        .font(RealFont.inter(13, .medium)).foregroundStyle(reorderMode ? .white : RealColors.subtext)
                        .frame(minWidth: 44, minHeight: 44, alignment: .trailing)
                        .accessibilityLabel(reorderMode ? t("Sıralamayı bitir") : t("Hedefleri sırala"))
                }
            }
            .padding(.horizontal, 6).padding(.top, 14)
            ForEach(Array(goals.enumerated()), id: \.element.id) { index, goal in
                goalRow(goal, next: goal.id == nextId)
                    .opacity(intro ? 1 : 0)
                    .animation(.easeOut(duration: 0.38).delay(0.22 + Double(min(index, 6)) * 0.05), value: intro)
            }
            Button(action: newGoal) {
                HStack(spacing: 8) {
                    RealSymbol("plus", size: 15)
                    Text(t("Hedef ekle")).font(RealFont.inter(14))
                }
                .foregroundStyle(RealColors.subtext)
                .frame(maxWidth: .infinity, minHeight: 52)
                .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .strokeBorder(Color(argb: 0xFF3A3A42), style: StrokeStyle(lineWidth: 1, dash: [6, 5])))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Button { path.append(.customCategory(categoryId)) } label: {
                Text(t("Kategorinin kartlarını aç")).font(RealFont.inter(13)).foregroundStyle(RealColors.textMuted)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.plain)
        }
    }

    private func startNote(_ goal: Goal) {
        saveNote()
        noteText = goal.note ?? ""
        noteGoalId = goal.id
    }

    /// Saves the note being written (empty removes it) and closes the field.
    private func saveNote() {
        guard let id = noteGoalId else { return }
        noteGoalId = nil
        noteFocused = false
        let text = String(noteText.trimmingCharacters(in: .whitespacesAndNewlines).prefix(140))
        if text != (store.reminder(id)?.completionNote ?? "") {
            store.mutate(id) { $0.completionNote = text.isEmpty ? nil : text; $0.updatedAt = .now }
        }
    }

    /// A goal as a REAL card row: the check, the title and its date / checklist count, then what belongs to its state —
    /// the checklist line while open, the Tamamlama notu once done. In "Sırala" a handle on the right moves it.
    private func goalRow(_ goal: Goal, next: Bool) -> some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        let dragging = dragId == goal.id
        let caption = goal.done ? t("Tamamlandı") : goal.caption
        return HStack(alignment: .top, spacing: 6) {
            Button { complete(goal.id) } label: {
                ZStack {
                    if goal.done {
                        Circle().fill(accent)
                        RealSymbol("checkmark", size: 12).foregroundStyle(onAccent)
                    } else {
                        Circle().strokeBorder(next ? accent : Self.faint, lineWidth: 1.5)
                    }
                }
                .frame(width: 24, height: 24)
                .frame(width: 40, height: 46)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(goal.done ? t("Tamamlanmadı olarak işaretle") : t("Tamamlandı olarak işaretle"))
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(goal.title).font(RealType.cardTitle)
                        .foregroundStyle(goal.done ? RealColors.textMuted : RealColors.text)
                        .strikethrough(goal.done, color: RealColors.textMuted)
                        .lineLimit(3)
                    if !caption.isEmpty {
                        Text(caption).font(RealType.cardMetaPrimary).foregroundStyle(goal.done ? RealColors.textMuted : RealColors.accentBlue)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .onTapGesture { path.append(.detail(goal.id)) }
                // Tamamlama notu under a completed goal, written in place.
                if goal.done && noteGoalId == goal.id {
                    TextField(t("Not yaz…"), text: $noteText, axis: .vertical)
                        .font(RealFont.inter(13))
                        .foregroundStyle(Color(argb: 0xFFE6E6EC)).tint(accent)
                        .focused($noteFocused)
                        .onChange(of: noteFocused) { _, focused in if !focused { saveNote() } }
                        .submitLabel(.done)
                        .onChange(of: noteText) { _, t in
                            // Return finishes the note (a vertical field would otherwise add a line).
                            if t.contains(where: \.isNewline) { noteText = t.filter { !$0.isNewline }; saveNote() }
                            else if t.count > 140 { noteText = String(t.prefix(140)) }
                        }
                        .padding(.top, 7)
                        .onAppear { noteFocused = true }
                } else if goal.done {
                    Text(goal.note ?? t("Not yaz…"))
                        .font(RealFont.inter(13))
                        .foregroundStyle(goal.note == nil ? Self.faint : RealColors.subtext)
                        .frame(maxWidth: .infinity, minHeight: 30, alignment: .leading)
                        .contentShape(Rectangle())
                        .onTapGesture { startNote(goal) }
                        .accessibilityLabel(goal.note.map { t("Tamamlama notu: {0}", $0) } ?? t("Not yaz…"))
                        .accessibilityAddTraits(.isButton)
                }
                if let part = goal.progress, !goal.done {
                    progressLine(part, track: .white.opacity(0.10), fill: accent).padding(.top, 9)
                }
            }
            .padding(.vertical, 13)
            if reorderMode {
                RealSymbol("line.3.horizontal", size: 16)
                    .foregroundStyle(dragging ? accent : RealColors.textMuted)
                    .frame(width: 44, height: 46)
                    .contentShape(Rectangle())
                    .gesture(reorderGesture(goal.id))
                    .accessibilityLabel(t("Sürükleyerek sırala"))
            }
        }
        .padding(.leading, 6).padding(.trailing, reorderMode ? 4 : 16)
        .background(shape.fill(dragging ? Color(argb: 0xFF1B1B21) : RealColors.cardSurface))
        .overlay(shape.strokeBorder(RealColors.cardBorder, lineWidth: 1))
        .scaleEffect(dragging ? 1.02 : 1)
        .zIndex(dragging ? 1 : 0)
    }

    /// Reorder mode: drag the handle; every ~76pt of travel (a row) swaps with the neighbour.
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
                let steps = Int((value.translation.height / 76).rounded(.towardZero))
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

    /// One active goal per category is free; more is Pro (the same rule as Android's PlannerScreen).
    private func newGoal() {
        if goals.contains(where: { !$0.done }) { ProAccess.shared.perform(.plannerGoals) { composing = true } } else { composing = true }
    }

    private var goalComposer: some View {
        GoalComposerWindow(accent: accent, categoryName: category?.name ?? "", onDismiss: { composing = false }) { title in
            composing = false
            store.addReminder(text: title, categoryId: categoryId)
        }
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
                Text(t("{0} için yeni hedef", categoryName)).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8C8C95)).lineLimit(1)
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
            Text(t("Kart şekli")).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8C8C95))
            HStack(spacing: 10) {
                ForEach(["square", "rectangle"], id: \.self) { shape in
                    let selected = (store.categoryCardShape[categoryId] ?? "square") == shape
                    Button { store.setCategoryCardShape(categoryId, shape) } label: {
                        VStack(spacing: 8) {
                            RoundedRectangle(cornerRadius: 7).strokeBorder(selected ? .white : Color(argb: 0xFF8C8C95), lineWidth: 1.8)
                                .frame(width: shape == "square" ? 26 : 42, height: 26)
                            Text(shape == "square" ? t("Kare") : t("Dikdörtgen")).font(RealFont.inter(13))
                                .foregroundStyle(selected ? .white : Color(argb: 0xFF8C8C95))
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(RoundedRectangle(cornerRadius: 18).fill(.white.opacity(selected ? 0.14 : 0.05)))
                        .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(.white.opacity(selected ? 0.7 : 0), lineWidth: 1))
                    }
                }
            }
            Button(action: onReorder) {
                Text(t("Hedefleri sırala")).font(RealFont.inter(14)).foregroundStyle(.white)
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
            Text(t("Kategori linki")).font(RealFont.inter(20, .semibold)).foregroundStyle(.white)
            Text(error ? t("Geçerli bir web bağlantısı kopyala.") : t("Meet, Zoom veya başka bir web bağlantısını kopyala; klavye açmadan buradan yapıştır."))
                .font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFF9A9AA2)).multilineTextAlignment(.center)
            Button {
                let value = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if let url = URL(string: value), let scheme = url.scheme, scheme.hasPrefix("http"), url.host != nil {
                    store.setCategoryLink(categoryId, value)
                    dismiss()
                } else { error = true }
            } label: {
                Text(t("Panodan yapıştır")).font(RealFont.inter(16, .semibold)).foregroundStyle(.black)
                    .frame(maxWidth: .infinity).frame(height: 52).background(Capsule().fill(.white))
            }
            Button(t("Vazgeç")) { dismiss() }.font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFF9A9AA2)).frame(height: 44)
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
