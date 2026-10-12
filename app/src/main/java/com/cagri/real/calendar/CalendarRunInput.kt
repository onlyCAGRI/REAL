package com.cagri.real.calendar

import com.cagri.real.data.ReminderItem

internal data class CalendarRunInput(val cards: List<ReminderItem>, val existingCardIds: Set<Long>)
internal fun calendarRunInput(reminders: List<ReminderItem>, options: CalendarOptions): CalendarRunInput =
    CalendarRunInput(reminders.filter { card ->
        options.exportEnabled && !card.isCompleted &&
            (card.alertAt != null || card.plannedDateEpochDay != null) &&
            (options.exportAll || card.id in options.selectedIds)
    }, reminders.mapTo(HashSet<Long>()) { it.id })
