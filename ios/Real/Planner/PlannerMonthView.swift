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
    private let weekdays = ["Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"]

    var body: some View {
        let days = monthDays(month)
        let start = days.first!, end = calendar.date(byAdding: .day, value: 1, to: days.last!)!
        let all = store.reminders + externals(start, end)
        let events = Planner.events(all, rangeStart: start.epochMillis, rangeEnd: end.epochMillis, calendar: calendar)
        let byDay = Dictionary(grouping: events) { calendar.startOfDay(for: $0.startMillis.date) }
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
                        HStack(spacing: 4) {
                            ForEach(days[(row * 7)..<(row * 7 + 7)], id: \.self) { day in
                                tile(day, events: byDay[day] ?? [])
                            }
                        }
                    }
                }
                .id(month)
                .transition(.asymmetric(insertion: .move(edge: direction > 0 ? .trailing : .leading).combined(with: .opacity),
                                        removal: .move(edge: direction > 0 ? .leading : .trailing).combined(with: .opacity)))
                VStack(alignment: .leading, spacing: 4) {
                    Text(RealDates.longDay(selectedDay)).font(RealType.editorialItem).kerning(-1).foregroundStyle(Color(argb: 0xFFF3F1F4))
                    Text(picked.isEmpty ? "Bu gün için plan yok" : "\(picked.count) plan").font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF8C8C95))
                }
                .padding(.top, 14).padding(.horizontal, 4)
                VStack(spacing: 12) {
                    ForEach(picked) { event in
                        PlannerNext24Card(event: event, categories: store.categories, ownerName: store.ownerName, now: Date())
                            .onTapGesture { onOpenEvent(event.reminder.id) }
                    }
                }
                Color.clear.frame(height: 110)
            }
            .padding(.horizontal, 12).padding(.top, 14)
        }
        .clipped()
        .onAppear { month = calendar.startOfMonth(for: selectedDay) }
        .gesture(DragGesture(minimumDistance: 30).onEnded { v in
            guard abs(v.translation.width) > abs(v.translation.height) * 1.5 else { return }
            changeMonth(v.translation.width < 0 ? 1 : -1)
        })
    }

    private var monthBar: some View {
        HStack {
            Button { changeMonth(-1) } label: { RealSymbol("chevron.left", size: 16).foregroundStyle(Color(argb: 0xFFB0B0B8)).frame(width: 40, height: 40) }
                .accessibilityLabel("Önceki ay")
            Spacer()
            Text(month.formatted(.dateTime.month(.wide).year().locale(Locale(identifier: "tr_TR"))).capitalized(with: Locale(identifier: "tr_TR")))
                .font(RealFont.inter(19, .semibold)).foregroundStyle(.white)
                .contentTransition(.opacity)
            Spacer()
            Button { changeMonth(1) } label: { RealSymbol("chevron.right", size: 16).foregroundStyle(Color(argb: 0xFFB0B0B8)).frame(width: 40, height: 40) }
                .accessibilityLabel("Sonraki ay")
        }
        .padding(.horizontal, 6)
        .liquidGlass(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func tile(_ day: Date, events: [PlannerEvent]) -> some View {
        let inMonth = calendar.isDate(day, equalTo: month, toGranularity: .month)
        let isToday = calendar.isDate(day, inSameDayAs: today)
        let isSelected = calendar.isDate(day, inSameDayAs: selectedDay)
        let open = events.filter { !$0.reminder.isCompleted }
        let colors = open.prefix(3).map(eventColor)
        // The day takes its first plan's real colour (a touch softened), with dark or light text by its brightness.
        let pastel = inMonth ? colors.first.map { $0.lerp(Color(argb: 0xFFFFFBF4), 0.14) } : nil
        let ink = pastel.map { categoryForeground(start: $0, end: $0) == .white ? Color.white : Color(argb: 0xFF17181B) }
            ?? Color(argb: 0xFF17181B)
        let text: Color = !inMonth ? .white.opacity(0.2) : (pastel != nil ? ink : .white.opacity(isSelected || isToday ? 1 : 0.88))
        let shape = RoundedRectangle(cornerRadius: 13, style: .continuous)
        return Button {
            withAnimation(.spring(duration: 0.3)) { selectedDay = day }
        } label: {
            ZStack(alignment: .topLeading) {
                shape.fill(!inMonth ? Color(argb: 0xFF141517) : (pastel ?? Color(argb: 0xFF1B1C1E)))
                Text("\(calendar.component(.day, from: day))")
                    .font(RealFont.inter(15, isSelected ? .bold : (isToday ? .semibold : .regular))).foregroundStyle(text)
                    .padding(.leading, 9).padding(.top, 8)
                if inMonth && !colors.isEmpty {
                    HStack(spacing: 3) {
                        ForEach(Array(colors.enumerated()), id: \.offset) { _, c in
                            // Each plan keeps its own colour; a thin ring keeps it visible on a tinted day.
                            Circle().fill(c).overlay(Circle().strokeBorder(ink.opacity(pastel != nil ? 0.55 : 0), lineWidth: 0.8))
                                .frame(width: 6, height: 6)
                        }
                    }
                    .padding(.leading, 9).padding(.bottom, 8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                }
            }
            .aspectRatio(0.85, contentMode: .fit)
            .overlay {
                if inMonth && isSelected { shape.strokeBorder(pastel != nil ? ink : .white, lineWidth: 2) }
                else if inMonth && isToday { shape.strokeBorder((pastel != nil ? ink : .white).opacity(0.75), lineWidth: 1.2) }
            }
        }
        .buttonStyle(PressScaleStyle(scale: 0.95))
        .frame(maxWidth: .infinity)
        .accessibilityLabel("\(RealDates.longDay(day)), \(events.count) etkinlik\(isToday ? ", bugün" : "")")
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
