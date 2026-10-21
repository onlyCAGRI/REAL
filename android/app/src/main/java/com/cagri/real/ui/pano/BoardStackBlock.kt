package com.cagri.real.ui.pano

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.cagri.real.boards.PanoItem
import com.cagri.real.data.CardStackBlock
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.CardStackContentSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Native stack model/editor, saved as an independent board item, never a reminder. */
@Composable
internal fun BoardStackBlock(item:PanoItem,controller:PanoController,style:DesignStyle,inEditor:Boolean=false,detail:Boolean=false) {
    val editable=inEditor && controller.editingBoard==item.boardId
    var draft by remember(item.id) { mutableStateOf(CardStackBlock.decode(item.content().optJSONObject("stack")) ?: CardStackBlock()) }
    var dirty by remember(item.id) { mutableStateOf(false) }
    var removedImages by remember(item.id) {mutableStateOf(emptySet<String>())}
    var base by remember(item.id) { mutableStateOf(item) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    val presentation=LocalBoardRealPresentation.current
    fun save() {
        if(dirty) { controller.stackContent(base,draft,removedImages);dirty=false }
    }
    val latestSave by rememberUpdatedState {save()}
    if(inEditor) DisposableEffect(item.id,controller) {
        val key=item.id+":stack-editor"
        controller.registerDraft(key) {latestSave()}
        onDispose {latestSave();controller.unregisterDraft(key)}
    }
    LaunchedEffect(item.payload) {
        if(!dirty) {
            val next=CardStackBlock.decode(item.content().optJSONObject("stack")) ?: return@LaunchedEffect
            val images=item.content().optJSONArray("images")
            val paths=mutableMapOf<String,String>()
            if(images!=null) for(i in 0 until images.length()) {
                val image=images.getJSONObject(i)
                try { paths[image.getString("cardId")]=controller.repository.assets.cached(item.boardId,image.getString("assetId")).path }
                catch(e:CancellationException) {throw e}
                catch(_:Exception) {error="Some stack photos are unavailable offline."}
            }
            if(!dirty) draft=next.copy(cards=next.cards.map { card->paths[card.id]?.let {card.copy(imageUri=it)}?:card })
        }
    }
    LaunchedEffect(draft,dirty) {if(dirty) {delay(350);save()}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        CardStackContentSection(draft,style,editing=editable,
            onRequestInlineEdit={if(!detail) {if(controller.canEdit(item.boardId))presentation.onEditStack(item) else presentation.onPreviewCard(item)}},
            onUpdate={next->
                if(controller.canEdit(item.boardId) && inEditor) {
                    if(!dirty)base=item
                    val nextById=next.cards.associateBy {it.id}
                    val removed=draft.cards.filter {it.imageUri!=null && nextById[it.id]?.imageUri==null}.map {it.id}.toSet()
                    val replaced=next.cards.filter {it.imageUri!=null}.map {it.id}.toSet()
                    removedImages=(removedImages+removed)-replaced
                    draft=next;dirty=true
                }
            },onRemove={},showRemoveControl=false)
        error?.let {Text(it,color=Color.Gray)}
    }
}
