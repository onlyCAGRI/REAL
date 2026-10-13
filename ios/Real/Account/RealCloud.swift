import Foundation

/// REAL worker access (AndroidBackend.ORIGIN + WorkerClient): every request carries the Firebase ID token and
/// the App Check token from RealAuth.
enum RealBackend {
    static let origin = "https://real-backend-staging.cagrigul1907.workers.dev"

    struct Failure: Error { let status: Int }

    static func request(_ path: String, method: String = "GET", body: Data? = nil) async throws -> Data {
        guard let url = URL(string: origin + path) else { throw URLError(.badURL) }
        var request = URLRequest(url: url, timeoutInterval: 30)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        for (key, value) in try await RealAuth.requestHeaders() { request.setValue(value, forHTTPHeaderField: key) }
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else { throw Failure(status: status) }
        return data
    }
}

/// First step of iOS sync: a read-only restore of the signed-in account (SyncEngine's pull half).
/// Reads the whole change feed and replaces the device's cards, categories and moods with the account's.
/// Writing changes back from iOS and downloading photo / voice files come in the next step.
@MainActor
enum RealCloudRestore {
    struct Summary { let cards: Int; let categories: Int; let moods: Int }

    static func run(store: ReminderStore, life: LifeStore) async throws -> Summary {
        var cards: [Int64: ReminderItem] = [:]
        var categories: [Int64: CustomCategory] = [:]
        var moods: [Int64: MoodTrackerEntry] = [:]
        let decoder = JSONDecoder()
        var cursor: Int64 = 0
        var pages = 0
        while true {
            let data = try await RealBackend.request("/api/cloud/v1/sync/changes?cursor=\(cursor)")
            guard let page = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let records = page["records"] as? [[String: Any]] else { throw RealBackend.Failure(status: -1) }
            for record in records {
                guard let kind = record["kind"] as? String, let key = record["key"] as? String, let id = Int64(key) else { continue }
                // A null payload is a deletion; any later record for the same key replaces the earlier one.
                guard let payload = record["payload"] as? [String: Any],
                      let json = try? JSONSerialization.data(withJSONObject: payload) else {
                    switch kind {
                    case "CARD": cards[id] = nil
                    case "CATEGORY": categories[id] = nil
                    case "MOOD": moods[id] = nil
                    default: break
                    }
                    continue
                }
                _ = json
                switch kind {
                case "CARD": if let card = decodeCard(payload, decoder) { cards[id] = card }
                case "CATEGORY": if let category = decodeCategory(payload, decoder) { categories[id] = category }
                case "MOOD": if let mood = decodeMood(payload, decoder) { moods[id] = mood }
                default: break // planner details, water and preferences arrive with full sync
                }
            }
            let next = (page["cursor"] as? NSNumber)?.int64Value ?? cursor
            let hasMore = page["hasMore"] as? Bool ?? false
            pages += 1
            guard hasMore, next > cursor, pages < 500 else { break }
            cursor = next
        }
        // Files live in REAL's cloud storage; until file sync exists, photo / voice / file blocks without a local copy are hidden.
        let restoredCards = cards.values.map { card -> ReminderItem in
            var c = card
            c.attachments = c.attachments.filter { $0.type == .textSnippet || !$0.localPath.isEmpty }
            c.voiceRecordings = c.voiceRecordings.filter { !$0.localPath.isEmpty }
            return c
        }.sorted { $0.createdAt > $1.createdAt }
        store.replaceAll(reminders: restoredCards, categories: categories.values.sorted { $0.id < $1.id }, ownerName: store.ownerName)
        if !moods.isEmpty {
            life.replaceAll(goal: life.goalMl, entries: life.entries, moods: moods.values.sorted { $0.id > $1.id })
        }
        return Summary(cards: restoredCards.count, categories: categories.count, moods: moods.count)
    }

    // MARK: Android wire format → iOS models (missing keys get the defaults the iOS models use)

    private static func decode<T: Decodable>(_ type: T.Type, _ dict: [String: Any], _ decoder: JSONDecoder) -> T? {
        guard let data = try? JSONSerialization.data(withJSONObject: dict) else { return nil }
        return try? decoder.decode(type, from: data)
    }

    /// Cloud attachments carry a blobId instead of a device path. If a rich block still does not fit the iOS
    /// model, the card is kept without it rather than dropped.
    private static func decodeCard(_ payload: [String: Any], _ decoder: JSONDecoder) -> ReminderItem? {
        var p = payload
        for key in ["attachments", "voiceRecordings"] {
            if let rows = p[key] as? [[String: Any]] {
                p[key] = rows.map { row -> [String: Any] in
                    var r = row
                    if r["localPath"] == nil || r["localPath"] is NSNull { r["localPath"] = "" }
                    return r
                }
            }
        }
        if let card = decode(ReminderItem.self, p, decoder) { return card }
        for key in ["musicBlocks", "mediaBlocks", "cardStack", "voiceRecordings", "contentBlockLayout", "attachments"] {
            p[key] = nil
            if let card = decode(ReminderItem.self, p, decoder) { return card }
        }
        return nil
    }

    /// Android keeps the category description in a separate preference, so it is not in the record.
    private static func decodeCategory(_ payload: [String: Any], _ decoder: JSONDecoder) -> CustomCategory? {
        var p = payload
        let defaults: [String: Any] = ["description": "", "hideFromGeneralLists": false, "useDarkText": false,
                                       "iconKey": "folder", "accentStart": 0,
                                       "createdAt": (payload["id"] as? NSNumber)?.int64Value ?? EpochMillis.now]
        for (key, value) in defaults where p[key] == nil || p[key] is NSNull { p[key] = value }
        return decode(CustomCategory.self, p, decoder)
    }

    /// Android stores a mood entry flat (scope, descriptors, energy, factors, loggedAt next to id / mood);
    /// iOS nests them in `details`.
    private static func decodeMood(_ payload: [String: Any], _ decoder: JSONDecoder) -> MoodTrackerEntry? {
        var p = payload
        if p["details"] == nil {
            var details: [String: Any] = [:]
            for key in ["scope", "descriptors", "energy", "factors", "loggedAt"] {
                if let value = p[key], !(value is NSNull) { details[key] = value }
            }
            if details["loggedAt"] == nil { details["loggedAt"] = (p["id"] as? NSNumber)?.int64Value ?? EpochMillis.now }
            p["details"] = details
        }
        return decode(MoodTrackerEntry.self, p, decoder)
    }
}
