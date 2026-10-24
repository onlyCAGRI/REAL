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
    @State private var streetView = false
    @State private var item: MKMapItem?

    private var coordinate: CLLocationCoordinate2D? {
        guard let lat = location.latitude, let lng = location.longitude else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let scene {
                LookAroundPreview(initialScene: scene, allowsNavigation: true, badgePosition: .bottomTrailing)
                    .frame(height: 140)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
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

    private func load() async {
        scene = nil
        noLookAround = false
        guard let c = coordinate else { return }
        async let look = try? await MKLookAroundSceneRequest(coordinate: c).scene
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
