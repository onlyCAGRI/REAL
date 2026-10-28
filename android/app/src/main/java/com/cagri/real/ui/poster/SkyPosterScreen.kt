package com.cagri.real.ui.poster

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.data.ReminderLocation
import com.cagri.real.i18n.I18n
import com.cagri.real.i18n.t
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.RealToast
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Real Labs sky poster: the sky over the card's place at the card's moment. The hour bar moves the time within
 * that day (a daytime card can still show its evening sky); the rest works like the map poster.
 */
@Composable
fun SkyPosterDialog(place: ReminderLocation, momentMillis: Long, onAttach: ((java.io.File) -> Unit)?, onClose: () -> Unit) {
    val lat = place.latitude ?: return
    val lng = place.longitude ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var template by remember { mutableStateOf(SKY_POSTER_TEMPLATES.first()) }
    var posterPlace by remember { mutableStateOf<MapPosterPlace?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val dayStart = remember(momentMillis) {
        Calendar.getInstance().apply {
            timeInMillis = momentMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    // A daytime card (Sun up at the place) opens on that day's 21:00 sky; the hour bar still reaches its real time.
    val start = remember(momentMillis, lat, lng) {
        if (SkyMath.sunAltitude(momentMillis, lat, lng) > 0) dayStart + 21 * 3_600_000L else momentMillis
    }
    var minute by remember { mutableStateOf(((start - dayStart) / 60_000L).toFloat()) }
    var moment by remember { mutableStateOf(start) }

    DisposableEffect(Unit) {
        MapPosterJobs.openScreens++
        onDispose { MapPosterJobs.openScreens-- }
    }
    LaunchedEffect(lat, lng) { posterPlace = MapPosterRenderer.place(context, lat, lng, place.placeName, I18n.locale) }
    LaunchedEffect(posterPlace, template, moment) {
        val p = posterPlace ?: return@LaunchedEffect
        failed = false
        val next = SkyPosterRenderer.render(context, template, p, moment, scale = 0.34f)
        if (next == null) failed = true else preview = next
    }

    suspend fun printSize(): Bitmap? {
        val p = posterPlace ?: return null
        return SkyPosterRenderer.render(context, template, p, moment, SkyPosterRenderer.HIGH_RES)
            ?: SkyPosterRenderer.render(context, template, p, moment, 1f)
    }
    fun fileName() = "REAL-sky-" + (posterPlace?.title ?: place.placeName).filter { it.isLetterOrDigit() }.ifEmpty { "poster" } + "-" + template.id

    val surface by animateColorAsState(Color(template.background), tween(320), label = "skySurface")
    val ink by animateColorAsState(if (template.dark) Color.White else Color(0xFF151515), tween(320), label = "skyInk")
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(surface).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Gökyüzü posteri"), color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(ink.copy(alpha = 0.1f))
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { ActionIcon(ActionGlyph.Close, Modifier.size(14.dp), ink) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(0.8f), contentAlignment = Alignment.Center) {
                    val image = preview
                    if (image != null && !image.isRecycled) {
                        Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                    if (failed) Text(t("Yıldız haritası yüklenemedi"), color = ink.copy(alpha = 0.7f), fontSize = 13.sp)
                    else if (image == null) Text(t("Poster hazırlanıyor…"), color = ink.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
            TimeRuler(
                minute = minute,
                ink = ink,
                enabled = !busy,
                onChange = { minute = it },
                onSettled = { moment = dayStart + it.toLong() * 60_000L }
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                items(SKY_POSTER_TEMPLATES, key = { it.id }) { item ->
                    SkySwatch(item, selected = item.id == template.id, ink = ink) { if (!busy) template = item }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onAttach != null) PosterButton(t("Karta ekle"), ink, primary = true, enabled = posterPlace != null && !busy, modifier = Modifier.weight(1f)) {
                    busy = true
                    scope.launch {
                        val p = posterPlace
                        val bitmap = p?.let { SkyPosterRenderer.render(context, template, it, moment, 1f) }
                        val file = bitmap?.let { MapPosterRenderer.cameraCacheFile(context, it, fileName()) }
                        bitmap?.recycle()
                        busy = false
                        if (file == null) RealToast.show(context, t("Poster hazırlanamadı"))
                        else { onAttach(file); onClose() }
                    }
                }
                PosterButton(t("Kaydet"), ink, primary = onAttach == null, enabled = posterPlace != null && !busy, modifier = Modifier.weight(1f)) {
                    val p = posterPlace ?: return@PosterButton
                    busy = true
                    MapPosterJobs.save(context, p, fileName(), render = { printSize() }, onDone = { busy = false })
                }
                PosterButton(t("Paylaş"), ink, enabled = posterPlace != null && !busy, modifier = Modifier.weight(1f)) {
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
            if (busy) Text(
                t("Poster hazırlanıyor…"), color = ink.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
            )
        }
    }
}

/**
 * The hour picker as a clock ruler: the day scrolls under a fixed centre line, a tick every 5 minutes, taller
 * ticks with labels on the hours. Ticks swell near the centre and fade towards the edges; a fling glides and the
 * ruler settles on the nearest 5 minutes, then [onSettled] redraws the sky. The big time above is the centre.
 */
@Composable
private fun TimeRuler(minute: Float, ink: Color, enabled: Boolean, onChange: (Float) -> Unit, onSettled: (Float) -> Unit) {
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pxPerMinute = with(density) { 2.2.dp.toPx() }
    val position = remember { androidx.compose.animation.core.Animatable(minute) }
    LaunchedEffect(minute) { if (!position.isRunning && position.value != minute) position.snapTo(minute) }
    val labelPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }

    fun settle(velocity: Float) {
        scope.launch {
            position.updateBounds(0f, 1435f)
            // Glide with the fling, then land on the nearest 5 minutes.
            val target = (androidx.compose.animation.core.exponentialDecay<Float>(frictionMultiplier = 2f)
                .calculateTargetValue(position.value, velocity) / 5f).let { kotlin.math.round(it) * 5f }.coerceIn(0f, 1435f)
            position.animateTo(target, androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 180f), velocity) { onChange(value) }
            onSettled(target)
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val m = kotlin.math.round(position.value).toInt().coerceIn(0, 1439)
        Text(
            String.format("%02d:%02d", m / 60, m % 60), color = ink, fontSize = 22.sp,
            fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
        )
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxWidth().height(58.dp).padding(top = 4.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking(); scope.launch { position.stop() } },
                        onHorizontalDrag = { change, dx ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            val next = (position.value - dx / pxPerMinute).coerceIn(0f, 1435f)
                            scope.launch { position.snapTo(next) }
                            onChange(next)
                        },
                        onDragEnd = { settle(-tracker.calculateVelocity().x / pxPerMinute) },
                        onDragCancel = { settle(0f) }
                    )
                }
        ) {
            val cx = size.width / 2f
            val half = size.width / 2f
            val base = size.height * 0.62f
            labelPaint.textSize = 10.sp.toPx()
            labelPaint.color = android.graphics.Color.argb(255, (ink.red * 255).toInt(), (ink.green * 255).toInt(), (ink.blue * 255).toInt())
            val visible = (half / pxPerMinute).toInt() + 10
            val first = ((position.value.toInt() - visible) / 5) * 5
            var t = first.coerceAtLeast(0)
            while (t <= (position.value + visible).toInt().coerceAtMost(1435)) {
                val x = cx + (t - position.value) * pxPerMinute
                val near = 1f - (kotlin.math.abs(x - cx) / half).coerceIn(0f, 1f)
                val hour = t % 60 == 0
                val quarter = t % 15 == 0
                val len = (if (hour) 22.dp else if (quarter) 14.dp else 8.dp).toPx() * (0.7f + 0.5f * near)
                drawLine(
                    ink.copy(alpha = (0.15f + 0.65f * near) * if (hour) 1f else 0.75f),
                    androidx.compose.ui.geometry.Offset(x, base - len), androidx.compose.ui.geometry.Offset(x, base),
                    strokeWidth = (if (hour) 1.6.dp else 1.dp).toPx()
                )
                if (hour) {
                    labelPaint.alpha = (60 + 160 * near).toInt()
                    drawContext.canvas.nativeCanvas.drawText(String.format("%02d", t / 60), x, base + 14.dp.toPx(), labelPaint)
                }
                t += 5
            }
            // The fixed centre: a taller line with a small dot, where the time above is read.
            drawLine(ink, androidx.compose.ui.geometry.Offset(cx, base - 30.dp.toPx()), androidx.compose.ui.geometry.Offset(cx, base + 2.dp.toPx()), strokeWidth = 2.dp.toPx())
            drawCircle(ink, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(cx, base - 32.dp.toPx()))
        }
    }
}

@Composable
private fun SkySwatch(template: SkyPosterTemplate, selected: Boolean, ink: Color, onClick: () -> Unit) {
    Column(Modifier.clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(54.dp).clip(CircleShape)
                .border(if (selected) 2.dp else 0.5.dp, if (selected) ink else ink.copy(alpha = 0.2f), CircleShape)
                .background(Color(template.background)),
            contentAlignment = Alignment.Center
        ) {
            // The sky disc with a ring and three stars, in the template's colours.
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(template.sky))
                    .border(1.dp, Color(template.ring), CircleShape)
            ) {
                listOf(Alignment.TopStart to 9.dp, Alignment.Center to 0.dp, Alignment.BottomEnd to 10.dp).forEach { (align, pad) ->
                    Box(Modifier.align(align).padding(pad).size(3.dp).clip(CircleShape).background(Color(template.star)))
                }
            }
        }
        Text(t(template.name), color = ink.copy(alpha = if (selected) 1f else 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
    }
}
