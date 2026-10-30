package com.cagri.real.ui.components

import android.content.res.Resources
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A pose has up to ~25 frames; 64 keeps the current pose plus the next one decoded.
private val petFrameCache = LruCache<Int, ImageBitmap>(64)

private fun realPetFrameBitmap(resources: Resources, resource: Int): ImageBitmap =
    petFrameCache.get(resource) ?: ImageBitmap.imageResource(resources, resource).also {
        petFrameCache.put(resource, it)
    }

/** Decodes a pose's frames off the main thread before it starts playing. */
internal suspend fun warmRealPetFrames(resources: Resources, frames: IntArray) {
    withContext(Dispatchers.Default) {
        frames.forEach { realPetFrameBitmap(resources, it) }
    }
}

/**
 * Every real_pet_* frame shares one canvas shape (640x560 or 320x280): same cat scale, facing
 * left, ground shadow removed, feet at 540/560. So each pose draws the whole canvas at one size.
 */
@Composable
internal fun RealPetFrameImage(
    resource: Int,
    modifier: Modifier = Modifier,
    breath: Float = 0f
) {
    val bitmap = realPetFrameBitmap(LocalContext.current.resources, resource)
    BoxWithConstraints(modifier) {
        // 640px canvas -> 90dp in the 114dp envelope keeps the old idle cat size.
        val canvasWidth = maxWidth * (90f / 114f)
        val canvasHeight = canvasWidth * (560f / 640f)
        // Old sprites stood ~6dp above the envelope bottom; the canvas already has 20px under the feet.
        val footPadding = maxHeight * (3f / 114f)
        Image(
            bitmap = bitmap,
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
