import MapKit
import SwiftUI
import WebKit

/// Under a card's place (iOS only, Apple Maps): street-level Look Around preview when Apple has it, and the place's
/// phone and website when it is a business Apple Maps knows. Nothing is stored; looked up when the card opens.
/// Where Apple has no Look Around, a "Sokak görünümü" chip opens Google's keyless Street View embed on tap.
struct PlaceDetails: View {
    let location: ReminderLocation
    @State private var scene: MKLookAroundScene?
    @State private var noLookAround = false
    @State private var lookImage: UIImage?
    @State private var showLook = false
    /// Scenes found this run (or nil: none here) and their stills, so a card opened again shows them at once.
    @MainActor private static var scenes: [String: MKLookAroundScene?] = [:]
    @MainActor private static var stills: [String: UIImage] = [:]
    @State private var streetView = false
    @State private var item: MKMapItem?

    private var coordinate: CLLocationCoordinate2D? {
        guard let lat = location.latitude, let lng = location.longitude else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let scene {
                // A still of the scene that fades in once it is drawn (the live preview sat black while it loaded),
                // with Apple's badge; a tap opens the full Look Around.
                Button { showLook = true } label: {
                    // The still carries Apple's own Maps badge; ours only names the action.
                    Color.white.opacity(0.06)
                        .frame(maxWidth: .infinity).frame(height: 140)
                        .overlay {
                            if let lookImage {
                                Image(uiImage: lookImage).resizable().scaledToFill().transition(.opacity)
                            }
                        }
                        .overlay(alignment: .bottomTrailing) {
                            Label(t("Etrafa Bak"), systemImage: "binoculars.fill")
                                .font(.system(size: 12, weight: .semibold)).foregroundStyle(.white)
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(Capsule().fill(.black.opacity(0.6)))
                                .padding(10)
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(PressScaleStyle(scale: 0.98))
                .lookAroundViewer(isPresented: $showLook, initialScene: scene)
                .task(id: sceneKey) { await drawLook(scene) }
            }
            let phone = item?.phoneNumber.flatMap { $0.isEmpty ? nil : $0 }
            let site = item?.url
            let showStreetView = noLookAround && scene == nil && coordinate != nil
            if showStreetView || phone != nil || site != nil {
                HStack(spacing: 8) {
                    if showStreetView {
                        chip("binoculars.fill", t("Sokak görünümü")) { streetView = true }
                    }
                    if let phone, let tel = URL(string: "tel:" + phone.filter { $0.isNumber || $0 == "+" }) {
                        chip("phone.fill", t("Ara")) { UIApplication.shared.open(tel) }
                    }
                    if let site { chip("safari", t("Web sitesi")) { UIApplication.shared.open(site) } }
                    Spacer(minLength: 0)
                }
            }
        }
        .fullScreenCover(isPresented: $streetView) {
            if let c = coordinate {
                ZStack(alignment: .topTrailing) {
                    Color.black.ignoresSafeArea()
                    StreetViewEmbedWebView(coordinate: c)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .ignoresSafeArea()
                    Button { streetView = false } label: {
                        Text("✕")
                            .font(.system(size: 16))
                            .foregroundStyle(.white)
                            .frame(width: 40, height: 40)
                            .background(Circle().fill(.black.opacity(0.55)))
                    }
                    .buttonStyle(.plain)
                    .padding(16)
                }
            }
        }
        .task(id: "\(location.latitude ?? 0),\(location.longitude ?? 0)") { await load() }
    }

    private func chip(_ symbol: String, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                RealSymbol(symbol, size: 12)
                Text(label).font(RealFont.inter(13, .medium))
            }
            .foregroundStyle(.white.opacity(0.9)).padding(.horizontal, 12).frame(height: 34)
            .background(Capsule().fill(.white.opacity(0.08)))
        }
        .buttonStyle(.plain)
    }

    private static func sameName(_ a: String?, _ b: String) -> Bool {
        func norm(_ s: String) -> String {
            s.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "tr_TR"))
                .components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }.joined(separator: " ")
        }
        let x = norm(a ?? ""), y = norm(b)
        return x.count >= 3 && y.count >= 3 && (x == y || x.hasPrefix(y) || y.hasPrefix(x))
    }

    private func drawLook(_ scene: MKLookAroundScene) async {
        let key = sceneKey
        if let still = Self.stills[key] { lookImage = still; return }
        let options = MKLookAroundSnapshotter.Options()
        options.size = CGSize(width: 344, height: 140)   // the tile's own shape, so Apple's badge is not cropped
        guard let image = try? await MKLookAroundSnapshotter(scene: scene, options: options).snapshot.image,
              !Task.isCancelled else { return }
        Self.stills[key] = image
        withAnimation(.easeOut(duration: 0.35)) { lookImage = image }
    }

    private static func scene(at c: CLLocationCoordinate2D, known: MKLookAroundScene??) async -> MKLookAroundScene? {
        if let known { return known }
        return try? await MKLookAroundSceneRequest(coordinate: c).scene
    }

    private var sceneKey: String { String(format: "%.5f,%.5f", location.latitude ?? 0, location.longitude ?? 0) }

    private func load() async {
        noLookAround = false
        guard let c = coordinate else { scene = nil; return }
        let key = sceneKey
        if let remembered = Self.scenes[key] { scene = remembered; noLookAround = remembered == nil } else { scene = nil }
        lookImage = Self.stills[key]
        let known = Self.scenes[key]
        async let look = Self.scene(at: c, known: known)
        // The business at the pin: Apple Maps' result for its name, only when it is really this place (within 150 m
        // and the same name; a street or an address picked by hand never borrows a nearby shop's phone).
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = location.placeName
        request.resultTypes = .pointOfInterest
        request.region = MKCoordinateRegion(center: c, latitudinalMeters: 600, longitudinalMeters: 600)
        let here = CLLocation(latitude: c.latitude, longitude: c.longitude)
        let found = (try? await MKLocalSearch(request: request).start())?.mapItems.first { m in
            CLLocation(latitude: m.placemark.coordinate.latitude, longitude: m.placemark.coordinate.longitude).distance(from: here) < 150
                && Self.sameName(m.name, location.placeName)
        }
        let s = await look
        guard !Task.isCancelled else { return }
        Self.scenes[key] = s
        withAnimation(.easeOut(duration: 0.25)) {
            scene = s
            noLookAround = (s == nil)
            item = found
        }
    }
}

/// Google's own keyless Share → Embed iframe, used only where Apple has no Look Around.
private struct StreetViewEmbedWebView: UIViewRepresentable {
    let coordinate: CLLocationCoordinate2D

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        config.allowsInlineMediaPlayback = true
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        webView.isOpaque = false
        webView.backgroundColor = .black
        webView.scrollView.backgroundColor = .black
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        let posix = Locale(identifier: "en_US_POSIX")
        let lat = String(format: "%.6f", locale: posix, coordinate.latitude)
        let lng = String(format: "%.6f", locale: posix, coordinate.longitude)
        let html = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{position:fixed;inset:0;border:0;width:100%;height:100%}</style></head>
        <body><iframe allowfullscreen referrerpolicy="no-referrer-when-downgrade"
        src="https://www.google.com/maps/embed?pb=!6m6!1m5!2m2!1d\(lat)!2d\(lng)!4f-0!5f1"></iframe></body></html>
        """
        webView.loadHTMLString(html, baseURL: URL(string: "https://real-backend-staging.cagrigul1907.workers.dev"))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
