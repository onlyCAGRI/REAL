package com.cagri.real.ui.poster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.cagri.real.i18n.I18n
import com.cagri.real.i18n.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** A sky poster look: page, the sky disc inside the ring, stars, constellation lines, ring and title ink. */
data class SkyPosterTemplate(
    val id: String,
    /** Turkish key; shown through t(). */
    val name: String,
    val background: Long,
    val sky: Long,
    val star: Long,
    val line: Long,
    val ring: Long,
    val text: Long,
    val dark: Boolean
)

val SKY_POSTER_TEMPLATES = listOf(
    SkyPosterTemplate("midnight", "Gece mavisi", 0xFF0B1530, 0xFF0E1A3A, 0xFFFFFFFF, 0xFF7F95CC, 0xFFDDB65A, 0xFFDDB65A, true),
    SkyPosterTemplate("night", "Gece", 0xFF000000, 0xFF000000, 0xFFFFFFFF, 0xFF8A8A8A, 0xFFFFFFFF, 0xFFFFFFFF, true),
    SkyPosterTemplate("ink", "Mürekkep", 0xFFFFFFFF, 0xFFFFFFFF, 0xFF111111, 0xFF6A6A6A, 0xFF111111, 0xFF111111, false),
    SkyPosterTemplate("paper", "Kâğıt", 0xFFF8F6F3, 0xFF1C2233, 0xFFFFFFFF, 0xFF8E98B8, 0xFF1C2233, 0xFF1C2233, false),
    SkyPosterTemplate("wine", "Bordo", 0xFF1F0A12, 0xFF2A0E19, 0xFFF7DCC5, 0xFFA04A64, 0xFFF7DCC5, 0xFFF7DCC5, true),
    SkyPosterTemplate("ocean", "Okyanus", 0xFF06202B, 0xFF082C3A, 0xFFE6FFFD, 0xFF3BB8C0, 0xFF9FF5F0, 0xFF9FF5F0, true),
    SkyPosterTemplate("neon", "Neon", 0xFF0B0D18, 0xFF120E26, 0xFFFFFFFF, 0xFFC45BFF, 0xFFFF4FA3, 0xFFFFD000, true),
    SkyPosterTemplate("espresso", "Espresso", 0xFF1E110C, 0xFF2A1710, 0xFFFFE3B0, 0xFFB2552B, 0xFFFFD58E, 0xFFFFD58E, true),
    SkyPosterTemplate("forest", "Orman", 0xFF16241B, 0xFF1B2E22, 0xFFF2EBC8, 0xFF7FA07A, 0xFFD9C27A, 0xFFE8DDB0, true),
    SkyPosterTemplate("sand", "Kum", 0xFFE3D7C9, 0xFF3F3029, 0xFFF5E6C8, 0xFFA0705F, 0xFF3F3029, 0xFF3F3029, false),
    SkyPosterTemplate("sage", "Adaçayı", 0xFFEEF3EE, 0xFF24493C, 0xFFF3F7EE, 0xFF86AB99, 0xFF24493C, 0xFF24493C, false),
    SkyPosterTemplate("rose", "Gül", 0xFFFBF0F1, 0xFF5A1F35, 0xFFFFE9EE, 0xFFD46A88, 0xFF9C2F52, 0xFF9C2F52, false)
)

/**
 * The sky over a card's place at the card's moment, as a round star chart: zenith in the middle, horizon on the
 * ring, north up and east on the left (the sky seen lying on your back). Drawn on a plain Canvas, so the 8K print
 * needs no GPU tiling. Stars: Yale Bright Star Catalog (public domain); figures: d3-celestial (BSD).
 */
object SkyPosterRenderer {
    private const val CANVAS_W = 3000
    private const val CANVAS_H = 3750
    const val HIGH_RES = 2.56f

    private class Catalog(val stars: FloatArray, val lines: List<FloatArray>)
    @Volatile private var catalog: Catalog? = null

    /** assets/sky/stars.csv: "ra,dec,mag" per star; assets/sky/lines.csv: "ra dec;ra dec;…" per figure stroke. */
    private suspend fun catalog(context: Context): Catalog? = catalog ?: withContext(Dispatchers.IO) {
        runCatching {
            val stars = ArrayList<Float>(15_000)
            context.assets.open("sky/stars.csv").bufferedReader().useLines { rows ->
                rows.forEach { row ->
                    val p = row.split(',')
                    if (p.size >= 3) { stars += p[0].toFloat(); stars += p[1].toFloat(); stars += p[2].toFloat() }
                }
            }
            val lines = context.assets.open("sky/lines.csv").bufferedReader().useLines { rows ->
                rows.mapNotNull { row ->
                    val pts = row.split(';').mapNotNull { pair ->
                        pair.trim().split(' ').takeIf { it.size == 2 }?.let { it[0].toFloat() to it[1].toFloat() }
                    }
                    if (pts.size < 2) null else FloatArray(pts.size * 2) { i -> if (i % 2 == 0) pts[i / 2].first else pts[i / 2].second }
                }.toList()
            }
            Catalog(stars.toFloatArray(), lines).also { catalog = it }
        }.getOrNull()
    }

    suspend fun render(
        context: Context, template: SkyPosterTemplate, place: MapPosterPlace, epochMillis: Long, scale: Float
    ): Bitmap? {
        val data = catalog(context) ?: return null
        return withContext(Dispatchers.Default) {
            runCatching {
                val bitmap = Bitmap.createBitmap((CANVAS_W * scale).toInt(), (CANVAS_H * scale).toInt(), Bitmap.Config.ARGB_8888)
                draw(Canvas(bitmap), bitmap.width.toFloat(), bitmap.height.toFloat(), template, place, epochMillis, data)
                bitmap
            }.getOrNull()
        }
    }

    private fun draw(
        canvas: Canvas, w: Float, h: Float, template: SkyPosterTemplate, place: MapPosterPlace, epochMillis: Long, data: Catalog
    ) {
        canvas.drawColor(template.background.toInt())
        val cx = w / 2f
        val cy = h * 0.40f
        val radius = w * 0.40f
        val jd = SkyMath.julian(epochMillis)
        val lst = SkyMath.localSidereal(jd, place.longitude)

        /** Stereographic from the zenith: the horizon lands exactly on the ring. Null below the horizon. */
        fun project(ra: Double, dec: Double, margin: Double = 0.0): Pair<Float, Float>? {
            val (alt, az) = SkyMath.altAz(ra, dec, place.latitude, lst)
            if (alt < -margin) return null
            val r = radius * tan(Math.toRadians(90.0 - alt) / 2).toFloat()
            val a = Math.toRadians(az)
            return (cx - r * sin(a).toFloat()) to (cy - r * cos(a).toFloat())
        }

        val disc = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = template.sky.toInt() }
        canvas.drawPath(disc, fill)
        canvas.save()
        canvas.clipPath(disc)

        // Faint altitude rings (30°, 60°) and the four cardinal spokes.
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = w * 0.0006f; color = template.line.toInt(); alpha = 40
        }
        listOf(30.0, 60.0).forEach { alt -> canvas.drawCircle(cx, cy, radius * tan(Math.toRadians(90.0 - alt) / 2).toFloat(), grid) }
        canvas.drawLine(cx - radius, cy, cx + radius, cy, grid)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, grid)

        val figure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = w * 0.0009f; color = template.line.toInt(); alpha = 150
            strokeCap = Paint.Cap.ROUND
        }
        data.lines.forEach { pts ->
            var prev: Pair<Float, Float>? = null
            for (i in pts.indices step 2) {
                val p = project(pts[i].toDouble(), pts[i + 1].toDouble(), margin = 4.0)
                if (p != null && prev != null) canvas.drawLine(prev.first, prev.second, p.first, p.second, figure)
                prev = p
            }
        }

        val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = template.star.toInt() }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        val starRgb = template.star.toInt() and 0x00FFFFFF
        val s = data.stars
        for (i in s.indices step 3) {
            val mag = s[i + 2]
            // 5.5 keeps ~2,800 stars: full but calm, the figures stay readable.
            if (mag > 5.5f) continue
            val p = project(s[i].toDouble(), s[i + 1].toDouble()) ?: continue
            val r = (w * 0.0011f * (1f + (5.0f - mag) * 0.42f)).coerceAtLeast(w * 0.0004f)
            if (mag < 1.5f) {
                // Soft halo fading to nothing, instead of a flat grey disc.
                glow.shader = android.graphics.RadialGradient(
                    p.first, p.second, r * 4.5f, (0x55 shl 24) or starRgb, starRgb, android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawCircle(p.first, p.second, r * 4.5f, glow)
            }
            canvas.drawCircle(p.first, p.second, r, star)
        }

        val moon = SkyMath.moon(jd)
        project(moon.ra, moon.dec)?.let { (mx, my) -> drawMoon(canvas, mx, my, w * 0.014f, moon, template) }
        canvas.restore()

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = template.ring.toInt() }
        ring.strokeWidth = w * 0.0022f
        canvas.drawCircle(cx, cy, radius, ring)
        ring.strokeWidth = w * 0.0008f; ring.alpha = 140
        canvas.drawCircle(cx, cy, radius * 1.025f, ring)

        val ink = template.text.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }
        paint.textSize = w * 0.017f
        val (n, e, so, wst) = if (I18n.isEnglish) listOf("N", "E", "S", "W") else listOf("K", "D", "G", "B")
        val off = radius * 1.075f
        val mid = paint.textSize * 0.35f
        canvas.drawText(n, cx, cy - off + mid, paint)
        canvas.drawText(so, cx, cy + off + mid, paint)
        canvas.drawText(e, cx - off, cy + mid, paint)
        canvas.drawText(wst, cx + off, cy + mid, paint)

        // Title block, as on the map poster: place, rule, the moment, coordinates and the Moon.
        val title = place.title.uppercase(place.locale)
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.letterSpacing = 0.55f
        paint.textSize = w * 0.075f
        while (paint.measureText(title) > w * 0.86f && paint.textSize > w * 0.03f) paint.textSize *= 0.95f
        canvas.drawText(title, w / 2f, h * 0.835f, paint)
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; alpha = 200; strokeWidth = w * 0.0008f }
        canvas.drawLine(w * 0.40f, h * 0.86f, w * 0.60f, h * 0.86f, rule)

        paint.letterSpacing = 0.12f
        paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        paint.textSize = w * 0.026f
        val moment = SimpleDateFormat("d MMMM yyyy · HH:mm", I18n.locale).format(Date(epochMillis))
        canvas.drawText(moment.uppercase(I18n.locale), w / 2f, h * 0.893f, paint)

        paint.letterSpacing = 0f
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = w * 0.0165f
        paint.alpha = 215
        canvas.drawText(MapPosterRenderer.coordinates(place.latitude, place.longitude), w / 2f, h * 0.922f, paint)
        // Below the horizon the Moon is not on the chart; say so, or the phase line reads like a missing Moon.
        val moonUp = SkyMath.altAz(moon.ra, moon.dec, place.latitude, lst).first > 0
        canvas.drawText(
            "${t(SkyMath.phaseName(moon))} · %${(moon.illumination * 100).toInt()}" + if (moonUp) "" else " · " + t("ufkun altında"),
            w / 2f, h * 0.945f, paint
        )

        paint.textSize = w * 0.0115f
        paint.alpha = 165
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Yale Bright Star Catalog · d3-celestial", w * 0.975f, h * 0.978f, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("REAL", w * 0.025f, h * 0.978f, paint)
    }

    /**
     * The Moon with its real phase: the lit half-disc plus (gibbous) or minus (crescent) the terminator ellipse.
     * Waxing is lit on the right, as seen from the northern hemisphere.
     */
    private fun drawMoon(canvas: Canvas, x: Float, y: Float, r: Float, moon: SkyMath.Moon, template: SkyPosterTemplate) {
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = template.star.toInt(); alpha = 40 }
        val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = template.star.toInt() }
        canvas.drawCircle(x, y, r, dark)
        val k = moon.illumination.toFloat()
        val half = Path().apply {
            addArc(RectF(x - r, y - r, x + r, y + r), if (moon.waxing) -90f else 90f, 180f)
            close()
        }
        val ellipse = RectF(x - r * abs(1 - 2 * k), y - r, x + r * abs(1 - 2 * k), y + r)
        canvas.save()
        if (k >= 0.5f) {
            canvas.drawPath(half, lit)
            canvas.drawOval(ellipse, lit)
        } else {
            canvas.clipPath(half)
            canvas.drawPath(half, lit)
            canvas.drawOval(ellipse, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = template.sky.toInt() })
            canvas.drawCircle(x, y, r, dark)
        }
        canvas.restore()
        // A soft halo so even a thin crescent reads at poster size.
        canvas.drawCircle(x, y, r * 1.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = template.star.toInt(); alpha = 22 })
    }
}
