import Foundation

/// The search being typed after a "/" that opens a word; it runs to the end of the text.
struct ComposerSlashQuery: Hashable {
    var start: Int
    var query: String
}

private let slashWordRegex = try! NSRegularExpression(pattern: #"(^|\s)/(?=[\p{L}\d])"#)
private let slashEndRegex = try! NSRegularExpression(pattern: #"(^|\s)/\s*$"#)

func composerSlashQuery(_ text: String) -> ComposerSlashQuery? {
    guard let idx = text.lastIndex(of: "/") else { return nil }
    if idx > text.startIndex && !text[text.index(before: idx)].isWhitespace { return nil }
    let start = text.distance(from: text.startIndex, to: idx)
    let query = text[text.index(after: idx)...].trimmingCharacters(in: .whitespacesAndNewlines)
    return ComposerSlashQuery(start: start, query: query)
}

/// A result was picked: "/silo" becomes "Silo " and the sentence goes on after it.
func replaceComposerSlashQuery(_ text: String, _ title: String) -> String {
    guard let found = composerSlashQuery(text) else { return text }
    let idx = text.index(text.startIndex, offsetBy: min(max(found.start, 0), text.count))
    return String(text[..<idx]) + title + " "
}

/// Saved without picking: the words stay as typed, only the search mark goes. Links keep their slashes.
func stripComposerSlash(_ text: String) -> String {
    let step1 = slashWordRegex.stringByReplacingMatches(in: text, range: NSRange(text.startIndex..., in: text), withTemplate: "$1")
    let step2 = slashEndRegex.stringByReplacingMatches(in: step1, range: NSRange(step1.startIndex..., in: step1), withTemplate: "$1")
    return step2.trimmingCharacters(in: .whitespacesAndNewlines)
}

struct ComposerSlashResult: Identifiable, Hashable {
    enum Kind: Hashable {
        case media(MediaBlock)
        case music(MusicBlock)
        case game(CardGameBlock)
    }

    var id: String
    var label: String
    var title: String
    var kind: Kind
}

struct ComposerSlashOutcome: Hashable {
    var results: [ComposerSlashResult]
    var allFailed: Bool
}

/// "/silo" typed: films, series, songs and games searched concurrently. Only the words after the slash leave the device.
func composerSlashSearch(_ query: String) async -> ComposerSlashOutcome {
    if query.count < 2 { return ComposerSlashOutcome(results: [], allFailed: false) }
    let dizi = t("Dizi"), film = t("Film"), oyun = t("Oyun")

    async let moviesTask: ([MediaBlock], Bool) = {
        do { return (try await MediaCatalog.search(query, type: .MOVIE), false) }
        catch { return ([], true) }
    }()
    async let seriesTask: ([MediaBlock], Bool) = {
        do { return (try await MediaCatalog.search(query, type: .TV), false) }
        catch { return ([], true) }
    }()
    async let songsTask: ([MusicBlock], Bool) = {
        do { return (try await MusicCatalog.search(query), false) }
        catch { return ([], true) }
    }()
    async let gamesTask: ([CardGameBlock], Bool) = {
        do { return (try await GameSearch.search(query), false) }
        catch { return ([], true) }
    }()

    let (movies, series, songs, games) = await (moviesTask, seriesTask, songsTask, gamesTask)
    let allFailed = movies.1 && series.1 && songs.1 && games.1

    let merged = await MediaSearchSheet.merge(query, movies.0, series.0)
    let mediaItems = merged.prefix(4).map { block in
        let kindLabel = block.type == .TV ? dizi : film
        let label = [block.title, kindLabel, block.year.map(String.init)].compactMap { $0 }.joined(separator: " · ")
        return ComposerSlashResult(id: "media:\(block.id)", label: label, title: block.title, kind: .media(block))
    }
    let songItems = songs.0.prefix(3).map { song in
        let label = [song.title, song.artist].filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }.joined(separator: " · ")
        return ComposerSlashResult(id: "music:\(song.id)", label: label, title: song.title, kind: .music(song))
    }
    let gameItems = games.0.prefix(3).map { game in
        let label = [game.title, oyun, game.year.map(String.init)].compactMap { $0 }.joined(separator: " · ")
        return ComposerSlashResult(id: "game:\(game.id)", label: label, title: game.title, kind: .game(game))
    }
    return ComposerSlashOutcome(results: mediaItems + songItems + gameItems, allFailed: allFailed)
}
