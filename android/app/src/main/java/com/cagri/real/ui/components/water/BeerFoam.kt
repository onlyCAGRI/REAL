package com.cagri.real.ui.components.water

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.sin

/** Cream-colored head follows the very same tilted/rippling surface as the liquid. */
internal fun DrawScope.drawBeerFoam(
    engine: WaterSurfaceEngine, reducedMotion: Boolean, foamPath: Path, surfaceY: (Float) -> Float
) {
    val phase = if (reducedMotion) 0f else engine.time
    val thickness = (18f + engine.energy * 7f) * density
    fun foamTop(x: Float): Float = surfaceY(x) - thickness +
        sin(x * 19f + phase * 0.8f) * 2f * density + sin(x * 47f - phase) * density
    val foam = foamPath.apply {
        rewind()
        moveTo(0f, foamTop(0f))
        for (index in 1..96) {
            val x = index / 96f
            lineTo(x * size.width, foamTop(x))
        }
        for (index in 96 downTo 0) {
            val x = index / 96f
            lineTo(x * size.width, surfaceY(x) + 3f * density)
        }
        close()
    }
    val center = surfaceY(0.5f)
    drawPath(foam, Brush.verticalGradient(
        listOf(Color(0xFFFFFBEF), Color(0xFFF3E8CF), Color(0xFFE0CE9E)),
        startY = center - thickness * 2f, endY = center + thickness
    ))
    clipPath(foam) {
        for (index in 0 until 90) {
            val x = ((index * 0.618034f) % 1f)
            val depth = (index * 0.381966f) % 1f
            val y = foamTop(x) + thickness * depth
            val radius = (0.65f + (index % 5) * 0.42f) * density
            drawCircle(Color(0xFFC7AF7B).copy(alpha = 0.26f), radius,
                Offset(x * size.width, y), style = Stroke(0.55f * density))
            if (index % 3 == 0) drawCircle(Color.White.copy(alpha = 0.45f), radius * 0.44f,
                Offset(x * size.width - radius * 0.2f, y - radius * 0.2f))
        }
    }
}
