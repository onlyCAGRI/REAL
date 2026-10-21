package com.cagri.real.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cagri.real.data.ReminderRepository
import kotlinx.coroutines.*

open class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.cagri.real.platform.android.sync.SyncLocal.awaitInitialized()
                synchronized(ReminderRepository.mutationLock) {
                    val repository = ReminderRepository(app)
                    ReminderScheduler.reconcile(app, repository)
                    ReminderNotifications.restorePinned(app, repository.load())
                }
            } catch (e: Exception) {
                Log.e("Real", "Reminder reconciliation failed", e)
            } finally { pending.finish() }
        }
    }
}
