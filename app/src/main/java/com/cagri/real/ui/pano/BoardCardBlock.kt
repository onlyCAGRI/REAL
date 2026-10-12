package com.cagri.real.ui.pano

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.boards.PanoItem
import com.cagri.real.data.*
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.*
import kotlinx.coroutines.CancellationException
import org.json.*

/** Existing REAL model/codec and content renderers; board copy never edits the source reminder. */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun BoardCardBlock(item:PanoItem,controller:PanoController,designStyle:DesignStyle,editable:Boolean=false,detail:Boolean=false){
    val payload=item.content();
    val card=payload.optJSONObject("card")?:return;
    val stackView = com.cagri.real.data.boards.BoardRealContent.isStackView(payload)
    val expanded=detail || payload.optBoolean("expanded")
    var hydrated by remember(item.id,item.payload){mutableStateOf(card.toString())}
    var assetError by remember(item.id){mutableStateOf<String?>(null)}
    LaunchedEffect(item.id,item.payload,expanded,stackView){
        if(expanded || stackView){val copy=JSONObject(card.toString())
            suspend fun hydrate(o:JSONObject,pathKey:String="localPath"){val id=o.optString("blobId");
                if(id.isNotBlank())try{o.put(pathKey,controller.repository.assets.cached(item.boardId,id).path)}catch(c:CancellationException){throw c}catch(_:Exception){assetError="Some files are unavailable offline."}}
            for(key in listOf("attachments","voiceRecordings")){val a=copy.optJSONArray(key)?:continue;
                for(i in 0 until a.length())hydrate(a.getJSONObject(i))}
            val stack=copy.optJSONObject("cardStack")?.optJSONArray("cards");
            if(stack!=null)for(i in 0 until stack.length())hydrate(stack.getJSONObject(i),"imageUri")
            hydrated=copy.toString()
        }
    }
    val snapshot=remember(hydrated){runCatching{ReminderJson.decode(JSONArray().put(JSONObject(hydrated)).toString()).first()}.getOrNull()}?:return
    val presentation=LocalBoardRealPresentation.current
    val source=com.cagri.real.data.boards.BoardRealContent.referencedCard(payload,item.authorUid,presentation.uid,presentation.reminders)
    val reminder=source?:snapshot
    val category=presentation.categories.firstOrNull{it.id==source?.customCategoryId}
    val displayReminder=if(category!=null)reminder.copy(customCategoryId=category.id)else reminder
    val context=LocalContext.current
    val backdrop=androidx.compose.ui.graphics.rememberGraphicsLayer()
    val focus=remember(item.id){androidx.compose.ui.focus.FocusRequester()}
    if(stackView && !detail) {
        reminder.cardStack?.let { CardStackContentSection(it,designStyle,false,onUpdate={},onRemove={}) }
        return
    }
    // Only card stacks grow on tap; every other card stays in its box.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        ReminderCard(displayReminder,onToggle={if(!editable && !detail && stackView)presentation.onPreviewCard(item)},onOpen={if(!editable && !detail && stackView)presentation.onPreviewCard(item)},reduceAnimations=true,customCategory=category,modifier=Modifier.fillMaxWidth())
        if(expanded){
            if(reminder.text.isNotBlank())PanoInfoBox{ClickableNoteText(reminder.text,color=Color.White,fontSize=16.sp,lineHeight=23.sp,onNonLinkClick={})}
            if(reminder.checklistItems.isNotEmpty())com.cagri.real.ui.DetailChecklistBox(
                designStyle,false,reminder.checklistItems,"",{}, {}, {_,_->}, {}, onToggleItem={id->
                    if(!editable || source!=null)return@DetailChecklistBox
                    val p=JSONObject(payload.toString())
                    val list=JSONArray()
                    reminder.checklistItems.forEach{entry->list.put(JSONObject().put("id",entry.id).put("done",if(entry.id==id)!entry.isCompleted else entry.isCompleted))}
                    p.put("checklistItems",list)
                    controller.content(item,p)
                },onRemoveChecklist={},onFocusField={},firstItemFocusRequester=focus,reduceAnimations=true,savingDetail=false)
            reminder.location?.let{place->PanoInfoBox{
                Text("⌖ ${place.placeName}",color=Color.White,fontSize=16.sp,modifier=Modifier.clickable {openReminderLocationInMaps(context,place)})
                if(place.latitude!=null&&place.longitude!=null)ReminderLocationMap(place,Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))){openReminderLocationInMaps(context,place)}}}
            MusicContentSection(reminder.musicBlocks,designStyle,false,{})
            MediaContentSection(reminder.mediaBlocks,designStyle,false,{})
            reminder.cardStack?.let{CardStackContentSection(it,designStyle,false,onUpdate={},onRemove={})}
            com.cagri.real.ui.AttachmentMediaSection(reminder.attachments.filter{it.type==AttachmentType.IMAGE},designStyle,false,
                onOpen={presentation.onPreviewPhoto(it.localPath)},onRemove={})
            ReminderContentSections(reminder.attachments,reminder.referenceUrl,false,designStyle,backdrop,
                backdropPosition={androidx.compose.ui.geometry.Offset.Zero},onRemove={},onRemoveLink={},
                onOpenAttachment={if(source!=null)AttachmentStorage.open(context,it) else controller.repository.assets.open(it)},allowSnippetEditing=false)
            reminder.voiceRecordings.forEach{VoicePlaybackRow(it,compact=true)}
            reminder.journal?.let{PanoInfoBox{ClickableNoteText(it.text,color=Color.White.copy(alpha=0.85f),fontSize=15.sp,lineHeight=22.sp,onNonLinkClick={})}}
            reminder.mood?.let{PanoInfoBox{Text(it.label,color=Color.White,fontSize=15.sp)}}
            assetError?.let{PanoInfoBox{Text(it,color=PanoMuted,fontSize=13.sp)}}
            Spacer(Modifier.height(28.dp))
        }
    }
}
