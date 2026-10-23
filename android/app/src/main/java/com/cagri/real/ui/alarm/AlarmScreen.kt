package com.cagri.real.ui.alarm

import com.cagri.real.i18n.t
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

private val AlarmInk = Color(0xFF08080A)
private val TR: Locale = Locale.forLanguageTag("tr-TR")

/**
 * The ringing alarm: a breathing glow in the card's colour, a dot grid with waves running out from it,
 * the time in hollow dot-matrix digits and a glass panel (title, snooze, slide to stop) at the bottom.
 * Sliding calms the screen: the glow fades and the waves settle as the knob travels.
 */
@Composable
fun AlarmScreen(
    alarm: AlarmInfo,
    snoozeMinutes: Int,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    onSlide: (Float) -> Unit = {}
) {
    val accent = Color(alarm.color)
    val slide = remember { Animatable(0f) }
    // Every knob position, dragged or springing back, goes out (the sound fades with it).
    LaunchedEffect(slide) { snapshotFlow { slide.value }.collect { onSlide(it) } }
    // Leaving the page mid-slide must not leave a still-ringing alarm turned down.
    DisposableEffect(Unit) { onDispose { onSlide(0f) } }
    val pulse = rememberInfiniteTransition(label = "alarm")
    val breath by pulse.animateFloat(0.82f, 1.08f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val wave by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "wave")
    val zoned = remember(alarm.at) { Instant.ofEpochMilli(alarm.at).atZone(ZoneId.systemDefault()) }
    val time = remember(zoned) { zoned.format(DateTimeFormatter.ofPattern("HH:mm", TR)) }
    val day = remember(zoned) { zoned.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", TR)) }

    Box(
        Modifier.fillMaxSize().background(AlarmInk).drawBehind {
            val calm = 1f - slide.value
            val center = Offset(size.width / 2f, size.height * 0.38f)
            val r = 220.dp.toPx() * breath * (0.6f + 0.4f * calm)
            drawCircle(
                Brush.radialGradient(
                    0f to accent.copy(alpha = calm), 0.4f to accent.copy(alpha = 0.55f * calm), 1f to Color.Transparent,
                    center = center, radius = r
                ),
                radius = r, center = center
            )
            drawDotWaves(center, wave, calm)
        }
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionIcon(ActionGlyph.Bell, Modifier.size(13.dp), Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.size(6.dp))
                Text("ALARM", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, letterSpacing = 2.sp)
            }
            Spacer(Modifier.weight(0.55f))
            DotMatrixTime(time, Modifier.semantics { contentDescription = t("Alarm {0}", time) })
            Spacer(Modifier.height(14.dp))
            Text(day.replaceFirstChar { it.titlecase(TR) }, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            AlarmPanel(alarm, accent, snoozeMinutes, slide, onStop, onSnooze)
        }
    }
}

/** A grid of faint dots; crests of a wave travel outward from the glow and light them up. */
private fun DrawScope.drawDotWaves(center: Offset, wave: Float, calm: Float) {
    val step = 14.dp.toPx()
    val dot = 1.1.dp.toPx()
    val ring = 26.dp.toPx()
    val reach = 380.dp.toPx()
    val phase = wave * 2f * PI.toFloat()
    var y = step / 2f
    while (y < size.height) {
        var x = step / 2f
        while (x < size.width) {
            val d = hypot(x - center.x, y - center.y)
            val crest = max(0f, sin(d / ring - phase)).pow(6) * calm * max(0f, 1f - d / reach)
            drawCircle(Color.White.copy(alpha = 0.07f + crest * 0.55f), dot * (1f + crest * 0.8f), Offset(x, y))
            x += step
        }
        y += step
    }
}

/** 5×7 hollow-dot digits, as on the reference screens. */
private val DIGITS = mapOf(
    '0' to "01110100011001110101110011000101110", '1' to "00100011000010000100001000010001110",
    '2' to "01110100010000100010001000100011111", '3' to "11110000010000101110000010000111110",
    '4' to "00010001100101010010111110001000010", '5' to "11111100001111000001000011000101110",
    '6' to "00110010001000011110100011000101110", '7' to "11111000010001000100010000100001000",
    '8' to "01110100011000101110100011000101110", '9' to "01110100011000101111000010001001100"
)

@Composable
private fun DotMatrixTime(time: String, modifier: Modifier = Modifier) {
    val cell = 11.dp
    val glyphs = time.map { if (it == ':') 1 else 5 }
    val gaps = (time.length - 1) * 1.2f
    val widthCells = glyphs.sum() + gaps
    Canvas(modifier.size(cell * widthCells, cell * 7)) {
        val c = cell.toPx()
        val stroke = Stroke(width = 2.dp.toPx())
        val r = c * 0.34f
        var ox = 0f
        time.forEach { ch ->
            if (ch == ':') {
                listOf(2, 4).forEach { row -> drawCircle(Color.White, r, Offset(ox + c / 2f, row * c + c / 2f), style = stroke) }
                ox += c * 2.2f
            } else {
                val bits = DIGITS[ch] ?: return@forEach
                for (row in 0 until 7) for (col in 0 until 5) {
                    if (bits[row * 5 + col] == '1') drawCircle(Color.White, r, Offset(ox + col * c + c / 2f, row * c + c / 2f), style = stroke)
                }
                ox += c * 6.2f
            }
        }
    }
}

@Composable
private fun AlarmPanel(
    alarm: AlarmInfo,
    accent: Color,
    snoozeMinutes: Int,
    slide: Animatable<Float, AnimationVector1D>,
    onStop: () -> Unit,
    onSnooze: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(34.dp))
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), Color(0xF2191623))))
            .padding(start = 20.dp, end = 16.dp, top = 22.dp, bottom = 16.dp)
    ) {
        if (alarm.subtitle.isNotBlank()) Text(alarm.subtitle, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
        Text(alarm.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.size(62.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f))
                    .clickable(onClick = onSnooze)
                    .semantics { contentDescription = t("{0} dakika ertele", snoozeMinutes) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(t("Ertele"), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                Text(t("{0} dk", snoozeMinutes), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            SlideToStop(Modifier.weight(1f), slide, onStop)
        }
    }
}

@Composable
private fun SlideToStop(modifier: Modifier, slide: Animatable<Float, AnimationVector1D>, onStop: () -> Unit) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier.height(62.dp).clip(RoundedCornerShape(31.dp)).background(Color.Black.copy(alpha = 0.28f))
            .semantics { contentDescription = t("Kaydırarak alarmı kapat") }
    ) {
        val density = LocalDensity.current
        val knob = 52.dp
        val travel = with(density) { (maxWidth - knob - 10.dp).toPx() }.coerceAtLeast(1f)
        Text(
            t("Kaydır, kapat"), color = Color.White.copy(alpha = 0.75f * (1f - slide.value)), fontSize = 14.sp,
            modifier = Modifier.align(Alignment.Center).padding(start = 44.dp)
        )
        Box(
            Modifier.offset { IntOffset((5.dp.toPx() + slide.value * travel).roundToInt(), 5.dp.roundToPx()) }
                .size(knob).clip(CircleShape).background(Color.White)
                .pointerInput(travel) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (slide.value > 0.85f) { slide.animateTo(1f, tween(120)); onStop() }
                                else slide.animateTo(0f, tween(220))
                            }
                        },
                        onDragCancel = { scope.launch { slide.animateTo(0f, tween(220)) } }
                    ) { change, dx ->
                        change.consume()
                        scope.launch { slide.snapTo((slide.value + dx / travel).coerceIn(0f, 1f)) }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            ActionIcon(ActionGlyph.ChevronRight, Modifier.size(20.dp), AlarmInk)
        }
    }
}
