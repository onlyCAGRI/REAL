package com.cagri.real.ui.pano

import com.cagri.real.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.boards.BoardBlockType
import com.cagri.real.data.*

private val SheetInk = Color(0xFF0B0B0D)
private val GlassFill = Color.White.copy(alpha = 0.06f)
private val GlassStroke = Color.White.copy(alpha = 0.10f)
private val MutedText = Color(0xFF9A9AA2)
private val AccentCyan = Color(0xFF6FD6FF)

private class AddEntry(val glyph: String, val label: String, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanoAddSheet(cards:List<ReminderItem>,onDismiss:()->Unit,onAdd:(BoardBlockType,ReminderItem?,ReminderLocation?,Boolean)->Unit,onPhoto:(Boolean)->Unit,onLocation:()->Unit,onContent:(BoardBlockType)->Unit,onStack:()->Unit={}){
    var page by remember {mutableStateOf("menu")};
    var sensitive by remember {mutableStateOf(false)}
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = SheetInk, contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
        }
    ){
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)){
            if(page!="menu") com.cagri.real.ui.components.RealBackButton({ page="menu" })
            Text(if(page=="menu")"Add to Pano" else if(page=="card")"REAL card" else "Card Stack",
                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = if(page=="menu") 8.dp else 0.dp, bottom = 14.dp))
            when(page){
                "menu"->{
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)){
                        SectionLabel("Create")
                        TileGrid(listOf(
                            AddEntry("Aa", "Text") { onAdd(BoardBlockType.TEXT,null,null,false) },
                            AddEntry("✓", t("Checklist")) { onAdd(BoardBlockType.CHECKLIST,null,null,false) },
                            AddEntry("▭", t("REAL card")) { page = "card" },
                            AddEntry("▤", "Card Stack") { onStack() }
                        ))
                        SectionLabel("Media")
                        TileGrid(listOf(
                            AddEntry("◩", "Photo") { onPhoto(false) },
                            AddEntry("GIF", "GIF") { onPhoto(true) },
                            AddEntry("◉", "Voice") { onContent(BoardBlockType.VOICE) },
                            AddEntry("↥", "File / Attachment") { onContent(BoardBlockType.ATTACHMENT) }
                        ))
                        SectionLabel("More")
                        TileGrid(listOf(
                            AddEntry("⌖", "Location") { onLocation() },
                            AddEntry("♪", "Music") { onContent(BoardBlockType.MUSIC) },
                            AddEntry("▶", "Movie / TV") { onContent(BoardBlockType.MEDIA) },
                            AddEntry("☺", t("Mood")) { onContent(BoardBlockType.MOOD) },
                            AddEntry("↗", "Link / snippet") { onContent(BoardBlockType.LINK) }
                        ))
                    }
                }
                "card"->{
                    val shape = RoundedCornerShape(20.dp)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
                            .background(GlassFill).border(1.dp, GlassStroke, shape)
                            .toggleable(value = sensitive, role = Role.Switch, onValueChange = { sensitive = it })
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        Text("Include location, voice and journal", Modifier.weight(1f), color = Color.White, fontSize = 15.sp)
                        Switch(
                            checked = sensitive, onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = AccentCyan.copy(alpha = 0.55f), checkedThumbColor = Color.White,
                                uncheckedTrackColor = GlassFill, uncheckedThumbColor = MutedText, uncheckedBorderColor = GlassStroke
                            )
                        )
                    }
                    Text("An independent snapshot is shared with board members. Your original card stays unchanged.",
                        color = MutedText, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)){
                        items(cards,key={it.id}){card->
                            Column(Modifier.fillMaxWidth().clip(shape).background(GlassFill).border(1.dp, GlassStroke, shape)
                                .clickable{onAdd(BoardBlockType.CARD,card,null,sensitive)}
                                .padding(horizontal = 16.dp, vertical = 14.dp)){
                                Text(card.text, color = Color.White, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if(card.isChecklist)t("{0} checklist items", card.checklistItems.size) else t("REAL card"),
                                    color = MutedText, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                    if(cards.isEmpty())Text("No local cards available.", color = MutedText, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun TileGrid(entries: List<AddEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { AddTile(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AddTile(entry: AddEntry, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.heightIn(min = 60.dp).clip(shape).background(GlassFill).border(1.dp, GlassStroke, shape)
            .clickable(role = Role.Button, onClick = entry.onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Text(entry.glyph, color = Color.White, fontSize = if (entry.glyph.length > 2) 11.sp else 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Text(entry.label, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
