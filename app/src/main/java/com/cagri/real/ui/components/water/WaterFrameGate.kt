package com.cagri.real.ui.components.water

/** VSYNC-aligned drawing budget. Physics receives actual elapsed time, never a fixed 1/30 step. */
internal class WaterFrameGate {
    private var previous = Long.MIN_VALUE
    private var next = Long.MIN_VALUE

    fun elapsedSeconds(now: Long): Float? {
        if (previous == Long.MIN_VALUE || now < previous) {
            previous = now
            next = now + INTERVAL_NANOS
            return 0f
        }
        // Small clock jitter must not turn a two-VSYNC interval into three on a 60Hz display.
        if (now + JITTER_NANOS < next) return null
        val elapsed = (now - previous) / 1_000_000_000f
        previous = now
        val deadline = next + INTERVAL_NANOS
        // A slow frame does not create a burst of catch-up draws.
        next = if (deadline <= now + JITTER_NANOS) now + INTERVAL_NANOS else deadline
        return elapsed
    }

    companion object {
        private const val INTERVAL_NANOS = 33_333_333L
        private const val JITTER_NANOS = 1_000_000L
    }
}
