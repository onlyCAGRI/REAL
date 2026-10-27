import Foundation
import Observation
import UIKit

enum BoardBlockType: String, Codable, CaseIterable { case text = "TEXT", card = "CARD", checklist = "CHECKLIST", photo = "PHOTO", stack = "STACK" }

struct BoardTransform: Codable, Hashable {
    var x: Double = 0
    var y: Double = 0
    var width: Double = 280
    var height: Double = 180
    var rotation: Double = 0
    var z: Int = 0
}

struct BoardViewport: Codable, Hashable {
    var x: Double = 24
    var y: Double = 24
    var zoom: Double = 1
    static let minZoom = 0.01
    static let maxZoom = 3.0
}

/// The server only takes lowercase UUIDs.
func panoId() -> String { UUID().uuidString.lowercased() }

struct PanoChecklistEntry: Codable, Hashable, Identifiable {
    var id = panoId()
    var text: String
    var done = false
}

struct PanoItem: Codable, Identifiable, Hashable {
    var id = panoId()
    var boardId: String
    var type: BoardBlockType
    var transform: BoardTransform
    var text: String = ""
    var reminderId: Int64? = nil
    var checklist: [PanoChecklistEntry] = []
    var photoPath: String? = nil
    var stack: [CardStackCard] = []
    var authorName: String = "ME"
    // Cloud (BoardRepository): the versions the server last confirmed, the board asset behind a photo, the stack's
    // board-owned photos (card id → asset id) and the server payload, kept so fields iOS does not edit (text colour,
    // alignment, stack id…) go back unchanged. Optional so boards saved before cloud sync still load.
    var revision: Int? = nil
    var transformVersion: Int? = nil
    var contentVersion: Int? = nil
    var assetId: String? = nil
    var mime: String? = nil
    var stackImages: [String: String]? = nil
    var rawPayload: String? = nil
}

struct PanoBoard: Codable, Identifiable, Hashable {
    var id = panoId()
    var title: String
    var shared: Bool
    var role = "OWNER"
    var updatedAt: EpochMillis = .now
    var background = "BLACK_DOTS"
}

/// One queued change (BoardMutation). The request body is built when it is sent, from the block as it is then, so a
/// block added and edited offline goes up once with its latest content.
struct PanoMutation: Codable, Hashable {
    enum Kind: String, Codable { case board = "BOARD", add = "ADD", transform = "TRANSFORM", content = "CONTENT", delete = "DELETE", removeBoard = "REMOVE_BOARD" }
    var id = panoId()
    var kind: Kind
    var boardId: String
    var itemId: String? = nil
    /// Sent at least once: its operation id must be reused, so it is never merged with a later edit.
    var attempted = false
    /// BOARD: title / shared; REMOVE_BOARD: role; DELETE: the block's last server revision.
    var title: String? = nil
    var shared: Bool? = nil
    var role: String? = nil
    var revision: Int? = nil
}

/// Boards, blocks and per-board viewports, kept on the phone and synced both ways with the REAL server
/// (BoardRepository: an item-level outbox, version-checked edits; the server copy wins a conflict).
@Observable
final class PanoStore {
    private(set) var boards: [PanoBoard] = []
    private(set) var items: [String: [PanoItem]] = [:]
    var viewports: [String: BoardViewport] = [:]
    private(set) var queue: [PanoMutation] = []
    private(set) var cursors: [String: Int64] = [:]
    private(set) var syncing = false
    /// Boards made on this iPhone before cloud sync were sent up once (see `migrateLocalBoards`).
    @ObservationIgnored private var migrated = false

    @ObservationIgnored private let fileURL: URL
    @ObservationIgnored private let persist: Bool
    @ObservationIgnored private var pendingSync: Task<Void, Never>?
    /// Card blocks need the card in this account's cloud copy first (RealCloudSync), so the stores are kept here.
    @ObservationIgnored weak var reminders: ReminderStore?
    @ObservationIgnored weak var life: LifeStore?

    private struct Snapshot: Codable {
        var boards: [PanoBoard]
        var items: [String: [PanoItem]]
        var viewports: [String: BoardViewport]
        var queue: [PanoMutation]?
        var cursors: [String: Int64]?
        var migrated: Bool?
    }

    init(persist: Bool = true) {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appendingPathComponent("real-pano.json")
        self.persist = persist
        if persist, let data = try? Data(contentsOf: fileURL), let snap = try? JSONDecoder().decode(Snapshot.self, from: data) {
            boards = snap.boards
            items = snap.items
            viewports = snap.viewports
            queue = snap.queue ?? []
            cursors = snap.cursors ?? [:]
            migrated = snap.migrated ?? false
        }
    }

    func save() {
        guard persist else { return }
        let snap = Snapshot(boards: boards, items: items, viewports: viewports, queue: queue, cursors: cursors, migrated: migrated)
        if let data = try? JSONEncoder().encode(snap) { try? data.write(to: fileURL, options: .atomic) }
    }

    // MARK: Local edits (each one queued for the server)

    @discardableResult
    func createBoard(title: String, shared: Bool) -> PanoBoard {
        let board = PanoBoard(title: title.trimmingCharacters(in: .whitespacesAndNewlines), shared: shared)
        boards.insert(board, at: 0)
        queue.append(PanoMutation(kind: .board, boardId: board.id, title: board.title, shared: shared))
        save()
        requestSync()
        return board
    }

    /// The account's boards replace their copies here; boards that exist only on this device are kept.
    /// The look (background) and viewport chosen on this device stay as they were.
    func mergeFromAccount(boards remote: [PanoBoard], items remoteItems: [String: [PanoItem]]) {
        let remoteIds = Set(remote.map(\.id))
        let looks = Dictionary(boards.map { ($0.id, $0.background) }, uniquingKeysWith: { a, _ in a })
        let merged = remote.map { board -> PanoBoard in
            var b = board
            if let look = looks[b.id] { b.background = look }
            return b
        }
        boards = (merged + boards.filter { !remoteIds.contains($0.id) }).sorted { $0.updatedAt > $1.updatedAt }
        for (id, list) in remoteItems where !queue.contains(where: { $0.boardId == id }) { items[id] = list }
        save()
    }

    func deleteBoard(_ id: String) {
        let role = boards.first { $0.id == id }?.role ?? "OWNER"
        let neverSent = queue.contains { $0.kind == .board && $0.boardId == id && !$0.attempted }
        boards.removeAll { $0.id == id }
        items[id] = nil
        viewports[id] = nil
        cursors[id] = nil
        queue.removeAll { $0.boardId == id && !$0.attempted }
        if !neverSent { queue.append(PanoMutation(kind: .removeBoard, boardId: id, role: role)) }
        save()
        requestSync()
    }

    func setBackground(_ boardId: String, _ look: String) {
        guard let i = boards.firstIndex(where: { $0.id == boardId }) else { return }
        boards[i].background = look
        save()
    }

    func items(of boardId: String) -> [PanoItem] { (items[boardId] ?? []).sorted { $0.transform.z < $1.transform.z } }

    @discardableResult
    func add(_ item: PanoItem) -> PanoItem {
        var it = item
        it.transform.z = ((items[item.boardId] ?? []).map(\.transform.z).max() ?? 0) + 1
        items[item.boardId, default: []].append(it)
        touch(item.boardId)
        queue.append(PanoMutation(kind: .add, boardId: it.boardId, itemId: it.id))
        save()
        requestSync()
        return it
    }

    func update(_ item: PanoItem) {
        guard var list = items[item.boardId], let i = list.firstIndex(where: { $0.id == item.id }) else { return }
        let old = list[i]
        list[i] = item
        items[item.boardId] = list
        touch(item.boardId)
        if old.transform != item.transform { enqueueEdit(.transform, item) }
        if contentChanged(old, item) { enqueueEdit(.content, item) }
        save()
        requestSync()
    }

    func remove(_ item: PanoItem) {
        items[item.boardId]?.removeAll { $0.id == item.id }
        touch(item.boardId)
        let pendingAdd = queue.contains { $0.itemId == item.id && $0.kind == .add && !$0.attempted }
        queue.removeAll { $0.itemId == item.id && !$0.attempted }
        if !pendingAdd { queue.append(PanoMutation(kind: .delete, boardId: item.boardId, itemId: item.id, revision: item.revision)) }
        save()
        requestSync()
    }

    func bringToFront(_ item: PanoItem) {
        var it = item
        it.transform.z = ((items[item.boardId] ?? []).map(\.transform.z).max() ?? 0) + 1
        update(it)
    }

    private func touch(_ boardId: String) {
        if let i = boards.firstIndex(where: { $0.id == boardId }) { boards[i].updatedAt = .now }
    }

    func replaceAll(boards: [PanoBoard], items: [String: [PanoItem]]) {
        self.boards = boards
        self.items = items
        save()
    }

    private func contentChanged(_ a: PanoItem, _ b: PanoItem) -> Bool {
        switch b.type {
        case .text: return a.text != b.text
        case .checklist: return a.text != b.text || a.checklist != b.checklist
        case .stack: return a.stack != b.stack
        case .card, .photo: return false            // a card is a server snapshot; a photo is replaced, not edited
        }
    }

    /// One unsent edit per block and kind; a block whose add has not gone up yet needs none (the add carries it).
    private func enqueueEdit(_ kind: PanoMutation.Kind, _ item: PanoItem) {
        if queue.contains(where: { $0.itemId == item.id && $0.kind == .add && !$0.attempted }) { return }
        if queue.contains(where: { $0.itemId == item.id && $0.kind == kind && !$0.attempted }) { return }
        queue.append(PanoMutation(kind: kind, boardId: item.boardId, itemId: item.id))
    }

    // MARK: Cloud sync

    /// A short pause after the last edit, then one sync (a drag does not send every step).
    func requestSync(delay: Double = 1.5) {
        guard persist else { return }
        pendingSync?.cancel()
        pendingSync = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(delay))
            guard !Task.isCancelled else { return }
            await self?.sync()
        }
    }

    /// Sends the queue, then reads the board list and every board's changes since the last read.
    @MainActor
    func sync() async {
        guard persist, !syncing, RealAuth.shared.account != nil else { return }
        syncing = true
        defer { syncing = false; save() }
        do {
            try await migrateLocalBoards()
            try await flush()
            try await pullBoards()
            for board in boards where !queue.contains(where: { $0.kind == .board && $0.boardId == board.id }) {
                try await pull(board.id)
            }
            await downloadAssets()
        } catch {
            // Offline or the server said no: the queue stays and goes up next time.
        }
    }

    /// Once: boards made on this iPhone before cloud sync go up, and blocks added here to boards that came from the
    /// account (the old one-way restore) go up to those boards.
    @MainActor
    private func migrateLocalBoards() async throws {
        guard !migrated else { return }
        let list = try await call("boards")
        let remote = Set((list["boards"] as? [[String: Any]] ?? []).compactMap { $0["id"] as? String })
        func valid(_ id: String) -> Bool { id.range(of: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", options: .regularExpression) != nil }
        for board in boards where !remote.contains(board.id) && board.role == "OWNER" && !queue.contains(where: { $0.boardId == board.id }) {
            // Old boards used uppercase ids; the server only takes lowercase ones.
            let id = valid(board.id) ? board.id : panoId()
            if id != board.id, let i = boards.firstIndex(where: { $0.id == board.id }) {
                boards[i].id = id
                items[id] = items[board.id]?.map { var it = $0; it.boardId = id; return it }
                items[board.id] = nil
                viewports[id] = viewports[board.id]; viewports[board.id] = nil
            }
            queue.append(PanoMutation(kind: .board, boardId: id, title: board.title, shared: board.shared))
            for item in items[id] ?? [] { queueAdd(item, boardId: id) }
        }
        for board in boards where remote.contains(board.id) {
            let r = try await call("boards/\(board.id)?cursor=0")
            let serverIds = Set((r["items"] as? [[String: Any]] ?? []).compactMap { $0["id"] as? String })
            for item in items[board.id] ?? [] where !serverIds.contains(item.id) { queueAdd(item, boardId: board.id) }
        }
        migrated = true
        save()
    }

    /// Queues a never-sent block, giving it a lowercase id first if it has an old one.
    private func queueAdd(_ item: PanoItem, boardId: String) {
        var it = item
        if it.id != it.id.lowercased() || it.id.count != 36 {
            items[boardId]?.removeAll { $0.id == item.id }
            it.id = panoId()
            items[boardId, default: []].append(it)
        }
        if !queue.contains(where: { $0.itemId == it.id }) { queue.append(PanoMutation(kind: .add, boardId: boardId, itemId: it.id)) }
    }

    private enum PanoFailure: Error { case http(Int, [String: Any]) }

    @MainActor
    private func call(_ path: String, method: String = "GET", body: [String: Any]? = nil) async throws -> [String: Any] {
        guard let url = URL(string: RealBackend.origin + "/api/boards/v1/" + path) else { throw URLError(.badURL) }
        var request = URLRequest(url: url, timeoutInterval: 60)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let body { request.httpBody = try JSONSerialization.data(withJSONObject: body) }
        for (k, v) in try await RealAuth.requestHeaders() { request.setValue(v, forHTTPHeaderField: k) }
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        AppUpdateGate.observe(status: status)
        let json = (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
        guard (200..<300).contains(status) else { throw PanoFailure.http(status, json) }
        return json
    }

    @MainActor
    private func flush() async throws {
        var rounds = 0
        while let op = queue.first, rounds < 200 {
            rounds += 1
            if let i = queue.firstIndex(where: { $0.id == op.id }) { queue[i].attempted = true }
            save()
            do {
                let result = try await send(op)
                queue.removeAll { $0.id == op.id }
                if let raw = result["item"] as? [String: Any] { apply(raw, boardId: op.boardId) }
                if let b = result["board"] as? [String: Any], let i = boards.firstIndex(where: { $0.id == op.boardId }) {
                    boards[i].role = (b["role"] as? String) ?? boards[i].role
                }
            } catch PanoFailure.http(let status, let body) {
                let error = body["error"] as? String ?? ""
                if status == 409, let raw = body["item"] as? [String: Any], let itemId = op.itemId {
                    // Changed on another device: the server copy wins.
                    queue.removeAll { $0.itemId == itemId }
                    if let server = Self.item(raw, boardId: op.boardId) { replace(server) }
                    RealToast.shared.show(t("Bir pano bloğu başka cihazda değişmişti; son hali alındı"))
                } else if status == 410 || (status == 404 && error == "ITEM_NOT_FOUND"), let itemId = op.itemId {
                    queue.removeAll { $0.itemId == itemId }
                    items[op.boardId]?.removeAll { $0.id == itemId }
                } else if status == 404 && error == "BOARD_NOT_FOUND" {
                    queue.removeAll { $0.boardId == op.boardId }
                    boards.removeAll { $0.id == op.boardId }
                    items[op.boardId] = nil; cursors[op.boardId] = nil
                } else if [400, 403, 413, 415].contains(status) || (status == 409 && error != "SYNC_CARD_FIRST" && error != "ASSET_NOT_READY") {
                    // The server will never take this one; drop it rather than block the rest of the queue.
                    queue.removeAll { $0.id == op.id }
                } else {
                    throw PanoFailure.http(status, body)
                }
            }
        }
    }

    @MainActor
    private func send(_ op: PanoMutation) async throws -> [String: Any] {
        let path = "boards/\(op.boardId)"
        let current = op.itemId.flatMap { id in items[op.boardId]?.first { $0.id == id } }
        switch op.kind {
        case .board:
            return try await call("boards", method: "POST", body: ["id": op.boardId, "title": op.title ?? "Pano", "shared": op.shared ?? false])
        case .removeBoard:
            if op.role == "MEMBER", let uid = RealAuth.shared.account?.uid {
                return try await call("\(path)/members/\(uid.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? uid)", method: "DELETE", body: [:])
            }
            return try await call(path, method: "DELETE", body: [:])
        case .add:
            guard var item = current else { return [:] }
            var body: [String: Any] = ["id": item.id, "type": item.type.rawValue, "transform": Self.transformJSON(item.transform)]
            switch item.type {
            case .card:
                guard let cardId = item.reminderId else { return [:] }
                // The server copies the card from this account's cloud copy, so it is synced first.
                if let reminders, let life { await RealCloudSync.shared.runFully(reminders, life) }
                body["cardKey"] = String(cardId)
                body["payload"] = [String: Any]()
            case .photo:
                try await uploadPhoto(&item)
                body["payload"] = ["assetId": item.assetId ?? "", "mime": item.mime ?? "image/jpeg"]
            case .stack:
                try await uploadStackImages(&item)
                body["payload"] = Self.payload(item)
            case .text, .checklist:
                body["payload"] = Self.payload(item)
            }
            replace(item)
            return try await call("\(path)/items", method: "POST", body: body)
        case .transform:
            guard let item = current else { return [:] }
            return try await call("\(path)/items/\(item.id)", method: "PATCH",
                                  body: ["operationId": op.id, "baseVersion": item.transformVersion ?? 1, "group": "transform",
                                         "transform": Self.transformJSON(item.transform)])
        case .content:
            guard var item = current, item.type != .card, item.type != .photo else { return [:] }
            if item.type == .stack { try await uploadStackImages(&item); replace(item) }
            return try await call("\(path)/items/\(item.id)", method: "PATCH",
                                  body: ["operationId": op.id, "baseVersion": item.contentVersion ?? 1, "group": "content", "payload": Self.payload(item)])
        case .delete:
            guard let itemId = op.itemId else { return [:] }
            return try await call("\(path)/items/\(itemId)", method: "DELETE", body: ["operationId": op.id, "baseVersion": op.revision ?? 1])
        }
    }

    @MainActor
    private func pullBoards() async throws {
        let r = try await call("boards")
        let rows = r["boards"] as? [[String: Any]] ?? []
        let pending = Set(queue.filter { $0.kind == .board }.map(\.boardId))
        let removing = Set(queue.filter { $0.kind == .removeBoard }.map(\.boardId))
        let looks = Dictionary(boards.map { ($0.id, $0.background) }, uniquingKeysWith: { a, _ in a })
        var remote: [PanoBoard] = []
        for row in rows {
            guard let id = row["id"] as? String, !removing.contains(id), let title = row["title"] as? String else { continue }
            var b = PanoBoard(id: id, title: title, shared: (row["shared"] as? Int) == 1 || (row["shared"] as? Bool) == true)
            b.role = (row["role"] as? String) ?? "OWNER"
            b.updatedAt = (row["updated_at"] as? NSNumber)?.int64Value ?? .now
            if let look = looks[id] { b.background = look }
            remote.append(b)
        }
        let remoteIds = Set(remote.map(\.id))
        let allowed = remoteIds.union(pending)
        boards = (remote + boards.filter { pending.contains($0.id) && !remoteIds.contains($0.id) }).sorted { $0.updatedAt > $1.updatedAt }
        items = items.filter { allowed.contains($0.key) }
        cursors = cursors.filter { allowed.contains($0.key) }
    }

    @MainActor
    private func pull(_ boardId: String) async throws {
        var pages = 0
        while pages < 50 {
            pages += 1
            let cursor = cursors[boardId] ?? 0
            let r = try await call("boards/\(boardId)?cursor=\(cursor)")
            let next = (r["cursor"] as? NSNumber)?.int64Value ?? cursor
            let busy = Set(queue.compactMap(\.itemId))
            let before = items[boardId] ?? []
            var list = before
            let incoming = r["items"] as? [[String: Any]] ?? []
            if cursor == 0 {
                // First read: the server's blocks, plus local blocks still waiting to go up.
                let ids = Set(incoming.compactMap { $0["id"] as? String })
                list = list.filter { busy.contains($0.id) && !ids.contains($0.id) }
            }
            for raw in incoming {
                guard let id = raw["id"] as? String, !busy.contains(id) else { continue }
                list.removeAll { $0.id == id }
                let deleted = raw["deleted_at"] != nil && !(raw["deleted_at"] is NSNull)
                guard !deleted, let server = Self.item(raw, boardId: boardId) else { continue }
                list.append(Self.keepingFiles(server, before.first { $0.id == id }))
            }
            items[boardId] = list
            cursors[boardId] = next
            guard r["hasMore"] as? Bool == true, next > cursor else { break }
        }
    }

    /// A server copy keeps the photos already downloaded for the same assets.
    private static func keepingFiles(_ server: PanoItem, _ old: PanoItem?) -> PanoItem {
        guard let old else { return server }
        var it = server
        if it.assetId == old.assetId { it.photoPath = old.photoPath }
        it.stack = it.stack.map { card in
            var c = card
            if it.stackImages?[c.id] != nil, it.stackImages?[c.id] == old.stackImages?[c.id] { c.imageUri = old.stack.first { $0.id == c.id }?.imageUri }
            return c
        }
        return it
    }

    /// A server answer for one block: its versions and payload. Edits made while the request was out stay local
    /// (their own queued operations follow).
    private func apply(_ raw: [String: Any], boardId: String) {
        guard let server = Self.item(raw, boardId: boardId) else { return }
        let old = items[boardId]?.first { $0.id == server.id }
        if let old, queue.contains(where: { $0.itemId == server.id }) {
            var kept = old
            kept.revision = server.revision; kept.transformVersion = server.transformVersion; kept.contentVersion = server.contentVersion
            kept.rawPayload = server.rawPayload
            replace(kept)
        } else if let old, old.type == .stack || old.type == .photo {
            // Local files stay; the server only confirms the asset references this phone sent.
            var it = server
            it.photoPath = old.photoPath
            it.stack = it.stack.map { card in var c = card; c.imageUri = old.stack.first { $0.id == c.id }?.imageUri; return c }
            replace(it)
        } else {
            replace(server)
        }
    }

    private func replace(_ item: PanoItem) {
        var list = items[item.boardId] ?? []
        if let i = list.firstIndex(where: { $0.id == item.id }) { list[i] = item } else { list.append(item) }
        items[item.boardId] = list
    }

    // MARK: Files (board-owned assets)

    private static var fileDirectory: URL { AttachmentStorage.directory }

    @MainActor
    private func uploadPhoto(_ item: inout PanoItem) async throws {
        guard let path = item.photoPath, let data = try? Data(contentsOf: Self.fileDirectory.appendingPathComponent(path)) else { return }
        let id = item.assetId ?? panoId()
        let mime = Self.mime(of: data)
        item.assetId = id; item.mime = mime
        replace(item)                                   // a retry reuses the same asset id
        try await uploadAsset(boardId: item.boardId, id: id, mime: mime, data: data)
    }

    @MainActor
    private func uploadStackImages(_ item: inout PanoItem) async throws {
        var images = item.stackImages ?? [:]
        for card in item.stack {
            guard images[card.id] == nil, let path = card.imageUri, !path.hasPrefix("https://"),
                  let data = try? Data(contentsOf: Self.fileDirectory.appendingPathComponent(path)) else { continue }
            let id = panoId()
            try await uploadAsset(boardId: item.boardId, id: id, mime: Self.mime(of: data), data: data)
            images[card.id] = id
        }
        // A card whose photo was taken off no longer points at it.
        let withImage = Set(item.stack.filter { $0.imageUri != nil }.map(\.id))
        item.stackImages = images.filter { withImage.contains($0.key) }
    }

    @MainActor
    private func uploadAsset(boardId: String, id: String, mime: String, data: Data) async throws {
        guard data.count <= 10 * 1024 * 1024 else { throw PanoFailure.http(413, [:]) }
        let r = try await call("boards/\(boardId)/assets/\(id)", method: "POST", body: ["mime": mime, "byte_size": data.count, "kind": "IMAGE"])
        if (r["state"] as? String) == "READY" { return }
        guard let url = URL(string: RealBackend.origin + "/api/boards/v1/boards/\(boardId)/assets/\(id)/content") else { return }
        var request = URLRequest(url: url, timeoutInterval: 90)
        request.httpMethod = "PUT"
        for (k, v) in try await RealAuth.requestHeaders() { request.setValue(v, forHTTPHeaderField: k) }
        request.setValue(mime, forHTTPHeaderField: "Content-Type")
        let (body, response) = try await URLSession.shared.upload(for: request, from: data)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        AppUpdateGate.observe(status: status)
        guard (200..<300).contains(status) else {
            throw PanoFailure.http(status, (try? JSONSerialization.jsonObject(with: body) as? [String: Any]) ?? [:])
        }
    }

    /// Photos and stack photos from the server are fetched into the attachments folder.
    @MainActor
    private func downloadAssets() async {
        func missing(_ path: String?) -> Bool { path.map { !FileManager.default.fileExists(atPath: Self.fileDirectory.appendingPathComponent($0).path) } ?? true }
        for (boardId, list) in items {
            for item in list {
                if let asset = item.assetId, missing(item.photoPath), let name = await download(boardId: boardId, assetId: asset),
                   var current = items[boardId]?.first(where: { $0.id == item.id }) {
                    current.photoPath = name
                    replace(current)
                }
                for (cardId, asset) in item.stackImages ?? [:] {
                    guard let card = item.stack.first(where: { $0.id == cardId }), missing(card.imageUri),
                          let name = await download(boardId: boardId, assetId: asset),
                          var current = items[boardId]?.first(where: { $0.id == item.id }),
                          let i = current.stack.firstIndex(where: { $0.id == cardId }) else { continue }
                    current.stack[i].imageUri = name
                    replace(current)
                }
            }
        }
    }

    @MainActor
    private func download(boardId: String, assetId: String) async -> String? {
        let name = "board-\(assetId)"
        let target = Self.fileDirectory.appendingPathComponent(name)
        if FileManager.default.fileExists(atPath: target.path) { return name }
        guard let url = URL(string: RealBackend.origin + "/api/boards/v1/boards/\(boardId)/assets/\(assetId)/content"),
              let headers = try? await RealAuth.requestHeaders() else { return nil }
        var request = URLRequest(url: url, timeoutInterval: 60)
        for (k, v) in headers { request.setValue(v, forHTTPHeaderField: k) }
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              ((response as? HTTPURLResponse)?.statusCode ?? 0) / 100 == 2, data.count <= 16 * 1024 * 1024 else { return nil }
        do { try data.write(to: target, options: .atomic) } catch { return nil }
        return name
    }

    private static func mime(of data: Data) -> String {
        let b = [UInt8](data.prefix(12))
        if b.starts(with: [0x89, 0x50, 0x4E, 0x47]) { return "image/png" }
        if b.starts(with: [0x47, 0x49, 0x46]) { return "image/gif" }
        if b.count >= 12, b[0] == 0x52, b[1] == 0x49, b[8] == 0x57, b[9] == 0x45 { return "image/webp" }
        return "image/jpeg"
    }

    // MARK: Wire format (PanoItem.from / json on Android)

    static func transformJSON(_ t: BoardTransform) -> [String: Any] {
        ["x": t.x, "y": t.y, "width": t.width, "height": t.height, "rotation": t.rotation, "z": t.z]
    }

    private static func raw(_ item: PanoItem) -> [String: Any] {
        item.rawPayload.flatMap { $0.data(using: .utf8) }.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:]
    }

    static func payload(_ item: PanoItem) -> [String: Any] {
        var p = raw(item)
        switch item.type {
        case .text:
            p["text"] = item.text
            if p["title"] == nil { p["title"] = false }
        case .checklist:
            p = ["title": item.text, "items": item.checklist.map { ["id": $0.id, "text": $0.text, "done": $0.done] }]
        case .stack:
            let old = p["stack"] as? [String: Any] ?? [:]
            let cards: [[String: Any]] = item.stack.map { c in
                var o: [String: Any] = ["id": c.id, "title": c.title, "body": c.body, "imageUri": NSNull(), "colorArgb": NSNull()]
                if let argb = c.colorArgb { o["colorArgb"] = NSNumber(value: Int32(truncatingIfNeeded: argb)) }
                return o
            }
            let createdAt = old["createdAt"] ?? NSNumber(value: Int64(Date().timeIntervalSince1970 * 1000))
            p = ["stack": ["id": old["id"] as? String ?? panoId(), "prompt": old["prompt"] ?? NSNull(), "cards": cards, "createdAt": createdAt],
                 "images": (item.stackImages ?? [:]).map { ["cardId": $0.key, "assetId": $0.value, "mime": "image/jpeg"] }]
        case .photo:
            p = ["assetId": item.assetId ?? "", "mime": item.mime ?? "image/jpeg"]
        case .card:
            break
        }
        return p
    }

    /// Server item → iOS block. Types iOS cannot show yet (music, film, mood, voice, file, link, location, GIF) stay
    /// on the server untouched.
    static func item(_ o: [String: Any], boardId: String) -> PanoItem? {
        guard let typeName = o["type"] as? String, let type = BoardBlockType(rawValue: typeName), let id = o["id"] as? String else { return nil }
        let t = (o["transform"] as? [String: Any]) ?? [:]
        func num(_ key: String, _ fallback: Double) -> Double { (t[key] as? NSNumber)?.doubleValue ?? fallback }
        let transform = BoardTransform(x: num("x", 0), y: num("y", 0), width: num("width", 280), height: num("height", 180),
                                       rotation: num("rotation", 0), z: (t["z"] as? NSNumber)?.intValue ?? 0)
        let payload = (o["payload"] as? [String: Any]) ?? [:]
        var item = PanoItem(id: id, boardId: boardId, type: type, transform: transform)
        item.authorName = (o["author_name"] as? String) ?? "REAL user"
        item.revision = (o["revision"] as? NSNumber)?.intValue ?? 1
        item.transformVersion = (o["transform_version"] as? NSNumber)?.intValue ?? 1
        item.contentVersion = (o["content_version"] as? NSNumber)?.intValue ?? 1
        item.rawPayload = (try? JSONSerialization.data(withJSONObject: payload)).flatMap { String(data: $0, encoding: .utf8) }
        switch type {
        case .text:
            item.text = (payload["text"] as? String) ?? ""
        case .checklist:
            item.text = (payload["title"] as? String) ?? ""
            item.checklist = ((payload["items"] as? [[String: Any]]) ?? []).map {
                PanoChecklistEntry(id: ($0["id"] as? String) ?? panoId(), text: ($0["text"] as? String) ?? "", done: ($0["done"] as? Bool) ?? false)
            }
        case .card:
            guard let card = payload["card"] as? [String: Any], let cardId = (card["id"] as? NSNumber)?.int64Value else { return nil }
            item.reminderId = cardId
        case .stack:
            let cards = ((payload["stack"] as? [String: Any])?["cards"] as? [[String: Any]]) ?? []
            item.stack = cards.compactMap { c in
                var copy = c
                copy["imageUri"] = nil
                guard let data = try? JSONSerialization.data(withJSONObject: copy) else { return nil }
                return try? JSONDecoder().decode(CardStackCard.self, from: data)
            }
            var images: [String: String] = [:]
            for image in (payload["images"] as? [[String: Any]]) ?? [] {
                if let card = image["cardId"] as? String, let asset = image["assetId"] as? String { images[card] = asset }
            }
            item.stackImages = images
        case .photo:
            item.assetId = payload["assetId"] as? String
            item.mime = payload["mime"] as? String
            guard item.assetId != nil else { return nil }
        }
        return item
    }
}
