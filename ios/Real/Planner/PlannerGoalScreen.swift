import SwiftUI
import UIKit

/// A category's goals (Amaç), drawn as one path ("Yol"): the count, a thick progress bar and the week's bars on top;
/// then every goal on a rail that fills in the category's colour as goals are completed — the finished ones first,
/// with their dates and notes, the next one as the large coloured card, the later ones after it, and "Hedef ekle".
struct PlannerGoalScreen: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let categoryId: Int64
    @Binding var path: [Route]

    @State private var intro = false
    @State private var composing = false
    @State private var reorderMode = false
    /// More than four finished goals fold into one row at the top of the path until it is tapped.
    @State private var showAllDone = false
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
    private static let track = Color(argb: 0xFF1C1C22)

    private var category: CustomCategory? { store.category(categoryId) }

    /// plannerCategoryDetailAccent: the category's own colour (as on its home card and in the monthly calendar).
    private var accent: Color {
        guard let c = category else { return Color(argb: 0xFF4558C8) }
        return c.visual.stripe.0
    }

    /// The accent as it is drawn on the black page (rail, nodes, bars): a near-black category colour is lifted so it shows.
    private var mark: Color {
        let c = accent.rgba
        return 0.299 * c.r + 0.587 * c.g + 0.114 * c.b < 0.22 ? accent.lerp(.white, 0.45) : accent
    }

    /// The check drawn on a filled node: dark on light colours, white on dark ones.
    private var onMark: Color {
        categoryForeground(start: mark, end: mark) == .white ? .white : RealColors.plannerInk
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
        /// Date and checklist count on one line, for the later rows.
        let caption: String
        /// "12 Kas, 10:00" and "1/3", shown apart on the next card.
        let due: String?
        let checklist: String?
        let done: Bool
        let progress: Double?
        var note: String? = nil
        var completedAt: Date? = nil
        /// When it was finished: "Bugün 11:35" or "10 Kas".
        var doneWhen: String? = nil
    }

    /// The path's order: what is done first, oldest at the top, then the open goals in the order the user gave them.
    private var goals: [Goal] {
        let day = DateFormatter()
        day.locale = I18n.locale
        day.setLocalizedDateFormatFromTemplate("dMMM")
        let time = DateFormatter()
        time.locale = I18n.locale
        time.dateFormat = "HH:mm"
        var list = members.map { r -> Goal in
            let alert = r.alertAt.map { $0.date }
            let dueDate = alert ?? r.plannedDateEpochDay.map { calendar.startOfDay(epochDay: $0) }
            let due = dueDate.map { alert == nil ? day.string(from: $0) : "\(day.string(from: $0)), \(time.string(from: $0))" }
            let items = r.isChecklist ? r.checklistItems.filter { !$0.text.trimmingCharacters(in: .whitespaces).isEmpty } : []
            let checked = items.filter(\.isCompleted).count
            let checklist = items.isEmpty ? nil : "\(checked)/\(items.count)"
            let finished = r.isCompleted ? r.completedAt.map { $0.date } : nil
            let doneWhen = finished.map { calendar.isDateInToday($0) ? "\(t("Bugün")) \(time.string(from: $0))" : day.string(from: $0) }
            return Goal(id: r.id, title: r.text.trimmingCharacters(in: .whitespaces).isEmpty ? t("Başlıksız") : r.text,
                        caption: [dueDate.map { day.string(from: $0) }, checklist].compactMap { $0 }.joined(separator: "  ·  "),
                        due: due, checklist: checklist, done: r.isCompleted,
                        progress: (!r.isCompleted && !items.isEmpty && checked > 0) ? Double(checked) / Double(items.count) : nil,
                        note: r.completionNote, completedAt: finished, doneWhen: doneWhen)
        }
        list = list.filter(\.done).sorted { ($0.completedAt ?? .distantPast) < ($1.completedAt ?? .distantPast) } + list.filter { !$0.done }
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
        let done = goals.filter(\.done)
        let open = goals.filter { !$0.done }
        ZStack(alignment: .bottom) {
            Color.black.ignoresSafeArea()
            LinearGradient(colors: [accent.opacity(0.20), .clear], startPoint: .top, endPoint: .bottom)
                .frame(height: 360).frame(maxHeight: .infinity, alignment: .top).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    topBar.intro(intro, delay: 0)
                    summary(doneCount: done.count, total: goals.count).intro(intro, delay: 0.05, y: 12)
                    extras.intro(intro, delay: 0.12, y: 12).padding(.top, 16)
                    pathSection(done: done, open: open, total: goals.count)
                    Color.clear.frame(height: 120)
                }
                .padding(.horizontal, 16)
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

    private var topBar: some View {
        HStack {
            barRound("chevron.left", label: t("Geri")) { dismiss() }
            Spacer(minLength: 8)
            HStack(spacing: 7) {
                if let symbol = category?.icon.symbol, !symbol.isEmpty { RealSymbol(symbol, size: 14).foregroundStyle(mark) }
                Text(category?.name ?? "").foregroundStyle(RealColors.text).lineLimit(1)
                Text("· " + t("Amaç")).foregroundStyle(RealColors.textMuted).fixedSize()
            }
            .font(RealFont.inter(14, .medium))
            Spacer(minLength: 8)
            barRound("ellipsis", label: t("Görünüm ve sıralama")) { stylePopup = true }
        }
        .padding(.top, 8)
    }

    private func barRound(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            RealSymbol(symbol, size: 18).foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .liquidGlass(Circle(), role: .chrome)
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    /// How far the category is: the count, the week's bars beside it, and one thick bar under both.
    private func summary(doneCount: Int, total: Int) -> some View {
        let part = total == 0 ? 0 : Double(doneCount) / Double(total)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 6) {
                    if total == 0 {
                        Text(t("Henüz hedef yok")).font(RealFont.inter(30, .medium)).kerning(-0.8).foregroundStyle(RealColors.text)
                    } else {
                        (Text("\(doneCount)").foregroundColor(RealColors.text) + Text("/\(total)").foregroundColor(Self.faint))
                            .font(RealFont.inter(54)).kerning(-2.4)
                            .contentTransition(.numericText())
                        Text(t("hedef tamamlandı")).font(RealFont.inter(13)).foregroundStyle(RealColors.subtext)
                    }
                }
                Spacer(minLength: 12)
                weekBlock
            }
            Capsule().fill(Self.track).frame(height: 14)
                .overlay(alignment: .leading) {
                    GeometryReader { g in Capsule().fill(mark).frame(width: part > 0 ? max(14, g.size.width * part) : 0) }
                }
                .overlay(alignment: .trailing) {
                    if total > 0 && part < 0.85 {
                        Text(t("%{0}", Int((part * 100).rounded()))).font(RealFont.inter(11)).foregroundStyle(RealColors.textMuted).padding(.trailing, 8)
                    }
                }
                .animation(.spring(duration: 0.5), value: part)
                .padding(.top, 18)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, 4).padding(.top, 22)
    }

    /// The week's seven days as small bars (how many goals were finished each day). A tap steps back a week.
    private var weekBlock: some View {
        let weeks = self.weeks
        let index = min(max(weekPage, 0), max(weeks.count - 1, 0))
        let week = weeks.indices.contains(index) ? weeks[index] : nil
        let top = max(1, week?.activity.max() ?? 1)
        return Button {
            guard weeks.count > 1 else { return }
            withAnimation(.easeOut(duration: 0.2)) { weekPage = index == 0 ? weeks.count - 1 : index - 1 }
        } label: {
            VStack(alignment: .trailing, spacing: 8) {
                HStack(alignment: .bottom, spacing: 6) {
                    ForEach(0..<7, id: \.self) { i in
                        let value = (week?.activity.indices.contains(i) ?? false) ? (week?.activity[i] ?? 0) : 0
                        let strong = week?.todayIndex == i || week?.todayIndex == -1
                        Capsule().fill(value == 0 ? Self.track : mark.opacity(strong ? 1 : 0.6))
                            .frame(width: 7, height: value == 0 ? 5 : 10 + 24 * CGFloat(value) / CGFloat(top))
                    }
                }
                .frame(height: 36, alignment: .bottom)
                HStack(spacing: 4) {
                    if weeks.count > 1 { RealSymbol("chevron.left", size: 9) }
                    Text("\(week?.title ?? "") \(week?.done ?? 0)")
                }
                .font(RealFont.inter(13)).foregroundStyle(RealColors.subtext)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(t("{0}: {1} tamamlandı. Önceki hafta için dokun.", week?.title ?? "", week?.done ?? 0))
    }

    private func complete(_ id: Int64) {
        withAnimation(.spring(duration: 0.35)) { store.toggleCompleted(id) }
    }

    private func progressLine(_ part: Double, track: Color, fill: Color) -> some View {
        Capsule().fill(track).frame(height: 4)
            .overlay(alignment: .leading) { GeometryReader { g in Capsule().fill(fill).frame(width: g.size.width * part) } }
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

    /// One step of the path: its node on the rail, and its card. [rail] is the colour of the stretch below the node
    /// (nil: the path ends here); it runs on under the next row's node so the line has no gaps.
    private func pathRow<Node: View, Card: View>(rail: Color?, @ViewBuilder node: () -> Node, @ViewBuilder card: () -> Card) -> some View {
        HStack(alignment: .top, spacing: 12) {
            node().frame(width: 24, height: 24).padding(.top, 10)
            card().frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.bottom, 10)
        .background(alignment: .topLeading) {
            if let rail {
                Rectangle().fill(rail).frame(width: 6).padding(.leading, 9).padding(.top, 22).padding(.bottom, -22)
            }
        }
    }

    private func pathSection(done: [Goal], open: [Goal], total: Int) -> some View {
        let hidden = (done.count > 4 && !showAllDone) ? done.count - 2 : 0
        let shown = Array(done.dropFirst(hidden))
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text(t("Hedef yolu")).font(RealType.sectionTitle).foregroundStyle(.white)
                Spacer()
                if open.count > 1 {
                    Button(reorderMode ? t("Bitti") : t("Sırala")) { withAnimation(.easeOut(duration: 0.2)) { reorderMode.toggle() } }
                        .buttonStyle(.plain)
                        .font(RealFont.inter(13, .medium)).foregroundStyle(reorderMode ? .white : RealColors.subtext)
                        .frame(minWidth: 44, minHeight: 44, alignment: .trailing)
                        .accessibilityLabel(reorderMode ? t("Sıralamayı bitir") : t("Hedefleri sırala"))
                }
            }
            .padding(.horizontal, 4).padding(.top, 16).padding(.bottom, 4)
            if hidden > 0 {
                pathRow(rail: mark) {
                    Circle().fill(mark).frame(width: 10, height: 10)
                } card: {
                    Button { withAnimation(.easeOut(duration: 0.25)) { showAllDone = true } } label: {
                        Text(t("Önceki {0} hedefi göster", hidden)).font(RealFont.inter(13, .medium)).foregroundStyle(RealColors.subtext)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            ForEach(shown) { goal in
                pathRow(rail: mark) { node(goal, state: .done) } card: { doneCard(goal) }
            }
            ForEach(Array(open.enumerated()), id: \.element.id) { index, goal in
                pathRow(rail: Self.track) {
                    node(goal, state: index == 0 ? .next : .later)
                } card: {
                    if index == 0 && !reorderMode {
                        nextCard(goal, number: done.count + 1, total: total)
                    } else {
                        laterCard(goal)
                    }
                }
                .zIndex(dragId == goal.id ? 1 : 0)
            }
            pathRow(rail: nil) {
                ZStack {
                    Circle().strokeBorder(Color(argb: 0xFF3A3A42), style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
                    RealSymbol("plus", size: 10).foregroundStyle(RealColors.textMuted)
                }
            } card: {
                Button(action: newGoal) {
                    Text(t("Hedef ekle")).font(RealFont.inter(14)).foregroundStyle(RealColors.subtext)
                        .padding(.horizontal, 16)
                        .frame(maxWidth: .infinity, minHeight: 46, alignment: .leading)
                        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .strokeBorder(Color(argb: 0xFF3A3A42), style: StrokeStyle(lineWidth: 1, dash: [6, 5])))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            Button { path.append(.customCategory(categoryId)) } label: {
                Text(t("Kategorinin kartlarını aç")).font(RealFont.inter(13)).foregroundStyle(RealColors.textMuted)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.plain)
            .padding(.top, 6)
        }
        .opacity(intro ? 1 : 0)
        .animation(.easeOut(duration: 0.4).delay(0.2), value: intro)
    }

    private enum Step { case done, next, later }

    /// The goal's mark on the rail; a tap completes it (or takes the completion back).
    private func node(_ goal: Goal, state: Step) -> some View {
        Button { complete(goal.id) } label: {
            ZStack {
                switch state {
                case .done:
                    Circle().fill(mark)
                    RealSymbol("checkmark", size: 12).foregroundStyle(onMark)
                case .next:
                    Circle().fill(.black)
                    Circle().strokeBorder(mark, lineWidth: 6)
                case .later:
                    Circle().fill(.black)
                    Circle().strokeBorder(Color(argb: 0xFF3A3A42), lineWidth: 2)
                }
            }
            .frame(width: 24, height: 24)
            .contentShape(Rectangle().inset(by: -10))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(goal.done ? t("Tamamlanmadı olarak işaretle") : t("Tamamlandı olarak işaretle"))
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

    /// A finished goal stays on the path: its title, when it was done, and its Tamamlama notu (written in place).
    private func doneCard(_ goal: Goal) -> some View {
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(goal.title).font(RealType.cardTitle).foregroundStyle(Color(argb: 0xFFC9C9D0)).lineLimit(2)
                Spacer(minLength: 0)
                if let when = goal.doneWhen {
                    Text(when).font(RealType.cardMetaPrimary).foregroundStyle(RealColors.textMuted).fixedSize()
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { path.append(.detail(goal.id)) }
            if noteGoalId == goal.id {
                TextField(t("Not yaz…"), text: $noteText, axis: .vertical)
                    .font(RealFont.inter(13))
                    .foregroundStyle(Color(argb: 0xFFE6E6EC)).tint(mark)
                    .focused($noteFocused)
                    .onChange(of: noteFocused) { _, focused in if !focused { saveNote() } }
                    .submitLabel(.done)
                    .onChange(of: noteText) { _, t in
                        // Return finishes the note (a vertical field would otherwise add a line).
                        if t.contains(where: \.isNewline) { noteText = t.filter { !$0.isNewline }; saveNote() }
                        else if t.count > 140 { noteText = String(t.prefix(140)) }
                    }
                    .padding(.top, 6)
                    .onAppear { noteFocused = true }
            } else {
                Text(goal.note ?? t("Not yaz…"))
                    .font(RealFont.inter(13))
                    .foregroundStyle(goal.note == nil ? Self.faint : RealColors.subtext)
                    .frame(maxWidth: .infinity, minHeight: 26, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture { startNote(goal) }
                    .accessibilityLabel(goal.note.map { t("Tamamlama notu: {0}", $0) } ?? t("Not yaz…"))
                    .accessibilityAddTraits(.isButton)
            }
        }
        .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 8)
        .background(shape.fill(RealColors.cardSurface))
        .overlay(shape.strokeBorder(RealColors.cardBorder, lineWidth: 1))
    }

    /// The first open goal, in the category's own colours, with its "Tamamla".
    private func nextCard(_ goal: Goal, number: Int, total: Int) -> some View {
        let fg = category?.visual.foreground ?? .white
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(t("Sıradaki")).font(RealFont.inter(12, .medium)).foregroundStyle(fg)
                    .padding(.horizontal, 10).padding(.vertical, 4)
                    .background(Capsule().fill(fg.opacity(0.18)))
                Spacer()
                Text("\(number) / \(total)").font(RealFont.inter(12)).foregroundStyle(fg.opacity(0.75))
            }
            Text(goal.title).font(RealFont.inter(24)).kerning(-0.8).foregroundStyle(fg).lineLimit(3).padding(.top, 16)
            if goal.due != nil || goal.checklist != nil {
                HStack(spacing: 12) {
                    if let due = goal.due {
                        HStack(spacing: 5) { RealSymbol("calendar", size: 14); Text(due) }
                    }
                    if let list = goal.checklist {
                        HStack(spacing: 5) { RealSymbol("checklist", size: 14); Text(list) }
                    }
                }
                .font(RealFont.inter(13)).foregroundStyle(fg.opacity(0.85))
                .padding(.top, 8)
            }
            if let part = goal.progress { progressLine(part, track: fg.opacity(0.24), fill: fg).padding(.top, 12) }
            HStack {
                Spacer()
                Button { complete(goal.id) } label: {
                    HStack(spacing: 6) {
                        RealSymbol("checkmark", size: 15)
                        Text(t("Tamamla")).font(RealFont.inter(14, .semibold))
                    }
                    .foregroundStyle(fg == .white ? RealColors.plannerInk : Color.white)
                    .padding(.horizontal, 16).frame(height: 44)
                    .background(Capsule().fill(fg))
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 14)
        }
        .padding(.leading, 18).padding(.trailing, 16).padding(.top, 16).padding(.bottom, 14)
        .background(shape.fill(category?.visual.gradient ?? LinearGradient(colors: [accent, accent], startPoint: .top, endPoint: .bottom)))
        .contentShape(shape)
        .onTapGesture { path.append(.detail(goal.id)) }
    }

    /// A goal further down the path; in "Sırala" a handle on the right moves it.
    private func laterCard(_ goal: Goal) -> some View {
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        let dragging = dragId == goal.id
        return HStack(spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(goal.title).font(RealType.cardTitle).foregroundStyle(RealColors.text).lineLimit(2)
                Spacer(minLength: 0)
                if !goal.caption.isEmpty {
                    Text(goal.caption).font(RealType.cardMetaPrimary).foregroundStyle(RealColors.accentBlue).fixedSize()
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture { path.append(.detail(goal.id)) }
            if reorderMode {
                RealSymbol("line.3.horizontal", size: 16)
                    .foregroundStyle(dragging ? mark : RealColors.textMuted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
                    .gesture(reorderGesture(goal.id))
                    .accessibilityLabel(t("Sürükleyerek sırala"))
            }
        }
        .padding(.leading, 16).padding(.trailing, reorderMode ? 2 : 16)
        .frame(minHeight: 46)
        .background(shape.fill(dragging ? Color(argb: 0xFF1B1B21) : Color.black))
        .overlay(shape.strokeBorder(RealColors.cardBorder, lineWidth: 1))
        .scaleEffect(dragging ? 1.02 : 1)
    }

    /// Reorder mode: drag the handle; every ~58pt of travel (a row) swaps with the neighbour. Only open goals move:
    /// the finished ones keep the top of the path.
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
                let steps = Int((value.translation.height / 58).rounded(.towardZero))
                let firstOpen = goals.filter(\.done).count
                let target = min(max(dragStartIndex + steps, firstOpen), order.count - 1)
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
