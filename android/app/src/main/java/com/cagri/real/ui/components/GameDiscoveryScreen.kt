package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cagri.real.discovery.*
import com.cagri.real.ui.theme.RealType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

private val GameAccent = Color(0xFFF26A1B)      // ana buton + "oynasak?"
private val GameGlowTop = Color(0xFFFFB347)      // referanstaki sarı-turuncu üst ışık
private val GameGlowCore = Color(0xFFFF3B30)     // referanstaki kırmızı çekirdek
private const val CoverAspect = .667f            // film afişleriyle aynı oran
private enum class GamePage { ENTRY, FILTERS, DECK, LIST }

private fun bigCover(url: String?) = url?.replace("t_cover_big", "t_cover_big_2x")

/** Film ekranının sağındaki sayfa: turuncu-kırmızı ışık, oyun seçimi ve Oynanacak listem. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameDiscoveryScreen(
    reduceAnimations: Boolean,
    onDismiss: () -> Unit,
    /** "REAL'e ekle": a card with the game block. */
    onAddGame: suspend (com.cagri.real.data.CardGameBlock) -> Long? = { null },
    onAtEntry: (Boolean) -> Unit = {},
    reduceDynamicBlur: Boolean = false,
    /** Yaşam "OYNANACAK LİSTEM": giriş ekranı atlanır, doğrudan oynanacak listesi açılır; geri Yaşam'a döner. */
    startInList: Boolean = false,
    startInFilters: Boolean = false,
    filterHeader: (@Composable () -> Unit)? = null,
    filterBodyModifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val repo = remember(context, uid) { uid?.let { GameDiscoveryRepository(context, it) } }
    val scope = rememberCoroutineScope()
    var snapshot by remember(repo) { mutableStateOf<GameDiscoveryRepository.Snapshot?>(null) }
    var ready by remember(repo) { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(if (startInList) GamePage.LIST else if (startInFilters) GamePage.FILTERS else GamePage.ENTRY) }
    var filters by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var platforms by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<GameCard?>(null) }
    var detailOpen by remember { mutableStateOf(false) }

    LaunchedEffect(page) { onAtEntry(page == GamePage.ENTRY) }
    fun back() {
        if (detailOpen) { detailOpen = false; return }
        if (page == GamePage.ENTRY || (startInList && page == GamePage.LIST) ||
            (startInFilters && (page == GamePage.FILTERS || page == GamePage.DECK))) onDismiss()
        else { page = if (startInFilters) GamePage.FILTERS else GamePage.ENTRY; error = null }
    }
    BackHandler(onBack = ::back)
    LaunchedEffect(repo) {
        if (repo == null) {
            error = t("Öneriler için REAL'e giriş yap."); ready = true
            com.cagri.real.ui.auth.SignInRequired.needed(com.cagri.real.ui.auth.SignInRequired.Reason.Suggestions)
            return@LaunchedEffect
        }
        try { snapshot = repo.load() } catch (e: CancellationException) { throw e } catch (_: Exception) { error = t("Kaydedilmiş öneriler okunamadı.") }
        ready = true
    }
    fun discover(saved: Boolean = false) {
        if (loading || repo == null || !ready) return
        val f = if (saved) snapshot?.filters.orEmpty() else filters.toSet()
        val p = if (saved) snapshot?.platforms.orEmpty() else platforms.toSet()
        loading = true; error = null
        scope.launch {
            try {
                if (!com.cagri.real.platform.android.access.ProAccess.allowed(com.cagri.real.access.RealFeature.GameDiscovery)) return@launch
                snapshot = fetchWideningGames(repo, f, p)
                if (page == GamePage.FILTERS || page == GamePage.DECK) page = GamePage.DECK
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: t("Öneriler alınamadı.") }
            finally { loading = false }
        }
    }

    val listMode = page == GamePage.LIST
    val cards = if (listMode) snapshot?.list.orEmpty() else snapshot?.cards.orEmpty()
    // Shaking the phone brings the last swiped game back on top.
    ShakeToUndo(page == GamePage.DECK) { scope.launch { runCatching { repo?.undo() }.getOrNull()?.let { snapshot = it; error = null } } }
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) } }) {
        if (page == GamePage.ENTRY) GameGlow(reduceAnimations)
        else PosterAmbient(if (page == GamePage.DECK || listMode) cards.firstOrNull()?.game?.artworkUrl else null, reduceAnimations, reduceDynamicBlur)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (page != GamePage.FILTERS || filterHeader == null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RealBackButton(onBack = ::back)
                }
            }
            when (page) {
                GamePage.ENTRY -> GameEntryContent(
                    covers = snapshot?.list.orEmpty().mapNotNull { bigCover(it.game.artworkUrl) }.take(3),
                    savedCount = snapshot?.list?.size ?: 0,
                    canContinue = !snapshot?.cards.isNullOrEmpty(), loading = loading, enabled = ready && !loading,
                    onPick = { filters = arrayListOf(); platforms = arrayListOf(); page = GamePage.FILTERS; error = null },
                    onContinue = { page = GamePage.DECK; error = null },
                    onList = { page = GamePage.LIST; error = null }, modifier = Modifier.weight(1f))
                GamePage.FILTERS -> {
                    if (filterHeader == null) GameHeadline("Ne oynasak", Modifier.padding(horizontal = 20.dp), TextAlign.Start)
                    else filterHeader()
                    // One question at a time: 2 genres at most, then (optional) one feeling and one platform.
                    val platformKeys = GamePlatformGroup.entries.map { it.key }.toSet()
                    DiscoveryStepper(
                        steps = listOf(
                            DiscoveryStep(t("Hangi türde bir oyun?"), t("En fazla 2 tür"), 2, false,
                                listOf(null to GameFilter.entries.filter { it.genre }.map { StepOption(it.key, it.label) })),
                            DiscoveryStep(t("Nasıl hissettirsin?"), t("İstersen bir tane"), 1, true,
                                listOf(null to GameFilter.entries.filter { !it.genre }.map { StepOption(it.key, it.label) })),
                            DiscoveryStep(t("Nerede oynayacaksın?"), t("İstersen bir platform"), 1, true,
                                listOf(null to GamePlatformGroup.entries.map { StepOption(it.key, it.label) })),
                        ),
                        selection = filters.toSet() + platforms.toSet(),
                        onSelection = { all -> filters = ArrayList(all.filter { it !in platformKeys }); platforms = ArrayList(all.filter { it in platformKeys }) },
                        accent = GameAccent, loading = loading, enabled = ready && repo != null, reduceAnimations = reduceAnimations,
                        doneLabel = t("oyunları getir"), modifier = Modifier.weight(1f).then(filterBodyModifier),
                        rare = { chosen, key -> DiscoveryRarePairs.rare(DiscoveryRarePairs.game, chosen, key) }
                    ) { discover() }
                }
                GamePage.DECK, GamePage.LIST -> {
                    GameHeadline(if (listMode) t("OYNANACAK LİSTEM") else t("SANA GÖRE"), Modifier.padding(top = 2.dp, bottom = 6.dp), small = listMode)
                    if (cards.isNotEmpty()) {
                        com.cagri.real.ui.ai.AiReportButton(com.cagri.real.platform.android.ai.AiReportFeature.GAME,
                            cards.first().game.title, cards.first().reason, Modifier.align(Alignment.CenterHorizontally))
                        GameDeck(cards, snapshot?.filters.orEmpty(), reduceAnimations, listMode, Modifier.weight(1f).padding(horizontal = 14.dp), onAddGame,
                            onOpen = { detail = it; detailOpen = true }) { card, action ->
                            error = null
                            try { snapshot = repo?.act(card, action); true }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = if (e is IllegalArgumentException) e.message else t("İşlem tamamlanamadı. Tekrar deneyebilirsin."); false }
                        }
                    } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Kayıtlı liste okunana kadar "boş" yazısı gösterilmez (listeye doğrudan açılışta kısa yanıp sönmesin).
                            if (ready) Text(if (listMode) t("Oynanacak listen henüz boş.") else t("Bu destenin sonuna geldin."), color = Color.White)
                            if (ready && !listMode) GameChip(if (loading) t("Hazırlanıyor…") else t("Daha fazla öner"), false, ready && !loading) { discover(saved = true) }
                        }
                    }
                }
            }
            error?.let { Text(it, color = Color(0xFFE6BCB1), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
        }
        AnimatedVisibility(detailOpen,
            enter = fadeIn(tween(if (reduceAnimations) 0 else 240)) + slideInVertically(tween(if (reduceAnimations) 0 else 380)) { it / 5 },
            exit = fadeOut(tween(if (reduceAnimations) 0 else 120))) {
            detail?.let { GameDetailPanel(it) { detailOpen = false } }
        }
    }
}

/** Referanstaki sağ ekran: üstte sarı-turuncu, ortada kırmızı bulanık ışık, alta doğru siyaha akar. */
@Composable
private fun GameGlow(reduceAnimations: Boolean) {
    // The drift is read in draw only: the glow repaints each frame without recomposing.
    val driftState = if (reduceAnimations) null else rememberInfiniteTransition(label = "game-glow")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "drift")
    Box(Modifier.fillMaxSize().drawBehind {
        val drift = driftState?.value ?: 0f
        val w = size.width; val h = size.height
        drawRect(Brush.radialGradient(listOf(GameGlowTop, Color.Transparent), center = Offset(w * (.50f + .08f * drift), h * .06f), radius = w * 1.0f))
        drawRect(Brush.radialGradient(listOf(GameGlowCore, Color.Transparent), center = Offset(w * (.52f - .06f * drift), h * (.30f + .03f * drift)), radius = w * .68f))
        drawRect(Brush.verticalGradient(.30f to Color.Transparent, .66f to Color.Black, startY = 0f, endY = h))
    })
}

@Composable
private fun GameEntryContent(covers: List<String>, savedCount: Int, canContinue: Boolean, loading: Boolean, enabled: Boolean,
                             onPick: () -> Unit, onContinue: () -> Unit, onList: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            for (i in covers.indices.reversed()) {
                AsyncImage(covers[i], null, Modifier.size(136.dp, 192.dp).graphicsLayer {
                    rotationZ = when (i) { 0 -> 0f; 1 -> -9f; else -> 9f }
                    translationX = when (i) { 0 -> 0f; 1 -> -62.dp.toPx(); else -> 62.dp.toPx() }
                    translationY = if (i == 0) 0f else 10.dp.toPx(); alpha = if (i == 0) 1f else .8f
                }.clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B1B20)).border(.5.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(ActionGlyph.Play, Modifier.size(20.dp), Color.White)
            Spacer(Modifier.width(8.dp))
            Text(t("Oyun"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        val title = RealType.editorialTitle.copy(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1.4).sp)
        Text(t("Bu gece ne"), style = title.copy(color = Color.White))
        Text("oynasak?", style = title.copy(color = GameAccent))
        Spacer(Modifier.height(10.dp))
        Text(when {
            loading -> t("Önerilerin hazırlanıyor…")
            savedCount > 0 -> t("Oynanacak listende {0} oyun var. Yeni bir tür seç ya da kaldığın yerden devam et.", savedCount)
            else -> t("Türünü, hissini ve platformunu seç; sana uyan oyunları keşfet.")
        }, color = Color.White.copy(alpha = .55f), fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(end = 24.dp))
        Spacer(Modifier.height(28.dp))
        if (canContinue) {
            GameChip(t("Keşfetmeye devam et"), false, enabled, primary = true, modifier = Modifier.fillMaxWidth()) { onContinue() }
            Spacer(Modifier.height(10.dp))
            GameChip(t("Tür seç"), false, enabled, modifier = Modifier.fillMaxWidth(), tall = true) { onPick() }
        } else GameChip(t("Tür seç"), false, enabled, primary = true, modifier = Modifier.fillMaxWidth()) { onPick() }
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onList), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(ActionGlyph.Calendar, Modifier.size(18.dp), if (enabled) Color.White else Color(0xFF66666C))
                Spacer(Modifier.width(8.dp))
                Text(t("Oynanacak listem ({0})", savedCount), color = if (enabled) Color.White else Color(0xFF66666C), fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GameHeadline(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center, small: Boolean = false) {
    Text(text, modifier = modifier.fillMaxWidth(), textAlign = align, maxLines = 1,
        style = RealType.editorialTitle.copy(brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFB4B4B9))),
            fontSize = if (small) 30.sp else 46.sp, lineHeight = if (small) 36.sp else 50.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp))
}

@Composable
private fun GameChip(label: String, selected: Boolean, enabled: Boolean, primary: Boolean = false, tall: Boolean = false,
                     modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (primary || tall) 28.dp else 24.dp)
    val base = when {
        primary -> modifier.height(56.dp).clip(shape).background(if (enabled) GameAccent else GameAccent.copy(alpha = .35f))
            .drawBehind { if (enabled) drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .30f), Color.Transparent), center = center, radius = size.width * .38f)) }
        tall -> modifier.height(56.dp).clip(shape).background(Color.White.copy(alpha = .07f)).border(1.dp, Color.White.copy(alpha = .18f), shape)
        else -> modifier.heightIn(min = 44.dp).liquidChrome(corners = 24.dp, selected = selected)
            .then(if (selected) Modifier.background(GameAccent.copy(alpha = .28f), shape).border(1.dp, GameAccent.copy(alpha = .9f), shape) else Modifier).clip(shape)
    }
    val isSelected = selected
    Row(base.semantics { this.selected = isSelected }.clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = if (primary || tall) 0.dp else 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (enabled) Color.White else Color(0xFF66666C), fontSize = if (primary || tall) 16.sp else 13.sp,
            fontWeight = if (primary) FontWeight.SemiBold else if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun GameLabel(text: String) {
    Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .85f)).border(.5.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 4.dp))
}

private fun kindLabel(kind: String) = when (kind) { "remake" -> "Remake"; "remaster" -> "Remaster"; "port" -> "Port"; "expanded" -> t("Genişletilmiş"); else -> null }

@Composable
private fun GameDeck(cards: List<GameCard>, filters: Set<String>, reduceAnimations: Boolean, listMode: Boolean, modifier: Modifier,
                     onAddGame: suspend (com.cagri.real.data.CardGameBlock) -> Long?,
                     onOpen: (GameCard) -> Unit, onDecision: suspend (GameCard, GameAction) -> Boolean) {
    val card = cards.first()
    // The front poster loads first; the ones behind it start once it is on screen.
    // The deck's first front cover loads alone; after that the covers behind always load, and the next ones are
    // fetched ahead so a swipe never shows an empty cover.
    var frontReady by remember { mutableStateOf(card.game.artworkUrl.isNullOrBlank()) }
    val prefetchContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(frontReady, cards.take(8).map { it.key }) {
        if (!frontReady) return@LaunchedEffect
        cards.drop(1).take(6).mapNotNull { bigCover(it.game.artworkUrl) }.forEach { u ->
            coil.Coil.imageLoader(prefetchContext).enqueue(coil.request.ImageRequest.Builder(prefetchContext).data(u).build())
        }
    }
    val scope = rememberCoroutineScope()
    var offset by remember(card.key) { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val latestOpen by rememberUpdatedState(onOpen)
    val latestAddGame by rememberUpdatedState(onAddGame)
    val context = androidx.compose.ui.platform.LocalContext.current
    val latestDecision by rememberUpdatedState(onDecision)
    val currentBusy by rememberUpdatedState(busy)
    val threshold = with(LocalDensity.current) { 85.dp.toPx() }
    fun settle() {
        settling?.cancel()
        if (reduceAnimations) offset = Offset.Zero else settling = scope.launch { Animatable(offset, Offset.VectorConverter).animateTo(Offset.Zero, spring()) { offset = value } }
    }
    fun decide(action: GameAction, flight: Offset = Offset(threshold * 6, 0f)) {
        if (busy) return
        settling?.cancel(); busy = true
        scope.launch {
            try {
                if (!reduceAnimations) Animatable(offset, Offset.VectorConverter).animateTo(flight, tween(240)) { offset = value }
                if (!latestDecision(card, action)) {
                    if (reduceAnimations) offset = Offset.Zero else Animatable(offset, Offset.VectorConverter).animateTo(Offset.Zero, spring()) { offset = value }
                } else offset = Offset.Zero
            } finally { busy = false }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = 34.dp, bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
        val width = min(maxWidth.value * .96f, (maxHeight.value - 76f).coerceAtLeast(1f) * CoverAspect).dp
        val coverHeight = width / CoverAspect
        val visible = cards.take(4)
        PosterEdgeGlow(card.game.artworkUrl, reduceAnimations, Modifier.fillMaxWidth().height(coverHeight))
        SwipeEdgeGlow({ offset }, threshold, listMode)
        for (depth in visible.indices.reversed()) {
            val item = visible[depth]
            key(item.key) {
                val stackDepth = if (listMode) animateFloatAsState(depth.toFloat(),
                    animationSpec = if (reduceAnimations) tween(0) else spring(dampingRatio = .85f, stiffness = 350f),
                    label = "game-list-depth").value else depth.toFloat()
                val gestures = if (depth == 0) Modifier
                    .pointerInput(card.key) { detectTapGestures(onTap = { if (!currentBusy) latestOpen(card) }) }
                    .pointerInput(card.key, listMode) {
                        detectDragGestures(onDragStart = { if (!currentBusy) settling?.cancel() }, onDragCancel = { if (!currentBusy) settle() },
                            onDragEnd = {
                                // Right = save to the list (list: keep, next) · left = pass (list: remove) · up/down: nothing yet.
                                if (!currentBusy) when {
                                    abs(offset.y) > abs(offset.x) -> settle()
                                    offset.x > threshold -> decide(if (listMode) GameAction.REMOVE_FROM_LIST else GameAction.ADD_TO_LIST, Offset(threshold * 6, 0f))
                                    offset.x < -threshold -> decide(if (listMode) GameAction.BROWSE_LIST else GameAction.SKIP, Offset(-threshold * 6, 0f))
                                    else -> settle()
                                }
                            }) { change, delta -> change.consume(); if (!currentBusy) offset += delta }
                } else Modifier
                Box(Modifier.width(width).aspectRatio(CoverAspect)
                    // Queue rotation must keep the actual front cover above every reused rear layer.
                    .zIndex((visible.size - depth).toFloat())
                    .then(gestures).graphicsLayer {
                    val scale = 1f - stackDepth * .045f
                    scaleX = scale; scaleY = scale
                    translationY = -stackDepth * (10.dp.toPx() + .045f * size.height / 2f)
                    alpha = if (listMode && depth == 0) 1f else 1f - stackDepth * .1f
                    if (depth == 0) { translationX = offset.x; translationY += offset.y; rotationZ = (offset.x / threshold * 7f + offset.y / threshold * 2f).coerceIn(-24f, 24f) }
                }.clip(RoundedCornerShape(18.dp)).background(Color(0xFF1B1B20))) {
                    var failed by remember(item.game.artworkUrl) { mutableStateOf(false) }
                    val noCover = failed || item.game.artworkUrl.isNullOrBlank()
                    AsyncImage(if (depth == 0 || frontReady) bigCover(item.game.artworkUrl) else null, item.game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                        onSuccess = { if (depth == 0) frontReady = true }, onError = { failed = true; if (depth == 0) frontReady = true })
                    if (noCover) Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ActionIcon(ActionGlyph.Play, Modifier.size(52.dp), GameAccent)
                        Text(item.game.title, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    if (depth == 0) {
                        Column(Modifier.align(Alignment.TopStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GameLabel(item.game.year?.toString() ?: t("Oyun"))
                            item.game.genres.firstOrNull()?.let { GameLabel(GameGenres.label(it)) }
                            kindLabel(item.game.kind)?.let { GameLabel(it) }
                        }
                        Column(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GameLabel(t("Oyun"))
                            if (filters.isNotEmpty()) GameLabel("${filters.count { it in item.tags }}/${filters.size} · AI")
                        }
                        if (busy) com.cagri.real.ui.components.RealSpinner(Modifier.size(32.dp).align(Alignment.Center), color = Color.White, strokeWidth = 2.dp)
                    }
                }
            }
        }
        DiscoveryActionBar(t("Oynadım"), busy, artworkUrl = card.game.artworkUrl, onDone = { decide(GameAction.PLAYED, Offset(0f, -threshold * 9)) },
            onSave = {
                if (!busy) scope.launch {
                    busy = true
                    try {
                        val g = card.game
                        val block = com.cagri.real.data.CardGameBlock(providerId = g.providerId, title = g.title, year = g.year,
                            genres = g.genres, platforms = g.platforms, artworkUrl = g.artworkUrl, description = g.description,
                            providerUrl = g.providerUrl)
                        if (latestAddGame(block) != null) RealToast.show(context, "REAL'e eklendi")
                        else RealToast.show(context, t("Kart eklenemedi. Tekrar deneyebilirsin."))
                    } finally { busy = false }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter).offset(y = coverHeight + 12.dp), accent = GameAccent)
        if (listMode) SwipeTutorial("game_list", t("Kaldır"), t("Sona at")) else SwipeTutorial("game", t("Kaydet"), t("Geç"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameDetailPanel(card: GameCard, onClose: () -> Unit) {
    val g = card.game
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) } }) {
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(scroll).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) { RealBackButton(onClose, description = t("Kapat")) }
            AsyncImage(bigCover(g.artworkUrl), g.title, Modifier.align(Alignment.CenterHorizontally).width(220.dp).aspectRatio(CoverAspect)
                .clip(RoundedCornerShape(18.dp)).background(Color(0xFF1B1B20)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(20.dp))
            Text(g.title, style = RealType.editorialTitle.copy(color = Color.White, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                g.year?.let { GameLabel(t("İlk çıkış {0}", it)) }
                kindLabel(g.kind)?.let { GameLabel(it) }
                g.genres.forEach { GameLabel(GameGenres.label(it)) }
            }
            if (g.releases.any { it.year != null }) {
                Spacer(Modifier.height(18.dp))
                Text(t("Çıktığı platformlar"), style = RealType.editorialSubtitle, color = Color.White.copy(alpha = .8f))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.releases.take(16).forEach { GameLabel(if (it.year != null) "${it.platform} · ${it.year}" else it.platform) }
                }
            } else if (g.platforms.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(t("Çıktığı platformlar"), style = RealType.editorialSubtitle, color = Color.White.copy(alpha = .8f))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { g.platforms.take(16).forEach { GameLabel(it) } }
            }
            g.description?.let {
                Spacer(Modifier.height(18.dp))
                Text(it, color = Color.White.copy(alpha = .75f), fontSize = 15.sp, lineHeight = 22.sp)
            }
            if (card.reason.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Text(card.reason, color = GameAccent, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(20.dp))
            GameMediaSection(g.providerId, GameAccent)
            Spacer(Modifier.height(14.dp))
            Text(t("Kaynak: IGDB"), color = Color(0xFF85858E), fontSize = 12.sp)
            Spacer(Modifier.height(32.dp))
        }
        // Same as the film detail: no glass, content just fades out softly under the status bar while scrolling.
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().height(28.dp)
            .graphicsLayer { alpha = (scroll.value / 60f).coerceIn(0f, 1f) }
            .background(Brush.verticalGradient(listOf(Color.Black, Color.Transparent))))
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .graphicsLayer { alpha = (scroll.value / 60f).coerceIn(0f, 1f) }.background(Color.Black))
    }
}

/**
 * The exact choice first; when it finds fewer than six games the request widens quietly into the same deck: the
 * feeling goes, then the platform, then each genre on its own.
 */
private suspend fun fetchWideningGames(repo: com.cagri.real.discovery.GameDiscoveryRepository, filters: Set<String>,
                                       platforms: Set<String>): com.cagri.real.discovery.GameDiscoveryRepository.Snapshot {
    var first: Exception? = null
    var snapshot = try { repo.fetch(filters, platforms) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { first = e; null }
    val genres = filters.filter { com.cagri.real.discovery.GameFilter.fromKey(it)?.genre == true }.toSet()
    val wider = buildList {
        if (genres.isNotEmpty() && genres.size < filters.size) add(genres to platforms)
        if (platforms.isNotEmpty() && genres.isNotEmpty()) add(genres to emptySet<String>())
        if (genres.size > 1) genres.forEach { add(setOf(it) to emptySet<String>()) }
    }
    for ((f, p) in wider) {
        if ((snapshot?.cards?.size ?: 0) >= 6) break
        snapshot = try { repo.fetch(f, p, deckFilters = filters, deckPlatforms = platforms, append = snapshot != null) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { snapshot }
    }
    return snapshot ?: throw (first ?: java.io.IOException(t("Öneriler alınamadı.")))
}
