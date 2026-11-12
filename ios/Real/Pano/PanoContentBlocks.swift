import SwiftUI
import MapKit
import ImageIO
import UniformTypeIdentifiers

// REAL content on a board (BoardCanonicalBlock.kt / BoardRealContent.kt): music, film, mood, voice, file, link,
// location and GIF blocks. The payload is the card model's own JSON, so the card's own views draw it.

// MARK: Reading a block's payload

extension PanoItem {
    var payloadObject: [String: Any] {
        rawPayload.flatMap { $0.data(using: .utf8) }.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:]
    }

    private func model<T: Decodable>(_ key: String, as type: T.Type) -> T? {
        guard let object = payloadObject[key] as? [String: Any],
              let data = try? JSONSerialization.data(withJSONObject: object) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    var boardMusic: MusicBlock? { model("music", as: MusicBlock.self) }
    var boardMedia: MediaBlock? { model("media", as: MediaBlock.self) }
    var boardMood: JournalMood? { (payloadObject["mood"] as? String).flatMap { JournalMood(rawValue: $0) } }
    var boardMoodDetails: MoodDetails? { model("moodDetails", as: MoodDetails.self) }
    var boardLink: String? { payloadObject["referenceUrl"] as? String }

    /// The file sits in the attachments folder once fetched (photoPath); empty until then.
    var boardAttachment: ReminderAttachment? {
        guard let a = payloadObject["attachment"] as? [String: Any], let id = a["id"] as? String else { return nil }
        let type = AttachmentType(rawValue: (a["type"] as? String) ?? "") ?? .file
        return ReminderAttachment(id: id, localPath: photoPath ?? "", type: type, mimeType: a["mimeType"] as? String,
                                  displayName: a["displayName"] as? String, sizeBytes: (a["sizeBytes"] as? NSNumber)?.int64Value,
                                  text: a["text"] as? String, sourceTitle: a["sourceTitle"] as? String, source: a["source"] as? String)
    }

    var boardVoice: ReminderVoiceRecording? {
        guard let v = payloadObject["voice"] as? [String: Any], let id = v["id"] as? String else { return nil }
        return ReminderVoiceRecording(id: id, localPath: photoPath ?? "",
                                      durationMillis: (v["durationMillis"] as? NSNumber)?.int64Value ?? 0,
                                      createdAt: (v["createdAt"] as? NSNumber)?.int64Value ?? 0)
    }

    var boardLocation: ReminderLocation? {
        let p = payloadObject
        guard let name = p["placeName"] as? String, !name.isEmpty else { return nil }
        return ReminderLocation(placeName: name, address: (p["address"] as? String) ?? "",
                                latitude: (p["latitude"] as? NSNumber)?.doubleValue, longitude: (p["longitude"] as? NSNumber)?.doubleValue,
                                mapsUrl: p["mapsUrl"] as? String)
    }
}

// MARK: Building a payload (the worker checks every field: boards/content.mjs, boards/model.mjs)

/// What a picker hands back: the payload, the block's first size and, for a file or recording, its local file.
struct PanoNewContent {
    var type: BoardBlockType
    var payload: [String: Any]
    var width: Double
    var height: Double
    var file: String? = nil
    var assetId: String? = nil
    var mime: String? = nil
}

enum PanoPayload {
    static func json(_ object: [String: Any]) -> String? {
        (try? JSONSerialization.data(withJSONObject: object)).flatMap { String(data: $0, encoding: .utf8) }
    }

    private static func object<T: Encodable>(_ value: T) -> [String: Any] {
        ((try? JSONEncoder().encode(value)).flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }) ?? [:]
    }

    static func music(_ block: MusicBlock) -> PanoNewContent? {
        guard URL(string: block.providerUrl)?.scheme?.hasPrefix("http") == true else { return nil }
        var m = object(block)
        m["explicit"] = nil
        return PanoNewContent(type: .music, payload: ["music": m], width: 320, height: 150)
    }

    static func media(_ block: MediaBlock) -> PanoNewContent? {
        guard URL(string: block.providerUrl)?.scheme?.hasPrefix("http") == true else { return nil }
        return PanoNewContent(type: .media, payload: ["media": object(block)], width: 320, height: 200)
    }

    static func mood(_ mood: JournalMood, _ details: MoodDetails) -> PanoNewContent {
        let d: [String: Any] = ["scope": details.scope.rawValue, "descriptors": details.descriptors,
                                "energy": details.energy.map { $0.rawValue as Any } ?? NSNull(),
                                "factors": details.factors, "loggedAt": NSNumber(value: details.loggedAt)]
        return PanoNewContent(type: .mood, payload: ["mood": mood.rawValue, "moodDetails": d], width: 220, height: 200)
    }

    static func location(_ place: ReminderLocation) -> PanoNewContent {
        let maps = place.mapsUrl.flatMap { $0.hasPrefix("https://") ? $0 : nil }
        let p: [String: Any] = ["placeName": place.placeName, "address": place.address,
                                "latitude": place.latitude.map { $0 as Any } ?? NSNull(),
                                "longitude": place.longitude.map { $0 as Any } ?? NSNull(),
                                "mapsUrl": maps.map { $0 as Any } ?? NSNull(), "approximateCoordinates": false]
        return PanoNewContent(type: .location, payload: p, width: 300, height: place.latitude == nil ? 96 : 280)
    }

    /// A web address becomes a link block; anything else a text snippet (IncomingContentParser on Android).
    static func linkOrSnippet(_ text: String) -> PanoNewContent? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let candidate = trimmed.contains("://") ? trimmed : "https://" + trimmed
        if !trimmed.contains(where: \.isWhitespace), trimmed.contains("."), let url = URL(string: candidate),
           let scheme = url.scheme, ["http", "https"].contains(scheme), url.host != nil {
            return PanoNewContent(type: .link, payload: ["referenceUrl": candidate], width: 300, height: 96)
        }
        let a: [String: Any] = ["id": panoId(), "type": "TEXT_SNIPPET", "text": String(trimmed.prefix(60000)), "source": "PLAIN_TEXT"]
        return PanoNewContent(type: .link, payload: ["attachment": a], width: 300, height: 180)
    }

    static func voice(_ recording: ReminderVoiceRecording) -> PanoNewContent {
        let id = panoId()
        let v: [String: Any] = ["id": id, "durationMillis": NSNumber(value: recording.durationMillis), "createdAt": NSNumber(value: recording.createdAt)]
        return PanoNewContent(type: .voice, payload: ["assetId": id, "mime": "audio/mp4", "voice": v], width: 300, height: 120,
                              file: recording.localPath, assetId: id, mime: "audio/mp4")
    }

    /// A picked file. Pictures go up as JPEG; any other file as it is (the worker stores those as octet-stream).
    static func file(_ url: URL) -> PanoNewContent? {
        guard let saved = AttachmentStorage.saveFile(url) else { return nil }
        var a = saved
        if saved.type == .image,
           let data = try? Data(contentsOf: AttachmentStorage.directory.appendingPathComponent(saved.localPath)),
           let jpeg = AttachmentStorage.save(data) {
            AttachmentStorage.remove(saved)
            a = jpeg
            a.displayName = saved.displayName
        }
        let size = a.sizeBytes ?? 0
        guard size > 0, size <= 10 * 1024 * 1024 else { AttachmentStorage.remove(a); return nil }
        let id = panoId()
        let image = a.type == .image
        let mime = image ? "image/jpeg" : "application/octet-stream"
        let declared = image ? "image/jpeg"
            : a.mimeType.flatMap { $0.range(of: "^[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+$", options: .regularExpression) != nil ? $0 : nil }
        let model: [String: Any] = ["id": id, "type": image ? "IMAGE" : "FILE", "mimeType": declared.map { $0 as Any } ?? NSNull(),
                                    "displayName": String((a.displayName ?? "File").prefix(500)), "sizeBytes": NSNumber(value: size)]
        return PanoNewContent(type: .attachment, payload: ["assetId": id, "mime": mime, "attachment": model],
                              width: image ? 260 : 300, height: image ? 260 : 96, file: a.localPath, assetId: id, mime: mime)
    }
}

// MARK: Drawing

struct PanoContentBlock: View {
    let item: PanoItem

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    @ViewBuilder
    private var content: some View {
        switch item.type {
        case .music:
            if let block = item.boardMusic { MusicBlockView(block: block) } else { PanoNote(text: "Music") }
        case .media:
            if let block = item.boardMedia { MediaBlockView(block: block) } else { PanoNote(text: "Movie / TV") }
        case .mood:
            if let mood = item.boardMood { PanoMoodBlock(mood: mood, details: item.boardMoodDetails) } else { PanoNote(text: "Mood") }
        case .voice:
            if let voice = item.boardVoice, !voice.localPath.isEmpty {
                VoiceRecordingBlock(recordings: [voice], editing: false).padding(12).panoBox()
            } else { PanoNote(text: "Loading voice…") }
        case .location:
            if let place = item.boardLocation { PanoLocationBlock(place: place) } else { PanoNote(text: "Location") }
        case .gif:
            if let path = item.photoPath { PanoGIFView(path: path) } else { PanoNote(text: "Loading image…") }
        case .attachment, .link:
            PanoAttachmentBlock(item: item)
        default:
            EmptyView()
        }
    }
}

private extension View {
    func panoBox() -> some View {
        frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Color(argb: 0xFF1A1A1E)))
    }
}

private struct PanoNote: View {
    let text: String
    var body: some View {
        Text(text).font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.5)).padding(16).panoBox()
    }
}

private struct PanoMoodBlock: View {
    let mood: JournalMood
    let details: MoodDetails?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            MoodFace(mood: mood).frame(width: 64, height: 64)
            Text(mood.label).font(RealFont.inter(17, .medium)).foregroundStyle(.white)
            if let details {
                Text(details.scope.label).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.5))
                if !details.descriptors.isEmpty {
                    Text(details.descriptors.map { MoodV2.descriptorLabel($0) }.joined(separator: ", "))
                        .font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.75)).lineLimit(2)
                }
                if !details.factors.isEmpty {
                    Text(details.factors.map { MoodHistoryScreen.factorLabel($0) }.joined(separator: ", "))
                        .font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.75)).lineLimit(2)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .panoBox()
    }
}

private struct PanoLocationBlock: View {
    let place: ReminderLocation

    private var coordinate: CLLocationCoordinate2D? {
        guard let lat = place.latitude, let lng = place.longitude else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text("⌖ " + place.placeName).font(RealFont.inter(17)).foregroundStyle(.white).lineLimit(2)
                if !place.address.isEmpty {
                    Text(place.address).font(RealFont.inter(12)).foregroundStyle(PanoColors.muted).lineLimit(2)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color(argb: 0xFF1A1A1E)))
            if let coordinate {
                Map(initialPosition: .region(MKCoordinateRegion(center: coordinate, latitudinalMeters: 900, longitudinalMeters: 900)),
                    interactionModes: []) {
                    Marker(place.placeName, coordinate: coordinate)
                }
                .allowsHitTesting(false)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                Button(action: openMap) {
                    Text("Open map").font(RealFont.inter(13, .medium)).foregroundStyle(.white)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(Capsule().fill(Color.white.opacity(0.1)))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func openMap() {
        guard let coordinate else { return }
        let mapItem = MKMapItem(placemark: MKPlacemark(coordinate: coordinate))
        mapItem.name = place.placeName
        mapItem.openInMaps()
    }
}

/// A file, a picture, a text snippet or a link.
private struct PanoAttachmentBlock: View {
    let item: PanoItem
    @Environment(\.openURL) private var openURL

    var body: some View {
        if let link = item.boardLink {
            Button { if let url = URL(string: link) { openURL(url) } } label: {
                row(symbol: "link", title: URL(string: link)?.host ?? link, subtitle: link)
            }
            .buttonStyle(.plain)
        } else if let a = item.boardAttachment {
            switch a.type {
            case .textSnippet:
                SnippetBlockView(attachment: a, onSave: { _ in })
            case .image:
                if let image = AttachmentStorage.image(a.localPath) {
                    Image(uiImage: image).resizable().scaledToFill()
                        .frame(maxWidth: .infinity, maxHeight: .infinity).clipped()
                } else { PanoNote(text: "Loading image…") }
            case .file:
                if a.localPath.isEmpty {
                    PanoNote(text: "Loading file…")
                } else {
                    ShareLink(item: PanoFiles.shareURL(a)) {
                        row(symbol: "doc", title: a.displayName ?? "File",
                            subtitle: a.sizeBytes.map { ByteCountFormatter.string(fromByteCount: $0, countStyle: .file) } ?? "")
                    }
                    .buttonStyle(.plain)
                }
            }
        } else {
            PanoNote(text: "File")
        }
    }

    private func row(symbol: String, title: String, subtitle: String) -> some View {
        HStack(spacing: 12) {
            RealSymbol(symbol, size: 20.7).foregroundStyle(RealColors.accentBlue)
                .frame(width: 40, height: 40).background(RoundedRectangle(cornerRadius: 12).fill(.white.opacity(0.06)))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(RealFont.inter(14, .medium)).foregroundStyle(.white).lineLimit(1)
                if !subtitle.isEmpty { Text(subtitle).font(RealFont.inter(12)).foregroundStyle(RealColors.subtext).lineLimit(1) }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .panoBox()
        .contentShape(Rectangle())
    }
}

enum PanoFiles {
    /// A fetched board file is stored without its name; sharing hands out a copy that carries it.
    static func shareURL(_ a: ReminderAttachment) -> URL {
        let source = AttachmentStorage.directory.appendingPathComponent(a.localPath)
        guard let name = a.displayName, !name.isEmpty, source.lastPathComponent != name else { return source }
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("pano-\(a.id)")
        let target = folder.appendingPathComponent(name)
        if !FileManager.default.fileExists(atPath: target.path) {
            try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            try? FileManager.default.copyItem(at: source, to: target)
        }
        return FileManager.default.fileExists(atPath: target.path) ? target : source
    }

    /// Keeps a picked GIF as it is (photos are re-encoded as JPEG, which would freeze it).
    static func saveGIF(_ data: Data) -> String? {
        guard data.count <= 10 * 1024 * 1024 else { return nil }
        let name = "\(UUID().uuidString).gif"
        do { try data.write(to: AttachmentStorage.directory.appendingPathComponent(name)) } catch { return nil }
        return name
    }

    static func isGIF(_ data: Data) -> Bool { data.prefix(3) == Data([0x47, 0x49, 0x46]) }
}

/// An animated GIF (at most about sixty frames are kept in memory).
struct PanoGIFView: UIViewRepresentable {
    let path: String

    func makeUIView(context: Context) -> UIImageView {
        let view = UIImageView()
        view.contentMode = .scaleAspectFill
        view.clipsToBounds = true
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        view.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        view.setContentHuggingPriority(.defaultLow, for: .horizontal)
        view.setContentHuggingPriority(.defaultLow, for: .vertical)
        view.image = Self.animated(path)
        return view
    }

    func updateUIView(_ view: UIImageView, context: Context) {}

    static func animated(_ path: String) -> UIImage? {
        let url = AttachmentStorage.directory.appendingPathComponent(path)
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let count = CGImageSourceGetCount(source)
        guard count > 1 else { return UIImage(contentsOfFile: url.path) }
        let step = max(1, count / 60)
        var frames: [UIImage] = []
        var duration = 0.0
        for index in stride(from: 0, to: count, by: step) {
            guard let frame = CGImageSourceCreateImageAtIndex(source, index, nil) else { continue }
            frames.append(UIImage(cgImage: frame))
            let properties = CGImageSourceCopyPropertiesAtIndex(source, index, nil) as? [CFString: Any]
            let gif = properties?[kCGImagePropertyGIFDictionary] as? [CFString: Any]
            let delay = (gif?[kCGImagePropertyGIFUnclampedDelayTime] as? Double) ?? (gif?[kCGImagePropertyGIFDelayTime] as? Double) ?? 0.1
            duration += max(delay, 0.02) * Double(step)
        }
        return UIImage.animatedImage(with: frames, duration: duration)
    }
}

// MARK: Adding (PanoContentFlow.kt: the pickers are REAL's own)

struct PanoContentFlow: View {
    let type: BoardBlockType
    let onDone: (PanoNewContent) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        switch type {
        case .music:
            MusicSearchSheet { block in if let c = PanoPayload.music(block) { onDone(c) } }
        case .media:
            MediaSearchSheet { block in if let c = PanoPayload.media(block) { onDone(c) } }
        case .mood:
            MoodAddSheet(onSaved: { mood, details in onDone(PanoPayload.mood(mood, details)) })
        case .location:
            LocationEditor(initial: nil) { place in if let place { onDone(PanoPayload.location(place)) } }
        case .voice:
            VoiceRecordingBlock(recordings: [], editing: true, onRecorded: { onDone(PanoPayload.voice($0)) })
                .padding(20)
                .presentationDetents([.height(240)])
                .presentationBackground(.ultraThinMaterial)
        default:
            linkForm
        }
    }

    private var linkForm: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Link / text snippet").font(RealFont.inter(17, .semibold)).foregroundStyle(.white)
            TextField("", text: $text, prompt: Text("https://… or a note").foregroundStyle(Color(argb: 0xFF6F6F78)), axis: .vertical)
                .font(RealFont.inter(16)).foregroundStyle(.white).tint(RealColors.accentBlue)
                .lineLimit(1...6)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
                .focused($focused)
                .padding(14)
                .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color(argb: 0xFF16161A)))
            HStack {
                Button(t("Vazgeç")) { dismiss() }.font(RealFont.inter(16)).foregroundStyle(RealColors.accentBlue)
                Spacer()
                Button {
                    if let c = PanoPayload.linkOrSnippet(text) { onDone(c) }
                } label: {
                    Text("Add").font(RealFont.inter(16, .semibold)).foregroundStyle(.white)
                        .padding(.horizontal, 18).frame(height: 40)
                        .background(Capsule().fill(Color(argb: 0xFF2C5696)))
                }
                .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .presentationDetents([.height(280)])
        .presentationBackground(.ultraThinMaterial)
        .onAppear { focused = true }
    }
}
