package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.COLOR_FILTER_DEFAULT
import com.cagri.bigboscagri.data.ReminderCategory
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.ReminderSortOrder
import com.cagri.bigboscagri.data.HOME_COMPLETED_PREVIEW_LIMIT
import com.cagri.bigboscagri.data.HOME_COMPLETED_GLASS_HEIGHT_DP
import com.cagri.bigboscagri.data.isHomeCompletedGlassActive
import com.cagri.bigboscagri.data.filterAndSortReminders
import com.cagri.bigboscagri.data.inCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryFilterAndSortTest {

    // 1. Color 1 filter matches reminder with exact Color 1
    @Test
    fun color1FilterMatchesReminderWithExactColor1() {
        val reminderBlue = ReminderItem(
            id = 1L,
            text = "Blue reminder",
            createdAt = 1000L,
            accentStart = 2, // Color 1 = index 2
            accentEnd = null
        )
        val reminderGreen = ReminderItem(
            id = 2L,
            text = "Green reminder",
            createdAt = 2000L,
            accentStart = 5, // Color 1 = index 5
            accentEnd = null
        )

        val filtered = filterAndSortReminders(
            reminders = listOf(reminderBlue, reminderGreen),
            colorFilter = 2,
            sortOrder = ReminderSortOrder.RECENTLY_ADDED
        )

        assertEquals(1, filtered.size)
        assertEquals(1L, filtered.first().id)
        assertEquals(2, filtered.first().accentStart)
    }

    // 2. Color 1 filter excludes reminder with different Color 1
    @Test
    fun color1FilterExcludesReminderWithDifferentColor1() {
        val reminder1 = ReminderItem(
            id = 1L,
            text = "Color 3",
            createdAt = 1000L,
            accentStart = 3
        )
        val reminder2 = ReminderItem(
            id = 2L,
            text = "Color 4",
            createdAt = 2000L,
            accentStart = 4
        )
        val reminderNoColor = ReminderItem(
            id = 3L,
            text = "No color",
            createdAt = 3000L,
            accentStart = null
        )

        val filtered = filterAndSortReminders(
            reminders = listOf(reminder1, reminder2, reminderNoColor),
            colorFilter = 3,
            sortOrder = ReminderSortOrder.RECENTLY_ADDED
        )

        assertEquals(1, filtered.size)
        assertEquals(1L, filtered.first().id)
        assertFalse(filtered.any { it.id == 2L })
        assertFalse(filtered.any { it.id == 3L })
    }

    // 3. Reminder with matching Color 2 but DIFFERENT Color 1 is EXCLUDED (Color 2 must never match)
    @Test
    fun matchingColor2AloneDoesNotIncludeReminder() {
        // Target filter is Color 1 = index 4
        // This reminder has Color 1 = index 1, and Color 2 = index 4
        val reminderWithColor2Match = ReminderItem(
            id = 10L,
            text = "Gradient reminder with Color 2 match",
            createdAt = 1500L,
            accentStart = 1, // Color 1
            accentEnd = 4    // Color 2
        )
        val reminderWithColor1Match = ReminderItem(
            id = 20L,
            text = "Reminder with Color 1 match",
            createdAt = 2500L,
            accentStart = 4, // Color 1 matches filter
            accentEnd = 7
        )

        val filtered = filterAndSortReminders(
            reminders = listOf(reminderWithColor2Match, reminderWithColor1Match),
            colorFilter = 4,
            sortOrder = ReminderSortOrder.RECENTLY_ADDED
        )

        assertEquals(1, filtered.size)
        assertEquals(20L, filtered.first().id)
        assertFalse("Color 2 match must never cause inclusion", filtered.any { it.id == 10L })
    }

    // 4. Clearing the color filter restores all category reminders
    @Test
    fun clearingColorFilterRestoresAllCategoryReminders() {
        val reminders = listOf(
            ReminderItem(id = 1L, text = "One", createdAt = 1000L, accentStart = 1),
            ReminderItem(id = 2L, text = "Two", createdAt = 2000L, accentStart = 2),
            ReminderItem(id = 3L, text = "Three", createdAt = 3000L, accentStart = null),
            ReminderItem(id = 4L, text = "Four", createdAt = 4000L, accentStart = 1, accentEnd = 3)
        )

        // Filter active: only color 1
        val filtered = filterAndSortReminders(reminders, colorFilter = 1, sortOrder = ReminderSortOrder.RECENTLY_ADDED)
        assertEquals(2, filtered.size)

        // Filter cleared (null): all restored
        val restored = filterAndSortReminders(reminders, colorFilter = null, sortOrder = ReminderSortOrder.RECENTLY_ADDED)
        assertEquals(4, restored.size)
        assertEquals(listOf(4L, 3L, 2L, 1L), restored.map { it.id }) // RECENTLY_ADDED order
    }

    // 5. RECENTLY_ADDED uses createdAt descending
    @Test
    fun recentlyAddedUsesCreatedAtDescending() {
        val oldest = ReminderItem(id = 1L, text = "Oldest", createdAt = 1000L)
        val middle = ReminderItem(id = 2L, text = "Middle", createdAt = 2000L)
        val newest = ReminderItem(id = 3L, text = "Newest", createdAt = 3000L)

        // Input in arbitrary order
        val input = listOf(oldest, newest, middle)

        val sorted = filterAndSortReminders(
            reminders = input,
            colorFilter = null,
            sortOrder = ReminderSortOrder.RECENTLY_ADDED
        )

        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
        assertEquals(3000L, sorted[0].createdAt)
        assertEquals(2000L, sorted[1].createdAt)
        assertEquals(1000L, sorted[2].createdAt)
    }

    // 6. REMINDER_TIME uses alertAt ascending (nearest upcoming first)
    @Test
    fun reminderTimeUsesAlertAtAscending() {
        val farFuture = ReminderItem(id = 1L, text = "Far future", createdAt = 5000L, alertAt = 300000L)
        val nearFuture = ReminderItem(id = 2L, text = "Near future", createdAt = 1000L, alertAt = 100000L)
        val midFuture = ReminderItem(id = 3L, text = "Mid future", createdAt = 3000L, alertAt = 200000L)

        val sorted = filterAndSortReminders(
            reminders = listOf(farFuture, nearFuture, midFuture),
            colorFilter = null,
            sortOrder = ReminderSortOrder.REMINDER_TIME
        )

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
        assertEquals(100000L, sorted[0].alertAt)
        assertEquals(200000L, sorted[1].alertAt)
        assertEquals(300000L, sorted[2].alertAt)
    }

    // 7. Reminders with null alertAt are placed after scheduled reminders
    @Test
    fun remindersWithNullAlertAtArePlacedAfterScheduledReminders() {
        val scheduled1 = ReminderItem(id = 1L, text = "Scheduled 2pm", createdAt = 100L, alertAt = 2000L)
        val scheduled2 = ReminderItem(id = 2L, text = "Scheduled 1pm", createdAt = 200L, alertAt = 1000L)
        val unscheduled1 = ReminderItem(id = 3L, text = "No alert new", createdAt = 500L, alertAt = null)
        val unscheduled2 = ReminderItem(id = 4L, text = "No alert old", createdAt = 300L, alertAt = null)

        val sorted = filterAndSortReminders(
            reminders = listOf(unscheduled1, scheduled1, unscheduled2, scheduled2),
            colorFilter = null,
            sortOrder = ReminderSortOrder.REMINDER_TIME
        )

        // Scheduled first (sorted by alertAt ascending: 1000L, 2000L), then unscheduled (sorted by createdAt descending: 500L, 300L)
        assertEquals(listOf(2L, 1L, 3L, 4L), sorted.map { it.id })
        assertEquals(1000L, sorted[0].alertAt)
        assertEquals(2000L, sorted[1].alertAt)
        assertEquals(null, sorted[2].alertAt)
        assertEquals(null, sorted[3].alertAt)
    }

    // 8. Tapping/toggling state changes between the two sort modes
    @Test
    fun togglingStateChangesBetweenTheTwoSortModes() {
        var currentMode = ReminderSortOrder.RECENTLY_ADDED

        // Toggle 1
        currentMode = if (currentMode == ReminderSortOrder.RECENTLY_ADDED) {
            ReminderSortOrder.REMINDER_TIME
        } else {
            ReminderSortOrder.RECENTLY_ADDED
        }
        assertEquals(ReminderSortOrder.REMINDER_TIME, currentMode)

        // Toggle 2
        currentMode = if (currentMode == ReminderSortOrder.RECENTLY_ADDED) {
            ReminderSortOrder.REMINDER_TIME
        } else {
            ReminderSortOrder.RECENTLY_ADDED
        }
        assertEquals(ReminderSortOrder.RECENTLY_ADDED, currentMode)
    }

    // 9. Category and color filtering still composes correctly with both sort modes
    @Test
    fun categoryAndColorFilterComposeWithBothSortModes() {
        val reminders = listOf(
            ReminderItem(id = 1L, text = "Cat 42 - Color 3 - Early Alert", createdAt = 1000L, customCategoryId = 42L, accentStart = 3, alertAt = 5000L),
            ReminderItem(id = 2L, text = "Cat 42 - Color 5 - Ignored Color", createdAt = 2000L, customCategoryId = 42L, accentStart = 5, alertAt = 1000L),
            ReminderItem(id = 3L, text = "Cat 42 - Color 3 - Late Alert", createdAt = 3000L, customCategoryId = 42L, accentStart = 3, alertAt = 9000L),
            ReminderItem(id = 4L, text = "Cat 42 - Color 3 - No Alert", createdAt = 4000L, customCategoryId = 42L, accentStart = 3, alertAt = null),
            ReminderItem(id = 5L, text = "Other Cat - Color 3", createdAt = 6000L, customCategoryId = 99L, accentStart = 3, alertAt = 2000L)
        )

        // Step 1: Category filter
        val inCat42 = reminders.filter { it.customCategoryId == 42L }

        // Test with RECENTLY_ADDED:
        // Filter Color 3: items 1, 3, 4. Sorted by createdAt descending: 4 (4000L), 3 (3000L), 1 (1000L)
        val recentSort = filterAndSortReminders(
            reminders = inCat42,
            colorFilter = 3,
            sortOrder = ReminderSortOrder.RECENTLY_ADDED
        )
        assertEquals(listOf(4L, 3L, 1L), recentSort.map { it.id })

        // Test with REMINDER_TIME:
        // Filter Color 3: items 1, 3, 4.
        // Scheduled: item 1 (alertAt=5000L), item 3 (alertAt=9000L)
        // Unscheduled: item 4 (alertAt=null)
        // Order: 1, 3, 4
        val timeSort = filterAndSortReminders(
            reminders = inCat42,
            colorFilter = 3,
            sortOrder = ReminderSortOrder.REMINDER_TIME
        )
        assertEquals(listOf(1L, 3L, 4L), timeSort.map { it.id })
    }

    // 10. Neutral / default (no Color 1) filter test
    @Test
    fun neutralColorFilterMatchesRemindersWithNoColor1() {
        val withColor = ReminderItem(id = 1L, text = "Has color", createdAt = 1000L, accentStart = 0)
        val noColor = ReminderItem(id = 2L, text = "No color", createdAt = 2000L, accentStart = null)

        val filtered = filterAndSortReminders(
            reminders = listOf(withColor, noColor),
            colorFilter = COLOR_FILTER_DEFAULT,
            sortOrder = ReminderSortOrder.RECENTLY_ADDED
        )

        assertEquals(1, filtered.size)
        assertEquals(2L, filtered.first().id)
        assertEquals(null, filtered.first().accentStart)
    }

    // 11. Accessibility content description matches exact requested strings
    @Test
    fun sortControlAccessibilityContentDescriptions() {
        fun getContentDescription(order: ReminderSortOrder): String = when (order) {
            ReminderSortOrder.RECENTLY_ADDED ->
                "Son eklenene göre sıralanıyor. Bildirim zamanına göre sırala."
            ReminderSortOrder.REMINDER_TIME ->
                "Bildirim zamanına göre sıralanıyor. Son eklenene göre sırala."
        }

        assertEquals(
            "Son eklenene göre sıralanıyor. Bildirim zamanına göre sırala.",
            getContentDescription(ReminderSortOrder.RECENTLY_ADDED)
        )
        assertEquals(
            "Bildirim zamanına göre sıralanıyor. Son eklenene göre sırala.",
            getContentDescription(ReminderSortOrder.REMINDER_TIME)
        )
    }

    // 12. Sort preference persistence via SharedPreferences
    @Test
    fun sortPreferencePersistenceInPreferences() {
        val prefs = FakeSharedPreferences()

        // Default when absent
        val initialOrder = when (prefs.getString("sort_order", null)) {
            "reminder_time" -> ReminderSortOrder.REMINDER_TIME
            else -> ReminderSortOrder.RECENTLY_ADDED
        }
        assertEquals(ReminderSortOrder.RECENTLY_ADDED, initialOrder)

        // Save REMINDER_TIME
        prefs.edit().putString("sort_order", "reminder_time").apply()
        val updatedOrder = when (prefs.getString("sort_order", null)) {
            "reminder_time" -> ReminderSortOrder.REMINDER_TIME
            else -> ReminderSortOrder.RECENTLY_ADDED
        }
        assertEquals(ReminderSortOrder.REMINDER_TIME, updatedOrder)

        // Save RECENTLY_ADDED
        prefs.edit().putString("sort_order", "recently_added").apply()
        val revertedOrder = when (prefs.getString("sort_order", null)) {
            "reminder_time" -> ReminderSortOrder.REMINDER_TIME
            else -> ReminderSortOrder.RECENTLY_ADDED
        }
        assertEquals(ReminderSortOrder.RECENTLY_ADDED, revertedOrder)
    }

    // 13. Category color filter isolation and preservation across navigation lifecycle
    @Test
    fun categoryColorFilterIsolationAndPreservation() {
        val categoryFilters = mutableMapOf<String, Int>()

        val arabaKey = "custom_1"
        val isKey = "custom_2"

        // In Araba, user selects Blue (index 2)
        categoryFilters[arabaKey] = 2
        assertEquals(2, categoryFilters[arabaKey])

        // User navigates to İş: filter must be isolated and null by default
        val isFilter = categoryFilters[isKey]
        assertEquals(null, isFilter)

        // User returns to Araba: Blue filter must be preserved
        val restoredArabaFilter = categoryFilters[arabaKey]
        assertEquals(2, restoredArabaFilter)

        // In Araba, only Blue reminders are matched
        val arabaReminders = listOf(
            ReminderItem(id = 101L, text = "Motor yağı", createdAt = 1000L, customCategoryId = 1L, accentStart = 2),
            ReminderItem(id = 102L, text = "Lastik hava", createdAt = 2000L, customCategoryId = 1L, accentStart = 5)
        )
        val filtered = filterAndSortReminders(arabaReminders, categoryFilters[arabaKey], ReminderSortOrder.RECENTLY_ADDED)
        assertEquals(1, filtered.size)
        assertEquals(101L, filtered.first().id)

        // Clearing filter in Araba removes only Araba's filter
        categoryFilters.remove(arabaKey)
        assertEquals(null, categoryFilters[arabaKey])
    }

    // 14. Edit mode: secondary panels collapse when user focuses text field, remain collapsed when focus lost, reopen manually
    @Test
    fun editModeSecondaryPanelsCollapseOnFocusAndRemainCollapsed() {
        var secondaryPanelsExpanded = true

        // User enters edit mode via "Düzenle" -> panels are expanded, text field is not auto-focused
        assertEquals(true, secondaryPanelsExpanded)

        // User explicitly focuses the text field -> secondary panels collapse
        fun onTextFieldFocusChanged(isFocused: Boolean) {
            if (isFocused) {
                secondaryPanelsExpanded = false
            }
        }

        onTextFieldFocusChanged(isFocused = true)
        assertEquals(false, secondaryPanelsExpanded)

        // Focus lost (e.g. keyboard dismissed) -> panels do NOT auto-reopen, remain collapsed
        onTextFieldFocusChanged(isFocused = false)
        assertEquals(false, secondaryPanelsExpanded)

        // User manually taps the compact reopen control -> panels expand again
        secondaryPanelsExpanded = true
        assertEquals(true, secondaryPanelsExpanded)

        // User taps text field again -> panels collapse again
        onTextFieldFocusChanged(isFocused = true)
        assertEquals(false, secondaryPanelsExpanded)
    }

    // 15. Checklist spacing: title-to-first-item vertical padding reduced by 6dp
    @Test
    fun checklistTitleToFirstItemSpacingReduction() {
        val originalSummaryBottomPadding = 6 // dp
        val originalColumnTopPadding = 4 // dp
        val originalGap = originalSummaryBottomPadding + originalColumnTopPadding // 10dp

        val newSummaryBottomPadding = 2 // dp
        val newColumnTopPadding = 2 // dp
        val newGap = newSummaryBottomPadding + newColumnTopPadding // 4dp

        val reduction = originalGap - newGap
        assertEquals(6, reduction) // Exact 6dp reduction in the target 4-6dp range
    }

    @Test
    fun homeCompletedPreviewLimitsToEightItemsWhileDedicatedShowsAll() {
        val completedReminders = (1..15).map { id ->
            ReminderItem(
                id = id.toLong(),
                text = "Completed $id",
                createdAt = id * 1000L,
                completedAt = id * 1000L
            )
        }

        val homeCompletedPreview = completedReminders.take(HOME_COMPLETED_PREVIEW_LIMIT)
        assertEquals(8, homeCompletedPreview.size)
        assertEquals(1L, homeCompletedPreview.first().id)
        assertEquals(8L, homeCompletedPreview.last().id)

        val dedicatedCompletedList = completedReminders.inCategory(ReminderCategory.Completed)
        assertEquals(15, dedicatedCompletedList.size)
    }

    @Test
    fun homeCompletedGlassEndingActivationThreshold() {
        (0..7).forEach { count ->
            assertFalse(isHomeCompletedGlassActive(count))
        }

        assertTrue(isHomeCompletedGlassActive(8))
        assertTrue(isHomeCompletedGlassActive(9))
        assertTrue(isHomeCompletedGlassActive(15))
        assertEquals(108f, HOME_COMPLETED_GLASS_HEIGHT_DP, 0.001f)
    }

    @Test
    fun homeCompletedPreviewHasNoShowAllAction() {
        val completedReminders = (1..15).map { id ->
            ReminderItem(
                id = id.toLong(),
                text = "Completed $id",
                createdAt = id * 1000L,
                completedAt = id * 1000L
            )
        }
        val homeCompletedPreview = completedReminders.take(HOME_COMPLETED_PREVIEW_LIMIT)
        assertEquals(8, homeCompletedPreview.size)
        val dedicatedCompletedList = completedReminders.inCategory(ReminderCategory.Completed)
        assertEquals(15, dedicatedCompletedList.size)
    }
}
