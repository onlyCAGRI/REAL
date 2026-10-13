import SwiftUI

// MoodSuggestionsScreen.kt / MoodSuggestionClient.kt: after a mood is saved, four AI ideas for the feeling (Groq via
// /api/mood-suggestions). Songs are verified on iTunes and films / series on TheTVDB / TVmaze before they can be added;
// any idea becomes a REAL card with one tap (and can be taken back). A song or title that cannot be verified can be
// swapped for a fresh idea of the same kind.

enum MoodSuggestionKind: String { case TEXT, MESSAGE, CALL, OUTDOOR, ACTIVITY, READ, MUSIC, MOVIE, TV }

struct MoodSuggestion: Identifiable {
    struct Music { var artist: String; var track: String; var searchQuery: String }
    struct Media { var title: String; var year: Int?; var searchQuery: String }

    let id = UUID()
    var kind: MoodSuggestionKind
    var title: String
    var subtitle: String
    var music: Music?
    var media: Media?

    /// exclusionKeys (Android): what a replacement must not repeat.
    var exclusionKeys: [String] {
        var keys = [title]
        if let m = music { keys += [m.track, "\(m.artist) \(m.track)", m.searchQuery] }
        if let m = media { keys += [m.title, m.searchQuery] }
        var seen = Set<String>()
        return keys.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty && seen.insert($0).inserted }
    }
}

enum MoodSuggestionClient {
    static func fetch(mood: JournalMood, details: MoodDetails, replace: MoodSuggestionKind? = nil,
                      exclude: [String] = [], excludeMusic: [String] = []) async throws -> [MoodSuggestion] {
        var body: [String: Any] = [
            "source": "mood_tracker",
            "mood": mood.rawValue.uppercased(),          // Android sends JournalMood.name (VERY_GOOD, …)
            "descriptors": details.descriptors,
            "factors": details.factors,
            "excludeMusic": Array(excludeMusic.suffix(32)),
        ]
        if let replace {
            body["replaceKind"] = replace.rawValue
            body["exclude"] = Array(exclude.prefix(16))
        }
        let json = try await RealBackend.post("/api/mood-suggestions", body, timeout: 18)
        guard json["ok"] as? Bool == true, let list = json["suggestions"] as? [[String: Any]] else { throw RealBackend.Failure(status: -1) }
        return list.compactMap(parse)
    }

    private static func parse(_ item: [String: Any]) -> MoodSuggestion? {
        let title = ((item["title"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
        guard !title.isEmpty, let kind = MoodSuggestionKind(rawValue: ((item["kind"] as? String) ?? "").uppercased()) else { return nil }
        var suggestion = MoodSuggestion(kind: kind, title: title, subtitle: ((item["subtitle"] as? String) ?? "").trimmingCharacters(in: .whitespaces))
        if kind == .MUSIC, let m = item["music"] as? [String: Any] {
            let artist = ((m["artist"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            let track = ((m["track"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            var query = ((m["searchQuery"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            if query.isEmpty { query = [artist, track].filter { !$0.isEmpty }.joined(separator: " ") }
            if !query.isEmpty { suggestion.music = .init(artist: artist, track: track, searchQuery: query) }
        }
        if kind == .MOVIE || kind == .TV, let m = item["media"] as? [String: Any] {
            let mediaTitle = ((m["title"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            let year = (m["year"] as? Int).flatMap { $0 > 0 ? $0 : nil }
            var query = ((m["searchQuery"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            if query.isEmpty { query = [mediaTitle, year.map(String.init)].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " ") }
            if !mediaTitle.isEmpty || !query.isEmpty { suggestion.media = .init(title: mediaTitle, year: year, searchQuery: query) }
        }
        return suggestion
    }

    /// MusicCandidateMatcher.resolve: the iTunes track for a suggested song (exact, then strong, then relaxed match).
    static func resolveMusic(_ candidate: MoodSuggestion.Music) async -> MusicBlock? {
        func norm(_ s: String) -> String { DiscoveryRanking.normalized(s) }
        func match(_ results: [MusicBlock]) -> MusicBlock? {
            let artist = norm(candidate.artist), track = norm(candidate.track)
            if artist.isEmpty && track.isEmpty { return results.first }
            if let hit = results.first(where: { norm($0.artist) == artist && norm($0.title) == track }) { return hit }
            if let hit = results.first(where: {
                let a = norm($0.artist), t = norm($0.title)
                return (a == artist && (t.contains(track) || track.contains(t))) || (t == track && (a.contains(artist) || artist.contains(a)))
            }) { return hit }
            return results.first(where: {
                let a = norm($0.artist), t = norm($0.title)
                return (artist.isEmpty || a.contains(artist) || artist.contains(a)) && (track.isEmpty || t.contains(track) || track.contains(t))
            })
        }
        if let hit = match((try? await MusicCatalog.search(candidate.searchQuery)) ?? []) { return hit }
        let fallback = "\(candidate.artist) \(candidate.track)".trimmingCharacters(in: .whitespaces)
        guard !fallback.isEmpty, fallback.lowercased() != candidate.searchQuery.lowercased() else { return nil }
        return match((try? await MusicCatalog.search(fallback)) ?? [])
    }
}

/// MoodSuggestionHistoryStore: the last 32 suggested songs, on this device and per account, so new ideas repeat less.
enum MoodMusicHistory {
    @MainActor private static func key() -> String? { RealAuth.shared.account.map { "real_mood_music_history_\($0.uid)" } }

    @MainActor static func exclusions() -> [String] {
        guard let key = key() else { return [] }
        return UserDefaults.standard.stringArray(forKey: key) ?? []
    }

    @MainActor static func record(_ suggestions: [MoodSuggestion]) {
        guard let key = key() else { return }
        var list = UserDefaults.standard.stringArray(forKey: key) ?? []
        for s in suggestions { if let m = s.music { list.append("\(m.artist) - \(m.track)".trimmingCharacters(in: .whitespaces)) } }
        var seen = Set<String>()
        let unique = list.reversed().filter { !$0.isEmpty && seen.insert($0.lowercased()).inserted }.reversed()
        UserDefaults.standard.set(Array(unique.suffix(32)), forKey: key)
    }
}

struct MoodSuggestionsView: View {
    let mood: JournalMood
    let details: MoodDetails
    let onDone: () -> Void

    @Environment(ReminderStore.self) private var store
    private enum Phase { case loading, failed(String), ready }
    private enum Resolution { case loading, music(MusicBlock), media(MediaBlock), failed }

    @State private var phase: Phase = .loading
    @State private var suggestions: [MoodSuggestion] = []
    @State private var resolutions: [UUID: Resolution] = [:]
    @State private var created: [UUID: Int64] = [:]
    @State private var pending: Set<UUID> = []
    @State private var attempt = 0

    private var accent: Color { MoodV2.tint(mood) }

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color.black.lerp(accent, 0.20), Color(argb: 0xFF08090C), .black], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        HStack(spacing: 10) {
                            MoodFace(mood: mood, tint: accent).frame(width: 32, height: 32)
                            Text("REAL Önerileri").font(RealFont.inter(18, .semibold)).foregroundStyle(Color(argb: 0xFFF4F4F6))
                        }
                        Spacer()
                        Button(created.isEmpty ? "Geç" : "Bitti", action: onDone)
                            .font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFF9898A2))
                    }
                    .padding(.vertical, 12)
                    Text("Nasıl hissettiğine göre birkaç fikir").font(RealFont.inter(20, .semibold)).foregroundStyle(Color(argb: 0xFFE0E0E5))
                        .padding(.top, 8)
                    Text("Dilersen birini seçip tek dokunuşla REAL yapabilirsin.").font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF85858E))
                        .padding(.top, 4)
                    Color.clear.frame(height: 24)
                    switch phase {
                    case .loading:
                        HStack(spacing: 12) {
                            ProgressView().tint(accent)
                            Text("Öneriler hazırlanıyor…").font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFF9898A2))
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 40)
                    case .failed(let message):
                        VStack(spacing: 14) {
                            Text(message).font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFFE6BCB1)).multilineTextAlignment(.center)
                            HStack(spacing: 10) {
                                pill("Tekrar dene", filled: true) { attempt += 1 }
                                pill("Kapat", filled: false, action: onDone)
                            }
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 30)
                    case .ready:
                        VStack(spacing: 12) {
                            ForEach(suggestions) { s in card(s) }
                        }
                        pill(created.isEmpty ? "Şimdilik geç" : "Bitti", filled: !created.isEmpty, action: onDone)
                            .frame(maxWidth: .infinity).padding(.top, 16)
                    }
                    Color.clear.frame(height: 40)
                }
                .padding(.horizontal, 22)
            }
            .scrollIndicators(.hidden)
        }
        .task(id: attempt) { await load() }
    }

    // MARK: Loading

    private func load() async {
        phase = .loading
        resolutions = [:]
        do {
            try await AiConsent.require(.mood)
        } catch {
            onDone()                                  // no consent: the mood is saved, nothing is sent
            return
        }
        do {
            let list = try await MoodSuggestionClient.fetch(mood: mood, details: details, excludeMusic: MoodMusicHistory.exclusions())
            guard !list.isEmpty else { throw RealBackend.Failure(status: -1) }
            MoodMusicHistory.record(list)
            suggestions = list
            phase = .ready
            for s in list { resolve(s) }
        } catch {
            phase = .failed(RealBackend.message(for: error))
        }
    }

    private func resolve(_ s: MoodSuggestion) {
        switch s.kind {
        case .MUSIC:
            guard let candidate = s.music else { resolutions[s.id] = .failed; return }
            resolutions[s.id] = .loading
            Task {
                let block = await MoodSuggestionClient.resolveMusic(candidate)
                resolutions[s.id] = block.map { .music($0) } ?? .failed
            }
        case .MOVIE, .TV:
            guard let candidate = s.media else { resolutions[s.id] = .failed; return }
            resolutions[s.id] = .loading
            Task {
                let title = candidate.title.isEmpty ? candidate.searchQuery : candidate.title
                let block = await MediaCatalog.resolve(title: title, year: candidate.year, type: s.kind == .TV ? .TV : .MOVIE)
                resolutions[s.id] = block.map { .media($0) } ?? .failed
            }
        default:
            break
        }
    }

    /// A song / title that could not be verified: ask for a fresh idea of the same kind.
    private func replace(_ s: MoodSuggestion) {
        guard !pending.contains(s.id) else { return }
        pending.insert(s.id)
        resolutions[s.id] = .loading
        Task {
            defer { pending.remove(s.id) }
            let exclude = Array(suggestions.flatMap(\.exclusionKeys).suffix(16))
            guard let fresh = try? await MoodSuggestionClient.fetch(mood: mood, details: details, replace: s.kind, exclude: exclude,
                                                                    excludeMusic: MoodMusicHistory.exclusions()).first,
                  fresh.kind == s.kind, let index = suggestions.firstIndex(where: { $0.id == s.id }) else {
                resolutions[s.id] = .failed
                return
            }
            MoodMusicHistory.record([fresh])
            suggestions[index] = fresh
            resolve(fresh)
        }
    }

    // MARK: Card

    private func card(_ s: MoodSuggestion) -> some View {
        let added = created[s.id] != nil
        let busy = pending.contains(s.id)
        let resolution = resolutions[s.id]
        let needsCatalog = s.kind == .MUSIC || s.kind == .MOVIE || s.kind == .TV
        let verified: Bool = {
            switch resolution { case .music, .media: return true; default: return false }
        }()
        let enabled = !busy && (added || !needsCatalog || verified)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(s.title).font(RealFont.inter(15, .semibold)).foregroundStyle(Color(argb: 0xFFF4F4F6))
                    if !s.subtitle.isEmpty {
                        Text(s.subtitle).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF9898A2))
                    }
                }
                Spacer(minLength: 0)
                Button { toggleAdd(s) } label: {
                    Group {
                        if busy { Text("…").foregroundStyle(Color(argb: 0xFF8E8E98)) }
                        else { RealSymbol(added ? "checkmark" : "plus", size: 16).foregroundStyle(added ? accent : (enabled ? Color(argb: 0xFFF1F1F4) : Color(argb: 0xFF55555E))) }
                    }
                    .frame(width: 38, height: 38)
                    .background(RoundedRectangle(cornerRadius: 13).fill(added ? accent.opacity(0.25) : Color(argb: enabled ? 0xFF222228 : 0xFF19191D)))
                    .overlay(RoundedRectangle(cornerRadius: 13).strokeBorder(added ? accent.opacity(0.7) : Color(argb: enabled ? 0xFF383842 : 0xFF2B2B33), lineWidth: 0.8))
                }
                .buttonStyle(.plain)
                .disabled(!enabled)
                .accessibilityLabel(added ? "Eklenen REAL’i geri al" : "REAL olarak ekle")
            }
            if needsCatalog {
                Color.clear.frame(height: 14)
                switch resolution {
                case .music(let block): MusicBlockView(block: block)
                case .media(let block): MediaBlockView(block: block)
                case .failed:
                    HStack {
                        Text(s.kind == .MUSIC ? "Şarkı şu anda bulunamadı." : (s.kind == .TV ? "Dizi şu anda bulunamadı." : "Film şu anda bulunamadı."))
                            .font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF85858E))
                        Spacer()
                        Button("Tekrar dene") { replace(s) }.font(RealFont.inter(13, .medium)).foregroundStyle(accent)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 14).fill(Color(argb: 0xFF16161A)))
                default:
                    HStack(spacing: 12) {
                        ProgressView().tint(Color(argb: 0xFF85858E))
                        Text(s.kind == .MUSIC
                             ? [s.music?.track, "iTunes aranıyor…"].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " • ")
                             : [s.media?.title, s.kind == .TV ? "TVmaze aranıyor…" : "TheTVDB aranıyor…"].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " • "))
                            .font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF85858E)).lineLimit(1)
                    }
                    .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 14).fill(Color(argb: 0xFF16161A)))
                }
            }
        }
        .padding(.horizontal, 18).padding(.vertical, 16)
        .background(
            RoundedRectangle(cornerRadius: 20).fill(LinearGradient(
                colors: [accent.opacity(added ? 0.22 : 0.14), Color(argb: 0xFF141417), Color(argb: 0xFF0F0F12)],
                startPoint: .top, endPoint: .bottom))
        )
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(accent.opacity(added ? 0.65 : 0.28), lineWidth: 0.8))
    }

    /// onAddSuggestedReal / onUndoSuggestedReal: a plain card with the verified song or title (a writing prompt for TEXT).
    private func toggleAdd(_ s: MoodSuggestion) {
        if let id = created[s.id] {
            store.delete([id])
            created[s.id] = nil
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            return
        }
        let resolution = resolutions[s.id]
        let item = store.addReminder(text: s.title) { item in
            if case .music(let block) = resolution { item.musicBlocks = [block] }
            if case .media(let block) = resolution { item.mediaBlocks = [block] }
            if s.kind == .TEXT {
                item.cardStack = CardStackBlock(prompt: s.subtitle.isEmpty ? nil : s.subtitle, cards: [CardStackCard()])
            }
        }
        created[s.id] = item.id
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    private func pill(_ label: String, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(RealFont.inter(14, .semibold))
                .foregroundStyle(filled ? Color(argb: 0xFF111114) : Color(argb: 0xFFE0E0E5))
                .padding(.horizontal, 22).frame(height: 44)
                .background(RoundedRectangle(cornerRadius: 22).fill(filled ? Color(argb: 0xFFF3F3F5) : Color(argb: 0xFF1A1A1E)))
        }
        .buttonStyle(.plain)
    }
}
