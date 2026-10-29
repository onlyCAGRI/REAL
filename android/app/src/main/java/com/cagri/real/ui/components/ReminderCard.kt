package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.drawscope.rotate
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
import androidx.compose.foundation.layout.widthIn
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
    /** Swipe right: move the card into 24 hour (or complete it, see [swipeRightLabel]). */
    onSwipeRightTo24Hour: (() -> Unit)? = null,
    swipeRightLabel: String = "Move 24 hour",
    /** True when the card stays in the list after the right swipe, so it slides back into place. */
    swipeRightKeepsCard: Boolean = true,
    /** Sade kartlar (Ayarlar, deneysel): no completion circle; the text moves left. */
    plainStyle: Boolean = false,
    /** Swipe left: delete (the caller shows "Geri al"). */
    onSwipeLeftDelete: (() -> Unit)? = null,
    /** The left swipe only takes the card off this list (a phone-calendar event that ended): grey, "Gizle". */
    swipeLeftHides: Boolean = false,
    /** Home search results: the card's category name in a small pill (like the category descriptions). */
    categoryLabel: CustomCategory? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val shape = ReminderCardShape
    val arrivalProgress = remember(reminder.id) {
        Animatable(if (isNewlyCreated && !reduceAnimations) 0f else 1f)
    }
    LaunchedEffect(reminder.id, isNewlyCreated) {
        if (isNewlyCreated && !reduceAnimations) {
            arrivalProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 320,
                    easing = RealMotion.Emphasized
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
    val useDark = reminderUsesDarkText(reminder.useDarkText, !journalListMode && reminder.isCompleted,
        reminder.accentStart, reminder.accentEnd, reminder.customSolidColorArgb)
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
            circlePulse.animateTo(1.0f, tween(120, easing = RealMotion.Standard))
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
                    circlePulse.animateTo(1.0f, tween(100, easing = RealMotion.Standard))
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
    val shouldStrikeThrough = !journalListMode && reminder.isCompleted && !com.cagri.real.ui.hasCompletionNote(reminder)
    val strikethroughProgress = remember(reminder.id) {
        Animatable(if (shouldStrikeThrough) 1f else 0f)
    }
    LaunchedEffect(shouldStrikeThrough) {
        if (!reduceAnimations) {
            strikethroughProgress.animateTo(
                targetValue = if (shouldStrikeThrough) 1f else 0f,
                animationSpec = tween(180, easing = RealMotion.Emphasized)
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
                boundsTransform = { _, _ -> tween(320, easing = RealMotion.Standard) },
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
    // Sade kartlar: a completed card swipes right to undo ("Geri al").
    val swipeRightOn = onSwipeRightTo24Hour != null && (!reminder.isCompleted || plainStyle)
    val swipeLeftOn = onSwipeLeftDelete != null
    val swipeEnabled = (swipeRightOn || swipeLeftOn) && !isSelectionMode && !journalListMode
    val swipeScope = rememberCoroutineScope()
    val swipeThresholdPx = with(density) { 82.dp.toPx() }
    val swipeExitPx = with(density) { 900.dp.toPx() }
    val swipeAnim = remember(reminder.id) { Animatable(0f) }
    var swipeDragX by remember(reminder.id) { mutableFloatStateOf(0f) }
    var swipeDragging by remember(reminder.id) { mutableStateOf(false) }
    var swipeArmed by remember(reminder.id) { mutableStateOf(false) }
    // The drag the finger made; past the threshold the card follows it with rubber-band resistance.
    var swipeRawX by remember(reminder.id) { mutableFloatStateOf(0f) }
    // Kept on while a committed card leaves, so its action icon keeps riding the card's edge out.
    var swipeCommitting by remember(reminder.id) { mutableStateOf(false) }
    val swipeArmedProgress = animateFloatAsState(
        if (swipeArmed || swipeCommitting) 1f else 0f,
        if (reduceAnimations) snap() else androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "swipeArmed"
    )
    val swipeRubberPx = with(density) { 140.dp.toPx() }
    fun rubberSwipe(raw: Float): Float {
        val d = kotlin.math.abs(raw)
        if (d <= swipeThresholdPx) return raw
        val over = d - swipeThresholdPx
        val eased = swipeThresholdPx + swipeRubberPx * (1f - 1f / (over / swipeRubberPx * 0.9f + 1f))
        return if (raw < 0f) -eased else eased
    }
    // Which way this drag may go: a card is swiped from its edge (delete from the right side, complete / 24 hour from
    // the left side); a drag starting in the middle does not move it, so a scroll or a tap cannot throw a card away.
    var swipeMayLeft by remember(reminder.id) { mutableStateOf(false) }
    var swipeMayRight by remember(reminder.id) { mutableStateOf(false) }
    val swipeVelocityTracker = remember(reminder.id) { VelocityTracker() }
    val swipeFlingVelocityPx = with(density) { 520.dp.toPx() }
    val swipeFlingMinDistancePx = with(density) { 28.dp.toPx() }
    // Read only inside graphicsLayer lambdas so a swipe redraws the card instead of recomposing it every frame.
    fun renderedSwipeX(): Float = if (swipeDragging) swipeDragX else swipeAnim.value
    suspend fun settleSwipe(startX: Float, commit: Boolean, velocityX: Float) {
        swipeAnim.snapTo(startX)
        swipeDragging = false
        swipeDragX = 0f
        val target = if (commit) (if (startX < 0f) -swipeExitPx else swipeExitPx) else 0f
        if (reduceAnimations) {
            swipeAnim.snapTo(target)
        } else {
            swipeAnim.animateTo(target, RealMotion.interactive(), initialVelocity = velocityX)
        }
    }
    val swipeGestureModifier = if (swipeEnabled) {
        Modifier.pointerInput(reminder.id, onSwipeRightTo24Hour, onSwipeLeftDelete, swipeRightOn, swipeLeftOn, reduceAnimations) {
            detectHorizontalDragGestures(
                onDragStart = { start ->
                    val at = start.x / size.width.coerceAtLeast(1)
                    swipeMayLeft = swipeLeftOn && at >= 0.6f
                    swipeMayRight = swipeRightOn && at <= 0.4f
                    swipeDragging = true
                    swipeDragX = swipeAnim.value
                    swipeRawX = swipeAnim.value
                    swipeArmed = false
                    swipeCommitting = false
                    swipeVelocityTracker.resetTracking()
                },
                onHorizontalDrag = { change, dragAmount ->
                    val raw = (swipeRawX + dragAmount)
                        .coerceIn(if (swipeMayLeft) -swipeExitPx else 0f, if (swipeMayRight) swipeExitPx else 0f)
                    val next = rubberSwipe(raw)
                    if (next != swipeDragX || swipeDragX != 0f) {
                        change.consume()
                        swipeRawX = raw
                        swipeDragX = next
                        swipeVelocityTracker.addPosition(change.uptimeMillis, Offset(swipeDragX, 0f))
                        val armed = kotlin.math.abs(swipeDragX) >= swipeThresholdPx
                        // A tick each way across the line, as iOS does: the action is on, then off again.
                        if (armed != swipeArmed) RealHaptics.tick(view)
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
                    val left = startX < 0f
                    val commit = kotlin.math.abs(startX) >= swipeThresholdPx ||
                        (kotlin.math.abs(vx) >= swipeFlingVelocityPx && (vx < 0f) == left &&
                            kotlin.math.abs(startX) >= swipeFlingMinDistancePx)
                    swipeArmed = false
                    swipeCommitting = commit
                    if (commit && !left && swipeRightKeepsCard) {
                        // Move to 24 hour keeps the card here: act, then it slides back in (no bounce).
                        swipeScope.launch {
                            swipeAnim.snapTo(startX)
                            swipeDragging = false
                            swipeDragX = 0f
                            onSwipeRightTo24Hour?.invoke()
                            swipeCommitting = false
                            if (reduceAnimations) swipeAnim.snapTo(0f)
                            else swipeAnim.animateTo(0f, tween(320, easing = RealMotion.Emphasized))
                        }
                    } else swipeScope.launch { settleSwipe(startX, commit, vx) }
                    if (commit && (left || !swipeRightKeepsCard)) {
                        swipeScope.launch {
                            if (!reduceAnimations) delay(140)
                            if (left) onSwipeLeftDelete?.invoke() else onSwipeRightTo24Hour?.invoke()
                        }
                    }
                }
            )
        }
    } else Modifier

    Box(modifier.fillMaxWidth()) {
        if (swipeEnabled) {
            // The action behind the card (iOS Mail / Reminders): a solid pill that grows out of the edge the card
            // leaves, its icon riding along; past the line it deepens to full colour and the icon jumps to the card.
            val rightColor = if (plainStyle && !reminder.isCompleted) SwipeCompleteColor else SwipeUndoColor
            Box(Modifier.matchParentSize().drawBehind {
                val x = renderedSwipeX()
                drawSwipeAction(x, if (x < 0f) (if (swipeLeftHides) SwipeHideColor else SwipeDeleteColor) else rightColor, swipeArmedProgress.value,
                    22.dp.toPx(), SwipeActionGap.toPx())
            })
            if (swipeRightOn) SwipeActionIcon(
                glyph = if (reminder.isCompleted) ActionGlyph.Undo else if (plainStyle) ActionGlyph.Check else ActionGlyph.Calendar,
                label = swipeRightLabel,
                fromStart = true,
                x = ::renderedSwipeX,
                armed = { swipeArmedProgress.value },
                modifier = Modifier.align(Alignment.CenterStart)
            )
            if (swipeLeftOn) SwipeActionIcon(
                glyph = if (swipeLeftHides) ActionGlyph.Close else ActionGlyph.Delete,
                label = if (swipeLeftHides) t("Gizle") else t("Sil"),
                fromStart = false,
                x = ::renderedSwipeX,
                armed = { swipeArmedProgress.value },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        Box(
            Modifier
            .then(sharedBoundsModifier)
            .then(arrivalModifier)
            .then(swipeGestureModifier)
            .graphicsLayer { translationX = renderedSwipeX() }
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
            // Kontrol ettir: a slow light from the right edge until this viewer opens the card.
            .checkGlow(CheckRequests.glow(androidx.compose.ui.platform.LocalContext.current, reminder,
                com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.id,
                categoryColor = customCategory?.takeIf { it.id == reminder.customCategoryId }
                    ?.let { cat -> cat.customSolidColorArgb?.let { Color(it) } ?: resolveCategoryStripeColors(cat).first }), reduceAnimations)
            .border(borderWidth, borderColor, shape)
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = null /* pressScale already gives the press feedback */,
                role = Role.Button,
                onClickLabel = if (isSelectionMode) (if (isSelected) t("Seçimi kaldır") else t("Seç")) else t("Hatırlatıcıyı aç"),
                onLongClickLabel = if (!isSelectionMode) t("Çoklu seçim") else null,
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
        val plain = plainStyle && !isSelectionMode
        // Ortak kategori: a card someone else added carries just their name, bottom right.
        val myUid = remember { com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.id }
        val author = reminder.sharedAuthorName?.trim()?.takeIf { it.isNotEmpty() && reminder.sharedAuthorUid != null && reminder.sharedAuthorUid != myUid }
        if (author != null) {
            Text(
                author, color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomEnd).widthIn(max = 140.dp)
                    .padding(end = if (categoryStripeColors != null) 32.dp else 16.dp, bottom = 8.dp)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if ((journalListMode || plain) && !isSelectionMode) 18.dp else 4.dp,
                    end = if (categoryStripeColors != null) 32.dp else 16.dp,
                    top = 8.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.Top
        ) {
            if ((!journalListMode && !plain) || isSelectionMode) {
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
                        contentDescription = if (isSelectionMode) (if (isSelected) t("Seçimi kaldır") else t("Seç"))
                            else if (reminder.isCompleted) t("Tamamlamayı geri al") else t("Tamamla")
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
            Column(Modifier.weight(1f).padding(top = 11.dp, bottom = 10.dp, start = if ((journalListMode || plain) && !isSelectionMode) 0.dp else 2.dp)) {
                Text(
                text = reminder.text,
                color = textColor,
                style = RealType.cardTitle,
                textDecoration = if (reduceAnimations && shouldStrikeThrough) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier.drawWithContent {
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
                            t("Günlük"),
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
                                    if (reminder.voiceRecordings.size > 1) append(t(" • {0} kayıt", reminder.voiceRecordings.size))
                                },
                                color = cardSubtextColor,
                                style = RealType.cardMetaSecondary
                            )
                        }
                    }
                }
                // One quiet line: date · place (grey pin, or the Varınca / Ayrılınca arrow) · ✓ 2/5 for a checklist.
                // Photos, voice and stacks stay on the detail page.
                val trigger = reminder.locationTrigger.takeUnless { reminder.isCompleted }
                val plainPlace = reminder.location.takeIf { trigger == null && !reminder.isCompleted && it?.latitude != null }
                    ?.let { reminderLocationLabel(it) }
                val checklist = reminder.checklistItems.takeIf { reminder.isChecklist && it.isNotEmpty() }
                val dated = !reminder.isCompleted && (reminder.alertAt != null || reminder.plannedDateEpochDay != null)
                if (dated || trigger != null || plainPlace != null || checklist != null) Row(Modifier.padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (dated) reminder.alertAt?.let {
                        val isOverdue = it < System.currentTimeMillis()
                        val dateColor = when {
                            useDark -> if (isOverdue) Color(0xFFC53030) else Color(0xFF1E3A8A)
                            else -> if (isOverdue) Color(0xFFF87171) else Color(0xFF94B8F7)
                        }
                        val alertText = if (reminder.hasRepeat) "${reminderDate(it)} • ${reminder.repeatRule.displayName}" else reminderDate(it)
                        Text(alertText, color = dateColor, style = RealType.cardMetaSecondary, maxLines = 1)
                    } ?: reminder.plannedDateEpochDay?.let { epochDay ->
                        Text(reminderPlannedDate(epochDay), color = if (useDark) Color(0xFF1E3A8A) else Color(0xFF94B8F7),
                            style = RealType.cardMetaSecondary, maxLines = 1)
                    }
                    // Location permission (or "Her zaman") taken back: the trigger can never fire, so the card says so.
                    val triggerBlocked = trigger != null && androidx.compose.ui.platform.LocalContext.current.let {
                        !com.cagri.real.notifications.LocationReminders.hasLocation(it) || !com.cagri.real.notifications.LocationReminders.hasBackground(it)
                    }
                    if (trigger != null) {
                        val tone = if (triggerBlocked) Color(0xFFF87171) else if (trigger.onExit) Color(0xFFFFB86F) else Color(0xFF5DCAA5)
                        PlaceTriggerGlyph(trigger.onExit, tone, Modifier.size(12.dp))
                        if (triggerBlocked) Text(t("Konum izni yok"), color = tone, style = RealType.cardMetaSecondary, maxLines = 1)
                    } else if (plainPlace != null) {
                        ActionIcon(ActionGlyph.Pin, Modifier.size(12.dp), cardSubtextColor)
                    }
                    (trigger?.placeName?.takeIf { it.isNotBlank() } ?: plainPlace)?.let { name ->
                        Text(name, color = cardSubtextColor, style = RealType.cardMetaSecondary, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                    checklist?.let { items ->
                        if (dated || trigger != null || plainPlace != null) Text("·", color = cardSubtextColor, style = RealType.cardMetaSecondary)
                        ActionIcon(ActionGlyph.Checklist, Modifier.size(12.dp), cardSubtextColor)
                        Text("${items.count { it.completedAt != null }}/${items.size}", color = cardSubtextColor,
                            style = RealType.cardMetaSecondary, maxLines = 1)
                    }
                }
                reminder.completedAt?.let {
                    Text(t("Tamamlandı: {0}", reminderDate(it)), color = if (useDark) Color(0xFF55555E) else Color(0xFF92929A), style = RealType.cardMetaSecondary,
                        modifier = Modifier.padding(top = 5.dp))
                }
                categoryLabel?.let { c ->
                    // The category colour, lifted a little so dark ones stay readable on the card.
                    val tint = if (useDark) textColor else androidx.compose.ui.graphics.lerp(
                        ReminderColorChoices.getOrNull(c.accentStart)?.color ?: Color(0xFFA5A5B0), Color.White, 0.3f)
                    CategoryPill(c.name, tint, Modifier.padding(top = 8.dp).widthIn(max = 200.dp))
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

/** Varınca: a navigation arrow pointing in; Ayrılınca: the same arrow turned around. */
@Composable
internal fun PlaceTriggerGlyph(onExit: Boolean, color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.semantics { contentDescription = if (onExit) t("Ayrılınca hatırlatır") else t("Varınca hatırlatır") }) {
        val w = size.width; val h = size.height
        val arrow = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * .92f, h * .08f); lineTo(w * .08f, h * .45f); lineTo(w * .48f, h * .52f); lineTo(w * .55f, h * .92f); close()
        }
        if (onExit) rotate(180f) { drawPath(arrow, color) } else drawPath(arrow, color)
    }
}

private val SwipeCompleteColor = Color(0xFF30D158)
private val SwipeUndoColor = Color(0xFF0A84FF)
private val SwipeDeleteColor = Color(0xFFFF453A)
private val SwipeHideColor = Color(0xFF636366)
/** Width of one action "button": the icon waits in its middle until the swipe passes the line. */
private val SwipeActionWidth = 76.dp
private val SwipeActionGap = 6.dp

/**
 * The revealed action behind a swiped card: a solid rounded pill from the card's edge up to a small gap before the
 * card, a little deeper until the swipe will act ([armed] 0 to 1). No light, no glow.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSwipeAction(
    x: Float, color: Color, armed: Float, corner: Float, gap: Float
) {
    val w = kotlin.math.abs(x) - gap
    if (w <= 0f) return
    val fill = androidx.compose.ui.graphics.lerp(color, Color.Black, 0.22f * (1f - armed.coerceIn(0f, 1f)))
    val r = kotlin.math.min(corner, w / 2f)
    drawRoundRect(
        fill,
        topLeft = Offset(if (x < 0f) size.width - w else 0f, 0f),
        size = androidx.compose.ui.geometry.Size(w, size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
    )
}

/**
 * The white icon on the revealed pill: it fades and grows in over the first dp, waits in the middle of the first
 * [SwipeActionWidth], and once the swipe will act springs over to ride right against the card's edge.
 */
@Composable
private fun SwipeActionIcon(
    glyph: ActionGlyph, label: String, fromStart: Boolean, x: () -> Float, armed: () -> Float, modifier: Modifier
) {
    Box(
        modifier
            .size(SwipeActionWidth, 44.dp)
            .graphicsLayer {
                val buttonPx = SwipeActionWidth.toPx()
                val shown = if (fromStart) x().coerceAtLeast(0f) else (-x()).coerceAtLeast(0f)
                val w = (shown - SwipeActionGap.toPx()).coerceAtLeast(0f)
                val a = armed()
                val resting = kotlin.math.min(w, buttonPx) / 2f
                val riding = w - buttonPx / 2f
                val dx = resting + (riding - resting).coerceAtLeast(0f) * a - buttonPx / 2f
                translationX = if (fromStart) dx else -dx
                alpha = ((w - 8.dp.toPx()) / (buttonPx * 0.45f)).coerceIn(0f, 1f)
                val s = (0.55f + 0.45f * (w / buttonPx).coerceIn(0f, 1f)) * (1f + 0.1f * a)
                scaleX = s; scaleY = s
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        ActionIcon(glyph, Modifier.size(22.dp), Color.White)
    }
}
