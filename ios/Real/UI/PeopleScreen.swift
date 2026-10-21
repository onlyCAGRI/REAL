import SwiftUI

/// Kişilerim (REAL connections, ConnectionUi.kt): the people you share with. Invite with a one-time link (7 days),
/// accept someone's link from a "real://connection/<token>" URL, remove or block a person.
@MainActor
@Observable
final class RealConnections {
    static let shared = RealConnections()
    struct Person: Identifiable, Hashable { let uid: String; let name: String; var pictureVersion: Int64? = nil; var id: String { uid } }
    struct Invite: Identifiable, Hashable { let id: String; let expiresAt: EpochMillis }

    private(set) var people: [Person] = []
    private(set) var invites: [Invite] = []
    private(set) var blocked: [Person] = []
    private(set) var loading = false
    var error: String?

    func refresh() async {
        guard RealAuth.shared.account != nil else { return }
        loading = true
        defer { loading = false }
        do {
            let data = try await RealBackend.request("/api/connections/v1/me")
            let o = (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
            people = ((o["connections"] as? [[String: Any]]) ?? []).compactMap { c in
                (c["uid"] as? String).map { Person(uid: $0, name: c["name"] as? String ?? "REAL user", pictureVersion: (c["pictureVersion"] as? NSNumber)?.int64Value) }
            }
            invites = ((o["invites"] as? [[String: Any]]) ?? []).compactMap { i in
                (i["id"] as? String).map { Invite(id: $0, expiresAt: (i["expiresAt"] as? NSNumber)?.int64Value ?? 0) }
            }
            blocked = ((o["blocked"] as? [[String: Any]]) ?? []).compactMap { c in
                (c["uid"] as? String).map { Person(uid: $0, name: c["name"] as? String ?? "REAL user") }
            }
            error = nil
        } catch { self.error = RealBackend.message(for: error) }
    }

    /// A one-time invite link (7 days) to send to someone.
    func createInvite() async throws -> URL {
        let r = try await RealBackend.post("/api/connections/v1/invites", [:])
        guard let s = r["url"] as? String, let url = URL(string: s), url.scheme == "https" else { throw RealBackend.Failure(status: -1) }
        await refresh()
        return url
    }

    func revokeInvite(_ id: String) async {
        _ = try? await RealBackend.request("/api/connections/v1/invites/\(id)", method: "DELETE", body: Data("{}".utf8))
        await refresh()
    }

    func remove(_ uid: String, block: Bool) async {
        let path = "/api/connections/v1/people/" + (uid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? uid) + (block ? "/block" : "")
        _ = try? await RealBackend.request(path, method: block ? "POST" : "DELETE", body: Data("{}".utf8))
        await refresh()
    }

    func unblock(_ uid: String) async {
        let path = "/api/connections/v1/people/" + (uid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? uid) + "/block"
        _ = try? await RealBackend.request(path, method: "DELETE", body: Data("{}".utf8))
        await refresh()
    }

    /// Who sent the invite (before accepting).
    func preview(_ token: String) async throws -> String {
        let data = try await RealBackend.request("/api/connections/v1/open/\(token)")
        return ((try JSONSerialization.jsonObject(with: data) as? [String: Any])?["name"] as? String) ?? "REAL user"
    }

    func accept(_ token: String) async throws {
        _ = try await RealBackend.post("/api/connections/v1/open/\(token)/accept", [:])
        await refresh()
    }
}

/// Links REAL opens: real://connection/<token> (Kişilerim invite), real://category-invite/<token> (category invite).
@MainActor
@Observable
final class RealDeepLinks {
    static let shared = RealDeepLinks()
    enum Pending: Identifiable, Equatable {
        case connection(String), category(String), sharedCard(String)
        var id: String { switch self { case .connection(let t): "c" + t; case .category(let t): "g" + t; case .sharedCard(let t): "s" + t } }
    }
    var pending: Pending?

    /// True when the URL was a REAL link.
    func handle(_ url: URL) -> Bool {
        guard url.scheme == "real", url.user == nil, url.port == nil, url.query == nil, url.fragment == nil else { return false }
        let token = String(url.path.dropFirst())
        // real://shared/<share id>: a card someone shared (SharedCardLink.kt).
        if url.host == "shared" {
            guard CardShareClient.isShareId(token) else { return false }
            pending = .sharedCard(token); return true
        }
        guard token.range(of: "^[0-9a-f]{64}$", options: .regularExpression) != nil else { return false }
        switch url.host {
        case "connection": pending = .connection(token); return true
        case "category-invite": pending = .category(token); return true
        default: return false
        }
    }
}

/// Accepting an invite link: who it is from, then Kabul et / Vazgeç.
struct InviteAcceptSheet: View {
    let link: RealDeepLinks.Pending
    @Environment(\.dismiss) private var dismiss
    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life
    @State private var from: String?
    @State private var error: String?
    @State private var busy = false
    @State private var done = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title).font(RealFont.inter(22, .semibold)).foregroundStyle(.white)
            Text(message).font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.7))
            if let error { Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFB4AB)) }
            HStack(spacing: 10) {
                Button { done ? dismiss() : accept() } label: {
                    Text(done ? "Tamam" : (busy ? "Bekle…" : "Kabul et")).font(RealFont.inter(15, .semibold)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity, minHeight: 48).background(Capsule().fill(.white))
                }
                .disabled(busy || (RealAuth.shared.account == nil))
                if !done {
                    Button { dismiss() } label: {
                        Text("Vazgeç").font(RealFont.inter(15)).foregroundStyle(.white)
                            .frame(maxWidth: .infinity, minHeight: 48).background(Capsule().fill(.white.opacity(0.08)))
                    }
                }
            }
            .buttonStyle(.plain)
        }
        .padding(22)
        .presentationDetents([.height(260)])
        .presentationBackground(Color(argb: 0xFF111113))
        .task {
            guard RealAuth.shared.account != nil else { error = "Kabul etmek için REAL hesabınla giriş yap."; return }
            if case .connection(let t) = link { from = try? await RealConnections.shared.preview(t) }
            if case .category(let t) = link {
                let data = try? await RealBackend.request("/api/categories/v1/invites/\(t)")
                let o = data.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
                from = o.map { "\($0["owner"] as? String ?? "REAL user") · \($0["name"] as? String ?? "")" }
            }
        }
    }

    private var title: String {
        if done { return "Eklendi" }
        if case .category = link { return "Kategori daveti" }
        if case .sharedCard = link { return "Paylaşılan kart" }
        return "Kişi daveti"
    }
    private var message: String {
        if done, case .sharedCard = link { return "Kart REAL'ine eklendi." }
        if done { return "Artık birbirinizin Kişilerim listesindesiniz." }
        if case .sharedCard = link { return "Bu kartın bir kopyası REAL'ine eklensin mi?" }
        if case .category = link { return "\(from ?? "Bir arkadaşın") seni ortak bir REAL kategorisine davet ediyor." }
        return "\(from ?? "Bir arkadaşın") seni REAL'de Kişilerim'e eklemek istiyor."
    }

    private func accept() {
        busy = true; error = nil
        Task {
            do {
                switch link {
                case .connection(let t): try await RealConnections.shared.accept(t)
                case .category(let t):
                    _ = try await RealBackend.post("/api/categories/v1/invites/\(t)/accept", [:])
                    await SharedCategorySync.shared.syncNow(store)
                case .sharedCard(let id):
                    try await CardShareClient.claim(id, store: store, life: life)
                }
                done = true
            } catch { self.error = (error as? LocalizedError)?.errorDescription ?? RealBackend.message(for: error) }
            busy = false
        }
    }
}

/// Kişilerim screen.
struct PeopleScreen: View {
    @Environment(\.dismiss) private var dismiss
    @State private var connections = RealConnections.shared
    @State private var inviteURL: URL?
    @State private var confirm: RealConnections.Person?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Kişilerim").font(RealFont.inter(26, .semibold)).foregroundStyle(.white)
                Spacer()
                Button("Bitti") { dismiss() }.font(RealFont.inter(15, .medium)).foregroundStyle(.white)
            }
            if RealAuth.shared.account == nil {
                Text("Kişi eklemek için REAL hesabınla giriş yap.").font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.6))
            } else {
                Button {
                    Task { inviteURL = try? await connections.createInvite() }
                } label: {
                    Text("Davet linki oluştur").font(RealFont.inter(15, .semibold)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity, minHeight: 48).background(Capsule().fill(.white))
                }
                .buttonStyle(.plain)
                if let inviteURL {
                    ShareLink(item: inviteURL, message: Text("REAL'de beni Kişilerim'e ekle")) {
                        Text("Linki paylaş").font(RealFont.inter(15)).foregroundStyle(.white)
                            .frame(maxWidth: .infinity, minHeight: 44).background(Capsule().fill(.white.opacity(0.08)))
                    }
                }
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        if connections.people.isEmpty && !connections.loading {
                            Text("Henüz kimse yok. Bir davet linki gönder.").font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.5))
                        }
                        ForEach(connections.people) { p in
                            HStack(spacing: 12) {
                                ConnectionAvatar(name: p.name, key: p.uid, size: 40, pictureVersion: p.pictureVersion)
                                Text(p.name).font(RealFont.inter(16)).foregroundStyle(.white).lineLimit(1)
                                Spacer()
                                Button { confirm = p } label: {
                                    Image(systemName: "ellipsis").foregroundStyle(.white.opacity(0.6)).frame(width: 36, height: 36)
                                }
                            }
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 18).fill(.white.opacity(0.05)))
                        }
                        if !connections.invites.isEmpty {
                            Text("Bekleyen davetler").font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.45)).padding(.top, 10)
                            ForEach(connections.invites) { i in
                                HStack {
                                    Text("Link · " + RealDates.dayLabel(i.expiresAt.date) + "'e kadar").font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.8))
                                    Spacer()
                                    Button("İptal") { Task { await connections.revokeInvite(i.id) } }
                                        .font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFB4AB))
                                }
                                .padding(.horizontal, 14).padding(.vertical, 10)
                            }
                        }
                        if !connections.blocked.isEmpty {
                            Text("Engellenenler").font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.45)).padding(.top, 10)
                            ForEach(connections.blocked) { p in
                                HStack {
                                    Text(p.name).font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.8))
                                    Spacer()
                                    Button("Engeli kaldır") { Task { await connections.unblock(p.uid) } }
                                        .font(RealFont.inter(13)).foregroundStyle(.white)
                                }
                                .padding(.horizontal, 14).padding(.vertical, 10)
                            }
                        }
                    }
                }
            }
            if let e = connections.error { Text(e).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFB4AB)) }
        }
        .padding(20)
        .background(Color.black.ignoresSafeArea())
        .task { await connections.refresh() }
        .confirmationDialog(confirm?.name ?? "", isPresented: Binding(get: { confirm != nil }, set: { if !$0 { confirm = nil } }), titleVisibility: .visible) {
            if let p = confirm {
                Button("Kişilerden çıkar", role: .destructive) { Task { await connections.remove(p.uid, block: false) } }
                Button("Engelle", role: .destructive) { Task { await connections.remove(p.uid, block: true) } }
                Button("Vazgeç", role: .cancel) {}
            }
        }
    }
}

/// "REAL'de adın ne olsun?" — asked once after Google sign-in, prefilled with the first name only.
struct ProfileNameSheet: View {
    private let editing = RealAuth.shared.nameEditRequested
    @State private var name = RealAuth.shared.nameEditRequested ? (RealAuth.shared.account?.displayName ?? "") : (RealAuth.shared.account?.firstName ?? "")
    @State private var busy = false
    @State private var error: String?
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Capsule().fill(Color(argb: 0xFF666669)).frame(width: 34, height: 3).frame(maxWidth: .infinity)
            Text("REAL'de adın ne olsun?").font(RealFont.inter(22, .semibold)).foregroundStyle(.white)
            Text("Ortak kategorilerde ve paylaştığın kartlarda bu ad görünür.").font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.65))
            TextField("Adın", text: $name)
                .font(RealFont.inter(16)).foregroundStyle(.white).tint(.white)
                .textContentType(.givenName).submitLabel(.done).focused($focused)
                .onSubmit { save() }
                .padding(.horizontal, 18).frame(height: 52)
                .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(.white.opacity(0.06)))
            if let error { Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFB4AB)) }
            Button { save() } label: {
                Text(busy ? "Kaydediliyor…" : "Kaydet").font(RealFont.inter(15, .semibold)).foregroundStyle(.black)
                    .frame(maxWidth: .infinity, minHeight: 50).background(Capsule().fill(.white))
            }
            .buttonStyle(.plain).disabled(busy || name.trimmingCharacters(in: .whitespaces).isEmpty)
            Button { RealAuth.shared.confirmName() } label: {
                Text(editing ? "Vazgeç" : "Google adım kalsın").font(RealFont.inter(15)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 50).background(Capsule().fill(.white.opacity(0.08)))
            }
            .buttonStyle(.plain).disabled(busy)
        }
        .padding(22)
        .presentationDetents([.height(error == nil ? 360 : 400)])
        .presentationBackground(Color(argb: 0xFF111113))
        .interactiveDismissDisabled()
    }

    private func save() {
        guard !busy else { return }
        busy = true; error = nil
        Task {
            do { try await RealAuth.shared.updateDisplayName(name) }
            catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Ad kaydedilemedi." }
            busy = false
        }
    }
}
