package com.cagri.bigboscagri.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cagri.bigboscagri.R

/** A pose stays still throughout a nap; only its breathing transform is animated. */
@Composable
internal fun RealPetSleepingImage(
    poseIndex: Int,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier
) {
    // The layer advances only when a new nap begins; no repeats until all eight are shown.
    val pose = SLEEP_POSES[poseIndex.coerceAtLeast(0) % SLEEP_POSES.size]
    val breath = if (reduceAnimations) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "realPetSleepBreathing")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 4_200
                    0f at 0 using FastOutSlowInEasing
                    1f at 1_600 using FastOutSlowInEasing
                    0f at 3_800
                    0f at 4_200
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "realPetSleepBreath"
        )
        value
    }

    RealPetSpriteImage(
        resource = pose.resource,
        sourceOffset = pose.offset,
        sourceSize = pose.size,
        sizeScale = 40f / 50f,
        breath = breath,
        modifier = modifier
    )
}

internal const val REAL_PET_SLEEP_POSE_COUNT = 8

private data class SleepPose(
    val resource: Int,
    val offset: IntOffset,
    val size: IntSize
)

// Eight separate poses from white-kitten(1).zip, not sequential animation frames.
// Bounds are the nontransparent region of each original PNG.
private val SLEEP_POSES = listOf(
    SleepPose(R.drawable.real_pet_sleep_1, IntOffset(25, 35), IntSize(508, 430)),
    SleepPose(R.drawable.real_pet_sleep_2, IntOffset(29, 76), IntSize(708, 552)),
    SleepPose(R.drawable.real_pet_sleep_3, IntOffset(12, 106), IntSize(685, 494)),
    SleepPose(R.drawable.real_pet_sleep_4, IntOffset(19, 15), IntSize(955, 510)),
    SleepPose(R.drawable.real_pet_sleep_5, IntOffset(4, 19), IntSize(805, 407)),
    SleepPose(R.drawable.real_pet_sleep_6, IntOffset(7, 8), IntSize(654, 379)),
    SleepPose(R.drawable.real_pet_sleep_7, IntOffset(28, 6), IntSize(623, 405)),
    SleepPose(R.drawable.real_pet_sleep_8, IntOffset(25, 21), IntSize(647, 401))
)
