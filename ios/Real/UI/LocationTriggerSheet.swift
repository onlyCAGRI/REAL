import SwiftUI
import CoreLocation

/// "Konuma varınca hatırlat" permission: iOS's own dialog (precise/approximate + while using). A place reminder needs
/// precise location, so an approximate answer asks once more for full accuracy. "While using" is enough: iOS itself
/// watches the region for UNLocationNotificationTrigger, also with REAL closed.
@Observable
final class PlaceReminderPermission: NSObject, CLLocationManagerDelegate {
    @ObservationIgnored private let manager = CLLocationManager()
    @ObservationIgnored private var completion: ((Bool) -> Void)?

    override init() {
        super.init()
        manager.delegate = self
    }

    /// Already allowed (while using, precise): the pin skips the explanation panel and opens the map.
    var granted: Bool {
        [.authorizedWhenInUse, .authorizedAlways].contains(manager.authorizationStatus) && manager.accuracyAuthorization == .fullAccuracy
    }

    func request(_ done: @escaping (Bool) -> Void) {
        completion = done
        switch manager.authorizationStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .denied, .restricted:
            finish(false)
            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
        default: checkAccuracy()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard completion != nil else { return }
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: checkAccuracy()
        case .denied, .restricted: finish(false)
        default: break
        }
    }

    private func checkAccuracy() {
        guard manager.accuracyAuthorization == .reducedAccuracy else { finish(true); return }
        manager.requestTemporaryFullAccuracyAuthorization(withPurposeKey: "PlaceReminder") { [weak self] _ in
            self?.finish(self?.manager.accuracyAuthorization == .fullAccuracy)
        }
    }

    private func finish(_ granted: Bool) {
        let done = completion
        completion = nil
        DispatchQueue.main.async { done?(granted) }
    }
}

/// After the place is picked: arrive or leave, and how close counts (LocationTriggerSheet.kt).
struct LocationTriggerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let place: ReminderLocation
    let initial: LocationTrigger?
    let onSave: (LocationTrigger) -> Void
    @State private var onExit = false
    @State private var radius = LocationTrigger.defaultRadius

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(place.placeName).font(RealFont.inter(18, .semibold)).foregroundStyle(Color(argb: 0xFFF4F4F6)).lineLimit(2)
            Text(t("Bir kere hatırlatır, sonra konum takibi biter.")).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF8E8E98))
                .padding(.top, 4)
            Picker("", selection: $onExit) {
                Text(t("Varınca")).tag(false)
                Text(t("Ayrılınca")).tag(true)
            }
            .pickerStyle(.segmented).padding(.top, 16)
            Text(t("Mesafe")).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8E8E98)).padding(.top, 14)
            Picker("", selection: $radius) {
                ForEach(LocationTrigger.radiusChoices, id: \.self) { Text(t("{0} m", $0)).tag($0) }
            }
            .pickerStyle(.segmented).padding(.top, 6)
            Button {
                if let lat = place.latitude, let lng = place.longitude {
                    onSave(LocationTrigger(placeName: place.placeName, latitude: lat, longitude: lng, radiusMeters: radius, onExit: onExit))
                }
                dismiss()
            } label: {
                Text(t("Kaydet")).font(RealFont.inter(15, .semibold)).foregroundStyle(.black)
                    .frame(maxWidth: .infinity).frame(height: 50)
                    .background(Capsule().fill(place.latitude == nil ? Color(argb: 0xFF4A4A50) : Color(argb: 0xFFF3F3F5)))
            }
            .buttonStyle(PressScaleStyle(scale: 0.97))
            .disabled(place.latitude == nil || place.longitude == nil)
            .padding(.top, 20)
            Spacer(minLength: 0)
        }
        .padding(24)
        .presentationDetents([.height(320)])
        .presentationBackground(LiquidGlassTokens.contentColor)
        .onAppear {
            onExit = initial?.onExit ?? false
            radius = initial?.radiusMeters ?? LocationTrigger.defaultRadius
        }
    }
}
