import SwiftUI
import Observation

/// BookModels.kt: one locally stored book (catalog fields kept for parity with Android).
struct Book: Codable, Identifiable, Hashable {
    var id: String
    var title: String
    var author: String
    var category: String = "Genel"
    var pageCount: Int?
    var coverUrl: String?
    var description: String?
    var fileName: String?
    var format: String?          // pdf · epub · txt · image
    var rights: String?
    var infoUrl: String?
    var licenseUrl: String?
}

struct Shelf: Identifiable {
    var name: String
    var books: [Book]
    var plankColor: Color
    var id: String { name }
}

/// ReaderTheme: AÇIK, soft KOYU and AMOLED (pure black).
enum ReaderTheme: String, CaseIterable, Codable {
    case LIGHT, DARK, AMOLED

    var label: String { switch self { case .LIGHT: t("Açık"); case .DARK: t("Koyu"); case .AMOLED: "AMOLED" } }
    var background: Color { switch self { case .LIGHT: Color(argb: 0xFFEDEDEB); case .DARK: Color(argb: 0xFF1C1C1E); case .AMOLED: .black } }
    var text: Color { switch self { case .LIGHT: Color(argb: 0xFF111111); case .DARK: Color(argb: 0xFFDADADC); case .AMOLED: Color(argb: 0xFFCFCFCF) } }
    var muted: Color { switch self { case .AMOLED: Color(argb: 0xFF6E6E73); default: Color(argb: 0xFF8E8E93) } }
    var chrome: Color { switch self { case .LIGHT: .black; case .DARK: Color(argb: 0xFF3A3A3C); case .AMOLED: Color(argb: 0xFF1A1A1A) } }
    var chromeText: Color { switch self { case .DARK: Color(argb: 0xFFF2F2F7); default: .white } }
    var colorScheme: ColorScheme { self == .LIGHT ? .light : .dark }
    var next: ReaderTheme { let all = Self.allCases; return all[(all.firstIndex(of: self)! + 1) % all.count] }
}

enum ReadMode: String { case VERTICAL, HORIZONTAL }

/// ReaderFont.kt: reader typeface, chosen from the "Tт" row and used in every book.
enum ReaderFont: String, CaseIterable {
    case SANS, SERIF, MONO

    var label: String { switch self { case .SANS: "Inter"; case .SERIF: "Serif"; case .MONO: "Mono" } }

    func font(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        switch self {
        case .SANS: RealFont.inter(size, weight)
        case .SERIF: .system(size: size, weight: weight, design: .serif)
        case .MONO: .system(size: size, weight: weight, design: .monospaced)
        }
    }
}

enum ReaderDefaults {
    static let fontSize = 16
    static let minSize = 12
    static let maxSize = 28
    static let lineHeight: CGFloat = 1.5
}

/// Kapak görseli yokken kapağın ve sırtın ortak zemin rengi (kitap kimliğinden türetilir).
func coverBaseColor(_ book: Book) -> Color {
    let colors: [UInt32] = [0xFF394E5A, 0xFF765846, 0xFF4D6152, 0xFF695367]
    var h: UInt32 = 0
    for u in book.id.unicodeScalars { h = h &* 31 &+ u.value }
    return Color(argb: colors[Int(h & 0x7fffffff) % colors.count])
}

/// BookShelfStore.kt: private local library. Books are files under Application Support/books, metadata in books.json.
@Observable
final class BookStore {
    private(set) var books: [Book] = []
    var theme: ReaderTheme { didSet { defaults.set(theme.rawValue, forKey: "books_theme") } }
    var fontSize: Int { didSet { defaults.set(fontSize, forKey: "books_font") } }
    var readMode: ReadMode { didSet { defaults.set(readMode.rawValue, forKey: "books_read_mode") } }
    var readerFont: ReaderFont { didSet { defaults.set(readerFont.rawValue, forKey: "books_reader_font") } }
    /// Sections the person opened; a section stays on the shelf even while it holds no book.
    private(set) var sections: [String] { didSet { defaults.set(sections, forKey: "books_sections") } }
    /// Books with a page read by OCR this session (scans, or a text layer too broken to use).
    private(set) var scannedBooks: Set<String> = []
    private(set) var lastReadId: String?

    @ObservationIgnored let directory: URL
    @ObservationIgnored private let persist: Bool
    @ObservationIgnored private let defaults = UserDefaults.standard

    init(persist: Bool = true) {
        self.persist = persist
        directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("books", isDirectory: true)
        theme = ReaderTheme(rawValue: defaults.string(forKey: "books_theme") ?? "") ?? .AMOLED
        let f = defaults.integer(forKey: "books_font")
        fontSize = f == 0 ? ReaderDefaults.fontSize : min(max(f, ReaderDefaults.minSize), ReaderDefaults.maxSize)
        readMode = ReadMode(rawValue: defaults.string(forKey: "books_read_mode") ?? "") ?? .VERTICAL
        readerFont = ReaderFont(rawValue: defaults.string(forKey: "books_reader_font") ?? "") ?? .SANS
        sections = defaults.stringArray(forKey: "books_sections") ?? []
        lastReadId = defaults.string(forKey: "books_last_read")
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        if persist, let data = try? Data(contentsOf: directory.appendingPathComponent("books.json")),
           let list = try? JSONDecoder().decode([Book].self, from: data) {
            books = list
        }
    }

    private func save() {
        guard persist, let data = try? JSONEncoder().encode(books) else { return }
        try? data.write(to: directory.appendingPathComponent("books.json"), options: .atomic)
    }

    var shelves: [Shelf] {
        let colors: [UInt32] = [0xFFF0A24A, 0xFF48A6EE, 0xFF6FBF8B, 0xFFB587E0]
        var order: [String] = []
        var groups: [String: [Book]] = [:]
        for b in books {
            let key = b.category.trimmingCharacters(in: .whitespaces).isEmpty ? "Genel" : b.category
            if groups[key] == nil { order.append(key) }
            groups[key, default: []].append(b)
        }
        for s in sections where groups[s] == nil { order.append(s) }
        return order.enumerated().map { i, name in Shelf(name: name, books: groups[name] ?? [], plankColor: Color(argb: colors[i % colors.count])) }
    }

    static let defaultSection = "Dosyalarım"

    private func sectionNames() -> [String] {
        books.map { $0.category.trimmingCharacters(in: .whitespaces).isEmpty ? "Genel" : $0.category } + sections
    }

    /// Adds an empty section; nil when the name is empty, taken, or there are already 30.
    @discardableResult
    func addSection(_ raw: String) -> String? {
        let name = String(raw.trimmingCharacters(in: .whitespacesAndNewlines).prefix(40))
        guard !name.isEmpty, sections.count < 30,
              !sectionNames().contains(where: { $0.caseInsensitiveCompare(name) == .orderedSame }) else { return nil }
        sections.append(name)
        return name
    }

    func renameSection(_ old: String, to raw: String) {
        let name = String(raw.trimmingCharacters(in: .whitespacesAndNewlines).prefix(40))
        guard !name.isEmpty, name != old,
              !sectionNames().contains(where: { $0 != old && $0.caseInsensitiveCompare(name) == .orderedSame }) else { return }
        for i in books.indices where books[i].category == old { books[i].category = name }
        if let i = sections.firstIndex(of: old) { sections[i] = name }
        save()
    }

    /// The section goes; its books move to Dosyalarım.
    func deleteSection(_ name: String) {
        guard name != Self.defaultSection else { return }
        for i in books.indices where books[i].category == name { books[i].category = Self.defaultSection }
        sections.removeAll { $0 == name }
        save()
    }

    /// The section it leaves stays on the shelf (as an empty one) so a drag never deletes what the person made.
    func moveBook(_ id: String, to section: String) {
        guard let i = books.firstIndex(where: { $0.id == id }), books[i].category != section else { return }
        let old = books[i].category
        books[i].category = section
        if !books.contains(where: { $0.category == old }), !sections.contains(old) { sections.append(old) }
        save()
    }

    func noteScanned(_ id: String) { if !scannedBooks.contains(id) { scannedBooks.insert(id) } }
    func scanNoticeShown(_ book: Book) -> Bool { defaults.bool(forKey: "books_scan_notice_\(book.id)") }
    func markScanNoticeShown(_ book: Book) { defaults.set(true, forKey: "books_scan_notice_\(book.id)") }

    func add(_ book: Book) throws {
        guard AppUpdateGate.writable() else { throw BookError.message(AppUpdateGate.blockedMessage) }
        guard !books.contains(where: { $0.id == book.id }) else { return }
        guard books.count < 200 else { throw BookError.message(t("Kitaplık sınırına ulaşıldı (200 kitap).")) }
        books.append(book)
        save()
    }

    func rename(_ id: String, to title: String) {
        let clean = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, let i = books.firstIndex(where: { $0.id == id }) else { return }
        books[i].title = clean
        save()
    }

    func remove(_ id: String) {
        guard let book = books.first(where: { $0.id == id }) else { return }
        books.removeAll { $0.id == id }
        save()
        if let f = file(book) { try? FileManager.default.removeItem(at: f) }
        try? FileManager.default.removeItem(at: directory.appendingPathComponent("\(id).cover.png"))
        for key in ["page_", "offset_", "scan_notice_"] { defaults.removeObject(forKey: "books_\(key)\(id)") }
        if lastReadId == id { lastReadId = nil; defaults.removeObject(forKey: "books_last_read") }
    }

    func file(_ book: Book) -> URL? {
        guard let name = book.fileName, name.range(of: "^[a-zA-Z0-9_.-]+$", options: .regularExpression) != nil else { return nil }
        return directory.appendingPathComponent(name)
    }

    func hasLocalContent(_ book: Book) -> Bool {
        guard let f = file(book) else { return false }
        return FileManager.default.fileExists(atPath: f.path)
    }

    @ObservationIgnored private let covers = NSCache<NSString, UIImage>()

    func coverImage(_ book: Book) -> UIImage? {
        guard book.coverUrl != nil else { return nil }
        if let c = covers.object(forKey: book.id as NSString) { return c }
        guard let img = UIImage(contentsOfFile: directory.appendingPathComponent("\(book.id).cover.png").path) else { return nil }
        covers.setObject(img, forKey: book.id as NSString)
        return img
    }

    func page(_ book: Book) -> Int { max(0, defaults.integer(forKey: "books_page_\(book.id)")) }
    func offset(_ book: Book) -> Int { max(0, defaults.integer(forKey: "books_offset_\(book.id)")) }

    func savePosition(_ book: Book, page: Int, offset: Int = 0) {
        guard books.contains(where: { $0.id == book.id }) else { return }
        defaults.set(page, forKey: "books_page_\(book.id)")
        defaults.set(offset, forKey: "books_offset_\(book.id)")
        defaults.set(book.id, forKey: "books_last_read")
        lastReadId = book.id
    }

    /// Yaşam row: the most recently read book and its percentage.
    var lastRead: (book: Book, percent: Int?)? {
        guard let id = lastReadId, let b = books.first(where: { $0.id == id }) else { return nil }
        guard let count = b.pageCount, count > 1 else { return (b, nil) }
        return (b, min(100, Int((Double(page(b) + 1) / Double(count) * 100).rounded())))
    }
}

enum BookError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let m) = self { return m }; return nil }
}
