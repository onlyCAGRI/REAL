import SwiftUI
import UIKit

/// Runs a state change without the default sheet / cover animation, so overlays animate themselves.
func withoutAnimation(_ change: () -> Void) {
    var t = Transaction()
    t.disablesAnimations = true
    withTransaction(t, change)
}

extension View {
    /// Floating glass overlay (DesignPanel liquidSheet): the presented view draws its own scrim and bottom panel.
    /// Toggle `isPresented` through `withoutAnimation` so only the panel's own motion plays.
    func realOverlay<Content: View>(isPresented: Binding<Bool>, @ViewBuilder content: @escaping () -> Content) -> some View {
        fullScreenCover(isPresented: isPresented) { content().presentationBackground(.clear) }
    }
}

/// Scrim + bottom glass panel with the Android slide/fade, shared by the picker overlays.
struct OverlayPanel<Content: View>: View {
    @Environment(\.dismiss) private var dismiss
    var corners: CGFloat = 32
    var onScrimTap: (() -> Bool)? = nil
    @ViewBuilder let content: (_ close: @escaping () -> Void) -> Content
    @State private var appeared = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(appeared ? 0.38 : 0).ignoresSafeArea()
                .onTapGesture { if onScrimTap?() != true { close() } }
            content(close)
                .frame(maxWidth: .infinity)
                .liquidGlass(RoundedRectangle(cornerRadius: corners, style: .continuous), role: .sheet)
                .padding(.horizontal, 10)
                .padding(.bottom, 8)
                .offset(y: appeared ? 0 : 700)
        }
        .onAppear { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.34)) { appeared = true } }
    }

    private func close() {
        withAnimation(.timingCurve(0.4, 0, 1, 1, duration: 0.2)) { appeared = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { withoutAnimation { dismiss() } }
    }
}

/// DateTimePickerOverlay (DateTimePicker.kt): "weekday · dd/MM · HH:mm" summary, Tarih / Saat segmented control,
/// snapping wheels, "Yıl gir · Tekrarlama · Erken uyarı" and İptal | Bitti. A time is always chosen, like Android.
struct ScheduleSheet: View {
    @Binding var draft: ScheduleDraft

    @State private var day = 1
    @State private var month = 1
    @State private var year = 2026
    @State private var hour = 9
    @State private var minute = 0
    @State private var includeYear = false
    @State private var timeTab = false
    @State private var repeatRule: RecurrenceRule = .none
    @State private var showRepeat = false
    @State private var error: String?

    private let calendar = Calendar.real
    private static let months = ["OCA", "ŞUB", "MAR", "NİS", "MAY", "HAZ", "TEM", "AĞU", "EYL", "EKİ", "KAS", "ARA"]

    private var currentYear: Int { calendar.component(.year, from: Date()) }

    /// Without "Yıl gir" the year is inferred: this year if the moment is still ahead, otherwise next year.
    private var effectiveYear: Int {
        if includeYear { return year }
        return (makeDate(year: currentYear).map { $0 > Date() } ?? true) ? currentYear : currentYear + 1
    }

    private func makeDate(year: Int) -> Date? {
        guard let first = calendar.date(from: DateComponents(year: year, month: month, day: 1)) else { return nil }
        let length = calendar.range(of: .day, in: .month, for: first)?.count ?? 28
        return calendar.date(from: DateComponents(year: year, month: month, day: min(day, length), hour: hour, minute: minute))
    }

    private var daysInMonth: Int {
        let first = calendar.date(from: DateComponents(year: effectiveYear, month: month, day: 1))!
        return calendar.range(of: .day, in: .month, for: first)?.count ?? 31
    }

    var body: some View {
        OverlayPanel(onScrimTap: { if showRepeat { showRepeat = false; return true }; return false }) { close in
            Group {
                if showRepeat {
                    repeatScreen.transition(.asymmetric(insertion: .opacity.combined(with: .offset(x: 30)),
                                                        removal: .opacity.combined(with: .offset(x: 30))))
                } else {
                    mainScreen(close).transition(.asymmetric(insertion: .opacity.combined(with: .offset(x: -30)),
                                                             removal: .opacity.combined(with: .offset(x: -30))))
                }
            }
            .padding(20)
            .animation(.timingCurve(0.2, 0, 0, 1, duration: 0.22), value: showRepeat)
        }
        .onAppear(perform: load)
    }

    private func load() {
        let base = Date().addingTimeInterval(3600)
        let fallback = calendar.date(bySettingHour: calendar.component(.hour, from: base), minute: 0, second: 0, of: base)!
        let seed: Date = draft.date.map { draft.hasTime ? $0 : calendar.date(bySettingHour: 9, minute: 0, second: 0, of: $0)! } ?? fallback
        let c = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: seed)
        day = c.day ?? 1; month = c.month ?? 1; year = c.year ?? currentYear
        hour = c.hour ?? 9; minute = c.minute ?? 0
        includeYear = (c.year ?? currentYear) > currentYear + 1
        repeatRule = draft.repeatRule
    }

    private var summary: String {
        let date = makeDate(year: effectiveYear) ?? Date()
        let dateText = includeYear ? String(format: "%02d/%02d/%d", day, month, effectiveYear) : String(format: "%02d/%02d", day, month)
        return "\(calendar.isoWeekday(of: date).full) · \(dateText) · " + String(format: "%02d:%02d", hour, minute)
    }

    private func mainScreen(_ close: @escaping () -> Void) -> some View {
        VStack(spacing: 0) {
            Capsule().fill(Color(argb: 0xFF666669)).frame(width: 34, height: 3)
            HStack(spacing: 12) {
                Text(summary).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFD9D9DE)).lineLimit(1)
                if repeatRule != .none {
                    Button { showRepeat = true } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "repeat").font(.system(size: 11)).foregroundStyle(RealColors.accentBlue)
                            Text(Recurrence.description(repeatRule, alert: makeDate(year: effectiveYear)?.epochMillis))
                                .font(RealFont.inter(11, .medium)).foregroundStyle(Color(argb: 0xFFD2D2D8)).lineLimit(1)
                        }
                    }
                    .transition(.opacity.combined(with: .offset(x: 20)))
                }
            }
            .padding(.vertical, 18)
            .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.22), value: repeatRule)
            LiquidSegmentedControl(labels: ["Tarih", "Saat"], selected: Binding(get: { timeTab ? 1 : 0 }, set: { timeTab = $0 == 1 }))
            ZStack {
                if timeTab {
                    HStack(spacing: 4) {
                        NumberWheel(values: Array(0...23), value: $hour, format: { String(format: "%02d", $0) })
                        Text(":").font(RealFont.inter(30)).foregroundStyle(.white)
                        NumberWheel(values: Array(0...59), value: $minute, format: { String(format: "%02d", $0) })
                    }
                    .transition(.opacity.combined(with: .offset(x: 12)))
                } else {
                    HStack(spacing: 4) {
                        NumberWheel(values: Array(1...daysInMonth), value: $day, format: { "\($0)" })
                        NumberWheel(values: Array(1...12), value: $month, format: { Self.months[$0 - 1] })
                        if includeYear {
                            NumberWheel(values: Array(currentYear...(currentYear + 30)), value: $year, format: { "\($0)" }, fontSize: 26)
                                .transition(.opacity.combined(with: .scale(scale: 0.9)))
                        }
                    }
                    .transition(.opacity.combined(with: .offset(x: -12)))
                }
            }
            .padding(.top, 18)
            .animation(.timingCurve(0.2, 0, 0, 1, duration: 0.18), value: timeTab)
            .animation(.easeOut(duration: 0.2), value: includeYear)
            .onChange(of: month) { _, _ in day = min(day, daysInMonth) }
            HStack(spacing: 0) {
                optionButton("Yıl gir", selected: includeYear, symbol: includeYear ? "checkmark" : nil, filled: true) {
                    includeYear.toggle()
                    if includeYear { year = effectiveYear }
                }
                optionButton("Tekrarlama", selected: repeatRule != .none, symbol: "repeat", filled: false) { showRepeat = true }
                optionButton("Erken uyarı", selected: false, symbol: nil, filled: false, enabled: false) {}
                    .accessibilityLabel("Erken uyarı, yakında")
            }
            .padding(.top, 12)
            if let error {
                Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFA4AA)).padding(.top, 10)
            }
            HStack(spacing: 0) {
                Button(action: close) {
                    Text("İptal").font(RealFont.inter(18)).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 48)
                }
                Rectangle().fill(Color(argb: 0xFF555559)).frame(width: 1, height: 22)
                Button { confirm(close) } label: {
                    Text("Bitti").font(RealFont.inter(18)).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 48)
                }
            }
            .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous), role: .chrome)
            .padding(.top, 12)
            if draft.date != nil {
                Button("Tarih ve saati kaldır") { draft = ScheduleDraft(); close() }
                    .font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFA4AA))
                    .padding(.top, 12)
            }
        }
    }

    private func optionButton(_ title: String, selected: Bool, symbol: String?, filled: Bool, enabled: Bool = true,
                              action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                ZStack {
                    if filled && selected { Circle().fill(Color(argb: 0xFF6EA8FF)) }
                    else { Circle().strokeBorder(selected ? RealColors.accentBlue : Color(argb: 0xFF777780), lineWidth: 1.2) }
                    if let symbol {
                        Image(systemName: symbol).font(.system(size: filled ? 10 : 9, weight: .bold))
                            .foregroundStyle(filled ? Color(argb: 0xFF111216) : (selected ? RealColors.accentBlue : Color(argb: 0xFF9A9AA3)))
                    }
                }
                .frame(width: 20, height: 20)
                Text(title).font(RealFont.inter(enabled ? 11 : 10.5, .medium))
                    .foregroundStyle(enabled ? (selected && !filled ? Color(argb: 0xFFDCE7FA) : Color(argb: 0xFFE1E1E6)) : Color(argb: 0xFFA7A7AF))
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 44)
        }
        .disabled(!enabled)
    }

    private var repeatScreen: some View {
        let alert = makeDate(year: effectiveYear)?.epochMillis
        let options: [(RecurrenceRule, String)] = [
            (.none, "Yok"), (.daily, "Her gün"),
            (.weekly, Recurrence.description(.weekly, alert: alert)),
            (.monthly, Recurrence.description(.monthly, alert: alert)),
            (.yearly, Recurrence.description(.yearly, alert: alert)),
        ]
        return VStack(spacing: 0) {
            Capsule().fill(Color(argb: 0xFF666669)).frame(width: 34, height: 3)
            HStack(spacing: 8) {
                Button { showRepeat = false } label: {
                    Image(systemName: "chevron.left").font(.system(size: 17, weight: .medium)).foregroundStyle(.white)
                        .frame(width: 40, height: 40).liquidGlass(Circle(), role: .chrome)
                }
                .accessibilityLabel("Geri dön")
                Text("Tekrarlama").font(RealFont.inter(18, .semibold)).foregroundStyle(.white)
                Spacer()
            }
            .padding(.top, 12).padding(.bottom, 16)
            VStack(spacing: 0) {
                ForEach(Array(options.enumerated()), id: \.offset) { index, option in
                    Button {
                        repeatRule = option.0
                        showRepeat = false
                    } label: {
                        HStack {
                            Text(option.1).font(RealFont.inter(16, repeatRule == option.0 ? .semibold : .regular))
                                .foregroundStyle(repeatRule == option.0 ? Color(argb: 0xFF82B3FF) : .white)
                            Spacer()
                            if repeatRule == option.0 {
                                Image(systemName: "checkmark").font(.system(size: 14, weight: .semibold)).foregroundStyle(Color(argb: 0xFF82B3FF))
                            }
                        }
                        .padding(.horizontal, 18).padding(.vertical, 15)
                        .contentShape(Rectangle())
                    }
                    if index < options.count - 1 { Rectangle().fill(.white.opacity(0.07)).frame(height: 1).padding(.horizontal, 16) }
                }
            }
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(LiquidGlassTokens.contentColor))
            Button("Kapat") { showRepeat = false }
                .font(RealFont.inter(16)).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 44).padding(.top, 24)
        }
    }

    private func confirm(_ close: () -> Void) {
        guard let at = makeDate(year: effectiveYear) else { error = "Bu saat, saat değişimi nedeniyle kullanılamıyor."; return }
        guard at > Date() else { error = "Gelecekte bir tarih ve saat seç."; return }
        var result = draft
        result.date = at
        result.hasTime = true
        result.repeatRule = repeatRule
        if repeatRule != .weekly { result.weekdaysMask = 0 }
        if repeatRule == .none { result.until = nil }
        draft = result
        close()
    }
}

/// Snapping 3-row wheel (NumberWheel): centred value bold, white and slightly larger; others grey.
struct NumberWheel: View {
    let values: [Int]
    @Binding var value: Int
    let format: (Int) -> String
    var fontSize: CGFloat = 28
    @State private var position: Int?
    private let row: CGFloat = 54

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            LazyVStack(spacing: 0) {
                ForEach(values, id: \.self) { item in
                    let centred = item == (position ?? value)
                    Text(format(item))
                        .font(RealFont.inter(fontSize, centred ? .bold : .regular))
                        .foregroundStyle(centred ? Color.white : Color(argb: 0xFF8E8E93))
                        .scaleEffect(centred ? 1.04 : 0.96)
                        .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.14), value: centred)
                        .frame(maxWidth: .infinity)
                        .frame(height: row)
                        .contentShape(Rectangle())
                        .onTapGesture { withAnimation(.spring(duration: 0.3)) { position = item } }
                        .id(item)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.viewAligned)
        .scrollPosition(id: $position, anchor: .center)
        .safeAreaPadding(.vertical, row)
        .frame(height: row * 3)
        .background {
            // liquidWheelWindow: the glass window behind the centred row.
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(LiquidGlassTokens.contentColor)
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(.white.opacity(0.08), lineWidth: 0.7))
                .frame(height: row)
        }
        .onAppear { position = value }
        .onChange(of: position) { _, p in
            guard let p, p != value else { return }
            UISelectionFeedbackGenerator().selectionChanged()
            value = p
        }
        .onChange(of: value) { _, v in if position != v { position = v } }
        .onChange(of: values) { _, vs in if !vs.contains(value), let last = vs.last { value = last } }
    }
}

/// LiquidSegmentedControl: glass track with a sliding dark pill.
struct LiquidSegmentedControl: View {
    let labels: [String]
    @Binding var selected: Int
    @Namespace private var ns

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                Button { withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { selected = index } } label: {
                    Text(label).font(RealFont.inter(14, selected == index ? .semibold : .regular))
                        .foregroundStyle(selected == index ? .white : Color(argb: 0xFFB8B8BE))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background {
                            if selected == index {
                                Capsule().fill(LiquidGlassTokens.contentColor)
                                    .overlay(Capsule().strokeBorder(.white.opacity(0.18), lineWidth: 0.8))
                                    .matchedGeometryEffect(id: "pill", in: ns)
                            }
                        }
                        .contentShape(Rectangle())
                }
            }
        }
        .padding(4)
        .frame(height: 48)
        .liquidGlass(Capsule(), role: .chrome)
    }
}
