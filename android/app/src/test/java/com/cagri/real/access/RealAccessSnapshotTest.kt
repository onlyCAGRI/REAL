package com.cagri.real.access

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealAccessSnapshotTest {
    private fun snapshot(plan: String = "FREE", rollout: String = "OBSERVE",
        features: Set<RealFeature> = emptySet(), tester: Boolean = false) = RealAccessSnapshot(
        uid = "account", plan = plan, source = if (tester) "TESTER" else plan,
        tester = tester, expiresAt = null, serverTime = 0, cacheUntil = 1000,
        rollout = rollout, features = features, effectiveFeatures = RealFeature.entries.toSet(),
        aiUsed = 0, aiLimit = 100, aiResetAt = 1000
    )

    @Test fun freeCannotUseProFeaturesEvenWhenObserveAdvertisesEffectiveAccess() {
        for (mode in listOf("OBSERVE", "ENFORCE")) {
            val value = snapshot(rollout = mode)
            RealFeature.entries.forEach { assertFalse(value.allows(it)) }
        }
    }

    @Test fun proAndTesterCanUseOnlyTheirGrantedFeatures() {
        for (tester in listOf(false, true)) {
            val value = snapshot(plan = "PRO", features = setOf(RealFeature.VoiceAi), tester = tester)
            assertTrue(value.allows(RealFeature.VoiceAi))
            assertFalse(value.allows(RealFeature.MoodAi))
        }
    }

    @Test fun featureFlagsAloneDoNotOverrideFreePlan() {
        val value = snapshot(features = RealFeature.entries.toSet())
        RealFeature.entries.forEach { assertFalse(value.allows(it)) }
    }
}
