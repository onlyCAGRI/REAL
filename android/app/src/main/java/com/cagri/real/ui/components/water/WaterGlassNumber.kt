package com.cagri.real.ui.components.water

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.cos
import kotlin.math.sin

/**
 * A moving waterline lens: dry glyphs stay sharp; submerged strokes stretch near the meniscus.
 * Enhanced with stronger refraction, caustic shimmer, and underwater distortion matching
 * the reference app's glass-like percentage display.
 */
internal fun DrawScope.drawWaterGlassNumber(
    layout: TextLayoutResult,
    origin: Offset,
    water: Path,
    engine: WaterSurfaceEngine,
    reducedMotion: Boolean,
    ink: Color = Color(0xFFE3F7FA),
    numberCache: WaterNumberCache,
    softLight: Brush,
    surfaceY: (Float) -> Float
) {
    numberCache.prepare(this, layout, ink)
    // ── Above water: draw crisp text ──
    clipPath(water, clipOp = ClipOp.Difference) {
        // Subtle bright outline/glow on dry portion for visibility
        numberCache.drawDry(this, origin)
    }

    // ── Below water: refraction lens effect ──
    clipPath(water) {
        if (reducedMotion) {
            numberCache.drawInk(this, origin)
        } else {
            // Thin vertical slices follow the tilted surface independently, avoiding chunky bands.
            val sliceWidth = 2f * density
            // Deeper lens zone for the larger text
            val lensDepth = 56f * density
            val count = (layout.size.width / sliceWidth).toInt() + 1

            for (slice in 0..count) {
                val left = origin.x + slice * sliceWidth
                val mid = left + sliceWidth * 0.5f
                val line = surfaceY(mid / size.width.coerceAtLeast(1f))
                val phase = mid / (20f * density) + engine.time * 1.6f
                // Stronger horizontal displacement for the underwater refraction look
                val shiftX = sin(phase) * (3.8f + engine.energy * 1.5f) * density
                // Vertical wobble for more organic water distortion
                val shiftY = cos(phase * 0.7f + 0.5f) * 1.8f * density
                // More dramatic magnification/compression
                val magnification = 1.55f + sin(phase * 0.65f) * 0.25f
                val lensBottom = line + lensDepth

                // ── Lens zone (refraction area near surface) ──
                clipRect(left, line, left + sliceWidth, lensBottom) {
                    withTransform({
                        // Anchor the lower edge so the stretched strokes join the undistorted glyph.
                        scale(1f, magnification, Offset(mid, lensBottom))
                    }) {
                        val position = origin + Offset(shiftX, shiftY)
                        // Replay the unchanged ghost/glyph display list under the original per-slice transform.
                        numberCache.drawLens(this, position)
                    }
                }

                // ── Deep underwater zone (below lens) ──
                clipRect(left, lensBottom, left + sliceWidth, size.height) {
                    // Slight deep-water wobble even below the main lens
                    val deepPhase = mid / (35f * density) + engine.time * 0.8f
                    val deepShift = sin(deepPhase) * 1.2f * density
                    numberCache.drawDeep(this, origin + Offset(deepShift, 0f))
                }
            }

            // ── Caustic light shimmer on the text area ──
            val textCenterX = origin.x + layout.size.width * 0.5f
            val textCenterY = origin.y + layout.size.height * 0.5f
            for (i in 0..5) {
                val causticPhase = engine.time * 0.5f + i * 1.2f
                val cx = textCenterX + sin(causticPhase * 0.6f + i) * layout.size.width * 0.3f
                val cy = textCenterY + cos(causticPhase * 0.4f + i * 0.7f) * layout.size.height * 0.25f
                val r = (10f + sin(causticPhase) * 6f) * density
                drawWaterSoftLight(softLight, Offset(cx, cy), r, 0.06f + sin(causticPhase) * 0.02f)
            }
        }
    }
}
