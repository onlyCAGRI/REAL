package com.cagri.real.boards

import org.json.JSONObject

/** A pending card interaction changes only its board view/checklist, never snapshot assets. */
fun boardContentAfter(item:PanoItem,incoming:JSONObject):String {
    if(item.type!=BoardBlockType.CARD)return incoming.toString()
    val p=item.content();
    p.put("expanded",incoming.optBoolean("expanded",p.optBoolean("expanded")))
    incoming.optJSONArray("checklistItems")?.let { changes->
        val states=(0 until changes.length()).associate { val c=changes.getJSONObject(it);
            c.getLong("id") to c.getBoolean("done") }
        val entries=p.optJSONObject("card")?.optJSONArray("checklistItems")
        if(entries!=null)for(i in 0 until entries.length()){val e=entries.getJSONObject(i);
            states[e.optLong("id")]?.let { e.put("completedAt",if(it)1 else JSONObject.NULL) }}
        p.put("checklistItems",changes)
    }
    return p.toString()
}
/** Merge a confirmed item with only this device's pending field groups. */
fun overlayBoardItem(server:PanoItem,queue:List<BoardMutation>):PanoItem {
    var value=server
    for(op in queue.filter {it.itemId==server.id}){
        val b=JSONObject(op.body)
        value=when(op.kind){
            "TRANSFORM"->value.copy(transform=BoardTransform.from(b.getJSONObject("transform")),transformVersion=b.getInt("baseVersion")+1,revision=value.revision+1)
            "CONTENT"->value.copy(payload=boardContentAfter(value,b.getJSONObject("payload")),contentVersion=b.getInt("baseVersion")+1,revision=value.revision+1)
            "DELETE"->value.copy(deleted=true)
            else->value
        }
    }
    return value
}
