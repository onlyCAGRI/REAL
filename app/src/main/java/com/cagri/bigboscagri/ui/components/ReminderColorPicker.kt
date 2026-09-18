package com.cagri.bigboscagri.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.ui.DesignStyle

data class ReminderColorChoice(val label: String, val color: Color)

/**
 * 10 harmonized tones designed for dark glass surfaces and seamless gradient mixing.
 */
val ReminderColorChoices = listOf(
    ReminderColorChoice("Gece Mavisi", Color(0xFF233F73)),
    ReminderColorChoice("Kobalt", Color(0xFF2C5696)),
    ReminderColorChoice("Buzul Cyan", Color(0xFF246C7E)),
    ReminderColorChoice("Zümrüt", Color(0xFF276B53)),
    ReminderColorChoice("Yosun Yeşili", Color(0xFF3C6A48)),
    ReminderColorChoice("Amber", Color(0xFF8B5D34)),
    ReminderColorChoice("Terracotta", Color(0xFF8C4638)),
    ReminderColorChoice("Mürdüm", Color(0xFF873858)),
    ReminderColorChoice("Erik Moru", Color(0xFF6B3A79)),
    ReminderColorChoice("İris Moru", Color(0xFF4C3C82))
)

fun reminderAccentBrush(startIndex: Int?, endIndex: Int?, completed: Boolean = false): Brush? {
    val start = startIndex?.let { ReminderColorChoices.getOrNull(it)?.color }
    val end = endIndex?.let { ReminderColorChoices.getOrNull(it)?.color }
    if (start == null && end == null) return null
    val alpha = if (completed) 0.38f else 0.76f
    return when {
        start != null && end != null -> Brush.linearGradient(
            listOf(start.copy(alpha = alpha), end.copy(alpha = alpha))
        )
        start != null -> {
            val c = start.copy(alpha = alpha)
            val dim = start.copy(alpha = alpha * 0.55f)
            Brush.linearGradient(listOf(c, dim))
        }
        else -> {
            val c = end!!.copy(alpha = alpha)
            val dim = end.copy(alpha = alpha * 0.55f)
            Brush.linearGradient(listOf(dim, c))
        }
    }
}

@Composable
fun ReminderColorPicker(
    startIndex: Int?,
    endIndex: Int?,
    onStartChange: (Int?) -> Unit,
    onEndChange: (Int?) -> Unit,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    modifier: Modifier = Modifier
) {
    val previewBrush = remember(startIndex, endIndex) {
        val start = startIndex?.let { ReminderColorChoices.getOrNull(it)?.color }
        val end = endIndex?.let { ReminderColorChoices.getOrNull(it)?.color }
        when {
            start != null && end != null -> Brush.linearGradient(listOf(start, end))
            start != null -> Brush.linearGradient(listOf(start, start.copy(alpha = 0.55f)))
            end != null -> Brush.linearGradient(listOf(end.copy(alpha = 0.55f), end))
            else -> Brush.linearGradient(listOf(Color(0xFF26262E), Color(0xFF17171B)))
        }
    }

    DesignPanel(
        backdrop = backdrop,
        backdropPosition = backdropPosition,
        modifier = modifier,
        corners = 28.dp,
        designStyle = designStyle,
        reduceDynamicBlur = reduceDynamicBlur
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Text("Kart renkleri", color = Color(0xFFF0F0F4), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            ColorChoiceRow("1. renk", startIndex, onStartChange)
            ColorChoiceRow("2. renk", endIndex, onEndChange)
            Box(
                Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp))
                    .background(previewBrush)
                    .border(0.7.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(9.dp))
            )
        }
    }
}

@Composable
private fun ColorChoiceRow(label: String, selectedIndex: Int?, onSelected: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFB7B7C0), fontSize = 11.sp, modifier = Modifier.width(52.dp))
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "none") {
                val selected = selectedIndex == null
                Box(
                    Modifier.size(31.dp).clip(CircleShape)
                        .background(if (selected) Color(0xFF2D2D36) else Color(0xFF1B1B1F))
                        .border(
                            width = if (selected) 2.2.dp else 0.8.dp,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.18f),
                            shape = CircleShape
                        )
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelected(null) }
                        )
                        .semantics { contentDescription = "$label: Renk yok (Varsayılan)" },
                    contentAlignment = Alignment.Center
                ) {
                    ActionIcon(
                        glyph = ActionGlyph.Close,
                        modifier = Modifier.size(13.dp),
                        color = if (selected) Color.White else Color(0xFF878792)
                    )
                }
            }
            items(ReminderColorChoices.size, key = { it }) { index ->
                val choice = ReminderColorChoices[index]
                val selected = index == selectedIndex
                Box(
                    Modifier.size(31.dp).clip(CircleShape).background(choice.color)
                        .border(
                            width = if (selected) 2.2.dp else 0.8.dp,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.16f),
                            shape = CircleShape
                        )
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelected(index) }
                        )
                        .semantics { contentDescription = "$label: ${choice.label}" }
                )
            }
        }
    }
}
