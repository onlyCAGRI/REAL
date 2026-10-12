package com.cagri.real.boards

import kotlin.math.*
import org.json.JSONObject
import org.json.JSONArray

/** Lowest board zoom (1%). Used by pinch, the zoom buttons and persisted viewports. */
const val BOARD_MIN_ZOOM = 0.01f
const val BOARD_MAX_ZOOM = 3f

/** Canonical clockwise degrees. Old signed angles retain exactly the same visual orientation. */
fun normalizedBoardRotation(degrees: Float): Float {
    require(degrees.isFinite())
    return ((degrees % 360f + 540f) % 360f) - 180f
}

/** Drop only the board being left; preserve other boards and their independent outboxes. */
fun PanoState.withoutBoard(boardId: String): PanoState = copy(
    boards = boards.filterNot { it.id == boardId }, items = items - boardId,
    members = members - boardId, viewports = viewports - boardId,
    cursors = cursors - boardId, backgrounds = backgrounds - boardId,
    queue = queue.filterNot { it.boardId == boardId },
    conflict = conflict?.takeUnless { it.serverItem.boardId == boardId }
)

enum class BoardBlockType { TEXT, CARD, CHECKLIST, LOCATION, PHOTO, GIF, MUSIC, MEDIA, MOOD, ATTACHMENT, VOICE, LINK, STACK }
data class BlockBounds(val minWidth: Float, val minHeight: Float, val maxWidth: Float, val maxHeight: Float)
fun BoardBlockType.bounds() = when (this) {
    BoardBlockType.TEXT -> BlockBounds(120f,64f,640f,640f)
    BoardBlockType.CARD -> BlockBounds(220f,120f,720f,1200f)
    BoardBlockType.STACK -> BlockBounds(220f,120f,1000f,1200f)
    BoardBlockType.CHECKLIST -> BlockBounds(180f,120f,640f,900f)
    BoardBlockType.LOCATION -> BlockBounds(180f,120f,1000f,1000f)
    BoardBlockType.PHOTO -> BlockBounds(100f,100f,800f,800f)
    BoardBlockType.GIF -> BlockBounds(100f,100f,640f,640f)
    BoardBlockType.MUSIC, BoardBlockType.MEDIA, BoardBlockType.MOOD,
    BoardBlockType.ATTACHMENT, BoardBlockType.VOICE, BoardBlockType.LINK -> BlockBounds(220f,120f,720f,900f)
}
data class BoardTransform(val x: Float=0f,val y: Float=0f,val width: Float=280f,val height: Float=180f,val rotation: Float=0f,val z: Int=0) {
    fun clamped(type: BoardBlockType): BoardTransform {
        require(listOf(x,y,width,height,rotation).all { it.isFinite() })
        val b=type.bounds();
        var w=width.coerceIn(b.minWidth,b.maxWidth);
        var h=height.coerceIn(b.minHeight,b.maxHeight)
        if(type==BoardBlockType.PHOTO||type==BoardBlockType.GIF){w=min(w,h*4);
            h=min(h,w*4)}
        return copy(x=x.coerceIn(-100000f,100000f),y=y.coerceIn(-100000f,100000f),width=w,height=h,rotation=normalizedBoardRotation(rotation),z=z.coerceIn(0,1000000000))
    }
    fun json()=JSONObject().put("x",x.toDouble()).put("y",y.toDouble()).put("width",width.toDouble()).put("height",height.toDouble()).put("rotation",rotation.toDouble()).put("z",z)
    companion object {fun from(o: JSONObject)=BoardTransform(o.optDouble("x",0.0).toFloat(),o.optDouble("y",0.0).toFloat(),o.optDouble("width",280.0).toFloat(),o.optDouble("height",180.0).toFloat(),o.optDouble("rotation",0.0).toFloat(),o.optInt("z",0))}
}
data class BoardViewport(val x: Float=24f,val y: Float=24f,val zoom: Float=1f) {
    fun clamped(): BoardViewport {require(listOf(x,y,zoom).all { it.isFinite() });
        return copy(x=x.coerceIn(-300000f,300000f),y=y.coerceIn(-300000f,300000f),zoom=zoom.coerceIn(BOARD_MIN_ZOOM,BOARD_MAX_ZOOM))}
    fun json()=JSONObject().put("x",x.toDouble()).put("y",y.toDouble()).put("zoom",zoom.toDouble())
    companion object {fun from(o:JSONObject)=BoardViewport(o.optDouble("x",24.0).toFloat(),o.optDouble("y",24.0).toFloat(),o.optDouble("zoom",1.0).toFloat()).clamped()}
}
data class PanoBoard(val id:String,val title:String,val ownerUid:String,val ownerName:String,val shared:Boolean,val role:String="OWNER",val updatedAt:Long=0) {
    fun json()=JSONObject().put("id",id).put("title",title).put("owner_uid",ownerUid).put("owner_name",ownerName).put("shared",if(shared)1 else 0).put("role",role).put("updated_at",updatedAt)
    companion object {fun from(o:JSONObject)=PanoBoard(o.getString("id"),o.getString("title"),o.getString("owner_uid"),o.optString("owner_name","REAL user"),o.optInt("shared")==1,o.optString("role","OWNER"),o.optLong("updated_at"))}
}
data class PanoItem(val id:String,val boardId:String,val type:BoardBlockType,val transform:BoardTransform,val payload:String,val authorUid:String,val authorName:String,val transformVersion:Int=1,val contentVersion:Int=1,val revision:Int=1,val deleted:Boolean=false) {
    fun content()=JSONObject(payload)
    fun json()=JSONObject().put("id",id).put("board_id",boardId).put("type",type.name).put("transform",transform.json()).put("payload",content()).put("author_uid",authorUid).put("author_name",authorName).put("transform_version",transformVersion).put("content_version",contentVersion).put("revision",revision).put("deleted_at",if(deleted)1 else JSONObject.NULL)
    companion object {fun from(o:JSONObject)=PanoItem(o.getString("id"),o.getString("board_id"),BoardBlockType.valueOf(o.getString("type")),BoardTransform.from(o.getJSONObject("transform")),o.getJSONObject("payload").toString(),o.getString("author_uid"),o.optString("author_name","REAL user"),o.optInt("transform_version",1),o.optInt("content_version",1),o.optInt("revision",1),!o.isNull("deleted_at"))}
}
data class PanoMember(val uid:String,val name:String,val role:String)
data class PanoInvite(val id:String,val title:String,val ownerName:String)
data class BoardMutation(val id:String,val boardId:String,val itemId:String?,val kind:String,val body:String) {
    fun json()=JSONObject().put("id",id).put("board",boardId).put("item",itemId?:JSONObject.NULL).put("kind",kind).put("body",JSONObject(body))
    companion object {fun from(o:JSONObject)=BoardMutation(o.getString("id"),o.getString("board"),if(o.isNull("item"))null else o.getString("item"),o.getString("kind"),o.getJSONObject("body").toString())}
}
data class PanoState(val boards:List<PanoBoard> = emptyList(),val items:Map<String,List<PanoItem>> = emptyMap(),val members:Map<String,List<PanoMember>> = emptyMap(),val invites:List<PanoInvite> = emptyList(),val queue:List<BoardMutation> = emptyList(),val viewports:Map<String,BoardViewport> = emptyMap(),val cursors:Map<String,Long> = emptyMap(),val syncing:Boolean=false,val error:String?=null,val conflict:BoardConflict?=null,val loaded:Boolean=false,val backgrounds:Map<String,String> = emptyMap())
data class BoardConflict(val mutationId:String,val serverItem:PanoItem)
