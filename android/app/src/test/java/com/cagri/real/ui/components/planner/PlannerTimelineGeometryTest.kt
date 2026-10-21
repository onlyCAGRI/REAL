package com.cagri.real.ui.components.planner

import org.junit.Assert.*
import org.junit.Test

class PlannerTimelineGeometryTest {
    @Test fun exactMinuteGeometryForApprovedExample() {
        val clock = PlannerTimelineGeometry(listOf(PlannerTimeInterval("music", 1100f, 1230f)))
        assertEquals(24f, clock.x(1100f) - clock.x(1080f), 0.001f)
        assertEquals(36f, clock.x(1230f) - clock.x(1200f), 0.001f)
        assertEquals(156f, clock.x(1230f) - clock.x(1100f), 0.001f)
        assertEquals(96f, clock.x(1020f) - clock.x(540f), 0.001f)
        assertEquals(1100f, clock.minuteAt(clock.x(1100f)), 0.001f)
    }

    @Test fun overlapUsesSeparateLanesButTouchingEventsReuseLane() {
        val clock = PlannerTimelineGeometry(listOf(
            PlannerTimeInterval("meeting", 1080f, 1170f),
            PlannerTimeInterval("music", 1100f, 1230f),
            PlannerTimeInterval("next", 1170f, 1200f)
        ))
        val byKey = clock.placements.associateBy { it.interval.key }
        assertNotEquals(byKey.getValue("meeting").lane, byKey.getValue("music").lane)
        assertEquals(byKey.getValue("meeting").lane, byKey.getValue("next").lane)
        assertEquals(2, clock.laneCount)
    }

    @Test fun shortEventDoesNotGrowToFitTitleAndExactEndDoesNotExpandNextHour() {
        val clock = PlannerTimelineGeometry(listOf(PlannerTimeInterval("short", 740f, 750f)))
        assertEquals(12f, clock.x(750f) - clock.x(740f), 0.001f)
        val full = PlannerTimelineGeometry(listOf(PlannerTimeInterval("hour", 720f, 780f)))
        assertEquals(PlannerCompactHourDp, full.hourWidths[13], 0f)
        assertEquals(PlannerExpandedHourDp, full.hourWidths[12], 0f)
    }

    @Test fun overnightPortionIsClippedAndEmptyDayStaysCompact() {
        val clock = PlannerTimelineGeometry(listOf(PlannerTimeInterval("overnight", 1410f, 1470f)))
        assertEquals(1440f, clock.placements.single().interval.endMinute, 0f)
        assertEquals(36f, clock.x(1440f) - clock.x(1410f), 0.001f)
        assertEquals(288f, PlannerTimelineGeometry(emptyList()).width, 0.001f)
    }

    @Test fun daylightSavingDaysRetainTheirActualHourCount() {
        assertEquals(23, PlannerTimelineGeometry(emptyList(), 1380).hourWidths.size)
        val longDay = PlannerTimelineGeometry(listOf(PlannerTimeInterval("last", 1440f, 1500f)), 1500)
        assertEquals(25, longDay.hourWidths.size)
        assertEquals(72f, longDay.x(1500f) - longDay.x(1440f), 0.001f)
        assertEquals(1500f, longDay.minuteAt(longDay.width), 0f)
    }

    @Test fun adaptiveRangeExampleMatchesReference() {
        val clock = PlannerTimelineGeometry(
            intervals = listOf(PlannerTimeInterval("music", 1100f, 1230f)),
            startHour = 17,
            endHour = 22,
            compactHourDp = 28f,
            expandedHourDp = 76f
        )
        assertEquals(28f, clock.hourWidths[0], 0f)
        assertEquals(76f, clock.hourWidths[1], 0f)
        assertEquals(76f, clock.hourWidths[2], 0f)
        assertEquals(76f, clock.hourWidths[3], 0f)
        assertEquals(28f, clock.hourWidths[4], 0f)

        val startX = clock.x(1100f)
        assertEquals(28f + 76f / 3f, startX, 0.01f)

        val endX = clock.x(1230f)
        assertEquals(28f + 76f + 76f + 38f, endX, 0.01f)

        assertEquals(endX - startX, clock.x(1230f) - clock.x(1100f), 0.001f)
        assertEquals(1100f, clock.minuteAt(startX), 0.01f)
        assertEquals(1230f, clock.minuteAt(endX), 0.01f)
    }
}
