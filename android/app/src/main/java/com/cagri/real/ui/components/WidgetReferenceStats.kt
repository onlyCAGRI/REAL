package com.cagri.real.ui.components

import com.cagri.real.data.ReminderItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Read-only projection over REAL's existing cards. No additional widget database. */
internal fun refTasks(all: List<ReminderItem>): List<ReminderItem> =
    all.filter { it.mood == null && it.journal == null }

internal fun refDay(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

internal fun refDueDay(reminder: ReminderItem, zone: ZoneId): LocalDate? =
    reminder.plannedDateEpochDay?.let { LocalDate.ofEpochDay(it) }
        ?: reminder.alertAt?.let { refDay(it, zone) }

internal fun refActiveTasks(all: List<ReminderItem>, zone: ZoneId): List<ReminderItem> =
    refTasks(all).filterNot { it.isCompleted }.sortedWith(
        compareBy<ReminderItem> {
            it.plannedDateEpochDay?.let { day ->
                LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
            } ?: it.alertAt ?: Long.MAX_VALUE
        }.thenByDescending { it.createdAt }.thenBy { it.id }
    )

internal fun refDoneTasks(all: List<ReminderItem>): List<ReminderItem> =
    refTasks(all).filter { it.isCompleted }.sortedWith(
        compareByDescending<ReminderItem> { it.completedAt }.thenBy { it.id }
    )

internal data class RefTodayStats(val done: Int, val total: Int, val pending: Int, val overdue: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** Today's real completions + open tasks due today; overdue is a separate, all-date count. */
internal fun refTodayStats(all: List<ReminderItem>, today: LocalDate, zone: ZoneId, nowMs: Long): RefTodayStats {
    val tasks = refTasks(all)
    val done = tasks.count { reminder -> reminder.completedAt?.let { refDay(it, zone) == today } == true }
    val pending = tasks.count { !it.isCompleted && refDueDay(it, zone) == today }
    val overdue = tasks.count { reminder ->
        if (reminder.isCompleted) false
        else if (reminder.plannedDateEpochDay != null) reminder.plannedDateEpochDay < today.toEpochDay()
        else reminder.alertAt?.let { it < nowMs } == true
    }
    return RefTodayStats(done, done + pending, pending, overdue)
}

/** Completed cards per local day, oldest first, ending today; no invented graph samples. */
internal fun refCompletedPerDay(all: List<ReminderItem>, days: Int, today: LocalDate, zone: ZoneId): List<Int> {
    require(days in 1..366)
    val byDay = refTasks(all).mapNotNull { it.completedAt?.let { ms -> refDay(ms, zone) } }
        .groupingBy { it }.eachCount()
    return (days - 1 downTo 0).map { back -> byDay[today.minusDays(back.toLong())] ?: 0 }
}

internal data class WidgetReferenceClock(val today: LocalDate, val zone: ZoneId, val nowMillis: Long)
internal fun widgetReferenceClock(
    nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()
): WidgetReferenceClock = WidgetReferenceClock(refDay(nowMillis, zone), zone, nowMillis)
