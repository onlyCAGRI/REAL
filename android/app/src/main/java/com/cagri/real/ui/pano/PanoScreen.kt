package com.cagri.real.ui.pano

import com.cagri.real.i18n.t
import androidx.activity.compose.BackHandler
import com.cagri.real.ui.motion.RealEntrancePop
import com.cagri.real.ui.motion.realEntrance
import com.cagri.real.ui.motion.rememberRealEntrance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cagri.real.boards.*
import com.cagri.real.data.ReminderItem
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.platform.android.boards.*
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.liquidChrome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanoScreen(reminders:List<ReminderItem>,designStyle:DesignStyle,onDismiss:()->Unit, categories:List<com.cagri.real.data.CustomCategory> = emptyList(), requestedBoard:String? = null,onRequestHandled:()->Unit = {}) {
    var user by remember { mutableStateOf(AndroidBackend.auth.currentUser()) }
    DisposableEffect(Unit){val subscription=AndroidBackend.auth.observeUser { user=it };
        onDispose{subscription.cancel()}}
    key(user?.id?:"guest") {
        val context=LocalContext.current.applicationContext;
        val uid=user?.id?:"guest"
        val repository=remember(uid){BoardRepositories.obtain(context,uid,user?.displayName?:"REAL user")}
        val scope=rememberCoroutineScope();
        val controller=remember(repository,scope){PanoController(repository,scope,context)}
        DisposableEffect(controller){onDispose{controller.imageLoader.shutdown()}}
        val state by repository.state.collectAsState();
        val message by controller.message.collectAsState()
        var selectedBoard by rememberSaveable(uid){mutableStateOf<String?>(null)}
        LaunchedEffect(repository,requestedBoard,state.loaded) {
            if (state.loaded && requestedBoard != null) {
                selectedBoard = requestedBoard
                onRequestHandled()
            }
        }
        DisposableEffect(selectedBoard){PanoPush.openBoard=selectedBoard;onDispose{PanoPush.openBoard=null}}
        var stackEditing by remember { mutableStateOf(false) }
        var previewCard by remember { mutableStateOf<PanoItem?>(null) }
        LaunchedEffect(previewCard?.id,state.items) {
            val shown=previewCard
            if(shown!=null && state.items[shown.boardId].orEmpty().any {it.id==shown.id && it.deleted})previewCard=null
        }
        var previewPhoto by rememberSaveable(uid){mutableStateOf<String?>(null)}
        val lifecycle=LocalLifecycleOwner.current
        LaunchedEffect(repository){controller.run { repository.load() }}
        LaunchedEffect(repository,selectedBoard,lifecycle){
            repository.state.first { it.loaded }
            lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){repository.sync(selectedBoard);
                    delay(15000)}}
        }
        BackHandler {if(previewPhoto!=null)previewPhoto=null else if(previewCard!=null)previewCard=null else if(selectedBoard!=null)selectedBoard=null else onDismiss()}
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit){awaitPointerEventScope{while(true)awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)}}.statusBarsPadding().navigationBarsPadding().imePadding()) {
            if(!state.loaded){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=androidx.compose.ui.Alignment.Center){com.cagri.real.ui.components.RealSpinner(color=Color.White)}}
            else {
                val board=state.boards.firstOrNull { it.id==selectedBoard }
                if(board==null)PanoGallery(state,controller,onDismiss,selfUid=uid,onOpen={selectedBoard=it})
                else CompositionLocalProvider(LocalBoardRealPresentation provides BoardRealPresentation(reminders,categories,uid,onPreviewPhoto={previewPhoto=it},onPreviewCard={stackEditing=false;previewCard=it},onEditStack={stackEditing=true;previewCard=it})) {
                    PanoBoardScreen(board,state,controller,reminders,designStyle,onBack={selectedBoard=null})
                }
            }
            val info=message?:state.error
            if(info!=null)Text(info,color=Color(0xFFB1B1BA),fontSize=12.sp,modifier=Modifier.fillMaxWidth().padding(10.dp))
        }
        previewCard?.let { requested ->
            val card=state.items[requested.boardId].orEmpty().firstOrNull { it.id==requested.id && !it.deleted } ?: requested
                ModalBottomSheet(onDismissRequest={previewCard=null},containerColor=Color.Black) {
                    CompositionLocalProvider(LocalBoardRealPresentation provides BoardRealPresentation(reminders,categories,uid,onPreviewPhoto={previewPhoto=it})) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(16.dp)) {
                            when(card.type) {
                                BoardBlockType.STACK -> BoardStackBlock(card,controller,designStyle,inEditor=stackEditing,detail=true)
                                BoardBlockType.TEXT -> androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(card.content().optString("text"),color=Color.White,modifier=Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                                }
                                else -> BoardCardBlock(card,controller,designStyle,editable=false,detail=true)
                            }
                        }
                    }
                }
        }
        previewPhoto?.let { path -> com.cagri.real.ui.ReminderPhotoPreview(path,{previewPhoto=null}) }
        }
        val focus=androidx.compose.ui.platform.LocalFocusManager.current
        LaunchedEffect(state.conflict?.mutationId){if(state.conflict!=null)focus.clearFocus()}
        state.conflict?.let {
            com.cagri.real.ui.components.RealAlertDialog(onDismissRequest={},containerColor=Color(0xFF171719),title={Text("Block changed",color=Color.White)},text={Text("Another device edited this block. Keep your queued edit or use the server version. Other blocks are unaffected.",color=Color.LightGray)},
                confirmButton={TextButton(onClick={controller.run {repository.resolveConflict(true);
                        repository.sync(selectedBoard)}}){Text("Keep mine")}},
                dismissButton={TextButton(onClick={controller.run {repository.resolveConflict(false);
                        repository.sync(selectedBoard)}}){Text("Use server")}})
        }
    }
}

@Composable
private fun ColumnScope.PanoGallery(state:PanoState,controller:PanoController,onBack:()->Unit,selfUid:String,onOpen:(String)->Unit) {
    val entrance = rememberRealEntrance(com.cagri.real.ui.components.LocalLiquidReduceMotion.current, totalMs=1100)
    var leaving by remember {mutableStateOf<PanoBoard?>(null)}
    var creating by remember {mutableStateOf(false)}
    var peek by remember {mutableStateOf<PanoBoard?>(null)}
    var peopleOf by remember {mutableStateOf<PanoBoard?>(null)}
    var deleting by remember {mutableStateOf<PanoBoard?>(null)}
    val mine=state.boards.filter { it.role=="OWNER" }
    val others=state.boards.filter { it.role!="OWNER" }
    val colors=panoCardColors((mine+others).map{it.id})
    fun membersOf(b:PanoBoard)=state.members[b.id].orEmpty()
    Box(Modifier.fillMaxWidth().realEntrance(entrance, durationMs=360)) { PanoGalleryHeader(onBack){creating=true} }
    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=18.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(state.boards.isEmpty())item{Column(Modifier.realEntrance(entrance,80,520,slideY=16.dp).padding(vertical=40.dp,horizontal=6.dp)){Text("A PLACE FOR\nYOUR IDEAS",color=Color.White,fontSize=34.sp,lineHeight=32.sp);
                Text("Arrange cards, notes and photos. Build alone or invite people.",color=Color.Gray,modifier=Modifier.padding(top=12.dp));
                Box(Modifier.padding(top=18.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(PanoIvory).clickable{creating=true}.padding(horizontal=22.dp,vertical=12.dp)){Text("+ NEW BOARD",color=Color.Black)}}}
        if(state.invites.isNotEmpty())item{Box(Modifier.realEntrance(entrance,80,360)){PanoSectionLabel("Invitations")}}
        items(state.invites,key={"invite-"+it.id}){invite->Row(Modifier.realEntrance(entrance,160,500,slideY=12.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp)).background(Color(0xFF1C1C1E)).padding(start=20.dp,end=12.dp,top=14.dp,bottom=14.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(invite.title.uppercase(),color=Color.White,fontSize=20.sp);
                    Text("From ${invite.ownerName}",color=Color.Gray,fontSize=12.sp)};
                Box(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(PanoSage).clickable{controller.run {controller.repository.accept(invite.id)}}.padding(horizontal=18.dp,vertical=9.dp)){Text("ACCEPT",color=Color.Black,fontSize=13.sp)}}}
        if(mine.isNotEmpty())item{Box(Modifier.realEntrance(entrance,120,360)){PanoSectionLabel("My boards")}}
        itemsIndexed(mine,key={_,board->board.id}){index,board->
            Box(Modifier.fillMaxWidth().realEntrance(entrance,200+minOf(index,5)*55,500,slideY=14.dp,fromScale=0.96f,easing=RealEntrancePop)) {
                PanoBoardCard(board,boardPeople(board,membersOf(board),selfUid),colors[board.id]?:PanoCardPalette[0],{onOpen(board.id)},{peek=board})
            }
        }
        if(others.isNotEmpty())item{Box(Modifier.realEntrance(entrance,180,360)){PanoSectionLabel("Shared with me")}}
        itemsIndexed(others,key={_,board->board.id}){index,board->
            Box(Modifier.fillMaxWidth().realEntrance(entrance,280+minOf(index,5)*55,500,slideY=14.dp,fromScale=0.96f,easing=RealEntrancePop)) {
                PanoBoardCard(board,boardPeople(board,membersOf(board),selfUid),colors[board.id]?:PanoCardPalette[0],{onOpen(board.id)},{peek=board})
            }
        }
    }
    if(state.queue.isNotEmpty())Text("${state.queue.size} local changes waiting to sync",color=Color.Gray,fontSize=11.sp,modifier=Modifier.padding(16.dp))
    leaving?.let { PanoLeaveDialog(it,controller,{leaving=null},{leaving=null}) }
    peek?.let { b->
        PanoBoardPeek(b,colors[b.id]?:PanoCardPalette[0],boardPeople(b,membersOf(b),selfUid).size,
            onOpen={peek=null;onOpen(b.id)},onPeople={peek=null;peopleOf=b},
            onRemove={peek=null;if(b.role=="OWNER")deleting=b else leaving=b},onDismiss={peek=null})
    }
    peopleOf?.let { b->
        PanoMembersSheet(b,membersOf(b).ifEmpty{listOf(PanoMember(b.ownerUid,b.ownerName,"OWNER"))},controller,onDismiss={peopleOf=null},onDeleted={peopleOf=null})
    }
    deleting?.let { b->
        com.cagri.real.ui.components.RealAlertDialog(onDismissRequest={deleting=null},containerColor=Color(0xFF161618),title={Text("Delete board?",color=Color.White)},text={Text("This removes cloud access for all members. Original REAL cards are kept.",color=Color.LightGray)},
            confirmButton={TextButton(onClick={deleting=null;controller.run{controller.repository.deleteBoard(b.id)}}){Text("Delete")}},
            dismissButton={TextButton(onClick={deleting=null}){Text("Cancel")}})
    }
    if(creating)PanoNewBoardOverlay(onDismiss={creating=false},onCreate={title,shared->
        creating=false
        controller.run {onOpen(controller.repository.create(title,shared));
            controller.repository.sync(null)}
    })
}
