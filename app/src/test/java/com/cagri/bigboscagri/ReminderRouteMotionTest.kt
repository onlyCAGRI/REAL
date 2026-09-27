package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.data.ReminderCategory
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.ui.ReminderRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test




class ReminderRouteMotionTest {

    @Test
    fun routeKeyIdentity() {
        val homeRoute = ReminderRoute(category = null, customCategoryId = null, detailId = null)
        assertEquals("home", homeRoute.key)

        val todayRoute = ReminderRoute(category = ReminderCategory.Today, customCategoryId = null, detailId = null)
        assertEquals("Today", todayRoute.key)

        val plannedRoute = ReminderRoute(category = ReminderCategory.Planned, customCategoryId = null, detailId = null)
        assertEquals("Planned", plannedRoute.key)

        val completedRoute = ReminderRoute(category = ReminderCategory.Completed, customCategoryId = null, detailId = null)
        assertEquals("Completed", completedRoute.key)

        val customCategoryRoute = ReminderRoute(category = null, customCategoryId = 105L, detailId = null)
        assertEquals("custom_cat_105", customCategoryRoute.key)

        
        val homeDetail = ReminderRoute(category = null, customCategoryId = null, detailId = 42L)
        assertEquals("detail_42", homeDetail.key)

        val categoryDetail = ReminderRoute(category = ReminderCategory.Today, customCategoryId = null, detailId = 42L)
        assertEquals("detail_42", categoryDetail.key)

        val customCategoryDetail = ReminderRoute(category = null, customCategoryId = 105L, detailId = 42L)
        assertEquals("detail_42", customCategoryDetail.key)
    }

    @Test
    fun routeTransitionClassification() {
        val home = ReminderRoute(null, null, null)
        val categoryToday = ReminderRoute(ReminderCategory.Today, null, null)
        val customCat = ReminderRoute(null, 200L, null)
        val detail42 = ReminderRoute(null, null, 42L)
        val categoryDetail42 = ReminderRoute(ReminderCategory.Today, null, 42L)

        
        assertTrue(isOpeningDetail(home, detail42))
        assertFalse(isClosingDetail(home, detail42))

        
        assertFalse(isOpeningDetail(detail42, home))
        assertTrue(isClosingDetail(detail42, home))

        
        assertTrue(isOpeningDetail(categoryToday, categoryDetail42))
        assertFalse(isClosingDetail(categoryToday, categoryDetail42))

        
        assertFalse(isOpeningDetail(categoryDetail42, categoryToday))
        assertTrue(isClosingDetail(categoryDetail42, categoryToday))

        
        assertFalse(isOpeningDetail(home, categoryToday))
        assertFalse(isClosingDetail(home, categoryToday))
        assertFalse(isOpeningDetail(categoryToday, home))
        assertFalse(isClosingDetail(categoryToday, home))
        assertFalse(isOpeningDetail(home, customCat))
        assertFalse(isClosingDetail(home, customCat))
    }

    @Test
    fun openDetailGuardPreventsDoubleTapRace() {
        var detailId: Long? = null
        val onOpenReminder: (Long) -> Unit = { id ->
            if (detailId == null) detailId = id
        }

        
        onOpenReminder(10L)
        assertEquals(10L, detailId)

        
        onOpenReminder(20L)
        assertEquals(10L, detailId) 

        
        detailId = null

        
        onOpenReminder(20L)
        assertEquals(20L, detailId)
    }

    @Test
    fun sharedContentKeyStabilityAcrossMutations() {
        val item = ReminderItem(
            id = 77L,
            text = "Fatura öde",
            createdAt = 1000L,
            alertAt = 1750000000000L,
            repeatRule = RecurrenceRule.NONE,
            isImportant = false,
            completedAt = null
        )

        fun sharedKey(reminder: ReminderItem) = "reminder-card-${reminder.id}"

        val initialKey = sharedKey(item)
        assertEquals("reminder-card-77", initialKey)

        
        val completedItem = item.copy(completedAt = System.currentTimeMillis())
        assertEquals(initialKey, sharedKey(completedItem))

        
        val recurringItem = item.copy(repeatRule = RecurrenceRule.MONTHLY, alertAt = 1752000000000L)
        assertEquals(initialKey, sharedKey(recurringItem))

        
        val starredItem = item.copy(isImportant = true)
        assertEquals(initialKey, sharedKey(starredItem))

        
        val editedItem = item.copy(text = "Elektrik faturası öde")
        assertEquals(initialKey, sharedKey(editedItem))
    }

    @Test
    fun categoryContextPreservedOnDetailReturn() {
        var category: ReminderCategory? = null
        var customCategoryId: Long? = null
        var detailId: Long? = null

        fun currentRoute() = ReminderRoute(category, customCategoryId, detailId)

        
        assertEquals("home", currentRoute().key)

        
        category = ReminderCategory.Today
        assertEquals("Today", currentRoute().key)

        
        detailId = 88L
        assertEquals("detail_88", currentRoute().key)

        
        detailId = null
        
        assertEquals("Today", currentRoute().key)
        assertEquals(ReminderCategory.Today, category)

        
        category = null
        assertEquals("home", currentRoute().key)
    }

    private fun isOpeningDetail(initial: ReminderRoute, target: ReminderRoute): Boolean =
        initial.detailId == null && target.detailId != null

    private fun isClosingDetail(initial: ReminderRoute, target: ReminderRoute): Boolean =
        initial.detailId != null && target.detailId == null
}
