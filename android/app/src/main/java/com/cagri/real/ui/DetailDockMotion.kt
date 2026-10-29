package com.cagri.real.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.cagri.real.ui.components.drawRealSweepOnce
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Card detail bottom dock motion (iOS 27 style): when the dock switches between its sets of actions the glass
 * capsules behind the buttons flow into each other like drops, and the labels blur out and back in. Kaydet also
 * sends REAL's mic sweep through the glass once; the new buttons appear as the band passes over them.
 */
internal enum class DetailDockMode(val slots: Int) { Normal(4), Done(4), Edit(2), InlineStack(1) }

/** Glass drops behind the dock buttons: one per slot of the widest set. */
private const val DROPS = 4

internal const val DETAIL_DOCK_MILLIS = 450
private const val ITEM_OUT_MILLIS = 180
private const val ITEM_IN_MILLIS = 220
private const val ITEM_IN_DELAY_MILLIS = 160
private const val PILL_MILLIS = 360
private const val PILL_WAVE_MILLIS = 250
private val DockEasing = CubicBezierEasing(0.3f, 0.9f, 0.25f, 1f)
private val LabelBlur = 9.dp
private val PillHeight = 48.dp
private val PillMidHeight = 40.dp
private val PillGap = 6.dp
private val RowPadding = 6.dp
private val GooBlur = 6.dp
private const val PILL_ALPHA = 0.14f

/** When the sweep reaches the middle of button [index] of [count], counted from the moment Kaydet was tapped. */
private fun waveDelayMillis(index: Int, count: Int, fromLeft: Boolean = false): Int {
    val center = ((index + 0.5f) / count).let { if (fromLeft) 1f - it else it }
    return (DETAIL_DOCK_MILLIS * (1.30f - center) / 1.60f).toInt().coerceAtLeast(0)
}

/** Enter delays for the new buttons: staggered by the sweep while it still crosses the glass, else one short pause. */
internal fun detailDockEnterDelays(count: Int, waveElapsedMillis: Long?, fromLeft: Boolean = false): IntArray = IntArray(count) { i ->
    if (waveElapsedMillis == null || waveElapsedMillis >= DETAIL_DOCK_MILLIS) ITEM_IN_DELAY_MILLIS
    else (waveDelayMillis(i, count, fromLeft) - waveElapsedMillis).toInt().coerceAtLeast(0)
}

/** Blur, fade and scale of one dock button while its set of actions comes in or leaves. */
@Composable
internal fun AnimatedVisibilityScope.detailDockItemMotion(enterDelayMillis: Int, reduceAnimations: Boolean): Modifier {
    if (reduceAnimations) return Modifier
    val hidden by transition.animateFloat(
        transitionSpec = {
            if (EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible)
                tween(ITEM_IN_MILLIS, delayMillis = enterDelayMillis, easing = DockEasing)
            else tween(ITEM_OUT_MILLIS, easing = DockEasing)
        },
        label = "detailDockItemHidden"
    ) { if (it == EnterExitState.Visible) 0f else 1f }
    val leaving = transition.targetState == EnterExitState.PostExit
    return Modifier.graphicsLayer {
        alpha = 1f - hidden
        // Leaving labels swell slightly as they blur away; arriving ones grow into place.
        val scale = if (leaving) 1f + 0.15f * hidden else 1f - 0.2f * hidden
        scaleX = scale
        scaleY = scale
        val radius = LabelBlur.toPx() * hidden
        renderEffect = if (Build.VERSION.SDK_INT >= 31 && radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
    }
}

/** The one-shot Kaydet sweep (REAL's mic band), drawn behind the dock buttons while [progress] runs 0..1. */
internal fun Modifier.detailDockSweep(progress: () -> Float, fromLeft: () -> Boolean = { false }): Modifier = drawBehind {
    // Tamamla runs the same band mirrored, from the left (where its button sits).
    if (fromLeft()) scale(-1f, 1f) { drawRealSweepOnce(progress()) } else drawRealSweepOnce(progress())
}

/** Sil confirmed in the card detail: the page leaves to the left on its way home (ReminderScreen). */
internal object DetailDeleteMotion {
    var active by androidx.compose.runtime.mutableStateOf(false)
}


/**
 * Four glass drops behind the dock buttons, shown only during a mode change: each drop narrows, travels to its new slot and
 * widens again; on Android 12+ a blur + alpha threshold makes neighbouring drops merge and split like liquid.
 * [waveStartMillis] (uptime of the Kaydet tap) staggers the drops with the sweep.
 */
@Composable
internal fun DetailDockDrops(
    mode: DetailDockMode,
    waveStartMillis: Long,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    waveFromLeft: Boolean = false
) {
    var fromMode by remember { mutableStateOf(mode) }
    var toMode by remember { mutableStateOf(mode) }
    var delays by remember { mutableStateOf(IntArray(DROPS)) }
    var duration by remember { mutableStateOf(PILL_MILLIS) }
    val clock = remember { Animatable(1f) }
    LaunchedEffect(mode) {
        if (mode == toMode) return@LaunchedEffect
        fromMode = toMode
        toMode = mode
        if (reduceAnimations) {
            clock.snapTo(1f)
            return@LaunchedEffect
        }
        val elapsed = android.os.SystemClock.uptimeMillis() - waveStartMillis
        val wave = (mode == DetailDockMode.Normal || mode == DetailDockMode.Done) && elapsed in 0 until DETAIL_DOCK_MILLIS
        duration = if (wave) PILL_WAVE_MILLIS else PILL_MILLIS
        // Tamamla is Kaydet mirrored: the drops flow out of a two-button dock just as they do after Kaydet (the same
        // four slots before and after would leave them still), only the band and stagger run from the left.
        if (wave && waveFromLeft && mode == DetailDockMode.Done) fromMode = DetailDockMode.Edit
        delays = IntArray(DROPS) { i ->
            if (!wave) 0 else max(0, waveDelayMillis(slotOf(i, DROPS), DROPS, waveFromLeft) - elapsed.toInt() - 50)
        }
        val total = duration + (delays.maxOrNull() ?: 0)
        clock.snapTo(0f)
        clock.animateTo(1f, tween(total, easing = LinearEasing))
    }
    val density = LocalDensity.current
    val goo = remember(density) {
        if (Build.VERSION.SDK_INT >= 31) {
            val radius = with(density) { GooBlur.toPx() }
            RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 22f, -9f * 255f
                ))),
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
            ).asComposeRenderEffect()
        } else null
    }
    Canvas(modifier.graphicsLayer {
        renderEffect = goo
        // Only while the dock changes: the drops rise out of the glass, flow and sink back, so the resting dock
        // shows no shapes of its own.
        alpha = if (clock.value >= 1f) 0f else PILL_ALPHA * sin(clock.value * PI.toFloat())
    }) {
        val total = duration + (delays.maxOrNull() ?: 0)
        val now = clock.value * total
        val pad = RowPadding.toPx()
        val gap = PillGap.toPx()
        fun slot(index: Int, count: Int): Pair<Float, Float> {
            val width = (size.width - pad * 2) / count
            return (pad + index * width + gap / 2) to (width - gap)
        }
        for (i in 0 until DROPS) {
            val (fromLeft, fromWidth) = slot(slotOf(i, fromMode.slots), fromMode.slots)
            val (toLeft, toWidth) = slot(slotOf(i, toMode.slots), toMode.slots)
            val t = DockEasing.transform(((now - delays[i]) / duration).coerceIn(0f, 1f))
            val midLeft = (fromLeft + toLeft) / 2
            val midWidth = max(fromWidth, toWidth) * 0.7f
            val left: Float
            val width: Float
            val height: Float
            if (t < 0.45f) {
                val k = t / 0.45f
                left = fromLeft + (midLeft - fromLeft) * k
                width = fromWidth + (midWidth - fromWidth) * k
                height = PillHeight.toPx() + (PillMidHeight.toPx() - PillHeight.toPx()) * k
            } else {
                val k = (t - 0.45f) / 0.55f
                left = midLeft + (toLeft - midLeft) * k
                width = midWidth + (toWidth - midWidth) * k
                height = PillMidHeight.toPx() + (PillHeight.toPx() - PillMidHeight.toPx()) * k
            }
            drawRoundRect(
                Color.White,
                topLeft = Offset(left, (size.height - height) / 2),
                size = Size(width, height),
                cornerRadius = CornerRadius(height / 2)
            )
        }
    }
}

/** Which slot drop [index] (of [DROPS]) sits in when the dock shows [count] buttons. */
private fun slotOf(index: Int, count: Int): Int = index * count / DROPS
