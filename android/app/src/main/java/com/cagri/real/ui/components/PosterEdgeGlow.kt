package com.cagri.real.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Six radial lights from the actual left/right poster regions; no rectangular blurred image. */
@Composable
internal fun PosterEdgeGlow(url: String?, reduceAnimations: Boolean, modifier: Modifier,
                            /** Music deck: 0..1 sound pulse; the cover's own colours behind it grow and fade with it. */
                            pulse: (() -> Float)? = null) {
    val context = LocalContext.current
    var palette by remember { mutableStateOf(List(6) { Color.Transparent }) }
    LaunchedEffect(url, context) {
        if (url.isNullOrBlank()) { palette = List(6) { Color.Transparent }; return@LaunchedEffect }
        try {
            val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url)
                .size(24, 36).allowHardware(false).build())
            val sampled = if (result is SuccessResult) withContext(Dispatchers.Default) {
                val bitmap = result.drawable.toBitmap(24, 36)
                List(6) { region ->
                    val side = region % 2
                    val band = region / 2
                    var red = 0L; var green = 0L; var blue = 0L; var count = 0L
                    for (y in band * 12 until (band + 1) * 12) {
                        for (x in (if (side == 0) 0 else 16) until (if (side == 0) 8 else 24)) {
                            val pixel = bitmap.getPixel(x, y)
                            red += (pixel ushr 16) and 255; green += (pixel ushr 8) and 255; blue += pixel and 255; count++
                        }
                    }
                    Color((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
                }
            } else List(6) { Color.Transparent }
            palette = sampled
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { palette = List(6) { Color.Transparent } }
    }
    // Read animation values in draw, not composition. These settle once when the poster changes.
    val colors = palette.map { target -> animateColorAsState(target,
        animationSpec = tween(if (reduceAnimations) 0 else 380), label = "poster-edge") }
    Canvas(modifier) {
        val beat = pulse?.invoke()?.coerceIn(0f, 1f) ?: 0f
        val radius = size.width * (.47f + .14f * beat)
        val boost = 1f + 1.3f * beat
        for (region in 0 until 6) {
            val color = colors[region].value
            if (color.alpha == 0f) continue
            val center = Offset(size.width * (if (region % 2 == 0) .15f else .85f),
                size.height * (.16f + (region / 2) * .34f))
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = (.32f * boost).coerceAtMost(.8f)), color.copy(alpha = (.12f * boost).coerceAtMost(.4f)), Color.Transparent),
                center = center, radius = radius), radius = radius, center = center)
        }
    }
}
