import Foundation
import Observation

/// AppLanguage.kt: the language chosen in Settings.
enum AppLanguage: String, CaseIterable {
    case system
    case turkish = "tr"
    case english = "en"
}

/// I18n.kt: Turkish strings are keys; the English values live in the Android en.json resource.
enum I18n {
    static let defaultsKey = "app_language"

    private static let dictionary: [String: String] = {
        guard let url = Bundle.main.url(forResource: "en", withExtension: "json")
                ?? Bundle.main.url(forResource: "en", withExtension: "json", subdirectory: "i18n"),
              let data = try? Data(contentsOf: url),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: String] else {
            return [:]
        }
        return json
    }()

    static var selected: AppLanguage {
        let raw = UserDefaults.standard.string(forKey: defaultsKey)
        return AppLanguage(rawValue: raw ?? "") ?? .system
    }

    /// The phone's first preferred language decides "system"; anything other than Turkish is English.
    /// This mirrors Android's phoneLanguage() != "tr".
    static var isEnglish: Bool {
        switch selected {
        case .english: return true
        case .turkish: return false
        case .system:
            guard let first = Locale.preferredLanguages.first else { return false }
            return !first.hasPrefix("tr")
        }
    }

    static var locale: Locale {
        Locale(identifier: isEnglish ? "en_US" : "tr_TR")
    }

    static func translate(_ tr: String) -> String {
        isEnglish ? (dictionary[tr] ?? tr) : tr
    }
}

/// Global translator for Turkish keys; the variadic overload fills {0}, {1}, ...
func t(_ tr: String) -> String {
    I18n.translate(tr)
}

func t(_ tr: String, _ args: Any...) -> String {
    var out = I18n.translate(tr)
    for (index, arg) in args.enumerated() {
        out = out.replacingOccurrences(of: "{\(index)}", with: "\(arg)")
    }
    return out
}

/// Context-sensitive translation: looks up "context:tr" first, then falls back to the plain key.
func tIn(_ context: String, _ tr: String) -> String {
    let key = "\(context):\(tr)"
    let translated = I18n.translate(key)
    return translated == key ? t(tr) : translated
}

/// Runtime language choice. Changing it persists the choice and bumps revision so the app root rebuilds.
@MainActor
@Observable
final class LanguageSettings {
    static let shared = LanguageSettings()

    private(set) var choice: AppLanguage
    private(set) var revision = 0

    init() {
        let raw = UserDefaults.standard.string(forKey: I18n.defaultsKey)
        choice = AppLanguage(rawValue: raw ?? "") ?? .system
    }

    var currentLabel: String {
        switch choice {
        case .system: return t("Sistem")
        case .turkish: return t("Türkçe")
        case .english: return "English"
        }
    }

    func set(_ language: AppLanguage) {
        choice = language
        UserDefaults.standard.set(language.rawValue, forKey: I18n.defaultsKey)
        revision &+= 1
    }
}
