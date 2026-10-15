package com.cagri.real.ui.components.planner

import com.cagri.real.ui.components.RealMotion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay

/**
 * Motion only (no layout/design): one 0..1500 ms clock per screen, every element reads its own slice of it
 * inside graphicsLayer, so the whole entrance costs no recomposition.
 */
internal val PlannerEaseOut = RealMotion.Standard
/** Slight overshoot, used for tiles/event cards that "pop" in. */
internal val PlannerPopEase = RealMotion.Pop
internal const val PlannerIntroTotalMs = 1500f
internal const val PlannerRevealOpenMs = 380
internal const val PlannerRevealCloseMs = 300

@Stable
internal class PlannerIntro(private val driver: Animatable<Float, AnimationVector1D>, val enabled: Boolean) {
    /** 0..1 progress of the slice that starts at [startMs] and lasts [lengthMs]. */
    fun slice(startMs: Int, lengthMs: Int): Float {
        if (!enabled) return 1f
        return ((driver.value - startMs) / lengthMs).coerceIn(0f, 1f)
    }

    /** Plays the whole entrance again from the start (used when coming back to a screen). */
    suspend fun restart(delayMs: Long = 0L) {
        driver.snapTo(0f)
        if (delayMs > 0L) delay(delayMs)
        driver.animateTo(PlannerIntroTotalMs, tween(PlannerIntroTotalMs.toInt(), easing = LinearEasing))
    }

    /** Plays the entrance backwards (late items leave first) while the screen is closing. */
    suspend fun reverse(durationMs: Int = 320) {
        driver.animateTo(0f, tween(durationMs, easing = LinearEasing))
    }
}

@Composable
internal fun rememberPlannerIntro(reduce: Boolean, delayMs: Long = 0L): PlannerIntro {
    val driver = remember { Animatable(if (reduce) PlannerIntroTotalMs else 0f) }
    LaunchedEffect(reduce) {
        if (reduce) {
            driver.snapTo(PlannerIntroTotalMs)
        } else if (driver.value < PlannerIntroTotalMs) {
            if (delayMs > 0L) delay(delayMs)
            driver.animateTo(PlannerIntroTotalMs, tween(PlannerIntroTotalMs.toInt(), easing = LinearEasing))
        }
    }
    return remember(driver, reduce) { PlannerIntro(driver, !reduce) }
}

/** Fade + slide + scale entrance for one element, staggered through [startMs]. Once finished it is a no-op. */
internal fun Modifier.plannerIntro(
    intro: PlannerIntro,
    startMs: Int,
    lengthMs: Int = 460,
    slideX: Dp = 0.dp,
    slideY: Dp = 0.dp,
    fromScale: Float = 1f,
    fade: Boolean = true,
    easing: Easing = PlannerEaseOut
): Modifier = if (!intro.enabled) this else graphicsLayer {
    val p = intro.slice(startMs, lengthMs)
    if (p < 1f) {
        val e = easing.transform(p)
        if (fade) alpha = (p * 2f).coerceIn(0f, 1f)
        translationX = (1f - e) * slideX.toPx()
        translationY = (1f - e) * slideY.toPx()
        if (fromScale != 1f) {
            val s = fromScale + (1f - fromScale) * e
            scaleX = s
            scaleY = s
        }
    }
}

/** Where the tapped category card sits (root coordinates), so its detail screen can grow out of / shrink back into it. */
@Stable
internal class PlannerRevealOrigin {
    var bounds by mutableStateOf<Rect?>(null)
    /** Bumped every time a detail screen starts closing, so the screen underneath replays its entrance. */
    var returnTick by mutableStateOf(0)
}

private class PlannerRevealShape(
    private val from: Rect, private val progress: Float, private val fromRadius: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rect = Rect(
            lerp(from.left, 0f, progress), lerp(from.top, 0f, progress),
            lerp(from.right, size.width, progress), lerp(from.bottom, size.height, progress)
        )
        val r = lerp(fromRadius, 0f, progress)
        return Outline.Rounded(RoundRect(rect, CornerRadius(r, r)))
    }
}

internal fun plannerRevealShape(from: Rect, progress: Float, fromRadius: Float): Shape =
    PlannerRevealShape(from, progress, fromRadius)
