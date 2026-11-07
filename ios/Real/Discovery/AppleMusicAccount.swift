import SwiftUI

/// AppleMusicAccount.kt: Ayarlar → Apple Music hesabı. REAL's public sign-in page (real.realapp.workers.dev) runs
/// Apple's MusicKit sign-in and sends the Music User Token back as real://apple-music?state=…#token=…; it stays on
/// this phone. With it Müzik keşfi offers "Sana özel" and a right swipe also adds the song to a "REAL" playlist in Apple Music.
@MainActor
@Observable
final class AppleMusicAccount {
    static let shared = AppleMusicAccount()
    private static let connectPage = "https://real.realapp.workers.dev/music/connect"
    private let defaults = UserDefaults.standard

    private(set) var isConnected: Bool

    private init() {
        // Earlier builds kept the token in UserDefaults (part of device backups); move it to the Keychain once.
        if let old = defaults.string(forKey: "real_apple_music_token") {
            if !old.isEmpty, TokenKeychain.read() == nil { TokenKeychain.write(old) }
            defaults.removeObject(forKey: "real_apple_music_token")
        }
        isConnected = TokenKeychain.read() != nil
    }

    private var token: String? { TokenKeychain.read() }

    /// The Music User Token lives in the Keychain, this device only: not in backups, not moved to a new phone.
    private enum TokenKeychain {
        private static var query: [String: Any] { [kSecClass as String: kSecClassGenericPassword,
                                                   kSecAttrService as String: "real.apple-music",
                                                   kSecAttrAccount as String: "music-user-token"] }
        static func read() -> String? {
            var q = query
            q[kSecReturnData as String] = true
            q[kSecMatchLimit as String] = kSecMatchLimitOne
            var out: AnyObject?
            guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data,
                  let value = String(data: data, encoding: .utf8), !value.isEmpty else { return nil }
            return value
        }
        @discardableResult static func write(_ value: String) -> Bool {
            delete()
            var q = query
            q[kSecValueData as String] = Data(value.utf8)
            q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            return SecItemAdd(q as CFDictionary, nil) == errSecSuccess
        }
        static func delete() { SecItemDelete(query as CFDictionary) }
    }

    /// The sign-in page in Safari; a fresh state ties the answer to this request.
    func connect() {
        var bytes = [UInt8](repeating: 0, count: 24)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let state = Data(bytes).base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        defaults.set(state, forKey: "real_apple_music_state")
        if let url = URL(string: "\(Self.connectPage)?state=\(state)") { UIApplication.shared.open(url) }
    }

    /// real://apple-music…: true when the link was the page's answer (handled, right or wrong).
    func handle(_ url: URL) -> Bool {
        guard url.scheme == "real", url.host == "apple-music" else { return false }
        let state = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "state" }?.value
        let token = url.fragment?.split(separator: "&").first { $0.hasPrefix("token=") }
            .map { String($0.dropFirst(6)).removingPercentEncoding ?? "" }
        if let expected = defaults.string(forKey: "real_apple_music_state"), state == expected,
           let token, !token.isEmpty, token.count <= 4096, TokenKeychain.write(token) {
            defaults.removeObject(forKey: "real_apple_music_state")
            isConnected = true
            RealToast.shared.show(t("Apple Music bağlandı"))
        } else {
            RealToast.shared.show(t("Apple Music bağlantısı tamamlanmadı"))
        }
        return true
    }

    func disconnect() {
        TokenKeychain.delete()
        defaults.removeObject(forKey: "real_apple_music_state")
        defaults.removeObject(forKey: "real_apple_music_playlist")
        defaults.removeObject(forKey: "real_apple_music_list_copied")
        isConnected = false
    }

    private func call(_ body: [String: Any]) async throws -> [String: Any]? {
        guard let token else { return nil }
        var body = body
        body["userToken"] = token
        do { return try await RealBackend.post("/api/music-me", body, timeout: 25) }
        catch let failure as RealBackend.Failure where failure.status == 401 {
            // Apple no longer accepts the token (signed out, subscription ended…): back to "not connected".
            disconnect(); return nil
        }
    }

    /// Songs Apple picks for the person within these iTunes genre ids; the Worker's song objects.
    func personalSongs(genreIds: [Int]) async -> [[String: Any]] {
        let json = (try? await call(["action": "discover", "genres": genreIds])) ?? nil
        return (json?["songs"] as? [[String: Any]]) ?? []
    }

    /// Once per connection: the songs already in "Müzik listem" go to the "REAL" playlist too (100 per call, in the
    /// list's order). Marked done only when every part went through, so a failure is tried again next time.
    func copyListOnce(_ songIds: [String]) async {
        guard token != nil, !defaults.bool(forKey: "real_apple_music_list_copied") else { return }
        var seen = Set<String>()
        let ids = songIds.filter { !$0.isEmpty && $0.allSatisfy(\.isNumber) && seen.insert($0).inserted }
        for start in stride(from: 0, to: ids.count, by: 100) {
            var body: [String: Any] = ["action": "add", "songIds": Array(ids[start..<min(start + 100, ids.count)])]
            if let playlist = defaults.string(forKey: "real_apple_music_playlist") { body["playlistId"] = playlist }
            guard let json = (try? await call(body)) ?? nil else { return }
            if let id = json["playlistId"] as? String, id.hasPrefix("p.") { defaults.set(id, forKey: "real_apple_music_playlist") }
            guard json["ok"] as? Bool == true else { return }
        }
        defaults.set(true, forKey: "real_apple_music_list_copied")
    }

    /// Adds an Apple Music catalog song to the person's "REAL" playlist (made on the first save; its id is kept here).
    func addToLibrary(_ songId: String) async {
        guard !songId.isEmpty, songId.allSatisfy(\.isNumber) else { return }
        var body: [String: Any] = ["action": "add", "songId": songId]
        if let playlist = defaults.string(forKey: "real_apple_music_playlist") { body["playlistId"] = playlist }
        let json = (try? await call(body)) ?? nil
        if let id = json?["playlistId"] as? String, id.hasPrefix("p.") { defaults.set(id, forKey: "real_apple_music_playlist") }
    }
}
