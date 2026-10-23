package com.cagri.real.books

import com.cagri.real.i18n.t
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import com.cagri.real.ui.components.liquidChrome
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.cos
import kotlin.math.sin

/** PDF / sayfa görseli: sayfalar dikey akışta ya da yatay kaydırmalı okunur. Sayfa metni görünür olunca yüklenir. */
class PagedContent(
    val count: Int,
    val initialPage: Int,
    val loadText: suspend (Int) -> List<String>,
    val loadOriginal: suspend (Int) -> Bitmap,
    val deleteParagraph: suspend (page: Int, paragraph: Int) -> Unit,
    val setHeading: suspend (page: Int, paragraph: Int, heading: Boolean) -> Unit,
    val onPageVisible: (Int) -> Unit,
    val rebuildText: suspend (Int) -> List<String>,
    val editable: Boolean = true,
    val supportsOriginal: Boolean = true,
    val initialOffset: Int = 0,
    val onPagedPosition: (Int, Int) -> Unit = { _, _ -> },
    val prefetchNext: suspend (Int) -> Unit = {},
)

private sealed interface PageState {
    object Loading : PageState
    object Failed : PageState
    class Text(val paragraphs: List<String>, val followsHeading: Boolean = false) : PageState
    class Pic(val bitmap: Bitmap) : PageState
}

/** Shared reading layout for extracted TXT, EPUB, PDF text and OCR. No Listen/TTS, no timer. Top/bottom glass edges. */
@Composable
fun BookReaderScreen(
    chapterLabel: String, chapterTitle: String, paragraphs: List<String>, theme: ReaderTheme,
    onThemeChange: (ReaderTheme) -> Unit, onBack: () -> Unit,
    fontSp: Int, onFontSp: (Int) -> Unit,
    readerFont: ReaderFont = ReaderFont.SANS, onReaderFont: (ReaderFont) -> Unit = {},
    modifier: Modifier = Modifier, loading: Boolean = false, error: String? = null,
    hasOriginal: Boolean = false, showOriginal: Boolean = false, onOriginal: () -> Unit = {},
    paged: PagedContent? = null, onRetry: () -> Unit = {}, reduceAnimations: Boolean = false,
    readMode: ReadMode = ReadMode.VERTICAL, onReadMode: (ReadMode) -> Unit = {},
    initialIndex: Int = 0, initialOffset: Int = 0,
    onPositionChange: (index: Int, offset: Int) -> Unit = { _, _ -> },
) {
    var showTextTools by rememberSaveable { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var currentPage by remember(paged) { mutableIntStateOf(paged?.initialPage ?: 0) }
    var pagedRestored by remember(paged) { mutableStateOf(false) }
    var pageRetry by remember { mutableIntStateOf(0) }
    val family = rememberReaderFamily(readerFont)
    val bg = theme.background; val fg = theme.text
    val sectionInitials = remember(paragraphs) { ParagraphKind.sectionInitials(paragraphs) }
    val horizontal = paged != null && readMode == ReadMode.HORIZONTAL
    val listState = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(currentPage, paged, showOriginal, lifecycle) {
        if (paged != null && !showOriginal) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try { paged.loadText(currentPage); paged.prefetchNext(currentPage) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { /* optional warmup */ }
        }
    }
    val bodyStyle = TextStyle(
        fontFamily = family, fontSize = fontSp.sp, lineHeight = (fontSp * ReaderDefaults.LINE_HEIGHT).sp,
        textAlign = TextAlign.Start, hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph, color = fg,
    )
    // Aşağı kaydırırken ikonlar gizlenir, yukarı kaydırınca geri gelir.
    val connection = remember { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -8f) chromeVisible = false else if (available.y > 8f) chromeVisible = true
            return Offset.Zero
        }
    } }
    LaunchedEffect(chromeVisible) { if (!chromeVisible) showTextTools = false }
    if (paged == null) {
        var positionRestored by remember(chapterLabel) { mutableStateOf(false) }
        LaunchedEffect(paragraphs, initialIndex, initialOffset) {
            if (paragraphs.isNotEmpty() && !positionRestored) {
                val (targetIndex, targetOffset) = ReaderPositionHelper.paragraphToRaw(initialIndex, initialOffset, paragraphs.size)
                listState.scrollToItem(targetIndex, targetOffset)
                positionRestored = true
            }
        }
        LaunchedEffect(listState, positionRestored) {
            if (!positionRestored) return@LaunchedEffect
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .collectLatest { (rawIdx, off) ->
                    delay(300)
                    val (paragraphIdx, safeOffset) = ReaderPositionHelper.rawToParagraph(rawIdx, off)
                    onPositionChange(paragraphIdx, safeOffset)
                }
        }
        DisposableEffect(listState) {
            onDispose {
                val (paragraphIdx, safeOffset) = ReaderPositionHelper.rawToParagraph(
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset
                )
                if (positionRestored) onPositionChange(paragraphIdx, safeOffset)
            }
        }
    } else if (!horizontal) {
        // Restore before emitting a new position, so initial composition cannot overwrite it.
        LaunchedEffect(paged) { listState.scrollToItem((currentPage + 1).coerceAtLeast(0), paged.initialOffset); pagedRestored = true }
        LaunchedEffect(paged, listState, pagedRestored) {
            if (!pagedRestored) return@LaunchedEffect
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .map { (i, offset) -> (i - 1).coerceIn(0, (paged.count - 1).coerceAtLeast(0)) to if (i > 0) offset else 0 }
                .distinctUntilChanged()
                .collectLatest { (page, offset) -> currentPage = page; delay(300); paged.onPagedPosition(page, offset) }
        }
    }
    if (paged != null && !horizontal) DisposableEffect(listState, paged) {
        onDispose { if (pagedRestored) paged.onPagedPosition((listState.firstVisibleItemIndex - 1).coerceIn(0, paged.count - 1), listState.firstVisibleItemScrollOffset) }
    }
    val statusTop = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val navBottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Box(modifier.fillMaxSize().background(bg).nestedScroll(connection)) {
        GlassEdges(background = bg, top = statusTop + 64.dp, bottom = navBottom + 130.dp, modifier = Modifier.fillMaxSize()) {
            if (horizontal && paged != null) {
                HorizontalReader(paged, currentPage, { currentPage = it }, chapterLabel, chapterTitle,
                    showOriginal, pageRetry, theme, family, bodyStyle, fontSp) { pageRetry++ }
            } else LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), state = listState,
                contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 70.dp, bottom = 150.dp)) {
                item("header") { ReaderHeader(chapterLabel, chapterTitle, theme, family) }
                if (paged != null) {
                    items(paged.count, key = { "page_$it" }) { i ->
                        PagedItem(i, paged, showOriginal, pageRetry, theme, family, bodyStyle, fontSp) { pageRetry++ }
                    }
                } else {
                    if (loading) item("loading") { Text(t("Sayfa hazırlanıyor…"), color = theme.muted, fontFamily = family) }
                    if (error != null) item("error") { ReaderRetry(error, theme, onRetry) }
                    itemsIndexed(paragraphs, key = { index, _ -> "paragraph_$index" }) { i, paragraph ->
                        ParagraphText(ParagraphKind.text(paragraph), first = i in sectionInitials, bodyStyle, fontSp, heading = ParagraphKind.isHeading(paragraph), attribution = ParagraphKind.isAttribution(paragraph), furniture = ParagraphKind.isFurniture(paragraph),
                            gap = ParagraphKind.gapLevel(paragraph))
                    }
                }
            }
        }
        AnimatedVisibility(chromeVisible, modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn(tween(if (reduceAnimations) 0 else 140)), exit = fadeOut(tween(if (reduceAnimations) 0 else 100))) {
            com.cagri.real.ui.components.RealBackButton(onBack, Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp), tint = theme.chromeText)
        }
        // Referans: küçük siyah yuvarlak ikonlar (parlaklık, yazı). Listen / zamanlayıcı / ok yok.
        Column(Modifier.align(Alignment.BottomCenter).rawNavBarPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(chromeVisible,
                enter = fadeIn(tween(if (reduceAnimations) 0 else 140)), exit = fadeOut(tween(if (reduceAnimations) 0 else 100))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasOriginal) Text(if (showOriginal) t("Metni oku") else t("Orijinal sayfa"), color = theme.muted, fontSize = 13.sp, fontFamily = family,
                        modifier = Modifier.clickable(onClick = onOriginal).padding(6.dp))
                    AnimatedVisibility(showTextTools, enter = fadeIn(tween(if (reduceAnimations) 0 else 140)),
                        exit = fadeOut(tween(if (reduceAnimations) 0 else 100))) { Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Yazı tipi: her seçenek kendi yazı tipiyle yazılır.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReaderFont.entries.forEach { f ->
                                val on = f == readerFont
                                Text(f.label, color = if (on) theme.background else theme.chromeText, fontSize = 13.sp,
                                    fontFamily = rememberReaderFamily(f),
                                    modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape)
                                        .then(if (on) Modifier.background(theme.text) else Modifier.readerChrome(theme, 18.dp))
                                        .clickable { onReaderFont(f) }.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        ReaderPill("A−", theme) { onFontSp((fontSp - 1).coerceAtLeast(ReaderDefaults.MIN_SP)) }
                        Text("$fontSp", color = theme.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp))
                        ReaderPill("A+", theme) { onFontSp((fontSp + 1).coerceAtMost(ReaderDefaults.MAX_SP)) }
                        if (paged != null) {
                            Spacer(Modifier.width(14.dp))
                            ReaderPill(if (readMode == ReadMode.VERTICAL) "↓ Dikey" else "→ Yatay", theme) {
                                onReadMode(if (readMode == ReadMode.VERTICAL) ReadMode.HORIZONTAL else ReadMode.VERTICAL)
                            }
                        }
                    } } }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).readerChrome(theme, 19.dp).clickable { onThemeChange(theme.next()) }, contentAlignment = Alignment.Center) {
                            SunIcon(theme.chromeText)
                        }
                        Box(Modifier.size(38.dp).readerChrome(theme, 19.dp).clickable { showTextTools = !showTextTools }, contentAlignment = Alignment.Center) {
                            Text("Tт", color = theme.chromeText, fontSize = 15.sp, fontFamily = FontFamily.Serif)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderHeader(label: String, title: String, theme: ReaderTheme, family: FontFamily) {
    Text(label, color = theme.muted, fontSize = 13.sp, fontFamily = family, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(title, color = theme.text, fontSize = 18.sp, fontFamily = family, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 32.dp))
}

/** Yatay mod: sayfalar elle yana kaydırılır. Sayfa ekrandan uzunsa içinde dikey kayar; bitince yana geçilir. Düğme/dokunma yok. */
@Composable
private fun HorizontalReader(
    paged: PagedContent, startPage: Int, onPage: (Int) -> Unit, chapterLabel: String, chapterTitle: String,
    showOriginal: Boolean, retry: Int, theme: ReaderTheme, family: FontFamily, bodyStyle: TextStyle, fontSp: Int, onRetry: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = startPage.coerceIn(0, (paged.count - 1).coerceAtLeast(0))) { paged.count }
    LaunchedEffect(pagerState, paged) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged()
            .collectLatest { p -> onPage(p) }
    }
    HorizontalPager(pagerState, Modifier.fillMaxSize(), key = { "page_$it" }) { i ->
        val scroll = rememberScrollState(if (i == paged.initialPage) paged.initialOffset else 0)
        DisposableEffect(scroll, paged, i) { onDispose { if (pagerState.currentPage == i) paged.onPagedPosition(i, scroll.value) } }
        LaunchedEffect(scroll, paged, i) {
            snapshotFlow { scroll.value }.distinctUntilChanged().collectLatest { offset ->
                delay(300); if (pagerState.currentPage == i) paged.onPagedPosition(i, offset)
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(scroll)
            .padding(start = 26.dp, end = 26.dp, top = 70.dp, bottom = 150.dp)) {
            if (i == 0) ReaderHeader(chapterLabel, chapterTitle, theme, family)
            PagedItem(i, paged, showOriginal, retry, theme, family, bodyStyle, fontSp, onRetry)
        }
    }
}

@Composable
private fun PagedItem(
    index: Int, paged: PagedContent, showOriginal: Boolean, retry: Int, theme: ReaderTheme,
    family: FontFamily, bodyStyle: TextStyle, fontSp: Int, onRetry: () -> Unit,
) {
    var state by remember(index, showOriginal, retry) { mutableStateOf<PageState>(PageState.Loading) }
    var pendingDelete by remember(index, showOriginal, retry) { mutableIntStateOf(-1) }
    var confirmRebuild by remember(index, showOriginal) { mutableStateOf(false) }
    var rebuilding by remember(index) { mutableStateOf(false) }
    var rebuildError by remember(index) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(index, showOriginal, retry) {
        state = try {
            if (showOriginal) PageState.Pic(paged.loadOriginal(index)) else {
                val paragraphs = paged.loadText(index)
                // Only one preceding page is needed. Failure must not hide an otherwise readable page.
                val first = paragraphs.firstOrNull { !BookRichContent.isBlock(it) && ParagraphKind.text(it).isNotBlank() }
                val followsHeading = if (index > 0 && first != null && !ParagraphKind.isHeading(first)) {
                    try {
                        paged.loadText(index - 1).lastOrNull { !BookRichContent.isBlock(it) && ParagraphKind.text(it).isNotBlank() }
                            ?.let(ParagraphKind::isHeading) ?: false
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
                } else false
                PageState.Text(paragraphs, followsHeading)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { PageState.Failed }
    }
    // Yükleme sırasında sabit yükseklik: hızlı kaydırmada tüm sayfaların aynı anda yüklenmesini önler.
    Column(Modifier.fillMaxWidth().then(if (state is PageState.Loading) Modifier.heightIn(min = 460.dp) else Modifier)) {
        Text("${index + 1} / ${paged.count}", color = theme.muted, fontSize = 12.sp, fontFamily = family,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp))
        when (val s = state) {
            PageState.Loading -> Text(t("Sayfa hazırlanıyor…"), color = theme.muted, fontFamily = family)
            PageState.Failed -> ReaderRetry(t("Sayfa şu an açılamadı. Biraz sonra tekrar deneyebilirsin."), theme, onRetry)
            is PageState.Pic -> Image(s.bitmap.asImageBitmap(), t("Orijinal sayfa {0}", index + 1),
                Modifier.fillMaxWidth().aspectRatio(s.bitmap.width.toFloat() / s.bitmap.height), contentScale = ContentScale.Fit)
            is PageState.Text -> if (s.paragraphs.isEmpty()) {
                Text(t("Bu sayfada metin kalmadı. Orijinal sayfayı görüntüleyebilirsin."), color = theme.muted, fontFamily = family)
            } else {
              val initials = remember(s.paragraphs, s.followsHeading) { ParagraphKind.sectionInitials(s.paragraphs, s.followsHeading) }
              s.paragraphs.forEachIndexed { i, raw ->
                val heading = ParagraphKind.isHeading(raw)
                if (BookRichContent.isBlock(raw)) BookRichBlock(raw, bodyStyle) else ParagraphText(ParagraphKind.text(raw), first = i in initials, bodyStyle, fontSp, heading = heading, attribution = ParagraphKind.isAttribution(raw), furniture = ParagraphKind.isFurniture(raw),
                    selected = pendingDelete == i, onLongPress = if (paged.editable) ({ pendingDelete = i }) else null, gap = ParagraphKind.gapLevel(raw))
                // Uzun bas → başlık yap / başlık değil / sil (kalıcı; PDF dosyası değişmez).
                if (pendingDelete == i) Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ReaderPill(if (heading) t("Başlık değil") else t("Başlık yap"), theme) {
                        scope.launch {
                            try {
                                paged.setHeading(index, i, !heading)
                                state = PageState.Text(s.paragraphs.mapIndexed { k, r -> if (k == i) ParagraphKind.withKind(r, !heading) else r }, s.followsHeading)
                                onRetry() // Refresh visible neighbours whose leading initial depends on this page.
                            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                            pendingDelete = -1
                        }
                    }
                    ReaderPill(t("Sil"), theme) {
                        scope.launch {
                            try {
                                paged.deleteParagraph(index, i)
                                state = PageState.Text(s.paragraphs.filterIndexed { k, _ -> k != i }, s.followsHeading)
                                onRetry()
                            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                            pendingDelete = -1
                        }
                    }
                    ReaderPill(t("Vazgeç"), theme) { pendingDelete = -1 }
                }
                ReaderPill(t("Metni yeniden ayır"), theme, enabled = !rebuilding) {
                    rebuildError = null
                    confirmRebuild = true
                }
                rebuildError?.let { Text(it, color = theme.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                Spacer(Modifier.height(14.dp))
                }
              }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
    if (confirmRebuild) Dialog(onDismissRequest = { if (!rebuilding) confirmRebuild = false }) {
        Column(Modifier.fillMaxWidth().readerChrome(theme, 24.dp).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(t("Sayfa metni yeniden ayrılsın mı?"), color = theme.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(t("Yalnız bu sayfanın metni yeniden çıkarılır. Bu sayfada yaptığın başlık ve silme düzenlemeleri sıfırlanır. PDF dosyan değişmez."),
                color = theme.muted, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReaderPill(t("Vazgeç"), theme, enabled = !rebuilding) { confirmRebuild = false }
                ReaderPill(if (rebuilding) t("Hazırlanıyor…") else t("Yeniden ayır"), theme, enabled = !rebuilding) {
                    rebuilding = true
                    scope.launch {
                        try {
                            val paragraphs = paged.rebuildText(index)
                            state = PageState.Text(paragraphs)
                            pendingDelete = -1
                            confirmRebuild = false
                            onRetry()
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            rebuildError = t("Metin yeniden ayrılamadı. Önceki metin korundu; tekrar deneyebilirsin.")
                            confirmRebuild = false
                        } finally { rebuilding = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderRetry(message: String, theme: ReaderTheme, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, color = theme.muted, fontSize = 12.sp)
        Text(t("Tekrar dene"), color = theme.text, fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onRetry).padding(vertical = 12.dp))
    }
}

@Composable
private fun ParagraphText(
    paragraph: String, first: Boolean, style: TextStyle, fontSp: Int,
    selected: Boolean = false, onLongPress: (() -> Unit)? = null, heading: Boolean = false, gap: Int = 0,
    attribution: Boolean = false, furniture: Boolean = false,
) {
    // Eğik işaretleri ayır: düz metin + eğik aralıklar.
    val parsed = remember(paragraph) { BookInline.parse(PdfTextLines.readableSpacing(paragraph)) }
    val plain = parsed.text
    val styled = remember(paragraph, first, fontSp) { buildAnnotatedString {
        val initial = if (first) ParagraphKind.initialRange(plain) else null
        if (initial != null) {
            append(plain.substring(0, initial.first))
            withStyle(SpanStyle(fontSize = (fontSp * 2.8f).sp, fontFamily = FontFamily.Serif)) {
                append(plain.substring(initial.first, initial.last + 1))
            }
            append(plain.substring(initial.last + 1))
        } else append(plain)
        for (r in parsed.italics) addStyle(SpanStyle(fontStyle = FontStyle.Italic), r.first, r.last + 1)
        for (r in parsed.bold) addStyle(SpanStyle(fontWeight = FontWeight.Bold), r.first, r.last + 1)
    } }
    // Orijinal sayfadaki belirgin boşluklar: paragrafın üstüne biraz ekstra yer bırak.
    val extra = when (if (attribution) maxOf(gap, 1) else gap) { 2 -> 34.dp; 1 -> 16.dp; else -> 0.dp }
    val longPress by rememberUpdatedState(onLongPress)
    val gesture = if (onLongPress != null) Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { longPress?.invoke() }) } else Modifier
    val mark = if (selected) Modifier.background(Color.Gray.copy(alpha = .25f), RoundedCornerShape(6.dp)) else Modifier
    if (heading) {
        // Başlık: ortalı, kalın, gövdeden biraz büyük.
        val size = (fontSp * 1.15f)
        Text(styled.let { androidx.compose.ui.text.AnnotatedString(plain, it.spanStyles.filter { s -> s.item.fontStyle == FontStyle.Italic }) },
            style = style.copy(fontSize = size.sp, lineHeight = (size * 1.3f).sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, hyphens = Hyphens.None, letterSpacing = (-0.2).sp),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp + extra, bottom = 14.dp).then(gesture).then(mark))
    } else Text(styled, style = style.copy(
        textAlign = if (attribution) TextAlign.End else TextAlign.Start,
        fontSize = style.fontSize), modifier = Modifier.fillMaxWidth().padding(top = (if (first) 8.dp else 0.dp) + extra, bottom = 12.dp).then(gesture).then(mark))
}

/** Referanstaki gibi düz siyah (temaya göre) yuvarlak / hap yüzey. */
private fun Modifier.readerChrome(theme: ReaderTheme, corners: androidx.compose.ui.unit.Dp): Modifier =
    background(theme.chrome, RoundedCornerShape(corners))

@Composable
private fun ReaderPill(text: String, theme: ReaderTheme, enabled: Boolean = true, onClick: () -> Unit) {
    Text(text, color = theme.chromeText.copy(alpha = if (enabled) 1f else .35f), fontSize = 13.sp,
        modifier = Modifier.heightIn(min = 36.dp).readerChrome(theme, 18.dp).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
private fun SunIcon(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val r = size.minDimension * .19f
        drawCircle(color, r, center, style = Stroke(1.5.dp.toPx()))
        repeat(8) { index ->
            val a = Math.toRadians(index * 45.0)
            drawLine(color, Offset(center.x + cos(a).toFloat() * r * 1.7f, center.y + sin(a).toFloat() * r * 1.7f),
                Offset(center.x + cos(a).toFloat() * r * 2.3f, center.y + sin(a).toFloat() * r * 2.3f), strokeWidth = 1.2.dp.toPx())
        }
    }
}
