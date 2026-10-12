package com.cagri.real.ui.components.planner

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Presentation metadata only. ReminderRepository remains authoritative for dates/titles/deletion. */
internal class PlannerRepository(private val preferences: SharedPreferences) {
    suspend fun load(): Map<Long, PlannerEventDetails> = withContext(Dispatchers.IO) { synchronized(lock) { read() } }

    suspend fun put(id: Long, details: PlannerEventDetails): Map<Long, PlannerEventDetails> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val updated = read() + (id to details)
            val entries = JSONArray().apply {
                updated.forEach { (key, value) -> put(JSONObject().put("reminderId", key)
                    .put("durationMinutes", value.durationMinutes ?: JSONObject.NULL).put("colorIndex", value.colorIndex)
                    .put("people", JSONArray(value.people))) }
            }
            val raw = JSONObject().put("version", 1).put("events", entries).toString()
            check(preferences.edit().putString("event_details", raw).commit()) { "Plan ayrıntıları kaydedilemedi" }
            updated
        }
    }

    private fun read(): Map<Long, PlannerEventDetails> {
        val raw = preferences.getString("event_details", null) ?: return emptyMap()
        val root = JSONObject(raw)
        check(root.getInt("version") == 1)
        val entries = root.getJSONArray("events")
        return buildMap {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val people = item.optJSONArray("people")
                val dur = if (item.has("durationMinutes") && !item.isNull("durationMinutes")) {
                    item.getInt("durationMinutes").coerceIn(1, 1440)
                } else null
                put(item.getLong("reminderId"), PlannerEventDetails(
                    durationMinutes = dur,
                    people = (0 until (people?.length() ?: 0)).mapNotNull { people?.optString(it)?.takeIf(String::isNotBlank) },
                    colorIndex = item.optInt("colorIndex", 0).coerceIn(0, PlannerPastels.lastIndex)
                ))
            }
        }
    }

    companion object { private val lock = com.cagri.real.platform.android.sync.SyncLocal.lock }
}
