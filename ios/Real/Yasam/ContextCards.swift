import SwiftUI

// contextcards/*: rare, gentle Home nudges (water · mood · book · media) shown as the last item of the Home list.

enum ContextCardType: String, CaseIterable, Codable, Identifiable {
    var id: String { rawValue }
    case water, mood, book, media

    var accent: Color {
        switch self {
        case .water: return Color(argb: 0xFF5FB4E6)
        case .mood: return Color(argb: 0xFFB69BE8)
        case .book: return Color(argb: 0xFFD5B786)
        case .media: return Color(argb: 0xFFDDD4C6)
        }
    }
}

struct ContextCardSignals {
    var now: Date
    var waterTodayMl: Int?
    var waterGoalMl: Int = 2_000
    var moodLoggedToday = false
    var lastMoodToday: JournalMood?
    var unfinishedBookTitle: String?
    var unfinishedBookAuthor: String?
}

struct ContextCard: Equatable {
    var type: ContextCardType
    var eyebrow: String
    var title: String
    var message: String
    var primaryLabel: String
    var secondaryLabel = "Şimdi değil"
    var progress: Double?
}

/// ContextCardHistory: persisted bookkeeping that keeps cards rare.
struct ContextCardHistory: Codable {
    var enabled = true
    var epochDay: Int64 = .min
    var shownToday: [String: Int] = [:]
    var lastActivationAt: Int64 = 0
    var activeType: String?
    var activeSince: Int64 = 0
    var lastType: String?
    var snoozeUntil: [String: Int64] = [:]
    var dismissStreak: [String: Int] = [:]
    var forcedType: String?

    func rolled(_ day: Int64) -> ContextCardHistory {
        var h = self
        if h.epochDay != day { h.epochDay = day; h.shownToday = [:] }
        return h
    }

    func activated(_ type: ContextCardType, now: Int64, day: Int64) -> ContextCardHistory {
        var h = rolled(day)
        h.shownToday[type.rawValue, default: 0] += 1
        h.lastActivationAt = now; h.activeType = type.rawValue; h.activeSince = now; h.lastType = type.rawValue
        return h
    }

    /// "Şimdi değil": three dismissals in a row silence that type for days.
    func dismissed(_ type: ContextCardType, now: Int64) -> ContextCardHistory {
        var h = self
        let streak = (dismissStreak[type.rawValue] ?? 0) + 1
        let limitHit = streak >= ContextCardEngine.streakLimit
        if h.activeType == type.rawValue { h.activeType = nil }
        if h.forcedType == type.rawValue { h.forcedType = nil }
        h.dismissStreak[type.rawValue] = limitHit ? 0 : streak
        h.snoozeUntil[type.rawValue] = now + (limitHit ? ContextCardEngine.streakSnooze : ContextCardEngine.dismissSnooze)
        return h
    }

    func completed(_ type: ContextCardType, now: Int64) -> ContextCardHistory {
        var h = self
        if h.activeType == type.rawValue { h.activeType = nil }
        if h.forcedType == type.rawValue { h.forcedType = nil }
        h.dismissStreak[type.rawValue] = 0
        h.snoozeUntil[type.rawValue] = now + ContextCardEngine.completeSnooze
        return h
    }
}

enum ContextCardStore {
    private static let key = "real_context_cards_v1"
    static func load() -> ContextCardHistory {
        guard let d = UserDefaults.standard.data(forKey: key), let h = try? JSONDecoder().decode(ContextCardHistory.self, from: d) else {
            var h = ContextCardHistory()
            h.enabled = UserDefaults.standard.object(forKey: "context_cards_enabled") as? Bool ?? true
            return h
        }
        return h
    }
    static func update(_ change: (ContextCardHistory) -> ContextCardHistory) {
        let next = change(load())
        if let d = try? JSONEncoder().encode(next) { UserDefaults.standard.set(d, forKey: key) }
    }
}

enum ContextCardEngine {
    static let hour: Int64 = 3_600_000
    static let activeWindow = 2 * hour
    static let minGap = 3 * hour
    static let maxPerDay = 2
    static let dismissSnooze = 4 * hour
    static let completeSnooze = 12 * hour
    static let streakLimit = 3
    static let streakSnooze = 3 * 24 * hour
    private static let sameTypePenalty = 25
    private static let lowMoods: Set<JournalMood> = [.tired, .bored, .sad, .low, .stressed, .hard, .bad]

    struct Decision { var card: ContextCard?; var activate: ContextCardType? }

    static func decide(_ s: ContextCardSignals, _ history: ContextCardHistory) -> Decision {
        guard history.enabled else { return Decision() }
        if let forced = history.forcedType.flatMap(ContextCardType.init(rawValue:)) { return Decision(card: build(forced, s)) }
        let nowMs = Int64(s.now.timeIntervalSince1970 * 1000)
        let h = history.rolled(Calendar.real.epochDay(of: s.now))
        if let active = h.activeType.flatMap(ContextCardType.init(rawValue:)),
           (0..<activeWindow).contains(nowMs - h.activeSince), isEligible(active, s) {
            return Decision(card: build(active, s))
        }
        if h.shownToday.values.reduce(0, +) >= maxPerDay { return Decision() }
        if (0..<minGap).contains(nowMs - h.lastActivationAt) { return Decision() }
        let best = ContextCardType.allCases
            .filter { (h.shownToday[$0.rawValue] ?? 0) == 0 }
            .filter { (h.snoozeUntil[$0.rawValue] ?? 0) <= nowMs }
            .filter { isEligible($0, s) }
            .max { score($0, s) - ($0.rawValue == h.lastType ? sameTypePenalty : 0) < score($1, s) - ($1.rawValue == h.lastType ? sameTypePenalty : 0) }
        guard let best else { return Decision() }
        return Decision(card: build(best, s), activate: best)
    }

    private static func minutes(_ s: ContextCardSignals) -> Int {
        let c = Calendar.real.dateComponents([.hour, .minute], from: s.now)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }
    private static func isLowMood(_ s: ContextCardSignals) -> Bool { s.lastMoodToday.map { lowMoods.contains($0) } ?? false }
    private static func isWeekendEvening(_ s: ContextCardSignals) -> Bool {
        // Calendar weekday: 1 = Sunday … 6 = Friday, 7 = Saturday.
        let wd = Calendar.real.component(.weekday, from: s.now)
        return [6, 7, 1].contains(wd) && minutes(s) >= 18 * 60
    }

    static func isEligible(_ type: ContextCardType, _ s: ContextCardSignals) -> Bool {
        let m = minutes(s)
        switch type {
        case .water:
            guard let ml = s.waterTodayMl else { return false }
            let threshold = m >= 17 * 60 ? 0.75 : 0.5
            return (11 * 60..<21 * 60).contains(m) && Double(ml) / Double(max(s.waterGoalMl, 1)) < threshold
        case .mood: return !s.moodLoggedToday && (12 * 60..<23 * 60).contains(m)
        case .book: return s.unfinishedBookTitle != nil && (20 * 60...23 * 60 + 30).contains(m)
        case .media: return (17 * 60...23 * 60 + 30).contains(m) && (isWeekendEvening(s) || isLowMood(s))
        }
    }

    private static func score(_ type: ContextCardType, _ s: ContextCardSignals) -> Int {
        switch type {
        case .water:
            let p = min(max(Double(s.waterTodayMl ?? 0) / Double(max(s.waterGoalMl, 1)), 0), 1)
            return 60 + Int((1 - p) * 20) + (minutes(s) >= 15 * 60 ? 10 : 0)
        case .mood: return 55 + (minutes(s) >= 18 * 60 ? 15 : 0)
        case .book: return 50 + (isLowMood(s) ? 10 : 0)
        case .media: return 45 + (isLowMood(s) ? 20 : 0)
        }
    }

    static func build(_ type: ContextCardType, _ s: ContextCardSignals) -> ContextCard {
        switch type {
        case .water:
            let ml = s.waterTodayMl ?? 0
            let goal = max(s.waterGoalMl, 1)
            return ContextCard(type: type, eyebrow: "Bugün",
                               title: ml == 0 ? "Bugün su tarafı sessiz" : "Su hedefinin biraz gerisindesin",
                               message: ml == 0 ? "Henüz hiç su içmedin. Bir bardakla başlayalım." : "\(ml) / \(goal) ml. Bir bardak iyi gelir.",
                               primaryLabel: "+250 ml", progress: min(max(Double(ml) / Double(goal), 0), 1))
        case .mood:
            return ContextCard(type: type, eyebrow: "Bugün", title: "Bugün nasıl hissediyorsun?",
                               message: "Günün havasını tek dokunuşla kaydet.", primaryLabel: "Kaydet")
        case .book:
            let parts = [s.unfinishedBookTitle, s.unfinishedBookAuthor].compactMap { $0 }.filter { !$0.isEmpty }
            return ContextCard(type: type, eyebrow: "Akşam", title: "Kaldığın yerden devam",
                               message: (parts.isEmpty ? "Kitaplığındaki kitap" : parts.joined(separator: " · ")) + " seni bekliyor.",
                               primaryLabel: "Oku")
        case .media:
            return ContextCard(type: type, eyebrow: "Akşam", title: "Bu akşam ne izlesek?",
                               message: isLowMood(s) ? "Yorucu bir gün gibi. Hafif bir şey bulalım." : "Türünü seç, afişleri keşfet.",
                               primaryLabel: "Keşfet")
        }
    }
}

/// HomeContextCard: evaluates once a minute, shows the card in a WidgetGlowCard, +250 ml animates the bar first.
struct HomeContextCard: View {
    let onOpen: (ContextCardType) -> Void
    @Environment(LifeStore.self) private var life
    @Environment(BookStore.self) private var books
    @AppStorage("context_cards_enabled") private var enabled = true
    @State private var card: ContextCard?
    @State private var busy = false
    @State private var progress: Double = 0.02

    var body: some View {
        Group {
            if let card {
                content(card)
                    .padding(.horizontal, 16).padding(.top, 20).padding(.bottom, 8)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .animation(.easeOut(duration: 0.26), value: card)
        .task(id: enabled) {
            while !Task.isCancelled {
                evaluate()
                try? await Task.sleep(nanoseconds: 60_000_000_000)
            }
        }
    }

    private func signals() -> ContextCardSignals {
        let now = Date()
        let cal = Calendar.real
        let moodsToday = life.moods.filter { cal.isDate(Date(timeIntervalSince1970: Double($0.details.loggedAt) / 1000), inSameDayAs: now) }
            .sorted { $0.details.loggedAt > $1.details.loggedAt }
        let book = books.books.last { books.page($0) > 0 }
        return ContextCardSignals(now: now, waterTodayMl: life.total(on: now), waterGoalMl: life.goal(on: now),
                                  moodLoggedToday: !moodsToday.isEmpty, lastMoodToday: moodsToday.first?.mood,
                                  unfinishedBookTitle: book?.title, unfinishedBookAuthor: book?.author)
    }

    private func evaluate() {
        guard !busy else { return }
        var history = ContextCardStore.load()
        history.enabled = enabled
        let now = Date()
        let decision = ContextCardEngine.decide(signals(), history)
        if let t = decision.activate {
            ContextCardStore.update { $0.activated(t, now: now.epochMillis, day: Calendar.real.epochDay(of: now)) }
        }
        card = decision.card
        if let p = decision.card?.progress { progress = max(p, 0.02) }
    }

    private func finish(_ type: ContextCardType, dismissed: Bool, then: (() -> Void)? = nil) {
        let now = Date().epochMillis
        ContextCardStore.update { dismissed ? $0.dismissed(type, now: now) : $0.completed(type, now: now) }
        withAnimation(.easeOut(duration: 0.2)) { card = nil }
        then?()
    }

    private func primary(_ c: ContextCard) {
        guard !busy else { return }
        if c.type == .water {
            busy = true
            life.addDrink(250)
            let updated = ContextCardEngine.build(.water, signals())
            card = updated
            withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.52)) { progress = max(updated.progress ?? 0, 0.02) }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) {
                busy = false
                finish(.water, dismissed: false)
            }
        } else {
            finish(c.type, dismissed: false) { onOpen(c.type) }
        }
    }

    private func content(_ c: ContextCard) -> some View {
        WidgetGlowCard(glow: c.type.accent) {
            HStack(spacing: 8) {
                Circle().fill(c.type.accent).frame(width: 7, height: 7)
                Text(c.eyebrow).font(RealFont.inter(12)).foregroundStyle(c.type.accent)
            }
            Text(c.title).font(RealFont.inter(30)).kerning(-1).foregroundStyle(.white).padding(.top, 12)
            Text(c.message).font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.65)).padding(.top, 8)
            if c.progress != nil {
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(.white.opacity(0.12))
                        Capsule().fill(c.type.accent).frame(width: g.size.width * min(max(progress, 0.02), 1))
                    }
                }
                .frame(height: 4).padding(.top, 14)
            }
            HStack(spacing: 16) {
                Button { primary(c) } label: {
                    Text(c.primaryLabel).font(RealFont.inter(14)).foregroundStyle(.black)
                        .padding(.horizontal, 18).padding(.vertical, 10).background(Capsule().fill(.white))
                }
                .buttonStyle(.plain)
                Button { if !busy { finish(c.type, dismissed: true) } } label: {
                    Text(c.secondaryLabel).font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.55))
                        .padding(.horizontal, 6).padding(.vertical, 10)
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 16)
        }
        .contentShape(Rectangle())
        .onTapGesture { if !busy { finish(c.type, dismissed: false) { onOpen(c.type) } } }
    }
}

/// WidgetGlowCard: dark 32pt card with the accent glowing up from the bottom edge; fades/rises in.
struct WidgetGlowCard<Content: View>: View {
    let glow: Color
    @ViewBuilder let content: () -> Content
    @State private var entered = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 32, style: .continuous)
        VStack(alignment: .leading, spacing: 0) { content() }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 22).padding(.vertical, 18)
            .background {
                ZStack {
                    LinearGradient(colors: [Color(argb: 0xFF0D1119), Color(argb: 0xFF05070B)], startPoint: .top, endPoint: .bottom)
                    LinearGradient(stops: [.init(color: .clear, location: 0.55), .init(color: glow.opacity(0.30), location: 0.90),
                                           .init(color: glow.opacity(0.85), location: 1)], startPoint: .top, endPoint: .bottom)
                }
                .clipShape(shape)
            }
            .overlay(shape.strokeBorder(.white.opacity(0.10), lineWidth: 0.8))
            .opacity(entered ? 1 : 0)
            .offset(y: entered ? 0 : 22)
            .onAppear { withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.52)) { entered = true } }
    }
}
