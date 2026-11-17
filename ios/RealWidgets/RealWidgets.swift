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
        AppIntentConfiguration(kind: "RealCards", intent: CardsWidgetIntent.self,
                               provider: ConfiguredProvider<CardsWidgetIntent> { config, entry in
                                   entry.cardsShow = config.show
                                   entry.surfaceGlow = config.surface == .glow
                               }) { entry in
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
    /// The cards Edit Widget lets through, in the summary's order.
    static func shown(_ entry: RealEntry) -> [WidgetData.Card] {
        let cards = entry.visible(entry.data.cards)
        switch entry.cardsShow {
        case .all: return cards
        case .today: return cards.filter { $0.running(at: entry.now) || $0.day == entry.today }
        case .hideOverdue: return cards.filter { !entry.overdue($0) }
        }
    }

    static func glow(_ entry: RealEntry) -> Color? {
        guard entry.surfaceGlow, let first = shown(entry).first, !first.done else { return nil }
        if first.running(at: entry.now) { return WColor.green }
        if entry.overdue(first) { return WColor.late }
        return first.stripeA.map { Color(argb: $0) }
    }

    var body: some View {
        let large = family == .systemLarge
        let cards = Array(Self.shown(entry).prefix(8))
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
                    // A hero without a date line is shorter, so one more line fits instead of a gap.
                    let short: CGFloat = hero.running(at: entry.now) || entry.dateText(hero) != nil ? 0 : 17
                    let used: CGFloat = (large ? 32 + 26 + 68 + (hero.running(at: entry.now) ? 20 : 0) + 8 : 28 + 62) - short
                    let rows = min(cards.count - 1, max(0, Int((geo.size.height - used) / rowHeight)))
                    HeroCard(card: hero, entry: entry, large: large)
                    Spacer(minLength: large ? 8 : 0)
                    ForEach(cards.dropFirst().prefix(rows)) { CardLine(card: $0, entry: entry, height: rowHeight) }
                } else {
                    WidgetEmpty(text: entry.cardsShow == .today ? entry.s("Bugün için kart yok.", "Nothing for today.")
                                                                : entry.s("Açık kart yok.", "No open cards."))
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
            .widgetURL(URL(string: "real://widget/category/" + category.id.replacingOccurrences(of: "custom:", with: "")))
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
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text(c.title).font(.system(size: 19, weight: .semibold)).lineLimit(1)
                Spacer(minLength: 6)
                pill(c)
                Text("↗").font(.system(size: 19))
            }
            Spacer(minLength: 4)
            if cards.isEmpty {
                Text(entry.s("Bu kategoride açık kart yok.", "No open cards in this category.")).font(.system(size: 13)).opacity(0.8)
                    .padding(.bottom, 8)
            } else {
                ForEach(cards.prefix(3)) { CategoryLine(card: $0, entry: entry, ink: foreground(c)) }
            }
        }
        .padding(14)
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

/// WaveTop's upper edge alone.
struct WaveLine: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let amplitude: CGFloat = 4, length = rect.width / 1.4
        path.move(to: CGPoint(x: 0, y: amplitude))
        var x: CGFloat = 0
        while x <= rect.width {
            path.addLine(to: CGPoint(x: x, y: amplitude + sin(x / length * 2 * .pi) * amplitude))
            x += 2
        }
        return path
    }
}

struct AddGlassButton: View {
    var size: CGFloat = 44
    var fill: Color = .white
    var ink: Color = Color(hex: 0xFF0B1E4A)
    var ml: Int = WidgetData.glassMl
    @Environment(\.widgetRenderingMode) private var mode
    var body: some View {
        Button(intent: AddGlassIntent(ml: ml)) {
            // On glass everything is one tint: a translucent disc keeps the plus readable.
            Image(systemName: "plus").font(.system(size: size * 0.42, weight: .semibold))
                .foregroundStyle(mode == .fullColor ? ink : Color.white)
                .frame(width: size, height: size)
                .background(Circle().fill(mode == .fullColor ? fill : Color.white.opacity(0.22))
                    .shadow(color: .black.opacity(mode == .fullColor ? 0.18 : 0), radius: 5, y: 3))
        }
        .buttonStyle(.plain)
    }
}

struct WaterGlassWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "RealWaterGlass", intent: WaterGlassIntent.self,
                               provider: ConfiguredProvider<WaterGlassIntent> { config, entry in entry.unitMl = config.unit.ml }) { entry in
            WaterGlassView(entry: entry)
                .containerBackground(for: .widget) {
                    LinearGradient(colors: [WColor.waterTop, WColor.waterBottom], startPoint: .top, endPoint: .bottom)
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
        let unit = max(1, entry.unitMl)
        let glasses = water.todayMl / unit
        let goal = max(1, Int((Double(water.goalMl) / Double(unit)).rounded(.up)))
        let level = water.goalMl > 0 ? min(1, Double(water.todayMl) / Double(water.goalMl)) : 0
        GeometryReader { geo in
            let height = level <= 0 ? 0 : geo.size.height * level + 8
            ZStack(alignment: .bottomTrailing) {
                WaveTop()
                    .fill(LinearGradient(colors: [WColor.water1, WColor.water2], startPoint: .top, endPoint: .bottom))
                    .opacity(mode == .fullColor ? 1 : 0.55)
                    .widgetAccentable()
                    .frame(height: height)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                // On glass the water is one tint: its surface as a bright line keeps the level readable.
                if mode != .fullColor && height > 0 {
                    WaveLine().stroke(Color.white, lineWidth: 2.4)
                        .widgetAccentable()
                        .frame(height: height)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                }
                words(glasses, goal).foregroundStyle(WColor.text)
                // Where the water covers the words they turn white, so nothing is lost as it rises.
                words(glasses, goal).foregroundStyle(Color.white)
                    .mask(alignment: .bottom) { WaveTop().frame(height: height) }
                AddGlassButton(size: 38, fill: WColor.plusFill, ink: WColor.plusInk, ml: entry.unitMl)
                    .padding(15)
            }
        }
        .widgetURL(URL(string: "real://widget/water"))
    }

    private func words(_ glasses: Int, _ goal: Int) -> some View {
        let over = glasses - goal
        let name = entry.unitMl >= 500 ? entry.s("şişe", glasses == 1 ? "bottle" : "bottles")
                                       : entry.s("bardak", glasses == 1 ? "glass" : "glasses")
        let unit = name + (over >= 0 ? " · ✓" + (over > 0 ? " +\(over)" : "") : "")
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(entry.s("Su", "Water")).font(.system(size: 13, weight: .semibold))
                Spacer()
                Text(entry.s("hedef \(goal)", "goal \(goal)")).font(.system(size: 13)).opacity(0.6)
            }
            Spacer(minLength: 0)
            Text("\(glasses)").font(.system(size: 48, weight: .light)).contentTransition(.numericText())
            Text(unit).font(.system(size: 12)).opacity(0.75).lineLimit(1).padding(.top, 2).padding(.trailing, 44)
        }
        .padding(15)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
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
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let light = scheme == .light
        let ink = light ? Color(hex: 0xFF2563EB) : Color.white
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
                .stroke(light ? Color.black.opacity(0.14) : Color.white.opacity(0.14), style: StrokeStyle(lineWidth: 1, dash: [6, 7]))
                if light, let first = points.first, let last = points.last {
                    Path { p in
                        p.addPath(WeekLine(points: points).path(in: .zero))
                        p.addLine(to: CGPoint(x: last.x, y: bottom))
                        p.addLine(to: CGPoint(x: first.x, y: bottom))
                        p.closeSubpath()
                    }
                    .fill(ink.opacity(0.08))
                }
                ForEach(Array(points.enumerated()), id: \.offset) { _, point in
                    Rectangle()
                        .fill(LinearGradient(colors: [ink.opacity(light ? 0.22 : 0.32), .clear], startPoint: .top, endPoint: .bottom))
                        .frame(width: 1.2, height: max(0, bottom - point.y))
                        .position(x: point.x, y: (point.y + bottom) / 2)
                }
                WeekLine(points: points)
                    .stroke(light ? AnyShapeStyle(ink) : AnyShapeStyle(LinearGradient(colors: [.white.opacity(0.25), .white], startPoint: .leading, endPoint: .trailing)),
                            style: StrokeStyle(lineWidth: 2, lineCap: .round))
                    .shadow(color: light ? ink.opacity(0.35) : WColor.blue, radius: light ? 3 : 5)
                    .widgetAccentable()
                ForEach(Array(points.enumerated()), id: \.offset) { _, point in
                    if light {
                        Circle().fill(Color.white).frame(width: 7, height: 7).position(point)
                        Circle().stroke(ink, lineWidth: 1.6).frame(width: 7, height: 7).position(point)
                    } else {
                        Circle().fill(Color.white.opacity(0.22)).frame(width: 11, height: 11).position(point)
                        Circle().fill(Color(hex: 0xFFE2E5EA)).frame(width: 6, height: 6).position(point)
                    }
                }
            }
        }
    }
}

/// Su hedefi's surface, picked in Edit Widget: plain like the small Su, a halo from today's point, or a glow from the corner.
enum WeekGlow: String, AppEnum {
    case plain, today, corner

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Zemin"
    static var caseDisplayRepresentations: [WeekGlow: DisplayRepresentation] = [
        .plain: "Düz",
        .today: "Bugünden hale",
        .corner: "Köşeden ışıma"
    ]
}

struct WaterWeekStyleIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Su hedefi"
    static var description = IntentDescription("Widget'ın zemini ve + ile eklenen miktar.")
    @Parameter(title: "Zemin", default: .plain) var glow: WeekGlow
    @Parameter(title: "Miktar", default: .glass) var unit: WaterUnit

    init() {}
}

struct WaterWeekProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> RealEntry { RealEntry(date: Date(), data: .sample) }

    func snapshot(for configuration: WaterWeekStyleIntent, in context: Context) async -> RealEntry {
        var entry = RealTimeline.entries(preview: context.isPreview).first ?? RealEntry(date: Date(), data: .sample)
        entry.weekGlow = configuration.glow
        entry.unitMl = configuration.unit.ml
        return entry
    }

    func timeline(for configuration: WaterWeekStyleIntent, in context: Context) async -> Timeline<RealEntry> {
        let entries = RealTimeline.entries().map { entry -> RealEntry in
            var entry = entry
            entry.weekGlow = configuration.glow
            entry.unitMl = configuration.unit.ml
            return entry
        }
        return Timeline(entries: entries, policy: .atEnd)
    }
}

struct WeekSurface: View {
    let glow: WeekGlow
    /// Today's ratio, to put the halo where today's point is.
    let today: Double
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let light = scheme == .light
        ZStack {
            LinearGradient(colors: [WColor.waterTop, WColor.waterBottom], startPoint: .top, endPoint: .bottom)
            if light {
                LinearGradient(stops: [.init(color: .clear, location: 0.55), .init(color: Color(hex: 0xFFE3EBFB), location: 1)],
                               startPoint: .top, endPoint: .bottom)
            }
            switch glow {
            case .plain:
                EmptyView()
            case .today:
                RadialGradient(colors: [WColor.blue.opacity(light ? 0.2 : 0.42), WColor.blue.opacity(0)],
                               center: UnitPoint(x: 0.92, y: 0.37 + 0.41 * (1 - min(today, 1.15) / 1.15)), startRadius: 0, endRadius: 150)
            case .corner:
                RadialGradient(colors: [WColor.blue.opacity(light ? 0.16 : 0.3), WColor.blue.opacity(0)],
                               center: .topLeading, startRadius: 0, endRadius: 250)
            }
        }
    }
}

struct WaterWeekWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "RealWaterWeek", intent: WaterWeekStyleIntent.self, provider: WaterWeekProvider()) { entry in
            WaterWeekView(entry: entry)
                .containerBackground(for: .widget) { WeekSurface(glow: entry.weekGlow, today: WaterWeekView.ratios(entry).last ?? 0) }
        }
        .configurationDisplayName("Su hedefi")
        .description("Son 7 günün su hedefi. + ile bir bardak ya da şişe ekle.")
        .supportedFamilies([.systemMedium])
        .contentMarginsDisabled()
    }
}

struct WaterWeekView: View {
    let entry: RealEntry

    /// Seven ratios, oldest first; the last is today.
    static func ratios(_ entry: RealEntry) -> [Double] {
        let water = WidgetData.water(entry.data.water, today: entry.today)
        let todayRatio = water.goalMl > 0 ? Double(water.todayMl) / Double(water.goalMl) : 0
        let past = Array(repeating: 0.0, count: max(0, 6 - water.past.count)) + water.past.suffix(6)
        return past + [todayRatio]
    }

    var body: some View {
        let ratios = Self.ratios(entry)
        let percent = Int((ratios.map { min($0, 1) }.reduce(0, +) / Double(ratios.count) * 100).rounded())
        VStack(spacing: 0) {
            HStack {
                Text(entry.s("Su hedefi", "Water goal")).font(.system(size: 14)).foregroundStyle(WColor.sub)
                Spacer()
                AddGlassButton(size: 34, fill: WColor.plusFill, ink: WColor.plusInk, ml: entry.unitMl)
            }
            HStack(alignment: .bottom, spacing: 10) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("\(percent)%").font(.system(size: 40)).foregroundStyle(WColor.text).contentTransition(.numericText())
                        .lineLimit(1).minimumScaleFactor(0.7)
                    Text(entry.s("7 gün ortalaması", "7 day average")).font(.system(size: 11.5)).foregroundStyle(WColor.sub)
                }
                .frame(width: 96, alignment: .leading)
                VStack(spacing: 2) {
                    WeekChart(ratios: ratios)
                    HStack(spacing: 0) {
                        ForEach(0..<7, id: \.self) { i in
                            Text(dayLetter(daysBack: 6 - i)).font(.system(size: 10, weight: i == 6 ? .semibold : .regular))
                                .foregroundStyle(i == 6 ? WColor.text : WColor.sub)
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
        AppIntentConfiguration(kind: "RealCalendar", intent: CalendarWidgetIntent.self,
                               provider: ConfiguredProvider<CalendarWidgetIntent> { config, entry in
                                   entry.sundayFirst = config.weekStart == .sunday
                                   entry.surfaceGlow = config.surface == .glow
                               }) { entry in
            CalendarWidgetView(entry: entry)
                .environment(\.locale, entry.locale)
                .containerBackground(for: .widget) { WidgetSurface(glow: CalendarWidgetView.glow(entry)) }
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
        calendar.firstWeekday = entry.sundayFirst ? 1 : 2
        guard let month = calendar.dateInterval(of: .month, for: entry.date),
              let count = calendar.range(of: .day, in: .month, for: entry.date)?.count else { return [] }
        let first = WidgetData.epochDay(month.start)
        let weekday = calendar.component(.weekday, from: month.start)      // Sunday = 1
        let lead = entry.sundayFirst ? weekday - 1 : (weekday + 5) % 7
        var cells: [Int?] = Array(repeating: nil, count: lead)
        cells += (0..<count).map { Optional(first + $0) }
        while cells.count % 7 != 0 { cells.append(nil) }
        return stride(from: 0, to: cells.count, by: 7).map { Array(cells[$0..<$0 + 7]) }
    }

    private var weekdayLetters: [String] {
        let monday = entry.data.english ? ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"] : ["Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz"]
        return entry.sundayFirst ? Array(monday.suffix(1) + monday.prefix(6)) : monday
    }

    /// With Zemin: Işıma, the picked day's first card glows from the corner (green while its event runs).
    static func glow(_ entry: RealEntry) -> Color? {
        guard entry.surfaceGlow, let first = entry.cards(on: entry.pickedDay ?? entry.today).first(where: { !$0.done }) else { return nil }
        if first.running(at: entry.now) { return WColor.green }
        return first.stripeA.map { Color(argb: $0) }
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
