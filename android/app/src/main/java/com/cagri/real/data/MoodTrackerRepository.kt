package com.cagri.real.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class MoodTrackerRepository(private val preferences: SharedPreferences) : MoodEntryRepository {
    constructor(context: Context) : this(com.cagri.real.platform.android.sync.SyncLocal.preferences(context, PREFERENCES_NAME, Context.MODE_PRIVATE))

    override fun load(): List<MoodTrackerEntry> = synchronized(mutationLock) {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return@synchronized emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val mood = JournalMood.fromKey(item.optNullableString("mood")) ?: continue
                val descriptors = item.optJSONArray("descriptors")?.toStringList().orEmpty()
                val factors = item.optJSONArray("factors")?.toStringList().orEmpty()
                val loggedAt = item.optLong("loggedAt", item.optLong("id", System.currentTimeMillis()))
                add(
                    MoodTrackerEntry(
                        id = item.optLong("id", loggedAt),
                        mood = mood,
                        details = MoodDetails(
                            scope = MoodScope.fromKey(item.optNullableString("scope")) ?: MoodScope.DAILY,
                            descriptors = descriptors,
                            energy = MoodEnergy.fromKey(item.optNullableString("energy")),
                            factors = factors,
                            loggedAt = loggedAt
                        )
                    )
                )
            }
        }.sortedByDescending { it.details.loggedAt }
    }

    override fun add(mood: JournalMood, details: MoodDetails): List<MoodTrackerEntry> = synchronized(mutationLock) {
        val previous = load()
        val now = System.currentTimeMillis()
        val updated = MoodTrackerRules.add(previous, mood, details, now)
        save(updated)
        updated
    }

    override fun clearAll() = synchronized(mutationLock) {
        check(preferences.edit().remove(KEY_ENTRIES).commit()) {
            "Mood tracker temizlenemedi"
        }
    }

    private fun save(entries: List<MoodTrackerEntry>) {
        val array = JSONArray()
        entries.sortedByDescending { it.details.loggedAt }.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("mood", entry.mood.persistKey)
                    .put("scope", entry.details.scope.persistKey)
                    .put("energy", entry.details.energy?.persistKey ?: JSONObject.NULL)
                    .put("loggedAt", entry.details.loggedAt)
                    .put("descriptors", JSONArray(entry.details.descriptors))
                    .put("factors", JSONArray(entry.details.factors))
            )
        }
        check(preferences.edit().putString(KEY_ENTRIES, array.toString()).commit()) {
            "Mood tracker kaydedilemedi"
        }
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) {
            val value = optString(index, "")
            if (value.isNotBlank()) add(value)
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    companion object {
        private const val PREFERENCES_NAME = "real_mood_tracker"
        private const val KEY_ENTRIES = "entries_v1"
        private val mutationLock = com.cagri.real.platform.android.sync.SyncLocal.lock
    }
}
