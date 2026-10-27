import SwiftUI
import UIKit

// CardBodyFormatBar.kt on iOS: styled card stack / journal bodies. The text stays plain; BodySpan ranges are drawn
// with NSAttributedString, so the editor (UITextView) and the read-only label share one renderer and the paragraph
// gap is real NSParagraphStyle spacing (no extra lines, offsets stay 1:1 with the plain text).

extension BodySize {
    var points: CGFloat { switch self { case .small: return 13; case .medium: return 14; case .large: return 16 } }
    /// Android lineHeight 18 / 20 / 23sp minus the font size.
    var lineGap: CGFloat { switch self { case .small: return 5; case .medium: return 6; case .large: return 7 } }
}

extension BodyGap {
    var points: CGFloat { switch self { case .none: return 0; case .small: return 4; case .large: return 9 } }
}

extension BodyMark {
    /// Text colour on a dark card / on a light card.
    var inkPair: (onDark: UInt32, onLight: UInt32)? {
        switch self {
        case .orange: return (0xFFFF8A5C, 0xFFC2410C)
        case .yellow: return (0xFFF5D96B, 0xFF8A6A00)
        case .green: return (0xFF7FDCA4, 0xFF1E7A4C)
        case .blue: return (0xFF8EB8FF, 0xFF1F5BB8)
        case .pink: return (0xFFF4A3C4, 0xFFB0306A)
        default: return nil
        }
    }

    func ink(onDark: Bool) -> UIColor? {
        inkPair.map { UIColor(Color(argb: onDark ? $0.onDark : $0.onLight)) }
    }

    var glyph: String {
        switch self {
        case .bold: return t("K")
        case .italic: return t("İ")
        case .underline: return t("A")
        case .strike: return t("Ü")
        default: return ""
        }
    }

    var label: String {
        switch self {
        case .bold: return t("Kalın")
        case .italic: return t("İtalik")
        case .underline: return t("Altı çizili")
        case .strike: return t("Üstü çizili")
        case .small: return t("Küçük")
        case .large: return t("Büyük")
        case .title: return t("Başlık")
        case .orange: return t("Turuncu")
        case .yellow: return t("Sarı")
        case .green: return t("Yeşil")
        case .blue: return t("Mavi")
        case .pink: return t("Pembe")
        }
    }
}

enum CardBodyText {
    /// Base attributes of a body in `format` (font, colour, line and paragraph spacing, first-line indent).
    static func baseAttributes(_ format: BodyFormat, foreground: UIColor, size: CGFloat? = nil) -> [NSAttributedString.Key: Any] {
        let points = size ?? format.size.points
        return [.font: font(points, bold: false, italic: false), .foregroundColor: foreground,
                .paragraphStyle: paragraph(format, points: points)]
    }

    /// Attributes the next typed letter gets (UITextView.typingAttributes) for `marks`.
    static func typingAttributes(_ marks: Set<BodyMark>, format: BodyFormat, foreground: UIColor, onDark: Bool, size: CGFloat? = nil) -> [NSAttributedString.Key: Any] {
        var a = baseAttributes(format, foreground: foreground, size: size)
        apply(marks, to: &a, base: size ?? format.size.points, onDark: onDark)
        return a
    }

    static func attributed(_ text: String, styles: [BodySpan], format: BodyFormat, foreground: UIColor, onDark: Bool,
                           size: CGFloat? = nil) -> NSAttributedString {
        let base = size ?? format.size.points
        let out = NSMutableAttributedString(string: text, attributes: baseAttributes(format, foreground: foreground, size: size))
        let length = (text as NSString).length
        let spans = BodySpans.normalize(styles, length)
        guard !spans.isEmpty else { return out }
        // Each run between span edges gets one font (size × weight × slant) and its decorations.
        let edges = Array(Set(spans.flatMap { [$0.start, $0.end] } + [0, length])).sorted()
        for (a, b) in zip(edges, edges.dropFirst()) where b > a {
            let marks = Set(spans.filter { $0.start <= a && b <= $0.end }.map(\.mark))
            guard !marks.isEmpty else { continue }
            var attrs: [NSAttributedString.Key: Any] = [:]
            apply(marks, to: &attrs, base: base, onDark: onDark)
            out.addAttributes(attrs, range: NSRange(location: a, length: b - a))
        }
        return out
    }

    private static func apply(_ marks: Set<BodyMark>, to a: inout [NSAttributedString.Key: Any], base: CGFloat, onDark: Bool) {
        let scale: CGFloat = marks.contains(.title) ? 1.5 : marks.contains(.large) ? 1.22 : marks.contains(.small) ? 0.86 : 1
        let italic = marks.contains(.italic)
        let f = font(base * scale, bold: marks.contains(.bold) || marks.contains(.title), italic: italic)
        a[.font] = f
        if italic && !f.fontDescriptor.symbolicTraits.contains(.traitItalic) { a[.obliqueness] = 0.18 }
        if marks.contains(.underline) { a[.underlineStyle] = NSUnderlineStyle.single.rawValue }
        if marks.contains(.strike) { a[.strikethroughStyle] = NSUnderlineStyle.single.rawValue }
        if let ink = marks.lazy.compactMap({ $0.ink(onDark: onDark) }).first { a[.foregroundColor] = ink }
    }

    private static func font(_ size: CGFloat, bold: Bool, italic: Bool) -> UIFont {
        let base = UIFont(name: bold ? "Inter-Bold" : "Inter-Regular", size: size) ?? .systemFont(ofSize: size, weight: bold ? .bold : .regular)
        guard italic, let d = base.fontDescriptor.withSymbolicTraits(base.fontDescriptor.symbolicTraits.union(.traitItalic)) else { return base }
        return UIFont(descriptor: d, size: size)
    }

    private static func paragraph(_ format: BodyFormat, points: CGFloat) -> NSParagraphStyle {
        let p = NSMutableParagraphStyle()
        p.lineSpacing = format.size.lineGap * points / format.size.points
        p.paragraphSpacing = format.gap.points
        p.firstLineHeadIndent = format.indent ? points * 1.6 : 0
        return p
    }
}

/// Read-only styled body (a UILabel, so paragraph spacing and indent draw like the editor).
struct StyledBodyText: UIViewRepresentable {
    let text: String
    let styles: [BodySpan]
    var format = BodyFormat()
    var foreground: Color
    var onDark = true
    var size: CGFloat? = nil
    var lineLimit = 0

    func makeUIView(context: Context) -> UILabel {
        let label = UILabel()
        label.isUserInteractionEnabled = false
        label.lineBreakMode = .byTruncatingTail
        label.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return label
    }

    func updateUIView(_ label: UILabel, context: Context) {
        label.numberOfLines = lineLimit
        label.attributedText = CardBodyText.attributed(text, styles: styles, format: format, foreground: UIColor(foreground), onDark: onDark, size: size)
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UILabel, context: Context) -> CGSize? {
        let width = proposal.width ?? UIScreen.main.bounds.width
        let fit = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: width, height: ceil(fit.height))
    }
}

/// Plain text + styles + caret, edited together. Every text change moves the styles with `BodySpans.applyEdit`.
struct RichBodyEditor: UIViewRepresentable {
    @Binding var text: String
    @Binding var styles: [BodySpan]
    @Binding var selection: NSRange
    /// Marks for the next typed letters after a style was tapped with no selection; nil = follow the caret.
    @Binding var pending: Set<BodyMark>?
    var format = BodyFormat()
    var foreground: Color
    var onDark = true
    var size: CGFloat? = nil
    var minHeight: CGFloat = 0
    var autoFocus = false

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UITextView {
        let view = UITextView()
        view.delegate = context.coordinator
        view.backgroundColor = .clear
        view.isScrollEnabled = false
        view.textContainerInset = .zero
        view.textContainer.lineFragmentPadding = 0
        view.autocapitalizationType = .sentences
        view.tintColor = UIColor(foreground)
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        context.coordinator.restyle(view)
        if autoFocus { DispatchQueue.main.async { view.becomeFirstResponder() } }
        return view
    }

    func updateUIView(_ view: UITextView, context: Context) {
        let c = context.coordinator
        c.parent = self
        view.tintColor = UIColor(foreground)
        // Outside changes (a style tapped in the bar, a new format, a reset text) redraw; typing already did.
        if view.markedTextRange == nil && c.drawn != c.snapshot { c.restyle(view) }
        else if view.markedTextRange == nil { view.typingAttributes = c.typingAttributes(at: view.selectedRange) }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextView, context: Context) -> CGSize? {
        let width = proposal.width ?? UIScreen.main.bounds.width
        let fit = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: width, height: max(ceil(fit.height), minHeight))
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: RichBodyEditor
        /// What the view shows now; compared with the bindings to know when to redraw.
        var drawn: Snapshot?
        private var typing = false
        private var applying = false

        struct Snapshot: Equatable {
            var text: String; var styles: [BodySpan]; var format: BodyFormat; var onDark: Bool; var size: CGFloat?
        }

        init(_ parent: RichBodyEditor) { self.parent = parent }

        var snapshot: Snapshot { Snapshot(text: parent.text, styles: parent.styles, format: parent.format, onDark: parent.onDark, size: parent.size) }

        func typingAttributes(at range: NSRange) -> [NSAttributedString.Key: Any] {
            let marks = range.length > 0 ? BodySpans.marksAt(parent.styles, range.location + range.length)
                : BodySpans.typingMarks(parent.styles, range.location, parent.pending)
            return CardBodyText.typingAttributes(marks, format: parent.format, foreground: UIColor(parent.foreground), onDark: parent.onDark, size: parent.size)
        }

        func restyle(_ view: UITextView) {
            applying = true
            defer { applying = false }
            let selected = view.selectedRange
            view.attributedText = CardBodyText.attributed(parent.text, styles: parent.styles, format: parent.format,
                                                          foreground: UIColor(parent.foreground), onDark: parent.onDark, size: parent.size)
            let length = (parent.text as NSString).length
            view.selectedRange = NSRange(location: min(selected.location, length), length: min(selected.length, max(length - min(selected.location, length), 0)))
            view.typingAttributes = typingAttributes(at: view.selectedRange)
            drawn = snapshot
        }

        func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool {
            typing = true
            return true
        }

        func textViewDidChange(_ view: UITextView) {
            // Selection callbacks of this same keystroke may still come; they must not drop a pending style.
            defer { DispatchQueue.main.async { self.typing = false } }
            // Keyboard composition (marked text) settles first; the styles follow once it is committed.
            guard view.markedTextRange == nil else { return }
            let new = view.text ?? ""
            let old = drawn?.text ?? parent.text
            guard new != old else { return }
            let styles = BodySpans.applyEdit(parent.styles, old: old, new: new, typingMarks: parent.pending)
            let restyleNeeded = !styles.isEmpty || !parent.styles.isEmpty
            parent.text = new
            parent.styles = styles
            parent.selection = view.selectedRange
            if restyleNeeded { restyle(view) } else { drawn = snapshot }
        }

        func textViewDidChangeSelection(_ view: UITextView) {
            guard !applying else { return }
            parent.selection = view.selectedRange
            // Moving the caret drops a pending style, like Android.
            if !typing && parent.pending != nil { parent.pending = nil }
            if view.markedTextRange == nil { view.typingAttributes = typingAttributes(at: view.selectedRange) }
        }
    }
}

/// "Aa" bar logic (BodyFormatControls): sizes act on whole paragraphs, styles and colours on the selection; with a
/// bare caret a tap sets the marks for the next typed letters.
struct BodyFormatControls: View {
    let text: String
    let selection: NSRange
    @Binding var styles: [BodySpan]
    @Binding var pending: Set<BodyMark>?
    @Binding var open: Bool
    var foreground: Color = .white
    var onDark = true

    var body: some View {
        let length = (text as NSString).length
        let start = min(selection.location, length), end = min(selection.location + selection.length, length)
        let collapsed = end <= start
        let paragraphs = BodySpans.paragraphRange(text, start, end)
        let charMarks: Set<BodyMark> = collapsed ? BodySpans.typingMarks(styles, start, pending)
            : Set(BodyMark.allCases.filter { BodySpans.covers(styles, $0, start, end) })
        let sizeMarks: Set<BodyMark> = paragraphs.isEmpty ? charMarks.filter { $0.group == .size }
            : Set(BodyMark.of(.size).filter { BodySpans.covers(styles, $0, paragraphs.lowerBound, paragraphs.upperBound) })
        let active = charMarks.filter { $0.group != .size }.union(sizeMarks)
        func commit(_ next: [BodySpan]) { styles = BodySpans.normalize(next, length) }
        func toggle(_ mark: BodyMark) {
            if mark.group == .size && !paragraphs.isEmpty { commit(BodySpans.toggle(styles, mark, paragraphs.lowerBound, paragraphs.upperBound, length)) }
            else if collapsed { pending = BodySpans.switchMark(active, mark) }
            else { commit(BodySpans.toggle(styles, mark, start, end, length)) }
        }
        func clear(_ group: BodyMarkGroup) {
            if group == .size && !paragraphs.isEmpty { commit(BodySpans.clearGroup(styles, group, paragraphs.lowerBound, paragraphs.upperBound)) }
            else if collapsed { pending = active.filter { $0.group != group } }
            else { commit(BodySpans.clearGroup(styles, group, start, end)) }
        }
        return CardBodyFormatBar(open: $open, active: active, foreground: foreground, onDark: onDark, onToggle: toggle, onClear: clear)
    }
}

struct CardBodyFormatBar: View {
    @Binding var open: Bool
    let active: Set<BodyMark>
    var foreground: Color = .white
    var onDark = true
    let onToggle: (BodyMark) -> Void
    let onClear: (BodyMarkGroup) -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Ink on a lit chip (the chip is filled with `foreground`).
    private var onInk: Color { onDark ? Color(argb: 0xFF161616) : .white }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                chip(on: open, label: open ? t("Biçim şeridini kapat") : t("Biçim şeridini aç"), action: {
                    withAnimation(reduceMotion ? nil : .timingCurve(0.2, 0, 0, 1, duration: 0.2)) { open.toggle() }
                }) {
                    Text("Aa").font(RealFont.inter(14, .semibold)).foregroundStyle(open ? onInk : foreground)
                }
                if open {
                    ForEach(BodyMark.of(.style), id: \.self) { mark in
                        let on = active.contains(mark)
                        chip(on: on, label: mark.label, action: { onToggle(mark) }) {
                            Text(mark.glyph)
                                .font(RealFont.inter(15, mark == .bold ? .bold : .medium))
                                .italic(mark == .italic)
                                .underline(mark == .underline)
                                .strikethrough(mark == .strike)
                                .foregroundStyle(on ? onInk : foreground)
                        }
                        .transition(.opacity)
                    }
                }
            }
            if open {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        let noSize = BodyMark.of(.size).allSatisfy { !active.contains($0) }
                        sizeChip(t("Küçük"), 11, bold: false, on: active.contains(.small)) { onToggle(.small) }
                        sizeChip(t("Gövde"), 13, bold: false, on: noSize) { onClear(.size) }
                        sizeChip(t("Büyük"), 15, bold: false, on: active.contains(.large)) { onToggle(.large) }
                        sizeChip(t("Başlık"), 15, bold: true, on: active.contains(.title)) { onToggle(.title) }
                        Rectangle().fill(foreground.opacity(0.18)).frame(width: 1, height: 20).padding(.horizontal, 2)
                        let noColor = BodyMark.of(.color).allSatisfy { !active.contains($0) }
                        colorDot(foreground, on: noColor, label: t("Varsayılan renk")) { onClear(.color) }
                        ForEach(BodyMark.of(.color), id: \.self) { mark in
                            colorDot(Color(uiColor: mark.ink(onDark: onDark) ?? .white), on: active.contains(mark), label: mark.label) { onToggle(mark) }
                        }
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }

    private func chip<Content: View>(on: Bool, label: String, action: @escaping () -> Void, @ViewBuilder content: () -> Content) -> some View {
        Button(action: action) {
            content()
                .frame(minWidth: 36, minHeight: 36)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(on ? foreground : foreground.opacity(0.10)))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(foreground.opacity(on ? 0 : 0.18), lineWidth: 0.7))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func sizeChip(_ label: String, _ points: CGFloat, bold: Bool, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(RealFont.inter(points, bold ? .bold : .medium)).foregroundStyle(on ? onInk : foreground)
                .padding(.horizontal, 10).frame(height: 32)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(on ? foreground : foreground.opacity(0.10)))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(foreground.opacity(on ? 0 : 0.18), lineWidth: 0.7))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func colorDot(_ color: Color, on: Bool, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Circle().fill(color).padding(5)
                .frame(width: 28, height: 28)
                .overlay(Circle().strokeBorder(on ? foreground : foreground.opacity(0.22), lineWidth: on ? 2 : 0.7))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// Stack settings rows (CardBodyFormatSettings): text size, first-line indent, paragraph gap for the whole stack.
struct CardBodyFormatSettings: View {
    @Binding var format: BodyFormat

    var body: some View {
        Picker(t("Yazı boyutu"), selection: $format.size) {
            Text(t("Küçük")).tag(BodySize.small)
            Text(t("Orta")).tag(BodySize.medium)
            Text(t("Büyük")).tag(BodySize.large)
        }
        Picker(t("Paragraf başı"), selection: $format.indent) {
            Text(t("Yok")).tag(false)
            Text(t("Girinti")).tag(true)
        }
        Picker(t("Paragraf aralığı"), selection: $format.gap) {
            Text(t("Yok")).tag(BodyGap.none)
            Text(t("Az")).tag(BodyGap.small)
            Text(t("Çok")).tag(BodyGap.large)
        }
    }
}
