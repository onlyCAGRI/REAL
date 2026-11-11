import CoreLocation
import MapKit
import UserNotifications

/// "Çıkma vakti": a card with a time and a place gets one more notification when it is time to leave, from Apple
/// Maps' driving time with the expected traffic for that hour (plus 10 minutes). Planned from where the phone last
/// was (no location prompt of its own: only when location is already allowed), for cards within the next 24 hours,
/// and planned again whenever the card is scheduled or REAL comes to the front.
enum DepartureReminder {
    static func identifier(_ id: Int64) -> String { "reminder-\(id)-depart" }
    private static let margin: TimeInterval = 10 * 60

    static func cancel(_ id: Int64) {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [identifier(id)])
    }

    static func plan(_ item: ReminderItem) {
        cancel(item.id)
        guard !item.isCompleted, let alert = item.alertAt, let lat = item.location?.latitude, let lng = item.location?.longitude else { return }
        let arrival = Date(timeIntervalSince1970: TimeInterval(alert) / 1000)
        guard arrival.timeIntervalSinceNow > 20 * 60, arrival.timeIntervalSinceNow < 24 * 3600 else { return }
        let place = CLLocation(latitude: lat, longitude: lng)
        let id = item.id, name = item.location?.placeName ?? "", title = item.text
        Task {
            guard let here = await CurrentLocation.fix() else { return }
            // Close by: nothing to plan.
            guard here.distance(from: place) > 1500 else { return }
            let mode = TravelMode.current
            guard let travel = await travelTime(from: here, to: place, arrival: arrival, mode: mode) else { return }
            let leave = arrival.addingTimeInterval(-travel - margin)
            guard leave.timeIntervalSinceNow > 60 else { return }
            let minutes = Int((travel / 60).rounded())
            let clock = DateFormatter()
            clock.locale = I18n.locale; clock.dateFormat = "HH:mm"
            let content = UNMutableNotificationContent()
            content.title = t("Çıkma vakti")
            let what = name.isEmpty ? title.split(separator: "\n").first.map(String.init) ?? "" : name
            // "trafikle" only for a drive; walking, transit and the average just say "yolda".
            content.body = mode == .car
                ? t("{0} · trafikle yaklaşık {1} dk. {2}'da orada olmak için şimdi çık.", what.isEmpty ? t("Yol") : what, minutes, clock.string(from: arrival))
                : t("{0} · yolda yaklaşık {1} dk. {2}'da orada olmak için şimdi çık.", what.isEmpty ? t("Yol") : what, minutes, clock.string(from: arrival))
            content.sound = .default
            content.threadIdentifier = "departure"
            content.userInfo = ["reminderId": id]
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: leave.timeIntervalSinceNow, repeats: false)
            try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: identifier(id), content: content, trigger: trigger))
        }
    }

    static func refresh(_ items: [ReminderItem]) { items.forEach(plan) }

    /// Seconds to the place for [mode], as the worker does on Android: driving (traffic at that hour), walking, transit,
    /// or Ortalama = halfway between driving and walking, never more than twice the drive. Transit falls back to the
    /// average where Apple has no transit data (and none for dolmuş).
    private static func travelTime(from here: CLLocation, to place: CLLocation, arrival: Date, mode: TravelMode) async -> TimeInterval? {
        func eta(_ type: MKDirectionsTransportType) async -> TimeInterval? {
            let request = MKDirections.Request()
            request.source = MKMapItem(placemark: MKPlacemark(coordinate: here.coordinate))
            request.destination = MKMapItem(placemark: MKPlacemark(coordinate: place.coordinate))
            request.transportType = type
            request.arrivalDate = arrival
            return (try? await MKDirections(request: request).calculateETA())?.expectedTravelTime
        }
        func average() async -> TimeInterval? {
            async let car = eta(.automobile)
            async let walk = eta(.walking)
            guard let drive = await car else { return nil }
            guard let foot = await walk else { return drive }
            return min((drive + foot) / 2, drive * 2)
        }
        switch mode {
        case .car: return await eta(.automobile)
        case .walk: return await eta(.walking)
        case .transit: if let transit = await eta(.transit) { return transit } else { return await average() }
        case .average: return await average()
        }
    }
}

/// Ayarlar → "Yola çıkış" (TravelModeSetting on Android): how the time to leave is counted, Ortalama by default.
enum TravelMode: String, CaseIterable, Identifiable {
    case average, car, transit, walk
    var id: String { rawValue }
    var label: String {
        switch self {
        case .average: return t("Ortalama")
        case .car: return t("Araba")
        case .transit: return t("Toplu taşıma")
        case .walk: return t("Yürüyüş")
        }
    }
    static let storageKey = "travel_mode"
    static var current: TravelMode { TravelMode(rawValue: UserDefaults.standard.string(forKey: storageKey) ?? "") ?? .average }
}

/// Where the phone is, for the departure time and the card's route: the system's cached position when it is under
/// 30 minutes old, otherwise one fresh fix (nil without location permission, or after 15 seconds without a fix).
@MainActor
final class CurrentLocation: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var waiting: CheckedContinuation<CLLocation?, Never>?
    private static var active: Set<CurrentLocation> = []

    static func fix() async -> CLLocation? {
        let probe = CurrentLocation()
        guard [.authorizedWhenInUse, .authorizedAlways].contains(probe.manager.authorizationStatus) else { return nil }
        if let cached = probe.manager.location, cached.timestamp.timeIntervalSinceNow > -30 * 60 { return cached }
        active.insert(probe)
        defer { active.remove(probe) }
        let fresh = await probe.request()
        return fresh ?? probe.manager.location
    }

    private func request() async -> CLLocation? {
        await withCheckedContinuation { c in
            waiting = c
            manager.delegate = self
            manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
            manager.requestLocation()
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                self?.finish(nil)
            }
        }
    }

    private func finish(_ location: CLLocation?) {
        waiting?.resume(returning: location)
        waiting = nil
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let last = locations.last
        Task { @MainActor in self.finish(last) }
    }
    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.finish(nil) }
    }
}
