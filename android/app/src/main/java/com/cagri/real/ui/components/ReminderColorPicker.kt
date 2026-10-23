package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.annotation.SuppressLint

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.cagri.real.data.resolveGradientBlendFraction
import com.cagri.real.ui.DesignStyle

data class ReminderColorChoice(val label: String, val color: Color)

val ReminderColorChoices get() = listOf(
    // REAL 2026 palette — rebuilt from the approved visual references.
    // The six reference colors are kept at their exact supplied hex values.
    ReminderColorChoice(t("Duman Beyazı"), Color(0xFFEFF0F6)), // Smoky White — exact reference
    ReminderColorChoice(t("Jet Siyahı"), Color(0xFF292B2D)),   // Jet Black — exact reference
    ReminderColorChoice(t("Royal Mavi"), Color(0xFF4558C8)),  // Royal Blue — exact reference
    ReminderColorChoice(t("Gece Mavisi"), Color(0xFF263C74)),
    ReminderColorChoice(t("Gökyüzü"), Color(0xFF78C9F2)),
    ReminderColorChoice(t("Petrol"), Color(0xFF2E8793)),
    ReminderColorChoice(t("Turkuaz"), Color(0xFF50C4B7)),
    ReminderColorChoice(t("Nane"), Color(0xFF8ED9B6)),
    ReminderColorChoice(t("Zümrüt"), Color(0xFF36A269)),
    ReminderColorChoice("Old Flax", Color(0xFFDFF37D)),    // Old flax — exact reference
    ReminderColorChoice(t("Altın"), Color(0xFFE5B84D)),
    ReminderColorChoice(t("Bronz"), Color(0xFFA77952)),
    ReminderColorChoice(t("Somon Turuncu"), Color(0xFFEE5E37)), // Salmon orange — exact reference
    ReminderColorChoice(t("Mercan"), Color(0xFFE9786A)),
    ReminderColorChoice(t("Gül"), Color(0xFFDE6D98)),
    ReminderColorChoice(t("Yakut"), Color(0xFFA83B5D)),
    ReminderColorChoice(t("Fuşya"), Color(0xFFC85AB4)),
    ReminderColorChoice(t("Lavanta"), Color(0xFFD9B8F3)),     // Purple — exact reference
    ReminderColorChoice(t("Menekşe"), Color(0xFF8666D6)),
    ReminderColorChoice(t("Erik"), Color(0xFF70436F)),
    // Collection reference colors: append only, because saved choices use list indices.
    ReminderColorChoice(t("Koleksiyon Lavanta"), Color(0xFFDAB7F3)),
    ReminderColorChoice(t("Koleksiyon Lime"), Color(0xFFE0F37D)),
    ReminderColorChoice(t("Koleksiyon Mavi"), Color(0xFF4658C8)),
    ReminderColorChoice(t("Koleksiyon Turuncu"), Color(0xFFEE5E36)),
    // Vivid companions (from the analytics references), each shown next to the colour it echoes. Index 24 + (k - 2)
    // is the companion of colour k; append only, since saved choices use list indices.
    ReminderColorChoice(t("Elektrik Mavisi"), Color(0xFF2F5BEA)),
    ReminderColorChoice(t("Lacivert"), Color(0xFF1B2A6B)),
    ReminderColorChoice("Periwinkle", Color(0xFF8FA8E6)),
    ReminderColorChoice(t("Adaçayı Koyu"), Color(0xFF3E6E62)),
    ReminderColorChoice("Aqua", Color(0xFF2ED3A2)),
    ReminderColorChoice(t("Adaçayı"), Color(0xFF8FB5A2)),
    ReminderColorChoice(t("Yaprak"), Color(0xFF6BB300)),
    ReminderColorChoice(t("Neon Sarı"), Color(0xFFE8F24A)),
    ReminderColorChoice(t("Zeytin"), Color(0xFFB6B12E)),
    ReminderColorChoice(t("Bakır"), Color(0xFFA0603C)),
    ReminderColorChoice(t("Yanık Turuncu"), Color(0xFFD9622B)),
    ReminderColorChoice(t("Somon"), Color(0xFFE6806D)),
    ReminderColorChoice(t("Şeker Pembe"), Color(0xFFE58BC6)),
    ReminderColorChoice(t("Şarap"), Color(0xFFA3123F)),
    ReminderColorChoice("Magenta", Color(0xFFE0136C)),
    ReminderColorChoice(t("Leylak"), Color(0xFFC9A6E8)),
    ReminderColorChoice(t("Mor"), Color(0xFF8D6BE0)),
    ReminderColorChoice(t("Mürdüm"), Color(0xFF5A2440))
)

/**
 * Card text colour chosen automatically, as for categories: dark on light card colours, white on dark ones. Cards
 * draw their colour at 76% over the dark card surface, so that blend is what is measured. No colour: white text.
 */
/**
 * The colours as pickers show them, REAL-style: the two signature neutrals, then round the colour wheel —
 * yellow, orange, pink-red, purple, blue, teal, green — each colour followed by its vivid companion. Values are indices into
 * [ReminderColorChoices] (cards store the index, so the order changes nothing saved). The four "Koleksiyon"
 * entries duplicate other colours and are not offered; cards that already use them keep showing them.
 */
val ReminderColorDisplayOrder = listOf(0, 1, 9, 31, 10, 32, 11, 33, 13, 35, 12, 34, 14, 36, 16, 38, 15, 37, 17, 39, 18, 40, 19, 41, 4, 26, 2, 24, 3, 25, 5, 27, 6, 28, 7, 29, 8, 30)

fun autoCardDarkText(accentStart: Int?, accentEnd: Int?, customSolidColorArgb: Int?): Boolean {
    val colors = if (customSolidColorArgb != null) listOf(Color(customSolidColorArgb))
        else listOfNotNull(accentStart?.let { ReminderColorChoices.getOrNull(it)?.color },
            accentEnd?.let { ReminderColorChoices.getOrNull(it)?.color })
    if (colors.isEmpty()) return false
    val surface = Color(0xFF151517)
    val light = colors.map { androidx.compose.ui.graphics.lerp(surface, it, 0.76f).luminance().toDouble() }.average()
    return light > 0.35
}

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
    useDarkText: Boolean = false,
    onUseDarkTextChange: ((Boolean) -> Unit)? = null,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    modifier: Modifier = Modifier
) {
    val proAction = com.cagri.real.ui.subscription.rememberProAction(com.cagri.real.access.RealFeature.CardColors)
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
            Text(t("Kart renkleri"), color = Color(0xFFF0F0F4), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            ColorChoiceRow(t("Ana renk"), startIndex, onSelected = { value -> proAction { onStartChange(value) } })
            ColorChoiceRow(t("Geçiş rengi"), endIndex, onSelected = { value -> proAction { onEndChange(value) } })
            // Star ring is always white now, so there is no ring colour to pick.
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
                        Text(t("Geçiş"), color = Color(0xFFB7B7C0), fontSize = 11.sp, modifier = Modifier.width(96.dp))
                        ContinuousGradientTransitionBar(
                            fraction = fraction,
                            onFractionChange = { value -> proAction { onGradientBlendFractionChange(value) } },
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
                                        onClick = { proAction { onUseTransitionColor() } }
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
                                    text = t("Geçiş rengini kullan"),
                                    color = Color(0xFFC8C8D2),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            } else {
                val previewBorderColor = if (isStarred) StarCardBorderColor else Color.White.copy(alpha = 0.14f)
                val previewBorderWidth = if (isStarred) 1.25.dp else 0.7.dp
                Box(
                    Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp))
                        .background(previewBrush)
                        .border(previewBorderWidth, previewBorderColor, RoundedCornerShape(9.dp))
                )
            }

            if (onUseDarkTextChange != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onUseDarkTextChange(!useDarkText) }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                if (useDarkText) Color(0xFF2C5696).copy(alpha = 0.85f)
                                else Color.White.copy(alpha = 0.08f),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                if (useDarkText) Color(0xFF91B9FF) else Color.White.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (useDarkText) {
                            ActionIcon(ActionGlyph.Check, Modifier.size(12.dp), Color.White)
                        }
                    }
                    Text(
                        text = t("Koyu renk metin kullan (Siyah)"),
                        color = if (useDarkText) Color(0xFFF5F5F8) else Color(0xFFB7B7C0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
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
                            .semantics { contentDescription = t("{0}: Renk yok (Varsayılan)", label) },
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
            items(ReminderColorDisplayOrder.size, key = { ReminderColorDisplayOrder[it] }) { position ->
                val index = ReminderColorDisplayOrder[position]
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

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ContinuousGradientTransitionBar(
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
            .semantics { contentDescription = t("Geçiş çubuğu") },
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
