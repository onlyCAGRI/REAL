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
        let manager = CLLocationManager()
        guard [.authorizedWhenInUse, .authorizedAlways].contains(manager.authorizationStatus), let here = manager.location else { return }
        let place = CLLocation(latitude: lat, longitude: lng)
        // Close by: nothing to plan.
        guard here.distance(from: place) > 1500 else { return }
        let id = item.id, name = item.location?.placeName ?? "", title = item.text
        Task {
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
            clock.locale = Locale(identifier: "tr_TR"); clock.dateFormat = "HH:mm"
            let content = UNMutableNotificationContent()
            content.title = "Çıkma vakti"
            let what = name.isEmpty ? title.split(separator: "\n").first.map(String.init) ?? "" : name
            content.body = "\(what.isEmpty ? "Yol" : what) · trafikle yaklaşık \(minutes) dk. \(clock.string(from: arrival))'da orada olmak için şimdi çık."
            content.sound = .default
            content.threadIdentifier = "departure"
            content.userInfo = ["reminderId": id]
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: leave.timeIntervalSinceNow, repeats: false)
            try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: identifier(id), content: content, trigger: trigger))
        }
    }

    static func refresh(_ items: [ReminderItem]) { items.forEach(plan) }
}
