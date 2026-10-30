package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.MediaBlock
import com.cagri.real.data.MediaCatalog
import com.cagri.real.data.MediaType
import com.cagri.real.data.MusicBlock
import com.cagri.real.data.MusicCatalog
import com.cagri.real.ui.theme.RealType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Full-screen composer "Medya" page: one search box for songs, films and series.
 * Picked items stay listed on top; results fill in under the box while typing.
 */
@Composable
internal fun ComposerMediaSearch(
    music: List<MusicBlock>,
    media: List<MediaBlock>,
    onAddMusic: (MusicBlock) -> Unit,
    onAddMedia: (MediaBlock) -> Unit,
    onRemoveMusic: (MusicBlock) -> Unit,
    onRemoveMedia: (MediaBlock) -> Unit,
    modifier: Modifier = Modifier,
    /** Games come last; null leaves them out of the search. */
    games: List<com.cagri.real.data.CardGameBlock> = emptyList(),
    onAddGame: ((com.cagri.real.data.CardGameBlock) -> Unit)? = null,
    onRemoveGame: (com.cagri.real.data.CardGameBlock) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var songs by remember { mutableStateOf<List<MusicBlock>>(emptyList()) }
    var titles by remember { mutableStateOf<List<MediaBlock>>(emptyList()) }
    var gameResults by remember { mutableStateOf<List<com.cagri.real.data.CardGameBlock>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { songs = emptyList(); titles = emptyList(); gameResults = emptyList(); message = null; searching = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(350)
        searching = true
        message = null
        try {
            val (s, m, tv) = coroutineScope {
                val a = async { runCatching { MusicCatalog.search(q) } }
                val b = async { runCatching { MediaCatalog.search(q, MediaType.MOVIE) } }
                val c = async { runCatching { MediaCatalog.search(q, MediaType.TV) } }
                val g = async { if (onAddGame != null) runCatching { GameSearch.search(q) } else Result.success(emptyList()) }
                gameResults = g.await().getOrDefault(emptyList()).take(5)
                Triple(a.await(), b.await(), c.await())
            }
            songs = s.getOrDefault(emptyList()).take(6)
            titles = mergeMediaResults(q, m.getOrDefault(emptyList()), tv.getOrDefault(emptyList())).take(8)
            message = when {
                s.isFailure && m.isFailure && tv.isFailure -> t("Arama yapılamadı. Bağlantını kontrol edip yeniden dene.")
                songs.isEmpty() && titles.isEmpty() && gameResults.isEmpty() -> t("Sonuç bulunamadı.")
                else -> null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            searching = false
        }
    }

    val block = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.055f))
    val sectionTitle: @Composable (String) -> Unit = { label ->
        Text(label, color = Color(0xFF8E8E98), style = RealType.composerStatus.copy(fontSize = 12.sp),
            modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp))
    }
    val pickedRow: @Composable (String, String?, () -> Unit) -> Unit = { title, meta, onRemove ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color(0xFFEDEDF2), style = RealType.composerStatus.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta != null) Text(meta, color = Color(0xFF8E8E98), style = RealType.composerStatus.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onRemove)
                .semantics { contentDescription = t("Kaldır") }, contentAlignment = Alignment.Center) {
                ActionIcon(ActionGlyph.Close, Modifier.size(14.dp), Color(0xFF9A9AA4))
            }
        }
    }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(block.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(ActionGlyph.Music, Modifier.size(18.dp), Color(0xFFBABAC3))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(if (onAddGame != null) t("Şarkı, film, dizi veya oyun ara") else t("Şarkı, film veya dizi ara"), color = Color(0xFF7C7C86), style = RealType.composerStatus.copy(fontSize = 15.sp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(200) },
                    singleLine = true,
                    textStyle = RealType.composerStatus.copy(fontSize = 15.sp, color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            if (searching) Text(t("Aranıyor…"), color = Color(0xFF8E8E98), style = RealType.composerStatus.copy(fontSize = 12.sp))
        }

        if (music.isNotEmpty() || media.isNotEmpty() || games.isNotEmpty()) {
            Column(block) {
                music.forEach { song -> key(song.id) { pickedRow(song.title, song.artist) { onRemoveMusic(song) } } }
                media.forEach { item ->
                    key(item.id) {
                        val type = if (item.type == MediaType.MOVIE) t("Film") else t("Dizi")
                        pickedRow(item.title, listOfNotNull(item.year?.toString(), type).joinToString(" • ")) { onRemoveMedia(item) }
                    }
                }
                games.forEach { game ->
                    key(game.id) { pickedRow(game.title, listOfNotNull(game.year?.toString(), t("Oyun")).joinToString(" • ")) { onRemoveGame(game) } }
                }
            }
        }

        message?.let { Text(it, color = Color(0xFF9A9AA4), style = RealType.composerStatus.copy(fontSize = 13.sp), modifier = Modifier.padding(horizontal = 6.dp)) }

        if (songs.isNotEmpty()) {
            sectionTitle(t("Müzik"))
            Column(block.padding(4.dp)) {
                songs.forEach { track ->
                    key(track.id) { MusicResultRow(track) { onAddMusic(track.copy(createdAt = System.currentTimeMillis())); query = "" } }
                }
            }
        }
        if (titles.isNotEmpty()) {
            sectionTitle(t("Film & Dizi"))
            Column(block.padding(4.dp)) {
                titles.forEach { item ->
                    key(item.id) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .clickable { onAddMedia(item.copy(createdAt = System.currentTimeMillis())); query = "" }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MediaArtwork(item, Modifier.width(34.dp).height(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val type = if (item.type == MediaType.MOVIE) t("Film") else t("Dizi")
                                Text(listOfNotNull(item.year?.toString(), type, item.genre).joinToString(" • "),
                                    color = Color(0xFFA5A5B0), style = RealType.detailBlockMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            ActionIcon(ActionGlyph.Add, Modifier.size(18.dp), Color.White)
                        }
                    }
                }
            }
        }
        if (gameResults.isNotEmpty() && onAddGame != null) {
            sectionTitle(t("Oyun"))
            Column(block.padding(4.dp)) {
                gameResults.forEach { game ->
                    key(game.id) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .clickable { onAddGame(game.copy(createdAt = System.currentTimeMillis())); query = "" }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GameCover(game, Modifier.width(34.dp).height(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(game.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(game.year?.toString(), game.platforms.take(3).joinToString(" · ").takeIf { it.isNotBlank() }).joinToString(" · "),
                                    color = Color(0xFFA5A5B0), style = RealType.detailBlockMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            ActionIcon(ActionGlyph.Add, Modifier.size(18.dp), Color.White)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Card detail: the same one-box search in a sheet (songs, films, series and games). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun MediaSearchAllSheet(
    music: List<MusicBlock>,
    media: List<MediaBlock>,
    games: List<com.cagri.real.data.CardGameBlock>,
    onAddMusic: (MusicBlock) -> Unit,
    onAddMedia: (MediaBlock) -> Unit,
    onAddGame: (com.cagri.real.data.CardGameBlock) -> Unit,
    onRemoveMusic: (MusicBlock) -> Unit,
    onRemoveMedia: (MediaBlock) -> Unit,
    onRemoveGame: (com.cagri.real.data.CardGameBlock) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RealSheetDefaults.Ink,
        contentColor = Color.White,
        scrimColor = RealSheetDefaults.Scrim,
        shape = RealSheetDefaults.Shape,
        dragHandle = { RealSheetDefaults.DragHandle() },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Scrolling the results must not drag the sheet away; Tamam or a tap outside closes it.
        sheetGesturesEnabled = false
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Medya"), style = RealType.detailDialogTitle, modifier = Modifier.weight(1f))
            Text(t("Tamam"), color = Color(0xFFBCBCC5), style = RealType.composerStatus.copy(fontSize = 15.sp),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDismiss).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        ComposerMediaSearch(
            music = music, media = media,
            onAddMusic = onAddMusic, onAddMedia = onAddMedia,
            onRemoveMusic = onRemoveMusic, onRemoveMedia = onRemoveMedia,
            games = games, onAddGame = onAddGame, onRemoveGame = onRemoveGame,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f).padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}
