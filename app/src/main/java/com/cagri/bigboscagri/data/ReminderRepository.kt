package com.cagri.bigboscagri.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ReminderRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    // All callers use Dispatchers.IO. Shared lock serializes receivers and activity mutations.
    private var cachedRaw: String? = null
    private var cachedItems: List<ReminderItem> = emptyList()

    fun load(): List<ReminderItem> = synchronized(mutationLock) {
        val raw = preferences.getString(KEY_REMINDERS, null) ?: return@synchronized emptyList()
        if (raw == cachedRaw) return@synchronized cachedItems
        val decoded = run {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        ReminderItem(
                            id = item.getLong("id"),
                            text = item.getString("text"),
                            createdAt = item.getLong("createdAt"),
                            updatedAt = item.optLong("updatedAt", item.getLong("createdAt")),
                            completedAt = item.optionalTimestamp("completedAt"),
                            isImportant = item.optBoolean("isImportant", false),
                            alertAt = item.optionalTimestamp("alertAt"),
                            notifiedAlertAt = item.optionalTimestamp("notifiedAlertAt"),
                            accentStart = item.optionalAccent("accentStart"),
                            accentEnd = item.optionalAccent("accentEnd")
                        )
                    )
                }
            }.sortedByDescending { it.createdAt }
        }
        cachedRaw = raw
        cachedItems = decoded
        decoded
    }

    fun add(text: String, alertAt: Long? = null): List<ReminderItem> = synchronized(mutationLock) {
        require(text.isNotBlank())
        val now = System.currentTimeMillis()
        require(alertAt == null || alertAt > now)
        val previous = load()
        val id = maxOf(now, (previous.maxOfOrNull { it.id } ?: 0L) + 1L)
        val newItem = ReminderItem(id = id, text = text.trim(), createdAt = now, alertAt = alertAt)
        val updated = listOf(newItem) + previous
        save(updated)
        updated
    }

    fun toggleCompleted(id: Long): List<ReminderItem> = update(id) {
        it.toggleCompleted(System.currentTimeMillis())
    }

    fun toggleImportant(id: Long): List<ReminderItem> = update(id) {
        it.copy(isImportant = !it.isImportant, updatedAt = System.currentTimeMillis())
    }

    fun edit(id: Long, text: String, accentStart: Int?, accentEnd: Int?): List<ReminderItem> {
        require(text.isNotBlank())
        require(accentStart == null || accentStart in ACCENT_RANGE)
        require(accentEnd == null || accentEnd in ACCENT_RANGE)
        return update(id) {
            it.copy(
                text = text.trim(),
                accentStart = accentStart,
                accentEnd = accentEnd,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun setAlert(id: Long, alertAt: Long?): List<ReminderItem> {
        require(alertAt == null || alertAt > System.currentTimeMillis())
        return update(id) { it.copy(alertAt = alertAt, notifiedAlertAt = null, updatedAt = System.currentTimeMillis()) }
    }

    fun deliverDue(now: Long, post: (ReminderItem) -> Boolean) = synchronized(mutationLock) {
        val before = load()
        val after = before.map { if (it.isAlertDue(now) && post(it)) it.copy(notifiedAlertAt = it.alertAt) else it }
        if (after != before) save(after)
    }

    fun delete(id: Long): List<ReminderItem> = synchronized(mutationLock) {
        load().filterNot { it.id == id }.also(::save)
    }

    private fun update(id: Long, change: (ReminderItem) -> ReminderItem): List<ReminderItem> =
        synchronized(mutationLock) { load().map { if (it.id == id) change(it) else it }.also(::save) }

    private fun save(items: List<ReminderItem>) {
        val array = JSONArray()
        items.forEach { reminder ->
            array.put(
                JSONObject()
                    .put("id", reminder.id)
                    .put("text", reminder.text)
                    .put("createdAt", reminder.createdAt)
                    .put("updatedAt", reminder.updatedAt)
                    .put("completedAt", reminder.completedAt ?: JSONObject.NULL)
                    .put("isImportant", reminder.isImportant)
                    .put("alertAt", reminder.alertAt ?: JSONObject.NULL)
                    .put("notifiedAlertAt", reminder.notifiedAlertAt ?: JSONObject.NULL)
                    .put("accentStart", reminder.accentStart ?: JSONObject.NULL)
                    .put("accentEnd", reminder.accentEnd ?: JSONObject.NULL)
            )
        }
        val raw = array.toString()
        check(preferences.edit().putString(KEY_REMINDERS, raw).commit()) { "Not kaydedilemedi" }
        cachedRaw = raw
        cachedItems = items
    }

    private fun JSONObject.optionalTimestamp(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key).takeIf { it > 0L }

    private fun JSONObject.optionalAccent(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key).takeIf { it in ACCENT_RANGE }

    companion object {
        val mutationLock = Any()
        private const val PREFERENCES_NAME = "big_bos_cagri_reminders"
        private const val KEY_REMINDERS = "reminders"
        val ACCENT_RANGE = 0..9
    }
}
