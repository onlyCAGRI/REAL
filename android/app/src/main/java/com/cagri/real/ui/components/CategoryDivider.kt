package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cagri.real.data.ReminderRepository

/** Ayraç in a category's list: the title in the middle with a thin line on each side (just the line when untitled). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryDividerRow(title: String, editable: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(enabled = editable, onClick = {}, onLongClickLabel = t("Ayracı düzenle"), onLongClick = { menu = true })
                .padding(horizontal = 28.dp, vertical = 14.dp)
                .semantics { contentDescription = if (title.isBlank()) t("Ayraç") else t("Ayraç: {0}", title) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val line = Modifier.weight(1f).height(0.75.dp).background(Color.White.copy(alpha = 0.16f))
            Box(line)
            if (title.isNotBlank()) {
                Text(title, color = Color(0xFFB7B7C0), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 240.dp))
                Box(line)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.align(Alignment.Center)) {
            DropdownMenuItem(text = { Text(t("Başlığı düzenle")) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text(t("Sil"), color = Color(0xFFFF9AA0)) }, onClick = { menu = false; onDelete() })
        }
    }
}

/** Title of a new or existing Ayraç (may stay empty: then it is just a line). */
@Composable
fun DividerTitleDialog(initial: String, editing: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(Color(0xFF16161A), RoundedCornerShape(26.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (editing) t("Ayracı düzenle") else t("Ayraç ekle"), color = Color(0xFFF5F5F8), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(t("Kartlarının arasına bir çizgi ve kısa bir başlık. Boş bırakırsan sadece çizgi olur."),
                color = Color(0xFFBEBEC8), fontSize = 13.sp, lineHeight = 19.sp)
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp)) {
                if (text.isEmpty()) Text(t("Ör. 1. hafta bitti"), color = Color(0xFF6F6F78), fontSize = 15.sp)
                BasicTextField(
                    value = text, onValueChange = { text = it.take(ReminderRepository.DIVIDER_TITLE_MAX) }, singleLine = true,
                    cursorBrush = SolidColor(Color(0xFF91B9FF)), textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = t("Ayraç başlığı") }
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(46.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(23.dp))) {
                    Text(t("Vazgeç"), color = Color(0xFFE9E9EF), fontSize = 14.sp)
                }
                TextButton(onClick = { onSave(text.trim()) }, modifier = Modifier.weight(1f).height(46.dp)
                    .background(Color.White, RoundedCornerShape(23.dp))) {
                    Text(if (editing) t("Kaydet") else t("Ekle"), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** A short line of text in the app's dark dialog (Tamamlama notu and the like). */
@Composable
fun ShortTextDialog(title: String, body: String, hint: String, initial: String, max: Int, saveLabel: String,
                    onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(Color(0xFF16161A), RoundedCornerShape(26.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = Color(0xFFF5F5F8), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = Color(0xFFBEBEC8), fontSize = 13.sp, lineHeight = 19.sp)
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp)) {
                if (text.isEmpty()) Text(hint, color = Color(0xFF6F6F78), fontSize = 15.sp)
                BasicTextField(
                    value = text, onValueChange = { text = it.take(max) }, maxLines = 3,
                    cursorBrush = SolidColor(Color(0xFF91B9FF)), textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = title }
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(46.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(23.dp))) {
                    Text(t("Vazgeç"), color = Color(0xFFE9E9EF), fontSize = 14.sp)
                }
                TextButton(onClick = { onSave(text.trim()) }, modifier = Modifier.weight(1f).height(46.dp)
                    .background(Color.White, RoundedCornerShape(23.dp))) {
                    Text(saveLabel, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
