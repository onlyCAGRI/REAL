import SwiftUI

/// "Hesap ekle" (AuthChoiceDialog in AuthDialogs.kt): Google or e-posta. `startWithEmail` opens the e-mail form at once
/// (the welcome screen's "E-posta ile devam et").
struct AuthChoiceSheet: View {
    var startWithEmail = false
    /// SignInRequired: what the guest tapped asks for an account.
    var title = t("Hesap ekle")
    var subtitle = t("Notların bu cihazda kalır. Hesap; eşitleme ve yedekleme için kullanılır.")
    let onAuthenticated: (RealAuth.Account) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var auth = RealAuth.shared
    @State private var showEmail = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if showEmail {
                EmailAuthForm(onBack: { if startWithEmail { dismiss() } else { withAnimation(.easeOut(duration: 0.18)) { showEmail = false } } },
                              onAuthenticated: { account in dismiss(); onAuthenticated(account) })
                    .transition(.opacity)
            } else {
                Text(t(title)).font(RealFont.inter(24, .semibold)).foregroundStyle(.white).padding(.top, 8)
                Text(t(subtitle))
                    .font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFFA9A9B2)).padding(.bottom, 6)
                methodRow(glyph: "G", tone: Color(argb: 0xFF6FD6FF), title: auth.busy ? t("Google açılıyor…") : t("Google ile devam et"),
                          subtitle: t("Hesabını Google ile bağla")) {
                    error = nil
                    Task { @MainActor in
                        if let account = await auth.signInWithGoogle() { dismiss(); onAuthenticated(account) }
                        else { error = auth.lastError }
                    }
                }
                methodRow(glyph: "A", tone: .white, title: t("Apple ile devam et"), subtitle: t("Hesabını Apple ile bağla")) {
                    error = nil
                    Task { @MainActor in
                        if let account = await auth.signInWithApple() { dismiss(); onAuthenticated(account) }
                        else { error = auth.lastError }
                    }
                }
                methodRow(glyph: "@", tone: Color(argb: 0xFFB59BFF), title: t("E-posta ile devam et"), subtitle: t("Giriş yap ya da hesap oluştur")) {
                    error = nil
                    withAnimation(.easeOut(duration: 0.18)) { showEmail = true }
                }
                if let error { Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFF7A70)) }
                Button(t("Vazgeç")) { dismiss() }
                    .font(RealFont.inter(14, .medium)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 40)
                    .background(Capsule().fill(.white.opacity(0.06))).overlay(Capsule().strokeBorder(.white.opacity(0.10)))
                    .padding(.top, 4)
            }
        }
        .padding(.horizontal, 20).padding(.vertical, 16)
        .frame(maxHeight: .infinity, alignment: .top)
        .presentationDetents([.large])
        .presentationBackground(Color(argb: 0xFF0B0B0D))
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(auth.busy)
        .onAppear { if startWithEmail { showEmail = true } }
    }

    private func methodRow(glyph: String, tone: Color, title: String, subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Group {
                    if glyph == "G" { Image("GoogleG").resizable().frame(width: 20, height: 20) }
                    else if glyph == "A" { Image(systemName: "apple.logo").font(.system(size: 19)).foregroundStyle(.white) }
                    else { Text(glyph).font(RealFont.inter(18, .bold)).foregroundStyle(tone) }
                }
                    .frame(width: 42, height: 42).background(Circle().fill(tone.opacity(0.14)))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(RealFont.inter(16, .medium)).foregroundStyle(.white)
                    Text(subtitle).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF9A9AA2))
                }
                Spacer()
                Text("›").font(RealFont.inter(20)).foregroundStyle(Color(argb: 0xFF9A9AA2))
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.white.opacity(0.06)))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(.white.opacity(0.10)))
        }
        .buttonStyle(PressScaleStyle(scale: 0.98))
        .disabled(auth.busy)
    }
}

/// E-posta ile giriş / Hesap oluştur (EmailAuthContent): sign in, create an account with a name, or reset the password.
struct EmailAuthForm: View {
    let onBack: () -> Void
    let onAuthenticated: (RealAuth.Account) -> Void
    private enum Mode { case signIn, create }
    @State private var mode: Mode = .signIn
    @State private var email = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var busy = false
    @State private var error: String?
    @State private var info: String?
    @FocusState private var focus: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Button(action: onBack) {
                RealSymbol("chevron.left", size: 19.5).foregroundStyle(.white).frame(width: 40, height: 40).liquidGlass(Circle(), role: .chrome)
            }
            .buttonStyle(.plain).disabled(busy).padding(.top, 8)
            .accessibilityLabel(t("Geri"))
            Text(mode == .signIn ? t("E-posta ile giriş") : t("Hesap oluştur"))
                .font(RealFont.inter(28, .semibold)).foregroundStyle(.white).padding(.top, 4).padding(.bottom, 8)
            if mode == .create {
                field(t("Ad (zorunlu)"), text: $firstName, tag: 0).textContentType(.givenName)
                field(t("Soyad (isteğe bağlı)"), text: $lastName, tag: 1).textContentType(.familyName)
            }
            field(t("E-posta"), text: $email, tag: 2)
                .textContentType(.emailAddress).keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
            field(t("Şifre"), text: $password, tag: 3, secure: true).textContentType(mode == .signIn ? .password : .newPassword)
            if mode == .create { field(t("Şifreyi tekrar yaz"), text: $confirm, tag: 4, secure: true).textContentType(.newPassword) }
            if let error { Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFF7A70)) }
            if let info { Text(info).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF7BE0A4)) }
            Button(action: submit) {
                Text(busy ? t("Bekleyin…") : (mode == .signIn ? t("Giriş yap") : t("Hesap oluştur")))
                    .font(RealFont.inter(15, .semibold)).foregroundStyle(.black)
                    .frame(maxWidth: .infinity, minHeight: 50).background(Capsule().fill(.white))
            }
            .buttonStyle(PressScaleStyle(scale: 0.98)).disabled(busy).padding(.top, 4)
            HStack(spacing: 8) {
                if mode == .signIn { pill(t("Şifremi unuttum"), action: resetPassword) }
                pill(mode == .signIn ? t("Yeni hesap oluştur") : t("Zaten hesabım var")) {
                    mode = mode == .signIn ? .create : .signIn
                    password = ""; confirm = ""; error = nil; info = nil
                }
            }
        }
        .onSubmit { if focus == 3 && mode == .signIn || focus == 4 { submit() } else { focus = (focus ?? 0) + 1 } }
    }

    private func field(_ placeholder: String, text: Binding<String>, tag: Int, secure: Bool = false) -> some View {
        Group {
            if secure { SecureField("", text: text, prompt: Text(placeholder).foregroundStyle(Color(argb: 0xFF6F6F78))) }
            else { TextField("", text: text, prompt: Text(placeholder).foregroundStyle(Color(argb: 0xFF6F6F78))) }
        }
        .font(RealFont.inter(16)).foregroundStyle(.white).tint(RealColors.accentBlue)
        .focused($focus, equals: tag)
        .padding(.horizontal, 16).frame(height: 52)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.white.opacity(0.06)))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(.white.opacity(0.10)))
        .disabled(busy)
        .onChange(of: text.wrappedValue) { _, _ in error = nil; info = nil }
    }

    private func pill(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(RealFont.inter(13, .medium)).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity, minHeight: 38)
                .background(Capsule().fill(.white.opacity(0.06))).overlay(Capsule().strokeBorder(.white.opacity(0.10)))
        }
        .buttonStyle(.plain).disabled(busy)
    }

    private func submit() {
        guard !busy else { return }
        let mail = email.trimmingCharacters(in: .whitespaces)
        guard !mail.isEmpty, !password.isEmpty else { error = t("E-posta ve şifreyi doldurun."); return }
        if mode == .create {
            guard !firstName.trimmingCharacters(in: .whitespaces).isEmpty else { error = t("Adını yaz."); return }
            guard password.count >= 6 else { error = t("Şifre en az 6 karakter olmalı."); return }
            guard password == confirm else { error = t("Şifreler eşleşmiyor."); return }
        }
        busy = true; error = nil; info = nil; focus = nil
        Task { @MainActor in
            defer { busy = false }
            do {
                let account = mode == .signIn
                    ? try await RealAuth.shared.signInWithEmail(mail, password: password)
                    : try await RealAuth.shared.createAccount(email: mail, password: password, firstName: firstName, lastName: lastName)
                if let account { onAuthenticated(account) }
            } catch { self.error = error.localizedDescription }
        }
    }

    private func resetPassword() {
        let mail = email.trimmingCharacters(in: .whitespaces)
        guard !mail.isEmpty else { error = t("Önce e-posta adresini yazın."); return }
        busy = true; error = nil; info = nil
        Task { @MainActor in
            defer { busy = false }
            do { try await RealAuth.shared.sendPasswordReset(mail); info = t("Şifre sıfırlama bağlantısı e-posta adresine gönderildi.") }
            catch { self.error = error.localizedDescription }
        }
    }
}
