import SwiftUI

/// SignInRequired.kt: Misafir taps something that lives in the account (sharing, people, backup). The usual "Hesap ekle"
/// sheet opens with a title for it; on sign-in what the guest made on this iPhone moves into the account.
enum SignInReason {
    case share, people, backup

    var title: String {
        switch self {
        case .share: return "Paylaşmak için hesap gerekiyor"
        case .people: return "Kişiler için hesap gerekiyor"
        case .backup: return "Yedekleme için hesap gerekiyor"
        }
    }
    static let keepNote = "Misafirken yaptıkların kaybolmaz; hesabına hemen taşınır ve buluta yedeklenir."
}

@MainActor
enum GuestAdoption {
    /// After signing in from Misafir: the account's cards come down and this iPhone's own ones stay (and go up).
    static func signedIn(store: ReminderStore, life: LifeStore) async {
        do {
            RealCloudSync.shared.reset()
            _ = try await RealCloudRestore.run(store: store, life: life, keepGuest: true)
            RealToast.shared.show("Kartların hesabına taşındı")
        } catch {
            RealToast.shared.show("Giriş yapıldı; kartlar birazdan eşitlenecek")
        }
    }
}

/// Inside a sheet (Kartı paylaş, Kişilerim): the note and a "Hesap ekle" button that opens the titled sign-in sheet.
struct SignInRequiredPrompt: View {
    let reason: SignInReason
    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life
    @State private var showAuth = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(reason.title).font(RealFont.inter(16, .medium)).foregroundStyle(.white)
            Text(SignInReason.keepNote).font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.6))
            Button { showAuth = true } label: {
                Text("Hesap ekle").font(RealFont.inter(15, .semibold)).foregroundStyle(.black)
                    .frame(maxWidth: .infinity, minHeight: 46).background(Capsule().fill(.white))
            }
            .buttonStyle(.plain)
        }
        .sheet(isPresented: $showAuth) {
            AuthChoiceSheet(title: reason.title, subtitle: SignInReason.keepNote) { _ in
                Task { await GuestAdoption.signedIn(store: store, life: life) }
            }
        }
    }
}

/// Hafif hatırlatma on Home (GuestBackupNudge in SignInRequired.kt): a guest with ≥ 10 cards sees it once; "Sonra"
/// brings it back a week later one more time, ✕ (or the second "Sonra") puts it away for good.
struct GuestBackupNudge: View {
    let cardCount: Int
    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life
    @AppStorage("real_guest_nudge_done") private var done = false
    @AppStorage("real_guest_nudge_later_until") private var laterUntil: Double = 0
    @State private var auth = RealAuth.shared
    @State private var showAuth = false

    private var visible: Bool {
        auth.account == nil && cardCount >= 10 && !done && Date().timeIntervalSince1970 >= laterUntil
    }

    var body: some View {
        if visible {
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Kartların yalnız bu telefonda").font(RealFont.inter(14, .medium)).foregroundStyle(.white)
                    Text("Telefon kaybolursa ya da uygulama silinirse gider. Hesap ekle, hepsi yedeklensin.")
                        .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF9A9AA3)).padding(.bottom, 7)
                    HStack(spacing: 8) {
                        Button { showAuth = true } label: {
                            Text("Hesap ekle").font(RealFont.inter(13, .medium)).foregroundStyle(.black)
                                .padding(.horizontal, 14).padding(.vertical, 8).background(Capsule().fill(.white))
                        }
                        Button { later() } label: {
                            Text("Sonra").font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                                .padding(.horizontal, 14).padding(.vertical, 8)
                                .overlay(Capsule().strokeBorder(.white.opacity(0.16)))
                        }
                    }
                    .buttonStyle(.plain)
                }
                Spacer(minLength: 0)
                Button { done = true } label: {
                    Text("✕").font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFF6A6A72)).padding(4)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Hatırlatmayı kapat")
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Color(argb: 0xFF121A14)))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Color(argb: 0xFF1F3A26)))
            .padding(.horizontal, 16).padding(.bottom, 8)
            .sheet(isPresented: $showAuth) {
                AuthChoiceSheet(title: SignInReason.backup.title, subtitle: SignInReason.keepNote) { _ in
                    Task { await GuestAdoption.signedIn(store: store, life: life) }
                }
            }
        }
    }

    private func later() {
        if laterUntil == 0 { laterUntil = Date().timeIntervalSince1970 + 7 * 24 * 3600 } else { done = true }
    }
}
