package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cagri.real.data.CardGameBlock
import com.cagri.real.data.CatalogCardDisplay
import com.cagri.real.discovery.GameGenres
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.theme.RealType

private val GameBlockTone = Color(0xFFF26A1B)

/** Oyun bloğu on a card: cover, name, year · genres, platforms, "Oyun sayfasını aç". */
@Composable
fun GameContentSection(
    blocks: List<CardGameBlock>,
    designStyle: DesignStyle,
    editing: Boolean,
    onRemove: (String) -> Unit,
    display: CatalogCardDisplay = CatalogCardDisplay.FULL,
    showActions: Boolean = true
) {
    val context = LocalContext.current
    blocks.forEach { block ->
        key(block.id) {
            if (display != CatalogCardDisplay.FULL) {
                CatalogCompactContent(block.title, display, 3f / 4f, editing, { onRemove(block.id) }) { modifier -> GameCover(block, modifier) }
            } else if (isDetailHero() && !editing) {
                // The card's cover: game art on top, title, year · genres, platforms, then "Oyun sayfasını aç".
                var ambience by remember(block.id) { mutableStateOf(Color.Transparent) }
                CatalogHeroCover(
                    imageUrl = block.artworkUrl?.replace("t_cover_big", "t_cover_big_2x"), posterAspect = 3f / 4f, title = block.title,
                    subtitle = listOfNotNull(block.year?.toString(), block.genres.take(2).joinToString(" · ") { GameGenres.label(it) }.takeIf { it.isNotBlank() }).joinToString(" · "),
                    placeholder = ActionGlyph.Play, ambience = ambience, onAmbience = { ambience = it },
                    description = block.platforms.take(6).joinToString(" · ")
                ) {
                    if (showActions) HeroOpenRow(t("Oyun sayfasını aç ↗"), onOpen = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(block.providerUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { RealToast.show(context, t("Oyun sayfası açılamadı")) }
                    }) { Text(block.provider, color = Color(0xFF8E8E98), fontSize = 12.sp) }
                }
            } else {
                TimelineContent(designStyle, GameBlockTone.copy(alpha = .6f)) {
                    if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { onRemove(block.id) }, modifier = Modifier.size(40.dp)) {
                            ActionIcon(ActionGlyph.Close, Modifier.size(15.dp).semantics { contentDescription = t("Oyunu kaldır") }, Color(0xFFB8B8C2))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                        GameCover(block, Modifier.width(92.dp).aspectRatio(3f / 4f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(block.title, color = Color(0xFFF1F1F4), style = RealType.detailMusicTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(block.year?.toString(), block.genres.take(2).joinToString(" · ") { GameGenres.label(it) }.takeIf { it.isNotBlank() })
                            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = Color(0xFFB1B1BC), style = RealType.detailBlockMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (block.platforms.isNotEmpty()) Text(block.platforms.take(6).joinToString(" · "), color = Color(0xFF8E8E98),
                                fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (showActions) {
                        Spacer(Modifier.height(10.dp))
                        Text(t("Oyun sayfasını aç ↗"), color = Color(0xFFD0D0D8), style = RealType.detailBlockMetaSecondary,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.07f))
                                .clickable {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(block.providerUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                        .onFailure { RealToast.show(context, t("Oyun sayfası açılamadı")) }
                                }
                                .padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text(t("Kaynak: {0}", block.provider), color = Color(0xFF6E6E78), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun GameCover(block: CardGameBlock, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF26262C)), contentAlignment = Alignment.Center) {
        ActionIcon(ActionGlyph.Play, Modifier.size(24.dp), Color(0xFFB8B8C2))
        AsyncImage(block.artworkUrl?.replace("t_cover_big", "t_cover_big_2x"), block.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Kart detayı → Oyun: search IGDB by name (REAL server) and add a game block. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GameSearchSheet(designStyle: DesignStyle, onDismiss: () -> Unit, onSelect: (CardGameBlock) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CardGameBlock>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RealSheetDefaults.Ink,
        contentColor = Color.White, scrimColor = RealSheetDefaults.Scrim, shape = RealSheetDefaults.Shape,
        dragHandle = { RealSheetDefaults.DragHandle() },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val panel = if (designStyle == DesignStyle.LiquidGlass) Modifier.liquidChrome(corners = 24.dp) else Modifier
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).then(panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Oyun"), style = RealType.detailDialogTitle)
            androidx.compose.material3.OutlinedTextField(value = query, onValueChange = { query = it.take(120) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(t("Oyun ara")) }, shape = RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF85858D),
                    unfocusedBorderColor = Color(0xFF424248), cursorColor = Color.White))
            // Searches while typing: a short pause after the last keystroke, the previous search is cancelled.
            LaunchedEffect(query) {
                val q = query.trim()
                if (q.length < 2) { results = emptyList(); message = null; searching = false; return@LaunchedEffect }
                kotlinx.coroutines.delay(400)
                searching = true; message = null
                try {
                    results = GameSearch.search(q)
                    if (results.isEmpty()) message = t("Sonuç bulunamadı. Oyunun adını farklı yazmayı dene.")
                } catch (c: kotlinx.coroutines.CancellationException) { throw c }
                catch (e: Exception) { message = e.message ?: t("Oyun araması yapılamadı.") }
                finally { searching = false }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (searching) t("Aranıyor…") else "", style = RealType.detailBlockMeta, color = Color(0xFFA5A5B0))
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text(t("Kapat"), color = Color(0xFFBCBCC5)) }
            }
            message?.let { Text(it, style = RealType.detailBlockMeta, color = Color(0xFFBCBCC5)) }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results.size, key = { results[it].providerId }) { i ->
                    val game = results[i]
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onSelect(game.copy(id = java.util.UUID.randomUUID().toString(), createdAt = System.currentTimeMillis())) }
                        .padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GameCover(game, Modifier.width(40.dp).aspectRatio(3f / 4f))
                        Column(Modifier.weight(1f)) {
                            Text(game.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(game.year?.toString(), game.platforms.take(3).joinToString(" · ").takeIf { it.isNotBlank() }).joinToString(" · "),
                                color = Color(0xFFA5A5B0), style = RealType.detailBlockMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ActionIcon(ActionGlyph.Add, Modifier.size(18.dp), Color.White)
                    }
                }
            }
            Text(t("Kaynak: IGDB"), style = RealType.detailAttribution, color = Color(0xFF95959F))
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** IGDB search through the REAL server (the IGDB key stays on the server). */
object GameSearch {
    suspend fun search(query: String): List<CardGameBlock> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val body = org.json.JSONObject().put("query", query).toString()
        val response = com.cagri.real.network.WorkerClient(com.cagri.real.platform.android.auth.AndroidBackend.workerTransport)
            .postJson("${com.cagri.real.platform.android.auth.AndroidBackend.ORIGIN}/api/game-search", body, 12_000, 20_000, readErrorBody = true)
        when (response.statusCode) {
            in 200..299 -> {}
            503 -> throw java.io.IOException(t("Oyun kataloğu şu anda hazır değil."))
            404 -> throw java.io.IOException(t("Oyun araması için sunucunun güncellenmesi gerekiyor."))
            else -> throw java.io.IOException(t("Oyun araması yapılamadı. Bağlantını kontrol edip yeniden dene."))
        }
        val array = org.json.JSONObject(response.body).optJSONArray("games") ?: return@withContext emptyList()
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(com.cagri.real.discovery.GameCodec::fromWorker) }.map { c ->
            val g = c.game
            CardGameBlock(providerId = g.providerId, title = g.title, year = g.year, genres = g.genres, platforms = g.platforms,
                artworkUrl = g.artworkUrl, description = g.description, providerUrl = g.providerUrl)
        }
    }
}
