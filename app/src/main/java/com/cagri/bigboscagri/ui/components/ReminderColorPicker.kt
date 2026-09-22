package com.cagri.bigboscagri.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.data.DEFAULT_GRADIENT_BLEND_FRACTION
import com.cagri.bigboscagri.data.resolveGradientBlendFraction
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

fun reminderAccentBrush(
    startIndex: Int?,
    endIndex: Int?,
    completed: Boolean = false,
    blendFraction: Float? = null,
    customSolidColorArgb: Int? = null
): Brush? {
    val alpha = if (completed) 0.38f else 0.76f
    if (customSolidColorArgb != null) {
        val customColor = Color(customSolidColorArgb)
        val c = customColor.copy(alpha = alpha)
        val dim = customColor.copy(alpha = alpha * 0.55f)
        return Brush.linearGradient(listOf(c, dim))
    }
    val start = startIndex?.let { ReminderColorChoices.getOrNull(it)?.color }
    val end = endIndex?.let { ReminderColorChoices.getOrNull(it)?.color }
    if (start == null && end == null) return null
    return when {
        start != null && end != null -> {
            val fraction = resolveGradientBlendFraction(blendFraction)
            val c1 = start.copy(alpha = alpha)
            val c2 = end.copy(alpha = alpha)
            val bridge = lerp(c1, c2, fraction)
            Brush.linearGradient(
                0.0f to c1,
                0.5f to bridge,
                1.0f to c2
            )
        }
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
    gradientBlendFraction: Float? = null,
    onGradientBlendFractionChange: (Float) -> Unit = {},
    customSolidColorArgb: Int? = null,
    onUseTransitionColor: (() -> Unit)? = null,
    isStarred: Boolean = false,
    starBorderIndex: Int? = null,
    onStarBorderChange: (Int?) -> Unit = {},
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    modifier: Modifier = Modifier
) {
    val previewBrush = remember(startIndex, endIndex, customSolidColorArgb) {
        if (customSolidColorArgb != null) {
            val c = Color(customSolidColorArgb)
            Brush.linearGradient(listOf(c, c.copy(alpha = 0.55f)))
        } else {
            val start = startIndex?.let { ReminderColorChoices.getOrNull(it)?.color }
            val end = endIndex?.let { ReminderColorChoices.getOrNull(it)?.color }
            when {
                start != null && end != null -> Brush.linearGradient(listOf(start, end))
                start != null -> Brush.linearGradient(listOf(start, start.copy(alpha = 0.55f)))
                end != null -> Brush.linearGradient(listOf(end.copy(alpha = 0.55f), end))
                else -> Brush.linearGradient(listOf(Color(0xFF26262E), Color(0xFF17171B)))
            }
        }
    }

    val startChoice = startIndex?.let { ReminderColorChoices.getOrNull(it) }
    val endChoice = endIndex?.let { ReminderColorChoices.getOrNull(it) }

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
            if (isStarred) {
                ColorChoiceRow("Yıldız çerçevesi", starBorderIndex, onStarBorderChange, allowNone = false)
            }
            if (startChoice != null && endChoice != null) {
                val fraction = resolveGradientBlendFraction(gradientBlendFraction)
                val bridgeColor = remember(startChoice.color, endChoice.color, fraction) {
                    lerp(startChoice.color, endChoice.color, fraction)
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Geçiş", color = Color(0xFFB7B7C0), fontSize = 11.sp, modifier = Modifier.width(96.dp))
                        ContinuousGradientTransitionBar(
                            fraction = fraction,
                            onFractionChange = onGradientBlendFractionChange,
                            startColor = startChoice.color,
                            endColor = endChoice.color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (onUseTransitionColor != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 96.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(0.7.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onUseTransitionColor
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(bridgeColor)
                                        .border(0.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                )
                                Text(
                                    text = "Geçiş rengini kullan",
                                    color = Color(0xFFC8C8D2),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            } else {
                val previewBorderColor = if (isStarred && starBorderIndex != null) {
                    ReminderColorChoices.getOrNull(starBorderIndex)?.color?.copy(alpha = 0.85f) ?: Color.White.copy(alpha = 0.14f)
                } else {
                    Color.White.copy(alpha = 0.14f)
                }
                val previewBorderWidth = if (isStarred && starBorderIndex != null) 1.25.dp else 0.7.dp
                Box(
                    Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp))
                        .background(previewBrush)
                        .border(previewBorderWidth, previewBorderColor, RoundedCornerShape(9.dp))
                )
            }
        }
    }
}

@Composable
private fun ColorChoiceRow(
    label: String,
    selectedIndex: Int?,
    onSelected: (Int?) -> Unit,
    allowNone: Boolean = true
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFB7B7C0), fontSize = 11.sp, modifier = Modifier.width(96.dp))
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (allowNone) {
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

@Composable
private fun ContinuousGradientTransitionBar(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    startColor: Color,
    endColor: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val clampedFraction = fraction.coerceIn(0.0f, 1.0f)
    val bridgeColor = remember(startColor, endColor, clampedFraction) {
        lerp(startColor, endColor, clampedFraction)
    }
    val gradientBrush = remember(startColor, endColor) {
        Brush.horizontalGradient(listOf(startColor, endColor))
    }

    BoxWithConstraints(
        modifier = modifier
            .height(36.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width
                    val indicatorRadiusPx = with(density) { 7.dp.toPx() }
                    val availableTravelPx = (width - 2 * indicatorRadiusPx).coerceAtLeast(1f)
                    val updateFraction: (Float) -> Unit = { x ->
                        val newFrac = ((x - indicatorRadiusPx) / availableTravelPx).coerceIn(0.0f, 1.0f)
                        onFractionChange(newFrac)
                    }
                    updateFraction(down.position.x)
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        updateFraction(change.position.x)
                        change.consume()
                    }
                }
            }
            .semantics { contentDescription = "Geçiş çubuğu" },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val indicatorSize = 14.dp
        val availableTravel = (totalWidth - indicatorSize).coerceAtLeast(0.dp)
        val indicatorOffset = availableTravel * clampedFraction

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(gradientBrush)
                .border(0.7.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .size(indicatorSize)
                .clip(CircleShape)
                .background(bridgeColor)
                .border(1.5.dp, Color.White.copy(alpha = 0.95f), CircleShape)
        )
    }
}
