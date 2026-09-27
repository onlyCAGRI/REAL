package com.cagri.bigboscagri.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cagri.bigboscagri.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Visual states and rendering primitives for REAL pet companion.
 */
enum class RealPetState {
    Walking,
    Running,
    Idle,
    Sitting,
    Peeking,
    Sleeping,
    Meowing,
    Jumping
}

internal enum class RealPetFacing { Left, Right }
internal enum class RealPetMotion { None, Jump, Drop }
internal enum class RealPetJumpVisual { LegacyHop, ForwardTravel }

internal const val NEW_POSE_VISUAL_SCALE = 0.60f

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

internal val LEGACY_JUMP_FRAMES = intArrayOf(
    R.drawable.real_pet_walk_1,
    R.drawable.real_pet_walk_3,
    R.drawable.real_pet_walk_5
)

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

@Composable
internal fun RealPetRenderer(
    state: RealPetState,
    sleepPoseIndex: Int,
    reduceAnimations: Boolean,
    sitFrameIndex: Int,
    jumpProgress: Float,
    jumpVisual: RealPetJumpVisual,
    modifier: Modifier = Modifier
) {
    if (state == RealPetState.Sleeping) {
        RealPetSleepingImage(
            poseIndex = sleepPoseIndex,
            reduceAnimations = reduceAnimations,
            modifier = modifier
        )
        return
    }

    if (state == RealPetState.Sitting) {
        RealPetFrameImage(
            resource = SIT_FRAMES[sitFrameIndex.coerceIn(0, SIT_FRAMES.lastIndex)],
            sizeScale = NEW_POSE_VISUAL_SCALE,
            // User-provided sit art faces right; normalize it to the legacy sprite's
            // left-facing source orientation, then the outer facing transform works for both.
            modifier = modifier.graphicsLayer { scaleX = -1f }
        )
        return
    }

    if (state == RealPetState.Peeking) {
        // Peek is head-only. The interaction box remains the same, but the renderer crops
        // the idle sprite down to the face so no body hangs into the screen.
        RealPetSpriteImage(
            resource = R.drawable.real_pet_idle_1,
            sourceOffset = IntOffset(25, 18),
            sourceSize = IntSize(130, 130),
            sizeScale = 1.34f,
            modifier = modifier
        )
        return
    }

    if (state == RealPetState.Jumping) {
        val progress = jumpProgress.coerceIn(0f, 1f)
        if (jumpVisual == RealPetJumpVisual.ForwardTravel) {
            // The new six-pose jump is a forward-travel animation only: takeoff -> stretch -> landing.
            val frameIndex = if (reduceAnimations) {
                0
            } else {
                (progress * JUMP_FRAMES.size).toInt().coerceIn(0, JUMP_FRAMES.lastIndex)
            }
            RealPetFrameImage(
                resource = JUMP_FRAMES[frameIndex],
                sizeScale = NEW_POSE_VISUAL_SCALE,
                // Forward-jump art also faces right; normalize before applying pet facing.
                modifier = modifier.graphicsLayer { scaleX = -1f }
            )
        } else {
            // In-place hops (celebration/prank cue/etc.) keep the original REAL jump look.
            val frameIndex = if (reduceAnimations) 0 else
                (progress * LEGACY_JUMP_FRAMES.size).toInt().coerceIn(0, LEGACY_JUMP_FRAMES.lastIndex)
            RealPetSpriteImage(
                resource = LEGACY_JUMP_FRAMES[frameIndex],
                sourceOffset = IntOffset(18, 82),
                sourceSize = IntSize(220, 164),
                modifier = modifier
            )
        }
        return
    }

    val frames: IntArray
    val frameDelayMs: Long
    when (state) {
        RealPetState.Walking -> {
            frames = WALK_FRAMES
            frameDelayMs = 125L
        }
        RealPetState.Running -> {
            frames = WALK_FRAMES
            frameDelayMs = 70L
        }
        RealPetState.Idle -> {
            frames = IDLE_FRAMES
            frameDelayMs = 430L
        }
        RealPetState.Meowing -> {
            frames = MEOW_FRAMES
            frameDelayMs = 170L
        }
        else -> {
            frames = IDLE_FRAMES
            frameDelayMs = 430L
        }
    }

    var frameIndex by remember(state) { mutableIntStateOf(0) }
    LaunchedEffect(state, frames, reduceAnimations) {
        frameIndex = 0
        if (!reduceAnimations && frames.size > 1) {
            while (isActive) {
                delay(frameDelayMs)
                frameIndex = (frameIndex + 1) % frames.size
            }
        }
    }

    val cropOffset: IntOffset
    val cropSize: IntSize
    when (state) {
        RealPetState.Meowing -> {
            cropOffset = IntOffset(31, 22)
            cropSize = IntSize(175, 189)
        }
        RealPetState.Idle -> {
            cropOffset = IntOffset(18, 35)
            cropSize = IntSize(220, 211)
        }
        else -> {
            cropOffset = IntOffset(18, 82)
            cropSize = IntSize(220, 164)
        }
    }
    RealPetSpriteImage(
        resource = frames[frameIndex],
        sourceOffset = cropOffset,
        sourceSize = cropSize,
        sizeScale = when (state) {
            RealPetState.Idle -> 60f / 50f
            RealPetState.Meowing -> 65f / 50f
            else -> 1f
        },
        modifier = modifier
    )
}
