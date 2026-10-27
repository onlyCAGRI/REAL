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
            let request = MKDirections.Request()
            request.source = MKMapItem(placemark: MKPlacemark(coordinate: here.coordinate))
            request.destination = MKMapItem(placemark: MKPlacemark(coordinate: place.coordinate))
            request.transportType = .automobile
            request.arrivalDate = arrival
            guard let eta = try? await MKDirections(request: request).calculateETA() else { return }
            let leave = arrival.addingTimeInterval(-eta.expectedTravelTime - margin)
            guard leave.timeIntervalSinceNow > 60 else { return }
            let minutes = Int((eta.expectedTravelTime / 60).rounded())
            let clock = DateFormatter()
            clock.locale = I18n.locale; clock.dateFormat = "HH:mm"
            let content = UNMutableNotificationContent()
            content.title = t("Çıkma vakti")
            let what = name.isEmpty ? title.split(separator: "\n").first.map(String.init) ?? "" : name
            content.body = t("{0} · trafikle yaklaşık {1} dk. {2}'da orada olmak için şimdi çık.", what.isEmpty ? t("Yol") : what, minutes, clock.string(from: arrival))
            content.sound = .default
            content.threadIdentifier = "departure"
            content.userInfo = ["reminderId": id]
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: leave.timeIntervalSinceNow, repeats: false)
            try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: identifier(id), content: content, trigger: trigger))
        }
    }

    static func refresh(_ items: [ReminderItem]) { items.forEach(plan) }
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
