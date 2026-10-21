import SwiftUI

// BooksShelfScreen.kt: flat front covers on a 3D-rotating box; each shelf always keeps one book open (cover turned
// towards the viewer) and the selected book is held in the centre while the others show only their spines.

enum BookShelfMetrics {
    static let coverW: CGFloat = 112
    static let coverH: CGFloat = 168
    static let spineW: CGFloat = 30
    static let spineGap: CGFloat = 10
    static let openDeg: CGFloat = 24
    static let camDist: CGFloat = 420
    static let openDuration = 0.52
}

/// A face's on-screen corners (TL, TR, BR, BL) in slot coordinates.
struct BookGeom {
    var width: CGFloat
    var spine: [CGPoint]
    var cover: [CGPoint]
    var coverVisible: Bool

    /// Real 3D box: spine face (thickness t) + front cover (width w), hinge axis through the spine centre, rotated by
    /// [deg] around the vertical axis and projected with perspective, exactly like bookGeom on Android.
    static func make(deg: CGFloat, w: CGFloat = BookShelfMetrics.coverW, t: CGFloat = BookShelfMetrics.spineW, h: CGFloat = BookShelfMetrics.coverH) -> BookGeom {
        let a = deg * .pi / 180
        let c = cos(a), s = sin(a)
        let cam = BookShelfMetrics.camDist
        func k(_ xr: CGFloat, _ z: CGFloat) -> CGFloat { cam / (cam - (-xr * s + z * c)) }
        func sx(_ xr: CGFloat, _ z: CGFloat) -> CGFloat { (xr * c + z * s) * k(xr, z) }
        let xBack = sx(0, -t / 2), xFront = sx(0, t / 2), xRight = sx(w, t / 2)
        let kBack = k(0, -t / 2), kFront = k(0, t / 2), kRight = k(w, t / 2)
        let minX = min(xBack, xFront, xRight), maxX = max(xBack, xFront, xRight)
        let cy = h / 2
        func quad(_ xl: CGFloat, _ kl: CGFloat, _ xr: CGFloat, _ kr: CGFloat) -> [CGPoint] {
            [CGPoint(x: xl - minX, y: cy - cy * kl), CGPoint(x: xr - minX, y: cy - cy * kr),
             CGPoint(x: xr - minX, y: cy + cy * kr), CGPoint(x: xl - minX, y: cy + cy * kl)]
        }
        return BookGeom(width: maxX - minX, spine: quad(xBack, kBack, xFront, kFront),
                        cover: quad(xFront, kFront, xRight, kRight), coverVisible: c * cam > t / 2 + 1)
    }
}

/// setPolyToPoly equivalent: maps the rect (0,0,w,h) onto an arbitrary quad.
struct QuadProjection: GeometryEffect {
    var quad: [CGPoint]

    func effectValue(size: CGSize) -> ProjectionTransform {
        guard quad.count == 4, size.width > 0, size.height > 0 else { return ProjectionTransform() }
        let (x0, y0) = (quad[0].x, quad[0].y), (x1, y1) = (quad[1].x, quad[1].y)
        let (x2, y2) = (quad[2].x, quad[2].y), (x3, y3) = (quad[3].x, quad[3].y)
        let dx1 = x1 - x2, dx2 = x3 - x2, dx3 = x0 - x1 + x2 - x3
        let dy1 = y1 - y2, dy2 = y3 - y2, dy3 = y0 - y1 + y2 - y3
        var g: CGFloat = 0, hh: CGFloat = 0
        if abs(dx3) > 0.0001 || abs(dy3) > 0.0001 {
            let det = dx1 * dy2 - dx2 * dy1
            if abs(det) > 0.000001 {
                g = (dx3 * dy2 - dx2 * dy3) / det
                hh = (dx1 * dy3 - dx3 * dy1) / det
            }
        }
        let a = x1 - x0 + g * x1, b = x3 - x0 + hh * x3, c = x0
        let d = y1 - y0 + g * y1, e = y3 - y0 + hh * y3, f = y0
        var t = ProjectionTransform()
        t.m11 = a / size.width; t.m12 = d / size.width; t.m13 = g / size.width
        t.m21 = b / size.height; t.m22 = e / size.height; t.m23 = hh / size.height
        t.m31 = c; t.m32 = f; t.m33 = 1
        return t
    }
}

struct BooksShelfScreen: View {
    let shelves: [Shelf]
    let theme: ReaderTheme
    var hiddenId: String?
    var focusId: String?
    let onBookTap: (Book, CGRect) -> Void
    let onAdd: () -> Void
    let onBack: () -> Void
    let onTheme: () -> Void
    var onAddSection: () -> Void = {}
    var onRenameSection: (String) -> Void = { _ in }
    var onDeleteSection: (String) -> Void = { _ in }
    var onMoveBook: (String, String) -> Void = { _, _ in }

    var body: some View {
        ZStack(alignment: .top) {
            theme.background.ignoresSafeArea()
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 0) {
                        Text("KİTAPLAR")
                            .font(.system(size: 52, weight: .medium, design: .serif)).kerning(-1)
                            .foregroundStyle(theme.text)
                            .padding(.top, 26).padding(.bottom, 34)
                        ForEach(shelves) { shelf in
                            ShelfSection(shelf: shelf, theme: theme, hiddenId: hiddenId, focusId: focusId, onBookTap: onBookTap,
                                         onRename: { onRenameSection(shelf.name) }, onDelete: { onDeleteSection(shelf.name) },
                                         onDropBook: { id in onMoveBook(id, shelf.name) })
                                .id(shelf.name)
                                .padding(.bottom, 36)
                        }
                        Color.clear.frame(height: 170)
                    }
                    .frame(maxWidth: .infinity)
                }
                .scrollIndicators(.hidden)
                .onAppear {
                    if let focusId, let shelf = shelves.first(where: { $0.books.contains { $0.id == focusId } }) {
                        proxy.scrollTo(shelf.name, anchor: .center)
                    }
                }
            }
            GlassEdgesOverlay(background: theme.background)

            HStack {
                ReaderCircleButton(theme: theme, glass: true, action: onBack) {
                    RealSymbol("chevron.left", size: 20.7)
                }
                .accessibilityLabel("Geri")
                Spacer()
                ReaderCircleButton(theme: theme, glass: true, action: onTheme) { ThemeGlyph() }
                    .accessibilityLabel("Tema değiştir: \(theme.label)")
            }
            .padding(.horizontal, 16).padding(.top, 8)

            if shelves.isEmpty {
                Text("Kitap ekleyerek kitaplığını oluştur.")
                    .font(RealFont.inter(15)).foregroundStyle(theme.text.opacity(0.55))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .overlay(alignment: .bottom) {
            HStack(spacing: 10) {
                Button(action: onAdd) {
                    Text("Kitap Ekle").font(RealFont.inter(17, .medium)).foregroundStyle(theme.chromeText)
                        .frame(width: 172, height: 56)
                        .background(theme.chrome, in: Capsule())
                        .overlay(Capsule().strokeBorder(.white.opacity(0.12), lineWidth: 0.8))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Kitap ekle")
                // "+": yeni bölüm (raf) açar; kitaplar basılı tutup sürüklenerek oraya taşınır.
                Button(action: onAddSection) {
                    ActionIcon(.add, color: theme.chromeText, size: 22)
                        .frame(width: 56, height: 56)
                        .background(theme.chrome, in: Circle())
                        .overlay(Circle().strokeBorder(.white.opacity(0.12), lineWidth: 0.8))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Bölüm ekle")
            }
            .padding(.bottom, 18)
        }
    }
}

/// GlassEdges.kt: content softly fades under the status bar and the bottom pill.
struct GlassEdgesOverlay: View {
    let background: Color
    var body: some View {
        VStack(spacing: 0) {
            LinearGradient(colors: [background, background.opacity(0.85), background.opacity(0)], startPoint: .top, endPoint: .bottom)
                .frame(height: 110)
            Spacer()
            LinearGradient(colors: [background.opacity(0), background.opacity(0.85), background], startPoint: .top, endPoint: .bottom)
                .frame(height: 170)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}

private struct ShelfSection: View {
    let shelf: Shelf
    let theme: ReaderTheme
    let hiddenId: String?
    let focusId: String?
    let onBookTap: (Book, CGRect) -> Void
    let onRename: () -> Void
    let onDelete: () -> Void
    let onDropBook: (String) -> Void

    @State private var selectedId: String = ""
    @State private var dragAccum: CGFloat = 0
    @State private var dropHere = false

    private var totalWidth: CGFloat {
        let n = CGFloat(shelf.books.count)
        return (n - 1) * BookGeom.make(deg: 90).width + BookGeom.make(deg: BookShelfMetrics.openDeg).width + (n - 1) * BookShelfMetrics.spineGap
    }

    var body: some View {
        VStack(spacing: 0) {
            // Bölüm başlığı: basılı tutunca menü; üstüne kitap sürüklenince çizgiler parlar.
            HStack(spacing: 14) {
                Rectangle().fill(theme.text.opacity(dropHere ? 0.8 : 0.28)).frame(height: 0.7)
                Text(shelf.name.uppercased(with: Locale(identifier: "tr_TR")))
                    .font(.system(size: 12, design: .serif)).kerning(1.2)
                    .foregroundStyle(theme.text.opacity(0.85)).lineLimit(1).fixedSize()
                Rectangle().fill(theme.text.opacity(dropHere ? 0.8 : 0.28)).frame(height: 0.7)
            }
            .animation(.easeOut(duration: 0.16), value: dropHere)
            .padding(.horizontal, 40)
            .padding(.vertical, 6)
            .contentShape(Rectangle())
            .contextMenu {
                Button("Yeniden adlandır", action: onRename)
                if shelf.name != BookStore.defaultSection {
                    Button("Bölümü sil", role: .destructive, action: onDelete)
                }
            }
            .padding(.bottom, 16)

            if shelf.books.isEmpty {
                Text("Kitapları buraya sürükle")
                    .font(RealFont.inter(14)).foregroundStyle(theme.text.opacity(0.45))
                    .frame(maxWidth: .infinity).frame(height: BookShelfMetrics.coverH * 0.6)
                    .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(theme.text.opacity(dropHere ? 0.6 : 0.18),
                                                                             style: StrokeStyle(lineWidth: 1, dash: [5, 5])))
                    .padding(.horizontal, 40)
            } else {
                shelfRow
            }
        }
        .contentShape(Rectangle())
        .dropDestination(for: String.self) { ids, _ in
            guard let id = ids.first else { return false }
            if !shelf.books.contains(where: { $0.id == id }) { onDropBook(id) }
            return true
        } isTargeted: { dropHere = $0 }
        .onAppear {
            if selectedId.isEmpty { selectedId = shelf.books.first(where: { $0.id == focusId })?.id ?? shelf.books.first?.id ?? "" }
        }
    }

    private var shelfRow: some View {
            GeometryReader { geo in
                let fits = totalWidth <= geo.size.width - 24
                ScrollViewReader { proxy in
                    ScrollView(.horizontal) {
                        HStack(spacing: BookShelfMetrics.spineGap) {
                            ForEach(shelf.books) { b in
                                BookSlot(book: b, open: current == b.id, hidden: hiddenId == b.id,
                                         onSelect: { withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: BookShelfMetrics.openDuration)) { selectedId = b.id } },
                                         onOpen: { frame in onBookTap(b, frame) })
                                .id(b.id)
                            }
                        }
                        .padding(.horizontal, fits ? max(0, (geo.size.width - totalWidth) / 2) : (geo.size.width - BookShelfMetrics.coverW) / 2)
                        .frame(height: BookShelfMetrics.coverH + 8)
                    }
                    .scrollDisabled(true)
                    .scrollIndicators(.hidden)
                    .onAppear { if !fits { proxy.scrollTo(current, anchor: .center) } }
                    .onChange(of: selectedId) { _, id in
                        guard !fits else { return }
                        withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: BookShelfMetrics.openDuration)) { proxy.scrollTo(id, anchor: .center) }
                    }
                }
            }
            .frame(height: BookShelfMetrics.coverH + 8)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 8).onChanged { v in
                let delta = v.translation.width - dragAccum
                guard abs(delta) >= 44 else { return }
                dragAccum = v.translation.width
                let books = shelf.books
                guard !books.isEmpty else { return }
                let cur = books.firstIndex { $0.id == current } ?? 0
                let next = min(max(cur + (delta < 0 ? 1 : -1), 0), books.count - 1)
                withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: BookShelfMetrics.openDuration)) { selectedId = books[next].id }
            }.onEnded { _ in dragAccum = 0 })
    }

    /// A moved-away selected book falls back to the first one on the shelf.
    private var current: String {
        if !selectedId.isEmpty, shelf.books.contains(where: { $0.id == selectedId }) { return selectedId }
        return shelf.books.first(where: { $0.id == focusId })?.id ?? shelf.books.first?.id ?? ""
    }
}

/// One book: p=0 closed spine facing the viewer, p=1 open (cover forward, spine face visible on the left).
private struct BookSlot: View {
    let book: Book
    let open: Bool
    let hidden: Bool
    let onSelect: () -> Void
    let onOpen: (CGRect) -> Void
    @Environment(BookStore.self) private var store

    var body: some View {
        BookSlotBody(book: book, progress: open ? 1 : 0)
            .opacity(hidden ? 0 : 1)
            .overlay {
                GeometryReader { geo in
                    Color.clear.contentShape(Rectangle()).onTapGesture {
                        if open { onOpen(geo.frame(in: .global)) } else { onSelect() }
                    }
                }
            }
            // Basılı tut → kitap raftan kalkar, başka bölümün üstüne bırakılır.
            .draggable(book.id) {
                BookCoverView(book: book, titleSize: 13)
                    .frame(width: BookShelfMetrics.coverW * 0.85, height: BookShelfMetrics.coverH * 0.85)
                    .rotationEffect(.degrees(-4))
                    .environment(store)
            }
            .accessibilityElement()
            .accessibilityLabel("\(book.title), \(book.author)")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { if open { onOpen(.zero) } else { onSelect() } }
    }
}

/// Animatable so the box really rotates frame by frame (the width changes with the angle).
private struct BookSlotBody: View, Animatable {
    let book: Book
    var progress: CGFloat

    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        let angle = 90 - (90 - BookShelfMetrics.openDeg) * progress
        let g = BookGeom.make(deg: angle)
        return ZStack(alignment: .topLeading) {
            BookSpine(book: book)
                .frame(width: BookShelfMetrics.spineW, height: BookShelfMetrics.coverH)
                .modifier(QuadProjection(quad: g.spine))
            if g.coverVisible {
                BookCoverView(book: book, titleSize: 13)
                    .frame(width: BookShelfMetrics.coverW, height: BookShelfMetrics.coverH)
                    .modifier(QuadProjection(quad: g.cover))
            }
        }
        .frame(width: g.width, height: BookShelfMetrics.coverH, alignment: .topLeading)
    }
}

/// Spine coloured with the cover's dominant colour (CoverPalette), text running top to bottom.
struct BookSpine: View {
    let book: Book
    @Environment(BookStore.self) private var books

    var body: some View {
        let bg = books.coverImage(book).flatMap(CoverPalette.dominant) ?? coverBaseColor(book)
        RoundedRectangle(cornerRadius: 1.5).fill(bg)
            .overlay(alignment: .top) {
                Text(book.title)
                    .font(RealFont.inter(12, .semibold)).lineLimit(1)
                    .foregroundStyle(bg.luminance > 0.55 ? Color(argb: 0xFF111111) : .white)
                    .frame(width: BookShelfMetrics.coverH - 24, alignment: .leading)
                    .rotationEffect(.degrees(90))
                    .frame(width: BookShelfMetrics.spineW, height: BookShelfMetrics.coverH - 24)
                    .padding(.top, 12)
            }
            .clipShape(RoundedRectangle(cornerRadius: 1.5))
    }
}

/// BookCover.kt: metadata-only books still get a deliberate, readable cover.
struct BookCoverView: View {
    let book: Book
    var titleSize: CGFloat = 18
    @Environment(BookStore.self) private var books

    var body: some View {
        let base = coverBaseColor(book)
        let small = titleSize < 16
        ZStack(alignment: .topLeading) {
            LinearGradient(colors: [base, base.opacity(0.65)], startPoint: .top, endPoint: .bottom)
            VStack(alignment: .leading) {
                Text(book.title).font(.system(size: titleSize, weight: .semibold, design: .serif))
                    .foregroundStyle(.white).lineLimit(6)
                Spacer(minLength: 4)
                Text(book.author).font(RealFont.inter(small ? 8 : 10)).foregroundStyle(.white.opacity(0.7)).lineLimit(2)
            }
            .padding(small ? 8 : 14)
            if let img = books.coverImage(book) {
                Image(uiImage: img).resizable().scaledToFill()
            }
            LinearGradient(colors: [.black.opacity(0.2), .clear], startPoint: .leading, endPoint: .trailing).frame(width: 5)
        }
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .shadow(color: .black.opacity(0.35), radius: 5, y: 3)
    }
}

/// CoverPalette.kt: most crowded of 512 colour buckets on a 48px thumbnail.
enum CoverPalette {
    private static var cache: [ObjectIdentifier: Color] = [:]

    static func dominant(_ image: UIImage) -> Color? {
        let key = ObjectIdentifier(image)
        if let c = cache[key] { return c }
        guard let cg = image.cgImage else { return nil }
        let w = 48, h = max(1, Int(48 * image.size.height / max(1, image.size.width)))
        var px = [UInt8](repeating: 0, count: w * h * 4)
        guard let ctx = CGContext(data: &px, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        var count = [Int](repeating: 0, count: 512), sr = [Int](repeating: 0, count: 512)
        var sg = [Int](repeating: 0, count: 512), sb = [Int](repeating: 0, count: 512)
        for i in stride(from: 0, to: px.count, by: 4) where px[i + 3] >= 128 {
            let r = Int(px[i]), g = Int(px[i + 1]), b = Int(px[i + 2])
            let k = (r >> 5) << 6 | (g >> 5) << 3 | (b >> 5)
            count[k] += 1; sr[k] += r; sg[k] += g; sb[k] += b
        }
        guard let best = count.indices.max(by: { count[$0] < count[$1] }), count[best] > 0 else { return nil }
        let n = Double(count[best])
        let color = Color(red: Double(sr[best]) / n / 255, green: Double(sg[best]) / n / 255, blue: Double(sb[best]) / n / 255)
        cache[key] = color
        return color
    }

    /// PosterEdgeGlow.kt: average colours of six regions (left / right edge × three bands) on a 24×36 thumbnail.
    static func edgeRegions(_ image: UIImage) -> [Color]? {
        guard let cg = image.cgImage else { return nil }
        let w = 24, h = 36
        var px = [UInt8](repeating: 0, count: w * h * 4)
        guard let ctx = CGContext(data: &px, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return (0..<6).map { region in
            let side = region % 2, band = region / 2
            var r = 0, g = 0, b = 0, n = 0
            for y in band * 12 ..< (band + 1) * 12 {
                for x in (side == 0 ? 0 : 16) ..< (side == 0 ? 8 : 24) {
                    let i = (y * w + x) * 4
                    r += Int(px[i]); g += Int(px[i + 1]); b += Int(px[i + 2]); n += 1
                }
            }
            return Color(red: Double(r) / Double(n) / 255, green: Double(g) / Double(n) / 255, blue: Double(b) / Double(n) / 255)
        }
    }
}

extension Color {
    var luminance: Double {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(self).getRed(&r, green: &g, blue: &b, alpha: &a)
        func lin(_ c: CGFloat) -> Double { let c = Double(c); return c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }
}

/// Small round reader chrome button (theme-tinted, or Liquid Glass on the shelf).
struct ReaderCircleButton<Label: View>: View {
    let theme: ReaderTheme
    var glass = false
    var size: CGFloat = 40
    let action: () -> Void
    @ViewBuilder let label: () -> Label

    var body: some View {
        Button(action: action) {
            Group {
                if glass {
                    label().foregroundStyle(.white).frame(width: size, height: size).liquidGlass(Circle(), role: .chrome).contentShape(Circle())
                } else {
                    label().foregroundStyle(theme.chromeText).frame(width: size, height: size).background(theme.chrome, in: Circle())
                }
            }
        }
        .buttonStyle(.plain)
    }
}

/// ActionGlyph.Theme: half-filled circle.
struct ThemeGlyph: View {
    var body: some View {
        ZStack {
            Circle().strokeBorder(.white, lineWidth: 1.6)
            Circle().trim(from: 0.25, to: 0.75).fill(.white).rotationEffect(.degrees(180)).padding(1)
        }
        .frame(width: 18, height: 18)
    }
}
