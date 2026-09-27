package com.cagri.bigboscagri.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** One visible size envelope for every pose, based on the user's sleeping-cat reference. */
@Composable
internal fun RealPetSpriteImage(
    resource: Int,
    sourceOffset: IntOffset,
    sourceSize: IntSize,
    modifier: Modifier = Modifier,
    sizeScale: Float = 1f,
    breath: Float = 0f
) {
    val bitmap = ImageBitmap.imageResource(resource)
    val painter = remember(bitmap, sourceOffset, sourceSize) {
        BitmapPainter(bitmap, srcOffset = sourceOffset, srcSize = sourceSize)
    }
    BoxWithConstraints(modifier) {
        val bodyWidth = maxWidth * (220f / 256f)
        // white-kitten5's opaque bounds are 805x407. Preserve its existing screen size.
        val bodyHeight = maxHeight * (220f * 407f / 805f / 256f)
        val footPadding = maxHeight * (10f / 256f)
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = footPadding)
                .size(bodyWidth, bodyHeight)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = sizeScale * (1f + breath * 0.055f)
                    scaleX = sizeScale * (1f + breath * 0.008f)
                }
        )
    }
}
