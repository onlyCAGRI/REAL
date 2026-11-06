import SwiftUI

/// "Veri kullanımı" (RealDataUseDialog.kt): a short, plain summary of the privacy policy, one tap per topic,
/// with the full documents one tap away. Reading it grants nothing; AI asks for its own permission.
struct DataUseSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var open: Int?

    private let origin = "https://real-privacy-staging.realapp.workers.dev"
    private var lang: String { I18n.isEnglish ? "en" : "tr" }

    private var sections: [(String, String)] {
        [
            (t("Kısaca"), t("Verilerini satmayız, reklam göstermeyiz, seni takip etmeyiz ve analitik araç kullanmayız. Kartların sana aittir; destek istemediğin, güvenlik ya da yasa gerektirmediği sürece içeriğine bakmayız. Sorular için: cagrigull@outlook.com (Çağrı Gül, REAL’in geliştiricisi).")),
            (t("Hesap ve bulut"), t("Giriş yaptığında kullanıcı kimliğin, e-postan ve adın Firebase ile doğrulanır; şifreni görmeyiz. Bulut eşitleme açıksa kartların, Panoların ve eklediğin dosyalar Cloudflare sunucularında şifreli bağlantıyla tutulur. Bu sunucular çoğunlukla ABD ve Avrupa’dadır.")),
            (t("Cihazında kalanlar"), t("Hesap açmadan kullanırsan her şey cihazında kalır. Kitapların ve okuma ilerlemen her zaman cihazdadır.")),
            (t("İzinler"), t("Konum yalnız karta yer eklerken veya hava durumu için kullanılır; arka planda takip yoktur. Takvimde yalnız seçtiğin takvimler okunur ve yazılır. Mikrofon sesli komut ve kayıt içindir. Fotoğraflardan yalnız seçtiklerin kullanılır. İzinleri iPhone Ayarları’ndan istediğin zaman kapatabilirsin.")),
            (t("Paylaşım"), t("Panoya eklediğin kişiler o Panoyu görür. Public bir bağlantıya sahip olan herkes onu açabilir ve başkasına iletebilir; yeni bağlantılar 24 saat geçerlidir. Takvime aktardığın planın açıklamasındaki bağlantıyı etkinliğin davetlileri de görebilir.")),
            (t("Yapay zekâ"), t("AI özellikleri isteğe bağlıdır ve ilk kullanımdan önce ne gönderileceğini gösterip izin ister. Komutlar önce cihazda çözülür; gerekirse yalnız gereken metin Groq veya Google Gemini’ye gider. Ham ses ve fotoğraf gönderilmez. İzni Ayarlar → AI ve gizlilik bölümünden kapatabilirsin.")),
            (t("Birlikte çalıştığımız hizmetler"), t("Google Firebase (giriş, doğrulama, bildirim), Cloudflare (sunucu ve dosyalar), Groq ve Google Gemini (yalnız izin verdiğin AI özellikleri), Apple ve Google (mağaza ve satın alma), Apple (hava durumu, harita, Apple Music), Google (Street View) ve WhatsApp ile göndermeyi seçersen Meta. Film, müzik, oyun ve harita aramalarında cihazın ilgili kataloglara doğrudan bağlanır; bu isteklerde yalnız arama terimi ve IP adresin görünür.")),
            (t("Saklama ve silme"), t("Kartların sen silene kadar saklanır; buluttan sildiğin dosyalar 30 gün sonra kalıcı olarak silinir. Hesapları yönet bölümünden yalnız bulut verilerini (kartların cihazda kalır) ya da hesabının tamamını silebilirsin. Hesap silmek mağaza aboneliğini iptal etmez.")),
            (t("Hakların"), t("Verilerine erişme, kopyasını alma, düzeltme, silme, taşıma ve işlemeye itiraz etme hakkın var. Talebin için cagrigull@outlook.com adresine yaz; en geç 30 gün içinde, ücretsiz yanıt veririz. Yaşadığın ülkenin veri koruma kurumuna şikâyet hakkın her zaman saklıdır.")),
        ]
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text(t("Veri kullanımı")).font(RealFont.inter(20, .semibold)).foregroundStyle(.white)
                    Spacer()
                    Button(t("Bitti")) { dismiss() }.font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFFBCBCC5))
                }
                Text(t("Kısa özet. Tam metin Gizlilik Politikası’nda."))
                    .font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF9D9DA7))
                VStack(spacing: 0) {
                    ForEach(Array(sections.enumerated()), id: \.offset) { index, item in
                        if index > 0 { Rectangle().fill(.white.opacity(0.08)).frame(height: 1).padding(.leading, 16) }
                        Button {
                            withAnimation(.easeOut(duration: 0.2)) { open = open == index ? nil : index }
                        } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text(item.0).font(RealFont.inter(15, .medium)).foregroundStyle(.white)
                                    Spacer()
                                    Image(systemName: open == index ? "chevron.up" : "chevron.down")
                                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(Color(argb: 0xFF7E7E88))
                                }
                                if open == index {
                                    Text(item.1).font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.72))
                                        .multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
                                }
                            }
                            .padding(.horizontal, 16).padding(.vertical, 14)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint(open == index ? t("Açık") : t("Kapalı"))
                    }
                }
                .background(RoundedRectangle(cornerRadius: 20).fill(Color(argb: 0xFF17171B)))
                .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(.white.opacity(0.08), lineWidth: 0.8))
                VStack(alignment: .leading, spacing: 10) {
                    docLink(t("Gizlilik politikası"), "/privacy")
                    docLink(t("Kullanım koşulları"), "/terms")
                    docLink(t("AI ve gizlilik"), "/ai")
                    docLink(t("Hakların ve talepler"), "/rights")
                    if !I18n.isEnglish { docLink(t("KVKK aydınlatma"), "/kvkk") }
                }
                .padding(.top, 4)
            }
            .padding(20)
        }
        .background(Color.black.ignoresSafeArea())
        .presentationDragIndicator(.visible)
    }

    private func docLink(_ title: String, _ path: String) -> some View {
        Button {
            if let url = URL(string: origin + path + "?lang=" + lang) { openURL(url) }
        } label: {
            HStack(spacing: 6) {
                Text(title).font(RealFont.inter(14, .medium))
                Image(systemName: "arrow.up.right").font(.system(size: 11, weight: .semibold))
            }
            .foregroundStyle(Color(argb: 0xFFBCD6FF))
            .frame(minHeight: 32)
        }
        .buttonStyle(.plain)
    }
}
