import SwiftUI
import UIKit
import CryptoKit

/// Profile pictures between people (ProfilePictures.kt): connected people and shared-category co-members only, never
/// public pages. A Humation avatar is always shared; a real photo only when "Fotoğrafımı kişilerimle paylaş" is on (off by default).
@MainActor
enum ProfilePictures {
    private static let side: CGFloat = 256
    private static let maxBytes = 44_000
    private static let memory = NSCache<NSString, UIImage>()
    private static var pushing = false

    private static func key(_ name: String) -> String? { RealAuth.shared.account.map { "\(name)_\($0.uid)" } }

    static var sharePhoto: Bool {
        get { key("share_photo_with_people").map { UserDefaults.standard.bool(forKey: $0) } ?? false }
        set { if let k = key("share_photo_with_people") { UserDefaults.standard.set(newValue, forKey: k) } }
    }

    /// Brings the server in line with this phone's photo; a no-op when nothing changed. Errors wait for the next try.
    static func push() async {
        guard !pushing, let stateKey = key("picture_pushed_state") else { return }
        pushing = true
        defer { pushing = false }
        let revision = UserDefaults.standard.double(forKey: "profile_photo_revision")
        // An avatar is always shared (it is drawn, not a real face); a photo only when the person allowed it.
        let kind = ProfilePhotoStore.kind
        let photo = (kind == "avatar" || sharePhoto) ? ProfilePhotoStore.load() : nil
        let wanted = photo != nil ? "\(kind)/\(revision)" : "none"
        guard UserDefaults.standard.string(forKey: stateKey) != wanted else { return }
        do {
            if let photo {
                guard let base64 = encode(photo) else { return }
                _ = try await RealBackend.request("/api/connections/v1/me/picture", method: "PUT",
                                                  body: try JSONSerialization.data(withJSONObject: ["kind": kind, "image": base64]))
            } else {
                _ = try await RealBackend.request("/api/connections/v1/me/picture", method: "DELETE", body: Data("{}".utf8))
            }
            UserDefaults.standard.set(wanted, forKey: stateKey)
        } catch {}
    }

    /// Another person's picture: memory, then this phone's cache, then the server (cached per version).
    static func load(uid: String, version: Int64) async -> UIImage? {
        let cacheKey = "\(uid)/\(version)" as NSString
        if let hit = memory.object(forKey: cacheKey) { return hit }
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("people_pictures")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let prefix = SHA256.hash(data: Data(uid.utf8)).prefix(12).map { String(format: "%02x", $0) }.joined()
        let file = dir.appendingPathComponent("\(prefix)_\(version).jpg")
        var image = UIImage(contentsOfFile: file.path)
        if image == nil, RealAuth.shared.account != nil {
            let path = "/api/connections/v1/people/" + (uid.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? uid) + "/picture"
            if let data = try? await RealBackend.request(path),
               let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let b64 = o["image"] as? String, let bytes = Data(base64Encoded: b64), let decoded = UIImage(data: bytes) {
                // Older versions of this person's picture go.
                for old in (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
                    where old.lastPathComponent.hasPrefix(prefix + "_") { try? FileManager.default.removeItem(at: old) }
                try? bytes.write(to: file, options: .atomic)
                image = decoded
            }
        }
        if let image { memory.setObject(image, forKey: cacheKey) }
        return image
    }

    /// Centre-cropped 256 pt JPEG, at most ~44 KB.
    private static func encode(_ image: UIImage) -> String? {
        let s = min(image.size.width, image.size.height)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let out = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { _ in
            let scale = side / max(s, 1)
            let w = image.size.width * scale, h = image.size.height * scale
            image.draw(in: CGRect(x: (side - w) / 2, y: (side - h) / 2, width: w, height: h))
        }
        for q in [0.82, 0.7, 0.58, 0.46] {
            if let data = out.jpegData(compressionQuality: q), data.count <= maxBytes { return data.base64EncodedString() }
        }
        return nil
    }
}
