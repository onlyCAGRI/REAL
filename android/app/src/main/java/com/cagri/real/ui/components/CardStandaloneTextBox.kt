package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.cagri.real.data.CardBlockPlacement
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.cardBlockSurface

/** A single detached toolbar for the selected text box, with no contribution to grid measurement. */
@Composable
internal fun CardTextSettingsControl(onRemove: (() -> Unit)? = null, onSettings: () -> Unit) {
    val offset = with(LocalDensity.current) { -32.dp.roundToPx() }
    Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, offset)) {
        Row(Modifier.liquidDockChip(RoundedCornerShape(14.dp)), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSettings, modifier = Modifier.size(28.dp)
                .semantics { contentDescription = t("Metin araçları") }) {
                ActionIcon(ActionGlyph.Settings, Modifier.size(15.dp), Color(0xFFB7B7C0))
            }
            if (onRemove != null) IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)
                .semantics { contentDescription = t("Metin kutusunu kaldır") }) {
                ActionIcon(ActionGlyph.Close, Modifier.size(15.dp), Color(0xFFB7B7C0))
            }
        }
    }
}

/** Reuses REAL's TEXT_SNIPPET attachment data; it is not another title or another storage system. */
@Composable
internal fun CardStandaloneTextBox(
    text: String, editing: Boolean, placement: CardBlockPlacement, darkText: Boolean,
    designStyle: DesignStyle, accentBrush: Brush?, showSettings: Boolean,
    onSettings: () -> Unit, onTextChange: (String) -> Unit, onRemove: () -> Unit
) {
    val compact = placement.minHeight in 1 until 200
    val padding = if (compact) 8.dp else 25.dp
    val lines = if (placement.minHeight > 0)
        ((placement.minHeight - if (compact) 16 else 50) / 31).coerceAtLeast(1) else Int.MAX_VALUE
    val ink = if (darkText) Color(0xFF101010) else Color(0xFFF4F4F6)
    val style = MaterialTheme.typography.bodyLarge.copy(color = ink, fontSize = 21.sp, lineHeight = 31.sp,
        textAlign = when (placement.textAlignment) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start },
        fontWeight = if (placement.textBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (placement.textItalic) FontStyle.Italic else FontStyle.Normal)
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()
            .then(if (placement.minHeight > 0) Modifier.height(placement.minHeight.dp) else Modifier)
            .cardBlockSurface().clip(RoundedCornerShape(28.dp))
            .background(accentBrush ?: if (designStyle == DesignStyle.LiquidGlass)
                Brush.verticalGradient(listOf(Color(0xFF18181A), LiquidGlassTokens.contentColor))
                else Brush.linearGradient(listOf(Color(0xFF25252C), Color(0xFF17171B), Color(0xFF131315))))
            .padding(horizontal = 22.dp, vertical = padding)) {
            if (editing) BasicTextField(
                value = text, onValueChange = onTextChange, textStyle = style, maxLines = lines,
                modifier = Modifier.fillMaxWidth().heightIn(min = if (placement.minHeight > 0) 0.dp else 96.dp)
                    .semantics { contentDescription = t("Metin kutusu") },
                cursorBrush = SolidColor(ink), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { field ->
                    if (text.isEmpty()) Text(t("Metin yaz…"), style = style.copy(color = ink.copy(alpha = .4f)))
                    field()
                }
            ) else SelectionContainer {
                Text(text, style = style, modifier = Modifier.fillMaxWidth(), maxLines = lines, overflow = TextOverflow.Ellipsis)
            }
        }
        if (editing && showSettings) CardTextSettingsControl(onRemove = onRemove, onSettings = onSettings)
    }
}
