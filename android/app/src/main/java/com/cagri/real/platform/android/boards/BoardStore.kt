package com.cagri.real.platform.android.boards

import android.content.Context
import android.util.AtomicFile
import com.cagri.real.boards.*
import org.json.*
import java.io.File
import java.security.MessageDigest

/** Account-scoped atomic snapshots include durable outbox and viewport, never per-pixel writes. */
class BoardStore(context:Context,uid:String) {
    val directory=File(context.filesDir,"pano/"+MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString(""){"%02x".format(it)}).apply { mkdirs() }
    val assetsDirectory=File(context.filesDir,"pano_assets/"+directory.name).apply { mkdirs() }
    private val file=AtomicFile(File(directory,"state.json"))
    fun load():PanoState {
        if(!file.baseFile.exists())return PanoState(loaded=true)
        val o=JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        val boards=o.getJSONArray("boards").objects().map(PanoBoard::from)
        val items=o.getJSONArray("items").objects().map(PanoItem::from).groupBy { it.boardId }
        val queue=o.getJSONArray("queue").objects().map(BoardMutation::from)
        val views=o.optJSONObject("viewports")?:JSONObject()
        val cursors=o.optJSONObject("cursors")?:JSONObject()
        val looks=o.optJSONObject("backgrounds")?:JSONObject()
        return PanoState(boards=boards,items=items,queue=queue,cursors=cursors.keys().asSequence().associateWith { cursors.getLong(it) },viewports=views.keys().asSequence().associateWith { BoardViewport.from(views.getJSONObject(it)) },backgrounds=looks.keys().asSequence().associateWith { looks.getString(it) },loaded=true)
    }
    fun save(s:PanoState) {
        val o=JSONObject().put("boards",JSONArray().apply { s.boards.forEach { put(it.json()) } }).put("items",JSONArray().apply { s.items.values.flatten().forEach { put(it.json()) } }).put("queue",JSONArray().apply { s.queue.forEach { put(it.json()) } }).put("cursors",JSONObject().apply { s.cursors.forEach { (id,c)->put(id,c) } }).put("viewports",JSONObject().apply { s.viewports.forEach { (id,v)->put(id,v.json()) } }).put("backgrounds",JSONObject().apply { s.backgrounds.forEach { (id,b)->put(id,b) } })
        val stream=file.startWrite();
        try{stream.write(o.toString().toByteArray());
            file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);
            throw e}
    }
}
internal fun JSONArray.objects()=(0 until length()).map { getJSONObject(it) }
