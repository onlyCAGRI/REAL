package com.cagri.real.speech

import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.firstPatternOccurrence
import java.time.Instant
import java.time.ZoneId

/** Single schedule policy for voice drafts and direct multi-card creation. */
data class VoiceCreationSchedule(
    val alertAt: Long?, val plannedEpochDay: Long?, val repeatRule: RecurrenceRule,
    val needsTime: Boolean
)

fun resolveVoiceCreationSchedule(
    intent: GeminiVoiceIntent,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): VoiceCreationSchedule {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val repeating = intent.recurrence != RecurrenceRule.NONE || intent.weekdaysMask != 0
    if (intent.time == null) {
        return VoiceCreationSchedule(null, intent.date?.toEpochDay(),
            if (intent.weekdaysMask != 0) RecurrenceRule.WEEKLY else intent.recurrence,
            repeating || intent.requiresTimeSelection || intent.timeOfDay != null)
    }
    var day = intent.date ?: now.toLocalDate()
    val at = if (intent.weekdaysMask != 0) {
        firstPatternOccurrence(maxOf(day, now.toLocalDate()), intent.time, intent.weekdaysMask, nowMillis, zone)
            ?: error("Tekrar için uygun gün bulunamadı.")
    } else {
        var value = day.atTime(intent.time).atZone(zone).toInstant().toEpochMilli()
        if (value <= nowMillis && (intent.date == null || intent.recurrence == RecurrenceRule.DAILY)) {
            day = maxOf(day, now.toLocalDate()).plusDays(1)
            value = day.atTime(intent.time).atZone(zone).toInstant().toEpochMilli()
        }
        require(value > nowMillis) { "Bu zaman geçmişte kaldı. Gelecekte bir gün ve saat söyle." }
        value
    }
    val selected = Instant.ofEpochMilli(at).atZone(zone)
    require(selected.toLocalTime() == intent.time && zone.rules.getValidOffsets(selected.toLocalDate().atTime(intent.time)).size == 1) {
        "Bu saat belirsiz veya geçerli değil. Başka bir saat seç."
    }
    require(intent.repeatUntil == null || !selected.toLocalDate().isAfter(intent.repeatUntil)) {
        "Tekrarın bitiş tarihi ilk tekrardan önce."
    }
    return VoiceCreationSchedule(at, null,
        if (intent.weekdaysMask != 0) RecurrenceRule.WEEKLY else intent.recurrence, false)
}
