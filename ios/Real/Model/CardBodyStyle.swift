import Foundation

/// Marks of one group exclude each other on a character (one size, one colour); style marks combine.
enum BodyMarkGroup { case style, size, color }

/// Styles a card stack body can carry (CardBodyStyle.kt). The raw value is the stored form; keep it stable. Size
/// marks are paragraph formats (they always cover whole paragraphs); "Gövde" and the default colour are no mark.
enum BodyMark: String, CaseIterable, Hashable {
    case bold = "b", italic = "i", underline = "u", strike = "s"
    case small = "sm", large = "lg", title = "h"
    case orange = "co", yellow = "cy", green = "cg", blue = "cb", pink = "cp"

    var group: BodyMarkGroup {
        switch self {
        case .small, .large, .title: return .size
        case .orange, .yellow, .green, .blue, .pink: return .color
        default: return .style
        }
    }

    static func of(_ group: BodyMarkGroup) -> [BodyMark] { allCases.filter { $0.group == group } }
    fileprivate var ordinal: Int { Self.allCases.firstIndex(of: self) ?? 0 }
}

enum BodySize: String, CaseIterable { case small = "s", medium = "m", large = "l" }
enum BodyGap: String, CaseIterable { case none = "0", small = "1", large = "2" }

/// Writing settings of a whole card stack (set from its settings, copied onto every card of it).
struct BodyFormat: Codable, Hashable {
    var size: BodySize = .medium
    var indent = false
    var gap: BodyGap = .none

    var isDefault: Bool { self == BodyFormat() }

    enum CodingKeys: String, CodingKey { case size, indent, gap }

    init(size: BodySize = .medium, indent: Bool = false, gap: BodyGap = .none) {
        self.size = size; self.indent = indent; self.gap = gap
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let sizeKey: String? = try? c.decodeIfPresent(String.self, forKey: .size)
        let gapKey: String? = try? c.decodeIfPresent(String.self, forKey: .gap)
        let indentOn: Bool? = try? c.decodeIfPresent(Bool.self, forKey: .indent)
        size = sizeKey.flatMap(BodySize.init(rawValue:)) ?? .medium
        indent = indentOn ?? false
        gap = gapKey.flatMap(BodyGap.init(rawValue:)) ?? .none
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(size.rawValue, forKey: .size)
        try c.encode(indent, forKey: .indent)
        try c.encode(gap.rawValue, forKey: .gap)
    }
}

/// `start` inclusive, `end` exclusive, in UTF-16 offsets of the plain body (NSString / Android String units).
/// Stored as `[start, end, "b"]`.
struct BodySpan: Hashable, Encodable {
    var start: Int
    var end: Int
    var mark: BodyMark

    func encode(to encoder: Encoder) throws {
        var c = encoder.unkeyedContainer()
        try c.encode(start); try c.encode(end); try c.encode(mark.rawValue)
    }
}

/// Decodes `[[start, end, key], …]`, skipping rows it cannot read (unknown mark, wrong shape) instead of failing.
struct StoredBodySpans: Decodable {
    var spans: [BodySpan] = []

    private enum Cell: Decodable {
        case int(Int), text(String), other
        init(from decoder: Decoder) throws {
            let c = try decoder.singleValueContainer()
            if let i = try? c.decode(Int.self) { self = .int(i) }
            else if let s = try? c.decode(String.self) { self = .text(s) }
            else { self = .other }
        }
    }

    init(from decoder: Decoder) throws {
        let rows = (try? decoder.singleValueContainer().decode([[Cell]].self)) ?? []
        spans = rows.compactMap { row in
            guard row.count >= 3, case .int(let a) = row[0], case .int(let b) = row[1], case .text(let key) = row[2],
                  let mark = BodyMark(rawValue: key) else { return nil }
            return BodySpan(start: a, end: b, mark: mark)
        }
    }
}

/// The body stays plain text; styles ride next to it as ranges. Every edit goes through `applyEdit` so the ranges
/// move with the text.
enum BodySpans {
    /// Clamped to `length`, empty ranges dropped, overlapping or touching ranges of one mark merged.
    static func normalize(_ spans: [BodySpan], _ length: Int) -> [BodySpan] {
        var out: [BodySpan] = []
        for mark in BodyMark.allCases {
            var merged: [BodySpan] = []
            spans.filter { $0.mark == mark }
                .map { BodySpan(start: min(max($0.start, 0), length), end: min(max($0.end, 0), length), mark: mark) }
                .filter { $0.end > $0.start }
                .sorted { $0.start < $1.start }
                .forEach { span in
                    if let last = merged.last, span.start <= last.end { merged[merged.count - 1].end = max(last.end, span.end) }
                    else { merged.append(span) }
                }
            out += merged
        }
        return out.sorted { ($0.start, $0.mark.ordinal) < ($1.start, $1.mark.ordinal) }
    }

    /// True when every character of [start, end) carries `mark`.
    static func covers(_ spans: [BodySpan], _ mark: BodyMark, _ start: Int, _ end: Int) -> Bool {
        guard end > start else { return false }
        var at = start
        for s in spans.filter({ $0.mark == mark }).sorted(by: { $0.start < $1.start }) {
            if s.start > at { break }
            if s.end > at { at = s.end }
            if at >= end { return true }
        }
        return false
    }

    /// Marks new text typed at `position` continues: a span reaching up to the caret keeps going.
    static func marksAt(_ spans: [BodySpan], _ position: Int) -> Set<BodyMark> {
        Set(spans.filter { $0.start < position && position <= $0.end }.map(\.mark))
    }

    static func add(_ spans: [BodySpan], _ mark: BodyMark, _ start: Int, _ end: Int, _ length: Int) -> [BodySpan] {
        normalize(spans + [BodySpan(start: start, end: end, mark: mark)], length)
    }

    static func remove(_ spans: [BodySpan], _ mark: BodyMark, _ start: Int, _ end: Int) -> [BodySpan] {
        spans.flatMap { s -> [BodySpan] in
            if s.mark != mark || s.end <= start || s.start >= end { return [s] }
            var parts: [BodySpan] = []
            if start > s.start { parts.append(BodySpan(start: s.start, end: start, mark: mark)) }
            if s.end > end { parts.append(BodySpan(start: end, end: s.end, mark: mark)) }
            return parts
        }
    }

    /// Selection fully styled: the style comes off; otherwise the whole selection gets it (replacing another size or
    /// colour there).
    static func toggle(_ spans: [BodySpan], _ mark: BodyMark, _ start: Int, _ end: Int, _ length: Int) -> [BodySpan] {
        covers(spans, mark, start, end)
            ? normalize(remove(spans, mark, start, end), length)
            : add(clearGroup(spans, mark.group, start, end), mark, start, end, length)
    }

    /// Back to "Gövde" / default colour in [start, end). Style marks have no default and are left alone.
    static func clearGroup(_ spans: [BodySpan], _ group: BodyMarkGroup, _ start: Int, _ end: Int) -> [BodySpan] {
        guard group != .style else { return spans }
        return BodyMark.of(group).reduce(spans) { remove($0, $1, start, end) }
    }

    /// The whole paragraphs [start, end) touches, each with its line break.
    static func paragraphRange(_ text: String, _ start: Int, _ end: Int) -> Range<Int> {
        let u = Array(text.utf16)
        var from = 0
        if start > 0 {
            var i = min(start - 1, u.count - 1)
            while i >= 0 && u[i] != 10 { i -= 1 }
            from = i + 1
        }
        let lastChar = min(max(end, start + 1) - 1, u.count)
        var to = u.count
        if lastChar < u.count, let nl = u[lastChar...].firstIndex(of: 10) { to = nl + 1 }
        return from..<max(from, to)
    }

    /// Marks for the next typed letter at the caret: `pending` wins, else what the caret continues.
    static func typingMarks(_ spans: [BodySpan], _ position: Int, _ pending: Set<BodyMark>?) -> Set<BodyMark> {
        pending ?? marksAt(spans, position)
    }

    /// `current` with `mark` switched: on → the rest of its group goes off.
    static func switchMark(_ current: Set<BodyMark>, _ mark: BodyMark) -> Set<BodyMark> {
        if current.contains(mark) { return current.subtracting([mark]) }
        if mark.group == .style { return current.union([mark]) }
        return current.filter { $0.group != mark.group }.union([mark])
    }

    /// Moves `spans` from `old` to `new`. The changed region is found by common prefix / suffix; deleted text takes
    /// its styles with it, inserted text gets `typingMarks` (nil = continue the marks at the caret).
    static func applyEdit(_ spans: [BodySpan], old: String, new: String, typingMarks: Set<BodyMark>? = nil) -> [BodySpan] {
        let o = Array(old.utf16), n = Array(new.utf16)
        if o == n || (spans.isEmpty && (typingMarks?.isEmpty ?? true)) { return normalize(spans, n.count) }
        let limit = min(o.count, n.count)
        var prefix = 0
        while prefix < limit && o[prefix] == n[prefix] { prefix += 1 }
        var suffix = 0
        while suffix < limit - prefix && o[o.count - 1 - suffix] == n[n.count - 1 - suffix] { suffix += 1 }
        let removedEnd = o.count - suffix
        let inserted = n.count - suffix - prefix
        let delta = n.count - o.count
        func map(_ x: Int) -> Int { x <= prefix ? x : (x >= removedEnd ? x + delta : prefix) }
        var moved = spans.map { BodySpan(start: map($0.start), end: map($0.end), mark: $0.mark) }
        if inserted > 0 {
            // A new line after a heading starts as body text, like any notes app.
            let newLine = n[prefix..<(prefix + inserted)].contains(10)
            let marks = typingMarks ?? marksAt(spans, prefix).filter { !newLine || $0.group != .size }
            for mark in BodyMark.allCases {
                moved = marks.contains(mark)
                    ? moved + [BodySpan(start: prefix, end: prefix + inserted, mark: mark)]
                    : remove(moved, mark, prefix, prefix + inserted)
            }
        }
        return normalize(moved, n.count)
    }

    /// The part of `spans` inside [from, to), shifted so `from` becomes 0.
    static func slice(_ spans: [BodySpan], _ from: Int, _ to: Int) -> [BodySpan] {
        normalize(spans.compactMap { s in
            let a = max(s.start, from), b = min(s.end, to)
            return b > a ? BodySpan(start: a - from, end: b - from, mark: s.mark) : nil
        }, to - from)
    }
}
