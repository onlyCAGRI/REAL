package com.cagri.real.books

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Üst ve alt kenarda kademeli (progressive) cam bulanıklığı. Referanstaki gibi içerik kenara yaklaştıkça
 * önce hafif, sonra giderek daha fazla bulanıklaşır ve zemine karışır; metin hâlâ "orada" gibi görünür.
 * Android 12+ gerçek blur (3 kademe, yalnız kenar şeritlerinde -> ucuz); daha eskide yumuşak zemin geçişi.
 * Şeritler ayrı katman değil, içerikle aynı katmanda çizilir; böylece kaydırırken her karede güncellenir.
 * Overlay dokunmayı yakalamaz.
 */
@Composable
fun GlassEdges(
    background: Color,
    top: Dp,
    bottom: Dp,
    modifier: Modifier = Modifier,
    blur: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = rememberGraphicsLayer()
    var rootHeight by remember { mutableIntStateOf(0) }
    Box(modifier.onSizeChanged { rootHeight = it.height }) {
        Box(Modifier.matchParentSize().drawWithContent {
            source.record { this@drawWithContent.drawContent() }
            drawLayer(source)
        }, content = content)
        if (top > 0.dp) EdgeStrip(source, background, top, atTop = true, blur = blur) { rootHeight }
        if (bottom > 0.dp) EdgeStrip(source, background, bottom, atTop = false, blur = blur) { rootHeight }
    }
}

@Composable
private fun BoxScope.EdgeStrip(
    source: GraphicsLayer,
    background: Color,
    height: Dp,
    atTop: Boolean,
    blur: Dp,
    rootHeight: () -> Int,
) {
    val level0 = rememberGraphicsLayer()
    val level1 = rememberGraphicsLayer()
    val level2 = rememberGraphicsLayer()
    Box(
        Modifier.align(if (atTop) Alignment.TopStart else Alignment.BottomStart).fillMaxWidth().height(height)
            .drawWithCache {
                val h = size.height
                val layers = arrayOf(level0, level1, level2)
                val radius = floatArrayOf(.2f, .55f, 1.2f)
                val full = floatArrayOf(.55f, .30f, .05f)
                val none = floatArrayOf(1f, .75f, .5f)
                val masks = Array(3) { edgeMask(atTop, full[it], none[it]) }
                val paint = Paint()
                val rect = Rect(0f, 0f, size.width, h)
                val clear = background.copy(alpha = 0f)
                val tint = background.copy(alpha = if (Build.VERSION.SDK_INT >= 31) {
                    if (atTop) .5f else .42f
                } else .96f)
                val tintBrush = if (atTop) Brush.verticalGradient(0f to tint, 1f to clear)
                    else Brush.verticalGradient(0f to clear, 1f to tint)
                if (Build.VERSION.SDK_INT >= 31) layers.forEachIndexed { k, layer ->
                    val r = blur.toPx() * radius[k]
                    layer.renderEffect = BlurEffect(r, r, TileMode.Clamp)
                }
                onDrawWithContent {
                    if (h > 0f) {
                        if (Build.VERSION.SDK_INT >= 31) {
                            val yOffset = if (atTop) 0f else (rootHeight() - h).coerceAtLeast(0f)
                            val canvas = drawContext.canvas
                            for (k in 0..2) {
                                layers[k].record { translate(top = -yOffset) { drawLayer(source) } }
                                canvas.saveLayer(rect, paint)
                                drawRect(background)
                                drawLayer(layers[k])
                                drawRect(masks[k], blendMode = BlendMode.DstIn)
                                canvas.restore()
                            }
                        }
                        drawRect(tintBrush)
                    }
                }
            },
    ) {}
}

/** Kenarda opak, içeri doğru şeffaflaşan maske. depth: kenardan uzaklık oranı (0..1). */
private fun edgeMask(atTop: Boolean, full: Float, none: Float): Brush {
    val a = Color.Black
    val z = Color.Transparent
    return if (atTop) Brush.verticalGradient(0f to a, full to a, none to z, 1f to z)
    else Brush.verticalGradient(0f to z, (1f - none) to z, (1f - full) to a, 1f to a)
}
