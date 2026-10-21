package com.cagri.real.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.cagri.real.data.ReminderRepository
import com.cagri.real.data.earlyAlertAt
import com.cagri.real.data.nextAlertAfter


object ReminderScheduler {
    fun canScheduleExact(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun reconcile(context: Context, repository: ReminderRepository) = synchronized(ReminderRepository.mutationLock) {
        NowBarReminderController.reconcile(context, repository)
        val now = System.currentTimeMillis()
        repository.deliverDue(now) { item ->
            ReminderNotifications.show(context, item, alerting = true)
        }
        deliverEarlyAlerts(context, repository.load(), now)
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 0,
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = "com.cagri.real.NEXT_REMINDER"
                data = Uri.parse("real://next-reminder")
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val next = repository.load().nextAlertAfter(now)
        if (next == null) { manager.cancel(pending); pending.cancel(); return@synchronized }
        try {
            if (canScheduleExact(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }

    /** "Erken uyarı": shows the heads-up once per alert time, between the warning time and the alert itself. */
    private fun deliverEarlyAlerts(context: Context, reminders: List<com.cagri.real.data.ReminderItem>, now: Long) {
        val prefs = com.cagri.real.platform.android.sync.SyncLocal.preferences(context.applicationContext, "real_early_alerts", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        var changed = false
        reminders.forEach { item ->
            val early = item.earlyAlertAt() ?: return@forEach
            val at = item.alertAt ?: return@forEach
            if (item.isCompleted || early > now || at <= now || prefs.getLong("r_${item.id}", 0L) == at) return@forEach
            if (ReminderNotifications.showEarly(context, item)) { edit.putLong("r_${item.id}", at); changed = true }
        }
        if (changed) edit.apply()
    }
}
