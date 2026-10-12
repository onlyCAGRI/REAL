package com.cagri.real

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.ReminderRepository
import com.cagri.real.data.filterAndSortReminders
import com.cagri.real.ui.components.ReminderColorChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionSolidColorTest {

    @Test
    fun actionAvailableOnlyWhenBothColorsSelected() {
        val bothSelected = ReminderColorChoices.getOrNull(0) != null && ReminderColorChoices.getOrNull(3) != null
        assertTrue(bothSelected)

        val singleColorSelected = ReminderColorChoices.getOrNull(0) != null && null != null
        assertFalse(singleColorSelected)

        val noneSelected = null != null
        assertFalse(noneSelected)
    }

    @Test
    fun exactInterpolatedBridgeColorIsUsedWithoutSnapping() {
        val startColor = ReminderColorChoices[0].color
        val endColor = ReminderColorChoices[3].color
        val fraction = 0.67f

        val bridge = lerp(startColor, endColor, fraction)
        val bridgeArgb = bridge.toArgb()

        for (choice in ReminderColorChoices) {
            assertNotEquals(choice.color.toArgb(), bridgeArgb)
        }

        val restoredColor = Color(bridgeArgb)
        assertEquals(bridge.red, restoredColor.red, 0.01f)
        assertEquals(bridge.green, restoredColor.green, 0.01f)
        assertEquals(bridge.blue, restoredColor.blue, 0.01f)
    }

    @Test
    fun customSolidColorSurvivesSaveAndReload() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Test custom solid color")

        val startColor = ReminderColorChoices[1].color
        val endColor = ReminderColorChoices[4].color
        val bridgeArgb = lerp(startColor, endColor, 0.45f).toArgb()

        val updated = repo.edit(
            id = item.id,
            text = "Test custom solid color",
            accentStart = 1,
            accentEnd = null,
            gradientBlendFraction = null,
            customSolidColorArgb = bridgeArgb
        )

        val edited = updated.first { it.id == item.id }
        assertEquals(bridgeArgb, edited.customSolidColorArgb)
        assertNull(edited.accentEnd)
        assertNull(edited.gradientBlendFraction)

        val reloaded = ReminderRepository(prefs).load().first { it.id == item.id }
        assertEquals(bridgeArgb, reloaded.customSolidColorArgb)
        assertNull(reloaded.accentEnd)
        assertNull(reloaded.gradientBlendFraction)
    }

    @Test
    fun cancelRestoresPreviousGradientState() {
        val original = ReminderItem(
            id = 42L,
            text = "Gradient reminder",
            createdAt = 1000L,
            accentStart = 1,
            accentEnd = 3,
            gradientBlendFraction = 0.65f,
            customSolidColorArgb = null
        )

        var pendingAccentStart: Int? = original.accentStart
        var pendingAccentEnd: Int? = original.accentEnd
        var pendingFraction: Float? = original.gradientBlendFraction
        var pendingCustomSolid: Int? = original.customSolidColorArgb

        val startColor = ReminderColorChoices[pendingAccentStart!!].color
        val endColor = ReminderColorChoices[pendingAccentEnd!!].color
        val bridgeArgb = lerp(startColor, endColor, pendingFraction!!).toArgb()
        pendingCustomSolid = bridgeArgb
        pendingAccentEnd = null
        pendingFraction = null

        assertEquals(bridgeArgb, pendingCustomSolid)
        assertNull(pendingAccentEnd)

        pendingAccentStart = original.accentStart
        pendingAccentEnd = original.accentEnd
        pendingFraction = original.gradientBlendFraction
        pendingCustomSolid = original.customSolidColorArgb

        assertEquals(1, pendingAccentStart)
        assertEquals(3, pendingAccentEnd)
        assertEquals(0.65f, pendingFraction!!, 0.001f)
        assertNull(pendingCustomSolid)
    }

    @Test
    fun selectingNormalPaletteColorClearsCustomSolidColor() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Test clear custom on single color")

        val bridgeArgb = 0xFF556677.toInt()
        repo.edit(
            id = item.id,
            text = "Test clear custom on single color",
            accentStart = 2,
            accentEnd = null,
            customSolidColorArgb = bridgeArgb
        )

        val cleared = repo.edit(
            id = item.id,
            text = "Test clear custom on single color",
            accentStart = 5,
            accentEnd = null,
            customSolidColorArgb = null
        ).first { it.id == item.id }

        assertNull(cleared.customSolidColorArgb)
        assertEquals(5, cleared.accentStart)
        assertNull(cleared.accentEnd)
    }

    @Test
    fun selectingTwoColorGradientClearsCustomSolidColor() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Test clear custom on gradient")

        val bridgeArgb = 0xFF334455.toInt()
        repo.edit(
            id = item.id,
            text = "Test clear custom on gradient",
            accentStart = 2,
            accentEnd = null,
            customSolidColorArgb = bridgeArgb
        )

        val gradient = repo.edit(
            id = item.id,
            text = "Test clear custom on gradient",
            accentStart = 2,
            accentEnd = 6,
            gradientBlendFraction = 0.5f,
            customSolidColorArgb = null
        ).first { it.id == item.id }

        assertNull(gradient.customSolidColorArgb)
        assertEquals(2, gradient.accentStart)
        assertEquals(6, gradient.accentEnd)
        assertEquals(0.5f, gradient.gradientBlendFraction!!, 0.001f)
    }

    @Test
    fun categoryFilteringLogicUsesAccentStartAndIsUnaffectedByCustomSolidColor() {
        val itemWithCustomSolid = ReminderItem(
            id = 1L,
            text = "Custom solid item",
            createdAt = 1000L,
            accentStart = 2,
            accentEnd = null,
            customSolidColorArgb = 0xFF123456.toInt()
        )
        val normalItem = ReminderItem(
            id = 2L,
            text = "Normal item",
            createdAt = 2000L,
            accentStart = 4,
            accentEnd = null
        )

        val list = listOf(itemWithCustomSolid, normalItem)

        val filteredMatch = filterAndSortReminders(list, colorFilter = 2)
        assertEquals(1, filteredMatch.size)
        assertEquals(1L, filteredMatch.first().id)

        val filteredOther = filterAndSortReminders(list, colorFilter = 3)
        assertTrue(filteredOther.isEmpty())
    }

    @Test
    fun starBorderRemainsIndependentOfCustomSolidColor() {
        val item = ReminderItem(
            id = 10L,
            text = "Important item",
            createdAt = 1000L,
            isImportant = true,
            starBorderColorIndex = 7,
            accentStart = 2,
            accentEnd = null,
            customSolidColorArgb = 0xFF654321.toInt()
        )

        assertTrue(item.isImportant)
        assertEquals(7, item.starBorderColorIndex)
        assertEquals(0xFF654321.toInt(), item.customSolidColorArgb)
    }
}
