package com.cagri.real.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * REAL's loading indicator, a drop-in for Material's CircularProgressIndicator: a thin arc whose
 * tail fades out, turning smoothly. The rotation runs on the layer, so it never recomposes.
 */
@Composable
fun RealSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: Dp = 2.dp
) {
    val turn = rememberInfiniteTransition(label = "realSpinner")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "realSpinnerTurn")
    androidx.compose.foundation.layout.Box(
        modifier
            .size(40.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
            .graphicsLayer { rotationZ = turn.value }
            .drawWithCache {
                val stroke = strokeWidth.toPx()
                val inset = stroke / 2f
                val tail = Brush.sweepGradient(
                    0f to color.copy(alpha = 0f), 0.78f to color, 1f to color,
                    center = Offset(size.width / 2f, size.height / 2f)
                )
                onDrawBehind {
                    drawArc(
                        brush = tail, startAngle = 0f, sweepAngle = 280f, useCenter = false,
                        topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
    )
}
