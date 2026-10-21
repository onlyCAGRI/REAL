package com.cagri.real.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SyncFeedProgressTest {
    private class Store : SyncStore {
        var at = 4L; var receives = 0; var checkpoints = 0
        override fun cursor() = at
        override fun receive(record: SyncRecord, cursor: Long) { receives++; at = cursor }
        override fun checkpoint(cursor: Long, initialPullComplete: Boolean) { checkpoints++; at = cursor }
        override fun pending() = emptyList<PendingRecord>()
        override fun acknowledge(sent: PendingRecord, accepted: SyncRecord) = error("unexpected")
    }
    private val binaries = object : SyncBinaries {
        override suspend fun prepareUpload(record: SyncRecord) = Unit
        override suspend fun prepareDownload(record: SyncRecord) = Unit
    }
    private suspend fun runPage(page: SyncPage, store: Store): Result<Unit> {
        var calls = 0
        val remote = object : SyncRemote {
            override suspend fun changes(cursor: Long): SyncPage { check(++calls == 1); return page }
            override suspend fun get(id: String) = error("unexpected")
            override suspend fun put(record: SyncRecord) = error("unexpected")
        }
        return runCatching { SyncEngine(remote, store, binaries, {}).run() }
    }
    @Test fun `same cursor with hasMore fails before applying or checkpointing`() = runBlocking {
        val store = Store()
        assertTrue(runPage(SyncPage(listOf(SyncRecord("id", "CARD", "1", "x", 1)), 4, true), store).isFailure)
        assertEquals(0, store.receives); assertEquals(0, store.checkpoints); assertEquals(4L, store.at)
    }
    @Test fun `backward terminal cursor cannot roll back durable state`() = runBlocking {
        val store = Store()
        assertTrue(runPage(SyncPage(emptyList(), 3, false), store).isFailure)
        assertEquals(0, store.checkpoints); assertEquals(4L, store.at)
    }
    @Test fun `empty terminal page at current cursor remains valid`() = runBlocking {
        val store = Store()
        assertTrue(runPage(SyncPage(emptyList(), 4, false), store).isSuccess)
        assertEquals(1, store.checkpoints); assertEquals(4L, store.at)
    }
}
