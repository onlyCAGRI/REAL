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
            // An alarm rings full screen; one missed by more than 15 minutes (phone off) only notifies.
            if (item.isAlarm && now - (item.alertAt ?: now) < 15 * 60_000L) { RealAlarm.ring(context, item); true }
            else ReminderNotifications.show(context, item, alerting = true)
        }
        deliverEarlyAlerts(context, repository.load(), now)
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 0,
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = "com.cagri.real.NEXT_REMINDER"
                data = Uri.parse("real://next-reminder")
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val all = repository.load()
        LocationReminders.sync(context, all)
        DepartureReminder.plan(context, all)
        val next = all.nextAlertAfter(now)
        if (next == null) { manager.cancel(pending); pending.cancel(); return@synchronized }
        try {
            // The next alert being an alarm card: schedule it as a real alarm clock (status bar icon, never deferred).
            val alarmNext = all.any { it.isAlarm && !it.isCompleted && it.alertAt == next && it.notifiedAlertAt != next }
            if (alarmNext && canScheduleExact(context)) manager.setAlarmClock(AlarmManager.AlarmClockInfo(next, launchPending(context)), pending)
            else if (canScheduleExact(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }

    private fun launchPending(context: Context): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, com.cagri.real.MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

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
