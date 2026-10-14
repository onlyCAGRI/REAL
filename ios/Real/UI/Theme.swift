import SwiftUI
import UIKit

extension Color {
    /// 0xAARRGGBB, the same encoding Compose Color(Long) and Android ARGB ints use.
    init(argb: UInt32) {
        let a = Double((argb >> 24) & 0xFF) / 255
        let r = Double((argb >> 16) & 0xFF) / 255
        let g = Double((argb >> 8) & 0xFF) / 255
        let b = Double(argb & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a == 0 ? 1 : a)
    }

    init(argbInt: Int) { self.init(argb: UInt32(truncatingIfNeeded: argbInt)) }

    var rgba: (r: Double, g: Double, b: Double, a: Double) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(self).getRed(&r, green: &g, blue: &b, alpha: &a)
        return (r, g, b, a)
    }

    func lerp(_ other: Color, _ t: Double) -> Color {
        let x = rgba, y = other.rgba
        return Color(.sRGB, red: x.r + (y.r - x.r) * t, green: x.g + (y.g - x.g) * t,
                     blue: x.b + (y.b - x.b) * t, opacity: x.a + (y.a - x.a) * t)
    }

    var argbInt: Int {
        let c = rgba
        let v = (UInt32(c.a * 255) << 24) | (UInt32(c.r * 255) << 16) | (UInt32(c.g * 255) << 8) | UInt32(c.b * 255)
        return Int(Int32(bitPattern: v))
    }
}

struct ReminderColorChoice: Identifiable {
    let index: Int
    let label: String
    let color: Color
    var id: Int { index }
}

/// REAL 2026 palette. Saved choices are list indices, so this list is append-only (ReminderColorPicker.kt).
private let rawPalette: [(String, UInt32)] = [
    ("Duman Beyazı", 0xFFEFF0F6), ("Jet Siyahı", 0xFF292B2D), ("Royal Mavi", 0xFF4558C8),
    ("Gece Mavisi", 0xFF263C74), ("Gökyüzü", 0xFF78C9F2), ("Petrol", 0xFF2E8793), ("Turkuaz", 0xFF50C4B7),
    ("Nane", 0xFF8ED9B6), ("Zümrüt", 0xFF36A269), ("Old Flax", 0xFFDFF37D), ("Altın", 0xFFE5B84D),
    ("Bronz", 0xFFA77952), ("Somon Turuncu", 0xFFEE5E37), ("Mercan", 0xFFE9786A), ("Gül", 0xFFDE6D98),
    ("Yakut", 0xFFA83B5D), ("Fuşya", 0xFFC85AB4), ("Lavanta", 0xFFD9B8F3), ("Menekşe", 0xFF8666D6),
    ("Erik", 0xFF70436F), ("Koleksiyon Lavanta", 0xFFDAB7F3), ("Koleksiyon Lime", 0xFFE0F37D),
    ("Koleksiyon Mavi", 0xFF4658C8), ("Koleksiyon Turuncu", 0xFFEE5E36),
    // Vivid companions, each shown next to the colour it echoes (24 + (k - 2) pairs with k). Append only: saved
    // choices are indices, shared with Android.
    ("Elektrik Mavisi", 0xFF2F5BEA),
    ("Lacivert", 0xFF1B2A6B),
    ("Periwinkle", 0xFF8FA8E6),
    ("Adaçayı Koyu", 0xFF3E6E62),
    ("Aqua", 0xFF2ED3A2),
    ("Adaçayı", 0xFF8FB5A2),
    ("Yaprak", 0xFF6BB300),
    ("Neon Sarı", 0xFFE8F24A),
    ("Zeytin", 0xFFB6B12E),
    ("Bakır", 0xFFA0603C),
    ("Yanık Turuncu", 0xFFD9622B),
    ("Somon", 0xFFE6806D),
    ("Şeker Pembe", 0xFFE58BC6),
    ("Şarap", 0xFFA3123F),
    ("Magenta", 0xFFE0136C),
    ("Leylak", 0xFFC9A6E8),
    ("Mor", 0xFF8D6BE0),
    ("Mürdüm", 0xFF5A2440),
]

let reminderColorChoices: [ReminderColorChoice] = rawPalette.enumerated().map { pair in
    ReminderColorChoice(index: pair.offset, label: pair.element.0, color: Color(argb: pair.element.1))
}

/// ReminderColorDisplayOrder: the colours as pickers show them, REAL-style — the two signature neutrals, then
/// round the colour wheel (yellow, orange, pink-red, purple, blue, teal, green), each followed by its vivid companion. Cards
/// store the index, so the order changes nothing saved; the four "Koleksiyon" duplicates are not offered.
let reminderColorDisplayOrder = [0, 1, 9, 31, 10, 32, 11, 33, 13, 35, 12, 34, 14, 36, 16, 38, 15, 37, 17, 39, 18, 40, 19, 41, 4, 26, 2, 24, 3, 25, 5, 27, 6, 28, 7, 29, 8, 30]
let pickerColorChoices: [ReminderColorChoice] = reminderColorDisplayOrder.map { reminderColorChoices[$0] }

func paletteColor(_ index: Int?) -> Color? {
    guard let index, reminderColorChoices.indices.contains(index) else { return nil }
    return reminderColorChoices[index].color
}

let gradientBlendFractions: [Double] = [0.20, 0.35, 0.50, 0.65, 0.80]

/// reminderAccentBrush: nil means the plain dark card surface.
func reminderAccentGradient(start: Int?, end: Int?, completed: Bool, blend: Double?, solidArgb: Int?) -> LinearGradient? {
    let alpha = completed ? 0.38 : 0.76
    if let solidArgb {
        let c = Color(argbInt: solidArgb)
        return LinearGradient(colors: [c.opacity(alpha), c.opacity(alpha * 0.55)], startPoint: .topLeading, endPoint: .bottomTrailing)
    }
    let s = paletteColor(start), e = paletteColor(end)
    switch (s, e) {
    case let (s?, e?):
        let f = min(max(blend ?? 0.5, 0), 1)
        let c1 = s.opacity(alpha), c2 = e.opacity(alpha)
        return LinearGradient(stops: [.init(color: c1, location: 0), .init(color: c1.lerp(c2, f), location: 0.5),
                                      .init(color: c2, location: 1)], startPoint: .topLeading, endPoint: .bottomTrailing)
    case let (s?, nil):
        return LinearGradient(colors: [s.opacity(alpha), s.opacity(alpha * 0.55)], startPoint: .topLeading, endPoint: .bottomTrailing)
    case let (nil, e?):
        return LinearGradient(colors: [e.opacity(alpha * 0.55), e.opacity(alpha)], startPoint: .topLeading, endPoint: .bottomTrailing)
    default:
        return nil
    }
}

/// Category tile gradient (CollectionCategoryCard brush).
func categoryGradient(start: Color, end: Color, blend: Double?) -> LinearGradient {
    let f = min(max(blend ?? 0.5, 0), 1)
    return LinearGradient(stops: [.init(color: start, location: 0), .init(color: start.lerp(end, f), location: 0.5),
                                  .init(color: end, location: 1)], startPoint: .topLeading, endPoint: .bottomTrailing)
}

/// Black on light category colours, white on dark ones (WCAG luminance averaged over the two ends), like Android.
/// WCAG relative luminance of a colour (0 black … 1 white).
func relativeLuminance(_ c: Color) -> Double {
    let v = c.rgba
    func ch(_ x: Double) -> Double { x <= 0.03928 ? x / 12.92 : pow((x + 0.055) / 1.055, 2.4) }
    return 0.2126 * ch(v.r) + 0.7152 * ch(v.g) + 0.0722 * ch(v.b)
}

extension ReminderItem {
    /// Card text follows the card's colour, as categories do: dark on light colours, white on dark ones. Cards draw
    /// their colour at 76% over the dark card surface, so that blend is measured. No colour: white text.
    var autoDarkText: Bool {
        let colors: [Color] = customSolidColorArgb.map { [Color(argbInt: $0)] } ?? [paletteColor(accentStart), paletteColor(accentEnd)].compactMap { $0 }
        guard !colors.isEmpty else { return false }
        let surface = Color(argb: 0xFF151517)
        let light = colors.map { relativeLuminance(surface.lerp($0, 0.76)) }.reduce(0, +) / Double(colors.count)
        return light > 0.35
    }
}

func categoryForeground(start: Color, end: Color) -> Color {
    func lum(_ c: Color) -> Double {
        let v = c.rgba
        func ch(_ x: Double) -> Double { x <= 0.03928 ? x / 12.92 : pow((x + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(v.r) + 0.7152 * ch(v.g) + 0.0722 * ch(v.b)
    }
    return (lum(start) + lum(end)) / 2 > 0.42 ? Color(argb: 0xFF101010) : .white
}

struct CategoryVisual {
    var title: String
    var symbol: String
    var gradient: LinearGradient
    var foreground: Color
    var stripe: (Color, Color)
}

extension CustomCategory {
    var visual: CategoryVisual {
        let start = customSolidColorArgb.map { Color(argbInt: $0) } ?? paletteColor(accentStart) ?? Color(argb: 0xFF233F73)
        let end = customSolidColorArgb.map { Color(argbInt: $0) } ?? paletteColor(accentEnd) ?? start
        let stripeEnd = (accentEnd != nil && accentEnd != accentStart) ? end : start.opacity(0.55)
        return CategoryVisual(title: name, symbol: icon.symbol, gradient: categoryGradient(start: start, end: end, blend: gradientBlendFraction),
                              foreground: categoryForeground(start: start, end: end), stripe: (start, stripeEnd))
    }
}

extension ReminderCategory {
    func visual(override: CategoryColorOverride?) -> CategoryVisual {
        let (u, l, dark) = defaultColors
        var start = Color(argb: u), end = Color(argb: l), useDark = dark
        var blend: Double? = nil
        if let o = override {
            let solid = o.customSolidColorArgb.map { Color(argbInt: $0) }
            start = solid ?? paletteColor(o.start) ?? start
            end = solid ?? paletteColor(o.end) ?? start
            useDark = o.useDarkText
            blend = o.gradientBlendFraction
        }
        return CategoryVisual(title: title, symbol: symbol, gradient: categoryGradient(start: start, end: end, blend: blend),
                              foreground: categoryForeground(start: start, end: end), stripe: (start, end))
    }
}

/// Inter, the font Android ships, with the system font as fallback.
enum RealFont {
    static func inter(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        let name: String
        switch weight {
        case .bold, .heavy, .black: name = "Inter-Bold"
        case .semibold: name = "Inter-SemiBold"
        case .medium: name = "Inter-Medium"
        default: name = "Inter-Regular"
        }
        return UIFont(name: name, size: size) != nil ? .custom(name, size: size) : .system(size: size, weight: weight)
    }
}

/// RealType tokens used by the ported screens.
enum RealType {
    static let cardTitle = RealFont.inter(16)
    static let cardMeta = RealFont.inter(11)
    static let cardMetaPrimary = RealFont.inter(12)
    static let categoryTitle = RealFont.inter(30)
    static let categoryTitleHero = RealFont.inter(32)
    static let categoryPill = RealFont.inter(13, .medium)
    static let sectionTitle = RealFont.inter(20, .semibold)
    static let editorialTitle = RealFont.inter(54)
    static let editorialItem = RealFont.inter(30)
    static let editorialSubtitle = RealFont.inter(14)
    static let editorialMeta = RealFont.inter(10)
    static let composer = RealFont.inter(16)
}

enum RealColors {
    static let background = Color.black
    static let cardSurface = Color(argb: 0xFF141416)
    static let cardBorder = Color(argb: 0xFF222225)
    static let text = Color(argb: 0xFFF3F3F5)
    static let textMuted = Color(argb: 0xFF808089)
    static let subtext = Color(argb: 0xFFA5A5B0)
    static let accentBlue = Color(argb: 0xFF94B8F7)
    static let overdue = Color(argb: 0xFFF87171)
    static let selection = Color(argb: 0xFF91B9FF)
    static let plannerInk = Color(argb: 0xFF242322)
    static let sheet = Color(argb: 0xFF101013)
    static let field = Color(argb: 0xFF1A1A1E)
}

/// Turkish date strings used across cards ("12 Eki, 14:30").
enum RealDates {
    static let calendar = Calendar.real
    private static let shortMonths = ["Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"]

    static func time(_ date: Date) -> String {
        let c = calendar.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
    }

    static func dayLabel(_ date: Date) -> String {
        let todayStart = calendar.startOfDay(for: Date())
        let dayStart = calendar.startOfDay(for: date)
        let diff = calendar.dateComponents([.day], from: todayStart, to: dayStart).day ?? 0
        switch diff {
        case 0: return "Bugün"
        case 1: return "Yarın"
        case -1: return "Dün"
        default:
            let c = calendar.dateComponents([.year, .month, .day], from: date)
            let thisYear = calendar.component(.year, from: Date())
            var s = "\(c.day ?? 1) \(shortMonths[(c.month ?? 1) - 1])"
            if c.year != thisYear { s += " \(c.year ?? thisYear)" }
            return s
        }
    }

    /// reminderDate
    static func reminder(_ millis: EpochMillis) -> String { "\(dayLabel(millis.date)), \(time(millis.date))" }

    /// reminderPlannedDate
    static func planned(_ epochDay: EpochDay) -> String { dayLabel(calendar.startOfDay(epochDay: epochDay)) }

    static func longDay(_ date: Date) -> String {
        let c = calendar.dateComponents([.month, .day], from: date)
        return "\(c.day ?? 1) \(turkishMonths[(c.month ?? 1) - 1]) \(calendar.isoWeekday(of: date).full)"
    }
}
