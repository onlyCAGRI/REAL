package com.cagri.real

import com.cagri.real.startup.StartupPhase
import com.cagri.real.startup.StartupSession
import org.junit.Assert.*
import org.junit.Test

class StartupSessionTest {
    @Test fun unresolvedAuthNeverRoutesToWelcomeOrEmptyHome() {
        val s = StartupSession().state.value
        assertEquals(StartupPhase.UNKNOWN, s.phase)
        assertFalse(s.showWelcome(false))
        assertTrue(s.blocksHome("u:a", false, 0))
    }

    @Test fun restoredSessionWaitsForSyncAndRenderedProjection() {
        val c = StartupSession()
        c.authResolved("a", "u:a")
        assertEquals(StartupPhase.SIGNED_IN_LOADING, c.state.value.phase)
        assertTrue(c.state.value.blocksHome("u:a", false, 0))
        c.completed(c.state.value.generation)
        val s = c.state.value
        assertEquals(StartupPhase.READY, s.phase)
        assertTrue(s.blocksHome("u:a", false, 0))
        assertFalse(s.blocksHome("u:a", false, s.revision))
    }

    @Test fun googleSuccessBypassesMissingOnboardingEvenAfterUiRecreation() {
        val c = StartupSession()
        c.authResolved(null, "guest:g")
        assertTrue(c.state.value.showWelcome(false))
        // Firebase observation, independently of the destroyed Welcome callback.
        c.authResolved("google", "u:google")
        assertFalse(c.state.value.showWelcome(false))
        assertTrue(c.state.value.blocksHome("guest:g", true, 0))
        val generation = c.state.value.generation
        c.authResolved("google", "u:google") // listener replay / recreated controller
        assertEquals(generation, c.state.value.generation)
        assertFalse(c.state.value.showWelcome(false))
        c.completed(generation)
        assertFalse(c.state.value.showWelcome(false))
        assertFalse(c.state.value.blocksHome("u:google", false, c.state.value.revision))
    }

    @Test fun existingLocalCardsRemainUsableDuringLoadingAndNetworkFailure() {
        val c = StartupSession()
        c.authResolved("a", "u:a")
        assertFalse(c.state.value.blocksHome("u:a", true, -1))
        c.failed(c.state.value.generation)
        assertFalse(c.state.value.blocksHome("u:a", true, -1))
        assertEquals(StartupPhase.ERROR_OFFLINE, c.state.value.phase)
        assertFalse(c.state.value.showWelcome(false))
    }

    @Test fun appCheckFailureIsNotAnEmptyAccountAndRetryCanRecover() {
        val c = StartupSession()
        c.authResolved("a", "u:a")
        c.failed(c.state.value.generation)
        assertTrue(c.state.value.blocksHome("u:a", false, 0))
        c.retry()
        assertEquals(StartupPhase.SIGNED_IN_LOADING, c.state.value.phase)
        c.completed(c.state.value.generation)
        assertFalse(c.state.value.blocksHome("u:a", false, c.state.value.revision))
    }

    @Test fun offlineEmptyWorkspaceRequiresExplicitChoice() {
        val c = StartupSession()
        c.authResolved("a", "u:a")
        c.allowOffline()
        assertTrue(c.state.value.blocksHome("u:a", false, 0))
        c.failed(c.state.value.generation)
        c.allowOffline()
        assertFalse(c.state.value.blocksHome("u:a", false, 0))
        assertEquals(StartupPhase.ERROR_OFFLINE, c.state.value.phase)
    }

    @Test fun staleSyncCannotCompleteOrFailNewAccount() {
        val c = StartupSession()
        c.authResolved("a", "u:a")
        val old = c.state.value.generation
        c.authResolved("b", "u:b")
        c.completed(old)
        c.failed(old)
        assertEquals(StartupPhase.SIGNED_IN_LOADING, c.state.value.phase)
        assertEquals("b", c.state.value.uid)
    }

    @Test fun logoutUsesRealGuestStateAndIgnoresOldCompletion() {
        val c = StartupSession()
        c.authResolved("a", "u:a")
        val old = c.state.value.generation
        c.authResolved(null, "guest:new")
        c.completed(old)
        assertEquals(StartupPhase.SIGNED_OUT, c.state.value.phase)
        assertTrue(c.state.value.showWelcome(false))
        assertFalse(c.state.value.showWelcome(true)) // existing local guest onboarding
    }

    @Test fun backgroundSyncDoesNotRestartAnAlreadyResolvedEmptyHome() {
        val c = StartupSession()
        c.authResolved("a", "u:a")
        val generation = c.state.value.generation
        c.completed(generation)
        val ready = c.state.value
        c.failed(generation)
        c.completed(generation)
        assertEquals(ready, c.state.value)
        assertFalse(c.state.value.blocksHome("u:a", false, ready.revision))
    }

    @Test fun staleCompletionAfterLogoutAndReloginToSameUidIsIgnored() {
        val c = StartupSession()
        c.authResolved("a", "u:a")
        val old = c.state.value.generation
        c.authResolved(null, "guest:new")
        c.authResolved("a", "u:a")
        c.completed(old)
        assertEquals(StartupPhase.SIGNED_IN_LOADING, c.state.value.phase)
    }
}
