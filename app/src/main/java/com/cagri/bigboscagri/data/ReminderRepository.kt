package com.cagri.bigboscagri.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ReminderRepository(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

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
                            accentEnd = item.optionalAccent("accentEnd"),
                            repeatRule = RecurrenceRule.fromStorage(if (item.has("repeatRule")) item.optString("repeatRule") else null),
                            anchorAlertAt = item.optionalTimestamp("anchorAlertAt") ?: item.optionalTimestamp("alertAt"),
                            customCategoryId = item.optionalTimestamp("customCategoryId")
                        )
                    )
                }
            }.sortedByDescending { it.createdAt }
        }
        cachedRaw = raw
        cachedItems = decoded
        decoded
    }

    fun add(
        text: String,
        alertAt: Long? = null,
        repeatRule: RecurrenceRule = RecurrenceRule.NONE,
        customCategoryId: Long? = null
    ): List<ReminderItem> = synchronized(mutationLock) {
        require(text.isNotBlank())
        val now = System.currentTimeMillis()
        require(alertAt == null || alertAt > now)
        val previous = load()
        val id = maxOf(now, (previous.maxOfOrNull { it.id } ?: 0L) + 1L)
        val newItem = ReminderItem(
            id = id,
            text = text.trim(),
            createdAt = now,
            alertAt = alertAt,
            repeatRule = if (alertAt != null) repeatRule else RecurrenceRule.NONE,
            anchorAlertAt = alertAt,
            customCategoryId = customCategoryId
        )
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

    fun edit(
        id: Long,
        text: String,
        accentStart: Int?,
        accentEnd: Int?,
        customCategoryId: Long? = null,
        updateCategory: Boolean = true
    ): List<ReminderItem> {
        require(text.isNotBlank())
        require(accentStart == null || accentStart in ACCENT_RANGE)
        require(accentEnd == null || accentEnd in ACCENT_RANGE)
        return update(id) {
            it.copy(
                text = text.trim(),
                accentStart = accentStart,
                accentEnd = accentEnd,
                customCategoryId = if (updateCategory) customCategoryId else it.customCategoryId,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun setCustomCategory(id: Long, customCategoryId: Long?): List<ReminderItem> = update(id) {
        it.copy(customCategoryId = customCategoryId, updatedAt = System.currentTimeMillis())
    }

    fun setAlert(
        id: Long,
        alertAt: Long?,
        repeatRule: RecurrenceRule = RecurrenceRule.NONE
    ): List<ReminderItem> {
        require(alertAt == null || alertAt > System.currentTimeMillis())
        return update(id) {
            it.copy(
                alertAt = alertAt,
                notifiedAlertAt = null,
                repeatRule = if (alertAt != null) repeatRule else RecurrenceRule.NONE,
                anchorAlertAt = alertAt,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun deliverDue(now: Long, post: (ReminderItem) -> Boolean) = synchronized(mutationLock) {
        val before = load()
        val after = before.map { reminder ->
            var item = reminder
            // Deliver main alert if due
            if (item.isAlertDue(now)) {
                if (post(item)) {
                    val anchor = item.anchorAlertAt ?: item.alertAt
                    val nextOccurrence = if (item.hasRepeat && item.alertAt != null) {
                        computeNextOccurrence(anchor ?: item.alertAt!!, item.repeatRule, afterMillis = now)
                    } else null

                    item = if (nextOccurrence != null) {
                        // Advance to next cycle: reset notification tracking for new cycle
                        item.copy(
                            alertAt = nextOccurrence,
                            anchorAlertAt = anchor ?: item.alertAt,
                            notifiedAlertAt = null,
                            updatedAt = now
                        )
                    } else {
                        item.copy(notifiedAlertAt = item.alertAt)
                    }
                }
            }
            item
        }
        if (after != before) save(after)
    }

    fun delete(id: Long): List<ReminderItem> = synchronized(mutationLock) {
        load().filterNot { it.id == id }.also(::save)
    }

    fun deleteAll(ids: Set<Long>): List<ReminderItem> = synchronized(mutationLock) {
        if (ids.isEmpty()) load()
        else load().filterNot { it.id in ids }.also(::save)
    }

    fun unassignCategory(categoryId: Long): List<ReminderItem> = synchronized(mutationLock) {
        val items = load().map {
            if (it.customCategoryId == categoryId) it.copy(customCategoryId = null, updatedAt = System.currentTimeMillis())
            else it
        }
        save(items)
        items
    }

    fun restore(items: List<ReminderItem>): List<ReminderItem> = synchronized(mutationLock) {
        save(items)
        items
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
                    .put("repeatRule", reminder.repeatRule.storageKey)
                    .put("anchorAlertAt", reminder.anchorAlertAt ?: JSONObject.NULL)
                    .put("customCategoryId", reminder.customCategoryId ?: JSONObject.NULL)
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
