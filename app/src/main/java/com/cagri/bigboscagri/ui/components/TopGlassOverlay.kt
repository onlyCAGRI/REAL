package com.cagri.bigboscagri.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** The scrolling scene extends behind the system clock and status icons. */
@Composable
fun TopGlassOverlay(
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    modifier: Modifier = Modifier,
    reduceDynamicBlur: Boolean = false
) {
    val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val radius = with(density) { 14.dp.toPx() }
    val fadeStart = with(density) { statusHeight.toPx() / (statusHeight + 18.dp).toPx() }
    var position by remember { mutableStateOf(Offset.Zero) }
    val dynamic = Build.VERSION.SDK_INT >= 31 && !reduceDynamicBlur
    val fadeBrush = remember(fadeStart) {
        Brush.verticalGradient(0f to Color.White, fadeStart to Color.White, 1f to Color.Transparent)
    }
    Box(
        modifier.fillMaxWidth().height(statusHeight + 18.dp)
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
        if (dynamic) BlurredBackdrop(backdrop, { position - backdropPosition() }, 14.dp, Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize().background(
                if (reduceDynamicBlur) Color.Black
                else Color.Black.copy(alpha = if (dynamic) 0.2f else 0.76f)
            )
        )
    }
}
