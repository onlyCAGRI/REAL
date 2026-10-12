package com.cagri.real.ai

import org.junit.Assert.*
import org.junit.Test

class MoodMusicHistoryTest {
    @Test fun recencyMovesDuplicatesAndPreservesDifferentSongs() {
        assertEquals(listOf("Other song", "ARTIST – SONG"),
            MoodMusicHistory.merge(listOf("Artist Song", "Other song"), listOf("ARTIST – SONG")))
    }
    @Test fun boundedHistoryAndRequestKeepTheNewestSongs() {
        val history = MoodMusicHistory.merge(emptyList(), (1..100).map { "Artist Track$it" })
        assertEquals(64, history.size)
        assertEquals("Artist Track37", history.first())
        assertEquals("Artist Track100", history.last())
        val sent = MoodMusicHistory.exclusions(history)
        assertEquals(32, sent.size)
        assertEquals("Artist Track69", sent.first())
    }
    @Test fun turkishCaseAndPunctuationDoNotDuplicateSongs() {
        assertEquals(1, MoodMusicHistory.merge(listOf("Çağrı Şarkı"), listOf("CAGRI SARKI")).size)
    }
    @Test fun providerErrorsDoNotExposeInternalCodes() {
        assertFalse(aiServiceFailureMessage(503, "PRIVATE_AI_NOT_CONFIGURED").contains("PRIVATE_AI"))
        assertFalse(aiServiceFailureMessage(502, "SECRET_STACK_TRACE").contains("SECRET"))
        assertTrue(aiServiceFailureMessage(429, "QUOTA_LIMIT").contains("sınır"))
    }
    @Test fun emptyOrMalformedHistoryDoesNotProduceExclusions() {
        assertTrue(MoodMusicHistory.merge(listOf("", " -- "), listOf(" ")).isEmpty())
    }
}
