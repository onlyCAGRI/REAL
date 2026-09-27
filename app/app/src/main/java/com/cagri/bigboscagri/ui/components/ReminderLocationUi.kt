package com.cagri.bigboscagri.ui.components

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
import com.cagri.bigboscagri.data.ReminderLocation
import org.maplibre.android.MapLibre
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

/**
 * A single noninteractive MapLibre preview; the full card opens the system Maps intent.
 * Snapshot branding controls are disabled and REAL renders a compact legal attribution.
 */
@Composable
fun ReminderLocationMap(place: ReminderLocation, modifier: Modifier = Modifier, onOpen: () -> Unit = {}) {
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
        LaunchedEffect(width, height, latitude, longitude) {
            if (width <= 0 || height <= 0) return@LaunchedEffect

            MapLibre.getInstance(context.applicationContext)

            val options = MapSnapshotter.Options(width, height)
                .withStyleBuilder(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/dark"))
                .withLogo(false)
                .withCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(latitude, longitude))
                        .zoom(if (place.approximateCoordinates) 15.10 else 15.35)
                        .build()
                )

            // MapLibre Android 11.8.0 does not expose Options.withAttribution().
            // Suppress MapSnapshotter's built-in logo/attribution overlay here and render
            // REAL's compact legal attribution below instead.
            val snapshotter = object : MapSnapshotter(context, options) {
                override fun addOverlay(mapSnapshot: MapSnapshot) = Unit
            }
            snapshotter.start(object : MapSnapshotter.SnapshotReadyCallback {
                override fun onSnapshotReady(snapshot: MapSnapshot) {
                    snapshotBitmap = snapshot.bitmap
                }
            })
        }

        snapshotBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Harita görünümü",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Keep attribution inside the rounded safe area and visually subordinate to the map.
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
