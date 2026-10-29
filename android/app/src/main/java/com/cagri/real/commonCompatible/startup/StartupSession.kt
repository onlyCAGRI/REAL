package com.cagri.real.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StartupPhase { UNKNOWN, SIGNED_OUT, SIGNED_IN_LOADING, READY, ERROR_OFFLINE }

/** Why the first load failed, so the startup error screen can say it instead of a generic "check your connection". */
enum class StartupIssue { NO_NETWORK, TIMEOUT, SECURE_CONNECTION, SESSION, SERVER, UNKNOWN }

data class StartupState(
    val phase: StartupPhase = StartupPhase.UNKNOWN,
    val workspace: String? = null,
    val uid: String? = null,
    val generation: Long = 0,
    val revision: Long = 0,
    val offlineAllowed: Boolean = false,
    val issue: StartupIssue = StartupIssue.UNKNOWN,
    /** Short technical line ("HTTP 503", "SSLHandshakeException") shown small under the explanation. */
    val issueDetail: String? = null
) {
    fun showWelcome(onboardingCompleted: Boolean): Boolean =
        phase == StartupPhase.SIGNED_OUT && !onboardingCompleted

    fun blocksHome(localWorkspace: String, hasLocalCards: Boolean, loadedRevision: Long): Boolean {
        if (phase == StartupPhase.UNKNOWN || workspace != localWorkspace) return true
        if (hasLocalCards) return false
        return when (phase) {
            StartupPhase.SIGNED_OUT -> false
            StartupPhase.READY -> loadedRevision < revision
            StartupPhase.ERROR_OFFLINE -> !offlineAllowed
            else -> true
        }
    }
}

/** Process-session state only. Firebase observation supplies identity; no persisted login flags. */
class StartupSession {
    private val mutable = MutableStateFlow(StartupState())
    val state = mutable.asStateFlow()

    @Synchronized fun authResolved(uid: String?, workspace: String) {
        val old = mutable.value
        if (old.phase != StartupPhase.UNKNOWN && old.workspace == workspace && old.uid == uid) return
        mutable.value = StartupState(
            phase = if (uid == null) StartupPhase.SIGNED_OUT else StartupPhase.SIGNED_IN_LOADING,
            workspace = workspace, uid = uid, generation = old.generation + 1
        )
    }

    @Synchronized fun retry() {
        val old = mutable.value
        if (old.uid != null && old.phase == StartupPhase.ERROR_OFFLINE) {
            mutable.value = old.copy(phase = StartupPhase.SIGNED_IN_LOADING, offlineAllowed = false)
        }
    }

    @Synchronized fun completed(generation: Long) {
        val old = mutable.value
        if (old.generation != generation || old.uid == null || old.phase == StartupPhase.READY) return
        mutable.value = old.copy(phase = StartupPhase.READY, revision = old.revision + 1, offlineAllowed = false)
    }

    @Synchronized fun failed(generation: Long, issue: StartupIssue = StartupIssue.UNKNOWN, detail: String? = null) {
        val old = mutable.value
        if (old.generation != generation || old.uid == null || old.phase == StartupPhase.READY) return
        mutable.value = old.copy(phase = StartupPhase.ERROR_OFFLINE, issue = issue, issueDetail = detail)
    }

    @Synchronized fun allowOffline() {
        val old = mutable.value
        if (old.phase == StartupPhase.ERROR_OFFLINE) mutable.value = old.copy(offlineAllowed = true)
    }
}
