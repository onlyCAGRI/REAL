package com.cagri.real.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import com.cagri.real.MainActivity
import com.cagri.real.R
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.ReminderRepository
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * "Çıkma vakti" (DepartureReminder.swift): a card with a time and a place gets one more notification when it is time to
 * leave, from Apple Maps' driving time with the traffic expected then (plus 10 minutes). Planned from where the phone
 * last was (no location prompt of its own: only when location is already allowed), for cards within the next 24 hours.
 * Re-planned at most every two hours per card, so traffic estimates stay fresh without asking on every reconcile.
 */
object DepartureReminder {
    private const val MARGIN_MS = 10 * 60_000L
    private const val REFRESH_MS = 2 * 60 * 60_000L
    private const val ACTION = "com.cagri.real.DEPART"
    private const val EXTRA_MINUTES = "minutes"
    private const val EXTRA_ARRIVAL = "arrival"
    private const val ETA_URL = "https://real-backend-staging.cagrigul1907.workers.dev/api/place-search"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val planning = Mutex()

    private fun prefs(context: Context) = context.getSharedPreferences("real_departure", Context.MODE_PRIVATE)

    fun plan(context: Context, reminders: List<ReminderItem>) {
        val app = context.applicationContext
        scope.launch { planning.withLock { runCatching { planNow(app, reminders) } } }
    }

    private suspend fun planNow(context: Context, reminders: List<ReminderItem>) {
        val now = System.currentTimeMillis()
        val prefs = prefs(context)
        val wanted = reminders.filter { r ->
            val at = r.alertAt
            !r.isCompleted && at != null && at - now in 20 * 60_000L..24 * 60 * 60_000L &&
                r.location?.latitude != null && r.location.longitude != null
        }
        // Cards that no longer qualify (done, moved, place removed): their departure alarm goes.
        prefs.all.keys.filter { it.startsWith("d_") }.forEach { key ->
            val id = key.removePrefix("d_").toLongOrNull() ?: return@forEach
            if (wanted.none { it.id == id }) { cancel(context, id); prefs.edit().remove(key).apply() }
        }
        if (wanted.isEmpty() || !locationAllowed(context)) return
        val here = lastLocation(context) ?: return
        wanted.forEach { r ->
            val arrival = r.alertAt ?: return@forEach
            val saved = prefs.getString("d_${r.id}", null)?.split(':')
            if (saved?.getOrNull(0)?.toLongOrNull() == arrival && (saved.getOrNull(2)?.toLongOrNull() ?: 0L) > now - REFRESH_MS) return@forEach
            val place = r.location ?: return@forEach
            val out = FloatArray(1)
            Location.distanceBetween(here.latitude, here.longitude, place.latitude!!, place.longitude!!, out)
            // Close by: nothing to plan.
            if (out[0] <= 1500f) { cancel(context, r.id); prefs.edit().remove("d_${r.id}").apply(); return@forEach }
            val seconds = eta(here, place.latitude, place.longitude, arrival - 30 * 60_000L) ?: return@forEach
            val leave = arrival - seconds * 1000L - MARGIN_MS
            if (leave <= now + 60_000L) { cancel(context, r.id); prefs.edit().remove("d_${r.id}").apply(); return@forEach }
            schedule(context, r.id, leave, ((seconds + 30) / 60).coerceAtLeast(1), arrival)
            prefs.edit().putString("d_${r.id}", "$arrival:$leave:$now").apply()
        }
    }

    private fun locationAllowed(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The phone's last position; when none is cached (or it is over 30 minutes old) one fresh low-power fix. */
    @SuppressLint("MissingPermission")
    private suspend fun lastLocation(context: Context): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cached: Location? = suspendCancellableCoroutine { c ->
            client.lastLocation.addOnCompleteListener { c.resume(if (it.isSuccessful) it.result else null) }
        }
        if (cached != null && System.currentTimeMillis() - cached.time < 30 * 60_000L) return cached
        val fresh: Location? = kotlinx.coroutines.withTimeoutOrNull(20_000L) {
            suspendCancellableCoroutine { c ->
                val token = com.google.android.gms.tasks.CancellationTokenSource()
                c.invokeOnCancellation { token.cancel() }
                client.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                    .addOnCompleteListener { c.resume(if (it.isSuccessful) it.result else null) }
            }
        }
        return fresh ?: cached
    }

    /** Driving seconds from [here] to the place, with the traffic expected at [departure]; null offline or signed out. */
    private suspend fun eta(here: Location, lat: Double, lng: Double, departure: Long): Long? = runCatching {
        val body = JSONObject().put("eta", JSONObject()
            .put("from", JSONObject().put("lat", here.latitude).put("lng", here.longitude))
            .put("to", JSONObject().put("lat", lat).put("lng", lng))
            .put("departure", departure))
        val response = com.cagri.real.network.WorkerClient(com.cagri.real.platform.android.auth.AndroidBackend.workerTransport)
            .postJson(ETA_URL, body.toString(), 10_000, 15_000, readErrorBody = false)
        if (response.statusCode !in 200..299) return@runCatching null
        JSONObject(response.body).optDouble("seconds").takeIf { it.isFinite() && it > 0 }?.toLong()
    }.getOrNull()

    /** A driving route from where the phone is now to a card's place (the card map near its time). */
    data class Route(val points: List<Pair<Double, Double>>, val minutes: Long?, val origin: Pair<Double, Double>)

    /** Null without location permission, offline, signed out, or when the place is close by (≤ 1.5 km). */
    suspend fun route(context: Context, lat: Double, lng: Double): Route? {
        if (!locationAllowed(context)) return null
        val here = lastLocation(context.applicationContext) ?: return null
        val out = FloatArray(1)
        Location.distanceBetween(here.latitude, here.longitude, lat, lng, out)
        if (out[0] <= 1500f) return null
        return routeBetween(here.latitude, here.longitude, lat, lng)
    }

    /** The phone's position (cached or one fresh fix); null without location permission. */
    suspend fun currentLocation(context: Context): Location? =
        if (!locationAllowed(context)) null else lastLocation(context.applicationContext)

    /** A driving route between two explicit points (e.g. the "Varınca" intro map); null offline or signed out. */
    suspend fun routeBetween(fromLat: Double, fromLng: Double, lat: Double, lng: Double): Route? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("route", JSONObject()
                    .put("from", JSONObject().put("lat", fromLat).put("lng", fromLng))
                    .put("to", JSONObject().put("lat", lat).put("lng", lng)))
                val response = com.cagri.real.network.WorkerClient(com.cagri.real.platform.android.auth.AndroidBackend.workerTransport)
                    .postJson(ETA_URL, body.toString(), 10_000, 15_000, readErrorBody = false)
                if (response.statusCode !in 200..299) return@runCatching null
                val json = JSONObject(response.body)
                val array = json.optJSONArray("points") ?: return@runCatching null
                val points = (0 until array.length()).mapNotNull { i ->
                    val p = array.optJSONArray(i) ?: return@mapNotNull null
                    (p.optDouble(0) to p.optDouble(1)).takeIf { !it.first.isNaN() && !it.second.isNaN() }
                }
                val seconds = json.optDouble("seconds").takeIf { it.isFinite() && it > 0 }
                Route(points, seconds?.let { ((it + 30) / 60).toLong().coerceAtLeast(1) }, fromLat to fromLng)
                    .takeIf { points.size >= 2 }
            }.getOrNull()
        }
    }

    private fun pending(context: Context, id: Long, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(context, ("depart$id").hashCode(),
            Intent(context, DepartureReceiver::class.java).apply {
                action = ACTION
                data = Uri.parse("real://depart/$id")
                putExtra(ReminderNotifications.EXTRA_REMINDER_ID, id)
                extras()
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun schedule(context: Context, id: Long, at: Long, minutes: Long, arrival: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val intent = pending(context, id) { putExtra(EXTRA_MINUTES, minutes); putExtra(EXTRA_ARRIVAL, arrival) }
        try {
            if (ReminderScheduler.canScheduleExact(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id))
    }

    /** The alarm went off: one notification, only while the card still has that time and is not done. */
    internal fun show(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1L)
        val arrival = intent.getLongExtra(EXTRA_ARRIVAL, 0L)
        val minutes = intent.getLongExtra(EXTRA_MINUTES, 0L)
        val reminder = ReminderRepository(context).load().firstOrNull { it.id == id } ?: return
        if (reminder.isCompleted || reminder.alertAt != arrival) return
        ReminderNotifications.ensureChannel(context)
        val name = reminder.location?.placeName?.trim().orEmpty()
        val what = name.ifEmpty { reminder.text.lineSequence().firstOrNull()?.trim().orEmpty() }.ifEmpty { com.cagri.real.i18n.t("Yol") }
        val clock = SimpleDateFormat(if (com.cagri.real.i18n.I18n.isEnglish) "h:mm a" else "HH:mm", com.cagri.real.i18n.I18n.locale).format(Date(arrival))
        val open = PendingIntent.getActivity(context, id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("real://reminder/$id")
                putExtra(ReminderNotifications.EXTRA_REMINDER_ID, id)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = com.cagri.real.i18n.t("{0} · trafikle yaklaşık {1} dk. {2}'da orada olmak için şimdi çık.", what, minutes, clock)
        val notification = android.app.Notification.Builder(context, ReminderNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_r)
            .setContentTitle(com.cagri.real.i18n.t("Çıkma vakti"))
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setCategory(android.app.Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(("depart$id").hashCode(), notification)
        } catch (_: SecurityException) { }
        prefs(context).edit().remove("d_$id").apply()
    }
}

class DepartureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.cagri.real.platform.android.sync.SyncLocal.awaitInitialized()
                DepartureReminder.show(app, intent)
            } finally { pending.finish() }
        }
    }
}
