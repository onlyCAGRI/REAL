import CoreLocation
import Photos
import SwiftUI
import UIKit
import UserNotifications

/// MapPosterRenderer.kt MapPosterPlace: what a poster says under its picture, the province (il) or city, its
/// country and the coordinates.
struct PosterPlace {
    let latitude: Double
    let longitude: Double
    let title: String
    let country: String
    let locale: Locale
}

/// Shared by the Real Labs posters (map and sky): place naming, gallery, share, the "Poster hazır" notification
/// and text drawing on a CGContext.
enum PosterKit {
    /// Province first for Türkiye (il), the city elsewhere; the country in the app language. The card's own place
    /// name when the geocoder has nothing (offline).
    static func place(latitude: Double, longitude: Double, fallback: String) async -> PosterPlace {
        let mark = try? await CLGeocoder().reverseGeocodeLocation(CLLocation(latitude: latitude, longitude: longitude),
                                                                    preferredLocale: I18n.locale).first
        let turkey = mark?.isoCountryCode?.uppercased() == "TR"
        let title = turkey ? (mark?.administrativeArea ?? mark?.locality)
                           : (mark?.locality ?? mark?.subAdministrativeArea ?? mark?.administrativeArea)
        let locale = turkey ? Locale(identifier: "tr_TR") : I18n.locale
        let name = (title?.isEmpty == false ? title : nil) ?? fallback
        return PosterPlace(latitude: latitude, longitude: longitude, title: name, country: mark?.country ?? "", locale: locale)
    }

    static func coordinates(_ lat: Double, _ lng: Double) -> String {
        String(format: "%.4f° %@ / %.4f° %@", abs(lat), lat >= 0 ? "N" : "S", abs(lng), lng >= 0 ? "E" : "W")
    }

    static func fileName(_ prefix: String, _ title: String, _ template: String) -> String {
        let clean = title.filter { $0.isLetter || $0.isNumber }
        return "\(prefix)-\(clean.isEmpty ? "poster" : clean)-\(template)"
    }

    /// Saves the PNG into the Photos library (add-only permission). False when refused or it failed.
    static func saveToPhotos(_ image: UIImage) async -> Bool {
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited, let data = image.pngData() else { return false }
        return await withCheckedContinuation { cont in
            PHPhotoLibrary.shared().performChanges({
                PHAssetCreationRequest.forAsset().addResource(with: .photo, data: data, options: nil)
            }) { ok, _ in cont.resume(returning: ok) }
        }
    }

    /// A PNG copy in the temporary folder for the share sheet.
    static func shareFile(_ image: UIImage, name: String) -> URL? {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("posters", isDirectory: true)
        try? FileManager.default.removeItem(at: dir)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let url = dir.appendingPathComponent("\(name).png")
        guard let data = image.pngData(), (try? data.write(to: url)) != nil else { return nil }
        return url
    }

    /// Opens the system share sheet over the top view controller.
    @MainActor static func share(_ url: URL) {
        let sheet = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        guard let root = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.keyWindow }).first?.rootViewController else { return }
        var top = root
        while let next = top.presentedViewController { top = next }
        sheet.popoverPresentationController?.sourceView = top.view
        top.present(sheet, animated: true)
    }

    /// Screens showing a poster; a save that ends with none open says so with a notification (MapPosterJobs).
    @MainActor static var openScreens = 0

    /// The 8K save keeps running after the screen closes; it ends in a toast (screen open) or a notification.
    @MainActor static func save(title: String, render: @escaping () async -> UIImage?, onDone: @escaping () -> Void) {
        saveAll(title: title, renders: [render], onDone: onDone)
    }

    /// MapPosterJobs.saveAll: several sizes, one after another (one big image in memory at a time).
    @MainActor static func saveAll(title: String, renders: [() async -> UIImage?], onDone: @escaping () -> Void) {
        Task { @MainActor in
            var saved = 0
            for render in renders {
                if let image = await render(), await saveToPhotos(image) { saved += 1 }
            }
            onDone()
            if openScreens > 0 {
                RealToast.shared.show(saved == 0 ? t("Poster kaydedilemedi")
                                      : saved == 1 ? t("Galeriye kaydedildi") : t("{0} poster galeriye kaydedildi", saved))
            } else if saved > 0 {
                let content = UNMutableNotificationContent()
                content.title = t("Poster hazır")
                content.body = t("{0} · galeriye kaydedildi", title)
                try? await UNUserNotificationCenter.current().add(
                    UNNotificationRequest(identifier: "poster-\(UUID().uuidString)", content: content, trigger: nil))
            }
        }
    }

    /// Centred text with letter spacing, [baseline] like Android's drawText; [kern] is in ems.
    static func drawCentered(_ text: String, x: CGFloat, baseline: CGFloat, font: UIFont, color: UIColor, kern: CGFloat = 0,
                             maxWidth: CGFloat? = nil, align: NSTextAlignment = .center) {
        var font = font
        func attributed() -> NSAttributedString {
            NSAttributedString(string: text, attributes: [.font: font, .foregroundColor: color, .kern: kern * font.pointSize])
        }
        var string = attributed()
        if let maxWidth {
            while string.size().width > maxWidth && font.pointSize > 8 {
                font = font.withSize(font.pointSize * 0.95)
                string = attributed()
            }
        }
        // The kern after the last letter is not part of the visible width.
        let width = string.size().width - kern * font.pointSize
        let left: CGFloat
        switch align {
        case .right: left = x - width
        case .left: left = x
        default: left = x - width / 2
        }
        string.draw(at: CGPoint(x: left, y: baseline - font.ascender))
    }

    /// Fits a title the way the posters do: wide-spaced capitals shrunk until they fit [maxWidth].
    static func titleFont(size: CGFloat) -> UIFont { .systemFont(ofSize: size, weight: .bold) }
    static func lightFont(size: CGFloat) -> UIFont { .systemFont(ofSize: size, weight: .light) }
    static func monoFont(size: CGFloat) -> UIFont { .monospacedSystemFont(ofSize: size, weight: .regular) }
    /// The logo's typeface for the "Real" signature (Inter Tight Bold, bundled).
    static func brandFont(size: CGFloat) -> UIFont { UIFont(name: "InterTight-Bold", size: size) ?? .boldSystemFont(ofSize: size) }
}

/// PosterFormats.kt PosterFormat: the sizes a Real Labs poster can be saved in. [poster] is the 4:5 print (8K); the
/// others are drawn with their long side 3000 px; [wallpaper] takes this phone's own full-screen shape.
enum PosterFormat: String, CaseIterable, Identifiable {
    case poster, instagram, story, wallpaper, r3x2, r4x3, r16x9, r21x9, r3x1

    var id: String { rawValue }

    /// Turkish key (shown through t()).
    var label: String {
        switch self {
        case .poster: "Poster"
        case .instagram: "Instagram"
        case .story: "Hikâye"
        case .wallpaper: "Duvar kâğıdı"
        case .r3x2: "3:2"
        case .r4x3: "4:3"
        case .r16x9: "16:9"
        case .r21x9: "21:9"
        case .r3x1: "3:1"
        }
    }

    var note: String {
        switch self {
        case .poster: "4:5 · 8K"
        case .instagram: "1:1"
        case .story: "9:16"
        case .wallpaper: "tam ekran"
        default: ""
        }
    }

    /// Shown as a picture tile (the rest are ratio chips).
    var named: Bool { [.poster, .instagram, .story, .wallpaper].contains(self) }

    /// Width ÷ height.
    @MainActor var aspect: CGFloat {
        switch self {
        case .poster: 0.8
        case .instagram: 1
        case .story: 9.0 / 16.0
        case .wallpaper: Self.screenAspect
        case .r3x2: 1.5
        case .r4x3: 4.0 / 3.0
        case .r16x9: 16.0 / 9.0
        case .r21x9: 21.0 / 9.0
        case .r3x1: 3
        }
    }

    /// This phone's full screen, short side ÷ long side.
    @MainActor private static var screenAspect: CGFloat {
        let b = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.screen.bounds }.first ?? .zero
        let short = min(b.width, b.height), long = max(b.width, b.height)
        return short > 0 && long > 0 ? short / long : 9.0 / 19.5
    }

    /// Pixel size with the long side [longSide].
    @MainActor func size(longSide: CGFloat = 3000) -> CGSize {
        let a = aspect
        return a >= 1 ? CGSize(width: longSide, height: (longSide / a).rounded()) : CGSize(width: (longSide * a).rounded(), height: longSide)
    }
}

/// PosterFormats.kt PosterFrame: where a poster's title block sits for a format. Everything is in units of [u] (the
/// 4:5 poster's width), so the 4:5 poster comes out exactly as before; taller pages keep the block at the bottom,
/// wide ones move it to the bottom-left, the wallpaper keeps it at the bottom a little higher up.
struct PosterFrame {
    let w: CGFloat, h: CGFloat
    let format: PosterFormat

    var u: CGFloat { min(w, h * 0.8) }
    var wide: Bool { w > h * 1.05 }
    var wallpaper: Bool { format == .wallpaper }
    /// The line the block's offsets count up from; on the wallpaper lifted clear of the home indicator and dock.
    var bottom: CGFloat { wallpaper ? h - u * 0.12 : h }
    var x: CGFloat { wide ? w * 0.06 : w / 2 }
    var align: NSTextAlignment { wide ? .left : .center }
    var titleMaxWidth: CGFloat { wide ? w * 0.55 : w * 0.86 }

    func y(_ fromBottom: CGFloat) -> CGFloat { bottom - fromBottom * u }

    /// The short rule under the title.
    func drawRule(_ c: CGContext, _ fromBottom: CGFloat, ink: UInt32) {
        c.setStrokeColor(UIColor(argb: ink, alpha: 200 / 255).cgColor)
        c.setLineWidth(u * 0.0008)
        let half = u * 0.10, yy = y(fromBottom)
        if wide { c.strokeLineSegments(between: [CGPoint(x: x, y: yy), CGPoint(x: x + half * 2, y: yy)]) }
        else { c.strokeLineSegments(between: [CGPoint(x: x - half, y: yy), CGPoint(x: x + half, y: yy)]) }
    }

    /// The data credit (small, bottom-right) and the Real signature: tiny on the print, readable on shared sizes.
    /// Call inside UIGraphicsPushContext.
    func drawCredit(_ credit: String, ink: UInt32) {
        let baseline = h - 0.0275 * u
        let faint = UIColor(argb: ink, alpha: 165 / 255)
        PosterKit.drawCentered(credit, x: w - 0.025 * u, baseline: baseline, font: PosterKit.monoFont(size: u * 0.0115), color: faint, align: .right)
        if format == .poster {
            PosterKit.drawCentered("Real", x: 0.025 * u, baseline: baseline, font: PosterKit.brandFont(size: u * 0.0115), color: faint, align: .left)
            return
        }
        let strong = UIColor(argb: ink, alpha: 235 / 255)
        let font = PosterKit.brandFont(size: u * 0.034)
        if wide {
            PosterKit.drawCentered("Real", x: w - 0.025 * u, baseline: baseline - u * 0.03, font: font, color: strong, kern: -0.03, align: .right)
        } else {
            // Above the home indicator on a wallpaper.
            PosterKit.drawCentered("Real", x: 0.03 * u, baseline: h - (wallpaper ? 0.12 : 0.03) * u, font: font, color: strong, kern: -0.03, align: .left)
        }
    }
}

/// PosterFormatSheet.kt: "Hangi ölçülerde kaydedilsin?" — the poster, Instagram, story and wallpaper as small
/// pictures of their shape, then the plain ratios as chips. Several can be picked; each is saved as its own image.
struct PosterFormatSheet: View {
    let surface: Color
    let ink: Color
    let onSave: ([PosterFormat]) -> Void
    @State private var picked: Set<PosterFormat> = [.poster]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(t("Hangi ölçülerde kaydedilsin?")).font(RealFont.inter(17, .semibold)).foregroundStyle(ink)
            Text(t("Birden fazla seçebilirsin. Her birinin köşesinde Real yazar.")).font(RealFont.inter(13))
                .foregroundStyle(ink.opacity(0.6)).padding(.top, 2).padding(.bottom, 14)
            HStack(spacing: 8) {
                ForEach(PosterFormat.allCases.filter(\.named)) { format in tile(format) }
            }
            Text(t("Oran")).font(RealFont.inter(13)).foregroundStyle(ink.opacity(0.6)).padding(.top, 16).padding(.bottom, 8)
            HStack(spacing: 8) {
                ForEach(PosterFormat.allCases.filter { !$0.named }) { format in
                    let on = picked.contains(format)
                    Button { toggle(format) } label: {
                        Text(format.label).font(RealFont.inter(13)).foregroundStyle(ink)
                            .padding(.horizontal, 14).frame(height: 36)
                            .background(Capsule().fill(on ? ink.opacity(0.14) : .clear))
                            .overlay(Capsule().strokeBorder(ink.opacity(on ? 1 : 0.3), lineWidth: on ? 2 : 0.5))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            PosterButton(label: picked.isEmpty ? t("Bir ölçü seç") : t("{0} ölçüyü kaydet", picked.count), ink: ink, primary: true,
                         enabled: !picked.isEmpty) {
                onSave(PosterFormat.allCases.filter { picked.contains($0) })
            }
            .padding(.top, 16)
        }
        .padding(.horizontal, 16).padding(.top, 22).padding(.bottom, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func toggle(_ format: PosterFormat) {
        if picked.contains(format) { picked.remove(format) } else { picked.insert(format) }
    }

    /// A named size: its shape drawn small (the title's place marked), the name and its ratio under it.
    private func tile(_ format: PosterFormat) -> some View {
        let on = picked.contains(format)
        let aspect = format.aspect
        return Button { toggle(format) } label: {
            VStack(spacing: 0) {
                Canvas { ctx, size in
                    let box = aspect >= size.width / size.height ? CGSize(width: size.width, height: size.width / aspect)
                                                                 : CGSize(width: size.height * aspect, height: size.height)
                    let rect = CGRect(x: (size.width - box.width) / 2, y: (size.height - box.height) / 2, width: box.width, height: box.height)
                    let shape = Path(roundedRect: rect, cornerRadius: 4)
                    ctx.fill(shape, with: .color(ink.opacity(0.12)))
                    ctx.stroke(shape, with: .color(ink.opacity(0.35)), lineWidth: 1)
                    let lineY = rect.minY + rect.height * (format == .wallpaper ? 0.76 : 0.80)
                    let half = rect.width * 0.28
                    var line = Path()
                    line.move(to: CGPoint(x: rect.midX - half, y: lineY))
                    line.addLine(to: CGPoint(x: rect.midX + half, y: lineY))
                    ctx.stroke(line, with: .color(ink.opacity(0.85)), lineWidth: 2)
                }
                .frame(width: 54, height: 62)
                Text(t(format.label)).font(RealFont.inter(12)).foregroundStyle(ink).lineLimit(1).padding(.top, 6)
                Text(t(format.note)).font(RealFont.inter(10)).foregroundStyle(ink.opacity(0.55)).lineLimit(1)
            }
            .padding(.vertical, 10).padding(.horizontal, 4)
            .frame(maxWidth: .infinity)
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(ink.opacity(on ? 1 : 0.25), lineWidth: on ? 2 : 0.5))
            .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

extension UIColor {
    convenience init(argb: UInt32, alpha: CGFloat? = nil) {
        self.init(red: CGFloat((argb >> 16) & 0xFF) / 255, green: CGFloat((argb >> 8) & 0xFF) / 255,
                  blue: CGFloat(argb & 0xFF) / 255, alpha: alpha ?? CGFloat((argb >> 24) & 0xFF) / 255)
    }
}

/// MapPosterScreen.kt PosterButton: primary is filled with the ink, the rest a faint ink wash.
struct PosterButton: View {
    let label: String
    let ink: Color
    var primary = false
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label).font(RealFont.inter(13, .semibold)).lineLimit(1)
                .foregroundStyle((primary ? onInk : ink).opacity(enabled ? 1 : 0.4))
                .frame(maxWidth: .infinity).frame(height: 46)
                .background(Capsule().fill(primary ? ink : ink.opacity(0.1)))
        }
        .buttonStyle(PressScaleStyle(scale: 0.97))
        .disabled(!enabled)
    }

    private var onInk: Color {
        var white: CGFloat = 0
        UIColor(ink).getWhite(&white, alpha: nil)
        return white > 0.5 ? Color(argb: 0xFF151515) : .white
    }
}

/// The poster screens' header: title and a round close button in the template's ink.
struct PosterHeader: View {
    let title: String
    let ink: Color
    let onClose: () -> Void

    var body: some View {
        HStack {
            Text(title).font(RealFont.inter(17, .semibold)).foregroundStyle(ink)
            Spacer()
            Button(action: onClose) {
                ActionIcon(.close, color: ink, size: 14).frame(width: 36, height: 36).background(Circle().fill(ink.opacity(0.1)))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }
}
