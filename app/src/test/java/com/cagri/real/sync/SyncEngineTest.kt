package com.cagri.real.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SyncEngineTest {
    @Test fun threeWayPreservesConcurrentEditAndDelete() {
        assertEquals(Reconciliation.Value("B"),reconcile("A","A","B"))
        assertEquals(Reconciliation.Value("B"),reconcile("A","B","A"))
        assertEquals(Reconciliation.Value("B"),reconcile("A","B","B"))
        assertEquals(Reconciliation.Value(null),reconcile("A","A",null))
        assertTrue(reconcile("A","offline edit",null) is Reconciliation.Conflict)
        assertTrue(reconcile("A","phone A","phone B") is Reconciliation.Conflict)
        assertEquals(Reconciliation.Value("same"),reconcile(null,"same","same"))
    }
    private class Store:SyncStore {
        var at=0L;var restored=false
        var local=SyncRecord("card","CARD","1","local",0)
        var generation=1L;var dirty=true
        val received=mutableListOf<SyncRecord>()
        override fun cursor()=at
        override fun receive(record:SyncRecord,cursor:Long) {received+=record;at=cursor}
        override fun checkpoint(cursor:Long,initialPullComplete:Boolean) {at=cursor;restored=initialPullComplete}
        override fun pending()=if(restored&&dirty)listOf(PendingRecord(local,generation))else emptyList()
        override fun acknowledge(sent:PendingRecord,accepted:SyncRecord) {dirty=local.payload!=accepted.payload;local=local.copy(revision=accepted.revision)}
    }
    @Test fun allRestorePagesBeforeAnyPushAndInterruptedRestoreNeverPushes()=runBlocking {
        val store=Store();val order=mutableListOf<String>()
        val remote=object:SyncRemote {
            override suspend fun changes(cursor:Long):SyncPage {order+="pull$cursor";return if(cursor==0L)SyncPage(listOf(SyncRecord("other","MOOD","2","remote",1,7)),7,true)else SyncPage(emptyList(),7,false)}
            override suspend fun get(id:String)=error("unexpected")
            override suspend fun put(record:SyncRecord):SyncRecord {order+="push";assertTrue(store.restored);return record.copy(revision=1)}
        }
        val binaries=object:SyncBinaries {
            override suspend fun prepareDownload(record:SyncRecord) {order+="binary"}
            override suspend fun prepareUpload(record:SyncRecord) {order+="upload"}
        }
        SyncEngine(remote,store,binaries,{}).run()
        assertEquals(listOf("pull0","binary","pull7","upload","push"),order)
        assertFalse(store.dirty)
        val interrupted=Store()
        val fail=object:SyncBinaries {
            override suspend fun prepareDownload(record:SyncRecord) {error("download failed")}
            override suspend fun prepareUpload(record:SyncRecord) {error("must not upload")}
        }
        assertTrue(runCatching { SyncEngine(remote,interrupted,fail,{}).run() }.isFailure)
        assertFalse(interrupted.restored);assertEquals(0L,interrupted.at);assertTrue(interrupted.dirty)
    }
    @Test fun accountSwitchAndConcurrentLocalEditCannotClearPending()=runBlocking {
        val store=Store();var current="A"
        val remote=object:SyncRemote {
            override suspend fun changes(cursor:Long)=SyncPage(emptyList(),cursor,false)
            override suspend fun get(id:String)=error("unexpected")
            override suspend fun put(record:SyncRecord):SyncRecord {
                store.local=store.local.copy(payload="new edit during HTTP");store.generation++
                return record.copy(revision=1)
            }
        }
        val binaries=object:SyncBinaries {
            override suspend fun prepareDownload(record:SyncRecord) {}
            override suspend fun prepareUpload(record:SyncRecord) {}
        }
        SyncEngine(remote,store,binaries,{check(current=="A")}).run()
        assertTrue(store.dirty);assertEquals("new edit during HTTP",store.local.payload)
        current="B"
        assertTrue(runCatching { SyncEngine(remote,store,binaries,{check(current=="A")}).run() }.isFailure)
    }
    @Test fun revisionConflictGetDoesNotSkipChangeFeed()=runBlocking {
        val store=Store()
        val remote=object:SyncRemote {
            override suspend fun changes(cursor:Long)=SyncPage(emptyList(),11,false)
            override suspend fun get(id:String)=SyncRecord(id,"CARD","1","remote",2,99)
            override suspend fun put(record:SyncRecord):SyncRecord=throw SyncRevisionConflict()
        }
        val binaries=object:SyncBinaries {
            override suspend fun prepareDownload(record:SyncRecord) {}
            override suspend fun prepareUpload(record:SyncRecord) {}
        }
        SyncEngine(remote,store,binaries,{}).run()
        assertEquals(11L,store.at);assertEquals("remote",store.received.single().payload);assertTrue(store.dirty)
    }
}
