import SwiftUI

/// AccountManagementSheet.kt's Bulut alanı and Veri ve silme: what the account uses in the cloud, deleting that data
/// (this iPhone's copy stays) and starting the backup again afterwards.
@MainActor
@Observable
final class CloudAccountData {
    struct Usage { let used: Int64; let active: Int64; let retained: Int64; let quota: Int64; let files: Int }
    struct Erasure { let operationId: String; let mode: String; let phase: String }

    var usage: Usage?
    var erasure: Erasure?
    var deviceApproved = true
    var busy: String?
    var error: String?

    private var uid: String { RealAuth.shared.account?.uid ?? "" }
    private var restartOperationKey: String { "real_cloud_restart_operation_\(uid)" }
    private var restartErasureKey: String { "real_cloud_restart_erasure_\(uid)" }
    var pendingRestart: Bool { UserDefaults.standard.string(forKey: restartOperationKey) != nil }

    private static func erasure(in json: [String: Any]) -> Erasure? {
        guard let e = json["erasure"] as? [String: Any], let id = e["operationId"] as? String,
              let mode = e["mode"] as? String, let phase = e["phase"] as? String else { return nil }
        return Erasure(operationId: id, mode: mode, phase: phase)
    }

    private static func object(_ data: Data) throws -> [String: Any] {
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw RealBackend.Failure(status: -1) }
        return json
    }

    func refresh() async {
        guard RealAuth.shared.account != nil else { return }
        do {
            let json = try Self.object(try await RealBackend.request("/api/account/v1/usage", timeout: 30))
            if let u = json["usage"] as? [String: Any] {
                func n(_ key: String) -> Int64 { (u[key] as? NSNumber)?.int64Value ?? 0 }
                usage = Usage(used: n("usedBytes"), active: n("activeBytes"), retained: n("retainedBytes"), quota: n("quotaBytes"), files: Int(n("fileCount")))
            }
            erasure = Self.erasure(in: json)
            deviceApproved = (json["backupDeviceApproved"] as? Bool) ?? true
            // The server decides: paused while an erasure stands or another device holds the backup, else back on.
            if erasure != nil || !deviceApproved { RealCloudSync.shared.paused = true }
            else if RealCloudSync.shared.paused && !pendingRestart { RealCloudSync.shared.paused = false }
            error = nil
        } catch {
            self.error = t("Hesap yönetimine şu anda ulaşılamıyor.")
        }
    }

    func purge() async {
        busy = t("Temizle")
        _ = await CloudSpace.shared.purgeRetained()
        busy = nil
        await refresh()
    }

    /// The server wants a fresh sign-in before deleting or restarting.
    private func reauthenticate(password: String?) async throws {
        busy = t("Doğrulanıyor…")
        if let password { try await RealAuth.shared.reauthenticateWithPassword(password) }
        else if RealAuth.shared.usesApple { try await RealAuth.shared.reauthenticateWithApple() }
        else { try await RealAuth.shared.reauthenticateWithGoogle() }
    }

    /// Sync stops first and stays stopped; the erasure can continue later from the phase the server reports.
    func eraseCloud(password: String?) async {
        error = nil
        do {
            try await reauthenticate(password: password)
            busy = t("Bulut verileri siliniyor…")
            RealCloudSync.shared.paused = true
            let op = erasure?.operationId ?? UUID().uuidString.lowercased()
            var json = try await RealBackend.post("/api/account/v1/erasure",
                ["operationId": op, "mode": "CLOUD", "confirmation": "DELETE_CLOUD", "deleteOwnedBoards": true], timeout: 60)
            for _ in 0..<40 {
                if Self.erasure(in: json)?.phase == "COMPLETE" { break }
                try await Task.sleep(for: .seconds(2))
                json = try await RealBackend.post("/api/account/v1/erasure/continue", ["operationId": Self.erasure(in: json)?.operationId ?? op], timeout: 60)
            }
            erasure = Self.erasure(in: json)
            if erasure?.phase == "COMPLETE" { RealToast.shared.show(t("Bulut verileri silindi.")) }
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? RealBackend.message(for: error)
        }
        busy = nil
        await refresh()
    }

    /// Everything still on this iPhone goes up again under a new epoch. Forgetting the sync state gives every record a new id.
    func restartBackup(password: String?, store: ReminderStore, life: LifeStore) async {
        error = nil
        do {
            try await reauthenticate(password: password)
            busy = t("Yedeklemeyi yeniden başlat")
            let defaults = UserDefaults.standard
            let deletion = defaults.string(forKey: restartErasureKey) ?? erasure?.operationId
            guard let deletion else { throw RealBackend.Failure(status: 409) }
            let operation = defaults.string(forKey: restartOperationKey) ?? UUID().uuidString.lowercased()
            // Kept until done: a lost answer retries the same operation, as the server allows.
            defaults.set(deletion, forKey: restartErasureKey)
            defaults.set(operation, forKey: restartOperationKey)
            RealCloudSync.shared.paused = true
            let json = try await RealBackend.post("/api/account/v1/backup/restart",
                ["operationId": operation, "erasureOperationId": deletion, "confirmation": "REUPLOAD_THIS_DEVICE_LOCAL_DATA"], timeout: 60)
            guard let epoch = json["epoch"] as? String else { throw RealBackend.Failure(status: -1) }
            defaults.set(epoch, forKey: "real_cloud_epoch_\(uid)")
            // The erasure also removed push registrations.
            defaults.removeObject(forKey: "real_push_sent")
            RealCloudSync.shared.reset()
            defaults.removeObject(forKey: restartOperationKey)
            defaults.removeObject(forKey: restartErasureKey)
            RealCloudSync.shared.paused = false
            RealPush.shared.register()
            RealToast.shared.show(t("Yedekleme yeniden başladı."))
            await RealCloudSync.shared.runFully(store, life)
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? RealBackend.message(for: error)
        }
        busy = nil
        await refresh()
    }
}

private func cloudSize(_ bytes: Int64) -> String { ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file) }

private struct CloudPanelCard<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 8) { content() }
            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(.white.opacity(0.05)))
    }
}

/// Bulut alanı: used of quota, what waits to be cleaned, and the way to free space.
struct CloudUsagePanel: View {
    let data: CloudAccountData
    @State private var confirmPurge = false
    @State private var showFiles = false

    var body: some View {
        CloudPanelCard {
            Text(t("Bulut alanı")).font(RealFont.inter(15, .medium)).foregroundStyle(.white)
            if let usage = data.usage {
                let fraction = usage.quota > 0 ? min(1, Double(usage.used) / Double(usage.quota)) : 0
                Text("\(cloudSize(usage.used)) / \(cloudSize(usage.quota))").font(RealFont.inter(24)).foregroundStyle(Color(argb: 0xFF91B9FF))
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(.white.opacity(0.08))
                        Capsule().fill(fraction > 0.9 ? Color(argb: 0xFFFFB86F) : Color(argb: 0xFF91B9FF)).frame(width: g.size.width * fraction)
                    }
                }
                .frame(height: 6)
                HStack(alignment: .top) {
                    stat(t("Aktif"), cloudSize(usage.active))
                    stat(t("Temizlenmeyi bekleyen"), cloudSize(usage.retained))
                }
                .padding(.top, 4)
                Text(t("{0} dosya", usage.files)).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A93))
                HStack(spacing: 16) {
                    if fraction > 0.8 { Button(t("Yer aç")) { showFiles = true } }
                    if usage.retained > 0 && data.erasure == nil {
                        if confirmPurge {
                            Button(t("Temizle")) { confirmPurge = false; Task { await data.purge() } }.foregroundStyle(Color(argb: 0xFFFFB4AB))
                            Button(t("Vazgeç")) { confirmPurge = false }
                        } else {
                            Button(t("Bekleyenleri şimdi temizle")) { confirmPurge = true }
                        }
                    }
                }
                .font(RealFont.inter(13, .medium)).foregroundStyle(.white).disabled(data.busy != nil)
                if confirmPurge {
                    Text(t("Silinmiş dosyalar buluttan kalıcı olarak kaldırılır. Aktif dosyaların etkilenmez.")).font(RealFont.inter(12))
                        .foregroundStyle(Color(argb: 0xFF8A8A93)).fixedSize(horizontal: false, vertical: true)
                }
            } else {
                Text(t("Kullanım bilgisi henüz alınamadı.")).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF8A8A93))
            }
        }
        .task { await data.refresh() }
        .sheet(isPresented: $showFiles) { CloudSpaceFilesSheet() }
    }

    private func stat(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(RealFont.inter(14, .medium)).foregroundStyle(.white)
            Text(label).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A93))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Bulut verilerini sil, then Yedeklemeyi yeniden başlat. The account itself is deleted by the sheet's own button.
struct CloudDataPanel: View {
    let data: CloudAccountData
    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life
    @State private var confirmErase = false
    @State private var confirmRestart = false
    @State private var askPassword = false
    @State private var password = ""
    @State private var restarting = false

    private var cloudErased: Bool { data.erasure?.mode == "CLOUD" && data.erasure?.phase == "COMPLETE" }

    private func go(_ typed: String? = nil) {
        if RealAuth.shared.usesPassword && typed == nil { password = ""; askPassword = true; return }
        Task {
            if restarting { await data.restartBackup(password: typed, store: store, life: life) }
            else { await data.eraseCloud(password: typed) }
        }
    }

    var body: some View {
        CloudPanelCard {
            if !data.deviceApproved {
                note(t("Bulut yedeği başka bir cihazda yeniden başlatılmış. Bu iPhone'daki eski veriler otomatik yüklenmez."))
            } else if cloudErased || data.pendingRestart {
                Text(t("Bulut yedeklemesi")).font(RealFont.inter(15, .medium)).foregroundStyle(.white)
                note(t("Bulut verilerin silindi. Bu iPhone'daki kartların duruyor; otomatik eşitleme kapalı."))
                note(t("Bu iPhone'daki kartlar, kategoriler ve planlar yeniden buluta yedeklenir."))
                action(data.pendingRestart ? t("Yeniden başlatmayı tamamla") : t("Yedeklemeyi yeniden başlat"), color: .white) { confirmRestart = true }
            } else if let erasure = data.erasure, erasure.mode == "CLOUD" {
                Text(t("Silme devam ediyor")).font(RealFont.inter(15, .medium)).foregroundStyle(.white)
                action(t("Devam et"), color: .white) { restarting = false; go() }
            } else {
                Text(t("Bulut verilerini sil")).font(RealFont.inter(15, .medium)).foregroundStyle(.white)
                note(t("Buluttaki kartlar, ekler ve paylaşımlar silinir. Yerel verilerin kalır."))
                action(t("Bulut verilerini sil"), color: Color(argb: 0xFFFFB4AB)) { confirmErase = true }
            }
            if let busy = data.busy { note(busy) }
            if let error = data.error { Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFB4AB)).fixedSize(horizontal: false, vertical: true) }
        }
        .confirmationDialog(t("Bulut verilerin silinsin mi?"), isPresented: $confirmErase, titleVisibility: .visible) {
            Button(t("Bulut verilerini sil"), role: .destructive) { restarting = false; go() }
            Button(t("Vazgeç"), role: .cancel) {}
        } message: {
            Text(t("Buluttaki kartların, kategorilerin, planların, eklerin, paylaşım bağlantıların, bağlantıların ve sahip olduğun panolar silinir. Bu iPhone'daki veriler ve hesabın kalır; otomatik eşitleme kapanır. Geri alınamaz."))
        }
        .confirmationDialog(t("Yedeklemeyi yeniden başlat"), isPresented: $confirmRestart, titleVisibility: .visible) {
            Button(t("Doğrula ve başlat")) { restarting = true; go() }
            Button(t("Vazgeç"), role: .cancel) {}
        } message: {
            Text(t("Yalnız bu iPhone'da hâlâ bulunan kartlar, kategoriler ve planlar yeniden yedeklenir. Silinen ortak panolar, arkadaş bağlantıları ve paylaşım linkleri geri gelmez."))
        }
        .alert(t("Şifreni yaz"), isPresented: $askPassword) {
            SecureField(t("Şifre"), text: $password)
            Button(t("Doğrula ve başlat")) { go(password) }
            Button(t("Vazgeç"), role: .cancel) { password = "" }
        }
    }

    private func note(_ text: String) -> some View {
        Text(text).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A93)).fixedSize(horizontal: false, vertical: true)
    }

    private func action(_ label: String, color: Color, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            Text(label).font(RealFont.inter(14, .medium)).foregroundStyle(color)
                .frame(maxWidth: .infinity, minHeight: 44).background(Capsule().fill(.white.opacity(0.08)))
        }
        .buttonStyle(.plain)
        .disabled(data.busy != nil)
    }
}
