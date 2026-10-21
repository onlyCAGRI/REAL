package com.cagri.real.speech

import com.cagri.real.data.MoodSuggestionMediaCandidate
import com.cagri.real.data.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSubstitutionsTest {
    private fun intent(title: String, location: String? = null, media: String? = null, people: List<String> = emptyList()) =
        VoiceIntent<String, String>(
            title = title, date = null, time = null, recurrence = RecurrenceRule.NONE, hasSchedule = false,
            requiresTimeSelection = false, timeOfDay = null, durationMinutes = null, people = people,
            locationName = location, categoryName = null, isChecklist = false, checklistItems = emptyList(),
            media = media?.let { MoodSuggestionMediaCandidate(title = it, searchQuery = it) }
        )

    @Test fun aMisheardForeignNameIsAskedAboutEvenWhenTheAiIsSure() {
        val names = VoiceSubstitutions.names("yarın ordu vende kiçında olacağım", emptyList(),
            listOf(intent("When Kitchen'da ol", "When Kitchen, Ordu")))
        assertEquals(setOf("When", "Kitchen'da"), names.toSet())
        val shown = VoiceSubstitutions.text("When Kitchen'da ol — When Kitchen, Ordu", names)
        assertEquals(listOf("When", "Kitchen'da"), shown.spans.map { shown.text.substring(it.start, it.end) })
    }

    @Test fun wordsThatWereSaidAreNotAskedAbout() {
        assertEquals(emptyList<String>(), VoiceSubstitutions.names("yarın ordu büyükşehir belediyesinde olacağım saat 15te",
            emptyList(), listOf(intent("Belediyede ol", "Ordu Büyükşehir Belediyesi"))))
        assertEquals(emptyList<String>(), VoiceSubstitutions.names("bu akşam silo izleyeceğim", emptyList(),
            listOf(intent("Silo izle", media = "Silo"))))
        assertEquals(emptyList<String>(), VoiceSubstitutions.names("yarın when kitchen da olacağım", emptyList(),
            listOf(intent("When Kitchen'da ol", "When Kitchen"))))
    }

    @Test fun aNameFoundInAnAlternativeCountsAsSaid() {
        assertEquals(emptyList<String>(), VoiceSubstitutions.names("yarın vende olacağım", listOf("yarın when de olacağım"),
            listOf(intent("When'de ol", "When"))))
    }

    @Test fun recommendationsAreNotQuestioned() {
        assertEquals(emptyList<String>(), VoiceSubstitutions.names("bana bir film öner", emptyList(),
            listOf(intent("Arrival izle", media = "Arrival"))))
    }
}
