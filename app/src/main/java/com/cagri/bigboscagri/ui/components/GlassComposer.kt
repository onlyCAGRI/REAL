package com.cagri.bigboscagri.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import com.cagri.bigboscagri.ui.reminderDate
import java.time.LocalDate
import com.cagri.bigboscagri.speech.TimeOfDay
import com.cagri.bigboscagri.speech.formatPendingScheduleLabel
import com.cagri.bigboscagri.speech.formatComposerScheduleLabel
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.cagri.bigboscagri.speech.VoiceTranscriptFormatter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.speech.VoicePhase
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.ui.DesignStyle
import androidx.compose.foundation.gestures.detectTapGestures
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
    customCategories: List<CustomCategory> = emptyList(),
    selectedCategoryId: Long? = null,
    onPickCategory: () -> Unit = {},
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    expanded: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    voicePhase: VoicePhase,
    voiceLevel: Float,
    onMicClick: () -> Unit,
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
    isShareDraft: Boolean = false,
    isHomeReady: Boolean = true,
    modifier: Modifier = Modifier
) {
    val corners by animateDpAsState(
        if (expanded) 26.dp else 30.dp,
        if (reduceAnimations) snap() else tween(250),
        label = "glassCorners"
    )
    val handleAlpha by animateFloatAsState(
        if (expanded) 1f else 0f,
        if (reduceAnimations) snap() else tween(200),
        label = "handle"
    )
    val textInset by animateDpAsState(
        if (expanded) 14.dp else 0.dp,
        if (reduceAnimations) snap() else tween(250),
        label = "textInset"
    )
    val recording = voicePhase != VoicePhase.Idle
    val micColor by animateColorAsState(
        if (recording) Color(0xFF69ACFF) else Color(0xFFE4E4EA),
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
    val view = LocalView.current
    val submitInteractionSource = remember { MutableInteractionSource() }
    val micInteractionSource = remember { MutableInteractionSource() }

    val voiceArrivalProgress = remember(isVoiceDraft) {
        Animatable(if (isVoiceDraft && !reduceAnimations) 0f else 1f)
    }
    LaunchedEffect(isVoiceDraft) {
        if (isVoiceDraft && !reduceAnimations) {
            voiceArrivalProgress.snapTo(0f)
            voiceArrivalProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(180, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))
            )
        }
    }
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

    val shouldShimmer = isResumed && isHomeReady && !reduceAnimations && !expanded && !isComposerFocused && !isChecklistMode && !recording && value.isEmpty()
    val shimmerProgress = remember { Animatable(0f) }

    LaunchedEffect(shouldShimmer) {
        if (shouldShimmer) {
            delay(800)
            while (isActive && shouldShimmer) {
                shimmerProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 1600,
                        easing = LinearEasing
                    )
                )
                shimmerProgress.snapTo(0f)
                delay(5000)
            }
        } else {
            shimmerProgress.snapTo(0f)
        }
    }

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
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(width = 36.dp, height = 3.dp)
                .graphicsLayer { alpha = handleAlpha }
                .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { /* consume panel tap */ } }
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
                    .height(if (expanded) 48.dp else 58.dp)
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
                                when (voicePhase) {
                                    VoicePhase.Idle -> "Hatırlatıcı ekle"
                                    VoicePhase.Listening -> "Türkçe dinleniyor…"
                                    VoicePhase.Finishing -> "Metin hazırlanıyor…"
                                },
                                color = Color(0xFFA2C7FF),
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                            )
                        } else {
                            Text(
                                text = styledVoiceText,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Normal
                                ),
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
                            onValueChange = { onValueChange(it.replace("\r", "").replace("\n", " ")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(voiceArrivalModifier)
                                .focusRequester(focusRequester)
                                .onFocusChanged { onFocusChanged(it.isFocused) }
                                .semantics { contentDescription = if (isChecklistMode) "Başlık" else "Hatırlatıcı metni" },
                            readOnly = saving,
                            singleLine = isChecklistMode,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = Color(0xFFF7F7F8),
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = if (isChecklistMode) FontWeight.SemiBold else FontWeight.Normal
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
                                                fontSize = 16.sp
                                            )
                                        } else {
                                            var placeholderTextWidthPx by remember { mutableFloatStateOf(0f) }

                                            // 1. Base Layer: Calm Grey Text (bone-grey for high contrast)
                                            Text(
                                                text = "Hatırlatıcı ekle",
                                                color = Color(0xFF8E8E96),
                                                fontSize = 16.sp,
                                                onTextLayout = { layoutResult ->
                                                    placeholderTextWidthPx = layoutResult.size.width.toFloat()
                                                }
                                            )

                                            // 2. Shimmer Layer: Pure White Text masked with moving wide gradient mask
                                            val progress = shimmerProgress.value
                                            if (progress > 0.001f && progress < 0.999f) {
                                                val textWidth = if (placeholderTextWidthPx > 0f) placeholderTextWidthPx else 100f
                                                val bandWidth = textWidth * 0.65f
                                                val startX = -bandWidth + progress * (textWidth + bandWidth)
                                                val endX = startX + bandWidth
                                                Text(
                                                    text = "Hatırlatıcı ekle",
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    modifier = Modifier
                                                        .graphicsLayer {
                                                            compositingStrategy = CompositingStrategy.Offscreen
                                                        }
                                                        .drawWithContent {
                                                            drawContent()
                                                            val maskBrush = Brush.linearGradient(
                                                                0.00f to Color.Transparent,
                                                                0.15f to Color.White.copy(alpha = 0.30f),
                                                                0.28f to Color.White.copy(alpha = 0.70f),
                                                                0.40f to Color.White.copy(alpha = 0.95f),
                                                                0.46f to Color.White,
                                                                0.54f to Color.White,
                                                                0.60f to Color.White.copy(alpha = 0.95f),
                                                                0.72f to Color.White.copy(alpha = 0.70f),
                                                                0.85f to Color.White.copy(alpha = 0.30f),
                                                                1.00f to Color.Transparent,
                                                                start = Offset(startX, 0f),
                                                                end = Offset(endX, 0f)
                                                            )
                                                            drawRect(
                                                                brush = maskBrush,
                                                                blendMode = BlendMode.DstIn
                                                            )
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

                IconButton(
                    onClick = onMicClick,
                    interactionSource = micInteractionSource,
                    enabled = voicePhase != VoicePhase.Finishing && !saving,
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = if (recording) "Dinlemeyi bitir" else "Türkçe sesle yaz"
                        stateDescription = when (voicePhase) {
                            VoicePhase.Idle -> "Mikrofon kapalı"
                            VoicePhase.Listening -> "Türkçe dinleniyor"
                            VoicePhase.Finishing -> "Metin hazırlanıyor"
                        }
                    }
                ) {
                    Box(
                        Modifier
                            .pressScale(micInteractionSource, enabled = voicePhase != VoicePhase.Finishing && !saving, targetScale = 0.95f, reduceAnimations = reduceAnimations)
                            .size(40.dp)
                            .graphicsLayer { scaleX = micScale; scaleY = micScale }
                            .background(
                                if (recording) {
                                    Color(0xFF2D6099).copy(
                                        alpha = if (reduceAnimations) 0.32f else (0.32f + voiceLevel.coerceIn(0f, 1f) * 0.12f)
                                    )
                                } else {
                                    Color.Transparent
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (voicePhase == VoicePhase.Finishing) {
                            if (!reduceAnimations) {
                                CircularProgressIndicator(Modifier.size(23.dp), color = micColor, strokeWidth = 2.dp)
                            } else {
                                Box(
                                    Modifier.size(22.dp).border(2.dp, micColor.copy(alpha = 0.55f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ActionIcon(ActionGlyph.Mic, Modifier.size(13.dp), micColor)
                                }
                            }
                        } else {
                            ActionIcon(ActionGlyph.Mic, Modifier.size(24.dp), micColor)
                        }
                    }
                }
            }

            if (expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val hasAlert = alertAt != null
                    val hasPendingTime = requiresTimeSelection && alertAt == null
                    val isScheduled = hasAlert || hasPendingTime

                    if (isScheduled) {
                        val scheduleLabel = if (hasPendingTime) {
                            formatPendingScheduleLabel(dateHint, repeatRule, timeOfDay)
                        } else {
                            formatComposerScheduleLabel(alertAt!!, repeatRule)
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
                                    contentDescription = if (hasPendingTime) "Zamanlama: $scheduleLabel" else "Tarih ve saat: $scheduleLabel"
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
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
                                Modifier.size(21.dp),
                                Color(0xFFBABAC3)
                            )
                        }
                    }

                    if (customCategories.isNotEmpty()) {
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
                                contentDescription = if (isCatSelected) "Kategori: ${selectedCategory!!.name}" else "Kategori seç"
                            }
                        ) {
                            if (isCatSelected) {
                                CategoryIconGlyph(
                                    CategoryIcon.fromKey(selectedCategory!!.iconKey),
                                    Modifier.size(21.dp),
                                    catColor
                                )
                            } else {
                                ActionIcon(
                                    ActionGlyph.Note,
                                    Modifier.size(21.dp),
                                    catColor
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
                            Modifier.size(21.dp),
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
                                Modifier.size(21.dp),
                                if (sendImmediateNotification) Color(0xFF91B9FF) else Color(0xFFBABAC3)
                            )
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
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFFEEEEF2), fontSize = 14.sp
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
                                            Text("Madde ${index + 1}", color = Color(0xFF6C6C76), fontSize = 14.sp)
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (designStyle == DesignStyle.Ice) {
        IcePanel(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            modifier = modifier,
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
            modifier = modifier,
            corners = corners,
            reduceDynamicBlur = reduceDynamicBlur,
            content = panelContent
        )
    }
}
