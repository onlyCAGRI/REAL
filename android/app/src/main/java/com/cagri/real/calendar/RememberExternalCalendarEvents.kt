package com.cagri.real.calendar

import com.cagri.real.i18n.t
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cagri.real.data.ReminderItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class ExternalCalendarRead(val events: List<CalendarOccurrence> = emptyList(), val error: String? = null)

/**
 * Okuma yalnız şu durumlarda yapılır (sistem takvimi DİNLENMEZ):
 *  - bu ekran açılınca / uygulama öne gelince (lifecycle RESUMED),
 *  - görünür aralık (start/end) değişince,
 *  - kart eklenip silinince (realIds),
 *  - okuma seçimi / etkinlik<->kart bağı değişince (options, links),
 *  - [refreshKey] artınca (aşağı çekerek yenile bunu artırır).
 * Başka uygulamada takvim düzenleyip dönmek zaten "öne gelme" tetikler.
 */
@Composable
internal fun rememberExternalCalendarEvents(
    start: Long, end: Long, reminders: List<ReminderItem>, refreshKey: Int = 0
): ExternalCalendarRead {
    val context = LocalContext.current.applicationContext
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val store = remember(context) { CalendarStore(context) }
    val provider = remember(context) { DeviceCalendarProvider(context) }
    var revision by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf(ExternalCalendarRead()) }
    // Okuma yalnız HANGİ kartların var olduğuna bakar (kartla temsil edilen etkinlikleri gizlemek için).
    val realIds = remember(reminders) { reminders.mapTo(HashSet<Long>()) { it.id } }
    DisposableEffect(store) {
        val handler = Handler(Looper.getMainLooper())
        // SyncLocal broadcasts `changed(allKeys)` after every cloud sync (60 s poll, resume, each local write) even when
        // nothing changed; reacting to the key alone re-queried the system calendar after each of them. Only a real
        // change of the stored value counts.
        val watched = setOf("options", "links")
        fun rawValue(key: String): String? =
            runCatching { store.preferences.getString(key, null) }.getOrNull()
                ?: runCatching { store.preferences.all[key]?.toString() }.getOrNull()
        val seen = HashMap<String, String?>().also { m -> watched.forEach { m[it] = rawValue(it) } }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val keys = if (key == null) watched else if (key in watched) setOf(key) else emptySet()
            val changed = keys.any { k -> rawValue(k).let { now -> (seen[k] != now).also { seen[k] = now } } }
            if (changed) handler.post { revision++ }
        }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            store.preferences.unregisterOnSharedPreferenceChangeListener(listener)
            handler.removeCallbacksAndMessages(null)
        }
    }
    LaunchedEffect(start, end, revision, lifecycle, realIds, refreshKey) {
        if (!store.isCurrent()) { result = ExternalCalendarRead(); return@LaunchedEffect }
        if (!lifecycle.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        val options = store.options()
        if (options.readIds.isEmpty()) { result = ExternalCalendarRead(); return@LaunchedEffect }
        if (!provider.canRead()) { result = ExternalCalendarRead(error = t("Takvim okuma izni gerekli. Ayarlar → Takvim bağlantısı.")); return@LaunchedEffect }
        delay(150)
        try {
            val events = withContext(Dispatchers.IO) {
                val links = store.links().filter { it.key.substringBefore(':').toLongOrNull() in realIds && !it.suppressed }
                provider.occurrences(options, start, end, provider.representedLinkedIds(links))
            }
            if (store.isCurrent()) {
                val unchanged = result.error == null && result.events == events
                calendarSyncLog("reader: ${events.size} events, " + (if (unchanged) "no-op" else "CHANGED") + " refreshKey=${refreshKey}")
                if (!unchanged) result = ExternalCalendarRead(events)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            result = ExternalCalendarRead(error = t("Cihaz takvimi okunamadı. Ayarlardan yenileyebilirsin."))
        }
    }
    return result
}
