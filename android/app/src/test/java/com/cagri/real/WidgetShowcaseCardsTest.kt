package com.cagri.real

import com.cagri.real.data.BeverageType
import com.cagri.real.data.JournalMood
import com.cagri.real.data.MoodDetails
import com.cagri.real.data.MoodTrackerEntry
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.WaterIntakeEntry
import com.cagri.real.ui.components.MoodRange
import com.cagri.real.ui.components.moodMonthPoints
import com.cagri.real.ui.components.moodPlotPoints
import com.cagri.real.ui.components.categoryProgress
import com.cagri.real.data.ReminderCategory
import com.cagri.real.data.ReminderSortOrder
import com.cagri.real.data.filterAndSortReminders
import com.cagri.real.ui.components.weekProgress
import com.cagri.real.ui.components.weekWaterRatios
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetShowcaseCardsTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private fun ms(d: LocalDate, hour: Int = 12) = d.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun moodPointsAverageWithinADayAndIgnoreOtherMonths() {
        val d = LocalDate.of(2026, 9, 10)
        val entries = listOf(
            MoodTrackerEntry(1, JournalMood.BAD, MoodDetails(loggedAt = ms(d, 9))),        // level 0
            MoodTrackerEntry(2, JournalMood.VERY_GOOD, MoodDetails(loggedAt = ms(d, 18))), // level 4
            MoodTrackerEntry(3, JournalMood.GOOD, MoodDetails(loggedAt = ms(LocalDate.of(2026, 8, 31))))
        )
        val points = moodMonthPoints(entries, YearMonth.of(2026, 9), zone)
        assertEquals(1, points.size)
        assertEquals(10, points[0].day)
        assertEquals(2f, points[0].level, 0.001f)
    }

    @Test fun weekProgressCountsOnlyThisWeekAndSkipsMoodCards() {
        val today = LocalDate.of(2026, 9, 29) // Tuesday, week = Sep 28 .. Oct 4
        fun r(id: Long, day: LocalDate, done: Boolean, important: Boolean = false, mood: JournalMood? = null) =
            ReminderItem(
                id = id, text = "t$id", createdAt = 0L, isImportant = important, mood = mood,
                plannedDateEpochDay = day.toEpochDay(), completedAt = if (done) ms(day) else null
            )
        val list = listOf(
            r(1, today, done = true, important = true),
            r(2, today.plusDays(2), done = false, important = true),
            r(3, today.minusDays(9), done = true),           // last week: excluded
            r(4, today, done = true, mood = JournalMood.GOOD) // mood card: excluded
        )
        val p = weekProgress(list, today, zone)
        assertEquals(1, p.done); assertEquals(2, p.total)
        assertEquals(1, p.importantDone); assertEquals(2, p.importantTotal)
        assertEquals(0.5f, p.fraction, 0.001f)
    }

    @Test fun emptyWeekIsZeroNotNaN() {
        assertEquals(0f, weekProgress(emptyList(), LocalDate.of(2026, 9, 29), zone).fraction, 0f)
    }

    @Test fun waterRatiosCoverSevenDaysOldestFirst() {
        val today = LocalDate.of(2026, 9, 29)
        val entries = listOf(
            WaterIntakeEntry("a", 1000, ms(today), BeverageType.WATER),
            WaterIntakeEntry("b", 500, ms(today.minusDays(6)), BeverageType.WATER),
            WaterIntakeEntry("c", 999, ms(today), BeverageType.BEER) // other drinks do not count toward water
        )
        val r = weekWaterRatios(entries, 2000, today, zone)
        assertEquals(7, r.size)
        assertEquals(0.25f, r.first(), 0.001f)
        assertEquals(0.5f, r.last(), 0.001f)
        assertTrue(r.subList(1, 6).all { it == 0f })
    }

    @Test fun dayViewShowsEveryRecordOfTheDayInTimeOrder() {
        val now = LocalDate.of(2026, 9, 29).atTime(20, 0).atZone(zone)
        val today = now.toLocalDate()
        val entries = listOf(
            MoodTrackerEntry(1, JournalMood.GOOD, MoodDetails(loggedAt = ms(today, 18))),
            MoodTrackerEntry(2, JournalMood.BAD, MoodDetails(loggedAt = ms(today, 8))),
            MoodTrackerEntry(3, JournalMood.HAPPY, MoodDetails(loggedAt = ms(today.minusDays(1), 9)))
        )
        val points = moodPlotPoints(entries, MoodRange.Day, now)
        assertEquals(2, points.size)                       // yesterday's record is not shown
        assertEquals("08:00", points[0].label)             // chronological
        assertEquals("18:00", points[1].label)
        assertTrue(points[0].x < points[1].x)
        assertEquals("Kötü", points[0].detail)
    }

    @Test fun weekViewAveragesPerDayAndSpansMondayToSunday() {
        val now = LocalDate.of(2026, 9, 29).atTime(12, 0).atZone(zone) // Tuesday
        val monday = LocalDate.of(2026, 9, 28)
        val entries = listOf(
            MoodTrackerEntry(1, JournalMood.BAD, MoodDetails(loggedAt = ms(monday, 9))),
            MoodTrackerEntry(2, JournalMood.VERY_GOOD, MoodDetails(loggedAt = ms(monday, 21))),
            MoodTrackerEntry(3, JournalMood.GOOD, MoodDetails(loggedAt = ms(LocalDate.of(2026, 10, 4)))),
            MoodTrackerEntry(4, JournalMood.GOOD, MoodDetails(loggedAt = ms(LocalDate.of(2026, 9, 27)))) // previous Sunday
        )
        val points = moodPlotPoints(entries, MoodRange.Week, now)
        assertEquals(2, points.size)
        assertEquals(0f, points[0].x, 0f)
        assertEquals(2f, points[0].level, 0.001f)          // (0 + 4) / 2
        assertEquals(1f, points[1].x, 0f)                  // Sunday Oct 4
    }

    @Test fun categoryProgressCountsCompletedItemsThatTheListHides() {
        val today = LocalDate.of(2026, 9, 29)
        fun r(id: Long, done: Boolean, important: Boolean = false, alert: Long? = null, cat: Long? = null) =
            ReminderItem(id = id, text = "t$id", createdAt = id, isImportant = important, alertAt = alert,
                customCategoryId = cat, completedAt = if (done) ms(today) else null)
        val list = listOf(
            r(1, done = true, important = true), r(2, done = false, important = true),
            r(3, done = true, important = true), r(4, done = false),
            r(5, done = true, alert = 5000L), r(6, done = false, cat = 9L), r(7, done = true, cat = 9L)
        )
        val important = categoryProgress(list, ReminderCategory.Important, null, today, zone)!!
        assertEquals(2, important.done); assertEquals(3, important.total); assertEquals(2, important.doneToday)
        val custom = categoryProgress(list, null, 9L, today, zone)!!
        assertEquals(1, custom.done); assertEquals(2, custom.total)
        assertEquals(0.5f, custom.fraction, 0.001f)
        assertEquals(null, categoryProgress(list, ReminderCategory.Completed, null, today, zone))
        assertEquals(null, categoryProgress(list, ReminderCategory.JournalEntries, null, today, zone))
    }

    @Test fun categorySortModesOrderAsExpected() {
        val now = 1_000_000L
        val a = ReminderItem(id = 1, text = "a", createdAt = 100L, alertAt = now + 500)    // near future
        val b = ReminderItem(id = 2, text = "b", createdAt = 200L, alertAt = now + 9_000)  // far future
        val c = ReminderItem(id = 3, text = "c", createdAt = 300L, alertAt = now - 100)    // just passed
        val d = ReminderItem(id = 4, text = "d", createdAt = 400L)                          // undated
        val all = listOf(a, b, c, d)
        assertEquals(listOf(4L, 3L, 2L, 1L), filterAndSortReminders(all, sortOrder = ReminderSortOrder.RECENTLY_ADDED).map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), filterAndSortReminders(all, sortOrder = ReminderSortOrder.OLDEST_FIRST).map { it.id })
        assertEquals(
            listOf(3L, 1L, 2L, 4L),
            filterAndSortReminders(all, sortOrder = ReminderSortOrder.NEAREST_DATE, nowMillis = now, zone = zone).map { it.id }
        )
    }
}
