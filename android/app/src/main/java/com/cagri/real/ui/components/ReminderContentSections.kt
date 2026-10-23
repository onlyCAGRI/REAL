package com.cagri.real.ui.components

import com.cagri.real.i18n.I18n
import com.cagri.real.i18n.t
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.focus.onFocusChanged
import com.cagri.real.data.*
import com.cagri.real.ui.LocalCardContentComposition
import com.cagri.real.ui.cardBlockSurface
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.theme.RealType
import java.util.Locale
import kotlinx.coroutines.launch


@Composable
internal fun TimelineContent(
    style: DesignStyle, ambience: Color = Color.Transparent,
    /** Live sound level 0..1 (music preview): the whole block glows with it, like the voice block. Read while drawing. */
    liveLevel: (() -> Float)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glowColor = if (ambience == Color.Transparent) Color(0xFF2D6FD6) else ambience
    val glow = if (liveLevel == null) Modifier else Modifier.drawBehind {
        val level = liveLevel()
        if (level > 0.001f) {
            val maxDimension = maxOf(size.width, size.height)
            drawRect(Brush.radialGradient(listOf(glowColor.copy(alpha = 0.12f + level * 0.32f), Color.Transparent),
                center = Offset(size.width * 0.86f, size.height * 0.95f), radius = maxDimension * (1.10f + level * 0.30f)))
            drawRect(Brush.verticalGradient(listOf(glowColor.copy(alpha = 0.04f + level * 0.12f), Color.Transparent)))
        }
    }
    val tint = glow.then(if (ambience == Color.Transparent) Modifier else Modifier.background(
        Brush.linearGradient(listOf(ambience.copy(alpha = 0.19f), ambience.copy(alpha = 0.07f), Color.Transparent))))
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
    // "Metni aç" grows the block downward in place (no separate window).
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var editingSnippet by remember { mutableStateOf<ReminderAttachment?>(null) }
    var editingSnippetText by remember { mutableStateOf("") }
    var snippetSaving by remember { mutableStateOf(false) }
    var snippetError by remember { mutableStateOf<String?>(null) }
    val snippetScope = rememberCoroutineScope()
    /** Saves the in-place edit; an unchanged text just closes it. */
    fun saveInlineSnippet(snippet: ReminderAttachment) {
        if (snippetSaving || editingSnippet?.id != snippet.id) return
        val text = editingSnippetText
        if (text == snippet.text.orEmpty()) { editingSnippet = null; snippetError = null; return }
        snippetSaving = true
        snippetError = null
        snippetScope.launch {
            val saved = runCatching { onUpdateSnippet(snippet, text) }.getOrDefault(false)
            snippetSaving = false
            if (saved) { editingSnippet = null; snippetError = null }
            else snippetError = t("Not kaydedilemedi. Yeniden dene.")
        }
    }

    if (files.isNotEmpty() || !referenceUrl.isNullOrBlank()) {
        TimelineContent(designStyle) {
            Text(t("Ekler"), color = Color(0xFFE2E2E8), style = RealType.detailBlockHeader)
            files.forEachIndexed { index, file ->
                key(file.id) {
                    if (index > 0) HorizontalDivider(color = Color(0xFF303035))
                    FileContentRow(fileBadge(file), file.displayName ?: t("Dosya"), fileMetadata(file), editing,
                        onOpen = {
                            try { if (onOpenAttachment != null) onOpenAttachment(file) else AttachmentStorage.open(context, file) }
                            catch (_: android.content.ActivityNotFoundException) { com.cagri.real.ui.components.RealToast.show(context, t("Bu dosyayı açabilecek bir uygulama bulunamadı"), long = true) }
                            catch (_: Exception) { com.cagri.real.ui.components.RealToast.show(context, t("Dosyaya erişilemiyor. Silinmiş veya erişim izni kaldırılmış olabilir."), long = true) }
                        }, onRemove = { onRemove(file) })
                }
            }
            if (!referenceUrl.isNullOrBlank()) {
                if (files.isNotEmpty()) HorizontalDivider(color = Color(0xFF303035))
                val domain = remember(referenceUrl) { runCatching { Uri.parse(referenceUrl).host?.removePrefix("www.") }.getOrNull() ?: referenceUrl }
                FileContentRow("LINK", domain, referenceUrl, editing, onOpen = {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(referenceUrl))) }
                    catch (_: Exception) { com.cagri.real.ui.components.RealToast.show(context, t("Bağlantı açılamadı")) }
                }, onRemove = onRemoveLink)
            }
        }
    }

    snippets.forEach { snippet ->
        key(snippet.id) {
            TimelineContent(designStyle) {
                val inlineEditing = editingSnippet?.id == snippet.id
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ActionIcon(if (snippet.source == SnippetSource.WHATSAPP_TEXT) ActionGlyph.WhatsApp else ActionGlyph.Note,
                        Modifier.size(18.dp), Color(0xFFC2C6CB))
                    Text(snippet.source.label, color = Color(0xFFD1D1D8), style = RealType.detailSnippetSource,
                        modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = if (LocalCardContentComposition.current) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                    // Editing happens in place: tap the text, type, "Bitti" (or tap elsewhere) saves.
                    if (inlineEditing) Text(
                        if (snippetSaving) t("Kaydediliyor…") else t("Bitti"),
                        color = Color(0xFFE6E6EC),
                        style = RealType.detailBlockMetaSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !snippetSaving) { saveInlineSnippet(snippet) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    if (editing) IconButton(onClick = { onRemove(snippet) }, modifier = Modifier.semantics { contentDescription = t("Metin alıntısını kaldır") }) {
                        ActionIcon(ActionGlyph.Close, Modifier.size(17.dp), Color(0xFFAEAFB8))
                    }
                }
                val open = snippet.id in expanded
                val long = remember(snippet.text) { snippet.text.orEmpty().let { it.length > 180 || it.lines().size > 4 } }
                fun toggle() { if (long) expanded = if (open) expanded - snippet.id else expanded + snippet.id }
                fun startEditing() {
                    if (!allowSnippetEditing) { toggle(); return }
                    if (editingSnippet != null && editingSnippet?.id != snippet.id) editingSnippet?.let { saveInlineSnippet(it) }
                    snippetError = null
                    editingSnippetText = snippet.text.orEmpty()
                    editingSnippet = snippet
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .animateContentSize(androidx.compose.animation.core.tween(240))
                    .padding(top = 8.dp, bottom = 4.dp)) {
                    snippet.sourceTitle?.let {
                        Text(
                            it,
                            color = Color(0xFFE6E6EC),
                            style = RealType.detailSnippetTitle,
                            maxLines = if (open || inlineEditing) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggle() }
                                .padding(bottom = 5.dp)
                        )
                    }
                    if (inlineEditing) {
                        val focusRequester = remember(snippet.id) { FocusRequester() }
                        val keyboard = LocalSoftwareKeyboardController.current
                        var hadFocus by remember(snippet.id) { mutableStateOf(false) }
                        LaunchedEffect(snippet.id) {
                            kotlinx.coroutines.delay(60)
                            runCatching { focusRequester.requestFocus() }
                            keyboard?.show()
                        }
                        BasicTextField(
                            value = editingSnippetText,
                            onValueChange = { editingSnippetText = it },
                            enabled = !snippetSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused) hadFocus = true
                                    else if (hadFocus && editingSnippet?.id == snippet.id) saveInlineSnippet(snippet)
                                },
                            textStyle = RealType.detailSnippetBody.copy(color = Color(0xFFE6E6EC)),
                            cursorBrush = SolidColor(Color(0xFF91B9FF))
                        )
                        snippetError?.let {
                            Text(it, color = Color(0xFFE19B9B), style = RealType.detailBlockMetaSecondary, modifier = Modifier.padding(top = 6.dp))
                        }
                    } else {
                        ClickableNoteText(
                            text = snippet.text.orEmpty(),
                            color = Color(0xFFBFC0C8),
                            style = RealType.detailSnippetBody,
                            maxLines = if (open) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            onNonLinkClick = { startEditing() }
                        )
                        if (long) Text(
                            if (open) t("Daralt") else t("Metni aç"),
                            color = Color(0xFFCAD3D9),
                            style = RealType.detailBlockMetaSecondary,
                            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { toggle() }
                        )
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
        com.cagri.real.ui.components.RealToast.show(context, t("Bağlantı açılamadı"))
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
        if (editing) IconButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = t("{0} ekini kaldır", title) }) {
            ActionIcon(ActionGlyph.Close, Modifier.size(16.dp), Color(0xFFADB0BA))
        }
    }
}

private fun fileBadge(file: ReminderAttachment): String = file.displayName?.substringAfterLast('.', "")
    ?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)?.take(5)
    ?: when (file.mimeType) { "application/pdf" -> "PDF"; "application/zip" -> "ZIP"; else -> "DOSYA" }
private fun fileMetadata(file: ReminderAttachment): String = fileBadge(file) + (file.sizeBytes?.let { size ->
    when {
        size >= 1024 * 1024 -> " · " + String.format(I18n.locale, "%.1f MB", size / (1024.0 * 1024))
        size >= 1024 -> t(" · {0} KB", size / 1024)
        else -> " · $size B"
    }
} ?: "")
