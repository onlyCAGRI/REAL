package com.cagri.bigboscagri.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val reminderDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("tr-TR"))

fun reminderDate(timestamp: Long): String = reminderDateFormat.format(
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
)
