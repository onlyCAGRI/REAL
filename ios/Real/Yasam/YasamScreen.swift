import SwiftUI
import UIKit

/// Yaşam (HomeWidgetContent.kt + Yasam*.kt): module hub opened from the dock with a circular reveal.
/// Hero sentence card, three icon tabs, the big water figure, line-separated rows (Su, Mood, Günlüklerim,
/// Kitap, Film & Dizi · Oyun) that expand to quick actions, an in-place Günlüklerim mode, and the glass mic.
struct YasamScreen: View {
    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life
    @Environment(BookStore.self) private var books
    @Environment(DiscoveryStore.self) private var discovery
    @Environment(\.dismiss) private var dismiss
    @State private var showBooks = false
    @State private var discoveryTarget: (kind: DiscoveryKind, list: Bool)?
    /// Müzik keşfi: false = öneri, true = Müzik listem.
    @State private var musicTarget: Bool?
    /// Puanlarım (Keşfet → Puanla).
    @State private var showRatings = false
    @State private var resumeBook: String?
    var onOpenCard: (Int64) -> Void = { _ in }

    @State private var revealed = false
    @State private var closing = false
    @State private var section: Section = .home
    @State private var tab = 0
    @State private var open: String?
    @State private var moodHistoryOpen = false
    @State private var waterHistoryOpen = false
    @State private var showWater = false
    @State private var showMoodAdd = false
    @State private var showMoodHistory = false
    @State private var showJournalSheet = false
    @State private var heroTopic = Int.random(in: 0..<6)
    @State private var contentID = 0

    enum Section { case home, journals }

    private let hour = Calendar.real.component(.hour, from: Date())

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottom) {
                Color.black.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        heroCard.padding(.top, 4)
                        if section == .journals {
                            journalList
                        } else {
                            YasamIconTabs(tab: $tab).yasamEnter(revealed, delay: 0.38)
                            tiles
                        }
                        Color.clear.frame(height: 130)
                    }
                    .id(contentID)
                }
                .scrollIndicators(.hidden)
                micButton.yasamEnter(revealed, delay: 0.0, y: 0, scale: 0.6)
            }
            // Circular reveal from the bottom-centre (the dock), like rememberRealEntrance on Android.
            .mask {
                Circle()
                    .frame(width: revealed ? hypot(geo.size.width, geo.size.height) * 2.2 : 10,
                           height: revealed ? hypot(geo.size.width, geo.size.height) * 2.2 : 10)
                    .position(x: geo.size.width / 2, y: geo.size.height - 40)
                    .ignoresSafeArea()
            }
            .scaleEffect(closing ? 0.92 : 1)
            .opacity(closing ? 0 : 1)
        }
        .ignoresSafeArea(.container, edges: .bottom)
        .overlay {
            if let target = discoveryTarget {
                DiscoveryScreen(kind: target.kind, startInList: target.list) {
                    withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.42)) { discoveryTarget = nil }
                }
                .transition(.move(edge: .trailing).combined(with: .opacity))
                .zIndex(4)
            }
            if showRatings {
                RatingsScreen {
                    withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.42)) { showRatings = false }
                }
                .transition(.move(edge: .trailing).combined(with: .opacity))
                .zIndex(4)
            }
            if let list = musicTarget {
                MusicDiscoveryScreen(startInList: list) {
                    withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.42)) { musicTarget = nil }
                }
                .transition(.move(edge: .trailing).combined(with: .opacity))
                .zIndex(4)
            }
        }
        .presentationBackground(.clear)
        .onAppear {
            MediaRatingStore.shared.importWatched(discovery.film.watched)
            withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.9)) { revealed = true }
        }
        .fullScreenCover(isPresented: $showBooks) { BooksModuleScreen(resumeBookId: resumeBook).liftedTopSafeArea() }
        .fullScreenCover(isPresented: $showWater) { WaterTrackerScreen().liftedTopSafeArea().presentationBackground(.black) }
        .fullScreenCover(isPresented: $showMoodAdd) { MoodAddSheet().liftedTopSafeArea() }
        .sheet(isPresented: $showJournalSheet) {
            YasamJournalSheet { text in
                _ = store.addReminder(text: YasamJournalSheet.title()) { r in
                    r.journal = ReminderJournal(mood: nil, text: text, createdAt: .now, updatedAt: .now)
                }
                return true
            }
        }
        .fullScreenCover(isPresented: $showMoodHistory) {
            MoodHistoryScreen(onAdd: { showMoodHistory = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { showMoodAdd = true } })
                .liftedTopSafeArea()
        }
    }

    private func close() {
        if section == .journals { switchSection(.home); return }
        withAnimation(.timingCurve(0.4, 0, 1, 1, duration: 0.28)) { closing = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.28) { withoutAnimation { dismiss() } }
    }

    private func switchSection(_ s: Section) {
        withAnimation(.easeOut(duration: 0.2)) { revealed = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            section = s
            contentID += 1
            withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.6)) { revealed = true }
        }
    }

    // MARK: Data

    private var water: (today: Int, goal: Int, yesterday: Int) {
        let today = Date()
        let yesterday = Calendar.real.date(byAdding: .day, value: -1, to: today)!
        return (life.total(on: today), life.goal(on: today), life.total(on: yesterday))
    }

    private var lastMood: JournalMood? { life.moods.max { $0.details.loggedAt < $1.details.loggedAt }?.mood }

    private var yesterdayMood: JournalMood? {
        let y = Calendar.real.date(byAdding: .day, value: -1, to: Date())!
        return life.moods.filter { Calendar.real.isDate($0.details.loggedAt.date, inSameDayAs: y) }
            .max { $0.details.loggedAt < $1.details.loggedAt }?.mood
    }

    private var journals: [ReminderItem] {
        store.reminders.filter { $0.journal != nil }.sorted { ($0.journal?.createdAt ?? 0) > ($1.journal?.createdAt ?? 0) }
    }

    private var journalPreview: (when: String, line: String)? {
        guard let r = journals.first(where: { !($0.journal?.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true) }),
              let j = r.journal,
              let first = j.text.split(whereSeparator: \.isNewline).map({ $0.trimmingCharacters(in: .whitespaces) }).first(where: { !$0.isEmpty })
        else { return nil }
        return (YasamText.whenLabel(j.createdAt), first)
    }

    private var mediaPrompt: String { hour < 17 ? "Bugün ne izleyelim?" : "Bu akşam ne izleyelim?" }

    private var heroSentence: String {
        let w = water
        let progress = Double(w.today) / Double(max(w.goal, 1))
        switch heroTopic {
        case 0:
            if progress <= 0 { return "Bugün bir bardak suyla başlamaya ne dersin?" }
            if progress >= 1 { return "Su hedefini tamamladın, harika gidiyorsun." }
            return "Bugün \(YasamText.litres(w.today)) L su içtin, hedefe \(YasamText.litres(w.goal - w.today)) L kaldı."
        case 1: return YasamText.yesterday(ml: w.yesterday, mood: yesterdayMood) ?? "Dün nasıl geçti? Bugün nasıl gidiyor?"
        case 2: return lastMood.map { "En son ruh halin \($0.label) idi. Şimdi nasılsın?" } ?? "Bugün ruh halin nasıl?"
        case 3:
            if let last = books.lastRead {
                if let p = last.percent { return "Kitabın %\(p)'de. Bu akşam birkaç sayfa okumaya ne dersin?" }
                return "\(last.book.title) seni bekliyor."
            }
            return "Rafında yeni bir kitap için zaman var mı?"
        case 4: return "\(mediaPrompt) Sana bir film, dizi ya da oyun önerelim."
        default:
            if let p = journalPreview {
                let short = p.line.count <= 38 ? p.line : String(p.line.prefix(37)).trimmingCharacters(in: .whitespaces) + "…"
                return "En son \"\(short)\" yazmıştın. Bugün neler var?"
            }
            return "Bugünü iki satırla kayda geçirmek ister misin?"
        }
    }

    // MARK: Hero

    private var heroCard: some View {
        let journalsMode = section == .journals
        return ZStack(alignment: .topLeading) {
            YasamHeroSentence(greeting: journalsMode ? "\(journals.count) GİRİŞ" : YasamText.greeting(hour),
                              sentence: journalsMode ? "Journal" : heroSentence,
                              tone: YasamTone.mood(lastMood), hour: hour, revealed: revealed)
            Button(action: close) {
                RealSymbol("chevron.left", size: 19.5).foregroundStyle(.white)
                    .frame(width: 40, height: 40).liquidGlass(Circle(), role: .chrome)
            }
            .padding(.leading, 22).padding(.top, 8)
            .accessibilityLabel(journalsMode ? "Yaşam'a dön" : "Geri")
        }
        .scaleEffect(revealed ? 1 : 0.96)
        .opacity(revealed ? 1 : 0)
        .animation(.spring(duration: 0.56, bounce: 0.3).delay(0.29), value: revealed)
    }

    // MARK: Tiles

    private var tiles: some View {
        let w = water
        let progress = min(Double(w.today) / Double(max(w.goal, 1)), 1)
        return VStack(alignment: .leading, spacing: 0) {
            if tab != 2 {
                YasamWaterHero(todayMl: w.today, progress: progress, revealed: revealed)
                YasamRow(label: "SU", value: "/ \(YasamText.litres(w.goal)) L", expanded: open == "water", index: 0, revealed: revealed,
                         toggle: { toggle("water") }) {
                    Text(progress >= 1 ? "Günlük hedefi tamamladın" : "Hedefe \(YasamText.litres(max(w.goal - w.today, 0))) L kaldı")
                        .font(.system(size: 13, weight: .light)).foregroundStyle(.white.opacity(0.7))
                } content: {
                    Text("BUGÜN \(Int((progress * 100).rounded()))% · 250 ML").font(.system(size: 11, design: .monospaced)).kerning(1).foregroundStyle(YasamTone.muted)
                    Color.clear.frame(height: 12)
                    YasamSlideToAdd(label: "Kaydır · 250 ml su ekle") { life.addDrink(250) }
                    YasamTextAction(text: "SU EKRANINI AÇ  ›", tone: YasamTone.water) { showWater = true }
                    YasamTextAction(text: waterHistoryOpen ? "GEÇMİŞİ GİZLE  ‹" : "GEÇMİŞİ AÇ  ›", tone: YasamTone.muted) {
                        withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.3)) { waterHistoryOpen.toggle() }
                    }
                    if waterHistoryOpen { YasamWaterHistory().transition(.opacity.combined(with: .move(edge: .top))) }
                }
                YasamRow(label: "MOOD", value: lastMood?.label ?? "Henüz yok", expanded: open == "mood", index: 1, revealed: revealed,
                         toggle: { toggle("mood") }) {
                    YasamSpark(points: life.moods.sorted { $0.details.loggedAt < $1.details.loggedAt }.suffix(7).map { YasamText.moodScore($0.mood) },
                               tone: YasamTone.moodDefault)
                } content: {
                    YasamTextAction(text: "MOOD EKLE  +", tone: YasamTone.moodDefault) { showMoodAdd = true }
                    YasamTextAction(text: moodHistoryOpen ? "GEÇMİŞİ GİZLE  ‹" : "GEÇMİŞİ AÇ  ›", tone: YasamTone.muted) {
                        withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.3)) { moodHistoryOpen.toggle() }
                    }
                    if moodHistoryOpen { YasamMoodHistory(onAdd: { showMoodAdd = true }, onMore: { showMoodHistory = true }).transition(.opacity.combined(with: .move(edge: .top))) }
                }
                YasamRow(label: "GÜNLÜKLERİM", value: "Bugün yaz", expanded: open == "journal", index: 2, revealed: revealed,
                         toggle: { toggle("journal") }) {
                    if let p = journalPreview {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(p.when).font(.system(size: 10, design: .monospaced)).kerning(1.2).foregroundStyle(YasamTone.muted)
                            Text(p.line).font(.system(size: 14, weight: .light)).foregroundStyle(.white.opacity(0.85)).lineLimit(2)
                        }
                    } else {
                        Text("Bugün ne düşünüyorsun?").font(.system(size: 13, weight: .light)).foregroundStyle(.white.opacity(0.7))
                    }
                } content: {
                    YasamTextAction(text: "BUGÜN YAZ  +", tone: YasamTone.journal, action: newJournal)
                    YasamTextAction(text: "GÜNLÜKLERİ AÇ  ›", tone: YasamTone.muted) { switchSection(.journals) }
                }
            }
            if tab != 1 {
                let last = books.lastRead
                YasamRow(label: "KİTAP", value: last?.percent.map { "%\($0)" } ?? last?.book.title ?? "Rafını aç",
                         expanded: open == "book", index: 3, revealed: revealed, toggle: { toggle("book") }) {
                    if let last { YasamBookPreview(book: last.book, percent: last.percent) }
                    else { Text("Rafını aç").font(.system(size: 13, weight: .light)).foregroundStyle(.white.opacity(0.7)) }
                } content: {
                    if let last {
                        YasamTextAction(text: "OKUMAYA DEVAM ET  ›", tone: YasamTone.book) { resumeBook = last.book.id; showBooks = true }
                        YasamTextAction(text: "RAFI AÇ  ›", tone: YasamTone.muted) { resumeBook = nil; showBooks = true }
                    } else {
                        YasamTextAction(text: "RAFINI AÇ  ›", tone: YasamTone.book) { resumeBook = nil; showBooks = true }
                    }
                }
                Rectangle().fill(.white.opacity(0.10)).frame(height: 1)
                YasamDiscoverGrid(
                    watchCount: discovery.snapshot(.film).later.count, playCount: discovery.snapshot(.game).later.count,
                    listenCount: MusicDiscoveryStore.shared.snapshot.list.count,
                    watchArt: discovery.snapshot(.film).later.compactMap(\.media.artworkUrl),
                    playArt: discovery.snapshot(.game).later.compactMap(\.media.artworkUrl),
                    listenArt: MusicDiscoveryStore.shared.snapshot.list.reversed().compactMap(\.music.artworkUrl),
                    ratings: MediaRatingStore.shared.entries,
                    open: { kind, list in
                        switch kind {
                        case 0: openDiscovery(.film, list: list)
                        case 1: openDiscovery(.game, list: list)
                        case 2: withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.42)) { musicTarget = list }
                        default: withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.42)) { showRatings = true }
                        }
                    })
                .yasamEnter(revealed, delay: 0.48 + 4 * 0.055, y: 14)
            }
            if tab == 1 {
                Rectangle().fill(.white.opacity(0.10)).frame(height: 1)
            }
        }
        .padding(.horizontal, 22).padding(.top, 20)
        .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.32), value: open)
        .animation(.easeOut(duration: 0.22), value: tab)
    }

    private func openDiscovery(_ kind: DiscoveryKind, list: Bool) {
        withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.42)) { discoveryTarget = (kind, list) }
    }

    private var musicCount: String {
        let n = MusicDiscoveryStore.shared.snapshot.list.count
        return n > 0 ? " · \(n)" : ""
    }

    private func listCount(_ kind: DiscoveryKind) -> String {
        let n = discovery.snapshot(kind).later.count
        return n > 0 ? " · \(n)" : ""
    }

    private func toggle(_ key: String) {
        open = open == key ? nil : key
        if open != "mood" { moodHistoryOpen = false }
        if open != "water" { waterHistoryOpen = false }
    }

    /// "BUGÜN YAZ" opens YasamJournalSheet (Android); saving adds a "Günlük · d MMMM" journal card.
    private func newJournal() { showJournalSheet = true }

    // MARK: Journals mode

    private var journalList: some View {
        VStack(alignment: .leading, spacing: 0) {
            YasamTextAction(text: "BUGÜN YAZ  +", tone: YasamTone.journal, action: newJournal).yasamEnter(revealed, delay: 0.3, y: 14)
            if journals.isEmpty {
                Text("Henüz günlük yok").font(.system(size: 19, weight: .light)).foregroundStyle(.white.opacity(0.85)).padding(.vertical, 12)
            }
            ForEach(Array(journals.prefix(60).enumerated()), id: \.element.id) { index, r in
                if let j = r.journal {
                    VStack(alignment: .leading, spacing: 0) {
                        Rectangle().fill(.white.opacity(0.10)).frame(height: 1)
                        Button { onOpenCard(r.id) } label: {
                            VStack(alignment: .leading, spacing: 5) {
                                HStack(spacing: 8) {
                                    Text(YasamText.whenLabel(j.createdAt)).font(.system(size: 10, design: .monospaced)).kerning(1.2).foregroundStyle(YasamTone.muted)
                                    if let m = j.mood { Circle().fill(YasamTone.mood(m)).frame(width: 7, height: 7) }
                                }
                                let text = j.text.trimmingCharacters(in: .whitespacesAndNewlines)
                                if !text.isEmpty {
                                    Text(text).font(.system(size: 14, weight: .light)).foregroundStyle(.white.opacity(0.88)).lineLimit(2).multilineTextAlignment(.leading)
                                }
                            }
                            .padding(.vertical, 14)
                            .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    .yasamEnter(revealed, delay: 0.3 + Double(min(index + 1, 12)) * 0.055, y: 14)
                }
            }
            Rectangle().fill(.white.opacity(0.10)).frame(height: 1)
        }
        .padding(.horizontal, 22).padding(.top, 12)
    }

    private var micButton: some View {
        // YasamMic → onComposerMicClick: Yaşam closes and the Home dock starts voice capture.
        Button {
            close()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { NotificationCenter.default.post(name: .realStartVoice, object: nil) }
        } label: {
            RealSymbol("waveform", size: 23).foregroundStyle(.white)
                .frame(width: 64, height: 64).liquidGlass(Circle())
        }
        .padding(.top, 8).padding(.bottom, 34)
        .accessibilityLabel("Sesli komut")
    }
}

// MARK: - Pieces

enum YasamTone {
    static let water = Color(argb: 0xFF6FD6FF)
    static let moodDefault = Color(argb: 0xFFB59BFF)
    static let book = Color(argb: 0xFFFFB86F)
    static let journal = Color(argb: 0xFFFF8FB1)
    static let media = Color(argb: 0xFF7BE0A4)
    static let muted = Color(argb: 0xFF9A9AA2)

    static func mood(_ m: JournalMood?) -> Color {
        if let m { return YasamTone.legacy(m) }
        return moodDefault
    }

    static func legacy(_ m: JournalMood?) -> Color {
        switch m {
        case nil, .good?, .neutral?: return moodDefault
        case .veryGood?, .happy?, .excited?: return Color(argb: 0xFFFFB86F)
        case .calm?: return Color(argb: 0xFF6FD6FF)
        case .tired?, .bored?, .low?: return Color(argb: 0xFF8A94B8)
        case .hard?, .bad?, .sad?: return Color(argb: 0xFF6F8FFF)
        case .stressed?, .angry?, .fuckOff?: return Color(argb: 0xFFFF8F8F)
        }
    }
}

/// YasamSentence: greeting, litres, yesterday summary, mood scores.
enum YasamText {
    static func greeting(_ hour: Int) -> String {
        switch hour { case 5...10: return "Günaydın"; case 11...17: return "İyi günler"; default: return "İyi akşamlar" }
    }

    static func litres(_ ml: Int) -> String {
        let text = String(format: "%.1f", Double(ml) / 1000).replacingOccurrences(of: ".", with: ",")
        return text.hasSuffix(",0") ? String(text.dropLast(2)) : text
    }

    static func moodPhrase(_ m: JournalMood) -> String {
        switch m {
        case .veryGood: return "çok iyiydi"
        case .good: return "iyiydi"
        case .neutral: return "normaldi"
        case .hard, .fuckOff: return "zordu"
        case .bad: return "kötüydü"
        case .happy: return "mutluydu"
        case .excited: return "heyecanlıydı"
        case .calm: return "sakindi"
        case .tired: return "yorgundu"
        case .bored: return "sıkkındı"
        case .stressed: return "stresliydi"
        case .angry: return "sinirliydi"
        case .sad: return "üzgündü"
        case .low: return "keyifsizdi"
        }
    }

    static func yesterday(ml: Int, mood: JournalMood?) -> String? {
        let water: String? = ml > 0 ? (ml < 1000 ? "Dün \(ml) ml su içtin" : "Dün \(litres(ml)) L su içtin") : nil
        let moodText = mood.map { "ruh halin \(moodPhrase($0))" }
        switch (water, moodText) {
        case let (w?, m?): return "\(w),\n\(m)."
        case let (w?, nil): return "\(w)."
        case let (nil, m?): return "Dün \(m)."
        default: return nil
        }
    }

    static func moodScore(_ m: JournalMood) -> Double {
        switch m {
        case .veryGood: return 5
        case .happy, .excited: return 4.5
        case .good: return 4
        case .calm: return 3.5
        case .neutral: return 3
        case .bored, .tired: return 2.5
        case .low, .hard: return 2
        case .stressed, .sad, .angry: return 1.5
        case .bad, .fuckOff: return 1
        }
    }

    /// "BUGÜN · 14:20" / "DÜN · 23:10" / "3 EKİ"
    static func whenLabel(_ millis: EpochMillis) -> String {
        let cal = Calendar.real
        let d = millis.date
        let days = cal.dateComponents([.day], from: cal.startOfDay(for: d), to: cal.startOfDay(for: Date())).day ?? 0
        switch days {
        case 0: return "BUGÜN · \(RealDates.time(d))"
        case 1: return "DÜN · \(RealDates.time(d))"
        default:
            let months = ["OCA", "ŞUB", "MAR", "NİS", "MAY", "HAZ", "TEM", "AĞU", "EYL", "EKİ", "KAS", "ARA"]
            let c = cal.dateComponents([.day, .month], from: d)
            return "\(c.day ?? 1) \(months[(c.month ?? 1) - 1])"
        }
    }
}

extension View {
    /// realEntrance: fade + slide (+ scale) after a delay, driven by one reveal flag.
    func yasamEnter(_ visible: Bool, delay: Double, y: CGFloat = 8, scale: CGFloat = 1) -> some View {
        opacity(visible ? 1 : 0)
            .offset(y: visible ? 0 : y)
            .scaleEffect(visible ? 1 : scale)
            .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.46).delay(delay), value: visible)
    }
}

/// Gradient card that "breathes" (7s), tinted by the last mood and the time of day; words sharpen one by one.
struct YasamHeroSentence: View {
    let greeting: String
    let sentence: String
    let tone: Color
    let hour: Int
    let revealed: Bool
    var compact = false
    @State private var breathe = false

    var body: some View {
        let ink = Color(argb: 0xFF05080C)
        let bottom: Color = hour < 12 ? Color(argb: 0xFFE9B79B) : (hour < 18 ? Color(argb: 0xFF9AA7B4) : Color(argb: 0xFF6B5B95))
        let b: Double = breathe ? 1 : 0
        // "\n" in a sentence is an intentional line break (YasamSentence); it becomes a break token for WordFlow.
        let words: [String] = sentence.components(separatedBy: "\n").enumerated().flatMap { i, line in
            (i > 0 ? [WordFlow.lineBreak] : []) + line.split(separator: " ").map(String.init)
        }
        VStack(spacing: 0) {
            if !compact {
                Text(greeting.uppercased(with: Locale(identifier: "tr_TR")))
                    .font(.system(size: 11, design: .monospaced)).kerning(1.6).foregroundStyle(.white.opacity(0.6))
                    .multilineTextAlignment(.center)
                Color.clear.frame(height: 18)
            }
            WordFlow(spacing: 8, lineSpacing: 2) {
                ForEach(Array(words.enumerated()), id: \.offset) { i, word in
                    if word == WordFlow.lineBreak {
                        Color.clear.frame(width: 0, height: 0).layoutValue(key: WordFlowBreak.self, value: true)
                    } else {
                    Text(word)
                        .font(.system(size: 25, weight: .light))
                        .foregroundStyle(.white.opacity(0.94))
                        .blur(radius: revealed ? 0 : 8)
                        .opacity(revealed ? 1 : 0)
                        .offset(y: revealed ? 0 : 6)
                        .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.5).delay(0.45 + Double(i) * 0.045), value: revealed)
                    }
                }
            }
        }
        .padding(.horizontal, 26)
        .padding(.top, (compact ? 14 : 26) + (compact ? 0 : 20))
        .padding(.bottom, compact ? 14 : 26)
        .frame(maxWidth: .infinity, minHeight: compact ? 110 : 220)
        .background {
            LinearGradient(stops: [
                .init(color: ink, location: 0),
                .init(color: ink.lerp(tone, 0.5), location: 0.42 + 0.12 * b),
                .init(color: ink.lerp(bottom, 0.55 + 0.1 * b), location: 1),
            ], startPoint: .top, endPoint: .bottom)
        }
        .clipShape(RoundedRectangle(cornerRadius: 32, style: .continuous))
        .padding(.horizontal, 14)
        .onAppear { withAnimation(.easeInOut(duration: 7).repeatForever(autoreverses: true)) { breathe = true } }
    }
}

/// Centred flowing layout for the hero words (FlowRow with centred arrangement).
struct WordFlowBreak: LayoutValueKey { static let defaultValue = false }

struct WordFlow: Layout {
    static let lineBreak = "\u{2028}"
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 2

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(subviews, width: proposal.width ?? .infinity)
        let height = rows.reduce(0) { $0 + $1.height } + CGFloat(max(rows.count - 1, 0)) * lineSpacing
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(subviews, width: bounds.width) {
            var x = bounds.minX + (bounds.width - row.width) / 2
            for index in row.items {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y + (row.height - size.height) / 2), proposal: .unspecified)
                x += size.width + spacing
            }
            y += row.height + lineSpacing
        }
    }

    private struct Row { var items: [Int] = []; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func arrange(_ subviews: Subviews, width: CGFloat) -> [Row] {
        var rows: [Row] = [Row()]
        for (i, sub) in subviews.enumerated() {
            if sub[WordFlowBreak.self] {
                if !rows[rows.count - 1].items.isEmpty { rows.append(Row()) }
                continue
            }
            let size = sub.sizeThatFits(.unspecified)
            let needed = rows[rows.count - 1].items.isEmpty ? size.width : rows[rows.count - 1].width + spacing + size.width
            if needed > width && !rows[rows.count - 1].items.isEmpty { rows.append(Row()) }
            var row = rows[rows.count - 1]
            row.width = row.items.isEmpty ? size.width : row.width + spacing + size.width
            row.height = max(row.height, size.height)
            row.items.append(i)
            rows[rows.count - 1] = row
        }
        return rows
    }
}

/// Three icon tabs (Hepsi · Kayıtlar · Keşfet) with a sliding 28pt underline.
struct YasamIconTabs: View {
    @Binding var tab: Int
    var body: some View {
        GeometryReader { geo in
            let cell = geo.size.width / 3
            ZStack(alignment: .bottomLeading) {
                HStack(spacing: 0) {
                    ForEach(0..<3, id: \.self) { i in
                        Button { tab = i } label: {
                            glyph(i).opacity(i == tab ? 1 : 0.4).frame(maxWidth: .infinity, minHeight: 48)
                        }
                        .accessibilityLabel(["Hepsi", "Kayıtlar", "Keşfet"][i])
                        .accessibilityAddTraits(i == tab ? .isSelected : [])
                    }
                }
                Rectangle().fill(.white).frame(width: 28, height: 1.5)
                    .offset(x: cell * CGFloat(tab) + (cell - 28) / 2)
                    .animation(.spring(response: 0.3, dampingFraction: 0.8), value: tab)
            }
        }
        .frame(height: 50)
        .padding(.top, 14).padding(.bottom, 4)
    }

    @ViewBuilder
    private func glyph(_ i: Int) -> some View {
        switch i {
        case 0:
            VStack(spacing: 6) {
                HStack(spacing: 6) { Circle().frame(width: 4.4, height: 4.4); Circle().frame(width: 4.4, height: 4.4) }
                HStack(spacing: 6) { Circle().frame(width: 4.4, height: 4.4); Circle().frame(width: 4.4, height: 4.4) }
            }
            .foregroundStyle(.white).frame(width: 20, height: 20)
        case 1:
            HStack(alignment: .bottom, spacing: 4.5) {
                Capsule().frame(width: 1.5, height: 9)
                Capsule().frame(width: 1.5, height: 19)
                Capsule().frame(width: 1.5, height: 13)
            }
            .foregroundStyle(.white).frame(width: 20, height: 20, alignment: .bottom)
        default:
            ZStack { Circle().stroke(.white, lineWidth: 1.5).frame(width: 17, height: 17); Circle().fill(.white).frame(width: 4, height: 4) }
                .frame(width: 20, height: 20)
        }
    }
}

/// "SU · BUGÜN" giant thin figure + 36 rising bars lit up to today's progress.
struct YasamWaterHero: View {
    let todayMl: Int
    let progress: Double
    let revealed: Bool
    @State private var shown: Double = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("SU · BUGÜN").font(.system(size: 11, design: .monospaced)).kerning(1.6).foregroundStyle(YasamTone.muted)
            HStack(alignment: .lastTextBaseline, spacing: 0) {
                Text(String(format: "%.1f", shown / 1000).replacingOccurrences(of: ".", with: ","))
                    .font(.system(size: 48, weight: .light)).foregroundStyle(.white)
                    .contentTransition(.numericText())
                Text(" L").font(RealFont.inter(20)).foregroundStyle(YasamTone.muted)
            }
            GeometryReader { geo in
                let n = 36
                let gap = geo.size.width / CGFloat(n)
                ZStack(alignment: .bottomLeading) {
                    ForEach(0..<n, id: \.self) { k in
                        let curve = 0.28 + 0.72 * pow(Double(k) / Double(n - 1), 1.6)
                        Capsule()
                            .fill(Double(k) < progress * Double(n) ? YasamTone.water : Color.white.opacity(0.14))
                            .frame(width: 1.6, height: geo.size.height * curve * (revealed ? 1 : 0))
                            .offset(x: gap * CGFloat(k) + gap / 2)
                            .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.52).delay(0.38 + Double(k) * 0.014), value: revealed)
                    }
                }
                .frame(maxHeight: .infinity, alignment: .bottom)
            }
            .frame(height: 44)
            .padding(.top, 10)
        }
        .padding(.bottom, 12)
        .yasamEnter(revealed, delay: 0.3, y: 12)
        .onAppear { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.9).delay(0.3)) { shown = Double(todayMl) } }
        .onChange(of: todayMl) { _, v in withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.9)) { shown = Double(v) } }
    }
}

/// Hairline-separated row: mono label, thin value, rotating chevron, always-visible preview, expandable actions.
/// YasamBookPreview: the book's main colour as a small box plus a thin progress bar.
struct YasamBookPreview: View {
    let book: Book
    let percent: Int?
    @Environment(BookStore.self) private var books

    var body: some View {
        let base = books.coverImage(book).flatMap(CoverPalette.dominant) ?? coverBaseColor(book)
        let tone = base.luminance < 0.25 ? base.blend(.white, by: 0.35) : base
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 5)
                .fill(LinearGradient(colors: [tone, tone.blend(.black, by: 0.2)], startPoint: .top, endPoint: .bottom))
                .frame(width: 34, height: 48)
            VStack(alignment: .leading, spacing: 8) {
                Text(book.author.isEmpty ? book.title : "\(book.title) by \(book.author)")
                    .font(.system(size: 13, weight: .light)).foregroundStyle(.white.opacity(0.85)).lineLimit(2)
                if let percent {
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 2).fill(.white.opacity(0.10))
                            RoundedRectangle(cornerRadius: 2).fill(tone).frame(width: g.size.width * CGFloat(min(max(percent, 0), 100)) / 100)
                        }
                    }
                    .frame(height: 4)
                }
            }
        }
    }
}

extension Color {
    /// Linear sRGB mix (Compose lerp equivalent).
    func blend(_ other: Color, by t: Double) -> Color {
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        UIColor(self).getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        UIColor(other).getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        let t = CGFloat(t)
        return Color(red: r1 + (r2 - r1) * t, green: g1 + (g2 - g1) * t, blue: b1 + (b2 - b1) * t, opacity: a1 + (a2 - a1) * t)
    }
}

struct YasamRow<Preview: View, Content: View>: View {
    let label: String
    let value: String
    let expanded: Bool
    let index: Int
    let revealed: Bool
    let toggle: () -> Void
    @ViewBuilder let preview: Preview
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Rectangle().fill(.white.opacity(0.10)).frame(height: 1)
            Button(action: toggle) {
                HStack(spacing: 0) {
                    Text(label).font(.system(size: 12, design: .monospaced)).kerning(1.2).foregroundStyle(YasamTone.muted).lineLimit(1)
                    Spacer()
                    Text(value).font(.system(size: 15, weight: .light)).foregroundStyle(.white).lineLimit(1)
                    RealSymbol("chevron.down", size: 12.6).foregroundStyle(.white.opacity(0.6))
                        .rotationEffect(.degrees(expanded ? 180 : 0))
                        .frame(width: 14).padding(.leading, 14)
                }
                .frame(minHeight: 56)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(expanded ? "Açık" : "Kapalı")
            preview.padding(.bottom, 16)
            if expanded {
                VStack(alignment: .leading, spacing: 0) { content }
                    .padding(.bottom, 14)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .yasamEnter(revealed, delay: 0.48 + Double(index) * 0.055, y: 14)
    }
}

struct YasamTextAction: View {
    let text: String
    let tone: Color
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(text).font(.system(size: 12, design: .monospaced)).kerning(1.2).foregroundStyle(tone)
                .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// "Slide to add": drag the white knob past 85% to log 250 ml; springs back.
struct YasamSlideToAdd: View {
    let label: String
    let onDone: () -> Void
    @State private var drag: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            let maxX = max(geo.size.width - 44 - 8, 1)
            let fraction = min(max(drag / maxX, 0), 1)
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.07))
                Text(label).font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.6 * (1 - fraction)))
                    .frame(maxWidth: .infinity).padding(.leading, 22)
                RealSymbol("arrow.right", size: 16.1).foregroundStyle(.black)
                    .frame(width: 44, height: 44).background(Circle().fill(.white))
                    .padding(4)
                    .offset(x: drag)
                    .gesture(DragGesture()
                        .onChanged { drag = min(max($0.translation.width, 0), maxX) }
                        .onEnded { _ in
                            if drag > maxX * 0.85 { UIImpactFeedbackGenerator(style: .medium).impactOccurred(); onDone() }
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) { drag = 0 }
                        })
            }
        }
        .frame(height: 52)
        .accessibilityElement()
        .accessibilityLabel(label)
        .accessibilityAction { onDone() }
    }
}

/// Mini mood line (last 7 scores); a faint baseline with fewer than two points.
struct YasamSpark: View {
    let points: [Double]
    let tone: Color

    var body: some View {
        Canvas { ctx, size in Self.draw(&ctx, size: size, points: points, tone: tone) }
            .frame(height: 34)
    }

    static func curve(_ pts: [CGPoint]) -> Path {
        var path = Path()
        guard let first = pts.first else { return path }
        path.move(to: first)
        var i = 1
        while i < pts.count {
            let mid = (pts[i - 1].x + pts[i].x) / 2
            path.addCurve(to: pts[i], control1: CGPoint(x: mid, y: pts[i - 1].y), control2: CGPoint(x: mid, y: pts[i].y))
            i += 1
        }
        return path
    }

    private static func draw(_ ctx: inout GraphicsContext, size: CGSize, points: [Double], tone: Color) {
        let style = StrokeStyle(lineWidth: 2, lineCap: .round)
        if points.count < 2 {
            var p = Path()
            p.move(to: CGPoint(x: 2, y: size.height / 2))
            p.addLine(to: CGPoint(x: size.width - 2, y: size.height / 2))
            ctx.stroke(p, with: .color(tone.opacity(0.25)), style: style)
            return
        }
        let step: CGFloat = (size.width - 4) / CGFloat(points.count - 1)
        var pts: [CGPoint] = []
        for (i, v) in points.enumerated() {
            let norm: CGFloat = CGFloat(1 - (v - 1) / 4)
            let y: CGFloat = 3 + norm * (size.height - 6)
            pts.append(CGPoint(x: 2 + CGFloat(i) * step, y: y))
        }
        ctx.stroke(curve(pts), with: .color(tone), style: style)
    }
}

/// Mood row history: one continuous line of the last 20 moods, 5 per screen width, newest on the right.
struct YasamMoodHistory: View {
    @Environment(LifeStore.self) private var life
    let onAdd: () -> Void
    var onMore: () -> Void = {}
    @State private var drawn: CGFloat = 0

    var body: some View {
        let points = Array(life.moods.sorted { $0.details.loggedAt > $1.details.loggedAt }.prefix(20).reversed())
        VStack(alignment: .leading, spacing: 0) {
            if points.isEmpty {
                Text("Henüz mood yok").font(.system(size: 13, weight: .light)).foregroundStyle(.white.opacity(0.7)).padding(.vertical, 8)
                YasamTextAction(text: "MOOD EKLE  +", tone: YasamTone.moodDefault, action: onAdd)
            } else {
                GeometryReader { geo in
                    let width = geo.size.width * CGFloat(max(points.count, 5)) / 5
                    ScrollView(.horizontal, showsIndicators: false) {
                        VStack(spacing: 6) {
                            Canvas { ctx, size in Self.drawChart(&ctx, size: size, points: points, drawn: drawn) }
                            .frame(height: 60)
                            HStack(spacing: 0) {
                                ForEach(points) { e in
                                    VStack(spacing: 2) {
                                        Text(YasamText.whenLabel(e.details.loggedAt).components(separatedBy: " · ").first ?? "")
                                            .font(.system(size: 10, design: .monospaced)).foregroundStyle(YasamTone.muted).lineLimit(1)
                                        Text(e.mood.label).font(.system(size: 11, design: .monospaced)).foregroundStyle(.white.opacity(0.85)).lineLimit(1)
                                    }
                                    .frame(maxWidth: .infinity)
                                }
                            }
                        }
                        .frame(width: width)
                    }
                    .defaultScrollAnchor(.trailing)
                }
                .frame(height: 100)
                YasamTextAction(text: "DAHA FAZLA  ›", tone: YasamTone.muted, action: onMore)
            }
        }
        .padding(.top, 4)
        .onAppear { withAnimation(.linear(duration: 0.5)) { drawn = 1 } }
    }
}

extension YasamMoodHistory {
    static func drawChart(_ ctx: inout GraphicsContext, size: CGSize, points: [MoodTrackerEntry], drawn: CGFloat) {
        let n = points.count
        guard n > 0 else { return }
        let cell: CGFloat = size.width / CGFloat(n)
        var pts: [CGPoint] = []
        for (i, e) in points.enumerated() {
            let norm: CGFloat = CGFloat(1 - (YasamText.moodScore(e.mood) - 1) / 4)
            let y: CGFloat = 8 + norm * (size.height - 16)
            pts.append(CGPoint(x: cell * (CGFloat(i) + 0.5), y: y))
        }
        var base = Path()
        base.move(to: CGPoint(x: 0, y: size.height / 2))
        base.addLine(to: CGPoint(x: size.width, y: size.height / 2))
        ctx.stroke(base, with: .color(Color.white.opacity(0.1)), lineWidth: 1)
        if n > 1 {
            let path = YasamSpark.curve(pts).trimmedPath(from: 0, to: drawn)
            ctx.stroke(path, with: .color(Color.white.opacity(0.55)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
        }
        for i in 0..<n {
            let reach: CGFloat = CGFloat(i) / CGFloat(max(n - 1, 1))
            guard reach <= drawn + 0.01 else { continue }
            let rect = CGRect(x: pts[i].x - 3.5, y: pts[i].y - 3.5, width: 7, height: 7)
            ctx.fill(Path(ellipseIn: rect), with: .color(YasamTone.mood(points[i].mood)))
        }
    }
}

/// Water row history: daily totals of the last 20 days against each day's frozen goal.
struct YasamWaterHistory: View {
    @Environment(LifeStore.self) private var life

    var body: some View {
        let cal = Calendar.real
        let days = (0..<20).reversed().map { cal.date(byAdding: .day, value: -$0, to: Date())! }
        let values = days.map { (day: $0, ml: life.total(on: $0), goal: life.goal(on: $0)) }
        let maxValue = max(values.map { max($0.ml, $0.goal) }.max() ?? 1, 1)
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .bottom, spacing: 4) {
                ForEach(values, id: \.day) { v in
                    VStack(spacing: 0) {
                        Spacer(minLength: 0)
                        RoundedRectangle(cornerRadius: 2)
                            .fill(v.ml >= v.goal ? YasamTone.water : YasamTone.water.opacity(v.ml > 0 ? 0.5 : 0.12))
                            .frame(height: max(3, 56 * CGFloat(v.ml) / CGFloat(maxValue)))
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 60)
            HStack {
                Text("20 GÜN ÖNCE").font(.system(size: 10, design: .monospaced)).foregroundStyle(YasamTone.muted)
                Spacer()
                Text("BUGÜN").font(.system(size: 10, design: .monospaced)).foregroundStyle(YasamTone.muted)
            }
        }
        .padding(.vertical, 8)
    }
}
