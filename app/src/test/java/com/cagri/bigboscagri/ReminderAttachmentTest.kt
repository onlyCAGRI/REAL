package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.AttachmentStorage
import com.cagri.bigboscagri.data.ChecklistItem
import com.cagri.bigboscagri.data.ReminderAttachment
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.ReminderRepository
import com.cagri.bigboscagri.data.buildReminderShareText
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReminderAttachmentTest {

    @Test
    fun legacyReminderJsonWithoutAttachmentsLoadsWithEmptyList() {
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
        assertTrue(items[0].attachments.isEmpty())
    }

    @Test
    fun maxThreeImagesEnforcement() {
        val allUris = listOf("uri1", "uri2", "uri3", "uri4", "uri5")
        val currentAttachments = emptyList<ReminderAttachment>()
        val slotsRemaining = 3 - currentAttachments.size
        val accepted = allUris.take(slotsRemaining)
        assertEquals(3, accepted.size)
        assertEquals(listOf("uri1", "uri2", "uri3"), accepted)

        val existingTwo = listOf(
            ReminderAttachment("1", "/path/1.jpg"),
            ReminderAttachment("2", "/path/2.jpg")
        )
        val slotsRemaining2 = 3 - existingTwo.size
        val accepted2 = allUris.take(slotsRemaining2)
        assertEquals(1, accepted2.size)
        assertEquals(listOf("uri1"), accepted2)
    }

    @Test
    fun serializationAndDeserializationOfReminderAttachment() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val attachments = listOf(
            ReminderAttachment(id = "att_1", localPath = "/data/user/0/app/files/reminder_attachments/att_1.jpg"),
            ReminderAttachment(id = "att_2", localPath = "/data/user/0/app/files/reminder_attachments/att_2.png")
        )
        val item = repo.add(
            text = "Fotoğraflı hatırlatıcı",
            attachments = attachments
        )
        assertEquals(2, item.attachments.size)
        assertEquals("att_1", item.attachments[0].id)
        assertEquals("/data/user/0/app/files/reminder_attachments/att_1.jpg", item.attachments[0].localPath)

        val reloadedRepo = ReminderRepository(prefs)
        val loadedItems = reloadedRepo.load()
        val loadedItem = loadedItems.first { it.id == item.id }
        assertEquals(2, loadedItem.attachments.size)
        assertEquals("att_1", loadedItem.attachments[0].id)
        assertEquals("/data/user/0/app/files/reminder_attachments/att_1.jpg", loadedItem.attachments[0].localPath)
        assertEquals("att_2", loadedItem.attachments[1].id)
        assertEquals("/data/user/0/app/files/reminder_attachments/att_2.png", loadedItem.attachments[1].localPath)
    }

    @Test
    fun editRemoveAttachment() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val initialAttachments = listOf(
            ReminderAttachment("att_1", "/path/1.jpg"),
            ReminderAttachment("att_2", "/path/2.jpg"),
            ReminderAttachment("att_3", "/path/3.jpg")
        )
        val item = repo.add("Üç fotoğraflı", attachments = initialAttachments)
        assertEquals(3, item.attachments.size)

        val keptAttachments = listOf(initialAttachments[0], initialAttachments[2])
        val updated = repo.edit(item.id, "İki fotoğraflı kaldı", attachments = keptAttachments)
        val edited = updated.first { it.id == item.id }
        assertEquals(2, edited.attachments.size)
        assertEquals("att_1", edited.attachments[0].id)
        assertEquals("att_3", edited.attachments[1].id)

        val reloaded = ReminderRepository(prefs).load().first { it.id == item.id }
        assertEquals(2, reloaded.attachments.size)
        assertEquals("att_1", reloaded.attachments[0].id)
        assertEquals("att_3", reloaded.attachments[1].id)
    }

    @Test
    fun cancelEditRestoresOriginalAttachments() {
        val originalAttachments = listOf(
            ReminderAttachment("att_1", "/path/1.jpg")
        )
        val reminder = ReminderItem(
            id = 1L,
            text = "Orijinal",
            createdAt = 1000L,
            attachments = originalAttachments
        )

        var editedAttachments = reminder.attachments
        var newlyAddedAttachments = listOf(
            ReminderAttachment("att_new", "/path/new.jpg")
        )
        editedAttachments = editedAttachments + newlyAddedAttachments
        assertEquals(2, editedAttachments.size)

        val finishEditing = {
            newlyAddedAttachments = emptyList()
            editedAttachments = reminder.attachments
        }
        finishEditing()

        assertEquals(1, editedAttachments.size)
        assertEquals("att_1", editedAttachments[0].id)
        assertTrue(newlyAddedAttachments.isEmpty())
    }

    @Test
    fun deleteReminderCleansUpAttachmentFiles() {
        val tempFile = File.createTempFile("att_test_", ".jpg")
        tempFile.writeText("test image data")
        assertTrue(tempFile.exists())

        val deleted = AttachmentStorage.deleteAttachmentFile(tempFile.absolutePath)
        assertTrue(deleted)
        assertFalse(tempFile.exists())

        val tempFile2 = File.createTempFile("att_test_2_", ".png")
        tempFile2.writeText("test image data 2")
        assertTrue(tempFile2.exists())

        AttachmentStorage.deleteAttachmentFilePaths(listOf(tempFile2.absolutePath))
        assertFalse(tempFile2.exists())
    }

    @Test
    fun checklistAndReminderSharingTextRemainsUnchanged() {
        val reminderWithAttachments = ReminderItem(
            id = 10L,
            text = "Alışveriş Listesi",
            createdAt = 1000L,
            isChecklist = true,
            checklistItems = listOf(
                ChecklistItem(1L, "Elma", null),
                ChecklistItem(2L, "Armut", 1050L)
            ),
            attachments = listOf(
                ReminderAttachment("att_1", "/path/att_1.jpg"),
                ReminderAttachment("att_2", "/path/att_2.jpg")
            )
        )
        val shareText = buildReminderShareText(reminderWithAttachments)
        val expected = "Alışveriş Listesi\n\n☐ Elma\n☑ Armut"
        assertEquals(expected, shareText)

        val simpleReminder = ReminderItem(
            id = 11L,
            text = "Düz metin hatırlatıcı",
            createdAt = 1000L,
            isChecklist = false,
            attachments = listOf(ReminderAttachment("att_1", "/path/att_1.jpg"))
        )
        assertEquals("Düz metin hatırlatıcı", buildReminderShareText(simpleReminder))
    }
}
