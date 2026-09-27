package com.cagri.bigboscagri.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cagri.bigboscagri.data.ChecklistItem
import com.cagri.bigboscagri.data.JournalMood
import com.cagri.bigboscagri.data.MoodDetails
import com.cagri.bigboscagri.data.MoodSuggestion
import com.cagri.bigboscagri.data.MoodSuggestionClient
import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.data.ReminderAttachment
import com.cagri.bigboscagri.data.ReminderLocation
import com.cagri.bigboscagri.data.ReminderVoiceRecording
import kotlinx.coroutines.launch

private sealed interface SuggestionsUiState {
    object Loading : SuggestionsUiState
    data class Success(val suggestions: List<MoodSuggestion>) : SuggestionsUiState
    data class Error(val message: String) : SuggestionsUiState
}

@Composable
fun MoodSuggestionsScreen(
    mood: JournalMood,
    details: MoodDetails,
    onDismiss: () -> Unit,
    onAddReminder: suspend (
        String,
        Long?,
        RecurrenceRule,
        Long?,
        Boolean,
        List<ChecklistItem>,
        Boolean,
        String?,
        List<ReminderAttachment>,
        List<ReminderVoiceRecording>,
        ReminderLocation?
    ) -> Long?,
    reduceAnimations: Boolean = false
) {
    BackHandler(onBack = onDismiss)

    val accent = moodV2Tint(mood)
    val topTone = remember(accent) { lerp(Color.Black, accent, 0.20f) }

    var uiState by remember { mutableStateOf<SuggestionsUiState>(SuggestionsUiState.Loading) }
    var fetchTrigger by remember { mutableIntStateOf(0) }
    var addedIndices by remember { mutableStateOf(setOf<Int>()) }
    var pendingIndices by remember { mutableStateOf(setOf<Int>()) }

    val scope = rememberCoroutineScope()
    val view = LocalView.current

    LaunchedEffect(fetchTrigger) {
        uiState = SuggestionsUiState.Loading
        val result = MoodSuggestionClient.fetchSuggestions(mood, details)
        result.fold(
            onSuccess = { list ->
                uiState = SuggestionsUiState.Success(list)
            },
            onFailure = { error ->
                uiState = SuggestionsUiState.Error(error.localizedMessage ?: "Öneriler alınamadı")
            }
        )
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
                        text = "REAL Önerileri",
                        color = Color(0xFFF4F4F6),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = if (addedIndices.isNotEmpty()) "Bitti" else "Geç",
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
                text = "Nasıl hissettiğine göre birkaç fikir",
                color = Color(0xFFE0E0E5),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 26.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Dilersen birini seçip tek dokunuşla REAL yapabilirsin.",
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
                            val isAdded = index in addedIndices
                            val isPending = index in pendingIndices

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
                                                    else -> Color(0xFF222228)
                                                }
                                            )
                                            .border(
                                                0.8.dp,
                                                when {
                                                    isAdded -> accent.copy(alpha = 0.70f)
                                                    else -> Color(0xFF383842)
                                                },
                                                buttonShape
                                            )
                                            .clickable(
                                                enabled = !isAdded && !isPending,
                                                onClick = {
                                                    scope.launch {
                                                        if (index in addedIndices || index in pendingIndices) return@launch
                                                        pendingIndices = pendingIndices + index
                                                        try {
                                                            val createdId = onAddReminder(
                                                                suggestion.title,
                                                                null,
                                                                RecurrenceRule.NONE,
                                                                null,
                                                                false,
                                                                emptyList(),
                                                                false,
                                                                null,
                                                                emptyList(),
                                                                emptyList(),
                                                                null
                                                            )
                                                            if (createdId != null) {
                                                                addedIndices = addedIndices + index
                                                                RealHaptics.success(view)
                                                            }
                                                        } catch (_: Exception) {
                                                            // restore so user can retry
                                                        } finally {
                                                            pendingIndices = pendingIndices - index
                                                        }
                                                    }
                                                }
                                            )
                                            .semantics {
                                                contentDescription = if (isAdded) "REAL oluşturuldu" else "REAL olarak ekle"
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when {
                                            isAdded -> ActionIcon(ActionGlyph.Check, Modifier.size(17.dp), color = accent)
                                            isPending -> Text("…", color = Color(0xFF8E8E98), fontSize = 16.sp)
                                            else -> ActionIcon(ActionGlyph.Add, Modifier.size(17.dp), color = Color(0xFFF1F1F4))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (addedIndices.isNotEmpty()) accent.copy(alpha = 0.22f)
                                    else Color(0xFF1A1A1E)
                                )
                                .border(
                                    0.8.dp,
                                    if (addedIndices.isNotEmpty()) accent.copy(alpha = 0.55f)
                                    else Color(0xFF2C2C33),
                                    RoundedCornerShape(18.dp)
                                )
                                .clickable(onClick = onDismiss)
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (addedIndices.isNotEmpty()) "Tamam" else "Geç",
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
            text = "Öneriler hazırlanıyor…",
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
            text = "Öneriler şu anda alınamadı",
            color = Color(0xFFF4F4F6),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Bağlantını kontrol edip tekrar deneyebilir veya doğrudan devam edebilirsin.",
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
                    text = "Tekrar dene",
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
                    text = "Geç",
                    color = Color(0xFF9898A2),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
