package com.cagri.real.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cagri.real.data.MediaType
import com.cagri.real.discovery.DiscoveryCard
import com.cagri.real.discovery.MediaCastMember
import com.cagri.real.discovery.MediaDetail
import com.cagri.real.discovery.MediaDetailRepository
import com.cagri.real.ui.theme.RealType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Read-only catalog details; the underlying poster queue is retained while this is open. */
@Composable
fun MediaDiscoveryDetail(card: DiscoveryCard, repository: MediaDetailRepository, reduceAnimations: Boolean, onBack: () -> Unit) {
    var detail by remember(card.key) { mutableStateOf<MediaDetail?>(null) }
    var loading by remember(card.key) { mutableStateOf(true) }
    var failed by remember(card.key) { mutableStateOf(false) }
    var retry by remember(card.key) { mutableIntStateOf(0) }
    var readMore by remember(card.key) { mutableStateOf(false) }
    var arrived by remember(card.key) { mutableStateOf(false) }
    var leaving by remember(card.key) { mutableStateOf(false) }
    BackHandler { leaving = true }
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    // A fixed decode size avoids restarting the image request on every animated layout frame.
    val heroImage = remember(card.media.artworkUrl, context) {
        ImageRequest.Builder(context).data(card.media.artworkUrl).size(720, 1080).build()
    }
    LaunchedEffect(card.key, repository, retry) {
        loading = true; failed = false
        try { detail = repository.load(card.media) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(card.key) { arrived = true }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .94f)).pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent() }
    }) {
        val heroWidth = (maxWidth - 40.dp).coerceAtLeast(1.dp)
        val finalHeight = (heroWidth * .88f).coerceAtMost(300.dp)
        val initialHeight = (heroWidth / .667f).coerceAtMost((maxHeight - 88.dp).coerceAtLeast(1.dp))
        val heroHeight by animateDpAsState(if (arrived && !leaving) finalHeight else initialHeight,
            animationSpec = tween(if (reduceAnimations) 0 else 380, easing = FastOutSlowInEasing), label = "detail-poster-height")
        PosterEdgeGlow(card.media.artworkUrl, reduceAnimations, Modifier.fillMaxWidth().height(340.dp))
        val scroll = rememberScrollState()
        val density = LocalDensity.current
        var headerPx by remember { mutableIntStateOf(0) }
        val contentAlpha by animateFloatAsState(if (leaving) 0f else 1f,
            tween(if (reduceAnimations) 0 else 240), label = "detail-content-alpha")
        // Closing replays the opening: poster grows back to full size while the rest fades, then the screen is dismissed.
        LaunchedEffect(leaving) {
            if (!leaving) return@LaunchedEffect
            if (!reduceAnimations && scroll.value > 0) launch { scroll.animateScrollTo(0, tween(260, easing = FastOutSlowInEasing)) }
            delay(if (reduceAnimations) 0L else 380L)
            onBack()
        }
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(scroll)) {
            // The header floats above the scrolling content, so reserve its height here.
            Spacer(Modifier.height(with(density) { headerPx.toDp() }))
            run {
                Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(heroHeight)
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xFF1B1B20))) {
                    var imageFailed by remember(card.media.artworkUrl) { mutableStateOf(false) }
                    AsyncImage(heroImage, card.media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                        onError = { imageFailed = true })
                    if (imageFailed || card.media.artworkUrl.isNullOrBlank()) ActionIcon(ActionGlyph.Video,
                        Modifier.size(44.dp).align(Alignment.Center), Color.White.copy(alpha = .6f))
                }
                Column(Modifier.graphicsLayer { alpha = contentAlpha }) {
                Text(card.media.title, style = RealType.editorialSubtitle, color = Color.White,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), fontSize = 26.sp, lineHeight = 30.sp)
                val description = detail?.description?.takeIf { it.isNotBlank() } ?: card.media.description
                var collapsedPx by remember(card.key, description) { mutableIntStateOf(0) }
                var fullPx by remember(card.key, description) { mutableIntStateOf(0) }
                val textAnim = remember(card.key) { Animatable(0f) }
                val targetPx = if (readMore) fullPx else collapsedPx
                LaunchedEffect(targetPx, reduceAnimations) {
                    if (targetPx == 0) return@LaunchedEffect
                    if (textAnim.value == 0f || reduceAnimations) textAnim.snapTo(targetPx.toFloat())
                    else textAnim.animateTo(targetPx.toFloat(), tween(380, easing = FastOutSlowInEasing))
                }
                val fade = if (fullPx > collapsedPx + 2) (1f - (textAnim.value - collapsedPx) / (fullPx - collapsedPx).toFloat()).coerceIn(0f, 1f) else 0f
                val guessHeight = with(density) { (21.sp * 4).toDp() }
                // The full text is always laid out; only the clipped box height animates, so no frame jump or ellipsis flicker.
                Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth()
                    .height(if (fullPx > 0 && textAnim.value > 0f) with(density) { textAnim.value.toDp() } else guessHeight)
                    .clipToBounds()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (fade > 0f) drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - fade)),
                            startY = size.height - 26.dp.toPx(), endY = size.height), blendMode = BlendMode.DstIn)
                    }) {
                    Text(description?.takeIf { it.isNotBlank() } ?: "Bu yapımın konusu henüz bulunamadı.",
                        color = Color.White.copy(alpha = .72f), fontSize = 14.sp, lineHeight = 21.sp,
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true),
                        onTextLayout = { r ->
                            fullPx = r.size.height
                            collapsedPx = r.getLineBottom(minOf(3, r.lineCount - 1)).toInt()
                        })
                }
                if (!description.isNullOrBlank()) Text(if (readMore) "Daha az" else "Devamını oku", color = Color.White,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp)
                        .clickable(role = Role.Button) { readMore = !readMore }.padding(8.dp))
                Text("Cast", style = RealType.editorialSubtitle, color = Color.White,
                    modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 12.dp))
                when {
                    loading -> Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.cagri.real.ui.components.RealSpinner(Modifier.size(18.dp), color = Color.White.copy(alpha = .65f), strokeWidth = 2.dp)
                        Text("Oyuncular yükleniyor…", color = Color.White.copy(alpha = .55f), fontSize = 12.sp)
                    }
                    failed -> Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Oyuncular şu anda alınamadı.", color = Color.White.copy(alpha = .55f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("Tekrar dene", color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button) { retry++ }.padding(12.dp))
                    }
                    detail?.cast.isNullOrEmpty() -> Text("Oyuncu bilgisi bulunamadı.", color = Color.White.copy(alpha = .55f), fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    else -> LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(detail!!.cast, key = { it.id }) { person -> CastPortrait(person) }
                    }
                }
                val sourceUrl = if (card.media.provider.equals("TVmaze", true)) "https://www.tvmaze.com/shows/${card.media.providerId}"
                    else "https://thetvdb.com"
                Text("${card.media.provider} · Kaynak", color = Color.White.copy(alpha = .4f), fontSize = 11.sp,
                    modifier = Modifier.padding(start = 12.dp, top = 18.dp).clickable(role = Role.Button) { runCatching { uri.openUri(sourceUrl) } }.padding(8.dp))
                Spacer(Modifier.height(24.dp))
                }
            }
        }
        // Floating liquid-glass header: the poster scrolls under it and shows through blurred.
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().graphicsLayer { alpha = contentAlpha }.onSizeChanged { headerPx = it.height }) {
            Box(Modifier.matchParentSize().clipToBounds()) {
                AsyncImage(heroImage, null,
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().requiredHeight(heroHeight)
                        .graphicsLayer {
                            translationY = headerPx - scroll.value.toFloat()
                            alpha = (scroll.value / 60f).coerceIn(0f, 1f)
                        }.blur(28.dp),
                    contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().graphicsLayer { alpha = (scroll.value / 60f).coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = .38f)))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(0.5.dp)
                    .graphicsLayer { alpha = (scroll.value / 60f).coerceIn(0f, 1f) * .7f }
                    .background(Color.White.copy(alpha = .18f)))
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RealBackButton({ leaving = true }, description = "Afişlere dön")
                Spacer(Modifier.weight(1f))
                DetailCapsule(if (card.media.type == MediaType.TV) "Dizi" else "Film")
                val duration = detail?.durationMillis ?: card.media.durationMillis
                DetailCapsule(duration?.let { detailDuration(it, card.media.type == MediaType.TV) })
                DetailCapsule(null, rating = true)
            }
        }
    }
}

@Composable
private fun DetailCapsule(label: String?, rating: Boolean = false) {
    Box(Modifier.heightIn(min = 30.dp).widthIn(min = if (rating) 54.dp else 48.dp)
        .liquidChrome(corners = 20.dp).padding(horizontal = 10.dp, vertical = 6.dp)
        .semantics { if (label == null) contentDescription = if (rating) "Puan henüz mevcut değil" else "Süre bilgisi mevcut değil" },
        contentAlignment = Alignment.Center) {
        if (label != null) Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}
private fun detailDuration(duration: Long, series: Boolean): String? {
    if (duration !in 60_000L..86_400_000L) return null
    val minutes = duration / 60_000L
    val value = if (minutes >= 60) "${minutes / 60} sa ${minutes % 60} dk" else "$minutes dk"
    return if (series) "$value / bölüm" else value
}
@Composable
private fun CastPortrait(person: MediaCastMember) {
    Column(Modifier.width(94.dp)) {
        var failed by remember(person.imageUrl) { mutableStateOf(false) }
        Box(Modifier.size(94.dp, 124.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF202025)), contentAlignment = Alignment.Center) {
            AsyncImage(person.imageUrl, person.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = true })
            if (failed || person.imageUrl == null) Text(person.name.take(1), color = Color.White.copy(alpha = .5f), fontSize = 28.sp)
        }
        Text(person.name, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        person.role?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = .45f), fontSize = 10.sp,
            lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
    }
}
