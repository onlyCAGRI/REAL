package com.cagri.real.data

import android.content.Context
import android.content.SharedPreferences
import com.cagri.real.ui.components.ReminderColorChoices
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject

class ReminderRepository(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(com.cagri.real.platform.android.sync.SyncLocal.preferences(context, PREFERENCES_NAME, Context.MODE_PRIVATE))

    
    private var cachedRaw: String? = null
    private var cachedItems: List<ReminderItem> = emptyList()

    fun load(): List<ReminderItem> = synchronized(mutationLock) {
        val raw = preferences.getString(KEY_REMINDERS, null) ?: return@synchronized emptyList()
        if (raw == cachedRaw) return@synchronized cachedItems
        val decoded = ReminderJson.decode(raw)
        cachedRaw = raw
        cachedItems = decoded
        decoded
    }

    fun add(
        text: String,
        alertAt: Long? = null,
        plannedDateEpochDay: Long? = null,
        quick24HourUntilAt: Long? = null,
        repeatRule: RecurrenceRule = RecurrenceRule.NONE,
        customCategoryId: Long? = null,
        isChecklist: Boolean = false,
        checklistItems: List<ChecklistItem> = emptyList(),
        sendImmediateNotification: Boolean = true,
        referenceUrl: String? = null,
        attachments: List<ReminderAttachment> = emptyList(),
        voiceRecordings: List<ReminderVoiceRecording> = emptyList(),
        musicBlocks: List<MusicBlock> = emptyList(),
        mediaBlocks: List<MediaBlock> = emptyList(),
        cardStack: CardStackBlock? = null,
        location: ReminderLocation? = null
    ): ReminderItem = synchronized(mutationLock) {
        val typedTitle = text.trim()
        val trimmedTitle = typedTitle.ifBlank {
            voiceRecordings.firstOrNull()?.let { voiceRecordingPresentationTitle(it.createdAt) }.orEmpty()
        }
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
            plannedDateEpochDay = if (alertAt == null && quick24HourUntilAt == null) plannedDateEpochDay else null,
            quick24HourUntilAt = quick24HourUntilAt?.takeIf { it > now },
            repeatRule = if (alertAt != null) repeatRule else RecurrenceRule.NONE,
            anchorAlertAt = alertAt,
            customCategoryId = customCategoryId,
            isChecklist = isChecklist,
            checklistItems = normalizedItems,
            sendImmediateNotification = sendImmediateNotification,
            referenceUrl = referenceUrl?.trim()?.takeIf { it.isNotBlank() },
            attachments = attachments,
            voiceRecordings = voiceRecordings,
            musicBlocks = musicBlocks,
            mediaBlocks = mediaBlocks,
            cardStack = cardStack,
            location = location
        )
        val updated = listOf(newItem) + previous
        save(updated)
        newItem
    }

    fun toggleChecklistItem(reminderId: Long, itemId: Long): List<ReminderItem> = update(reminderId) { reminder ->
        if (reminder.checklistItems.isEmpty()) reminder
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
        attachments: List<ReminderAttachment>? = null,
        voiceRecordings: List<ReminderVoiceRecording>? = null,
        location: ReminderLocation? = null,
        updateLocation: Boolean = false,
        referenceUrl: String? = null,
        updateReferenceUrl: Boolean = false,
        musicBlocks: List<MusicBlock>? = null,
        mediaBlocks: List<MediaBlock>? = null,
        cardStack: CardStackBlock? = null,
        updateCardStack: Boolean = false,
        contentBlockOrder: List<String>? = null,
        useDarkText: Boolean? = null,
        contentBlockLayout: Map<String, CardBlockPlacement>? = null
    ): List<ReminderItem> {
        val resolvedVoiceRecordings = voiceRecordings ?: load().firstOrNull { it.id == id }?.voiceRecordings.orEmpty()
        val resolvedText = text.trim().ifBlank {
            resolvedVoiceRecordings.firstOrNull()?.let { voiceRecordingPresentationTitle(it.createdAt) }.orEmpty()
        }
        require(resolvedText.isNotBlank())
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
                text = resolvedText,
                accentStart = accentStart,
                accentEnd = accentEnd,
                customCategoryId = if (updateCategory) customCategoryId else reminder.customCategoryId,
                isChecklist = if (checklistItems != null) updatedChecklist.isNotEmpty() else reminder.isChecklist,
                checklistItems = updatedChecklist,
                starBorderColorIndex = starBorderColorIndex ?: reminder.starBorderColorIndex,
                gradientBlendFraction = resolvedBlendFraction,
                customSolidColorArgb = customSolidColorArgb,
                useDarkText = useDarkText ?: reminder.useDarkText,
                attachments = attachments ?: reminder.attachments,
                voiceRecordings = voiceRecordings ?: reminder.voiceRecordings,
                musicBlocks = musicBlocks ?: reminder.musicBlocks,
                mediaBlocks = mediaBlocks ?: reminder.mediaBlocks,
                cardStack = if (updateCardStack) cardStack else reminder.cardStack,
                referenceUrl = if (updateReferenceUrl) referenceUrl else reminder.referenceUrl,
                location = if (updateLocation) location else reminder.location,
                contentBlockOrder = contentBlockOrder ?: reminder.contentBlockOrder,
                contentBlockLayout = contentBlockLayout?.let(::normalizeCardLayout) ?: reminder.contentBlockLayout,
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

    
    fun enrichLocationCoordinates(id: Long, expectedMapsUrl: String, resolved: ReminderLocation): List<ReminderItem> {
        require(resolved.latitude != null && resolved.longitude != null)
        return update(id) { reminder ->
            val existing = reminder.location
            if (existing?.mapsUrl != expectedMapsUrl || existing.latitude != null || existing.longitude != null) reminder
            else reminder.copy(location = existing.copy(
                latitude = resolved.latitude,
                longitude = resolved.longitude,
                approximateCoordinates = resolved.approximateCoordinates
            ))
        }
    }

    fun setCustomCategory(id: Long, customCategoryId: Long?): List<ReminderItem> = update(id) {
        it.copy(customCategoryId = customCategoryId, updatedAt = System.currentTimeMillis())
    }

    fun setCustomCategoryForReminders(ids: Set<Long>, customCategoryId: Long?): List<ReminderItem> = synchronized(mutationLock) {
        if (ids.isEmpty()) return@synchronized load()
        val now = System.currentTimeMillis()
        val items = load().map {
            if (it.id in ids) it.copy(customCategoryId = customCategoryId, updatedAt = now)
            else it
        }
        save(items)
        items
    }

    fun moveTo24Hour(id: Long, nowMillis: Long = System.currentTimeMillis()): List<ReminderItem> = update(id) { reminder ->
        val todayEpochDay = java.time.Instant.ofEpochMilli(nowMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        reminder.copy(
            alertAt = null,
            plannedDateEpochDay = todayEpochDay,
            quick24HourUntilAt = nowMillis + ROLLING_24_HOURS_MILLIS,
            notifiedAlertAt = null,
            repeatRule = RecurrenceRule.NONE,
            repeatWeekdaysMask = 0,
            repeatUntilEpochDay = null,
            repeatSkippedEpochDays = emptyList(),
            anchorAlertAt = null,
            updatedAt = nowMillis
        )
    }

    fun setAlert(
        id: Long,
        alertAt: Long?,
        repeatRule: RecurrenceRule = RecurrenceRule.NONE
    ): List<ReminderItem> {
        require(alertAt == null || alertAt > System.currentTimeMillis())
        return update(id) {
            val changed = it.copy(
                alertAt = alertAt,
                plannedDateEpochDay = null,
                quick24HourUntilAt = null,
                notifiedAlertAt = null,
                repeatRule = if (alertAt != null) repeatRule else RecurrenceRule.NONE,
                repeatWeekdaysMask = if (alertAt != null && repeatRule == RecurrenceRule.WEEKLY) it.repeatWeekdaysMask else 0,
                repeatUntilEpochDay = if (alertAt != null && repeatRule != RecurrenceRule.NONE) it.repeatUntilEpochDay else null,
                anchorAlertAt = alertAt,
                updatedAt = System.currentTimeMillis()
            )
            // Deleted occurrences survive unless the set of occurrence days actually changed.
            changed.copy(repeatSkippedEpochDays = rebaseSkippedDays(it, changed))
        }
    }

    /**
     * Attaches a weekday set / end date to a freshly created repeating reminder. If the current
     * alert does not fall on one of the chosen weekdays it moves to the first one that does.
     */
    fun setRepeatPattern(id: Long, weekdaysMask: Int, untilEpochDay: Long?): List<ReminderItem> = update(id) { item ->
        val alert = item.alertAt ?: return@update item
        val mask = weekdaysMask and 0x7F
        val zone = java.time.ZoneId.systemDefault()
        val zdt = java.time.Instant.ofEpochMilli(alert).atZone(zone)
        val alignedAlert = if (mask != 0 && !maskContains(mask, zdt.dayOfWeek)) {
            firstPatternOccurrence(zdt.toLocalDate(), zdt.toLocalTime(), mask, System.currentTimeMillis(), zone) ?: alert
        } else alert
        val until = untilEpochDay?.takeIf {
            it >= java.time.Instant.ofEpochMilli(alignedAlert).atZone(zone).toLocalDate().toEpochDay()
        }
        val changed = item.copy(
            alertAt = alignedAlert,
            anchorAlertAt = alignedAlert,
            repeatRule = if (mask != 0) RecurrenceRule.WEEKLY else item.repeatRule,
            repeatWeekdaysMask = mask,
            repeatUntilEpochDay = until,
            notifiedAlertAt = if (alignedAlert != alert) null else item.notifiedAlertAt,
            updatedAt = System.currentTimeMillis()
        )
        changed.copy(repeatSkippedEpochDays = rebaseSkippedDays(item, changed))
    }

    /** Applies a resolved spoken edit to one reminder in place (no new card is created). */
    fun applyEdit(id: Long, patch: ReminderEditPatch): List<ReminderItem> = update(id) { item ->
        var r = item
        patch.text?.takeIf { it.isNotBlank() }?.let { r = r.copy(text = it.trim()) }
        if (patch.setAlert && patch.alertAt != null) {
            r = r.copy(
                alertAt = patch.alertAt,
                anchorAlertAt = patch.alertAt,
                plannedDateEpochDay = null,
                quick24HourUntilAt = null,
                notifiedAlertAt = null
            )
        }
        if (patch.setPlannedDate && patch.plannedEpochDay != null && r.alertAt == null) {
            r = r.copy(plannedDateEpochDay = patch.plannedEpochDay, quick24HourUntilAt = null)
        }
        if (patch.setRepeat && r.alertAt != null) {
            r = r.copy(
                repeatRule = patch.repeatRule,
                repeatWeekdaysMask = if (patch.repeatRule == RecurrenceRule.WEEKLY) patch.weekdaysMask and 0x7F else 0,
                repeatUntilEpochDay = if (patch.repeatRule == RecurrenceRule.NONE) null else patch.untilEpochDay
            )
        }
        if (patch.setLocation) r = r.copy(location = patch.location)
        if (patch.setCategory) r = r.copy(customCategoryId = patch.customCategoryId)
        // Text/category/location edits keep deleted occurrences; only a real schedule change drops them.
        r.copy(updatedAt = System.currentTimeMillis(), repeatSkippedEpochDays = rebaseSkippedDays(item, r))
    }

    fun deliverDue(now: Long, post: (ReminderItem) -> Boolean) = synchronized(mutationLock) {
        val before = load()
        val after = before.map { reminder ->
            var item = reminder
            
            if (item.isAlertDue(now)) {
                if (post(item)) {
                    val anchor = item.anchorAlertAt ?: item.alertAt
                    val nextOccurrence = if (item.hasRepeat && item.alertAt != null) {
                        computeNextRepeat(anchor ?: item.alertAt!!, item.repeatRule, item.repeatWeekdaysMask, item.repeatUntilEpochDay, afterMillis = now, skippedEpochDays = item.repeatSkippedEpochDays)
                    } else null

                    item = if (nextOccurrence != null) {
                        
                        item.copy(
                            alertAt = nextOccurrence,
                            anchorAlertAt = anchor ?: item.alertAt,
                            notifiedAlertAt = item.alertAt,
                            updatedAt = now
                        )
                    } else if (item.hasRepeat && item.repeatUntilEpochDay != null) {
                        // Last occurrence of a finite series ("sadece 1 ay"): it just fired, so the series is over.
                        item.copy(notifiedAlertAt = item.alertAt, completedAt = now, updatedAt = now)
                    } else {
                        item.copy(notifiedAlertAt = item.alertAt)
                    }
                }
            }
            item
        }
        if (after != before) save(after)
    }

    /**
     * Voice "skip one occurrence". Uses the same exception model as the Planner delete: the targeted
     * occurrence day is added to repeatSkippedEpochDays (series intact, idempotent) and alertAt moves to the
     * next surviving occurrence. [occurrenceMillis] defaults to the currently scheduled alarm, i.e. the
     * upcoming occurrence; pass a specific one to skip a later day. If nothing is left, the card is completed.
     */
    fun skipNextOccurrence(
        id: Long,
        now: Long = System.currentTimeMillis(),
        occurrenceMillis: Long? = null,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ): SkipOccurrenceResult = synchronized(mutationLock) {
        val item = load().firstOrNull { it.id == id } ?: return@synchronized SkipOccurrenceResult(SkipStatus.NOT_FOUND)
        val current = item.alertAt
        if (item.isCompleted || !item.hasRepeat || current == null) {
            return@synchronized SkipOccurrenceResult(SkipStatus.NOT_REPEATING, item)
        }
        if (occurrenceMillis != null) {
            // An explicit day must be a real, not-yet-passed occurrence of this series (voice: "çarşamba günkü sporu atla").
            val day = occurrenceEpochDay(occurrenceMillis, zone)
            if (day < occurrenceEpochDay(now, zone) || !isSeriesOccurrenceDay(item, day, zone)) {
                return@synchronized SkipOccurrenceResult(SkipStatus.NO_SUCH_OCCURRENCE, item)
            }
        }
        val result = removeOccurrences(
            id, occurrenceMillis ?: current, RepeatDeleteScope.THIS_OCCURRENCE, now, zone,
            deleteWhenNothingSurvives = false
        )
        when (result.status) {
            RepeatDeleteStatus.OCCURRENCE_REMOVED ->
                SkipOccurrenceResult(SkipStatus.SKIPPED, result.item, result.item?.alertAt)
            RepeatDeleteStatus.SERIES_ENDED -> SkipOccurrenceResult(SkipStatus.SERIES_ENDED, result.item)
            RepeatDeleteStatus.NOT_FOUND -> SkipOccurrenceResult(SkipStatus.NOT_FOUND)
            else -> SkipOccurrenceResult(SkipStatus.NOT_REPEATING, result.item)
        }
    }

    /**
     * Applies precomputed day shifts to many cards in ONE save. Cards with an alert move their alertAt
     * (keeping the clock time); date-only cards move plannedDateEpochDay. A repeating card only moves
     * its current occurrence: anchorAlertAt is kept, so the series pattern does not drift.
     */
    fun shiftMany(changes: List<VoiceBulkShift>, now: Long = System.currentTimeMillis()): List<ReminderItem> =
        synchronized(mutationLock) {
            if (changes.isEmpty()) return@synchronized load()
            val byId = changes.associateBy { it.id }
            load().map { item ->
                val change = byId[item.id] ?: return@map item
                when {
                    change.alertAt != null -> item.copy(
                        alertAt = change.alertAt,
                        anchorAlertAt = if (item.hasRepeat) (item.anchorAlertAt ?: item.alertAt) else change.alertAt,
                        plannedDateEpochDay = null,
                        quick24HourUntilAt = null,
                        notifiedAlertAt = null,
                        updatedAt = now
                    )
                    change.plannedEpochDay != null -> item.copy(
                        plannedDateEpochDay = change.plannedEpochDay,
                        quick24HourUntilAt = null,
                        updatedAt = now
                    )
                    else -> item
                }
            }.also(::save)
        }

    /**
     * Appends spoken items to an EXISTING checklist card. An item that already exists (case-insensitive)
     * is not duplicated: if it was checked off it is re-opened, if it is still open it counts as a duplicate.
     */
    fun addChecklistItems(id: Long, texts: List<String>, now: Long = System.currentTimeMillis()): AddChecklistResult =
        synchronized(mutationLock) {
            val items = load()
            val card = items.firstOrNull { it.id == id } ?: return@synchronized AddChecklistResult(AddChecklistStatus.NOT_FOUND)
            if (!card.isChecklist) return@synchronized AddChecklistResult(AddChecklistStatus.NOT_CHECKLIST, item = card)
            val tr = java.util.Locale.forLanguageTag("tr")
            fun key(text: String) = text.trim().lowercase(tr)
            var list = card.checklistItems
            var added = 0
            var reopened = 0
            val duplicates = mutableListOf<String>()
            var nextId = maxOf(now, list.maxOfOrNull { it.id } ?: 0L) + 1L
            for (raw in texts.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { key(it) }) {
                val existing = list.firstOrNull { key(it.text) == key(raw) }
                when {
                    existing == null -> {
                        list = list + ChecklistItem(id = nextId++, text = raw)
                        added++
                    }
                    existing.isCompleted -> {
                        list = list.map { if (it.id == existing.id) it.copy(completedAt = null) else it }
                        reopened++
                    }
                    else -> duplicates += existing.text
                }
            }
            if (added == 0 && reopened == 0) {
                return@synchronized AddChecklistResult(AddChecklistStatus.ADDED, card, 0, 0, duplicates)
            }
            // Adding to a finished list makes it active again.
            val updated = card.copy(checklistItems = list, completedAt = null, updatedAt = now)
            save(items.map { if (it.id == id) updated else it })
            AddChecklistResult(AddChecklistStatus.ADDED, updated, added, reopened, duplicates)
        }

    /**
     * Deletes part of a repeating series from the Planner. A repeating reminder is ONE stored card, so:
     *  - THIS_OCCURRENCE records the selected local day in repeatSkippedEpochDays (series intact);
     *  - THIS_AND_FUTURE ends the series the day before ([RecurrenceRule] repeatUntilEpochDay), so earlier
     *    occurrences stay visible; selecting the first occurrence removes the whole card.
     * alertAt is then re-pointed at the next surviving occurrence. If none is left the card is completed
     * (same as skipNextOccurrence); if nothing at all survives, the card is deleted.
     */
    fun deleteRepeatOccurrence(
        id: Long,
        occurrenceMillis: Long,
        scope: RepeatDeleteScope,
        now: Long = System.currentTimeMillis(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ): RepeatDeleteResult = synchronized(mutationLock) {
        removeOccurrences(id, occurrenceMillis, scope, now, zone, deleteWhenNothingSurvives = true)
    }

    /** Shared by Planner delete and voice skip: both record the same exception model. Caller holds [mutationLock]. */
    private fun removeOccurrences(
        id: Long,
        occurrenceMillis: Long,
        scope: RepeatDeleteScope,
        now: Long,
        zone: java.time.ZoneId,
        deleteWhenNothingSurvives: Boolean
    ): RepeatDeleteResult {
        val items = load()
        val item = items.firstOrNull { it.id == id } ?: return RepeatDeleteResult(RepeatDeleteStatus.NOT_FOUND)
        val anchor = item.anchorAlertAt ?: item.alertAt
        if (!item.hasRepeat || anchor == null) return RepeatDeleteResult(RepeatDeleteStatus.NOT_REPEATING, item)

        fun deleteWholeCard(): RepeatDeleteResult {
            save(items.filterNot { it.id == id })
            return RepeatDeleteResult(RepeatDeleteStatus.SERIES_DELETED)
        }

        val day = occurrenceEpochDay(occurrenceMillis, zone)
        val anchorDay = occurrenceEpochDay(anchor, zone)
        val base = when (scope) {
            RepeatDeleteScope.THIS_AND_FUTURE -> {
                if (day <= anchorDay) return deleteWholeCard()
                // Never extend an existing end date; the cut is the day before the selected occurrence,
                // which also works for weekday patterns where that calendar day is not an occurrence.
                val until = minOf(item.repeatUntilEpochDay ?: Long.MAX_VALUE, day - 1)
                item.copy(
                    repeatUntilEpochDay = until,
                    repeatSkippedEpochDays = item.repeatSkippedEpochDays.filter { it <= until }.distinct().sorted()
                )
            }
            // Set semantics: skipping an already-skipped day changes nothing.
            RepeatDeleteScope.THIS_OCCURRENCE ->
                item.copy(repeatSkippedEpochDays = (item.repeatSkippedEpochDays + day).distinct().sorted())
        }

        val skipped = base.repeatSkippedEpochDays
        val until = base.repeatUntilEpochDay
        val anchorSurvives = anchorDay !in skipped && (until == null || anchorDay <= until)
        val anySurvivor = anchorSurvives || computeNextRepeat(
            baseAlertAt = anchor, rule = base.repeatRule, weekdaysMask = base.repeatWeekdaysMask,
            untilEpochDay = until, zone = zone, afterMillis = anchor, skippedEpochDays = skipped
        ) != null
        if (!anySurvivor && deleteWhenNothingSurvives) return deleteWholeCard()

        var updated = base.copy(updatedAt = now)
        var ended = false
        val alert = base.alertAt
        if (!base.isCompleted && alert != null) {
            val alertDay = occurrenceEpochDay(alert, zone)
            val alertRemoved = alertDay in skipped || (until != null && alertDay > until)
            if (alertRemoved) {
                val next = computeNextRepeat(
                    baseAlertAt = anchor, rule = base.repeatRule, weekdaysMask = base.repeatWeekdaysMask,
                    untilEpochDay = until, zone = zone, afterMillis = maxOf(now, alert - 1L), skippedEpochDays = skipped
                )
                updated = if (next != null) updated.copy(alertAt = next, notifiedAlertAt = null)
                else { ended = true; updated.copy(completedAt = now, notifiedAlertAt = null) }
            }
        }
        save(items.map { if (it.id == id) updated else it })
        return RepeatDeleteResult(if (ended) RepeatDeleteStatus.SERIES_ENDED else RepeatDeleteStatus.OCCURRENCE_REMOVED, updated)
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

    
    fun appendContent(id: Long, attachments: List<ReminderAttachment>, referenceUrl: String?): ReminderItem =
        synchronized(mutationLock) {
            val current = load().firstOrNull { it.id == id } ?: error("Hatırlatıcı artık mevcut değil")
            val merged = (current.attachments + attachments).distinctBy { it.id }
            require(merged.count { it.type == AttachmentType.IMAGE } <= 3) { "Bir hatırlatıcıya en fazla 3 fotoğraf eklenebilir" }
            val updated = current.copy(attachments = merged,
                referenceUrl = referenceUrl ?: current.referenceUrl, updatedAt = System.currentTimeMillis())
            save(load().map { if (it.id == id) updated else it })
            updated
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

    /** Stores Mood V2 on the current reminder as a detail content block. */
    fun setMood(id: Long, mood: JournalMood?, details: MoodDetails? = null): List<ReminderItem> = update(id) { reminder ->
        val now = System.currentTimeMillis()
        reminder.copy(
            mood = mood,
            moodDetails = if (mood == null) null else details ?: reminder.moodDetails,
            updatedAt = now
        )
    }

    /** Clears only Mood V2 data from every reminder. Journal entries are intentionally preserved. */
    fun clearAllMoods(): List<ReminderItem> = synchronized(mutationLock) {
        load().map { reminder ->
            if (reminder.mood == null && reminder.moodDetails == null) reminder
            else reminder.copy(mood = null, moodDetails = null)
        }.also(::save)
    }

    private fun update(id: Long, change: (ReminderItem) -> ReminderItem): List<ReminderItem> =
        synchronized(mutationLock) { load().map { if (it.id == id) change(it) else it }.also(::save) }

    private fun save(items: List<ReminderItem>) {
        // Another repository instance or restore may have changed storage since this cache was filled.
        if (cachedRaw != null && items == cachedItems && preferences.getString(KEY_REMINDERS, null) == cachedRaw) return
        val raw = ReminderJson.encode(items)
        check(preferences.edit().putString(KEY_REMINDERS, raw).commit()) { "Not kaydedilemedi" }
        cachedRaw = raw
        cachedItems = items.toList()
    }

    fun getAttachmentPathsForReminders(ids: Set<Long>): List<String> = synchronized(mutationLock) {
        load().filter { it.id in ids }.flatMap {
            it.attachments.map { att -> att.localPath } +
                (it.cardStack?.cards?.mapNotNull { card -> card.imageUri } ?: emptyList())
        }
    }

    fun getAllAttachmentPaths(): Set<String> = synchronized(mutationLock) {
        load().flatMap {
            it.attachments.map { att -> att.localPath } +
                (it.cardStack?.cards?.mapNotNull { card -> card.imageUri } ?: emptyList())
        }.toSet()
    }

    fun getVoiceRecordingPathsForReminders(ids: Set<Long>): List<String> = synchronized(mutationLock) {
        load().filter { it.id in ids }.flatMap { it.voiceRecordings.map { recording -> recording.localPath } }
    }

    fun getAllVoiceRecordingPaths(): Set<String> = synchronized(mutationLock) {
        load().flatMap { it.voiceRecordings.map { recording -> recording.localPath } }.toSet()
    }

    companion object {
        val mutationLock = com.cagri.real.platform.android.sync.SyncLocal.lock
        private const val PREFERENCES_NAME = "big_bos_cagri_reminders"
        private const val KEY_REMINDERS = "reminders"
        val ACCENT_RANGE: IntRange get() = ReminderColorChoices.indices

        private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val updates: SharedFlow<Unit> = _updates.asSharedFlow()

        fun notifyChanged() {
            _updates.tryEmit(Unit)
        }
    }
}

/** Which part of a repeating series a Planner delete applies to. */
enum class RepeatDeleteScope { THIS_OCCURRENCE, THIS_AND_FUTURE }

enum class RepeatDeleteStatus { OCCURRENCE_REMOVED, SERIES_ENDED, SERIES_DELETED, NOT_REPEATING, NOT_FOUND }

data class RepeatDeleteResult(val status: RepeatDeleteStatus, val item: ReminderItem? = null)

enum class SkipStatus { SKIPPED, SERIES_ENDED, NOT_REPEATING, NOT_FOUND, NO_SUCH_OCCURRENCE }

data class SkipOccurrenceResult(
    val status: SkipStatus,
    val item: ReminderItem? = null,
    val nextAlertAt: Long? = null
)

enum class AddChecklistStatus { ADDED, NOT_CHECKLIST, NOT_FOUND }

data class AddChecklistResult(
    val status: AddChecklistStatus,
    val item: ReminderItem? = null,
    val added: Int = 0,
    val reopened: Int = 0,
    val duplicates: List<String> = emptyList()
)
