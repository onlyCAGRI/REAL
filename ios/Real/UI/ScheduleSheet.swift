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

/// DateTimePickerOverlay (DateTimePicker.kt): "Başlangıç → Bitiş" summary (tap a side to set it with the same
/// wheels; long-press Bitiş to remove it), Tarih / Saat segmented control, snapping wheels,
/// "Yıl gir · Tekrarlama · Erken uyarı" and İptal | Bitti. A time is always chosen, like Android.
struct ScheduleSheet: View {
    @Binding var draft: ScheduleDraft

    @State private var day = 1
    @State private var month = 1
    @State private var year = 2026
    @State private var hour = 9
    @State private var minute = 0
    @State private var includeYear = false
    var initialTimeTab = false
    /// Offer "Erken uyarı" and "Bitiş" (off for pickers that only choose a time, like an end time).
    var showTiming = true
    /// When set, Saat can be pushed right to "Alarm"; written on "Bitti".
    var alarm: Binding<Bool>? = nil
    @State private var alarmOn = false
    @State private var alarmHint = false
    @State private var alarmNudged = false
    @State private var timeTab = false
    @State private var repeatRule: RecurrenceRule = .none
    @State private var showRepeat = false
    @State private var showEarly = false
    @State private var earlyMinutes: Int?
    // Bitiş, edited on the same wheels.
    @State private var hasEnd = false
    @State private var editingEnd = false
    @State private var endDay = 1
    @State private var endMonth = 1
    @State private var endHour = 10
    @State private var endMinute = 0
    @State private var error: String?

    private let calendar = Calendar.real
    private static var months: [String] { [t("OCA"), t("ŞUB"), t("MAR"), t("NİS"), t("MAY"), t("HAZ"), t("TEM"), t("AĞU"), t("EYL"), t("EKİ"), t("KAS"), t("ARA")] }

    private var currentYear: Int { calendar.component(.year, from: Date()) }

    /// Without "Yıl gir" the year is inferred from the day alone: this year from today on, next year for a day already
    /// gone. Today with a clock time already past stays today (confirm says so) — judging by the moment flipped to next
    /// year while the date wheels still held the default 09:00.
    private var effectiveYear: Int {
        if includeYear { return year }
        guard let day = makeDate(year: currentYear) else { return currentYear }
        return calendar.startOfDay(for: day) >= calendar.startOfDay(for: Date()) ? currentYear : currentYear + 1
    }

    private func makeDate(year: Int) -> Date? {
        guard let first = calendar.date(from: DateComponents(year: year, month: month, day: 1)) else { return nil }
        let length = calendar.range(of: .day, in: .month, for: first)?.count ?? 28
        return calendar.date(from: DateComponents(year: year, month: month, day: min(day, length), hour: hour, minute: minute))
    }

    /// The end's year: the first such date on or after the start's day (a December start can end in January).
    private var endDate: Date? {
        guard hasEnd, let start = makeDate(year: effectiveYear) else { return nil }
        let startYear = calendar.component(.year, from: start)
        func build(_ y: Int) -> Date? {
            guard let first = calendar.date(from: DateComponents(year: y, month: endMonth, day: 1)) else { return nil }
            let length = calendar.range(of: .day, in: .month, for: first)?.count ?? 28
            return calendar.date(from: DateComponents(year: y, month: endMonth, day: min(endDay, length), hour: endHour, minute: endMinute))
        }
        guard let same = build(startYear) else { return nil }
        return calendar.startOfDay(for: same) < calendar.startOfDay(for: start) ? build(startYear + 1) : same
    }

    private var daysInMonth: Int {
        let first = calendar.date(from: DateComponents(year: effectiveYear, month: editingEnd ? endMonth : month, day: 1))!
        return calendar.range(of: .day, in: .month, for: first)?.count ?? 31
    }

    var body: some View {
        OverlayPanel(onScrimTap: {
            if showRepeat || showEarly { showRepeat = false; showEarly = false; return true }
            return false
        }) { close in
            Group {
                if showRepeat {
                    repeatScreen.transition(.asymmetric(insertion: .opacity.combined(with: .offset(x: 30)),
                                                        removal: .opacity.combined(with: .offset(x: 30))))
                } else if showEarly {
                    EarlyAlertScreen(selected: $earlyMinutes) { showEarly = false }
                        .transition(.asymmetric(insertion: .opacity.combined(with: .offset(x: 30)),
                                                removal: .opacity.combined(with: .offset(x: 30))))
                } else {
                    mainScreen(close).transition(.asymmetric(insertion: .opacity.combined(with: .offset(x: -30)),
                                                             removal: .opacity.combined(with: .offset(x: -30))))
                }
            }
            .padding(20)
            .animation(.timingCurve(0.2, 0, 0, 1, duration: 0.22), value: showRepeat)
            .animation(.timingCurve(0.2, 0, 0, 1, duration: 0.22), value: showEarly)
        }
        .onAppear { load(); if initialTimeTab { timeTab = true }; maybeNudgeAlarm() }
        .onChange(of: timeTab) { _, _ in maybeNudgeAlarm() }
    }

    /// First three sheets that show Saat: the thumb is pushed once towards the alarm with a short hint.
    private func maybeNudgeAlarm() {
        guard alarm != nil, timeTab, !alarmOn, !alarmNudged else { return }
        alarmNudged = true
        let seen = UserDefaults.standard.integer(forKey: "realAlarmNudge")
        guard seen < 3 else { return }
        UserDefaults.standard.set(seen + 1, forKey: "realAlarmNudge")
        withAnimation(.easeOut(duration: 0.26)) { alarmHint = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.2) { withAnimation(.easeOut(duration: 0.36)) { alarmHint = false } }
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
        earlyMinutes = draft.earlyAlertMinutes
        hasEnd = draft.endAt != nil
        seedEnd(draft.endAt ?? seed.addingTimeInterval(3600))
        alarmOn = alarm?.wrappedValue ?? false
    }

    private func seedEnd(_ date: Date) {
        let e = calendar.dateComponents([.month, .day, .hour, .minute], from: date)
        endDay = e.day ?? 1; endMonth = e.month ?? 1; endHour = e.hour ?? 10; endMinute = e.minute ?? 0
    }

    private var startText: String {
        let date = makeDate(year: effectiveYear) ?? Date()
        let time = String(format: "%02d:%02d", hour, minute)
        if hasEnd { return "\(day) \(Self.months[month - 1].capitalized(with: I18n.locale)) · " + time }
        let dateText = includeYear ? String(format: "%02d/%02d/%d", day, month, effectiveYear) : String(format: "%02d/%02d", day, month)
        return "\(t(calendar.isoWeekday(of: date).full)) · \(dateText) · " + time
    }
    private var endText: String {
        guard hasEnd else { return t("Bitiş") }
        return "\(endDay) \(Self.months[endMonth - 1].capitalized(with: I18n.locale)) · " + String(format: "%02d:%02d", endHour, endMinute)
    }

    // Wheels follow the side being edited.
    private var wDay: Binding<Int> { editingEnd ? $endDay : $day }
    private var wMonth: Binding<Int> { editingEnd ? $endMonth : $month }
    private var wHour: Binding<Int> { editingEnd ? $endHour : $hour }
    private var wMinute: Binding<Int> { editingEnd ? $endMinute : $minute }

    private func mainScreen(_ close: @escaping () -> Void) -> some View {
        VStack(spacing: 0) {
            Capsule().fill(Color(argb: 0xFF666669)).frame(width: 34, height: 3)
            HStack(spacing: 4) {
                // An alarm warms the start time a little, in the glow's colour.
                Text(startText).font(RealFont.inter(13, !editingEnd && hasEnd ? .semibold : .medium))
                    .foregroundStyle(editingEnd ? Color(argb: 0xFFD9D9DE).opacity(0.5) : (alarmOn ? Color(argb: 0xFFFFCFBA) : Color(argb: 0xFFF2F2F5))).lineLimit(1)
                    .animation(.easeOut(duration: 0.32), value: alarmOn)
                    .padding(.horizontal, 4).padding(.vertical, 4).contentShape(Rectangle())
                    .onTapGesture { editingEnd = false }
                if showTiming {
                    Text("→").font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF8E8E93))
                    Text(endText).font(RealFont.inter(13, editingEnd ? .semibold : .medium))
                        .foregroundStyle(editingEnd ? Color(argb: 0xFFF2F2F5) : (hasEnd ? Color(argb: 0xFFD9D9DE).opacity(0.5) : Color(argb: 0xFF8E8E93)))
                        .lineLimit(1)
                        .padding(.horizontal, 4).padding(.vertical, 4).contentShape(Rectangle())
                        .onTapGesture {
                            if !hasEnd { seedEnd((makeDate(year: effectiveYear) ?? Date()).addingTimeInterval(3600)); hasEnd = true }
                            editingEnd = true; error = nil
                        }
                        .onLongPressGesture {
                            // Long press removes the end.
                            if hasEnd { hasEnd = false; editingEnd = false; UISelectionFeedbackGenerator().selectionChanged() }
                        }
                        .accessibilityLabel(hasEnd ? t("Bitiş {0}", endText) : t("Bitiş ekle"))
                        .accessibilityAction(named: t("Bitişi kaldır")) { hasEnd = false; editingEnd = false }
                }
            }
            .padding(.vertical, 18)
            if alarm != nil {
                DateTimeAlarmSegment(timeTab: $timeTab, alarm: Binding(get: { alarmOn }, set: { on in
                    withAnimation(.easeOut(duration: 0.3)) { alarmOn = on; if on { alarmHint = false } }
                }), nudge: alarmHint)
            } else {
                LiquidSegmentedControl(labels: [t("Tarih"), t("Saat")], selected: Binding(get: { timeTab ? 1 : 0 }, set: { timeTab = $0 == 1 }))
            }
            // The hint sits in the gap under the control, so nothing below moves.
            Text(t("Alarm için Saat'i sağa it")).font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFF9A9AA3))
                .lineLimit(1).opacity(alarmHint ? 1 : 0)
                .frame(height: 18)
            ZStack {
                if timeTab {
                    HStack(spacing: 4) {
                        NumberWheel(values: Array(0...23), value: wHour, format: { String(format: "%02d", $0) })
                        Text(":").font(RealFont.inter(30)).foregroundStyle(.white)
                        NumberWheel(values: Array(0...59), value: wMinute, format: { String(format: "%02d", $0) })
                    }
                    .transition(.opacity.combined(with: .offset(x: 12)))
                } else {
                    HStack(spacing: 4) {
                        NumberWheel(values: Array(1...daysInMonth), value: wDay, format: { "\($0)" })
                        NumberWheel(values: Array(1...12), value: wMonth, format: { Self.months[$0 - 1] })
                        if includeYear && !editingEnd {
                            // "Yıl gir" grows the year column in from the right (and back out) instead of popping it.
                            NumberWheel(values: Array(currentYear...(currentYear + 30)), value: $year, format: { "\($0)" }, fontSize: 26)
                                .transition(.asymmetric(insertion: .opacity.combined(with: .move(edge: .trailing)).combined(with: .scale(scale: 0.85, anchor: .leading)),
                                                        removal: .opacity.combined(with: .scale(scale: 0.85, anchor: .leading))))
                        }
                    }
                    .transition(.opacity.combined(with: .offset(x: -12)))
                }
            }
            .id(editingEnd) // fresh wheels when switching between start and end
            .animation(.timingCurve(0.2, 0, 0, 1, duration: 0.18), value: timeTab)
            .animation(.timingCurve(0.2, 0, 0, 1, duration: 0.32), value: includeYear)
            .onChange(of: month) { _, _ in day = min(day, daysInMonth) }
            .onChange(of: endMonth) { _, _ in endDay = min(endDay, daysInMonth) }
            HStack(spacing: 0) {
                optionButton(t("Yıl gir"), selected: includeYear, symbol: includeYear ? "checkmark" : nil, filled: true) {
                    includeYear.toggle()
                    if includeYear { year = effectiveYear }
                }
                optionButton(t("Tekrarlama"), selected: repeatRule != .none, symbol: "repeat", filled: false) { showRepeat = true }
                optionButton(earlyMinutes.map { t("{0} önce", EarlyAlertScreen.label($0)) } ?? t("Erken uyarı"),
                             selected: earlyMinutes != nil, symbol: "bell", filled: false, enabled: showTiming) { showEarly = true }
            }
            .padding(.top, 12)
            if let error {
                Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFA4AA)).padding(.top, 10)
            }
            HStack(spacing: 0) {
                Button(action: close) {
                    Text(t("İptal")).font(RealFont.inter(18)).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 48)
                }
                Rectangle().fill(Color(argb: 0xFF555559)).frame(width: 1, height: 22)
                Button { confirm(close) } label: {
                    Text(t("Bitti")).font(RealFont.inter(18)).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 48)
                }
            }
            .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous), role: .chrome)
            .padding(.top, 12)
            if draft.date != nil {
                Button(t("Tarih ve saati kaldır")) { draft = ScheduleDraft(); alarm?.wrappedValue = false; close() }
                    .font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFA4AA))
                    .padding(.top, 12)
            }
        }
        .background(AlarmBreathGlow(active: alarm != nil && alarmOn).padding(-20))
    }

    /// Selected options read white, like the rest of the sheet (no blue).
    private func optionButton(_ title: String, selected: Bool, symbol: String?, filled: Bool, enabled: Bool = true,
                              action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                ZStack {
                    if filled && selected { Circle().fill(Color.white) }
                    else { Circle().strokeBorder(selected ? Color.white : Color(argb: 0xFF777780), lineWidth: 1.2) }
                    if let symbol {
                        Image(systemName: symbol).font(.system(size: filled ? 10 : 9, weight: .bold))
                            .foregroundStyle(filled ? Color(argb: 0xFF111216) : (selected ? Color.white : Color(argb: 0xFF9A9AA3)))
                    }
                }
                .frame(width: 20, height: 20)
                Text(title).font(RealFont.inter(enabled ? 11 : 10.5, .medium))
                    .foregroundStyle(enabled ? (selected ? Color.white : Color(argb: 0xFFE1E1E6)) : Color(argb: 0xFFA7A7AF))
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 44)
        }
        .disabled(!enabled)
    }

    private var repeatScreen: some View {
        let alert = makeDate(year: effectiveYear)?.epochMillis
        let options: [(RecurrenceRule, String)] = [
            (.none, t("Yok")), (.daily, t("Her gün")),
            (.weekly, Recurrence.description(.weekly, alert: alert)),
            (.monthly, Recurrence.description(.monthly, alert: alert)),
            (.yearly, Recurrence.description(.yearly, alert: alert)),
        ]
        return VStack(spacing: 0) {
            Capsule().fill(Color(argb: 0xFF666669)).frame(width: 34, height: 3)
            SheetBackHeader(title: t("Tekrarlama")) { showRepeat = false }
            VStack(spacing: 0) {
                ForEach(Array(options.enumerated()), id: \.offset) { index, option in
                    Button {
                        repeatRule = option.0
                        showRepeat = false
                    } label: {
                        HStack {
                            Text(option.1).font(RealFont.inter(16, repeatRule == option.0 ? .semibold : .regular))
                                .foregroundStyle(repeatRule == option.0 ? Color.white : Color.white.opacity(0.72))
                            Spacer()
                        }
                        .padding(.horizontal, 18).padding(.vertical, 15)
                        .contentShape(Rectangle())
                    }
                    if index < options.count - 1 { Rectangle().fill(.white.opacity(0.07)).frame(height: 1).padding(.horizontal, 16) }
                }
            }
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(LiquidGlassTokens.contentColor))
            Button(t("Kapat")) { showRepeat = false }
                .font(RealFont.inter(16)).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 44).padding(.top, 24)
        }
    }

    private func confirm(_ close: () -> Void) {
        guard let at = makeDate(year: effectiveYear) else { error = t("Bu saat, saat değişimi nedeniyle kullanılamıyor."); return }
        guard at > Date() else { error = t("Gelecekte bir tarih ve saat seç."); return }
        if hasEnd {
            guard let end = endDate else { error = t("Bitiş saati, saat değişimi nedeniyle kullanılamıyor."); return }
            guard end > at else { error = t("Bitiş, başlangıçtan sonra olmalı."); return }
        }
        var result = draft
        result.date = at
        result.hasTime = true
        result.repeatRule = repeatRule
        if repeatRule != .weekly { result.weekdaysMask = 0 }
        if repeatRule == .none { result.until = nil }
        if showTiming {
            result.earlyAlertMinutes = earlyMinutes
            result.endAt = endDate
        }
        draft = result
        alarm?.wrappedValue = alarmOn
        close()
    }
}

/// Back button + title row of a picker sub-screen.
struct SheetBackHeader: View {
    let title: String
    let onBack: () -> Void
    var body: some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                RealSymbol("chevron.left", size: 19.5).foregroundStyle(.white)
                    .frame(width: 40, height: 40).liquidGlass(Circle(), role: .chrome)
            }
            .accessibilityLabel(t("Geri dön"))
            Text(title).font(RealFont.inter(18, .semibold)).foregroundStyle(.white)
            Spacer()
        }
        .padding(.top, 12).padding(.bottom, 16)
    }
}

/// Erken uyarı, laid out like the repeat list: Yok / 15 dakika önce / 1 saat önce / 1 gün önce. Tapping a row picks it,
/// its number grows and arrows appear; dragging the row (or tapping an arrow) rolls the number. Minutes go to 3 hours
/// in 5s, hours to 72, days to 30.
struct EarlyAlertScreen: View {
    @Binding var selected: Int?
    let onBack: () -> Void

    enum Unit: CaseIterable { case minutes, hours, days
        var factor: Int { switch self { case .minutes: 1; case .hours: 60; case .days: 1440 } }
        var word: String { switch self { case .minutes: t("dakika"); case .hours: t("saat"); case .days: t("gün") } }
        var range: ClosedRange<Int> { switch self { case .minutes: 5...180; case .hours: 1...72; case .days: 1...30 } }
        var step: Int { self == .minutes ? 5 : 1 }
        var initial: Int { self == .minutes ? 15 : 1 }
    }
    static func unit(of minutes: Int) -> Unit {
        if minutes >= 1440 && minutes % 1440 == 0 { return .days }
        if minutes >= 60 && minutes % 60 == 0 && minutes / 60 <= 72 { return .hours }
        return .minutes
    }
    /// "15 dk", "1 saat", "2 gün", "1 sa 30 dk".
    static func label(_ minutes: Int) -> String {
        if minutes >= 1440 && minutes % 1440 == 0 { return t("{0} gün", minutes / 1440) }
        if minutes >= 60 && minutes % 60 == 0 { return t("{0} saat", minutes / 60) }
        if minutes > 60 { return t("{0} sa {1} dk", minutes / 60, minutes % 60) }
        return t("{0} dk", minutes)
    }

    @State private var active: Unit?
    @State private var amounts: [Unit: Int] = [:]
    @State private var dragCarry: CGFloat = 0

    private func amount(_ u: Unit) -> Int { amounts[u] ?? u.initial }

    var body: some View {
        VStack(spacing: 0) {
            Capsule().fill(Color(argb: 0xFF666669)).frame(width: 34, height: 3)
            SheetBackHeader(title: t("Erken uyarı"), onBack: onBack)
            VStack(spacing: 0) {
                Button { active = nil; selected = nil; onBack() } label: {
                    HStack {
                        Text(t("Yok")).font(RealFont.inter(16, active == nil ? .semibold : .regular))
                            .foregroundStyle(active == nil ? Color.white : Color.white.opacity(0.72))
                        Spacer()
                    }
                    .padding(.horizontal, 18).padding(.vertical, 15).contentShape(Rectangle())
                }
                ForEach(Unit.allCases, id: \.self) { u in
                    Rectangle().fill(.white.opacity(0.07)).frame(height: 1).padding(.horizontal, 16)
                    row(u)
                }
            }
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(LiquidGlassTokens.contentColor))
            Button(t("Tamam"), action: onBack)
                .font(RealFont.inter(16)).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 44).padding(.top, 24)
        }
        .onAppear {
            if let s = selected {
                let u = Self.unit(of: s)
                active = u
                amounts[u] = min(max(s / u.factor, u.range.lowerBound), u.range.upperBound)
            }
        }
    }

    private func step(_ u: Unit, up: Bool) {
        let next = min(max(amount(u) + (up ? u.step : -u.step), u.range.lowerBound), u.range.upperBound)
        guard next != amount(u) else { return }
        withAnimation(.spring(response: 0.3, dampingFraction: 0.82)) { amounts[u] = next }
        selected = next * u.factor
        UISelectionFeedbackGenerator().selectionChanged()
    }

    private func row(_ u: Unit) -> some View {
        let on = active == u
        return HStack(spacing: 0) {
            VStack(spacing: 2) {
                Image(systemName: "chevron.up").font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.white.opacity(on ? 0.55 : 0))
                    .onTapGesture { if on { step(u, up: true) } }
                Text("\(amount(u))")
                    .font(RealFont.inter(on ? 26 : 16, on ? .semibold : .regular))
                    .foregroundStyle(on ? Color.white : Color.white.opacity(0.72))
                    .contentTransition(.numericText(value: Double(amount(u))))
                    .monospacedDigit()
                Image(systemName: "chevron.down").font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.white.opacity(on ? 0.55 : 0))
                    .onTapGesture { if on { step(u, up: false) } }
            }
            .frame(minWidth: 34)
            Text(" " + t("{0} önce", u.word)).font(RealFont.inter(16, on ? .semibold : .regular))
                .foregroundStyle(on ? Color.white : Color.white.opacity(0.72))
            Spacer()
        }
        .padding(.horizontal, 18).padding(.vertical, 4)
        .contentShape(Rectangle())
        .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.22), value: on)
        .onTapGesture { active = u; selected = amount(u) * u.factor }
        .gesture(DragGesture(minimumDistance: 6).onChanged { v in
            if active != u { active = u; selected = amount(u) * u.factor }
            let delta = -v.translation.height - dragCarry
            if abs(delta) >= 18 { step(u, up: delta > 0); dragCarry += delta > 0 ? 18 : -18 }
        }.onEnded { _ in dragCarry = 0 })
        .accessibilityElement(children: .combine)
        .accessibilityLabel(t("{0} {1} önce", amount(u), u.word))
        .accessibilityAdjustableAction { dir in active = u; step(u, up: dir == .increment) }
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
