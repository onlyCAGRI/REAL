package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cagri.real.data.CatalogCardDisplay
import com.cagri.real.data.MediaBlock
import com.cagri.real.data.MediaCatalog
import com.cagri.real.data.MediaType
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.theme.RealType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSearchSheet(
    designStyle: DesignStyle,
    onDismiss: () -> Unit,
    onSelect: (MediaBlock) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaBlock>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RealSheetDefaults.Ink,
        contentColor = Color.White,
        scrimColor = RealSheetDefaults.Scrim,
        shape = RealSheetDefaults.Shape,
        dragHandle = { RealSheetDefaults.DragHandle() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        val panel = if (designStyle == DesignStyle.LiquidGlass) Modifier.liquidChrome(corners = 24.dp) else Modifier
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .then(panel)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(t("Film & Dizi"), style = RealType.detailDialogTitle)

            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(200) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(t("Film veya dizi ara")) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF85858D),
                    unfocusedBorderColor = Color(0xFF424248),
                    cursorColor = Color.White
                )
            )

            // Searches while typing (short pause after the last keystroke): films (TheTVDB) and series (TVmaze) together,
            // closest title matches first, then the rest alternating film / series.
            LaunchedEffect(query) {
                val q = query.trim()
                if (q.length < 2) { results = emptyList(); message = null; searching = false; return@LaunchedEffect }
                kotlinx.coroutines.delay(350)
                searching = true
                message = null
                try {
                    val (movies, series) = kotlinx.coroutines.coroutineScope {
                        val m = async { runCatching { MediaCatalog.search(q, MediaType.MOVIE) } }
                        val t = async { runCatching { MediaCatalog.search(q, MediaType.TV) } }
                        m.await() to t.await()
                    }
                    if (movies.isFailure && series.isFailure) {
                        results = emptyList()
                        message = t("İçerik kataloğuna şu anda ulaşılamadı.")
                    } else {
                        results = mergeMediaResults(q, movies.getOrDefault(emptyList()), series.getOrDefault(emptyList()))
                        if (results.isEmpty()) message = t("Sonuç bulunamadı.")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } finally {
                    searching = false
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (searching) t("Aranıyor…") else "", style = RealType.detailBlockMeta, color = Color(0xFFA5A5B0))
                TextButton(onClick = onDismiss) { Text(t("Kapat"), color = Color(0xFFBCBCC5)) }
            }

            message?.let { Text(it, style = RealType.detailBlockMeta, color = Color(0xFFBCBCC5)) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(results, key = { it.id }) { media ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelect(media.copy(createdAt = System.currentTimeMillis())) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MediaArtwork(
                            media,
                            Modifier
                                .width(38.dp)
                                .height(54.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(media.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val typeLabel = if (media.type == MediaType.MOVIE) "Film" else "Dizi"
                            val meta = listOfNotNull(media.year?.toString(), typeLabel, media.genre).joinToString(" • ")
                            Text(meta, color = Color(0xFFA5A5B0), style = RealType.detailBlockMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ActionIcon(ActionGlyph.Add, Modifier.size(18.dp), Color.White)
                    }
                }
            }

            Text(t("Filmler TheTVDB • Diziler TVmaze"), style = RealType.detailAttribution, color = Color(0xFF95959F))
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Films and series in one list: titles that start with the query first, then the rest alternating film / series. */
private fun mergeMediaResults(query: String, movies: List<MediaBlock>, series: List<MediaBlock>): List<MediaBlock> {
    val q = query.lowercase()
    val alternating = buildList {
        for (i in 0 until maxOf(movies.size, series.size)) {
            movies.getOrNull(i)?.let(::add)
            series.getOrNull(i)?.let(::add)
        }
    }
    val (close, rest) = alternating.partition { it.title.lowercase().startsWith(q) }
    return (close + rest).distinctBy { it.id }.take(30)
}

private fun strongerMediaAmbience(type: MediaType, sampled: Color): Color {
    val fallback = if (type == MediaType.MOVIE) Color(0xFF7A4D3D) else Color(0xFF3D6660)
    val source = if (sampled == Color.Transparent) fallback else sampled
    val typeBias = if (type == MediaType.MOVIE) Color(0xFF8A513F) else Color(0xFF3F6D67)

    // Keep the poster-derived identity, but lift dark averages and add a very small
    // movie/TV bias so the glass surface reads as intentionally colored rather than gray.
    return Color(
        red = (source.red * 0.78f + typeBias.red * 0.22f + 0.06f).coerceIn(0f, 1f),
        green = (source.green * 0.78f + typeBias.green * 0.22f + 0.05f).coerceIn(0f, 1f),
        blue = (source.blue * 0.78f + typeBias.blue * 0.22f + 0.05f).coerceIn(0f, 1f),
        alpha = 1f
    )
}

@Composable
fun MediaContentSection(
    blocks: List<MediaBlock>,
    designStyle: DesignStyle,
    editing: Boolean,
    onRemove: (String) -> Unit,
    display: CatalogCardDisplay = CatalogCardDisplay.FULL,
    showActions: Boolean = true
) {
    blocks.forEachIndexed { index, block ->
        if (index > 0) {
            MediaTimelineConnector()
        }
        key(block.id) {
            var ambience by remember(block.artworkUrl) { mutableStateOf(Color.Transparent) }
            val visibleAmbience = remember(block.type, ambience) { strongerMediaAmbience(block.type, ambience) }
            if (display != CatalogCardDisplay.FULL) {
                CatalogCompactContent(block.title, display, 2f / 3f, editing, { onRemove(block.id) }) { modifier ->
                    MediaArtwork(block, modifier)
                }
            } else if (isDetailHero() && !editing) {
                // The card's cover: poster on top, title, year · type · genre, the story, then "…'de aç".
                val context = LocalContext.current
                val typeLabel = if (block.type == MediaType.MOVIE) "Film" else "Dizi"
                CatalogHeroCover(
                    imageUrl = block.artworkUrl, posterAspect = 2f / 3f, title = block.title,
                    subtitle = listOfNotNull(block.year?.toString(), typeLabel, block.genre).joinToString(" • "),
                    placeholder = ActionGlyph.Video, ambience = visibleAmbience, onAmbience = { ambience = it },
                    description = block.description
                ) {
                    if (showActions) HeroOpenRow(mediaOpenLabel(block), onOpen = { openMediaBlock(context, block) }) {
                        Text(mediaProviderName(block), color = Color(0xFF8E8E98), fontSize = 12.sp)
                    }
                }
            } else {
                TimelineContent(designStyle, visibleAmbience) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val label = if (block.type == MediaType.MOVIE) "Film" else "Dizi"
                        Text(label, color = Color(0xFFA5A5B0), style = RealType.detailBlockLabel, modifier = Modifier.weight(1f))
                        if (editing) {
                            IconButton(onClick = { onRemove(block.id) }, modifier = Modifier.size(40.dp)) {
                                ActionIcon(
                                    ActionGlyph.Close,
                                    Modifier.size(15.dp).semantics { contentDescription = t("İçeriği kaldır") },
                                    Color(0xFFB8B8C2)
                                )
                            }
                        }
                    }
                    MediaTrackCard(block = block, onAmbience = { ambience = it }, showActions = showActions)
                }
            }
        }
    }
}


@Composable
private fun MediaTimelineConnector() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(28.dp)
                .width(1.5.dp)
                .background(Color(0xFF38383F))
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF38383F))
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun MediaArtwork(
    block: MediaBlock,
    modifier: Modifier,
    onAmbience: ((Color) -> Unit)? = null
) {
    val context = LocalContext.current
    val request = remember(block.artworkUrl, context) {
        ImageRequest.Builder(context).data(block.artworkUrl).allowHardware(false).build()
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF26262C)),
        contentAlignment = Alignment.Center
    ) {
        ActionIcon(ActionGlyph.Video, Modifier.size(28.dp), Color(0xFFB8B8C2))
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onSuccess = { result ->
                if (onAmbience != null) {
                    val color = runCatching {
                        val bitmap = result.result.drawable.toBitmap(12, 12)
                        var red = 0f
                        var green = 0f
                        var blue = 0f
                        var weight = 0f
                        for (y in 0 until bitmap.height) {
                            for (x in 0 until bitmap.width) {
                                val pixel = bitmap.getPixel(x, y)
                                val a = android.graphics.Color.alpha(pixel) / 255f
                                red += android.graphics.Color.red(pixel) * a
                                green += android.graphics.Color.green(pixel) * a
                                blue += android.graphics.Color.blue(pixel) * a
                                weight += a
                            }
                        }
                        if (weight > 0f) Color(red / weight / 255f, green / weight / 255f, blue / weight / 255f)
                        else Color.Transparent
                    }.getOrDefault(Color.Transparent)
                    onAmbience(color)
                }
            }
        )
    }
}

@Composable
fun MediaTrackCard(
    block: MediaBlock,
    onAmbience: (Color) -> Unit = {},
    showActions: Boolean = true
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MediaArtwork(
                block = block,
                modifier = Modifier
                    .width(96.dp)
                    .height(150.dp),
                onAmbience = onAmbience
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = block.title,
                    color = Color(0xFFF1F1F4),
                    style = RealType.detailMediaTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val typeLabel = if (block.type == MediaType.MOVIE) "Film" else "Dizi"
                val subtitleText = listOfNotNull(block.year?.toString(), typeLabel, block.genre).joinToString(" • ")
                Text(
                    text = subtitleText,
                    color = Color(0xFFB1B1BC),
                    style = RealType.detailBlockMeta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!block.description.isNullOrBlank()) {
                    Text(
                        text = block.description,
                        color = Color(0xFF868690),
                        style = RealType.cardMetaPrimary,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (showActions) {
            val openButtonText = mediaOpenLabel(block)
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.07f)),
                onClick = {
                    val uri = runCatching { Uri.parse(block.providerUrl) }.getOrNull()
                    if (uri?.scheme in listOf("http", "https")) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
                            com.cagri.real.ui.components.RealToast.show(context, t("Bağlantı açılamadı"))
                        }
                    } else {
                        com.cagri.real.ui.components.RealToast.show(context, t("Bağlantı kullanılamıyor"))
                    }
                }
            ) {
                Text(openButtonText, color = Color(0xFFD0D0D8), style = RealType.detailBlockMetaSecondary)
            }
        }
    }
}

internal fun mediaProviderName(block: MediaBlock): String = when (block.provider.trim().lowercase()) {
    "tvmaze" -> "TVmaze"
    "thetvdb" -> "TheTVDB"
    "apple" -> "Apple TV"
    else -> block.provider
}

internal fun mediaOpenLabel(block: MediaBlock): String = t("{0}'de aç ↗", mediaProviderName(block))

internal fun openMediaBlock(context: android.content.Context, block: MediaBlock) {
    val uri = runCatching { Uri.parse(block.providerUrl) }.getOrNull()
    if (uri?.scheme in listOf("http", "https")) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { RealToast.show(context, t("Bağlantı açılamadı")) }
    } else RealToast.show(context, t("Bağlantı kullanılamıyor"))
}
