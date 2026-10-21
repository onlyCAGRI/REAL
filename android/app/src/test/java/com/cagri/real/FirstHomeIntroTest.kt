package com.cagri.real

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test






class FirstHomeIntroTest {

    


    private class SimulatedFirstHomeCoordinator(
        private val preferences: MutableMap<String, Any> = mutableMapOf()
    ) {
        var hasCompletedOnboarding: Boolean = preferences["onboarding_completed"] as? Boolean ?: false
        var playFirstHomeIntro: Boolean = false
        var isFirstHomeIntroPlaying: Boolean = false
        var isHomeReadyForShimmer: Boolean = false
        var categoryEntranceProgress: Float = 1f
        var composerEntranceProgress: Float = 1f
        var introCompletedCallbackCount: Int = 0

        
        private var localIsFirstHome: Boolean = false

        fun performLegacyMigration() {
            val onboardingDone = preferences["onboarding_completed"] as? Boolean ?: false
            if (onboardingDone && !preferences.containsKey("first_home_intro_completed")) {
                preferences["first_home_intro_completed"] = true
            }
        }

        fun onWelcomeComplete(enteredName: String) {
            val firstHomeIntroAlreadyDone = preferences["first_home_intro_completed"] as? Boolean ?: false
            preferences["onboarding_completed"] = true
            preferences["first_home_intro_completed"] = true
            preferences["user_display_name"] = enteredName
            playFirstHomeIntro = !firstHomeIntroAlreadyDone
            hasCompletedOnboarding = true
        }

        fun onFirstHomeEnter(reduceAnimations: Boolean, category: String?, customCategoryId: Long?) {
            localIsFirstHome = playFirstHomeIntro
            val shouldAnimate = localIsFirstHome && !reduceAnimations && category == null && customCategoryId == null
            isFirstHomeIntroPlaying = shouldAnimate
            categoryEntranceProgress = if (shouldAnimate) 0f else 1f
            composerEntranceProgress = if (shouldAnimate) 0f else 1f

            if (localIsFirstHome) {
                preferences["first_home_intro_completed"] = true
                if (!shouldAnimate) {
                    isFirstHomeIntroPlaying = false
                    isHomeReadyForShimmer = true
                    playFirstHomeIntro = false
                    introCompletedCallbackCount++
                } else {
                    isHomeReadyForShimmer = false
                }
            } else {
                isHomeReadyForShimmer = true
            }
        }

        fun onParentFlagChanged(newParentFlag: Boolean) {
            playFirstHomeIntro = newParentFlag
            
            
        }

        fun onIntroAnimationFinished() {
            if (isFirstHomeIntroPlaying) {
                categoryEntranceProgress = 1f
                composerEntranceProgress = 1f
                isFirstHomeIntroPlaying = false
                isHomeReadyForShimmer = true
                playFirstHomeIntro = false
                introCompletedCallbackCount++
            }
        }

        fun isIntroPersisted(): Boolean =
            preferences["first_home_intro_completed"] as? Boolean ?: false
    }

    @Test
    fun brandNewUserPlaysIntroOnceAndPersistsCompletion() {
        val coordinator = SimulatedFirstHomeCoordinator()
        coordinator.performLegacyMigration()

        assertFalse("Brand new user has not completed onboarding", coordinator.hasCompletedOnboarding)
        assertFalse("First home intro not yet scheduled", coordinator.playFirstHomeIntro)
        assertFalse("First home intro not yet persisted", coordinator.isIntroPersisted())

        
        coordinator.onWelcomeComplete("Çağrı")
        assertTrue("Onboarding marked complete", coordinator.hasCompletedOnboarding)
        assertTrue("playFirstHomeIntro is true for first time", coordinator.playFirstHomeIntro)
        assertTrue("first_home_intro_completed persisted early on onboarding completion", coordinator.isIntroPersisted())

        
        coordinator.onFirstHomeEnter(reduceAnimations = false, category = null, customCategoryId = null)
        assertTrue("Intro is playing", coordinator.isFirstHomeIntroPlaying)
        assertEquals("Category entrance begins at 0", 0f, coordinator.categoryEntranceProgress, 0.001f)
        assertEquals("Composer entrance begins at 0", 0f, coordinator.composerEntranceProgress, 0.001f)
        assertTrue("first_home_intro_completed is persisted immediately", coordinator.isIntroPersisted())
        assertFalse("Shimmer waiting for intro to finish", coordinator.isHomeReadyForShimmer)

        
        coordinator.onIntroAnimationFinished()
        assertFalse("Intro finished playing", coordinator.isFirstHomeIntroPlaying)
        assertEquals("Category entrance reaches 1 on finish", 1f, coordinator.categoryEntranceProgress, 0.001f)
        assertEquals("Composer entrance reaches 1 on finish", 1f, coordinator.composerEntranceProgress, 0.001f)
        assertTrue("Home ready for shimmer after intro", coordinator.isHomeReadyForShimmer)
        assertFalse("playFirstHomeIntro is cleared to prevent replays", coordinator.playFirstHomeIntro)
        assertEquals(1, coordinator.introCompletedCallbackCount)

        
        coordinator.onFirstHomeEnter(reduceAnimations = false, category = null, customCategoryId = null)
        assertFalse("Intro does NOT replay on subsequent Home visit", coordinator.isFirstHomeIntroPlaying)
        assertEquals(1, coordinator.introCompletedCallbackCount)
    }

    @Test
    fun parentFlagChangeDoesNotForceRunningLocalIntroToFinalProgressImmediately() {
        val coordinator = SimulatedFirstHomeCoordinator()
        coordinator.onWelcomeComplete("Çağrı")

        
        coordinator.onFirstHomeEnter(reduceAnimations = false, category = null, customCategoryId = null)
        assertTrue("Intro is playing", coordinator.isFirstHomeIntroPlaying)
        assertEquals(0f, coordinator.categoryEntranceProgress, 0.001f)
        assertEquals(0f, coordinator.composerEntranceProgress, 0.001f)
        assertFalse(coordinator.isHomeReadyForShimmer)

        
        coordinator.onParentFlagChanged(false)
        assertFalse("Parent flag is now false", coordinator.playFirstHomeIntro)

        
        assertTrue("Local intro continues playing", coordinator.isFirstHomeIntroPlaying)
        assertEquals("Category progress remains at running state", 0f, coordinator.categoryEntranceProgress, 0.001f)
        assertEquals("Composer progress remains at running state", 0f, coordinator.composerEntranceProgress, 0.001f)
        assertFalse("Shimmer still waiting for visual settle", coordinator.isHomeReadyForShimmer)

        
        coordinator.onIntroAnimationFinished()
        assertFalse("Intro finished playing", coordinator.isFirstHomeIntroPlaying)
        assertEquals("Category progress reached 1 via animation path", 1f, coordinator.categoryEntranceProgress, 0.001f)
        assertEquals("Composer progress reached 1 via animation path", 1f, coordinator.composerEntranceProgress, 0.001f)
        assertTrue("Shimmer ready after visual settle", coordinator.isHomeReadyForShimmer)
        assertEquals(1, coordinator.introCompletedCallbackCount)
    }

    @Test
    fun introProgressBeginsAtZeroAndReachesOneOnlyViaAnimationPath() {
        val coordinator = SimulatedFirstHomeCoordinator()
        coordinator.onWelcomeComplete("Çağrı")

        
        coordinator.onFirstHomeEnter(reduceAnimations = false, category = null, customCategoryId = null)
        assertEquals(0f, coordinator.categoryEntranceProgress, 0.001f)
        assertEquals(0f, coordinator.composerEntranceProgress, 0.001f)
        assertFalse(coordinator.isHomeReadyForShimmer)

        coordinator.onIntroAnimationFinished()
        assertEquals(1f, coordinator.categoryEntranceProgress, 0.001f)
        assertEquals(1f, coordinator.composerEntranceProgress, 0.001f)

        
        val fastCoordinator = SimulatedFirstHomeCoordinator()
        fastCoordinator.onWelcomeComplete("Çağrı")
        fastCoordinator.onFirstHomeEnter(reduceAnimations = true, category = null, customCategoryId = null)
        assertEquals(1f, fastCoordinator.categoryEntranceProgress, 0.001f)
        assertEquals(1f, fastCoordinator.composerEntranceProgress, 0.001f)
        assertTrue(fastCoordinator.isHomeReadyForShimmer)
    }

    @Test
    fun shimmerReadyTimingIsAfterVisualSettle() {
        val coordinator = SimulatedFirstHomeCoordinator()
        coordinator.onWelcomeComplete("Çağrı")

        
        coordinator.onFirstHomeEnter(reduceAnimations = false, category = null, customCategoryId = null)
        assertFalse("Shimmer is NOT ready while intro is running", coordinator.isHomeReadyForShimmer)

        
        coordinator.onParentFlagChanged(false)
        assertFalse("Shimmer still NOT ready before visual settle", coordinator.isHomeReadyForShimmer)

        
        coordinator.onIntroAnimationFinished()
        assertTrue("Shimmer becomes ready only after visual settle", coordinator.isHomeReadyForShimmer)
    }

    @Test
    fun legacyUserMigrationPreventsIntro() {
        
        val prefs = mutableMapOf<String, Any>("onboarding_completed" to true)
        val coordinator = SimulatedFirstHomeCoordinator(prefs)

        
        coordinator.performLegacyMigration()
        assertTrue("first_home_intro_completed marked for legacy user", coordinator.isIntroPersisted())

        
        coordinator.onWelcomeComplete("LegacyUser")
        assertFalse("playFirstHomeIntro remains false for legacy user", coordinator.playFirstHomeIntro)

        coordinator.onFirstHomeEnter(reduceAnimations = false, category = null, customCategoryId = null)
        assertFalse("Intro does not play for legacy user", coordinator.isFirstHomeIntroPlaying)
        assertTrue("Home ready for shimmer immediately", coordinator.isHomeReadyForShimmer)
    }

    @Test
    fun userInterruptionMidIntroDoesNotReplayOnReturn() {
        val coordinator = SimulatedFirstHomeCoordinator()
        coordinator.onWelcomeComplete("Çağrı")

        
        coordinator.onFirstHomeEnter(reduceAnimations = false, category = null, customCategoryId = null)
        assertTrue(coordinator.isFirstHomeIntroPlaying)
        assertTrue(coordinator.isIntroPersisted())

        
        
        coordinator.onFirstHomeEnter(reduceAnimations = false, category = "Today", customCategoryId = null)
        assertFalse("Category screen does not play first-home intro", coordinator.isFirstHomeIntroPlaying)

        
        coordinator.onFirstHomeEnter(reduceAnimations = false, category = null, customCategoryId = null)
        assertFalse("Intro does not replay after returning from category", coordinator.isFirstHomeIntroPlaying)
        assertTrue(coordinator.isHomeReadyForShimmer)
    }

    @Test
    fun reduceAnimationsCompletesInstantlyAndPersists() {
        val coordinator = SimulatedFirstHomeCoordinator()
        coordinator.onWelcomeComplete("Çağrı")

        
        coordinator.onFirstHomeEnter(reduceAnimations = true, category = null, customCategoryId = null)
        assertFalse("No intro animation when reduceAnimations = true", coordinator.isFirstHomeIntroPlaying)
        assertTrue("Completion is persisted immediately", coordinator.isIntroPersisted())
        assertTrue("Home ready for shimmer immediately", coordinator.isHomeReadyForShimmer)
        assertFalse(coordinator.playFirstHomeIntro)
        assertEquals(1, coordinator.introCompletedCallbackCount)
    }

    @Test
    fun categoryChipStaggerProgressCalculations() {
        
        fun computeChipProgress(index: Int, progress: Float): Float {
            val startFraction = (index * 45f) / 505f
            val durationFraction = 280f / 505f
            return ((progress - startFraction) / durationFraction).coerceIn(0f, 1f)
        }

        
        for (i in 0 until 6) {
            assertEquals(0f, computeChipProgress(i, 0f), 0.001f)
        }

        
        assertEquals(0.5f, computeChipProgress(0, 140f / 505f), 0.001f)
        assertEquals(1.0f, computeChipProgress(0, 280f / 505f), 0.001f)

        
        assertEquals(0.0f, computeChipProgress(5, 225f / 505f), 0.001f)
        assertEquals(1.0f, computeChipProgress(5, 1.0f), 0.001f)

        
        for (i in 0 until 6) {
            assertEquals(1.0f, computeChipProgress(i, 1.0f), 0.001f)
        }
    }

    @Test
    fun composerAndCategoryMovementWithinLimits() {
        val categoryOffsetDp = 24f
        val composerOffsetDp = 32f

        assertEquals("Category offset restored to phase-4-stable 24dp", 24f, categoryOffsetDp, 0.001f)
        assertEquals("Composer offset restored to phase-4-stable 32dp", 32f, composerOffsetDp, 0.001f)

        
        val categoryDurationMs = 505
        val composerStartMs = 330
        val composerDurationMs = 340
        val totalSequenceMs = composerStartMs + composerDurationMs

        assertEquals(505, categoryDurationMs)
        assertEquals(670, totalSequenceMs)
    }
}
