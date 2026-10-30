package com.cagri.real.ui.poster

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

/** What the poster says under the map: the province (il) or city, its country, and the coordinates. */
data class MapPosterPlace(val latitude: Double, val longitude: Double, val title: String, val country: String, val locale: Locale)

object MapPosterRenderer {
    /** Poster canvas (4:5). The camera always works on this size so the preview frames exactly what is saved. */
    private const val CANVAS_W = 3000
    private const val CANVAS_H = 3750
    /** 3000 × 2.56 = 7680 px wide; each of the 3 × 3 snapshots is 2560 × 3200. */
    private const val HIGH_RES_FACTOR = 2.56f
    private const val GRID = 3

    /**
     * Province first for Türkiye (il), the city elsewhere; country name in the app language.
     * Falls back to the card's own place name when the phone has no geocoder or is offline.
     */
    suspend fun place(context: Context, lat: Double, lng: Double, fallback: String, appLocale: Locale): MapPosterPlace =
        withContext(Dispatchers.IO) {
            val address = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, appLocale).getFromLocation(lat, lng, 1)?.firstOrNull()
            }.getOrNull()
            val turkey = address?.countryCode.equals("TR", ignoreCase = true)
            val title = if (turkey) address?.adminArea ?: address?.locality
            else address?.locality ?: address?.subAdminArea ?: address?.adminArea
            val nameLocale = if (turkey) Locale.forLanguageTag("tr-TR") else appLocale
            MapPosterPlace(lat, lng, title?.takeIf { it.isNotBlank() } ?: fallback, address?.countryName.orEmpty(), nameLocale)
        }

    /**
     * The finished poster. [scale] < 1 renders a light preview of the same frame (same tiles, thinner pixels).
     * Null when the map could not load (offline) or the phone ran out of memory.
     */
    suspend fun render(
        context: Context, template: MapPosterTemplate, place: MapPosterPlace, scale: Float = 1f, zoom: Double = POSTER_ZOOM
    ): Bitmap? {
        val center = LatLng(place.latitude, place.longitude)
        val snap = snapshot(context, template, center, CANVAS_W, CANVAS_H, scale, zoom) ?: return null
        return runCatching {
            val bitmap = if (snap.isMutable) snap else snap.copy(Bitmap.Config.ARGB_8888, true).also { snap.recycle() }
            decorate(bitmap, template, place)
            bitmap
        }.getOrNull()
    }

    /**
     * The print poster: the same frame as the preview, drawn [HIGH_RES_FACTOR]× sharper (7680 × 9600, 8K wide).
     * One GPU snapshot that size goes past most phones' texture limit, so the frame is drawn as a 3 × 3 grid of
     * snapshots, each centred on its own part of the map, and stitched into one bitmap (pixels live in native memory).
     */
    suspend fun renderHighRes(
        context: Context, template: MapPosterTemplate, place: MapPosterPlace, zoom: Double,
        onProgress: (Float) -> Unit = {}
    ): Bitmap? {
        val factor = HIGH_RES_FACTOR
        val tileW = CANVAS_W / GRID
        val tileH = CANVAS_H / GRID
        val out = runCatching {
            Bitmap.createBitmap((CANVAS_W * factor).toInt(), (CANVAS_H * factor).toInt(), Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        // MapLibre's world is 512 × 2^zoom logical pixels wide (Web Mercator).
        val world = 512.0 * Math.pow(2.0, zoom)
        val cx = (place.longitude + 180.0) / 360.0 * world
        val sinLat = Math.sin(Math.toRadians(place.latitude))
        val cy = (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * world
        for (row in 0 until GRID) for (col in 0 until GRID) {
            val x = cx + (col + 0.5) * tileW - CANVAS_W / 2.0
            val y = cy + (row + 0.5) * tileH - CANVAS_H / 2.0
            val lng = x / world * 360.0 - 180.0
            val lat = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * y / world))))
            val tile = snapshot(context, template, LatLng(lat, lng), tileW, tileH, factor, zoom)
            if (tile == null) { out.recycle(); return null }
            val dst = android.graphics.Rect(
                (col * tileW * factor).toInt(), (row * tileH * factor).toInt(),
                ((col + 1) * tileW * factor).toInt(), ((row + 1) * tileH * factor).toInt()
            )
            canvas.drawBitmap(tile, null, dst, paint)
            tile.recycle()
            onProgress((row * GRID + col + 1f) / (GRID * GRID))
        }
        return runCatching { decorate(out, template, place); out }.getOrElse { out.recycle(); null }
    }

    private suspend fun snapshot(
        context: Context, template: MapPosterTemplate, center: LatLng, width: Int, height: Int, scale: Float, zoom: Double
    ): Bitmap? =
        withContext(Dispatchers.Main) {
            runCatching {
                MapLibre.getInstance(context.applicationContext)
                val options = MapSnapshotter.Options(width, height)
                    .withStyleBuilder(Style.Builder().fromJson(template.styleJson()))
                    .withPixelRatio(scale)
                    .withLogo(false)
                    .withCameraPosition(CameraPosition.Builder().target(center).zoom(zoom).build())
                val snapshotter = object : MapSnapshotter(context.applicationContext, options) {
                    override fun addOverlay(mapSnapshot: MapSnapshot) = Unit
                }
                suspendCancellableCoroutine<Bitmap?> { cont ->
                    cont.invokeOnCancellation { runCatching { snapshotter.cancel() } }
                    snapshotter.start(
                        { snap -> if (cont.isActive) cont.resume(snap.bitmap) },
                        { _ -> if (cont.isActive) cont.resume(null) }
                    )
                }
            }.getOrNull()
        }

    /** Soft fades at the top and under the title, then the title block and the OpenStreetMap credit. */
    private fun decorate(bitmap: Bitmap, template: MapPosterTemplate, place: MapPosterPlace) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val canvas = Canvas(bitmap)
        val bg = template.background.toInt()
        fun bgAlpha(a: Float) = (bg and 0x00FFFFFF) or ((a * 255).toInt() shl 24)
        val fade = Paint()
        fade.shader = LinearGradient(0f, 0f, 0f, h * 0.16f, bgAlpha(0.75f), bgAlpha(0f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.16f, fade)
        fade.shader = LinearGradient(
            0f, h * 0.62f, 0f, h,
            intArrayOf(bgAlpha(0f), bgAlpha(0.82f), bgAlpha(0.94f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.62f, w, h, fade)

        val ink = template.text.toInt()
        val sans = Typeface.create("sans-serif", Typeface.BOLD)
        val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val mono = Typeface.MONOSPACE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textAlign = Paint.Align.CENTER }

        // Title: wide-spaced capitals, shrunk until it fits the poster's width.
        val title = place.title.uppercase(place.locale)
        paint.typeface = sans
        paint.letterSpacing = 0.55f
        paint.textSize = w * 0.085f
        while (paint.measureText(title) > w * 0.86f && paint.textSize > w * 0.03f) paint.textSize *= 0.95f
        canvas.drawText(title, w / 2f, h * 0.842f, paint)

        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; alpha = 200; strokeWidth = w * 0.0008f }
        canvas.drawLine(w * 0.40f, h * 0.868f, w * 0.60f, h * 0.868f, rule)

        if (place.country.isNotBlank()) {
            paint.typeface = light
            paint.letterSpacing = 0f
            paint.textSize = w * 0.027f
            canvas.drawText(place.country.uppercase(place.locale), w / 2f, h * 0.902f, paint)
        }

        paint.typeface = mono
        paint.textSize = w * 0.0165f
        paint.alpha = 215
        canvas.drawText(coordinates(place.latitude, place.longitude), w / 2f, h * 0.935f, paint)

        paint.textSize = w * 0.0115f
        paint.alpha = 165
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("© OpenStreetMap contributors", w * 0.975f, h * 0.978f, paint)
        paint.textAlign = Paint.Align.LEFT
        // Signature in the logo's typeface.
        paint.typeface = com.cagri.real.ui.RealBrandType.bold
        paint.letterSpacing = -0.03f
        canvas.drawText("Real", w * 0.025f, h * 0.978f, paint)
    }

    fun coordinates(lat: Double, lng: Double): String = String.format(
        Locale.US, "%.4f° %s / %.4f° %s", abs(lat), if (lat >= 0) "N" else "S", abs(lng), if (lng >= 0) "E" else "W"
    )

    /** Pictures/REAL in the phone's gallery. Android 9 and older need a storage permission REAL does not ask for: null there. */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, name: String): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/REAL")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        val ok = runCatching { resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true }
            .getOrDefault(false)
        if (!ok) { resolver.delete(uri, null, null); return@withContext null }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        uri
    }

    /** The poster as a file in CameraX's private cache, so the card imports it like a camera photo. */
    suspend fun cameraCacheFile(context: Context, bitmap: Bitmap, name: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "real_camera").apply { mkdirs() }
            File(dir, "$name-${System.currentTimeMillis()}.jpg").also { file ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
            }
        }.getOrNull()
    }

    /** A cache copy for the share sheet (served by the attachments FileProvider). */
    suspend fun shareFile(context: Context, bitmap: Bitmap, name: String): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "map_posters").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "$name.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.attachments", file)
        }.getOrNull()
    }
}
