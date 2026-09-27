package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.ReminderCategory
import com.cagri.bigboscagri.data.ReminderRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryLinesAndStarBorderTest {

    // 1. Home screen vs Category screen category stripes logic
    @Test
    fun categoryStripesVisibleOnlyOnHome() {
        fun shouldShowCategoryStripes(category: ReminderCategory?, customCategoryId: Long?): Boolean {
            return category == null && customCategoryId == null
        }

        // Home screen (both null) -> true
        assertTrue(shouldShowCategoryStripes(null, null))

        // Built-in category (e.g. Today, Planned, Important) -> false
        assertFalse(shouldShowCategoryStripes(ReminderCategory.Today, null))
        assertFalse(shouldShowCategoryStripes(ReminderCategory.Planned, null))
        assertFalse(shouldShowCategoryStripes(ReminderCategory.Important, null))

        // Custom category (e.g. id = 42L) -> false
        assertFalse(shouldShowCategoryStripes(null, 42L))

        // Both non-null (edge case) -> false
        assertFalse(shouldShowCategoryStripes(ReminderCategory.Important, 42L))
    }

    // 2. First time starring a reminder assigns a valid color index (0..9)
    @Test
    fun firstTimeStarringAssignsColorIndexInPaletteRange() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Test star color")
        assertFalse(item.isImportant)
        assertNull(item.starBorderColorIndex)

        val updatedList = repo.toggleImportant(item.id)
        val starredItem = updatedList.first { it.id == item.id }

        assertTrue(starredItem.isImportant)
        assertNotNull(starredItem.starBorderColorIndex)
        assertTrue(starredItem.starBorderColorIndex!! in 0..9)

        // Verify persisted in SharedPreferences JSON
        val rawJson = prefs.getString("reminders", null)
        assertNotNull(rawJson)
        val jsonArray = JSONArray(rawJson)
        val jsonObj = jsonArray.getJSONObject(0)
        assertEquals(starredItem.starBorderColorIndex, jsonObj.getInt("starBorderColorIndex"))
    }

    // 3. Unstar and re-star reuses previously chosen color
    @Test
    fun unstarAndRestarReusesPreviouslyChosenColor() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Test toggle retention")

        // Star 1st time
        val starredList = repo.toggleImportant(item.id)
        val initialColor = starredList.first { it.id == item.id }.starBorderColorIndex
        assertNotNull(initialColor)

        // Unstar
        val unstarredList = repo.toggleImportant(item.id)
        val unstarredItem = unstarredList.first { it.id == item.id }
        assertFalse(unstarredItem.isImportant)
        // Stored color index is preserved in the data model
        assertEquals(initialColor, unstarredItem.starBorderColorIndex)

        // Re-star
        val reStarredList = repo.toggleImportant(item.id)
        val reStarredItem = reStarredList.first { it.id == item.id }
        assertTrue(reStarredItem.isImportant)
        assertEquals("Must reuse the previously chosen color", initialColor, reStarredItem.starBorderColorIndex)
    }

    // 4. Legacy starred reminders without stored color use deterministic fallback
    @Test
    fun legacyStarredRemindersUseDeterministicFallback() {
        val legacyId = 12345L
        val legacyJson = JSONArray().apply {
            put(JSONObject().apply {
                put("id", legacyId)
                put("text", "Legacy starred reminder")
                put("createdAt", 1000L)
                put("isImportant", true)
                // NO starBorderColorIndex in JSON
            })
        }.toString()

        val prefs = FakeSharedPreferences()
        prefs.edit().putString("reminders", legacyJson).commit()

        val repo = ReminderRepository(prefs)
        val loaded = repo.load()

        assertEquals(1, loaded.size)
        val item = loaded.first()
        assertTrue(item.isImportant)
        val expectedDeterministic = Math.floorMod(legacyId, 10)
        assertEquals(expectedDeterministic, item.starBorderColorIndex)
    }

    // 5. Unstarred reminder without stored color remains null
    @Test
    fun unstarredReminderWithoutStoredColorRemainsNull() {
        val legacyId = 12345L
        val legacyJson = JSONArray().apply {
            put(JSONObject().apply {
                put("id", legacyId)
                put("text", "Legacy unstarred reminder")
                put("createdAt", 1000L)
                put("isImportant", false)
            })
        }.toString()

        val prefs = FakeSharedPreferences()
        prefs.edit().putString("reminders", legacyJson).commit()

        val repo = ReminderRepository(prefs)
        val loaded = repo.load()

        assertEquals(1, loaded.size)
        val item = loaded.first()
        assertFalse(item.isImportant)
        assertNull(item.starBorderColorIndex)
    }

    // 6. Editing starBorderColorIndex persists and updates reminder
    @Test
    fun editStarBorderColorIndexPersistsAndUpdates() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Test edit star color")
        repo.toggleImportant(item.id)

        // Edit star border color to index 7
        val editedList = repo.edit(
            id = item.id,
            text = "Test edit star color",
            starBorderColorIndex = 7
        )
        val editedItem = editedList.first { it.id == item.id }
        assertEquals(7, editedItem.starBorderColorIndex)

        // Verify persisted in SharedPreferences JSON
        val rawJson = prefs.getString("reminders", null)
        assertNotNull(rawJson)
        val jsonArray = JSONArray(rawJson)
        val jsonObj = jsonArray.getJSONObject(0)
        assertEquals(7, jsonObj.getInt("starBorderColorIndex"))
    }
}
