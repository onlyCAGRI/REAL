import SwiftUI
import UIKit

/// Minimum app version (AppUpdateGate.kt; set in the admin panel). Below it the app asks for an update; "Şimdilik
/// sadece görüntüle" keeps REAL open read-only: everything can be looked at, nothing added or changed (the server
/// refuses writes from this version too, with 426 UPDATE_REQUIRED).
@MainActor
@Observable
final class AppUpdateGate {
    static let shared = AppUpdateGate()
    static var blockedMessage: String { t("Bu sürümde ekleme yapılamaz. Önce REAL'i güncelle.") }
    /// "ios/12": CFBundleVersion (CURRENT_PROJECT_VERSION) as the version code the admin compares.
    nonisolated static let clientTag: String = {
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "ios/\(Int(build).map { max($0, 1) } ?? 1)"
    }()

    var required = false
    var sheetOpen = false

    func markRequired() {
        if !required { sheetOpen = true }
        required = true
    }

    /// False (with a short note and the update sheet) when this version may only look.
    func allowWrite() -> Bool {
        guard required else { return true }
        RealToast.shared.show(Self.blockedMessage)
        sheetOpen = true
        return false
    }

    /// For the stores (not actor-bound, called from the UI on the main thread): false = this version may only look.
    nonisolated static func writable() -> Bool {
        guard Thread.isMainThread else { return true }
        return MainActor.assumeIsolated { shared.allowWrite() }
    }

    /// Called by every worker request: 426 = this version may only read.
    nonisolated static func observe(status: Int) {
        guard status == 426 else { return }
        Task { @MainActor in AppUpdateGate.shared.markRequired() }
    }

    /// Asked at start (signed in): is this version still allowed to write?
    func check() async {
        guard let data = try? await RealBackend.request("/api/app/v1/status", timeout: 15),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        if json["updateRequired"] as? Bool == true { markRequired() } else { required = false; sheetOpen = false }
    }
}

/// "Güncelleme gerekli" (RequiredUpdateHost): Güncelle opens the store / TestFlight page; the second button keeps REAL
/// open read-only.
struct RequiredUpdateHost: View {
    let uid: String?
    @MainActor private var gate: AppUpdateGate { AppUpdateGate.shared }

    var body: some View {
        ZStack {
            if gate.required && gate.sheetOpen {
                PermissionSheet(title: t("Güncelleme gerekli"),
                                description: t("Bu REAL sürümü artık desteklenmiyor. Güncelleyince her şey kaldığı yerden devam eder; kartların telefonunda güvende."),
                                primaryLabel: t("Güncelle"), secondaryLabel: t("Şimdilik sadece görüntüle"), glyph: .star,
                                onPrimary: openStore, onSecondary: { gate.sheetOpen = false })
                    .transition(.opacity)
            }
        }
        .task(id: uid) { if uid != nil { await gate.check() } }
    }

    private func openStore() {
        // TestFlight while REAL is in testing; the App Store page once it is published.
        if let url = URL(string: "itms-beta://"), UIApplication.shared.canOpenURL(url) { UIApplication.shared.open(url) }
        else if let url = URL(string: "https://testflight.apple.com") { UIApplication.shared.open(url) }
        // The sheet comes back next time if the app is still old.
        gate.sheetOpen = false
    }
}
