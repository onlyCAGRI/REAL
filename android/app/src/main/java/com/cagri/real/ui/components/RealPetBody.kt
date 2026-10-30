package com.cagri.real.ui.components

import android.os.SystemClock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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

        /** Awake and settled; asleep it rides the card instead of chasing the scroll. */
        private val FOLLOW_STATES = setOf(
            RealPetState.Idle, RealPetState.Sitting, RealPetState.Purring, RealPetState.Grooming,
            RealPetState.Pawing, RealPetState.Calling, RealPetState.Meowing
        )

        private const val LOVE_GAP_MS = 10_000L
        private const val LOVE_PURR_AT = 3
        private const val LOVE_SLEEP_AT = 9

        /** Resting on a card; walking or jumping cats are already on the move. */
        private val STARTLE_STATES = setOf(
            RealPetState.Idle, RealPetState.Sitting, RealPetState.Sleeping, RealPetState.LyingDown,
            RealPetState.Waking, RealPetState.Purring, RealPetState.Grooming, RealPetState.Pawing,
            RealPetState.Calling, RealPetState.Meowing, RealPetState.Drinking
        )
    }

    var state: RealPetState
        get() = stateState.value
        private set(value) {
            if (value != stateState.value) {
                Log.d("RealPet", "state ${stateState.value} -> $value at ${renderX.toInt()},${renderY.toInt()} anchor=$anchorId")
            }
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

    /** In-between frames shown while [transitionTo] steps between poses that do not meet. */
    var bridgeFrames by mutableStateOf<IntArray?>(null)
        private set

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

    /** Sitting on a home card while a card's detail screen covers it. */
    val isCovered: Boolean
        get() = (anchorId ?: heldOverPerch)?.let { RealPetRuntime.isPerchCovered(it) } == true

    /** The card the cat was just lifted off while [watchPerchShifts] decides whether to startle. */
    private var heldOverPerch by mutableStateOf<String?>(null)

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
        scrollSincePerchCheck += dy
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
        if (moving && isOutOfView()) vanish() else maybeFollowScroll()
    }

    /** Dozing because the user went quiet: the next touch anywhere startles it awake. */
    var idleNapActive = false

    fun startleAwake() {
        idleNapActive = false
        val asleep = state == RealPetState.Sleeping || state == RealPetState.LyingDown || state == RealPetState.Waking
        if (!asleep) return
        Log.d("RealPet", "startled awake by a touch")
        cancelActiveIntent()
        brain.clearActiveIntent()
        activeIntentJob = coroutineScope.launch {
            bridgeFrames = null
            // Jolts straight onto its feet (no waking frames), looks around, then goes.
            state = RealPetState.Idle
            delay(randomizedMs(500L, 900L))
            facing = if (facing == RealPetFacing.Left) RealPetFacing.Right else RealPetFacing.Left
            delay(randomizedMs(400L, 700L))
            transitionTo(RealPetState.Sitting)
            delay(randomizedMs(1_200L, 2_200L))
            performLeave(running = random.nextBoolean())
        }
    }

    private var followHopping = false

    private fun isCardPerch(id: String) =
        id.startsWith("real_pet:reminder:") || (id.startsWith("real_pet:category:") && !id.contains(":pill:"))

    /**
     * The page scrolls the cat's card toward an edge: it hops card to card to stay in view, one card
     * or now and then two. Asleep it just rides along; scrolled too fast, the card wins and it is gone.
     */
    private fun maybeFollowScroll() {
        if (followHopping || motion != RealPetMotion.None || isDragging || brain.isUserBusy) return
        if (state !in FOLLOW_STATES) return
        val anchor = anchorId ?: return
        if (!isCardPerch(anchor)) return
        val y = renderY
        // y is the sprite's top; the body is its lower half. Hop targets sit well inside the band.
        val goingUp = y < heightPx * 0.18f
        val goingDown = y > groundY - petSizePx * 0.15f
        if (!goingUp && !goingDown) return
        val lo = heightPx * 0.28f
        val hi = groundY - petSizePx * 0.5f
        val candidates = RealPetRuntime.perches.entries
            .filter { (id, bounds) ->
                val py = perchY(id, bounds)
                id != anchor && isCardPerch(id) && !RealPetRuntime.isPerchCovered(id) &&
                    py in lo..hi && (if (goingUp) py > y else py < y)
            }
            .sortedBy { abs(perchY(it.key, it.value) - y) }
        if (candidates.isEmpty()) return
        val target = (if (candidates.size > 1 && random.nextFloat() < 0.35f) candidates[1] else candidates[0]).key
        Log.d("RealPet", "follow scroll -> $target")
        cancelActiveIntent()
        brain.clearActiveIntent()
        activeIntentJob = coroutineScope.launch { performFollowHop(target) }
    }

    /** Straight from sitting into the hop (no stand-up first: the card is leaving), then settles there. */
    private suspend fun performFollowHop(targetId: String) {
        followHopping = true
        try {
            val fraction = 0.25f + random.nextFloat() * 0.5f
            val target = perchTargetPosition(targetId, fraction) ?: return
            val startX = renderX
            val startY = renderY
            val distDp = hypot(target.x - startX, target.y - startY) / density.density
            anchorId = null
            x.snapTo(startX)
            y.snapTo(startY)
            facing = if (target.x < startX) RealPetFacing.Left else RealPetFacing.Right
            motionStartX = startX
            motionStartY = startY
            motionTargetX = target.x
            motionTargetY = target.y
            motionHeightPx = with(density) { (28 + (distDp / 10f).toInt().coerceIn(0, 30)).dp.toPx() }
            val useHop = distDp < 95f
            jumpVisual = if (useHop) RealPetJumpVisual.LegacyHop else RealPetJumpVisual.ForwardTravel
            state = if (useHop) RealPetState.Hopping else RealPetState.Jumping
            motion = RealPetMotion.Jump
            try {
                if (!reduceAnimations) {
                    motionProgress.snapTo(0f)
                    motionProgress.animateTo(
                        1f,
                        tween((380 + (distDp * 1.4f).toInt()).coerceIn(400, 720), easing = RealMotion.Standard)
                    )
                }
            } finally {
                motionProgress.snapTo(0f)
                motion = RealPetMotion.None
                // Re-read: the page kept scrolling while it was in the air.
                val landed = perchTargetPosition(targetId, fraction) ?: target
                x.snapTo(landed.x)
                y.snapTo(landed.y)
                perchProgress.snapTo(fraction)
                lastAnchorX = landed.x
                lastAnchorY = landed.y
                anchorId = targetId
                state = RealPetState.Sitting
            }
        } finally {
            followHopping = false
        }
        holdSittingWithButtonGuard(randomizedMs(4_000L, 8_000L))
        if (random.nextFloat() < 0.35f) {
            transitionTo(RealPetState.Sleeping)
            delay(randomizedMs(8_000L, 14_000L))
            transitionTo(RealPetState.Sitting)
            delay(randomizedMs(1_200L, 2_000L))
        }
        performLeave(running = false)
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
                    is RealPetIntent.Pose -> performPose(intent)
                    is RealPetIntent.Prank -> performPrank(intent)
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
            val bridge = if (reduceAnimations) null else realPetBridgeFrames(state, step)
            if (bridge != null) {
                bridgeFrames = bridge
                // A little slack so the renderer's decode of the first frames does not cut the end off.
                delay(bridge.size * REAL_PET_BRIDGE_FRAME_MS + 60L)
            }
            state = step
            bridgeFrames = null
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
        if (intent.purpose == RealPetVisitPurpose.RunThrough) {
            if (state == RealPetState.Offscreen) runThrough()
            return
        }
        if (intent.purpose == RealPetVisitPurpose.ListenToMic) {
            listenToMic()
            return
        }
        if (intent.purpose == RealPetVisitPurpose.RemindDueSoon) {
            remindOnCard(intent.targetPerchId)
            return
        }

        val foundSitting = intent.purpose == RealPetVisitPurpose.FoundSitting && state == RealPetState.Offscreen
        if (foundSitting) {
            // Already sitting somewhere when the app opens: stays a while, then carries on.
            placeForOpening(intent.targetPerchId)
            transitionTo(RealPetState.Sitting, allowOpeningSleep = true)
            holdSittingWithButtonGuard(randomizedMs(4_000L, 8_000L))
        } else {
            // 1. Enter from offscreen if currently Offscreen
            if (state == RealPetState.Offscreen) {
                if (intent.startDelayMs > 0L) delay(intent.startDelayMs)
                // Not while the user is on another screen (card detail etc.); walks in once they are back.
                while (brain.isUserBusy) delay(400L)
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
            if (safePerch != null && (intent.targetPerchId != null || random.nextFloat() < 0.65f)) {
                moveToPerch(safePerch, preferRunning = intent.preferRunning)
            } else {
                strollOnGround(running = intent.preferRunning && random.nextBoolean())
            }

            transitionTo(RealPetState.Idle)
            delay(randomizedMs(600L, 1_300L))

            // 4. Oturur (Sit)
            transitionTo(RealPetState.Sitting)
            holdSittingWithButtonGuard(randomizedMs(2_800L, 5_600L))
        }

        // Something to do while sitting (Sitting -> X -> Sitting per Section 5), not every time.
        val pastime = random.nextFloat()
        val activity = when {
            pastime < 0.30f -> RealPetState.Grooming
            pastime < 0.48f -> RealPetState.Purring
            pastime < 0.58f -> RealPetState.Pawing
            else -> null
        }
        if (activity == RealPetState.Grooming && !reduceAnimations && random.nextBoolean()) {
            longGroom()
            holdSittingWithButtonGuard(randomizedMs(1_400L, 2_800L))
        } else if (activity != null) {
            transitionTo(activity)
            if (activity == RealPetState.Purring) delay(randomizedMs(2_400L, 4_000L))
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

    /**
     * A proper wash: licks the paw back and forth a few times (groom 5–10), rubs its face a few times
     * (12–18), then puts the paw down. Each frame shown twice, ~60 ms, so the licking reads calm.
     */
    private suspend fun longGroom() {
        transitionTo(RealPetState.Sitting)
        val lick = (5..10) + (9 downTo 6)
        val wash = (12..18) + (17 downTo 13)
        val order = (1..4) +
            List(random.nextInt(3, 7)) { lick }.flatten() + (5..11) +
            List(random.nextInt(2, 5)) { wash }.flatten() + (12..21)
        val frames = order.flatMap { listOf(GROOM_FRAMES[it - 1], GROOM_FRAMES[it - 1]) }.toIntArray()
        bridgeFrames = frames
        delay(frames.size * REAL_PET_BRIDGE_FRAME_MS + 60L)
        bridgeFrames = null
    }

    /** Puts the cat straight onto a perch (or the ground) for the "already here" app-open poses. */
    private suspend fun placeForOpening(targetPerchId: String?) {
        val perchId = targetPerchId ?: pickSafePerch()
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
        facing = if (random.nextBoolean()) RealPetFacing.Left else RealPetFacing.Right
        RealPetRuntime.markArrived()
    }

    /**
     * Hurries over to the mic, sits beside it facing it and opens and closes its mouth in short bursts
     * while the user speaks (silent: the meow sound is off while the mic records). Leaves after.
     */
    private suspend fun listenToMic() {
        val mic = RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER_MIC] ?: RealPetRuntime.lastMicBounds
        if (mic == null) {
            Log.d("RealPet", "mic: no mic bounds known")
            return
        }
        // Just clear of the button, or the "sitting on a button" guard moves it away again.
        val targetX = (mic.left - petSizePx * 0.84f).coerceIn(sidePaddingPx, maxX)
        RealPetRuntime.requestVisit(RealPetVisitReason.CasualVisit, 60_000L)
        if (state == RealPetState.Offscreen) {
            anchorId = null
            x.snapTo(widthPx + petSizePx * 0.02f)
            y.snapTo(groundY)
            facing = RealPetFacing.Left
            state = RealPetState.Running
            RealPetRuntime.markArrived()
        } else {
            val startX = renderX
            val startY = renderY
            anchorId = null
            x.snapTo(startX)
            y.snapTo(startY)
            facing = if (targetX < startX) RealPetFacing.Left else RealPetFacing.Right
            if (abs(startY - groundY) > 2f) {
                if (reduceAnimations) y.snapTo(groundY) else hopToGround(towardX = targetX)
            }
            transitionTo(RealPetState.Running, preferRunning = true)
        }
        val distDp = abs(targetX - x.value) / density.density
        if (reduceAnimations) {
            x.snapTo(targetX)
        } else if (distDp > 4f) {
            facing = if (targetX < x.value) RealPetFacing.Left else RealPetFacing.Right
            x.animateTo(targetX, tween(realPetTravelDuration(distDp, running = true), easing = LinearEasing))
        }
        transitionTo(RealPetState.Idle)
        facing = RealPetFacing.Right // the mic sits to its right
        transitionTo(RealPetState.Sitting)
        // Chatter: quick syllables cut from the meow (mouth opens a random amount and shuts), short gaps,
        // now and then a breath. Every other frame at bridge speed, so one syllable is ~0.15–0.35 s.
        val closed = intArrayOf(MEOW_FRAMES[0])
        while (RealPetRuntime.composerMicActive) {
            val open = random.nextInt(4, 12)
            val up = (0..open step 2).map { MEOW_FRAMES[it] }
            val syllable = (up + up.reversed().drop(1)).toIntArray()
            bridgeFrames = syllable
            delay(syllable.size * REAL_PET_BRIDGE_FRAME_MS + 20L)
            bridgeFrames = closed
            delay(if (random.nextFloat() < 0.15f) randomizedMs(380L, 700L) else randomizedMs(40L, 160L))
        }
        bridgeFrames = null
        transitionTo(RealPetState.Sitting)
        holdSittingWithButtonGuard(randomizedMs(2_000L, 3_500L))
        performLeave(running = false)
    }

    /** Dashes in from one edge and straight out of the opposite one. */
    private suspend fun runThrough() {
        RealPetRuntime.requestVisit(RealPetVisitReason.CasualVisit, 15_000L)
        val fromLeft = random.nextBoolean()
        val start = if (fromLeft) -petSizePx * 0.95f else widthPx + petSizePx * 0.02f
        val end = if (fromLeft) widthPx + petSizePx * 0.05f else -petSizePx * 1.05f
        anchorId = null
        x.snapTo(start)
        y.snapTo(groundY)
        facing = if (fromLeft) RealPetFacing.Right else RealPetFacing.Left
        state = RealPetState.Running
        RealPetRuntime.markArrived()
        try {
            if (!reduceAnimations) {
                val distDp = abs(end - start) / density.density
                x.animateTo(end, tween(realPetTravelDuration(distDp, running = true), easing = LinearEasing))
            }
        } finally {
            state = RealPetState.Offscreen
            RealPetRuntime.markLeft()
        }
    }

    private suspend fun performNap(intent: RealPetIntent.Nap) {
        if (state == RealPetState.Offscreen && intent.startAlreadySleeping) {
            placeForOpening(intent.targetPerchId)
            transitionTo(RealPetState.Sleeping, allowOpeningSleep = true)
        } else {
            if (state == RealPetState.Offscreen) {
                enterFromOffscreen(running = false)
            }
            transitionTo(RealPetState.Sleeping)
        }

        delay(randomizedMs(9_000L, 18_000L) + consumeVisitExtension())
        // Dozing by a quiet user: sleeps on until a touch startles it (see startleAwake), up to ~2 min.
        if (idleNapActive) delay(randomizedMs(90_000L, 120_000L))
        // Wake up: Sleeping -> Waking -> Sitting
        transitionTo(RealPetState.Sitting)
        holdSittingWithButtonGuard(randomizedMs(2_000L, 4_000L))
        performLeave(running = false)
    }

    private suspend fun performPrank(intent: RealPetIntent.Prank) {
        if (intent.kind == RealPetPrankKind.SitOnComposer) {
            typeMeowOnComposer()
            return
        }
        if (intent.kind != RealPetPrankKind.CategoryMeowText) return
        // Found asleep on a category whose name it "typed over"; any touch wakes it and the name comes back.
        val perchId = intent.targetPerchId ?: RealPetRuntime.perches.keys
            .filter { it.startsWith("real_pet:category:") && !it.contains(":pill:") }
            .filter { pickableCategory(it) }
            .randomOrNull(random)
        if (perchId == null) {
            Log.d("RealPet", "name prank: no visible category")
            performNap(RealPetIntent.Nap(startAlreadySleeping = true))
            return
        }
        try {
            val foundAsleep = state == RealPetState.Offscreen
            if (foundAsleep) placeForOpening(perchId) else moveToPerch(perchId)
            RealPetRuntime.namePrankPerchId = perchId
            Log.d("RealPet", "name prank on $perchId")
            idleNapActive = true
            if (foundAsleep) {
                transitionTo(RealPetState.Sleeping, allowOpeningSleep = true)
            } else {
                transitionTo(RealPetState.Sitting)
                transitionTo(RealPetState.Sleeping)
            }
            delay(randomizedMs(60_000L, 120_000L))
            idleNapActive = false
            transitionTo(RealPetState.Sitting)
            RealPetRuntime.namePrankPerchId = null
            holdSittingWithButtonGuard(randomizedMs(2_000L, 4_000L))
            performLeave(running = false)
        } finally {
            RealPetRuntime.namePrankPerchId = null
        }
    }

    /** Typing paused: hops onto the composer, paws at it and a "meaw" of its own appears in the draft. */
    private suspend fun typeMeowOnComposer() {
        if (RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER] == null || RealPetRuntime.composerDraft.isBlank()) return
        // One word of its own per draft.
        if (RealPetRuntime.composerMeow?.let { RealPetRuntime.composerDraft.contains(it) } == true) return
        if (state == RealPetState.Offscreen) enterFromOffscreen(running = true)
        if (anchorId != REAL_PET_PERCH_COMPOSER) moveToPerch(REAL_PET_PERCH_COMPOSER)
        if (anchorId != REAL_PET_PERCH_COMPOSER) return
        transitionTo(RealPetState.Sitting)
        transitionTo(RealPetState.Pawing)
        RealPetRuntime.forcePrank(REAL_PET_PERCH_COMPOSER, RealPetPrankType.ComposerTypedMeow)
        Log.d("RealPet", "typed a meow")
        transitionTo(RealPetState.Sitting)
        holdSittingWithButtonGuard(randomizedMs(2_000L, 3_500L))
        performLeave(running = random.nextBoolean())
    }

    private fun pickableCategory(id: String): Boolean {
        val bounds = RealPetRuntime.perches[id] ?: return false
        return !RealPetRuntime.isPerchCovered(id) && perchY(id, bounds) in (heightPx * 0.2f)..groundY
    }

    private suspend fun performReaction(intent: RealPetIntent.React) {
        when (intent.kind) {
            RealPetReactionKind.WakeAndSit -> {
                // PLAN.md Section 5 & 7: "Uyurken dokunulursa uyanır (wake), oturur" — unless it fell
                // asleep from being petted: then more petting keeps it asleep.
                noteLoved(1)
                spawnHeartBurst(1)
                RealHaptics.tick(view)
                if (loveStreak < LOVE_SLEEP_AT) transitionTo(RealPetState.Sitting) // Sleeping -> Waking -> Sitting
                stayWhileLoved()
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
                noteLoved(1)
                stayWhileLoved()
            }

            RealPetReactionKind.TapHeart -> {
                // PLAN.md Section 7: "Dokunma -> Kalp, kedi biraz daha kalır"
                noteLoved(1)
                spawnHeartBurst(1)
                RealHaptics.tick(view)
                if (state == RealPetState.Idle) {
                    transitionTo(RealPetState.Sitting)
                }
                stayWhileLoved()
            }

            RealPetReactionKind.HoldPurr -> {
                // PLAN.md Section 7 & 12.1: "Basılı tutma -> Oturur, mırlar (purr)"
                noteLoved(3)
                spawnHeartBurst(2)
                RealHaptics.success(view)
                stayWhileLoved()
            }

            RealPetReactionKind.RepeatedTapPurrAndLieDown -> {
                // PLAN.md Section 7: "Art arda dokunma -> Kalpler çoğalır, mırlar, sonra uzanır"
                noteLoved(2)
                spawnHeartBurst(3)
                RealHaptics.success(view)
                stayWhileLoved()
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

            RealPetReactionKind.InspectNewCard -> inspectNewCard(intent.targetPerchId)

            else -> Unit
        }
    }

    /** Goes and sits on a card whose alert is coming up, calls a couple of times, stays ~20–30 s. */
    private suspend fun remindOnCard(perchId: String?) {
        val id = perchId?.takeIf { RealPetRuntime.perches[it] != null } ?: return
        if (state == RealPetState.Offscreen) enterFromOffscreen(running = false)
        if (anchorId != id) moveToPerch(id)
        repeat(random.nextInt(2, 4)) {
            transitionTo(RealPetState.Sitting)
            transitionTo(RealPetState.Calling)
            transitionTo(RealPetState.Sitting)
            holdSittingWithButtonGuard(randomizedMs(6_000L, 10_000L))
        }
        performLeave(running = false)
    }

    /** Hurries to a card that was just added, pats it a few times, sits by it a moment and goes. */
    private suspend fun inspectNewCard(perchId: String?) {
        val id = perchId?.takeIf { RealPetRuntime.perches[it] != null }
        if (id == null) {
            Log.d("RealPet", "new card $perchId has no perch on screen")
            return
        }
        if (state == RealPetState.Offscreen) enterFromOffscreen(running = true)
        if (anchorId != id) moveToPerch(id, preferRunning = true)
        transitionTo(RealPetState.Sitting)
        delay(randomizedMs(400L, 800L))
        transitionTo(RealPetState.Pawing)
        delay(REAL_PET_FRAME_TABLE.getValue(RealPetState.Pawing).totalDurationMs)
        transitionTo(RealPetState.Sitting)
        holdSittingWithButtonGuard(randomizedMs(2_500L, 4_500L))
        performLeave(running = false)
    }

    /** Touch points in the current petting session; a pause longer than [LOVE_GAP_MS] starts a new one. */
    private var loveStreak = 0
    private var lastLoveTouchMs = 0L
    private var loveStayMs = 0L

    private fun noteLoved(points: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastLoveTouchMs > LOVE_GAP_MS) {
            loveStreak = 0
            loveStayMs = 0L
        }
        loveStreak += points
        if (loveStreak >= LOVE_PURR_AT && loveStayMs == 0L) loveStayMs = randomizedMs(20_000L, 40_000L)
        lastLoveTouchMs = now
        Log.d("RealPet", "loved streak=$loveStreak")
    }

    /**
     * Stays while it is being petted: sits, then purrs, then lies down and sleeps as the petting adds up.
     * A short pat keeps it a few seconds; a real petting session keeps it 20–40 s after the last touch.
     */
    private suspend fun stayWhileLoved() {
        while (true) {
            val since = SystemClock.uptimeMillis() - lastLoveTouchMs
            val stayMs = if (loveStreak >= LOVE_PURR_AT) loveStayMs else 5_000L
            if (since > stayMs) break
            val wanted = when {
                loveStreak >= LOVE_SLEEP_AT -> RealPetState.Sleeping
                // Purrs while the hand is there, settles back to sitting once it stops.
                loveStreak >= LOVE_PURR_AT && since < 6_000L -> RealPetState.Purring
                else -> RealPetState.Sitting
            }
            val there = state == wanted || (wanted == RealPetState.Sleeping && state == RealPetState.LyingDown)
            if (!there) transitionTo(wanted)
            delay(250L)
        }
        if (state == RealPetState.Sleeping || state == RealPetState.LyingDown) {
            transitionTo(RealPetState.Sitting)
            delay(randomizedMs(1_200L, 2_200L))
        }
        loveStreak = 0
        performLeave(running = false)
    }

    private suspend fun performPose(intent: RealPetIntent.Pose) {
        if (state == RealPetState.Offscreen) {
            enterFromOffscreen(running = false)
        }
        transitionTo(RealPetState.Sitting)
        holdSittingWithButtonGuard(randomizedMs(1_000L, 1_600L))
        if (intent.pose == RealPetState.Grooming && !reduceAnimations) longGroom() else transitionTo(intent.pose)
        // Looping poses have no end of their own; give them a few cycles.
        if (REAL_PET_FRAME_TABLE[intent.pose]?.looping == true) delay(3_000L)
        transitionTo(RealPetState.Sitting)
        holdSittingWithButtonGuard(randomizedMs(1_800L, 2_800L))
        performLeave(running = false)
    }

    private suspend fun performAskLove(intent: RealPetIntent.AskLove) {
        if (state == RealPetState.Offscreen) {
            enterFromOffscreen(running = false)
        }
        // On a card, where the user's thumb is, not on the composer.
        val perch = intent.targetPerchId ?: pickSafePerch(cardsOnly = true) ?: pickSafePerch()
        if (perch != null && perch != anchorId) {
            moveToPerch(perch, preferRunning = false)
        }
        // Asks a few times and gives the user time to come and pet it; a touch takes over from here.
        repeat(random.nextInt(2, 4)) {
            transitionTo(RealPetState.Sitting)
            transitionTo(RealPetState.Calling)
            transitionTo(RealPetState.Sitting)
            holdSittingWithButtonGuard(randomizedMs(4_000L, 6_000L))
        }
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
        facing = if (targetX < currentX) RealPetFacing.Left else RealPetFacing.Right
        // Off a card first, with a hop; sliding down to the ground read as falling.
        if (abs(currentY - groundY) > 2f) {
            if (reduceAnimations) y.snapTo(groundY) else hopToGround(towardX = targetX)
        }
        if (abs(targetX - x.value) > with(density) { 8.dp.toPx() }) {
            transitionTo(RealPetState.Walking)
            val distDp = abs(targetX - x.value) / density.density
            if (reduceAnimations) {
                x.snapTo(targetX)
            } else {
                x.animateTo(targetX, tween(realPetTravelDuration(distDp, running = false), easing = LinearEasing))
            }
        }
        if (state != RealPetState.Sitting) {
            transitionTo(RealPetState.Idle)
            transitionTo(RealPetState.Sitting)
        }
    }

    private var scrollSincePerchCheck = 0f

    /**
     * Watches the card the cat sits on. A jump that scrolling does not explain (a list expanding,
     * a card moving or going away) startles it: it stays in the air where it was and hops off.
     */
    suspend fun watchPerchShifts() {
        val limit = with(density) { 40.dp.toPx() }
        var lastId: String? = null
        var last: Offset? = null
        // Since the cat settled: lists animate items over many frames, so the move adds up, not per frame.
        var driftX = 0f
        var driftY = 0f
        var detailSeenAtMs = 0L
        var pendingStartle: Job? = null
        var detailWasOpen = false
        snapshotFlow {
            Triple(anchorId, perchTargetPosition(anchorId, perchProgress.value), RealPetRuntime.isDetailOpen())
        }.collect { (id, pos, detailOpen) ->
            // Open or just closed: both ends of the card <-> detail animation.
            if (detailOpen || detailWasOpen) detailSeenAtMs = SystemClock.uptimeMillis()
            detailWasOpen = detailOpen
            val prev = last
            val scrolled = scrollSincePerchCheck
            scrollSincePerchCheck = 0f
            val sameSpot = id != null && id == lastId && pos != null && prev != null
            lastId = id
            last = pos
            if (!sameSpot) {
                driftX = 0f
                driftY = 0f
                return@collect
            }
            driftY += pos!!.y - prev!!.y - scrolled
            driftX += pos.x - prev.x
            // Startles from where it is seen now: it rides the first bit of the move, then jumps clear.
            if ((abs(driftY) > limit || abs(driftX) > limit) && pendingStartle?.isActive != true) {
                driftX = 0f
                driftY = 0f
                // A tapped card grows into its detail screen (and shrinks back): that move is not a
                // startle, the cat just waits on it. Give the detail a moment to show up before deciding.
                if (!canBeStartled(prev)) return@collect
                val perchId = id!!
                // Hold it in the air where it was while deciding, so it does not ride the card away.
                anchorId = null
                heldOverPerch = perchId
                pendingStartle = coroutineScope.launch {
                    x.snapTo(prev.x)
                    y.snapTo(prev.y)
                    delay(250L)
                    heldOverPerch = null
                    val sinceDetail = SystemClock.uptimeMillis() - detailSeenAtMs
                    if (anchorId == null && motion == RealPetMotion.None) {
                        if (RealPetRuntime.isDetailOpen() || sinceDetail <= 1_200L) {
                            anchorId = perchId // the card opened; wait on it, covered
                        } else {
                            startleOffPerch(perchId, prev)
                        }
                    }
                }
            }
        }
    }

    private fun canBeStartled(at: Offset): Boolean =
        motion == RealPetMotion.None && !isDragging && !brain.isUserBusy && at.y + petSizePx > 0f && at.y < heightPx &&
            state in STARTLE_STATES

    private fun startleOffPerch(perchId: String, from: Offset) {
        Log.d("RealPet", "startled: perch $perchId moved from ${from.x.toInt()},${from.y.toInt()}")
        cancelActiveIntent()
        brain.clearActiveIntent()
        activeIntentJob = coroutineScope.launch {
            anchorId = null
            x.snapTo(from.x)
            y.snapTo(from.y)
            bridgeFrames = null
            // Straight up on its feet, no waking or stand-up frames: that abruptness is the startle.
            state = RealPetState.Idle
            delay(if (reduceAnimations) 0L else 160L)
            val moved = perchTargetPosition(perchId, 0.5f)
            if (moved != null && moved.y in 0f..groundY) {
                moveToPerch(perchId)
            } else {
                hopToGround(towardX = from.x)
            }
            delay(randomizedMs(2_500L, 4_500L))
            if (!brain.isUserBusy) performLeave(running = false)
        }
    }

    /** Short hop from where the cat stands down to the ground, a little toward [towardX]; lands sitting. */
    private suspend fun hopToGround(towardX: Float) {
        val startX = x.value
        val startY = y.value
        val step = with(density) { 36.dp.toPx() }
        val landX = startX + (towardX - startX).coerceIn(-step, step)
        motionStartX = startX
        motionStartY = startY
        motionTargetX = landX
        motionTargetY = groundY
        // A long way down gets a real leap (higher arc, longer air time) so it never reads as a fall.
        val dropDp = abs(groundY - startY) / density.density
        motionHeightPx = with(density) { (24f + dropDp * 0.08f).coerceAtMost(72f).dp.toPx() }
        jumpVisual = if (dropDp > 120f) RealPetJumpVisual.ForwardTravel else RealPetJumpVisual.LegacyHop
        state = if (dropDp > 120f) RealPetState.Jumping else RealPetState.Hopping
        motion = RealPetMotion.Jump
        try {
            motionProgress.snapTo(0f)
            val airMs = (440 + dropDp * 0.7f).toInt().coerceIn(460, 900)
            motionProgress.animateTo(1f, tween(durationMillis = airMs, easing = RealMotion.Standard))
        } finally {
            motionProgress.snapTo(0f)
            motion = RealPetMotion.None
            x.snapTo(landX)
            y.snapTo(groundY)
            state = RealPetState.Sitting
        }
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
        // Leaving from a covered card would walk through the detail screen in mid-air.
        if (isCovered) {
            vanish()
            return
        }
        RealPetRuntime.startLeaving()
        try {
            val startX = renderX
            val startY = renderY
            val exitLeft = (startX + petSizePx / 2f) <= widthPx / 2f
            val exitTarget = if (exitLeft) -petSizePx * 1.05f else widthPx + petSizePx * 0.05f
            facing = if (exitLeft) RealPetFacing.Left else RealPetFacing.Right
            // Through transitionTo so standing up plays its bridge frames before the walk starts.
            val gait = if (running) RealPetState.Running else RealPetState.Walking
            if (state != RealPetState.Walking && state != RealPetState.Running) {
                transitionTo(gait, preferRunning = running)
            }
            state = gait
            val cardRange = perchRange(anchorId)
            anchorId = null
            x.snapTo(startX)
            y.snapTo(startY)
            if (abs(startY - groundY) > 2f && !reduceAnimations) {
                // Up on a card: walk to its edge, hop down, then off the screen (it used to walk off through the air).
                val edgeX = cardRange?.let { if (exitLeft) it.start else it.endInclusive } ?: startX
                val edgeDp = abs(edgeX - startX) / density.density
                if (edgeDp > 8f) {
                    x.animateTo(edgeX, tween(realPetTravelDuration(edgeDp, running = running), easing = LinearEasing))
                }
                hopToGround(towardX = exitTarget)
                transitionTo(gait, preferRunning = running)
                state = gait
            } else if (abs(startY - groundY) > 2f) {
                y.snapTo(groundY)
            }
            val exitDistanceDp = abs(exitTarget - x.value) / density.density

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
            // Cut short (a touch, a drag) it is still here: only a finished walk-out leaves.
            if (state == RealPetState.Offscreen) {
                RealPetRuntime.markLeft()
            } else {
                RealPetRuntime.markArrived()
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

        val currentY = renderY
        anchorId = null
        x.snapTo(currentX)
        y.snapTo(currentY)
        facing = if (targetX < currentX) RealPetFacing.Left else RealPetFacing.Right
        // On a card: hop down first (snapping to the ground here was the cat "falling").
        if (abs(currentY - groundY) > 2f) {
            if (reduceAnimations) y.snapTo(groundY) else hopToGround(towardX = targetX)
        }
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
        // Same way down as stepping aside: hop off first, then walk on the ground.
        stepAsideAndSit()
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

    private fun pickSafePerch(exclude: String? = null, cardsOnly: Boolean = false): String? {
        val candidates = RealPetRuntime.perches.entries
            .filter { (id, bounds) ->
                id != exclude &&
                    (!cardsOnly || isCardPerch(id)) &&
                    !realPetPerchIsInteractiveButton(id) &&
                    !RealPetRuntime.isPerchCovered(id) &&
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
            bridgeFrames = bodyState.bridgeFrames,
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