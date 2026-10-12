package com.cagri.real.ui.components

import android.os.SystemClock
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cagri.real.data.ReminderItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.random.Random

internal fun realPetParentCategoryPerchId(perchId: String?): String? {
    val id = perchId ?: return null
    val marker = id.indexOf(":pill:")
    return if (marker >= 0) id.substring(0, marker) else null
}

internal fun realPetPerchIsCategorySurface(perchId: String): Boolean =
    perchId == REAL_PET_PERCH_CATEGORY_HERO ||
        (perchId.startsWith("real_pet:category:") && ":pill:" !in perchId)

/**
 * State scope and atomic motion execution engine for the REAL pet companion.
 */
internal class RealPetActionScope(
    val density: Density,
    val view: View,
    val coroutineScope: CoroutineScope,
    val random: Random,
    val reduceAnimations: Boolean,

    // Layout metrics
    val petSizePx: Float,
    val sidePaddingPx: Float,
    val groundPaddingPx: Float,
    val perchSinkPx: Float,
    val snapDistancePx: Float,
    val navigationBottomPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val maxX: Float,
    val groundY: Float,

    // Animatables
    val x: Animatable<Float, AnimationVector1D>,
    val y: Animatable<Float, AnimationVector1D>,
    val motionProgress: Animatable<Float, AnimationVector1D>,
    val perchProgress: Animatable<Float, AnimationVector1D>,

    // State holders
    val sleepPerchFractionState: MutableFloatState,
    val sleepUntilState: MutableLongState,
    val sleepXState: MutableFloatState,
    val sleepYState: MutableFloatState,
    val stateState: MutableState<RealPetState>,
    val meowCaptionState: MutableState<String?>,
    val sleepPoseIndexState: MutableIntState,
    val sitFrameIndexState: MutableIntState,
    val heartBursts: SnapshotStateList<RealPetHeartBurst>,
    val nextHeartIdState: MutableIntState,
    val facingState: MutableState<RealPetFacing>,
    val motionState: MutableState<RealPetMotion>,
    val jumpVisualState: MutableState<RealPetJumpVisual>,
    val motionStartXState: MutableFloatState,
    val motionStartYState: MutableFloatState,
    val motionTargetXState: MutableFloatState,
    val motionTargetYState: MutableFloatState,
    val motionHeightPxState: MutableFloatState,
    val motionTargetPerchIdState: MutableState<String?>,
    val anchorIdState: MutableState<String?>,
    val lastAnchorXState: MutableFloatState,
    val lastAnchorYState: MutableFloatState,
    val isDraggingState: MutableState<Boolean>,
    val dragXState: MutableFloatState,
    val dragYState: MutableFloatState,
    val peekActiveState: MutableState<Boolean>,
    val peekFleeRequestedState: MutableState<Boolean>,
    val doubleTapSitActiveState: MutableState<Boolean>,
    val sitUntilDeadlineState: MutableLongState,
    val attentionActiveState: MutableState<Boolean>,
    val attentionTapCountState: MutableIntState,
    val attentionTapGoalState: MutableIntState,

    // Concurrency
    val motionMutex: Mutex,
    val activeMotionOwnerState: MutableState<String?>,
    val activeScenarioJobState: MutableState<Job?>,
    val activeScenarioKindState: MutableState<RealPetGuestScenario?>,
    val pendingScenarioResumeState: MutableState<RealPetGuestScenario?>,
    val scenarioResumeNotBeforeState: MutableLongState,
    val activeExclusiveJobState: MutableState<Job?>,
    val activeExclusivePriorityState: MutableState<RealPetPriority>,

    // Render positions & callbacks
    val latestRenderX: State<Float>,
    val latestRenderY: State<Float>,
    val latestHeartOriginX: State<Float>,
    val latestHeartOriginY: State<Float>,
    val latestCreatePersistentCard: State<(String) -> Unit>,
    val reminders: () -> List<ReminderItem>
) {
    var sleepPerchFraction: Float
        get() = sleepPerchFractionState.floatValue
        set(value) { sleepPerchFractionState.floatValue = value }

    var sleepUntil: Long
        get() = sleepUntilState.longValue
        set(value) { sleepUntilState.longValue = value }

    var sleepX: Float
        get() = sleepXState.floatValue
        set(value) { sleepXState.floatValue = value }

    var sleepY: Float
        get() = sleepYState.floatValue
        set(value) { sleepYState.floatValue = value }

    var state: RealPetState
        get() = stateState.value
        set(value) { stateState.value = value }

    var meowCaption: String?
        get() = meowCaptionState.value
        set(value) { meowCaptionState.value = value }

    var sleepPoseIndex: Int
        get() = sleepPoseIndexState.intValue
        set(value) { sleepPoseIndexState.intValue = value }

    var sitFrameIndex: Int
        get() = sitFrameIndexState.intValue
        set(value) { sitFrameIndexState.intValue = value }

    var nextHeartId: Int
        get() = nextHeartIdState.intValue
        set(value) { nextHeartIdState.intValue = value }

    var facing: RealPetFacing
        get() = facingState.value
        set(value) { facingState.value = value }

    var motion: RealPetMotion
        get() = motionState.value
        set(value) { motionState.value = value }

    var jumpVisual: RealPetJumpVisual
        get() = jumpVisualState.value
        set(value) { jumpVisualState.value = value }

    var motionStartX: Float
        get() = motionStartXState.floatValue
        set(value) { motionStartXState.floatValue = value }

    var motionStartY: Float
        get() = motionStartYState.floatValue
        set(value) { motionStartYState.floatValue = value }

    var motionTargetX: Float
        get() = motionTargetXState.floatValue
        set(value) { motionTargetXState.floatValue = value }

    var motionTargetY: Float
        get() = motionTargetYState.floatValue
        set(value) { motionTargetYState.floatValue = value }

    var motionHeightPx: Float
        get() = motionHeightPxState.floatValue
        set(value) { motionHeightPxState.floatValue = value }

    var motionTargetPerchId: String?
        get() = motionTargetPerchIdState.value
        set(value) { motionTargetPerchIdState.value = value }

    var anchorId: String?
        get() = anchorIdState.value
        set(value) { anchorIdState.value = value }

    var lastAnchorX: Float
        get() = lastAnchorXState.floatValue
        set(value) { lastAnchorXState.floatValue = value }

    var lastAnchorY: Float
        get() = lastAnchorYState.floatValue
        set(value) { lastAnchorYState.floatValue = value }

    var isDragging: Boolean
        get() = isDraggingState.value
        set(value) { isDraggingState.value = value }

    var dragX: Float
        get() = dragXState.floatValue
        set(value) { dragXState.floatValue = value }

    var dragY: Float
        get() = dragYState.floatValue
        set(value) { dragYState.floatValue = value }

    var peekActive: Boolean
        get() = peekActiveState.value
        set(value) { peekActiveState.value = value }

    var peekFleeRequested: Boolean
        get() = peekFleeRequestedState.value
        set(value) { peekFleeRequestedState.value = value }

    var doubleTapSitActive: Boolean
        get() = doubleTapSitActiveState.value
        set(value) { doubleTapSitActiveState.value = value }

    var sitUntilDeadline: Long
        get() = sitUntilDeadlineState.longValue
        set(value) { sitUntilDeadlineState.longValue = value }

    var attentionActive: Boolean
        get() = attentionActiveState.value
        set(value) { attentionActiveState.value = value }

    var attentionTapCount: Int
        get() = attentionTapCountState.intValue
        set(value) { attentionTapCountState.intValue = value }

    var attentionTapGoal: Int
        get() = attentionTapGoalState.intValue
        set(value) { attentionTapGoalState.intValue = value }

    var activeMotionOwner: String?
        get() = activeMotionOwnerState.value
        set(value) { activeMotionOwnerState.value = value }

    var activeScenarioJob: Job?
        get() = activeScenarioJobState.value
        set(value) { activeScenarioJobState.value = value }

    var activeScenarioKind: RealPetGuestScenario?
        get() = activeScenarioKindState.value
        set(value) { activeScenarioKindState.value = value }

    var pendingScenarioResume: RealPetGuestScenario?
        get() = pendingScenarioResumeState.value
        set(value) { pendingScenarioResumeState.value = value }

    var scenarioResumeNotBefore: Long
        get() = scenarioResumeNotBeforeState.longValue
        set(value) { scenarioResumeNotBeforeState.longValue = value }

    var activeExclusiveJob: Job?
        get() = activeExclusiveJobState.value
        set(value) { activeExclusiveJobState.value = value }

    var activeExclusivePriority: RealPetPriority
        get() = activeExclusivePriorityState.value
        set(value) { activeExclusivePriorityState.value = value }

    private fun priorityForOwner(owner: String): RealPetPriority = when {
        owner.startsWith("drag", ignoreCase = true) || owner == "holdSit" -> RealPetPriority.UserInteraction
        owner.startsWith("keyword:", ignoreCase = true) -> RealPetPriority.KeywordScenario
        owner.startsWith("scenario:", ignoreCase = true) || owner == "visitExpired" -> RealPetPriority.Autonomous
        else -> RealPetPriority.ContextEvent
    }

    fun requestScenarioResumeAfterInterruption(pauseMs: Long = 1_800L) {
        if (RealPetRuntime.presence == RealPetPresence.Hidden || RealPetRuntime.presence == RealPetPresence.Leaving) return
        val currentScenario = activeScenarioKind ?: return
        val currentScenarioJob = activeScenarioJob ?: return
        if (!currentScenarioJob.isActive) return
        pendingScenarioResume = currentScenario
        scenarioResumeNotBefore = maxOf(
            scenarioResumeNotBefore,
            elapsedRealtime() + pauseMs.coerceAtLeast(0L)
        )
        currentScenarioJob.cancel()
    }

    suspend fun restAfterUserPlacement() {
        val now = elapsedRealtime()
        if (sleepUntil > now) {
            state = RealPetState.Sleeping
            return
        }
        when (random.nextInt(10)) {
            0 -> takeNap(if (reduceAnimations) 900L else random.nextLong(5_000L, 8_500L))
            in 1..3 -> playSitSequence(if (reduceAnimations) 500L else random.nextLong(1_000L, 2_200L))
            else -> delay(if (reduceAnimations) 220L else random.nextLong(650L, 1_350L))
        }
    }

    suspend fun <T> withExclusivePetMotion(
        owner: String,
        block: suspend () -> T
    ): T? {
        if (isDragging) return null
        val requestedPriority = priorityForOwner(owner)
        val currentScenario = activeScenarioJob
        if (currentScenario?.isActive == true) {
            if (requestedPriority == RealPetPriority.UserInteraction) {
                requestScenarioResumeAfterInterruption(
                    pauseMs = if (owner == "holdSit") 4_500L else 1_800L
                )
            } else {
                currentScenario.cancel()
            }
        }
        val currentExclusive = activeExclusiveJob
        if (currentExclusive?.isActive == true && currentExclusive != coroutineContext[Job]) {
            if (requestedPriority.level < activeExclusivePriority.level) return null
            currentExclusive.cancel()
        }
        return motionMutex.withLock {
            if (isDragging) return null
            activeMotionOwner = owner
            activeExclusiveJob = coroutineContext[Job]
            activeExclusivePriority = requestedPriority
            try {
                block()
            } finally {
                if (activeExclusiveJob == coroutineContext[Job]) {
                    activeExclusiveJob = null
                    activeExclusivePriority = RealPetPriority.Autonomous
                }
                activeMotionOwner = null
                if (motion != RealPetMotion.None) {
                    motionProgress.snapTo(0f)
                    motion = RealPetMotion.None
                }
            }
        }
    }

    private fun elapsedRealtime(): Long = try {
        SystemClock.elapsedRealtime()
    } catch (_: RuntimeException) {
        System.currentTimeMillis()
    }

    suspend fun executeClaimedPetEvent(
        tokenKey: String,
        owner: String,
        maxWaitMs: Long = 12_000L,
        canExecute: () -> Boolean = { true },
        consumeOnTimeout: Boolean = false,
        onConsume: () -> Unit,
        block: suspend () -> Unit
    ): Boolean {
        // If already claimed or consumed by another execution, reject duplicate
        if (RealPetRuntime.getTokenState(tokenKey) != RealPetTokenState.PENDING) {
            return false
        }

        val deadline = elapsedRealtime() + maxWaitMs
        while (coroutineContext.isActive && elapsedRealtime() < deadline) {
            // Wait for preconditions (e.g. perch bounds measured)
            while (!canExecute() && coroutineContext.isActive && elapsedRealtime() < deadline) {
                delay(80L)
            }
            if (!canExecute() || !coroutineContext.isActive) break

            // Wait if the user is currently dragging so the event is not starved
            while (isDragging && coroutineContext.isActive && elapsedRealtime() < deadline) {
                delay(80L)
            }
            if (!coroutineContext.isActive) break

            var executed = false
            try {
                withExclusivePetMotion(owner) {
                    if (isDragging) return@withExclusivePetMotion
                    // Atomically claim the token under the exclusive mutex
                    if (!RealPetRuntime.tryClaimToken(tokenKey)) {
                        return@withExclusivePetMotion
                    }
                    RealPetRuntime.markTokenExecuting(tokenKey)
                    executed = true
                    try {
                        block()
                    } finally {
                        RealPetRuntime.markTokenConsumed(tokenKey, onConsume)
                    }
                }
            } finally {
                // If cancelled after successfully claiming, ensure the token is marked CONSUMED
                // so it does not hang forever.
                if (executed && RealPetRuntime.getTokenState(tokenKey) != RealPetTokenState.CONSUMED) {
                    RealPetRuntime.markTokenConsumed(tokenKey, onConsume)
                }
            }

            if (executed) return true
            delay(80L)
        }

        if (consumeOnTimeout && coroutineContext.isActive) {
            if (RealPetRuntime.tryClaimToken(tokenKey)) {
                RealPetRuntime.markTokenConsumed(tokenKey, onConsume)
            }
        }
        return false
    }

    fun perchRange(perchId: String?): ClosedFloatingPointRange<Float>? {
        val bounds = perchId?.let { RealPetRuntime.perches[it] } ?: return null
        return realPetPerchRange(bounds.left, bounds.right, petSizePx, sidePaddingPx, maxX)
    }

    fun perchY(perchId: String, bounds: Rect): Float {
        // A category pill is only an X target. The cat lands on the parent category's
        // top edge instead of shrinking into the pill itself. This keeps the visual
        // language consistent with every other category-card perch.
        val parentCategoryBounds = realPetParentCategoryPerchId(perchId)
            ?.let { RealPetRuntime.perches[it] }

        // Add to REAL has two kinds of targets: the bar itself is a top-edge perch,
        // while text/mic are allowed to be true inside-the-bar visits at normal size.
        if (perchId == REAL_PET_PERCH_COMPOSER_TEXT || perchId == REAL_PET_PERCH_COMPOSER_MIC) {
            return (bounds.center.y - petSizePx / 2f).coerceIn(-petSizePx * 0.12f, groundY)
        }

        // Category pill targets only choose X. Physically the cat always stands
        // on the category card's top edge, never in the middle of the card.
        val surfaceTop = parentCategoryBounds?.top ?: bounds.top
        return surfaceTop - petSizePx + perchSinkPx
    }

    fun perchTargetPosition(perchId: String?, fraction: Float): Offset? {
        val id = perchId ?: return null
        val bounds = RealPetRuntime.perches[id] ?: return null
        if (id == REAL_PET_PERCH_AVATAR) {
            // Approach the avatar at normal size from beside it; the takeover itself
            // then replaces the avatar and hides the roaming body.
            return Offset(
                x = (bounds.center.x - petSizePx * 0.68f).coerceIn(sidePaddingPx, maxX),
                y = (bounds.center.y - petSizePx * 0.69f).coerceIn(-petSizePx * 0.20f, groundY)
            )
        }
        val range = perchRange(id) ?: return null
        return Offset(
            x = range.start + (range.endInclusive - range.start) * fraction.coerceIn(0f, 1f),
            y = perchY(id, bounds)
        )
    }

    fun perchPosition(perchId: String?): Offset? =
        perchTargetPosition(perchId, perchProgress.value)

    fun nearestDropLanding(fromX: Float, fromY: Float, excludedId: String): Pair<String, Offset>? {
        val maxHorizontalDrift = petSizePx * 1.15f
        return RealPetRuntime.perches.keys.mapNotNull { id ->
            if (id == excludedId || id == REAL_PET_PERCH_AVATAR || ":pill:" in id ||
                id == REAL_PET_PERCH_COMPOSER_TEXT || id == REAL_PET_PERCH_COMPOSER_MIC) return@mapNotNull null
            val target = perchTargetPosition(id, 0.5f) ?: return@mapNotNull null
            val dy = target.y - fromY
            val dx = abs(target.x - fromX)
            if (dy < with(density) { 10.dp.toPx() } || dx > maxHorizontalDrift) return@mapNotNull null
            Triple(id, target, dy + dx * 0.30f)
        }.minByOrNull { it.third }?.let { it.first to it.second }
    }

    fun meowWord(): String = listOf("MEAW", "MEAWW", "MEAWWW", "MEAWWWW")[random.nextInt(4)]

    suspend fun meow(durationMs: Long = 720L, showCaption: Boolean = false) {
        // Most pet events keep the MEAW animation/sound but no floating word.
        // The microphone interaction is the one deliberate exception.
        meowCaption = if (showCaption) meowWord() else null
        state = RealPetState.Meowing
        delay(durationMs)
        state = RealPetState.Idle
        if (showCaption) meowCaption = null
    }

    suspend fun playSitSequence(seatedHoldMs: Long) {
        val hold = seatedHoldMs.coerceAtLeast(900L)
        state = RealPetState.Sitting
        sitFrameIndex = if (reduceAnimations) 4 else 0

        if (reduceAnimations) {
            delay(hold.coerceAtMost(1_500L))
            sitFrameIndex = 0
            state = RealPetState.Idle
            return
        }

        // Sit down: 1 -> 2 -> 3 -> 4 -> 5.
        for (frame in 1..4) {
            delay(115L)
            sitFrameIndex = frame
        }

        // Stay mostly on frame 5 (eyes open), with two brief 6 -> 5 blinks.
        // This is intentionally not a 5/6 loop: the open-eyed pose dominates.
        val firstOpen = (hold * 0.28f).toLong().coerceAtLeast(350L)
        val secondOpen = (hold * 0.38f).toLong().coerceAtLeast(450L)
        val thirdOpen = (hold - firstOpen - secondOpen).coerceAtLeast(350L)

        delay(firstOpen)
        sitFrameIndex = 5
        delay(155L)
        sitFrameIndex = 4

        delay(secondOpen)
        sitFrameIndex = 5
        delay(155L)
        sitFrameIndex = 4

        delay(thirdOpen)

        // Stand back up by reversing the entrance:
        // ... 6 -> 5 -> 5 -> 4 -> 3 -> 2 -> 1.
        sitFrameIndex = 4
        delay(180L)
        for (frame in 3 downTo 0) {
            sitFrameIndex = frame
            delay(115L)
        }
        state = RealPetState.Idle
    }

    suspend fun performDoubleTapSit(): Boolean {
        sleepUntil = 0L
        state = RealPetState.Sitting
        sitFrameIndex = if (reduceAnimations) 4 else 0

        if (!reduceAnimations) {
            for (frame in 1..4) {
                delay(115L)
                sitFrameIndex = frame
            }
        }

        sitUntilDeadline = SystemClock.elapsedRealtime() + 30_000L
        while (SystemClock.elapsedRealtime() < sitUntilDeadline) {
            val remaining = sitUntilDeadline - SystemClock.elapsedRealtime()
            if (remaining <= 0L) break
            val nextBlinkDelay = random.nextLong(2_400L, 5_200L)
            delay(minOf(nextBlinkDelay, remaining))
            if (SystemClock.elapsedRealtime() >= sitUntilDeadline) break
            if (!reduceAnimations) {
                sitFrameIndex = 5
                delay(155L)
                sitFrameIndex = 4
            }
        }

        if (!reduceAnimations) {
            sitFrameIndex = 4
            delay(180L)
            for (frame in 3 downTo 0) {
                sitFrameIndex = frame
                delay(115L)
            }
        }
        state = RealPetState.Idle

        RealPetRuntime.startLeaving()
        val startX = latestRenderX.value
        val startY = latestRenderY.value
        anchorId = null
        x.snapTo(startX)
        y.snapTo(startY)

        val exitLeft = (startX + petSizePx / 2f) <= widthPx / 2f
        val exitTarget = if (exitLeft) -petSizePx * 1.05f else widthPx + petSizePx * 0.05f
        facing = if (exitLeft) RealPetFacing.Left else RealPetFacing.Right
        val isRunning = random.nextBoolean()
        state = if (isRunning) RealPetState.Running else RealPetState.Walking
        val exitDistanceDp = abs(exitTarget - startX) / density.density
        val travelDuration = realPetTravelDuration(exitDistanceDp, running = isRunning)

        try {
            if (reduceAnimations) {
                x.snapTo(exitTarget)
            } else {
                x.animateTo(exitTarget, tween(travelDuration, easing = LinearEasing))
            }
        } finally {
            state = RealPetState.Idle
            if (RealPetRuntime.presence == RealPetPresence.Leaving) {
                if (isDragging) RealPetRuntime.markArrived() else RealPetRuntime.markLeft()
            }
        }
        return true
    }

    suspend fun hopOnPerch(perchId: String) {
        val position = perchPosition(perchId) ?: return
        motionStartX = position.x
        motionStartY = position.y
        motionTargetX = position.x
        motionTargetY = position.y
        motionHeightPx = with(density) { (30 + random.nextInt(0, 25)).dp.toPx() }
        jumpVisual = RealPetJumpVisual.LegacyHop
        motion = RealPetMotion.Jump
        state = RealPetState.Jumping
        try {
            motionProgress.snapTo(0f)
            motionProgress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = random.nextInt(520, 681),
                    easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
                )
            )
        } finally {
            motionProgress.snapTo(0f)
            motion = RealPetMotion.None
            state = RealPetState.Idle
        }
    }

    suspend fun moveToPerch(
        targetId: String,
        napOnArrival: Boolean = false
    ) {
        perchRange(targetId) ?: return
        val targetFraction = when {
            ":pill:" in targetId -> 0.5f
            targetId == REAL_PET_PERCH_AVATAR || targetId == REAL_PET_PERCH_COMPOSER_MIC -> 0.5f
            else -> 0.12f + random.nextFloat() * 0.76f
        }
        val target = perchTargetPosition(targetId, targetFraction) ?: return
        val startX = latestRenderX.value
        val startY = latestRenderY.value
        val distanceDp = kotlin.math.hypot(target.x - startX, target.y - startY) / density.density

        facing = if (target.x < startX) RealPetFacing.Left else RealPetFacing.Right
        try {
            if (reduceAnimations) {
                x.snapTo(target.x)
                y.snapTo(target.y)
            } else if ((distanceDp > 135f || targetId == REAL_PET_PERCH_AVATAR) && anchorId != targetId) {
                // Long-distance travel never becomes one giant diagonal jump. Leave from the
                // nearest horizontal edge, disappear briefly, then re-enter at the target's
                // height. Short distances still use a real jump.
                val exitLeft = (startX + petSizePx / 2f) <= widthPx / 2f
                val exitTarget = if (exitLeft) -petSizePx * 1.05f else widthPx + petSizePx * 0.05f
                motionTargetPerchId = null
                facing = if (exitLeft) RealPetFacing.Left else RealPetFacing.Right
                state = RealPetState.Running
                motionStartX = startX
                motionStartY = startY
                motionTargetX = exitTarget
                motionTargetY = startY
                motionHeightPx = 0f
                motion = RealPetMotion.Drop
                motionProgress.snapTo(0f)
                motionProgress.animateTo(
                    1f,
                    tween(realPetTravelDuration(abs(exitTarget - startX) / density.density, running = true), easing = LinearEasing)
                )
                x.snapTo(exitTarget)
                y.snapTo(startY)
                delay(random.nextLong(260L, 680L))

                val targetNearLeft = target.x < widthPx / 2f
                val enterLeft = if (random.nextFloat() < 0.62f) targetNearLeft else !targetNearLeft
                val enterStart = if (enterLeft) -petSizePx * 0.95f else widthPx + petSizePx * 0.02f
                x.snapTo(enterStart)
                y.snapTo(target.y)
                facing = if (enterLeft) RealPetFacing.Right else RealPetFacing.Left
                state = RealPetState.Running
                motionStartX = enterStart
                motionStartY = target.y
                motionTargetX = target.x
                motionTargetY = target.y
                motion = RealPetMotion.Drop
                motionProgress.snapTo(0f)
                motionProgress.animateTo(
                    1f,
                    tween(realPetTravelDuration(abs(target.x - enterStart) / density.density, running = true), easing = LinearEasing)
                )
                motionProgress.snapTo(0f)
                motion = RealPetMotion.None
                x.snapTo(target.x)
                y.snapTo(target.y)
            } else {
                motionTargetPerchId = targetId
                motionStartX = startX
                motionStartY = startY
                motionTargetX = target.x
                motionTargetY = target.y
                motionHeightPx = with(density) {
                    (38 + (distanceDp / 9f).toInt().coerceIn(0, 52)).dp.toPx()
                }
                jumpVisual = if (distanceDp >= 16f) {
                    RealPetJumpVisual.ForwardTravel
                } else {
                    RealPetJumpVisual.LegacyHop
                }
                motion = RealPetMotion.Jump
                state = RealPetState.Jumping
                motionProgress.snapTo(0f)
                motionProgress.animateTo(
                    1f,
                    tween(
                        durationMillis = (420 + (distanceDp * 2.2f).toInt()).coerceIn(460, 1_050),
                        easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
                    )
                )
                motionProgress.snapTo(0f)
                motion = RealPetMotion.None
                x.snapTo(target.x)
                y.snapTo(target.y)
            }
        } finally {
            motionProgress.snapTo(0f)
            motion = RealPetMotion.None
            motionTargetPerchId = null
        }
        perchProgress.snapTo(targetFraction)
        lastAnchorX = target.x
        lastAnchorY = target.y
        motionTargetPerchId = null

        if (napOnArrival) {
            val napDuration = random.nextLong(8_000L, 16_000L)
            sleepX = target.x
            sleepY = target.y
            sleepPerchFraction = targetFraction
            sleepPoseIndex = (sleepPoseIndex + 1) % REAL_PET_SLEEP_POSE_COUNT
            state = RealPetState.Sleeping
            sleepUntil = SystemClock.elapsedRealtime() + napDuration
        } else {
            state = RealPetState.Idle
        }

        anchorId = targetId
    }

    suspend fun textPrankCue(perchId: String) {
        if (RealPetRuntime.perches[perchId] == null) return
        // One small cue is enough: a single hop, with an optional single meow.
        // No repeated three-hop ritual before every prank.
        hopOnPerch(perchId)
        if (random.nextFloat() < 0.52f) {
            meow(durationMs = if (reduceAnimations) 180L else random.nextLong(360L, 560L))
        }
    }

    suspend fun performDropMotion(
        targetX: Float,
        targetY: Float,
        targetPerchId: String?,
        startX: Float = latestRenderX.value.coerceIn(sidePaddingPx, maxX),
        startY: Float = latestRenderY.value.coerceIn(-petSizePx * 0.25f, groundY),
        heightPx: Float = 0f
    ) {
        sleepUntil = 0L
        sleepPoseIndex = -1
        anchorId = null
        motionTargetPerchId = targetPerchId
        facing = if (targetX < startX) RealPetFacing.Left else RealPetFacing.Right
        jumpVisual = RealPetJumpVisual.LegacyHop
        state = RealPetState.Jumping

        try {
            if (reduceAnimations) {
                x.snapTo(targetX)
                y.snapTo(targetY)
            } else {
                motionStartX = startX
                motionStartY = startY
                motionTargetX = targetX
                motionTargetY = targetY
                motionHeightPx = heightPx
                motion = RealPetMotion.Drop
                motionProgress.snapTo(0f)
                motionProgress.animateTo(
                    1f,
                    tween(460, easing = CubicBezierEasing(0.22f, 0f, 0.58f, 1f))
                )
            }
        } finally {
            motionProgress.snapTo(0f)
            motion = RealPetMotion.None
            x.snapTo(targetX)
            y.snapTo(targetY)
            lastAnchorX = targetX
            lastAnchorY = targetY
            perchProgress.snapTo(0.5f)
            motionTargetPerchId = null
            anchorId = targetPerchId
            state = RealPetState.Idle
        }
    }

    suspend fun detachToGround(force: Boolean = false) {
        if (anchorId == null && !force) return
        val startX = latestRenderX.value.coerceIn(sidePaddingPx, maxX)
        val startY = latestRenderY.value.coerceIn(-petSizePx * 0.25f, groundY)
        performDropMotion(
            targetX = startX,
            targetY = groundY,
            targetPerchId = null,
            startX = startX,
            startY = startY,
            heightPx = with(density) { 28.dp.toPx() }
        )
    }

    suspend fun runOffscreenAndLeave() {
        RealPetRuntime.startLeaving()
        try {
            if (reduceAnimations) {
                return
            }
            // Leave horizontally from whatever height/surface the cat currently occupies.
            // No forced drop to the floor before exiting.
            val startX = latestRenderX.value
            val startY = latestRenderY.value
            anchorId = null
            x.snapTo(startX)
            y.snapTo(startY)
            val exitLeft = (startX + petSizePx / 2f) <= widthPx / 2f
            val exitTarget = if (exitLeft) -petSizePx * 1.05f else widthPx + petSizePx * 0.05f
            facing = if (exitLeft) RealPetFacing.Left else RealPetFacing.Right
            state = RealPetState.Running
            val exitDistanceDp = abs(exitTarget - startX) / density.density
            x.animateTo(exitTarget, tween(realPetTravelDuration(exitDistanceDp, running = true), easing = LinearEasing))
        } finally {
            state = RealPetState.Idle
            if (RealPetRuntime.presence == RealPetPresence.Leaving) {
                if (isDragging) {
                    // A user grab wins over a leave animation. Keep the same visit alive.
                    RealPetRuntime.markArrived()
                } else {
                    // Even if this coroutine was cancelled, never strand the runtime in Leaving.
                    RealPetRuntime.markLeft()
                }
            }
        }
    }

    suspend fun patrolCurrentSurface(running: Boolean) {
        val currentPerch = anchorId
        if (currentPerch != null) {
            // Sometimes use a walk/run decision to relocate to another nearby UI surface.
            // moveToPerch handles short hops with JUMP and long routes by leaving/re-entering.
            if (random.nextFloat() < 0.30f) {
                val nearby = visibleInterestingPerches(currentPerch).filter { targetId ->
                    val target = perchTargetPosition(targetId, 0.5f) ?: return@filter false
                    val distanceDp = kotlin.math.hypot(
                        target.x - latestRenderX.value,
                        target.y - latestRenderY.value
                    ) / density.density
                    distanceDp in 18f..190f
                }
                if (nearby.isNotEmpty()) {
                    moveToPerch(nearby[random.nextInt(nearby.size)])
                    return
                }
            }

            val range = perchRange(currentPerch) ?: return
            val span = range.endInclusive - range.start
            if (span <= with(density) { 12.dp.toPx() }) return
            val current = perchProgress.value
            val targetFraction = if (current < 0.5f) {
                0.62f + random.nextFloat() * 0.32f
            } else {
                0.06f + random.nextFloat() * 0.32f
            }
            val distanceDp = abs(targetFraction - current) * span / density.density
            facing = if (targetFraction < current) RealPetFacing.Left else RealPetFacing.Right
            state = if (running) RealPetState.Running else RealPetState.Walking
            perchProgress.animateTo(
                targetFraction,
                tween(realPetTravelDuration(distanceDp.coerceAtLeast(8f), running), easing = LinearEasing)
            )
            state = RealPetState.Idle
            return
        }

        val minTravel = with(density) { if (running) 72.dp.toPx() else 38.dp.toPx() }
        val maxTravel = with(density) { if (running) 190.dp.toPx() else 115.dp.toPx() }
        val direction = if (random.nextBoolean()) 1f else -1f
        val travel = minTravel + random.nextFloat() * (maxTravel - minTravel)
        val targetX = (x.value + direction * travel).coerceIn(sidePaddingPx, maxX)
        if (abs(targetX - x.value) < with(density) { 10.dp.toPx() }) return
        facing = if (targetX < x.value) RealPetFacing.Left else RealPetFacing.Right
        state = if (running) RealPetState.Running else RealPetState.Walking
        val distanceDp = abs(targetX - x.value) / density.density
        x.animateTo(targetX, tween(realPetTravelDuration(distanceDp, running), easing = LinearEasing))
        state = RealPetState.Idle
    }

    suspend fun takeNap(explicitDurationMs: Long? = null) {
        val napDuration = explicitDurationMs ?: random.nextLong(60_000L, 120_000L) // 1-2 minutes
        sleepX = latestRenderX.value
        sleepY = latestRenderY.value
        sleepPerchFraction = perchProgress.value
        sleepPoseIndex = (sleepPoseIndex + 1) % REAL_PET_SLEEP_POSE_COUNT
        state = RealPetState.Sleeping
        sleepUntil = SystemClock.elapsedRealtime() + napDuration
        // The deadline effect owns wake-up even if navigation restarts behavior effects.
        delay(napDuration)
    }

    suspend fun edgePeek() {
        if (anchorId != null || reduceAnimations) return
        val fromLeft = random.nextBoolean()
        val fromTopCorner = random.nextFloat() < 0.72f

        // Peek is a true corner peek: only the cropped head becomes visible.
        val peekY = if (fromTopCorner) {
            -petSizePx * 0.18f
        } else {
            (groundY - petSizePx * 0.15f).coerceAtLeast(0f)
        }
        val almostHiddenX = if (fromLeft) {
            -petSizePx * 0.78f
        } else {
            widthPx - petSizePx * 0.18f
        }
        val peekX = if (fromLeft) {
            -petSizePx * 0.38f
        } else {
            widthPx - petSizePx * 0.62f
        }

        try {
            anchorId = null
            facing = if (fromLeft) RealPetFacing.Right else RealPetFacing.Left
            y.snapTo(peekY)
            x.snapTo(almostHiddenX)
            state = RealPetState.Peeking
            delay(260L)
            x.animateTo(peekX, tween(300, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))

            peekFleeRequested = false
            peekActive = true
            var waited = 0L
            while (waited < 2_200L && !peekFleeRequested) {
                delay(100L)
                waited += 100L
            }
            peekActive = false

            // Tapped or not, the head retracts behind the same corner. A tap simply
            // makes the retreat quicker and ends a normal guest visit.
            x.animateTo(
                almostHiddenX,
                tween(
                    durationMillis = if (peekFleeRequested) 190 else 300,
                    easing = if (peekFleeRequested) LinearEasing else CubicBezierEasing(0.4f, 0f, 1f, 1f)
                )
            )

            if (peekFleeRequested) {
                RealPetRuntime.startLeaving()
                RealPetRuntime.markLeft()
            } else {
                y.snapTo(groundY)
                x.snapTo(if (fromLeft) sidePaddingPx else maxX)
                state = RealPetState.Idle
            }
        } finally {
            peekActive = false
            peekFleeRequested = false
            if (state == RealPetState.Peeking) {
                state = RealPetState.Idle
            }
        }
    }

    suspend fun sitAndObserve(minMs: Long = 4_000L, maxMs: Long = 10_000L) {
        playSitSequence(
            if (reduceAnimations) 1_200L else random.nextLong(minMs, maxMs + 1L)
        )
    }
}
