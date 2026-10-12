package com.cagri.real.ui.pano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.cagri.real.boards.*
import com.cagri.real.platform.android.boards.PanoPush
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon

/** People, background and board actions. [look]/[onLook] are optional so older callers keep compiling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanoMembersSheet(board:PanoBoard,members:List<PanoMember>,controller:PanoController,onDismiss:()->Unit,onDeleted:()->Unit,
    look:BoardLook=BoardLook(),onLook:((BoardLook)->Unit)?=null){
    var confirmLeave by remember {mutableStateOf(false)}
    var confirmDelete by remember {mutableStateOf(false)}
    val notifying by controller.repository.notifyBoards.collectAsState()
    val notifyOn=board.id in notifying
    val appContext=LocalContext.current.applicationContext
    fun setNotify(enabled:Boolean)=controller.run{
        controller.repository.setNotify(board.id,enabled)
        if(enabled){PanoPush.ensureChannel(appContext);PanoPush.registerToken(appContext)}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)setNotify(true)}
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=Color(0xFF0C0C0E),contentColor=Color.White,
        shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp)){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=28.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("People",fontSize=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=2.dp))
            Text("${members.size} ${if(members.size==1)"person" else "people"}",color=PanoMuted,fontSize=13.sp,modifier=Modifier.padding(bottom=6.dp))

            // Grouped list, one rounded surface with hairline separators (iOS inset-grouped style).
            Column(Modifier.fillMaxWidth().panoSurface(22.dp)){
                members.forEachIndexed{index,member->
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                        PanoAvatar(member.name,40.dp)
                        Column(Modifier.weight(1f).padding(start=12.dp)){
                            Text(member.name,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Medium,maxLines=1)
                            Text(member.role.lowercase().replaceFirstChar{it.uppercase()},color=PanoMuted,fontSize=12.sp)
                        }
                        if(board.role=="OWNER"&&member.uid!=board.ownerUid)
                            Box(Modifier.panoSurface(14.dp).clickable{controller.run {controller.repository.removeMember(board.id,member.uid)}}.padding(horizontal=12.dp,vertical=7.dp)){
                                Text("Remove",color=PanoDanger,fontSize=12.sp,fontWeight=FontWeight.Medium)}
                    }
                    if(index<members.lastIndex)Box(Modifier.fillMaxWidth().padding(start=66.dp).height(0.5.dp).background(Color.White.copy(alpha=0.10f)))
                }
            }

            if(board.role=="OWNER"){
                SheetLabel("Invite")
                Column(Modifier.fillMaxWidth().panoSurface(22.dp).padding(14.dp)){
                    com.cagri.real.ui.connections.ConnectionPeoplePicker(actionLabel="Pano'ya davet et",onSelect={person->
                        controller.repository.inviteConnection(board.id,person.uid)
                    })
                }
            }

            // Only boards other people can write to: a private board has nothing to be notified about.
            if(board.shared||members.size>1){
                SheetLabel("Notifications")
                Row(Modifier.fillMaxWidth().panoSurface(22.dp).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                    ActionIcon(ActionGlyph.Bell,Modifier.size(20.dp),color=if(notifyOn)PanoAccent else Color.White)
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)){
                        Text("New content",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Medium)
                        Text("Get notified when someone adds to this board",color=PanoMuted,fontSize=12.sp)
                    }
                    Switch(checked=notifyOn,onCheckedChange={on->
                        if(on&&android.os.Build.VERSION.SDK_INT>=33&&
                            androidx.core.content.ContextCompat.checkSelfPermission(appContext,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        else setNotify(on)})
                }
            }

            if(onLook!=null){
                SheetLabel("Background")
                Column(Modifier.fillMaxWidth().panoSurface(22.dp).padding(12.dp)){BoardLookSelector(look,onLook)}
            }

            if(board.role=="OWNER")DangerRow("Delete board"){confirmDelete=true}
            if(board.role=="MEMBER")DangerRow("Leave board"){confirmLeave=true}
        }
    }
    if(confirmLeave)PanoLeaveDialog(board,controller,{confirmLeave=false},{confirmLeave=false;onDismiss();onDeleted()})
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},containerColor=Color(0xFF161618),title={Text("Delete board?",color=Color.White)},text={Text("This removes cloud access for all members. Original REAL cards are kept.",color=Color.LightGray)},confirmButton={TextButton(onClick={controller.run{controller.repository.deleteBoard(board.id);
                onDismiss();
                onDeleted()}}){Text("Delete")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("Cancel")}})
}

@Composable
private fun SheetLabel(text:String){
    Text(text.uppercase(),color=PanoMuted,fontSize=11.sp,fontWeight=FontWeight.SemiBold,letterSpacing=0.8.sp,
        modifier=Modifier.padding(start=8.dp,top=10.dp))
}

@Composable
private fun DangerRow(label:String,onClick:()->Unit){
    Box(Modifier.padding(top=10.dp).fillMaxWidth().panoSurface(22.dp).clickable(onClick=onClick).padding(vertical=15.dp),contentAlignment=Alignment.Center){
        Text(label,color=PanoDanger,fontSize=16.sp,fontWeight=FontWeight.Medium)}
}
