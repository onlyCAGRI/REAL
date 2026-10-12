package com.cagri.real

import com.cagri.real.data.*
import org.junit.Assert.*
import org.junit.Test

class CardStackPlacementRegressionTest {
    @Test fun offscreenStackHasFullGeometryAndDoesNotBlockOtherDrops() {
        val keys = listOf("photos:a", "card_stack:s")
        val layout = mapOf("photos:a" to CardBlockPlacement(3, 0, top = 16),
            "card_stack:s" to CardBlockPlacement(3, 0, top = 3000))
        val boxes = cardGridMeasuredBoxes(keys, layout,
            mapOf("photos:a" to (138f to 100f), "card_stack:s" to (138f to 900f)), 50f)
        assertEquals(3000f, boxes.getValue("card_stack:s").top, 0f)
        assertEquals(900f, boxes.getValue("card_stack:s").height, 0f)
        assertEquals(138f, boxes.getValue("card_stack:s").width, 0f)
        val placed = placeCardInFreeSpace(keys, layout, boxes, "photos:a", 150f, 16f, 50f)!!
        assertEquals(3, placed.getValue("photos:a").column)
        assertEquals(layout.getValue("card_stack:s"), placed.getValue("card_stack:s"))
    }
    @Test fun twoStacksCanBeDroppedSideBySideWithoutLosingTheirSizes() {
        val keys = listOf("card_stack:a", "card_stack:b")
        val layout = mapOf(keys[0] to CardBlockPlacement(3, 0, top = 16), keys[1] to CardBlockPlacement(3, 0, top = 1000))
        val boxes = cardGridMeasuredBoxes(keys, layout, keys.associateWith { 138f to 600f }, 50f)
        val placed = placeCardInFreeSpace(keys, layout, boxes, keys[1], 150f, 16f, 50f)!!
        assertEquals(3, placed.getValue(keys[1]).column)
        assertEquals(16, placed.getValue(keys[1]).top)
        assertEquals(layout.getValue(keys[0]), placed.getValue(keys[0]))
        assertEquals(3, placed.getValue(keys[1]).span)
    }
    @Test fun stackCanMoveToEmptySpaceAboveOrBelowItsOldPosition() {
        val keys = listOf("text", "card_stack:s")
        val layout = mapOf("text" to CardBlockPlacement(top = 0), "card_stack:s" to CardBlockPlacement(3, 0, top = 400))
        val boxes = cardGridMeasuredBoxes(keys, layout, mapOf("text" to (288f to 80f), "card_stack:s" to (138f to 300f)), 50f)
        assertEquals(96, placeCardInFreeSpace(keys, layout, boxes, "card_stack:s", 150f, 96f, 50f)!!.getValue("card_stack:s").top)
        assertEquals(800, placeCardInFreeSpace(keys, layout, boxes, "card_stack:s", 0f, 800f, 50f)!!.getValue("card_stack:s").top)
        assertNull(placeCardInFreeSpace(keys, layout, boxes, "card_stack:s", 0f, 16f, 50f))
    }
    @Test fun flowingGeometryIncludesEntireTallStackBeforeFollowingContent() {
        val keys = listOf("card_stack:s", "photos:a")
        val boxes = cardGridMeasuredBoxes(keys, emptyMap(), mapOf(keys[0] to (288f to 900f), keys[1] to (288f to 100f)), 50f)
        assertEquals(12f, boxes.getValue(keys[0]).top, 0f)
        assertEquals(900f, boxes.getValue(keys[0]).height, 0f)
        assertTrue(boxes.getValue(keys[1]).top >= boxes.getValue(keys[0]).top + boxes.getValue(keys[0]).height)
    }
    @Test fun explicitHeightShrinksActualFrontCardBelowLegacy390Floor() {
        assertEquals(336f, cardStackFrontHeightDp(500f, 140f, 24f), 0f)
        assertEquals(236f, cardStackFrontHeightDp(400f, 140f, 24f), 0f)
        assertEquals(80f, cardStackFrontHeightDp(100f, 140f, 24f), 0f)
        assertEquals(800f, cardStackFrontHeightDp(2000f, 0f, 0f), 0f)
    }
    @Test fun resizedStackCollisionUsesCurrentHeightRatherThanLegacyFloor() {
        val keys = listOf("card_stack:s", "photos:a")
        val layout = mapOf(keys[0] to CardBlockPlacement(3, 0, minHeight = 240, top = 16), keys[1] to CardBlockPlacement(3, 0, top = 1000))
        val boxes = cardGridMeasuredBoxes(keys, layout, mapOf(keys[0] to (138f to 240f), keys[1] to (138f to 100f)), 50f)
        assertNotNull(placeCardInFreeSpace(keys, layout, boxes, keys[1], 0f, 264f, 50f))
        assertNull(placeCardInFreeSpace(keys, layout, boxes, keys[1], 0f, 200f, 50f))
    }
    @Test fun sizeAndPositionRemainPresentationOnlyAcrossJsonSave() {
        val source = ReminderItem(id = 1, text = "Card", createdAt = 1000,
            cardStack = CardStackBlock(cards = listOf(CardStackCard(id = "a", title = "Title", body = "Body", stackGroupId = "s"))))
        val edited = source.copy(contentBlockLayout = mapOf("card_stack:s" to CardBlockPlacement(3, 3, minHeight = 240, top = 96)))
        val restored = ReminderJson.decode(ReminderJson.encode(listOf(edited))).single()
        assertEquals(source.cardStack, restored.cardStack)
        assertEquals(edited.contentBlockLayout, restored.contentBlockLayout)
        assertTrue(source.contentBlockLayout.isEmpty())
    }
}
