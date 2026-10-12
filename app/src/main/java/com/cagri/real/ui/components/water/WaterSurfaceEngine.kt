package com.cagri.real.ui.components.water

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Frame-rate independent, damped surface motion. Values use normalized canvas coordinates. */
internal const val MAX_VISUAL_OVERFLOW_PROGRESS = 1.40f

internal class WaterSurfaceEngine(initialFill: Float, private val smoothWater: Boolean = false) {
    var fill = initialFill.coerceIn(0f, MAX_VISUAL_OVERFLOW_PROGRESS)
        private set
    var targetFill = fill
    var targetTilt = 0f
    var aspectRatio = 0.65f
    var tilt = 0f
        private set
    var time = 0f
        private set
    var energy = 0f
        private set
    var touchX = 0.5f
        private set
    private var fillVelocity = 0f
    private var tiltVelocity = 0f

    fun disturb(x: Float, strength: Float = 0.55f) {
        touchX = x.coerceIn(0f, 1f)
        energy = (energy + strength).coerceAtMost(1.6f)
    }

    fun step(elapsedSeconds: Float, reducedMotion: Boolean) {
        if (reducedMotion) {
            fill = targetFill.coerceIn(0f, MAX_VISUAL_OVERFLOW_PROGRESS)
            fillVelocity = 0f
            tilt = 0f
            tiltVelocity = 0f
            energy = 0f
            return
        }
        // Substeps keep sensor impulses stable on slow frames and after lifecycle changes.
        var remaining = elapsedSeconds.coerceIn(0f, 0.10f)
        while (remaining > 0f) {
            val dt = minOf(remaining, 1f / 120f)
            val fillSpring = if (smoothWater) 20f else 34f
            val fillDamping = if (smoothWater) 9f else 11f
            fillVelocity += ((targetFill - fill) * fillSpring - fillVelocity * fillDamping) * dt
            fill = (fill + fillVelocity * dt).coerceIn(0f, MAX_VISUAL_OVERFLOW_PROGRESS)
            tiltVelocity += ((targetTilt.coerceIn(-0.65f, 0.65f) - tilt) * 38f - tiltVelocity * (if (smoothWater) 9f else 5.8f)) * dt
            tilt = (tilt + tiltVelocity * dt).coerceIn(-0.8f, 0.8f)
            energy *= exp(-dt * 1.6f)
            time += dt
            remaining -= dt
        }
    }

    fun surface(x: Float, reducedMotion: Boolean = false): Float {
        val baseline = 0.93f - fill * 0.69f
        if (reducedMotion) return baseline
        val pace = if (smoothWater) time * 0.7f else time
        val wave = sin(x * PI.toFloat() * 2f + pace * 1.45f) * 0.009f +
            sin(x * PI.toFloat() * 3.6f - pace * 1.1f) * 0.004f
        val ripple = if (smoothWater) {
            sin((x - touchX) * 12f - time * 3.8f) * energy * 0.012f
        } else sin((x - touchX) * 19f - time * 7f) * energy * 0.018f
        return baseline + (x - 0.5f) * tilt * aspectRatio + wave + ripple
    }
}
