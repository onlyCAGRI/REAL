package com.cagri.real

import com.cagri.real.data.AttachmentType
import com.cagri.real.data.ReminderJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnnouncementCardPayloadTest {
    @Test fun canonicalReceivedCardKeepsTitleAndSeparateDescriptionWithoutBinaryFile() {
        val wire = JSONObject()
            .put("id", 1001L).put("text", "REAL yenilikleri")
            .put("createdAt", 1000L).put("updatedAt", 1000L)
            .put("sentBy", "REAL").put("shareOrigin", "11111111-1111-4111-8111-111111111111")
            .put("sendImmediateNotification", false)
            .put("attachments", JSONArray().put(JSONObject()
                .put("id", "11111111-1111-4111-8111-111111111111")
                .put("type", "TEXT_SNIPPET").put("source", "PLAIN_TEXT")
                .put("sourceTitle", "REAL").put("text", "Açıklama\nİkinci paragraf")))
        val item = ReminderJson.decode(JSONArray().put(wire).toString()).single()
        assertEquals("REAL yenilikleri", item.text)
        assertEquals("REAL", item.sentBy)
        assertFalse(item.sendImmediateNotification)
        assertEquals(AttachmentType.TEXT_SNIPPET, item.attachments.single().type)
        assertEquals("Açıklama\nİkinci paragraf", item.attachments.single().text)
        assertEquals("", item.attachments.single().localPath)
        val roundTrip = ReminderJson.decode(ReminderJson.encode(listOf(item))).single()
        assertEquals(item, roundTrip)
    }
}
