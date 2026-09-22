package com.cagri.bigboscagri.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cagri.bigboscagri.MainActivity
import com.cagri.bigboscagri.R

object NowBarProbe {
    private const val CHANNEL_ID = "real_now_bar_probe_v2"
    private const val NOTIFICATION_ID = 99991

    fun postTest(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        
        val channel = NotificationChannel(
            CHANNEL_ID,
            "REAL Now Bar Test",
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, ProbeCancelReceiver::class.java)
        val cancelPending = PendingIntent.getBroadcast(
            context,
            2,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val samsungExtras = Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 1)
            putString("android.ongoingActivityNoti.primaryInfo", "REAL")
            putString("android.ongoingActivityNoti.secondaryInfo", "Now Bar testi aktif")
            putString("android.ongoingActivityNoti.chipExpandedText", "REAL")
            putString("android.ongoingActivityNoti.nowbarPrimaryInfo", "REAL")
            putString("android.ongoingActivityNoti.nowbarSecondaryInfo", "Now Bar testi aktif")
            putInt("android.ongoingActivityNoti.actionType", 1)
            putInt("android.ongoingActivityNoti.actionPrimarySet", 0)
            putInt("android.ongoingActivityNoti.chipBgColor", 0xFF91B9FF.toInt())
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // Use existing valid small icon
            .setContentTitle("REAL")
            .setContentText("Now Bar testi aktif")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPending)
            .setExtras(samsungExtras)
            .addAction(0, "Bitir", cancelPending)

        if (Build.VERSION.SDK_INT >= 36) { // Android 16 (API 36) minimum for proper promoted status mapping
            // In AndroidX Core 1.17+, setRequestPromotedOngoing accepts a boolean
            builder.setRequestPromotedOngoing(true)
        }

        try {
            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Permission missing
        }
    }

    fun cancelTest(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(NOTIFICATION_ID)
    }

    fun canPostPromoted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return try {
            nm.canPostPromotedNotifications()
        } catch (e: NoSuchMethodError) {
            false
        }
    }

    fun hasPromotableCharacteristics(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("REAL")
            .setContentText("Now Bar testi aktif")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true)

        val notification = builder.build()
        // Determine if notification builder successfully flagged characteristics internally
        return try {
            val flags = notification.flags
            // Ensure ongoing is respected
            if ((flags and Notification.FLAG_ONGOING_EVENT) == 0) return false
            // Note: actual promotes checking via Notification API is complex, this asserts compat builder holds
            true
        } catch (e: Exception) {
            false
        }
    }

    fun buildSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 36) return null
        // Fallback to explicit string if Settings constants are hidden in current compileSdk
        val action = "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"
        val intent = Intent(action).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        return if (intent.resolveActivity(context.packageManager) != null) intent else null
    }
}

class ProbeCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NowBarProbe.cancelTest(context)
    }
}
