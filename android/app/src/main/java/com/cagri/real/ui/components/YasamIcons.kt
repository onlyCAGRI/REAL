package com.cagri.real.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class YasamGlyph { Plus, PlayFilled, PlayOutline, Pencil, Back, ChevronDown, ChevronUp, MicBars }

/** Emojisiz, Canvas çizimi ikonlar. Hepsi 16 birimlik ızgarada çizilir. */
@Composable
internal fun YasamIcon(glyph: YasamGlyph, color: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawYasamGlyph(glyph, color) }
}

private fun DrawScope.drawYasamGlyph(glyph: YasamGlyph, color: Color) {
    val u = size.minDimension / 16f
    val width = (if (glyph == YasamGlyph.MicBars) 1.45f else 1.8f) * u
    val stroke = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), width, StrokeCap.Round)
    fun poly(vararg pts: Pair<Float, Float>, close: Boolean = false, fill: Boolean = false) {
        val path = Path().apply {
            moveTo(pts[0].first * u, pts[0].second * u)
            for (i in 1 until pts.size) lineTo(pts[i].first * u, pts[i].second * u)
            if (close) close()
        }
        drawPath(path, color, style = if (fill) Fill else stroke)
    }
    when (glyph) {
        YasamGlyph.Plus -> { line(8f, 3f, 8f, 13f); line(3f, 8f, 13f, 8f) }
        YasamGlyph.PlayFilled -> poly(3.4f to 1.7f, 3.4f to 14.3f, 13.7f to 8f, close = true, fill = true)
        YasamGlyph.PlayOutline -> poly(5f to 3f, 13f to 8f, 5f to 13f, close = true)
        YasamGlyph.Pencil -> poly(2.5f to 13.5f, 3.3f to 10.5f, 11f to 2.8f, 13.2f to 5f, 5.5f to 12.7f, close = true)
        YasamGlyph.Back -> poly(10f to 3f, 5f to 8f, 10f to 13f)
        YasamGlyph.ChevronDown -> poly(3f to 6f, 8f to 11f, 13f to 6f)
        YasamGlyph.ChevronUp -> poly(3f to 10f, 8f to 5f, 13f to 10f)
        YasamGlyph.MicBars -> { line(5.1f, 5.8f, 5.1f, 10.2f); line(8f, 2.9f, 8f, 13.1f); line(10.9f, 5.8f, 10.9f, 10.2f) }
    }
}
