package com.cagri.real.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.BeverageType
import com.cagri.real.data.MoodTrackerEntry
import com.cagri.real.data.ReminderItem
import com.cagri.real.ui.components.water.rememberWaterTrackerState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Widget page showcase cards. Read-only views over data REAL already stores (mood entries, reminders,
 * water entries); they never write anything. Visual language: dark glass card, thin rim, colored glow
 * on the bottom edge, smooth line / glowing bar that draws itself in.
 */

private val CardTop = Color(0xFF0D1119)
private val CardBottom = Color(0xFF05070B)
private val SoftText = Color(0xFFB4B9C4)

// ---------------------------------------------------------------------------------------------
// Shared card shell + drawing helpers
// ---------------------------------------------------------------------------------------------

@Composable
internal fun WidgetGlowCard(
    glow: Color,
    reduceAnimations: Boolean,
    enterDelayMillis: Int,
    modifier: Modifier = Modifier,
    /** Verilirse alt parıltı soldan sağa glow -> glowEnd renk geçişi olur (ör. mavi -> turuncu). */
    glowEnd: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    val enter = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    LaunchedEffect(reduceAnimations) {
        if (reduceAnimations) enter.snapTo(1f)
        else enter.animateTo(1f, tween(520, delayMillis = enterDelayMillis, easing = FastOutSlowInEasing))
    }
    Box(
        modifier
            .animateContentSize()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 22.dp.toPx()
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
            .border(0.8.dp, Color.White.copy(alpha = 0.10f), shape)
    ) {
        if (glowEnd == null) Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        0.90f to glow.copy(alpha = 0.30f),
                        1f to glow.copy(alpha = 0.85f)
                    )
                )
            }
        ) else Box(
            Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawBehind {
                drawRect(Brush.horizontalGradient(listOf(glow, glowEnd)))
                // Aynı alt parıltı yoğunluğu: renk geçişini dikey maskeyle kes.
                drawRect(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        0.90f to Color.White.copy(alpha = 0.30f),
                        1f to Color.White.copy(alpha = 0.85f)
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), content = content)
    }
}

@Composable
private fun CardHeader(title: String, chip: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = SoftText, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            chip,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/** Horizontal-tangent smoothing: no overshoot, so the line never dips below a lower neighbour. */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val mx = (a.x + b.x) / 2f
        path.cubicTo(mx, a.y, mx, b.y, b.x, b.y)
    }
    return path
}

private fun DrawScope.drawGrowingLine(path: Path, progress: Float, brush: Brush, widthPx: Float, glow: Color) {
    val measure = PathMeasure()
    measure.setPath(path, false)
    val length = measure.length
    if (length <= 0f) return
    val partial = Path()
    measure.getSegment(0f, length * progress.coerceIn(0f, 1f), partial, true)
    drawPath(partial, glow.copy(alpha = 0.16f), style = Stroke(widthPx * 4f, cap = StrokeCap.Round))
    drawPath(partial, glow.copy(alpha = 0.28f), style = Stroke(widthPx * 2f, cap = StrokeCap.Round))
    drawPath(partial, brush, style = Stroke(widthPx, cap = StrokeCap.Round))
}

@Composable
private fun rememberDrawIn(key: Any, reduceAnimations: Boolean, delayMillis: Int = 250, durationMillis: Int = 1100): Float {
    val anim = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    LaunchedEffect(key, reduceAnimations) {
        if (reduceAnimations) anim.snapTo(1f)
        else {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(durationMillis, delayMillis = delayMillis, easing = FastOutSlowInEasing))
        }
    }
    return anim.value
}

// ---------------------------------------------------------------------------------------------
// 1) Mood: line chart per Gün / Hafta / Ay (Kötü .. Çok iyi)
// ---------------------------------------------------------------------------------------------

internal enum class MoodRange(val chip: String) { Day("Gün"), Week("Hafta"), Month("Ay") }

internal data class MoodDayPoint(val day: Int, val level: Float, val tint: Color)

/** One plotted point. [x] is 0..1 across the chart; [detail] is the specific mood label (Gün view). */
internal data class MoodPlotPoint(val x: Float, val level: Float, val tint: Color, val label: String, val detail: String)

private fun moodTintAt(level: Float): Color {
    val clamped = level.coerceIn(0f, 4f)
    val left = clamped.toInt().coerceIn(0, 4)
    val right = (left + 1).coerceAtMost(4)
    return androidx.compose.ui.graphics.lerp(MoodFamiliesV2[left].tint, MoodFamiliesV2[right].tint, clamped - left)
}

internal fun moodMonthPoints(entries: List<MoodTrackerEntry>, month: YearMonth, zone: ZoneId): List<MoodDayPoint> =
    entries.mapNotNull { entry ->
        val date = Instant.ofEpochMilli(entry.details.loggedAt).atZone(zone).toLocalDate()
        if (YearMonth.from(date) == month) date.dayOfMonth to entry.mood.familyIndexV2() else null
    }.groupBy({ it.first }, { it.second })
        .map { (day, levels) ->
            val level = levels.average().toFloat()
            MoodDayPoint(day, level, moodTintAt(level))
        }
        .sortedBy { it.day }

private val WeekDayShort = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

/**
 * Ay / Hafta: one point per day (average of that day's records).
 * Gün: every single record logged today, placed at its time of day.
 */
internal fun moodPlotPoints(entries: List<MoodTrackerEntry>, range: MoodRange, now: java.time.ZonedDateTime): List<MoodPlotPoint> {
    val zone = now.zone
    val today = now.toLocalDate()
    fun dateOf(e: MoodTrackerEntry) = Instant.ofEpochMilli(e.details.loggedAt).atZone(zone).toLocalDate()
    return when (range) {
        MoodRange.Month -> {
            val month = YearMonth.from(today)
            val span = (month.lengthOfMonth() - 1).coerceAtLeast(1)
            moodMonthPoints(entries, month, zone).map {
                MoodPlotPoint((it.day - 1).toFloat() / span, it.level, it.tint, it.day.toString(), "")
            }
        }
        MoodRange.Week -> {
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            entries.mapNotNull { e ->
                val d = dateOf(e)
                val idx = java.time.temporal.ChronoUnit.DAYS.between(start, d).toInt()
                if (idx in 0..6) idx to e.mood.familyIndexV2() else null
            }.groupBy({ it.first }, { it.second }).map { (idx, levels) ->
                val level = levels.average().toFloat()
                MoodPlotPoint(idx / 6f, level, moodTintAt(level), WeekDayShort[idx], "")
            }.sortedBy { it.x }
        }
        MoodRange.Day -> entries.filter { dateOf(it) == today }
            .sortedBy { it.details.loggedAt }
            .map { e ->
                val t = Instant.ofEpochMilli(e.details.loggedAt).atZone(zone)
                val minutes = t.hour * 60 + t.minute
                val level = e.mood.familyIndexV2().toFloat()
                MoodPlotPoint(
                    minutes / 1439f, level, moodV2Tint(e.mood),
                    String.format(Locale.ROOT, "%02d:%02d", t.hour, t.minute), e.mood.label
                )
            }
    }
}

@Composable
private fun MoodRangeChips(selected: MoodRange, onSelect: (MoodRange) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(3.dp)
    ) {
        MoodRange.entries.reversed().forEach { r ->
            val on = r == selected
            Text(
                r.chip,
                color = if (on) Color(0xFF10131A) else Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Color.White.copy(alpha = 0.92f) else Color.Transparent)
                    .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onSelect(r) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun MoodMonthChartCard(
    entries: List<MoodTrackerEntry>,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    enterDelayMillis: Int = 0
) {
    var range by rememberSaveable { mutableStateOf(MoodRange.Month) }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { java.time.ZonedDateTime.now(zone) }
    val points = remember(entries, range, today) { moodPlotPoints(entries, range, today) }
    val monthName = remember {
        YearMonth.from(today).month.getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("tr"))
    }
    val average = if (points.isEmpty()) null else points.map { it.level }.average().toFloat()
    val progress = rememberDrawIn(listOf(range, points.size), reduceAnimations)
    val title = when (range) {
        MoodRange.Month -> "Mood · $monthName"
        MoodRange.Week -> "Mood · Bu hafta"
        MoodRange.Day -> "Mood · Bugün"
    }

    WidgetGlowCard(Color(0xFF3B82F6), reduceAnimations, enterDelayMillis, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = SoftText, fontSize = 15.sp, modifier = Modifier.weight(1f))
            MoodRangeChips(range) { range = it }
        }
        Spacer(Modifier.height(8.dp))

        val labelGutter = 46.dp
        BoxWithConstraints(Modifier.fillMaxWidth().height(170.dp)) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val left = with(density) { labelGutter.toPx() }
            val right = with(density) { 10.dp.toPx() }
            val topPad = with(density) { 22.dp.toPx() }
            val bottomPad = with(density) { 14.dp.toPx() }
            fun xOf(fraction: Float) = left + fraction * (wPx - left - right)
            fun yOf(level: Float) = topPad + (1f - level / 4f) * (hPx - topPad - bottomPad)
            val offsets = points.map { Offset(xOf(it.x), yOf(it.level)) }
            val latestIndex = points.lastIndex

            // State read inside the Canvas only: the pulse redraws the chart, it does not recompose the card.
            val pulseState: State<Float>? = if (reduceAnimations) null else {
                rememberInfiniteTransition(label = "moodPulse").animateFloat(
                    0f, 1f,
                    infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
                    label = "moodPulseValue"
                )
            }

            Canvas(Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 7.dp.toPx()))
                listOf(4f, 2f, 0f).forEach { level ->
                    val y = yOf(level)
                    drawLine(
                        Color.White.copy(alpha = 0.10f), Offset(left, y), Offset(wPx - right, y),
                        strokeWidth = 1.dp.toPx(), pathEffect = dash
                    )
                }
                if (offsets.isEmpty()) return@Canvas

                offsets.forEachIndexed { i, o ->
                    if (progress >= points[i].x) drawLine(
                        Brush.verticalGradient(
                            listOf(points[i].tint.copy(alpha = 0.45f), Color.Transparent),
                            startY = o.y, endY = hPx - bottomPad
                        ),
                        Offset(o.x, o.y), Offset(o.x, hPx - bottomPad), strokeWidth = 1.2.dp.toPx()
                    )
                }

                if (offsets.size >= 2) {
                    val colors = points.map { it.tint }
                    val span = (offsets.last().x - offsets.first().x).coerceAtLeast(1f)
                    // line progress is measured across the plotted span, not the whole axis
                    val lineProgress = ((progress * (wPx - left - right) + left - offsets.first().x) / span).coerceIn(0f, 1f)
                    drawGrowingLine(
                        smoothPath(offsets), lineProgress,
                        Brush.horizontalGradient(colors, startX = offsets.first().x, endX = offsets.last().x),
                        3.dp.toPx(), Color.White
                    )
                }

                offsets.forEachIndexed { i, o ->
                    if (progress < points[i].x) return@forEachIndexed
                    val isLatest = i == latestIndex
                    if (isLatest && !reduceAnimations) {
                        val pulse = pulseState?.value ?: 0f
                        drawCircle(points[i].tint.copy(alpha = 0.35f * (1f - pulse)), 7.dp.toPx() + 12.dp.toPx() * pulse, o)
                    }
                    drawCircle(points[i].tint.copy(alpha = 0.28f), if (isLatest) 11.dp.toPx() else 8.dp.toPx(), o)
                    drawCircle(Color.White, if (isLatest) 5.5.dp.toPx() else 4.dp.toPx(), o)
                    drawCircle(points[i].tint, if (isLatest) 3.2.dp.toPx() else 2.2.dp.toPx(), o)
                }
            }

            Column(
                Modifier.fillMaxSize().padding(top = 12.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Çok iyi", "Normal", "Kötü").forEach {
                    Text(it, color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, modifier = Modifier.width(labelGutter))
                }
            }

            if (points.isNotEmpty() && progress >= 1f) {
                val last = points.last()
                val o = offsets.last()
                val bubbleW = with(density) { 74.dp.toPx() }
                val bubbleH = with(density) { 30.dp.toPx() }
                val bx = (o.x - bubbleW / 2f).coerceIn(left, wPx - bubbleW - 2f)
                val by = (o.y - bubbleH - with(density) { 14.dp.toPx() }).coerceAtLeast(0f)
                Text(
                    MoodFamiliesV2[last.level.roundToInt().coerceIn(0, 4)].title,
                    color = Color(0xFF10131A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset { IntOffset(bx.roundToInt(), by.roundToInt()) }
                        .width(74.dp)
                        .clip(RoundedCornerShape(50))
                        .background(last.tint)
                        .padding(vertical = 6.dp)
                )
            }
        }

        // x-axis labels
        when (range) {
            MoodRange.Week -> Row(Modifier.fillMaxWidth().padding(start = labelGutter, end = 10.dp)) {
                WeekDayShort.forEach {
                    Text(it, color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            MoodRange.Day -> Row(
                Modifier.fillMaxWidth().padding(start = labelGutter, end = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("00", "06", "12", "18", "24").forEach {
                    Text(it, color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp)
                }
            }
            MoodRange.Month -> Unit
        }

        Spacer(Modifier.height(6.dp))
        Text(
            when (range) { MoodRange.Day -> "Gün ortalaması"; MoodRange.Week -> "Hafta ortalaması"; MoodRange.Month -> "Ay ortalaması" },
            color = SoftText, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Text(
            text = average?.let { MoodFamiliesV2[it.roundToInt().coerceIn(0, 4)].title } ?: "—",
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = when {
                points.isEmpty() -> when (range) {
                    MoodRange.Day -> "Bugün henüz mood kaydı yok"
                    MoodRange.Week -> "Bu hafta henüz mood kaydı yok"
                    MoodRange.Month -> "Bu ay henüz mood kaydı yok"
                }
                range == MoodRange.Day -> "${points.size} kayıt"
                else -> "${points.size} gün kayıtlı"
            },
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = if (range == MoodRange.Day && points.isNotEmpty()) 12.dp else 16.dp),
            textAlign = TextAlign.Center
        )

        // Gün view: every record added during the day, newest first.
        if (range == MoodRange.Day && points.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                points.asReversed().forEach { p ->
                    val shape = RoundedCornerShape(18.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.09f), shape)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.tint))
                        Spacer(Modifier.width(12.dp))
                        Text(p.detail, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(p.label, color = SoftText, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2) Weekly progress: glowing gradient bar (reference: "Project Progress")
// ---------------------------------------------------------------------------------------------

internal data class WeekProgress(
    val done: Int, val total: Int,
    val importantDone: Int, val importantTotal: Int,
    val plannedDone: Int, val plannedTotal: Int
) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** This week's (Mon-Sun) reminders: due this week, or completed this week. Mood/journal cards are not tasks. */
internal fun weekProgress(reminders: List<ReminderItem>, today: LocalDate, zone: ZoneId): WeekProgress {
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = start.plusDays(6)
    fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun inWeek(d: LocalDate?) = d != null && !d.isBefore(start) && !d.isAfter(end)

    val relevant = reminders.filter { r ->
        if (r.mood != null || r.journal != null) return@filter false
        val due = r.plannedDateEpochDay?.let { LocalDate.ofEpochDay(it) } ?: r.alertAt?.let { dayOf(it) }
        inWeek(due) || inWeek(r.completedAt?.let { dayOf(it) })
    }
    val important = relevant.filter { it.isImportant }
    val planned = relevant.filter { it.plannedDateEpochDay != null || it.alertAt != null }
    return WeekProgress(
        relevant.count { it.isCompleted }, relevant.size,
        important.count { it.isCompleted }, important.size,
        planned.count { it.isCompleted }, planned.size
    )
}

@Composable
fun WeekProgressCard(
    reminders: List<ReminderItem>,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    enterDelayMillis: Int = 0
) {
    val zone = remember { ZoneId.systemDefault() }
    val progress = remember(reminders) { weekProgress(reminders, LocalDate.now(zone), zone) }
    val fill = remember { Animatable(if (reduceAnimations) progress.fraction else 0f) }
    LaunchedEffect(progress.fraction, reduceAnimations) {
        if (reduceAnimations) fill.snapTo(progress.fraction)
        else fill.animateTo(progress.fraction, tween(1200, delayMillis = 300, easing = FastOutSlowInEasing))
    }
    val aqua = Color(0xFF62F5DC)
    val percent = (fill.value * 100f).roundToInt()

    WidgetGlowCard(aqua.copy(alpha = 0.55f), reduceAnimations, enterDelayMillis, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Haftalık ilerleme", color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text(
                "Bu hafta",
                color = SoftText,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$percent", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Normal)
            Text("%", color = Color.White.copy(alpha = 0.45f), fontSize = 52.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(10.dp))

        GlowProgressBar(
            fraction = fill.value,
            label = if (progress.total == 0) "Plan yok" else "${progress.done}/${progress.total}",
            height = 56.dp
        )

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Important", progress.importantDone, progress.importantTotal, Modifier.weight(1f))
            MiniStat("Planned", progress.plannedDone, progress.plannedTotal, Modifier.weight(1f))
        }
    }
}

/** Glowing progress bar with its label right-aligned; supports custom or category accentColor. */
@Composable
internal fun GlowProgressBar(
    fraction: Float,
    label: String,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val barAccent = accentColor ?: Color(0xFF62F5DC)
    val barStart = remember(barAccent) {
        if (accentColor == null) Color(0xFFC4FF6A) else lerp(barAccent, Color.White, 0.28f)
    }
    val isAccentLight = remember(barAccent) {
        (0.2126f * barAccent.red + 0.7152f * barAccent.green + 0.0722f * barAccent.blue) > 0.45f
    }
    val onFillEnd = fraction > 0.72f
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            drawRoundRect(Color.White.copy(alpha = 0.10f), cornerRadius = CornerRadius(h / 2f))
            if (fraction > 0f) {
                val w = max(size.width * fraction.coerceIn(0f, 1f), h * 0.7f)
                for (i in 1..5) {
                    val g = i * 4.5f
                    drawRoundRect(
                        barAccent.copy(alpha = 0.05f),
                        topLeft = Offset(-g * 0.4f, -g),
                        size = Size(w + g * 0.8f, h + g * 2f),
                        cornerRadius = CornerRadius(h / 2f + g)
                    )
                }
                drawRoundRect(
                    Brush.horizontalGradient(listOf(barStart, barAccent), startX = 0f, endX = w),
                    size = Size(w, h), cornerRadius = CornerRadius(h / 2f)
                )
            }
        }
        val textFillColor = if (isAccentLight) Color(0xFF0E1014) else Color.White
        Text(
            label,
            color = if (onFillEnd) textFillColor else Color.White.copy(alpha = 0.88f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp)
        )
    }
}

@Composable
private fun MiniStat(label: String, done: Int, total: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.8.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label, color = SoftText, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$done", color = Color.White, fontSize = 24.sp)
            Text(" / $total", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 3) Drinks week: glowing line over the last 7 days of water vs. goal (reference: blue glow cards)
// ---------------------------------------------------------------------------------------------

internal fun weekWaterRatios(
    entries: List<com.cagri.real.data.WaterIntakeEntry>,
    goalMl: Int,
    today: LocalDate,
    zone: ZoneId
): List<Float> {
    val goal = goalMl.coerceAtLeast(1).toFloat()
    val byDay = entries.filter { it.beverage == BeverageType.WATER }
        .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
        .mapValues { (_, list) -> list.sumOf { it.amountMl.toLong() }.toFloat() }
    return (6 downTo 0).map { back -> (byDay[today.minusDays(back.toLong())] ?: 0f) / goal }
}

private val ShortDays = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

@Composable
fun DrinksWeekCard(
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    enterDelayMillis: Int = 0
) {
    val water = rememberWaterTrackerState()
    val zone = water.zone
    val today = water.day
    val ratios = remember(water.snapshot, today, zone) {
        weekWaterRatios(water.snapshot.entries, water.snapshot.goalMl, today, zone)
    }
    val average = ratios.map { min(it, 1f) }.average().toFloat()
    val progress = rememberDrawIn(ratios, reduceAnimations, delayMillis = 350)
    val blue = Color(0xFF2E8BFF)
    val percent = (average * 100f * progress).roundToInt()

    WidgetGlowCard(blue, reduceAnimations, enterDelayMillis, modifier) {
        CardHeader("Su hedefi", "7 gün")
        Spacer(Modifier.height(6.dp))

        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = ratios.size
            val padX = 14.dp.toPx()
            val top = 14.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val offsets = ratios.mapIndexed { i, r ->
                val x = padX + i.toFloat() / (n - 1) * (size.width - padX * 2f)
                val y = top + (1f - min(r, 1.15f) / 1.15f) * (bottom - top)
                Offset(x, y)
            }
            // goal line
            val goalY = top + (1f - 1f / 1.15f) * (bottom - top)
            drawLine(
                Color.White.copy(alpha = 0.12f), Offset(padX, goalY), Offset(size.width - padX, goalY),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 7.dp.toPx()))
            )
            offsets.forEachIndexed { i, o ->
                val shown = progress >= i.toFloat() / (n - 1)
                if (shown) drawLine(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), startY = o.y, endY = bottom),
                    Offset(o.x, o.y), Offset(o.x, bottom), strokeWidth = 1.2.dp.toPx()
                )
            }
            drawGrowingLine(
                smoothPath(offsets), progress,
                Brush.horizontalGradient(
                    listOf(Color.White.copy(alpha = 0.25f), Color.White),
                    startX = offsets.first().x, endX = offsets.last().x
                ),
                3.dp.toPx(), blue
            )
            offsets.forEachIndexed { i, o ->
                if (progress >= i.toFloat() / (n - 1)) {
                    drawCircle(Color.White.copy(alpha = 0.22f), 9.dp.toPx(), o)
                    drawCircle(Color(0xFFE2E5EA), 5.dp.toPx(), o)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                val d = today.minusDays((6 - i).toLong()).dayOfWeek
                Text(
                    ShortDays[d.value - 1],
                    color = if (i == 6) Color.White else Color.White.copy(alpha = 0.42f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Ortalama tamamlanma", color = SoftText, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("$percent%", color = Color.White, fontSize = 48.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(
            "Son 7 gün · günlük ${water.snapshot.goalMl} ml hedefe göre",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            textAlign = TextAlign.Center
        )
    }
}
