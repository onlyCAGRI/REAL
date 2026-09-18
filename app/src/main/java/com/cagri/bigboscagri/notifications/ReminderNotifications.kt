package com.cagri.bigboscagri.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.app.ActivityCompat
import com.cagri.bigboscagri.MainActivity
import com.cagri.bigboscagri.R
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.ReminderRepository

object ReminderNotifications {
    private const val CHANNEL_ID = "instant_reminders"
    private const val PINNED_CHANNEL_ID = "important_reminders"
    const val EXTRA_REMINDER_ID = "reminder_id"

    @Volatile private var channelsCreated = false

    fun ensureChannel(context: Context) {
        if (channelsCreated) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Anlık hatırlatıcılar",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Yazdığınız metni anında bildirim paneline gönderir."
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(NotificationChannel(
            PINNED_CHANNEL_ID, "Sabit önemli hatırlatıcılar", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Yıldızladığın aktif notlar. Uygulamadan yıldızı kaldırınca bildirim de kaldırılır."
            enableVibration(false)
            setShowBadge(false)
        })
        channelsCreated = true
    }

    private fun canNotify(context: Context, channelId: String): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(context: Context, reminder: ReminderItem, alerting: Boolean = false): Boolean {
        if (reminder.isCompleted) return false
        val id = reminder.id
        val text = reminder.text
        val pinned = reminder.shouldPinNotification
        val channelId = if (pinned && !alerting) PINNED_CHANNEL_ID else CHANNEL_ID
        ensureChannel(context)
        if (!canNotify(context, channelId)) return false
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("bigboscagri://reminder/$id")
            putExtra(EXTRA_REMINDER_ID, id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setCategory(android.app.Notification.CATEGORY_REMINDER)
            .setOngoing(pinned)
            .setSubText(if (pinned) "Önemli" else null)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setOnlyAlertOnce(!alerting)
            .setWhen(if (alerting) reminder.alertAt ?: reminder.createdAt else reminder.createdAt)
            .build()

        return try {
            val manager = context.getSystemService(NotificationManager::class.java)
            val previous = manager.activeNotifications.firstOrNull { it.id == id.hashCode() }
            if (previous != null && previous.notification.channelId != channelId) {
                manager.cancel(id.hashCode())
            }
            manager.notify(id.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false // Permission can change after the preflight check.
        }
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
    }

    fun refreshEdited(context: Context, reminder: ReminderItem) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (reminder.isCompleted) cancel(context, reminder.id)
        else if (reminder.shouldPinNotification || manager.activeNotifications.any { it.id == reminder.id.hashCode() }) {
            show(context, reminder)
        }
    }

    fun restorePinned(context: Context, reminders: List<ReminderItem>) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val saved = reminders.filter { it.shouldPinNotification }
        val savedIds = saved.map { it.id.hashCode() }.toSet()
        val visible = manager.activeNotifications
            .filter { it.isOngoing }.map { it.id }.toSet()
        // Reconcile with saved notes if the process stopped between a save and cancellation.
        visible.filter { it !in savedIds }.forEach { manager.cancel(it) }
        if (!canNotify(context, PINNED_CHANNEL_ID)) return
        saved.filter { it.id.hashCode() !in visible }.forEach {
            show(context, it)
        }
    }
}
