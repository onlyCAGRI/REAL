import SwiftUI
import CoreMotion
import UIKit

/// İçecek ekranı (WaterTrackerScreen.kt): Su / Coffee / Beer tabs, a living liquid surface that rises with the
/// day's progress (tap / drag to disturb, tilt the phone to slope it), the big percentage riding the surface,
/// quick-add glass buttons and the "Bugün" history dock.
struct WaterTrackerScreen: View {
    @Environment(LifeStore.self) private var life
    @Environment(\.dismiss) private var dismiss
    @State private var beverage: BeverageType = .water
    @State private var engine = WaveEngine()
    @State private var goalDialog = false
    @State private var goalText = ""
    @State private var shown = false

    private struct Palette { let background: [Color]; let liquid: [Color]; let edge: Color; let number: Color }

    private var palette: Palette {
        switch beverage {
        case .water: return Palette(background: [0xFF00384E, 0xFF00647D, 0xFF315766].map { Color(argb: $0) },
                                    liquid: [0xFF00384E, 0xFF00647D, 0xFF315766].map { Color(argb: $0) },
                                    edge: Color(argb: 0xFFACF8F5), number: Color(argb: 0xFFF3FCFC))
        case .coffee: return Palette(background: [0xFF1C100B, 0xFF2D1B12, 0xFF180F0B].map { Color(argb: $0) },
                                     liquid: [0xFFA36A3B, 0xFF724020, 0xFF482616, 0xFF29170F].map { Color(argb: $0) },
                                     edge: Color(argb: 0xFFE5BB84), number: Color(argb: 0xFFF3FCFC))
        case .beer: return Palette(background: [0xFF211B0D, 0xFF302814, 0xFF1A170E].map { Color(argb: $0) },
                                   liquid: [0xFFF6D16A, 0xFFE6B443, 0xFFC8982C, 0xFF9C7122].map { Color(argb: $0) },
                                   edge: Color(argb: 0xFFFFF1BF), number: Color(argb: 0xFF3A2B12))
        }
    }

    private var total: Int { life.total(on: Date(), beverage: beverage) }
    private var target: Int { beverage == .water ? life.goal(on: Date()) : 2_000 }
    private var progress: Double { Double(total) / Double(max(target, 1)) }

    private func amountText(_ ml: Int) -> String {
        guard beverage == .beer else { return "\(ml) ml" }
        return ml % 10 == 0 ? "\(ml / 10) cl" : String(format: "%.1f cl", Double(ml) / 10)
    }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .top) {
                liquid(size: geo.size)
                    .ignoresSafeArea()
                VStack(alignment: .leading, spacing: 0) {
                    tabs
                    VStack(alignment: .leading, spacing: 0) {
                        Button { close() } label: {
                            Image(systemName: "chevron.left").font(.system(size: 17, weight: .medium)).foregroundStyle(.white)
                                .frame(width: 44, height: 44).liquidGlass(Circle(), role: .chrome)
                        }
                        .accessibilityLabel("İçecek ekranını kapat")
                        Text(beverage.label).font(RealFont.inter(34, .medium)).foregroundStyle(.white).padding(.top, 16)
                        Button {
                            guard beverage == .water else { return }
                            goalText = "\(life.goalMl)"
                            goalDialog = true
                        } label: {
                            Text(beverage == .water ? "Bugün: \(total) / \(target) ml" : "Bugün: \(amountText(total))")
                                .font(RealFont.inter(20)).foregroundStyle(.white)
                                .padding(.vertical, 9)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 24).padding(.vertical, 12)
                    Spacer()
                    HStack(spacing: 10) {
                        ForEach(beverage.quickAmounts, id: \.self) { amount in
                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                life.addDrink(amount, beverage: beverage)
                                engine.disturb(at: 0.5, strength: 0.9)
                            } label: {
                                VStack(spacing: 2) {
                                    Text("+ \(amountText(amount))").font(RealFont.inter(18, .medium))
                                    Text(beverage.label).font(RealFont.inter(12)).opacity(0.7)
                                }
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity, minHeight: 64)
                                .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous))
                            }
                            .buttonStyle(PressScaleStyle(scale: 0.96))
                        }
                    }
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    history(height: min(max(geo.size.height * 0.26, 120), 230))
                }
            }
            .offset(x: shown ? 0 : geo.size.width)
        }
        .onAppear {
            engine.target = min(progress, 1.15)
            engine.level = engine.target
            engine.startMotion()
            withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.22)) { shown = true }
        }
        .onDisappear { engine.stopMotion() }
        .onChange(of: progress) { _, p in engine.target = min(p, 1.15) }
        .onChange(of: beverage) { _, _ in engine.target = min(progress, 1.15); engine.disturb(at: 0.5, strength: 0.6) }
        .alert("Günlük su hedefi", isPresented: $goalDialog) {
            TextField("Hedef (ml)", text: $goalText).keyboardType(.numberPad)
            Button("Kaydet") { if let v = Int(goalText) { life.setGoal(v) } }
            Button("Vazgeç", role: .cancel) {}
        }
    }

    private func close() {
        withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.22)) { shown = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) { withoutAnimation { dismiss() } }
    }

    private var tabs: some View {
        HStack(spacing: 22) {
            ForEach(BeverageType.allCases) { option in
                Button { beverage = option } label: {
                    Text(option.label).font(RealFont.inter(16, beverage == option ? .semibold : .regular))
                        .foregroundStyle(beverage == option ? Color.white : Color.white.opacity(0.4))
                        .padding(.vertical, 6)
                }
                .accessibilityAddTraits(beverage == option ? .isSelected : [])
            }
            Spacer()
        }
        .padding(.horizontal, 24).padding(.vertical, 8)
    }

    private func liquid(size: CGSize) -> some View {
        TimelineView(.animation) { timeline in
            Canvas { ctx, canvasSize in
                let t = timeline.date.timeIntervalSinceReferenceDate
                engine.step(time: t)
                let w = canvasSize.width, h = canvasSize.height
                ctx.fill(Path(CGRect(origin: .zero, size: canvasSize)), with: .linearGradient(Gradient(colors: palette.background),
                         startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
                let header = min(148, h * 0.56)
                func surfaceY(_ x: Double) -> Double {
                    let natural = engine.surface(x: x, time: t)
                    return max(header + (natural - 0.24) / 0.76 * (h - header), -72)
                }
                var surface = Path()
                for i in 0...80 {
                    let x = Double(i) / 80
                    let p = CGPoint(x: x * w, y: surfaceY(x))
                    if i == 0 { surface.move(to: p) } else { surface.addLine(to: p) }
                }
                var water = surface
                water.addLine(to: CGPoint(x: w, y: h))
                water.addLine(to: CGPoint(x: 0, y: h))
                water.closeSubpath()
                let centerY = surfaceY(0.5)
                ctx.drawLayer { layer in
                    layer.clip(to: water)
                    layer.fill(Path(CGRect(origin: .zero, size: canvasSize)), with: .linearGradient(Gradient(colors: palette.liquid),
                               startPoint: CGPoint(x: 0, y: beverage == .water ? 0 : centerY - h * 0.08), endPoint: CGPoint(x: 0, y: h)))
                    if beverage == .water {
                        layer.fill(Path(CGRect(x: 0, y: centerY - 16, width: w, height: h * 0.45)),
                                   with: .linearGradient(Gradient(colors: [Color(argb: 0xFF00B7DD).opacity(0.3), .clear]),
                                                         startPoint: CGPoint(x: 0, y: centerY - 16), endPoint: CGPoint(x: 0, y: centerY + h * 0.45)))
                        let ct = t * 0.3
                        for i in 0..<12 {
                            let cx = (sin(ct * 0.4 + Double(i) * 1.7) * 0.3 + 0.5 + Double(i) * 0.08).truncatingRemainder(dividingBy: 1) * w
                            let cy = centerY + h * (0.08 + Double(i % 5) * 0.12 + sin(ct * 0.5 + Double(i) * 2.1) * 0.04)
                            let r = 22 + sin(ct + Double(i) * 0.9) * 14
                            let a = min(max(0.025 + sin(ct * 0.7 + Double(i)) * 0.015, 0.01), 0.05)
                            layer.fill(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)),
                                       with: .radialGradient(Gradient(colors: [.white.opacity(a * 3), .clear]), center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: r))
                        }
                    }
                }
                ctx.stroke(surface, with: .color(palette.edge.opacity(0.75)), lineWidth: 1.5)
                // Big percentage riding the surface.
                let label = beverage == .water || progress >= 1 ? "\(max(Int((progress * 100).rounded()), 0))%" : amountText(total)
                let text = ctx.resolve(Text(label).font(.system(size: 72, weight: .bold)).kerning(-3).foregroundColor(palette.number))
                let textSize = text.measure(in: canvasSize)
                let textY = min(surfaceY((24 + textSize.width / 2) / max(w, 1)) - 8, h - textSize.height - 10)
                ctx.draw(text, at: CGPoint(x: 24, y: textY), anchor: .topLeading)
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { v in engine.disturb(at: v.location.x / max(size.width, 1), strength: min(0.3, (abs(v.velocity.width) + abs(v.velocity.height)) / 6000 + 0.05)) }
                .onEnded { v in engine.disturb(at: v.location.x / max(size.width, 1), strength: 0.45) })
        }
    }

    private func history(height: CGFloat) -> some View {
        let entries = life.entries(on: Date(), beverage: beverage)
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Bugün").font(RealFont.inter(26)).foregroundStyle(.white)
                Spacer()
                Text(beverage == .water || progress >= 1 ? "\(Int((progress * 100).rounded()))%" : amountText(total))
                    .font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.78))
            }
            .padding(.horizontal, 24)
            if entries.isEmpty {
                Text("İlk yudumunu ekle.").font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFA3A6AA)).padding(24)
                Spacer(minLength: 0)
            } else {
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(entries) { e in
                            HStack {
                                Text("\(e.beverage.label) (+\(amountText(e.amountMl)))").font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFE0E2E3))
                                Spacer()
                                Text(RealDates.time(e.createdAt.date)).font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.7))
                                Button { life.removeDrink(e.id) } label: {
                                    Text("−").font(RealFont.inter(20)).foregroundStyle(Color(argb: 0xFFB9BEC0)).frame(width: 44, height: 44)
                                }
                                .accessibilityLabel("\(e.amountMl) mililitre kaydını geri al")
                            }
                            .padding(.leading, 24).padding(.trailing, 10)
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
        }
        .padding(.top, 18)
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .liquidGlass(RoundedRectangle(cornerRadius: 30, style: .continuous))
        .padding(.horizontal, 10).padding(.vertical, 6)
    }
}

/// WaterSurfaceEngine: spring-smoothed level, travelling disturbance ripples and a tilt slope from CoreMotion.
final class WaveEngine {
    var level: Double = 0
    var target: Double = 0
    private var lastTime: Double = 0
    private var velocity: Double = 0
    private var ripples: [(x: Double, strength: Double, born: Double)] = []
    private var tilt: Double = 0
    private var tiltTarget: Double = 0
    private let motion = CMMotionManager()
    private var now: Double = 0

    func startMotion() {
        guard motion.isDeviceMotionAvailable else { return }
        motion.deviceMotionUpdateInterval = 1 / 30
        motion.startDeviceMotionUpdates(to: .main) { [weak self] data, _ in
            guard let g = data?.gravity else { return }
            self?.tiltTarget = max(-0.35, min(0.35, g.x))
        }
    }

    func stopMotion() { motion.stopDeviceMotionUpdates() }

    func step(time: Double) {
        let dt = lastTime == 0 ? 1 / 60 : min(time - lastTime, 1 / 20)
        lastTime = time
        now = time
        let force = (target - level) * 18 - velocity * 7
        velocity += force * dt
        level += velocity * dt
        tilt += (tiltTarget - tilt) * min(dt * 4, 1)
        ripples.removeAll { time - $0.born > 4 }
    }

    func disturb(at x: Double, strength: Double) {
        ripples.append((x: min(max(x, 0), 1), strength: strength, born: now))
        if ripples.count > 12 { ripples.removeFirst(ripples.count - 12) }
    }

    /// Natural surface position 0 (top) … 1 (bottom) at horizontal position x.
    func surface(x: Double, time: Double) -> Double {
        var y = 1 - 0.76 * level
        y += sin(x * 7 + time * 1.6) * 0.006 + sin(x * 13 - time * 2.3) * 0.003
        y += (x - 0.5) * tilt * 0.35
        for r in ripples {
            let age = time - r.born
            let dist = abs(x - r.x)
            let wave = sin((dist * 22) - age * 9) * exp(-age * 1.4) * exp(-dist * 3)
            y += wave * 0.018 * r.strength
        }
        return y
    }
}
