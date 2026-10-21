package com.cagri.real.data

import java.time.LocalDate
import java.time.ZoneId

fun List<ReminderItem>.inCategory(
    category: ReminderCategory,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis()
): List<ReminderItem> = (if (category == ReminderCategory.JournalEntries) {
    map { item ->
        val entry = item.completionJournalEntry()
        if (item.journal == null && entry != null) item.copy(journal = entry) else item
    }
} else this).filter { item ->
    when (category) {
        ReminderCategory.Completed -> item.isCompleted
        ReminderCategory.NoAlert -> item.isNoAlert
        ReminderCategory.Important -> !item.isCompleted && item.isImportant
        ReminderCategory.Planned -> !item.isCompleted && (item.alertAt != null || item.plannedDateEpochDay != null)
        ReminderCategory.Today -> item.isIn24HourWindow(nowMillis, zone)
        ReminderCategory.JournalEntries -> item.journal != null
    }
}.let { items ->
    if (category == ReminderCategory.Completed) items.sortedByDescending { it.completedAt }
    else if (category == ReminderCategory.JournalEntries) items.sortedByDescending { it.journal?.createdAt ?: it.createdAt }
    else if (category == ReminderCategory.Today) items.sortedWith(
        compareBy<ReminderItem> { item ->
            when {
                item.alertAt != null -> item.alertAt
                item.quick24HourUntilAt != null -> item.quick24HourUntilAt - ROLLING_24_HOURS_MILLIS
                else -> Long.MAX_VALUE
            }
        }.thenByDescending { it.createdAt }
    )
    else items.sortedByDescending { it.createdAt }
}

fun List<ReminderItem>.categoryCounts(
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis()
): Map<ReminderCategory, Int> {
    val counts = ReminderCategory.entries.associateWith { 0 }.toMutableMap()
    fun add(category: ReminderCategory) { counts[category] = counts.getValue(category) + 1 }
    forEach { item ->
        if (item.journal != null) add(ReminderCategory.JournalEntries)
        if (item.isCompleted) add(ReminderCategory.Completed)
        else {
            if (item.isImportant) add(ReminderCategory.Important)
            if (item.isNoAlert) add(ReminderCategory.NoAlert)
            if (item.alertAt != null || item.plannedDateEpochDay != null) add(ReminderCategory.Planned)
            if (item.isIn24HourWindow(nowMillis, zone)) add(ReminderCategory.Today)
        }
    }
    return counts
}


/** Read saved completion cards through the journal index without duplicating stored content. */
fun ReminderItem.completionJournalEntry(): ReminderJournal? {
    journal?.let { return it }
    val stack = cardStack ?: return null
    val card = stack.cards.firstOrNull {
        (it.id.startsWith("completed_journal_") ||
            (isCompleted && it.title.trim().equals("Nasıl geçti?", ignoreCase = true))) &&
            it.body.isNotBlank()
    } ?: return null
    val timestamp = card.id.removePrefix("completed_journal_").toLongOrNull()
        ?.takeIf { it > 0L } ?: stack.createdAt
    return ReminderJournal(mood = null, text = card.body.trim(), createdAt = timestamp)
}
