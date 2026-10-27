import SwiftUI
import EventKit

/// A REAL card added to a phone-calendar event with "REAL içeriği ekle" (CalendarCardLinks.kt). The event stays the
/// calendar's; REAL only remembers which card belongs to it.
struct CalendarCardAssociation: Codable, Hashable {
    let eventId: String
    let calendarId: String
    let marker: String
    var cardId: Int64
    /// The "REAL kartı: <url>" block REAL last wrote into the event's notes.
    var publicBlock: String?
}

/// SYNC (CalendarCardPublisher.kt): with the person's approval the card is published as a read-only web page and the
/// link is written into the event's notes. The server snapshots the cloud copy of the card, so it is synced first.
@MainActor
@Observable
final class CalendarCardPublisher {
    static let shared = CalendarCardPublisher()

    /// The card whose "Takvime web bağlantısı eklensin mi?" question is open.
    var offerCardId: Int64?
    private(set) var revision = 0

    private struct Receipt: Codable { let requestId: String; let cardVersion: Int64; var url: String?; var applied: Bool; let expiresAt: Int64 }
    @ObservationIgnored private let defaults = UserDefaults.standard

    var associations: [CalendarCardAssociation] {
        _ = revision
        return (try? JSONDecoder().decode([CalendarCardAssociation].self, from: defaults.data(forKey: "calendar_card_associations") ?? Data())) ?? []
    }
    private func save(_ items: [CalendarCardAssociation]) {
        if let d = try? JSONEncoder().encode(items) { defaults.set(d, forKey: "calendar_card_associations") }
        revision += 1
    }
    private var receipts: [String: Receipt] {
        get { (try? JSONDecoder().decode([String: Receipt].self, from: defaults.data(forKey: "calendar_card_publications") ?? Data())) ?? [:] }
        set { if let d = try? JSONEncoder().encode(newValue) { defaults.set(d, forKey: "calendar_card_publications") } }
    }

    func hasExternalTarget(_ cardId: Int64) -> Bool { associations.contains { $0.cardId == cardId } }
    /// Whether the link of this exact card version is already in the calendar.
    func isPublished(_ card: ReminderItem) -> Bool {
        guard let r = receipts[String(card.id)] else { return false }
        return r.applied && r.cardVersion == card.updatedAt
    }

    static func eventId(_ occurrence: CalendarOccurrence) -> String {
        guard let at = occurrence.id.lastIndex(of: "@") else { return occurrence.id }
        return String(occurrence.id[..<at])
    }

    /// The card already added to this event, if it still exists.
    func card(for occurrence: CalendarOccurrence, in store: ReminderStore) -> Int64? {
        let id = Self.eventId(occurrence)
        return associations.first { $0.eventId == id && $0.calendarId == occurrence.calendarId && store.reminder($0.cardId) != nil }?.cardId
    }

    func associate(_ occurrence: CalendarOccurrence, cardId: Int64) {
        let id = Self.eventId(occurrence)
        var items = associations
        if let i = items.firstIndex(where: { $0.eventId == id && $0.calendarId == occurrence.calendarId }) {
            items[i].cardId = cardId
        } else {
            items.append(CalendarCardAssociation(eventId: id, calendarId: occurrence.calendarId, marker: UUID().uuidString.lowercased(), cardId: cardId))
        }
        save(items)
    }

    func offer(_ cardId: Int64) { if hasExternalTarget(cardId) { offerCardId = cardId } }

    struct Failure: LocalizedError { let errorDescription: String? }

    /// Publishes [card] and writes its link into every linked event. Returns the status line.
    func publish(_ card: ReminderItem, store: ReminderStore, life: LifeStore) async throws -> String {
        guard RealAuth.shared.account != nil else { throw Failure(errorDescription: t("Bağlantı eklemek için REAL hesabınla giriş yap.")) }
        let calendar = CalendarConnection.shared
        guard calendar.writable else { throw Failure(errorDescription: t("Takvim yazma izni gerekli. İçeriğin REAL'de duruyor.")) }
        let linked = associations.filter { $0.cardId == card.id }
        guard !linked.isEmpty else { throw Failure(errorDescription: t("Bu kartın bağlı bir takvim etkinliği yok.")) }
        for a in linked {
            guard let ev = calendar.store.event(withIdentifier: a.eventId), ev.calendar.calendarIdentifier == a.calendarId,
                  ev.calendar.allowsContentModifications else {
                throw Failure(errorDescription: t("Bağlı takvim etkinliği bulunamadı veya düzenleme yetkisi yok. REAL içeriğin korunuyor."))
            }
        }
        let url = try await shareURL(card, store: store, life: life)
        var items = associations
        for a in linked {
            guard let ev = calendar.store.event(withIdentifier: a.eventId) else { continue }
            let notes = try Self.withLink(ev.notes, a, url)
            if notes != (ev.notes ?? "") {
                ev.notes = notes
                // A repeating event keeps one description for the whole series.
                do { try calendar.store.save(ev, span: ev.hasRecurrenceRules ? .futureEvents : .thisEvent, commit: true) }
                catch { throw Failure(errorDescription: t("Takvim etkinliği güncellenemedi. REAL kartın kaydedildi; yeniden dene.")) }
            }
            if let i = items.firstIndex(where: { $0.marker == a.marker }) { items[i].publicBlock = Self.block(a.marker, url) }
        }
        save(items)
        var all = receipts
        all[String(card.id)]?.applied = true
        receipts = all
        return t("Web bağlantısı takvime eklendi.")
    }

    /// The same request id is reused while it is valid, so a retry refreshes the same link instead of making another.
    private func shareURL(_ card: ReminderItem, store: ReminderStore, life: LifeStore) async throws -> String {
        let key = String(card.id), now = EpochMillis.now
        let prior = receipts[key]
        if let prior, prior.cardVersion == card.updatedAt, let url = prior.url, prior.expiresAt > now + 3_600_000 { return url }
        let requestId = prior.flatMap { $0.expiresAt > now ? $0.requestId : nil } ?? UUID().uuidString.lowercased()
        var all = receipts
        all[key] = Receipt(requestId: requestId, cardVersion: card.updatedAt, url: nil, applied: false, expiresAt: now + 86_400_000)
        receipts = all
        await RealCloudSync.shared.runFully(store, life)
        let latest = store.reminder(card.id) ?? card
        let r: [String: Any]
        do {
            r = try await RealBackend.post("/api/share/v1/calendar-shares", [
                "id": requestId, "cardKey": key, "version": latest.updatedAt,
                "options": ["includeAttachments": true, "includeLocation": false, "includeVoice": false, "includeJournal": false]
            ])
        } catch let f as RealBackend.Failure where f.code == "SYNC_CARD_FIRST" {
            throw Failure(errorDescription: t("Kart henüz buluta ulaşmadı. Birkaç saniye sonra yeniden dene."))
        }
        guard r["id"] as? String == requestId, let url = r["url"] as? String, url.hasPrefix("https://"), url.hasSuffix("/s/\(requestId)") else {
            throw Failure(errorDescription: t("Paylaşım linki doğrulanamadı. Lütfen tekrar dene."))
        }
        all = receipts
        all[key] = Receipt(requestId: requestId, cardVersion: latest.updatedAt, url: url, applied: false, expiresAt: now + 86_400_000)
        receipts = all
        return url
    }

    static func block(_ marker: String, _ url: String) -> String { "REAL kartı: \(url)\n[REAL-CALENDAR:\(marker)]" }

    /// Replaces REAL's previous block, or appends one; the rest of the notes is never touched (withExternalCardLink).
    static func withLink(_ notes: String?, _ a: CalendarCardAssociation, _ url: String) throws -> String {
        let original = notes ?? ""
        let next = block(a.marker, url)
        if let previous = a.publicBlock, let range = original.range(of: previous) {
            guard original.ranges(of: previous).count == 1 else { throw Failure(errorDescription: t("Takvim bağlantısı birden fazla kez bulunuyor.")) }
            return original.replacingCharacters(in: range, with: next)
        }
        let marker = "[REAL-CALENDAR:\(a.marker)]"
        if original.contains(marker) {
            var lines = original.components(separatedBy: "\n")
            guard let index = lines.firstIndex(of: marker), index > 0, lines.filter({ $0 == marker }).count == 1,
                  lines[index - 1].hasPrefix("REAL kartı: ") else {
                throw Failure(errorDescription: t("Takvimdeki bağlantı dışarıda değiştirildi; açıklamayı kontrol et."))
            }
            lines[index - 1] = "REAL kartı: \(url)"
            return lines.joined(separator: "\n")
        }
        return original.isEmpty ? next : original + "\n\n" + next
    }
}

/// "Takvime web bağlantısı eklensin mi?" — a plain sheet like the notification question: two answers.
struct CalendarPublicationSheet: View {
    let cardId: Int64
    @Environment(\.dismiss) private var dismiss
    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life
    @State private var busy = false
    @State private var message: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Capsule().fill(Color(argb: 0xFF666669)).frame(width: 34, height: 3).frame(maxWidth: .infinity)
            Text(t("Takvime web bağlantısı eklensin mi?")).font(RealFont.inter(22, .semibold)).foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)
            if let message { Text(message).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFB4AB)) }
            Button { publish() } label: {
                Text(busy ? t("Ekleniyor…") : t("Evet, bağlantı ekle")).font(RealFont.inter(15, .semibold)).foregroundStyle(.black)
                    .frame(maxWidth: .infinity, minHeight: 50).background(Capsule().fill(.white))
            }
            .buttonStyle(.plain).disabled(busy)
            Button { dismiss() } label: {
                Text(t("Hayır, REAL'de kalsın")).font(RealFont.inter(15)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 50).background(Capsule().fill(.white.opacity(0.08)))
            }
            .buttonStyle(.plain).disabled(busy)
        }
        .padding(22)
        .presentationDetents([.height(message == nil ? 250 : 300)])
        .presentationBackground(Color(argb: 0xFF111113))
        .interactiveDismissDisabled(busy)
    }

    private func publish() {
        guard let card = store.reminder(cardId) else { dismiss(); return }
        busy = true; message = nil
        Task {
            do {
                if !CalendarConnection.shared.writable { _ = await CalendarConnection.shared.requestRead() }
                let status = try await CalendarCardPublisher.shared.publish(card, store: store, life: life)
                RealToast.shared.show(status)
                dismiss()
            } catch {
                message = (error as? LocalizedError)?.errorDescription ?? RealBackend.message(for: error)
            }
            busy = false
        }
    }
}
