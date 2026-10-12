package com.cagri.real

import com.cagri.real.data.JournalMood
import com.cagri.real.ui.components.MoodFamiliesV2
import com.cagri.real.ui.components.familyIndexV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodV2Test {

    @Test
    fun moodFamiliesCountIsFiveAndMatchesDesignAnchors() {
        assertEquals(5, MoodFamiliesV2.size)
        assertEquals("Kötü", MoodFamiliesV2[0].title)
        assertEquals("Zor", MoodFamiliesV2[1].title)
        assertEquals("Normal", MoodFamiliesV2[2].title)
        assertEquals("İyi", MoodFamiliesV2[3].title)
        assertEquals("Çok iyi", MoodFamiliesV2[4].title)
    }

    @Test
    fun allSelectableJournalMoodsArePartitionedWithoutLegacyOrDuplicates() {
        val allFamilyMoods = MoodFamiliesV2.flatMap { it.moods }
        assertEquals(14, allFamilyMoods.size)
        assertEquals(allFamilyMoods.size, allFamilyMoods.distinct().size)
        assertFalse(allFamilyMoods.contains(JournalMood.FUCK_OFF))
        assertEquals(JournalMood.entries.filterNot { it == JournalMood.FUCK_OFF }.toSet(), allFamilyMoods.toSet())
    }

    @Test
    fun familySizesMatchExpectedChipsLayout() {
        // Legacy FUCK_OFF remains readable but is not a selectable chip.
        assertEquals(listOf(JournalMood.BAD, JournalMood.SAD, JournalMood.ANGRY), MoodFamiliesV2[0].moods)
        // HARD has 4 moods (wraps nicely in FlowRow)
        assertEquals(listOf(JournalMood.HARD, JournalMood.TIRED, JournalMood.STRESSED, JournalMood.LOW), MoodFamiliesV2[1].moods)
        // NEUTRAL has 2 moods
        assertEquals(listOf(JournalMood.NEUTRAL, JournalMood.BORED), MoodFamiliesV2[2].moods)
        // GOOD has 2 moods
        assertEquals(listOf(JournalMood.GOOD, JournalMood.CALM), MoodFamiliesV2[3].moods)
        // VERY_GOOD has 3 moods
        assertEquals(listOf(JournalMood.VERY_GOOD, JournalMood.HAPPY, JournalMood.EXCITED), MoodFamiliesV2[4].moods)
    }

    @Test
    fun familyDefaultMoodsMatchAnchors() {
        assertEquals(JournalMood.BAD, MoodFamiliesV2[0].defaultMood)
        assertEquals(JournalMood.HARD, MoodFamiliesV2[1].defaultMood)
        assertEquals(JournalMood.NEUTRAL, MoodFamiliesV2[2].defaultMood)
        assertEquals(JournalMood.GOOD, MoodFamiliesV2[3].defaultMood)
        assertEquals(JournalMood.VERY_GOOD, MoodFamiliesV2[4].defaultMood)
    }

    @Test
    fun familyIndexMappingWorksCorrectlyForEveryMood() {
        assertEquals(0, JournalMood.BAD.familyIndexV2())
        assertEquals(0, JournalMood.SAD.familyIndexV2())
        assertEquals(0, JournalMood.ANGRY.familyIndexV2())
        assertEquals(0, JournalMood.FUCK_OFF.familyIndexV2())

        assertEquals(1, JournalMood.HARD.familyIndexV2())
        assertEquals(1, JournalMood.TIRED.familyIndexV2())
        assertEquals(1, JournalMood.STRESSED.familyIndexV2())
        assertEquals(1, JournalMood.LOW.familyIndexV2())

        assertEquals(2, JournalMood.NEUTRAL.familyIndexV2())
        assertEquals(2, JournalMood.BORED.familyIndexV2())

        assertEquals(3, JournalMood.GOOD.familyIndexV2())
        assertEquals(3, JournalMood.CALM.familyIndexV2())

        assertEquals(4, JournalMood.VERY_GOOD.familyIndexV2())
        assertEquals(4, JournalMood.HAPPY.familyIndexV2())
        assertEquals(4, JournalMood.EXCITED.familyIndexV2())
    }

    @Test
    fun systemBarLuminanceSeparatesVeryGoodFromDarkFamilies() {
        // Luminance formula used in MoodV2FullScreen:
        // topLuminance = (0.2126f * r + 0.7152f * g + 0.0722f * b) * 0.42f
        fun isLightStatus(color: androidx.compose.ui.graphics.Color): Boolean {
            val r = color.red
            val g = color.green
            val b = color.blue
            val topLuminance = (0.2126f * r + 0.7152f * g + 0.0722f * b) * 0.42f
            return topLuminance > 0.32f
        }

        // VERY_GOOD lime/yellow has high luminance -> light background -> dark icons (isLightStatus = true)
        assertTrue("VERY_GOOD tint must trigger dark system bar icons", isLightStatus(MoodFamiliesV2[4].tint))

        // BAD, HARD, NEUTRAL, GOOD have darker effective luminance -> dark background -> light icons (isLightStatus = false)
        assertFalse("BAD tint must keep light system bar icons", isLightStatus(MoodFamiliesV2[0].tint))
        assertFalse("HARD tint must keep light system bar icons", isLightStatus(MoodFamiliesV2[1].tint))
        assertFalse("NEUTRAL tint must keep light system bar icons", isLightStatus(MoodFamiliesV2[2].tint))
        assertFalse("GOOD tint must keep light system bar icons", isLightStatus(MoodFamiliesV2[3].tint))
    }
}
