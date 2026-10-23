package com.cagri.real.notifications

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Rings an alarm card: the sound loops on the alarm stream (it plays on vibrate and silent too, at the alarm
 * volume) and the phone vibrates, until [RealAlarm.stop] or a snooze stops this service.
 */
class AlarmSoundService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile private var active: AlarmSoundService? = null

        /** The alarm page's slide: 1 = full volume, 0 = silent (the knob at the end). */
        fun setLevel(level: Float) {
            val v = level.coerceIn(0f, 1f)
            runCatching { active?.player?.setVolume(v, v) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(RealAlarm.EXTRA_ID, Long.MIN_VALUE) ?: Long.MIN_VALUE
        if (id == Long.MIN_VALUE) { stopSelf(); return START_NOT_STICKY }
        val notification = RealAlarm.notification(
            this, id,
            intent?.getStringExtra(RealAlarm.EXTRA_TITLE).orEmpty(),
            intent?.getStringExtra(RealAlarm.EXTRA_SUBTITLE).orEmpty(),
            intent?.getIntExtra(RealAlarm.EXTRA_COLOR, 0xFF7C5CFF.toInt()) ?: 0xFF7C5CFF.toInt(),
            intent?.getLongExtra(RealAlarm.EXTRA_AT, System.currentTimeMillis()) ?: System.currentTimeMillis()
        )
        if (Build.VERSION.SDK_INT >= 29) startForeground(RealAlarm.notificationId(id), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(RealAlarm.notificationId(id), notification)
        active = this
        startSound()
        startVibration()
        // In use (unlocked) Android shows the full-screen intent only as a heads-up. Opening the alarm page directly
        // works when Android allows it here (REAL on screen, or "display over other apps" granted); otherwise it is
        // silently refused and the heads-up stays.
        runCatching {
            startActivity(com.cagri.real.ui.alarm.AlarmActivity.intent(
                this, id,
                intent?.getStringExtra(RealAlarm.EXTRA_TITLE).orEmpty(),
                intent?.getStringExtra(RealAlarm.EXTRA_SUBTITLE).orEmpty(),
                intent?.getIntExtra(RealAlarm.EXTRA_COLOR, 0xFF7C5CFF.toInt()) ?: 0xFF7C5CFF.toInt(),
                intent?.getLongExtra(RealAlarm.EXTRA_AT, System.currentTimeMillis()) ?: System.currentTimeMillis()
            ))
        }
        return START_NOT_STICKY
    }

    private fun startSound() {
        player?.release()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // The chosen sound first; if it cannot be read (deleted file, no ringtone set) the phone's alarm sound.
        val candidates = listOfNotNull(
            RealAlarm.soundUri(this),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )
        player = candidates.firstNotNullOfOrNull { uri -> runCatching { prepared(uri, attributes) }.getOrNull() }
    }

    private fun prepared(uri: Uri, attributes: AudioAttributes): MediaPlayer = MediaPlayer().apply {
        try {
            setAudioAttributes(attributes)
            setWakeMode(this@AlarmSoundService, PowerManager.PARTIAL_WAKE_LOCK)
            setDataSource(this@AlarmSoundService, uri)
            isLooping = true
            prepare()
            start()
        } catch (e: Exception) {
            release()
            throw e
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        vibrator = v
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        runCatching { v?.vibrate(effect, attributes) }
    }

    override fun onDestroy() {
        if (active === this) active = null
        player?.runCatching { stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
