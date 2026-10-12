package com.cagri.real.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

// ---------------------------------------------------------------------------
// Multi-weekday repeat ("her hafta pazartesi, carsamba, cuma") + optional end date.
// The weekday set is a 7-bit mask: bit0 = Monday ... bit6 = Sunday (DayOfWeek.value - 1).
// ---------------------------------------------------------------------------

fun weekdaysToMask(days: Collection<DayOfWeek>): Int =
    days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

fun maskToWeekdays(mask: Int): List<DayOfWeek> =
    DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }

fun maskContains(mask: Int, day: DayOfWeek): Boolean = mask and (1 shl (day.value - 1)) != 0

/** First moment strictly after [afterMillis] whose date is one of the masked weekdays, searching from [fromDate]. */
fun firstPatternOccurrence(
    fromDate: LocalDate,
    time: LocalTime,
    mask: Int,
    afterMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Long? {
    if (mask and 0x7F == 0) return null
    for (offset in 0L..400L) {
        val date = fromDate.plusDays(offset)
        if (!maskContains(mask, date.dayOfWeek)) continue
        val millis = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        if (millis > afterMillis) return millis
    }
    return null
}

/**
 * Next occurrence for a repeat that may carry a weekday set and/or an inclusive end date
 * ([untilEpochDay]). Returns null when the series is over, so callers stop rescheduling.
 * Without a weekday mask this behaves exactly like [computeNextOccurrence].
 */
fun computeNextRepeat(
    baseAlertAt: Long,
    rule: RecurrenceRule,
    weekdaysMask: Int,
    untilEpochDay: Long?,
    zone: ZoneId = ZoneId.systemDefault(),
    afterMillis: Long = baseAlertAt,
    skippedEpochDays: Collection<Long> = emptyList()
): Long? {
    if (rule == RecurrenceRule.NONE) return null
    var cursor = afterMillis
    // Deleted single occurrences are hopped over; the bound only guards against a corrupt skip list.
    repeat(MAX_SKIP_HOPS) {
        val candidate: Long? = if (rule == RecurrenceRule.WEEKLY && (weekdaysMask and 0x7F) != 0) {
            val baseZdt = Instant.ofEpochMilli(baseAlertAt).atZone(zone)
            val afterDate = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val start = if (afterDate.isAfter(baseZdt.toLocalDate())) afterDate else baseZdt.toLocalDate()
            firstPatternOccurrence(start, baseZdt.toLocalTime(), weekdaysMask, cursor, zone)
        } else {
            computeNextOccurrence(baseAlertAt, rule, zone, cursor)
        }
        val next = candidate ?: return null
        val nextDay = Instant.ofEpochMilli(next).atZone(zone).toLocalDate().toEpochDay()
        if (untilEpochDay != null && nextDay > untilEpochDay) return null
        if (nextDay !in skippedEpochDays) return next
        cursor = next
    }
    return null
}

private const val MAX_SKIP_HOPS = 800

/** Which calendar days a rule lands on, independent of clock time; null when the item does not repeat. */
private fun ReminderItem.occurrencePatternKey(zone: ZoneId): List<Long>? {
    val anchor = anchorAlertAt ?: alertAt ?: return null
    val date = Instant.ofEpochMilli(anchor).atZone(zone).toLocalDate()
    return when (repeatRule) {
        RecurrenceRule.NONE -> null
        RecurrenceRule.DAILY -> listOf(0L)
        RecurrenceRule.WEEKLY ->
            if (repeatWeekdaysMask and 0x7F != 0) listOf((repeatWeekdaysMask and 0x7F).toLong())
            else listOf(-1L, date.dayOfWeek.value.toLong())
        RecurrenceRule.MONTHLY -> listOf(date.dayOfMonth.toLong())
        RecurrenceRule.YEARLY -> listOf(date.monthValue.toLong(), date.dayOfMonth.toLong())
    }
}

/**
 * Skipped (deleted) occurrence days to keep after [old] became [new]. They are explicit user deletions, so
 * they are preserved across every edit except one that changes WHICH days the series lands on (repeat rule,
 * weekday set, or the anchor's weekday/day-of-month/month-day). Changing the clock time, the end date, text,
 * category, colour, checklist, attachments or notification flags never resurrects a deleted occurrence.
 * If the alert is explicitly moved onto a skipped day, that day is un-skipped so alarm and Planner agree.
 */
fun rebaseSkippedDays(old: ReminderItem, new: ReminderItem, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
    val newKey = new.occurrencePatternKey(zone) ?: return emptyList()
    if (newKey != old.occurrencePatternKey(zone)) return emptyList()
    val kept = old.repeatSkippedEpochDays.distinct().sorted()
    val alert = new.alertAt
    if (alert == null || alert == old.alertAt) return kept
    val alertDay = Instant.ofEpochMilli(alert).atZone(zone).toLocalDate().toEpochDay()
    return kept.filter { it != alertDay }
}

/** Local calendar day (epoch day) an occurrence timestamp falls on. */
fun occurrenceEpochDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

private fun turkishDayShort(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Pzt"
    DayOfWeek.TUESDAY -> "Sal"
    DayOfWeek.WEDNESDAY -> "Çar"
    DayOfWeek.THURSDAY -> "Per"
    DayOfWeek.FRIDAY -> "Cum"
    DayOfWeek.SATURDAY -> "Cmt"
    DayOfWeek.SUNDAY -> "Paz"
}

private val TURKISH_MONTHS = listOf(
    "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
)

/** "Her Pzt, Çar, Cum · 29 Ekim'e kadar" — null when the item has no weekday set / end date to show. */
fun ReminderItem.patternLabel(): String? {
    if (repeatRule == RecurrenceRule.NONE) return null
    val hasDays = repeatWeekdaysMask and 0x7F != 0
    if (!hasDays && repeatUntilEpochDay == null) return null
    val days = if (hasDays) "Her " + maskToWeekdays(repeatWeekdaysMask).joinToString(", ") { turkishDayShort(it) }
    else repeatRule.descriptionFor(anchorAlertAt ?: alertAt)
    val until = repeatUntilEpochDay?.let {
        val d = LocalDate.ofEpochDay(it)
        " · ${d.dayOfMonth} ${TURKISH_MONTHS[d.monthValue - 1]}'e kadar"
    }.orEmpty()
    return days + until
}

/**
 * True when [epochDay] is a real day of the series pattern (the anchor day, or a later pattern day inside the end
 * date), whether or not that day was deleted. Voice skip uses it to reject days the card never lands on.
 */
fun isSeriesOccurrenceDay(item: ReminderItem, epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    if (!item.hasRepeat) return false
    val anchor = item.anchorAlertAt ?: item.alertAt ?: return false
    val until = item.repeatUntilEpochDay
    if (until != null && epochDay > until) return false
    val anchorDay = occurrenceEpochDay(anchor, zone)
    if (epochDay < anchorDay) return false
    if (epochDay == anchorDay) return true
    val dayStart = java.time.LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
    val next = computeNextRepeat(
        baseAlertAt = anchor, rule = item.repeatRule, weekdaysMask = item.repeatWeekdaysMask,
        untilEpochDay = until, zone = zone, afterMillis = dayStart - 1L
    ) ?: return false
    return occurrenceEpochDay(next, zone) == epochDay
}

/**
 * The occurrence a delete from a context without a selected day (Home / Category card detail) targets: the next
 * surviving occurrence at or after [now]. Never the raw anchor once it has passed or been deleted; respects the
 * pattern, weekday set, end date and deleted days. Null when the card does not repeat or nothing is left ahead.
 */
fun nextDeletableOccurrence(item: ReminderItem, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    if (!item.hasRepeat) return null
    val anchor = item.anchorAlertAt ?: item.alertAt ?: return null
    val skipped = item.repeatSkippedEpochDays
    val until = item.repeatUntilEpochDay
    val anchorDay = occurrenceEpochDay(anchor, zone)
    val anchorAlive = anchorDay !in skipped && (until == null || anchorDay <= until)
    if (anchor >= now && anchorAlive) return anchor
    return computeNextRepeat(
        baseAlertAt = anchor, rule = item.repeatRule, weekdaysMask = item.repeatWeekdaysMask,
        untilEpochDay = until, zone = zone, afterMillis = maxOf(now - 1L, anchor), skippedEpochDays = skipped
    )
}
