package com.cagri.real.ui.components.planner

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** UI preference only; no second Planner repository or event model. */
internal enum class PlannerDailyView(val preference: String) {
    Classic("classic"), New("new"), NewPlus("new_plus");

    val label: String get() = if (this == NewPlus) "New+" else name
    val usesNewLayout: Boolean get() = this != Classic

    companion object {
        fun fromPreference(value: String?): PlannerDailyView =
            entries.firstOrNull { it.preference == value } ?: New
    }
}

/** How a custom category's card looks in the Focus carousel. Stored per category in planner prefs (UI only). */
enum class PlannerCategoryCardShape(val key: String) {
    Square("square"), Rectangle("rectangle");

    companion object {
        fun fromKey(value: String?): PlannerCategoryCardShape = entries.firstOrNull { it.key == value } ?: Square
    }
}

private val FocusDateFormat = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)
internal fun plannerFocusHeading(day: LocalDate, today: LocalDate, count: Int): String {
    val date = if (day == today) "Today’s" else day.format(FocusDateFormat)
    return "$date\nFocus" + if (count > 0) " ($count)" else ""
}

internal fun plannerFocusWeek(day: LocalDate): List<LocalDate> {
    val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return List(7) { monday.plusDays(it.toLong()) }
}

internal fun plannerFocusEvents(events: List<PlannerEvent>, day: LocalDate, zone: ZoneId): List<PlannerEvent> =
    events.filter { event ->
        event.reminder.isImportant &&
            (event.reminder.plannedDateEpochDay != null || event.reminder.alertAt != null) &&
            event.day(zone) == day
    }.distinctBy { it.id }.sortedBy { it.startMillis }

/** Same 20-minute fallback as Classic, strictly clipped at the selected day's boundaries. */
internal fun plannerDailyGeometry(events: List<PlannerEvent>, day: LocalDate, zone: ZoneId): PlannerTimelineGeometry {
    val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val intervals = events.filterNot { it.isDateOnly24Hour }.map { event ->
        // Completed events are drawn as small pills: they claim a short, low slot instead of the
        // full card span, so their hour lines and the empty space below them collapse.
        val completed = event.reminder.isCompleted
        val eventEnd = if (completed) event.startMillis + 50 * 60_000L
        else event.endMillis ?: (event.startMillis + 20 * 60_000L)
        PlannerTimeInterval(
            event.occurrenceKey(),
            ((maxOf(event.startMillis, start) - start) / 60_000.0).toFloat(),
            ((minOf(eventEnd, end) - start) / 60_000.0).toFloat(),
            small = completed
        )
    }
    return PlannerTimelineGeometry(
        intervals,
        dayMinutes = ((end - start) / 60_000L).toInt(),
        // The existing piecewise clock and lane allocator are used vertically; hours without a task collapse.
        compactHourDp = 2f,
        expandedHourDp = 96f,
        smallHourDp = 46f
    )
}

internal fun plannerDailyTime(event: PlannerEvent, zone: ZoneId): String {
    if (event.isDateOnly24Hour) return "All day"
    val start = Instant.ofEpochMilli(event.startMillis).atZone(zone).format(PlannerTimeFormat)
    return event.endMillis?.let {
        "$start – ${Instant.ofEpochMilli(it).atZone(zone).format(PlannerTimeFormat)}"
    } ?: start
}
