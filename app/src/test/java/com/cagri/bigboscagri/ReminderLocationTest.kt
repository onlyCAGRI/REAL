package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.ChecklistItem
import com.cagri.bigboscagri.data.ReminderAttachment
import com.cagri.bigboscagri.data.ReminderLocation
import com.cagri.bigboscagri.data.ReminderRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderLocationTest {
    @Test fun oldRemindersWithoutLocationStillLoad() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("reminders", JSONArray().put(JSONObject()
            .put("id", 42L).put("text", "Eski kayıt").put("createdAt", 1000L)).toString()).commit()
        assertNull(ReminderRepository(prefs).load().single().location)
    }

    @Test fun mapsLinkOnlyPersistsWithoutCoordinates() {
        val repository = ReminderRepository(FakeSharedPreferences())
        val location = ReminderLocation(placeName = "Samsun", mapsUrl = "https://maps.app.goo.gl/xyz")
        repository.add("Buluşma", location = location)
        assertEquals(location, repository.load().single().location)
    }

    @Test fun existingLinkOnlyLocationCanGainApproximatePreviewWithoutChangingReminder() {
        val repository = ReminderRepository(FakeSharedPreferences())
        val link = "https://maps.app.goo.gl/xyz"
        val saved = repository.add("Buluşma", location = ReminderLocation(
            placeName = "Atabeyoğlu Lezzet Çiftliği", mapsUrl = link
        ))
        val resolved = ReminderLocation(placeName = "Wrong name", latitude = 41.0,
            longitude = 36.0, approximateCoordinates = true, mapsUrl = link)
        repository.enrichLocationCoordinates(saved.id, link, resolved)
        val updated = repository.load().single()
        assertEquals(saved.text, updated.text)
        assertEquals(saved.updatedAt, updated.updatedAt)
        assertEquals("Atabeyoğlu Lezzet Çiftliği", updated.location?.placeName)
        assertEquals(41.0, updated.location?.latitude!!, 0.000001)
        assertEquals(true, updated.location?.approximateCoordinates)
        repository.enrichLocationCoordinates(saved.id, "https://maps.app.goo.gl/other", resolved)
        assertEquals(updated, repository.load().single())
    }

    @Test fun selectedPlacePersistsAndRemovingItKeepsOtherFields() {
        val prefs = FakeSharedPreferences()
        val repository = ReminderRepository(prefs)
        val place = ReminderLocation("google-place-id", "Starbucks Piazza", "Canik, Samsun", 41.28, 36.32)
        val attachment = ReminderAttachment("photo", "/photo.jpg")
        val item = repository.add("Ahmet ile buluş", alertAt = System.currentTimeMillis() + 86_400_000L, customCategoryId = 11L,
            isChecklist = true, checklistItems = listOf(ChecklistItem(1L, "Hediye al")),
            attachments = listOf(attachment), location = place)
        assertEquals(place, ReminderRepository(prefs).load().single().location)

        repository.edit(item.id, item.text, updateCategory = false, location = null)
        assertEquals(place, repository.load().single().location)

        repository.edit(item.id, item.text, updateCategory = false, location = null, updateLocation = true)
        val remaining = ReminderRepository(prefs).load().single()
        assertNull(remaining.location)
        assertEquals(item.text, remaining.text)
        assertEquals(item.alertAt, remaining.alertAt)
        assertEquals(11L, remaining.customCategoryId)
        assertEquals(item.checklistItems, remaining.checklistItems)
        assertEquals(listOf(attachment), remaining.attachments)
    }
}
