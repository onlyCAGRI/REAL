import SwiftUI
import PhotosUI
import UserNotifications

// MARK: - Account menu (AccountMenu.kt)

/// AccountMenuOverlay: dim scrim + top glass panel (Hesap · Bu cihazdaki notlar · avatar row · items).
struct AccountMenuOverlay: View {
    let onSettings: () -> Void
    let onCategoryOrder: () -> Void
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var progress: CGFloat = 0
    @State private var photoItem: PhotosPickerItem?
    @State private var showPhotoPicker = false
    @State private var notice: String?

    var body: some View {
        ZStack(alignment: .top) {
            Color.black.opacity(0.40 * progress).ignoresSafeArea()
                .onTapGesture { close() }
                .accessibilityLabel("Hesap menüsünü kapat")
            VStack(alignment: .leading, spacing: 0) {
                Text("Hesap").font(RealFont.inter(21, .bold)).foregroundStyle(Color(argb: 0xFFF7F7F8))
                Text("Bu cihazdaki notlar").font(RealFont.inter(14, .medium)).foregroundStyle(Color(argb: 0xFFA4A4AD)).padding(.top, 12)
                HStack(spacing: 12) {
                    ProfileAvatar(name: store.ownerName, size: 44)
                    Text(store.ownerName.trimmingCharacters(in: .whitespaces).isEmpty ? "Kullanıcı" : store.ownerName)
                        .font(RealFont.inter(16, .medium)).foregroundStyle(Color(argb: 0xFFF7F7F8))
                }
                .padding(.top, 13)
                VStack(spacing: 3) {
                    item(.person, "Fotoğrafın veya avatarın") { showPhotoPicker = true }
                    item(.star, "REAL Pro") { notice = "REAL Pro aboneliği iOS'ta REAL hesabı ve App Store bağlantısı kurulunca açılacak." }
                    item(.add, "Hesap ekle") { notice = "REAL hesabıyla giriş iOS sürümüne Firebase kurulumu ile eklenecek. Notların şimdilik bu cihazda tutuluyor." }
                    item(.manage, "Hesapları yönet") { notice = "Hesap yönetimi, giriş eklendiğinde burada görünecek." }
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
                }
            }
        }
        .alert("REAL", isPresented: Binding(get: { notice != nil }, set: { if !$0 { notice = nil } })) {
            Button("Tamam", role: .cancel) {}
        } message: { Text(notice ?? "") }
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
    @Environment(LifeStore.self) private var life
    @Environment(ReminderStore.self) private var store
    @AppStorage("reduce_animations") private var reduceAnimations = false
    @AppStorage("reduce_dynamic_blur") private var reduceDynamicBlur = false
    @AppStorage("context_cards_enabled") private var contextCards = true
    @AppStorage("planner_daily_view") private var dailyView = "new"
    @State private var notificationsAllowed = true
    @State private var page: Page = .main
    @State private var showDailyChoice = false
    @State private var showLicenses = false
    @State private var showClearMoods = false
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
        .confirmationDialog("Daily Planner View", isPresented: $showDailyChoice, titleVisibility: .visible) {
            ForEach([("classic", "Classic"), ("new", "New"), ("new_plus", "New+")], id: \.0) { key, label in
                Button(label + (dailyView == key ? "  ✓" : "")) { dailyView = key }
            }
            Button("Kapat", role: .cancel) {}
        }
        .sheet(isPresented: $showLicenses) { LicensesSheet() }
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

    private var dailyLabel: String { dailyView == "new_plus" ? "New+" : (dailyView == "classic" ? "Classic" : "New") }

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
            SettingsActionRow(icon: .calendar, title: "Daily Planner View", subtitle: dailyLabel) { showDailyChoice = true }
            SettingsDivider()
            SettingsActionRow(icon: .calendar, title: "Takvim bağlantısı", subtitle: "Google / Samsung takvimlerini göster ve planlarını gönder") {
                notice = "Takvim bağlantısı iOS'ta Apple Takvim (EventKit) ile eklenecek."
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
                notice = "iOS sürümünde notların, kategorilerin, su, mood ve kitap kayıtların yalnızca bu cihazda saklanıyor. Hesap ve bulut eşitlemesi henüz yok."
            }
            SettingsDivider()
            SettingsActionRow(icon: .note, title: "AI ve gizlilik", subtitle: "Açıklamalar ve veri aktarımı onayları") {
                notice = "AI önerileri (film, oyun, mood) REAL hesabıyla giriş eklendiğinde ve onay verdiğinde kullanılacak. Şu an hiçbir veri AI servisine gönderilmiyor."
            }
            SettingsDivider()
            SettingsActionRow(icon: .note, title: "Veri hakları ve talepler", subtitle: "Kopya, düzeltme, silme veya itiraz talebi") {
                notice = "Hesap verisi talepleri giriş eklendiğinde buradan yapılacak. Cihazdaki verileri uygulamayı silerek tamamen kaldırabilirsin."
            }
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
                Text("Ayarlar").font(RealFont.inter(18, .semibold)).kerning(-0.3).foregroundStyle(.white)
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
