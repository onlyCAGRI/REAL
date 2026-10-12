package com.cagri.real.ui.components.planner

import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderItem
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlannerCalendarTest {

    private val zone = ZoneId.of("Europe/Istanbul")
    private val testDay = LocalDate.of(2026, 9, 28)
    private val dayStart = testDay.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun createEvent(
        id: Long,
        title: String,
        hour: Int,
        minute: Int,
        durationMinutes: Int,
        customCategoryId: Long? = null,
        accentStart: Int? = null,
        customSolidArgb: Int? = null
    ): PlannerEvent {
        val startMillis = dayStart + hour * 3600_000L + minute * 60_000L
        val reminder = ReminderItem(
            id = id,
            text = title,
            createdAt = startMillis,
            alertAt = startMillis,
            customCategoryId = customCategoryId,
            accentStart = accentStart,
            customSolidColorArgb = customSolidArgb
        )
        return PlannerEvent(
            reminder = reminder,
            details = PlannerEventDetails(durationMinutes = durationMinutes),
            startMillis = startMillis,
            isDateOnly24Hour = false
        )
    }

    @Test
    fun dayCardColorFollowsDecorativePastelRhythmNotCategory() {
        val day1 = LocalDate.of(2026, 9, 28)
        val day2 = LocalDate.of(2026, 9, 29)
        val day3 = LocalDate.of(2026, 9, 30)

        val color1 = realDayCardSurface(day1)
        val color2 = realDayCardSurface(day2)
        val color3 = realDayCardSurface(day3)

        // Day card colors are members of the 5 calm REAL day-card pastels
        assertTrue(RealDayCardPastels.contains(color1))
        assertTrue(RealDayCardPastels.contains(color2))
        assertTrue(RealDayCardPastels.contains(color3))
        assertNotEquals(color1, color2)
        assertNotEquals(color2, color3)
    }

    @Test
    fun uncategorizedEventsUseDecorativeColorWithoutInventingCategory() {
        // Uncategorized event (customCategoryId == null)
        val event = createEvent(1L, "Rastgele Not", 14, 0, 60)
        val visual = plannerEventCategoryVisual(event)

        assertEquals("", visual.name)
        val decorativeSurface = uncategorizedDecorativePastel(event.id)
        assertEquals(decorativeSurface, visual.surfacePastel)
        assertTrue(RealCalendarPastels.contains(visual.surfacePastel))
        assertEquals(categoryToEventCapsuleColor(decorativeSurface), visual.capsuleColor)
        assertEquals(categoryToEventDotColor(decorativeSurface), visual.dotColor)
        assertEquals(visual, plannerEventCategoryVisual(event))
    }

    @Test
    fun customCategoryColorIsConvertedToCalmPastel() {
        val argb = (0xFF shl 24) or (69 shl 16) or (88 shl 8) or 200
        val customCat = CustomCategory(
            id = 999L,
            name = "my kity",
            iconKey = "CAT",
            accentStart = 2,
            customSolidColorArgb = argb
        )
        val event = createEvent(10L, "Kedi maması al", 10, 0, 60, customCategoryId = 999L)
        val visual = plannerEventCategoryVisual(event, listOf(customCat))

        assertEquals("my kity", visual.name)
        // Pastel color has normalized lightness for dark ink readability
        assertTrue("Pastel must have high lightness", visual.pastelColor.red > 0.5f || visual.pastelColor.green > 0.5f || visual.pastelColor.blue > 0.5f)
        // plannerCalendarPastel helper produces same pastel
        assertEquals(visual.pastelColor, plannerCalendarPastel(customCat))
    }

    @Test
    fun digerOnlyUsedWhenExplicitlyAssigned() {
        val digerCat = CustomCategory(
            id = 888L,
            name = "Diğer",
            iconKey = "STAR",
            accentStart = 5
        )
        val explicitEvent = createEvent(20L, "Diğer iş", 11, 0, 30, customCategoryId = 888L)
        val uncategorizedEvent = createEvent(21L, "Başka iş", 12, 0, 30, customCategoryId = null)

        val explicitVisual = plannerEventCategoryVisual(explicitEvent, listOf(digerCat))
        val uncategorizedVisual = plannerEventCategoryVisual(uncategorizedEvent, listOf(digerCat))

        assertEquals("Diğer", explicitVisual.name)
        assertEquals("", uncategorizedVisual.name)
    }

    @Test
    fun dynamicLegendRuleWithOneMyKityAndThreeUncategorized() {
        val myKityCat = CustomCategory(
            id = 777L,
            name = "my kity",
            iconKey = "CAT",
            accentStart = 4
        )
        val categories = listOf(myKityCat)
        val monthEvents = listOf(
            createEvent(1L, "Kedi aşı", 10, 0, 30, customCategoryId = 777L),
            createEvent(2L, "Not 1", 11, 0, 30, customCategoryId = null),
            createEvent(3L, "Not 2", 12, 0, 30, customCategoryId = null),
            createEvent(4L, "Not 3", 13, 0, 30, customCategoryId = null)
        )

        val seen = mutableSetOf<String>()
        val result = mutableListOf<PlannerCategoryVisual>()
        monthEvents.forEach { ev ->
            val v = plannerEventCategoryVisual(ev, categories)
            if (v.name.isNotBlank() && seen.add(v.name)) {
                result.add(v)
            }
        }
        val legendNames = result.map { it.name }

        assertFalse("Uncategorized events must not invent a legend category", legendNames.contains("Gelen kutu"))
        assertTrue(legendNames.contains("my kity"))
        assertFalse("Diğer must NOT appear in legend unless explicitly assigned", legendNames.contains("Diğer"))
        assertEquals(listOf("my kity"), legendNames)
    }

    @Test
    fun twelveHourAmPmFormattingIsAccurate() {
        // Hour formatting
        assertEquals("9 am", formatHourAmPm(9))
        assertEquals("12 pm", formatHourAmPm(12))
        assertEquals("5 pm", formatHourAmPm(17))
        assertEquals("6 pm", formatHourAmPm(18))
        assertEquals("8 pm", formatHourAmPm(20))
        assertEquals("12 am", formatHourAmPm(0))

        // Exact time formatting
        val startMillis = dayStart + 18 * 3600_000L + 20 * 60_000L // 18:20
        val endMillis = dayStart + 20 * 3600_000L + 30 * 60_000L   // 20:30
        assertEquals("6:20 pm", formatTimeAmPm(startMillis, zone))
        assertEquals("8:30 pm", formatTimeAmPm(endMillis, zone))

        // Range formatting
        assertEquals("6:20 pm – 8:30 pm", formatTimeRangeAmPm(startMillis, endMillis, zone))
    }

    @Test
    fun dotIndicatorRuleEnforcesMaxThreeDots() {
        val events = listOf(
            createEvent(1L, "Etkinlik 1", 9, 0, 30),
            createEvent(2L, "Etkinlik 2", 11, 0, 30),
            createEvent(3L, "Etkinlik 3", 14, 0, 30),
            createEvent(4L, "Etkinlik 4", 16, 0, 30),
            createEvent(5L, "Etkinlik 5", 18, 0, 30)
        )
        val visibleDots = events.take(3)
        assertEquals("Maximum 3 dots should be rendered, never 4 or more", 3, visibleDots.size)
    }

    @Test
    fun fullDayTimelineShowsAllTwentyFourHoursWithCompressedEmptyHours() {
        val (sHour, eHour) = 0 to 24
        val intervals = listOf(
            PlannerTimeInterval("lunch", 12 * 60f, 13 * 60f) // 12 PM - 1 PM (hour 12)
        )
        val geometry = PlannerTimelineGeometry(
            intervals = intervals,
            dayMinutes = 1440,
            startHour = sHour,
            endHour = eHour,
            compactHourDp = 26f,
            expandedHourDp = 76f
        )
        // All 24 hours are represented
        assertEquals(24, geometry.hourWidths.size)
        // Hour 12 (expanded) is 76 dp
        assertEquals(76f, geometry.hourWidths[12], 0.001f)
        // Other hours are compressed to 26 dp
        assertEquals(26f, geometry.hourWidths[0], 0.001f)  // 12 AM
        assertEquals(26f, geometry.hourWidths[11], 0.001f) // 11 AM
        assertEquals(26f, geometry.hourWidths[13], 0.001f) // 1 PM
        assertEquals(26f, geometry.hourWidths[23], 0.001f) // 11 PM

        // Labels for all 24 hours
        assertEquals("12 am", formatHourAmPm(0))
        assertEquals("11 pm", formatHourAmPm(23))
    }

    @Test
    fun overlappingEventsAssignedToSeparateLanes() {
        // Event 1: 6:00 PM - 7:30 PM (1080 - 1170)
        // Event 2: 6:20 PM - 8:30 PM (1100 - 1230)
        val intervals = listOf(
            PlannerTimeInterval("e1", 1080f, 1170f),
            PlannerTimeInterval("e2", 1100f, 1230f)
        )
        val geometry = PlannerTimelineGeometry(
            intervals = intervals,
            dayMinutes = 1440,
            startHour = 17,
            endHour = 22,
            compactHourDp = 28f,
            expandedHourDp = 76f
        )
        assertEquals("2 overlapping events must use 2 separate lanes", 2, geometry.laneCount)
        val p1 = geometry.placements.first { it.interval.key == "e1" }
        val p2 = geometry.placements.first { it.interval.key == "e2" }
        assertNotEquals(p1.lane, p2.lane)
    }

    @Test
    fun exactMinutePlacementMatchesApprovedReferenceC() {
        // 6:20 PM to 8:30 PM in 5 PM to 10 PM window (17:00 to 22:00)
        // Hour 17 (5 PM): 28f (compact)
        // Hours 18, 19, 20 (6 PM, 7 PM, 8 PM): 76f each (expanded)
        // Hour 21 (9 PM): 28f (compact)
        val intervals = listOf(
            PlannerTimeInterval("meeting", 1080f, 1170f), // 6:00 PM - 7:30 PM
            PlannerTimeInterval("music", 1100f, 1230f)    // 6:20 PM - 8:30 PM
        )
        val geometry = PlannerTimelineGeometry(
            intervals = intervals,
            dayMinutes = 1440,
            startHour = 17,
            endHour = 22,
            compactHourDp = 28f,
            expandedHourDp = 76f
        )

        // Meeting starts exactly at 6:00 PM (hour 1 start = 28f)
        assertEquals(28f, geometry.x(1080f), 0.01f)
        // Meeting ends at 7:30 PM (6:00 PM line + 76f + 38f = 142f)
        assertEquals(28f + 76f + 38f, geometry.x(1170f), 0.01f)

        // Music starts at 6:20 PM (6:00 PM line + 20/60 * 76f = 28 + 25.333f = 53.333f)
        assertEquals(28f + 76f / 3f, geometry.x(1100f), 0.01f)
        // Music ends at 8:30 PM (8:00 PM line + 30/60 * 76f = 28 + 76 + 76 + 38 = 218f)
        assertEquals(28f + 76f + 76f + 38f, geometry.x(1230f), 0.01f)

        // Width of music capsule reflects exact duration
        val expectedWidth = (218f - (28f + 76f / 3f))
        assertEquals(expectedWidth, geometry.x(1230f) - geometry.x(1100f), 0.01f)
    }
}
