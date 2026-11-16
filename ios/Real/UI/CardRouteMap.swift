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
    /// How much of the line is drawn (0…1): it grows from the phone to the place once per entry.
    @State private var progress: Double = 0
    @AppStorage("reduce_animations") private var reduceAnimations = false
    @AppStorage(MapAppearance.darkKey) private var mapDark = false
    @AppStorage(MapAppearance.plainKey) private var mapPlain = false
    /// The live map stays hidden until its tiles had time to load: it used to show an empty grid, then pop in.
    @State private var mapVisible = false
    /// A picture of this map from an earlier visit (or drawn now), under the live map while it loads.
    @State private var snapshot: UIImage?
    private static let snapshots: NSCache<NSString, UIImage> = { let c = NSCache<NSString, UIImage>(); c.countLimit = 30; return c }()

    var body: some View {
        Map(position: Binding(get: { position ?? .region(MKCoordinateRegion(center: coordinate, latitudinalMeters: 900, longitudinalMeters: 900)) },
                              set: { position = $0 }),
            interactionModes: []) {
            if let route {
                MapPolyline(coordinates: Self.prefix(of: route.polyline, progress)).stroke(Color(argb: 0xFF5B8DEF), style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
            }
            if let origin {
                Annotation("", coordinate: origin) {
                    Circle().fill(Color(argb: 0xFF5B8DEF)).frame(width: 12, height: 12)
                        .overlay(Circle().strokeBorder(.white, lineWidth: 2.5))
                }
            }
            Marker(location.placeName, coordinate: coordinate).tint(.white)
        }
        .mapStyle(MapAppearance.style(plain: mapPlain))
        .environment(\.colorScheme, MapAppearance.scheme(dark: mapDark))
        .opacity(mapVisible ? 1 : 0)
        .background { placeholder }
        .frame(height: route == nil ? 150 : 280)
        .clipped()
        .allowsHitTesting(false)
        .overlay(alignment: .topLeading) {
            if let route {
                Text(t("Trafikle {0} dk", max(1, Int((route.expectedTravelTime / 60).rounded()))))
                    .font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(Capsule().fill(Color.black.opacity(0.85)))
                    .padding(10)
            }
        }
        .animation(.easeOut(duration: 0.3), value: route == nil)
        .task(id: routeWanted) { await load() }
        .task { await reveal() }
    }

    /// The map's own ground colour, then the remembered picture of it; the live map fades in over both.
    private var placeholder: some View {
        ZStack {
            mapDark ? Color(argb: 0xFF24262A) : Color(argb: 0xFFEEEAE2)
            if let snapshot {
                Image(uiImage: snapshot).resizable().scaledToFill().transition(.opacity)
            }
        }
    }

    private var snapshotKey: NSString {
        String(format: "%.5f,%.5f,%d,%d", coordinate.latitude, coordinate.longitude, mapDark ? 1 : 0, mapPlain ? 1 : 0) as NSString
    }

    private func reveal() async {
        let key = snapshotKey
        let cached = Self.snapshots.object(forKey: key)
        if let cached { snapshot = cached } else { Task { await drawSnapshot(key) } }
        // A remembered picture looks like the map already, so the live one can come sooner.
        try? await Task.sleep(for: .milliseconds(cached == nil ? 450 : 200))
        guard !Task.isCancelled else { return }
        withAnimation(.easeOut(duration: 0.3)) { mapVisible = true }
    }

    private func drawSnapshot(_ key: NSString) async {
        let options = MKMapSnapshotter.Options()
        options.region = MKCoordinateRegion(center: coordinate, latitudinalMeters: 900, longitudinalMeters: 900)
        options.size = CGSize(width: 400, height: 150)
        options.traitCollection = UITraitCollection(userInterfaceStyle: mapDark ? .dark : .light)
        let config = MKStandardMapConfiguration()
        config.pointOfInterestFilter = mapPlain ? .excludingAll : .includingAll
        options.preferredConfiguration = config
        guard let image = try? await MKMapSnapshotter(options: options).start().image else { return }
        Self.snapshots.setObject(image, forKey: key)
        if !mapVisible { withAnimation(.easeOut(duration: 0.2)) { snapshot = image } }
    }

    private func load() async {
        guard routeWanted else { route = nil; origin = nil; return }
        guard let here = await CurrentLocation.fix() else { return }
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
        await draw()
    }

    /// Grows the line start → destination (ease-out, ~1.4 s); with reduced animations it is whole at once.
    private func draw() async {
        guard !reduceAnimations else { progress = 1; return }
        progress = 0
        try? await Task.sleep(for: .milliseconds(150))
        let steps = 70
        for step in 1...steps {
            guard !Task.isCancelled else { progress = 1; return }
            let x = Double(step) / Double(steps)
            progress = 1 - pow(1 - x, 3)
            try? await Task.sleep(for: .milliseconds(20))
        }
        progress = 1
    }

    /// The first [fraction] of the polyline by length, ending on an interpolated point.
    private static func prefix(of line: MKPolyline, _ fraction: Double) -> [CLLocationCoordinate2D] {
        let points = UnsafeBufferPointer(start: line.points(), count: line.pointCount).map { $0 }
        guard points.count > 1, fraction < 1 else { return points.map(\.coordinate) }
        var lengths: [Double] = []
        for i in 1..<points.count { lengths.append(points[i - 1].distance(to: points[i])) }
        var left = lengths.reduce(0, +) * max(0, fraction)
        var out = [points[0].coordinate]
        for i in 1..<points.count {
            let seg = lengths[i - 1]
            if left >= seg { out.append(points[i].coordinate); left -= seg; continue }
            let t = seg > 0 ? left / seg : 0
            out.append(MKMapPoint(x: points[i - 1].x + (points[i].x - points[i - 1].x) * t,
                                  y: points[i - 1].y + (points[i].y - points[i - 1].y) * t).coordinate)
            break
        }
        return out
    }
}
