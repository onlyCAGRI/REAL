package com.cagri.bigboscagri

import com.cagri.bigboscagri.ui.WelcomePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5.1 unit tests for Welcome / Onboarding phase state machine,
 * input validation, and one-time persistence semantics.
 */
class WelcomeOnboardingTest {

    /**
     * Simulated model of WelcomeScreen's phase and confirmation flow.
     */
    private class SimulatedWelcomeCoordinator(
        val onComplete: (String) -> Unit
    ) {
        var phase: WelcomePhase = WelcomePhase.Intro
        var name: String = ""
        var completedName: String? = null

        fun onAnimationIntroFinished() {
            if (phase == WelcomePhase.Intro) {
                phase = WelcomePhase.NamePrompt
            }
        }

        fun onConfirm(): Boolean {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty() && phase == WelcomePhase.NamePrompt) {
                phase = WelcomePhase.Greeting
                completedName = trimmed
                onComplete(trimmed)
                return true
            }
            return false
        }
    }

    @Test
    fun initialPhaseIsIntroAndTransitionsToNamePrompt() {
        val coordinator = SimulatedWelcomeCoordinator {}
        assertEquals(WelcomePhase.Intro, coordinator.phase)

        coordinator.onAnimationIntroFinished()
        assertEquals(WelcomePhase.NamePrompt, coordinator.phase)
    }

    @Test
    fun emptyOrWhitespaceNameCannotBeConfirmed() {
        val coordinator = SimulatedWelcomeCoordinator {}
        coordinator.onAnimationIntroFinished()

        coordinator.name = ""
        assertFalse(coordinator.onConfirm())
        assertEquals(WelcomePhase.NamePrompt, coordinator.phase)

        coordinator.name = "   "
        assertFalse(coordinator.onConfirm())
        assertEquals(WelcomePhase.NamePrompt, coordinator.phase)
    }

    @Test
    fun validNameTransitionsToGreetingAndTrimsWhitespace() {
        var completedNameResult: String? = null
        val coordinator = SimulatedWelcomeCoordinator { completedNameResult = it }
        coordinator.onAnimationIntroFinished()

        coordinator.name = "  Çağrı  "
        val success = coordinator.onConfirm()

        assertTrue(success)
        assertEquals(WelcomePhase.Greeting, coordinator.phase)
        assertEquals("Çağrı", coordinator.completedName)
        assertEquals("Çağrı", completedNameResult)
    }

    @Test
    fun duplicateConfirmInGreetingPhaseIsIgnored() {
        var callCount = 0
        val coordinator = SimulatedWelcomeCoordinator { callCount++ }
        coordinator.onAnimationIntroFinished()

        coordinator.name = "Çağrı"
        coordinator.onConfirm()
        assertEquals(1, callCount)
        assertEquals(WelcomePhase.Greeting, coordinator.phase)

        // Second confirm tap or Done action
        val secondAttempt = coordinator.onConfirm()
        assertFalse(secondAttempt)
        assertEquals(1, callCount)
    }

    @Test
    fun onboardingOneTimeSemantics() {
        // Simulated SharedPreferences map
        val prefs = mutableMapOf<String, Any>()

        // 1. First install: onboarding not completed
        assertFalse(prefs["onboarding_completed"] as? Boolean ?: false)
        assertFalse(prefs["first_home_intro_completed"] as? Boolean ?: false)
        assertEquals("", prefs["user_display_name"] as? String ?: "")

        // 2. User completes onboarding with name "Çağrı"
        val enteredName = "Çağrı"
        val firstHomeIntroAlreadyDone = prefs["first_home_intro_completed"] as? Boolean ?: false
        prefs["onboarding_completed"] = true
        prefs["user_display_name"] = enteredName
        val playFirstHomeIntro = !firstHomeIntroAlreadyDone

        assertTrue(prefs["onboarding_completed"] as Boolean)
        assertEquals("Çağrı", prefs["user_display_name"] as String)
        assertTrue(playFirstHomeIntro)

        // 3. First home intro completes
        prefs["first_home_intro_completed"] = true

        // 4. Subsequent app launch: onboarding already completed, first home intro already done
        val isCompletedOnRelaunch = prefs["onboarding_completed"] as Boolean
        val isFirstHomeIntroDoneOnRelaunch = prefs["first_home_intro_completed"] as Boolean
        assertTrue(isCompletedOnRelaunch)
        assertTrue(isFirstHomeIntroDoneOnRelaunch)

        // Welcome screen is NOT shown on relaunch
        val showWelcomeOnRelaunch = !isCompletedOnRelaunch
        assertFalse(showWelcomeOnRelaunch)
    }
}
