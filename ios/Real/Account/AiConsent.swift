import UIKit

/// AiConsent.kt / AndroidAiConsent: before a feature sends anything to an external AI for the first time, the person
/// is asked once per account and feature. The answer is stored on this device; dismissing never counts as agreement.
@MainActor
enum AiConsent {
    enum Feature: String {
        case voice, mood, discovery

        var title: String {
            switch self {
            case .voice: return "Sesli komutları AI ile işle"
            case .mood: return "AI ile öneriler al"
            case .discovery: return "AI ile keşfet"
            }
        }
        var disclosure: String {
            switch self {
            case .voice: return "Komut cihazında çözülemediğinde konuşma metnin ve gerekli kart bilgileri harici AI hizmeti Groq’a gönderilir. AI işlemleri hata yapabilir."
            case .mood: return "Seçtiğin duygu ve öneri tercihleri harici AI hizmeti Groq’a gönderilir. AI önerileri hata içerebilir."
            case .discovery: return "Seçtiğin filtreler ve önceki öneri başlıkları harici AI hizmetleri Google Gemini veya Groq’a gönderilir. AI önerileri hata içerebilir."
            }
        }
    }

    struct Declined: LocalizedError {
        var errorDescription: String? { "AI için veri gönderilmedi. İstersen daha sonra yeniden deneyebilirsin." }
    }

    /// Same disclosure version as Android (AI_DISCLOSURE_VERSION): a new text asks again.
    private static let version = 4

    private static func key(_ uid: String, _ feature: Feature) -> String { "real_ai_consent_v\(version)_\(uid)_\(feature.rawValue)" }

    static func allowed(_ feature: Feature) -> Bool {
        guard let uid = RealAuth.shared.account?.uid else { return false }
        return UserDefaults.standard.bool(forKey: key(uid, feature))
    }

    static func revoke(_ feature: Feature) {
        guard let uid = RealAuth.shared.account?.uid else { return }
        UserDefaults.standard.removeObject(forKey: key(uid, feature))
    }

    /// Returns when the feature may send data; throws Declined otherwise.
    static func require(_ feature: Feature) async throws {
        guard let uid = RealAuth.shared.account?.uid else { throw URLError(.userAuthenticationRequired) }
        if UserDefaults.standard.bool(forKey: key(uid, feature)) { return }
        let allow = await ask(feature)
        guard allow, RealAuth.shared.account?.uid == uid else { throw Declined() }
        UserDefaults.standard.set(true, forKey: key(uid, feature))
    }

    private static func ask(_ feature: Feature) async -> Bool {
        guard let presenter = topViewController() else { return false }
        return await withCheckedContinuation { continuation in
            let alert = UIAlertController(
                title: feature.title,
                message: feature.disclosure + "\n\nİsteğe bağlıdır. İstediğin zaman Ayarlar’dan kapatabilirsin.",
                preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Şimdi değil", style: .cancel) { _ in continuation.resume(returning: false) })
            alert.addAction(UIAlertAction(title: "İzin ver", style: .default) { _ in continuation.resume(returning: true) })
            presenter.present(alert, animated: true)
        }
    }

    private static func topViewController() -> UIViewController? {
        var top = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }
            .first
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
