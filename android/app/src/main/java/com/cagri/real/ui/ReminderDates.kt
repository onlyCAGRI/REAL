package com.cagri.real.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val trLocale = Locale.forLanguageTag("tr-TR")
private val homeReminderDateFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", trLocale)
private val detailReminderDateFormat = DateTimeFormatter.ofPattern("EEEE • d MMM yyyy, HH:mm", trLocale)
private val plannedDateFormat = DateTimeFormatter.ofPattern("d MMM", trLocale)
private val detailPlannedDateFormat = DateTimeFormatter.ofPattern("EEEE • d MMM yyyy", trLocale)


fun reminderDate(timestamp: Long): String = homeReminderDateFormat.format(
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
)


fun detailReminderDate(timestamp: Long): String {
    val formatted = detailReminderDateFormat.format(
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    )
    return formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(trLocale) else it.toString() }
}


fun reminderPlannedDate(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val today = LocalDate.now()
    return when (date) {
        today -> "Bugün"
        today.plusDays(1) -> "Yarın"
        else -> plannedDateFormat.format(date)
    }
}

fun detailPlannedDate(epochDay: Long): String {
    val formatted = detailPlannedDateFormat.format(LocalDate.ofEpochDay(epochDay))
    return formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(trLocale) else it.toString() }
}
