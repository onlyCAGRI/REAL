import SwiftUI
import UserNotifications

@main
struct RealApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var store: ReminderStore
    @State private var life: LifeStore
    @State private var pano: PanoStore
    @State private var books: BookStore
    @State private var discovery: DiscoveryStore
    private let initialPath: [Route]

    init() {
        // No scroll indicator bars anywhere (Android shows none): every SwiftUI ScrollView / List is a UIScrollView.
        UIScrollView.appearance().showsVerticalScrollIndicator = false
        UIScrollView.appearance().showsHorizontalScrollIndicator = false
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-REALDemo") {
            // Screenshot / UI test mode: in-memory store with a fixed demo dataset, no permission prompts.
            NotificationScheduler.enabled = false
            let store = ReminderStore(persist: false)
            let showcase = args.contains("-REALShowcase")
            if showcase { DemoData.seedShowcase(store) } else { DemoData.seed(store) }
            _store = State(initialValue: store)
            let life = LifeStore(persist: false)
            DemoData.seedLife(life)
            _life = State(initialValue: life)
            let pano = PanoStore(persist: false)
            DemoData.seedPano(pano, store: store)
            _pano = State(initialValue: pano)
            let books = BookStore(persist: false)
            DemoData.seedBooks(books)
            _books = State(initialValue: books)
            let discovery = DiscoveryStore(persist: false, service: DemoDiscoveryService())
            // Showcase screenshots keep real film titles out.
            if !showcase { discovery.seedDemo() }
            _discovery = State(initialValue: discovery)
            initialPath = DemoData.path(for: args, store: store)
        } else {
            _store = State(initialValue: ReminderStore())
            _life = State(initialValue: LifeStore())
            _pano = State(initialValue: PanoStore())
            _books = State(initialValue: BookStore())
            _discovery = State(initialValue: DiscoveryStore())
            initialPath = []
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView(initialPath: initialPath)
                .scrollIndicators(.hidden)
                .environment(store)
                .environment(life)
                .environment(pano)
                .environment(books)
                .environment(discovery)
                .background(Color.black)
                .overlay { RequiredUpdateHost(uid: RealAuth.shared.account?.uid) }
                .overlay { RealToastHost() }
                .onOpenURL { url in
                    if RealDeepLinks.shared.handle(url) { return }
                    _ = RealAuth.shared.handle(url: url)
                }
                .sheet(isPresented: Binding(get: { RealAuth.shared.needsNameChoice }, set: { _ in })) {
                    ProfileNameSheet()
                }
                .sheet(item: Binding(get: { RealDeepLinks.shared.pending }, set: { RealDeepLinks.shared.pending = $0 })) { link in
                    InviteAcceptSheet(link: link).environment(store).environment(life)
                }
                .sheet(item: Binding(get: { CalendarCardPublisher.shared.offerCardId.map(PublicationOffer.init) },
                                     set: { CalendarCardPublisher.shared.offerCardId = $0?.id })) { offer in
                    CalendarPublicationSheet(cardId: offer.id).environment(store).environment(life)
                }
                // Alarm: REAL's own alarm page ("Aç" on the system alarm, or an alarm going off while REAL is open).
                .fullScreenCover(item: Binding(get: { AlarmCenter.shared.ringing },
                                               set: { if $0 == nil { AlarmCenter.shared.stop() } })) { alarm in
                    AlarmScreen(alarm: alarm)
                }
                .task {
                    if #available(iOS 26.0, *) { await RealAlarmKit.watch() }
                }
                // Ortak kategori: local edits go up shortly; while the app is open it also pulls every minute.
                .task {
                    let store = store
                    _ = KeyboardWatcher.shared
                    let life = life
                    store.onSaved = { [weak store] in
                        guard let store else { return }
                        Task { @MainActor in
                            SharedCategorySync.shared.request(store)
                            RealCloudSync.shared.request(store, life)
                            ShareImport.writeSnapshot(store)
                        }
                    }
                    // Share → REAL: shares left by the share extension, and the card list it shows.
                    ShareImport.run(store)
                    ShareImport.writeSnapshot(store)
                    _ = ClipboardWatcher.shared
                    NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { _ in
                        Task { @MainActor in ShareImport.run(store) }
                    }
                    // Two-way cloud sync, shared categories and Pano, every minute while the app is open.
                    pano.reminders = store
                    pano.life = life
                    while !Task.isCancelled {
                        await RealCloudSync.shared.run(store, life)
                        await pano.sync()
                        await SharedCategorySync.shared.syncNow(store)
                        await CardShareClient.receive(store: store, life: life)
                        await AnnouncementClient.receive(store: store, life: life)
                        try? await Task.sleep(for: .seconds(60))
                    }
                }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        NotificationScheduler.registerCategories()
        MainActor.assumeIsolated { RealAuth.shared.configure() }
        return true
    }

    /// NotificationActionReceiver: "Tamamla" completes (repeats move on), "10 dk ertele" snoozes, a tap opens the card.
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard let id = response.notification.request.content.userInfo["reminderId"] as? Int64 else { return }
        await MainActor.run {
            let store = ReminderStore.current
            switch response.actionIdentifier {
            case NotificationScheduler.completeAction:
                if let r = store?.reminder(id), !r.isCompleted { store?.toggleCompleted(id) }
                NotificationScheduler.removeDelivered(id)
            case NotificationScheduler.snoozeAction:
                store?.snooze(id)
            case UNNotificationDefaultActionIdentifier:
                NotificationCenter.default.post(name: .realOpenReminder, object: nil, userInfo: ["reminderId": id])
            default: break
            }
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }
}

/// Demo content used by CI screenshots (-REALDemo -REALScreen <home|category|detail|planner>).
enum DemoData {
    static func seed(_ store: ReminderStore) {
        let cal = Calendar.real
        let now = Date()
        let today = cal.startOfDay(for: now)
        func at(_ hour: Int, _ minute: Int = 0, dayOffset: Int = 0) -> EpochMillis {
            let d = cal.date(byAdding: .day, value: dayOffset, to: today)!
            return cal.date(bySettingHour: hour, minute: minute, second: 0, of: d)!.epochMillis
        }
        let base = now.epochMillis - 3_600_000

        var categories: [CustomCategory] = [
            CustomCategory(id: 1, name: "İş", iconKey: "WORK", accentStart: 2, accentEnd: 3, gradientBlendFraction: 0.5,
                           description: "Sprint 42"),
            CustomCategory(id: 2, name: "Ev", iconKey: "HOME", accentStart: 7, accentEnd: 9, useDarkText: true),
            CustomCategory(id: 3, name: "Spor", iconKey: "SPORT", accentStart: 12, accentEnd: 14),
        ]
        categories[0].createdAt = base

        var items: [ReminderItem] = []
        func add(_ id: Int64, _ text: String, _ build: (inout ReminderItem) -> Void = { _ in }) {
            var r = ReminderItem(id: id, text: text, createdAt: base + id * 1000)
            build(&r)
            r.anchorAlertAt = r.alertAt
            items.append(r)
        }
        add(1, "Tasarım incelemesi", { $0.alertAt = at(10); $0.durationMinutes = 60; $0.customCategoryId = 1; $0.isImportant = true
            $0.people = ["Ayşe Demir", "Mert Kaya"]
            $0.accentStart = 2; $0.accentEnd = 18; $0.gradientBlendFraction = 0.5
            $0.isChecklist = true
            $0.checklistItems = [ChecklistItem(id: 11, text: "Ana sayfa akışı", completedAt: base), ChecklistItem(id: 12, text: "Kart detay blokları"),
                                 ChecklistItem(id: 13, text: "Takvim focus kartları")]
            $0.referenceUrl = "https://www.figma.com/file/real-ios"
            $0.location = ReminderLocation(placeName: "Kolektif House Levent", address: "Esentepe, Talatpaşa Cd. No:5, Şişli/İstanbul",
                                           latitude: 41.0786, longitude: 29.0107)
            $0.cardStack = CardStackBlock(prompt: nil, cards: [
                CardStackCard(title: "Gündem", body: "Dock, detay ve takvim ekranlarını Android ile karşılaştır.", colorArgb: Int(Int32(bitPattern: 0xFF4558C8))),
                CardStackCard(title: "Notlar", body: "Liquid Glass yüzeyler iOS 26'da gerçek materyal.", colorArgb: Int(Int32(bitPattern: 0xFFDAB7F3))),
            ]) })
        add(2, "Haftalık planlama", { $0.alertAt = at(13, 30); $0.durationMinutes = 45; $0.customCategoryId = 1
            $0.repeatRule = .weekly; $0.repeatWeekdaysMask = Weekday.monday.bit | Weekday.wednesday.bit | Weekday.friday.bit | cal.isoWeekday(of: today).bit })
        add(3, "Koşu 5K", { $0.alertAt = at(18); $0.durationMinutes = 40; $0.customCategoryId = 3; $0.isImportant = true })
        add(4, "Market alışverişi", { $0.customCategoryId = 2; $0.isChecklist = true
            $0.checklistItems = [ChecklistItem(id: 41, text: "Süt", completedAt: base), ChecklistItem(id: 42, text: "Ekmek"),
                                 ChecklistItem(id: 43, text: "Zeytinyağı"), ChecklistItem(id: 44, text: "Kahve")] })
        add(5, "Faturaları öde", { $0.plannedDateEpochDay = cal.epochDay(of: today); $0.customCategoryId = 2 })
        add(6, "Annemi ara", { $0.alertAt = at(20, 15); $0.accentStart = 14; $0.accentEnd = 17; $0.repeatRule = .daily })
        add(7, "Kitap: Sapiens 3. bölüm", { $0.accentStart = 17; $0.accentEnd = 20; $0.useDarkText = true })
        add(8, "Doktor randevusu", { $0.alertAt = at(9, 30, dayOffset: 2); $0.isImportant = true; $0.accentStart = 4
            $0.location = ReminderLocation(placeName: "Acıbadem Hastanesi", address: "Kozyatağı, İstanbul") })
        add(9, "Proje fikirleri", { $0.cardStack = CardStackBlock(prompt: nil, cards: [
            CardStackCard(title: "Sesli not", body: "Kartı sesle oluştur, takvime otomatik düşsün.", colorArgb: Int(Int32(bitPattern: 0xFFDAB7F3))),
            CardStackCard(title: "Paylaşılan pano", body: "Ekip için ortak kart destesi.", colorArgb: Int(Int32(bitPattern: 0xFFE0F37D))),
        ]) })
        add(10, "Sabah meditasyonu", { $0.alertAt = at(7, 30); $0.durationMinutes = 15; $0.completedAt = at(7, 45)
            $0.journal = ReminderJournal(mood: .calm, text: "Güne sakin başladım.", createdAt: at(7, 50), updatedAt: at(7, 50)) })
        add(11, "Sunum slaytları", { $0.alertAt = at(15); $0.durationMinutes = 90; $0.customCategoryId = 1 })
        add(12, "Su iç", { $0.quick24HourUntilAt = now.epochMillis + CategoryRules.rolling24HoursMillis })
        add(13, "Kod incelemesi", { $0.alertAt = at(11); $0.durationMinutes = 30; $0.customCategoryId = 1; $0.completedAt = at(11, 35) })
        add(14, "Hafta sonu", {
            $0.musicBlocks = [MusicBlock(title: "bellyache", artist: "Billie Eilish", provider: "iTunes", providerUrl: "https://music.apple.com/tr/search?term=bellyache",
                                         previewUrl: "https://example.invalid/preview.m4a")]
            $0.mediaBlocks = [MediaBlock(type: .TV, providerId: "44778", title: "Silo", year: 2023, genre: "Drama, Science-Fiction",
                                         description: "Silo depicts a ruined and toxic future, when thousands of people live in a giant silo deep underground.",
                                         provider: "TVmaze", providerUrl: "https://www.tvmaze.com/shows/44778", durationMillis: 3_000_000)]
        })

        store.replaceAll(reminders: items, categories: categories, ownerName: "Çağrı Gül")
    }

    static func seedLife(_ life: LifeStore) {
        let now = EpochMillis.now
        let hour: EpochMillis = 3_600_000
        let entries = [
            WaterIntakeEntry(id: "w1", amountMl: 250, createdAt: now - 5 * hour),
            WaterIntakeEntry(id: "w2", amountMl: 500, createdAt: now - 3 * hour),
            WaterIntakeEntry(id: "w3", amountMl: 250, createdAt: now - hour),
            WaterIntakeEntry(id: "c1", amountMl: 200, createdAt: now - 4 * hour, beverage: .coffee),
        ] + (1...12).map { d in WaterIntakeEntry(id: "h\(d)", amountMl: [1500, 2100, 1800, 900, 2400, 1200][d % 6], createdAt: now - EpochMillis(d) * 24 * hour) }
        let moodsSeq: [JournalMood] = [.calm, .good, .tired, .happy, .stressed, .good, .veryGood, .neutral]
        let moods = moodsSeq.enumerated().map { i, m in
            MoodTrackerEntry(id: now - EpochMillis(i) * 24 * hour, mood: m, details: MoodDetails(loggedAt: now - EpochMillis(i) * 24 * hour - 2 * hour))
        }
        life.replaceAll(goal: 2_000, entries: entries, moods: moods)
    }

    static func seedBooks(_ books: BookStore) {
        let text = """
        BİRİNCİ BÖLÜM

        Sabahın ilk ışıkları pencereden süzülürken kasaba henüz uyanmamıştı. Denizden gelen hafif rüzgâr, limandaki teknelerin direklerini birbirine vurdurup ince bir çan sesi çıkarıyordu.

        Elif, mutfaktaki eski masaya oturdu ve defterini açtı. Her sabah olduğu gibi önce bugünün tarihini yazdı, sonra uzun uzun pencereye baktı. Yazmak istediği şey aklındaydı ama kelimeler bir türlü bir araya gelmiyordu.

        Kahvesinden bir yudum aldı. Sokağın karşısındaki fırından taze ekmek kokusu geliyordu. Belki de bugün, diye düşündü, hikâyeyi en baştan değil ortasından anlatmalıyım.

        İKİNCİ BÖLÜM

        Öğleden sonra liman kalabalıklaştı. Balıkçılar ağlarını kurutuyor, çocuklar iskelenin ucundan suya taş atıyordu. Elif defterini koltuğunun altına sıkıştırıp yürüyüşe çıktı.
        """
        let id = "demo_txt"
        try? text.data(using: .utf8)?.write(to: books.directory.appendingPathComponent("\(id).txt"))
        let list: [Book] = [
            Book(id: id, title: "Limandaki Defter", author: "Deniz Arda", category: "Roman", fileName: "\(id).txt", format: "txt"),
            Book(id: "demo_2", title: "Akhilleus'un Şarkısı", author: "Madeline Miller", category: "Roman", pageCount: 412),
            Book(id: "demo_3", title: "Sefiller", author: "Victor Hugo", category: "Roman", pageCount: 1460),
            Book(id: "demo_4", title: "Küçük Prens", author: "Antoine de Saint-Exupéry", category: "Roman", pageCount: 96),
            Book(id: "demo_5", title: "Atomik Alışkanlıklar", author: "James Clear", category: "Kişisel Gelişim", pageCount: 320),
            Book(id: "demo_6", title: "Derin Çalışma", author: "Cal Newport", category: "Kişisel Gelişim", pageCount: 296),
        ]
        for b in list { try? books.add(b) }
        books.savePosition(list[1], page: 8)
    }

    static func seedPano(_ pano: PanoStore, store: ReminderStore) {
        let a = PanoBoard(title: "Olmuyo (Genel)", shared: true)
        let b = PanoBoard(title: "Okkk", shared: false)
        var c = PanoBoard(title: "Djfkfkggl", shared: true)
        c.role = "MEMBER"
        let items: [PanoItem] = [
            PanoItem(boardId: a.id, type: .text, transform: BoardTransform(x: 0, y: 40, width: 240, height: 140, z: 1), text: "Sprint fikirleri: dock morph, takvim, Yaşam"),
            PanoItem(boardId: a.id, type: .card, transform: BoardTransform(x: 30, y: 220, width: 300, height: 120, z: 2), reminderId: 1),
            PanoItem(boardId: a.id, type: .checklist, transform: BoardTransform(x: 0, y: 380, width: 260, height: 190, z: 3),
                     checklist: [PanoChecklistEntry(text: "Logo", done: true), PanoChecklistEntry(text: "Renkler"), PanoChecklistEntry(text: "Kartlar")]),
        ]
        pano.replaceAll(boards: [a, b, c], items: [a.id: items])
    }

    static func path(for args: [String], store: ReminderStore) -> [Route] {
        guard let i = args.firstIndex(of: "-REALScreen"), i + 1 < args.count else { return [] }
        switch args[i + 1] {
        case "category": return [.customCategory(1)]
        case "builtin": return [.category(.planned)]
        case "detail": return [.detail(1)]
        case "checklist": return [.detail(4)]
        case "notes": return [.detail(2)]
        case "stack": return [.detail(6)]
        case "reading": return [.detail(7)]
        case "planner":
            UserDefaults.standard.set("daily", forKey: "planner_view_mode")
            return [.planner]
        case "yasam": return [.module("Yaşam")]
        case "pano": return [.module("Pano")]
        case "water": return [.module("Su")]
        case "books": return [.module("Kitaplar")]
        case "film": return [.module("Film")]
        case "media": return [.detail(14)]
        case "moods": return [.module("Moods")]
        case "settings": return [.module("Ayarlar")]
        case "filmdeck": return [.module("FilmDeck")]
        case "game": return [.module("Oyun")]
        case "reader": return [.module("Okuyucu")]
        case "planner24":
            UserDefaults.standard.set("hours_24", forKey: "planner_view_mode")
            return [.planner]
        case "goal":
            UserDefaults.standard.set("daily", forKey: "planner_view_mode")
            return [.planner, .goal(1)]
        default: return []
        }
    }
}

private struct PublicationOffer: Identifiable { let id: Int64 }
