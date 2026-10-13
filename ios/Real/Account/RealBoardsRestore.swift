import Foundation

/// BoardRepository.sync's pull half (first iOS step): reads the account's boards from /api/boards/v1 and puts them on
/// this device. Text, checklist, card and card-stack blocks come across; photo and other file blocks need the board
/// file store and come in a later step. Boards made only on this iPhone stay; nothing is sent back yet.
@MainActor
enum RealBoardsRestore {
    struct Summary { let boards: Int; let blocks: Int; let skipped: Int }

    static func run(pano: PanoStore) async throws -> Summary {
        let listData = try await RealBackend.request("/api/boards/v1/boards")
        guard let list = try JSONSerialization.jsonObject(with: listData) as? [String: Any],
              let rows = list["boards"] as? [[String: Any]] else { throw RealBackend.Failure(status: -1) }
        var boards: [PanoBoard] = []
        var items: [String: [PanoItem]] = [:]
        var skipped = 0
        for row in rows {
            guard let id = row["id"] as? String, let title = row["title"] as? String else { continue }
            let shared = (row["shared"] as? Int) == 1 || (row["shared"] as? Bool) == true
            var board = PanoBoard(id: id, title: title, shared: shared)
            board.role = (row["role"] as? String) ?? "OWNER"
            board.updatedAt = (row["updated_at"] as? NSNumber)?.int64Value ?? .now
            boards.append(board)

            var blocks: [String: PanoItem] = [:]
            var cursor: Int64 = 0
            var pages = 0
            while true {
                let data = try await RealBackend.request("/api/boards/v1/boards/\(id)?cursor=\(cursor)")
                guard let page = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { break }
                for raw in (page["items"] as? [[String: Any]]) ?? [] {
                    guard let itemId = raw["id"] as? String else { continue }
                    if !(raw["deleted_at"] is NSNull) && raw["deleted_at"] != nil { blocks[itemId] = nil; continue }
                    if let item = item(raw, boardId: id) { blocks[itemId] = item } else { skipped += 1 }
                }
                let next = (page["cursor"] as? NSNumber)?.int64Value ?? cursor
                pages += 1
                guard page["hasMore"] as? Bool == true, next > cursor, pages < 50 else { break }
                cursor = next
            }
            items[id] = Array(blocks.values)
        }
        pano.mergeFromAccount(boards: boards, items: items)
        return Summary(boards: boards.count, blocks: items.values.reduce(0) { $0 + $1.count }, skipped: skipped)
    }

    /// PanoItem.from (Android) → the iOS block types.
    private static func item(_ o: [String: Any], boardId: String) -> PanoItem? {
        guard let typeName = o["type"] as? String, let type = BoardBlockType(rawValue: typeName), type != .photo else { return nil }
        let t = (o["transform"] as? [String: Any]) ?? [:]
        func num(_ key: String, _ fallback: Double) -> Double { (t[key] as? NSNumber)?.doubleValue ?? fallback }
        let transform = BoardTransform(x: num("x", 0), y: num("y", 0), width: num("width", 280), height: num("height", 180),
                                       rotation: num("rotation", 0), z: (t["z"] as? NSNumber)?.intValue ?? 0)
        let payload = (o["payload"] as? [String: Any]) ?? [:]
        var item = PanoItem(id: o["id"] as? String ?? UUID().uuidString, boardId: boardId, type: type, transform: transform)
        item.authorName = (o["author_name"] as? String) ?? "REAL user"
        switch type {
        case .text:
            item.text = (payload["text"] as? String) ?? ""
        case .checklist:
            item.text = (payload["title"] as? String) ?? ""
            item.checklist = ((payload["items"] as? [[String: Any]]) ?? []).map {
                PanoChecklistEntry(id: ($0["id"] as? String) ?? UUID().uuidString, text: ($0["text"] as? String) ?? "", done: ($0["done"] as? Bool) ?? false)
            }
        case .card:
            guard let card = payload["card"] as? [String: Any], let cardId = (card["id"] as? NSNumber)?.int64Value else { return nil }
            item.reminderId = cardId
        case .stack:
            let cards = ((payload["stack"] as? [String: Any])?["cards"] as? [[String: Any]]) ?? []
            item.stack = cards.compactMap { c in
                var copy = c
                copy["imageUri"] = nil                 // board stack photos live in the board file store
                guard let data = try? JSONSerialization.data(withJSONObject: copy) else { return nil }
                return try? JSONDecoder().decode(CardStackCard.self, from: data)
            }
        case .photo:
            return nil
        }
        return item
    }
}
