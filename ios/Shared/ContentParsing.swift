import Foundation

/// Shared by the app and the share extension (IncomingContent.kt, SnippetSourceDetector.kt, SharedLinkResolver.kt).

/// IncomingContentParser.smart: a link inside other words ("Title https://…") becomes the link and the words the title;
/// several links or a long text stay a text snippet.
struct ParsedText: Equatable {
    var url: String?
    var title: String?
    var snippet: String?
}

enum SmartText {
    private static let urlPattern = try! NSRegularExpression(pattern: "https?://[^\\s<>\"]+", options: [.caseInsensitive])

    static func isWebLink(_ s: String) -> Bool {
        guard !s.contains(where: \.isWhitespace), let u = URL(string: s), let scheme = u.scheme?.lowercased(),
              ["http", "https"].contains(scheme), !(u.host ?? "").isEmpty else { return false }
        return true
    }

    static func parse(_ raw: String?, subject: String? = nil) -> ParsedText {
        let value = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanSubject = subject?.split(whereSeparator: \.isWhitespace).joined(separator: " ").nilIfBlank.map { String($0.prefix(200)) }
        if value.isEmpty { return ParsedText(title: cleanSubject) }
        if isWebLink(value) { return ParsedText(url: value, title: cleanSubject) }
        let ns = value as NSString
        let links = Array(Set(urlPattern.matches(in: value, range: NSRange(location: 0, length: ns.length)).map {
            ns.substring(with: $0.range).trimmingCharacters(in: CharacterSet(charactersIn: ".,)!?;:»\"'"))
        }))
        if links.count == 1, isWebLink(links[0]) {
            let link = links[0]
            let rest = value.replacingOccurrences(of: link, with: " ")
                .trimmingCharacters(in: CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: ":-|–—•.,;")))
            let words = rest.split(whereSeparator: \.isWhitespace).joined(separator: " ")
            if words.isEmpty { return ParsedText(url: link, title: cleanSubject) }
            if words.count <= 200, rest.split(separator: "\n").filter({ !$0.trimmingCharacters(in: .whitespaces).isEmpty }).count <= 3 {
                return ParsedText(url: link, title: cleanSubject ?? words)
            }
        }
        let firstLine = value.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.first { !$0.isEmpty } ?? ""
        return ParsedText(title: cleanSubject ?? String(firstLine.prefix(120)), snippet: String(value.prefix(100_000)))
    }
}

/// SnippetSourceDetector.kt: WhatsApp / e-mail from the sending app, then from the text's shape.
enum SnippetSourceDetector {
    static let whatsapp = "WHATSAPP_TEXT", email = "EMAIL_TEXT", plain = "PLAIN_TEXT"
    private static let whatsappCopy = try! NSRegularExpression(pattern: #"^\[\d{1,2}[./]\d{1,2}[./]\d{2,4},? \d{1,2}[:.]\d{2}(?:[:.]\d{2})?(?:\s?[AaPp][Mm])?\] [^:\n]{1,60}: "#, options: [.anchorsMatchLines])
    private static let whatsappExport = try! NSRegularExpression(pattern: #"^\d{1,2}[./]\d{1,2}[./]\d{2,4},? \d{1,2}[:.]\d{2}(?:\s?[AaPp][Mm])? - [^:\n]{1,60}: "#, options: [.anchorsMatchLines])
    private static let mailHeader = try! NSRegularExpression(pattern: #"^(From|Kimden|Gönderen|De|Von):\s.+$"#, options: [.anchorsMatchLines, .caseInsensitive])
    private static let mailSubject = try! NSRegularExpression(pattern: #"^(Subject|Konu|Objet|Betreff|Sent|Gönderildi|Tarih|Date):\s.+$"#, options: [.anchorsMatchLines, .caseInsensitive])

    static func detect(app: String? = nil, text: String?) -> String {
        if let app = app?.lowercased() {
            if app.contains("whatsapp") { return whatsapp }
            if ["com.google.gmail", "com.apple.mobilemail", "com.microsoft.office.outlook", "ch.protonmail", "com.readdle.smartemail"].contains(where: app.hasPrefix) { return email }
        }
        let value = text ?? ""
        let range = NSRange(location: 0, length: (value as NSString).length)
        if whatsappCopy.firstMatch(in: value, range: range) != nil || whatsappExport.firstMatch(in: value, range: range) != nil { return whatsapp }
        if mailHeader.firstMatch(in: value, range: range) != nil && mailSubject.firstMatch(in: value, range: range) != nil { return email }
        return plain
    }

    static func label(_ source: String?) -> String {
        switch source { case whatsapp: return "WhatsApp"; case email: return "E-posta"; default: return "Not" }
    }
}

/// SharedLinkResolver.kt: what a shared / pasted link is. Apple Music → iTunes lookup; YouTube → oEmbed; Spotify and
/// everything else → the page's Open Graph tags (Spotify also gives its own 30 s preview, og:audio).
struct ResolvedLink {
    var title: String?
    var imageUrl: String?
    var music: MusicBlock?
}

enum LinkResolver {
    static func resolve(_ raw: String) async -> ResolvedLink {
        guard let uri = URLComponents(string: raw), let hostRaw = uri.host?.lowercased() else { return ResolvedLink() }
        let host = hostRaw.hasPrefix("www.") ? String(hostRaw.dropFirst(4)) : hostRaw
        if host == "music.apple.com" || host == "itunes.apple.com" {
            let id = uri.queryItems?.first { $0.name == "i" }?.value
                ?? raw.range(of: #"/song/[^/]+/(\d+)"#, options: .regularExpression).map { String(raw[$0]).components(separatedBy: "/").last ?? "" }
            if let id, !id.isEmpty, id.allSatisfy(\.isNumber), let m = await itunes(id, link: raw) { return ResolvedLink(title: m.title, imageUrl: m.artworkUrl, music: m) }
        }
        if ["youtube.com", "m.youtube.com", "youtu.be", "music.youtube.com"].contains(host) {
            guard let o = await json("https://www.youtube.com/oembed?format=json&url=" + (raw.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "")) else { return ResolvedLink() }
            let title = (o["title"] as? String)?.nilIfBlank
            let image = (o["thumbnail_url"] as? String).flatMap { $0.hasPrefix("https://") ? $0 : nil }
            var music: MusicBlock?
            if host == "music.youtube.com", let title {
                let artist = ((o["author_name"] as? String) ?? "").replacingOccurrences(of: " - Topic", with: "")
                music = MusicBlock(title: String(title.prefix(300)), artist: String(artist.prefix(200)), artworkUrl: image, provider: "YouTube Music",
                                   providerTrackId: uri.queryItems?.first { $0.name == "v" }?.value, providerUrl: raw)
            }
            return ResolvedLink(title: title, imageUrl: image, music: music)
        }
        guard let page = await fetchPage(raw) else { return ResolvedLink() }
        let tags = metaTags(page.html)
        let title = (tags["og:title"] ?? tags["twitter:title"] ?? titleTag(page.html)).map(decodeEntities)?.trimmingCharacters(in: .whitespaces)
        let image = (tags["og:image"] ?? tags["twitter:image"]).map(decodeEntities).flatMap { $0.hasPrefix("https://") ? $0 : nil }
        if let final = URLComponents(string: page.finalUrl), final.host?.lowercased() == "open.spotify.com",
           let r = final.path.range(of: #"/track/([A-Za-z0-9]{22})"#, options: .regularExpression), let title {
            let id = String(final.path[r].dropFirst("/track/".count))
            let parts = (tags["og:description"].map(decodeEntities) ?? "").components(separatedBy: " · ").map { $0.trimmingCharacters(in: .whitespaces) }
            let preview = tags["og:audio"].flatMap { $0.hasPrefix("https://p.scdn.co/") ? $0 : nil }
            return ResolvedLink(title: title, imageUrl: image, music: MusicBlock(
                title: String(title.prefix(300)), artist: String((parts.first ?? "").prefix(200)),
                album: parts.count >= 3 ? parts[1] : nil, artworkUrl: image, provider: "Spotify", providerTrackId: id,
                providerUrl: "https://open.spotify.com/track/\(id)", previewUrl: preview, previewDurationMillis: preview == nil ? nil : 30_000))
        }
        return ResolvedLink(title: title.map { String($0.prefix(300)) }, imageUrl: image)
    }

    static func itunes(_ id: String, link: String) async -> MusicBlock? {
        guard let o = await json("https://itunes.apple.com/lookup?id=\(id)&country=\(MusicStorefront.code)"),
              let item = (o["results"] as? [[String: Any]])?.first, let title = (item["trackName"] as? String)?.nilIfBlank else { return nil }
        func https(_ s: Any?) -> String? {
            guard let v = (s as? String)?.nilIfBlank else { return nil }
            let fixed = v.hasPrefix("http://") ? "https://" + v.dropFirst(7) : v
            return fixed.hasPrefix("https://") ? fixed : nil
        }
        let preview = https(item["previewUrl"])
        return MusicBlock(title: title, artist: (item["artistName"] as? String) ?? "", album: (item["collectionName"] as? String)?.nilIfBlank,
                          artworkUrl: https(item["artworkUrl100"])?.replacingOccurrences(of: "100x100bb", with: "600x600bb"),
                          provider: "iTunes", providerTrackId: id, providerUrl: https(item["trackViewUrl"]) ?? link,
                          previewUrl: preview, previewDurationMillis: preview == nil ? nil : 30_000)
    }

    private static func request(_ url: String) -> URLRequest? {
        guard url.hasPrefix("https://"), let u = URL(string: url) else { return nil }
        var r = URLRequest(url: u, timeoutInterval: 6)
        r.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) REAL/1.0", forHTTPHeaderField: "User-Agent")
        r.setValue("tr-TR,tr;q=0.9,en;q=0.8", forHTTPHeaderField: "Accept-Language")
        return r
    }

    private static func json(_ url: String) async -> [String: Any]? {
        guard let r = request(url), let (data, resp) = try? await URLSession.shared.data(for: r),
              (resp as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) == true, data.count < 512_000 else { return nil }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    private static func fetchPage(_ url: String) async -> (finalUrl: String, html: String)? {
        guard let r = request(url), let (data, resp) = try? await URLSession.shared.data(for: r),
              let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              let final = http.url?.absoluteString, final.hasPrefix("https://") else { return nil }
        let html = String(decoding: data.prefix(400_000), as: UTF8.self)
        return (final, html)
    }

    static func metaTags(_ html: String) -> [String: String] {
        var result: [String: String] = [:]
        let meta = try! NSRegularExpression(pattern: "<meta\\s[^>]*>", options: [.caseInsensitive])
        let attr = try! NSRegularExpression(pattern: "([a-zA-Z:-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")
        let ns = html as NSString
        for m in meta.matches(in: html, range: NSRange(location: 0, length: ns.length)) {
            let tag = ns.substring(with: m.range) as NSString
            var attrs: [String: String] = [:]
            for a in attr.matches(in: tag as String, range: NSRange(location: 0, length: tag.length)) {
                let key = tag.substring(with: a.range(at: 1)).lowercased()
                let v3 = a.range(at: 3), v4 = a.range(at: 4)
                attrs[key] = v3.location != NSNotFound ? tag.substring(with: v3) : (v4.location != NSNotFound ? tag.substring(with: v4) : "")
            }
            guard let key = (attrs["property"] ?? attrs["name"])?.lowercased(), let content = attrs["content"], !content.isEmpty else { continue }
            if result[key] == nil { result[key] = content }
        }
        return result
    }

    private static func titleTag(_ html: String) -> String? {
        guard let r = html.range(of: "<title[^>]*>([^<]{1,300})</title>", options: [.regularExpression, .caseInsensitive]) else { return nil }
        return String(html[r]).replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
    }

    static func decodeEntities(_ s: String) -> String {
        var v = s
        for (k, r) in [("&quot;", "\""), ("&apos;", "'"), ("&#39;", "'"), ("&#x27;", "'"), ("&lt;", "<"), ("&gt;", ">"), ("&amp;", "&")] { v = v.replacingOccurrences(of: k, with: r) }
        return v
    }
}

/// The Apple Music store of the person's country (phone region), not always Turkey (MusicCatalog.storefront).
enum MusicStorefront {
    static var code: String {
        let c = (Locale.current.region?.identifier ?? "").uppercased()
        return c.count == 2 && c.allSatisfy(\.isLetter) ? c : "US"
    }
    static var name: String { Locale(identifier: "tr").localizedString(forRegionCode: code) ?? code }
}

extension String {
    var nilIfBlank: String? { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self }
}
