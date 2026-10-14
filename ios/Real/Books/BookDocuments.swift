import SwiftUI
import PDFKit
import Vision
import Compression
import UniformTypeIdentifiers

/// A readable paragraph (ParagraphKind on Android keeps the same heading/body split).
struct BookParagraph: Hashable {
    var text: String
    var heading: Bool = false
    /// Bold / italic runs from the PDF's fonts; empty when the paragraph is plain.
    var spans: [BookSpan] = []
}

/// BookDocumentRepository.kt: imports PDF / EPUB / TXT / images into the private library and serves page text.
final class BookDocuments {
    let store: BookStore
    private var textCache: [String: [BookParagraph]] = [:]
    private var epubCache: [String: EpubBook] = [:]

    init(store: BookStore) { self.store = store }

    static let importTypes: [UTType] = [.pdf, UTType(filenameExtension: "epub") ?? .data, .plainText, .image]

    // MARK: Import

    func importFile(_ url: URL) throws -> Book {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let ext = url.pathExtension.lowercased()
        let format: String
        switch ext {
        case "pdf": format = "pdf"
        case "epub": format = "epub"
        case "txt", "text", "md": format = "txt"
        case "jpg", "jpeg", "png", "heic", "webp", "gif", "tif", "tiff": format = "image"
        default: throw BookError.message("Bu dosya türü desteklenmiyor. PDF, EPUB, TXT veya görsel ekleyebilirsin.")
        }
        let id = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        let fileName = "\(id).\(ext.isEmpty ? format : ext)"
        let dest = store.directory.appendingPathComponent(fileName)
        try? FileManager.default.removeItem(at: dest)
        try FileManager.default.copyItem(at: url, to: dest)

        var book = Book(id: id, title: url.deletingPathExtension().lastPathComponent, author: "", fileName: fileName, format: format)
        var cover: UIImage?
        do {
            switch format {
            case "pdf":
                guard let doc = PDFDocument(url: dest) else { throw BookError.message("PDF açılamadı.") }
                book.pageCount = doc.pageCount
                let attrs = doc.documentAttributes ?? [:]
                if let t = attrs[PDFDocumentAttribute.titleAttribute] as? String, !t.trimmingCharacters(in: .whitespaces).isEmpty { book.title = t }
                if let a = attrs[PDFDocumentAttribute.authorAttribute] as? String { book.author = a }
                if let first = doc.page(at: 0) { cover = first.thumbnail(of: CGSize(width: 420, height: 630), for: .mediaBox) }
            case "epub":
                let epub = try EpubBook(url: dest)
                book.pageCount = max(1, epub.spine.count)
                if let t = epub.title { book.title = t }
                if let a = epub.author { book.author = a }
                book.description = epub.summary
                if let data = epub.coverData { cover = UIImage(data: data) }
                epubCache[id] = epub
            case "image":
                book.pageCount = 1
                cover = UIImage(contentsOfFile: dest.path)
            default:
                book.pageCount = nil
            }
        } catch {
            try? FileManager.default.removeItem(at: dest)
            throw error
        }
        if let cover, let png = cover.preparingThumbnail(of: CGSize(width: 420, height: 420 * cover.size.height / max(1, cover.size.width)))?.pngData() ?? cover.pngData() {
            try? png.write(to: store.directory.appendingPathComponent("\(id).cover.png"))
            book.coverUrl = "\(id).cover.png"
        }
        try store.add(book)
        return book
    }

    // MARK: Reading

    /// Paragraphs for a page (PDF/image), chapter (EPUB) or the whole text (TXT).
    func text(_ book: Book, page: Int) async -> [BookParagraph] {
        let key = "\(book.id)#\(page)"
        if let cached = textCache[key] { return cached }
        guard let url = store.file(book) else { return [] }
        let result: [BookParagraph]
        switch book.format {
        case "pdf":
            result = await Task.detached(priority: .userInitiated) { () -> [BookParagraph] in
                guard let doc = PDFDocument(url: url), let p = doc.page(at: page) else { return [] }
                // Copyable PDF: its own text, laid out from line positions and fonts (bold / italic kept).
                if let layered = BookPdfText.paragraphs(p) { return layered }
                // Scanned page (or a text layer that cannot be read): recognise text like the Android OCR fallback.
                let img = p.thumbnail(of: CGSize(width: 1400, height: 2000), for: .mediaBox)
                return Self.paragraphs(fromPdf: Self.ocr(img))
            }.value
        case "image":
            result = await Task.detached(priority: .userInitiated) { () -> [BookParagraph] in
                guard let img = UIImage(contentsOfFile: url.path) else { return [] }
                return Self.paragraphs(fromPdf: Self.ocr(img))
            }.value
        case "epub":
            let epub = epubCache[book.id] ?? (try? EpubBook(url: url))
            epubCache[book.id] = epub
            result = epub.map { e in Self.paragraphs(fromHtml: e.chapterHtml(page) ?? "") } ?? []
        default:
            result = await Task.detached(priority: .userInitiated) { () -> [BookParagraph] in
                guard let data = try? Data(contentsOf: url) else { return [] }
                let s = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .windowsCP1254) ?? String(decoding: data, as: UTF8.self)
                return Self.paragraphs(fromPlain: s)
            }.value
        }
        textCache[key] = result
        return result
    }

    func original(_ book: Book, page: Int) async -> UIImage? {
        guard let url = store.file(book) else { return nil }
        return await Task.detached(priority: .userInitiated) { () -> UIImage? in
            if book.format == "image" { return UIImage(contentsOfFile: url.path) }
            guard let doc = PDFDocument(url: url), let p = doc.page(at: page) else { return nil }
            let box = p.bounds(for: .mediaBox)
            let w: CGFloat = 1200
            return p.thumbnail(of: CGSize(width: w, height: w * box.height / max(1, box.width)), for: .mediaBox)
        }.value
    }

    // MARK: Text shaping

    static func ocr(_ image: UIImage) -> String {
        guard let cg = image.cgImage else { return "" }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = true
        request.automaticallyDetectsLanguage = true
        try? VNImageRequestHandler(cgImage: cg).perform([request])
        return (request.results ?? []).compactMap { $0.topCandidates(1).first?.string }.joined(separator: "\n")
    }

    static func paragraphs(fromPlain s: String) -> [BookParagraph] {
        let normalized = s.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
        let blocks = normalized.contains("\n\n") ? normalized.components(separatedBy: "\n\n") : normalized.components(separatedBy: "\n")
        return blocks.map { $0.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .map { BookParagraph(text: $0, heading: isHeadingLike($0)) }
    }

    /// PdfTextLayout: joins wrapped lines, repairs hyphenation and breaks paragraphs on short sentence-ending lines.
    static func paragraphs(fromPdf raw: String) -> [BookParagraph] {
        let lines = raw.replacingOccurrences(of: "\r", with: "\n").components(separatedBy: "\n").map { $0.trimmingCharacters(in: .whitespaces) }
        let longest = lines.map(\.count).max() ?? 0
        var out: [BookParagraph] = []
        var current = ""
        func flush() {
            let t = current.trimmingCharacters(in: .whitespaces)
            if !t.isEmpty { out.append(BookParagraph(text: t, heading: isHeadingLike(t))) }
            current = ""
        }
        for line in lines {
            if line.isEmpty { flush(); continue }
            if line.range(of: "^\\d{1,4}$", options: .regularExpression) != nil { continue } // page numbers
            if current.hasSuffix("-"), let last = current.dropLast().last, last.isLetter {
                current = String(current.dropLast()) + line
            } else {
                current += current.isEmpty ? line : " " + line
            }
            let ends = line.last.map { ".!?:;\"”’»…".contains($0) } ?? false
            if ends && Double(line.count) < Double(longest) * 0.78 { flush() }
            else if isHeadingLike(line) && Double(line.count) < Double(longest) * 0.6 && current == line { flush() }
        }
        flush()
        return out
    }

    static func isHeadingLike(_ t: String) -> Bool {
        guard t.count <= 60, t.count >= 2 else { return false }
        if t.last.map({ ".,;".contains($0) }) ?? false { return false }
        let letters = t.filter(\.isLetter)
        guard letters.count >= 2 else { return false }
        if letters == letters.uppercased(with: Locale(identifier: "tr_TR")) { return true }
        return t.range(of: "^(BÖLÜM|Bölüm|Chapter|CHAPTER|KISIM|Kısım)\\b", options: .regularExpression) != nil
    }

    static func paragraphs(fromHtml html: String) -> [BookParagraph] {
        var s = html
        if let body = s.range(of: "<body[^>]*>", options: [.regularExpression, .caseInsensitive]) { s = String(s[body.upperBound...]) }
        s = s.replacingOccurrences(of: "<(script|style)[^>]*>[\\s\\S]*?</\\1>", with: "", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: "<h[1-6][^>]*>", with: "\n\n\u{1}", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: "</(p|div|h[1-6]|li|blockquote|tr|section)>", with: "\n\n", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: "<br[^>]*>", with: "\n", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        s = decodeEntities(s)
        return s.components(separatedBy: "\n\n").compactMap { block in
            var t = block.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces)
            let heading = t.hasPrefix("\u{1}")
            t = t.replacingOccurrences(of: "\u{1}", with: "").trimmingCharacters(in: .whitespaces)
            return t.isEmpty ? nil : BookParagraph(text: t, heading: heading)
        }
    }

    static func decodeEntities(_ s: String) -> String {
        var r = s
        let named = ["&nbsp;": " ", "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": "\"", "&apos;": "'", "&#39;": "'",
                     "&mdash;": "—", "&ndash;": "–", "&hellip;": "…", "&lsquo;": "‘", "&rsquo;": "’", "&ldquo;": "“", "&rdquo;": "”",
                     "&laquo;": "«", "&raquo;": "»"]
        for (k, v) in named { r = r.replacingOccurrences(of: k, with: v) }
        guard let re = try? NSRegularExpression(pattern: "&#(x?)([0-9a-fA-F]+);") else { return r }
        let ns = r as NSString
        var out = ""
        var last = 0
        for m in re.matches(in: r, range: NSRange(location: 0, length: ns.length)) {
            out += ns.substring(with: NSRange(location: last, length: m.range.location - last))
            let hex = ns.substring(with: m.range(at: 1)) == "x"
            if let v = UInt32(ns.substring(with: m.range(at: 2)), radix: hex ? 16 : 10), let u = Unicode.Scalar(v) { out.unicodeScalars.append(u) }
            last = m.range.location + m.range.length
        }
        out += ns.substring(from: last)
        return out
    }
}

// MARK: EPUB

/// EpubChapters.kt: container.xml → OPF → spine; each spine item is one readable chapter.
struct EpubBook {
    let zip: ZipArchive
    let spine: [String]
    var title: String?
    var author: String?
    var summary: String?
    var coverData: Data?

    init(url: URL) throws {
        zip = try ZipArchive(url: url)
        guard let container = zip.string("META-INF/container.xml"),
              let opfPath = Self.firstMatch(container, "full-path=\"([^\"]+)\""),
              let opf = zip.string(opfPath) else { throw BookError.message("EPUB okunamadı.") }
        let base = (opfPath as NSString).deletingLastPathComponent
        func resolve(_ href: String) -> String {
            let clean = href.removingPercentEncoding ?? href
            let joined = base.isEmpty ? clean : base + "/" + clean
            var parts: [String] = []
            for p in joined.components(separatedBy: "/") { if p == ".." { _ = parts.popLast() } else if p != "." { parts.append(p) } }
            return parts.joined(separator: "/")
        }
        var manifest: [String: (href: String, type: String, props: String)] = [:]
        for item in Self.allMatches(opf, "<item\\b[^>]*>") {
            guard let id = Self.firstMatch(item, "\\bid=\"([^\"]+)\""), let href = Self.firstMatch(item, "\\bhref=\"([^\"]+)\"") else { continue }
            manifest[id] = (resolve(href), Self.firstMatch(item, "media-type=\"([^\"]+)\"") ?? "", Self.firstMatch(item, "properties=\"([^\"]+)\"") ?? "")
        }
        spine = Self.allMatches(opf, "<itemref\\b[^>]*>").compactMap { ref in
            guard let idref = Self.firstMatch(ref, "idref=\"([^\"]+)\"") else { return nil }
            return manifest[idref]?.href
        }
        title = Self.firstMatch(opf, "<dc:title[^>]*>([\\s\\S]*?)</dc:title>").map(BookDocuments.decodeEntities)
        author = Self.firstMatch(opf, "<dc:creator[^>]*>([\\s\\S]*?)</dc:creator>").map(BookDocuments.decodeEntities)
        summary = Self.firstMatch(opf, "<dc:description[^>]*>([\\s\\S]*?)</dc:description>")
            .map { BookDocuments.decodeEntities($0.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)) }
        let coverId = Self.firstMatch(opf, "<meta[^>]*name=\"cover\"[^>]*content=\"([^\"]+)\"")
        let coverHref = coverId.flatMap { manifest[$0]?.href }
            ?? manifest.values.first(where: { $0.props.contains("cover-image") })?.href
            ?? manifest.values.first(where: { $0.type.hasPrefix("image/") && $0.href.lowercased().contains("cover") })?.href
        coverData = coverHref.flatMap { zip.data($0) }
    }

    func chapterHtml(_ index: Int) -> String? {
        guard spine.indices.contains(index) else { return nil }
        return zip.string(spine[index])
    }

    static func firstMatch(_ s: String, _ pattern: String) -> String? {
        guard let re = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
              let m = re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)), m.numberOfRanges > 1,
              let r = Range(m.range(at: 1), in: s) else { return nil }
        return String(s[r]).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func allMatches(_ s: String, _ pattern: String) -> [String] {
        guard let re = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return [] }
        return re.matches(in: s, range: NSRange(s.startIndex..., in: s)).compactMap { Range($0.range, in: s).map { String(s[$0]) } }
    }
}

/// Minimal ZIP reader (stored + deflate) for EPUB containers; Apple's COMPRESSION_ZLIB is raw DEFLATE.
struct ZipArchive {
    private let data: Data
    private var entries: [String: (offset: Int, method: UInt16, compressed: Int, size: Int)] = [:]

    init(url: URL) throws {
        data = try Data(contentsOf: url, options: .mappedIfSafe)
        let bytes = [UInt8](data)
        guard bytes.count > 22 else { throw BookError.message("EPUB bozuk.") }
        var eocd = -1
        var i = bytes.count - 22
        while i >= max(0, bytes.count - 66_000) {
            if bytes[i] == 0x50, bytes[i + 1] == 0x4b, bytes[i + 2] == 0x05, bytes[i + 3] == 0x06 { eocd = i; break }
            i -= 1
        }
        guard eocd >= 0 else { throw BookError.message("EPUB bozuk.") }
        func u16(_ o: Int) -> Int { Int(bytes[o]) | Int(bytes[o + 1]) << 8 }
        func u32(_ o: Int) -> Int { u16(o) | u16(o + 2) << 16 }
        let count = u16(eocd + 10)
        var p = u32(eocd + 16)
        for _ in 0..<count {
            guard p + 46 <= bytes.count, u32(p) == 0x02014b50 else { break }
            let method = UInt16(u16(p + 10))
            let compressed = u32(p + 20), size = u32(p + 24)
            let nameLen = u16(p + 28), extraLen = u16(p + 30), commentLen = u16(p + 32)
            let local = u32(p + 42)
            let name = String(decoding: bytes[(p + 46)..<(p + 46 + nameLen)], as: UTF8.self)
            if local + 30 <= bytes.count {
                let dataStart = local + 30 + u16(local + 26) + u16(local + 28)
                entries[name] = (dataStart, method, compressed, size)
            }
            p += 46 + nameLen + extraLen + commentLen
        }
    }

    func data(_ name: String) -> Data? {
        guard let e = entries[name] ?? entries.first(where: { $0.key.caseInsensitiveCompare(name) == .orderedSame })?.value,
              e.offset + e.compressed <= data.count else { return nil }
        let slice = data.subdata(in: e.offset..<(e.offset + e.compressed))
        if e.method == 0 { return slice }
        guard e.method == 8, e.size > 0 else { return e.size == 0 ? Data() : nil }
        var out = Data(count: e.size)
        let written = out.withUnsafeMutableBytes { dst in
            slice.withUnsafeBytes { src in
                compression_decode_buffer(dst.bindMemory(to: UInt8.self).baseAddress!, e.size,
                                          src.bindMemory(to: UInt8.self).baseAddress!, slice.count, nil, COMPRESSION_ZLIB)
            }
        }
        return written > 0 ? out.prefix(written) : nil
    }

    func string(_ name: String) -> String? {
        data(name).map { String(decoding: $0, as: UTF8.self) }
    }
}
