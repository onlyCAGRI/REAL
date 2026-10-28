package com.cagri.real.ui.components

import android.content.Context
import android.location.Location
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cagri.real.data.ReminderItem
import com.cagri.real.notifications.DepartureReminder
import com.cagri.real.notifications.LocationReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume

/**
 * A real OpenFreeMap snapshot for the "Varınca / Ayrılınca" focus intro.
 *
 * - Varınca: the driving route (the same Worker route as the card's "Trafikle X dk" map) from where the phone came
 *   from to the place, projected to pixels so it can be drawn progressively as a white line.
 * - Ayrılınca: just the phone's current position (or the place) with a pulsing dot.
 */
internal class RealIntroMap(
    val image: ImageBitmap,
    /** Route polyline in snapshot pixels (empty when there is no route). */
    val route: List<Offset>,
    val origin: Offset?,
    val destination: Offset
)

internal object RealIntroMapLoader {
    private const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
    private val cache = HashMap<String, RealIntroMap>()

    private fun distance(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Float {
        val out = FloatArray(1)
        Location.distanceBetween(aLat, aLng, bLat, bLng, out)
        return out[0]
    }

    suspend fun load(
        context: Context,
        reminder: ReminderItem,
        onExit: Boolean,
        widthPx: Int,
        heightPx: Int
    ): RealIntroMap? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val placeLat = reminder.locationTrigger?.latitude ?: reminder.location?.latitude ?: return null
        val placeLng = reminder.locationTrigger?.longitude ?: reminder.location?.longitude ?: return null
        val key = "${reminder.id}:$onExit:$widthPx:$heightPx"
        cache[key]?.let { return it }

        val app = context.applicationContext
        val here = runCatching { DepartureReminder.currentLocation(app) }.getOrNull()

        val result = if (onExit) {
            // Ayrılınca: the instant position is enough (falls back to the place itself).
            val (lat, lng) = if (here != null && distance(here.latitude, here.longitude, placeLat, placeLng) < 5_000f) {
                here.latitude to here.longitude
            } else {
                placeLat to placeLng
            }
            snapshot(app, widthPx, heightPx, center = LatLng(lat, lng), bounds = null)?.let { snap ->
                val d = snap.pixelForLatLng(LatLng(lat, lng))
                RealIntroMap(snap.bitmap.asImageBitmap(), emptyList(), null, Offset(d.x, d.y))
            }
        } else {
            // Varınca: where did the phone come from? Live position when still far (e.g. developer test),
            // otherwise the point remembered when the arrival geofence was added.
            val origin: Pair<Double, Double>? = when {
                here != null && distance(here.latitude, here.longitude, placeLat, placeLng) > 1_500f ->
                    here.latitude to here.longitude
                else -> LocationReminders.storedOrigin(app, reminder.id)
                    ?.takeIf { distance(it.first, it.second, placeLat, placeLng) > 400f }
            }
            val route = origin?.let { DepartureReminder.routeBetween(it.first, it.second, placeLat, placeLng) }
            if (route != null) {
                snapshot(app, widthPx, heightPx, center = null, bounds = routeBounds(route, placeLat, placeLng))?.let { snap ->
                    val pts = route.points.map { (a, b) -> snap.pixelForLatLng(LatLng(a, b)).let { Offset(it.x, it.y) } }
                    val o = snap.pixelForLatLng(LatLng(route.origin.first, route.origin.second))
                    val d = snap.pixelForLatLng(LatLng(placeLat, placeLng))
                    RealIntroMap(snap.bitmap.asImageBitmap(), pts, Offset(o.x, o.y), Offset(d.x, d.y))
                }
            } else {
                snapshot(app, widthPx, heightPx, center = LatLng(placeLat, placeLng), bounds = null)?.let { snap ->
                    val d = snap.pixelForLatLng(LatLng(placeLat, placeLng))
                    RealIntroMap(snap.bitmap.asImageBitmap(), emptyList(), null, Offset(d.x, d.y))
                }
            }
        }
        if (result != null) cache[key] = result
        return result
    }

    /** The arrived place from close above, centred: what the strip shows once the list is up. */
    suspend fun loadCloseUp(context: Context, reminder: ReminderItem, widthPx: Int, heightPx: Int): RealIntroMap? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val lat = reminder.locationTrigger?.latitude ?: reminder.location?.latitude ?: return null
        val lng = reminder.locationTrigger?.longitude ?: reminder.location?.longitude ?: return null
        val key = "close:${reminder.id}:$lat:$lng:$widthPx:$heightPx"
        cache[key]?.let { return it }
        val snap = snapshot(context.applicationContext, widthPx, heightPx, center = LatLng(lat, lng), bounds = null, zoom = 16.8)
            ?: return null
        val d = snap.pixelForLatLng(LatLng(lat, lng))
        return RealIntroMap(snap.bitmap.asImageBitmap(), emptyList(), null, Offset(d.x, d.y)).also { cache[key] = it }
    }

    /** Route + place in view, with extra room at the top and bottom for the edge fade. */
    private fun routeBounds(route: DepartureReminder.Route, lat: Double, lng: Double): LatLngBounds {
        val lats = route.points.map { it.first } + route.origin.first + lat
        val lngs = route.points.map { it.second } + route.origin.second + lng
        val padLat = (lats.max() - lats.min()).coerceAtLeast(0.002) * 0.30
        val padLng = (lngs.max() - lngs.min()).coerceAtLeast(0.002) * 0.24
        return LatLngBounds.from(lats.max() + padLat, lngs.max() + padLng, lats.min() - padLat, lngs.min() - padLng)
    }

    private suspend fun snapshot(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        center: LatLng?,
        bounds: LatLngBounds?,
        zoom: Double = 15.4
    ): MapSnapshot? = withContext(Dispatchers.Main) {
        runCatching {
            MapLibre.getInstance(context)
            val options = MapSnapshotter.Options(widthPx, heightPx)
                .withStyleBuilder(Style.Builder().fromUri(STYLE_URL))
                .withLogo(false)
            if (bounds != null) {
                options.withRegion(bounds)
            } else if (center != null) {
                options.withCameraPosition(CameraPosition.Builder().target(center).zoom(zoom).build())
            }
            val snapshotter = object : MapSnapshotter(context, options) {
                override fun addOverlay(mapSnapshot: MapSnapshot) = Unit
            }
            suspendCancellableCoroutine<MapSnapshot?> { cont ->
                cont.invokeOnCancellation { runCatching { snapshotter.cancel() } }
                snapshotter.start(
                    { snap -> if (cont.isActive) cont.resume(snap) },
                    { _ -> if (cont.isActive) cont.resume(null) }
                )
            }
        }.getOrNull()
    }
}

/** While the real map loads (max ~3.5 s): a calm white locating pulse in the middle of the map area. */
@Composable
internal fun MapLoadingPulse(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "mapLoading")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "mapLoadingT"
    )
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = (8f + 34f * t) * density
        drawCircle(Color.White.copy(alpha = 0.55f * (1f - t)), r, c, style = Stroke(1.4f * density))
        drawCircle(Color.White, 5f * density, c)
    }
}

/** The close-up in the strip: the monochrome map with the place dot and a calm, looping pulse in [accent]. */
@Composable
internal fun PlaceCloseUpCanvas(map: RealIntroMap, accent: Color, modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "closeUp")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1900, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "closeUpT"
    )
    val filter = remember { ColorFilter.colorMatrix(monoMatrix) }
    Canvas(modifier = modifier) {
        val dx = (size.width - map.image.width) / 2f
        val dy = (size.height - map.image.height) / 2f
        drawImage(map.image, topLeft = Offset(dx, dy), colorFilter = filter)
        val c = Offset(map.destination.x + dx, map.destination.y + dy)
        val r = (8f + 30f * t) * density
        drawCircle(accent.copy(alpha = 0.45f * (1f - t)), r, c)
        drawCircle(Color.Black.copy(alpha = 0.55f), 9f * density, c)
        drawCircle(Color.White, 7f * density, c)
        drawCircle(accent, 3.5f * density, c)
    }
}

/** Monochrome (grayscale, slightly lifted) so the real map sits in the white-on-AMOLED language of the screen. */
private val monoMatrix = ColorMatrix(
    floatArrayOf(
        0.36f, 0.62f, 0.10f, 0f, 6f,
        0.36f, 0.62f, 0.10f, 0f, 6f,
        0.36f, 0.62f, 0.10f, 0f, 6f,
        0f, 0f, 0f, 1f, 0f
    )
)

/**
 * 3-second intro on top of the real map snapshot.
 * Camera eases in (1.10 → 1.0), the route draws from origin to the place as a glowing white line,
 * and the place pulses as the line arrives. Ayrılınca: only the pulsing position.
 */
@Composable
internal fun RealMapIntroCanvas(
    map: RealIntroMap,
    progress: Float,
    modifier: Modifier = Modifier,
    /** Route and place pulse colour (Varınca green, Ayrılınca amber). */
    lineColor: Color = Color.White
) {
    val layerPaint = remember { Paint() }
    val routePath = remember(map) {
        Path().apply {
            map.route.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
    }
    val routeLength = remember(map) { PathMeasure().apply { setPath(routePath, false) }.length }
    val filter = remember { ColorFilter.colorMatrix(monoMatrix) }

    Canvas(modifier = modifier) {
        val p = progress.coerceIn(0f, 1f)
        val camU = (p / 0.55f).coerceIn(0f, 1f)
        val camEase = 1f - (1f - camU) * (1f - camU) * (1f - camU)
        val zoom = 1.10f - 0.10f * camEase
        val pivot = map.destination

        // Map image is rendered at exactly this canvas size; centre-crop if the layout differs slightly.
        val dx = (size.width - map.image.width) / 2f
        val dy = (size.height - map.image.height) / 2f

        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), layerPaint)
            withTransform({
                translate(dx, dy)
                scale(zoom, zoom, pivot = pivot)
            }) {
                drawImage(
                    image = map.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(map.image.width, map.image.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(map.image.width, map.image.height),
                    alpha = (p / 0.12f).coerceIn(0f, 1f),
                    colorFilter = filter
                )
            }
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent, 0.16f to Color.White, 0.84f to Color.White, 1f to Color.Transparent,
                    startY = 0f, endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent, 0.10f to Color.White, 0.90f to Color.White, 1f to Color.Transparent,
                    startX = 0f, endX = size.width
                ),
                blendMode = BlendMode.DstIn
            )
            canvas.restore()
        }

        withTransform({
            translate(dx, dy)
            scale(zoom, zoom, pivot = pivot)
        }) {
            val unit = 1f / zoom
            val density = this.density

            fun pulse(center: Offset, localP: Float, loop: Boolean) {
                val u = if (loop) (localP % 1f) else localP.coerceIn(0f, 1f)
                val r = (6f + 22f * (1f - (1f - u) * (1f - u))) * density * unit
                val a = 1f - u
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(lineColor.copy(alpha = 0.40f * a), Color.Transparent),
                        center = center, radius = r
                    ),
                    radius = r, center = center
                )
                drawCircle(lineColor.copy(alpha = 0.70f * a), r, center, style = Stroke(1.4f * density * unit))
            }

            fun dot(center: Offset, alpha: Float) {
                drawCircle(Color.Black.copy(alpha = 0.55f * alpha), 7.5f * density * unit, center)
                drawCircle(Color.White.copy(alpha = alpha), 5.5f * density * unit, center)
            }

            if (map.route.size < 2) {
                // Ayrılınca (or no route available): calm, looping signal on the position
                if (p > 0.08f) pulse(map.destination, (p - 0.08f) / 0.45f, loop = true)
                dot(map.destination, (p / 0.15f).coerceIn(0f, 1f))
                return@withTransform
            }

            // Origin
            val originAlpha = ((p - 0.10f) / 0.10f).coerceIn(0f, 1f)
            map.origin?.let { if (originAlpha > 0f) dot(it, originAlpha * 0.85f) }

            // Route head travels origin → place between 18% and 78% of the intro
            val drawU = ((p - 0.18f) / 0.60f).coerceIn(0f, 1f)
            val drawEase = if (drawU < 0.5f) 4f * drawU * drawU * drawU else 1f - (-2f * drawU + 2f).let { it * it * it } / 2f
            if (drawEase > 0f && routeLength > 0f) {
                val partial = Path()
                PathMeasure().apply { setPath(routePath, false) }
                    .getSegment(0f, routeLength * drawEase, partial, true)
                drawPath(
                    partial,
                    lineColor.copy(alpha = 0.3f),
                    style = Stroke(width = 9f * density * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    partial,
                    lineColor,
                    style = Stroke(width = 3.4f * density * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Place: pulses once the line has arrived
            if (drawEase >= 0.98f) {
                val local = (p - 0.78f) / 0.30f
                pulse(map.destination, local, loop = false)
                if (local > 0.5f) pulse(map.destination, local - 0.5f, loop = false)
            }
            dot(map.destination, ((p - 0.05f) / 0.12f).coerceIn(0f, 1f))
        }
    }
}
