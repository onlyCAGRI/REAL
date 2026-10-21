package com.cagri.real.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceUnderstandingTest {
    private fun heard(vararg unsure: Pair<String, String?>) =
        VoiceHeard("", unsure.map { VoiceUnsureWord(it.first, it.second) }, confident = false)

    @Test fun unsureWordIsShownAsTheGuessAndUnderlined() {
        val t = VoiceUnderstanding.build("Cuma günü velvet statik izle", heard("velvet statik" to "Velvet Static"))
        assertEquals("Cuma günü Velvet Static izle", t.text)
        assertEquals(1, t.spans.size)
        assertEquals("Velvet Static", t.text.substring(t.spans[0].start, t.spans[0].end))
        assertEquals(listOf("Velvet Static", "velvet statik"), t.spans[0].options)
    }

    @Test fun withoutAGuessTheHeardWordStaysAndIsUnderlined() {
        val t = VoiceUnderstanding.build("Yarın Kerem ile buluş", heard("Kerem" to null))
        assertEquals("Yarın Kerem ile buluş", t.text)
        assertEquals("Kerem", t.text.substring(t.spans[0].start, t.spans[0].end))
    }

    @Test fun lowConfidenceWordsFromTheRecognizerAreUnderlinedToo() {
        val t = VoiceUnderstanding.build("Saat sekizde sayılo izle", heard(), listOf("sayılo", "olmayan"))
        assertEquals(listOf("sayılo"), t.spans.map { t.text.substring(it.start, it.end) })
    }

    @Test fun pickingAnotherOptionShiftsLaterSpans() {
        val t = VoiceUnderstanding.build("leo ile velvet statik izle", heard("leo" to "Leo", "velvet statik" to "Velvet Static"))
        val picked = VoiceUnderstanding.pick(t, 0, "Leon")
        assertEquals("Leon ile Velvet Static izle", picked.text)
        assertEquals("Velvet Static", picked.text.substring(picked.spans[1].start, picked.spans[1].end))
        assertEquals("Leon", picked.spans[0].options.first())
    }

    @Test fun overlappingWordsAreUnderlinedOnce() {
        val t = VoiceUnderstanding.build("velvet statik izle", heard("velvet statik" to "Velvet Static"), listOf("statik"))
        assertEquals(1, t.spans.size)
    }
}
