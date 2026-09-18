package com.cagri.bigboscagri.data

import java.time.LocalDate
import java.time.ZoneId

fun ReminderItem.isAlertDue(now: Long): Boolean =
    !isCompleted && alertAt != null && alertAt <= now && notifiedAlertAt != alertAt

fun List<ReminderItem>.nextAlertAfter(now: Long): Long? =
    asSequence().filter { !it.isCompleted && it.alertAt != it.notifiedAlertAt }
        .mapNotNull { it.alertAt }.filter { it > now }.minOrNull()

fun clampedDate(year: Int, month: Int, day: Int): LocalDate {
    val first = LocalDate.of(year, month, 1)
    return first.withDayOfMonth(day.coerceIn(1, first.lengthOfMonth()))
}

fun reminderTimestamp(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long? {
    val local = date.atTime(hour, minute)
    val zoned = local.atZone(zone)
    return if (zoned.toLocalDateTime() == local) zoned.toInstant().toEpochMilli() else null
}
