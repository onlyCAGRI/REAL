import Foundation

/// Store screenshot content (-REALDemo -REALShowcase -REALScreen <screen>), one story per language:
/// TR is a twenty-something in İstanbul (Ece's birthday, a Kapadokya trip), EN the same life in New York (Emma, Lisbon).
/// The app language (-app_language tr|en) picks the story. Cards stay plain on purpose: only the birthday dinner is
/// coloured (salmon orange → rose) and starred, so the eye lands there first; categories keep calm tones.
/// Song and book titles are invented (no real titles in store art). Ids line up with DemoData.path:
/// category 1, detail 1 (hero card), 2 (shared trip notes), 4 (checklist), 6 (stack), 7 (reading).
extension DemoData {
    static func seedShowcase(_ store: ReminderStore) {
        let en = I18n.isEnglish
        func L(_ tr: String, _ english: String) -> String { en ? english : tr }

        let cal = Calendar.real
        let now = Date()
        let today = cal.startOfDay(for: now)
        func at(_ hour: Int, _ minute: Int = 0, dayOffset: Int = 0) -> EpochMillis {
            let d = cal.date(byAdding: .day, value: dayOffset, to: today)!
            return cal.date(bySettingHour: hour, minute: minute, second: 0, of: d)!.epochMillis
        }
        // Next Saturday (today when it is Saturday): the birthday dinner.
        let saturday = (7 - cal.component(.weekday, from: today)) % 7
        let base = now.epochMillis - 3_600_000
        func note(_ id: String, _ text: String) -> ReminderAttachment {
            ReminderAttachment(id: id, type: .textSnippet, text: text)
        }

        let friend = L("Ece", "Emma")
        let guests = en ? ["Emma Carter", "Leo Brooks", "Mia Turner"] : ["Ece Yılmaz", "Mert Demir", "Zeynep Kaya"]

        var categories: [CustomCategory] = [
            // Jet Siyahı → Gece Mavisi: a quiet dark navy.
            CustomCategory(id: 1, name: L("Kapadokya Kaçamağı", "Lisbon Getaway"), iconKey: "EVENT", accentStart: 1, accentEnd: 3,
                           gradientBlendFraction: 0.5, description: L("Ekim sonu", "Late October")),
            // Adaçayı Koyu → Adaçayı.
            CustomCategory(id: 2, name: L("Okuma Listesi", "Reading List"), iconKey: "BOOK", accentStart: 27, accentEnd: 29,
                           gradientBlendFraction: 0.5, description: L("Bu ay", "This month")),
            // Duman Beyazı → Periwinkle, dark text.
            CustomCategory(id: 3, name: L("Ev", "Home"), iconKey: "HOME", accentStart: 0, accentEnd: 26, useDarkText: true,
                           gradientBlendFraction: 0.5, description: L("Haftalık", "Weekly")),
            CustomCategory(id: 4, name: L("Arkadaşlar", "Friends"), iconKey: "FRIENDS", accentStart: 1, accentEnd: 27,
                           gradientBlendFraction: 0.5, description: L("Planlar", "Plans")),
        ]
        categories[0].createdAt = base
        categories[0].sharedId = "showcase-trip"
        categories[0].sharedRole = "owner"

        var items: [ReminderItem] = []
        func add(_ id: Int64, _ text: String, _ build: (inout ReminderItem) -> Void = { _ in }) {
            var r = ReminderItem(id: id, text: text, createdAt: base + id * 1000)
            build(&r)
            r.anchorAlertAt = r.alertAt
            items.append(r)
        }

        // The hero card: place, weather (date + place), music, people, checklist and a note.
        add(1, L("Ece'nin doğum günü yemeği", "Emma's birthday dinner"), {
            $0.alertAt = at(20, dayOffset: saturday); $0.durationMinutes = 150; $0.customCategoryId = 4; $0.isImportant = true
            $0.accentStart = 12; $0.accentEnd = 14; $0.gradientBlendFraction = 0.5
            $0.people = guests
            $0.location = en
                ? ReminderLocation(placeName: "Brooklyn Bridge Park", address: "Brooklyn, New York", latitude: 40.7003, longitude: -73.9967)
                : ReminderLocation(placeName: "Moda Sahili", address: "Kadıköy, İstanbul", latitude: 40.9819, longitude: 29.0254)
            $0.isChecklist = true
            $0.checklistItems = [ChecklistItem(id: 11, text: L("Pasta siparişi", "Order the cake"), completedAt: base),
                                 ChecklistItem(id: 12, text: L("Hediye: plak çalar", "Gift: record player"), completedAt: base),
                                 ChecklistItem(id: 13, text: L("Masa rezervasyonu", "Book a table"))]
            $0.musicBlocks = [MusicBlock(id: "showcase-song", title: L("Gece Yarısı Treni", "Midnight Train"),
                                         artist: L("Mavi Kuşlar", "Blue Birds"), provider: "Apple Music", providerUrl: "https://music.apple.com")]
            $0.attachments = [note("n11", L("Sürpriz! \(friend)'ye söyleme 🤫", "Surprise! Don't tell \(friend) 🤫"))]
        })
        // Shared trip notes (category is shared with friends).
        add(2, L("Gezi planı notları", "Trip plan notes"), { $0.plannedDateEpochDay = cal.epochDay(of: today); $0.customCategoryId = 1
            $0.sharedAuthorName = guests[1]
            $0.attachments = en ? [
                note("n21", "Day 1\n• Tram 28 through Alfama\n• Sunset at Miradouro da Senhora do Monte"),
                note("n22", "Day 2\nDay trip to Sintra. Leave early, the palace gets busy after 11."),
            ] : [
                note("n21", "1. gün\n• Gün doğumunda balon turu\n• Öğleden sonra Göreme Açık Hava Müzesi"),
                note("n22", "2. gün\nATV turu ve Kızılçukur'da gün batımı. Sabah erken çıkalım."),
            ] })
        add(3, L("Balon turu rezervasyonu", "Book the hotel in Alfama"), { $0.alertAt = at(12, dayOffset: 1); $0.customCategoryId = 1 })
        add(4, L("Market listesi", "Grocery list"), { $0.customCategoryId = 3; $0.isChecklist = true
            $0.checklistItems = en
                ? [ChecklistItem(id: 41, text: "Milk", completedAt: base), ChecklistItem(id: 42, text: "Bread"), ChecklistItem(id: 43, text: "Eggs"),
                   ChecklistItem(id: 44, text: "Coffee"), ChecklistItem(id: 45, text: "Avocados", completedAt: base)]
                : [ChecklistItem(id: 41, text: "Süt", completedAt: base), ChecklistItem(id: 42, text: "Ekmek"), ChecklistItem(id: 43, text: "Zeytinyağı"),
                   ChecklistItem(id: 44, text: "Kahve"), ChecklistItem(id: 45, text: "Mevsim sebzeleri", completedAt: base)] })
        add(5, L("Kirayı öde", "Pay rent"), { $0.alertAt = at(10, dayOffset: 3); $0.customCategoryId = 3; $0.repeatRule = .monthly })
        add(6, L("Hafta sonu fikirleri", "Weekend ideas"), {
            $0.cardStack = CardStackBlock(prompt: nil, cards: [
                CardStackCard(title: L("Sahilde kahvaltı", "Brunch by the water"), body: L("Pazar sabahı, erken gidelim.", "Sunday morning, let's go early."),
                              colorArgb: Int(Int32(bitPattern: 0xFF3A4152))),
                CardStackCard(title: L("Film gecesi", "Movie night"), body: L("Herkes bir film getirsin.", "Everyone brings one film."),
                              colorArgb: Int(Int32(bitPattern: 0xFF4E5D57))),
                CardStackCard(title: L("Bisiklet turu", "Bike ride"), body: L("Sahil yolu, 20 km.", "Along the river, 12 miles."),
                              colorArgb: Int(Int32(bitPattern: 0xFF5B5566))),
            ]) })
        add(7, L("Okuma: Sessiz Şehir", "Reading: The Quiet City"), { $0.customCategoryId = 2
            $0.attachments = [
                note("n71", L("Bölüm 2: Kısa ama düzenli odak blokları, uzun ve dağınık çalışmadan daha verimli.",
                              "Chapter 2: Short, regular focus blocks beat long, scattered work.")),
                note("n72", L("Uygulama\n• Sabah ilk 45 dakika bildirimsiz.\n• Gün sonunda üç satır not.",
                              "Try\n• First 45 minutes of the day without notifications.\n• Three lines of notes every evening.")),
            ] })
        add(8, "Pilates", { $0.alertAt = at(7, 30, dayOffset: 1); $0.durationMinutes = 50; $0.repeatRule = .weekly })
        add(9, L("Sabah rutini", "Morning routine"), { $0.alertAt = at(7, 30); $0.durationMinutes = 15; $0.completedAt = at(7, 45)
            $0.journal = ReminderJournal(mood: .calm, text: L("Güne sakin başladım, plan hazır.", "Calm start, the day is planned."),
                                         createdAt: at(7, 50), updatedAt: at(7, 50)) })
        // The card the voice screenshot says out loud.
        add(10, L("Dişçi randevusu", "Dentist"), { $0.alertAt = at(15, dayOffset: 1); $0.durationMinutes = 30 })
        add(11, L("Su iç", "Drink water"), { $0.quick24HourUntilAt = now.epochMillis + CategoryRules.rolling24HoursMillis })
        add(12, L("Annemi ara", "Call Mom"), { $0.alertAt = at(19); $0.isImportant = false })
        add(13, L("Valiz listesi", "Packing list"), { $0.customCategoryId = 1; $0.isChecklist = true
            $0.checklistItems = [ChecklistItem(id: 131, text: L("Pasaport", "Passport"), completedAt: base),
                                 ChecklistItem(id: 132, text: L("Şarj aleti", "Charger")), ChecklistItem(id: 133, text: L("Rahat ayakkabı", "Comfy shoes"))] })
        add(14, L("Okuma: Uzak Sinyal", "Reading: Distant Signal"), { $0.customCategoryId = 2; $0.plannedDateEpochDay = cal.epochDay(of: today) + 1
            $0.attachments = [note("n141", L("Her kitaptan en fazla on not; her not kendi cümlemle.", "At most ten notes per book, each in my own words."))] })

        store.replaceAll(reminders: items, categories: categories, ownerName: en ? "Alex Morgan" : "Deniz Arslan")
    }

    /// Store screenshot boards: the shared trip board first, then two quiet ones.
    static func seedShowcasePano(_ pano: PanoStore) {
        let en = I18n.isEnglish
        func L(_ tr: String, _ english: String) -> String { en ? english : tr }
        let trip = PanoBoard(title: L("Kapadokya Kaçamağı", "Lisbon Getaway"), shared: true)
        let home = PanoBoard(title: L("Ev", "Home"), shared: false)
        var friends = PanoBoard(title: L("Doğum günü", "Birthday"), shared: true)
        friends.role = "MEMBER"
        let items: [PanoItem] = [
            PanoItem(boardId: trip.id, type: .text, transform: BoardTransform(x: 0, y: 40, width: 240, height: 140, z: 1),
                     text: L("Balon turu gün doğumunda, sonra Göreme'de kahvaltı.", "Tram 28 in the morning, then pastéis in Belém.")),
            PanoItem(boardId: trip.id, type: .card, transform: BoardTransform(x: 30, y: 220, width: 300, height: 120, z: 2), reminderId: 2),
            PanoItem(boardId: trip.id, type: .checklist, transform: BoardTransform(x: 0, y: 380, width: 260, height: 190, z: 3),
                     checklist: [PanoChecklistEntry(text: L("Uçak biletleri", "Flights"), done: true),
                                 PanoChecklistEntry(text: L("Mağara otel", "Hotel in Alfama"), done: true),
                                 PanoChecklistEntry(text: L("ATV turu", "Day trip to Sintra"))]),
        ]
        pano.replaceAll(boards: [trip, home, friends], items: [trip.id: items])
    }
}
