package com.cagri.real.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.EnterExitState

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalView
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.formatVoiceDuration
import com.cagri.real.ui.reminderDate
import com.cagri.real.ui.reminderPlannedDate
import com.cagri.real.ui.theme.RealType

fun resolveCategoryStripeColors(category: CustomCategory): Pair<Color, Color> {
    val startColor = ReminderColorChoices.getOrNull(category.accentStart)?.color ?: Color(0xFF233F73)
    val endColor = category.accentEnd?.let { ReminderColorChoices.getOrNull(it)?.color }
    return if (endColor != null && endColor != startColor) {
        startColor to endColor
    } else {
        startColor to startColor.copy(alpha = 0.55f)
    }
}

private val ReminderCardShape = RoundedCornerShape(22.dp)

class ReminderEntryTracker(
    private val scope: CoroutineScope? = null,
    private val timeoutMillis: Long = 450L
) {
    var animatingIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    fun onReminderCreated(id: Long, reduceAnimations: Boolean) {
        if (reduceAnimations) return
        animatingIds = animatingIds + id
        scope?.launch {
            delay(timeoutMillis)
            onAnimationFinished(id)
        }
    }

    fun onAnimationFinished(id: Long) {
        animatingIds = animatingIds - id
    }

    fun isAnimating(id: Long): Boolean = id in animatingIds

    fun clear() {
        animatingIds = emptySet()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ReminderCard(
    reminder: ReminderItem,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    reduceAnimations: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    onSelectToggle: () -> Unit = {},
    customCategory: CustomCategory? = null,
    showCategoryStripes: Boolean = true,
    isNewlyCreated: Boolean = false,
    onEntryAnimationFinished: (() -> Unit)? = null,
    journalListMode: Boolean = false,
    showVoiceSummary: Boolean = true,
    onSwipeLeftTo24Hour: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val corner = if (!reduceAnimations && animatedVisibilityScope != null) {
        animatedVisibilityScope.transition.animateFloat(
            transitionSpec = { RealMotion.smooth(0.32f) }, label = "listCardCorner"
        ) { if (it == EnterExitState.Visible) 22f else 28f }.value
    } else 22f
    val shape = RoundedCornerShape(corner.dp)
    val arrivalProgress = remember(reminder.id) {
        Animatable(if (isNewlyCreated && !reduceAnimations) 0f else 1f)
    }
    LaunchedEffect(reminder.id, isNewlyCreated) {
        if (isNewlyCreated && !reduceAnimations) {
            arrivalProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 320,
                    easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
                )
            )
            onEntryAnimationFinished?.invoke()
        }
    }
    val arrivalModifier = if (!reduceAnimations && isNewlyCreated) {
        Modifier.graphicsLayer {
            val p = arrivalProgress.value
            alpha = 0.1f + 0.9f * p
            translationY = with(density) { ((1f - p) * 32.dp.toPx()) }
            val scale = 0.98f + 0.02f * p
            scaleX = scale
            scaleY = scale
        }
    } else Modifier

    val accentBrush = remember(reminder.accentStart, reminder.accentEnd, reminder.isCompleted, reminder.gradientBlendFraction, reminder.customSolidColorArgb, journalListMode) {
        reminderAccentBrush(reminder.accentStart, reminder.accentEnd, if (journalListMode) false else reminder.isCompleted, reminder.gradientBlendFraction, reminder.customSolidColorArgb)
    }
    val categoryStripeColors = remember(reminder.customCategoryId, customCategory, showCategoryStripes) {
        if (showCategoryStripes && reminder.customCategoryId != null && customCategory != null) {
            resolveCategoryStripeColors(customCategory)
        } else null
    }
    val useDark = reminder.useDarkText
    val textColor by animateColorAsState(
        when {
            useDark -> if (reminder.isCompleted) Color(0xFF55555A) else Color(0xFF101010)
            journalListMode || !reminder.isCompleted -> Color(0xFFF3F3F5)
            else -> Color(0xFF808089)
        },
        if (reduceAnimations) snap() else tween(200),
        label = "completionColor"
    )

    val circleBgColor by animateColorAsState(
        targetValue = if (reminder.isCompleted) {
            if (useDark) Color(0xFF202025) else Color(0xFF808089)
        } else Color.Transparent,
        animationSpec = if (reduceAnimations) snap() else tween(120),
        label = "circleBg"
    )
    val circleBorderColor by animateColorAsState(
        targetValue = if (reminder.isCompleted) Color.Transparent else (if (useDark) Color(0xFF35353C) else Color(0xFF626269)),
        animationSpec = if (reduceAnimations) snap() else tween(120),
        label = "circleBorder"
    )
    val circlePulse = remember(reminder.id) { Animatable(1f) }
    var hasRenderedInitialCircle by remember(reminder.id) { mutableStateOf(false) }
    LaunchedEffect(reminder.isCompleted) {
        if (!hasRenderedInitialCircle) {
            hasRenderedInitialCircle = true
            return@LaunchedEffect
        }
        if (reminder.isCompleted && !reduceAnimations) {
            circlePulse.animateTo(1.05f, tween(100))
            circlePulse.animateTo(1.0f, tween(120, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)))
        } else {
            circlePulse.snapTo(1.0f)
        }
    }
    var recurringAcknowledged by remember(reminder.id) { mutableStateOf(false) }
    val recurringAckAlpha = remember(reminder.id) { Animatable(0f) }
    LaunchedEffect(recurringAcknowledged) {
        if (recurringAcknowledged && !reduceAnimations) {
            try {
                launch {
                    circlePulse.animateTo(1.05f, tween(80))
                    circlePulse.animateTo(1.0f, tween(100, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)))
                }
                recurringAckAlpha.animateTo(1f, tween(60))
                recurringAckAlpha.animateTo(0f, tween(140))
            } finally {
                recurringAckAlpha.snapTo(0f)
                circlePulse.snapTo(1f)
                recurringAcknowledged = false
            }
        }
    }
    val shouldStrikeThrough = !journalListMode && reminder.isCompleted && reminder.journal == null
    val strikethroughProgress = remember(reminder.id) {
        Animatable(if (shouldStrikeThrough) 1f else 0f)
    }
    LaunchedEffect(shouldStrikeThrough) {
        if (!reduceAnimations) {
            strikethroughProgress.animateTo(
                targetValue = if (shouldStrikeThrough) 1f else 0f,
                animationSpec = tween(180, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))
            )
        } else {
            strikethroughProgress.snapTo(if (shouldStrikeThrough) 1f else 0f)
        }
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val sharedBoundsModifier = if (!reduceAnimations && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "reminder-card-${reminder.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> RealMotion.smooth(0.32f) },
                clipInOverlayDuringTransition = OverlayClip(shape)
            )
        }
    } else Modifier

    // Starred cards always get a plain white outer ring. starBorderColorIndex stays in the data
    // (no migration) but is no longer used for drawing.
    val starBorderColor = if (reminder.isImportant) StarCardBorderColor else null

    val borderColor = when {
        isSelected -> Color(0xFF94B8F7).copy(alpha = 0.65f)
        starBorderColor != null -> starBorderColor
        accentBrush != null -> Color.White.copy(alpha = 0.10f)
        else -> Color(0xFF222225)
    }
    val borderWidth = if (isSelected || starBorderColor != null) 1.25.dp else 0.75.dp
    val selectionOverlay = if (isSelected) Color(0xFF94B8F7).copy(alpha = 0.07f) else Color.Transparent
    val cardInteractionSource = remember(reminder.id) { MutableInteractionSource() }
    val circleInteractionSource = remember(reminder.id) { MutableInteractionSource() }
    val view = LocalView.current
    val swipeEnabled = onSwipeLeftTo24Hour != null && !isSelectionMode && !reminder.isCompleted && !journalListMode
    val swipeScope = rememberCoroutineScope()
    val swipeThresholdPx = with(density) { 82.dp.toPx() }
    val swipeExitPx = with(density) { 900.dp.toPx() }
    val swipeAnim = remember(reminder.id) { Animatable(0f) }
    var swipeDragX by remember(reminder.id) { mutableFloatStateOf(0f) }
    var swipeDragging by remember(reminder.id) { mutableStateOf(false) }
    var swipeArmed by remember(reminder.id) { mutableStateOf(false) }
    val swipeVelocityTracker = remember(reminder.id) { VelocityTracker() }
    val swipeFlingVelocityPx = with(density) { 520.dp.toPx() }
    val swipeFlingMinDistancePx = with(density) { 28.dp.toPx() }
    val renderedSwipeX = if (swipeDragging) swipeDragX else swipeAnim.value
    suspend fun settleSwipe(startX: Float, commit: Boolean, velocityX: Float) {
        swipeAnim.snapTo(startX)
        swipeDragging = false
        swipeDragX = 0f
        val target = if (commit) -swipeExitPx else 0f
        if (reduceAnimations) {
            swipeAnim.snapTo(target)
        } else {
            swipeAnim.animateTo(target, RealMotion.interactive(), initialVelocity = velocityX)
        }
    }
    val swipeGestureModifier = if (swipeEnabled) {
        Modifier.pointerInput(reminder.id, onSwipeLeftTo24Hour, reduceAnimations) {
            detectHorizontalDragGestures(
                onDragStart = {
                    swipeDragging = true
                    swipeDragX = swipeAnim.value
                    swipeArmed = false
                    swipeVelocityTracker.resetTracking()
                },
                onHorizontalDrag = { change, dragAmount ->
                    if (dragAmount < 0f || swipeDragX < 0f) {
                        change.consume()
                        swipeDragX = (swipeDragX + dragAmount).coerceIn(-swipeExitPx, 0f)
                        swipeVelocityTracker.addPosition(change.uptimeMillis, Offset(swipeDragX, 0f))
                        val armed = swipeDragX <= -swipeThresholdPx
                        if (armed && !swipeArmed) RealHaptics.tick(view)
                        swipeArmed = armed
                    }
                },
                onDragCancel = {
                    val startX = swipeDragX
                    swipeArmed = false
                    swipeScope.launch { settleSwipe(startX, commit = false, velocityX = 0f) }
                },
                onDragEnd = {
                    val startX = swipeDragX
                    val vx = swipeVelocityTracker.calculateVelocity().x
                    val commit = startX <= -swipeThresholdPx ||
                        (vx <= -swipeFlingVelocityPx && startX <= -swipeFlingMinDistancePx)
                    swipeArmed = false
                    swipeScope.launch { settleSwipe(startX, commit, vx) }
                    if (commit) {
                        swipeScope.launch {
                            if (!reduceAnimations) delay(140)
                            onSwipeLeftTo24Hour?.invoke()
                        }
                    }
                }
            )
        }
    } else Modifier

    Box(modifier.fillMaxWidth()) {
        if (swipeEnabled) {
            val revealAlpha = (-renderedSwipeX / swipeThresholdPx).coerceIn(0f, 1f)
            Text(
                text = "Move 24 hour",
                color = Color(0xFF91B9FF),
                style = RealType.cardSwipeAction,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .graphicsLayer { alpha = revealAlpha }
            )
        }
        Box(
            Modifier
            .then(sharedBoundsModifier)
            .then(arrivalModifier)
            .then(swipeGestureModifier)
            .graphicsLayer { translationX = renderedSwipeX }
            .fillMaxWidth()
            .realPetPerch(realPetReminderPerchId(reminder.id))
            .pressScale(cardInteractionSource, reduceAnimations = reduceAnimations, targetScale = 0.985f)
            .clip(shape)
            .then(
                when {
                    accentBrush != null -> Modifier.background(accentBrush)
                    LocalDesignStyle.current == com.cagri.real.ui.DesignStyle.LiquidGlass && reminder.isCompleted && !journalListMode ->
                        Modifier.liquidChrome(corners = 22.dp)
                    LocalDesignStyle.current == com.cagri.real.ui.DesignStyle.LiquidGlass ->
                        Modifier.background(LiquidGlassTokens.contentColor)
                    else -> Modifier.background(Color(0xFF141416))
                }
            )
            .then(if (isSelected) Modifier.background(selectionOverlay) else Modifier)
            .border(borderWidth, borderColor, shape)
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = if (isSelectionMode) (if (isSelected) "Seçimi kaldır" else "Seç") else "Hatırlatıcıyı aç",
                onLongClickLabel = if (!isSelectionMode) "Çoklu seçim" else null,
                onLongClick = {
                    if (!isSelectionMode) onLongClick() else onSelectToggle()
                },
                onClick = {
                    if (isSelectionMode) onSelectToggle() else onOpen()
                }
            )
    ) {
        if (categoryStripeColors != null) {
            val (stripeColor1, stripeColor2) = categoryStripeColors
            Box(
                Modifier
                    .matchParentSize()
                    .padding(end = 9.dp)
            ) {
                Row(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(5.dp)
                            .background(stripeColor1)
                    )
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(5.dp)
                            .background(stripeColor2)
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if (journalListMode && !isSelectionMode) 18.dp else 4.dp,
                    end = if (categoryStripeColors != null) 32.dp else 16.dp,
                    top = 8.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.Top
        ) {
            if (!journalListMode || isSelectionMode) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                    .then(
                        if (isSelectionMode) {
                            Modifier.clickable(
                                interactionSource = circleInteractionSource,
                                indication = null,
                                role = Role.Checkbox,
                                onClick = onSelectToggle
                            )
                        } else {
                            Modifier.toggleable(
                                value = reminder.isCompleted,
                                interactionSource = circleInteractionSource,
                                indication = null,
                                role = Role.Checkbox,
                                onValueChange = {
                                    if (reminder.hasRepeat) {
                                        RealHaptics.success(view)
                                        if (!reduceAnimations) {
                                            recurringAcknowledged = true
                                        }
                                        onToggle()
                                    } else {
                                        if (!reminder.isCompleted) {
                                            RealHaptics.success(view)
                                        }
                                        onToggle()
                                    }
                                }
                            )
                        }
                    )
                    .semantics {
                        contentDescription = if (isSelectionMode) (if (isSelected) "Seçimi kaldır" else "Seç")
                            else if (reminder.isCompleted) "Tamamlamayı geri al" else "Tamamla"
                    },
                    contentAlignment = Alignment.Center
                ) {
                    val selectionCircleBg by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF91B9FF) else Color.Transparent,
                        animationSpec = if (reduceAnimations) snap() else tween(120),
                        label = "selCircleBg"
                    )
                    val selectionCircleBorder by animateColorAsState(
                        targetValue = if (isSelected) Color.Transparent else Color(0xFF626269),
                        animationSpec = if (reduceAnimations) snap() else tween(120),
                        label = "selCircleBorder"
                    )
                    Box(
                        Modifier.size(20.dp)
                            .graphicsLayer {
                                scaleX = circlePulse.value
                                scaleY = circlePulse.value
                            }
                            .pressScale(circleInteractionSource, reduceAnimations = reduceAnimations, targetScale = 0.95f)
                            .background(
                                when {
                                    isSelectionMode -> selectionCircleBg
                                    reminder.isCompleted -> circleBgColor
                                    recurringAckAlpha.value > 0.01f -> Color(0xFF94B8F7).copy(alpha = 0.25f * recurringAckAlpha.value)
                                    else -> circleBgColor
                                },
                                CircleShape
                            )
                            .border(
                                1.5.dp,
                                when {
                                    isSelectionMode -> selectionCircleBorder
                                    reminder.isCompleted -> circleBorderColor
                                    recurringAckAlpha.value > 0.01f -> Color(0xFF94B8F7).copy(alpha = 0.7f * recurringAckAlpha.value + 0.3f * (1f - recurringAckAlpha.value))
                                    else -> circleBorderColor
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelectionMode) {
                            if (isSelected) {
                                ActionIcon(ActionGlyph.Check, Modifier.size(14.dp), Color(0xFF1B1B1F))
                            }
                        } else if (reminder.isCompleted) {
                            ActionIcon(ActionGlyph.Check, Modifier.size(14.dp), if (useDark) Color.White else Color(0xFF1B1B1F))
                        } else if (recurringAckAlpha.value > 0.01f) {
                            ActionIcon(ActionGlyph.Check, Modifier.size(14.dp), Color(0xFF94B8F7).copy(alpha = recurringAckAlpha.value))
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).padding(top = 11.dp, bottom = 10.dp, start = if (journalListMode && !isSelectionMode) 0.dp else 2.dp)) {
                Text(
                text = reminder.text,
                color = textColor,
                style = RealType.cardTitle,
                textDecoration = if (reduceAnimations && shouldStrikeThrough) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult = it },
                modifier = titleModifier.drawWithContent {
                    drawContent()
                    val progress = strikethroughProgress.value
                    if (progress > 0.001f && !reduceAnimations) {
                        val layout = textLayoutResult
                        if (layout != null) {
                            val lineCount = layout.lineCount
                            for (lineIndex in 0 until lineCount) {
                                val lineLeft = layout.getLineLeft(lineIndex)
                                val lineRight = layout.getLineRight(lineIndex)
                                val lineTop = layout.getLineTop(lineIndex)
                                val lineBottom = layout.getLineBottom(lineIndex)
                                val lineY = (lineTop + lineBottom) / 2f
                                val strikeEnd = lineLeft + (lineRight - lineLeft) * progress
                                if (strikeEnd > lineLeft) {
                                    drawLine(
                                        color = textColor,
                                        start = Offset(lineLeft, lineY),
                                        end = Offset(strikeEnd, lineY),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                }
                            }
                        }
                    }
                }
            )
            com.cagri.real.ui.share.SharedCardAttribution(reminder.sentBy)
            val cardSubtextColor = if (useDark) Color(0xFF282832) else Color(0xFFA5A5B0)
            if (reminder.isChecklist && reminder.checklistItems.isNotEmpty()) {
                val completedCount = reminder.checklistItems.count { it.completedAt != null }
                val totalCount = reminder.checklistItems.size
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$totalCount madde",
                        color = cardSubtextColor,
                        style = RealType.cardMetaSecondary
                    )
                    Text(
                        text = "$completedCount tamamlandı",
                        color = cardSubtextColor,
                        style = RealType.cardMetaSecondary
                    )
                }
            }
            val hasJournal = reminder.journal != null
            if (journalListMode) {
                reminder.journal?.mood?.let { mood ->
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MoodGlyph(mood, 13.dp, cardSubtextColor)
                        Text(
                            journalMoodLabel(mood),
                            color = cardSubtextColor,
                            style = RealType.cardMetaPrimary
                        )
                    }
                }
                reminder.voiceRecordings.forEach { recording ->
                    VoicePlaybackRow(
                        recording = recording,
                        compact = true,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
            } else {
                if (hasJournal) {
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Günlük",
                            color = cardSubtextColor,
                            style = RealType.cardMetaPrimary
                        )
                        val mood = reminder.journal.mood
                        if (mood != null) {
                            Spacer(Modifier.width(4.dp))
                            MoodGlyph(mood, 10.dp, cardSubtextColor)
                        }
                    }
                }
                if (showVoiceSummary) {
                    reminder.voiceRecordings.firstOrNull()?.let { recording ->
                        Row(
                            modifier = Modifier.padding(top = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            ActionIcon(ActionGlyph.Mic, Modifier.size(12.dp), cardSubtextColor)
                            Text(
                                buildString {
                                    append(formatVoiceDuration(recording.durationMillis))
                                    if (reminder.voiceRecordings.size > 1) append(" • ${reminder.voiceRecordings.size} kayıt")
                                },
                                color = cardSubtextColor,
                                style = RealType.cardMetaSecondary
                            )
                        }
                    }
                }
                if (!reminder.isCompleted) {
                    reminder.alertAt?.let {
                        val isOverdue = it < System.currentTimeMillis()
                        val dateColor = when {
                            useDark -> if (isOverdue) Color(0xFFC53030) else Color(0xFF1E3A8A)
                            else -> if (isOverdue) Color(0xFFF87171) else Color(0xFF94B8F7)
                        }
                        val alertText = if (reminder.hasRepeat) "${reminderDate(it)} • ${reminder.repeatRule.displayName}" else reminderDate(it)
                        Text(alertText, color = dateColor, style = RealType.cardMetaSecondary,
                            modifier = Modifier.padding(top = 5.dp))
                    } ?: reminder.plannedDateEpochDay?.let { epochDay ->
                        Text(
                            reminderPlannedDate(epochDay),
                            color = if (useDark) Color(0xFF1E3A8A) else Color(0xFF94B8F7),
                            style = RealType.cardMetaSecondary,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
                reminder.completedAt?.let {
                    Text("Tamamlandı: ${reminderDate(it)}", color = if (useDark) Color(0xFF55555E) else Color(0xFF92929A), style = RealType.cardMetaSecondary,
                        modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        if (isSelectionMode) {
            Box(
                Modifier
                    .padding(start = 10.dp, end = 2.dp, top = 11.dp)
                    .size(22.dp)
                    .background(
                        if (isSelected) Color(0xFF94B8F7) else Color.Transparent,
                        CircleShape
                    )
                    .border(
                        1.5.dp,
                        if (isSelected) Color(0xFF94B8F7) else Color.White.copy(alpha = 0.30f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    ActionIcon(ActionGlyph.Check, Modifier.size(13.dp), Color(0xFF0F172A))
                }
            }
        }
    }
    }
}
}

/** Outer ring of every starred card (list card, detail card, picker preview). */
internal val StarCardBorderColor = Color.White.copy(alpha = 0.85f)
