package com.cagri.real.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin

internal val RealSweepBlue = Color(0xFF69ACFF)
internal val RealSweepBlueDeep = Color(0xFF2D6FD6)

/**
 * The REAL blue band that travels right -> left through the glass while the mic window is thinking; the card
 * detail dock sends it through once when Kaydet is tapped. [progress] 0..1
 * carries the band across, [pulse] 0..1 is its breathing and [fade] scales the whole band in and out.
 */
internal fun DrawScope.drawRealThinkingSweep(
    progress: Float, pulse: Float, fade: Float = 1f,
    light: Color = RealSweepBlue, deep: Color = RealSweepBlueDeep
) {
    if (fade <= 0f) return
    val centerX = size.width * (1.30f - progress * 1.60f)
    val bandWidth = size.width * 0.72f
    drawRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.22f to deep.copy(alpha = ((0.07f + pulse * 0.04f) * fade).coerceAtMost(1f)),
                0.46f to light.copy(alpha = ((0.19f + pulse * 0.08f) * fade).coerceAtMost(1f)),
                0.62f to light.copy(alpha = ((0.24f + pulse * 0.09f) * fade).coerceAtMost(1f)),
                0.82f to deep.copy(alpha = ((0.07f + pulse * 0.04f) * fade).coerceAtMost(1f)),
                1.00f to Color.Transparent
            ),
            start = Offset(centerX + bandWidth, size.height * 0.12f),
            end = Offset(centerX - bandWidth, size.height * 0.88f)
        )
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(light.copy(alpha = ((0.09f + pulse * 0.05f) * fade).coerceAtMost(1f)), Color.Transparent),
            center = Offset(centerX, size.height * 0.58f),
            radius = size.width * 0.72f
        )
    )
}

/**
 * One pass of the band (a button's confirmation rather than the mic's loop): it fades in at the right edge,
 * breathes once on the way and fades out at the left. [strength] above 1 makes a short pass easier to notice.
 */
internal fun DrawScope.drawRealSweepOnce(
    progress: Float, strength: Float = 1f,
    light: Color = RealSweepBlue, deep: Color = RealSweepBlueDeep
) {
    if (progress <= 0f || progress >= 1f) return
    val fade = when {
        progress < 0.12f -> progress / 0.12f
        progress > 0.85f -> (1f - progress) / 0.15f
        else -> 1f
    }
    val pulse = (0.7f + 0.3f * sin(progress * PI.toFloat() * 2.2f)).coerceIn(0f, 1f)
    drawRealThinkingSweep(progress, pulse, fade * strength, light, deep)
}
