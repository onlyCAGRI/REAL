import AVFoundation

/// The welcome story's music (WelcomeSound.kt): Resources/Sounds/real_welcome.mp3, word 1 at 0 s, a new word every
/// second (120 BPM, two beats), the chord under "Welcome to" at 10 s, then a soft groove fading out by ~19.5 s.
/// The session is .ambient: the ring/silent switch mutes it and it mixes with (never stops) the user's own audio;
/// when something else is already playing it stays silent.
@MainActor
final class WelcomeSound {
    private var player: AVAudioPlayer?

    /// Loads the file ahead of time so start() is instant.
    func prepare() {
        guard player == nil,
              !AVAudioSession.sharedInstance().isOtherAudioPlaying,
              let url = Bundle.main.url(forResource: "real_welcome", withExtension: "mp3") else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        player = try? AVAudioPlayer(contentsOf: url)
        player?.volume = 0.8
        player?.prepareToPlay()
    }

    func start() { player?.play() }

    /// Skipping the story: the music dips out over a quarter second instead of cutting.
    func fadeOut() {
        guard let player else { return }
        player.setVolume(0, fadeDuration: 0.25)
        Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(300))
            self?.stop()
        }
    }

    func stop() {
        player?.stop()
        player = nil
    }
}
