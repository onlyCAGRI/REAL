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
        // Test builds exchange the debug token registered in the Firebase console for App Check tokens
        // (simulators and devices without App Attest). A small provider of our own does the exchange, so it
        // always uses exactly that token.
        if !BuildSecrets.appCheckDebugToken.isEmpty {
            AppCheck.setAppCheckProviderFactory(RealDebugAppCheckFactory(debugToken: BuildSecrets.appCheckDebugToken))
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

    // MARK: Account deletion (FirebaseAuthRepository.reauthenticateWithGoogle / deleteAccount)

    /// The server asks for a sign-in from the last 5 minutes before deleting; this signs in with Google again.
    func reauthenticateWithGoogle() async throws {
        guard let user = Auth.auth().currentUser, let presenter = Self.topViewController() else { throw URLError(.userAuthenticationRequired) }
        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
        guard let idToken = result.user.idToken?.tokenString else { throw URLError(.userAuthenticationRequired) }
        let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: result.user.accessToken.tokenString)
        try await user.reauthenticate(with: credential)
        _ = try await user.getIDTokenResult(forcingRefresh: true)
    }

    func deleteFirebaseUser() async throws {
        guard let user = Auth.auth().currentUser else { return }
        try await user.delete()
        GIDSignIn.sharedInstance.signOut()
        refreshAccount()
    }

    // MARK: Name shown to others

    private var nameRevision = 0
    /// Hesap menüsü → Adın: opens the same window again.
    var nameEditRequested = false
    /// Google gives the full name ("Ad Soyad"); once per account REAL asks which name others should see.
    var needsNameChoice: Bool {
        _ = nameRevision
        guard let uid = account?.uid else { return false }
        if nameEditRequested { return true }
        return !UserDefaults.standard.bool(forKey: "profile_name_confirmed_\(uid)")
    }
    func confirmName() {
        guard let uid = account?.uid else { return }
        UserDefaults.standard.set(true, forKey: "profile_name_confirmed_\(uid)")
        nameEditRequested = false
        nameRevision += 1
    }

    /// Saves the Firebase display name; the worker reads it from a fresh ID token.
    func updateDisplayName(_ name: String) async throws {
        let clean = name.split(whereSeparator: \.isWhitespace).joined(separator: " ")
        guard !clean.isEmpty else { throw CalendarCardPublisher.Failure(errorDescription: "Adını yaz.") }
        guard clean.count <= 80, !clean.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else {
            throw CalendarCardPublisher.Failure(errorDescription: "Ad en fazla 80 karakter olmalı.")
        }
        guard let user = Auth.auth().currentUser, user.uid == account?.uid else { throw URLError(.userAuthenticationRequired) }
        let change = user.createProfileChangeRequest()
        change.displayName = clean
        try await change.commitChanges()
        _ = try await user.getIDTokenResult(forcingRefresh: true)
        refreshAccount()
        confirmName()
    }

    /// Headers for REAL worker requests.
    nonisolated static func requestHeaders() async throws -> [String: String] {
        guard let user = await MainActor.run(body: { Auth.auth().currentUser }) else {
            throw URLError(.userAuthenticationRequired)
        }
        let idToken = try await user.getIDToken()
        let appCheck: AppCheckToken
        do {
            appCheck = try await AppCheck.appCheck().token(forcingRefresh: false)
        } catch {
            throw AppCheckUnavailable(reason: (error as NSError).localizedDescription)
        }
        return ["Authorization": "Bearer \(idToken)", "X-Firebase-AppCheck": appCheck.token]
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

/// App Check could not produce a token; REAL's worker requires one on every account request.
struct AppCheckUnavailable: Error { let reason: String }

/// Exchanges the console-registered debug token for an App Check token (projects.apps.exchangeDebugToken).
final class RealDebugAppCheckFactory: NSObject, AppCheckProviderFactory {
    private let debugToken: String
    init(debugToken: String) { self.debugToken = debugToken }
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? { RealDebugAppCheckProvider(app: app, debugToken: debugToken) }
}

final class RealDebugAppCheckProvider: NSObject, AppCheckProvider {
    private let debugToken: String
    private let appID: String
    private let projectID: String
    private let apiKey: String

    init(app: FirebaseApp, debugToken: String) {
        self.debugToken = debugToken
        appID = app.options.googleAppID
        projectID = app.options.projectID ?? ""
        apiKey = app.options.apiKey ?? ""
    }

    func getToken(completion handler: @escaping (AppCheckToken?, Error?) -> Void) {
        let path = "https://firebaseappcheck.googleapis.com/v1/projects/\(projectID)/apps/\(appID):exchangeDebugToken?key=\(apiKey)"
        guard let url = URL(string: path) else { handler(nil, URLError(.badURL)); return }
        var request = URLRequest(url: url, timeoutInterval: 20)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(Bundle.main.bundleIdentifier, forHTTPHeaderField: "X-Ios-Bundle-Identifier")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["debugToken": debugToken])
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error { handler(nil, error); return }
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            guard (200..<300).contains(status), let data,
                  let body = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let token = body["token"] as? String else {
                handler(nil, NSError(domain: "RealAppCheck", code: status,
                                     userInfo: [NSLocalizedDescriptionKey: "App Check jeton değişimi başarısız (\(status))"]))
                return
            }
            let ttl = Double((body["ttl"] as? String)?.replacingOccurrences(of: "s", with: "") ?? "") ?? 3600
            handler(AppCheckToken(token: token, expirationDate: Date().addingTimeInterval(ttl)), nil)
        }.resume()
    }
}
