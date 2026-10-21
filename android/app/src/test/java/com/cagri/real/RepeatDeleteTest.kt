package com.cagri.real

import com.cagri.real.data.*
import com.cagri.real.ui.components.planner.PlannerEventDetails
import com.cagri.real.ui.components.planner.day
import com.cagri.real.ui.components.planner.plannerEvents
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** Planner delete of ONE occurrence / "this and future" of a repeating reminder (a single stored card). */
class RepeatDeleteTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val oct = { d: Int -> LocalDate.of(2026, 10, d) }
    private fun at(d: Int, hour: Int = 9): Long = oct(d).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun epochDay(d: Int) = oct(d).toEpochDay()
    private val now = at(4, 12) // Sun 4 Oct 2026, noon

    private fun daily(anchorDay: Int = 5, alertDay: Int = anchorDay) = ReminderItem(
        id = 1, text = "Spor", createdAt = at(1),
        alertAt = at(alertDay), anchorAlertAt = at(anchorDay), repeatRule = RecurrenceRule.DAILY
    )

    private fun repo(item: ReminderItem): ReminderRepository =
        ReminderRepository(FakeSharedPreferences()).also { it.restore(listOf(item)) }

    private fun shownDays(item: ReminderItem, fromDay: Int = 1, toDay: Int = 20): List<Int> =
        plannerEvents(listOf(item), emptyMap<Long, PlannerEventDetails>(), zone, at(fromDay, 0), at(toDay, 0))
            .map { it.day(zone).dayOfMonth }

    @Test fun deletingOneDayKeepsTheSeriesAndHidesOnlyThatDay() {
        val repo = repo(daily())
        val result = repo.deleteRepeatOccurrence(1, at(7), RepeatDeleteScope.THIS_OCCURRENCE, now, zone)
        assertEquals(RepeatDeleteStatus.OCCURRENCE_REMOVED, result.status)
        val item = repo.load().single()
        assertEquals(listOf(epochDay(7)), item.repeatSkippedEpochDays)
        assertNull(item.repeatUntilEpochDay)
        assertEquals(at(5), item.alertAt)
        assertEquals(listOf(5, 6, 8, 9), shownDays(item, 5, 10))
    }

    @Test fun deletingTheUpcomingOccurrenceMovesTheAlarmToTheNextDay() {
        val repo = repo(daily())
        repo.deleteRepeatOccurrence(1, at(5), RepeatDeleteScope.THIS_OCCURRENCE, now, zone)
        val item = repo.load().single()
        assertEquals(at(6), item.alertAt)
        assertEquals(at(5), item.anchorAlertAt)
        assertEquals(listOf(6, 7), shownDays(item, 5, 8))
    }

    @Test fun deletedDaysAreSkippedWhenTheAlarmAdvances() {
        val item = daily().copy(repeatSkippedEpochDays = listOf(epochDay(6)))
        val next = computeNextRepeat(
            baseAlertAt = item.anchorAlertAt!!, rule = item.repeatRule, weekdaysMask = 0, untilEpochDay = null,
            zone = zone, afterMillis = at(5), skippedEpochDays = item.repeatSkippedEpochDays
        )
        assertEquals(at(7), next)
    }

    @Test fun thisAndFutureEndsTheSeriesTheDayBeforeAndKeepsEarlierDays() {
        val repo = repo(daily())
        val result = repo.deleteRepeatOccurrence(1, at(8), RepeatDeleteScope.THIS_AND_FUTURE, now, zone)
        assertEquals(RepeatDeleteStatus.OCCURRENCE_REMOVED, result.status)
        val item = repo.load().single()
        assertEquals(epochDay(7), item.repeatUntilEpochDay)
        assertEquals(at(5), item.alertAt)
        assertFalse(item.isCompleted)
        assertEquals(listOf(5, 6, 7), shownDays(item))
    }

    @Test fun thisAndFutureOnTheFirstOccurrenceRemovesTheWholeCard() {
        val repo = repo(daily())
        val result = repo.deleteRepeatOccurrence(1, at(5), RepeatDeleteScope.THIS_AND_FUTURE, now, zone)
        assertEquals(RepeatDeleteStatus.SERIES_DELETED, result.status)
        assertTrue(repo.load().isEmpty())
    }

    @Test fun thisAndFutureThatRemovesEveryUpcomingDayCompletesTheCardButKeepsHistory() {
        // Series started 1 Oct, next pending alarm is 5 Oct; cutting from 5 Oct leaves only past days.
        val repo = repo(daily(anchorDay = 1, alertDay = 5))
        val result = repo.deleteRepeatOccurrence(1, at(5), RepeatDeleteScope.THIS_AND_FUTURE, now, zone)
        assertEquals(RepeatDeleteStatus.SERIES_ENDED, result.status)
        val item = repo.load().single()
        assertTrue(item.isCompleted)
        assertEquals(epochDay(4), item.repeatUntilEpochDay)
        assertEquals(listOf(1, 2, 3, 4), shownDays(item))
    }

    @Test fun deletingTheOnlySurvivingDayDeletesTheCard() {
        val single = daily().copy(repeatUntilEpochDay = epochDay(5))
        val repo = repo(single)
        val result = repo.deleteRepeatOccurrence(1, at(5), RepeatDeleteScope.THIS_OCCURRENCE, now, zone)
        assertEquals(RepeatDeleteStatus.SERIES_DELETED, result.status)
        assertTrue(repo.load().isEmpty())
    }

    @Test fun weekdayPatternShowsEveryChosenDayAndDeletesOneOfThem() {
        val mask = weekdaysToMask(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val item = daily().copy(repeatRule = RecurrenceRule.WEEKLY, repeatWeekdaysMask = mask) // 5 Oct = Monday
        assertEquals(listOf(5, 7, 9), shownDays(item, 5, 12))
        val repo = repo(item)
        repo.deleteRepeatOccurrence(1, at(7), RepeatDeleteScope.THIS_OCCURRENCE, now, zone)
        assertEquals(listOf(5, 9, 12), shownDays(repo.load().single(), 5, 13))
    }

    @Test fun existingEndDateWinsWhenItIsEarlierThanTheCut() {
        val repo = repo(daily().copy(repeatUntilEpochDay = epochDay(9)))
        repo.deleteRepeatOccurrence(1, at(12), RepeatDeleteScope.THIS_AND_FUTURE, now, zone)
        assertEquals(epochDay(9), repo.load().single().repeatUntilEpochDay)
    }

    @Test fun nonRepeatingCardIsLeftAloneForTheCallerToDelete() {
        val plain = ReminderItem(id = 1, text = "Tek", createdAt = at(1), alertAt = at(5))
        val repo = repo(plain)
        val result = repo.deleteRepeatOccurrence(1, at(5), RepeatDeleteScope.THIS_OCCURRENCE, now, zone)
        assertEquals(RepeatDeleteStatus.NOT_REPEATING, result.status)
        assertEquals(1, repo.load().size)
        assertEquals(RepeatDeleteStatus.NOT_FOUND,
            repo.deleteRepeatOccurrence(99, at(5), RepeatDeleteScope.THIS_OCCURRENCE, now, zone).status)
    }

    @Test fun skippedDaysSurviveSaveAndReload() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        repo.restore(listOf(daily()))
        repo.deleteRepeatOccurrence(1, at(7), RepeatDeleteScope.THIS_OCCURRENCE, now, zone)
        repo.deleteRepeatOccurrence(1, at(9), RepeatDeleteScope.THIS_OCCURRENCE, now, zone)
        val reloaded = ReminderRepository(prefs).load().single()
        assertEquals(listOf(epochDay(7), epochDay(9)), reloaded.repeatSkippedEpochDays)
    }

    // ---- preservation / rebase rule -------------------------------------------------------------

    private fun futureDaily(skippedOffsets: List<Long>): ReminderItem {
        val zoneNow = ZoneId.systemDefault()
        val base = java.time.LocalDate.now(zoneNow).plusDays(2)
        val anchor = base.atTime(9, 0).atZone(zoneNow).toInstant().toEpochMilli()
        return ReminderItem(
            id = 1, text = "Spor", createdAt = System.currentTimeMillis(), alertAt = anchor,
            anchorAlertAt = anchor, repeatRule = RecurrenceRule.DAILY,
            repeatSkippedEpochDays = skippedOffsets.map { base.toEpochDay() + it }
        )
    }

    @Test fun setAlertWithSamePatternPreservesDeletedDays() {
        val item = futureDaily(listOf(3L, 5L))
        val repo = repo(item)
        val newTime = item.alertAt!! + 3_600_000L // same day, one hour later; still DAILY
        repo.setAlert(1, newTime, RecurrenceRule.DAILY)
        assertEquals(item.repeatSkippedEpochDays, repo.load().single().repeatSkippedEpochDays)
    }

    @Test fun setAlertToDifferentRuleClearsDeletedDays() {
        val item = futureDaily(listOf(3L))
        val repo = repo(item)
        repo.setAlert(1, item.alertAt!!, RecurrenceRule.WEEKLY)
        assertTrue(repo.load().single().repeatSkippedEpochDays.isEmpty())
    }

    @Test fun movingTheAlertOntoADeletedDayUnskipsOnlyThatDay() {
        val item = futureDaily(listOf(3L, 5L))
        val repo = repo(item)
        repo.setAlert(1, item.alertAt!! + 3 * 86_400_000L, RecurrenceRule.DAILY)
        assertEquals(listOf(item.repeatSkippedEpochDays[1]), repo.load().single().repeatSkippedEpochDays)
    }

    @Test fun contentEditsNeverResurrectDeletedDays() {
        val item = futureDaily(listOf(3L))
        val edited = item.copy(text = "Yeni başlık", customCategoryId = 7L, isImportant = true,
            isChecklist = true, sendImmediateNotification = false, accentStart = 1)
        assertEquals(item.repeatSkippedEpochDays, rebaseSkippedDays(item, edited))
    }

    @Test fun timeOfDayAndEndDateChangesKeepDeletedDays() {
        val item = futureDaily(listOf(3L))
        val later = item.copy(alertAt = item.alertAt!! + 7_200_000L, anchorAlertAt = item.anchorAlertAt!! + 7_200_000L,
            repeatUntilEpochDay = item.repeatSkippedEpochDays.single() + 30)
        assertEquals(item.repeatSkippedEpochDays, rebaseSkippedDays(item, later))
    }

    @Test fun scheduleSemanticsChangesDropDeletedDays() {
        val item = futureDaily(listOf(3L))
        val weekly = item.copy(repeatRule = RecurrenceRule.WEEKLY)
        assertTrue(rebaseSkippedDays(item, weekly).isEmpty())
        val masked = weekly.copy(repeatWeekdaysMask = weekdaysToMask(listOf(DayOfWeek.MONDAY)))
        assertTrue(rebaseSkippedDays(weekly, masked).isEmpty())
        val otherMask = masked.copy(repeatWeekdaysMask = weekdaysToMask(listOf(DayOfWeek.TUESDAY)))
        val withSkips = masked.copy(repeatSkippedEpochDays = item.repeatSkippedEpochDays)
        assertTrue(rebaseSkippedDays(withSkips, otherMask).isEmpty())
        val stopped = item.copy(repeatRule = RecurrenceRule.NONE)
        assertTrue(rebaseSkippedDays(item, stopped).isEmpty())
    }

    // ---- voice skip uses the same exception model ------------------------------------------------

    @Test fun voiceSkipWritesSkippedDayAndAdvancesTheAlarm() {
        val repo = repo(daily())
        val result = repo.skipNextOccurrence(1, now, zone = zone)
        assertEquals(SkipStatus.SKIPPED, result.status)
        val item = repo.load().single()
        assertEquals(listOf(epochDay(5)), item.repeatSkippedEpochDays)
        assertEquals(at(6), item.alertAt)
        assertEquals(at(6), result.nextAlertAt)
        assertEquals(at(5), item.anchorAlertAt)
        assertEquals(listOf(6, 7), shownDays(item, 5, 8))
    }

    @Test fun voiceSkipTwiceSkipsConsecutiveOccurrencesAndRespectsWeekdays() {
        val mask = weekdaysToMask(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val repo = repo(daily().copy(repeatRule = RecurrenceRule.WEEKLY, repeatWeekdaysMask = mask)) // 5 Oct = Mon
        repo.skipNextOccurrence(1, now, zone = zone)
        assertEquals(at(7), repo.load().single().alertAt)
        repo.skipNextOccurrence(1, now, zone = zone)
        val item = repo.load().single()
        assertEquals(listOf(epochDay(5), epochDay(7)), item.repeatSkippedEpochDays)
        assertEquals(at(12), item.alertAt)
    }

    @Test fun voiceSkipOfAnExplicitLaterOccurrenceLeavesTheAlarmAlone() {
        val repo = repo(daily())
        val result = repo.skipNextOccurrence(1, now, occurrenceMillis = at(8), zone = zone)
        assertEquals(SkipStatus.SKIPPED, result.status)
        val item = repo.load().single()
        assertEquals(listOf(epochDay(8)), item.repeatSkippedEpochDays)
        assertEquals(at(5), item.alertAt)
    }

    @Test fun voiceSkipOfTheLastOccurrenceKeepsTheTerminalCompletedBehaviour() {
        val repo = repo(daily().copy(repeatUntilEpochDay = epochDay(5)))
        val result = repo.skipNextOccurrence(1, now, zone = zone)
        assertEquals(SkipStatus.SERIES_ENDED, result.status)
        val item = repo.load().single()
        assertTrue(item.isCompleted)
        assertEquals(listOf(epochDay(5)), item.repeatSkippedEpochDays)
    }

    // ---- idempotency -----------------------------------------------------------------------------

    @Test fun skippingTheSameDayAgainIsHarmless() {
        val repo = repo(daily())
        repo.deleteRepeatOccurrence(1, at(7), RepeatDeleteScope.THIS_OCCURRENCE, now, zone)
        val once = repo.load().single()
        val again = repo.deleteRepeatOccurrence(1, at(7), RepeatDeleteScope.THIS_OCCURRENCE, now, zone)
        assertEquals(RepeatDeleteStatus.OCCURRENCE_REMOVED, again.status)
        assertEquals(once.repeatSkippedEpochDays, repo.load().single().repeatSkippedEpochDays)
        assertEquals(1, repo.load().single().repeatSkippedEpochDays.size)
    }

    @Test fun duplicateAndUnsortedStoredDaysAreNormalisedOnReload() {
        val encoded = ReminderJson.encode(listOf(daily().copy(
            repeatSkippedEpochDays = listOf(epochDay(9), epochDay(7), epochDay(9)))))
        val written = org.json.JSONArray(encoded).getJSONObject(0).getJSONArray("repeatSkippedEpochDays")
        assertEquals(listOf(epochDay(7), epochDay(9)), (0 until written.length()).map { written.getLong(it) })
        assertEquals(listOf(epochDay(7), epochDay(9)), ReminderJson.decode(encoded).single().repeatSkippedEpochDays)
    }

    // ---- "this and future" edge cases ------------------------------------------------------------

    @Test fun thisAndFutureOnAWeekdayPatternCutsAtTheDayBeforeEvenIfNotAnOccurrence() {
        val mask = weekdaysToMask(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val repo = repo(daily().copy(repeatRule = RecurrenceRule.WEEKLY, repeatWeekdaysMask = mask)) // 5 Oct = Mon
        repo.deleteRepeatOccurrence(1, at(9), RepeatDeleteScope.THIS_AND_FUTURE, now, zone) // Friday
        val item = repo.load().single()
        assertEquals(epochDay(8), item.repeatUntilEpochDay) // Thursday, not an occurrence
        assertEquals(listOf(5, 7), shownDays(item, 5, 20))
    }

    // ---- voice skip with an explicit day ---------------------------------------------------------

    @Test fun voiceSkipOfAWeekdayTheSeriesNeverLandsOnIsRejected() {
        val mask = weekdaysToMask(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val repo = repo(daily().copy(repeatRule = RecurrenceRule.WEEKLY, repeatWeekdaysMask = mask)) // 5 Oct = Mon
        val result = repo.skipNextOccurrence(1, now, occurrenceMillis = at(6), zone = zone) // Tuesday
        assertEquals(SkipStatus.NO_SUCH_OCCURRENCE, result.status)
        val item = repo.load().single()
        assertTrue(item.repeatSkippedEpochDays.isEmpty())
        assertEquals(at(5), item.alertAt)
    }

    @Test fun voiceSkipOfARealLaterWeekdayLeavesTheAlarmAlone() {
        val mask = weekdaysToMask(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val repo = repo(daily().copy(repeatRule = RecurrenceRule.WEEKLY, repeatWeekdaysMask = mask))
        val result = repo.skipNextOccurrence(1, now, occurrenceMillis = at(7), zone = zone) // Wednesday
        assertEquals(SkipStatus.SKIPPED, result.status)
        val item = repo.load().single()
        assertEquals(listOf(epochDay(7)), item.repeatSkippedEpochDays)
        assertEquals(at(5), item.alertAt)
        assertEquals(listOf(5, 12), shownDays(item, 5, 13))
    }

    @Test fun voiceSkipOfADayThatAlreadyPassedIsRejected() {
        val repo = repo(daily(anchorDay = 2, alertDay = 5))
        val result = repo.skipNextOccurrence(1, now, occurrenceMillis = at(3), zone = zone)
        assertEquals(SkipStatus.NO_SUCH_OCCURRENCE, result.status)
        assertTrue(repo.load().single().repeatSkippedEpochDays.isEmpty())
    }

    @Test fun voiceSkipOfADayAfterTheEndDateIsRejected() {
        val repo = repo(daily().copy(repeatUntilEpochDay = epochDay(6)))
        val result = repo.skipNextOccurrence(1, now, occurrenceMillis = at(9), zone = zone)
        assertEquals(SkipStatus.NO_SUCH_OCCURRENCE, result.status)
        assertTrue(repo.load().single().repeatSkippedEpochDays.isEmpty())
    }

    @Test fun voiceSkipOfAnAlreadySkippedRealDayIsHarmless() {
        val repo = repo(daily())
        repo.skipNextOccurrence(1, now, occurrenceMillis = at(8), zone = zone)
        val again = repo.skipNextOccurrence(1, now, occurrenceMillis = at(8), zone = zone)
        assertEquals(SkipStatus.SKIPPED, again.status)
        assertEquals(listOf(epochDay(8)), repo.load().single().repeatSkippedEpochDays)
    }

    @Test fun isSeriesOccurrenceDayMatchesThePatternRegardlessOfDeletions() {
        val mask = weekdaysToMask(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val item = daily().copy(repeatRule = RecurrenceRule.WEEKLY, repeatWeekdaysMask = mask, repeatSkippedEpochDays = listOf(epochDay(7)))
        assertTrue(isSeriesOccurrenceDay(item, epochDay(5), zone))
        assertTrue(isSeriesOccurrenceDay(item, epochDay(7), zone))
        assertFalse(isSeriesOccurrenceDay(item, epochDay(6), zone))
        assertFalse(isSeriesOccurrenceDay(item, epochDay(4), zone))
        assertFalse(isSeriesOccurrenceDay(item.copy(repeatRule = RecurrenceRule.NONE), epochDay(5), zone))
    }


    // ---- Card Detail from Home / Category: next surviving occurrence ------------------------------

    @Test fun nextDeletableOccurrenceIsTheAnchorWhileItIsStillAhead() {
        assertEquals(at(5), nextDeletableOccurrence(daily(), now, zone))
    }

    @Test fun nextDeletableOccurrenceSkipsAPassedAnchor() {
        val item = daily(anchorDay = 1, alertDay = 5)
        assertEquals(at(5), nextDeletableOccurrence(item, now, zone))
    }

    @Test fun nextDeletableOccurrenceSkipsDeletedDaysAndTodaysPassedTime() {
        val item = daily(anchorDay = 1, alertDay = 5).copy(repeatSkippedEpochDays = listOf(epochDay(5), epochDay(6)))
        assertEquals(at(7), nextDeletableOccurrence(item, now, zone))
        assertEquals(at(5), nextDeletableOccurrence(daily(anchorDay = 1, alertDay = 5), at(4, 12), zone)) // 4 Oct 09:00 is gone, but 5 Oct is next
    }

    @Test fun nextDeletableOccurrenceDoesNotUseADeletedAnchor() {
        val item = daily().copy(repeatSkippedEpochDays = listOf(epochDay(5)))
        assertEquals(at(6), nextDeletableOccurrence(item, now, zone))
    }

    @Test fun nextDeletableOccurrenceRespectsWeekdaySetAndEndDate() {
        val mask = weekdaysToMask(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val weekly = daily(anchorDay = 5).copy(repeatRule = RecurrenceRule.WEEKLY, repeatWeekdaysMask = mask)
        assertEquals(at(12), nextDeletableOccurrence(weekly, at(8), zone)) // after Wed 7, next Monday 12
        assertNull(nextDeletableOccurrence(weekly.copy(repeatUntilEpochDay = epochDay(7)), at(8), zone))
    }

    @Test fun nextDeletableOccurrenceIsNullForNonRepeatingCards() {
        assertNull(nextDeletableOccurrence(daily().copy(repeatRule = RecurrenceRule.NONE), now, zone))
    }

    @Test fun homeDeleteOfOnlyTheNextOccurrenceUsesTheSameOccurrenceAwareDelete() {
        val repo = repo(daily(anchorDay = 1, alertDay = 5))
        val target = nextDeletableOccurrence(repo.load().single(), now, zone)!!
        repo.deleteRepeatOccurrence(1, target, RepeatDeleteScope.THIS_OCCURRENCE, now, zone)
        val item = repo.load().single()
        assertEquals(listOf(epochDay(5)), item.repeatSkippedEpochDays)
        assertEquals(at(6), item.alertAt)
        assertEquals(listOf(1, 2, 3, 4, 6), shownDays(item, 1, 7))
    }

    @Test fun homeDeleteOfThisAndFutureCutsFromTheNextOccurrenceAndKeepsThePast() {
        val repo = repo(daily(anchorDay = 1, alertDay = 5))
        val target = nextDeletableOccurrence(repo.load().single(), now, zone)!!
        repo.deleteRepeatOccurrence(1, target, RepeatDeleteScope.THIS_AND_FUTURE, now, zone)
        val item = repo.load().single()
        assertEquals(epochDay(4), item.repeatUntilEpochDay)
        assertEquals(listOf(1, 2, 3, 4), shownDays(item, 1, 10))
    }

    @Test fun homeDeleteOfThisAndFutureOnTheFirstOccurrenceRemovesTheWholeCard() {
        val repo = repo(daily())
        val target = nextDeletableOccurrence(repo.load().single(), now, zone)!!
        val result = repo.deleteRepeatOccurrence(1, target, RepeatDeleteScope.THIS_AND_FUTURE, now, zone)
        assertEquals(RepeatDeleteStatus.SERIES_DELETED, result.status)
        assertTrue(repo.load().isEmpty())
    }
}
