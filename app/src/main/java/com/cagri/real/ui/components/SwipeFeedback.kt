package com.cagri.real.ui.components

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs

internal val SwipeSave = Color(0xFF3BE3A3)
internal val SwipePass = Color(0xFFDCE3F0)
internal val SwipeNext = Color(0xFF4D8DFF)
internal val SwipeRemove = Color(0xFFFF4D6D)

/**
 * While a poster is dragged, light rises from the screen edge it is heading to — the whole height of the screen,
 * a soft wide glow plus a bright thin line on the very edge (right: save / next, left: pass / remove, top: open,
 * music only). It grows with the drag and fades back on release. [offset] is read while drawing (no recomposition).
 */
@Composable
internal fun BoxWithConstraintsScope.SwipeEdgeGlow(offset: () -> Offset, threshold: Float, listMode: Boolean, upColor: Color? = null) {
    // In a saved list: right removes, left sends the poster to the back.
    val right = if (listMode) SwipeRemove else SwipeSave
    val left = if (listMode) SwipeNext else SwipePass
    val view = androidx.compose.ui.platform.LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Drawn in screen coordinates from inside the deck, so it covers the full screen, edge to edge.
    Box(Modifier.matchParentSize().zIndex(-1f)
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind {
            translate(-origin.x, -origin.y) {
                drawEdgeGlow(offset(), threshold, right, left, upColor, Size(view.width.toFloat(), view.height.toFloat()))
            }
        })
}

private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

internal fun DrawScope.drawEdgeGlow(o: Offset, threshold: Float, right: Color, left: Color, up: Color?, area: Size = size) {
    val w = area.width; val h = area.height
    val horizontal = abs(o.x) >= abs(o.y)
    if (horizontal && o.x != 0f) {
        val s = smooth(abs(o.x) / (threshold * 1.6f))
        if (s <= 0.01f) return
        val color = if (o.x > 0f) right else left
        val edgeX = if (o.x > 0f) w else 0f
        // Wide, soft light: an ellipse centred on the edge, tall as the screen.
        val center = Offset(edgeX, h * 0.5f)
        val radius = h * (0.55f + 0.2f * s)
        scale(scaleX = 0.42f + 0.18f * s, scaleY = 1f, pivot = center) {
            drawCircle(Brush.radialGradient(
                0f to color.copy(alpha = 0.55f * s), 0.35f to color.copy(alpha = 0.26f * s),
                0.7f to color.copy(alpha = 0.07f * s), 1f to Color.Transparent,
                center = center, radius = radius), radius = radius, center = center)
        }
        // The bright line on the very edge, strongest in the middle.
        val line = 3.dp.toPx()
        drawRect(Brush.verticalGradient(
            0f to Color.Transparent, 0.25f to color.copy(alpha = 0.7f * s), 0.5f to Color.White.copy(alpha = 0.85f * s),
            0.75f to color.copy(alpha = 0.7f * s), 1f to Color.Transparent, startY = 0f, endY = h),
            topLeft = Offset(if (o.x > 0f) w - line else 0f, 0f), size = Size(line, h))
    } else if (up != null && o.y < 0f) {
        val s = smooth(-o.y / (threshold * 1.6f))
        if (s <= 0.01f) return
        val center = Offset(w * 0.5f, 0f)
        val radius = w * (0.75f + 0.25f * s)
        scale(scaleX = 1f, scaleY = 0.5f + 0.2f * s, pivot = center) {
            drawCircle(Brush.radialGradient(
                0f to up.copy(alpha = 0.55f * s), 0.4f to up.copy(alpha = 0.22f * s), 1f to Color.Transparent,
                center = center, radius = radius), radius = radius, center = center)
        }
        val line = 3.dp.toPx()
        drawRect(Brush.horizontalGradient(
            0f to Color.Transparent, 0.3f to up.copy(alpha = 0.7f * s), 0.5f to Color.White.copy(alpha = 0.85f * s),
            0.7f to up.copy(alpha = 0.7f * s), 1f to Color.Transparent, startX = 0f, endX = w), size = Size(w, line))
    }
}

private fun tutorialPrefs(context: Context) = context.getSharedPreferences("real_swipe_tutorial", Context.MODE_PRIVATE)

/**
 * First visit of a deck (Film / Dizi, Oyun, Müzik): a ghost poster shows the swipes with the same edge light.
 * Tapping closes it for now; "Bir daha gösterme" closes it for good (per deck).
 */
@Composable
internal fun BoxWithConstraintsScope.SwipeTutorial(kind: String, rightLabel: String, leftLabel: String, upLabel: String? = null) {
    val context = LocalContext.current
    var visible by remember(kind) { mutableStateOf(!tutorialPrefs(context).getBoolean(kind, false)) }
    if (!visible) return
    val phases = if (upLabel != null) 3 else 2
    val time by rememberInfiniteTransition(label = "swipe-tutorial").animateFloat(0f, phases.toFloat(),
        infiniteRepeatable(tween(phases * 1500, easing = LinearEasing), RepeatMode.Restart), label = "swipe-tutorial-t")
    val phase = time.toInt().coerceAtMost(phases - 1)
    val frac = time - phase
    // Out in the first 55%, hold, back quickly at the end.
    val travel = when {
        frac < 0.55f -> (frac / 0.55f).let { 1f - (1f - it) * (1f - it) }
        frac < 0.8f -> 1f
        else -> 1f - (frac - 0.8f) / 0.2f
    }
    val distance = with(LocalDensity.current) { 120.dp.toPx() }
    val threshold = with(LocalDensity.current) { 85.dp.toPx() }
    val ghost = when (phase) { 0 -> Offset(distance * travel, 0f); 1 -> Offset(-distance * travel, 0f); else -> Offset(0f, -distance * travel) }
    Box(Modifier.requiredWidth(maxWidth + 28.dp).fillMaxHeight().zIndex(50f).background(Color.Black.copy(alpha = .62f))
        .clickable(remember { MutableInteractionSource() }, null) { visible = false }
        .drawBehind { drawEdgeGlow(ghost, threshold * 0.8f, SwipeSave, SwipePass, if (upLabel != null) MusicTone else null) }) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(120.dp, 170.dp).graphicsLayer {
                translationX = ghost.x; translationY = ghost.y; rotationZ = (ghost.x / threshold * 7f).coerceIn(-24f, 24f)
            }.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .10f)).border(1.5.dp, Color.White.copy(alpha = .7f), RoundedCornerShape(16.dp)))
            Spacer(Modifier.height(28.dp))
            val (label, color) = when (phase) { 0 -> rightLabel to SwipeSave; 1 -> leftLabel to SwipePass; else -> (upLabel ?: "") to MusicTone }
            Text(when (phase) { 0 -> "Sağa kaydır"; 1 -> "Sola kaydır"; else -> "Yukarı kaydır" }, color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
            Text(label, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text("Bir daha gösterme", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = .12f)).clickable {
                    tutorialPrefs(context).edit().putBoolean(kind, true).apply(); visible = false
                }.padding(horizontal = 18.dp, vertical = 11.dp))
    }
}
