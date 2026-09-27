package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.JournalMood
import com.cagri.bigboscagri.data.ReminderRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderJournalTest {

    @Test
    fun legacyReminderJsonWithoutJournalLoadsWithNullJournal() {
        val prefs = FakeSharedPreferences()
        val json = JSONArray().apply {
            put(
                JSONObject()
                    .put("id", 1001L)
                    .put("text", "Eski hatırlatıcı")
                    .put("createdAt", 1000L)
            )
        }
        prefs.edit().putString("reminders", json.toString()).commit()

        val repo = ReminderRepository(prefs)
        val items = repo.load()
        assertEquals(1, items.size)
        assertNull(items[0].journal)
    }

    @Test
    fun legacyJournalWithoutSpecificMoodPreservesGeneralMood() {
        val prefs = FakeSharedPreferences()
        val json = JSONArray().apply {
            put(JSONObject()
                .put("id", 1001L)
                .put("text", "Eski günlük")
                .put("createdAt", 1000L)
                .put("journal", JSONObject()
                    .put("mood", "good")
                    .put("text", "Eski not")
                    .put("createdAt", 1000L)))
        }
        prefs.edit().putString("reminders", json.toString()).commit()

        val journal = ReminderRepository(prefs).load().first().journal
        assertEquals(JournalMood.GOOD, journal?.mood)
        assertEquals("Eski not", journal?.text)
    }

    @Test
    fun phase4aDualMoodLoadsAsSingleSpecificChoiceAndRewritesWithoutSecondaryField() {
        val prefs = FakeSharedPreferences()
        val json = JSONArray().put(JSONObject()
            .put("id", 1001L).put("text", "Phase 4A günlüğü").put("createdAt", 1000L)
            .put("journal", JSONObject().put("mood", "good")
                .put("specificMood", "calm").put("text", "Not").put("createdAt", 1000L)))
        prefs.edit().putString("reminders", json.toString()).commit()
        val repo = ReminderRepository(prefs)
        assertEquals(JournalMood.CALM, repo.load().single().journal?.mood)
        repo.setJournal(1001L, JournalMood.CALM, "Not")
        val stored = JSONArray(prefs.getString("reminders", "[]")).getJSONObject(0).getJSONObject("journal")
        assertEquals("calm", stored.getString("mood"))
        assertTrue(!stored.has("specificMood"))
    }

    @Test
    fun unknownPhase4aSecondaryChoiceFallsBackToOriginalMood() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("reminders", JSONArray().put(JSONObject()
            .put("id", 1002L).put("text", "Eski günlük").put("createdAt", 1000L)
            .put("journal", JSONObject().put("mood", "bad").put("specificMood", "unknown")
                .put("text", "Not").put("createdAt", 1000L))).toString()).commit()
        assertEquals(JournalMood.BAD, ReminderRepository(prefs).load().single().journal?.mood)
    }

    @Test
    fun everyNewChoiceRoundTripsInOriginalMoodField() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val reminder = repo.add("Bugün")
        val newChoices = JournalMood.entries.drop(5)
        assertEquals(10, newChoices.size)
        newChoices.forEach { mood ->
            repo.setJournal(reminder.id, mood, "Not")
            val stored = JSONArray(prefs.getString("reminders", "[]")).getJSONObject(0).getJSONObject("journal")
            assertEquals(mood.persistKey, stored.getString("mood"))
            assertTrue(!stored.has("specificMood"))
            assertEquals(mood, ReminderRepository(prefs).load().single().journal?.mood)
        }
        repo.setJournal(reminder.id, null, "Not")
        assertNull(ReminderRepository(prefs).load().single().journal?.mood)
    }

    @Test
    fun journalSaveAndReload() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val reminder = repo.add("Yeni hatırlatıcı")
        
        repo.setJournal(reminder.id, JournalMood.GOOD, "Her şey yolunda.")
        
        val loaded = repo.load().first()
        assertNotNull(loaded.journal)
        assertEquals(JournalMood.GOOD, loaded.journal?.mood)
        assertEquals("Her şey yolunda.", loaded.journal?.text)
    }

    @Test
    fun journalEdit() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val reminder = repo.add("Toplantı")
        
        repo.setJournal(reminder.id, JournalMood.HARD, "Biraz zor geçti.")
        var loaded = repo.load().first()
        assertEquals(JournalMood.HARD, loaded.journal?.mood)
        
        repo.setJournal(reminder.id, JournalMood.VERY_GOOD, "Aslında çok iyiydi.")
        loaded = repo.load().first()
        assertEquals(JournalMood.VERY_GOOD, loaded.journal?.mood)
        assertEquals("Aslında çok iyiydi.", loaded.journal?.text)
    }

    @Test
    fun journalDelete() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val reminder = repo.add("Buluşma")
        
        repo.setJournal(reminder.id, JournalMood.NEUTRAL, "Normal bir gündü.")
        assertNotNull(repo.load().first().journal)
        
        repo.deleteJournal(reminder.id)
        assertNull(repo.load().first().journal)
    }

    @Test
    fun moodOnlyJournalPersistence() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val reminder = repo.add("Sadece mod")
        
        repo.setJournal(reminder.id, JournalMood.BAD, "")
        val loaded = repo.load().first()
        assertNotNull(loaded.journal)
        assertEquals(JournalMood.BAD, loaded.journal?.mood)
        assertEquals("", loaded.journal?.text)
    }

    @Test
    fun textOnlyJournalPersistence() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val reminder = repo.add("Sadece metin")
        
        repo.setJournal(reminder.id, null, "Bugün mod girmedim.")
        val loaded = repo.load().first()
        assertNotNull(loaded.journal)
        assertNull(loaded.journal?.mood)
        assertEquals("Bugün mod girmedim.", loaded.journal?.text)
    }

    @Test
    fun restoringCompletedReminderDoesNotDeleteJournal() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val reminder = repo.add("Görev")
        
        
        repo.toggleCompleted(reminder.id)
        assertTrue(repo.load().first().isCompleted)
        
        
        repo.setJournal(reminder.id, JournalMood.VERY_GOOD, "Tamamladım!")
        assertNotNull(repo.load().first().journal)
        
        
        repo.toggleCompleted(reminder.id)
        val activeReminder = repo.load().first()
        assertTrue(!activeReminder.isCompleted)
        assertNotNull(activeReminder.journal)
        assertEquals("Tamamladım!", activeReminder.journal?.text)
    }
}
