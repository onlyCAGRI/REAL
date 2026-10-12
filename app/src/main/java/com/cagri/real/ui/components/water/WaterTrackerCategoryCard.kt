package com.cagri.real.ui.components.water

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cagri.real.data.BeverageType
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Slightly longer than the 220 ms slide-in of the drinks destination, so the preview never freezes while still visible. */
private const val TRACKER_COVER_DELAY_MS = 260L

/** One integration entry point, owning its state and full-screen destination. */
@Composable
fun WaterTrackerCategoryCard(
    modifier: Modifier = Modifier,
    reduceAnimations: Boolean,
    enabled: Boolean,
    globalIndex: Int,
    visibleCategoryCount: Int,
    localYs: FloatArray,
    listState: LazyListState?,
    topInsetPx: Float,
    stackOffsetPx: Float,
    extendUnderNext: Boolean,
    reduceDynamicBlur: Boolean = false,
    onTrackerDismissed: () -> Unit = {},
    /** Home context card deep link: open the full-screen tracker as soon as this card appears. */
    openOnStart: Boolean = false
) {
    val state = rememberWaterTrackerState()
    var trackerMounted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (openOnStart) trackerMounted = true }
    var trackerVisible by remember { mutableStateOf(false) }
    var trackerClosing by remember { mutableStateOf(false) }
    val transitionScope = rememberCoroutineScope()
    LaunchedEffect(trackerMounted, reduceAnimations) {
        if (trackerMounted) {
            if (reduceAnimations) {
                trackerVisible = true
            } else {
                trackerVisible = false
                withFrameNanos { _ -> }
                trackerVisible = true
            }
        }
    }
    fun closeTracker() {
        if (!trackerMounted || trackerClosing) return
        trackerClosing = true
        if (reduceAnimations) {
            trackerVisible = false
            trackerMounted = false
            trackerClosing = false
            onTrackerDismissed()
        } else {
            trackerVisible = false
            transitionScope.launch {
                delay(230L)
                trackerMounted = false
                trackerClosing = false
                onTrackerDismissed()
            }
        }
    }
    var localY by remember { mutableFloatStateOf(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(26.dp)
    val waterPalette = remember { BeverageType.WATER.appearance() }
    // Same spring/wave engine the full-screen glass uses, tuned calmer for a small preview:
    // the fill eases into place with a little liquid overshoot instead of snapping, and the
    // surface keeps a gentle idle ripple rather than sitting dead flat.
    val cardEngine = remember(reduceAnimations) {
        WaterSurfaceEngine(
            initialFill = if (reduceAnimations) state.progressFor(BeverageType.WATER) else 0f,
            smoothWater = true
        )
    }
    var cardFrame by remember { mutableIntStateOf(0) }
    val cardOwner = LocalLifecycleOwner.current
    val cardProgress = state.progressFor(BeverageType.WATER).coerceIn(0f, MAX_VISUAL_OVERFLOW_PROGRESS)
    // The full-screen tracker is a separate window that completely covers this card once its slide-in
    // has finished. Nothing is visible underneath, so the preview stops requesting frames until the
    // slide-out starts again; this keeps the whole home layer from redrawing behind the drinks screen.
    var coveredByTracker by remember { mutableStateOf(false) }
    LaunchedEffect(trackerMounted, trackerVisible, reduceAnimations) {
        if (trackerMounted && trackerVisible) {
            if (!reduceAnimations) delay(TRACKER_COVER_DELAY_MS)
            coveredByTracker = true
        } else {
            coveredByTracker = false
        }
    }
    // The preview only draws (and only needs frames) once there is water in it.
    val cardAnimating = state.todayMl > 0L && !coveredByTracker
    LaunchedEffect(cardEngine, cardProgress, reduceAnimations, cardAnimating) {
        if (cardAnimating && cardProgress > cardEngine.targetFill) cardEngine.disturb(0.5f, 0.7f)
        cardEngine.targetFill = cardProgress
        // While idle the engine is parked at its settled state, so the card looks the same when it returns.
        if (reduceAnimations || !cardAnimating) { cardEngine.step(0f, true); cardFrame++ }
    }
    LaunchedEffect(cardEngine, cardOwner, reduceAnimations, cardAnimating) {
        if (!reduceAnimations && cardAnimating) cardOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    if (previous != 0L) cardEngine.step((now - previous) / 1_000_000_000f, false)
                    previous = now
                    cardFrame++
                }
            }
        }
    }
    Box(modifier.fillMaxWidth()
        .onPlaced { coordinates ->
            localY = coordinates.positionInParent().y
            cardHeightPx = coordinates.size.height.toFloat()
            if (globalIndex < localYs.size) localYs[globalIndex] = localY
        }
        .graphicsLayer {
            alpha = if (enabled) 1f else 0.58f
            translationY = 0f
            if (listState != null && cardHeightPx > 0f && localYs.size == visibleCategoryCount) {
                val container = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "categories" }
                val lastY = localYs.lastOrNull() ?: 0f
                if (container != null && lastY > 0f) {
                    val naturalY = container.offset + localY
                    val pinnedY = topInsetPx + globalIndex * stackOffsetPx
                    val maxY = container.offset + lastY - (visibleCategoryCount - 1 - globalIndex) * stackOffsetPx
                    translationY = minOf(maxOf(naturalY, pinnedY), maxY) - naturalY
                }
            }
        }
        .clip(shape)
        .background(Brush.verticalGradient(waterPalette.background))
        .border(0.9.dp, waterPalette.edge.copy(alpha = 0.30f), shape)
        .clickable(enabled = enabled, role = Role.Button) {
            trackerClosing = false
            trackerMounted = true
        }
    ) {
        if (state.todayMl > 0L) {
            Canvas(Modifier.matchParentSize()) {
                @Suppress("UNUSED_VARIABLE") val redraw = cardFrame // read in draw phase to drive invalidation
                val progress = cardEngine.fill.coerceIn(0f, 1f)
                val time = cardEngine.time
                val energy = cardEngine.energy
                // Empty water has no decorative wave. At 100% the surface reaches the Drinks title zone.
                val y = size.height * (0.90f - progress * 0.72f)
                // Idle sine drift plus a short-lived ripple burst whenever new water lands,
                // so the surface reads as liquid settling rather than a fixed decal.
                val wave1 = 10.dp.toPx() + sin(time * 1.3f) * 3.dp.toPx() + sin(time * 6.4f) * energy * 12.dp.toPx()
                val wave2 = 9.dp.toPx() + sin(time * 1.7f + 1.4f) * 3.dp.toPx() - sin(time * 6.4f + 1.1f) * energy * 9.dp.toPx()
                val wave3 = 3.dp.toPx() + sin(time * 1.1f + 2.1f) * 2.dp.toPx() + sin(time * 6.4f + 2f) * energy * 6.dp.toPx()
                val path = Path().apply {
                    moveTo(0f, y)
                    cubicTo(
                        size.width * 0.34f, y - wave1,
                        size.width * 0.70f, y + wave2,
                        size.width, y - wave3
                    )
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, waterPalette.liquid[1].copy(alpha = 0.34f))
                val edge = Path().apply {
                    moveTo(0f, y)
                    cubicTo(
                        size.width * 0.34f, y - wave1,
                        size.width * 0.70f, y + wave2,
                        size.width, y - wave3
                    )
                }
                drawPath(edge, waterPalette.edge.copy(alpha = 0.22f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                // A couple of small bubbles drift up right after a pour, echoing the full-screen glass.
                if (energy > 0.03f) {
                    for (index in 0..3) {
                        val travel = (time * (0.22f + index * 0.05f) + index * 0.27f) % 1f
                        val bx = ((index * 0.23f + sin(time + index) * 0.05f) % 1f) * size.width
                        val by = size.height - travel * (size.height - y + 14f * density)
                        drawCircle(
                            waterPalette.bubble.copy(alpha = (energy * 0.4f).coerceAtMost(0.5f)),
                            radius = (1.2f + index % 3) * density,
                            center = Offset(bx, by),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(0.6f * density)
                        )
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(min = 112.dp)
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 46.dp + if (extendUnderNext) 40.dp else 0.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Drinks", color = Color.White, fontSize = 28.sp, lineHeight = 34.sp,
                    modifier = Modifier.weight(1f).offset(x = 10.dp))
                Text("${state.todayEntries.size} kayıt", color = Color(0xFFB8BEC0), fontSize = 14.sp)
            }
            Spacer(Modifier.height(13.dp))
            val waterBand = waterLevelBand(state.progressFor(BeverageType.WATER))
            val waterStatus = remember(waterBand) { randomWaterStatus(waterBand) }
            Text(when {
                state.error != null -> "Kayıtları görüntüle"
                !state.ready -> "Yükleniyor…"
                else -> waterStatus
            }, color = Color(0xFFD2DFDF), fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF0D2F3B).copy(alpha = 0.64f))
                    .padding(horizontal = 12.dp, vertical = 5.dp))
        }
    }
    if (trackerMounted) {
        WaterTrackerScreen(
            state = state,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            visible = trackerVisible,
            onDismiss = { closeTracker() }
        )
    }
}


private fun waterLevelBand(progress: Float): Int {
    val percent = (progress.coerceAtLeast(0f) * 100f).toInt()
    return when {
        percent < 25 -> 0
        percent < 50 -> 1
        percent < 75 -> 2
        percent < 100 -> 3
        percent < 125 -> 4
        percent < 150 -> 5
        percent < 200 -> 6
        else -> 7
    }
}

private fun randomWaterStatus(band: Int): String {
    val options = when (band) {
        0 -> listOf(
            "Su seni bekliyor",
            "Daha yeni başlıyoruz",
            "İlk bardak için yer var",
            "Bugün su tarafı sessiz"
        )
        1 -> listOf(
            "Ritim başladı",
            "Yarıya doğru gidiyor",
            "Su tarafı hareketlendi",
            "Biraz daha devam"
        )
        2 -> listOf(
            "İyi gidiyor",
            "Yarıyı geçtin",
            "Su ritmi yerinde",
            "Hedefe doğru akıyor"
        )
        3 -> listOf(
            "Hedefe çok az kaldı",
            "Neredeyse tamam",
            "Son düzlüktesin",
            "Bugünün hedefi yakın"
        )
        4 -> listOf(
            "Hedef tamam",
            "Bugünün hedefi geçildi",
            "Su tarafı tamam",
            "Hedef çizgisinin üstünde"
        )
        5 -> listOf(
            "Hedefin üstündesin",
            "Bugün su bol",
            "Hedef biraz aşıldı",
            "Su seviyesi güçlü"
        )
        6 -> listOf(
            "Bugün bayağı su var",
            "Hedef epey geçildi",
            "Su tarafı yüksek",
            "Bugün su önde"
        )
        else -> listOf(
            "Su tarafı tavan",
            "Bugün su çok yüksek",
            "Hedefin çok üzerindesin",
            "Su sayacı uçmuş"
        )
    }
    return options[kotlin.random.Random.nextInt(options.size)]
}
