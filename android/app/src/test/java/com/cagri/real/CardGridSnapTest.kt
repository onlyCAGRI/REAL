package com.cagri.real

import com.cagri.real.data.*
import com.cagri.real.ui.CardDropZone
import com.cagri.real.ui.applyCardDrop
import com.cagri.real.ui.detailDockCollapsedAfterDrag
import org.junit.Assert.*
import org.junit.Test

class CardGridSnapTest {
    @Test fun shortDragSnapsGapInsteadOfReordering() {
        assertEquals(32, snappedCardGapBefore(16, 17f))
        assertEquals(8, snappedCardGapBefore(32, -23f))
        assertEquals(24, snappedCardGapBefore(12, 8f))
    }
    @Test fun gapsRemainIndependentAndSourceMapIsImmutable() {
        val original = mapOf("text" to CardBlockPlacement(gapBefore = 16), "photos:a" to CardBlockPlacement(gapBefore = 80))
        val moved = original + ("text" to original.getValue("text").copy(gapBefore = snappedCardGapBefore(16, 17f)))
        assertEquals(32, moved.getValue("text").gapBefore)
        assertEquals(80, moved.getValue("photos:a").gapBefore)
        assertEquals(16, original.getValue("text").gapBefore)
    }
    @Test fun finiteBoundsAndInvalidDeltasCannotCreateBadGeometry() {
        assertEquals(0, snappedCardGapBefore(16, -1000f))
        assertEquals(160, snappedCardGapBefore(16, Float.MAX_VALUE))
        assertEquals(16, snappedCardGapBefore(16, Float.NaN))
        assertEquals(16, snappedCardGapBefore(16, Float.POSITIVE_INFINITY))
    }
    @Test fun rowAnchorRoundsUpToGridAndCannotOverlapPreviousContent() {
        assertEquals(104f, snappedCardRowBottom(101f, 8f), 0f)
        assertEquals(104f, snappedCardRowBottom(104f, 8f), 0f)
        assertEquals(312f, snappedCardRowBottom(307f, 24f), 0f)
    }
    @Test fun dropCanPlaceSnappedGapWithoutEqualizingOtherGaps() {
        val keys = listOf("text", "photos:a", "music:song")
        val source = mapOf("text" to CardBlockPlacement(gapBefore = 16), "photos:a" to CardBlockPlacement(gapBefore = 80))
        val (order, layout) = applyCardDrop(keys, source, "photos:a", "text", CardDropZone.Before, gapBefore = 32)
        assertEquals("photos:a", order.first())
        assertEquals(32, layout.getValue("photos:a").gapBefore)
        assertEquals(16, layout.getValue("text").gapBefore)
        assertEquals(80, source.getValue("photos:a").gapBefore)
    }
    @Test fun staleDropTargetDoesNotChangeOrderOrGeometry() {
        val keys = listOf("text", "photos:a")
        val source = mapOf("text" to CardBlockPlacement(gapBefore = 40))
        assertEquals(keys to source, applyCardDrop(keys, source, "photos:a", "deleted", CardDropZone.After, gapBefore = 32))
    }
    @Test fun dockDownCollapsesAndUpRestores() {
        assertTrue(detailDockCollapsedAfterDrag(false, 30f))
        assertFalse(detailDockCollapsedAfterDrag(true, -30f))
    }
    @Test fun shortDockGesturesAndInvalidDeltasLeaveStateAlone() {
        assertTrue(detailDockCollapsedAfterDrag(true, 5f))
        assertFalse(detailDockCollapsedAfterDrag(false, -5f))
        assertFalse(detailDockCollapsedAfterDrag(false, Float.NaN))
    }
}
