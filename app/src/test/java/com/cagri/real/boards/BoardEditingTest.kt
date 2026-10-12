package com.cagri.real.boards

import org.junit.Assert.*
import org.junit.Test

class BoardEditingTest {
    @Test fun boardsStartLockedAndOnlyExplicitBoardCanEdit() {
        val edit = BoardEditSession()
        assertFalse(edit.allows("one"))
        edit.begin("one")
        assertTrue(edit.allows("one"))
        assertFalse(edit.allows("two"))
        edit.finish()
        assertFalse(edit.allows("one"))
    }
    @Test fun lockingFlushesAuthorizedDraftBeforeRevokingWrites() {
        val edit = BoardEditSession()
        var saves = 0
        edit.begin("one")
        edit.register("text") { assertTrue(edit.allows("one")); saves++ }
        edit.finish()
        assertEquals(1, saves)
        assertFalse(edit.allows("one"))
        edit.unregister("text")
        edit.finish()
        assertEquals(1, saves)
    }
    @Test fun switchingBoardFlushesPreviousBoardAndDoesNotAuthorizeBoth() {
        val edit = BoardEditSession()
        edit.begin("one")
        var flushed = false
        edit.register("checklist") { if (edit.allows("one")) flushed = true }
        edit.begin("two")
        assertTrue(flushed)
        assertFalse(edit.allows("one"))
        assertTrue(edit.allows("two"))
    }
    @Test fun backgroundChoiceRoundTripsAndOldValuesRemainSafe() {
        BoardBackgroundColor.entries.forEach { color -> BoardBackgroundPattern.entries.forEach { pattern ->
            val look = BoardLook(color, pattern)
            assertEquals(look, BoardLook.parse(look.encode()))
        } }
        assertEquals(BoardLook(), BoardLook.parse(null))
        assertEquals(BoardLook(), BoardLook.parse("old-unknown-background"))
        assertEquals(BoardLook(BoardBackgroundColor.BLUE), BoardLook.parse("BLUE:invalid"))
    }
    @Test fun minimumZoomRemainsFiniteAndLocationCanGrowWithoutChangingRotationContract() {
        assertEquals(0.01f, BoardViewport(zoom = 0f).clamped().zoom, 0f)
        val location = BoardTransform(width = 1000f, height = 1000f, rotation = 15f).clamped(BoardBlockType.LOCATION)
        assertEquals(1000f, location.width, 0f)
        assertEquals(1000f, location.height, 0f)
        assertEquals(15f, location.rotation, 0f)
    }
}
