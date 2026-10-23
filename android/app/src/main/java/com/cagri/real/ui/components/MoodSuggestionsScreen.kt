package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cagri.real.data.JournalMood
import com.cagri.real.data.MoodDetails
import com.cagri.real.data.MoodSuggestion
import com.cagri.real.data.MoodSuggestionClient
import com.cagri.real.data.MoodSuggestionKind
import com.cagri.real.data.MusicBlock
import com.cagri.real.data.MediaBlock
import com.cagri.real.data.MediaCatalog
import com.cagri.real.data.MusicCandidateMatcher
import com.cagri.real.data.CardStackBlock
import com.cagri.real.data.CardStackCard
import kotlinx.coroutines.launch

private sealed interface SuggestionsUiState {
    object Loading : SuggestionsUiState
    data class Success(val suggestions: List<MoodSuggestion>) : SuggestionsUiState
    data class Error(val message: String) : SuggestionsUiState
}

private sealed interface MusicResolutionState {
    object Loading : MusicResolutionState
    data class Success(val musicBlock: MusicBlock) : MusicResolutionState
    object Failed : MusicResolutionState
}

private sealed interface MediaResolutionState {
    object Loading : MediaResolutionState
    data class Success(val mediaBlock: MediaBlock) : MediaResolutionState
    object Failed : MediaResolutionState
}

@Composable
fun MoodSuggestionsScreen(
    mood: JournalMood,
    details: MoodDetails,
    onDismiss: () -> Unit,
    onAddSuggestedReal: suspend (String, MusicBlock?, MediaBlock?, CardStackBlock?) -> Long?,
    onUndoSuggestedReal: suspend (Long) -> Boolean = { false },
    reduceAnimations: Boolean = false
) {
    BackHandler(onBack = onDismiss)

    val accent = moodV2Tint(mood)
    val topTone = remember(accent) { lerp(Color.Black, accent, 0.20f) }

    var uiState by remember { mutableStateOf<SuggestionsUiState>(SuggestionsUiState.Loading) }
    var fetchTrigger by remember { mutableIntStateOf(0) }
    var createdRealIds by remember { mutableStateOf(mapOf<Int, Long>()) }
    var pendingIndices by remember { mutableStateOf(setOf<Int>()) }

    var musicResolutions by remember { mutableStateOf(mapOf<Int, MusicResolutionState>()) }
    var mediaResolutions by remember { mutableStateOf(mapOf<Int, MediaResolutionState>()) }
    var replacementExcludes by remember { mutableStateOf(mapOf<Int, List<String>>()) }

    val historyContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val historyUid = com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.id
    val musicHistory = remember(historyContext, historyUid) {
        historyUid?.let { com.cagri.real.platform.android.ai.MoodSuggestionHistoryStore(historyContext, it) }
    }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    fun exclusionKeys(suggestion: MoodSuggestion): List<String> = buildList {
        suggestion.title.trim().takeIf { it.isNotBlank() }?.let { add(it) }
        suggestion.music?.let { music ->
            for (value in listOf(music.track, "${music.artist} ${music.track}".trim(), music.searchQuery)) {
                value.trim().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
        suggestion.media?.let { media ->
            for (value in listOf(media.title, media.searchQuery)) {
                value.trim().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }.distinct()

    val requestReplacement: (Int, MoodSuggestion) -> Unit = { index, failedSuggestion ->
        if (index !in pendingIndices) {
            pendingIndices = pendingIndices + index
            if (failedSuggestion.kind == MoodSuggestionKind.MUSIC) {
                musicResolutions = musicResolutions + (index to MusicResolutionState.Loading)
            } else if (failedSuggestion.kind == MoodSuggestionKind.MOVIE || failedSuggestion.kind == MoodSuggestionKind.TV) {
                mediaResolutions = mediaResolutions + (index to MediaResolutionState.Loading)
            }
            scope.launch {
                val currentSuggestions = (uiState as? SuggestionsUiState.Success)?.suggestions.orEmpty()
                val exclude = (
                    replacementExcludes[index].orEmpty() +
                        currentSuggestions.flatMap { exclusionKeys(it) } +
                        exclusionKeys(failedSuggestion)
                    ).distinct().takeLast(16)
                MoodSuggestionClient.fetchReplacement(
                    mood = mood,
                    details = details,
                    kind = failedSuggestion.kind,
                    exclude = exclude,
                    excludeMusic = musicHistory?.exclusions().orEmpty()
                ).fold(
                    onSuccess = { replacement ->
                        val latest = uiState as? SuggestionsUiState.Success
                        if (latest != null && index in latest.suggestions.indices) {
                            val updated = latest.suggestions.toMutableList().apply { this[index] = replacement }
                            replacementExcludes = replacementExcludes +
                                (index to (replacementExcludes[index].orEmpty() + exclusionKeys(failedSuggestion)).distinct().takeLast(16))
                            musicResolutions = musicResolutions - index
                            mediaResolutions = mediaResolutions - index
                            musicHistory?.record(listOf(replacement))
                            uiState = SuggestionsUiState.Success(updated)
                        }
                    },
                    onFailure = {
                        if (failedSuggestion.kind == MoodSuggestionKind.MUSIC) {
                            musicResolutions = musicResolutions + (index to MusicResolutionState.Failed)
                        } else if (failedSuggestion.kind == MoodSuggestionKind.MOVIE || failedSuggestion.kind == MoodSuggestionKind.TV) {
                            mediaResolutions = mediaResolutions + (index to MediaResolutionState.Failed)
                        }
                    }
                )
                pendingIndices = pendingIndices - index
            }
        }
    }

    LaunchedEffect(fetchTrigger, historyUid) {
        uiState = SuggestionsUiState.Loading
        musicResolutions = emptyMap()
        mediaResolutions = emptyMap()
        replacementExcludes = emptyMap()
        val result = MoodSuggestionClient.fetchSuggestions(mood, details,
            excludeMusic = musicHistory?.exclusions().orEmpty())
        result.fold(
            onSuccess = { list ->
                musicHistory?.record(list)
                uiState = SuggestionsUiState.Success(list)
            },
            onFailure = { error ->
                uiState = SuggestionsUiState.Error(error.localizedMessage ?: t("Öneriler alınamadı"))
            }
        )
    }

    // Resolve music and media candidates when suggestions load
    val currentUiState = uiState
    if (currentUiState is SuggestionsUiState.Success) {
        currentUiState.suggestions.forEachIndexed { index, suggestion ->
            if (suggestion.kind == MoodSuggestionKind.MUSIC) {
                val candidate = suggestion.music
                LaunchedEffect(index, candidate) {
                    if (candidate == null) {
                        musicResolutions = musicResolutions + (index to MusicResolutionState.Failed)
                        return@LaunchedEffect
                    }
                    if (musicResolutions[index] is MusicResolutionState.Success) return@LaunchedEffect
                    musicResolutions = musicResolutions + (index to MusicResolutionState.Loading)
                    val resolvedBlock = MusicCandidateMatcher.resolve(candidate)
                    musicResolutions = if (resolvedBlock != null) {
                        musicResolutions + (index to MusicResolutionState.Success(resolvedBlock))
                    } else {
                        musicResolutions + (index to MusicResolutionState.Failed)
                    }
                }
            } else if (suggestion.kind == MoodSuggestionKind.MOVIE || suggestion.kind == MoodSuggestionKind.TV) {
                val candidate = suggestion.media
                LaunchedEffect(index, candidate) {
                    if (candidate == null) {
                        mediaResolutions = mediaResolutions + (index to MediaResolutionState.Failed)
                        return@LaunchedEffect
                    }
                    if (mediaResolutions[index] is MediaResolutionState.Success) return@LaunchedEffect
                    mediaResolutions = mediaResolutions + (index to MediaResolutionState.Loading)
                    val resolvedBlock = if (suggestion.kind == MoodSuggestionKind.MOVIE) {
                        MediaCatalog.resolveMovie(candidate)
                    } else {
                        MediaCatalog.resolveTv(candidate)
                    }
                    mediaResolutions = if (resolvedBlock != null) {
                        mediaResolutions + (index to MediaResolutionState.Success(resolvedBlock))
                    } else {
                        mediaResolutions + (index to MediaResolutionState.Failed)
                    }
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(85f)
            .background(
                Brush.verticalGradient(
                    colors = listOf(topTone, Color(0xFF08090C), Color.Black)
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            // Header Bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MoodV2Face(mood = mood, size = 32.dp, tint = accent)
                    Text(
                        text = t("REAL Önerileri"),
                        color = Color(0xFFF4F4F6),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = if (createdRealIds.isNotEmpty()) t("Bitti") else t("Geç"),
                    color = Color(0xFF9898A2),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = t("Nasıl hissettiğine göre birkaç fikir"),
                color = Color(0xFFE0E0E5),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 26.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = t("Dilersen birini seçip tek dokunuşla REAL yapabilirsin."),
                color = Color(0xFF85858E),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(24.dp))

            when (val state = uiState) {
                is SuggestionsUiState.Loading -> {
                    SuggestionsLoadingView(accent = accent, reduceAnimations = reduceAnimations)
                }

                is SuggestionsUiState.Error -> {
                    SuggestionsErrorView(
                        accent = accent,
                        onRetry = { fetchTrigger++ },
                        onDismiss = onDismiss
                    )
                }

                is SuggestionsUiState.Success -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.suggestions.forEachIndexed { index, suggestion ->
                            val createdRealId = createdRealIds[index]
                            val isAdded = createdRealId != null
                            val isPending = index in pendingIndices
                            val isMusic = suggestion.kind == MoodSuggestionKind.MUSIC
                            val isMovie = suggestion.kind == MoodSuggestionKind.MOVIE
                            val isTv = suggestion.kind == MoodSuggestionKind.TV
                            val isMedia = isMovie || isTv

                            val musicResolutionState = if (isMusic) {
                                musicResolutions[index] ?: if (suggestion.music != null) MusicResolutionState.Loading else MusicResolutionState.Failed
                            } else null
                            val verifiedMusicBlock = (musicResolutionState as? MusicResolutionState.Success)?.musicBlock

                            val mediaResolutionState = if (isMedia) {
                                mediaResolutions[index] ?: if (suggestion.media != null) MediaResolutionState.Loading else MediaResolutionState.Failed
                            } else null
                            val verifiedMediaBlock = (mediaResolutionState as? MediaResolutionState.Success)?.mediaBlock

                            val isActionEnabled = !isPending && (
                                isAdded || (
                                    (!isMusic || verifiedMusicBlock != null) &&
                                        (!isMedia || verifiedMediaBlock != null)
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                accent.copy(alpha = if (isAdded) 0.22f else 0.14f),
                                                Color(0xFF141417).copy(alpha = 0.98f),
                                                Color(0xFF0F0F12).copy(alpha = 0.98f)
                                            )
                                        )
                                    )
                                    .border(
                                        0.8.dp,
                                        if (isAdded) accent.copy(alpha = 0.65f) else accent.copy(alpha = 0.28f),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 18.dp, vertical = 16.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 14.dp)
                                        ) {
                                            Text(
                                                text = suggestion.title,
                                                color = Color(0xFFF4F4F6),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 21.sp
                                            )
                                            if (suggestion.subtitle.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = suggestion.subtitle,
                                                    color = Color(0xFF9898A2),
                                                    fontSize = 13.sp,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }

                                        val buttonShape = RoundedCornerShape(13.dp)
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(buttonShape)
                                                .background(
                                                    when {
                                                        isAdded -> accent.copy(alpha = 0.25f)
                                                        isPending -> Color(0xFF202026)
                                                        !isActionEnabled -> Color(0xFF19191D)
                                                        else -> Color(0xFF222228)
                                                    }
                                                )
                                                .border(
                                                    0.8.dp,
                                                    when {
                                                        isAdded -> accent.copy(alpha = 0.70f)
                                                        !isActionEnabled -> Color(0xFF2B2B33)
                                                        else -> Color(0xFF383842)
                                                    },
                                                    buttonShape
                                                )
                                                .clickable(
                                                    enabled = isActionEnabled,
                                                    onClick = {
                                                        scope.launch {
                                                            if (index in pendingIndices) return@launch
                                                            pendingIndices = pendingIndices + index
                                                            try {
                                                                val existingId = createdRealIds[index]
                                                                if (existingId != null) {
                                                                    if (onUndoSuggestedReal(existingId)) {
                                                                        createdRealIds = createdRealIds - index
                                                                        RealHaptics.tick(view)
                                                                    }
                                                                    return@launch
                                                                }

                                                                val musicToAttach = if (isMusic) {
                                                                    verifiedMusicBlock ?: return@launch
                                                                } else null
                                                                val mediaToAttach = if (isMedia) {
                                                                    verifiedMediaBlock ?: return@launch
                                                                } else null
                                                                val cardStackToAttach = if (suggestion.kind == MoodSuggestionKind.TEXT) {
                                                                    CardStackBlock(
                                                                        prompt = suggestion.subtitle.trim().takeIf { it.isNotBlank() },
                                                                        cards = listOf(CardStackCard())
                                                                    )
                                                                } else null

                                                                val createdId = onAddSuggestedReal(
                                                                    suggestion.title,
                                                                    musicToAttach,
                                                                    mediaToAttach,
                                                                    cardStackToAttach
                                                                )
                                                                if (createdId != null) {
                                                                    createdRealIds = createdRealIds + (index to createdId)
                                                                    RealHaptics.success(view)
                                                                }
                                                            } catch (_: Exception) {
                                                                // Keep the current state so the user can retry add/undo.
                                                            } finally {
                                                                pendingIndices = pendingIndices - index
                                                            }
                                                        }
                                                    }
                                                )
                                                .semantics {
                                                    contentDescription = if (isAdded) t("Eklenen REAL’i geri al") else t("REAL olarak ekle")
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when {
                                                isAdded -> ActionIcon(ActionGlyph.Check, Modifier.size(17.dp), color = accent)
                                                isPending -> Text("…", color = Color(0xFF8E8E98), fontSize = 16.sp)
                                                !isActionEnabled -> ActionIcon(ActionGlyph.Add, Modifier.size(17.dp), color = Color(0xFF55555E))
                                                else -> ActionIcon(ActionGlyph.Add, Modifier.size(17.dp), color = Color(0xFFF1F1F4))
                                            }
                                        }
                                    }

                                    if (isMusic) {
                                        when (musicResolutionState) {
                                            is MusicResolutionState.Loading -> {
                                                Spacer(Modifier.height(14.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(Color(0xFF16161A))
                                                        .border(0.8.dp, Color(0xFF282830), RoundedCornerShape(14.dp))
                                                        .padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(Color.White.copy(alpha = 0.08f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        ActionIcon(ActionGlyph.Music, Modifier.size(22.dp), Color(0xFF85858E))
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = suggestion.music?.track?.takeIf { it.isNotBlank() } ?: t("Şarkı aranıyor…"),
                                                            color = Color(0xFFE0E0E5),
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(
                                                            text = suggestion.music?.artist?.takeIf { it.isNotBlank() }?.let { t("{0} • iTunes aranıyor…", it) } ?: t("Şarkı aranıyor…"),
                                                            color = Color(0xFF85858E),
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            is MusicResolutionState.Success -> {
                                                Spacer(Modifier.height(14.dp))
                                                MusicTrackCard(
                                                    block = verifiedMusicBlock!!,
                                                    selected = true
                                                )
                                            }

                                            is MusicResolutionState.Failed -> {
                                                Spacer(Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(Color(0xFF16161A))
                                                        .border(0.8.dp, Color(0xFF282830), RoundedCornerShape(14.dp))
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        ActionIcon(ActionGlyph.Music, Modifier.size(20.dp), Color(0xFF85858E))
                                                        Text(
                                                            text = t("Şarkı şu anda bulunamadı."),
                                                            color = Color(0xFF85858E),
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                    if (suggestion.music != null) {
                                                        Text(
                                                            text = t("Tekrar dene"),
                                                            color = accent,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable { requestReplacement(index, suggestion) }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            null -> {}
                                        }
                                    }

                                    if (isMedia) {
                                        when (mediaResolutionState) {
                                            is MediaResolutionState.Loading -> {
                                                val searchLabel = if (isMovie) t("Film aranıyor…") else t("Dizi aranıyor…")
                                                val providerLabel = if (isMovie) t("Film • TheTVDB aranıyor…") else t("Dizi • TVmaze aranıyor…")
                                                Spacer(Modifier.height(14.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(Color(0xFF16161A))
                                                        .border(0.8.dp, Color(0xFF282830), RoundedCornerShape(14.dp))
                                                        .padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(Color.White.copy(alpha = 0.08f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        ActionIcon(ActionGlyph.Video, Modifier.size(22.dp), Color(0xFF85858E))
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = suggestion.media?.title?.takeIf { it.isNotBlank() } ?: searchLabel,
                                                            color = Color(0xFFE0E0E5),
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(
                                                            text = providerLabel,
                                                            color = Color(0xFF85858E),
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            is MediaResolutionState.Success -> {
                                                Spacer(Modifier.height(14.dp))
                                                MediaTrackCard(
                                                    block = verifiedMediaBlock!!
                                                )
                                            }

                                            is MediaResolutionState.Failed -> {
                                                val failText = if (isMovie) t("Film şu anda bulunamadı.") else t("Dizi şu anda bulunamadı.")
                                                Spacer(Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(Color(0xFF16161A))
                                                        .border(0.8.dp, Color(0xFF282830), RoundedCornerShape(14.dp))
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        ActionIcon(ActionGlyph.Video, Modifier.size(20.dp), Color(0xFF85858E))
                                                        Text(
                                                            text = failText,
                                                            color = Color(0xFF85858E),
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                    if (suggestion.media != null) {
                                                        Text(
                                                            text = t("Tekrar dene"),
                                                            color = accent,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable { requestReplacement(index, suggestion) }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            null -> {}
                                        }
                                    }
                                    com.cagri.real.ui.ai.AiReportButton(
                                        com.cagri.real.platform.android.ai.AiReportFeature.MOOD,
                                        suggestion.title, suggestion.subtitle)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (createdRealIds.isNotEmpty()) accent.copy(alpha = 0.22f)
                                    else Color(0xFF1A1A1E)
                                )
                                .border(
                                    0.8.dp,
                                    if (createdRealIds.isNotEmpty()) accent.copy(alpha = 0.55f)
                                    else Color(0xFF2C2C33),
                                    RoundedCornerShape(18.dp)
                                )
                                .clickable(onClick = onDismiss)
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (createdRealIds.isNotEmpty()) t("Tamam") else t("Geç"),
                                color = Color(0xFFF4F4F6),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SuggestionsLoadingView(
    accent: Color,
    reduceAnimations: Boolean
) {
    val alphaAnim = if (reduceAnimations) {
        0.45f
    } else {
        val transition = rememberInfiniteTransition(label = "skeletonPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.30f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(850),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeletonAlpha"
        )
        alpha
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.08f * (alphaAnim / 0.5f)),
                                Color(0xFF141417).copy(alpha = 0.95f),
                                Color(0xFF0F0F12).copy(alpha = 0.95f)
                            )
                        )
                    )
                    .border(
                        0.8.dp,
                        accent.copy(alpha = 0.20f * (alphaAnim / 0.5f)),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.70f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.12f * (alphaAnim / 0.5f)))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color.White.copy(alpha = 0.07f * (alphaAnim / 0.5f)))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color.White.copy(alpha = 0.06f * (alphaAnim / 0.5f)))
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = t("Öneriler hazırlanıyor…"),
            color = Color(0xFF85858E),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SuggestionsErrorView(
    accent: Color,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF141417))
            .border(0.8.dp, Color(0xFF28282E), RoundedCornerShape(20.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = t("Öneriler şu anda alınamadı"),
            color = Color(0xFFF4F4F6),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = t("Bağlantını kontrol edip tekrar deneyebilir veya doğrudan devam edebilirsin."),
            color = Color(0xFF85858E),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.22f))
                    .border(0.8.dp, accent.copy(alpha = 0.60f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = t("Tekrar dene"),
                    color = Color(0xFFF4F4F6),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E23))
                    .border(0.8.dp, Color(0xFF33333A), RoundedCornerShape(16.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = t("Geç"),
                    color = Color(0xFF9898A2),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
