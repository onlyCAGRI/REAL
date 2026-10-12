package com.cagri.real

import com.cagri.real.data.RecurrenceRule
import com.cagri.real.speech.GeminiVoiceIntentClient
import com.cagri.real.speech.TimeOfDay
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class GeminiVoiceIntentClientTest {
    @Test
    fun parsesStructuredVoiceIntent() {
        val json = JSONObject()
            .put("title", "Ahmet'le toplantı")
            .put("date", "2026-09-28")
            .put("time", "15:00")
            .put("recurrence", "WEEKLY")
            .put("hasSchedule", true)
            .put("requiresTimeSelection", false)
            .put("timeOfDay", "NONE")
            .put("durationMinutes", 45)
            .put("people", JSONArray().put("Ahmet"))
            .put("locationName", "Ordu")
            .put("categoryName", "İş")
            .put("isChecklist", false)
            .put("checklistItems", JSONArray())

        val intent = GeminiVoiceIntentClient.parseIntentObject(json, "fallback")

        assertEquals("Ahmet'le toplantı", intent.title)
        assertEquals(LocalDate.of(2026, 9, 28), intent.date)
        assertEquals(LocalTime.of(15, 0), intent.time)
        assertEquals(RecurrenceRule.WEEKLY, intent.recurrence)
        assertEquals(45, intent.durationMinutes)
        assertEquals(listOf("Ahmet"), intent.people)
        assertEquals("Ordu", intent.locationName)
        assertEquals("İş", intent.categoryName)
        assertTrue(intent.hasSchedule)
        assertFalse(intent.requiresTimeSelection)
    }

    @Test
    fun keepsVagueDaypartForUiSelection() {
        val json = JSONObject()
            .put("title", "Sinemaya git")
            .put("date", "2026-09-28")
            .put("time", "")
            .put("recurrence", "NONE")
            .put("hasSchedule", true)
            .put("requiresTimeSelection", true)
            .put("timeOfDay", "EVENING")
            .put("durationMinutes", 0)
            .put("people", JSONArray())
            .put("locationName", "")
            .put("categoryName", "")
            .put("isChecklist", false)
            .put("checklistItems", JSONArray())

        val intent = GeminiVoiceIntentClient.parseIntentObject(json, "fallback")

        assertEquals(TimeOfDay.Evening, intent.timeOfDay)
        assertTrue(intent.requiresTimeSelection)
        assertEquals(null, intent.time)
    }

    @Test
    fun skipOccurrenceActionCarriesTheNamedDay() {
        val action = GeminiVoiceIntentClient.parseActionObject(
            JSONObject().put("type", "SKIP_OCCURRENCE").put("targetId", "3").put("occurrenceDate", "2026-10-07")
        )
        assertEquals(com.cagri.real.speech.VoiceActionType.SKIP_OCCURRENCE, action?.type)
        assertEquals(3L, action?.targetId)
        assertEquals(LocalDate.of(2026, 10, 7).toEpochDay(), action?.occurrenceEpochDay)
    }

    @Test
    fun skipOccurrenceWithoutOrWithBadDateFallsBackToUpcoming() {
        for (date in listOf("", "Çarşamba", "2026-02-30")) {
            val action = GeminiVoiceIntentClient.parseActionObject(
                JSONObject().put("type", "SKIP_OCCURRENCE").put("targetId", "3").put("occurrenceDate", date)
            )
            assertEquals(null, action?.occurrenceEpochDay)
        }
    }

    @Test
    fun occurrenceDateIsIgnoredForOtherActions() {
        val action = GeminiVoiceIntentClient.parseActionObject(
            JSONObject().put("type", "DELETE").put("targetId", "3").put("occurrenceDate", "2026-10-07")
        )
        assertEquals(null, action?.occurrenceEpochDay)
    }
}
