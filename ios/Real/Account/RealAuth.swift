import SwiftUI
import UIKit
import FirebaseCore
import FirebaseAuth
import FirebaseAppCheck
import GoogleSignIn

/// REAL account on iOS (AndroidBackend.auth + AndroidAuthUiController): Google sign-in through Firebase Auth, and the
/// two tokens every REAL worker request carries (Firebase ID token + App Check token).
/// Firebase is configured only when GoogleService-Info.plist is in the bundle (CI injects it from repository secrets),
/// so local builds without it keep running with sign-in shown as unavailable.
@MainActor
@Observable
final class RealAuth {
    static let shared = RealAuth()

    struct Account: Equatable {
        let uid: String
        let displayName: String?
        let email: String?
        var firstName: String? { displayName?.split(separator: " ").first.map(String.init) }
    }

    private(set) var isAvailable = false
    private(set) var account: Account?
    private(set) var busy = false
    var lastError: String?

    private var stateListener: AuthStateDidChangeListenerHandle?

    /// Called once from the app delegate, before anything touches Firebase.
    func configure() {
        guard !isAvailable, Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return }
        // Test builds use App Check's debug provider with a token registered in the Firebase console
        // (simulators and devices without App Attest). The provider reads it from this user default.
        if !BuildSecrets.appCheckDebugToken.isEmpty {
            UserDefaults.standard.set(BuildSecrets.appCheckDebugToken, forKey: "FIRAAppCheckDebugToken")
            AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        }
        FirebaseApp.configure()
        if let clientID = FirebaseApp.app()?.options.clientID {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        }
        isAvailable = true
        refreshAccount()
        stateListener = Auth.auth().addStateDidChangeListener { [weak self] _, _ in
            Task { @MainActor in self?.refreshAccount() }
        }
    }

    private func refreshAccount() {
        account = Auth.auth().currentUser.map { Account(uid: $0.uid, displayName: $0.displayName, email: $0.email) }
    }

    /// Google → Firebase credential. Returns the signed-in account, or nil when cancelled / failed (see lastError).
    func signInWithGoogle() async -> Account? {
        guard isAvailable else {
            lastError = "Google ile giriş bu sürümde henüz kullanılamıyor."
            return nil
        }
        guard let presenter = Self.topViewController() else { return nil }
        busy = true
        lastError = nil
        defer { busy = false }
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
            guard let idToken = result.user.idToken?.tokenString else {
                lastError = "Google girişi tamamlanamadı."
                return nil
            }
            let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: result.user.accessToken.tokenString)
            _ = try await Auth.auth().signIn(with: credential)
            refreshAccount()
            return account
        } catch {
            let nsError = error as NSError
            if nsError.domain == kGIDSignInErrorDomain && nsError.code == GIDSignInError.canceled.rawValue { return nil }
            lastError = "Google ile giriş yapılamadı."
            return nil
        }
    }

    func signOut() {
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
        refreshAccount()
    }

    func handle(url: URL) -> Bool { GIDSignIn.sharedInstance.handle(url) }

    /// Headers for REAL worker requests.
    nonisolated static func requestHeaders() async throws -> [String: String] {
        guard let user = await MainActor.run(body: { Auth.auth().currentUser }) else {
            throw URLError(.userAuthenticationRequired)
        }
        let idToken = try await user.getIDToken()
        var headers = ["Authorization": "Bearer \(idToken)"]
        if let appCheck = try? await AppCheck.appCheck().token(forcingRefresh: false) {
            headers["X-Firebase-AppCheck"] = appCheck.token
        }
        return headers
    }

    private static func topViewController() -> UIViewController? {
        let root = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }
            .first
        var top = root
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
