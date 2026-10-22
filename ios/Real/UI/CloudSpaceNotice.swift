import SwiftUI

/// Bulut alanı uyarısı (CloudSpaceNotice.kt): once at 90% and once when full (again only after dropping under 90%).
/// The app keeps working; new files stay on this iPhone and go up by themselves once there is room.
@MainActor
@Observable
final class CloudSpace {
    static let shared = CloudSpace()
    /// 1: 90%, 2: full; set when the level rose since the last notice.
    var notice = 0
    private let defaults = UserDefaults.standard
    private static let checkEvery: TimeInterval = 6 * 3600

    /// Start / foreground (at most every six hours) and right after a file was refused for space ([force]).
    func check(force: Bool = false) async {
        guard let uid = RealAuth.shared.account?.uid else { return }
        let now = Date().timeIntervalSince1970
        guard force || now - defaults.double(forKey: "real_cloud_space_checked_\(uid)") >= Self.checkEvery,
              let data = try? await RealBackend.request("/api/account/v1/usage", timeout: 20),
              let usage = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["usage"] as? [String: Any],
              let used = (usage["usedBytes"] as? NSNumber)?.int64Value,
              let quota = (usage["quotaBytes"] as? NSNumber)?.int64Value, quota > 0 else { return }
        defaults.set(now, forKey: "real_cloud_space_checked_\(uid)")
        let level = used >= quota ? 2 : (used * 10 >= quota * 9 ? 1 : 0)
        let key = "real_cloud_space_shown_\(uid)"
        let shown = defaults.integer(forKey: key)
        if level != shown && (level == 0 || level > shown) { defaults.set(level, forKey: key) }
        if level > shown { notice = level }
    }

    /// Bekleyenleri şimdi temizle: files removed from cards stop counting now instead of after 30 days.
    func purgeRetained() async -> Bool {
        for _ in 0..<20 {
            guard let data = try? await RealBackend.request("/api/account/v1/storage/purge-retained", method: "POST", body: Data("{}".utf8)),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return false }
            if ((json["remaining"] as? NSNumber)?.intValue ?? 0) == 0 { return true }
        }
        return false
    }
}

extension View {
    /// Home: the one-time notice and its "Yer aç" sheet.
    func cloudSpaceNotice() -> some View { modifier(CloudSpaceNoticeModifier()) }
}

private struct CloudSpaceNoticeModifier: ViewModifier {
    @State private var space = CloudSpace.shared
    @State private var showFiles = false
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .task { await space.check() }
            .onChange(of: scenePhase) { _, phase in if phase == .active { Task { await space.check() } } }
            .alert(space.notice == 2 ? "Bulut alanın doldu" : "Bulut alanın dolmak üzere",
                   isPresented: Binding(get: { space.notice != 0 }, set: { if !$0 { space.notice = 0 } })) {
                Button("Yer aç") { space.notice = 0; showFiles = true }
                Button("Tamam", role: .cancel) { space.notice = 0 }
            } message: {
                Text(space.notice == 2
                     ? "REAL'i kullanmaya devam edebilirsin. Yeni fotoğraf ve ekler bu telefonda kalır, yer açınca kendiliğinden yüklenir."
                     : "Alanının %90'ını kullandın. Dolunca yeni ekler yalnız bu telefonda kalır.")
            }
            .sheet(isPresented: $showFiles) { CloudSpaceFilesSheet() }
    }
}

/// Yer aç: this iPhone's largest photos, files and voice recordings, with the card each sits on.
struct CloudSpaceFilesSheet: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var confirm: String?
    @State private var purging = false
    @State private var note: String?

    private struct SpaceFile: Identifiable { let id: String; let cardId: Int64; let title: String; let kind: String; let bytes: Int64 }

    private var files: [SpaceFile] {
        func size(_ name: String) -> Int64 {
            let url = AttachmentStorage.directory.appendingPathComponent(name)
            return ((try? FileManager.default.attributesOfItem(atPath: url.path)[.size]) as? NSNumber)?.int64Value ?? 0
        }
        return store.reminders.flatMap { card -> [SpaceFile] in
            let title = card.text.split(separator: "\n").first.map { $0.trimmingCharacters(in: .whitespaces) } ?? "Başlıksız kart"
            let attachments = card.attachments.filter { $0.type != .textSnippet && !$0.localPath.isEmpty }.map {
                SpaceFile(id: $0.id, cardId: card.id, title: title.isEmpty ? "Başlıksız kart" : title,
                          kind: $0.type == .image ? "Fotoğraf" : ($0.displayName ?? "Dosya"),
                          bytes: max(size($0.localPath), $0.sizeBytes ?? 0))
            }
            let voices = card.voiceRecordings.map {
                SpaceFile(id: $0.id, cardId: card.id, title: title.isEmpty ? "Başlıksız kart" : title, kind: "Ses kaydı", bytes: size($0.localPath))
            }
            return attachments + voices
        }.filter { $0.bytes > 0 }.sorted { $0.bytes > $1.bytes }.prefix(10).map { $0 }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Yer aç").font(RealFont.inter(24, .semibold)).foregroundStyle(.white)
                Spacer()
                Button("Bitti") { dismiss() }.font(RealFont.inter(15, .medium)).foregroundStyle(.white)
            }
            Text("En büyük ekler").font(RealFont.inter(14, .medium)).foregroundStyle(.white)
            let list = files
            if list.isEmpty {
                Text("Bu telefonda ek yok.").font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF9A9AA3))
            }
            ForEach(list) { file in
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(file.title).font(RealFont.inter(14)).foregroundStyle(.white).lineLimit(1)
                        Text("\(file.kind) · \(Self.size(file.bytes))").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF9A9AA3)).lineLimit(1)
                    }
                    Spacer()
                    Button(confirm == file.id ? "Sil" : "Kaldır") {
                        if confirm == file.id { remove(file); confirm = nil } else { confirm = file.id }
                    }
                    .font(RealFont.inter(13, .medium))
                    .foregroundStyle(confirm == file.id ? Color(argb: 0xFFFF6B6B) : .white)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .overlay(Capsule().strokeBorder(.white.opacity(0.16)))
                }
            }
            Text("Kaldırılan ekler buluttan 30 gün içinde silinir; hemen yer açmak için bekleyenleri temizle.")
                .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF9A9AA3))
            Button {
                purging = true
                Task {
                    note = await CloudSpace.shared.purgeRetained() ? "Bekleyenler temizlendi." : "Şu anda temizlenemedi. Biraz sonra tekrar dene."
                    purging = false
                    await CloudSpace.shared.check(force: true)
                }
            } label: {
                Text(purging ? "Temizleniyor…" : "Bekleyenleri şimdi temizle").font(RealFont.inter(14, .medium)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 44).overlay(Capsule().strokeBorder(.white.opacity(0.16)))
            }
            .buttonStyle(.plain).disabled(purging)
            if let note { Text(note).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF9A9AA3)) }
            Spacer(minLength: 0)
        }
        .padding(20)
        .presentationBackground(Color(argb: 0xFF0B0B0D))
        .presentationDetents([.large])
    }

    private func remove(_ file: SpaceFile) {
        guard var card = store.reminders.first(where: { $0.id == file.cardId }) else { return }
        card.attachments.removeAll { $0.id == file.id }
        card.voiceRecordings.removeAll { $0.id == file.id }
        store.update(card)
    }

    private static func size(_ bytes: Int64) -> String {
        bytes < 1_048_576 ? String(format: "%.0f KB", Double(bytes) / 1024) : String(format: "%.1f MB", Double(bytes) / 1_048_576)
    }
}
