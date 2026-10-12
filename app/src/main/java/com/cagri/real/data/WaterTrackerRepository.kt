package com.cagri.real.data

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class WaterIntakeEntry(
    val id: String, val amountMl: Int, val createdAt: Long,
    val beverage: BeverageType = BeverageType.WATER
)

data class WaterTrackerSnapshot(
    val goalMl: Int = 2_000,
    val entries: List<WaterIntakeEntry> = emptyList(),
    val dailyGoals: Map<Long, Int> = emptyMap()
) {
    fun entriesOn(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<WaterIntakeEntry> =
        entries.filter { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == day }
            .sortedByDescending { it.createdAt }
}

/** Dedicated preferences: never writes reminder, mood, category or Worker data. */
class WaterTrackerRepository(private val preferences: SharedPreferences) {
    suspend fun load(): WaterTrackerSnapshot = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val original = read()
            val retained = retainSnapshot(original)
            if (retained != original) persist(retained)
            retained
        }
    }

    suspend fun add(amountMl: Int, beverage: BeverageType = BeverageType.WATER): WaterTrackerSnapshot = mutate { snapshot ->
        require(amountMl in 1..5_000)
        snapshot.copy(entries = snapshot.entries + WaterIntakeEntry(
            UUID.randomUUID().toString(), amountMl, System.currentTimeMillis(), beverage
        ))
    }

    suspend fun remove(id: String): WaterTrackerSnapshot = mutate { snapshot ->
        snapshot.copy(entries = snapshot.entries.filterNot { it.id == id })
    }

    suspend fun setGoal(amountMl: Int): WaterTrackerSnapshot = mutate { snapshot ->
        require(amountMl in 100..10_000)
        snapshot.copy(goalMl = amountMl, dailyGoals = snapshot.dailyGoals + (LocalDate.now().toEpochDay() to amountMl))
    }

    private suspend fun mutate(change: (WaterTrackerSnapshot) -> WaterTrackerSnapshot): WaterTrackerSnapshot =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val changed = change(retainSnapshot(read()))
                val updated = retainSnapshot(changed)
                persist(updated)
                updated
            }
        }

    private fun retainSnapshot(snapshot: WaterTrackerSnapshot): WaterTrackerSnapshot {
        val today = LocalDate.now().toEpochDay()
        // Freeze each known day's goal; later edits do not rewrite previous dates.
        val last = snapshot.dailyGoals.keys.filter { it <= today }.maxOrNull()
        val goals = snapshot.dailyGoals.toMutableMap()
        if (last != null) {
            for (day in maxOf(last + 1, today - 29)..today) goals.putIfAbsent(day, snapshot.goalMl)
        }
        goals.putIfAbsent(today, snapshot.goalMl)
        return snapshot.copy(entries = retainRecentWaterEntries(snapshot.entries),
            dailyGoals = goals.filterKeys { it >= today - 29 })
    }

    private fun persist(updated: WaterTrackerSnapshot) {
                val array = JSONArray().apply {
                    updated.entries.forEach { entry -> put(JSONObject()
                        .put("id", entry.id).put("amountMl", entry.amountMl).put("createdAt", entry.createdAt)
                        .put("beverage", entry.beverage.persistKey)) }
                }
                val goals = JSONObject().apply { updated.dailyGoals.forEach { (day, goal) -> put(day.toString(), goal) } }
                val json = JSONObject().put("schemaVersion", 3).put("goalMl", updated.goalMl).put("dailyGoals", goals)
                    .put("entries", array).toString()
                check(preferences.edit().putString(KEY, json).commit()) { "İçecek kaydı kaydedilemedi" }
    }

    private fun read(): WaterTrackerSnapshot {
        val raw = preferences.getString(KEY, null) ?: return WaterTrackerSnapshot()
        val obj = JSONObject(raw)
        // Unknown/corrupt data must not be silently overwritten by a fresh empty snapshot.
        check(obj.optInt("schemaVersion", 1) in 1..3) { "Desteklenmeyen içecek kaydı sürümü" }
        val array = obj.getJSONArray("entries")
        val entries = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val amount = item.getInt("amountMl")
                val timestamp = item.getLong("createdAt")
                val id = item.getString("id")
                check(amount in 1..5_000 && timestamp > 0 && id.isNotBlank())
                val beverage = if (!item.has("beverage")) BeverageType.WATER
                    else BeverageType.fromKey(item.getString("beverage"))
                add(WaterIntakeEntry(id, amount, timestamp, beverage))
            }
        }
        val goals = buildMap<Long, Int> {
            val saved = obj.optJSONObject("dailyGoals")
            saved?.keys()?.forEach { key ->
                val day = key.toLongOrNull()
                val goal = saved?.optInt(key, 0) ?: 0
                if (day != null && goal in 100..10_000) put(day, goal)
            }
        }
        return WaterTrackerSnapshot(obj.optInt("goalMl", 2_000).coerceIn(100, 10_000), entries, goals)
    }

    companion object {
        const val PREFERENCES_NAME = "real_water_tracker_v1"
        private const val KEY = "water_tracker"
        private val lock = com.cagri.real.platform.android.sync.SyncLocal.lock
    }
}

/** Today and the previous 29 local calendar days; all beverage types share the same retention. */
internal fun retainRecentWaterEntries(
    entries: List<WaterIntakeEntry>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()
): List<WaterIntakeEntry> {
    val cutoff = today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
    return entries.filter { it.createdAt >= cutoff }
}
