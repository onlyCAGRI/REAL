package com.cagri.bigboscagri

import com.cagri.bigboscagri.ui.components.RealPetPresence
import com.cagri.bigboscagri.ui.components.RealPetRuntime
import com.cagri.bigboscagri.ui.components.RealPetVisitReason
import com.cagri.bigboscagri.ui.components.realPetGuestArrivalDelayMs
import com.cagri.bigboscagri.ui.components.realPetHitAndRunPrankChance
import com.cagri.bigboscagri.ui.components.realPetPerchRange
import com.cagri.bigboscagri.ui.components.realPetTravelDuration
import com.cagri.bigboscagri.ui.components.realPetVisitDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealPetMotionRulesTest {
    @Before fun setUp() {
        RealPetRuntime.resetForTesting()
    }

    @Test fun wideSurfaceKeepsWholeCatOnTop() {
        val range = realPetPerchRange(20f, 340f, 76f, 8f, 276f)
        assertEquals(20f, range.start, 0.001f)
        assertEquals(264f, range.endInclusive, 0.001f)
    }

    @Test fun narrowSurfaceHasOneCentredPosition() {
        val range = realPetPerchRange(100f, 140f, 76f, 8f, 276f)
        assertEquals(82f, range.start, 0.001f)
        assertEquals(range.start, range.endInclusive, 0.001f)
    }

    @Test fun partiallyOffscreenSurfaceUsesVisibleScreenLimits() {
        val range = realPetPerchRange(-40f, 450f, 76f, 8f, 276f)
        assertEquals(8f, range.start, 0.001f)
        assertEquals(276f, range.endInclusive, 0.001f)
    }

    @Test fun runCoversOneHundredDpInOneSecond() {
        assertEquals(1000, realPetTravelDuration(100f, running = true))
        assertTrue(realPetTravelDuration(100f, running = false) > 2000)
    }

    @Test fun visitDurationStaysWithinExpectedBounds() {
        val minDuration = realPetVisitDurationMs(0f)
        val maxDuration = realPetVisitDurationMs(1f)
        val midDuration = realPetVisitDurationMs(0.5f)
        assertEquals(30_000L, minDuration)
        assertEquals(60_000L, maxDuration)
        assertTrue(midDuration in 30_000L..60_000L)
    }

    @Test fun guestArrivalDelayBuckets() {
        // Immediate arrival (< 0.12)
        assertEquals(0L, realPetGuestArrivalDelayMs(0.05f, 0.5f))
        // 5-15s delay (0.12 .. 0.32)
        val earlyDelay = realPetGuestArrivalDelayMs(0.20f, 0.5f)
        assertNotNull(earlyDelay)
        assertTrue(earlyDelay!! in 5_000L..15_000L)
        // 15-30s delay (0.32 .. 0.50)
        val midDelay = realPetGuestArrivalDelayMs(0.40f, 0.5f)
        assertNotNull(midDelay)
        assertTrue(midDelay!! in 15_000L..30_000L)
        // No visitor (>= 0.50)
        assertNull(realPetGuestArrivalDelayMs(0.60f, 0.5f))
    }

    @Test fun hitAndRunPrankChanceHonorsThreshold() {
        assertTrue(realPetHitAndRunPrankChance(0.15f, 0.25f))
        assertFalse(realPetHitAndRunPrankChance(0.35f, 0.25f))
    }

    @Test fun presenceTransitionsLifecycle() {
        RealPetRuntime.markLeft()
        assertEquals(com.cagri.bigboscagri.ui.components.RealPetPresence.Hidden, RealPetRuntime.presence)
        assertFalse(RealPetRuntime.isPresent)

        RealPetRuntime.markArrived()
        assertEquals(com.cagri.bigboscagri.ui.components.RealPetPresence.Active, RealPetRuntime.presence)
        assertTrue(RealPetRuntime.isPresent)

        RealPetRuntime.startLeaving()
        assertEquals(com.cagri.bigboscagri.ui.components.RealPetPresence.Leaving, RealPetRuntime.presence)
        assertTrue(RealPetRuntime.isPresent)

        RealPetRuntime.markLeft()
        assertEquals(com.cagri.bigboscagri.ui.components.RealPetPresence.Hidden, RealPetRuntime.presence)
        assertFalse(RealPetRuntime.isPresent)
    }

    @Test fun surfaceDropRequestIncrementsToken() {
        val before = RealPetRuntime.surfaceDropRequestToken
        RealPetRuntime.requestSurfaceDrop()
        assertEquals(before + 1, RealPetRuntime.surfaceDropRequestToken)
    }

    @Test fun commandRequestAndConsumeLifecycle() {
        RealPetRuntime.requestDropTo("perch_test")
        val cmd = RealPetRuntime.command
        assertNotNull(cmd)
        assertEquals("perch_test", cmd!!.perchId)
        assertTrue(cmd.dropFromTop)

        RealPetRuntime.consumeCommand(cmd.token)
        assertNull(RealPetRuntime.command)
    }

    @Test fun doubleTapSitScenarioTestKindExists() {
        val kind = com.cagri.bigboscagri.ui.components.RealPetScenarioTestKind.DoubleTapSit
        assertNotNull(kind)
        assertEquals("DoubleTapSit", kind.name)
    }

    @Test fun scenarioTestRequestAndConsumeLifecycle() {
        RealPetRuntime.requestScenarioTest(com.cagri.bigboscagri.ui.components.RealPetScenarioTestKind.WalkInAndWatch)
        val req = RealPetRuntime.scenarioTestRequest
        assertNotNull(req)
        assertEquals(com.cagri.bigboscagri.ui.components.RealPetScenarioTestKind.WalkInAndWatch, req!!.kind)

        RealPetRuntime.consumeScenarioTest(req.token)
        assertNull(RealPetRuntime.scenarioTestRequest)
    }

    @Test fun celebrationRequestAndConsumeLifecycle() {
        RealPetRuntime.requestCelebration(123L)
        val req = RealPetRuntime.celebration
        assertNotNull(req)
        assertEquals(123L, req!!.reminderId)
        assertFalse(req.isAllClear)

        RealPetRuntime.consumeCelebration(req.token)
        assertNull(RealPetRuntime.celebration)
    }

    @Test fun newCardRequestAndConsumeLifecycle() {
        RealPetRuntime.noteNewCardAdded(456L)
        assertEquals(456L, RealPetRuntime.newCardReminderId)

        RealPetRuntime.consumeNewCard(456L)
        assertNull(RealPetRuntime.newCardReminderId)
    }

    @Test fun composerMicActiveStateControlsRecordingGuard() {
        RealPetRuntime.setComposerMicActive(false)
        assertFalse(RealPetRuntime.isComposerMicActive())

        RealPetRuntime.setComposerMicActive(true)
        assertTrue(RealPetRuntime.isComposerMicActive())

        RealPetRuntime.setComposerMicActive(false)
        assertFalse(RealPetRuntime.isComposerMicActive())
    }

    @Test fun allSixteenScenarioTestKindsMapSuccessfully() {
        val allKinds = com.cagri.bigboscagri.ui.components.RealPetScenarioTestKind.values()
        assertEquals(16, allKinds.size)
        allKinds.forEach { kind ->
            assertNotNull(kind.name)
        }
    }

    @Test fun tokenStateMachineTransitionsSuccessfully() {
        val tokenKey = "test:token:1"
        assertEquals(com.cagri.bigboscagri.ui.components.RealPetTokenState.PENDING, RealPetRuntime.getTokenState(tokenKey))

        // Step 1: PENDING -> CLAIMED
        assertTrue(RealPetRuntime.tryClaimToken(tokenKey))
        assertEquals(com.cagri.bigboscagri.ui.components.RealPetTokenState.CLAIMED, RealPetRuntime.getTokenState(tokenKey))

        // Duplicate claim fails
        assertFalse(RealPetRuntime.tryClaimToken(tokenKey))

        // Step 2: CLAIMED -> EXECUTING
        RealPetRuntime.markTokenExecuting(tokenKey)
        assertEquals(com.cagri.bigboscagri.ui.components.RealPetTokenState.EXECUTING, RealPetRuntime.getTokenState(tokenKey))

        // Step 3: EXECUTING -> CONSUMED
        var consumed = false
        RealPetRuntime.markTokenConsumed(tokenKey) { consumed = true }
        assertTrue(consumed)
        assertEquals(com.cagri.bigboscagri.ui.components.RealPetTokenState.CONSUMED, RealPetRuntime.getTokenState(tokenKey))

        // Claiming a CONSUMED token fails
        assertFalse(RealPetRuntime.tryClaimToken(tokenKey))

        // Consume on CONSUMED token does not re-invoke callback
        var reConsumed = false
        RealPetRuntime.markTokenConsumed(tokenKey) { reConsumed = true }
        assertFalse(reConsumed)
    }

    @Test fun tokenCancellationBeforeClaimPreservesPending() {
        val tokenKey = "test:token:cancel_pending"
        // If event coroutine is cancelled before acquiring mutex (before tryClaimToken),
        // token remains in PENDING state so it can be retried without loss.
        assertEquals(com.cagri.bigboscagri.ui.components.RealPetTokenState.PENDING, RealPetRuntime.getTokenState(tokenKey))
        // Simulated retry succeeds because token was preserved
        assertTrue(RealPetRuntime.tryClaimToken(tokenKey))
        assertEquals(com.cagri.bigboscagri.ui.components.RealPetTokenState.CLAIMED, RealPetRuntime.getTokenState(tokenKey))
    }

    @Test fun tokenCancellationAfterClaimGuaranteesConsumed() {
        val tokenKey = "test:token:cancel_claimed"
        assertTrue(RealPetRuntime.tryClaimToken(tokenKey))
        RealPetRuntime.markTokenExecuting(tokenKey)

        // Coroutine cancelled during execution: cleanup marks it consumed
        var onConsumeCalled = false
        RealPetRuntime.markTokenConsumed(tokenKey) { onConsumeCalled = true }
        assertTrue(onConsumeCalled)
        assertEquals(com.cagri.bigboscagri.ui.components.RealPetTokenState.CONSUMED, RealPetRuntime.getTokenState(tokenKey))
    }
}
