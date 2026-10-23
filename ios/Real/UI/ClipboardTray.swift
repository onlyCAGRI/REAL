import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// "Panodan ekle" (Android ClipboardTray.kt). iOS has no keyboard clipboard history, so the tray is filled with the
/// system PasteButton (no "Allow paste?" prompt): each tap adds what is on the clipboard now as its own row. Rows live
/// as long as the app runs, so they can go into several cards; "+" adds a row to the end of the card.
enum TrayItem: Identifiable {
    case text(id: Int, String)
    case image(id: Int, Data)

    var id: Int {
        switch self {
        case .text(let id, _), .image(let id, _): return id
        }
    }
}

@MainActor
@Observable
final class ClipboardTrayStore {
    static let shared = ClipboardTrayStore()
    private(set) var items: [TrayItem] = []
    @ObservationIgnored private var nextId = 0

    func add(_ providers: [NSItemProvider]) async {
        for provider in providers {
            if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier),
               let data = await Self.loadData(provider) {
                insert(.image(id: take(), data)); continue
            }
            if provider.canLoadObject(ofClass: NSURL.self), let url = await Self.loadURL(provider) {
                insert(.text(id: take(), url.absoluteString)); continue
            }
            if provider.canLoadObject(ofClass: NSString.self), let text = await Self.loadString(provider),
               !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                insert(.text(id: take(), text.trimmingCharacters(in: .whitespacesAndNewlines)))
            }
        }
    }

    func addTyped(_ text: String) {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if !clean.isEmpty { insert(.text(id: take(), clean)) }
    }

    /// The same blocks a normal paste makes: music link → music, link → the card's link, text → snippet, image → photo.
    static func result(for item: TrayItem) async -> CardPaste.Result? {
        switch item {
        case .text(_, let text): return await CardPaste.fromText(text)
        case .image(_, let data):
            guard let photo = AttachmentStorage.save(data) else { return nil }
            return CardPaste.Result(attachments: [photo], label: t("Fotoğraf"))
        }
    }

    private func take() -> Int { defer { nextId += 1 }; return nextId }
    private func insert(_ item: TrayItem) { items.insert(item, at: 0) }

    private static func loadData(_ p: NSItemProvider) async -> Data? {
        await withCheckedContinuation { c in _ = p.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { d, _ in c.resume(returning: d) } }
    }
    private static func loadURL(_ p: NSItemProvider) async -> URL? {
        await withCheckedContinuation { c in _ = p.loadObject(ofClass: NSURL.self) { o, _ in c.resume(returning: (o as? NSURL) as URL?) } }
    }
    private static func loadString(_ p: NSItemProvider) async -> String? {
        await withCheckedContinuation { c in _ = p.loadObject(ofClass: NSString.self) { o, _ in c.resume(returning: (o as? NSString) as String?) } }
    }
}

struct ClipboardTraySheet: View {
    let busy: Bool
    let onAppend: (TrayItem) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var store = ClipboardTrayStore.shared
    @State private var typed = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                PasteButton(supportedContentTypes: CardPaste.contentTypes) { providers in
                    Task { @MainActor in await store.add(providers) }
                }
                .labelStyle(.titleAndIcon)
                .buttonBorderShape(.capsule)
                .tint(Color(red: 0.57, green: 0.73, blue: 1))
                Spacer()
                Button(t("Bitti")) { dismiss() }.foregroundStyle(Color(white: 0.8))
            }
            Text(t("Kopyaladığın her şeyi Yapıştır ile buraya topla; + ile karta ekle. Liste uygulama kapanana kadar durur."))
                .font(.system(size: 12)).foregroundStyle(Color(white: 0.56))
            TextField(t("ya da buraya yaz…"), text: $typed)
                .textFieldStyle(.plain)
                .padding(.horizontal, 12).padding(.vertical, 9)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.07)))
                .onSubmit { store.addTyped(typed); typed = "" }
            if store.items.isEmpty {
                Text(t("Henüz bir şey yok")).font(.system(size: 13)).foregroundStyle(Color(white: 0.43)).padding(.vertical, 10)
            } else {
                ScrollView {
                    LazyVStack(spacing: 6) {
                        ForEach(store.items) { item in row(item) }
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .presentationDetents([.medium, .large])
        .presentationBackground(Color(red: 0.10, green: 0.10, blue: 0.12))
        .preferredColorScheme(.dark)
    }

    private func row(_ item: TrayItem) -> some View {
        HStack(spacing: 10) {
            switch item {
            case .text(_, let text):
                VStack(alignment: .leading, spacing: 2) {
                    Text(text.replacingOccurrences(of: "\n", with: " ")).font(.system(size: 13)).lineLimit(2)
                    Text(text.lowercased().hasPrefix("http") ? t("Bağlantı") : t("Metin")).font(.system(size: 11)).foregroundStyle(Color(white: 0.56))
                }
            case .image(_, let data):
                if let image = UIImage(data: data) {
                    Image(uiImage: image).resizable().scaledToFill().frame(width: 40, height: 40).clipShape(RoundedRectangle(cornerRadius: 8))
                }
                Text(t("Fotoğraf")).font(.system(size: 13))
            }
            Spacer()
            Button { onAppend(item) } label: {
                Text("+").font(.system(size: 20, weight: .medium)).foregroundStyle(Color(red: 0.04, green: 0.10, blue: 0.20))
                    .frame(width: 34, height: 34).background(Circle().fill(Color(red: 0.57, green: 0.73, blue: 1)))
            }
            .disabled(busy)
            .accessibilityLabel(t("Sona ekle"))
        }
        .padding(.leading, 12).padding(.trailing, 6).padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.07)))
    }
}
