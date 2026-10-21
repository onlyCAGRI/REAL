package com.cagri.real.ui.components.planner

import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.ReminderItem
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class PlannerDailyPresentationTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val day = LocalDate.of(2026, 10, 3)
    private val start = day.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun reminder(id: Long, minute: Int = 480, star: Boolean = true): ReminderItem = ReminderItem(
        id = id, text = "REAL $id", createdAt = start,
        alertAt = start + minute * 60_000L, isImportant = star
    )

    private fun project(reminders: List<ReminderItem>, details: Map<Long, PlannerEventDetails> = emptyMap()) =
        plannerEvents(reminders, details, zone, start - 86_400_000L, start + 2 * 86_400_000L)

    @Test fun newIsDefaultAndUnknownPreferenceFallsBackSafely() {
        assertEquals(PlannerDailyView.New, PlannerDailyView.fromPreference(null))
        assertEquals(PlannerDailyView.New, PlannerDailyView.fromPreference("future"))
        PlannerDailyView.entries.forEach {
            assertEquals(it, PlannerDailyView.fromPreference(it.preference))
        }
    }

    @Test fun newPlusKeepsTheNewDailyRouteAndItsOwnSavedChoice() {
        assertEquals("New+", PlannerDailyView.NewPlus.label)
        assertEquals(PlannerDailyView.NewPlus, PlannerDailyView.fromPreference("new_plus"))
        assertEquals(PlannerDailyView.Classic, PlannerDailyView.fromPreference("classic"))
        assertTrue(PlannerDailyView.New.usesNewLayout)
        assertTrue(PlannerDailyView.NewPlus.usesNewLayout)
        assertFalse(PlannerDailyView.Classic.usesNewLayout)
    }

    @Test fun focusRequiresStarAndAnActualSelectedDate() {
        val inputs = listOf(
            reminder(1), reminder(2, star = false),
            reminder(3).copy(alertAt = null),
            reminder(4).copy(alertAt = null, quick24HourUntilAt = start + 3_600_000L),
            reminder(5).copy(alertAt = start + 86_400_000L),
            reminder(6).copy(alertAt = null, plannedDateEpochDay = day.toEpochDay())
        )
        assertEquals(listOf(6L, 1L), plannerFocusEvents(project(inputs), day, zone).map { it.id })
    }

    @Test fun recurrenceUsesTheExistingOccurrenceProjection() {
        val repeat = reminder(1).copy(repeatRule = RecurrenceRule.DAILY, anchorAlertAt = start - 2 * 86_400_000L + 480 * 60_000L)
        val events = project(listOf(repeat))
        val focus = plannerFocusEvents(events, day, zone)
        assertEquals(1, focus.size)
        assertEquals(day, focus.single().day(zone))
        assertEquals(480f, plannerDailyGeometry(events, day, zone).placements.single().interval.startMinute, 0.001f)
    }

    @Test fun focusCountsRealCardsOnceAndUsesOccurrenceDateInTheCurrentZone() {
        val event = PlannerEvent(reminder(1), null, start)
        assertEquals(1, plannerFocusEvents(listOf(event, event), day, zone).size)
        assertTrue(plannerFocusEvents(listOf(event), day, ZoneId.of("America/New_York")).isEmpty())
        assertEquals(1, plannerFocusEvents(listOf(event), day.minusDays(1), ZoneId.of("America/New_York")).size)
    }

    @Test fun headingHasDynamicCountAndEnglishMonthEvenInTurkishLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("Today’s\nFocus (2)", plannerFocusHeading(day, day, 2))
            assertEquals("Today’s\nFocus", plannerFocusHeading(day, day, 0))
            assertEquals("03 Oct\nFocus (3)", plannerFocusHeading(day, day.minusDays(1), 3))
            assertEquals("18 Nov\nFocus", plannerFocusHeading(LocalDate.of(2026, 11, 18), day, 0))
        } finally { Locale.setDefault(original) }
    }

    @Test fun weekStartsMondayAndCrossesMonthAndYear() {
        val week = plannerFocusWeek(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), week.first())
        assertEquals(LocalDate.of(2027, 1, 3), week.last())
        assertEquals(listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"),
            week.map { it.dayOfWeek.name.take(3) })
    }

    @Test fun dateOnlyCardsDoNotBecomeMidnightTimedBlocks() {
        val event = project(listOf(reminder(1).copy(alertAt = null, plannedDateEpochDay = day.toEpochDay()))).single()
        assertEquals("All day", plannerDailyTime(event, zone))
        assertTrue(plannerDailyGeometry(listOf(event), day, zone).placements.isEmpty())
    }

    @Test fun exactMinutesDurationAndOverlapsUseExistingLanes() {
        val events = project(listOf(reminder(1, 500), reminder(2, 510), reminder(3, 530)),
            mapOf(1L to PlannerEventDetails(30), 2L to PlannerEventDetails(20), 3L to PlannerEventDetails(15)))
        val geometry = plannerDailyGeometry(events, day, zone)
        val first = geometry.placements.first { it.interval.key.startsWith("1:") }
        val second = geometry.placements.first { it.interval.key.startsWith("2:") }
        val third = geometry.placements.first { it.interval.key.startsWith("3:") }
        assertEquals(500f, first.interval.startMinute, 0.001f)
        assertEquals(530f, first.interval.endMinute, 0.001f)
        assertNotEquals(first.lane, second.lane)
        assertEquals(first.lane, third.lane)
        assertEquals(500f, geometry.minuteAt(geometry.x(500f)), 0.001f)
        assertEquals(48f, geometry.x(530f) - geometry.x(500f), 0.001f)
    }

    @Test fun unduratedTimedCardsUseClassicsTwentyMinuteFallback() {
        val geometry = plannerDailyGeometry(project(listOf(reminder(1, 495))), day, zone)
        assertEquals(20f, geometry.placements.single().interval.let { it.endMinute - it.startMinute }, 0.001f)
    }

    @Test fun midnightBoundaryDoesNotDuplicateThePreviousDaysEvent() {
        val previous = reminder(1).copy(alertAt = start - 20 * 60_000L)
        val spanning = reminder(2).copy(alertAt = start - 10 * 60_000L)
        val following = reminder(3).copy(alertAt = start + 86_400_000L)
        val events = project(listOf(previous, spanning, following),
            mapOf(1L to PlannerEventDetails(20), 2L to PlannerEventDetails(30)))
        val geometry = plannerDailyGeometry(events, day, zone)
        assertEquals(1, geometry.placements.size)
        assertEquals(spanning.id.toString(), geometry.placements.single().interval.key.substringBefore(':'))
        assertEquals(0f, geometry.placements.single().interval.startMinute, 0.001f)
        assertEquals(20f, geometry.placements.single().interval.endMinute, 0.001f)
    }

    @Test fun spanningEventsAreClippedWithoutChangingCardDuration() {
        val card = reminder(1, 1430)
        val event = project(listOf(card), mapOf(1L to PlannerEventDetails(90))).single()
        val geometry = plannerDailyGeometry(listOf(event), day, zone)
        assertEquals(1440f, geometry.placements.single().interval.endMinute, 0.001f)
        assertEquals(90, event.details!!.durationMinutes)
        assertEquals(card, event.reminder)
    }

    @Test fun dstDayLengthsFollowTheExistingEpochMinuteGeometry() {
        val berlin = ZoneId.of("Europe/Berlin")
        val shortDay = LocalDate.of(2026, 3, 29)
        val longDay = LocalDate.of(2026, 10, 25)
        assertEquals(1380, plannerDailyGeometry(emptyList(), shortDay, berlin).dayMinutes)
        assertEquals(23, plannerDailyGeometry(emptyList(), shortDay, berlin).endHour)
        assertEquals(1500, plannerDailyGeometry(emptyList(), longDay, berlin).dayMinutes)
        assertEquals(25, plannerDailyGeometry(emptyList(), longDay, berlin).endHour)
    }

    @Test fun timelineTimesRemainMinutePreciseWithoutFakeEndTimes() {
        val event = project(listOf(reminder(1, 500))).single()
        assertEquals("08:20", plannerDailyTime(event, zone))
        assertEquals("08:20 – 08:55", plannerDailyTime(event.copy(details = PlannerEventDetails(35)), zone))
    }

    @Test fun emptyDayStillHasACompleteChronologicalSchedule() {
        val geometry = plannerDailyGeometry(emptyList(), day, zone)
        assertEquals(24, geometry.hourWidths.size)
        assertTrue(geometry.placements.isEmpty())
        assertEquals(0f, geometry.x(0f), 0.001f)
        assertEquals(geometry.width, geometry.x(1440f), 0.001f)
        assertTrue(geometry.boundaries.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun plannerViewModePersistenceRoundtripsAndDefaultsTo24Hours() {
        assertEquals(PlannerViewMode.Hours24, PlannerViewMode.fromPreference(null))
        assertEquals(PlannerViewMode.Hours24, PlannerViewMode.fromPreference("unknown_mode"))
        assertEquals(PlannerViewMode.Hours24, PlannerViewMode.fromPreference("24_hours"))
        assertEquals(PlannerViewMode.Hours24, PlannerViewMode.fromPreference("hours_24"))
        assertEquals(PlannerViewMode.Hours24, PlannerViewMode.fromPreference("Hours24"))
        assertEquals(PlannerViewMode.Daily, PlannerViewMode.fromPreference("daily"))
        assertEquals(PlannerViewMode.Daily, PlannerViewMode.fromPreference("Daily"))
        assertEquals(PlannerViewMode.Monthly, PlannerViewMode.fromPreference("monthly"))
        assertEquals(PlannerViewMode.Monthly, PlannerViewMode.fromPreference("Monthly"))
        PlannerViewMode.entries.forEach {
            assertEquals(it, PlannerViewMode.fromPreference(it.preference))
        }
    }
}

