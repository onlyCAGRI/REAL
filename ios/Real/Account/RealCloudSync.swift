import Foundation
import CryptoKit

/// Two-way REAL cloud sync on iOS (SyncEngine.kt): pull the account's change feed, merge it with this iPhone's cards
/// and push what changed here, with the same records Android uses (CARD, CATEGORY, PLANNER, MOOD and the category
/// order / description preferences). Each record keeps the last synced payload as its base; a record changed on both
/// sides takes the cloud copy. Photos, files and voice notes travel as attachments of their card.
@MainActor
final class RealCloudSync {
    static let shared = RealCloudSync()
    private var running = false
    private var pending: Task<Void, Never>?
    private(set) var lastError: String?
    private(set) var lastSuccess: Date?

    /// Runs a sync shortly (edits in a row collapse into one).
    func request(_ store: ReminderStore, _ life: LifeStore, delay: Double = 2) {
        guard RealAuth.shared.account != nil else { return }
        pending?.cancel()
        pending = Task { [weak self] in
            try? await Task.sleep(for: .seconds(delay))
            guard !Task.isCancelled else { return }
            await self?.run(store, life)
        }
    }

    // MARK: State

    private struct Rec: Codable {
        var id: String
        var revision: Int64
        /// Canonical JSON of the last synced payload; nil = deleted / never had one.
        var base: String?
        /// The last synced payload as JSON text: unknown Android fields are kept from it when this iPhone writes.
        var raw: String?
    }
    private struct State: Codable {
        var cursor: Int64 = 0
        var records: [String: Rec] = [:]      // "KIND/key"
        /// Local file name → blob id.
        var blobs: [String: String] = [:]
        /// PLANNER details that arrived before their card.
        var planner: [String: String] = [:]
    }
    private var stateURL: URL? {
        guard let uid = RealAuth.shared.account?.uid else { return nil }
        let hash = SHA256.hash(data: Data(uid.utf8)).map { String(format: "%02x", $0) }.joined().prefix(16)
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent("cloud-sync-\(hash).json")
    }
    private func loadState() -> State {
        guard let url = stateURL, let data = try? Data(contentsOf: url) else { return State() }
        return (try? JSONDecoder().decode(State.self, from: data)) ?? State()
    }
    private func saveState(_ st: State) {
        guard let url = stateURL, let data = try? JSONEncoder().encode(st) else { return }
        try? data.write(to: url, options: .atomic)
    }
    /// Starts over from the beginning of the feed (e.g. "Kartları hesaptan yeniden yükle").
    func reset() { if let url = stateURL { try? FileManager.default.removeItem(at: url) } }

    // MARK: Run

    /// Waits for a run already in progress, then runs once more (before sharing, so the server has the latest copy).
    func runFully(_ store: ReminderStore, _ life: LifeStore) async {
        while running { try? await Task.sleep(for: .milliseconds(200)) }
        await run(store, life)
    }

    func run(_ store: ReminderStore, _ life: LifeStore) async {
        guard !running, RealAuth.shared.account != nil else { return }
        running = true
        defer { running = false }
        var st = loadState()
        do {
            try await pull(store, life, &st)
            saveState(st)
            try await push(store, life, &st)
            saveState(st)
            lastError = nil
            lastSuccess = Date()
        } catch {
            saveState(st)
            lastError = RealBackend.message(for: error)
        }
    }

    private func pull(_ store: ReminderStore, _ life: LifeStore, _ st: inout State) async throws {
        var pages = 0
        while pages < 500 {
            let data = try await RealBackend.request("/api/cloud/v1/sync/changes?cursor=\(st.cursor)")
            guard let page = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw RealBackend.Failure(status: -1) }
            for record in (page["records"] as? [[String: Any]]) ?? [] { receive(record, store, life, &st) }
            let next = (page["cursor"] as? NSNumber)?.int64Value ?? st.cursor
            let more = page["hasMore"] as? Bool ?? false
            st.cursor = max(st.cursor, next)
            pages += 1
            if !more || next <= 0 { break }
        }
        // Files of cards that arrived without them on this iPhone.
        let missing = store.reminders.flatMap { card -> [RealCloudFiles.Ref] in
            guard let raw = st.records["CARD/\(card.id)"]?.raw, let payload = Self.object(raw) else { return [] }
            return RealCloudFiles.refs(in: payload, cardId: card.id).filter { ref in
                !card.attachments.contains { $0.id == (ref.row["id"] as? String) } &&
                !card.voiceRecordings.contains { $0.id == (ref.row["id"] as? String) }
            }
        }
        if !missing.isEmpty { Task { await RealCloudFiles.download(missing, into: store) } }
    }

    /// One remote record: three-way merge against this iPhone's current value.
    private func receive(_ record: [String: Any], _ store: ReminderStore, _ life: LifeStore, _ st: inout State) {
        guard let id = record["id"] as? String, let kind = record["kind"] as? String, let key = record["key"] as? String else { return }
        let docKey = kind + "/" + key
        let payload = record["payload"] as? [String: Any]
        let remote = payload.map(Self.canonical)
        let revision = (record["revision"] as? NSNumber)?.int64Value ?? 0
        let old = st.records[docKey]
        let local = export(docKey, store, life, base: old?.raw, &st).map(Self.canonical)
        let base = old?.base
        var take = false
        if local == remote { take = false }
        else if local == base { take = true }
        else if remote == base { take = false }         // only this iPhone changed it: it goes up
        else { take = true }                            // changed on both sides: the cloud copy wins
        if take { apply(kind: kind, key: key, payload: payload, store, life, &st) }
        st.records[docKey] = Rec(id: id, revision: revision, base: remote,
                                 raw: payload.flatMap { try? JSONSerialization.data(withJSONObject: $0) }.flatMap { String(data: $0, encoding: .utf8) })
    }

    private func push(_ store: ReminderStore, _ life: LifeStore, _ st: inout State) async throws {
        var docs: [String: [String: Any]] = [:]
        for card in store.reminders where card.id >= 0 && card.sharedCardId == nil {
            docs["CARD/\(card.id)"] = cardPayload(card, base: st.records["CARD/\(card.id)"]?.raw, &st, upload: false)
            if let p = plannerPayload(card, base: st.records["PLANNER/\(card.id)"]?.raw) { docs["PLANNER/\(card.id)"] = p }
        }
        for c in store.categories where !c.isSharedMirror {
            docs["CATEGORY/\(c.id)"] = categoryPayload(c, base: st.records["CATEGORY/\(c.id)"]?.raw)
            let dk = "PREFERENCE/description/custom_\(c.id)_\(c.createdAt)"
            if !c.description.isEmpty || st.records[dk]?.base != nil { docs[dk] = ["value": c.description.isEmpty ? NSNull() : c.description] }
        }
        for m in life.moods { docs["MOOD/\(m.id)"] = moodPayload(m, base: st.records["MOOD/\(m.id)"]?.raw) }
        let order = store.categoryOrderKeys.joined(separator: "|")
        if !order.isEmpty || st.records["PREFERENCE/ui/category_order_v1"]?.base != nil {
            docs["PREFERENCE/ui/category_order_v1"] = ["value": order.isEmpty ? NSNull() : order]
        }
        let preview = UserDefaults.standard.integer(forKey: "home_category_preview_count_v1")
        if preview > 0 { docs["PREFERENCE/ui/home_category_preview_count_v1"] = ["value": preview] }

        // Everything this account had that is gone here is deleted (only kinds this iPhone writes).
        let written: Set<String> = ["CARD", "CATEGORY", "PLANNER", "MOOD"]
        var keys = Set(docs.keys)
        for (k, rec) in st.records where rec.base != nil && written.contains(String(k.split(separator: "/").first ?? "")) { keys.insert(k) }

        for docKey in keys.sorted(by: { Self.order($0) < Self.order($1) }) {
            var payload = docs[docKey]
            let rec = st.records[docKey]
            if payload.map(Self.canonical) == rec?.base { continue }
            let parts = docKey.split(separator: "/", maxSplits: 1).map(String.init)
            guard parts.count == 2 else { continue }
            let kind = parts[0], key = parts[1]
            let id = rec?.id ?? UUID().uuidString.lowercased()
            if kind == "CARD" {
                // The card is a cloud resource; its files are attachments of it.
                try await ensureResource(id, title: (payload?["text"] as? String) ?? "Deleted REAL")
                if let card = store.reminders.first(where: { "CARD/\($0.id)" == docKey }) {
                    payload = cardPayload(card, base: rec?.raw, &st, upload: true, resourceId: id)
                    if let p = payload { try await uploadFiles(p, resourceId: id, &st) }
                }
            }
            var body: [String: Any] = ["kind": kind, "key": key, "revision": rec?.revision ?? 0, "schema": 1]
            body["payload"] = payload ?? NSNull()
            do {
                let data = try await RealBackend.request("/api/cloud/v1/sync/records/\(id)", method: "PUT",
                                                         body: try JSONSerialization.data(withJSONObject: body))
                if let accepted = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    let p = accepted["payload"] as? [String: Any]
                    st.records[docKey] = Rec(id: id, revision: (accepted["revision"] as? NSNumber)?.int64Value ?? ((rec?.revision ?? 0) + 1),
                                             base: p.map(Self.canonical),
                                             raw: p.flatMap { try? JSONSerialization.data(withJSONObject: $0) }.flatMap { String(data: $0, encoding: .utf8) })
                }
            } catch let f as RealBackend.Failure where f.status == 409 {
                // Someone else wrote it first: take theirs (next run pushes again if this iPhone still differs).
                if let data = try? await RealBackend.request("/api/cloud/v1/sync/records/\(id)"),
                   let current = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    receive(current, store, life, &st)
                }
            }
        }
    }

    /// Categories before their cards, cards before their planner details.
    private static func order(_ docKey: String) -> Int {
        if docKey.hasPrefix("CATEGORY/") { return 0 }
        if docKey.hasPrefix("CARD/") { return 1 }
        if docKey.hasPrefix("PLANNER/") { return 2 }
        return 3
    }

    // MARK: Cloud resources and files

    private func ensureResource(_ id: String, title: String) async throws {
        let body: [String: Any] = ["id": id, "kind": "CARD", "title": String((title.isEmpty ? "REAL Card" : title).prefix(10_000)), "completed": false]
        do { _ = try await RealBackend.post("/api/cloud/v1/resources", body) }
        catch let f as RealBackend.Failure where f.status == 409 {}
    }

    private func uploadFiles(_ payload: [String: Any], resourceId: String, _ st: inout State) async throws {
        for (key, voice) in [("attachments", false), ("voiceRecordings", true)] {
            for row in (payload[key] as? [[String: Any]]) ?? [] {
                guard let blob = row["blobId"] as? String,
                      let name = st.blobs.first(where: { $0.value == blob })?.key else { continue }
                let url = AttachmentStorage.directory.appendingPathComponent(name)
                guard let data = try? Data(contentsOf: url), !data.isEmpty else { continue }
                // Already uploaded?
                if let meta = try? await RealBackend.request("/api/cloud/v1/attachments/\(blob)"),
                   let a = (try? JSONSerialization.jsonObject(with: meta) as? [String: Any])?["attachment"] as? [String: Any],
                   a["state"] as? String == "READY" { continue }
                let mime = (row["mimeType"] as? String) ?? (voice ? "audio/mp4" : "application/octet-stream")
                let wire = ["image/jpeg", "image/png", "image/webp", "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/mpeg",
                            "application/pdf", "application/octet-stream"].contains(mime) ? mime : "application/octet-stream"
                let kind = voice ? "VOICE" : (row["type"] as? String == "IMAGE" ? "IMAGE" : (wire == "application/pdf" ? "PDF" : "FILE"))
                do {
                    _ = try await RealBackend.post("/api/cloud/v1/resources/\(resourceId)/attachments",
                                                   ["id": blob, "kind": kind, "mime": wire, "byte_size": data.count])
                } catch let f as RealBackend.Failure where f.status == 409 {}
                guard let u = URL(string: RealBackend.origin + "/api/cloud/v1/attachments/\(blob)/content") else { continue }
                var req = URLRequest(url: u, timeoutInterval: 60)
                req.httpMethod = "PUT"
                req.httpBody = data
                for (k, v) in try await RealAuth.requestHeaders() { req.setValue(v, forHTTPHeaderField: k) }
                req.setValue(wire, forHTTPHeaderField: "Content-Type")
                let (_, response) = try await URLSession.shared.data(for: req)
                guard (200..<300).contains((response as? HTTPURLResponse)?.statusCode ?? 0) else { throw RealBackend.Failure(status: -1) }
            }
        }
    }

    /// The blob id of a local file: a downloaded cloud file carries it in its name; a new one gets a fresh id.
    private func blob(for name: String, _ st: inout State, create: Bool) -> String? {
        if let b = st.blobs[name] { return b }
        if name.hasPrefix("cloud_") {
            let b = String(name.dropFirst(6).split(separator: ".").first ?? "")
            if !b.isEmpty { st.blobs[name] = b; return b }
        }
        guard create, FileManager.default.fileExists(atPath: AttachmentStorage.directory.appendingPathComponent(name).path) else { return nil }
        let b = UUID().uuidString.lowercased()
        st.blobs[name] = b
        return b
    }

    // MARK: Export (this iPhone → wire format, keeping fields only Android knows)

    private static let cardKeys = ["text", "createdAt", "updatedAt", "completedAt", "isImportant", "alertAt", "plannedDateEpochDay",
        "quick24HourUntilAt", "accentStart", "accentEnd", "repeatRule", "anchorAlertAt", "customCategoryId", "isChecklist",
        "checklistItems", "sendImmediateNotification", "referenceUrl", "gradientBlendFraction", "customSolidColorArgb",
        "useDarkText", "journal", "mood", "location", "cardStack", "musicBlocks", "mediaBlocks", "repeatWeekdaysMask",
        "repeatUntilEpochDay", "contentBlockOrder", "contentBlockLayout"]
    /// Android leaves these out when they are empty / unset.
    private static let omitWhenEmpty: Set<String> = ["endAt", "earlyAlertMinutes", "repeatSkippedEpochDays"]

    private func cardPayload(_ card: ReminderItem, base: String?, _ st: inout State, upload: Bool, resourceId: String? = nil) -> [String: Any] {
        var o = base.flatMap(Self.object) ?? [:]
        let mine = (try? JSONSerialization.jsonObject(with: JSONEncoder().encode(card)) as? [String: Any]) ?? [:]
        o["id"] = card.id
        for key in Self.cardKeys { o[key] = mine[key] ?? NSNull() }
        for key in Self.omitWhenEmpty {
            if let v = mine[key], !((v as? [Any])?.isEmpty ?? false) { o[key] = v } else { o.removeValue(forKey: key) }
        }
        // Files: a local name becomes the blob id; a cloud file not downloaded here yet stays as the cloud has it.
        let baseObject = base.flatMap(Self.object)
        for key in ["attachments", "voiceRecordings"] {
            var rows: [[String: Any]] = []
            for var row in (mine[key] as? [[String: Any]]) ?? [] {
                if row["type"] as? String == "TEXT_SNIPPET" { row.removeValue(forKey: "localPath"); rows.append(row); continue }
                let name = row["localPath"] as? String ?? ""
                row.removeValue(forKey: "localPath")
                if let b = blob(for: name, &st, create: upload) { row["blobId"] = b }
                else if let old = (baseObject?[key] as? [[String: Any]])?.first(where: { ($0["id"] as? String) == (row["id"] as? String) }),
                        let b = old["blobId"] { row["blobId"] = b }
                else if !upload { row["blobId"] = "pending:\(name)" } // makes the card look changed until it uploads
                rows.append(row)
            }
            let localIds = Set(rows.compactMap { $0["id"] as? String })
            for old in (baseObject?[key] as? [[String: Any]]) ?? [] {
                guard let id = old["id"] as? String, !localIds.contains(id), let b = old["blobId"] as? String else { continue }
                // Never downloaded here: keep it (it was not removed on this iPhone).
                if !st.blobs.values.contains(b) && !FileManager.default.fileExists(atPath: AttachmentStorage.directory.appendingPathComponent("cloud_" + b).path) {
                    rows.append(old)
                }
            }
            o[key] = rows
        }
        if var stack = o["cardStack"] as? [String: Any], var cards = stack["cards"] as? [[String: Any]] {
            let oldCards = ((baseObject?["cardStack"] as? [String: Any])?["cards"] as? [[String: Any]]) ?? []
            for i in cards.indices where !((cards[i]["imageUri"] as? String)?.hasPrefix("https://") ?? true) {
                cards[i].removeValue(forKey: "imageUri")
                if let old = oldCards.first(where: { ($0["id"] as? String) == (cards[i]["id"] as? String) }), let b = old["blobId"] { cards[i]["blobId"] = b }
            }
            stack["cards"] = cards; o["cardStack"] = stack
        }
        for key in ["notifiedAlertAt", "durationMinutes", "people", "plannerColorIndex", "sharedCardId", "sharedAuthorUid", "sharedAuthorName"] {
            o.removeValue(forKey: key)
        }
        return o
    }

    private func plannerPayload(_ card: ReminderItem, base: String?) -> [String: Any]? {
        guard card.durationMinutes != nil || !card.people.isEmpty || card.plannerColorIndex != nil || base != nil else { return nil }
        var o = base.flatMap(Self.object) ?? [:]
        o["reminderId"] = card.id
        o["durationMinutes"] = card.durationMinutes ?? NSNull()
        o["people"] = card.people
        o["colorIndex"] = card.plannerColorIndex ?? 0
        return o
    }

    private func categoryPayload(_ c: CustomCategory, base: String?) -> [String: Any] {
        var o = base.flatMap(Self.object) ?? [:]
        o["id"] = c.id
        o["name"] = c.name
        o["iconKey"] = c.iconKey
        o["accentStart"] = c.accentStart
        o["accentEnd"] = c.accentEnd ?? NSNull()
        o["hideFromGeneralLists"] = c.hideFromGeneralLists
        o["useDarkText"] = c.useDarkText
        o["gradientBlendFraction"] = c.gradientBlendFraction ?? NSNull()
        o["customSolidColorArgb"] = c.customSolidColorArgb ?? NSNull()
        o["createdAt"] = c.createdAt
        for key in ["description", "showInCalendar", "sharedId", "sharedRole", "sharedOwnerName"] { o.removeValue(forKey: key) }
        return o
    }

    /// Android stores a mood entry flat.
    private func moodPayload(_ m: MoodTrackerEntry, base: String?) -> [String: Any] {
        var o = base.flatMap(Self.object) ?? [:]
        let details = (try? JSONSerialization.jsonObject(with: JSONEncoder().encode(m.details)) as? [String: Any]) ?? [:]
        o["id"] = m.id
        o["mood"] = m.mood.rawValue
        for key in ["scope", "descriptors", "energy", "factors", "loggedAt"] { o[key] = details[key] ?? NSNull() }
        o.removeValue(forKey: "details")
        return o
    }

    /// The current wire value of one record on this iPhone (nil = it does not exist here).
    private func export(_ docKey: String, _ store: ReminderStore, _ life: LifeStore, base: String?, _ st: inout State) -> [String: Any]? {
        let parts = docKey.split(separator: "/", maxSplits: 1).map(String.init)
        guard parts.count == 2 else { return nil }
        let (kind, key) = (parts[0], parts[1])
        switch kind {
        case "CARD":
            guard let id = Int64(key), let card = store.reminder(id), card.sharedCardId == nil else { return nil }
            return cardPayload(card, base: base, &st, upload: false)
        case "PLANNER":
            guard let id = Int64(key), let card = store.reminder(id) else { return nil }
            return plannerPayload(card, base: base)
        case "CATEGORY":
            guard let id = Int64(key), let c = store.category(id), !c.isSharedMirror else { return nil }
            return categoryPayload(c, base: base)
        case "MOOD":
            guard let id = Int64(key), let m = life.moods.first(where: { $0.id == id }) else { return nil }
            return moodPayload(m, base: base)
        case "PREFERENCE":
            if key == "ui/category_order_v1" {
                let order = store.categoryOrderKeys.joined(separator: "|")
                return order.isEmpty ? nil : ["value": order]
            }
            if key == "ui/home_category_preview_count_v1" {
                let n = UserDefaults.standard.integer(forKey: "home_category_preview_count_v1")
                return n > 0 ? ["value": n] : nil
            }
            if key.hasPrefix("description/custom_"), let c = store.categories.first(where: { "description/custom_\($0.id)_\($0.createdAt)" == key }) {
                return c.description.isEmpty ? nil : ["value": c.description]
            }
            return base.flatMap(Self.object) // preferences this iPhone does not use are left as they are
        default:
            return base.flatMap(Self.object)
        }
    }

    // MARK: Import (wire format → this iPhone)

    private func apply(kind: String, key: String, payload: [String: Any]?, _ store: ReminderStore, _ life: LifeStore, _ st: inout State) {
        let decoder = JSONDecoder()
        switch kind {
        case "CARD":
            guard let id = Int64(key) else { return }
            guard let payload, var card = RealCloudRestore.decodeCard(payload, decoder) else { store.removeFromSync(id); return }
            let existing = store.reminder(id)
            // Files this iPhone already has keep their local name; others download after the pull.
            card.attachments = card.attachments.compactMap { a in
                if a.type == .textSnippet { return a }
                let row = (payload["attachments"] as? [[String: Any]])?.first { ($0["id"] as? String) == a.id }
                guard let b = row?["blobId"] as? String, let name = localName(for: b, st) else { return existing?.attachments.first { $0.id == a.id } }
                var copy = a; copy.localPath = name; return copy
            }
            card.voiceRecordings = card.voiceRecordings.compactMap { r in
                let row = (payload["voiceRecordings"] as? [[String: Any]])?.first { ($0["id"] as? String) == r.id }
                guard let b = row?["blobId"] as? String, let name = localName(for: b, st) else { return existing?.voiceRecordings.first { $0.id == r.id } }
                var copy = r; copy.localPath = name; return copy
            }
            // Device-local / planner fields stay.
            if let existing {
                card.durationMinutes = existing.durationMinutes
                card.people = existing.people
                card.plannerColorIndex = existing.plannerColorIndex
                if existing.alertAt == card.alertAt { card.notifiedAlertAt = existing.notifiedAlertAt }
            }
            if let raw = st.planner["\(id)"], let p = Self.object(raw) { applyPlanner(p, to: &card); st.planner["\(id)"] = nil }
            store.putFromSync(card)
        case "PLANNER":
            guard let id = Int64(key) else { return }
            guard var card = store.reminder(id) else {
                if let payload, let data = try? JSONSerialization.data(withJSONObject: payload) { st.planner[key] = String(data: data, encoding: .utf8) }
                return
            }
            if let payload { applyPlanner(payload, to: &card) } else { card.durationMinutes = nil; card.people = []; card.plannerColorIndex = nil }
            store.putFromSync(card)
        case "CATEGORY":
            guard let id = Int64(key) else { return }
            guard let payload, var c = RealCloudRestore.decodeCategory(payload, decoder) else { store.removeCategoryOnlyFromSync(id); return }
            if let old = store.category(id) {
                c.description = old.description; c.showInCalendar = old.showInCalendar
                c.sharedId = old.sharedId; c.sharedRole = old.sharedRole; c.sharedOwnerName = old.sharedOwnerName
            }
            store.putCategoryFromSync(c)
        case "MOOD":
            guard let id = Int64(key) else { return }
            var moods = life.moods.filter { $0.id != id }
            if let payload, let m = RealCloudRestore.decodeMood(payload, decoder) { moods.append(m) }
            life.replaceAll(goal: life.goalMl, entries: life.entries, moods: moods.sorted { $0.id > $1.id })
        case "PREFERENCE":
            let value = payload?["value"]
            if key == "ui/category_order_v1" {
                store.setCategoryOrderFromSync(((value as? String) ?? "").split(separator: "|").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty })
            } else if key == "ui/home_category_preview_count_v1", let n = (value as? NSNumber)?.intValue, n > 0 {
                UserDefaults.standard.set(n, forKey: "home_category_preview_count_v1")
            } else if key.hasPrefix("description/custom_"),
                      var c = store.categories.first(where: { "description/custom_\($0.id)_\($0.createdAt)" == key }) {
                c.description = (value as? String) ?? ""
                store.putCategoryFromSync(c)
            }
        default:
            break // water comes later
        }
    }

    private func applyPlanner(_ p: [String: Any], to card: inout ReminderItem) {
        card.durationMinutes = (p["durationMinutes"] as? NSNumber)?.intValue
        card.people = (p["people"] as? [String]) ?? []
        card.plannerColorIndex = (p["colorIndex"] as? NSNumber)?.intValue
    }

    private func localName(for blob: String, _ st: State) -> String? {
        if let name = st.blobs.first(where: { $0.value == blob })?.key,
           FileManager.default.fileExists(atPath: AttachmentStorage.directory.appendingPathComponent(name).path) { return name }
        let files = (try? FileManager.default.contentsOfDirectory(atPath: AttachmentStorage.directory.path)) ?? []
        return files.first { $0.hasPrefix("cloud_\(blob).") }
    }

    // MARK: JSON

    static func object(_ raw: String) -> [String: Any]? {
        guard let data = raw.data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    /// SyncJson.canonical: sorted keys, plain numbers, org.json string quoting.
    static func canonical(_ value: Any?) -> String {
        guard let value, !(value is NSNull) else { return "null" }
        switch value {
        case let d as [String: Any]:
            return "{" + d.keys.sorted().map { quote($0) + ":" + canonical(d[$0]) }.joined(separator: ",") + "}"
        case let a as [Any]:
            return "[" + a.map(canonical).joined(separator: ",") + "]"
        case let s as String: return quote(s)
        case let n as NSNumber:
            if CFGetTypeID(n) == CFBooleanGetTypeID() { return n.boolValue ? "true" : "false" }
            return NSDecimalNumber(decimal: n.decimalValue).stringValue
        case let b as Bool: return b ? "true" : "false"
        case let i as Int: return String(i)
        case let i as Int64: return String(i)
        case let x as Double: return NSDecimalNumber(value: x).stringValue
        default: return quote(String(describing: value))
        }
    }

    private static func quote(_ s: String) -> String {
        var out = "\""
        var prev: Character = " "
        for c in s {
            switch c {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "/": out += prev == "<" ? "\\/" : "/"
            case "\u{08}": out += "\\b"
            case "\t": out += "\\t"
            case "\n": out += "\\n"
            case "\u{0C}": out += "\\f"
            case "\r": out += "\\r"
            default:
                if let v = c.unicodeScalars.first?.value, c.unicodeScalars.count == 1,
                   v < 0x20 || (v >= 0x80 && v < 0xA0) || (v >= 0x2000 && v < 0x2100) {
                    out += String(format: "\\u%04x", v)
                } else { out.append(c) }
            }
            prev = c
        }
        return out + "\""
    }
}
