import SwiftUI
import UIKit

/// MusicSmoke.kt: clouds in the cover's own colours that drift slowly while the preview plays and fade out after.
/// The song's bass (MusicBeats) thickens and spreads them; they never flash.
struct MusicSmoke: View {
    let previewUrl: String?
    let artworkUrl: String?
    let fallback: Color
    let active: Bool
    var strength: Double = 1
    /// The player's position, read on each frame.
    let position: () -> Int64

    @AppStorage("reduce_animations") private var reduceAnimations = false
    @State private var track: BeatTrack?
    @State private var colors: [Color] = []

    // Where each cloud's puffs sit and how they wander; the unrelated speeds keep the motion from visibly repeating.
    private static let puffs: [[Double]] = [
        [0.20, 0.78, 0.62, 0.19, 0.13, 0.0],
        [0.38, 0.92, 0.48, 0.13, 0.17, 1.7],
        [0.62, 0.84, 0.56, 0.17, 0.11, 3.1],
        [0.84, 0.72, 0.66, 0.11, 0.19, 4.6],
        [0.28, 0.40, 0.44, 0.23, 0.09, 2.3],
        [0.72, 0.36, 0.42, 0.09, 0.23, 5.4],
        [0.50, 0.58, 0.70, 0.15, 0.07, 0.9]
    ]

    var body: some View {
        let still = reduceAnimations || UIAccessibility.isReduceMotionEnabled
        TimelineView(.animation(paused: !active || still)) { timeline in
            Canvas { context, size in
                guard !colors.isEmpty else { return }
                let bass = still ? 0.3 : Double(track?.level(position()) ?? 0.16)
                let t = still ? 0 : timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 100_000)
                let side = max(size.width, size.height)
                let alpha = (0.07 + 0.27 * bass) * strength
                for (index, puff) in Self.puffs.enumerated() {
                    let phase = puff[5]
                    let center = CGPoint(x: size.width * (puff[0] + puff[3] * sin(t * 0.21 + phase)),
                                         y: size.height * (puff[1] + puff[4] * sin(t * 0.17 + phase * 1.6)))
                    let radius = side * puff[2] * (0.78 + 0.30 * bass + 0.05 * sin(t * 0.33 + phase))
                    let color = colors[index % colors.count]
                    let stops = Gradient(stops: [.init(color: color.opacity(alpha), location: 0),
                                                 .init(color: color.opacity(alpha * 0.48), location: 0.45),
                                                 .init(color: color.opacity(0), location: 1)])
                    context.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)),
                                 with: .radialGradient(stops, center: center, startRadius: 0, endRadius: radius))
                }
            }
        }
        .opacity(active ? 1 : 0)
        .animation(still ? nil : .easeOut(duration: active ? 0.9 : 0.7), value: active)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .task(id: active ? artworkUrl : nil) {
            guard active else { return }
            var sampled: [Color]?
            if let artworkUrl, let image = await PosterImageCache.load(artworkUrl) { sampled = Self.smokeColors(image) }
            colors = sampled ?? Array(repeating: fallback, count: 4)
        }
        .task(id: active ? previewUrl : nil) {
            guard active, let previewUrl else { return }
            track = nil
            track = await MusicBeats.shared.track(previewUrl)
        }
    }

    /// Each quarter of the cover, brightened a little: dark covers still need smoke that can be seen.
    private static func smokeColors(_ image: UIImage) -> [Color]? {
        guard let cg = image.cgImage else { return nil }
        var pixels = [UInt8](repeating: 0, count: 24 * 24 * 4)
        let drawn: Bool = pixels.withUnsafeMutableBytes { raw in
            guard let context = CGContext(data: raw.baseAddress, width: 24, height: 24, bitsPerComponent: 8, bytesPerRow: 96,
                                          space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return false }
            context.interpolationQuality = .low
            context.draw(cg, in: CGRect(x: 0, y: 0, width: 24, height: 24))
            return true
        }
        guard drawn else { return nil }
        return (0..<4).map { region in
            var red = 0, green = 0, blue = 0
            let left = region % 2 == 0 ? 0 : 12
            let top = region < 2 ? 0 : 12
            for y in top..<(top + 12) {
                for x in left..<(left + 12) {
                    let at = (y * 24 + x) * 4
                    red += Int(pixels[at]); green += Int(pixels[at + 1]); blue += Int(pixels[at + 2])
                }
            }
            let r = Double(red) / 144 / 255, g = Double(green) / 144 / 255, b = Double(blue) / 144 / 255
            let lift = min(0.85 / max(max(r, g, b), 0.2), 3)
            return Color(red: min(r * lift + 0.04, 1), green: min(g * lift + 0.04, 1), blue: min(b * lift + 0.04, 1))
        }
    }
}
