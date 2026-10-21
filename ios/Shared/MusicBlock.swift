import Foundation

/// MusicBlock.kt: an iTunes track attached to a card ("music:<id>" detail block). No audio is downloaded.
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

