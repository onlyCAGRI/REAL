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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cagri.bigboscagri.MainActivity
import com.cagri.bigboscagri.R
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.ReminderRepository

object NowBarReminderController {
    private const val CHANNEL_ID = "real_now_bar_reminders_v1"
    private const val NOTIFICATION_ID = 99992

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences("bigbos_ui", Context.MODE_PRIVATE)
            .getBoolean("samsung_now_bar_enabled", false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("bigbos_ui", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("samsung_now_bar_enabled", enabled)
            .apply()
        reconcile(context)
    }

    fun reconcile(context: Context, repository: ReminderRepository? = null) {
        if (!isEnabled(context) || Build.VERSION.SDK_INT < 36) {
            cancelAll(context)
            return
        }
        
        val repo = repository ?: ReminderRepository(context)
        val now = System.currentTimeMillis()
        val allReminders = synchronized(ReminderRepository.mutationLock) { repo.load() }
        
        val eligible = allReminders.filter { 
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }
        
        val best = eligible.minWithOrNull(compareBy({ it.alertAt }, { it.id }))
        
        if (best == null) {
            cancelAll(context)
            return
        }

        val alertAt = best.alertAt!!
        val isDue = now >= alertAt
        val isPreStart = now >= alertAt - 5 * 60 * 1000L

        if (isPreStart) {
            showNotification(context, best, isDue, now)
        } else {
            cancelNotification(context)
        }

        scheduleAlarms(context, best)
    }

    private fun showNotification(context: Context, reminder: ReminderItem, isDue: Boolean, now: Long) {
        val nm = NotificationManagerCompat.from(context)
        
        val channel = NotificationChannel(
            CHANNEL_ID,
            "REAL Now Bar Reminders",
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderNotifications.EXTRA_REMINDER_ID, reminder.id)
        }
        val openPending = PendingIntent.getActivity(
            context,
            10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val secondaryText = if (isDue) {
            "Şimdi"
        } else {
            val remainingMillis = reminder.alertAt!! - now
            val remainingMins = (remainingMillis / 60000L).coerceAtLeast(1)
            "$remainingMins dk kaldı"
        }

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
            .setContentText(secondaryText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPending)
            .setExtras(samsungExtras)

        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
        }

        if (isDue) {
            val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_COMPLETE
                putExtra(ReminderNotifications.EXTRA_REMINDER_ID, reminder.id)
            }
            val completePending = PendingIntent.getBroadcast(
                context,
                (reminder.id % Int.MAX_VALUE).toInt(),
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_SNOOZE_10
                putExtra(ReminderNotifications.EXTRA_REMINDER_ID, reminder.id)
            }
            val snoozePending = PendingIntent.getBroadcast(
                context,
                ((reminder.id % Int.MAX_VALUE).toInt() + 10000),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(0, "Tamamla", completePending)
            builder.addAction(0, "10 dk ertele", snoozePending)
        }

        try {
            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) { }
    }

    private fun cancelNotification(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(NOTIFICATION_ID)
    }

    private fun cancelAll(context: Context) {
        cancelNotification(context)
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
        val alertAt = reminder.alertAt!!
        val now = System.currentTimeMillis()

        manager.cancel(getPendingIntent(context, "PRE_START"))
        manager.cancel(getPendingIntent(context, "DUE"))
        manager.cancel(getPendingIntent(context, "EXPIRE"))

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
