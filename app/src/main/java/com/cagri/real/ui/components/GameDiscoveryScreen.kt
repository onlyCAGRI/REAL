package com.cagri.real.ui.components

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
            (startInFilters && page == GamePage.FILTERS)) onDismiss()
        else { page = if (startInFilters) GamePage.FILTERS else GamePage.ENTRY; error = null }
    }
    BackHandler(onBack = ::back)
    LaunchedEffect(repo) {
        if (repo == null) { error = "Öneriler için REAL'e giriş yap."; ready = true; return@LaunchedEffect }
        try { snapshot = repo.load() } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Kaydedilmiş öneriler okunamadı." }
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
                snapshot = repo.fetch(f, p)
                if (page == GamePage.FILTERS || page == GamePage.DECK) page = GamePage.DECK
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Öneriler alınamadı." }
            finally { loading = false }
        }
    }

    val listMode = page == GamePage.LIST
    val cards = if (listMode) snapshot?.list.orEmpty() else snapshot?.cards.orEmpty()
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
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        if (filterHeader == null) GameHeadline("Ne oynasak", Modifier.padding(horizontal = 20.dp), TextAlign.Start)
                        else filterHeader()
                        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp).then(filterBodyModifier),
                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Platform", style = RealType.editorialSubtitle, color = Color.White.copy(alpha = .8f))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GamePlatformGroup.entries.forEach { g ->
                                val on = g.key in platforms
                                GameChip(g.label, on, !loading) { platforms = ArrayList(if (on) platforms - g.key else platforms + g.key) }
                            }
                        }
                        Text(if (filterHeader == null) "Seçmezsen tüm platformlar · En fazla 3 seçim · Birlikte uyanlar önce gelir." else "Seçmezsen tüm platformlar · Her bölümde en fazla 3 seçim", color = Color(0xFF9A9A9F), fontSize = 13.sp)
                        @Composable fun chips(options: List<GameFilter>) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                options.forEach { f ->
                                    val on = f.key in filters
                                    val count = filters.count { GameFilter.fromKey(it)?.genre == f.genre }
                                    GameChip(f.label, on, !loading && (on || count < GameFilter.MAX_PER_GROUP)) { filters = ArrayList(if (on) filters - f.key else filters + f.key) }
                                }
                            }
                        }
                        Text("Tür", style = RealType.editorialSubtitle, color = Color.White.copy(alpha = .8f))
                        val genrePages = GameFilter.entries.filter { it.genre }.chunked(8)
                        DiscoverySelectionPager(
                            titles = genrePages.mapIndexed { index, _ -> if (index == 0) "Türler" else "Diğer türler" },
                            selectedCounts = genrePages.map { options -> options.count { it.key in filters } },
                            reduceAnimations = reduceAnimations, accent = GameAccent
                        ) { index, _ ->
                            chips(genrePages[index])
                        }
                        Text("Nasıl hissettirsin?", style = RealType.editorialSubtitle, color = Color.White.copy(alpha = .8f))
                        val feelingPages = GameFilter.entries.filter { !it.genre }.chunked(4)
                        DiscoverySelectionPager(
                            titles = feelingPages.mapIndexed { index, _ -> if (index == 0) "Hisler" else "Diğer hisler" },
                            selectedCounts = feelingPages.map { options -> options.count { it.key in filters } },
                            reduceAnimations = reduceAnimations, accent = GameAccent
                        ) { index, _ ->
                            chips(feelingPages[index])
                        }
                        Text("Öneriler AI ile seçilir; kapak, çıkış yılı ve platformlar IGDB'den doğrulanır. İnternet gerekir.", color = Color(0xFF85858E), fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    }
                    DiscoveryLoadingButton("Oyunları getir", loading, ready && repo != null, GameAccent, reduceAnimations,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) { discover() }
                }
                GamePage.DECK, GamePage.LIST -> {
                    GameHeadline(if (listMode) "OYNANACAK LİSTEM" else "SANA GÖRE", Modifier.padding(top = 2.dp, bottom = 6.dp), small = listMode)
                    if (cards.isNotEmpty()) {
                        com.cagri.real.ui.ai.AiReportButton(com.cagri.real.platform.android.ai.AiReportFeature.GAME,
                            cards.first().game.title, cards.first().reason, Modifier.align(Alignment.CenterHorizontally))
                        GameDeck(cards, snapshot?.filters.orEmpty(), reduceAnimations, listMode, Modifier.weight(1f).padding(horizontal = 14.dp),
                            onOpen = { detail = it; detailOpen = true }) { card, action ->
                            error = null
                            try { snapshot = repo?.act(card, action); true }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = if (e is IllegalArgumentException) e.message else "İşlem tamamlanamadı. Tekrar deneyebilirsin."; false }
                        }
                    } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Kayıtlı liste okunana kadar "boş" yazısı gösterilmez (listeye doğrudan açılışta kısa yanıp sönmesin).
                            if (ready) Text(if (listMode) "Oynanacak listen henüz boş." else "Bu destenin sonuna geldin.", color = Color.White)
                            if (ready && !listMode) GameChip(if (loading) "Hazırlanıyor…" else "Daha fazla öner", false, ready && !loading) { discover(saved = true) }
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
    val drift = if (reduceAnimations) 0f else rememberInfiniteTransition(label = "game-glow")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "drift").value
    Box(Modifier.fillMaxSize().drawBehind {
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
            Text("Oyun", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        val title = RealType.editorialTitle.copy(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1.4).sp)
        Text("Bu gece ne", style = title.copy(color = Color.White))
        Text("oynasak?", style = title.copy(color = GameAccent))
        Spacer(Modifier.height(10.dp))
        Text(when {
            loading -> "Önerilerin hazırlanıyor…"
            savedCount > 0 -> "Oynanacak listende $savedCount oyun var. Yeni bir tür seç ya da kaldığın yerden devam et."
            else -> "Türünü, hissini ve platformunu seç; sana uyan oyunları keşfet."
        }, color = Color.White.copy(alpha = .55f), fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(end = 24.dp))
        Spacer(Modifier.height(28.dp))
        if (canContinue) {
            GameChip("Keşfetmeye devam et", false, enabled, primary = true, modifier = Modifier.fillMaxWidth()) { onContinue() }
            Spacer(Modifier.height(10.dp))
            GameChip("Tür seç", false, enabled, modifier = Modifier.fillMaxWidth(), tall = true) { onPick() }
        } else GameChip("Tür seç", false, enabled, primary = true, modifier = Modifier.fillMaxWidth()) { onPick() }
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onList), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(ActionGlyph.Calendar, Modifier.size(18.dp), if (enabled) Color.White else Color(0xFF66666C))
                Spacer(Modifier.width(8.dp))
                Text("Oynanacak listem ($savedCount)", color = if (enabled) Color.White else Color(0xFF66666C), fontSize = 15.sp)
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
private fun GameBarButton(glyph: ActionGlyph, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).semantics { contentDescription = description }.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        ActionIcon(glyph, Modifier.size(22.dp), if (enabled) Color.White.copy(alpha = .85f) else Color(0xFF66666C))
    }
}

@Composable
private fun GameLabel(text: String) {
    Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .85f)).border(.5.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 4.dp))
}

private fun kindLabel(kind: String) = when (kind) { "remake" -> "Remake"; "remaster" -> "Remaster"; "port" -> "Port"; "expanded" -> "Genişletilmiş"; else -> null }

@Composable
private fun GameDeck(cards: List<GameCard>, filters: Set<String>, reduceAnimations: Boolean, listMode: Boolean, modifier: Modifier,
                     onOpen: (GameCard) -> Unit, onDecision: suspend (GameCard, GameAction) -> Boolean) {
    val card = cards.first()
    val scope = rememberCoroutineScope()
    var offset by remember(card.key) { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val latestOpen by rememberUpdatedState(onOpen)
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
    val swipeAction = if (listMode) GameAction.BROWSE_LIST else GameAction.SKIP
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = 34.dp, bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
        val width = min(maxWidth.value * .96f, (maxHeight.value - 76f).coerceAtLeast(1f) * CoverAspect).dp
        val coverHeight = width / CoverAspect
        val visible = cards.take(4)
        PosterEdgeGlow(card.game.artworkUrl, reduceAnimations, Modifier.fillMaxWidth().height(coverHeight))
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
                                if (!currentBusy) when {
                                    offset.y > threshold && offset.y > abs(offset.x) -> decide(swipeAction, Offset(0f, threshold * 9))
                                    offset.x > threshold -> decide(swipeAction, Offset(threshold * 6, 0f))
                                    offset.x < -threshold -> decide(swipeAction, Offset(-threshold * 6, 0f))
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
                    AsyncImage(bigCover(item.game.artworkUrl), item.game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = true })
                    if (noCover) Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ActionIcon(ActionGlyph.Play, Modifier.size(52.dp), GameAccent)
                        Text(item.game.title, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    if (depth == 0) {
                        Column(Modifier.align(Alignment.TopStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GameLabel(item.game.year?.toString() ?: "Oyun")
                            item.game.genres.firstOrNull()?.let { GameLabel(GameGenres.label(it)) }
                            kindLabel(item.game.kind)?.let { GameLabel(it) }
                        }
                        Column(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GameLabel("Oyun")
                            if (filters.isNotEmpty()) GameLabel("${filters.count { it in item.tags }}/${filters.size} · AI")
                        }
                        if (busy) CircularProgressIndicator(Modifier.size(32.dp).align(Alignment.Center), color = Color.White, strokeWidth = 2.dp)
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.TopCenter).offset(y = coverHeight + 12.dp).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(32.dp))
            .liquidChrome(corners = 32.dp, selected = false).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (listMode) GameBarButton(ActionGlyph.Close, "Oynanacak listemden çıkar", !busy) { decide(GameAction.REMOVE_FROM_LIST, Offset(-threshold * 6, 0f)) }
            else {
                GameBarButton(ActionGlyph.Close, "Geç", !busy) { decide(GameAction.SKIP, Offset(-threshold * 6, 0f)) }
                Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(GameAccent.copy(alpha = .9f))
                    .clickable(enabled = !busy, role = Role.Button) { decide(GameAction.ADD_TO_LIST, Offset(0f, threshold * 9)) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    ActionIcon(ActionGlyph.Add, Modifier.size(20.dp), Color.White)
                    Spacer(Modifier.width(7.dp))
                    Text("Oynanacak listeme ekle", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameDetailPanel(card: GameCard, onClose: () -> Unit) {
    val g = card.game
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) } }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) { RealBackButton(onClose, description = "Kapat") }
            AsyncImage(bigCover(g.artworkUrl), g.title, Modifier.align(Alignment.CenterHorizontally).width(220.dp).aspectRatio(CoverAspect)
                .clip(RoundedCornerShape(18.dp)).background(Color(0xFF1B1B20)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(20.dp))
            Text(g.title, style = RealType.editorialTitle.copy(color = Color.White, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                g.year?.let { GameLabel("İlk çıkış $it") }
                kindLabel(g.kind)?.let { GameLabel(it) }
                g.genres.forEach { GameLabel(GameGenres.label(it)) }
            }
            if (g.releases.any { it.year != null }) {
                Spacer(Modifier.height(18.dp))
                Text("Çıktığı platformlar", style = RealType.editorialSubtitle, color = Color.White.copy(alpha = .8f))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.releases.take(16).forEach { GameLabel(if (it.year != null) "${it.platform} · ${it.year}" else it.platform) }
                }
            } else if (g.platforms.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Çıktığı platformlar", style = RealType.editorialSubtitle, color = Color.White.copy(alpha = .8f))
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
            Spacer(Modifier.height(10.dp))
            Text("Kaynak: IGDB", color = Color(0xFF85858E), fontSize = 12.sp)
            Spacer(Modifier.height(32.dp))
        }
    }
}
