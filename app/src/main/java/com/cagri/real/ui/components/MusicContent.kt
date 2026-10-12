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
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF101012),
        contentColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val panel = if (designStyle == DesignStyle.LiquidGlass) Modifier.liquidChrome(corners = 24.dp) else Modifier
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).then(panel).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Müzik", style = RealType.detailDialogTitle)
            OutlinedTextField(value = query, onValueChange = { query = it.take(200) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Şarkı veya sanatçı ara") }, shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF85858D),
                    unfocusedBorderColor = Color(0xFF424248), cursorColor = Color.White))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Kapat", color = Color(0xFFBCBCC5)) }
                TextButton(enabled = query.trim().isNotEmpty() && !searching, onClick = {
                    searching = true
                    message = null
                    results = emptyList()
                    scope.launch {
                        try {
                            results = MusicCatalog.search(query)
                            if (results.isEmpty()) message = "Sonuç bulunamadı. Başka bir şarkı veya sanatçı dene."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            message = "Müzik araması yapılamadı. Bağlantını kontrol edip yeniden dene."
                        } finally {
                            searching = false
                        }
                    }
                }) { Text(if (searching) "Aranıyor…" else "Ara", color = Color.White) }
            }
            message?.let { Text(it, style = RealType.detailBlockMeta, color = Color(0xFFBCBCC5)) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results, key = { it.id }) { track ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(track.copy(createdAt = System.currentTimeMillis())) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MusicArtwork(track, Modifier.size(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = Color(0xFFA5A5B0), style = RealType.detailBlockMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ActionIcon(ActionGlyph.Add, Modifier.size(18.dp), Color.White)
                    }
                }
            }
            Text("iTunes • Türkiye kataloğu", style = RealType.detailAttribution, color = Color(0xFF95959F))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun MusicContentSection(blocks: List<MusicBlock>, designStyle: DesignStyle, editing: Boolean, onRemove: (String) -> Unit,
    display: CatalogCardDisplay = CatalogCardDisplay.FULL, showActions: Boolean = true) {
    var activeId by remember { mutableStateOf<String?>(null) }
    blocks.forEach { block ->
        key(block.id) {
            var ambience by remember(block.artworkUrl) { mutableStateOf(Color.Transparent) }
            if (display != CatalogCardDisplay.FULL) {
                CatalogCompactContent(block.title, display, 1f, editing, { onRemove(block.id) }) { modifier ->
                    MusicArtwork(block, modifier)
                }
            } else {
                TimelineContent(designStyle, ambience) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Müzik", color = Color(0xFFA5A5B0), style = RealType.detailBlockLabel, modifier = Modifier.weight(1f))
                        if (editing) IconButton(onClick = { onRemove(block.id) }, modifier = Modifier.size(40.dp)) {
                            ActionIcon(ActionGlyph.Close, Modifier.size(15.dp).semantics { contentDescription = "Müziği kaldır" }, Color(0xFFB8B8C2))
                        }
                    }
                    MusicTrackCard(
                        block = block,
                        showActions = showActions,
                        selected = activeId == block.id,
                        onActivate = { activeId = block.id },
                        ambience = ambience,
                        onAmbience = { ambience = it }
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
    showActions: Boolean = true
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

    // The preview now uses the same colour-first motion language as the redesigned microphone.
    // Instead of dot/equalizer shapes, the artwork-derived ambience moves softly through the card.
    // A stopped preview never reads this motion; do not keep its frame loop alive.
    val colourMotion = if (playing) {
        rememberInfiniteTransition(label = "musicColourMotion").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Reverse),
            label = "musicColourMotionValue"
        )
    } else rememberUpdatedState(0f)
    val musicTint = if (ambience == Color.Transparent) Color(0xFF777783) else ambience
    val musicSurfaceModifier = Modifier
        .fillMaxWidth()
        .drawBehind {
            if (playing) {
                val motion = colourMotion.value
                val centerX = size.width * (0.20f + 0.60f * motion)
                val strength = 0.12f + 0.10f * motion
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(musicTint.copy(alpha = strength), Color.Transparent),
                        center = Offset(centerX, size.height * 0.62f),
                        radius = size.width * (0.48f + 0.12f * motion)
                    )
                )
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(musicTint.copy(alpha = 0.06f + 0.05f * motion), Color.Transparent)
                    )
                )
            }
        }

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
    Column(modifier = musicSurfaceModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicArtwork(block, Modifier.size(92.dp), onAmbience)
            Column(Modifier.weight(1f)) {
                Text(block.title, color = Color(0xFFF1F1F4), style = RealType.detailMusicTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(block.artist, color = Color(0xFFB1B1BC), style = RealType.detailBlockMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(block.provider, color = Color(0xFF868690), style = RealType.detailAttribution)
            }
        }
        if (showActions) {
            if (block.previewUrl?.startsWith("https://") == true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { toggle() }, enabled = !loading, modifier = Modifier.size(48.dp).semantics {
                        contentDescription = if (playing) "Önizlemeyi duraklat" else "Kısa önizlemeyi oynat"
                    }) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 1.5.dp)
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
                val uri = runCatching { Uri.parse(block.providerUrl) }.getOrNull()
                if (uri?.scheme in listOf("http", "https")) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
                        Toast.makeText(context, "Şarkı bağlantısı açılamadı", Toast.LENGTH_SHORT).show()
                    }
                } else Toast.makeText(context, "Şarkı bağlantısı kullanılamıyor", Toast.LENGTH_SHORT).show()
            }) {
                Text(if (block.provider == "iTunes") "iTunes’da aç ↗" else "${block.provider} üzerinde aç ↗", color = Color(0xFFD0D0D8), style = RealType.detailBlockMetaSecondary)
            }
            if (block.provider == "iTunes" && block.previewUrl != null) {
                Text("Preview provided courtesy of iTunes", color = Color(0xFF868690), fontSize = 10.sp)
            }
        }
    }
}
