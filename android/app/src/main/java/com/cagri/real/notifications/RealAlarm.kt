package com.cagri.real.notifications

import com.cagri.real.i18n.t

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.cagri.real.R
import com.cagri.real.data.CategoryRepository
import com.cagri.real.data.ReminderItem
import com.cagri.real.ui.alarm.AlarmActivity

/**
 * Alarm cards: at their time they ring full screen ([AlarmActivity]) and keep ringing (FLAG_INSISTENT on an
 * alarm-usage channel) until slid away or snoozed. The sound and the snooze length are device settings.
 */
object RealAlarm {
    const val ACTION_STOP = "com.cagri.real.ALARM_STOP"
    const val ACTION_SNOOZE = "com.cagri.real.ALARM_SNOOZE"
    const val ACTION_RING = "com.cagri.real.ALARM_RING"
    const val EXTRA_ID = "alarm_id"
    const val EXTRA_TITLE = "alarm_title"
    const val EXTRA_SUBTITLE = "alarm_subtitle"
    const val EXTRA_COLOR = "alarm_color"
    const val EXTRA_AT = "alarm_at"

    val SNOOZE_CHOICES = listOf(5, 10, 15, 20)
    private const val PREFS = "real_alarm"
    private const val KEY_SOUND = "sound_uri"
    private const val KEY_SNOOZE = "snooze_minutes"
    private const val KEY_OVER_PHONE_ASKED = "over_phone_asked"
    private const val CHANNEL_PREFIX = "real_alarm"
    private const val CHANNEL_ID = "real_alarm_silent_v2"

    /** No category: one of five tones, fixed per card so the same alarm always looks the same. */
    private val FALLBACK_TONES = intArrayOf(
        0xFF7C5CFF.toInt(), // mor
        0xFFEC4899.toInt(), // pembe
        0xFFFF7A45.toInt(), // turuncu
        0xFF2DD4CF.toInt(), // turkuaz
        0xFF55D3B4.toInt()  // yeşil
    )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Default: the phone's own ringtone ("arama sesi"). */
    fun soundUri(context: Context): Uri =
        prefs(context).getString(KEY_SOUND, null)?.let(Uri::parse) ?: Settings.System.DEFAULT_RINGTONE_URI

    fun setSound(context: Context, uri: Uri?) {
        prefs(context).edit().apply { if (uri == null) remove(KEY_SOUND) else putString(KEY_SOUND, uri.toString()) }.apply()
        ensureChannel(context)
    }

    fun soundTitle(context: Context): String {
        if (prefs(context).getString(KEY_SOUND, null) == null) return t("Telefonun zil sesi")
        return runCatching { RingtoneManager.getRingtone(context, soundUri(context))?.getTitle(context) }.getOrNull() ?: t("Seçili ses")
    }

    fun snoozeMinutes(context: Context): Int = prefs(context).getInt(KEY_SNOOZE, 10).takeIf { it in SNOOZE_CHOICES } ?: 10

    fun setSnoozeMinutes(context: Context, minutes: Int) { prefs(context).edit().putInt(KEY_SNOOZE, minutes).apply() }

    fun canRingFullScreen(context: Context): Boolean = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun openFullScreenSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= 34)
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** "Diğer uygulamaların üzerinde göster": lets the alarm page open full screen while the phone is in use too. */
    fun canShowOverPhone(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openOverPhoneSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Asked once, the first time an alarm is picked; afterwards only the Settings row offers it. */
    fun shouldAskOverPhone(context: Context): Boolean =
        !canShowOverPhone(context) && !prefs(context).getBoolean(KEY_OVER_PHONE_ASKED, false)

    fun markOverPhoneAsked(context: Context) { prefs(context).edit().putBoolean(KEY_OVER_PHONE_ASKED, true).apply() }

    /**
     * A silent channel: a notification's own sound is muted when the phone is on vibrate or silent, so the
     * ringing comes from [AlarmSoundService] on the alarm stream instead. Older sounding channels are removed.
     */
    private fun ensureChannel(context: Context): String {
        val id = CHANNEL_ID
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(id) != null) return id
        manager.notificationChannels.filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != id }
            .forEach { manager.deleteNotificationChannel(it.id) }
        manager.createNotificationChannel(NotificationChannel(id, t("Alarmlar"), NotificationManager.IMPORTANCE_HIGH).apply {
            description = t("Alarm olarak kurulan kartlar tam ekran açılır; ses alarm ses seviyesinden çalar.")
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        return id
    }

    internal fun notificationId(id: Long) = ("alarm$id").hashCode()

    fun accentFor(item: ReminderItem, categories: CategoryRepository?): Int =
        resolveNotificationMetadata(item, categories).accentColor
            ?: FALLBACK_TONES[Math.floorMod(item.id, FALLBACK_TONES.size.toLong()).toInt()]

    /** Called when an alarm card comes due (from [ReminderScheduler.reconcile]). */
    fun ring(context: Context, item: ReminderItem) {
        val categories = runCatching { CategoryRepository(context) }.getOrNull()
        val subtitle = resolveNotificationMetadata(item, categories, "Alarm").title
        ring(context, item.id, item.text.lineSequence().firstOrNull().orEmpty().ifBlank { "Alarm" }, subtitle,
            accentFor(item, categories), item.alertAt ?: System.currentTimeMillis())
    }

    /**
     * Starts the ringing: [AlarmSoundService] shows the alarm notification as its foreground notification and plays
     * the sound on the alarm stream. If the service cannot start, the notification still opens the alarm screen.
     */
    fun ring(context: Context, id: Long, title: String, subtitle: String, color: Int, at: Long) {
        val start = actionIntent(context, ACTION_RING, id, title, subtitle, color, at).setClass(context, AlarmSoundService::class.java)
        try {
            context.startForegroundService(start)
        } catch (_: Exception) {
            runCatching { context.getSystemService(NotificationManager::class.java).notify(notificationId(id), notification(context, id, title, subtitle, color, at)) }
        }
    }

    internal fun notification(context: Context, id: Long, title: String, subtitle: String, color: Int, at: Long): Notification {
        val channel = ensureChannel(context)
        val screen = PendingIntent.getActivity(context, notificationId(id),
            AlarmActivity.intent(context, id, title, subtitle, color, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val extras = actionIntent(context, "", id, title, subtitle, color, at)
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification_r)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setOngoing(true)
            .setAutoCancel(false)
            .setColor(color)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(at)
            .addAction(Notification.Action.Builder(null, "Ertele · ${snoozeMinutes(context)} dk",
                broadcast(context, Intent(extras).setAction(ACTION_SNOOZE), id)).build())
            .addAction(Notification.Action.Builder(null, t("Kapat"),
                broadcast(context, Intent(extras).setAction(ACTION_STOP), id)).build())
            .build()
        return notification
    }

    fun stop(context: Context, id: Long) {
        context.stopService(Intent(context, AlarmSoundService::class.java))
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(id))
    }

    /** Stops the ringing and rings the same alarm again after the snooze length. */
    fun snooze(context: Context, id: Long, title: String, subtitle: String, color: Int): Long {
        stop(context, id)
        val at = System.currentTimeMillis() + snoozeMinutes(context) * 60_000L
        val ring = broadcast(context, actionIntent(context, ACTION_RING, id, title, subtitle, color, at), id)
        val show = PendingIntent.getActivity(context, notificationId(id) + 1,
            AlarmActivity.intent(context, id, title, subtitle, color, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = context.getSystemService(AlarmManager::class.java)
        try {
            if (ReminderScheduler.canScheduleExact(context)) manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), ring)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, ring)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, ring)
        }
        return at
    }

    private fun actionIntent(context: Context, action: String, id: Long, title: String, subtitle: String, color: Int, at: Long) =
        Intent(context, RealAlarmReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("real://alarm/$id")
            putExtra(EXTRA_ID, id); putExtra(EXTRA_TITLE, title); putExtra(EXTRA_SUBTITLE, subtitle)
            putExtra(EXTRA_COLOR, color); putExtra(EXTRA_AT, at)
        }

    private fun broadcast(context: Context, intent: Intent, id: Long): PendingIntent {
        val code = notificationId(id) * 4 + when (intent.action) { ACTION_STOP -> 1; ACTION_SNOOZE -> 2; else -> 3 }
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class RealAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(RealAlarm.EXTRA_ID, 0L)
        val title = intent.getStringExtra(RealAlarm.EXTRA_TITLE).orEmpty()
        val subtitle = intent.getStringExtra(RealAlarm.EXTRA_SUBTITLE).orEmpty()
        val color = intent.getIntExtra(RealAlarm.EXTRA_COLOR, 0xFF7C5CFF.toInt())
        when (intent.action) {
            RealAlarm.ACTION_STOP -> RealAlarm.stop(context, id)
            RealAlarm.ACTION_SNOOZE -> RealAlarm.snooze(context, id, title, subtitle, color)
            RealAlarm.ACTION_RING -> RealAlarm.ring(context, id, title, subtitle, color,
                intent.getLongExtra(RealAlarm.EXTRA_AT, System.currentTimeMillis()))
        }
    }
}
