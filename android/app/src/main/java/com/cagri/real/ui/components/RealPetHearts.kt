package com.cagri.real.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

internal data class RealPetHeartBurst(val id: Int, val origin: Offset)

@Composable
internal fun RealPetHearts(
    burst: RealPetHeartBurst,
    reduceAnimations: Boolean,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val finished = rememberUpdatedState(onFinished)
    val heart = remember {
        Path().apply {
            moveTo(0.5f, 0.93f)
            cubicTo(0.36f, 0.80f, 0f, 0.55f, 0f, 0.28f)
            cubicTo(0f, -0.02f, 0.36f, -0.08f, 0.5f, 0.18f)
            cubicTo(0.64f, -0.08f, 1f, -0.02f, 1f, 0.28f)
            cubicTo(1f, 0.55f, 0.64f, 0.80f, 0.5f, 0.93f)
            close()
        }
    }
    LaunchedEffect(burst.id, reduceAnimations) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(if (reduceAnimations) 480 else 820, easing = LinearEasing))
        finished.value()
    }
    Canvas(Modifier.fillMaxSize()) {
        repeat(5) { index ->
            val t = ((progress.value - index * 0.05f) / 0.75f).coerceIn(0f, 1f)
            if (t > 0f && t < 1f) {
                val lane = index - 2f
                val spread = lane * 5.5.dp.toPx()
                val drift = if (reduceAnimations) 0f else {
                    lane * 3.5.dp.toPx() * t + sin((t * PI * 2 + index).toFloat()) * 2.dp.toPx()
                }
                val rise = if (reduceAnimations) 0f else t * (12 + index % 3 * 3).dp.toPx()
                val edge = (8 + index % 3 * 2).dp.toPx()
                val pop = if (reduceAnimations) 1f else 0.75f + 0.25f * (t / 0.14f).coerceAtMost(1f)
                val opacity = (t / 0.08f).coerceAtMost(1f) * ((1f - t) / 0.40f).coerceAtMost(1f)
                val x = (burst.origin.x + spread + drift - edge / 2f)
                    .coerceIn(0f, (size.width - edge).coerceAtLeast(0f))
                val y = (burst.origin.y - edge - rise).coerceAtLeast(0f)
                translate(x, y) {
                    scale(edge * pop, edge * pop, pivot = Offset.Zero) {
                        drawPath(
                            heart,
                            color = (if (index % 2 == 0) Color(0xFFFF7DA8) else Color(0xFFFFB0C9))
                                .copy(alpha = opacity * 0.95f)
                        )
                    }
                }
            }
        }
    }
}
