package com.cagri.real.platform.android.sync

import com.cagri.real.sync.PersistedSyncConflict
import com.cagri.real.sync.SyncConflictChoice

/** Small account-bound UI facade; storage, auth and scheduling remain in SyncLocal. */
class AndroidSyncHealthRepository(private val uid: String) {
    val snapshots = SyncLocal.health
    suspend fun refresh() = SyncLocal.readSyncHealth(uid)
    suspend fun retry(token: String) = SyncLocal.retrySyncIssue(uid, token)
    suspend fun choose(conflict: PersistedSyncConflict, choice: SyncConflictChoice) = SyncLocal.chooseSyncConflict(uid, conflict, choice)
}
