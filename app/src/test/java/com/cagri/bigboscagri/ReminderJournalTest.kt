package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.JournalMood
import com.cagri.bigboscagri.data.ReminderItem
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
        
        // Complete the reminder
        repo.toggleCompleted(reminder.id)
        assertTrue(repo.load().first().isCompleted)
        
        // Add journal
        repo.setJournal(reminder.id, JournalMood.VERY_GOOD, "Tamamladım!")
        assertNotNull(repo.load().first().journal)
        
        // Restore reminder
        repo.toggleCompleted(reminder.id)
        val activeReminder = repo.load().first()
        assertTrue(!activeReminder.isCompleted)
        assertNotNull(activeReminder.journal)
        assertEquals("Tamamladım!", activeReminder.journal?.text)
    }
}
