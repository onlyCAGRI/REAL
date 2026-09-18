package com.cagri.bigboscagri.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun IcePanel(backdrop: GraphicsLayer, backdropPosition: () -> Offset,
    modifier: Modifier = Modifier, corners: Dp = 30.dp, reduceDynamicBlur: Boolean = false,
    blurRadius: Dp = 38.dp, dense: Boolean = false,
    content: @Composable BoxScope.() -> Unit) {
    FrostedSurface(backdrop, backdropPosition, modifier, corners, reduceDynamicBlur, blurRadius, true, dense, content)
}
