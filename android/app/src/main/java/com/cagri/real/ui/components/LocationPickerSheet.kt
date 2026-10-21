package com.cagri.real.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cagri.real.data.ReminderLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val PickerBlue = Color(0xFF91B9FF)
private val PickerInk = Color(0xFF0B0B0D)

/**
 * Konum ekle: move the map under the pin (or tap a spot), jump to where you are now with one GPS fix, or hand off
 * to Google Maps → Paylaş → REAL as before. "Bu konumu kaydet" stores the pin with its reverse-geocoded name.
 */
@Composable
fun LocationPickerSheet(
    visible: Boolean,
    initial: ReminderLocation?,
    onSave: (ReminderLocation) -> Unit,
    onShareFromGoogleMaps: () -> Unit,
    onCancel: () -> Unit
) {
    if (!visible) return
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        LocationPickerContent(initial, onSave, onShareFromGoogleMaps, onCancel)
    }
}

@Composable
private fun LocationPickerContent(
    initial: ReminderLocation?,
    onSave: (ReminderLocation) -> Unit,
    onShareFromGoogleMaps: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onCancel)
    val start = remember {
        val lat = initial?.latitude; val lon = initial?.longitude
        if (lat != null && lon != null) LatLng(lat, lon) else null
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var center by remember { mutableStateOf(start ?: LatLng(41.0082, 28.9784)) }
    var picked by remember { mutableStateOf(start != null) }
    var name by remember { mutableStateOf(initial?.placeName.orEmpty()) }
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var locating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var geocodeJob by remember { mutableStateOf<Job?>(null) }

    fun lookUp(target: LatLng) {
        geocodeJob?.cancel()
        geocodeJob = scope.launch {
            delay(350)
            val (n, a) = reverseGeocode(context, target)
            name = n; address = a
        }
    }
    fun moveTo(target: LatLng, zoom: Double = 16.0) {
        map?.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(target).zoom(zoom).build()), 450)
    }
    fun locateNow() {
        locating = true; message = null
        scope.launch {
            val fix = currentLocation(context)
            locating = false
            if (fix == null) message = "Konum alınamadı. Konum servisinin açık olduğundan emin ol."
            else { picked = true; moveTo(fix) }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locateNow() else message = "Konum izni verilmedi. Haritadan da seçebilirsin."
    }
    fun requestLocate() {
        if (hasLocationPermission(context)) locateNow()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E0F12))) {
        LiveMap(
            start = center,
            startZoom = if (start != null) 16.0 else 10.5,
            onReady = { map = it },
            onMoved = { target, byUser ->
                center = target
                if (byUser) picked = true
                if (picked) lookUp(target)
            },
            onTap = { target -> picked = true; moveTo(target, map?.cameraPosition?.zoom?.coerceAtLeast(15.0) ?: 16.0) }
        )
        // The pin marks the map centre; the map moves under it.
        PinMark(Modifier.align(Alignment.Center).offset(y = (-18).dp), active = picked)

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RealBackButton(onBack = onCancel, description = "Vazgeç", surface = {
                Box(Modifier.fillMaxSize().background(Color(0xCC16171A), CircleShape))
            })
            Spacer(Modifier.weight(1f))
            Text(
                "Haritayı kaydır veya bir yere dokun",
                color = Color.White, fontSize = 12.sp,
                modifier = Modifier.background(Color(0xCC16171A), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp)
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(PickerBlue)
                    .clickable(enabled = !locating, role = Role.Button, onClick = ::requestLocate),
                contentAlignment = Alignment.Center
            ) {
                if (locating) CircularProgressIndicator(Modifier.size(18.dp), color = PickerInk, strokeWidth = 2.dp)
                else LocateGlyph()
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(0xF2121316), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .border(0.7.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            message?.let { Text(it, color = Color(0xFFE6BCB1), fontSize = 13.sp) }
            if (picked) {
                Text(name.ifBlank { "Seçilen yer" }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(address.ifBlank { String.format(Locale.ROOT, "%.5f, %.5f", center.latitude, center.longitude) },
                    color = Color(0xFF9A9AA4), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text("Bir yer seç ya da konum düğmesiyle bulunduğun yeri al.", color = Color(0xFFB8B9C2), fontSize = 14.sp)
            }
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50))
                    .background(if (picked) PickerBlue else PickerBlue.copy(alpha = 0.3f))
                    .clickable(enabled = picked, role = Role.Button) {
                        onSave(ReminderLocation(
                            placeName = name.ifBlank { address.ifBlank { "Seçilen yer" } },
                            address = address,
                            latitude = center.latitude,
                            longitude = center.longitude,
                            mapsUrl = "https://www.google.com/maps/search/?api=1&query=${center.latitude},${center.longitude}"
                        ))
                    },
                contentAlignment = Alignment.Center
            ) { Text("Bu konumu kaydet", color = PickerInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Text(
                "Google Haritalar'dan paylaş",
                color = Color(0xFF9DC3FF), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onShareFromGoogleMaps).padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun LiveMap(
    start: LatLng,
    startZoom: Double,
    onReady: (MapLibreMap) -> Unit,
    onMoved: (LatLng, Boolean) -> Unit,
    onTap: (LatLng) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.onStart(); mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }
    AndroidView(factory = {
        mapView.getMapAsync { map ->
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            map.setStyle("https://tiles.openfreemap.org/styles/dark")
            map.cameraPosition = CameraPosition.Builder().target(start).zoom(startZoom).build()
            var byUser = false
            map.addOnCameraMoveStartedListener { reason -> byUser = reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE }
            map.addOnCameraIdleListener {
                map.cameraPosition.target?.let { onMoved(it, byUser) }
                byUser = false
            }
            map.addOnMapClickListener { onTap(it); true }
            onReady(map)
        }
        mapView
    }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun PinMark(modifier: Modifier, active: Boolean) {
    Box(modifier.size(36.dp).drawBehind {
        val w = size.width; val h = size.height
        val r = w * 0.36f
        val c = Offset(w / 2f, r + 2f)
        val fill = if (active) PickerBlue else Color.White.copy(alpha = 0.7f)
        val path = Path().apply {
            moveTo(w / 2f, h)
            lineTo(c.x - r * 0.72f, c.y + r * 0.62f)
            lineTo(c.x + r * 0.72f, c.y + r * 0.62f)
            close()
        }
        drawPath(path, fill)
        drawCircle(fill, r, c)
        drawCircle(PickerInk, r * 0.38f, c)
    })
}

@Composable
private fun LocateGlyph() {
    Box(Modifier.size(20.dp).drawBehind {
        val c = Offset(size.width / 2f, size.height / 2f)
        val stroke = 2.dp.toPx()
        drawCircle(PickerInk, size.minDimension * 0.30f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawCircle(PickerInk, size.minDimension * 0.12f, c)
        val l = size.minDimension * 0.5f
        val g = size.minDimension * 0.34f
        drawLine(PickerInk, Offset(c.x, c.y - l), Offset(c.x, c.y - g), stroke)
        drawLine(PickerInk, Offset(c.x, c.y + g), Offset(c.x, c.y + l), stroke)
        drawLine(PickerInk, Offset(c.x - l, c.y), Offset(c.x - g, c.y), stroke)
        drawLine(PickerInk, Offset(c.x + g, c.y), Offset(c.x + l, c.y), stroke)
    })
}

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** One fix from the platform location service (no Play Services needed); null when off or unavailable. */
@SuppressLint("MissingPermission")
private suspend fun currentLocation(context: Context): LatLng? {
    if (!hasLocationPermission(context)) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) add(LocationManager.FUSED_PROVIDER)
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
    }
    for (provider in providers) {
        val fix = kotlinx.coroutines.withTimeoutOrNull(12_000) {
            suspendCoroutine<android.location.Location?> { cont ->
                LocationManagerCompat.getCurrentLocation(lm, provider, null as androidx.core.os.CancellationSignal?,
                    ContextCompat.getMainExecutor(context)) { location: android.location.Location? -> cont.resume(location) }
            }
        }
        if (fix != null) return LatLng(fix.latitude, fix.longitude)
    }
    return providers.firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }?.let { LatLng(it.latitude, it.longitude) }
}

/** Place name and a short address for the pin, from the platform geocoder; blank when it has nothing. */
private suspend fun reverseGeocode(context: Context, target: LatLng): Pair<String, String> = withContext(Dispatchers.IO) {
    runCatching {
        if (!Geocoder.isPresent()) return@runCatching "" to ""
        @Suppress("DEPRECATION")
        val a = Geocoder(context, Locale.forLanguageTag("tr-TR")).getFromLocation(target.latitude, target.longitude, 1)?.firstOrNull()
            ?: return@runCatching "" to ""
        val name = a.featureName?.takeIf { it.isNotBlank() && !it.all { ch -> ch.isDigit() } } ?: a.thoroughfare.orEmpty()
        val address = listOfNotNull(a.thoroughfare, a.subLocality, a.locality ?: a.adminArea).filter { it.isNotBlank() }.distinct().joinToString(", ")
        name to address
    }.getOrDefault("" to "")
}
