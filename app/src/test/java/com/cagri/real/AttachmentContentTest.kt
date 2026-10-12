package com.cagri.real

import com.cagri.real.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AttachmentContentTest {
    @Test fun oldPhotoWithoutTypeAndExistingLinkStillLoad() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("reminders", JSONArray().put(JSONObject()
            .put("id", 9).put("text", "Eski kayıt").put("createdAt", 1000)
            .put("referenceUrl", "https://example.org/report")
            .put("attachments", JSONArray().put(JSONObject().put("id", "old").put("localPath", "/photos/a.jpg")))).toString()).commit()
        val item = ReminderRepository(prefs).load().single()
        assertEquals(AttachmentType.IMAGE, item.attachments.single().type)
        assertEquals("/photos/a.jpg", item.attachments.single().localPath)
        assertEquals("https://example.org/report", item.referenceUrl)
    }

    @Test fun fileAndSnippetMetadataSurviveRepositoryRoundTrip() {
        val prefs = FakeSharedPreferences()
        val attachments = listOf(
            ReminderAttachment("uri", type = AttachmentType.FILE, uri = "content://documents/17",
                mimeType = "application/pdf", displayName = "Sonuçlar.pdf", sizeBytes = 2400000),
            ReminderAttachment("copy", "/private/reminder_attachments/a.zip", AttachmentType.FILE,
                mimeType = "application/zip", displayName = "Yedek.zip"),
        ) + SnippetSource.entries.map { source ->
            ReminderAttachment(source.name, type = AttachmentType.TEXT_SNIPPET, text = "Merhaba\nRaporu getir.",
                source = source, sourceTitle = "Randevu")
        }
        val created = ReminderRepository(prefs).add("Not", attachments = attachments)
        assertEquals(attachments, ReminderRepository(prefs).load().first { it.id == created.id }.attachments)
    }

    @Test fun onlyWholeWebUrlUsesExistingLinkField() {
        val result = IncomingContentParser.text("  https://example.org/results?q=1  ", "Sonuçlar")
        assertEquals("https://example.org/results?q=1", result.referenceUrl)
        assertTrue(result.attachments.isEmpty())
        assertEquals("https://example.org/sonuçlar", IncomingContentParser.text("https://example.org/sonuçlar").referenceUrl)
        listOf("Yarın raporu getir", "Bu bağlantıyı oku: https://example.org", "file:///private/data", "javascript:alert(1)").forEach { text ->
            val snippet = IncomingContentParser.text(text)
            assertNull(snippet.referenceUrl)
            assertEquals(text, snippet.attachments.single().text)
            assertEquals(SnippetSource.PLAIN_TEXT, snippet.attachments.single().source)
        }
    }

    @Test fun pendingImportSurvivesSerialization() {
        val pending = IncomingContent(IncomingContentParser.text("E-posta metni", "Konu").attachments,
            referenceUrl = "https://example.org", warning = "Bir dosya okunamadı")
        assertEquals(pending, IncomingContent.decode(pending.encode()))
    }

    @Test fun retriesDoNotDuplicateAttachmentsOrChangeReminderSchedule() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val date = System.currentTimeMillis() + 86400000
        val original = repo.add("Aynı hatırlatıcı", alertAt = date, repeatRule = RecurrenceRule.DAILY,
            referenceUrl = "https://example.org")
        val file = ReminderAttachment("stable", type = AttachmentType.FILE, uri = "content://documents/1", displayName = "A.pdf")
        repo.appendContent(original.id, listOf(file), null)
        val updated = repo.appendContent(original.id, listOf(file), null)
        assertEquals(listOf(file), updated.attachments)
        assertEquals(original.alertAt, updated.alertAt)
        assertEquals(original.repeatRule, updated.repeatRule)
        assertEquals(original.referenceUrl, updated.referenceUrl)
        assertEquals(original.text, updated.text)
    }

    @Test fun fileDoesNotConsumePhotoSlotsAndFourthPhotoIsRejectedAtomically() {
        val repo = ReminderRepository(FakeSharedPreferences())
        val photos = (1..3).map { ReminderAttachment("$it", "/private/$it.jpg") }
        val item = repo.add("Medya", attachments = photos)
        val file = ReminderAttachment("pdf", type = AttachmentType.FILE, uri = "content://documents/1")
        repo.appendContent(item.id, listOf(file), null)
        assertThrows(IllegalArgumentException::class.java) { repo.appendContent(item.id, listOf(ReminderAttachment("4", "/private/4.jpg")), null) }
        assertEquals(photos + file, repo.load().single().attachments)
    }

    @Test fun linkRemovalIsExplicitAndDoesNotRemoveReminderOrOtherAttachments() {
        val repo = ReminderRepository(FakeSharedPreferences())
        val file = ReminderAttachment("pdf", type = AttachmentType.FILE, uri = "content://documents/1")
        val item = repo.add("Not", referenceUrl = "https://example.org", attachments = listOf(file))
        repo.edit(item.id, item.text)
        assertEquals(item.referenceUrl, repo.load().single().referenceUrl)
        repo.edit(item.id, item.text, referenceUrl = null, updateReferenceUrl = true)
        assertNull(repo.load().single().referenceUrl)
        assertEquals(listOf(file), repo.load().single().attachments)
    }

    @Test fun deletionCannotEscapePrivateAttachmentDirectory() {
        val parent = Files.createTempDirectory("real-attachments").toFile()
        try {
            val owned = parent.resolve("reminder_attachments").apply { mkdir() }
            val inside = owned.resolve("a.pdf").apply { writeText("owned") }
            val outside = parent.resolve("source.pdf").apply { writeText("external") }
            assertFalse(AttachmentStorage.deleteOwnedFile(owned, outside.path))
            assertFalse(AttachmentStorage.deleteOwnedFile(owned, owned.resolve("../source.pdf").path))
            assertTrue(outside.exists())
            assertTrue(AttachmentStorage.deleteOwnedFile(owned, inside.path))
            assertFalse(inside.exists())
        } finally { parent.deleteRecursively() }
    }
}
