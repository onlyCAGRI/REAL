import SwiftUI

// MoodSuggestionsScreen.kt / MoodSuggestionClient.kt: after a mood is saved, four AI companion cards for the feeling
// (Groq via /api/mood-suggestions). Pure AMOLED (#000000) surface with MoodV3 tone contrast (paper & ink).
// Songs are verified on iTunes and films / series on TheTVDB / TVmaze before they can be added;
// a song / film that isn't found can be swapped ("Başka getir"); any card can be turned into a REAL card with one tap (and taken back).

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
    static func fetch(
        mood: JournalMood,
        details: MoodDetails,
        replace: MoodSuggestionKind? = nil,
        exclude: [String] = [],
        excludeMusic: [String] = [],
        excludeMedia: [String] = [],
        excludeIdeas: [String] = []
    ) async throws -> [MoodSuggestion] {
        var body: [String: Any] = [
            "source": "mood_tracker",
            "mood": mood.rawValue.uppercased(),          // Android sends JournalMood.name (VERY_GOOD, …)
            "descriptors": details.descriptors,
            "factors": details.factors,
            "scope": details.scope.rawValue,
            "localHour": Calendar.current.component(.hour, from: Date()),
            "language": I18n.isEnglish ? "en" : "tr",
            "excludeMusic": Array(excludeMusic.suffix(32)),
            "excludeMedia": Array(excludeMedia.suffix(32)),
            "excludeIdeas": Array(excludeIdeas.suffix(24)),
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

/// MoodSuggestionHistoryStore: recent suggested songs, films/series and companion card titles, per account, so new ideas repeat less.
enum MoodMusicHistory {
    @MainActor private static func musicKey() -> String? { RealAuth.shared.account.map { "real_mood_music_history_\($0.uid)" } }
    @MainActor private static func mediaKey() -> String? { RealAuth.shared.account.map { "real_mood_media_history_\($0.uid)" } }
    @MainActor private static func ideaKey() -> String? { RealAuth.shared.account.map { "real_mood_idea_history_\($0.uid)" } }

    @MainActor static func exclusions() -> [String] {
        guard let key = musicKey() else { return [] }
        return UserDefaults.standard.stringArray(forKey: key) ?? []
    }

    @MainActor static func mediaExclusions() -> [String] {
        guard let key = mediaKey() else { return [] }
        return UserDefaults.standard.stringArray(forKey: key) ?? []
    }

    @MainActor static func ideaExclusions() -> [String] {
        guard let key = ideaKey() else { return [] }
        return UserDefaults.standard.stringArray(forKey: key) ?? []
    }

    @MainActor static func record(_ suggestions: [MoodSuggestion]) {
        if let mKey = musicKey() {
            var list = UserDefaults.standard.stringArray(forKey: mKey) ?? []
            for s in suggestions {
                if let m = s.music {
                    let entry = "\(m.artist) - \(m.track)".trimmingCharacters(in: .whitespaces)
                    if !entry.isEmpty { list.append(entry) }
                }
            }
            UserDefaults.standard.set(dedupe(list, maxItems: 32), forKey: mKey)
        }
        if let mdKey = mediaKey() {
            var list = UserDefaults.standard.stringArray(forKey: mdKey) ?? []
            for s in suggestions {
                if let m = s.media {
                    let entry = m.title.isEmpty ? m.searchQuery : m.title
                    let trimmed = entry.trimmingCharacters(in: .whitespaces)
                    if !trimmed.isEmpty { list.append(trimmed) }
                }
            }
            UserDefaults.standard.set(dedupe(list, maxItems: 32), forKey: mdKey)
        }
        if let iKey = ideaKey() {
            var list = UserDefaults.standard.stringArray(forKey: iKey) ?? []
            for s in suggestions where s.kind != .MUSIC && s.kind != .MOVIE && s.kind != .TV {
                let trimmed = s.title.trimmingCharacters(in: .whitespaces)
                if !trimmed.isEmpty { list.append(trimmed) }
            }
            UserDefaults.standard.set(dedupe(list, maxItems: 24), forKey: iKey)
        }
    }

    private static func dedupe(_ list: [String], maxItems: Int) -> [String] {
        var seen = Set<String>()
        let unique = list.reversed().filter { !$0.isEmpty && seen.insert($0.lowercased()).inserted }.reversed()
        return Array(unique.suffix(maxItems))
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
    @State private var autoRetried: Set<MoodSuggestionKind> = []
    @State private var attempt = 0

    private var family: Int { MoodV2.familyIndex(mood) }
    private var tone: (paper: Color, ink: Color) { MoodV3.tones[min(max(family, 0), MoodV3.tones.count - 1)] }
    private var accent: Color { tone.paper }
    private var darkInk: Color { tone.ink }

    private var tagLabels: [String] {
        let factorLookup = Dictionary(uniqueKeysWithValues: MoodV2.factorGroups.flatMap(\.1))
        let d = details.descriptors.map { MoodV2.descriptorLabel($0) }
        let f = details.factors.map { factorLookup[$0].map { t($0) } ?? $0 }
        return Array((d + f).prefix(4))
    }

    var body: some View {
        ZStack {
            // Pure AMOLED black background with a subtle top glow from the Mood V3 paper tone
            Color.black.ignoresSafeArea()
            RadialGradient(
                colors: [accent.opacity(0.14), .clear],
                center: .top,
                startRadius: 10,
                endRadius: 340
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    // Top bar: V3 face + family + tags pill on the left, Geç / Bitti pill on the right
                    HStack(alignment: .center, spacing: 10) {
                        HStack(spacing: 10) {
                            MoodV3Face(params: MoodFaceParams.at(Double(family), mood: mood), ink: accent)
                                .frame(width: 28, height: 28)
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(MoodV2.families[family].title)
                                        .font(RealFont.inter(14, .bold))
                                        .foregroundStyle(accent)
                                    Text(t("Kaydedildi"))
                                        .font(RealFont.inter(10, .bold))
                                        .foregroundStyle(accent)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Capsule().fill(accent.opacity(0.16)))
                                }
                                if !tagLabels.isEmpty {
                                    Text(tagLabels.joined(separator: " · "))
                                        .font(RealFont.inter(11, .medium))
                                        .foregroundStyle(Color(argb: 0xFF9898A4))
                                        .lineLimit(1)
                                }
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Capsule().fill(accent.opacity(0.08)))
                        .overlay(Capsule().strokeBorder(accent.opacity(0.32), lineWidth: 1))

                        Spacer(minLength: 8)

                        Button(action: onDone) {
                            Text(created.isEmpty ? t("Geç") : t("Bitti · {0}", created.count))
                                .font(RealFont.inter(13, .bold))
                                .foregroundStyle(created.isEmpty ? Color(argb: 0xFFA8A8B4) : darkInk)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 9)
                                .background(Capsule().fill(created.isEmpty ? Color.white.opacity(0.05) : accent))
                                .overlay(
                                    Capsule().strokeBorder(
                                        created.isEmpty ? Color.white.opacity(0.14) : accent,
                                        lineWidth: 1
                                    )
                                )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.vertical, 10)

                    // Companion headline (never advice/coaching)
                    Text(companionHeadline)
                        .font(RealFont.inter(26, .black))
                        .foregroundStyle(Color(argb: 0xFFF5F5F7))
                        .padding(.top, 12)
                    Text(t("Tavsiye değil; hissine eşlik eden kartlar, sesler ve sahneler."))
                        .font(RealFont.inter(13, .medium))
                        .foregroundStyle(Color(argb: 0xFF8E8E9A))
                        .padding(.top, 6)

                    Color.clear.frame(height: 22)

                    switch phase {
                    case .loading:
                        VStack(spacing: 12) {
                            ForEach(0..<4, id: \.self) { index in
                                skeletonCard(index: index)
                            }
                        }
                    case .failed(let message):
                        VStack(spacing: 16) {
                            Text(message)
                                .font(RealFont.inter(14, .medium))
                                .foregroundStyle(accent)
                                .multilineTextAlignment(.center)
                            HStack(spacing: 10) {
                                pill(t("Tekrar dene"), filled: true) { attempt += 1 }
                                pill(t("Kapat"), filled: false, action: onDone)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(22)
                        .background(RoundedRectangle(cornerRadius: 24).fill(Color.black))
                        .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(accent.opacity(0.35), lineWidth: 1))
                        .padding(.vertical, 20)
                    case .ready:
                        VStack(spacing: 12) {
                            ForEach(suggestions) { s in card(s) }
                        }
                        Color.clear.frame(height: 18)
                        pill(
                            created.isEmpty ? t("Şimdilik geç") : t("REAL'e dön · {0} kart eklendi", created.count),
                            filled: !created.isEmpty,
                            action: onDone
                        )
                        .frame(maxWidth: .infinity)
                    }
                    Color.clear.frame(height: 40)
                }
                .padding(.horizontal, 20)
            }
            .scrollIndicators(.hidden)
        }
        .task(id: attempt) { await load() }
    }

    private var companionHeadline: String {
        switch family {
        case 0: return t("Bu ağırlığı düzeltmeye çalışmayalım.\nSadece adını koyalım.")
        case 1: return t("Kafandaki gürültüyle yarışma.\nŞu anın yanına oturalım.")
        case 2: return t("Düz ve sessiz bir an.\nİçinden geçen küçük bir izi yakalayalım.")
        case 3: return t("İçindeki bu sakin akışı\nolduğu gibi kayda geçirelim.")
        default: return t("Bu yüksek frekansı kaçırma.\nÜstüne bir iz bırakalım.")
        }
    }

    private func skeletonCard(index: Int) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Capsule()
                .fill(accent.opacity(0.18))
                .frame(width: 96, height: 18)
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.white.opacity(0.08))
                .frame(width: index % 2 == 0 ? 230 : 190, height: 18)
            RoundedRectangle(cornerRadius: 7)
                .fill(Color.white.opacity(0.05))
                .frame(width: 260, height: 14)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 18)
        .padding(.vertical, 18)
        .background(RoundedRectangle(cornerRadius: 24).fill(Color.black))
        .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(accent.opacity(0.20), lineWidth: 1))
    }

    // MARK: Loading

    private func load() async {
        phase = .loading
        resolutions = [:]
        autoRetried = []
        do {
            try await AiConsent.require(.mood)
        } catch {
            onDone()                                  // no consent: the mood is saved, nothing is sent
            return
        }
        do {
            let list = try await MoodSuggestionClient.fetch(
                mood: mood,
                details: details,
                excludeMusic: MoodMusicHistory.exclusions(),
                excludeMedia: MoodMusicHistory.mediaExclusions(),
                excludeIdeas: MoodMusicHistory.ideaExclusions()
            )
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
                if let block = await MoodSuggestionClient.resolveMusic(candidate) {
                    resolutions[s.id] = .music(block)
                } else if !autoRetried.contains(s.kind) {
                    autoRetried.insert(s.kind)
                    replace(s)
                } else {
                    resolutions[s.id] = .failed
                }
            }
        case .MOVIE, .TV:
            guard let candidate = s.media else { resolutions[s.id] = .failed; return }
            resolutions[s.id] = .loading
            Task {
                let title = candidate.title.isEmpty ? candidate.searchQuery : candidate.title
                if let block = await MediaCatalog.resolve(title: title, year: candidate.year, type: s.kind == .TV ? .TV : .MOVIE) {
                    resolutions[s.id] = .media(block)
                } else if !autoRetried.contains(s.kind) {
                    autoRetried.insert(s.kind)
                    replace(s)
                } else {
                    resolutions[s.id] = .failed
                }
            }
        default:
            break
        }
    }

    /// Swap any card (companion seed, song, or film/series) for a fresh companion idea of the same kind.
    private func replace(_ s: MoodSuggestion) {
        guard !pending.contains(s.id) else { return }
        pending.insert(s.id)
        if s.kind == .MUSIC || s.kind == .MOVIE || s.kind == .TV {
            resolutions[s.id] = .loading
        }
        Task {
            defer { pending.remove(s.id) }
            let exclude = Array((suggestions.flatMap(\.exclusionKeys) + s.exclusionKeys).suffix(16))
            guard let fresh = try? await MoodSuggestionClient.fetch(
                mood: mood,
                details: details,
                replace: s.kind,
                exclude: exclude,
                excludeMusic: MoodMusicHistory.exclusions(),
                excludeMedia: MoodMusicHistory.mediaExclusions(),
                excludeIdeas: MoodMusicHistory.ideaExclusions()
            ).first,
            let index = suggestions.firstIndex(where: { $0.id == s.id }) else {
                if s.kind == .MUSIC || s.kind == .MOVIE || s.kind == .TV {
                    resolutions[s.id] = .failed
                }
                return
            }
            if let oldReminderId = created.removeValue(forKey: s.id) {
                store.delete([oldReminderId])
            }
            MoodMusicHistory.record([fresh])
            suggestions[index] = fresh
            resolve(fresh)
        }
    }

    // MARK: Card

    private func badgeLabel(for kind: MoodSuggestionKind) -> String {
        switch kind {
        case .TEXT: return t("İç Ses · Kart Tohumu")
        case .MESSAGE: return t("Mesaj / Sınır · Kart Tohumu")
        case .CALL: return t("Temas · Kart Tohumu")
        case .OUTDOOR, .ACTIVITY: return t("Anın İzi · Kart Tohumu")
        case .READ: return t("Düşünce · Kart Tohumu")
        case .MUSIC: return t("Frekans · Şarkı")
        case .MOVIE: return t("Atmosfer · Film")
        case .TV: return t("Atmosfer · Dizi")
        }
    }

    private func card(_ s: MoodSuggestion) -> some View {
        let added = created[s.id] != nil
        let busy = pending.contains(s.id)
        let resolution = resolutions[s.id]
        let needsCatalog = s.kind == .MUSIC || s.kind == .MOVIE || s.kind == .TV
        let verified: Bool = {
            switch resolution { case .music, .media: return true; default: return false }
        }()
        let enabled = !busy && (added || !needsCatalog || verified)
        let cardBg = added ? accent : Color.black
        let borderColor = added ? accent : accent.opacity(0.28)
        let primaryText = added ? darkInk : Color(argb: 0xFFF5F5F7)
        let secondaryText = added ? darkInk.opacity(0.78) : Color(argb: 0xFF9898A4)

        return VStack(alignment: .leading, spacing: 0) {
            // Top row: companion badge on the left, "+ REAL" / "✓ REAL'de" on the right
            HStack(alignment: .center, spacing: 8) {
                Text(badgeLabel(for: s.kind))
                    .font(RealFont.inter(11, .bold))
                    .foregroundStyle(added ? darkInk : accent)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Capsule().fill(added ? darkInk.opacity(0.12) : accent.opacity(0.12)))

                Spacer(minLength: 4)

                // ↻ swap hidden for now; "Başka getir" stays on catalog misses only.

                // + REAL / ✓ REAL'de pill
                Button { toggleAdd(s) } label: {
                    HStack(spacing: 5) {
                        RealSymbol(added ? "checkmark" : "plus", size: 13)
                        Text(added ? t("REAL'de") : "REAL")
                            .font(RealFont.inter(12, .bold))
                    }
                    .foregroundStyle(
                        added ? accent : (enabled ? darkInk : Color(argb: 0xFF5E5E68))
                    )
                    .padding(.horizontal, 13)
                    .frame(height: 34)
                    .background(
                        Capsule().fill(
                            added ? darkInk : (enabled ? accent : Color(argb: 0xFF141418))
                        )
                    )
                }
                .buttonStyle(.plain)
                .disabled(!enabled)
                .accessibilityLabel(added ? t("Eklenen REAL’i geri al") : t("REAL olarak ekle"))
            }

            Color.clear.frame(height: 12)

            // Main title (the card seed / feeling statement)
            Text(s.title)
                .font(RealFont.inter(16, .bold))
                .foregroundStyle(primaryText)
                .fixedSize(horizontal: false, vertical: true)

            if !s.subtitle.isEmpty {
                Text(s.subtitle)
                    .font(RealFont.inter(13, .medium))
                    .foregroundStyle(secondaryText)
                    .padding(.top, 5)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if needsCatalog {
                Color.clear.frame(height: 14)
                switch resolution {
                case .music(let block):
                    MusicBlockView(block: block)
                case .media(let block):
                    MediaBlockView(block: block)
                case .failed:
                    HStack {
                        Text(s.kind == .MUSIC ? t("Şarkı şu anda bulunamadı.") : (s.kind == .TV ? t("Dizi şu anda bulunamadı.") : t("Film şu anda bulunamadı.")))
                            .font(RealFont.inter(13, .medium))
                            .foregroundStyle(secondaryText)
                        Spacer()
                        Button(t("Başka getir")) { replace(s) }
                            .font(RealFont.inter(13, .bold))
                            .foregroundStyle(added ? darkInk : accent)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color(argb: 0xFF0A0A0D)))
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(accent.opacity(0.22), lineWidth: 0.8))
                default:
                    HStack(spacing: 12) {
                        ProgressView().tint(accent)
                        Text(s.kind == .MUSIC
                             ? [s.music?.track, t("iTunes aranıyor…")].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " • ")
                             : [s.media?.title, s.kind == .TV ? t("TVmaze aranıyor…") : t("TheTVDB aranıyor…")].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " • "))
                            .font(RealFont.inter(13, .medium))
                            .foregroundStyle(Color(argb: 0xFF9898A4))
                            .lineLimit(1)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color(argb: 0xFF0A0A0D)))
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(accent.opacity(0.18), lineWidth: 0.8))
                }
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .background(RoundedRectangle(cornerRadius: 24).fill(cardBg))
        .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(borderColor, lineWidth: added ? 1.5 : 1.0))
        .animation(.easeOut(duration: 0.2), value: added)
    }

    /// onAddSuggestedReal / onUndoSuggestedReal: turns the companion card into a REAL card (with prompt on non-media seeds).
    private func toggleAdd(_ s: MoodSuggestion) {
        if let id = created[s.id] {
            store.delete([id])
            created[s.id] = nil
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            return
        }
        let resolution = resolutions[s.id]
        let needsCatalog = s.kind == .MUSIC || s.kind == .MOVIE || s.kind == .TV
        let item = store.addReminder(text: s.title) { item in
            item.sendImmediateNotification = false // added from a suggestion, not a reminder: no "Hemen bildir"
            if case .music(let block) = resolution { item.musicBlocks = [block] }
            if case .media(let block) = resolution { item.mediaBlocks = [block] }
            if !needsCatalog {
                item.cardStack = CardStackBlock(prompt: s.subtitle.isEmpty ? nil : s.subtitle, cards: [CardStackCard()])
            }
        }
        created[s.id] = item.id
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    private func pill(_ label: String, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(RealFont.inter(14, .bold))
                .foregroundStyle(filled ? darkInk : Color(argb: 0xFFF2F2F6))
                .padding(.horizontal, 22)
                .frame(maxWidth: filled ? .infinity : nil, minHeight: 50)
                .background(Capsule().fill(filled ? accent : Color.black))
                .overlay(Capsule().strokeBorder(filled ? accent : accent.opacity(0.32), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
