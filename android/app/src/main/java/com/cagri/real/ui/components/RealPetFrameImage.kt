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
 * Every real_pet_* frame shares one 640x560 canvas: same cat scale, facing left, ground shadow
 * removed, feet on row 540. So each pose draws the whole canvas at one fixed size.
 */
@Composable
internal fun RealPetFrameImage(
    resource: Int,
    modifier: Modifier = Modifier,
    breath: Float = 0f
) {
    BoxWithConstraints(modifier) {
        // 640px canvas -> 90dp in the 114dp envelope keeps the old idle cat size.
        val canvasWidth = maxWidth * (90f / 114f)
        val canvasHeight = canvasWidth * (560f / 640f)
        // Old sprites stood ~6dp above the envelope bottom; the canvas already has 20px under the feet.
        val footPadding = maxHeight * (3f / 114f)
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = footPadding)
                .size(canvasWidth, canvasHeight)
                .graphicsLayer {
                    // Breathing grows from the feet line (row 540 of 560).
                    transformOrigin = TransformOrigin(0.5f, 540f / 560f)
                    scaleY = 1f + breath * 0.045f
                    scaleX = 1f + breath * 0.008f
                }
        )
    }
}
