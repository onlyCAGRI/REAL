import AVFoundation
import CryptoKit
import Foundation

/// MusicBeats.kt: how strong a song's bass is at each moment, read from its preview ahead of time.
/// A smooth curve of the real sound rather than picked hits, so the smoke cannot fall out of step with the music.
struct BeatTrack: Sendable {
    let curve: [Float]

    func level(_ positionMillis: Int64) -> Float {
        guard !curve.isEmpty else { return 0.2 }
        // The curve rises over 100 ms, so it looks a little ahead to land with the sound.
        let at = min(Float(max(positionMillis + 60, 0)) / 10, Float(curve.count - 1))
        let index = Int(at)
        let next = min(index + 1, curve.count - 1)
        return curve[index] + (curve[next] - curve[index]) * (at - Float(index))
    }
}

extension AVPlayer {
    var positionMillis: Int64 {
        let seconds = currentTime().seconds
        return seconds.isFinite ? Int64(seconds * 1000) : 0
    }
}

/// Songs on cards keep their smoke on disk until the card is gone; the deck's latest songs stay in the cache.
/// Only the small curve is kept: the audio file is deleted once it is read, and playback still streams.
actor MusicBeats {
    static let shared = MusicBeats()

    private static let hopMillis = 10
    private static let sampleRate = 24_000
    private static let maxBytes = 8 * 1024 * 1024
    private static let deckLimit = 60
    private static let memoryLimit = 24
    private static let aheadAtOnce = 3

    private var memory: [String: BeatTrack] = [:]
    private var memoryOrder: [String] = []
    private var running: [String: Task<BeatTrack?, Never>] = [:]
    private var waiting: [String] = []
    private var ahead = 0
    private var cardsTask: Task<Void, Never>?

    /// The preview's bass curve, analysed once per URL; nil when it could not be read.
    func track(_ url: String) async -> BeatTrack? {
        if let known = memory[url] { return known }
        if let current = running[url] { return await current.value }
        // Not tied to the caller: a swipe must not throw away work that is nearly done.
        let task = Task.detached(priority: .utility) { await Self.load(url) }
        running[url] = task
        let found = await task.value
        running[url] = nil
        if let found { remember(url, found) }
        return found
    }

    /// Readies these previews in the background, a few at a time, the latest request first.
    nonisolated func prepare(_ urls: [String]) {
        Task { await enqueue(urls) }
    }

    /// Keeps the smoke of every song on a card, without waiting for a press, and removes it when the card is gone.
    nonisolated func cardsChanged(_ urls: [String]) {
        Task { await scheduleCards(urls) }
    }

    private func remember(_ url: String, _ track: BeatTrack) {
        memory[url] = track
        memoryOrder.removeAll { $0 == url }
        memoryOrder.append(url)
        if memoryOrder.count > Self.memoryLimit { memory[memoryOrder.removeFirst()] = nil }
    }

    private func enqueue(_ urls: [String]) {
        var seen = Set<String>()
        let fresh = urls.filter { url in
            url.hasPrefix("https://") && memory[url] == nil && running[url] == nil && !waiting.contains(url) && seen.insert(url).inserted
        }
        waiting.insert(contentsOf: fresh, at: 0)
        pump()
    }

    private func pump() {
        while ahead < Self.aheadAtOnce, !waiting.isEmpty {
            let url = waiting.removeFirst()
            ahead += 1
            Task {
                _ = await track(url)
                aheadFinished()
            }
        }
    }

    private func aheadFinished() {
        ahead -= 1
        pump()
    }

    private func scheduleCards(_ urls: [String]) {
        cardsTask?.cancel()
        cardsTask = Task {
            // A burst of saves only runs once.
            try? await Task.sleep(for: .milliseconds(1500))
            guard !Task.isCancelled else { return }
            await keepCards(urls)
        }
    }

    private func keepCards(_ urls: [String]) async {
        let unique = Array(Set(urls.filter { $0.hasPrefix("https://") }))
        let wanted = Set(unique.map(Self.digest))
        let directory = Self.keptDirectory
        for name in (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? [] where !wanted.contains(name) {
            try? FileManager.default.removeItem(at: directory.appendingPathComponent(name))
        }
        for url in unique {
            guard !Task.isCancelled else { return }
            let kept = Self.keptFile(url)
            if FileManager.default.fileExists(atPath: kept.path) { continue }
            // A failure (no connection) is tried again at the next change or start.
            if let found = await track(url) {
                let ready = Self.deckFile(url)
                if (try? FileManager.default.moveItem(at: ready, to: kept)) == nil { Self.save(found, to: kept) }
            }
        }
    }

    // MARK: Disk

    private static func digest(_ url: String) -> String {
        SHA256.hash(data: Data(url.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    private static func directory(_ base: FileManager.SearchPathDirectory) -> URL {
        let root = FileManager.default.urls(for: base, in: .userDomainMask)[0].appendingPathComponent("music_beats", isDirectory: true)
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        return root
    }

    private static var keptDirectory: URL { directory(.applicationSupportDirectory) }
    private static var deckDirectory: URL { directory(.cachesDirectory) }
    private static func keptFile(_ url: String) -> URL { keptDirectory.appendingPathComponent(digest(url)) }
    private static func deckFile(_ url: String) -> URL { deckDirectory.appendingPathComponent(digest(url)) }

    /// One byte per point (0...1). An empty file is a song with no bass to follow.
    private static func save(_ track: BeatTrack, to file: URL) {
        let bytes = track.curve.map { UInt8(min(max($0, 0), 1) * 255) }
        try? Data(bytes).write(to: file, options: .atomic)
    }

    private static func read(_ file: URL) -> BeatTrack? {
        guard let data = try? Data(contentsOf: file) else { return nil }
        return BeatTrack(curve: data.map { Float($0) / 255 })
    }

    /// The deck's songs come and go: only the latest stay.
    private static func trimDeck() {
        let directory = deckDirectory
        guard let files = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.contentModificationDateKey]),
              files.count > deckLimit else { return }
        func modified(_ file: URL) -> Date { (try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate ?? .distantPast }
        for file in files.sorted(by: { modified($0) > modified($1) }).dropFirst(deckLimit) { try? FileManager.default.removeItem(at: file) }
    }

    private static func load(_ url: String) async -> BeatTrack? {
        if let kept = read(keptFile(url)) { return kept }
        if let ready = read(deckFile(url)) { return ready }
        guard let found = await analyse(url) else { return nil }
        save(found, to: deckFile(url))
        trimDeck()
        return found
    }

    // MARK: Analysis

    private static func analyse(_ url: String) async -> BeatTrack? {
        guard url.hasPrefix("https://"), let remote = URL(string: url),
              let (downloaded, response) = try? await URLSession.shared.download(from: remote) else { return nil }
        // The reader recognises the file by its extension.
        let suffix = remote.pathExtension.isEmpty ? "m4a" : remote.pathExtension
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + "." + suffix)
        defer {
            try? FileManager.default.removeItem(at: downloaded)
            try? FileManager.default.removeItem(at: file)
        }
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode),
              let size = (try? downloaded.resourceValues(forKeys: [.fileSizeKey]))?.fileSize, size <= maxBytes,
              (try? FileManager.default.moveItem(at: downloaded, to: file)) != nil,
              let bands = await decode(file) else { return nil }
        return bassCurve(bands)
    }

    /// RBJ low or high pass; a band is one of each.
    private struct Biquad {
        private let b0, b1, b2, a1, a2: Double
        private var x1 = 0.0, x2 = 0.0, y1 = 0.0, y2 = 0.0

        init(_ frequency: Double, highPass: Bool) {
            let w = 2 * Double.pi * frequency / Double(MusicBeats.sampleRate)
            let alpha = sin(w) / (2 * 0.707)
            let c = cos(w)
            let a0 = 1 + alpha
            if highPass {
                b0 = (1 + c) / 2 / a0; b1 = -(1 + c) / a0; b2 = b0
            } else {
                b0 = (1 - c) / 2 / a0; b1 = (1 - c) / a0; b2 = b0
            }
            a1 = -2 * c / a0
            a2 = (1 - alpha) / a0
        }

        mutating func run(_ x: Double) -> Double {
            let y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    private struct Bands { var kick: [Float] = [], bass: [Float] = [], full: [Float] = [] }

    /// The song's loudness every 10 ms: kick (40-120 Hz), bass (150-250 Hz) and everything.
    private static func decode(_ file: URL) async -> Bands? {
        let asset = AVURLAsset(url: file)
        guard let audio = try? await asset.loadTracks(withMediaType: .audio).first,
              let reader = try? AVAssetReader(asset: asset) else { return nil }
        let output = AVAssetReaderTrackOutput(track: audio, outputSettings: [
            AVFormatIDKey: kAudioFormatLinearPCM, AVLinearPCMBitDepthKey: 32, AVLinearPCMIsFloatKey: true,
            AVLinearPCMIsBigEndianKey: false, AVLinearPCMIsNonInterleaved: false,
            AVSampleRateKey: sampleRate, AVNumberOfChannelsKey: 1
        ])
        guard reader.canAdd(output) else { return nil }
        reader.add(output)
        guard reader.startReading() else { return nil }

        var kickHigh = Biquad(40, highPass: true), kickLow = Biquad(120, highPass: false)
        var bassHigh = Biquad(150, highPass: true), bassLow = Biquad(250, highPass: false)
        let hop = sampleRate * hopMillis / 1000
        var bands = Bands()
        var sumKick = 0.0, sumBass = 0.0, sumFull = 0.0, inHop = 0
        while let buffer = output.copyNextSampleBuffer() {
            guard let block = CMSampleBufferGetDataBuffer(buffer) else { continue }
            let length = CMBlockBufferGetDataLength(block)
            var samples = [Float](repeating: 0, count: length / MemoryLayout<Float>.size)
            let copied = samples.withUnsafeMutableBytes { CMBlockBufferCopyDataBytes(block, atOffset: 0, dataLength: length, destination: $0.baseAddress!) }
            guard copied == kCMBlockBufferNoErr else { return nil }
            for sample in samples {
                let mono = Double(sample)
                let k = kickLow.run(kickHigh.run(mono))
                let b = bassLow.run(bassHigh.run(mono))
                sumKick += k * k; sumBass += b * b; sumFull += mono * mono
                inHop += 1
                if inHop == hop {
                    bands.kick.append(Float((sumKick / Double(hop)).squareRoot()))
                    bands.bass.append(Float((sumBass / Double(hop)).squareRoot()))
                    bands.full.append(Float((sumFull / Double(hop)).squareRoot()))
                    sumKick = 0; sumBass = 0; sumFull = 0; inHop = 0
                }
            }
        }
        return reader.status == .completed ? bands : nil
    }

    /// The bass and kick together, spread over each song's own range; a song with almost no bass has no curve.
    private static func bassCurve(_ bands: Bands) -> BeatTrack {
        let size = bands.full.count
        guard size * hopMillis >= 1000 else { return BeatTrack(curve: []) }
        let low = (0..<size).map { bands.kick[$0] + 0.6 * bands.bass[$0] }
        let sorted = low.sorted()
        let quiet = sorted[size * 20 / 100]
        let loud = sorted[size * 95 / 100]
        let average = bands.full.reduce(0, +) / Float(size)
        guard loud >= average * 0.03, loud > quiet else { return BeatTrack(curve: []) }
        let rise = 1 - exp(-Float(hopMillis) / 100)
        let fall = 1 - exp(-Float(hopMillis) / 500)
        var now: Float = 0
        return BeatTrack(curve: low.map { value in
            let target = pow(min(max((value - quiet) / (loud - quiet), 0), 1), 1.2)
            now += (target - now) * (target > now ? rise : fall)
            return 0.12 + 0.88 * now
        })
    }
}
