import SwiftUI

/// A one-tap suggestion under the composer: a date read from the words, or a "/" search result.
struct ComposerSuggestionChip: View {
    let symbol: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                RealSymbol(symbol, size: 13).foregroundStyle(Color(argb: 0xFF91B9FF))
                Text(label)
                    .font(RealFont.inter(12, .medium))
                    .foregroundStyle(Color(argb: 0xFFDCE8FF))
                    .lineLimit(1)
            }
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Capsule().fill(RealColors.accentBlue.opacity(0.16)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// "/silo" typed: films, series, songs and games as chips under the composer (ComposerSlashResults.kt). Only the words
/// after the slash leave the device. Picking one adds its block to the draft, where it shows as a tile in the tray.
struct ComposerSlashRow: View {
    let text: String
    let onPick: (ComposerSlashResult) -> Void
    @State private var results: [ComposerSlashResult] = []
    @State private var status: String?

    var body: some View {
        let query = composerSlashQuery(text)?.query
        VStack(spacing: 0) {
            // Always in the tree: the search has to run while nothing is shown yet.
            Color.clear.frame(height: 0).task(id: query) { await search(query) }
            if query != nil && (status != nil || !results.isEmpty) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        if let status {
                            Text(status).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8E8E98)).lineLimit(1)
                        }
                        ForEach(results) { result in
                            ComposerSuggestionChip(symbol: symbol(result), label: result.label) { onPick(result) }
                        }
                    }
                    .padding(.horizontal, 14)
                }
                .padding(.bottom, 4)
            }
        }
    }

    private func symbol(_ result: ComposerSlashResult) -> String {
        switch result.kind {
        case .media: return "film"
        case .music: return "music.note"
        case .game: return "gamecontroller"
        }
    }

    private func search(_ query: String?) async {
        results = []
        status = nil
        guard let query, query.count >= 2 else { return }
        try? await Task.sleep(nanoseconds: 350_000_000)
        if Task.isCancelled { return }
        status = t("Aranıyor…")
        let outcome = await composerSlashSearch(query)
        if Task.isCancelled { return }
        results = outcome.results
        if outcome.allFailed {
            status = t("Arama yapılamadı. Bağlantını kontrol edip yeniden dene.")
        } else {
            status = outcome.results.isEmpty ? t("Sonuç bulunamadı.") : nil
        }
    }
}
