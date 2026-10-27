import SwiftUI

/// CardGameBlock.kt — Oyun bloğu: a game on a card (cover, name, year, genres, platforms, IGDB page). Its own
/// `gameBlocks` field, not mediaBlocks (MediaType is decoded strictly and an unknown type would drop the card).
struct CardGameBlock: Codable, Hashable, Identifiable {
    var id: String = UUID().uuidString.lowercased()
    var providerId: String
    var title: String
    var year: Int?
    var genres: [String] = []
    var platforms: [String] = []
    var artworkUrl: String?
    var description: String?
    var provider: String = "IGDB"
    var providerUrl: String
    var createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)

    enum CodingKeys: String, CodingKey { case id, providerId, title, year, genres, platforms, artworkUrl, description, provider, providerUrl, createdAt }

    init(id: String = UUID().uuidString.lowercased(), providerId: String, title: String, year: Int? = nil, genres: [String] = [],
         platforms: [String] = [], artworkUrl: String? = nil, description: String? = nil, provider: String = "IGDB",
         providerUrl: String, createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        self.id = id; self.providerId = providerId; self.title = title; self.year = year; self.genres = genres
        self.platforms = platforms; self.artworkUrl = artworkUrl; self.description = description
        self.provider = provider; self.providerUrl = providerUrl; self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decode(String.self, forKey: .title)
        providerId = (try? c.decodeIfPresent(String.self, forKey: .providerId)) ?? id
        year = (try? c.decodeIfPresent(Int.self, forKey: .year)) ?? nil
        genres = (try? c.decodeIfPresent([String].self, forKey: .genres)) ?? []
        platforms = (try? c.decodeIfPresent([String].self, forKey: .platforms)) ?? []
        artworkUrl = (try? c.decodeIfPresent(String.self, forKey: .artworkUrl)) ?? nil
        description = (try? c.decodeIfPresent(String.self, forKey: .description)) ?? nil
        provider = (try? c.decodeIfPresent(String.self, forKey: .provider)) ?? "IGDB"
        providerUrl = try c.decode(String.self, forKey: .providerUrl)
        createdAt = (try? c.decodeIfPresent(Int64.self, forKey: .createdAt)) ?? 0
    }

    /// Worker `games[]` row (IGDB, /api/game-search and /api/game-recommendations).
    static func fromWorker(_ o: [String: Any]) -> CardGameBlock? {
        guard let title = (o["title"] as? String)?.trimmingCharacters(in: .whitespaces), !title.isEmpty,
              let url = o["providerUrl"] as? String, url.hasPrefix("https://") else { return nil }
        let id = (o["providerId"] as? String) ?? "\(o["providerId"] ?? "")"
        guard !id.isEmpty else { return nil }
        let platforms = ((o["platforms"] as? [[String: Any]]) ?? []).compactMap { ($0["short"] as? String) ?? ($0["name"] as? String) }
        return CardGameBlock(providerId: id, title: String(title.prefix(300)), year: (o["year"] as? Int).flatMap { $0 > 0 ? $0 : nil },
                             genres: Array(((o["genres"] as? [String]) ?? []).prefix(8)), platforms: Array(platforms.prefix(16)),
                             artworkUrl: (o["artworkUrl"] as? String).flatMap { $0.hasPrefix("https://") ? $0 : nil },
                             description: o["description"] as? String, providerUrl: url)
    }
}

/// IGDB name search through the REAL server (the IGDB key stays there).
enum GameSearch {
    static func search(_ query: String) async throws -> [CardGameBlock] {
        let json: [String: Any]
        do { json = try await RealBackend.post("/api/game-search", ["query": String(query.prefix(120))], timeout: 20) }
        catch let f as RealBackend.Failure where f.status == 404 { throw CalendarCardPublisher.Failure(errorDescription: t("Oyun araması için sunucunun güncellenmesi gerekiyor.")) }
        catch let f as RealBackend.Failure where f.status == 503 { throw CalendarCardPublisher.Failure(errorDescription: t("Oyun kataloğu şu anda hazır değil.")) }
        return ((json["games"] as? [[String: Any]]) ?? []).compactMap(CardGameBlock.fromWorker)
    }
}

/// GameContent.kt — the block on a card.
struct GameBlockView: View {
    let block: CardGameBlock
    var editing = false
    var onRemove: () -> Void = {}
    /// > 0: drawn as the card's cover (CatalogHeroCover) under the status bar.
    var heroInset: CGFloat = 0

    var body: some View {
        if heroInset > 0 && !editing {
            CatalogHeroCover(imageURL: block.artworkUrl.flatMap { URL(string: $0.replacingOccurrences(of: "t_cover_big", with: "t_cover_big_2x")) },
                             posterAspect: 3.0 / 4.0, title: block.title,
                             subtitle: [block.year.map(String.init), block.genres.prefix(2).joined(separator: " · ").nilIfEmpty].compactMap { $0 }.joined(separator: " · "),
                             description: block.platforms.prefix(6).joined(separator: " · "), placeholderSymbol: "play.rectangle", inset: heroInset) {
                if let url = URL(string: block.providerUrl), url.scheme == "https" {
                    HeroOpenRow(label: t("Oyun sayfasını aç ↗"), action: { UIApplication.shared.open(url) }) {
                        Text(block.provider).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8E8E98))
                    }
                }
            }
        } else {
            compact
        }
    }

    private var compact: some View {
        VStack(alignment: .leading, spacing: 10) {
            if editing {
                HStack {
                    Spacer()
                    Button(action: onRemove) { RealSymbol("xmark", size: 14.9).foregroundStyle(Color(argb: 0xFFB8B8C2)).frame(width: 40, height: 40) }
                        .accessibilityLabel(t("Oyunu kaldır"))
                }
            }
            HStack(alignment: .top, spacing: 14) {
                GameCover(block: block).frame(width: 92, height: 123)
                VStack(alignment: .leading, spacing: 4) {
                    Text(block.title).font(RealFont.inter(18, .medium)).foregroundStyle(Color(argb: 0xFFF1F1F4)).lineLimit(3)
                    let meta = [block.year.map(String.init), block.genres.prefix(2).joined(separator: " · ").nilIfEmpty].compactMap { $0 }
                    if !meta.isEmpty { Text(meta.joined(separator: " · ")).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFB1B1BC)).lineLimit(1) }
                    if !block.platforms.isEmpty {
                        Text(block.platforms.prefix(6).joined(separator: " · ")).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8E8E98)).lineLimit(2)
                    }
                }
            }
            if let url = URL(string: block.providerUrl), url.scheme == "https" {
                Link(destination: url) {
                    Text(t("Oyun sayfasını aç ↗")).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFD0D0D8))
                        .frame(maxWidth: .infinity).frame(height: 40)
                        .background(Color.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 24))
                }
                Text(t("Kaynak: {0}", block.provider)).font(RealFont.inter(10)).foregroundStyle(Color(argb: 0xFF6E6E78))
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Color(argb: 0xFF17171B))
                .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(LinearGradient(colors: [Color(argb: 0xFFF26A1B).opacity(0.16), .clear], startPoint: .topLeading, endPoint: .bottomTrailing)))
        )
    }
}

struct GameCover: View {
    let block: CardGameBlock
    var body: some View {
        ZStack {
            Color(argb: 0xFF26262C)
            RealSymbol("play.rectangle", size: 22).foregroundStyle(Color(argb: 0xFFB8B8C2))
            if let s = block.artworkUrl?.replacingOccurrences(of: "t_cover_big", with: "t_cover_big_2x"), let url = URL(string: s) {
                AsyncImage(url: url) { phase in
                    if let image = phase.image { image.resizable().scaledToFill() } else { Color.clear }
                }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
