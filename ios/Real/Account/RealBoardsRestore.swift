import Foundation

/// "Hesabından yeniden yükle": reads the account's boards from /api/boards/v1 and puts them on this device in one go.
/// The everyday two-way sync (and photo downloads) is PanoStore.sync.
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

    /// PanoItem.from (Android) → the iOS block types (shared with the two-way sync).
    private static func item(_ o: [String: Any], boardId: String) -> PanoItem? { PanoStore.item(o, boardId: boardId) }
}
