import Foundation

/// Calm showcase content for presentation screenshots (-REALDemo -REALShowcase -REALScreen <screen>).
/// Muted category colours, note-heavy cards, no real brands or titles. Ids line up with DemoData.path:
/// category 1, detail 1 (meeting notes), detail 4 (checklist); 2, 6 and 7 are the extra "notes", "stack", "reading" screens.
extension DemoData {
    static func seedShowcase(_ store: ReminderStore) {
        let cal = Calendar.real
        let now = Date()
        let today = cal.startOfDay(for: now)
        func at(_ hour: Int, _ minute: Int = 0, dayOffset: Int = 0) -> EpochMillis {
            let d = cal.date(byAdding: .day, value: dayOffset, to: today)!
            return cal.date(bySettingHour: hour, minute: minute, second: 0, of: d)!.epochMillis
        }
        let base = now.epochMillis - 3_600_000
        func note(_ id: String, _ text: String) -> ReminderAttachment {
            ReminderAttachment(id: id, type: .textSnippet, text: text)
        }

        var categories: [CustomCategory] = [
            // Jet Siyahı → Gece Mavisi: a quiet dark navy.
            CustomCategory(id: 1, name: "Ürün Lansmanı", iconKey: "WORK", accentStart: 1, accentEnd: 3, gradientBlendFraction: 0.5,
                           description: "Ekim sürümü"),
            // Adaçayı Koyu → Adaçayı.
            CustomCategory(id: 2, name: "Okuma Notları", iconKey: "BOOK", accentStart: 27, accentEnd: 29, gradientBlendFraction: 0.5,
                           description: "Bu ay"),
            // Duman Beyazı → Periwinkle, dark text.
            CustomCategory(id: 3, name: "Ev", iconKey: "HOME", accentStart: 0, accentEnd: 26, useDarkText: true, gradientBlendFraction: 0.5,
                           description: "Haftalık"),
        ]
        categories[0].createdAt = base
        categories[0].sharedId = "showcase-launch"
        categories[0].sharedRole = "owner"

        var items: [ReminderItem] = []
        func add(_ id: Int64, _ text: String, _ build: (inout ReminderItem) -> Void = { _ in }) {
            var r = ReminderItem(id: id, text: text, createdAt: base + id * 1000)
            build(&r)
            r.anchorAlertAt = r.alertAt
            items.append(r)
        }

        add(1, "Lansman toplantısı", { $0.alertAt = at(10); $0.durationMinutes = 60; $0.customCategoryId = 1; $0.isImportant = true
            $0.people = ["Ece Yılmaz", "Can Aksoy"]
            $0.location = ReminderLocation(placeName: "Ofis, toplantı odası 2", address: "")
            $0.isChecklist = true
            $0.checklistItems = [ChecklistItem(id: 11, text: "Sunum taslağı", completedAt: base), ChecklistItem(id: 12, text: "Beta geri bildirim raporu"),
                                 ChecklistItem(id: 13, text: "Yayın notları")]
            $0.attachments = [
                note("n11", "Gündem\n• Beta geri bildirimlerinin özeti\n• Mağaza sayfası metinleri\n• Yayın tarihinin netleşmesi"),
                note("n12", "Kararlar\nBeta kullanıcılarından gelen ilk üç sorun yayından önce kapanacak. Mağaza görselleri cuma günü hazır olacak."),
            ] })
        add(2, "Kullanıcı görüşmesi notları", { $0.plannedDateEpochDay = cal.epochDay(of: today); $0.customCategoryId = 1
            $0.sharedAuthorName = "Ece Yılmaz"
            $0.attachments = [
                note("n21", "Öne çıkanlar\n• Sesle kart eklemeyi ilk gün keşfettiler.\n• Ortak kategori aile listesi için kullanılıyor.\n• Takvimde renkler kategoriyi hemen gösteriyor."),
                note("n22", "Sorunlar\nTekrarlayan kartlarda bitiş tarihi beklenen yerde değil. Bildirim saati ayarı iki adım fazla."),
                ReminderAttachment(id: "n23", type: .textSnippet,
                                   text: "Görüşme 3: \"Akşam her şeyi tek yerde görmek rahatlatıyor, ayrı uygulamalara bakmıyorum.\"",
                                   sourceTitle: "Notlar uygulamasından paylaşıldı", source: "PLAIN_TEXT"),
            ] })
        add(3, "Mağaza sayfası metni", { $0.alertAt = at(14, dayOffset: 1); $0.durationMinutes = 45; $0.customCategoryId = 1
            $0.attachments = [note("n31", "Taslak\nGününü konuşarak planla. Hatırlatıcılar, takvim ve kişisel takip tek uygulamada; internet olmasa da çalışır.")] })
        add(4, "Haftalık alışveriş", { $0.customCategoryId = 3; $0.isChecklist = true
            $0.checklistItems = [ChecklistItem(id: 41, text: "Süt", completedAt: base), ChecklistItem(id: 42, text: "Tam buğday ekmek"),
                                 ChecklistItem(id: 43, text: "Zeytinyağı"), ChecklistItem(id: 44, text: "Kahve"),
                                 ChecklistItem(id: 45, text: "Mevsim sebzeleri", completedAt: base)]
            $0.attachments = [note("n41", "Cumartesi sabah pazara uğra, sebzeleri oradan al.")] })
        add(5, "Faturalar", { $0.plannedDateEpochDay = cal.epochDay(of: today); $0.customCategoryId = 3 })
        add(6, "Fikir destesi", {
            $0.cardStack = CardStackBlock(prompt: nil, cards: [
                CardStackCard(title: "Sesli hızlı not", body: "Kilit ekranından tek dokunuşla konuş, kart taslak olarak düşsün.", colorArgb: Int(Int32(bitPattern: 0xFF3A4152))),
                CardStackCard(title: "Haftalık özet", body: "Pazar akşamı tamamlananlar ve gelecek hafta tek kartta.", colorArgb: Int(Int32(bitPattern: 0xFF4E5D57))),
                CardStackCard(title: "Ortak liste şablonu", body: "Ev ve tatil listeleri için hazır kategori şablonları.", colorArgb: Int(Int32(bitPattern: 0xFF5B5566))),
            ])
            $0.attachments = [note("n61", "Bu destedeki fikirleri bir sonraki sürüm planlamasında oylayalım.")] })
        add(7, "Okuma: Sessiz Odak", { $0.customCategoryId = 2
            $0.attachments = [
                note("n71", "Bölüm 2: Dikkat bir kas gibi; kısa ama düzenli odak blokları uzun ve dağınık çalışmadan verimli."),
                note("n72", "Uygulama\n• Sabah ilk 45 dakika bildirimsiz.\n• Gün sonunda üç satır not."),
            ] })
        add(8, "Akşam yürüyüşü", { $0.alertAt = at(19, 30); $0.durationMinutes = 30; $0.repeatRule = .daily })
        add(9, "Sabah rutini", { $0.alertAt = at(7, 30); $0.durationMinutes = 15; $0.completedAt = at(7, 45)
            $0.journal = ReminderJournal(mood: .calm, text: "Güne sakin başladım, plan hazır.", createdAt: at(7, 50), updatedAt: at(7, 50)) })
        add(10, "Diş hekimi kontrolü", { $0.alertAt = at(9, 30, dayOffset: 2); $0.isImportant = true
            $0.location = ReminderLocation(placeName: "Ağız ve Diş Sağlığı Merkezi", address: "") })
        add(11, "Su iç", { $0.quick24HourUntilAt = now.epochMillis + CategoryRules.rolling24HoursMillis })
        add(12, "Ekip sunumu", { $0.alertAt = at(15); $0.durationMinutes = 90; $0.customCategoryId = 1
            $0.sharedAuthorName = "Can Aksoy" })
        add(13, "Kod incelemesi", { $0.alertAt = at(11); $0.durationMinutes = 30; $0.customCategoryId = 1; $0.completedAt = at(11, 35) })
        add(14, "Okuma: Not alma yöntemi", { $0.customCategoryId = 2; $0.plannedDateEpochDay = cal.epochDay(of: today) + 1
            $0.attachments = [note("n141", "Her kitaptan en fazla on not; her not kendi cümlemle.")] })

        store.replaceAll(reminders: items, categories: categories, ownerName: "Çağrı Gül")
    }
}
