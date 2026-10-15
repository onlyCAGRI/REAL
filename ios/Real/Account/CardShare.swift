import SwiftUI

/// CardShareClient.kt: a read-only web link of a card (24 h), sending it to someone in Kişilerim or by e-mail, taking a
/// shared card into this account, and receiving cards others sent. The server snapshots this account's cloud copy of
/// the card, so the card is synced first.
@MainActor
enum CardShareClient {
    struct Options { var attachments = true, location = false, voice = false, journal = false }
    struct Link: Identifiable { let id: String; let url: URL }

    struct Failure: LocalizedError { let errorDescription: String? }

    private static func message(_ error: Error, path: String) -> Failure {
        guard let f = error as? RealBackend.Failure else { return Failure(errorDescription: RealBackend.message(for: error)) }
        switch (f.status, f.code ?? "") {
        case (423, _): return Failure(errorDescription: "Bulut verileri silindi veya siliniyor; bu hesabın paylaşımı kapalı.")
        case (401, _): return Failure(errorDescription: "Kartı paylaşmak için yeniden giriş yap.")
        case (404, _) where path.hasSuffix("/shares") || path.hasSuffix("/inbox") || path.hasSuffix("/profile"):
            return Failure(errorDescription: "Paylaşım şu an kullanılamıyor. Lütfen daha sonra tekrar dene.")
        case (404, _): return Failure(errorDescription: "Bu paylaşılan kart artık mevcut değil.")
        case (_, "SYNC_CARD_FIRST"): return Failure(errorDescription: "Bu kartın eşitlemesi henüz bitmedi. Lütfen tekrar dene.")
        case (_, let c) where c.contains("STORAGE_LIMIT"): return Failure(errorDescription: "Bu kartı kopyalamak için bulut alanı yetersiz.")
        case (429, _): return Failure(errorDescription: "Günlük paylaşım sınırına ulaşıldı. Lütfen yarın tekrar dene.")
        case (503, _), (_, "SHARING_NOT_ENABLED"): return Failure(errorDescription: "Paylaşım şu an kullanılamıyor. Lütfen daha sonra tekrar dene.")
        default: return Failure(errorDescription: "Paylaşım tamamlanamadı. Lütfen tekrar dene.")
        }
    }

    private static func post(_ path: String, _ body: [String: Any]) async throws -> [String: Any] {
        guard RealAuth.shared.account != nil else { throw Failure(errorDescription: "Kartı paylaşmak için REAL hesabınla giriş yap.") }
        do { return try await RealBackend.post(path, body, timeout: 60) } catch { throw message(error, path: path) }
    }

    static func isShareId(_ s: String) -> Bool { s.range(of: "^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$", options: .regularExpression) != nil }

    static func create(_ card: ReminderItem, options: Options, store: ReminderStore, life: LifeStore) async throws -> Link {
        await RealCloudSync.shared.runFully(store, life)
        let requestId = UUID().uuidString.lowercased()
        let r = try await post("/api/share/v1/shares", [
            "id": requestId, "cardKey": String(card.id),
            "options": ["includeAttachments": options.attachments, "includeLocation": options.location,
                        "includeVoice": options.voice, "includeJournal": options.journal]
        ])
        guard r["id"] as? String == requestId, let s = r["url"] as? String, s.hasPrefix("https://"), s.hasSuffix("/s/\(requestId)"),
              let url = URL(string: s) else { throw Failure(errorDescription: "Paylaşım linki doğrulanamadı. Lütfen tekrar dene.") }
        return Link(id: requestId, url: url)
    }

    static func sendToConnection(_ shareId: String, uid: String) async throws {
        _ = try await post("/api/share/v1/shares/\(shareId)/send-connection", ["recipientUid": uid])
    }

    static func sendToEmail(_ shareId: String, email: String) async throws {
        _ = try await post("/api/share/v1/shares/\(shareId)/send", ["email": email.trimmingCharacters(in: .whitespaces)])
    }

    static func revoke(_ shareId: String) async throws {
        guard RealAuth.shared.account != nil else { return }
        do { _ = try await RealBackend.request("/api/share/v1/shares/\(shareId)", method: "DELETE", body: Data("{}".utf8)) }
        catch { throw message(error, path: "/api/share/v1/shares/\(shareId)") }
    }

    /// Takes a shared card into this account; it then arrives with the cloud sync.
    static func claim(_ shareId: String, store: ReminderStore, life: LifeStore) async throws {
        _ = try await post("/api/share/v1/shares/\(shareId)/claim", [:])
        await RealCloudSync.shared.runFully(store, life)
    }

    /// Cards others sent to this account (needs "Gelen kartlara izin ver"; on by default on the server).
    static func receive(store: ReminderStore, life: LifeStore) async {
        guard RealAuth.shared.account != nil else { return }
        _ = try? await RealBackend.post("/api/share/v1/profile", [:], timeout: 20)
        guard let r = try? await RealBackend.post("/api/share/v1/inbox", [:], timeout: 30),
              let delivered = (r["delivered"] as? NSNumber)?.intValue, delivered > 0 else { return }
        await RealCloudSync.shared.runFully(store, life)
        RealToast.shared.show(delivered == 1 ? "Sana bir kart gönderildi" : "Sana \(delivered) kart gönderildi")
    }
}

/// "Kartı paylaş": what to include, a 24 h web link (share / copy / close it), and sending it to someone in Kişilerim.
struct CardShareSheet: View {
    let card: ReminderItem
    @Environment(\.dismiss) private var dismiss
    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life
    @State private var options = CardShareClient.Options()
    @State private var link: CardShareClient.Link?
    @State private var busy = false
    @State private var error: String?
    @State private var people = RealConnections.shared
    @State private var sent: Set<String> = []

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Kartı paylaş").font(RealFont.inter(26, .semibold)).foregroundStyle(.white)
                Spacer()
                Button("Bitti") { dismiss() }.font(RealFont.inter(15, .medium)).foregroundStyle(.white)
            }
            if RealAuth.shared.account == nil {
                Text("Kart paylaşmak için REAL hesabınla giriş yap.").font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.6))
                ShareLink(item: card.shareText) {
                    Text("Metin olarak paylaş").font(RealFont.inter(15)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 46).background(Capsule().fill(.white.opacity(0.08)))
                }
            } else {
                Text("Dahil edilecek içerik").font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.6))
                VStack(spacing: 0) {
                    toggle("Fotoğraf ve dosyalar", $options.attachments, show: !card.attachments.isEmpty)
                    toggle("Konum", $options.location, show: card.location != nil)
                    toggle("Ses kayıtları", $options.voice, show: !card.voiceRecordings.isEmpty)
                    toggle("Günlük", $options.journal, show: card.journal != nil)
                }
                .disabled(link != nil)
                if let link {
                    Text("Bağlantıya sahip olan herkes seçilen içeriği görüntüleyebilir. Bağlantı 24 saat geçerli.")
                        .font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.5))
                    HStack(spacing: 10) {
                        ShareLink(item: link.url) {
                            Text("Linki paylaş").font(RealFont.inter(15, .semibold)).foregroundStyle(.black)
                                .frame(maxWidth: .infinity, minHeight: 46).background(Capsule().fill(.white))
                        }
                        Button { UIPasteboard.general.url = link.url; RealToast.shared.show("Link kopyalandı") } label: {
                            Text("Kopyala").font(RealFont.inter(15)).foregroundStyle(.white)
                                .frame(width: 100, height: 46).background(Capsule().fill(.white.opacity(0.08)))
                        }
                        .buttonStyle(.plain)
                    }
                    Button("Bu linki kapat") {
                        Task { try? await CardShareClient.revoke(link.id); self.link = nil; sent = [] }
                    }
                    .font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.6))
                    if !people.people.isEmpty {
                        Text("Kişilerim'e gönder").font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.6)).padding(.top, 4)
                        ScrollView {
                            VStack(spacing: 6) {
                                ForEach(people.people) { p in
                                    HStack(spacing: 12) {
                                        ProfileAvatar(name: p.name, size: 34)
                                        Text(p.name).font(RealFont.inter(15)).foregroundStyle(.white).lineLimit(1)
                                        Spacer()
                                        Button(sent.contains(p.uid) ? "Gönderildi" : "Gönder") { send(to: p.uid, shareId: link.id) }
                                            .font(RealFont.inter(13, .semibold)).foregroundStyle(sent.contains(p.uid) ? .white.opacity(0.5) : .black)
                                            .padding(.horizontal, 14).padding(.vertical, 7)
                                            .background(Capsule().fill(sent.contains(p.uid) ? Color.white.opacity(0.08) : Color.white))
                                            .disabled(sent.contains(p.uid) || busy)
                                    }
                                    .padding(.horizontal, 12).padding(.vertical, 6)
                                }
                            }
                        }
                        .frame(maxHeight: 220)
                    }
                } else {
                    Button { create() } label: {
                        Text(busy ? "Hazırlanıyor…" : "Paylaşım linki oluştur").font(RealFont.inter(15, .semibold)).foregroundStyle(.black)
                            .frame(maxWidth: .infinity, minHeight: 48).background(Capsule().fill(.white))
                    }
                    .buttonStyle(.plain).disabled(busy)
                }
            }
            if let error { Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFB4AB)) }
        }
        .padding(22)
        .presentationDetents([.medium, .large])
        .presentationBackground(Color(argb: 0xFF111113))
        .task { await people.refresh() }
    }

    @ViewBuilder private func toggle(_ title: String, _ value: Binding<Bool>, show: Bool) -> some View {
        if show {
            Toggle(title, isOn: value).font(RealFont.inter(15)).foregroundStyle(.white).tint(Color(argb: 0xFF5BC4D9)).padding(.vertical, 6)
        }
    }

    private func create() {
        busy = true; error = nil
        Task {
            do { link = try await CardShareClient.create(card, options: options, store: store, life: life) }
            catch { self.error = (error as? LocalizedError)?.errorDescription ?? RealBackend.message(for: error) }
            busy = false
        }
    }

    private func send(to uid: String, shareId: String) {
        busy = true; error = nil
        Task {
            do { try await CardShareClient.sendToConnection(shareId, uid: uid); sent.insert(uid) }
            catch { self.error = (error as? LocalizedError)?.errorDescription ?? RealBackend.message(for: error) }
            busy = false
        }
    }
}

/// AnnouncementCardClient.kt: REAL announcements arrive as cards through the account's sync feed; this asks the
/// server to deliver new ones (and retire expired ones), then pulls them.
@MainActor
enum AnnouncementClient {
    static func receive(store: ReminderStore, life: LifeStore) async {
        guard RealAuth.shared.account != nil,
              let r = try? await RealBackend.post("/api/announcements/v1/deliver", [:], timeout: 10) else { return }
        let changed = ((r["delivered"] as? NSNumber)?.intValue ?? 0) + ((r["retired"] as? NSNumber)?.intValue ?? 0)
        if changed > 0 { await RealCloudSync.shared.runFully(store, life) }
    }
}
