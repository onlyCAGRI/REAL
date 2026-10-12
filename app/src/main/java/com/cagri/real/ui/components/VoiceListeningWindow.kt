package com.cagri.real.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.speech.VoicePhase
import com.cagri.real.speech.VoiceTranscriptFormatter
import com.cagri.real.ui.DesignStyle

private val VoiceBlue = Color(0xFF69ACFF)
private val VoiceBlueSoft = Color(0xFF91B9FF)
private val VoiceBlueDeep = Color(0xFF2D6FD6)
private val VoiceText = Color(0xFFF7F7F8)

/**
 * Home screen voice window.
 *
 * A separate liquid-glass window (same material as the "Add to REAL" composer) that replaces the
 * composer while the user dictates. The glass takes a blue tone that follows the microphone level,
 * and the recognised words are written on the left. Tapping the mic (or going silent) sends the
 * transcript on; the caller then saves the reminder automatically when the answer comes back.
 */
@Composable
fun VoiceListeningWindow(
    text: String,
    committedText: String,
    partialText: String,
    phase: VoicePhase,
    level: Float,
    aiProcessing: Boolean,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    reduceAnimations: Boolean,
    onMicClick: () -> Unit,
    feedbackText: String? = null,
    canSaveRawText: Boolean = false,
    onSaveRawText: () -> Unit = {},
    onDismissFeedback: () -> Unit = {},
    onLineCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    corners: Dp = 26.dp
) {
    val listening = phase == VoicePhase.Listening
    val processing = phase == VoicePhase.Finishing || aiProcessing
    val showingFeedback = !feedbackText.isNullOrBlank() && !listening && !processing
    val displayText = if (showingFeedback) feedbackText.orEmpty() else text
    val feedbackReveal = remember { Animatable(1f) }
    val dismissProgress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismissFeedback)
    val currentFeedback by rememberUpdatedState(feedbackText)
    val currentShowingFeedback by rememberUpdatedState(showingFeedback)
    val saveRawText by rememberUpdatedState(onSaveRawText)
    val currentCanSaveRawText by rememberUpdatedState(canSaveRawText)
    val rawSwipe = !showingFeedback && canSaveRawText &&
        (listening || phase == VoicePhase.Finishing || aiProcessing)
    val currentRawSwipe by rememberUpdatedState(rawSwipe)
    var dragY by remember(feedbackText, rawSwipe) { mutableFloatStateOf(0f) }
    val dismissDistance = with(LocalDensity.current) { 42.dp.toPx() }
    LaunchedEffect(feedbackText, showingFeedback, reduceAnimations) {
        dismissProgress.snapTo(1f)
        dragY = 0f
        if (showingFeedback && !reduceAnimations) {
            feedbackReveal.snapTo(0f)
            feedbackReveal.animateTo(1f, tween((displayText.length * 14).coerceIn(280, 1000), easing = LinearEasing))
        } else feedbackReveal.snapTo(1f)
    }
    // Character boundaries avoid displaying half of an emoji during the text reveal.
    val revealBoundaries = remember(displayText) {
        buildList {
            add(0)
            var index = 0
            while (index < displayText.length) {
                index += Character.charCount(Character.codePointAt(displayText, index))
                add(index)
            }
        }
    }
    val visibleFeedback = displayText.take(revealBoundaries[
        ((revealBoundaries.size - 1) * feedbackReveal.value).toInt().coerceIn(0, revealBoundaries.lastIndex)])

    DisposableEffect(listening) {
        RealPetRuntime.setComposerMicActive(listening)
        onDispose { if (listening) RealPetRuntime.setComposerMicActive(false) }
    }

    // The mic level drives the blue tone. Quiet room = calm tint, loud speech = brighter glass.
    val smoothLevel by animateFloatAsState(
        targetValue = if (listening) level.coerceIn(0f, 1f) else 0f,
        animationSpec = if (reduceAnimations) snap() else tween(110),
        label = "voiceWindowLevel"
    )
    val processingAlphaState: State<Float>
    val thinkingSweepState: State<Float>
    if (processing && !reduceAnimations) {
        val thinkingTransition = rememberInfiniteTransition(label = "voiceWindowThinking")
        processingAlphaState = thinkingTransition.animateFloat(0.45f, 1f,
            infiniteRepeatable(tween(640, easing = LinearEasing), RepeatMode.Reverse), label = "voiceWindowThinkingAlpha")
        thinkingSweepState = thinkingTransition.animateFloat(0f, 1f,
            infiniteRepeatable(tween(1450, easing = LinearEasing), RepeatMode.Restart), label = "voiceWindowThinkingSweep")
    } else {
        processingAlphaState = rememberUpdatedState(1f)
        thinkingSweepState = rememberUpdatedState(0.5f)
    }
    val processingAlpha by processingAlphaState
    val thinkingSweep by thinkingSweepState

    val enter = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (enter.value < 1f) enter.animateTo(1f, tween(200))
    }

    val (committed, partial) = remember(displayText, committedText, partialText, showingFeedback) {
        if (showingFeedback) displayText to "" else VoiceTranscriptFormatter.splitCommittedAndPartial(displayText, committedText, partialText)
    }
    val styledText = remember(displayText, committed, partial) {
        buildAnnotatedString {
            append(displayText)
            if (displayText.isNotEmpty()) {
                val committedLength = committed.length.coerceAtMost(displayText.length)
                when {
                    committed.isNotEmpty() && partial.isNotEmpty() -> {
                        addStyle(SpanStyle(color = VoiceText), 0, committedLength)
                        val partialStart = displayText.lastIndexOf(partial).takeIf { it >= 0 } ?: committedLength
                        addStyle(SpanStyle(color = VoiceBlueSoft), partialStart, displayText.length)
                    }
                    partial.isNotEmpty() -> addStyle(SpanStyle(color = VoiceBlueSoft), 0, displayText.length)
                    else -> addStyle(SpanStyle(color = VoiceText), 0, displayText.length)
                }
            }
        }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(text) {
        if (text.isNotEmpty()) scrollState.scrollTo(scrollState.maxValue)
    }

    val panelContent: @Composable BoxScope.() -> Unit = {
        // Readability dim, identical to the expanded composer.
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.26f)))
        // Listening keeps the existing microphone-reactive blue. While REAL is processing,
        // the same REAL blue tint travels right -> left through the existing glass instead of
        // introducing a second colour palette or a separate "REAL düşünüyor" status line.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    if (processing) {
                        val progress = if (reduceAnimations) 0.5f else thinkingSweep
                        val centerX = size.width * (1.30f - progress * 1.60f)
                        val bandWidth = size.width * 0.72f
                        drawRect(
                            brush = Brush.linearGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Transparent,
                                    0.22f to VoiceBlueDeep.copy(alpha = 0.07f + processingAlpha * 0.04f),
                                    0.46f to VoiceBlue.copy(alpha = 0.19f + processingAlpha * 0.08f),
                                    0.62f to VoiceBlue.copy(alpha = 0.24f + processingAlpha * 0.09f),
                                    0.82f to VoiceBlueDeep.copy(alpha = 0.07f + processingAlpha * 0.04f),
                                    1.00f to Color.Transparent
                                ),
                                start = Offset(centerX + bandWidth, size.height * 0.12f),
                                end = Offset(centerX - bandWidth, size.height * 0.88f)
                            )
                        )
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(
                                    VoiceBlue.copy(alpha = 0.09f + processingAlpha * 0.05f),
                                    Color.Transparent
                                ),
                                center = Offset(centerX, size.height * 0.58f),
                                radius = size.width * 0.72f
                            )
                        )
                    } else {
                        val strength = 0.10f + smoothLevel * 0.34f
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(VoiceBlueDeep.copy(alpha = strength), Color.Transparent),
                                center = Offset(size.width * 0.86f, size.height * 0.95f),
                                radius = size.width * (0.55f + smoothLevel * 0.30f)
                            )
                        )
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(VoiceBlue.copy(alpha = 0.03f + smoothLevel * 0.10f), Color.Transparent)
                            )
                        )
                    }
                }
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 22.dp, end = 9.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.Top
            ) {
                if (!processing) {
                    Text(
                        text = if (showingFeedback) "REAL" else if (listening && !feedbackText.isNullOrBlank()) feedbackText else "Dinleniyor…",
                        color = VoiceBlueSoft.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (showingFeedback || rawSwipe) Modifier else Modifier.verticalScroll(scrollState)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (displayText.isEmpty()) {
                        Text(
                            text = if (listening && !feedbackText.isNullOrBlank()) "Cevabını söyle…" else "Aklında ne var? Konuş…",
                            color = Color(0xFFA2C7FF),
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                        )
                    } else if (showingFeedback) {
                        Box(Modifier.fillMaxWidth().clearAndSetSemantics {
                            liveRegion = LiveRegionMode.Polite
                            this.text = androidx.compose.ui.text.AnnotatedString(displayText)
                        }) {
                            // Reserve the final text's height: reveal never shifts the panel line by line.
                            Text(displayText, color = Color.Transparent,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                                onTextLayout = { onLineCountChange(it.lineCount) },
                                modifier = Modifier.fillMaxWidth())
                            Text(visibleFeedback, color = VoiceText,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                                modifier = Modifier.fillMaxWidth())
                        }
                    } else {
                        Text(
                            text = styledText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            onTextLayout = { onLineCountChange(it.lineCount) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }


            IconButton(
                onClick = onMicClick,
                enabled = listening || showingFeedback,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = if (showingFeedback) "Yeni ses komutu" else if (listening) "Dinlemeyi bitir" else "REAL düşünüyor"
                        stateDescription = if (showingFeedback) "Ses komutu sonucu" else if (processing) "REAL düşünüyor" else "Dinleniyor"
                    }
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            val pulse = if (reduceAnimations) 0f else smoothLevel * 0.14f
                            scaleX = 1f + pulse
                            scaleY = 1f + pulse
                        }
                        .background(
                            VoiceBlueDeep.copy(alpha = 0.30f + smoothLevel * 0.28f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    ActionIcon(
                        ActionGlyph.Mic,
                        Modifier
                            .size(if (processing) 20.dp else 24.dp)
                            .graphicsLayer { alpha = if (processing) processingAlpha else 1f },
                        VoiceBlue
                    )
                }
            }
        }
    }

    val feedbackGesture = if (showingFeedback || rawSwipe) Modifier.pointerInput(feedbackText, rawSwipe) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, amount ->
                change.consume()
                dragY = (dragY + amount).coerceIn(-dismissDistance * 2f, 0f)
            },
            onDragCancel = { dragY = 0f },
            onDragEnd = {
                if (dragY <= -dismissDistance && rawSwipe) {
                    // One explicit gesture saves the visible words; no AI finalize/interpretation.
                    if (currentRawSwipe && currentCanSaveRawText) saveRawText()
                    dragY = 0f
                } else if (dragY <= -dismissDistance) {
                    val resultBeingDismissed = feedbackText
                    scope.launch {
                        if (!reduceAnimations) dismissProgress.animateTo(0f, tween(140))
                        // An old dismissal must not clear a newer response or active recording.
                        if (currentShowingFeedback && resultBeingDismissed == currentFeedback) dismiss()
                    }
                } else dragY = 0f
            }
        )
    } else Modifier
    val windowModifier = modifier.then(feedbackGesture).graphicsLayer {
        alpha = enter.value * dismissProgress.value *
            (1f + dragY / (dismissDistance * 3f)).coerceIn(0.25f, 1f)
        translationY = (1f - enter.value) * 12.dp.toPx() + dragY - (1f - dismissProgress.value) * 20.dp.toPx()
    }

    when (designStyle) {
        DesignStyle.LiquidGlass -> LiquidGlassSurface(
            backdrop, backdropPosition, windowModifier, corners, reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            role = LiquidGlassRole.Dock,
            content = panelContent
        )
        DesignStyle.Ice -> IcePanel(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            modifier = windowModifier,
            corners = corners,
            reduceDynamicBlur = reduceDynamicBlur,
            blurRadius = 38.dp,
            dense = false,
            content = panelContent
        )
        else -> GlassPanel(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            modifier = windowModifier,
            corners = corners,
            reduceDynamicBlur = reduceDynamicBlur,
            content = panelContent
        )
    }
}
