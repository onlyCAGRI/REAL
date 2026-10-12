package com.cagri.real.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp


@Composable
fun TopGlassOverlay(
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    modifier: Modifier = Modifier,
    reduceDynamicBlur: Boolean = false,
    extendLiquidArea: Boolean = true
) {
    val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    
    val liquid = LocalDesignStyle.current == com.cagri.real.ui.DesignStyle.LiquidGlass
    val extension by animateDpAsState(
        if (liquid && extendLiquidArea) LiquidGlassTokens.topExtension else 0.dp,
        if (LocalLiquidReduceMotion.current) snap() else tween(180), label = "liquidTopExtension"
    )
    val totalHeight = if (liquid) statusHeight * 1.9f + extension else statusHeight * 1.9f

    val fadeStart = with(density) {
        val s = statusHeight.toPx()
        val t = totalHeight.toPx()
        if (t > 0f) s / t else 0.5f
    }

    var position by remember { mutableStateOf(Offset.Zero) }
    val dynamic = shouldCaptureBackdrop(LocalDesignStyle.current, reduceDynamicBlur, Build.VERSION.SDK_INT)

    
    val fadeBrush = remember(fadeStart) {
        Brush.verticalGradient(
            0f to Color.White,
            fadeStart to Color.White,
            1f to Color.Transparent
        )
    }

    Box(
        modifier.fillMaxWidth().height(totalHeight)
            .onGloballyPositioned { position = it.positionInRoot() }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = fadeBrush,
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        if (dynamic) BlurredBackdrop(backdrop, { position - backdropPosition() }, if (liquid) LiquidGlassTokens.topBlur else 14.dp, Modifier.matchParentSize())
        if (liquid) {
            
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = if (dynamic) 0.64f else 0.96f),
                Color.Black.copy(alpha = if (dynamic) 0.18f else 0.72f)
            ))))
        } else {
        Box(
            Modifier.matchParentSize().background(
                if (reduceDynamicBlur) Color.Black
                else Color.Black.copy(alpha = if (dynamic) 0.2f else 0.76f)
            )
        )
        }
    }
}
