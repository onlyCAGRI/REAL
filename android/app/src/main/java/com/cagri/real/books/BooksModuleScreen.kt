package com.cagri.real.books

import com.cagri.real.i18n.t
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.FirebaseAuth
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.PermissionSheet
import com.cagri.real.ui.components.WidgetGlowCard
import com.cagri.real.ui.theme.RealType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun BooksWidgetCard(reduceAnimations: Boolean, onClick: () -> Unit) {
    WidgetGlowCard(Color(0xFFD5B786), reduceAnimations, 0, Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Kitaplar"), color = Color.White, style = RealType.editorialSection)
                Text(t("Kendi rafın, kendi okuma alanın"), color = Color.White.copy(alpha = .6f), fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Color(0xFFAD774F), Color(0xFF6A8591), Color(0xFF788163)).forEachIndexed { i, color ->
                    Box(Modifier.width(20.dp).height((70 + i * 8).dp).background(color, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}
@Composable
fun BooksModuleScreen(reduceAnimations: Boolean, onDismiss: () -> Unit, resumeBookId: String? = null) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
    FullBleed { key(uid) { BooksContent(uid, reduceAnimations, onDismiss, resumeBookId) } }
}

/** Navigation bar boşluğu: üst katmanlar boşluğu "tüketmiş" olsa bile gerçek değeri verir (ham değer). */
@Composable
internal fun Modifier.rawNavBarPadding(): Modifier =
    padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())

/**
 * Üst katmanlar (ör. kök kutudaki navigationBarsPadding) bu ekranı pencerenin altından kısa bırakırsa, eksik kısmı
 * aşağı doğru uzatır; böylece sayfa rengi navigation bar'ın arkasına kadar iner. Boşluk yoksa hiçbir şey yapmaz.
 */
@Composable
internal fun FullBleed(content: @Composable () -> Unit) {
    val windowH = LocalView.current.rootView.height
    var topInWindow by remember { mutableIntStateOf(0) }
    var ownH by remember { mutableIntStateOf(0) }
    val extra = if (ownH == 0) 0 else (windowH - topInWindow - ownH).coerceIn(0, 400)
    Box(Modifier.fillMaxSize()
        .onGloballyPositioned { topInWindow = it.positionInWindow().y.toInt(); ownH = it.size.height }
        .layout { measurable, constraints ->
            val h = constraints.maxHeight + extra
            val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
            layout(placeable.width, constraints.maxHeight) { placeable.place(0, 0) }
        }) { content() }
}
private enum class BookPage { SHELF, DETAIL, READER }
@Composable
private fun BooksContent(uid: String, reduceAnimations: Boolean, onDismiss: () -> Unit, resumeBookId: String?) {
    val context = LocalContext.current
    val store = remember(context, uid) { BookShelfStore(context, uid) }
    val documents = remember(store) { BookDocumentRepository(context.applicationContext, store) }
    val scope = rememberCoroutineScope()
    val failureNotice = remember { mutableStateOf(false) }
    val positionWriter = remember(store) { BookPositionWriter(store) { failureNotice.value = true } }
    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    var theme by remember { mutableStateOf(ReaderTheme.AMOLED) }
    var view by remember { mutableStateOf(BookPage.SHELF) }
    var selected by remember { mutableStateOf<Book?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var paragraphs by remember { mutableStateOf<List<String>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var pageOffset by remember { mutableIntStateOf(0) }
    var pageLoading by remember { mutableStateOf(false) }
    var pageGeneration by remember { mutableIntStateOf(0) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var fontSp by remember { mutableIntStateOf(ReaderDefaults.FONT_SP) }
    var readerFont by remember { mutableStateOf(ReaderFont.SANS) }
    var sections by remember { mutableStateOf<List<String>>(emptyList()) }
    // Bölüm adı penceresi: null kapalı, "" yeni bölüm, diğerleri o bölümün yeniden adlandırılması.
    var sectionEditing by remember { mutableStateOf<String?>(null) }
    var sectionMenu by remember { mutableStateOf<String?>(null) }
    var scanNoticeFor by remember { mutableStateOf<String?>(null) }
    val scannedBooks by documents.scannedBooks.collectAsState()
    LaunchedEffect(view, selected?.id, scannedBooks) {
        val b = selected ?: return@LaunchedEffect
        if (view == BookPage.READER && b.id in scannedBooks && scanNoticeFor != b.id &&
            !runCatching { store.scanNoticeShown(b) }.getOrDefault(true)) scanNoticeFor = b.id
    }
    var readMode by remember { mutableStateOf(ReadMode.VERTICAL) }
    var showOriginal by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    var leaving by remember { mutableStateOf(false) }               // detaydan çıkarken kart önce aşağı iner
    var shelfCoverBounds by remember { mutableStateOf<Rect?>(null) } // dönüşte raftaki kapağın canlı konumu (kök koordinatı)
    val lastShelfBounds = remember { HashMap<String, Rect>() }       // kitabın raftan çıkış konumu (yedek)
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    // Detaydaki kapağın yeri/boyu: ekrana göre hesaplanır; BookFlight tam buraya iner, buradan geri döner.
    val detailCover = remember(rootSize, statusTopPx, navBottomPx, density) { detailCoverRect(rootSize, statusTopPx, navBottomPx, density) }
    fun back() {
        error = null
        when (view) {
            BookPage.SHELF -> onDismiss()
            BookPage.DETAIL -> {
                val b = selected
                if (b == null || reduceAnimations || rootSize.width == 0 || leaving) { selected = null; view = BookPage.SHELF }
                else {
                    // Önce kart aşağı iner, sonra kapak raftaki yerine geri konur.
                    leaving = true
                    scope.launch {
                        delay(200)
                        shelfCoverBounds = null
                        flight = Flight(b, lastShelfBounds[b.id] ?: Rect.Zero, reverse = true)
                        selected = null; view = BookPage.SHELF; leaving = false
                    }
                }
            }
            BookPage.READER -> { view = BookPage.DETAIL; showOriginal = false }
        }
    }
    BackHandler { if (!busy && flight == null && !leaving) back() }
    fun setTheme(value: ReaderTheme) { scope.launch {
        try { store.saveTheme(value); theme = value } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = t("Tema kaydedilemedi.") }
    } }
    fun openBook(book: Book) { selected = book; view = BookPage.DETAIL; error = null }
    fun startFlight(book: Book, bounds: Rect) {
        if (flight != null) return
        if (reduceAnimations || bounds == Rect.Zero || rootSize.width == 0) { openBook(book); return }
        val from = bounds.translate(-rootPos.x, -rootPos.y)
        lastShelfBounds[book.id] = from
        flight = Flight(book, from)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null
            try {
                val book = documents.importFile(uri)
                books = store.load()
                openBook(book)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error = e.message?.take(180) ?: t("Kitap eklenemedi.")
            } finally { busy = false }
        }
    }
    LaunchedEffect(store) {
        try {
            books = store.load(); theme = store.theme(); fontSp = store.fontSize(); readMode = store.readMode()
            readerFont = store.readerFont(); sections = store.sections(); loaded = true
        }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = t("Kitaplık açılamadı. Veriler değiştirilmedi.") }
        finally { busy = false }
    }
    // Yaşam ekranından "kaldığın yerden devam": kitap yüklenince doğrudan okuyucuyu açar, olmazsa detayı açar.
    LaunchedEffect(loaded) {
        val id = resumeBookId ?: return@LaunchedEffect
        if (!loaded) return@LaunchedEffect
        val target = books.firstOrNull { it.id == id } ?: return@LaunchedEffect
        try {
            if (documents.hasLocalContent(target)) {
                val prepared = documents.prepareReading(target); selected = prepared
                books = store.load()
                page = store.page(prepared).coerceAtMost((prepared.pageCount ?: 1) - 1)
                pageOffset = store.readingPosition(prepared).second
                showOriginal = false; view = BookPage.READER
            } else openBook(target)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { openBook(target) }
    }
    // PDF / görsel PagedContent ile sayfa sayfa tembel yüklenir; burada yalnız TXT / EPUB / Gutenberg.
    LaunchedEffect(view, selected?.id, retry) {
        val generation = ++pageGeneration
        val book = selected
        if (view != BookPage.READER || book == null || book.format == "pdf" || book.format == "image" || book.format == "epub") { pageLoading = false; return@LaunchedEffect }
        pageLoading = true; pageError = null; paragraphs = emptyList()
        try {
            paragraphs = documents.text(book, 0)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { pageError = t("Sayfa açılamadı. Tekrar denemek için dokun.") }
        finally { if (pageGeneration == generation) pageLoading = false }
    }
    // Displayed bitmaps are released through GC; RenderThread may still hold a prior frame.
    val shelfColors = remember { listOf(Color(0xFFF0A24A), Color(0xFF48A6EE), Color(0xFF6FBF8B), Color(0xFFB587E0)) }
    // Kitapların bölümleri, ardından kişinin açtığı ama henüz boş olan bölümler.
    val shelves = remember(books, sections) {
        val grouped = books.groupBy { it.category.ifBlank { "Genel" } }
        val names = grouped.keys + sections.filterNot { it in grouped.keys }
        names.mapIndexed { i, name -> Shelf(name, grouped[name].orEmpty(), shelfColors[i % shelfColors.size]) }
    }
    fun sectionAction(block: suspend () -> Unit) { scope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message?.take(120) ?: t("Bölüm kaydedilemedi.") }
    } }
    BookSystemBars(theme)
    Box(Modifier.fillMaxSize().background(theme.background)
        .onGloballyPositioned { rootPos = it.positionInRoot(); rootSize = it.size }.pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) }
    }) {
        when (view) {
            BookPage.SHELF -> BooksShelfScreen(shelves, { b, r -> startFlight(b, r) }, hiddenId = flight?.book?.id,
                focusId = flight?.takeIf { it.reverse }?.book?.id,
                onFocusBounds = { r -> shelfCoverBounds = r.translate(-rootPos.x, -rootPos.y) },
                onAddBooks = { if (loaded && !busy) { error = null; importer.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain", "image/*")) } }, onBack = ::back,
                onThemeChange = { setTheme(theme.next()) }, themeLabel = theme.label,
                background = theme.background, textColor = theme.text,
                chrome = theme.chrome, chromeText = theme.chromeText,
                onAddSection = { if (loaded) sectionEditing = "" },
                onSectionMenu = { sectionMenu = it },
                onMoveBook = { b, to -> sectionAction { books = store.moveBook(b.id, to) } })
            BookPage.DETAIL -> selected?.let { book ->
                // Kapak raftan çıkıp öne gelir (BookFlight), ardından alttaki "pencere" yumuşakça gelir.
                var shown by remember(book.id) { mutableStateOf(false) }
                LaunchedEffect(book.id) { shown = true }
                val reveal by animateFloatAsState(if (shown && !leaving) 1f else 0f, tween(if (reduceAnimations) 0 else if (leaving) 180 else 300), label = "detailReveal")
                val canRead by produceState<Boolean?>(null, book, documents) {
                    value = documents.hasLocalContent(book)
                }
                val showPrimary = canRead != null
                val primaryLabel = if (canRead == true) "Oku" else "Dosyadan ekle"
                val primary: () -> Unit = {
                    if (canRead == true) scope.launch {
                        try {
                            val prepared = documents.prepareReading(book); selected = prepared
                            books = store.load()
                            page = store.page(prepared).coerceAtMost((prepared.pageCount ?: 1) - 1)
                            pageOffset = store.readingPosition(prepared).second
                            showOriginal = false; view = BookPage.READER
                        }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = t("Okuma konumu açılamadı.") }
                    } else importer.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain", "image/*"))
                }

                val remove: () -> Unit = { scope.launch {
                    busy = true
                    try { books = store.remove(book); selected = null; view = BookPage.SHELF }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = t("Kitap kaldırılamadı.") }
                    finally { busy = false }
                } }
                var editing by remember(book.id) { mutableStateOf(false) }
                var draft by remember(book.id) { mutableStateOf(book.title) }
                val saveTitle: () -> Unit = {
                    val t = draft.trim()
                    if (t.isNotEmpty() && t != book.title) scope.launch {
                        try {
                            books = store.rename(book, t)
                            selected = books.firstOrNull { it.id == book.id } ?: book.copy(title = t)
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = t("Kitap adı kaydedilemedi.") }
                    }
                    editing = false
                }
                val coverW = with(density) { detailCover.width.toDp() }
                val coverH = with(density) { detailCover.height.toDp() }
                val coverTopInItem = with(density) { (detailCover.top - statusTopPx).toDp() } - 24.dp   // içerik üst boşluğu 24dp
                LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(24.dp, 24.dp, 24.dp, 0.dp)) {
                    // İlk öğe tam ekran: üstte başlık + büyük kapak, altta (ekranın dibinde) başlık/yazar/Oku/Kaldır kutusu.
                    item {
                        Box(Modifier.fillParentMaxHeight(1f).fillMaxWidth()) {
                            ModuleHeader(t("Kitap"), theme, ::back)
                            Box(Modifier.align(Alignment.TopCenter).padding(top = coverTopInItem)) {
                                BookCover(book, Modifier.width(coverW).height(coverH))
                            }
                            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().rawNavBarPadding().padding(bottom = 20.dp)
                                .graphicsLayer { alpha = reveal; translationY = (1f - reveal) * 60.dp.toPx() }
                                .clip(RoundedCornerShape(28.dp)).background(theme.chrome).padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(book.title, color = theme.chromeText, fontSize = 24.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { draft = book.title; editing = true })
                                Text(book.author, color = theme.chromeText.copy(alpha = .6f), fontSize = 14.sp, textAlign = TextAlign.Center,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                Text(t("Adı düzenle"), color = theme.chromeText.copy(alpha = .55f), fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 10.dp).clip(CircleShape)
                                        .clickable { draft = book.title; editing = true }.padding(horizontal = 12.dp, vertical = 4.dp))
                                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (showPrimary) PanelButton(primaryLabel, true, theme, Modifier.weight(1f), primary)
                                    PanelButton(t("Kaldır"), false, theme, Modifier.weight(1f), remove)
                                }
                            }
                            if (editing) Dialog(onDismissRequest = { editing = false }) {
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(theme.chrome).padding(20.dp)) {
                                    Text(t("Kitap adı"), color = theme.chromeText.copy(alpha = .6f), fontSize = 13.sp)
                                    Box(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                        .background(theme.chromeText.copy(alpha = .1f)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                                        BasicTextField(draft, { draft = it },
                                            textStyle = TextStyle(color = theme.chromeText, fontSize = 18.sp, fontFamily = FontFamily.Serif),
                                            cursorBrush = SolidColor(theme.chromeText), maxLines = 3,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { saveTitle() }),
                                            modifier = Modifier.fillMaxWidth())
                                    }
                                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        PanelButton(t("Vazgeç"), false, theme, Modifier.weight(1f)) { editing = false }
                                        PanelButton(t("Kaydet"), true, theme, Modifier.weight(1f), saveTitle)
                                    }
                                }
                            }
                        }
                    }
                    // Açıklama, lisans, kaynak: aşağı kaydırınca görünür.
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 120.dp).graphicsLayer { alpha = reveal },
                            horizontalAlignment = Alignment.Start) {
                            book.rights?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = theme.muted, fontSize = 12.sp)
                            }
                            if (!book.description.isNullOrBlank()) Text(book.description, color = theme.text, fontSize = 15.sp,
                                modifier = Modifier.padding(top = 12.dp))
                            if (canRead == false) Text(t("Bu kitap için cihazda tam dosya bulunamadı. Kendi PDF, EPUB veya TXT dosyanı ekleyebilirsin. Mevcut kitap bilgileri korunuyor."),
                                color = theme.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                book.licenseUrl?.let { url -> ModulePill(t("Kitabın lisansı ↗"), theme) {
                                    try { if (Uri.parse(url).scheme == "https") context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    catch (_: Exception) { error = t("Lisans bağlantısı açılamadı.") }
                                } }
                                book.infoUrl?.let { url -> ModulePill(t("Kaynakta göster ↗"), theme) {
                                    try { if (Uri.parse(url).scheme == "https") context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    catch (_: Exception) { error = t("Bağlantı açılamadı.") }
                                } }
                            }
                        }
                    }
                }
            }
            BookPage.READER -> selected?.let { book ->
                val hasPdfOriginal = book.format == "pdf" || book.format == "image"
                val isPaged = hasPdfOriginal || book.format == "epub"
                val paged = if (isPaged) remember(book.id, page) { PagedContent(
                    count = (book.pageCount ?: 1).coerceAtLeast(1), initialPage = page, initialOffset = pageOffset,
                    loadText = { documents.text(book, it) }, loadOriginal = { documents.original(book, it) },
                    deleteParagraph = { p, i -> documents.deleteParagraph(book, p, i) },
                    setHeading = { p, i, h -> documents.setHeading(book, p, i, h) },
                    rebuildText = { p -> documents.text(book, p, rebuildLayout = true).also {
                        if (it.isEmpty()) throw java.io.IOException(t("Sayfa metni bulunamadı."))
                    } },
                    editable = hasPdfOriginal, supportsOriginal = hasPdfOriginal,
                    onPagedPosition = { p, offset -> positionWriter.save(book, p, offset, true) },
                    prefetchNext = { p -> documents.prefetchNext(book, p) },
                    onPageVisible = { p -> positionWriter.save(book, p, 0, true) }) } else null
                var readerPos by remember(book.id) { mutableStateOf(0 to 0) }
                LaunchedEffect(book.id) {
                    if (!isPaged) {
                        try { readerPos = store.readingPosition(book) } catch (_: Exception) {}
                    }
                }
                BookReaderScreen(if (book.format == "epub") t("{0} bölüm", book.pageCount ?: 0) else if (book.pageCount != null) t("{0} sayfa", book.pageCount) else t("Okuma"), book.title, paragraphs, theme,
                    ::setTheme, ::back, fontSp = fontSp,
                    readerFont = readerFont,
                    onReaderFont = { f -> readerFont = f; scope.launch { try { store.saveReaderFont(f) } catch (e: CancellationException) { throw e } catch (_: Exception) {} } },
                    onFontSp = { v -> fontSp = v; scope.launch { try { store.saveFontSize(v) } catch (e: CancellationException) { throw e } catch (_: Exception) {} } },
                    readMode = readMode,
                    onReadMode = { m -> readMode = m; scope.launch { try { store.saveReadMode(m) } catch (e: CancellationException) { throw e } catch (_: Exception) {} } },
                    loading = pageLoading, error = pageError,
                    hasOriginal = hasPdfOriginal, showOriginal = showOriginal,
                    onOriginal = { showOriginal = !showOriginal }, paged = paged,
                    onRetry = { retry++ }, reduceAnimations = reduceAnimations,
                    initialIndex = readerPos.first, initialOffset = readerPos.second,
                    onPositionChange = { idx, off ->
                        readerPos = idx to off
                        positionWriter.save(book, idx, off, false)
                    })
            }
        }
        if (failureNotice.value) {
            Text(t("Okuma konumu kaydedilemedi. Son başarılı konum korunuyor; yeniden denemek için dokun."),
                color = theme.text, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(20.dp)
                    .clickable { failureNotice.value = false; positionWriter.retry() })
        }
        flight?.let { f ->
            BookFlight(f, detailCover,
                liveShelf = { if (f.reverse) shelfCoverBounds else null }) {
                if (!f.reverse) openBook(f.book)
                flight = null
            }
        }
        // Taranmış sayfa: metin OCR ile okunur; kişiye bir kez, açıkça söylenir.
        val noticeBook = selected?.takeIf { view == BookPage.READER && it.id == scanNoticeFor }
        fun closeScanNotice(original: Boolean) {
            val b = noticeBook ?: return
            scanNoticeFor = null
            if (original) showOriginal = true
            scope.launch { try { store.markScanNoticeShown(b) } catch (e: CancellationException) { throw e } catch (_: Exception) {} }
        }
        PermissionSheet(visible = noticeBook != null, title = t("Taranmış sayfalar"),
            description = t("Bu kitabın bazı sayfalarında seçilebilir metin yok, sayfa yalnız görüntü olarak kayıtlı. ") +
                t("REAL bu sayfaları cihazında yazı tanıma (OCR) ile okuyor; bazı kelimeler yanlış ya da eksik çıkabilir. ") +
                t("Emin olmadığın yerde asıl sayfaya bakabilirsin."),
            onAllow = { closeScanNotice(false) }, onLater = { closeScanNotice(true) },
            reduceAnimations = reduceAnimations, primaryLabel = t("Anladım"), secondaryLabel = t("Asıl sayfayı göster"),
            glyph = null, onDismiss = { closeScanNotice(false) })
        sectionMenu?.let { name ->
            SectionMenu(name, theme,
                onRename = { sectionMenu = null; sectionEditing = name },
                onDelete = { sectionMenu = null; sectionAction { val (b, s) = store.deleteSection(name); books = b; sections = s } },
                onDismiss = { sectionMenu = null })
        }
        sectionEditing?.let { editing ->
            SectionNameSheet(initial = editing, theme = theme,
                onSave = { name ->
                    sectionEditing = null
                    sectionAction {
                        if (editing.isEmpty()) sections = store.addSection(name)
                        else { val (b, s) = store.renameSection(editing, name); books = b; sections = s }
                    }
                },
                onDismiss = { sectionEditing = null })
        }
        error?.let { message ->
            Column(Modifier.align(Alignment.BottomCenter).rawNavBarPadding().padding(16.dp)
                .background(theme.chrome, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(message, color = theme.chromeText, fontSize = 12.sp)
                Text(t("Kapat"), color = theme.chromeText, fontSize = 12.sp,
                    modifier = Modifier.clickable { error = null }.padding(vertical = 12.dp))
            }
        }
    }
    if (busy) Dialog(onDismissRequest = {}) {
        Text(t("Hazırlanıyor…"), color = theme.text, modifier = Modifier.background(theme.chrome, RoundedCornerShape(24.dp)).padding(28.dp))
    }
}
/** Yeni bölüm / yeniden adlandırma: koyu cam kart, tek satırlık ad. */
@Composable
private fun SectionNameSheet(initial: String, theme: ReaderTheme, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial, androidx.compose.ui.text.TextRange(initial.length))) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f))
        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 32.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(theme.chrome).border(0.8.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(26.dp))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
            .padding(22.dp)) {
            Text(if (initial.isEmpty()) t("Yeni bölüm") else t("Bölümün adı"), color = theme.chromeText, fontSize = 20.sp,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = name, onValueChange = { if (it.text.length <= 40) name = it }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = theme.chromeText, fontSize = 17.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.chromeText),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (name.text.isNotBlank()) onSave(name.text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = .08f)).padding(horizontal = 14.dp, vertical = 13.dp),
                decorationBox = { inner ->
                    if (name.text.isEmpty()) Text(t("Örn. Felsefe"), color = theme.chromeText.copy(alpha = .4f), fontSize = 17.sp)
                    inner()
                })
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("Vazgeç"), color = theme.chromeText.copy(alpha = .7f), fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(CircleShape).clickable(onClick = onDismiss).padding(vertical = 13.dp))
                Text(if (initial.isEmpty()) t("Ekle") else t("Kaydet"), color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(CircleShape).background(Color.White.copy(alpha = if (name.text.isBlank()) .4f else 1f))
                        .clickable(enabled = name.text.isNotBlank()) { onSave(name.text) }.padding(vertical = 13.dp))
            }
        }
    }
}

/** Bölüm başlığına basılı tutunca: yeniden adlandır / sil (kitaplar Dosyalarım'a döner). */
@Composable
private fun SectionMenu(name: String, theme: ReaderTheme, onRename: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f))
        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.rawNavBarPadding().padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(theme.chrome).border(0.8.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(26.dp)).padding(vertical = 8.dp)) {
            Text(name.uppercase(), color = theme.chromeText.copy(alpha = .55f), fontSize = 12.sp, fontFamily = FontFamily.Serif,
                letterSpacing = 1.2.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
            Text(t("Yeniden adlandır"), color = theme.chromeText, fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onRename).padding(horizontal = 22.dp, vertical = 15.dp))
            if (name != BookShelfStore.DEFAULT_SECTION) {
                Text(t("Bölümü sil"), color = Color(0xFFFF8A7A), fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onDelete).padding(horizontal = 22.dp, vertical = 15.dp))
                Text(t("İçindeki kitaplar Dosyalarım'a taşınır."), color = theme.chromeText.copy(alpha = .45f), fontSize = 12.sp,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 12.dp))
            }
        }
    }
}

@Composable
private fun ModuleHeader(title: String, theme: ReaderTheme, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        com.cagri.real.ui.components.RealBackButton(onBack, tint = theme.chromeText)
        Text(title, color = theme.text, fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 14.dp))
    }
}
@Composable
private fun ModulePill(text: String, theme: ReaderTheme, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Text(text, color = (if (selected) Color.Black else theme.chromeText).copy(alpha = if (enabled) 1f else .4f), fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 4.dp).clip(CircleShape).background(if (selected) Color(0xFFDED4C1) else theme.chrome)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp))
}

/**
 * Detay sayfasındaki kapağın konumu (kök piksel koordinatı). Üstte başlığın altı, altta kutuya (~262dp) kadar
 * olan boşluğu doldurur; oran 2:3, en fazla 480dp yüksek. Kutu ekranın dibinde durur.
 */
private fun detailCoverRect(root: IntSize, statusTop: Int, navBottom: Int, d: Density): Rect {
    val top0 = statusTop + with(d) { 84.dp.toPx() }   // 24 içerik boşluğu + 40 başlık + 20
    if (root.width == 0 || root.height == 0) {
        val w = with(d) { 232.dp.toPx() }; val h = with(d) { 348.dp.toPx() }
        return Rect(0f, top0, w, top0 + h)
    }
    val availH = root.height - navBottom - with(d) { 262.dp.toPx() } - top0
    val byWidth = (root.width - with(d) { 64.dp.toPx() }) * 1.5f
    val h = minOf(availH, byWidth, with(d) { 480.dp.toPx() }).coerceAtLeast(with(d) { 220.dp.toPx() })
    val w = h / 1.5f
    val top = top0 + (availH - h).coerceAtLeast(0f) / 2f
    return Rect((root.width - w) / 2f, top, (root.width + w) / 2f, top + h)
}

private class Flight(val book: Book, val from: Rect, val reverse: Boolean = false)

/**
 * İleri: sırttan açılmış kapak, rafın (satırın) içinden ekranın önüne (detaydaki yerine) süzülür.
 * Geri (reverse): kapak detaydaki yerinden satırdaki yerine süzülür; ardından sıra içinde sırta kapanır.
 * Raf/tahta yok; kırpma ve yukarı kalkma kaldırıldı.
 */
@Composable
private fun BookFlight(flight: Flight, target: Rect, liveShelf: () -> Rect?, onDone: () -> Unit) {
    val start = if (flight.reverse) 1f else 0f
    val fly = remember(flight) { Animatable(start) }
    LaunchedEffect(flight) {
        if (flight.reverse) {
            // Satırdaki kapağın konumu ilk yerleşimden sonra gelir; kısa süre bekle.
            withTimeoutOrNull(400) { snapshotFlow { liveShelf() }.first { it != null } }
            if ((liveShelf() ?: flight.from) == Rect.Zero) { onDone(); return@LaunchedEffect }
            fly.animateTo(0f, tween(460, easing = FastOutSlowInEasing))
        } else {
            fly.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
        onDone()
    }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
    }) {
        Box(Modifier.size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
            .graphicsLayer {
                val shelf = liveShelf() ?: flight.from
                val f = fly.value
                val scale = lerp(shelf.width / target.width, 1f, f)
                val cx = lerp(shelf.center.x, target.center.x, f)
                val cy = lerp(shelf.center.y, target.center.y, f)
                transformOrigin = TransformOrigin.Center
                scaleX = scale; scaleY = scale
                translationX = cx - target.width / 2f
                translationY = cy - target.height / 2f
                shadowElevation = 22.dp.toPx() * f
                clip = false; shape = RectangleShape
            }) { BookCover(flight.book, Modifier.fillMaxSize(), titleSize = 18.sp) }
    }
}

@Composable
private fun PanelButton(text: String, primary: Boolean, theme: ReaderTheme, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(46.dp).clip(CircleShape)
        .background(if (primary) theme.chromeText else theme.chromeText.copy(alpha = .14f))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (primary) theme.chrome else theme.chromeText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
