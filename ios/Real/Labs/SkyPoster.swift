import SwiftUI
import UIKit

/// SkyMath.kt: low-precision sky positions, good to a fraction of a degree (far below one star dot on the poster).
/// Star coordinates are J2000; precession since then (~0.4°) is ignored on purpose.
enum SkyMath {
    private static let rad = Double.pi / 180

    static func julian(_ epochMillis: Int64) -> Double { Double(epochMillis) / 86_400_000 + 2_440_587.5 }

    static func localSidereal(_ jd: Double, _ longitude: Double) -> Double {
        norm(280.46061837 + 360.98564736629 * (jd - 2_451_545) + longitude)
    }

    /// Altitude and azimuth (degrees; azimuth from north towards east).
    static func altAz(ra: Double, dec: Double, latitude: Double, lst: Double) -> (alt: Double, az: Double) {
        let h = (lst - ra) * rad, d = dec * rad, p = latitude * rad
        let alt = asin(sin(p) * sin(d) + cos(p) * cos(d) * cos(h))
        let az = atan2(-sin(h) * cos(d), cos(p) * sin(d) - sin(p) * cos(d) * cos(h))
        return (alt / rad, norm(az / rad))
    }

    struct Moon { let ra: Double; let dec: Double; let illumination: Double; let waxing: Bool }

    static func moon(_ jd: Double) -> Moon {
        let d = jd - 2_451_545
        let l0 = 218.316 + 13.176396 * d
        let m = 134.963 + 13.064993 * d
        let f = 93.272 + 13.229350 * d
        let lambda = norm(l0 + 6.289 * sin(m * rad))
        let beta = 5.128 * sin(f * rad)
        let (ra, dec) = eclipticToEquatorial(lambda, beta, d)
        let g = 357.528 + 0.9856003 * d
        let sun = norm(280.460 + 0.9856474 * d + 1.915 * sin(g * rad) + 0.020 * sin(2 * g * rad))
        let elongation = norm(lambda - sun)
        return Moon(ra: ra, dec: dec, illumination: (1 - cos(elongation * rad)) / 2, waxing: elongation < 180)
    }

    /// Above 0 means it is daytime at the place.
    static func sunAltitude(_ epochMillis: Int64, latitude: Double, longitude: Double) -> Double {
        let jd = julian(epochMillis)
        let d = jd - 2_451_545
        let g = 357.528 + 0.9856003 * d
        let lambda = norm(280.460 + 0.9856474 * d + 1.915 * sin(g * rad) + 0.020 * sin(2 * g * rad))
        let (ra, dec) = eclipticToEquatorial(lambda, 0, d)
        return altAz(ra: ra, dec: dec, latitude: latitude, lst: localSidereal(jd, longitude)).alt
    }

    private static func eclipticToEquatorial(_ lambdaDeg: Double, _ betaDeg: Double, _ d: Double) -> (Double, Double) {
        let e = (23.439 - 0.0000004 * d) * rad, l = lambdaDeg * rad, b = betaDeg * rad
        let ra = atan2(sin(l) * cos(e) - tan(b) * sin(e), cos(l))
        let dec = asin(sin(b) * cos(e) + cos(b) * sin(e) * sin(l))
        return (norm(ra / rad), dec / rad)
    }

    /// Turkish key for the phase name, shown through t().
    static func phaseName(_ moon: Moon) -> String {
        if moon.illumination < 0.03 { return "Yeni ay" }
        if moon.illumination > 0.97 { return "Dolunay" }
        if (0.45...0.55).contains(moon.illumination) { return moon.waxing ? "İlk dördün" : "Son dördün" }
        if moon.illumination < 0.5 { return moon.waxing ? "Hilal" : "Balzamik hilal" }
        return moon.waxing ? "Büyüyen ay" : "Küçülen ay"
    }

    private static func norm(_ deg: Double) -> Double { (deg.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) }
}

/// SkyPosterRenderer.kt SkyPosterTemplate: page, sky disc, stars, constellation lines, ring and title ink.
struct SkyPosterTemplate: Identifiable, Equatable {
    let id: String
    /// Turkish key; shown through t().
    let name: String
    let background: UInt32, sky: UInt32, star: UInt32, line: UInt32, ring: UInt32, text: UInt32
    let dark: Bool

    static let all: [SkyPosterTemplate] = [
        .init(id: "midnight", name: "Gece mavisi", background: 0xFF0B1530, sky: 0xFF0E1A3A, star: 0xFFFFFFFF, line: 0xFF7F95CC, ring: 0xFFDDB65A, text: 0xFFDDB65A, dark: true),
        .init(id: "night", name: "Gece", background: 0xFF000000, sky: 0xFF000000, star: 0xFFFFFFFF, line: 0xFF8A8A8A, ring: 0xFFFFFFFF, text: 0xFFFFFFFF, dark: true),
        .init(id: "ink", name: "Mürekkep", background: 0xFFFFFFFF, sky: 0xFFFFFFFF, star: 0xFF111111, line: 0xFF6A6A6A, ring: 0xFF111111, text: 0xFF111111, dark: false),
        .init(id: "paper", name: "Kâğıt", background: 0xFFF8F6F3, sky: 0xFF1C2233, star: 0xFFFFFFFF, line: 0xFF8E98B8, ring: 0xFF1C2233, text: 0xFF1C2233, dark: false),
        .init(id: "wine", name: "Bordo", background: 0xFF1F0A12, sky: 0xFF2A0E19, star: 0xFFF7DCC5, line: 0xFFA04A64, ring: 0xFFF7DCC5, text: 0xFFF7DCC5, dark: true),
        .init(id: "ocean", name: "Okyanus", background: 0xFF06202B, sky: 0xFF082C3A, star: 0xFFE6FFFD, line: 0xFF3BB8C0, ring: 0xFF9FF5F0, text: 0xFF9FF5F0, dark: true),
        .init(id: "neon", name: "Neon", background: 0xFF0B0D18, sky: 0xFF120E26, star: 0xFFFFFFFF, line: 0xFFC45BFF, ring: 0xFFFF4FA3, text: 0xFFFFD000, dark: true),
        .init(id: "espresso", name: "Espresso", background: 0xFF1E110C, sky: 0xFF2A1710, star: 0xFFFFE3B0, line: 0xFFB2552B, ring: 0xFFFFD58E, text: 0xFFFFD58E, dark: true),
        .init(id: "forest", name: "Orman", background: 0xFF16241B, sky: 0xFF1B2E22, star: 0xFFF2EBC8, line: 0xFF7FA07A, ring: 0xFFD9C27A, text: 0xFFE8DDB0, dark: true),
        .init(id: "sand", name: "Kum", background: 0xFFE3D7C9, sky: 0xFF3F3029, star: 0xFFF5E6C8, line: 0xFFA0705F, ring: 0xFF3F3029, text: 0xFF3F3029, dark: false),
        .init(id: "sage", name: "Adaçayı", background: 0xFFEEF3EE, sky: 0xFF24493C, star: 0xFFF3F7EE, line: 0xFF86AB99, ring: 0xFF24493C, text: 0xFF24493C, dark: false),
        .init(id: "rose", name: "Gül", background: 0xFFFBF0F1, sky: 0xFF5A1F35, star: 0xFFFFE9EE, line: 0xFFD46A88, ring: 0xFF9C2F52, text: 0xFF9C2F52, dark: false),
    ]
}

/// SkyPosterRenderer.kt: the sky over the card's place at its moment as a round star chart: zenith in the middle,
/// horizon on the ring, north up and east on the left. Stars: Yale Bright Star Catalog (public domain); figures:
/// d3-celestial (BSD). Drawn on a CGContext, so the 8K print needs no tiling.
enum SkyPosterRenderer {
    static let canvas = CGSize(width: 3000, height: 3750)
    static let highRes: CGFloat = 2.56

    private struct Catalog { let stars: [(Double, Double, Float)]; let lines: [[(Double, Double)]] }
    private static var cached: Catalog?

    /// sky/stars.csv: "ra,dec,mag" per star; sky/lines.csv: "ra dec;ra dec;…" per figure stroke (Android assets).
    private static func catalog() -> Catalog? {
        if let cached { return cached }
        guard let starsURL = Bundle.main.url(forResource: "stars", withExtension: "csv"),
              let linesURL = Bundle.main.url(forResource: "lines", withExtension: "csv"),
              let starsText = try? String(contentsOf: starsURL, encoding: .utf8),
              let linesText = try? String(contentsOf: linesURL, encoding: .utf8) else { return nil }
        let stars: [(Double, Double, Float)] = starsText.split(separator: "\n").compactMap { row in
            let p = row.split(separator: ",")
            guard p.count >= 3, let ra = Double(p[0]), let dec = Double(p[1]), let mag = Float(p[2]) else { return nil }
            return (ra, dec, mag)
        }
        let lines: [[(Double, Double)]] = linesText.split(separator: "\n").compactMap { row in
            let pts: [(Double, Double)] = row.split(separator: ";").compactMap { pair in
                let v = pair.trimmingCharacters(in: .whitespaces).split(separator: " ")
                guard v.count == 2, let a = Double(v[0]), let b = Double(v[1]) else { return nil }
                return (a, b)
            }
            return pts.count < 2 ? nil : pts
        }
        let catalog = Catalog(stars: stars, lines: lines)
        cached = catalog
        return catalog
    }

    static func render(_ template: SkyPosterTemplate, place: PosterPlace, epochMillis: Int64, scale: CGFloat) async -> UIImage? {
        await Task.detached(priority: .userInitiated) {
            guard let data = catalog() else { return nil }
            let size = CGSize(width: (canvas.width * scale).rounded(), height: (canvas.height * scale).rounded())
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            format.opaque = true
            return UIGraphicsImageRenderer(size: size, format: format).image { ctx in
                draw(ctx.cgContext, w: size.width, h: size.height, template, place, epochMillis, data)
            }
        }.value
    }

    private static func draw(_ c: CGContext, w: CGFloat, h: CGFloat, _ template: SkyPosterTemplate, _ place: PosterPlace,
                             _ epochMillis: Int64, _ data: Catalog) {
        c.setFillColor(UIColor(argb: template.background).cgColor)
        c.fill(CGRect(x: 0, y: 0, width: w, height: h))
        let cx = w / 2, cy = h * 0.40, radius = w * 0.40
        let jd = SkyMath.julian(epochMillis)
        let lst = SkyMath.localSidereal(jd, place.longitude)

        /// Stereographic from the zenith: the horizon lands exactly on the ring. Nil below the horizon.
        func project(_ ra: Double, _ dec: Double, margin: Double = 0) -> CGPoint? {
            let (alt, az) = SkyMath.altAz(ra: ra, dec: dec, latitude: place.latitude, lst: lst)
            if alt < -margin { return nil }
            let r = radius * CGFloat(tan((90 - alt) * .pi / 180 / 2))
            let a = az * .pi / 180
            return CGPoint(x: cx - r * CGFloat(sin(a)), y: cy - r * CGFloat(cos(a)))
        }
        func ringRadius(_ alt: Double) -> CGFloat { radius * CGFloat(tan((90 - alt) * .pi / 180 / 2)) }

        let disc = CGRect(x: cx - radius, y: cy - radius, width: radius * 2, height: radius * 2)
        c.setFillColor(UIColor(argb: template.sky).cgColor)
        c.fillEllipse(in: disc)
        c.saveGState()
        c.addEllipse(in: disc)
        c.clip()

        // Faint altitude rings (30°, 60°) and the four cardinal spokes.
        c.setStrokeColor(UIColor(argb: template.line, alpha: 40 / 255).cgColor)
        c.setLineWidth(w * 0.0006)
        for alt in [30.0, 60.0] {
            let r = ringRadius(alt)
            c.strokeEllipse(in: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
        }
        c.strokeLineSegments(between: [CGPoint(x: cx - radius, y: cy), CGPoint(x: cx + radius, y: cy),
                                       CGPoint(x: cx, y: cy - radius), CGPoint(x: cx, y: cy + radius)])

        c.setStrokeColor(UIColor(argb: template.line, alpha: 150 / 255).cgColor)
        c.setLineWidth(w * 0.0009)
        c.setLineCap(.round)
        for stroke in data.lines {
            var prev: CGPoint?
            for (ra, dec) in stroke {
                let p = project(ra, dec, margin: 4)
                if let p, let prev { c.strokeLineSegments(between: [prev, p]) }
                prev = p
            }
        }

        let starColor = UIColor(argb: template.star)
        let space = CGColorSpaceCreateDeviceRGB()
        let halo = CGGradient(colorsSpace: space, colors: [UIColor(argb: template.star, alpha: 0x55 / 255).cgColor,
                                                           UIColor(argb: template.star, alpha: 0).cgColor] as CFArray, locations: [0, 1])
        c.setFillColor(starColor.cgColor)
        for (ra, dec, mag) in data.stars where mag <= 5.5 {
            // 5.5 keeps ~2,800 stars: full but calm, the figures stay readable.
            guard let p = project(ra, dec) else { continue }
            let r = max(w * 0.0011 * (1 + CGFloat(5.0 - mag) * 0.42), w * 0.0004)
            if mag < 1.5, let halo {
                c.drawRadialGradient(halo, startCenter: p, startRadius: 0, endCenter: p, endRadius: r * 4.5, options: [])
                c.setFillColor(starColor.cgColor)
            }
            c.fillEllipse(in: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2))
        }

        let moon = SkyMath.moon(jd)
        if let m = project(moon.ra, moon.dec) { drawMoon(c, at: m, r: w * 0.014, moon, template) }
        c.restoreGState()

        c.setStrokeColor(UIColor(argb: template.ring).cgColor)
        c.setLineWidth(w * 0.0022)
        c.strokeEllipse(in: disc)
        c.setStrokeColor(UIColor(argb: template.ring, alpha: 140 / 255).cgColor)
        c.setLineWidth(w * 0.0008)
        let outer = radius * 1.025
        c.strokeEllipse(in: CGRect(x: cx - outer, y: cy - outer, width: outer * 2, height: outer * 2))

        UIGraphicsPushContext(c)
        let ink = UIColor(argb: template.text)
        let cardinal = PosterKit.monoFont(size: w * 0.017)
        let (n, e, s, wst) = I18n.isEnglish ? ("N", "E", "S", "W") : ("K", "D", "G", "B")
        let off = radius * 1.075, mid = cardinal.pointSize * 0.35
        PosterKit.drawCentered(n, x: cx, baseline: cy - off + mid, font: cardinal, color: ink)
        PosterKit.drawCentered(s, x: cx, baseline: cy + off + mid, font: cardinal, color: ink)
        PosterKit.drawCentered(e, x: cx - off, baseline: cy + mid, font: cardinal, color: ink)
        PosterKit.drawCentered(wst, x: cx + off, baseline: cy + mid, font: cardinal, color: ink)

        // Title block, as on the map poster: place, rule, the moment, coordinates and the Moon.
        PosterKit.drawCentered(place.title.uppercased(with: place.locale), x: w / 2, baseline: h * 0.835,
                               font: PosterKit.titleFont(size: w * 0.075), color: ink, kern: 0.55, maxWidth: w * 0.86)
        c.setStrokeColor(UIColor(argb: template.text, alpha: 200 / 255).cgColor)
        c.setLineWidth(w * 0.0008)
        c.strokeLineSegments(between: [CGPoint(x: w * 0.40, y: h * 0.86), CGPoint(x: w * 0.60, y: h * 0.86)])

        let formatter = DateFormatter()
        formatter.locale = I18n.locale
        formatter.dateFormat = "d MMMM yyyy · HH:mm"
        let moment = formatter.string(from: Date(timeIntervalSince1970: Double(epochMillis) / 1000)).uppercased(with: I18n.locale)
        PosterKit.drawCentered(moment, x: w / 2, baseline: h * 0.893, font: PosterKit.lightFont(size: w * 0.026), color: ink, kern: 0.12)

        let mono = PosterKit.monoFont(size: w * 0.0165)
        let soft = UIColor(argb: template.text, alpha: 215 / 255)
        PosterKit.drawCentered(PosterKit.coordinates(place.latitude, place.longitude), x: w / 2, baseline: h * 0.922, font: mono, color: soft)
        // Below the horizon the Moon is not on the chart; say so, or the phase line reads like a missing Moon.
        let moonUp = SkyMath.altAz(ra: moon.ra, dec: moon.dec, latitude: place.latitude, lst: lst).alt > 0
        let phase = "\(t(SkyMath.phaseName(moon))) · %\(Int(moon.illumination * 100))" + (moonUp ? "" : " · " + t("ufkun altında"))
        PosterKit.drawCentered(phase, x: w / 2, baseline: h * 0.945, font: mono, color: soft)

        let small = PosterKit.monoFont(size: w * 0.0115)
        let faint = UIColor(argb: template.text, alpha: 165 / 255)
        PosterKit.drawCentered("Yale Bright Star Catalog · d3-celestial", x: w * 0.975, baseline: h * 0.978, font: small, color: faint, align: .right)
        PosterKit.drawCentered("Real", x: w * 0.025, baseline: h * 0.978, font: PosterKit.brandFont(size: w * 0.0115), color: faint, align: .left)
        UIGraphicsPopContext()
    }

    /// The Moon with its real phase: the lit half-disc plus (gibbous) or minus (crescent) the terminator ellipse.
    /// Waxing is lit on the right, as seen from the northern hemisphere.
    private static func drawMoon(_ c: CGContext, at p: CGPoint, r: CGFloat, _ moon: SkyMath.Moon, _ template: SkyPosterTemplate) {
        let disc = CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)
        let dark = UIColor(argb: template.star, alpha: 40 / 255).cgColor
        let lit = UIColor(argb: template.star).cgColor
        c.setFillColor(dark)
        c.fillEllipse(in: disc)
        let k = CGFloat(moon.illumination)
        let half = CGMutablePath()
        // Right half when waxing (angles -90°…90° in screen space), left half when waning.
        half.addArc(center: p, radius: r, startAngle: moon.waxing ? -.pi / 2 : .pi / 2, endAngle: moon.waxing ? .pi / 2 : 3 * .pi / 2, clockwise: false)
        half.closeSubpath()
        let ew = r * abs(1 - 2 * k)
        let ellipse = CGRect(x: p.x - ew, y: p.y - r, width: ew * 2, height: r * 2)
        c.saveGState()
        if k >= 0.5 {
            c.setFillColor(lit)
            c.addPath(half); c.fillPath()
            c.fillEllipse(in: ellipse)
        } else {
            c.addPath(half); c.clip()
            c.setFillColor(lit)
            c.addPath(half); c.fillPath()
            c.setFillColor(UIColor(argb: template.sky).cgColor)
            c.fillEllipse(in: ellipse)
            c.setFillColor(dark)
            c.fillEllipse(in: disc)
        }
        c.restoreGState()
        // A soft halo so even a thin crescent reads at poster size.
        c.setFillColor(UIColor(argb: template.star, alpha: 22 / 255).cgColor)
        let glow = r * 1.8
        c.fillEllipse(in: CGRect(x: p.x - glow, y: p.y - glow, width: glow * 2, height: glow * 2))
    }
}

/// SkyPosterScreen.kt SkyPosterDialog: the sky over the card's place at the card's moment. The clock ruler moves
/// the time within that day (a daytime card opens on its 21:00 sky); the rest works like the map poster.
struct SkyPosterScreen: View {
    let latitude: Double
    let longitude: Double
    let placeName: String
    let momentMillis: Int64
    let onAttach: ((UIImage) -> Void)?
    let onClose: () -> Void

    @State private var template = SkyPosterTemplate.all[0]
    @State private var place: PosterPlace?
    @State private var preview: UIImage?
    @State private var failed = false
    @State private var busy = false
    @State private var minute: Double = 0
    @State private var moment: Int64 = 0
    @State private var dayStart: Int64 = 0

    var body: some View {
        let surface = Color(argb: template.background)
        let ink = template.dark ? Color.white : Color(argb: 0xFF151515)
        VStack(spacing: 0) {
            PosterHeader(title: t("Gökyüzü posteri"), ink: ink, onClose: onClose)
            Color.clear
                .aspectRatio(0.8, contentMode: .fit)
                .overlay {
                    if let preview { Image(uiImage: preview).resizable().scaledToFit() }
                }
                .overlay {
                    if failed { Text(t("Yıldız haritası yüklenemedi")).font(RealFont.inter(13)).foregroundStyle(ink.opacity(0.7)) }
                    else if preview == nil { Text(t("Poster hazırlanıyor…")).font(RealFont.inter(13)).foregroundStyle(ink.opacity(0.6)) }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.horizontal, 12).padding(.vertical, 4)
            SkyTimeRuler(minute: $minute, ink: ink, enabled: !busy) { settled in
                moment = dayStart + Int64(settled) * 60_000
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(SkyPosterTemplate.all) { item in
                        SkySwatch(template: item, selected: item.id == template.id, ink: ink) { if !busy { template = item } }
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.vertical, 10)
            HStack(spacing: 8) {
                if let onAttach {
                    PosterButton(label: t("Karta ekle"), ink: ink, primary: true, enabled: place != nil && !busy) {
                        guard let place else { return }
                        busy = true
                        Task {
                            let image = await SkyPosterRenderer.render(template, place: place, epochMillis: moment, scale: 1)
                            busy = false
                            if let image { onAttach(image); onClose() } else { RealToast.shared.show(t("Poster hazırlanamadı")) }
                        }
                    }
                }
                PosterButton(label: t("Kaydet"), ink: ink, primary: onAttach == nil, enabled: place != nil && !busy) {
                    guard let place else { return }
                    busy = true
                    let template = template, moment = moment
                    PosterKit.save(title: place.title, render: { await printSize(template, place, moment) }) { busy = false }
                }
                PosterButton(label: t("Paylaş"), ink: ink, enabled: place != nil && !busy) {
                    guard let place else { return }
                    busy = true
                    Task {
                        let image = await printSize(template, place, moment)
                        busy = false
                        if let image, let url = PosterKit.shareFile(image, name: PosterKit.fileName("REAL-sky", place.title, template.id)) {
                            PosterKit.share(url)
                        } else { RealToast.shared.show(t("Poster hazırlanamadı")) }
                    }
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
            if busy { Text(t("Poster hazırlanıyor…")).font(RealFont.inter(12)).foregroundStyle(ink.opacity(0.6)).padding(.bottom, 8) }
        }
        .background(surface.ignoresSafeArea())
        .animation(.easeInOut(duration: 0.32), value: template.id)
        .onAppear {
            PosterKit.openScreens += 1
            let cal = Calendar.current
            let start = cal.startOfDay(for: Date(timeIntervalSince1970: Double(momentMillis) / 1000))
            dayStart = Int64(start.timeIntervalSince1970 * 1000)
            // A daytime card (Sun up at the place) opens on that day's 21:00 sky; the ruler still reaches its real time.
            let first = SkyMath.sunAltitude(momentMillis, latitude: latitude, longitude: longitude) > 0 ? dayStart + 21 * 3_600_000 : momentMillis
            minute = Double((first - dayStart) / 60_000)
            moment = first
        }
        .onDisappear { PosterKit.openScreens -= 1 }
        .task { place = await PosterKit.place(latitude: latitude, longitude: longitude, fallback: placeName) }
        .task(id: "\(template.id)-\(moment)-\(place != nil)") {
            guard let place, moment > 0 else { return }
            failed = false
            if let next = await SkyPosterRenderer.render(template, place: place, epochMillis: moment, scale: 0.34) { preview = next } else { failed = true }
        }
    }

    private func printSize(_ template: SkyPosterTemplate, _ place: PosterPlace, _ moment: Int64) async -> UIImage? {
        if let big = await SkyPosterRenderer.render(template, place: place, epochMillis: moment, scale: SkyPosterRenderer.highRes) { return big }
        return await SkyPosterRenderer.render(template, place: place, epochMillis: moment, scale: 1)
    }
}

/// SkyPosterScreen.kt TimeRuler: the day scrolls under a fixed centre line, a tick every 5 minutes, taller ticks with
/// labels on the hours; ticks swell near the centre. A fling glides and lands on the nearest 5 minutes, then
/// [onSettled] redraws the sky. The big time above is the centre.
private struct SkyTimeRuler: View {
    @Binding var minute: Double
    let ink: Color
    let enabled: Bool
    let onSettled: (Double) -> Void

    @State private var dragStart: Double?
    private let perMinute: CGFloat = 2.2

    var body: some View {
        VStack(spacing: 4) {
            let m = min(1439, max(0, Int(minute.rounded())))
            Text(String(format: "%02d:%02d", m / 60, m % 60))
                .font(.system(size: 22, weight: .light, design: .monospaced)).kerning(2).foregroundStyle(ink)
            Canvas { ctx, size in
                let cx = size.width / 2, half = size.width / 2, base = size.height * 0.62
                let visible = Int(half / perMinute) + 10
                var tick = max(0, ((Int(minute) - visible) / 5) * 5)
                let last = min(1435, Int(minute) + visible)
                while tick <= last {
                    let x = cx + CGFloat(Double(tick) - minute) * perMinute
                    let near = 1 - min(1, max(0, abs(x - cx) / half))
                    let hour = tick % 60 == 0, quarter = tick % 15 == 0
                    let len = (hour ? 22 : quarter ? 14 : 8) * (0.7 + 0.5 * near)
                    var line = Path()
                    line.move(to: CGPoint(x: x, y: base - len))
                    line.addLine(to: CGPoint(x: x, y: base))
                    ctx.stroke(line, with: .color(ink.opacity((0.15 + 0.65 * near) * (hour ? 1 : 0.75))), lineWidth: hour ? 1.6 : 1)
                    if hour {
                        ctx.draw(Text(String(format: "%02d", tick / 60)).font(.system(size: 10)).foregroundColor(ink.opacity(0.24 + 0.63 * near)),
                                 at: CGPoint(x: x, y: base + 12))
                    }
                    tick += 5
                }
                // The fixed centre: a taller line with a small dot, where the time above is read.
                var centre = Path()
                centre.move(to: CGPoint(x: cx, y: base - 30))
                centre.addLine(to: CGPoint(x: cx, y: base + 2))
                ctx.stroke(centre, with: .color(ink), lineWidth: 2)
                ctx.fill(Path(ellipseIn: CGRect(x: cx - 3, y: base - 35, width: 6, height: 6)), with: .color(ink))
            }
            .frame(height: 58)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 2)
                .onChanged { g in
                    guard enabled else { return }
                    if dragStart == nil { dragStart = minute }
                    minute = min(1435, max(0, (dragStart ?? minute) - Double(g.translation.width / perMinute)))
                }
                .onEnded { g in
                    guard enabled, let start = dragStart else { return }
                    dragStart = nil
                    // Glide with the fling (iOS's own predicted end), then land on the nearest 5 minutes.
                    let glide = start - Double(g.predictedEndTranslation.width / perMinute)
                    let target = min(1435, max(0, (glide / 5).rounded() * 5))
                    withAnimation(.spring(response: 0.55, dampingFraction: 0.9)) { minute = target }
                    onSettled(target)
                })
        }
        .frame(maxWidth: .infinity)
    }
}

private struct SkySwatch: View {
    let template: SkyPosterTemplate
    let selected: Bool
    let ink: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 5) {
                ZStack {
                    Circle().fill(Color(argb: template.background))
                    Circle().fill(Color(argb: template.sky)).frame(width: 40, height: 40)
                        .overlay(Circle().strokeBorder(Color(argb: template.ring), lineWidth: 1))
                    ForEach(Array([CGPoint(x: -8, y: -8), .zero, CGPoint(x: 8, y: 9)].enumerated()), id: \.offset) { _, p in
                        Circle().fill(Color(argb: template.star)).frame(width: 3, height: 3).offset(x: p.x, y: p.y)
                    }
                }
                .frame(width: 54, height: 54)
                .overlay(Circle().strokeBorder(selected ? ink : ink.opacity(0.2), lineWidth: selected ? 2 : 0.5))
                Text(t(template.name)).font(RealFont.inter(11)).foregroundStyle(ink.opacity(selected ? 1 : 0.6))
            }
        }
        .buttonStyle(.plain)
    }
}
