package com.cagri.real.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cagri.real.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0L) return

        val action = intent.action ?: return
        val pending = goAsync()
        val app = context.applicationContext

        
        ReminderNotifications.cancel(app, reminderId)

        CoroutineScope(Dispatchers.IO).launch {
            var changed = false
            try {
                com.cagri.real.platform.android.sync.SyncLocal.awaitInitialized()
                synchronized(ReminderRepository.mutationLock) {
                    val repository = ReminderRepository(app)
                    val reminder = repository.load().firstOrNull { it.id == reminderId }

                    if (reminder == null) {
                        
                        return@synchronized
                    }

                    when (action) {
                        ACTION_COMPLETE -> {
                            if (!reminder.isCompleted) {
                                repository.completeFromNotification(reminderId)
                                ReminderScheduler.reconcile(app, repository)
                                changed = true
                            }
                        }
                        ACTION_SNOOZE_10 -> {
                            if (!reminder.isCompleted) {
                                val now = System.currentTimeMillis()
                                val snoozeUntil = now + SNOOZE_DURATION_MILLIS
                                repository.snooze(reminderId, snoozeUntil)
                                ReminderScheduler.reconcile(app, repository)
                                changed = true
                            }
                        }
                    }
                }
                if (changed) {
                    ReminderRepository.notifyChanged()
                }
            } catch (e: Exception) {
                Log.e("Real", "Notification action failed for action=$action, id=$reminderId", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.cagri.real.ACTION_COMPLETE"
        const val ACTION_SNOOZE_10 = "com.cagri.real.ACTION_SNOOZE_10"
        const val SNOOZE_DURATION_MILLIS = 10 * 60 * 1000L
    }
}
