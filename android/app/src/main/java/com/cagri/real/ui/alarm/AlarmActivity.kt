package com.cagri.real.ui.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.cagri.real.notifications.AlarmSoundService
import com.cagri.real.notifications.RealAlarm
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One ringing alarm card, as the full-screen alarm page shows it. */
data class AlarmInfo(val id: Long, val title: String, val subtitle: String, val color: Int, val at: Long)

/**
 * Full-screen alarm page, opened over the lock screen by the alarm notification's full-screen intent.
 * The sound belongs to that notification: sliding (or "Kapat") cancels it, "Ertele" reschedules it.
 * Leaving the page any other way keeps the alarm ringing.
 */
class AlarmActivity : ComponentActivity() {
    private var alarm by mutableStateOf<AlarmInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        alarm = read(intent)
        setContent {
            val current = alarm ?: return@setContent
            AlarmScreen(
                alarm = current,
                snoozeMinutes = RealAlarm.snoozeMinutes(this),
                onStop = {
                    RealAlarm.stop(this, current.id)
                    finish()
                },
                onSnooze = {
                    val at = RealAlarm.snooze(this, current.id, current.title, current.subtitle, current.color)
                    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if (com.cagri.real.i18n.I18n.isEnglish) "h:mm a" else "HH:mm", com.cagri.real.i18n.I18n.locale))
                    Toast.makeText(this, com.cagri.real.i18n.t("{0}'da tekrar çalacak", time), Toast.LENGTH_SHORT).show()
                    finish()
                },
                // Sliding turns the sound down (a little faster at first, so the fade is heard right away),
                // silent at the end; springing back brings it up again.
                onSlide = { p -> AlarmSoundService.setLevel((1f - p) * (1f - p)) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)?.let { alarm = it }
    }

    private fun read(intent: Intent?): AlarmInfo? {
        intent ?: return null
        val id = intent.getLongExtra(RealAlarm.EXTRA_ID, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return null
        return AlarmInfo(
            id = id,
            title = intent.getStringExtra(RealAlarm.EXTRA_TITLE).orEmpty(),
            subtitle = intent.getStringExtra(RealAlarm.EXTRA_SUBTITLE).orEmpty(),
            color = intent.getIntExtra(RealAlarm.EXTRA_COLOR, 0xFF7C5CFF.toInt()),
            at = intent.getLongExtra(RealAlarm.EXTRA_AT, System.currentTimeMillis())
        )
    }

    companion object {
        fun intent(context: Context, id: Long, title: String, subtitle: String, color: Int, at: Long): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                data = Uri.parse("real://alarm-screen/$id")
                putExtra(RealAlarm.EXTRA_ID, id)
                putExtra(RealAlarm.EXTRA_TITLE, title)
                putExtra(RealAlarm.EXTRA_SUBTITLE, subtitle)
                putExtra(RealAlarm.EXTRA_COLOR, color)
                putExtra(RealAlarm.EXTRA_AT, at)
            }
    }
}
