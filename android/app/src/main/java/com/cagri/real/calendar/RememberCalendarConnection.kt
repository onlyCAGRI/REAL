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
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val CalendarReceiptKeys = setOf("options", "links", "card_associations", "card_publications", "event_details")

internal class CalendarConnectionState(val store: CalendarStore, val provider: DeviceCalendarProvider) {
    var options by mutableStateOf(store.options())
    var calendars by mutableStateOf<List<DeviceCalendar>>(emptyList())
    var status by mutableStateOf("")
    var busy by mutableStateOf(false)
    var revision by mutableIntStateOf(0)
    var request by mutableIntStateOf(0)
    var operation = 0
    // What the last COMPLETE run covered. A later trigger that changes none of these is skipped, so unrelated
    // card edits (checklist ticks, notes ...) no longer restart publishing/reconcile.
    var lastRunSignature: Any? = null
    var lastRunRequest = -1
    var lastRunToken = 0L
    var lastRunClean = false
    // True once a non-empty card list was seen this session. Until then an empty list may just be "not loaded yet",
    // so nothing is removed from the calendar because of it.
    var cardsSeen = false
    var publicationCardId by mutableStateOf<Long?>(null)
    var publicationStatus by mutableStateOf("")
    fun offerPublication(cardId: Long) {
        if (store.isCurrent() && runCatching { CalendarCardLinks(store).hasExternalTarget(cardId) }.getOrDefault(false))
            publicationCardId = cardId
    }
    fun refresh() { request++ }
    companion object { val writeMutex = Mutex() }
    fun save(options: CalendarOptions) { store.saveOptions(options); this.options = options; refresh() }
}

/** Everything a reconcile run depends on that can come from REAL cards: planned occurrences + card versions. */
private fun calendarRunSignature(
    state: CalendarConnectionState, reminders: List<ReminderItem>, options: CalendarOptions, now: Long,
    existingCardIds: Set<Long>
): List<Any?>? = runCatching {
    if (!options.exportEnabled) return@runCatching listOf<Any?>(options, false)
    val externalIds = runCatching { CalendarCardLinks(state.store).associations().map { it.cardId }.toSet() }.getOrDefault(emptySet())
    val plans = calendarPlans(reminders, options, state.store.durationMinutes(), now, linkedExternalCardIds = externalIds)
    val planned = plans.mapTo(HashSet<Long>()) { it.reminderId }
    val versions = reminders.filter { it.id in planned }.map { it.id to it.updatedAt }
    // Cards that were deleted in REAL but still own calendar copies: their presence forces a run (cleanup), even if
    // the deleted card had no future occurrence and therefore did not change `plans`.
    val cardIds = existingCardIds
    val orphans = runCatching {
        state.store.links().mapNotNull { it.key.substringBefore(':').toLongOrNull() }.filter { it !in cardIds }.toSet()
    }.getOrDefault(emptySet())
    listOf<Any?>(options, LocalDate.now().toEpochDay(), plans, versions, orphans)
}.getOrNull() // An unreadable signature must not create a random invalidation token.

/** Foreground reconciliation only. Device sync adapters own network sync, not REAL's backend. */
@Composable
internal fun rememberCalendarConnection(reminders: List<ReminderItem>): CalendarConnectionState {
    val context = LocalContext.current.applicationContext
    // Defined below from the stable, calendar-relevant projection.
    val owner = LocalLifecycleOwner.current
    val lifecycle by owner.lifecycle.currentStateFlow.collectAsState()
    val state = remember(context) { CalendarConnectionState(CalendarStore(context), DeviceCalendarProvider(context)) }
    val input = remember(reminders, state.options) { calendarRunInput(reminders, state.options) }
    val latestInput = rememberUpdatedState(input)
    DisposableEffect(state, lifecycle, state.request) {
        val handler = Handler(Looper.getMainLooper())
        // Only keys that really feed this state. Other writes in these files (planner view mode, shapes ...) used to
        // bump `revision`, which recomposes every screen that reads calendarSyncStatus().
        // SyncLocal calls `changed(allKeys)` on EVERY wrapper after EVERY cloud sync (60 s poll, resume, each local write),
        // whether or not a value changed. Reacting to the key alone made each cloud sync start a calendar run, and the
        // calendar run's own receipt writes started the next cloud sync. So a notification only counts when the stored
        // value of that key really differs from the last one seen.
        fun rawValue(prefs: SharedPreferences, key: String): String? =
            runCatching { prefs.getString(key, null) }.getOrNull() ?: runCatching { prefs.all[key]?.toString() }.getOrNull()
        fun listenerFor(prefs: SharedPreferences): SharedPreferences.OnSharedPreferenceChangeListener {
            val seen = HashMap<String, String?>()
            for (k in CalendarReceiptKeys) seen[k] = rawValue(prefs, k)
            return SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                val keys = if (key == null) CalendarReceiptKeys else if (key in CalendarReceiptKeys) setOf(key) else emptySet()
                val changed = keys.filter { k -> rawValue(prefs, k).let { now -> (seen[k] != now).also { seen[k] = now } } }
                if (changed.isNotEmpty()) {
                    calendarSyncLog("listener: changed=${changed}")
                    handler.post { if (state.store.isCurrent()) {
                        if ("options" in changed) state.options = state.store.options()
                        if ("event_details" in changed || "card_associations" in changed) state.request++
                        state.revision++
                    } }
                }
            }
        }
        val storeListener = listenerFor(state.store.preferences)
        state.store.preferences.registerOnSharedPreferenceChangeListener(storeListener)
        val planner = com.cagri.real.platform.android.sync.SyncLocal.preferences(context, "real_planner_v1")
        val plannerListener = listenerFor(planner)
        planner.registerOnSharedPreferenceChangeListener(plannerListener)
        // No CalendarContract observer here: nothing in `revision`'s readers depends on provider rows (they read
        // REAL's own receipts), and the effect below refreshes calendars on every resume / request.
        onDispose {
            state.store.preferences.unregisterOnSharedPreferenceChangeListener(storeListener)
            planner.unregisterOnSharedPreferenceChangeListener(plannerListener)
            handler.removeCallbacksAndMessages(null)
        }
    }
    // Provider notifications never cancel a running write. They only trigger the separate read projection.
    val resumeToken = remember(lifecycle) { System.nanoTime() }
    LaunchedEffect(input, state.options, state.request, lifecycle) {
        if (!lifecycle.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        if (!state.provider.canRead() || !state.store.isCurrent()) {
            if (state.options.readIds.isNotEmpty() || state.options.exportEnabled) state.status = t("Takvim izni gerekli. Ayarlardan yeniden izin ver.")
            return@LaunchedEffect
        }
        delay(400)
        if (input.existingCardIds.isNotEmpty()) state.cardsSeen = true
        val snapshot = state.options
        val requestAtStart = state.request
        val signature = withContext(Dispatchers.Default) {
            calendarRunSignature(state, input.cards, snapshot, System.currentTimeMillis(), input.existingCardIds)
        }
        if (signature == null) {
            state.lastRunClean = false
            state.status = t("Takvim kayıtları okunamadı. Yeniden eşitle.")
            return@LaunchedEffect
        }
        if (state.lastRunClean && state.lastRunRequest == requestAtStart && state.lastRunToken == resumeToken &&
            state.lastRunSignature == signature) {
            calendarSyncLog("main: skipped, no calendar-relevant change")
            return@LaunchedEffect
        }
        calendarSyncLog("main: run request=${requestAtStart} cards=${input.cards.size} export=${snapshot.exportEnabled}")
        val operation = ++state.operation
        state.busy = true
        try {
            val result = withContext(Dispatchers.IO) { CalendarConnectionState.writeMutex.withLock {
                check(state.store.isCurrent())
                val calendars = state.provider.calendars()
                val result = if (snapshot.exportEnabled && state.provider.canWrite()) {
                    val now = System.currentTimeMillis()
                    val cardLinks = CalendarCardLinks(state.store)
                    val externalIds = cardLinks.associations().map { it.cardId }.toSet()
                    val validTarget = calendars.any { it.id == snapshot.targetId && it.identity == snapshot.targetIdentity && it.writable }
                    val nativeIds = calendarPlans(input.cards, snapshot, state.store.durationMinutes(), now,
                        linkedExternalCardIds = externalIds).map { it.reminderId }.toSet()
                    var unpublished = 0
                    for (card in input.cards.filter { validTarget && it.id in nativeIds }) {
                        check(state.store.isCurrent() && state.store.options() == snapshot && latestInput.value == input)
                        try {
                            val publication = prepareCalendarPublication(state, card,
                                com.cagri.real.platform.android.share.CardShareClient.Options())
                            // Write only when the receipt really changes: an unconditional write made every run look like
                            // a local edit to the cloud sync, which then woke this run again.
                            val receipt = publication.copy(applied = true)
                            if (cardLinks.publications()[card.id] != receipt) cardLinks.savePublication(receipt)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { unpublished++; calendarSyncLog("publish FAILED card=${card.id}: $error") }
                    }
                    state.publicationStatus = if (unpublished > 0)
                        t("{0} kartın web bağlantısı güncellenemedi. Kartların REAL'de korunuyor; yeniden eşitle.", unpublished) else ""
                    CalendarExporter(state.provider, state.store, updatePublicLink = { existing, plan, link ->
                        val previous = cardLinks.publications()[plan.reminderId]?.previousUrl
                        if (existing.description == DeviceCalendarProvider.description(link.marker, previous) && plan.publicUrl != null) {
                            check(state.store.isCurrent() && state.store.options() == snapshot && latestInput.value == input)
                            state.provider.updateDescription(existing, DeviceCalendarProvider.description(link.marker, plan.publicUrl)) != null
                        } else false
                    }) { state.store.options() == snapshot && latestInput.value == input }.let { exporter ->
                        val plans = calendarPlans(input.cards, snapshot, state.store.durationMinutes(), now,
                            publicUrls = CalendarCardLinks(state.store).publicUrls(),
                            linkedExternalCardIds = CalendarCardLinks(state.store).associations().map { it.cardId }.toSet())
                        exporter.reconcile(snapshot, plans, now,
                            existingCardIds = input.existingCardIds,
                            allowRemoval = input.existingCardIds.isNotEmpty() || state.cardsSeen
                        ).also { calendarSyncLog("export: plans=${plans.size} validTarget=${validTarget} result=${it}") }
                    }
                } else {
                    calendarSyncLog("export: skipped enabled=${snapshot.exportEnabled} canWrite=${state.provider.canWrite()}")
                    null
                }
                calendars to result
            } }
            if (state.store.isCurrent() && state.operation == operation) {
                state.calendars = result.first
                state.status = if (state.options.exportEnabled && !state.provider.canWrite()) t("Takvime yazma izni gerekli.")
                    else result.second?.message ?: t("Cihaz takvimleri hazır.")
                state.lastRunSignature = signature
                state.lastRunRequest = requestAtStart
                state.lastRunToken = resumeToken
                state.lastRunClean = state.publicationStatus.isBlank() // a failed web-link refresh must be retried
                state.revision++
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            calendarSyncLog("main: run FAILED: ${error}")
            state.lastRunClean = false
            if (state.operation == operation) state.status = t("Takvim işlemi tamamlanamadı. Bağlantıyı ve izinleri kontrol edip yeniden dene.")
        }
        finally { if (state.operation == operation) state.busy = false }
    }
    return state
}
