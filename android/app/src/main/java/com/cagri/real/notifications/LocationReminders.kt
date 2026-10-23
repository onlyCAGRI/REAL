package com.cagri.real.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
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
    /** The Geofencing API allows 100 per app; a few are left for safety. */
    private const val MAX = 90
    /** Once per process the geofences are added again: the system silently drops them when location is turned off
     *  or Play services resets, and the remembered set would otherwise keep REAL from ever adding them back. */
    private val session = System.currentTimeMillis()

    /** Play services lost the geofences (GEOFENCE_NOT_AVAILABLE…): forget the remembered set so the next sync adds them. */
    fun forget(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_REGISTERED).apply()
    }

    fun active(reminders: List<ReminderItem>): List<ReminderItem> =
        reminders.filter { it.locationTrigger != null && !it.isCompleted && !it.isDivider }.take(MAX)

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

    /**
     * Brings the registered geofences in line with the cards. Cheap when nothing changed: the set is remembered
     * together with the boot count and app version (a reboot or an update clears geofences).
     */
    fun sync(context: Context, reminders: List<ReminderItem>) {
        val app = context.applicationContext
        val active = active(reminders)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val boot = runCatching { Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(0)
        val version = runCatching {
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(app.packageManager.getPackageInfo(app.packageName, 0))
        }.getOrDefault(0L)
        val key = active.joinToString("|") { item ->
            val t = item.locationTrigger!!
            "${item.id}:${t.latitude}:${t.longitude}:${t.radiusMeters}:${t.onExit}"
        } + "#$boot#$version#$session"
        if (prefs.getString(KEY_REGISTERED, null) == key) return
        val client = LocationServices.getGeofencingClient(app)
        val intent = pendingIntent(app)
        client.removeGeofences(intent)
        if (active.isEmpty()) { prefs.edit().putString(KEY_REGISTERED, key).apply(); return }
        // Without permission nothing is stored, so the next reconcile tries again once it is granted.
        if (!hasLocation(app) || !hasBackground(app)) { prefs.edit().remove(KEY_REGISTERED).apply(); return }
        val request = GeofencingRequest.Builder()
            // Already inside when it is set: wait for a real arrival (or departure), do not fire at once.
            .setInitialTrigger(0)
            .addGeofences(active.map { item ->
                val t = item.locationTrigger!!
                Geofence.Builder()
                    .setRequestId(item.id.toString())
                    .setCircularRegion(t.latitude, t.longitude, t.radiusMeters.toFloat())
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(if (t.onExit) Geofence.GEOFENCE_TRANSITION_EXIT else Geofence.GEOFENCE_TRANSITION_ENTER)
                    .build()
            })
            .build()
        try {
            client.addGeofences(request, intent)
                .addOnSuccessListener { prefs.edit().putString(KEY_REGISTERED, key).apply() }
                .addOnFailureListener { e -> prefs.edit().remove(KEY_REGISTERED).apply(); Log.w("Real", "Geofences not added", e) }
        } catch (e: SecurityException) {
            prefs.edit().remove(KEY_REGISTERED).apply()
        }
    }
}

/** A watched place was reached (or left): notify once (or ring, for an alarm card) and stop watching it. */
class LocationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w("Real", "Geofence error ${event.errorCode}")
            LocationReminders.forget(context)
            return
        }
        val ids = event.triggeringGeofences?.mapNotNull { it.requestId.toLongOrNull() }.orEmpty()
        if (ids.isEmpty()) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.cagri.real.platform.android.sync.SyncLocal.awaitInitialized()
                synchronized(ReminderRepository.mutationLock) {
                    val repository = ReminderRepository(app)
                    val cards = repository.load()
                    ids.forEach { id ->
                        val item = cards.firstOrNull { it.id == id && it.locationTrigger != null && !it.isCompleted } ?: return@forEach
                        if (item.isAlarm) RealAlarm.ring(app, item) else ReminderNotifications.show(app, item, alerting = true)
                        repository.setLocationTrigger(id, null)
                    }
                    ReminderScheduler.reconcile(app, repository)
                }
            } catch (e: Exception) {
                Log.e("Real", "Location reminder failed", e)
            } finally { pending.finish() }
        }
    }
}
