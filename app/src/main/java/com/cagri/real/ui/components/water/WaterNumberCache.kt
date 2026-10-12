package com.cagri.real.ui.components.water

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/** Retained vector display lists: same text/ghost draws, clips and refraction, recorded only when glyphs change. */
internal class WaterNumberCache(
    private val dry: GraphicsLayer, private val lens: GraphicsLayer,
    private val deep: GraphicsLayer, private val inkOnly: GraphicsLayer
) {
    private var lastLayout: TextLayoutResult? = null
    private var lastInk = Color.Unspecified
    private var lastDensity = -1f
    private var lastFontScale = -1f
    private var padding = 0f

    fun prepare(scope: DrawScope, layout: TextLayoutResult, ink: Color) {
        if (lastLayout === layout && lastInk == ink && lastDensity == scope.density && lastFontScale == scope.fontScale) return
        padding = ceil(8f * scope.density)
        val origin = Offset(padding, padding)
        val bounds = IntSize(layout.size.width + (padding * 2).toInt(), layout.size.height + (padding * 2).toInt())
        val d = scope.density
        dry.record(scope, scope.layoutDirection, bounds) {
            drawText(layout, color = Color.White.copy(alpha = 0.08f), topLeft = origin + Offset(-d, 0f))
            drawText(layout, color = Color.White.copy(alpha = 0.08f), topLeft = origin + Offset(d, 0f))
            drawText(layout, color = ink, topLeft = origin)
        }
        lens.record(scope, scope.layoutDirection, bounds) {
            drawText(layout, color = ink.copy(alpha = 0.28f), topLeft = origin + Offset(-2.5f * d, 0.5f * d))
            drawText(layout, color = Color.White.copy(alpha = 0.12f), topLeft = origin + Offset(1.8f * d, -0.3f * d))
            drawText(layout, color = Color(0xFF80E8F0).copy(alpha = 0.18f), topLeft = origin + Offset(-0.6f * d, 1.2f * d))
            drawText(layout, color = ink, topLeft = origin)
        }
        deep.record(scope, scope.layoutDirection, bounds) {
            drawText(layout, color = ink.copy(alpha = 0.92f), topLeft = origin)
        }
        inkOnly.record(scope, scope.layoutDirection, bounds) {
            drawText(layout, color = ink, topLeft = origin)
        }
        lastLayout = layout; lastInk = ink; lastDensity = scope.density; lastFontScale = scope.fontScale
    }

    fun drawDry(scope: DrawScope, position: Offset) = draw(scope, dry, position)
    fun drawLens(scope: DrawScope, position: Offset) = draw(scope, lens, position)
    fun drawDeep(scope: DrawScope, position: Offset) = draw(scope, deep, position)
    fun drawInk(scope: DrawScope, position: Offset) = draw(scope, inkOnly, position)

    private fun draw(scope: DrawScope, layer: GraphicsLayer, position: Offset) = with(scope) {
        withTransform({ translate(position.x - padding, position.y - padding) }) { drawLayer(layer) }
    }
}
