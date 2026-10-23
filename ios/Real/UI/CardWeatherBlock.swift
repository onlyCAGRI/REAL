import SwiftUI

/// CardWeatherBlock.kt: one forecast for a card (Apple WeatherKit through the REAL Worker).
/// kind: sun, cloud, rain, storm, snow, fog, night.
struct CardWeather: Codable, Equatable {
    var kind: String
    var label: String
    var temperature: Int?
    var high: Int?
    var low: Int?
    var precipitationChance: Int?
    var windKmh: Int?
    var attributionUrl: String?
}

/// Hava durumu bloğu: never added by hand. A card gets it when it has both a time (or a planned day) and a place with
/// coordinates, within WeatherKit's 10-day window. Forecasts are cached per card for 3 hours; once the card's time has
/// passed the last forecast stays as it was. Hiding it is per card, on this phone.
enum CardWeatherRepository {
    private static let fresh: Int64 = 3 * 60 * 60 * 1000
    private static let window: Int64 = 10 * 24 * 60 * 60 * 1000
    private static let defaults = UserDefaults(suiteName: "real_card_weather") ?? .standard

    private struct Stored: Codable { var at: Int64; var lat: Double; var lng: Double; var fetchedAt: Int64; var weather: CardWeather }

    private static var now: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    /// The time the forecast is for, and whether it is a whole day ("Tüm gün"); nil without a date.
    static func target(_ item: ReminderItem) -> (at: Int64, allDay: Bool)? {
        if let a = item.alertAt { return (a, false) }
        guard let day = item.plannedDateEpochDay else { return nil }
        let date = Date(timeIntervalSince1970: TimeInterval(day) * 86_400)
        var utc = Calendar(identifier: .gregorian); utc.timeZone = TimeZone(identifier: "UTC")!
        let c = utc.dateComponents([.year, .month, .day], from: date)
        let noon = Calendar.current.date(from: DateComponents(year: c.year, month: c.month, day: c.day, hour: 12)) ?? date
        return (Int64(noon.timeIntervalSince1970 * 1000), true)
    }

    static func isHidden(_ id: Int64) -> Bool { defaults.bool(forKey: "hidden_\(id)") }

    private static func stored(_ id: Int64) -> Stored? {
        defaults.data(forKey: "w_\(id)").flatMap { try? JSONDecoder().decode(Stored.self, from: $0) }
    }

    /// Whether the block has anything to show: not hidden, and inside the forecast window or a kept forecast.
    static func mayShow(_ item: ReminderItem) -> Bool {
        guard !isHidden(item.id), let at = target(item)?.at else { return false }
        return (at >= now - 2 * 60 * 60 * 1000 && at <= now + window) || stored(item.id) != nil
    }

    static func hide(_ id: Int64) {
        defaults.set(true, forKey: "hidden_\(id)")
        defaults.removeObject(forKey: "w_\(id)")
    }

    static func load(_ item: ReminderItem) async -> CardWeather? {
        guard let goal = target(item), let lat = item.location?.latitude, let lng = item.location?.longitude,
              !isHidden(item.id) else { return nil }
        let (at, allDay) = goal
        let now = Self.now
        let kept = stored(item.id).flatMap { $0.at == at && abs($0.lat - lat) < 0.01 && abs($0.lng - lng) < 0.01 ? $0 : nil }
        if let kept, at < now || now - kept.fetchedAt < fresh { return kept.weather }
        if at < now - 2 * 60 * 60 * 1000 || at > now + window { return kept?.weather }
        guard let json = try? await RealBackend.post("/api/weather", ["latitude": lat, "longitude": lng, "at": at, "allDay": allDay,
                                                                      "timezone": TimeZone.current.identifier], timeout: 15),
              let w = json["weather"] as? [String: Any],
              let data = try? JSONSerialization.data(withJSONObject: w),
              let weather = try? JSONDecoder().decode(CardWeather.self, from: data), !weather.kind.isEmpty else { return kept?.weather }
        if let d = try? JSONEncoder().encode(Stored(at: at, lat: lat, lng: lng, fetchedAt: now, weather: weather)) {
            defaults.set(d, forKey: "w_\(item.id)")
        }
        return weather
    }
}

private func weatherColors(_ kind: String) -> (Color, Color) {
    switch kind {
    case "sun": return (Color(argb: 0xFFFFCF6B), Color(argb: 0xFFFF7A4D))
    case "cloud": return (Color(argb: 0xFFD9E6FF), Color(argb: 0xFF8FB4F0))
    case "rain": return (Color(argb: 0xFF7AB8FF), Color(argb: 0xFF3D7FE0))
    case "storm": return (Color(argb: 0xFFC9C3FF), Color(argb: 0xFF8F8DE0))
    case "snow": return (Color(argb: 0xFFFFFFFF), Color(argb: 0xFFA9CDF0))
    case "fog": return (Color(argb: 0xFFD4D8DE), Color(argb: 0xFF8E959E))
    default: return (Color(argb: 0xFFF3F0FF), Color(argb: 0xFFA9A3EF))
    }
}

private let weatherSurface = Color(argb: 0xFF141416)
private let attributionFallback = "https://developer.apple.com/weatherkit/data-source-attribution/"

struct CardWeatherBlock: View {
    let reminder: ReminderItem
    /// Small slot (half width or a short height chosen in "Sırala"): icon, temperature and condition only.
    var compact = false
    /// Long press → "Hava durumunu gizle"; off while the card is being edited (the grid owns the gestures then).
    var interactive = true
    var onHide: () -> Void = {}
    /// No forecast came (offline, not signed in, too far ahead…): the card drops the slot instead of an empty box.
    var onUnavailable: () -> Void = {}
    @State private var weather: CardWeather?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var loadKey: String {
        let t = CardWeatherRepository.target(reminder)
        return "\(reminder.id)|\(t?.at ?? 0)|\(reminder.location?.latitude ?? 0)|\(reminder.location?.longitude ?? 0)"
    }

    var body: some View {
        Group {
            if let w = weather, let target = CardWeatherRepository.target(reminder) {
                content(w, target)
            } else {
                // Still loading: keep the slot calm instead of jumping.
                RoundedRectangle(cornerRadius: 22, style: .continuous).fill(weatherSurface)
                    .frame(maxWidth: .infinity, minHeight: compact ? 72 : 120)
            }
        }
        .task(id: loadKey) {
            weather = await CardWeatherRepository.load(reminder)
            if weather == nil, !Task.isCancelled { onUnavailable() }
        }
    }

    private func whenText(_ at: Int64, _ allDay: Bool) -> String {
        let f = DateFormatter()
        f.locale = I18n.locale
        f.dateFormat = allDay ? "EEE '· \(t("Tüm gün"))'" : "EEE HH:mm"
        let s = f.string(from: Date(timeIntervalSince1970: TimeInterval(at) / 1000))
        return s.prefix(1).uppercased() + s.dropFirst()
    }

    private func openSources(_ w: CardWeather) {
        if let url = URL(string: w.attributionUrl ?? attributionFallback) { UIApplication.shared.open(url) }
    }

    @ViewBuilder
    private func content(_ w: CardWeather, _ target: (at: Int64, allDay: Bool)) -> some View {
        let (top, bottom) = weatherColors(w.kind)
        let when = whenText(target.at, target.allDay)
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        let body = Group {
            if compact {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 10) {
                        WeatherGlyph(kind: w.kind, top: top, bottom: bottom, still: reduceMotion).frame(width: 52, height: 46)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(t(w.label)).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.7)).lineLimit(1)
                            Text(when).font(RealFont.inter(11)).foregroundStyle(.white.opacity(0.45)).lineLimit(1)
                        }
                        Spacer(minLength: 0)
                        if let t = w.temperature {
                            Text("\(t)°").font(RealFont.inter(34, .light)).tracking(-1).foregroundStyle(.white).lineLimit(1)
                        }
                    }
                    Text("Apple Weather").font(RealFont.inter(10)).foregroundStyle(.white.opacity(0.4))
                        .onTapGesture { openSources(w) }
                }
                .padding(.horizontal, 14).padding(.top, 12).padding(.bottom, 10)
            } else {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text(reminder.location?.placeName ?? "").font(RealFont.inter(11)).foregroundStyle(.white.opacity(0.8)).lineLimit(1)
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .overlay(RoundedRectangle(cornerRadius: 6).stroke(.white.opacity(0.16), lineWidth: 0.5))
                        Spacer(minLength: 12)
                        Text(when).font(RealFont.inter(12, .medium)).foregroundStyle(.white.opacity(0.85))
                    }
                    HStack(spacing: 14) {
                        VStack(spacing: 4) {
                            WeatherGlyph(kind: w.kind, top: top, bottom: bottom, still: reduceMotion).frame(width: 96, height: 84)
                            Text(t(w.label)).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.55)).lineLimit(2)
                                .multilineTextAlignment(.center)
                        }
                        .frame(width: 96)
                        VStack(alignment: .leading, spacing: 7) {
                            if let v = w.windKmh { WeatherStat(icon: .wind, text: t("{0} km/s", v)) }
                            if let v = w.precipitationChance { WeatherStat(icon: .drop, text: t("%{0}", v)) }
                            if let h = w.high, let l = w.low { WeatherStat(icon: .thermo, text: "\(h)° / \(l)°") }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        if let t = w.temperature {
                            Text("\(t)°").font(RealFont.inter(52, .light)).tracking(-2).foregroundStyle(.white).lineLimit(1)
                        }
                    }
                    // Apple asks for its mark and the data sources link wherever WeatherKit data is shown.
                    HStack {
                        Text("Apple Weather")
                        Spacer()
                        Text(t("Kaynaklar")).onTapGesture { openSources(w) }
                    }
                    .font(RealFont.inter(11)).foregroundStyle(.white.opacity(0.4))
                }
                .padding(.horizontal, 18).padding(.top, 16).padding(.bottom, 12)
            }
        }
        body
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                // A soft light in the weather's colour behind the icon.
                GeometryReader { g in
                    RadialGradient(colors: [bottom.opacity(0.16), .clear], center: UnitPoint(x: 0.12, y: 0),
                                   startRadius: 0, endRadius: min(g.size.width, g.size.height) * 1.1)
                }
                .background(weatherSurface)
            }
            .clipShape(shape)
            .overlay(shape.stroke(.white.opacity(0.08), lineWidth: 0.5))
            .contentShape(shape)
            .contextMenu(interactive ? ContextMenu {
                Button(role: .destructive) { CardWeatherRepository.hide(reminder.id); onHide() } label: {
                    Label(t("Hava durumunu gizle"), systemImage: "eye.slash")
                }
            } : nil)
    }
}

private struct WeatherStat: View {
    enum Icon { case wind, drop, thermo }
    let icon: Icon
    let text: String
    var body: some View {
        HStack(spacing: 8) {
            Canvas { ctx, size in
                let u = size.width / 14
                let c = GraphicsContext.Shading.color(.white.opacity(0.45))
                let s = StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round)
                var p = Path()
                switch icon {
                case .wind:
                    p.move(to: CGPoint(x: u, y: 5 * u)); p.addLine(to: CGPoint(x: 9 * u, y: 5 * u))
                    p.addCurve(to: CGPoint(x: 9 * u, y: 1.5 * u), control1: CGPoint(x: 12 * u, y: 5 * u), control2: CGPoint(x: 12 * u, y: u))
                    p.move(to: CGPoint(x: u, y: 9 * u)); p.addLine(to: CGPoint(x: 11 * u, y: 9 * u))
                    p.addCurve(to: CGPoint(x: 11 * u, y: 12.5 * u), control1: CGPoint(x: 14 * u, y: 9 * u), control2: CGPoint(x: 14 * u, y: 13 * u))
                case .drop:
                    p.move(to: CGPoint(x: 7 * u, y: u))
                    p.addCurve(to: CGPoint(x: 12 * u, y: 9 * u), control1: CGPoint(x: 11 * u, y: 6 * u), control2: CGPoint(x: 12 * u, y: 8 * u))
                    p.addCurve(to: CGPoint(x: 7 * u, y: 13.5 * u), control1: CGPoint(x: 12 * u, y: 12 * u), control2: CGPoint(x: 9.8 * u, y: 13.5 * u))
                    p.addCurve(to: CGPoint(x: 2 * u, y: 9 * u), control1: CGPoint(x: 4.2 * u, y: 13.5 * u), control2: CGPoint(x: 2 * u, y: 12 * u))
                    p.addCurve(to: CGPoint(x: 7 * u, y: u), control1: CGPoint(x: 2 * u, y: 8 * u), control2: CGPoint(x: 3 * u, y: 6 * u))
                    p.closeSubpath()
                case .thermo:
                    p.move(to: CGPoint(x: 7 * u, y: 1.5 * u)); p.addLine(to: CGPoint(x: 7 * u, y: 8.5 * u))
                    p.addEllipse(in: CGRect(x: 4.4 * u, y: 8.4 * u, width: 5.2 * u, height: 5.2 * u))
                }
                ctx.stroke(p, with: c, style: s)
            }
            .frame(width: 14, height: 14)
            Text(text).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.85))
        }
    }
}

/// The animated line icon, drawn in a 192 × 168 space and scaled to the box.
private struct WeatherGlyph: View {
    let kind: String
    let top: Color
    let bottom: Color
    let still: Bool
    @State private var appear: CGFloat = 0

    var body: some View {
        TimelineView(.animation(paused: still)) { tl in
            let t = still ? 0.4 : CGFloat(tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 3600))
            Canvas { ctx, size in
                let k = min(size.width / 192, size.height / 168)
                let ease = 1 - pow(1 - appear, 3)
                ctx.scaleBy(x: k, y: k)
                ctx.translateBy(x: 0, y: (1 - ease) * 8)
                let brush = GraphicsContext.Shading.linearGradient(Gradient(colors: [top, bottom]),
                                                                   startPoint: CGPoint(x: 40, y: 20), endPoint: CGPoint(x: 150, y: 150))
                drawGlyph(&ctx, t: t, alpha: ease, brush: brush)
            }
        }
        .onAppear { withAnimation(.easeOut(duration: still ? 0 : 0.5)) { appear = 1 } }
    }

    private func cloud(_ cx: CGFloat, _ cy: CGFloat) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: cx - 48, y: cy + 22))
        func arc(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ from: Double, _ sweep: Double) {
            p.addArc(center: CGPoint(x: cx + x, y: cy + y), radius: r, startAngle: .degrees(from), endAngle: .degrees(from + sweep), clockwise: false)
        }
        arc(-30, 2, 20, 108, 153)
        arc(2, -14, 30, 198, 153)
        arc(36, 6, 18, 252, 189)
        p.closeSubpath()
        return p
    }

    private func drawGlyph(_ ctx: inout GraphicsContext, t: CGFloat, alpha: CGFloat, brush: GraphicsContext.Shading) {
        let line = StrokeStyle(lineWidth: 7, lineCap: .round, lineJoin: .round)
        func stroke(_ path: Path, _ a: CGFloat = alpha, _ b: GraphicsContext.Shading? = nil) {
            var c = ctx; c.opacity = Double(min(max(a, 0), 1)); c.stroke(path, with: b ?? brush, style: line)
        }
        func seg(_ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat, _ a: CGFloat = alpha, _ b: GraphicsContext.Shading? = nil, in c0: GraphicsContext? = nil) {
            var c = c0 ?? ctx; c.opacity = Double(min(max(a, 0), 1))
            var p = Path(); p.move(to: CGPoint(x: x1, y: y1)); p.addLine(to: CGPoint(x: x2, y: y2))
            c.stroke(p, with: b ?? brush, style: line)
        }
        func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)) }
        func rotated(_ c: GraphicsContext, _ radians: CGFloat, _ x: CGFloat, _ y: CGFloat) -> GraphicsContext {
            var c = c; c.translateBy(x: x, y: y); c.rotate(by: .radians(Double(radians))); c.translateBy(x: -x, y: -y); return c
        }
        switch kind {
        case "sun":
            stroke(circle(96, 84, 26 + sin(t * 2) * 1.2))
            let spin = rotated(ctx, t * 0.35, 96, 84)
            for i in 0..<8 {
                let l = 8 + sin(t * 3 + CGFloat(i)) * 3
                seg(136, 84, 136 + l, 84, in: rotated(spin, CGFloat(i) * .pi / 4, 96, 84))
            }
        case "cloud":
            let sun = GraphicsContext.Shading.linearGradient(Gradient(colors: [Color(argb: 0xFFFFD27A), Color(argb: 0xFFFF9B5A)]),
                                                             startPoint: CGPoint(x: 100, y: 30), endPoint: CGPoint(x: 140, y: 75))
            stroke(circle(120, 52, 17), alpha, sun)
            let spin = rotated(ctx, t * 0.3, 120, 52)
            for i in 0..<6 { seg(146, 52, 152, 52, alpha, sun, in: rotated(spin, CGFloat(i) * .pi / 3, 120, 52)) }
            let path = cloud(88 + sin(t * 0.8) * 4, 96)
            var fill = ctx; fill.opacity = Double(alpha); fill.fill(path, with: .color(weatherSurface))
            stroke(path)
        case "rain":
            for (i, d) in [(CGFloat(64), CGFloat(0), CGFloat(1)), (100, 0.33, 1.25), (132, 0.66, 1)].enumerated() {
                let (cx, ph, s) = d
                let q = (t * 0.7 + ph).truncatingRemainder(dividingBy: 1)
                let cy = 40 + q * 80 + (i == 1 ? -10 : 0)
                var p = Path()
                p.move(to: CGPoint(x: cx, y: cy - 16 * s))
                p.addCurve(to: CGPoint(x: cx, y: cy + 12 * s), control1: CGPoint(x: cx + 11 * s, y: cy - 2 * s), control2: CGPoint(x: cx + 11 * s, y: cy + 12 * s))
                p.addCurve(to: CGPoint(x: cx, y: cy - 16 * s), control1: CGPoint(x: cx - 11 * s, y: cy + 12 * s), control2: CGPoint(x: cx - 11 * s, y: cy - 2 * s))
                stroke(p, alpha * sin(q * .pi))
            }
        case "storm":
            let path = cloud(96, 70)
            var fill = ctx; fill.opacity = Double(alpha); fill.fill(path, with: .color(weatherSurface))
            stroke(path)
            let m = t.truncatingRemainder(dividingBy: 2.2)
            let flash = m < 0.12 || (m > 0.22 && m < 0.3)
            let bolt = GraphicsContext.Shading.linearGradient(Gradient(colors: [Color(argb: 0xFFFFE27A), Color(argb: 0xFFFFB23D)]),
                                                              startPoint: CGPoint(x: 84, y: 98), endPoint: CGPoint(x: 104, y: 152))
            var p = Path()
            p.move(to: CGPoint(x: 98, y: 98)); p.addLine(to: CGPoint(x: 84, y: 124)); p.addLine(to: CGPoint(x: 104, y: 124)); p.addLine(to: CGPoint(x: 90, y: 152))
            stroke(p, alpha * (flash ? 1 : 0.55), bolt)
        case "snow":
            let spin = rotated(ctx, t * 0.25, 96, 84)
            for i in 0..<6 {
                let c = rotated(spin, CGFloat(i) * .pi / 3, 96, 84)
                seg(96, 84, 96, 44, in: c); seg(96, 58, 86, 50, in: c); seg(96, 58, 106, 50, in: c)
            }
            for i in 0..<5 {
                let q = (t * 0.35 + CGFloat(i) / 5).truncatingRemainder(dividingBy: 1)
                var c = ctx; c.opacity = Double(max(alpha * sin(q * .pi) * 0.8, 0))
                c.fill(circle(30 + CGFloat(i) * 33 + sin(t + CGFloat(i)) * 5, 10 + q * 150, 3), with: .color(.white))
            }
        case "fog":
            for i in 0..<4 {
                let o = sin(t * 0.9 + CGFloat(i) * 1.3) * 10
                let l: CGFloat = i % 2 == 1 ? 90 : 120
                seg(96 - l / 2 + o, 46 + CGFloat(i) * 26, 96 + l / 2 + o, 46 + CGFloat(i) * 26)
            }
        default:
            var moon = Path()
            moon.addArc(center: CGPoint(x: 92, y: 84), radius: 36, startAngle: .degrees(63), endAngle: .degrees(297), clockwise: false)
            moon.addArc(center: CGPoint(x: 112, y: 72), radius: 28, startAngle: .degrees(261), endAngle: .degrees(99), clockwise: true)
            moon.closeSubpath()
            stroke(moon)
            for (a, b, ph) in [(CGFloat(146), CGFloat(40), CGFloat(0)), (154, 92, 1.4), (128, 128, 2.6)] {
                let z = 0.4 + 0.6 * (0.5 + 0.5 * sin(t * 2.2 + ph))
                var c = ctx; c.opacity = Double(alpha * z)
                var p = Path()
                p.move(to: CGPoint(x: a - 6 * z, y: b)); p.addLine(to: CGPoint(x: a + 6 * z, y: b))
                p.move(to: CGPoint(x: a, y: b - 6 * z)); p.addLine(to: CGPoint(x: a, y: b + 6 * z))
                c.stroke(p, with: brush, style: StrokeStyle(lineWidth: 4, lineCap: .round))
            }
        }
    }
}
