package com.cagri.real.data

import java.time.LocalDate
import java.time.ZoneId

fun ReminderItem.isAlertDue(now: Long): Boolean =
    !isCompleted && alertAt != null && alertAt <= now && notifiedAlertAt != alertAt

fun List<ReminderItem>.nextAlertAfter(now: Long): Long? =
    asSequence().filter { !it.isCompleted }
        .flatMap { item ->
            if (item.alertAt != null && item.alertAt != item.notifiedAlertAt && item.alertAt > now) {
                sequenceOf(item.alertAt, item.earlyAlertAt()?.takeIf { it > now })
            } else emptySequence()
        }.filterNotNull().minOrNull()

/** Longest early warning: 30 days ("30 gün önce"). */
const val MAX_EARLY_ALERT_MINUTES = 30 * 24 * 60

/** When the early warning for the current [ReminderItem.alertAt] fires, or null without one. */
fun ReminderItem.earlyAlertAt(): Long? {
    val at = alertAt ?: return null
    val minutes = earlyAlertMinutes?.takeIf { it in 1..MAX_EARLY_ALERT_MINUTES } ?: return null
    return at - minutes * 60_000L
}

fun clampedDate(year: Int, month: Int, day: Int): LocalDate {
    val first = LocalDate.of(year, month, 1)
    return first.withDayOfMonth(day.coerceIn(1, first.lengthOfMonth()))
}

fun reminderTimestamp(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long? {
    val local = date.atTime(hour, minute)
    val zoned = local.atZone(zone)
    return if (zoned.toLocalDateTime() == local) zoned.toInstant().toEpochMilli() else null
}

