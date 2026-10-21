import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Share → REAL (AttachmentImportActivity.kt on Android). A sheet over the app you shared from: make a new card or add
/// to an existing card. The share is left in the App Group inbox; REAL puts it on the card when it opens.
final class ShareViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        let model = ShareModel(context: extensionContext)
        let host = UIHostingController(rootView: ShareSheetView(model: model))
        host.view.backgroundColor = .clear
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)
        Task { await model.load() }
    }
}

private extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, opacity: alpha)
    }
}

@MainActor
@Observable
final class ShareModel {
    enum Tab: String, CaseIterable { case new = "Yeni kart", existing = "Karta ekle" }

    let context: NSExtensionContext?
    let itemId = UUID().uuidString.lowercased()
    var loading = true
    var resolving = false
    var error: String?
    var done: String?
    var snapshot = ShareInbox.snapshot

    var snippet: String?
    var snippetSource = SnippetSourceDetector.plain
    var url: String?
    var previewTitle: String?
    var previewImage: String?
    var music: [MusicBlock] = []
    var files: [ShareInbox.File] = []
    var images: [UIImage] = []
    var suggestedTitle = ""

    init(context: NSExtensionContext?) { self.context = context }

    private var folder: URL? { ShareInbox.newItemFolder(itemId) }

    func load() async {
        defer { loading = false }
        guard ShareInbox.container != nil else { error = "REAL'in paylaşım alanına ulaşılamadı."; return }
        let items = (context?.inputItems as? [NSExtensionItem]) ?? []
        let subject = items.first?.attributedContentText?.string
        var text: String?
        for item in items {
            for provider in item.attachments ?? [] {
                if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier), let u = await loadURL(provider) {
                    if u.isFileURL { await copyFile(u) } else if text == nil { text = u.absoluteString }
                } else if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
                    await copyImage(provider)
                } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier), let s = await loadString(provider) {
                    text = text.map { $0 + "\n" + s } ?? s
                } else if provider.hasItemConformingToTypeIdentifier(UTType.data.identifier) {
                    await copyData(provider)
                }
            }
        }
        if let text {
            let parsed = SmartText.parse(text, subject: subject == text ? nil : subject)
            url = parsed.url
            snippet = parsed.snippet
            suggestedTitle = parsed.title ?? ""
            snippetSource = SnippetSourceDetector.detect(text: text)
        } else if suggestedTitle.isEmpty, let subject { suggestedTitle = String(subject.prefix(200)) }
        if url == nil && snippet == nil && files.isEmpty { error = "Okunabilir içerik bulunamadı." }
        if let link = url { await resolve(link) }
    }

    private func resolve(_ link: String) async {
        resolving = true
        let found = await LinkResolver.resolve(link)
        resolving = false
        if let m = found.music {
            music = [m]; url = nil
            if suggestedTitle.isEmpty { suggestedTitle = [m.title, m.artist].filter { !$0.isEmpty }.joined(separator: " – ") }
        }
        previewTitle = found.title; previewImage = found.imageUrl
        if suggestedTitle.isEmpty, let t = found.title { suggestedTitle = t }
    }

    // MARK: Files into the item's folder

    private func copyFile(_ u: URL) async {
        guard let folder else { return }
        let access = u.startAccessingSecurityScopedResource()
        defer { if access { u.stopAccessingSecurityScopedResource() } }
        let name = UUID().uuidString.lowercased() + "-" + u.lastPathComponent
        guard (try? FileManager.default.copyItem(at: u, to: folder.appendingPathComponent(name))) != nil else { return }
        let type = UTType(filenameExtension: u.pathExtension)
        let isImage = type?.conforms(to: .image) ?? false
        if isImage, let img = UIImage(contentsOfFile: folder.appendingPathComponent(name).path) { images.append(img) }
        files.append(.init(name: name, displayName: u.lastPathComponent, mime: type?.preferredMIMEType, isImage: isImage,
                           isAudio: type?.conforms(to: .audio) ?? false))
    }

    private func copyImage(_ p: NSItemProvider) async {
        guard let folder, let data = await loadData(p, .image) else { return }
        let name = UUID().uuidString.lowercased() + ".jpg"
        guard (try? data.write(to: folder.appendingPathComponent(name))) != nil else { return }
        if let img = UIImage(data: data) { images.append(img) }
        files.append(.init(name: name, displayName: "Fotoğraf", mime: "image/jpeg", isImage: true, isAudio: false))
    }

    private func copyData(_ p: NSItemProvider) async {
        guard let folder else { return }
        let type = p.registeredContentTypes.first ?? .data
        guard let data = await loadData(p, type) else { return }
        let ext = type.preferredFilenameExtension ?? "dat"
        let name = UUID().uuidString.lowercased() + "." + ext
        guard (try? data.write(to: folder.appendingPathComponent(name))) != nil else { return }
        files.append(.init(name: name, displayName: p.suggestedName ?? "Dosya", mime: type.preferredMIMEType,
                           isImage: false, isAudio: type.conforms(to: .audio)))
    }

    private func loadURL(_ p: NSItemProvider) async -> URL? {
        await withCheckedContinuation { c in _ = p.loadObject(ofClass: NSURL.self) { o, _ in c.resume(returning: (o as? NSURL) as URL?) } }
    }
    private func loadString(_ p: NSItemProvider) async -> String? {
        await withCheckedContinuation { c in _ = p.loadObject(ofClass: NSString.self) { o, _ in c.resume(returning: (o as? NSString) as String?) } }
    }
    private func loadData(_ p: NSItemProvider, _ t: UTType) async -> Data? {
        await withCheckedContinuation { c in _ = p.loadDataRepresentation(forTypeIdentifier: t.identifier) { d, _ in c.resume(returning: d) } }
    }

    // MARK: Finish

    func save(_ destination: ShareInbox.Destination) {
        let item = ShareInbox.Item(id: itemId, destination: destination, snippet: snippet, snippetSource: snippet == nil ? nil : snippetSource,
                                   url: url, music: music, files: files)
        guard ShareInbox.commit(item) else { error = "Kaydedilemedi. Yeniden dene."; return }
        if case .existingCard = destination { done = "Karta eklendi" } else { done = "Yeni kart eklendi" }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) { [context] in context?.completeRequest(returningItems: nil) }
    }

    func cancel() {
        ShareInbox.discard(itemId)
        context?.cancelRequest(withError: NSError(domain: "REAL", code: 0))
    }
}

struct ShareSheetView: View {
    @Bindable var model: ShareModel
    @State private var tab = ShareModel.Tab.new
    @State private var title = ""
    @State private var titleEdited = false
    @State private var categoryId: Int64?
    @State private var plannedOffset: Int?
    @State private var selectedCard: Int64?
    @State private var replaceLink = false
    @State private var query = ""

    private let ink = Color(hex: 0xF2F2F5), muted = Color(hex: 0x9C9CA6), field = Color.white.opacity(0.06)

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture { if model.done == nil { model.cancel() } }
            VStack(alignment: .leading, spacing: 12) {
                Capsule().fill(.white.opacity(0.22)).frame(width: 38, height: 4).frame(maxWidth: .infinity).padding(.top, 10)
                if let done = model.done {
                    VStack(spacing: 12) {
                        Image(systemName: "checkmark").font(.system(size: 22, weight: .semibold)).foregroundStyle(ink)
                            .frame(width: 52, height: 52).background(Circle().fill(.white.opacity(0.1)))
                        Text(done).font(.system(size: 18, weight: .semibold)).foregroundStyle(ink)
                        Text("REAL'i açınca kartında olacak.").font(.system(size: 13)).foregroundStyle(muted)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 20)
                } else {
                    content
                }
            }
            .padding(.horizontal, 20).padding(.bottom, 16)
            .background(UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32).fill(Color(hex: 0x141416)).ignoresSafeArea())
        }
        .preferredColorScheme(.dark)
        .onChange(of: model.suggestedTitle, initial: true) { _, t in if !titleEdited { title = t } }
    }

    @ViewBuilder private var content: some View {
        HStack {
            Text("REAL'e ekle").font(.system(size: 22, weight: .semibold)).foregroundStyle(ink)
            Spacer()
            Button("İptal") { model.cancel() }.foregroundStyle(muted)
        }
        if model.loading {
            HStack(spacing: 10) { ProgressView().tint(muted); Text("İçerik hazırlanıyor…").foregroundStyle(muted) }.padding(.vertical, 20)
        } else {
            preview
            if let e = model.error { Text(e).font(.system(size: 13)).foregroundStyle(Color(hex: 0xFFB4AB)) }
            Picker("", selection: $tab) { ForEach(ShareModel.Tab.allCases, id: \.self) { Text($0.rawValue).tag($0) } }
                .pickerStyle(.segmented)
            if tab == .new { newCard } else { existingCard }
            let canSave = tab == .new || selectedCard != nil
            Button {
                if tab == .new {
                    let day = plannedOffset.map { Int64(Calendar.current.startOfDay(for: Date().addingTimeInterval(Double($0) * 86_400)).timeIntervalSince1970 / 86_400) }
                    model.save(.newCard(title: title, categoryId: categoryId, plannedDay: day))
                } else if let id = selectedCard { model.save(.existingCard(id: id, replaceLink: replaceLink)) }
            } label: {
                Text(tab == .new ? "Kart oluştur" : "Karta ekle").font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.black).frame(maxWidth: .infinity, minHeight: 52)
                    .background(Capsule().fill(canSave ? Color.white : Color.white.opacity(0.25)))
            }
            .disabled(!canSave || model.error != nil && model.files.isEmpty && model.snippet == nil && model.url == nil && model.music.isEmpty)
        }
    }

    @ViewBuilder private var preview: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !model.images.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(model.images.indices, id: \.self) { i in
                            Image(uiImage: model.images[i]).resizable().scaledToFill().frame(width: 72, height: 72).clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                    }
                }
            }
            ForEach(model.music, id: \.id) { m in row(m.artworkUrl, m.title, [m.artist, m.provider == "iTunes" ? "Apple Music" : m.provider].filter { !$0.isEmpty }.joined(separator: " · ")) }
            if let link = model.url {
                let host = URL(string: link)?.host?.replacingOccurrences(of: "www.", with: "") ?? link
                row(model.previewImage, model.previewTitle ?? (model.resolving ? "Önizleme yükleniyor…" : host), host)
            }
            if let s = model.snippet {
                Text(s).font(.system(size: 14)).foregroundStyle(ink.opacity(0.85)).lineLimit(3)
                    .padding(12).frame(maxWidth: .infinity, alignment: .leading).background(RoundedRectangle(cornerRadius: 16).fill(field))
            }
            let others = model.files.filter { !$0.isImage }
            if !others.isEmpty { Text(others.map(\.displayName).joined(separator: " · ")).font(.system(size: 13)).foregroundStyle(muted).lineLimit(2) }
        }
    }

    private func row(_ image: String?, _ title: String, _ subtitle: String) -> some View {
        HStack(spacing: 12) {
            AsyncImage(url: image.flatMap(URL.init(string:))) { p in
                if let i = p.image { i.resizable().scaledToFill() } else { Color.white.opacity(0.06) }
            }
            .frame(width: 48, height: 48).clipShape(RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 14, weight: .medium)).foregroundStyle(ink).lineLimit(2)
                Text(subtitle).font(.system(size: 12)).foregroundStyle(muted).lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(10).background(RoundedRectangle(cornerRadius: 16).fill(field))
    }

    private var newCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            TextField("Kart başlığı", text: Binding(get: { title }, set: { title = $0; titleEdited = true }), axis: .vertical)
                .lineLimit(1...3).foregroundStyle(ink)
                .padding(14).background(RoundedRectangle(cornerRadius: 18).fill(field))
            if !model.snapshot.categories.isEmpty {
                Text("Kategori").font(.system(size: 13)).foregroundStyle(muted)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        chip("Kategorisiz", on: categoryId == nil) { categoryId = nil }
                        ForEach(model.snapshot.categories) { c in
                            chip(c.name, on: categoryId == c.id, dot: c.colorArgb.map { Color(hex: UInt32(truncatingIfNeeded: $0)) }) { categoryId = c.id }
                        }
                    }
                }
            }
            Text("Tarih").font(.system(size: 13)).foregroundStyle(muted)
            HStack(spacing: 8) {
                chip("Tarih yok", on: plannedOffset == nil) { plannedOffset = nil }
                chip("Bugün", on: plannedOffset == 0) { plannedOffset = 0 }
                chip("Yarın", on: plannedOffset == 1) { plannedOffset = 1 }
            }
        }
    }

    private var existingCard: some View {
        let cards = model.snapshot.cards.filter { query.isEmpty || $0.text.localizedCaseInsensitiveContains(query) }
            .sorted { $0.updatedAt > $1.updatedAt }
        let selected = model.snapshot.cards.first { $0.id == selectedCard }
        return VStack(alignment: .leading, spacing: 8) {
            TextField("Kart ara", text: $query).foregroundStyle(ink).padding(12).background(RoundedRectangle(cornerRadius: 18).fill(field))
            if model.snapshot.cards.isEmpty { Text("REAL'i bir kez açınca kartların burada görünür.").font(.system(size: 13)).foregroundStyle(muted) }
            ScrollView {
                LazyVStack(spacing: 6) {
                    ForEach(cards.filter { !$0.completed } + cards.filter(\.completed)) { c in
                        Button { selectedCard = c.id } label: {
                            HStack {
                                Text(c.text.isEmpty ? "Boş not" : c.text).font(.system(size: 15)).foregroundStyle(c.completed ? muted : ink).lineLimit(2)
                                Spacer()
                                if selectedCard == c.id { Image(systemName: "checkmark").foregroundStyle(ink) }
                            }
                            .padding(.horizontal, 14).padding(.vertical, 12)
                            .background(RoundedRectangle(cornerRadius: 18).fill(field))
                            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(selectedCard == c.id ? Color.white : .clear, lineWidth: 1.2))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .frame(maxHeight: 300)
            if let selected, selected.hasLink, model.url != nil, selected.link != model.url {
                Toggle("Kartın mevcut bağlantısını değiştir", isOn: $replaceLink).foregroundStyle(ink).font(.system(size: 14))
            }
        }
    }

    private func chip(_ label: String, on: Bool, dot: Color? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 7) {
                if let dot { Circle().fill(dot).frame(width: 9, height: 9) }
                Text(label).font(.system(size: 13, weight: on ? .semibold : .regular)).foregroundStyle(on ? .black : ink.opacity(0.85)).lineLimit(1)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(Capsule().fill(on ? Color.white : field))
        }
        .buttonStyle(.plain)
    }
}

private extension ShareInbox {
    static var snapshot: Snapshot { readSnapshot() }
}
