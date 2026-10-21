import SwiftUI

/// CategoryIcon.kt glyphs (24×24, 1.9 round stroke).
struct CategoryIconGlyph: View {
    let icon: CategoryIcon
    var color: Color?
    var size: CGFloat = 24

    var body: some View {
        Canvas { ctx, sz in
            ctx.scaleBy(x: sz.width / 24, y: sz.height / 24)
            var g = GlyphPen(ctx: ctx, color: color)
            g.width = 1.9
            CategoryIconGlyph.draw(icon, &g)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private static func P(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }

    // swiftlint:disable:next function_body_length cyclomatic_complexity
    /// The newer icons as SVG path data, the same strings Android draws (CategoryIconPaths).
    static let svgPaths: [CategoryIcon: String] = [
        .birthday: "M4 20.5h16M5.5 20.5V14a1.5 1.5 0 0 1 1.5-1.5h10a1.5 1.5 0 0 1 1.5 1.5v6.5M5.5 16.3c1.4.9 2.9.9 4.33 0s2.9-.9 4.33 0 2.9.9 4.34 0M9 12.5V10M12 12.5V9.5M15 12.5V10M9 7.4v.2M12 6.9v.2M15 7.4v.2",
        .racing: "M6.5 4h11M7 21h10M11.2 4h1.6l.7 6 1.5 1.6v6.6l-1 1.8H10l-1-1.8v-6.6l1.5-1.6zM5.6 6.4v4.4M18.4 6.4v4.4M5.2 13.8v4.8M18.8 13.8v4.8M5.6 8.6h4.8M13.6 8.6h4.8M5.2 16.2H9M15 16.2h3.8",
        .racingSide: "M2.5 8.5h3.5v2.5M4.2 11v2.6h3.6l1.9-2.6h1.5l1 1.6h2l7.8 2.7v1.2H21M9.1 16.8h6.4M21 18h1.8M9.1 16.3a2.3 2.3 0 1 1-4.6 0 2.3 2.3 0 1 1 4.6 0zM20.3 16.3a2.3 2.3 0 1 1-4.6 0 2.3 2.3 0 1 1 4.6 0z",
        .vehicle: "M5.5 16.5H4v-4l2.2-.6L8.5 8h6.2l3 3.6 2.3.6v4.3h-1.5M9.5 16.5h5M8.6 11.9h9M9.5 16.5a2 2 0 1 1-4 0 2 2 0 1 1 4 0zM18.5 16.5a2 2 0 1 1-4 0 2 2 0 1 1 4 0z",
        .ball: "M20.5 12a8.5 8.5 0 1 1-17 0 8.5 8.5 0 1 1 17 0zM12 8.3l3.5 2.5-1.3 4.1H9.8l-1.3-4.1zM12 3.5v4.8M15.5 10.8l4.5-1.5M14.2 14.9l2.8 3.9M9.8 14.9L7 18.8M8.5 10.8L4 9.3",
        .music: "M9 18V6l10-2v12M9 9.5l10-2M9 18a2.5 2.5 0 1 1-5 0 2.5 2.5 0 1 1 5 0zM19 16a2.5 2.5 0 1 1-5 0 2.5 2.5 0 1 1 5 0z",
        .movie: "M4 10h16v9.5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM4 10l-.6-3.3 15.6-2.8.6 3.2M8.2 6.1l1.9 3.4M12.7 5.3l1.9 3.4",
        .gaming: "M7 7h10a4.5 4.5 0 0 1 4.4 5.4l-.9 4.4a2.3 2.3 0 0 1-4 1L14.6 16H9.4l-1.9 1.8a2.3 2.3 0 0 1-4-1l-.9-4.4A4.5 4.5 0 0 1 7 7zM7.5 10.5v3M6 12h3M15.5 11.2v.1M17.5 13v.1",
        .book: "M5 19.5V4.5A1.5 1.5 0 0 1 6.5 3H19v15H6.5A1.5 1.5 0 0 0 5 19.5zM5 19.5A1.5 1.5 0 0 0 6.5 21H19v-3M9 7h6",
        .medicine: "M8.2 19.6l11.4-11.4a3.6 3.6 0 0 0-5.1-5.1L3.1 14.5a3.6 3.6 0 0 0 5.1 5.1zM9.5 9.5l3.7 3.7",
        .plant: "M12 21v-9M12 14c-4.5 0-7-2.5-7-7 4.5 0 7 2.5 7 7zM12 11.5c0-3.8 2.3-6.5 6.5-6.5 0 4-2.5 6.5-6.5 6.5z",
        .code: "M8.5 7.5L4 12l4.5 4.5M15.5 7.5L20 12l-4.5 4.5M13.5 5l-3 14",
        .camera: "M4 8.5A1.5 1.5 0 0 1 5.5 7h2.7l1.5-2h4.6l1.5 2h2.7A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5zM15.5 13a3.5 3.5 0 1 1-7 0 3.5 3.5 0 1 1 7 0z",
        .event: "M5 6.5h14a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7.5a1 1 0 0 1 1-1zM4 10.5h16M8 4.5v4M16 4.5v4M8 14h.01M12 14h.01M16 14h.01",
        .friends: "M12 8a3 3 0 1 1-6 0 3 3 0 1 1 6 0zM3.5 19.5c0-3.3 2.4-5.5 5.5-5.5s5.5 2.2 5.5 5.5M16 10.5a2.5 2.5 0 1 0 0-5M17 14c2.2.4 3.5 2.4 3.5 5.5",
    ]
    private static var parsed: [CategoryIcon: Path] = [:]
    private static func svg(_ icon: CategoryIcon) -> Path {
        if let p = parsed[icon] { return p }
        let p = SvgPath.parse(svgPaths[icon] ?? "")
        parsed[icon] = p
        return p
    }

    static func draw(_ icon: CategoryIcon, _ g: inout GlyphPen) {
        if svgPaths[icon] != nil { g.path { $0.addPath(svg(icon)) }; return }
        switch icon {
        case .work:
            g.roundRect(3.5, 7.5, 17, 12.5, 2.5); g.roundRect(8.5, 4, 7, 3.5, 1.5)
            g.line(3.5, 12, 20.5, 12); g.line(11, 12, 13, 12)
        case .home:
            g.poly([(3.5, 11), (12, 4), (20.5, 11)], close: false)
            g.poly([(5.5, 10.5), (5.5, 20), (18.5, 20), (18.5, 10.5)], close: false)
            g.poly([(9.5, 20), (9.5, 14.5), (14.5, 14.5), (14.5, 20)], close: false)
        case .shopping:
            g.circle(1.3, 8.5, 19.5, fill: true); g.circle(1.3, 17.5, 19.5, fill: true)
            g.poly([(3.5, 4.5), (6.5, 4.5), (8.5, 15.5), (18.5, 15.5), (20.5, 7.5), (7, 7.5)], close: false)
        case .health:
            g.poly([(9, 3.5), (15, 3.5), (15, 9), (20.5, 9), (20.5, 15), (15, 15), (15, 20.5), (9, 20.5), (9, 15), (3.5, 15), (3.5, 9), (9, 9)], close: true)
        case .sport:
            g.line(6.5, 12, 17.5, 12); g.line(6.5, 8.5, 6.5, 15.5); g.line(4, 9.5, 4, 14.5)
            g.line(17.5, 8.5, 17.5, 15.5); g.line(20, 9.5, 20, 14.5)
        case .money:
            g.line(12, 3.5, 12, 20.5)
            g.path { p in
                p.move(to: P(16.8, 6.5)); p.addCurve(to: P(12, 4.7), control1: P(15.4, 5.2), control2: P(13.8, 4.7))
                p.addCurve(to: P(6.8, 8.7), control1: P(8.6, 4.7), control2: P(6.8, 6.4))
                p.addCurve(to: P(12.2, 12.7), control1: P(6.8, 11.3), control2: P(8.9, 12.1))
                p.addCurve(to: P(17.2, 16.5), control1: P(15.5, 13.3), control2: P(17.2, 14.2))
                p.addCurve(to: P(12, 20.1), control1: P(17.2, 18.9), control2: P(15.3, 20.1))
                p.addCurve(to: P(6.4, 17.9), control1: P(9.8, 20.1), control2: P(7.9, 19.4))
            }
        case .school:
            g.path { p in
                p.move(to: P(12, 6.5)); p.addCurve(to: P(3.5, 6), control1: P(9.5, 5), control2: P(5.5, 5))
                p.addLine(to: P(3.5, 18.5)); p.addCurve(to: P(12, 19), control1: P(5.5, 17.5), control2: P(9.5, 17.5))
                p.addCurve(to: P(20.5, 18.5), control1: P(14.5, 17.5), control2: P(18.5, 17.5))
                p.addLine(to: P(20.5, 6)); p.addCurve(to: P(12, 6.5), control1: P(18.5, 5), control2: P(14.5, 5))
            }
            g.line(12, 6.5, 12, 19)
        case .travel:
            g.roundRect(5.5, 7.5, 13, 13, 2.5); g.roundRect(9.5, 4, 5, 3.5, 1.2)
            g.line(9.5, 7.5, 9.5, 20.5); g.line(14.5, 7.5, 14.5, 20.5)
        case .family:
            g.circle(3.2, 12, 7)
            g.path { p in
                p.move(to: P(5.5, 19.5)); p.addCurve(to: P(12, 13.5), control1: P(5.5, 15), control2: P(8.5, 13.5))
                p.addCurve(to: P(18.5, 19.5), control1: P(15.5, 13.5), control2: P(18.5, 15))
            }
        case .pet:
            g.oval(8.15, 3.6, 3.4, 5); g.oval(12.45, 3.6, 3.4, 5); g.oval(3.25, 8.1, 3.4, 5); g.oval(17.35, 8.1, 3.4, 5)
            g.path { p in
                p.move(to: P(14.7, 13.5)); p.addCurve(to: P(12, 11), control1: P(13.6, 11.5), control2: P(13.25, 11))
                p.addCurve(to: P(9.16, 13.75), control1: P(10.7, 11), control2: P(10.25, 11.75))
                p.addCurve(to: P(5.7, 18), control1: P(8.2, 15.45), control2: P(5.85, 15.6))
                p.addCurve(to: P(7.5, 20), control1: P(5.7, 19.2), control2: P(6.5, 20))
                p.addCurve(to: P(12, 19), control1: P(8.75, 20), control2: P(10.5, 19))
                p.addCurve(to: P(16.5, 20), control1: P(13.5, 19), control2: P(15.25, 20))
                p.addCurve(to: P(18.3, 18), control1: P(17.5, 20), control2: P(18.3, 19.2))
                p.addCurve(to: P(14.7, 13.5), control1: P(18.15, 15.6), control2: P(15.65, 15.45))
            }
        case .food:
            g.path { p in
                p.move(to: P(5, 7.5)); p.addLine(to: P(16, 7.5)); p.addLine(to: P(15, 15.5))
                p.addCurve(to: P(6, 15.5), control1: P(15, 17.5), control2: P(6, 17.5)); p.closeSubpath()
            }
            g.path { p in p.move(to: P(15.5, 9.5)); p.addCurve(to: P(14.5, 14), control1: P(18.5, 9.5), control2: P(18.5, 14)) }
            g.line(4, 20, 17, 20)
        case .idea:
            g.path { p in
                p.move(to: P(8.5, 14.5)); p.addCurve(to: P(12, 5.5), control1: P(6, 12), control2: P(6, 7.5))
                p.addCurve(to: P(15.5, 14.5), control1: P(18, 7.5), control2: P(18, 12))
            }
            g.line(9.5, 17, 14.5, 17); g.line(10.5, 19.5, 13.5, 19.5)
        case .heart:
            g.path { p in
                p.move(to: P(12, 19.5)); p.addCurve(to: P(4, 8), control1: P(6.5, 15), control2: P(4, 11))
                p.addCurve(to: P(9, 4.5), control1: P(4, 5), control2: P(6.5, 4))
                p.addCurve(to: P(12, 7.5), control1: P(10.5, 5.2), control2: P(11.5, 6.5))
                p.addCurve(to: P(15, 4.5), control1: P(12.5, 6.5), control2: P(13.5, 5.2))
                p.addCurve(to: P(20, 8), control1: P(17.5, 4), control2: P(20, 5))
                p.addCurve(to: P(12, 19.5), control1: P(20, 11), control2: P(17.5, 15)); p.closeSubpath()
            }
        case .star:
            g.poly([(12, 2.5), (14.8, 8.5), (21.5, 9.4), (16.6, 14.2), (17.8, 20.9), (12, 17.7), (6.2, 20.9), (7.4, 14.2), (2.5, 9.4), (9.2, 8.5)], close: true)
        case .file:
            g.poly([(6, 3.5), (14, 3.5), (18.5, 8), (18.5, 20.5), (6, 20.5)], close: true)
            g.line(14, 3.5, 14, 8); g.line(14, 8, 18.5, 8); g.line(9, 12, 15.5, 12); g.line(9, 16, 13.5, 16)
        case .location:
            g.path { p in
                p.move(to: P(12, 21)); p.addCurve(to: P(5.5, 9.5), control1: P(10.1, 18.7), control2: P(5.5, 14.1))
                p.addCurve(to: P(12, 3), control1: P(5.5, 5.8), control2: P(8.4, 3))
                p.addCurve(to: P(18.5, 9.5), control1: P(15.6, 3), control2: P(18.5, 5.8))
                p.addCurve(to: P(12, 21), control1: P(18.5, 14.1), control2: P(13.9, 18.7)); p.closeSubpath()
            }
            g.circle(2.2, 12, 9.5)
        case .bookmark:
            g.poly([(7, 3.5), (17, 3.5), (17, 20.5), (12, 17), (7, 20.5)], close: true)
        case .noIcon:
            break
        case .gift:
            g.roundRect(3, 8, 18, 4, 1.2)
            g.path { p in
                p.move(to: P(5, 12)); p.addLine(to: P(5, 19)); p.addCurve(to: P(7, 21), control1: P(5, 20.1), control2: P(5.9, 21))
                p.addLine(to: P(17, 21)); p.addCurve(to: P(19, 19), control1: P(18.1, 21), control2: P(19, 20.1)); p.addLine(to: P(19, 12))
            }
            g.line(12, 8, 12, 21)
            g.path { p in
                p.move(to: P(12, 8)); p.addCurve(to: P(7.5, 3), control1: P(10.6, 5), control2: P(9.1, 3))
                p.addCurve(to: P(5, 5.5), control1: P(5.9, 3), control2: P(5, 4.1))
                p.addCurve(to: P(7.5, 8), control1: P(5, 7), control2: P(6.1, 8)); p.closeSubpath()
            }
            g.path { p in
                p.move(to: P(12, 8)); p.addCurve(to: P(16.5, 3), control1: P(13.4, 5), control2: P(14.9, 3))
                p.addCurve(to: P(19, 5.5), control1: P(18.1, 3), control2: P(19, 4.1))
                p.addCurve(to: P(16.5, 8), control1: P(19, 7), control2: P(17.9, 8)); p.closeSubpath()
            }
        default:
            break // SVG icons are drawn above
        }
    }
}
