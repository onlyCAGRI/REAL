package com.cagri.real.ui.components

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Original animation keys, timings and curves, isolated from the composer's rendering body. */
@Composable
internal fun rememberComposerAiPulse(processing: Boolean, reduceAnimations: Boolean): Animatable<Float, AnimationVector1D> {
    val aiPulse = remember { Animatable(0f) }

    LaunchedEffect(processing, reduceAnimations) {
        aiPulse.stop()
        aiPulse.snapTo(0f)
        if (processing && !reduceAnimations) {
            while (isActive) {
                aiPulse.animateTo(1f, tween(560, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
                aiPulse.animateTo(0f, tween(560, easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)))
            }
        }
    }

    return aiPulse
}

@Composable
internal fun rememberComposerVoiceArrival(isVoiceDraft: Boolean, reduceAnimations: Boolean): Animatable<Float, AnimationVector1D> {
    val voiceArrivalProgress = remember(isVoiceDraft) {
        Animatable(if (isVoiceDraft && !reduceAnimations) 0f else 1f)
    }
    LaunchedEffect(isVoiceDraft) {
        if (isVoiceDraft && !reduceAnimations) {
            voiceArrivalProgress.snapTo(0f)
            voiceArrivalProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(180, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))
            )
        }
    }
    return voiceArrivalProgress
}

@Composable
internal fun rememberComposerShimmer(shouldShimmer: Boolean): Animatable<Float, AnimationVector1D> {
    val shimmerProgress = remember { Animatable(0f) }

    LaunchedEffect(shouldShimmer) {
        if (shouldShimmer) {
            delay(800)
            while (isActive) {
                shimmerProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 1600,
                        easing = LinearEasing
                    )
                )
                shimmerProgress.snapTo(0f)
                delay(5000)
            }
        } else {
            shimmerProgress.snapTo(0f)
        }
    }

    return shimmerProgress
}
