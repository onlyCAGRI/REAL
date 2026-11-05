import Foundation

/// MusicBlock.kt: an iTunes track attached to a card ("music:<id>" detail block). No audio is downloaded.
extension MusicBlock {
    /// Read from Spotify's page before cards moved to Apple's preview; each one is replaced at start.
    var hasSpotifyPreview: Bool { previewUrl?.hasPrefix("https://p.scdn.co/") == true }

    /// Apple's terms: a preview it serves carries its credit, also on a song that opens in Spotify.
    var showsITunesCredit: Bool {
        guard let previewUrl else { return false }
        return provider == "iTunes" || previewUrl.range(of: "apple.com", options: .caseInsensitive) != nil
    }
}

struct MusicBlock: Codable, Hashable, Identifiable {
    var id: String = UUID().uuidString
    var title: String
    var artist: String
    var album: String?
    var artworkUrl: String?
    var provider: String
    var providerTrackId: String?
    var providerUrl: String
    var previewUrl: String?
    var previewDurationMillis: Int64?
    /// Apple Music marks the song explicit ("E"); stored only when true, like Android.
    var explicit: Bool?
    var createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
}

