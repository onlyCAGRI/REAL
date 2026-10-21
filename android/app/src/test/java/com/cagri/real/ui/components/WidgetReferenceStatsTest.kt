package com.cagri.real.ui.components

import com.cagri.real.data.JournalMood
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.ReminderJournal
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WidgetReferenceStatsTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val today = LocalDate.of(2026, 10, 3)
    private val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val now = start + 12 * 3_600_000L
    private fun card(id: Long) = ReminderItem(id, "REAL $id", start + id)

    @Test fun emptyWidgetsUseRealZeros() {
        val stats = refTodayStats(emptyList(), today, zone, now)
        assertEquals(RefTodayStats(0, 0, 0, 0), stats)
        assertEquals(0f, stats.fraction, 0f)
        assertEquals(List(7) { 0 }, refCompletedPerDay(emptyList(), 7, today, zone))
    }

    @Test fun moodAndJournalAreNotTaskCountsOrChartSamples() {
        val cards = listOf(card(1), card(2).copy(mood = JournalMood.GOOD, completedAt = now),
            card(3).copy(journal = ReminderJournal(null, "Journal", now), completedAt = now))
        assertEquals(listOf(1L), refTasks(cards).map { it.id })
        assertTrue(refDoneTasks(cards).isEmpty())
        assertEquals(List(7) { 0 }, refCompletedPerDay(cards, 7, today, zone))
    }

    @Test fun pendingAndRealCompletionsFormTodaysDenominator() {
        val cards = listOf(
            card(1).copy(plannedDateEpochDay = today.toEpochDay()),
            card(2).copy(alertAt = now + 3_600_000L),
            card(3).copy(completedAt = now),
            card(4).copy(plannedDateEpochDay = today.toEpochDay(), completedAt = start - 1),
            card(5).copy(alertAt = start + 86_400_000L), card(6)
        )
        val stats = refTodayStats(cards, today, zone, now)
        assertEquals(RefTodayStats(1, 3, 2, 0), stats)
        assertEquals(1f / 3f, stats.fraction, 0.001f)
    }

    @Test fun overdueIsSeparateAndDoesNotCountCompletedOrFutureCards() {
        val cards = listOf(
            card(1).copy(alertAt = now - 1),
            card(2).copy(alertAt = now),
            card(3).copy(plannedDateEpochDay = today.minusDays(1).toEpochDay()),
            card(4).copy(plannedDateEpochDay = today.toEpochDay()),
            card(5).copy(alertAt = now - 1, completedAt = now),
            card(6).copy(alertAt = now + 1)
        )
        assertEquals(2, refTodayStats(cards, today, zone, now).overdue)
    }

    @Test fun completedUndatedTaskStillCountsAsARealCompletionToday() {
        val stats = refTodayStats(listOf(card(1).copy(completedAt = now)), today, zone, now)
        assertEquals(RefTodayStats(1, 1, 0, 0), stats)
        assertEquals(1f, stats.fraction, 0f)
    }

    @Test fun dateOnlySortUsesLocalMidnightAndUndatedCardsFollowDatedCards() {
        val cards = listOf(card(1), card(2).copy(plannedDateEpochDay = today.toEpochDay()),
            card(3).copy(alertAt = start + 30 * 60_000L), card(4).copy(completedAt = now))
        assertEquals(listOf(2L, 3L, 1L), refActiveTasks(cards, zone).map { it.id })
    }

    @Test fun completedCardsAreNewestFirstAndReadOnlyProjectionDoesNotMutateCards() {
        val cards = listOf(card(1).copy(completedAt = now - 1), card(2).copy(completedAt = now), card(3))
        val before = cards.toList()
        assertEquals(listOf(2L, 1L), refDoneTasks(cards).map { it.id })
        assertEquals(before, cards)
        assertFalse(cards.last().isCompleted)
    }

    @Test fun histogramHasExactRangeAndLocalMidnightBoundaries() {
        val cards = listOf(
            card(1).copy(completedAt = start), card(2).copy(completedAt = start - 1),
            card(3).copy(completedAt = today.minusDays(8).atStartOfDay(zone).toInstant().toEpochMilli()),
            card(4).copy(completedAt = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        )
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1), refCompletedPerDay(cards, 7, today, zone))
        assertEquals(14, refCompletedPerDay(cards, 14, today, zone).size)
        assertEquals(30, refCompletedPerDay(cards, 30, today, zone).size)
    }

    @Test fun plannedDateTakesPriorityLikeTheExistingPlannerModel() {
        val reminder = card(1).copy(plannedDateEpochDay = today.toEpochDay(), alertAt = start - 86_400_000L)
        assertEquals(today, refDueDay(reminder, zone))
        assertEquals(0, refTodayStats(listOf(reminder), today, zone, now).overdue)
    }

    @Test fun midnightAndTimezoneChangeGenerateANewClockDay() {
        assertEquals(today, widgetReferenceClock(start, zone).today)
        assertEquals(today.minusDays(1), widgetReferenceClock(start - 1, zone).today)
        assertEquals(today.minusDays(1), widgetReferenceClock(start, ZoneId.of("America/New_York")).today)
        assertEquals(start, widgetReferenceClock(start, zone).nowMillis)
    }

    @Test fun dstHistogramsUseLocalDaysInsteadOfFixedTwentyFourHourBuckets() {
        val berlin = ZoneId.of("Europe/Berlin")
        val date = LocalDate.of(2026, 10, 25)
        val early = date.atTime(2, 15).atZone(berlin).withEarlierOffsetAtOverlap().toInstant().toEpochMilli()
        val late = date.atTime(2, 15).atZone(berlin).withLaterOffsetAtOverlap().toInstant().toEpochMilli()
        assertEquals(listOf(2), refCompletedPerDay(listOf(card(1).copy(completedAt = early),
            card(2).copy(completedAt = late)), 1, date, berlin))
    }

    @Test(expected = IllegalArgumentException::class)
    fun histogramRejectsZeroLengthRange() { refCompletedPerDay(emptyList(), 0, today, zone) }
}
