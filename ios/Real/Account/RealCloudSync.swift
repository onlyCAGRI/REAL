import Foundation
import CryptoKit
import Observation

/// Two-way REAL cloud sync on iOS (SyncEngine.kt): pull the account's change feed, merge it with this iPhone's cards
/// and push what changed here, with the same records Android uses (CARD, CATEGORY, PLANNER, MOOD and the category
/// order / description preferences). Each record keeps the last synced payload as its base; a card, category or mood
/// changed on both sides (or deleted in the account while changed here) waits as a conflict until the person chooses,
/// and a record the account refuses waits with its reason; the others keep syncing. Photos, files and voice notes
/// travel as attachments of their card.
@MainActor
@Observable
final class RealCloudSync {
    static let shared = RealCloudSync()
    private var running = false
    private var pending: Task<Void, Never>?
    private(set) var lastError: String?
    private(set) var lastSuccess: Date?
    /// Records the account did not take in the last run (SyncManagementPanel's issues). The others still went up;
    /// these are tried again on every run and nothing is removed from this iPhone.
    private(set) var issues: [Issue] = []

    struct Issue: Identifiable, Equatable {
        let id: String       // "KIND/key"
        let title: String
        let reason: String
        /// The account has a version of it ("Hesaptakini kullan"); otherwise it never reached the account.
        var hasAccountCopy = false
    }

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
        /// Records changed here AND in the account (or deleted there): nothing is applied or sent until the person
        /// chooses in Hesapları yönet → Eşitleme ("KIND/key" → both versions).
        var conflicts: [String: Conflict] = [:]
        /// Account records this iPhone could not read (e.g. a field a newer Android added): never removed here and
        /// never deleted from the account because they are "missing" on this iPhone.
        var unreadable: Set<String> = []

        init() {}
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            cursor = try c.decodeIfPresent(Int64.self, forKey: .cursor) ?? 0
            records = try c.decodeIfPresent([String: Rec].self, forKey: .records) ?? [:]
            blobs = try c.decodeIfPresent([String: String].self, forKey: .blobs) ?? [:]
            planner = try c.decodeIfPresent([String: String].self, forKey: .planner) ?? [:]
            conflicts = try c.decodeIfPresent([String: Conflict].self, forKey: .conflicts) ?? [:]
            unreadable = try c.decodeIfPresent(Set<String>.self, forKey: .unreadable) ?? []
        }
    }
    private struct Conflict: Codable {
        /// This iPhone's version when the account's arrived (JSON text).
        var local: String
        /// The account's version; nil = deleted there.
        var remote: String?
    }

    /// A conflict as Hesapları yönet shows it.
    struct ConflictInfo: Identifiable, Equatable {
        let id: String           // "KIND/key"
        let title: String
        let isCard: Bool
        let local: String
        /// nil: the account deleted it.
        let remote: String?
    }
    private(set) var conflicts: [ConflictInfo] = []
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
            issues = try await push(store, life, &st)
            saveState(st)
            lastError = nil
            lastSuccess = Date()
        } catch {
            saveState(st)
            lastError = Self.describe(error)
        }
        publishConflicts(st, store)
    }

    private func publishConflicts(_ st: State, _ store: ReminderStore) {
        conflicts = st.conflicts.keys.sorted().compactMap { docKey in
            guard let c = st.conflicts[docKey] else { return nil }
            return ConflictInfo(id: docKey, title: issueTitle(docKey, store), isCard: docKey.hasPrefix("CARD/"),
                                local: Self.preview(c.local), remote: c.remote.map(Self.preview))
        }
    }

    /// A few lines of a version for comparing (SyncManagementPanel's syncBranchPreview).
    private static func preview(_ raw: String) -> String {
        guard let o = object(raw) else { return "İçerik önizlenemedi." }
        var lines: [String] = []
        for key in ["text", "name"] { if let t = o[key] as? String, !t.isEmpty { lines.append(String(t.prefix(300))); break } }
        if let items = o["checklistItems"] as? [[String: Any]] {
            lines += items.prefix(6).compactMap { ($0["text"] as? String).map { "• " + $0.prefix(100) } }
            if items.count > 6 { lines.append("… \(items.count) madde") }
        }
        for (key, label) in [("attachments", "ek"), ("voiceRecordings", "ses kaydı"), ("musicBlocks", "müzik"), ("mediaBlocks", "film/dizi")] {
            if let list = o[key] as? [Any], !list.isEmpty { lines.append("\(list.count) \(label)") }
        }
        if let ms = (o["alertAt"] as? NSNumber)?.int64Value, ms > 0 {
            lines.append("Saat: " + Date(timeIntervalSince1970: Double(ms) / 1000).formatted(date: .abbreviated, time: .shortened))
        }
        if let ms = (o["updatedAt"] as? NSNumber)?.int64Value, ms > 0 {
            lines.append("Düzenleme: " + Date(timeIntervalSince1970: Double(ms) / 1000).formatted(date: .abbreviated, time: .shortened))
        }
        return lines.isEmpty ? "İçerik kaydı" : lines.joined(separator: "\n")
    }

    // MARK: Choices (Hesapları yönet → Eşitleme)

    enum Choice { case keepLocal, useAccount, keepAsNewCard }

    /// Resolves one conflict as the person chose, then syncs.
    func resolve(_ docKey: String, _ choice: Choice, _ store: ReminderStore, _ life: LifeStore) async {
        while running { try? await Task.sleep(for: .milliseconds(200)) }
        var st = loadState()
        guard let c = st.conflicts[docKey] else { publishConflicts(st, store); return }
        let parts = docKey.split(separator: "/", maxSplits: 1).map(String.init)
        guard parts.count == 2 else { return }
        switch choice {
        case .keepLocal:
            // Base is already the account's version: this iPhone's differs from it, so the next push sends it.
            guard c.remote != nil else { return }   // a card deleted in the account cannot come back under its old id
        case .useAccount:
            apply(kind: parts[0], key: parts[1], payload: c.remote.flatMap(Self.object), store, life, &st)
        case .keepAsNewCard:
            guard parts[0] == "CARD", let id = Int64(parts[1]), let card = store.reminder(id) else { return }
            store.putFromSync(Self.copy(of: card, newId: Self.freeId(store)))
            apply(kind: parts[0], key: parts[1], payload: c.remote.flatMap(Self.object), store, life, &st)
        }
        st.conflicts[docKey] = nil
        saveState(st)
        publishConflicts(st, store)
        await run(store, life)
    }

    /// "Hesaptakini kullan" for a refused record: this iPhone takes the account's copy back (none there: removed here).
    func useAccount(forRefused docKey: String, _ store: ReminderStore, _ life: LifeStore) async {
        while running { try? await Task.sleep(for: .milliseconds(200)) }
        var st = loadState()
        let parts = docKey.split(separator: "/", maxSplits: 1).map(String.init)
        guard parts.count == 2 else { return }
        apply(kind: parts[0], key: parts[1], payload: st.records[docKey]?.raw.flatMap(Self.object), store, life, &st)
        saveState(st)
        issues.removeAll { $0.id == docKey }
        await run(store, life)
    }

    private static func freeId(_ store: ReminderStore) -> Int64 {
        var id = Int64(Date().timeIntervalSince1970 * 1000)
        while store.reminder(id) != nil { id += 1 }
        return id
    }

    /// The same card under a new id; its files are copied so the two cards never share one upload.
    private static func copy(of card: ReminderItem, newId: Int64) -> ReminderItem {
        var copy = card
        copy.id = newId
        copy.createdAt = newId
        copy.updatedAt = newId
        copy.notifiedAlertAt = nil
        func duplicate(_ name: String) -> String {
            guard !name.isEmpty else { return name }
            let src = AttachmentStorage.directory.appendingPathComponent(name)
            let ext = src.pathExtension
            let fresh = UUID().uuidString.lowercased() + (ext.isEmpty ? "" : "." + ext)
            return (try? FileManager.default.copyItem(at: src, to: AttachmentStorage.directory.appendingPathComponent(fresh))) != nil ? fresh : name
        }
        copy.attachments = card.attachments.map { a in
            var a = a; if a.type != .textSnippet { a.localPath = duplicate(a.localPath) }; return a
        }
        copy.voiceRecordings = card.voiceRecordings.map { r in var r = r; r.localPath = duplicate(r.localPath); return r }
        return copy
    }

    /// A short Turkish line for a failed run, with the worker's code so a problem can be traced.
    static func describe(_ error: Error) -> String {
        if let u = error as? URLError {
            switch u.code {
            case .notConnectedToInternet, .networkConnectionLost: return "İnternet bağlantısı yok."
            case .timedOut: return "Bağlantı zaman aşımına uğradı. Biraz sonra tekrar dene."
            case .userAuthenticationRequired: return "Bunun için REAL'e giriş yap."
            default: return "Sunucuya ulaşılamadı (\(u.code.rawValue))."
            }
        }
        if error is AppCheckUnavailable { return "Cihaz doğrulaması yapılamadı. Biraz sonra tekrar dene." }
        guard let f = error as? RealBackend.Failure else { return "Şu anda yapılamadı (\(String(describing: type(of: error))))." }
        let code = f.code ?? (f.status > 0 ? "HTTP \(f.status)" : "yanıt okunamadı")
        switch f.status {
        case 401: return "Oturumun yenilenmeli. Hesaptan çıkıp tekrar gir."
        case 403 where f.code == "APP_CHECK_REQUIRED": return "Cihaz doğrulaması yapılamadı. Biraz sonra tekrar dene."
        case 423: return "Hesabın şu anda bir işlem nedeniyle kilitli. Biraz sonra tekrar dene."
        case 429: return "Çok sık eşitlendi. Biraz sonra tekrar dene."
        case 503: return "Bulut eşitlemesi şu anda kapalı (\(code))."
        default: return "Şu anda yapılamadı (\(code)). Biraz sonra tekrar dene."
        }
    }

    /// Errors that belong to one record (it stays here and is listed); the rest stop the whole run.
    private static func recordOnly(_ error: Error) -> Bool {
        guard let f = error as? RealBackend.Failure else { return false }
        return f.status == -1 || ((400..<500).contains(f.status) && ![401, 403, 423, 426, 429].contains(f.status))
    }

    private static func reason(_ error: Error) -> String {
        guard let f = error as? RealBackend.Failure else { return "Gönderilemedi." }
        let code = f.code ?? (f.status > 0 ? "HTTP \(f.status)" : "yanıt okunamadı")
        switch f.code ?? "" {
        case "INVALID_CARD", "INVALID_SYNC_PAYLOAD", "LOCAL_ONLY_FIELD", "LOCAL_IMAGE_URI", "INVALID_ID", "INVALID_LOCAL_ID",
             "INVALID_RELATION", "INVALID_SHARE_PROVENANCE", "INVALID_CARD_LAYOUT":
            return "Kaydın bir alanı hesabın kurallarına uymadı (\(code))."
        case "SYNC_ARRAY_LIMIT", "ATTACHMENT_COUNT_LIMIT", "PAYLOAD_TOO_LARGE":
            return "Kayıt eşitleme sınırını aşıyor (\(code))."
        default:
            return f.status == 413 ? "Kayıt eşitleme sınırını aşıyor (\(code))." : "Hesap bu kaydı kabul etmedi (\(code))."
        }
    }

    private func issue(_ docKey: String, _ store: ReminderStore, _ st: State, reason: String) -> Issue {
        Issue(id: docKey, title: issueTitle(docKey, store), reason: reason, hasAccountCopy: st.records[docKey]?.raw != nil)
    }

    private func issueTitle(_ docKey: String, _ store: ReminderStore) -> String {
        let parts = docKey.split(separator: "/", maxSplits: 1).map(String.init)
        guard parts.count == 2 else { return docKey }
        switch parts[0] {
        case "CARD", "PLANNER":
            if let id = Int64(parts[1]), let card = store.reminder(id) {
                let t = card.text.trimmingCharacters(in: .whitespacesAndNewlines)
                return t.isEmpty ? (card.isDivider ? "Ayraç" : "Başlıksız kart") : String(t.prefix(60))
            }
            return "Silinen kart"
        case "CATEGORY":
            if let id = Int64(parts[1]), let c = store.category(id) { return "Kategori: \(c.name)" }
            return "Silinen kategori"
        case "MOOD": return "Mood kaydı"
        case "DRINK": return "Su kaydı"
        default: return "Ayar"
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
        // An Ortak kategori card lives in the shared store: its personal record (the copy from before it was shared)
        // never overwrites it, or it loses its link and goes up to the category again as a duplicate. The record is
        // only remembered, so the next push removes it from the personal cloud (as Android does).
        let raw = payload.flatMap { try? JSONSerialization.data(withJSONObject: $0) }.flatMap { String(data: $0, encoding: .utf8) }
        if kind == "CARD" || kind == "PLANNER", let localId = Int64(key), store.reminder(localId)?.sharedCardId != nil {
            st.records[docKey] = Rec(id: id, revision: revision, base: remote, raw: raw)
            return
        }
        let exported = export(docKey, store, life, base: old?.raw, &st)
        let local = exported.map(Self.canonical)
        let base = old?.base
        var take = false
        if local == remote { take = false; st.conflicts[docKey] = nil }
        else if st.conflicts[docKey] != nil {
            // Still waiting for the person's choice: only the account's side is refreshed.
            st.conflicts[docKey]?.remote = raw
        }
        else if local == base { take = true }
        else if remote == base { take = false }         // only this iPhone changed it: it goes up
        else if let exported, ["CARD", "CATEGORY", "MOOD"].contains(kind),
                let localRaw = (try? JSONSerialization.data(withJSONObject: exported)).flatMap({ String(data: $0, encoding: .utf8) }) {
            // Changed here AND in the account (or deleted there): nothing is overwritten; the person chooses.
            st.conflicts[docKey] = Conflict(local: localRaw, remote: raw)
        }
        else { take = true }                            // the rest (drinks, settings, deleted here): the account's copy
        if take { apply(kind: kind, key: key, payload: payload, store, life, &st) }
        st.records[docKey] = Rec(id: id, revision: revision, base: remote, raw: raw)
    }

    private static func drinkPayload(_ e: WaterIntakeEntry) -> [String: Any] {
        ["id": e.id, "amountMl": e.amountMl, "createdAt": e.createdAt, "beverage": e.beverage.rawValue]
    }

    private func push(_ store: ReminderStore, _ life: LifeStore, _ st: inout State) async throws -> [Issue] {
        var found: [Issue] = []
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
        // Drinks (DRINK) and the water goal, like Android's water tracker.
        for e in life.entries { docs["DRINK/\(e.id)"] = Self.drinkPayload(e) }
        docs["PREFERENCE/water/goal"] = ["value": life.goalMl]
        let order = store.categoryOrderKeys.joined(separator: "|")
        if !order.isEmpty || st.records["PREFERENCE/ui/category_order_v1"]?.base != nil {
            docs["PREFERENCE/ui/category_order_v1"] = ["value": order.isEmpty ? NSNull() : order]
        }
        let preview = UserDefaults.standard.integer(forKey: "home_category_preview_count_v1")
        if preview > 0 { docs["PREFERENCE/ui/home_category_preview_count_v1"] = ["value": preview] }

        // Everything this account had that is gone here is deleted (only kinds this iPhone writes).
        let written: Set<String> = ["CARD", "CATEGORY", "PLANNER", "MOOD", "DRINK"]
        var keys = Set(docs.keys)
        for (k, rec) in st.records where rec.base != nil && !st.unreadable.contains(k) && written.contains(String(k.split(separator: "/").first ?? "")) { keys.insert(k) }

        for docKey in keys.sorted(by: { Self.order($0) < Self.order($1) }) {
            // Waiting for the person's choice (Hesapları yönet → Eşitleme): neither side is touched.
            if st.conflicts[docKey] != nil { continue }
            // Unreadable here: this iPhone's older copy must not overwrite (or delete) the account's newer one.
            if st.unreadable.contains(docKey) { continue }
            var payload = docs[docKey]
            let rec = st.records[docKey]
            if payload.map(Self.canonical) == rec?.base { continue }
            let parts = docKey.split(separator: "/", maxSplits: 1).map(String.init)
            guard parts.count == 2 else { continue }
            let kind = parts[0], key = parts[1]
            let id = rec?.id ?? UUID().uuidString.lowercased()
            // The account takes no untitled card (only an Ayraç may be empty): it stays here and is listed.
            if kind == "CARD", let p = payload, ((p["text"] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
               p["isDivider"] as? Bool != true {
                found.append(issue(docKey, store, st, reason: "Kartın metni boş; hesaba gönderilmesi için bir başlık yaz."))
                continue
            }
            if kind == "CARD" {
                // The card is a cloud resource; its files are attachments of it. A file that does not go up keeps
                // only this card waiting; the others continue.
                do {
                    try await ensureResource(id, title: (payload?["text"] as? String) ?? "Deleted REAL")
                    if let card = store.reminders.first(where: { "CARD/\($0.id)" == docKey }) {
                        payload = cardPayload(card, base: rec?.raw, &st, upload: true, resourceId: id)
                        if let p = payload { try await uploadFiles(p, resourceId: id, &st) }
                    }
                } catch {
                    let network = (error as? URLError).map { [.notConnectedToInternet, .networkConnectionLost, .cancelled].contains($0.code) } ?? false
                    if network || error is CancellationError || !(Self.recordOnly(error) || error is URLError) { throw error }
                    found.append(issue(docKey, store, st,
                                       reason: "Bu kartın bir eki buluta gönderilemedi. Yerel içerik kaldırılmadı. (\((error as? RealBackend.Failure)?.code ?? "ek"))"))
                    continue
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
            } catch where Self.recordOnly(error) {
                found.append(issue(docKey, store, st, reason: Self.reason(error)))
            }
        }
        return found
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
                AppUpdateGate.observe(status: (response as? HTTPURLResponse)?.statusCode ?? 0)
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
    private static let omitWhenEmpty: Set<String> = ["endAt", "earlyAlertMinutes", "repeatSkippedEpochDays", "gameBlocks", "completionNote", "checkRequest"]

    private func cardPayload(_ card: ReminderItem, base: String?, _ st: inout State, upload: Bool, resourceId: String? = nil) -> [String: Any] {
        var o = base.flatMap(Self.object) ?? [:]
        let mine = (try? JSONSerialization.jsonObject(with: JSONEncoder().encode(card)) as? [String: Any]) ?? [:]
        o["id"] = card.id
        for key in Self.cardKeys { o[key] = mine[key] ?? NSNull() }
        for key in Self.omitWhenEmpty {
            if let v = mine[key], !((v as? [Any])?.isEmpty ?? false) { o[key] = v } else { o.removeValue(forKey: key) }
        }
        o["contentBlockLayout"] = Self.layoutPayload(card.contentBlockLayout)
        // Ayraç: only a divider carries the flag (as on Android).
        if card.isDivider { o["isDivider"] = true } else { o.removeValue(forKey: "isDivider") }
        // Alarm: like Android, only an alarm card carries the flag.
        if card.isAlarm { o["isAlarm"] = true } else { o.removeValue(forKey: "isAlarm") }
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

    /// encodeCardContentLayout (CardContentLayoutJson.kt): "grid": 6 marks the six-unit layout (without it the account
    /// reads the old two-column one and rejects span 6); text styling only on text / files, hideActions only on
    /// music / media, and only block kinds the account knows.
    private static let layoutKinds: Set<String> = ["text", "card_stack", "mood", "checklist", "photos", "files", "voice", "music", "media", "location", "journal"]
    private static func layoutPayload(_ layout: [String: CardBlockPlacement]) -> [String: Any] {
        var out: [String: Any] = [:]
        for (key, raw) in layout {
            let kind = String(key.split(separator: ":").first ?? "")
            guard layoutKinds.contains(kind), key.count <= 160, out.count < 100 else { continue }
            let p = raw.normalized
            var row: [String: Any] = ["grid": CardBlockPlacement.units, "span": p.span, "column": p.column,
                                      "gapBefore": p.gapBefore, "minHeight": p.minHeight]
            if let top = p.top { row["top"] = top }
            if p.hideActions && (kind == "music" || kind == "media") { row["hideActions"] = true }
            if key == "text" || key.hasPrefix("files:") {
                if ["center", "end"].contains(p.textAlignment) { row["textAlignment"] = p.textAlignment }
                if p.textBold { row["textBold"] = true }
                if p.textItalic { row["textItalic"] = true }
            }
            out[key] = row
        }
        return out
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
        case "DRINK":
            return life.entries.first { $0.id == key }.map(Self.drinkPayload)
        case "PREFERENCE":
            if key == "ui/category_order_v1" {
                let order = store.categoryOrderKeys.joined(separator: "|")
                return order.isEmpty ? nil : ["value": order]
            }
            if key == "ui/home_category_preview_count_v1" {
                let n = UserDefaults.standard.integer(forKey: "home_category_preview_count_v1")
                return n > 0 ? ["value": n] : nil
            }
            if key == "water/goal" { return ["value": life.goalMl] }
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
            guard let payload else { st.unreadable.remove("CARD/\(key)"); store.removeFromSync(id); return }
            guard var card = RealCloudRestore.decodeCard(payload, decoder) else { st.unreadable.insert("CARD/\(key)"); return }
            st.unreadable.remove("CARD/\(key)")
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
            guard let payload else { st.unreadable.remove("CATEGORY/\(key)"); store.removeCategoryOnlyFromSync(id); return }
            guard var c = RealCloudRestore.decodeCategory(payload, decoder) else { st.unreadable.insert("CATEGORY/\(key)"); return }
            st.unreadable.remove("CATEGORY/\(key)")
            if let old = store.category(id) {
                c.description = old.description; c.showInCalendar = old.showInCalendar
                c.sharedId = old.sharedId; c.sharedRole = old.sharedRole; c.sharedOwnerName = old.sharedOwnerName
            }
            store.putCategoryFromSync(c)
        case "MOOD":
            guard let id = Int64(key) else { return }
            if let payload, RealCloudRestore.decodeMood(payload, decoder) == nil { st.unreadable.insert("MOOD/\(key)"); return }
            st.unreadable.remove("MOOD/\(key)")
            var moods = life.moods.filter { $0.id != id }
            if let payload, let m = RealCloudRestore.decodeMood(payload, decoder) { moods.append(m) }
            life.replaceAll(goal: life.goalMl, entries: life.entries, moods: moods.sorted { $0.id > $1.id })
        case "DRINK":
            var entries = life.entries.filter { $0.id != key }
            if let payload, let ml = (payload["amountMl"] as? NSNumber)?.intValue, let at = (payload["createdAt"] as? NSNumber)?.int64Value {
                entries.append(WaterIntakeEntry(id: key, amountMl: ml, createdAt: at,
                                                beverage: BeverageType(rawValue: ((payload["beverage"] as? String) ?? "water").lowercased()) ?? .water))
            }
            life.replaceAll(goal: life.goalMl, entries: entries.sorted { $0.createdAt < $1.createdAt }, moods: life.moods)
        case "PREFERENCE":
            let value = payload?["value"]
            if key == "water/goal", let ml = (value as? NSNumber)?.intValue, (100...10_000).contains(ml) {
                life.replaceAll(goal: ml, entries: life.entries, moods: life.moods)
                return
            }
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
            break
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
