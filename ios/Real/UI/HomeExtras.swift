import SwiftUI
import UIKit

/// Whether the keyboard is up. While it is, a tap on the list only closes it: it does not open the category or the
/// card under the finger (scrolling still works).
@MainActor
@Observable
final class KeyboardWatcher {
    static let shared = KeyboardWatcher()
    private(set) var visible = false
    private var observers: [NSObjectProtocol] = []

    private init() {
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: UIResponder.keyboardWillShowNotification, object: nil, queue: .main) { _ in
            MainActor.assumeIsolated { KeyboardWatcher.shared.visible = true }
        })
        observers.append(center.addObserver(forName: UIResponder.keyboardWillHideNotification, object: nil, queue: .main) { _ in
            MainActor.assumeIsolated { KeyboardWatcher.shared.visible = false }
        })
    }

    /// True (and the keyboard goes down) when this tap should only close the keyboard.
    func consumeTap() -> Bool {
        guard visible else { return false }
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        return true
    }
}

/// Ayarlar → "Sade kartlar" (deneysel): Home cards without the circle; a right swipe completes them (or undoes).
enum HomeCardStyle {
    static let plainKey = "home_plain_cards"
}

/// The note after a category's card count: overdue cards first ("2 gecikmiş", shown as a warning), then what is on
/// today ("1 tanesi bugün").
func categoryHeaderDetail(_ items: [ReminderItem], calendar: Calendar = .real) -> (text: String, alert: Bool)? {
    let now = EpochMillis.now
    let today = calendar.epochDay(of: Date())
    let open = items.filter { !$0.isCompleted }
    let overdue = open.filter { r in
        // iOS has no delivery receipt: a past time on a non-repeating card is overdue.
        r.id >= 0 && ((r.alertAt.map { $0 < now && !r.hasRepeat } ?? false) ||
            (r.alertAt == nil && (r.plannedDateEpochDay.map { $0 < today } ?? false) && r.quick24HourUntilAt == nil))
    }.count
    if overdue > 0 { return ("\(overdue) gecikmiş", true) }
    let todayCount = open.filter { r in
        (r.alertAt.map { calendar.epochDay(ofMillis: $0) } ?? r.plannedDateEpochDay) == today
    }.count
    return todayCount > 0 ? ("\(todayCount) tanesi bugün", false) : nil
}
