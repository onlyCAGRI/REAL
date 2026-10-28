package com.cagri.real.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.cagri.real.R

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

    RealPetFrameImage(
        resource = pose,
        breath = breath,
        modifier = modifier
    )
}

internal const val REAL_PET_SLEEP_POSE_COUNT = 8

// Eight separate poses from white-kitten(1).zip, not sequential animation frames.
private val SLEEP_POSES = intArrayOf(
    R.drawable.real_pet_sleep_1,
    R.drawable.real_pet_sleep_2,
    R.drawable.real_pet_sleep_3,
    R.drawable.real_pet_sleep_4,
    R.drawable.real_pet_sleep_5,
    R.drawable.real_pet_sleep_6,
    R.drawable.real_pet_sleep_7,
    R.drawable.real_pet_sleep_8
)
