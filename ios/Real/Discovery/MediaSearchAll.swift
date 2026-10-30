import SwiftUI

/// MediaSearchAll (ComposerMediaSearch.kt): one box for songs, films, series and games. Picked items stay on top;
/// results fill in under the box while typing.
struct MediaSearchAll: View {
    var music: [MusicBlock]
    var media: [MediaBlock]
    var games: [CardGameBlock]
    var onAddMusic: (MusicBlock) -> Void
    var onAddMedia: (MediaBlock) -> Void
    var onAddGame: ((CardGameBlock) -> Void)?
    var onRemoveMusic: (MusicBlock) -> Void
    var onRemoveMedia: (MediaBlock) -> Void
    var onRemoveGame: (CardGameBlock) -> Void
    @State private var query = ""
    @State private var songs: [MusicBlock] = []
    @State private var titles: [MediaBlock] = []
    @State private var gameResults: [CardGameBlock] = []
    @State private var searching = false
    @State private var message: String?
    @FocusState private var focused: Bool

    private let block = RoundedRectangle(cornerRadius: 18, style: .continuous)

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 12) {
                    RealSymbol("music.note", size: 17).foregroundStyle(Color(argb: 0xFFBABAC3))
                    TextField("", text: $query, prompt: Text(onAddGame != nil ? t("Şarkı, film, dizi veya oyun ara") : t("Şarkı, film veya dizi ara"))
                        .foregroundStyle(Color(argb: 0xFF7C7C86)))
                        .font(RealFont.inter(15)).foregroundStyle(.white).tint(.white)
                        .focused($focused).submitLabel(.search).onSubmit { focused = false }
                    if searching { Text(t("Aranıyor…")).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8E8E98)) }
                }
                .padding(.horizontal, 14).padding(.vertical, 13)
                .background(block.fill(Color.white.opacity(0.055)))

                if !music.isEmpty || !media.isEmpty || !games.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(music) { m in picked(m.title, m.artist) { onRemoveMusic(m) } }
                        ForEach(media) { m in
                            picked(m.title, [m.year.map(String.init), m.type == .MOVIE ? t("Film") : t("Dizi")].compactMap { $0 }.joined(separator: " • ")) { onRemoveMedia(m) }
                        }
                        ForEach(games) { g in
                            picked(g.title, [g.year.map(String.init), t("Oyun")].compactMap { $0 }.joined(separator: " • ")) { onRemoveGame(g) }
                        }
                    }
                    .background(block.fill(Color.white.opacity(0.055)))
                }
                if let message { Text(message).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF9A9AA4)).padding(.horizontal, 6) }
                if !songs.isEmpty {
                    section(t("Müzik"))
                    VStack(spacing: 0) {
                        ForEach(songs) { m in
                            resultRow(m.title, m.artist, add: { onAddMusic(m); query = "" }) {
                                MusicArtwork(url: m.artworkUrl).frame(width: 44, height: 44)
                            }
                        }
                    }
                    .padding(4).background(block.fill(Color.white.opacity(0.055)))
                }
                if !titles.isEmpty {
                    section(t("Film & Dizi"))
                    VStack(spacing: 0) {
                        ForEach(titles) { m in
                            resultRow(m.title, [m.year.map(String.init), m.type == .MOVIE ? "Film" : "Dizi", m.genre].compactMap { $0 }.joined(separator: " • "),
                                      add: { onAddMedia(m); query = "" }) {
                                PosterArtwork(media: m, titleSize: 6).frame(width: 34, height: 48).clipShape(RoundedRectangle(cornerRadius: 7))
                            }
                        }
                    }
                    .padding(4).background(block.fill(Color.white.opacity(0.055)))
                }
                if !gameResults.isEmpty, let onAddGame {
                    section(t("Oyun"))
                    VStack(spacing: 0) {
                        ForEach(gameResults, id: \.providerId) { g in
                            resultRow(g.title, [g.year.map(String.init), g.platforms.prefix(3).joined(separator: " · ").nilIfEmpty].compactMap { $0 }.joined(separator: " · "),
                                      add: { var p = g; p.id = UUID().uuidString.lowercased(); onAddGame(p); query = "" }) {
                                GameCover(block: g).frame(width: 34, height: 45)
                            }
                        }
                    }
                    .padding(4).background(block.fill(Color.white.opacity(0.055)))
                }
                Spacer(minLength: 24)
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .onAppear { focused = true }
        .task(id: query) { await search(query) }
    }

    private func section(_ label: String) -> some View {
        Text(label).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8E8E98)).padding(.leading, 6).padding(.top, 6)
    }

    private func picked(_ title: String, _ meta: String, remove: @escaping () -> Void) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFEDEDF2)).lineLimit(1)
                if !meta.isEmpty { Text(meta).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8E8E98)).lineLimit(1) }
            }
            Spacer()
            Button(action: remove) { RealSymbol("xmark", size: 12).foregroundStyle(Color(argb: 0xFF9A9AA4)).frame(width: 32, height: 32) }
                .accessibilityLabel(t("Kaldır"))
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
    }

    private func resultRow<Art: View>(_ title: String, _ meta: String, add: @escaping () -> Void, @ViewBuilder art: () -> Art) -> some View {
        Button(action: add) {
            HStack(spacing: 12) {
                art()
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(RealFont.inter(15)).foregroundStyle(.white).lineLimit(1)
                    Text(meta).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFA5A5B0)).lineLimit(1)
                }
                Spacer()
                RealSymbol("plus", size: 17.2).foregroundStyle(.white)
            }
            .padding(8).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func search(_ raw: String) async {
        let q = raw.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { songs = []; titles = []; gameResults = []; message = nil; searching = false; return }
        try? await Task.sleep(for: .milliseconds(350))
        guard !Task.isCancelled else { return }
        searching = true
        message = nil
        let wantsGames = onAddGame != nil
        async let s = try? MusicCatalog.search(q)
        async let m = try? MediaCatalog.search(q, type: .MOVIE)
        async let tv = try? MediaCatalog.search(q, type: .TV)
        async let g: [CardGameBlock]? = wantsGames ? (try? GameSearch.search(q)) : []
        let (songsFound, movies, shows, gamesFound) = await (s, m, tv, g)
        guard !Task.isCancelled else { return }
        searching = false
        songs = Array((songsFound ?? []).prefix(6))
        titles = Array(MediaSearchSheet.merge(q, movies ?? [], shows ?? []).prefix(8))
        gameResults = Array((gamesFound ?? []).prefix(5))
        if songsFound == nil && movies == nil && shows == nil { message = t("Arama yapılamadı. Bağlantını kontrol edip yeniden dene.") }
        else if songs.isEmpty && titles.isEmpty && gameResults.isEmpty { message = t("Sonuç bulunamadı.") }
    }
}

/// Card detail: the same one-box search in a sheet; scrolling the results never drags it away.
struct MediaSearchAllSheet: View {
    let search: MediaSearchAll
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(t("Medya")).font(RealFont.inter(19, .semibold)).foregroundStyle(.white)
                Spacer()
                Button(t("Tamam")) { dismiss() }.font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFFBCBCC5))
            }
            search
        }
        .padding(.horizontal, 16).padding(.top, 20)
        .presentationDetents([.large])
        .presentationBackground(Color(argb: 0xFF101012))
        .interactiveDismissDisabled()
    }
}
