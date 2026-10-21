package com.cagri.real.boards

import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.geometry.Offset
import com.cagri.real.ui.pano.*

class BoardInteractionUpdateTest {
    @Test fun rotationUsesClockwiseAnglesAndCrossesSeamWithoutJump() {
        assertEquals(90f,boardRotationDelta(Offset(10f,0f),Offset(0f,10f)),0.001f)
        assertEquals(-90f,boardRotationDelta(Offset(0f,10f),Offset(10f,0f)),0.001f)
        assertEquals(0f,boardRotationDelta(Offset.Zero,Offset(10f,0f)),0f)
        assertEquals(2f,normalizedBoardRotation(-358f),0f)
    }
    @Test fun dragTracksFingerAtDifferentZoomsWithoutDoubleScaling() {
        val density=2f
        for(zoom in listOf(0.1f,0.5f,1f,3f)) {
            // Simulate inverse layer mapping performed by Compose's pointer dispatcher.
            val local=Offset(20f,-10f)/zoom
            val world=boardLocalDragToWorld(local,density,0f)
            assertEquals(20f,world.x*density*zoom,0.001f)
            assertEquals(-10f,world.y*density*zoom,0.001f)
        }
        val turned=boardLocalDragToWorld(Offset(20f,10f),2f,90f)
        assertEquals(-5f,turned.x,0.001f);assertEquals(10f,turned.y,0.001f)
    }
    @Test fun everyTypeAcceptsFullCircleAndPreservesOldAngles() {
        BoardBlockType.entries.forEach { type ->
            listOf(-15f,0f,15f,90f,135f).forEach { angle ->
                assertEquals(angle,BoardTransform(rotation=angle).clamped(type).rotation,0f)
            }
            assertEquals(-90f,BoardTransform(rotation=270f).clamped(type).rotation,0f)
            assertEquals(0f,BoardTransform(rotation=360f).clamped(type).rotation,0f)
        }
        assertThrows(IllegalArgumentException::class.java){ normalizedBoardRotation(Float.POSITIVE_INFINITY) }
    }
    @Test fun rotatedHitTestWorksAroundFullCircle() {
        listOf(-180f,-90f,0f,90f,135f).forEach { angle ->
            val t=BoardTransform(x=100f,y=200f,width=300f,height=400f,rotation=angle)
            val p=rotatedDrag(Offset(50f,60f),angle)+Offset(t.x,t.y)
            assertTrue(blockHit(p,t))
            assertFalse(blockHit(rotatedDrag(Offset(-100f,-100f),angle)+Offset(t.x,t.y),t))
        }
    }
    @Test fun legacyBackgroundFallsBackToSupportedChoices() {
        assertEquals(BoardLook(BoardBackgroundColor.BLACK,BoardBackgroundPattern.DOTS),BoardLook.parse("BLUE:NONE").supported())
        assertEquals(BoardLook(BoardBackgroundColor.BLACK,BoardBackgroundPattern.GRID),BoardLook.parse("GREEN:GRID").supported())
        assertEquals(BoardLook(BoardBackgroundColor.GRAY,BoardBackgroundPattern.DOTS),BoardLook.parse("GRAY:DOTS").supported())
    }
    @Test fun leavingOnlyRemovesTargetBoardCacheAndOutbox() {
        val item=PanoItem("i","gone",BoardBlockType.TEXT,BoardTransform(),"{}","u","N")
        val op=BoardMutation("o","gone","i","TRANSFORM","{}")
        val other=op.copy(id="keep",boardId="other")
        val s=PanoState(items=mapOf("gone" to listOf(item),"other" to emptyList()),
            members=mapOf("gone" to emptyList(),"other" to emptyList()),
            queue=listOf(op,other),viewports=mapOf("gone" to BoardViewport(),"other" to BoardViewport()),
            backgrounds=mapOf("gone" to "BLACK:DOTS","other" to "GRAY:GRID"),
            cursors=mapOf("gone" to 1L,"other" to 2L),conflict=BoardConflict("o",item))
        val next=s.withoutBoard("gone")
        assertFalse(next.items.containsKey("gone"));assertTrue(next.items.containsKey("other"))
        assertFalse(next.members.containsKey("gone"));assertFalse(next.viewports.containsKey("gone"))
        assertFalse(next.backgrounds.containsKey("gone"));assertFalse(next.cursors.containsKey("gone"))
        assertEquals(listOf(other),next.queue);assertNull(next.conflict)
        assertEquals(2,s.queue.size)
    }
}
