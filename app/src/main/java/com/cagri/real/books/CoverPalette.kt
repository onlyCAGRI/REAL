package com.cagri.real.books

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kapak görselinde en çok yer kaplayan rengi bulur; raftaki kitap sırtı bu renkle boyanır.
 * Görsel 48 px'e küçültülüp renkler 512 kovaya (kanal başına 3 bit) dağıtılır; en kalabalık kovanın
 * ortalaması sırt rengi olur. Sonuç bellekte tutulur, aynı kapak için tekrar hesaplanmaz.
 */
internal object CoverPalette {
    private val cache = LruCache<String, Int>(300)

    fun cached(url: String): Int? = cache.get(url)

    suspend fun dominant(context: Context, url: String): Int? {
        cache.get(url)?.let { return it }
        val color = withContext(Dispatchers.IO) { runCatching { load(context.applicationContext, url) }.getOrNull() }
        if (color != null) cache.put(url, color)
        return color
    }

    private suspend fun load(context: Context, url: String): Int? {
        val local = when {
            url.startsWith("/") -> File(url)
            url.startsWith("file:") -> File(android.net.Uri.parse(url).path.orEmpty())
            else -> null
        }
        if (local != null) {
            if (!local.isFile) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(local.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 128) sample *= 2
            val bitmap = BitmapFactory.decodeFile(local.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            // Bu bitmap'i biz açtık; işimiz bitince biz bırakırız.
            return try { analyze(bitmap) } finally { bitmap.recycle() }
        }
        // Ağ kapağı: AsyncImage ile aynı Coil önbelleğinden gelir. Donanım bitmap'i piksel okumaya izin vermez.
        val request = ImageRequest.Builder(context).data(url).size(96, 96).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
        return analyze(bitmap)   // Coil'in önbellek bitmap'i: recycle edilmez.
    }

    private fun analyze(src: Bitmap): Int? {
        val scale = 48f / maxOf(src.width, src.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1), true) else src
        try {
            val w = small.width; val h = small.height
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            val count = IntArray(512); val sr = LongArray(512); val sg = LongArray(512); val sb = LongArray(512)
            for (c in px) {
                if ((c ushr 24) < 128) continue            // saydam pikseller sayılmaz
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
                val i = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
                count[i]++; sr[i] += r.toLong(); sg[i] += g.toLong(); sb[i] += b.toLong()
            }
            var best = -1
            for (i in count.indices) if (count[i] > 0 && (best < 0 || count[i] > count[best])) best = i
            if (best < 0) return null
            val n = count[best]
            return android.graphics.Color.rgb((sr[best] / n).toInt(), (sg[best] / n).toInt(), (sb[best] / n).toInt())
        } finally { if (small !== src) small.recycle() }
    }
}
