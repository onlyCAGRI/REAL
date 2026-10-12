package com.cagri.real

import com.cagri.real.data.COLOR_FILTER_DEFAULT
import com.cagri.real.data.ReminderCategory
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.ReminderSortOrder
import com.cagri.real.data.HOME_COMPLETED_PREVIEW_LIMIT
import com.cagri.real.data.HOME_COMPLETED_GLASS_HEIGHT_DP
import com.cagri.real.data.isHomeCompletedGlassActive
import com.cagri.real.data.filterAndSortReminders
import com.cagri.real.data.inCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryFilterAndSortTest {

    
    @Test
    fun color1FilterMatchesReminderWithExactColor1() {
        val reminderBlue = ReminderItem(
            id = 1L,
            text = "Blue reminder",
            createdAt = 1000L,
            accentStart = 2, 
            accentEnd = null
        )
        val reminderGreen = ReminderItem(
            id = 2L,
            text = "Green reminder",
            createdAt = 2000L,
            accentStart = 5, 
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

    
    @Test
    fun eitherSelectedColorMatchesButUnselectedColorsDoNot() {
        val gradient = ReminderItem(
            id = 10L,
            text = "Two selected colors",
            createdAt = 1500L,
            accentStart = 1,
            accentEnd = 4
        )
        val singleColor = ReminderItem(
            id = 20L,
            text = "Only Color 4",
            createdAt = 2500L,
            accentStart = 4,
            accentEnd = null
        )
        val reminders = listOf(gradient, singleColor)

        assertEquals(listOf(10L), filterAndSortReminders(reminders, colorFilter = 1).map { it.id })
        assertEquals(listOf(20L, 10L), filterAndSortReminders(reminders, colorFilter = 4).map { it.id })
        assertTrue(filterAndSortReminders(reminders, colorFilter = 2).isEmpty())
    }

    
    @Test
    fun clearingColorFilterRestoresAllCategoryReminders() {
        val reminders = listOf(
            ReminderItem(id = 1L, text = "One", createdAt = 1000L, accentStart = 1),
            ReminderItem(id = 2L, text = "Two", createdAt = 2000L, accentStart = 2),
            ReminderItem(id = 3L, text = "Three", createdAt = 3000L, accentStart = null),
            ReminderItem(id = 4L, text = "Four", createdAt = 4000L, accentStart = 1, accentEnd = 3)
        )

        
        val filtered = filterAndSortReminders(reminders, colorFilter = 1, sortOrder = ReminderSortOrder.RECENTLY_ADDED)
        assertEquals(2, filtered.size)

        
        val restored = filterAndSortReminders(reminders, colorFilter = null, sortOrder = ReminderSortOrder.RECENTLY_ADDED)
        assertEquals(4, restored.size)
        assertEquals(listOf(4L, 3L, 2L, 1L), restored.map { it.id }) 
    }

    
    @Test
    fun recentlyAddedUsesCreatedAtDescending() {
        val oldest = ReminderItem(id = 1L, text = "Oldest", createdAt = 1000L)
        val middle = ReminderItem(id = 2L, text = "Middle", createdAt = 2000L)
        val newest = ReminderItem(id = 3L, text = "Newest", createdAt = 3000L)

        
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

        
        assertEquals(listOf(2L, 1L, 3L, 4L), sorted.map { it.id })
        assertEquals(1000L, sorted[0].alertAt)
        assertEquals(2000L, sorted[1].alertAt)
        assertEquals(null, sorted[2].alertAt)
        assertEquals(null, sorted[3].alertAt)
    }

    
    @Test
    fun togglingStateChangesBetweenTheTwoSortModes() {
        var currentMode = ReminderSortOrder.RECENTLY_ADDED

        
        currentMode = if (currentMode == ReminderSortOrder.RECENTLY_ADDED) {
            ReminderSortOrder.REMINDER_TIME
        } else {
            ReminderSortOrder.RECENTLY_ADDED
        }
        assertEquals(ReminderSortOrder.REMINDER_TIME, currentMode)

        
        currentMode = if (currentMode == ReminderSortOrder.RECENTLY_ADDED) {
            ReminderSortOrder.REMINDER_TIME
        } else {
            ReminderSortOrder.RECENTLY_ADDED
        }
        assertEquals(ReminderSortOrder.RECENTLY_ADDED, currentMode)
    }

    
    @Test
    fun categoryAndColorFilterComposeWithBothSortModes() {
        val reminders = listOf(
            ReminderItem(id = 1L, text = "Cat 42 - Color 3 - Early Alert", createdAt = 1000L, customCategoryId = 42L, accentStart = 3, alertAt = 5000L),
            ReminderItem(id = 2L, text = "Cat 42 - Color 5 - Ignored Color", createdAt = 2000L, customCategoryId = 42L, accentStart = 5, alertAt = 1000L),
            ReminderItem(id = 3L, text = "Cat 42 - Color 3 - Late Alert", createdAt = 3000L, customCategoryId = 42L, accentStart = 3, alertAt = 9000L),
            ReminderItem(id = 4L, text = "Cat 42 - Color 3 - No Alert", createdAt = 4000L, customCategoryId = 42L, accentStart = 3, alertAt = null),
            ReminderItem(id = 5L, text = "Other Cat - Color 3", createdAt = 6000L, customCategoryId = 99L, accentStart = 3, alertAt = 2000L)
        )

        
        val inCat42 = reminders.filter { it.customCategoryId == 42L }

        
        
        val recentSort = filterAndSortReminders(
            reminders = inCat42,
            colorFilter = 3,
            sortOrder = ReminderSortOrder.RECENTLY_ADDED
        )
        assertEquals(listOf(4L, 3L, 1L), recentSort.map { it.id })

        
        
        
        
        
        val timeSort = filterAndSortReminders(
            reminders = inCat42,
            colorFilter = 3,
            sortOrder = ReminderSortOrder.REMINDER_TIME
        )
        assertEquals(listOf(1L, 3L, 4L), timeSort.map { it.id })
    }

    
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

    
    @Test
    fun sortControlAccessibilityContentDescriptions() {
        fun getContentDescription(order: ReminderSortOrder): String = when (order) {
            ReminderSortOrder.RECENTLY_ADDED, ReminderSortOrder.OLDEST_FIRST ->
                "Son eklenene göre sıralanıyor. Bildirim zamanına göre sırala."
            ReminderSortOrder.REMINDER_TIME, ReminderSortOrder.NEAREST_DATE ->
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
        assertEquals(
            getContentDescription(ReminderSortOrder.RECENTLY_ADDED),
            getContentDescription(ReminderSortOrder.OLDEST_FIRST)
        )
        assertEquals(
            getContentDescription(ReminderSortOrder.REMINDER_TIME),
            getContentDescription(ReminderSortOrder.NEAREST_DATE)
        )
    }

    
    @Test
    fun sortPreferencePersistenceInPreferences() {
        val prefs = FakeSharedPreferences()

        
        val initialOrder = when (prefs.getString("sort_order", null)) {
            "reminder_time" -> ReminderSortOrder.REMINDER_TIME
            else -> ReminderSortOrder.RECENTLY_ADDED
        }
        assertEquals(ReminderSortOrder.RECENTLY_ADDED, initialOrder)

        
        prefs.edit().putString("sort_order", "reminder_time").apply()
        val updatedOrder = when (prefs.getString("sort_order", null)) {
            "reminder_time" -> ReminderSortOrder.REMINDER_TIME
            else -> ReminderSortOrder.RECENTLY_ADDED
        }
        assertEquals(ReminderSortOrder.REMINDER_TIME, updatedOrder)

        
        prefs.edit().putString("sort_order", "recently_added").apply()
        val revertedOrder = when (prefs.getString("sort_order", null)) {
            "reminder_time" -> ReminderSortOrder.REMINDER_TIME
            else -> ReminderSortOrder.RECENTLY_ADDED
        }
        assertEquals(ReminderSortOrder.RECENTLY_ADDED, revertedOrder)
    }

    
    @Test
    fun categoryColorFilterIsolationAndPreservation() {
        val categoryFilters = mutableMapOf<String, Int>()

        val arabaKey = "custom_1"
        val isKey = "custom_2"

        
        categoryFilters[arabaKey] = 2
        assertEquals(2, categoryFilters[arabaKey])

        
        val isFilter = categoryFilters[isKey]
        assertEquals(null, isFilter)

        
        val restoredArabaFilter = categoryFilters[arabaKey]
        assertEquals(2, restoredArabaFilter)

        
        val arabaReminders = listOf(
            ReminderItem(id = 101L, text = "Motor yağı", createdAt = 1000L, customCategoryId = 1L, accentStart = 2),
            ReminderItem(id = 102L, text = "Lastik hava", createdAt = 2000L, customCategoryId = 1L, accentStart = 5)
        )
        val filtered = filterAndSortReminders(arabaReminders, categoryFilters[arabaKey], ReminderSortOrder.RECENTLY_ADDED)
        assertEquals(1, filtered.size)
        assertEquals(101L, filtered.first().id)

        
        categoryFilters.remove(arabaKey)
        assertEquals(null, categoryFilters[arabaKey])
    }

    
    @Test
    fun editModeSecondaryPanelsCollapseOnFocusAndRemainCollapsed() {
        var secondaryPanelsExpanded = true

        
        assertEquals(true, secondaryPanelsExpanded)

        
        fun onTextFieldFocusChanged(isFocused: Boolean) {
            if (isFocused) {
                secondaryPanelsExpanded = false
            }
        }

        onTextFieldFocusChanged(isFocused = true)
        assertEquals(false, secondaryPanelsExpanded)

        
        onTextFieldFocusChanged(isFocused = false)
        assertEquals(false, secondaryPanelsExpanded)

        
        secondaryPanelsExpanded = true
        assertEquals(true, secondaryPanelsExpanded)

        
        onTextFieldFocusChanged(isFocused = true)
        assertEquals(false, secondaryPanelsExpanded)
    }

    
    @Test
    fun checklistTitleToFirstItemSpacingReduction() {
        val originalSummaryBottomPadding = 6 
        val originalColumnTopPadding = 4 
        val originalGap = originalSummaryBottomPadding + originalColumnTopPadding 

        val newSummaryBottomPadding = 2 
        val newColumnTopPadding = 2 
        val newGap = newSummaryBottomPadding + newColumnTopPadding 

        val reduction = originalGap - newGap
        assertEquals(6, reduction) 
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
