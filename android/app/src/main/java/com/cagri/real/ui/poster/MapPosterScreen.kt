package com.cagri.real.ui.poster

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cagri.real.data.ReminderLocation
import com.cagri.real.i18n.I18n
import com.cagri.real.i18n.t
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.RealToast
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Real Labs map poster for a card's place: pick one of the ready templates over a live preview of the real poster,
 * then add it to the card as a photo, save it to the gallery, share it, or walk around the same styled map.
 * [onAttach] gets a private cache file (the camera import path takes and deletes it); null hides "Karta ekle".
 */
@Composable
fun MapPosterDialog(place: ReminderLocation, onAttach: ((java.io.File) -> Unit)?, onClose: () -> Unit) {
    val lat = place.latitude ?: return
    val lng = place.longitude ?: return
    val placeName = place.placeName
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var template by remember { mutableStateOf(MAP_POSTER_TEMPLATES.first()) }
    var place by remember { mutableStateOf<MapPosterPlace?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var explore by remember { mutableStateOf(false) }
    // [zoom] is what the preview and the saved poster use; [sliderZoom] follows the finger and only scales the
    // current preview, so dragging the bar stays smooth and the map is fetched once, when the finger lifts.
    var zoom by remember { mutableStateOf(POSTER_ZOOM) }
    var sliderZoom by remember { mutableStateOf(POSTER_ZOOM.toFloat()) }
    var previewZoom by remember { mutableStateOf(POSTER_ZOOM) }

    LaunchedEffect(lat, lng) { place = MapPosterRenderer.place(context, lat, lng, placeName, I18n.locale) }
    LaunchedEffect(place, template, zoom) {
        val p = place ?: return@LaunchedEffect
        failed = false
        val next = MapPosterRenderer.render(context, template, p, scale = 0.34f, zoom = zoom)
        // The old preview is left to the GC: recycling it here raced the Image still drawing it.
        if (next == null) failed = true else { preview = next; previewZoom = zoom }
    }

    /** The full-size poster; a smaller canvas when the phone cannot hold the large one. */
    suspend fun fullSize(): Bitmap? {
        val p = place ?: return null
        return MapPosterRenderer.render(context, template, p, 1f, zoom) ?: MapPosterRenderer.render(context, template, p, 0.7f, zoom)
    }
    DisposableEffect(Unit) {
        MapPosterJobs.openScreens++
        onDispose { MapPosterJobs.openScreens-- }
    }
    var progress by remember { mutableStateOf<Float?>(null) }
    /** Gallery and share get the 8K print; a phone that cannot hold it still gets the 3000 px poster. */
    suspend fun printSize(): Bitmap? {
        val p = place ?: return null
        progress = 0f
        val bitmap = MapPosterRenderer.renderHighRes(context, template, p, zoom) { progress = it } ?: fullSize()
        progress = null
        return bitmap
    }
    fun fileName() = "REAL-" + (place?.title ?: placeName).filter { it.isLetterOrDigit() }.ifEmpty { "poster" } + "-" + template.id

    // The whole screen wears the template: the poster's own background runs out to the screen edges, so the
    // preview reads as a full-bleed afiş instead of a card on black; controls take the poster's ink.
    val surface by animateColorAsState(Color(template.background), tween(320), label = "posterSurface")
    val ink by animateColorAsState(if (template.dark) Color.White else Color(0xFF151515), tween(320), label = "posterInk")
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(surface).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Harita posteri"), color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(ink.copy(alpha = 0.1f))
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { ActionIcon(ActionGlyph.Close, Modifier.size(14.dp), ink) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.aspectRatio(0.8f).background(surface),
                    contentAlignment = Alignment.Center
                ) {
                    val image = preview
                    if (image != null && !image.isRecycled) {
                        val live = Math.pow(2.0, sliderZoom - previewZoom).toFloat()
                        Image(
                            image.asImageBitmap(), null,
                            Modifier.fillMaxSize().graphicsLayer { scaleX = live; scaleY = live },
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (failed) Text(t("Harita yüklenemedi"), color = Color(template.text), fontSize = 13.sp)
                    else if (image == null) Text(t("Poster hazırlanıyor…"), color = ink.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionIcon(ActionGlyph.Minus, Modifier.size(14.dp), ink.copy(alpha = 0.6f))
                Slider(
                    value = sliderZoom,
                    onValueChange = { sliderZoom = it },
                    onValueChangeFinished = { zoom = sliderZoom.toDouble() },
                    valueRange = POSTER_ZOOM_MIN.toFloat()..POSTER_ZOOM_MAX.toFloat(),
                    enabled = !busy,
                    colors = SliderDefaults.colors(
                        thumbColor = ink, activeTrackColor = ink,
                        inactiveTrackColor = ink.copy(alpha = 0.18f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                ActionIcon(ActionGlyph.Add, Modifier.size(14.dp), ink.copy(alpha = 0.6f))
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                items(MAP_POSTER_TEMPLATES, key = { it.id }) { item ->
                    TemplateSwatch(item, selected = item.id == template.id, ink = ink) { if (!busy) template = item }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onAttach != null) PosterButton(t("Karta ekle"), ink, primary = true, enabled = place != null && !busy, modifier = Modifier.weight(1f)) {
                    busy = true
                    scope.launch {
                        val bitmap = fullSize()
                        val file = bitmap?.let { MapPosterRenderer.cameraCacheFile(context, it, fileName()) }
                        bitmap?.recycle()
                        busy = false
                        if (file == null) RealToast.show(context, t("Poster hazırlanamadı"))
                        else { onAttach(file); onClose() }
                    }
                }
                PosterButton(t("Kaydet"), ink, primary = onAttach == null, enabled = place != null && !busy, modifier = Modifier.weight(1f)) {
                    val p = place ?: return@PosterButton
                    busy = true
                    // App-scoped: closing the screen keeps the 8K render going and ends in a notification.
                    MapPosterJobs.save(context, p, fileName(), render = { printSize() }, onDone = { busy = false })
                }
                PosterButton(t("Paylaş"), ink, enabled = place != null && !busy, modifier = Modifier.weight(1f)) {
                    busy = true
                    scope.launch {
                        val bitmap = printSize()
                        val uri = bitmap?.let { MapPosterRenderer.shareFile(context, it, fileName()) }
                        bitmap?.recycle()
                        busy = false
                        if (uri == null) RealToast.show(context, t("Poster hazırlanamadı"))
                        else runCatching {
                            val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(send, null))
                        }
                    }
                }
            }
            PosterButton(
                t("Haritada gez"), ink, enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
            ) { explore = true }
            if (busy) Text(
                progress?.let { t("8K poster hazırlanıyor… %{0}", (it * 100).toInt()) } ?: t("Poster hazırlanıyor…"), color = ink.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
            )
        }
        if (explore) MapPosterExplore(lat, lng, template, place, zoom) { explore = false }
    }
}

@Composable
private fun TemplateSwatch(template: MapPosterTemplate, selected: Boolean, ink: Color, onClick: () -> Unit) {
    Column(
        Modifier.clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                .border(if (selected) 2.dp else 0.5.dp, if (selected) ink else ink.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .background(Color(template.background))
        ) {
            // A tiny map sketch in the template's own colours: water, a motorway, a main road, the title ink.
            Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(RoundedCornerShape(bottomStart = 14.dp)).background(Color(template.water)))
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(3.dp).background(Color(template.motorway)))
            Box(Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(width = 2.dp, height = 54.dp).background(Color(template.major)))
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).size(width = 24.dp, height = 3.dp)
                    .clip(CircleShape).background(Color(template.text))
            )
        }
        Text(
            t(template.name), color = ink.copy(alpha = if (selected) 1f else 0.6f), fontSize = 11.sp,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
internal fun PosterButton(label: String, ink: Color, primary: Boolean = false, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // Primary is filled with the ink (text in the poster's colour behind it); the rest are a faint ink wash.
    val onInk = if (ink.luminance() > 0.5f) Color(0xFF151515) else Color.White
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(23.dp))
            .background(if (primary) ink else ink.copy(alpha = 0.1f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = (if (primary) onInk else ink).copy(alpha = if (enabled) 1f else 0.4f)
        )
    }
}

/** The template as a map you can pan and zoom, with the poster's title resting at the bottom. */
@Composable
private fun MapPosterExplore(lat: Double, lng: Double, template: MapPosterTemplate, place: MapPosterPlace?, zoom: Double, onClose: () -> Unit) {
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
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(template.background))) {
            AndroidView(factory = {
                mapView.getMapAsync { map ->
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isAttributionEnabled = false
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false
                    map.setStyle(Style.Builder().fromJson(template.styleJson()))
                    map.cameraPosition = CameraPosition.Builder().target(LatLng(lat, lng)).zoom(zoom - 1.2).build()
                }
                mapView
            }, modifier = Modifier.fillMaxSize())
            val bg = Color(template.background)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg.copy(alpha = 0.92f))))
                    .navigationBarsPadding().padding(top = 48.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val ink = Color(template.text)
                if (place != null) {
                    Text(place.title.uppercase(place.locale), color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 9.sp, maxLines = 1)
                    if (place.country.isNotBlank()) Text(place.country.uppercase(place.locale), color = ink, fontSize = 13.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 6.dp))
                }
                Text(MapPosterRenderer.coordinates(lat, lng), color = ink.copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                Text("© OpenStreetMap contributors", color = ink.copy(alpha = 0.55f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 10.dp))
            }
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(40.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) { ActionIcon(ActionGlyph.Close, Modifier.size(14.dp), Color.White) }
        }
    }
}
