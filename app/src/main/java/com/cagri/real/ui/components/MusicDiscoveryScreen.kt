package com.cagri.real.ui.components

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.cagri.real.data.MusicBlock
import com.cagri.real.data.MusicCatalog
import com.cagri.real.discovery.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

internal val MusicTone = Color(0xFFE0457B)
private enum class MusicPage { FILTERS, DISCOVER, LIST }

/**
 * Müzik keşfi: pick genres / a mood → song covers in a deck; the top song's preview plays and the whole cover glows
 * with it. Right = save to "Müzik listem" · left = pass · up = open in your music app (Ayarlar: Apple Music / Spotify).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicDiscoveryScreen(
    reduceAnimations: Boolean,
    onDismiss: () -> Unit,
    onAddMusic: suspend (MusicBlock) -> Long?,
    reduceDynamicBlur: Boolean = false,
    startInList: Boolean = false,
    filterHeader: (@Composable () -> Unit)? = null,
    filterBodyModifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
    val repo = remember(context, uid) { MusicDiscoveryRepository(context, uid) }
    val scope = rememberCoroutineScope()
    var snapshot by remember(repo) { mutableStateOf<MusicDiscoveryRepository.Snapshot?>(null) }
    var selection by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var page by rememberSaveable { mutableStateOf(if (startInList) MusicPage.LIST else MusicPage.FILTERS) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun back() {
        if (page == MusicPage.FILTERS || (startInList && page == MusicPage.LIST)) onDismiss() else { page = MusicPage.FILTERS; error = null }
    }
    BackHandler(onBack = ::back)
    LaunchedEffect(repo) {
        runCatching { repo.load() }.onSuccess { snapshot = it; if (selection.isEmpty()) selection = ArrayList(it.selection) }
    }
    fun discover() {
        if (loading) return
        loading = true; error = null
        scope.launch {
            try {
                snapshot = repo.fetch(selection.toSet())
                if (page == MusicPage.FILTERS || page == MusicPage.DISCOVER) page = MusicPage.DISCOVER
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Şarkılar alınamadı." }
            finally { loading = false }
        }
    }
    val listMode = page == MusicPage.LIST
    val cards = if (listMode) snapshot?.list.orEmpty() else snapshot?.cards.orEmpty()
    LaunchedEffect(page, cards.size, loading) {
        if (page == MusicPage.DISCOVER && !loading && error == null && cards.size <= 3 && snapshot != null) discover()
    }
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) }
    }) {
        PosterAmbient(if (page != MusicPage.FILTERS) cards.firstOrNull()?.music?.artworkUrl else null, reduceAnimations, reduceDynamicBlur)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (page != MusicPage.FILTERS || filterHeader == null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RealBackButton(onBack = ::back)
                }
            }
            when (page) {
                MusicPage.FILTERS -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        if (filterHeader == null) DiscoveryHeadline("Ne dinlesek", Modifier.padding(horizontal = 20.dp), TextAlign.Start)
                        else filterHeader()
                        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp).then(filterBodyModifier),
                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Tür", style = com.cagri.real.ui.theme.RealType.editorialSubtitle, color = Color.White.copy(alpha = .8f))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MusicGenre.entries.forEach { g ->
                                    val on = g.key in selection
                                    DiscoveryButton(g.label, on, !loading && (on || selection.size < 6),
                                        onClick = { selection = ArrayList(if (on) selection - g.key else selection + g.key) })
                                }
                            }
                            Text("Nasıl hissettirsin?", style = com.cagri.real.ui.theme.RealType.editorialSubtitle, color = Color.White.copy(alpha = .8f))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MusicMood.entries.forEach { m ->
                                    val on = m.key in selection
                                    DiscoveryButton(m.label, on, !loading && (on || selection.size < 6),
                                        onClick = { selection = ArrayList(if (on) selection - m.key else selection + m.key) })
                                }
                            }
                            Text("Apple Music · ${MusicCatalog.storefrontName()} listeleri", color = Color(0xFF5E5E66), fontSize = 11.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        DiscoveryLoadingButton("Şarkıları getir", loading, true, MusicTone, reduceAnimations) { discover() }
                    }
                }
                MusicPage.DISCOVER, MusicPage.LIST -> {
                    DiscoveryHeadline(if (listMode) "MÜZİK LİSTEM" else "SANA GÖRE", Modifier.padding(top = 2.dp, bottom = 6.dp), small = listMode)
                    if (cards.isNotEmpty()) {
                        MusicDeck(cards, reduceAnimations, listMode, Modifier.weight(1f).padding(horizontal = 14.dp), onAddMusic) { card, action ->
                            error = null
                            try { snapshot = repo.act(card, action); true }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "İşlem tamamlanamadı. Tekrar deneyebilirsin."; false }
                        }
                    } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(if (listMode) "Müzik listen henüz boş. Sağa kaydırdığın şarkılar buraya gelir." else
                                if (loading) "Şarkılar hazırlanıyor…" else "Bu destenin sonuna geldin.", color = Color.White, textAlign = TextAlign.Center)
                            if (!listMode && !loading) DiscoveryButton("Daha fazla getir", onClick = { discover() })
                        }
                    }
                }
            }
            error?.let { Text(it, color = Color(0xFFE6BCB1), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
        }
    }
}

/** Opens the song in the person's music app: Apple Music exactly, Spotify by searching "title artist". */
internal fun openInPreferredMusicApp(context: android.content.Context, music: MusicBlock) {
    if (MusicSource.saved(context) == MusicSource.Spotify) {
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
    } else openMusicBlock(context, music)
}

@Composable
private fun MusicDeck(
    cards: List<MusicCard>, reduceAnimations: Boolean, listMode: Boolean, modifier: Modifier,
    onAddMusic: suspend (MusicBlock) -> Long?, onDecision: suspend (MusicCard, MusicDiscoveryRepository.Action) -> Boolean
) {
    val context = LocalContext.current
    val card = cards.first()
    val scope = rememberCoroutineScope()
    var offset by remember(card.key) { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val latestDecision by rememberUpdatedState(onDecision)
    val currentBusy by rememberUpdatedState(busy)
    val threshold = with(LocalDensity.current) { 85.dp.toPx() }
    // Preview of the top song: plays on its own; a tap pauses / resumes. While it plays, the cover's own colours
    // behind the deck grow and fade softly (nothing is drawn over the cover).
    var paused by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    val smoothLevel by animateFloatAsState(level, tween(140), label = "music-deck-level")
    var lifecycleActive by remember { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) lifecycleActive = false
            if (e == Lifecycle.Event.ON_RESUME) lifecycleActive = true
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val previewUrl = card.music.previewUrl?.takeIf { it.startsWith("https://") }
    LaunchedEffect(card.key, paused, lifecycleActive, busy) {
        level = 0f
        if (previewUrl == null || paused || !lifecycleActive || busy) return@LaunchedEffect
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            player.isLooping = true
            player.setDataSource(previewUrl)
            val prepared = kotlinx.coroutines.CompletableDeferred<Unit>()
            player.setOnPreparedListener { prepared.complete(Unit) }
            player.setOnErrorListener { _, _, _ -> prepared.completeExceptionally(IllegalStateException()); true }
            player.prepareAsync()
            prepared.await()
            player.start()
            var t = 0.0
            while (true) {
                t += 0.09
                val beat = (kotlin.math.sin(t * 7.0) * 0.5 + 0.5).let { it * it }
                level = (0.25 + 0.45 * beat + 0.10 * kotlin.math.sin(t * 2.3)).toFloat().coerceIn(0f, 1f)
                delay(90L)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {}
        finally { level = 0f; runCatching { player.stop() }; player.release() }
    }
    fun settle() {
        settling?.cancel()
        if (reduceAnimations) offset = Offset.Zero
        else settling = scope.launch { Animatable(offset, Offset.VectorConverter).animateTo(Offset.Zero, spring()) { offset = value } }
    }
    fun decide(action: MusicDiscoveryRepository.Action, flight: Offset) {
        if (busy) return
        settling?.cancel(); busy = true
        scope.launch {
            try {
                if (!reduceAnimations) Animatable(offset, Offset.VectorConverter).animateTo(flight, tween(240)) { offset = value }
                if (!latestDecision(card, action)) {
                    if (reduceAnimations) offset = Offset.Zero else Animatable(offset, Offset.VectorConverter).animateTo(Offset.Zero, spring()) { offset = value }
                } else { offset = Offset.Zero; paused = false }
            } finally { busy = false }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = 34.dp, bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
        val side = min(maxWidth.value * .96f, (maxHeight.value - 76f).coerceAtLeast(1f)).dp
        // A square cover is shorter than a film poster: the cover and its buttons sit in the middle of the free space.
        val top = ((maxHeight - 76.dp - side) / 2).coerceAtLeast(0.dp)
        val visible = cards.take(4)
        PosterEdgeGlow(card.music.artworkUrl, reduceAnimations, Modifier.offset(y = top).fillMaxWidth().height(side), pulse = { smoothLevel })
        SwipeEdgeGlow({ offset }, threshold, listMode, upColor = MusicTone)
        for (depth in visible.indices.reversed()) {
            val item = visible[depth]
            key(item.key) {
                val stackDepth by animateFloatAsState(depth.toFloat(),
                    animationSpec = if (reduceAnimations) tween(0) else spring(dampingRatio = .85f, stiffness = 350f), label = "music-depth")
                val gestures = if (depth == 0) Modifier
                    .pointerInput(card.key) { detectTapGestures(onTap = { if (!currentBusy) paused = !paused }) }
                    .pointerInput(card.key, listMode) {
                        detectDragGestures(onDragStart = { if (!currentBusy) settling?.cancel() }, onDragCancel = { if (!currentBusy) settle() },
                            onDragEnd = {
                                if (!currentBusy) when {
                                    // Up: open the song in your music app (Ayarlar → Müzik uygulaması).
                                    -offset.y > threshold && -offset.y > abs(offset.x) -> { settle(); paused = true; openInPreferredMusicApp(context, card.music) }
                                    abs(offset.y) > abs(offset.x) -> settle()
                                    offset.x > threshold -> decide(if (listMode) MusicDiscoveryRepository.Action.KEEP else MusicDiscoveryRepository.Action.SAVE, Offset(threshold * 6, 0f))
                                    offset.x < -threshold -> decide(if (listMode) MusicDiscoveryRepository.Action.REMOVE else MusicDiscoveryRepository.Action.SKIP, Offset(-threshold * 6, 0f))
                                    else -> settle()
                                }
                            }) { change, delta -> change.consume(); if (!currentBusy) offset += delta }
                    } else Modifier
                val tint = Color(0xFF2D2D33)
                Box(Modifier.offset(y = top).size(side).zIndex((visible.size - depth).toFloat()).then(gestures).graphicsLayer {
                    val scale = 1f - stackDepth * .045f
                    scaleX = scale; scaleY = scale
                    translationY = -stackDepth * (10.dp.toPx() + .045f * size.height / 2f)
                    alpha = 1f - stackDepth * .1f
                    if (depth == 0) {
                        translationX = offset.x; translationY += offset.y
                        rotationZ = (offset.x / threshold * 7f).coerceIn(-24f, 24f)
                    }
                }.clip(RoundedCornerShape(18.dp)).background(Color(0xFF1B1B20))
                ) {
                    AsyncImage(item.music.artworkUrl, item.music.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (depth == 0) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(side * .42f)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))))
                        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                            Text(item.music.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.music.artist, color = Color.White.copy(alpha = .75f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        MusicSourceLogo(item.music.provider, 20.dp, Modifier.align(Alignment.TopEnd).padding(14.dp))
                        if (previewUrl == null) Text("Önizleme yok", color = Color.White.copy(alpha = .7f), fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = .7f)).padding(horizontal = 9.dp, vertical = 4.dp))
                        else if (paused) ActionIcon(ActionGlyph.Play, Modifier.align(Alignment.Center).size(44.dp), Color.White.copy(alpha = .9f))
                        if (busy) RealSpinner(Modifier.size(32.dp).align(Alignment.Center), color = Color.White, strokeWidth = 2.dp)
                    }
                }
            }
        }
        DiscoveryActionBar(null, busy, onDone = {}, accent = MusicTone,
            onSave = {
                if (!busy) scope.launch {
                    busy = true
                    try {
                        if (onAddMusic(card.music) != null) RealToast.show(context, "REAL'e eklendi")
                        else RealToast.show(context, "Kart eklenemedi. Tekrar deneyebilirsin.")
                    } finally { busy = false }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter).offset(y = top + side + 12.dp))
        SwipeTutorial("music", "Kaydet", "Geç", upLabel = "Uygulamada aç")
    }
}
