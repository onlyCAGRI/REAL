import SwiftUI
import UIKit
import AVFoundation

/// Share → REAL, app side: puts the shares the extension left in the App Group inbox on cards, and keeps the light
/// card / category list the extension's "Karta ekle" shows.
@MainActor
enum ShareImport {
    static func run(_ store: ReminderStore) {
        let pending = ShareInbox.pending()
        guard !pending.isEmpty else { return }
        var added = 0
        for (item, dir) in pending {
            if apply(item, dir, store) { added += 1 }
            ShareInbox.discard(item.id)
        }
        if added > 0 { RealToast.shared.show(added == 1 ? "Paylaşım REAL'e eklendi" : "\(added) paylaşım REAL'e eklendi") }
        writeSnapshot(store)
    }

    private static func apply(_ item: ShareInbox.Item, _ dir: URL, _ store: ReminderStore) -> Bool {
        var attachments: [ReminderAttachment] = []
        var voices: [ReminderVoiceRecording] = []
        for f in item.files {
            let url = dir.appendingPathComponent(f.name)
            if f.isImage, let data = try? Data(contentsOf: url), let photo = AttachmentStorage.save(data) { attachments.append(photo); continue }
            if f.isAudio, let voice = voiceRecording(url) { voices.append(voice); continue }
            if var file = AttachmentStorage.saveFile(url) {
                file.displayName = f.displayName
                if file.type == .image, f.isImage == false { file.type = .file }
                attachments.append(file)
            }
        }
        if let s = item.snippet, !s.isEmpty {
            attachments.append(ReminderAttachment(id: UUID().uuidString.lowercased(), type: .textSnippet, mimeType: "text/plain",
                                                  text: s, sourceTitle: item.snippetTitle, source: item.snippetSource))
        }
        func limitPhotos(_ list: [ReminderAttachment], existing: Int) -> [ReminderAttachment] {
            var room = max(0, 3 - existing)
            return list.map { a in
                var a = a
                if a.type == .image { if room > 0 { room -= 1 } else { a.type = .file } }
                return a
            }
        }
        switch item.destination {
        case let .newCard(title, categoryId, plannedDay):
            let fallback = item.music.first.map { [$0.title, $0.artist].filter { !$0.isEmpty }.joined(separator: " – ") }
                ?? item.url ?? item.files.first?.displayName ?? "Paylaşılan içerik"
            let text = title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? fallback : title
            let category = categoryId.flatMap { id in store.categories.contains { $0.id == id } ? id : nil }
            _ = store.addReminder(text: String(text.prefix(2000)), plannedDay: plannedDay, categoryId: category) { r in
                r.attachments = limitPhotos(attachments, existing: 0)
                r.voiceRecordings = voices
                r.referenceUrl = item.url
                r.musicBlocks = item.music
                r.sendImmediateNotification = false
            }
            return true
        case let .existingCard(id, replaceLink):
            guard store.reminder(id) != nil else { return false }
            store.mutate(id) { r in
                r.attachments += limitPhotos(attachments, existing: r.attachments.filter { $0.type == .image }.count)
                r.voiceRecordings += voices
                if let link = item.url {
                    if r.referenceUrl == nil || r.referenceUrl == link || replaceLink { r.referenceUrl = link }
                    else { r.attachments.append(ReminderAttachment(id: UUID().uuidString.lowercased(), type: .textSnippet, mimeType: "text/plain", text: link)) }
                }
                r.musicBlocks += item.music
                r.updatedAt = .now
            }
            return true
        }
    }

    /// m4a / aac audio becomes a voice recording (like Android); other audio stays a file.
    private static func voiceRecording(_ url: URL) -> ReminderVoiceRecording? {
        guard ["m4a", "aac", "mp4"].contains(url.pathExtension.lowercased()) else { return nil }
        let id = UUID().uuidString.lowercased()
        let name = "voice_\(id).m4a"
        let target = AttachmentStorage.directory.appendingPathComponent(name)
        guard (try? FileManager.default.copyItem(at: url, to: target)) != nil else { return nil }
        let seconds = CMTimeGetSeconds(AVURLAsset(url: target).duration)
        guard seconds.isFinite, seconds > 0 else { try? FileManager.default.removeItem(at: target); return nil }
        return ReminderVoiceRecording(id: id, localPath: name, durationMillis: Int64(seconds * 1000), createdAt: .now)
    }

    /// The light list the extension shows under "Karta ekle".
    static func writeSnapshot(_ store: ReminderStore) {
        let cards = store.reminders.filter { $0.mood == nil }.prefix(400).map {
            ShareInbox.CardSummary(id: $0.id, text: String($0.text.prefix(200)), completed: $0.isCompleted, updatedAt: $0.updatedAt,
                                   hasLink: $0.referenceUrl != nil, link: $0.referenceUrl,
                                   photoCount: $0.attachments.filter { $0.type == .image }.count)
        }
        let categories = store.categories.filter { !$0.isSharedMirror || $0.sharedRole == "full" }.map { c in
            ShareInbox.CategorySummary(id: c.id, name: c.name, colorArgb: c.customSolidColorArgb ?? argb(reminderColorChoices.first { $0.index == c.accentStart }?.color))
        }
        ShareInbox.writeSnapshot(.init(cards: Array(cards), categories: categories))
    }

    private static func argb(_ color: Color?) -> Int? {
        guard let color else { return nil }
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        guard UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a) else { return nil }
        let rgb = (UInt32(max(0, min(255, r * 255))) << 16) | (UInt32(max(0, min(255, g * 255))) << 8) | UInt32(max(0, min(255, b * 255)))
        return Int(rgb)
    }
}
