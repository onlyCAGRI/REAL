package com.cagri.real.calendar

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExternalCalendarDetailTimeTest {
    @Test fun `single all day detail excludes following day`() {
        val day = LocalDate.parse("2026-10-07"); val utc = ZoneId.of("UTC")
        val event = CalendarOccurrence(1, 2, "Test", day.atStartOfDay(utc).toInstant().toEpochMilli(),
            day.plusDays(1).atStartOfDay(utc).toInstant().toEpochMilli(), true, "Calendar")
        val label = externalCalendarTimeLabel(event, ZoneId.of("Europe/Istanbul"))
        assertTrue(label.contains("7 Ekim")); assertFalse(label.contains("8 Ekim")); assertTrue(label.contains("Tüm gün"))
    }
}
