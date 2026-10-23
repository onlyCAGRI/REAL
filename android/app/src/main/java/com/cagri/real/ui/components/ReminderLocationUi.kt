package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.ui.graphics.asImageBitmap
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
    /** Live pinch preview: scales the current snapshot until the re-rendered one arrives. */
    previewScale: (() -> Float)? = null,
    /** Called each time a freshly rendered snapshot is on screen. */
    onSnapshotShown: (() -> Unit)? = null
) {
    val latitude = place.latitude ?: return
    val longitude = place.longitude ?: return
    val context = LocalContext.current

    var snapshotBitmap by remember(latitude, longitude) { mutableStateOf<Bitmap?>(null) }
    var width by remember { mutableIntStateOf(0) }
    var height by remember { mutableIntStateOf(0) }

    Box(
        modifier
            .background(Color(0xFF131820))
            .clickable(onClick = onOpen)
            .onSizeChanged {
                width = it.width
                height = it.height
            }
    ) {
        LaunchedEffect(width, height, latitude, longitude, cameraZoom, route) {
            if (width <= 0 || height <= 0) return@LaunchedEffect

            MapLibre.getInstance(context.applicationContext)

            val options = MapSnapshotter.Options(width, height)
                .withStyleBuilder(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/dark"))
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
                    snapshotBitmap = if (route == null) snapshot.bitmap
                        else drawRoute(snapshot, route, latitude, longitude, context.resources.displayMetrics.density)
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

/** Apple Maps–style route on the snapshot: blue line with a dark edge, the phone as a blue dot, the place as the white pin. */
private fun drawRoute(snapshot: MapSnapshot, route: com.cagri.real.notifications.DepartureReminder.Route, lat: Double, lng: Double, density: Float): Bitmap {
    val bitmap = snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(bitmap)
    val path = android.graphics.Path()
    route.points.forEachIndexed { i, (a, b) ->
        val p = snapshot.pixelForLatLng(LatLng(a, b))
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    canvas.drawPath(path, stroke.apply { color = 0xFF0B2A5C.toInt(); strokeWidth = 7.5f * density })
    canvas.drawPath(path, stroke.apply { color = 0xFF5B8DEF.toInt(); strokeWidth = 5f * density })
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val o = snapshot.pixelForLatLng(LatLng(route.origin.first, route.origin.second))
    canvas.drawCircle(o.x, o.y, 8.5f * density, fill.apply { color = android.graphics.Color.WHITE })
    canvas.drawCircle(o.x, o.y, 6f * density, fill.apply { color = 0xFF5B8DEF.toInt() })
    val d = snapshot.pixelForLatLng(LatLng(lat, lng))
    val head = d.y - 11f * density
    canvas.drawLine(d.x, d.y, d.x, head, stroke.apply { color = android.graphics.Color.WHITE; strokeWidth = 1.5f * density })
    canvas.drawCircle(d.x, head, 8.5f * density, fill.apply { color = 0x73000000 })
    canvas.drawCircle(d.x, head, 7f * density, fill.apply { color = android.graphics.Color.WHITE })
    canvas.drawCircle(d.x, head, 2f * density, fill.apply { color = 0xFF131820.toInt() })
    return bitmap
}
