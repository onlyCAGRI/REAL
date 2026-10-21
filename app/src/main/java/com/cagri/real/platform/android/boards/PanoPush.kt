package com.cagri.real.platform.android.boards

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cagri.real.platform.android.auth.AndroidBackend
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Board push, device side. The server sends a tiny data message ONLY when someone else adds new content to a
 * board this user opted into. No polling, no background job: the OS wakes the service, which builds the notification.
 */
object PanoPush {
    const val CHANNEL = "pano_updates"
    const val EXTRA_BOARD = "pano_board_id"
    /** Set by PanoScreen while a board is on screen: no notification for what the user is already looking at. */
    @Volatile var openBoard: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pano updates", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private suspend fun fcmToken(): String? = suspendCancellableCoroutine { c ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { c.resume(it.result?.takeIf { _ -> it.isSuccessful }) }
    }

    /** Uploads this device's token for the signed-in account. Skips the request when the same account+token was already sent. */
    fun registerToken(context: Context, knownToken: String? = null) {
        val app = context.applicationContext
        scope.launch {
            val uid = AndroidBackend.auth.currentUser()?.id ?: return@launch
            val token = knownToken ?: fcmToken() ?: return@launch
            val prefs = app.getSharedPreferences("pano_push", Context.MODE_PRIVATE)
            if (prefs.getString("sent", null) == "$uid:$token") return@launch
            runCatching { BoardClient(uid).request("push-token", "POST", JSONObject().put("token", token)) }
                .onSuccess { prefs.edit().putString("sent", "$uid:$token").apply() }
        }
    }

    /**
     * Before signing out: this device's token is released from the account, so the next account that signs in here
     * can register it (the server never moves a token from one account to another). Best effort, a few seconds at most.
     */
    suspend fun releaseToken(context: Context) {
        val uid = AndroidBackend.auth.currentUser()?.id ?: return
        kotlinx.coroutines.withTimeoutOrNull(4_000) {
            val token = fcmToken() ?: return@withTimeoutOrNull
            runCatching { BoardClient(uid).request("push-token", "DELETE", JSONObject().put("token", token)) }
        }
        context.applicationContext.getSharedPreferences("pano_push", Context.MODE_PRIVATE).edit().remove("sent").apply()
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, boardId: String, title: String, by: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val open = Intent().setClassName(context, "com.cagri.real.MainActivity").putExtra(EXTRA_BOARD, boardId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, boardId.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(context.applicationInfo.icon) // swap for your monochrome status-bar icon
            .setContentTitle(title.ifBlank { "Pano" })
            .setContentText(if (by.isBlank()) "New content on this board" else "$by added something new")
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL).build()
        // One notification per board: a newer one replaces the old, never a stack of spam.
        NotificationManagerCompat.from(context).notify(boardId, 1, n)
    }
}

class PanoMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = PanoPush.registerToken(applicationContext, token)
    override fun onMessageReceived(message: RemoteMessage) {
        // A card was sent to this account: pull the share inbox now (the sync shows the notification).
        // "sync": a timed card was saved on REAL Web; pull it now so its reminder is set. "share_inbox": a card was sent.
        if (message.data["type"] == "share_inbox" || message.data["type"] == "sync") {
            com.cagri.real.platform.android.sync.SyncLocal.requestSync()
            return
        }
        // Ortak kategori: someone changed a shared card; pull it (a timed card sets its reminder here too).
        if (message.data["type"] == "category_cards") {
            com.cagri.real.sharedcategories.SharedCategorySync.request(applicationContext, 0)
            return
        }
        if (message.data["type"] != "pano_item") return
        val board = message.data["boardId"] ?: return
        if (PanoPush.openBoard == board) return
        PanoPush.show(applicationContext, board, message.data["title"].orEmpty(), message.data["by"].orEmpty())
    }
}
