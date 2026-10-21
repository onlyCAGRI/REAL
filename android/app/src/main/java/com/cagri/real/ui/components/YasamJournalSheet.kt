package com.cagri.real.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val SheetInk = Color(0xFF0B0B0D)
private val SheetGlass = Color(0xFF151518)
private val SheetStroke = Color.White.copy(alpha = 0.10f)
private val SheetMuted = Color(0xFF8E8E98)

/**
 * Yaşam ✎ "Bugün yaz". [onSave] yeni günlük kartını oluşturur; true dönerse sayfa kapanır,
 * false veya hata durumunda yazılan metin korunur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YasamJournalSheet(onDismiss: () -> Unit, onSave: suspend (String) -> Boolean) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun save() {
        val value = text.trim()
        if (value.isEmpty() || busy) return
        busy = true; error = null
        scope.launch {
            try {
                if (onSave(value)) onDismiss() else error = "Günlük kaydedilemedi."
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                error = "Günlük kaydedilemedi."
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = state,
        containerColor = SheetInk,
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 4.dp).size(36.dp, 4.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.22f))
            )
        }
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Bugün yaz", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp))
            Text("Nasıl geçti? Aklında kalanı yaz.", color = SheetMuted, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            val shape = RoundedCornerShape(22.dp)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 180.dp).clip(shape).background(SheetGlass)
                    .border(1.dp, SheetStroke, shape).padding(18.dp)
            ) {
                if (text.isEmpty()) Text("Günlüğün…", color = SheetMuted, fontSize = 16.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { if (!busy) { text = it; error = null } },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp, lineHeight = 24.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
            error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp) }
            val canSave = !busy && text.isNotBlank()
            SheetButton(if (busy) "Kaydediliyor…" else "Kaydet", filled = true, enabled = canSave, onClick = ::save)
            SheetButton("Vazgeç", filled = false, enabled = !busy) { if (!busy) onDismiss() }
        }
    }
}

@Composable
private fun SheetButton(label: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(shape)
            .background(if (filled) Color.White.copy(alpha = if (enabled) 1f else 0.35f) else SheetGlass)
            .border(1.dp, if (filled) Color.Transparent else SheetStroke, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (filled) Color.Black else Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold
        )
    }
}
