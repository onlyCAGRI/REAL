import SwiftUI

/// ActionGlyph.kt: REAL's own icon set, drawn on a 24×24 grid with a 1.7 round stroke (Tabler icons use 2).
enum ActionGlyph: CaseIterable {
    case pin, calendar, back, check, undo, edit, share, delete, star, nowBar, mic, music, audioWave, play, pause, video, cards, close,
         note, category, photo, camera, file, person, cat, add, settings, manage, theme, blurOff, motionOff, chevronRight, repeat_,
         bell, bellOff, minus, checklist, dragHandle, sort, liquidGlass, whatsApp, moodVeryGood, moodGood, moodNeutral, moodHard,
         moodBad, moodExcited, moodCalm, moodTired, moodAngry, moodFuckOff, board
}

struct ActionIcon: View {
    let glyph: ActionGlyph
    /// nil = the view's foreground style (so `.foregroundStyle` tints it like an SF Symbol).
    var color: Color?
    var filled = false
    var size: CGFloat = 24

    init(_ glyph: ActionGlyph, color: Color? = nil, filled: Bool = false, size: CGFloat = 24) {
        self.glyph = glyph; self.color = color; self.filled = filled; self.size = size
    }

    var body: some View {
        Canvas { ctx, sz in
            ctx.scaleBy(x: sz.width / 24, y: sz.height / 24)
            var pen = GlyphPen(ctx: ctx, color: color)
            ActionIcon.draw(glyph, filled: filled, &pen)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    // swiftlint:disable:next function_body_length cyclomatic_complexity
    static func draw(_ glyph: ActionGlyph, filled: Bool, _ g: inout GlyphPen) {
        switch glyph {
        case .music:
            g.line(9, 17, 9, 5); g.line(9, 5, 20, 3); g.line(20, 3, 20, 15)
            g.oval(3, 15, 6, 5); g.oval(14, 13, 6, 5)
        case .pin:
            g.path { p in
                p.move(to: .init(x: 12, y: 22)); p.addCurve(to: .init(x: 4.5, y: 9), control1: .init(x: 9, y: 18), control2: .init(x: 4.5, y: 13))
                p.addCurve(to: .init(x: 12, y: 2), control1: .init(x: 4.5, y: 4.9), control2: .init(x: 7.8, y: 2))
                p.addCurve(to: .init(x: 19.5, y: 9), control1: .init(x: 16.2, y: 2), control2: .init(x: 19.5, y: 4.9))
                p.addCurve(to: .init(x: 12, y: 22), control1: .init(x: 19.5, y: 13), control2: .init(x: 15, y: 18))
                p.closeSubpath()
            }
            g.circle(2.5, 12, 9)
        case .calendar:
            g.roundRect(4, 5, 16, 16, 3)
            g.line(8, 3, 8, 7); g.line(16, 3, 16, 7); g.line(4, 10, 20, 10)
            g.circle(1.2, 8, 14, fill: true); g.circle(1.2, 13, 14, fill: true)
        case .back: g.line(15, 4, 7, 12); g.line(7, 12, 15, 20)
        case .check: g.line(4, 12, 9, 17); g.line(9, 17, 20, 6)
        case .close: g.line(6, 6, 18, 18); g.line(18, 6, 6, 18)
        case .undo:
            g.line(3, 5, 3, 11); g.line(3, 11, 9, 11)
            g.path { p in
                p.move(to: .init(x: 3, y: 11)); p.addCurve(to: .init(x: 20, y: 13), control1: .init(x: 7, y: 2), control2: .init(x: 20, y: 4))
                p.addCurve(to: .init(x: 10, y: 19), control1: .init(x: 20, y: 18), control2: .init(x: 15, y: 21))
            }
        case .edit:
            g.poly([(4, 15), (15, 4), (20, 9), (9, 20), (3, 21)], close: true)
            g.line(12.5, 6.5, 17.5, 11.5)
        case .share:
            g.line(6, 12, 18, 5); g.line(6, 12, 18, 19)
            for (x, y) in [(6.0, 12.0), (18.0, 5.0), (18.0, 19.0)] { g.circle(2.7, x, y, fill: true) }
        case .delete:
            g.line(4, 6, 20, 6); g.line(9, 3, 15, 3)
            g.poly([(6, 6), (7, 21), (17, 21), (18, 6)], close: false)
            g.line(10, 10, 10, 17); g.line(14, 10, 14, 17)
        case .star:
            g.poly([(12, 2), (15, 8.4), (22, 9.4), (17, 14.4), (18.2, 21.4), (12, 18.1), (5.8, 21.4), (7, 14.4), (2, 9.4), (9, 8.4)], close: true, fill: filled)
        case .nowBar:
            g.roundRect(3.5, 7.5, 17, 9, 4.5)
            g.roundRect(7, 10, 7, 4, 2, fill: filled)
            g.circle(1.15, 17.5, 12, fill: filled)
        case .mic:
            g.roundRect(9, 2, 6, 12, 3, fill: true)
            g.arc(5.5, 6, 13, 12, start: 0, sweep: 180)
            g.line(12, 18, 12, 22)
        case .audioWave:
            g.line(4, 12, 4, 12); g.line(7, 9, 7, 15); g.line(10, 6, 10, 18)
            g.line(13, 8, 13, 16); g.line(16, 5, 16, 19); g.line(19, 10, 19, 14)
        case .play:
            g.poly([(8, 5), (19, 12), (8, 19)], close: true, fill: true)
        case .pause:
            g.roundRect(7, 5, 3.5, 14, 1, fill: true); g.roundRect(13.5, 5, 3.5, 14, 1, fill: true)
        case .video:
            g.roundRect(3, 5, 18, 14, 3)
            g.poly([(10, 9), (16, 12), (10, 15)], close: true, fill: true)
        case .cards:
            g.roundRect(6.5, 3.5, 13, 13, 2.5); g.roundRect(4.5, 7.5, 13, 13, 2.5)
        case .note:
            g.roundRect(5, 3, 14, 18, 3)
            g.line(9, 8, 15, 8); g.line(9, 12, 15, 12); g.line(9, 16, 12, 16)
        case .category:
            g.width = 2
            g.rect(4, 4, 6, 6); g.rect(14, 4, 6, 6); g.rect(4, 14, 6, 6); g.circle(3, 17, 17)
        case .camera:
            g.poly([(3, 7), (7, 7), (9, 4), (15, 4), (17, 7), (21, 7), (21, 20), (3, 20)], close: true)
            g.circle(4, 12, 13)
        case .photo:
            g.width = 2
            g.roundRect(7, 3, 14, 14, 2.667)
            g.path { p in
                p.move(to: .init(x: 4.012, y: 7.26)); p.addCurve(to: .init(x: 3, y: 8.997), control1: .init(x: 3.39, y: 7.62), control2: .init(x: 3, y: 8.27))
                p.addLine(to: .init(x: 3, y: 18.997)); p.addCurve(to: .init(x: 5, y: 21), control1: .init(x: 3, y: 20.1), control2: .init(x: 3.9, y: 21))
                p.addLine(to: .init(x: 15, y: 21)); p.addCurve(to: .init(x: 16.5, y: 20), control1: .init(x: 15.75, y: 21), control2: .init(x: 16.158, y: 20.615))
            }
            g.circle(0.9, 17, 7, fill: true)
            g.path { p in
                p.move(to: .init(x: 7, y: 13)); p.addLine(to: .init(x: 10.644, y: 9.356))
                p.addCurve(to: .init(x: 12.356, y: 9.356), control1: .init(x: 11.117, y: 8.883), control2: .init(x: 11.883, y: 8.883))
                p.addLine(to: .init(x: 16, y: 13))
            }
            g.path { p in
                p.move(to: .init(x: 15, y: 12)); p.addLine(to: .init(x: 16.644, y: 10.356))
                p.addCurve(to: .init(x: 18.356, y: 10.356), control1: .init(x: 17.117, y: 9.883), control2: .init(x: 17.883, y: 9.883))
                p.addLine(to: .init(x: 21, y: 13))
            }
        case .file:
            g.width = 2
            g.path { p in
                p.move(to: .init(x: 15, y: 3)); p.addLine(to: .init(x: 15, y: 7))
                p.addCurve(to: .init(x: 16, y: 8), control1: .init(x: 15, y: 7.55), control2: .init(x: 15.45, y: 8)); p.addLine(to: .init(x: 20, y: 8))
            }
            g.path { p in
                p.move(to: .init(x: 18, y: 17)); p.addLine(to: .init(x: 11, y: 17))
                p.addCurve(to: .init(x: 9, y: 15), control1: .init(x: 9.9, y: 17), control2: .init(x: 9, y: 16.1)); p.addLine(to: .init(x: 9, y: 5))
                p.addCurve(to: .init(x: 11, y: 3), control1: .init(x: 9, y: 3.9), control2: .init(x: 9.9, y: 3)); p.addLine(to: .init(x: 15, y: 3))
                p.addLine(to: .init(x: 20, y: 8)); p.addLine(to: .init(x: 20, y: 15))
                p.addCurve(to: .init(x: 18, y: 17), control1: .init(x: 20, y: 16.1), control2: .init(x: 19.1, y: 17))
            }
            g.path { p in
                p.move(to: .init(x: 16, y: 17)); p.addLine(to: .init(x: 16, y: 19))
                p.addCurve(to: .init(x: 14, y: 21), control1: .init(x: 16, y: 20.1), control2: .init(x: 15.1, y: 21)); p.addLine(to: .init(x: 7, y: 21))
                p.addCurve(to: .init(x: 5, y: 19), control1: .init(x: 5.9, y: 21), control2: .init(x: 5, y: 20.1)); p.addLine(to: .init(x: 5, y: 9))
                p.addCurve(to: .init(x: 7, y: 7), control1: .init(x: 5, y: 7.9), control2: .init(x: 5.9, y: 7)); p.addLine(to: .init(x: 9, y: 7))
            }
        case .person:
            g.circle(3.2, 12, 7.5, fill: filled)
            g.path { p in
                p.move(to: .init(x: 5.2, y: 19.5)); p.addCurve(to: .init(x: 12, y: 12.8), control1: .init(x: 5.8, y: 14.8), control2: .init(x: 8.6, y: 12.8))
                p.addCurve(to: .init(x: 18.8, y: 19.5), control1: .init(x: 15.4, y: 12.8), control2: .init(x: 18.2, y: 14.8))
            }
        case .cat:
            g.path { p in
                p.move(to: .init(x: 6, y: 8)); p.addLine(to: .init(x: 5, y: 3.5)); p.addLine(to: .init(x: 9, y: 6))
                p.addCurve(to: .init(x: 12, y: 5.2), control1: .init(x: 10, y: 5.5), control2: .init(x: 11, y: 5.2))
                p.addCurve(to: .init(x: 15, y: 6), control1: .init(x: 13, y: 5.2), control2: .init(x: 14, y: 5.5))
                p.addLine(to: .init(x: 19, y: 3.5)); p.addLine(to: .init(x: 18, y: 8))
                p.addCurve(to: .init(x: 19.5, y: 12.5), control1: .init(x: 19, y: 9.2), control2: .init(x: 19.5, y: 10.8))
                p.addCurve(to: .init(x: 12, y: 20), control1: .init(x: 19.5, y: 16.8), control2: .init(x: 16.2, y: 20))
                p.addCurve(to: .init(x: 4.5, y: 12.5), control1: .init(x: 7.8, y: 20), control2: .init(x: 4.5, y: 16.8))
                p.addCurve(to: .init(x: 6, y: 8), control1: .init(x: 4.5, y: 10.8), control2: .init(x: 5, y: 9.2))
                p.closeSubpath()
            }
            g.circle(0.9, 9, 12, fill: true); g.circle(0.9, 15, 12, fill: true)
            g.path { p in p.move(to: .init(x: 10.3, y: 15)); p.addQuadCurve(to: .init(x: 13.7, y: 15), control: .init(x: 12, y: 16.2)) }
        case .add: g.line(12, 5, 12, 19); g.line(5, 12, 19, 12)
        case .settings:
            for i in 0..<6 { g.rotated(Double(i) * 60, cx: 12, cy: 12) { $0.roundRect(11, 1.9, 2, 4.2, 1, fill: true) } }
            g.circle(6.1, 12, 12); g.circle(2.3, 12, 12)
        case .manage:
            g.circle(2.6, 9, 8); g.circle(2.2, 15.8, 9.5)
            g.path { p in
                p.move(to: .init(x: 4.8, y: 18.8)); p.addCurve(to: .init(x: 10.2, y: 13.3), control1: .init(x: 5.4, y: 14.9), control2: .init(x: 7.7, y: 13.3))
                p.addCurve(to: .init(x: 15, y: 16.6), control1: .init(x: 12.2, y: 13.3), control2: .init(x: 13.9, y: 14.3))
            }
            g.path { p in
                p.move(to: .init(x: 12.8, y: 18.6)); p.addCurve(to: .init(x: 17, y: 14.8), control1: .init(x: 13.3, y: 16.2), control2: .init(x: 14.8, y: 14.8))
                p.addCurve(to: .init(x: 20.6, y: 18.6), control1: .init(x: 18.8, y: 14.8), control2: .init(x: 20.1, y: 15.8))
            }
        case .theme:
            g.circle(4.2, 12, 12)
            g.width = 2
            for deg in stride(from: 0.0, to: 360.0, by: 45.0) {
                let r = deg * .pi / 180
                g.line(12 + 6.8 * cos(r), 12 + 6.8 * sin(r), 12 + 9.5 * cos(r), 12 + 9.5 * sin(r))
            }
        case .blurOff:
            for (x, y) in [(6.0, 7.0), (10, 5), (14, 7), (8, 11), (12, 10), (16, 11), (6, 15), (10, 16), (14, 15), (18, 16)] { g.circle(1.05, x, y, fill: true) }
            g.width = 2; g.line(4, 4, 20, 20)
        case .motionOff:
            g.path { p in
                p.move(to: .init(x: 4, y: 12)); p.addCurve(to: .init(x: 20, y: 12), control1: .init(x: 7, y: 6.8), control2: .init(x: 17, y: 6.8))
                p.addCurve(to: .init(x: 4, y: 12), control1: .init(x: 17, y: 17.2), control2: .init(x: 7, y: 17.2)); p.closeSubpath()
            }
            g.circle(2.6, 12, 12)
            g.width = 2; g.line(4, 4, 20, 20)
        case .liquidGlass:
            g.path(fill: true) { p in
                p.move(to: .init(x: 9, y: 5)); p.addCurve(to: .init(x: 15, y: 13), control1: .init(x: 9, y: 9), control2: .init(x: 10, y: 13))
                p.addCurve(to: .init(x: 9, y: 21), control1: .init(x: 10, y: 13), control2: .init(x: 9, y: 17))
                p.addCurve(to: .init(x: 3, y: 13), control1: .init(x: 9, y: 17), control2: .init(x: 8, y: 13))
                p.addCurve(to: .init(x: 9, y: 5), control1: .init(x: 8, y: 13), control2: .init(x: 9, y: 9)); p.closeSubpath()
            }
            g.path(fill: true) { p in
                p.move(to: .init(x: 17.5, y: 4)); p.addCurve(to: .init(x: 21, y: 7.8), control1: .init(x: 17.5, y: 6.2), control2: .init(x: 18.2, y: 7.8))
                p.addCurve(to: .init(x: 17.5, y: 11.6), control1: .init(x: 18.2, y: 7.8), control2: .init(x: 17.5, y: 9.4))
                p.addCurve(to: .init(x: 14, y: 7.8), control1: .init(x: 17.5, y: 9.4), control2: .init(x: 16.8, y: 7.8))
                p.addCurve(to: .init(x: 17.5, y: 4), control1: .init(x: 16.8, y: 7.8), control2: .init(x: 17.5, y: 6.2)); p.closeSubpath()
            }
        case .chevronRight: g.line(9, 6, 15, 12); g.line(15, 12, 9, 18)
        case .repeat_:
            g.line(6, 7, 18, 7); g.line(15, 4, 18, 7); g.line(15, 10, 18, 7)
            g.arc(14, 7, 4, 4, start: 270, sweep: 90)
            g.line(18, 17, 6, 17); g.line(9, 14, 6, 17); g.line(9, 20, 6, 17)
            g.arc(6, 13, 4, 4, start: 90, sweep: 90)
        case .bell, .bellOff:
            g.path { p in
                p.move(to: .init(x: 12, y: 3.5)); p.addLine(to: .init(x: 12, y: 5)); p.move(to: .init(x: 12, y: 5))
                p.addCurve(to: .init(x: 6.5, y: 12), control1: .init(x: 9, y: 5.5), control2: .init(x: 6.5, y: 8))
                p.addLine(to: .init(x: 5, y: 16.5)); p.addLine(to: .init(x: 19, y: 16.5)); p.addLine(to: .init(x: 17.5, y: 12))
                p.addCurve(to: .init(x: 12, y: 5), control1: .init(x: 17.5, y: 8), control2: .init(x: 15, y: 5.5))
            }
            g.arc(10, 17.5, 4, 3.5, start: 0, sweep: 180)
            if glyph == .bellOff { g.line(4.5, 4.5, 19.5, 19.5) }
        case .minus: g.line(5, 12, 19, 12)
        case .checklist:
            for y in [5.5, 11.5, 17.5] { g.roundRect(4, y, 4.5, 4.5, 1.2); g.line(11.5, y + 2.25, 20, y + 2.25) }
        case .dragHandle: g.line(5, 7.5, 19, 7.5); g.line(5, 12, 19, 12); g.line(5, 16.5, 19, 16.5)
        case .board:
            g.roundRect(3, 3, 18, 18, 3); g.roundRect(6, 6, 5, 7, 1); g.roundRect(14, 6, 4, 4, 1)
            g.line(14, 14, 18, 14); g.line(6, 17, 13, 17)
        case .sort:
            g.line(3, 9, 7, 5); g.line(7, 5, 11, 9); g.line(7, 5, 7, 19)
            g.line(13, 15, 17, 19); g.line(17, 19, 21, 15); g.line(17, 5, 17, 19)
        case .whatsApp:
            g.path { p in
                p.move(to: .init(x: 12, y: 3.5)); p.addCurve(to: .init(x: 20.5, y: 12), control1: .init(x: 16.7, y: 3.5), control2: .init(x: 20.5, y: 7.3))
                p.addCurve(to: .init(x: 12, y: 20.5), control1: .init(x: 20.5, y: 16.7), control2: .init(x: 16.7, y: 20.5))
                p.addCurve(to: .init(x: 7.6, y: 19.2), control1: .init(x: 10.4, y: 20.5), control2: .init(x: 8.9, y: 20))
                p.addLine(to: .init(x: 3.5, y: 20.5)); p.addLine(to: .init(x: 4.8, y: 16.5))
                p.addCurve(to: .init(x: 3.5, y: 12), control1: .init(x: 3.9, y: 15.2), control2: .init(x: 3.5, y: 13.6))
                p.addCurve(to: .init(x: 12, y: 3.5), control1: .init(x: 3.5, y: 7.3), control2: .init(x: 7.3, y: 3.5)); p.closeSubpath()
            }
        case .moodVeryGood: g.face(); g.arc(6.5, 5.5, 11, 11, start: 15, sweep: 150); g.eyes()
        case .moodGood: g.face(); g.arc(7, 6.5, 10, 10, start: 30, sweep: 120); g.eyes()
        case .moodNeutral: g.face(); g.line(8, 15, 16, 15); g.eyes()
        case .moodHard: g.face(); g.arc(7, 10.5, 10, 10, start: 210, sweep: 120); g.eyes()
        case .moodBad: g.face(); g.arc(6.5, 11.5, 11, 11, start: 195, sweep: 150); g.eyes()
        case .moodExcited:
            g.face(); g.line(7, 8.5, 9, 9.5); g.line(9, 9.5, 7, 10.5); g.line(15, 9.5, 17, 8.5); g.line(15, 9.5, 17, 10.5); g.oval(10, 13, 4, 5)
        case .moodCalm:
            g.face(); g.line(6.5, 9.5, 9.5, 9.5); g.line(14.5, 9.5, 17.5, 9.5); g.arc(8, 10, 8, 7, start: 30, sweep: 120)
        case .moodTired:
            g.face(); g.line(6.5, 10, 9.5, 10.5); g.line(14.5, 10.5, 17.5, 10); g.oval(10.5, 13, 3, 4)
        case .moodAngry:
            g.face(); g.line(6.5, 7.5, 9.5, 9); g.line(14.5, 9, 17.5, 7.5); g.line(8, 10.5, 9, 10.5); g.line(15, 10.5, 16, 10.5)
            g.arc(8, 13, 8, 7, start: 210, sweep: 120)
        case .moodFuckOff:
            g.width = 2
            g.path { p in
                p.move(to: .init(x: 8, y: 13)); p.addLine(to: .init(x: 8, y: 10.5))
                p.addCurve(to: .init(x: 9.5, y: 9), control1: .init(x: 8, y: 9.677), control2: .init(x: 8.677, y: 9))
                p.addCurve(to: .init(x: 11, y: 10.5), control1: .init(x: 10.323, y: 9), control2: .init(x: 11, y: 9.677)); p.addLine(to: .init(x: 11, y: 12))
                p.move(to: .init(x: 11, y: 11.5)); p.addLine(to: .init(x: 11, y: 3.5))
                p.addCurve(to: .init(x: 12.5, y: 2), control1: .init(x: 11, y: 2.677), control2: .init(x: 11.677, y: 2))
                p.addCurve(to: .init(x: 14, y: 3.5), control1: .init(x: 13.323, y: 2), control2: .init(x: 14, y: 2.677)); p.addLine(to: .init(x: 14, y: 12))
                p.move(to: .init(x: 14, y: 10.5)); p.addCurve(to: .init(x: 15.5, y: 9), control1: .init(x: 14, y: 9.677), control2: .init(x: 14.677, y: 9))
                p.addCurve(to: .init(x: 17, y: 10.5), control1: .init(x: 16.323, y: 9), control2: .init(x: 17, y: 9.677)); p.addLine(to: .init(x: 17, y: 12))
                p.move(to: .init(x: 17, y: 11.5)); p.addCurve(to: .init(x: 18.5, y: 10), control1: .init(x: 17, y: 10.677), control2: .init(x: 17.677, y: 10))
                p.addCurve(to: .init(x: 20, y: 11.5), control1: .init(x: 19.323, y: 10), control2: .init(x: 20, y: 10.677)); p.addLine(to: .init(x: 20, y: 16))
                p.addCurve(to: .init(x: 14, y: 22), control1: .init(x: 20, y: 19.314), control2: .init(x: 17.314, y: 22)); p.addLine(to: .init(x: 12, y: 22))
                p.addCurve(to: .init(x: 7.196, y: 19.3), control1: .init(x: 10.192, y: 22), control2: .init(x: 8.305, y: 20.984))
                p.addCurve(to: .init(x: 7, y: 19), control1: .init(x: 7.13, y: 19.2), control2: .init(x: 7.065, y: 19.1))
                p.addCurve(to: .init(x: 3.714, y: 13.272), control1: .init(x: 6.688, y: 18.521), control2: .init(x: 5.593, y: 16.612))
                p.addCurve(to: .init(x: 4.25, y: 11.25), control1: .init(x: 3.318, y: 12.568), control2: .init(x: 3.558, y: 11.665))
                p.addCurve(to: .init(x: 6.53, y: 11.53), control1: .init(x: 4.982, y: 10.811), control2: .init(x: 5.926, y: 10.927)); p.addLine(to: .init(x: 8, y: 13))
            }
        }
    }
}

/// Small drawing helper mirroring the Compose DrawScope calls used by ActionGlyph.kt.
struct GlyphPen {
    var ctx: GraphicsContext
    let color: Color?
    var width: CGFloat = 1.7

    private var style: StrokeStyle { StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round) }
    private var shading: GraphicsContext.Shading { color.map { .color($0) } ?? .foreground }

    private func draw(_ p: Path, fill: Bool) {
        if fill { ctx.fill(p, with: shading) } else { ctx.stroke(p, with: shading, style: style) }
    }

    func line(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) {
        var p = Path(); p.move(to: .init(x: x1, y: y1)); p.addLine(to: .init(x: x2, y: y2)); draw(p, fill: false)
    }
    func oval(_ x: Double, _ y: Double, _ w: Double, _ h: Double, fill: Bool = false) { draw(Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h)), fill: fill) }
    func circle(_ r: Double, _ cx: Double, _ cy: Double, fill: Bool = false) { oval(cx - r, cy - r, r * 2, r * 2, fill: fill) }
    func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, fill: Bool = false) { draw(Path(CGRect(x: x, y: y, width: w, height: h)), fill: fill) }
    func roundRect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double, fill: Bool = false) {
        draw(Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r), fill: fill)
    }
    func poly(_ pts: [(Double, Double)], close: Bool, fill: Bool = false) {
        var p = Path()
        for (i, pt) in pts.enumerated() { if i == 0 { p.move(to: .init(x: pt.0, y: pt.1)) } else { p.addLine(to: .init(x: pt.0, y: pt.1)) } }
        if close { p.closeSubpath() }
        draw(p, fill: fill)
    }
    func path(fill: Bool = false, _ build: (inout Path) -> Void) { var p = Path(); build(&p); draw(p, fill: fill) }
    /// drawArc(startAngle, sweepAngle, useCenter = false, topLeft, size): degrees, clockwise from 3 o'clock.
    func arc(_ x: Double, _ y: Double, _ w: Double, _ h: Double, start: Double, sweep: Double) {
        var unit = Path()
        unit.addArc(center: .zero, radius: 1, startAngle: .degrees(start), endAngle: .degrees(start + sweep), clockwise: false)
        let t = CGAffineTransform(translationX: x + w / 2, y: y + h / 2).scaledBy(x: w / 2, y: h / 2)
        draw(unit.applying(t), fill: false)
    }
    func rotated(_ deg: Double, cx: Double, cy: Double, _ body: (GlyphPen) -> Void) {
        var copy = self
        copy.ctx.translateBy(x: cx, y: cy); copy.ctx.rotate(by: .degrees(deg)); copy.ctx.translateBy(x: -cx, y: -cy)
        body(copy)
    }
    func face() { circle(9, 12, 12) }
    func eyes() { line(8, 9.5, 8.1, 9.5); line(16, 9.5, 16.1, 9.5) }
}

/// Drop-in for `Image(systemName:)`: REAL's Android glyph when one exists, otherwise the SF Symbol.
struct RealSymbol: View {
    let name: String
    var size: CGFloat = 20

    init(_ name: String, size: CGFloat = 20) { self.name = name; self.size = size }

    static func glyph(_ name: String) -> (ActionGlyph, Bool)? {
        switch name {
        case "xmark": return (.close, false)
        case "chevron.left": return (.back, false)
        case "chevron.right": return (.chevronRight, false)
        case "plus": return (.add, false)
        case "checkmark": return (.check, false)
        case "calendar": return (.calendar, false)
        case "waveform": return (.audioWave, false)
        case "trash": return (.delete, false)
        case "square.and.arrow.up": return (.share, false)
        case "pencil": return (.edit, false)
        case "doc": return (.file, false)
        case "text.alignleft", "book.closed", "note.text": return (.note, false)
        case "repeat": return (.repeat_, false)
        case "rectangle.stack": return (.cards, false)
        case "play.rectangle", "film": return (.video, false)
        case "photo": return (.photo, false)
        case "person": return (.person, false)
        case "person.fill": return (.person, true)
        case "music.note": return (.music, false)
        case "mic", "mic.fill": return (.mic, false)
        case "mappin", "mappin.and.ellipse": return (.pin, false)
        case "face.smiling": return (.moodVeryGood, false)
        case "checklist": return (.checklist, false)
        case "camera": return (.camera, false)
        case "star": return (.star, false)
        case "star.fill": return (.star, true)
        case "arrow.up.arrow.down": return (.sort, false)
        case "circle.lefthalf.filled": return (.theme, false)
        case "play.fill": return (.play, false)
        case "pause.fill": return (.pause, false)
        case "minus": return (.minus, false)
        case "gearshape": return (.settings, false)
        case "bell": return (.bell, false)
        case "bell.slash": return (.bellOff, false)
        case "line.3.horizontal": return (.dragHandle, false)
        case "square.grid.2x2": return (.category, false)
        default: return nil
        }
    }

    var body: some View {
        if name.hasPrefix("cat."), let icon = CategoryIcon(rawValue: String(name.dropFirst(4))) {
            CategoryIconGlyph(icon: icon, size: size)
        } else if let m = Self.glyph(name) {
            ActionIcon(m.0, filled: m.1, size: size)
        } else {
            Image(systemName: name).font(.system(size: size * 0.85))
        }
    }
}
