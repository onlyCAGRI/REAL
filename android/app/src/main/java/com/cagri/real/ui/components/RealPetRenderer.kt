package com.cagri.real.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cagri.real.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * Visual states and rendering primitives for REAL pet companion (PLAN.md Sections 5, 9, 11.2).
 *
 * Every frame in `real_pet_*` shares one canvas shape (320x280; sleep poses 640x560), feet at
 * 540/560 of the height, facing left.
 */
enum class RealPetState {
    Offscreen,
    Walking,
    Running,
    Idle,
    Sitting,
    LyingDown,
    Sleeping,
    Waking,
    Purring,
    Grooming,
    Drinking,
    Pawing,
    Calling,
    Hopping,
    Jumping,
    Meowing,
    Peeking
}

internal enum class RealPetFacing { Left, Right }
internal enum class RealPetMotion { None, Jump, Drop }
internal enum class RealPetJumpVisual { LegacyHop, ForwardTravel }

internal val WALK_FRAMES = intArrayOf(
    R.drawable.real_pet_walk_1,
    R.drawable.real_pet_walk_2,
    R.drawable.real_pet_walk_3,
    R.drawable.real_pet_walk_4,
    R.drawable.real_pet_walk_5,
    R.drawable.real_pet_walk_6,
    R.drawable.real_pet_walk_7,
    R.drawable.real_pet_walk_8,
    R.drawable.real_pet_walk_9,
    R.drawable.real_pet_walk_10,
    R.drawable.real_pet_walk_11,
    R.drawable.real_pet_walk_12,
    R.drawable.real_pet_walk_13,
    R.drawable.real_pet_walk_14,
    R.drawable.real_pet_walk_15,
    R.drawable.real_pet_walk_16,
    R.drawable.real_pet_walk_17,
    R.drawable.real_pet_walk_18,
    R.drawable.real_pet_walk_19,
    R.drawable.real_pet_walk_20,
    R.drawable.real_pet_walk_21,
    R.drawable.real_pet_walk_22,
    R.drawable.real_pet_walk_23,
    R.drawable.real_pet_walk_24
)

internal val IDLE_FRAMES = intArrayOf(
    R.drawable.real_pet_idle_1,
    R.drawable.real_pet_idle_2,
    R.drawable.real_pet_idle_3,
    R.drawable.real_pet_idle_4,
    R.drawable.real_pet_idle_5,
    R.drawable.real_pet_idle_6,
    R.drawable.real_pet_idle_7,
    R.drawable.real_pet_idle_8,
    R.drawable.real_pet_idle_9,
    R.drawable.real_pet_idle_10,
    R.drawable.real_pet_idle_11,
    R.drawable.real_pet_idle_12
)

internal val SIT_FRAMES = intArrayOf(
    R.drawable.real_pet_sit_1,
    R.drawable.real_pet_sit_2,
    R.drawable.real_pet_sit_3,
    R.drawable.real_pet_sit_4,
    R.drawable.real_pet_sit_5,
    R.drawable.real_pet_sit_6,
    R.drawable.real_pet_sit_7,
    R.drawable.real_pet_sit_8,
    R.drawable.real_pet_sit_9,
    R.drawable.real_pet_sit_10,
    R.drawable.real_pet_sit_11,
    R.drawable.real_pet_sit_12,
    R.drawable.real_pet_sit_13,
    R.drawable.real_pet_sit_14,
    R.drawable.real_pet_sit_15,
    R.drawable.real_pet_sit_16,
    R.drawable.real_pet_sit_17,
    R.drawable.real_pet_sit_18,
    R.drawable.real_pet_sit_19,
    R.drawable.real_pet_sit_20,
    R.drawable.real_pet_sit_21
)

// PLAN.md Section 9: Sitting holds call_1 (open eyes) and only blinks briefly with purr_1.
// Never cycles call_2/call_3 during sitting since those read as "asking for love".
internal val OUTLINED_SIT_FRAMES = intArrayOf(
    R.drawable.real_pet_call_1,
    R.drawable.real_pet_purr_1
)

// Stretch held a beat, yawn held ~0.8s; wake_11/12 are RIFE blobs between stretch and yawn, skipped.
private val WAKE_YAWN_FRAMES by lazy {
    WAKE_FRAMES.copyOfRange(0, 10) + IntArray(5) { WAKE_FRAMES[9] } +
        IntArray(18) { WAKE_FRAMES[12] } + IntArray(4) { WAKE_FRAMES[13] } +
        WAKE_FRAMES.copyOfRange(14, WAKE_FRAMES.size)
}

internal val WAKE_FRAMES = intArrayOf(
    R.drawable.real_pet_wake_1,
    R.drawable.real_pet_wake_2,
    R.drawable.real_pet_wake_3,
    R.drawable.real_pet_wake_4,
    R.drawable.real_pet_wake_5,
    R.drawable.real_pet_wake_6,
    R.drawable.real_pet_wake_7,
    R.drawable.real_pet_wake_8,
    R.drawable.real_pet_wake_9,
    R.drawable.real_pet_wake_10,
    R.drawable.real_pet_wake_11,
    R.drawable.real_pet_wake_12,
    R.drawable.real_pet_wake_13,
    R.drawable.real_pet_wake_14,
    R.drawable.real_pet_wake_15,
    R.drawable.real_pet_wake_16,
    R.drawable.real_pet_wake_17,
    R.drawable.real_pet_wake_18,
    R.drawable.real_pet_wake_19,
    R.drawable.real_pet_wake_20,
    R.drawable.real_pet_wake_21
)

internal val LIEDOWN_FRAMES = intArrayOf(
    R.drawable.real_pet_liedown_1,
    R.drawable.real_pet_liedown_2,
    R.drawable.real_pet_liedown_3,
    R.drawable.real_pet_liedown_4,
    R.drawable.real_pet_liedown_5,
    R.drawable.real_pet_liedown_6,
    R.drawable.real_pet_liedown_7,
    R.drawable.real_pet_liedown_8,
    R.drawable.real_pet_liedown_9,
    R.drawable.real_pet_liedown_10,
    R.drawable.real_pet_liedown_11,
    R.drawable.real_pet_liedown_12,
    R.drawable.real_pet_liedown_13,
    R.drawable.real_pet_liedown_14,
    R.drawable.real_pet_liedown_15,
    R.drawable.real_pet_liedown_16,
    R.drawable.real_pet_liedown_17,
    R.drawable.real_pet_liedown_18,
    R.drawable.real_pet_liedown_19,
    R.drawable.real_pet_liedown_20,
    R.drawable.real_pet_liedown_21
)

internal val PURR_FRAMES = intArrayOf(
    R.drawable.real_pet_purr_1,
    R.drawable.real_pet_purr_2,
    R.drawable.real_pet_purr_3,
    R.drawable.real_pet_purr_4,
    R.drawable.real_pet_purr_5,
    R.drawable.real_pet_purr_6,
    R.drawable.real_pet_purr_7,
    R.drawable.real_pet_purr_8,
    R.drawable.real_pet_purr_9,
    R.drawable.real_pet_purr_10,
    R.drawable.real_pet_purr_11,
    R.drawable.real_pet_purr_12,
    R.drawable.real_pet_purr_13,
    R.drawable.real_pet_purr_14,
    R.drawable.real_pet_purr_15,
    R.drawable.real_pet_purr_16,
    R.drawable.real_pet_purr_17,
    R.drawable.real_pet_purr_18,
    R.drawable.real_pet_purr_19,
    R.drawable.real_pet_purr_20,
    R.drawable.real_pet_purr_21,
    R.drawable.real_pet_purr_22,
    R.drawable.real_pet_purr_23,
    R.drawable.real_pet_purr_24
)

internal val GROOM_FRAMES = intArrayOf(
    R.drawable.real_pet_groom_1,
    R.drawable.real_pet_groom_2,
    R.drawable.real_pet_groom_3,
    R.drawable.real_pet_groom_4,
    R.drawable.real_pet_groom_5,
    R.drawable.real_pet_groom_6,
    R.drawable.real_pet_groom_7,
    R.drawable.real_pet_groom_8,
    R.drawable.real_pet_groom_9,
    R.drawable.real_pet_groom_10,
    R.drawable.real_pet_groom_11,
    R.drawable.real_pet_groom_12,
    R.drawable.real_pet_groom_13,
    R.drawable.real_pet_groom_14,
    R.drawable.real_pet_groom_15,
    R.drawable.real_pet_groom_16,
    R.drawable.real_pet_groom_17,
    R.drawable.real_pet_groom_18,
    R.drawable.real_pet_groom_19,
    R.drawable.real_pet_groom_20,
    R.drawable.real_pet_groom_21
)

internal val DRINK_FRAMES = intArrayOf(
    R.drawable.real_pet_drink_1,
    R.drawable.real_pet_drink_2,
    R.drawable.real_pet_drink_3,
    R.drawable.real_pet_drink_4,
    R.drawable.real_pet_drink_5,
    R.drawable.real_pet_drink_6,
    R.drawable.real_pet_drink_7,
    R.drawable.real_pet_drink_8,
    R.drawable.real_pet_drink_9,
    R.drawable.real_pet_drink_10,
    R.drawable.real_pet_drink_11,
    R.drawable.real_pet_drink_12,
    R.drawable.real_pet_drink_13,
    R.drawable.real_pet_drink_14,
    R.drawable.real_pet_drink_15,
    R.drawable.real_pet_drink_16,
    R.drawable.real_pet_drink_17,
    R.drawable.real_pet_drink_18,
    R.drawable.real_pet_drink_19,
    R.drawable.real_pet_drink_20,
    R.drawable.real_pet_drink_21,
    R.drawable.real_pet_drink_22,
    R.drawable.real_pet_drink_23,
    R.drawable.real_pet_drink_24
)

internal val PAW_FRAMES = intArrayOf(
    R.drawable.real_pet_paw_1,
    R.drawable.real_pet_paw_2,
    R.drawable.real_pet_paw_3,
    R.drawable.real_pet_paw_4,
    R.drawable.real_pet_paw_5,
    R.drawable.real_pet_paw_6,
    R.drawable.real_pet_paw_7,
    R.drawable.real_pet_paw_8,
    R.drawable.real_pet_paw_9,
    R.drawable.real_pet_paw_10,
    R.drawable.real_pet_paw_11,
    R.drawable.real_pet_paw_12,
    R.drawable.real_pet_paw_13,
    R.drawable.real_pet_paw_14,
    R.drawable.real_pet_paw_15,
    R.drawable.real_pet_paw_16,
    R.drawable.real_pet_paw_17,
    R.drawable.real_pet_paw_18,
    R.drawable.real_pet_paw_19,
    R.drawable.real_pet_paw_20,
    R.drawable.real_pet_paw_21
)

internal val CALL_FRAMES = intArrayOf(
    R.drawable.real_pet_call_1,
    R.drawable.real_pet_call_2,
    R.drawable.real_pet_call_3,
    R.drawable.real_pet_call_4,
    R.drawable.real_pet_call_5,
    R.drawable.real_pet_call_6,
    R.drawable.real_pet_call_7,
    R.drawable.real_pet_call_8,
    R.drawable.real_pet_call_9,
    R.drawable.real_pet_call_10,
    R.drawable.real_pet_call_11,
    R.drawable.real_pet_call_12,
    R.drawable.real_pet_call_13,
    R.drawable.real_pet_call_14,
    R.drawable.real_pet_call_15,
    R.drawable.real_pet_call_16,
    R.drawable.real_pet_call_17,
    R.drawable.real_pet_call_18,
    R.drawable.real_pet_call_19,
    R.drawable.real_pet_call_20,
    R.drawable.real_pet_call_21
)

internal val HOP_FRAMES = intArrayOf(
    R.drawable.real_pet_hop_1,
    R.drawable.real_pet_hop_2,
    R.drawable.real_pet_hop_3,
    R.drawable.real_pet_hop_4,
    R.drawable.real_pet_hop_5,
    R.drawable.real_pet_hop_6,
    R.drawable.real_pet_hop_7,
    R.drawable.real_pet_hop_8,
    R.drawable.real_pet_hop_9,
    R.drawable.real_pet_hop_10,
    R.drawable.real_pet_hop_11,
    R.drawable.real_pet_hop_12,
    R.drawable.real_pet_hop_13,
    R.drawable.real_pet_hop_14,
    R.drawable.real_pet_hop_15,
    R.drawable.real_pet_hop_16,
    R.drawable.real_pet_hop_17,
    R.drawable.real_pet_hop_18,
    R.drawable.real_pet_hop_19,
    R.drawable.real_pet_hop_20,
    R.drawable.real_pet_hop_21
)

internal val LEGACY_JUMP_FRAMES = HOP_FRAMES

internal val JUMP_FRAMES = intArrayOf(
    R.drawable.real_pet_jump_1,
    R.drawable.real_pet_jump_2,
    R.drawable.real_pet_jump_3,
    R.drawable.real_pet_jump_4,
    R.drawable.real_pet_jump_5,
    R.drawable.real_pet_jump_6,
    R.drawable.real_pet_jump_7,
    R.drawable.real_pet_jump_8,
    R.drawable.real_pet_jump_9,
    R.drawable.real_pet_jump_10,
    R.drawable.real_pet_jump_11,
    R.drawable.real_pet_jump_12,
    R.drawable.real_pet_jump_13,
    R.drawable.real_pet_jump_14,
    R.drawable.real_pet_jump_15,
    R.drawable.real_pet_jump_16,
    R.drawable.real_pet_jump_17,
    R.drawable.real_pet_jump_18,
    R.drawable.real_pet_jump_19,
    R.drawable.real_pet_jump_20,
    R.drawable.real_pet_jump_21
)

// Open -> full meow -> close, so the mouth never snaps abruptly back to the first frame.
internal val MEOW_FRAMES = intArrayOf(
    R.drawable.real_pet_meow_1,
    R.drawable.real_pet_meow_2,
    R.drawable.real_pet_meow_3,
    R.drawable.real_pet_meow_4,
    R.drawable.real_pet_meow_5,
    R.drawable.real_pet_meow_6,
    R.drawable.real_pet_meow_7,
    R.drawable.real_pet_meow_8,
    R.drawable.real_pet_meow_9,
    R.drawable.real_pet_meow_10,
    R.drawable.real_pet_meow_11,
    R.drawable.real_pet_meow_12,
    R.drawable.real_pet_meow_13,
    R.drawable.real_pet_meow_12,
    R.drawable.real_pet_meow_11,
    R.drawable.real_pet_meow_10,
    R.drawable.real_pet_meow_9,
    R.drawable.real_pet_meow_8,
    R.drawable.real_pet_meow_7,
    R.drawable.real_pet_meow_6,
    R.drawable.real_pet_meow_5,
    R.drawable.real_pet_meow_4,
    R.drawable.real_pet_meow_3,
    R.drawable.real_pet_meow_2,
    R.drawable.real_pet_meow_1
)

// Bridges between poses that do not meet: generated key poses joined with RIFE in-betweens.
// walk_21 -> standing -> half-sit -> call_1; reversed it is the stand-up.
internal val SIT_DOWN_FRAMES = intArrayOf(
    R.drawable.real_pet_sitdown_1,
    R.drawable.real_pet_sitdown_2,
    R.drawable.real_pet_sitdown_3,
    R.drawable.real_pet_sitdown_4,
    R.drawable.real_pet_sitdown_5,
    R.drawable.real_pet_sitdown_6,
    R.drawable.real_pet_sitdown_7,
    R.drawable.real_pet_sitdown_8,
    R.drawable.real_pet_sitdown_9,
    R.drawable.real_pet_sitdown_10,
    R.drawable.real_pet_sitdown_11,
    R.drawable.real_pet_sitdown_12,
    R.drawable.real_pet_sitdown_13,
    R.drawable.real_pet_sitdown_14,
    R.drawable.real_pet_sitdown_15,
    R.drawable.real_pet_sitdown_16
)

// call_1 -> looking at the bowl (bowl fades in) -> drink_1; reversed it is back to sitting.
internal val TO_DRINK_FRAMES = intArrayOf(
    R.drawable.real_pet_todrink_1,
    R.drawable.real_pet_todrink_2,
    R.drawable.real_pet_todrink_3,
    R.drawable.real_pet_todrink_4,
    R.drawable.real_pet_todrink_5,
    R.drawable.real_pet_todrink_6,
    R.drawable.real_pet_todrink_7,
    R.drawable.real_pet_todrink_8,
    R.drawable.real_pet_todrink_9,
    R.drawable.real_pet_todrink_10,
    R.drawable.real_pet_todrink_11
)

internal const val REAL_PET_BRIDGE_FRAME_MS = 30L

// Key poses sit at 0 (walk), 5 (standing), 10 (half-sit) and 15 (sitting). Standing is the Idle pose,
// so walk <-> stand <-> sit all run along this one bridge.
private const val STAND_KEY = 5
internal val STAND_FRAME = SIT_DOWN_FRAMES[STAND_KEY]
private val WALK_TO_STAND = SIT_DOWN_FRAMES.copyOfRange(0, STAND_KEY + 1)
private val STAND_TO_SIT = SIT_DOWN_FRAMES.copyOfRange(STAND_KEY, SIT_DOWN_FRAMES.size)
private val STAND_UP_FRAMES = SIT_DOWN_FRAMES.reversedArray()
private val STAND_TO_WALK = WALK_TO_STAND.reversedArray()
private val SIT_TO_STAND = STAND_TO_SIT.reversedArray()
private val FROM_DRINK_FRAMES = TO_DRINK_FRAMES.reversedArray()

private fun RealPetState.isMoving() = this == RealPetState.Walking || this == RealPetState.Running

/** Frames to play while the body steps [from] -> [to], or null when the two poses already meet. */
internal fun realPetBridgeFrames(from: RealPetState, to: RealPetState): IntArray? = when {
    from.isMoving() && to == RealPetState.Sitting -> SIT_DOWN_FRAMES
    from == RealPetState.Sitting && to.isMoving() -> STAND_UP_FRAMES
    from.isMoving() && to == RealPetState.Idle -> WALK_TO_STAND
    from == RealPetState.Idle && to.isMoving() -> STAND_TO_WALK
    from == RealPetState.Idle && to == RealPetState.Sitting -> STAND_TO_SIT
    from == RealPetState.Sitting && to == RealPetState.Idle -> SIT_TO_STAND
    from == RealPetState.Sitting && to == RealPetState.Drinking -> TO_DRINK_FRAMES
    from == RealPetState.Drinking && to == RealPetState.Sitting -> FROM_DRINK_FRAMES
    else -> null
}

/**
 * Single unified frame table for all states as required by PLAN.md Section 11.2:
 * "Her durumun kare listesi ve kare süresi tek bir tabloda."
 */
internal data class RealPetFrameSpec(
    val frames: IntArray,
    val frameDurationMs: Long,
    val looping: Boolean
) {
    val totalDurationMs: Long get() = frames.size * frameDurationMs
}

internal val REAL_PET_FRAME_TABLE: Map<RealPetState, RealPetFrameSpec> = mapOf(
    RealPetState.Offscreen to RealPetFrameSpec(intArrayOf(), 0L, looping = false),
    // RIFE in-betweens (3 per original pair); durations keep each pose's old total length.
    RealPetState.Walking to RealPetFrameSpec(WALK_FRAMES, 30L, looping = true),
    RealPetState.Running to RealPetFrameSpec(WALK_FRAMES, 18L, looping = true),
    // Standing side-on (the bridge's standing key pose); the old idle frames face away from the walk.
    RealPetState.Idle to RealPetFrameSpec(intArrayOf(STAND_FRAME), 0L, looping = true),
    RealPetState.Sitting to RealPetFrameSpec(OUTLINED_SIT_FRAMES, 4_200L, looping = true),
    RealPetState.LyingDown to RealPetFrameSpec(LIEDOWN_FRAMES, 37L, looping = false),
    RealPetState.Sleeping to RealPetFrameSpec(intArrayOf(R.drawable.real_pet_sleep_1), 4_200L, looping = true),
    RealPetState.Waking to RealPetFrameSpec(WAKE_YAWN_FRAMES, 44L, looping = false),
    RealPetState.Purring to RealPetFrameSpec(PURR_FRAMES, 34L, looping = true),
    RealPetState.Grooming to RealPetFrameSpec(GROOM_FRAMES, 43L, looping = false),
    RealPetState.Drinking to RealPetFrameSpec(DRINK_FRAMES, 40L, looping = false),
    RealPetState.Pawing to RealPetFrameSpec(PAW_FRAMES, 39L, looping = false),
    RealPetState.Calling to RealPetFrameSpec(CALL_FRAMES, 44L, looping = false),
    RealPetState.Hopping to RealPetFrameSpec(HOP_FRAMES, 31L, looping = false),
    RealPetState.Jumping to RealPetFrameSpec(JUMP_FRAMES, 30L, looping = false),
    RealPetState.Meowing to RealPetFrameSpec(MEOW_FRAMES, 42L, looping = false),
    RealPetState.Peeking to RealPetFrameSpec(IDLE_FRAMES, 105L, looping = true)
)

@Composable
internal fun RealPetRenderer(
    state: RealPetState,
    sleepPoseIndex: Int,
    reduceAnimations: Boolean,
    sitFrameIndex: Int = 0,
    jumpProgress: Float = 0f,
    jumpVisual: RealPetJumpVisual = RealPetJumpVisual.ForwardTravel,
    bridgeFrames: IntArray? = null,
    modifier: Modifier = Modifier
) {
    if (state == RealPetState.Offscreen) return
    val resources = LocalContext.current.resources

    if (bridgeFrames != null && bridgeFrames.isNotEmpty()) {
        // Played once while the body steps between two poses, then held on the last frame.
        var bridgeIndex by remember(bridgeFrames) { mutableIntStateOf(0) }
        LaunchedEffect(bridgeFrames) {
            warmRealPetFrames(resources, bridgeFrames)
            for (i in 1..bridgeFrames.lastIndex) {
                delay(REAL_PET_BRIDGE_FRAME_MS)
                bridgeIndex = i
            }
        }
        RealPetFrameImage(resource = bridgeFrames[bridgeIndex], modifier = modifier)
        return
    }

    if (state == RealPetState.Sleeping) {
        RealPetSleepingImage(
            poseIndex = sleepPoseIndex,
            reduceAnimations = reduceAnimations,
            modifier = modifier
        )
        return
    }

    if (state == RealPetState.Sitting || state == RealPetState.Idle) {
        // Sitting = call_1 held, standing = the bridge's stand frame; both blink briefly every 3–6s for ~180ms.
        val sitting = state == RealPetState.Sitting
        var blinking by remember { mutableStateOf(false) }
        LaunchedEffect(reduceAnimations) {
            blinking = false
            if (!reduceAnimations) {
                while (isActive) {
                    delay(Random.nextLong(3_000L, 6_001L))
                    blinking = true
                    delay(180L)
                    blinking = false
                }
            }
        }
        RealPetFrameImage(
            resource = when {
                sitting && blinking -> R.drawable.real_pet_purr_1
                sitting -> R.drawable.real_pet_call_1
                blinking -> R.drawable.real_pet_stand_blink
                else -> STAND_FRAME
            },
            modifier = modifier
        )
        return
    }

    if (state == RealPetState.Peeking) {
        RealPetSpriteImage(
            resource = R.drawable.real_pet_idle_1,
            // idle_1 is a 320x280 canvas now (half of the old 640x560).
            sourceOffset = IntOffset(57, 47),
            sourceSize = IntSize(130, 130),
            sizeScale = 1.34f,
            modifier = modifier
        )
        return
    }

    if (state == RealPetState.Jumping || state == RealPetState.Hopping) {
        val frames = if (state == RealPetState.Hopping || jumpVisual == RealPetJumpVisual.LegacyHop) {
            HOP_FRAMES
        } else {
            JUMP_FRAMES
        }
        LaunchedEffect(frames) { warmRealPetFrames(resources, frames) }
        if (jumpProgress > 0f) {
            val progress = jumpProgress.coerceIn(0f, 1f)
            val frameIndex = if (reduceAnimations) {
                0
            } else {
                (progress * frames.size).toInt().coerceIn(0, frames.lastIndex)
            }
            RealPetFrameImage(resource = frames[frameIndex], modifier = modifier)
            return
        }
    }

    val spec = REAL_PET_FRAME_TABLE[state] ?: REAL_PET_FRAME_TABLE.getValue(RealPetState.Idle)
    val frames = spec.frames
    if (frames.isEmpty()) return

    var frameIndex by remember(state) {
        mutableIntStateOf(0)
    }

    LaunchedEffect(state, spec, reduceAnimations) {
        frameIndex = 0
        // Decode the whole pose up front so 30 ms frames never wait on a PNG decode.
        warmRealPetFrames(resources, frames)
        if (!reduceAnimations && frames.size > 1 && spec.frameDurationMs > 0L) {
            if (spec.looping) {
                while (isActive) {
                    delay(spec.frameDurationMs)
                    frameIndex = (frameIndex + 1) % frames.size
                }
            } else {
                for (i in 1..frames.lastIndex) {
                    delay(spec.frameDurationMs)
                    frameIndex = i
                }
            }
        }
    }

    RealPetFrameImage(
        resource = frames[frameIndex.coerceIn(0, frames.lastIndex)],
        modifier = modifier
    )
}