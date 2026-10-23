package com.cagri.real.storage

import com.cagri.real.i18n.t
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import java.io.File

/** Only called for explicit photo imports, before model/blob reservation. Never touches originals. */
internal object PhotoOptimizer {
    const val MAX_EDGE = 2048
    data class Output(val mime: String, val extension: String)
    private val lock = Any()
    fun optimize(source: File, output: File): Output? = synchronized(lock) { optimizeLocked(source, output) }
    private fun optimizeLocked(source: File, output: File): Output? {
        if (animated(source)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp")) return null
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000L) { t("Fotoğraf boyutu desteklenmiyor.") }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2 || (bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > 4_000_000L) sample *= 2
        val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error(t("Fotoğraf okunamadı."))
        var oriented: Bitmap? = null; var resized: Bitmap? = null
        try {
            val m = Matrix()
            when (runCatching { ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(1)) {
                2 -> m.setScale(-1f, 1f)
                3 -> m.setRotate(180f)
                4 -> m.setScale(1f, -1f)
                5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
                6 -> m.setRotate(90f)
                7 -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
                8 -> m.setRotate(-90f)
            }
            oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            val image = checkNotNull(oriented)
            val ratio = minOf(1.0, MAX_EDGE.toDouble() / maxOf(image.width, image.height))
            resized = Bitmap.createScaledBitmap(image, maxOf(1, (image.width * ratio).toInt()), maxOf(1, (image.height * ratio).toInt()), true)
            val bitmap = checkNotNull(resized)
            val alpha = bitmap.hasAlpha()
            val format = if (!alpha) Bitmap.CompressFormat.JPEG else if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.PNG
            output.outputStream().use { check(bitmap.compress(format, if (alpha) 100 else 85, it)); it.fd.sync() }
            return if (!alpha) Output("image/jpeg", "jpg") else if (Build.VERSION.SDK_INT >= 30) Output("image/webp", "webp") else Output("image/png", "png")
        } finally { listOfNotNull(decoded, oriented, resized).distinct().forEach { it.recycle() } }
    }
    /** GIF magic, WebP animation flag and PNG acTL: do not flatten animated input. */
    internal fun animated(file: File): Boolean = java.io.RandomAccessFile(file, "r").use { f ->
        val header = ByteArray(minOf(32L, f.length()).toInt()); f.readFully(header)
        fun ascii(start: Int, length: Int) = if (start + length <= header.size) String(header, start, length, Charsets.US_ASCII) else ""
        if (ascii(0, 3) == "GIF") return@use true
        if (ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP") return@use ascii(12, 4) == "VP8X" && header.size > 20 && (header[20].toInt() and 2) != 0
        if (header.size >= 8 && header[0] == 0x89.toByte() && ascii(1, 3) == "PNG") {
            var position = 8L
            while (position + 12 <= f.length()) {
                f.seek(position); val length = f.readInt().toLong() and 0xffffffffL
                val type = ByteArray(4); f.readFully(type); val name = String(type, Charsets.US_ASCII)
                if (name == "acTL") return@use true
                if (name == "IDAT" || name == "IEND") break
                if (length > f.length() - position - 12) break
                position += length + 12
            }
        }
        false
    }
}
