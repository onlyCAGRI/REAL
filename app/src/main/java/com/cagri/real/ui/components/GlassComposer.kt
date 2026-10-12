package com.cagri.real.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import com.cagri.real.data.ReminderAttachment

import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.interaction.MutableInteractionSource
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.cagri.real.speech.TimeOfDay
import com.cagri.real.speech.formatPendingScheduleLabel
import com.cagri.real.speech.formatComposerScheduleLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.cagri.real.speech.VoiceTranscriptFormatter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.speech.VoicePhase
import com.cagri.real.ui.theme.RealType
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cagri.real.data.CustomCategory
import com.cagri.real.ui.DesignStyle
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassComposer(
    value: String,
    alertAt: Long? = null,
    repeatRule: RecurrenceRule = RecurrenceRule.NONE,
    requiresTimeSelection: Boolean = false,
    dateHint: LocalDate? = null,
    timeOfDay: TimeOfDay? = null,
    saving: Boolean = false,
    onPickDate: () -> Unit = {},
    onClearDate: () -> Unit = {},
    onApplyDateSuggestion: ((isToday: Boolean) -> Unit)? = null,
    customCategories: List<CustomCategory> = emptyList(),
    selectedCategoryId: Long? = null,
    onPickCategory: () -> Unit = {},
    onPickAttachment: () -> Unit = {},
    draftAttachments: List<ReminderAttachment> = emptyList(),
    onRemoveDraftAttachment: (Int) -> Unit = {},
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    expanded: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    voicePhase: VoicePhase,
    voiceLevel: Float,
    isAiProcessing: Boolean = false,
    onMicClick: () -> Unit,
    showCompactMicrophone: Boolean = true,
    showMicrophoneWhenExpanded: Boolean = true,
    reduceDynamicBlur: Boolean = false,
    reduceAnimations: Boolean = false,
    designStyle: DesignStyle = DesignStyle.Glass,
    isChecklistMode: Boolean = false,
    onToggleChecklistMode: () -> Unit = {},
    checklistDraftItems: List<String> = emptyList(),
    onChecklistItemChange: (Int, String) -> Unit = { _, _ -> },
    onAddChecklistItem: () -> Unit = {},
    onRemoveChecklistItem: (Int) -> Unit = {},
    sendImmediateNotification: Boolean = false,
    onToggleImmediateNotification: () -> Unit = {},
    voiceCommittedText: String = "",
    voicePartialText: String = "",
    onVoiceLineCountChange: (Int) -> Unit = {},
    textLineCount: Int = 1,
    onTextLineCountChange: (Int) -> Unit = {},
    isVoiceDraft: Boolean = false,
    voiceDurationMinutes: Int? = null,
    composerDurationMinutes: Int? = null,
    onComposerDurationMinutesChange: (Int?) -> Unit = {},
    voicePeople: List<String> = emptyList(),
    voiceLocationName: String? = null,
    isShareDraft: Boolean = false,
    isHomeReady: Boolean = true,
    isLabsOpen: Boolean = false,
    labsProgress: Float = 0f,
    isWhatsAppArmed: Boolean = false,
    onToggleWhatsApp: () -> Unit = {},
    hasSavedRecipient: Boolean = false,
    onConfigureRecipient: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val corners by animateDpAsState(
        if (expanded) 26.dp else 30.dp,
        if (reduceAnimations) snap() else RealMotion.smooth(0.28f),
        label = "glassCorners"
    )
    // Expanded panel hosts text: same clear dock glass, plus a dim so text stays readable over pastel cards.
    val expandedDim by animateFloatAsState(
        if (expanded) 0.26f else 0f,
        if (reduceAnimations) snap() else tween(220, easing = FastOutSlowInEasing),
        label = "composerDim"
    )
    val textInset by animateDpAsState(
        if (expanded) 14.dp else 0.dp,
        if (reduceAnimations) snap() else RealMotion.smooth(0.28f),
        label = "textInset"
    )
    val listening = voicePhase == VoicePhase.Listening
    val recording = voicePhase != VoicePhase.Idle
    val processing = voicePhase == VoicePhase.Finishing || isAiProcessing
    DisposableEffect(listening) {
        RealPetRuntime.setComposerMicActive(listening)
        onDispose {
            if (listening) RealPetRuntime.setComposerMicActive(false)
        }
    }
    val micColor by animateColorAsState(
        if (recording || isAiProcessing) Color(0xFF69ACFF) else Color(0xFFE4E4EA),
        if (reduceAnimations) snap() else tween(180),
        label = "micColor"
    )
    val micScale by animateFloatAsState(
        if (voicePhase == VoicePhase.Listening && !reduceAnimations) {
            1f + (voiceLevel * 0.05f).coerceIn(0f, 0.05f)
        } else {
            1f
        },
        if (reduceAnimations) snap() else tween(120),
        label = "micLevel"
    )

    val keyboard = LocalSoftwareKeyboardController.current
    val itemFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    fun getItemFocusRequester(index: Int): FocusRequester =
        itemFocusRequesters.getOrPut(index) { FocusRequester() }

    var pendingFocusIndex by remember { mutableStateOf<Int?>(null) }
    var isComposerFocused by remember { mutableStateOf(false) }
    val submitInteractionSource = remember { MutableInteractionSource() }
    val micInteractionSource = remember { MutableInteractionSource() }
    val petMicKick = remember { Animatable(0f) }
    val aiPulse = rememberComposerAiPulse(processing, reduceAnimations)

    val voiceArrivalProgress = rememberComposerVoiceArrival(isVoiceDraft, reduceAnimations)

    val density = LocalDensity.current
    val voiceArrivalModifier = if (isVoiceDraft && !reduceAnimations) {
        Modifier.graphicsLayer {
            alpha = voiceArrivalProgress.value
            translationY = (1f - voiceArrivalProgress.value) * density.run { 5.dp.toPx() }
        }
    } else Modifier

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val petPrank = RealPetRuntime.prank
    val latestComposerValue = rememberUpdatedState(value)
    val latestOnValueChange = rememberUpdatedState(onValueChange)

    LaunchedEffect(petPrank?.token) {
        val prank = petPrank ?: return@LaunchedEffect
        if (prank.perchId != REAL_PET_PERCH_COMPOSER || prank.type != RealPetPrankType.ComposerTypedMeow) {
            return@LaunchedEffect
        }

        // This prank is real text: append to the user's current draft and keep a local
        // working copy while animating the characters. Relying on recomposition between
        // characters could repeatedly overwrite the same stale value.
        if (isChecklistMode || recording || saving || latestComposerValue.value.isBlank()) {
            RealPetRuntime.consumePrank(prank.token)
            return@LaunchedEffect
        }

        val target = listOf(" meaw", " meaww", " meawww", " meaw meaww")[prank.token % 4]
        var draft = latestComposerValue.value
        target.forEach { ch ->
            draft += ch
            latestOnValueChange.value(draft)
            delay(if (reduceAnimations) 22L else 82L)
        }
        RealPetRuntime.consumePrank(prank.token)
    }

    LaunchedEffect(petPrank?.token, reduceAnimations) {
        val prank = petPrank ?: return@LaunchedEffect
        if (prank.perchId != REAL_PET_PERCH_COMPOSER_MIC || prank.type != RealPetPrankType.MicNudge) {
            return@LaunchedEffect
        }
        RealPetRuntime.consumePrank(prank.token)
        if (saving) return@LaunchedEffect
        if (reduceAnimations) {
            petMicKick.snapTo(0.14f)
            delay(180L)
            petMicKick.snapTo(0f)
        } else {
            petMicKick.snapTo(0f)
            petMicKick.animateTo(1f, tween(110, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
            petMicKick.animateTo(-0.65f, tween(120))
            petMicKick.animateTo(0.35f, tween(95))
            petMicKick.animateTo(0f, tween(140))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            RealPetRuntime.clearComposerTyping()
        }
    }

    val shouldShimmer = isResumed && isHomeReady && !reduceAnimations && !expanded && !isComposerFocused && !isChecklistMode && !recording && value.isEmpty()
    val shimmerProgress = rememberComposerShimmer(shouldShimmer)

    LaunchedEffect(isChecklistMode) {
        if (isChecklistMode) {
            delay(60)
            try {
                if (value.isBlank()) {
                    focusRequester.requestFocus()
                } else {
                    getItemFocusRequester(0).requestFocus()
                }
                keyboard?.show()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(pendingFocusIndex, checklistDraftItems.size) {
        val target = pendingFocusIndex
        if (target != null && target in checklistDraftItems.indices) {
            delay(40)
            try {
                getItemFocusRequester(target).requestFocus()
                keyboard?.show()
            } catch (_: Exception) {}
            pendingFocusIndex = null
        }
    }

    DisposableEffect(isChecklistMode) {
        onDispose {
            itemFocusRequesters.clear()
            pendingFocusIndex = null
        }
    }

    val handleChecklistEnter: (Int) -> Unit = { index ->
        val currentText = checklistDraftItems.getOrElse(index) { "" }
        if (currentText.isNotBlank()) {
            if (index == checklistDraftItems.lastIndex) {
                onAddChecklistItem()
                pendingFocusIndex = index + 1
            } else {
                pendingFocusIndex = index + 1
            }
        } else {
            if (index < checklistDraftItems.lastIndex) {
                pendingFocusIndex = index + 1
            }
        }
    }

    val voiceScrollState = rememberScrollState()
    val textScrollState = rememberScrollState()

    LaunchedEffect(value, expanded) {
        if (expanded && !isChecklistMode && textLineCount > 4) {
            textScrollState.scrollTo(textScrollState.maxValue)
        }
    }

    val (effectiveCommitted, effectivePartial) = remember(value, voiceCommittedText, voicePartialText) {
        VoiceTranscriptFormatter.splitCommittedAndPartial(value, voiceCommittedText, voicePartialText)
    }

    val styledVoiceText = remember(value, effectiveCommitted, effectivePartial) {
        if (value.isEmpty()) {
            AnnotatedString("")
        } else {
            buildAnnotatedString {
                append(value)
                if (effectiveCommitted.isNotEmpty() && effectivePartial.isNotEmpty()) {
                    val committedLen = effectiveCommitted.length.coerceAtMost(value.length)
                    addStyle(
                        SpanStyle(color = Color(0xFFF7F7F8)),
                        start = 0,
                        end = committedLen
                    )
                    val partialStart = value.lastIndexOf(effectivePartial).takeIf { it >= 0 } ?: committedLen
                    addStyle(
                        SpanStyle(color = Color(0xFF91B9FF)),
                        start = partialStart,
                        end = value.length
                    )
                } else if (effectivePartial.isNotEmpty()) {
                    addStyle(
                        SpanStyle(color = Color(0xFF91B9FF)),
                        start = 0,
                        end = value.length
                    )
                } else {
                    addStyle(
                        SpanStyle(color = Color(0xFFF7F7F8)),
                        start = 0,
                        end = value.length
                    )
                }
            }
        }
    }

    LaunchedEffect(value, recording) {
        if (recording && value.isNotEmpty()) {
            voiceScrollState.scrollTo(voiceScrollState.maxValue)
        }
    }

    val panelContent: @Composable BoxScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(expanded) {
                    // The pull-up "Labs" panel is a future feature; no vertical pull gestures for now.
                    if (!expanded) {
                        detectTapGestures {  }
                    }
                }
                .padding(
                    top = if (expanded) 16.dp else textInset,
                    bottom = if (expanded) 6.dp else 0.dp
                )
        ) {
            val inputRowModifier = if (recording) {
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 9.dp)
                    .weight(1f)
            } else if (expanded && !isChecklistMode) {
                val extraLines = (textLineCount - 1).coerceIn(0, 3)
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 9.dp)
                    .height((48 + extraLines * 27).dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 9.dp)
                    .height(if (expanded) 48.dp else 54.dp)
            }
            Row(
                modifier = inputRowModifier,
                verticalAlignment = if (recording) Alignment.Bottom else if (expanded && !isChecklistMode) Alignment.Bottom else Alignment.CenterVertically
            ) {
                if (recording) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp, top = 6.dp, bottom = 6.dp)
                            .fillMaxHeight()
                            .verticalScroll(voiceScrollState),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                when {
                                    processing -> "REAL düşünüyor…"
                                    voicePhase == VoicePhase.Listening -> "Dinleniyor…"
                                    else -> RealPetRuntime.placeholderOverride ?: "Aklında ne var?"
                                },
                                color = Color(0xFFA2C7FF),
                                style = RealType.composerVoicePrompt
                            )
                        } else {
                            Text(
                                text = styledVoiceText,
                                style = RealType.composerInput,
                                onTextLayout = { textLayoutResult ->
                                    onVoiceLineCountChange(textLayoutResult.lineCount)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                            .realPetPerch(REAL_PET_PERCH_COMPOSER_TEXT)
                            .then(
                                if (expanded && !isChecklistMode) {
                                    Modifier
                                        .fillMaxHeight()
                                        .verticalScroll(textScrollState)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = if (expanded && !isChecklistMode && textLineCount > 1) Alignment.BottomStart else Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = value,
                            onValueChange = { changed ->
                                val cleaned = changed.replace("\r", "").replace("\n", " ")
                                if (cleaned != value && !isChecklistMode) RealPetRuntime.noteComposerTyping()
                                onValueChange(cleaned)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(voiceArrivalModifier)
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isComposerFocused = focusState.isFocused
                                    if (!focusState.isFocused) RealPetRuntime.clearComposerTyping()
                                    onFocusChanged(focusState.isFocused)
                                }
                                .semantics { contentDescription = if (isChecklistMode) "Başlık" else "Hatırlatıcı metni" },
                            readOnly = saving,
                            singleLine = isChecklistMode,
                            visualTransformation = VisualTransformation.None,
                            textStyle = (if (isChecklistMode) RealType.composerInputEmphasized else RealType.composerInput).copy(
                                color = Color(0xFFF7F7F8)
                            ),
                            cursorBrush = SolidColor(Color(0xFF9AB9FF)),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = if (isChecklistMode) ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { pendingFocusIndex = 0 },
                                onDone = { onSubmit() }
                            ),
                            onTextLayout = { textLayoutResult ->
                                if (!isChecklistMode) {
                                    onTextLineCountChange(textLayoutResult.lineCount)
                                }
                            },
                            decorationBox = { field ->
                                Box(
                                    Modifier.padding(vertical = if (expanded && !isChecklistMode) 12.dp else 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (value.isEmpty()) {
                                        if (isChecklistMode) {
                                            Text(
                                                text = "Başlık",
                                                color = Color(0xFFB8B8BF),
                                                style = RealType.composerPrompt
                                            )
                                        } else {
                                            var placeholderTextWidthPx by remember { mutableFloatStateOf(0f) }

                                            
                                            Text(
                                                text = RealPetRuntime.placeholderOverride ?: if (expanded) "Aklında ne var?" else "Add to REAL",
                                                color = Color(0xFF8E8E96),
                                                style = RealType.composerPrompt,
                                                onTextLayout = { layoutResult ->
                                                    placeholderTextWidthPx = layoutResult.size.width.toFloat()
                                                }
                                            )

                                            
                                            if (shouldShimmer) {
                                                Text(
                                                    text = RealPetRuntime.placeholderOverride ?: if (expanded) "Aklında ne var?" else "Add to REAL",
                                                    color = Color.White,
                                                    style = RealType.composerPrompt,
                                                    modifier = Modifier
                                                        .graphicsLayer {
                                                            val p = shimmerProgress.value
                                                            if (p > 0.001f && p < 0.999f) {
                                                                compositingStrategy = CompositingStrategy.Offscreen
                                                                alpha = 1f
                                                            } else {
                                                                compositingStrategy = CompositingStrategy.Auto
                                                                alpha = 0f
                                                            }
                                                        }
                                                        .drawWithContent {
                                                            val p = shimmerProgress.value
                                                            if (p > 0.001f && p < 0.999f) {
                                                                drawContent()
                                                                val textWidth = if (placeholderTextWidthPx > 0f) placeholderTextWidthPx else size.width
                                                                val bandWidth = textWidth * 0.65f
                                                                val startX = -bandWidth + p * (textWidth + bandWidth)
                                                                val endX = startX + bandWidth
                                                                val maskBrush = Brush.linearGradient(
                                                                    0.00f to Color.Transparent,
                                                                    0.18f to Color.White.copy(alpha = 0.20f),
                                                                    0.35f to Color.White.copy(alpha = 0.55f),
                                                                    0.50f to Color.White.copy(alpha = 0.82f),
                                                                    0.65f to Color.White.copy(alpha = 0.55f),
                                                                    0.82f to Color.White.copy(alpha = 0.20f),
                                                                    1.00f to Color.Transparent,
                                                                    start = Offset(startX, 0f),
                                                                    end = Offset(endX, 0f)
                                                                )
                                                                drawRect(
                                                                    brush = maskBrush,
                                                                    blendMode = BlendMode.DstIn
                                                                )
                                                            }
                                                        }
                                                )
                                            }
                                        }
                                    }
                                    field()
                                }
                            }
                        )
                    }
                }

                if (isChecklistMode) {
                    val canSubmit = value.isNotBlank() && checklistDraftItems.any { it.isNotBlank() }
                    IconButton(
                        onClick = {
                            onSubmit()
                        },
                        interactionSource = submitInteractionSource,
                        enabled = canSubmit && !saving,
                        modifier = Modifier.size(36.dp).semantics { contentDescription = "Listeyi kaydet" }
                    ) {
                        Box(
                            Modifier
                                .pressScale(submitInteractionSource, enabled = canSubmit && !saving, targetScale = 0.95f, reduceAnimations = reduceAnimations)
                                .size(30.dp)
                                .background(
                                    if (canSubmit) Color(0xFF295799) else Color(0xFF1E1E24),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            ActionIcon(
                                ActionGlyph.Check,
                                Modifier.size(15.dp),
                                if (canSubmit) Color.White else Color(0xFF6B6B74)
                            )
                        }
                    }
                } else if ((isVoiceDraft || isShareDraft) && !recording) {
                    val canSubmit = value.isNotBlank()
                    IconButton(
                        onClick = {
                            onSubmit()
                        },
                        interactionSource = submitInteractionSource,
                        enabled = canSubmit && !saving,
                        modifier = Modifier.size(36.dp)
                            .then(if (expanded && !isChecklistMode) Modifier.padding(bottom = 6.dp) else Modifier)
                            .semantics { contentDescription = "Hatırlatıcıyı kaydet" }
                    ) {
                        Box(
                            Modifier
                                .pressScale(submitInteractionSource, enabled = canSubmit && !saving, targetScale = 0.95f, reduceAnimations = reduceAnimations)
                                .size(30.dp)
                                .background(
                                    if (canSubmit) Color(0xFF295799) else Color(0xFF1E1E24),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            ActionIcon(
                                ActionGlyph.Check,
                                Modifier.size(15.dp),
                                if (canSubmit) Color.White else Color(0xFF6B6B74)
                            )
                        }
                    }
                }

                if ((expanded && showMicrophoneWhenExpanded) || showCompactMicrophone) {
                IconButton(
                    onClick = onMicClick,
                    interactionSource = micInteractionSource,
                    enabled = !processing && !saving,
                    modifier = Modifier
                        .size(48.dp)
                        .realPetPerch(REAL_PET_PERCH_COMPOSER_MIC)
                        .semantics {
                        contentDescription = if (listening) "Dinlemeyi bitir" else "Sesle REAL oluştur"
                        stateDescription = when {
                            processing -> "REAL düşünüyor"
                            listening -> "Dinleniyor"
                            else -> "Mikrofon kapalı"
                        }
                    }
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                val kick = petMicKick.value
                                scaleX = micScale * (1f + kotlin.math.abs(kick) * 0.025f)
                                scaleY = micScale * (1f - kotlin.math.abs(kick) * 0.018f)
                                rotationZ = kick * 7f
                                translationX = kick * 2.5f
                            }
                            .background(
                                when {
                                    processing -> Color(0xFF285B9E).copy(alpha = 0.26f + aiPulse.value * 0.10f)
                                    listening -> Color(0xFF2D6099).copy(
                                        alpha = if (reduceAnimations) 0.32f else (0.32f + voiceLevel.coerceIn(0f, 1f) * 0.12f)
                                    )
                                    else -> Color.Transparent
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (processing && !reduceAnimations) {
                            Box(
                                Modifier
                                    .size((29f + aiPulse.value * 7f).dp)
                                    .border(
                                        1.2.dp,
                                        micColor.copy(alpha = 0.18f + (1f - aiPulse.value) * 0.24f),
                                        CircleShape
                                    )
                            )
                        }
                        ActionIcon(
                            ActionGlyph.Mic,
                            Modifier
                                .size(if (processing) 20.dp else 24.dp)
                                .graphicsLayer {
                                    if (processing && !reduceAnimations) {
                                        alpha = 0.78f + aiPulse.value * 0.22f
                                    }
                                },
                            micColor
                        )
                    }
                }
                }
            }

            if (expanded) {
                val detectedSuggestion = remember(value) { detectDateSuggestion(value) }
                val showSuggestionChip = remember(detectedSuggestion, dateHint, alertAt) {
                    if (detectedSuggestion == null || onApplyDateSuggestion == null) false
                    else {
                        val currentSelectedDate = alertAt?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        } ?: dateHint
                        val today = LocalDate.now(ZoneId.systemDefault())
                        val targetDate = when (detectedSuggestion) {
                            DateSuggestionType.TODAY -> today
                            DateSuggestionType.TOMORROW -> today.plusDays(1)
                        }
                        currentSelectedDate != targetDate
                    }
                }

                if (showSuggestionChip && detectedSuggestion != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ComposerDateSuggestionChip(
                            suggestion = detectedSuggestion,
                            onClick = {
                                onApplyDateSuggestion?.invoke(detectedSuggestion == DateSuggestionType.TODAY)
                            },
                            designStyle = designStyle,
                            reduceAnimations = reduceAnimations
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val hasAlert = alertAt != null
                    val hasPendingTime = requiresTimeSelection && alertAt == null
                    val hasDateOnly = !hasAlert && !hasPendingTime && dateHint != null
                    val isScheduled = hasAlert || hasPendingTime || hasDateOnly

                    if (isScheduled) {
                        val scheduleLabel = when {
                            hasAlert -> formatComposerScheduleLabel(alertAt!!, repeatRule)
                            hasPendingTime -> formatPendingScheduleLabel(dateHint, repeatRule, timeOfDay)
                            hasDateOnly -> {
                                val today = LocalDate.now(ZoneId.systemDefault())
                                when (dateHint) {
                                    today -> "Bugün"
                                    today.plusDays(1) -> "Yarın"
                                    else -> dateHint!!.format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("tr-TR")))
                                }
                            }
                            else -> ""
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (hasPendingTime) Color(0xFF3B82F6).copy(alpha = 0.18f)
                                    else Color(0xFF91B9FF).copy(alpha = 0.14f)
                                )
                                .clickable(enabled = !recording && !saving, onClick = onPickDate)
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                                .semantics {
                                    contentDescription = when {
                                        hasPendingTime -> "Zamanlama: $scheduleLabel"
                                        hasDateOnly -> "Tarih: $scheduleLabel"
                                        else -> "Tarih ve saat: $scheduleLabel"
                                    }
                                }
                        ) {
                            ActionIcon(
                                ActionGlyph.Calendar,
                                Modifier.size(16.dp),
                                if (hasPendingTime) Color(0xFF60A5FA) else Color(0xFF91B9FF)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = scheduleLabel,
                                color = if (hasPendingTime) Color(0xFF93C5FD) else Color(0xFFE2E8F0),
                                style = RealType.composerScheduleChip
                            )
                        }

                        IconButton(
                            onClick = onClearDate,
                            enabled = !saving,
                            modifier = Modifier.size(28.dp).semantics { contentDescription = "Tarih ve saati kaldır" }
                        ) {
                            ActionIcon(ActionGlyph.Close, Modifier.size(13.dp), Color(0xFFAAAAB4))
                        }
                    } else {
                        IconButton(
                            onClick = onPickDate,
                            enabled = !recording && !saving,
                            modifier = Modifier.size(40.dp).semantics {
                                contentDescription = "Tarih ve saat ekle"
                            }
                        ) {
                            ActionIcon(
                                ActionGlyph.Calendar,
                                Modifier.size(20.dp),
                                Color(0xFFBABAC3)
                            )
                        }
                    }

                    val selectedCategory = remember(customCategories, selectedCategoryId) {
                        customCategories.firstOrNull { it.id == selectedCategoryId }
                    }
                    val isCatSelected = selectedCategory != null
                    val catColor = if (isCatSelected) {
                        ReminderColorChoices.getOrNull(selectedCategory!!.accentStart)?.color ?: Color(0xFF91B9FF)
                    } else Color(0xFFBABAC3)

                    IconButton(
                        onClick = onPickCategory,
                        enabled = !recording && !saving,
                        modifier = Modifier.size(40.dp).semantics {
                            contentDescription = if (isCatSelected) "Kategori: ${selectedCategory!!.name}" else "Kategori seç veya oluştur"
                        }
                    ) {
                        if (isCatSelected) {
                            CategoryIconGlyph(
                                CategoryIcon.fromKey(selectedCategory!!.iconKey),
                                Modifier.size(20.dp),
                                catColor
                            )
                        } else {
                            ActionIcon(
                                ActionGlyph.Category,
                                Modifier.size(20.dp),
                                catColor
                            )
                        }
                    }

                    val hasDraftAttachments = draftAttachments.isNotEmpty()
                    val attachmentColor = if (hasDraftAttachments) Color(0xFF91B9FF) else Color(0xFFBABAC3)
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = onPickAttachment,
                            enabled = !recording && !saving,
                            modifier = Modifier.size(40.dp).semantics {
                                contentDescription = if (hasDraftAttachments) "Ekler: ${draftAttachments.size} fotoğraf" else "Ek ekle"
                            }
                        ) {
                            ActionIcon(
                                ActionGlyph.Add,
                                Modifier.size(20.dp),
                                attachmentColor
                            )
                        }
                        if (hasDraftAttachments) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 4.dp)
                                    .size(14.dp)
                                    .background(Color(0xFF3B82F6), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = draftAttachments.size.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }


                    IconButton(
                        onClick = onToggleChecklistMode,
                        enabled = !recording && !saving,
                        modifier = Modifier.size(40.dp).semantics {
                            contentDescription = if (isChecklistMode) "Liste modunu kapat" else "Liste modu"
                        }
                    ) {
                        ActionIcon(
                            ActionGlyph.Checklist,
                            Modifier.size(20.dp),
                            if (isChecklistMode) Color(0xFF91B9FF) else Color(0xFFBABAC3)
                        )
                    }

                    if (alertAt == null && !hasPendingTime) {
                        IconButton(
                            onClick = onToggleImmediateNotification,
                            enabled = !recording && !saving,
                            modifier = Modifier.size(40.dp).semantics {
                                contentDescription = if (sendImmediateNotification) "Anlık bildirim açık" else "Anlık bildirim kapalı"
                            }
                        ) {
                            ActionIcon(
                                if (sendImmediateNotification) ActionGlyph.Bell else ActionGlyph.BellOff,
                                Modifier.size(20.dp),
                                if (sendImmediateNotification) Color(0xFF91B9FF) else Color(0xFFBABAC3)
                            )
                        }
                    }
                }
            }

            if (expanded && (alertAt != null || voiceDurationMinutes != null || voicePeople.isNotEmpty() || !voiceLocationName.isNullOrBlank())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (alertAt != null) {
                        listOf(30, 45, 60, 90, 120, 150, 180).forEach { mins ->
                            val selected = composerDurationMinutes == mins
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Color(0xFF91B9FF).copy(alpha = 0.24f) else Color.White.copy(alpha = 0.06f))
                                    .border(0.5.dp, if (selected) Color(0xFF91B9FF).copy(alpha = 0.50f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        onComposerDurationMinutesChange(if (selected) null else mins)
                                    }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$mins min",
                                    color = if (selected) Color(0xFFDCE8FF) else Color(0xFFB0B0B8),
                                    fontSize = 10.5.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    voiceDurationMinutes?.let { minutes ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFF91B9FF).copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$minutes min",
                                color = Color(0xFFDCE8FF),
                                style = RealType.composerAuxiliaryPill
                            )
                        }
                    }
                    voicePeople.take(if (voiceLocationName.isNullOrBlank()) 3 else 2).forEach { person ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = person,
                                color = Color(0xFFD8D8DF),
                                style = RealType.composerAuxiliaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    voiceLocationName?.takeIf { it.isNotBlank() }?.let { location ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFF8ED7C4).copy(alpha = 0.11f))
                                .widthIn(max = 120.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = location,
                                color = Color(0xFFCDEEE5),
                                style = RealType.composerAuxiliaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (expanded && draftAttachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    draftAttachments.forEachIndexed { index, attachment ->
                        Box(modifier = Modifier.size(40.dp)) {
                            AsyncImage(
                                model = File(attachment.localPath),
                                contentDescription = "Taslak ek ${index + 1}",
                                modifier = Modifier
                                    .size(36.dp)
                                    .align(Alignment.BottomStart)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(Color(0xE61E1E24))
                                    .border(0.5.dp, Color(0x4DFFFFFF), CircleShape)
                                    .clickable(enabled = !saving) { onRemoveDraftAttachment(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                ActionIcon(ActionGlyph.Close, Modifier.size(8.dp), Color(0xFFEEEEF2))
                            }
                        }
                    }
                }
            }


            if (expanded && isChecklistMode) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    checklistDraftItems.forEachIndexed { index, itemText ->
                        val requester = getItemFocusRequester(index)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(16.dp).border(1.2.dp, Color(0xFF5A5A66), CircleShape)
                            )
                            BasicTextField(
                                value = itemText,
                                onValueChange = { input ->
                                    if (input.contains('\n') || input.contains('\r')) {
                                        val cleaned = input.replace("\r", "").replace("\n", "")
                                        onChecklistItemChange(index, cleaned)
                                        handleChecklistEnter(index)
                                    } else {
                                        onChecklistItemChange(index, input)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                                    .focusRequester(requester)
                                    .onFocusChanged { onFocusChanged(it.isFocused) },
                                readOnly = saving,
                                singleLine = true,
                                textStyle = RealType.composerChecklistItem.copy(
                                    color = Color(0xFFEEEEF2)
                                ),
                                cursorBrush = SolidColor(Color(0xFF9AB9FF)),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { handleChecklistEnter(index) }
                                ),
                                decorationBox = { field ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (itemText.isEmpty()) {
                                            Text("Madde ${index + 1}", color = Color(0xFF6C6C76), style = RealType.composerChecklistPlaceholder)
                                        }
                                        field()
                                    }
                                }
                            )
                            if (checklistDraftItems.size > 1) {
                                IconButton(
                                    onClick = { onRemoveChecklistItem(index) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    ActionIcon(ActionGlyph.Close, Modifier.size(12.dp), Color(0xFF7A7A85))
                                }
                            }
                        }
                    }

                    Row(
                        Modifier
                            .padding(horizontal = 18.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !saving) {
                                val lastItem = checklistDraftItems.lastOrNull() ?: ""
                                if (lastItem.isNotBlank() || checklistDraftItems.isEmpty()) {
                                    onAddChecklistItem()
                                    pendingFocusIndex = checklistDraftItems.size
                                } else {
                                    pendingFocusIndex = checklistDraftItems.lastIndex
                                }
                            }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIcon(ActionGlyph.Add, Modifier.size(13.dp), Color(0xFF91B9FF))
                        Text(
                            "Madde ekle",
                            color = Color(0xFF91B9FF),
                            style = RealType.composerAction,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }

            if (expanded && labsProgress > 0f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = labsProgress
                            translationY = (1f - labsProgress) * with(density) { 8.dp.toPx() }
                        }
                ) {
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 3.dp)
                            .height(0.5.dp)
                            .background(Color.White.copy(alpha = 0.08f))
                    )

                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Real Labs.",
                            color = Color(0xFF7A7A85),
                            style = RealType.composerSectionLabel
                        )
                    }

                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val whatsappGreen = Color(0xFF34D399) 
                        val iconTint = if (isWhatsAppArmed) whatsappGreen else Color(0xFFBABAC3)
                        val interactionSource = remember { MutableInteractionSource() }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isWhatsAppArmed) whatsappGreen.copy(alpha = 0.14f) else Color.Transparent)
                                .border(
                                    width = if (isWhatsAppArmed) 0.75.dp else 0.dp,
                                    color = if (isWhatsAppArmed) whatsappGreen.copy(alpha = 0.35f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    enabled = !saving,
                                    onClick = onToggleWhatsApp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            ActionIcon(
                                ActionGlyph.WhatsApp,
                                Modifier.size(21.dp),
                                iconTint
                            )
                            if (isWhatsAppArmed) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                        .size(12.dp)
                                        .background(whatsappGreen, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ActionIcon(
                                        ActionGlyph.Check,
                                        Modifier.size(8.dp),
                                        Color(0xFF0F172A)
                                    )
                                }
                            }
                        }

                        
                        AnimatedVisibility(
                            visible = isWhatsAppArmed,
                            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(180)),
                            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(140))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = "WhatsApp'tan da gönderilecek",
                                    color = whatsappGreen,
                                    style = RealType.composerStatus
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        if (hasSavedRecipient) {
                            Text(
                                text = "Numarayı değiştir",
                                color = Color(0xFF6B6B76),
                                style = RealType.composerActionSmall,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = !saving, onClick = onConfigureRecipient)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    val petPerchModifier = modifier.realPetPerch(REAL_PET_PERCH_COMPOSER)

    if (designStyle == DesignStyle.LiquidGlass) {
        LiquidGlassSurface(backdrop, backdropPosition, petPerchModifier, corners, reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            role = LiquidGlassRole.Dock,
            content = {
                if (expandedDim > 0.001f) {
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = expandedDim)))
                }
                panelContent()
            })
    } else if (designStyle == DesignStyle.Ice) {
        IcePanel(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            modifier = petPerchModifier,
            corners = corners,
            reduceDynamicBlur = reduceDynamicBlur,
            blurRadius = 38.dp,
            dense = false,
            content = panelContent
        )
    } else {
        GlassPanel(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            modifier = petPerchModifier,
            corners = corners,
            reduceDynamicBlur = reduceDynamicBlur,
            content = panelContent
        )
    }
}
