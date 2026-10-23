package com.cagri.real.ui.pano

import com.cagri.real.i18n.t
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.cagri.real.data.boards.BoardRealContent

import android.net.Uri
import coil.imageLoader
import com.cagri.real.boards.*
import com.cagri.real.data.*
import com.cagri.real.platform.android.boards.BoardRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.*

/** UI commands only; persistence/versioning belong to repository, gestures to canvas. */
class PanoController(val repository:BoardRepository,private val scope:CoroutineScope,context:android.content.Context) {
    val imageLoader=context.applicationContext.imageLoader
    private val edits = BoardEditSession()
    var editingBoard by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    fun beginEdit(board: String) { edits.begin(board); editingBoard = board }
    fun endEdit() { edits.finish(); editingBoard = null }
    fun canEdit(board: String) = edits.allows(board)
    fun registerDraft(id: String, save: () -> Unit) = edits.register(id, save)
    fun unregisterDraft(id: String) = edits.unregister(id)
    val message=MutableStateFlow<String?>(null)
    fun run(action:suspend ()->Unit) {scope.launch {try{message.value=null;
                action()}catch(c:CancellationException){throw c}catch(e:Exception){message.value=e.message?:"Could not update Pano."}}}
    private fun save(action:suspend ()->Unit){scope.launch(start=CoroutineStart.UNDISPATCHED){try{withContext(NonCancellable){action()}}catch(e:Exception){message.value=e.message?:"Could not save Pano locally."}}}
    fun transform(item:PanoItem,t:BoardTransform){ if (canEdit(item.boardId)) save {repository.transform(item,t)} }
    fun content(item:PanoItem,p:JSONObject){ if (canEdit(item.boardId)) save {repository.content(item,p)} }
    fun delete(item:PanoItem){ if (canEdit(item.boardId)) save {repository.delete(item)} }
    fun viewport(board:String,v:BoardViewport)=save {repository.viewport(board,v)}
    fun background(board:String,look:String)=save {repository.background(board,look)}
    fun add(board:String,type:BoardBlockType,t:BoardTransform,onAdded:(String)->Unit,card:ReminderItem?=null,location:ReminderLocation?=null,uri:Uri?=null,includePrivate:Boolean=false,stackView:Boolean=false)=run {
        check(canEdit(board)) {"Enable Edit to add content"}
        val payload=when(type){
            BoardBlockType.STACK->JSONObject().put("stack",CardStackBlock.encode(CardStackBlock(cards=(1..3).map { CardStackCard(title=t("Card {0}", it)) }))).put("images",JSONArray())
            BoardBlockType.TEXT->JSONObject().put("text","").put("title",false)
            BoardBlockType.CHECKLIST->BoardRealContent.checklistPayload(JSONObject().put("title",""), emptyList())
            BoardBlockType.LOCATION->BoardRealContent.locationPayload(checkNotNull(location))
            BoardBlockType.PHOTO,BoardBlockType.GIF->repository.assets.import(checkNotNull(uri),type==BoardBlockType.GIF)
            BoardBlockType.CARD->BoardRealContent.cardSnapshot(checkNotNull(card),includePrivate,stackView)
            else->error("Use the canonical REAL content result")
        }
        val sized=if(type==BoardBlockType.PHOTO||type==BoardBlockType.GIF)t.copy(height=(t.width/payload.optDouble("aspect",1.0).toFloat()).coerceIn(100f,800f))else if(stackView || type==BoardBlockType.STACK)t.copy(width=360f,height=650f) else t
        check(canEdit(board)) {"Editing was cancelled"}
        onAdded(repository.add(board,type,sized,payload,card?.id,includePrivate))
    }
    fun stackContent(item:PanoItem,block:CardStackBlock,removedImages:Set<String>) {
        if(!canEdit(item.boardId))return
        save {
            val payload=repository.assets.stageStack(block,item.content(),removedImages)
            repository.content(item,payload)
        }
    }
    private fun requireDestination(request:com.cagri.real.content.ContentRequest,type:BoardBlockType):com.cagri.real.content.ContentDestination.Board {
        check(request.isCurrent(System.currentTimeMillis(),com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.id?:"guest",type.name)){"Content destination is stale"}
        val target=request.destination as? com.cagri.real.content.ContentDestination.Board ?: error("Expected board destination")
        check(canEdit(target.boardId)) {"Editing was cancelled"}
        check(repository.state.value.boards.any{it.id==target.boardId}){"Board is unavailable"}
        return target
    }
    suspend fun addCanonical(request:com.cagri.real.content.ContentRequest,type:BoardBlockType,payload:JSONObject,stillActive:()->Boolean={true}):String {
        check(stillActive()) {"Content destination was cancelled"}
        val target=requireDestination(request,type)
        require(payload.toString().toByteArray(Charsets.UTF_8).size<=60000){"Content exceeds the board payload limit"}
        var staged:JSONObject?=null
        try {
            val result=if(type==BoardBlockType.ATTACHMENT||type==BoardBlockType.VOICE)
                repository.assets.stageContent(type,payload).also{staged=it} else payload
            check(stillActive()) {"Content destination was cancelled"}
            requireDestination(request,type)
            return repository.add(target.boardId,type,target.insertion,result)
        } catch(e:Exception){staged?.let{repository.assets.discardStaged(it)};throw e}
    }
    fun addKeyboardImage(request:com.cagri.real.content.ContentRequest,uri:Uri,permission:android.view.inputmethod.InputContentInfo?,stillActive:()->Boolean,onAdded:(String)->Unit) {
        scope.launch(start=CoroutineStart.UNDISPATCHED) {
            try { onAdded(addPhoto(request,uri,stillActive)) }
            catch(c:CancellationException){throw c}
            catch(e:Exception){message.value=e.message?:"Could not add keyboard image."}
            finally { permission?.releasePermission() }
        }
    }
    suspend fun addPhoto(request:com.cagri.real.content.ContentRequest,uri:Uri,stillActive:()->Boolean={true}):String {
        val type=BoardBlockType.valueOf(request.kind)
        require(type==BoardBlockType.PHOTO||type==BoardBlockType.GIF)
        check(stillActive()) {"Content destination was cancelled"}
        val target=requireDestination(request,type)
        val callerJob=currentCoroutineContext()[Job]
        var staged:JSONObject?=null
        var committed=false
        try {
            return withContext(NonCancellable) {
                val payload=repository.assets.import(uri,type==BoardBlockType.GIF).also{staged=it}
                check(stillActive() && callerJob?.isActive!=false) {"Content destination was cancelled"}
                requireDestination(request,type)
                val t=target.insertion.copy(height=(target.insertion.width/payload.optDouble("aspect",1.0).toFloat()).coerceIn(100f,800f))
                repository.add(target.boardId,if(payload.optString("mime")=="image/gif")BoardBlockType.GIF else BoardBlockType.PHOTO,t,payload).also { committed=true }
            }
        }catch(e:Exception){if(!committed)staged?.let{repository.assets.discardStaged(it)};throw e}
    }

}
