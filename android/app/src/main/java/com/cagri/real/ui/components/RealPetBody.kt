package com.cagri.real.ui.components

import android.util.Log
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

internal fun realPetParentCategoryPerchId(perchId: String?): String? {
    val id = perchId ?: return null
    val marker = id.indexOf(":pill:")
    return if (marker >= 0) id.substring(0, marker) else null
}

internal fun realPetPerchIsCategorySurface(perchId: String): Boolean =
    perchId == REAL_PET_PERCH_CATEGORY_HERO ||
        (perchId.startsWith("real_pet:category:") && ":pill:" !in perchId)

internal fun realPetPerchIsInteractiveButton(perchId: String): Boolean =
    perchId == REAL_PET_PERCH_COMPOSER_MIC ||
        perchId == REAL_PET_PERCH_AVATAR ||
        perchId == REAL_PET_PERCH_SHOW_ALL ||
        perchId == REAL_PET_PERCH_DETAIL_BOTTOM_BAR ||
        ":pill:" in perchId

/**
 * Physical body state & motion controller ("Gövde", PLAN.md Sections 5, 11.2, 12.1).
 *
 * Translates [RealPetIntent]s from [RealPetBrain] into smooth screen choreography,
 * strictly enforcing the transition table in [RealPetTransitions] and playing frames
 * from [REAL_PET_FRAME_TABLE].
 */
internal class RealPetBodyState(
    val brain: RealPetBrain,
    val density: Density,
    val view: View,
    val coroutineScope: CoroutineScope,
    val random: Random,
    val reduceAnimations: Boolean,
    val petSizePx: Float,
    val sidePaddingPx: Float,
    val groundPaddingPx: Float,
    val perchSinkPx: Float,
    val snapDistancePx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val maxX: Float,
    val groundY: Float,
    val x: Animatable<Float, AnimationVector1D>,
    val y: Animatable<Float, AnimationVector1D>,
    val motionProgress: Animatable<Float, AnimationVector1D>,
    val perchProgress: Animatable<Float, AnimationVector1D>,
    val stateState: MutableState<RealPetState>,
    val facingState: MutableState<RealPetFacing>,
    val motionState: MutableState<RealPetMotion>,
    val jumpVisualState: MutableState<RealPetJumpVisual>,
    val motionStartXState: MutableFloatState,
    val motionStartYState: MutableFloatState,
    val motionTargetXState: MutableFloatState,
    val motionTargetYState: MutableFloatState,
    val motionHeightPxState: MutableFloatState,
    val anchorIdState: MutableState<String?>,
    val lastAnchorXState: MutableFloatState,
    val lastAnchorYState: MutableFloatState,
    val sleepPoseIndexState: MutableIntState,
    val isDraggingState: MutableState<Boolean>,
    val dragXState: MutableFloatState,
    val dragYState: MutableFloatState,
    val visitExtendMsState: MutableLongState,
    val heartBursts: SnapshotStateList<RealPetHeartBurst>,
    val nextHeartIdState: MutableIntState
) {
    companion object {
        const val BUTTON_MAX_DWELL_MS = 2_000L
    }

    var state: RealPetState
        get() = stateState.value
        private set(value) {
            stateState.value = value
            brain.updateBodyState(value)
        }

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

    var anchorId: String?
        get() = anchorIdState.value
        set(value) { anchorIdState.value = value }

    var lastAnchorX: Float
        get() = lastAnchorXState.floatValue
        set(value) { lastAnchorXState.floatValue = value }

    var lastAnchorY: Float
        get() = lastAnchorYState.floatValue
        set(value) { lastAnchorYState.floatValue = value }

    var sleepPoseIndex: Int
        get() = sleepPoseIndexState.intValue
        set(value) { sleepPoseIndexState.intValue = value }

    var isDragging: Boolean
        get() = isDraggingState.value
        set(value) { isDraggingState.value = value }

    var dragX: Float
        get() = dragXState.floatValue
        set(value) { dragXState.floatValue = value }

    var dragY: Float
        get() = dragYState.floatValue
        set(value) { dragYState.floatValue = value }

    var visitExtendMs: Long
        get() = visitExtendMsState.longValue
        set(value) { visitExtendMsState.longValue = value }

    var nextHeartId: Int
        get() = nextHeartIdState.intValue
        set(value) { nextHeartIdState.intValue = value }

    private var activeIntentJob: Job? = null

    val renderX: Float
        get() {
            val p = if (motion != RealPetMotion.None) motionProgress.value else 0f
            return when {
                isDragging -> dragX
                motion != RealPetMotion.None -> motionStartX + (motionTargetX - motionStartX) * p
                anchorId != null -> perchTargetPosition(anchorId, perchProgress.value)?.x ?: lastAnchorX
                else -> x.value
            }
        }

    val renderY: Float
        get() {
            val p = if (motion != RealPetMotion.None) motionProgress.value else 0f
            val linearY = motionStartY + (motionTargetY - motionStartY) * p
            val arcY = if (motion == RealPetMotion.Jump) -4f * motionHeightPx * p * (1f - p) else 0f
            return when {
                isDragging -> dragY
                motion != RealPetMotion.None -> linearY + arcY
                anchorId != null -> perchTargetPosition(anchorId, perchProgress.value)?.y ?: lastAnchorY
                else -> y.value
            }
        }

    val heartOrigin: Offset
        get() {
            val hx = when {
                state == RealPetState.Sleeping -> petSizePx * 0.50f
                facing == RealPetFacing.Left -> petSizePx * 0.38f
                else -> petSizePx * 0.62f
            }
            val hy = if (state == RealPetState.Sleeping || state == RealPetState.LyingDown) {
                petSizePx * 0.56f
            } else {
                petSizePx * 0.36f
            }
            return Offset(renderX + hx, renderY + hy)
        }

    fun spawnHeartBurst(count: Int = 1) {
        repeat(count.coerceAtLeast(1)) {
            if (heartBursts.size >= 6) heartBursts.removeAt(0)
            heartBursts.add(RealPetHeartBurst(id = nextHeartId++, origin = heartOrigin))
        }
    }

    /**
     * The page moved by [dy] px. The cat keeps its place in the page, so it can scroll out of view.
     * Sitting/sleeping out of view it stays and can be found again; moving out of view it simply leaves.
     */
    fun onContentScrolled(dy: Float) {
        if (dy == 0f || state == RealPetState.Offscreen || isDragging) return
        val anchor = anchorId
        when {
            motion != RealPetMotion.None -> {
                motionStartY += dy
                motionTargetY += dy
            }
            anchor != null -> {
                // A disposed (scrolled away) card stops reporting bounds; keep following it by hand.
                val live = perchTargetPosition(anchor, perchProgress.value)
                if (live != null) {
                    lastAnchorX = live.x
                    lastAnchorY = live.y + dy
                } else {
                    lastAnchorY += dy
                }
            }
            !y.isRunning -> coroutineScope.launch { y.snapTo(y.value + dy) }
        }
        val moving = motion != RealPetMotion.None ||
            state == RealPetState.Walking || state == RealPetState.Running
        if (moving && isOutOfView()) vanish()
    }

    private fun isOutOfView(): Boolean {
        val body = visibleBodyBoundsPx()
        return body.bottom < 0f || body.top > heightPx
    }

    /** Leaves without walking: used when the cat is out of view and would have to move. */
    private fun vanish() {
        Log.d("RealPet", "vanish out of view (state=$state)")
        cancelActiveIntent()
        brain.clearActiveIntent()
        anchorId = null
        motion = RealPetMotion.None
        state = RealPetState.Offscreen
        RealPetRuntime.markLeft()
        coroutineScope.launch {
            motionProgress.snapTo(0f)
            x.snapTo(-petSizePx * 1.05f)
            y.snapTo(groundY)
        }
    }

    /** Called before any walk: a cat left out of view by scrolling does not walk back in. */
    private fun vanishIfOutOfView(): Boolean {
        if (state == RealPetState.Offscreen || !isOutOfView()) return false
        vanish()
        return true
    }

    fun cancelActiveIntent() {
        activeIntentJob?.cancel()
        activeIntentJob = null
    }

    fun stopAll() {
        cancelActiveIntent()
        coroutineScope.launch {
            x.stop()
            y.stop()
            motionProgress.stop()
            motion = RealPetMotion.None
        }
    }

    /**
     * Dispatches a [RealPetEvent] through [RealPetBrain] using wall-clock [nowMs]
     * (`System.currentTimeMillis()`) and launches the resulting [RealPetIntent].
     */
    fun dispatchEvent(event: RealPetEvent, nowMs: Long = System.currentTimeMillis()) {
        val intent = brain.onEvent(event, nowMs)
        Log.d("RealPet", "event $event -> $intent (state=$state)")
        executeIntent(intent ?: return)
    }

    fun executeIntent(intent: RealPetIntent) {
        Log.d("RealPet", "intent $intent (state=$state, body=${System.identityHashCode(this)})")
        activeIntentJob?.cancel()
        activeIntentJob = coroutineScope.launch {
            try {
                when (intent) {
                    is RealPetIntent.Visit -> performVisit(intent)
                    is RealPetIntent.Nap -> performNap(intent)
                    is RealPetIntent.React -> performReaction(intent)
                    is RealPetIntent.AskLove -> performAskLove(intent)
                    is RealPetIntent.Prank -> Unit // Stage 6
                    is RealPetIntent.Leave -> performLeave(running = intent.running)
                }
            } finally {
                if (activeIntentJob == coroutineContext[Job]) {
                    brain.clearActiveIntent()
                }
            }
        }
    }

    /**
     * Strictly steps the body from its current [state] to [target] following
     * the allowed transitions in [RealPetTransitions] (PLAN.md Section 5).
     */
    suspend fun transitionTo(
        target: RealPetState,
        allowOpeningSleep: Boolean = false,
        preferRunning: Boolean = false
    ) {
        if (state == target) return
        val path = RealPetTransitions.findPath(
            from = state,
            to = target,
            allowOpeningSleep = allowOpeningSleep,
            preferRunning = preferRunning
        )
        for (step in path) {
            if (!coroutineContext.isActive) return
            if (step == RealPetState.Sleeping && state != RealPetState.Sleeping) {
                sleepPoseIndex = (sleepPoseIndex + 1) % REAL_PET_SLEEP_POSE_COUNT
            }
            state = step
            if (step != target) {
                val spec = REAL_PET_FRAME_TABLE[step]
                val waitMs = when {
                    reduceAnimations -> 80L
                    spec != null && !spec.looping && spec.totalDurationMs > 0L -> spec.totalDurationMs
                    else -> 220L
                }
                delay(waitMs)
            } else {
                val spec = REAL_PET_FRAME_TABLE[step]
                if (!reduceAnimations && spec != null && !spec.looping && spec.totalDurationMs > 0L) {
                    delay(spec.totalDurationMs)
                }
            }
        }
    }

    /**
     * Stage 1 main cycle (PLAN.md Sections 5 & 12.1):
     * gelir (yürüyerek/koşarak) → durur → bakınır → oturur → (yatıp uyur → uyanır → oturur) → kalkıp gider
     */
    private suspend fun performVisit(intent: RealPetIntent.Visit) {
        if (intent.purpose == RealPetVisitPurpose.StepAsideAndSit) {
            stepAsideAndSit()
            return
        }

        // 1. Enter from offscreen if currently Offscreen
        if (state == RealPetState.Offscreen) {
            enterFromOffscreen(running = intent.preferRunning)
        }

        // 2. Durur & bakınır (Idle: stand and look around)
        transitionTo(RealPetState.Idle)
        delay(randomizedMs(800L, 1_600L))
        if (random.nextFloat() < 0.65f) {
            facing = if (facing == RealPetFacing.Left) RealPetFacing.Right else RealPetFacing.Left
            delay(randomizedMs(700L, 1_400L))
        }

        // 3. Dolaşır (stroll to another spot on ground or hop onto a safe non-button perch)
        val safePerch = intent.targetPerchId ?: pickSafePerch(exclude = anchorId)
        if (safePerch != null && random.nextFloat() < 0.65f) {
            moveToPerch(safePerch, preferRunning = intent.preferRunning)
        } else {
            strollOnGround(running = intent.preferRunning && random.nextBoolean())
        }

        transitionTo(RealPetState.Idle)
        delay(randomizedMs(600L, 1_300L))

        // 4. Oturur (Sit)
        transitionTo(RealPetState.Sitting)
        holdSittingWithButtonGuard(randomizedMs(2_800L, 5_600L))

        // Optional brief self-groom while sitting (Sitting -> Grooming -> Sitting per Section 5)
        if (random.nextFloat() < 0.45f) {
            transitionTo(RealPetState.Grooming)
            transitionTo(RealPetState.Sitting)
            holdSittingWithButtonGuard(randomizedMs(1_400L, 2_800L))
        }

        // 5. Yatıp uyur -> uyanır -> oturur (Sitting -> LyingDown -> Sleeping -> Waking -> Sitting)
        if (intent.purpose == RealPetVisitPurpose.RoamAndNap) {
            transitionTo(RealPetState.Sleeping) // Automatically steps Sitting -> LyingDown -> Sleeping
            val baseNapMs = randomizedMs(7_000L, 14_000L)
            delay(baseNapMs + consumeVisitExtension())
            // Wake up: Sleeping -> Waking (gerinme, esneme) -> Sitting
            transitionTo(RealPetState.Sitting)
            holdSittingWithButtonGuard(randomizedMs(1_800L, 3_600L))
        } else {
            val extra = consumeVisitExtension()
            if (extra > 0L) {
                holdSittingWithButtonGuard(extra)
            }
        }

        // 6. Kalkıp gider (Sitting -> Idle -> Walking/Running -> Offscreen)
        if (!brain.isUserBusy) {
            performLeave(running = brain.energy >= 0.72f && random.nextBoolean())
        }
    }

    private suspend fun performNap(intent: RealPetIntent.Nap) {
        if (state == RealPetState.Offscreen && intent.startAlreadySleeping) {
            val perchId = intent.targetPerchId ?: pickSafePerch()
            val pos = perchId?.let { perchTargetPosition(it, 0.5f) }
            if (perchId != null && pos != null) {
                anchorId = perchId
                perchProgress.snapTo(0.5f)
                x.snapTo(pos.x)
                y.snapTo(pos.y)
                lastAnchorX = pos.x
                lastAnchorY = pos.y
            } else {
                anchorId = null
                val groundX = sidePaddingPx + random.nextFloat() * (maxX - sidePaddingPx).coerceAtLeast(1f)
                x.snapTo(groundX)
                y.snapTo(groundY)
            }
            RealPetRuntime.markArrived()
            transitionTo(RealPetState.Sleeping, allowOpeningSleep = true)
        } else {
            if (state == RealPetState.Offscreen) {
                enterFromOffscreen(running = false)
            }
            transitionTo(RealPetState.Sleeping)
        }

        delay(randomizedMs(9_000L, 18_000L) + consumeVisitExtension())
        // Wake up: Sleeping -> Waking -> Sitting
        transitionTo(RealPetState.Sitting)
        holdSittingWithButtonGuard(randomizedMs(2_000L, 4_000L))
        performLeave(running = false)
    }

    private suspend fun performReaction(intent: RealPetIntent.React) {
        when (intent.kind) {
            RealPetReactionKind.WakeAndSit -> {
                // PLAN.md Section 5 & 7: "Uyurken dokunulursa uyanır (wake), oturur"
                spawnHeartBurst(1)
                RealHaptics.tick(view)
                transitionTo(RealPetState.Sitting) // Sleeping -> Waking -> Sitting
                holdSittingWithButtonGuard(randomizedMs(4_000L, 7_500L))
                performLeave(running = false)
            }

            RealPetReactionKind.StopTurnAndSit -> {
                // PLAN.md Section 5: "Koşarken dokunulursa durur, döner, oturur."
                x.stop()
                y.stop()
                motionProgress.stop()
                motion = RealPetMotion.None
                spawnHeartBurst(1)
                RealHaptics.tick(view)
                facing = if (facing == RealPetFacing.Left) RealPetFacing.Right else RealPetFacing.Left
                transitionTo(RealPetState.Idle)
                delay(randomizedMs(350L, 650L))
                transitionTo(RealPetState.Sitting)
                holdSittingWithButtonGuard(randomizedMs(4_000L, 7_500L))
                performLeave(running = false)
            }

            RealPetReactionKind.TapHeart -> {
                // PLAN.md Section 7: "Dokunma -> Kalp, kedi biraz daha kalır"
                spawnHeartBurst(1)
                RealHaptics.tick(view)
                visitExtendMs = (visitExtendMs + 5_000L).coerceAtMost(20_000L)
                if (state == RealPetState.Idle) {
                    transitionTo(RealPetState.Sitting)
                }
                holdSittingWithButtonGuard(randomizedMs(4_000L, 7_000L))
                performLeave(running = false)
            }

            RealPetReactionKind.HoldPurr -> {
                // PLAN.md Section 7 & 12.1: "Basılı tutma -> Oturur, mırlar (purr)"
                spawnHeartBurst(2)
                RealHaptics.success(view)
                transitionTo(RealPetState.Sitting)
                transitionTo(RealPetState.Purring)
                delay(randomizedMs(2_600L, 4_200L))
                transitionTo(RealPetState.Sitting)
                holdSittingWithButtonGuard(randomizedMs(3_000L, 5_500L))
                performLeave(running = false)
            }

            RealPetReactionKind.RepeatedTapPurrAndLieDown -> {
                // PLAN.md Section 7: "Art arda dokunma -> Kalpler çoğalır, mırlar, sonra uzanır"
                spawnHeartBurst(3)
                RealHaptics.success(view)
                transitionTo(RealPetState.Sitting)
                transitionTo(RealPetState.Purring)
                delay(randomizedMs(2_200L, 3_400L))
                transitionTo(RealPetState.Sitting)
                transitionTo(RealPetState.Sleeping) // Sitting -> LyingDown -> Sleeping
                delay(randomizedMs(7_000L, 12_000L))
                transitionTo(RealPetState.Sitting) // Sleeping -> Waking -> Sitting
                holdSittingWithButtonGuard(randomizedMs(1_800L, 3_200L))
                performLeave(running = false)
            }

            RealPetReactionKind.SettledAfterDrag -> {
                // PLAN.md Section 7: "Sürükleme -> Bıraktığın yere oturur; kartın üstüne bırakılırsa orada kalır"
                transitionTo(RealPetState.Sitting)
                val stayMs = if (intent.targetPerchId != null) {
                    randomizedMs(8_000L, 14_000L)
                } else {
                    randomizedMs(4_500L, 8_000L)
                }
                holdSittingWithButtonGuard(stayMs)
                if (random.nextBoolean()) {
                    transitionTo(RealPetState.Sleeping)
                    delay(randomizedMs(6_000L, 11_000L))
                    transitionTo(RealPetState.Sitting)
                    delay(randomizedMs(1_200L, 2_200L))
                }
                performLeave(running = false)
            }

            else -> Unit
        }
    }

    private suspend fun performAskLove(intent: RealPetIntent.AskLove) {
        if (state == RealPetState.Offscreen) {
            enterFromOffscreen(running = false)
        }
        val perch = intent.targetPerchId ?: pickSafePerch()
        if (perch != null && perch != anchorId) {
            moveToPerch(perch, preferRunning = false)
        }
        transitionTo(RealPetState.Sitting)
        transitionTo(RealPetState.Calling)
        transitionTo(RealPetState.Sitting)
        holdSittingWithButtonGuard(randomizedMs(3_500L, 6_000L))
        performLeave(running = false)
    }

    /**
     * Moves the cat aside to a quiet screen edge and sits when the user starts typing/recording
     * or when the cat has been sitting on an interactive button for >2s (PLAN.md Section 3 Rules 1 & 2).
     */
    suspend fun stepAsideAndSit() {
        if (state == RealPetState.Offscreen) return
        if (isOutOfView()) return // already out of the user's way
        if (state == RealPetState.Sleeping || state == RealPetState.LyingDown) {
            transitionTo(RealPetState.Sitting)
        }
        transitionTo(RealPetState.Idle)
        val currentX = renderX
        val currentY = renderY
        anchorId = null
        x.snapTo(currentX)
        y.snapTo(currentY)
        val targetX = if (currentX + petSizePx / 2f <= widthPx / 2f) sidePaddingPx else maxX
        if (abs(targetX - currentX) > with(density) { 8.dp.toPx() }) {
            facing = if (targetX < currentX) RealPetFacing.Left else RealPetFacing.Right
            state = RealPetState.Walking
            val distDp = abs(targetX - currentX) / density.density
            if (reduceAnimations) {
                x.snapTo(targetX)
                y.snapTo(groundY)
            } else {
                x.animateTo(targetX, tween(realPetTravelDuration(distDp, running = false), easing = LinearEasing))
                if (abs(y.value - groundY) > 2f) {
                    y.animateTo(groundY, tween(260, easing = LinearEasing))
                }
            }
        }
        transitionTo(RealPetState.Idle)
        transitionTo(RealPetState.Sitting)
    }

    /**
     * Leaves the screen following Section 5:
     * (from Sleeping -> Waking -> Sitting -> Idle -> Walking/Running -> Offscreen)
     */
    suspend fun performLeave(running: Boolean) {
        if (state == RealPetState.Offscreen) {
            RealPetRuntime.markLeft()
            return
        }
        if (vanishIfOutOfView()) return
        RealPetRuntime.startLeaving()
        try {
            if (state != RealPetState.Walking && state != RealPetState.Running) {
                transitionTo(RealPetState.Idle, preferRunning = running)
            }
            val startX = renderX
            val startY = renderY
            anchorId = null
            x.snapTo(startX)
            y.snapTo(startY)

            val exitLeft = (startX + petSizePx / 2f) <= widthPx / 2f
            val exitTarget = if (exitLeft) -petSizePx * 1.05f else widthPx + petSizePx * 0.05f
            facing = if (exitLeft) RealPetFacing.Left else RealPetFacing.Right
            state = if (running) RealPetState.Running else RealPetState.Walking
            val exitDistanceDp = abs(exitTarget - startX) / density.density

            if (reduceAnimations) {
                x.snapTo(exitTarget)
            } else {
                x.animateTo(
                    exitTarget,
                    tween(realPetTravelDuration(exitDistanceDp, running = running), easing = LinearEasing)
                )
            }
            state = RealPetState.Offscreen
        } finally {
            if (isDragging) {
                RealPetRuntime.markArrived()
            } else {
                state = RealPetState.Offscreen
                RealPetRuntime.markLeft()
            }
        }
    }

    private suspend fun enterFromOffscreen(running: Boolean) {
        RealPetRuntime.requestVisit(RealPetVisitReason.CasualVisit, 45_000L)
        val enterLeft = random.nextBoolean()
        val enterStart = if (enterLeft) -petSizePx * 0.95f else widthPx + petSizePx * 0.02f
        val span = (maxX - sidePaddingPx).coerceAtLeast(1f)
        val targetX = if (enterLeft) {
            sidePaddingPx + span * (0.18f + random.nextFloat() * 0.35f)
        } else {
            maxX - span * (0.18f + random.nextFloat() * 0.35f)
        }.coerceIn(sidePaddingPx, maxX)

        anchorId = null
        x.snapTo(enterStart)
        y.snapTo(groundY)
        facing = if (enterLeft) RealPetFacing.Right else RealPetFacing.Left
        state = if (running) RealPetState.Running else RealPetState.Walking
        RealPetRuntime.markArrived()

        val distDp = abs(targetX - enterStart) / density.density
        if (reduceAnimations) {
            x.snapTo(targetX)
        } else {
            x.animateTo(
                targetX,
                tween(realPetTravelDuration(distDp, running = running), easing = LinearEasing)
            )
        }
        RealHaptics.tick(view)
    }

    private suspend fun strollOnGround(running: Boolean) {
        if (vanishIfOutOfView()) return
        val currentX = renderX
        val minTravel = with(density) { if (running) 64.dp.toPx() else 36.dp.toPx() }
        val maxTravel = with(density) { if (running) 160.dp.toPx() else 108.dp.toPx() }
        val direction = if (currentX - sidePaddingPx < maxX - currentX) 1f else -1f
        val travel = minTravel + random.nextFloat() * (maxTravel - minTravel)
        val targetX = (currentX + direction * travel).coerceIn(sidePaddingPx, maxX)
        if (abs(targetX - currentX) < with(density) { 10.dp.toPx() }) return

        anchorId = null
        x.snapTo(currentX)
        y.snapTo(groundY)
        facing = if (targetX < currentX) RealPetFacing.Left else RealPetFacing.Right
        transitionTo(if (running) RealPetState.Running else RealPetState.Walking)
        val distDp = abs(targetX - currentX) / density.density
        if (reduceAnimations) {
            x.snapTo(targetX)
        } else {
            x.animateTo(
                targetX,
                tween(realPetTravelDuration(distDp, running = running), easing = LinearEasing)
            )
        }
    }

    suspend fun moveToPerch(targetId: String, preferRunning: Boolean = false) {
        if (vanishIfOutOfView()) return
        val fraction = 0.20f + random.nextFloat() * 0.60f
        val target = perchTargetPosition(targetId, fraction) ?: return
        val startX = renderX
        val startY = renderY
        val distDp = hypot(target.x - startX, target.y - startY) / density.density

        facing = if (target.x < startX) RealPetFacing.Left else RealPetFacing.Right
        // Section 5: Walking/Running -> Hopping/Jumping -> Sitting/Idle
        if (state != RealPetState.Walking && state != RealPetState.Running) {
            transitionTo(if (preferRunning) RealPetState.Running else RealPetState.Walking)
        }

        motionStartX = startX
        motionStartY = startY
        motionTargetX = target.x
        motionTargetY = target.y
        motionHeightPx = with(density) { (32 + (distDp / 8f).toInt().coerceIn(0, 44)).dp.toPx() }
        val useHop = distDp < 95f
        jumpVisual = if (useHop) RealPetJumpVisual.LegacyHop else RealPetJumpVisual.ForwardTravel
        state = if (useHop) RealPetState.Hopping else RealPetState.Jumping
        motion = RealPetMotion.Jump

        try {
            if (reduceAnimations) {
                x.snapTo(target.x)
                y.snapTo(target.y)
            } else {
                motionProgress.snapTo(0f)
                motionProgress.animateTo(
                    1f,
                    tween(
                        durationMillis = (460 + (distDp * 2.0f).toInt()).coerceIn(480, 920),
                        easing = RealMotion.Standard
                    )
                )
            }
        } finally {
            motionProgress.snapTo(0f)
            motion = RealPetMotion.None
            x.snapTo(target.x)
            y.snapTo(target.y)
            perchProgress.snapTo(fraction)
            lastAnchorX = target.x
            lastAnchorY = target.y
            anchorId = targetId
            state = if (useHop) RealPetState.Sitting else RealPetState.Idle
        }
    }

    /**
     * Holds the sitting posture for [durationMs], but enforces PLAN.md Section 3 Rule 1:
     * "Bir butonun üstünde 2 saniyeden fazla durmaz, kenara kayar."
     */
    private suspend fun holdSittingWithButtonGuard(durationMs: Long) {
        var remaining = durationMs.coerceAtLeast(0L)
        var onButtonMs = 0L
        val step = 200L
        while (remaining > 0L && coroutineContext.isActive) {
            val slice = minOf(step, remaining)
            delay(slice)
            remaining -= slice
            if (isCurrentlyOverInteractiveButton()) {
                onButtonMs += slice
                if (onButtonMs >= BUTTON_MAX_DWELL_MS) {
                    slideOffInteractiveButton()
                    onButtonMs = 0L
                }
            } else {
                onButtonMs = 0L
            }
        }
    }

    private fun isCurrentlyOverInteractiveButton(): Boolean {
        val currentAnchor = anchorId
        if (currentAnchor != null && realPetPerchIsInteractiveButton(currentAnchor)) return true
        val bodyRect = visibleBodyBoundsPx()
        return RealPetRuntime.perches.any { (id, bounds) ->
            realPetPerchIsInteractiveButton(id) && bodyRect.overlaps(bounds)
        }
    }

    private suspend fun slideOffInteractiveButton() {
        if (vanishIfOutOfView()) return
        val currentX = renderX
        val currentY = renderY
        anchorId = null
        x.snapTo(currentX)
        y.snapTo(currentY)
        val targetX = if (currentX + petSizePx / 2f <= widthPx / 2f) sidePaddingPx else maxX
        facing = if (targetX < currentX) RealPetFacing.Left else RealPetFacing.Right
        transitionTo(RealPetState.Idle)
        transitionTo(RealPetState.Walking)
        val distDp = abs(targetX - currentX) / density.density
        if (reduceAnimations) {
            x.snapTo(targetX)
            y.snapTo(groundY)
        } else {
            x.animateTo(targetX, tween(realPetTravelDuration(distDp, running = false), easing = LinearEasing))
            if (abs(y.value - groundY) > 2f) {
                y.animateTo(groundY, tween(240, easing = LinearEasing))
            }
        }
        transitionTo(RealPetState.Idle)
        transitionTo(RealPetState.Sitting)
    }

    fun visibleBodyBoundsPx(): Rect {
        val left = renderX + petSizePx * 0.22f
        val right = renderX + petSizePx * 0.78f
        val top = renderY + petSizePx * 0.42f
        val bottom = renderY + petSizePx * 0.96f
        return Rect(left, top, right, bottom)
    }

    fun perchRange(perchId: String?): ClosedFloatingPointRange<Float>? {
        val bounds = perchId?.let { RealPetRuntime.perches[it] } ?: return null
        return realPetPerchRange(bounds.left, bounds.right, petSizePx, sidePaddingPx, maxX)
    }

    fun perchY(perchId: String, bounds: Rect): Float {
        val parentCategoryBounds = realPetParentCategoryPerchId(perchId)
            ?.let { RealPetRuntime.perches[it] }
        if (perchId == REAL_PET_PERCH_COMPOSER_TEXT || perchId == REAL_PET_PERCH_COMPOSER_MIC) {
            return (bounds.center.y - petSizePx / 2f).coerceIn(-petSizePx * 0.12f, groundY)
        }
        // Not clamped to the screen: a card scrolled away takes the cat with it, out of view.
        val surfaceTop = parentCategoryBounds?.top ?: bounds.top
        return surfaceTop - petSizePx + perchSinkPx
    }

    fun perchTargetPosition(perchId: String?, fraction: Float): Offset? {
        val id = perchId ?: return null
        val bounds = RealPetRuntime.perches[id] ?: return null
        val range = perchRange(id) ?: return null
        return Offset(
            x = range.start + (range.endInclusive - range.start) * fraction.coerceIn(0f, 1f),
            y = perchY(id, bounds)
        )
    }

    private fun pickSafePerch(exclude: String? = null): String? {
        val candidates = RealPetRuntime.perches.entries
            .filter { (id, bounds) ->
                id != exclude &&
                    !realPetPerchIsInteractiveButton(id) &&
                    id != REAL_PET_PERCH_COMPOSER_TEXT &&
                    perchY(id, bounds) in 0f..groundY &&
                    bounds.right >= 0f &&
                    bounds.left <= widthPx
            }
            .map { it.key }
        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }

    private fun consumeVisitExtension(): Long {
        val ext = visitExtendMs
        visitExtendMs = 0L
        return ext
    }

    private fun randomizedMs(minMs: Long, maxMs: Long): Long {
        if (reduceAnimations) return (minMs / 3L).coerceAtLeast(180L)
        return random.nextLong(minMs, (maxMs + 1L).coerceAtLeast(minMs + 1L))
    }
}

/**
 * Visual body composable ("Gövde") with a strict visible-body-only touch hitbox
 * so transparent canvas padding never blocks user taps on underlying UI elements
 * (PLAN.md Section 3 Rule 1).
 */
@Composable
internal fun RealPetBody(
    bodyState: RealPetBodyState,
    petSize: Dp,
    petVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!petVisible || bodyState.state == RealPetState.Offscreen) return

    val p = if (bodyState.motion != RealPetMotion.None) bodyState.motionProgress.value else 0f
    val edgeSquash = if (bodyState.motion == RealPetMotion.Jump) {
        val takeoff = ((0.12f - p) / 0.12f).coerceIn(0f, 1f)
        val landing = ((p - 0.88f) / 0.12f).coerceIn(0f, 1f)
        maxOf(takeoff, landing)
    } else 0f
    val jumpScaleX = 1f + edgeSquash * 0.035f
    val jumpScaleY = 1f - edgeSquash * 0.035f
    // All 640x560 frames face left; mirror horizontally when facing right (PLAN.md Section 9 & 11.3).
    val directionScale = if (bodyState.facing == RealPetFacing.Left) 1f else -1f

    val hitWidth = petSize * 0.54f
    val hitHeight = if (bodyState.state == RealPetState.Sleeping || bodyState.state == RealPetState.LyingDown) {
        petSize * 0.38f
    } else {
        petSize * 0.50f
    }

    Box(
        modifier = modifier
            .size(petSize)
            .graphicsLayer {
                translationX = bodyState.renderX
                translationY = bodyState.renderY
            }
    ) {
        RealPetRenderer(
            state = bodyState.state,
            sleepPoseIndex = bodyState.sleepPoseIndex,
            reduceAnimations = bodyState.reduceAnimations,
            jumpProgress = p,
            jumpVisual = bodyState.jumpVisual,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = directionScale * jumpScaleX
                    scaleY = jumpScaleY
                }
        )

        // Touch target is strictly confined to the visible cat body at the bottom-center
        // so taps outside the cat's body pass right through to the app UI.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = hitWidth, height = hitHeight)
                .realPetGestures(bodyState, petVisible)
        )
    }
}