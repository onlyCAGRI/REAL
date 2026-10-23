import SwiftUI

/// Text snippet block (ReminderContentSections.kt): source (WhatsApp / E-posta / Not), optional title and the text.
/// "Metni aç" grows the block in place ("Daralt" closes it); tapping the text edits it right there, "Bitti" or tapping
/// elsewhere saves.
struct SnippetBlockView: View {
    let attachment: ReminderAttachment
    var editing = false
    var onSave: (String) -> Void
    var onRemove: () -> Void = {}

    @State private var expanded = false
    @State private var inline = false
    @State private var draft = ""
    @FocusState private var focused: Bool

    private var text: String { attachment.text ?? "" }
    private var long: Bool { text.count > 180 || text.split(separator: "\n", omittingEmptySubsequences: false).count > 4 }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                RealSymbol(attachment.source == SnippetSourceDetector.whatsapp ? "message" : (attachment.source == SnippetSourceDetector.email ? "envelope" : "note.text"), size: 16)
                    .foregroundStyle(Color(argb: 0xFFC2C6CB))
                Text(SnippetSourceDetector.label(attachment.source)).font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFD1D1D8))
                Spacer()
                if inline {
                    Button(t("Bitti")) { save() }.font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFE6E6EC))
                }
                if editing {
                    Button(action: onRemove) { RealSymbol("xmark", size: 14).foregroundStyle(Color(argb: 0xFFAEAFB8)).frame(width: 36, height: 36) }
                        .accessibilityLabel(t("Metin alıntısını kaldır"))
                }
            }
            if let title = attachment.sourceTitle, !title.isEmpty, title != "REAL_TEXT_BLOCK" {
                Text(title).font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFFE6E6EC))
                    .lineLimit(expanded || inline ? nil : 1)
            }
            if inline {
                TextField("", text: $draft, axis: .vertical)
                    .font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFE6E6EC)).tint(Color(argb: 0xFF91B9FF))
                    .focused($focused)
            } else {
                Text(text.isEmpty ? " " : text).font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFBFC0C8))
                    .lineLimit(expanded ? nil : 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture { draft = text; inline = true; DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) { focused = true } }
                if long {
                    Button(expanded ? t("Daralt") : t("Metni aç")) { withAnimation(.easeOut(duration: 0.24)) { expanded.toggle() } }
                        .font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFCAD3D9))
                }
            }
        }
        .padding(16)
        .cardContentChrome(corners: 22)
        .animation(.easeOut(duration: 0.24), value: inline)
        .onChange(of: focused) { _, isFocused in if !isFocused && inline { save() } }
    }

    private func save() {
        guard inline else { return }
        inline = false
        focused = false
        if draft != text { onSave(draft) }
    }
}
