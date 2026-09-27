package com.cagri.bigboscagri.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val trLocale = Locale.forLanguageTag("tr-TR")
private val homeReminderDateFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", trLocale)
private val detailReminderDateFormat = DateTimeFormatter.ofPattern("EEEE • d MMM yyyy, HH:mm", trLocale)

/** Compact list date: intentionally omits the year to keep Home cards quiet. */
fun reminderDate(timestamp: Long): String = homeReminderDateFormat.format(
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
)

/** Full detail date: weekday + day/month/year + time. */
fun detailReminderDate(timestamp: Long): String {
    val formatted = detailReminderDateFormat.format(
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    )
    return formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(trLocale) else it.toString() }
}
