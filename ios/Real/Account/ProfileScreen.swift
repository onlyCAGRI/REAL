import SwiftUI
import PhotosUI
import FirebaseAuth

// Profile, member card, achievements and titles (ProfileActivity.kt / Achievements.kt / AvatarUnlocks.kt on Android).
// Design: claude.ai/artifact/Tz8DvEvfbgYuifGjEBJdnp

// MARK: - Avatar reward unlocks

/// Which reward parts of the avatar editor are open. The lock table lives in the editor page (LOCKS); this says what
/// is earned. Settings → Geliştirici "Tüm avatar parçaları açık" opens everything for trying parts out.
enum AvatarUnlocks {
    private static let allKey = "avatar_unlocks_all_open"
    private static let earnedKey = "avatar_unlocks_earned"

    static var allOpen: Bool {
        get { UserDefaults.standard.bool(forKey: allKey) }
        set { UserDefaults.standard.set(newValue, forKey: allKey) }
    }
    static var earned: Set<String> { Set(UserDefaults.standard.stringArray(forKey: earnedKey) ?? []) }

    /// Rewards from finished achievements. Add-only: a part once earned stays open.
    static func addEarned(_ names: [String]) {
        let now = earned
        guard !now.isSuperset(of: names) else { return }
        UserDefaults.standard.set(Array(now.union(names)).sorted(), forKey: earnedKey)
    }

    /// The editor page's __UNLOCKED__ value: "*" or base64 JSON of the earned part names.
    static func pageValue() -> String {
        if allOpen { return "*" }
        let data = (try? JSONSerialization.data(withJSONObject: earned.sorted())) ?? Data("[]".utf8)
        return data.base64EncodedString()
    }
}

// MARK: - Whole figure

/// The avatar head to shoes on a transparent background (560 × 1020), saved by the editor beside the square picture.
enum ProfileFullFigure {
    static var url: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("profile_full.png")
    }
    static func save(_ png: Data) {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? png.write(to: url, options: .atomic)
    }
    static func load() -> UIImage? { UIImage(contentsOfFile: url.path) }
}

// MARK: - Achievements

/// Counted on this phone from the cards, the daily-open streak and the join date. Finished ones open their reward
/// parts (names from the editor's LOCKS table); rare ones give a card stamp. Rewards are only ever added.
enum Achievements {
    enum Group: String, CaseIterable { case start = "BAŞLANGIÇ", habit = "ALIŞKANLIK", cards = "KARTLARIN", life = "YAŞAM", together = "BİRLİKTE", season = "DÖNEMSEL" }

    struct Def {
        let id: String, group: Group, title: String, detail: String, goal: Int, rewards: [String]
        var stamp = false, counted = true
    }
    struct Progress: Identifiable {
        let def: Def, now: Int
        var id: String { def.id }
        var done: Bool { def.counted && now >= def.goal }
    }
    struct Title: Identifiable, Equatable { let id: String, label: String, rule: String, earned: Bool }

    static let all: [Def] = [
        Def(id: "ilk-kart", group: .start, title: "İlk kart", detail: "İlk kartını oluştur", goal: 1, rewards: ["real-allik"]),
        Def(id: "ilk-tamam", group: .start, title: "İlk tamamlanan", detail: "Bir kartı tamamla", goal: 1, rewards: ["real-parilti"]),
        Def(id: "karta-sarki", group: .start, title: "Karta şarkı", detail: "Bir karta müzik ekle", goal: 1, rewards: ["wind-chime", "real-goz-kirpma"]),
        Def(id: "karta-konum", group: .start, title: "Karta konum", detail: "Bir karta konum ekle", goal: 1, rewards: ["camera"]),
        Def(id: "gun-7", group: .habit, title: "7 gün üst üste", detail: "Her gün REAL'i aç", goal: 7, rewards: ["hoodie"]),
        Def(id: "gun-30", group: .habit, title: "30 gün üst üste", detail: "Bir ay aksatmadan", goal: 30, rewards: ["jacket"]),
        Def(id: "gun-100", group: .habit, title: "100 gün üst üste", detail: "Yüz gün aksatmadan", goal: 100, rewards: ["halo"], stamp: true),
        Def(id: "tamam-25", group: .habit, title: "25 kart tamam", detail: "25 kartı tamamla", goal: 25, rewards: ["culottes"]),
        Def(id: "tamam-100", group: .habit, title: "100 kart tamam", detail: "100 kartı tamamla", goal: 100, rewards: ["flared-skirt"]),
        Def(id: "tamam-1000", group: .habit, title: "1000 kart tamam", detail: "Bin kartı tamamla", goal: 1000, rewards: ["crown"], stamp: true),
        Def(id: "gezgin", group: .cards, title: "Gezgin", detail: "10 farklı şehirde konumlu kart", goal: 10,
            rewards: ["sunglasses", "real-gunes", "real-yuvarlak-gunes"], stamp: true),
        Def(id: "tam-zamaninda", group: .cards, title: "Tam zamanında", detail: "Varınca hatırlatıcısıyla 5 kart", goal: 5,
            rewards: ["goggles", "real-sikica-kapali"], counted: false),
        Def(id: "yil-1", group: .cards, title: "1 yıl REAL", detail: "REAL'de bir yılını tamamla", goal: 365, rewards: [], stamp: true),
        Def(id: "ruh-ilk", group: .life, title: "İlk ruh hali", detail: "Bir karta ruh halini ekle", goal: 1, rewards: ["duck", "real-gulen"]),
        Def(id: "ruh-7", group: .life, title: "Ruh hali", detail: "7 farklı günde ruh halini kaydet", goal: 7, rewards: ["real-uzgun"]),
        Def(id: "okur", group: .life, title: "Okur", detail: "Bir kitabı bitir", goal: 1, rewards: ["real-kalin-kare"], counted: false),
        Def(id: "kitap-kurdu", group: .life, title: "Kitap kurdu", detail: "10 kitap bitir", goal: 10, rewards: ["fox-mask"], stamp: true, counted: false),
        Def(id: "vi-10", group: .life, title: "Vi’nin dostu", detail: "Kediyle 10 gün", goal: 10,
            rewards: ["tabby-cat", "cream-cat", "ginger-cat", "real-kedi-gozu"], counted: false),
        Def(id: "ilk-baglanti", group: .together, title: "İlk bağlantı", detail: "Bir arkadaşını REAL'e bağla", goal: 1, rewards: ["real-kalpler"], counted: false),
        Def(id: "ortak-kategori", group: .together, title: "Ortak kategori", detail: "Bir kategoriyi biriyle paylaş", goal: 1, rewards: ["bunny-ears"], counted: false),
        Def(id: "pano", group: .together, title: "Pano", detail: "Ortak panoya ilk şeyi as", goal: 1, rewards: ["yoyo", "real-sikilmis"], counted: false),
        Def(id: "yilbasi", group: .season, title: "Yılbaşı", detail: "24 Aralık–1 Ocak arasına bir kart planla", goal: 1, rewards: ["santa-hat"])
    ]

    // Daily-open streak: a day counts when REAL is opened on it.
    private static let daysKey = "achievement_open_days"
    private static let bestKey = "achievement_best_streak"
    private static let titlesKey = "achievement_earned_titles"

    private static func key(_ date: Date) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }
    private static func streak(ending end: Date, in days: Set<String>) -> Int {
        var n = 0, d = end
        while days.contains(key(d)) { n += 1; d = Calendar.current.date(byAdding: .day, value: -1, to: d) ?? d.addingTimeInterval(-86_400) }
        return n
    }

    /// Called when the app comes to the front. Keeps the last 400 days, enough for the 100-day streak.
    static func recordOpen(_ today: Date = Date()) {
        var days = Set(UserDefaults.standard.stringArray(forKey: daysKey) ?? [])
        let k = key(today)
        guard !days.contains(k) else { return }
        days.insert(k)
        let kept = Array(days.sorted().suffix(400))
        let best = max(UserDefaults.standard.integer(forKey: bestKey), streak(ending: today, in: Set(kept)))
        UserDefaults.standard.set(kept, forKey: daysKey)
        UserDefaults.standard.set(best, forKey: bestKey)
    }

    private static let titleDefs: [(String, String, String)] = [
        ("oncu", "Öncü Üye", "İlk 100 yıllık destekçiye verilir"),
        ("gece", "Gece Planlayıcısı", "Kartlarının çoğu 22:00–02:00 arası"),
        ("sabah", "Sabah Kuşu", "Kartlarının çoğu 05:00–09:00 arası"),
        ("hafta-sonu", "Hafta Sonu Gezgini", "Konumlu kartların çoğu hafta sonu"),
        ("listeci", "Listeci", "Kartlarının çoğu kontrol listesi"),
        ("muzik", "Müzik Kutusu", "Son 30 günde 5 karta şarkı"),
        ("kesifci", "Keşifçi", "REAL'de yenisin"),
        ("gezgin", "Gezgin", "Gezgin başarımı"),
        ("sarsilmaz", "Sarsılmaz", "100 gün üst üste başarımı"),
        ("kitap", "Kitap Kurdu", "Yakında"),
        ("takim", "Takım Oyuncusu", "Yakında"),
        ("zaman", "Tam Zamanında", "Yakında")
    ]

    private static func date(_ ms: EpochMillis) -> Date { Date(timeIntervalSince1970: Double(ms) / 1000) }

    /// Counts every achievement and title from [cards], opens earned rewards and keeps earned titles.
    static func evaluate(cards: [ReminderItem], joinedAt: Date?) -> (progress: [Progress], titles: [Title]) {
        let cal = Calendar.current
        let completed = cards.filter { $0.completedAt != nil }.count
        let moodDays = Set(cards.filter { $0.mood != nil }.map { key(date($0.createdAt)) }).count
        let days = Set(UserDefaults.standard.stringArray(forKey: daysKey) ?? [])
        let run = max(UserDefaults.standard.integer(forKey: bestKey), streak(ending: Date(), in: days))
        let joinedDays = joinedAt.map { max(0, Int(Date().timeIntervalSince($0) / 86_400)) } ?? 0
        // "Cities" without a city field: places at least ~20 km apart (0.2° grid cells).
        let regions = Set(cards.compactMap { c -> String? in
            guard let lat = c.location?.latitude, let lng = c.location?.longitude else { return nil }
            return "\(Int(floor(lat / 0.2))):\(Int(floor(lng / 0.2)))"
        }).count
        let newYear = cards.contains { c in
            guard let at = c.alertAt else { return false }
            let d = cal.dateComponents([.month, .day], from: date(at))
            return (d.month == 12 && (d.day ?? 0) >= 24) || (d.month == 1 && d.day == 1)
        }
        let values: [String: Int] = [
            "ilk-kart": cards.count, "ilk-tamam": completed,
            "karta-sarki": cards.filter { !$0.musicBlocks.isEmpty }.count, "karta-konum": cards.filter { $0.location != nil }.count,
            "gun-7": run, "gun-30": run, "gun-100": run,
            "tamam-25": completed, "tamam-100": completed, "tamam-1000": completed,
            "gezgin": regions, "yil-1": joinedDays,
            "ruh-ilk": cards.filter { $0.mood != nil }.count, "ruh-7": moodDays,
            "yilbasi": newYear ? 1 : 0
        ]
        let progress = all.map { Progress(def: $0, now: min(values[$0.id] ?? 0, $0.goal)) }
        let rewards = progress.filter(\.done).flatMap(\.def.rewards)
        if !rewards.isEmpty { AvatarUnlocks.addEarned(rewards) }

        // Titles: the last 30 days with a threshold, so one late night does not decide; earned ones are kept.
        let since = Date().addingTimeInterval(-30 * 86_400)
        let recent = cards.filter { date($0.createdAt) >= since }
        func share(_ test: (ReminderItem) -> Bool) -> Double { recent.isEmpty ? 0 : Double(recent.filter(test).count) / Double(recent.count) }
        func hour(_ c: ReminderItem) -> Int { cal.component(.hour, from: date(c.createdAt)) }
        let placed = recent.filter { $0.location != nil }
        let weekend = placed.filter { cal.isDateInWeekend(date($0.alertAt ?? $0.createdAt)) }.count
        let done = Set(progress.filter(\.done).map(\.def.id))
        let checks: [String: Bool] = [
            // First 100 yearly paying members: opens from the server's founder data once that programme exists.
            "oncu": false,
            "gece": recent.count >= 15 && share { hour($0) >= 22 || hour($0) < 2 } > 0.5,
            "sabah": recent.count >= 15 && share { (5...8).contains(hour($0)) } > 0.5,
            "hafta-sonu": weekend >= 5 && weekend * 2 > placed.count,
            "listeci": recent.filter(\.isChecklist).count >= 10 && share(\.isChecklist) > 0.4,
            "muzik": recent.filter { !$0.musicBlocks.isEmpty }.count >= 5,
            "kesifci": cards.count < 15 || (joinedAt.map { Date().timeIntervalSince($0) < 14 * 86_400 } ?? false),
            "gezgin": done.contains("gezgin"),
            "sarsilmaz": done.contains("gun-100")
        ]
        var kept = Set(UserDefaults.standard.stringArray(forKey: titlesKey) ?? [])
        let before = kept
        kept.formUnion(checks.filter(\.value).map(\.key))
        if kept != before { UserDefaults.standard.set(kept.sorted(), forKey: titlesKey) }
        let titles = titleDefs.map { Title(id: $0.0, label: $0.1, rule: $0.2, earned: kept.contains($0.0)) }
        return (progress, titles)
    }
}

// MARK: - Profile screen

private enum CardTier { case tester, pro, member }

private struct Stamp: Identifiable { let id: String, big: String, small: String, color: Color, tilt: Double }

private func stampFor(_ id: String) -> Stamp? {
    switch id {
    case "gun-100": return Stamp(id: id, big: "100", small: "GÜN", color: Color(argb: 0xFF8ED9B6), tilt: 6)
    case "tamam-1000": return Stamp(id: id, big: "1000", small: "KART", color: Color(argb: 0xFF8FA8E6), tilt: -4)
    case "gezgin": return Stamp(id: id, big: "10", small: "ŞEHİR", color: Color(argb: 0xFF78C9F2), tilt: 9)
    case "yil-1": return Stamp(id: id, big: "1", small: "YIL", color: Color(argb: 0xFFC9C9D0), tilt: -10)
    case "kitap-kurdu": return Stamp(id: id, big: "10", small: "KİTAP", color: Color(argb: 0xFFDAB7F3), tilt: 5)
    case "tester": return Stamp(id: id, big: "T", small: "TEST", color: Color(argb: 0xFFE5B84D), tilt: 4)
    case "kurucu-ekip": return Stamp(id: id, big: "K", small: "KURUCU", color: Color(argb: 0xFF5FD3A0), tilt: -7) // admin panel: "Kurucu Üye"
    case "hata-avcisi": return Stamp(id: id, big: "H", small: "HATA", color: Color(argb: 0xFFF48FAE), tilt: 7)
    default: return nil
    }
}

/// Titles that come with an admin-given stamp (admin panel → Kart damgaları).
private let adminTitles = ["kurucu-ekip": "Kurucu Üye", "tester": "Tester", "hata-avcisi": "Hata Avcısı"]

private let muted = Color(argb: 0xFFA4A4AD)
private let faint = Color(argb: 0xFF8A8A93)
private let cardInk = Color(argb: 0xFF0B0B0C)
private let line = Color(argb: 0xFF2A2A2E)
private func mono(_ size: CGFloat) -> Font { .system(size: size, weight: .medium, design: .monospaced) }

/// The avatar's own background (editor → Arka plan), from its saved state; Humation's default when there is none.
@MainActor private func avatarBackground() -> Color {
    let json = (try? JSONSerialization.jsonObject(with: Data(AvatarStore.stateJSON.utf8))) as? [String: Any]
    let hex = ((json?["background"] as? String) ?? "").replacingOccurrences(of: "#", with: "")
    guard hex.count == 6, let value = UInt32(hex, radix: 16) else { return Color(argb: 0xFFF6F5F4) }
    return Color(argb: 0xFF00_0000 | value)
}

struct ProfileScreen: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(ReminderStore.self) private var store
    @AppStorage("profile_photo_revision") private var revision: Double = 0
    @AppStorage("profile_title") private var titleId: String = ""
    @State private var access = ProAccess.shared
    @State private var progress: [Achievements.Progress] = []
    @State private var titles: [Achievements.Title] = []
    @State private var granted: [String] = []
    @State private var showEditor = false
    @State private var showPicker = false
    @State private var photoItem: PhotosPickerItem?
    @State private var showTitles = false
    @State private var showCard = false
    @State private var showAchievements = false

    private var name: String { store.ownerName.trimmingCharacters(in: .whitespaces).isEmpty ? t("Kullanıcı") : store.ownerName }
    private var photo: Bool { ProfilePhotoStore.kind == "photo" && ProfilePhotoStore.load() != nil }
    private var tier: CardTier {
        if access.snapshot?.tester == true { return .tester }
        if access.snapshot?.isPro == true { return .pro }
        return .member
    }
    private var joinedAt: Date? { Auth.auth().currentUser?.metadata.creationDate }
    private var stamps: [Stamp] {
        let earnedStamps = progress.filter { $0.done && $0.def.stamp }.map(\.def.id)
        var ids: [String] = []
        for id in granted + earnedStamps where !ids.contains(id) { ids.append(id) }
        return ids.compactMap(stampFor)
    }
    private var allTitles: [Achievements.Title] {
        granted.compactMap { id in adminTitles[id].map { Achievements.Title(id: "admin-" + id, label: $0, rule: "", earned: true) } } + titles
    }
    private var titleLabel: String? { allTitles.first { $0.id == titleId && $0.earned }.map { t($0.label) } }

    var body: some View {
        let background = avatarBackground()
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header(t("Profil")) { dismiss() }
                stage(background)
                HStack(spacing: 10) {
                    actionButton(t("Avatarı düzenle"), .edit, filled: true) { showEditor = true }
                    actionButton(t("Fotoğraf yükle"), .photo, filled: false) { showPicker = true }
                }
                .padding(.horizontal, 20)
                VStack(alignment: .leading, spacing: 6) {
                    Text(name).font(RealFont.inter(34, .bold)).foregroundStyle(.white)
                    // The picked title under the name; tapping it opens the picker. With none earned, nothing shows.
                    if titleLabel != nil || allTitles.contains(where: \.earned) {
                        Button { showTitles = true } label: {
                            Text((titleLabel ?? t("Unvan seç")).uppercased() + "  ▾").font(mono(12)).tracking(1)
                                .foregroundStyle(titleLabel != nil ? muted : faint).padding(.vertical, 10)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 20)
                compactCard(background).onTapGesture { showCard = true }
                    .accessibilityAddTraits(.isButton).accessibilityLabel(t("Üye kartını aç"))
                achievementSummary
            }
            .padding(.bottom, 32)
        }
        .background(Color.black.ignoresSafeArea())
        .id(revision)
        .task {
            Achievements.recordOpen()
            let result = Achievements.evaluate(cards: store.cards, joinedAt: joinedAt)
            progress = result.progress; titles = result.titles
            _ = try? await access.refresh()
            if let data = try? await RealBackend.request("/api/access/v1/stamps", timeout: 15),
               let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let list = root["stamps"] as? [[String: Any]] {
                granted = list.compactMap { $0["stamp"] as? String }.filter { ["tester", "kurucu-ekip", "hata-avcisi"].contains($0) }
            }
        }
        .fullScreenCover(isPresented: $showEditor) { AvatarEditorView() }
        .photosPicker(isPresented: $showPicker, selection: $photoItem, matching: .images)
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) {
                    ProfilePhotoStore.save(img)
                    await ProfilePictures.push()
                }
            }
        }
        .sheet(isPresented: $showTitles) { titleSheet.presentationDetents([.medium, .large]) }
        .fullScreenCover(isPresented: $showCard) { cardView(background) }
        .fullScreenCover(isPresented: $showAchievements) { achievementsView }
    }

    // MARK: Parts

    private func header(_ title: String, back: @escaping () -> Void) -> some View {
        ZStack {
            Text(title).font(RealFont.inter(17, .semibold)).foregroundStyle(.white)
            HStack {
                Button(action: back) {
                    Image(systemName: "chevron.left").font(.system(size: 17, weight: .semibold)).foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                }
                .liquidGlass(Circle(), role: .chrome)
                .accessibilityLabel(t("Geri"))
                Spacer()
            }
        }
        .padding(.horizontal, 14).padding(.top, 6)
    }

    private func stage(_ background: Color) -> some View {
        ZStack(alignment: .bottom) {
            background
            if photo, let image = ProfilePhotoStore.load() {
                Image(uiImage: image).resizable().scaledToFill().frame(width: 240, height: 240)
                    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                    .frame(maxHeight: .infinity).accessibilityLabel(t("Profil fotoğrafın"))
            } else if let full = ProfileFullFigure.load() {
                Image(uiImage: full).resizable().scaledToFit().frame(height: 395).accessibilityLabel(t("Karakterin, baştan ayağa"))
            } else if let square = ProfilePhotoStore.load() {
                // Avatars saved before the whole-figure image existed: the square one until the next save.
                VStack(spacing: 14) {
                    Image(uiImage: square).resizable().scaledToFill().frame(width: 250, height: 250).clipShape(Circle())
                    Text(t("Tam boy görünüm için avatarını bir kez kaydet.")).font(RealFont.inter(13)).foregroundStyle(.white)
                        .padding(.horizontal, 12).padding(.vertical, 6).background(.black.opacity(0.45), in: Capsule())
                }
                .frame(maxHeight: .infinity)
            } else {
                VStack(spacing: 10) {
                    ActionIcon(.person, color: .black.opacity(0.75), size: 64)
                    Text(t("Henüz bir avatarın yok.")).font(RealFont.inter(15)).foregroundStyle(.black.opacity(0.75))
                }
                .frame(maxHeight: .infinity)
            }
        }
        .frame(height: 420).frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 36, style: .continuous))
        .padding(.horizontal, 12)
    }

    private func actionButton(_ label: String, _ glyph: ActionGlyph, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                ActionIcon(glyph, color: filled ? Color(argb: 0xFF111111) : .white, size: 18)
                Text(label).font(RealFont.inter(15, .semibold)).foregroundStyle(filled ? Color(argb: 0xFF111111) : .white)
            }
            .frame(maxWidth: .infinity).frame(height: 52)
            .background(filled ? Color(argb: 0xFFF4F4F6) : Color(argb: 0xFF151518), in: Capsule())
            .overlay { if !filled { Capsule().strokeBorder(line, lineWidth: 1) } }
        }
        .buttonStyle(.plain)
    }

    private func tierAccent(_ tier: CardTier) -> Color { tier == .tester ? Color(argb: 0xFFE5B84D) : tier == .pro ? Color(argb: 0xFFC9C9D0) : faint }
    private func tierLabel(_ tier: CardTier) -> String { tier == .tester ? t("TEST EKİBİ") : tier == .pro ? "REAL PRO" : "REAL" }
    private func tierName(_ tier: CardTier) -> String { tier == .tester ? t("Tester kartı") : tier == .pro ? t("Pro kartı") : t("Üye kartı") }
    private func tierArt(_ tier: CardTier, _ background: Color) -> LinearGradient {
        switch tier {
        case .tester: LinearGradient(colors: [Color(argb: 0xFFE5B84D), Color(argb: 0xFFB8862B)], startPoint: .topLeading, endPoint: .bottomTrailing)
        case .pro: LinearGradient(colors: [Color(argb: 0xFF3A3A40), Color(argb: 0xFF1C1C20), Color(argb: 0xFF0E0E10)], startPoint: .topLeading, endPoint: .bottomTrailing)
        case .member: LinearGradient(colors: [background, background], startPoint: .top, endPoint: .bottom)
        }
    }

    /// The figure (or photo) in a card's art box, head to chest.
    @ViewBuilder private func figure(width: CGFloat) -> some View {
        if photo, let image = ProfilePhotoStore.load() {
            Image(uiImage: image).resizable().scaledToFill()
        } else if let full = ProfileFullFigure.load() {
            Image(uiImage: full).resizable().scaledToFit().frame(width: width).frame(maxHeight: .infinity, alignment: .top).offset(y: 10)
        } else if let square = ProfilePhotoStore.load() {
            Image(uiImage: square).resizable().scaledToFill()
        }
    }

    private func stampBadge(_ s: Stamp, size: CGFloat = 54) -> some View {
        let k = size / 54
        return ZStack {
            Circle().fill(cardInk)
            Circle().strokeBorder(s.color, lineWidth: 2 * k)
            Circle().strokeBorder(s.color.opacity(0.7), lineWidth: 0.8).padding(4 * k)
            VStack(spacing: 0) {
                Text(s.big).font(.system(size: (s.big.count >= 4 ? 11 : 15) * k, weight: .heavy)).foregroundStyle(s.color)
                if size >= 44 { Text(s.small).font(mono(6.5)).tracking(0.8).foregroundStyle(s.color) }
            }
        }
        .frame(width: size, height: size).rotationEffect(.degrees(s.tilt))
    }

    private func compactCard(_ background: Color) -> some View {
        HStack(spacing: 14) {
            ZStack(alignment: .top) { tierArt(tier, background); figure(width: 74) }
                .frame(width: 74, height: 92).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            VStack(alignment: .leading, spacing: 6) {
                Text(tierLabel(tier)).font(mono(10)).tracking(1.4).foregroundStyle(tierAccent(tier))
                Text(tierName(tier)).font(RealFont.inter(20, .bold)).foregroundStyle(.white)
                if !stamps.isEmpty {
                    HStack(spacing: -10) { ForEach(stamps.prefix(3)) { stampBadge($0, size: 36) } }
                }
            }
            Spacer(minLength: 0)
            ActionIcon(.chevronRight, color: faint, size: 18)
        }
        .padding(14)
        .background(cardInk, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(line, lineWidth: 1))
        .padding(.horizontal, 20)
        .contentShape(Rectangle())
    }

    private var achievementSummary: some View {
        let counted = progress.filter(\.def.counted)
        let earned = Array(counted.filter(\.done).suffix(3))
        let next = counted.filter { !$0.done }.max { Double($0.now) / Double($0.def.goal) < Double($1.now) / Double($1.def.goal) }
        let shown = earned + (next.map { [$0] } ?? [])
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(t("Başarımlar")).font(RealFont.inter(19, .bold)).foregroundStyle(.white)
                Spacer()
                Button { showAchievements = true } label: {
                    Text(t("Tümü · {0} / {1}", counted.filter(\.done).count, counted.count)).font(RealFont.inter(14, .semibold))
                        .foregroundStyle(.white).padding(.vertical, 10)
                }
                .buttonStyle(.plain)
            }
            HStack(alignment: .top, spacing: 10) {
                ForEach(shown) { p in
                    VStack(spacing: 8) {
                        ZStack {
                            if p.done {
                                Circle().fill(Color(argb: 0xFF3E6E62))
                                ActionIcon(.check, color: Color(argb: 0xFF8ED9B6), size: 26)
                            } else {
                                Circle().strokeBorder(Color(argb: 0xFF55555E), lineWidth: 1.5)
                                Text("\(p.now)/\(p.def.goal)").font(RealFont.inter(13, .bold)).foregroundStyle(muted)
                            }
                        }
                        .frame(width: 64, height: 64)
                        Text(t(p.def.title)).font(RealFont.inter(12)).multilineTextAlignment(.center)
                            .foregroundStyle(p.done ? Color(argb: 0xFFC9C9D0) : faint)
                    }
                    .frame(maxWidth: .infinity)
                    .onTapGesture { showAchievements = true }
                }
                ForEach(0..<max(0, 4 - shown.count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity, maxHeight: 1) }
            }
        }
        .padding(.horizontal, 20)
    }

    private var titleSheet: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(t("Unvan")).font(RealFont.inter(21, .bold)).foregroundStyle(.white)
                Text(t("Profilinde ve kartında adının altında yazar. Seçmezsen bir şey yazmaz.")).font(RealFont.inter(14))
                    .foregroundStyle(muted).padding(.top, 6).padding(.bottom, 12)
                titleRow(t("Unvan yok"), hint: nil, on: titleId.isEmpty, enabled: true) { titleId = ""; showTitles = false }
                ForEach(allTitles.sorted { $0.earned && !$1.earned }) { title in
                    titleRow(t(title.label), hint: title.earned ? nil : t(title.rule), on: titleId == title.id, enabled: title.earned) {
                        titleId = title.id; showTitles = false
                    }
                }
            }
            .padding(20)
        }
        .background(Color(argb: 0xFF111113).ignoresSafeArea())
    }

    private func titleRow(_ label: String, hint: String?, on: Bool, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).font(RealFont.inter(15, .medium)).foregroundStyle(enabled ? .white : faint)
                    if let hint { Text(t("Kilitli · {0}", hint)).font(RealFont.inter(12)).foregroundStyle(faint) }
                }
                Spacer()
                if on { ActionIcon(.check, color: .white, size: 20) }
            }
            .padding(.vertical, 12).contentShape(Rectangle())
        }
        .buttonStyle(.plain).disabled(!enabled)
    }

    private func cardView(_ background: Color) -> some View {
        let joined = joinedAt.map { DateFormatter.localizedString(from: $0, dateStyle: .medium, timeStyle: .none).uppercased() }
        return ScrollView {
            VStack(spacing: 20) {
                header(t("Üye kartı")) { showCard = false }
                VStack(alignment: .leading, spacing: 16) {
                    GeometryReader { geo in
                        ZStack(alignment: .topLeading) {
                            tierArt(tier, background)
                            figure(width: geo.size.width * 0.6).frame(maxWidth: .infinity)
                            Text(tierName(tier)).font(mono(12)).foregroundStyle(.white)
                                .padding(.horizontal, 10).padding(.vertical, 5).background(.black.opacity(0.45), in: Capsule()).padding(12)
                        }
                    }
                    .aspectRatio(1.35, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    VStack(alignment: .leading, spacing: 6) {
                        Text(tierLabel(tier)).font(mono(11)).tracking(1.6).foregroundStyle(tierAccent(tier))
                        Text(name).font(RealFont.inter(30, .bold)).foregroundStyle(.white)
                        if let titleLabel { Text(titleLabel.uppercased()).font(mono(12)).tracking(1).foregroundStyle(muted) }
                    }
                    if !stamps.isEmpty {
                        HStack(spacing: -12) { ForEach(stamps.prefix(3)) { stampBadge($0) } }
                    }
                    HStack {
                        Text(joined.map { t("KATILDI · {0}", $0) } ?? "").font(mono(10)).foregroundStyle(faint)
                        Spacer()
                        Text("Real").font(RealFont.inter(17, .heavy)).foregroundStyle(.white)
                    }
                }
                .padding(18)
                .background(cardInk, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(line, lineWidth: 1))
                .padding(.horizontal, 20)
            }
            .padding(.bottom, 32)
        }
        .background(Color.black.ignoresSafeArea())
    }

    private var achievementsView: some View {
        let counted = progress.filter(\.def.counted)
        return ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                header(t("Başarımlar")) { showAchievements = false }.padding(.bottom, 10)
                HStack {
                    Text(t("Başarımlar")).font(RealFont.inter(19, .bold)).foregroundStyle(.white)
                    Spacer()
                    Text("\(counted.filter(\.done).count) / \(counted.count)").font(mono(14)).foregroundStyle(muted)
                }
                .padding(.horizontal, 20)
                Text(t("Başarımlar REAL'i kullandıkça kendiliğinden sayılır; bitenler avatar düzenleyicide yeni parçalar açar."))
                    .font(RealFont.inter(13)).foregroundStyle(muted).padding(.horizontal, 20)
                ForEach(Achievements.Group.allCases, id: \.self) { group in
                    let rows = progress.filter { $0.def.group == group }
                    if !rows.isEmpty {
                        Text(t(group.rawValue)).font(mono(11)).tracking(1.4).foregroundStyle(faint).padding(.horizontal, 20).padding(.top, 8)
                        ForEach(rows) { achievementRow($0).padding(.horizontal, 20) }
                    }
                }
            }
            .padding(.bottom, 32)
        }
        .background(Color.black.ignoresSafeArea())
    }

    private func achievementRow(_ p: Achievements.Progress) -> some View {
        let fraction = p.def.goal > 0 ? Double(p.now) / Double(p.def.goal) : 0
        return HStack(spacing: 14) {
            ZStack {
                if p.done {
                    Circle().fill(Color(argb: 0xFF3E6E62))
                    ActionIcon(.check, color: Color(argb: 0xFF8ED9B6), size: 22)
                } else {
                    Circle().strokeBorder(Color(argb: 0xFF55555E), lineWidth: 1.5)
                    Text(p.def.counted ? "\(Int(fraction * 100))%" : "…").font(RealFont.inter(11, .bold)).foregroundStyle(muted)
                }
            }
            .frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 5) {
                Text(t(p.def.title)).font(RealFont.inter(15, .semibold)).foregroundStyle(.white)
                Text(t(p.def.detail)).font(RealFont.inter(13)).foregroundStyle(muted)
                if p.def.counted && !p.done {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(line)
                            Capsule().fill(Color(argb: 0xFFF4F4F6)).frame(width: geo.size.width * min(1, fraction))
                        }
                    }
                    .frame(height: 6)
                }
            }
            Spacer(minLength: 0)
            Text(p.def.counted ? "\(p.now) / \(p.def.goal)" : t("Yakında")).font(mono(12)).foregroundStyle(p.def.counted ? muted : faint)
        }
        .padding(14)
        .background(Color(argb: 0xFF111113), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Color(argb: 0xFF222226), lineWidth: 1))
    }
}
