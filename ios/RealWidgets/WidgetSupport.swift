import AppIntents
import SwiftUI
import UIKit
import WidgetKit

// MARK: Colours

extension Color {
    /// 0xAARRGGBB, as the app stores colours.
    init(argb: Int) {
        let v = UInt32(truncatingIfNeeded: argb)
        let a = Double((v >> 24) & 0xFF) / 255
        self.init(.sRGB, red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
                  blue: Double(v & 0xFF) / 255, opacity: a == 0 ? 1 : a)
    }

    init(hex: UInt32) { self.init(argb: Int(hex)) }
}

/// Dark on a dark Home Screen, light on a light one. The fills are translucent so that the system's glass and tinted
/// modes (which keep only a view's alpha) still show the text over them.
private func dyn(_ dark: UInt32, _ light: UInt32) -> Color {
    Color(uiColor: UIColor { $0.userInterfaceStyle == .light ? UIColor(Color(hex: light)) : UIColor(Color(hex: dark)) })
}

enum WColor {
    static let text = dyn(0xFFF3F3F5, 0xFF141416)
    static let sub = dyn(0xFFA5A5B0, 0xFF6B6B73)
    static let muted = dyn(0xFF808089, 0xFF8E8E96)
    static let card = Color(hex: 0xFF17181B)
    static let cardBorder = Color(hex: 0xFF26262A)
    static let circle = dyn(0xFF626269, 0xFFB0B0B8)
    static let hair = dyn(0x17FFFFFF, 0x14000000)
    /// PlannerMonthView's day tile: a coming day, a past day, the hairline around them.
    static let tile = dyn(0x14FFFFFF, 0x0E000000)
    static let tilePast = dyn(0x06FFFFFF, 0x05000000)
    static let tileBorder = dyn(0x12FFFFFF, 0x0D000000)
    static let green = Color(hex: 0xFF5DCAA5)
    static let blue = Color(hex: 0xFF2E8BFF)
    static let top = dyn(0xFF232327, 0xFFFBFAF7)
    static let bottom = dyn(0xFF111113, 0xFFECEAE4)
    static let late = Color(hex: 0xFFFF9F45)
    /// Su (small): its own surface, water and plus button.
    static let waterTop = dyn(0xFF11141C, 0xFFFBFAF7)
    static let waterBottom = dyn(0xFF080A10, 0xFFECEAE4)
    static let water1 = dyn(0xFF3D8BFF, 0xFF6AA6FF)
    static let water2 = dyn(0xFF0B36A8, 0xFF2563EB)
    static let plusFill = dyn(0xFFFFFFFF, 0xFF141416)
    static let plusInk = dyn(0xFF0B1E4A, 0xFFFFFFFF)
}

/// Relative luminance of a 0xAARRGGBB colour (dark text goes on a light colour).
func widgetLuminance(_ argb: Int) -> Double {
    let v = UInt32(truncatingIfNeeded: argb)
    func ch(_ x: Double) -> Double { x <= 0.03928 ? x / 12.92 : pow((x + 0.055) / 1.055, 2.4) }
    return 0.2126 * ch(Double((v >> 16) & 0xFF) / 255) + 0.7152 * ch(Double((v >> 8) & 0xFF) / 255) + 0.0722 * ch(Double(v & 0xFF) / 255)
}

/// The widgets' own surface (the system swaps it for glass when the Home Screen is set to Şeffaf / Renkli), with the
/// first card's colour glowing from the top-left corner: green while its event runs, else its category's colour.
struct WidgetSurface: View {
    var glow: Color? = nil
    var body: some View {
        ZStack {
            LinearGradient(colors: [WColor.top, WColor.bottom], startPoint: .top, endPoint: .bottom)
            if let glow {
                RadialGradient(colors: [glow.opacity(0.32), glow.opacity(0)], center: .topLeading, startRadius: 0, endRadius: 250)
            }
        }
    }
}

// MARK: Entry and timeline

struct RealEntry: TimelineEntry {
    let date: Date
    let data: WidgetData.Snapshot
    /// Takvim: the day picked on the widget, while the pick lasts.
    var pickedDay: Int? = nil
    /// Kategori: the category chosen in the widget's settings.
    var categoryId: String? = nil
    /// Su hedefi: the surface picked in the widget's settings.
    var weekGlow: WeekGlow = .plain
    /// Kartlarım: which cards it shows.
    var cardsShow: CardsShow = .all
    /// Kartlarım and Takvim: the coloured glow in the corner, or a plain surface.
    var surfaceGlow = true
    /// Su: what one tap on + adds, and what the count counts.
    var unitMl = WidgetData.glassMl
    /// Takvim: weeks start on Sunday instead of Monday.
    var sundayFirst = false

    var now: Int64 { Int64(date.timeIntervalSince1970 * 1000) }
    var today: Int { WidgetData.epochDay(date) }
    var locale: Locale { Locale(identifier: data.english ? "en_US" : "tr_TR") }
    func s(_ tr: String, _ en: String) -> String { data.english ? en : tr }

    /// Open cards, and the ones ticked on a widget a moment ago (they linger, struck through).
    func visible(_ cards: [WidgetData.Card]) -> [WidgetData.Card] {
        cards.filter { !$0.done || ($0.doneAt ?? 0) + WidgetData.doneLingerMs > now }
    }

    func clock(_ ms: Int64) -> String {
        Date(timeIntervalSince1970: Double(ms) / 1000).formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits).locale(Locale(identifier: "tr_TR")))
    }

    private func dayName(_ day: Int) -> String {
        switch day - today {
        case 0: return s("Bugün", "Today")
        case 1: return s("Yarın", "Tomorrow")
        case -1: return s("Dün", "Yesterday")
        default: return WidgetData.date(ofEpochDay: day).formatted(.dateTime.day().month(.abbreviated).locale(locale))
        }
    }

    /// "Bugün 14:30", "Yarın 09:00", "16 Kas 14:30"; a planned day without a time is just the day.
    func dateText(_ card: WidgetData.Card) -> String? {
        guard let day = card.day else { return nil }
        return card.alertAt.map { dayName(day) + " " + clock($0) } ?? dayName(day)
    }

    /// Its alert (or, without one, its day) has passed and it is still open; an event that ran is "Bitti" instead.
    func overdue(_ card: WidgetData.Card) -> Bool {
        guard !card.done, card.spanEnd == nil else { return false }
        if let alert = card.alertAt { return alert < now }
        return (card.day ?? today) < today
    }

    /// A row's short time: today's hour, else the day.
    func shortTime(_ card: WidgetData.Card) -> String? {
        guard let day = card.day else { return nil }
        if day == today, let alert = card.alertAt { return clock(alert) }
        return dayName(day)
    }

    /// A category's open cards, then its phone-calendar events.
    func cards(in category: String) -> [WidgetData.Card] {
        visible(data.cards.filter { $0.category == category }) + (data.categoryEvents ?? []).filter { $0.category == category }
    }
    /// A day's cards, open ones first: from the month the app drew, with a tick made on the widget carried over.
    func cards(on day: Int) -> [WidgetData.Card] {
        if let month = data.month, day >= month.first, day <= month.last {
            let live = Dictionary(data.cards.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            return month.cards.filter { $0.day == day }
                .map { c -> WidgetData.Card in
                    var c = c
                    if let l = live[c.id] { c.done = l.done; c.doneAt = l.doneAt }
                    return c
                }
                .sorted { ($0.done ? 1 : 0, $0.alertAt ?? .max) < ($1.done ? 1 : 0, $1.alertAt ?? .max) }
        }
        return visible(data.cards.filter { $0.day == day }).sorted { ($0.alertAt ?? .max) < ($1.alertAt ?? .max) }
    }
}

enum RealTimeline {
    /// When the widgets must redraw in the next day: an event starts or ends (and a few steps while it runs, for
    /// the mist), an alert passes, a ticked card stops lingering, a picked day runs out, midnight.
    static func dates(_ data: WidgetData.Snapshot, from start: Date, extra: [Int64] = []) -> [Date] {
        let now = Int64(start.timeIntervalSince1970 * 1000)
        let horizon = now + 24 * 60 * 60_000
        var moments = Set(extra)
        for card in data.cards {
            if card.done, let at = card.doneAt { moments.insert(at + WidgetData.doneLingerMs) }
            if let alert = card.alertAt { moments.insert(alert) }
            guard !card.done, let s = card.spanStart, let e = card.spanEnd, e > s, e > now, s < horizon else { continue }
            moments.insert(s)
            moments.insert(e)
            let step = max(5 * 60_000, (e - s) / 12)
            var t = max(s, now) + step
            while t < min(e, horizon) { moments.insert(t); t += step }
        }
        let midnight = WidgetData.date(ofEpochDay: WidgetData.epochDay(start) + 1)
        moments.insert(Int64(midnight.timeIntervalSince1970 * 1000) + 1_000)
        let later = moments.filter { $0 > now && $0 <= horizon }.sorted().prefix(48)
        return [start] + later.map { Date(timeIntervalSince1970: Double($0) / 1000) }
    }

    static func entries(category: String? = nil, preview: Bool = false) -> [RealEntry] {
        if preview { return [RealEntry(date: Date(), data: .sample, categoryId: category)] }
        let data = WidgetData.read()
        let start = Date()
        let picked = WidgetData.pickedDay()
        return dates(data, from: start, extra: picked.map { [$0.until] } ?? []).map { date in
            let ms = Int64(date.timeIntervalSince1970 * 1000)
            let day = picked.flatMap { ms < $0.until ? $0.day : nil }
            return RealEntry(date: date, data: data, pickedDay: day, categoryId: category)
        }
    }
}

struct RealProvider: TimelineProvider {
    func placeholder(in context: Context) -> RealEntry { RealEntry(date: Date(), data: .sample) }

    func getSnapshot(in context: Context, completion: @escaping (RealEntry) -> Void) {
        completion(RealTimeline.entries(preview: context.isPreview).first ?? RealEntry(date: Date(), data: .sample))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<RealEntry>) -> Void) {
        completion(Timeline(entries: RealTimeline.entries(), policy: .atEnd))
    }
}

extension WidgetData.Snapshot {
    /// The widget gallery's example (invented cards), in the phone's language.
    static var sample: WidgetData.Snapshot {
        let english = !(Locale.preferredLanguages.first ?? "tr").hasPrefix("tr")
        let now = WidgetData.nowMs()
        let today = WidgetData.epochDay()
        func card(_ id: Int64, _ tr: String, _ en: String, minutes: Int64, length: Int64? = nil, day: Int = 0, a: UInt32, b: UInt32, category: String? = nil) -> WidgetData.Card {
            let at = now + minutes * 60_000
            return WidgetData.Card(id: id, title: english ? en : tr, alertAt: at, day: today + day,
                                   spanStart: length == nil ? nil : at, spanEnd: length.map { at + $0 * 60_000 },
                                   category: category, stripeA: Int(a), stripeB: Int(b))
        }
        let cards = [
            card(1, "Akşam koşusu", "Evening run", minutes: -20, length: 45, a: 0xFFF26A1B, b: 0xFFFFB35C, category: "custom:2"),
            card(2, "Rapor taslağını gönder", "Send the report draft", minutes: 90, a: 0xFF3D7BFF, b: 0xFF9CC0FF),
            card(3, "Fidanları sula", "Water the saplings", minutes: 24 * 60, day: 1, a: 0xFFB9D47A, b: 0xFFDCEBA8),
            card(4, "Filtre değişimi", "Change the filter", minutes: 3 * 24 * 60, day: 3, a: 0xFFC06CFF, b: 0xFFE2B8FF, category: "custom:1"),
            card(5, "Yüzme seansı", "Swimming session", minutes: 4 * 24 * 60, day: 4, a: 0xFFF26A1B, b: 0xFFFFB35C, category: "custom:2"),
            card(6, "Balkon lambası", "Balcony lamp", minutes: 5 * 24 * 60, day: 5, a: 0xFFC06CFF, b: 0xFFE2B8FF, category: "custom:1")
        ]
        return WidgetData.Snapshot(
            cards: cards,
            categories: [
                .init(id: "custom:1", title: english ? "Home" : "Ev", start: Int(UInt32(0xFF5A2A8A)), end: Int(UInt32(0xFFC06CFF)), open: 4),
                .init(id: "custom:2", title: english ? "Sport" : "Spor", start: Int(UInt32(0xFF8A3A12)), end: Int(UInt32(0xFFF26A1B)), open: 3)
            ],
            water: .init(day: today, todayMl: 1_250, goalMl: 2_000, past: [0.72, 1.05, 0.6, 0.9, 1, 0.48]),
            english: english)
    }
}

// MARK: Edit Widget

enum CardsShow: String, AppEnum {
    case all, today, hideOverdue

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Göster"
    static var caseDisplayRepresentations: [CardsShow: DisplayRepresentation] = [
        .all: "Hepsi",
        .today: "Yalnızca bugün",
        .hideOverdue: "Gecikenler hariç"
    ]
}

enum SurfaceStyle: String, AppEnum {
    case glow, plain

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Zemin"
    static var caseDisplayRepresentations: [SurfaceStyle: DisplayRepresentation] = [
        .glow: "Işıma",
        .plain: "Düz"
    ]
}

enum WaterUnit: String, AppEnum {
    case glass, bottle

    var ml: Int { self == .bottle ? 500 : WidgetData.glassMl }

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Miktar"
    static var caseDisplayRepresentations: [WaterUnit: DisplayRepresentation] = [
        .glass: "Bardak (250 ml)",
        .bottle: "Şişe (500 ml)"
    ]
}

enum WeekStart: String, AppEnum {
    case monday, sunday

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Hafta başı"
    static var caseDisplayRepresentations: [WeekStart: DisplayRepresentation] = [
        .monday: "Pazartesi",
        .sunday: "Pazar"
    ]
}

struct CardsWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Kartlarım"
    static var description = IntentDescription("Hangi kartlar ve zemin.")
    @Parameter(title: "Göster", default: .all) var show: CardsShow
    @Parameter(title: "Zemin", default: .glow) var surface: SurfaceStyle

    init() {}
}

struct WaterGlassIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Su"
    static var description = IntentDescription("+ ile eklenen miktar.")
    @Parameter(title: "Miktar", default: .glass) var unit: WaterUnit

    init() {}
}

struct CalendarWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Takvim"
    static var description = IntentDescription("Haftanın ilk günü ve zemin.")
    @Parameter(title: "Hafta başı", default: .monday) var weekStart: WeekStart
    @Parameter(title: "Zemin", default: .plain) var surface: SurfaceStyle

    init() {}
}

/// A widget's timeline with what was picked in Edit Widget written onto each entry.
struct ConfiguredProvider<Intent: WidgetConfigurationIntent>: AppIntentTimelineProvider {
    let apply: (Intent, inout RealEntry) -> Void

    func placeholder(in context: Context) -> RealEntry { RealEntry(date: Date(), data: .sample) }

    func snapshot(for configuration: Intent, in context: Context) async -> RealEntry {
        var entry = RealTimeline.entries(preview: context.isPreview).first ?? RealEntry(date: Date(), data: .sample)
        apply(configuration, &entry)
        return entry
    }

    func timeline(for configuration: Intent, in context: Context) async -> Timeline<RealEntry> {
        let entries = RealTimeline.entries().map { entry -> RealEntry in
            var entry = entry
            apply(configuration, &entry)
            return entry
        }
        return Timeline(entries: entries, policy: .atEnd)
    }
}

// MARK: Buttons

struct ToggleCardIntent: AppIntent {
    static var title: LocalizedStringResource = "Kartı tamamla"
    static var isDiscoverable = false
    @Parameter(title: "Kart") var card: Int

    init() {}
    init(card: Int64) { self.card = Int(card) }

    func perform() async throws -> some IntentResult {
        WidgetData.toggleCard(Int64(card))
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct AddGlassIntent: AppIntent {
    static var title: LocalizedStringResource = "Bir bardak su ekle"
    static var isDiscoverable = false
    @Parameter(title: "Miktar", default: 250) var ml: Int

    init() {}
    init(ml: Int) { self.ml = ml }

    func perform() async throws -> some IntentResult {
        WidgetData.addGlass(ml)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct PickDayIntent: AppIntent {
    static var title: LocalizedStringResource = "Günü göster"
    static var isDiscoverable = false
    @Parameter(title: "Gün") var day: Int

    init() {}
    init(day: Int) { self.day = day }

    func perform() async throws -> some IntentResult {
        WidgetData.pickDay(day)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

/// A widget with nothing to show yet.
struct WidgetEmpty: View {
    let text: String
    var body: some View {
        Text(text).font(.system(size: 13)).foregroundStyle(WColor.sub).multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: Kartlarım's pieces

extension WidgetData.Card {
    func running(at now: Int64) -> Bool {
        guard !done, let s = spanStart, let e = spanEnd else { return false }
        return now >= s && now < e
    }
    func ended(at now: Int64) -> Bool {
        guard !done, let e = spanEnd else { return false }
        return now >= e
    }
    func progress(at now: Int64) -> Double {
        guard let s = spanStart, let e = spanEnd, e > s else { return 0 }
        return min(1, max(0, Double(now - s) / Double(e - s)))
    }
    var url: URL { URL(string: "real://widget/card/\(id)")! }
}

struct WidgetTick: View {
    var done: Bool
    var size: CGFloat = 22
    var ring: Color = WColor.circle
    @Environment(\.widgetRenderingMode) private var mode

    var body: some View {
        ZStack {
            // On glass a filled circle would swallow its own check mark.
            if done && mode == .fullColor { Circle().fill(WColor.green) }
            Circle().strokeBorder(done && mode == .fullColor ? Color.clear : ring, lineWidth: 1.6)
            if done {
                Image(systemName: "checkmark").font(.system(size: size * 0.45, weight: .bold))
                    .foregroundStyle(mode == .fullColor ? Color(hex: 0xFF0B2B20) : ring)
            }
        }
        .frame(width: size, height: size)
        .contentShape(Rectangle())
    }
}

/// The card that matters now, large: what it is, when, and how far its event has run.
struct HeroCard: View {
    let card: WidgetData.Card
    let entry: RealEntry
    var large = true

    private var running: Bool { card.running(at: entry.now) }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Link(destination: card.url) {
                    VStack(alignment: .leading, spacing: 3) {
                        status
                        Text(card.title).font(.system(size: large ? 24 : 20, weight: .bold)).foregroundStyle(WColor.text)
                            .strikethrough(card.done, color: WColor.text).lineLimit(1).minimumScaleFactor(0.8)
                        detail
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                Button(intent: ToggleCardIntent(card: card.id)) { WidgetTick(done: card.done, size: large ? 34 : 30) }
                    .buttonStyle(.plain)
            }
            if large && running {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(WColor.hair)
                        Capsule().fill(WColor.green).frame(width: geo.size.width * card.progress(at: entry.now))
                    }
                }
                .frame(height: 4)
                .padding(.top, 12).padding(.bottom, 4)
            }
        }
        .opacity(card.done ? 0.5 : 1)
    }

    @ViewBuilder private var status: some View {
        if running {
            HStack(spacing: 6) {
                Circle().fill(WColor.green).frame(width: 7, height: 7)
                Text(entry.s("Devam ediyor", "Ongoing"))
            }
            .font(.system(size: 12, weight: .semibold)).foregroundStyle(WColor.green)
        } else if card.ended(at: entry.now) {
            Text(entry.s("Bitti · işaretle", "Ended · tick it")).font(.system(size: 12, weight: .semibold)).foregroundStyle(WColor.muted)
        } else if entry.overdue(card) {
            HStack(spacing: 6) {
                Circle().fill(WColor.late).frame(width: 7, height: 7)
                Text(entry.s("Gecikti", "Overdue"))
            }
            .font(.system(size: 12, weight: .semibold)).foregroundStyle(WColor.late)
        } else {
            HStack(spacing: 6) {
                if let a = card.stripeA { Circle().fill(Color(argb: a)).frame(width: 7, height: 7) }
                Text(entry.s("Sırada", "Next"))
            }
            .font(.system(size: 12, weight: .semibold)).foregroundStyle(WColor.sub)
        }
    }

    @ViewBuilder private var detail: some View {
        if running, let end = card.spanEnd {
            (Text(Date(timeIntervalSince1970: Double(end) / 1000), style: .relative)
                + Text(entry.s(" kaldı · bitiş ", " left · ends ") + entry.clock(end)))
                .font(.system(size: 13)).foregroundStyle(WColor.sub).lineLimit(1)
        } else if let text = entry.dateText(card) {
            let late = entry.overdue(card)
            let ago = late ? (card.day.map { entry.today - $0 } ?? 0) : 0
            Text(ago >= 2 ? text + " · " + entry.s("\(ago) gün önce", "\(ago) days ago") : text)
                .font(.system(size: 13)).foregroundStyle(late ? WColor.late.opacity(0.9) : WColor.sub).lineLimit(1)
        }
    }
}

/// The cards after it: one line each under a hairline, the category's colour as a dot beside the time.
struct CardLine: View {
    let card: WidgetData.Card
    let entry: RealEntry
    var height: CGFloat = 42

    var body: some View {
        HStack(spacing: 11) {
            Button(intent: ToggleCardIntent(card: card.id)) { WidgetTick(done: card.done, size: height > 30 ? 22 : 17) }
                .buttonStyle(.plain)
            Link(destination: card.url) {
                HStack(spacing: 6) {
                    Text(card.title).font(.system(size: height > 30 ? 15 : 14)).foregroundStyle(WColor.text)
                        .strikethrough(card.done, color: WColor.text).lineLimit(1)
                    Spacer(minLength: 4)
                    if let a = card.stripeA { Circle().fill(Color(argb: a)).frame(width: 7, height: 7) }
                    if let time = entry.shortTime(card) {
                        let late = entry.overdue(card)
                        Text(late ? entry.s("Gecikti · ", "Late · ") + time : time)
                            .font(.system(size: 13, weight: late ? .semibold : .regular))
                            .foregroundStyle(late ? WColor.late : WColor.sub).lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
            }
        }
        .frame(height: height)
        .overlay(alignment: .top) { Rectangle().fill(WColor.hair).frame(height: 1) }
        .opacity(card.done ? 0.5 : 1)
    }
}

/// Kategori (medium): a line on the category's colour, in its foreground colour, overdue as a dark capsule.
struct CategoryLine: View {
    let card: WidgetData.Card
    let entry: RealEntry
    let ink: Color

    var body: some View {
        let late = entry.overdue(card)
        HStack(spacing: 10) {
            // A phone-calendar event (negative id) has nothing to tick.
            if card.id > 0 {
                Button(intent: ToggleCardIntent(card: card.id)) { WidgetTick(done: card.done, size: 18, ring: ink.opacity(0.85)) }
                    .buttonStyle(.plain)
            } else {
                Color.clear.frame(width: 18, height: 18)
            }
            Link(destination: card.url) {
                HStack(spacing: 6) {
                    Text(card.title).font(.system(size: 14)).strikethrough(card.done, color: ink).lineLimit(1)
                    Spacer(minLength: 4)
                    if let time = entry.shortTime(card) {
                        Text(late ? entry.s("Gecikti · ", "Late · ") + time : time)
                            .font(.system(size: 12.5, weight: late ? .semibold : .regular)).lineLimit(1)
                            .opacity(late ? 1 : 0.8)
                            .padding(.horizontal, late ? 7 : 0).padding(.vertical, late ? 1 : 0)
                            .background { if late { Capsule().fill(Color.black.opacity(0.18)) } }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
            }
        }
        .foregroundStyle(ink)
        .frame(height: 28)
        .overlay(alignment: .top) { Rectangle().fill(ink.opacity(0.22)).frame(height: 1) }
        .opacity(card.done ? 0.5 : 1)
    }
}
