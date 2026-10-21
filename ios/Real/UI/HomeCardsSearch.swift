import SwiftUI
import UIKit

/// Home "Kartlarım" (HomeCardsSearch.kt): swiping the title left turns it into "Kartlarda ara…" in the same place; the
/// cards under it filter while typing. Swiping the field right or "×" brings the title back. The first time, the title
/// nudges left twice so the gesture can be found.
struct HomeCardsSearchTitle<Trailing: View>: View {
    @Binding var open: Bool
    @Binding var query: String
    @ViewBuilder var trailing: () -> Trailing
    @State private var drag: CGFloat = 0
    @AppStorage("home_cards_search_swipe_hint") private var hinted = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    private let threshold: CGFloat = 64

    var body: some View {
        ZStack {
            // The field opens right where the title was (no slide in from the right edge).
            if open {
                field.transition(.opacity)
            } else {
                closed.transition(.move(edge: .leading).combined(with: .opacity))
            }
        }
        .animation(reduceMotion ? nil : .timingCurve(0.2, 0, 0, 1, duration: 0.3), value: open)
    }

    private var progress: Double { Double(min(max(-drag / threshold, 0), 1)) }

    /// Measured on screen: the title it sits on slides with the finger.
    private var pull: some Gesture {
        DragGesture(minimumDistance: 10, coordinateSpace: .global)
            .onChanged { v in
                guard abs(v.translation.width) > abs(v.translation.height) else { return }
                let next = min(0, max(-threshold * 1.6, v.translation.width))
                if next < -threshold && drag >= -threshold { UISelectionFeedbackGenerator().selectionChanged() }
                drag = next
            }
            .onEnded { _ in
                let pulled = drag < -threshold
                withAnimation(.spring(duration: 0.3)) { drag = 0 }
                if pulled { open = true }
            }
    }

    private var closed: some View {
        HStack(spacing: 0) {
            // Pulling left: the title slides out with the finger and "Kartlarda ara" comes up in its place.
            ZStack(alignment: .leading) {
                Text("Kartlarda ara…").font(RealFont.inter(17)).foregroundStyle(Color(argb: 0xFF9A9AA4))
                    .offset(x: (1 - progress) * 24)
                    .opacity(progress)
                HStack(spacing: 8) {
                    Text("Kartlarım").font(RealFont.inter(20, .semibold)).foregroundStyle(Color(argb: 0xFFF2F2F4))
                    // A context card is waiting on top of the list: its colour dot sits right beside the title.
                    if let type = ContextCardPresence.shared.activeType {
                        Circle().fill(type.accent).frame(width: 7, height: 7)
                    }
                }
                // Only the title itself pulls (not the empty row beside it).
                .padding(.vertical, 10)
                .contentShape(Rectangle())
                .simultaneousGesture(pull)
                .offset(x: drag)
                .opacity(1 - progress)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: 44)
            .accessibilityAction(named: "Kartlarda ara") { open = true }
            trailing()
        }
        .task {
            guard !hinted, !reduceMotion else { return }
            try? await Task.sleep(for: .seconds(1.4))
            for _ in 0..<2 {
                withAnimation(.easeOut(duration: 0.22)) { drag = -22 }
                try? await Task.sleep(for: .milliseconds(240))
                withAnimation(.spring(duration: 0.35, bounce: 0.5)) { drag = 0 }
                try? await Task.sleep(for: .milliseconds(380))
            }
            hinted = true
        }
    }

    private var field: some View {
        HStack(spacing: 4) {
            TextField("", text: $query, prompt: Text("Kartlarda ara…").foregroundStyle(Color(argb: 0xFF77777F)))
                .font(RealFont.inter(17)).foregroundStyle(Color(argb: 0xFFF2F2F4)).tint(.white)
                .focused($focused).submitLabel(.search).autocorrectionDisabled()
                .accessibilityLabel("Kartlarda ara")
            Button(action: close) {
                Text("×").font(.system(size: 24)).foregroundStyle(Color(argb: 0xFFC8C8D0)).frame(width: 38, height: 38)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Aramayı kapat")
        }
        .padding(.leading, 16).padding(.trailing, 4).frame(height: 44)
        .background(Capsule().fill(Color(argb: 0xFF1C1C21)))
        .padding(.trailing, 12)
        .simultaneousGesture(DragGesture(minimumDistance: 20).onEnded { v in
            if v.translation.width > threshold && abs(v.translation.width) > abs(v.translation.height) { close() }
        })
        .onAppear { focused = true }
    }

    private func close() {
        focused = false
        query = ""
        open = false
    }
}
