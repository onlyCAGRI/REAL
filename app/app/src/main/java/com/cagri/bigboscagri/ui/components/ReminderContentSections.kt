package com.cagri.bigboscagri.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cagri.bigboscagri.data.*
import com.cagri.bigboscagri.ui.DesignStyle
import java.util.Locale

/** Quiet content surfaces inside the recorded timeline never sample their own backdrop. */
@Composable
private fun TimelineContent(style: DesignStyle, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(28.dp).width(1.5.dp).background(Color(0xFF38383F))) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF38383F)).align(Alignment.Center))
        }
        if (style == DesignStyle.LiquidGlass) {
            LiquidGlassContentSurface(Modifier.fillMaxWidth(), 22.dp) { Column(Modifier.fillMaxWidth().padding(16.dp), content = content) }
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF141416))
                .border(0.8.dp, Color(0xFF353539), RoundedCornerShape(22.dp)).padding(16.dp), content = content)
        }
    }
}

@Composable
fun ReminderContentSections(
    attachments: List<ReminderAttachment>, referenceUrl: String?, editing: Boolean,
    designStyle: DesignStyle, backdrop: GraphicsLayer, backdropPosition: () -> Offset,
    onRemove: (ReminderAttachment) -> Unit, onRemoveLink: () -> Unit
) {
    val context = LocalContext.current
    val files = attachments.filter { it.type == AttachmentType.FILE }
    val snippets = attachments.filter { it.type == AttachmentType.TEXT_SNIPPET }
    var reading by remember { mutableStateOf<ReminderAttachment?>(null) }
    if (files.isNotEmpty() || !referenceUrl.isNullOrBlank()) {
        TimelineContent(designStyle) {
            Text("Ekler", color = Color(0xFFE2E2E8), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            files.forEachIndexed { index, file ->
                key(file.id) {
                    if (index > 0) HorizontalDivider(color = Color(0xFF303035))
                    FileContentRow(fileBadge(file), file.displayName ?: "Dosya", fileMetadata(file), editing,
                        onOpen = {
                            try { AttachmentStorage.open(context, file) }
                            catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "Bu dosyayı açabilecek bir uygulama bulunamadı", Toast.LENGTH_LONG).show() }
                            catch (_: Exception) { Toast.makeText(context, "Dosyaya erişilemiyor. Silinmiş veya erişim izni kaldırılmış olabilir.", Toast.LENGTH_LONG).show() }
                        }, onRemove = { onRemove(file) })
                }
            }
            if (!referenceUrl.isNullOrBlank()) {
                if (files.isNotEmpty()) HorizontalDivider(color = Color(0xFF303035))
                val domain = remember(referenceUrl) { runCatching { Uri.parse(referenceUrl).host?.removePrefix("www.") }.getOrNull() ?: referenceUrl }
                FileContentRow("LINK", domain, referenceUrl, editing, onOpen = {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(referenceUrl))) }
                    catch (_: Exception) { Toast.makeText(context, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show() }
                }, onRemove = onRemoveLink)
            }
        }
    }
    snippets.forEach { snippet ->
        key(snippet.id) {
            TimelineContent(designStyle) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ActionIcon(if (snippet.source == SnippetSource.WHATSAPP_TEXT) ActionGlyph.WhatsApp else ActionGlyph.Note,
                        Modifier.size(18.dp), Color(0xFFC2C6CB))
                    Text(snippet.source.label, color = Color(0xFFD1D1D8), fontSize = 13.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    if (editing) IconButton(onClick = { onRemove(snippet) }, modifier = Modifier.semantics { contentDescription = "Metin alıntısını kaldır" }) {
                        ActionIcon(ActionGlyph.Close, Modifier.size(17.dp), Color(0xFFAEAFB8))
                    }
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { reading = snippet }.padding(top = 8.dp, bottom = 4.dp)) {
                    snippet.sourceTitle?.let { Text(it, color = Color(0xFFE6E6EC), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 5.dp)) }
                    Text(snippet.text.orEmpty(), color = Color(0xFFBFC0C8), fontSize = 14.sp, lineHeight = 21.sp,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text("Metni aç", color = Color(0xFFCAD3D9), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    reading?.let { snippet ->
        Dialog(onDismissRequest = { reading = null }) {
            // Dialog has its own window coordinates; use the same material's static fallback.
            DesignPanel(backdrop, backdropPosition, Modifier.fillMaxWidth().heightIn(max = 560.dp), corners = 28.dp,
                designStyle = designStyle, reduceDynamicBlur = true, liquidSheet = true) {
                Column(Modifier.padding(20.dp)) {
                    Text(snippet.source.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                    snippet.sourceTitle?.let { Text(it, color = Color(0xFFD8D8E0), modifier = Modifier.padding(top = 8.dp)) }
                    val paragraphs = remember(snippet.text) { snippet.text.orEmpty().split('\n').flatMap { it.ifEmpty { " " }.chunked(4000) } }
                    SelectionContainer(Modifier.weight(1f, fill = false).padding(top = 16.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(paragraphs) { paragraph -> Text(paragraph, color = Color(0xFFD3D3DB), fontSize = 15.sp, lineHeight = 23.sp) }
                        }
                    }
                    TextButton(onClick = { reading = null }, modifier = Modifier.align(Alignment.End)) { Text("Kapat", color = Color(0xFFD1DBE0)) }
                }
            }
        }
    }
}

@Composable
private fun FileContentRow(badge: String, title: String, metadata: String, editing: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF252529)), contentAlignment = Alignment.Center) {
                Text(badge.take(5), color = Color(0xFFD0D1D7), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color(0xFFE1E1E7), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(metadata, color = Color(0xFF92939E), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ActionIcon(ActionGlyph.ChevronRight, Modifier.size(14.dp), Color(0xFF8E8E98))
        }
        if (editing) IconButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = "$title ekini kaldır" }) {
            ActionIcon(ActionGlyph.Close, Modifier.size(16.dp), Color(0xFFADB0BA))
        }
    }
}

private fun fileBadge(file: ReminderAttachment): String = file.displayName?.substringAfterLast('.', "")
    ?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)?.take(5)
    ?: when (file.mimeType) { "application/pdf" -> "PDF"; "application/zip" -> "ZIP"; else -> "DOSYA" }
private fun fileMetadata(file: ReminderAttachment): String = fileBadge(file) + (file.sizeBytes?.let { size ->
    when {
        size >= 1024 * 1024 -> " · " + String.format(Locale.forLanguageTag("tr"), "%.1f MB", size / (1024.0 * 1024))
        size >= 1024 -> " · ${size / 1024} KB"
        else -> " · $size B"
    }
} ?: "")
