package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.ReminderItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** One forecast for a card (Apple WeatherKit through the REAL Worker). [kind]: sun, cloud, rain, storm, snow, fog, night. */
data class CardWeather(
    val kind: String, val label: String, val temperature: Int?, val high: Int?, val low: Int?,
    val precipitationChance: Int?, val windKmh: Int?, val attributionUrl: String
)

/**
 * Hava durumu bloğu: never added by hand. A card gets it when it has both a time (or a planned day) and a place with
 * coordinates, within WeatherKit's 10-day window. Forecasts are cached per card for 3 hours; once the card's time has
 * passed the last forecast stays as it was. Hiding it is per card, on this phone.
 */
object CardWeatherRepository {
    private const val ENDPOINT = "https://real-backend-staging.cagrigul1907.workers.dev/api/weather"
    private const val FRESH_MS = 3 * 60 * 60 * 1000L
    private const val WINDOW_MS = 10 * 24 * 60 * 60 * 1000L

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("real_card_weather", Context.MODE_PRIVATE)

    /** The time the forecast is for, and whether it is a whole day ("Tüm gün"); null without a date. */
    fun target(item: ReminderItem): Pair<Long, Boolean>? = item.alertAt?.let { it to false }
        ?: item.plannedDateEpochDay?.let { LocalDate.ofEpochDay(it).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() to true }

    fun isHidden(context: Context, id: Long) = prefs(context).getBoolean("hidden_$id", false)

    /** Whether the block has anything to show: not hidden, and inside the forecast window or a kept forecast. */
    fun mayShow(context: Context, item: ReminderItem): Boolean {
        if (isHidden(context, item.id)) return false
        val (at, _) = target(item) ?: return false
        val now = System.currentTimeMillis()
        return (at >= now - 2 * 60 * 60 * 1000L && at <= now + WINDOW_MS) || prefs(context).contains("w_${item.id}")
    }
    fun hide(context: Context, id: Long) { prefs(context).edit().putBoolean("hidden_$id", true).remove("w_$id").apply() }

    suspend fun load(context: Context, item: ReminderItem): CardWeather? = withContext(Dispatchers.IO) {
        val (at, allDay) = target(item) ?: return@withContext null
        val place = item.location ?: return@withContext null
        val lat = place.latitude ?: return@withContext null
        val lng = place.longitude ?: return@withContext null
        if (isHidden(context, item.id)) return@withContext null
        val now = System.currentTimeMillis()
        val p = prefs(context)
        val stored = runCatching { JSONObject(p.getString("w_${item.id}", null) ?: "") }.getOrNull()
            ?.takeIf { it.optLong("at") == at && abs(it.optDouble("lat") - lat) < 0.01 && abs(it.optDouble("lng") - lng) < 0.01 }
        val cached = stored?.optJSONObject("weather")?.let(::decode)
        if (cached != null && (at < now || now - stored.optLong("fetchedAt") < FRESH_MS)) return@withContext cached
        if (at < now - 2 * 60 * 60 * 1000L || at > now + WINDOW_MS) return@withContext cached
        val fresh = runCatching {
            val payload = JSONObject().put("latitude", lat).put("longitude", lng).put("at", at).put("allDay", allDay)
                .put("timezone", TimeZone.getDefault().id)
            val response = com.cagri.real.network.WorkerClient(com.cagri.real.platform.android.auth.AndroidBackend.workerTransport)
                .postJson(ENDPOINT, payload.toString(), 10_000, 15_000, readErrorBody = false)
            check(response.statusCode in 200..299) { "HTTP ${response.statusCode} from REAL Worker weather" }
            JSONObject(response.body).optJSONObject("weather")
        }.getOrNull() ?: return@withContext cached
        p.edit().putString("w_${item.id}", JSONObject().put("at", at).put("lat", lat).put("lng", lng)
            .put("fetchedAt", now).put("weather", fresh).toString()).apply()
        decode(fresh)
    }

    private fun decode(o: JSONObject): CardWeather? {
        fun int(name: String) = if (o.isNull(name) || !o.has(name)) null else o.optInt(name)
        return CardWeather(o.optString("kind").ifBlank { return null }, o.optString("label"), int("temperature"), int("high"), int("low"),
            int("precipitationChance"), int("windKmh"),
            o.optString("attributionUrl").ifBlank { "https://developer.apple.com/weatherkit/data-source-attribution/" })
    }
}

private fun weatherColors(kind: String): Pair<Color, Color> = when (kind) {
    "sun" -> Color(0xFFFFCF6B) to Color(0xFFFF7A4D)
    "cloud" -> Color(0xFFD9E6FF) to Color(0xFF8FB4F0)
    "rain" -> Color(0xFF7AB8FF) to Color(0xFF3D7FE0)
    "storm" -> Color(0xFFC9C3FF) to Color(0xFF8F8DE0)
    "snow" -> Color(0xFFFFFFFF) to Color(0xFFA9CDF0)
    "fog" -> Color(0xFFD4D8DE) to Color(0xFF8E959E)
    else -> Color(0xFFF3F0FF) to Color(0xFFA9A3EF)
}

private val BlockSurface = Color(0xFF141416)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardWeatherBlock(
    reminder: ReminderItem, reduceAnimations: Boolean, modifier: Modifier = Modifier,
    /** Small slot (half width or a short height chosen in "Sırala"): icon, temperature and condition only. */
    compact: Boolean = false,
    /** Long press → "Hava durumunu gizle"; off while the card is being edited (the grid owns the gestures then). */
    interactive: Boolean = true,
    onHide: () -> Unit = {}
) {
    val context = LocalContext.current
    val key = Triple(reminder.id, CardWeatherRepository.target(reminder), reminder.location?.latitude to reminder.location?.longitude)
    val weather by produceState<CardWeather?>(null, key) { value = CardWeatherRepository.load(context, reminder) }
    val w = weather
    val (at, allDay) = CardWeatherRepository.target(reminder) ?: return
    if (w == null) {
        // Still loading (or no forecast yet): keep the slot calm instead of jumping.
        Box(modifier.fillMaxWidth().heightIn(min = if (compact) 72.dp else 120.dp).clip(RoundedCornerShape(22.dp)).background(BlockSurface))
        return
    }
    val (top, bottom) = weatherColors(w.kind)
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val uri = LocalUriHandler.current
    val shape = RoundedCornerShape(22.dp)
    val whenText = remember(at, allDay, com.cagri.real.i18n.I18n.isEnglish) {
        val locale = com.cagri.real.i18n.I18n.locale
        if (allDay) SimpleDateFormat("EEE · '" + t("Tüm gün") + "'", locale).format(Date(at)) else SimpleDateFormat("EEE HH:mm", locale).format(Date(at))
    }
    Box(modifier.fillMaxWidth().clip(shape).background(BlockSurface).border(0.5.dp, Color.White.copy(alpha = .08f), shape)
        .drawBehind {
            // A soft light in the weather's colour behind the icon.
            val r = size.minDimension * 1.1f
            drawCircle(Brush.radialGradient(listOf(bottom.copy(alpha = .16f), Color.Transparent), Offset(size.width * .12f, 0f), r), r, Offset(size.width * .12f, 0f))
        }
        .then(if (interactive) Modifier.combinedClickable(onClick = {}, onLongClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true
        }) else Modifier)
        .padding(start = if (compact) 14.dp else 18.dp, end = if (compact) 14.dp else 18.dp, top = if (compact) 12.dp else 16.dp,
            bottom = if (compact) 10.dp else 12.dp)) {
        if (compact) Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WeatherGlyph(w.kind, top, bottom, reduceAnimations, Modifier.size(52.dp, 46.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.label, color = Color.White.copy(alpha = .7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(whenText.replaceFirstChar { it.uppercase() }, color = Color.White.copy(alpha = .45f), fontSize = 11.sp, maxLines = 1)
                }
                w.temperature?.let { Text("$it°", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp, maxLines = 1) }
            }
            Text(t("Apple Weather"), color = Color.White.copy(alpha = .4f), fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp).clickable { runCatching { uri.openUri(w.attributionUrl) } })
        } else Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(reminder.location?.placeName.orEmpty(), color = Color.White.copy(alpha = .8f), fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).border(0.5.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp))
                Spacer(Modifier.width(12.dp))
                Text(whenText.replaceFirstChar { it.uppercase() }, color = Color.White.copy(alpha = .85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WeatherGlyph(w.kind, top, bottom, reduceAnimations, Modifier.size(96.dp, 84.dp))
                    Text(w.label, color = Color.White.copy(alpha = .55f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    w.windKmh?.let { WeatherStat(StatIcon.Wind, t("{0} km/s", it)) }
                    w.precipitationChance?.let { WeatherStat(StatIcon.Drop, "%$it") }
                    if (w.high != null && w.low != null) WeatherStat(StatIcon.Thermo, "${w.high}° / ${w.low}°")
                }
                w.temperature?.let {
                    Text("$it°", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp, maxLines = 1)
                }
            }
            // Apple asks for its mark and the data sources link wherever WeatherKit data is shown.
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t("Apple Weather"), color = Color.White.copy(alpha = .4f), fontSize = 11.sp)
                Text(t("Kaynaklar"), color = Color.White.copy(alpha = .4f), fontSize = 11.sp,
                    modifier = Modifier.clickable { runCatching { uri.openUri(w.attributionUrl) } })
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(t("Hava durumunu gizle")) }, onClick = {
                menu = false; CardWeatherRepository.hide(context, reminder.id); onHide()
            })
        }
    }
}

private enum class StatIcon { Wind, Drop, Thermo }

@Composable
private fun WeatherStat(icon: StatIcon, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(14.dp)) {
            val c = Color.White.copy(alpha = .45f)
            val s = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val u = size.width / 14f
            when (icon) {
                StatIcon.Wind -> {
                    drawPath(Path().apply { moveTo(1 * u, 5 * u); lineTo(9 * u, 5 * u); cubicTo(12 * u, 5 * u, 12 * u, 1 * u, 9 * u, 1.5f * u) }, c, style = s)
                    drawPath(Path().apply { moveTo(1 * u, 9 * u); lineTo(11 * u, 9 * u); cubicTo(14 * u, 9 * u, 14 * u, 13 * u, 11 * u, 12.5f * u) }, c, style = s)
                }
                StatIcon.Drop -> drawPath(Path().apply {
                    moveTo(7 * u, 1 * u); cubicTo(11 * u, 6 * u, 12 * u, 8 * u, 12 * u, 9 * u)
                    cubicTo(12 * u, 12 * u, 9.8f * u, 13.5f * u, 7 * u, 13.5f * u); cubicTo(4.2f * u, 13.5f * u, 2 * u, 12 * u, 2 * u, 9 * u)
                    cubicTo(2 * u, 8 * u, 3 * u, 6 * u, 7 * u, 1 * u); close()
                }, c, style = s)
                StatIcon.Thermo -> {
                    drawLine(c, Offset(7 * u, 1.5f * u), Offset(7 * u, 8.5f * u), s.width, StrokeCap.Round)
                    drawCircle(c, 2.6f * u, Offset(7 * u, 11 * u), style = s)
                }
            }
        }
        Text(text, color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
    }
}

/** The animated line icon, drawn in a 192 × 168 space and scaled to the box. */
@Composable
private fun WeatherGlyph(kind: String, top: Color, bottom: Color, reduceAnimations: Boolean, modifier: Modifier) {
    val clock = rememberInfiniteTransition(label = "weather")
    val animated by clock.animateFloat(0f, 3600f, infiniteRepeatable(tween(3_600_000, easing = LinearEasing)), label = "t")
    val t = if (reduceAnimations) 0.4f else animated
    val appear = remember(kind) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(kind) { appear.animateTo(1f, tween(if (reduceAnimations) 0 else 500)) }
    Canvas(modifier) {
        val e = appear.value
        val ease = 1f - (1f - e).pow(3)
        val k = min(size.width / 192f, size.height / 168f)
        scale(k, k, pivot = Offset.Zero) {
            translate(top = (1f - ease) * 8f) {
                drawGlyph(kind, t, ease, Brush.linearGradient(listOf(top, bottom), Offset(40f, 20f), Offset(150f, 150f)))
            }
        }
    }
}

private fun cloudPath(cx: Float, cy: Float, s: Float) = Path().apply {
    fun arc(x: Float, y: Float, r: Float, from: Float, sweep: Float) =
        arcTo(Rect(Offset(cx + x * s, cy + y * s), r * s), from, sweep, false)
    moveTo(cx - 48 * s, cy + 22 * s)
    arc(-30f, 2f, 20f, 108f, 153f)
    arc(2f, -14f, 30f, 198f, 153f)
    arc(36f, 6f, 18f, 252f, 189f)
    close()
}

private fun DrawScope.drawGlyph(kind: String, t: Float, alpha: Float, brush: Brush) {
    val line = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun stroke(path: Path, a: Float = alpha, b: Brush = brush) = drawPath(path, b, alpha = a.coerceIn(0f, 1f), style = line)
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float, a: Float = alpha, b: Brush = brush) =
        drawLine(b, Offset(x1, y1), Offset(x2, y2), 7f, StrokeCap.Round, alpha = a.coerceIn(0f, 1f))
    when (kind) {
        "sun" -> {
            drawCircle(brush, 26f + sin(t * 2f) * 1.2f, Offset(96f, 84f), alpha = alpha, style = line)
            rotate(t * 0.35f * 180f / PI.toFloat(), Offset(96f, 84f)) {
                for (i in 0 until 8) rotate(i * 45f, Offset(96f, 84f)) {
                    val l = 8f + sin(t * 3f + i) * 3f
                    seg(136f, 84f, 136f + l, 84f)
                }
            }
        }
        "cloud" -> {
            val sun = Brush.linearGradient(listOf(Color(0xFFFFD27A), Color(0xFFFF9B5A)), Offset(100f, 30f), Offset(140f, 75f))
            drawCircle(sun, 17f, Offset(120f, 52f), alpha = alpha, style = line)
            rotate(t * 0.3f * 180f / PI.toFloat(), Offset(120f, 52f)) {
                for (i in 0 until 6) rotate(i * 60f, Offset(120f, 52f)) { seg(146f, 52f, 152f, 52f, b = sun) }
            }
            val path = cloudPath(88f + sin(t * 0.8f) * 4f, 96f, 1f)
            drawPath(path, BlockSurface, alpha = alpha)
            stroke(path)
        }
        "rain" -> listOf(Triple(64f, 0f, 1f), Triple(100f, .33f, 1.25f), Triple(132f, .66f, 1f)).forEachIndexed { i, (cx, p, s) ->
            val q = (t * 0.7f + p) % 1f
            val cy = 40f + q * 80f + if (i == 1) -10f else 0f
            stroke(Path().apply {
                moveTo(cx, cy - 16 * s); cubicTo(cx + 11 * s, cy - 2 * s, cx + 11 * s, cy + 12 * s, cx, cy + 12 * s)
                cubicTo(cx - 11 * s, cy + 12 * s, cx - 11 * s, cy - 2 * s, cx, cy - 16 * s)
            }, alpha * sin(q * PI.toFloat()))
        }
        "storm" -> {
            val path = cloudPath(96f, 70f, 1f)
            drawPath(path, BlockSurface, alpha = alpha)
            stroke(path)
            val m = t % 2.2f
            val flash = m < .12f || (m > .22f && m < .3f)
            val bolt = Brush.linearGradient(listOf(Color(0xFFFFE27A), Color(0xFFFFB23D)), Offset(84f, 98f), Offset(104f, 152f))
            stroke(Path().apply { moveTo(98f, 98f); lineTo(84f, 124f); lineTo(104f, 124f); lineTo(90f, 152f) }, alpha * if (flash) 1f else .55f, bolt)
        }
        "snow" -> {
            rotate(t * 0.25f * 180f / PI.toFloat(), Offset(96f, 84f)) {
                for (i in 0 until 6) rotate(i * 60f, Offset(96f, 84f)) {
                    seg(96f, 84f, 96f, 44f); seg(96f, 58f, 86f, 50f); seg(96f, 58f, 106f, 50f)
                }
            }
            for (i in 0 until 5) {
                val q = (t * 0.35f + i / 5f) % 1f
                drawCircle(Color.White, 3f, Offset(30f + i * 33f + sin(t + i) * 5f, 10f + q * 150f), alpha = alpha * sin(q * PI.toFloat()) * .8f)
            }
        }
        "fog" -> for (i in 0 until 4) {
            val o = sin(t * 0.9f + i * 1.3f) * 10f
            val l = if (i % 2 == 1) 90f else 120f
            seg(96f - l / 2 + o, 46f + i * 26f, 96f + l / 2 + o, 46f + i * 26f)
        }
        else -> {
            stroke(Path().apply {
                arcTo(Rect(Offset(92f, 84f), 36f), 63f, 234f, true)
                arcTo(Rect(Offset(112f, 72f), 28f), 261f, -162f, false)
                close()
            })
            listOf(Triple(146f, 40f, 0f), Triple(154f, 92f, 1.4f), Triple(128f, 128f, 2.6f)).forEach { (a, b, p) ->
                val z = .4f + .6f * (.5f + .5f * sin(t * 2.2f + p))
                val star = Stroke(width = 4f, cap = StrokeCap.Round)
                drawLine(brush, Offset(a - 6 * z, b), Offset(a + 6 * z, b), star.width, StrokeCap.Round, alpha = alpha * z)
                drawLine(brush, Offset(a, b - 6 * z), Offset(a, b + 6 * z), star.width, StrokeCap.Round, alpha = alpha * z)
            }
        }
    }
}
