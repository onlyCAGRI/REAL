package com.cagri.real.ui.components

import androidx.core.graphics.drawable.toBitmap
import coil.request.ImageRequest
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.cagri.real.data.CatalogCardDisplay
import com.cagri.real.data.MusicBlock
import com.cagri.real.data.MusicCatalog
import com.cagri.real.data.formatVoiceDuration
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.theme.RealType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSearchSheet(designStyle: DesignStyle, onDismiss: () -> Unit, onSelect: (MusicBlock) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MusicBlock>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var source by remember { mutableStateOf(MusicSource.saved(context)) }
    /** Spotify: the song behind a link that was copied or pasted. */
    var spotifyTrack by remember { mutableStateOf<MusicBlock?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RealSheetDefaults.Ink,
        contentColor = Color.White, scrimColor = RealSheetDefaults.Scrim, shape = RealSheetDefaults.Shape,
        dragHandle = { RealSheetDefaults.DragHandle() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val panel = if (designStyle == DesignStyle.LiquidGlass) Modifier.liquidChrome(corners = 24.dp) else Modifier
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).then(panel).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Müzik", style = RealType.detailDialogTitle)
            // Which app the block opens (remembered): logos only.
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)).padding(4.dp)) {
                MusicSource.entries.forEach { s ->
                    val on = s == source
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { source = s; MusicSource.save(context, s); message = null }
                        .semantics { contentDescription = s.label }
                        .padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        MusicSourceLogo(if (s == MusicSource.Spotify) "Spotify" else "iTunes", 24.dp,
                            Modifier.alpha(if (on) 1f else 0.45f))
                    }
                }
            }
            // One search for both: songs come from the Apple Music catalog. With Spotify chosen the block opens the song
            // in Spotify (searched by title and artist, see openMusicBlock). A pasted Spotify song link works too.
            OutlinedTextField(value = query, onValueChange = { query = it.take(500) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(if (source == MusicSource.Spotify) "Şarkı ara veya Spotify bağlantısı yapıştır" else "Şarkı veya sanatçı ara") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF85858D),
                    unfocusedBorderColor = Color(0xFF424248), cursorColor = Color.White))
            // Searches while typing: a short pause after the last keystroke, the previous search is cancelled.
            LaunchedEffect(query) {
                val q = query.trim()
                val link = spotifyLinkIn(q)
                if (link != null) {
                    results = emptyList(); searching = true; message = null
                    val found = com.cagri.real.data.SharedLinkResolver.resolve(link).music
                    searching = false
                    if (found?.provider == "Spotify") spotifyTrack = found
                    else message = "Bu bağlantıdan şarkı okunamadı. Spotify'da bir şarkının bağlantısını kopyala."
                    return@LaunchedEffect
                }
                spotifyTrack = null
                if (q.length < 2) { results = emptyList(); message = null; searching = false; return@LaunchedEffect }
                kotlinx.coroutines.delay(350)
                searching = true
                message = null
                try {
                    results = MusicCatalog.search(q)
                    if (results.isEmpty()) message = "Sonuç bulunamadı. Başka bir şarkı veya sanatçı dene."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    message = "Müzik araması yapılamadı. Bağlantını kontrol edip yeniden dene."
                } finally {
                    searching = false
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (searching) "Aranıyor…" else "", style = RealType.detailBlockMeta, color = Color(0xFFA5A5B0))
                TextButton(onClick = onDismiss) { Text("Kapat", color = Color(0xFFBCBCC5)) }
            }
            message?.let { Text(it, style = RealType.detailBlockMeta, color = Color(0xFFBCBCC5)) }
            spotifyTrack?.let { track ->
                Text("Yapıştırdığın şarkı", style = RealType.detailBlockMeta, color = Color(0xFFA5A5B0))
                MusicResultRow(track) { onSelect(track.copy(createdAt = System.currentTimeMillis())) }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results, key = { it.id }) { track ->
                    MusicResultRow(track) { onSelect(track.copy(createdAt = System.currentTimeMillis())) }
                }
            }
            Text("Apple Music • ${MusicCatalog.storefrontName()} kataloğu", style = RealType.detailAttribution, color = Color(0xFF95959F))
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** An open.spotify.com / spotify.link song link inside [text], if any. */
private fun spotifyLinkIn(text: String?): String? = text?.let {
    Regex("https://(?:open\\.spotify\\.com/(?:intl-[a-z-]+/)?track/[A-Za-z0-9]{22}|spotify\\.link/[A-Za-z0-9]+)").find(it)?.value
}

@Composable
private fun MusicResultRow(track: MusicBlock, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onAdd).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MusicArtwork(track, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Color(0xFFA5A5B0), style = RealType.detailBlockMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ActionIcon(ActionGlyph.Add, Modifier.size(18.dp), Color.White)
    }
}

@Composable
fun MusicContentSection(blocks: List<MusicBlock>, designStyle: DesignStyle, editing: Boolean, onRemove: (String) -> Unit,
    display: CatalogCardDisplay = CatalogCardDisplay.FULL, showActions: Boolean = true) {
    var activeId by remember { mutableStateOf<String?>(null) }
    blocks.forEach { block ->
        key(block.id) {
            var ambience by remember(block.artworkUrl) { mutableStateOf(Color.Transparent) }
            var level by remember(block.id) { mutableFloatStateOf(0f) }
            val smoothLevel = animateFloatAsState(level, tween(110), label = "musicLiveLevel")
            if (display != CatalogCardDisplay.FULL) {
                CatalogCompactContent(block.title, display, 1f, editing, { onRemove(block.id) }) { modifier ->
                    MusicArtwork(block, modifier)
                }
            } else if (isDetailHero() && !editing) {
                // The card's cover ("En üstteki içeriği büyüt"): large artwork on top, then title, preview and opening.
                MusicTrackCard(block = block, showActions = showActions, selected = activeId == block.id,
                    onActivate = { activeId = block.id }, ambience = ambience, onAmbience = { ambience = it },
                    onLevel = { level = it }, hero = true)
            } else {
                TimelineContent(designStyle, ambience, liveLevel = { smoothLevel.value }) {
                    // No "Müzik" label: the cover and the source logo say it. Only the remove button while editing.
                    if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { onRemove(block.id) }, modifier = Modifier.size(40.dp)) {
                            ActionIcon(ActionGlyph.Close, Modifier.size(15.dp).semantics { contentDescription = "Müziği kaldır" }, Color(0xFFB8B8C2))
                        }
                    }
                    MusicTrackCard(
                        block = block,
                        showActions = showActions,
                        selected = activeId == block.id,
                        onActivate = { activeId = block.id },
                        ambience = ambience,
                        onAmbience = { ambience = it },
                        onLevel = { level = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicArtwork(block: MusicBlock, modifier: Modifier, onAmbience: ((Color) -> Unit)? = null) {
    val context = LocalContext.current
    val request = remember(block.artworkUrl, context) {
        ImageRequest.Builder(context).data(block.artworkUrl).allowHardware(false).build()
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF26262C)), contentAlignment = Alignment.Center) {
        ActionIcon(ActionGlyph.Music, Modifier.size(24.dp), Color(0xFFB8B8C2))
        AsyncImage(model = request, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            onSuccess = { result ->
                if (onAmbience != null) {
                    val color = runCatching {
                        // A tiny sample of the already-loaded artwork; no second request or palette dependency.
                        val bitmap = result.result.drawable.toBitmap(12, 12)
                        var red = 0f; var green = 0f; var blue = 0f; var weight = 0f
                        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                            val pixel = bitmap.getPixel(x, y)
                            val a = android.graphics.Color.alpha(pixel) / 255f
                            red += android.graphics.Color.red(pixel) * a
                            green += android.graphics.Color.green(pixel) * a
                            blue += android.graphics.Color.blue(pixel) * a
                            weight += a
                        }
                        if (weight > 0f) Color(red / weight / 255f, green / weight / 255f, blue / weight / 255f)
                        else Color.Transparent
                    }.getOrDefault(Color.Transparent)
                    onAmbience(color)
                }
            })
    }
}

@Composable
fun MusicTrackCard(
    block: MusicBlock,
    selected: Boolean = true,
    onActivate: () -> Unit = {},
    ambience: Color = Color.Transparent,
    onAmbience: (Color) -> Unit = {},
    showActions: Boolean = true,
    /** Live sound level 0..1 while the preview plays (0 when stopped). */
    onLevel: (Float) -> Unit = {},
    /** Drawn as the card's cover (CatalogHeroCover) instead of the compact row. */
    hero: Boolean = false
) {
    val context = LocalContext.current
    val currentShowActions by rememberUpdatedState(showActions)
    val lifecycle = LocalLifecycleOwner.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val attributes = remember { AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build() }
    var player by remember(block.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(block.id) { mutableStateOf(false) }
    var loading by remember(block.id) { mutableStateOf(false) }
    var ready by remember(block.id) { mutableStateOf(false) }
    var error by remember(block.id) { mutableStateOf<String?>(null) }
    var duration by remember(block.id) { mutableLongStateOf(block.previewDurationMillis ?: 0L) }
    var position by remember(block.id) { mutableLongStateOf(0L) }
    var focusRequest by remember(block.id) { mutableStateOf<AudioFocusRequest?>(null) }

    val musicSurfaceModifier = Modifier.fillMaxWidth()
    val currentOnLevel by rememberUpdatedState(onLevel)

    fun release() {
        val previous = player
        player = null
        runCatching { previous?.release() }
        focusRequest?.let { runCatching { audioManager?.abandonAudioFocusRequest(it) } }
        focusRequest = null
        playing = false
        loading = false
        ready = false
        position = 0L
    }
    fun start(current: MediaPlayer) {
        if (!currentShowActions) { release(); return }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                if (change < 0) release()
            }.build()
        focusRequest = request
        if (audioManager?.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            release()
            error = "Önizleme şu anda oynatılamıyor."
            return
        }
        runCatching { current.start() }.onSuccess { playing = true }.onFailure {
            release(); error = "Önizleme oynatılamadı."
        }
    }
    fun toggle() {
        if (loading) return
        if (playing) {
            runCatching { player?.pause() }
            playing = false
            focusRequest?.let { runCatching { audioManager?.abandonAudioFocusRequest(it) } }
            focusRequest = null
            return
        }
        onActivate()
        error = null
        val existing = player
        if (ready && existing != null) { start(existing); return }
        val url = block.previewUrl?.takeIf { it.startsWith("https://") } ?: return
        loading = true
        val created = MediaPlayer()
        player = created
        runCatching {
            created.setAudioAttributes(attributes)
            created.setOnPreparedListener { prepared ->
                if (player === prepared) {
                    loading = false
                    ready = true
                    duration = runCatching { prepared.duration.toLong() }.getOrDefault(0L)
                    start(prepared)
                }
            }
            created.setOnCompletionListener { release() }
            created.setOnErrorListener { _, _, _ ->
                release(); error = "Önizleme yüklenemedi. Yeniden deneyebilirsin."; true
            }
            created.setDataSource(url)
            created.prepareAsync()
        }.onFailure { release(); error = "Önizleme yüklenemedi." }
    }

    LaunchedEffect(selected, showActions) { if (!selected || !showActions) release() }
    // The whole block reacts to the music, like the voice block does to the voice. With microphone permission the
    // real sound of the preview is measured (Android's Visualizer needs it); otherwise a soft beat-like pulse.
    LaunchedEffect(playing, player) {
        val current = player
        if (!playing || current == null) { currentOnLevel(0f); return@LaunchedEffect }
        val canMeasure = androidx.core.content.ContextCompat.checkSelfPermission(context,
            android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val visualizer = if (canMeasure) runCatching {
            android.media.audiofx.Visualizer(current.audioSessionId).apply {
                captureSize = android.media.audiofx.Visualizer.getCaptureSizeRange()[0].coerceAtLeast(128)
                setDataCaptureListener(object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: android.media.audiofx.Visualizer?, wave: ByteArray?, rate: Int) {
                        val bytes = wave ?: return
                        var sum = 0.0
                        for (b in bytes) { val x = ((b.toInt() and 0xFF) - 128) / 128.0; sum += x * x }
                        val rms = kotlin.math.sqrt(sum / bytes.size.coerceAtLeast(1)).toFloat()
                        currentOnLevel(kotlin.math.sqrt((rms * 2.6f).coerceIn(0f, 1f)))
                    }
                    override fun onFftDataCapture(v: android.media.audiofx.Visualizer?, fft: ByteArray?, rate: Int) {}
                }, (android.media.audiofx.Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(10_000), true, false)
                enabled = true
            }
        }.getOrNull() else null
        try {
            if (visualizer != null) kotlinx.coroutines.awaitCancellation()
            var t = 0.0
            while (true) {
                t += 0.09
                val beat = (kotlin.math.sin(t * 7.0) * 0.5 + 0.5).let { it * it }
                currentOnLevel((0.30 + 0.35 * beat + 0.10 * kotlin.math.sin(t * 2.3)).toFloat().coerceIn(0f, 1f))
                delay(90L)
            }
        } finally {
            visualizer?.let { runCatching { it.enabled = false; it.release() } }
            currentOnLevel(0f)
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
            delay(250L)
        }
    }
    DisposableEffect(block.id, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) release() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); release() }
    }
    // Spotify blocks: the logo turns the card over to its Spotify Code (scan it with the Spotify camera).
    val spotifyCodeId = block.providerTrackId?.takeIf { block.provider == "Spotify" && Regex("[A-Za-z0-9]{22}").matches(it) }
    var flipped by remember(block.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(480, easing = FastOutSlowInEasing), label = "spotifyCodeFlip")
    val showingBack = rotation > 90f
    Box(Modifier.fillMaxWidth().graphicsLayer { rotationY = rotation; cameraDistance = 14f * density }) {
    if (hero) {
        val opensIn = remember(block.provider) { musicOpensIn(context, block) }
        Box(Modifier.fillMaxWidth().alpha(if (showingBack) 0f else 1f)) {
            CatalogHeroCover(
                imageUrl = block.artworkUrl?.replace("100x100bb", "600x600bb"), posterAspect = 1f,
                title = block.title, subtitle = listOfNotNull(block.artist, block.album).joinToString(" · "),
                placeholder = ActionGlyph.Music, ambience = ambience, onAmbience = onAmbience
            ) {
                if (showActions) {
                    if (block.previewUrl?.startsWith("https://") == true) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { toggle() }, enabled = !loading, modifier = Modifier.size(48.dp)
                                .clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).semantics {
                                    contentDescription = if (playing) "Önizlemeyi duraklat" else "Kısa önizlemeyi oynat"
                                }) {
                                if (loading) com.cagri.real.ui.components.RealSpinner(Modifier.size(20.dp), color = Color.White, strokeWidth = 1.5.dp)
                                else ActionIcon(if (playing) ActionGlyph.Pause else ActionGlyph.Play, Modifier.size(20.dp), Color.White)
                            }
                            Text(if (playing || position > 0) formatVoiceDuration(position)
                                else if (duration > 0) formatVoiceDuration(duration) else "Önizleme",
                                color = Color(0xFFB8B8C2), style = RealType.detailBlockMetaSecondary)
                        }
                    }
                    error?.let { Text(it, color = Color(0xFFE0B5A8), style = RealType.detailBlockMetaSecondary) }
                    HeroOpenRow("${musicProviderLabel(opensIn)}'da aç ↗".replace("Music'da", "Music'te"),
                        onOpen = { release(); openMusicBlock(context, block) }) {
                        // The source at the bottom right; on Spotify it turns the cover over to its Spotify Code.
                        MusicSourceLogo(opensIn, 22.dp, Modifier
                            .then(if (spotifyCodeId != null) Modifier.clip(CircleShape).clickable(enabled = !showingBack) { flipped = true } else Modifier)
                            .padding(6.dp)
                            .semantics { contentDescription = if (spotifyCodeId != null) "Spotify kodunu göster" else musicProviderLabel(opensIn) })
                    }
                    if (block.provider == "iTunes" && block.previewUrl != null) {
                        Text("Preview provided courtesy of iTunes", color = Color(0xFF868690), fontSize = 10.sp)
                    }
                }
            }
        }
    } else {
    Column(modifier = musicSurfaceModifier.alpha(if (showingBack) 0f else 1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth()) {
        val opensIn = remember(block.provider) { musicOpensIn(context, block) }
        MusicSourceLogo(opensIn, 18.dp, Modifier.align(Alignment.TopEnd)
            .then(if (spotifyCodeId != null) Modifier.clip(CircleShape).clickable(enabled = !showingBack) { flipped = true } else Modifier)
            .semantics { contentDescription = if (spotifyCodeId != null) "Spotify kodunu göster" else musicProviderLabel(opensIn) })
        Row(Modifier.padding(end = 26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicArtwork(block, Modifier.size(92.dp), onAmbience)
            Column(Modifier.weight(1f)) {
                Text(block.title, color = Color(0xFFF1F1F4), style = RealType.detailMusicTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(block.artist, color = Color(0xFFB1B1BC), style = RealType.detailBlockMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        }
        if (showActions) {
            if (block.previewUrl?.startsWith("https://") == true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { toggle() }, enabled = !loading, modifier = Modifier.size(48.dp).semantics {
                        contentDescription = if (playing) "Önizlemeyi duraklat" else "Kısa önizlemeyi oynat"
                    }) {
                        if (loading) com.cagri.real.ui.components.RealSpinner(Modifier.size(20.dp), color = Color.White, strokeWidth = 1.5.dp)
                        else ActionIcon(if (playing) ActionGlyph.Pause else ActionGlyph.Play, Modifier.size(20.dp), Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(if (playing || position > 0) formatVoiceDuration(position)
                        else if (duration > 0) formatVoiceDuration(duration) else "Önizleme",
                        color = Color(0xFFB8B8C2), style = RealType.detailBlockMetaSecondary)
                }
            } else Text("Önizleme yok", color = Color(0xFF95959F), style = RealType.detailBlockMetaSecondary)
            error?.let { Text(it, color = Color(0xFFE0B5A8), style = RealType.detailBlockMetaSecondary) }
            TextButton(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                .clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.07f)), onClick = {
                release()
                openMusicBlock(context, block)
            }) {
                Text("${musicProviderLabel(musicOpensIn(context, block))}'da aç ↗".replace("Music'da", "Music'te"),
                    color = Color(0xFFD0D0D8), style = RealType.detailBlockMetaSecondary)
            }
            if (block.provider == "iTunes" && block.previewUrl != null) {
                Text("Preview provided courtesy of iTunes", color = Color(0xFF868690), fontSize = 10.sp)
            }
        }
    }
    }
    if (showingBack && spotifyCodeId != null) {
        // Back face: drawn mirrored so it reads correctly once turned. Tap anywhere to turn back.
        Column(Modifier.matchParentSize().graphicsLayer { rotationY = 180f }
            .clip(RoundedCornerShape(18.dp)).background(Color.Black)
            .clickable { flipped = false }.padding(16.dp)
            .semantics { contentDescription = "Spotify kodu. Geri dönmek için dokun." },
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(model = "https://scannables.scdn.co/uri/plain/png/000000/white/640/spotify:track:$spotifyCodeId",
                contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(4f), contentScale = ContentScale.Fit)
        }
    }
    }
}

/**
 * Opens the song in the phone's own app (Spotify / Apple Music / YouTube Music) when it is installed, otherwise in the
 * browser. A plain https link would often open the website even with the app installed.
 */
/**
 * Where an Apple Music song opens for this person: in Spotify when Ayarlar → Müzik uygulaması is Spotify (the same
 * card opens in Apple Music for someone else), otherwise in its own app.
 */
internal fun musicOpensIn(context: android.content.Context, block: MusicBlock): String =
    if (block.provider in setOf("iTunes", "Apple Music") && MusicSource.saved(context) == MusicSource.Spotify) "Spotify" else block.provider

fun openMusicBlock(context: android.content.Context, block: MusicBlock) {
    if (block.provider != "Spotify" && musicOpensIn(context, block) == "Spotify") { searchSpotify(context, block); return }
    val web = runCatching { Uri.parse(block.providerUrl) }.getOrNull()?.takeIf { it.scheme in listOf("http", "https") }
    val attempts = buildList {
        when (block.provider) {
            "Spotify" -> {
                block.providerTrackId?.takeIf { Regex("[A-Za-z0-9]{22}").matches(it) }?.let {
                    add(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:track:$it")).setPackage("com.spotify.music"))
                }
                web?.let { add(Intent(Intent.ACTION_VIEW, it).setPackage("com.spotify.music")) }
            }
            "iTunes", "Apple Music" -> web?.let { add(Intent(Intent.ACTION_VIEW, it).setPackage("com.apple.android.music")) }
            "YouTube Music" -> web?.let { add(Intent(Intent.ACTION_VIEW, it).setPackage("com.google.android.apps.youtube.music")) }
        }
        web?.let { add(Intent(Intent.ACTION_VIEW, it)) }
    }
    if (attempts.isEmpty()) { RealToast.show(context, "Şarkı bağlantısı kullanılamıyor"); return }
    for (intent in attempts) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
        catch (_: android.content.ActivityNotFoundException) {}
        catch (_: SecurityException) {}
    }
    RealToast.show(context, "Şarkı bağlantısı açılamadı")
}

/** Spotify has no open catalog for apps: the song is opened as a Spotify search for "title artist". */
private fun searchSpotify(context: android.content.Context, music: MusicBlock) {
    val query = listOf(music.title, music.artist).filter { it.isNotBlank() }.joinToString(" ")
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(query))).setPackage("com.spotify.music"),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/" + Uri.encode(query)))
    )
    for (intent in attempts) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
        catch (_: android.content.ActivityNotFoundException) {}
    }
    RealToast.show(context, "Spotify açılamadı")
}
