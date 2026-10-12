package com.cagri.real

import com.cagri.real.data.*
import org.junit.Assert.*
import org.junit.Test

class CardFreePlacementTest {
    private val keys = listOf("photos:p", "card_stack:s", "text")
    private val layout = mapOf("photos:p" to CardBlockPlacement(span = 3), "card_stack:s" to CardBlockPlacement(span = 3))
    private val boxes = mapOf(
        "photos:p" to CardMeasuredBox(0f, 16f, 138f, 100f),
        "card_stack:s" to CardMeasuredBox(0f, 200f, 138f, 180f),
        "text" to CardMeasuredBox(0f, 400f, 288f, 100f)
    )

    @Test fun stackFitsBesideReducedPhotoWithoutResizingOrReordering() {
        val placed = placeCardInFreeSpace(keys, layout, boxes, "card_stack:s", 144f, 16f, 50f)!!
        assertEquals(3, placed.getValue("card_stack:s").column)
        assertEquals(16, placed.getValue("card_stack:s").top)
        assertEquals(3, placed.getValue("photos:p").span)
        assertEquals(0, placed.getValue("photos:p").column)
        assertEquals(16, placed.getValue("photos:p").top)
        assertEquals(400, placed.getValue("text").top)
        assertNull(layout.getValue("photos:p").top)
    }
    @Test fun lonePhotoCanMoveLeftAndRightInSameRow() {
        val k = listOf("photos:p")
        assertEquals(3, placeCardInFreeSpace(k, layout, boxes, k.single(), 151f, 19f, 50f)!!.getValue(k.single()).column)
        val right = mapOf(k.single() to CardBlockPlacement(span = 3, column = 3, top = 16))
        val rightBox = mapOf(k.single() to CardMeasuredBox(150f, 16f, 138f, 100f))
        val left = placeCardInFreeSpace(k, right, rightBox, k.single(), 0f, 17f, 50f)!!
        assertEquals(0, left.getValue(k.single()).column)
        assertEquals(16, left.getValue(k.single()).top)
    }
    @Test fun emptySpaceAboveAndBelowAcceptsIndependentGridTop() {
        val moved = placeCardInFreeSpace(keys, layout, boxes, "card_stack:s", 0f, 600f, 50f)!!
        assertEquals(600, moved.getValue("card_stack:s").top)
        assertEquals(400, moved.getValue("text").top)
        assertEquals(16, moved.getValue("photos:p").top)
    }
    @Test fun occupiedSpaceRejectsDropRatherThanReorderingNeighbors() {
        assertNull(placeCardInFreeSpace(keys, layout, boxes, "card_stack:s", 0f, 16f, 50f))
        assertNull(placeCardInFreeSpace(keys, layout, boxes, "card_stack:s", 100f, 400f, 50f))
    }
    @Test fun staleAndInvalidMeasurementsDoNotCommit() {
        assertNull(placeCardInFreeSpace(keys, layout, boxes - "text", "card_stack:s", 150f, 16f, 50f))
        assertNull(placeCardInFreeSpace(keys, layout, boxes, "deleted", 150f, 16f, 50f))
        assertNull(placeCardInFreeSpace(keys, layout, boxes, "card_stack:s", Float.NaN, 16f, 50f))
        assertNull(placeCardInFreeSpace(keys, layout, boxes, "card_stack:s", 150f, 16f, 0f))
    }
    @Test fun stackGroupsKeepLegacyCardsTogetherAndAllowRepeatedIndependentStacks() {
        val block = CardStackBlock(cards = listOf(CardStackCard(id = "old1"), CardStackCard(id = "old2"),
            CardStackCard(id = "new1", stackGroupId = "a"), CardStackCard(id = "new2", stackGroupId = "b")))
        assertEquals(listOf("card_stack", "card_stack:a", "card_stack:b"), block.layoutStacks().map { it.first })
        assertEquals(listOf("old1", "old2"), block.layoutStacks().first().second.cards.map { it.id })
        val group = block.layoutStacks()[1].second
        val changed = block.replaceLayoutStack("card_stack:a", group.copy(cards = group.cards + CardStackCard(id = "inner")))!!
        assertEquals(listOf("new1", "inner"), changed.layoutStacks()[1].second.cards.map { it.id })
        assertTrue(changed.layoutStacks()[1].second.cards.all { it.stackGroupId == "a" })
        assertEquals(block.layoutStacks()[2], changed.layoutStacks()[2])
        assertEquals(listOf("old1", "old2", "new2"), changed.replaceLayoutStack("card_stack:a", null)!!.cards.map { it.id })
        assertEquals(block, block.replaceLayoutStack("card_stack:missing", null))
    }
    @Test fun positionAndStackGroupsRoundTripThroughCanonicalReminderJson() {
        val reminder = ReminderItem(id = 7, text = "Card", createdAt = 1000,
            cardStack = CardStackBlock(cards = listOf(CardStackCard(stackGroupId = "one"), CardStackCard(stackGroupId = "two"))),
            contentBlockLayout = mapOf("card_stack:one" to CardBlockPlacement(span = 3, column = 3, top = 480)),
            contentBlockOrder = listOf("text", "card_stack:one", "card_stack:two"))
        val restored = ReminderJson.decode(ReminderJson.encode(listOf(reminder))).single()
        assertEquals(reminder.cardStack, restored.cardStack)
        assertEquals(reminder.contentBlockLayout, restored.contentBlockLayout)
        assertEquals(reminder.contentBlockOrder, restored.contentBlockOrder)
        assertEquals(setOf("text", "card_stack:one", "card_stack:two"), restored.cardContentKeys())
    }
}
