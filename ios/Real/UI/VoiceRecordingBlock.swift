import SwiftUI
import AVFoundation

/// ReminderVoiceRecording.kt: a local m4a recording attached to a card ("voice" detail block).
struct ReminderVoiceRecording: Codable, Hashable, Identifiable {
    var id: String
    var localPath: String
    var durationMillis: Int64
    var createdAt: Int64
}

func formatVoiceDuration(_ millis: Int64) -> String {
    let s = max(0, Int(millis / 1000))
    return String(format: "%d:%02d", s / 60, s % 60)
}

/// Records with metering (drives the blue glow) and plays recordings back; one recorder/player at a time.
@Observable
final class VoiceRecorderEngine: NSObject, AVAudioPlayerDelegate {
    private(set) var recording = false
    private(set) var elapsed: Int64 = 0
    private(set) var level: Double = 0
    private(set) var playingId: String?
    var error: String?

    @ObservationIgnored private var recorder: AVAudioRecorder?
    @ObservationIgnored private var player: AVAudioPlayer?
    @ObservationIgnored private var timer: Timer?
    @ObservationIgnored private var startedAt = Date()
    @ObservationIgnored private var currentName = ""

    func start() {
        stopPlayback()
        AVAudioApplication.requestRecordPermission { granted in
            DispatchQueue.main.async {
                guard granted else { self.error = "Mikrofon izni gerekli. Ayarlar'dan REAL için mikrofonu açabilirsin."; return }
                self.beginRecording()
            }
        }
    }

    private func beginRecording() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try session.setActive(true)
            currentName = "voice-\(UUID().uuidString).m4a"
            let url = AttachmentStorage.directory.appendingPathComponent(currentName)
            try FileManager.default.createDirectory(at: AttachmentStorage.directory, withIntermediateDirectories: true)
            let r = try AVAudioRecorder(url: url, settings: [AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 44_100,
                                                             AVNumberOfChannelsKey: 1, AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue])
            r.isMeteringEnabled = true
            guard r.record() else { error = "Kayıt başlatılamadı."; return }
            recorder = r
            startedAt = Date()
            error = nil
            recording = true
            timer = Timer.scheduledTimer(withTimeInterval: 0.08, repeats: true) { [weak self] _ in
                guard let self, let r = self.recorder else { return }
                r.updateMeters()
                let db = Double(r.averagePower(forChannel: 0))
                self.level = max(0, min(1, (db + 50) / 50))
                self.elapsed = Int64(Date().timeIntervalSince(self.startedAt) * 1000)
                if self.elapsed >= 10 * 60 * 1000 { _ = self.stop() }
            }
        } catch {
            self.error = "Kayıt başlatılamadı."
        }
    }

    /// Stops recording and returns the finished recording (nil if it was too short).
    func stop() -> ReminderVoiceRecording? {
        timer?.invalidate(); timer = nil
        recorder?.stop(); recorder = nil
        recording = false
        level = 0
        let duration = elapsed
        elapsed = 0
        guard duration >= 500 else {
            try? FileManager.default.removeItem(at: AttachmentStorage.directory.appendingPathComponent(currentName))
            return nil
        }
        return ReminderVoiceRecording(id: UUID().uuidString, localPath: currentName, durationMillis: duration, createdAt: .now)
    }

    func cancel() {
        guard recording else { return }
        _ = stop()
    }

    func togglePlayback(_ r: ReminderVoiceRecording) {
        if playingId == r.id { stopPlayback(); return }
        stopPlayback()
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback)
            try AVAudioSession.sharedInstance().setActive(true)
            let p = try AVAudioPlayer(contentsOf: AttachmentStorage.directory.appendingPathComponent(r.localPath))
            p.delegate = self
            p.play()
            player = p
            playingId = r.id
        } catch {
            self.error = "Ses kaydı oynatılamadı."
        }
    }

    func stopPlayback() {
        player?.stop(); player = nil
        playingId = nil
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        DispatchQueue.main.async { self.playingId = nil }
    }
}

/// VoiceRecorderInline: "Ses" header, playback rows, and the record / stop control with the live blue glow.
struct VoiceRecordingBlock: View {
    let recordings: [ReminderVoiceRecording]
    let editing: Bool
    var onRecorded: (ReminderVoiceRecording) -> Void = { _ in }
    var onRemove: (ReminderVoiceRecording) -> Void = { _ in }
    var onClose: (() -> Void)?
    @State private var engine = VoiceRecorderEngine()

    var body: some View {
        let active = engine.recording || engine.playingId != nil
        let live = engine.recording ? sqrt(engine.level) : (engine.playingId != nil ? 0.35 : 0)
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Ses").font(RealFont.inter(14, .medium)).foregroundStyle(Color(argb: 0xFFA5A5B0))
                Spacer()
                if editing, let onClose {
                    Button { engine.cancel(); onClose() } label: {
                        RealSymbol("xmark", size: 14.9).foregroundStyle(Color(argb: 0xFFA7A7B0)).frame(width: 32, height: 32)
                    }
                    .accessibilityLabel("Ses bloğunu kaldır")
                }
            }
            if engine.recording {
                control(active: true)
            } else {
                ForEach(recordings) { r in playbackRow(r) }
                if editing { control(active: false) }
            }
            if let e = engine.error { Text(e).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFE0B5A8)) }
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .background {
            ZStack {
                RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Color(argb: 0xFF141416))
                if active {
                    GeometryReader { g in
                        RadialGradient(colors: [Color(argb: 0xFF2D6FD6).opacity(0.12 + live * 0.32), .clear],
                                       center: UnitPoint(x: 0.86, y: 0.95), startRadius: 0,
                                       endRadius: max(g.size.width, g.size.height) * (1.1 + live * 0.3))
                        LinearGradient(colors: [Color(argb: 0xFF69ACFF).opacity(0.04 + live * 0.1), .clear], startPoint: .top, endPoint: .bottom)
                    }
                    .animation(.linear(duration: 0.11), value: live)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .onDisappear { engine.cancel(); engine.stopPlayback() }
    }

    private func control(active: Bool) -> some View {
        HStack(spacing: 8) {
            Button {
                if engine.recording { if let r = engine.stop() { onRecorded(r) } } else { engine.start() }
            } label: {
                RealSymbol("waveform", size: 24.1)
                    .foregroundStyle(active ? Color(argb: 0xFF91B9FF) : .white).frame(width: 48, height: 48)
            }
            .accessibilityLabel(active ? "Ses kaydını durdur" : "Ses kaydını başlat")
            Spacer()
            Text(formatVoiceDuration(engine.elapsed)).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFEAEAF0)).monospacedDigit()
        }
        .padding(.vertical, 4)
    }

    private func playbackRow(_ r: ReminderVoiceRecording) -> some View {
        let playing = engine.playingId == r.id
        return HStack(spacing: 8) {
            Button { engine.togglePlayback(r) } label: {
                RealSymbol(playing ? "pause.fill" : "play.fill", size: 17.2).foregroundStyle(Color(argb: 0xFFEAEAF0))
                    .frame(width: 34, height: 34)
            }
            .accessibilityLabel(playing ? "Ses kaydını duraklat" : "Ses kaydını oynat")
            Spacer()
            Text(formatVoiceDuration(r.durationMillis)).font(RealFont.inter(12, .medium)).foregroundStyle(Color(argb: 0xFFB8B8C2)).monospacedDigit()
            if editing {
                Button { if playing { engine.stopPlayback() }; onRemove(r) } label: {
                    RealSymbol("xmark", size: 13.8).foregroundStyle(Color(argb: 0xFFA7A7B0)).frame(width: 32, height: 32)
                }
                .accessibilityLabel("Ses kaydını sil")
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
    }
}
