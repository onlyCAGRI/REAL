package com.cagri.real.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import com.cagri.real.ui.DesignStyle
import kotlin.math.ceil


@Composable
fun BlurredBackdrop(backdrop: GraphicsLayer, offset: () -> Offset, radius: Dp, modifier: Modifier, sampleScale: Float = 0.5f) {
    if (Build.VERSION.SDK_INT < 31) return
    // The blur radius is expressed in the downsampled layer, so the visible blur stays the same at any scale.
    val px = with(LocalDensity.current) { radius.toPx() * sampleScale }
    val effect = remember(px) {
        android.graphics.RenderEffect.createBlurEffect(px, px, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
    }
    val sample = rememberGraphicsLayer()
    Canvas(modifier) {
        val position = offset()
        sample.record(size = IntSize(ceil(size.width * sampleScale).toInt().coerceAtLeast(1), ceil(size.height * sampleScale).toInt().coerceAtLeast(1))) {
            drawRect(Color.Black)
            scale(sampleScale, sampleScale, Offset.Zero) { translate(-position.x, -position.y) { drawLayer(backdrop) } }
        }
        sample.renderEffect = effect
        sample.clip = true
        scale(1f / sampleScale, 1f / sampleScale, Offset.Zero) { drawLayer(sample) }
    }
}

@Composable
fun FrostedSurface(
    backdrop: GraphicsLayer, backdropPosition: () -> Offset, modifier: Modifier,
    corners: Dp, reduceDynamicBlur: Boolean, blurRadius: Dp, icy: Boolean, dense: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val shape = remember(corners) { RoundedCornerShape(corners) }
    val dynamic = !reduceDynamicBlur && Build.VERSION.SDK_INT >= 31
    val tint = remember(icy, dense, dynamic) {
        Brush.linearGradient(when {
            !dynamic -> listOf(Color(0xFF222225), Color(0xFF161619))
            icy && dense -> listOf(Color(0xC9101012), Color(0xAD0B0B0E), Color(0xCC0E0E10))
            icy -> listOf(Color(0xB8202023), Color(0xA008080B), Color(0xC4101012))
            else -> listOf(Color(0xAC353537), Color(0xA0171719), Color(0xBA1B1B1E))
        })
    }
    val rim = remember(icy) { Brush.verticalGradient(listOf(Color.White.copy(alpha = if (icy) 0.15f else 0.22f), Color.White.copy(alpha = 0.055f))) }
    Box(modifier.onGloballyPositioned { position = it.positionInRoot() }.clip(shape).border(0.8.dp, rim, shape)) {
        if (dynamic) BlurredBackdrop(backdrop, { position - backdropPosition() }, blurRadius, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(tint))
        content()
    }
}

@Composable
fun DesignPanel(backdrop: GraphicsLayer, backdropPosition: () -> Offset, modifier: Modifier = Modifier,
    corners: Dp = 30.dp, reduceDynamicBlur: Boolean = false, designStyle: DesignStyle = DesignStyle.Glass,
    liquidDynamic: Boolean = false, liquidSheet: Boolean = false, liquidDock: Boolean = false,
    content: @Composable BoxScope.() -> Unit) {
    if (designStyle == DesignStyle.LiquidGlass) {
        if (liquidDynamic || liquidSheet) LiquidGlassSurface(backdrop, backdropPosition, modifier, corners, reduceDynamicBlur,
            role = when { liquidSheet -> LiquidGlassRole.Sheet; liquidDock -> LiquidGlassRole.Dock; else -> LiquidGlassRole.Chrome }, content = content)
        else LiquidGlassContentSurface(modifier, corners, content)
        return
    }
    FrostedSurface(backdrop, backdropPosition, modifier, corners, reduceDynamicBlur,
        if (designStyle == DesignStyle.Ice) 38.dp else 18.dp, designStyle == DesignStyle.Ice, false, content)
}
