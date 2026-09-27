package com.cagri.bigboscagri.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class RecurrenceRule(val storageKey: String, val displayName: String) {
    NONE("none", "Tekrarlama"),
    DAILY("daily", "Her gün"),
    WEEKLY("weekly", "Her hafta"),
    MONTHLY("monthly", "Her ay"),
    YEARLY("yearly", "Her yıl");

    companion object {
        fun fromStorage(key: String?): RecurrenceRule =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: NONE
    }
}

fun RecurrenceRule.descriptionFor(alertTimestamp: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (alertTimestamp == null) return "Yok"
    val date = Instant.ofEpochMilli(alertTimestamp).atZone(zone).toLocalDate()
    val tr = Locale.forLanguageTag("tr-TR")
    return when (this) {
        RecurrenceRule.NONE -> "Yok"
        RecurrenceRule.DAILY -> "Her gün"
        RecurrenceRule.WEEKLY -> {
            val dayName = when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> "Pazartesi"
                DayOfWeek.TUESDAY -> "Salı"
                DayOfWeek.WEDNESDAY -> "Çarşamba"
                DayOfWeek.THURSDAY -> "Perşembe"
                DayOfWeek.FRIDAY -> "Cuma"
                DayOfWeek.SATURDAY -> "Cumartesi"
                DayOfWeek.SUNDAY -> "Pazar"
                else -> date.format(DateTimeFormatter.ofPattern("EEEE", tr))
            }
            "Her $dayName"
        }
        RecurrenceRule.MONTHLY -> "Her ayın ${date.dayOfMonth}'i"
        RecurrenceRule.YEARLY -> {
            val monthName = when (date.monthValue) {
                1 -> "Ocak"
                2 -> "Şubat"
                3 -> "Mart"
                4 -> "Nisan"
                5 -> "Mayıs"
                6 -> "Haziran"
                7 -> "Temmuz"
                8 -> "Ağustos"
                9 -> "Eylül"
                10 -> "Ekim"
                11 -> "Kasım"
                12 -> "Aralık"
                else -> date.format(DateTimeFormatter.ofPattern("MMMM", tr))
            }
            "Her yıl ${date.dayOfMonth} $monthName"
        }
    }
}

fun RecurrenceRule.repeatSummary(alertTimestamp: Long?, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (this == RecurrenceRule.NONE || alertTimestamp == null) return null
    val date = Instant.ofEpochMilli(alertTimestamp).atZone(zone).toLocalDate()
    return when (this) {
        RecurrenceRule.NONE -> null
        RecurrenceRule.DAILY -> "Her gün tekrarlanacak"
        RecurrenceRule.WEEKLY -> {
            val dayName = when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> "Pazartesi"
                DayOfWeek.TUESDAY -> "Salı"
                DayOfWeek.WEDNESDAY -> "Çarşamba"
                DayOfWeek.THURSDAY -> "Perşembe"
                DayOfWeek.FRIDAY -> "Cuma"
                DayOfWeek.SATURDAY -> "Cumartesi"
                DayOfWeek.SUNDAY -> "Pazar"
                else -> date.dayOfWeek.name
            }
            "Her $dayName tekrarlanacak"
        }
        RecurrenceRule.MONTHLY -> {
            val suffix = when (date.dayOfMonth) {
                1, 5, 8, 11, 15, 18, 21, 25, 28, 31 -> "'i"
                2, 7, 12, 17, 20, 22, 27 -> "'si"
                3, 4, 13, 14, 23, 24 -> "'ü"
                9, 10, 19, 29, 30 -> "'u"
                6, 16, 26 -> "'sı"
                else -> "'i"
            }
            "Her ayın ${date.dayOfMonth}$suffix tekrarlanacak"
        }
        RecurrenceRule.YEARLY -> {
            val monthName = when (date.monthValue) {
                1 -> "Ocak"
                2 -> "Şubat"
                3 -> "Mart"
                4 -> "Nisan"
                5 -> "Mayıs"
                6 -> "Haziran"
                7 -> "Temmuz"
                8 -> "Ağustos"
                9 -> "Eylül"
                10 -> "Ekim"
                11 -> "Kasım"
                12 -> "Aralık"
                else -> ""
            }
            "Her yıl ${date.dayOfMonth} $monthName tekrarlanacak"
        }
    }
}

fun computeNextOccurrence(
    baseAlertAt: Long,
    rule: RecurrenceRule,
    zone: ZoneId = ZoneId.systemDefault(),
    afterMillis: Long = baseAlertAt
): Long? {
    if (rule == RecurrenceRule.NONE) return null
    val baseZdt = Instant.ofEpochMilli(baseAlertAt).atZone(zone)
    val time = baseZdt.toLocalTime()
    val baseDate = baseZdt.toLocalDate()
    val afterZdt = Instant.ofEpochMilli(afterMillis).atZone(zone)
    val afterDate = afterZdt.toLocalDate()

    val minStep = when (rule) {
        RecurrenceRule.DAILY -> java.time.temporal.ChronoUnit.DAYS.between(baseDate, afterDate)
        RecurrenceRule.WEEKLY -> java.time.temporal.ChronoUnit.WEEKS.between(baseDate, afterDate)
        RecurrenceRule.MONTHLY -> java.time.temporal.ChronoUnit.MONTHS.between(baseDate, afterDate)
        RecurrenceRule.YEARLY -> java.time.temporal.ChronoUnit.YEARS.between(baseDate, afterDate)
        RecurrenceRule.NONE -> return null
    }

    var step = maxOf(1L, minStep)
    var attempts = 0
    while (attempts < 1000) {
        attempts++
        val nextDate = when (rule) {
            RecurrenceRule.DAILY -> baseDate.plusDays(step)
            RecurrenceRule.WEEKLY -> baseDate.plusWeeks(step)
            RecurrenceRule.MONTHLY -> baseDate.plusMonths(step)
            RecurrenceRule.YEARLY -> baseDate.plusYears(step)
            RecurrenceRule.NONE -> return null
        }
        val nextZdt = nextDate.atTime(time).atZone(zone)
        val epochMilli = nextZdt.toInstant().toEpochMilli()
        if (epochMilli > afterMillis) {
            return epochMilli
        }
        step++
    }
    return null
}
