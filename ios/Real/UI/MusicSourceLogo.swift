import SwiftUI

/// MusicSourceLogo.kt: single-colour source marks. Spotify icon (Simple Icons, CC0) in white, as Spotify allows on
/// dark backgrounds; Apple Music app icon, official white version (Apple Marketing Tools white.svg), unmodified.
enum MusicSource: String, CaseIterable {
    case apple, spotify
    var label: String { self == .apple ? "Apple Music" : "Spotify" }
    static var saved: MusicSource {
        get { MusicSource(rawValue: UserDefaults.standard.string(forKey: "music_source") ?? "") ?? .apple }
        set { UserDefaults.standard.set(newValue.rawValue, forKey: "music_source") }
    }
}

func musicProviderLabel(_ provider: String) -> String { provider == "iTunes" ? "Apple Music" : provider }

private let spotifyPath = SvgPath.parse("M12 0C5.4 0 0 5.4 0 12s5.4 12 12 12 12-5.4 12-12S18.66 0 12 0zm5.521 17.34c-.24.359-.66.48-1.021.24-2.82-1.74-6.36-2.101-10.561-1.141-.418.122-.779-.179-.899-.539-.12-.421.18-.78.54-.9 4.56-1.021 8.52-.6 11.64 1.32.42.18.479.659.301 1.02zm1.44-3.3c-.301.42-.841.6-1.262.3-3.239-1.98-8.159-2.58-11.939-1.38-.479.12-1.02-.12-1.14-.6-.12-.48.12-1.021.6-1.141C9.6 9.9 15 10.561 18.72 12.84c.361.181.54.78.241 1.2zm.12-3.36C15.24 8.4 8.82 8.16 5.16 9.301c-.6.179-1.2-.181-1.38-.721-.18-.601.18-1.2.72-1.381 4.26-1.26 11.28-1.02 15.721 1.621.539.3.719 1.02.419 1.56-.299.421-1.02.599-1.559.3z")
private let appleMusicPath = SvgPath.parse("M28.187,7.195c-0.001-0.867-0.076-1.728-0.282-2.574c-0.372-1.536-1.247-2.713-2.561-3.573 C24.67,0.607,23.93,0.335,23.14,0.193c-0.608-0.109-1.221-0.159-1.838-0.177c-0.048-0.002-0.098-0.009-0.146-0.014H7.032 c-0.178,0.01-0.356,0.02-0.534,0.03c-0.878,0.05-1.75,0.146-2.575,0.472C2.351,1.126,1.219,2.21,0.555,3.768 C0.33,4.296,0.212,4.856,0.129,5.423C0.062,5.883,0.024,6.345,0.011,6.81C0.01,6.846,0.002,6.882-0.001,6.918v14.359 c0.01,0.166,0.019,0.332,0.03,0.498c0.059,0.957,0.181,1.908,0.584,2.787c0.764,1.668,2.041,2.764,3.799,3.291 c0.495,0.149,1.005,0.22,1.519,0.268c0.651,0.062,1.304,0.072,1.957,0.072c4.319,0,8.638,0,12.957-0.002 c0.617,0,1.231-0.039,1.844-0.116c0.967-0.124,1.876-0.412,2.697-0.952c0.988-0.65,1.729-1.512,2.208-2.594 c0.219-0.495,0.344-1.021,0.434-1.555c0.134-0.793,0.163-1.595,0.162-2.397C28.188,16.116,28.191,11.656,28.187,7.195 M20.642,11.882c0,2.237,0.001,4.474-0.001,6.71c0,0.49-0.067,0.971-0.285,1.417c-0.339,0.694-0.894,1.13-1.63,1.339 c-0.41,0.117-0.829,0.184-1.256,0.203c-1.118,0.053-2.084-0.705-2.284-1.804c-0.166-0.907,0.268-1.907,1.221-2.374 c0.38-0.188,0.786-0.293,1.196-0.381c0.444-0.096,0.89-0.18,1.332-0.282c0.322-0.074,0.537-0.271,0.6-0.606 c0.015-0.074,0.021-0.151,0.021-0.227c0.001-2.132,0.001-4.263-0.001-6.394c0-0.073-0.012-0.147-0.03-0.217 c-0.046-0.177-0.175-0.285-0.357-0.275c-0.188,0.011-0.373,0.041-0.558,0.078c-0.893,0.176-1.785,0.357-2.677,0.537 c-0.911,0.183-1.822,0.368-2.734,0.552c-0.538,0.109-1.076,0.217-1.614,0.326c-0.019,0.004-0.038,0.01-0.056,0.015 c-0.325,0.09-0.443,0.239-0.458,0.575c-0.002,0.051-0.001,0.102-0.001,0.154c-0.001,3.056,0,6.113-0.002,9.168 c0,0.494-0.055,0.982-0.253,1.441c-0.327,0.754-0.905,1.223-1.685,1.45c-0.412,0.119-0.835,0.187-1.263,0.202 c-1.126,0.042-2.061-0.706-2.254-1.814c-0.167-0.955,0.27-1.98,1.354-2.439c0.419-0.176,0.859-0.273,1.301-0.365 c0.337-0.071,0.675-0.135,1.011-0.207c0.449-0.098,0.684-0.38,0.703-0.839c0.002-0.06,0.001-0.117,0.001-0.177 c0-3.477,0-6.955,0.002-10.432c0-0.145,0.015-0.294,0.049-0.435c0.082-0.335,0.321-0.526,0.641-0.609 c0.3-0.078,0.605-0.132,0.909-0.194c0.861-0.174,1.722-0.347,2.583-0.52c0.89-0.18,1.779-0.359,2.669-0.539 c0.786-0.159,1.572-0.32,2.36-0.475c0.258-0.051,0.52-0.103,0.78-0.124c0.364-0.029,0.614,0.2,0.651,0.566 c0.009,0.087,0.014,0.175,0.014,0.263C20.643,7.394,20.643,9.638,20.642,11.882L20.642,11.882z")

struct MusicSourceLogo: View {
    let provider: String
    var size: CGFloat = 16

    var body: some View {
        Canvas { ctx, sz in
            let s = min(sz.width, sz.height)
            switch provider {
            case "Spotify":
                ctx.scaleBy(x: s / 24, y: s / 24)
                ctx.fill(spotifyPath, with: .color(.white), style: FillStyle(eoFill: true))
            case "YouTube Music":
                ctx.fill(Path(ellipseIn: CGRect(x: 0, y: 0, width: s, height: s)), with: .color(Color(argb: 0xFFFF0000)))
                ctx.stroke(Path(ellipseIn: CGRect(x: s * 0.2, y: s * 0.2, width: s * 0.6, height: s * 0.6)), with: .color(.white), lineWidth: s * 0.06)
                var play = Path()
                play.move(to: CGPoint(x: s * 0.42, y: s * 0.36)); play.addLine(to: CGPoint(x: s * 0.66, y: s * 0.5)); play.addLine(to: CGPoint(x: s * 0.42, y: s * 0.64)); play.closeSubpath()
                ctx.fill(play, with: .color(.white))
            default:
                ctx.scaleBy(x: s / 28.195, y: s / 28.195)
                ctx.fill(appleMusicPath, with: .color(.white), style: FillStyle(eoFill: true))
            }
        }
        .frame(width: size, height: size)
        .accessibilityLabel(musicProviderLabel(provider))
    }
}

/// Opens the song in the phone's own app (Spotify by its track id; Apple Music links open the Music app), else the web.
@MainActor
func openMusicBlock(_ block: MusicBlock) {
    // An Apple Music song opens as a Spotify search for someone whose music app is Spotify (per viewer).
    if block.provider != "Spotify" && musicOpensIn(block) == "Spotify" { searchSpotify(block); return }
    let web = URL(string: block.providerUrl).flatMap { ["http", "https"].contains($0.scheme ?? "") ? $0 : nil }
    if block.provider == "Spotify", let id = block.providerTrackId, id.count == 22, let app = URL(string: "spotify:track:\(id)") {
        UIApplication.shared.open(app) { ok in if !ok, let web { UIApplication.shared.open(web) } }
        return
    }
    if let web { UIApplication.shared.open(web) }
}

/// Where a song opens for this person: an Apple Music song opens in Spotify when Ayarlar → Müzik uygulaması is
/// Spotify (the same card opens in Apple Music for someone else); a Spotify song always in Spotify.
func musicOpensIn(_ block: MusicBlock) -> String {
    (block.provider == "iTunes" || block.provider == "Apple Music") && MusicSource.saved == .spotify ? "Spotify" : block.provider
}

/// Opens a song in the app chosen in Settings → Müzik uygulaması.
@MainActor
func openInPreferredMusicApp(_ music: MusicBlock) { openMusicBlock(music) }

/// Spotify has no open catalog for apps: the song opens as a Spotify search for "title artist".
@MainActor
private func searchSpotify(_ music: MusicBlock) {
    let q = [music.title, music.artist].filter { !$0.isEmpty }.joined(separator: " ")
    let encoded = q.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? q
    if let app = URL(string: "spotify:search:\(encoded)") {
        UIApplication.shared.open(app) { ok in
            if !ok, let web = URL(string: "https://open.spotify.com/search/\(encoded)") { UIApplication.shared.open(web) }
        }
    }
}
