import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Yapıştır (ClipboardPaste.kt). The card header's Paste button is the system PasteButton, so iOS never asks
/// "Allow paste?"; it hands over the copied item only when tapped. The glass pill next to it shows up only for
/// something copied in the last 30 seconds (counted from when REAL notices the new copy).
@MainActor
@Observable
final class ClipboardWatcher {
    static let shared = ClipboardWatcher()
    static let window: TimeInterval = 30

    private(set) var freshUntil: Date = .distantPast
    @ObservationIgnored private var lastCount = UIPasteboard.general.changeCount
    @ObservationIgnored private var consumedCount = -1

    private init() {
        for name in [UIApplication.didBecomeActiveNotification, UIPasteboard.changedNotification] {
            NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { _ in
                Task { @MainActor in ClipboardWatcher.shared.check() }
            }
        }
    }

    /// Only the clipboard's change counter and kind are read here (no content, no system prompt).
    func check() {
        let pb = UIPasteboard.general
        let count = pb.changeCount
        guard count != lastCount else { return }
        lastCount = count
        if count != consumedCount, pb.hasStrings || pb.hasURLs || pb.hasImages { freshUntil = Date().addingTimeInterval(Self.window) }
    }

    func isFresh(at date: Date) -> Bool { date < freshUntil }

    func consume() { consumedCount = lastCount; freshUntil = .distantPast }
}

enum CardPaste {
    struct Result {
        var attachments: [ReminderAttachment] = []
        var url: String?
        var music: [MusicBlock] = []
        var label: String
    }

    static let contentTypes: [UTType] = [.image, .url, .plainText]

    /// Turns what PasteButton handed over into blocks: music link → music block, other link → the card's link,
    /// text → WhatsApp / e-posta / not snippet, image → photo.
    static func read(_ providers: [NSItemProvider]) async -> Result? {
        for provider in providers {
            if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier),
               let data = await loadData(provider, .image), let photo = AttachmentStorage.save(data) {
                return Result(attachments: [photo], label: "Fotoğraf")
            }
            if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier), let url = await loadURL(provider) {
                return await fromText(url.absoluteString)
            }
            if provider.canLoadObject(ofClass: NSString.self), let text = await loadString(provider), !text.isEmpty {
                return await fromText(text)
            }
        }
        return nil
    }

    static func fromText(_ raw: String) async -> Result {
        let parsed = SmartText.parse(raw)
        if let link = parsed.url {
            let found = await LinkResolver.resolve(link)
            if let music = found.music {
                return Result(music: [music], label: music.provider == "iTunes" ? "Apple Music şarkısı" : "\(music.provider) şarkısı")
            }
            return Result(url: link, label: "Bağlantı")
        }
        let source = SnippetSourceDetector.detect(text: raw)
        let snippet = ReminderAttachment(id: UUID().uuidString.lowercased(), type: .textSnippet, mimeType: "text/plain",
                                         text: parsed.snippet ?? raw, source: source)
        return Result(attachments: [snippet], label: SnippetSourceDetector.label(source))
    }

    /// A card shows at most 3 photos; more come in as files. A card has one link; a second one becomes a text block.
    static func apply(_ r: Result, to item: inout ReminderItem) {
        var room = max(0, 3 - item.attachments.filter { $0.type == .image }.count)
        for var a in r.attachments {
            if a.type == .image { if room > 0 { room -= 1 } else { a.type = .file } }
            item.attachments.append(a)
        }
        if let link = r.url {
            if item.referenceUrl == nil || item.referenceUrl == link { item.referenceUrl = link }
            else { item.attachments.append(ReminderAttachment(id: UUID().uuidString.lowercased(), type: .textSnippet, mimeType: "text/plain", text: link)) }
        }
        item.musicBlocks += r.music
        item.updatedAt = .now
    }

    private static func loadData(_ p: NSItemProvider, _ type: UTType) async -> Data? {
        await withCheckedContinuation { c in _ = p.loadDataRepresentation(forTypeIdentifier: type.identifier) { data, _ in c.resume(returning: data) } }
    }
    private static func loadURL(_ p: NSItemProvider) async -> URL? {
        await withCheckedContinuation { c in _ = p.loadObject(ofClass: NSURL.self) { obj, _ in c.resume(returning: (obj as? NSURL) as URL?) } }
    }
    private static func loadString(_ p: NSItemProvider) async -> String? {
        await withCheckedContinuation { c in _ = p.loadObject(ofClass: NSString.self) { obj, _ in c.resume(returning: (obj as? NSString) as String?) } }
    }
}

/// The glass "Yapıştır" pill (fresh copies only). PasteButton, so no system prompt either.
struct FreshPastePill: View {
    let onPaste: ([NSItemProvider]) -> Void
    @State private var watcher = ClipboardWatcher.shared

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            if watcher.isFresh(at: context.date) {
                PasteButton(supportedContentTypes: CardPaste.contentTypes) { providers in onPaste(providers) }
                    .labelStyle(.titleAndIcon)
                    .buttonBorderShape(.capsule)
                    .tint(Color.white.opacity(0.18))
                    .transition(.scale(scale: 0.85).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.2), value: watcher.freshUntil)
    }
}
