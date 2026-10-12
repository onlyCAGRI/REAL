package com.cagri.real.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.cagri.real.data.*
import com.cagri.real.ui.LocalCardContentComposition
import com.cagri.real.ui.cardBlockSurface
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.theme.RealType
import java.util.Locale
import kotlinx.coroutines.launch


@Composable
internal fun TimelineContent(style: DesignStyle, ambience: Color = Color.Transparent, content: @Composable ColumnScope.() -> Unit) {
    val tint = if (ambience == Color.Transparent) Modifier else Modifier.background(
        Brush.linearGradient(listOf(ambience.copy(alpha = 0.19f), ambience.copy(alpha = 0.07f), Color.Transparent)))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (LocalCardContentComposition.current) {
            Column(Modifier.fillMaxWidth().cardBlockSurface().clip(RoundedCornerShape(22.dp)).background(Color(0xFF141416)).then(tint).padding(16.dp), content = content)
        } else if (style == DesignStyle.LiquidGlass) {
            LiquidGlassContentSurface(Modifier.fillMaxWidth(), 22.dp) { Column(Modifier.fillMaxWidth().then(tint).padding(16.dp), content = content) }
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF141416))
                .border(0.8.dp, Color(0xFF353539), RoundedCornerShape(22.dp)).then(tint).padding(16.dp), content = content)
        }
    }
}

@Composable
fun ReminderContentSections(
    attachments: List<ReminderAttachment>, referenceUrl: String?, editing: Boolean,
    designStyle: DesignStyle, backdrop: GraphicsLayer, backdropPosition: () -> Offset,
    onRemove: (ReminderAttachment) -> Unit, onRemoveLink: () -> Unit,
    onUpdateSnippet: suspend (ReminderAttachment, String) -> Boolean = { _, _ -> false },
    onOpenAttachment: ((ReminderAttachment) -> Unit)? = null,
    allowSnippetEditing: Boolean = true
) {
    val context = LocalContext.current
    val files = attachments.filter { it.type == AttachmentType.FILE }
    val snippets = attachments.filter { it.type == AttachmentType.TEXT_SNIPPET }
    var reading by remember { mutableStateOf<ReminderAttachment?>(null) }
    var editingSnippet by remember { mutableStateOf<ReminderAttachment?>(null) }
    var editingSnippetText by remember { mutableStateOf("") }
    var snippetSaving by remember { mutableStateOf(false) }
    var snippetError by remember { mutableStateOf<String?>(null) }
    val snippetScope = rememberCoroutineScope()

    if (files.isNotEmpty() || !referenceUrl.isNullOrBlank()) {
        TimelineContent(designStyle) {
            Text("Ekler", color = Color(0xFFE2E2E8), style = RealType.detailBlockHeader)
            files.forEachIndexed { index, file ->
                key(file.id) {
                    if (index > 0) HorizontalDivider(color = Color(0xFF303035))
                    FileContentRow(fileBadge(file), file.displayName ?: "Dosya", fileMetadata(file), editing,
                        onOpen = {
                            try { if (onOpenAttachment != null) onOpenAttachment(file) else AttachmentStorage.open(context, file) }
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
                    Text(snippet.source.label, color = Color(0xFFD1D1D8), style = RealType.detailSnippetSource,
                        modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = if (LocalCardContentComposition.current) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                    if (allowSnippetEditing) Text(
                        "Düzenle",
                        color = Color(0xFFBFC9D2),
                        style = RealType.detailBlockMetaSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                snippetError = null
                                editingSnippetText = snippet.text.orEmpty()
                                editingSnippet = snippet
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    if (editing) IconButton(onClick = { onRemove(snippet) }, modifier = Modifier.semantics { contentDescription = "Metin alıntısını kaldır" }) {
                        ActionIcon(ActionGlyph.Close, Modifier.size(17.dp), Color(0xFFAEAFB8))
                    }
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(top = 8.dp, bottom = 4.dp)) {
                    snippet.sourceTitle?.let {
                        Text(
                            it,
                            color = Color(0xFFE6E6EC),
                            style = RealType.detailSnippetTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reading = snippet }
                                .padding(bottom = 5.dp)
                        )
                    }
                    ClickableNoteText(
                        text = snippet.text.orEmpty(),
                        color = Color(0xFFBFC0C8),
                        style = RealType.detailSnippetBody,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        onNonLinkClick = { reading = snippet }
                    )
                    Text(
                        "Metni aç",
                        color = Color(0xFFCAD3D9),
                        style = RealType.detailBlockMetaSecondary,
                        modifier = Modifier.padding(top = 8.dp).clickable { reading = snippet }
                    )
                }
            }
        }
    }

    reading?.let { snippet ->
        Dialog(onDismissRequest = { reading = null }) {
            DesignPanel(backdrop, backdropPosition, Modifier.fillMaxWidth().heightIn(max = 560.dp), corners = 28.dp,
                designStyle = designStyle, reduceDynamicBlur = true, liquidSheet = true) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(snippet.source.label, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (allowSnippetEditing) Text(
                            "Düzenle",
                            color = Color(0xFFBFC9D2),
                            style = RealType.detailBlockMeta,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    snippetError = null
                                    editingSnippetText = snippet.text.orEmpty()
                                    editingSnippet = snippet
                                    reading = null
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    snippet.sourceTitle?.let { Text(it, color = Color(0xFFD8D8E0), modifier = Modifier.padding(top = 8.dp)) }
                    SelectionContainer(
                        Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 410.dp)
                            .padding(top = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ClickableNoteText(
                            text = snippet.text.orEmpty(),
                            color = Color(0xFFD3D3DB),
                            style = RealType.detailSnippetReadingBody,
                            onNonLinkClick = {}
                        )
                    }
                    TextButton(onClick = { reading = null }, modifier = Modifier.align(Alignment.End)) { Text("Kapat", color = Color(0xFFD1DBE0)) }
                }
            }
        }
    }

    editingSnippet?.let { snippet ->
        val focusRequester = remember(snippet.id) { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(snippet.id) {
            kotlinx.coroutines.delay(80)
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
        Dialog(onDismissRequest = {
            if (!snippetSaving) {
                editingSnippet = null
                snippetError = null
            }
        }) {
            DesignPanel(backdrop, backdropPosition, Modifier.fillMaxWidth().heightIn(max = 590.dp), corners = 28.dp,
                designStyle = designStyle, reduceDynamicBlur = true, liquidSheet = true) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Notu düzenle", color = Color.White, style = RealType.detailDialogSubtitle)
                    Text(snippet.source.label, color = Color(0xFF9FA0AA), style = RealType.detailBlockMetaSecondary, modifier = Modifier.padding(top = 3.dp))
                    BasicTextField(
                        value = editingSnippetText,
                        onValueChange = { editingSnippetText = it },
                        enabled = !snippetSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 390.dp)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xB3121215))
                            .border(0.7.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                            .focusRequester(focusRequester),
                        textStyle = RealType.cardBodyProminent.copy(color = Color(0xFFE2E2E8)),
                        cursorBrush = SolidColor(Color(0xFF91B9FF))
                    )
                    snippetError?.let {
                        Text(it, color = Color(0xFFE19B9B), style = RealType.detailBlockMetaSecondary, modifier = Modifier.padding(top = 8.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            editingSnippet = null
                            snippetError = null
                        }, enabled = !snippetSaving) { Text("Vazgeç", color = Color(0xFFB6B7BF)) }
                        TextButton(
                            enabled = !snippetSaving,
                            onClick = {
                                if (snippetSaving) return@TextButton
                                snippetSaving = true
                                snippetError = null
                                snippetScope.launch {
                                    val saved = runCatching { onUpdateSnippet(snippet, editingSnippetText) }.getOrDefault(false)
                                    snippetSaving = false
                                    if (saved) {
                                        editingSnippet = null
                                        snippetError = null
                                    } else {
                                        snippetError = "Not kaydedilemedi. Yeniden dene."
                                    }
                                }
                            }
                        ) { Text(if (snippetSaving) "Kaydediliyor…" else "Kaydet", color = Color(0xFF91B9FF)) }
                    }
                }
            }
        }
    }
}

private data class NoteUrlSpan(val start: Int, val end: Int, val url: String)

private val NOTE_URL_REGEX = Regex("""(?i)(?:https?://|www\.)[^\s<>()]+""")
private val NOTE_URL_TRAILING_PUNCTUATION = setOf('.', ',', ';', ':', '!', '?', '\'', '"')

private fun detectNoteUrls(text: String): List<NoteUrlSpan> = NOTE_URL_REGEX.findAll(text).mapNotNull { match ->
    var end = match.range.last + 1
    while (end > match.range.first && text[end - 1] in NOTE_URL_TRAILING_PUNCTUATION) end--
    if (end <= match.range.first) null else NoteUrlSpan(match.range.first, end, text.substring(match.range.first, end))
}.toList()

private fun normalizeExternalNoteUrl(url: String): String =
    if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url

private fun openExternalNoteUrl(context: android.content.Context, rawUrl: String) {
    val normalized = normalizeExternalNoteUrl(rawUrl)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
    } catch (_: Exception) {
        Toast.makeText(context, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun ClickableNoteText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onNonLinkClick: () -> Unit
) {
    val context = LocalContext.current
    val urlSpans = remember(text) { detectNoteUrls(text) }
    val annotated = remember(text, urlSpans) {
        buildAnnotatedString {
            append(text)
            urlSpans.forEach { span ->
                addStringAnnotation(tag = "URL", annotation = span.url, start = span.start, end = span.end)
                addStyle(
                    SpanStyle(color = Color(0xFF91B9FF), textDecoration = TextDecoration.Underline),
                    start = span.start,
                    end = span.end
                )
            }
        }
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicText(
        text = annotated,
        modifier = Modifier.pointerInput(annotated, onNonLinkClick) {
            detectTapGestures { position ->
                val offset = textLayoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                val link = annotated
                    .getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()
                if (link != null) openExternalNoteUrl(context, link.item) else onNonLinkClick()
            }
        },
        style = TextStyle(color = color, fontSize = fontSize, lineHeight = lineHeight),
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { textLayoutResult = it }
    )
}

@Composable
internal fun ClickableNoteText(
    text: String,
    color: Color,
    style: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onNonLinkClick: () -> Unit
) {
    ClickableNoteText(
        text = text,
        color = color,
        fontSize = style.fontSize,
        lineHeight = style.lineHeight,
        maxLines = maxLines,
        overflow = overflow,
        onNonLinkClick = onNonLinkClick
    )
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
                Text(title, color = Color(0xFFE1E1E7), style = RealType.detailBlockMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(metadata, color = Color(0xFF92939E), style = RealType.detailAttribution, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
