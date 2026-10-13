import SwiftUI

/// YasamJournalSheet.kt: "Bugün yaz" bottom sheet. Saving creates a "Günlük · d MMMM" journal card; the text is kept
/// if saving fails.
struct YasamJournalSheet: View {
    let onSave: (String) -> Bool
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var error: String?
    @FocusState private var focused: Bool

    private let ink = Color(argb: 0xFF0B0B0D)
    private let glass = Color(argb: 0xFF151518)
    private let muted = Color(argb: 0xFF8E8E98)

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text("Bugün yaz").font(RealFont.inter(28, .semibold)).foregroundStyle(.white).padding(.top, 8)
                Text("Nasıl geçti? Aklında kalanı yaz.").font(RealFont.inter(14)).foregroundStyle(muted)
                Color.clear.frame(height: 4)
                ZStack(alignment: .topLeading) {
                    if text.isEmpty { Text("Günlüğün…").font(RealFont.inter(16)).foregroundStyle(muted) }
                    TextField("", text: $text, axis: .vertical)
                        .font(RealFont.inter(16)).lineSpacing(8).foregroundStyle(.white).tint(.white)
                        .focused($focused)
                        .onChange(of: text) { _, _ in error = nil }
                }
                .padding(18)
                .frame(maxWidth: .infinity, minHeight: 180, alignment: .topLeading)
                .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(glass))
                .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.white.opacity(0.10), lineWidth: 1))
                .contentShape(Rectangle())
                .onTapGesture { focused = true }
                if let error { Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFF8A80)) }
                let canSave = !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                Button {
                    if onSave(text.trimmingCharacters(in: .whitespacesAndNewlines)) { dismiss() } else { error = "Günlük kaydedilemedi." }
                } label: {
                    Text("Kaydet").font(RealFont.inter(16, .semibold)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity).frame(height: 52)
                        .background(Capsule().fill(.white.opacity(canSave ? 1 : 0.35)))
                }
                .buttonStyle(.plain).disabled(!canSave)
                Button { dismiss() } label: {
                    Text("Vazgeç").font(RealFont.inter(16, .semibold)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).frame(height: 52)
                        .background(Capsule().fill(glass))
                        .overlay(Capsule().strokeBorder(.white.opacity(0.10), lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20).padding(.bottom, 20)
        }
        .scrollDismissesKeyboard(.interactively)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(32)
        .presentationBackground(ink)
        .onAppear { focused = true }
    }

    /// STANDALONE_JOURNAL_PREFIX + "d MMMM" (tr).
    static func title(_ date: Date = Date()) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "tr_TR")
        f.dateFormat = "d MMMM"
        return "Günlük · " + f.string(from: date)
    }
}
