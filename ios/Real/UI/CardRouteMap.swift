import CoreLocation
import MapKit
import SwiftUI

/// The card's map in the detail page (ReminderLocationMap in Android). Near the card's time ([routeWanted]) and when
/// location is already allowed, Apple's driving route from where the phone is to the place is drawn on it, the map
/// grows taller and shows "Trafikle N dk". Close by (≤ 1.5 km) or without a route it stays the usual small map.
struct CardRouteMap: View {
    let location: ReminderLocation
    let coordinate: CLLocationCoordinate2D
    let routeWanted: Bool
    @State private var route: MKRoute?
    @State private var origin: CLLocationCoordinate2D?
    @State private var position: MapCameraPosition?

    var body: some View {
        Map(position: Binding(get: { position ?? .region(MKCoordinateRegion(center: coordinate, latitudinalMeters: 900, longitudinalMeters: 900)) },
                              set: { position = $0 }),
            interactionModes: []) {
            if let route {
                MapPolyline(route.polyline).stroke(Color(argb: 0xFF5B8DEF), style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
            }
            if let origin {
                Annotation("", coordinate: origin) {
                    Circle().fill(Color(argb: 0xFF5B8DEF)).frame(width: 12, height: 12)
                        .overlay(Circle().strokeBorder(.white, lineWidth: 2.5))
                }
            }
            Marker(location.placeName, coordinate: coordinate).tint(.white)
        }
        .environment(\.colorScheme, .dark)
        .frame(height: route == nil ? 150 : 280)
        .allowsHitTesting(false)
        .overlay(alignment: .topLeading) {
            if let route {
                Text("Trafikle \(max(1, Int((route.expectedTravelTime / 60).rounded()))) dk")
                    .font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(Capsule().fill(Color.black.opacity(0.85)))
                    .padding(10)
            }
        }
        .animation(.easeOut(duration: 0.3), value: route == nil)
        .task(id: routeWanted) { await load() }
    }

    private func load() async {
        guard routeWanted else { route = nil; origin = nil; return }
        let manager = CLLocationManager()
        guard [.authorizedWhenInUse, .authorizedAlways].contains(manager.authorizationStatus), let here = manager.location else { return }
        guard here.distance(from: CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)) > 1500 else { return }
        let request = MKDirections.Request()
        request.source = MKMapItem(placemark: MKPlacemark(coordinate: here.coordinate))
        request.destination = MKMapItem(placemark: MKPlacemark(coordinate: coordinate))
        request.transportType = .automobile
        guard let found = try? await MKDirections(request: request).calculate().routes.first else { return }
        // Both ends in view, with room around the line.
        let rect = found.polyline.boundingMapRect.union(MKMapRect(origin: MKMapPoint(coordinate), size: MKMapSize(width: 1, height: 1)))
        let pad = max(rect.width, rect.height) * 0.18
        origin = here.coordinate
        route = found
        position = .rect(rect.insetBy(dx: -pad, dy: -pad))
    }
}
