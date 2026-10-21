package com.cagri.real.sync

import kotlinx.coroutines.CancellationException

/** Canonical JSON is a wire value. No platform JSON, clock, file, or auth SDK here. */
data class SyncRecord(val id: String, val kind: String, val key: String,
    val payload: String?, val revision: Long, val sequence: Long = 0)
data class PendingRecord(val record: SyncRecord, val generation: Long)
data class SyncPage(val records: List<SyncRecord>, val cursor: Long, val hasMore: Boolean)
data class SyncConflict(val base: String?, val local: String?, val remote: String?)
sealed interface Reconciliation {
    data class Value(val payload: String?) : Reconciliation
    data class Conflict(val versions: SyncConflict) : Reconciliation
}

/** No wall clocks / last-write-wins. Arrays and rich blocks are deliberately atomic. */
fun reconcile(base: String?, local: String?, remote: String?): Reconciliation = when {
    local == remote -> Reconciliation.Value(local)
    local == base -> Reconciliation.Value(remote)
    remote == base -> Reconciliation.Value(local)
    else -> Reconciliation.Conflict(SyncConflict(base, local, remote))
}

interface SyncRemote {
    suspend fun changes(cursor: Long): SyncPage
    suspend fun get(id: String): SyncRecord
    suspend fun put(record: SyncRecord): SyncRecord
}
interface SyncStore {
    fun cursor(): Long
    /** Must persist merge/conflict, local projection and cursor in one transaction. */
    fun receive(record: SyncRecord, cursor: Long)
    fun checkpoint(cursor: Long, initialPullComplete: Boolean)
    fun pending(): List<PendingRecord>
    /** Latest durable snapshot; never replace a prepared upload with an unprepared edit. */
    fun current(id: String): SyncRecord? = null
    /** Acknowledge only this generation; a concurrent local edit stays pending. */
    fun acknowledge(sent: PendingRecord, accepted: SyncRecord)
    /** Must durably retain the complete remote record BEFORE a feed checkpoint can pass it. */
    fun deferDownload(record: SyncRecord, failure: SyncBinaryFailure) { throw failure }
    fun deferredDownloads(): List<SyncRecord> = emptyList()
    fun blockUpload(pending: PendingRecord, failure: SyncBinaryFailure) { throw failure }
    fun clearUploadIssue(id: String) {}
    /** The account deleted this card while it changed here: keep it as a conflict (the person chooses), never fail the run. */
    fun remoteDeleted(pending: PendingRecord) { throw SyncRemoteDeleted() }
    /** The account refused this record ([code]): park it with its reason until it changes again or the person chooses. */
    fun rejectUpload(pending: PendingRecord, failure: SyncRecordRejected) { throw failure }
}
interface SyncBinaries {
    suspend fun prepareUpload(record: SyncRecord)
    suspend fun prepareDownload(record: SyncRecord)
}
class SyncRevisionConflict : Exception()
/** The card's cloud resource is deleted (and the account would not restore it). */
class SyncRemoteDeleted : Exception("Cloud card is deleted")
/** A 4xx answer that belongs to this one record (invalid field, too large…); [code] is the worker's error code. */
class SyncRecordRejected(val status: Int, val code: String?) : Exception("Rejected $status ${code.orEmpty()}".trim())

/** One run per UID at a time is supplied by the platform scheduler. Pull before any push. */
class SyncEngine(private val remote: SyncRemote, private val store: SyncStore,
    private val binaries: SyncBinaries, private val ensureAccount: () -> Unit) {
    suspend fun run() {
        ensureAccount()
        val attemptedDownloads = mutableSetOf<String>()
        suspend fun restore(record: SyncRecord, cursor: Long) {
            ensureAccount()
            attemptedDownloads += record.id
            try {
                binaries.prepareDownload(record)
                ensureAccount()
                store.receive(record, cursor)
            } catch (failure: SyncBinaryFailure) {
                ensureAccount()
                // Persistence failures propagate: never skip data that was not durably queued.
                store.deferDownload(record, failure)
            }
        }
        var more: Boolean
        do {
            val requestedCursor = store.cursor()
            val page = remote.changes(requestedCursor)
            check(page.cursor >= requestedCursor && (!page.hasMore || page.cursor > requestedCursor)) {
                "Sync feed did not advance"
            }
            for (record in page.records) restore(record, record.sequence)
            ensureAccount()
            store.checkpoint(page.cursor, !page.hasMore)
            more = page.hasMore
        } while (more)
        // Latest feed versions win. No second attempt of a just-failed binary in this run.
        for (record in store.deferredDownloads().filterNot { it.id in attemptedDownloads }.take(20)) {
            restore(record, store.cursor()) // A deferred GET must not skip any feed records.
        }
        var firstFailure: Exception? = null
        for (pending in store.pending()) {
            ensureAccount()
            try {
                binaries.prepareUpload(pending.record)
                ensureAccount()
                // Send exactly the prepared snapshot. A concurrent local edit stays dirty.
                val accepted = remote.put(pending.record)
                ensureAccount()
                store.acknowledge(pending, accepted)
                store.clearUploadIssue(pending.record.id)
            } catch (failure: SyncBinaryFailure) {
                ensureAccount()
                store.blockUpload(pending, failure)
            } catch (deleted: SyncRemoteDeleted) {
                // One card, not the whole run: it waits as a conflict (apply the deletion / keep it as a new card).
                ensureAccount()
                store.remoteDeleted(pending)
            } catch (rejected: SyncRecordRejected) {
                ensureAccount()
                store.rejectUpload(pending, rejected)
            } catch (conflict: SyncRevisionConflict) {
                try {
                    restore(remote.get(pending.record.id), store.cursor())
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    ensureAccount()
                    if (firstFailure == null) firstFailure = failure
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                ensureAccount()
                if (firstFailure == null) firstFailure = failure
            }
        }
        firstFailure?.let { throw it }
    }
}

/** Conflict previews stay on device; payload branches are never logged. */
data class PersistedSyncConflict(val id:String,val kind:String,val key:String,val base:String?,
    val savedLocal:String?,val currentLocal:String?,val remote:String?,val revision:Long,val generation:Long)
enum class SyncConflictChoice { CURRENT_LOCAL, SAVED_LOCAL, REMOTE, LOCAL_AS_NEW_CARD }
data class SyncStatus(val restored:Boolean,val pending:Int,val conflicts:Int,val lastError:String?,val lastSuccessMillis:Long?)
