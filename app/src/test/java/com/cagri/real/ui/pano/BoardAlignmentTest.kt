package com.cagri.real.ui.pano

import com.cagri.real.boards.BoardTransform
import org.junit.Assert.*
import org.junit.Test

class BoardAlignmentTest {
    @Test fun nearbyGridSnapsButDistantPositionsStayFree() {
        val snapped = alignBoardBlock(BoardTransform(x = 47f, y = 73f), emptyList(), 1f, true)
        assertEquals(48f, snapped.transform.x, 0f)
        assertEquals(72f, snapped.transform.y, 0f)
        val free = BoardTransform(x = 35f, y = 83f)
        assertEquals(free, alignBoardBlock(free, emptyList(), 1f, true).transform)
    }

    @Test fun adjacentBlocksKeepGapAndDoNotChangeSizeRotationOrZ() {
        val other = BoardTransform(x = 100f, y = 50f, width = 200f, height = 100f)
        val draft = BoardTransform(x = 310f, y = 52f, width = 140f, height = 80f, z = 7)
        val result = alignBoardBlock(draft, listOf(other), 1f, false)
        assertEquals(draft.copy(x = 312f, y = 50f), result.transform)
        assertEquals(312f, result.guideX!!, 0f)
        assertEquals(50f, result.guideY!!, 0f)
    }

    @Test fun centreAlignmentUsesActualSizes() {
        val other = BoardTransform(x = 100f, y = 0f, width = 200f, height = 100f)
        val draft = BoardTransform(x = 151f, y = 114f, width = 100f, height = 60f)
        val result = alignBoardBlock(draft, listOf(other), 1f, false)
        assertEquals(150f, result.transform.x, 0f)
        assertEquals(112f, result.transform.y, 0f)
        assertEquals(200f, result.guideX!!, 0f)
    }

    @Test fun rotatedBlocksAndInvalidZoomAreNeverReinterpreted() {
        val rotated = BoardTransform(x = 47f, y = 73f, rotation = 15f)
        assertEquals(rotated, alignBoardBlock(rotated, emptyList(), 1f, true).transform)
        val plain = rotated.copy(rotation = 0f)
        assertEquals(plain, alignBoardBlock(plain, emptyList(), Float.NaN, true).transform)
        assertEquals(plain, alignBoardBlock(plain, emptyList(), 0f, true).transform)
    }

    @Test fun zoomedOutSnapDoesNotPullBlocksAcrossLargeWorldDistances() {
        val draft = BoardTransform(x = 500f, y = 500f)
        val result = alignBoardBlock(draft, emptyList(), 0.01f, true)
        assertEquals(draft, result.transform)
        assertNull(result.guideX)
        assertNull(result.guideY)
    }

    @Test fun noGridAndNoNeighboursMeansFreePlacement() {
        val draft = BoardTransform(x = -47f, y = -73f)
        assertEquals(draft, alignBoardBlock(draft, emptyList(), 1f, false).transform)
    }
}
