package com.cagri.real

import com.cagri.real.data.*
import org.junit.Assert.*
import org.junit.Test

class CardDetailSizingTest {
    @Test fun writingBlocksHaveReadableWidthFloorButArtworkCanStaySmall() {
        listOf("text", "checklist", "journal", "voice", "files:snippet", "location").forEach {
            assertEquals(4, cardDetailMinimumSpan(it, 361f))
        }
        assertEquals(3, cardDetailMinimumSpan("card_stack:one", 361f))
        listOf("mood", "photos:a", "music:a", "media:a").forEach {
            assertEquals(2, cardDetailMinimumSpan(it, 361f))
        }
        assertEquals(5, cardDetailMinimumSpan("checklist", 288f))
    }
    @Test fun legacyNarrowWritingAreaGetsReadableWidthWithoutChangingSource() {
        val source = mapOf("checklist" to CardBlockPlacement(span = 2, column = 4, minHeight = 200, top = 400),
            "music:a" to CardBlockPlacement(span = 3, column = 0))
        val shown = cardDetailReadablePlacements(source, 361f)
        assertEquals(4, shown.getValue("checklist").span)
        assertEquals(2, shown.getValue("checklist").column)
        assertEquals(200, shown.getValue("checklist").minHeight)
        assertEquals(400, shown.getValue("checklist").top)
        assertEquals(source.getValue("music:a"), shown.getValue("music:a"))
        assertEquals(2, source.getValue("checklist").span)
    }
    @Test fun safeDropStillAllowsIndependentGapsAndRejectsOccupiedCells() {
        val keys = listOf("photos:a", "card_stack:b")
        val layout = keys.associateWith { CardBlockPlacement(span = 3, column = 0) }
        val boxes = mapOf(keys[0] to CardMeasuredBox(0f, 0f, 168f, 100f),
            keys[1] to CardMeasuredBox(0f, 200f, 168f, 160f))
        val beside = placeCardInFreeSpace(keys, layout, boxes, keys[1], 180f, 0f, 60f)!!
        assertEquals(3, beside.getValue(keys[1]).column)
        assertEquals(0, beside.getValue(keys[1]).top)
        assertNull(placeCardInFreeSpace(keys, layout, boxes, keys[1], 0f, 40f, 60f))
        val gap = placeCardInFreeSpace(keys, layout, boxes, keys[1], 0f, 321f, 60f)!!
        assertEquals(320, gap.getValue(keys[1]).top)
    }
    @Test fun handleCannotGrowUnderNextBlockAndCanShrinkBack() {
        val boxes = linkedMapOf("text" to CardMeasuredBox(0f, 0f, 348f, 160f),
            "checklist" to CardMeasuredBox(0f, 176f, 348f, 100f))
        assertFalse(cardDetailResizeFits("text", 6, 200f, boxes, 60f, 12f))
        assertTrue(cardDetailResizeFits("text", 6, 176f, boxes, 60f, 12f))
        assertTrue(cardDetailResizeFits("text", 6, 100f, boxes, 60f, 12f))
        assertFalse(cardDetailResizeFits("text", 6, Float.NaN, boxes, 60f, 12f))
    }
    @Test fun photoCannotResizeThroughAdjacentStack() {
        val boxes = linkedMapOf("photos:a" to CardMeasuredBox(0f, 0f, 168f, 160f),
            "card_stack:a" to CardMeasuredBox(180f, 0f, 168f, 160f))
        assertFalse(cardDetailResizeFits("photos:a", 4, 160f, boxes, 60f, 12f))
        assertTrue(cardDetailResizeFits("photos:a", 2, 160f, boxes, 60f, 12f))
    }
    @Test fun growingContentCannotHideNeighborsOrTheirResizeHandles() {
        val boxes = linkedMapOf("text" to CardMeasuredBox(0f, 0f, 348f, 220f),
            "checklist" to CardMeasuredBox(0f, 160f, 348f, 180f),
            "photos:a" to CardMeasuredBox(0f, 350f, 168f, 100f),
            "card_stack:a" to CardMeasuredBox(180f, 350f, 168f, 100f))
        val result = cardDetailNonOverlappingBoxes(boxes)
        assertTrue(result.getValue("checklist").top >= 220f)
        assertTrue(result.getValue("photos:a").top >= result.getValue("checklist").top + 180f)
        assertEquals(result.getValue("photos:a").top, result.getValue("card_stack:a").top)
        assertEquals(result, cardDetailNonOverlappingBoxes(result))
        assertEquals(160f, boxes.getValue("checklist").top, .001f) // source is untouched
    }
    @Test fun deliberateGapsAndSideBySideCardsArePreserved() {
        val boxes = linkedMapOf("a" to CardMeasuredBox(0f, 0f, 168f, 100f),
            "b" to CardMeasuredBox(180f, 0f, 168f, 200f),
            "c" to CardMeasuredBox(0f, 400f, 348f, 100f))
        assertEquals(boxes, cardDetailNonOverlappingBoxes(boxes))
    }
    @Test fun imagePinchKeepsFocalPointAndClampsWithoutBlankEdges() {
        val zoomed = cardPhotoTransform(1f, 0f, 0f, 2f, 0f, 0f, 25f, 20f, 200f, 160f)
        assertEquals(CardPhotoTransform(2f, -25f, -20f), zoomed)
        assertEquals(CardPhotoTransform(4f, 300f, -240f),
            cardPhotoTransform(2f, 0f, 0f, 20f, 9999f, -9999f, 0f, 0f, 200f, 160f))
        assertEquals(CardPhotoTransform(1f, 0f, 0f),
            cardPhotoTransform(4f, 100f, 80f, .01f, 0f, 0f, 0f, 0f, 200f, 160f))
        assertEquals(CardPhotoTransform(1f, 0f, 0f),
            cardPhotoTransform(1f, 0f, 0f, Float.NaN, 0f, 0f, 0f, 0f, 200f, 160f))
    }
    @Test fun originalCatalogStagesStayUnchanged() {
        assertEquals(CatalogCardDisplay.FULL, catalogCardDisplay(6))
        assertEquals(CatalogCardDisplay.FULL, catalogCardDisplay(4))
        assertEquals(CatalogCardDisplay.COVER_TITLE, catalogCardDisplay(3))
        assertEquals(CatalogCardDisplay.COVER_ONLY, catalogCardDisplay(2))
        assertTrue(catalogActionsHiddenAfterResize(false, -8f))
    }
}
