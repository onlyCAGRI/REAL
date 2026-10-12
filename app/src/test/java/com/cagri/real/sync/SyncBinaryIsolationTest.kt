package com.cagri.real.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SyncBinaryIsolationTest {
    private val broken = SyncRecord("broken", "CARD", "1", "complete remote payload", 1, 1)
    private val good = SyncRecord("good", "CARD", "2", "good", 1, 2)
    /** Reused durable state survives construction of a different Store/Engine. */
    private class State {
        var cursor = 0L
        var restored = false
        val deferred = linkedMapOf<String, Pair<SyncRecord, SyncBinaryFailure>>()
        val blocked = mutableMapOf<String, Pair<Long, SyncBinaryFailure>>()
        val received = mutableListOf<SyncRecord>()
        val pending = mutableListOf<PendingRecord>()
    }
    private class Store(val state: State) : SyncStore {
        var failJournal = false
        override fun cursor() = state.cursor
        override fun checkpoint(cursor: Long, initialPullComplete: Boolean) { state.cursor = cursor; state.restored = initialPullComplete }
        override fun receive(record: SyncRecord, cursor: Long) {
            state.received += record; state.deferred.remove(record.id); state.cursor = maxOf(state.cursor, cursor)
        }
        override fun pending() = state.pending.filter { row ->
            row.record.id !in state.deferred && state.blocked[row.record.id]?.let { it.first == row.generation && !it.second.automaticRetry } != true
        }
        override fun acknowledge(sent: PendingRecord, accepted: SyncRecord) { state.pending.remove(sent) }
        override fun deferDownload(record: SyncRecord, failure: SyncBinaryFailure) {
            check(!failJournal) { "disk full" }
            state.deferred[record.id] = record to failure
        }
        override fun deferredDownloads() = state.deferred.values.filter { it.second.automaticRetry }.map { it.first }
        override fun blockUpload(pending: PendingRecord, failure: SyncBinaryFailure) { state.blocked[pending.record.id] = pending.generation to failure }
        override fun clearUploadIssue(id: String) { state.blocked.remove(id) }
    }
    private class Remote(var feed: List<SyncRecord>) : SyncRemote {
        val sent = mutableListOf<SyncRecord>()
        var conflict = false
        var getResult: SyncRecord? = null
        override suspend fun changes(cursor: Long) = SyncPage(feed.filter { it.sequence > cursor }, maxOf(cursor, feed.maxOfOrNull { it.sequence } ?: 0), false)
        override suspend fun get(id: String) = checkNotNull(getResult)
        override suspend fun put(record: SyncRecord): SyncRecord {
            if (conflict) throw SyncRevisionConflict()
            sent += record
            return record.copy(revision = record.revision + 1)
        }
    }
    private class Binaries : SyncBinaries {
        val downloadErrors = mutableMapOf<String, Exception>()
        val uploadErrors = mutableMapOf<String, Exception>()
        val downloads = mutableListOf<String>()
        val uploads = mutableListOf<String>()
        var editDuringUpload: (() -> Unit)? = null
        override suspend fun prepareDownload(record: SyncRecord) {
            downloads += record.id; downloadErrors[record.id]?.let { throw it }
        }
        override suspend fun prepareUpload(record: SyncRecord) {
            uploads += record.id; editDuringUpload?.invoke(); uploadErrors[record.id]?.let { throw it }
        }
    }
    private fun failure(auto: Boolean = true) = SyncBinaryFailure("blob", SyncBinaryCode.IO_FAILURE, auto)

    @Test fun failedDownloadIsDurableBeforeCursorMovesAndDoesNotBlockHealthyCardsOrPush() = runBlocking {
        val state = State(); val store = Store(state); val remote = Remote(listOf(broken, good)); val files = Binaries()
        files.downloadErrors[broken.id] = failure()
        state.pending += PendingRecord(SyncRecord("local", "CARD", "3", "local", 0), 1)
        SyncEngine(remote, store, files, {}).run()
        assertEquals(2L, state.cursor); assertTrue(state.restored)
        assertEquals(listOf(good), state.received)
        assertEquals(broken, state.deferred[broken.id]?.first)
        assertEquals(listOf("local"), remote.sent.map { it.id })
        assertEquals(1, files.downloads.count { it == broken.id })
    }
    @Test fun deferredRestoreSurvivesAnotherStoreAndNeverMovesFeedCursorToGetSequence() = runBlocking {
        val state = State(); state.cursor = 4; state.deferred[broken.id] = broken.copy(sequence = 99) to failure()
        SyncEngine(Remote(emptyList()), Store(state), Binaries(), {}).run()
        assertEquals(4L, state.cursor); assertTrue(state.deferred.isEmpty())
        assertEquals(99L, state.received.single().sequence)
    }
    @Test fun newerTombstoneClearsDeferredWithoutResurrectingOlderPayload() = runBlocking {
        val state = State(); state.cursor = 1; state.deferred[broken.id] = broken to failure(false)
        val deletion = broken.copy(payload = null, revision = 2, sequence = 2)
        SyncEngine(Remote(listOf(deletion)), Store(state), Binaries(), {}).run()
        assertTrue(state.deferred.isEmpty()); assertNull(state.received.single().payload)
    }
    @Test fun terminalDownloadWaitsForExplicitRetry() = runBlocking {
        val state = State(); state.cursor = 1; state.deferred[broken.id] = broken to failure(false)
        val files = Binaries()
        SyncEngine(Remote(emptyList()), Store(state), files, {}).run()
        assertTrue(files.downloads.isEmpty()); assertTrue(state.deferred.isNotEmpty())
        state.deferred[broken.id] = broken to failure(true) // Explicit UI retry journal update.
        SyncEngine(Remote(emptyList()), Store(state), files, {}).run()
        assertEquals(listOf(broken.id), files.downloads); assertTrue(state.deferred.isEmpty())
    }
    @Test fun journalWriteFailureMustNotAdvanceCursorOrSendLocalCards() = runBlocking {
        val state = State(); val store = Store(state).apply { failJournal = true }; val remote = Remote(listOf(broken))
        val files = Binaries().apply { downloadErrors[broken.id] = failure() }
        assertTrue(runCatching { SyncEngine(remote, store, files, {}).run() }.isFailure)
        assertEquals(0L, state.cursor); assertFalse(state.restored); assertTrue(remote.sent.isEmpty())
    }
    @Test fun terminalUploadRetainsCompletePayloadAndOtherCardsContinueThenManualRetryWorks() = runBlocking {
        val state = State(); val remote = Remote(emptyList()); val files = Binaries()
        val first = PendingRecord(broken.copy(revision = 0), 1); val second = PendingRecord(good.copy(revision = 0), 1)
        state.pending += listOf(first, second); files.uploadErrors[broken.id] = failure(false)
        SyncEngine(remote, Store(state), files, {}).run()
        assertEquals(listOf(first), state.pending); assertEquals(first.record.payload, state.pending.single().record.payload)
        assertEquals(listOf(good.id), remote.sent.map { it.id })
        SyncEngine(remote, Store(state), files, {}).run()
        assertEquals(1, files.uploads.count { it == broken.id })
        state.blocked.remove(broken.id); files.uploadErrors.clear()
        SyncEngine(remote, Store(state), files, {}).run()
        assertTrue(state.pending.isEmpty()); assertEquals(listOf(good.id, broken.id), remote.sent.map { it.id })
    }
    @Test fun pendingUploadWithUnresolvedRemoteDownloadCannotPushAnOldRevision() = runBlocking {
        val state = State(); state.cursor = 1
        state.pending += PendingRecord(broken.copy(payload = "offline edit", revision = 0), 1)
        state.deferred[broken.id] = broken to failure(false)
        val remote = Remote(emptyList())
        SyncEngine(remote, Store(state), Binaries(), {}).run()
        assertTrue(remote.sent.isEmpty()); assertEquals("offline edit", state.pending.single().record.payload)
    }
    @Test fun revisionConflictDownloadIsDeferredAtCurrentFeedCursor() = runBlocking {
        val state = State(); state.cursor = 5; state.pending += PendingRecord(broken.copy(revision = 0), 1)
        val remote = Remote(emptyList()).apply { conflict = true; getResult = broken.copy(revision = 2, sequence = 99) }
        val files = Binaries().apply { downloadErrors[broken.id] = failure(false) }
        SyncEngine(remote, Store(state), files, {}).run()
        assertEquals(5L, state.cursor); assertEquals(2L, state.deferred[broken.id]?.first?.revision)
    }
    @Test fun neverSendAConcurrentEditThatHasNotHadItsBinariesPrepared() = runBlocking {
        val state = State(); val first = PendingRecord(broken.copy(payload = "prepared", revision = 0), 1)
        state.pending += first
        val remote = Remote(emptyList()); val files = Binaries().apply {
            editDuringUpload = { state.pending.clear(); state.pending += first.copy(record = first.record.copy(payload = "new unprepared file"), generation = 2) }
        }
        SyncEngine(remote, Store(state), files, {}).run()
        assertEquals("prepared", remote.sent.single().payload)
        assertEquals("new unprepared file", state.pending.single().record.payload)
    }
    @Test fun authFailuresCancellationAndAccountSwitchCannotBeDowngradedToBinaryIssues() = runBlocking {
        for (error in listOf(IllegalStateException("Auth barrier"), CancellationException("cancel"))) {
            val state = State(); val files = Binaries().apply { downloadErrors[broken.id] = error }
            assertTrue(runCatching { SyncEngine(Remote(listOf(broken)), Store(state), files, {}).run() }.isFailure)
            assertEquals(0L, state.cursor); assertTrue(state.deferred.isEmpty())
        }
        val state = State(); var guards = 0
        val files = Binaries().apply { downloadErrors[broken.id] = failure() }
        assertTrue(runCatching { SyncEngine(Remote(listOf(broken)), Store(state), files, { check(++guards < 3) }).run() }.isFailure)
        assertTrue(state.deferred.isEmpty())
    }
    @Test fun legacyStoreWithoutDurableJournalFailsClosed() = runBlocking {
        val store = object : SyncStore {
            var cursor = 0L
            override fun cursor() = cursor
            override fun receive(record: SyncRecord, cursor: Long) { this.cursor = cursor }
            override fun checkpoint(cursor: Long, initialPullComplete: Boolean) { this.cursor = cursor }
            override fun pending() = emptyList<PendingRecord>()
            override fun acknowledge(sent: PendingRecord, accepted: SyncRecord) {}
        }
        val files = Binaries().apply { downloadErrors[broken.id] = failure() }
        assertTrue(runCatching { SyncEngine(Remote(listOf(broken)), store, files, {}).run() }.isFailure)
        assertEquals(0L, store.cursor)
    }
}
