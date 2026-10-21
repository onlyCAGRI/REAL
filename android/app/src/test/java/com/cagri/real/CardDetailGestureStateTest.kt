package com.cagri.real

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import com.cagri.real.ui.CardBlockDragState
import org.junit.Assert.*
import org.junit.Test

class CardDetailGestureStateTest {
    @Test fun secondFingerScrollKeepsHeldBlockUnderPrimaryFingerInContentCoordinates() {
        val state = CardBlockDragState()
        state.begin(PointerId(1), Offset(50f, 300f), 100) { }
        state.move(Offset(50f, 300f), 220)
        assertEquals(Offset(0f, 120f), state.offset)
        state.move(Offset(70f, 280f), 240)
        assertEquals(Offset(20f, 120f), state.offset)
        assertEquals(PointerId(1), state.pointer)
    }
    @Test fun finishingCommitsOnceAndResetsWithoutTransferringPointer() {
        val state = CardBlockDragState()
        val results = mutableListOf<Boolean>()
        var committedOffset = Offset.Zero
        state.begin(PointerId(7), Offset(40f, 100f), 0) {
            results += it
            committedOffset = state.offset
        }
        state.move(Offset(80f, 130f), 50)
        state.finish(true)
        state.cancel()
        assertEquals(listOf(true), results)
        assertEquals(Offset(40f, 80f), committedOffset)
        assertFalse(state.active)
        assertNull(state.pointer)
        assertEquals(Offset.Zero, state.offset)
    }
    @Test fun editExitOrDisposalCancelsWithoutCommittingLayout() {
        val state = CardBlockDragState()
        val results = mutableListOf<Boolean>()
        state.begin(PointerId(1), Offset.Zero, 0) { results += it }
        state.move(Offset(100f, 100f), 100)
        state.cancel()
        state.cancel()
        assertEquals(listOf(false), results)
        assertFalse(state.active)
        assertEquals(Offset.Zero, state.offset)
    }
    @Test fun newSessionCancelsPreviousSessionAndStartsWithFreshOrigin() {
        val state = CardBlockDragState()
        val previous = mutableListOf<Boolean>()
        state.begin(PointerId(1), Offset.Zero, 0) { previous += it }
        state.move(Offset(50f, 70f), 30)
        state.begin(PointerId(2), Offset(80f, 80f), 400) { }
        state.move(Offset(90f, 90f), 400)
        assertEquals(listOf(false), previous)
        assertEquals(Offset(10f, 10f), state.offset)
        assertEquals(PointerId(2), state.pointer)
    }

    @Test fun backgroundTapAllowedInEditingOnEmptySpace() {
        val tap = Offset(200f, 400f)
        val textBlock = androidx.compose.ui.geometry.Rect(16f, 100f, 384f, 250f)
        val bottomPanel = androidx.compose.ui.geometry.Rect(0f, 600f, 400f, 800f)
        val result = com.cagri.real.ui.isCardDetailBackgroundTap(
            tapInRoot = tap,
            editing = true,
            isReordering = false,
            isDragging = false,
            isBusy = false,
            hasActiveOverlay = false,
            topAreaBottom = 80f,
            bottomPanelBounds = bottomPanel,
            blockBounds = listOf(textBlock)
        )
        assertTrue(result)
    }

    @Test fun backgroundTapRejectedWhenNotEditing() {
        val tap = Offset(200f, 400f)
        val result = com.cagri.real.ui.isCardDetailBackgroundTap(
            tapInRoot = tap,
            editing = false,
            isReordering = false,
            isDragging = false,
            isBusy = false,
            hasActiveOverlay = false,
            topAreaBottom = 80f,
            bottomPanelBounds = null,
            blockBounds = emptyList()
        )
        assertFalse(result)
    }

    @Test fun backgroundTapRejectedInsideContentBlock() {
        val tap = Offset(100f, 150f) // inside textBlock
        val textBlock = androidx.compose.ui.geometry.Rect(16f, 100f, 384f, 250f)
        val result = com.cagri.real.ui.isCardDetailBackgroundTap(
            tapInRoot = tap,
            editing = true,
            isReordering = false,
            isDragging = false,
            isBusy = false,
            hasActiveOverlay = false,
            topAreaBottom = 80f,
            bottomPanelBounds = androidx.compose.ui.geometry.Rect(0f, 600f, 400f, 800f),
            blockBounds = listOf(textBlock)
        )
        assertFalse(result)
    }

    @Test fun backgroundTapRejectedInsideBottomPanel() {
        val tap = Offset(200f, 650f) // inside bottomPanel
        val bottomPanel = androidx.compose.ui.geometry.Rect(0f, 600f, 400f, 800f)
        val result = com.cagri.real.ui.isCardDetailBackgroundTap(
            tapInRoot = tap,
            editing = true,
            isReordering = false,
            isDragging = false,
            isBusy = false,
            hasActiveOverlay = false,
            topAreaBottom = 80f,
            bottomPanelBounds = bottomPanel,
            blockBounds = emptyList()
        )
        assertFalse(result)
    }

    @Test fun backgroundTapRejectedWhenBusyOrDraggingOrReorderingOrOverlay() {
        val tap = Offset(200f, 400f)
        // Dragging
        assertFalse(com.cagri.real.ui.isCardDetailBackgroundTap(
            tapInRoot = tap, editing = true, isReordering = false, isDragging = true, isBusy = false,
            hasActiveOverlay = false, topAreaBottom = 80f, bottomPanelBounds = null, blockBounds = emptyList()
        ))
        // Reordering
        assertFalse(com.cagri.real.ui.isCardDetailBackgroundTap(
            tapInRoot = tap, editing = true, isReordering = true, isDragging = false, isBusy = false,
            hasActiveOverlay = false, topAreaBottom = 80f, bottomPanelBounds = null, blockBounds = emptyList()
        ))
        // Busy
        assertFalse(com.cagri.real.ui.isCardDetailBackgroundTap(
            tapInRoot = tap, editing = true, isReordering = false, isDragging = false, isBusy = true,
            hasActiveOverlay = false, topAreaBottom = 80f, bottomPanelBounds = null, blockBounds = emptyList()
        ))
        // Overlay active
        assertFalse(com.cagri.real.ui.isCardDetailBackgroundTap(
            tapInRoot = tap, editing = true, isReordering = false, isDragging = false, isBusy = false,
            hasActiveOverlay = true, topAreaBottom = 80f, bottomPanelBounds = null, blockBounds = emptyList()
        ))
    }

    @Test fun backgroundTapRejectedInTopAreaOrFooter() {
        val topTap = Offset(200f, 40f) // above topAreaBottom (80f)
        assertFalse(com.cagri.real.ui.isCardDetailBackgroundTap(
            tapInRoot = topTap, editing = true, isReordering = false, isDragging = false, isBusy = false,
            hasActiveOverlay = false, topAreaBottom = 80f, bottomPanelBounds = null, blockBounds = emptyList()
        ))

        val footerTap = Offset(300f, 480f)
        val footerBounds = androidx.compose.ui.geometry.Rect(200f, 460f, 390f, 500f)
        assertFalse(com.cagri.real.ui.isCardDetailBackgroundTap(
            tapInRoot = footerTap, editing = true, isReordering = false, isDragging = false, isBusy = false,
            hasActiveOverlay = false, topAreaBottom = 80f, bottomPanelBounds = null, blockBounds = emptyList(),
            footerBounds = footerBounds
        ))
    }
}
