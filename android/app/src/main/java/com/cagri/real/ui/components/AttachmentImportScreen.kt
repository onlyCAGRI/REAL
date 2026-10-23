package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cagri.real.AttachmentImportModel
import com.cagri.real.ShareDestination
import com.cagri.real.data.*
import java.io.File
import java.time.LocalDate

private val Ink = Color(0xFFF2F2F5)
private val Muted = Color(0xFF9C9CA6)
private val Field = Color.White.copy(alpha = 0.06f)
private val Stroke = Color.White.copy(alpha = 0.10f)

private enum class ShareTab(val label: String) { New(t("Yeni kart")), Existing(t("Karta ekle")), Board(t("Pano")) }

/** Share → REAL sheet (AttachmentImportActivity). */
@Composable
internal fun AttachmentImportScreen(model: AttachmentImportModel, reduceMotion: Boolean, onOpen: (Intent) -> Unit) {
    val content = model.content
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    BackHandler { if (!model.busy) { if (model.done != null) model.next() else model.discard() } }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, null) {
                if (!model.busy) { if (model.done != null) model.next() else model.discard() }
            })
        AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter),
            enter = if (reduceMotion) fadeIn(tween(1)) else slideInVertically(tween(320)) { it } + fadeIn(tween(200))) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp)
                    .realSheetSurface()
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .navigationBarsPadding().imePadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RealSheetHandle()
                val done = model.done
                if (done != null) DoneContent(done.first, onOpen = { onOpen(done.second) }, onClose = model::next)
                else key(model.generation) { ShareContent(model, content) }
            }
        }
    }
}

@Composable
private fun DoneContent(text: String, onOpen: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            ActionIcon(ActionGlyph.Check, Modifier.size(24.dp), Ink)
        }
        Spacer(Modifier.height(12.dp))
        Text(text, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetButton(t("Aç"), filled = true, modifier = Modifier.weight(1f), onClick = onOpen)
            SheetButton(t("Tamam"), filled = false, modifier = Modifier.weight(1f), onClick = onClose)
        }
    }
}

@Composable
private fun ColumnScope.ShareContent(model: AttachmentImportModel, content: IncomingContent?) {
    var tab by rememberSaveable { mutableStateOf(ShareTab.New) }
    var title by rememberSaveable { mutableStateOf("") }
    var titleEdited by rememberSaveable { mutableStateOf(false) }
    var categoryId by rememberSaveable { mutableStateOf(model.presetCategoryId) }
    var plannedOffset by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedCard by rememberSaveable { mutableStateOf<Long?>(null) }
    var replaceLink by rememberSaveable { mutableStateOf(false) }
    var selectedBoard by rememberSaveable { mutableStateOf<String?>(null) }
    var source by rememberSaveable { mutableStateOf(model.detectedSource) }
    // The suggested title fills the field until the person types (it may arrive later, with the link preview).
    LaunchedEffect(content?.suggestedTitle) { if (!titleEdited) title = content?.suggestedTitle.orEmpty() }
    LaunchedEffect(model.presetCategoryId) { if (model.presetCategoryId != null) categoryId = model.presetCategoryId }

    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t("REAL'e ekle"), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(t("İptal"), color = Muted, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !model.loading && !model.busy) { model.discard() }.padding(horizontal = 10.dp, vertical = 6.dp))
    }
    if (model.loading) {
        Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Muted, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(t("İçerik hazırlanıyor…"), color = Muted, fontSize = 14.sp)
        }
    }
    content?.let { Preview(it, model.resolving) }
    model.error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
    if (content == null) { Spacer(Modifier.height(16.dp)); return }

    // Destination tabs.
    Row(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(20.dp)).background(Field).padding(4.dp)) {
        ShareTab.entries.forEach { t ->
            val on = tab == t
            Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) Color.White else Color.Transparent)
                .clickable(enabled = !model.busy) { tab = t }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(t.label, color = if (on) Color.Black else Ink.copy(alpha = 0.8f), fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
    Box(Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 12.dp)) {
        when (tab) {
            ShareTab.New -> NewCardTab(model.categories, title, { title = it; titleEdited = true }, categoryId, { categoryId = it },
                plannedOffset, { plannedOffset = it }, model.busy)
            ShareTab.Existing -> ExistingCardTab(model.reminders, selectedCard, { selectedCard = it }, model.busy)
            ShareTab.Board -> BoardTab(model.boards, selectedBoard, { selectedBoard = it }, model.busy)
        }
    }
    val selected = model.reminders.firstOrNull { it.id == selectedCard }
    val linkConflict = tab == ShareTab.Existing && content.referenceUrl != null && selected?.referenceUrl != null && content.referenceUrl != selected.referenceUrl
    if (linkConflict) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = !model.busy) { replaceLink = !replaceLink }
            .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(if (replaceLink) Color.White else Color.Transparent)
                .border(1.2.dp, if (replaceLink) Color.White else Muted, CircleShape), contentAlignment = Alignment.Center) {
                if (replaceLink) ActionIcon(ActionGlyph.Check, Modifier.size(12.dp), Color.Black)
            }
            Spacer(Modifier.width(10.dp))
            Text(t("Kartın mevcut bağlantısını değiştir"), color = Ink, fontSize = 14.sp)
        }
    }
    if (tab != ShareTab.Board && content.attachments.any { it.type == AttachmentType.TEXT_SNIPPET }) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Metin"), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
            SnippetSource.entries.forEach { s ->
                Chip(s.label, s == source, enabled = !model.busy) { source = s }
            }
        }
    }
    val canSave = !model.busy && when (tab) {
        ShareTab.New -> true
        ShareTab.Existing -> selected != null && (!linkConflict || replaceLink)
        ShareTab.Board -> selectedBoard != null && model.boards.any { it.id == selectedBoard }
    }
    SheetButton(
        when {
            model.busy -> t("Kaydediliyor…")
            tab == ShareTab.New -> t("Kart oluştur")
            tab == ShareTab.Existing -> t("Karta ekle")
            else -> t("Panoya ekle")
        }, filled = true, enabled = canSave, modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp)
    ) {
        val destination = when (tab) {
            ShareTab.New -> ShareDestination.NewCard(title, categoryId,
                plannedOffset?.let { LocalDate.now().plusDays(it.toLong()).toEpochDay() })
            ShareTab.Existing -> ShareDestination.ExistingCard(selectedCard ?: return@SheetButton, replaceLink)
            ShareTab.Board -> ShareDestination.Board(selectedBoard ?: return@SheetButton)
        }
        model.save(destination, source)
    }
}

/** What is being shared: photos, link preview, music, text, files, voice. */
@Composable
private fun Preview(content: IncomingContent, resolving: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val photos = content.attachments.filter { it.type == AttachmentType.IMAGE }
        if (photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photos, key = { it.id }) { photo ->
                AsyncImage(File(photo.localPath), null, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(Field),
                    contentScale = ContentScale.Crop)
            }
        }
        content.musicBlocks.forEach { music ->
            PreviewRow(music.artworkUrl, music.title, listOf(music.artist, music.provider.let { if (it == "iTunes") "Apple Music" else it })
                .filter { it.isNotBlank() }.joinToString(" · "))
        }
        content.referenceUrl?.let { url ->
            val host = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
            PreviewRow(content.previewImageUrl, content.previewTitle?.takeIf { it.isNotBlank() }
                ?: if (resolving) t("Önizleme yükleniyor…") else host, host)
        }
        content.attachments.firstOrNull { it.type == AttachmentType.TEXT_SNIPPET }?.text?.let {
            Text(it, color = Ink.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Field).padding(12.dp))
        }
        val files = content.attachments.filter { it.type == AttachmentType.FILE }
        val voices = content.voiceRecordings
        val extra = listOfNotNull(
            if (files.isNotEmpty()) files.joinToString(" · ") { it.displayName ?: t("Dosya") } else null,
            if (voices.isNotEmpty()) t("{0} ses kaydı · ", voices.size) + voices.joinToString(" · ") { duration(it.durationMillis) } else null,
            if (photos.size > 3) t("Kartta 3 fotoğraf görünür; kalanlar dosya olarak eklenir.") else null
        )
        extra.forEach { Text(it, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth()) }
        content.warning?.let { Text(it, color = Color(0xFFCEB98B), fontSize = 12.sp, modifier = Modifier.fillMaxWidth()) }
    }
}

private fun duration(ms: Long): String { val s = (ms / 1000).coerceAtLeast(1); return "%d:%02d".format(s / 60, s % 60) }

@Composable
private fun PreviewRow(image: String?, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Field).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (image != null) AsyncImage(image, null, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        else Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Field), contentAlignment = Alignment.Center) {
            ActionIcon(ActionGlyph.Share, Modifier.size(20.dp), Muted)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NewCardTab(
    categories: List<CustomCategory>, title: String, onTitle: (String) -> Unit, categoryId: Long?, onCategory: (Long?) -> Unit,
    plannedOffset: Int?, onPlanned: (Int?) -> Unit, busy: Boolean
) {
    Column(Modifier.fillMaxWidth().verticalScrollIfNeeded(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Field).border(0.7.dp, Stroke, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (title.isEmpty()) Text(t("Kart başlığı"), color = Muted, fontSize = 16.sp)
            BasicTextField(title, { if (!busy) onTitle(it.take(2000)) }, Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Ink, fontSize = 16.sp), cursorBrush = SolidColor(Color.White), maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done))
        }
        if (categories.isNotEmpty()) {
            Text(t("Kategori"), color = Muted, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(t("Kategorisiz"), categoryId == null, enabled = !busy) { onCategory(null) }
                categories.forEach { c ->
                    Chip(c.name, categoryId == c.id, dot = categoryColor(c), enabled = !busy) { onCategory(c.id) }
                }
            }
        }
        Text(t("Tarih"), color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t("Tarih yok"), plannedOffset == null, enabled = !busy) { onPlanned(null) }
            Chip(t("Bugün"), plannedOffset == 0, enabled = !busy) { onPlanned(0) }
            Chip(t("Yarın"), plannedOffset == 1, enabled = !busy) { onPlanned(1) }
        }
    }
}

@Composable
private fun Modifier.verticalScrollIfNeeded(): Modifier = this.verticalScroll(rememberScrollState())

private fun categoryColor(c: CustomCategory): Color = c.customSolidColorArgb?.let { Color(it) }
    ?: ReminderColorChoices.getOrNull(c.accentStart)?.color ?: Color(0xFF3B82F6)

@Composable
private fun ExistingCardTab(reminders: List<ReminderItem>, selected: Long?, onSelect: (Long) -> Unit, busy: Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    val (active, completed) = remember(reminders, query) {
        reminders.filter { it.text.contains(query.trim(), ignoreCase = true) && it.mood == null }
            .sortedByDescending { it.updatedAt }.partition { !it.isCompleted }
    }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Field).padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (query.isEmpty()) Text(t("Kart ara"), color = Muted, fontSize = 15.sp)
            BasicTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 15.sp), cursorBrush = SolidColor(Color.White))
        }
        if (reminders.isEmpty()) Text(t("Henüz kart yok. \"Yeni kart\" ile oluştur."), color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(active, key = { it.id }) { CardRow(it, it.id == selected, busy) { onSelect(it.id) } }
            if (completed.isNotEmpty()) {
                item(key = "done") { Text(t("Tamamlananlar"), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
                items(completed, key = { it.id }) { CardRow(it, it.id == selected, busy) { onSelect(it.id) } }
            }
        }
    }
}

@Composable
private fun CardRow(item: ReminderItem, on: Boolean, busy: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Field)
        .border(if (on) 1.2.dp else 0.7.dp, if (on) Color.White else Stroke, RoundedCornerShape(18.dp))
        .clickable(enabled = !busy, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(item.text.ifBlank { t("Boş not") }, color = if (item.isCompleted) Muted else Ink, fontSize = 15.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (on) ActionIcon(ActionGlyph.Check, Modifier.size(18.dp), Ink)
    }
}

@Composable
private fun BoardTab(boards: List<com.cagri.real.boards.PanoBoard>, selected: String?, onSelect: (String) -> Unit, busy: Boolean) {
    if (boards.isEmpty()) {
        Text(t("Henüz pano yok. Pano'yu REAL'de oluşturduktan sonra buradan ekleyebilirsin."), color = Muted, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(boards, key = { it.id }) { b ->
            val on = b.id == selected
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Field)
                .border(if (on) 1.2.dp else 0.7.dp, if (on) Color.White else Stroke, RoundedCornerShape(18.dp))
                .clickable(enabled = !busy) { onSelect(b.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(ActionGlyph.Board, Modifier.size(18.dp), Muted)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (b.shared) Text(if (b.role == "OWNER") t("Paylaşılan pano") else t("{0} ile ortak", b.ownerName), color = Muted, fontSize = 12.sp)
                }
                if (on) ActionIcon(ActionGlyph.Check, Modifier.size(18.dp), Ink)
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, dot: Color? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Color.White else Field)
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(9.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(7.dp)) }
        Text(label, color = if (on) Color.Black else Ink.copy(alpha = 0.85f), fontSize = 13.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun SheetButton(text: String, filled: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp))
        .background(if (filled) (if (enabled) Color.White else Color.White.copy(alpha = 0.25f)) else Field)
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (filled) Color.Black else Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
