import CoreLocation
import Network
import SwiftUI
import UserNotifications

/// True while the phone has a usable network path.
@MainActor
@Observable
final class NetworkStatus {
    static let shared = NetworkStatus()
    var online = true
    private let monitor = NWPathMonitor()

    private init() {
        monitor.pathUpdateHandler = { path in
            let ok = path.status == .satisfied
            Task { @MainActor in NetworkStatus.shared.online = ok }
        }
        monitor.start(queue: DispatchQueue(label: "real.network"))
    }
}

/// HomeStatusCapsule (OfflineBadge.kt): beside the home avatar, a crossed Wi-Fi while offline and a blue location
/// arrow while iOS watches places for arrival / departure cards. Both share one capsule, which buds out of the
/// avatar like a droplet (a neck that thins and snaps) and draws back into it when empty. Tap → what it means.
struct HomeStatusCapsule: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var watching = 0
    @State private var width: CGFloat = 0
    @State private var detach: CGFloat = 0
    @State private var info: Info?
    /// The header's spacing to the avatar, where the neck runs.
    var gap: CGFloat = 8

    enum Info: Int, Identifiable { case wifi, gps; var id: Int { rawValue } }

    private var offline: Bool { !NetworkStatus.shared.online }
    private var gps: Bool { watching > 0 }
    private var count: Int { (offline ? 1 : 0) + (gps ? 1 : 0) }
    private var shift: CGFloat { (1 - detach) * (width / 2 + gap) }
    /// Cards with a place: a change re-reads what iOS is watching.
    private var placeKey: [Int64] { store.reminders.filter { $0.locationTrigger != nil && !$0.isCompleted }.map(\.id) }

    var body: some View {
        ZStack {
            DropletShape(detach: detach, gap: gap, part: .neck).fill(Color(argb: 0xFF232328))
            DropletShape(detach: detach, gap: gap, part: .capsule).fill(Color(argb: 0xFF232328))
            Capsule().strokeBorder(.white.opacity(0.12 * min(max(detach, 0), 1)), lineWidth: 0.75)
                .offset(x: shift)
            HStack(spacing: 0) {
                if offline {
                    Image(systemName: "wifi.slash").font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.72))
                        .frame(maxWidth: .infinity, maxHeight: .infinity).contentShape(Rectangle())
                        .onTapGesture { info = .wifi }
                        .accessibilityLabel(t("İnternet bağlantın yok")).accessibilityAddTraits(.isButton)
                }
                if gps {
                    Image(systemName: "location.fill").font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color(argb: 0xFF5FB4E6))
                        .frame(maxWidth: .infinity, maxHeight: .infinity).contentShape(Rectangle())
                        .onTapGesture { info = .gps }
                        .accessibilityLabel(t("Konum izleniyor")).accessibilityAddTraits(.isButton)
                }
            }
            .padding(.horizontal, count == 2 ? 3 : 0)
            .opacity(Double(min(max((detach - 0.45) / 0.55, 0), 1)))
            .offset(x: shift)
        }
        .frame(width: max(width, 0), height: 30)
        .onChange(of: count, initial: true) { _, n in animate(to: n) }
        .task(id: placeKey) {
            try? await Task.sleep(for: .seconds(1))
            while !Task.isCancelled {
                await refreshWatching()
                try? await Task.sleep(for: .seconds(30))
            }
        }
        .popover(item: $info) { kind in
            VStack(alignment: .leading, spacing: 4) {
                Text(kind == .gps ? t("Konum izleniyor") : t("İnternet bağlantın yok"))
                    .font(RealFont.inter(15, .semibold)).foregroundStyle(Color(white: 0.95))
                Text(kind == .gps
                     ? t("{0} yer için varınca veya ayrılınca hatırlatıcısı bekliyor. Hiç kalmayınca REAL konumunu kullanmaz.", watching)
                     : t("Kartların bu cihazda kalır, bağlantı gelince eşitlenir."))
                    .font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.62))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .frame(width: 260, alignment: .leading)
            .presentationCompactAdaptation(.popover)
            .presentationBackground(Color(argb: 0xFF1C1C20))
        }
    }

    private func refreshWatching() async {
        let status = CLLocationManager().authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { watching = 0; return }
        let pending = await UNUserNotificationCenter.current().pendingNotificationRequests()
        watching = pending.filter { $0.identifier.hasSuffix("-place") }.count
    }

    private func animate(to n: Int) {
        let target: CGFloat = n == 0 ? 0 : (n == 1 ? 30 : 54)
        if reduceMotion {
            withAnimation(.easeOut(duration: 0.2)) { width = target; detach = n == 0 ? 0 : 1 }
            return
        }
        if n > 0 {
            if width < 1 {
                var still = Transaction()
                still.disablesAnimations = true
                withTransaction(still) { detach = 0 }
            }
            withAnimation(.spring(response: 0.45, dampingFraction: 0.62)) { width = target }
            withAnimation(.spring(response: 0.6, dampingFraction: 0.5)) { detach = 1 }
        } else {
            withAnimation(.spring(response: 0.35, dampingFraction: 1)) { detach = 0; width = 0 }
        }
    }
}

/// The capsule at its place, or the neck that ties it to the avatar while it buds (thick → thin → gone).
private struct DropletShape: Shape {
    enum Part { case neck, capsule }
    var detach: CGFloat
    let gap: CGFloat
    let part: Part

    var animatableData: CGFloat {
        get { detach }
        set { detach = newValue }
    }

    func path(in rect: CGRect) -> Path {
        var p = Path()
        let w = rect.width, h = rect.height, cy = rect.midY
        guard w > 0.5 else { return p }
        let shift = (1 - detach) * (w / 2 + gap)
        switch part {
        case .capsule:
            p.addRoundedRect(in: CGRect(x: shift, y: 0, width: w, height: h), cornerSize: CGSize(width: h / 2, height: h / 2))
        case .neck:
            let neck = min(max(1 - detach / 0.8, 0), 1)
            guard neck > 0 else { return p }
            let x0 = shift + w - h / 2, x1 = w + gap + 8, mid = (x0 + x1) / 2
            let h0 = h * 0.42 * neck, hm = h * 0.14 * neck * neck, h1 = h * 0.46 * neck
            p.move(to: CGPoint(x: x0, y: cy - h0))
            p.addQuadCurve(to: CGPoint(x: x1, y: cy - h1), control: CGPoint(x: mid, y: cy - hm))
            p.addLine(to: CGPoint(x: x1, y: cy + h1))
            p.addQuadCurve(to: CGPoint(x: x0, y: cy + h0), control: CGPoint(x: mid, y: cy + hm))
            p.closeSubpath()
        }
        return p
    }
}
