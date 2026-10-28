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
        Task { @MainActor in
            let image = await render()
            let ok = await image.map { await saveToPhotos($0) } ?? false
            onDone()
            if openScreens > 0 {
                RealToast.shared.show(ok ? t("Galeriye kaydedildi") : t("Poster kaydedilemedi"))
            } else if ok {
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
