package com.cagri.real.ui.components

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
        LaunchedEffect(width, height, latitude, longitude, cameraZoom) {
            if (width <= 0 || height <= 0) return@LaunchedEffect

            MapLibre.getInstance(context.applicationContext)

            val options = MapSnapshotter.Options(width, height)
                .withStyleBuilder(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/dark"))
                .withLogo(false)
                .withCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(latitude, longitude))
                        .zoom(cameraZoom?.takeIf { it.isFinite() }?.coerceIn(1.0,19.0) ?: if (place.approximateCoordinates) 15.10 else 15.35)
                        .build()
                )

            
            
            
            val snapshotter = object : MapSnapshotter(context, options) {
                override fun addOverlay(mapSnapshot: MapSnapshot) = Unit
            }
            var active = true
            try {
            snapshotter.start(object : MapSnapshotter.SnapshotReadyCallback {
                override fun onSnapshotReady(snapshot: MapSnapshot) {
                    if (active) { snapshotBitmap = snapshot.bitmap; onSnapshotShown?.invoke() }
                }
            })
            kotlinx.coroutines.awaitCancellation()
            } finally { active = false; snapshotter.cancel() }
        }

        snapshotBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Harita görünümü",
                modifier = Modifier.fillMaxSize().then(
                    if (previewScale == null) Modifier
                    else Modifier.graphicsLayer { val s = previewScale(); scaleX = s; scaleY = s }
                ),
                contentScale = ContentScale.Crop
            )
        }

        
        Text(
            text = "© OpenStreetMap contributors · © OpenMapTiles",
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
