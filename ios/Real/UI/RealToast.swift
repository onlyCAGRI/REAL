import SwiftUI

/// RealToast.kt: one message at a time at the top of the screen, optionally with one action ("Geri al").
@MainActor @Observable
final class RealToast {
    static let shared = RealToast()

    struct Message: Equatable {
        let id: Int
        let text: String
        let actionLabel: String?
        let duration: Double
        let action: (() -> Void)?
        static func == (a: Message, b: Message) -> Bool { a.id == b.id }
    }

    private(set) var current: Message?
    private var sequence = 0

    func show(_ text: String, duration: Double = 2.4) {
        sequence += 1
        current = Message(id: sequence, text: text, actionLabel: nil, duration: duration, action: nil)
    }

    func showAction(_ text: String, _ actionLabel: String, duration: Double = 4.5, action: @escaping () -> Void) {
        sequence += 1
        current = Message(id: sequence, text: text, actionLabel: actionLabel, duration: duration, action: action)
    }

    func dismiss(_ id: Int) {
        if current?.id == id { current = nil }
    }
}

/// RealToastHost: slides in under the status bar; tap or swipe up to dismiss.
struct RealToastHost: View {
    @State private var toast = RealToast.shared
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack {
            if let m = toast.current {
                HStack(spacing: 14) {
                    Text(m.text)
                        .font(RealFont.inter(14, .medium))
                        .foregroundStyle(Color(argb: 0xFFF3F3F5))
                    if let label = m.actionLabel, let action = m.action {
                        Button {
                            toast.dismiss(m.id)
                            action()
                        } label: {
                            Text(label).font(RealFont.inter(14, .semibold)).foregroundStyle(Color(argb: 0xFF91B9FF))
                                .padding(.horizontal, 8).padding(.vertical, 4)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 18).padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Color(argb: 0xF2161618)))
                .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.white.opacity(0.10), lineWidth: 0.7))
                .shadow(color: .black.opacity(0.5), radius: 18, y: 6)
                .frame(maxWidth: 380)
                .padding(.horizontal, 24)
                .padding(.top, 8)
                .onTapGesture { toast.dismiss(m.id) }
                .gesture(DragGesture(minimumDistance: 8).onEnded { if $0.translation.height < -12 { toast.dismiss(m.id) } })
                .transition(reduceMotion ? .opacity : .move(edge: .top).combined(with: .opacity).combined(with: .scale(scale: 0.9)))
                .id(m.id)
                .task(id: m.id) {
                    try? await Task.sleep(for: .seconds(m.duration))
                    toast.dismiss(m.id)
                }
                .accessibilityAddTraits(.isStaticText)
            }
            Spacer(minLength: 0)
        }
        .animation(reduceMotion ? .easeOut(duration: 0.12) : .spring(response: 0.38, dampingFraction: 0.78), value: toast.current)
    }
}
