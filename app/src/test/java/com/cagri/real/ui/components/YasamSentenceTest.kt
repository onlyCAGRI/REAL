package com.cagri.real.ui.components

import com.cagri.real.data.JournalMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YasamSentenceTest {
    @Test fun greetingFollowsHour() {
        assertEquals("Günaydın", YasamSentence.greeting(8))
        assertEquals("İyi günler", YasamSentence.greeting(14))
        assertEquals("İyi akşamlar", YasamSentence.greeting(21))
        assertEquals("İyi akşamlar", YasamSentence.greeting(2))
    }

    @Test fun sceneFollowsHour() {
        assertEquals(0, YasamSentence.activeScene(8))
        assertEquals(1, YasamSentence.activeScene(21))
        assertEquals(2, YasamSentence.activeScene(14))
    }

    @Test fun litresUseTurkishDecimalComma() {
        assertEquals("1,8", YasamSentence.litres(1800))
        assertEquals("2", YasamSentence.litres(2000))
        assertEquals("0", YasamSentence.litres(0))
    }

    @Test fun sentenceUsesOnlyAvailableParts() {
        assertEquals("Dün 1,8 L su içtin,\nruh halin iyiydi.", YasamSentence.build(1800, JournalMood.GOOD))
        assertEquals("Dün 750 ml su içtin.", YasamSentence.build(750, null))
        assertEquals("Dün ruh halin sakindi.", YasamSentence.build(0, JournalMood.CALM))
        assertEquals("Dün ruh halin sakindi.", YasamSentence.build(null, JournalMood.CALM))
        assertNull(YasamSentence.build(null, null))
        assertNull(YasamSentence.build(0, null))
    }
}
