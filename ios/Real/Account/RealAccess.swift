import SwiftUI

/// Stable wire keys shared with the worker and Android's RealFeature. Not UI labels.
enum RealFeature: String {
    case voiceAi = "voice.ai", moodAi = "mood.ai", mediaDiscovery = "discover.media", gameDiscovery = "discover.games"
    case plannerGoals = "planner.goals", cardColors = "card.colors", categoryGradients = "category.gradients"
}

/// White, black and one each of blue, sky, green, gold, coral and violet; the rest of the palette is Pro.
let freeCardColors: Set<Int> = [0, 1, 2, 4, 8, 10, 13, 18]

/// RealAccessSnapshot: the server's answer for this account. Kept in memory only, bound to its uid.
struct RealAccessSnapshot {
    let uid: String
    let isPro: Bool
    /// Admin panel's tester grant (member card: Tester kartı).
    var tester: Bool = false
    /// Pro comes from an App Store subscription (not a gift), so the Pro page offers "Aboneliği yönet".
    var subscribed: Bool = false
    /// Öncü üye number, for the first hundred yearly subscribers.
    var founderNumber: Int? = nil
    let effectiveFeatures: Set<String>
    let validUntil: Date

    func allows(_ feature: RealFeature) -> Bool { effectiveFeatures.contains(feature.rawValue) }
}

/// ProAccess.kt: what this account may use, and the sheet a refusal opens. The server still checks every AI request;
/// those refusals reach [serverRefused] from RealBackend, so the AI features need no check of their own here.
@MainActor
@Observable
final class ProAccess {
    static let shared = ProAccess()

    struct Prompt: Equatable { let feature: RealFeature; var limit = false; var pro = false }
    /// Lives in its own window, so it also shows over the screens that are presented above the app's root.
    var prompt: Prompt? { didSet { if prompt != nil { ProWindow.show() } } }
    private(set) var snapshot: RealAccessSnapshot?
    /// Read while views draw: asked at most once a minute, whatever the answer was.
    @ObservationIgnored private var lastAsked = Date.distantPast

    func clear() { snapshot = nil; prompt = nil }

    private func current() -> RealAccessSnapshot? {
        guard let snapshot, snapshot.uid == RealAuth.shared.account?.uid, snapshot.validUntil > Date() else { return nil }
        return snapshot
    }

    /// For small marks beside a control: what is known now, asking the server when nothing is. The tap checks again.
    func unlocked(_ feature: RealFeature) -> Bool {
        if let known = current() { return known.allows(feature) }
        if RealAuth.shared.account != nil, Date().timeIntervalSince(lastAsked) > 60 {
            lastAsked = Date()
            Task { _ = try? await refresh() }
        }
        return snapshot?.uid == RealAuth.shared.account?.uid && snapshot?.allows(feature) == true
    }

    func refresh() async throws -> RealAccessSnapshot {
        guard let uid = RealAuth.shared.account?.uid else { throw URLError(.userAuthenticationRequired) }
        let data = try await RealBackend.request("/api/access/v1/me", timeout: 15)
        guard uid == RealAuth.shared.account?.uid,
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let body = root["access"] as? [String: Any], body["uid"] as? String == uid,
              let flags = body["effectiveFeatures"] as? [String: Any] else { throw RealBackend.Failure(status: -1) }
        let ttl = (((body["cacheUntil"] as? NSNumber)?.doubleValue ?? 0) - ((body["serverTime"] as? NSNumber)?.doubleValue ?? 0)) / 1000
        let value = RealAccessSnapshot(uid: uid, isPro: body["plan"] as? String == "PRO",
                                       tester: body["tester"] as? Bool == true,
                                       subscribed: body["source"] as? String == "APP_STORE",
                                       founderNumber: ((body["purchase"] as? [String: Any])?["founderNumber"] as? NSNumber)?.intValue,
                                       effectiveFeatures: Set(flags.compactMap { ($0.value as? Bool) == true ? $0.key : nil }),
                                       validUntil: Date().addingTimeInterval(min(max(ttl, 0), 15 * 60)))
        snapshot = value
        return value
    }

    /// rememberProAction: runs [action] when the account has the feature, else opens the sheet. Tap again after an upgrade.
    func perform(_ feature: RealFeature, _ action: @escaping () -> Void) {
        let uid = RealAuth.shared.account?.uid
        Task { @MainActor in
            guard uid != nil else { prompt = Prompt(feature: feature); return }
            do {
                let access: RealAccessSnapshot
                if let known = current() { access = known } else { access = try await refresh() }
                guard uid == RealAuth.shared.account?.uid else { return }
                if access.allows(feature) { action() } else { prompt = Prompt(feature: feature) }
            } catch {
                // Fails closed; with no answer it says why rather than selling Pro.
                RealToast.shared.show(RealBackend.message(for: error))
            }
        }
    }

    private static func feature(for path: String) -> RealFeature? {
        switch path {
        case "/api/voice-intent", "/api/ai-diagnostic": return .voiceAi
        case "/api/mood-suggestions": return .moodAi
        case "/api/media-recommendations": return .mediaDiscovery
        case "/api/game-recommendations": return .gameDiscovery
        default: return nil
        }
    }

    /// The server refused an AI request: no Pro, or the day's allowance is used up. The next check reads the new count.
    func serverRefused(path: String, status: Int, code: String?) {
        guard prompt == nil, let feature = Self.feature(for: path) else { return }
        if status == 403, code == "PRO_REQUIRED" {
            snapshot = nil
            prompt = Prompt(feature: feature)
        } else if status == 429, code == "AI_DAILY_LIMIT" {
            let pro = snapshot?.isPro == true
            snapshot = nil
            prompt = Prompt(feature: feature, limit: true, pro: pro)
        }
    }
}

/// A window of its own above everything, up only while the sheet or the Pro screen is open.
@MainActor
enum ProWindow {
    private static var window: UIWindow?

    static func show() {
        guard window == nil, let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }) else { return }
        let new = UIWindow(windowScene: scene)
        new.windowLevel = .alert
        let host = UIHostingController(rootView: ProAccessHost())
        host.view.backgroundColor = .clear
        new.rootViewController = host
        new.isHidden = false
        window = new
    }

    static func hide() {
        window?.isHidden = true
        window = nil
    }
}

/// ProAccessHost.kt: the lock sheet, then the Pro screen.
struct ProAccessHost: View {
    @State private var access = ProAccess.shared
    @State private var showPro = false

    private var uid: String? { RealAuth.shared.account?.uid }

    var body: some View {
        ZStack {
            if let prompt = access.prompt {
                sheet(prompt).transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onChange(of: access.prompt == nil && !showPro) { _, done in if done { ProWindow.hide() } }
        .fullScreenCover(isPresented: $showPro) { RealProScreen() }
    }

    @ViewBuilder
    private func sheet(_ prompt: ProAccess.Prompt) -> some View {
        let close = { access.prompt = nil }
        let openPro = { access.prompt = nil; showPro = true }
        if uid == nil {
            PermissionSheet(title: t("REAL Pro için giriş yap"), description: t("REAL Pro hesabına bağlıdır. Hesap menüsünden giriş yapabilirsin."),
                            primaryLabel: t("Tamam"), secondaryLabel: t("Şimdi değil"), glyph: .star,
                            onPrimary: close, onSecondary: close, onDismiss: close)
        } else if prompt.limit {
            PermissionSheet(title: t("Bugünlük AI hakkın bitti"),
                            description: t("Hakkın {0} saatinde yenilenir. Basit sesli komutlar çalışmaya devam eder.", Self.nextReset()),
                            primaryLabel: prompt.pro ? t("Tamam") : t("REAL Pro'yu gör"),
                            secondaryLabel: prompt.pro ? t("Pro durumunu gör") : t("Tamam"), glyph: .star,
                            onPrimary: prompt.pro ? close : openPro, onSecondary: prompt.pro ? openPro : close, onDismiss: close)
        } else {
            let copy = Self.copy(prompt.feature)
            PermissionSheet(title: copy.0, description: copy.1, primaryLabel: t("REAL Pro'yu gör"), secondaryLabel: t("Şimdi değil"), glyph: .star,
                            onPrimary: openPro, onSecondary: close, onDismiss: close)
        }
    }

    /// When the shared AI allowance renews (midnight UTC), in the phone's own time.
    private static func nextReset() -> String {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let reset = utc.date(byAdding: .day, value: 1, to: utc.startOfDay(for: Date())) ?? Date()
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f.string(from: reset)
    }

    private static func copy(_ feature: RealFeature) -> (String, String) {
        switch feature {
        case .voiceAi: return (t("Söyle, kartı REAL kursun"), t("Basit komutlar telefonda ücretsiz çalışır. Uzun cümleleri AI anlar."))
        case .moodAi: return (t("Haline göre öneriler"), t("Mood kaydı ücretsiz. Ona göre kişisel öneri AI ile gelir."))
        case .mediaDiscovery: return (t("Sana göre film ve dizi"), t("Arama ve eklemek ücretsiz. Zevkine göre öneriyi AI hazırlar."))
        case .gameDiscovery: return (t("Sana göre oyun"), t("Arama ve eklemek ücretsiz. Zevkine göre öneriyi AI hazırlar."))
        case .plannerGoals: return (t("Bir hedef daha"), t("Her kategoride bir aktif hedef ücretsiz. Daha fazlası Pro ile. Mevcut hedeflerin aynen kalır."))
        case .cardColors: return (t("Kartın senin rengin olsun"), t("8 temel renk ücretsiz. Tüm palet ve geçişli renkler Pro ile açılır."))
        case .categoryGradients: return (t("Kategorilerine geçiş rengi"), t("Düz renkler ücretsiz. Geçişli renkler Pro ile açılır."))
        }
    }
}

/// RealProDialog.kt: the full Pro screen. Plans and prices come from the App Store (ProStore); the server decides who is Pro.
struct RealProScreen: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var access = ProAccess.shared
    @State private var store = ProStore.shared
    @State private var showDetails = false
    @State private var choice: String?

    private var isPro: Bool { access.snapshot?.isPro == true }
    private var signedIn: Bool { RealAuth.shared.account != nil }
    private var selected: ProStore.Offer? { store.offers.first { $0.id == choice } ?? store.offers.first }

    var body: some View {
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button { dismiss() } label: {
                        ActionIcon(.close, color: .white.opacity(0.7), size: 18).frame(width: 48, height: 48)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(t("Kapat"))
                }
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("REAL Pro").font(RealFont.inter(40, .semibold)).kerning(-1.2).foregroundStyle(Color(argb: 0xFFF3F1F4))
                            .accessibilityAddTraits(.isHeader)
                        Text(t("Daha akıllı planlar ve kendi tarzın.")).font(RealFont.inter(16)).foregroundStyle(Color(argb: 0xFF9B9BA4)).padding(.top, 6)
                        VStack(alignment: .leading, spacing: 22) {
                            benefit(.mic, t("AI her yerde"), t("Ses, mood, film ve oyun önerileri, her gün çok daha fazlası"))
                            benefit(.calendar, t("Sınırsız hedef"), t("Free hesapta kategori başına 1 aktif hedef"))
                            benefit(.theme, t("Tüm renkler"), t("Kart paletinin tamamı ve kategori geçişleri"))
                        }
                        .padding(.top, 30)
                        Text(t("Abonelik biterse kartların ve dosyaların silinmez.")).font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.7)).padding(.top, 26)
                        Button { showDetails = true } label: {
                            Text(t("Neler dahil?")).font(RealFont.inter(15, .medium)).foregroundStyle(.white.opacity(0.75))
                                .frame(maxWidth: .infinity, minHeight: 48)
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 8)
                        plans.padding(.vertical, 12)
                    }
                    .padding(.horizontal, 24)
                }
                .scrollIndicators(.hidden)
                VStack(spacing: 2) {
                    if let notice = store.notice {
                        Text(notice).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFC27A)).multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true).padding(.bottom, 8)
                    }
                    let canBuy = !isPro && selected != nil && !store.working
                    Button {
                        if isPro { dismiss() } else if let offer = selected { Task { await store.buy(offer) } }
                    } label: {
                        ZStack {
                            if store.working { ProgressView().tint(.black) } else {
                                Text(isPro ? t("Tamam") : t("App Store ile abone ol")).font(RealFont.inter(16, .semibold)).foregroundStyle(.black)
                            }
                        }
                        .frame(maxWidth: .infinity, minHeight: 54)
                        .background(Capsule().fill(.white.opacity(isPro || canBuy ? 1 : 0.28)))
                    }
                    .buttonStyle(.plain)
                    .disabled(!isPro && !canBuy)
                    HStack(spacing: 0) {
                        Button(t("Geri yükle")) { Task { await store.restore() } }
                            .disabled(!signedIn || store.working).padding(.horizontal, 8).padding(.vertical, 13)
                        Button(t("Kullanım koşulları")) {
                            if let url = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/") { openURL(url) }
                        }
                        .padding(.horizontal, 8).padding(.vertical, 13)
                        Button(t("Gizlilik politikası")) {
                            if let url = URL(string: "https://real-privacy-staging.realapp.workers.dev/privacy") { openURL(url) }
                        }
                        .padding(.horizontal, 8).padding(.vertical, 13)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.white.opacity(0.65))
                    .font(RealFont.inter(13))
                }
                .padding(.horizontal, 24).padding(.top, 10).padding(.bottom, 6)
            }
        }
        .preferredColorScheme(.dark)
        .task {
            _ = try? await access.refresh()
            await store.load()
        }
        .onDisappear { store.notice = nil }
        .sheet(isPresented: $showDetails) { details }
    }

    /// The account's state, or what is on sale: founder (while places are left), yearly, monthly.
    @ViewBuilder
    private var plans: some View {
        if isPro {
            VStack(alignment: .leading, spacing: 4) {
                Text(access.snapshot?.founderNumber.map { t("Bu hesapta REAL Pro etkin. Öncü üye #{0}", $0) } ?? t("Bu hesapta REAL Pro etkin."))
                    .font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.7)).fixedSize(horizontal: false, vertical: true)
                if access.snapshot?.subscribed == true {
                    Button(t("Aboneliği yönet")) {
                        if let url = URL(string: "https://apps.apple.com/account/subscriptions") { openURL(url) }
                    }
                    .buttonStyle(.plain).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFF91B9FF)).frame(minHeight: 44)
                }
            }
        } else if !signedIn && !store.demo {
            note(t("REAL Pro hesabına bağlıdır. Hesap menüsünden giriş yapabilirsin."))
        } else if !store.offers.isEmpty {
            VStack(spacing: 10) {
                ForEach(store.offers) { offer in planRow(offer) }
                Text(t("Abonelik, dönem bitmeden en az 24 saat önce iptal edilmezse aynı fiyatla kendiliğinden yenilenir. App Store hesap ayarlarından yönetip iptal edebilirsin."))
                    .font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.5)).fixedSize(horizontal: false, vertical: true).padding(.top, 4)
            }
        } else if store.loaded {
            note(t("Planlar şu anda yüklenemedi. Biraz sonra tekrar dene."))
        } else {
            ProgressView().tint(.white).frame(maxWidth: .infinity, minHeight: 60)
        }
    }

    private func note(_ text: String) -> some View {
        Text(text).font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.7)).fixedSize(horizontal: false, vertical: true)
    }

    private func planRow(_ offer: ProStore.Offer) -> some View {
        let on = offer.id == selected?.id
        let yearly = offer.plan != .monthly
        let title = offer.plan == .founder ? t("Öncü üye") : yearly ? t("Yıllık") : t("Aylık")
        let summary: String = {
            switch offer.plan {
            case .founder: return t("İlk 100 üyeye özel yıllık fiyat · {0} yer kaldı", store.founderLeft)
            case .yearly: return t("Ayda {0}", offer.perMonth ?? "")
            case .monthly: return t("İstediğin zaman iptal et")
            }
        }()
        return Button { choice = offer.id } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(RealFont.inter(16, .semibold)).foregroundStyle(Color(argb: 0xFFF3F1F4))
                    Text(summary).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF9B9BA4)).fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(offer.price).font(RealFont.inter(17, .semibold)).foregroundStyle(Color(argb: 0xFFF3F1F4))
                    Text(yearly ? t("yılda") : t("ayda")).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF9B9BA4))
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.white.opacity(on ? 0.10 : 0.04)))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(.white.opacity(on ? 0.9 : 0.12), lineWidth: on ? 1.5 : 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func benefit(_ glyph: ActionGlyph, _ title: String, _ summary: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            ActionIcon(glyph, color: Color(argb: 0xFF91B9FF), size: 22)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(RealFont.inter(16, .medium)).foregroundStyle(Color(argb: 0xFFF3F1F4))
                Text(summary).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF9B9BA4)).fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var details: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(t("Neler dahil?")).font(RealFont.inter(24, .semibold)).foregroundStyle(.white)
                Text(t("Pro için hazırlanan özellikler")).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF9B9BA4))
                ForEach([t("Pro; AI ile sesli komutları, mood önerilerini, film, dizi ve oyun önerilerini, sınırsız hedefi ve tüm renkleri açar."),
                         t("Temel kartlar, telefonda çözülen sesli komutlar, mood kaydı, 8 temel renk ve kategori başına bir hedef ücretsiz kalır."),
                         store.proDailyAi.map { t("Pro ile günde {0} AI isteği hakkın olur. Sınırsız AI kullanımı sunulmuyor.", $0) }
                            ?? t("Pro ile AI için günlük bir hakkın olur. Sınırsız AI kullanımı sunulmuyor."),
                         t("Öncü üye fiyatı ilk 100 yıllık üyeye özeldir ve aboneliğin kesintisiz sürdükçe aynı kalır."),
                         t("iPhone'da ödeme App Store üzerinden tamamlanır. Satın almadan önce fiyatı ve yenileme koşullarını görürsün."),
                         t("Abonelik AI gizlilik tercihlerini değiştirmez. Abonelik bitince kartların silinmez.")], id: \.self) { line in
                    Text(line).font(RealFont.inter(15)).foregroundStyle(.white.opacity(0.8)).fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(24)
        }
        .presentationDetents([.large])
        .presentationBackground(Color(argb: 0xFF111113))
    }
}
