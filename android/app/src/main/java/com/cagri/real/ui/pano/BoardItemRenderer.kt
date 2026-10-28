package com.cagri.real.ui.pano

import com.cagri.real.i18n.t
import com.cagri.real.data.boards.BoardRealContent

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.cagri.real.boards.*
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.openReminderLocationInMaps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import org.json.*
import java.io.File

@Composable
fun BoardItemContent(item:PanoItem,selected:Boolean,controller:PanoController,designStyle:DesignStyle,editable:Boolean=false){
    when(item.type){
        BoardBlockType.TEXT->BoardTextBlock(item,selected && editable,controller,designStyle)
        BoardBlockType.STACK->BoardStackBlock(item,controller,designStyle)
        BoardBlockType.CHECKLIST->BoardChecklistBlock(item,selected && editable,controller,designStyle,editable)
        BoardBlockType.LOCATION->BoardLocationBlock(item,controller)
        BoardBlockType.PHOTO,BoardBlockType.GIF->BoardImageBlock(item,controller,designStyle)
        BoardBlockType.CARD->BoardCardBlock(item,controller,designStyle,editable)
        BoardBlockType.MUSIC,BoardBlockType.MEDIA,BoardBlockType.MOOD,
        BoardBlockType.ATTACHMENT,BoardBlockType.VOICE,BoardBlockType.LINK->BoardCanonicalBlock(item,controller,designStyle,editable)
    }
}

@Composable
private fun BoardTextBlock(item:PanoItem,selected:Boolean,controller:PanoController,style:DesignStyle){
    val content=item.content();
    var draft by remember(item.id){mutableStateOf(content.optString("text"))}
    var dirty by remember(item.id){mutableStateOf(false)};
    var base by remember(item.id){mutableStateOf(item)}
    val latestTitle by rememberUpdatedState(content.optBoolean("title"))
    val colorKey by rememberUpdatedState(content.optString("color").ifBlank{"white"})
    val alignKey by rememberUpdatedState(content.optString("align").ifBlank{"left"})
    // Optional keys are written only when changed from the defaults, so untouched blocks keep their old payload.
    fun payload(title:Boolean=latestTitle,color:String=colorKey,align:String=alignKey):JSONObject{
        val o=JSONObject().put("text",draft).put("title",title)
        if(color!="white")o.put("color",color)
        if(align!="left")o.put("align",align)
        return o}
    fun save(){if(dirty){controller.content(base,payload());
            dirty=false}}
    fun restyle(title:Boolean=latestTitle,color:String=colorKey,align:String=alignKey){
        controller.content(if(dirty)base else item,payload(title,color,align));dirty=false}
    val latestSave by rememberUpdatedState { save() }
    DisposableEffect(item.id, controller) {
        controller.registerDraft(item.id) { latestSave() }
        onDispose { latestSave(); controller.unregisterDraft(item.id) }
    }
    LaunchedEffect(selected){if(!selected)save()}
    LaunchedEffect(item.payload){if(!dirty)draft=content.optString("text")}
    LaunchedEffect(draft,dirty){if(dirty){kotlinx.coroutines.delay(350);
            save()}}
    val host=LocalPanoTextStyleHost.current
    val applyStyle by rememberUpdatedState<(Boolean,String,String)->Unit> { title,color,align ->
        restyle(title,color,align)
    }
    // Reuse the same action object until selection/style changes. Assigning a fresh object
    // into parent-observed state on every SideEffect can continuously recompose this block.
    val actions=remember(selected,colorKey,alignKey,latestTitle) {
        if(selected)PanoTextStyleActions(colorKey,alignKey,latestTitle,
            setColor={applyStyle(latestTitle,it,alignKey)},
            setAlign={applyStyle(latestTitle,colorKey,it)},
            toggleTitle={applyStyle(!latestTitle,colorKey,alignKey)}) else null
    }
    SideEffect{host?.actions=actions}
    DisposableEffect(host){onDispose{host?.actions=null}}
    val textColor=panoTextColor(colorKey)
    val textAlign=panoTextAlign(alignKey)
    Column(Modifier.fillMaxSize()){
        if(selected){
            Box(Modifier.fillMaxSize().panoSurface(18.dp).padding(14.dp)){
                BasicBoardText(draft,{if(!dirty)base=item;
                    draft=it.take(10000);
                    dirty=true},latestTitle,textColor,textAlign,Modifier.fillMaxSize().onFocusChanged{if(!it.hasFocus)save()})}
        }else Box(Modifier.fillMaxSize().panoSurface(18.dp).verticalScroll(rememberScrollState()).padding(14.dp)){
            Text(draft.ifBlank{t("Tap to write")},color=if(draft.isBlank())PanoMuted else textColor,textAlign=textAlign,
                fontSize=if(latestTitle)28.sp else 18.sp,fontWeight=if(latestTitle)FontWeight.SemiBold else FontWeight.Normal,
                modifier=Modifier.fillMaxWidth())}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BasicBoardText(value:String,onValue:(String)->Unit,title:Boolean,color:Color,align:androidx.compose.ui.text.style.TextAlign,modifier:Modifier){
    val state=rememberTextFieldState(value)
    val limit=remember { object:InputTransformation {
        override fun TextFieldBuffer.transformInput(){if(length>10000)revertAllChanges()}
    }}
    val latestValue by rememberUpdatedState(value)
    val latestOnValue by rememberUpdatedState(onValue)
    val receiver=LocalPanoKeyboardImage.current
    LaunchedEffect(value) {
        if(state.text.toString()!=value)state.edit {
            val cursor=selection.end.coerceAtMost(value.length)
            replace(0,length,value)
            selection=androidx.compose.ui.text.TextRange(cursor)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            if(text!=latestValue)latestOnValue(text)
        }
    }
    androidx.compose.foundation.text.BasicTextField(state=state,inputTransformation=limit,
        modifier=modifier.contentReceiver { content ->
            if(!content.hasMediaType(MediaType.Image))content else content.consume { item ->
                val uri=item.uri
                if(uri==null || uri.scheme!="content" || receiver==null)false else {
                    @Suppress("DEPRECATION")
                    val permission=content.platformTransferableContent?.extras?.getParcelable<android.view.inputmethod.InputContentInfo>("EXTRA_INPUT_CONTENT_INFO")
                    try {
                        permission?.requestPermission()
                        receiver(uri,permission).also { accepted -> if(!accepted)permission?.releasePermission() }
                    } catch(_:Exception){ permission?.releasePermission();false }
                }
            }
        },
        textStyle=androidx.compose.ui.text.TextStyle(color=color,fontSize=if(title)28.sp else 18.sp,textAlign=align),
        cursorBrush=androidx.compose.ui.graphics.SolidColor(color),
        decorator={inner->Box(Modifier.fillMaxWidth()){
            if(state.text.isEmpty())Text("Write here",color=Color.Gray,textAlign=align,modifier=Modifier.fillMaxWidth())
            inner()
        }})
}

@Composable
private fun BoardLocationBlock(item:PanoItem,controller:PanoController){
    val p=item.content();
    val context=LocalContext.current
    val place=BoardRealContent.location(p)
    var cameraZoom by rememberSaveable(item.id) { mutableFloatStateOf(if(place.approximateCoordinates)15.10f else 15.35f) }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        PanoInfoBox{
            Text("⌖ ${place.placeName}",color=Color.White,fontSize=17.sp)
            if(place.address.isNotBlank())Text(place.address,color=PanoMuted,fontSize=12.sp,maxLines=2)}
        com.cagri.real.ui.components.ReminderLocationMap(place,Modifier.weight(1f).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),cameraZoom=cameraZoom.toDouble(),onOpen={ openReminderLocationInMaps(context,place) })
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            PanoPill("− Map"){cameraZoom=(cameraZoom-1f).coerceAtLeast(1f)}
            PanoPill("+ Map"){cameraZoom=(cameraZoom+1f).coerceAtMost(19f)}
            PanoPill("Open map"){openReminderLocationInMaps(context,place)}
        }
    }
}

@Composable
private fun BoardImageBlock(item:PanoItem,controller:PanoController,designStyle:DesignStyle){
    val p=item.content();
    val id=p.optString("assetId");
    val context=LocalContext.current
    var file by remember(id){mutableStateOf<File?>(runCatching{controller.repository.assets.file(id)}.getOrNull()?.takeIf{it.isFile})}
    var failed by remember(id){mutableStateOf(false)};
    var retry by remember{mutableIntStateOf(0)}
    LaunchedEffect(id,retry){if(file==null)try{file=controller.repository.assets.cached(item.boardId,id);
            failed=false}catch(c:CancellationException){throw c}catch(_:Exception){failed=true}}
    val presentation=LocalBoardRealPresentation.current
    if(file!=null && item.type==BoardBlockType.PHOTO && p.optString("mime")!="image/gif")com.cagri.real.ui.AttachmentMediaSection(
        listOf(com.cagri.real.data.ReminderAttachment(id,file!!.path,mimeType=p.optString("mime"))),
        designStyle,false,onOpen={},onRemove={})
    else if(file!=null)AsyncImage(model=ImageRequest.Builder(context).data(file).size(1024).build(),imageLoader=controller.imageLoader,contentDescription=if(item.type==BoardBlockType.GIF)"GIF" else "Photo",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
    else if(failed)TextButton(onClick={retry++}){Text("Retry image")}
    else Text("Loading image…",color=Color.Gray)
}
