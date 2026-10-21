package com.cagri.real.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cagri.real.R

/** One short, preloaded sample; never plays behind another app or over an audio call. */
@Composable
internal fun RealPetMeowSound(state: RealPetState) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember { mutableStateOf<RealPetMeowPlayer?>(null) }
    DisposableEffect(context, lifecycle) {
        val sound = RealPetMeowPlayer(context)
        player = sound
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) sound.stop()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sound.release()
        }
    }
    LaunchedEffect(state, player) {
        if (state == RealPetState.Meowing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            player?.play()
        } else {
            player?.stop()
        }
    }
}

private class RealPetMeowPlayer(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val pool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    @Volatile private var ready = false
    private var stream = 0
    private var released = false
    private val sample: Int

    init {
        pool.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
        sample = pool.load(context, R.raw.real_pet_meow, 1)
    }

    fun play() {
        if (released || !ready || audio.mode != AudioManager.MODE_NORMAL ||
            audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || audio.isMusicActive
        ) return
        stop()
        // Uses current system volume; never changes volume, ringer mode or audio focus.
        stream = pool.play(sample, 0.45f, 0.45f, 1, 0, 1f)
    }

    fun stop() {
        if (!released && stream != 0) pool.stop(stream)
        stream = 0
    }

    fun release() {
        if (released) return
        stop()
        released = true
        ready = false
        pool.setOnLoadCompleteListener(null)
        pool.release()
    }
}
