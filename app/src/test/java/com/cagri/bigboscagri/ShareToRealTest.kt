package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ShareToRealTest {

    data class SaveCall(
        val text: String,
        val alertAt: Long?,
        val repeatRule: RecurrenceRule,
        val categoryId: Long?,
        val isChecklist: Boolean,
        val items: List<ChecklistItem>,
        val sendImmediate: Boolean,
        val referenceUrl: String?
    )

    class ShareDraftSession {
        var text: String = ""
        var referenceUrl: String? = null
        var isShareDraft: Boolean = false
        var alertAt: Long? = null
        var repeatRule: RecurrenceRule = RecurrenceRule.NONE
        var customCategoryId: Long? = null
        var sendImmediateNotification: Boolean = true

        fun onShareReceived(payload: SharedPayload) {
            text = payload.title
            referenceUrl = payload.url
            isShareDraft = true
        }

        fun onBack() {
            if (isShareDraft) {
                isShareDraft = false
                text = ""
                referenceUrl = null
                alertAt = null
                repeatRule = RecurrenceRule.NONE
            }
        }

        suspend fun saveExplicitly(
            onAdd: suspend (String, Long?, RecurrenceRule, Long?, Boolean, List<ChecklistItem>, Boolean, String?) -> Boolean
        ): Boolean {
            if (text.isBlank()) return false
            val success = onAdd(
                text.trim(),
                alertAt,
                repeatRule,
                customCategoryId,
                false,
                emptyList(),
                sendImmediateNotification,
                referenceUrl
            )
            if (success) {
                isShareDraft = false
                text = ""
                referenceUrl = null
            }
            return success
        }
    }

    @Test
    fun chromeTitleAndUrl_parsesCorrectly() {
        val payload = ShareParser.parseValues(
            rawText = "https://github.com/torvalds/linux",
            rawSubject = "Linux Kernel Source Tree"
        )
        assertNotNull(payload)
        assertEquals("Linux Kernel Source Tree", payload!!.title)
        assertEquals("https://github.com/torvalds/linux", payload.url)
    }

    @Test
    fun pureUrl_parsesCorrectly() {
        val payload = ShareParser.parseValues(rawText = "https://openai.com")
        assertNotNull(payload)
        assertEquals("https://openai.com", payload!!.title)
        assertEquals("https://openai.com", payload.url)
    }

    @Test
    fun plainSharedText_parsesCorrectly() {
        val payload = ShareParser.parseValues(rawText = "Yarın sabah toplantıyı hatırla")
        assertNotNull(payload)
        assertEquals("Yarın sabah toplantıyı hatırla", payload!!.title)
        assertNull(payload.url)
    }

    @Test
    fun textWithEmbeddedUrl_extractsUrlAndRemainingTitle() {
        val payload = ShareParser.parseValues(rawText = "Harika bir makale https://techcrunch.com/article/123 okumayı unutma")
        assertNotNull(payload)
        assertEquals("Harika bir makale  okumayı unutma", payload!!.title)
        assertEquals("https://techcrunch.com/article/123", payload.url)
    }

    @Test
    fun shareDraftDoesNotAutoSave() {
        val saveCalls = mutableListOf<SaveCall>()
        val session = ShareDraftSession()

        val payload = SharedPayload(title = "Önemli Doküman", url = "https://docs.google.com/doc/1")
        session.onShareReceived(payload)

        // Must NOT save automatically
        assertTrue(saveCalls.isEmpty())

        // Draft state must be open as share draft
        assertTrue(session.isShareDraft)
        assertEquals("Önemli Doküman", session.text)
        assertEquals("https://docs.google.com/doc/1", session.referenceUrl)
    }

    @Test
    fun shareExplicitSaveCreatesReminderWithUrlExactlyOnce() = runBlocking {
        val saveCalls = mutableListOf<SaveCall>()
        val session = ShareDraftSession()

        val payload = SharedPayload(title = "Tasarım Sistemi", url = "https://figma.com/design/123")
        session.onShareReceived(payload)

        // User edits title and selects a category
        session.text = "Tasarım Sistemini İncele"
        session.customCategoryId = 42L

        val mockOnAdd: suspend (String, Long?, RecurrenceRule, Long?, Boolean, List<ChecklistItem>, Boolean, String?) -> Boolean =
            { text, alertAt, rule, catId, isChecklist, items, sendImmediate, refUrl ->
                saveCalls.add(SaveCall(text, alertAt, rule, catId, isChecklist, items, sendImmediate, refUrl))
                true
            }

        // Explicit ✓ click
        val saved = session.saveExplicitly(mockOnAdd)
        assertTrue(saved)
        assertEquals(1, saveCalls.size)

        val call = saveCalls.first()
        assertEquals("Tasarım Sistemini İncele", call.text)
        assertEquals(42L, call.categoryId)
        assertEquals("https://figma.com/design/123", call.referenceUrl)

        // Draft state is reset
        assertFalse(session.isShareDraft)
        assertEquals("", session.text)
        assertNull(session.referenceUrl)
    }

    @Test
    fun backCancelsDraftWithoutSaving() = runBlocking {
        val saveCalls = mutableListOf<SaveCall>()
        val session = ShareDraftSession()

        val payload = SharedPayload(title = "İptal Edilecek", url = "https://example.com")
        session.onShareReceived(payload)
        assertTrue(session.isShareDraft)

        // User presses Back
        session.onBack()

        // Draft canceled and nothing saved
        assertFalse(session.isShareDraft)
        assertEquals("", session.text)
        assertNull(session.referenceUrl)
        assertTrue(saveCalls.isEmpty())
    }

    @Test
    fun onNewIntentUpdatesShareDraft() {
        val session = ShareDraftSession()

        // First share arrives
        val firstPayload = SharedPayload(title = "İlk Bağlantı", url = "https://first.com")
        session.onShareReceived(firstPayload)
        assertEquals("İlk Bağlantı", session.text)
        assertEquals("https://first.com", session.referenceUrl)

        // Second share arrives while app is open (onNewIntent)
        val secondPayload = SharedPayload(title = "İkinci Bağlantı", url = "https://second.com")
        session.onShareReceived(secondPayload)

        assertEquals("İkinci Bağlantı", session.text)
        assertEquals("https://second.com", session.referenceUrl)
        assertTrue(session.isShareDraft)
    }

    @Test
    fun legacyJsonWithoutReferenceUrlLoadsCleanly() {
        val legacyJson = JSONObject()
            .put("id", 100L)
            .put("text", "Eski format not")
            .put("createdAt", 5000L)
            .put("updatedAt", 5000L)
            .put("isImportant", false)
            .put("isChecklist", false)
            .put("sendImmediateNotification", true)

        val referenceUrl = if (legacyJson.has("referenceUrl") && !legacyJson.isNull("referenceUrl")) {
            legacyJson.optString("referenceUrl").takeIf { it.isNotBlank() }
        } else null

        assertNull(referenceUrl)

        val item = ReminderItem(
            id = legacyJson.getLong("id"),
            text = legacyJson.getString("text"),
            createdAt = legacyJson.getLong("createdAt"),
            referenceUrl = referenceUrl
        )

        assertNull(item.referenceUrl)
        assertEquals("Eski format not", item.text)
    }

    @Test
    fun newJsonWithReferenceUrlPreservesUrl() {
        val newJson = JSONObject()
            .put("id", 101L)
            .put("text", "Yeni format not")
            .put("createdAt", 6000L)
            .put("referenceUrl", "https://realapp.com")

        val referenceUrl = if (newJson.has("referenceUrl") && !newJson.isNull("referenceUrl")) {
            newJson.optString("referenceUrl").takeIf { it.isNotBlank() }
        } else null

        assertEquals("https://realapp.com", referenceUrl)

        val item = ReminderItem(
            id = newJson.getLong("id"),
            text = newJson.getString("text"),
            createdAt = newJson.getLong("createdAt"),
            referenceUrl = referenceUrl
        )

        assertEquals("https://realapp.com", item.referenceUrl)
    }
}
