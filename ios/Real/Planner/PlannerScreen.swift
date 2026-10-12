import SwiftUI
import UIKit
import Combine

/// Takvim, "New" daily presentation (PlannerNewDailyView.kt): greeting, Focus carousel of starred cards
/// and category progress cards, the snapping date ruler, all-day notes and the compressed timeline.
struct PlannerScreen: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.navNamespace) private var nav
    @Binding var path: [Route]
    @State private var selectedDay: Date
    @State private var introVisible = false
    @State private var addRequest: PlannerAddRequest?
    @State private var now = Date()
    /// Last active Planner view mode (rememberPlannerViewModePreference): "hours_24" or "daily" (New).
    @AppStorage("planner_view_mode") private var modeRaw = "hours_24"

    private let calendar = Calendar.real
    private let timer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    init(path: Binding<[Route]>, initialDay: Date = Date()) {
        _path = path
        _selectedDay = State(initialValue: Calendar.real.startOfDay(for: initialDay))
    }

    var body: some View {
        let today = calendar.startOfDay(for: now)
        let dayStart = calendar.startOfDay(for: selectedDay)
        let dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart)!
        let hiddenFromCalendar = Set(store.categories.filter { !$0.isShownInCalendar }.map(\.id))
        let calendarReminders = store.reminders.filter { !($0.customCategoryId.map(hiddenFromCalendar.contains) ?? false) }
        let events = Planner.events(calendarReminders, rangeStart: dayStart.epochMillis, rangeEnd: dayEnd.epochMillis, calendar: calendar)
        let focus = Planner.focusEvents(events, day: selectedDay, calendar: calendar)
        let allDay = events.filter(\.isDateOnly)
        let geometry = PlannerTimelineGeometry.daily(events, day: selectedDay, calendar: calendar)
        let categoryCards = store.categories.compactMap { c -> PlannerCategoryCardData? in
            guard let stats = store.reminders.categoryProgress(builtIn: nil, customId: c.id, calendar: calendar) else { return nil }
            let raw = c.customSolidColorArgb.map { Color(argbInt: $0) } ?? paletteColor(c.accentStart) ?? Color(argb: 0xFF4558C8)
            return PlannerCategoryCardData(category: c, stats: stats, surface: plannerSoftPastel(nearestCalendarPastel(raw)))
        }

        let isCalendar = modeRaw != "hours_24"
        ZStack {
            if isCalendar {
                dailyContent(today: today, focus: focus, categoryCards: categoryCards, allDay: allDay, geometry: geometry, events: events)
                    .transition(.asymmetric(insertion: .opacity.combined(with: .offset(x: 80)), removal: .opacity.combined(with: .offset(x: 80))))
            } else {
                hours24Content(reminders: calendarReminders)
                    .transition(.asymmetric(insertion: .opacity.combined(with: .offset(x: -80)), removal: .opacity.combined(with: .offset(x: -80))))
            }
        }
        .animation(.timingCurve(0.2, 0, 0, 1, duration: 0.28), value: isCalendar)
        .background(Color.black.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .safeAreaInset(edge: .top) { topBar(isCalendar: isCalendar) }
        .overlay {
            if let request = addRequest {
                PlannerEventEditor(request: request) { addRequest = nil }.id(request.id).zIndex(10)
            }
        }
        .onAppear { withAnimation(.easeOut(duration: 0.56)) { introVisible = true } }
        .onReceive(timer) { now = $0 }
        .simultaneousGesture(DragGesture(minimumDistance: 40).onEnded { value in
            guard abs(value.translation.width) > abs(value.translation.height) * 2 else { return }
            // Swipe between 24 hour and Takvim like Android; inside Takvim a swipe changes the day.
            if !isCalendar {
                if value.translation.width < 0 { modeRaw = "daily" }
            } else if value.translation.width > 0 && value.startLocation.x < 40 {
                modeRaw = "hours_24"
            } else {
                withAnimation(.spring(duration: 0.35)) {
                    selectedDay = calendar.date(byAdding: .day, value: value.translation.width < 0 ? 1 : -1, to: selectedDay)!
                }
            }
        })
    }

    @ViewBuilder
    private func dailyContent(today: Date, focus: [PlannerEvent], categoryCards: [PlannerCategoryCardData], allDay: [PlannerEvent],
                              geometry: PlannerTimelineGeometry, events: [PlannerEvent]) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(Planner.firstName(store.ownerName).map { "Hello, \($0)" } ?? "Hello")
                    .font(RealType.editorialSubtitle)
                    .foregroundStyle(Color(argb: 0xFF8C8C95))
                    .padding(.horizontal, 8)
                    .intro(introVisible, delay: 0, y: 8)
                Text(Planner.focusHeading(day: selectedDay, today: today, count: focus.count, calendar: calendar))
                    .font(RealType.editorialTitle)
                    .kerning(-2.2)
                    .lineSpacing(-6)
                    .foregroundStyle(Color(argb: 0xFFF3F1F4))
                    .padding(.leading, 6)
                    .padding(.top, 12)
                    .padding(.bottom, 20)
                    .contentTransition(.opacity)
                    .animation(.easeOut(duration: 0.14), value: selectedDay)
                    .intro(introVisible, delay: 0.07, y: 14)
                if !focus.isEmpty || !categoryCards.isEmpty {
                    carousel(focus: focus, categories: categoryCards).padding(.bottom, 10)
                }
                PlannerDateRuler(selectedDay: $selectedDay, today: today)
                    .intro(introVisible, delay: 0.3, y: 10)
                scheduleHeader(hasAllDay: !allDay.isEmpty, overlaps: geometry.laneCount > 2, today: today)
                    .intro(introVisible, delay: 0.38, y: 10)
                ForEach(allDay) { event in allDayNote(event) }
                if !allDay.isEmpty { Color.clear.frame(height: 14) }
                PlannerDailyTimelineView(day: selectedDay, events: events, geometry: geometry, categories: store.categories,
                                         ownerName: store.ownerName, introVisible: introVisible,
                                         onOpen: { path.append(.detail($0.reminder.id)) },
                                         onAdd: { hour in addRequest = PlannerAddRequest(day: selectedDay, hour: hour) })
                    .intro(introVisible, delay: 0.38, y: 0)
                if events.isEmpty {
                    Text("Bu gün için plan yok. Saatlere ya da + düğmesine dokunarak ekle.")
                        .font(RealFont.inter(14))
                        .foregroundStyle(Color(argb: 0xFF6F6F78))
                        .padding(.horizontal, 8)
                        .padding(.top, 12)
                }
                Color.clear.frame(height: 110)
            }
            .padding(.horizontal, 10)
            .padding(.top, 24)
        }
    }

    /// 24 hour view: PlannerTodayHeader (big dd.MM / month, world clocks) + "Next 24 hours" cards.
    private func hours24Content(reminders: [ReminderItem]) -> some View {
        let nowMs = now.epochMillis
        let end = nowMs + CategoryRules.rolling24HoursMillis
        let timed = Planner.events(reminders, rangeStart: nowMs, rangeEnd: end, calendar: calendar).filter { !$0.isDateOnly }
        let manual = reminders.filter { r in
            r.isQuick24HourActive(now: nowMs) && !(r.alertAt.map { $0 >= nowMs && $0 <= end } ?? false)
        }.map { PlannerEvent(reminder: $0, startMillis: $0.alertAt ?? $0.createdAt, isDateOnly: $0.alertAt == nil) }
        let todayDay = calendar.epochDay(of: now)
        let dateOnly = reminders.filter { !$0.isCompleted && $0.plannedDateEpochDay == todayDay && !$0.isQuick24HourActive(now: nowMs) }
            .map { PlannerEvent(reminder: $0, startMillis: calendar.startOfDayMillis(epochDay: todayDay), isDateOnly: true) }
        let items = (manual + dateOnly + timed).sorted { ($0.startMillis, $0.reminder.id) < ($1.startMillis, $1.reminder.id) }
        let next = items.first { !$0.isDateOnly && $0.startMillis >= nowMs }?.startMillis
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                PlannerTodayHeader(now: now)
                Color.clear.frame(height: 10)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Next 24 hours").font(RealType.editorialItem).kerning(-1).foregroundStyle(Color(argb: 0xFFF3F1F4))
                    Text(next24Summary(count: items.count, next: next)).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF8C8C95))
                }
                .padding(.horizontal, 6).padding(.vertical, 8)
                if items.isEmpty {
                    Text("Önündeki 24 saatte etkinlik yok.")
                        .font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFF8E8E93))
                        .frame(maxWidth: .infinity).padding(.vertical, 32)
                } else {
                    VStack(spacing: 14) {
                        ForEach(items) { event in
                            PlannerNext24Card(event: event, categories: store.categories, ownerName: store.ownerName, now: now)
                                .onTapGesture { path.append(.detail(event.reminder.id)) }
                        }
                    }
                    .padding(.horizontal, 2).padding(.vertical, 8)
                }
                Color.clear.frame(height: 40)
            }
            .padding(.horizontal, 12)
        }
    }

    private func next24Summary(count: Int, next: EpochMillis?) -> String {
        if count == 0 { return "Nothing planned" }
        let plans = "\(count) \(count == 1 ? "plan" : "plans")"
        guard let next else { return plans }
        let minutes = max(0, (next - now.epochMillis) / 60_000)
        let countdown = minutes < 1 ? "now" : (minutes < 60 ? "\(minutes)m" : "\(minutes / 60)h \(String(format: "%02d", minutes % 60))m")
        return "\(plans)  ·  next in \(countdown)"
    }

    /// Glass back · "24 hour | Takvim" segmented · + (PlannerScreen.kt chrome).
    private func topBar(isCalendar: Bool) -> some View {
        HStack(spacing: 8) {
            Button { dismiss() } label: {
                RealSymbol("chevron.left", size: 19.5).foregroundStyle(.white)
                    .frame(width: 44, height: 44).liquidGlass(Circle())
            }
            .accessibilityLabel("Önceki görünüme dön")
            LiquidSegmentedControl(labels: ["24 hour", "Takvim"], selected: Binding(
                get: { isCalendar ? 1 : 0 },
                set: { modeRaw = $0 == 1 ? "daily" : "hours_24" }))
                .frame(height: 44)
            Button {
                let base = Date().addingTimeInterval(3600)
                addRequest = PlannerAddRequest(day: isCalendar ? selectedDay : base, hour: calendar.component(.hour, from: base))
            } label: {
                RealSymbol("plus", size: 20.7).foregroundStyle(.white)
                    .frame(width: 44, height: 44).liquidGlass(Circle())
            }
            .padding(.leading, 2)
            .accessibilityLabel("Yeni etkinlik ekle")
        }
        .padding(.horizontal, 14)
        .padding(.top, 10).padding(.bottom, 6)
        .background(LinearGradient(colors: [.black, .black.opacity(0.85), .black.opacity(0)], startPoint: .top, endPoint: .bottom).ignoresSafeArea())
    }

    private func carousel(focus: [PlannerEvent], categories: [PlannerCategoryCardData]) -> some View {
        GeometryReader { geo in
            let width = geo.size.width * 0.72
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(Array(focus.enumerated()), id: \.element.id) { index, event in
                        PlannerFocusCard(event: event, categories: store.categories, now: now, width: width)
                            .onTapGesture { path.append(.detail(event.reminder.id)) }
                            .intro(introVisible, delay: 0.15 + Double(min(index, 3)) * 0.09, x: 90)
                    }
                    ForEach(Array(categories.enumerated()), id: \.element.category.id) { index, card in
                        Button { path.append(.goal(card.category.id)) } label: {
                            PlannerCategoryCardView(card: card, width: width)
                        }
                        .buttonStyle(PressScaleStyle(scale: 0.965))
                        .zoomSource("goal-\(card.category.id)", in: nav)
                        .intro(introVisible, delay: 0.15 + Double(min(focus.count + index, 3)) * 0.09, x: 90)
                    }
                }
                .padding(.horizontal, 6)
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.viewAligned)
        }
        .frame(height: max(UIScreen.main.bounds.width * 0.72 * 0.96, 232))
    }

    private func scheduleHeader(hasAllDay: Bool, overlaps: Bool, today: Date) -> some View {
        GeometryReader { geo in
            let anchorX = geo.size.width * PlannerDateRuler.anchorFraction
            let isToday = calendar.isDate(selectedDay, inSameDayAs: today)
            let pillWidth: CGFloat = isToday ? 96 : 104
            ZStack(alignment: .topLeading) {
                VStack(alignment: .leading, spacing: 5) {
                    Text("SCHEDULE")
                    if hasAllDay { Text("ALL DAY") }
                    if overlaps { Text("Swipe across overlaps").kerning(0) }
                }
                .font(RealType.editorialMeta)
                .kerning(1)
                .foregroundStyle(Color(argb: 0xFF85858E))
                .padding(.leading, 8)
                .padding(.top, 6)
                todayPill(isToday: isToday, today: today)
                    .frame(width: pillWidth)
                    .offset(x: min(max(anchorX - pillWidth / 2, 0), geo.size.width - pillWidth))
                Rectangle()
                    .fill(LinearGradient(colors: [Color(argb: 0xFF55555D), .clear], startPoint: .top, endPoint: .bottom))
                    .frame(width: 1, height: 44)
                    .offset(x: anchorX - 0.5, y: 34)
            }
        }
        .frame(height: hasAllDay ? 52 : 60)
    }

    private func todayPill(isToday: Bool, today: Date) -> some View {
        HStack(spacing: 0) {
            Button { selectedDay = calendar.date(byAdding: .day, value: -1, to: selectedDay)! } label: {
                RealSymbol("chevron.left", size: 11.5).frame(width: 24, height: 34)
            }
            .accessibilityLabel("Previous day")
            Button { withAnimation { selectedDay = today } } label: {
                Text(isToday ? "Today" : "Back to").font(RealFont.inter(12)).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 34)
            }
            .accessibilityLabel("Go to today")
            Button { selectedDay = calendar.date(byAdding: .day, value: 1, to: selectedDay)! } label: {
                RealSymbol("chevron.right", size: 11.5).frame(width: 24, height: 34)
            }
            .accessibilityLabel("Next day")
        }
        .foregroundStyle(Color(argb: 0xFFB4B4BC))
        .overlay(Capsule().strokeBorder(Color(argb: 0xFF55555D), lineWidth: 1))
    }

    @ViewBuilder
    private func allDayNote(_ event: PlannerEvent) -> some View {
        let surface = plannerSoftPastel(plannerCardColor(event.reminder, categories: store.categories))
        if event.reminder.isCompleted {
            HStack(spacing: 8) {
                PlannerEventLogo(category: store.category(event.reminder.customCategoryId), starred: event.reminder.isImportant, size: 24)
                Text(event.reminder.text).font(RealFont.inter(14)).kerning(-0.2).lineLimit(1)
            }
            .foregroundStyle(RealColors.plannerInk)
            .padding(.leading, 7).padding(.trailing, 16).padding(.vertical, 7)
            .background(Capsule().fill(surface))
            .opacity(0.62)
            .padding(.horizontal, 6).padding(.vertical, 3)
            .onTapGesture { path.append(.detail(event.reminder.id)) }
        } else {
            Text(event.reminder.text)
                .font(RealFont.inter(17))
                .kerning(-0.3)
                .lineLimit(3)
                .foregroundStyle(RealColors.plannerInk)
                .padding(.horizontal, 18).padding(.vertical, 16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(surface))
                .padding(.horizontal, 6).padding(.vertical, 3)
                .onTapGesture { path.append(.detail(event.reminder.id)) }
        }
    }
}

struct PlannerCategoryCardData {
    let category: CustomCategory
    let stats: CategoryProgressStats
    let surface: Color
}

struct PlannerAddRequest: Identifiable {
    let id = UUID()
    let day: Date
    let hour: Int?
}

private extension View {
    /// Entrance choreography (PlannerIntroMotion): fade + slide, staggered by delay.
    func intro(_ visible: Bool, delay: Double, x: CGFloat = 0, y: CGFloat = 0) -> some View {
        opacity(visible ? 1 : 0)
            .offset(x: visible ? 0 : x, y: visible ? 0 : y)
            .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.56).delay(delay), value: visible)
    }
}

/// Black badge with the category icon, or the star (PlannerCardBadge).
struct PlannerCardBadge: View {
    let category: CustomCategory?
    var size: CGFloat = 42
    var body: some View {
        RealSymbol(category?.icon.symbol ?? "star.fill", size: size * 0.46)
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(Circle().fill(Color(argb: 0xFF0B0B0D)))
    }
}

struct PlannerChevron: View {
    var body: some View {
        Image(systemName: "chevron.right")
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(RealColors.plannerInk)
            .frame(width: 42, height: 42)
            .overlay(Circle().strokeBorder(RealColors.plannerInk.opacity(0.45), lineWidth: 1))
    }
}

struct PlannerProgressPill: View {
    let label: String
    let fraction: Double
    var body: some View {
        HStack {
            Text(label).font(RealFont.inter(13)).foregroundStyle(RealColors.plannerInk.opacity(0.6)).lineLimit(1)
            Spacer()
            Capsule().fill(RealColors.plannerInk.opacity(0.14)).frame(width: 60, height: 4)
                .overlay(alignment: .leading) {
                    Capsule().fill(RealColors.plannerInk).frame(width: 60 * min(max(fraction, 0), 1), height: 4)
                }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(RealColors.plannerInk.opacity(0.07)))
    }
}

struct PlannerFocusCard: View {
    let event: PlannerEvent
    let categories: [CustomCategory]
    let now: Date
    let width: CGFloat

    var body: some View {
        let category = event.reminder.customCategoryId.flatMap { id in categories.first { $0.id == id } }
        let surface = plannerSoftPastel(plannerCardColor(event.reminder, categories: categories))
        let start = event.startMillis, end = event.endMillis
        let nowMs = now.epochMillis
        let hasSpan = end.map { $0 > start } ?? false
        let progress: Double = event.isDateOnly ? 0
            : hasSpan ? min(max(Double(nowMs - start) / Double(end! - start), 0), 1)
            : (nowMs >= start ? 1 : 0)
        let duration = event.isDateOnly ? "All day" : hasSpan ? Planner.durationLabel(minutes: (end! - start) / 60_000) : "Reminder"
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                PlannerCardBadge(category: category)
                Spacer()
                if category != nil {
                    RealSymbol("star.fill", size: 16.1).foregroundStyle(RealColors.plannerInk.opacity(0.65))
                        .padding(.trailing, 10)
                }
                PlannerChevron()
            }
            Spacer()
            Text([category?.name ?? "REAL", Planner.timeLabel(event)].joined(separator: "  ·  "))
                .font(RealFont.inter(12))
                .foregroundStyle(RealColors.plannerInk.opacity(0.55))
                .lineLimit(1)
            Text(event.reminder.text)
                .font(RealType.editorialItem)
                .kerning(-1)
                .foregroundStyle(RealColors.plannerInk)
                .lineLimit(3)
                .padding(.top, 8)
                .padding(.bottom, 16)
            PlannerProgressPill(label: duration, fraction: progress)
        }
        .padding(18)
        .frame(width: width, height: max(width * 0.96, 232))
        .background(RoundedRectangle(cornerRadius: 32, style: .continuous).fill(surface))
        .contentShape(RoundedRectangle(cornerRadius: 32))
    }
}

struct PlannerCategoryCardView: View {
    let card: PlannerCategoryCardData
    let width: CGFloat

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                PlannerCardBadge(category: card.category)
                Spacer()
                PlannerChevron()
            }
            Spacer()
            Text(card.category.name)
                .font(RealType.editorialItem)
                .kerning(-1)
                .foregroundStyle(RealColors.plannerInk)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                .padding(.bottom, 16)
            PlannerProgressPill(label: "\(card.stats.done)/\(card.stats.total)", fraction: card.stats.fraction)
        }
        .padding(18)
        .frame(width: width, height: max(width * 0.96, 232))
        .background(RoundedRectangle(cornerRadius: 32, style: .continuous).fill(card.surface))
        .accessibilityLabel("\(card.category.name), \(card.stats.done) of \(card.stats.total) done")
    }
}

/// Faded "Fr 18" ruler: drag it, it snaps, and the day under the anchor becomes the selected day.
struct PlannerDateRuler: View {
    static let anchorFraction: CGFloat = 0.64
    static let span = 1500
    private let itemWidth: CGFloat = 62

    @Binding var selectedDay: Date
    let today: Date
    @State private var centered: Int?

    private let calendar = Calendar.real

    private var origin: Date { calendar.date(byAdding: .day, value: -Self.span, to: calendar.startOfDay(for: today))! }

    private func index(of day: Date) -> Int {
        let d = calendar.dateComponents([.day], from: origin, to: calendar.startOfDay(for: day)).day ?? Self.span
        return min(max(d, 0), Self.span * 2)
    }

    private func date(at index: Int) -> Date { calendar.date(byAdding: .day, value: index, to: origin)! }

    var body: some View {
        GeometryReader { geo in
            let anchorX = geo.size.width * Self.anchorFraction
            let current = centered ?? index(of: selectedDay)
            VStack(alignment: .leading, spacing: 0) {
                Text(monthLabel(date(at: current)))
                    .font(RealFont.inter(12))
                    .foregroundStyle(Color(argb: 0xFF8C8C95))
                    .padding(.leading, 8)
                    .padding(.bottom, 2)
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: 0) {
                        ForEach(0...(Self.span * 2), id: \.self) { i in
                            dayCell(i, distance: abs(i - current))
                                .id(i)
                                .onTapGesture { withAnimation(.spring(duration: 0.35)) { centered = i } }
                        }
                    }
                    .scrollTargetLayout()
                }
                .scrollTargetBehavior(.viewAligned)
                .scrollPosition(id: $centered, anchor: .leading)
                .safeAreaPadding(.leading, max(anchorX - itemWidth / 2, 0))
                .safeAreaPadding(.trailing, max(geo.size.width - anchorX - itemWidth / 2, 0))
                .frame(height: 44)
                Circle().fill(.white).frame(width: 6, height: 6).offset(x: anchorX - 3)
                Rectangle().fill(Color(argb: 0xFF55555D)).frame(width: 1, height: 14).offset(x: anchorX - 0.5)
            }
        }
        .frame(height: 84)
        .onAppear { centered = index(of: selectedDay) }
        .onChange(of: centered) { _, value in
            guard let value else { return }
            let picked = date(at: value)
            if !calendar.isDate(picked, inSameDayAs: selectedDay) { selectedDay = picked }
        }
        .onChange(of: selectedDay) { _, day in
            let target = index(of: day)
            if centered != target { withAnimation(.spring(duration: 0.35)) { centered = target } }
        }
    }

    private func dayCell(_ i: Int, distance: Int) -> some View {
        let day = date(at: i)
        let active = distance == 0
        let ink: Color = active ? .white : (distance == 1 ? Color(argb: 0xFF7C7C85) : Color(argb: 0xFF4F4F57))
        let isToday = calendar.isDate(day, inSameDayAs: today)
        return VStack(spacing: 5) {
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text(calendar.isoWeekday(of: day).english2).font(RealFont.inter(11))
                Text("\(calendar.component(.day, from: day))").font(RealFont.inter(17, active ? .medium : .regular))
            }
            .foregroundStyle(ink)
            .scaleEffect(active ? 1.22 : 1)
            .animation(.easeOut(duration: 0.16), value: active)
            Circle().fill(isToday && !active ? realCalendarPastels[9] : .clear).frame(width: 3, height: 3)
        }
        .frame(width: itemWidth, height: 44)
        .contentShape(Rectangle())
        .accessibilityAddTraits(active ? .isSelected : [])
    }

    private func monthLabel(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US")
        f.dateFormat = "MMM yyyy"
        return f.string(from: date)
    }
}

/// Tinted circle with the card's logo (PlannerEventLogo).
struct PlannerEventLogo: View {
    let category: CustomCategory?
    let starred: Bool
    let size: CGFloat

    var body: some View {
        RealSymbol(category?.icon.symbol ?? (starred ? "star.fill" : "calendar"), size: size * 0.55)
            .foregroundStyle(RealColors.plannerInk)
            .frame(width: size, height: size)
            .background(Circle().fill(RealColors.plannerInk.opacity(0.13)))
    }
}

/// Vertical timeline: occupied hours expand, empty ones collapse; overlaps share lanes (PlannerDailyTimeline.kt).
struct PlannerDailyTimelineView: View {
    let day: Date
    let events: [PlannerEvent]
    let geometry: PlannerTimelineGeometry
    let categories: [CustomCategory]
    let ownerName: String
    let introVisible: Bool
    let onOpen: (PlannerEvent) -> Void
    let onAdd: (Int) -> Void

    private let gutter: CGFloat = 54

    var body: some View {
        let eventMap = Dictionary(uniqueKeysWithValues: events.map { ($0.id, $0) })
        let clusterLanes = Self.clusterLanes(geometry)
        let occupied = Set(geometry.placements.flatMap { p -> [Int] in
            let first = Int(p.interval.startMinute / 60)
            let last = Int((p.interval.endMinute / 60).rounded(.up))
            return Array(first..<max(last, first + 1))
        })
        let leadTrim = occupied.min().map { geometry.boundaries[min(max($0, 0), geometry.boundaries.count - 1)] } ?? 0
        let lanes = max(1, geometry.laneCount)
        GeometryReader { geo in
            let viewport = geo.size.width
            let laneWidth = max((viewport - gutter) / CGFloat(lanes), 128)
            let canvasWidth = max(viewport, gutter + laneWidth * CGFloat(lanes))
            ScrollView(.horizontal, showsIndicators: false) {
                ZStack(alignment: .topLeading) {
                    ForEach(Array(occupied).sorted(), id: \.self) { hour in
                        let y = geometry.y(Double(hour * 60)) - leadTrim
                        Rectangle().fill(Color(argb: 0xFF1C1C20)).frame(width: canvasWidth - gutter, height: 0.5)
                            .offset(x: gutter, y: y)
                        Button { onAdd(hour) } label: {
                            Text(String(format: "%02d:00", hour))
                                .font(RealFont.inter(11))
                                .foregroundStyle(Color(argb: 0xFF777780))
                                .frame(width: 50, height: 44, alignment: .topLeading)
                                .padding(.top, 3).padding(.leading, 4)
                        }
                        .offset(y: y)
                        .accessibilityLabel("Add event at \(hour):00")
                    }
                    ForEach(Array(geometry.placements.enumerated()), id: \.element.interval.key) { index, placement in
                        if let event = eventMap[placement.interval.key] {
                            let groupLanes = clusterLanes[placement.interval.key] ?? 1
                            let slotWidth = max((viewport - gutter) / CGFloat(groupLanes), 128)
                            let y = geometry.y(placement.interval.startMinute)
                            let geometryHeight = geometry.y(placement.interval.endMinute) - y
                            let noDuration = event.endMillis == nil
                            let nextY = geometry.placements
                                .filter { $0.lane == placement.lane && $0.interval.startMinute > placement.interval.startMinute }
                                .map { geometry.y($0.interval.startMinute) }.min()
                            let room = nextY.map { $0 - y - 4 } ?? .greatestFiniteMagnitude
                            let height = noDuration ? max(geometryHeight, min(48, room)) : geometryHeight
                            PlannerTimelineEvent(event: event, height: height, width: slotWidth - 6, groupLanes: groupLanes,
                                                 categories: categories, ownerName: ownerName)
                                .onTapGesture { onOpen(event) }
                                .offset(x: gutter + slotWidth * CGFloat(placement.lane), y: y - leadTrim)
                                .scaleEffect(introVisible ? 1 : 0.8, anchor: .topLeading)
                                .opacity(introVisible ? 1 : 0)
                                .animation(.spring(duration: 0.48, bounce: 0.25).delay(0.46 + Double(min(index, 5)) * 0.07), value: introVisible)
                        }
                    }
                }
                .frame(width: canvasWidth, height: max(geometry.height - leadTrim + 8, 8), alignment: .topLeading)
            }
            .scrollDisabled(canvasWidth <= viewport)
        }
        .frame(height: max(geometry.height - leadTrim + 8, 8))
    }

    /// Each group of mutually overlapping events shares the width; a lone event gets the full width.
    static func clusterLanes(_ geometry: PlannerTimelineGeometry) -> [String: Int] {
        var result: [String: Int] = [:]
        var group: [PlannerTimePlacement] = []
        var groupEnd = -1.0
        func flush() {
            let n = (group.map(\.lane).max() ?? -1) + 1
            group.forEach { result[$0.interval.key] = max(1, n) }
            group = []
        }
        for p in geometry.placements.sorted(by: { $0.interval.startMinute < $1.interval.startMinute }) {
            if !group.isEmpty && p.interval.startMinute >= groupEnd { flush(); groupEnd = -1 }
            group.append(p)
            groupEnd = max(groupEnd, p.interval.endMinute)
        }
        if !group.isEmpty { flush() }
        return result
    }
}

struct PlannerTimelineEvent: View {
    let event: PlannerEvent
    let height: Double
    let width: CGFloat
    let groupLanes: Int
    let categories: [CustomCategory]
    let ownerName: String

    var body: some View {
        let surface = plannerSoftPastel(plannerCardColor(event.reminder, categories: categories))
        let category = event.reminder.customCategoryId.flatMap { id in categories.first { $0.id == id } }
        if event.reminder.isCompleted {
            HStack(spacing: 8) {
                PlannerEventLogo(category: category, starred: event.reminder.isImportant, size: 24)
                Text(event.reminder.text).font(RealFont.inter(14)).kerning(-0.2).lineLimit(1)
            }
            .foregroundStyle(RealColors.plannerInk)
            .padding(.leading, 7).padding(.trailing, 16)
            .frame(height: 38)
            .background(Capsule().fill(surface))
            .frame(maxWidth: width, alignment: .leading)
            .opacity(0.62)
        } else {
            let roomy = height >= 76, medium = !roomy && height >= 44
            let logoSize: CGFloat = roomy ? 30 : (medium ? 24 : 20)
            let titleSize: CGFloat = groupLanes > 1 ? (roomy ? 14 : 12) : (roomy ? 18 : (medium ? 15 : 13))
            let lines = height >= 160 ? 3 : (height >= 124 ? 2 : 1)
            let people = event.reminder.people.filter { !$0.isEmpty && $0.caseInsensitiveCompare(ownerName) != .orderedSame }
            let title = Text(event.reminder.text).font(RealFont.inter(titleSize)).kerning(-0.3).lineLimit(lines)
            let time = Text(Planner.timeLabel(event)).font(RealFont.inter(roomy ? 12 : 11)).foregroundStyle(RealColors.plannerInk.opacity(0.6)).lineLimit(1)
            Group {
                if roomy {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(alignment: .top) {
                            PlannerEventLogo(category: category, starred: event.reminder.isImportant, size: logoSize)
                            Spacer()
                            PeopleAvatars(people: people, size: logoSize, maxPeople: groupLanes > 1 ? 1 : 2, ownerName: ownerName)
                        }
                        Spacer(minLength: 0)
                        title
                        time
                    }
                } else if height >= 30 {
                    HStack(spacing: 8) {
                        PlannerEventLogo(category: category, starred: event.reminder.isImportant, size: logoSize)
                        VStack(alignment: .leading, spacing: 2) {
                            title
                            if height >= 44 { time }
                        }
                        Spacer(minLength: 6)
                        PeopleAvatars(people: people, size: logoSize, maxPeople: groupLanes > 1 ? 1 : 2, ownerName: ownerName)
                    }
                } else {
                    title
                }
            }
            .foregroundStyle(RealColors.plannerInk)
            .padding(.horizontal, 14)
            .padding(.vertical, height >= 76 ? 10 : (height >= 64 ? 12 : (height >= 48 ? 7 : 3)))
            .frame(width: width, height: height, alignment: roomy ? .topLeading : .leading)
            .background(RoundedRectangle(cornerRadius: height < 30 ? 8 : 22, style: .continuous).fill(surface))
            .contentShape(Rectangle())
        }
    }
}

/// Owner avatar first, then the other people as overlapping initials ("+N" past maxPeople).
struct PeopleAvatars: View {
    let people: [String]
    let size: CGFloat
    let maxPeople: Int
    let ownerName: String

    var body: some View {
        if !people.isEmpty {
            let shown = Array(people.prefix(maxPeople))
            let extra = people.count - shown.count
            HStack(spacing: -size * 0.27) {
                ProfileAvatar(name: ownerName, size: size)
                ForEach(Array(shown.enumerated()), id: \.offset) { index, name in
                    Text(Planner.initials(name).isEmpty ? "?" : Planner.initials(name))
                        .font(RealFont.inter(size * 0.36, .medium))
                        .foregroundStyle(index == 0 ? RealColors.plannerInk : .white)
                        .frame(width: size, height: size)
                        .background(Circle().fill(index == 0 ? Color(argb: 0xFFE9E0D3) : Color(argb: 0xFF596361)))
                        .overlay(Circle().strokeBorder(.white.opacity(0.65), lineWidth: 1.5))
                }
                if extra > 0 {
                    Text("+\(extra)").font(RealFont.inter(size * 0.34)).foregroundStyle(.white)
                        .frame(width: size, height: size).background(Circle().fill(RealColors.plannerInk))
                }
            }
        }
    }
}

/// Add an event straight from the calendar (PlannerEventEditor.kt, condensed).
struct PlannerQuickAddSheet: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let request: PlannerAddRequest
    @State private var text = ""
    @State private var start = Date()
    @State private var allDay = false
    @State private var duration: Int? = 60
    @State private var important = false
    @State private var categoryId: Int64?
    @State private var peopleText = ""
    @State private var showCategory = false

    var body: some View {
        NavigationStack {
            Form {
                TextField("Ne planlıyorsun?", text: $text)
                Toggle("Tüm gün", isOn: $allDay.animation())
                DatePicker("Başlangıç", selection: $start, displayedComponents: allDay ? .date : [.date, .hourAndMinute])
                    .environment(\.locale, Locale(identifier: "tr_TR"))
                if !allDay {
                    Picker("Süre", selection: $duration) {
                        Text("Yok").tag(Int?.none)
                        ForEach([15, 30, 45, 60, 90, 120, 180], id: \.self) { Text(Planner.durationLabel(minutes: Int64($0))).tag(Int?.some($0)) }
                    }
                    TextField("Kişiler (virgülle)", text: $peopleText)
                }
                Toggle("Önemli (Focus)", isOn: $important)
                Button { withoutAnimation { showCategory = true } } label: {
                    LabeledContent("Kategori", value: store.category(categoryId)?.name ?? "Kategori yok")
                }
            }
            .navigationTitle("Yeni etkinlik")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        let cal = Calendar.real
                        let people = peopleText.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
                        store.addReminder(text: text, alertAt: allDay ? nil : start.epochMillis,
                                          plannedDay: allDay ? cal.epochDay(of: start) : nil,
                                          categoryId: categoryId, isImportant: important) { item in
                            item.durationMinutes = allDay ? nil : duration
                            item.people = allDay ? [] : people
                        }
                        if !allDay { NotificationScheduler.requestAuthorization() }
                        dismiss()
                    }
                    .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .realOverlay(isPresented: $showCategory) { CategoryPickerSheet(selected: $categoryId) }
            .onAppear {
                let cal = Calendar.real
                if let hour = request.hour {
                    start = cal.date(bySettingHour: hour, minute: 0, second: 0, of: request.day) ?? request.day
                } else {
                    let base = cal.isDateInToday(request.day) ? Date() : cal.date(bySettingHour: 9, minute: 0, second: 0, of: request.day)!
                    let next = cal.date(byAdding: .hour, value: cal.isDateInToday(request.day) ? 1 : 0, to: base)!
                    start = cal.date(bySettingHour: cal.component(.hour, from: next), minute: 0, second: 0, of: next) ?? next
                }
            }
        }
    }
}


/// PlannerTodayHeader: weekday, giant "dd.MM" + month, divider, Türkiye / Almanya clocks.
struct PlannerTodayHeader: View {
    let now: Date

    var body: some View {
        let cal = Calendar.real
        let c = cal.dateComponents([.day, .month], from: now)
        let months = ["OCA", "ŞUB", "MAR", "NİS", "MAY", "HAZ", "TEM", "AĞU", "EYL", "EKİ", "KAS", "ARA"]
        HStack(alignment: .center, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text(cal.isoWeekday(of: now).full).font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFF8C8C95)).padding(.bottom, 8)
                Text(String(format: "%02d.%02d", c.day ?? 1, c.month ?? 1)).font(RealFont.inter(58)).kerning(-2.6)
                    .foregroundStyle(Color(argb: 0xFFF3F1F4)).lineLimit(1)
                Text(months[(c.month ?? 1) - 1]).font(RealFont.inter(58)).kerning(-2.2).foregroundStyle(Color(argb: 0xFFF3F1F4))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Rectangle().fill(Color(argb: 0xFF3A3A42)).frame(width: 1, height: 98)
            VStack(alignment: .leading, spacing: 18) {
                clock("Europe/Istanbul", "Türkiye")
                clock("Europe/Berlin", "Almanya")
            }
            .frame(width: 118, alignment: .leading)
            .padding(.leading, 18)
        }
        .padding(.horizontal, 12).padding(.vertical, 22)
    }

    private func clock(_ zone: String, _ label: String) -> some View {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: zone) ?? .current
        let c = cal.dateComponents([.hour, .minute], from: now)
        return VStack(alignment: .leading, spacing: 0) {
            Text(String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)).font(RealFont.inter(22)).kerning(-0.6).foregroundStyle(Color(argb: 0xFFF3F1F4))
            Text(label).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF85858E))
        }
    }
}

/// PlannerNext24Card: pastel card, title + owner avatar, Start · duration pill · End, progress while running.
struct PlannerNext24Card: View {
    let event: PlannerEvent
    let categories: [CustomCategory]
    let ownerName: String
    let now: Date

    private static func amPm(_ millis: EpochMillis) -> String {
        let c = Calendar.real.dateComponents([.hour, .minute], from: millis.date)
        let hour = c.hour ?? 0
        let h = hour == 0 ? 12 : (hour <= 12 ? hour : hour - 12)
        return String(format: "%d:%02d %@", h, c.minute ?? 0, hour < 12 ? "am" : "pm")
    }

    var body: some View {
        let surface = plannerSoftPastel(plannerCardColor(event.reminder, categories: categories))
        let end = event.endMillis
        let span: EpochMillis = (!event.isDateOnly && end != nil && end! > event.startMillis) ? end! - event.startMillis : 0
        let progress: Double = span > 0 ? min(max(Double(now.epochMillis - event.startMillis) / Double(span), 0), 1) : 0
        let ink = RealColors.plannerInk
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                Text(event.reminder.text)
                    .font(RealType.editorialItem).kerning(-1)
                    .foregroundStyle(event.reminder.isCompleted ? Color(argb: 0xFF6F6F78) : ink)
                    .strikethrough(event.reminder.isCompleted)
                    .lineLimit(2)
                Spacer(minLength: 12)
                ProfileAvatar(name: ownerName, size: 32).overlay(Circle().strokeBorder(Color.white.opacity(0.7), lineWidth: 1.5))
            }
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(event.isDateOnly ? "All Day" : Self.amPm(event.startMillis)).font(RealFont.inter(22)).kerning(-0.6).foregroundStyle(ink).lineLimit(1)
                    Text("Start").font(RealFont.inter(12)).foregroundStyle(ink.opacity(0.55))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text(event.isDateOnly ? "24h" : (event.reminder.durationMinutes.map { "\($0) min" } ?? "Süre ekle"))
                    .font(RealFont.inter(12)).foregroundStyle(ink.opacity(0.78))
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 16).fill(ink.opacity(0.10)))
                VStack(alignment: .trailing, spacing: 2) {
                    Text(event.isDateOnly || end == nil ? "—" : Self.amPm(end!)).font(RealFont.inter(22)).kerning(-0.6).foregroundStyle(ink).lineLimit(1)
                    Text("End").font(RealFont.inter(12)).foregroundStyle(ink.opacity(0.55))
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .padding(.top, 26)
            if span > 0 {
                Capsule().fill(ink.opacity(0.14)).frame(height: 4)
                    .overlay(alignment: .leading) { GeometryReader { g in Capsule().fill(ink).frame(width: g.size.width * progress) } }
                    .padding(.top, 18)
            }
        }
        .padding(22)
        .background(RoundedRectangle(cornerRadius: 32, style: .continuous).fill(surface))
        .contentShape(RoundedRectangle(cornerRadius: 32))
    }
}
