import Foundation

/// Share → REAL on iOS. The share extension cannot write into the app's own storage, so it leaves each share in the
/// App Group container (`inbox/<id>/item.json` + its files) and REAL imports it the next time it is active. For
/// "Karta ekle" the app keeps a light list of its cards and categories there (`cards.json`).
enum ShareInbox {
    static let groupId = "group.com.cagri.real"

    static var container: URL? { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupId) }
    static var inbox: URL? {
        guard let c = container else { return nil }
        let dir = c.appendingPathComponent("inbox", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    // MARK: What the app tells the extension

    struct CardSummary: Codable, Hashable, Identifiable {
        var id: Int64
        var text: String
        var completed: Bool
        var updatedAt: Int64
        var hasLink: Bool
        var link: String?
        var photoCount: Int
    }
    struct CategorySummary: Codable, Hashable, Identifiable {
        var id: Int64
        var name: String
        var colorArgb: Int?
    }
    struct Snapshot: Codable {
        var cards: [CardSummary] = []
        var categories: [CategorySummary] = []
    }

    static func writeSnapshot(_ s: Snapshot) {
        guard let c = container, let data = try? JSONEncoder().encode(s) else { return }
        try? data.write(to: c.appendingPathComponent("cards.json"), options: .atomic)
    }

    static func readSnapshot() -> Snapshot {
        guard let c = container, let data = try? Data(contentsOf: c.appendingPathComponent("cards.json")),
              let s = try? JSONDecoder().decode(Snapshot.self, from: data) else { return Snapshot() }
        return s
    }

    // MARK: What the extension leaves for the app

    struct File: Codable, Hashable {
        var name: String          // inside the item's folder
        var displayName: String
        var mime: String?
        var isImage: Bool
        var isAudio: Bool
    }
    enum Destination: Codable, Hashable {
        case newCard(title: String, categoryId: Int64?, plannedDay: Int64?)
        case existingCard(id: Int64, replaceLink: Bool)
    }
    struct Item: Codable, Identifiable {
        var id: String = UUID().uuidString.lowercased()
        var createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
        var destination: Destination
        var snippet: String?
        var snippetSource: String?
        var snippetTitle: String?
        var url: String?
        var music: [MusicBlock] = []
        var files: [File] = []
    }

    /// The folder an extension copies one share's files into before `commit`.
    static func newItemFolder(_ id: String) -> URL? {
        guard let inbox else { return nil }
        let dir = inbox.appendingPathComponent(id, isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    /// Written last, so the app never imports a half-copied share.
    static func commit(_ item: Item) -> Bool {
        guard let dir = newItemFolder(item.id), let data = try? JSONEncoder().encode(item) else { return false }
        return (try? data.write(to: dir.appendingPathComponent("item.json"), options: .atomic)) != nil
    }

    static func discard(_ id: String) {
        guard let inbox else { return }
        try? FileManager.default.removeItem(at: inbox.appendingPathComponent(id, isDirectory: true))
    }

    /// Finished shares, oldest first, with their folders.
    static func pending() -> [(Item, URL)] {
        guard let inbox, let dirs = try? FileManager.default.contentsOfDirectory(at: inbox, includingPropertiesForKeys: nil) else { return [] }
        return dirs.compactMap { dir -> (Item, URL)? in
            guard let data = try? Data(contentsOf: dir.appendingPathComponent("item.json")),
                  let item = try? JSONDecoder().decode(Item.self, from: data) else { return nil }
            return (item, dir)
        }.sorted { $0.0.createdAt < $1.0.createdAt }
    }
}
