package com.cagri.real.ui.components

import com.cagri.real.i18n.I18n
import com.cagri.real.i18n.t
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
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

private fun companionHeadlineFor(family: Int): String = when (family) {
    0 -> t("Bu ağırlığa ortak olabilecek birkaç şey")
    1 -> t("Bu yorgunluğun yanına bırakabileceğin birkaç şey")
    2 -> t("Bu sakin ana eşlik edebilecek birkaç şey")
    3 -> t("Bu iyi hissin yanına yakışacak birkaç şey")
    else -> t("Bu enerjiye ortak olabilecek birkaç şey")
}

private fun companionBadgeFor(kind: MoodSuggestionKind): String = when (kind) {
    MoodSuggestionKind.MUSIC -> t("MÜZİK · EŞLİK")
    MoodSuggestionKind.MOVIE -> t("FİLM · ORTAK")
    MoodSuggestionKind.TV -> t("DİZİ · ORTAK")
    MoodSuggestionKind.READ -> t("KART · NOT")
    MoodSuggestionKind.MESSAGE, MoodSuggestionKind.CALL -> t("KART · İFADE")
    else -> t("KART · DÜŞÜNCE")
}

private fun contextSummaryLabels(family: Int, details: MoodDetails): List<String> {
    val descMap = MoodDescriptorsV2[family].orEmpty().associate { it.key to it.label }
    val factorMap = MoodFactorGroupsV2.flatMap { it.options }.associate { it.key to it.label }
    val d = details.descriptors.mapNotNull { descMap[it] }
    val f = details.factors.mapNotNull { factorMap[it] }
    return (d + f).distinct().take(4)
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

    val family = remember(mood) { mood.familyIndexV2().coerceIn(0, 4) }
    val tone = remember(family) { MoodV3Tones[family] }
    val accent = tone.paper
    val darkInk = tone.ink
    val isEnglish = I18n.isEnglish
    val contextTags = remember(family, details, isEnglish) { contextSummaryLabels(family, details) }

    var uiState by remember { mutableStateOf<SuggestionsUiState>(SuggestionsUiState.Loading) }
    var fetchTrigger by remember { mutableIntStateOf(0) }
    var createdRealIds by remember { mutableStateOf(mapOf<Int, Long>()) }
    var pendingIndices by remember { mutableStateOf(setOf<Int>()) }

    var musicResolutions by remember { mutableStateOf(mapOf<Int, MusicResolutionState>()) }
    var mediaResolutions by remember { mutableStateOf(mapOf<Int, MediaResolutionState>()) }
    var replacementExcludes by remember { mutableStateOf(mapOf<Int, List<String>>()) }
    var autoRetriedIndices by remember { mutableStateOf(setOf<Int>()) }

    val historyContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val historyUid = com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.id
    val historyStore = remember(historyContext, historyUid) {
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

    val requestReplacement: (Int, MoodSuggestion) -> Unit = { index, targetSuggestion ->
        if (index !in pendingIndices) {
            pendingIndices = pendingIndices + index
            if (targetSuggestion.kind == MoodSuggestionKind.MUSIC) {
                musicResolutions = musicResolutions + (index to MusicResolutionState.Loading)
            } else if (targetSuggestion.kind == MoodSuggestionKind.MOVIE || targetSuggestion.kind == MoodSuggestionKind.TV) {
                mediaResolutions = mediaResolutions + (index to MediaResolutionState.Loading)
            }
            scope.launch {
                val currentSuggestions = (uiState as? SuggestionsUiState.Success)?.suggestions.orEmpty()
                val exclude = (
                    replacementExcludes[index].orEmpty() +
                        currentSuggestions.flatMap { exclusionKeys(it) } +
                        exclusionKeys(targetSuggestion)
                    ).distinct().takeLast(16)
                MoodSuggestionClient.fetchReplacement(
                    mood = mood,
                    details = details,
                    kind = targetSuggestion.kind,
                    exclude = exclude,
                    excludeMusic = historyStore?.exclusions().orEmpty(),
                    excludeMedia = historyStore?.mediaExclusions().orEmpty(),
                    excludeIdeas = historyStore?.ideaExclusions().orEmpty()
                ).fold(
                    onSuccess = { replacement ->
                        val latest = uiState as? SuggestionsUiState.Success
                        if (latest != null && index in latest.suggestions.indices) {
                            val updated = latest.suggestions.toMutableList().apply { this[index] = replacement }
                            replacementExcludes = replacementExcludes +
                                (index to (replacementExcludes[index].orEmpty() + exclusionKeys(targetSuggestion)).distinct().takeLast(16))
                            musicResolutions = musicResolutions - index
                            mediaResolutions = mediaResolutions - index
                            historyStore?.record(listOf(replacement))
                            uiState = SuggestionsUiState.Success(updated)
                        }
                    },
                    onFailure = {
                        if (targetSuggestion.kind == MoodSuggestionKind.MUSIC) {
                            musicResolutions = musicResolutions + (index to MusicResolutionState.Failed)
                        } else if (targetSuggestion.kind == MoodSuggestionKind.MOVIE || targetSuggestion.kind == MoodSuggestionKind.TV) {
                            mediaResolutions = mediaResolutions + (index to MediaResolutionState.Failed)
                        }
                    }
                )
                pendingIndices = pendingIndices - index
            }
        }
    }

    LaunchedEffect(fetchTrigger, historyUid, isEnglish) {
        uiState = SuggestionsUiState.Loading
        musicResolutions = emptyMap()
        mediaResolutions = emptyMap()
        replacementExcludes = emptyMap()
        autoRetriedIndices = emptySet()
        val result = MoodSuggestionClient.fetchSuggestions(
            mood = mood,
            details = details,
            excludeMusic = historyStore?.exclusions().orEmpty(),
            excludeMedia = historyStore?.mediaExclusions().orEmpty(),
            excludeIdeas = historyStore?.ideaExclusions().orEmpty(),
            language = if (isEnglish) "en" else "tr"
        )
        result.fold(
            onSuccess = { list ->
                historyStore?.record(list)
                uiState = SuggestionsUiState.Success(list)
            },
            onFailure = { error ->
                uiState = SuggestionsUiState.Error(error.localizedMessage ?: t("Öneriler alınamadı"))
            }
        )
    }

    // Resolve music and media candidates when suggestions load (with one automatic silent replacement on catalog miss)
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
                    if (resolvedBlock != null) {
                        musicResolutions = musicResolutions + (index to MusicResolutionState.Success(resolvedBlock))
                    } else if (index !in autoRetriedIndices) {
                        autoRetriedIndices = autoRetriedIndices + index
                        requestReplacement(index, suggestion)
                    } else {
                        musicResolutions = musicResolutions + (index to MusicResolutionState.Failed)
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
                    if (resolvedBlock != null) {
                        mediaResolutions = mediaResolutions + (index to MediaResolutionState.Success(resolvedBlock))
                    } else if (index !in autoRetriedIndices) {
                        autoRetriedIndices = autoRetriedIndices + index
                        requestReplacement(index, suggestion)
                    } else {
                        mediaResolutions = mediaResolutions + (index to MediaResolutionState.Failed)
                    }
                }
            }
        }
    }

    // Pure AMOLED Black (#000000) background with V3 Mood Contrast
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(85f)
            .background(Color.Black)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // Top Bar: V3 Mood Pill on Pure AMOLED + Done/Skip Capsule
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black)
                        .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(28.dp))
                        .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MoodV3Face(
                        params = faceParamsAtV2(family.toFloat(), mood),
                        ink = accent,
                        size = 32.dp,
                        description = MoodFamiliesV2[family].title
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = MoodFamiliesV2[family].title.uppercase(I18n.locale),
                                color = accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.4.sp
                            )
                            Text(
                                text = t("Kaydedildi"),
                                color = accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accent.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (contextTags.isNotEmpty()) {
                            Text(
                                text = contextTags.joinToString(" · "),
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                val hasAdded = createdRealIds.isNotEmpty()
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (hasAdded) accent else Color.Black)
                        .border(
                            1.dp,
                            if (hasAdded) accent else Color.White.copy(alpha = 0.20f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (hasAdded) t("Bitti · {0}", createdRealIds.size) else t("Geç"),
                        color = if (hasAdded) darkInk else Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // V3 Editorial Companion Headline
            Text(
                text = companionHeadlineFor(family),
                color = accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 28.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = t("Tavsiye değil; hissine eşlik eden kartlar, sesler ve sahneler."),
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(20.dp))

            when (val state = uiState) {
                is SuggestionsUiState.Loading -> {
                    SuggestionsLoadingView(
                        family = family,
                        mood = mood,
                        accent = accent,
                        reduceAnimations = reduceAnimations
                    )
                }

                is SuggestionsUiState.Error -> {
                    SuggestionsErrorView(
                        accent = accent,
                        darkInk = darkInk,
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
                            val isCardSeed = !isMusic && !isMedia

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

                            val cardBg by animateColorAsState(
                                targetValue = if (isAdded) accent else Color.Black,
                                animationSpec = if (reduceAnimations) snap() else tween(200),
                                label = "v3SugCardBg"
                            )
                            val cardBorder = if (isAdded) accent else accent.copy(alpha = 0.36f)
                            val titleColor = if (isAdded) darkInk else Color(0xFFF5F5F7)
                            val subColor = if (isAdded) darkInk.copy(alpha = 0.82f) else Color(0xFF9E9EA8)
                            val badgeColor = if (isAdded) darkInk else accent

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(cardBg)
                                    .border(1.2.dp, cardBorder, RoundedCornerShape(22.dp))
                                    .padding(horizontal = 16.dp, vertical = 15.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Top Row: Companion Badge + Add Pill (+ REAL)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = companionBadgeFor(suggestion.kind),
                                            color = badgeColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Swap (↻) hidden for now; "Başka getir" stays on catalog misses only.
                                            val pillShape = RoundedCornerShape(16.dp)
                                            Box(
                                                modifier = Modifier
                                                    .clip(pillShape)
                                                    .background(
                                                        when {
                                                            isAdded -> darkInk
                                                            !isActionEnabled -> Color(0xFF141418)
                                                            else -> accent
                                                        }
                                                    )
                                                    .clickable(
                                                        enabled = isActionEnabled,
                                                        role = Role.Button,
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
                                                                    val cardStackToAttach = if (isCardSeed) {
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
                                                                    // Keep current state so user can retry.
                                                                } finally {
                                                                    pendingIndices = pendingIndices - index
                                                                }
                                                            }
                                                        }
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    .semantics {
                                                        contentDescription = if (isAdded) t("Eklenen REAL’i geri al") else t("REAL olarak ekle")
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = when {
                                                        isAdded -> t("✓ REAL'de")
                                                        isPending -> "…"
                                                        else -> t("+ REAL")
                                                    },
                                                    color = when {
                                                        isAdded -> accent
                                                        !isActionEnabled -> Color(0xFF55555E)
                                                        else -> darkInk
                                                    },
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        text = suggestion.title,
                                        color = titleColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 22.sp
                                    )

                                    if (suggestion.subtitle.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = suggestion.subtitle,
                                            color = subColor,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }

                                    if (isMusic) {
                                        when (musicResolutionState) {
                                            is MusicResolutionState.Loading -> {
                                                Spacer(Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(Color.Black)
                                                        .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                                        .padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(accent.copy(alpha = 0.14f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        ActionIcon(ActionGlyph.Music, Modifier.size(20.dp), accent)
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = suggestion.music?.track?.takeIf { it.isNotBlank() } ?: t("Şarkı aranıyor…"),
                                                            color = Color(0xFFF5F5F7),
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(
                                                            text = suggestion.music?.artist?.takeIf { it.isNotBlank() } ?: t("Katalog kontrol ediliyor…"),
                                                            color = Color(0xFF9E9EA8),
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            is MusicResolutionState.Success -> {
                                                Spacer(Modifier.height(12.dp))
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
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(Color.Black)
                                                        .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        ActionIcon(ActionGlyph.Music, Modifier.size(18.dp), accent)
                                                        Text(
                                                            text = t("Şarkı şu anda bulunamadı."),
                                                            color = Color(0xFF9E9EA8),
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                    Text(
                                                        text = t("Başka getir"),
                                                        color = accent,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { requestReplacement(index, suggestion) }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            null -> {}
                                        }
                                    }

                                    if (isMedia) {
                                        when (mediaResolutionState) {
                                            is MediaResolutionState.Loading -> {
                                                val searchLabel = if (isMovie) t("Film aranıyor…") else t("Dizi aranıyor…")
                                                Spacer(Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(Color.Black)
                                                        .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                                        .padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(accent.copy(alpha = 0.14f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        ActionIcon(ActionGlyph.Video, Modifier.size(20.dp), accent)
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = suggestion.media?.title?.takeIf { it.isNotBlank() } ?: searchLabel,
                                                            color = Color(0xFFF5F5F7),
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(
                                                            text = searchLabel,
                                                            color = Color(0xFF9E9EA8),
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            is MediaResolutionState.Success -> {
                                                Spacer(Modifier.height(12.dp))
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
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(Color.Black)
                                                        .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        ActionIcon(ActionGlyph.Video, Modifier.size(18.dp), accent)
                                                        Text(
                                                            text = failText,
                                                            color = Color(0xFF9E9EA8),
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                    Text(
                                                        text = t("Başka getir"),
                                                        color = accent,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { requestReplacement(index, suggestion) }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            null -> {}
                                        }
                                    }

                                    com.cagri.real.ui.ai.AiReportButton(
                                        com.cagri.real.platform.android.ai.AiReportFeature.MOOD,
                                        suggestion.title, suggestion.subtitle
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        val hasAdded = createdRealIds.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(26.dp))
                                .background(if (hasAdded) accent else Color.Black)
                                .border(
                                    1.4.dp,
                                    if (hasAdded) accent else accent.copy(alpha = 0.50f),
                                    RoundedCornerShape(26.dp)
                                )
                                .clickable(role = Role.Button, onClick = onDismiss)
                                .padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (hasAdded) t("Tamam") else t("Şimdilik geç"),
                                color = if (hasAdded) darkInk else accent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
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
    family: Int,
    mood: JournalMood,
    accent: Color,
    reduceAnimations: Boolean
) {
    val pulse = if (reduceAnimations) {
        0.55f
    } else {
        val transition = rememberInfiniteTransition(label = "v3MoodLoadingPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.28f,
            targetValue = 0.72f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "v3PulseAlpha"
        )
        alpha
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black)
                    .border(
                        1.2.dp,
                        accent.copy(alpha = 0.32f * pulse),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 15.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.32f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(accent.copy(alpha = 0.28f * pulse))
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accent.copy(alpha = 0.22f * pulse))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.76f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.14f * pulse))
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MoodV3Face(
                params = faceParamsAtV2(family.toFloat(), mood),
                ink = accent.copy(alpha = pulse),
                size = 22.dp,
                description = ""
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = t("Hissine ortak olanlar hazırlanıyor…"),
                color = accent.copy(alpha = 0.78f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SuggestionsErrorView(
    accent: Color,
    darkInk: Color,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black)
            .border(1.2.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(22.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = t("Öneriler şu anda alınamadı"),
            color = Color(0xFFF5F5F7),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = t("Bağlantını kontrol edip tekrar deneyebilir veya doğrudan devam edebilirsin."),
            color = Color(0xFF9E9EA8),
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent)
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = t("Tekrar dene"),
                    color = darkInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = t("Geç"),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
