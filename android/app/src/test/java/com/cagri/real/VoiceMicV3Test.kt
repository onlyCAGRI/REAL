package com.cagri.real

import com.cagri.real.speech.VoiceDraft
import com.cagri.real.speech.VoiceHypotheses
import org.junit.Assert.*
import org.junit.Test

class VoiceMicV3Test {
    @Test fun partialOnlyIsNeverFinalAndSurvivesMissingResult() {
        val draft = VoiceDraft("Önceki")
        draft.updatePartial("Silo izle")
        draft.commitResult(null)
        assertEquals("Önceki", draft.committed)
        assertEquals("Önceki Silo izle", draft.text)
        assertFalse(draft.hasSpokenText)
    }
    @Test fun finalCorrectsProperNameWithoutAppendingThePartial() {
        val draft = VoiceDraft("")
        draft.updatePartial("billi hayırlı işler şarkısını kaydet bugün")
        draft.commitResult("Billie Eilish şarkısını kaydet")
        assertEquals("Billie Eilish şarkısını kaydet", draft.committed)
        assertEquals("", draft.partial)
        assertTrue(draft.hasSpokenText)
    }
    @Test fun alternativesIncludeEarlierAndLaterRoundsWithoutConfidenceScores() {
        val hypotheses = VoiceHypotheses("Kaydet")
        hypotheses.append(listOf("Silo izle", "Silo'yu izle"))
        hypotheses.append(listOf("annemi ara", "annemi arayacağım"))
        assertTrue(hypotheses.alternatives.contains("Kaydet Silo'yu izle annemi ara"))
        assertTrue(hypotheses.alternatives.contains("Kaydet Silo izle annemi arayacağım"))
        assertTrue(hypotheses.alternatives.all { it.startsWith("Kaydet Silo") && it.contains("annemi") })
    }
    @Test fun beamIsBoundedAndNoMatchDoesNotEraseEarlierAlternatives() {
        val hypotheses = VoiceHypotheses("")
        hypotheses.append(listOf("Silo", "Silo'yu", "SİLO", "silo dizisi", "Silo serisi"))
        hypotheses.append(listOf("izle", "seyret", "izleyeceğim", "kaydet", "ekle"))
        val before = hypotheses.alternatives
        hypotheses.append(emptyList())
        assertEquals(before, hypotheses.alternatives)
        assertTrue(before.size <= 5)
    }
    @Test fun cumulativeAndDuplicateFinalsDoNotDuplicateText() {
        val draft = VoiceDraft("")
        draft.commitResult("Silo izle")
        draft.commitResult("Silo izle")
        draft.commitResult("Silo izle ve annemi ara")
        assertEquals("Silo izle ve annemi ara", draft.committed)
    }
    @Test fun cumulativeAlternativeDoesNotRepeatTheEarlierPrimary() {
        val hypotheses = VoiceHypotheses("")
        hypotheses.append(listOf("Silo izle", "Silo'yu izle"))
        hypotheses.append(listOf("Silo izle ve annemi ara"))
        assertEquals(listOf("Silo'yu izle ve annemi ara"), hypotheses.alternatives)
    }
}
