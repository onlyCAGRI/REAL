package com.cagri.real.speech

import com.cagri.real.data.RecurrenceRule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** A conservative front-end to REAL's existing schedule resolver, not a second scheduler. */
internal data class LocalVoiceTiming(
    val remaining: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val daypart: TimeOfDay? = null,
    val rule: RecurrenceRule = RecurrenceRule.NONE,
    val weekdays: Int = 0,
    val until: LocalDate? = null,
    val needsClock: Boolean = false,
    val ambiguousClock: Boolean = false,
    val relative: Boolean = false,
    val consumed: Boolean = false,
    val dateSpecified: Boolean = false
)

internal fun localVoiceKey(text: String): String = text.lowercase(Locale.forLanguageTag("tr-TR"))
    .replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c')
    .replace('’','\'')

internal fun localNumber(text: String): Int? = text.trim().toIntOrNull() ?: mapOf(
    "bir" to 1,"iki" to 2,"uc" to 3,"dort" to 4,"bes" to 5,"alti" to 6,"yedi" to 7,
    "sekiz" to 8,"dokuz" to 9,"on" to 10,"on bir" to 11,"on iki" to 12,
    "on uc" to 13,"on dort" to 14,"on bes" to 15,"on alti" to 16,"on yedi" to 17,
    "on sekiz" to 18,"on dokuz" to 19,"yirmi" to 20,"yirmi bir" to 21,"yirmi iki" to 22,"yirmi uc" to 23
)[text.trim()]

internal object LocalVoiceScheduleParser {
    private val number = "(?:\\d{1,3}|yirmi uc|yirmi iki|yirmi bir|yirmi|on dokuz|on sekiz|on yedi|on alti|on bes|on dort|on uc|on iki|on bir|on|dokuz|sekiz|yedi|alti|bes|dort|uc|iki|bir)"
    private val days = listOf("pazartesi","sali","carsamba","persembe","cuma","cumartesi","pazar")
    private val months = listOf("ocak","subat","mart","nisan","mayis","haziran","temmuz","agustos","eylul","ekim","kasim","aralik")
    fun parse(raw: String, now: ZonedDateTime): LocalVoiceTiming? {
        val key=localVoiceKey(raw)
        val spans=mutableListOf<IntRange>()
        var masked=key
        fun consume(range: IntRange) { spans+=range; val chars=masked.toCharArray();for(i in range) chars[i]=' ';masked=String(chars) }
        var date:LocalDate?=null;var time:LocalTime?=null;var daypart:TimeOfDay?=null
        var rule=RecurrenceRule.NONE;var mask=0;var until:LocalDate?=null;var ambiguous=false;var relative=false
        val period=Regex("\\b(?:sadece\\s+)?($number)\\s+(gun|hafta|ay|yil)\\s+(?:boyunca|icin)\\b|\\bsadece\\s+($number)\\s+(gun|hafta|ay|yil)\\b").find(masked)
        var periodCount:Int?=null;var periodUnit=""
        if(period!=null) {
            val parsedCount=localNumber(period.groupValues[1].ifBlank { period.groupValues[3] }) ?: return null
            if(parsedCount !in 1..366) return null
            periodCount=parsedCount
            periodUnit=period.groupValues[2].ifBlank { period.groupValues[4] };consume(period.range)
        }
        val partMatches=Regex("\\b(?:bu\\s+)?(sabahlari|sabah|aksamlari|aksam|geceleri|gece|oglenleri|oglen|ogle)\\b").findAll(masked).toList()
        if(partMatches.size>1) return null
        partMatches.singleOrNull()?.let { m ->
            daypart=when { m.groupValues[1].startsWith("sabah")->TimeOfDay.Morning;m.groupValues[1].startsWith("aksam")->TimeOfDay.Evening;m.groupValues[1].startsWith("gece")->TimeOfDay.Night;else->TimeOfDay.Noon }
            if(m.value.endsWith("lari") || m.value.endsWith("leri")) rule=RecurrenceRule.DAILY
            consume(m.range)
        }
        val clockMatches=Regex("\\b(?:saat\\s+)?(\\d{1,2})[:.](\\d{2})(?:[' ]?(?:da|de|ta|te))?\\b|\\b(?:saat\\s+)?($number)\\s+bucuk(?:ta|te|da|de)?\\b|\\bsaat\\s+($number)(?:[' ]?(?:da|de|ta|te))?\\b|\\b($number)[' ]?(?:da|de|ta|te)\\b").findAll(masked).toList()
        if(clockMatches.size>1) return null
        clockMatches.singleOrNull()?.let { m ->
            val hour=if(m.groupValues[1].isNotBlank()) m.groupValues[1].toIntOrNull() else localNumber(m.groupValues.drop(3).firstOrNull { it.isNotBlank() }.orEmpty())
            val minute=if(m.groupValues[2].isNotBlank()) m.groupValues[2].toIntOrNull() else if(m.value.contains("bucuk")) 30 else 0
            if(hour==null || hour !in 0..23 || minute==null || minute !in 0..59) return null
            if(hour==12 && daypart in setOf(TimeOfDay.Morning,TimeOfDay.Evening)) return null
            val adjusted=when {
                hour>=13 || hour==0 -> hour
                daypart==TimeOfDay.Evening -> if(hour==12) 12 else hour+12
                daypart==TimeOfDay.Noon -> if(hour in 1..6) hour+12 else hour
                daypart==TimeOfDay.Night -> if(hour in 6..11) hour+12 else if(hour==12) 0 else hour
                daypart==TimeOfDay.Morning -> if(hour==12) 0 else hour
                else -> { ambiguous=true;hour }
            }
            if(!ambiguous) time=LocalTime.of(adjusted,minute)
            consume(m.range)
        }
        val relativeMatch=Regex("\\b($number)\\s+(dakika|dk|saat|gun|hafta)\\s+sonra\\b|\\b(?:bir\\s+)?yarim\\s+saat\\s+sonra\\b").findAll(masked).toList()
        if(relativeMatch.size>1) return null
        relativeMatch.singleOrNull()?.let { m ->
            if(time!=null || ambiguous || daypart!=null) return null
            val amount=if(m.value.contains("yarim")) 30 else localNumber(m.groupValues[1]) ?: return null
            if(amount !in 1..366) return null
            val unit=if(m.value.contains("yarim")) "dakika" else m.groupValues[2]
            // Reuse existing relative-time parser after converting spoken numbers.
            val parsed=VoiceReminderParser.parse("$amount $unit sonra görev",now)
            val zdt=parsed.alertAt?.let { java.time.Instant.ofEpochMilli(it).atZone(now.zone) }
            if(unit in setOf("gun","hafta")) { date=now.toLocalDate().plusDays(amount.toLong()*(if(unit=="hafta") 7 else 1)) }
            else { if(zdt==null) return null;date=zdt.toLocalDate();time=zdt.toLocalTime();relative=true }
            consume(m.range)
        }
        val explicit=Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b|\\b(\\d{1,2})\\s+(${months.joinToString("|")})(?:[' ]?(?:da|de|ta|te))?(?:\\s+(20\\d{2}))?\\b").findAll(masked).toList()
        if(explicit.size>1) return null
        explicit.singleOrNull()?.let { m ->
            if(date!=null) return null
            date=runCatching { if(m.groupValues[1].isNotBlank()) LocalDate.of(m.groupValues[1].toInt(),m.groupValues[2].toInt(),m.groupValues[3].toInt())
                else LocalDate.of(m.groupValues[6].toIntOrNull() ?: now.year,months.indexOf(m.groupValues[5])+1,m.groupValues[4].toInt()) }.getOrNull() ?: return null
            consume(m.range)
        }
        val relativeDays=Regex("\\b(obur gun|yarin|bugun|haftaya)\\b").findAll(masked).toList()
        if(relativeDays.size>1) return null
        relativeDays.singleOrNull()?.let { m ->
            if(date!=null) return null
            date=now.toLocalDate().plusDays(when(m.value) { "yarin"->1L;"obur gun"->2L;"haftaya"->7L;else->0L });consume(m.range)
        }
        val repeatMatches=Regex("\\b(her gun|her hafta|her ay|her yil|hafta ici|hafta sonu)\\b").findAll(masked).toList()
        if(repeatMatches.size>1) return null
        repeatMatches.singleOrNull()?.let { m ->
            rule=when(m.value) { "her gun"->RecurrenceRule.DAILY;"her ay"->RecurrenceRule.MONTHLY;"her yil"->RecurrenceRule.YEARLY;else->RecurrenceRule.WEEKLY }
            mask=when(m.value) { "hafta ici"->31;"hafta sonu"->96;else->0 };consume(m.range)
        }
        val weekdayMatches=Regex("\\b(?:(?:her|bu)\\s+)?(${days.sortedByDescending { it.length }.joinToString("|")})(?:lari|leri)?(?:\\s+gunu)?\\b").findAll(masked).toList()
        if(weekdayMatches.isNotEmpty()) {
            if(mask!=0 || date!=null) return null
            val chosen=weekdayMatches.map { days.indexOf(it.groupValues[1]) }.distinct()
            val recurring=chosen.size>1 || rule!=RecurrenceRule.NONE || weekdayMatches.any { it.value.startsWith("her ") || it.value.endsWith("lari") || it.value.endsWith("leri") }
            if(rule!=RecurrenceRule.NONE && rule!=RecurrenceRule.WEEKLY && !(rule==RecurrenceRule.DAILY && partMatches.isNotEmpty())) return null
            if(recurring) { rule=RecurrenceRule.WEEKLY;mask=chosen.fold(0) { v,d->v or (1 shl d) } }
            else date=now.toLocalDate().with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(chosen.first()+1)))
            weekdayMatches.forEach { consume(it.range) }
            // Only connectors between weekday spans are consumed; connectors in task text remain.
            for(i in 0 until weekdayMatches.lastIndex) {
                val start=weekdayMatches[i].range.last+1;val end=weekdayMatches[i+1].range.first
                val between=key.substring(start,end)
                if(!Regex("[ ,]*(?:ve[ ,]*)?").matches(between)) return null
                if(end>start) consume(start until end)
            }
        }
        val clock=time
        if(daypart==TimeOfDay.Night && clock!=null && clock.hour in 0..5) return null
        if(periodCount!=null) {
            val count=periodCount ?: return null
            if(rule==RecurrenceRule.NONE) return null
            val start=if(mask!=0) {
                if(clock!=null) com.cagri.real.data.firstPatternOccurrence(now.toLocalDate(),clock,mask,now.toInstant().toEpochMilli(),now.zone)
                    ?.let { java.time.Instant.ofEpochMilli(it).atZone(now.zone).toLocalDate() } ?: return null
                else (0L..6L).map { now.toLocalDate().plusDays(it) }.first { (mask and (1 shl (it.dayOfWeek.value-1)))!=0 }
            } else date ?: now.toLocalDate().let { day ->
                if(rule==RecurrenceRule.DAILY && clock!=null && !day.atTime(clock).atZone(now.zone).isAfter(now)) day.plusDays(1) else day
            }
            until=when(periodUnit) { "gun"->start.plusDays(count.toLong());"hafta"->start.plusWeeks(count.toLong());"ay"->start.plusMonths(count.toLong());else->start.plusYears(count.toLong()) }.minusDays(1)
        }
        val dateSpecified=date!=null
        if(date==null && daypart!=null) date=now.toLocalDate()
        if(clock!=null && date==null && rule==RecurrenceRule.NONE) {
            date=now.toLocalDate().let { if(it.atTime(clock).atZone(now.zone).isAfter(now)) it else it.plusDays(1) }
        }
        val rest=raw.toCharArray();for(span in spans) for(i in span) rest[i]=' '
        val remaining=String(rest).replace(Regex("\\s+")," ").trim().trim(',',';')
        if(Regex("\\b(saat|sonra|once|yarin|bugun|dun|gelecek|gecen|aksam|sabah|oglen|gunu|gun|gunleri|boyunca|hafta|ay|yil|dakika|dk|her|tekrarlasin|tekrar|bucuk|ceyrek|kala|geceleyin|ogleden|pazartesi|sali|carsamba|persembe|cuma|cumartesi|pazar)\\b").containsMatchIn(localVoiceKey(remaining))) return null
        if(Regex("\\b(?:${(days+months).joinToString("|")})(?:[' ]?(?:ya|ye|a|e|da|de|ta|te|ki))?\\b").containsMatchIn(localVoiceKey(remaining))) return null
        if(daypart!=null && time==null && Regex("\\b(?:\\d+|bir|iki|uc|dort|bes|alti|yedi|sekiz|dokuz|on)\\b").containsMatchIn(localVoiceKey(remaining))) return null
        return LocalVoiceTiming(remaining,date,time,daypart,rule,mask,until,
            needsClock=ambiguous || (time==null && (daypart!=null || rule!=RecurrenceRule.NONE)),
            ambiguousClock=ambiguous,relative=relative,consumed=spans.isNotEmpty(),dateSpecified=dateSpecified)
    }
}
