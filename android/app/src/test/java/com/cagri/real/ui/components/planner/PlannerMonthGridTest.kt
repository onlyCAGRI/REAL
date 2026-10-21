package com.cagri.real.ui.components.planner

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerMonthGridTest {
    /** Glass chrome sits above every month layout; 5- and 6-week months must both produce whole weeks. */
    @Test
    fun everyMonthProducesWholeWeeksAndContainsAllDays() {
        var month = YearMonth.of(2024, 1)
        repeat(12 * 6) {
            val cells = plannerMonthDays(month)
            assertEquals("$month", 0, cells.size % 7)
            assertTrue("$month", cells.size in 28..42)
            assertEquals("$month", month.lengthOfMonth(), cells.count { YearMonth.from(it) == month })
            assertEquals("$month", java.time.DayOfWeek.MONDAY, cells.first().dayOfWeek)
            month = month.plusMonths(1)
        }
    }
}
