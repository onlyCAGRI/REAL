import Foundation
import Speech
import AVFoundation
import Observation

/// Speech-to-text for the composer microphone (VoiceInputController.kt): tr-TR, partial results shown live.
@Observable
final class VoiceInput {
    /// Words to expect, set by the composer before listening.
    nonisolated(unsafe) static var hints: [String] = []
    enum Phase { case idle, listening, finishing }

    private(set) var phase: Phase = .idle
    private(set) var committed = ""
    private(set) var partial = ""
    private(set) var level: Float = 0
    var errorText: String?

    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "tr-TR"))
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var onText: ((String) -> Void)?
    /// Called once with the final transcript; the phase stays .finishing ("REAL düşünüyor…") until `done` runs.
    private var onFinished: ((String, @escaping () -> Void) -> Void)?

    var text: String { [committed, partial].filter { !$0.isEmpty }.joined(separator: " ") }

    func toggle(onText: @escaping (String) -> Void, onFinished: ((String, @escaping () -> Void) -> Void)? = nil) {
        if phase == .idle { start(onText: onText, onFinished: onFinished) } else { finish() }
    }

    func start(onText: @escaping (String) -> Void, onFinished: ((String, @escaping () -> Void) -> Void)? = nil) {
        self.onText = onText
        self.onFinished = onFinished
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async {
                guard status == .authorized else { self.errorText = "Konuşma tanıma izni verilmedi"; return }
                AVAudioApplication.requestRecordPermission { granted in
                    DispatchQueue.main.async {
                        guard granted else { self.errorText = "Mikrofon izni verilmedi"; return }
                        self.begin()
                    }
                }
            }
        }
    }

    private func begin() {
        guard let recognizer, recognizer.isAvailable else { errorText = "Konuşma tanıma şu an kullanılamıyor"; return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            // Category names and short card titles: proper names and foreign titles are what it mishears most.
            request.contextualStrings = Array(VoiceInput.hints.prefix(100))
            if recognizer.supportsOnDeviceRecognition { request.requiresOnDeviceRecognition = false }
            self.request = request
            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.removeTap(onBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
                request.append(buffer)
                guard let data = buffer.floatChannelData?[0] else { return }
                let n = Int(buffer.frameLength)
                var sum: Float = 0
                for i in stride(from: 0, to: n, by: 8) { sum += abs(data[i]) }
                let avg = n > 0 ? sum / Float(max(n / 8, 1)) : 0
                DispatchQueue.main.async { self?.level = min(avg * 12, 1) }
            }
            engine.prepare()
            try engine.start()
            committed = ""
            partial = ""
            phase = .listening
            task = recognizer.recognitionTask(with: request) { [weak self] result, error in
                guard let self else { return }
                DispatchQueue.main.async {
                    if let result {
                        self.partial = result.bestTranscription.formattedString
                        self.onText?(self.text)
                        if result.isFinal {
                            self.committed = self.text
                            self.partial = ""
                            self.stopEngine()
                        }
                    }
                    if error != nil { self.stopEngine() }
                }
            }
        } catch {
            errorText = "Mikrofon başlatılamadı"
            stopEngine()
        }
    }

    func finish() {
        guard phase == .listening else { return }
        phase = .finishing
        request?.endAudio()
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { [weak self] in self?.stopEngine() }
    }

    func cancel() {
        onFinished = nil
        task?.cancel()
        stopEngine()
        committed = ""
        partial = ""
    }

    private func stopEngine() {
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
        task = nil
        request = nil
        level = 0
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        let final = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if phase != .idle, let onFinished, !final.isEmpty {
            self.onFinished = nil
            phase = .finishing
            onFinished(final) { [weak self] in self?.phase = .idle }
            return
        }
        onFinished = nil
        phase = .idle
    }
}
