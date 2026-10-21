package com.cagri.real.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/** One thing taken from the keyboard's clipboard: text (links too) or an image the keyboard sent. */
sealed class TrayItem(val id: Long) {
    class Text(id: Long, val text: String) : TrayItem(id)
    class Image(id: Long, val uri: Uri) : TrayItem(id)
}

/**
 * The tray's rows live as long as the app process: copy a few things, add one to this card, open another card and
 * the rest are still there. Images are copied into the app cache at once (the keyboard's permission is short-lived).
 */
object ClipboardTrayStore {
    val items = mutableStateListOf<TrayItem>()
    private var nextId = 0L
    private var cacheCleared = false

    fun addText(text: String) { items.add(0, TrayItem.Text(nextId++, text)) }

    fun addImage(context: android.content.Context, source: Uri): Boolean {
        // Directly in real_camera: the photo import used for camera shots accepts only files from that folder.
        val dir = java.io.File(context.cacheDir, "real_camera").apply { mkdirs() }
        if (!cacheCleared) { // tray leftovers of an earlier app run; camera shots are not touched
            dir.listFiles { file -> file.name.startsWith("tray_") }?.forEach { it.delete() }
            cacheCleared = true
        }
        val file = java.io.File(dir, "tray_${System.currentTimeMillis()}_$nextId")
        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input -> file.outputStream().use { input.copyTo(it) } } != null
        }.getOrDefault(false)
        if (!copied || file.length() == 0L) { file.delete(); return false }
        items.add(0, TrayItem.Image(nextId++, Uri.fromFile(file)))
        return true
    }

    fun remove(item: TrayItem) {
        items.remove(item)
        if (item is TrayItem.Image) runCatching { item.uri.path?.let { java.io.File(it).delete() } }
    }
}

/**
 * "Panodan ekle": Android does not give apps the clipboard history, but the keyboard (Samsung, Gboard) has it.
 * A hidden text field opens the keyboard; every item the person taps in the keyboard's clipboard lands here as its
 * own row. "+" adds a row to the end of the card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardTray(
    visible: Boolean,
    busy: Boolean,
    reduceAnimations: Boolean,
    onAppend: (TrayItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val context = LocalContext.current
    val items = ClipboardTrayStore.items
    // TextFieldState: only this kind of field tells the keyboard it accepts images (Samsung, Gboard clipboards).
    val field = rememberTextFieldState()
    var capturing by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    // One keyboard clipboard tap arrives as one insertion; a short pause separates items (typing works too).
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collectLatest { text ->
            if (text.isBlank()) return@collectLatest
            delay(350)
            ClipboardTrayStore.addText(text.trim())
            field.clearText()
        }
    }
    fun startCapture() {
        capturing = true
        focus.requestFocus()
        keyboard?.show()
    }
    fun close() {
        keyboard?.hide()
        onDismiss()
    }

    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = ::close,
        properties = PopupProperties(focusable = true)
    ) {
        Box(Modifier.fillMaxSize().imePadding().statusBarsPadding(), contentAlignment = Alignment.TopEnd) {
            AnimatedVisibility(
                visible = shown,
                enter = if (reduceAnimations) fadeIn(tween(0)) else slideInHorizontally(tween(260)) { it } + fadeIn(tween(200)),
                exit = fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { it }
            ) {
                Column(
                    Modifier
                        .padding(top = 64.dp, end = 10.dp, bottom = 10.dp)
                        .width(268.dp)
                        .heightIn(max = 460.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xF21A1A1F))
                        .border(0.5.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(24.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The round "Panodan ekle" button: opens the keyboard.
                        Row(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (capturing) Color(0xFF91B9FF) else Color.White.copy(alpha = .12f))
                                .clickable(role = Role.Button, onClick = ::startCapture)
                                .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape)
                                    .background(if (capturing) Color(0xFF0B1A33) else Color.White.copy(alpha = .18f)),
                                contentAlignment = Alignment.Center
                            ) { ActionIcon(ActionGlyph.Paste, Modifier.size(14.dp), if (capturing) Color(0xFF91B9FF) else Color.White) }
                            Spacer(Modifier.width(8.dp))
                            Text("Panodan ekle", color = if (capturing) Color(0xFF0B1A33) else Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("Bitti", color = Color(0xFFC9CBD3), fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = ::close)
                                .padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                    Text(
                        if (capturing) "Klavyedeki pano simgesine bas, eklemek istediklerine dokun."
                        else "Klavyenin panosundaki son kopyaladıkların buraya gelir.",
                        color = Color(0xFF8E8E93), fontSize = 12.sp, lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                    )
                    // Invisible receiver for what the keyboard inserts: text, and images where the keyboard sends them.
                    BasicTextField(
                        state = field,
                        modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focus)
                            .contentReceiver { content ->
                                if (!content.hasMediaType(MediaType.Image)) return@contentReceiver content
                                content.consume { item ->
                                    val uri = item.uri ?: return@consume false
                                    ClipboardTrayStore.addImage(context, uri)
                                }
                            }
                    )
                    if (items.isEmpty()) {
                        Text("Henüz bir şey yok", color = Color(0xFF6E6E76), fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth())
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(items, key = { it.id }) { item ->
                                // Rows stay after "+": the same item can go to another card too.
                                TrayRow(item, enabled = !busy, onAppend = { onAppend(item) })
                            }
                        }
                        Text("+ kartın sonuna ekler. Liste uygulama kapanana kadar durur.", color = Color(0xFF6E6E76),
                            fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrayRow(item: TrayItem, enabled: Boolean, onAppend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .07f))
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            when (item) {
                is TrayItem.Text -> {
                    val link = item.text.startsWith("http", ignoreCase = true)
                    Text(item.text.replace('\n', ' '), color = Color.White, fontSize = 13.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (link) "Bağlantı" else "Metin", color = Color(0xFF8E8E93), fontSize = 11.sp)
                }
                is TrayItem.Image -> {
                    Text("Fotoğraf", color = Color.White, fontSize = 13.sp)
                    Text("Klavyenin panosundan", color = Color(0xFF8E8E93), fontSize = 11.sp)
                }
            }
        }
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF91B9FF).copy(alpha = if (enabled) 1f else .4f))
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Sona ekle", onClick = onAppend),
            contentAlignment = Alignment.Center
        ) { Text("+", color = Color(0xFF0B1A33), fontSize = 20.sp, fontWeight = FontWeight.Medium) }
    }
}
