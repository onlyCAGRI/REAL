package com.cagri.bigboscagri.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.cagri.bigboscagri.data.ReminderRepository
import com.cagri.bigboscagri.data.nextAlertAfter

/** One system alarm for the next batch; no timer, polling or permanent service. */
object ReminderScheduler {
    fun canScheduleExact(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun reconcile(context: Context, repository: ReminderRepository) = synchronized(ReminderRepository.mutationLock) {
        val now = System.currentTimeMillis()
        repository.deliverDue(now) { ReminderNotifications.show(context, it, alerting = true) }
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 0,
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = "com.cagri.bigboscagri.NEXT_REMINDER"
                data = Uri.parse("bigboscagri://next-reminder")
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
}
