import SwiftUI

/// YasamJournalSheet.kt: "Bugün yaz" bottom sheet. Saving creates a "Günlük · d MMMM" journal card; the text is kept
/// if saving fails.
struct YasamJournalSheet: View {
    let onSave: (String, [BodySpan]) -> Bool
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var styles: [BodySpan] = []
    @State private var selection = NSRange(location: 0, length: 0)
    @State private var pending: Set<BodyMark>?
    @State private var formatOpen = false
    @State private var error: String?

    private let ink = Color(argb: 0xFF0B0B0D)
    private let glass = Color(argb: 0xFF151518)
    private let muted = Color(argb: 0xFF8E8E98)

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text(t("Bugün yaz")).font(RealFont.inter(28, .semibold)).foregroundStyle(.white).padding(.top, 8)
                Text(t("Nasıl geçti? Aklında kalanı yaz.")).font(RealFont.inter(14)).foregroundStyle(muted)
                Color.clear.frame(height: 4)
                ZStack(alignment: .topLeading) {
                    if text.isEmpty { Text(t("Günlüğün…")).font(RealFont.inter(16)).foregroundStyle(muted).allowsHitTesting(false) }
                    RichBodyEditor(text: $text, styles: $styles, selection: $selection, pending: $pending,
                                   format: Self.format, foreground: .white, onDark: true, minHeight: 144, autoFocus: true)
                        .onChange(of: text) { _, _ in error = nil }
                }
                .padding(18)
                .frame(maxWidth: .infinity, minHeight: 180, alignment: .topLeading)
                .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(glass))
                .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.white.opacity(0.10), lineWidth: 1))
                BodyFormatControls(text: text, selection: selection, styles: $styles, pending: $pending, open: $formatOpen)
                if let error { Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFF8A80)) }
                let canSave = !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                Button {
                    let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    let from = text.prefix { $0.isWhitespace || $0.isNewline }.utf16.count
                    let valueStyles = BodySpans.slice(styles, from, from + value.utf16.count)
                    if onSave(value, valueStyles) { dismiss() } else { error = t("Günlük kaydedilemedi.") }
                } label: {
                    Text(t("Kaydet")).font(RealFont.inter(16, .semibold)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity).frame(height: 52)
                        .background(Capsule().fill(.white.opacity(canSave ? 1 : 0.35)))
                }
                .buttonStyle(.plain).disabled(!canSave)
                Button { dismiss() } label: {
                    Text(t("Vazgeç")).font(RealFont.inter(16, .semibold)).foregroundStyle(.white)
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
    }

    /// Android YasamJournalSheet: BodyFormat(size = LARGE).
    static let format = BodyFormat(size: .large)

    /// STANDALONE_JOURNAL_PREFIX + "d MMMM" (tr). Stored card text: Android finds journal cards by this
    /// Turkish prefix, so it is never translated.
    static func title(_ date: Date = Date()) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "tr_TR")
        f.dateFormat = "d MMMM"
        return "Günlük · " + f.string(from: date)
    }
}
