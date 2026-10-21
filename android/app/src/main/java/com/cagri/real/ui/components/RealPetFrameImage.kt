package com.cagri.real.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/**
 * Full-frame pet pose renderer for user-provided PNG poses with transparent backgrounds.
 * The image is bottom-aligned inside the same 114dp interaction envelope used by the pet.
 */
@Composable
internal fun RealPetFrameImage(
    resource: Int,
    modifier: Modifier = Modifier,
    sizeScale: Float = 1f,
    breath: Float = 0f
) {
    BoxWithConstraints(modifier) {
        val bodyWidth = maxWidth * 0.92f
        val bodyHeight = maxHeight * 0.82f
        val footPadding = maxHeight * (8f / 256f)
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = footPadding)
                .size(bodyWidth, bodyHeight)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = sizeScale * (1f + breath * 0.045f)
                    scaleX = sizeScale * (1f + breath * 0.008f)
                }
        )
    }
}
