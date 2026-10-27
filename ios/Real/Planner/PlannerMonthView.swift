import SwiftUI

/// Takvim (PlannerCalendar.kt month grid): month bar with ‹ ›, weekday row, day tiles tinted with the day's main
/// category colour and up to three event dots, then the picked day's plans.
struct PlannerMonthView: View {
    @Environment(ReminderStore.self) private var store
    @Binding var selectedDay: Date
    let today: Date
    let externals: (Date, Date) -> [ReminderItem]
    let onOpenEvent: (Int64) -> Void

    @State private var month: Date = Calendar.real.startOfMonth(for: Date())
    @State private var direction: CGFloat = 1
    private let calendar = Calendar.real
    private let weekdays = ["Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"].map { t($0) }

    var body: some View {
        let days = monthDays(month)
        let start = days.first!, end = calendar.date(byAdding: .day, value: 1, to: days.last!)!
        let all = store.cards + externals(start, end)
        let events = Planner.events(all, rangeStart: start.epochMillis, rangeEnd: end.epochMillis, calendar: calendar)
        let byDay = Planner.byDay(events, calendar: calendar)
        let picked = (byDay[calendar.startOfDay(for: selectedDay)] ?? []).sorted { ($0.isDateOnly ? 0 : 1, $0.startMillis) < ($1.isDateOnly ? 0 : 1, $1.startMillis) }

        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                monthBar
                HStack(spacing: 4) {
                    ForEach(weekdays, id: \.self) { d in
                        Text(d).font(RealFont.inter(12, .medium)).foregroundStyle(Color(argb: 0xFF8E9094)).frame(maxWidth: .infinity)
                    }
                }
                VStack(spacing: 5) {
                    ForEach(0..<(days.count / 7), id: \.self) { row in
                        weekRow(Array(days[(row * 7)..<(row * 7 + 7)]), byDay: byDay)
                    }
                }
                .id(month)
                .transition(.asymmetric(insertion: .move(edge: direction > 0 ? .trailing : .leading).combined(with: .opacity),
                                        removal: .move(edge: direction > 0 ? .leading : .trailing).combined(with: .opacity)))
                VStack(alignment: .leading, spacing: 4) {
                    Text(RealDates.longDay(selectedDay)).font(RealType.editorialItem).kerning(-1).foregroundStyle(Color(argb: 0xFFF3F1F4))
                    Text(picked.isEmpty ? t("Bu gün için plan yok") : t("{0} plan", picked.count)).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF8C8C95))
                }
                .padding(.top, 14).padding(.horizontal, 4)
                VStack(spacing: 12) {
                    ForEach(picked) { event in
                        VStack(spacing: 0) {
                            PlannerNext24Card(event: event, categories: store.categories, ownerName: store.ownerName, now: Date(),
                                              onDurationTap: event.reminder.id < 0 ? nil : {
                                                  withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.26)) {
                                                      durationEditorId = durationEditorId == event.id ? nil : event.id
                                                  }
                                              })
                                .onTapGesture { onOpenEvent(event.reminder.id) }
                            if durationEditorId == event.id {
                                PlannerInlineEventEditor(reminderId: event.reminder.id)
                                    .transition(.opacity.combined(with: .move(edge: .top)))
                            }
                        }
                    }
                }
                Color.clear.frame(height: 110)
            }
            .padding(.horizontal, 12).padding(.top, 14)
        }
        .clipped()
        .onAppear { month = calendar.startOfMonth(for: selectedDay) }
        .onChange(of: selectedDay) { _, _ in durationEditorId = nil }
        .gesture(DragGesture(minimumDistance: 30).onEnded { v in
            guard abs(v.translation.width) > abs(v.translation.height) * 1.5 else { return }
            changeMonth(v.translation.width < 0 ? 1 : -1)
        })
    }

    @State private var durationEditorId: String?

    private var monthBar: some View {
        HStack {
            Button { changeMonth(-1) } label: { RealSymbol("chevron.left", size: 16).foregroundStyle(Color(argb: 0xFFB0B0B8)).frame(width: 40, height: 40) }
                .accessibilityLabel(t("Önceki ay"))
            Spacer()
            Text(month.formatted(.dateTime.month(.wide).year().locale(I18n.locale)).capitalized(with: I18n.locale))
                .font(RealFont.inter(19, .semibold)).foregroundStyle(.white)
                .contentTransition(.opacity)
            Spacer()
            Button { changeMonth(1) } label: { RealSymbol("chevron.right", size: 16).foregroundStyle(Color(argb: 0xFFB0B0B8)).frame(width: 40, height: 40) }
                .accessibilityLabel(t("Sonraki ay"))
        }
        .padding(.horizontal, 6)
        .liquidGlass(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private static let dotSize: CGFloat = 4.8
    private static let dotGap: CGFloat = 3.5

    /// Up to three dots a day; several-day events first so their dots line up day to day.
    private func dotEvents(_ day: Date, _ byDay: [Date: [PlannerEvent]]) -> [PlannerEvent] {
        guard calendar.isDate(day, equalTo: month, toGranularity: .month) else { return [] }
        return (byDay[day] ?? []).filter { !$0.reminder.isCompleted }
            .sorted { ($0.isMultiDay(calendar) ? 0 : 1, $0.startMillis, $0.reminder.id) < ($1.isMultiDay(calendar) ? 0 : 1, $1.startMillis, $1.reminder.id) }
            .prefix(3).map { $0 }
    }

    /// One week: tiles, then a line joining the dots of several-day events (straight: a fixed dot slot per event all week).
    private func weekRow(_ week: [Date], byDay: [Date: [PlannerEvent]]) -> some View {
        let dots = week.map { dotEvents($0, byDay) }
        let colors = dots.map { $0.map(eventColor) }
        let spanKeys = Array(Set(dots.flatMap { $0 }.filter { $0.isMultiDay(calendar) }.map(\.id))).sorted()
        let order = dots.flatMap { $0 }.filter { $0.isMultiDay(calendar) }.map(\.id)
        var firstSeen: [String] = []
        for k in order where !firstSeen.contains(k) { firstSeen.append(k) }
        _ = spanKeys
        let spanSlot = Dictionary(uniqueKeysWithValues: firstSeen.prefix(3).enumerated().map { ($0.element, [1, 0, 2][$0.offset]) })
        let slots: [[Int]?] = dots.map { list -> [Int]? in
            guard list.contains(where: { spanSlot[$0.id] != nil }) else { return nil }
            let used = Set(list.compactMap { spanSlot[$0.id] })
            var free = (0...2).filter { !used.contains($0) }
            return list.map { e in spanSlot[e.id] ?? free.removeFirst() }
        }
        return HStack(spacing: 4) {
            ForEach(Array(week.enumerated()), id: \.element) { i, day in
                tile(day, events: byDay[day] ?? [], colors: colors[i], slots: slots[i])
            }
        }
        .overlay {
            Canvas { ctx, size in
                let gap: CGFloat = 4
                let tileW = (size.width - gap * 6) / 7
                let d = Self.dotSize, s = Self.dotGap
                func center(_ i: Int, _ k: Int) -> CGPoint {
                    let x = CGFloat(i) * (tileW + gap) + tileW - 7 - d / 2
                    if let slot = slots[i]?[safe: k] { return CGPoint(x: x, y: size.height / 2 + CGFloat(slot - 1) * (d + s)) }
                    let n = CGFloat(dots[i].count)
                    let total = d * n + s * (n - 1)
                    return CGPoint(x: x, y: (size.height - total) / 2 + d / 2 + CGFloat(k) * (d + s))
                }
                func numberGap(_ i: Int) -> ClosedRange<CGFloat> {
                    let digits = CGFloat(String(calendar.component(.day, from: week[i])).count)
                    let left = CGFloat(i) * (tileW + gap) + 9
                    return (left - 3)...(left + digits * 9 + 3)
                }
                func segment(_ a: CGPoint, _ b: CGPoint, _ color: Color, skip: ClosedRange<CGFloat>?) {
                    func draw(_ p: CGPoint, _ q: CGPoint) {
                        var path = Path(); path.move(to: p); path.addLine(to: q)
                        ctx.stroke(path, with: .color(color.opacity(0.9)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
                    }
                    guard let skip, b.x > skip.lowerBound, a.x < skip.upperBound else { draw(a, b); return }
                    func at(_ x: CGFloat) -> CGPoint { CGPoint(x: x, y: a.y + (b.y - a.y) * ((x - a.x) / (b.x - a.x))) }
                    if skip.lowerBound > a.x { draw(a, at(skip.lowerBound)) }
                    if skip.upperBound < b.x { draw(at(skip.upperBound), b) }
                }
                for (i, list) in dots.enumerated() {
                    for (k, e) in list.enumerated() where e.isMultiDay(calendar) {
                        let here = center(i, k)
                        let color = colors[i][k]
                        if i + 1 < dots.count, let next = dots[i + 1].firstIndex(where: { $0.id == e.id }) {
                            segment(here, center(i + 1, next), color, skip: numberGap(i + 1))
                        } else if i == 6, e.lastDay(calendar) > week[i],
                                  calendar.isDate(calendar.date(byAdding: .day, value: 1, to: week[i])!, equalTo: month, toGranularity: .month) {
                            segment(here, CGPoint(x: size.width, y: here.y), color, skip: nil)
                        }
                        if i == 0, e.startDay(calendar) < week[i],
                           calendar.isDate(calendar.date(byAdding: .day, value: -1, to: week[i])!, equalTo: month, toGranularity: .month) {
                            segment(CGPoint(x: 0, y: here.y), here, color, skip: numberGap(0))
                        }
                    }
                }
            }
            .allowsHitTesting(false)
        }
    }

    private func tile(_ day: Date, events: [PlannerEvent], colors: [Color], slots: [Int]?) -> some View {
        let inMonth = calendar.isDate(day, equalTo: month, toGranularity: .month)
        let isToday = calendar.isDate(day, inSameDayAs: today)
        let isSelected = calendar.isDate(day, inSameDayAs: selectedDay)
        // Days are neutral: before today faded, today framed in white, later days normal; the dots carry the colours.
        let isPast = day < calendar.startOfDay(for: today)
        let background: Color = !inMonth ? Color(argb: 0xFF141517) : (isPast ? Color(argb: 0xFF151618) : Color(argb: 0xFF222326))
        let text: Color = !inMonth ? .white.opacity(0.2) : (isSelected || isToday ? .white : (isPast ? .white.opacity(0.38) : .white.opacity(0.92)))
        let shape = RoundedRectangle(cornerRadius: 13, style: .continuous)
        return Button {
            withAnimation(.spring(duration: 0.3)) { selectedDay = day }
        } label: {
            ZStack {
                shape.fill(background)
                Text("\(calendar.component(.day, from: day))")
                    .font(RealFont.inter(15, isSelected ? .bold : (isToday ? .semibold : .regular))).foregroundStyle(text)
                    .padding(.leading, 9)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                if inMonth && !colors.isEmpty {
                    let rows = slots == nil ? colors.count : 3
                    ZStack(alignment: .top) {
                        ForEach(Array(colors.enumerated()), id: \.offset) { k, c in
                            Circle().fill(c).frame(width: Self.dotSize, height: Self.dotSize)
                                .offset(y: CGFloat(slots?[safe: k] ?? k) * (Self.dotSize + Self.dotGap))
                        }
                    }
                    .frame(width: Self.dotSize, height: Self.dotSize * CGFloat(rows) + Self.dotGap * CGFloat(rows - 1), alignment: .top)
                    .padding(.trailing, 7)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                }
            }
            .aspectRatio(0.85, contentMode: .fit)
            .overlay {
                if !inMonth { EmptyView() }
                else if isToday { shape.strokeBorder(.white, lineWidth: 2) }
                else if isSelected { shape.strokeBorder(.white.opacity(0.55), lineWidth: 1.5) }
                else { shape.strokeBorder(.white.opacity(isPast ? 0.04 : 0.07), lineWidth: 0.5) }
            }
        }
        .buttonStyle(PressScaleStyle(scale: 0.95))
        .frame(maxWidth: .infinity)
        .accessibilityLabel(t("{0}, {1} etkinlik", RealDates.longDay(day), events.count) + (isToday ? t(", bugün") : ""))
    }

    /// The card's category colour; without a category the colour the person picked (planner or card colour);
    /// otherwise an automatic colour that differs from card to card.
    private func eventColor(_ e: PlannerEvent) -> Color {
        let r = e.reminder
        if let c = store.category(r.customCategoryId) { return c.visual.stripe.0 }
        if let i = r.plannerColorIndex, realCalendarPastels.indices.contains(i) { return realCalendarPastels[i] }
        if let solid = r.customSolidColorArgb { return Color(argbInt: solid) }
        if let picked = paletteColor(r.accentStart) { return picked }
        return uncategorizedPastel(r.id)
    }

    private func changeMonth(_ step: Int) {
        direction = CGFloat(step)
        withAnimation(.timingCurve(0.2, 0, 0, 1, duration: 0.28)) {
            month = calendar.date(byAdding: .month, value: step, to: month)!
        }
    }

    /// Monday-first weeks covering the whole month (plannerMonthDays).
    private func monthDays(_ month: Date) -> [Date] {
        let first = calendar.startOfMonth(for: month)
        let weekday = (calendar.component(.weekday, from: first) + 5) % 7      // Monday = 0
        let start = calendar.date(byAdding: .day, value: -weekday, to: first)!
        let count = calendar.range(of: .day, in: .month, for: first)!.count
        let cells = Int(ceil(Double(weekday + count) / 7.0)) * 7
        return (0..<cells).map { calendar.date(byAdding: .day, value: $0, to: start)! }
    }
}

extension Calendar {
    func startOfMonth(for date: Date) -> Date {
        self.date(from: dateComponents([.year, .month], from: date)) ?? startOfDay(for: date)
    }
}

extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}
