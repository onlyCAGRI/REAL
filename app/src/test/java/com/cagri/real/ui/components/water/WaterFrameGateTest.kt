package com.cagri.real.ui.components.water

import org.junit.Assert.*
import org.junit.Test

class WaterFrameGateTest {
    private val start = 1_000_000_000L

    @Test fun firstFrameDoesNotInjectBackgroundElapsedTime() {
        assertEquals(0f, WaterFrameGate().elapsedSeconds(start)!!, 0f)
    }
    @Test fun sixtyAndOneHundredTwentyHzBothProduceAboutThirtyDrawsPerSecond() {
        for (refreshRate in listOf(60, 120)) {
            val gate = WaterFrameGate()
            val updates = (0 until refreshRate).count { index ->
                gate.elapsedSeconds(start + index * 1_000_000_000L / refreshRate) != null
            }
            assertEquals("Unexpected draw count at ${refreshRate}Hz", 30, updates)
        }
    }
    @Test fun actualElapsedTimeReachesPhysicsRatherThanFixedFpsStep() {
        val gate = WaterFrameGate()
        gate.elapsedSeconds(start)
        assertNull(gate.elapsedSeconds(start + 16_000_000L))
        assertEquals(0.037f, gate.elapsedSeconds(start + 37_000_000L)!!, 0.000001f)
    }
    @Test fun aSmallVsyncJitterDoesNotLoseAWholeExtraFrame() {
        val gate = WaterFrameGate()
        gate.elapsedSeconds(start)
        assertNotNull(gate.elapsedSeconds(start + 32_833_333L))
        assertNull(gate.elapsedSeconds(start + 49_000_000L))
        assertNotNull(gate.elapsedSeconds(start + 66_666_666L))
    }
    @Test fun slowFrameDoesNotCreateCatchUpBurst() {
        val gate = WaterFrameGate()
        gate.elapsedSeconds(start)
        assertEquals(0.2f, gate.elapsedSeconds(start + 200_000_000L)!!, 0.000001f)
        assertNull(gate.elapsedSeconds(start + 201_000_000L))
        assertNull(gate.elapsedSeconds(start + 216_000_000L))
        assertNotNull(gate.elapsedSeconds(start + 233_333_333L))
    }
    @Test fun aNewForegroundGateStartsWithoutThePauseGap() {
        val old = WaterFrameGate()
        old.elapsedSeconds(start)
        old.elapsedSeconds(start + 33_333_333L)
        assertEquals(0f, WaterFrameGate().elapsedSeconds(start + 300_000_000_000L)!!, 0f)
    }
    @Test fun clockResetAndRepeatedTimestampAreSafe() {
        val gate = WaterFrameGate()
        gate.elapsedSeconds(start)
        assertNull(gate.elapsedSeconds(start))
        assertEquals(0f, gate.elapsedSeconds(start - 10L)!!, 0f)
    }
}
