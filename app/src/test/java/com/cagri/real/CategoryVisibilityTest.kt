package com.cagri.real

import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderCategory
import com.cagri.real.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class CategoryVisibilityTest {
    private val custom = CustomCategory(77, "Çalışma", "star", 0)
    private val a = categoryOrderKey(ReminderCategory.Important)
    private val b = categoryOrderKey(ReminderCategory.Planned)
    private val c = categoryOrderKey(custom)

    @Test fun legacyOrderStillResolvesAndNewCategoriesAppend() {
        val keys = resolveHomeCategoryOrder(listOf(b, a), listOf(custom)).map { it.orderKey }
        assertEquals(listOf(b, a), keys.take(2))
        assertEquals(c, keys.last())
    }
    @Test fun hiddenBuiltInAndCustomStayHiddenAfterPreferenceRoundTrip() {
        val stored = categoryOrderWithVisibility(listOf(a, b, c), setOf(a, c))
        val loaded = parseCategoryOrderPreference(encodeCategoryOrderPreference(stored))
        assertEquals(setOf(a, c), hiddenCategoryKeys(loaded))
        val visible = resolveHomeCategoryOrder(loaded, listOf(custom)).map { it.orderKey }
        assertFalse(a in visible); assertFalse(c in visible); assertTrue(b in visible)
        assertEquals(3, resolveCategoryOrder(loaded, listOf(custom)).count { it.orderKey in listOf(a,b,c) })
    }
    @Test fun showAllControlCanBeHiddenWithoutHidingCategories() {
        val stored = categoryOrderWithVisibility(listOf(a,b,c,CATEGORY_SHOW_ALL_KEY), setOf(CATEGORY_SHOW_ALL_KEY))
        assertTrue(CATEGORY_SHOW_ALL_KEY in hiddenCategoryKeys(stored))
        assertEquals(resolveHomeCategoryOrder(listOf(a,b,c), listOf(custom)), resolveHomeCategoryOrder(stored, listOf(custom)))
    }
    @Test fun collapsedCountIsAppliedAfterHiddenCategoriesAreRemoved() {
        val stored = categoryOrderWithVisibility(listOf(a,b,c), setOf(a))
        assertEquals(listOf(b,c), resolveHomeCategoryOrder(stored, listOf(custom)).take(2).map { it.orderKey })
    }
    @Test fun restorePreservesOriginalPositionAndSupportsHidingEverything() {
        val all = resolveHomeCategoryOrder(emptyList(), listOf(custom)).map { it.orderKey }
        val stored = categoryOrderWithVisibility(all, all.toSet() + CATEGORY_SHOW_ALL_KEY)
        assertTrue(resolveHomeCategoryOrder(stored, listOf(custom)).isEmpty())
        val restored = categoryOrderWithVisibility(all, emptySet())
        assertEquals(all, resolveHomeCategoryOrder(restored, listOf(custom)).map { it.orderKey })
        assertTrue(hiddenCategoryKeys(restored).isEmpty())
    }
    @Test fun droppingAboveBelowAndIntoSameSlotIsStable() {
        val keys = listOf(a,b,c)
        assertEquals(listOf(b,c,a), moveCategoryToSlot(keys,a,3))
        assertEquals(listOf(c,a,b), moveCategoryToSlot(keys,c,0))
        assertEquals(keys, moveCategoryToSlot(keys,b,1))
        assertEquals(keys, moveCategoryToSlot(keys,b,2))
        assertEquals(keys, moveCategoryToSlot(keys,"missing",0))
        assertEquals(listOf(b,c,a), moveCategoryToSlot(keys,a,999))
    }
    @Test fun staleAndDuplicateKeysNeverCreatePhantomCategories() {
        val stored = categoryOrderWithVisibility(listOf(a,a,"custom:deleted",c),setOf("custom:deleted"))
        val visible = resolveHomeCategoryOrder(stored,listOf(custom)).map { it.orderKey }
        assertEquals(1,visible.count { it==a })
        assertFalse("custom:deleted" in visible)
    }
}
