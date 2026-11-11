import SwiftUI

/// OngoingEvent.kt: a card whose event is running fills with a soft green mist and says "Devam ediyor · 36 dk kaldı".
enum OngoingEvent {
    /// The Varınca green, so a running card and an arrival read as the same "now".
    static let green = Color(argb: 0xFF5DCAA5)
    static let greenOnLight = Color(argb: 0xFF0F7A5A)

    /// "Devam ediyor · 36 dk kaldı" / "· 2 sa 10 dk kaldı" / "· 3 gün kaldı".
    static func label(end: EpochMillis, now: EpochMillis) -> String {
        let minutes = max(1, (end - now + 59_999) / 60_000)
        let left: String
        if minutes < 60 { left = t("{0} dk kaldı", minutes) }
        else if minutes < 24 * 60 {
            left = minutes % 60 == 0 ? t("{0} sa kaldı", minutes / 60) : t("{0} sa {1} dk kaldı", minutes / 60, minutes % 60)
        } else { left = t("{0} gün kaldı", (minutes + 24 * 60 - 1) / (24 * 60)) }
        return t("Devam ediyor") + " · " + left
    }
}

/// Sis dolumu: green mist from the left up to the event's progress; its front edge is blurred, never a hard bar.
/// Sits over the card's own fill and under its content; redraws every 30 s.
struct OngoingMist: View {
    let reminder: ReminderItem
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // One-off and repeating events with an end (a repeating one runs again at each occurrence).
        if reminder.endAt != nil, !reminder.isCompleted {
            TimelineView(.periodic(from: .now, by: 30)) { context in
                let now = EpochMillis(context.date.timeIntervalSince1970 * 1000)
                if let p = reminder.ongoingProgress(at: now) {
                    let front = max(0, p - 0.14), fade = min(1, max(front, p + 0.06))
                    LinearGradient(stops: [
                        .init(color: OngoingEvent.green.opacity(0.16), location: 0),
                        .init(color: OngoingEvent.green.opacity(0.10), location: front),
                        .init(color: OngoingEvent.green.opacity(0), location: fade)
                    ], startPoint: .leading, endPoint: .trailing)
                    .animation(reduceMotion ? nil : .easeInOut(duration: 0.9), value: p)
                    .transition(.opacity)
                    .allowsHitTesting(false)
                }
            }
        }
    }
}

/// Çakışma (OngoingEvent.kt ConflictAmber / conflictEdge): ids of cards whose times overlap, set once per list screen.
private struct CardConflictsKey: EnvironmentKey { static let defaultValue: Set<Int64> = [] }
extension EnvironmentValues {
    var cardConflicts: Set<Int64> {
        get { self[CardConflictsKey.self] }
        set { self[CardConflictsKey.self] = newValue }
    }
}

enum Conflict {
    static let amber = Color(argb: 0xFFFFB86F)
    static let amberOnLight = Color(argb: 0xFFB45309)
}

/// A thin amber line on the card's left edge with a narrow glow, breathing like the Kontrol ettir light but dimmer
/// and slower (2.6 s each way), so it reads as a quiet note.
struct ConflictEdge: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var bright = false

    // The Kontrol ettir light mirrored onto the left edge: full height, 17 % of the width (half of its 34 %), no line,
    // dimmer and slower (8 % ↔ 32 %, 2.6 s each way).
    var body: some View {
        let a = reduceMotion ? 0.32 : (bright ? 0.32 : 0.08)
        GeometryReader { g in
            LinearGradient(stops: [.init(color: Conflict.amber.opacity(a), location: 0),
                                   .init(color: Conflict.amber.opacity(0.35 * a), location: 0.38),
                                   .init(color: .clear, location: 1)], startPoint: .leading, endPoint: .trailing)
                .frame(width: g.size.width * 0.085)
        }
        .allowsHitTesting(false)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 2.6).repeatForever(autoreverses: true)) { bright = true }
        }
    }
}

/// The small pulsing dot in front of "Devam ediyor".
struct OngoingDot: View {
    let color: Color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dim = false

    var body: some View {
        Circle().fill(color).frame(width: 6, height: 6)
            .opacity(dim ? 0.35 : 1)
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { dim = true }
            }
    }
}

/// The card's date line for an event with an end: running → dot + green "Devam ediyor · …", ended → grey
/// "Bitti · işaretle" (not for calendar cards, which cannot be ticked), else nil and the caller draws the date.
struct OngoingDateLine: View {
    let reminder: ReminderItem
    let useDark: Bool
    let subtext: Color
    let fallback: AnyView

    var body: some View {
        TimelineView(.periodic(from: .now, by: 30)) { context in
            let now = EpochMillis(context.date.timeIntervalSince1970 * 1000)
            if let end = reminder.runningSpan(at: now)?.end {
                let green = useDark ? OngoingEvent.greenOnLight : OngoingEvent.green
                HStack(spacing: 5) {
                    OngoingDot(color: green)
                    Text(OngoingEvent.label(end: end, now: now)).foregroundStyle(green).lineLimit(1)
                }
                .layoutPriority(1)
            } else if reminder.id > 0, reminder.hasEventEnded(at: now) {
                Text(t("Bitti · işaretle")).foregroundStyle(subtext).lineLimit(1).layoutPriority(1)
            } else {
                fallback
            }
        }
    }
}

/// Çakışma in the card detail, under the time: an amber chip saying what this card overlaps with
/// ("Diş hekimi ile çakışıyor", or "Diş hekimi ve 2 kart ile çakışıyor"). Nothing when it overlaps nothing.
struct DetailConflictLine: View {
    let reminder: ReminderItem
    let others: [ReminderItem]

    var body: some View {
        let partners = reminder.isCompleted ? [] : ReminderItem.conflictPartners(of: reminder, in: others, now: .now)
        if let first = partners.first {
            let raw = first.text.split(separator: "\n").first.map { $0.trimmingCharacters(in: .whitespaces) } ?? ""
            let name = raw.isEmpty ? t("bir kart") : (raw.count > 28 ? String(raw.prefix(27)).trimmingCharacters(in: .whitespaces) + "…" : raw)
            HStack(spacing: 5) {
                Circle().fill(Conflict.amber).frame(width: 6, height: 6)
                Text(partners.count == 1 ? t("{0} ile çakışıyor", name) : t("{0} ve {1} kart ile çakışıyor", name, partners.count - 1))
                    .font(RealFont.inter(12, .semibold)).foregroundStyle(Conflict.amber).lineLimit(1)
            }
            .padding(.horizontal, 9).padding(.vertical, 3)
            .background(Capsule().fill(Conflict.amber.opacity(0.12)))
            .padding(.top, 6)
        }
    }
}
