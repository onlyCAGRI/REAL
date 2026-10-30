import CoreLocation
import MapLibre
import SwiftUI
import UIKit

/// MapPosterTemplates.kt MapPosterTemplate: one colour per OpenMapTiles layer group (OpenFreeMap's vector tiles,
/// OpenStreetMap data), no labels. The same style drives the poster snapshot and the pannable map.
struct MapPosterTemplate: Identifiable, Equatable {
    let id: String
    /// Turkish key; shown through t().
    let name: String
    let background: UInt32, water: UInt32, park: UInt32, building: UInt32
    let motorway: UInt32, major: UInt32, minor: UInt32, rail: UInt32, text: UInt32
    let dark: Bool

    private init(_ id: String, _ name: String, _ background: UInt32, _ water: UInt32, _ park: UInt32, _ building: UInt32,
                 _ motorway: UInt32, _ major: UInt32, _ minor: UInt32, _ rail: UInt32, _ text: UInt32, _ dark: Bool) {
        self.id = id; self.name = name; self.background = background; self.water = water; self.park = park
        self.building = building; self.motorway = motorway; self.major = major; self.minor = minor; self.rail = rail
        self.text = text; self.dark = dark
    }

    static let all: [MapPosterTemplate] = [
        .init("sage", "Adaçayı", 0xFFEEF3EE, 0xFFB9D5D8, 0xFFDCEBD8, 0xFF9DBFAE, 0xFF2F5C49, 0xFF3E6B57, 0xFF86AB99, 0xFF9DB5AA, 0xFF24493C, false),
        .init("coast", "Kıyı", 0xFFE6F0EF, 0xFFA3CBCC, 0xFFDCEAE5, 0xFFA3C6C7, 0xFFC2702F, 0xFF4E8A8B, 0xFF93BEC0, 0xFF6E9C9D, 0xFF1F5357, false),
        .init("neon", "Neon", 0xFF0B0D18, 0xFF0D2236, 0xFF14122A, 0xFFA448E8, 0xFFFF4FA3, 0xFFEE6CC6, 0xFF7440DE, 0xFF2CC3C9, 0xFFFFD000, true),
        .init("lavender", "Lavanta", 0xFFF8F6F3, 0xFFD4E5EE, 0xFFEEF1EA, 0xFFC9BFCC, 0xFF5F6596, 0xFF8E8FB8, 0xFFB8B6CD, 0xFF9C9CB0, 0xFF57566C, false),
        .init("gold", "Gece altını", 0xFF0A1730, 0xFF0F2645, 0xFF0D1C36, 0xFF6E5D3D, 0xFFE0B040, 0xFFB98E37, 0xFF5D5434, 0xFF7A6A45, 0xFFDDB65A, true),
        .init("mint", "Nane", 0xFFE8F0EF, 0xFF8CC4C4, 0xFFDCEAE3, 0xFFA6CACA, 0xFF4A8B8A, 0xFF5D9898, 0xFFA9CCCC, 0xFF7FAAAA, 0xFFB0632A, false),
        .init("wine", "Bordo", 0xFF1F0A12, 0xFF2A1220, 0xFF250E17, 0xFFF09AB0, 0xFF9A1E42, 0xFF8E1C3C, 0xFF54403F, 0xFFEEDCC8, 0xFFF7DCC5, true),
        .init("sky", "Gökyüzü", 0xFFEEF6F9, 0xFFB9D8E6, 0xFFDCEBEA, 0xFF7DB5D4, 0xFF1C5A78, 0xFF3C7FA3, 0xFF93C3DA, 0xFF7FA9BF, 0xFF1C5A78, false),
        .init("espresso", "Espresso", 0xFF1E110C, 0xFF2A1712, 0xFF2E1810, 0xFFE6B573, 0xFFC85A2A, 0xFFB2552B, 0xFF5E4C37, 0xFFE8CFA0, 0xFFFFD58E, true),
        .init("paper", "Kâğıt", 0xFFF8F6F3, 0xFFE1E4E4, 0xFFEFEDEA, 0xFF2E2E2E, 0xFF8B2A10, 0xFF333333, 0xFF9C9C9C, 0xFF8A8A8A, 0xFF8B2A10, false),
        .init("peach", "Şeftali", 0xFFF5EDE4, 0xFFA9C6C8, 0xFFEEE4D8, 0xFFD98A78, 0xFFC66A3D, 0xFFD07A52, 0xFFE2B096, 0xFFB79A88, 0xFF8A3E14, false),
        .init("sand", "Kum", 0xFFE3D7C9, 0xFFCBD3D0, 0xFFDCD1C2, 0xFFA0705F, 0xFF7E4636, 0xFF8E5848, 0xFFB8907F, 0xFF9C8576, 0xFF3F3029, false),
        // The black-and-white pair: ink on white, and white on black.
        .init("ink", "Mürekkep", 0xFFFFFFFF, 0xFFE8E8E8, 0xFFF3F3F3, 0xFF1A1A1A, 0xFF000000, 0xFF111111, 0xFF707070, 0xFF555555, 0xFF000000, false),
        .init("night", "Gece", 0xFF000000, 0xFF151515, 0xFF0A0A0A, 0xFFE6E6E6, 0xFFFFFFFF, 0xFFF0F0F0, 0xFF7A7A7A, 0xFFAAAAAA, 0xFFFFFFFF, true),
        .init("ocean", "Okyanus", 0xFF06202B, 0xFF0B3A4A, 0xFF0A2A33, 0xFF2E8C9A, 0xFF4FE3E0, 0xFF3BB8C0, 0xFF1F5F6A, 0xFF7FD6D6, 0xFF9FF5F0, true),
        .init("forest", "Orman", 0xFF16241B, 0xFF1E3A3A, 0xFF1D3324, 0xFF7FA07A, 0xFFD9C27A, 0xFFA7C48F, 0xFF4D6B52, 0xFFB5A77A, 0xFFE8DDB0, true),
        .init("rose", "Gül", 0xFFFBF0F1, 0xFFD6E3EC, 0xFFF3E3E5, 0xFFE3A3B0, 0xFFC2426A, 0xFFD46A88, 0xFFE8B4C0, 0xFFC99AA6, 0xFF9C2F52, false),
        .init("olive", "Zeytin", 0xFFEFEDE0, 0xFFC9D3C9, 0xFFE3E3CC, 0xFF9C9A6A, 0xFF5E6B2E, 0xFF77823F, 0xFFB0AF85, 0xFF8A8A6A, 0xFF4A5420, false),
        .init("swiss", "Kırmızı", 0xFFF4EFE6, 0xFFD7DEE0, 0xFFECE6DA, 0xFFC9B9A6, 0xFFD62828, 0xFF2B2B2B, 0xFFA39E95, 0xFF7A7A7A, 0xFFD62828, false),
        .init("midnight", "Gece mavisi", 0xFF0E1B3D, 0xFF15285A, 0xFF12224A, 0xFF4A5F96, 0xFFFFFFFF, 0xFFC9D3F0, 0xFF4A5C8E, 0xFF9AA8D0, 0xFFFFFFFF, true),
        .init("pastel", "Pastel", 0xFFF4F1FA, 0xFFCFE7E3, 0xFFE6F2EA, 0xFFC8B6E2, 0xFF8A6FC9, 0xFFA992D8, 0xFFCFC3E8, 0xFFB3A8CC, 0xFF6A4FB0, false),
        .init("lava", "Lav", 0xFF1A1A1A, 0xFF262626, 0xFF202020, 0xFF8A3324, 0xFFFF5A1F, 0xFFE04A1A, 0xFF5A3A30, 0xFFB0B0B0, 0xFFFF7A3D, true),
        .init("beach", "Kumsal", 0xFFF3EAD7, 0xFF7FC8C4, 0xFFE8DFC6, 0xFFD9B98A, 0xFFE07A3F, 0xFFC99A5E, 0xFFDCC6A0, 0xFFB8A88A, 0xFF2F7F7A, false),
        .init("silver", "Gümüş", 0xFFD9DCDF, 0xFFBFC5CB, 0xFFD0D4D7, 0xFF8E949A, 0xFF2E3338, 0xFF4A5056, 0xFF9AA0A6, 0xFF6E747A, 0xFF23272B, false),
    ]

    /// Camera for the poster: a 3000 pt wide z13 canvas covers ~20-25 km, the whole city with street detail.
    static let posterZoom = 13.0
    /// The zoom bar: ~90 km (region) to ~3 km (a neighbourhood) across.
    static let zoomRange = 11.0...15.5

    /// The MapLibre style as a file (MapLibre iOS takes a style URL); one file per template.
    var styleURL: URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("map-poster-\(id).json")
        if FileManager.default.fileExists(atPath: url.path) { return url }
        guard let data = try? JSONSerialization.data(withJSONObject: styleObject()), (try? data.write(to: url)) != nil else { return nil }
        return url
    }

    private func styleObject() -> [String: Any] {
        func hex(_ c: UInt32) -> String { String(format: "#%06X", c & 0xFFFFFF) }
        /// Widths are tuned for the poster's camera and grow with zoom on the pannable map.
        func width(_ at: Double) -> [Any] {
            ["interpolate", ["exponential", 1.6], ["zoom"], 9, at * 0.35, Self.posterZoom, at, 16, at * 4, 19, at * 12]
        }
        func classes(_ list: String...) -> [Any] { ["match", ["get", "class"], list, true, false] }
        var layers: [[String: Any]] = [["id": "bg", "type": "background", "paint": ["background-color": hex(background)]]]
        func fill(_ id: String, _ layer: String, _ color: UInt32, _ filter: [Any]? = nil, _ opacity: Double = 1) {
            var l: [String: Any] = ["id": id, "type": "fill", "source": "omt", "source-layer": layer,
                                    "paint": ["fill-color": hex(color), "fill-opacity": opacity, "fill-antialias": true]]
            if let filter { l["filter"] = filter }
            layers.append(l)
        }
        func line(_ id: String, _ layer: String, _ color: UInt32, _ w: [Any], _ filter: [Any]? = nil, dash: Bool = false) {
            var paint: [String: Any] = ["line-color": hex(color), "line-width": w]
            if dash { paint["line-dasharray"] = [3, 2] }
            var l: [String: Any] = ["id": id, "type": "line", "source": "omt", "source-layer": layer,
                                    "layout": ["line-cap": "round", "line-join": "round"], "paint": paint]
            if let filter { l["filter"] = filter }
            layers.append(l)
        }
        fill("landcover", "landcover", park, classes("wood", "grass", "farmland", "scrub"), 0.55)
        fill("park", "park", park, nil, 0.8)
        fill("water", "water", water)
        line("waterway", "waterway", water, width(1.2))
        fill("aeroway", "aeroway", park, nil, 0.7)
        fill("building", "building", building, nil, dark ? 0.85 : 0.75)
        line("road-minor", "transportation", minor, width(0.8), classes("minor", "service", "track"))
        line("road-secondary", "transportation", minor, width(1.3), classes("secondary", "tertiary"))
        line("road-primary", "transportation", major, width(2.0), classes("primary", "trunk"))
        line("rail", "transportation", rail, width(0.9), classes("rail"), dash: true)
        line("road-motorway", "transportation", motorway, width(3.0), classes("motorway"))
        return ["version": 8, "sources": ["omt": ["type": "vector", "url": "https://tiles.openfreemap.org/planet"]], "layers": layers]
    }
}

/// MapPosterRenderer.kt: the poster canvas is 4:5 (3000 × 3750 pt); the camera always works on it so the preview
/// frames exactly what is saved.
@MainActor
enum MapPosterRenderer {
    static let canvas = CGSize(width: 3000, height: 3750)
    /// 3000 × 2.56 = 7680 px wide; each of the 3 × 3 snapshots is 2560 × 3200.
    static let highRes: CGFloat = 2.56
    private static let grid = 3

    /// The finished poster; [scale] < 1 is a light preview of the same frame. Nil when the map could not load.
    static func render(_ template: MapPosterTemplate, place: PosterPlace, scale: CGFloat = 1,
                       zoom: Double = MapPosterTemplate.posterZoom) async -> UIImage? {
        let center = CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude)
        guard let snap = await snapshot(template, center: center, size: canvas, scale: scale, zoom: zoom) else { return nil }
        let size = CGSize(width: canvas.width * scale, height: canvas.height * scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            snap.draw(in: CGRect(origin: .zero, size: size))
            decorate(ctx.cgContext, size: size, template, place)
        }
    }

    /// The print poster: the same frame drawn 2.56× sharper (8K wide). One snapshot that size passes the GPU's
    /// texture limit, so it is a 3 × 3 grid of snapshots, each centred on its own part of the map, stitched.
    static func renderHighRes(_ template: MapPosterTemplate, place: PosterPlace, zoom: Double,
                              onProgress: @escaping (Double) -> Void) async -> UIImage? {
        let tileW = canvas.width / CGFloat(grid), tileH = canvas.height / CGFloat(grid)
        // MapLibre's world is 512 × 2^zoom points wide (Web Mercator).
        let world = 512 * pow(2, zoom)
        let cx = (place.longitude + 180) / 360 * world
        let sinLat = sin(place.latitude * .pi / 180)
        let cy = (0.5 - log((1 + sinLat) / (1 - sinLat)) / (4 * .pi)) * world
        var tiles: [UIImage] = []
        for row in 0..<grid {
            for col in 0..<grid {
                let x = cx + (Double(col) + 0.5) * Double(tileW) - Double(canvas.width) / 2
                let y = cy + (Double(row) + 0.5) * Double(tileH) - Double(canvas.height) / 2
                let lng = x / world * 360 - 180
                let lat = atan(sinh(.pi * (1 - 2 * y / world))) * 180 / .pi
                guard let tile = await snapshot(template, center: CLLocationCoordinate2D(latitude: lat, longitude: lng),
                                                size: CGSize(width: tileW, height: tileH), scale: highRes, zoom: zoom) else { return nil }
                tiles.append(tile)
                onProgress(Double(row * grid + col + 1) / Double(grid * grid))
            }
        }
        let size = CGSize(width: canvas.width * highRes, height: canvas.height * highRes)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            for (i, tile) in tiles.enumerated() {
                let row = i / grid, col = i % grid
                tile.draw(in: CGRect(x: CGFloat(col) * tileW * highRes, y: CGFloat(row) * tileH * highRes,
                                     width: tileW * highRes, height: tileH * highRes))
            }
            decorate(ctx.cgContext, size: size, template, place)
        }
    }

    private static func snapshot(_ template: MapPosterTemplate, center: CLLocationCoordinate2D, size: CGSize,
                                 scale: CGFloat, zoom: Double) async -> UIImage? {
        guard let url = template.styleURL else { return nil }
        let camera = MLNMapCamera(lookingAtCenter: center, altitude: 10_000, pitch: 0, heading: 0)
        let options = MLNMapSnapshotOptions(styleURL: url, camera: camera, size: size)
        options.zoomLevel = zoom
        options.scale = scale
        options.showsLogo = false
        options.showsAttribution = false
        let snapshotter = MLNMapSnapshotter(options: options)
        return await withCheckedContinuation { cont in
            snapshotter.start { snapshot, _ in
                // Keeps the snapshotter alive until it answers.
                _ = snapshotter
                cont.resume(returning: snapshot?.image)
            }
        }
    }

    /// Soft fades at the top and under the title, then the title block and the OpenStreetMap credit.
    private static func decorate(_ c: CGContext, size: CGSize, _ template: MapPosterTemplate, _ place: PosterPlace) {
        let w = size.width, h = size.height
        let space = CGColorSpaceCreateDeviceRGB()
        func bg(_ a: CGFloat) -> CGColor { UIColor(argb: template.background, alpha: a).cgColor }
        if let top = CGGradient(colorsSpace: space, colors: [bg(0.75), bg(0)] as CFArray, locations: [0, 1]) {
            c.saveGState(); c.clip(to: CGRect(x: 0, y: 0, width: w, height: h * 0.16))
            c.drawLinearGradient(top, start: .zero, end: CGPoint(x: 0, y: h * 0.16), options: [])
            c.restoreGState()
        }
        if let bottom = CGGradient(colorsSpace: space, colors: [bg(0), bg(0.82), bg(0.94)] as CFArray, locations: [0, 0.55, 1]) {
            c.saveGState(); c.clip(to: CGRect(x: 0, y: h * 0.62, width: w, height: h * 0.38))
            c.drawLinearGradient(bottom, start: CGPoint(x: 0, y: h * 0.62), end: CGPoint(x: 0, y: h), options: [])
            c.restoreGState()
        }
        UIGraphicsPushContext(c)
        let ink = UIColor(argb: template.text)
        // Title: wide-spaced capitals, shrunk until it fits the poster's width.
        PosterKit.drawCentered(place.title.uppercased(with: place.locale), x: w / 2, baseline: h * 0.842,
                               font: PosterKit.titleFont(size: w * 0.085), color: ink, kern: 0.55, maxWidth: w * 0.86)
        c.setStrokeColor(UIColor(argb: template.text, alpha: 200 / 255).cgColor)
        c.setLineWidth(w * 0.0008)
        c.strokeLineSegments(between: [CGPoint(x: w * 0.40, y: h * 0.868), CGPoint(x: w * 0.60, y: h * 0.868)])
        if !place.country.isEmpty {
            PosterKit.drawCentered(place.country.uppercased(with: place.locale), x: w / 2, baseline: h * 0.902,
                                   font: PosterKit.lightFont(size: w * 0.027), color: ink)
        }
        PosterKit.drawCentered(PosterKit.coordinates(place.latitude, place.longitude), x: w / 2, baseline: h * 0.935,
                               font: PosterKit.monoFont(size: w * 0.0165), color: UIColor(argb: template.text, alpha: 215 / 255))
        let small = PosterKit.monoFont(size: w * 0.0115)
        let faint = UIColor(argb: template.text, alpha: 165 / 255)
        PosterKit.drawCentered("© OpenStreetMap contributors", x: w * 0.975, baseline: h * 0.978, font: small, color: faint, align: .right)
        PosterKit.drawCentered("Real", x: w * 0.025, baseline: h * 0.978, font: PosterKit.brandFont(size: w * 0.0115), color: faint, align: .left)
        UIGraphicsPopContext()
    }
}

/// MapPosterScreen.kt MapPosterDialog: pick a template over a live preview of the real poster, then add it to the
/// card as a photo, save it to Photos (8K), share it, or walk around the same styled map. The whole screen wears
/// the template's background, edge to edge.
struct MapPosterScreen: View {
    let latitude: Double
    let longitude: Double
    let placeName: String
    let onAttach: ((UIImage) -> Void)?
    let onClose: () -> Void

    @State private var template = MapPosterTemplate.all[0]
    @State private var place: PosterPlace?
    @State private var preview: UIImage?
    @State private var failed = false
    @State private var busy = false
    @State private var explore = false
    /// [zoom] is what the preview and the saved poster use; [sliderZoom] follows the finger and only scales the
    /// current preview, so the map is fetched once, when the finger lifts.
    @State private var zoom = MapPosterTemplate.posterZoom
    @State private var sliderZoom = MapPosterTemplate.posterZoom
    @State private var previewZoom = MapPosterTemplate.posterZoom
    @State private var progress: Double?

    var body: some View {
        let surface = Color(argb: template.background)
        let ink = template.dark ? Color.white : Color(argb: 0xFF151515)
        VStack(spacing: 0) {
            PosterHeader(title: t("Harita posteri"), ink: ink, onClose: onClose)
            Color.clear
                .aspectRatio(0.8, contentMode: .fit)
                .overlay {
                    if let preview {
                        Image(uiImage: preview).resizable().scaledToFill()
                            .scaleEffect(CGFloat(pow(2, sliderZoom - previewZoom)))
                    }
                }
                .overlay {
                    if failed { Text(t("Harita yüklenemedi")).font(RealFont.inter(13)).foregroundStyle(Color(argb: template.text)) }
                    else if preview == nil { Text(t("Poster hazırlanıyor…")).font(RealFont.inter(13)).foregroundStyle(ink.opacity(0.6)) }
                }
                .clipped()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.horizontal, 12).padding(.vertical, 4)
            HStack(spacing: 10) {
                ActionIcon(.minus, color: ink.opacity(0.6), size: 14)
                Slider(value: $sliderZoom, in: MapPosterTemplate.zoomRange, onEditingChanged: { editing in
                    if !editing { zoom = sliderZoom }
                })
                .tint(ink)
                .disabled(busy)
                ActionIcon(.add, color: ink.opacity(0.6), size: 14)
            }
            .padding(.horizontal, 16)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(MapPosterTemplate.all) { item in
                        TemplateSwatch(template: item, selected: item.id == template.id, ink: ink) { if !busy { template = item } }
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
                            let image = await MapPosterRenderer.render(template, place: place, scale: 1, zoom: zoom)
                            busy = false
                            if let image { onAttach(image); onClose() } else { RealToast.shared.show(t("Poster hazırlanamadı")) }
                        }
                    }
                }
                PosterButton(label: t("Kaydet"), ink: ink, primary: onAttach == nil, enabled: place != nil && !busy) {
                    guard let place else { return }
                    busy = true
                    let template = template, zoom = zoom
                    // Keeps going if the screen closes and then ends in a notification.
                    PosterKit.save(title: place.title, render: { await printSize(template, place, zoom) }) { busy = false }
                }
                PosterButton(label: t("Paylaş"), ink: ink, enabled: place != nil && !busy) {
                    guard let place else { return }
                    busy = true
                    Task {
                        let image = await printSize(template, place, zoom)
                        busy = false
                        if let image, let url = PosterKit.shareFile(image, name: PosterKit.fileName("REAL", place.title, template.id)) {
                            PosterKit.share(url)
                        } else { RealToast.shared.show(t("Poster hazırlanamadı")) }
                    }
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
            PosterButton(label: t("Haritada gez"), ink: ink, enabled: !busy) { explore = true }
                .padding(.horizontal, 16).padding(.bottom, 8)
            if busy {
                Text(progress.map { t("8K poster hazırlanıyor… %{0}", Int($0 * 100)) } ?? t("Poster hazırlanıyor…"))
                    .font(RealFont.inter(12)).foregroundStyle(ink.opacity(0.6)).padding(.bottom, 8)
            }
        }
        .background(surface.ignoresSafeArea())
        .animation(.easeInOut(duration: 0.32), value: template.id)
        .onAppear { PosterKit.openScreens += 1 }
        .onDisappear { PosterKit.openScreens -= 1 }
        .task { place = await PosterKit.place(latitude: latitude, longitude: longitude, fallback: placeName) }
        .task(id: "\(template.id)-\(zoom)-\(place != nil)") {
            guard let place else { return }
            failed = false
            let z = zoom
            if let next = await MapPosterRenderer.render(template, place: place, scale: 0.34, zoom: z) {
                preview = next
                previewZoom = z
            } else { failed = true }
        }
        .fullScreenCover(isPresented: $explore) {
            MapPosterExplore(latitude: latitude, longitude: longitude, template: template, place: place, zoom: zoom) { explore = false }
        }
    }

    /// Photos and share get the 8K print; a phone that cannot hold it still gets the 3000 pt poster.
    @MainActor private func printSize(_ template: MapPosterTemplate, _ place: PosterPlace, _ zoom: Double) async -> UIImage? {
        progress = 0
        defer { progress = nil }
        if let big = await MapPosterRenderer.renderHighRes(template, place: place, zoom: zoom, onProgress: { progress = $0 }) { return big }
        return await MapPosterRenderer.render(template, place: place, scale: 1, zoom: zoom)
    }
}

private struct TemplateSwatch: View {
    let template: MapPosterTemplate
    let selected: Bool
    let ink: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 5) {
                // A tiny map sketch in the template's own colours: water, a motorway, a main road, the title ink.
                ZStack {
                    Color(argb: template.background)
                    Color(argb: template.water).frame(width: 22, height: 22)
                        .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 14))
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    Color(argb: template.motorway).frame(height: 3)
                    Color(argb: template.major).frame(width: 2).frame(maxWidth: .infinity, alignment: .leading).padding(.leading, 16)
                    Capsule().fill(Color(argb: template.text)).frame(width: 24, height: 3)
                        .frame(maxHeight: .infinity, alignment: .bottom).padding(.bottom, 7)
                }
                .frame(width: 54, height: 54)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(selected ? ink : ink.opacity(0.2), lineWidth: selected ? 2 : 0.5))
                Text(t(template.name)).font(RealFont.inter(11)).foregroundStyle(ink.opacity(selected ? 1 : 0.6))
            }
        }
        .buttonStyle(.plain)
    }
}

/// The template as a map you can pan and zoom, with the poster's title resting at the bottom.
private struct MapPosterExplore: View {
    let latitude: Double
    let longitude: Double
    let template: MapPosterTemplate
    let place: PosterPlace?
    let zoom: Double
    let onClose: () -> Void

    var body: some View {
        let ink = Color(argb: template.text)
        let bg = Color(argb: template.background)
        ZStack {
            PosterMapView(center: CLLocationCoordinate2D(latitude: latitude, longitude: longitude), zoom: zoom - 1.2, styleURL: template.styleURL)
                .ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button(action: onClose) {
                        ActionIcon(.close, color: .white, size: 14).frame(width: 40, height: 40).background(Circle().fill(.black.opacity(0.55)))
                    }
                    .buttonStyle(.plain)
                }
                .padding(16)
                Spacer()
                VStack(spacing: 4) {
                    if let place {
                        Text(place.title.uppercased(with: place.locale)).font(.system(size: 26, weight: .bold)).kerning(9).lineLimit(1).foregroundStyle(ink)
                        if !place.country.isEmpty {
                            Text(place.country.uppercased(with: place.locale)).font(.system(size: 13, weight: .light)).foregroundStyle(ink).padding(.top, 2)
                        }
                    }
                    Text(PosterKit.coordinates(latitude, longitude)).font(.system(size: 11, design: .monospaced)).foregroundStyle(ink.opacity(0.8))
                    Text("© OpenStreetMap contributors").font(.system(size: 9, design: .monospaced)).foregroundStyle(ink.opacity(0.55)).padding(.top, 6)
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 48).padding(.bottom, 22)
                .background(LinearGradient(colors: [bg.opacity(0), bg.opacity(0.92)], startPoint: .top, endPoint: .bottom).ignoresSafeArea(edges: .bottom))
            }
        }
        .background(bg.ignoresSafeArea())
    }
}

/// MLNMapView with the template style; rotation and tilt off, as on Android.
private struct PosterMapView: UIViewRepresentable {
    let center: CLLocationCoordinate2D
    let zoom: Double
    let styleURL: URL?

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero, styleURL: styleURL)
        map.logoView.isHidden = true
        map.attributionButton.isHidden = true
        map.compassView.isHidden = true
        map.isRotateEnabled = false
        map.isPitchEnabled = false
        map.setCenter(center, zoomLevel: zoom, animated: false)
        return map
    }

    func updateUIView(_ uiView: MLNMapView, context: Context) {}
}
