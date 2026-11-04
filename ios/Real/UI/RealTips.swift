import SwiftUI
import UIKit

/// RealTips.kt: the first time someone taps something that is not obvious, a small white tip explains it.
/// Each tip shows once; Ayarlar → Geliştirici araçları can show every tip on every tap or start over.
@MainActor
@Observable
final class RealTips {
    static let shared = RealTips()

    /// Turkish text doubles as the t() key; English lives in en.json.
    struct Tip { let title: String; let body: String }
    struct Shown { let key: String; let tip: Tip; let anchor: String?; let token: Int; let onGotIt: (() -> Void)? }

    private static let tips: [String: Tip] = [
        "test": Tip(title: "Eğitim ipucu", body: "Bir şeye ilk dokunduğunda böyle kısa bir açıklama çıkar."),
        "composer_date": Tip(title: "Tarih ve saat", body: "Karta gün ve saat ver. \"Yarın 9'da\" ya da \"her cumartesi 6'da\" diye yazarsan altta seçenekler çıkar; birine dokununca tarih karta geçer."),
        "composer_bell": Tip(title: "Bildirim", body: "Zil açıkken kart sana bildirim gönderir; tarihsiz kartta hemen gelir. Alarm için tarih ekranında saati sağa it."),
        "composer_location": Tip(title: "Konum", body: "Karta bir yer ekle ya da oraya varınca veya ayrılınca hatırlatsın."),
        "composer_category": Tip(title: "Kategori", body: "Kartı bir kategoriye koy ya da yeni kategori oluştur. Kategoriler ana ekranda renkli kartlar olarak durur."),
        "composer_add": Tip(title: "Ekler", body: "Fotoğraf, kamera, ses kaydı, konum ve liste buradan eklenir."),
        "detail_edit_dock": Tip(title: "Bloklar", body: "Alttaki simgeleri sağa sola kaydır; her biri karta yeni bir blok ekler."),
        "detail_stack": Tip(title: "Kart yığını", body: "Üst üste duran küçük kartlar: not, alıntı ya da fikir yaz."),
        "detail_stack_use": Tip(title: "Kart yığını", body: "Yazmak için öndeki karta dokun; arkadaki karta dokununca öne gelir."),
        "detail_location": Tip(title: "Konum", body: "Bir yer seç; oraya varınca ya da ayrılınca REAL hatırlatır. Kartta tarih de varsa hava durumu görünür."),
        "detail_share": Tip(title: "Paylaş", body: "Kartı bir REAL kullanıcısına gönder, bağlantı olarak paylaş ya da düz metin yolla."),
        "detail_media": Tip(title: "Medya", body: "Şarkı, film, dizi ya da oyun ekle; karttan tek dokunuşla açılır.")
    ]

    private static let alwaysOnKey = "real_tips_always_on"
    private static func seenKey(_ key: String) -> String { "real_tips_seen:\(key)" }

    private(set) var shown: Shown?
    private(set) var closing = false
    /// Every tap shows its tip (development). Off: each tip once.
    private(set) var alwaysOn = UserDefaults.standard.bool(forKey: alwaysOnKey)
    @ObservationIgnored var anchors: [String: CGRect] = [:]
    @ObservationIgnored private var token = 0

    func setAlwaysOn(_ on: Bool) {
        UserDefaults.standard.set(on, forKey: Self.alwaysOnKey)
        alwaysOn = on
    }

    /// Forgets which tips were seen, so each shows once more.
    func resetSeen() {
        for key in Self.tips.keys { UserDefaults.standard.removeObject(forKey: Self.seenKey(key)) }
    }

    /// An action that first explains itself: the first time, [key]'s tip shows and the action runs on "Anladım".
    static func gated(_ key: String?, _ action: @escaping () -> Void) -> () -> Void {
        guard let key else { return action }
        return { shared.gate(key, action) }
    }

    func gate(_ key: String, _ action: @escaping () -> Void) {
        if !show(key, onGotIt: action) { action() }
    }

    /// Shows [key]'s tip next to its anchor. False when it was already seen.
    @discardableResult
    func show(_ key: String, onGotIt: (() -> Void)? = nil) -> Bool {
        guard let tip = Self.tips[key] else { return false }
        if !alwaysOn {
            guard !UserDefaults.standard.bool(forKey: Self.seenKey(key)) else { return false }
            UserDefaults.standard.set(true, forKey: Self.seenKey(key))
        }
        token += 1
        closing = false
        shown = Shown(key: key, tip: tip, anchor: "tip:\(key)", token: token, onGotIt: onGotIt)
        RealTipWindow.show()
        return true
    }

    func dismiss() {
        guard shown != nil, !closing else { return }
        closing = true
        let closed = token
        Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(180))
            guard let self, self.closing, self.token == closed else { return }
            self.closing = false
            self.shown = nil
            RealTipWindow.hide()
        }
    }

    /// "Anladım": closes the tip and carries on with what was tapped.
    func gotIt() {
        let action = shown?.onGotIt
        dismiss()
        action?()
    }
}

/// Above the sheets and full-screen covers, like ProWindow.
@MainActor
private enum RealTipWindow {
    static var window: UIWindow?

    static func show() {
        guard window == nil, let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }) else { return }
        let new = UIWindow(windowScene: scene)
        new.windowLevel = .alert + 1
        let host = UIHostingController(rootView: RealTipHost())
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

private struct RealTipHost: View {
    private var tips: RealTips { RealTips.shared }
    private let reduceMotion = UIAccessibility.isReduceMotionEnabled
    @State private var appeared = false
    @State private var shine: CGFloat = 0
    @State private var tipHeight: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                // A tap outside closes the tip and still carries on with what was tapped, so no press is lost.
                Color.black.opacity(0.001).onTapGesture { tips.gotIt() }
                if let current = tips.shown {
                    card(current)
                        .padding(.horizontal, 16)
                        .offset(y: placement(current, screen: geo.size.height))
                        .opacity(tipHeight == 0 || !appeared || tips.closing ? 0 : 1)
                        .scaleEffect(reduceMotion || appeared && !tips.closing ? 1 : (tips.closing ? 0.94 : 0.86))
                        .offset(y: reduceMotion || (appeared && !tips.closing) ? 0 : tipHeight / (tips.closing ? 5 : 2))
                        .animation(tips.closing ? .easeOut(duration: 0.17) : .spring(response: 0.5, dampingFraction: 0.65), value: appeared)
                        .animation(.easeOut(duration: 0.17), value: tips.closing)
                        .task(id: current.token) {
                            appeared = false
                            shine = 0
                            try? await Task.sleep(for: .milliseconds(30))
                            appeared = true
                            if !reduceMotion { withAnimation(.linear(duration: 1)) { shine = 1 } }
                        }
                }
            }
        }
        .ignoresSafeArea()
    }

    /// Below an anchor in the upper half; just above one in the lower half.
    private func placement(_ current: RealTips.Shown, screen: CGFloat) -> CGFloat {
        let insets = RealTipWindow.window?.safeAreaInsets ?? .zero
        let top = insets.top + 16
        let bottom = screen - insets.bottom - 16
        let y: CGFloat
        if let anchor = current.anchor.flatMap({ RealTips.shared.anchors[$0] }) {
            y = anchor.midY < screen / 2 ? anchor.maxY + 14 : anchor.minY - tipHeight - 14
        } else {
            y = bottom - tipHeight - 96
        }
        return min(max(y, top), max(bottom - tipHeight, top))
    }

    private func card(_ current: RealTips.Shown) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(t(current.tip.title)).font(RealFont.inter(15, .bold)).foregroundStyle(Color(argb: 0xFF0B0B0D))
                Text(t(current.tip.body)).font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFF3A3A42))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button { tips.gotIt() } label: {
                Text(t("Anladım")).font(RealFont.inter(14, .semibold)).foregroundStyle(.white)
                    .padding(.horizontal, 14).padding(.vertical, 9)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color(argb: 0xFF0B0B0D)))
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, 18).padding(.vertical, 14).padding(.trailing, 12)
        .background(Color(argb: 0xFFF5F5F7))
        // One soft light passes over the card as it lands.
        .overlay {
            GeometryReader { box in
                let band = box.size.width * 0.6
                LinearGradient(colors: [.clear, Color(argb: 0xB33D7BFF), .clear], startPoint: .topLeading, endPoint: .bottomTrailing)
                    .frame(width: band * 2)
                    .offset(x: -band * 2 + (box.size.width + band * 2) * shine)
            }
            .allowsHitTesting(false)
        }
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .background { GeometryReader { g in Color.clear.onChange(of: g.size.height, initial: true) { _, h in tipHeight = h } } }
        .shadow(color: .black.opacity(0.28), radius: 18, y: 6)
        // Taps on the tip itself do not close it; only "Anladım" or a tap outside do.
        .contentShape(Rectangle())
        .onTapGesture { }
    }
}

extension View {
    /// Where [key]'s tip appears: next to this view.
    func realTipAnchor(_ key: String?) -> some View {
        background {
            if let key {
                GeometryReader { geo in
                    Color.clear
                        .onChange(of: geo.frame(in: .global), initial: true) { _, frame in RealTips.shared.anchors["tip:\(key)"] = frame }
                        .onDisappear { RealTips.shared.anchors["tip:\(key)"] = nil }
                }
            }
        }
    }
}
