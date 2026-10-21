package com.cagri.real.sync

import org.junit.Assert.*
import org.junit.Test

class SyncConflictChoiceTest {
    private fun conflict(remote: String? = "cloud") = PersistedSyncConflict("id", "CARD", "1", "base", "saved", "current", remote, 7, 3)
    @Test fun selectionCannotUseAStaleCloudRevisionOrLocalGeneration() {
        assertTrue(runCatching { conflictResolutionPayload(conflict(), 6, 3, SyncConflictChoice.REMOTE) }.isFailure)
        assertTrue(runCatching { conflictResolutionPayload(conflict(), 7, 2, SyncConflictChoice.CURRENT_LOCAL) }.isFailure)
    }
    @Test fun eachBranchRemainsExplicitAndNoAutomaticMergeOccurs() {
        assertEquals("current", conflictResolutionPayload(conflict(), 7, 3, SyncConflictChoice.CURRENT_LOCAL))
        assertEquals("saved", conflictResolutionPayload(conflict(), 7, 3, SyncConflictChoice.SAVED_LOCAL))
        assertEquals("cloud", conflictResolutionPayload(conflict(), 7, 3, SyncConflictChoice.REMOTE))
    }
    @Test fun permanentRemoteDeletionCannotBeUndoneWithEitherLocalBranch() {
        val deleted = conflict(null)
        assertTrue(runCatching { conflictResolutionPayload(deleted, 7, 3, SyncConflictChoice.CURRENT_LOCAL) }.isFailure)
        assertTrue(runCatching { conflictResolutionPayload(deleted, 7, 3, SyncConflictChoice.SAVED_LOCAL) }.isFailure)
        assertNull(conflictResolutionPayload(deleted, 7, 3, SyncConflictChoice.REMOTE))
    }
    @Test fun preservingLocalAsCopyAlwaysAppliesCloudBranchToOriginalIdentity() {
        assertNull(conflictResolutionPayload(conflict(null), 7, 3, SyncConflictChoice.LOCAL_AS_NEW_CARD))
        assertEquals("cloud", conflictResolutionPayload(conflict(), 7, 3, SyncConflictChoice.LOCAL_AS_NEW_CARD))
    }
    @Test fun copyingCannotInventANewNonCardOrMissingLocalPayload() {
        assertTrue(runCatching { conflictResolutionPayload(conflict().copy(kind = "CATEGORY"), 7, 3, SyncConflictChoice.LOCAL_AS_NEW_CARD) }.isFailure)
        assertTrue(runCatching { conflictResolutionPayload(conflict().copy(currentLocal = null), 7, 3, SyncConflictChoice.LOCAL_AS_NEW_CARD) }.isFailure)
    }
}
