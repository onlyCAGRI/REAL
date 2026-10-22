import SwiftUI
import UIKit

// contextcards/*: rare, gentle Home nudges (at most two a day, one at a time) shown at the TOP of Kartlarım, looking
// like a card: tap opens it, a swipe to the left puts it away ("Şimdi değil").

enum ContextCardType: String, CaseIterable, Codable, Identifiable {
    var id: String { rawValue }
    case water, mood, book, media, watch, listen, play, journal

    var accent: Color {
        switch self {
        case .water: return Color(argb: 0xFF5FB4E6)
        case .mood: return Color(argb: 0xFFB69BE8)
        case .book: return Color(argb: 0xFFD5B786)
        case .media: return Color(argb: 0xFFDDD4C6)
        case .watch: return Color(argb: 0xFFE8B04F)
        case .listen: return Color(argb: 0xFF7FD3A8)
        case .play: return Color(argb: 0xFF8FA8FF)
        case .journal: return Color(argb: 0xFFF2A6A0)
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
    /// When the app was first installed; nil = unknown, no warm-up quiet.
    var installedAt: Date? = ContextCardSignals.appInstalledAt
    /// The series rated most recently in Puanlarım and its last rated episode (season, number).
    var seriesInProgress: String?
    var lastEpisode: (season: Int, number: Int)?
    /// nil = journal data unknown -> the journal card is never shown.
    var journalWrittenToday: Bool?

    /// iOS has no install date API: the app's Documents folder is created on the first launch after install.
    static let appInstalledAt: Date? = {
        guard let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return nil }
        return (try? FileManager.default.attributesOfItem(atPath: docs.path))?[.creationDate] as? Date
    }()
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
    /// Settings test hook: one type, or ContextCardEngine.forceAll for every type in turn.
    var forcedType: String?
    var forcedDone: [String]? = nil

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
        var h = actedOn(type)
        let streak = (dismissStreak[type.rawValue] ?? 0) + 1
        let limitHit = streak >= ContextCardEngine.streakLimit
        h.dismissStreak[type.rawValue] = limitHit ? 0 : streak
        h.snoozeUntil[type.rawValue] = now + (limitHit ? ContextCardEngine.streakSnooze : ContextCardEngine.dismissSnooze)
        return h
    }

    func completed(_ type: ContextCardType, now: Int64) -> ContextCardHistory {
        var h = actedOn(type)
        h.dismissStreak[type.rawValue] = 0
        h.snoozeUntil[type.rawValue] = now + ContextCardEngine.completeSnooze
        return h
    }

    private func actedOn(_ type: ContextCardType) -> ContextCardHistory {
        var h = self
        if h.activeType == type.rawValue { h.activeType = nil }
        if h.forcedType == type.rawValue { h.forcedType = nil; h.forcedDone = nil }
        if h.forcedType == ContextCardEngine.forceAll {
            let done = (h.forcedDone ?? []) + [type.rawValue]
            if ContextCardType.allCases.allSatisfy({ done.contains($0.rawValue) }) { h.forcedType = nil; h.forcedDone = nil }
            else { h.forcedDone = done }
        }
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
    /// Posted after Settings' test buttons (and every other change): Home looks again right away, not on the next minute.
    static let changed = Notification.Name("RealContextCardsChanged")
    static func update(_ change: (ContextCardHistory) -> ContextCardHistory) {
        let next = change(load())
        if let d = try? JSONEncoder().encode(next) { UserDefaults.standard.set(d, forKey: key) }
    }
    /// Settings test buttons.
    static func force(_ type: ContextCardType?) {
        update { var h = $0; h.forcedType = type?.rawValue; h.forcedDone = nil; return h }
        NotificationCenter.default.post(name: changed, object: nil)
    }
    static func forceAll() {
        update { var h = $0; h.forcedType = ContextCardEngine.forceAll; h.forcedDone = nil; return h }
        NotificationCenter.default.post(name: changed, object: nil)
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
    /// A fresh install stays quiet for its first day: no "you haven't had water" on the very first open.
    static let installQuiet = 24 * hour
    static let forceAll = "all"
    private static let sameTypePenalty = 25
    private static let lowMoods: Set<JournalMood> = [.tired, .bored, .sad, .low, .stressed, .hard, .bad]

    struct Decision { var card: ContextCard?; var activate: ContextCardType? }

    static func decide(_ s: ContextCardSignals, _ history: ContextCardHistory) -> Decision {
        guard history.enabled else { return Decision() }
        if history.forcedType == forceAll {
            // Test hook: every type in turn (the next one comes when the shown one is acted on).
            let next = ContextCardType.allCases.first { !(history.forcedDone ?? []).contains($0.rawValue) }
            return Decision(card: next.map { build($0, s) })
        }
        if let forced = history.forcedType.flatMap(ContextCardType.init(rawValue:)) { return Decision(card: build(forced, s)) }
        let nowMs = Int64(s.now.timeIntervalSince1970 * 1000)
        if let installed = s.installedAt, (0..<installQuiet).contains(nowMs - Int64(installed.timeIntervalSince1970 * 1000)) {
            return Decision()
        }
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
    /// Calendar weekday: 1 = Sunday … 6 = Friday, 7 = Saturday.
    private static func weekday(_ s: ContextCardSignals) -> Int { Calendar.real.component(.weekday, from: s.now) }
    private static func isWeekend(_ s: ContextCardSignals) -> Bool { [7, 1].contains(weekday(s)) }
    private static func isWeekendEvening(_ s: ContextCardSignals) -> Bool {
        [6, 7, 1].contains(weekday(s)) && minutes(s) >= 18 * 60
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
        case .watch: return s.seriesInProgress != nil && s.lastEpisode != nil && (19 * 60...23 * 60 + 30).contains(m)
        // Commute hours: on the way in, on the way back.
        case .listen: return (8 * 60..<10 * 60).contains(m) || (17 * 60..<19 * 60 + 30).contains(m)
        case .play: return isWeekend(s) ? (14 * 60..<22 * 60).contains(m) : (20 * 60 + 30..<23 * 60).contains(m)
        case .journal: return s.journalWrittenToday == false && m >= 21 * 60
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
        case .watch: return 58
        case .journal: return 52
        case .listen: return 40
        case .play: return 38 + (isLowMood(s) ? 10 : 0)
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
        case .watch:
            let message: String
            if let series = s.seriesInProgress, let ep = s.lastEpisode {
                message = "\(series) · \(ep.season). sezon \(ep.number + 1). bölüm seni bekliyor."
            } else if let series = s.seriesInProgress {
                message = "\(series) seni bekliyor."
            } else { message = "Yarım kalan dizin seni bekliyor." }
            return ContextCard(type: type, eyebrow: "Akşam", title: "Sıradaki bölüm", message: message, primaryLabel: "Puanlarım")
        case .listen:
            return ContextCard(type: type, eyebrow: minutes(s) < 12 * 60 ? "Sabah" : "Yolda", title: "Ne dinlesek?",
                               message: "Türünü seç, kartları kaydır.", primaryLabel: "Keşfet")
        case .play:
            return ContextCard(type: type, eyebrow: isWeekend(s) ? "Hafta sonu" : "Akşam", title: "Ne oynasak?",
                               message: isLowMood(s) ? "Kafa dağıtacak hafif bir oyun bulalım." : "Bir tür seç, sana uyanları bulalım.",
                               primaryLabel: "Keşfet")
        case .journal:
            return ContextCard(type: type, eyebrow: "Gece", title: "Bugünü iki satırla yaz",
                               message: "Kısa bir not, yarına güzel bir iz bırakır.", primaryLabel: "Yaz")
        }
    }
}

/// The card currently waiting at the top of Kartlarım (for the dot beside the title).
@MainActor
@Observable
final class ContextCardPresence {
    static let shared = ContextCardPresence()
    var activeType: ContextCardType?
}

/// HomeContextCard: evaluates once a minute and shows the card as a tile like the person's own cards.
struct HomeContextCard: View {
    let onOpen: (ContextCardType) -> Void
    @Environment(LifeStore.self) private var life
    @Environment(BookStore.self) private var books
    @Environment(ReminderStore.self) private var store
    @AppStorage("context_cards_enabled") private var enabled = true
    @State private var card: ContextCard?
    @State private var busy = false
    @State private var progress: Double = 0.02
    @State private var dragX: CGFloat = 0

    var body: some View {
        Group {
            if let card {
                tile(card)
                    .padding(.horizontal, 16).padding(.bottom, 8)
                    .transition(.opacity.combined(with: .move(edge: .leading)))
            }
        }
        .animation(.easeOut(duration: 0.26), value: card)
        .task(id: enabled) {
            while !Task.isCancelled {
                evaluate()
                try? await Task.sleep(nanoseconds: 60_000_000_000)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: ContextCardStore.changed)) { _ in evaluate() }
        .onAppear { evaluate() }
        .onChange(of: card?.type) { _, type in ContextCardPresence.shared.activeType = type }
        .onDisappear { ContextCardPresence.shared.activeType = nil }
    }

    private func signals() -> ContextCardSignals {
        let now = Date()
        let cal = Calendar.real
        let moodsToday = life.moods.filter { cal.isDate(Date(timeIntervalSince1970: Double($0.details.loggedAt) / 1000), inSameDayAs: now) }
            .sorted { $0.details.loggedAt > $1.details.loggedAt }
        let book = books.books.last { books.page($0) > 0 }
        // A series moves to the end of Puanlarım whenever an episode is rated: the last one is the current one.
        let series = MediaRatingStore.shared.entries.last { $0.series && !$0.episodes.isEmpty }
        let lastEpisode = series?.episodes.keys.compactMap { key -> (Int, Int)? in
            let parts = key.split(separator: ":").compactMap { Int($0) }
            return parts.count == 2 ? (parts[0], parts[1]) : nil
        }.max { $0.0 != $1.0 ? $0.0 < $1.0 : $0.1 < $1.1 }
        let journalToday = store.reminders.contains { r in
            guard let j = r.journal else { return false }
            return cal.isDate(Date(timeIntervalSince1970: Double(j.updatedAt) / 1000), inSameDayAs: now)
        }
        return ContextCardSignals(now: now, waterTodayMl: life.total(on: now), waterGoalMl: life.goal(on: now),
                                  moodLoggedToday: !moodsToday.isEmpty, lastMoodToday: moodsToday.first?.mood,
                                  unfinishedBookTitle: book?.title, unfinishedBookAuthor: book?.author,
                                  seriesInProgress: series?.media.title,
                                  lastEpisode: lastEpisode.map { (season: $0.0, number: $0.1) },
                                  journalWrittenToday: journalToday)
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
        dragX = 0
        then?()
        // The forced test row ("Tüm kartları göster") shows the next type right away.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { evaluate() }
    }

    private func addWater() {
        guard !busy else { return }
        busy = true
        life.addDrink(250)
        let updated = ContextCardEngine.build(.water, signals())
        card = updated
        withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.52)) { progress = max(updated.progress ?? 0, 0.02) }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) {
            busy = false
            finish(.water, dismissed: false)
        }
    }

    /// Looks like one of the person's cards, tinted with its colour so it reads as a REAL suggestion.
    private func tile(_ c: ContextCard) -> some View {
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        return HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 2).fill(c.type.accent).frame(width: 4, height: 34)
            VStack(alignment: .leading, spacing: 2) {
                Text(c.title).font(RealFont.inter(15)).foregroundStyle(Color(white: 0.95)).lineLimit(1)
                Text(c.message).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.6)).lineLimit(1)
                Text("REAL önerisi · \(c.eyebrow.lowercased())").font(RealFont.inter(11))
                    .foregroundStyle(c.type.accent.opacity(0.8)).padding(.top, 1)
                if c.progress != nil {
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            Capsule().fill(.white.opacity(0.10))
                            Capsule().fill(c.type.accent).frame(width: g.size.width * min(max(progress, 0.02), 1))
                        }
                    }
                    .frame(height: 3).padding(.top, 6)
                }
            }
            Spacer(minLength: 6)
            if c.type == .water {
                Button { addWater() } label: {
                    Text(c.primaryLabel).font(RealFont.inter(13)).foregroundStyle(.black)
                        .padding(.horizontal, 12).padding(.vertical, 8).background(Capsule().fill(.white))
                }
                .buttonStyle(.plain)
            } else {
                Text("›").font(.system(size: 24)).foregroundStyle(c.type.accent).padding(.horizontal, 4)
            }
        }
        .padding(.leading, 14).padding(.trailing, 12).padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(shape.fill(Color(argb: 0xFF141416)).overlay(shape.fill(c.type.accent.opacity(0.10))))
        .overlay(shape.strokeBorder(c.type.accent.opacity(0.35), lineWidth: 0.5))
        .contentShape(shape)
        .offset(x: dragX)
        .opacity(1 - min(Double(-dragX) / 300, 1))
        .onTapGesture { if !busy { finish(c.type, dismissed: false) { onOpen(c.type) } } }
        .gesture(
            DragGesture(minimumDistance: 12)
                .onChanged { v in if !busy { dragX = min(0, v.translation.width) } }
                .onEnded { v in
                    if v.translation.width < -96 {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        withAnimation(.easeOut(duration: 0.18)) { dragX = -400 }
                        finish(c.type, dismissed: true)
                    } else {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { dragX = 0 }
                    }
                }
        )
        .accessibilityAction(named: Text(c.secondaryLabel)) { finish(c.type, dismissed: true) }
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
