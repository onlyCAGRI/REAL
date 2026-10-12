package com.cagri.real.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.ReminderItem
import com.cagri.real.ui.components.water.rememberWaterTrackerState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Widget page: four designs from the reference sheets, all fed by data REAL already stores.
 *
 *   1) Active / Done Tasks   -> reminders (open / completed). Rows can be ticked when the parent passes
 *                               onToggleReminder; without it they are read-only.
 *   2) Stacked "Bugün" card  -> today's reminders (done/total, pending, overdue).
 *   3) Sparkline + lime card -> completions per day (7/14/30 days, tap to switch) + this week's progress.
 *   4) Ring pills            -> today's task completion and today's water vs. the water goal.
 *
 * Nothing here writes data except the optional reminder toggle callback. No fake numbers: with no data
 * every widget shows zeros / an empty state.
 */

private val RefRim = Color.White.copy(alpha = 0.08f)
private val RefSoft = Color(0xFFB4B9C4)
private val RefPanel = Color(0xFF0B0F15)
private val RefRow = Color(0xFF0D1117)
private val RefPurple = Color(0xFFA855F7)
private val RefPurpleLight = Color(0xFFE9A8FF)
private val RefMint = Color(0xFF2EE59D)
private val RefMintLight = Color(0xFFA6FFD6)

// ---------------------------------------------------------------------------------------------
// Animation + formatting helpers
// ---------------------------------------------------------------------------------------------

@Composable
private fun rememberRefEnter(reduce: Boolean, delayMs: Int, durationMs: Int = 560): State<Float> {
    val anim = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) {
        if (reduce) anim.snapTo(1f)
        else anim.animateTo(1f, tween(durationMillis = durationMs, delayMillis = delayMs, easing = FastOutSlowInEasing))
    }
    return anim.asState()
}

/** Counts up from the previous value (0 on first show) to [target]. */
@Composable
private fun rememberRefCount(target: Float, reduce: Boolean, delayMs: Int = 200, durationMs: Int = 1000): Float {
    val anim = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target, reduce) {
        if (reduce) anim.snapTo(target)
        else anim.animateTo(target, tween(durationMillis = durationMs, delayMillis = delayMs, easing = FastOutSlowInEasing))
    }
    return anim.value
}

/** 0 -> 1 every time [key] changes (used to redraw a chart from the start). */
@Composable
private fun rememberRefDrawIn(key: Any, reduce: Boolean, delayMs: Int = 300, durationMs: Int = 1200): Float {
    val anim = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(key, reduce) {
        if (reduce) anim.snapTo(1f)
        else {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(durationMillis = durationMs, delayMillis = delayMs, easing = FastOutSlowInEasing))
        }
    }
    return anim.value
}

private fun Modifier.refRiseIn(progress: State<Float>, rise: Dp = 22.dp): Modifier = graphicsLayer {
    val v = progress.value
    alpha = v
    translationY = (1f - v) * rise.toPx()
}

private fun refNumber(n: Int): String = String.format(Locale.getDefault(), "%,d", n)

@Composable
private fun rememberRefPulse(reduce: Boolean, low: Float, durationMillis: Int, label: String): Float {
    if (reduce) return 0.9f
    val transition = rememberInfiniteTransition(label = label)
    val pulse by transition.animateFloat(
        low, 1f, infiniteRepeatable(tween(durationMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "$label-pulse"
    )
    return pulse
}

@Composable
private fun rememberRefClockAngle(reduce: Boolean): Float {
    if (reduce) return 0f
    val transition = rememberInfiniteTransition(label = "task-clock")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "task-clock-angle"
    )
    return angle
}

@Composable
private fun rememberRefClock(): State<WidgetReferenceClock> {
    val owner = LocalLifecycleOwner.current
    return produceState(initialValue = widgetReferenceClock(), key1 = owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = widgetReferenceClock()
                delay(30_000L)
            }
        }
    }
}


// ---------------------------------------------------------------------------------------------
// Glyphs (drawn, no assets)
// ---------------------------------------------------------------------------------------------

@Composable
private fun RefClockGlyph(modifier: Modifier, face: Color, hands: Color, minuteAngle: Float) = Canvas(modifier) {
    val r = size.minDimension / 2f
    drawCircle(face, r)
    val sw = r * 0.17f
    fun hand(angleDeg: Float, len: Float) {
        val a = Math.toRadians((angleDeg - 90f).toDouble())
        drawLine(
            hands, center,
            Offset(center.x + (cos(a) * r * len).toFloat(), center.y + (sin(a) * r * len).toFloat()),
            sw, StrokeCap.Round
        )
    }
    hand(minuteAngle, 0.62f)
    hand(minuteAngle / 12f + 120f, 0.42f)
}

@Composable
private fun RefCheckGlyph(modifier: Modifier, color: Color) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val sw = w * 0.12f
    drawLine(color, Offset(w * 0.2f, h * 0.54f), Offset(w * 0.42f, h * 0.76f), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.42f, h * 0.76f), Offset(w * 0.8f, h * 0.28f), sw, StrokeCap.Round)
}

@Composable
private fun RefChevronGlyph(modifier: Modifier, color: Color, down: Boolean) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val sw = w * 0.11f
    if (down) {
        drawLine(color, Offset(w * 0.22f, h * 0.38f), Offset(w * 0.5f, h * 0.64f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.64f), Offset(w * 0.78f, h * 0.38f), sw, StrokeCap.Round)
    } else {
        drawLine(color, Offset(w * 0.38f, h * 0.22f), Offset(w * 0.64f, h * 0.5f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.64f, h * 0.5f), Offset(w * 0.38f, h * 0.78f), sw, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------------------------
// Data derivation (pure, from existing reminders)
// ---------------------------------------------------------------------------------------------

// ---------------------------------------------------------------------------------------------
// 1) Active / Done Tasks
// ---------------------------------------------------------------------------------------------

@Composable
fun ActiveTasksWidget(
    reminders: List<ReminderItem>,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    enterDelayMillis: Int = 0,
    onToggleReminder: ((Long) -> Unit)? = null
) {
    val clock by rememberRefClock()
    val tasks = remember(reminders, clock.zone) { refActiveTasks(reminders, clock.zone) }
    RefTaskGlowCard(
        label = "Aktif görevler", tasks = tasks, completedStyle = false,
        accent = RefPurple, accentLight = RefPurpleLight, emptyText = "Açık görev yok",
        reduce = reduceAnimations, delayMs = enterDelayMillis, modifier = modifier, onToggle = onToggleReminder
    )
}

@Composable
fun DoneTasksWidget(
    reminders: List<ReminderItem>,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    enterDelayMillis: Int = 0,
    onToggleReminder: ((Long) -> Unit)? = null
) {
    val tasks = remember(reminders) { refDoneTasks(reminders) }
    RefTaskGlowCard(
        label = "Tamamlanan görevler", tasks = tasks, completedStyle = true,
        accent = RefMint, accentLight = RefMintLight, emptyText = "Henüz tamamlanan yok",
        reduce = reduceAnimations, delayMs = enterDelayMillis, modifier = modifier, onToggle = onToggleReminder
    )
}

@Composable
private fun RefTaskGlowCard(
    label: String,
    tasks: List<ReminderItem>,
    completedStyle: Boolean,
    accent: Color,
    accentLight: Color,
    emptyText: String,
    reduce: Boolean,
    delayMs: Int,
    modifier: Modifier,
    onToggle: ((Long) -> Unit)?
) {
    val shape = RoundedCornerShape(38.dp)
    val panelShape = RoundedCornerShape(28.dp)
    val enter = rememberRefEnter(reduce, delayMs)
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    val limit = if (expanded) 8 else 3
    val shown = tasks.take(limit)
    val count = rememberRefCount(tasks.size.toFloat(), reduce, delayMs + 150).roundToInt()

    val breathe = rememberRefPulse(reduce, 0.72f, 2600, "task-glow")
    val minuteAngle = rememberRefClockAngle(reduce)

    Column(
        modifier
            .fillMaxWidth()
            .refRiseIn(enter)
            .animateContentSize(animationSpec = if (reduce) snap() else spring())
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF121722), Color(0xFF0B0F16))))
            .border(0.8.dp, RefRim, shape)
            .padding(14.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(panelShape)
                .background(RefPanel)
                .border(0.8.dp, RefRim, panelShape)
                .clickable(role = Role.Button) { expanded = !expanded }
                .drawBehind {
                    // Glowing dome rising from the bottom edge.
                    val cx = size.width / 2f
                    val cy = size.height
                    val radius = size.width * 0.62f
                    withTransform({ scale(1f, 0.42f, Offset(cx, cy)) }) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentLight.copy(alpha = 0.95f * breathe),
                                    accent.copy(alpha = 0.72f * breathe),
                                    accent.copy(alpha = 0f)
                                ),
                                center = Offset(cx, cy), radius = radius
                            ),
                            radius = radius, center = Offset(cx, cy)
                        )
                    }
                }
        ) {
            Text(
                refNumber(count), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 16.dp)
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp).size(52.dp)
                    .graphicsLayer {
                        val s = 0.97f + 0.03f * breathe
                        scaleX = s; scaleY = s
                    }
                    .clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center
            ) {
                if (completedStyle) RefCheckGlyph(Modifier.size(24.dp), Color.White)
                else RefClockGlyph(
                    Modifier.size(24.dp), face = Color.White, hands = accent,
                    minuteAngle = if (reduce) 0f else minuteAngle
                )
            }
            Text(
                label, color = RefSoft, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 16.dp)
            )
        }

        if (tasks.isEmpty()) {
            Text(
                emptyText, color = RefSoft.copy(alpha = 0.7f), fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
                textDecoration = null
            )
        } else {
            shown.forEachIndexed { index, reminder ->
                key(reminder.id) {
                RefTaskRow(
                    reminder = reminder, index = index, accent = accent, completedStyle = completedStyle,
                    reduce = reduce, baseDelay = delayMs,
                    onClick = onToggle?.let { toggle -> { toggle(reminder.id) } }
                )
                }
            }
            if (expanded && tasks.size > 8) {
                Text("İlk 8 görev gösteriliyor · ${tasks.size - 8} diğer", color = RefSoft,
                    fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
            if (tasks.size > 3) {
                Text(
                    if (expanded) "Daha az göster" else "${minOf(tasks.size - 3, 5)} görev daha göster",
                    color = RefSoft, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RefTaskRow(
    reminder: ReminderItem,
    index: Int,
    accent: Color,
    completedStyle: Boolean,
    reduce: Boolean,
    baseDelay: Int,
    onClick: (() -> Unit)?
) {
    val rowShape = RoundedCornerShape(20.dp)
    val enter = rememberRefEnter(reduce, baseDelay + 140 + index * 70, 480)
    // Completion is always authoritative parent data, never a local fake completion state.
    val checked = reminder.isCompleted
    val fill by animateFloatAsState(if (checked) 1f else 0f,
        animationSpec = if (reduce) snap() else tween(260), label = "task-row-fill")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .refRiseIn(enter, 14.dp)
            .height(58.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
            .clip(rowShape)
            .background(RefRow)
            .border(0.8.dp, RefRim, rowShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (completedStyle && checked) {
                RefCheckGlyph(Modifier.size(24.dp), Color.White)
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.White.copy(alpha = 0.16f), style = Stroke(1.2.dp.toPx()))
                    if (fill > 0f) drawCircle(accent.copy(alpha = fill), radius = size.minDimension / 2f * fill)
                }
                if (fill > 0.5f) RefCheckGlyph(Modifier.size(15.dp), Color.White)
            }
        }
        val struck = !completedStyle && checked
        Text(
            reminder.text, color = if (struck) Color.White.copy(alpha = 0.55f) else Color.White,
            fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textDecoration = if (struck) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
        )
        RefChevronGlyph(Modifier.size(20.dp), Color(0xFF8E94A0), down = completedStyle)
    }
}

// ---------------------------------------------------------------------------------------------
// 2) Stacked card: "Bugün" (reference: bank balance + two stacked cards)
// ---------------------------------------------------------------------------------------------

@Composable
fun TodayStackWidget(
    reminders: List<ReminderItem>,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    enterDelayMillis: Int = 0
) {
    val clock by rememberRefClock()
    val stats = remember(reminders, clock) {
        refTodayStats(refTasks(reminders), clock.today, clock.zone, clock.nowMillis)
    }
    val shape = RoundedCornerShape(38.dp)
    val enter = rememberRefEnter(reduceAnimations, enterDelayMillis)
    val cardsEnter = rememberRefEnter(reduceAnimations, enterDelayMillis + 220, 700)
    var fanned by rememberSaveable { mutableStateOf(false) }
    val spread by animateDpAsState(
        if (fanned) 100.dp else 58.dp,
        animationSpec = if (reduceAnimations) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "stack-spread"
    )
    val done = rememberRefCount(stats.done.toFloat(), reduceAnimations, enterDelayMillis + 200).roundToInt()
    val chipText = when {
        stats.total == 0 -> "Boş"
        stats.pending > 0 -> "Aktif"
        else -> "Tamam"
    }
    val green = Color(0xFF4ADE80)

    Box(
        modifier
            .fillMaxWidth()
            .height(356.dp)
            .refRiseIn(enter)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2B2B2F), Color(0xFF161618), Color(0xFF0E0E10))))
            .border(0.9.dp, Color.White.copy(alpha = 0.13f), shape)
            .clickable(role = Role.Button) { fanned = !fanned }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 26.dp, end = 22.dp, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bugün", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text(
                chipText, color = green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(green.copy(alpha = 0.14f))
                    .border(0.8.dp, green.copy(alpha = 0.32f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            )
        }
        Column(Modifier.padding(start = 26.dp, top = 74.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$done", color = Color.White, fontSize = 62.sp, fontWeight = FontWeight.Normal)
                Text("/${stats.total}", color = Color(0xFF7C7C82), fontSize = 62.sp, fontWeight = FontWeight.Normal)
            }
            Text("bugün tamamlanan", color = RefSoft, fontSize = 14.sp)
        }

        // Blue card sits behind; the violet one in front. Tap the widget to fan them apart.
        RefStackCard(
            label = "Bekleyen", value = stats.pending,
            gradient = listOf(Color(0xFF4F46E5), Color(0xFF6366F1)), textColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(14.dp + spread))
                .graphicsLayer {
                    val p = cardsEnter.value
                    alpha = p
                    translationY = (1f - p) * 90.dp.toPx()
                }
        )
        RefStackCard(
            label = "Gecikmiş", value = stats.overdue,
            gradient = listOf(Color(0xFFA78BFA), Color(0xFFC4B5FD)), textColor = Color(0xFF4C1D95),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-14).dp)
                .graphicsLayer {
                    val p = cardsEnter.value
                    alpha = p
                    translationY = (1f - p) * 130.dp.toPx()
                }
        )
    }
}

@Composable
private fun RefStackCard(
    label: String, value: Int, gradient: List<Color>, textColor: Color, modifier: Modifier
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .height(92.dp)
            .clip(shape)
            .background(Brush.verticalGradient(gradient))
            .border(0.9.dp, Color.White.copy(alpha = 0.28f), shape)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(top = 20.dp))
        Text(refNumber(value), color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// 3) Sparkline card with the lime card behind (reference: sales + 45%)
// ---------------------------------------------------------------------------------------------

private val RefRanges = listOf(7, 14, 30)

private fun refSmoothPath(points: List<Offset>, closeToY: Float? = null): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val mx = (a.x + b.x) / 2f
        path.cubicTo(mx, a.y, mx, b.y, b.x, b.y)
    }
    if (closeToY != null) {
        path.lineTo(points.last().x, closeToY)
        path.lineTo(points.first().x, closeToY)
        path.close()
    }
    return path
}

@Composable
fun ProgressSparkWidget(
    reminders: List<ReminderItem>,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    enterDelayMillis: Int = 0
) {
    val clock by rememberRefClock()
    val zone = clock.zone
    val today = clock.today
    var rangeIndex by rememberSaveable { mutableStateOf(1) }
    val days = RefRanges[rangeIndex]
    val tasks = remember(reminders) { refTasks(reminders) }
    val values = remember(tasks, days, today, zone) { refCompletedPerDay(tasks, days, today, zone) }
    val total = values.sum()
    val week = remember(reminders, today, zone) { weekProgress(reminders, today, zone) }

    val enter = rememberRefEnter(reduceAnimations, enterDelayMillis)
    val limeEnter = rememberRefEnter(reduceAnimations, enterDelayMillis + 260, 700)
    val draw = rememberRefDrawIn(values, reduceAnimations, delayMs = enterDelayMillis + 350)
    val totalShown = rememberRefCount(total.toFloat(), reduceAnimations, enterDelayMillis + 200).roundToInt()
    val percentShown = rememberRefCount(week.fraction * 100f, reduceAnimations, enterDelayMillis + 400).roundToInt()
    val pulse = rememberRefPulse(reduceAnimations, 0.35f, 1500, "spark-head")
    val green = Color(0xFF22C55E)
    val blackShape = RoundedCornerShape(44.dp)
    val limeShape = RoundedCornerShape(44.dp)

    Box(modifier.fillMaxWidth().height(376.dp).refRiseIn(enter)) {
        // Lime card: slides out from behind the black one.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .graphicsLayer {
                    val p = limeEnter.value
                    alpha = p
                    translationY = -(1f - p) * 70.dp.toPx()
                }
                .clip(limeShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF8CCB3C), Color(0xFFA5E862))))
        ) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 26.dp, end = 26.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$percentShown%", color = Color(0xFF2A3416), fontSize = 46.sp, modifier = Modifier.weight(1f))
                Text(
                    "Hafta", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF4F7A1F).copy(alpha = 0.55f))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
        // Black card with the sparkline.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(290.dp)
                .clip(blackShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF353A32), Color(0xFF161817), Color.Black)))
                .border(0.9.dp, Color.White.copy(alpha = 0.10f), blackShape)
                .clickable(role = Role.Button) { rangeIndex = (rangeIndex + 1) % RefRanges.size }
        ) {
            Text(
                refNumber(totalShown), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 26.dp, top = 22.dp)
            )
            Text(
                "${days} gün", color = RefSoft, fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd).padding(top = 28.dp, end = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Canvas(
                Modifier.align(Alignment.Center).offset(y = 14.dp).padding(horizontal = 22.dp)
                    .fillMaxWidth().height(116.dp)
            ) {
                val n = values.size
                val maxV = max(1, values.maxOrNull() ?: 1)
                val flat = values.all { it == 0 }
                val top = 10.dp.toPx()
                val bottom = size.height - 6.dp.toPx()
                val pts = values.mapIndexed { i, v ->
                    val x = if (n <= 1) size.width / 2f else i.toFloat() / (n - 1) * size.width
                    val y = if (flat) size.height * 0.72f else top + (1f - v.toFloat() / maxV) * (bottom - top)
                    Offset(x, y)
                }
                val line = refSmoothPath(pts)
                val area = refSmoothPath(pts, closeToY = size.height)
                clipRect(right = size.width * draw) {
                    drawPath(area, Brush.verticalGradient(listOf(green.copy(alpha = 0.22f), Color.Transparent)))
                }
                val measure = PathMeasure()
                measure.setPath(line, false)
                val len = measure.length
                if (len > 0f) {
                    val seg = Path()
                    measure.getSegment(0f, len * draw, seg, true)
                    val sw = 2.4.dp.toPx()
                    drawPath(seg, green.copy(alpha = 0.16f), style = Stroke(sw * 4f, cap = StrokeCap.Round))
                    drawPath(seg, green, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    val position = FloatArray(2)
                    val sampler = android.graphics.PathMeasure(line.asAndroidPath(), false)
                    if (sampler.getPosTan(len * draw, position, null)) {
                        val head = Offset(position[0], position[1])
                        drawCircle(green.copy(alpha = 0.30f * pulse), 11.dp.toPx(), head)
                        drawCircle(Color.White, 3.6.dp.toPx(), head)
                    }
                }
            }
            Text(
                "Son $days günde tamamlanan", color = RefSoft, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 26.dp, bottom = 24.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 4) Ring pills (reference: 67% / $234 "Sales by Day")
// ---------------------------------------------------------------------------------------------

/**
 * Dark glass pill with a progress ring. [fraction] drives the ring (0..1); [value] is counted up from 0
 * and formatted with [format].
 */
@Composable
fun RingStatPill(
    label: String,
    value: Float,
    format: (Float) -> String,
    fraction: Float,
    color: Color,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    enterDelayMillis: Int = 0
) {
    val shape = RoundedCornerShape(60.dp)
    val enter = rememberRefEnter(reduceAnimations, enterDelayMillis)
    val ring = remember { Animatable(if (reduceAnimations) fraction.coerceIn(0f, 1f) else 0f) }
    LaunchedEffect(fraction, reduceAnimations) {
        val target = fraction.coerceIn(0f, 1f)
        if (reduceAnimations) ring.snapTo(target)
        else ring.animateTo(target, tween(durationMillis = 1300, delayMillis = enterDelayMillis + 250, easing = FastOutSlowInEasing))
    }
    val shown = rememberRefCount(value, reduceAnimations, enterDelayMillis + 250, 1300)
    val glow = rememberRefPulse(reduceAnimations, 0.55f, 2200, "ring-glow")

    Row(
        modifier
            .fillMaxWidth()
            .height(124.dp)
            .refRiseIn(enter, 18.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF3B3B3E), Color(0xFF1D1D1F))))
            .border(0.9.dp, Color.White.copy(alpha = 0.13f), shape)
            .drawBehind {
                // Soft tint spilling out of the ring's lower right, like the reference.
                val c = Offset(size.height * 0.62f, size.height * 0.78f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = 0.34f * glow), color.copy(alpha = 0f)),
                        center = c, radius = size.height * 0.75f
                    ),
                    radius = size.height * 0.75f, center = c
                )
            }
            .padding(start = 22.dp, end = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(88.dp)) {
            val sw = 10.dp.toPx()
            val d = size.minDimension - sw * 1.8f
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arc = Size(d, d)
            drawArc(
                color = color.copy(alpha = 0.22f), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = tl, size = arc, style = Stroke(sw)
            )
            val sweep = 360f * ring.value
            if (sweep > 0.5f) {
                drawArc(
                    color = color.copy(alpha = 0.22f * glow), startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    topLeft = tl, size = arc, style = Stroke(sw * 1.6f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = color, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    topLeft = tl, size = arc, style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(format(shown), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = RefSoft, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Section used by the widget page
// ---------------------------------------------------------------------------------------------

/** All four reference designs, in the order they appear on the widget page. */
@Composable
fun WidgetReferenceSection(
    reminders: List<ReminderItem>,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    onToggleReminder: ((Long) -> Unit)? = null
) {
    val clock by rememberRefClock()
    val stats = remember(reminders, clock) {
        refTodayStats(refTasks(reminders), clock.today, clock.zone, clock.nowMillis)
    }
    val water = rememberWaterTrackerState()
    val goal = water.snapshot.goalMl.coerceAtLeast(1)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActiveTasksWidget(reminders, reduceAnimations, Modifier, 0, onToggleReminder)
        DoneTasksWidget(reminders, reduceAnimations, Modifier, 90, onToggleReminder)
        TodayStackWidget(reminders, reduceAnimations, Modifier, 150)
        ProgressSparkWidget(reminders, reduceAnimations, Modifier, 210)
        RingStatPill(
            label = if (stats.total == 0) "Bugün plan yok" else "Bugün tamamlanan",
            value = stats.fraction * 100f, format = { "${it.roundToInt()}%" },
            fraction = stats.fraction, color = Color(0xFF22F08E),
            reduceAnimations = reduceAnimations, enterDelayMillis = 270
        )
        RingStatPill(
            label = when {
                water.error != null -> "Su verisi şu an okunamadı"
                !water.ready -> "Su verisi yükleniyor"
                else -> "Bugün su · hedef $goal ml"
            },
            value = water.todayMl.toFloat(), format = { "${refNumber(it.roundToInt())} ml" },
            fraction = water.todayMl.toFloat() / goal, color = Color(0xFFEA6BE3),
            reduceAnimations = reduceAnimations, enterDelayMillis = 330
        )
    }
}
