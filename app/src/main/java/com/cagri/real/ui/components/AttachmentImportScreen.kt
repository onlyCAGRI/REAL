package com.cagri.real.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.*
import com.cagri.real.ui.DesignStyle

@Composable
fun AttachmentImportScreen(
    content: IncomingContent?, reminders: List<ReminderItem>, loading: Boolean, busy: Boolean, error: String?,
    style: DesignStyle, reduceBlur: Boolean,
    onImport: (Long, SnippetSource, Boolean) -> Unit, onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var source by rememberSaveable { mutableStateOf(SnippetSource.PLAIN_TEXT) }
    var replaceLink by rememberSaveable(selectedId) { mutableStateOf(false) }
    val selected = reminders.firstOrNull { it.id == selectedId }
    val linkConflict = content?.referenceUrl != null && selected?.referenceUrl != null && content.referenceUrl != selected.referenceUrl
    val filtered = remember(reminders, query) { reminders.filter { it.text.contains(query, ignoreCase = true) } }
    val scene = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    BackHandler { if (!loading && !busy) onDismiss() }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DesignPanel(scene, { position }, Modifier.fillMaxWidth(), designStyle = style, reduceDynamicBlur = reduceBlur, liquidDynamic = true) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Hatırlatıcıya ekle", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(enabled = !loading && !busy, onClick = onDismiss) { Text("İptal", color = Color(0xFFB6B6BF)) }
            }
        }
        if (loading) Text("İçerik hazırlanıyor…", color = Color(0xFFB6B6BF))
        content?.let { payload ->
            val imageCount = payload.attachments.count { it.type == AttachmentType.IMAGE }
            val fileCount = payload.attachments.count { it.type == AttachmentType.FILE }
            val snippets = payload.attachments.filter { it.type == AttachmentType.TEXT_SNIPPET }
            Text(listOfNotNull(if (imageCount > 0) "$imageCount fotoğraf" else null,
                if (fileCount > 0) "$fileCount dosya" else null, if (payload.referenceUrl != null) "1 bağlantı" else null,
                if (snippets.isNotEmpty()) "Metin alıntısı" else null).joinToString(" · "), color = Color(0xFFB6B6BF), fontSize = 13.sp)
            val names = payload.attachments.filter { it.type != AttachmentType.TEXT_SNIPPET }
                .joinToString(" · ") { it.displayName ?: "Fotoğraf" }
            if (names.isNotEmpty()) Text(names, color = Color(0xFFA4A4AF), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            payload.referenceUrl?.let { Text(it, color = Color(0xFFBFC6CD), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (snippets.isNotEmpty()) {
                Text(snippets.first().text.orEmpty(), color = Color(0xFFD3D3DA), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SnippetSource.entries.forEach { type ->
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                            .background(if (source == type) Color(0xFF2A2A2E) else Color(0xFF131316))
                            .border(0.7.dp, if (source == type) Color(0xFF74747D) else Color(0xFF303035), RoundedCornerShape(20.dp))
                            .selectable(source == type, enabled = !busy, role = Role.RadioButton, onClick = { source = type })
                            .padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text(type.label, color = Color(0xFFE3E3E9), fontSize = 12.sp)
                        }
                    }
                }
            }
            payload.warning?.let { Text(it, color = Color(0xFFCEB98B), fontSize = 12.sp) }
        }
        error?.let { Text(it, color = Color(0xFFE4B1B1), fontSize = 13.sp) }
        if (!loading && reminders.isEmpty() && error == null) Text("Önce REAL’da bir hatırlatıcı oluştur, ardından içeriği yeniden paylaş.", color = Color(0xFFB6B6BF))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), enabled = !busy && !loading,
            singleLine = true, placeholder = { Text("Hatırlatıcı ara") }, shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF777780), unfocusedBorderColor = Color(0xFF35353C), cursorColor = Color.White))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().selectableGroup()
            .onGloballyPositioned { position = it.positionInRoot() }
            .drawWithContent {
                if (shouldCaptureBackdrop(style, reduceBlur, android.os.Build.VERSION.SDK_INT)) {
                    scene.record { this@drawWithContent.drawContent() }; drawLayer(scene)
                } else drawContent()
            },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF121215))
                    .border(0.7.dp, if (selectedId == item.id) Color(0xFF80808A) else Color(0xFF303035), RoundedCornerShape(22.dp))
                    .selectable(selectedId == item.id, enabled = !busy, role = Role.RadioButton, onClick = { selectedId = item.id })
                    .padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionIcon(if (selectedId == item.id) ActionGlyph.Check else ActionGlyph.Note, Modifier.size(20.dp), Color(0xFFC7C7D0))
                    Text(item.text, color = Color(0xFFE3E3EA), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (linkConflict) {
            Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { replaceLink = !replaceLink }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(replaceLink, { replaceLink = it }, enabled = !busy,
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFBFC9CE), checkmarkColor = Color.Black))
                Text("Mevcut web bağlantısını değiştir", color = Color(0xFFD8D8E0), fontSize = 13.sp)
            }
        }
        DesignPanel(scene, { position }, Modifier.fillMaxWidth(), designStyle = style, reduceDynamicBlur = reduceBlur, liquidDynamic = true) {
            TextButton(onClick = { selectedId?.let { onImport(it, source, replaceLink) } },
                enabled = selected != null && content != null && !loading && !busy && (!linkConflict || replaceLink),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (busy) "Kaydediliyor…" else "Ekle", color = if (selected != null && !busy) Color(0xFFDEE4E7) else Color(0xFF777780), fontSize = 16.sp)
            }
        }
    }
}
