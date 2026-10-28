package com.cagri.real

import com.cagri.real.data.*
import com.cagri.real.network.*
import com.cagri.real.platform.android.network.toJson
import com.cagri.real.speech.ChecklistParser
import com.cagri.real.speech.TimeOfDay
import com.cagri.real.speech.VoiceLastCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import com.cagri.real.speech.interpretHour
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PortableBoundaryTest {
    @Test fun rollingWindowKeepsInclusiveAlertAndExclusiveQuickExpiry() {
        val now = 1000L
        assertTrue(CategoryRules.isIn24HourWindow(false, now + 86_400_000L, null, null, now, 10L))
        assertFalse(CategoryRules.isIn24HourWindow(false, now + 86_400_001L, null, null, now, 10L))
        assertFalse(CategoryRules.isQuick24HourActive(false, now, now))
        assertTrue(CategoryRules.isQuick24HourActive(false, now + 1, now))
        assertFalse(CategoryRules.isIn24HourWindow(true, now, 10L, now + 1, now, 10L))
        assertTrue(CategoryRules.isIn24HourWindow(false, null, 10L, null, now, 10L))
    }

    @Test fun expiredQuickEntryReturnsToNoAlertUnlessAssigned() {
        assertTrue(CategoryRules.isNoAlert(false, null, null, 100L, null, 100L))
        assertFalse(CategoryRules.isNoAlert(false, null, null, 100L, 1L, 100L))
        assertFalse(CategoryRules.isNoAlert(false, null, 2L, null, null, 100L))
    }

    @Test fun moodBackdatedEntryKeepsDateAndMonotonicId() {
        val previous = listOf(MoodTrackerEntry(900L, JournalMood.GOOD, MoodDetails(loggedAt = 300L)))
        val result = MoodTrackerRules.add(previous, JournalMood.HARD, MoodDetails(loggedAt = 200L), 100L)
        assertEquals(listOf(900L, 901L), result.map { it.id })
        assertEquals(200L, result.last().details.loggedAt)
        assertEquals(100L, MoodTrackerRules.add(emptyList(), JournalMood.BAD, MoodDetails(loggedAt = 0L), 100L).single().details.loggedAt)
    }

    @Test fun moodRetentionStillKeepsNewest730Entries() {
        val previous = (1L..730L).map { MoodTrackerEntry(it, JournalMood.GOOD, MoodDetails(loggedAt = it)) }
        val result = MoodTrackerRules.add(previous, JournalMood.BAD, MoodDetails(loggedAt = 731L), 731L)
        assertEquals(730, result.size)
        assertEquals(731L, result.first().details.loggedAt)
        assertEquals(2L, result.last().details.loggedAt)
    }

    @Test fun checklistUsesTurkishCaseAndPreservesFinalConjunctionRule() {
        val tr = Locale.forLanguageTag("tr-TR")
        val parser = ChecklistParser(
            lowercase = { it.lowercase(tr) },
            titlecase = { value -> value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(tr) else it.toString() } }
        )
        val result = parser.extractChecklist("alışveriş listesi oluştur süt, yumurta ve ıspanak")!!
        assertEquals("Alışveriş listesi", result.title)
        assertEquals(listOf("Süt", "Yumurta", "Ispanak"), result.items)
        assertEquals(0, interpretHour(12, TimeOfDay.Night))
        assertEquals(19, interpretHour(7))
    }

    @Test fun requestSerializationPreservesLimitsAndWireKeys() {
        val voice = VoiceIntentRequest("  süt al  ", listOf("süt al", " SÜT AL ", "ekmek al", "EKMEK AL"),
            "2026-09-29T20:00:00+03:00", "Europe/Istanbul", "tr-TR", (1..51).map { "cat$it" })
        val json = JSONObject(voice.toJson())
        assertEquals("voice_composer", json.getString("source"))
        assertEquals("süt al", json.getString("transcript"))
        assertEquals(1, json.getJSONArray("transcriptAlternatives").length())
        assertEquals(50, json.getJSONArray("categories").length())
        assertEquals("Europe/Istanbul", json.getString("timezone"))
        val initial = JSONObject(MoodSuggestionRequest("GOOD", emptyList(), emptyList()).toJson())
        assertFalse(initial.has("replaceKind"))
        assertFalse(initial.has("exclude"))
        val replacement = JSONObject(MoodSuggestionRequest("GOOD", emptyList(), emptyList(), MoodSuggestionKind.MUSIC,
            listOf(" ") + (1..20).map { " song$it " }, language = "en").toJson())
        assertEquals(16, replacement.getJSONArray("exclude").length())
        assertEquals("song1", replacement.getJSONArray("exclude").getString(0))
        assertEquals("en", replacement.getString("language"))
    }

    @Test fun voiceRequestOmitsLastCardUnlessGivenAndKeepsItsWireKeys() {
        val plain = JSONObject(VoiceIntentRequest("x", emptyList(), "2026-09-29T20:00:00+03:00", "Europe/Istanbul", "tr-TR", emptyList()).toJson())
        assertFalse(plain.has("lastCard"))
        val card = VoiceLastCard("Annemi ara", null, "2026-09-30", "Kişisel", null, "Her Pzt")
        val withCard = JSONObject(VoiceIntentRequest("onu 8'e al", emptyList(), "2026-09-29T20:00:00+03:00", "Europe/Istanbul", "tr-TR", emptyList(), card).toJson())
        val sent = withCard.getJSONObject("lastCard")
        assertEquals("Annemi ara", sent.getString("title"))
        assertEquals("", sent.getString("alertAt"))
        assertEquals("2026-09-30", sent.getString("plannedDate"))
        assertEquals("Kişisel", sent.getString("categoryName"))
    }

    @Test fun privateVoiceOmitsSavedPlaceWhileExplicitTranscriptRemains() {
        val card = VoiceLastCard("Annemi ara", null, null, null, "SECRET_SAVED_PLACE", null)
        val wire = JSONObject(VoiceIntentRequest("Konumu Ankara yap", emptyList(), "2026-10-09T10:00:00+03:00", "Europe/Istanbul", "tr-TR", emptyList(), card).toJson())
        assertFalse(wire.getJSONObject("lastCard").has("locationName"))
        assertEquals("Konumu Ankara yap", wire.getString("transcript"))
        assertFalse(wire.toString().contains("SECRET_SAVED_PLACE"))
    }

    @Test fun moodInitialRequestCarriesBoundedMusicHistoryWithoutPrivateFields() {
        val wire = JSONObject(MoodSuggestionRequest("GOOD", emptyList(), emptyList(),
            excludeMusic = (1..50).map { "Artist Track$it" }).toJson())
        assertEquals(32, wire.getJSONArray("excludeMusic").length())
        assertEquals("Artist Track19", wire.getJSONArray("excludeMusic").getString(0))
        assertFalse(wire.has("uid"))
        assertFalse(wire.has("recentCards"))
    }

    @Test fun weekdayRepeatFollowsPatternAndStopsAtEndDate() {
        val zone = ZoneId.of("Europe/Istanbul")
        val mask = weekdaysToMask(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        fun at(d: LocalDate) = d.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val start = at(LocalDate.of(2026, 9, 30))                       // Wednesday
        val until = LocalDate.of(2026, 10, 30).toEpochDay()
        assertEquals(at(LocalDate.of(2026, 10, 2)), computeNextRepeat(start, RecurrenceRule.WEEKLY, mask, until, zone, start))
        assertEquals(at(LocalDate.of(2026, 10, 5)), computeNextRepeat(start, RecurrenceRule.WEEKLY, mask, until, zone, at(LocalDate.of(2026, 10, 2))))
        assertEquals(at(LocalDate.of(2026, 10, 30)), computeNextRepeat(start, RecurrenceRule.WEEKLY, mask, until, zone, at(LocalDate.of(2026, 10, 28))))
        assertNull(computeNextRepeat(start, RecurrenceRule.WEEKLY, mask, until, zone, at(LocalDate.of(2026, 10, 30))))
        // Without a mask or end date the classic weekly rule is unchanged.
        assertEquals(computeNextOccurrence(start, RecurrenceRule.WEEKLY, zone, start), computeNextRepeat(start, RecurrenceRule.WEEKLY, 0, null, zone, start))
    }

    @Test fun workerPreservesErrorResponseAndCallerTimeouts() = runBlocking {
        var sent: HttpRequest? = null
        val client = WorkerClient(HttpTransport { request -> sent = request; HttpResponse(503, "busy") })
        assertEquals(HttpResponse(503, "busy"), client.postJson("https://example.invalid/api", "{}", 5000, 12000))
        assertFalse(sent!!.headers.containsKey("Authorization"))
        assertEquals("application/json; charset=UTF-8", sent!!.headers["Content-Type"])
        assertEquals(5000, sent!!.connectTimeoutMillis)
        assertEquals(12000, sent!!.readTimeoutMillis)
        client.postJson("https://example.invalid/api", "{}", 15000, 15000, false)
        assertFalse(sent!!.headers.containsKey("Authorization"))
        assertFalse(sent!!.readErrorBody)
    }
}
