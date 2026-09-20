package com.cagri.bigboscagri.speech

import com.cagri.bigboscagri.data.RecurrenceRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class ParsedVoiceReminder(
    val originalText: String,
    val text: String,
    val alertAt: Long?,
    val repeatRule: RecurrenceRule = RecurrenceRule.NONE,
    val requiresTimeSelection: Boolean = false,
    val dateHint: LocalDate? = null,
    val timeOfDay: TimeOfDay? = null
)

enum class TimeOfDay { Morning, Noon, Evening, Night }

/**
 * Bare spoken hour heuristic (deterministic & decoupled):
 * 1–7 -> PM equivalent (13–19)
 * 8–11 -> AM equivalent (08–11)
 * 12 -> 12:00
 * Overridden if explicit morning/noon/evening/night modifier is given.
 */
fun interpretHour(hour: Int, timeOfDay: TimeOfDay? = null): Int {
    if (timeOfDay != null) {
        return when (timeOfDay) {
            TimeOfDay.Morning -> if (hour == 12) 0 else hour
            TimeOfDay.Noon -> if (hour in 1..6) hour + 12 else hour
            TimeOfDay.Evening -> if (hour in 1..11) hour + 12 else hour
            TimeOfDay.Night -> when {
                hour in 8..11 -> hour + 12
                hour == 12 -> 0
                else -> hour
            }
        }
    }
    return when (hour) {
        in 1..7 -> hour + 12
        in 8..11 -> hour
        12 -> 12
        else -> hour
    }
}

private val WORD_NUMBERS = mapOf(
    "bir" to 1, "iki" to 2, "üç" to 3, "uc" to 3, "dört" to 4, "dort" to 4,
    "beş" to 5, "bes" to 5, "altı" to 6, "alti" to 6, "yedi" to 7, "sekiz" to 8,
    "dokuz" to 9, "on" to 10, "on bir" to 11, "on iki" to 12
)

private val MONTHS = mapOf(
    "ocak" to 1, "şubat" to 2, "subat" to 2, "mart" to 3, "nisan" to 4,
    "mayıs" to 5, "mayis" to 5, "haziran" to 6, "temmuz" to 7, "ağustos" to 8, "agustos" to 8,
    "eylül" to 9, "eylul" to 9, "ekim" to 10, "kasım" to 11, "kasim" to 11, "aralık" to 12, "aralik" to 12
)

private val WEEKDAYS = mapOf(
    "pazartesi" to DayOfWeek.MONDAY,
    "salı" to DayOfWeek.TUESDAY,
    "sali" to DayOfWeek.TUESDAY,
    "çarşamba" to DayOfWeek.WEDNESDAY,
    "carsamba" to DayOfWeek.WEDNESDAY,
    "perşembe" to DayOfWeek.THURSDAY,
    "persembe" to DayOfWeek.THURSDAY,
    "cuma" to DayOfWeek.FRIDAY,
    "cumartesi" to DayOfWeek.SATURDAY,
    "pazar" to DayOfWeek.SUNDAY
)

private val VERB_NORMALIZATIONS = mapOf(
    "arıcam" to "ara",
    "arayacağım" to "ara",
    "arayacam" to "ara",
    "alıcam" to "al",
    "alacağım" to "al",
    "alacam" to "al",
    "gidicem" to "git",
    "gideceğim" to "git",
    "gidecem" to "git",
    "yapıcam" to "yap",
    "yapacağım" to "yap",
    "yapacam" to "yap",
    "atıcam" to "at",
    "atacağım" to "at",
    "atacam" to "at",
    "bakıcam" to "bak",
    "bakacağım" to "bak",
    "bakacam" to "bak",
    "çıkıcam" to "çık",
    "çıkacağım" to "çık",
    "çıkacam" to "çık",
    "kapatıcam" to "kapat",
    "kapatacağım" to "kapat",
    "kapatacam" to "kapat",
    "göndericem" to "gönder",
    "göndereceğim" to "gönder",
    "gönderecem" to "gönder",
    "yiycem" to "ye",
    "yiyeceğim" to "ye",
    "yiyecem" to "ye",
    "içicem" to "iç",
    "içeceğim" to "iç",
    "içecem" to "iç",
    "ödiycem" to "öde",
    "ödeyeceğim" to "öde",
    "ödeyecem" to "öde",
    "temizliycem" to "temizle",
    "temizleyeceğim" to "temizle",
    "konuşucam" to "konuş",
    "konuşacağım" to "konuş"
)

fun normalizeVerb(word: String): String {
    val clean = word.trim().lowercase(Locale.forLanguageTag("tr-TR"))
    return VERB_NORMALIZATIONS[clean] ?: word
}

fun stripBoundaryFillerYa(text: String): String {
    var result = text.trim()
    if (result.isEmpty()) return ""

    // Strip leading standalone "ya", optionally preceded/followed by punctuation or spaces.
    // Must NOT be followed by a letter (so "Yasin", "yarın", "yazıcı" are NOT stripped).
    // Must NOT be followed by "da" / "de" (so "ya da" is NOT stripped).
    val leadingYaRegex = Regex("""(?i)^\s*[,\s;:!?.…—–-]*ya(?!\p{L})(?!\s+(?:da|de)(?!\p{L}))[,\s;:!?.…—–-]*""")
    result = leadingYaRegex.replace(result, "")

    // Strip trailing standalone "ya", optionally preceded/followed by punctuation or spaces.
    // Must NOT be preceded by a letter (so "sinemaya", "masaya", "bankaya" are NOT stripped).
    val trailingYaRegex = Regex("""(?i)(?<!\p{L})ya[,\s;:!?.…—–-]*$""")
    result = trailingYaRegex.replace(result, "")

    return result.trim()
}

object VoiceReminderParser {

    fun stripBoundaryFillerYa(text: String): String = com.cagri.bigboscagri.speech.stripBoundaryFillerYa(text)

    fun parse(
        input: String,
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): ParsedVoiceReminder {
        val normalizedInput = stripBoundaryFillerYa(input)
        val trimmedInput = normalizedInput.trim()
        if (trimmedInput.isBlank()) {
            return ParsedVoiceReminder(input, "", null)
        }

        val trLocale = Locale.forLanguageTag("tr-TR")
        val lower = trimmedInput.lowercase(trLocale)

        // 1. Try Relative Time
        runCatching { parseRelativeTime(trimmedInput, lower, now) }.getOrNull()?.let { return it }

        // 2. Try Absolute / Calendar Time
        runCatching { parseCalendarTime(trimmedInput, lower, now) }.getOrNull()?.let { return it }

        // 3. Fallback: No date/time found, keep text as is (clean capitalization)
        val cleaned = cleanReminderText(trimmedInput, emptyList())
        return ParsedVoiceReminder(trimmedInput, cleaned.ifEmpty { trimmedInput }, null)
    }

    private fun parseRelativeTime(
        original: String,
        lower: String,
        now: ZonedDateTime
    ): ParsedVoiceReminder? {
        // yarım saat sonra / buçuk saat sonra
        val halfHourRegex = Regex("""\b(?:bir\s+)?(?:yarım|yarim|buçuk|bucuk)\s+saat\s+sonra\b""")
        halfHourRegex.find(lower)?.let { match ->
            val target = now.plusMinutes(30).truncatedTo(ChronoUnit.MINUTES)
            val cleaned = cleanReminderText(original, listOf(match.range))
            return ParsedVoiceReminder(original, cleaned, target.toInstant().toEpochMilli())
        }

        // bir buçuk saat sonra
        val oneAndHalfRegex = Regex("""\bbir\s+(?:buçuk|bucuk)\s+saat\s+sonra\b""")
        oneAndHalfRegex.find(lower)?.let { match ->
            val target = now.plusMinutes(90).truncatedTo(ChronoUnit.MINUTES)
            val cleaned = cleanReminderText(original, listOf(match.range))
            return ParsedVoiceReminder(original, cleaned, target.toInstant().toEpochMilli())
        }

        // X saat Y dakika sonra
        val hoursAndMinutesRegex = Regex("""\b(\d+)\s+saat\s+(\d+)\s+(?:dakika|dk)\s+sonra\b""")
        hoursAndMinutesRegex.find(lower)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: 0L
            val minutes = match.groupValues[2].toLongOrNull() ?: 0L
            val target = now.plusHours(hours).plusMinutes(minutes).truncatedTo(ChronoUnit.MINUTES)
            val cleaned = cleanReminderText(original, listOf(match.range))
            return ParsedVoiceReminder(original, cleaned, target.toInstant().toEpochMilli())
        }

        // X saat sonra (numeric or "bir saat sonra")
        val hoursRegex = Regex("""\b(\d+|bir)\s+saat\s+sonra\b""")
        hoursRegex.find(lower)?.let { match ->
            val hours = if (match.groupValues[1] == "bir") 1L else match.groupValues[1].toLongOrNull() ?: 1L
            val target = now.plusHours(hours).truncatedTo(ChronoUnit.MINUTES)
            val cleaned = cleanReminderText(original, listOf(match.range))
            return ParsedVoiceReminder(original, cleaned, target.toInstant().toEpochMilli())
        }

        // X dakika sonra
        val minutesRegex = Regex("""\b(\d+|bir)\s+(?:dakika|dk)\s+sonra\b""")
        minutesRegex.find(lower)?.let { match ->
            val minutes = if (match.groupValues[1] == "bir") 1L else match.groupValues[1].toLongOrNull() ?: 1L
            val target = now.plusMinutes(minutes).truncatedTo(ChronoUnit.MINUTES)
            val cleaned = cleanReminderText(original, listOf(match.range))
            return ParsedVoiceReminder(original, cleaned, target.toInstant().toEpochMilli())
        }

        return null
    }

    private fun parseCalendarTime(
        original: String,
        lower: String,
        now: ZonedDateTime
    ): ParsedVoiceReminder? {
        val rangesToRemove = mutableListOf<IntRange>()
        var masked = lower

        // 0. Recurrence extraction
        var repeatRule = RecurrenceRule.NONE
        var timeOfDay: TimeOfDay? = null

        // Her sabah / her öğlen / her akşam / her gece -> DAILY + daypart
        val dailyDaypartRegex = Regex("""\bher\s+(sabahleyin|sabah|öğleden sonra|ogleden sonra|öğleyin|ogleyin|öğlen|oglen|öğle|ogle|akşamleyin|aksamleyin|akşam|aksam|geceleyin|gece)\b""")
        val dailyDaypartMatch = dailyDaypartRegex.find(masked)
        if (dailyDaypartMatch != null) {
            repeatRule = RecurrenceRule.DAILY
            val word = dailyDaypartMatch.groupValues[1]
            timeOfDay = when {
                word.startsWith("sabah") -> TimeOfDay.Morning
                word.startsWith("öğl") || word.startsWith("ogl") -> TimeOfDay.Noon
                word.startsWith("akş") || word.startsWith("aks") -> TimeOfDay.Evening
                word.startsWith("gece") -> TimeOfDay.Night
                else -> null
            }
            rangesToRemove.add(dailyDaypartMatch.range)
            masked = maskRange(masked, dailyDaypartMatch.range)
        }

        // Her gün / günlük recurrence
        if (repeatRule == RecurrenceRule.NONE) {
            val recurrenceSignal = """(?:(?:düzenli\s+)?(?:olarak\s+)?(?:tekrar\s+eden|tekrarlayan|tekrarlanan|tekrar\s+etsin|tekrarla|tekrar))"""
            val dailyRegex = Regex("""\b(?:(?:her\s+gün|hergun)(?:\s+$recurrenceSignal)?|(?:günlük|gunluk)\s+$recurrenceSignal)\b""")
            dailyRegex.find(masked)?.let { m ->
                repeatRule = RecurrenceRule.DAILY
                rangesToRemove.add(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        // Her hafta
        if (repeatRule == RecurrenceRule.NONE) {
            val weeklyRegex = Regex("""\bher\s+hafta\b""")
            weeklyRegex.find(masked)?.let { m ->
                repeatRule = RecurrenceRule.WEEKLY
                rangesToRemove.add(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        // Her pazartesi ... her pazar
        var targetWeekday: DayOfWeek? = null
        if (repeatRule == RecurrenceRule.NONE) {
            val recurringWeekdayRegex = Regex("""\bher\s+(pazartesi|salı|sali|çarşamba|carsamba|perşembe|persembe|cuma|cumartesi|pazar)(?:\s+(?:günü|gunu))?\b""")
            recurringWeekdayRegex.find(masked)?.let { m ->
                repeatRule = RecurrenceRule.WEEKLY
                val word = m.groupValues[1]
                targetWeekday = WEEKDAYS[word]
                rangesToRemove.add(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        // Her ayın X'i / her ay
        var targetDayOfMonth: Int? = null
        if (repeatRule == RecurrenceRule.NONE) {
            val monthlyDayRegex = Regex("""\bher\s+ayın\s+(\d{1,2})(?:['’\`]?\s*(?:i|si|ü|u|ı|sı|e|a|de|da|te|ta|inde|ında|unde|ünde))?\b""")
            val mMatch = monthlyDayRegex.find(masked)
            if (mMatch != null) {
                repeatRule = RecurrenceRule.MONTHLY
                targetDayOfMonth = mMatch.groupValues[1].toIntOrNull()
                rangesToRemove.add(mMatch.range)
                masked = maskRange(masked, mMatch.range)
            } else {
                val bareMonthlyRegex = Regex("""\bher\s+ay\b""")
                bareMonthlyRegex.find(masked)?.let { m ->
                    repeatRule = RecurrenceRule.MONTHLY
                    rangesToRemove.add(m.range)
                    masked = maskRange(masked, m.range)
                }
            }
        }

        // Her yıl
        if (repeatRule == RecurrenceRule.NONE) {
            val yearlyRegex = Regex("""\bher\s+(?:yıl|yil)\b""")
            yearlyRegex.find(masked)?.let { m ->
                repeatRule = RecurrenceRule.YEARLY
                rangesToRemove.add(m.range)
                masked = maskRange(masked, m.range)
            }
        }

        // 1. Date extraction
        var targetDate: LocalDate? = null

        // Specific Date: e.g. "23 eylül" / "23 eylül günü" / "23 eylül tarihinde"
        val specificDateRegex = Regex("""\b(\d{1,2})\s+(ocak|şubat|subat|mart|nisan|mayıs|mayis|haziran|temmuz|ağustos|agustos|eylül|eylul|ekim|kasım|kasim|aralık|aralik)(?:\s+(?:günü|gunu|tarihinde))?\b""")
        val specificDateMatch = specificDateRegex.find(masked)
        if (specificDateMatch != null) {
            val day = specificDateMatch.groupValues[1].toIntOrNull() ?: 1
            val monthStr = specificDateMatch.groupValues[2]
            val month = MONTHS[monthStr] ?: 1
            var year = now.year
            val testDate = runCatching { LocalDate.of(year, month, day) }.getOrNull()
            if (testDate != null) {
                targetDate = testDate
                rangesToRemove.add(specificDateMatch.range)
                masked = maskRange(masked, specificDateMatch.range)
            }
        }

        // Relative Days: bugün, yarın
        if (targetDate == null) {
            val relativeDayRegex = Regex("""\b(bugün|bugun|yarın|yarin)\b""")
            val relMatch = relativeDayRegex.find(masked)
            if (relMatch != null) {
                val word = relMatch.groupValues[1]
                targetDate = if (word.startsWith("yar")) now.toLocalDate().plusDays(1) else now.toLocalDate()
                rangesToRemove.add(relMatch.range)
                masked = maskRange(masked, relMatch.range)
            }
        }

        // Weekday: pazartesi, salı, etc.
        if (targetDate == null && targetWeekday == null) {
            val weekdayRegex = Regex("""\b(pazartesi|salı|sali|çarşamba|carsamba|perşembe|persembe|cuma|cumartesi|pazar)(?:\s+(?:günü|gunu))?\b""")
            val weekdayMatch = weekdayRegex.find(masked)
            if (weekdayMatch != null) {
                val word = weekdayMatch.groupValues[1]
                targetWeekday = WEEKDAYS[word]
                rangesToRemove.add(weekdayMatch.range)
                masked = maskRange(masked, weekdayMatch.range)
            }
        }

        // 2. Daypart extraction (sabah, öğlen, akşam, gece)
        if (timeOfDay == null) {
            val daypartRegex = Regex("""\b(öğleden sonra|ogleden sonra|öğleyin|ogleyin|öğlen|oglen|öğle|ogle|sabahleyin|sabah|akşamleyin|aksamleyin|akşam|aksam|geceleyin|gece)\b""")
            val daypartMatch = daypartRegex.find(masked)
            if (daypartMatch != null) {
                val word = daypartMatch.groupValues[1]
                timeOfDay = when {
                    word.startsWith("sabah") -> TimeOfDay.Morning
                    word.startsWith("öğl") || word.startsWith("ogl") -> TimeOfDay.Noon
                    word.startsWith("akş") || word.startsWith("aks") -> TimeOfDay.Evening
                    word.startsWith("gece") -> TimeOfDay.Night
                    else -> null
                }
                rangesToRemove.add(daypartMatch.range)
                masked = maskRange(masked, daypartMatch.range)
            }
        }

        // 3. Time extraction
        var rawHour: Int? = null
        var rawMinute: Int? = null
        var isExplicit24h = false

        // Time Pattern A: 17:30 or 17.30 (optional 'da/de/ta/te or preceded by saat)
        // e.g. "17:30'da", "17.30da", "saat 14:00'te"
        val colonTimeRegex = Regex("""\b(?:saat\s+)?(\d{1,2})[:.](\d{2})(?:\s*['’\`]?\s*(?:da|de|ta|te))?\b""")
        val colonMatch = colonTimeRegex.find(masked)
        if (colonMatch != null) {
            val h = colonMatch.groupValues[1].toIntOrNull()
            val m = colonMatch.groupValues[2].toIntOrNull()
            if (h != null && h in 0..23 && m != null && m in 0..59) {
                rawHour = h
                rawMinute = m
                isExplicit24h = (rawHour >= 13)
                rangesToRemove.add(colonMatch.range)
                masked = maskRange(masked, colonMatch.range)
            }
        }

        // Time Pattern B: 17 30'da (space separated with suffix)
        if (rawHour == null) {
            val spaceTimeRegex = Regex("""\b(?:saat\s+)?(\d{1,2})\s+(\d{2})(?:\s*['’\`]?\s*(?:da|de|ta|te))\b""")
            val spaceMatch = spaceTimeRegex.find(masked)
            if (spaceMatch != null) {
                val h = spaceMatch.groupValues[1].toIntOrNull()
                val m = spaceMatch.groupValues[2].toIntOrNull()
                if (h != null && h in 0..23 && m != null && m in 0..59) {
                    rawHour = h
                    rawMinute = m
                    isExplicit24h = (rawHour >= 13)
                    rangesToRemove.add(spaceMatch.range)
                    masked = maskRange(masked, spaceMatch.range)
                }
            }
        }

        // Time Pattern C: 5 buçukta / saat 5 buçuk
        if (rawHour == null) {
            val bucukRegex = Regex("""\b(?:saat\s+)?(\d{1,2})\s+(?:buçuk|bucuk)(?:['’\`]?\s*(?:ta|te|da|de))?\b""")
            val bucukMatch = bucukRegex.find(masked)
            if (bucukMatch != null) {
                val h = bucukMatch.groupValues[1].toIntOrNull()
                if (h != null && h in 0..23) {
                    rawHour = h
                    rawMinute = 30
                    rangesToRemove.add(bucukMatch.range)
                    masked = maskRange(masked, bucukMatch.range)
                }
            }
        }

        // Time Pattern D: saat 5 / saat 5'te / saat 17 / saat 8de
        if (rawHour == null) {
            val saatHourRegex = Regex("""\bsaat\s+(\d{1,2})(?:['’\`]?\s*(?:da|de|ta|te)|\s+(?:da|de|ta|te))?\b""")
            val saatMatch = saatHourRegex.find(masked)
            if (saatMatch != null) {
                val h = saatMatch.groupValues[1].toIntOrNull()
                if (h != null && h in 0..23) {
                    rawHour = h
                    rawMinute = 0
                    isExplicit24h = (rawHour >= 13)
                    rangesToRemove.add(saatMatch.range)
                    masked = maskRange(masked, saatMatch.range)
                }
            }
        }

        // Time Pattern E: 5'te / 5te / 5 te / 8'de / 8de / 8 de / 11'de
        if (rawHour == null) {
            val suffixedHourRegex = Regex("""\b(\d{1,2})(?:['’\`]\s*(?:da|de|ta|te)|(?:ta|te|da|de)|\s+(?:da|de|ta|te))\b""")
            val suffixedMatch = suffixedHourRegex.find(masked)
            if (suffixedMatch != null) {
                val h = suffixedMatch.groupValues[1].toIntOrNull()
                if (h != null && h in 0..23) {
                    rawHour = h
                    rawMinute = 0
                    isExplicit24h = (rawHour >= 13)
                    rangesToRemove.add(suffixedMatch.range)
                    masked = maskRange(masked, suffixedMatch.range)
                }
            }
        }

        // Time Pattern F: Word numbers with saat or suffix (e.g. "saat beşte", "saat beş")
        if (rawHour == null) {
            val wordHourRegex = Regex("""\b(?:saat\s+)?(bir|iki|üç|uc|dört|dort|beş|bes|altı|alti|yedi|sekiz|dokuz|on|on bir|on iki)(?:['’\`]?\s*(?:da|de|ta|te))?\b""")
            val wordMatch = wordHourRegex.find(masked)
            if (wordMatch != null && (wordMatch.value.contains("saat") || Regex("""(?:da|de|ta|te)""").containsMatchIn(wordMatch.value))) {
                val word = wordMatch.groupValues[1]
                rawHour = WORD_NUMBERS[word]
                rawMinute = 0
                rangesToRemove.add(wordMatch.range)
                masked = maskRange(masked, wordMatch.range)
            }
        }

        // Time Pattern G: Daypart-backed bare word or bare digit (e.g. "akşam 7", "akşam yedi", "sabah sekiz")
        if (rawHour == null && timeOfDay != null) {
            val bareWordRegex = Regex("""\b(bir|iki|üç|uc|dört|dort|beş|bes|altı|alti|yedi|sekiz|dokuz|on|on bir|on iki)\b""")
            val bareWordMatch = bareWordRegex.find(masked)
            if (bareWordMatch != null) {
                val word = bareWordMatch.groupValues[1]
                rawHour = WORD_NUMBERS[word]
                rawMinute = 0
                rangesToRemove.add(bareWordMatch.range)
                masked = maskRange(masked, bareWordMatch.range)
            } else {
                val bareDigitRegex = Regex("""\b([1-9]|1[0-2])\b""")
                val bareDigitMatch = bareDigitRegex.find(masked)
                if (bareDigitMatch != null) {
                    rawHour = bareDigitMatch.groupValues[1].toIntOrNull()
                    rawMinute = 0
                    rangesToRemove.add(bareDigitMatch.range)
                    masked = maskRange(masked, bareDigitMatch.range)
                }
            }
        }

        // If neither time, date, weekday, day of month, nor recurrence was detected, cannot schedule
        if (rawHour == null && targetDate == null && targetWeekday == null && targetDayOfMonth == null && repeatRule == RecurrenceRule.NONE) {
            return null
        }

        // Calculate final hour and minute
        val finalHour = if (rawHour != null) {
            if (isExplicit24h) rawHour else interpretHour(rawHour, timeOfDay)
        } else {
            0
        }
        val finalMinute = rawMinute ?: 0

        // Determine final target date
        val resolvedDate: LocalDate? = when {
            targetDate != null -> {
                var d = targetDate
                if (rawHour != null) {
                    val candidate = d.atTime(finalHour, finalMinute).atZone(now.zone)
                    if (!candidate.isAfter(now)) {
                        // Candidate is in the past! Advance by 1 day or 1 year
                        d = if (specificDateMatch != null) {
                            d.plusYears(1)
                        } else {
                            d.plusDays(1)
                        }
                    }
                } else {
                    if (d.isBefore(now.toLocalDate())) {
                        d = if (specificDateMatch != null) d.plusYears(1) else d.plusDays(1)
                    }
                }
                d
            }
            targetDayOfMonth != null -> {
                var d = runCatching { LocalDate.of(now.year, now.monthValue, targetDayOfMonth) }.getOrNull()
                    ?: now.toLocalDate()
                if (rawHour != null) {
                    val candidate = d.atTime(finalHour, finalMinute).atZone(now.zone)
                    if (!candidate.isAfter(now)) {
                        d = d.plusMonths(1)
                    }
                } else {
                    if (d.isBefore(now.toLocalDate())) {
                        d = d.plusMonths(1)
                    }
                }
                d
            }
            targetWeekday != null -> {
                var daysAhead = (targetWeekday.value - now.dayOfWeek.value + 7) % 7
                if (daysAhead == 0) {
                    if (rawHour != null) {
                        val candidateToday = now.toLocalDate().atTime(finalHour, finalMinute).atZone(now.zone)
                        if (!candidateToday.isAfter(now)) {
                            daysAhead = 7
                        }
                    } else {
                        // If no hour was specified, but today is the target weekday:
                        // "Her cuma" / "cuma": if today is Friday, today is Friday unless the day has effectively ended
                        if (now.toLocalTime().isAfter(LocalTime.of(22, 0))) {
                            daysAhead = 7
                        }
                    }
                }
                now.toLocalDate().plusDays(daysAhead.toLong())
            }
            repeatRule == RecurrenceRule.DAILY -> {
                if (rawHour != null) {
                    val candidateToday = now.toLocalDate().atTime(finalHour, finalMinute).atZone(now.zone)
                    if (candidateToday.isAfter(now)) {
                        now.toLocalDate()
                    } else {
                        now.toLocalDate().plusDays(1)
                    }
                } else {
                    now.toLocalDate()
                }
            }
            repeatRule == RecurrenceRule.WEEKLY -> {
                // "Her hafta" without specific weekday -> dateHint remains null
                null
            }
            else -> {
                // No date mentioned, only time: e.g. "saat 5te annemi ara"
                if (rawHour != null) {
                    val candidateToday = now.toLocalDate().atTime(finalHour, finalMinute).atZone(now.zone)
                    if (candidateToday.isAfter(now)) {
                        now.toLocalDate()
                    } else {
                        now.toLocalDate().plusDays(1)
                    }
                } else {
                    null
                }
            }
        }

        val alertAt: Long?
        val requiresTimeSelection: Boolean
        val dateHint: LocalDate?

        if (rawHour != null && resolvedDate != null) {
            val targetZoned = resolvedDate.atTime(finalHour, finalMinute).atZone(now.zone)
            alertAt = targetZoned.toInstant().toEpochMilli()
            requiresTimeSelection = false
            dateHint = resolvedDate
        } else {
            // No exact hour was provided, or no resolved date -> do NOT invent 09:00!
            alertAt = null
            requiresTimeSelection = true
            dateHint = resolvedDate
        }

        // Clean text by removing matched date/daypart/time/recurrence ranges
        val cleaned = cleanReminderText(original, rangesToRemove)
        val finalText = cleaned.ifEmpty { "Hatırlatıcı" }

        return ParsedVoiceReminder(
            originalText = original,
            text = finalText,
            alertAt = alertAt,
            repeatRule = repeatRule,
            requiresTimeSelection = requiresTimeSelection,
            dateHint = dateHint,
            timeOfDay = timeOfDay
        )
    }

    private fun maskRange(input: String, range: IntRange): String {
        val chars = input.toCharArray()
        for (i in range) {
            if (i in chars.indices) chars[i] = ' '
        }
        return String(chars)
    }

    private fun cleanReminderText(original: String, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) {
            return formatText(original)
        }

        // Sort ranges and merge overlapping/adjacent
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        for (r in sorted) {
            if (merged.isEmpty()) {
                merged.add(r)
            } else {
                val last = merged.last()
                if (r.first <= last.last + 1) {
                    merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
                } else {
                    merged.add(r)
                }
            }
        }

        val sb = StringBuilder()
        var lastIdx = 0
        for (r in merged) {
            if (r.first > lastIdx) {
                sb.append(original.substring(lastIdx, minOf(r.first, original.length)))
            }
            lastIdx = maxOf(lastIdx, r.last + 1)
        }
        if (lastIdx < original.length) {
            sb.append(original.substring(lastIdx))
        }

        return formatText(sb.toString())
    }

    private fun formatText(raw: String): String {
        var clean = raw.trim()
            .trim(',', '.', ';', ':', '-', '’', '\'')
            .replace(Regex("""\s+"""), " ")
            .trim()

        // Strip boundary filler "ya" idempotently
        clean = stripBoundaryFillerYa(clean)

        // Strip trailing commands: e.g. "bana hatırlat", "hatırlat", "hatirlat"
        clean = clean.replace(Regex("""(?i)\s*(?:bana\s+)?(?:hatırlat|hatirlat)\s*[.!]?$"""), "").trim()
            .trim(',', '.', ';', ':', '-', '’', '\'')

        // Strip boundary filler "ya" again in case trailing command exposed it
        clean = stripBoundaryFillerYa(clean)

        // Strip standalone residual 'her' / 'Her'
        clean = clean.replace(Regex("""(?i)\bher\b"""), "").trim()
            .trim(',', '.', ';', ':', '-', '’', '\'')

        // Re-clean whitespace
        clean = clean.replace(Regex("""\s+"""), " ").trim()

        // Verb normalization on the last word if applicable
        val words = clean.split(" ").filter { it.isNotBlank() }
        if (words.isNotEmpty()) {
            val lastWord = words.last()
            val normalized = normalizeVerb(lastWord)
            if (normalized != lastWord) {
                clean = (words.dropLast(1) + normalized).joinToString(" ")
            }
        }

        // Capitalize first character in Turkish locale
        return if (clean.isNotEmpty()) {
            val trLocale = Locale.forLanguageTag("tr-TR")
            clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase(trLocale) else it.toString() }
        } else {
            ""
        }
    }

    fun formatPendingScheduleLabel(
        dateHint: LocalDate?,
        repeatRule: RecurrenceRule,
        timeOfDay: TimeOfDay? = null,
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): String = com.cagri.bigboscagri.speech.formatPendingScheduleLabel(dateHint, repeatRule, timeOfDay, now)

    fun formatComposerScheduleLabel(
        alertAt: Long,
        repeatRule: RecurrenceRule,
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): String = com.cagri.bigboscagri.speech.formatComposerScheduleLabel(alertAt, repeatRule, now)
}

fun formatPendingScheduleLabel(
    dateHint: LocalDate?,
    repeatRule: RecurrenceRule,
    timeOfDay: TimeOfDay? = null,
    now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
): String {
    val today = now.toLocalDate()
    val trLocale = Locale.forLanguageTag("tr-TR")

    val daypartStr = when (timeOfDay) {
        TimeOfDay.Morning -> "sabah"
        TimeOfDay.Noon -> "öğlen"
        TimeOfDay.Evening -> "akşam"
        TimeOfDay.Night -> "gece"
        null -> null
    }

    val schedulePart: String = when {
        repeatRule == RecurrenceRule.DAILY -> {
            if (daypartStr != null) "Her $daypartStr" else "Her gün"
        }
        repeatRule == RecurrenceRule.WEEKLY && dateHint != null -> {
            val weekdayName = when (dateHint.dayOfWeek) {
                DayOfWeek.MONDAY -> "Pazartesi"
                DayOfWeek.TUESDAY -> "Salı"
                DayOfWeek.WEDNESDAY -> "Çarşamba"
                DayOfWeek.THURSDAY -> "Perşembe"
                DayOfWeek.FRIDAY -> "Cuma"
                DayOfWeek.SATURDAY -> "Cumartesi"
                DayOfWeek.SUNDAY -> "Pazar"
                null -> ""
            }
            if (daypartStr != null) "$weekdayName $daypartStr" else weekdayName
        }
        repeatRule == RecurrenceRule.WEEKLY -> {
            return "Her hafta · Tarih ve saat seç"
        }
        repeatRule == RecurrenceRule.MONTHLY && dateHint != null -> {
            if (daypartStr != null) "Her ayın ${dateHint.dayOfMonth}'i $daypartStr" else "Her ayın ${dateHint.dayOfMonth}'i"
        }
        repeatRule == RecurrenceRule.MONTHLY -> {
            return "Her ay · Tarih ve saat seç"
        }
        dateHint != null -> {
            val dateLabel = when (dateHint) {
                today -> "Bugün"
                today.plusDays(1) -> "Yarın"
                else -> {
                    val days = ChronoUnit.DAYS.between(today, dateHint)
                    if (days in 2..6) {
                        when (dateHint.dayOfWeek) {
                            DayOfWeek.MONDAY -> "Pazartesi"
                            DayOfWeek.TUESDAY -> "Salı"
                            DayOfWeek.WEDNESDAY -> "Çarşamba"
                            DayOfWeek.THURSDAY -> "Perşembe"
                            DayOfWeek.FRIDAY -> "Cuma"
                            DayOfWeek.SATURDAY -> "Cumartesi"
                            DayOfWeek.SUNDAY -> "Pazar"
                        }
                    } else {
                        dateHint.format(DateTimeFormatter.ofPattern("d MMMM", trLocale))
                    }
                }
            }
            if (daypartStr != null) "$dateLabel $daypartStr" else dateLabel
        }
        else -> ""
    }

    return if (schedulePart.isNotBlank()) {
        "$schedulePart · Saat seç"
    } else {
        "Saat seç"
    }
}

fun formatComposerScheduleLabel(
    alertAt: Long,
    repeatRule: RecurrenceRule,
    now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
): String {
    val zdt = Instant.ofEpochMilli(alertAt).atZone(now.zone)
    val today = now.toLocalDate()
    val trLocale = Locale.forLanguageTag("tr-TR")
    val timeStr = String.format(trLocale, "%02d:%02d", zdt.hour, zdt.minute)

    val dateStr = when (zdt.toLocalDate()) {
        today -> "Bugün"
        today.plusDays(1) -> "Yarın"
        else -> {
            if (repeatRule == RecurrenceRule.WEEKLY) {
                when (zdt.dayOfWeek) {
                    DayOfWeek.MONDAY -> "Pazartesi"
                    DayOfWeek.TUESDAY -> "Salı"
                    DayOfWeek.WEDNESDAY -> "Çarşamba"
                    DayOfWeek.THURSDAY -> "Perşembe"
                    DayOfWeek.FRIDAY -> "Cuma"
                    DayOfWeek.SATURDAY -> "Cumartesi"
                    DayOfWeek.SUNDAY -> "Pazar"
                    null -> ""
                }
            } else {
                zdt.format(DateTimeFormatter.ofPattern("d MMM", trLocale))
            }
        }
    }

    return when (repeatRule) {
        RecurrenceRule.NONE -> "$dateStr $timeStr"
        RecurrenceRule.DAILY -> "$dateStr $timeStr · Her gün"
        RecurrenceRule.WEEKLY -> "$dateStr $timeStr · Her hafta"
        RecurrenceRule.MONTHLY -> "$dateStr $timeStr · Her ay"
        RecurrenceRule.YEARLY -> "$dateStr $timeStr · Her yıl"
    }
}