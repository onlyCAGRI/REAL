package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.ReminderLocation
import org.maplibre.android.MapLibre
import androidx.compose.ui.graphics.graphicsLayer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter

fun openReminderLocationInMaps(context: Context, place: ReminderLocation): Boolean {
    return try {
        val original = place.mapsUrl?.takeIf { it.startsWith("https://") }
        val intent = if (original != null) Intent(Intent.ACTION_VIEW, Uri.parse(original)) else {
            val lat = place.latitude ?: return false
            val lng = place.longitude ?: return false
            val label = Uri.encode("$lat,$lng(${place.placeName})")
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$label"))
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) { false }
}





/** Short name for the map's corner: the place's name, or the street when an empty spot was picked. */
fun reminderLocationLabel(place: ReminderLocation): String? {
    val name = place.placeName.trim()
    val street = place.address.split(',').map { it.trim() }.firstOrNull { it.isNotBlank() }
    return when {
        name.isNotBlank() && name != "Seçilen yer" && name != place.address.trim() && name.any { it.isLetter() } -> name
        street != null -> street
        name.isNotBlank() -> name
        else -> null
    }
}

/**
 * The look of the card map and the location picker, chosen in Settings. Açık is the coloured map (green parks,
 * blue sea) as Apple Maps shows it, Koyu the dark one; both name the places (restaurants, shops…) unless Sade is on.
 * The base style is fetched once and patched: Sade drops the place labels, Koyu gets its own (it has none).
 */
object MapAppearance {
    private const val PREFS = "real_map_prefs"
    private const val KEY_DARK = "map_dark"
    private const val KEY_PLAIN = "map_plain"
    private const val LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    private const val DARK = "https://tiles.openfreemap.org/styles/dark"
    private var loaded = false
    private val darkState = androidx.compose.runtime.mutableStateOf(false)
    private val plainState = androidx.compose.runtime.mutableStateOf(false)
    private val baseJson = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun prefs(context: android.content.Context) =
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    private fun load(context: android.content.Context) {
        if (loaded) return
        val p = prefs(context)
        darkState.value = p.getBoolean(KEY_DARK, false)
        plainState.value = p.getBoolean(KEY_PLAIN, false)
        loaded = true
    }

    fun isDark(context: android.content.Context): Boolean { load(context); return darkState.value }
    fun isPlain(context: android.content.Context): Boolean { load(context); return plainState.value }

    fun setDark(context: android.content.Context, dark: Boolean) {
        load(context); prefs(context).edit().putBoolean(KEY_DARK, dark).apply(); darkState.value = dark
    }

    fun setPlain(context: android.content.Context, plain: Boolean) {
        load(context); prefs(context).edit().putBoolean(KEY_PLAIN, plain).apply(); plainState.value = plain
    }

    /** Changes whenever the look does: a key for re-rendering snapshots. */
    fun key(context: android.content.Context): String = "${isDark(context)}-${isPlain(context)}"

    fun placeholder(context: android.content.Context): Color = if (isDark(context)) Color(0xFF131820) else Color(0xFFE8E4DC)

    /** The style for the current look; the plain URL style when the base JSON cannot be fetched. */
    suspend fun styleBuilder(context: android.content.Context): Style.Builder {
        val dark = isDark(context)
        val plain = isPlain(context)
        val url = if (dark) DARK else LIGHT
        if (dark && plain) return Style.Builder().fromUri(url)
        val json = baseStyle(context, url) ?: return Style.Builder().fromUri(url)
        val patched = runCatching {
            val style = org.json.JSONObject(json)
            val layers = style.getJSONArray("layers")
            val kept = org.json.JSONArray()
            for (i in 0 until layers.length()) {
                val layer = layers.getJSONObject(i)
                if (plain && layer.optString("source-layer") == "poi") continue
                kept.put(layer)
            }
            if (dark) DARK_PLACE_LAYERS.forEach { kept.put(org.json.JSONObject(it)) }
            style.put("layers", kept).toString()
        }.getOrNull() ?: return Style.Builder().fromUri(url)
        return Style.Builder().fromJson(patched)
    }

    private suspend fun baseStyle(context: android.content.Context, url: String): String? {
        baseJson[url]?.let { return it }
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = java.io.File(context.cacheDir, "map_style_" + url.substringAfterLast('/') + ".json")
            val fetched = runCatching {
                (java.net.URL(url).openConnection() as java.net.HttpURLConnection).run {
                    connectTimeout = 6_000; readTimeout = 8_000
                    try { inputStream.bufferedReader().readText() } finally { disconnect() }
                }
            }.getOrNull()?.takeIf { it.trimStart().startsWith("{") }
            if (fetched != null) runCatching { file.writeText(fetched) }
            (fetched ?: runCatching { file.readText() }.getOrNull())?.also { baseJson[url] = it }
        }
    }

    /** Place names for the dark map: a small teal dot and grey name, from zoom 14 so streets stay readable. */
    private val DARK_PLACE_LAYERS = listOf(
        """{"id":"real-poi-dot","type":"circle","source":"openmaptiles","source-layer":"poi","minzoom":14,
            "filter":["all",["has","name"],["<=",["get","rank"],24]],
            "paint":{"circle-radius":2.6,"circle-color":"#8ED7C4","circle-opacity":0.85}}""",
        """{"id":"real-poi-name","type":"symbol","source":"openmaptiles","source-layer":"poi","minzoom":14,
            "filter":["all",["has","name"],["<=",["get","rank"],24]],
            "layout":{"text-field":["coalesce",["get","name:latin"],["get","name"]],"text-font":["Noto Sans Regular"],
                "text-size":11,"text-anchor":"top","text-offset":[0,0.5],"text-max-width":8},
            "paint":{"text-color":"#C9CED6","text-halo-color":"#101318","text-halo-width":1.2}}"""
    )
}

@Composable
fun ReminderLocationMap(place: ReminderLocation, modifier: Modifier = Modifier, onOpen: () -> Unit = {}) {
    ReminderLocationMap(place, modifier, cameraZoom = null, onOpen = onOpen)
}

/** Optional camera control for Pano; original card callers keep their camera behavior. */
@Composable
fun ReminderLocationMap(
    place: ReminderLocation, modifier: Modifier, cameraZoom: Double?, onOpen: () -> Unit = {},
    /** Near the card's time: the driving route from the phone to the place, drawn on the map (both ends in view). */
    route: com.cagri.real.notifications.DepartureReminder.Route? = null,
    /** The route line draws itself from the phone to the place once per entry; off → shown whole at once. */
    animateRoute: Boolean = true,
    /** Live pinch preview: scales the current snapshot until the re-rendered one arrives. */
    previewScale: (() -> Float)? = null,
    /** Called each time a freshly rendered snapshot is on screen. */
    onSnapshotShown: (() -> Unit)? = null
) {
    val latitude = place.latitude ?: return
    val longitude = place.longitude ?: return
    val context = LocalContext.current

    var snapshotBitmap by remember(latitude, longitude) { mutableStateOf<Bitmap?>(null) }
    var routeOverlay by remember(latitude, longitude) { mutableStateOf<RouteOverlay?>(null) }
    val routeProgress = remember(latitude, longitude) { Animatable(0f) }
    var routePlayed by remember(latitude, longitude) { mutableStateOf(false) }
    LaunchedEffect(routeOverlay != null) {
        if (routeOverlay == null) return@LaunchedEffect
        if (!animateRoute || routePlayed) { routeProgress.snapTo(1f); return@LaunchedEffect }
        routePlayed = true
        routeProgress.snapTo(0f)
        routeProgress.animateTo(1f, tween(1400, delayMillis = 150, easing = FastOutSlowInEasing))
    }
    var width by remember { mutableIntStateOf(0) }
    var height by remember { mutableIntStateOf(0) }

    Box(
        modifier
            .background(MapAppearance.placeholder(context))
            .clickable(onClick = onOpen)
            .onSizeChanged {
                width = it.width
                height = it.height
            }
    ) {
        val mapLook = MapAppearance.key(context)
        LaunchedEffect(width, height, latitude, longitude, cameraZoom, route, mapLook) {
            if (width <= 0 || height <= 0) return@LaunchedEffect

            MapLibre.getInstance(context.applicationContext)

            // Rendered in dp at the screen's density (as Apple Maps), not 1:1 in pixels: the map reads as close
            // as on iOS and its street and place names are legible instead of tiny.
            val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
            val options = MapSnapshotter.Options((width / density).toInt().coerceAtLeast(1), (height / density).toInt().coerceAtLeast(1))
                .withPixelRatio(density)
                .withStyleBuilder(MapAppearance.styleBuilder(context))
                .withLogo(false)
            if (route == null) options.withCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(latitude, longitude))
                        .zoom(cameraZoom?.takeIf { it.isFinite() }?.coerceIn(1.0,19.0) ?: if (place.approximateCoordinates) 15.10 else 15.35)
                        .build()
                )
            else options.withRegion(routeBounds(route, latitude, longitude))

            
            
            
            val snapshotter = object : MapSnapshotter(context, options) {
                override fun addOverlay(mapSnapshot: MapSnapshot) = Unit
            }
            var active = true
            try {
            snapshotter.start(object : MapSnapshotter.SnapshotReadyCallback {
                override fun onSnapshotReady(snapshot: MapSnapshot) {
                    if (!active) return
                    snapshotBitmap = snapshot.bitmap
                    routeOverlay = route?.let { routeOverlay(snapshot, it, latitude, longitude, context.resources.displayMetrics.density) }
                    onSnapshotShown?.invoke()
                }
            })
            kotlinx.coroutines.awaitCancellation()
            } finally { active = false; snapshotter.cancel() }
        }

        snapshotBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = t("Harita görünümü"),
                modifier = Modifier.fillMaxSize().then(
                    if (previewScale == null) Modifier
                    else Modifier.graphicsLayer { val s = previewScale(); scaleX = s; scaleY = s }
                ),
                contentScale = ContentScale.Crop
            )
        }

        // The route over the snapshot, in the bitmap's pixel space (same centre-crop as the Image above).
        routeOverlay?.let { overlay ->
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val s = maxOf(size.width / overlay.width, size.height / overlay.height)
                translate((size.width - overlay.width * s) / 2, (size.height - overlay.height * s) / 2) {
                    scale(s, s, pivot = Offset.Zero) { drawRouteOverlay(overlay, routeProgress.value) }
                }
            }
        }

        // The place: a small white Apple-style pin at the centre (the camera is centred on it; with a route the pin is
        // drawn on the snapshot at the place).
        if (route == null) androidx.compose.foundation.Canvas(Modifier.align(Alignment.Center).size(width = 18.dp, height = 40.dp)) {
            val r = 7.dp.toPx()
            val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2 - r - 4.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.85f), c, androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2), 1.5.dp.toPx())
            drawCircle(Color.Black.copy(alpha = 0.35f), 1.6.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2))
            drawCircle(Color.Black.copy(alpha = 0.45f), r + 1.5.dp.toPx(), c)
            drawCircle(Color.White, r, c)
            drawCircle(Color(0xFF131820), 2.dp.toPx(), c)
        }
        Text(
            text = t("© OpenStreetMap contributors · © OpenMapTiles"),
            color = Color.White.copy(alpha = 0.22f),
            fontSize = 4.25.sp,
            lineHeight = 5.5.sp,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 9.dp, bottom = 7.dp)
        )
    }
}

/** Both ends of the route and every point on it, with a margin so the line never touches the edge. */
private fun routeBounds(route: com.cagri.real.notifications.DepartureReminder.Route, lat: Double, lng: Double): org.maplibre.android.geometry.LatLngBounds {
    val lats = route.points.map { it.first } + route.origin.first + lat
    val lngs = route.points.map { it.second } + route.origin.second + lng
    val padLat = (lats.max() - lats.min()).coerceAtLeast(0.002) * 0.18
    val padLng = (lngs.max() - lngs.min()).coerceAtLeast(0.002) * 0.18
    return org.maplibre.android.geometry.LatLngBounds.from(lats.max() + padLat * 1.6, lngs.max() + padLng, lats.min() - padLat, lngs.min() - padLng)
}

/** The route in snapshot pixels: line points, the phone and the place, plus the bitmap size they belong to. */
private class RouteOverlay(val width: Float, val height: Float, val points: List<Offset>, val origin: Offset, val place: Offset, val density: Float)

private fun routeOverlay(snapshot: MapSnapshot, route: com.cagri.real.notifications.DepartureReminder.Route, lat: Double, lng: Double, density: Float): RouteOverlay {
    fun px(a: Double, b: Double) = snapshot.pixelForLatLng(LatLng(a, b)).let { Offset(it.x, it.y) }
    return RouteOverlay(
        snapshot.bitmap.width.toFloat(), snapshot.bitmap.height.toFloat(),
        route.points.map { (a, b) -> px(a, b) }, px(route.origin.first, route.origin.second), px(lat, lng), density
    )
}

/** Apple Maps–style route: blue line with a dark edge (drawn up to [progress] from the phone), the phone as a blue dot, the place as the white pin. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRouteOverlay(overlay: RouteOverlay, progress: Float) {
    val d = overlay.density
    val full = androidx.compose.ui.graphics.Path()
    overlay.points.forEachIndexed { i, p -> if (i == 0) full.moveTo(p.x, p.y) else full.lineTo(p.x, p.y) }
    val line = if (progress >= 1f) full else androidx.compose.ui.graphics.Path().also { part ->
        val measure = PathMeasure().apply { setPath(full, false) }
        if (progress > 0f) measure.getSegment(0f, measure.length * progress, part, true)
    }
    drawPath(line, Color(0xFF0B2A5C), style = Stroke(7.5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(line, Color(0xFF5B8DEF), style = Stroke(5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(Color.White, 8.5f * d, overlay.origin)
    drawCircle(Color(0xFF5B8DEF), 6f * d, overlay.origin)
    val head = Offset(overlay.place.x, overlay.place.y - 11f * d)
    drawLine(Color.White, overlay.place, head, 1.5f * d, cap = StrokeCap.Round)
    drawCircle(Color(0x73000000), 8.5f * d, head)
    drawCircle(Color.White, 7f * d, head)
    drawCircle(Color(0xFF131820), 2f * d, head)
}
