package com.cagri.real.ui.components

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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSearchSheet(
    designStyle: DesignStyle,
    onDismiss: () -> Unit,
    onSelect: (MediaBlock) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(MediaType.MOVIE) }
    var results by remember { mutableStateOf<List<MediaBlock>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101012),
        contentColor = Color.White,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Film & Dizi", style = RealType.detailDialogTitle)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val typeOptions = listOf(MediaType.MOVIE to "Film", MediaType.TV to "Dizi")
                    for ((type, label) in typeOptions) {
                        val selected = selectedType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                .border(
                                    0.8.dp,
                                    if (selected) Color.White.copy(alpha = 0.40f) else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    if (selectedType != type) {
                                        selectedType = type
                                        results = emptyList()
                                        message = null
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Color.White else Color(0xFF9898A2),
                                style = RealType.detailBlockMeta.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(200) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Film veya dizi ara") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF85858D),
                    unfocusedBorderColor = Color(0xFF424248),
                    cursorColor = Color.White
                )
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Kapat", color = Color(0xFFBCBCC5)) }
                TextButton(
                    enabled = query.trim().isNotEmpty() && !searching,
                    onClick = {
                        searching = true
                        message = null
                        results = emptyList()
                        scope.launch {
                            try {
                                val searchResults = MediaCatalog.search(query, selectedType)
                                results = searchResults
                                if (searchResults.isEmpty()) {
                                    message = "Sonuç bulunamadı."
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                results = emptyList()
                                message = "İçerik kataloğuna şu anda ulaşılamadı."
                            } finally {
                                searching = false
                            }
                        }
                    }
                ) {
                    Text(if (searching) "Aranıyor…" else "Ara", color = Color.White)
                }
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

            val attribution = if (selectedType == MediaType.MOVIE) "TheTVDB" else "TVmaze"
            Text(attribution, style = RealType.detailAttribution, color = Color(0xFF95959F))
            Spacer(Modifier.height(12.dp))
        }
    }
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
            } else {
                TimelineContent(designStyle, visibleAmbience) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val label = if (block.type == MediaType.MOVIE) "Film" else "Dizi"
                        Text(label, color = Color(0xFFA5A5B0), style = RealType.detailBlockLabel, modifier = Modifier.weight(1f))
                        if (editing) {
                            IconButton(onClick = { onRemove(block.id) }, modifier = Modifier.size(40.dp)) {
                                ActionIcon(
                                    ActionGlyph.Close,
                                    Modifier.size(15.dp).semantics { contentDescription = "İçeriği kaldır" },
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
            val openButtonText = when (block.provider.trim().lowercase()) {
                "tvmaze" -> "TVmaze'de aç ↗"
                "thetvdb" -> "TheTVDB'de aç ↗"
                "apple" -> "Apple TV'de aç ↗"
                else -> "${block.provider}'de aç ↗"
            }
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
                            Toast.makeText(context, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Bağlantı kullanılamıyor", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text(openButtonText, color = Color(0xFFD0D0D8), style = RealType.detailBlockMetaSecondary)
            }
        }
    }
}
