import SwiftUI
import UserNotifications

@main
struct RealApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var store: ReminderStore
    private let initialPath: [Route]

    init() {
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-REALDemo") {
            // Screenshot / UI test mode: in-memory store with a fixed demo dataset, no permission prompts.
            NotificationScheduler.enabled = false
            let store = ReminderStore(persist: false)
            DemoData.seed(store)
            _store = State(initialValue: store)
            initialPath = DemoData.path(for: args, store: store)
        } else {
            _store = State(initialValue: ReminderStore())
            initialPath = []
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView(initialPath: initialPath)
                .environment(store)
                .background(Color.black)
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
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

        store.replaceAll(reminders: items, categories: categories, ownerName: "Çağrı Gül")
    }

    static func path(for args: [String], store: ReminderStore) -> [Route] {
        guard let i = args.firstIndex(of: "-REALScreen"), i + 1 < args.count else { return [] }
        switch args[i + 1] {
        case "category": return [.customCategory(1)]
        case "builtin": return [.category(.planned)]
        case "detail": return [.detail(1)]
        case "checklist": return [.detail(4)]
        case "planner": return [.planner]
        case "goal": return [.planner, .goal(1)]
        default: return []
        }
    }
}
