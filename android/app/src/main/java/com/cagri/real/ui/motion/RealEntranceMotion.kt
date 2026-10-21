package com.cagri.real.ui.motion

import com.cagri.real.ui.components.RealMotion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import kotlin.math.hypot

// Same easing and sliced clock as PlannerIntroMotion; independent of calendar state/data.
internal val RealEntranceEase = RealMotion.Standard
internal val RealEntrancePop = RealMotion.Pop

@Stable
internal class RealEntranceMotion(
    private val clock: Animatable<Float, AnimationVector1D>,
    val enabled: Boolean
) {
    fun slice(startMs: Int, durationMs: Int): Float = if (!enabled) 1f
        else ((clock.value - startMs) / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
}

/** One finite clock per screen. Identity changes only on navigation, never on content refresh. */
@Composable
internal fun rememberRealEntrance(
    reduceMotion: Boolean,
    identity: Any? = Unit,
    totalMs: Int = 1000,
    play: Boolean = true
): RealEntranceMotion {
    val clock = remember(identity, totalMs) { Animatable(if (reduceMotion || !play) totalMs.toFloat() else 0f) }
    LaunchedEffect(clock, reduceMotion, play) {
        if (reduceMotion || !play) clock.snapTo(totalMs.toFloat())
        else if (clock.value < totalMs) clock.animateTo(totalMs.toFloat(), tween(totalMs, easing = LinearEasing))
    }
    return remember(clock, reduceMotion, play) { RealEntranceMotion(clock, !reduceMotion && play) }
}

/** Visual transform only: does not change grid measurement, placement, size or persisted data. */
internal fun Modifier.realEntrance(
    motion: RealEntranceMotion,
    startMs: Int = 0,
    durationMs: Int = 460,
    slideY: Dp = 0.dp,
    slideX: Dp = 0.dp,
    fromScale: Float = 1f,
    easing: Easing = RealEntranceEase,
    paused: Boolean = false
): Modifier = if (!motion.enabled) this else graphicsLayer {
    val p = if (paused) 1f else motion.slice(startMs, durationMs)
    val e = easing.transform(p)
    // Always restore the identity transform, including the final frame and active gestures.
    alpha = (p * 2f).coerceIn(0f, 1f)
    translationX = (1f - e) * slideX.toPx()
    translationY = (1f - e) * slideY.toPx()
    scaleX = fromScale + (1f - fromScale) * e
    scaleY = scaleX
}

// ───────────────────────────── Yeni motion'lar ─────────────────────────────

/** Kutu eklenirken: üst-orta noktadan pill gibi açılıp tam boyuta büyür (clip reveal). */
private class RealGrowShape(private val p: Float, private val finalRadius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width * (0.55f + 0.45f * p)
        val h = size.height * p
        val left = (size.width - w) / 2f
        val r = lerp(minOf(h, w) / 2f, finalRadius, p)
        return Outline.Rounded(RoundRect(Rect(left, 0f, left + w, h), CornerRadius(r, r)))
    }
}

/**
 * "Grow": kutu üstten ve ortadan açılır, köşe yarıçapı pill'den asıl değerine iner, hafif overshoot ile oturur.
 * Yalnız görsel dönüşümdür; ölçüm/yerleşim değişmez (altındaki kutuları zaten spring iter).
 */
internal fun Modifier.realGrow(
    motion: RealEntranceMotion,
    startMs: Int = 0,
    durationMs: Int = 640,
    radius: Dp = 20.dp,
    paused: Boolean = false
): Modifier = if (!motion.enabled) this else graphicsLayer {
    val p = if (paused) 1f else motion.slice(startMs, durationMs)
    if (p < 1f) {
        val pop = RealEntrancePop.transform(p)
        transformOrigin = TransformOrigin(0.5f, 0f)
        shape = RealGrowShape(RealEntranceEase.transform(p), radius.toPx())
        clip = true
        scaleX = 0.94f + 0.06f * pop
        scaleY = scaleX
        alpha = ((p - 0.12f) * 2.2f).coerceIn(0f, 1f)
    }
}

/** [realGrow]'un dışına konur: büyürken kenarda ince bir ışık halkası belirip söner. */
internal fun Modifier.realGrowGlow(
    motion: RealEntranceMotion,
    startMs: Int = 0,
    durationMs: Int = 640,
    radius: Dp = 20.dp,
    paused: Boolean = false
): Modifier = if (!motion.enabled) this else drawWithContent {
    drawContent()
    val p = if (paused) 1f else motion.slice(startMs, durationMs)
    if (p < 1f) {
        val e = RealEntranceEase.transform(p)
        val w = size.width * (0.55f + 0.45f * e)
        val h = size.height * e
        val r = lerp(minOf(h, w) / 2f, radius.toPx(), e)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.45f * (1f - p)),
            topLeft = Offset((size.width - w) / 2f, 0f),
            size = Size(w, h),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

private class RealCircleShape(private val ox: Float, private val oy: Float, private val p: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width * ox
        val cy = size.height * oy
        val far = hypot(maxOf(cx, size.width - cx), maxOf(cy, size.height - cy))
        return Outline.Generic(Path().apply { addOval(Rect(Offset(cx, cy), far * p)) })
    }
}

/** Ekran, [originX]/[originY] (ekran oranı) noktasından dairesel olarak büyüyerek açılır. */
internal fun Modifier.realCircleReveal(
    motion: RealEntranceMotion,
    originX: Float = 0.5f,
    originY: Float = 0.93f,
    startMs: Int = 0,
    durationMs: Int = 700
): Modifier = if (!motion.enabled) this else graphicsLayer {
    val p = motion.slice(startMs, durationMs)
    if (p < 1f) {
        shape = RealCircleShape(originX, originY, RealEntranceEase.transform(p))
        clip = true
    }
}

/** Cümle kelimeleri: blur'dan netliğe + hafif yukarı kayma. Blur yalnız API 31+; altında sadece fade. */
internal fun Modifier.realWord(
    motion: RealEntranceMotion,
    startMs: Int,
    durationMs: Int = 560
): Modifier = if (!motion.enabled) this else graphicsLayer {
    val p = motion.slice(startMs, durationMs)
    if (p < 1f) {
        val e = RealEntranceEase.transform(p)
        alpha = (p * 2.2f).coerceIn(0f, 1f)
        translationY = (1f - e) * 10.dp.toPx()
        val r = (1f - e) * 14.dp.toPx()
        renderEffect = if (Build.VERSION.SDK_INT >= 31 && r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
    }
}
