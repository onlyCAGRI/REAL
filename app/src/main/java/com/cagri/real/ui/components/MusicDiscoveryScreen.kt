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
        // From the deck straight back to Yaşam; the picker was already done.
        if (page == MusicPage.FILTERS || page == MusicPage.DISCOVER || (startInList && page == MusicPage.LIST)) onDismiss()
        else { page = MusicPage.FILTERS; error = null }
    }
    BackHandler(onBack = ::back)
    LaunchedEffect(repo) {
        runCatching { repo.load() }.onSuccess { snapshot = it; if (selection.isEmpty()) selection = ArrayList(it.selection.filter { k -> MusicMood.fromKey(k) == null }
            .let { keys -> keys.filter { k -> MusicGenre.fromKey(k) == null } + keys.filter { k -> MusicGenre.fromKey(k) != null }.take(2) }) }
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
                    if (filterHeader == null) DiscoveryHeadline("Ne dinlesek", Modifier.padding(horizontal = 20.dp), TextAlign.Start)
                    else filterHeader()
                    // One question at a time: 2 genres at most, then (optional) one era.
                    DiscoveryStepper(
                        steps = listOf(
                            DiscoveryStep("Hangi türde müzik?", "En fazla 2 tür", 2, false,
                                listOf(null to MusicGenre.entries.map { StepOption(it.key, it.label) })),
                            DiscoveryStep("Hangi dönemden?", "İstersen bir dönem · Apple Music ${MusicCatalog.storefrontName()} listeleri", 1, true,
                                listOf(null to (listOf(StepOption(AllErasKey, "Hepsi")) + MusicEra.entries.map { StepOption(it.key, it.label) }))),
                        ),
                        // "Hepsi" is shown chosen when no era is; picking it clears the era.
                        selection = selection.toSet().let { s -> if (MusicEra.of(s) == null) s + AllErasKey else s },
                        onSelection = { selection = ArrayList(it - AllErasKey) },
                        accent = MusicTone, loading = loading, enabled = true, reduceAnimations = reduceAnimations,
                        doneLabel = "şarkıları getir", modifier = Modifier.weight(1f).then(filterBodyModifier),
                        rare = { chosen, key -> DiscoveryRarePairs.rare(DiscoveryRarePairs.music, chosen, key) }
                    ) { discover() }
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

/** Opens the song in the person's music app (Ayarlar → Müzik uygulaması): Apple Music, or a Spotify search. */
internal fun openInPreferredMusicApp(context: android.content.Context, music: MusicBlock) = openMusicBlock(context, music)

@Composable
private fun MusicDeck(
    cards: List<MusicCard>, reduceAnimations: Boolean, listMode: Boolean, modifier: Modifier,
    onAddMusic: suspend (MusicBlock) -> Long?, onDecision: suspend (MusicCard, MusicDiscoveryRepository.Action) -> Boolean
) {
    val context = LocalContext.current
    val card = cards.first()
    // The front poster loads first; the ones behind it start once it is on screen.
    // The deck's first front cover loads alone; after that the covers behind always load, and the next ones are
    // fetched ahead so a swipe never shows an empty cover.
    var frontReady by remember { mutableStateOf(card.music.artworkUrl.isNullOrBlank()) }
    val prefetchContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(frontReady, cards.take(8).map { it.key }) {
        if (!frontReady) return@LaunchedEffect
        cards.drop(1).take(6).mapNotNull { it.music.artworkUrl }.forEach { u ->
            coil.Coil.imageLoader(prefetchContext).enqueue(coil.request.ImageRequest.Builder(prefetchContext).data(u).build())
        }
    }
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
    // The next songs' previews are prepared in the background, so a swipe starts the next one at once.
    val preloader = remember { PreviewPreloader() }
    DisposableEffect(Unit) { onDispose { preloader.releaseAll() } }
    val upcoming = cards.take(3).mapNotNull { it.music.previewUrl?.takeIf { u -> u.startsWith("https://") } }
    LaunchedEffect(upcoming) {
        preloader.keepOnly(upcoming.toSet())
        upcoming.forEach { preloader.prefetch(it) }
    }
    LaunchedEffect(card.key, paused, lifecycleActive, busy) {
        level = 0f
        if (previewUrl == null || paused || !lifecycleActive || busy) return@LaunchedEffect
        val player = preloader.take(previewUrl) ?: return@LaunchedEffect
        try {
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
                                    offset.x > threshold -> decide(if (listMode) MusicDiscoveryRepository.Action.REMOVE else MusicDiscoveryRepository.Action.SAVE, Offset(threshold * 6, 0f))
                                    offset.x < -threshold -> decide(if (listMode) MusicDiscoveryRepository.Action.KEEP else MusicDiscoveryRepository.Action.SKIP, Offset(-threshold * 6, 0f))
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
                    AsyncImage(if (depth == 0 || frontReady) item.music.artworkUrl else null, item.music.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                        onSuccess = { if (depth == 0) frontReady = true }, onError = { if (depth == 0) frontReady = true })
                    if (depth == 0) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(side * .42f)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))))
                        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                            Text(item.music.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.music.artist, color = Color.White.copy(alpha = .75f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        MusicSourceLogo(musicOpensIn(LocalContext.current, item.music), 20.dp, Modifier.align(Alignment.TopEnd).padding(14.dp))
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
            // Same place as on the film / game screens.
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (maxHeight - 64.dp).coerceAtLeast(top + side + 12.dp)))
        if (listMode) SwipeTutorial("music_list", "Kaldır", "Sona at", upLabel = "Uygulamada aç")
        else SwipeTutorial("music", "Kaydet", "Geç", upLabel = "Uygulamada aç")
    }
}

/** Prepared MediaPlayers for the next previews (at most 3); handed over once, released when no longer needed. */
private class PreviewPreloader {
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val players = LinkedHashMap<String, Pair<MediaPlayer, kotlinx.coroutines.CompletableDeferred<Boolean>>>()

    fun prefetch(url: String) {
        if (players.containsKey(url)) return
        val player = MediaPlayer()
        val ready = kotlinx.coroutines.CompletableDeferred<Boolean>()
        try {
            player.setAudioAttributes(attributes)
            player.isLooping = true
            player.setDataSource(url)
            player.setOnPreparedListener { ready.complete(true) }
            player.setOnErrorListener { _, _, _ -> ready.complete(false); true }
            player.prepareAsync()
        } catch (_: Exception) { player.release(); return }
        players[url] = player to ready
        while (players.size > 3) players.remove(players.keys.first())?.first?.release()
    }

    /** The prepared player for [url] (waits if it is still preparing); the caller releases it. */
    suspend fun take(url: String): MediaPlayer? {
        prefetch(url)
        val (player, ready) = players.remove(url) ?: return null
        val ok = try { ready.await() } catch (e: CancellationException) { player.release(); throw e }
        return if (ok) player else { player.release(); null }
    }

    fun keepOnly(urls: Set<String>) { players.keys.filter { it !in urls }.forEach { players.remove(it)?.first?.release() } }

    fun releaseAll() { players.values.forEach { it.first.release() }; players.clear() }
}

/** Stepper key for "Hepsi" in the era question; never saved. */
private const val AllErasKey = "era_all"
