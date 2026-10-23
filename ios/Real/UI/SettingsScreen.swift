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
    @State private var showPhotoChoice = false
    @State private var showAvatarEditor = false
    @State private var showAuthChoice = false
    @State private var notice: String?
    @State private var showPeople = false
    @State private var showManage = false

    /// Google sign-in, then the account's cards / categories / moods replace this device's (read-only restore).
    /// Hesap ekle: Google or e-posta (AuthChoiceSheet), then the account's cards come back.
    private func signInAndRestore() { showAuthChoice = true }

    private func restore(keepGuest: Bool = false) {
        Task { @MainActor in
            do {
                RealCloudSync.shared.reset()
                let summary = try await RealCloudRestore.run(store: store, life: life, keepGuest: keepGuest)
                var text = t("Hesabından {0} kart, {1} kategori ve {2} mood yüklendi", summary.cards, summary.categories, summary.moods)
                if summary.categoryOrder { text += t(", kategori sıralaman uygulandı") }
                // Boards come from their own service; a failure there does not undo the cards.
                if let boards = try? await RealBoardsRestore.run(pano: pano) {
                    text += t(". {0} pano geldi", boards.boards)
                    if boards.skipped > 0 { text += t(" ({0} blok türü iOS'ta henüz gösterilemiyor)", boards.skipped) }
                    await pano.sync()
                } else {
                    text += t(". Panolar şu anda alınamadı")
                }
                notice = text + (summary.files > 0 ? t(". {0} fotoğraf, dosya ve ses kaydı arka planda iniyor.", summary.files) : ".")
            } catch let failure as AppCheckUnavailable {
                notice = t("Kartlar alınamadı: uygulama doğrulaması yapılamadı ({0}).", failure.reason)
            } catch let failure as RealBackend.Failure {
                notice = t("Kartlar alınamadı (sunucu: {0}).", failure.status)
            } catch {
                notice = t("Kartlar alınamadı. İnternet bağlantını kontrol et.")
            }
        }
    }

    var body: some View {
        ZStack(alignment: .top) {
            Color.black.opacity(0.40 * progress).ignoresSafeArea()
                .onTapGesture { close() }
                .accessibilityLabel(t("Hesap menüsünü kapat"))
            VStack(alignment: .leading, spacing: 0) {
                Text(t("Hesap")).font(RealFont.inter(21, .bold)).foregroundStyle(Color(argb: 0xFFF7F7F8))
                Text(auth.account?.email ?? t("Bu cihazdaki notlar")).font(RealFont.inter(14, .medium)).foregroundStyle(Color(argb: 0xFFA4A4AD)).padding(.top, 12)
                HStack(spacing: 12) {
                    ProfileAvatar(name: store.ownerName, size: 44)
                    Text(store.ownerName.trimmingCharacters(in: .whitespaces).isEmpty ? t("Kullanıcı") : store.ownerName)
                        .font(RealFont.inter(16, .medium)).foregroundStyle(Color(argb: 0xFFF7F7F8))
                }
                .padding(.top, 13)
                VStack(spacing: 3) {
                    item(.person, t("Fotoğrafın veya avatarın")) { showPhotoChoice = true }
                    item(.star, "REAL Pro") { notice = t("REAL Pro aboneliği iOS'ta REAL hesabı ve App Store bağlantısı kurulunca açılacak.") }
                    if auth.account == nil {
                        item(.add, auth.busy ? t("Google açılıyor…") : t("Hesap ekle")) { signInAndRestore() }
                            .sheet(isPresented: $showAuthChoice) { AuthChoiceSheet { _ in restore(keepGuest: true) } }
                    } else {
                        item(.manage, t("Hesapları yönet")) { showManage = true }
                        item(.person, t("Kişilerim")) { showPeople = true }
                    }
                    item(.category, t("Kategori sıralaması")) { close(then: onCategoryOrder) }
                    item(.settings, t("Ayarlar")) { close(then: onSettings) }
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
        // ProfilePhotoChooser.kt: your own photo, or a Humation avatar drawn in REAL.
        .confirmationDialog(t("Profil görünümün"), isPresented: $showPhotoChoice, titleVisibility: .visible) {
            Button(t("Fotoğraf seç")) { showPhotoPicker = true }
            Button(t("Avatar oluştur")) { showAvatarEditor = true }
            Button(t("Vazgeç"), role: .cancel) {}
        } message: { Text(t("Avatar kişilerinde her zaman görünür; fotoğraf yalnız Ayarlar'da paylaşmayı açarsan.")) }
        .fullScreenCover(isPresented: $showAvatarEditor) { AvatarEditorView() }
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
            Button(t("Tamam"), role: .cancel) {}
        } message: { Text(notice ?? "") }
        .sheet(isPresented: $showPeople) { PeopleScreen() }
        .sheet(isPresented: $showManage) {
            AccountManageSheet(
                onEditName: { showManage = false; close(then: { RealAuth.shared.nameEditRequested = true }) },
                onRestore: { showManage = false; restore() },
                onSignOut: {
                    showManage = false
                    Task {
                        await RealPush.shared.release()
                        auth.signOut()
                        notice = t("Hesaptan çıkıldı. Kartlar bu cihazda kalmaya devam ediyor.")
                    }
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
    /// "photo" (shared only when "Fotoğrafımı kişilerimle paylaş" is on) or "avatar" (always shared, as on Android).
    static var kind: String {
        get { UserDefaults.standard.string(forKey: "profile_photo_kind") ?? "photo" }
        set { UserDefaults.standard.set(newValue, forKey: "profile_photo_kind") }
    }
    static func save(_ image: UIImage, kind: String = "photo") {
        self.kind = kind
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
    @State private var language = LanguageSettings.shared
    @State private var showLanguage = false
    @State private var alarmSnooze = RealAlarmSettings.snoozeMinutes
    /// Set after asking; nil = read AlarmKit's state.
    @State private var alarmAllowed: Bool?

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
        .alert(t("Önerileri sıfırlamak istediğine emin misin?"), isPresented: $showSuggestionReset) {
            Button(t("Sıfırla"), role: .destructive) {
                MusicDiscoveryStore.shared.resetSeen()
                discovery.resetSeen()
                RealToast.shared.show(t("Öneriler sıfırlandı"))
            }
            Button(t("Vazgeç"), role: .cancel) {}
        } message: { Text(t("Müzik, film, dizi ve oyunda geçtiklerin ve mevcut desteler temizlenir. Kaydettiğin listeler ile İzledim / Oynadım dediklerin kalır.")) }
        .alert(t("Puanları sıfırlamak istediğine emin misin?"), isPresented: $showRatingReset) {
            Button(t("Sıfırla"), role: .destructive) {
                MediaRatingStore.shared.reset(watched: discovery.film.watched)
                RealToast.shared.show(t("Puanlar sıfırlandı"))
            }
            Button(t("Vazgeç"), role: .cancel) {}
        } message: { Text(t("Puanlarım listesi boşalır. Bu geri alınamaz.")) }
        .alert(t("Tüm mood kayıtlarını sil?"), isPresented: $showClearMoods) {
            Button(t("Sil"), role: .destructive) {
                life.clearAllMoods()
                for r in store.reminders where r.mood != nil { store.mutate(r.id) { $0.mood = nil } }
            }
            Button(t("Vazgeç"), role: .cancel) {}
        } message: { Text(t("Moods kayıtları ve REAL kartlarındaki Mood blokları silinecek. Günlükler korunacak.")) }
        .alert("REAL", isPresented: Binding(get: { notice != nil }, set: { if !$0 { notice = nil } })) {
            Button(t("Tamam"), role: .cancel) {}
        } message: { Text(notice ?? "") }
        .confirmationDialog(t("Uygulama dilini seç"), isPresented: $showLanguage, titleVisibility: .visible) {
            Button(t("Sistem")) { language.set(.system) }
            Button(t("Türkçe")) { language.set(.turkish) }
            Button("English") { language.set(.english) }
            Button(t("Kapat"), role: .cancel) {}
        }
    }

    @AppStorage(HomeCardStyle.plainKey) private var plainCards = false
    @AppStorage(CalendarCards.homeDaysKey) private var calendarHomeDays = 0
    @State private var musicSource = MusicSource.saved

    @ViewBuilder
    private var mainPage: some View {
        SettingsSectionHeader(title: t("GÖRÜNÜM"))
        SettingsGlassGroup {
            SettingsActionRow(icon: .theme, title: t("Dil"), subtitle: language.currentLabel) {
                showLanguage = true
            }
            SettingsDivider()
            SettingsToggleRow(icon: .motionOff, title: t("Animasyonu azalt"), subtitle: t("Arayüz hareketlerini ve geçiş efektlerini sakinleştirir"), isOn: $reduceAnimations)
            SettingsDivider()
            SettingsToggleRow(icon: .blurOff, title: t("Dinamik bulanıklığı azalt"), subtitle: t("Bulanıklık efektleri yerine sabit koyu yüzeyler kullanır"), isOn: $reduceDynamicBlur)
            SettingsDivider()
            SettingsToggleRow(icon: .cat, title: "REAL Pet", subtitle: t("Geçici olarak devre dışı bırakıldı"), isOn: .constant(false), enabled: false)
            SettingsDivider()
            SettingsToggleRow(icon: .bell, title: t("Bağlam kartları"), subtitle: t("Ana ekranın altında arada su, mood, kitap ve film önerisi gösterir"), isOn: $contextCards)
            SettingsDivider()
            SettingsActionRow(icon: .calendar, title: t("Takvim bağlantısı"), subtitle: t("Google / iCloud takvimlerini göster ve planlarını gönder")) {
                showCalendar = true
            }
            SettingsDivider()
            SettingsToggleRow(icon: .check, title: t("Sade kartlar (Deneysel)"),
                              subtitle: t("Ana ekranda tamamlama yuvarlağı yok; kartı sağa kaydırınca tamamlanır."), isOn: $plainCards)
            SettingsDivider()
            // Kapalı → 7 gün → 14 gün, one tap each.
            SettingsActionRow(icon: .calendar, title: t("Ana ekranda takvim"),
                              subtitle: calendarHomeDays > 0 ? t("Önümüzdeki {0} gün", calendarHomeDays) : t("Kapalı")) {
                calendarHomeDays = calendarHomeDays == 0 ? 7 : (calendarHomeDays == 7 ? 14 : 0)
            }
            SettingsDivider()
            // Where songs open (music discovery's up-swipe, the music search's default).
            SettingsActionRow(icon: .music, title: t("Müzik uygulaması"), subtitle: musicSource.label + t(" · şarkılar burada açılır")) {
                musicSource = musicSource == .apple ? .spotify : .apple
                MusicSource.saved = musicSource
            }
            SettingsDivider()
            // Apple Music hesabı: "Sana özel" in Müzik keşfi, a right swipe also adds to the library.
            let appleMusic = AppleMusicAccount.shared
            SettingsActionRow(icon: .music, title: t("Apple Music hesabı"),
                              subtitle: appleMusic.isConnected ? t("Bağlı · dokun, bağlantıyı kes") : t("Bağla · sana özel öneriler, REAL çalma listesi")) {
                if appleMusic.isConnected { appleMusic.disconnect() } else { appleMusic.connect() }
            }
            SettingsDivider()
            // Puanlarım: film / dizi / bölüm puanlarını siler (şimdilik test için).
            // Öneriler (müzik, film / dizi, oyun): geçilenler yeniden gelebilir; kayıtlı listeler ve İzledim / Oynadım kalır.
            SettingsActionRow(icon: .music, title: t("Önerileri sıfırla"), subtitle: t("Müzik, film, dizi ve oyunda geçtiklerin yeniden gelebilir; listelerin silinmez")) {
                showSuggestionReset = true
            }
            SettingsDivider()
            SettingsActionRow(icon: .delete, title: t("Puanlarımı sıfırla"), subtitle: t("Film, dizi ve bölüm puanlarının hepsini siler")) {
                showRatingReset = true
            }
        }
        SettingsSectionHeader(title: t("BİLDİRİMLER VE DAVRANIŞ"))
        SettingsGlassGroup {
            SettingsStatusRow(icon: .bell, title: t("Bildirim izni"), status: notificationsAllowed ? t("Açık") : t("Kapalı"),
                              statusColor: notificationsAllowed ? Color(argb: 0xFF91B9FF) : Color(argb: 0xFFA5A5AF),
                              subtitle: notificationsAllowed ? t("Hatırlatıcı bildirimleri bu cihazda etkin.") : t("Bildirimleri alabilmek için bildirim iznini açabilirsin."),
                              actionLabel: notificationsAllowed ? nil : t("İzni aç")) {
                NotificationScheduler.requestAuthorization()
                if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
            }
            SettingsDivider()
            // Android's "Hatırlatıcı kanalı" is a per-channel setting; iOS keeps sound/banners in the app's notification settings.
            SettingsStatusRow(icon: .settings, title: t("Hatırlatıcı kanalı"), status: notificationsAllowed ? t("Açık") : t("Kapalı"),
                              statusColor: notificationsAllowed ? Color(argb: 0xFF91B9FF) : Color(argb: 0xFFA5A5AF),
                              subtitle: t("Ses, titreşim ve kilit ekranı uyarıları iOS bildirim ayarlarından yönetilir."),
                              actionLabel: t("Kanal ayarları")) {
                if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
            }
            SettingsDivider()
            SettingsStatusRow(icon: .calendar, title: t("Tam saatinde hatırlatma"), status: t("Açık"), statusColor: Color(argb: 0xFF91B9FF),
                              subtitle: t("Alarmlar tam zamanında çalacak şekilde ayarlandı."), actionLabel: nil, action: {})
            SettingsDivider()
            // Alarm cards: iOS plays its own alarm sound (apps cannot use the phone's ringtones); REAL's page has its chime.
            if #available(iOS 26.0, *) {
                let allowed = alarmAllowed ?? RealAlarmKit.authorized
                SettingsStatusRow(icon: .bell, title: t("Alarm izni"), status: allowed ? t("Açık") : t("Kapalı"),
                                  statusColor: allowed ? Color(argb: 0xFF91B9FF) : Color(argb: 0xFFA5A5AF),
                                  subtitle: allowed ? t("Alarm kartları sessiz moddayken de tam ekran çalar.")
                                                    : t("Alarm kartlarının sessiz modu delip çalması için izni aç."),
                                  actionLabel: allowed ? nil : t("İzni aç")) {
                    Task {
                        let ok = await RealAlarmKit.requestAuthorization()
                        alarmAllowed = ok
                        if ok { NotificationScheduler.reschedule(store.reminders) }
                        else if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }
                }
                SettingsDivider()
            }
            SettingsActionRow(icon: .repeat_, title: t("Alarm erteleme"), subtitle: t("{0} dakika · değiştirmek için dokun", alarmSnooze)) {
                let choices = RealAlarmSettings.snoozeChoices
                alarmSnooze = choices[((choices.firstIndex(of: alarmSnooze) ?? 0) + 1) % choices.count]
                RealAlarmSettings.snoozeMinutes = alarmSnooze
            }
        }
        SettingsSectionHeader(title: "REAL PRO")
        SettingsGlassGroup {
            SettingsActionRow(icon: .star, title: "REAL Pro", subtitle: t("Pro’yu keşfet, mağaza aboneliklerini yönet")) {
                notice = t("REAL Pro aboneliği iOS'ta REAL hesabı ve App Store bağlantısı kurulunca açılacak.")
            }
        }
        SettingsSectionHeader(title: t("GİZLİLİK"))
        SettingsGlassGroup {
            SettingsActionRow(icon: .note, title: t("Veri kullanımı"), subtitle: t("Hesap, bulut, paylaşım ve cihaz izinleri")) {
                notice = t("Kartların, kategorilerin, su, mood ve kitap kayıtların bu iPhone'da saklanıyor. REAL hesabınla giriş yaptığında kartlar, kategoriler, mood ve su kayıtları hesabınla iki yönlü eşitlenir; durumu Hesapları yönet → Eşitleme'de görürsün.")
            }
            SettingsDivider()
            SettingsActionRow(icon: .note, title: t("AI ve gizlilik"), subtitle: t("Açıklamalar ve veri aktarımı onayları")) {
                showAiPrivacy = true
            }
            SettingsDivider()
            SettingsActionRow(icon: .note, title: t("Veri hakları ve talepler"), subtitle: t("Kopya, düzeltme, silme veya itiraz talebi")) {
                notice = t("Hesap verilerin için kopya, düzeltme veya silme talebini şimdilik Android uygulamasından ya da gizlilik sayfasındaki adresten yapabilirsin. Bu iPhone'daki verileri uygulamayı silerek tamamen kaldırabilirsin.")
            }
            SettingsDivider()
            // Off by default: the profile photo goes to Kişilerim and shared-category people only when this is on.
            SettingsToggleRow(icon: .person, title: t("Fotoğrafımı kişilerimle paylaş"),
                              subtitle: t("Profil fotoğrafın Kişilerim'de ve ortak kategorilerde görünür"),
                              isOn: Binding(get: { sharePhoto }, set: { on in
                                  sharePhoto = on
                                  ProfilePictures.sharePhoto = on
                                  Task { await ProfilePictures.push() }
                              }))
        }
        SettingsSectionHeader(title: t("HAKKINDA"))
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
            SettingsActionRow(icon: .note, title: t("Açık kaynak lisansları"), subtitle: t("Humation, Tabler Icons ve üçüncü taraf bildirimleri")) { showLicenses = true }
            SettingsDivider()
            SettingsActionRow(icon: .note, title: t("Veri kaynakları ve lisanslar"), subtitle: t("TheTVDB, TVmaze ve IGDB")) {
                withAnimation(.easeOut(duration: 0.2)) { page = .sources }
            }
        }
        SettingsSectionHeader(title: t("GELİŞTİRİCİ"))
        SettingsGlassGroup {
            SettingsActionRow(icon: .settings, title: t("Geliştirici araçları"), subtitle: t("Tanılama, testler ve tasarım demoları")) {
                withAnimation(.easeOut(duration: 0.2)) { page = .developer }
            }
        }
        Color.clear.frame(height: 40)
    }

    @ViewBuilder
    private var developerPage: some View {
        SettingsSectionHeader(title: t("GELİŞTİRİCİ ARAÇLARI"))
        SettingsSectionHeader(title: t("BAĞLAM KARTLARI TESTİ"))
        SettingsGlassGroup {
            let tests: [(ContextCardType, ActionGlyph, String)] = [(.water, .bell, t("Su kartını göster")), (.mood, .moodNeutral, t("Mood kartını göster")),
                                                                  (.book, .note, t("Kitap kartını göster")), (.media, .video, t("Film & dizi kartını göster")),
                                                                  (.watch, .play, t("Sıradaki bölüm kartını göster")), (.listen, .music, t("Müzik kartını göster")),
                                                                  (.play, .cards, t("Oyun kartını göster")), (.journal, .edit, t("Günlük kartını göster"))]
            SettingsActionRow(icon: .cards, title: t("Tüm kartları göster"), subtitle: t("Sadece test: bütün türler sırayla gelir (normalde günde en fazla 2 kart)")) {
                ContextCardStore.forceAll()
                notice = t("Kartlar ana ekranda Kartlarım'ın en üstünde, sırayla görünecek.")
            }
            SettingsDivider()
            ForEach(Array(tests.enumerated()), id: \.offset) { i, test in
                if i > 0 { SettingsDivider() }
                SettingsActionRow(icon: test.1, title: test.2, subtitle: t("Saat ve limit kurallarını yok sayar; Kartlarım'ın en üstünde görünür")) {
                    ContextCardStore.force(test.0)
                    notice = t("Kart ana ekranda Kartlarım'ın en üstünde görünecek.")
                }
            }
            SettingsDivider()
            SettingsActionRow(icon: .undo, title: t("Kart geçmişini sıfırla"), subtitle: t("Günlük limit, bekleme ve sessize alma sayaçlarını temizler")) {
                ContextCardStore.update { ContextCardHistory(enabled: $0.enabled) }
                notice = t("Bağlam kartı geçmişi sıfırlandı.")
            }
        }
        SettingsSectionHeader(title: t("MOOD TEST VE VERİ"))
        SettingsGlassGroup {
            VStack(alignment: .leading, spacing: 10) {
                Text(t("Mood Test ve Veri")).font(RealFont.inter(15)).foregroundStyle(.white)
                Text(t("Mood kayıtlarını geliştirme sırasında sıfırla")).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA5A5AF))
                Button { showClearMoods = true } label: {
                    Text(t("Tüm moodları sil")).font(RealFont.inter(13, .semibold)).foregroundStyle(Color(argb: 0xFFFF6B72))
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
        SettingsSectionHeader(title: t("VERİ KAYNAKLARI VE LİSANSLAR"))
        ForEach(CatalogSourceNotice.all, id: \.name) { source in
            SettingsGlassGroup {
                SettingsActionRow(icon: .note, title: source.name, subtitle: t("Resmî kaynağı aç")) { if let u = URL(string: source.website) { openURL(u) } }
                Text(t(source.summary)).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFA3A3AB)).lineSpacing(6)
                    .padding(.horizontal, 16).padding(.vertical, 12)
                SettingsDivider()
                SettingsActionRow(icon: .note, title: t("Lisans ve kullanım koşulları"), subtitle: source.name) { if let u = URL(string: source.terms) { openURL(u) } }
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

    static var all: [CatalogSourceNotice] {
        [
            CatalogSourceNotice(name: "TheTVDB", summary: t("Film bilgileri TheTVDB tarafından sağlanır. API kullanımı TheTVDB'nin proje lisansı ve kullanım koşullarına tabidir. Afişler ve oyuncu görselleri ilgili hak sahiplerine aittir; API erişimi görsel kullanım haklarını otomatik olarak vermez."), website: "https://thetvdb.com/", terms: "https://www.thetvdb.com/api-information"),
            CatalogSourceNotice(name: "TVmaze", summary: t("Dizi bilgileri TVmaze tarafından sağlanır. API verileri CC BY-SA lisansıyla sunulur; kaynak gösterme ve uyarlanmış materyal için aynı lisans koşullarını koruma yükümlülüğü vardır. Kapaklar ve oyuncu görselleri ilgili hak sahiplerine aittir."), website: "https://www.tvmaze.com/", terms: "https://www.tvmaze.com/api#licensing"),
            CatalogSourceNotice(name: "IGDB", summary: t("Oyun bilgileri ve kapaklar IGDB'den alınır. IGDB, ticari kullanım için partner başvurusu ve görünür kaynak gösterimi ister. API'nin ücretsiz sunulması bu başvuru koşulunu kaldırmaz. Oyun kapakları ve marka hakları ilgili hak sahiplerine aittir."), website: "https://www.igdb.com/", terms: "https://api-docs.igdb.com/#business-related-faq"),
            CatalogSourceNotice(name: "Apple iTunes", summary: t("Müzik araması, kapaklar ve önizleme bağlantıları Apple iTunes Search API'den alınır. Önizlemeler tanıtım amaçlıdır; indirilip kitaplığa kaydedilmez. Apple'ın önizleme, kaynak gösterimi ve mağazaya bağlantı koşulları geçerlidir."), website: "https://www.apple.com/itunes/", terms: "https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/index.html"),
            CatalogSourceNotice(name: "OpenFreeMap / OpenStreetMap", summary: t("Harita karoları OpenFreeMap üzerinden gelir. OpenMapTiles ve OpenStreetMap katkıda bulunanları harita verisinin kaynaklarıdır. Haritada görünen kaynak gösterimi korunmalıdır; bu sayfa harita üzerindeki kaynak gösteriminin yerine geçmez."), website: "https://openfreemap.org/", terms: "https://osmfoundation.org/wiki/Licence/Attribution_Guidelines"),
        ]
    }
}

// MARK: - Layout pieces (LiquidSettingsLayout.kt + SettingsScreen.kt private composables)

/// SettingsLayout: black backdrop with soft glows, a top glass bar (back · "Ayarlar") and a large glass sheet body.
struct SettingsLayout<Content: View>: View {
    let onBack: () -> Void
    var title = t("Ayarlar")
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
                    .accessibilityLabel(t("Geri"))
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
    private var entries: [(String, String, String)] {
        [("Humation", t("Humation 1 avatar varlıkları (MIT)"), "Humation-MIT"),
         ("Tabler Icons", t("Tabler Icons vektör simge seti (MIT)"), "Tabler-Icons-MIT"),
         ("Inter Font", t("Inter değişken yazı tipi (SIL OFL 1.1)"), "Inter-OFL")]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(t("Açık kaynak lisansları")).font(RealFont.inter(18, .semibold)).foregroundStyle(.white)
                Text(t(entries[index].1)).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF9D9DA7))
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
                Button(t("Kapat")) { dismiss() }.font(RealFont.inter(15, .semibold)).foregroundStyle(Color(argb: 0xFF91B9FF))
            }
        }
        .padding(22)
        .presentationDetents([.medium, .large])
        .presentationBackground(Color(argb: 0xFF15161A))
    }

    private var text: String {
        guard let url = Bundle.main.url(forResource: entries[index].2, withExtension: "txt"),
              let s = try? String(contentsOf: url, encoding: .utf8) else { return t("Lisans metni okunamadı.") }
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
        if let e = cloud.lastError { return t("Son deneme olmadı: {0}", e) }
        guard let date = cloud.lastSuccess else { return t("Bu oturumda henüz eşitlenmedi.") }
        return t("Son eşitleme: {0}", date.formatted(.relative(presentation: .named, unitsStyle: .wide).locale(I18n.locale)))
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
        case .keepLocal: return t("Bu iPhone'daki sürüm hesaba gönderilecek; hesaptaki sürüm değişecek.")
        case .useAccount: return c.remote == nil ? t("Kart bu iPhone'dan da kaldırılacak.") : t("Bu iPhone'da hesaptaki sürüm kullanılacak.")
        case .keepAsNewCard: return c.remote == nil
            ? t("Kart hesaba yeni bir kart olarak gönderilecek; bu iPhone'da içeriği aynen kalır.")
            : t("Bu iPhone'daki içerik ayrı bir kartta korunacak; eski karta hesaptaki sürüm uygulanacak.")
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
                        Text(c.remote == nil ? t("Hesapta silinmiş") : (c.isCard ? t("İki tarafta da değişti") : t("Kayıt sürümleri çakışıyor")))
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
            Text(c.remote == nil ? t("Başka bir cihazda silindi ama bu iPhone'da değişmişti. Ne olacağını sen seç.")
                                 : t("İki tarafta da değişti; hiçbiri ezilmedi. Karşılaştırıp seç."))
                .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A93)).fixedSize(horizontal: false, vertical: true)
            Text(t("Bu iPhone'daki")).font(RealFont.inter(11, .medium)).foregroundStyle(Color(argb: 0xFF6FD6FF))
            Text(c.local).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.85)).fixedSize(horizontal: false, vertical: true)
            Text(t("Hesaptaki")).font(RealFont.inter(11, .medium)).foregroundStyle(Color(argb: 0xFF6FD6FF))
            Text(c.remote ?? t("Silinmiş kayıt")).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.85)).fixedSize(horizontal: false, vertical: true)
            if let chosen = pendingChoice, chosen.0 == c.id {
                Text(choiceLabel(c, chosen.1)).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFFFD27A)).fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 14) {
                    Button(t("Onayla")) {
                        let (id, choice) = chosen
                        pendingChoice = nil
                        act { await cloud.resolve(id, choice, store, life) }
                    }
                    Button(t("Vazgeç")) { pendingChoice = nil }
                }
                .font(RealFont.inter(13, .medium)).foregroundStyle(.white).disabled(syncing)
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    if c.isCard && c.remote == nil { Button(t("Hesaba geri yükle")) { pendingChoice = (c.id, .keepAsNewCard) } }
                    if c.remote != nil { Button(t("Bu iPhone'dakini koru")) { pendingChoice = (c.id, .keepLocal) } }
                    Button(c.remote == nil ? t("Silmeyi uygula (bu iPhone'dan da kaldır)") : t("Hesaptakini kullan")) { pendingChoice = (c.id, .useAccount) }
                    if c.isCard && c.remote != nil { Button(t("Yeni kart olarak sakla")) { pendingChoice = (c.id, .keepAsNewCard) } }
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
            Text(issue.hasAccountCopy ? t("Bu iPhone'daki sürüm korunuyor. Kartı düzenlersen kendiliğinden tekrar gönderilir.")
                                      : t("Bu kayıt hesabına hiç ulaşmadı; bu iPhone'daki sürüm korunuyor."))
                .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A93)).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 14) {
                Button(t("Tekrar dene")) { syncNow() }
                if issue.hasAccountCopy { Button(t("Hesaptakini kullan")) { act { await cloud.useAccount(forRefused: issue.id, store, life) } } }
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
        deleting = password != nil ? t("Şifre doğrulanıyor…") : (RealAuth.shared.usesApple ? t("Apple ile doğrulanıyor…") : t("Google ile doğrulanıyor…"))
        Task {
            do {
                if let password { try await RealAuth.shared.reauthenticateWithPassword(password) }
                else if RealAuth.shared.usesApple { try await RealAuth.shared.reauthenticateWithApple() }
                else { try await RealAuth.shared.reauthenticateWithGoogle() }
                deleting = t("Bulut verileri siliniyor…")
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
                    throw CalendarCardPublisher.Failure(errorDescription: t("Silme sürüyor. Birazdan yeniden dene; işlem kaldığı yerden devam eder."))
                }
                deleting = t("Hesap siliniyor…")
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
            Text(t("Hesapları yönet")).font(RealFont.inter(26, .semibold)).foregroundStyle(.white)
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 14) {
                    ProfileAvatar(name: auth.account?.displayName ?? "REAL", size: 48)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(auth.account?.displayName ?? t("REAL hesabı")).font(RealFont.inter(17, .medium)).foregroundStyle(.white).lineLimit(1)
                        if let email = auth.account?.email { Text(email).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFA4A4AD)).lineLimit(1) }
                    }
                }
                HStack(spacing: 10) {
                    Text(t("Ortak kategorilerde ve paylaşımlarda bu ad görünür.")).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFA4A4AD))
                    Spacer(minLength: 8)
                    Button(t("Adını değiştir"), action: onEditName)
                        .font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                        .padding(.horizontal, 12).padding(.vertical, 7).background(Capsule().fill(.white.opacity(0.10)))
                }
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(.white.opacity(0.05)))
            // Eşitleme ve bu cihazdaki alan (SyncManagementPanel / LocalStoragePanel, short).
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(t("Eşitleme")).font(RealFont.inter(15, .medium)).foregroundStyle(.white)
                    Spacer()
                    Button(syncing ? t("Eşitleniyor…") : t("Şimdi eşitle")) { syncNow() }
                        .font(RealFont.inter(13, .medium)).foregroundStyle(.white).disabled(syncing)
                }
                Text(syncLine).font(RealFont.inter(13)).foregroundStyle(cloud.lastError == nil ? Color(argb: 0xFFA4A4AD) : Color(argb: 0xFFFFB4AB))
                Text(t("Kartlar, kategoriler, mood ve su kayıtları bu iPhone ile hesabın arasında iki yönlü eşitlenir. Bir kart iki tarafta da değişirse ya da hesapta silinirse hiçbiri ezilmez; ne olacağını sen seçersin."))
                    .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A93)).fixedSize(horizontal: false, vertical: true)
                let waiting = cloud.issues.count + cloud.conflicts.count
                if cloud.lastError == nil && cloud.lastSuccess != nil {
                    Text(waiting == 0 ? t("Bekleyen kayıt yok.") : t("{0} kayıt senin seçimini bekliyor · diğerleri eşitlendi", waiting))
                        .font(RealFont.inter(13)).foregroundStyle(waiting == 0 ? Color(argb: 0xFFA4A4AD) : Color(argb: 0xFFFFD27A))
                }
                if waiting > 0 {
                    Button(showIssues ? t("Ayrıntıları gizle") : t("Sorunları ve sürümleri göster")) { withAnimation(.snappy) { showIssues.toggle() } }
                        .font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                    if showIssues {
                        // Cards the account deleted while they changed here: one button for all of them.
                        let deletedCards = cloud.conflicts.filter { $0.isCard && $0.remote == nil }
                        if deletedCards.count > 1 {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(t("Hesapta silinmiş {0} kart", deletedCards.count)).font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                                Text(t("Bu kartlar bu iPhone'da duruyor ama hesabında silinmiş görünüyor. Geri yüklersen her biri hesaba yeni bir kart olarak gönderilir; bu iPhone'da hiçbir şey kaybolmaz."))
                                    .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A93)).fixedSize(horizontal: false, vertical: true)
                                HStack(spacing: 14) {
                                    if bulkConfirm {
                                        Button(t("Onayla: {0} kartı geri yükle", deletedCards.count)) {
                                            bulkConfirm = false
                                            let ids = deletedCards.map(\.id)
                                            act { for id in ids { await cloud.resolve(id, .keepAsNewCard, store, life) } }
                                        }
                                        Button(t("Vazgeç")) { bulkConfirm = false }
                                    } else {
                                        Button(t("Hepsini hesaba geri yükle")) { bulkConfirm = true }
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
                            if max(cloud.issues.count, cloud.conflicts.count) > issueLimit { Button(t("Daha fazla göster")) { issueLimit += 10 } }
                            Spacer()
                            if !cloud.issues.isEmpty { Button(syncing ? t("Deneniyor…") : t("Hepsini tekrar dene")) { syncNow() }.disabled(syncing) }
                        }
                        .font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                    }
                }
                Text(t("Bu cihazdaki dosyalar: {0}", localSize)).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFA4A4AD))
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(.white.opacity(0.05)))
            Button(action: onRestore) {
                Text(t("Kartları hesaptan yeniden yükle")).font(RealFont.inter(15)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 48).background(Capsule().fill(.white.opacity(0.08)))
            }
            .buttonStyle(.plain)
            Button(action: onSignOut) {
                Text(t("Hesaptan çık")).font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFFFB4AB))
                    .frame(maxWidth: .infinity, minHeight: 48).background(Capsule().fill(.white.opacity(0.05)))
            }
            .buttonStyle(.plain)
            Button { confirmDelete = true } label: {
                Text(deleting ?? t("Hesabı sil")).font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFFFF8A80))
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
        .confirmationDialog(t("Hesabın ve buluttaki tüm verilerin kalıcı olarak silinsin mi?"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(t("Hesabı kalıcı olarak sil"), role: .destructive) { deleteAccount() }
            Button(t("Vazgeç"), role: .cancel) {}
        } message: {
            Text(t("Buluttaki kartların, dosyaların, paylaşım linklerin ve sahibi olduğun panolar silinir. Bu iPhone'daki kartlar kalır. Geri alınamaz."))
        }
        .alert(t("Şifreni yaz"), isPresented: $askDeletePassword) {
            SecureField(t("Şifre"), text: $deletePassword)
            Button(t("Sil"), role: .destructive) { deleteAccount(password: deletePassword) }
            Button(t("Vazgeç"), role: .cancel) { deletePassword = "" }
        } message: { Text(t("Hesabı silmek için e-posta hesabının şifresiyle doğrula.")) }
        .presentationBackground(Color(argb: 0xFF111113))
    }
}
