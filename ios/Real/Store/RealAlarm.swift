import SwiftUI
import AVFoundation
import AudioToolbox
import AlarmKit
import AppIntents
import ActivityKit

/// RealAlarm.kt on iOS. An alarm card rings as a system alarm (AlarmKit, iOS 26+): it breaks through silent mode
/// and Focus and shows Apple's alarm UI with our title, colour, "Kapat" and "Aç". "Aç" — or the alarm going off
/// while REAL is open — brings up REAL's own alarm page ([AlarmScreen]), which rings until it is slid away.
/// Before iOS 26 an alarm card falls back to a time-sensitive notification.
enum RealAlarmSettings {
    static let snoozeChoices = [5, 10, 15, 20]
    private static let snoozeKey = "realAlarmSnoozeMinutes"

    static var snoozeMinutes: Int {
        get { let v = UserDefaults.standard.integer(forKey: snoozeKey); return snoozeChoices.contains(v) ? v : 10 }
        set { UserDefaults.standard.set(newValue, forKey: snoozeKey) }
    }

    /// No category: one of five tones, fixed per card (mor, pembe, turuncu, turkuaz, yeşil), as on Android.
    static let fallbackTones: [UInt32] = [0xFF7C5CFF, 0xFFEC4899, 0xFFFF7A45, 0xFF2DD4CF, 0xFF55D3B4]
}

/// One ringing alarm as REAL's alarm page shows it.
struct RingingAlarm: Identifiable, Codable, Equatable {
    var id: Int64
    var title: String
    var subtitle: String
    /// The category's accent index; nil = a fallback tone picked by the card id.
    var accentIndex: Int?
    var at: EpochMillis

    var color: Color {
        paletteColor(accentIndex)
            ?? Color(argb: RealAlarmSettings.fallbackTones[Int(UInt64(bitPattern: id) % UInt64(RealAlarmSettings.fallbackTones.count))])
    }

    init(item: ReminderItem, categories: [CustomCategory], at: EpochMillis) {
        let category = item.customCategoryId.flatMap { cid in categories.first { $0.id == cid } }
        id = item.id
        title = item.text.split(separator: "\n").first.map(String.init) ?? "Alarm"
        if title.trimmingCharacters(in: .whitespaces).isEmpty { title = "Alarm" }
        subtitle = category?.name ?? "Alarm"
        accentIndex = category?.accentStart
        self.at = at
    }
}

/// Presents REAL's alarm page and plays its sound.
@MainActor @Observable
final class AlarmCenter {
    static let shared = AlarmCenter()
    var ringing: RingingAlarm?
    private let tone = AlarmTonePlayer()

    func present(_ alarm: RingingAlarm) {
        ringing = alarm
        tone.start()
    }

    /// The slide: 0 = start (full volume) … 1 = end (silent).
    func slide(_ progress: Double) { tone.volume = Float((1 - progress) * (1 - progress)) }

    func stop() {
        tone.stop()
        ringing = nil
    }

    /// Rings again after the snooze length (a system alarm on iOS 26+, otherwise a notification).
    func snooze() -> Date {
        let at = Date().addingTimeInterval(TimeInterval(RealAlarmSettings.snoozeMinutes * 60))
        if let alarm = ringing {
            var again = alarm
            again.at = EpochMillis(at.timeIntervalSince1970 * 1000)
            if #available(iOS 26.0, *), RealAlarmKit.authorized { RealAlarmKit.scheduleSnooze(again) }
            else { NotificationScheduler.scheduleAlarmFallback(again) }
        }
        stop()
        return at
    }
}

// MARK: AlarmKit

@available(iOS 26.0, *)
struct RealAlarmMetadata: AlarmMetadata {
    var reminderId: Int64
}

@available(iOS 26.0, *)
enum RealAlarmKit {
    static let occurrences = 8
    private static let snoozeIndex = 15
    private static let infoKey = "realAlarmInfo"

    static var authorized: Bool { AlarmManager.shared.authorizationState == .authorized }

    /// The system's own permission prompt (the first time an alarm is picked).
    @discardableResult
    static func requestAuthorization() async -> Bool {
        if authorized { return true }
        return (try? await AlarmManager.shared.requestAuthorization()) == .authorized
    }

    /// A stable alarm id per card and occurrence: "REALALR" + occurrence + card id.
    static func uuid(_ id: Int64, _ index: Int) -> UUID {
        let b = withUnsafeBytes(of: id.bigEndian, Array.init)
        return UUID(uuid: (0x52, 0x45, 0x41, 0x4C, 0x41, 0x4C, 0x52, UInt8(index), b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7]))
    }

    static func schedule(_ item: ReminderItem, times: [EpochMillis], categories: [CustomCategory]) {
        cancel(item.id, keepSnooze: true)
        guard authorized else { return }
        for (i, time) in times.prefix(occurrences).enumerated() {
            add(RingingAlarm(item: item, categories: categories, at: time), index: i)
        }
    }

    static func scheduleSnooze(_ alarm: RingingAlarm) { add(alarm, index: snoozeIndex) }

    private static func add(_ alarm: RingingAlarm, index: Int) {
        let id = uuid(alarm.id, index)
        remember(alarm, for: id)
        let stop = AlarmButton(text: "\(t("Kapat"))", textColor: .white, systemImageName: "stop.fill")
        let open = AlarmButton(text: "\(t("Aç"))", textColor: .white, systemImageName: "arrow.up.forward.app")
        let alert = AlarmPresentation.Alert(title: "\(alarm.title)", stopButton: stop, secondaryButton: open, secondaryButtonBehavior: .custom)
        let attributes = AlarmAttributes<RealAlarmMetadata>(presentation: AlarmPresentation(alert: alert),
                                                            metadata: RealAlarmMetadata(reminderId: alarm.id), tintColor: alarm.color)
        let configuration = AlarmManager.AlarmConfiguration<RealAlarmMetadata>(
            schedule: .fixed(alarm.at.date), attributes: attributes,
            secondaryIntent: OpenRealAlarmIntent(alarmId: id.uuidString))
        Task { _ = try? await AlarmManager.shared.schedule(id: id, configuration: configuration) }
    }

    /// keepSnooze: the card is only being re-set (edited), so a snooze from the alarm page still rings.
    static func cancel(_ id: Int64, keepSnooze: Bool = false) {
        for i in Array(0..<occurrences) + (keepSnooze ? [] : [snoozeIndex]) { try? AlarmManager.shared.cancel(id: uuid(id, i)) }
    }

    /// Before re-adding every card: snoozes and a ringing alarm are not part of any card's schedule, so they stay.
    static func cancelAll() {
        for alarm in (try? AlarmManager.shared.alarms) ?? []
        where alarm.state != .alerting && alarm.id.uuid.7 != UInt8(snoozeIndex) {
            try? AlarmManager.shared.cancel(id: alarm.id)
        }
    }

    // What each scheduled alarm is, for the page that opens from it.
    private static func remember(_ alarm: RingingAlarm, for id: UUID) {
        var all = stored()
        all[id.uuidString] = alarm
        if let data = try? JSONEncoder().encode(all) { UserDefaults.standard.set(data, forKey: infoKey) }
    }

    static func info(_ id: String) -> RingingAlarm? { stored()[id] }

    private static func stored() -> [String: RingingAlarm] {
        guard let data = UserDefaults.standard.data(forKey: infoKey) else { return [:] }
        return (try? JSONDecoder().decode([String: RingingAlarm].self, from: data)) ?? [:]
    }

    /// While REAL is open, an alarm going off is taken over by REAL's own page (the system alert is stopped).
    @MainActor
    static func watch() async {
        for await alarms in AlarmManager.shared.alarmUpdates {
            guard UIApplication.shared.applicationState == .active else { continue }
            takeOver(alarms)
        }
    }

    /// REAL opened (not through "Aç") while an alarm is ringing: its page takes over too.
    @MainActor
    static func takeOverRinging() { takeOver((try? AlarmManager.shared.alarms) ?? []) }

    @MainActor
    private static func takeOver(_ alarms: [Alarm]) {
        for alarm in alarms where alarm.state == .alerting {
            guard let info = info(alarm.id.uuidString) else { continue }
            try? AlarmManager.shared.stop(id: alarm.id)
            if AlarmCenter.shared.ringing == nil { AlarmCenter.shared.present(info) }
        }
    }
}

/// "Aç" on the system alarm: opens REAL on its own alarm page, which keeps ringing until slid away.
@available(iOS 26.0, *)
struct OpenRealAlarmIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Alarmı REAL'de aç"
    static let openAppWhenRun = true

    @Parameter(title: "Alarm")
    var alarmId: String

    init() { alarmId = "" }
    init(alarmId: String) { self.alarmId = alarmId }

    @MainActor
    func perform() async throws -> some IntentResult {
        if let info = RealAlarmKit.info(alarmId) { AlarmCenter.shared.present(info) }
        return .result()
    }
}

// MARK: Sound

/// REAL's ring for its own alarm page: a soft two-note chime made in code (iOS gives apps no access to the
/// phone's ringtones), looping on the playback session so it sounds in silent mode, with a vibration each loop.
final class AlarmTonePlayer {
    private let engine = AVAudioEngine()
    private let node = AVAudioPlayerNode()
    private var buzz: Timer?
    private var attached = false

    var volume: Float {
        get { node.volume }
        set { node.volume = max(0, min(1, newValue)) }
    }

    func start() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default)
        try? session.setActive(true)
        guard let buffer = Self.chime(format: engine.mainMixerNode.outputFormat(forBus: 0)) else { return }
        if !attached {
            engine.attach(node)
            engine.connect(node, to: engine.mainMixerNode, format: buffer.format)
            attached = true
        }
        node.stop()
        node.volume = 1
        node.scheduleBuffer(buffer, at: nil, options: .loops)
        try? engine.start()
        node.play()
        buzz?.invalidate()
        buzz = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { _ in AudioServicesPlaySystemSound(kSystemSoundID_Vibrate) }
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
    }

    func stop() {
        buzz?.invalidate()
        buzz = nil
        node.stop()
        engine.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    /// 2 s loop: E6–C6, a pause, E6–C6 again, each note a decaying bell (fundamental + octave).
    private static func chime(format: AVAudioFormat) -> AVAudioPCMBuffer? {
        let rate = format.sampleRate > 0 ? format.sampleRate : 44_100
        guard let mono = AVAudioFormat(standardFormatWithSampleRate: rate, channels: format.channelCount > 0 ? format.channelCount : 2) else { return nil }
        let frames = AVAudioFrameCount(rate * 2)
        guard let buffer = AVAudioPCMBuffer(pcmFormat: mono, frameCapacity: frames), let channels = buffer.floatChannelData else { return nil }
        buffer.frameLength = frames
        let notes: [(start: Double, freq: Double)] = [(0, 1318.5), (0.2, 1046.5), (0.7, 1318.5), (0.9, 1046.5)]
        for f in 0..<Int(frames) {
            let t = Double(f) / rate
            var s = 0.0
            for n in notes where t >= n.start {
                let dt = t - n.start
                let env = exp(-dt * 5.5) * min(1, dt * 400)
                s += env * (sin(2 * .pi * n.freq * dt) * 0.42 + sin(4 * .pi * n.freq * dt) * 0.12)
            }
            let v = Float(max(-1, min(1, s)))
            for c in 0..<Int(mono.channelCount) { channels[c][f] = v }
        }
        return buffer
    }
}
