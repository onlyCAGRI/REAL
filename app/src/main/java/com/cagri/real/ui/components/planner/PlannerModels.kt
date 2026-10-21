package com.cagri.real.ui.components.planner

import androidx.compose.ui.graphics.Color
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.computeNextRepeat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val PlannerLocale = Locale.forLanguageTag("tr-TR")
internal val PlannerTimeFormat = DateTimeFormatter.ofPattern("HH:mm", PlannerLocale)
internal val PlannerPastels = RealCalendarPastels
internal val PlannerInk = Color(0xFF242322)

internal data class PlannerEventDetails(
    val durationMinutes: Int? = null,
    val people: List<String> = emptyList(),
    val colorIndex: Int = 0
)

internal data class PlannerEvent(
    val reminder: ReminderItem,
    val details: PlannerEventDetails?,
    val startMillis: Long,
    val isDateOnly24Hour: Boolean = false,
    val externalEndMillis: Long? = null,
    val externalCalendarName: String? = null,
    /** Bitiş: when this occurrence ends, possibly days later. Null = it lives on its start day only. */
    val spanEndMillis: Long? = null
) {
    val id get() = reminder.id
    val endMillis get() = externalEndMillis ?: if (isDateOnly24Hour) null else details?.durationMinutes?.let { startMillis + it * 60_000L }
}

internal fun plannerEvents(
    reminders: List<ReminderItem>,
    details: Map<Long, PlannerEventDetails>,
    zone: ZoneId,
    rangeStartMillis: Long,
    rangeEndMillis: Long
): List<PlannerEvent> = buildList {
    reminders.forEach { reminder ->
        val metadata = details[reminder.id]
        val plannedDay = reminder.plannedDateEpochDay
        if (plannedDay != null) {
            val start = LocalDate.ofEpochDay(plannedDay).atStartOfDay(zone).toInstant().toEpochMilli()
            val spanEnd = reminder.endAt?.takeIf { it > start }
            if (start < rangeEndMillis && (spanEnd ?: (start + 1)) > rangeStartMillis) {
                add(PlannerEvent(reminder, metadata, start, isDateOnly24Hour = true, spanEndMillis = spanEnd))
            }
            return@forEach
        }

        val currentAlert = reminder.alertAt ?: return@forEach
        if (reminder.repeatRule == RecurrenceRule.NONE) {
            val dur = metadata?.durationMinutes
            val spanEnd = reminder.endAt?.takeIf { it > currentAlert }
            val end = maxOf(if (dur != null) currentAlert + dur * 60_000L else currentAlert, spanEnd ?: 0L)
            if (currentAlert < rangeEndMillis && end >= rangeStartMillis) {
                add(PlannerEvent(reminder, metadata, currentAlert, isDateOnly24Hour = false, spanEndMillis = spanEnd))
            }
            return@forEach
        }
        // A repeating event with an end spans the same length each time (its first start → Bitiş).
        val repeatSpan = reminder.endAt?.let { it - (reminder.anchorAlertAt ?: currentAlert) }?.takeIf { it > 0L }

        val anchor = reminder.anchorAlertAt ?: currentAlert
        val skipped = reminder.repeatSkippedEpochDays
        val until = reminder.repeatUntilEpochDay
        fun next(afterMillis: Long): Long? = computeNextRepeat(
            baseAlertAt = anchor,
            rule = reminder.repeatRule,
            weekdaysMask = reminder.repeatWeekdaysMask,
            untilEpochDay = until,
            zone = zone,
            afterMillis = afterMillis,
            skippedEpochDays = skipped
        )
        // The anchor is the first occurrence; it is only real while it is inside the series and not deleted.
        val anchorDay = Instant.ofEpochMilli(anchor).atZone(zone).toLocalDate().toEpochDay()
        val anchorAlive = anchorDay !in skipped && (until == null || anchorDay <= until)
        val lookBack = repeatSpan ?: 0L
        var occurrence = when {
            anchor >= rangeStartMillis - lookBack -> if (anchorAlive) anchor else next(anchor)
            else -> next(rangeStartMillis - lookBack - 1L)
        }
        var safety = 0
        while (occurrence != null && occurrence < rangeEndMillis && safety < 5000) {
            if (occurrence + lookBack >= rangeStartMillis) {
                add(PlannerEvent(reminder, metadata, occurrence, isDateOnly24Hour = false,
                    spanEndMillis = repeatSpan?.let { occurrence!! + it }))
            }
            occurrence = next(occurrence)
            safety++
        }
    }
}.sortedBy { it.startMillis }

internal fun PlannerEvent.day(zone: ZoneId): LocalDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()

/** The last day this occurrence covers (its Bitiş day; an end at midnight belongs to the day before). */
internal fun PlannerEvent.lastDay(zone: ZoneId): LocalDate {
    val end = spanEndMillis ?: return day(zone)
    return maxOf(day(zone), Instant.ofEpochMilli(end - 1).atZone(zone).toLocalDate())
}

/** Spans more than one day, so the month view joins its dots with a line. */
internal fun PlannerEvent.isMultiDay(zone: ZoneId): Boolean = lastDay(zone) > day(zone)
internal fun plannerMonthLabel(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("MMM", PlannerLocale)).uppercase(PlannerLocale)
internal fun plannerDayLabel(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("EEEE", PlannerLocale))

/** External device events are transient presentation data, never persisted as REAL reminders. */
internal fun externalPlannerEvents(
    occurrences: List<com.cagri.real.calendar.CalendarOccurrence>, zone: ZoneId,
    /** "Kategorize et": the REAL category an event (or its calendar) is filed under, shown in that colour. */
    categoryFor: (com.cagri.real.calendar.CalendarOccurrence) -> Long? = { null }
): List<PlannerEvent> = occurrences.map { occurrence ->
    fun localAllDay(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.of("UTC"))
        .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    val start = if (occurrence.allDay) localAllDay(occurrence.start) else occurrence.start
    val end = if (occurrence.allDay) localAllDay(occurrence.end) else occurrence.end
    PlannerEvent(
        reminder = ReminderItem(id = -occurrence.eventId, text = occurrence.title, createdAt = start,
            alertAt = if (occurrence.allDay) null else start,
            plannedDateEpochDay = if (occurrence.allDay) Instant.ofEpochMilli(start).atZone(zone).toLocalDate().toEpochDay() else null,
            sendImmediateNotification = false, customCategoryId = categoryFor(occurrence)),
        details = PlannerEventDetails(durationMinutes = if (occurrence.allDay) null else
            ((end - start) / 60_000L).toInt().coerceIn(1, 1440)),
        startMillis = start, isDateOnly24Hour = occurrence.allDay,
        externalEndMillis = end, externalCalendarName = occurrence.calendarName, spanEndMillis = end
    )
}
