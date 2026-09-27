package com.cagri.bigboscagri.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import android.content.Context
import com.cagri.bigboscagri.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val cache = LruCache<Int, ImageBitmap>(2)

fun prewarmRealWordmark(context: Context) {
    try {
        val resources = context.resources
        val width = (112 * resources.displayMetrics.density).toInt()
        if (cache.get(width) == null) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, R.drawable.real_wordmark, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= width) sample *= 2
            BitmapFactory.decodeResource(
                resources, R.drawable.real_wordmark,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )?.asImageBitmap()?.also { cache.put(width, it) }
        }
    } catch (_: Exception) {}
}

@Composable
fun RealWordmark(modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val width = with(LocalDensity.current) { 112.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(cache.get(width), width) {
        value = cache.get(width) ?: withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, R.drawable.real_wordmark, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= width) sample *= 2
            BitmapFactory.decodeResource(resources, R.drawable.real_wordmark,
                BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()?.also { cache.put(width, it) }
        }
    }
    bitmap?.let { Image(it, "Real", modifier, contentScale = ContentScale.Fit) }
        ?: Text("Real", modifier, color = Color.White)
}
