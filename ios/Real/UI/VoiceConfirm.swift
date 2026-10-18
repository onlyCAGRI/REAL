import SwiftUI
import CoreLocation

/// "Doğru mu anladım?" (Android VoiceUnderstanding.kt / VoiceSubstitutions): shown before saving when the AI was
/// unsure, or when a card names something the person never said (a misheard foreign word turned into "When Kitchen").
/// Doğru saves, "Yeri yaz" lets the person type the place (its address is looked up), Düzelt puts the sentence into
/// the composer.
struct VoiceHeard {
    var understood: String
    var unsure: [(heard: String, meant: String?)]
    var confident: Bool

    static func parse(_ o: [String: Any]?, transcript: String) -> VoiceHeard? {
        guard let o else { return nil }
        let lower = transcript.lowercased(with: Locale(identifier: "tr_TR"))
        var unsure: [(String, String?)] = []
        for entry in (o["unsure"] as? [[String: Any]]) ?? [] {
            let heard = ((entry["heard"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            guard !heard.isEmpty, lower.contains(heard.lowercased(with: Locale(identifier: "tr_TR"))),
                  !unsure.contains(where: { $0.0.caseInsensitiveCompare(heard) == .orderedSame }) else { continue }
            let meant = ((entry["meant"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            unsure.append((heard, meant.isEmpty || meant.caseInsensitiveCompare(heard) == .orderedSame ? nil : meant))
            if unsure.count >= 6 { break }
        }
        return VoiceHeard(understood: ((o["understood"] as? String) ?? "").trimmingCharacters(in: .whitespaces),
                          unsure: unsure, confident: unsure.isEmpty && (o["confident"] as? Bool ?? true))
    }
}

enum VoiceSubstitutions {
    private static func key(_ word: String) -> String {
        let base = word.split(separator: "'").first.map(String.init) ?? word
        return base.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "tr_TR"))
            .replacingOccurrences(of: "ı", with: "i").filter { $0.isLetter || $0.isNumber }
    }

    /// Names on the cards that match no spoken word; a word counts as said when a spoken word starts with its first
    /// five letters ("Belediyesi" ~ "belediyesinde"). Recommendations ("bana film öner") are skipped.
    static func names(transcript: String, titles: [String], places: [String], content: [String]) -> [String] {
        let spoken = Set(transcript.split(whereSeparator: { !$0.isLetter && !$0.isNumber }).map { key(String($0)) })
        let recommends = ["oner", "tavsiye", "recommend", "suggest"].contains { k in spoken.contains { $0.hasPrefix(k) } }
        func said(_ word: String) -> Bool {
            let k = key(word)
            if k.count < 3 || k.allSatisfy(\.isNumber) { return true }
            let stem = String(k.prefix(5))
            return spoken.contains { $0.hasPrefix(stem) || (k.count <= 5 && $0 == k) }
        }
        let titleNames = titles.flatMap { $0.split(separator: " ").dropFirst().map(String.init).filter { $0.first?.isUppercase == true } }
        let placeWords = places.flatMap { $0.split(whereSeparator: { $0 == " " || $0 == "," }).map(String.init) }
        let contentWords = recommends ? [] : content.flatMap { $0.split(separator: " ").map(String.init) }
        var seen = Set<String>()
        return (titleNames + placeWords + contentWords)
            .map { $0.trimmingCharacters(in: CharacterSet(charactersIn: ",.;:!?\"")) }
            .filter { !$0.isEmpty && !said($0) && seen.insert(key($0)).inserted }
            .prefix(6).map { $0 }
    }
}

/// A voice place -> a card location with address and map position (Apple's geocoder; the city when the venue is
/// unknown; name and Maps link only when nothing is found).
enum VoicePlaceLookup {
    static func resolve(_ place: String) async -> ReminderLocation {
        let name = place.trimmingCharacters(in: .whitespaces)
        let query = name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? name
        var location = ReminderLocation(placeName: name, mapsUrl: "https://maps.apple.com/?q=\(query)")
        let geocoder = CLGeocoder()
        var mark = try? await geocoder.geocodeAddressString(name).first
        if mark?.location == nil, let comma = name.lastIndex(of: ",") {
            let city = name[name.index(after: comma)...].trimmingCharacters(in: .whitespaces)
            if city.count >= 2 { mark = try? await geocoder.geocodeAddressString(city).first }
        }
        if let mark, let coordinate = mark.location?.coordinate {
            location.latitude = coordinate.latitude
            location.longitude = coordinate.longitude
            location.address = [mark.name, mark.thoroughfare, mark.locality, mark.country].compactMap { $0 }
                .reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }.joined(separator: ", ")
        }
        return location
    }
}

struct VoiceConfirmSheet: View {
    let pending: VoiceAI.Pending
    let onConfirm: (_ place: String?) -> Void
    let onCorrect: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var editingPlace = false
    @State private var place = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Doğru mu anladım?").font(RealFont.inter(13, .medium)).foregroundStyle(Color(red: 0.57, green: 0.73, blue: 1))
            Text(sentence).font(RealFont.inter(17)).foregroundStyle(.white)
            if editingPlace {
                TextField("Yer (örn. When Kitchen, Ordu)", text: $place)
                    .textFieldStyle(.plain)
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.08)))
                    .submitLabel(.done)
                    .onSubmit(savePlace)
                Text("Adresini ben bulurum.").font(RealFont.inter(12)).foregroundStyle(Color(white: 0.56))
            }
            HStack(spacing: 8) {
                if editingPlace {
                    chip("Kaydet", filled: true, action: savePlace)
                } else {
                    chip("Doğru", filled: true) { dismiss(); onConfirm(nil) }
                    if let p = pending.place {
                        chip("Yeri yaz", filled: false) { place = p; editingPlace = true }
                    }
                    chip("Düzelt", filled: false) { dismiss(); onCorrect() }
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .presentationDetents([.height(editingPlace ? 300 : 210)])
        .presentationBackground(Color(red: 0.08, green: 0.10, blue: 0.16))
        .preferredColorScheme(.dark)
    }

    private func savePlace() {
        let typed = place.trimmingCharacters(in: .whitespaces)
        guard !typed.isEmpty else { return }
        dismiss()
        onConfirm(typed)
    }

    /// The sentence with every unsure / substituted word underlined.
    private var sentence: AttributedString {
        var text = AttributedString(pending.display)
        for word in pending.underlined {
            if let range = text.range(of: word, options: .caseInsensitive) {
                text[range].underlineStyle = .single
                text[range].foregroundColor = Color(red: 0.41, green: 0.67, blue: 1)
            }
        }
        return text
    }

    private func chip(_ label: String, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(RealFont.inter(14, .medium))
                .foregroundStyle(filled ? Color(red: 0.04, green: 0.10, blue: 0.20) : .white)
                .padding(.horizontal, 16).padding(.vertical, 9)
                .background(Capsule().fill(filled ? Color(red: 0.57, green: 0.73, blue: 1) : Color.white.opacity(0.12)))
        }
        .buttonStyle(.plain)
    }
}
