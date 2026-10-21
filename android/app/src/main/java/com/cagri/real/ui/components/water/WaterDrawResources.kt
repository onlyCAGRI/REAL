package com.cagri.real.ui.components.water

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

/** Reuse geometry and a unit radial shader; translate/scale it instead of creating a shader per light. */
internal class WaterDrawResources {
    val lightBands = List(6) { Path() }
    val foam = Path()
    val softLight = Brush.radialGradient(listOf(Color.White, Color.Transparent), center = Offset.Zero, radius = 1f)
}

internal fun DrawScope.drawWaterSoftLight(brush: Brush, center: Offset, radius: Float, alpha: Float) {
    if (radius <= 0f) return
    withTransform({
        translate(center.x, center.y)
        scale(radius, radius, Offset.Zero)
    }) { drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = alpha) }
}

/** Map a cached 0..1 vertical gradient onto the original moving start/end, retaining the full-screen rect. */
internal fun DrawScope.drawWaterVerticalFill(brush: Brush, startY: Float, endY: Float) {
    val height = endY - startY
    if (height <= 0f) return
    val canvasWidth = size.width
    val canvasHeight = size.height
    withTransform({
        translate(0f, startY)
        scale(1f, height, Offset.Zero)
    }) {
        drawRect(brush, topLeft = Offset(0f, -startY / height), size = Size(canvasWidth, canvasHeight / height))
    }
}
