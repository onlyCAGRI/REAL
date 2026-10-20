import SwiftUI

/// Yaşam "Kitaplar" widget card (BooksWidgetCard).
struct BooksWidgetCard: View {
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 10) {
                Text("Kitaplar").font(.system(size: 26, weight: .medium, design: .serif)).foregroundStyle(.white)
                Text("Kendi rafın, kendi okuma alanın").font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.6))
            }
            Spacer()
            HStack(spacing: 4) {
                ForEach(Array([0xFFAD774F, 0xFF6A8591, 0xFF788163].enumerated()), id: \.offset) { i, c in
                    RoundedRectangle(cornerRadius: 3).fill(Color(argb: UInt32(c))).frame(width: 20, height: CGFloat(70 + i * 8))
                }
            }
        }
    }
}

private enum BookPage { case shelf, detail, reader }

/// The cover flying between its shelf slot and the detail position.
private struct BookFlight: Equatable {
    var book: Book
    var from: CGRect
    var reverse: Bool
}

/// BooksModuleScreen.kt: shelf → (cover flight) → detail → reader.
struct BooksModuleScreen: View {
    var resumeBookId: String?
    @Environment(BookStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    @State private var documents: BookDocuments?
    @State private var page: BookPage = .shelf
    @State private var selected: Book?
    @State private var flight: BookFlight?
    @State private var flightProgress: CGFloat = 0
    @State private var lastShelfFrame: [String: CGRect] = [:]
    @State private var revealDetail = false
    @State private var importing = false
    @State private var busy = false
    @State private var error: String?
    @State private var editingTitle = false
    @State private var draftTitle = ""
    @State private var didResume = false
    /// The detail panel is sliding away before the cover flies back (Android: leaving).
    @State private var leaving = false

    var body: some View {
        GeometryReader { geo in
            let target = detailCoverRect(geo)
            ZStack {
                store.theme.background.ignoresSafeArea()
                switch page {
                case .shelf:
                    BooksShelfScreen(shelves: store.shelves, theme: store.theme, hiddenId: flight?.book.id,
                                     focusId: flight?.reverse == true ? flight?.book.id : nil,
                                     onBookTap: { b, frame in startFlight(b, frame, geo: geo, target: target) },
                                     onAdd: { if !busy { error = nil; importing = true } },
                                     onBack: back,
                                     onTheme: { store.theme = store.theme.next })
                    .transition(.identity)
                case .detail:
                    if let book = selected { detail(book, target: target, geo: geo) }
                case .reader:
                    if let book = selected, let documents {
                        BookReaderScreen(book: book, documents: documents, onBack: back).transition(.opacity)
                    }
                }
                if let f = flight { flightLayer(f, target: target, geo: geo) }
                if let error { errorToast(error) }
                if busy {
                    Text("Hazırlanıyor…").font(RealFont.inter(15)).foregroundStyle(store.theme.text)
                        .padding(28).background(store.theme.chrome, in: RoundedRectangle(cornerRadius: 24))
                }
                if editingTitle, let book = selected { renameDialog(book) }
            }
        }
        .preferredColorScheme(store.theme.colorScheme)
        .statusBarHidden(false)
        .edgeSwipeBack(enabled: !editingTitle) { back() }
        .onAppear {
            if documents == nil { documents = BookDocuments(store: store) }
            resumeIfNeeded()
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: BookDocuments.importTypes) { result in
            guard case .success(let url) = result, let documents else { return }
            busy = true
            DispatchQueue.main.async {
                do {
                    let book = try documents.importFile(url)
                    selected = book
                    revealDetail = false
                    page = .detail
                } catch let e {
                    error = String((e.localizedDescription).prefix(180))
                }
                busy = false
            }
        }
    }

    // MARK: Navigation

    private func back() {
        // As on Android: no back step while a cover is flying or a file is being prepared (it left a half state).
        guard flight == nil, !busy, !leaving else { return }
        error = nil
        switch page {
        case .shelf:
            dismiss()
        case .detail:
            guard let b = selected else { page = .shelf; return }
            withAnimation(.easeIn(duration: 0.18)) { revealDetail = false }
            leaving = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                leaving = false
                if let from = lastShelfFrame[b.id], from != .zero {
                    flight = BookFlight(book: b, from: from, reverse: true)
                    flightProgress = 1
                    page = .shelf
                    selected = nil
                    withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.46)) { flightProgress = 0 } completion: { flight = nil }
                } else {
                    selected = nil
                    page = .shelf
                }
            }
        case .reader:
            withAnimation(.easeOut(duration: 0.2)) { page = .detail }
            revealDetail = true
        }
    }

    private func startFlight(_ book: Book, _ frame: CGRect, geo: GeometryProxy, target: CGRect) {
        guard flight == nil else { return }
        let origin = geo.frame(in: .global).origin
        let local = frame.offsetBy(dx: -origin.x, dy: -origin.y)
        guard frame != .zero else { open(book); return }
        // The tapped slot includes the spine face; the cover occupies its right part.
        let coverFrame = CGRect(x: local.maxX - BookShelfMetrics.coverW * 0.92, y: local.minY, width: BookShelfMetrics.coverW * 0.92, height: local.height)
        lastShelfFrame[book.id] = coverFrame
        flight = BookFlight(book: book, from: coverFrame, reverse: false)
        flightProgress = 0
        withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.52)) { flightProgress = 1 } completion: {
            open(book)
            flight = nil
        }
    }

    private func open(_ book: Book) {
        selected = book
        revealDetail = false
        page = .detail
        error = nil
    }

    private func read(_ book: Book) {
        guard store.hasLocalContent(book) else { importing = true; return }
        withAnimation(.easeOut(duration: 0.22)) { page = .reader }
    }

    private func resumeIfNeeded() {
        guard !didResume, let id = resumeBookId, let book = store.books.first(where: { $0.id == id }) else { return }
        didResume = true
        selected = book
        page = store.hasLocalContent(book) ? .reader : .detail
        revealDetail = true
    }

    // MARK: Detail

    /// detailCoverRect: fills the space between the header and the ~262pt bottom box, 2:3, at most 480pt tall.
    private func detailCoverRect(_ geo: GeometryProxy) -> CGRect {
        let top0: CGFloat = 84
        let availH = geo.size.height - 262 - top0
        let byWidth = (geo.size.width - 64) * 1.5
        let h = max(220, min(availH, byWidth, 480))
        let w = h / 1.5
        let top = top0 + max(0, availH - h) / 2
        return CGRect(x: (geo.size.width - w) / 2, y: top, width: w, height: h)
    }

    private func detail(_ book: Book, target: CGRect, geo: GeometryProxy) -> some View {
        let theme = store.theme
        let canRead = store.hasLocalContent(book)
        return ScrollView {
            VStack(spacing: 0) {
                ZStack(alignment: .top) {
                    HStack(spacing: 14) {
                        ReaderCircleButton(theme: theme, glass: true, action: back) {
                            RealSymbol("chevron.left", size: 20.7)
                        }
                        Text("Kitap").font(.system(size: 26, weight: .medium, design: .serif)).foregroundStyle(theme.text)
                        Spacer()
                    }
                    .padding(.horizontal, 24).padding(.top, 8)

                    BookCoverView(book: book, titleSize: 18)
                        .frame(width: target.width, height: target.height)
                        .position(x: target.midX, y: target.midY)

                    VStack(spacing: 0) {
                        Spacer()
                        VStack(spacing: 0) {
                            Text(book.title).font(.system(size: 24, design: .serif)).foregroundStyle(theme.chromeText)
                                .multilineTextAlignment(.center).lineLimit(3)
                                .onTapGesture { draftTitle = book.title; editingTitle = true }
                            if !book.author.isEmpty {
                                Text(book.author).font(RealFont.inter(14)).foregroundStyle(theme.chromeText.opacity(0.6)).lineLimit(1).padding(.top, 8)
                            }
                            Button { draftTitle = book.title; editingTitle = true } label: {
                                Text("Adı düzenle").font(RealFont.inter(13)).foregroundStyle(theme.chromeText.opacity(0.55))
                                    .padding(.horizontal, 12).padding(.vertical, 4)
                            }
                            .buttonStyle(.plain).padding(.top, 10)
                            HStack(spacing: 10) {
                                PanelButton(text: canRead ? "Oku" : "Dosyadan ekle", primary: true, theme: theme) { read(book) }
                                PanelButton(text: "Kaldır", primary: false, theme: theme) {
                                    store.remove(book.id)
                                    selected = nil
                                    page = .shelf
                                }
                            }
                            .padding(.top, 10)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(20)
                        .background(theme.chrome, in: RoundedRectangle(cornerRadius: 28))
                        .padding(.horizontal, 24).padding(.bottom, 20)
                        .opacity(revealDetail ? 1 : 0)
                        .offset(y: revealDetail ? 0 : 60)
                    }
                }
                .frame(height: geo.size.height)

                VStack(alignment: .leading, spacing: 12) {
                    if let r = book.rights, !r.isEmpty { Text(r).font(RealFont.inter(12)).foregroundStyle(theme.muted) }
                    if let d = book.description, !d.isEmpty { Text(d).font(RealFont.inter(15)).foregroundStyle(theme.text) }
                    if !canRead {
                        Text("Bu kitap için cihazda tam dosya bulunamadı. Kendi PDF, EPUB veya TXT dosyanı ekleyebilirsin. Mevcut kitap bilgileri korunuyor.")
                            .font(RealFont.inter(13)).foregroundStyle(theme.muted)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24).padding(.top, 24).padding(.bottom, 120)
                .opacity(revealDetail ? 1 : 0)
            }
        }
        .scrollIndicators(.hidden)
        .onAppear { withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.3)) { revealDetail = true } }
    }

    private func flightLayer(_ f: BookFlight, target: CGRect, geo: GeometryProxy) -> some View {
        let p = flightProgress
        let from = f.from
        let scale = (from.width / target.width) + (1 - from.width / target.width) * p
        let cx = from.midX + (target.midX - from.midX) * p
        let cy = from.midY + (target.midY - from.midY) * p
        return BookCoverView(book: f.book, titleSize: 18)
            .frame(width: target.width, height: target.height)
            .shadow(color: .black.opacity(0.45 * p), radius: 22 * p, y: 10 * p)
            .scaleEffect(scale)
            .position(x: cx, y: cy)
            .allowsHitTesting(false)
    }

    private func renameDialog(_ book: Book) -> some View {
        let theme = store.theme
        return ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture { editingTitle = false }
            VStack(alignment: .leading, spacing: 0) {
                Text("Kitap adı").font(RealFont.inter(13)).foregroundStyle(theme.chromeText.opacity(0.6))
                TextField("", text: $draftTitle, axis: .vertical)
                    .font(.system(size: 18, design: .serif)).foregroundStyle(theme.chromeText).lineLimit(1...3)
                    .submitLabel(.done)
                    .onSubmit { saveTitle(book) }
                    .padding(.horizontal, 16).padding(.vertical, 14)
                    .background(theme.chromeText.opacity(0.1), in: RoundedRectangle(cornerRadius: 16))
                    .padding(.top, 10)
                HStack(spacing: 10) {
                    PanelButton(text: "Vazgeç", primary: false, theme: theme) { editingTitle = false }
                    PanelButton(text: "Kaydet", primary: true, theme: theme) { saveTitle(book) }
                }
                .padding(.top, 16)
            }
            .padding(20)
            .background(theme.chrome, in: RoundedRectangle(cornerRadius: 28))
            .padding(.horizontal, 28)
        }
    }

    private func saveTitle(_ book: Book) {
        store.rename(book.id, to: draftTitle)
        selected = store.books.first { $0.id == book.id }
        editingTitle = false
    }

    private func errorToast(_ message: String) -> some View {
        VStack {
            Spacer()
            VStack(alignment: .leading, spacing: 0) {
                Text(message).font(RealFont.inter(12)).foregroundStyle(store.theme.chromeText)
                Button("Kapat") { error = nil }.font(RealFont.inter(12)).foregroundStyle(store.theme.chromeText).padding(.vertical, 12)
            }
            .padding(.horizontal, 16).padding(.vertical, 10)
            .background(store.theme.chrome, in: RoundedRectangle(cornerRadius: 18))
            .padding(16)
        }
    }
}

struct PanelButton: View {
    let text: String
    let primary: Bool
    let theme: ReaderTheme
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text).font(RealFont.inter(15, .semibold))
                .foregroundStyle(primary ? theme.chrome : theme.chromeText)
                .frame(maxWidth: .infinity).frame(height: 46)
                .background(primary ? theme.chromeText : theme.chromeText.opacity(0.14), in: Capsule())
        }
        .buttonStyle(.plain)
    }
}
