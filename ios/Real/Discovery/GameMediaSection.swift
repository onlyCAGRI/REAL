import SwiftUI

/// Gameplay screenshots from IGDB (/api/game-details), cached per game for the session.
@MainActor
private enum GameMediaCache {
    static var shots: [String: [URL]] = [:]

    static func load(_ providerId: String) async throws -> [URL] {
        if let hit = shots[providerId] { return hit }
        let json = try await RealBackend.post("/api/game-details", ["provider": "IGDB", "providerId": providerId], timeout: 20)
        let game = json["game"] as? [String: Any] ?? [:]
        let urls = (game["screenshots"] as? [String] ?? []).filter { $0.hasPrefix("https://") }.compactMap(URL.init(string:))
        shots[providerId] = urls
        return urls
    }
}

/// "Oynanışı gör" in the game detail (GameMediaSection.kt): a button that opens, in the same panel, a large swipeable
/// stage and a strip of gameplay screenshots under it; the stage and the strip follow each other.
struct GameMediaSection: View {
    let providerId: String
    let accent: Color

    @State private var open = false
    @State private var shots: [URL]?
    @State private var error: String?
    @State private var loading = false
    @State private var selected = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: toggle) {
                HStack(spacing: 8) {
                    Text(open ? "▾" : "▶").font(RealFont.inter(13)).foregroundStyle(accent)
                    Text(open ? t("Oynanışı gizle") : t("Oynanışı gör")).font(RealFont.inter(14, .medium)).foregroundStyle(.white)
                }
                .padding(.horizontal, 18).padding(.vertical, 11)
                .overlay(Capsule().strokeBorder(accent.opacity(0.7), lineWidth: 1))
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            if open {
                stage.padding(.top, 14).transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .animation(.easeOut(duration: 0.25), value: open)
    }

    private func toggle() {
        open.toggle()
        guard open, shots == nil, !loading else { return }
        loading = true; error = nil
        Task {
            do { shots = try await GameMediaCache.load(providerId) } catch { self.error = t("Görseller şu anda alınamadı.") }
            loading = false
        }
    }

    @ViewBuilder
    private var stage: some View {
        if loading {
            RoundedRectangle(cornerRadius: 16).fill(Color(argb: 0xFF151518)).aspectRatio(16 / 9, contentMode: .fit)
                .overlay(ProgressView().tint(.white))
        } else if let error {
            Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFE6BCB1))
        } else if let shots, !shots.isEmpty {
            VStack(spacing: 10) {
                TabView(selection: $selected) {
                    ForEach(Array(shots.enumerated()), id: \.offset) { i, url in
                        shot(url, radius: 16).tag(i)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .aspectRatio(16 / 9, contentMode: .fit)
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                ScrollViewReader { proxy in
                    ScrollView(.horizontal) {
                        LazyHStack(spacing: 8) {
                            ForEach(Array(shots.enumerated()), id: \.offset) { i, url in
                                Button { withAnimation(.easeOut(duration: 0.3)) { selected = i } } label: {
                                    shot(url, radius: 10).frame(width: 112, height: 63)
                                        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(i == selected ? Color.white : .clear, lineWidth: 2))
                                }
                                .buttonStyle(.plain)
                                .id(i)
                            }
                        }
                    }
                    .scrollIndicators(.hidden)
                    .frame(height: 63)
                    .onChange(of: selected) { _, i in withAnimation { proxy.scrollTo(i, anchor: .center) } }
                }
            }
        } else {
            Text(t("Bu oyun için görsel yok.")).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF85858E))
        }
    }

    private func shot(_ url: URL, radius: CGFloat) -> some View {
        Color(argb: 0xFF151518)
            .overlay(AsyncImage(url: url) { p in if let i = p.image { i.resizable().scaledToFill() } })
            .clipShape(RoundedRectangle(cornerRadius: radius))
    }
}
