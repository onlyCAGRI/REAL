import SwiftUI

/// Warm tone of the alarm glow behind the date sheet (same family as the ringing alarm).
let alarmGlowTone = Color(argb: 0xFFFF8A5B)

/// DateTimeAlarmSegment.kt on iOS. Tarih | Saat with a third stop: the Saat thumb pushed (dragged) right stretches
/// over the faint alarm icon at the end and the card becomes an alarm; pushed back, it is Saat again. A tap on the
/// icon does the same. While held the thumb turns into a clear glass lens over the labels (magnified inside, a bright
/// rim); past the alarm stop the glass warms up. Positions: 0 = Tarih, 1 = Saat, 2 = Alarm.
struct DateTimeAlarmSegment: View {
    @Binding var timeTab: Bool
    @Binding var alarm: Bool
    /// Plays a short push of the Saat thumb so the move can be found.
    var nudge = false

    @State private var pos: CGFloat = 1
    @State private var dragStart: CGFloat?
    @State private var pastAlarm = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let slot: CGFloat = 46
    private let settle = Animation.spring(response: 0.34, dampingFraction: 0.82)
    private var target: CGFloat { !timeTab ? 0 : (alarm ? 2 : 1) }

    var body: some View {
        GeometryReader { geo in
            let inner = max(geo.size.width - 8, slot + 2)
            let cell = (inner - slot) / 2
            let stretch = min(max(pos - 1, 0), 1)
            let x = cell * min(pos, 1)
            let w = cell + slot * stretch
            let pressed = dragStart != nil
            let lensW = w * (pressed ? 1.06 : 1)
            let lensH: CGFloat = pressed ? 46 : 40
            let lensCenter = CGPoint(x: x + w / 2, y: 20)
            ZStack(alignment: .topLeading) {
                Capsule().fill(LiquidGlassTokens.contentColor)
                    .overlay(Capsule().strokeBorder(.white.opacity(0.18), lineWidth: 0.8))
                    .frame(width: w, height: 40).offset(x: x)
                    .opacity(pressed ? 0 : 1)
                labels(cell: cell, stretch: stretch, inner: inner)
                    .mask {
                        // The labels under the lens are drawn by the lens itself (magnified), not twice.
                        ZStack {
                            Rectangle()
                            if pressed {
                                Capsule().frame(width: lensW, height: lensH).position(lensCenter).blendMode(.destinationOut)
                            }
                        }
                        .compositingGroup()
                    }
                if pressed {
                    ZStack {
                        labels(cell: cell, stretch: stretch, inner: inner)
                            .scaleEffect(1.16, anchor: UnitPoint(x: lensCenter.x / inner, y: 0.5))
                            .brightness(0.18)
                            .mask(Capsule().frame(width: lensW, height: lensH).position(lensCenter))
                        Capsule().fill(pastAlarm ? alarmGlowTone.opacity(0.14) : .white.opacity(0.03))
                            .frame(width: lensW, height: lensH).position(lensCenter)
                        Capsule().strokeBorder(
                            LinearGradient(colors: pastAlarm
                                ? [alarmGlowTone.opacity(0.85), alarmGlowTone.opacity(0.35)]
                                : [.white.opacity(0.6), .white.opacity(0.18)], startPoint: .top, endPoint: .bottom),
                            lineWidth: 1)
                            .frame(width: lensW, height: lensH).position(lensCenter)
                            .shadow(color: .black.opacity(0.4), radius: 10, y: 6)
                    }
                    .frame(width: inner, height: 40)
                    .animation(.easeOut(duration: 0.18), value: pastAlarm)
                    .allowsHitTesting(false)
                }
                taps(cell: cell)
            }
            .frame(width: inner, height: 40)
            .padding(4)
            .contentShape(Rectangle())
            .gesture(drag(cell: cell))
        }
        .frame(height: 48)
        .liquidGlass(Capsule(), role: .chrome)
        .onAppear { pos = target }
        .onChange(of: target) { _, t in if dragStart == nil { withAnimation(reduceMotion ? nil : settle) { pos = t } } }
        .task(id: nudge) {
            guard nudge, !reduceMotion, target == 1 else { return }
            try? await Task.sleep(for: .milliseconds(450))
            for _ in 0..<2 {
                guard dragStart == nil, target == 1 else { return }
                withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.28)) { pos = 1.42 }
                try? await Task.sleep(for: .milliseconds(300))
                withAnimation(.spring(response: 0.4, dampingFraction: 0.55)) { pos = 1 }
                try? await Task.sleep(for: .milliseconds(560))
            }
        }
    }

    private func labels(cell: CGFloat, stretch: CGFloat, inner: CGFloat) -> some View {
        HStack(spacing: 0) {
            Text(t("Tarih")).font(RealFont.inter(14, timeTab ? .regular : .semibold))
                .foregroundStyle(timeTab ? Color(argb: 0xFFB8B8BE) : .white)
                .frame(width: cell)
            // Saat ↔ Alarm rides the thumb, centred on its stretched width.
            ZStack {
                Text(t("Saat")).font(RealFont.inter(14, timeTab ? .semibold : .regular))
                    .foregroundStyle(timeTab ? .white : Color(argb: 0xFFB8B8BE))
                    .opacity(Double(min(max(1 - stretch * 2, 0), 1)))
                HStack(spacing: 6) {
                    Image(systemName: "alarm").font(.system(size: 14, weight: .semibold))
                    Text(t("Alarm")).font(RealFont.inter(14, .semibold))
                }
                .foregroundStyle(.white)
                .opacity(Double(min(max(stretch * 2 - 1, 0), 1)))
            }
            .frame(width: cell + slot * stretch)
            // The faint alarm icon; on the Tarih tab a chosen alarm keeps it lit so it is not forgotten.
            Image(systemName: "alarm").font(.system(size: 15, weight: .medium))
                .foregroundStyle(.white.opacity(alarm && !timeTab ? 0.78 : 0.34))
                .frame(width: slot)
                .opacity(Double(1 - stretch))
                .frame(width: slot * (1 - stretch), alignment: .leading)
                .clipped()
        }
        .lineLimit(1)
        .frame(width: inner, height: 40, alignment: .leading)
    }

    private func taps(cell: CGFloat) -> some View {
        HStack(spacing: 0) {
            Color.clear.frame(width: cell).contentShape(Rectangle())
                .onTapGesture { timeTab = false }
                .accessibilityAddTraits(timeTab ? [] : .isSelected).accessibilityLabel(t("Tarih"))
            Color.clear.frame(width: cell).contentShape(Rectangle())
                .onTapGesture { if timeTab { alarm = false }; timeTab = true }
                .accessibilityAddTraits(timeTab && !alarm ? .isSelected : []).accessibilityLabel(t("Saat"))
            Color.clear.frame(width: slot).contentShape(Rectangle())
                .onTapGesture {
                    let on = !(timeTab && alarm)
                    timeTab = true; alarm = on
                    if on { UISelectionFeedbackGenerator().selectionChanged() }
                }
                .accessibilityAddTraits(timeTab && alarm ? .isSelected : []).accessibilityLabel(alarm ? t("Alarm açık") : t("Alarm"))
        }
        .accessibilityElement(children: .contain)
    }

    private func drag(cell: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { v in
                if dragStart == nil {
                    pastAlarm = pos >= 1.5
                    withAnimation(.easeOut(duration: 0.2)) { dragStart = pos }
                }
                var p = dragStart ?? pos
                var rest = v.translation.width
                if rest > 0 {
                    if p < 1 { let a = min(rest / cell, 1 - p); p += a; rest -= a * cell }
                    p += rest / slot
                } else {
                    if p > 1 { let a = max(rest / slot, 1 - p); p += a; rest -= a * slot }
                    p += rest / cell
                }
                p = min(max(p, 0), 2)
                // A tick when the thumb crosses into (or out of) the alarm stop.
                if (p >= 1.5) != pastAlarm { pastAlarm = p >= 1.5; UISelectionFeedbackGenerator().selectionChanged() }
                pos = p
            }
            .onEnded { _ in
                let pick: CGFloat = pos < 0.5 ? 0 : (pos < 1.5 ? 1 : 2)
                withAnimation(reduceMotion ? nil : settle) { dragStart = nil; pos = pick }
                pastAlarm = false
                // Pushed to Tarih the alarm choice is kept; Saat / Alarm decide it.
                if pick == 0 { timeTab = false } else { timeTab = true; alarm = pick == 2 }
            }
    }
}

/// Slow breathing warm light behind the date sheet while Alarm is chosen, like the alarm screen but calm.
struct AlarmBreathGlow: View {
    var active: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(paused: !active || reduceMotion)) { context in
            let t = context.date.timeIntervalSinceReferenceDate
            let b = reduceMotion ? 0.5 : (sin(t * .pi / 1.9) + 1) / 2
            GeometryReader { g in
                RadialGradient(stops: [
                    .init(color: alarmGlowTone.opacity(0.17 + 0.10 * b), location: 0),
                    .init(color: alarmGlowTone.opacity(0.05), location: 0.55),
                    .init(color: .clear, location: 1),
                ], center: UnitPoint(x: 0.5, y: 0.42), startRadius: 0,
                   endRadius: max(g.size.width, g.size.height) * 0.62 * (0.86 + 0.14 * b))
            }
        }
        .opacity(active ? 1 : 0)
        .animation(.easeInOut(duration: 0.5), value: active)
        .allowsHitTesting(false)
    }
}
