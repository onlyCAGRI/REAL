package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Only the loading branch owns a clock; its angle is read in draw, not composition. */
@Composable
internal fun DiscoveryLoadingButton(
    label: String, loading: Boolean, enabled: Boolean, accent: Color,
    reduceAnimations: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val angle = if (loading && !reduceAnimations) {
        rememberInfiniteTransition(label = "discovery-loading-orbit").animateFloat(
            0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
            label = "discovery-loading-angle")
    } else null
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(56.dp)
            .drawWithCache {
                fun ringPath(outer: Float, inner: Float) = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRoundRect(RoundRect(-outer, -outer, size.width + outer, size.height + outer,
                        CornerRadius(size.height / 2 + outer)))
                    addRoundRect(RoundRect(inner, inner, size.width - inner, size.height - inner,
                        CornerRadius((size.height / 2 - inner).coerceAtLeast(0f))))
                }
                val midpoint = Offset(size.width / 2f, size.height / 2f)
                val mid = if (accent.red > accent.blue) Color(0xFFFFA11F) else Color(0xFF7BACFF)
                // Fixed coloured corners keep the black pill visible even before loading.
                val fixed = Brush.sweepGradient(
                    0f to accent.copy(alpha = .12f), .12f to accent.copy(alpha = .85f),
                    .23f to mid, .34f to accent.copy(alpha = .18f),
                    .5f to accent.copy(alpha = .12f), .62f to accent.copy(alpha = .85f),
                    .73f to mid, .84f to accent.copy(alpha = .18f),
                    1f to accent.copy(alpha = .12f), center = midpoint)
                val beam = Brush.sweepGradient(
                    0f to Color.Transparent, .55f to Color.Transparent,
                    .65f to accent.copy(alpha = .22f), .73f to lerp(accent, Color.White, .5f).copy(alpha = .45f),
                    .81f to lerp(accent, Color.White, .8f), .86f to Color.White,
                    .9f to Color.White, .94f to accent.copy(alpha = .45f),
                    1f to Color.Transparent, center = midpoint)
                val outerHalo = ringPath(6.dp.toPx(), 1.dp.toPx())
                val nearHalo = ringPath(3.5.dp.toPx(), 1.dp.toPx())
                val innerHalo = ringPath(0f, 5.dp.toPx())
                val edge = ringPath(.4.dp.toPx(), 1.2.dp.toPx())
                onDrawWithContent {
                    drawContent()
                    drawRoundRect(Color.White.copy(alpha = if (enabled || loading) .12f else .07f),
                        cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.dp.toPx()))
                    val strength = if (enabled || loading) 1f else .4f
                    fun fixedIn(path: Path, alpha: Float) {
                        clipPath(path) { drawCircle(fixed, radius = size.maxDimension, center = center, alpha = alpha * strength) }
                    }
                    fixedIn(outerHalo, .05f)
                    fixedIn(nearHalo, .12f)
                    fixedIn(innerHalo, .1f)
                    fixedIn(edge, .85f)
                    if (loading) {
                        fun beamIn(path: Path, alpha: Float) {
                            clipPath(path) {
                                rotate(angle?.value ?: 0f) {
                                    drawCircle(beam, radius = size.maxDimension, center = center, alpha = alpha)
                                }
                            }
                        }
                        beamIn(outerHalo, .05f)
                        beamIn(nearHalo, .12f)
                        beamIn(innerHalo, .12f)
                        beamIn(edge, 1f)
                    }
                }
            }
            .clip(shape)
            .background(Color.Black)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center) {
            Text(if (loading) t("Hazırlanıyor…") else label, color = Color.White,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
