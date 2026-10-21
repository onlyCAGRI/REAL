package com.cagri.real.calendar

import com.cagri.real.data.ReminderItem
import com.cagri.real.data.RecurrenceRule
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CalendarPlansTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private fun at(day: String, hour: Int = 20) = LocalDate.parse(day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val options = CalendarOptions(exportEnabled = true, exportAll = true)
    private fun project(r: ReminderItem, now: Long = at("2026-10-05", 0), opts: CalendarOptions = options,
        duration: Map<Long, Int> = emptyMap()) = calendarPlans(listOf(r), opts, duration, now, zone)

    @Test fun `ordinary text and temporary focus cards are excluded`() {
        assertTrue(project(ReminderItem(1, "Dambıl al", 1)).isEmpty())
        assertTrue(project(ReminderItem(1, "Dambıl al", 1, quick24HourUntilAt = at("2026-10-06"))).isEmpty())
    }
    @Test fun `nothing is exported until selected`() {
        val r = ReminderItem(1, "Spor", 1, alertAt = at("2026-10-07"))
        assertTrue(project(r, opts = options.copy(exportAll = false)).isEmpty())
        assertEquals(1, project(r, opts = options.copy(exportAll = false, selectedIds = setOf(1))).size)
    }
    @Test fun `timed event uses native planner duration`() {
        val r = ReminderItem(1, "Spor", 1, alertAt = at("2026-10-07"))
        val p = project(r, duration = mapOf(1L to 45)).single()
        assertEquals(45 * 60_000L, p.end - p.start)
        assertEquals("Europe/Istanbul", p.zone)
    }
    @Test fun `date only is a full UTC day without invented local time`() {
        val day = LocalDate.parse("2026-10-07")
        val p = project(ReminderItem(1, "Seyahat", 1, plannedDateEpochDay = day.toEpochDay())).single()
        assertTrue(p.allDay)
        assertEquals(day.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(), p.start)
        assertEquals(86_400_000L, p.end - p.start)
    }
    @Test fun `Wednesday Saturday recurrence respects inclusive end and skipped occurrence`() {
        val skipped = LocalDate.parse("2026-10-10").toEpochDay()
        val r = ReminderItem(1, "Spor", 1, alertAt = at("2026-10-07"), repeatRule = RecurrenceRule.WEEKLY,
            repeatWeekdaysMask = (1 shl 2) or (1 shl 5), repeatUntilEpochDay = LocalDate.parse("2026-11-07").toEpochDay(),
            repeatSkippedEpochDays = listOf(skipped))
        val dates = project(r).map { java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }
        assertTrue(dates.isNotEmpty())
        assertTrue(dates.all { it.dayOfWeek.value in setOf(3, 6) && it <= LocalDate.parse("2026-11-07") })
        assertFalse(LocalDate.ofEpochDay(skipped) in dates)
        assertEquals(LocalDate.parse("2026-11-07"), dates.last())
    }
    @Test fun `rolling horizon is bounded and extends when REAL reopens`() {
        val r = ReminderItem(1, "Spor", 1, alertAt = at("2026-10-05"), repeatRule = RecurrenceRule.DAILY)
        val first = project(r)
        assertEquals(90, first.size)
        val later = project(r, now = at("2026-10-06", 0))
        assertTrue(later.last().start > first.last().start)
        assertEquals(90, later.size)
    }
    @Test fun `completed events excluded and time edits keep a stable occurrence key`() {
        val r = ReminderItem(1, "Spor", 1, alertAt = at("2026-10-07"))
        assertTrue(project(r.copy(completedAt = 1)).isEmpty())
        assertEquals(project(r).single().key, project(r.copy(alertAt = at("2026-10-07", 21))).single().key)
    }
    @Test fun `monthly projection preserves native month end clamp`() {
        val r = ReminderItem(1, "Plan", 1, alertAt = at("2027-01-31"), repeatRule = RecurrenceRule.MONTHLY)
        val days = project(r, now = at("2027-01-30", 0)).map { java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }
        assertTrue(LocalDate.parse("2027-02-28") in days)
        assertTrue(LocalDate.parse("2027-03-31") in days)
    }
    @Test fun `externally linked cards do not create a second calendar event`() {
        val r = ReminderItem(1, "Linked card", 1, alertAt = at("2026-10-07"))
        assertTrue(calendarPlans(listOf(r), options, emptyMap(), at("2026-10-05"), zone,
            linkedExternalCardIds = setOf(1)).isEmpty())
    }

}
