import SwiftUI

/// SVG path data ("M4 20.5h16a1.5 1.5 0 0 1…") → SwiftUI Path. Used for the category icons that Android keeps as path
/// strings (CategoryIconPaths), so both apps draw them from the same source. Supports M L H V C S Q T A Z, absolute
/// and relative, with repeated argument groups.
enum SvgPath {
    static func parse(_ d: String) -> Path {
        var path = Path()
        var tokens = Tokenizer(d)
        var current = CGPoint.zero, start = CGPoint.zero
        var lastControl: CGPoint? = nil
        var command: Character = "M"
        while let next = tokens.peekCommand() ?? (tokens.hasNumber ? command : nil) {
            if tokens.peekCommand() != nil { tokens.skipCommand() }
            command = next
            let relative = command.isLowercase
            func pt(_ x: Double, _ y: Double) -> CGPoint { relative ? CGPoint(x: current.x + x, y: current.y + y) : CGPoint(x: x, y: y) }
            switch command.uppercased().first! {
            case "M":
                guard let x = tokens.number(), let y = tokens.number() else { return path }
                current = pt(x, y); start = current; path.move(to: current); lastControl = nil
                command = relative ? "l" : "L" // further pairs are lines
            case "L":
                guard let x = tokens.number(), let y = tokens.number() else { return path }
                current = pt(x, y); path.addLine(to: current); lastControl = nil
            case "H":
                guard let x = tokens.number() else { return path }
                current = CGPoint(x: relative ? current.x + x : x, y: current.y); path.addLine(to: current); lastControl = nil
            case "V":
                guard let y = tokens.number() else { return path }
                current = CGPoint(x: current.x, y: relative ? current.y + y : y); path.addLine(to: current); lastControl = nil
            case "C":
                guard let x1 = tokens.number(), let y1 = tokens.number(), let x2 = tokens.number(), let y2 = tokens.number(),
                      let x = tokens.number(), let y = tokens.number() else { return path }
                let c1 = pt(x1, y1), c2 = pt(x2, y2), end = pt(x, y)
                path.addCurve(to: end, control1: c1, control2: c2); current = end; lastControl = c2
            case "S":
                guard let x2 = tokens.number(), let y2 = tokens.number(), let x = tokens.number(), let y = tokens.number() else { return path }
                let c1 = lastControl.map { CGPoint(x: 2 * current.x - $0.x, y: 2 * current.y - $0.y) } ?? current
                let c2 = pt(x2, y2), end = pt(x, y)
                path.addCurve(to: end, control1: c1, control2: c2); current = end; lastControl = c2
            case "Q":
                guard let x1 = tokens.number(), let y1 = tokens.number(), let x = tokens.number(), let y = tokens.number() else { return path }
                let c = pt(x1, y1), end = pt(x, y)
                path.addQuadCurve(to: end, control: c); current = end; lastControl = c
            case "T":
                guard let x = tokens.number(), let y = tokens.number() else { return path }
                let c = lastControl.map { CGPoint(x: 2 * current.x - $0.x, y: 2 * current.y - $0.y) } ?? current
                let end = pt(x, y)
                path.addQuadCurve(to: end, control: c); current = end; lastControl = c
            case "A":
                guard let rx = tokens.number(), let ry = tokens.number(), let rot = tokens.number(),
                      let large = tokens.flag(), let sweep = tokens.flag(), let x = tokens.number(), let y = tokens.number() else { return path }
                let end = pt(x, y)
                addArc(&path, from: current, to: end, rx: rx, ry: ry, rotation: rot, largeArc: large, sweep: sweep)
                current = end; lastControl = nil
            case "Z":
                path.closeSubpath(); current = start; lastControl = nil
            default:
                return path
            }
        }
        return path
    }

    /// SVG elliptical arc (endpoint form) as cubic Béziers.
    private static func addArc(_ path: inout Path, from p0: CGPoint, to p1: CGPoint, rx rxIn: Double, ry ryIn: Double,
                               rotation: Double, largeArc: Bool, sweep: Bool) {
        var rx = abs(rxIn), ry = abs(ryIn)
        if rx == 0 || ry == 0 || p0 == p1 { path.addLine(to: p1); return }
        let phi = rotation * .pi / 180, cosP = cos(phi), sinP = sin(phi)
        let dx = (p0.x - p1.x) / 2, dy = (p0.y - p1.y) / 2
        let x1 = cosP * dx + sinP * dy, y1 = -sinP * dx + cosP * dy
        let lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
        if lambda > 1 { rx *= sqrt(lambda); ry *= sqrt(lambda) }
        let num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        let den = rx * rx * y1 * y1 + ry * ry * x1 * x1
        var coef = sqrt(max(0, num / den))
        if largeArc == sweep { coef = -coef }
        let cx1 = coef * rx * y1 / ry, cy1 = -coef * ry * x1 / rx
        let cx = cosP * cx1 - sinP * cy1 + (p0.x + p1.x) / 2
        let cy = sinP * cx1 + cosP * cy1 + (p0.y + p1.y) / 2
        func angle(_ ux: Double, _ uy: Double, _ vx: Double, _ vy: Double) -> Double {
            let a = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
            return a
        }
        let theta1 = angle(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry)
        var delta = angle((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry)
        if !sweep && delta > 0 { delta -= 2 * .pi } else if sweep && delta < 0 { delta += 2 * .pi }
        let segments = max(1, Int(ceil(abs(delta) / (.pi / 2))))
        let step = delta / Double(segments)
        let k = 4.0 / 3.0 * tan(step / 4)
        func point(_ t: Double) -> (CGPoint, CGPoint) {
            let ct = cos(t), st = sin(t)
            let p = CGPoint(x: cx + rx * ct * cosP - ry * st * sinP, y: cy + rx * ct * sinP + ry * st * cosP)
            let d = CGPoint(x: -rx * st * cosP - ry * ct * sinP, y: -rx * st * sinP + ry * ct * cosP)
            return (p, d)
        }
        var t = theta1
        for _ in 0..<segments {
            let (a, da) = point(t), (b, db) = point(t + step)
            path.addCurve(to: b, control1: CGPoint(x: a.x + k * da.x, y: a.y + k * da.y),
                          control2: CGPoint(x: b.x - k * db.x, y: b.y - k * db.y))
            t += step
        }
    }

    private struct Tokenizer {
        let chars: [Character]; var i = 0
        init(_ s: String) { chars = Array(s) }
        mutating func skipSpace() { while i < chars.count, chars[i] == " " || chars[i] == "," || chars[i] == "\n" { i += 1 } }
        mutating func peekCommand() -> Character? {
            skipSpace()
            guard i < chars.count, chars[i].isLetter, chars[i] != "e", chars[i] != "E" else { return nil }
            return chars[i]
        }
        mutating func skipCommand() { i += 1 }
        var hasNumber: Bool {
            mutating get { skipSpace(); guard i < chars.count else { return false }; let c = chars[i]; return c.isNumber || c == "-" || c == "." || c == "+" }
        }
        mutating func number() -> Double? {
            skipSpace()
            var s = ""
            if i < chars.count, chars[i] == "-" || chars[i] == "+" { s.append(chars[i]); i += 1 }
            var dot = false
            while i < chars.count {
                let c = chars[i]
                if c.isNumber { s.append(c); i += 1 }
                else if c == "." && !dot { dot = true; s.append(c); i += 1 }
                else if (c == "e" || c == "E") && !s.isEmpty {
                    s.append(c); i += 1
                    if i < chars.count, chars[i] == "-" || chars[i] == "+" { s.append(chars[i]); i += 1 }
                } else { break }
            }
            return Double(s)
        }
        /// Arc flags may be written without separators ("0 01").
        mutating func flag() -> Bool? {
            skipSpace()
            guard i < chars.count, chars[i] == "0" || chars[i] == "1" else { return nil }
            defer { i += 1 }
            return chars[i] == "1"
        }
    }
}
