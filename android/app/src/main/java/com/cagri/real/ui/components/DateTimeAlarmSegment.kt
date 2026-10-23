package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Warm tone of the alarm glow behind the date sheet (same family as the ringing alarm). */
internal val AlarmGlowTone = Color(0xFFFF8A5B)

/** Faint alarm icon in the free end of the Tarih | Saat control: quiet until the thumb is pushed over it. */
private val AlarmSlotInk = Color.White.copy(alpha = 0.34f)

/**
 * Tarih | Saat with a third stop: the Saat thumb pushed (dragged) right stretches over the faint alarm icon at the end
 * and the card becomes an alarm; pushed back, it is Saat again. A tap on the icon does the same.
 * Positions: 0 = Tarih, 1 = Saat, 2 = Alarm. [nudge] plays a short push of the Saat thumb so the move can be found.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun DateTimeAlarmSegment(
    timeTab: Boolean,
    alarm: Boolean,
    onTimeTab: (Boolean) -> Unit,
    onAlarm: (Boolean) -> Unit,
    liquid: Boolean,
    trackFill: Color,
    thumbFill: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    nudge: Boolean = false,
    reduceAnimations: Boolean = false
) {
    val height = 40.dp * LocalDensity.current.fontScale.coerceAtLeast(1f) + 8.dp
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val track = if (liquid) Modifier.liquidChrome(26.dp) else Modifier.clip(RoundedCornerShape(24.dp)).background(trackFill)
    BoxWithConstraints(modifier.fillMaxWidth().height(height).then(track).padding(4.dp)) {
        val slot = 46.dp
        val cell = (maxWidth - slot) / 2
        val cellPx = with(LocalDensity.current) { cell.toPx() }
        val slotPx = with(LocalDensity.current) { slot.toPx() }
        val target = if (!timeTab) 0f else if (alarm) 2f else 1f
        val pos = remember { Animatable(target) }
        var dragging by remember { mutableStateOf(false) }
        var pastAlarm by remember { mutableStateOf(false) }
        val settleSpec = tween<Float>(LiquidGlassTokens.settleMillis)
        LaunchedEffect(target) {
            if (!dragging) { if (reduceAnimations) pos.snapTo(target) else pos.animateTo(target, settleSpec) }
        }
        LaunchedEffect(nudge) {
            if (nudge && !reduceAnimations && target == 1f) {
                delay(450)
                repeat(2) {
                    pos.animateTo(1.42f, tween(280, easing = FastOutSlowInEasing))
                    pos.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
                    delay(180)
                }
            }
        }
        val setTimeTab by rememberUpdatedState(onTimeTab)
        val setAlarm by rememberUpdatedState(onAlarm)
        // Pushed to Tarih the alarm choice is kept; Saat / Alarm decide it.
        fun settle(at: Float) {
            val pick = when { at < 0.5f -> 0; at < 1.5f -> 1; else -> 2 }
            if (pick == 0) setTimeTab(false) else { setTimeTab(true); setAlarm(pick == 2) }
            scope.launch { if (reduceAnimations) pos.snapTo(pick.toFloat()) else pos.animateTo(pick.toFloat(), settleSpec) }
        }
        val live = remember { floatArrayOf(0f) }
        val drag = if (!enabled) Modifier else Modifier.pointerInput(cellPx, slotPx) {
            detectHorizontalDragGestures(
                onDragStart = { dragging = true; live[0] = pos.value },
                onDragEnd = { dragging = false; settle(live[0]) },
                onDragCancel = { dragging = false; settle(live[0]) },
                onHorizontalDrag = { change, dx ->
                    change.consume()
                    val before = live[0]
                    val step = if (before < 1f || (before == 1f && dx < 0)) dx / cellPx else dx / slotPx
                    val now = (before + step).coerceIn(0f, 2f)
                    live[0] = now
                    // A tick when the thumb crosses into (or out of) the alarm stop.
                    if ((before < 1.5f) != (now < 1.5f)) RealHaptics.tick(view)
                    pastAlarm = now >= 1.5f
                    scope.launch { pos.snapTo(now) }
                }
            )
        }
        val p = pos.value
        val stretch = (p - 1f).coerceIn(0f, 1f)
        val thumbX = cell * p.coerceAtMost(1f)
        val thumbW = cell + slot * stretch
        // While held the thumb turns into a clear glass lens over the labels; past the alarm stop it warms up.
        val press by animateFloatAsState(if (dragging) 1f else 0f, tween(if (reduceAnimations) 0 else 200), label = "segmentLens")
        val warm by animateFloatAsState(if (dragging && pastAlarm) 1f else 0f, tween(180), label = "segmentLensWarm")
        val lens = remember { if (Build.VERSION.SDK_INT >= 33) runCatching { SegmentLens() }.getOrNull() else null }
        val innerH = constraints.maxHeight.toFloat()
        val lensRect = {
            val at = pos.value
            val x = cellPx * at.coerceAtMost(1f)
            val w = cellPx + slotPx * (at - 1f).coerceIn(0f, 1f)
            val gx = w * 0.05f * press
            val gy = innerH * 0.1f * press
            Rect(x - gx, -gy, x + w + gx, innerH + gy)
        }
        val labelsLayer = rememberGraphicsLayer()
        val lensOn = lens != null && press > 0.01f
        Box(Modifier.fillMaxSize().then(drag)) {
            val thumb = Modifier.offset { IntOffset(thumbX.roundToPx(), 0) }.width(thumbW).fillMaxHeight()
                .graphicsLayer {
                    // With the lens shader the filled thumb fades out under it; without, it swells and clears instead.
                    alpha = 1f - press * (if (lens != null) 1f else 0.75f)
                    scaleX = 1f + 0.05f * press; scaleY = 1f + 0.1f * press
                }
            Box(if (liquid) thumb.liquidChrome(22.dp, selected = true) else thumb.clip(RoundedCornerShape(22.dp)).background(thumbFill))
            if (lens == null && press > 0.01f) {
                Box(Modifier.offset { IntOffset(thumbX.roundToPx(), 0) }.width(thumbW).fillMaxHeight()
                    .graphicsLayer { scaleX = 1f + 0.05f * press; scaleY = 1f + 0.1f * press; alpha = press }
                    .border(0.8.dp, lerp(Color.White.copy(alpha = 0.5f), AlarmGlowTone.copy(alpha = 0.7f), warm), RoundedCornerShape(22.dp))
                    .background(lerp(Color.White.copy(alpha = 0.03f), AlarmGlowTone.copy(alpha = 0.14f), warm), RoundedCornerShape(22.dp)))
            }
            val labelColor = { on: Boolean -> if (!enabled) Color(0xFF8E8E93) else if (on) Color.White else Color(0xFFB1B1B7) }
            // The labels are recorded once: drawn as they are outside the lens, and refracted inside it.
            Box(Modifier.fillMaxSize().drawWithContent {
                labelsLayer.record { this@drawWithContent.drawContent() }
                if (lensOn) {
                    val r = lensRect()
                    val hole = Path().apply { addRoundRect(RoundRect(r, CornerRadius(r.height / 2f))) }
                    clipPath(hole, ClipOp.Difference) { drawLayer(labelsLayer) }
                } else drawLayer(labelsLayer)
            }) {
            Row(Modifier.fillMaxSize().selectableGroup()) {
                Box(Modifier.width(cell).fillMaxHeight()
                    .selectable(selected = !timeTab, enabled = enabled, role = Role.Tab, onClick = { onTimeTab(false) }),
                    contentAlignment = Alignment.Center) {
                    Text(t("Tarih"), fontSize = 14.sp, maxLines = 1, color = labelColor(!timeTab),
                        fontWeight = if (!timeTab) FontWeight.SemiBold else FontWeight.Normal)
                }
                Box(Modifier.width(cell).fillMaxHeight()
                    .selectable(selected = timeTab && !alarm, enabled = enabled, role = Role.Tab, onClick = {
                        if (timeTab) onAlarm(false)
                        onTimeTab(true)
                    }))
                Box(Modifier.width(slot).fillMaxHeight()
                    .selectable(selected = timeTab && alarm, enabled = enabled, role = Role.Tab, onClick = {
                        val on = !(timeTab && alarm)
                        onTimeTab(true); onAlarm(on)
                        if (on) RealHaptics.tick(view)
                    })
                    .semantics { contentDescription = if (alarm) t("Alarm açık") else t("Alarm") },
                    contentAlignment = Alignment.Center) {
                    // On the Tarih tab a chosen alarm keeps its icon lit so it is not forgotten.
                    val ink = if (alarm && !timeTab) Color.White.copy(alpha = 0.78f) else AlarmSlotInk
                    AlarmClockGlyph(ink, Modifier.size(17.dp).graphicsLayer { alpha = 1f - stretch })
                }
            }
            // Saat ↔ Alarm label rides the thumb, centred on its stretched width.
            Box(Modifier.offset { IntOffset(cell.roundToPx(), 0) }.width(cell + slot * stretch).fillMaxHeight(),
                contentAlignment = Alignment.Center) {
                val saatAlpha = (1f - stretch * 2f).coerceIn(0f, 1f)
                val alarmAlpha = (stretch * 2f - 1f).coerceIn(0f, 1f)
                Text(t("Saat"), fontSize = 14.sp, maxLines = 1, color = labelColor(timeTab),
                    fontWeight = if (timeTab) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.graphicsLayer { alpha = saatAlpha })
                Row(Modifier.graphicsLayer { alpha = alarmAlpha }, verticalAlignment = Alignment.CenterVertically) {
                    AlarmClockGlyph(Color.White, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Alarm"), fontSize = 14.sp, maxLines = 1, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            }
            if (lensOn && Build.VERSION.SDK_INT >= 33) {
                val extra = with(LocalDensity.current) { 10.dp.toPx() }
                // A layer a little larger than the track so the swollen lens is not cut at the edges.
                Box(Modifier.matchParentSize()
                    .layout { measurable, c ->
                        val e = extra.roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(c.maxWidth + 2 * e, c.maxHeight + 2 * e))
                        layout(c.maxWidth, c.maxHeight) { placeable.place(-e, -e) }
                    }
                    .graphicsLayer {
                        renderEffect = (lens as SegmentLens).effect(lensRect().translate(extra, extra), 1f + 0.16f * press, warm)
                    }
                    .drawBehind { translate(extra, extra) { drawLayer(labelsLayer) } })
            }
        }
    }
}

/** The lens: labels magnified in the middle and pulled in at the rim, a faint glass fill and a bright edge. */
@RequiresApi(33)
private class SegmentLens {
    private val shader = RuntimeShader(SEGMENT_LENS_SHADER)

    fun effect(r: Rect, magnify: Float, warm: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("rect", r.left, r.top, r.right, r.bottom)
        shader.setFloatUniform("corner", r.height / 2f)
        shader.setFloatUniform("magnify", magnify)
        shader.setFloatUniform("warm", warm)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

private const val SEGMENT_LENS_SHADER = """
uniform shader content;
uniform float4 rect;
uniform float corner;
uniform float magnify;
uniform float warm;

half4 main(float2 p) {
    float2 c = (rect.xy + rect.zw) * 0.5;
    float2 hs = (rect.zw - rect.xy) * 0.5;
    float2 q = abs(p - c) - (hs - corner);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - corner;
    if (d > 1.0) return half4(0.0);
    // 0 at the rim → 1 a few px inside: the rim samples from further out, like the edge of a lens.
    float t = clamp(-d / 12.0, 0.0, 1.0);
    float s = (1.0 + (1.0 - t) * (1.0 - t) * 0.5) / magnify;
    float4 col = float4(content.eval(c + (p - c) * s));
    // Labels under the glass read brighter.
    col.rgb = mix(col.rgb, float3(col.a), 0.55);
    float3 glass = mix(float3(1.0), float3(1.0, 0.54, 0.36), warm);
    float fill = mix(0.03, 0.14, warm);
    float rim = smoothstep(2.4, 0.0, -d) * mix(0.55, 0.8, warm) + smoothstep(10.0, 0.0, -d) * 0.07;
    float a = clamp(fill + rim, 0.0, 1.0) * smoothstep(1.0, -1.0, d);
    float4 g = float4(glass * a, a);
    return half4(col + g * (1.0 - col.a));
}
"""

/** A small alarm clock: round face, two hands, the two bells on top and little feet. */
@Composable
fun AlarmClockGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val sw = s * 0.09f
        val r = s * 0.33f
        val c = Offset(size.width / 2f, size.height / 2f + s * 0.05f)
        drawCircle(color, r, c, style = Stroke(sw))
        drawLine(color, c, Offset(c.x, c.y - r * 0.55f), sw, StrokeCap.Round)
        drawLine(color, c, Offset(c.x + r * 0.42f, c.y + r * 0.2f), sw, StrokeCap.Round)
        val bell = r * 1.36f
        val box = Size(bell * 2f, bell * 2f)
        val corner = Offset(c.x - bell, c.y - bell)
        drawArc(color, 212f, 42f, false, corner, box, style = Stroke(sw, cap = StrokeCap.Round))
        drawArc(color, 286f, 42f, false, corner, box, style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(color, Offset(c.x - r * 0.6f, c.y + r * 0.82f), Offset(c.x - r * 0.86f, c.y + r * 1.1f), sw, StrokeCap.Round)
        drawLine(color, Offset(c.x + r * 0.6f, c.y + r * 0.82f), Offset(c.x + r * 0.86f, c.y + r * 1.1f), sw, StrokeCap.Round)
    }
}

/** Slow breathing warm light behind the date sheet while Alarm is chosen, like the alarm screen but calm. */
@Composable
fun AlarmBreathGlow(visible: Boolean, reduceAnimations: Boolean, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, tween(if (reduceAnimations) 0 else 520), label = "alarmGlowIn")
    if (shown <= 0.001f) return
    val breath = if (reduceAnimations) null else rememberInfiniteTransition(label = "alarmGlow").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "alarmGlowBreath"
    )
    Box(modifier.drawBehind {
        val b = breath?.value ?: 0.5f
        val center = Offset(size.width / 2f, size.height * 0.42f)
        val radius = maxOf(size.width, size.height) * 0.62f * (0.86f + 0.14f * b)
        drawRect(Brush.radialGradient(
            0f to AlarmGlowTone.copy(alpha = (0.17f + 0.10f * b) * shown),
            0.55f to AlarmGlowTone.copy(alpha = 0.05f * shown),
            1f to Color.Transparent,
            center = center, radius = radius
        ))
    })
}
