import SwiftUI
import UIKit

/// Me (Yaşam; HomeWidgetContent.kt + Yasam*.kt): one screen, one rule. Numbers are the "Bugün" tiles (adım, kahve,
/// uyku, ruh hali, su, alkol), lists are the Keşfet tiles (izle, oyna, dinle, oku, puanla), writing lives in Günlüklerim.
/// Hold any tile to lift it: drag to reorder, Tam / Yarım to resize, "–" to hide; "Bitti" ends editing.
/// Me slides in from the right; every screen opened from a tile comes in fast from the right with a small lift and
/// leaves the same way, Me staying still underneath; coming back the tiles play their entrance again.
struct YasamScreen: View {
    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life
    @Environment(BookStore.self) private var books
    @Environment(DiscoveryStore.self) private var discovery
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var onOpenCard: (Int64) -> Void = { _ in }
    /// Opens straight into one of Me's screens (CI screenshots, deep links).
    var initialScreen: MeScreen? = nil

    enum MeScreen: String, Identifiable {
        case steps, coffee, alcohol, journal, water, moods, books
        var id: String { rawValue }
    }

    @State private var shown = false
    @State private var tilesShown = false
    @State private var editing = false
    @State private var meScreen: MeScreen?
    @State private var resumeBook: String?
    @State private var discoveryTarget: (kind: DiscoveryKind, list: Bool)?
    @State private var musicTarget: Bool?
    @State private var showRatings = false
    @State private var showLayout = false
    @State private var showMoodAdd = false
    @State private var showJournalSheet = false
    @State private var showPick = !YasamLayout.shared.picked
    @State private var entry: YasamPart?
    @State private var heroTopic = Int.random(in: 0..<6)
    /// Bumped when a Me screen changes data (steps, coffee…), so the tiles read again.
    @State private var tick = 0

    private let hour = Calendar.real.component(.hour, from: Date())
    static let openAnim = Animation.timingCurve(0.16, 1, 0.3, 1, duration: 0.34)
    static let closeAnim = Animation.timingCurve(0.16, 1, 0.3, 1, duration: 0.42)

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.black.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        PullToCloseMarker()
                        YasamMeHeader(greeting: YasamText.greeting(hour), bits: summaryBits, fallback: heroSentence, onBack: close)
                            .padding(.top, 8)
                            .yasamEnter(shown, delay: 0.04, y: 10)
                        tiles
                        Color.clear.frame(height: 60)
                    }
                }
                .scrollIndicators(.hidden)
                .allowsHitTesting(meScreen == nil)
            }
            .offset(x: reduceMotion ? 0 : (shown ? 0 : geo.size.width))
        }
        .overlay { overlays }
        .presentationBackground(.clear)
        .onAppear {
            MediaRatingStore.shared.importWatched(discovery.film.watched)
            withAnimation(reduceMotion ? nil : .timingCurve(0.16, 1, 0.3, 1, duration: 0.42)) { shown = true }
            replayTiles()
            if let initialScreen { meScreen = initialScreen }
        }
        .fullScreenCover(isPresented: $showMoodAdd, onDismiss: { tick += 1 }) { MoodAddSheet().liftedTopSafeArea() }
        .sheet(isPresented: $showJournalSheet) {
            YasamJournalSheet { text, styles in
                _ = store.addReminder(text: YasamJournalSheet.title()) { r in
                    r.journal = ReminderJournal(mood: nil, text: text, createdAt: .now, updatedAt: .now, styles: styles)
                }
                return true
            }
        }
        .sheet(isPresented: $showLayout) { YasamLayoutSheet() }
        .sheet(item: $entry) { part in
            if part == .steps {
                YasamNumberSheet(title: t("Bugün kaç adım?"), initial: YasamManual.steps(), step: 500, max: 100_000,
                                 quick: [("3.000", 3000), ("6.000", 6000), ("10.000", 10000)], format: YasamManual.stepsText) {
                    YasamManual.setSteps($0); tick += 1
                }
            } else {
                YasamNumberSheet(title: t("Dün gece ne kadar uyudun?"), initial: YasamManual.sleep() > 0 ? YasamManual.sleep() : 7 * 60,
                                 step: 15, max: 16 * 60, quick: [("6s", 360), ("7s", 420), ("8s", 480)], format: YasamManual.sleepText) {
                    YasamManual.setSleep($0); tick += 1
                }
            }
        }
    }

    // MARK: Overlays (Me's own screens, Keşfet, Puanla, first-run picker)

    @ViewBuilder
    private var overlays: some View {
        ZStack {
            if let screen = meScreen {
                meScreenView(screen)
                    .transition(Self.meTransition(reduceMotion))
                    .zIndex(3)
            }
            if let target = discoveryTarget {
                DiscoveryScreen(kind: target.kind, startInList: target.list) { closeScreen { discoveryTarget = nil } }
                    .transition(Self.meTransition(reduceMotion))
                    .zIndex(4)
            }
            if showRatings {
                RatingsScreen { closeScreen { showRatings = false } }
                    .transition(Self.meTransition(reduceMotion))
                    .zIndex(4)
            }
            if let list = musicTarget {
                MusicDiscoveryScreen(startInList: list) { closeScreen { musicTarget = nil } }
                    .transition(Self.meTransition(reduceMotion))
                    .zIndex(4)
            }
            if showPick {
                YasamPickScreen { withAnimation(.easeOut(duration: 0.25)) { showPick = false }; replayTiles() }
                    .transition(.opacity)
                    .zIndex(6)
            }
        }
    }

    /// MeMotion: in from the right with a small lift (340 ms), out to the right with a fade (420 ms).
    static func meTransition(_ reduce: Bool) -> AnyTransition {
        reduce ? .opacity : .asymmetric(
            insertion: .move(edge: .trailing).combined(with: .scale(scale: 0.94)).combined(with: .opacity).animation(openAnim),
            removal: .move(edge: .trailing).combined(with: .opacity).animation(closeAnim))
    }

    @ViewBuilder
    private func meScreenView(_ screen: MeScreen) -> some View {
        let back = { closeScreen { meScreen = nil } }
        switch screen {
        case .steps: YasamStepsScreen(onClose: back)
        case .coffee: YasamCoffeeScreen(onClose: back)
        case .alcohol: YasamAlcoholScreen(onClose: back)
        case .journal: YasamJournalScreen(onClose: back, onWrite: newJournal, onOpen: onOpenCard)
        case .water: WaterTrackerScreen(lockedBeverage: .water, onClose: back).background(Color.black.ignoresSafeArea())
        case .moods: MoodHistoryScreen(onAdd: { showMoodAdd = true }, onClose: back)
        case .books: BooksModuleScreen(resumeBookId: resumeBook, onClose: back).background(Color.black.ignoresSafeArea())
        }
    }

    private func openScreen(_ change: () -> Void) {
        editing = false
        withAnimation(reduceMotion ? nil : Self.openAnim) { change() }
    }

    /// Closing a Me screen: it slides out and the tiles play their entrance again (Android re-composes them).
    private func closeScreen(_ change: () -> Void) {
        withAnimation(reduceMotion ? nil : Self.closeAnim) { change() }
        tick += 1
        replayTiles()
    }

    private func replayTiles() {
        guard !reduceMotion else { tilesShown = true; return }
        var plain = Transaction(); plain.disablesAnimations = true
        withTransaction(plain) { tilesShown = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { tilesShown = true }
    }

    private func close() {
        if editing { withAnimation { editing = false }; return }
        withAnimation(reduceMotion ? nil : .timingCurve(0.16, 1, 0.3, 1, duration: 0.3)) { shown = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + (reduceMotion ? 0 : 0.3)) { withoutAnimation { dismiss() } }
    }

    // MARK: Data

    private var water: (today: Int, goal: Int, yesterday: Int, days: [Int]) {
        let cal = Calendar.real
        let today = Date()
        let days = (0..<14).reversed().map { life.total(on: cal.date(byAdding: .day, value: -$0, to: today)!) }
        return (life.total(on: today), life.goal(on: today), life.total(on: cal.date(byAdding: .day, value: -1, to: today)!), days)
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

    private var coffeeToday: Int {
        _ = tick
        return YasamCoffeeLog.today(YasamCoffeeLog.load()).count + life.entries(on: Date(), beverage: .coffee).count
    }

    private var lastCoffee: CoffeeKind? { _ = tick; return YasamCoffeeLog.load().last?.coffee }

    private var alcohol: (today: Int, week: Int, last: DrinkLogEntry?) {
        _ = tick
        let log = YasamDrinkLog.load()
        let cal = Calendar.real
        let legacyToday = life.entries(on: Date(), beverage: .beer).count
        let legacyWeek = (0..<7).reduce(0) { $0 + life.entries(on: cal.date(byAdding: .day, value: -$1, to: Date())!, beverage: .beer).count }
        return (legacyToday + YasamDrinkLog.today(log).count, legacyWeek + YasamDrinkLog.week(log), log.last)
    }

    private var steps: (today: Int, week: [Int]) { _ = tick; let w = YasamManual.stepsWeek(); return (w.last ?? 0, w) }
    private var sleep: Int { _ = tick; return YasamManual.sleep() }

    /// "6.240 adım", "1,2 L su", "2 kahve", "7s 20d uyku", "1 içki": only what Me shows and what was logged today.
    private var summaryBits: [String] {
        let w = water, a = alcohol, s = steps, sl = sleep, c = coffeeToday
        return YasamLayout.shared.visible.filter(\.counter).compactMap { p in
            switch p {
            case .steps: return s.today > 0 ? t("{0} adım", YasamManual.stepsText(s.today)) : nil
            case .water: return w.today > 0 ? t("{0} L su", YasamText.litres(w.today)) : nil
            case .coffee: return c > 0 ? t("{0} kahve", c) : nil
            case .sleep: return sl > 0 ? t("{0} uyku", YasamManual.sleepText(sl)) : nil
            case .alcohol: return a.today > 0 ? t("{0} içki", a.today) : nil
            default: return nil
            }
        }
    }

    private var heroSentence: String {
        let w = water
        let progress = Double(w.today) / Double(max(w.goal, 1))
        switch heroTopic {
        case 0:
            if progress <= 0 { return t("Bugün bir bardak suyla başlamaya ne dersin?") }
            if progress >= 1 { return t("Su hedefini tamamladın, harika gidiyorsun.") }
            return t("Bugün {0} L su içtin, hedefe {1} L kaldı.", YasamText.litres(w.today), YasamText.litres(w.goal - w.today))
        case 1: return YasamText.yesterday(ml: w.yesterday, mood: yesterdayMood)?.replacingOccurrences(of: "\n", with: " ")
            ?? t("Dün nasıl geçti? Bugün nasıl gidiyor?")
        case 2: return lastMood.map { t("En son ruh halin {0} idi. Şimdi nasılsın?", $0.label) } ?? t("Bugün ruh halin nasıl?")
        case 3:
            if let last = books.lastRead {
                if let p = last.percent { return t("Kitabın %{0}'de. Bu akşam birkaç sayfa okumaya ne dersin?", p) }
                return t("{0} seni bekliyor.", last.book.title)
            }
            return t("Rafında yeni bir kitap için zaman var mı?")
        case 4: return hour < 17 ? t("Bugün ne izleyelim? Sana bir film, dizi ya da oyun önerelim.")
                                 : t("Bu akşam ne izleyelim? Sana bir film, dizi ya da oyun önerelim.")
        default:
            if let p = journalPreview {
                let short = p.line.count <= 38 ? p.line : String(p.line.prefix(37)).trimmingCharacters(in: .whitespaces) + "…"
                return t("En son \"{0}\" yazmıştın. Bugün neler var?", short)
            }
            return t("Bugünü iki satırla kayda geçirmek ister misin?")
        }
    }

    // MARK: Tiles

    private var tiles: some View {
        let layout = YasamLayout.shared
        let parts = layout.visible
        let counters = parts.filter { $0.counter || $0 == .journal }
        let discover = parts.filter(\.discover)
        return VStack(alignment: .leading, spacing: 0) {
            if editing {
                YasamEditBar(hidden: YasamPart.allCases.filter { layout.hidden.contains($0) }) {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { editing = false }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
            if !counters.isEmpty {
                YasamTileGrid(parts: counters, wide: layout.wide, editing: $editing, onTap: tapTile) { part, wide in
                    counterTile(part, wide: wide)
                }
                .yasamEnter(tilesShown, delay: 0.12, y: 14)
            }
            if !discover.isEmpty {
                HStack(alignment: .bottom) {
                    Text(t("KEŞFET")).font(.system(size: 12, design: .monospaced)).kerning(1.2).foregroundStyle(YasamTone.muted)
                    Spacer()
                    Text(t("bugün ne yapsak")).font(.system(size: 12, weight: .light)).foregroundStyle(.white.opacity(0.4))
                }
                .padding(.top, 26).padding(.bottom, 12).padding(.horizontal, 4)
                .yasamEnter(tilesShown, delay: 0.2, y: 10)
                YasamTileGrid(parts: discover, wide: layout.wide, editing: $editing, onTap: tapTile) { part, wide in
                    discoverTile(part, wide: wide)
                }
                .yasamEnter(tilesShown, delay: 0.24, y: 14)
            }
            if parts.isEmpty {
                Text(t("Tüm bölümler gizli.")).font(.system(size: 13, weight: .light)).foregroundStyle(.white.opacity(0.7))
            }
            Button { showLayout = true } label: {
                Text(t("DÜZENLE")).font(.system(size: 11, design: .monospaced)).kerning(1.2).foregroundStyle(YasamTone.muted)
                    .padding(.horizontal, 12).padding(.vertical, 8)
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity)
            .padding(.top, 18)
        }
        .padding(.horizontal, 18).padding(.top, 16)
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: editing)
    }

    private func tapTile(_ part: YasamPart) {
        switch part {
        case .steps: openScreen { meScreen = .steps }
        case .sleep: entry = .sleep
        case .water: openScreen { meScreen = .water }
        case .coffee: openScreen { meScreen = .coffee }
        case .alcohol: openScreen { meScreen = .alcohol }
        case .mood: openScreen { meScreen = .moods }
        case .journal: openScreen { meScreen = .journal }
        case .watch: openScreen { discoveryTarget = (.film, false) }
        case .play: openScreen { discoveryTarget = (.game, false) }
        case .listen: openScreen { musicTarget = false }
        case .book: resumeBook = books.lastRead?.book.id; openScreen { meScreen = .books }
        case .rate: openScreen { showRatings = true }
        }
    }

    private func live(_ f: @escaping () -> Void) -> (() -> Void)? { editing ? nil : f }

    @ViewBuilder
    private func counterTile(_ part: YasamPart, wide: Bool) -> some View {
        let tone = part.glow
        switch part {
        case .journal:
            let n = journals.count
            YasamGlowTile(label: t("Günlüklerim"), tone: tone, value: n > 0 ? "\(n)" : "—", unit: n > 0 ? t("giriş") : nil,
                          sub: journalPreview.map { wide ? "\($0.when) · \(Self.short($0.line))" : $0.when } ?? t("Bir cümle yaz"),
                          wide: wide, onAdd: live(newJournal), addLabel: t("Yeni günlük"))
        case .steps:
            let s = steps
            YasamGlowTile(label: t("Adım"), tone: tone, value: s.today > 0 ? YasamManual.stepsText(s.today) : "—",
                          sub: s.today > 0 ? t("Hedef 10.000 · {0} km", yasamOneDecimal(Double(s.today) * 0.00075)) : t("Dokun, bugünü gir"),
                          wide: wide, onAdd: live { entry = .steps }, addLabel: t("Bugünü gir")) {
                YasamVisuals.progress(Double(s.today) / 10_000, width: wide ? 0 : 110)
            } side: { YasamVisuals.bars(s.week, gapRatio: 0.6) }
        case .sleep:
            let m = sleep
            YasamGlowTile(label: t("Uyku"), tone: tone, value: YasamManual.sleepText(m), sub: m > 0 ? t("Dün gece") : t("Dokun, gir"), wide: wide) {
                YasamVisuals.sleepRange(m, width: wide ? 220 : 110)
            }
        case .water:
            let w = water
            let p = Double(w.today) / Double(max(w.goal, 1))
            YasamGlowTile(label: t("Su"), tone: tone, value: YasamText.litres(w.today), unit: "L",
                          sub: p >= 1 ? t("Hedef tamam") : t("Hedefe {0} L kaldı", YasamText.litres(max(w.goal - w.today, 0))),
                          wide: wide, onAdd: live { life.addDrink(250); tick += 1 }, addLabel: t("250 ml su ekle")) {
                YasamVisuals.progress(p, width: wide ? 0 : 110)
            } side: { YasamVisuals.bars(w.days) }
        case .coffee:
            let c = coffeeToday
            YasamGlowTile(label: t("Kahve"), tone: tone, value: "\(c)", unit: t("fincan"),
                          sub: c == 0 ? t("Bugün henüz yok") : t("≈{0} mg kafein", c * 95), wide: wide,
                          onAdd: live {
                              if let k = lastCoffee { YasamCoffeeLog.add(k); tick += 1; UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                              else { openScreen { meScreen = .coffee } }
                          }, addLabel: t("Kahve ekle")) {
                YasamVisuals.beans(c, slots: wide ? 8 : 4)
            }
        case .alcohol:
            let a = alcohol
            YasamGlowTile(label: t("Alkol"), tone: tone, value: "\(a.today)", unit: t("bugün"), sub: t("Bu hafta {0}", a.week), wide: wide,
                          onAdd: live {
                              if let last = a.last {
                                  YasamDrinkLog.add(YasamDrinkLog.newEntry(last.drink, name: last.name, ml: last.ml,
                                                                          photo: last.photoURL.flatMap { FileManager.default.fileExists(atPath: $0.path) ? last.photo : nil }))
                                  tick += 1
                                  UIImpactFeedbackGenerator(style: .light).impactOccurred()
                              } else { openScreen { meScreen = .alcohol } }
                          }, addLabel: t("Alkol ekle")) {
                YasamVisuals.dots(a.week, slots: wide ? 14 : 7)
            }
        case .mood:
            let points = life.moods.sorted { $0.details.loggedAt < $1.details.loggedAt }.suffix(7).map { YasamText.moodScore($0.mood) }
            YasamGlowTile(label: t("Ruh hali"), tone: tone, value: lastMood?.label ?? "—", sub: t("Geçmişi aç ›"), wide: wide,
                          onAdd: live { showMoodAdd = true }, addLabel: t("Mood ekle")) {
                YasamSpark(points: Array(points), tone: .white).frame(width: wide ? 260 : 120)
            }
        default:
            EmptyView()
        }
    }

    private static func short(_ line: String) -> String { line.count <= 38 ? line : String(line.prefix(37)).trimmingCharacters(in: .whitespaces) + "…" }

    @ViewBuilder
    private func discoverTile(_ part: YasamPart, wide: Bool) -> some View {
        let tone = part.glow
        switch part {
        case .watch:
            let list = discovery.snapshot(.film).later
            let art = list.compactMap(\.media.artworkUrl)
            YasamGlowTile(label: t("İzle"), tone: tone, value: "\(list.count)", unit: t("listende"), sub: Self.listLine(list.count), wide: wide,
                          onSub: live { openScreen { discoveryTarget = (.film, !list.isEmpty) } }, wash: true) {
                if !wide { YasamVisuals.posterStack(art, round: false, w: 22, h: 32) }
            } side: { YasamVisuals.posterStack(art, round: false, w: 40, h: 58) }
        case .play:
            let list = discovery.snapshot(.game).later
            let art = list.compactMap(\.media.artworkUrl)
            YasamGlowTile(label: t("Oyna"), tone: tone, value: "\(list.count)", unit: t("listende"), sub: Self.listLine(list.count), wide: wide,
                          onSub: live { openScreen { discoveryTarget = (.game, !list.isEmpty) } }, wash: true) {
                if !wide { YasamVisuals.posterStack(art, round: false, w: 22, h: 32) }
            } side: { YasamVisuals.posterStack(art, round: false, w: 40, h: 58) }
        case .listen:
            let list = MusicDiscoveryStore.shared.snapshot.list
            let art = list.reversed().compactMap(\.music.artworkUrl)
            YasamGlowTile(label: t("Dinle"), tone: tone, value: "\(list.count)", unit: t("şarkı"), sub: Self.listLine(list.count), wide: wide,
                          onSub: live { openScreen { musicTarget = !list.isEmpty } }, wash: true) {
                if !wide { YasamVisuals.posterStack(art, round: true, w: 26, h: 26) }
            } side: { YasamVisuals.posterStack(art, round: true, w: 44, h: 44) }
        case .book:
            let last = books.lastRead
            YasamGlowTile(label: t("Oku"), tone: tone, value: last?.percent.map { "%\($0)" } ?? "—", sub: last?.book.title ?? t("Rafını aç"), wide: wide,
                          onSub: last == nil ? nil : live { resumeBook = nil; openScreen { meScreen = .books } }, wash: true) {
                GeometryReader { _ in
                    ZStack(alignment: .leading) {
                        Capsule().fill(.white.opacity(0.25))
                        Capsule().fill(.white).frame(width: (wide ? 180 : 110) * CGFloat(last?.percent ?? 0) / 100)
                    }
                }
                .frame(width: wide ? 180 : 110, height: 5)
            }
        case .rate:
            let entries = MediaRatingStore.shared.entries
            let values = entries.compactMap(\.value)
            let films = entries.filter { !$0.series }.count
            let series = entries.count - films
            let parts = [films > 0 ? t("{0} film", films) : nil, series > 0 ? t("{0} dizi", series) : nil].compactMap { $0 }.joined(separator: ", ")
            let line = entries.isEmpty ? t("İzlediklerine puan ver, zevkin ortaya çıksın")
                : (values.count < entries.count ? t("{0} · {1} puan bekliyor", parts, entries.count - values.count) : parts)
            let recent = entries.sorted { ($0.addedAt ?? .min) > ($1.addedAt ?? .min) }.compactMap(\.value).prefix(wide ? 10 : 6)
            let avg = values.isEmpty ? nil : (values.reduce(0, +) / Double(values.count) * 10).rounded() / 10
            YasamGlowTile(label: t("Puanla"), tone: tone, value: avg.map(scoreText) ?? "—", sub: line, wide: wide, wash: true) {
                if !recent.isEmpty {
                    HStack(spacing: 3) {
                        ForEach(Array(recent.enumerated()), id: \.offset) { _, v in
                            RoundedRectangle(cornerRadius: 3).fill(ratingColors(v).bg).frame(width: 10, height: 10)
                        }
                    }
                }
            }
        default:
            EmptyView()
        }
    }

    /// The line under the number is a pill with an arrow: an empty list invites to suggestions, a full one opens the list.
    private static func listLine(_ count: Int) -> String { count == 0 ? t("Öneri al") : t("Listeyi aç") }

    /// "+" on Günlüklerim opens YasamJournalSheet; saving adds a "Günlük · d MMMM" journal card.
    private func newJournal() { showJournalSheet = true }
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
        switch hour { case 5...10: return t("Günaydın"); case 11...17: return t("İyi günler"); default: return t("İyi akşamlar") }
    }

    static func litres(_ ml: Int) -> String {
        let raw = String(format: "%.1f", Double(ml) / 1000)
        let text = I18n.isEnglish ? raw : raw.replacingOccurrences(of: ".", with: ",")
        return text.hasSuffix(I18n.isEnglish ? ".0" : ",0") ? String(text.dropLast(2)) : text
    }

    static func moodPhrase(_ m: JournalMood) -> String {
        switch m {
        case .veryGood: return t("çok iyiydi")
        case .good: return t("iyiydi")
        case .neutral: return t("normaldi")
        case .hard, .fuckOff: return t("zordu")
        case .bad: return t("kötüydü")
        case .happy: return t("mutluydu")
        case .excited: return t("heyecanlıydı")
        case .calm: return t("sakindi")
        case .tired: return t("yorgundu")
        case .bored: return t("sıkkındı")
        case .stressed: return t("stresliydi")
        case .angry: return t("sinirliydi")
        case .sad: return t("üzgündü")
        case .low: return t("keyifsizdi")
        }
    }

    static func yesterday(ml: Int, mood: JournalMood?) -> String? {
        let water: String? = ml > 0 ? (ml < 1000 ? t("Dün {0} ml su içtin", ml) : t("Dün {0} L su içtin", litres(ml))) : nil
        let moodText = mood.map { t("ruh halin {0}", moodPhrase($0)) }
        switch (water, moodText) {
        case let (w?, m?): return "\(w),\n\(m)."
        case let (w?, nil): return "\(w)."
        case let (nil, m?): return t("Dün {0}.", m)
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
        case 0: return t("BUGÜN · {0}", RealDates.time(d))
        case 1: return t("DÜN · {0}", RealDates.time(d))
        default:
            let months = [t("OCA"), t("ŞUB"), t("MAR"), t("NİS"), t("MAY"), t("HAZ"), t("TEM"), t("AĞU"), t("EYL"), t("EKİ"), t("KAS"), t("ARA")]
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
                Text(greeting.uppercased(with: I18n.locale))
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
