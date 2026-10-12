package com.cagri.real.ui.components.planner

import com.cagri.real.data.ROLLING_24_HOURS_MILLIS
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class PlannerQueryRangeTest {
    private val istanbul = ZoneId.of("Europe/Istanbul")

    @Test fun sameLocalDayTicksDoNotMoveRangeForDistantPastOrFutureMonth() {
        val date = LocalDate.of(2026, 10, 4)
        for (month in listOf(YearMonth.of(2025, 1), YearMonth.of(2027, 7))) {
            val ranges = listOf(LocalTime.MIN, LocalTime.NOON, LocalTime.of(23, 59)).map { time ->
                val now = date.atTime(time).atZone(istanbul).toInstant()
                plannerQueryRange(month, now.atZone(istanbul).toLocalDate(), istanbul)
            }
            assertEquals(1, ranges.toSet().size)
        }
    }
    @Test fun visibleMonthAndItsWeekMarginsRemainCovered() {
        val month = YearMonth.of(2026, 10)
        val range = plannerQueryRange(month, LocalDate.of(2026, 10, 4), istanbul)
        assertTrue(range.startMillis <= month.atDay(1).minusDays(7).atStartOfDay(istanbul).toInstant().toEpochMilli())
        assertTrue(range.endMillis >= month.plusMonths(1).atDay(1).plusDays(6).atStartOfDay(istanbul).toInstant().toEpochMilli())
    }
    @Test fun lateEveningRollingWindowIsIncludedAcrossShortDstDays() {
        for ((zone, date) in listOf(
            ZoneId.of("America/New_York") to LocalDate.of(2026, 3, 7),
            ZoneId.of("Europe/Berlin") to LocalDate.of(2026, 3, 28)
        )) assertFullWindow(zone, date)
    }
    @Test fun fullRollingWindowIsIncludedAcrossLongDstDays() {
        for ((zone, date) in listOf(
            ZoneId.of("America/New_York") to LocalDate.of(2026, 10, 31),
            ZoneId.of("Europe/Berlin") to LocalDate.of(2026, 10, 24)
        )) assertFullWindow(zone, date)
    }
    private fun assertFullWindow(zone: ZoneId, date: LocalDate) {
        for (time in listOf(LocalTime.MIN, LocalTime.NOON, LocalTime.of(23, 59, 59))) {
            val now = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            for (month in listOf(YearMonth.of(2025, 1), YearMonth.of(2027, 7))) {
                val range = plannerQueryRange(month, date, zone)
                assertTrue("Range excludes now for $date $time $zone", range.startMillis <= now)
                assertTrue("Range truncates 24h for $date $time $zone", range.endMillis >= now + ROLLING_24_HOURS_MILLIS)
            }
        }
    }
    @Test fun midnightAdvancesTheRollingQueryBoundary() {
        val month = YearMonth.of(2025, 1)
        val day = LocalDate.of(2026, 10, 4)
        val old = plannerQueryRange(month, day, istanbul)
        val next = plannerQueryRange(month, day.plusDays(1), istanbul)
        assertEquals(old.startMillis, next.startMillis)
        assertEquals(86_400_000L, next.endMillis - old.endMillis)
    }
    @Test fun timezoneChangeUsesNewLocalDayBoundaries() {
        val day = LocalDate.of(2026, 10, 4)
        val month = YearMonth.of(2027, 7)
        val utc = plannerQueryRange(month, day, ZoneId.of("UTC"))
        val turkey = plannerQueryRange(month, day, istanbul)
        assertNotEquals(utc, turkey)
        assertEquals(day.atStartOfDay(istanbul).toInstant().toEpochMilli(), turkey.startMillis)
    }
    @Test fun monthMarginCrossesYearBoundaryWithoutTruncation() {
        val range = plannerQueryRange(YearMonth.of(2026, 12), LocalDate.of(2026, 12, 31), istanbul)
        assertEquals(LocalDate.of(2026, 11, 24).atStartOfDay(istanbul).toInstant().toEpochMilli(), range.startMillis)
        assertEquals(LocalDate.of(2027, 1, 7).atStartOfDay(istanbul).toInstant().toEpochMilli(), range.endMillis)
    }
}
