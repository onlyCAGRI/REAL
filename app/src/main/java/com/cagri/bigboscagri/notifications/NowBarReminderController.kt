package com.cagri.bigboscagri.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cagri.bigboscagri.MainActivity
import com.cagri.bigboscagri.R
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.ReminderRepository

object NowBarReminderController {
    private const val CHANNEL_ID = "real_now_bar_reminders_v1"
    private const val NOTIFICATION_ID = 99992
    private const val PREFS_NAME = "bigbos_ui"
    private const val PREF_ENABLED = "samsung_now_bar_enabled"
    private const val PREF_MANUAL_PIN_ID = "samsung_now_bar_manual_pin_id"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ENABLED, enabled)
        if (!enabled) {
            editor.remove(PREF_MANUAL_PIN_ID)
        }
        editor.apply()
        reconcile(context)
    }

    fun getManualPinnedReminderId(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(PREF_MANUAL_PIN_ID)) return null
        return prefs.getLong(PREF_MANUAL_PIN_ID, -1L).takeIf { it > 0L }
    }

    fun isManuallyPinned(context: Context, reminderId: Long): Boolean {
        return getManualPinnedReminderId(context) == reminderId
    }

    fun toggleManualPin(context: Context, reminderId: Long): Boolean {
        if (!isEnabled(context) || reminderId <= 0L) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getManualPinnedReminderId(context)
        if (current == reminderId) {
            prefs.edit().remove(PREF_MANUAL_PIN_ID).apply()
        } else {
            prefs.edit().putLong(PREF_MANUAL_PIN_ID, reminderId).apply()
        }
        reconcile(context)
        return isManuallyPinned(context, reminderId)
    }

    fun reconcile(context: Context, repository: ReminderRepository? = null) {
        if (!isEnabled(context) || Build.VERSION.SDK_INT < 36) {
            cancelAll(context)
            return
        }

        val repo = repository ?: ReminderRepository(context)
        val now = System.currentTimeMillis()
        val allReminders = synchronized(ReminderRepository.mutationLock) { repo.load() }
        val storedManualId = getManualPinnedReminderId(context)
        val validManualId = resolveValidManualPinnedReminderId(allReminders, storedManualId)

        if (storedManualId != null && validManualId == null) {
            clearManualPin(context)
        }

        val best = selectNowBarReminder(allReminders, now, validManualId)
        if (best == null) {
            cancelAll(context)
            return
        }

        val manuallyPinned = best.id == validManualId
        val alertAt = best.alertAt
        val isPreStart = alertAt != null && now >= alertAt - 5 * 60 * 1000L

        if (manuallyPinned || isPreStart) {
            showNotification(context, best, now, manuallyPinned)
        } else {
            cancelNotification(context)
        }

        scheduleAlarms(context, best)
    }

    internal fun resolveValidManualPinnedReminderId(
        reminders: List<ReminderItem>,
        manualPinnedId: Long?
    ): Long? {
        if (manualPinnedId == null) return null
        return reminders.firstOrNull { it.id == manualPinnedId && !it.isCompleted }?.id
    }

    internal fun selectAutomaticReminder(reminders: List<ReminderItem>, now: Long): ReminderItem? {
        return reminders
            .asSequence()
            .filter {
                !it.isCompleted &&
                    !it.isNoAlert &&
                    it.alertAt != null &&
                    it.alertAt > now - 30 * 60 * 1000L
            }
            .minWithOrNull(compareBy({ it.alertAt }, { it.id }))
    }

    internal fun selectNowBarReminder(
        reminders: List<ReminderItem>,
        now: Long,
        manualPinnedId: Long?
    ): ReminderItem? {
        val validManualId = resolveValidManualPinnedReminderId(reminders, manualPinnedId)
        if (validManualId != null) {
            return reminders.firstOrNull { it.id == validManualId }
        }
        return selectAutomaticReminder(reminders, now)
    }

    internal fun secondaryText(
        reminder: ReminderItem,
        now: Long,
        manuallyPinned: Boolean
    ): String {
        val alertAt = reminder.alertAt ?: return ""
        if (now >= alertAt) return "Şimdi"
        val remainingMillis = alertAt - now
        if (manuallyPinned && remainingMillis > 5 * 60 * 1000L) return ""
        val remainingMins = (remainingMillis / 60000L).coerceAtLeast(1)
        return "$remainingMins dk kaldı"
    }

    internal fun shouldShowSnoozeAction(reminder: ReminderItem, now: Long): Boolean {
        val alertAt = reminder.alertAt ?: return false
        return now >= alertAt - 5 * 60 * 1000L
    }

    private fun showNotification(
        context: Context,
        reminder: ReminderItem,
        now: Long,
        manuallyPinned: Boolean
    ) {
        val nm = NotificationManagerCompat.from(context)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "REAL Hatırlatıcılar",
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("bigboscagri://nowbar/${reminder.id}/open")
            putExtra(ReminderNotifications.EXTRA_REMINDER_ID, reminder.id)
        }
        val openPending = PendingIntent.getActivity(
            context,
            reminder.id.hashCode() * 31 + 10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE
            data = Uri.parse("bigboscagri://nowbar/${reminder.id}/complete")
            putExtra(ReminderNotifications.EXTRA_REMINDER_ID, reminder.id)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode() * 31 + 11,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_10
            data = Uri.parse("bigboscagri://nowbar/${reminder.id}/snooze_10")
            putExtra(ReminderNotifications.EXTRA_REMINDER_ID, reminder.id)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode() * 31 + 12,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val secondaryText = secondaryText(reminder, now, manuallyPinned)

        val samsungExtras = Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 1)
            putString("android.ongoingActivityNoti.primaryInfo", reminder.text)
            putString("android.ongoingActivityNoti.secondaryInfo", secondaryText)
            putString("android.ongoingActivityNoti.chipExpandedText", "REAL")
            putString("android.ongoingActivityNoti.nowbarPrimaryInfo", reminder.text)
            putString("android.ongoingActivityNoti.nowbarSecondaryInfo", secondaryText)
            putInt("android.ongoingActivityNoti.actionType", 1)
            putInt("android.ongoingActivityNoti.actionPrimarySet", 0)
            putInt("android.ongoingActivityNoti.chipBgColor", 0xFF91B9FF.toInt())
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(reminder.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPending)
            .setExtras(samsungExtras)

        if (secondaryText.isNotBlank()) {
            builder.setContentText(secondaryText)
        }

        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
        }

        
        
        builder.addAction(0, "Tamamla", completePending)
        if (shouldShowSnoozeAction(reminder, now)) {
            builder.addAction(0, "10 dk ertele", snoozePending)
        }

        try {
            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) { }
    }

    private fun clearManualPin(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_MANUAL_PIN_ID)
            .apply()
    }

    private fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun cancelAll(context: Context) {
        cancelNotification(context)
        cancelAlarms(context)
    }

    private fun cancelAlarms(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(getPendingIntent(context, "PRE_START"))
        manager.cancel(getPendingIntent(context, "DUE"))
        manager.cancel(getPendingIntent(context, "EXPIRE"))
    }

    private fun getPendingIntent(context: Context, type: String): PendingIntent {
        val intent = Intent(context, NowBarReminderReceiver::class.java).apply {
            action = "com.cagri.bigboscagri.NOWBAR_$type"
        }
        return PendingIntent.getBroadcast(
            context,
            type.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleAlarms(context: Context, reminder: ReminderItem) {
        val manager = context.getSystemService(AlarmManager::class.java)
        cancelAlarms(context)
        val alertAt = reminder.alertAt ?: return
        val now = System.currentTimeMillis()
        val canSchedule = ReminderScheduler.canScheduleExact(context)

        val schedule = { time: Long, type: String ->
            if (time > now) {
                val pi = getPendingIntent(context, type)
                try {
                    if (canSchedule) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                    else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                } catch (_: SecurityException) {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                }
            }
        }

        schedule(alertAt - 5 * 60 * 1000L, "PRE_START")
        schedule(alertAt, "DUE")
        schedule(alertAt + 30 * 60 * 1000L, "EXPIRE")
    }
}

class NowBarReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NowBarReminderController.reconcile(context)
    }
}
