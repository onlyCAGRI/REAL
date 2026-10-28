package com.cagri.real

import com.cagri.real.data.*
import org.junit.Assert.*
import org.junit.Test

class CardTitleFormatTest {
    @Test fun titleStyleSurvivesJsonAndPlacementEdits() {
        val card = ReminderItem(id = 5, text = "Başlık", createdAt = 1,
            contentBlockLayout = mapOf("text" to CardBlockPlacement(textAlignment = "end", textBold = true, textItalic = true)))
        val restored = ReminderJson.decode(ReminderJson.encode(listOf(card))).single()
        val placement = restored.contentBlockLayout.getValue("text")
        assertEquals("end", placement.textAlignment)
        assertTrue(placement.textBold)
        assertTrue(placement.textItalic)
        assertTrue(placement.copy(span = 3, column = 2).normalized().textItalic)
        assertEquals("Başlık", restored.text)
    }
    @Test fun legacyTitleIsUnformattedAndInvalidAlignmentFallsBack() {
        val card = ReminderItem(id = 6, text = "Eski", createdAt = 1)
        assertTrue(ReminderJson.decode(ReminderJson.encode(listOf(card))).single().contentBlockLayout.isEmpty())
        assertEquals("start", CardBlockPlacement(textAlignment = "unexpected").normalized().textAlignment)
    }
    @Test fun legacyTextBoxesBecomeOneCardStacksInTheirSlot() {
        val card = ReminderItem(id = 8, text = "Asıl başlık", createdAt = 1,
            attachments = listOf(
                ReminderAttachment("one", type = AttachmentType.TEXT_SNIPPET, text = "Birinci metin", sourceTitle = "REAL_TEXT_BLOCK"),
                ReminderAttachment("two", type = AttachmentType.TEXT_SNIPPET, text = "  ", sourceTitle = "REAL_TEXT_BLOCK")),
            contentBlockOrder = listOf("text", "files:one", "files:two"),
            contentBlockLayout = mapOf("files:one" to CardBlockPlacement(minHeight = 80), "files:two" to CardBlockPlacement()))
        val restored = ReminderJson.decode(ReminderJson.encode(listOf(card))).single()
        assertEquals("Asıl başlık", restored.text)
        assertTrue(restored.attachments.isEmpty())
        assertEquals(listOf("Birinci metin"), restored.cardStack?.cards?.map { it.body })
        assertEquals(listOf("text", "card_stack:tbone"), restored.contentBlockOrder)
        assertEquals(80, restored.contentBlockLayout.getValue("card_stack:tbone").minHeight)
        assertFalse(restored.contentBlockLayout.containsKey("files:two"))
    }
}
