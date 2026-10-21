import SwiftUI

/// Settings > AI ve gizlilik (AiPrivacyPanel.kt): one switch per AI feature, with what it sends. Turning a switch off
/// withdraws the consent on this device (nothing more is sent until it is given again); turning it on asks the same
/// question the feature asks the first time.
struct AiPrivacySheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var auth = RealAuth.shared
    @State private var revision = 0

    private let features: [(AiConsent.Feature, String)] = [
        (.voice, "Sesli komutlar"),
        (.mood, "Mood önerileri"),
        (.discovery, "Film, dizi ve oyun önerileri"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("AI ve gizlilik").font(RealFont.inter(20, .semibold)).foregroundStyle(.white)
                Spacer()
                Button("Bitti") { dismiss() }.font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFFBCBCC5))
            }
            Text(auth.account == nil
                 ? "AI özellikleri REAL hesabıyla çalışır. Giriş yaptığında her biri ilk kullanımda ayrıca izin ister."
                 : "Her AI özelliği yalnızca izin verdiğinde veri gönderir. İstediğin zaman kapatabilirsin.")
                .font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF9D9DA7))
            VStack(spacing: 0) {
                ForEach(Array(features.enumerated()), id: \.offset) { index, item in
                    if index > 0 { Rectangle().fill(.white.opacity(0.08)).frame(height: 1).padding(.leading, 16) }
                    row(item.0, item.1)
                }
            }
            .background(RoundedRectangle(cornerRadius: 20).fill(Color(argb: 0xFF17171B)))
            .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(.white.opacity(0.08), lineWidth: 0.8))
            Text("İzni kapatmak daha önce gönderilen verileri silmez. Veriler REAL sunucusu üzerinden Groq veya Google Gemini'ye iletilir ve yurt dışında işlenebilir.")
                .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF7E7E88))
            Spacer()
        }
        .padding(20)
        .presentationDetents([.medium, .large])
        .presentationBackground(Color(argb: 0xFF0E0E11))
        .presentationDragIndicator(.visible)
    }

    private func row(_ feature: AiConsent.Feature, _ title: String) -> some View {
        let _ = revision
        let on = AiConsent.allowed(feature)
        return Toggle(isOn: Binding(
            get: { on },
            set: { wanted in
                if wanted {
                    Task { @MainActor in
                        try? await AiConsent.require(feature)
                        revision += 1
                    }
                } else {
                    AiConsent.revoke(feature)
                    revision += 1
                }
            })) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(RealFont.inter(15, .medium)).foregroundStyle(.white)
                Text(feature.disclosure).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF9D9DA7)).fixedSize(horizontal: false, vertical: true)
            }
        }
        .tint(Color(argb: 0xFF69ACFF))
        .disabled(auth.account == nil)
        .padding(.horizontal, 16).padding(.vertical, 13)
    }
}
