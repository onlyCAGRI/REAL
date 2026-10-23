package com.cagri.real.ui.components

import com.cagri.real.i18n.I18n
import com.cagri.real.i18n.t
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Mono = FontFamily.Monospace
private val TR: Locale get() = I18n.locale
private const val HISTORY_PAGE_SIZE = 5
private const val DRAW_MS = 500

/** Twenty calendar days, latest five initially visible; existing water records only. */
@Composable
internal fun YasamWaterHistory(water: YasamWater?, reduceAnimations: Boolean) {
    val points = water?.history.orEmpty()
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        if (points.isEmpty()) {
            Text(t("Su verisi yükleniyor"), color = YasamMuted, fontSize = 13.sp, fontFamily = YasamThin)
        } else {
            if (points.any { it.goalMl == null }) Text(t("Eski hedef kaydı yoksa güncel hedef kullanılır"),
                color = YasamMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds()) {
                val scroll = rememberScrollState(initial = Int.MAX_VALUE)
                val chartWidth = maxWidth * (points.size.coerceAtLeast(HISTORY_PAGE_SIZE).toFloat() / HISTORY_PAGE_SIZE)
                Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                    Box(Modifier.width(chartWidth)) {
                        YasamWaterChart(points, water?.goalMl ?: 2000, reduceAnimations)
                    }
                }
            }
        }
    }
}

@Composable
private fun YasamWaterChart(points: List<YasamWaterDay>, goalMl: Int, reduceAnimations: Boolean) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM", TR) }
    val labels = remember(points) {
        points.map { e ->
            Triple(
                e.day.format(dateFormat).uppercase(TR),
                "${e.amountMl} ML",
                YasamWaterTone
            )
        }
    }
    val scores = remember(points, goalMl) { points.map { (it.amountMl.toFloat() / (it.goalMl ?: goalMl).coerceAtLeast(1)).coerceIn(0f, 1f) } }
    val summary = remember(points) {
        t("Son {0} gün su kaydı: ", points.size) + points.joinToString(", ") {
            t("{0}: {1} ml, hedef {2}", it.day, it.amountMl, it.goalMl?.let { goal -> t("{0} ml", goal) } ?: t("kaydı yok"))
        }
    }
    val progress = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    LaunchedEffect(reduceAnimations) {
        if (reduceAnimations) progress.snapTo(1f)
        else progress.animateTo(1f, tween(DRAW_MS, easing = LinearEasing))
    }
    val n = points.size
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().height(60.dp)
                .semantics { contentDescription = summary }
                .drawWithCache {
                    // Yol ve ölçüm yalnız boyut/veri değişince kurulur; kare başına yalnız kesit çizilir.
                    val padY = 8.dp.toPx()
                    val cell = size.width / n
                    val xs = List(n) { cell * (it + 0.5f) }
                    val ys = scores.map { padY + (1f - it) * (size.height - 2 * padY) }
                    val path = Path().apply {
                        if (n > 1) {
                            moveTo(xs[0], ys[0])
                            for (i in 1 until n) {
                                val mid = (xs[i - 1] + xs[i]) / 2f
                                cubicTo(mid, ys[i - 1], mid, ys[i], xs[i], ys[i])
                            }
                        }
                    }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val length = measure.length
                    val segment = Path()
                    val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val base = Color.White.copy(alpha = 0.10f)
                    onDrawBehind {
                        val p = progress.value
                        // Reference line represents 100% of each date’s own saved goal.
                        drawLine(base, Offset(0f, padY), Offset(size.width, padY), 1.dp.toPx())
                        if (n > 1 && length > 0f) {
                            segment.reset()
                            measure.getSegment(0f, length * p, segment, true)
                            drawPath(segment, Color.White.copy(alpha = 0.55f), style = stroke)
                        }
                        for (i in 0 until n) {
                            // Nokta, çizgi ona ulaşınca belirir.
                            val reach = if (n > 1) p * (n - 1) - i else p * 2f - 1f
                            val a = (reach + 0.25f).coerceIn(0f, 1f)
                            if (a > 0f) drawCircle(labels[i].third.copy(alpha = a), 3.5.dp.toPx(), Offset(xs[i], ys[i]))
                        }
                    }
                }
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics { },
            horizontalArrangement = Arrangement.Start
        ) {
            labels.forEach { (date, name, _) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        date, color = YasamMuted, fontSize = 10.sp, fontFamily = Mono, letterSpacing = 0.6.sp,
                        maxLines = 1, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        name, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontFamily = Mono,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
