package com.cagri.real.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * REAL motion tokens, modelled on Apple's spring model.
 *
 * stiffness = (2π / duration)²  (mass is fixed at 1 in Compose)
 * dampingRatio = 1 - bounce
 *
 * Use for spatial values (offset, scale, size). Keep alpha/colour on tween or a
 * dampingRatio = 1 spring so they never overshoot.
 */
object RealMotion {
    private fun stiffnessFor(durationSec: Float): Float {
        val w = (2f * Math.PI.toFloat()) / durationSec
        return w * w
    }

    /** No bounce. Layout, list placement. */
    fun <T> smooth(durationSec: Float = 0.4f): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = stiffnessFor(durationSec))

    /** Barely-there settle. Press release, dock. */
    fun <T> snappy(durationSec: Float = 0.35f): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = stiffnessFor(durationSec))

    /** Finger-driven release (swipe, fling). Carry the gesture velocity into it. */
    fun <T> interactive(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.86f, stiffness = stiffnessFor(0.15f))
}
