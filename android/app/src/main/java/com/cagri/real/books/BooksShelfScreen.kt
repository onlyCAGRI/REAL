package com.cagri.real.books

import com.cagri.real.i18n.t
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.abs
import kotlin.math.exp
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.liquidChrome
import kotlinx.coroutines.launch

/**
 * Referanstaki gibi kitaplık: düz (3B olmayan) ön kapaklar, her rafta alt kısmı kaplayan yarı saydam
 * renkli cam raf + iki vida. Alttaki "Kitap Ekle" siyah hap. Kenarlarda kademeli cam bulanıklığı.
 */
@Composable
fun BooksShelfScreen(
    shelves: List<Shelf>,
    onBookClick: (Book, Rect) -> Unit,
    onAddBooks: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onThemeChange: () -> Unit = {},
    themeLabel: String = "AMOLED",
    background: Color = Color.Black,
    textColor: Color = Color.White,
    chrome: Color = Color(0xFF1A1A1A),
    chromeText: Color = Color.White,
    hiddenId: String? = null,   // raftan çıkış animasyonu sırasında kapak overlay'de uçar; raftaki kopya gizlenir
    focusId: String? = null,    // geri dönen kapak: raf bu kitaba kaydırılır, konumu onFocusBounds ile bildirilir
    onFocusBounds: (Rect) -> Unit = {},
    onAddSection: () -> Unit = {},
    onSectionMenu: (String) -> Unit = {},
    onMoveBook: (Book, String) -> Unit = { _, _ -> },
) {
    val statusTop = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val navBottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val focusShelf = remember { shelves.indexOfFirst { sh -> sh.books.any { it.id == focusId } } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (focusShelf >= 2) focusShelf + 1 else 0)
    // Sürükle-bırak: kitaba basılı tut, başka bir bölümün üstüne bırak. Konumlar kök koordinatında.
    var dragBook by remember { mutableStateOf<Book?>(null) }
    var dragPos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    val shelfBounds = remember { androidx.compose.runtime.mutableStateMapOf<String, Rect>() }
    val dropTarget = dragBook?.let { shelfBounds.entries.firstOrNull { e -> dragPos.y in e.value.top..e.value.bottom }?.key }
    val edgePx = with(LocalDensity.current) { 150.dp.toPx() }
    LaunchedEffect(dragBook != null) {
        // Ekranın üst / alt kenarına yaklaşınca liste kendiliğinden kayar.
        while (dragBook != null) {
            withFrameNanos { }
            val y = dragPos.y - rootBounds.top
            when {
                y < edgePx -> listState.scrollBy(-((edgePx - y) / edgePx) * 18f)
                y > rootBounds.height - edgePx -> listState.scrollBy(((y - rootBounds.height + edgePx) / edgePx) * 18f)
            }
        }
    }
    val drag = remember { ShelfDrag() }
    drag.onStart = { b, p -> dragBook = b; dragPos = p }
    drag.onMove = { d -> dragPos += d }
    drag.onEnd = { drop ->
        val b = dragBook
        if (drop && b != null && dropTarget != null && dropTarget != b.category) onMoveBook(b, dropTarget)
        dragBook = null
    }
    Box(modifier.fillMaxSize().background(background).onGloballyPositioned { rootBounds = it.boundsInRoot() }) {
        GlassEdges(background = background, top = statusTop + 44.dp, bottom = navBottom + 140.dp, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 170.dp),
            ) {
                item {
                    // KİTAPLAR sits clear of the back / theme buttons (40 dp at 8 dp from the top).
                    Column(Modifier.fillMaxWidth().padding(top = 64.dp, bottom = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t("KİTAPLAR"), color = textColor, fontSize = 52.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium,
                            letterSpacing = (-1).sp, textAlign = TextAlign.Center)
                    }
                }
                items(shelves, key = { it.name }) { shelf ->
                    DisposableEffect(shelf.name) { onDispose { shelfBounds.remove(shelf.name) } }
                    Column(Modifier.onGloballyPositioned { shelfBounds[shelf.name] = it.boundsInRoot() }) {
                        ShelfSection(shelf, onBookClick, textColor, dragBook?.id ?: hiddenId, focusId, onFocusBounds,
                            dropHere = dropTarget == shelf.name && dragBook?.category != shelf.name,
                            onHeaderLongPress = { onSectionMenu(shelf.name) }, drag = drag)
                        Spacer(Modifier.height(36.dp))
                    }
                }
            }
        }

        com.cagri.real.ui.components.RealBackButton(onBack, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 8.dp), tint = textColor)
        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 16.dp, top = 8.dp).size(40.dp)
            .clip(CircleShape).liquidChrome(corners = 20.dp).clickable(onClick = onThemeChange)
            .semantics { contentDescription = t("Tema değiştir: {0}", themeLabel) }, contentAlignment = Alignment.Center) {
            ActionIcon(ActionGlyph.Theme, Modifier.size(20.dp), Color.White)
        }
        if (shelves.isEmpty()) Text(t("Kitap ekleyerek kitaplığını oluştur."), color = textColor.copy(alpha = .55f),
            modifier = Modifier.align(Alignment.Center))
        // Referanstaki siyah "Add Books" hapı; yanında yeni bölüm açan "+".
        Row(Modifier.align(Alignment.BottomCenter).rawNavBarPadding().padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.height(56.dp).width(172.dp).clip(CircleShape).background(chrome)
                    .border(0.8.dp, Color.White.copy(alpha = .12f), CircleShape)
                    .clickable(onClick = onAddBooks).semantics { contentDescription = t("Kitap ekle") },
                contentAlignment = Alignment.Center,
            ) { Text(t("Kitap Ekle"), color = chromeText, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(chrome)
                    .border(0.8.dp, Color.White.copy(alpha = .12f), CircleShape)
                    .clickable(onClick = onAddSection).semantics { contentDescription = t("Bölüm ekle") },
                contentAlignment = Alignment.Center,
            ) { ActionIcon(ActionGlyph.Add, Modifier.size(22.dp), chromeText) }
        }
        // Sürüklenen kitap parmağın altında, biraz eğik ve küçük.
        dragBook?.let { b ->
            val density = LocalDensity.current
            Box(Modifier.offset {
                androidx.compose.ui.unit.IntOffset(
                    (dragPos.x - rootBounds.left - with(density) { (CoverW * 0.85f).toPx() } / 2f).toInt(),
                    (dragPos.y - rootBounds.top - with(density) { (CoverH * 0.85f).toPx() } * 0.8f).toInt())
            }.graphicsLayer { rotationZ = -4f; alpha = 0.95f }) {
                BookCover(b, Modifier.width(CoverW * 0.85f).height(CoverH * 0.85f), titleSize = 12.sp)
            }
        }
    }
}

/** Raf satırlarından ekran seviyesine sürükleme olaylarını taşır (her karede yeniden kurulmaz). */
private class ShelfDrag {
    var onStart: (Book, androidx.compose.ui.geometry.Offset) -> Unit = { _, _ -> }
    var onMove: (androidx.compose.ui.geometry.Offset) -> Unit = {}
    var onEnd: (Boolean) -> Unit = {}
}

private val CoverW = 112.dp
private val CoverH = 168.dp
private val SpineW = 30.dp
private val SpineGap = 10.dp
private val SideW = 13.dp         // açık kitabın solunda görünen küçük sırt yüzü (3B hissi)
private const val OpenMs = 520

@Composable
private fun ShelfSection(shelf: Shelf, onBookClick: (Book, Rect) -> Unit, textColor: Color, hiddenId: String?,
                         focusId: String?, onFocusBounds: (Rect) -> Unit,
                         dropHere: Boolean, onHeaderLongPress: () -> Unit, drag: ShelfDrag) {
    // Kategori başlığı: iki yanda ince çizgi; basılı tutunca bölüm menüsü. Bırakılacak bölüm parlar.
    val lineAlpha by animateFloatAsState(if (dropHere) .8f else .28f, tween(160), label = "dropLine")
    Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp)
        .pointerInput(shelf.name) { detectTapGestures(onLongPress = { onHeaderLongPress() }) },
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(0.7.dp).background(textColor.copy(alpha = lineAlpha)))
        Text(t(shelf.name).uppercase(), color = textColor.copy(alpha = if (dropHere) 1f else .85f), fontSize = 12.sp, fontFamily = FontFamily.Serif,
            letterSpacing = 1.2.sp, modifier = Modifier.padding(horizontal = 14.dp))
        Box(Modifier.weight(1f).height(0.7.dp).background(textColor.copy(alpha = lineAlpha)))
    }
    Spacer(Modifier.height(22.dp))
    if (shelf.books.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(CoverH + 8.dp).padding(horizontal = 40.dp)
            .clip(RoundedCornerShape(14.dp)).border(0.8.dp, textColor.copy(alpha = if (dropHere) .5f else .14f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center) {
            Text(t("Kitapları buraya sürükle"), color = textColor.copy(alpha = .45f), fontSize = 14.sp, fontFamily = FontFamily.Serif)
        }
        return
    }
    BookRow(shelf, onBookClick, hiddenId, focusId, onFocusBounds, drag)
}

@Composable
private fun BookRow(shelf: Shelf, onBookClick: (Book, Rect) -> Unit, hiddenId: String?,
                    focusId: String?, onFocusBounds: (Rect) -> Unit, drag: ShelfDrag) {
    // Her rafta her zaman bir kitap açık (kapağı dönük). Geri dönen kitap açık başlar.
    var selectedId by remember { mutableStateOf(shelf.books.firstOrNull { it.id == focusId }?.id ?: shelf.books.first().id) }
    if (shelf.books.none { it.id == selectedId }) selectedId = shelf.books.first().id
    val sel = shelf.books.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    // Kitaplar ekrana sığıyorsa grup ortalanır ve kayma yapılmaz; sığmıyorsa seçili kitap ortada tutulur.
    val n = shelf.books.size
    val totalDp = (n - 1) * bookGeom(90f, CoverW.value, SpineW.value, CoverH.value).width +
        bookGeom(OpenDeg, CoverW.value, SpineW.value, CoverH.value).width + (n - 1) * SpineGap.value
    val fits = totalDp <= screenW.value - 24f
    val state = rememberLazyListState(initialFirstVisibleItemIndex = if (fits) 0 else sel)
    // Seçili kitabı ekran ortasında tutar. animateScrollToItem yerine her karede hedefi yeniden ölçer:
    // kitaplar açılıp kapanırken genişlikleri değiştiği için sabit bir hedefe kaymak sağa kaydırırken
    // pencereyi aniden zıplatıyordu. Burada hedef de kitaplarla birlikte süzülerek hareket eder.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(selectedId) {
        if (fits) { state.scrollToItem(0); return@LaunchedEffect }
        val index = shelf.books.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        val instant = !placed
        val startNs = withFrameNanos { it }
        var lastNs = startNs
        while (true) {
            val nowNs = withFrameNanos { it }
            val dt = ((nowNs - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNs = nowNs
            val info = state.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
            if (item == null) { state.scrollToItem(index); continue }   // hedef henüz çizilmediyse yakına atla
            val diff = (item.offset + item.size / 2f) - info.viewportSize.width / 2f
            val k = if (instant) 1f else 1f - exp(-dt / 0.085f)       // zamana bağlı yumuşak takip
            state.scrollBy(diff * k)
            placed = true
            if (instant && abs(diff) < 1f) break
            if ((nowNs - startNs) / 1_000_000L > OpenMs + 320) break
        }
    }

    var swipe by remember { mutableStateOf(0f) }
    LazyRow(
        state = state,
        userScrollEnabled = false,   // kaydırma elle seçimi değiştirir; seçili kitap hep ortada
        modifier = Modifier.fillMaxWidth().height(CoverH + 8.dp).pointerInput(shelf.books.size) {
            val step = 44.dp.toPx()
            detectHorizontalDragGestures(onDragStart = { swipe = 0f }) { change, dx ->
                change.consume()
                swipe += dx
                if (abs(swipe) >= step) {
                    val cur = shelf.books.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
                    val next = (cur + if (swipe < 0f) 1 else -1).coerceIn(0, shelf.books.lastIndex)
                    selectedId = shelf.books[next].id
                    swipe = 0f
                }
            }
        },
        contentPadding = PaddingValues(horizontal = if (fits) ((screenW.value - totalDp) / 2f).coerceAtLeast(0f).dp else (screenW - CoverW) / 2),
        horizontalArrangement = Arrangement.spacedBy(SpineGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(shelf.books, key = { it.id }) { b ->
            var bounds by remember { mutableStateOf(Rect.Zero) }
            BookSlot(
                book = b, open = selectedId == b.id,
                modifier = Modifier
                    .graphicsLayer { alpha = if (hiddenId == b.id) 0f else 1f }
                    .onGloballyPositioned { bounds = it.boundsInRoot(); if (focusId == b.id) onFocusBounds(bounds) }
                    // Basılı tut → kitap raftan kalkar, parmakla taşınır, başka bölüme bırakılır.
                    .pointerInput(b.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local -> drag.onStart(b, bounds.topLeft + local) },
                            onDrag = { change, amount -> change.consume(); drag.onMove(amount) },
                            onDragEnd = { drag.onEnd(true) },
                            onDragCancel = { drag.onEnd(false) },
                        )
                    },
                onSelect = { selectedId = b.id },             // sırta dokun: kapağı dönsün
                onOpen = { onBookClick(b, bounds) },          // açık kapağa dokun: öne gelsin
            )
        }
    }
}

/** Açık kitabın dönüş açısı: 90° = sırt tam karşıya bakar (kapalı), bu açıda kapak öne dönük ve sırt yüzü kenarda görünür. */
private const val OpenDeg = 24f
private const val CamDist = 420f   // dp cinsinden kamera uzaklığı (perspektif şiddeti)

/** Bir yüzün ekrandaki 4 köşesi (TL, TR, BR, BL), dp, slot koordinatında. */
private class BookGeom(val width: Float, val spine: FloatArray, val cover: FloatArray, val coverVisible: Boolean)

/**
 * Gerçek 3B kutu: sırt yüzü (kalınlık t) + ön kapak (genişlik w), menteşe ekseni sırt yüzünün ortası.
 * [aDeg] kadar dikey eksende döndürülüp perspektifle düzleme izdüşürülür; iki yüz aynı geometriden çıktığı için
 * birbirine kusursuz oturur (ortak kenar en yakın/en uzun, kapağın sağ kenarı uzakta ve biraz kısa).
 */
private fun bookGeom(aDeg: Float, w: Float, t: Float, h: Float): BookGeom {
    val a = Math.toRadians(aDeg.toDouble())
    val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
    fun sx(xr: Float, z: Float): Float { val zz = -xr * s + z * c; return (xr * c + z * s) * (CamDist / (CamDist - zz)) }
    fun k(xr: Float, z: Float): Float { val zz = -xr * s + z * c; return CamDist / (CamDist - zz) }
    val xBack = sx(0f, -t / 2); val xFront = sx(0f, t / 2); val xRight = sx(w, t / 2)
    val kBack = k(0f, -t / 2); val kFront = k(0f, t / 2); val kRight = k(w, t / 2)
    val minX = minOf(xBack, xFront, xRight); val maxX = maxOf(xBack, xFront, xRight)
    val cy = h / 2
    fun quad(xl: Float, kl: Float, xr: Float, kr: Float) =
        floatArrayOf(xl - minX, cy - cy * kl, xr - minX, cy - cy * kr, xr - minX, cy + cy * kr, xl - minX, cy + cy * kl)
    return BookGeom(
        width = maxX - minX,
        spine = quad(xBack, kBack, xFront, kFront),
        cover = quad(xFront, kFront, xRight, kRight),
        coverVisible = c * CamDist > t / 2 + 1f,
    )
}

/** İçeriği 0,0,w,h dikdörtgeninden [dst] (dp) dörtgenine perspektifle çizer. */
private fun Modifier.projectTo(dst: FloatArray, density: Float, show: Boolean): Modifier = drawWithContent {
    if (!show) return@drawWithContent
    val w = size.width; val h = size.height
    val m = android.graphics.Matrix().apply {
        setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, FloatArray(8) { dst[it] * density }, 0, 4)
    }
    val c = drawContext.canvas.nativeCanvas
    c.save()
    try { c.concat(m); this@drawWithContent.drawContent() } finally { c.restore() }
}

/**
 * Tek kitap: p=0 kapalı sırt (düz, karşıya dönük), p=1 açık (kapak öne dönük, solda sırt yüzü görünür).
 * Arada kutu gerçekten döner; sırt yazısı kapak çıkarken yanda kalır.
 */
@Composable
private fun BookSlot(book: Book, open: Boolean, modifier: Modifier, onSelect: () -> Unit, onOpen: () -> Unit) {
    val p by animateFloatAsState(if (open) 1f else 0f, tween(OpenMs, easing = FastOutSlowInEasing), label = "bookOpen")
    val density = LocalDensity.current.density
    val angle = 90f - (90f - OpenDeg) * p
    val g = bookGeom(angle, CoverW.value, SpineW.value, CoverH.value)
    Box(modifier.width(g.width.dp).height(CoverH)
        .semantics { contentDescription = "${book.title}, ${book.author}" }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = if (open) onOpen else onSelect)) {
        Spine(book, Modifier.width(SpineW).height(CoverH).projectTo(g.spine, density, true))
        BookCover(book, Modifier.width(CoverW).height(CoverH).projectTo(g.cover, density, g.coverVisible), titleSize = 13.sp)
    }
}

/**
 * Kitap sırtı: rengi kapak görselinde en çok yer kaplayan renktir. Görsel okunana kadar (ya da kapak yoksa)
 * kapağın kendi zemin rengi kullanılır; yazı rengi zemine göre açık/koyu seçilir.
 */
@Composable
private fun Spine(book: Book, modifier: Modifier) {
    val context = LocalContext.current
    val url = book.coverUrl
    val fallback = remember(book.id) { coverBaseColor(book) }
    val dominant by produceState<Color?>(url?.let { CoverPalette.cached(it) }?.let { Color(it) }, url) {
        if (url.isNullOrBlank()) { value = null; return@produceState }
        CoverPalette.dominant(context, url)?.let { value = Color(it) }
    }
    val bg by animateColorAsState(dominant ?: fallback, tween(220), label = "spineColor")
    val fg = if (bg.luminance() > .55f) Color(0xFF111111) else Color.White
    Box(modifier.clip(RoundedCornerShape(1.5.dp)).background(bg), contentAlignment = Alignment.TopCenter) {
        Text(book.title, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp).rotatedDown())
    }
}

/** Metni 90° çevirir (yukarıdan aşağı okunur) ve yerleşimi döndürülmüş boyuta göre ayarlar. */
private fun Modifier.rotatedDown(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints(maxWidth = constraints.maxHeight))
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(x = -(placeable.width - placeable.height) / 2, y = (placeable.width - placeable.height) / 2) { rotationZ = 90f }
    }
}
