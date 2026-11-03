import SwiftUI
import UIKit

/// Vi, the beta ("Lite") cat: RealPetBody.kt's simple cat. It walks in, hops onto a category or card, sits, grooms,
/// yawns or naps, then leaves; hops onto the opened composer, chatters by the mic, and now and then plays a prank.
/// Only drawn over Home; it never blocks taps outside its own body.
@MainActor
@Observable
final class RealPet {
    static let shared = RealPet()
    static let modeKey = "real_pet_mode"

    /// Envelope of the sprite, like Android's 114dp petSize.
    static let size: CGFloat = 114
    private static let perchSink: CGFloat = 7

    private(set) var x: CGFloat = -size
    private(set) var y: CGFloat = 0
    private(set) var frame: String?
    private(set) var facingRight = false
    private(set) var poseScale: CGFloat = 1
    private(set) var heartBurst = 0
    private(set) var cardScratch: RealPetScratch?
    private(set) var nudgedCard: Int64?
    private(set) var nudgeX: CGFloat = 0
    /// "Add to MAMA": replaces the composer's placeholder while the cat sits on it.
    private(set) var placeholder: String?

    @ObservationIgnored var perches: [String: CGRect] = [:]
    @ObservationIgnored private var screen = CGSize(width: 390, height: 844)
    @ObservationIgnored private var script: Task<Void, Never>?
    @ObservationIgnored private var follower: Task<Void, Never>?
    @ObservationIgnored private var spontaneous: Task<Void, Never>?
    @ObservationIgnored private var anchor: String?
    @ObservationIgnored private var anchorFraction: CGFloat = 0.5
    @ObservationIgnored private var lastPerch: String?
    @ObservationIgnored private var nextToken = 0
    @ObservationIgnored private var homeVisible = false
    @ObservationIgnored private var active = true
    @ObservationIgnored private var composerExpanded = false
    @ObservationIgnored private var micActive = false
    @ObservationIgnored private var scriptLevel = 0
    @ObservationIgnored private var scriptId = 0
    @ObservationIgnored private var lastMicVisit = Date.distantPast

    private var enabled: Bool { UserDefaults.standard.string(forKey: Self.modeKey) != "kapali" }
    private var onScreen: Bool { frame != nil }
    private var reduceMotion: Bool { UIAccessibility.isReduceMotionEnabled }
    private var ground: CGFloat { screen.height - 30 - Self.size }

    // MARK: - Events

    func layout(_ size: CGSize) { screen = size }

    func setHome(_ visible: Bool) {
        guard homeVisible != visible else { return }
        homeVisible = visible
        if visible { startSpontaneous() } else { stop() }
    }

    func scene(active now: Bool) {
        guard active != now else { return }
        active = now
        if now {
            startSpontaneous()
            openRoll()
        } else {
            // Claw marks last until the app is left and opened again.
            cardScratch = nil
            stop()
        }
    }

    func modeChanged() { if enabled { startSpontaneous() } else { stop() } }

    func started() {
        startSpontaneous()
        openRoll()
    }

    func composer(expanded: Bool) {
        composerExpanded = expanded
        guard expanded, enabled, homeVisible, onScreen else { return }
        run(level: 2) { pet in
            try await pet.pause(450)
            try await pet.sitOnComposerWhileOpen()
        }
    }

    func mic(active on: Bool) {
        micActive = on
        guard on, enabled, homeVisible, Date().timeIntervalSince(lastMicVisit) > 60, Double.random(in: 0...1) < 0.5 else { return }
        lastMicVisit = Date()
        run(level: 2) { pet in try await pet.listenToMic() }
    }

    func tapped() {
        heartBurst += 1
        guard scriptLevel < 2 else { return }
        run(level: 1) { pet in
            try await pet.sit()
            try await pet.hold(1500)
            try await pet.leave()
        }
    }

    // MARK: - Scheduling

    private func openRoll() {
        guard enabled, homeVisible, active, script == nil else { return }
        if canPrank(), Double.random(in: 0...1) < 0.30 {
            Self.notePrank()
            run(level: 0) { pet in try await pet.prank() }
        } else if Double.random(in: 0...1) < 0.5 {
            run(level: 0) { pet in
                try await pet.pause(Int.random(in: 900...2_000))
                try await pet.visit()
            }
        }
    }

    private func startSpontaneous() {
        guard enabled, homeVisible, active, spontaneous == nil else { return }
        spontaneous = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Int.random(in: 120...300)))
                guard let self, !Task.isCancelled else { return }
                if self.enabled, self.homeVisible, self.active, self.script == nil {
                    self.run(level: 0) { pet in try await pet.visit() }
                }
            }
        }
    }

    private func run(level: Int, _ body: @escaping @MainActor (RealPet) async throws -> Void) {
        guard level >= scriptLevel || script == nil else { return }
        script?.cancel()
        scriptLevel = level
        startFollower()
        scriptId += 1
        let id = scriptId
        script = Task { [weak self] in
            guard let self else { return }
            try? await body(self)
            if id == self.scriptId {
                self.script = nil
                self.scriptLevel = 0
            }
        }
    }

    private func stop() {
        script?.cancel(); script = nil
        scriptLevel = 0
        spontaneous?.cancel(); spontaneous = nil
        follower?.cancel(); follower = nil
        anchor = nil
        frame = nil
        placeholder = nil
    }

    /// Keeps the sitting cat on its perch while the list scrolls under it.
    private func startFollower() {
        guard follower == nil else { return }
        follower = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(16))
                guard let self else { return }
                guard let id = self.anchor else { continue }
                if let target = self.target(id, fraction: self.anchorFraction) {
                    self.x = target.x
                    self.y = target.y
                } else {
                    self.anchor = nil
                    self.script?.cancel(); self.script = nil
                    self.scriptLevel = 0
                    self.frame = nil
                }
            }
        }
    }

    private func pause(_ ms: Int) async throws {
        try await Task.sleep(for: .milliseconds(ms))
    }

    // MARK: - Geometry

    private func target(_ id: String, fraction: CGFloat) -> CGPoint? {
        guard let bounds = perches[id] else { return nil }
        let inset = min(bounds.width * 0.1, 16)
        let left = bounds.minX + inset - Self.size * 0.2
        let right = bounds.maxX - inset - Self.size * 0.8
        return CGPoint(x: left + max(right - left, 0) * fraction, y: bounds.minY - Self.size + Self.perchSink)
    }

    private func inView(_ id: String) -> Bool {
        guard let bounds = perches[id] else { return false }
        let top = bounds.minY - Self.size + Self.perchSink
        return top > -Self.size * 0.1 && top < ground && bounds.maxX >= 0 && bounds.minX <= screen.width
    }

    /// On Home: the top category 40%, the other categories 35%, the cards 25%, and not the same place twice in a row.
    private func pickPerch() -> String? {
        let all = perches.keys.filter { $0 != "composer" && inView($0) }
        let open = all.filter { $0 != lastPerch }
        let pool = open.isEmpty ? all : open
        guard !pool.isEmpty else { return nil }
        let categories = pool.filter { $0.hasPrefix("category:") }
        let cards = pool.filter { $0.hasPrefix("card:") }
        let top = categories.min { (perches[$0]?.minY ?? 0) < (perches[$1]?.minY ?? 0) }
        let roll = Double.random(in: 0...1)
        let picked: String?
        if roll < 0.40, let top { picked = top }
        else if roll < 0.75, !categories.isEmpty { picked = categories.randomElement() }
        else { picked = cards.randomElement() ?? categories.randomElement() }
        lastPerch = picked
        return picked
    }

    // MARK: - Behaviours

    private func visit() async throws {
        try await enterIfNeeded(running: false)
        try await stand()
        try await pause(Int.random(in: 300...600))
        if let perch = pickPerch() {
            try await move(to: perch, fraction: CGFloat.random(in: 0.2...0.8))
        } else {
            try await walk(toX: CGFloat.random(in: 16...(max(screen.width - Self.size - 16, 17))), running: false)
        }
        try await settleAndLeave()
    }

    /// Sits, does one small thing, goes.
    private func settleAndLeave() async throws {
        try await sit()
        try await hold(Int.random(in: 1_200...2_200))
        let roll = Double.random(in: 0...1)
        if !reduceMotion {
            if roll < 0.35 { try await groom() }
            else if roll < 0.62 { try await yawn() }
            else {
                setFrame("real_pet_sleep_1", scale: 1)
                try await pause(Int.random(in: 4_000...7_000))
                setFrame("real_pet_call_1", scale: 1)
            }
        }
        try await hold(Int.random(in: 800...1_400))
        try await leave()
    }

    private func sitOnComposerWhileOpen() async throws {
        guard target("composer", fraction: 0.5) != nil else { return }
        try await enterIfNeeded(running: true)
        try await move(to: "composer", fraction: CGFloat.random(in: 0.55...0.85), direct: true)
        try await sit()
        var waited = 0
        while composerExpanded && waited < 30_000 {
            try await hold(250)
            waited += 250
        }
        try await leave()
    }

    private func listenToMic() async throws {
        let dock = perches["composer"]
        try await enterIfNeeded(running: true)
        anchor = nil
        if y < ground - 2 { try await hop(toX: x, toY: ground, down: true) }
        try await walk(toX: max(screen.width - Self.size * 1.35, 16), running: true)
        if let dock { try await hop(toX: x, toY: dock.minY - Self.size + Self.perchSink, down: false) }
        facingRight = true
        try await sit()
        // Chatter: quick syllables cut from the meow, short gaps, now and then a breath.
        while micActive {
            let open = Int.random(in: 5...12)
            let up = stride(from: 1, through: open, by: 2).map { "real_pet_meow_\($0)" }
            for name in up + up.reversed().dropFirst() {
                frame = name
                try await pause(30)
            }
            frame = "real_pet_meow_1"
            try await pause(Double.random(in: 0...1) < 0.15 ? Int.random(in: 380...700) : Int.random(in: 40...160))
        }
        setFrame("real_pet_call_1", scale: 1)
        try await hold(Int.random(in: 2_000...3_500))
        try await leave()
    }

    private func prank() async throws {
        let roll = Double.random(in: 0...1)
        if roll < 0.5 {
            let seen = UserDefaults.standard.bool(forKey: "pet_scratch_seen")
            UserDefaults.standard.set(true, forKey: "pet_scratch_seen")
            if seen && Bool.random() {
                // The marks are just there when the app opens; only once it has been caught at it, or they read as a bug.
                if let card = try await awaitVisibleCard() { scratch(card, animate: false) }
            } else {
                try await pawAtVisibleCard { pet, card in pet.scratch(card, animate: true) }
            }
        } else if roll < 0.75 {
            try await pawAtVisibleCard { pet, card in pet.nudge(card) }
        } else {
            try await renameAddToMama()
        }
    }

    private func awaitVisibleCard() async throws -> String? {
        for _ in 0..<15 {
            if let card = perches.keys.filter({ $0.hasPrefix("card:") && inView($0) }).randomElement() { return card }
            try await pause(200)
        }
        return nil
    }

    private func pawAtVisibleCard(_ onPaw: @escaping @MainActor (RealPet, Int64) -> Void) async throws {
        guard let perch = try await awaitVisibleCard(), let id = Int64(perch.dropFirst("card:".count)) else { return }
        try await enterIfNeeded(running: true)
        try await move(to: perch, fraction: CGFloat.random(in: 0.2...0.8), running: true)
        try await sit()
        try await pause(Int.random(in: 300...600))
        let paw = (1...21).map { "real_pet_paw_\($0)" }
        for (index, name) in paw.enumerated() {
            frame = name
            if index == paw.count * 2 / 5 { onPaw(self, id) }
            try await pause(39)
        }
        setFrame("real_pet_call_1", scale: 1)
        try await hold(Int.random(in: 1_200...2_200))
        try await leave(running: true)
    }

    /// Sits on the composer, and it says "Add to MAMA" until the cat leaves.
    private func renameAddToMama() async throws {
        guard target("composer", fraction: 0.5) != nil else { return }
        defer { placeholder = nil }
        try await enterIfNeeded(running: true)
        try await move(to: "composer", fraction: CGFloat.random(in: 0.45...0.85))
        try await sit()
        for index in 1...21 {
            frame = "real_pet_paw_\(index)"
            if index == 9 { placeholder = "Add to MAMA" }
            try await pause(39)
        }
        setFrame("real_pet_call_1", scale: 1)
        try await hold(Int.random(in: 8_000...14_000))
        placeholder = nil
        try await leave()
    }

    private func scratch(_ card: String, animate: Bool) {
        guard let id = Int64(card.dropFirst("card:".count)) else { return }
        scratch(id, animate: animate)
    }

    private func scratch(_ card: Int64, animate: Bool) {
        nextToken += 1
        cardScratch = RealPetScratch(cardId: card, seed: UInt64.random(in: 1...UInt64.max), token: nextToken, animate: animate && !reduceMotion)
    }

    private func nudge(_ card: Int64) {
        nudgedCard = card
        let distance: CGFloat = Bool.random() ? 14 : -14
        withAnimation(.easeOut(duration: 0.13)) { nudgeX = distance }
        Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(130))
            withAnimation(.spring(response: 0.45, dampingFraction: 0.5)) { self?.nudgeX = 0 }
        }
    }

    // MARK: - Motion

    private func enterIfNeeded(running: Bool) async throws {
        guard !onScreen else { return }
        let fromRight = Bool.random()
        anchor = nil
        x = fromRight ? screen.width : -Self.size
        y = ground
        facingRight = !fromRight
        setFrame("real_pet_walk_1", scale: 1.12)
        try await walk(toX: fromRight ? screen.width - Self.size - 24 : 24, running: running)
    }

    private func walk(toX: CGFloat, running: Bool) async throws {
        anchor = nil
        let distance = abs(toX - x)
        guard distance > 2 else { return }
        if reduceMotion { x = toX; return }
        facingRight = toX > x
        setScale(1.12)
        let speed: CGFloat = running ? 190 : 80
        let frameMs = running ? 18 : 30
        let start = x
        let duration = Double(distance / speed)
        let began = Date()
        var index = 1
        while true {
            let progress = CGFloat(min(Date().timeIntervalSince(began) / duration, 1))
            x = start + (toX - start) * progress
            frame = "real_pet_walk_\(index)"
            index = index % 24 + 1
            if progress >= 1 { break }
            try await pause(frameMs)
        }
    }

    private func move(to id: String, fraction: CGFloat, direct: Bool = false, running: Bool = false) async throws {
        guard let spot = target(id, fraction: fraction) else { return }
        if !direct {
            if y < ground - 2 { try await hop(toX: x, toY: ground, down: true) }
            try await walk(toX: spot.x, running: running)
        }
        guard let now = target(id, fraction: fraction) else { return }
        try await hop(toX: now.x, toY: now.y, down: now.y > y)
        anchor = id
        anchorFraction = fraction
    }

    private func hop(toX: CGFloat, toY: CGFloat, down: Bool) async throws {
        anchor = nil
        if reduceMotion { x = toX; y = toY; return }
        setScale(1)
        if abs(toX - x) > 2 { facingRight = toX > x }
        let startX = x, startY = y
        let peak = min(startY, toY) - 34
        let duration = min(0.42 + Double(abs(toY - startY)) / 1_400, 0.8)
        let name = down ? "real_pet_hopdown_" : "real_pet_hop_"
        let began = Date()
        while true {
            let t = CGFloat(min(Date().timeIntervalSince(began) / duration, 1))
            x = startX + (toX - startX) * t
            // Quadratic through start, peak and end.
            let mid = 2 * peak - (startY + toY) / 2
            y = (1 - t) * (1 - t) * startY + 2 * (1 - t) * t * mid + t * t * toY
            frame = name + "\(min(Int(t * 31) + 1, 31))"
            if t >= 1 { break }
            try await pause(16)
        }
    }

    private func stand() async throws {
        setScale(1.18)
        frame = "real_pet_sitdown_6"
    }

    private func sit() async throws {
        setScale(1)
        if !reduceMotion {
            for index in 6...16 {
                frame = "real_pet_sitdown_\(index)"
                try await pause(30)
            }
        }
        frame = "real_pet_call_1"
    }

    /// Held on the sit, blinking every 3–6 s for ~180 ms.
    private func hold(_ ms: Int) async throws {
        var left = ms
        while left > 0 {
            let next = min(left, Int.random(in: 3_000...6_000))
            try await pause(next)
            left -= next
            if left > 0, !reduceMotion, frame == "real_pet_call_1" {
                frame = "real_pet_purr_1"
                try await pause(180)
                frame = "real_pet_call_1"
                left -= 180
            }
        }
    }

    private func groom() async throws {
        var order = Array(1...4)
        let lick = Array(5...10) + Array((6...9).reversed())
        let wash = Array(12...18) + Array((13...17).reversed())
        for _ in 0..<Int.random(in: 3...6) { order += lick }
        order += Array(5...11)
        for _ in 0..<Int.random(in: 2...4) { order += wash }
        order += Array(12...21)
        for index in order {
            frame = "real_pet_groom_\(index)"
            try await pause(60)
        }
        frame = "real_pet_call_1"
    }

    private func yawn() async throws {
        for offset in 0..<7 {
            frame = "real_pet_wake_\(21 - offset)"
            try await pause(30)
        }
        frame = "real_pet_wake_13"
        try await pause(1_200)
        for offset in 0..<8 {
            frame = "real_pet_wake_\(14 + offset)"
            try await pause(30)
        }
        frame = "real_pet_call_1"
    }

    private func leave(running: Bool = false) async throws {
        anchor = nil
        if !reduceMotion {
            for index in (6...15).reversed() {
                frame = "real_pet_sitdown_\(index)"
                try await pause(30)
            }
        }
        if y < ground - 2 { try await hop(toX: x, toY: ground, down: true) }
        let toRight = x + Self.size / 2 > screen.width / 2
        try await walk(toX: toRight ? screen.width + 4 : -Self.size - 4, running: running)
        frame = nil
    }

    private func setFrame(_ name: String, scale: CGFloat) {
        setScale(scale)
        frame = name
    }

    private func setScale(_ scale: CGFloat) {
        guard poseScale != scale else { return }
        if reduceMotion { poseScale = scale } else { withAnimation(.easeInOut(duration: 0.26)) { poseScale = scale } }
    }

    // MARK: - Daily prank limit

    private func canPrank() -> Bool {
        let d = UserDefaults.standard
        return d.string(forKey: "pet_prank_day") != Self.today || d.integer(forKey: "pet_prank_today") < 1
    }

    private static func notePrank() {
        let d = UserDefaults.standard
        let count = d.string(forKey: "pet_prank_day") == today ? d.integer(forKey: "pet_prank_today") : 0
        d.set(today, forKey: "pet_prank_day")
        d.set(count + 1, forKey: "pet_prank_today")
    }

    private static var today: String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        return "\(c.year ?? 0)-\(c.month ?? 0)-\(c.day ?? 0)"
    }
}

/// The claw marks the cat left on one card. Only drawn: nothing is saved, and leaving the app clears them.
struct RealPetScratch: Equatable {
    let cardId: Int64
    let seed: UInt64
    let token: Int
    let animate: Bool
}

private enum RealPetSprites {
    static let cache = NSCache<NSString, UIImage>()

    static func image(_ name: String) -> UIImage? {
        if let hit = cache.object(forKey: name as NSString) { return hit }
        guard let path = Bundle.main.path(forResource: name, ofType: "png") ?? Bundle.main.path(forResource: name, ofType: "png", inDirectory: "Pet"),
              let image = UIImage(contentsOfFile: path) else { return nil }
        cache.setObject(image, forKey: name as NSString)
        return image
    }
}

/// Over everything on Home; only the cat's own body takes taps.
struct RealPetLayer: View {
    private var pet: RealPet { RealPet.shared }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                Color.clear.allowsHitTesting(false)
                if let name = pet.frame, let image = RealPetSprites.image(name) {
                    Image(uiImage: image)
                        .resizable()
                        .frame(width: 90, height: 78.75)
                        .scaleEffect(x: pet.facingRight ? -pet.poseScale : pet.poseScale, y: pet.poseScale, anchor: .bottom)
                        .frame(width: RealPet.size, height: RealPet.size - 3, alignment: .bottom)
                        .padding(.bottom, 3)
                        .contentShape(Rectangle().inset(by: 20))
                        .onTapGesture { pet.tapped() }
                        .overlay(alignment: .top) { RealPetHearts(burst: pet.heartBurst) }
                        .offset(x: pet.x, y: pet.y)
                }
            }
            .onAppear { pet.layout(geo.size); pet.started() }
            .onChange(of: geo.size) { _, size in pet.layout(size) }
        }
    }
}

private struct RealPetHearts: View {
    let burst: Int
    @State private var rise = false

    var body: some View {
        ZStack {
            if burst > 0 {
                ForEach(0..<3, id: \.self) { index in
                    Image(systemName: "heart.fill")
                        .font(.system(size: 13 + CGFloat(index) * 2))
                        .foregroundStyle(Color(argb: 0xFFFF6B8A))
                        .offset(x: CGFloat(index - 1) * 14, y: rise ? -46 - CGFloat(index) * 8 : 0)
                        .opacity(rise ? 0 : 1)
                }
            }
        }
        .id(burst)
        .allowsHitTesting(false)
        .onChange(of: burst, initial: false) { _, _ in
            rise = false
            withAnimation(.easeOut(duration: 0.8)) { rise = true }
        }
    }
}

extension View {
    /// Reports this view's place on screen as a spot the cat can sit on.
    func realPetPerch(_ id: String) -> some View {
        background {
            GeometryReader { geo in
                Color.clear
                    .onChange(of: geo.frame(in: .global), initial: true) { _, frame in RealPet.shared.perches[id] = frame }
                    .onDisappear { RealPet.shared.perches[id] = nil }
            }
        }
    }

    /// The cat's claw marks and paw push on this card, when it has any.
    func realPetCardMarks(_ cardId: Int64, lightCard: Bool, shape: some Shape) -> some View {
        modifier(RealPetCardMarks(cardId: cardId, lightCard: lightCard, shape: AnyShape(shape)))
    }
}

private struct RealPetCardMarks: ViewModifier {
    let cardId: Int64
    let lightCard: Bool
    let shape: AnyShape

    func body(content: Content) -> some View {
        let pet = RealPet.shared
        content
            .overlay {
                if let scratch = pet.cardScratch, scratch.cardId == cardId {
                    RealPetScratchView(scratch: scratch, lightCard: lightCard).clipShape(shape).allowsHitTesting(false)
                }
            }
            .offset(x: pet.nudgedCard == cardId ? pet.nudgeX : 0)
    }
}

private struct RealPetScratchView: View {
    let scratch: RealPetScratch
    let lightCard: Bool
    @State private var reveal: CGFloat = 0

    var body: some View {
        Canvas { context, size in
            drawScratch(&context, size: size)
        }
        .task(id: scratch.token) {
            guard scratch.animate else { reveal = 1; return }
            reveal = 0
            for step in 1...17 {
                try? await Task.sleep(for: .milliseconds(16))
                let t = CGFloat(step) / 17
                reveal = 1 - (1 - t) * (1 - t)
            }
        }
    }

    /// The seed decides everything, so the marks stay the same while they are up: one of three looks, mostly on the right.
    private func drawScratch(_ context: inout GraphicsContext, size: CGSize) {
        var random = SeededRandom(state: scratch.seed)
        func between(_ a: CGFloat, _ b: CGFloat) -> CGFloat { a + CGFloat.random(in: 0...1, using: &random) * (b - a) }
        let style = Int.random(in: 0..<3, using: &random)
        let onRight = CGFloat.random(in: 0...1, using: &random) < 0.85
        let centerX = size.width * (onRight ? between(0.62, 0.9) : between(0.3, 0.62))
        let centerY = size.height * between(0.3, 0.7)
        let length = min(between(26, 36), size.height * 0.8)
        let angle = between(1.95, 2.3)
        let dirX = cos(angle), dirY = sin(angle)
        let claws = CGFloat.random(in: 0...1, using: &random) < 0.35 ? 3 : 4
        let gap = between(5.5, 7)
        let halfWidth: CGFloat = style == 0 ? 1.5 : (style == 1 ? 1.1 : 0.65)
        let main: Color = style == 1
            ? (lightCard ? Color.black.opacity(0.22) : Color.white.opacity(0.6))
            : (lightCard ? Color.black.opacity(0.55) : Color.black.opacity(0.72))
        let rim = lightCard ? Color.white.opacity(0.9) : Color.white.opacity(0.32)
        for index in 0..<claws {
            let offset = (CGFloat(index) - CGFloat(claws - 1) / 2) * gap
            let edge = index == 0 || index == claws - 1
            let clawLength = length * between(0.78, 1.06) * (edge ? 0.86 : 1)
            let slide = between(-3, 3)
            let bend = between(-2, 2)
            // Each claw starts a little after the one before it.
            let progress = min(max(reveal * (1 + 0.35 * CGFloat(claws - 1)) - CGFloat(index) * 0.35, 0), 1)
            guard progress > 0 else { continue }
            let startX = centerX - dirY * offset + dirX * (slide - clawLength / 2)
            let startY = centerY + dirX * offset + dirY * (slide - clawLength / 2)
            let shown = clawLength * progress
            let endX = startX + dirX * shown, endY = startY + dirY * shown
            let midX = (startX + endX) / 2 - dirY * bend, midY = (startY + endY) / 2 + dirX * bend
            func claw(_ shift: CGFloat) -> Path {
                var p = Path()
                p.move(to: CGPoint(x: startX - dirY * shift, y: startY + dirX * shift))
                p.addQuadCurve(to: CGPoint(x: endX - dirY * shift, y: endY + dirX * shift),
                               control: CGPoint(x: midX - dirY * (shift + halfWidth), y: midY + dirX * (shift + halfWidth)))
                p.addQuadCurve(to: CGPoint(x: startX - dirY * shift, y: startY + dirX * shift),
                               control: CGPoint(x: midX - dirY * (shift - halfWidth), y: midY + dirX * (shift - halfWidth)))
                p.closeSubpath()
                return p
            }
            if style != 1 { context.fill(claw(-0.7), with: .color(rim)) }
            context.fill(claw(0), with: .color(main))
        }
    }
}

private struct SeededRandom: RandomNumberGenerator {
    var state: UInt64
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}
