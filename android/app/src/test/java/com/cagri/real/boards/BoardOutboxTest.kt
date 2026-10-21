package com.cagri.real.boards

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class BoardOutboxTest {
    private fun item(id:String="one")=PanoItem(id,"board",BoardBlockType.TEXT,BoardTransform(),"{\"text\":\"server\"}","uid","Ayşe",4,5,8)
    @Test fun pendingTransformKeepsServerContentAndOtherItem(){val pending=BoardMutation("op","board","one","TRANSFORM",JSONObject().put("baseVersion",4).put("transform",BoardTransform(x=50f).json()).toString());
        val merged=overlayBoardItem(item(),listOf(pending));
        assertEquals(50f,merged.transform.x,0f);
        assertEquals("server",merged.content().getString("text"));
        assertEquals(item("other"),overlayBoardItem(item("other"),listOf(pending)))}
    @Test fun offlineDeleteDoesNotDisappearWhenAnOldRemoteSnapshotArrives(){val pending=BoardMutation("op","board","one","DELETE","{}");
        assertTrue(overlayBoardItem(item(),listOf(pending)).deleted)}
    @Test fun pendingCardExpansionKeepsRemappedServerFileReferences(){val card=PanoItem("one","board",BoardBlockType.CARD,BoardTransform(),"{\"expanded\":false,\"card\":{\"text\":\"Photo\",\"attachments\":[{\"blobId\":\"board-copy\"}]}}","uid","Ayşe");
        val p=JSONObject().put("baseVersion",1).put("payload",JSONObject().put("expanded",true).put("card",JSONObject().put("text","stale local snapshot")));
        val merged=overlayBoardItem(card,listOf(BoardMutation("op","board","one","CONTENT",p.toString())));
        assertTrue(merged.content().getBoolean("expanded"));
        assertEquals("board-copy",merged.content().getJSONObject("card").getJSONArray("attachments").getJSONObject(0).getString("blobId"));
        assertEquals("Photo",merged.content().getJSONObject("card").getString("text"))}
}
