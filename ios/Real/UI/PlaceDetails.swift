import MapKit
import SwiftUI

/// Under a card's place (iOS only, Apple Maps): street-level Look Around preview when Apple has it, and the place's
/// phone and website when it is a business Apple Maps knows. Nothing is stored; looked up when the card opens.
struct PlaceDetails: View {
    let location: ReminderLocation
    @State private var scene: MKLookAroundScene?
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
            if phone != nil || site != nil {
                HStack(spacing: 8) {
                    if let phone, let tel = URL(string: "tel:" + phone.filter { $0.isNumber || $0 == "+" }) {
                        chip("phone.fill", "Ara") { UIApplication.shared.open(tel) }
                    }
                    if let site { chip("safari", "Web sitesi") { UIApplication.shared.open(site) } }
                    Spacer(minLength: 0)
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

    private func load() async {
        guard let c = coordinate else { return }
        async let look = try? await MKLookAroundSceneRequest(coordinate: c).scene
        // The business at the pin: Apple Maps' result for its name, only when it is really this place (within 150 m).
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = location.placeName
        request.resultTypes = .pointOfInterest
        request.region = MKCoordinateRegion(center: c, latitudinalMeters: 600, longitudinalMeters: 600)
        let here = CLLocation(latitude: c.latitude, longitude: c.longitude)
        let found = (try? await MKLocalSearch(request: request).start())?.mapItems.first { m in
            CLLocation(latitude: m.placemark.coordinate.latitude, longitude: m.placemark.coordinate.longitude).distance(from: here) < 150
        }
        let s = await look
        withAnimation(.easeOut(duration: 0.25)) { scene = s; item = found }
    }
}
