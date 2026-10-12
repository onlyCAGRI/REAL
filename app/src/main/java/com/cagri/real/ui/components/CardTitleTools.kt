package com.cagri.real.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cagri.real.data.CardBlockPlacement

/** Detached tools never add height to the title's persisted canvas placement. */
@Composable
internal fun CardTitleTools(placement: CardBlockPlacement, onChange: (CardBlockPlacement) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().liquidDockChip(RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Metin biçimi", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Bitti", color = Color.White) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("start" to "Sola", "center" to "Ortala", "end" to "Sağa").forEach { (key, label) ->
                    TitleTool(label, placement.textAlignment == key, Modifier.weight(1f)) {
                        onChange(placement.copy(textAlignment = key))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TitleTool("Kalın", placement.textBold, Modifier.weight(1f), bold = true) {
                    onChange(placement.copy(textBold = !placement.textBold))
                }
                TitleTool("İtalik", placement.textItalic, Modifier.weight(1f), italic = true) {
                    onChange(placement.copy(textItalic = !placement.textItalic))
                }
            }
        }
    }
}

@Composable
private fun TitleTool(label: String, active: Boolean, modifier: Modifier, bold: Boolean = false,
                      italic: Boolean = false, onClick: () -> Unit) {
    val color by animateColorAsState(if (active) Color.White.copy(alpha = .18f) else Color.White.copy(alpha = .04f),
        if (LocalLiquidReduceMotion.current) snap() else tween(160), label = "title-tool")
    Box(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(color)
        .semantics { selected = active; contentDescription = label }
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (active) Color.White else Color(0xFFB7B7C0), fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)
    }
}
