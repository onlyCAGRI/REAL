package com.cagri.real.calendar

import com.cagri.real.i18n.t
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.computeNextRepeat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class DeviceCalendar(
    val id: Long, val name: String, val account: String, val accountType: String,
    val writable: Boolean, val syncEnabled: Boolean
) {
    val label: String get() = "$name · $account"
    val identity: String get() = "$accountType\n$account\n$name"
    val isGoogle: Boolean get() = accountType == "com.google"
}

internal data class CalendarOptions(
    val readIds: Set<Long> = emptySet(),
    val readIdentities: Map<Long, String> = emptyMap(),
    val targetId: Long? = null,
    val targetIdentity: String? = null,
    val exportEnabled: Boolean = false,
    val exportAll: Boolean = false,
    val selectedIds: Set<Long> = emptySet()
)

internal data class CalendarOccurrence(
    val eventId: Long, val calendarId: Long, val title: String, val start: Long,
    val end: Long, val allDay: Boolean, val calendarName: String
)

internal data class CalendarPlan(
    val key: String, val reminderId: Long, val title: String, val start: Long,
    val end: Long, val allDay: Boolean, val zone: String, val publicUrl: String? = null
)

/** Materialize native REAL occurrences, preserving its month-end and skipped-day semantics.
 * Repeats are a rolling 90-day projection, never an invented perpetual Google series. */
internal fun calendarPlans(
    reminders: List<ReminderItem>, options: CalendarOptions,
    durations: Map<Long, Int>, now: Long, zone: ZoneId = ZoneId.systemDefault(),
    publicUrls: Map<Long, String> = emptyMap(), linkedExternalCardIds: Set<Long> = emptySet()
): List<CalendarPlan> = buildList {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val endWindow = today.plusDays(90).atStartOfDay(zone).toInstant().toEpochMilli()
    reminders.filter { !it.isCompleted && it.id !in linkedExternalCardIds && (options.exportAll || it.id in options.selectedIds) }.forEach { r ->
        val minutes = (durations[r.id] ?: 60).coerceIn(1, 1440)
        fun addTimed(start: Long, key: String) {
            if (start + minutes * 60_000L > now) add(CalendarPlan(
                key, r.id, r.text, start, start + minutes * 60_000L, false, zone.id, publicUrls[r.id]
            ))
        }
        r.plannedDateEpochDay?.let { epoch ->
            val day = LocalDate.ofEpochDay(epoch)
            if (day >= today) add(CalendarPlan("${r.id}:single", r.id, r.text,
                day.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
                day.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(), true, "UTC", publicUrls[r.id]))
            return@forEach
        }
        val at = r.alertAt ?: return@forEach // Text-only and temporary 24-hour buckets are not scheduled events.
        if (r.repeatRule == RecurrenceRule.NONE) {
            addTimed(at, "${r.id}:single")
            return@forEach
        }
        val anchor = r.anchorAlertAt ?: at
        val anchorDay = Instant.ofEpochMilli(anchor).atZone(zone).toLocalDate().toEpochDay()
        val windowStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        fun next(after: Long) = computeNextRepeat(anchor, r.repeatRule, r.repeatWeekdaysMask,
            r.repeatUntilEpochDay, zone, after, r.repeatSkippedEpochDays)
        var occurrence = if (anchor >= windowStart && anchorDay !in r.repeatSkippedEpochDays &&
            (r.repeatUntilEpochDay == null || anchorDay <= r.repeatUntilEpochDay)) anchor
        else next(maxOf(anchor, windowStart - 1))
        var count = 0
        while (occurrence != null && occurrence < endWindow && count++ < 100) {
            val day = Instant.ofEpochMilli(occurrence).atZone(zone).toLocalDate().toEpochDay()
            addTimed(occurrence, "${r.id}:$day")
            occurrence = next(occurrence)
        }
    }
}

internal data class CalendarLink(
    val key: String, val marker: String, val calendarId: Long, val calendarIdentity: String,
    val eventId: Long? = null, val fingerprint: String? = null, val start: Long,
    val suppressed: Boolean = false
)

internal data class CalendarSyncResult(
    val inserted: Int = 0, val updated: Int = 0, val removed: Int = 0,
    val conflicts: Int = 0, val unavailable: Int = 0
) {
    val message: String get() = t("Takvim: {0} eklendi, {1} güncellendi, {2} kaldırıldı.", inserted, updated, removed) +
        if (unavailable > 0) t(" Hedef takvim kullanılamıyor; cihaz hesabını ve seçimini kontrol et.")
        else if (conflicts > 0) t(" {0} dış değişiklik korundu. Google/Samsung Takvim’den kontrol et.", conflicts) else ""
}
