package com.cagri.real.boards

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import androidx.compose.ui.geometry.Offset
import com.cagri.real.ui.pano.*
import com.cagri.real.data.*

class BoardModelTest {
    @Test fun everyTypeClampsPracticalDimensionsAndRotation(){BoardBlockType.entries.forEach{type->val b=type.bounds();
            val t=BoardTransform(width=1f,height=99999f,rotation=-180f).clamped(type);
            assertTrue(t.width>=b.minWidth);
            assertTrue(t.height>=b.minHeight);
            assertTrue(t.width<=b.maxWidth);
            assertTrue(t.height<=b.maxHeight);
            assertEquals(-180f,t.rotation,0.001f)}}
    @Test fun nonFiniteTransformsAreRejected(){assertThrows(IllegalArgumentException::class.java){BoardTransform(x=Float.NaN).clamped(BoardBlockType.TEXT)}}
    @Test fun photosRejectUnusableAspectRatios(){val t=BoardTransform(width=100f,height=800f).clamped(BoardBlockType.PHOTO);
        assertEquals(400f,t.height,0.001f)}
    @Test fun zoomIsClampedAndPinchKeepsTheAnchor(){val v=BoardViewport(20f,30f,1f);
        val center=Offset(200f,300f);
        val point=boardPoint(center,v);
        val zoomed=viewportAfterGesture(v,center,Offset.Zero,2f);
        assertEquals(point.x,boardPoint(center,zoomed).x,0.001f);
        assertEquals(point.y,boardPoint(center,zoomed).y,0.001f);
        assertEquals(3f,viewportAfterGesture(v,center,Offset.Zero,100f).zoom,0.001f);
        assertEquals(BOARD_MIN_ZOOM,BoardViewport(zoom=0.001f).clamped().zoom,0.001f)}
    @Test fun outsideControlsHitTestDoesNotMoveTheContentOrigin() {
        val transform=BoardTransform(x=100f,y=200f,width=300f,height=400f,rotation=15f)
        val delta=rotatedDrag(Offset(-20f,-20f),transform.rotation)
        val control=Offset(transform.x+delta.x,transform.y+delta.y)
        assertFalse(blockHit(control,transform))
        assertTrue(blockHit(control,transform,48f))
        assertTrue(blockHit(Offset(transform.x,transform.y),transform))
        assertEquals(100f,transform.x,0f)
        assertEquals(200f,transform.y,0f)
    }
    @Test fun standaloneStackItemUsesNativeStackCodecWithoutCardReference() {
        val stack=CardStackBlock(id="native",cards=listOf(CardStackCard(id="one",title="Card 1")))
        val payload=JSONObject().put("stack",CardStackBlock.encode(stack)).put("images",org.json.JSONArray())
        val item=PanoItem("stack-item","board",BoardBlockType.STACK,BoardTransform(),payload.toString(),"uid","Name")
        val restored=PanoItem.from(item.json())
        assertEquals(BoardBlockType.STACK,restored.type)
        assertEquals(stack,CardStackBlock.decode(restored.content().getJSONObject("stack")))
        assertFalse(restored.content().has("sourceCardKey"))
    }
    @Test fun rotatedHitTestingUsesBlockCoordinates(){val t=BoardTransform(x=100f,y=100f,width=200f,height=120f,rotation=15f);
        val delta=rotatedDrag(Offset(30f,40f),15f);
        assertTrue(blockHit(Offset(100f+delta.x,100f+delta.y),t));
        assertFalse(blockHit(Offset(0f,0f),t))}
    @Test fun queuedMutationRoundTripPreservesVersionAndTransform(){val m=BoardMutation("op","board","item","TRANSFORM",JSONObject().put("baseVersion",3).put("transform",BoardTransform(x=80f,rotation=9f).json()).toString());
        assertEquals(m,BoardMutation.from(m.json()));
        assertEquals(3,JSONObject(m.body).getInt("baseVersion"))}
    @Test fun itemRoundTripKeepsAuthorVersionsAndTombstone(){val i=PanoItem("item","board",BoardBlockType.TEXT,BoardTransform(),"{\"text\":\"Note\"}","firebase-uid","Çağrı",4,7,10,true);
        assertEquals(i,PanoItem.from(i.json()))}
    @Test fun sharedCardCodecKeepsAllExistingSemanticsWithoutMutatingSource(){val r=ReminderItem(12,"Checklist",100,isChecklist=true,checklistItems=listOf(ChecklistItem(1,"Item")),location=ReminderLocation(placeName="Ordu",latitude=40.98,longitude=37.87),sentBy="Ayşe",shareOrigin="original");
        val decoded=ReminderJson.decode(ReminderJson.encode(listOf(r))).single();
        assertEquals(r,decoded);
        val boardCopy=decoded.copy(text="Board copy");
        assertEquals("Checklist",r.text);
        assertEquals("Board copy",boardCopy.text)}
}
