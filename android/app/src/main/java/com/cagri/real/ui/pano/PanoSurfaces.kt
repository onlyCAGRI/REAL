package com.cagri.real.ui.pano

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.liquidChrome

/** Pano-local visual language: solid dark boxes with a hairline rim (Apple-style grouped boxes). */
internal val PanoAccent = Color(0xFF62F5DC)
internal val PanoMuted = Color(0xFF9A9AA2)
internal val PanoDanger = Color(0xFFFF6B6B)

internal fun Modifier.panoSurface(corners: Dp = 18.dp, strong: Boolean = false): Modifier {
    val shape = RoundedCornerShape(corners)
    // Solid, not glass: nothing from the board background shows through a box.
    return clip(shape)
        .background(if (strong) Color(0xFF2C2C2E) else Color(0xFF1C1C1E))
        .border(0.8.dp, Color.White.copy(alpha = 0.09f), shape)
}

/** Replaces loose text on the board: everything readable sits inside a rounded box. */
@Composable
internal fun PanoInfoBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().panoSurface(16.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

/** Round glass button used by the floating top controls (pencil / done). */
@Composable
internal fun PanoGlassButton(glyph: ActionGlyph, description: String, tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).liquidChrome(corners = 20.dp).semantics { contentDescription = description }
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        ActionIcon(glyph, Modifier.size(18.dp), color = tint)
    }
}

private val avatarTones = listOf(
    Color(0xFF5E7CE2) to Color(0xFF3B3F9E), Color(0xFF3FBF9F) to Color(0xFF1F6F6B),
    Color(0xFFE2905E) to Color(0xFF9E4B3B), Color(0xFFB45EE2) to Color(0xFF6A3B9E),
    Color(0xFFE25E8F) to Color(0xFF9E3B5B), Color(0xFF7FA3B8) to Color(0xFF3F5566))

internal fun panoAvatarBrush(name: String): Brush {
    val tone = avatarTones[(name.hashCode() and Int.MAX_VALUE) % avatarTones.size]
    return Brush.linearGradient(listOf(tone.first, tone.second))
}

@Composable
internal fun PanoAvatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(panoAvatarBrush(name)), contentAlignment = Alignment.Center) {
        Text(name.trim().take(2).uppercase(), color = Color.White, fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Small rounded text action used inside blocks instead of bare text buttons. */
@Composable
internal fun PanoPill(label: String, onClick: () -> Unit) {
    Box(Modifier.panoSurface(14.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Text-block palette. Keys are what gets stored in the block payload. */
internal val PanoTextColors = listOf(
    "white" to Color(0xFFFFFFFF), "mint" to Color(0xFF62F5DC), "yellow" to Color(0xFFFFE066),
    "orange" to Color(0xFFFF9F6B), "pink" to Color(0xFFFF7EB6), "blue" to Color(0xFF7FB2FF), "purple" to Color(0xFFB79CFF))

internal fun panoTextColor(key: String): Color = PanoTextColors.firstOrNull { it.first == key }?.second ?: Color.White

internal fun panoTextAlign(key: String): androidx.compose.ui.text.style.TextAlign = when (key) {
    "center" -> androidx.compose.ui.text.style.TextAlign.Center
    "right" -> androidx.compose.ui.text.style.TextAlign.End
    else -> androidx.compose.ui.text.style.TextAlign.Start
}

@Composable
internal fun PanoColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(color)
            .border(if (selected) 2.dp else 0.dp, if (selected) Color.White else Color.Transparent, CircleShape))
    }
}

/** Three short lines aligned left / center / right. */
@Composable
internal fun PanoAlignButton(kind: String, selected: Boolean, onClick: () -> Unit) {
    val ink = if (selected) Color.White else Color.White.copy(alpha = 0.45f)
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
        .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
            val h = size.height
            val widths = listOf(1f, 0.6f, 0.8f)
            widths.forEachIndexed { i, w ->
                val len = size.width * w
                val x0 = when (kind) { "center" -> (size.width - len) / 2f; "right" -> size.width - len; else -> 0f }
                val y = h * (0.2f + 0.3f * i)
                drawLine(ink, androidx.compose.ui.geometry.Offset(x0, y), androidx.compose.ui.geometry.Offset(x0 + len, y),
                    strokeWidth = 1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}

/** Lets a text block expose its style actions so the controls can live outside the box, with the other block controls. */
internal class PanoTextStyleActions(
    val color: String, val align: String, val title: Boolean,
    val setColor: (String) -> Unit, val setAlign: (String) -> Unit, val toggleTitle: () -> Unit)

internal class PanoTextStyleHost {
    var actions by androidx.compose.runtime.mutableStateOf<PanoTextStyleActions?>(null)
}

internal val LocalPanoTextStyleHost = androidx.compose.runtime.staticCompositionLocalOf<PanoTextStyleHost?> { null }
