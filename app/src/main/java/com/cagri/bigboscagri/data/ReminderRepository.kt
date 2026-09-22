package com.cagri.bigboscagri.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
                    val isChecklist = item.optBoolean("isChecklist", false)
                    val checklistItems = if (item.has("checklistItems")) {
                        val itemsArray = item.optJSONArray("checklistItems")
                        if (itemsArray != null) {
                            buildList {
                                for (i in 0 until itemsArray.length()) {
                                    val obj = itemsArray.getJSONObject(i)
                                    add(
                                        ChecklistItem(
                                            id = obj.getLong("id"),
                                            text = obj.getString("text"),
                                            completedAt = obj.optionalTimestamp("completedAt")
                                        )
                                    )
                                }
                            }
                        } else emptyList()
                    } else emptyList()
                    val attachments = if (item.has("attachments")) {
                        val attsArray = item.optJSONArray("attachments")
                        if (attsArray != null) {
                            buildList {
                                for (i in 0 until attsArray.length()) {
                                    val obj = attsArray.getJSONObject(i)
                                    val id = obj.optString("id")
                                    val localPath = obj.optString("localPath")
                                    if (id.isNotBlank() && localPath.isNotBlank()) {
                                        add(ReminderAttachment(id = id, localPath = localPath))
                                    }
                                }
                            }
                        } else emptyList()
                    } else emptyList()
                    val journal = if (item.has("journal") && !item.isNull("journal")) {
                        val jObj = item.getJSONObject("journal")
                        val moodKey = if (jObj.has("mood") && !jObj.isNull("mood")) jObj.getString("mood") else null
                        ReminderJournal(
                            mood = JournalMood.fromKey(moodKey),
                            text = jObj.optString("text", ""),
                            createdAt = jObj.getLong("createdAt"),
                            updatedAt = jObj.optLong("updatedAt", jObj.getLong("createdAt"))
                        )
                    } else null
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
                            customCategoryId = item.optionalTimestamp("customCategoryId"),
                            isChecklist = isChecklist,
                            checklistItems = checklistItems,
                            sendImmediateNotification = item.optBoolean("sendImmediateNotification", true),
                            referenceUrl = item.optString("referenceUrl").takeIf { !it.isNullOrBlank() && it != "null" },
                            starBorderColorIndex = item.optionalAccent("starBorderColorIndex")
                                ?: if (item.optBoolean("isImportant", false)) Math.floorMod(item.getLong("id"), 10) else null,
                            gradientBlendFraction = item.optionalBlendFraction(),
                            customSolidColorArgb = item.optionalColorArgb("customSolidColorArgb"),
                            attachments = attachments,
                            journal = journal
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
        customCategoryId: Long? = null,
        isChecklist: Boolean = false,
        checklistItems: List<ChecklistItem> = emptyList(),
        sendImmediateNotification: Boolean = true,
        referenceUrl: String? = null,
        attachments: List<ReminderAttachment> = emptyList()
    ): ReminderItem = synchronized(mutationLock) {
        val trimmedTitle = text.trim()
        require(trimmedTitle.isNotBlank()) { "Başlık boş olamaz" }
        val validItems = if (isChecklist) {
            checklistItems.filter { it.text.isNotBlank() }
        } else emptyList()
        if (isChecklist) {
            require(validItems.isNotEmpty()) { "En az bir madde olmalı" }
        }
        val now = System.currentTimeMillis()
        require(alertAt == null || alertAt > now)
        val previous = load()
        val id = maxOf(now, (previous.maxOfOrNull { it.id } ?: 0L) + 1L)
        val normalizedItems = validItems.mapIndexed { index, item ->
            if (item.id > 0L) item.copy(text = item.text.trim())
            else ChecklistItem(id = now + index + 1L, text = item.text.trim(), completedAt = item.completedAt)
        }
        val newItem = ReminderItem(
            id = id,
            text = trimmedTitle,
            createdAt = now,
            alertAt = alertAt,
            repeatRule = if (alertAt != null) repeatRule else RecurrenceRule.NONE,
            anchorAlertAt = alertAt,
            customCategoryId = customCategoryId,
            isChecklist = isChecklist,
            checklistItems = normalizedItems,
            sendImmediateNotification = sendImmediateNotification,
            referenceUrl = referenceUrl?.trim()?.takeIf { it.isNotBlank() },
            attachments = attachments
        )
        val updated = listOf(newItem) + previous
        save(updated)
        newItem
    }

    fun toggleChecklistItem(reminderId: Long, itemId: Long): List<ReminderItem> = update(reminderId) { reminder ->
        if (!reminder.isChecklist) reminder
        else {
            val now = System.currentTimeMillis()
            val updatedItems = reminder.checklistItems.map { item ->
                if (item.id == itemId) item.toggleCompleted(now) else item
            }
            reminder.copy(checklistItems = updatedItems, updatedAt = now)
        }
    }

    fun toggleCompleted(id: Long): List<ReminderItem> = update(id) {
        it.toggleCompleted(System.currentTimeMillis())
    }

    fun completeFromNotification(id: Long): List<ReminderItem> = update(id) { item ->
        if (item.isCompleted) item
        else if (item.hasRepeat) {
            // Recurring reminder: alertAt already advanced in deliverDue.
            // Acknowledging the delivered occurrence clears notifiedAlertAt.
            // Do not set completedAt or break the recurrence cycle.
            item.copy(notifiedAlertAt = null, updatedAt = System.currentTimeMillis())
        } else {
            item.copy(
                completedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun snooze(id: Long, snoozeUntil: Long): List<ReminderItem> = update(id) { item ->
        if (item.isCompleted) item
        else {
            val now = System.currentTimeMillis()
            item.copy(
                alertAt = snoozeUntil,
                notifiedAlertAt = null,
                anchorAlertAt = item.anchorAlertAt ?: item.alertAt,
                updatedAt = now
            )
        }
    }

    fun toggleImportant(id: Long): List<ReminderItem> = update(id) { reminder ->
        val newImportant = !reminder.isImportant
        val starBorderIndex = if (newImportant) {
            reminder.starBorderColorIndex ?: (0 until 10).random()
        } else {
            reminder.starBorderColorIndex
        }
        reminder.copy(
            isImportant = newImportant,
            starBorderColorIndex = starBorderIndex,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun edit(
        id: Long,
        text: String,
        accentStart: Int? = null,
        accentEnd: Int? = null,
        customCategoryId: Long? = null,
        updateCategory: Boolean = true,
        checklistItems: List<ChecklistItem>? = null,
        starBorderColorIndex: Int? = null,
        gradientBlendFraction: Float? = null,
        customSolidColorArgb: Int? = null,
        attachments: List<ReminderAttachment>? = null
    ): List<ReminderItem> {
        require(text.isNotBlank())
        require(accentStart == null || accentStart in ACCENT_RANGE)
        require(accentEnd == null || accentEnd in ACCENT_RANGE)
        require(starBorderColorIndex == null || starBorderColorIndex in ACCENT_RANGE)
        require(gradientBlendFraction == null || (gradientBlendFraction in 0.0f..1.0f))
        return update(id) { reminder ->
            val updatedChecklist = if (checklistItems != null) {
                checklistItems.filter { it.text.isNotBlank() }.mapIndexed { index, item ->
                    if (item.id > 0L) item.copy(text = item.text.trim())
                    else ChecklistItem(id = System.currentTimeMillis() + index + 1L, text = item.text.trim(), completedAt = item.completedAt)
                }
            } else reminder.checklistItems
            val resolvedBlendFraction = if (accentStart != null && accentEnd != null) {
                gradientBlendFraction ?: reminder.gradientBlendFraction
            } else null
            reminder.copy(
                text = text.trim(),
                accentStart = accentStart,
                accentEnd = accentEnd,
                customCategoryId = if (updateCategory) customCategoryId else reminder.customCategoryId,
                checklistItems = updatedChecklist,
                starBorderColorIndex = starBorderColorIndex ?: reminder.starBorderColorIndex,
                gradientBlendFraction = resolvedBlendFraction,
                customSolidColorArgb = customSolidColorArgb,
                attachments = attachments ?: reminder.attachments,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun edit(
        id: Long,
        text: String,
        accentStart: Int? = null,
        accentEnd: Int? = null,
        customCategoryId: Long? = null,
        updateCategory: Boolean = true,
        checklistItems: List<ChecklistItem>? = null,
        starBorderColorIndex: Int? = null,
        gradientBlendIndex: Int
    ): List<ReminderItem> = edit(
        id = id,
        text = text,
        accentStart = accentStart,
        accentEnd = accentEnd,
        customCategoryId = customCategoryId,
        updateCategory = updateCategory,
        checklistItems = checklistItems,
        starBorderColorIndex = starBorderColorIndex,
        gradientBlendFraction = migrateLegacyGradientBlendIndex(gradientBlendIndex)
    )

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
                        // Advance to next cycle: preserve delivered occurrence in notifiedAlertAt
                        item.copy(
                            alertAt = nextOccurrence,
                            anchorAlertAt = anchor ?: item.alertAt,
                            notifiedAlertAt = item.alertAt,
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

    fun unassignCategories(categoryIds: Set<Long>): List<ReminderItem> = synchronized(mutationLock) {
        if (categoryIds.isEmpty()) return@synchronized load()
        val now = System.currentTimeMillis()
        val items = load().map {
            if (it.customCategoryId in categoryIds) it.copy(customCategoryId = null, updatedAt = now)
            else it
        }
        save(items)
        items
    }

    fun restore(items: List<ReminderItem>): List<ReminderItem> = synchronized(mutationLock) {
        save(items)
        items
    }

    fun setJournal(id: Long, mood: JournalMood?, text: String): List<ReminderItem> = update(id) { reminder ->
        val now = System.currentTimeMillis()
        val journal = ReminderJournal(
            mood = mood,
            text = text,
            createdAt = reminder.journal?.createdAt ?: now,
            updatedAt = now
        )
        reminder.copy(journal = journal, updatedAt = now)
    }

    fun deleteJournal(id: Long): List<ReminderItem> = update(id) { reminder ->
        reminder.copy(journal = null, updatedAt = System.currentTimeMillis())
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
                    .put("starBorderColorIndex", reminder.starBorderColorIndex ?: JSONObject.NULL)
                    .put("alertAt", reminder.alertAt ?: JSONObject.NULL)
                    .put("notifiedAlertAt", reminder.notifiedAlertAt ?: JSONObject.NULL)
                    .put("accentStart", reminder.accentStart ?: JSONObject.NULL)
                    .put("accentEnd", reminder.accentEnd ?: JSONObject.NULL)
                    .put("gradientBlendFraction", reminder.gradientBlendFraction?.toDouble() ?: JSONObject.NULL)
                    .put("customSolidColorArgb", reminder.customSolidColorArgb ?: JSONObject.NULL)
                    .put("repeatRule", reminder.repeatRule.storageKey)
                    .put("anchorAlertAt", reminder.anchorAlertAt ?: JSONObject.NULL)
                    .put("customCategoryId", reminder.customCategoryId ?: JSONObject.NULL)
                    .put("isChecklist", reminder.isChecklist)
                    .put("sendImmediateNotification", reminder.sendImmediateNotification)
                    .put("referenceUrl", reminder.referenceUrl ?: JSONObject.NULL)
                    .put("checklistItems", JSONArray().apply {
                        reminder.checklistItems.forEach { checkItem ->
                            put(
                                JSONObject()
                                    .put("id", checkItem.id)
                                    .put("text", checkItem.text)
                                    .put("completedAt", checkItem.completedAt ?: JSONObject.NULL)
                            )
                        }
                    })
                    .put("attachments", JSONArray().apply {
                        reminder.attachments.forEach { att ->
                            put(
                                JSONObject()
                                    .put("id", att.id)
                                    .put("localPath", att.localPath)
                            )
                        }
                    })
                    .put("journal", reminder.journal?.let { j ->
                        JSONObject()
                            .put("mood", j.mood?.persistKey ?: JSONObject.NULL)
                            .put("text", j.text)
                            .put("createdAt", j.createdAt)
                            .put("updatedAt", j.updatedAt)
                    } ?: JSONObject.NULL)
            )
        }
        val raw = array.toString()
        check(preferences.edit().putString(KEY_REMINDERS, raw).commit()) { "Not kaydedilemedi" }
        cachedRaw = raw
        cachedItems = items
    }

    fun getAttachmentPathsForReminders(ids: Set<Long>): List<String> = synchronized(mutationLock) {
        load().filter { it.id in ids }.flatMap { it.attachments.map { att -> att.localPath } }
    }

    fun getAllAttachmentPaths(): Set<String> = synchronized(mutationLock) {
        load().flatMap { it.attachments.map { att -> att.localPath } }.toSet()
    }

    private fun JSONObject.optionalTimestamp(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key).takeIf { it > 0L }

    private fun JSONObject.optionalAccent(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key).takeIf { it in ACCENT_RANGE }

    private fun JSONObject.optionalColorArgb(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.optionalBlendFraction(): Float? {
        if (has("gradientBlendFraction") && !isNull("gradientBlendFraction")) {
            val f = optDouble("gradientBlendFraction").toFloat()
            return if (f.isFinite()) f.coerceIn(0.0f, 1.0f) else null
        }
        if (has("gradientBlendIndex") && !isNull("gradientBlendIndex")) {
            val idx = optInt("gradientBlendIndex")
            if (idx in GRADIENT_BLEND_FRACTIONS.indices) {
                return GRADIENT_BLEND_FRACTIONS[idx]
            }
        }
        return null
    }

    companion object {
        val mutationLock = Any()
        private const val PREFERENCES_NAME = "big_bos_cagri_reminders"
        private const val KEY_REMINDERS = "reminders"
        val ACCENT_RANGE = 0..9

        private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val updates: SharedFlow<Unit> = _updates.asSharedFlow()

        fun notifyChanged() {
            _updates.tryEmit(Unit)
        }
    }
}
