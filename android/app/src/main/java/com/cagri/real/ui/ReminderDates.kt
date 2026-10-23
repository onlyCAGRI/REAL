package com.cagri.real.ui

import com.cagri.real.i18n.I18n
import com.cagri.real.i18n.t
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun fmt(tr: String, en: String) = DateTimeFormatter.ofPattern(if (I18n.isEnglish) en else tr, I18n.locale)

private fun String.titlecased() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(I18n.locale) else it.toString() }

fun reminderDate(timestamp: Long): String =
    fmt("d MMM, HH:mm", "MMM d, h:mm a").format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

fun detailReminderDate(timestamp: Long): String =
    fmt("EEEE • d MMM yyyy, HH:mm", "EEEE • MMM d yyyy, h:mm a")
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())).titlecased()

fun reminderPlannedDate(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val today = LocalDate.now()
    return when (date) {
        today -> t("Bugün")
        today.plusDays(1) -> t("Yarın")
        else -> fmt("d MMM", "MMM d").format(date)
    }
}

fun detailPlannedDate(epochDay: Long): String =
    fmt("EEEE • d MMM yyyy", "EEEE • MMM d yyyy").format(LocalDate.ofEpochDay(epochDay)).titlecased()
