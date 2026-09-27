package com.cagri.bigboscagri.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val trLocale = Locale.forLanguageTag("tr-TR")
private val homeReminderDateFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", trLocale)
private val detailReminderDateFormat = DateTimeFormatter.ofPattern("EEEE • d MMM yyyy, HH:mm", trLocale)


fun reminderDate(timestamp: Long): String = homeReminderDateFormat.format(
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
)


fun detailReminderDate(timestamp: Long): String {
    val formatted = detailReminderDateFormat.format(
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    )
    return formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(trLocale) else it.toString() }
}
