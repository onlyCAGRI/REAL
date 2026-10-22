import UIKit
import FirebaseAuth
import FirebaseMessaging

/// PanoPush.kt on iOS: this iPhone's FCM token is kept on the signed-in account, and the server's tiny "pull now"
/// messages (no card content) wake the same syncs the app runs every minute while open.
@MainActor
final class RealPush: NSObject, MessagingDelegate {
    static let shared = RealPush()
    private weak var store: ReminderStore?
    private weak var life: LifeStore?
    private weak var pano: PanoStore?
    private var authListener: AuthStateDidChangeListenerHandle?
    private static let sentKey = "real_push_sent"

    /// App delegate, after Firebase is configured: APNs registration and the token's owner follow the account.
    func start(_ application: UIApplication) {
        guard RealAuth.shared.isAvailable, authListener == nil else { return }
        Messaging.messaging().delegate = self
        application.registerForRemoteNotifications()
        authListener = Auth.auth().addStateDidChangeListener { _, _ in Task { @MainActor in RealPush.shared.register() } }
    }

    func attach(_ store: ReminderStore, _ life: LifeStore, _ pano: PanoStore) {
        self.store = store
        self.life = life
        self.pano = pano
    }

    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        Task { @MainActor in RealPush.shared.register(fcmToken) }
    }

    /// Uploads the token for the signed-in account; skipped when the same account+token was already sent.
    func register(_ known: String? = nil) {
        Task {
            guard let uid = RealAuth.shared.account?.uid else { return }
            guard let token = known ?? (try? await Messaging.messaging().token()) else { return }
            guard UserDefaults.standard.string(forKey: Self.sentKey) != "\(uid):\(token)" else { return }
            if (try? await RealBackend.post("/api/boards/v1/push-token", ["token": token], timeout: 20)) != nil {
                UserDefaults.standard.set("\(uid):\(token)", forKey: Self.sentKey)
            }
        }
    }

    /// Before signing out: the token leaves this account so the next one can take it. Best effort, a few seconds.
    func release() async {
        guard RealAuth.shared.account != nil, let token = try? await Messaging.messaging().token() else { return }
        _ = try? await RealBackend.request("/api/boards/v1/push-token", method: "DELETE",
                                           body: try? JSONSerialization.data(withJSONObject: ["token": token]), timeout: 4)
        UserDefaults.standard.removeObject(forKey: Self.sentKey)
    }

    /// A data message: run the matching pull. Returns whether anything was fetched.
    func handle(_ userInfo: [AnyHashable: Any]) async -> Bool {
        guard let store, let life else { return false }
        switch userInfo["type"] as? String {
        case "share_inbox": await CardShareClient.receive(store: store, life: life)
        case "sync": await RealCloudSync.shared.run(store, life)
        case "category_cards": await SharedCategorySync.shared.syncNow(store)
        case "pano_item": await pano?.sync()
        default: return false
        }
        return true
    }
}
