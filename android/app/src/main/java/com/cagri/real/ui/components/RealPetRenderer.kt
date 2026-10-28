package com.cagri.real.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cagri.real.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * Visual states and rendering primitives for REAL pet companion (PLAN.md Sections 5, 9, 11.2).
 *
 * Every frame in `real_pet_*` shares a 640x560 canvas, feet on row 540, facing left.
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
    R.drawable.real_pet_walk_6
)

internal val IDLE_FRAMES = intArrayOf(
    R.drawable.real_pet_idle_1,
    R.drawable.real_pet_idle_2,
    R.drawable.real_pet_idle_3
)

internal val SIT_FRAMES = intArrayOf(
    R.drawable.real_pet_sit_1,
    R.drawable.real_pet_sit_2,
    R.drawable.real_pet_sit_3,
    R.drawable.real_pet_sit_4,
    R.drawable.real_pet_sit_5,
    R.drawable.real_pet_sit_6
)

// PLAN.md Section 9: Sitting holds call_1 (open eyes) and only blinks briefly with purr_1.
// Never cycles call_2/call_3 during sitting since those read as "asking for love".
internal val OUTLINED_SIT_FRAMES = intArrayOf(
    R.drawable.real_pet_call_1,
    R.drawable.real_pet_purr_1
)

internal val WAKE_FRAMES = intArrayOf(
    R.drawable.real_pet_wake_1,
    R.drawable.real_pet_wake_2,
    R.drawable.real_pet_wake_3,
    R.drawable.real_pet_wake_4,
    R.drawable.real_pet_wake_5,
    R.drawable.real_pet_wake_6
)

internal val LIEDOWN_FRAMES = intArrayOf(
    R.drawable.real_pet_liedown_1,
    R.drawable.real_pet_liedown_2,
    R.drawable.real_pet_liedown_3,
    R.drawable.real_pet_liedown_4,
    R.drawable.real_pet_liedown_5,
    R.drawable.real_pet_liedown_6
)

internal val PURR_FRAMES = intArrayOf(
    R.drawable.real_pet_purr_1,
    R.drawable.real_pet_purr_2,
    R.drawable.real_pet_purr_3,
    R.drawable.real_pet_purr_4,
    R.drawable.real_pet_purr_5,
    R.drawable.real_pet_purr_6
)

internal val GROOM_FRAMES = intArrayOf(
    R.drawable.real_pet_groom_1,
    R.drawable.real_pet_groom_2,
    R.drawable.real_pet_groom_3,
    R.drawable.real_pet_groom_4,
    R.drawable.real_pet_groom_5,
    R.drawable.real_pet_groom_6
)

internal val DRINK_FRAMES = intArrayOf(
    R.drawable.real_pet_drink_1,
    R.drawable.real_pet_drink_2,
    R.drawable.real_pet_drink_3,
    R.drawable.real_pet_drink_4,
    R.drawable.real_pet_drink_5,
    R.drawable.real_pet_drink_6
)

internal val PAW_FRAMES = intArrayOf(
    R.drawable.real_pet_paw_1,
    R.drawable.real_pet_paw_2,
    R.drawable.real_pet_paw_3,
    R.drawable.real_pet_paw_4,
    R.drawable.real_pet_paw_5,
    R.drawable.real_pet_paw_6
)

internal val CALL_FRAMES = intArrayOf(
    R.drawable.real_pet_call_1,
    R.drawable.real_pet_call_2,
    R.drawable.real_pet_call_3,
    R.drawable.real_pet_call_4,
    R.drawable.real_pet_call_5,
    R.drawable.real_pet_call_6
)

internal val HOP_FRAMES = intArrayOf(
    R.drawable.real_pet_hop_1,
    R.drawable.real_pet_hop_2,
    R.drawable.real_pet_hop_3,
    R.drawable.real_pet_hop_4,
    R.drawable.real_pet_hop_5,
    R.drawable.real_pet_hop_6
)

internal val LEGACY_JUMP_FRAMES = HOP_FRAMES

internal val JUMP_FRAMES = intArrayOf(
    R.drawable.real_pet_jump_1,
    R.drawable.real_pet_jump_2,
    R.drawable.real_pet_jump_3,
    R.drawable.real_pet_jump_4,
    R.drawable.real_pet_jump_5,
    R.drawable.real_pet_jump_6
)

// Open -> full meow -> close, so the mouth never snaps abruptly back to the first frame.
internal val MEOW_FRAMES = intArrayOf(
    R.drawable.real_pet_meow_1,
    R.drawable.real_pet_meow_2,
    R.drawable.real_pet_meow_3,
    R.drawable.real_pet_meow_4,
    R.drawable.real_pet_meow_3,
    R.drawable.real_pet_meow_2,
    R.drawable.real_pet_meow_1
)

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
    RealPetState.Walking to RealPetFrameSpec(WALK_FRAMES, 120L, looping = true),
    RealPetState.Running to RealPetFrameSpec(WALK_FRAMES, 70L, looping = true),
    RealPetState.Idle to RealPetFrameSpec(IDLE_FRAMES, 420L, looping = true),
    RealPetState.Sitting to RealPetFrameSpec(OUTLINED_SIT_FRAMES, 4_200L, looping = true),
    RealPetState.LyingDown to RealPetFrameSpec(LIEDOWN_FRAMES, 130L, looping = false),
    RealPetState.Sleeping to RealPetFrameSpec(intArrayOf(R.drawable.real_pet_sleep_1), 4_200L, looping = true),
    RealPetState.Waking to RealPetFrameSpec(WAKE_FRAMES, 155L, looping = false),
    RealPetState.Purring to RealPetFrameSpec(PURR_FRAMES, 135L, looping = true),
    RealPetState.Grooming to RealPetFrameSpec(GROOM_FRAMES, 150L, looping = false),
    RealPetState.Drinking to RealPetFrameSpec(DRINK_FRAMES, 160L, looping = false),
    RealPetState.Pawing to RealPetFrameSpec(PAW_FRAMES, 135L, looping = false),
    RealPetState.Calling to RealPetFrameSpec(CALL_FRAMES, 155L, looping = false),
    RealPetState.Hopping to RealPetFrameSpec(HOP_FRAMES, 110L, looping = false),
    RealPetState.Jumping to RealPetFrameSpec(JUMP_FRAMES, 105L, looping = false),
    RealPetState.Meowing to RealPetFrameSpec(MEOW_FRAMES, 150L, looping = false),
    RealPetState.Peeking to RealPetFrameSpec(IDLE_FRAMES, 420L, looping = true)
)

@Composable
internal fun RealPetRenderer(
    state: RealPetState,
    sleepPoseIndex: Int,
    reduceAnimations: Boolean,
    sitFrameIndex: Int = 0,
    jumpProgress: Float = 0f,
    jumpVisual: RealPetJumpVisual = RealPetJumpVisual.ForwardTravel,
    modifier: Modifier = Modifier
) {
    if (state == RealPetState.Offscreen) return

    if (state == RealPetState.Sleeping) {
        RealPetSleepingImage(
            poseIndex = sleepPoseIndex,
            reduceAnimations = reduceAnimations,
            modifier = modifier
        )
        return
    }

    if (state == RealPetState.Sitting) {
        // Sitting = call_1 held, with an occasional short blink using purr_1 every 3–6s for ~180ms.
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
            resource = if (blinking) R.drawable.real_pet_purr_1 else R.drawable.real_pet_call_1,
            modifier = modifier
        )
        return
    }

    if (state == RealPetState.Peeking) {
        RealPetSpriteImage(
            resource = R.drawable.real_pet_idle_1,
            sourceOffset = IntOffset(114, 94),
            sourceSize = IntSize(260, 260),
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