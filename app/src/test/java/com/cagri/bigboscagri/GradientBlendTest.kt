package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.COLOR_FILTER_DEFAULT
import com.cagri.bigboscagri.data.DEFAULT_GRADIENT_BLEND_FRACTION
import com.cagri.bigboscagri.data.GRADIENT_BLEND_FRACTIONS
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.ReminderRepository
import com.cagri.bigboscagri.data.filterAndSortReminders
import com.cagri.bigboscagri.data.migrateLegacyGradientBlendIndex
import com.cagri.bigboscagri.data.resolveGradientBlendFraction
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradientBlendTest {

    @Test
    fun defaultFractionEqualsHalf() {
        assertEquals(0.50f, DEFAULT_GRADIENT_BLEND_FRACTION, 0.001f)
        assertEquals(0.50f, resolveGradientBlendFraction(null), 0.001f)
    }

    @Test
    fun fractionClampsToValidRange() {
        assertEquals(0.0f, resolveGradientBlendFraction(-0.25f), 0.001f)
        assertEquals(1.0f, resolveGradientBlendFraction(1.25f), 0.001f)
        assertEquals(0.72f, resolveGradientBlendFraction(0.72f), 0.001f)
        assertEquals(0.0f, resolveGradientBlendFraction(0.0f), 0.001f)
        assertEquals(1.0f, resolveGradientBlendFraction(1.0f), 0.001f)
    }

    @Test
    fun legacyIndexMigration() {
        assertEquals(0.20f, migrateLegacyGradientBlendIndex(0), 0.001f)
        assertEquals(0.35f, migrateLegacyGradientBlendIndex(1), 0.001f)
        assertEquals(0.50f, migrateLegacyGradientBlendIndex(2), 0.001f)
        assertEquals(0.65f, migrateLegacyGradientBlendIndex(3), 0.001f)
        assertEquals(0.80f, migrateLegacyGradientBlendIndex(4), 0.001f)
        assertEquals(0.50f, migrateLegacyGradientBlendIndex(-1), 0.001f)
        assertEquals(0.50f, migrateLegacyGradientBlendIndex(5), 0.001f)
        assertEquals(0.50f, migrateLegacyGradientBlendIndex(99), 0.001f)
        assertEquals(0.50f, migrateLegacyGradientBlendIndex(null), 0.001f)
    }

    @Test
    fun persistedContinuousFractionSurvivesSaveAndReload() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Test continuous fraction")

        val updated = repo.edit(item.id, "Test continuous fraction", accentStart = 1, accentEnd = 3, gradientBlendFraction = 0.42f)
        val edited = updated.first { it.id == item.id }
        assertEquals(0.42f, edited.gradientBlendFraction!!, 0.001f)

        val newRepo = ReminderRepository(prefs)
        val loaded = newRepo.load().first { it.id == item.id }
        assertEquals(0.42f, loaded.gradientBlendFraction!!, 0.001f)
    }

    @Test
    fun editResetsGradientBlendFractionWhenSecondColorRemoved() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Test reset blend")

        repo.edit(item.id, "Test reset blend", accentStart = 1, accentEnd = 3, gradientBlendFraction = 0.85f)
        val updated = repo.edit(item.id, "Test reset blend", accentStart = 1, accentEnd = null)
        val edited = updated.first { it.id == item.id }
        assertNull(edited.gradientBlendFraction)
    }

    @Test
    fun loadHandlesLegacyAndContinuousJson() {
        val prefs = FakeSharedPreferences()
        val json = JSONArray().apply {
            put(
                JSONObject()
                    .put("id", 1001L)
                    .put("text", "Legacy item with neither value")
                    .put("createdAt", 1000L)
                    .put("accentStart", 2)
                    .put("accentEnd", 5)
            )
            put(
                JSONObject()
                    .put("id", 1002L)
                    .put("text", "Invalid legacy index item")
                    .put("createdAt", 1001L)
                    .put("accentStart", 2)
                    .put("accentEnd", 5)
                    .put("gradientBlendIndex", 99)
            )
            put(
                JSONObject()
                    .put("id", 1003L)
                    .put("text", "Valid legacy index item")
                    .put("createdAt", 1002L)
                    .put("accentStart", 2)
                    .put("accentEnd", 5)
                    .put("gradientBlendIndex", 3)
            )
            put(
                JSONObject()
                    .put("id", 1004L)
                    .put("text", "Continuous fraction item")
                    .put("createdAt", 1003L)
                    .put("accentStart", 2)
                    .put("accentEnd", 5)
                    .put("gradientBlendFraction", 0.73)
            )
        }.toString()

        prefs.edit().putString("reminders", json).commit()

        val repo = ReminderRepository(prefs)
        val items = repo.load()

        val legacyItem = items.first { it.id == 1001L }
        assertNull(legacyItem.gradientBlendFraction)
        assertEquals(0.50f, resolveGradientBlendFraction(legacyItem.gradientBlendFraction), 0.001f)

        val invalidItem = items.first { it.id == 1002L }
        assertNull(invalidItem.gradientBlendFraction)
        assertEquals(0.50f, resolveGradientBlendFraction(invalidItem.gradientBlendFraction), 0.001f)

        val validLegacyItem = items.first { it.id == 1003L }
        assertEquals(0.65f, validLegacyItem.gradientBlendFraction!!, 0.001f)
        assertEquals(0.65f, resolveGradientBlendFraction(validLegacyItem.gradientBlendFraction), 0.001f)

        val continuousItem = items.first { it.id == 1004L }
        assertEquals(0.73f, continuousItem.gradientBlendFraction!!, 0.001f)
        assertEquals(0.73f, resolveGradientBlendFraction(continuousItem.gradientBlendFraction), 0.001f)
    }

    @Test
    fun categoryFilterUnchanged() {
        val item1 = ReminderItem(id = 1L, text = "A", createdAt = 10L, accentStart = 0, accentEnd = 1, gradientBlendFraction = 0.15f)
        val item2 = ReminderItem(id = 2L, text = "B", createdAt = 20L, accentStart = 1, accentEnd = 0, gradientBlendFraction = 0.85f)
        val item3 = ReminderItem(id = 3L, text = "C", createdAt = 30L, accentStart = null, accentEnd = null)

        val list = listOf(item1, item2, item3)

        val filtered0 = filterAndSortReminders(list, colorFilter = 0)
        assertEquals(1, filtered0.size)
        assertEquals(1L, filtered0.first().id)

        val filteredDefault = filterAndSortReminders(list, colorFilter = COLOR_FILTER_DEFAULT)
        assertEquals(1, filteredDefault.size)
        assertEquals(3L, filteredDefault.first().id)
    }
}
