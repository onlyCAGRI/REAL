package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.activity.compose.BackHandler
import com.cagri.real.ui.detailDockItemMotion
import com.cagri.real.ui.DETAIL_DOCK_MILLIS
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlurEffect
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalFoundationApi::class)
/** Alarm accent in the composer bell (the alarm screen itself takes the category colour). */
internal val ComposerAlarmTone = Color(0xFFFF8A5B)

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
    onPickLocation: () -> Unit = {},
    /** A place reminder is waiting (REAL is watching location): a small dot on the pin. */
    locationWatching: Boolean = false,
    isAlarm: Boolean = false,
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
    /** Pulled up: the composer fills the screen above the keyboard (the host sizes it). */
    fullScreen: Boolean = false,
    onFullScreenChange: (Boolean) -> Unit = {},
    isImportant: Boolean = false,
    onToggleImportant: () -> Unit = {},
    /** Full screen picks a category in place (null: no category). */
    onSelectCategory: ((Long?) -> Unit)? = null,
    /** Full-screen "Yeni…": straight to the new-category editor, not the category list. */
    onCreateCategory: (() -> Unit)? = null,
    /** Full-screen body under the title; saved as the card's first stack card. */
    bodyText: String = "",
    onBodyChange: ((String) -> Unit)? = null,
    /** Full screen time suggestion for the picked day: date, hour, minute. */
    onApplyQuickTime: ((LocalDate, Int, Int) -> Unit)? = null,
    /** Full-screen Ekler: the card detail's attach tools. Null hides that tool. */
    onOpenCamera: (() -> Unit)? = null,
    onPickFiles: (() -> Unit)? = null,
    draftVoiceRecordings: List<com.cagri.real.data.ReminderVoiceRecording> = emptyList(),
    onVoiceRecorded: ((com.cagri.real.data.ReminderVoiceRecording) -> Unit)? = null,
    onRemoveVoiceRecording: (com.cagri.real.data.ReminderVoiceRecording) -> Unit = {},
    draftGames: List<com.cagri.real.data.CardGameBlock> = emptyList(),
    onAddGame: ((com.cagri.real.data.CardGameBlock) -> Unit)? = null,
    onRemoveGame: (com.cagri.real.data.CardGameBlock) -> Unit = {},
    draftMusic: List<com.cagri.real.data.MusicBlock> = emptyList(),
    draftMedia: List<com.cagri.real.data.MediaBlock> = emptyList(),
    onAddMusic: ((com.cagri.real.data.MusicBlock) -> Unit)? = null,
    onAddMedia: ((com.cagri.real.data.MediaBlock) -> Unit)? = null,
    onRemoveMusic: (com.cagri.real.data.MusicBlock) -> Unit = {},
    onRemoveMedia: (com.cagri.real.data.MediaBlock) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val corners by animateDpAsState(
        if (expanded) 26.dp else 30.dp,
        if (reduceAnimations) snap() else RealMotion.smooth(0.28f),
        label = "glassCorners"
    )
    // Expanded panel hosts text: dark glass so the cards behind never bleed into what is being typed;
    // full screen is nearly solid. Dim, tint and text inset are read in draw/layout only.
    val expandedDimState = animateFloatAsState(
        when {
            expanded && fullScreen -> 1f
            expanded -> 0.68f
            else -> 0f
        },
        if (reduceAnimations) snap() else tween(220, easing = FastOutSlowInEasing),
        label = "composerDim"
    )
    val selectedCategory = remember(customCategories, selectedCategoryId) {
        customCategories.firstOrNull { it.id == selectedCategoryId }
    }
    val selectedCategoryColor = selectedCategory?.let { ReminderColorChoices.getOrNull(it.accentStart)?.color }
    // Full screen runs under the status bar; its content starts below it.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val latestOnFullScreenChange = rememberUpdatedState(onFullScreenChange)
    // Full screen: the checklist items get their own page; back (or Tamam) returns to the rows.
    var checklistPageOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isChecklistMode, fullScreen) { if (!isChecklistMode || !fullScreen) checklistPageOpen = false }
    BackHandler(enabled = checklistPageOpen && fullScreen && isChecklistMode) { checklistPageOpen = false }
    // Medya (one search for songs, films and series) gets its own page in full screen.
    var mediaPageOpen by remember { mutableStateOf(false) }
    var voiceRecordingActive by remember { mutableStateOf(false) }
    LaunchedEffect(fullScreen) { if (!fullScreen) mediaPageOpen = false }
    BackHandler(enabled = mediaPageOpen && fullScreen) { mediaPageOpen = false }
    // Growing / shrinking: text and tools blur out and back in like the card detail dock's labels.
    val morph = remember { Animatable(0f) }
    var morphArmed by remember { mutableStateOf(false) }
    LaunchedEffect(fullScreen) {
        if (!morphArmed) { morphArmed = true; return@LaunchedEffect }
        if (reduceAnimations) return@LaunchedEffect
        morph.snapTo(0f)
        morph.animateTo(1f, tween(DETAIL_DOCK_MILLIS, easing = LinearEasing))
        morph.snapTo(0f)
    }
    fun GraphicsLayerScope.composerMorph(lag: Float) {
        val p = ((morph.value - lag) / (1f - lag)).coerceIn(0f, 1f)
        val pulse = kotlin.math.sin(Math.PI * p).toFloat()
        if (pulse <= 0.001f) { renderEffect = null; return }
        alpha = 1f - 0.5f * pulse
        scaleX = 1f + 0.03f * pulse
        scaleY = scaleX
        val radius = 9.dp.toPx() * pulse
        renderEffect = if (Build.VERSION.SDK_INT >= 31 && radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
    }
    val inputTextStyle = if (fullScreen && !isChecklistMode) {
        RealType.composerInput.copy(fontSize = 30.sp, lineHeight = 36.sp)
    } else RealType.composerInput
    val promptTextStyle = if (fullScreen) {
        RealType.composerPrompt.copy(fontSize = 30.sp, lineHeight = 36.sp)
    } else RealType.composerPrompt
    val textInsetState = animateDpAsState(
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

    var fieldLastText by remember { mutableStateOf(value) }
    var fieldSelection by remember { mutableStateOf(TextRange(value.length)) }
    var fieldComposition by remember { mutableStateOf<TextRange?>(null) }

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

        val target = listOf(" miyav", " mrrr", " meaww")[prank.token % 3]
        var draft = latestComposerValue.value
        target.forEach { ch ->
            draft += ch
            latestOnValueChange.value(draft)
            delay(if (reduceAnimations) 22L else 82L)
        }
        RealPetRuntime.composerMeow = target
        RealPetRuntime.composerDraft = draft
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
            petMicKick.animateTo(1f, tween(110, easing = RealMotion.Standard))
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
                    if (!expanded) {
                        detectTapGestures {  }
                    }
                }
                .pointerInput(expanded, recording, mediaPageOpen) {
                    // Only the open panel grows: a swipe up on the closed bar belongs to the home (Yaşam).
                    // On the Medya page a drag scrolls the results, never closes full screen.
                    if (recording || !expanded || mediaPageOpen) return@pointerInput
                    // Pushed up: the composer grows to full screen; pulled down: back.
                    // Watched on the Initial pass without consuming, because the text field's scroll takes drags first.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var dx = 0f
                        var dy = 0f
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                .firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            dx += change.position.x - change.previousPosition.x
                            dy += change.position.y - change.previousPosition.y
                        }
                        val threshold = 40.dp.toPx()
                        if (kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f) {
                            if (dy < -threshold) {
                                latestOnFullScreenChange.value(true)
                            } else if (dy > threshold) {
                                latestOnFullScreenChange.value(false)
                            }
                        }
                    }
                }
                .padding(bottom = if (expanded) 6.dp else 0.dp)
                .deferredTopPadding { if (fullScreen) statusTop + 40.dp else if (expanded) 16.dp else textInsetState.value }
        ) {
            val fullScreenRows = expanded && fullScreen && !recording && !(isChecklistMode && checklistPageOpen) && !mediaPageOpen
            val inputRowModifier = if (recording) {
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 9.dp)
                    .weight(1f)
            } else if (fullScreenRows) {
                // Title grows with its lines (up to five), the detail rows sit right under it.
                val extraLines = (textLineCount - 1).coerceIn(0, 4)
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 62.dp)
                    .height((56 + extraLines * 36).dp)
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
                    .height(if (fullScreen) 60.dp else if (expanded) 48.dp else 54.dp)
            }
            Row(
                modifier = inputRowModifier,
                verticalAlignment = if (recording) Alignment.Bottom else if (fullScreen && !isChecklistMode) Alignment.Top else if (expanded && !isChecklistMode) Alignment.Bottom else Alignment.CenterVertically
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
                                    processing -> t("REAL düşünüyor…")
                                    voicePhase == VoicePhase.Listening -> "Dinleniyor…"
                                    else -> RealPetRuntime.placeholderOverride ?: t("Aklında ne var?")
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
                            .graphicsLayer { composerMorph(0f) }
                            .then(
                                if (expanded && !isChecklistMode) {
                                    Modifier
                                        .fillMaxHeight()
                                        .verticalScroll(textScrollState)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = if (fullScreen && !isChecklistMode) Alignment.TopStart else if (expanded && !isChecklistMode && textLineCount > 1) Alignment.BottomStart else Alignment.CenterStart
                    ) {
                        // Text set from outside (the cat's "miyav", voice) puts the caret at the end.
                        if (value != fieldLastText) {
                            fieldLastText = value
                            fieldSelection = TextRange(value.length)
                            fieldComposition = null
                        }
                        BasicTextField(
                            value = TextFieldValue(value, fieldSelection, fieldComposition),
                            onValueChange = { changedField ->
                                val cleaned = changedField.text.replace("\r", "").replace("\n", " ")
                                val petMeow = RealPetRuntime.composerMeow
                                val next = if (petMeow != null && value.endsWith(petMeow) && cleaned.length == value.length - 1) {
                                    // One backspace (wherever the caret is) takes the cat's whole word away.
                                    RealPetRuntime.composerMeow = null
                                    value.removeSuffix(petMeow).also { fieldSelection = TextRange(it.length); fieldComposition = null }
                                } else {
                                    if (cleaned != value && !isChecklistMode) RealPetRuntime.noteComposerTyping()
                                    fieldSelection = TextRange(changedField.selection.start.coerceAtMost(cleaned.length), changedField.selection.end.coerceAtMost(cleaned.length))
                                    fieldComposition = changedField.composition?.takeIf { cleaned.length == changedField.text.length }
                                    cleaned
                                }
                                fieldLastText = next
                                RealPetRuntime.composerDraft = next
                                if (next != value) onValueChange(next)
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
                                .semantics { contentDescription = if (isChecklistMode) t("Başlık") else t("Hatırlatıcı metni") },
                            readOnly = saving,
                            singleLine = isChecklistMode,
                            visualTransformation = VisualTransformation.None,
                            textStyle = (if (isChecklistMode && fullScreen) RealType.composerInputEmphasized.copy(fontSize = 30.sp, lineHeight = 36.sp)
                                else if (isChecklistMode) RealType.composerInputEmphasized else inputTextStyle).copy(
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
                                                text = t("Başlık"),
                                                color = Color(0xFFB8B8BF),
                                                style = promptTextStyle
                                            )
                                        } else {
                                            var placeholderTextWidthPx by remember { mutableFloatStateOf(0f) }

                                            
                                            Text(
                                                text = RealPetRuntime.placeholderOverride ?: if (expanded) t("Aklında ne var?") else t("Add to REAL"),
                                                color = Color(0xFF8E8E96),
                                                style = promptTextStyle,
                                                onTextLayout = { layoutResult ->
                                                    placeholderTextWidthPx = layoutResult.size.width.toFloat()
                                                }
                                            )

                                            
                                            if (shouldShimmer) {
                                                Text(
                                                    text = RealPetRuntime.placeholderOverride ?: if (expanded) t("Aklında ne var?") else t("Add to REAL"),
                                                    color = Color.White,
                                                    style = promptTextStyle,
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
                        modifier = Modifier.size(36.dp).semantics { contentDescription = t("Listeyi kaydet") }
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
                            .semantics { contentDescription = t("Hatırlatıcıyı kaydet") }
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
                        contentDescription = if (listening) t("Dinlemeyi bitir") else t("Sesle REAL oluştur")
                        stateDescription = when {
                            processing -> t("REAL düşünüyor")
                            listening -> t("Dinleniyor")
                            else -> t("Mikrofon kapalı")
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

            if (fullScreenRows) {
                // Full screen: thin detail rows (Apple style, our line icons); date, time and category open in place.
                var openRow by remember { mutableStateOf<String?>(null) }
                val hasAlert = alertAt != null
                val hasPendingTime = requiresTimeSelection && alertAt == null
                val hasDateOnly = !hasAlert && !hasPendingTime && dateHint != null
                val today = LocalDate.now(ZoneId.systemDefault())
                val scheduleLabel = when {
                    hasAlert -> formatComposerScheduleLabel(alertAt!!, repeatRule)
                    hasPendingTime -> formatPendingScheduleLabel(dateHint, repeatRule, timeOfDay)
                    hasDateOnly -> when (dateHint) {
                        today -> t("Bugün")
                        today.plusDays(1) -> t("Yarın")
                        else -> dateHint!!.format(DateTimeFormatter.ofPattern(if (com.cagri.real.i18n.I18n.isEnglish) "MMMM d" else "d MMMM", com.cagri.real.i18n.I18n.locale))
                    }
                    else -> null
                }
                val rowEnabled = !saving
                val detailRow: @Composable (ActionGlyph, String, String?, Color?, Boolean, () -> Unit) -> Unit = { glyph, label, valueText, dot, active, onClick ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = rowEnabled, onClick = onClick)
                            .padding(horizontal = 14.dp, vertical = 15.dp)
                            .semantics { contentDescription = if (valueText != null) "$label: $valueText" else label },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIcon(glyph, Modifier.size(20.dp), if (active) Color(0xFF91B9FF) else Color(0xFFBABAC3))
                        Spacer(Modifier.width(14.dp))
                        Text(label, color = Color(0xFFE9E9EE), style = RealType.composerStatus.copy(fontSize = 15.sp))
                        Spacer(Modifier.weight(1f))
                        if (dot != null) {
                            Box(Modifier.size(7.dp).background(dot, CircleShape))
                            Spacer(Modifier.width(7.dp))
                        }
                        if (valueText != null) {
                            Text(
                                valueText,
                                color = if (active) Color(0xFFB9CFFF) else Color(0xFF7C7C86),
                                style = RealType.composerStatus.copy(fontSize = 13.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 170.dp)
                            )
                        }
                    }
                }
                val chip: @Composable (String, Color?, Boolean, () -> Unit) -> Unit = { label, dot, on, onClick ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .border(0.5.dp, if (on) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                            .background(if (on) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable(enabled = rowEnabled, onClick = onClick)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dot != null) {
                            Box(Modifier.size(7.dp).background(dot, CircleShape))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(label, color = if (on) Color.White else Color(0xFFD4D4DA), style = RealType.composerStatus)
                    }
                }
                val options: @Composable (Boolean, @Composable () -> Unit) -> Unit = { visible, content ->
                    AnimatedVisibility(
                        visible = visible,
                        enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(180)),
                        exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(140))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 48.dp, end = 12.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) { content() }
                    }
                }
                // Each row is its own soft block (separate pills read clearer than hairlines on the dark glass).
                val block: @Composable (@Composable ColumnScope.() -> Unit) -> Unit = { content ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.055f)),
                        content = content
                    )
                }
                val divider: @Composable () -> Unit = { Spacer(Modifier.height(8.dp)) }
                fun toggle(row: String) { openRow = if (openRow == row) null else row }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp)
                        .graphicsLayer { composerMorph(0.15f) }
                ) {
                    if (onBodyChange != null) {
                        Box(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 26.dp)) {
                            if (bodyText.isEmpty()) Text(t("Not ekle"), color = Color(0xFF6E6E78), style = RealType.composerStatus.copy(fontSize = 17.sp, lineHeight = 24.sp))
                            BasicTextField(
                                value = bodyText,
                                onValueChange = { onBodyChange(it.take(4000)) },
                                enabled = rowEnabled,
                                textStyle = RealType.composerStatus.copy(fontSize = 17.sp, lineHeight = 24.sp, color = Color(0xFFD9D9E0)),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            )
                        }
                    } else Spacer(Modifier.height(20.dp))
                    block {
                    detailRow(ActionGlyph.Calendar, t("Tarih ve saat"), scheduleLabel ?: t("Yok"), null, scheduleLabel != null) { toggle("date") }
                    options(openRow == "date") {
                        onApplyDateSuggestion?.let { apply ->
                            chip(t("Bugün"), null, (hasDateOnly || hasAlert) && (dateHint ?: alertAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }) == today) { apply(true) }
                            chip(t("Yarın"), null, (hasDateOnly || hasAlert) && (dateHint ?: alertAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }) == today.plusDays(1)) { apply(false) }
                        }
                        chip(t("Seç…"), null, false) { openRow = null; onPickDate() }
                        if (scheduleLabel != null) chip(t("Kaldır"), null, false) { openRow = null; onClearDate() }
                    }
                    // A day was picked without a time: a few likely times for that day.
                    val quickTimeDay = dateHint.takeIf { hasDateOnly }
                    val quickTimes = remember(quickTimeDay) {
                        val day = quickTimeDay ?: return@remember emptyList()
                        val nowHour = java.time.LocalTime.now().hour
                        listOf(9, 12, 15, 18, 21).filter { day != today || it > nowHour }.take(4)
                    }
                    options(openRow == "date" && quickTimeDay != null && quickTimes.isNotEmpty() && onApplyQuickTime != null) {
                        quickTimes.forEach { hour ->
                            chip(String.format(Locale.ROOT, "%02d:00", hour), null, false) {
                                quickTimeDay?.let { day -> onApplyQuickTime?.invoke(day, hour, 0) }
                                openRow = null
                            }
                        }
                    }
                    }
                    divider()
                    block {
                    detailRow(ActionGlyph.Category, t("Kategori"), selectedCategory?.name ?: t("Yok"), selectedCategoryColor, selectedCategory != null) {
                        if (onSelectCategory != null) toggle("category") else onPickCategory()
                    }
                    options(openRow == "category" && onSelectCategory != null) {
                        chip(t("Yok"), null, selectedCategory == null) { onSelectCategory?.invoke(null); openRow = null }
                        customCategories.forEach { category ->
                            val color = ReminderColorChoices.getOrNull(category.accentStart)?.color
                            chip(category.name, color, category.id == selectedCategoryId) { onSelectCategory?.invoke(category.id); openRow = null }
                        }
                        chip(t("Yeni…"), null, false) { openRow = null; (onCreateCategory ?: onPickCategory)() }
                    }
                    }
                    divider()
                    block { detailRow(ActionGlyph.Pin, t("Konum"), voiceLocationName?.takeIf { it.isNotBlank() } ?: t("Ekle"), null, !voiceLocationName.isNullOrBlank() || locationWatching, onPickLocation) }
                    divider()
                    val filledItems = checklistDraftItems.count { it.isNotBlank() }
                    block { detailRow(
                        ActionGlyph.Checklist,
                        t("Liste"),
                        if (isChecklistMode && filledItems > 0) t("{0} madde", filledItems) else t("Ekle"),
                        null,
                        isChecklistMode && filledItems > 0
                    ) {
                        if (!isChecklistMode) onToggleChecklistMode()
                        checklistPageOpen = true
                    } }
                    divider()
                    // Ekler: one picker for photos, files and audio. Camera, voice and media sit on their own rows.
                    block {
                        detailRow(
                            ActionGlyph.Add,
                            t("Ekler"),
                            if (draftAttachments.isEmpty()) t("Ekle") else t("{0} ek", draftAttachments.size),
                            null,
                            draftAttachments.isNotEmpty(),
                            onPickFiles ?: onPickAttachment
                        )
                    }
                    onOpenCamera?.let { openCamera ->
                        divider()
                        block { detailRow(ActionGlyph.Camera, t("Kamera"), t("Çek"), null, false, openCamera) }
                    }
                    if (onVoiceRecorded != null) {
                        divider()
                        block {
                            // Kaydet starts recording right here; a blue circle breathes with the voice level.
                            val recorder = rememberVoiceRecorder(rowEnabled, onVoiceRecorded, { voiceRecordingActive = it })
                            if (recorder.isRecording) {
                                val level by animateFloatAsState(kotlin.math.sqrt(recorder.activityLevel.coerceIn(0f, 1f)), tween(110), label = "composerVoiceLevel")
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = recorder.stop)
                                        .padding(horizontal = 10.dp, vertical = 11.dp)
                                        .semantics { contentDescription = t("Ses kaydını durdur") },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                        Box(
                                            Modifier
                                                .size(28.dp)
                                                .graphicsLayer { val k = 0.55f + level * 0.45f; scaleX = k; scaleY = k; alpha = 0.25f + level * 0.35f }
                                                .background(Color(0xFF4F8DFF), CircleShape)
                                        )
                                        Box(Modifier.size(12.dp).background(Color(0xFF91B9FF), CircleShape))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(t("Dinliyorum…"), color = Color(0xFFE9E9EE), style = RealType.composerStatus.copy(fontSize = 15.sp))
                                    Spacer(Modifier.weight(1f))
                                    Text(com.cagri.real.data.formatVoiceDuration(recorder.elapsedMillis), color = Color(0xFFB9CFFF), style = RealType.composerStatus.copy(fontSize = 13.sp))
                                    Spacer(Modifier.width(12.dp))
                                    Box(Modifier.size(13.dp).background(Color(0xFFE9E9EE), RoundedCornerShape(3.dp)))
                                    Spacer(Modifier.width(4.dp))
                                }
                            } else {
                                detailRow(
                                    ActionGlyph.Mic,
                                    t("Ses kaydı"),
                                    if (draftVoiceRecordings.isEmpty()) t("Kaydet") else t("{0} kayıt", draftVoiceRecordings.size),
                                    null,
                                    draftVoiceRecordings.isNotEmpty(),
                                    recorder.requestStart
                                )
                            }
                            recorder.errorText?.let {
                                Text(it, color = Color(0xFFE0B5A8), style = RealType.composerStatus.copy(fontSize = 12.sp), modifier = Modifier.padding(start = 48.dp, end = 12.dp, bottom = 10.dp))
                            }
                            draftVoiceRecordings.forEach { recording ->
                                key(recording.id) {
                                    VoicePlaybackRow(
                                        recording = recording,
                                        onDelete = { onRemoveVoiceRecording(recording) },
                                        compact = true,
                                        modifier = Modifier.padding(start = 34.dp, end = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (onAddMusic != null || onAddMedia != null) {
                        divider()
                        val mediaCount = draftMusic.size + draftMedia.size + draftGames.size
                        block {
                            detailRow(
                                ActionGlyph.Music,
                                t("Medya"),
                                if (mediaCount == 0) t("Şarkı, film, dizi") else t("{0} ek", mediaCount),
                                null,
                                mediaCount > 0
                            ) { mediaPageOpen = true }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (expanded && fullScreen && mediaPageOpen && !recording) {
                ComposerMediaSearch(
                    music = draftMusic,
                    media = draftMedia,
                    onAddMusic = { onAddMusic?.invoke(it) },
                    onAddMedia = { onAddMedia?.invoke(it) },
                    onRemoveMusic = onRemoveMusic,
                    onRemoveMedia = onRemoveMedia,
                    games = draftGames,
                    onAddGame = onAddGame,
                    onRemoveGame = onRemoveGame,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            // The Medya page is the media place itself: no tool icons under it.
            if (expanded && !fullScreenRows && !(fullScreen && mediaPageOpen)) {
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

                    // The tools come in after the panel starts growing: a soft left-to-right sweep, so they read as
                    // arriving one by one (calendar first, bell last).
                    val toolsReveal = remember { Animatable(if (reduceAnimations) 1f else 0f) }
                    LaunchedEffect(Unit) {
                        if (!reduceAnimations) { delay(70); toolsReveal.animateTo(1f, tween(460, easing = RealMotion.Standard)) }
                    }
                    // Tools scroll sideways when they do not fit (a long date chip); the bell is the last of them.
                    Row(
                        modifier = Modifier.weight(1f)
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                                translationY = (1f - toolsReveal.value) * 6.dp.toPx()
                                composerMorph(0.2f)
                            }
                            .drawWithContent {
                                drawContent()
                                val front = size.width * toolsReveal.value * 1.4f
                                drawRect(
                                    Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = front - size.width * 0.4f, endX = front),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {

                    if (isScheduled) {
                        val scheduleLabel = when {
                            hasAlert -> formatComposerScheduleLabel(alertAt!!, repeatRule)
                            hasPendingTime -> formatPendingScheduleLabel(dateHint, repeatRule, timeOfDay)
                            hasDateOnly -> {
                                val today = LocalDate.now(ZoneId.systemDefault())
                                when (dateHint) {
                                    today -> com.cagri.real.i18n.t("Bugün")
                                    today.plusDays(1) -> com.cagri.real.i18n.t("Yarın")
                                    else -> dateHint!!.format(DateTimeFormatter.ofPattern(if (com.cagri.real.i18n.I18n.isEnglish) "MMMM d" else "d MMMM", com.cagri.real.i18n.I18n.locale))
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
                                        hasPendingTime -> t("Zamanlama: {0}", scheduleLabel)
                                        hasDateOnly -> t("Tarih: {0}", scheduleLabel)
                                        else -> t("Tarih ve saat: {0}", scheduleLabel)
                                    }
                                }
                        ) {
                            val chipIconColor = if (hasPendingTime) Color(0xFF60A5FA) else Color(0xFF91B9FF)
                            // An alarm card shows the alarm clock instead of the calendar.
                            if (isAlarm) AlarmClockGlyph(chipIconColor, Modifier.size(16.dp))
                            else ActionIcon(ActionGlyph.Calendar, Modifier.size(16.dp), chipIconColor)
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
                            modifier = Modifier.size(28.dp).semantics { contentDescription = t("Tarih ve saati kaldır") }
                        ) {
                            ActionIcon(ActionGlyph.Close, Modifier.size(13.dp), Color(0xFFAAAAB4))
                        }
                    } else {
                        IconButton(
                            onClick = onPickDate,
                            enabled = !recording && !saving,
                            modifier = Modifier.size(40.dp).semantics {
                                contentDescription = t("Tarih ve saat ekle")
                            }
                        ) {
                            ActionIcon(
                                ActionGlyph.Calendar,
                                Modifier.size(20.dp),
                                Color(0xFFBABAC3)
                            )
                        }
                    }

                    val isCatSelected = selectedCategory != null
                    val catColor = if (isCatSelected) {
                        ReminderColorChoices.getOrNull(selectedCategory!!.accentStart)?.color ?: Color(0xFF91B9FF)
                    } else Color(0xFFBABAC3)

                    IconButton(
                        onClick = onPickCategory,
                        enabled = !recording && !saving,
                        modifier = Modifier.size(40.dp).semantics {
                            contentDescription = if (isCatSelected) t("Kategori: {0}", selectedCategory!!.name) else t("Kategori seç veya oluştur")
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
                                contentDescription = if (hasDraftAttachments) t("Ekler: {0} fotoğraf", draftAttachments.size) else t("Ek ekle")
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
                            contentDescription = if (isChecklistMode) t("Liste modunu kapat") else t("Liste modu")
                        }
                    ) {
                        ActionIcon(
                            ActionGlyph.Checklist,
                            Modifier.size(20.dp),
                            if (isChecklistMode) Color(0xFF91B9FF) else Color(0xFFBABAC3)
                        )
                    }

                    // Konum: a place on the card or "Konuma varınca hatırlat" (the pin's menu).
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = onPickLocation,
                            enabled = !recording && !saving,
                            modifier = Modifier.size(40.dp).semantics {
                                contentDescription = buildString {
                                    append(if (voiceLocationName.isNullOrBlank()) t("Konum ekle") else t("Konum: {0}", voiceLocationName))
                                    if (locationWatching) append(t(", konum hatırlatması için konum kullanılıyor"))
                                }
                            }
                        ) {
                            ActionIcon(
                                ActionGlyph.Pin,
                                Modifier.size(20.dp),
                                if (voiceLocationName.isNullOrBlank()) Color(0xFFBABAC3) else Color(0xFF91B9FF)
                            )
                        }
                        if (locationWatching) {
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 8.dp).size(6.dp)
                                    .background(Color(0xFF55D3B4), CircleShape)
                            )
                        }
                    }

                    // Bell, last of the tools: notification on/off (an undated card notifies right away).
                    // The alarm lives in the date sheet (Saat pushed right), so the bell has no alarm state.
                    val bellOn = hasAlert || hasPendingTime || sendImmediateNotification
                    IconButton(
                        onClick = onToggleImmediateNotification,
                        enabled = !recording && !saving,
                        modifier = Modifier.size(40.dp).semantics {
                            contentDescription = if (bellOn) t("Bildirim açık") else t("Bildirim kapalı")
                        }
                    ) {
                        ActionIcon(
                            if (bellOn) ActionGlyph.Bell else ActionGlyph.BellOff,
                            Modifier.size(20.dp),
                            if (bellOn) Color(0xFF91B9FF) else Color(0xFFBABAC3)
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
                                    text = t("{0} min", mins),
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
                                text = t("{0} min", minutes),
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

            // Everything added sits here as square tiles: photos, files, voice notes, songs, films and series.
            val trayCount = draftAttachments.size + draftVoiceRecordings.size + draftMusic.size + draftMedia.size + draftGames.size
            if (expanded && trayCount > 0 && !(fullScreen && isChecklistMode && checklistPageOpen) && !(fullScreen && mediaPageOpen)) {
                val tile = if (fullScreen) 58.dp else 36.dp
                val trayTile: @Composable (String, () -> Unit, @Composable BoxScope.() -> Unit) -> Unit = { description, onRemove, content ->
                    Box(modifier = Modifier.size(tile + 4.dp)) {
                        Box(
                            Modifier
                                .size(tile)
                                .align(Alignment.BottomStart)
                                .clip(RoundedCornerShape(if (fullScreen) 12.dp else 8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(if (fullScreen) 12.dp else 8.dp))
                                .semantics { contentDescription = description },
                            contentAlignment = Alignment.Center,
                            content = content
                        )
                        Box(
                            modifier = Modifier
                                .size(if (fullScreen) 20.dp else 16.dp)
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(Color(0xE61E1E24))
                                .border(0.5.dp, Color(0x4DFFFFFF), CircleShape)
                                .clickable(enabled = !saving, onClick = onRemove),
                            contentAlignment = Alignment.Center
                        ) {
                            ActionIcon(ActionGlyph.Close, Modifier.size(if (fullScreen) 9.dp else 8.dp), Color(0xFFEEEEF2))
                        }
                    }
                }
                val glyphSize = if (fullScreen) 22.dp else 18.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, bottom = if (fullScreen) 10.dp else 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    draftAttachments.forEachIndexed { index, attachment ->
                        trayTile(t("Taslak ek {0}", index + 1), { onRemoveDraftAttachment(index) }) {
                            if (attachment.type != com.cagri.real.data.AttachmentType.IMAGE) {
                                ActionIcon(ActionGlyph.File, Modifier.size(glyphSize), Color(0xFFD4D4DA))
                            } else AsyncImage(
                                model = File(attachment.localPath),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    draftVoiceRecordings.forEach { recording ->
                        key(recording.id) {
                            trayTile(t("Ses kaydı"), { onRemoveVoiceRecording(recording) }) {
                                ActionIcon(ActionGlyph.AudioWave, Modifier.size(glyphSize), Color(0xFF91B9FF))
                            }
                        }
                    }
                    draftMusic.forEach { song ->
                        key(song.id) {
                            trayTile(song.title, { onRemoveMusic(song) }) { MusicArtwork(song, Modifier.fillMaxSize()) }
                        }
                    }
                    draftMedia.forEach { item ->
                        key(item.id) {
                            trayTile(item.title, { onRemoveMedia(item) }) { MediaArtwork(item, Modifier.fillMaxSize()) }
                        }
                    }
                    draftGames.forEach { game ->
                        key(game.id) {
                            trayTile(game.title, { onRemoveGame(game) }) { GameCover(game, Modifier.fillMaxSize()) }
                        }
                    }
                }
            }


            if (expanded && isChecklistMode && !fullScreenRows) {
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
                                            Text(t("Madde {0}", index + 1), color = Color(0xFF6C6C76), style = RealType.composerChecklistPlaceholder)
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
                            t("Madde ekle"),
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
                            text = t("Real Labs."),
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
                                    text = t("WhatsApp'tan da gönderilecek"),
                                    color = whatsappGreen,
                                    style = RealType.composerStatus
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        if (hasSavedRecipient) {
                            Text(
                                text = t("Numarayı değiştir"),
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
        if (expanded && !recording) {
            // Grabber: pull (or tap) to switch between the panel and full screen.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (fullScreen) statusTop + 4.dp else 0.dp)
                    .size(width = 72.dp, height = 16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !saving
                    ) { onFullScreenChange(!fullScreen) }
                    .semantics { contentDescription = if (fullScreen) t("Küçült") else t("Tam ekran") },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(Color.White.copy(alpha = 0.28f), CircleShape))
            }
        }
        AnimatedVisibility(
            visible = fullScreen && selectedCategory != null,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = statusTop + 24.dp),
            enter = EnterTransition.None,
            exit = ExitTransition.None
        ) {
            val shownCategory = selectedCategory ?: return@AnimatedVisibility
            Row(Modifier.then(detailDockItemMotion(140, reduceAnimations)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(selectedCategoryColor ?: Color(0xFF91B9FF), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(
                    shownCategory.name,
                    color = Color(0xFFE4E4EA).copy(alpha = 0.8f),
                    style = RealType.composerStatus,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Bell and star sit top right in full screen; the checklist page shows Tamam there instead.
        AnimatedVisibility(
            visible = fullScreen && !(isChecklistMode && checklistPageOpen) && !mediaPageOpen,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = statusTop + 12.dp),
            enter = EnterTransition.None,
            exit = ExitTransition.None
        ) {
            val bellOn = alertAt != null || (requiresTimeSelection && alertAt == null) || sendImmediateNotification
            Row(Modifier.then(detailDockItemMotion(200, reduceAnimations))) {
                IconButton(
                    onClick = onToggleImmediateNotification,
                    enabled = !saving,
                    modifier = Modifier.size(40.dp).semantics { contentDescription = if (bellOn) t("Bildirim açık") else t("Bildirim kapalı") }
                ) {
                    ActionIcon(if (bellOn) ActionGlyph.Bell else ActionGlyph.BellOff, Modifier.size(18.dp), if (bellOn) Color(0xFF91B9FF) else Color(0xFFBABAC3))
                }
                IconButton(
                    onClick = onToggleImportant,
                    enabled = !saving,
                    modifier = Modifier.size(40.dp).semantics { contentDescription = if (isImportant) t("Önemli işaretini kaldır") else t("Önemli olarak işaretle") }
                ) {
                    ActionIcon(ActionGlyph.Star, Modifier.size(18.dp), if (isImportant) Color(0xFFEACB75) else Color(0xFFBABAC3), filled = isImportant)
                }
            }
        }
        // Small blue done key at the end of the title line (keyboard enter); only there once something is written.
        AnimatedVisibility(
            visible = fullScreen && value.isNotBlank() && !(isChecklistMode && checklistPageOpen) && !mediaPageOpen,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = statusTop + 55.dp),
            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(160)),
            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(120))
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3A7BFF))
                    .clickable(enabled = !saving) { onSubmit() }
                    .semantics { contentDescription = t("Kaydet") },
                contentAlignment = Alignment.Center
            ) {
                ActionIcon(ActionGlyph.Check, Modifier.size(13.dp), Color.White)
            }
        }
        AnimatedVisibility(
            visible = fullScreen && ((isChecklistMode && checklistPageOpen) || mediaPageOpen),
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = statusTop + 14.dp),
            enter = EnterTransition.None,
            exit = ExitTransition.None
        ) {
            Text(
                t("Tamam"),
                color = Color(0xFF91B9FF),
                style = RealType.composerStatus.copy(fontSize = 15.sp),
                modifier = Modifier
                    .then(detailDockItemMotion(120, reduceAnimations))
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !voiceRecordingActive) { checklistPageOpen = false; mediaPageOpen = false }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }

    // Dark glass under every design style; one colour whatever the category.
    val panelWithDim: @Composable BoxScope.() -> Unit = {
        Box(Modifier.matchParentSize().drawBehind {
            val dim = expandedDimState.value
            if (dim > 0.001f) drawRect(Color.Black.copy(alpha = dim))
        })
        panelContent()
    }

    val petPerchModifier = modifier.realPetPerch(REAL_PET_PERCH_COMPOSER)

    if (designStyle == DesignStyle.LiquidGlass) {
        LiquidGlassSurface(backdrop, backdropPosition, petPerchModifier, corners, reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            role = LiquidGlassRole.Dock,
            showRim = !fullScreen,
            content = panelWithDim)
    } else if (designStyle == DesignStyle.Ice) {
        IcePanel(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            modifier = petPerchModifier,
            corners = corners,
            reduceDynamicBlur = reduceDynamicBlur,
            blurRadius = 38.dp,
            dense = false,
            content = panelWithDim
        )
    } else {
        GlassPanel(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            modifier = petPerchModifier,
            corners = corners,
            reduceDynamicBlur = reduceDynamicBlur,
            rim = !fullScreen,
            content = panelWithDim
        )
    }
}
