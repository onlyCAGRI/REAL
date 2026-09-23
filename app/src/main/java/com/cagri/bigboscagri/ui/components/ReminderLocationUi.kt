package com.cagri.bigboscagri.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

/** A single noninteractive MapLibre preview; the full card opens the system Maps intent. */
@Composable
fun ReminderLocationMap(place: ReminderLocation, modifier: Modifier = Modifier, onOpen: () -> Unit = {}) {
    val latitude = place.latitude ?: return
    val longitude = place.longitude ?: return
    val context = LocalContext.current
    
    var snapshotBitmap by remember(latitude, longitude) { mutableStateOf<Bitmap?>(null) }
    var width by remember { mutableStateOf(0) }
    var height by remember { mutableStateOf(0) }
    
    Box(modifier
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
                .withCameraPosition(CameraPosition.Builder().target(LatLng(latitude, longitude)).zoom(15.0).build())
            
            val snapshotter = MapSnapshotter(context, options)
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
        
        Box(Modifier.align(Alignment.Center).size(36.dp)
            .background(Color(0xFF1B1B1E), CircleShape)
            .border(1.dp, Color(0xFFBDBDBD), CircleShape),
            contentAlignment = Alignment.Center) {
            ActionIcon(ActionGlyph.Pin, Modifier.size(20.dp), Color(0xFFF1F1F1))
        }
        
        Text("OpenFreeMap · OpenStreetMap", color = Color(0xFFCDD1D8), fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}
