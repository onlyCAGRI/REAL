package com.cagri.real.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.cagri.real.R

/**
 * The welcome story's music (res/raw/real_welcome.mp3): word 1 at 0 ms, a new word every second (120 BPM),
 * the chord under "Welcome to" at 10 s, then a soft groove that keeps playing while the name is typed and fades out by ~31 s.
 * It plays only when the ringer is in normal mode and nothing else is playing, and never takes audio focus,
 * so silent / vibrate mode and the user's own music are respected.
 */
internal class WelcomeSound(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    /** Loads the file ahead of time so [start] is instant. Does nothing when sound should stay off. */
    fun prepare() {
        if (player != null) return
        val audio = appContext.getSystemService(AudioManager::class.java) ?: return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || audio.isMusicActive) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        player = runCatching {
            MediaPlayer.create(appContext, R.raw.real_welcome, attributes, audio.generateAudioSessionId())
        }.getOrNull()?.apply {
            setVolume(VOLUME, VOLUME)
            setOnCompletionListener { release() }
        }
    }

    fun start() {
        runCatching { player?.start() }
    }

    /** Skipping the story: the music dips out over a quarter second instead of cutting. */
    fun fadeOut(durationMillis: Long = 250) {
        val p = player ?: return
        handler.removeCallbacksAndMessages(null)
        val steps = 10
        for (i in 1..steps) {
            handler.postDelayed({
                val v = VOLUME * (1f - i / steps.toFloat())
                runCatching { p.setVolume(v, v) }
                if (i == steps) release()
            }, durationMillis * i / steps)
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.release() } }
        player = null
    }

    private companion object {
        const val VOLUME = 0.8f
    }
}
