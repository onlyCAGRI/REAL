import Foundation
import CryptoKit

/// A person in a shared category.
struct SharedMember: Identifiable, Hashable {
    let uid: String
    let name: String
    let isOwner: Bool
    let access: String
    /// The person's shared profile picture (nil = none; their initial is shown).
    var pictureVersion: Int64? = nil
    var id: String { uid }
}

/// Ortak kategori on this phone (SharedCategorySync.kt). Shared cards are ordinary cards tagged with sharedCardId (so
/// lists, detail and reminders all work); this pulls each category's change feed into them and pushes local edits back
/// with revision checks (the server copy wins a conflict). Someone else's category lives here as a mirror category.
@MainActor
final class SharedCategorySync {
    static let shared = SharedCategorySync()
    private var pending: Task<Void, Never>?
    private var running = false

    private let base = "/api/categories/v1/"

    /// Runs a sync shortly (edits in a row collapse into one).
    func request(_ store: ReminderStore, delay: Double = 1.5) {
        guard RealAuth.shared.account != nil else { return }
        pending?.cancel()
        pending = Task { [weak self] in
            try? await Task.sleep(for: .seconds(delay))
            guard !Task.isCancelled else { return }
            await self?.syncNow(store)
        }
    }

    // MARK: HTTP

    private func json(_ path: String, method: String = "GET", body: [String: Any]? = nil) async throws -> [String: Any] {
        let data = try await RealBackend.request(base + path, method: method, body: body.map { try JSONSerialization.data(withJSONObject: $0) })
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }
    /// 409 / 410 answers carry the current card.
    private func call(_ path: String, method: String, body: [String: Any]) async throws -> (Int, [String: Any]) {
        do { return (200, try await json(path, method: method, body: body)) }
        catch let f as RealBackend.Failure where f.status == 409 || f.status == 410 {
            // RealBackend drops the body of a failure; fetch the card itself.
            return (f.status, [:])
        }
    }

    static func message(_ error: Error) -> String {
        guard let f = error as? RealBackend.Failure else { return RealBackend.message(for: error) }
        switch (f.status, f.code ?? "") {
        case (_, "CATEGORY_MEMBER_LIMIT"): return t("Bir kategoride en fazla 10 kişi olabilir.")
        case (_, "CATEGORY_READ_ONLY"): return t("Bu kategoride sadece görüntüleme iznin var.")
        case (_, "CATEGORY_SYNC_REQUIRED"): return t("Bu kategori henüz buluta eşitlenmedi. Android'de oluşturulan kategoriler paylaşılabilir.")
        case (401, _), (403, _): return t("Bu işlem için iznin yok.")
        case (404, _): return t("Kategoriye artık erişimin yok.")
        case (429, _): return t("Çok fazla işlem. Biraz sonra tekrar dene.")
        default: return t("Ortak kategori güncellenemedi. İnternet bağlantını kontrol et.")
        }
    }

    // MARK: State (per account, device-local)

    private struct State: Codable {
        var cursor: Int64 = 0
        var cards: [String: Entry] = [:]
        /// Local file name → server blob id (so a file uploads once and a downloaded one is not sent back).
        var blobs: [String: String] = [:]
        struct Entry: Codable { var localId: Int64; var revision: Int; var hash: String }
    }
    private func key(_ uid: String, _ sharedId: String) -> String {
        "shared_sync_" + SHA256.hash(data: Data(uid.utf8)).map { String(format: "%02x", $0) }.joined().prefix(16) + "_" + sharedId
    }
    private func load(_ uid: String, _ sharedId: String) -> State {
        (try? JSONDecoder().decode(State.self, from: UserDefaults.standard.data(forKey: key(uid, sharedId)) ?? Data())) ?? State()
    }
    private func save(_ st: State, _ uid: String, _ sharedId: String) {
        if let d = try? JSONEncoder().encode(st) { UserDefaults.standard.set(d, forKey: key(uid, sharedId)) }
    }
    private func clear(_ uid: String, _ sharedId: String) { UserDefaults.standard.removeObject(forKey: key(uid, sharedId)) }

    /// Same id for the same shared card on every device; far above timestamp ids.
    static func localId(for cardId: String) -> Int64 {
        let u = UUID(uuidString: cardId)?.uuid
        guard let u else { return 4_000_000_000_000_000 }
        let bytes = [u.0, u.1, u.2, u.3, u.4, u.5, u.6, u.7, u.8, u.9, u.10, u.11, u.12, u.13, u.14, u.15]
        var hi: UInt64 = 0, lo: UInt64 = 0
        for i in 0..<8 { hi = (hi << 8) | UInt64(bytes[i]); lo = (lo << 8) | UInt64(bytes[i + 8]) }
        let mixed = Int64(bitPattern: hi ^ lo)
        let m: Int64 = 1_000_000_000_000_000
        return 4_000_000_000_000_000 + ((mixed % m) + m) % m
    }

    // MARK: Card ⇄ payload

    /// Keys a shared card never carries (local bookkeeping and iOS-only planner fields).
    private static let localOnly = ["id", "customCategoryId", "notifiedAlertAt", "sentBy", "shareOrigin", "sharedCardId",
                                    "sharedAuthorUid", "sharedAuthorName", "durationMinutes", "people", "plannerColorIndex"]

    /// The server payload of a card. Files become blob ids; without [upload] a file that has no blob yet makes this
    /// return nil (= the card has changes to upload).
    private func outgoing(_ item: ReminderItem, _ st: inout State, sharedId: String, upload: Bool) async throws -> [String: Any]? {
        guard var o = try JSONSerialization.jsonObject(with: JSONEncoder().encode(item)) as? [String: Any] else { return nil }
        Self.localOnly.forEach { o.removeValue(forKey: $0) }
        if (o["gameBlocks"] as? [Any])?.isEmpty ?? true { o.removeValue(forKey: "gameBlocks") }
        // Ayraç: only a divider carries the flag.
        if o["isDivider"] as? Bool != true { o.removeValue(forKey: "isDivider") }
        for (key, voice) in [("attachments", false), ("voiceRecordings", true)] {
            guard var rows = o[key] as? [[String: Any]] else { continue }
            for i in rows.indices {
                if rows[i]["type"] as? String == "TEXT_SNIPPET" { rows[i]["localPath"] = nil; continue }
                guard let name = rows[i]["localPath"] as? String, !name.isEmpty else { rows[i]["localPath"] = nil; continue }
                rows[i]["localPath"] = nil
                if let blob = st.blobs[name] { rows[i]["blobId"] = blob; continue }
                let url = AttachmentStorage.directory.appendingPathComponent(name)
                guard FileManager.default.fileExists(atPath: url.path) else { continue }
                guard upload else { return nil }
                let mime = (rows[i]["mimeType"] as? String) ?? (voice ? "audio/mp4" : "application/octet-stream")
                let kind = voice ? "VOICE" : (rows[i]["type"] as? String == "IMAGE" ? "IMAGE" : (mime == "application/pdf" ? "PDF" : "FILE"))
                let blob = try await uploadFile(sharedId, url: url, kind: kind, mime: mime)
                st.blobs[name] = blob
                rows[i]["blobId"] = blob
            }
            o[key] = rows
        }
        if var stack = o["cardStack"] as? [String: Any], var cards = stack["cards"] as? [[String: Any]] {
            for i in cards.indices where !((cards[i]["imageUri"] as? String)?.hasPrefix("https://") ?? true) { cards[i]["imageUri"] = nil }
            stack["cards"] = cards; o["cardStack"] = stack
        }
        return o
    }

    private func hash(_ o: [String: Any]) -> String {
        let data = (try? JSONSerialization.data(withJSONObject: o, options: [.sortedKeys])) ?? Data()
        return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
    private func hashOf(_ item: ReminderItem, _ st: inout State, sharedId: String) async -> String? {
        (try? await outgoing(item, &st, sharedId: sharedId, upload: false))?.map(hash) ?? nil
    }

    /// A server card as a local card in [categoryId]; its files are downloaded into the app's storage.
    private func incoming(_ card: [String: Any], localId: Int64, categoryId: Int64, existing: ReminderItem?,
                          _ st: inout State, sharedId: String) async throws -> ReminderItem? {
        guard var p = card["payload"] as? [String: Any], let cardId = card["id"] as? String else { return nil }
        let known = Dictionary(st.blobs.map { ($0.value, $0.key) }, uniquingKeysWith: { a, _ in a })
        for (key, voice) in [("attachments", false), ("voiceRecordings", true)] {
            guard var rows = p[key] as? [[String: Any]] else { continue }
            for i in rows.indices {
                guard let blob = rows[i]["blobId"] as? String else { continue }
                if let name = known[blob], FileManager.default.fileExists(atPath: AttachmentStorage.directory.appendingPathComponent(name).path) {
                    rows[i]["localPath"] = name
                } else {
                    let ext = voice ? "m4a" : ((rows[i]["type"] as? String) == "IMAGE" ? "jpg" : "bin")
                    let name = "shared_\(blob).\(ext)"
                    let url = AttachmentStorage.directory.appendingPathComponent(name)
                    if !FileManager.default.fileExists(atPath: url.path) {
                        let data = try await RealBackend.request(base + "shared/\(sharedId)/assets/\(blob)/content", timeout: 60)
                        try data.write(to: url, options: .atomic)
                    }
                    rows[i]["localPath"] = name
                    st.blobs[name] = blob
                }
                rows[i]["blobId"] = nil
            }
            p[key] = rows
        }
        p["id"] = localId
        p["customCategoryId"] = categoryId
        p["sharedCardId"] = cardId
        p["sharedAuthorUid"] = card["authorUid"] as? String
        p["sharedAuthorName"] = card["authorName"] as? String
        if p["createdAt"] == nil { p["createdAt"] = (card["createdAt"] as? NSNumber)?.int64Value ?? EpochMillis.now }
        // iOS planner fields stay on this device.
        if let existing {
            if let d = existing.durationMinutes { p["durationMinutes"] = d }
            if !existing.people.isEmpty { p["people"] = existing.people }
            if let c = existing.plannerColorIndex { p["plannerColorIndex"] = c }
        }
        let data = try JSONSerialization.data(withJSONObject: p)
        return try JSONDecoder().decode(ReminderItem.self, from: data)
    }

    private func uploadFile(_ sharedId: String, url: URL, kind: String, mime: String) async throws -> String {
        let data = try Data(contentsOf: url)
        guard !data.isEmpty, data.count <= 10 * 1024 * 1024 else { throw RealBackend.Failure(status: 413) }
        let blob = UUID().uuidString.lowercased()
        let r = try await json("shared/\(sharedId)/assets/\(blob)", method: "POST", body: ["kind": kind, "mime": mime, "byte_size": data.count])
        if (r["state"] as? String) != "READY" {
            guard let u = URL(string: RealBackend.origin + base + "shared/\(sharedId)/assets/\(blob)/content") else { throw URLError(.badURL) }
            var req = URLRequest(url: u, timeoutInterval: 60)
            req.httpMethod = "PUT"
            req.httpBody = data
            for (k, v) in try await RealAuth.requestHeaders() { req.setValue(v, forHTTPHeaderField: k) }
            req.setValue(mime, forHTTPHeaderField: "Content-Type")
            let (_, response) = try await URLSession.shared.data(for: req)
            AppUpdateGate.observe(status: (response as? HTTPURLResponse)?.statusCode ?? 0)
            guard (200..<300).contains((response as? HTTPURLResponse)?.statusCode ?? 0) else { throw RealBackend.Failure(status: -1) }
        }
        return blob
    }

    // MARK: Sync

    func syncNow(_ store: ReminderStore) async {
        guard !running, let uid = RealAuth.shared.account?.uid else { return }
        running = true
        defer { running = false }
        do {
            let list = try await summaries()
            let shared = list.filter(\.collab)
            let ids = Set(shared.map(\.id))
            for s in shared where !s.isOwner { ensureMirror(store, s) }
            for mirror in store.categories where mirror.isSharedMirror && !ids.contains(mirror.sharedId ?? "") {
                store.removeCategoryFromSync(mirror.id); clear(uid, mirror.sharedId ?? "")
            }
            for own in store.categories where own.sharedRole == "owner" && !ids.contains(own.sharedId ?? "") { bringHome(store, own, uid: uid) }
            for s in shared where s.isOwner {
                let key = (s.category["id"] as? NSNumber)?.int64Value, gen = (s.category["createdAt"] as? NSNumber)?.int64Value
                if var local = store.categories.first(where: { $0.id == key && $0.createdAt == gen }), local.sharedId != s.id {
                    local.sharedId = s.id; local.sharedRole = "owner"; store.putCategoryFromSync(local)
                }
            }
            for s in shared {
                guard let local = store.categories.first(where: { $0.sharedId == s.id }) else { continue }
                try? await syncCategory(store, local, access: s.access, uid: uid)
            }
            // Cards left by a shared category that is gone here: they are personal now.
            let sharedLocal = Set(store.categories.filter(\.isShared).map(\.id))
            if store.reminders.contains(where: { $0.sharedCardId != nil && !sharedLocal.contains($0.customCategoryId ?? -1) }) {
                store.rewriteFromSync { list in
                    for i in list.indices where list[i].sharedCardId != nil && !sharedLocal.contains(list[i].customCategoryId ?? -1) {
                        list[i].sharedCardId = nil; list[i].sharedAuthorUid = nil; list[i].sharedAuthorName = nil
                    }
                }
            }
        } catch {
            // Offline or not signed in: the next round tries again.
        }
    }

    private struct Summary { let id: String; let category: [String: Any]; let isOwner: Bool; let collab: Bool; let access: String; var description: String? = nil }

    private func summaries() async throws -> [Summary] {
        var out: [Summary] = []
        var after: String? = nil
        repeat {
            let page = try await json("shared" + (after.map { "?after=\($0)" } ?? ""))
            for o in (page["items"] as? [[String: Any]]) ?? [] {
                guard let id = o["id"] as? String, let c = o["category"] as? [String: Any] else { continue }
                let owner = o["isOwner"] as? Bool ?? false
                out.append(Summary(id: id, category: c, isOwner: owner, collab: o["collab"] as? Bool ?? false,
                                   access: (o["access"] as? String) ?? (owner ? "owner" : "full"),
                                   description: o["description"] as? String))
            }
            after = page["next"] as? String
        } while after != nil
        return out
    }

    private func ensureMirror(_ store: ReminderStore, _ s: Summary) {
        let c = s.category
        let existing = store.categories.first { $0.sharedId == s.id }
        var mirror = existing ?? CustomCategory(id: CategoryRules.nextId(now: .now, existing: store.categories.map(\.id)),
                                                name: c["name"] as? String ?? "", iconKey: c["iconKey"] as? String ?? "NONE",
                                                accentStart: (c["accentStart"] as? NSNumber)?.intValue ?? 0)
        mirror.name = c["name"] as? String ?? mirror.name
        mirror.iconKey = c["iconKey"] as? String ?? mirror.iconKey
        mirror.accentStart = (c["accentStart"] as? NSNumber)?.intValue ?? mirror.accentStart
        mirror.accentEnd = (c["accentEnd"] as? NSNumber)?.intValue
        mirror.useDarkText = c["useDarkText"] as? Bool ?? false
        mirror.gradientBlendFraction = (c["gradientBlendFraction"] as? NSNumber)?.doubleValue
        mirror.customSolidColorArgb = (c["customSolidColorArgb"] as? NSNumber)?.intValue
        mirror.sharedId = s.id
        mirror.sharedRole = s.access
        // The owner's description (members read it; only the owner edits it).
        mirror.description = String((s.description ?? "").trimmingCharacters(in: .whitespacesAndNewlines).prefix(400))
        if mirror != existing { store.putCategoryFromSync(mirror) }
    }

    /// The category is private again: its shared cards become personal cards (new ids) and the bookkeeping goes.
    private func bringHome(_ store: ReminderStore, _ own: CustomCategory, uid: String) {
        store.rewriteFromSync { list in
            var used = Set(list.map(\.id))
            var next = EpochMillis.now
            for i in list.indices where list[i].customCategoryId == own.id && list[i].sharedCardId != nil {
                while used.contains(next) { next += 1 }
                used.insert(next)
                list[i].id = next
                list[i].sharedCardId = nil; list[i].sharedAuthorUid = nil; list[i].sharedAuthorName = nil
            }
        }
        var c = own; c.sharedId = nil; c.sharedRole = nil; c.sharedOwnerName = nil
        store.putCategoryFromSync(c)
        if let id = own.sharedId { clear(uid, id) }
    }

    /// Pull then push one category.
    private func syncCategory(_ store: ReminderStore, _ category: CustomCategory, access: String, uid: String) async throws {
        guard let sharedId = category.sharedId else { return }
        var st = load(uid, sharedId)
        let readOnly = access == "view"
        // Pull.
        while true {
            let page = try await json("shared/\(sharedId)/cards?cursor=\(st.cursor)")
            for card in (page["cards"] as? [[String: Any]]) ?? [] {
                guard let cardId = card["id"] as? String else { continue }
                let revision = (card["revision"] as? NSNumber)?.intValue ?? 1
                let entry = st.cards[cardId]
                let localId = entry?.localId ?? Self.localId(for: cardId)
                // An adopted card is linked to the owner's personal copy restored from the account (fresh install):
                // without this the copy looked new and went up again as a duplicate.
                let sourceKey = card["sourceKey"] as? String
                let current = store.reminders.first { $0.id == localId || $0.sharedCardId == cardId }
                    ?? sourceKey.flatMap { key in store.reminders.first { $0.sharedCardId == nil && $0.customCategoryId == category.id && String($0.id) == key } }
                if card["deleted"] as? Bool == true {
                    if let current { store.removeFromSync(current.id) }
                    st.cards[cardId] = nil; continue
                }
                if current != nil, let entry, entry.revision == revision { continue }
                if !readOnly, let current, let entry, await hashOf(current, &st, sharedId: sharedId) != entry.hash { continue }
                guard let item = try await incoming(card, localId: current?.id ?? localId, categoryId: category.id, existing: current, &st, sharedId: sharedId) else { continue }
                store.putFromSync(item)
                let freshHash = await hashOf(item, &st, sharedId: sharedId) ?? ""
                st.cards[cardId] = .init(localId: item.id, revision: revision, hash: freshHash)
            }
            st.cursor = (page["cursor"] as? NSNumber)?.int64Value ?? st.cursor
            save(st, uid, sharedId)
            if page["hasMore"] as? Bool != true { break }
        }
        if readOnly { return }
        // Push: new cards, edits, deletions.
        let locals = store.reminders.filter { $0.customCategoryId == category.id }
        for item in locals {
            if item.sharedCardId == nil {
                let cardId = UUID().uuidString.lowercased()
                guard let payload = try await outgoing(item, &st, sharedId: sharedId, upload: true) else { continue }
                guard let r = try? await json("shared/\(sharedId)/cards", method: "POST", body: ["id": cardId, "payload": payload]),
                      let card = r["card"] as? [String: Any] else { continue }
                var updated = item
                updated.sharedCardId = cardId
                updated.sharedAuthorUid = card["authorUid"] as? String
                updated.sharedAuthorName = card["authorName"] as? String
                store.putFromSync(updated)
                let freshHash = await hashOf(updated, &st, sharedId: sharedId) ?? ""
                st.cards[cardId] = .init(localId: updated.id, revision: (card["revision"] as? NSNumber)?.intValue ?? 1, hash: freshHash)
                continue
            }
            guard let cardId = item.sharedCardId, let entry = st.cards[cardId],
                  let payload = try await outgoing(item, &st, sharedId: sharedId, upload: true) else { continue }
            let h = hash(payload)
            if h == entry.hash { continue }
            let (status, r) = try await call("shared/\(sharedId)/cards/\(cardId)", method: "PATCH", body: ["baseRevision": entry.revision, "payload": payload])
            switch status {
            case 200:
                st.cards[cardId] = .init(localId: item.id, revision: ((r["card"] as? [String: Any])?["revision"] as? NSNumber)?.intValue ?? entry.revision + 1, hash: h)
            case 409:
                st.cursor = 0 // the next pull brings the winner
                st.cards[cardId] = .init(localId: item.id, revision: -1, hash: "")
            case 410:
                store.removeFromSync(item.id); st.cards[cardId] = nil
            default: break
            }
        }
        let localIds = Set(locals.map(\.id))
        for (cardId, entry) in st.cards where !localIds.contains(entry.localId) {
            let (status, _) = try await call("shared/\(sharedId)/cards/\(cardId)", method: "DELETE", body: ["baseRevision": entry.revision])
            if status == 200 || status == 410 {
                st.cards[cardId] = nil
                // Moved to another category: it is a personal card now.
                if store.reminders.contains(where: { $0.id == entry.localId && $0.sharedCardId == cardId }) {
                    store.rewriteFromSync { list in
                        if let i = list.firstIndex(where: { $0.id == entry.localId }) {
                            list[i].sharedCardId = nil; list[i].sharedAuthorUid = nil; list[i].sharedAuthorName = nil
                        }
                    }
                }
            } else if status == 409 { st.cursor = 0 }
        }
        save(st, uid, sharedId)
    }

    // MARK: Sharing actions

    /// Owner shares [category] with [personUid]: the first time, the category becomes collaborative and its cards move
    /// to the server ([progress] = cards moved so far).
    func share(_ store: ReminderStore, category: CustomCategory, personUid: String, access: String,
               progress: @escaping (Int) -> Void) async throws {
        guard let uid = RealAuth.shared.account?.uid else { throw URLError(.userAuthenticationRequired) }
        var sharedId = category.sharedId
        if sharedId == nil {
            let r = try await json("shared", method: "POST", body: ["categoryKey": String(category.id), "generation": category.createdAt, "link": ""])
            guard let id = (r["item"] as? [String: Any])?["id"] as? String else { throw RealBackend.Failure(status: -1) }
            sharedId = id
            _ = try await json("shared/\(id)/collab", method: "POST", body: [:])
            var c = store.category(category.id) ?? category
            c.sharedId = id; c.sharedRole = "owner"
            store.putCategoryFromSync(c)
            var st = load(uid, id)
            var after: String? = nil
            var moved = 0
            repeat {
                let page = try await json("shared/\(id)/adopt", method: "POST", body: after.map { ["after": $0] as [String: Any] } ?? [:])
                for a in (page["adopted"] as? [[String: Any]]) ?? [] {
                    guard let sourceKey = a["sourceKey"] as? String, let card = a["card"] as? [String: Any], let cardId = card["id"] as? String else { continue }
                    let item: ReminderItem?
                    if var local = store.reminders.first(where: { String($0.id) == sourceKey }) {
                        rememberBlobs(local, card["payload"] as? [String: Any] ?? [:], &st)
                        local.sharedCardId = cardId
                        local.sharedAuthorUid = card["authorUid"] as? String
                        local.sharedAuthorName = card["authorName"] as? String
                        item = local
                    } else {
                        item = try await incoming(card, localId: Self.localId(for: cardId), categoryId: category.id, existing: nil, &st, sharedId: id)
                    }
                    guard let item else { continue }
                    store.putFromSync(item)
                    let freshHash = await hashOf(item, &st, sharedId: id) ?? ""
                    st.cards[cardId] = .init(localId: item.id, revision: (card["revision"] as? NSNumber)?.intValue ?? 1, hash: freshHash)
                    moved += 1
                    progress(moved)
                }
                save(st, uid, id)
                after = page["next"] as? String
            } while after != nil
        }
        _ = try await json("shared/\(sharedId!)/members", method: "POST", body: ["uid": personUid, "access": access])
        request(store, delay: 0.2)
    }

    private func rememberBlobs(_ local: ReminderItem, _ payload: [String: Any], _ st: inout State) {
        func pair(_ names: [String], _ rows: [[String: Any]]?) {
            guard let rows else { return }
            for (i, name) in names.enumerated() where !name.isEmpty {
                if let blob = rows[safe: i]?["blobId"] as? String { st.blobs[name] = blob }
            }
        }
        pair(local.attachments.map(\.localPath), payload["attachments"] as? [[String: Any]])
        pair(local.voiceRecordings.map(\.localPath), payload["voiceRecordings"] as? [[String: Any]])
    }

    /// Owner, after the last person left: the cards come back home and the shared state goes away.
    func release(_ store: ReminderStore, category: CustomCategory) async throws {
        guard let uid = RealAuth.shared.account?.uid, let id = category.sharedId else { return }
        _ = try await json("shared/\(id)", method: "DELETE", body: [:])
        bringHome(store, category, uid: uid)
    }

    /// A member leaves; with [keepCopy] the category and its cards stay as their own private copy.
    func leave(_ store: ReminderStore, category: CustomCategory, keepCopy: Bool) async throws {
        guard let uid = RealAuth.shared.account?.uid, let id = category.sharedId else { return }
        _ = try await json("shared/\(id)/members/" + (uid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? uid), method: "DELETE", body: [:])
        if keepCopy {
            store.rewriteFromSync { list in
                for i in list.indices where list[i].customCategoryId == category.id && list[i].sharedCardId != nil {
                    list[i].sharedCardId = nil; list[i].sharedAuthorUid = nil; list[i].sharedAuthorName = nil
                }
            }
            var c = category; c.sharedId = nil; c.sharedRole = nil; c.sharedOwnerName = nil
            store.putCategoryFromSync(c)
        } else {
            store.removeCategoryFromSync(category.id)
        }
        clear(uid, id)
    }

    func members(_ category: CustomCategory) async throws -> [SharedMember] {
        guard let id = category.sharedId else { return [] }
        let r = try await json("shared/\(id)")
        return ((r["members"] as? [[String: Any]]) ?? []).compactMap { m in
            guard let uid = m["uid"] as? String else { return nil }
            let owner = m["isOwner"] as? Bool ?? false
            return SharedMember(uid: uid, name: m["name"] as? String ?? "REAL user", isOwner: owner,
                                access: owner ? "owner" : ((m["access"] as? String) ?? "full"),
                                pictureVersion: (m["pictureVersion"] as? NSNumber)?.int64Value)
        }
    }

    func setAccess(_ category: CustomCategory, uid: String, access: String) async throws {
        guard let id = category.sharedId else { return }
        _ = try await json("shared/\(id)/members/" + (uid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? uid), method: "PATCH", body: ["access": access])
    }

    /// Owner removes someone; true when nobody is left (the caller then releases the category).
    func removeMember(_ category: CustomCategory, uid: String) async throws -> Bool {
        guard let id = category.sharedId else { return false }
        _ = try await json("shared/\(id)/members/" + (uid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? uid), method: "DELETE", body: [:])
        return try await members(category).allSatisfy(\.isOwner)
    }

    func addMember(_ category: CustomCategory, uid: String, access: String) async throws {
        guard let id = category.sharedId else { return }
        _ = try await json("shared/\(id)/members", method: "POST", body: ["uid": uid, "access": access])
    }

    /// Kişilerim (REAL connections) to pick from.
    func connections() async throws -> [SharedMember] {
        let data = try await RealBackend.request("/api/connections/v1/me")
        let o = (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
        return ((o["connections"] as? [[String: Any]]) ?? []).compactMap { c in
            guard let uid = c["uid"] as? String else { return nil }
            return SharedMember(uid: uid, name: c["name"] as? String ?? "REAL user", isOwner: false, access: "",
                                pictureVersion: (c["pictureVersion"] as? NSNumber)?.int64Value)
        }
    }
}
