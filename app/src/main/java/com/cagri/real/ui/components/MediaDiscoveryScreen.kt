package com.cagri.real.ui.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cagri.real.data.MediaBlock
import com.cagri.real.discovery.*
import com.cagri.real.ui.theme.RealType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.abs
import kotlin.math.min

private val DiscoveryAccent = Color(0xFFDDD4C6)
private val DiscoveryBlue = Color(0xFF2F6BFF)   // seçili çipler, ana buton ve "REAL'e ekle"; oyun ekranındaki turuncunun mavi karşılığı
private const val PosterAspect = .667f
private enum class DiscoveryPage { ENTRY, FILTERS, DISCOVER, WATCHLIST }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaDiscoveryScreen(
    reduceAnimations: Boolean,
    onDismiss: () -> Unit,
    onAddMedia: suspend (MediaBlock) -> Long?,
    reduceDynamicBlur: Boolean = false,
    onAtEntry: (Boolean) -> Unit = {},
    /** Yaşam "İZLENECEK LİSTEM": giriş ekranı atlanır, doğrudan izlenecek listesi açılır; geri Yaşam'a döner. */
    startInWatchlist: Boolean = false,
    startInFilters: Boolean = false,
    filterHeader: (@Composable () -> Unit)? = null,
    filterBodyModifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val repo = remember(context, uid) { uid?.let { MediaDiscoveryRepository(context, it) } }
    val scope = rememberCoroutineScope()
    val detailRepository = remember(repo) { MediaDetailRepository() }
    var selectedDetail by remember(repo) { mutableStateOf<DiscoveryCard?>(null) }
    var detailOpen by remember(repo) { mutableStateOf(false) }
    var snapshot by remember(repo) { mutableStateOf<MediaDiscoveryRepository.Snapshot?>(null) }
    var ready by remember(repo) { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf("BOTH") }
    var filters by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var page by rememberSaveable { mutableStateOf(if (startInWatchlist) DiscoveryPage.WATCHLIST else if (startInFilters) DiscoveryPage.FILTERS else DiscoveryPage.ENTRY) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun back() {
        if (detailOpen) { detailOpen = false; return }
        if (page == DiscoveryPage.ENTRY || (startInWatchlist && page == DiscoveryPage.WATCHLIST) ||
            (startInFilters && page == DiscoveryPage.FILTERS)) onDismiss()
        else { page = if (startInFilters) DiscoveryPage.FILTERS else DiscoveryPage.ENTRY; error = null }
    }
    BackHandler(onBack = ::back)
    LaunchedEffect(page) { onAtEntry(page == DiscoveryPage.ENTRY) }
    LaunchedEffect(repo) {
        if (repo == null) { error = "Öneriler için REAL'e giriş yap."; ready = true; return@LaunchedEffect }
        try {
            val cached = repo.load(); snapshot = cached
            if (!startInFilters) { type = cached.type; filters = ArrayList(cached.filters) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Kaydedilmiş öneriler okunamadı." }
        ready = true
    }
    fun openFilters() {
        // Start a fresh draft without changing the saved deck/watchlist preferences.
        type = "BOTH"; filters = arrayListOf()
        page = DiscoveryPage.FILTERS; error = null
    }
    fun discover(useSavedPreferences: Boolean = false) {
        if (loading || repo == null || !ready) return
        val selectedType = if (useSavedPreferences) snapshot?.type ?: type else type
        val selectedFilters = if (useSavedPreferences) snapshot?.filters.orEmpty() else filters.toSet()
        loading = true; error = null
        scope.launch {
            try {
                if (!com.cagri.real.platform.android.access.ProAccess.allowed(com.cagri.real.access.RealFeature.MediaDiscovery)) return@launch
                snapshot = repo.fetch(selectedType, selectedFilters)
                // Back may have returned to the landing page while the request was running.
                if (page == DiscoveryPage.FILTERS || page == DiscoveryPage.DISCOVER) page = DiscoveryPage.DISCOVER
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Öneriler alınamadı." }
            finally { loading = false }
        }
    }
    val watchlist = page == DiscoveryPage.WATCHLIST
    val deckPage = page == DiscoveryPage.DISCOVER || watchlist
    val cards = if (watchlist) snapshot?.later.orEmpty() else snapshot?.cards.orEmpty()
    // Keep the deck stocked: when only a couple of posters are left, the next ones are fetched in the background.
    LaunchedEffect(page, cards.size, loading, error) {
        if (page == DiscoveryPage.DISCOVER && !loading && error == null && ready && repo != null && cards.size <= 2 &&
            snapshot != null && (snapshot?.cards?.isNotEmpty() == true || snapshot?.excluded?.isNotEmpty() == true)) {
            discover(useSavedPreferences = true)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) }
    }) {
        if (page == DiscoveryPage.ENTRY) EntryGlow(reduceAnimations)
        else PosterAmbient(if (deckPage) cards.firstOrNull()?.media?.artworkUrl else null, reduceAnimations, reduceDynamicBlur)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (page != DiscoveryPage.FILTERS || filterHeader == null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RealBackButton(onBack = ::back)
                }
            }
            when (page) {
                DiscoveryPage.ENTRY -> {
                    val savedCount = snapshot?.later?.size ?: 0
                    val canContinue = !snapshot?.cards.isNullOrEmpty() || !snapshot?.excluded.isNullOrEmpty()
                    EntryContent(
                        posterUrls = snapshot?.later.orEmpty().mapNotNull { it.media.artworkUrl }.take(3),
                        savedCount = savedCount, canContinue = canContinue, loading = loading, enabled = ready && !loading,
                        onPickGenre = ::openFilters,
                        onContinue = { page = DiscoveryPage.DISCOVER; error = null },
                        onWatchlist = { page = DiscoveryPage.WATCHLIST; error = null },
                        modifier = Modifier.weight(1f)
                    )
                }
                DiscoveryPage.FILTERS -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        if (filterHeader == null) DiscoveryHeadline("Ne izlesek", Modifier.padding(horizontal = 20.dp), TextAlign.Start)
                        else filterHeader()
                        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp).then(filterBodyModifier),
                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("BOTH" to "Film + Dizi", "MOVIE" to "Film", "TV" to "Dizi").forEach { (key, label) ->
                                DiscoveryButton(label, selected = type == key, enabled = !loading, onClick = { type = key })
                            }
                        }
                        @Composable
                        fun filterChips(options: List<DiscoveryFilter>) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                options.forEach { filter ->
                                    val selected = filter.key in filters
                                    val groupCount = filters.count { DiscoveryFilter.fromKey(it)?.selectionGroup == filter.selectionGroup }
                                    DiscoveryButton(filter.label, selected, !loading && (selected || groupCount < DiscoveryFilter.MAX_PER_GROUP), onClick = {
                                        filters = ArrayList(if (selected) filters - filter.key else filters + filter.key)
                                    })
                                }
                            }
                        }
                        Text("Tür", style = RealType.editorialSubtitle, color = Color.White.copy(alpha = .8f))
                        val genrePages = listOf("Türler" to DiscoveryFilter.entries.filter { it.genre }) +
                            DiscoveryFilter.entries.filter { it.themeGroup != null }.groupBy { it.themeGroup }.map { (name, options) -> name.orEmpty() to options }
                        DiscoverySelectionPager(
                            titles = genrePages.map { it.first },
                            selectedCounts = genrePages.map { (_, options) -> options.count { it.key in filters } },
                            reduceAnimations = reduceAnimations
                        ) { index, _ ->
                            filterChips(genrePages[index].second)
                        }
                        Text("Nasıl hissettirsin?", style = RealType.editorialSubtitle, color = Color.White.copy(alpha = .8f))
                        val feelingPages = DiscoveryFilter.entries.filter { !it.genre && it.themeGroup == null }.chunked(4)
                        DiscoverySelectionPager(
                            titles = feelingPages.mapIndexed { index, _ -> if (index == 0) "Hisler" else "Diğer hisler" },
                            selectedCounts = feelingPages.map { options -> options.count { it.key in filters } },
                            reduceAnimations = reduceAnimations
                        ) { index, _ ->
                            filterChips(feelingPages[index])
                        }
                        // Catalog credit only (TVmaze / TheTVDB attribution), small and quiet.
                        Text("TVmaze · TheTVDB", color = Color(0xFF5E5E66), fontSize = 11.sp)
                    }
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        DiscoveryLoadingButton("Afişleri getir", loading, ready && repo != null, DiscoveryBlue, reduceAnimations) { discover() }
                    }
                }
                DiscoveryPage.DISCOVER, DiscoveryPage.WATCHLIST -> {
                    DiscoveryHeadline(if (watchlist) "İZLENECEK LİSTEM" else "SANA GÖRE",
                        Modifier.padding(top = 2.dp, bottom = 6.dp), small = watchlist)
                    if (cards.isNotEmpty()) {
                        com.cagri.real.ui.ai.AiReportButton(com.cagri.real.platform.android.ai.AiReportFeature.MEDIA,
                            cards.first().media.title, cards.first().reason, Modifier.align(Alignment.CenterHorizontally))
                        PosterDeck(cards, snapshot?.filters.orEmpty(), reduceAnimations, watchlist,
                            Modifier.weight(1f).padding(horizontal = 14.dp),
                            onOpenDetails = { selectedDetail = it; detailOpen = true }) { card, action ->
                            error = null
                            try {
                                if ((action == DiscoveryAction.SAVE_DISCOVERY || action == DiscoveryAction.SAVE_WATCHLIST) && onAddMedia(card.media) == null) {
                                    error = "Kart eklenemedi. Tekrar deneyebilirsin."; false
                                } else {
                                    snapshot = repo?.act(card, action)
                                    true
                                }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = if (e is IllegalArgumentException) e.message else "İşlem tamamlanamadı. Tekrar deneyebilirsin."; false }
                        }
                    } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Kayıtlı liste okunana kadar "boş" yazısı gösterilmez (listeye doğrudan açılışta kısa yanıp sönmesin).
                            if (ready) Text(if (watchlist) "İzlenecek listen henüz boş." else "Bu destenin sonuna geldin.", color = Color.White)
                            if (ready && !watchlist) DiscoveryButton(if (loading) "Hazırlanıyor…" else "Daha fazla öner", enabled = ready && !loading, onClick = { discover(useSavedPreferences = true) })
                        }
                    }
                }
            }
            error?.let { Text(it, color = Color(0xFFE6BCB1), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
        }
        AnimatedVisibility(detailOpen,
            enter = fadeIn(tween(if (reduceAnimations) 0 else 240)) + slideInVertically(tween(if (reduceAnimations) 0 else 380)) { it / 5 },
            exit = fadeOut(tween(if (reduceAnimations) 0 else 120))) {
            selectedDetail?.let { MediaDiscoveryDetail(it, detailRepository, reduceAnimations) { detailOpen = false } }
        }
    }
}

@Composable
internal fun DiscoveryHeadline(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center, small: Boolean = false) {
    Text(text, modifier = modifier.fillMaxWidth(), textAlign = align, maxLines = 1,
        style = RealType.editorialTitle.copy(
            brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFB4B4B9))),
            fontSize = if (small) 30.sp else 46.sp, lineHeight = if (small) 36.sp else 50.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp))
}

/** Üstteki afişin renginde, ekranın tepesine yayılan bulanık ışık (Android 12+; eski sürümde sade geçiş). */
@Composable
internal fun PosterAmbient(url: String?, reduceAnimations: Boolean, reduceDynamicBlur: Boolean) {
    val context = LocalContext.current
    Crossfade(targetState = url, modifier = Modifier.fillMaxWidth().height(210.dp),
        animationSpec = tween(if (reduceAnimations) 0 else 450), label = "poster-ambient") { target ->
        Box(Modifier.fillMaxSize()) {
            if (!target.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val image = remember(target, context) {
                    ImageRequest.Builder(context).data(target).size(64, 96).build()
                }
                AsyncImage(image, null, Modifier.fillMaxSize().alpha(if (reduceDynamicBlur) .18f else .85f)
                    .then(if (reduceDynamicBlur) Modifier else Modifier.blur(48.dp)), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
        }
    }
}

@Composable
internal fun DiscoveryButton(label: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit,
                            modifier: Modifier = Modifier, icon: ActionGlyph? = null) {
    val shape = RoundedCornerShape(24.dp)
    val isSelected = selected
    Row(modifier.heightIn(min = 44.dp).liquidChrome(corners = 24.dp, selected = selected)
        .then(if (selected) Modifier.background(DiscoveryBlue.copy(alpha = .28f), shape)
            .border(1.dp, DiscoveryBlue.copy(alpha = .9f), shape) else Modifier)
        .semantics { this.selected = isSelected }
        .clip(shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { ActionIcon(icon, Modifier.size(18.dp), Color.White); Spacer(Modifier.width(5.dp)) }
        Text(label, color = if (enabled) Color.White else Color(0xFF66666C), fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun durationLabel(millis: Number?, series: Boolean): String? {
    val value = (millis ?: return null).toLong().takeIf { it in 60_000L..86_400_000L } ?: return null
    val minutes = (value / 60_000L).toInt()
    val text = if (minutes >= 60) "${minutes / 60} sa${if (minutes % 60 > 0) " ${minutes % 60} dk" else ""}" else "$minutes dk"
    return if (series) "$text / bölüm" else text
}

@Composable
private fun PosterDeck(cards: List<DiscoveryCard>, filters: Set<String>, reduceAnimations: Boolean, later: Boolean,
                       modifier: Modifier, onOpenDetails: (DiscoveryCard) -> Unit, onDecision: suspend (DiscoveryCard, DiscoveryAction) -> Boolean) {
    val card = cards.first()
    val scope = rememberCoroutineScope()
    var offset by remember(card.key) { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }
    val latestOpenDetails by rememberUpdatedState(onOpenDetails)
    var settling by remember { mutableStateOf<Job?>(null) }
    val latestDecision by rememberUpdatedState(onDecision)
    val threshold = with(LocalDensity.current) { 85.dp.toPx() }
    val currentBusy by rememberUpdatedState(busy)
    fun settle() {
        settling?.cancel()
        if (reduceAnimations) offset = Offset.Zero
        else settling = scope.launch { Animatable(offset, Offset.VectorConverter).animateTo(Offset.Zero, spring()) { offset = value } }
    }
    fun decide(action: DiscoveryAction, flight: Offset = if (action == DiscoveryAction.ADD_TO_WATCHLIST) Offset(0f, threshold * 9) else Offset(threshold * 6, 0f)) {
        if (busy) return
        settling?.cancel()
        busy = true
        scope.launch {
            try {
                if (!reduceAnimations) Animatable(offset, Offset.VectorConverter).animateTo(flight, tween(240)) { offset = value }
                if (!latestDecision(card, action)) {
                    if (reduceAnimations) offset = Offset.Zero
                    else Animatable(offset, Offset.VectorConverter).animateTo(Offset.Zero, spring()) { offset = value }
                } else offset = Offset.Zero // A one-item watchlist still needs its poster to return.
            } finally { busy = false }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = 34.dp, bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
        // Reserve a separate 64dp dock and a 12dp gap; never cover the artwork.
        val width = min(maxWidth.value * .96f, (maxHeight.value - 76f).coerceAtLeast(1f) * PosterAspect).dp
        val posterHeight = width / PosterAspect
        val visible = cards.take(4)
        PosterEdgeGlow(card.media.artworkUrl, reduceAnimations, Modifier.fillMaxWidth().height(posterHeight))
        SwipeEdgeGlow({ offset }, threshold, later)

        for (depth in visible.indices.reversed()) {
            val item = visible[depth]
            key(item.key) {
                val stackDepth by animateFloatAsState(depth.toFloat(),
                    animationSpec = if (reduceAnimations) tween(0) else spring(dampingRatio = .85f, stiffness = 350f), label = "poster-depth")
                val gestures = if (depth == 0) Modifier
                    .pointerInput(card.key, later) { detectTapGestures(onTap = { if (!currentBusy) latestOpenDetails(card) }) }
                    .pointerInput(card.key, later) {
                        detectDragGestures(onDragStart = { if (!currentBusy) settling?.cancel() }, onDragCancel = { if (!currentBusy) settle() },
                            onDragEnd = {
                                // Right = save (watchlist: keep, next) · left = pass (watchlist: remove) · up/down: nothing yet.
                                if (!currentBusy) when {
                                    abs(offset.y) > abs(offset.x) -> settle()
                                    offset.x > threshold -> decide(if (later) DiscoveryAction.BROWSE_WATCHLIST else DiscoveryAction.ADD_TO_WATCHLIST, Offset(threshold * 6, 0f))
                                    offset.x < -threshold -> decide(if (later) DiscoveryAction.REMOVE_FROM_WATCHLIST else DiscoveryAction.SKIP, Offset(-threshold * 6, 0f))
                                    else -> settle()
                                }
                            }) { change, delta -> change.consume(); if (!currentBusy) offset += delta }
                    } else Modifier
                val shape = RoundedCornerShape(18.dp)
                Box(Modifier.width(width).aspectRatio(PosterAspect)
                    // Keyed posters are reordered when the first is moved to the end.
                    // Explicit depth prevents a reused rear layer from covering the new front poster.
                    .zIndex((visible.size - depth).toFloat())
                    .then(gestures).graphicsLayer {
                    // Arkadaki her katman biraz küçülür ve yalnız üst kenarı ~10dp görünür kalır.
                    val step = .045f
                    val scale = 1f - stackDepth * step
                    scaleX = scale; scaleY = scale
                    translationY = -stackDepth * (10.dp.toPx() + step * size.height / 2f)
                    // Keep the layered fade, but a returning front poster must never inherit rear opacity.
                    alpha = if (later && depth == 0) 1f else 1f - stackDepth * .1f
                    if (depth == 0) {
                        translationX = offset.x; translationY += offset.y
                        rotationZ = (offset.x / threshold * 7f + offset.y / threshold * 2f).coerceIn(-24f, 24f)
                    }
                }.clip(shape).background(Color(0xFF1B1B20))) {
                    var artworkFailed by remember(item.media.artworkUrl) { mutableStateOf(false) }
                    val noArtwork = artworkFailed || item.media.artworkUrl.isNullOrBlank()
                    AsyncImage(item.media.artworkUrl, item.media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { artworkFailed = true })
                    if (noArtwork) Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ActionIcon(ActionGlyph.Video, Modifier.size(52.dp), DiscoveryAccent)
                        Text(item.media.title, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    if (depth == 0) {
                        val series = item.media.type.name == "TV"
                        val match = DiscoveryRanking.match(item, filters)
                        Column(Modifier.align(Alignment.TopStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PosterLabel(durationLabel(item.media.durationMillis, series) ?: item.media.year?.toString() ?: if (series) "Dizi" else "Film")
                            DiscoveryRanking.displayGenre(item.media.genre)?.let { PosterLabel(it) }
                        }
                        Column(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PosterLabel(if (series) "Dizi" else "Film")
                            if (filters.isNotEmpty()) PosterLabel("${match.keys.size}/${filters.size}${if (match.inferred.isNotEmpty()) " · AI" else ""}")
                        }
                        if (busy) com.cagri.real.ui.components.RealSpinner(Modifier.size(32.dp).align(Alignment.Center), color = Color.White, strokeWidth = 2.dp)
                    }
                }
            }
        }

        // The dock stays below the poster and does not move with the swiped artwork.
        DiscoveryActionBar("İzledim", busy,
            onDone = { decide(DiscoveryAction.WATCHED, Offset(0f, -threshold * 9)) },
            onSave = { decide(if (later) DiscoveryAction.SAVE_WATCHLIST else DiscoveryAction.SAVE_DISCOVERY) },
            modifier = Modifier.align(Alignment.TopCenter).offset(y = posterHeight + 12.dp))
        SwipeTutorial("film", "Kaydet", "Geç")
    }
}

/** Under the poster: "REAL'e ekle" (a card) and "İzledim" / "Oynadım" (done; never suggested again). */
@Composable
internal fun DiscoveryActionBar(doneLabel: String?, busy: Boolean, onDone: () -> Unit, onSave: (() -> Unit)?, modifier: Modifier = Modifier,
                                accent: Color = DiscoveryBlue) {
    Row(modifier.background(Color.Black.copy(alpha = .55f), RoundedCornerShape(32.dp)).liquidChrome(corners = 32.dp, selected = false)
        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (onSave != null) Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(accent.copy(alpha = .9f))
            .clickable(enabled = !busy, role = Role.Button, onClick = onSave).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(ActionGlyph.Add, Modifier.size(20.dp), if (busy) Color(0xFF66666C) else Color.White)
            Spacer(Modifier.width(7.dp))
            Text("REAL'e ekle", color = if (busy) Color(0xFF66666C) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        if (doneLabel != null) Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = .10f))
            .clickable(enabled = !busy, role = Role.Button, onClick = onDone).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(ActionGlyph.Check, Modifier.size(19.dp), if (busy) Color(0xFF66666C) else Color.White)
            Spacer(Modifier.width(7.dp))
            Text(doneLabel, color = if (busy) Color(0xFF66666C) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun BarIconButton(glyph: ActionGlyph, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).semantics { contentDescription = description }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        ActionIcon(glyph, Modifier.size(22.dp), if (enabled) Color.White.copy(alpha = .85f) else Color(0xFF66666C))
    }
}

@Composable
private fun PosterLabel(text: String) {
    Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .85f))
            .border(.5.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 4.dp))
}

