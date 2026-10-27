import SwiftUI
import UIKit
import FirebaseCore
import FirebaseAuth
import FirebaseAppCheck
import GoogleSignIn
import AuthenticationServices
import CryptoKit

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
            lastError = t("Google ile giriş bu sürümde henüz kullanılamıyor.")
            return nil
        }
        guard let presenter = Self.topViewController() else { return nil }
        busy = true
        lastError = nil
        defer { busy = false }
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
            guard let idToken = result.user.idToken?.tokenString else {
                lastError = t("Google girişi tamamlanamadı.")
                return nil
            }
            let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: result.user.accessToken.tokenString)
            _ = try await Auth.auth().signIn(with: credential)
            refreshAccount()
            return account
        } catch {
            let nsError = error as NSError
            if nsError.domain == kGIDSignInErrorDomain && nsError.code == GIDSignInError.canceled.rawValue { return nil }
            lastError = t("Google ile giriş yapılamadı.")
            return nil
        }
    }

    // MARK: Apple ile giriş

    /// Apple → Firebase credential (with a one-time nonce). The name comes only on the first sign-in; it is kept then.
    func signInWithApple() async -> Account? {
        guard isAvailable else {
            lastError = t("Apple ile giriş bu sürümde henüz kullanılamıyor.")
            return nil
        }
        busy = true
        lastError = nil
        defer { busy = false }
        let nonce = Self.randomNonce()
        do {
            let apple = try await AppleSignInRequest.run(nonce: nonce)
            guard let data = apple.identityToken, let idToken = String(data: data, encoding: .utf8) else {
                lastError = t("Apple girişi tamamlanamadı.")
                return nil
            }
            let credential = OAuthProvider.appleCredential(withIDToken: idToken, rawNonce: nonce, fullName: apple.fullName)
            let result = try await Auth.auth().signIn(with: credential)
            let name = [apple.fullName?.givenName, apple.fullName?.familyName].compactMap { $0 }.joined(separator: " ")
            if !name.isEmpty, (result.user.displayName ?? "").isEmpty {
                let change = result.user.createProfileChangeRequest()
                change.displayName = name
                try? await change.commitChanges()
                _ = try? await result.user.getIDTokenResult(forcingRefresh: true)
            }
            refreshAccount()
            return account
        } catch {
            if (error as? ASAuthorizationError)?.code == .canceled { return nil }
            lastError = t("Apple ile giriş yapılamadı.")
            return nil
        }
    }

    /// The signed-in account came from Apple (account deletion signs in with Apple again).
    var usesApple: Bool { Auth.auth().currentUser?.providerData.contains { $0.providerID == "apple.com" } ?? false }

    func reauthenticateWithApple() async throws {
        guard let user = Auth.auth().currentUser else { throw URLError(.userAuthenticationRequired) }
        let nonce = Self.randomNonce()
        let apple = try await AppleSignInRequest.run(nonce: nonce)
        guard let data = apple.identityToken, let idToken = String(data: data, encoding: .utf8) else { throw URLError(.userAuthenticationRequired) }
        try await user.reauthenticate(with: OAuthProvider.appleCredential(withIDToken: idToken, rawNonce: nonce, fullName: nil))
        _ = try await user.getIDTokenResult(forcingRefresh: true)
    }

    private static func randomNonce() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return bytes.map { String(format: "%02x", $0) }.joined()
    }

    // MARK: E-posta ile giriş (FirebaseAuthRepository: signInWithEmail / createAccountWithName / sendPasswordReset)

    /// The signed-in account uses e-mail + password (account deletion then asks for the password, not Google).
    var usesPassword: Bool { Auth.auth().currentUser?.providerData.contains { $0.providerID == EmailAuthProviderID } ?? false }

    func signInWithEmail(_ email: String, password: String) async throws -> Account? {
        guard isAvailable else { throw Self.failure(t("Giriş bu sürümde henüz kullanılamıyor.")) }
        do {
            _ = try await Auth.auth().signIn(withEmail: email.trimmingCharacters(in: .whitespaces), password: password)
            refreshAccount()
            return account
        } catch { throw Self.mapped(error) }
    }

    /// New account with the name others see (Ad zorunlu, Soyad isteğe bağlı); the name is saved before returning.
    func createAccount(email: String, password: String, firstName: String, lastName: String) async throws -> Account? {
        guard isAvailable else { throw Self.failure(t("Giriş bu sürümde henüz kullanılamıyor.")) }
        let name = [firstName, lastName].map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }.joined(separator: " ")
        do {
            let result = try await Auth.auth().createUser(withEmail: email.trimmingCharacters(in: .whitespaces), password: password)
            let change = result.user.createProfileChangeRequest()
            change.displayName = name
            try await change.commitChanges()
            _ = try await result.user.getIDTokenResult(forcingRefresh: true)
            refreshAccount()
            confirmName()
            return account
        } catch { throw Self.mapped(error) }
    }

    func sendPasswordReset(_ email: String) async throws {
        do { try await Auth.auth().sendPasswordReset(withEmail: email.trimmingCharacters(in: .whitespaces)) }
        catch { throw Self.mapped(error) }
    }

    /// Account deletion with an e-mail account: the server wants a sign-in from the last 5 minutes.
    func reauthenticateWithPassword(_ password: String) async throws {
        guard let user = Auth.auth().currentUser, let email = user.email else { throw URLError(.userAuthenticationRequired) }
        do {
            try await user.reauthenticate(with: EmailAuthProvider.credential(withEmail: email, password: password))
            _ = try await user.getIDTokenResult(forcingRefresh: true)
        } catch { throw Self.mapped(error) }
    }

    private static func failure(_ text: String) -> Error { CalendarCardPublisher.Failure(errorDescription: text) }

    /// Firebase errors in plain Turkish (FirebaseAuthRepository.mapFailure).
    private static func mapped(_ error: Error) -> Error {
        let code = AuthErrorCode(rawValue: (error as NSError).code)
        let text: String
        switch code {
        case .weakPassword: text = t("Şifre çok zayıf. En az 6 karakter kullanın.")
        case .emailAlreadyInUse: text = t("Bu e-posta adresi zaten başka bir hesapta kullanılıyor.")
        case .wrongPassword, .invalidCredential, .userNotFound, .invalidEmail: text = t("E-posta veya şifre geçersiz.")
        case .tooManyRequests: text = t("Çok fazla deneme yapıldı. Biraz sonra tekrar deneyin.")
        case .networkError: text = t("İnternet bağlantısını kontrol edin.")
        default: text = t("Kimlik doğrulama başarısız.")
        }
        return failure(text)
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
        guard !clean.isEmpty else { throw CalendarCardPublisher.Failure(errorDescription: t("Adını yaz.")) }
        guard clean.count <= 80, !clean.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else {
            throw CalendarCardPublisher.Failure(errorDescription: t("Ad en fazla 80 karakter olmalı."))
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
        // X-REAL-Client: the server keeps each person's app version and may ask an old one to update.
        return ["Authorization": "Bearer \(idToken)", "X-Firebase-AppCheck": appCheck.token, "X-REAL-Client": AppUpdateGate.clientTag]
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

/// One Apple ID request: ASAuthorizationController's delegate as an async call. The SHA-256 of [nonce] goes to Apple,
/// the raw nonce to Firebase.
@MainActor
private final class AppleSignInRequest: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<ASAuthorizationAppleIDCredential, Error>?
    private static var running: AppleSignInRequest?

    static func run(nonce: String) async throws -> ASAuthorizationAppleIDCredential {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = SHA256.hash(data: Data(nonce.utf8)).map { String(format: "%02x", $0) }.joined()
        let delegate = AppleSignInRequest()
        running = delegate
        defer { running = nil }
        return try await withCheckedThrowingContinuation { continuation in
            delegate.continuation = continuation
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = delegate
            controller.presentationContextProvider = delegate
            controller.performRequests()
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        MainActor.assumeIsolated {
            if let credential = authorization.credential as? ASAuthorizationAppleIDCredential { continuation?.resume(returning: credential) }
            else { continuation?.resume(throwing: ASAuthorizationError(.failed)) }
            continuation = nil
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        MainActor.assumeIsolated {
            continuation?.resume(throwing: error)
            continuation = nil
        }
    }

    nonisolated func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first ?? ASPresentationAnchor()
        }
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
                                     userInfo: [NSLocalizedDescriptionKey: t("App Check jeton değişimi başarısız ({0})", status)]))
                return
            }
            let ttl = Double((body["ttl"] as? String)?.replacingOccurrences(of: "s", with: "") ?? "") ?? 3600
            handler(AppCheckToken(token: token, expirationDate: Date().addingTimeInterval(ttl)), nil)
        }.resume()
    }
}
