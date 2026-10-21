package com.cagri.real

import com.cagri.real.connections.*
import com.cagri.real.platform.android.connections.ConnectionsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConnectionsTest {
    private val token = "a".repeat(64)
    private val person = RealConnection("firebase-uid", "REAL name", 1, 2, 2)
    private class Cache(var snapshot: ConnectionsSnapshot) : ConnectionsCache {
        var writes = 0
        override fun load() = snapshot
        override fun save(snapshot: ConnectionsSnapshot) { this.snapshot = snapshot; writes++ }
    }
    private class Api(var snapshot: ConnectionsSnapshot = ConnectionsSnapshot()) : ConnectionsGateway {
        var offline = false
        var afterList: () -> Unit = {}
        private fun available() { check(!offline) { "offline" } }
        override suspend fun list(): ConnectionsSnapshot { available(); afterList(); return snapshot }
        override suspend fun invite(email: String?): ConnectionInviteLink { available(); return ConnectionInviteLink("id", "https://share.test/c/${"b".repeat(64)}", 10) }
        override suspend fun revoke(id: String) { available() }
        override suspend fun remove(person: String) { available() }
        override suspend fun block(person: String) { available() }
        override suspend fun unblock(person: String) { available() }
    }
    @Test fun strictConnectionLinkIsSeparateFromCardShare() {
        assertEquals(token, connectionTokenFromLink("real://connection/$token"))
        for (url in listOf("real://shared/$token", "real://connection/$token?x=1", "real://connection/$token#x", "real://user@connection/$token", "real://connection:1/$token", "real://connection/${token.drop(1)}", "real://connection/%61${token.drop(1)}")) {
            assertNull(url, connectionTokenFromLink(url))
        }
    }
    @Test fun publicInviteRequiresExactHttpsCapabilityPath() {
        assertEquals("https://share.test/c/$token", validatedConnectionInviteUrl("https://share.test/c/$token"))
        for (url in listOf("http://share.test/c/$token", "https://share.test/s/$token", "https://user@share.test/c/$token", "https://share.test/c/$token?email=private", "https://share.test:123/c/$token", "https://share.test/c/${token.uppercase()}")) {
            assertNull(url, validatedConnectionInviteUrl(url))
        }
    }
    @Test fun offlineRefreshKeepsCachedPeopleAndDoesNotWriteEmptyState() = runBlocking {
        val cache = Cache(ConnectionsSnapshot(people = listOf(person)))
        val api = Api().apply { offline = true }
        val repo = ConnectionsRepository(api, cache) { true }
        repo.load()
        assertEquals(listOf(person), repo.state.value.people)
        assertTrue(runCatching { repo.refresh() }.isFailure)
        assertEquals(listOf(person), repo.state.value.people)
        assertEquals(0, cache.writes)
    }
    @Test fun failedRemoveOrBlockDoesNotSilentlyLoseOfflineConnection() = runBlocking {
        val cache = Cache(ConnectionsSnapshot(people = listOf(person)))
        val api = Api().apply { offline = true }
        val repo = ConnectionsRepository(api, cache) { true }; repo.load()
        assertTrue(runCatching { repo.remove(person) }.isFailure)
        assertTrue(runCatching { repo.block(person) }.isFailure)
        assertEquals(listOf(person), repo.state.value.people)
        assertEquals(0, cache.writes)
    }
    @Test fun accountSwitchRejectsLateResponseAndPreservesOldAccountCache() = runBlocking {
        var current = true
        val cache = Cache(ConnectionsSnapshot(people = listOf(person)))
        val api = Api().apply { afterList = { current = false } }
        val repo = ConnectionsRepository(api, cache) { current }; repo.load()
        assertTrue(runCatching { repo.refresh() }.isFailure)
        assertEquals(listOf(person), cache.snapshot.people)
        assertEquals(0, cache.writes)
    }
    @Test fun independentRepositoriesDoNotMixAccounts() = runBlocking {
        val a = ConnectionsRepository(Api(), Cache(ConnectionsSnapshot(people = listOf(person)))) { true }
        val b = ConnectionsRepository(Api(), Cache(ConnectionsSnapshot())) { true }
        a.load(); b.load()
        assertEquals(listOf(person), a.state.value.people)
        assertTrue(b.state.value.people.isEmpty())
    }
    @Test fun blockRemovesPersonButUnblockRequiresNewConnection() = runBlocking {
        val repo = ConnectionsRepository(Api(), Cache(ConnectionsSnapshot(people = listOf(person)))) { true }
        repo.load(); repo.block(person)
        assertTrue(repo.state.value.people.isEmpty())
        assertEquals(person.uid, repo.state.value.blocked.single().uid)
        repo.unblock(person.uid)
        assertTrue(repo.state.value.people.isEmpty()); assertTrue(repo.state.value.blocked.isEmpty())
    }
}
