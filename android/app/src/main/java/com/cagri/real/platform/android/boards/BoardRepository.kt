package com.cagri.real.platform.android.boards

import com.cagri.real.boards.*
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.platform.android.sync.SyncLocal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.util.UUID

/** Durable item-level outbox. Network I/O never holds the local mutation lock. */
class BoardRepository(private val store:BoardStore,private val uid:String,private val name:String,val assets:BoardAssets) {
    private val local=Mutex();
    private val network=Mutex();
    private var inFlight:String?=null
    private val mutable=MutableStateFlow(PanoState());
    val state:StateFlow<PanoState> = mutable.asStateFlow()
    private val notifyMutable=MutableStateFlow<Set<String>>(emptySet())
    /** Boards this user wants push notifications for (mirrors the server; filled by sync). */
    val notifyBoards:StateFlow<Set<String>> = notifyMutable.asStateFlow()
    suspend fun setNotify(board:String,enabled:Boolean){check(onlineAccount){"Sign in to get notifications."}
        client.request("boards/$board/notify","PUT",JSONObject().put("enabled",enabled))
        notifyMutable.value=if(enabled)notifyMutable.value+board else notifyMutable.value-board}
    private val client get()=BoardClient(uid)
    private val onlineAccount get()=uid!="guest"&&AndroidBackend.auth.currentUser()?.id==uid
    suspend fun load()=local.withLock { withContext(Dispatchers.IO){if(!mutable.value.loaded)mutable.value=store.load()} }
    private suspend fun change(f:(PanoState)->PanoState)=local.withLock { withContext(Dispatchers.IO){val next=f(mutable.value);
            if (!mutable.value.samePersistentContent(next)) store.save(next);
            mutable.value=next} }
    private fun updateItem(s:PanoState,i:PanoItem)=s.copy(items=s.items+(i.boardId to (s.items[i.boardId].orEmpty().filterNot { it.id==i.id }+i)))
    suspend fun create(title:String,shared:Boolean):String {
        require(title.trim().isNotEmpty()&&title.length<=300)
        val id=UUID.randomUUID().toString();
        val board=PanoBoard(id,title.trim(),uid,name,shared,updatedAt=System.currentTimeMillis())
        val body=JSONObject().put("id",id).put("title",board.title).put("shared",shared)
        change { it.copy(boards=it.boards+board,queue=it.queue+BoardMutation(UUID.randomUUID().toString(),id,null,"BOARD",body.toString())) };
        return id
    }
    suspend fun add(board:String,type:BoardBlockType,transform:BoardTransform,payload:JSONObject,cardKey:Long?=null,includePrivate:Boolean=false):String {
        val id=UUID.randomUUID().toString();
        val t=transform.clamped(type)
        val item=PanoItem(id,board,type,t,payload.toString(),uid,name)
        val body=JSONObject().put("id",id).put("type",type.name).put("transform",t.json()).put("payload",payload).apply {cardKey?.let { put("cardKey",it.toString());
                put("includePrivate",includePrivate) }}
        change {
            check(it.boards.any {b->b.id==board}) {"Board is unavailable"}
            updateItem(it,item).copy(queue=it.queue+BoardMutation(UUID.randomUUID().toString(),board,id,"ADD",body.toString()))
        };
        return id
    }
    suspend fun transform(item:PanoItem,t:BoardTransform)=edit(item,"TRANSFORM",JSONObject().put("transform",t.clamped(item.type).json()),item.transformVersion)
    suspend fun content(item:PanoItem,p:JSONObject)=edit(item,"CONTENT",JSONObject().put("payload",p),item.contentVersion)
    suspend fun delete(item:PanoItem)=edit(item,"DELETE",JSONObject(),item.revision)
    private suspend fun edit(item:PanoItem,kind:String,p:JSONObject,base:Int) {
        val op=UUID.randomUUID().toString();
        p.put("operationId",op).put("baseVersion",base);
        if(kind!="DELETE")p.put("group",kind.lowercase())
        change { s->
            if(s.boards.none {it.id==item.boardId})return@change s
            val current=s.items[item.boardId].orEmpty().firstOrNull { it.id==item.id }?:item
            val previous=s.queue.lastOrNull { it.itemId==item.id&&it.kind==kind&&it.id!=inFlight }
            // Coalesce only unsent drafts; preserve a sent operation's body and receipt id.
            if(previous!=null)p.put("baseVersion",JSONObject(previous.body).getInt("baseVersion"))
            val pendingAdd=s.queue.firstOrNull {it.itemId==item.id&&it.kind=="ADD"}
            if(kind=="DELETE"&&pendingAdd!=null)p.put("baseVersion",1)
            val retained=if(kind=="DELETE"&&pendingAdd!=null)s.queue.filterNot {it.itemId==item.id&&it.id!=inFlight}else s.queue.filterNot {it.id==previous?.id}
            val q=retained+BoardMutation(op,item.boardId,item.id,kind,p.toString())
            val changed=when(kind){
                "TRANSFORM"->current.copy(transform=BoardTransform.from(p.getJSONObject("transform")),transformVersion=p.getInt("baseVersion")+1,revision=current.revision+if(previous==null)1 else 0)
                "CONTENT"->current.copy(payload=boardContentAfter(current,p.getJSONObject("payload")),contentVersion=p.getInt("baseVersion")+1,revision=current.revision+if(previous==null)1 else 0)
                else->current.copy(deleted=true,revision=current.revision+1)
            }
            updateItem(s,changed).copy(queue=q,error=null)
        }
    }
    suspend fun viewport(board:String,v:BoardViewport)=change { it.copy(viewports=it.viewports+(board to v.clamped())) }
    /** Device-local look of a board; never queued or synced. */
    suspend fun background(board:String,name:String)=change { it.copy(backgrounds=it.backgrounds+(board to name)) }
    suspend fun sync(activeBoard:String?)=network.withLock {
        if(!onlineAccount)return@withLock
        mutable.value=mutable.value.copy(syncing=true,error=null)
        try {
            if(mutable.value.conflict==null)flush()
            val result=client.request("boards")
            val remote=result.getJSONArray("boards").objects().map(PanoBoard::from)
            // The list call now carries every board's people, so the gallery shows names without opening boards.
            val fresh=result.optJSONObject("members")?.let{o->o.keys().asSequence().associateWith{k->o.getJSONArray(k).objects().map{PanoMember(it.getString("uid"),it.getString("name"),it.getString("role"))}}}.orEmpty()
            result.optJSONArray("notifyBoards")?.let{a->notifyMutable.value=(0 until a.length()).map{a.getString(it)}.toSet()}
            change { s->val pending=s.queue.filter { it.kind=="BOARD" }.map { it.boardId }.toSet();
                val allowed=(remote.map { it.id }+pending).toSet();
                s.copy(boards=remote+s.boards.filter { it.id in pending&&remote.none { b->b.id==it.id } },items=s.items.filterKeys { it in allowed },cursors=s.cursors.filterKeys { it in allowed },members=s.members.filterKeys { it in allowed }+fresh.filterKeys { it in allowed },invites=result.getJSONArray("invites").objects().map { PanoInvite(it.getString("id"),it.getString("title"),it.getString("owner_name")) }) }
            if(activeBoard!=null&&mutable.value.boards.any { it.id==activeBoard }&&mutable.value.queue.none { it.kind=="BOARD"&&it.boardId==activeBoard })pull(activeBoard)
        }catch(c:CancellationException){throw c}catch(e:Exception){mutable.value=mutable.value.copy(error=e.message?:"Offline. Local changes are saved.")}
        finally{inFlight=null;
            mutable.value=mutable.value.copy(syncing=false)}
    }
    private suspend fun flush() {
        var count=0
        while(onlineAccount&&count++<100){
            val op=local.withLock { mutable.value.queue.firstOrNull()?.also { inFlight=it.id } }?:return
            val p=JSONObject(op.body)
            try{
                val path="boards/${op.boardId}";
                val result=when(op.kind){
                    "BOARD"->client.request("boards","POST",p)
                    "ADD"->{if(p.optString("type")=="CARD")SyncLocal.syncForSharing();
                        if(p.optString("type")=="STACK")assets.ensureStackUploaded(op.boardId,p.getJSONObject("payload"),client);
                        if(p.getJSONObject("payload").has("assetId"))assets.ensureUploaded(op.boardId,p.getJSONObject("payload"),client);
                        client.request("$path/items","POST",p)}
                    "DELETE"->client.request("$path/items/${op.itemId}","DELETE",p)
                    else->{
                        if(op.kind=="CONTENT" && mutable.value.items[op.boardId].orEmpty().any {it.id==op.itemId && it.type==BoardBlockType.STACK})
                            assets.ensureStackUploaded(op.boardId,p.getJSONObject("payload"),client)
                        client.request("$path/items/${op.itemId}","PATCH",p)
                    }
                }
                change { s->var next=s.copy(queue=s.queue.filterNot { it.id==op.id });
                    result.optJSONObject("board")?.let { b->next=next.copy(boards=next.boards.filterNot { it.id==op.boardId }+PanoBoard.from(b)) }
                    result.optJSONObject("item")?.let { raw->next=updateItem(next,overlayBoardItem(PanoItem.from(raw),next.queue)) };
                    next }
            }catch(e:BoardHttpFailure){
                if(e.status==409&&e.result.has("item")){mutable.value=mutable.value.copy(conflict=BoardConflict(op.id,PanoItem.from(e.result.getJSONObject("item"))),error="A block changed on another device. Choose which edit to keep.");
                    return}
                if((e.status==410||(e.status==404&&op.kind=="DELETE"&&e.result.optString("error")=="ITEM_NOT_FOUND"))&&op.itemId!=null){change { s->s.copy(queue=s.queue.filterNot { it.itemId==op.itemId },items=s.items+(op.boardId to s.items[op.boardId].orEmpty().map { if(it.id==op.itemId)it.copy(deleted=true) else it })) };
                    continue}
                if(e.status==404&&e.result.optString("error")=="BOARD_NOT_FOUND"){change { s->s.copy(boards=s.boards.filterNot { it.id==op.boardId },items=s.items-op.boardId,cursors=s.cursors-op.boardId,queue=s.queue.filterNot { it.boardId==op.boardId }) };
                    continue}
                throw e
            }finally{inFlight=null}
        }
    }
    private suspend fun pull(board:String) {
        var more:Boolean
        do {
            val cursor=mutable.value.cursors[board]?:0L
            val r=client.request("boards/$board?cursor=$cursor");
            val nextCursor = r.getLong("cursor")
            check(nextCursor >= cursor && (!r.optBoolean("hasMore") || nextCursor > cursor)) { "Board feed did not advance" }
            val incoming=r.getJSONArray("items").objects().map(PanoItem::from)
            change { s->val q=s.queue;
                val localItems=s.items[board].orEmpty();
                val ids=incoming.map { it.id }.toSet()
                val kept=if(cursor==0L)localItems.filter { i->i.id !in ids&&q.any { it.itemId==i.id } }else localItems.filterNot {it.id in ids}
                val merged=kept+incoming.map {overlayBoardItem(it,q)}
                s.copy(conflict=s.conflict?.let {c->incoming.firstOrNull {it.id==c.serverItem.id}?.let {c.copy(serverItem=it)}?:c},items=s.items+(board to merged),cursors=s.cursors+(board to r.getLong("cursor")),members=s.members+(board to r.getJSONArray("members").objects().map {PanoMember(it.getString("uid"),it.getString("name"),it.getString("role"))}))
            }
            more=r.optBoolean("hasMore")
        }while(more&&onlineAccount)
    }
    suspend fun resolveConflict(keepMine:Boolean) {
        change { s->val conflict=s.conflict?:return@change s;
            val op=s.queue.firstOrNull { it.id==conflict.mutationId }?:return@change s.copy(conflict=null)
            val others=s.queue.filterNot { it.itemId==op.itemId&&it.kind==op.kind };
            var q=others
            if(keepMine&&!conflict.serverItem.deleted){val p=JSONObject(op.body);
                val v=when(op.kind){"TRANSFORM"->conflict.serverItem.transformVersion;
                    "CONTENT"->conflict.serverItem.contentVersion;
                    else->conflict.serverItem.revision};
                val id=UUID.randomUUID().toString();
                p.put("operationId",id).put("baseVersion",v);
                val latest=s.queue.last { it.itemId==op.itemId&&it.kind==op.kind };
                val latestBody=JSONObject(latest.body);
                if(op.kind=="CONTENT")p.put("payload",latestBody.getJSONObject("payload"));
                if(op.kind=="TRANSFORM")p.put("transform",latestBody.getJSONObject("transform"));
                q=listOf(op.copy(id=id,body=p.toString()))+others}
            updateItem(s.copy(queue=q,conflict=null,error=null),overlayBoardItem(conflict.serverItem,q))
        }
    }
    suspend fun invite(board:String,email:String){check(onlineAccount){"Sign in to invite people."};
        sync(board);
        check(mutable.value.queue.none {it.kind=="BOARD"&&it.boardId==board}){"Sync this board before inviting people."};
        client.request("boards/$board/invites","POST",JSONObject().put("id",UUID.randomUUID().toString()).put("email",email));
        pull(board);
        val b=mutable.value.boards.first { it.id==board };
        change { s->s.copy(boards=s.boards.filterNot { it.id==board }+b.copy(shared=true)) } }
    suspend fun inviteConnection(board:String,recipientUid:String){check(onlineAccount){"Sign in to invite people."};
        sync(board);
        check(mutable.value.queue.none {it.kind=="BOARD"&&it.boardId==board}){"Sync this board before inviting people."};
        client.request("boards/$board/invites","POST",JSONObject().put("id",UUID.randomUUID().toString()).put("recipientUid",recipientUid));
        pull(board);
        val b=mutable.value.boards.first { it.id==board };
        change { s->s.copy(boards=s.boards.filterNot { it.id==board }+b.copy(shared=true)) } }
    suspend fun accept(invite:String){check(onlineAccount);
        client.request("invites/$invite/accept","POST");
        sync(null)}
    suspend fun removeMember(board:String,member:String){client.request("boards/$board/members/${java.net.URLEncoder.encode(member,"UTF-8")}","DELETE");
        pull(board)}
    /** Existing self-removal API; serialize against sync so an old pull cannot restore membership. */
    suspend fun leaveBoard(board:String)=network.withLock {
        check(onlineAccount){"Connect to leave a shared board."}
        check(mutable.value.boards.firstOrNull { it.id==board }?.role=="MEMBER") {"Only invited members can leave a board."}
        try {
            client.request("boards/$board/members/${java.net.URLEncoder.encode(uid,"UTF-8")}","DELETE")
        } catch(e:BoardHttpFailure) {
            // A retry after an uncertain successful leave sees revoked access, not a member row.
            if(e.status!=404 || e.result.optString("error")!="BOARD_NOT_FOUND")throw e
        }
        withContext(NonCancellable) { change { it.withoutBoard(board) } }
    }
    suspend fun deleteBoard(board:String){check(onlineAccount){"Connect to remove a cloud board."};
        client.request("boards/$board","DELETE");
        change { it.copy(boards=it.boards.filterNot { b->b.id==board },items=it.items-board,cursors=it.cursors-board,queue=it.queue.filterNot { q->q.boardId==board }) } }
}
