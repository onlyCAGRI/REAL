import Foundation
import Observation

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

struct PanoChecklistEntry: Codable, Hashable, Identifiable {
    var id = UUID().uuidString
    var text: String
    var done = false
}

struct PanoItem: Codable, Identifiable, Hashable {
    var id = UUID().uuidString
    var boardId: String
    var type: BoardBlockType
    var transform: BoardTransform
    var text: String = ""
    var reminderId: Int64? = nil
    var checklist: [PanoChecklistEntry] = []
    var photoPath: String? = nil
    var stack: [CardStackCard] = []
    var authorName: String = "ME"
}

struct PanoBoard: Codable, Identifiable, Hashable {
    var id = UUID().uuidString
    var title: String
    var shared: Bool
    var role = "OWNER"
    var updatedAt: EpochMillis = .now
    var background = "BLACK_DOTS"
}

/// Local boards (BoardRepository without the Worker sync): boards, items and per-board viewports.
@Observable
final class PanoStore {
    private(set) var boards: [PanoBoard] = []
    private(set) var items: [String: [PanoItem]] = [:]
    var viewports: [String: BoardViewport] = [:]

    private let fileURL: URL
    private let persist: Bool

    private struct Snapshot: Codable {
        var boards: [PanoBoard]
        var items: [String: [PanoItem]]
        var viewports: [String: BoardViewport]
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
        }
    }

    func save() {
        guard persist else { return }
        if let data = try? JSONEncoder().encode(Snapshot(boards: boards, items: items, viewports: viewports)) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }

    @discardableResult
    func createBoard(title: String, shared: Bool) -> PanoBoard {
        let board = PanoBoard(title: title.trimmingCharacters(in: .whitespacesAndNewlines), shared: shared)
        boards.insert(board, at: 0)
        save()
        return board
    }

    func deleteBoard(_ id: String) {
        boards.removeAll { $0.id == id }
        items[id] = nil
        viewports[id] = nil
        save()
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
        save()
        return it
    }

    func update(_ item: PanoItem) {
        guard var list = items[item.boardId], let i = list.firstIndex(where: { $0.id == item.id }) else { return }
        list[i] = item
        items[item.boardId] = list
        touch(item.boardId)
        save()
    }

    func remove(_ item: PanoItem) {
        items[item.boardId]?.removeAll { $0.id == item.id }
        touch(item.boardId)
        save()
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
}
