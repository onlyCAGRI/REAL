package com.cagri.real.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WaterRetentionTest {
    @Test fun todayAndPrevious29LocalDaysAreRetainedAcrossDst() {
        val today = LocalDate.of(2026, 3, 30)
        val zone = ZoneId.of("Europe/Berlin")
        val boundary = today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
        val entries = listOf(WaterIntakeEntry("old", 250, boundary - 1), WaterIntakeEntry("edge", 250, boundary),
            WaterIntakeEntry("today", 250, today.atStartOfDay(zone).toInstant().toEpochMilli()))
        assertEquals(listOf("edge", "today"), retainRecentWaterEntries(entries, today, zone).map { it.id })
        assertEquals(3, entries.size)
    }
    @Test fun retentionIsIdempotentAndEmptyHistoryIsSafe() {
        val today = LocalDate.of(2026, 10, 10); val zone = ZoneId.of("Europe/Istanbul")
        val rows = listOf(WaterIntakeEntry("today", 250, today.atStartOfDay(zone).toInstant().toEpochMilli()))
        assertEquals(rows, retainRecentWaterEntries(retainRecentWaterEntries(rows, today, zone), today, zone))
        assertTrue(retainRecentWaterEntries(emptyList(), today, zone).isEmpty())
    }
}
