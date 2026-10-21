package com.cagri.real.ui.pano

import androidx.activity.compose.rememberLauncherForActivityResult
import com.cagri.real.ui.motion.RealEntrancePop
import com.cagri.real.ui.motion.realEntrance
import com.cagri.real.ui.motion.rememberRealEntrance

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.boards.*
import com.cagri.real.data.ReminderItem
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.liquidChrome
import org.json.JSONObject

@Composable
fun ColumnScope.PanoBoardScreen(board:PanoBoard,state:PanoState,controller:PanoController,reminders:List<ReminderItem>,designStyle:DesignStyle,onBack:()->Unit){
    val reduceMotion = com.cagri.real.ui.components.LocalLiquidReduceMotion.current
    val entrance = rememberRealEntrance(reduceMotion, identity = board.id)
    val editable = controller.editingBoard == board.id
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var selected by rememberSaveable(board.id){mutableStateOf<String?>(null)}
    var adding by rememberSaveable(board.id){mutableStateOf(false)};
    var locationGuide by rememberSaveable(board.id){mutableStateOf(false)}
    var locationPosition by rememberSaveable(board.id){mutableStateOf<String?>(null)}
    val context=androidx.compose.ui.platform.LocalContext.current
    var members by rememberSaveable(board.id){mutableStateOf(false)}
    var viewport by remember(board.id){mutableStateOf(state.viewports[board.id]?:BoardViewport())}
    var canvasWidth by remember{mutableFloatStateOf(360f)};
    var canvasHeight by remember{mutableFloatStateOf(600f)}
    val density=LocalDensity.current.density
    val items=state.items[board.id].orEmpty().filterNot{it.deleted}
    fun placement():BoardTransform {val point=boardPoint(androidx.compose.ui.geometry.Offset(canvasWidth/2,canvasHeight/2),viewport);
        return BoardTransform(x=point.x-140f,y=point.y-90f,z=((items.maxOfOrNull{it.transform.z}?:0)+1).coerceAtMost(1000000000))}
    var screenAlive by remember(board.id){mutableStateOf(true)}
    DisposableEffect(board.id){onDispose{screenAlive=false; controller.endEdit()}}
    var pendingPhoto by rememberSaveable(board.id){mutableStateOf<String?>(null)}
    var pendingContent by rememberSaveable(board.id){mutableStateOf<String?>(null)}
    fun request(type:BoardBlockType)=com.cagri.real.content.ContentRequest(
        com.cagri.real.content.ContentDestination.Board(board.id,placement()),
        com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.id?:"guest",type.name,System.currentTimeMillis())
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->
        val pending=pendingPhoto?.let{runCatching{com.cagri.real.content.ContentRequest.from(it)}.getOrNull()}
        pendingPhoto=null
        if(uri!=null&&pending!=null)controller.run {
            selected=controller.addPhoto(pending,uri){screenAlive}
        }
    }
    val people=state.members[board.id].orEmpty().ifEmpty{listOf(PanoMember(board.ownerUid,board.ownerName,"OWNER"))}
    fun finishEditing(){ controller.endEdit(); focus.clearFocus(); selected=null; adding=false; pendingContent=null; pendingPhoto=null; locationGuide=false }
    // The whole screen is canvas. Title, names and back button are gone (system back still works);
    // only a floating pencil + people chip remain, and the dock exists only while editing.
    val receiveKeyboardImage: (android.net.Uri,android.view.inputmethod.InputContentInfo?)->Boolean = { uri,permission ->
        if(!editable || !screenAlive || uri.scheme!="content")false else {
            val destination=request(BoardBlockType.PHOTO)
            controller.addKeyboardImage(destination,uri,permission,{screenAlive && controller.canEdit(board.id)}) { selected=it }
            true
        }
    }
    Box(Modifier.weight(1f).fillMaxWidth()){
        CompositionLocalProvider(LocalPanoKeyboardImage provides receiveKeyboardImage) {
        BoardCanvas(board,items,viewport,selected,controller,designStyle,{selected=it},{viewport=it},Modifier.fillMaxSize().realEntrance(entrance, durationMs=440).onSizeChanged{canvasWidth=it.width/density;
            canvasHeight=it.height/density},editable=editable,look=BoardLook.parse(state.backgrounds[board.id]))
        }
        Row(Modifier.align(Alignment.TopEnd).realEntrance(entrance,120,460,fromScale=0.94f,easing=RealEntrancePop).padding(top=10.dp,end=14.dp),verticalAlignment=Alignment.CenterVertically){
            AnimatedContent(targetState=editable,transitionSpec={(fadeIn(tween(180))+scaleIn(initialScale=0.85f,animationSpec=tween(180))) togetherWith (fadeOut(tween(110))+scaleOut(targetScale=0.9f,animationSpec=tween(110)))},label="panoTopControls"){isEditing->
                if(isEditing) PanoGlassButton(ActionGlyph.Check,"Done",PanoAccent){finishEditing()}
                else Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                    PanoGlassButton(ActionGlyph.Edit,"Edit"){controller.beginEdit(board.id)}
                    PanoPeopleChip(people,board.role=="OWNER"){members=true}
                }
            }
        }
        Row(Modifier.align(Alignment.TopStart).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(state.syncing)com.cagri.real.ui.components.RealSpinner(Modifier.size(16.dp),color=Color.Gray,strokeWidth=1.dp)
            if(state.queue.isNotEmpty())Text("Saved locally · ${state.queue.size} pending",color=Color.Gray,fontSize=10.sp)
        }
        PanoEditDock(editable,Modifier.align(Alignment.BottomCenter).padding(bottom=14.dp),viewport,
            onAdd={adding=true},
            onZoom={viewport=it;controller.viewport(board.id,it)})
    }
    if(adding && editable)PanoAddSheet(reminders,{adding=false},onAdd={type,card,location,privateContent->adding=false;
        controller.add(board.id,type,placement(),{selected=it},card=card,location=location,includePrivate=privateContent)},onPhoto={gif->
        pendingPhoto=request(if(gif)BoardBlockType.GIF else BoardBlockType.PHOTO).json().toString()
        adding=false
        photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(
            if(gif)ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif") else ActivityResultContracts.PickVisualMedia.ImageOnly))
    },onLocation={
        locationPosition=placement().json().toString();adding=false;locationGuide=true
    },onContent={type->pendingContent=request(type).json().toString();adding=false},onStack={
        adding=false;controller.add(board.id,BoardBlockType.STACK,placement(),{selected=it})
    })
    pendingContent?.let { raw ->
        val target=remember(raw){com.cagri.real.content.ContentRequest.from(raw)}
        PanoContentFlow(target,designStyle,onDismiss={pendingContent=null}){type,payload,stillActive->
            check(pendingContent==raw){"Content destination was cancelled"}
            selected=controller.addCanonical(target,type,payload){stillActive()&&pendingContent==raw}
        }
    }
    com.cagri.real.ui.components.LocationGuideSheet(visible=locationGuide,onOpenMaps={
        locationGuide=false
        val transform=locationPosition?.let{BoardTransform.from(JSONObject(it))}?:placement()
        com.cagri.real.platform.android.location.RealLocationShareFlow.startBoard(context,board.id,transform)
    },onCancel={locationGuide=false;locationPosition=null},reduceAnimations=true)
    if(members)PanoMembersSheet(board,people,controller,{members=false},onBack,
        look=BoardLook.parse(state.backgrounds[board.id]),onLook={controller.background(board.id,it.encode())})
}

@Composable
private fun PanoBarButton(label:String,description:String,onClick:()->Unit){
    Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha=0.06f))
        .semantics{contentDescription=description}.clickable(role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center){
        Text(label,color=Color.White,fontSize=18.sp)}
}

@Composable
private fun PanoBarDivider(){
    Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha=0.12f)))
}

@Composable
private fun PanoPeopleChip(people:List<PanoMember>,canInvite:Boolean,onClick:()->Unit){
    Row(Modifier.height(40.dp).liquidChrome(corners=20.dp).semantics{contentDescription="People"}
        .clickable(role=Role.Button,onClick=onClick).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
        Row(horizontalArrangement=Arrangement.spacedBy((-8).dp),verticalAlignment=Alignment.CenterVertically){
            people.take(3).forEach{person->
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF0C0C0E)),contentAlignment=Alignment.Center){PanoAvatar(person.name,25.dp)}}
        }
        if(people.size>3)Text("+${people.size-3}",color=Color.White.copy(alpha=0.8f),fontSize=12.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(start=6.dp))
        if(canInvite)ActionIcon(ActionGlyph.Add,Modifier.padding(start=6.dp,end=2.dp).size(13.dp),color=Color.White.copy(alpha=0.8f))
    }
}

/** Not a scope extension on purpose: calling AnimatedVisibility here avoids the Column/Box receiver clash. */
@Composable
private fun PanoEditDock(visible:Boolean,modifier:Modifier,viewport:BoardViewport,onAdd:()->Unit,onZoom:(BoardViewport)->Unit){
    AnimatedVisibility(visible=visible,modifier=modifier,
        enter=slideInVertically(tween(220)){it}+fadeIn(tween(180)),exit=slideOutVertically(tween(160)){it}+fadeOut(tween(120))){
        Row(Modifier.liquidChrome(corners=24.dp).padding(horizontal=6.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
            Row(Modifier.height(34.dp).clip(CircleShape)
                .background(Color.White.copy(alpha=0.16f))
                .border(1.dp,Color.White.copy(alpha=0.22f),CircleShape)
                .clickable(role=Role.Button){onAdd()}.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){
                Text("+",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.width(5.dp))
                Text("Add",color=Color.White,fontSize=14.sp,fontWeight=FontWeight.SemiBold)}
            Spacer(Modifier.width(6.dp));PanoBarDivider();Spacer(Modifier.width(2.dp))
            PanoBarButton("−","Zoom out"){onZoom(viewport.copy(zoom=(viewport.zoom/1.2f).coerceAtLeast(BOARD_MIN_ZOOM)))}
            Text("${(viewport.zoom*100).toInt()}%",color=Color(0xFFB4B4BC),fontSize=12.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center,maxLines=1,modifier=Modifier.widthIn(min=40.dp))
            PanoBarButton("+","Zoom in"){onZoom(viewport.copy(zoom=(viewport.zoom*1.2f).coerceAtMost(BOARD_MAX_ZOOM)))}
            Spacer(Modifier.width(2.dp));PanoBarDivider()
            Box(Modifier.heightIn(min=34.dp).clip(CircleShape).alpha(if(viewport==BoardViewport())0.45f else 1f)
                .clickable(role=Role.Button){onZoom(BoardViewport())}
                .padding(horizontal=10.dp),contentAlignment=Alignment.Center){
                Text("Reset",color=Color(0xFFB4B4BC),fontSize=12.sp,maxLines=1)}
        }
    }
}
