import Foundation

/// Someone in Kişilerim, as the composer's person suggestions see them; `nickname` is what the user calls them.
struct ComposerKnownPerson: Hashable {
    var name: String
    var uid: String? = nil
    var pictureVersion: Int64? = nil
    var nickname: String? = nil
}

/// A person found in the typed words. Picking it puts `person` on the card and leaves `text`: a "with" form
/// ("annemle", "Ayşe ile") comes out of the sentence, any other form ("anneme hediye al") stays, the words need it.
/// `isNew` = not in Kişilerim yet; picking makes them there.
struct ComposerPersonSuggestion: Hashable {
    var person: CardPerson
    var text: String
    var isNew: Bool = false
}

private let trLocale = Locale(identifier: "tr_TR")
private let enLocale = Locale(identifier: "en_US_POSIX")
private let tokenRegex = try! NSRegularExpression(pattern: #"(?<![\p{L}\d/.@_-])[\p{L}]+(?:['’][\p{L}]+)?"#)
private let withWordsRegex = try! NSRegularExpression(pattern: #"^(?:\s+(?:ile|birlikte|beraber)(?=$|[\s.,!?]))+"#)
private let withPrefixRegex = try! NSRegularExpression(pattern: #"(?:^|\s)with(?:\s+my)?\s+$"#, options: [.caseInsensitive])
private let spacesRegex = try! NSRegularExpression(pattern: #"\s+"#)
private let spaceBeforePunctRegex = try! NSRegularExpression(pattern: #"\s+([.,!?])"#)
private let withSuffixes: Set<String> = ["le", "la", "yle", "yla", "lerle", "larla"]
private let suffixes: [String] = (withSuffixes.union([
    "e", "a", "i", "ı", "u", "ü", "ye", "ya", "yi", "yı", "yu", "yü",
    "in", "ın", "un", "ün", "nin", "nın", "nun", "nün",
    "de", "da", "te", "ta", "den", "dan", "ten", "tan", "nde", "nda", "nden", "ndan",
    "ler", "lar", "lere", "lara", "lerin", "ların"
])).sorted { $0.count < $1.count }

private let kin: Set<String> = [
    "annem", "babam", "kardeşim", "ablam", "abim", "ağabeyim", "dedem", "ninem", "babaannem", "anneannem",
    "teyzem", "halam", "amcam", "dayım", "eşim", "karım", "kocam", "sevgilim", "oğlum", "kızım", "yengem",
    "eniştem", "kuzenim", "arkadaşım"
]

private let trTitles: [String: String] = [
    "teyze": "teyze", "teyzem": "teyze",
    "hala": "hala", "halam": "hala",
    "amca": "amca", "amcam": "amca",
    "dayı": "dayı", "dayım": "dayı",
    "abi": "abi", "abim": "abi",
    "ağabey": "ağabey", "ağabeyim": "ağabey",
    "abla": "abla", "ablam": "abla",
    "yenge": "yenge", "yengem": "yenge",
    "enişte": "enişte", "eniştem": "enişte",
    "dede": "dede", "dedem": "dede",
    "nine": "nine", "ninem": "nine",
    "hoca": "hoca", "hocam": "hoca",
    "usta": "usta", "ustam": "usta",
    "hanım": "hanım", "bey": "bey"
]

private let trStoplist: Set<String> = Set("""
    bugün yarın dün sonra önce akşam sabah öğlen gece hafta bu şu o bir ben sen biz siz bizim sizin onun benim senin ve için
    kim yaz ay su yine hep belki galiba lütfen acaba ayrıca pazartesi salı çarşamba perşembe cuma cumartesi pazar
    akşama sabaha geceye bugünkü yarınki
""".split(whereSeparator: \.isWhitespace).map(String.init))

private let enAlwaysKin: Set<String> = ["mom", "mommy", "mummy", "dad", "daddy", "grandma", "grandpa", "granny"]
private let enMyKin: Set<String> = [
    "mother", "father", "sister", "brother", "wife", "husband", "son", "daughter", "aunt", "uncle",
    "cousin", "friend", "boyfriend", "girlfriend", "partner", "grandmother", "grandfather"
]
private let enTitles: Set<String> = ["aunt", "auntie", "uncle", "grandma", "grandpa", "cousin", "mr", "mrs", "ms", "dr"]
private let enDotTitles: Set<String> = ["mr", "mrs", "ms", "dr"]

/// Also everyday words: these count as a name only when written with a capital ("Deniz'e", not "deniz").
private let commonWordNames: [String] = """
    Deniz Umut Can Bahar Kaya Efe Ada Ela Nur Naz Sevgi Barış Yağmur Güneş Çiçek Pınar Irmak Özlem Ümit Tan Ece Nil
    Derin Dilek Murat Sevda Aslı Veli Emin Gül Güven Doğa Bulut Toprak Ceren Ferdi Kader Hayat Şeker Nazlı Sevim Ufuk
    Yıldız Zafer Aydın Ateş Kartal Mutlu Rüya Saadet Sevinç Şafak Tuna Yaz Ay Su
""".split(whereSeparator: \.isWhitespace).map(String.init)

/// English names that are also ordinary words: capitalised and not the first token (unless apostrophised).
private let enWordNames: [String] = """
    Will Mark Bill Frank Jack Ben Adam Kim Alan Sam Tom Dan Pat Sue Grace Hope Rose May June April Faith Joy Amber Ruby Ava
""".split(whereSeparator: \.isWhitespace).map(String.init)

private let plainNames: [String] = """
    Ahmet Mehmet Mustafa Ali Hüseyin Hasan İbrahim İsmail Osman Yusuf Ömer Ramazan Halil Süleyman Abdullah Mahmut
    Recep Salih Fatih Kadir Emre Hakan Adem Kemal Yaşar Bekir Musa Metin Serkan Uğur Burak Onur Cem Volkan Sinan Tolga
    Serdar Gökhan Erkan Erdem Selim Tuncay Ahmed Berk Mert Kerem Arda Eren Alp Baran Berkay Burhan Cengiz Çağrı Çağlar
    Cihan Coşkun Davut Dursun Ege Emir Emrah Enes Engin Ercan Erhan Ersin Fikret Furkan Görkem Hamza Harun İlker İlyas
    Kaan Kamil Kenan Koray Levent Mesut Nihat Oğuz Oğuzhan Okan Orhan Ozan Özgür Rıza Samet Savaş Semih Sercan Sezer
    Şahin Şükrü Taner Tarık Tayfun Tufan Turgut Vedat Yakup Yasin Yavuz Yiğit Yunus Yücel Zeki Ziya Batuhan Doruk
    Fatma Ayşe Emine Hatice Zeynep Elif Meryem Şerife Sultan Zehra Hanife Merve Özge Esra Yasemin Hacer Leyla Sevgül
    Songül Dilan Rabia Kübra Büşra Tuğba Sibel Aysel Ayla Aylin Ayşegül Arzu Asya Azra Bedriye Belgin Berna Beyza Betül
    Burcu Cansu Canan Cemile Damla Defne Derya Didem Dilara Duygu Dudu Ebru Eda Ecrin Esin Eylül Feride Fadime Fatoş
    Feyza Filiz Funda Gamze Gizem Gonca Gülay Gülşen Gülten Hande Havva Hilal Hülya İpek İrem Kezban Lale Melek Melike
    Melisa Mine Mualla Nazan Nazife Nermin Neslihan Nevin Nihal Nilay Nur Oya Öykü Perihan Reyhan Rukiye Sabriye Sare
    Sara Seda Sedef Selin Selma Sema Semra Serap Seher Sevil Sinem Şebnem Şule Tülay Ümmü Yeliz Yeşim Zuhal Zerrin
    John James Michael David Robert William Richard Thomas Daniel Matthew Andrew Joseph Christopher Anthony Steven Paul
    George Kevin Brian Jason Ryan Jacob Nicholas Jonathan Justin Brandon Oliver Harry Charlie Lucas Liam Noah Ethan Henry
    Mary Jennifer Linda Elizabeth Barbara Susan Jessica Sarah Karen Lisa Nancy Emily Emma Olivia Sophia Mia Charlotte
    Amelia Hannah Rachel Laura Anna Julia Kate Katie Megan Lauren Amy Alice Lucy Chloe Nicole Michelle Stephanie Rebecca
""".split(whereSeparator: \.isWhitespace).map(String.init)

private func personKey(_ word: String) -> String { word.lowercased(with: trLocale) }
private func enKey(_ word: String) -> String { word.lowercased(with: enLocale) }
private func trTitle(_ word: String) -> String { word.prefix(1).uppercased(with: trLocale) + word.dropFirst() }
private func enTitle(_ word: String) -> String { word.prefix(1).uppercased(with: enLocale) + word.dropFirst() }
private let plainNameByKey: [String: String] = Dictionary(plainNames.map { (personKey($0), $0) }, uniquingKeysWith: { _, b in b })
private let commonNameByKey: [String: String] = Dictionary(commonWordNames.map { (personKey($0), $0) }, uniquingKeysWith: { _, b in b })
private let enWordNameByKey: [String: String] = Dictionary(enWordNames.map { (enKey($0), $0) }, uniquingKeysWith: { _, b in b })

/// Every way to read `word` as stem + case ending, the whole word first; written apostrophes settle it.
private func readings(_ word: String) -> [(String, String)] {
    if let apostrophe = word.firstIndex(where: { $0 == "'" || $0 == "’" }), apostrophe > word.startIndex {
        let suffix = String(word[word.index(after: apostrophe)...])
        return (suffixes.contains(suffix) || suffix == "s") ? [(String(word[..<apostrophe]), suffix)] : []
    }
    return [(word, "")] + suffixes.filter { word.count > $0.count && word.hasSuffix($0) }
        .map { (String(word.dropLast($0.count)), $0) }
}

private func enKinReading(_ token: String) -> (String, String)? {
    let lower = enKey(token)
    guard let apostrophe = lower.firstIndex(where: { $0 == "'" || $0 == "’" }) else { return (lower, "") }
    guard apostrophe > lower.startIndex else { return nil }
    let offset = lower.distance(from: lower.startIndex, to: apostrophe)
    let tokenIdx = token.index(token.startIndex, offsetBy: offset)
    let suffix = personKey(String(token[token.index(after: tokenIdx)...]))
    return (suffixes.contains(suffix) || suffix == "s") ? (String(lower[..<apostrophe]), suffix) : nil
}

/// Finds people in typed text on the device, without any network. Kişilerim wins over the word lists: a nickname shows as
/// the nickname, a first name as the full name. People already in `chosen` are left out.
func composerPersonSuggestions(
    _ text: String,
    known: [ComposerKnownPerson] = [],
    chosen: [CardPerson] = [],
    limit: Int = 3
) -> [ComposerPersonSuggestion] {
    if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return [] }
    var byWord: [String: [CardPerson]] = [:]
    var byPhrase: [String: [CardPerson]] = [:]
    for person in known {
        let full = CardPerson(name: person.name, uid: person.uid, pictureVersion: person.pictureVersion)
        let cleanName = person.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let first = cleanName.split(separator: " ", maxSplits: 1, omittingEmptySubsequences: false).first.map(String.init) ?? ""
        byWord[personKey(first), default: []].append(full)
        byPhrase[personKey(cleanName), default: []].append(full)
        if let rawNick = person.nickname?.trimmingCharacters(in: .whitespacesAndNewlines), !rawNick.isEmpty {
            let nickFirst = rawNick.split(separator: " ", maxSplits: 1, omittingEmptySubsequences: false).first.map(String.init) ?? ""
            var copy = full
            copy.name = rawNick
            byWord[personKey(nickFirst), default: []].append(copy)
            byPhrase[personKey(rawNick), default: []].append(copy)
        }
    }
    var out: [ComposerPersonSuggestion] = []

    func add(_ person: CardPerson, _ range: Range<String.Index>, _ suffix: String, _ isNew: Bool) {
        let same: (CardPerson) -> Bool = { other in
            (person.uid != nil && other.uid == person.uid) || personKey(other.name) == personKey(person.name)
        }
        if chosen.contains(where: same) || out.contains(where: { same($0.person) }) { return }
        let before = String(text[..<range.lowerBound])
        let after = String(text[range.upperBound...])
        let afterLower = after.lowercased(with: trLocale)
        let withMatch = withWordsRegex.firstMatch(in: afterLower, range: NSRange(afterLower.startIndex..., in: afterLower))
        let prefixMatch = withPrefixRegex.firstMatch(in: before, range: NSRange(before.startIndex..., in: before))
        let removeStart: String.Index
        if let pm = prefixMatch, let pr = Range(pm.range, in: before) {
            let startIdx = (pr.lowerBound > before.startIndex && before[pr.lowerBound].isWhitespace)
                ? before.index(after: pr.lowerBound) : pr.lowerBound
            let offset = before.distance(from: before.startIndex, to: startIdx)
            removeStart = text.index(text.startIndex, offsetBy: offset)
        } else {
            removeStart = range.lowerBound
        }
        let rest: String
        if suffix != "s" && (withSuffixes.contains(suffix) || withMatch != nil || prefixMatch != nil) {
            let afterDrop: String
            if let m = withMatch, let r = Range(m.range, in: afterLower) {
                let charCount = afterLower[..<r.upperBound].count
                let idx = after.index(after.startIndex, offsetBy: min(charCount, after.count))
                afterDrop = String(after[idx...])
            } else {
                afterDrop = after
            }
            let combined = String(text[..<removeStart]) + afterDrop
            let collapsed = spacesRegex.stringByReplacingMatches(in: combined, range: NSRange(combined.startIndex..., in: combined), withTemplate: " ")
            let cleaned = spaceBeforePunctRegex.stringByReplacingMatches(in: collapsed, range: NSRange(collapsed.startIndex..., in: collapsed), withTemplate: "$1")
            rest = cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
        } else {
            rest = text
        }
        out.append(ComposerPersonSuggestion(person: person, text: rest, isNew: isNew))
    }

    func addPhrase(_ display: String, _ range: Range<String.Index>, _ suffix: String) {
        if let matches = byPhrase[personKey(display)] {
            for p in matches { add(p, range, suffix, false) }
        } else {
            add(CardPerson(name: display), range, suffix, true)
        }
    }

    func isWhitespaceOnly(_ s: Substring) -> Bool {
        !s.isEmpty && s.allSatisfy(\.isWhitespace)
    }

    func adjacent(_ a: Range<String.Index>, _ b: Range<String.Index>) -> Bool {
        a.upperBound < b.lowerBound && isWhitespaceOnly(text[a.upperBound..<b.lowerBound])
    }

    func isSentenceStart(_ start: String.Index) -> Bool {
        var idx = start
        while idx > text.startIndex {
            idx = text.index(before: idx)
            let c = text[idx]
            if c == "\n" || c == "\r" || c.isNewline { return true }
            if !c.isWhitespace { return c == "." || c == "!" || c == "?" }
        }
        return true
    }

    func titleAfterName(_ raw: String, possessiveTitle: Bool) -> String? {
        if raw.contains(where: { $0 == "'" || $0 == "’" }) { return nil }
        let k = personKey(raw)
        if kin.contains(k) || trTitles[k] != nil { return nil }
        if byWord[k] != nil { return trTitle(raw) }
        if trStoplist.contains(k) { return nil }
        if let p = plainNameByKey[k] { return p }
        if let c = commonNameByKey[k] { return c }
        let isUpper = raw.first?.isUppercase == true
        if isUpper, let e = enWordNameByKey[enKey(raw)] { return e }
        if isUpper && !possessiveTitle {
            let hasKnownStem = readings(k).contains { (stem, _) in
                kin.contains(stem) || plainNameByKey[stem] != nil || commonNameByKey[stem] != nil || byWord[stem] != nil
            }
            if !hasKnownStem { return trTitle(raw) }
        }
        return nil
    }

    let fullRange = NSRange(text.startIndex..., in: text)
    let tokens: [(range: Range<String.Index>, value: String)] = tokenRegex.matches(in: text, range: fullRange).compactMap { m in
        guard let r = Range(m.range, in: text) else { return nil }
        return (r, String(text[r]))
    }

    var i = 0
    while i < tokens.count {
        let (range, raw) = tokens[i]
        let lowerTr = personKey(raw)
        let lowerEn = enKey(raw)
        let prev = (i > 0 && adjacent(tokens[i - 1].range, range)) ? enKey(tokens[i - 1].value) : nil

        if i + 1 < tokens.count {
            let next = tokens[i + 1]
            let between = text[range.upperBound..<next.range.lowerBound]
            let isEnTitle = enTitles.contains(lowerEn) || (raw == "Miss" && lowerEn == "miss")
            let validGap = isWhitespaceOnly(between) ||
                (enDotTitles.contains(lowerEn) && between.hasPrefix(".") && isWhitespaceOnly(between.dropFirst()))
            if isEnTitle && validGap {
                var matchedEnTitle = false
                for (stem, suffix) in readings(personKey(next.value)) {
                    let stemLen = stem.count
                    guard stemLen > 0, stemLen <= next.value.count else { continue }
                    let rawStem = String(next.value.prefix(stemLen))
                    let k = personKey(rawStem)
                    let ek = enKey(rawStem)
                    let isNextUpper = rawStem.first?.isUppercase == true
                    let nameDisplay = plainNameByKey[k] ?? commonNameByKey[k]
                        ?? (isNextUpper ? enWordNameByKey[ek] : nil)
                        ?? ((byWord[k] != nil || isNextUpper) ? trTitle(rawStem) : nil)
                    if let nameDisplay {
                        let start = prev == "my" ? tokens[i - 1].range.lowerBound : range.lowerBound
                        addPhrase("\(enTitle(lowerEn)) \(nameDisplay)", start..<next.range.upperBound, suffix)
                        matchedEnTitle = true
                        break
                    }
                }
                if matchedEnTitle {
                    i += 2
                    continue
                }
            }
        }

        if i + 1 < tokens.count && adjacent(range, tokens[i + 1].range) {
            let next = tokens[i + 1]
            var matchedTitle = false
            for (stem, suffix) in readings(personKey(next.value)) {
                guard let baseTitle = trTitles[stem] else { continue }
                let afterNext = String(text[next.range.upperBound...]).lowercased(with: trLocale)
                if stem == "hala" && suffix.isEmpty && withWordsRegex.firstMatch(in: afterNext, range: NSRange(afterNext.startIndex..., in: afterNext)) == nil {
                    continue
                }
                guard let nameDisplay = titleAfterName(raw, possessiveTitle: stem != baseTitle) else { continue }
                addPhrase("\(nameDisplay) \(baseTitle)", range.lowerBound..<next.range.upperBound, suffix)
                matchedTitle = true
                break
            }
            if matchedTitle {
                i += 2
                continue
            }
        }

        if let (enStem, enSuffix) = enKinReading(raw) {
            if enAlwaysKin.contains(enStem) || (enStem == "mum" && (prev == "my" || prev == "with")) {
                addPhrase(enTitle(enStem), range, enSuffix)
                i += 1
                continue
            }
            if enMyKin.contains(enStem) && prev == "my" {
                addPhrase("My \(enStem)", tokens[i - 1].range.lowerBound..<range.upperBound, enSuffix)
                i += 1
                continue
            }
        }

        for (stem, suffix) in readings(lowerTr) {
            let people = byWord[stem]
            let isUpper = raw.first?.isUppercase == true
            let common = (isUpper && (!suffix.isEmpty || !trStoplist.contains(stem))) ? commonNameByKey[stem] : nil
            let hasApostrophe = raw.contains(where: { $0 == "'" || $0 == "’" })
            let enCommon: String? = (isUpper && (!isSentenceStart(range.lowerBound) || hasApostrophe) && stem.count <= raw.count)
                ? enWordNameByKey[enKey(String(raw.prefix(stem.count)))] : nil
            let name = plainNameByKey[stem] ?? common ?? enCommon
            if let people {
                for p in people { add(p, range, suffix, false) }
            } else if kin.contains(stem) {
                add(CardPerson(name: trTitle(stem)), range, suffix, true)
            } else if let name {
                add(CardPerson(name: name), range, suffix, true)
            } else {
                continue
            }
            break
        }
        i += 1
    }
    return Array(out.prefix(limit))
}

/// The connection this name on the card is: its uid, or a name or nickname that matches one. nil = can't be sent to.
func connectionFor(person: CardPerson, known: [ComposerKnownPerson]) -> ComposerKnownPerson? {
    let connections = known.filter { $0.uid != nil }
    let target = person.name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(with: trLocale)
    func same(_ a: String?) -> Bool {
        guard let a else { return false }
        return a.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(with: trLocale) == target
    }
    return connections.first { $0.uid == person.uid } ?? connections.first { same($0.name) || same($0.nickname) }
}

/// All people known to Kişilerim (rememberComposerConnections): REAL connections with their nickname if set, followed by local contacts.
@MainActor
func composerKnownPeople() -> [ComposerKnownPerson] {
    let contacts = RealContacts.shared.list
    return RealConnections.shared.people.map { person in
        ComposerKnownPerson(name: person.name, uid: person.uid, pictureVersion: person.pictureVersion,
                            nickname: contacts.first { $0.uid == person.uid }?.nickname)
    } + contacts.filter { $0.uid == nil }.map {
        ComposerKnownPerson(name: $0.name, uid: nil, pictureVersion: nil, nickname: $0.nickname)
    }
}
