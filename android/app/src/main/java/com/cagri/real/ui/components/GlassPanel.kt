package com.cagri.real.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassPanel(backdrop: GraphicsLayer, backdropPosition: () -> Offset,
    modifier: Modifier = Modifier, corners: Dp = 30.dp, reduceDynamicBlur: Boolean = false,
    blurRadius: Dp = 18.dp, extraFrosted: Boolean = false,
    content: @Composable BoxScope.() -> Unit) {
    if (LocalDesignStyle.current == com.cagri.real.ui.DesignStyle.LiquidGlass) {
        if (extraFrosted) LiquidGlassContentSurface(modifier, corners, content)
        else LiquidGlassSurface(backdrop, backdropPosition, modifier, corners, reduceDynamicBlur, content = content)
        return
    }
    FrostedSurface(backdrop, backdropPosition, modifier, corners, reduceDynamicBlur, blurRadius, false, extraFrosted, content)
}
