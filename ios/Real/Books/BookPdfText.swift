import PDFKit
import UIKit

/// A run of paragraph text with the style of its font in the PDF.
struct BookSpan: Hashable {
    var text: String
    var bold = false
    var italic = false
}

/// BookDocumentRepository.textLayer: a copyable PDF page read from its own text layer, line by line with each line's
/// position and fonts, so paragraphs, headings, bold and italic follow the page. Pages without usable text return nil
/// and go to OCR.
enum BookPdfText {
    private struct Line {
        var spans: [BookSpan]
        var box: CGRect          // top-left origin, points
        var text: String { spans.map(\.text).joined() }
    }

    static func paragraphs(_ page: PDFPage) -> [BookParagraph]? {
        let bounds = page.bounds(for: .cropBox)
        guard let all = page.selection(for: bounds) else { return nil }
        var lines: [Line] = []
        for sel in all.selectionsByLine() {
            let raw = sel.bounds(for: page)
            guard raw.width > 0, raw.height > 0, let attr = sel.attributedString else { continue }
            var spans: [BookSpan] = []
            attr.enumerateAttribute(.font, in: NSRange(location: 0, length: attr.length)) { value, range, _ in
                let piece = clean((attr.string as NSString).substring(with: range))
                guard !piece.isEmpty else { return }
                let (bold, italic) = style(value as? UIFont)
                if let last = spans.last, last.bold == bold, last.italic == italic { spans[spans.count - 1].text += piece }
                else { spans.append(BookSpan(text: piece, bold: bold, italic: italic)) }
            }
            // Trim the line's outer whitespace across spans.
            while let f = spans.first, f.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { spans.removeFirst() }
            while let l = spans.last, l.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { spans.removeLast() }
            guard !spans.isEmpty else { continue }
            spans[0].text = String(spans[0].text.drop { $0.isWhitespace })
            spans[spans.count - 1].text = String(spans[spans.count - 1].text.reversed().drop { $0.isWhitespace }.reversed())
            let box = CGRect(x: raw.minX - bounds.minX, y: bounds.maxY - raw.maxY, width: raw.width, height: raw.height)
            lines.append(Line(spans: spans, box: box))
        }
        let text = lines.map(\.text).joined(separator: "\n")
        guard usable(text), !spaceless(lines.map(\.text)) else { return nil }

        // Page numbers / running heads: a lone number, or a short line at the very top or bottom edge.
        lines.removeAll { l in
            let t = l.text.trimmingCharacters(in: .whitespaces)
            let edge = l.box.minY < bounds.height * 0.07 || l.box.maxY > bounds.height * 0.94
            return t.range(of: "^[-–—\\s]*\\d{1,4}[-–—\\s]*$", options: .regularExpression) != nil || (edge && t.count <= 60 && lines.count > 3)
        }
        guard !lines.isEmpty else { return [] }

        let heights = lines.map(\.box.height).sorted()
        let h = max(heights[heights.count / 2], 4)
        let lefts = lines.filter { $0.box.width > bounds.width * 0.4 }.map(\.box.minX).sorted()
        let left = lefts.isEmpty ? lines.map(\.box.minX).min() ?? 0 : lefts[lefts.count / 2]
        let rights = lines.map(\.box.maxX).sorted()
        let right = rights[min(rights.count - 1, Int(Double(rights.count) * 0.9))]
        var steps: [CGFloat] = []
        for i in 1..<max(lines.count, 1) where lines[i].box.minY > lines[i - 1].box.minY {
            steps.append(lines[i].box.minY - lines[i - 1].box.minY)
        }
        steps.sort()
        let step = steps.isEmpty ? h * 1.4 : steps[steps.count / 2]

        var out: [BookParagraph] = []
        var current: [Line] = []
        func flush() {
            guard !current.isEmpty else { return }
            out.append(paragraph(current, bodyHeight: h, pageWidth: bounds.width))
            current = []
        }
        for (i, line) in lines.enumerated() {
            if let prev = current.last {
                let gap = line.box.minY - prev.box.minY
                let prevText = prev.text
                let indent = line.box.minX - left > h * 0.9 && prev.box.minX - left <= h * 0.9
                let prevShort = prev.box.maxX < right - h * 2
                let prevEnds = prevText.last.map { ".!?:;\"”’»…".contains($0) } ?? false
                let sizeJump = abs(line.box.height - prev.box.height) > h * 0.35
                let titleLine = isTitle(line, h: h, pageWidth: bounds.width) || isTitle(prev, h: h, pageWidth: bounds.width)
                if gap > step * 1.45 || gap < 0 || indent || (prevShort && prevEnds) || sizeJump || titleLine { flush() }
            }
            current.append(line)
            if i == lines.count - 1 { flush() }
        }
        flush()
        return out
    }

    /// Joins lines into one paragraph: soft hyphens at line ends removed, adjacent spans of the same style merged.
    private static func paragraph(_ lines: [Line], bodyHeight h: CGFloat, pageWidth: CGFloat) -> BookParagraph {
        var spans: [BookSpan] = []
        func add(_ s: BookSpan) {
            if let last = spans.last, last.bold == s.bold, last.italic == s.italic { spans[spans.count - 1].text += s.text }
            else { spans.append(s) }
        }
        for (i, line) in lines.enumerated() {
            if i > 0, var last = spans.last {
                let next = line.text.first
                if last.text.hasSuffix("-"), last.text.dropLast().last?.isLetter == true, next?.isLowercase == true {
                    last.text.removeLast(); spans[spans.count - 1] = last
                } else {
                    spans[spans.count - 1].text += " "
                }
            }
            line.spans.forEach(add)
        }
        let plain = spans.map(\.text).joined()
        let allBold = spans.allSatisfy(\.bold)
        let heading = (lines.count <= 2 && plain.count <= 80 &&
                       (lines.allSatisfy { $0.box.height > h * 1.2 } || allBold || isTitle(lines[0], h: h, pageWidth: pageWidth)))
            || (lines.count == 1 && BookDocuments.isHeadingLike(plain))
        // A heading keeps its own (semibold) style; inside body text the PDF's bold / italic spans are kept.
        let styled = heading ? [] : (spans.contains { $0.bold || $0.italic } ? spans : [])
        return BookParagraph(text: plain, heading: heading, spans: styled)
    }

    /// Bigger type, or a short centred line on its own.
    private static func isTitle(_ l: Line, h: CGFloat, pageWidth: CGFloat) -> Bool {
        let t = l.text
        if t.count > 70 { return false }
        if l.box.height > h * 1.3 { return true }
        let centred = abs(l.box.midX - pageWidth / 2) < pageWidth * 0.06 && l.box.width < pageWidth * 0.6
        return centred && (l.spans.allSatisfy(\.bold) || BookDocuments.isHeadingLike(t))
    }

    private static func style(_ font: UIFont?) -> (Bool, Bool) {
        guard let font else { return (false, false) }
        let traits = font.fontDescriptor.symbolicTraits
        let name = font.fontName.lowercased()
        let bold = traits.contains(.traitBold) || ["bold", "black", "heavy", "semibold", "demibold"].contains { name.contains($0) }
        let italic = traits.contains(.traitItalic) || name.contains("italic") || name.contains("oblique")
        return (bold, italic)
    }

    private static func clean(_ s: String) -> String {
        var r = s
        for (k, v) in ["\u{FB00}": "ff", "\u{FB01}": "fi", "\u{FB02}": "fl", "\u{FB03}": "ffi", "\u{FB04}": "ffl", "\u{FB05}": "st", "\u{FB06}": "st"] {
            r = r.replacingOccurrences(of: k, with: v)
        }
        r = r.replacingOccurrences(of: "[\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000]", with: " ", options: .regularExpression)
        return r.replacingOccurrences(of: "\n", with: " ").replacingOccurrences(of: "\r", with: " ")
    }

    /// BookDocumentRepository.usableText: enough letters and almost no broken characters.
    private static func usable(_ raw: String) -> Bool {
        let letters = raw.filter(\.isLetter).count
        guard letters >= 12 else { return false }
        let visible = max(raw.filter { !$0.isWhitespace }.count, 1)
        let broken = raw.unicodeScalars.filter { $0 == "\u{FFFD}" || (0xE000...0xF8FF).contains($0.value) }.count
        return Double(broken) / Double(visible) <= 0.15
    }

    /// BookDocumentRepository.looksSpaceless: word spaces missing from the text layer.
    private static func spaceless(_ texts: [String]) -> Bool {
        if texts.contains(where: { t in t.filter(\.isLetter).count >= 40 && !t.contains(where: \.isWhitespace) }) { return true }
        let tokens = texts.flatMap { $0.split(whereSeparator: \.isWhitespace) }.map { $0.filter(\.isLetter).count }.filter { $0 > 0 }
        guard tokens.count >= 5 else { return false }
        let avg = Double(tokens.reduce(0, +)) / Double(tokens.count)
        let longShare = Double(tokens.filter { $0 >= 25 }.count) / Double(tokens.count)
        return avg > 13 || longShare > 0.12
    }
}
