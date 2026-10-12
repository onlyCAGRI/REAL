package com.cagri.real.contextcards

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** Bumped whenever the card bookkeeping changes from outside the Home card (Settings toggle, test buttons). */
object ContextCardBus {
    val tick = MutableStateFlow(0)
}

/**
 * Dedicated preferences for context-card bookkeeping. Never touches reminder, mood, water or book data.
 * The data is non-critical: if it is unreadable we fall back to a fresh history instead of failing.
 */
class ContextCardStore(private val preferences: SharedPreferences) {
    fun load(): ContextCardHistory = synchronized(lock) { read() }

    fun update(notify: Boolean = true, change: (ContextCardHistory) -> ContextCardHistory): ContextCardHistory {
        val updated = synchronized(lock) {
            val next = change(read())
            preferences.edit().putString(KEY, write(next)).commit()
            next
        }
        if (notify) ContextCardBus.tick.value += 1
        return updated
    }

    /** Settings test buttons. */
    fun force(type: ContextCardType?) { update { it.copy(forcedType = type?.key) } }

    /** Clears limits, snoozes and the forced card; keeps the on/off switch. */
    fun resetHistory() { update { ContextCardHistory(enabled = it.enabled) } }

    fun setEnabled(enabled: Boolean) { update { it.copy(enabled = enabled) } }

    private fun read(): ContextCardHistory {
        val raw = preferences.getString(KEY, null) ?: return ContextCardHistory()
        return runCatching {
            val o = JSONObject(raw)
            ContextCardHistory(
                enabled = o.optBoolean("enabled", true),
                epochDay = o.optLong("epochDay", Long.MIN_VALUE),
                shownToday = o.optJSONObject("shownToday").toIntMap(),
                lastActivationAt = o.optLong("lastActivationAt", 0L),
                activeType = o.optString("activeType").takeIf { it.isNotBlank() },
                activeSince = o.optLong("activeSince", 0L),
                lastType = o.optString("lastType").takeIf { it.isNotBlank() },
                snoozeUntil = o.optJSONObject("snoozeUntil").toLongMap(),
                dismissStreak = o.optJSONObject("dismissStreak").toIntMap(),
                forcedType = o.optString("forcedType").takeIf { it.isNotBlank() }
            )
        }.getOrDefault(ContextCardHistory())
    }

    private fun write(h: ContextCardHistory): String = JSONObject()
        .put("schemaVersion", 1)
        .put("enabled", h.enabled)
        .put("epochDay", h.epochDay)
        .put("shownToday", JSONObject(h.shownToday))
        .put("lastActivationAt", h.lastActivationAt)
        .put("activeType", h.activeType ?: "")
        .put("activeSince", h.activeSince)
        .put("lastType", h.lastType ?: "")
        .put("snoozeUntil", JSONObject(h.snoozeUntil))
        .put("dismissStreak", JSONObject(h.dismissStreak))
        .put("forcedType", h.forcedType ?: "")
        .toString()

    private fun JSONObject?.toIntMap(): Map<String, Int> =
        this?.keys()?.asSequence()?.associateWith { optInt(it) } ?: emptyMap()

    private fun JSONObject?.toLongMap(): Map<String, Long> =
        this?.keys()?.asSequence()?.associateWith { optLong(it) } ?: emptyMap()

    companion object {
        const val PREFERENCES_NAME = "real_context_cards_v1"
        private const val KEY = "history"
        private val lock = com.cagri.real.platform.android.sync.SyncLocal.lock
    }
}
