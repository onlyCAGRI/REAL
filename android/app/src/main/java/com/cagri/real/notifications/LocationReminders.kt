package com.cagri.real.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.cagri.real.data.LocationTrigger
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.ReminderRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Konuma varınca / ayrılınca hatırlat": one geofence per waiting card, nothing else. With no such card every
 * geofence is removed, so REAL does not use location at all; a card's geofence goes away the moment it fires.
 */
object LocationReminders {
    private const val PREFS = "real_location_reminders"
    private const val KEY_REGISTERED = "registered"
    /** The set being added right now: a newer sync makes an older one's late callback a no-op. */
    private const val KEY_PENDING = "pending"
    const val ACTION_RETRY = "com.cagri.real.LOCATION_RETRY"
    /** Location off (or Play services busy): adding is tried again this often until it works. */
    private const val RETRY_MS = 30 * 60_000L
    /** An arrival origin older than this is read again, so the route starts nearer to where the trip began. */
    private const val ORIGIN_MAX_AGE_MS = 3 * 60 * 60_000L
    /** The Geofencing API allows 100 per app; a few are left for safety. */
    private const val MAX = 90
    /** Once per process the geofences are added again: the system silently drops them when location is turned off
     *  or Play services resets, and the remembered set would otherwise keep REAL from ever adding them back. */
    private val session = System.currentTimeMillis()

    /** Play services lost the geofences (GEOFENCE_NOT_AVAILABLE…): forget the remembered set so the next sync adds them. */
    fun forget(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_REGISTERED).apply()
    }

    /** GPS drift easily crosses a small circle while the phone lies still, so "ayrılınca" never watches less than this. */
    fun radiusFor(trigger: LocationTrigger): Int =
        if (trigger.onExit) trigger.radiusMeters.coerceAtLeast(LocationTrigger.EXIT_MIN_RADIUS) else trigger.radiusMeters

    /** Drops the per-card values (origins, snoozed fires) of cards that are gone. */
    private fun prune(context: Context, all: List<ReminderItem>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = all.map { it.id }.toSet()
        val stale = prefs.all.keys.filter { k ->
            val id = when {
                k.startsWith("origin_") -> k.removePrefix("origin_")
                k.startsWith("fired_") -> k.removePrefix("fired_")
                else -> return@filter false
            }
            id.toLongOrNull() !in ids
        }
        if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach { remove(it) } }.apply()
    }

    /**
     * Where the phone was when a card's arrival geofence was added: the "came from" point for the route line on the
     * arrival focus screen. Only the cached fused position is read (no fresh fix). It is read again when the card's
     * place changes or the stored one is older than [ORIGIN_MAX_AGE_MS].
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun rememberOrigins(context: Context, active: List<ReminderItem>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val missing = active.filter { item ->
            val t = item.locationTrigger ?: return@filter false
            if (t.onExit) return@filter false
            val parts = prefs.getString("origin_${item.id}", null)?.split(',').orEmpty()
            val at = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            parts.getOrNull(3) != "${t.latitude}:${t.longitude}" || now - at > ORIGIN_MAX_AGE_MS
        }
        if (missing.isEmpty() || !hasLocation(context)) return
        runCatching {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
                if (loc == null) return@addOnSuccessListener
                val edit = prefs.edit()
                missing.forEach { item ->
                    val t = item.locationTrigger ?: return@forEach
                    edit.putString("origin_${item.id}", "${loc.latitude},${loc.longitude},$now,${t.latitude}:${t.longitude}")
                }
                edit.apply()
            }
        }
    }

    /** The point remembered by [rememberOrigins] for a card, if any. */
    fun storedOrigin(context: Context, id: Long): Pair<Double, Double>? {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("origin_$id", null) ?: return null
        val parts = raw.split(',')
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lng = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        return lat to lng
    }

    /** "10 dk ertele" on an arrival / departure notification: the snoozed alert at [until] keeps saying "Vardın · X". */
    fun rememberSnoozedFire(context: Context, id: Long, until: Long, placeName: String, onExit: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("fired_$id", "$until\t$onExit\t$placeName").apply()
    }

    /** The fire [rememberSnoozedFire] stored for exactly this alert time (only place and direction are meaningful). */
    fun snoozedFire(context: Context, reminder: ReminderItem): LocationTrigger? {
        val at = reminder.alertAt ?: return null
        val parts = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("fired_${reminder.id}", null)?.split('\t', limit = 3) ?: return null
        if (parts.size < 3 || parts[0].toLongOrNull() != at) return null
        return LocationTrigger(parts[2], 0.0, 0.0, onExit = parts[1].toBoolean())
    }

    /** The phone's cached position (no fresh fix), for the "you are outside" note when setting a departure reminder. */
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun lastKnown(context: Context): Pair<Double, Double>? {
        if (!hasLocation(context)) return null
        return runCatching {
            kotlinx.coroutines.suspendCancellableCoroutine { c ->
                LocationServices.getFusedLocationProviderClient(context).lastLocation
                    .addOnSuccessListener { loc -> if (c.isActive) c.resumeWith(Result.success(loc?.let { it.latitude to it.longitude })) }
                    .addOnFailureListener { if (c.isActive) c.resumeWith(Result.success(null)) }
            }
        }.getOrNull()
    }

    /** Newest cards first, so past [MAX] it is the old ones that are not watched. */
    fun active(reminders: List<ReminderItem>): List<ReminderItem> =
        reminders.filter { it.locationTrigger != null && !it.isCompleted && !it.isDivider }
            .sortedByDescending { it.createdAt }.take(MAX)

    fun hasLocation(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** "Her zaman izin ver": needed for geofences to fire while REAL is closed (Android 10+). */
    fun hasBackground(context: Context): Boolean = Build.VERSION.SDK_INT < 29 ||
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, LocationReminderReceiver::class.java),
        // Mutable: Play services adds the triggering geofences to it.
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
    )

    private fun retryIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, LocationReminderReceiver::class.java).setAction(ACTION_RETRY),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Location turned off drops every geofence and adding fails until it is back on; this tries again later. */
    private fun scheduleRetry(context: Context) {
        runCatching {
            context.getSystemService(AlarmManager::class.java)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + RETRY_MS, retryIntent(context))
        }
    }

    private fun cancelRetry(context: Context) {
        runCatching { context.getSystemService(AlarmManager::class.java).cancel(retryIntent(context)) }
    }

    /**
     * Brings the registered geofences in line with the cards. Cheap when nothing changed: the set is remembered
     * together with the boot count and app version (a reboot or an update clears geofences).
     */
    fun sync(context: Context, reminders: List<ReminderItem>) {
        val app = context.applicationContext
        val active = active(reminders)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prune(app, reminders)
        val boot = runCatching { Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(0)
        val version = runCatching {
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(app.packageManager.getPackageInfo(app.packageName, 0))
        }.getOrDefault(0L)
        val key = active.joinToString("|") { item ->
            val t = item.locationTrigger!!
            "${item.id}:${t.latitude}:${t.longitude}:${radiusFor(t)}:${t.onExit}"
        } + "#$boot#$version#$session"
        if (prefs.getString(KEY_REGISTERED, null) == key) return
        prefs.edit().putString(KEY_PENDING, key).apply()
        val client = LocationServices.getGeofencingClient(app)
        val intent = pendingIntent(app)
        // Adding waits for the removal, so the old set can never land after (and over) the new one.
        client.removeGeofences(intent).addOnCompleteListener {
            if (prefs.getString(KEY_PENDING, null) != key) return@addOnCompleteListener
            if (active.isEmpty()) { prefs.edit().putString(KEY_REGISTERED, key).apply(); cancelRetry(app); return@addOnCompleteListener }
            // Without permission nothing is stored, so the next reconcile tries again once it is granted (the card shows the warning).
            if (!hasLocation(app) || !hasBackground(app)) { prefs.edit().remove(KEY_REGISTERED).apply(); cancelRetry(app); return@addOnCompleteListener }
            val request = GeofencingRequest.Builder()
                // Already inside when it is set: wait for a real arrival (or departure), do not fire at once.
                .setInitialTrigger(0)
                .addGeofences(active.map { item ->
                    val t = item.locationTrigger!!
                    Geofence.Builder()
                        .setRequestId(item.id.toString())
                        .setCircularRegion(t.latitude, t.longitude, radiusFor(t).toFloat())
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(if (t.onExit) Geofence.GEOFENCE_TRANSITION_EXIT else Geofence.GEOFENCE_TRANSITION_ENTER)
                        .build()
                })
                .build()
            try {
                client.addGeofences(request, intent)
                    .addOnSuccessListener {
                        if (prefs.getString(KEY_PENDING, null) == key) prefs.edit().putString(KEY_REGISTERED, key).apply()
                        cancelRetry(app)
                        rememberOrigins(app, active)
                    }
                    .addOnFailureListener { e ->
                        prefs.edit().remove(KEY_REGISTERED).apply()
                        scheduleRetry(app)
                        Log.w("Real", "Geofences not added", e)
                    }
            } catch (e: SecurityException) {
                prefs.edit().remove(KEY_REGISTERED).apply()
            }
        }
    }
}

/** A watched place was reached (or left): notify once (or ring, for an alarm card) and stop watching it. */
class LocationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action == LocationReminders.ACTION_RETRY) { reconcile(app); return }
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            // Usually location was turned off: every geofence is gone. Add them again (or keep retrying until it is back).
            Log.w("Real", "Geofence error ${event.errorCode}")
            LocationReminders.forget(context)
            reconcile(app)
            return
        }
        val ids = event.triggeringGeofences?.mapNotNull { it.requestId.toLongOrNull() }.orEmpty()
        if (ids.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.cagri.real.platform.android.sync.SyncLocal.awaitInitialized()
                synchronized(ReminderRepository.mutationLock) {
                    val repository = ReminderRepository(app)
                    val cards = repository.load()
                    ids.forEach { id ->
                        val item = cards.firstOrNull { it.id == id && it.locationTrigger != null && !it.isCompleted } ?: return@forEach
                        val shown = if (item.isAlarm) { RealAlarm.ring(app, item); true }
                            else ReminderNotifications.show(app, item, alerting = true, locationTriggerFired = item.locationTrigger)
                        // Notifications off: the trigger stays, so the next arrival (or departure) tries again.
                        if (shown) repository.setLocationTrigger(id, null)
                        else Log.w("Real", "Location reminder $id not shown; kept")
                    }
                    ReminderScheduler.reconcile(app, repository)
                }
            } catch (e: Exception) {
                Log.e("Real", "Location reminder failed", e)
            } finally { pending.finish() }
        }
    }

    private fun reconcile(app: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.cagri.real.platform.android.sync.SyncLocal.awaitInitialized()
                synchronized(ReminderRepository.mutationLock) { ReminderScheduler.reconcile(app, ReminderRepository(app)) }
            } catch (e: Exception) {
                Log.e("Real", "Location reminder resync failed", e)
            } finally { pending.finish() }
        }
    }
}
