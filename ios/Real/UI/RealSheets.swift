import MapKit
import SwiftUI

/// ReminderLocationUi.kt MapAppearance: the look of the card map and the location picker, chosen in Settings.
/// Açık is Apple's light map, Koyu its dark one; both name the places unless Sade is on.
enum MapAppearance {
    static let darkKey = "map_dark"
    static let plainKey = "map_plain"

    static var isDark: Bool { UserDefaults.standard.bool(forKey: darkKey) }
    static var isPlain: Bool { UserDefaults.standard.bool(forKey: plainKey) }

    static func scheme(dark: Bool) -> ColorScheme { dark ? .dark : .light }
    static func style(plain: Bool) -> MapStyle { .standard(pointsOfInterest: plain ? .excludingAll : .all) }
}

/// ChoiceSheet.kt Choice: one option, its label and an optional grey line under it.
struct SheetChoice<T: Hashable>: Identifiable {
    let value: T
    let label: String
    var detail: String? = nil
    var id: T { value }
}

/// ChoiceSheet.kt ChoiceToggle: an on/off extra under the options; flipping it keeps the sheet open.
struct SheetToggle {
    let label: String
    let detail: String?
    let isOn: Binding<Bool>
}

/// ChoiceSheet.kt ChoiceSheet: the permission sheet's surface (no hero), the options as one inset list with a blue
/// check on the current one, Kapat under it. Picking closes the sheet after the choice is shown.
struct ChoiceSheet<T: Hashable>: View {
    let title: String
    let choices: [SheetChoice<T>]
    let selected: T
    let onSelect: (T) -> Void
    let onDismiss: () -> Void
    var toggle: SheetToggle? = nil

    var body: some View {
        RealSheetSurface(onDismiss: onDismiss, title: title, description: "") { entered, close in
            Color.clear.frame(height: 22)
            VStack(spacing: 0) {
                ForEach(Array(choices.enumerated()), id: \.element.id) { index, choice in
                    if index > 0 { Rectangle().fill(.white.opacity(0.09)).frame(height: 0.5).padding(.leading, 18) }
                    let picked = choice.value == selected
                    Button {
                        if !picked { onSelect(choice.value) }
                        close {}
                    } label: {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(choice.label).font(RealFont.inter(16)).foregroundStyle(Color(argb: 0xFFF3F1F4))
                                if let detail = choice.detail {
                                    Text(detail).font(RealFont.inter(12.5)).foregroundStyle(Color(argb: 0xFF9B9BA4))
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            if picked { ActionIcon(.check, color: Color(argb: 0xFF3B82F6), size: 18) }
                            else { Color.clear.frame(width: 18, height: 18) }
                        }
                        .padding(.horizontal, 18).padding(.vertical, 15)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(.white.opacity(0.06)))
            .sheetStagger(entered, 0.15)
            if let toggle {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(toggle.label).font(RealFont.inter(16)).foregroundStyle(Color(argb: 0xFFF3F1F4))
                        if let detail = toggle.detail {
                            Text(detail).font(RealFont.inter(12.5)).foregroundStyle(Color(argb: 0xFF9B9BA4))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Toggle("", isOn: toggle.isOn).labelsHidden().tint(Color(argb: 0xFF3B82F6))
                }
                .padding(.horizontal, 18).padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(.white.opacity(0.06)))
                .padding(.top, 10)
                .sheetStagger(entered, 0.22)
            }
            Color.clear.frame(height: 14)
            Button { close {} } label: {
                Text(t("Kapat")).font(RealFont.inter(14, .semibold)).foregroundStyle(Color(argb: 0xFF9B9BA4))
                    .frame(maxWidth: .infinity).frame(height: 46).contentShape(Capsule())
            }
            .buttonStyle(PressScaleStyle(scale: 0.97)).sheetStagger(entered, 0.28)
        }
    }
}

/// ChoiceSheet.kt ConfirmSheet: the question, what happens, a white pill with the red action and Vazgeç under it.
struct ConfirmSheet: View {
    let title: String
    let message: String
    let confirmLabel: String
    let onDismiss: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        RealSheetSurface(onDismiss: onDismiss, title: title, description: message) { entered, close in
            Color.clear.frame(height: 30)
            Button { close(onConfirm) } label: {
                Text(confirmLabel).font(RealFont.inter(15, .semibold)).foregroundStyle(Color(argb: 0xFFD93025))
                    .frame(maxWidth: .infinity).frame(height: 54)
                    .background(Capsule().fill(.white))
            }
            .buttonStyle(PressScaleStyle(scale: 0.97)).sheetStagger(entered, 0.22)
            Color.clear.frame(height: 6)
            Button { close {} } label: {
                Text(t("Vazgeç")).font(RealFont.inter(14, .semibold)).foregroundStyle(Color(argb: 0xFF9B9BA4))
                    .frame(maxWidth: .infinity).frame(height: 46).contentShape(Capsule())
            }
            .buttonStyle(PressScaleStyle(scale: 0.97)).sheetStagger(entered, 0.28)
        }
    }
}

/// PermissionSheet's scrim and glass surface (grabber, 28pt centred title, body) around the sheet's own content.
/// [content] gets `close(then)`, which slides the sheet away, dismisses, then runs `then`.
private struct RealSheetSurface<Content: View>: View {
    let onDismiss: () -> Void
    let title: String
    let description: String
    @ViewBuilder let content: (_ entered: Bool, _ close: @escaping (@escaping () -> Void) -> Void) -> Content

    @State private var shown = false
    @State private var entered = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(shown ? 0.68 : 0).ignoresSafeArea()
                .onTapGesture { close {} }
            VStack(spacing: 0) {
                Capsule().fill(.white.opacity(0.22)).frame(width: 38, height: 4)
                Color.clear.frame(height: 30)
                Text(title).font(RealFont.inter(28, .medium)).kerning(-0.9).foregroundStyle(Color(argb: 0xFFF3F1F4))
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity).sheetStagger(entered, 0.08)
                if !description.isEmpty {
                    Color.clear.frame(height: 12)
                    Text(description).font(RealFont.inter(16)).foregroundStyle(Color(argb: 0xFF9B9BA4)).lineSpacing(4)
                        .multilineTextAlignment(.center).padding(.horizontal, 6).frame(maxWidth: .infinity).sheetStagger(entered, 0.15)
                }
                content(entered, close)
                Color.clear.frame(height: 8)
            }
            .padding(.horizontal, 24).padding(.top, 12)
            .background {
                UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous)
                    .fill(LiquidGlassTokens.contentColor)
                    .overlay {
                        GeometryReader { g in
                            RadialGradient(colors: [.white.opacity(0.07), .clear], center: .top, startRadius: 0, endRadius: g.size.width * 0.85)
                        }
                        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous))
                    }
                    .overlay(UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous)
                        .strokeBorder(LinearGradient(colors: [LiquidGlassTokens.contentEdgeTop, LiquidGlassTokens.contentEdgeBottom], startPoint: .top, endPoint: .bottom), lineWidth: 0.75))
                    .ignoresSafeArea(edges: .bottom)
            }
            .offset(y: shown ? 0 : 640)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.3)) { shown = true }
            entered = true
        }
    }

    private func close(_ then: @escaping () -> Void) {
        withAnimation(.timingCurve(0.4, 0, 1, 1, duration: 0.3)) { shown = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { onDismiss(); then() }
    }
}

private extension View {
    /// staggerIn: fade + 18pt rise, 440ms, per-element delay.
    func sheetStagger(_ entered: Bool, _ delay: Double) -> some View {
        opacity(entered ? 1 : 0)
            .offset(y: entered ? 0 : 18)
            .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.44).delay(delay), value: entered)
    }
}

/// SettingsScreen.kt SettingsChoiceRow: a setting with a few values, the current one in grey on the right.
struct SettingsChoiceRow: View {
    let icon: ActionGlyph
    let title: String
    let value: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                SettingsIconTile(icon: icon)
                Text(title).font(RealFont.inter(16)).kerning(-0.2).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(value).font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFF9A9AA4)).lineLimit(1)
                    .padding(.leading, 8).padding(.trailing, 6)
                ActionIcon(.chevronRight, color: Color(argb: 0xFF75757F), size: 16)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
