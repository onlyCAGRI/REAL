package com.cagri.real.ui.components.planner

import com.cagri.real.FakeSharedPreferences
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlannerWeekHistoryTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun formatWeekTitle_returnsCorrectLabels() {
        val currentMonday = LocalDate.of(2026, 10, 5)
        assertEquals("This week", PlannerWeekHistoryStore.formatWeekTitle(currentMonday, currentMonday))
        assertEquals("Last week", PlannerWeekHistoryStore.formatWeekTitle(currentMonday.minusWeeks(1), currentMonday))
        assertEquals("Sep 21 – 27", PlannerWeekHistoryStore.formatWeekTitle(currentMonday.minusWeeks(2), currentMonday))

        val augCrossMonday = LocalDate.of(2026, 8, 31)
        assertEquals("Aug 31 – Sep 6", PlannerWeekHistoryStore.formatWeekTitle(augCrossMonday, currentMonday))

        val yearCrossMonday = LocalDate.of(2025, 12, 29)
        assertEquals("Dec 29, 2025 – Jan 4, 2026", PlannerWeekHistoryStore.formatWeekTitle(yearCrossMonday, currentMonday))
    }

    @Test
    fun loadOrCreateWeekHistory_producesAtLeastThreeWeeksEndingWithCurrentWeek() {
        val prefs = FakeSharedPreferences()
        val category = CustomCategory(id = 101L, name = "Fitness", iconKey = "fitness", accentStart = 0)
        val today = LocalDate.of(2026, 10, 7) // Wednesday

        val history = PlannerWeekHistoryStore.loadOrCreateWeekHistory(
            context = null,
            category = category,
            members = emptyList(),
            today = today,
            zone = zone,
            customPrefs = prefs
        )

        assertTrue(history.size >= 3)
        // Last week is current week
        val currentWeek = history.last()
        assertTrue(currentWeek.isCurrentWeek)
        assertEquals("This week", currentWeek.title)
        assertEquals(2, currentWeek.todayIndex) // Wednesday is index 2

        // Second to last is Last week
        val lastWeek = history[history.size - 2]
        assertFalse(lastWeek.isCurrentWeek)
        assertEquals("Last week", lastWeek.title)
        assertEquals(-1, lastWeek.todayIndex)

        // No future weeks
        val currentMonday = today.minusDays(2)
        assertEquals(currentMonday.toString(), currentWeek.weekKey)
    }

    @Test
    fun loadOrCreateWeekHistory_preservesFrozenHistoryAcrossUpdates() {
        val prefs = FakeSharedPreferences()
        val category = CustomCategory(id = 202L, name = "Learning", iconKey = "book", accentStart = 1)
        val pastMonday = LocalDate.of(2026, 9, 28) // Last week
        val currentMonday = LocalDate.of(2026, 10, 5)
        val today = LocalDate.of(2026, 10, 6)

        // Pre-populate frozen history for last week in SharedPreferences
        prefs.edit().putString("cat_202_week_$pastMonday", "1,2,0,3,1,0,0").apply()

        val history = PlannerWeekHistoryStore.loadOrCreateWeekHistory(
            context = null,
            category = category,
            members = emptyList(),
            today = today,
            zone = zone,
            customPrefs = prefs
        )

        val pastWeekUi = history.firstOrNull { it.weekKey == pastMonday.toString() }
        org.junit.Assert.assertNotNull(pastWeekUi)
        assertEquals(listOf(1, 2, 0, 3, 1, 0, 0), pastWeekUi!!.activity)
        assertEquals(7, pastWeekUi.doneCount)
        assertEquals("Last week", pastWeekUi.title)

        // Now suppose all reminders are cleared or modified - past week must stay frozen
        val modifiedMembers = listOf(
            ReminderItem(
                id = 999L,
                text = "New task",
                createdAt = currentMonday.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                customCategoryId = category.id,
                completedAt = currentMonday.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            )
        )

        val updatedHistory = PlannerWeekHistoryStore.loadOrCreateWeekHistory(
            context = null,
            category = category,
            members = modifiedMembers,
            today = today,
            zone = zone,
            customPrefs = prefs
        )

        val pastWeekAfterUpdate = updatedHistory.firstOrNull { it.weekKey == pastMonday.toString() }
        org.junit.Assert.assertNotNull(pastWeekAfterUpdate)
        assertEquals(listOf(1, 2, 0, 3, 1, 0, 0), pastWeekAfterUpdate!!.activity)
        assertEquals(7, pastWeekAfterUpdate.doneCount)

        // Current week reflected the new completion
        val currentWeekAfterUpdate = updatedHistory.last()
        assertEquals(1, currentWeekAfterUpdate.doneCount)
    }

    @Test
    fun loadOrCreateWeekHistory_automaticallyCreatesNewWeekWhenWeekAdvances() {
        val prefs = FakeSharedPreferences()
        val category = CustomCategory(id = 303L, name = "Work", iconKey = "briefcase", accentStart = 2)
        val week1Today = LocalDate.of(2026, 10, 7) // Week A Wednesday

        val initialHistory = PlannerWeekHistoryStore.loadOrCreateWeekHistory(
            context = null,
            category = category,
            members = emptyList(),
            today = week1Today,
            zone = zone,
            customPrefs = prefs
        )

        val initialCount = initialHistory.size
        assertEquals("This week", initialHistory.last().title)

        // Advance by 7 days to next Wednesday
        val nextWeekToday = week1Today.plusWeeks(1)
        val advancedHistory = PlannerWeekHistoryStore.loadOrCreateWeekHistory(
            context = null,
            category = category,
            members = emptyList(),
            today = nextWeekToday,
            zone = zone,
            customPrefs = prefs
        )

        assertEquals(initialCount + 1, advancedHistory.size)
        assertEquals("This week", advancedHistory.last().title)
        assertEquals("Last week", advancedHistory[advancedHistory.size - 2].title)
    }
}
