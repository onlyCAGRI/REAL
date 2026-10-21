package com.cagri.real.ui.components.planner

import com.cagri.real.calendar.CalendarOccurrence
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExternalPlannerEventsTest {
    @Test fun `external event stays a transient negative ID and does not create alerts`() {
        val occurrence = CalendarOccurrence(7, 10, "Meeting", 10_000, 70_000, false, "Google")
        val event = externalPlannerEvents(listOf(occurrence), ZoneId.of("UTC")).single()
        assertEquals(-7L, event.id)
        assertEquals(occurrence.end, event.endMillis)
        assertFalse(event.reminder.sendImmediateNotification)
        assertFalse(event.reminder.isImportant)
    }
    @Test fun `all day UTC dates display on the same Istanbul calendar day including multi day end`() {
        val utc = ZoneId.of("UTC"); val local = ZoneId.of("Europe/Istanbul")
        val day = LocalDate.parse("2026-10-07")
        val occurrence = CalendarOccurrence(7, 10, "Trip", day.atStartOfDay(utc).toInstant().toEpochMilli(),
            day.plusDays(3).atStartOfDay(utc).toInstant().toEpochMilli(), true, "Google")
        val event = externalPlannerEvents(listOf(occurrence), local).single()
        assertEquals(day, event.day(local))
        assertEquals(day.plusDays(3).atStartOfDay(local).toInstant().toEpochMilli(), event.endMillis)
        assertTrue(event.isDateOnly24Hour)
    }
}
