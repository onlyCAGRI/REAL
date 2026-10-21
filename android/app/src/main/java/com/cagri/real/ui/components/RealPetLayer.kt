package com.cagri.real.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cagri.real.data.ReminderItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

/**
 * Root companion layer for REAL.
 *
 * Coordinates pet presence, reactive events, gesture inputs, and visual placement
 * across modular scenario, action, and rendering engines.
 */
@Composable
fun RealPetLayer(
    reduceAnimations: Boolean,
    surfaceActive: Boolean = true,
    onCreatePersistentCard: (String) -> Unit = {},
    // Read-only: used only to notice a just-completed reminder (celebration) and to find
    // an overdue one to nudge. Never mutated from here.
    reminders: List<ReminderItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val isForeground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val petSize = 114.dp
        val petSizePx = with(density) { petSize.toPx() }
        val sidePaddingPx = with(density) { 8.dp.toPx() }
        val groundPaddingPx = with(density) { 5.dp.toPx() }
        val perchSinkPx = with(density) { 6.dp.toPx() }
        val snapDistancePx = with(density) { 116.dp.toPx() }
        val navigationBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val maxX = (widthPx - petSizePx - sidePaddingPx).coerceAtLeast(sidePaddingPx)
        val groundY = (heightPx - navigationBottomPx - petSizePx - groundPaddingPx)
            .coerceAtLeast(0f)

        val x = remember(widthPx) { Animatable(sidePaddingPx) }
        val y = remember(heightPx) { Animatable(groundY) }
        val motionProgress = remember { Animatable(0f) }
        val sleepPerchFractionState = rememberSaveable { mutableFloatStateOf(0.5f) }
        val perchProgress = remember { Animatable(sleepPerchFractionState.floatValue) }

        val sleepUntilState = rememberSaveable { mutableLongStateOf(0L) }
        val sleepXState = rememberSaveable { mutableFloatStateOf(sidePaddingPx) }
        val sleepYState = rememberSaveable { mutableFloatStateOf(groundY) }
        val stateState = remember {
            mutableStateOf(
                if (sleepUntilState.longValue > SystemClock.elapsedRealtime()) RealPetState.Sleeping else RealPetState.Idle
            )
        }
        val meowCaptionState = remember { mutableStateOf<String?>(null) }
        val sleepPoseIndexState = rememberSaveable { mutableIntStateOf(-1) }
        val sitFrameIndexState = remember { mutableIntStateOf(0) }

        val heartBursts = remember { mutableStateListOf<RealPetHeartBurst>() }
        val nextHeartIdState = remember { mutableIntStateOf(0) }
        val facingState = remember { mutableStateOf(RealPetFacing.Left) }
        val motionState = remember { mutableStateOf(RealPetMotion.None) }
        val jumpVisualState = remember { mutableStateOf(RealPetJumpVisual.LegacyHop) }
        val motionStartXState = remember { mutableFloatStateOf(sidePaddingPx) }
        val motionStartYState = remember { mutableFloatStateOf(groundY) }
        val motionTargetXState = remember { mutableFloatStateOf(sidePaddingPx) }
        val motionTargetYState = remember { mutableFloatStateOf(groundY) }
        val motionHeightPxState = remember { mutableFloatStateOf(with(density) { 52.dp.toPx() }) }
        val motionTargetPerchIdState = remember { mutableStateOf<String?>(null) }
        val latestCreatePersistentCard = rememberUpdatedState(onCreatePersistentCard)
        val latestSurfaceActive = rememberUpdatedState(surfaceActive)

        val anchorIdState = rememberSaveable { mutableStateOf<String?>(null) }
        val lastAnchorXState = rememberSaveable { mutableFloatStateOf(sidePaddingPx) }
        val lastAnchorYState = rememberSaveable { mutableFloatStateOf(groundY) }
        val isDraggingState = remember { mutableStateOf(false) }

        val motionMutex = remember { Mutex() }
        val activeMotionOwnerState = remember { mutableStateOf<String?>(null) }
        val activeScenarioJobState = remember { mutableStateOf<Job?>(null) }
        val activeScenarioKindState = remember { mutableStateOf<RealPetGuestScenario?>(null) }
        val pendingScenarioResumeState = remember { mutableStateOf<RealPetGuestScenario?>(null) }
        val scenarioResumeNotBeforeState = remember { mutableLongStateOf(0L) }
        val activeExclusiveJobState = remember { mutableStateOf<Job?>(null) }
        val activeExclusivePriorityState = remember { mutableStateOf(RealPetPriority.Autonomous) }
        val doubleTapSitActiveState = remember { mutableStateOf(false) }
        val sitUntilDeadlineState = remember { mutableLongStateOf(0L) }

        val dragXState = remember { mutableFloatStateOf(sidePaddingPx) }
        val dragYState = remember { mutableFloatStateOf(groundY) }
        val peekActiveState = remember { mutableStateOf(false) }
        val peekFleeRequestedState = remember { mutableStateOf(false) }

        val random = remember { Random(System.nanoTime().toInt()) }

        val attentionActiveState = remember { mutableStateOf(false) }
        val attentionTapCountState = remember { mutableIntStateOf(0) }
        val attentionTapGoalState = remember { mutableIntStateOf(3) }
        var plannedOpeningScenario by remember { mutableStateOf<RealPetGuestScenario?>(null) }
        var plannedOpeningPerchId by remember { mutableStateOf<String?>(null) }
        var openingSleepScenarioActive by remember { mutableStateOf(false) }

        val feedingProgress = remember { Animatable(0f) }
        var feedingCardTitle by remember { mutableStateOf<String?>(null) }
        var feedingStartX by remember { mutableFloatStateOf(0f) }
        var feedingStartY by remember { mutableFloatStateOf(0f) }
        var feedingEndX by remember { mutableFloatStateOf(0f) }
        var feedingEndY by remember { mutableFloatStateOf(0f) }

        val view = LocalView.current
        val presence = RealPetRuntime.presence
        val latestReminders = rememberUpdatedState(reminders)

        val anchorId = anchorIdState.value
        val motion = motionState.value
        val isDragging = isDraggingState.value
        val sleepUntil = sleepUntilState.longValue
        val sleepX = sleepXState.floatValue
        val sleepY = sleepYState.floatValue
        val lastAnchorX = lastAnchorXState.floatValue
        val lastAnchorY = lastAnchorYState.floatValue
        val dragX = dragXState.floatValue
        val dragY = dragYState.floatValue
        val motionStartX = motionStartXState.floatValue
        val motionStartY = motionStartYState.floatValue
        val motionTargetX = motionTargetXState.floatValue
        val motionTargetY = motionTargetYState.floatValue
        val motionHeightPx = motionHeightPxState.floatValue
        val state = stateState.value
        val facing = facingState.value
        val jumpVisual = jumpVisualState.value
        val sleepPoseIndex = sleepPoseIndexState.intValue
        val sitFrameIndex = sitFrameIndexState.intValue
        val meowCaption = meowCaptionState.value

        val anchorPosition = if (anchorId != null) {
            val bounds = RealPetRuntime.perches[anchorId]
            if (bounds != null) {
                if (anchorId == REAL_PET_PERCH_AVATAR) {
                    Offset(
                        x = (bounds.center.x - petSizePx * 0.68f).coerceIn(sidePaddingPx, maxX),
                        y = (bounds.center.y - petSizePx * 0.69f).coerceIn(-petSizePx * 0.20f, groundY)
                    )
                } else {
                    val range = realPetPerchRange(bounds.left, bounds.right, petSizePx, sidePaddingPx, maxX)
                    val parentCategoryBounds = realPetParentCategoryPerchId(anchorId)
                        ?.let { RealPetRuntime.perches[it] }
                    val posY = if (anchorId == REAL_PET_PERCH_COMPOSER_TEXT || anchorId == REAL_PET_PERCH_COMPOSER_MIC) {
                        (bounds.center.y - petSizePx / 2f).coerceIn(-petSizePx * 0.12f, groundY)
                    } else {
                        (parentCategoryBounds?.top ?: bounds.top) - petSizePx + perchSinkPx
                    }
                    Offset(
                        x = range.start + (range.endInclusive - range.start) * perchProgress.value.coerceIn(0f, 1f),
                        y = posY
                    )
                }
            } else null
        } else null

        val p = if (motion != RealPetMotion.None) motionProgress.value else 0f
        val motionX = motionStartX + (motionTargetX - motionStartX) * p
        val motionLinearY = motionStartY + (motionTargetY - motionStartY) * p
        val arcY = if (motion == RealPetMotion.Jump) {
            -4f * motionHeightPx * p * (1f - p)
        } else 0f

        val baseX = when {
            isDragging -> dragX
            motion != RealPetMotion.None -> motionX
            anchorId != null -> anchorPosition?.x ?: lastAnchorX
            sleepUntil > 0L -> sleepX
            else -> x.value
        }
        val baseY = when {
            isDragging -> dragY
            motion != RealPetMotion.None -> motionLinearY + arcY
            anchorId != null -> anchorPosition?.y ?: lastAnchorY
            sleepUntil > 0L -> sleepY
            else -> y.value
        }

        val latestRenderX = rememberUpdatedState(baseX)
        val latestRenderY = rememberUpdatedState(baseY)
        val visibleState = if (sleepUntil > 0L) RealPetState.Sleeping else state
        val spriteScale = when (visibleState) {
            RealPetState.Sleeping -> 40f / 50f
            RealPetState.Idle -> 60f / 50f
            RealPetState.Sitting -> 0.60f
            RealPetState.Meowing -> 65f / 50f
            else -> 1f
        }
        if (isForeground && surfaceActive) {
            RealPetMeowSound(state = visibleState)
        }

        val rawHeartY = if (visibleState == RealPetState.Sleeping) {
            petSizePx * 0.60f
        } else {
            petSizePx * (246f / 256f - (220f * 407f / 805f / 256f) * spriteScale) - with(density) { 3.dp.toPx() }
        }
        val rawHeartX = when {
            visibleState == RealPetState.Sleeping -> petSizePx * 0.50f
            facing == RealPetFacing.Left -> petSizePx * 0.38f
            else -> petSizePx * 0.62f
        }
        val latestHeartOriginY = rememberUpdatedState(baseY + rawHeartY)
        val latestHeartOriginX = rememberUpdatedState(baseX + rawHeartX)

        val actionScope = RealPetActionScope(
            density = density,
            view = view,
            coroutineScope = scope,
            random = random,
            reduceAnimations = reduceAnimations,
            petSizePx = petSizePx,
            sidePaddingPx = sidePaddingPx,
            groundPaddingPx = groundPaddingPx,
            perchSinkPx = perchSinkPx,
            snapDistancePx = snapDistancePx,
            navigationBottomPx = navigationBottomPx,
            widthPx = widthPx,
            heightPx = heightPx,
            maxX = maxX,
            groundY = groundY,
            x = x,
            y = y,
            motionProgress = motionProgress,
            perchProgress = perchProgress,
            sleepPerchFractionState = sleepPerchFractionState,
            sleepUntilState = sleepUntilState,
            sleepXState = sleepXState,
            sleepYState = sleepYState,
            stateState = stateState,
            meowCaptionState = meowCaptionState,
            sleepPoseIndexState = sleepPoseIndexState,
            sitFrameIndexState = sitFrameIndexState,
            heartBursts = heartBursts,
            nextHeartIdState = nextHeartIdState,
            facingState = facingState,
            motionState = motionState,
            jumpVisualState = jumpVisualState,
            motionStartXState = motionStartXState,
            motionStartYState = motionStartYState,
            motionTargetXState = motionTargetXState,
            motionTargetYState = motionTargetYState,
            motionHeightPxState = motionHeightPxState,
            motionTargetPerchIdState = motionTargetPerchIdState,
            anchorIdState = anchorIdState,
            lastAnchorXState = lastAnchorXState,
            lastAnchorYState = lastAnchorYState,
            isDraggingState = isDraggingState,
            dragXState = dragXState,
            dragYState = dragYState,
            peekActiveState = peekActiveState,
            peekFleeRequestedState = peekFleeRequestedState,
            doubleTapSitActiveState = doubleTapSitActiveState,
            sitUntilDeadlineState = sitUntilDeadlineState,
            attentionActiveState = attentionActiveState,
            attentionTapCountState = attentionTapCountState,
            attentionTapGoalState = attentionTapGoalState,
            motionMutex = motionMutex,
            activeMotionOwnerState = activeMotionOwnerState,
            activeScenarioJobState = activeScenarioJobState,
            activeScenarioKindState = activeScenarioKindState,
            pendingScenarioResumeState = pendingScenarioResumeState,
            scenarioResumeNotBeforeState = scenarioResumeNotBeforeState,
            activeExclusiveJobState = activeExclusiveJobState,
            activeExclusivePriorityState = activeExclusivePriorityState,
            latestRenderX = latestRenderX,
            latestRenderY = latestRenderY,
            latestHeartOriginX = latestHeartOriginX,
            latestHeartOriginY = latestHeartOriginY,
            latestCreatePersistentCard = latestCreatePersistentCard,
            reminders = { latestReminders.value }
        )

        LaunchedEffect(surfaceActive) {
            if (!surfaceActive) {
                // Non-pet pages hide the layer and pause a casual story without forgetting it.
                actionScope.requestScenarioResumeAfterInterruption(pauseMs = 0L)
            }
        }

        LaunchedEffect(isForeground) {
            if (!isForeground) {
                activeScenarioJobState.value?.cancel()
                activeExclusiveJobState.value?.cancel()
                x.stop()
                y.stop()
                motionProgress.stop()
                feedingProgress.stop()
                feedingCardTitle = null
                meowCaptionState.value = null
                RealPetRuntime.clearComposerTyping()
            }
        }

        if (isForeground) {
        // Guest mode is the main experience: every app session gets one launch plan.
        LaunchedEffect(Unit) {
            if (RealPetRuntime.presence == RealPetPresence.Hidden && RealPetRuntime.canStartCasualVisit()) {
                val arrivalDelay = realPetGuestArrivalDelayMs(random.nextFloat(), random.nextFloat())
                if (arrivalDelay != null) {
                    delay(arrivalDelay)
                    if (arrivalDelay == 0L) delay(180L) // let first-screen perches register
                    if (RealPetRuntime.presence == RealPetPresence.Hidden) {
                        val offscreenRestTargets = RealPetRuntime.perches.entries
                            .filter { (id, bounds) ->
                                id != REAL_PET_PERCH_AVATAR &&
                                    ":pill:" !in id &&
                                    id != REAL_PET_PERCH_COMPOSER_TEXT &&
                                    id != REAL_PET_PERCH_COMPOSER_MIC &&
                                    (bounds.bottom < 0f || bounds.top > heightPx)
                            }
                            .map { it.key }
                        if (offscreenRestTargets.isNotEmpty() && random.nextFloat() < 0.24f) {
                            plannedOpeningScenario = RealPetGuestScenario.AlreadySleeping
                            plannedOpeningPerchId = offscreenRestTargets[random.nextInt(offscreenRestTargets.size)]
                        } else {
                            plannedOpeningScenario = null
                            plannedOpeningPerchId = null
                        }
                        RealPetRuntime.requestVisit(
                            RealPetVisitReason.CasualVisit,
                            realPetVisitDurationMs(random.nextFloat())
                        )
                    }
                }
            }
        }

        // Detects completedAt null -> non-null (single celebration), all completed (Inbox Zero),
        // and newly added cards so the cat can react accordingly.
        var previousReminders by remember { mutableStateOf(reminders) }
        LaunchedEffect(reminders) {
            if (previousReminders !== reminders) {
                val previousCompletedIds = previousReminders
                    .filter { it.completedAt != null }
                    .mapTo(mutableSetOf()) { it.id }
                val newlyCompleted = reminders.firstOrNull { current ->
                    current.completedAt != null &&
                        current.id !in previousCompletedIds &&
                        previousReminders.any { it.id == current.id }
                }

                val hadActiveBefore = previousReminders.any { it.completedAt == null }
                val hasActiveNow = reminders.any { it.completedAt == null }
                val isAllClear = hadActiveBefore && !hasActiveNow && reminders.isNotEmpty()

                val previousIds = previousReminders.mapTo(mutableSetOf()) { it.id }
                val newlyAdded = reminders.firstOrNull { it.id !in previousIds }

                previousReminders = reminders
                if (isAllClear) {
                    RealPetRuntime.requestAllClearCelebration()
                } else if (newlyCompleted != null) {
                    RealPetRuntime.requestCelebration(newlyCompleted.id)
                } else if (newlyAdded != null && previousReminders.isNotEmpty()) {
                    RealPetRuntime.noteNewCardAdded(newlyAdded.id)
                }
            }
        }

        LaunchedEffect(anchorId, anchorPosition?.x, anchorPosition?.y) {
            val position = anchorPosition ?: return@LaunchedEffect
            lastAnchorXState.floatValue = position.x
            lastAnchorYState.floatValue = position.y
        }

        // Missing-Anchor Watchdog: falls to nearest valid surface or floor on surface loss.
        LaunchedEffect(anchorId, anchorPosition?.x, anchorPosition?.y, RealPetRuntime.perches.size, widthPx, heightPx) {
            val missingAnchor = anchorId ?: return@LaunchedEffect
            if (anchorPosition != null || isDragging || motionMutex.isLocked) return@LaunchedEffect
            delay(90L)
            if (RealPetRuntime.perches[missingAnchor] != null || actionScope.anchorId != missingAnchor || actionScope.isDragging) {
                return@LaunchedEffect
            }

            val lastBoxWasOffscreen =
                actionScope.lastAnchorY + petSizePx <= 0f || actionScope.lastAnchorY >= heightPx
            if (lastBoxWasOffscreen) return@LaunchedEffect

            actionScope.withExclusivePetMotion("missingAnchorDrop") {
                if (RealPetRuntime.perches[missingAnchor] != null || actionScope.anchorId != missingAnchor || actionScope.isDragging) {
                    return@withExclusivePetMotion
                }
                val startX = actionScope.lastAnchorX.coerceIn(sidePaddingPx, maxX)
                val startY = actionScope.lastAnchorY.coerceIn(-petSizePx * 0.25f, groundY)
                val landing = actionScope.nearestDropLanding(startX, startY, missingAnchor)
                val targetId = landing?.first
                val target = landing?.second ?: Offset(startX, groundY)

                actionScope.performDropMotion(
                    targetX = target.x,
                    targetY = target.y,
                    targetPerchId = targetId,
                    startX = startX,
                    startY = startY
                )
            }
        }

        // Explicit Surface Drop Request (e.g. Fullscreen Photo Viewer in Detail)
        val surfaceDropRequestToken = RealPetRuntime.surfaceDropRequestToken
        LaunchedEffect(surfaceDropRequestToken, widthPx, heightPx) {
            if (surfaceDropRequestToken <= 0) return@LaunchedEffect
            actionScope.withExclusivePetMotion("surfaceDropRequest") {
                actionScope.detachToGround(force = true)
            }
        }

        // Arriving from offscreen when an occasional visit is requested.
        LaunchedEffect(presence, widthPx, heightPx, surfaceActive) {
            if (!surfaceActive) return@LaunchedEffect
            if (presence == RealPetPresence.Arriving && !actionScope.isDragging && actionScope.sleepUntil <= 0L) {
                actionScope.withExclusivePetMotion("arriving") {
                    val sleepingTargetId = plannedOpeningPerchId
                        .takeIf { plannedOpeningScenario == RealPetGuestScenario.AlreadySleeping }
                    val sleepingTarget = sleepingTargetId?.let { actionScope.perchTargetPosition(it, 0.5f) }
                    if (sleepingTargetId != null && sleepingTarget != null) {
                        actionScope.anchorId = sleepingTargetId
                        perchProgress.snapTo(0.5f)
                        x.snapTo(sleepingTarget.x)
                        y.snapTo(sleepingTarget.y)
                        actionScope.lastAnchorX = sleepingTarget.x
                        actionScope.lastAnchorY = sleepingTarget.y
                        actionScope.sleepX = sleepingTarget.x
                        actionScope.sleepY = sleepingTarget.y
                        actionScope.sleepPerchFraction = 0.5f
                        actionScope.sleepPoseIndex = (actionScope.sleepPoseIndex + 1) % REAL_PET_SLEEP_POSE_COUNT
                        actionScope.state = RealPetState.Sleeping
                        actionScope.sleepUntil = SystemClock.elapsedRealtime() + random.nextLong(9_000L, 17_000L)
                        openingSleepScenarioActive = true
                        RealPetRuntime.markArrived()
                        plannedOpeningScenario = null
                        plannedOpeningPerchId = null
                    } else {
                        openingSleepScenarioActive = false
                        plannedOpeningScenario = null
                        plannedOpeningPerchId = null
                        val enterLeft = random.nextBoolean()
                        val enterStart = if (enterLeft) -petSizePx * 0.95f else widthPx + petSizePx * 0.02f
                        val targetX = if (enterLeft) {
                            sidePaddingPx + random.nextFloat() * (maxX - sidePaddingPx).coerceAtLeast(1f) * 0.42f
                        } else {
                            maxX - random.nextFloat() * (maxX - sidePaddingPx).coerceAtLeast(1f) * 0.42f
                        }.coerceIn(sidePaddingPx, maxX)

                        actionScope.anchorId = null
                        x.snapTo(enterStart)
                        y.snapTo(groundY)
                        actionScope.facing = if (enterLeft) RealPetFacing.Right else RealPetFacing.Left
                        actionScope.state = RealPetState.Running
                        val enterDistanceDp = abs(targetX - enterStart) / density.density
                        try {
                            x.animateTo(
                                targetX,
                                tween(realPetTravelDuration(enterDistanceDp, running = true), easing = LinearEasing)
                            )
                        } finally {
                            actionScope.state = RealPetState.Idle
                            RealPetRuntime.markArrived()
                        }
                        RealHaptics.tick(view)
                        if (random.nextFloat() < 0.34f) {
                            actionScope.meow(durationMs = 450L)
                        }
                    }
                }
            }
        }

        // Sleep wake-up watchdog
        LaunchedEffect(sleepUntil, widthPx, heightPx) {
            if (sleepUntil <= 0L) return@LaunchedEffect
            delay((sleepUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            val surfaceStillPresent = actionScope.anchorId?.let { RealPetRuntime.perches[it] } != null
            if (actionScope.anchorId == null || !surfaceStillPresent) {
                actionScope.anchorId = null
                x.snapTo(actionScope.sleepX.coerceIn(sidePaddingPx, maxX))
                y.snapTo(actionScope.sleepY)
            }
            actionScope.state = RealPetState.Idle
            actionScope.sleepUntil = 0L
        }

        // AlreadySleeping is itself the visit scenario. Once that nap ends, leave instead
        // of immediately chaining a second random autonomous scenario.
        LaunchedEffect(openingSleepScenarioActive, sleepUntil, isDragging, RealPetRuntime.presence) {
            if (!openingSleepScenarioActive || sleepUntil > 0L || isDragging) return@LaunchedEffect
            if (RealPetRuntime.presence != RealPetPresence.Active) {
                openingSleepScenarioActive = false
                return@LaunchedEffect
            }
            openingSleepScenarioActive = false
            actionScope.withExclusivePetMotion("openingSleepComplete") {
                actionScope.runOffscreenAndLeave()
            }
        }

        // Composer typing prank
        val composerTypingSession = RealPetRuntime.composerTypingSession
        LaunchedEffect(composerTypingSession, widthPx, heightPx, surfaceActive) {
            if (!surfaceActive) return@LaunchedEffect
            if (composerTypingSession <= 0) return@LaunchedEffect
            delay(650L)
            if (
                !RealPetRuntime.isComposerTypingNow() ||
                RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER_TEXT] == null ||
                !RealPetRuntime.canTriggerEvent(RealPetPrankType.ComposerTypedMeow.name, 45_000L)
            ) return@LaunchedEffect

            val isHidden = RealPetRuntime.presence == RealPetPresence.Hidden
            if (isHidden) {
                if (!RealPetRuntime.canStartCasualVisit()) return@LaunchedEffect
                if (!realPetHitAndRunPrankChance(random.nextFloat(), 0.20f)) return@LaunchedEffect
            } else {
                if (random.nextFloat() >= 0.20f) return@LaunchedEffect
            }

            RealPetRuntime.recordEventTriggered(RealPetPrankType.ComposerTypedMeow.name)
            actionScope.withExclusivePetMotion("composerTyping") {
                if (isHidden) {
                    if (!RealPetRuntime.requestVisit(RealPetVisitReason.ComposerPrank, 28_000L)) {
                        return@withExclusivePetMotion
                    }
                    val enterLeft = random.nextBoolean()
                    val enterStart = if (enterLeft) -petSizePx * 0.95f else widthPx + petSizePx * 0.02f
                    x.snapTo(enterStart)
                    y.snapTo(groundY)
                    RealPetRuntime.markArrived()
                }
                actionScope.moveToPerch(REAL_PET_PERCH_COMPOSER_TEXT)
                actionScope.textPrankCue(REAL_PET_PERCH_COMPOSER_TEXT)
                RealHaptics.tick(view)
                RealPetRuntime.forcePrank(
                    REAL_PET_PERCH_COMPOSER,
                    RealPetPrankType.ComposerTypedMeow
                )
                if (isHidden) {
                    delay(1200L)
                    RealPetRuntime.startLeaving()
                    actionScope.runOffscreenAndLeave()
                }
            }
        }

        // Mic Curiosity / Mic Meow reactive event (floating MEAW exception)
        val composerMicActive = RealPetRuntime.composerMicActive
        LaunchedEffect(composerMicActive, widthPx, heightPx, surfaceActive) {
            if (!surfaceActive) return@LaunchedEffect
            if (!composerMicActive) {
                // Clear temporary MEAW caption when recording ends
                if (actionScope.meowCaption != null && actionScope.meowCaption?.startsWith("MEAW") == true) {
                    actionScope.meowCaption = null
                }
                return@LaunchedEffect
            }
            if (reduceAnimations) return@LaunchedEffect
            if (RealPetRuntime.presence == RealPetPresence.Hidden || RealPetRuntime.presence == RealPetPresence.Leaving) {
                return@LaunchedEffect
            }
            if (!RealPetRuntime.canTriggerEvent("mic_live_meow", 35_000L)) return@LaunchedEffect
            if (random.nextInt(10) != 0) return@LaunchedEffect
            delay(520L)
            if (!RealPetRuntime.composerMicActive || RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER_MIC] == null) {
                return@LaunchedEffect
            }

            RealPetRuntime.recordEventTriggered("mic_live_meow")
            actionScope.withExclusivePetMotion("composerMic") {
                actionScope.moveToPerch(REAL_PET_PERCH_COMPOSER_MIC)
                actionScope.meow(520L, showCaption = true)
                RealPetRuntime.forcePrank(
                    REAL_PET_PERCH_COMPOSER_MIC,
                    RealPetPrankType.MicNudge
                )
            }
        }

        // Reminder completed celebration
        val celebrationRequest = RealPetRuntime.celebration
        LaunchedEffect(celebrationRequest?.token, widthPx, heightPx, surfaceActive) {
            if (!surfaceActive) return@LaunchedEffect
            val request = celebrationRequest ?: return@LaunchedEffect
            val tokenKey = "celebration:${request.token}"
            if (actionScope.sleepUntil > 0L) {
                if (RealPetRuntime.tryClaimToken(tokenKey)) {
                    RealPetRuntime.markTokenConsumed(tokenKey) {
                        RealPetRuntime.consumeCelebration(request.token)
                    }
                }
                return@LaunchedEffect
            }

            actionScope.executeClaimedPetEvent(
                tokenKey = tokenKey,
                owner = "celebration",
                maxWaitMs = 12_000L,
                consumeOnTimeout = true,
                onConsume = { RealPetRuntime.consumeCelebration(request.token) }
            ) {
                RealPetRuntime.markArrived()
                if (request.isAllClear) {
                    if (actionScope.anchorId != null) actionScope.detachToGround()
                    val centerX = ((widthPx - petSizePx) / 2f).coerceIn(sidePaddingPx, maxX)
                    x.animateTo(centerX, tween(380))
                    actionScope.facing = RealPetFacing.Right
                    repeat(2) {
                        actionScope.hopOnPerch(actionScope.anchorId ?: "")
                        RealHaptics.tick(view)
                        delay(120L)
                    }
                    RealHaptics.success(view)
                    actionScope.meowCaption = "ALL CLEAR!"
                    actionScope.meow(durationMs = 700L)
                    repeat(2) {
                        if (heartBursts.size >= 4) heartBursts.removeAt(0)
                        heartBursts.add(
                            RealPetHeartBurst(
                                id = actionScope.nextHeartId++,
                                origin = Offset(actionScope.latestHeartOriginX.value, actionScope.latestHeartOriginY.value)
                            )
                        )
                        delay(140L)
                    }
                } else {
                    val perchId = realPetReminderPerchId(request.reminderId)
                    val perchVisible = RealPetRuntime.perches[perchId] != null
                    if (!reduceAnimations && perchVisible) {
                        if (actionScope.anchorId != perchId) actionScope.moveToPerch(perchId)
                        actionScope.hopOnPerch(perchId)
                        RealHaptics.tick(view)
                    }
                    actionScope.meow(durationMs = if (reduceAnimations) 180L else 520L)
                    RealHaptics.success(view)
                    if (heartBursts.size >= 4) heartBursts.removeAt(0)
                    heartBursts.add(
                        RealPetHeartBurst(
                            id = actionScope.nextHeartId++,
                            origin = Offset(actionScope.latestHeartOriginX.value, actionScope.latestHeartOriginY.value)
                        )
                    )
                    if (!reduceAnimations && perchVisible) {
                        delay(140L)
                        actionScope.hopOnPerch(perchId)
                        RealHaptics.tick(view)
                    }
                }
                delay(650L)
                RealPetRuntime.startLeaving()
                actionScope.runOffscreenAndLeave()
            }
        }

        // New reminder card greeted by the cat
        val newCardReminderId = RealPetRuntime.newCardReminderId
        LaunchedEffect(newCardReminderId, widthPx, heightPx, surfaceActive) {
            if (!surfaceActive) return@LaunchedEffect
            val reminderId = newCardReminderId ?: return@LaunchedEffect
            val tokenKey = "newCard:$reminderId"
            val targetPerch = realPetReminderPerchId(reminderId)

            actionScope.executeClaimedPetEvent(
                tokenKey = tokenKey,
                owner = "newCard",
                maxWaitMs = 12_000L,
                canExecute = { RealPetRuntime.perches[targetPerch] != null },
                consumeOnTimeout = true,
                onConsume = { RealPetRuntime.consumeNewCard(reminderId) }
            ) {
                RealPetRuntime.markArrived()
                if (actionScope.anchorId != targetPerch) {
                    actionScope.moveToPerch(targetPerch)
                }
                actionScope.hopOnPerch(targetPerch)
                RealHaptics.tick(view)
                if (random.nextFloat() < 0.6f) {
                    actionScope.meow(durationMs = 450L)
                }
                delay(650L)
                RealPetRuntime.startLeaving()
                actionScope.runOffscreenAndLeave()
            }
        }

        // Navigation/perch commands
        val command = RealPetRuntime.command
        LaunchedEffect(command?.token, reduceAnimations, widthPx, heightPx, sleepUntil, surfaceActive) {
            if (!surfaceActive) return@LaunchedEffect
            val requested = command ?: return@LaunchedEffect
            val tokenKey = "command:${requested.token}"
            if (actionScope.sleepUntil > 0L) {
                if (RealPetRuntime.tryClaimToken(tokenKey)) {
                    RealPetRuntime.markTokenConsumed(tokenKey) {
                        RealPetRuntime.consumeCommand(requested.token)
                    }
                }
                return@LaunchedEffect
            }

            actionScope.executeClaimedPetEvent(
                tokenKey = tokenKey,
                owner = "command",
                maxWaitMs = 12_000L,
                canExecute = { RealPetRuntime.perches[requested.perchId] != null },
                consumeOnTimeout = true,
                onConsume = { RealPetRuntime.consumeCommand(requested.token) }
            ) {
                val bounds = RealPetRuntime.perches[requested.perchId] ?: return@executeClaimedPetEvent
                actionScope.isDragging = false
                RealPetRuntime.markArrived()

                if (!requested.dropFromTop) {
                    actionScope.moveToPerch(requested.perchId)
                    return@executeClaimedPetEvent
                }

                val targetRange = realPetPerchRange(bounds.left, bounds.right, petSizePx, sidePaddingPx, maxX)
                val targetX = (targetRange.start + targetRange.endInclusive) / 2f
                val targetY = actionScope.perchY(requested.perchId, bounds)
                actionScope.anchorId = null
                actionScope.motionTargetPerchId = requested.perchId
                try {
                    if (reduceAnimations) {
                        x.snapTo(targetX)
                        y.snapTo(targetY)
                    } else {
                        actionScope.motionStartX = targetX
                        actionScope.motionStartY = -petSizePx
                        actionScope.motionTargetX = targetX
                        actionScope.motionTargetY = targetY
                        actionScope.motionHeightPx = with(density) { 48.dp.toPx() }
                        actionScope.jumpVisual = RealPetJumpVisual.LegacyHop
                        actionScope.motion = RealPetMotion.Drop
                        actionScope.state = RealPetState.Jumping
                        actionScope.facing = if (targetX < actionScope.latestRenderX.value) RealPetFacing.Left else RealPetFacing.Right
                        motionProgress.snapTo(0f)
                        motionProgress.animateTo(
                            1f,
                            animationSpec = tween(
                                durationMillis = 620,
                                easing = CubicBezierEasing(0.22f, 0.0f, 0.58f, 1.0f)
                            )
                        )
                        motionProgress.snapTo(0f)
                        actionScope.motion = RealPetMotion.None
                        x.snapTo(targetX)
                        y.snapTo(targetY)
                    }
                } finally {
                    actionScope.lastAnchorX = targetX
                    actionScope.lastAnchorY = targetY
                    perchProgress.snapTo(0.5f)
                    actionScope.motionTargetPerchId = null
                    actionScope.anchorId = requested.perchId
                    actionScope.state = RealPetState.Idle
                }
            }
        }

        // Explicit keyword scenario: typing standalone "kedi" brings the pet to the composer briefly.
        val kediAppearanceRequest = RealPetRuntime.kediAppearanceRequest
        LaunchedEffect(kediAppearanceRequest, widthPx, heightPx, surfaceActive) {
            if (!surfaceActive) return@LaunchedEffect
            val requestToken = kediAppearanceRequest ?: return@LaunchedEffect
            val targetId = when {
                RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER_TEXT] != null -> REAL_PET_PERCH_COMPOSER_TEXT
                RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER] != null -> REAL_PET_PERCH_COMPOSER
                else -> null
            }
            val tokenKey = "keyword:kedi:$requestToken"
            actionScope.executeClaimedPetEvent(
                tokenKey = tokenKey,
                owner = "keyword:kedi",
                maxWaitMs = 8_000L,
                canExecute = { targetId != null && RealPetRuntime.perches[targetId] != null },
                consumeOnTimeout = true,
                onConsume = { RealPetRuntime.consumeKediAppearance(requestToken) }
            ) {
                val resolvedTarget = targetId ?: return@executeClaimedPetEvent
                val target = actionScope.perchTargetPosition(resolvedTarget, 0.5f)
                    ?: return@executeClaimedPetEvent
                RealPetRuntime.markArrived()
                actionScope.sleepUntil = 0L
                actionScope.anchorId = null
                val enterFromLeft = target.x < widthPx / 2f
                val enterStart = if (enterFromLeft) -petSizePx * 0.95f else widthPx + petSizePx * 0.02f
                x.snapTo(enterStart)
                y.snapTo(target.y)
                actionScope.facing = if (enterFromLeft) RealPetFacing.Right else RealPetFacing.Left
                actionScope.state = RealPetState.Running
                if (reduceAnimations) {
                    x.snapTo(target.x)
                } else {
                    x.animateTo(
                        target.x,
                        tween(realPetTravelDuration(abs(target.x - enterStart) / density.density, running = true), easing = LinearEasing)
                    )
                }
                actionScope.lastAnchorX = target.x
                actionScope.lastAnchorY = target.y
                perchProgress.snapTo(0.5f)
                actionScope.anchorId = resolvedTarget
                actionScope.state = RealPetState.Idle
                delay(if (reduceAnimations) 300L else 750L)
                actionScope.state = RealPetState.Meowing
                delay(if (reduceAnimations) 220L else 620L)
                actionScope.state = RealPetState.Idle
                delay(if (reduceAnimations) 180L else 420L)
                actionScope.runOffscreenAndLeave()
            }
        }

        // Explicit keyword scenario: after a successfully saved REAL containing standalone "mama",
        // animate only a temporary visual clone of that card into the pet's mouth.
        val mamaFeedingRequest = RealPetRuntime.mamaFeedingRequest
        LaunchedEffect(mamaFeedingRequest?.token, widthPx, heightPx, surfaceActive) {
            if (!surfaceActive) return@LaunchedEffect
            val request = mamaFeedingRequest ?: return@LaunchedEffect
            val tokenKey = "keyword:mama:${request.token}"
            try {
                actionScope.executeClaimedPetEvent(
                tokenKey = tokenKey,
                owner = "keyword:mama",
                maxWaitMs = 8_000L,
                consumeOnTimeout = true,
                onConsume = { RealPetRuntime.consumeMamaFeeding(request.token) }
            ) {
                RealPetRuntime.markArrived()
                actionScope.sleepUntil = 0L
                actionScope.anchorId = null
                val petOnLeft = random.nextBoolean()
                val targetPetX = if (petOnLeft) {
                    sidePaddingPx + with(density) { 5.dp.toPx() }
                } else {
                    maxX - with(density) { 5.dp.toPx() }
                }.coerceIn(sidePaddingPx, maxX)
                val enterStart = if (petOnLeft) -petSizePx * 0.95f else widthPx + petSizePx * 0.02f
                x.snapTo(enterStart)
                y.snapTo(groundY)
                actionScope.facing = if (petOnLeft) RealPetFacing.Right else RealPetFacing.Left
                actionScope.state = RealPetState.Running
                if (reduceAnimations) {
                    x.snapTo(targetPetX)
                } else {
                    x.animateTo(
                        targetPetX,
                        tween(realPetTravelDuration(abs(targetPetX - enterStart) / density.density, running = true), easing = LinearEasing)
                    )
                }
                actionScope.state = RealPetState.Idle
                actionScope.lastAnchorX = targetPetX
                actionScope.lastAnchorY = groundY

                val cardWidthPx = with(density) { 118.dp.toPx() }
                feedingStartX = (widthPx * 0.5f - cardWidthPx * 0.5f).coerceIn(0f, (widthPx - cardWidthPx).coerceAtLeast(0f))
                feedingStartY = (groundY - with(density) { 18.dp.toPx() }).coerceAtLeast(with(density) { 72.dp.toPx() })
                val mouthCenterX = targetPetX + if (petOnLeft) petSizePx * 0.62f else petSizePx * 0.38f
                feedingEndX = mouthCenterX - cardWidthPx * 0.5f
                feedingEndY = groundY + petSizePx * 0.43f - with(density) { 29.dp.toPx() }
                feedingCardTitle = request.cardTitle.ifBlank { "Mama" }
                feedingProgress.snapTo(0f)
                actionScope.state = RealPetState.Meowing
                if (reduceAnimations) {
                    feedingProgress.snapTo(1f)
                } else {
                    feedingProgress.animateTo(
                        1f,
                        tween(900, easing = RealMotion.Emphasized)
                    )
                }
                feedingCardTitle = null
                actionScope.state = RealPetState.Idle
                delay(if (reduceAnimations) 180L else 260L)
                actionScope.sitAndObserve(1_000L, 1_600L)
                actionScope.runOffscreenAndLeave()
                }
            } finally {
                feedingCardTitle = null
            }
        }

        // Scenario test request
        val scenarioTestRequest = RealPetRuntime.scenarioTestRequest
        LaunchedEffect(scenarioTestRequest?.token, widthPx, heightPx, surfaceActive) {
            if (!surfaceActive) return@LaunchedEffect
            val request = scenarioTestRequest ?: return@LaunchedEffect
            val tokenKey = "scenarioTest:${request.token}"
            delay(400L)

            actionScope.executeClaimedPetEvent(
                tokenKey = tokenKey,
                owner = "scenarioTest",
                maxWaitMs = 12_000L,
                canExecute = { actionScope.scenarioTestContextReady(request.kind) },
                consumeOnTimeout = true,
                onConsume = { RealPetRuntime.consumeScenarioTest(request.token) }
            ) {
                RealPetRuntime.markArrived()
                when (request.kind) {
                    RealPetScenarioTestKind.PoopAndRun -> actionScope.performPoopOnLastCard()
                    RealPetScenarioTestKind.DoubleTapSit,
                    RealPetScenarioTestKind.Sit -> actionScope.sitAndObserve(4_000L, 6_000L)
                    RealPetScenarioTestKind.Hearts -> {
                        repeat(3) { index ->
                            if (actionScope.heartBursts.size >= 6) actionScope.heartBursts.removeAt(0)
                            actionScope.heartBursts.add(
                                RealPetHeartBurst(
                                    id = actionScope.nextHeartId++,
                                    origin = Offset(actionScope.latestHeartOriginX.value, actionScope.latestHeartOriginY.value)
                                )
                            )
                            if (index < 2) delay(140L)
                        }
                    }
                    RealPetScenarioTestKind.KeywordKedi -> RealPetRuntime.requestKediAppearance()
                    RealPetScenarioTestKind.KeywordMama -> RealPetRuntime.requestMamaFeeding("Mama")
                    else -> actionScope.runGuestScenario(request.kind.toGuestScenario())
                }
            }
        }

        // Detail navigation journey (enter/exit Detail screen)
        var lastDetailEntranceScenario by remember { mutableStateOf<RealPetDetailEntranceScenario?>(null) }
        var lastDetailReturnScenario by remember { mutableStateOf<RealPetDetailReturnScenario?>(null) }

        val detailNav = RealPetRuntime.detailNavigation
        LaunchedEffect(detailNav?.token, widthPx, heightPx, surfaceActive) {
            if (!surfaceActive) return@LaunchedEffect
            val nav = detailNav ?: return@LaunchedEffect
            val tokenKey = "detailNav:${nav.token}"

            actionScope.executeClaimedPetEvent(
                tokenKey = tokenKey,
                owner = if (nav.isEntering) "detailEnter" else "detailExit",
                maxWaitMs = 8_000L,
                canExecute = {
                    !nav.isEntering || actionScope.visibleDetailPerches().isNotEmpty() ||
                        RealPetRuntime.perches[REAL_PET_PERCH_DETAIL_BOTTOM_BAR] != null
                },
                consumeOnTimeout = true,
                onConsume = { RealPetRuntime.consumeDetailNavigation(nav.token) }
            ) {
                if (RealPetRuntime.presence == RealPetPresence.Hidden) return@executeClaimedPetEvent
                if (nav.isEntering) {
                    delay(120L)
                    val candidates = RealPetDetailEntranceScenario.entries.filter { it != lastDetailEntranceScenario }
                    val scenario = if (candidates.isNotEmpty()) candidates.random(random) else RealPetDetailEntranceScenario.entries.random(random)
                    lastDetailEntranceScenario = scenario
                    when (scenario) {
                        RealPetDetailEntranceScenario.DropOntoTopCard -> actionScope.performDetailEntranceTopCardDrop(nav.reminderId)
                        RealPetDetailEntranceScenario.TravelDownCards -> actionScope.performDetailEntranceTravelDown(nav.reminderId)
                        RealPetDetailEntranceScenario.SideEntrance -> actionScope.performDetailEntranceSide(nav.reminderId)
                        RealPetDetailEntranceScenario.BottomBarExplorer -> actionScope.performDetailEntranceBottomBar(nav.reminderId)
                    }
                } else {
                    delay(120L)
                    val candidates = RealPetDetailReturnScenario.entries.filter { it != lastDetailReturnScenario }
                    val scenario = if (candidates.isNotEmpty()) candidates.random(random) else RealPetDetailReturnScenario.entries.random(random)
                    lastDetailReturnScenario = scenario
                    actionScope.performDetailReturn(nav.reminderId, scenario)
                }
            }
        }

        var lastGuestScenario by remember { mutableStateOf<RealPetGuestScenario?>(null) }

        // Scenario-driven visitor loop. A user drag/hold or temporary navigation can
        // interrupt the current motion, but the same story is resumed with a safe tail.
        LaunchedEffect(widthPx, heightPx, reduceAnimations, RealPetRuntime.presence) {
            if (widthPx <= petSizePx || heightPx <= petSizePx) return@LaunchedEffect
            if (RealPetRuntime.presence == RealPetPresence.Hidden || RealPetRuntime.presence == RealPetPresence.Leaving) {
                return@LaunchedEffect
            }

            while (isActive) {
                if (!latestSurfaceActive.value) {
                    delay(180L)
                    continue
                }

                if (RealPetRuntime.isVisitExpired()) {
                    actionScope.pendingScenarioResume = null
                    actionScope.scenarioResumeNotBefore = 0L
                    actionScope.withExclusivePetMotion("visitExpired") {
                        actionScope.runOffscreenAndLeave()
                    }
                    return@LaunchedEffect
                }

                if (motionMutex.isLocked || actionScope.isDragging || actionScope.sleepUntil > SystemClock.elapsedRealtime() || actionScope.doubleTapSitActive) {
                    delay(140L)
                    continue
                }

                val pendingResume = actionScope.pendingScenarioResume
                val isResuming = pendingResume != null
                val scenario: RealPetGuestScenario

                if (isResuming) {
                    val waitMs = actionScope.scenarioResumeNotBefore - SystemClock.elapsedRealtime()
                    if (waitMs > 0L) {
                        delay(minOf(waitMs, 220L))
                        continue
                    }
                    scenario = pendingResume!!
                } else {
                    delay(if (reduceAnimations) 7_000L else random.nextLong(4_200L, 9_500L))
                    if (!latestSurfaceActive.value || actionScope.isDragging ||
                        actionScope.sleepUntil > SystemClock.elapsedRealtime() || motionMutex.isLocked ||
                        actionScope.doubleTapSitActive
                    ) {
                        continue
                    }
                    if (RealPetRuntime.isVisitExpired()) continue

                    val cards = actionScope.visibleReminderPerches()
                    val categories = actionScope.visibleCategorySurfacePerches()
                    val details = (actionScope.visibleDetailPerches() + listOfNotNull(actionScope.detailBottomBarPerch())).distinct()
                    var picked = pickRealPetGuestScenario(
                        random = random,
                        hasCards = cards.isNotEmpty(),
                        hasCategories = categories.isNotEmpty(),
                        hasComposer = RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER] != null ||
                            RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER_TEXT] != null,
                        hasDetailBlocks = details.isNotEmpty(),
                        hasAvatar = RealPetRuntime.perches[REAL_PET_PERCH_AVATAR] != null,
                        canTextPrank = actionScope.guestTextChangeEligible(),
                        canPlaceholderPrank = actionScope.guestPlaceholderPrankEligible()
                    )
                    if (picked == lastGuestScenario) {
                        repeat(2) {
                            val retry = pickRealPetGuestScenario(
                                random = random,
                                hasCards = cards.isNotEmpty(),
                                hasCategories = categories.isNotEmpty(),
                                hasComposer = RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER] != null ||
                                    RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER_TEXT] != null,
                                hasDetailBlocks = details.isNotEmpty(),
                                hasAvatar = RealPetRuntime.perches[REAL_PET_PERCH_AVATAR] != null,
                                canTextPrank = actionScope.guestTextChangeEligible(),
                                canPlaceholderPrank = actionScope.guestPlaceholderPrankEligible()
                            )
                            if (retry != lastGuestScenario) {
                                picked = retry
                                return@repeat
                            }
                        }
                    }
                    scenario = picked
                    lastGuestScenario = scenario
                }

                val scenarioJob = Job(coroutineContext[Job])
                val acquired = motionMutex.tryLock()
                if (!acquired) {
                    scenarioJob.cancel()
                    delay(100L)
                    continue
                }

                if (isResuming) {
                    // Clear before execution; a second user interruption will enqueue it again.
                    actionScope.pendingScenarioResume = null
                    actionScope.scenarioResumeNotBefore = 0L
                }
                actionScope.activeScenarioJob = scenarioJob
                actionScope.activeScenarioKind = scenario
                actionScope.activeMotionOwner = "scenario:${scenario.name}"

                var interruptedForResume = false
                val endedVisit = try {
                    val scenarioEndedVisit = try {
                        withContext(scenarioJob) {
                            if (isResuming) {
                                actionScope.resumeGuestScenarioAfterInterruption(scenario)
                            } else {
                                actionScope.runGuestScenario(scenario)
                            }
                        }
                    } catch (e: CancellationException) {
                        if (!coroutineContext.isActive) throw e
                        interruptedForResume = actionScope.pendingScenarioResume == scenario
                        false
                    }

                    if (interruptedForResume) {
                        false
                    } else {
                        val isOnDetail = actionScope.visibleDetailPerches().isNotEmpty() ||
                            RealPetRuntime.perches[REAL_PET_PERCH_DETAIL_BOTTOM_BAR] != null
                        // One autonomous visit runs one coherent story on Home. Detail can keep
                        // the visitor alive until expiry/back navigation.
                        if (!scenarioEndedVisit && RealPetRuntime.presence != RealPetPresence.Hidden && !isOnDetail) {
                            actionScope.runOffscreenAndLeave()
                        }
                        scenarioEndedVisit || (!isOnDetail && RealPetRuntime.presence == RealPetPresence.Hidden)
                    }
                } finally {
                    if (actionScope.activeScenarioJob == scenarioJob) {
                        actionScope.activeScenarioJob = null
                        actionScope.activeScenarioKind = null
                    }
                    actionScope.activeMotionOwner = null
                    if (actionScope.motion != RealPetMotion.None) {
                        motionProgress.snapTo(0f)
                        actionScope.motion = RealPetMotion.None
                    }
                    scenarioJob.complete()
                    motionMutex.unlock()
                }

                if (RealPetRuntime.presence == RealPetPresence.Hidden) {
                    actionScope.pendingScenarioResume = null
                    actionScope.scenarioResumeNotBefore = 0L
                    return@LaunchedEffect
                }
                if (interruptedForResume) {
                    // User placement/hold or a hidden page owns the pet for a moment. The loop
                    // will wait for that interruption to settle, then resume this same scenario.
                    continue
                }
                if (endedVisit) return@LaunchedEffect
            }
        }

        val edgeSquash = if (motion == RealPetMotion.Jump) {
            val takeoff = ((0.12f - p) / 0.12f).coerceIn(0f, 1f)
            val landing = ((p - 0.88f) / 0.12f).coerceIn(0f, 1f)
            maxOf(takeoff, landing)
        } else if (motion == RealPetMotion.Drop && p > 0.88f) {
            ((p - 0.88f) / 0.12f).coerceIn(0f, 1f)
        } else 0f
        val jumpScaleX = 1f + edgeSquash * 0.035f
        val jumpScaleY = 1f - edgeSquash * 0.035f
        val directionScale = if (facing == RealPetFacing.Left) 1f else -1f

        val isPresenceVisible =
            RealPetRuntime.presence != RealPetPresence.Hidden
        val renderBoxOnScreen =
            baseX + petSizePx > 0f && baseX < widthPx &&
                baseY + petSizePx > 0f && baseY < heightPx
        val petVisible =
            surfaceActive && isPresenceVisible && renderBoxOnScreen

        feedingCardTitle?.takeIf { surfaceActive }?.let { title ->
            val feedP = feedingProgress.value.coerceIn(0f, 1f)
            val feedX = feedingStartX + (feedingEndX - feedingStartX) * feedP
            val feedY = feedingStartY + (feedingEndY - feedingStartY) * feedP
            val feedScale = 1f - 0.36f * feedP
            val feedAlpha = (1f - ((feedP - 0.72f) / 0.28f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .size(118.dp, 58.dp)
                    .graphicsLayer {
                        translationX = feedX
                        translationY = feedY
                        scaleX = feedScale
                        scaleY = feedScale
                        alpha = feedAlpha
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1C1D22).copy(alpha = 0.96f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            Modifier
                .size(petSize)
                .graphicsLayer {
                    alpha = if (petVisible) 1f else 0f
                    translationX = baseX
                    translationY = baseY
                    scaleX = 1f
                    scaleY = 1f
                    rotationZ = 0f
                }
                .realPetGestures(actionScope, petVisible)
        ) {
            meowCaption?.let { caption ->
                Text(
                    text = caption,
                    color = Color.White.copy(alpha = 0.96f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-11).dp)
                )
            }
            RealPetRenderer(
                state = if (sleepUntil > 0L) RealPetState.Sleeping else state,
                sleepPoseIndex = sleepPoseIndex,
                reduceAnimations = reduceAnimations,
                sitFrameIndex = sitFrameIndex,
                jumpProgress = p,
                jumpVisual = jumpVisual,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = directionScale * jumpScaleX
                    scaleY = jumpScaleY
                }
            )
        }

        if (surfaceActive) {
            heartBursts.toList().forEach { burst ->
                key(burst.id) {
                    RealPetHearts(burst, reduceAnimations) { heartBursts.remove(burst) }
                }
            }
        }
        } // isForeground
    }
}
