import Foundation

/// REAL worker access (AndroidBackend.ORIGIN + WorkerClient): every request carries the Firebase ID token and
/// the App Check token from RealAuth.
enum RealBackend {
    static let origin = "https://real-backend-staging.cagrigul1907.workers.dev"

    /// A non-2xx answer; `code` is the worker's `error` field when it sent one (e.g. PRO_REQUIRED).
    struct Failure: Error { let status: Int; var code: String? = nil }

    static func request(_ path: String, method: String = "GET", body: Data? = nil, timeout: TimeInterval = 30) async throws -> Data {
        guard let url = URL(string: origin + path) else { throw URLError(.badURL) }
        var request = URLRequest(url: url, timeoutInterval: timeout)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        for (key, value) in try await RealAuth.requestHeaders() { request.setValue(value, forHTTPHeaderField: key) }
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let code = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["error"] as? String
            throw Failure(status: status, code: code)
        }
        return data
    }

    /// POST a JSON object, return the decoded JSON object.
    static func post(_ path: String, _ body: [String: Any], timeout: TimeInterval = 30) async throws -> [String: Any] {
        let data = try await request(path, method: "POST", body: try JSONSerialization.data(withJSONObject: body), timeout: timeout)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw Failure(status: -1) }
        return json
    }

    /// aiServiceFailureMessage (Android): a short Turkish line for an AI / catalog request that failed.
    static func message(for error: Error) -> String {
        if error is AppCheckUnavailable { return "Cihaz doğrulaması yapılamadı. Biraz sonra tekrar dene." }
        if (error as? URLError)?.code == .userAuthenticationRequired { return "Bunun için REAL'e giriş yap." }
        if let urlError = error as? URLError, urlError.code == .notConnectedToInternet { return "İnternet bağlantısı yok." }
        guard let failure = error as? Failure else { return "Şu anda yapılamadı. Biraz sonra tekrar dene." }
        switch (failure.status, failure.code ?? "") {
        case (401, _): return "Bunun için REAL'e giriş yap."
        case (403, "PRO_REQUIRED"): return "Bu özellik REAL Pro ile açılıyor."
        case (403, "APP_CHECK_REQUIRED"): return "Cihaz doğrulaması yapılamadı. Biraz sonra tekrar dene."
        case (429, _): return "Bugünkü AI kullanım hakkın doldu. Yarın tekrar dene."
        case (503, _): return "Katalog şu anda hazır değil. Biraz sonra tekrar dene."
        default: return "Şu anda yapılamadı. Biraz sonra tekrar dene."
        }
    }
}

/// First step of iOS sync: a read-only restore of the signed-in account (SyncEngine's pull half).
/// Reads the whole change feed and replaces the device's cards, categories and moods with the account's.
/// Writing changes back from iOS and downloading photo / voice files come in the next step.
@MainActor
enum RealCloudRestore {
    struct Summary { let cards: Int; let categories: Int; let moods: Int; let categoryOrder: Bool; let files: Int }

    static func run(store: ReminderStore, life: LifeStore) async throws -> Summary {
        var cards: [Int64: ReminderItem] = [:]
        var categories: [Int64: CustomCategory] = [:]
        var moods: [Int64: MoodTrackerEntry] = [:]
        var categoryOrder: String?? = nil         // nil: never seen · .some(nil): removed · .some(value)
        var files: [Int64: [RealCloudFiles.Ref]] = [:]   // photos / files / voice notes kept in REAL's cloud, per card
        let decoder = JSONDecoder()
        var cursor: Int64 = 0
        var pages = 0
        while true {
            let data = try await RealBackend.request("/api/cloud/v1/sync/changes?cursor=\(cursor)")
            guard let page = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let records = page["records"] as? [[String: Any]] else { throw RealBackend.Failure(status: -1) }
            for record in records {
                // The home category order travels as the "ui/category_order_v1" preference ("builtin:Today|custom:12|…").
                if record["kind"] as? String == "PREFERENCE", record["key"] as? String == "ui/home_category_preview_count_v1" {
                    if let n = ((record["payload"] as? [String: Any])?["value"] as? NSNumber)?.intValue, n > 0 {
                        UserDefaults.standard.set(n, forKey: "home_category_preview_count_v1")
                    }
                    continue
                }
                if record["kind"] as? String == "PREFERENCE", record["key"] as? String == "ui/category_order_v1" {
                    categoryOrder = .some((record["payload"] as? [String: Any])?["value"] as? String)
                    continue
                }
                guard let kind = record["kind"] as? String, let key = record["key"] as? String, let id = Int64(key) else { continue }
                // A null payload is a deletion; any later record for the same key replaces the earlier one.
                guard let payload = record["payload"] as? [String: Any],
                      let json = try? JSONSerialization.data(withJSONObject: payload) else {
                    switch kind {
                    case "CARD": cards[id] = nil; files[id] = nil
                    case "CATEGORY": categories[id] = nil
                    case "MOOD": moods[id] = nil
                    default: break
                    }
                    continue
                }
                _ = json
                switch kind {
                case "CARD":
                    if let card = decodeCard(payload, decoder) { cards[id] = card }
                    files[id] = RealCloudFiles.refs(in: payload, cardId: id)
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
        // Files without a copy on this iPhone are left out for now and come back one by one as they download.
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
        if case .some(let raw) = categoryOrder {
            store.setCategoryOrder((raw ?? "").split(separator: "|").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty })
        }
        let pendingFiles = files.filter { cards[$0.key] != nil }.flatMap { $0.value }
        if !pendingFiles.isEmpty { Task { await RealCloudFiles.download(pendingFiles, into: store) } }
        return Summary(cards: restoredCards.count, categories: categories.count, moods: moods.count,
                       categoryOrder: (categoryOrder ?? nil) != nil, files: pendingFiles.count)
    }

    // MARK: Android wire format → iOS models (missing keys get the defaults the iOS models use)

    private static func decode<T: Decodable>(_ type: T.Type, _ dict: [String: Any], _ decoder: JSONDecoder) -> T? {
        guard let data = try? JSONSerialization.data(withJSONObject: dict) else { return nil }
        return try? decoder.decode(type, from: data)
    }

    /// Cloud attachments carry a blobId instead of a device path. If a rich block still does not fit the iOS
    /// model, the card is kept without it rather than dropped.
    static func decodeCard(_ payload: [String: Any], _ decoder: JSONDecoder) -> ReminderItem? {
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
    static func decodeCategory(_ payload: [String: Any], _ decoder: JSONDecoder) -> CustomCategory? {
        var p = payload
        let defaults: [String: Any] = ["description": "", "hideFromGeneralLists": false, "useDarkText": false,
                                       "iconKey": "folder", "accentStart": 0,
                                       "createdAt": (payload["id"] as? NSNumber)?.int64Value ?? EpochMillis.now]
        for (key, value) in defaults where p[key] == nil || p[key] is NSNull { p[key] = value }
        return decode(CustomCategory.self, p, decoder)
    }

    /// Android stores a mood entry flat (scope, descriptors, energy, factors, loggedAt next to id / mood);
    /// iOS nests them in `details`.
    static func decodeMood(_ payload: [String: Any], _ decoder: JSONDecoder) -> MoodTrackerEntry? {
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

/// AndroidSyncBinaries.prepareDownload (iOS): a card's photos, files and voice notes live in REAL's cloud storage as
/// blobs (/api/cloud/v1/attachments/<blobId>/content). They are saved next to the iPhone's own attachments under a
/// stable name (cloud_<blobId>) so a second restore does not download them again, then put back on their card.
@MainActor
enum RealCloudFiles {
    struct Ref {
        let cardId: Int64
        let voice: Bool
        let blobId: String
        let row: [String: Any]
    }

    static func refs(in payload: [String: Any], cardId: Int64) -> [Ref] {
        var out: [Ref] = []
        for (key, voice) in [("attachments", false), ("voiceRecordings", true)] {
            for row in (payload[key] as? [[String: Any]]) ?? [] {
                guard let blob = row["blobId"] as? String, blob.range(of: "^[A-Za-z0-9_-]{8,80}$", options: .regularExpression) != nil else { continue }
                out.append(Ref(cardId: cardId, voice: voice, blobId: blob, row: row))
            }
        }
        return out
    }

    private static func fileName(_ ref: Ref) -> String {
        let mime = ((ref.row["mimeType"] as? String) ?? (ref.voice ? "audio/mp4" : "")).lowercased()
        let ext: String
        switch mime {
        case "image/jpeg", "image/jpg": ext = "jpg"
        case "image/png": ext = "png"
        case "image/webp": ext = "webp"
        case "image/heic": ext = "heic"
        case "application/pdf": ext = "pdf"
        case "audio/mpeg": ext = "mp3"
        case "audio/mp4", "audio/aac", "audio/m4a", "audio/x-m4a": ext = "m4a"
        default: ext = ref.voice ? "m4a" : "bin"
        }
        return "cloud_\(ref.blobId).\(ext)"
    }

    static func download(_ refs: [Ref], into store: ReminderStore) async {
        for ref in refs {
            let name = fileName(ref)
            let url = AttachmentStorage.directory.appendingPathComponent(name)
            if !FileManager.default.fileExists(atPath: url.path) {
                guard let data = try? await RealBackend.request("/api/cloud/v1/attachments/\(ref.blobId)/content", timeout: 60),
                      !data.isEmpty, (try? data.write(to: url, options: .atomic)) != nil else { continue }
            }
            attach(ref, fileName: name, store: store)
        }
    }

    /// Puts the downloaded file back on its card (unless the card has gone or already has it).
    private static func attach(_ ref: Ref, fileName: String, store: ReminderStore) {
        var row = ref.row
        row["localPath"] = fileName
        row["blobId"] = nil
        guard store.reminder(ref.cardId) != nil, let data = try? JSONSerialization.data(withJSONObject: row) else { return }
        if ref.voice {
            guard let recording = try? JSONDecoder().decode(ReminderVoiceRecording.self, from: data) else { return }
            store.mutate(ref.cardId) { card in
                if let i = card.voiceRecordings.firstIndex(where: { $0.id == recording.id }) { card.voiceRecordings[i].localPath = fileName }
                else { card.voiceRecordings.append(recording) }
            }
        } else {
            guard let attachment = try? JSONDecoder().decode(ReminderAttachment.self, from: data) else { return }
            store.mutate(ref.cardId) { card in
                if let i = card.attachments.firstIndex(where: { $0.id == attachment.id }) { card.attachments[i].localPath = fileName }
                else { card.attachments.append(attachment) }
            }
        }
    }
}
