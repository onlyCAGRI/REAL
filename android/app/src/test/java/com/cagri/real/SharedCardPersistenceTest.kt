package com.cagri.real

import com.cagri.real.data.ReminderRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SharedCardPersistenceTest {
    @Test fun senderAttributionSurvivesLocalEditAndPersistence() {
        val preferences = FakeSharedPreferences()
        preferences.edit().putString("reminders", JSONArray().put(JSONObject()
            .put("id", 101L).put("text", "Shared shopping list").put("createdAt", 1000L)
            .put("sentBy", "Çağrı").put("shareOrigin", "11111111-1111-4111-8111-111111111111")).toString()).commit()
        val repository = ReminderRepository(preferences)
        assertEquals("Çağrı", repository.load().single().sentBy)
        repository.toggleCompleted(101L)
        val reloaded = ReminderRepository(preferences).load().single()
        assertEquals("Çağrı", reloaded.sentBy)
        assertEquals("11111111-1111-4111-8111-111111111111", reloaded.shareOrigin)
        assertTrue(reloaded.isCompleted)
    }
    @Test fun legacyCardsDoNotGainSharingFieldsOrAttribution() {
        val preferences = FakeSharedPreferences()
        preferences.edit().putString("reminders", JSONArray().put(JSONObject()
            .put("id", 101L).put("text", "Existing card").put("createdAt", 1000L)).toString()).commit()
        val repository = ReminderRepository(preferences)
        assertNull(repository.load().single().sentBy)
        assertNull(repository.load().single().shareOrigin)
        repository.toggleCompleted(101L)
        val saved = JSONArray(preferences.getString("reminders", "[]")).getJSONObject(0)
        assertFalse(saved.has("sentBy"))
        assertFalse(saved.has("shareOrigin"))
    }
    @Test fun receivedCardDeleteDoesNotAffectOtherCards() {
        val preferences = FakeSharedPreferences()
        val rows = JSONArray()
        for (id in listOf(101L, 102L)) rows.put(JSONObject()
            .put("id", id).put("text", "Card $id").put("createdAt", 1000L)
            .apply { if (id == 101L) put("sentBy", "Çağrı") })
        preferences.edit().putString("reminders", rows.toString()).commit()
        val repository = ReminderRepository(preferences)
        repository.delete(101L)
        assertEquals(102L, repository.load().single().id)
    }
}
