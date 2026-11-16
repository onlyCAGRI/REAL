import AppIntents
import SwiftUI
import WidgetKit

@main
struct RealWidgetsBundle: WidgetBundle {
    var body: some Widget {
        CardsWidget()
        CategoryWidget()
        WaterGlassWidget()
        WaterWeekWidget()
        CalendarWidget()
    }
}

// MARK: Kartlarım

struct CardsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "RealCards", provider: RealProvider()) { entry in
            CardsWidgetView(entry: entry)
                .environment(\.locale, entry.locale)
                .containerBackground(for: .widget) { WidgetSurface(glow: CardsWidgetView.glow(entry)) }
        }
        .configurationDisplayName("Kartlarım")
        .description("Sıradaki kartların. Tik ile tamamla.")
        .supportedFamilies([.systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}

struct CardsWidgetView: View {
    let entry: RealEntry
    @Environment(\.widgetFamily) private var family

    /// The surface's glow: green while the first card's event runs, else its category's colour.
    static func glow(_ entry: RealEntry) -> Color? {
        guard let first = entry.visible(entry.data.cards).first, !first.done else { return nil }
        if first.running(at: entry.now) { return WColor.green }
        return first.stripeA.map { Color(argb: $0) }
    }

    var body: some View {
        let large = family == .systemLarge
        let cards = Array(entry.visible(entry.data.cards).prefix(8))
        let open = entry.data.cards.filter { !$0.done }.count
        GeometryReader { geo in
            VStack(alignment: .leading, spacing: 0) {
                if large {
                    HStack {
                        Text(entry.s("BUGÜN", "TODAY") + " · " + entry.date.formatted(.dateTime.day().month(.wide).locale(entry.locale)).uppercased(with: entry.locale))
                        Spacer()
                        Text(entry.s("\(open) açık", "\(open) open"))
                    }
                    .font(.system(size: 12, weight: .semibold)).foregroundStyle(WColor.sub)
                    .padding(.bottom, 10)
                }
                if let hero = cards.first {
                    let rowHeight: CGFloat = large ? 42 : 27
                    // As many lines as the widget's height holds under the hero.
                    let used: CGFloat = large ? 32 + 26 + 68 + (hero.running(at: entry.now) ? 20 : 0) + 8 : 28 + 62
                    let rows = min(cards.count - 1, max(0, Int((geo.size.height - used) / rowHeight)))
                    HeroCard(card: hero, entry: entry, large: large)
                    Spacer(minLength: large ? 8 : 0)
                    ForEach(cards.dropFirst().prefix(rows)) { CardLine(card: $0, entry: entry, height: rowHeight) }
                } else {
                    WidgetEmpty(text: entry.s("Açık kart yok.", "No open cards."))
                }
            }
            .padding(large ? 16 : 14)
        }
        .widgetURL(URL(string: "real://widget/home"))
    }
}

// MARK: Kategori

struct CategoryEntity: AppEntity {
    let id: String
    let title: String

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Kategori"
    static var defaultQuery = CategoryQuery()
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(title)") }
}

struct CategoryQuery: EntityQuery {
    private func all() -> [CategoryEntity] { WidgetData.read().categories.map { CategoryEntity(id: $0.id, title: $0.title) } }
    func entities(for identifiers: [String]) async throws -> [CategoryEntity] { all().filter { identifiers.contains($0.id) } }
    func suggestedEntities() async throws -> [CategoryEntity] { all() }
    func defaultResult() async -> CategoryEntity? { all().first }
}

struct SelectCategoryIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Kategori"
    static var description = IntentDescription("Widget'ın göstereceği kategori.")
    @Parameter(title: "Kategori") var category: CategoryEntity?

    init() {}
}

struct CategoryProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> RealEntry { RealEntry(date: Date(), data: .sample) }

    func snapshot(for configuration: SelectCategoryIntent, in context: Context) async -> RealEntry {
        RealTimeline.entries(category: configuration.category?.id, preview: context.isPreview).first ?? RealEntry(date: Date(), data: .sample)
    }

    func timeline(for configuration: SelectCategoryIntent, in context: Context) async -> Timeline<RealEntry> {
        Timeline(entries: RealTimeline.entries(category: configuration.category?.id), policy: .atEnd)
    }
}

struct CategoryWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "RealCategory", intent: SelectCategoryIntent.self, provider: CategoryProvider()) { entry in
            CategoryWidgetView(entry: entry)
                .environment(\.locale, entry.locale)
        }
        .configurationDisplayName("Kategori")
        .description("Bir kategori ve sıradaki kartları.")
        .supportedFamilies([.systemSmall, .systemMedium])
        .contentMarginsDisabled()
    }
}

struct CategoryWidgetView: View {
    let entry: RealEntry
    @Environment(\.widgetFamily) private var family

    private var category: WidgetData.Category? {
        entry.data.categories.first { $0.id == entry.categoryId } ?? entry.data.categories.first
    }

    var body: some View {
        if let category {
            let cards = entry.cards(in: category.id)
            Group {
                if family == .systemSmall { small(category, cards) } else { medium(category, cards) }
            }
            .foregroundStyle(foreground(category))
            .containerBackground(for: .widget) { tileFill(category) }
            .widgetURL(URL(string: "real://widget/category"))
        } else {
            WidgetEmpty(text: entry.s("REAL'de bir kategori oluştur.", "Create a category in REAL."))
                .padding(14)
                .containerBackground(for: .widget) { WidgetSurface() }
        }
    }

    /// CategoryTileView: the category's gradient over 14151C.
    private func tileFill(_ c: WidgetData.Category) -> some View {
        ZStack {
            Color(hex: 0xFF14151C)
            LinearGradient(colors: [Color(argb: c.start), Color(argb: c.end)], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }

    /// Dark text on a light category, as categoryForeground decides in the app.
    private func foreground(_ c: WidgetData.Category) -> Color {
        return (widgetLuminance(c.start) + widgetLuminance(c.end)) / 2 > 0.42 ? Color(hex: 0xFF101010) : .white
    }

    private func pill(_ c: WidgetData.Category) -> some View {
        Text(entry.s("\(c.open) kart", c.open == 1 ? "1 card" : "\(c.open) cards")).font(.system(size: 12))
            .padding(.horizontal, 11).padding(.vertical, 5)
            .overlay { Capsule().strokeBorder(foreground(c).opacity(0.42), lineWidth: 1) }
    }

    private func small(_ c: WidgetData.Category, _ cards: [WidgetData.Card]) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                Text(c.title).font(.system(size: 22, weight: .medium)).lineLimit(2).minimumScaleFactor(0.7)
                Spacer(minLength: 6)
                Text("↗").font(.system(size: 24))
            }
            pill(c).padding(.top, 12)
            Spacer(minLength: 0)
            if let next = cards.first(where: { !$0.done }) {
                Text(entry.s("Sırada", "Next")).font(.system(size: 12)).opacity(0.86)
                Text(next.title).font(.system(size: 13)).lineLimit(1)
            }
        }
        .padding(.horizontal, 18).padding(.vertical, 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func medium(_ c: WidgetData.Category, _ cards: [WidgetData.Card]) -> some View {
        VStack(spacing: 5) {
            HStack(spacing: 8) {
                Text(c.title).font(.system(size: 20, weight: .medium)).lineLimit(1)
                Spacer(minLength: 6)
                pill(c)
                Text("↗").font(.system(size: 22))
            }
            .padding(.horizontal, 4).padding(.bottom, 3)
            if cards.isEmpty {
                Text(entry.s("Bu kategoride açık kart yok.", "No open cards in this category.")).font(.system(size: 13)).opacity(0.8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ForEach(cards.prefix(2)) { WidgetCardRow(card: $0, entry: entry, onTile: true, height: 44) }
                Spacer(minLength: 0)
            }
        }
        .padding(.horizontal, 14).padding(.top, 14).padding(.bottom, 12)
    }
}

// MARK: Su (küçük): the widget is the glass

/// The water's surface: a soft wave along the top edge.
struct WaveTop: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let amplitude: CGFloat = 4, length = rect.width / 1.4
        path.move(to: CGPoint(x: 0, y: amplitude))
        var x: CGFloat = 0
        while x <= rect.width {
            path.addLine(to: CGPoint(x: x, y: amplitude + sin(x / length * 2 * .pi) * amplitude))
            x += 2
        }
        path.addLine(to: CGPoint(x: rect.width, y: rect.height))
        path.addLine(to: CGPoint(x: 0, y: rect.height))
        path.closeSubpath()
        return path
    }
}

struct AddGlassButton: View {
    var size: CGFloat = 44
    @Environment(\.widgetRenderingMode) private var mode
    var body: some View {
        Button(intent: AddGlassIntent()) {
            // On glass everything is one tint: a translucent disc keeps the plus readable.
            Image(systemName: "plus").font(.system(size: size * 0.42, weight: .semibold))
                .foregroundStyle(mode == .fullColor ? Color(hex: 0xFF0B1E4A) : .white)
                .frame(width: size, height: size)
                .background(Circle().fill(.white.opacity(mode == .fullColor ? 1 : 0.22)))
        }
        .buttonStyle(.plain)
    }
}

struct WaterGlassWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "RealWaterGlass", provider: RealProvider()) { entry in
            WaterGlassView(entry: entry)
                .containerBackground(for: .widget) {
                    LinearGradient(colors: [Color(hex: 0xFF11141C), Color(hex: 0xFF080A10)], startPoint: .top, endPoint: .bottom)
                }
        }
        .configurationDisplayName("Su")
        .description("Bugün içtiğin su. + ile bir bardak ekle.")
        .supportedFamilies([.systemSmall])
        .contentMarginsDisabled()
    }
}

struct WaterGlassView: View {
    let entry: RealEntry
    @Environment(\.widgetRenderingMode) private var mode

    var body: some View {
        let water = WidgetData.water(entry.data.water, today: entry.today)
        let glasses = water.todayMl / WidgetData.glassMl
        let goal = max(1, Int((Double(water.goalMl) / Double(WidgetData.glassMl)).rounded(.up)))
        let level = water.goalMl > 0 ? min(1, Double(water.todayMl) / Double(water.goalMl)) : 0
        ZStack(alignment: .topLeading) {
            GeometryReader { geo in
                WaveTop()
                    .fill(LinearGradient(colors: [Color(hex: 0xFF3D8BFF), Color(hex: 0xFF1450D8), Color(hex: 0xFF0B36A8)], startPoint: .top, endPoint: .bottom))
                    .opacity(mode == .fullColor ? 1 : 0.3)
                    .frame(height: level <= 0 ? 0 : geo.size.height * level + 8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .animation(.spring(response: 0.6, dampingFraction: 0.7), value: level)
            }
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(entry.s("Su", "Water")).font(.system(size: 14))
                    Spacer()
                    Text(entry.s("hedef \(goal)", "goal \(goal)")).font(.system(size: 12)).opacity(0.8)
                }
                Text("\(glasses)").font(.system(size: 56, weight: .light)).contentTransition(.numericText())
                    .padding(.top, 4)
                Text(entry.s("bardak", glasses == 1 ? "glass" : "glasses")).font(.system(size: 12)).opacity(0.85)
                if glasses >= goal {
                    let over = glasses - goal
                    Text("✓ " + entry.s("hedef", "goal") + (over > 0 ? " +\(over)" : "")).font(.system(size: 11, weight: .bold))
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(Capsule().fill(.white.opacity(0.24)))
                        .padding(.top, 6)
                }
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 18).padding(.top, 16)
            AddGlassButton()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .padding(12)
        }
        .widgetURL(URL(string: "real://widget/water"))
    }
}

// MARK: Su hedefi (orta): the week's line

/// DrinksWeekCard's line: smooth through each day's ratio, with horizontal tangents so it never overshoots.
struct WeekLine: Shape {
    let points: [CGPoint]
    func path(in rect: CGRect) -> Path {
        var path = Path()
        guard let first = points.first else { return path }
        path.move(to: first)
        for i in 1..<max(1, points.count) {
            let a = points[i - 1], b = points[i], mid = (a.x + b.x) / 2
            path.addCurve(to: b, control1: CGPoint(x: mid, y: a.y), control2: CGPoint(x: mid, y: b.y))
        }
        return path
    }
}

struct WeekChart: View {
    /// Seven ratios (water over goal), oldest first; the last is today.
    let ratios: [Double]

    var body: some View {
        GeometryReader { geo in
            let padX: CGFloat = 9, top: CGFloat = 9, bottom = geo.size.height - 4
            let count = max(2, ratios.count)
            let points: [CGPoint] = ratios.enumerated().map { i, r in
                CGPoint(x: padX + CGFloat(i) / CGFloat(count - 1) * (geo.size.width - padX * 2),
                        y: top + (1 - CGFloat(min(r, 1.15)) / 1.15) * (bottom - top))
            }
            let goalY = top + (1 - 1 / 1.15) * (bottom - top)
            ZStack {
                Path { p in
                    p.move(to: CGPoint(x: padX, y: goalY))
                    p.addLine(to: CGPoint(x: geo.size.width - padX, y: goalY))
                }
                .stroke(Color.white.opacity(0.14), style: StrokeStyle(lineWidth: 1, dash: [6, 7]))
                ForEach(Array(points.enumerated()), id: \.offset) { _, point in
                    Rectangle()
                        .fill(LinearGradient(colors: [.white.opacity(0.32), .clear], startPoint: .top, endPoint: .bottom))
                        .frame(width: 1.2, height: max(0, bottom - point.y))
                        .position(x: point.x, y: (point.y + bottom) / 2)
                }
                WeekLine(points: points)
                    .stroke(LinearGradient(colors: [.white.opacity(0.25), .white], startPoint: .leading, endPoint: .trailing),
                            style: StrokeStyle(lineWidth: 2, lineCap: .round))
                    .shadow(color: WColor.blue, radius: 5)
                ForEach(Array(points.enumerated()), id: \.offset) { _, point in
                    Circle().fill(Color.white.opacity(0.22)).frame(width: 11, height: 11).position(point)
                    Circle().fill(Color(hex: 0xFFE2E5EA)).frame(width: 6, height: 6).position(point)
                }
            }
        }
    }
}

struct WaterWeekWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "RealWaterWeek", provider: RealProvider()) { entry in
            WaterWeekView(entry: entry)
                .containerBackground(for: .widget) {
                    ZStack {
                        LinearGradient(colors: [Color(hex: 0xFF0D1119), Color(hex: 0xFF05070B)], startPoint: .top, endPoint: .bottom)
                        // WidgetGlowCard: the colour rises from the bottom edge.
                        LinearGradient(stops: [.init(color: .clear, location: 0.55),
                                               .init(color: WColor.blue.opacity(0.28), location: 0.9),
                                               .init(color: WColor.blue.opacity(0.75), location: 1)], startPoint: .top, endPoint: .bottom)
                    }
                }
        }
        .configurationDisplayName("Su hedefi")
        .description("Son 7 günün su hedefi. + ile bir bardak ekle.")
        .supportedFamilies([.systemMedium])
        .contentMarginsDisabled()
    }
}

struct WaterWeekView: View {
    let entry: RealEntry

    var body: some View {
        let water = WidgetData.water(entry.data.water, today: entry.today)
        let todayRatio = water.goalMl > 0 ? Double(water.todayMl) / Double(water.goalMl) : 0
        let past = Array(repeating: 0.0, count: max(0, 6 - water.past.count)) + water.past.suffix(6)
        let ratios = past + [todayRatio]
        let percent = Int((ratios.map { min($0, 1) }.reduce(0, +) / Double(ratios.count) * 100).rounded())
        VStack(spacing: 0) {
            HStack {
                Text(entry.s("Su hedefi", "Water goal")).font(.system(size: 14)).foregroundStyle(Color(hex: 0xFFB4B9C4))
                Spacer()
                AddGlassButton(size: 34)
            }
            HStack(alignment: .bottom, spacing: 10) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("\(percent)%").font(.system(size: 40)).foregroundStyle(.white).contentTransition(.numericText())
                        .lineLimit(1).minimumScaleFactor(0.7)
                    Text(entry.s("7 gün ortalaması", "7 day average")).font(.system(size: 11.5)).foregroundStyle(Color(hex: 0xFFB4B9C4))
                }
                .frame(width: 96, alignment: .leading)
                VStack(spacing: 2) {
                    WeekChart(ratios: ratios)
                    HStack(spacing: 0) {
                        ForEach(0..<7, id: \.self) { i in
                            Text(dayLetter(daysBack: 6 - i)).font(.system(size: 10))
                                .foregroundStyle(i == 6 ? Color.white : Color.white.opacity(0.42))
                                .frame(maxWidth: .infinity)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 18).padding(.top, 14).padding(.bottom, 14)
        .widgetURL(URL(string: "real://widget/water"))
    }

    private func dayLetter(daysBack: Int) -> String {
        let date = WidgetData.date(ofEpochDay: entry.today - daysBack)
        return String(date.formatted(.dateTime.weekday(.narrow).locale(entry.locale)).prefix(1))
    }
}

// MARK: Takvim

struct CalendarWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "RealCalendar", provider: RealProvider()) { entry in
            CalendarWidgetView(entry: entry)
                .environment(\.locale, entry.locale)
                .containerBackground(for: .widget) { WidgetSurface() }
        }
        .configurationDisplayName("Takvim")
        .description("Ay ve günlerin kartları. Bir güne dokun, kartı altta görünsün.")
        .supportedFamilies([.systemLarge])
        .contentMarginsDisabled()
    }
}

struct CalendarWidgetView: View {
    let entry: RealEntry
    @Environment(\.widgetRenderingMode) private var mode

    /// The month's weeks, Monday first; nil pads the first and last week.
    private var weeks: [[Int?]] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        calendar.firstWeekday = 2
        guard let month = calendar.dateInterval(of: .month, for: entry.date),
              let count = calendar.range(of: .day, in: .month, for: entry.date)?.count else { return [] }
        let first = WidgetData.epochDay(month.start)
        let lead = (calendar.component(.weekday, from: month.start) + 5) % 7
        var cells: [Int?] = Array(repeating: nil, count: lead)
        cells += (0..<count).map { Optional(first + $0) }
        while cells.count % 7 != 0 { cells.append(nil) }
        return stride(from: 0, to: cells.count, by: 7).map { Array(cells[$0..<$0 + 7]) }
    }

    private var weekdayLetters: [String] {
        entry.data.english ? ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"] : ["Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz"]
    }

    var body: some View {
        let selected = entry.pickedDay ?? entry.today
        VStack(spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text(entry.date.formatted(.dateTime.month(.wide).locale(entry.locale)).capitalized(with: entry.locale))
                    .font(.system(size: 17, weight: .bold)).foregroundStyle(WColor.text)
                Spacer()
                Text(entry.date.formatted(.dateTime.year().locale(entry.locale))).font(.system(size: 12)).foregroundStyle(WColor.sub)
            }
            .padding(.horizontal, 2).padding(.bottom, 2)
            HStack(spacing: 4) {
                ForEach(weekdayLetters, id: \.self) {
                    Text($0).font(.system(size: 10, weight: .semibold)).foregroundStyle(WColor.sub).frame(maxWidth: .infinity)
                }
            }
            ForEach(Array(weeks.enumerated()), id: \.offset) { _, week in
                HStack(spacing: 4) {
                    ForEach(Array(week.enumerated()), id: \.offset) { _, day in
                        if let day { CalendarDay(day: day, entry: entry, selected: selected) } else { Color.clear.frame(maxWidth: .infinity, maxHeight: .infinity) }
                    }
                }
                .frame(maxHeight: 38)
            }
            Spacer(minLength: 0)
            band(selected).padding(.top, 4)
        }
        .padding(14)
    }

    /// The picked day's first card as a band in its category's colour (green while its event runs).
    @ViewBuilder private func band(_ day: Int) -> some View {
        let cards = entry.cards(on: day)
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        if let first = cards.first {
            let argb: Int? = first.running(at: entry.now) ? Int(UInt32(0xFF5DCAA5)) : first.stripeA
            let coloured = mode == .fullColor && argb != nil
            let ink: Color = coloured ? (widgetLuminance(argb ?? 0) > 0.5 ? Color(hex: 0xFF101010) : .white) : WColor.text
            let time = [first.alertAt.map { entry.clock($0) }, cards.count > 1 ? "+\(cards.count - 1)" : nil].compactMap { $0 }.joined(separator: "  ")
            HStack(spacing: 11) {
                Button(intent: ToggleCardIntent(card: first.id)) { WidgetTick(done: first.done, ring: ink.opacity(0.85)) }
                    .buttonStyle(.plain)
                Link(destination: first.url) {
                    HStack(spacing: 6) {
                        Text(first.title).font(.system(size: 15, weight: .semibold)).strikethrough(first.done, color: ink).lineLimit(1)
                        Spacer(minLength: 4)
                        Text(time).font(.system(size: 13)).opacity(0.9).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
                }
            }
            .foregroundStyle(ink)
            .padding(.horizontal, 13)
            .frame(height: 46)
            .background {
                if coloured, let argb {
                    shape.fill(LinearGradient(colors: [Color(argb: argb), Color(argb: argb).opacity(0.55)], startPoint: .leading, endPoint: .trailing))
                } else {
                    shape.fill(WColor.tile)
                }
            }
            .overlay { if !coloured { shape.strokeBorder(WColor.tileBorder, lineWidth: 0.5) } }
        } else {
            let name = day == entry.today ? entry.s("Bugün", "Today")
                : WidgetData.date(ofEpochDay: day).formatted(.dateTime.day().month(.wide).locale(entry.locale))
            Text(entry.s("\(name) boş · kart ekle ＋", "\(name) is free · add a card ＋")).font(.system(size: 14)).foregroundStyle(WColor.sub)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 13)
                .frame(height: 46)
                .background { shape.fill(WColor.tilePast) }
                .overlay { shape.strokeBorder(WColor.tileBorder, lineWidth: 0.5) }
        }
    }
}

/// One day as the app's month draws it (PlannerMonthView.tile): a box with the number on the left and up to three
/// dots in its cards' category colours stacked on the right; past days faded, today framed, the picked day ringed.
struct CalendarDay: View {
    let day: Int
    let entry: RealEntry
    let selected: Int

    private var dots: [Color] {
        var seen = Set<Int>()
        return entry.data.cards.filter { !$0.done && $0.day == day }
            .compactMap { card -> Color? in
                let key = card.stripeA ?? 0
                return seen.insert(key).inserted ? (card.stripeA.map { Color(argb: $0) } ?? WColor.sub) : nil
            }
            .prefix(3).map { $0 }
    }

    var body: some View {
        let isToday = day == entry.today, isSelected = day == selected, past = day < entry.today
        let number = Calendar.current.component(.day, from: WidgetData.date(ofEpochDay: day))
        let shape = RoundedRectangle(cornerRadius: 10, style: .continuous)
        Button(intent: PickDayIntent(day: day)) {
            HStack(spacing: 0) {
                Text("\(number)").font(.system(size: 13, weight: isToday || isSelected ? .bold : .regular))
                    .foregroundStyle(past ? WColor.muted : WColor.text)
                Spacer(minLength: 0)
                VStack(spacing: 2.5) {
                    ForEach(Array(dots.enumerated()), id: \.offset) { _, color in
                        Circle().fill(color).frame(width: 4.5, height: 4.5)
                    }
                }
            }
            .padding(.leading, 7).padding(.trailing, 5)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background { shape.fill(past ? WColor.tilePast : WColor.tile) }
            .overlay {
                if isToday { shape.strokeBorder(WColor.text, lineWidth: 2) }
                else if isSelected { shape.strokeBorder(WColor.sub, lineWidth: 1.5) }
                else { shape.strokeBorder(WColor.tileBorder, lineWidth: 0.5) }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
