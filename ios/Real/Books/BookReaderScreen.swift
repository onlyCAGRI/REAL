import SwiftUI

/// BookReaderScreen.kt: vertical flow (or horizontal pages for PDF/EPUB), Inter body text with section initials,
/// theme + text-size chrome that hides while scrolling down, and the PDF "Orijinal sayfa" toggle.
struct BookReaderScreen: View {
    let book: Book
    let documents: BookDocuments
    let onBack: () -> Void
    @Environment(BookStore.self) private var store

    @State private var chromeVisible = true
    @State private var showTextTools = false
    @State private var showOriginal = false
    @State private var paragraphs: [BookParagraph] = []
    @State private var loading = false
    @State private var position: Int?
    @State private var restored = false
    @State private var currentPage = 0
    @State private var lastOffset: CGFloat = 0

    private var isPaged: Bool { ["pdf", "image", "epub"].contains(book.format ?? "") }
    private var hasOriginal: Bool { book.format == "pdf" || book.format == "image" }
    private var pageCount: Int { max(1, book.pageCount ?? 1) }
    private var horizontal: Bool { isPaged && store.readMode == .HORIZONTAL }
    private var label: String {
        if book.format == "epub" { return "\(pageCount) bölüm" }
        if let c = book.pageCount { return "\(c) sayfa" }
        return "Okuma"
    }

    var body: some View {
        let theme = store.theme
        ZStack {
            theme.background.ignoresSafeArea()
            if horizontal { horizontalReader } else { verticalReader }
            GlassEdgesOverlay(background: theme.background)
            chrome
        }
        .task(id: book.id) {
            currentPage = min(store.page(book), pageCount - 1)
            if !isPaged {
                loading = true
                paragraphs = await documents.text(book, page: 0)
                loading = false
            }
        }
    }

    // MARK: Vertical

    private var verticalReader: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ReaderHeader(label: label, title: book.title, theme: store.theme).id(-1)
                if isPaged {
                    ForEach(0..<pageCount, id: \.self) { i in
                        ReaderPageItem(book: book, index: i, count: pageCount, documents: documents, showOriginal: showOriginal).id(i)
                    }
                } else {
                    if loading { Text("Sayfa hazırlanıyor…").font(RealFont.inter(15)).foregroundStyle(store.theme.muted) }
                    let initials = sectionInitials(paragraphs)
                    ForEach(Array(paragraphs.enumerated()), id: \.offset) { i, p in
                        ReaderParagraph(paragraph: p, first: initials.contains(i)).id(i)
                    }
                }
            }
            .scrollTargetLayout()
            .padding(.horizontal, 26).padding(.top, 70).padding(.bottom, 150)
            .background(scrollTracker)
        }
        .coordinateSpace(name: "reader")
        .scrollIndicators(.hidden)
        .scrollPosition(id: $position, anchor: .top)
        .onAppear(perform: restore)
        .onChange(of: paragraphs.count) { _, _ in restore() }
        .onChange(of: position) { _, p in
            guard restored, let p, p >= 0 else { return }
            if isPaged { currentPage = p }
            store.savePosition(book, page: isPaged ? p : 0, offset: isPaged ? 0 : p)
        }
    }

    private func restore() {
        guard !restored else { return }
        if isPaged {
            let target = min(store.page(book), pageCount - 1)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { position = target > 0 ? target : nil; restored = true }
        } else if !paragraphs.isEmpty {
            let target = min(store.offset(book), paragraphs.count - 1)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { position = target > 0 ? target : nil; restored = true }
        }
    }

    /// Hides the icons while scrolling down and brings them back on the way up (NestedScrollConnection).
    private var scrollTracker: some View {
        GeometryReader { g in
            Color.clear.preference(key: ReaderOffsetKey.self, value: g.frame(in: .named("reader")).minY)
        }
        .onPreferenceChange(ReaderOffsetKey.self) { y in
            let dy = y - lastOffset
            if dy < -8 { if chromeVisible { withAnimation(.easeOut(duration: 0.1)) { chromeVisible = false; showTextTools = false } } }
            else if dy > 8 { if !chromeVisible { withAnimation(.easeOut(duration: 0.14)) { chromeVisible = true } } }
            if abs(dy) > 8 { lastOffset = y }
        }
    }

    // MARK: Horizontal

    private var horizontalReader: some View {
        TabView(selection: $currentPage) {
            ForEach(0..<pageCount, id: \.self) { i in
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        if i == 0 { ReaderHeader(label: label, title: book.title, theme: store.theme) }
                        ReaderPageItem(book: book, index: i, count: pageCount, documents: documents, showOriginal: showOriginal)
                    }
                    .padding(.horizontal, 26).padding(.top, 70).padding(.bottom, 150)
                }
                .scrollIndicators(.hidden)
                .tag(i)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .ignoresSafeArea(edges: .bottom)
        .onChange(of: currentPage) { _, p in store.savePosition(book, page: p) }
    }

    // MARK: Chrome

    private var chrome: some View {
        let theme = store.theme
        return VStack {
            HStack {
                ReaderCircleButton(theme: theme, glass: theme != .LIGHT, action: onBack) {
                    RealSymbol("chevron.left", size: 20.7)
                }
                .accessibilityLabel("Geri")
                Spacer()
            }
            .padding(.horizontal, 16).padding(.top, 8)
            .opacity(chromeVisible ? 1 : 0)
            Spacer()
            VStack(spacing: 8) {
                if hasOriginal {
                    Button { showOriginal.toggle() } label: {
                        Text(showOriginal ? "Metni oku" : "Orijinal sayfa").font(RealFont.inter(13)).foregroundStyle(theme.muted).padding(6)
                    }
                    .buttonStyle(.plain)
                }
                if showTextTools {
                    HStack(spacing: 0) {
                        ReaderPill(text: "A−", theme: theme) { store.fontSize = max(ReaderDefaults.minSize, store.fontSize - 1) }
                        Text("\(store.fontSize)").font(RealFont.inter(13)).foregroundStyle(theme.muted).padding(.horizontal, 14)
                        ReaderPill(text: "A+", theme: theme) { store.fontSize = min(ReaderDefaults.maxSize, store.fontSize + 1) }
                        if isPaged {
                            ReaderPill(text: store.readMode == .VERTICAL ? "↓ Dikey" : "→ Yatay", theme: theme) {
                                store.readMode = store.readMode == .VERTICAL ? .HORIZONTAL : .VERTICAL
                            }
                            .padding(.leading, 14)
                        }
                    }
                    .transition(.opacity)
                }
                HStack(spacing: 10) {
                    ReaderCircleButton(theme: theme, size: 38, action: { store.theme = theme.next }) { SunGlyph(color: theme.chromeText) }
                        .accessibilityLabel("Tema: \(theme.label)")
                    ReaderCircleButton(theme: theme, size: 38, action: { withAnimation(.easeOut(duration: 0.14)) { showTextTools.toggle() } }) {
                        Text("Tт").font(.system(size: 15, design: .serif))
                    }
                    .accessibilityLabel("Yazı ayarları")
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 10)
            .opacity(chromeVisible ? 1 : 0)
            .allowsHitTesting(chromeVisible)
        }
    }
}

private struct ReaderOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

/// ParagraphKind.sectionInitials: the first body paragraph of a section (after a heading, or the very first) gets
/// an enlarged initial.
func sectionInitials(_ paragraphs: [BookParagraph], followsHeading: Bool = true) -> Set<Int> {
    var out = Set<Int>()
    var pending = followsHeading
    for (i, p) in paragraphs.enumerated() {
        if p.heading { pending = true; continue }
        if pending, p.text.count > 40 { out.insert(i) }
        pending = false
    }
    return out
}

private struct ReaderHeader: View {
    let label: String
    let title: String
    let theme: ReaderTheme

    var body: some View {
        VStack(spacing: 4) {
            Text(label).font(RealFont.inter(13)).foregroundStyle(theme.muted)
            Text(title).font(RealFont.inter(18, .semibold)).kerning(-0.3).foregroundStyle(theme.text)
                .multilineTextAlignment(.center).lineSpacing(4)
        }
        .frame(maxWidth: .infinity)
        .padding(.bottom, 32)
    }
}

struct ReaderParagraph: View {
    let paragraph: BookParagraph
    let first: Bool
    @Environment(BookStore.self) private var store

    var body: some View {
        let size = CGFloat(store.fontSize)
        let theme = store.theme
        if paragraph.heading {
            let h = size * 1.15
            Text(paragraph.text)
                .font(RealFont.inter(h, .semibold)).kerning(-0.2)
                .lineSpacing(h * 0.3)
                .foregroundStyle(theme.text)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.top, 14).padding(.bottom, 14)
        } else {
            styled(size: size)
                .lineSpacing(size * (ReaderDefaults.lineHeight - 1))
                .foregroundStyle(theme.text)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, first ? 8 : 0).padding(.bottom, 12)
        }
    }

    private func styled(size: CGFloat) -> Text {
        let t = paragraph.text
        if !paragraph.spans.isEmpty { return spanned(size: size) }
        guard first, let idx = t.firstIndex(where: { $0.isLetter || $0.isNumber }) else {
            return Text(t).font(RealFont.inter(size))
        }
        let before = String(t[..<idx])
        let initial = String(t[idx])
        let rest = String(t[t.index(after: idx)...])
        return Text(before).font(RealFont.inter(size))
            + Text(initial).font(.system(size: size * 2.8, design: .serif))
            + Text(rest).font(RealFont.inter(size))
    }

    /// The PDF's own bold / italic runs (the drop initial applies to the first letter of the first run).
    private func spanned(size: CGFloat) -> Text {
        var out = Text("")
        var initialDone = !first
        for span in paragraph.spans {
            var piece = span.text
            if !initialDone, let idx = piece.firstIndex(where: { $0.isLetter || $0.isNumber }) {
                out = out + run(String(piece[..<idx]), span, size)
                    + Text(String(piece[idx])).font(.system(size: size * 2.8, design: .serif))
                piece = String(piece[piece.index(after: idx)...])
                initialDone = true
            }
            out = out + run(piece, span, size)
        }
        return out
    }

    private func run(_ text: String, _ span: BookSpan, _ size: CGFloat) -> Text {
        var t = Text(text).font(RealFont.inter(size, span.bold ? .bold : .regular))
        if span.italic { t = t.italic() }
        return t
    }
}

/// PagedItem: one PDF page / EPUB chapter, loaded lazily, as text or as the original page image.
private struct ReaderPageItem: View {
    let book: Book
    let index: Int
    let count: Int
    let documents: BookDocuments
    let showOriginal: Bool
    @Environment(BookStore.self) private var store

    @State private var paragraphs: [BookParagraph]?
    @State private var image: UIImage?
    @State private var failed = false

    var body: some View {
        let theme = store.theme
        VStack(alignment: .leading, spacing: 0) {
            Text("\(index + 1) / \(count)").font(RealFont.inter(12)).foregroundStyle(theme.muted)
                .frame(maxWidth: .infinity).padding(.top, 8).padding(.bottom, 14)
            if showOriginal {
                if let image {
                    Image(uiImage: image).resizable().scaledToFit().frame(maxWidth: .infinity)
                        .accessibilityLabel("Orijinal sayfa \(index + 1)")
                } else {
                    Text("Sayfa hazırlanıyor…").font(RealFont.inter(15)).foregroundStyle(theme.muted).frame(minHeight: 460, alignment: .top)
                }
            } else if let paragraphs {
                if paragraphs.isEmpty {
                    Text(book.format == "epub" ? "Bu bölümde metin yok." : "Bu sayfada metin kalmadı. Orijinal sayfayı görüntüleyebilirsin.")
                        .font(RealFont.inter(15)).foregroundStyle(theme.muted)
                } else {
                    let initials = sectionInitials(paragraphs, followsHeading: index == 0 || book.format == "epub")
                    ForEach(Array(paragraphs.enumerated()), id: \.offset) { i, p in
                        ReaderParagraph(paragraph: p, first: initials.contains(i))
                    }
                }
            } else if failed {
                Text("Sayfa şu an açılamadı. Biraz sonra tekrar deneyebilirsin.").font(RealFont.inter(12)).foregroundStyle(theme.muted)
            } else {
                Text("Sayfa hazırlanıyor…").font(RealFont.inter(15)).foregroundStyle(theme.muted).frame(minHeight: 460, alignment: .top)
            }
            Color.clear.frame(height: 28)
        }
        .task(id: "\(index)-\(showOriginal)") {
            if showOriginal {
                if image == nil { image = await documents.original(book, page: index) }
            } else if paragraphs == nil {
                paragraphs = await documents.text(book, page: index)
            }
        }
    }
}

private struct ReaderPill: View {
    let text: String
    let theme: ReaderTheme
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text).font(RealFont.inter(13)).foregroundStyle(theme.chromeText)
                .padding(.horizontal, 16).frame(minHeight: 36)
                .background(theme.chrome, in: RoundedRectangle(cornerRadius: 18))
        }
        .buttonStyle(.plain)
    }
}

/// SunIcon: ring with eight rays.
struct SunGlyph: View {
    let color: Color
    var body: some View {
        Canvas { ctx, size in
            let c = CGPoint(x: size.width / 2, y: size.height / 2)
            let r = min(size.width, size.height) * 0.19
            ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(color), lineWidth: 1.5)
            for i in 0..<8 {
                let a = Double(i) * .pi / 4
                var p = Path()
                p.move(to: CGPoint(x: c.x + cos(a) * r * 1.7, y: c.y + sin(a) * r * 1.7))
                p.addLine(to: CGPoint(x: c.x + cos(a) * r * 2.3, y: c.y + sin(a) * r * 2.3))
                ctx.stroke(p, with: .color(color), lineWidth: 1.2)
            }
        }
        .frame(width: 16, height: 16)
    }
}
