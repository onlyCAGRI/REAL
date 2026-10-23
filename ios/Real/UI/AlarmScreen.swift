import SwiftUI

/// AlarmScreen.kt on iOS: a breathing glow in the card's colour, a dot grid with waves running out from it, the time
/// in hollow dot-matrix digits and a glass panel (title, snooze, slide to stop). Sliding calms the screen and turns
/// the sound down; letting go springs back and brings it up again.
struct AlarmScreen: View {
    let alarm: RingingAlarm
    @State private var slide: CGFloat = 0
    @State private var ramp: Task<Void, Never>?

    private static let ink = Color(argb: 0xFF08080A)

    var body: some View {
        let accent = alarm.color
        ZStack {
            Self.ink.ignoresSafeArea()
            TimelineView(.animation) { timeline in
                let t = timeline.date.timeIntervalSinceReferenceDate
                Canvas { ctx, size in Self.drawBackground(&ctx, size: size, t: t, accent: accent, calm: 1 - slide) }
            }
            .ignoresSafeArea()
            .accessibilityHidden(true)

            VStack(spacing: 0) {
                HStack(spacing: 6) {
                    RealSymbol("bell", size: 13)
                    Text("ALARM").font(RealFont.inter(12)).tracking(2)
                }
                .foregroundStyle(.white.opacity(0.85))
                .padding(.horizontal, 12).padding(.vertical, 5)
                .background(Capsule().fill(.white.opacity(0.08)))
                .padding(.top, 28)
                Spacer(minLength: 0).frame(maxHeight: 120)
                DotMatrixTime(text: time).accessibilityLabel(t("Alarm {0}", time))
                Text(day).font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.7)).padding(.top, 14)
                Spacer(minLength: 0)
                panel(accent)
            }
        }
        .statusBarHidden()
        .onDisappear { ramp?.cancel() }
    }

    private var time: String {
        let f = DateFormatter(); f.locale = I18n.locale; f.dateFormat = "HH:mm"
        return f.string(from: alarm.at.date)
    }

    private var day: String {
        let f = DateFormatter(); f.locale = I18n.locale; f.dateFormat = "EEEE, d MMMM"
        return f.string(from: alarm.at.date).capitalized(with: I18n.locale)
    }

    // MARK: Panel

    private func panel(_ accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if !alarm.subtitle.isEmpty { Text(alarm.subtitle).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.65)) }
            Text(alarm.title).font(RealFont.inter(22, .medium)).foregroundStyle(.white).lineLimit(2)
            HStack(spacing: 10) {
                Button {
                    let at = AlarmCenter.shared.snooze()
                    let f = DateFormatter(); f.locale = I18n.locale; f.dateFormat = "HH:mm"
                    RealToast.shared.show(t("{0}'da tekrar çalacak", f.string(from: at)))
                } label: {
                    VStack(spacing: 1) {
                        Text(t("Ertele")).font(RealFont.inter(10)).foregroundStyle(.white.opacity(0.7))
                        Text(t("{0} dk", RealAlarmSettings.snoozeMinutes)).font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                    }
                    .frame(width: 62, height: 62)
                    .background(Circle().fill(.black.opacity(0.28)))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(t("{0} dakika ertele", RealAlarmSettings.snoozeMinutes))
                slideTrack
            }
            .padding(.top, 26)
        }
        .padding(.leading, 20).padding(.trailing, 16).padding(.top, 22).padding(.bottom, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 34, style: .continuous)
                .fill(LinearGradient(colors: [accent.opacity(0.55), Color(argb: 0xF2191623)], startPoint: .top, endPoint: .bottom))
        )
        .padding(10)
    }

    private var slideTrack: some View {
        GeometryReader { geo in
            let knob: CGFloat = 52
            let travel = max(1, geo.size.width - knob - 10)
            ZStack(alignment: .leading) {
                Capsule().fill(.black.opacity(0.28))
                Text(t("Kaydır, kapat")).font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.75 * (1 - slide)))
                    .frame(maxWidth: .infinity).padding(.leading, 44)
                Circle().fill(.white).frame(width: knob, height: knob)
                    .overlay(RealSymbol("chevron.right", size: 18).foregroundStyle(Self.ink))
                    .offset(x: 5 + slide * travel)
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { v in
                                ramp?.cancel()
                                slide = min(1, max(0, v.translation.width / travel))
                                AlarmCenter.shared.slide(slide)
                            }
                            .onEnded { _ in
                                if slide > 0.85 {
                                    withAnimation(.easeOut(duration: 0.12)) { slide = 1 }
                                    AlarmCenter.shared.stop()
                                } else {
                                    springBack()
                                }
                            }
                    )
            }
        }
        .frame(height: 62)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(t("Kaydırarak alarmı kapat"))
        .accessibilityAction { AlarmCenter.shared.stop() }
    }

    /// Back to the start; the volume follows the knob up in small steps.
    private func springBack() {
        let from = slide
        withAnimation(.easeOut(duration: 0.22)) { slide = 0 }
        ramp?.cancel()
        ramp = Task { @MainActor in
            for step in 1...10 {
                try? await Task.sleep(nanoseconds: 22_000_000)
                if Task.isCancelled { return }
                AlarmCenter.shared.slide(from * (1 - CGFloat(step) / 10))
            }
        }
    }

    // MARK: Background

    private static func drawBackground(_ ctx: inout GraphicsContext, size: CGSize, t: TimeInterval, accent: Color, calm: CGFloat) {
        let center = CGPoint(x: size.width / 2, y: size.height * 0.38)
        let breath = 0.95 + 0.13 * sin(t * 2 * .pi / 1.4)
        let r = 220 * breath * (0.6 + 0.4 * calm)
        ctx.fill(Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2)),
                 with: .radialGradient(Gradient(stops: [.init(color: accent.opacity(calm), location: 0),
                                                        .init(color: accent.opacity(0.55 * calm), location: 0.4),
                                                        .init(color: accent.opacity(0), location: 1)]),
                                       center: center, startRadius: 0, endRadius: r))
        // Faint dot grid; crests of a wave travel outward from the glow and light the dots up.
        let step: CGFloat = 14, ring: CGFloat = 26, reach: CGFloat = 380
        let phase = CGFloat(t.truncatingRemainder(dividingBy: 1.4) / 1.4) * 2 * .pi
        var y = step / 2
        while y < size.height {
            var x = step / 2
            while x < size.width {
                let d = hypot(x - center.x, y - center.y)
                let crest = pow(max(0, sin(d / ring - phase)), 6) * calm * max(0, 1 - d / reach)
                let dot = 1.1 * (1 + crest * 0.8)
                ctx.fill(Path(ellipseIn: CGRect(x: x - dot, y: y - dot, width: dot * 2, height: dot * 2)),
                         with: .color(.white.opacity(0.07 + crest * 0.55)))
                x += step
            }
            y += step
        }
    }
}

/// 5×7 hollow-dot digits, as on Android.
private struct DotMatrixTime: View {
    let text: String
    private static let digits: [Character: String] = [
        "0": "01110100011001110101110011000101110", "1": "00100011000010000100001000010001110",
        "2": "01110100010000100010001000100011111", "3": "11110000010000101110000010000111110",
        "4": "00010001100101010010111110001000010", "5": "11111100001111000001000011000101110",
        "6": "00110010001000011110100011000101110", "7": "11111000010001000100010000100001000",
        "8": "01110100011000101110100011000101110", "9": "01110100011000101111000010001001100"
    ]
    private let cell: CGFloat = 11

    var body: some View {
        let width = text.reduce(CGFloat(0)) { $0 + ($1 == ":" ? 2.2 : 6.2) } - 1.2
        Canvas { ctx, _ in
            let r = cell * 0.34
            var ox: CGFloat = 0
            func ring(_ x: CGFloat, _ y: CGFloat) {
                ctx.stroke(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(.white), lineWidth: 2)
            }
            for ch in text {
                if ch == ":" {
                    for row in [2, 4] { ring(ox + cell / 2, CGFloat(row) * cell + cell / 2) }
                    ox += cell * 2.2
                } else if let bits = Self.digits[ch].map(Array.init) {
                    for row in 0..<7 { for col in 0..<5 where bits[row * 5 + col] == "1" {
                        ring(ox + CGFloat(col) * cell + cell / 2, CGFloat(row) * cell + cell / 2)
                    } }
                    ox += cell * 6.2
                }
            }
        }
        .frame(width: cell * width, height: cell * 7)
    }
}
