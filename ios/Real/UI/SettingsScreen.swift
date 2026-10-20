import SwiftUI
import PhotosUI
import UserNotifications

// MARK: - Account menu (AccountMenu.kt)

/// AccountMenuOverlay: dim scrim + top glass panel (Hesap · Bu cihazdaki notlar · avatar row · items).
struct AccountMenuOverlay: View {
    let onSettings: () -> Void
    let onCategoryOrder: () -> Void
    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life
    @Environment(PanoStore.self) private var pano
    @Environment(\.dismiss) private var dismiss
    @State private var auth = RealAuth.shared
    @State private var progress: CGFloat = 0
    @State private var photoItem: PhotosPickerItem?
    @State private var showPhotoPicker = false
    @State private var showAuthChoice = false
    @State private var notice: String?
    @State private var showPeople = false
    @State private var showManage = false

    /// Google sign-in, then the account's cards / categories / moods replace this device's (read-only restore).
    /// Hesap ekle: Google or e-posta (AuthChoiceSheet), then the account's cards come back.
    private func signInAndRestore() { showAuthChoice = true }

    private func restore() {
        Task { @MainActor in
            do {
                RealCloudSync.shared.reset()
                let summary = try await RealCloudRestore.run(store: store, life: life)
                var text = "Hesabından \(summary.cards) kart, \(summary.categories) kategori ve \(summary.moods) mood yüklendi"
                if summary.categoryOrder { text += ", kategori sıralaman uygulandı" }
                // Boards come from their own service; a failure there does not undo the cards.
                if let boards = try? await RealBoardsRestore.run(pano: pano) {
                    text += ". \(boards.boards) pano geldi"
                    if boards.skipped > 0 { text += " (\(boards.skipped) blok türü iOS'ta henüz gösterilemiyor)" }
                    await pano.sync()
                } else {
                    text += ". Panolar şu anda alınamadı"
                }
                notice = text + (summary.files > 0 ? ". \(summary.files) fotoğraf, dosya ve ses kaydı arka planda iniyor." : ".")
            } catch let failure as AppCheckUnavailable {
                notice = "Kartlar alınamadı: uygulama doğrulaması yapılamadı (\(failure.reason))."
            } catch let failure as RealBackend.Failure {
                notice = "Kartlar alınamadı (sunucu: \(failure.status))."
            } catch {
                notice = "Kartlar alınamadı. İnternet bağlantını kontrol et."
            }
        }
    }

    var body: some View {
        ZStack(alignment: .top) {
            Color.black.opacity(0.40 * progress).ignoresSafeArea()
                .onTapGesture { close() }
                .accessibilityLabel("Hesap menüsünü kapat")
            VStack(alignment: .leading, spacing: 0) {
                Text("Hesap").font(RealFont.inter(21, .bold)).foregroundStyle(Color(argb: 0xFFF7F7F8))
                Text(auth.account?.email ?? "Bu cihazdaki notlar").font(RealFont.inter(14, .medium)).foregroundStyle(Color(argb: 0xFFA4A4AD)).padding(.top, 12)
                HStack(spacing: 12) {
                    ProfileAvatar(name: store.ownerName, size: 44)
                    Text(store.ownerName.trimmingCharacters(in: .whitespaces).isEmpty ? "Kullanıcı" : store.ownerName)
                        .font(RealFont.inter(16, .medium)).foregroundStyle(Color(argb: 0xFFF7F7F8))
                }
                .padding(.top, 13)
                VStack(spacing: 3) {
                    item(.person, "Fotoğrafın veya avatarın") { showPhotoPicker = true }
                    item(.star, "REAL Pro") { notice = "REAL Pro aboneliği iOS'ta REAL hesabı ve App Store bağlantısı kurulunca açılacak." }
                    if auth.account == nil {
                        item(.add, auth.busy ? "Google açılıyor…" : "Hesap ekle") { signInAndRestore() }
                            .sheet(isPresented: $showAuthChoice) { AuthChoiceSheet { _ in restore() } }
                    } else {
                        item(.manage, "Hesapları yönet") { showManage = true }
                        item(.person, "Kişilerim") { showPeople = true }
                    }
                    item(.category, "Kategori sıralaması") { close(then: onCategoryOrder) }
                    item(.settings, "Ayarlar") { close(then: onSettings) }
                }
                .padding(.top, 12)
            }
            .padding(.horizontal, 18).padding(.vertical, 15)
            .frame(maxWidth: 380, alignment: .leading)
            .liquidGlass(RoundedRectangle(cornerRadius: 30, style: .continuous), role: .sheet)
            .padding(.horizontal, 20)
            .padding(.top, 14)
            .scaleEffect(0.96 + 0.04 * progress)
            .offset(y: (1 - progress) * -16)
            .opacity(progress)
        }
        .onAppear { withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.26)) { progress = 1 } }
        .photosPicker(isPresented: $showPhotoPicker, selection: $photoItem, matching: .images)
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) {
                    ProfilePhotoStore.save(img)
                    await ProfilePictures.push()
                }
            }
        }
        .alert("REAL", isPresented: Binding(get: { notice != nil }, set: { if !$0 { notice = nil } })) {
            Button("Tamam", role: .cancel) {}
        } message: { Text(notice ?? "") }
        .sheet(isPresented: $showPeople) { PeopleScreen() }
        .sheet(isPresented: $showManage) {
            AccountManageSheet(
                onEditName: { showManage = false; close(then: { RealAuth.shared.nameEditRequested = true }) },
                onRestore: { showManage = false; restore() },
                onSignOut: {
                    showManage = false
                    auth.signOut()
                    notice = "Hesaptan çıkıldı. Kartlar bu cihazda kalmaya devam ediyor."
                })
        }
    }

    private func item(_ glyph: ActionGlyph, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 13) {
                ActionIcon(glyph, color: Color(argb: 0xFFF1F1F5), size: 22).frame(width: 31, height: 31)
                Text(label).font(RealFont.inter(16, .medium)).foregroundStyle(Color(argb: 0xFFF7F7F8))
                Spacer()
            }
            .padding(.vertical, 6).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private func close(then: (() -> Void)? = nil) {
        withAnimation(.easeIn(duration: 0.18)) { progress = 0 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
            withoutAnimation { dismiss() }
            if let then { DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { then() } }
        }
    }
}

/// Profile photo chosen in the account menu (AvatarEditorActivity stores the user's photo the same way).
enum ProfilePhotoStore {
    static var url: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("profile_photo.jpg")
    }
    static func save(_ image: UIImage) {
        let side: CGFloat = 512
        let scale = side / max(1, min(image.size.width, image.size.height))
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let thumb = UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? thumb.jpegData(compressionQuality: 0.88)?.write(to: url, options: .atomic)
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "profile_photo_revision")
    }
    static func load() -> UIImage? { UIImage(contentsOfFile: url.path) }
}

// MARK: - Settings (SettingsScreen.kt)

struct SettingsScreen: View {
    let onBack: () -> Void
    @State private var sharePhoto = ProfilePictures.sharePhoto
    @Environment(LifeStore.self) private var life
    @Environment(ReminderStore.self) private var store
    @AppStorage("reduce_animations") private var reduceAnimations = false
    @AppStorage("reduce_dynamic_blur") private var reduceDynamicBlur = false
    @AppStorage("context_cards_enabled") private var contextCards = true
    @State private var notificationsAllowed = true
    @State private var page: Page = .main
    @State private var showLicenses = false
    @State private var showAiPrivacy = false
    @State private var showClearMoods = false
    @State private var showRatingReset = false
    @State private var showSuggestionReset = false
    @Environment(DiscoveryStore.self) private var discovery
    @State private var showCalendar = false
    @State private var notice: String?
    @Environment(\.openURL) private var openURL

    private enum Page { case main, developer, sources }

    var body: some View {
        SettingsLayout(onBack: {
            if page != .main { withAnimation(.easeOut(duration: 0.2)) { page = .main } } else { onBack() }
        }) {
            switch page {
            case .main: mainPage
            case .developer: developerPage
            case .sources: sourcesPage
            }
        }
        .task { await refreshNotificationStatus() }
        .sheet(isPresented: $showLicenses) { LicensesSheet() }
        .sheet(isPresented: $showAiPrivacy) { AiPrivacySheet() }
        .fullScreenCover(isPresented: $showCalendar) { CalendarConnectionScreen { showCalendar = false }.liftedTopSafeArea() }
        .alert("Önerileri sıfırlamak istediğine emin misin?", isPresented: $showSuggestionReset) {
            Button("Sıfırla", role: .destructive) {
                MusicDiscoveryStore.shared.resetSeen()
                discovery.resetSeen()
                RealToast.shared.show("Öneriler sıfırlandı")
            }
            Button("Vazgeç", role: .cancel) {}
        } message: { Text("Müzik, film, dizi ve oyunda geçtiklerin ve mevcut desteler temizlenir. Kaydettiğin listeler ile İzledim / Oynadım dediklerin kalır.") }
        .alert("Puanları sıfırlamak istediğine emin misin?", isPresented: $showRatingReset) {
            Button("Sıfırla", role: .destructive) {
                MediaRatingStore.shared.reset(watched: discovery.film.watched)
                RealToast.shared.show("Puanlar sıfırlandı")
            }
            Button("Vazgeç", role: .cancel) {}
        } message: { Text("Puanlarım listesi boşalır. Bu geri alınamaz.") }
        .alert("Tüm mood kayıtlarını sil?", isPresented: $showClearMoods) {
            Button("Sil", role: .destructive) {
                life.clearAllMoods()
                for r in store.reminders where r.mood != nil { store.mutate(r.id) { $0.mood = nil } }
            }
            Button("Vazgeç", role: .cancel) {}
        } message: { Text("Moods kayıtları ve REAL kartlarındaki Mood blokları silinecek. Günlükler korunacak.") }
        .alert("REAL", isPresented: Binding(get: { notice != nil }, set: { if !$0 { notice = nil } })) {
            Button("Tamam", role: .cancel) {}
        } message: { Text(notice ?? "") }
    }

    @AppStorage(HomeCardStyle.plainKey) private var plainCards = false
    @AppStorage(CalendarCards.homeDaysKey) private var calendarHomeDays = 0
    @State private var musicSource = MusicSource.saved

    @ViewBuilder
    private var mainPage: some View {
        SettingsSectionHeader(title: "GÖRÜNÜM")
        SettingsGlassGroup {
            SettingsToggleRow(icon: .motionOff, title: "Animasyonu azalt", subtitle: "Arayüz hareketlerini ve geçiş efektlerini sakinleştirir", isOn: $reduceAnimations)
            SettingsDivider()
            SettingsToggleRow(icon: .blurOff, title: "Dinamik bulanıklığı azalt", subtitle: "Bulanıklık efektleri yerine sabit koyu yüzeyler kullanır", isOn: $reduceDynamicBlur)
            SettingsDivider()
            SettingsToggleRow(icon: .cat, title: "REAL Pet", subtitle: "Geçici olarak devre dışı bırakıldı", isOn: .constant(false), enabled: false)
            SettingsDivider()
            SettingsToggleRow(icon: .bell, title: "Bağlam kartları", subtitle: "Ana ekranın altında arada su, mood, kitap ve film önerisi gösterir", isOn: $contextCards)
            SettingsDivider()
            SettingsActionRow(icon: .calendar, title: "Takvim bağlantısı", subtitle: "Google / iCloud takvimlerini göster ve planlarını gönder") {
                showCalendar = true
            }
            SettingsDivider()
            SettingsToggleRow(icon: .check, title: "Sade kartlar (Deneysel)",
                              subtitle: "Ana ekranda tamamlama yuvarlağı yok; kartı sağa kaydırınca tamamlanır.", isOn: $plainCards)
            SettingsDivider()
            // Kapalı → 7 gün → 14 gün, one tap each.
            SettingsActionRow(icon: .calendar, title: "Ana ekranda takvim",
                              subtitle: calendarHomeDays > 0 ? "Önümüzdeki \(calendarHomeDays) gün" : "Kapalı") {
                calendarHomeDays = calendarHomeDays == 0 ? 7 : (calendarHomeDays == 7 ? 14 : 0)
            }
            SettingsDivider()
            // Where songs open (music discovery's up-swipe, the music search's default).
            SettingsActionRow(icon: .music, title: "Müzik uygulaması", subtitle: musicSource.label + " · şarkılar burada açılır") {
                musicSource = musicSource == .apple ? .spotify : .apple
                MusicSource.saved = musicSource
            }
            SettingsDivider()
            // Puanlarım: film / dizi / bölüm puanlarını siler (şimdilik test için).
            // Öneriler (müzik, film / dizi, oyun): geçilenler yeniden gelebilir; kayıtlı listeler ve İzledim / Oynadım kalır.
            SettingsActionRow(icon: .music, title: "Önerileri sıfırla", subtitle: "Müzik, film, dizi ve oyunda geçtiklerin yeniden gelebilir; listelerin silinmez") {
                showSuggestionReset = true
            }
            SettingsDivider()
            SettingsActionRow(icon: .delete, title: "Puanlarımı sıfırla", subtitle: "Film, dizi ve bölüm puanlarının hepsini siler") {
                showRatingReset = true
            }
        }
        SettingsSectionHeader(title: "BİLDİRİMLER VE DAVRANIŞ")
        SettingsGlassGroup {
            SettingsStatusRow(icon: .bell, title: "Bildirim izni", status: notificationsAllowed ? "Açık" : "Kapalı",
                              statusColor: notificationsAllowed ? Color(argb: 0xFF91B9FF) : Color(argb: 0xFFA5A5AF),
                              subtitle: notificationsAllowed ? "Hatırlatıcı bildirimleri bu cihazda etkin." : "Bildirimleri alabilmek için bildirim iznini açabilirsin.",
                              actionLabel: notificationsAllowed ? nil : "İzni aç") {
                NotificationScheduler.requestAuthorization()
                if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
            }
            SettingsDivider()
            // Android's "Hatırlatıcı kanalı" is a per-channel setting; iOS keeps sound/banners in the app's notification settings.
            SettingsStatusRow(icon: .settings, title: "Hatırlatıcı kanalı", status: notificationsAllowed ? "Açık" : "Kapalı",
                              statusColor: notificationsAllowed ? Color(argb: 0xFF91B9FF) : Color(argb: 0xFFA5A5AF),
                              subtitle: "Ses, titreşim ve kilit ekranı uyarıları iOS bildirim ayarlarından yönetilir.",
                              actionLabel: "Kanal ayarları") {
                if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
            }
            SettingsDivider()
            SettingsStatusRow(icon: .calendar, title: "Tam saatinde hatırlatma", status: "Açık", statusColor: Color(argb: 0xFF91B9FF),
                              subtitle: "Alarmlar tam zamanında çalacak şekilde ayarlandı.", actionLabel: nil, action: {})
        }
        SettingsSectionHeader(title: "REAL PRO")
        SettingsGlassGroup {
            SettingsActionRow(icon: .star, title: "REAL Pro", subtitle: "Pro’yu keşfet, mağaza aboneliklerini yönet") {
                notice = "REAL Pro aboneliği iOS'ta REAL hesabı ve App Store bağlantısı kurulunca açılacak."
            }
        }
        SettingsSectionHeader(title: "GİZLİLİK")
        SettingsGlassGroup {
            SettingsActionRow(icon: .note, title: "Veri kullanımı", subtitle: "Hesap, bulut, paylaşım ve cihaz izinleri") {
                notice = "Kartların, kategorilerin, su, mood ve kitap kayıtların bu iPhone'da saklanıyor. REAL hesabınla giriş yaptığında kartlar, kategoriler, mood ve su kayıtları hesabınla iki yönlü eşitlenir; durumu Hesapları yönet → Eşitleme'de görürsün."
            }
            SettingsDivider()
            SettingsActionRow(icon: .note, title: "AI ve gizlilik", subtitle: "Açıklamalar ve veri aktarımı onayları") {
                showAiPrivacy = true
            }
            SettingsDivider()
            SettingsActionRow(icon: .note, title: "Veri hakları ve talepler", subtitle: "Kopya, düzeltme, silme veya itiraz talebi") {
                notice = "Hesap verilerin için kopya, düzeltme veya silme talebini şimdilik Android uygulamasından ya da gizlilik sayfasındaki adresten yapabilirsin. Bu iPhone'daki verileri uygulamayı silerek tamamen kaldırabilirsin."
            }
            SettingsDivider()
            // Off by default: the profile photo goes to Kişilerim and shared-category people only when this is on.
            SettingsToggleRow(icon: .person, title: "Fotoğrafımı kişilerimle paylaş",
                              subtitle: "Profil fotoğrafın Kişilerim'de ve ortak kategorilerde görünür",
                              isOn: Binding(get: { sharePhoto }, set: { on in
                                  sharePhoto = on
                                  ProfilePictures.sharePhoto = on
                                  Task { await ProfilePictures.push() }
                              }))
        }
        SettingsSectionHeader(title: "HAKKINDA")
        SettingsGlassGroup {
            HStack(spacing: 14) {
                ActionIcon(.checklist, color: .white, size: 20)
                VStack(alignment: .leading, spacing: 0) {
                    Text("REAL").font(RealFont.inter(15, .medium)).foregroundStyle(.white)
                    Text("v" + (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "-"))
                        .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA3A3AB))
                }
                Spacer()
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            SettingsDivider()
            SettingsActionRow(icon: .note, title: "Açık kaynak lisansları", subtitle: "Humation, Tabler Icons ve üçüncü taraf bildirimleri") { showLicenses = true }
            SettingsDivider()
            SettingsActionRow(icon: .note, title: "Veri kaynakları ve lisanslar", subtitle: "TheTVDB, TVmaze ve IGDB") {
                withAnimation(.easeOut(duration: 0.2)) { page = .sources }
            }
        }
        SettingsSectionHeader(title: "GELİŞTİRİCİ")
        SettingsGlassGroup {
            SettingsActionRow(icon: .settings, title: "Geliştirici araçları", subtitle: "Tanılama, testler ve tasarım demoları") {
                withAnimation(.easeOut(duration: 0.2)) { page = .developer }
            }
        }
        Color.clear.frame(height: 40)
    }

    @ViewBuilder
    private var developerPage: some View {
        SettingsSectionHeader(title: "GELİŞTİRİCİ ARAÇLARI")
        SettingsSectionHeader(title: "BAĞLAM KARTLARI TESTİ")
        SettingsGlassGroup {
            let tests: [(ContextCardType, ActionGlyph, String)] = [(.water, .bell, "Su kartını göster"), (.mood, .moodNeutral, "Mood kartını göster"),
                                                                  (.book, .note, "Kitap kartını göster"), (.media, .video, "Film & dizi kartını göster"),
                                                                  (.watch, .play, "Sıradaki bölüm kartını göster"), (.listen, .music, "Müzik kartını göster"),
                                                                  (.play, .cards, "Oyun kartını göster"), (.journal, .edit, "Günlük kartını göster")]
            SettingsActionRow(icon: .cards, title: "Tüm kartları göster", subtitle: "Sadece test: bütün türler sırayla gelir (normalde günde en fazla 2 kart)") {
                ContextCardStore.forceAll()
                notice = "Kartlar ana ekranda Kartlarım'ın en üstünde, sırayla görünecek."
            }
            SettingsDivider()
            ForEach(Array(tests.enumerated()), id: \.offset) { i, t in
                if i > 0 { SettingsDivider() }
                SettingsActionRow(icon: t.1, title: t.2, subtitle: "Saat ve limit kurallarını yok sayar; Kartlarım'ın en üstünde görünür") {
                    ContextCardStore.force(t.0)
                    notice = "Kart ana ekranda Kartlarım'ın en üstünde görünecek."
                }
            }
            SettingsDivider()
            SettingsActionRow(icon: .undo, title: "Kart geçmişini sıfırla", subtitle: "Günlük limit, bekleme ve sessize alma sayaçlarını temizler") {
                ContextCardStore.update { ContextCardHistory(enabled: $0.enabled) }
                notice = "Bağlam kartı geçmişi sıfırlandı."
            }
        }
        SettingsSectionHeader(title: "MOOD TEST VE VERİ")
        SettingsGlassGroup {
            VStack(alignment: .leading, spacing: 10) {
                Text("Mood Test ve Veri").font(RealFont.inter(15)).foregroundStyle(.white)
                Text("Mood kayıtlarını geliştirme sırasında sıfırla").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA5A5AF))
                Button { showClearMoods = true } label: {
                    Text("Tüm moodları sil").font(RealFont.inter(13, .semibold)).foregroundStyle(Color(argb: 0xFFFF6B72))
                }
                .buttonStyle(.plain)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16).padding(.vertical, 14)
        }
        Color.clear.frame(height: 40)
    }

    @ViewBuilder
    private var sourcesPage: some View {
        SettingsSectionHeader(title: "VERİ KAYNAKLARI VE LİSANSLAR")
        ForEach(CatalogSourceNotice.all, id: \.name) { source in
            SettingsGlassGroup {
                SettingsActionRow(icon: .note, title: source.name, subtitle: "Resmî kaynağı aç") { if let u = URL(string: source.website) { openURL(u) } }
                Text(source.summary).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFA3A3AB)).lineSpacing(6)
                    .padding(.horizontal, 16).padding(.vertical, 12)
                SettingsDivider()
                SettingsActionRow(icon: .note, title: "Lisans ve kullanım koşulları", subtitle: source.name) { if let u = URL(string: source.terms) { openURL(u) } }
            }
            .padding(.bottom, 10)
        }
        Color.clear.frame(height: 40)
    }

    private func refreshNotificationStatus() async {
        let s = await UNUserNotificationCenter.current().notificationSettings()
        notificationsAllowed = s.authorizationStatus == .authorized || s.authorizationStatus == .provisional
    }
}

struct CatalogSourceNotice {
    let name: String
    let summary: String
    let website: String
    let terms: String

    static let all: [CatalogSourceNotice] = [
        CatalogSourceNotice(name: "TheTVDB", summary: "Film bilgileri TheTVDB tarafından sağlanır. API kullanımı TheTVDB'nin proje lisansı ve kullanım koşullarına tabidir. Afişler ve oyuncu görselleri ilgili hak sahiplerine aittir; API erişimi görsel kullanım haklarını otomatik olarak vermez.", website: "https://thetvdb.com/", terms: "https://www.thetvdb.com/api-information"),
        CatalogSourceNotice(name: "TVmaze", summary: "Dizi bilgileri TVmaze tarafından sağlanır. API verileri CC BY-SA lisansıyla sunulur; kaynak gösterme ve uyarlanmış materyal için aynı lisans koşullarını koruma yükümlülüğü vardır. Kapaklar ve oyuncu görselleri ilgili hak sahiplerine aittir.", website: "https://www.tvmaze.com/", terms: "https://www.tvmaze.com/api#licensing"),
        CatalogSourceNotice(name: "IGDB", summary: "Oyun bilgileri ve kapaklar IGDB'den alınır. IGDB, ticari kullanım için partner başvurusu ve görünür kaynak gösterimi ister. API'nin ücretsiz sunulması bu başvuru koşulunu kaldırmaz. Oyun kapakları ve marka hakları ilgili hak sahiplerine aittir.", website: "https://www.igdb.com/", terms: "https://api-docs.igdb.com/#business-related-faq"),
        CatalogSourceNotice(name: "Apple iTunes", summary: "Müzik araması, kapaklar ve önizleme bağlantıları Apple iTunes Search API'den alınır. Önizlemeler tanıtım amaçlıdır; indirilip kitaplığa kaydedilmez. Apple'ın önizleme, kaynak gösterimi ve mağazaya bağlantı koşulları geçerlidir.", website: "https://www.apple.com/itunes/", terms: "https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/index.html"),
        CatalogSourceNotice(name: "OpenFreeMap / OpenStreetMap", summary: "Harita karoları OpenFreeMap üzerinden gelir. OpenMapTiles ve OpenStreetMap katkıda bulunanları harita verisinin kaynaklarıdır. Haritada görünen kaynak gösterimi korunmalıdır; bu sayfa harita üzerindeki kaynak gösteriminin yerine geçmez.", website: "https://openfreemap.org/", terms: "https://osmfoundation.org/wiki/Licence/Attribution_Guidelines"),
    ]
}

// MARK: - Layout pieces (LiquidSettingsLayout.kt + SettingsScreen.kt private composables)

/// SettingsLayout: black backdrop with soft glows, a top glass bar (back · "Ayarlar") and a large glass sheet body.
struct SettingsLayout<Content: View>: View {
    let onBack: () -> Void
    var title = "Ayarlar"
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack(alignment: .top) {
            GeometryReader { g in
                ZStack {
                    Color.black
                    RadialGradient(colors: [.white.opacity(0.075), .clear], center: UnitPoint(x: 0.82, y: 0.08), startRadius: 0, endRadius: g.size.width * 0.78)
                    RadialGradient(colors: [.white.opacity(0.055), .clear], center: UnitPoint(x: 0.12, y: 0.82), startRadius: 0, endRadius: g.size.width * 0.92)
                    LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: Color(argb: 0x12000000), location: 0.64),
                                           .init(color: Color(argb: 0xA6000000), location: 1)], startPoint: .top, endPoint: .bottom)
                }
            }
            .ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) { content() }
                    .padding(.horizontal, 14).padding(.top, 8).padding(.bottom, 28)
            }
            .scrollIndicators(.hidden)
            .liquidGlass(RoundedRectangle(cornerRadius: 34, style: .continuous), role: .sheet)
            .padding(.horizontal, 12).padding(.top, 70).padding(.bottom, 10)
            ZStack {
                HStack {
                    Button(action: onBack) {
                        ActionIcon(.back, color: .white, size: 20).frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Geri")
                    Spacer()
                }
                Text(title).font(RealFont.inter(18, .semibold)).kerning(-0.3).foregroundStyle(.white)
            }
            .padding(.horizontal, 4)
            .frame(height: 52)
            .liquidGlass(RoundedRectangle(cornerRadius: 26, style: .continuous), role: .chrome)
            .padding(.horizontal, 16).padding(.top, 8)
        }
        .background(Color.black.ignoresSafeArea())
    }
}

struct SettingsSectionHeader: View {
    let title: String
    var body: some View {
        Text(title).font(RealFont.inter(11.5, .semibold)).kerning(1.2).foregroundStyle(Color(argb: 0xFFA3A3AB).opacity(0.9))
            .padding(.leading, 12).padding(.top, 22).padding(.bottom, 9)
    }
}

struct SettingsGlassGroup<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .padding(.vertical, 4)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(LinearGradient(colors: [Color(argb: 0xB5202022), Color(argb: 0xCC0D0D0F)], startPoint: .top, endPoint: .bottom)))
            .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(liquidRim(.chrome), lineWidth: 0.65))
    }
}

struct SettingsDivider: View {
    var body: some View {
        Rectangle().fill(.white.opacity(0.07)).frame(height: 0.75).padding(.leading, 64).padding(.trailing, 16)
    }
}

/// SettingsIconTile: 36pt liquid tile holding a 19pt glyph.
struct SettingsIconTile: View {
    let icon: ActionGlyph
    var body: some View {
        ActionIcon(icon, color: .white, size: 19)
            .frame(width: 36, height: 36)
            .background(RoundedRectangle(cornerRadius: 11, style: .continuous)
                .fill(LinearGradient(colors: [.white.opacity(0.17), .white.opacity(0.06)], startPoint: .top, endPoint: .bottom)))
            .overlay(RoundedRectangle(cornerRadius: 11, style: .continuous).strokeBorder(liquidRim(.chrome), lineWidth: 0.75))
    }
}

private struct SettingsTexts: View {
    let title: String
    let subtitle: String
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(RealFont.inter(16)).kerning(-0.2).foregroundStyle(.white)
            if !subtitle.isEmpty {
                Text(subtitle).font(RealFont.inter(12.5)).foregroundStyle(Color(argb: 0xFF9A9AA5)).lineSpacing(4).fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

struct SettingsToggleRow: View {
    let icon: ActionGlyph
    let title: String
    let subtitle: String
    @Binding var isOn: Bool
    var enabled = true

    var body: some View {
        HStack(spacing: 14) {
            SettingsIconTile(icon: icon)
            SettingsTexts(title: title, subtitle: subtitle).frame(maxWidth: .infinity, alignment: .leading).padding(.trailing, 12)
            Toggle("", isOn: $isOn).labelsHidden().tint(Color(argb: 0xFF3B82F6)).disabled(!enabled)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .opacity(enabled ? 1 : 0.5)
        .contentShape(Rectangle())
        .onTapGesture { if enabled { isOn.toggle() } }
    }
}

struct SettingsActionRow: View {
    let icon: ActionGlyph
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                SettingsIconTile(icon: icon)
                SettingsTexts(title: title, subtitle: subtitle).frame(maxWidth: .infinity, alignment: .leading).padding(.trailing, 8)
                ActionIcon(.chevronRight, color: Color(argb: 0xFF75757F), size: 16)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct SettingsStatusRow: View {
    let icon: ActionGlyph
    let title: String
    let status: String
    let statusColor: Color
    let subtitle: String
    let actionLabel: String?
    let action: () -> Void

    var body: some View {
        VStack(alignment: .trailing, spacing: 0) {
            HStack(alignment: .top, spacing: 14) {
                SettingsIconTile(icon: icon)
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Text(title).font(RealFont.inter(16)).kerning(-0.2).foregroundStyle(.white)
                        Spacer(minLength: 8)
                        Text(status).font(RealFont.inter(12, .medium)).foregroundStyle(statusColor).lineLimit(1)
                            .padding(.horizontal, 10).padding(.vertical, 3)
                            .background(Capsule().fill(statusColor.opacity(0.14)))
                            .overlay(Capsule().strokeBorder(statusColor.opacity(0.30), lineWidth: 0.75))
                    }
                    .frame(minHeight: 36)
                    Text(subtitle).font(RealFont.inter(12.5)).foregroundStyle(Color(argb: 0xFF9A9AA5)).lineSpacing(4).fixedSize(horizontal: false, vertical: true)
                }
            }
            if let actionLabel {
                Button(action: action) {
                    Text(actionLabel).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFF91B9FF))
                        .padding(.horizontal, 16).padding(.vertical, 8)
                        .background(Capsule().fill(.white.opacity(0.09)))
                        .overlay(Capsule().strokeBorder(liquidRim(.chrome), lineWidth: 0.75))
                }
                .buttonStyle(.plain)
                .padding(.top, 10)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
    }
}

/// LicensesDialog: Humation / Tabler Icons / Inter tabs with the bundled license text.
struct LicensesSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var index = 0
    private let entries = [("Humation", "Humation 1 avatar varlıkları (MIT)", "Humation-MIT"),
                           ("Tabler Icons", "Tabler Icons vektör simge seti (MIT)", "Tabler-Icons-MIT"),
                           ("Inter Font", "Inter değişken yazı tipi (SIL OFL 1.1)", "Inter-OFL")]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Açık kaynak lisansları").font(RealFont.inter(18, .semibold)).foregroundStyle(.white)
                Text(entries[index].1).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF9D9DA7))
            }
            HStack(spacing: 0) {
                ForEach(entries.indices, id: \.self) { i in
                    Button { index = i } label: {
                        Text(entries[i].0).font(RealFont.inter(12, i == index ? .semibold : .regular))
                            .foregroundStyle(i == index ? .white : Color(argb: 0xFF90909A))
                            .frame(maxWidth: .infinity).padding(.vertical, 6)
                            .background(RoundedRectangle(cornerRadius: 8).fill(i == index ? Color(argb: 0xFF333338) : .clear))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(2)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color(argb: 0xFF202024)))
            ScrollView {
                Text(text).font(.system(size: 11, design: .monospaced)).foregroundStyle(Color(argb: 0xFFCBCBD4)).lineSpacing(5)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(12)
            .frame(height: 280)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color(argb: 0xFF0F0F12)))
            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color(argb: 0xFF28282C), lineWidth: 0.75))
            HStack {
                Spacer()
                Button("Kapat") { dismiss() }.font(RealFont.inter(15, .semibold)).foregroundStyle(Color(argb: 0xFF91B9FF))
            }
        }
        .padding(22)
        .presentationDetents([.medium, .large])
        .presentationBackground(Color(argb: 0xFF15161A))
    }

    private var text: String {
        guard let url = Bundle.main.url(forResource: entries[index].2, withExtension: "txt"),
              let s = try? String(contentsOf: url, encoding: .utf8) else { return "Lisans metni okunamadı." }
        return s
    }
}


/// Hesapları yönet (AccountManagementSheet.kt): the account card with "Adını değiştir", reload cards, sign out.
struct AccountManageSheet: View {
    let onEditName: () -> Void
    let onRestore: () -> Void
    let onSignOut: () -> Void
    @State private var auth = RealAuth.shared
    @State private var confirmDelete = false
    @State private var cloud = RealCloudSync.shared
    @State private var syncing = false
    @State private var showIssues = false
    @State private var issueLimit = 5
    /// The choice waiting for "Onayla" (conflict id + choice).
    @State private var pendingChoice: (String, RealCloudSync.Choice)?
    @State private var openConflicts: Set<String> = []
    @State private var bulkConfirm = false
    @State private var localSize = "…"
    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life

    private var syncLine: String {
        if let e = cloud.lastError { return "Son deneme olmadı: \(e)" }
        guard let t = cloud.lastSuccess else { return "Bu oturumda henüz eşitlenmedi." }
        return "Son eşitleme: " + t.formatted(.relative(presentation: .named, unitsStyle: .wide))
    }

    private func syncNow() {
        syncing = true
        Task { await cloud.runFully(store, life); syncing = false }
    }

    private func act(_ work: @escaping () async -> Void) {
        syncing = true
        Task { await work(); syncing = false }
    }

    private func choiceLabel(_ c: RealCloudSync.ConflictInfo, _ choice: RealCloudSync.Choice) -> String {
        switch choice {
        case .keepLocal: return "Bu iPhone'daki sürüm hesaba gönderilecek; hesaptaki sürüm değişecek."
        case .useAccount: return c.remote == nil ? "Kart bu iPhone'dan da kaldırılacak." : "Bu iPhone'da hesaptaki sürüm kullanılacak."
        case .keepAsNewCard: return c.remote == nil
            ? "Kart hesaba yeni bir kart olarak gönderilecek; bu iPhone'da içeriği aynen kalır."
            : "Bu iPhone'daki içerik ayrı bir kartta korunacak; eski karta hesaptaki sürüm uygulanacak."
        }
    }

    @ViewBuilder private func conflictRow(_ c: RealCloudSync.ConflictInfo) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            // One line per conflict; tapping it opens the versions and the choices.
            Button {
                withAnimation(.snappy) { if openConflicts.contains(c.id) { openConflicts.remove(c.id) } else { openConflicts.insert(c.id) } }
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(c.title).font(RealFont.inter(13, .medium)).foregroundStyle(.white).lineLimit(1)
                        Text(c.remote == nil ? "Hesapta silinmiş" : (c.isCard ? "İki tarafta da değişti" : "Kayıt sürümleri çakışıyor"))
                            .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFFFD27A))
                    }
                    Spacer()
                    Image(systemName: openConflicts.contains(c.id) ? "chevron.up" : "chevron.down").font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color(argb: 0xFF9A9AA4))
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if openConflicts.contains(c.id) {
            Text(c.remote == nil ? "Başka bir cihazda silindi ama bu iPhone'da değişmişti. Ne olacağını sen seç."
                                 : "İki tarafta da değişti; hiçbiri ezilmedi. Karşılaştırıp seç.")
                .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A93)).fixedSize(horizontal: false, vertical: true)
            Text("Bu iPhone'daki").font(RealFont.inter(11, .medium)).foregroundStyle(Color(argb: 0xFF6FD6FF))
            Text(c.local).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.85)).fixedSize(horizontal: false, vertical: true)
            Text("Hesaptaki").font(RealFont.inter(11, .medium)).foregroundStyle(Color(argb: 0xFF6FD6FF))
            Text(c.remote ?? "Silinmiş kayıt").font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.85)).fixedSize(horizontal: false, vertical: true)
            if let chosen = pendingChoice, chosen.0 == c.id {
                Text(choiceLabel(c, chosen.1)).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFFFD27A)).fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 14) {
                    Button("Onayla") {
                        let (id, choice) = chosen
                        pendingChoice = nil
                        act { await cloud.resolve(id, choice, store, life) }
                    }
                    Button("Vazgeç") { pendingChoice = nil }
                }
                .font(RealFont.inter(13, .medium)).foregroundStyle(.white).disabled(syncing)
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    if c.isCard && c.remote == nil { Button("Hesaba geri yükle") { pendingChoice = (c.id, .keepAsNewCard) } }
                    if c.remote != nil { Button("Bu iPhone'dakini koru") { pendingChoice = (c.id, .keepLocal) } }
                    Button(c.remote == nil ? "Silmeyi uygula (bu iPhone'dan da kaldır)" : "Hesaptakini kullan") { pendingChoice = (c.id, .useAccount) }
                    if c.isCard && c.remote != nil { Button("Yeni kart olarak sakla") { pendingChoice = (c.id, .keepAsNewCard) } }
                }
                .font(RealFont.inter(13, .medium)).foregroundStyle(.white).disabled(syncing)
            }
            }
        }
        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.white.opacity(0.05)))
    }

    @ViewBuilder private func issueRow(_ issue: RealCloudSync.Issue) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(issue.title).font(RealFont.inter(13, .medium)).foregroundStyle(.white).lineLimit(1)
            Text(issue.reason).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA4A4AD)).fixedSize(horizontal: false, vertical: true)
            Text(issue.hasAccountCopy ? "Bu iPhone'daki sürüm korunuyor. Kartı düzenlersen kendiliğinden tekrar gönderilir."
                                      : "Bu kayıt hesabına hiç ulaşmadı; bu iPhone'daki sürüm korunuyor.")
                .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A93)).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 14) {
                Button("Tekrar dene") { syncNow() }
                if issue.hasAccountCopy { Button("Hesaptakini kullan") { act { await cloud.useAccount(forRefused: issue.id, store, life) } } }
            }
            .font(RealFont.inter(13, .medium)).foregroundStyle(.white).disabled(syncing)
        }
        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.white.opacity(0.05)))
    }

    private func measureLocal() async {
        let dir = AttachmentStorage.directory
        let bytes = await Task.detached { () -> Int64 in
            let files = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.fileSizeKey])) ?? []
            return files.reduce(Int64(0)) { $0 + Int64((try? $1.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0) }
        }.value
        localSize = ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
    }
    @State private var deleting: String?
    @State private var deleteError: String?
    @State private var askDeletePassword = false
    @State private var deletePassword = ""

    /// AccountManagementSheet "Veri ve silme": Google sign-in again (the server wants a fresh one), start the account
    /// erasure, follow it to the end, then delete the Firebase account. Cards on this iPhone stay.
    private func deleteAccount(password: String? = nil) {
        // An e-mail account confirms with its password instead of Google.
        if RealAuth.shared.usesPassword && password == nil { deletePassword = ""; askDeletePassword = true; return }
        deleteError = nil
        deleting = password != nil ? "Şifre doğrulanıyor…" : "Google ile doğrulanıyor…"
        Task {
            do {
                if let password { try await RealAuth.shared.reauthenticateWithPassword(password) }
                else { try await RealAuth.shared.reauthenticateWithGoogle() }
                deleting = "Bulut verileri siliniyor…"
                let op = UUID().uuidString.lowercased()
                var r = try await RealBackend.post("/api/account/v1/erasure",
                    ["operationId": op, "mode": "ACCOUNT", "confirmation": "DELETE_ACCOUNT", "deleteOwnedBoards": true], timeout: 60)
                let operation = ((r["erasure"] as? [String: Any])?["operationId"] as? String) ?? op
                for _ in 0..<40 {
                    if ((r["erasure"] as? [String: Any])?["phase"] as? String) == "COMPLETE" { break }
                    try await Task.sleep(for: .seconds(2))
                    r = try await RealBackend.post("/api/account/v1/erasure/continue", ["operationId": operation], timeout: 60)
                }
                guard ((r["erasure"] as? [String: Any])?["phase"] as? String) == "COMPLETE" else {
                    throw CalendarCardPublisher.Failure(errorDescription: "Silme sürüyor. Birazdan yeniden dene; işlem kaldığı yerden devam eder.")
                }
                deleting = "Hesap siliniyor…"
                RealCloudSync.shared.reset()
                try await RealAuth.shared.deleteFirebaseUser()
                deleting = nil
                onSignOut()
            } catch {
                deleting = nil
                deleteError = (error as? LocalizedError)?.errorDescription ?? RealBackend.message(for: error)
            }
        }
    }

    var body: some View {
        ScrollView {
        VStack(alignment: .leading, spacing: 14) {
            Capsule().fill(Color(argb: 0xFF666669)).frame(width: 34, height: 3).frame(maxWidth: .infinity)
            Text("Hesapları yönet").font(RealFont.inter(26, .semibold)).foregroundStyle(.white)
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 14) {
                    ProfileAvatar(name: auth.account?.displayName ?? "REAL", size: 48)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(auth.account?.displayName ?? "REAL hesabı").font(RealFont.inter(17, .medium)).foregroundStyle(.white).lineLimit(1)
                        if let email = auth.account?.email { Text(email).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFA4A4AD)).lineLimit(1) }
                    }
                }
                HStack(spacing: 10) {
                    Text("Ortak kategorilerde ve paylaşımlarda bu ad görünür.").font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFA4A4AD))
                    Spacer(minLength: 8)
                    Button("Adını değiştir", action: onEditName)
                        .font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                        .padding(.horizontal, 12).padding(.vertical, 7).background(Capsule().fill(.white.opacity(0.10)))
                }
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(.white.opacity(0.05)))
            // Eşitleme ve bu cihazdaki alan (SyncManagementPanel / LocalStoragePanel, short).
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Eşitleme").font(RealFont.inter(15, .medium)).foregroundStyle(.white)
                    Spacer()
                    Button(syncing ? "Eşitleniyor…" : "Şimdi eşitle") { syncNow() }
                        .font(RealFont.inter(13, .medium)).foregroundStyle(.white).disabled(syncing)
                }
                Text(syncLine).font(RealFont.inter(13)).foregroundStyle(cloud.lastError == nil ? Color(argb: 0xFFA4A4AD) : Color(argb: 0xFFFFB4AB))
                Text("Kartlar, kategoriler, mood ve su kayıtları bu iPhone ile hesabın arasında iki yönlü eşitlenir. Bir kart iki tarafta da değişirse ya da hesapta silinirse hiçbiri ezilmez; ne olacağını sen seçersin.")
                    .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A93)).fixedSize(horizontal: false, vertical: true)
                let waiting = cloud.issues.count + cloud.conflicts.count
                if cloud.lastError == nil && cloud.lastSuccess != nil {
                    Text(waiting == 0 ? "Bekleyen kayıt yok." : "\(waiting) kayıt senin seçimini bekliyor · diğerleri eşitlendi")
                        .font(RealFont.inter(13)).foregroundStyle(waiting == 0 ? Color(argb: 0xFFA4A4AD) : Color(argb: 0xFFFFD27A))
                }
                if waiting > 0 {
                    Button(showIssues ? "Ayrıntıları gizle" : "Sorunları ve sürümleri göster") { withAnimation(.snappy) { showIssues.toggle() } }
                        .font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                    if showIssues {
                        // Cards the account deleted while they changed here: one button for all of them.
                        let deletedCards = cloud.conflicts.filter { $0.isCard && $0.remote == nil }
                        if deletedCards.count > 1 {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Hesapta silinmiş \(deletedCards.count) kart").font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                                Text("Bu kartlar bu iPhone'da duruyor ama hesabında silinmiş görünüyor. Geri yüklersen her biri hesaba yeni bir kart olarak gönderilir; bu iPhone'da hiçbir şey kaybolmaz.")
                                    .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A93)).fixedSize(horizontal: false, vertical: true)
                                HStack(spacing: 14) {
                                    if bulkConfirm {
                                        Button("Onayla: \(deletedCards.count) kartı geri yükle") {
                                            bulkConfirm = false
                                            let ids = deletedCards.map(\.id)
                                            act { for id in ids { await cloud.resolve(id, .keepAsNewCard, store, life) } }
                                        }
                                        Button("Vazgeç") { bulkConfirm = false }
                                    } else {
                                        Button("Hepsini hesaba geri yükle") { bulkConfirm = true }
                                    }
                                }
                                .font(RealFont.inter(13, .medium)).foregroundStyle(.white).disabled(syncing)
                            }
                            .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.white.opacity(0.05)))
                        }
                        ForEach(Array((deletedCards + cloud.conflicts.filter { !($0.isCard && $0.remote == nil) }).prefix(issueLimit))) { conflict in conflictRow(conflict) }
                        ForEach(cloud.issues.prefix(issueLimit)) { issue in issueRow(issue) }
                        HStack {
                            if max(cloud.issues.count, cloud.conflicts.count) > issueLimit { Button("Daha fazla göster") { issueLimit += 10 } }
                            Spacer()
                            if !cloud.issues.isEmpty { Button(syncing ? "Deneniyor…" : "Hepsini tekrar dene") { syncNow() }.disabled(syncing) }
                        }
                        .font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                    }
                }
                Text("Bu cihazdaki dosyalar: \(localSize)").font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFA4A4AD))
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(.white.opacity(0.05)))
            Button(action: onRestore) {
                Text("Kartları hesaptan yeniden yükle").font(RealFont.inter(15)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 48).background(Capsule().fill(.white.opacity(0.08)))
            }
            .buttonStyle(.plain)
            Button(action: onSignOut) {
                Text("Hesaptan çık").font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFFFB4AB))
                    .frame(maxWidth: .infinity, minHeight: 48).background(Capsule().fill(.white.opacity(0.05)))
            }
            .buttonStyle(.plain)
            Button { confirmDelete = true } label: {
                Text(deleting ?? "Hesabı sil").font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFFFF8A80))
                    .frame(maxWidth: .infinity, minHeight: 40)
            }
            .buttonStyle(.plain).disabled(deleting != nil)
            if let deleteError { Text(deleteError).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFB4AB)) }
        }
        .padding(22)
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.height(600), .large])
        .task { await measureLocal() }
        .confirmationDialog("Hesabın ve buluttaki tüm verilerin kalıcı olarak silinsin mi?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Hesabı kalıcı olarak sil", role: .destructive) { deleteAccount() }
            Button("Vazgeç", role: .cancel) {}
        } message: {
            Text("Buluttaki kartların, dosyaların, paylaşım linklerin ve sahibi olduğun panolar silinir. Bu iPhone'daki kartlar kalır. Geri alınamaz.")
        }
        .alert("Şifreni yaz", isPresented: $askDeletePassword) {
            SecureField("Şifre", text: $deletePassword)
            Button("Sil", role: .destructive) { deleteAccount(password: deletePassword) }
            Button("Vazgeç", role: .cancel) { deletePassword = "" }
        } message: { Text("Hesabı silmek için e-posta hesabının şifresiyle doğrula.") }
        .presentationBackground(Color(argb: 0xFF111113))
    }
}
