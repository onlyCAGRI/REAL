package com.cagri.real.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Pure helpers behind the voice card actions (skip one occurrence, shift to another day, append checklist
 * items). They live apart from [ReminderRepository] so the same logic drives the confirmation text the
 * person sees and the change the repository later persists.
 */

/** What skipping the next occurrence of a repeating card does. [nextAt] == null means the series is over. */
data class SkipPlan(val skippedAt: Long, val nextAt: Long?)

/**
 * The occurrence that would be skipped is the card's upcoming one ([ReminderItem.alertAt]); the one after
 * it is computed from the series anchor, so the repeat rule (weekdays, end date) is never touched.
 * Null when the card does not repeat, has no alert, or is already completed.
 */
fun ReminderItem.planSkipOccurrence(
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): SkipPlan? {
    val current = alertAt ?: return null
    if (!hasRepeat || isCompleted) return null
    val anchor = anchorAlertAt ?: current
    val next = computeNextRepeat(
        baseAlertAt = anchor,
        rule = repeatRule,
        weekdaysMask = repeatWeekdaysMask,
        untilEpochDay = repeatUntilEpochDay,
        zone = zone,
        // An overdue upcoming occurrence must not be "skipped" onto another past date.
        afterMillis = maxOf(current, nowMillis)
    )
    return SkipPlan(skippedAt = current, nextAt = next)
}

/** The card after skipping one occurrence. When no occurrence is left, the finished series completes the card. */
fun ReminderItem.skippedOccurrence(
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): ReminderItem? {
    val plan = planSkipOccurrence(nowMillis, zone) ?: return null
    val anchor = anchorAlertAt ?: alertAt
    return if (plan.nextAt != null) {
        copy(alertAt = plan.nextAt, anchorAlertAt = anchor, notifiedAlertAt = null, updatedAt = nowMillis)
    } else {
        copy(completedAt = nowMillis, notifiedAlertAt = null, updatedAt = nowMillis)
    }
}

/**
 * Moves one card to another calendar day, keeping its clock time. [mapDate] receives the card's current
 * day and returns the destination day. A repeating card only moves this occurrence (its anchor is kept).
 * Returns null when there is nothing to move: completed, undated, unchanged day, or a result in the past.
 */
fun ReminderItem.rescheduledToDate(
    mapDate: (LocalDate) -> LocalDate,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): ReminderItem? {
    if (isCompleted) return null
    val currentAlert = alertAt
    if (currentAlert != null) {
        val zdt = Instant.ofEpochMilli(currentAlert).atZone(zone)
        val newDate = mapDate(zdt.toLocalDate())
        if (newDate == zdt.toLocalDate()) return null
        val newAlert = newDate.atTime(zdt.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        if (newAlert <= nowMillis) return null
        return copy(
            alertAt = newAlert,
            anchorAlertAt = if (hasRepeat) (anchorAlertAt ?: currentAlert) else newAlert,
            plannedDateEpochDay = null,
            quick24HourUntilAt = null,
            notifiedAlertAt = null,
            updatedAt = nowMillis
        )
    }
    val currentPlanned = plannedDateEpochDay ?: return null
    val newDate = mapDate(LocalDate.ofEpochDay(currentPlanned))
    if (newDate.toEpochDay() == currentPlanned) return null
    if (newDate.isBefore(Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())) return null
    // A rolling 24-hour focus card that is planned for another day leaves that bucket.
    return copy(plannedDateEpochDay = newDate.toEpochDay(), quick24HourUntilAt = null, updatedAt = nowMillis)
}

data class ChecklistAppendResult(
    val item: ReminderItem,
    val added: Int,
    val reopened: Int,
    val duplicates: Int
) {
    val changed: Boolean get() = added + reopened > 0
}

/**
 * Appends spoken items to an existing checklist card. An item that is already on the list and open is a
 * duplicate and is skipped; one that was already ticked off is re-opened instead of added twice.
 * Null when the card is not a checklist or there is nothing to add.
 */
fun ReminderItem.appendChecklistItems(texts: List<String>, nowMillis: Long): ChecklistAppendResult? {
    if (!isChecklist) return null
    val turkish = Locale.forLanguageTag("tr-TR")
    fun key(value: String): String = value.trim().lowercase(turkish)
    val cleaned = texts.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { key(it) }.take(30)
    if (cleaned.isEmpty()) return null

    var items = checklistItems
    var nextId = maxOf(nowMillis, (checklistItems.maxOfOrNull { it.id } ?: 0L) + 1L)
    var added = 0
    var reopened = 0
    var duplicates = 0
    for (text in cleaned) {
        val existing = items.firstOrNull { key(it.text) == key(text) }
        when {
            existing == null -> {
                items = items + ChecklistItem(id = nextId, text = text)
                nextId += 1L
                added += 1
            }
            existing.isCompleted -> {
                items = items.map { if (it.id == existing.id) it.copy(completedAt = null) else it }
                reopened += 1
            }
            else -> duplicates += 1
        }
    }
    val updated = if (added + reopened > 0) copy(checklistItems = items, updatedAt = nowMillis) else this
    return ChecklistAppendResult(item = updated, added = added, reopened = reopened, duplicates = duplicates)
}
