package com.cagri.real.ui.components

import com.cagri.real.i18n.t
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
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.focus.focusRequester
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
import com.cagri.real.data.LocationTrigger
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

private val PickerInk = Color(0xFF0B0B0D)
private val PickerMuted = Color(0xFF9A9AA4)

/**
 * Konum ekle: move the map under the pin (or tap a spot), jump to where you are now with one GPS fix, or hand off
 * to Google Maps → Paylaş → REAL as before. "Bu konumu kaydet" stores the pin with its reverse-geocoded name.
 * With [onRemind] the panel also offers "Varınca / Ayrılınca hatırlat" (Hassas or Normal distance), saved in one tap.
 */
@Composable
fun LocationPickerSheet(
    visible: Boolean,
    initial: ReminderLocation?,
    onSave: (ReminderLocation) -> Unit,
    onShareFromGoogleMaps: () -> Unit,
    onCancel: () -> Unit,
    initialTrigger: LocationTrigger? = null,
    onRemind: ((ReminderLocation, LocationTrigger) -> Unit)? = null
) {
    if (!visible) return
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        LocationPickerContent(initial, onSave, onShareFromGoogleMaps, onCancel, initialTrigger, onRemind)
    }
}

@Composable
private fun LocationPickerContent(
    initial: ReminderLocation?,
    onSave: (ReminderLocation) -> Unit,
    onShareFromGoogleMaps: () -> Unit,
    onCancel: () -> Unit,
    initialTrigger: LocationTrigger?,
    onRemind: ((ReminderLocation, LocationTrigger) -> Unit)?
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
    var leaving by remember { mutableStateOf(initialTrigger?.onExit ?: false) }
    // Konum ara: the top pill turns into a search field; Apple Maps results through the REAL Worker.
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }
    var skipLookUp by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(query, searching) {
        if (!searching || query.trim().length < 2) { results = emptyList(); searchBusy = false; return@LaunchedEffect }
        delay(300)
        searchBusy = true
        results = searchPlaces(query.trim(), center)
        searchBusy = false
    }
    fun pickedPlace() = ReminderLocation(
        placeName = name.ifBlank { address.ifBlank { t("Seçilen yer") } },
        address = address,
        latitude = center.latitude,
        longitude = center.longitude,
        mapsUrl = "https://www.google.com/maps/search/?api=1&query=${center.latitude},${center.longitude}"
    )
    fun remind(onExit: Boolean) {
        val place = pickedPlace()
        onRemind?.invoke(place, LocationTrigger(place.placeName, center.latitude, center.longitude,
            if (onExit) LocationTrigger.EXIT_MIN_RADIUS else LocationTrigger.PRECISE_RADIUS, onExit))
    }

    var tappedPlaceName by remember { mutableStateOf<String?>(null) }
    fun lookUp(target: LatLng) {
        geocodeJob?.cancel()
        geocodeJob = scope.launch {
            delay(350)
            val (n, a) = reverseGeocode(context, target)
            // A tapped map place keeps its own name; the geocoder only fills in the address.
            name = tappedPlaceName ?: n; address = a
            tappedPlaceName = null
        }
    }
    /** The name of a place label (restaurant, shop, park…) under the tap, and where it sits, or null. */
    fun placeLabelAt(target: LatLng): Pair<String, LatLng>? {
        val m = map ?: return null
        val layers = m.style?.layers?.map { it.id }?.filter { it.startsWith("poi") || it.startsWith("real-poi") }?.toTypedArray() ?: return null
        if (layers.isEmpty()) return null
        val p = m.projection.toScreenLocation(target)
        val r = 18f * context.resources.displayMetrics.density
        val feature = m.queryRenderedFeatures(android.graphics.RectF(p.x - r, p.y - r, p.x + r, p.y + r), *layers)
            .firstOrNull { !it.getStringProperty("name").isNullOrBlank() } ?: return null
        val point = feature.geometry() as? org.maplibre.geojson.Point
        return feature.getStringProperty("name") to (point?.let { LatLng(it.latitude(), it.longitude()) } ?: target)
    }
    fun moveTo(target: LatLng, zoom: Double = 16.0) {
        map?.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(target).zoom(zoom).build()), 450)
    }
    fun locateNow() {
        locating = true; message = null
        scope.launch {
            val fix = currentLocation(context)
            locating = false
            if (fix == null) message = t("Konum alınamadı. Konum servisinin açık olduğundan emin ol.")
            else { picked = true; moveTo(fix) }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locateNow() else message = t("Konum izni verilmedi. Haritadan da seçebilirsin.")
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
                if (byUser) { picked = true; tappedPlaceName = null }
                // A search result already named the place; only a move by hand asks the geocoder again.
                if (skipLookUp && !byUser) skipLookUp = false
                else if (picked) lookUp(target)
            },
            onTap = { target ->
                picked = true
                val label = placeLabelAt(target)
                tappedPlaceName = label?.first
                label?.let { name = it.first }
                moveTo(label?.second ?: target, map?.cameraPosition?.zoom?.coerceAtLeast(15.0) ?: 16.0)
            }
        )
        // The pin marks the map centre; the map moves under it.
        PinMark(Modifier.align(Alignment.Center).offset(y = (-18).dp), active = picked)

        // Liquid glass chrome over the map: back, hint, "where I am".
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RealBackButton(onBack = onCancel, description = t("Vazgeç"), surface = {
                Box(Modifier.fillMaxSize().liquidChrome(22.dp))
            })
            // The picked place lives up here between back and locate, so the bottom panel only holds the buttons.
            // Tapping it searches ("Konum ara").
            Row(
                // Grows with a long name / address (neighbourhood, district…) instead of cutting it off.
                Modifier.weight(1f).heightIn(min = 44.dp).liquidChrome(22.dp)
                    .clickable(enabled = !searching, role = Role.Button) { searching = true; query = "" }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searching) {
                    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    androidx.compose.foundation.text.BasicTextField(
                        value = query, onValueChange = { query = it.take(120) }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFF3F1F4), fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { field ->
                            if (query.isEmpty()) Text(t("Yer ya da adres ara"), color = PickerMuted, fontSize = 14.sp, maxLines = 1)
                            field()
                        }
                    )
                    if (searchBusy) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 1.5.dp)
                    else Text("✕", color = PickerMuted, fontSize = 14.sp,
                        modifier = Modifier.clip(CircleShape).clickable { searching = false; query = "" }.padding(4.dp))
                } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    if (picked) {
                        Text(name.ifBlank { t("Seçilen yer") }, color = Color(0xFFF3F1F4), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(address.ifBlank { String.format(Locale.ROOT, "%.5f, %.5f", center.latitude, center.longitude) },
                            color = PickerMuted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(t("Ara, haritayı kaydır ya da bir yere dokun"), color = Color.White.copy(alpha = 0.86f), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(
                Modifier.size(44.dp).liquidChrome(22.dp)
                    .clickable(enabled = !locating, role = Role.Button, onClick = ::requestLocate),
                contentAlignment = Alignment.Center
            ) {
                if (locating) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else LocateGlyph(Color.White)
            }
        }

        if (searching && results.isNotEmpty()) {
            Column(
                Modifier.statusBarsPadding().padding(top = 60.dp, start = 12.dp, end = 12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)).background(Color(0xE6101014))
                    .border(0.75.dp, liquidContentEdge(), RoundedCornerShape(22.dp)).padding(vertical = 6.dp)
            ) {
                results.forEach { place ->
                    Column(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            searching = false; query = ""; results = emptyList()
                            name = place.name; address = place.address; picked = true; skipLookUp = true
                            moveTo(LatLng(place.latitude, place.longitude), 16.5)
                        }.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(place.name, color = Color(0xFFF3F1F4), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (place.address.isNotBlank()) Text(place.address, color = PickerMuted, fontSize = 12.sp,
                            lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        val panelShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(panelShape)
                // Dark but see-through: the map stays visible behind the panel.
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x8C08080A), Color(0xD908080A))))
                .drawBehind {
                    drawRect(androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f), radius = size.width * 0.85f
                    ))
                }
                .border(0.75.dp, liquidContentEdge(), panelShape)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            message?.let { Text(it, color = Color(0xFFE6BCB1), fontSize = 13.sp) }
            if (onRemind != null) {
                // Adding the place comes first; reminding on arrival / departure is the second choice.
                PickerPill(t("Konum ekle"), primary = true, enabled = picked, modifier = Modifier.fillMaxWidth()) { onSave(pickedPlace()) }
                if (leaving && picked) ExitOutsideNote(center.latitude, center.longitude, LocationTrigger.EXIT_MIN_RADIUS, PickerMuted)
                RemindPill(leaving = leaving, enabled = picked, onToggle = { leaving = !leaving }) { remind(onExit = leaving) }
            } else {
                PickerPill(t("Bu konumu kaydet"), primary = true, enabled = picked, modifier = Modifier.fillMaxWidth()) { onSave(pickedPlace()) }
            }
            Text(
                t("Google Haritalar'dan paylaş"),
                color = PickerMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onShareFromGoogleMaps).padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * "Varınca hatırlat" with a small "Ayrılınca" at its right end. Tapping the small one swaps them: "Ayrılınca
 * hatırlat" slides into the middle and "Varınca" moves to the left end. The middle saves.
 */
@Composable
private fun RemindPill(leaving: Boolean, enabled: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(shape)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = leaving,
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                (androidx.compose.animation.slideInHorizontally { it / 2 * dir } + androidx.compose.animation.fadeIn()) togetherWith
                    (androidx.compose.animation.slideOutHorizontally { -it / 2 * dir } + androidx.compose.animation.fadeOut())
            },
            modifier = Modifier.align(Alignment.Center),
            label = "remindMain"
        ) { exit ->
            Text(if (exit) t("Ayrılınca hatırlat") else t("Varınca hatırlat"), color = PickerInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        androidx.compose.animation.AnimatedContent(
            targetState = leaving,
            transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
            modifier = Modifier.fillMaxSize(),
            label = "remindAlt"
        ) { exit ->
            Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = if (exit) Alignment.CenterStart else Alignment.CenterEnd) {
                Text(
                    if (exit) t("Varınca") else t("Ayrılınca"),
                    color = PickerInk.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(PickerInk.copy(alpha = 0.06f))
                        .clickable(enabled = enabled, role = Role.Button, onClick = onToggle)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }
    }
}

/** White pill for the main action, liquid glass for the others (PermissionSheet buttons). */
@Composable
private fun PickerPill(label: String, primary: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.height(50.dp)
            .then(if (primary) Modifier.clip(shape).background(Color.White.copy(alpha = if (enabled) 1f else 0.4f)) else Modifier.liquidChrome(25.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (primary) PickerInk else Color.White.copy(alpha = if (enabled) 0.92f else 0.4f),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
    val styleScope = rememberCoroutineScope()
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
            styleScope.launch { map.setStyle(MapAppearance.styleBuilder(context)) }
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
        val fill = if (active) Color.White else Color.White.copy(alpha = 0.6f)
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
private fun LocateGlyph(color: Color) {
    Box(Modifier.size(20.dp).drawBehind {
        val c = Offset(size.width / 2f, size.height / 2f)
        val stroke = 2.dp.toPx()
        drawCircle(color, size.minDimension * 0.30f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawCircle(color, size.minDimension * 0.12f, c)
        val l = size.minDimension * 0.5f
        val g = size.minDimension * 0.34f
        drawLine(color, Offset(c.x, c.y - l), Offset(c.x, c.y - g), stroke)
        drawLine(color, Offset(c.x, c.y + g), Offset(c.x, c.y + l), stroke)
        drawLine(color, Offset(c.x - l, c.y), Offset(c.x - g, c.y), stroke)
        drawLine(color, Offset(c.x + g, c.y), Offset(c.x + l, c.y), stroke)
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
                LocationManagerCompat.getCurrentLocation(lm, provider, null as android.os.CancellationSignal?,
                    ContextCompat.getMainExecutor(context)) { location: android.location.Location? -> cont.resume(location) }
            }
        }
        if (fix != null) return LatLng(fix.latitude, fix.longitude)
    }
    return providers.firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }?.let { LatLng(it.latitude, it.longitude) }
}

private data class PlaceResult(val name: String, val address: String, val latitude: Double, val longitude: Double)

/** Konum ara: Apple Maps results near [near] (REAL Worker /api/place-search); empty when offline or signed out. */
private suspend fun searchPlaces(q: String, near: LatLng): List<PlaceResult> = withContext(Dispatchers.IO) {
    runCatching {
        val body = org.json.JSONObject().put("q", q).put("lat", near.latitude).put("lng", near.longitude)
        val response = com.cagri.real.network.WorkerClient(com.cagri.real.platform.android.auth.AndroidBackend.workerTransport)
            .postJson(PLACE_SEARCH, body.toString(), 10_000, 15_000, readErrorBody = false)
        if (response.statusCode !in 200..299) return@runCatching emptyList()
        val places = org.json.JSONObject(response.body).optJSONArray("places") ?: return@runCatching emptyList()
        (0 until places.length()).mapNotNull { i ->
            val p = places.optJSONObject(i) ?: return@mapNotNull null
            PlaceResult(p.optString("name"), p.optString("address"), p.optDouble("latitude"), p.optDouble("longitude"))
                .takeIf { it.name.isNotBlank() && !it.latitude.isNaN() && !it.longitude.isNaN() }
        }
    }.getOrDefault(emptyList())
}

private const val PLACE_SEARCH = "https://real-backend-staging.cagrigul1907.workers.dev/api/place-search"

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
