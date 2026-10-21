package com.cagri.real.platform.android.connections

import android.content.Context
import com.cagri.real.connections.*
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One shared repository per active UID, consumed by Card Share and Pano. */
class ConnectionsRepository(
    private val api: ConnectionsGateway,
    private val store: ConnectionsCache,
    private val isCurrentAccount: () -> Boolean
) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(ConnectionsSnapshot())
    val state = mutable.asStateFlow()
    private var loaded = false
    private fun checkAccount() { check(isCurrentAccount()) { "Hesap değişti." } }
    suspend fun load() = mutex.withLock { withContext(Dispatchers.IO) {
        checkAccount()
        if (!loaded) { mutable.value = store.load(); loaded = true }
    } }
    suspend fun refresh() = mutex.withLock { withContext(Dispatchers.IO) {
        checkAccount()
        val next = api.list()
        checkAccount()
        store.save(next)
        mutable.value = next
        loaded = true
    } }
    suspend fun invite(email: String?): ConnectionInviteLink = mutex.withLock {
        val link = api.invite(email)
        withContext(Dispatchers.IO) {
            checkAccount()
            val next = mutable.value.copy(invitations = mutable.value.invitations + ConnectionInvitation(link.id, System.currentTimeMillis(), link.expiresAt))
            store.save(next); mutable.value = next
        }
        link
    }
    suspend fun revoke(id: String) = mutate({ api.revoke(id) }) { it.copy(invitations = it.invitations.filterNot { p -> p.id == id }) }
    suspend fun remove(person: RealConnection) = mutate({ api.remove(person.uid) }) { it.copy(people = it.people.filterNot { p -> p.uid == person.uid }) }
    suspend fun block(person: RealConnection) = mutate({ api.block(person.uid) }) { it.copy(people = it.people.filterNot { p -> p.uid == person.uid }, blocked = it.blocked.filterNot { p -> p.uid == person.uid } + BlockedConnection(person.uid, person.name)) }
    suspend fun unblock(uid: String) = mutate({ api.unblock(uid) }) { it.copy(blocked = it.blocked.filterNot { p -> p.uid == uid }) }
    private suspend fun mutate(action: suspend () -> Unit, change: (ConnectionsSnapshot) -> ConnectionsSnapshot) = mutex.withLock {
        action()
        withContext(Dispatchers.IO) { checkAccount(); val next = change(mutable.value); store.save(next); mutable.value = next }
    }
}

/** Composition root only; models, API, persistence and UI remain separate. */
object RealConnections {
    private var currentUid: String? = null
    private var current: ConnectionsRepository? = null
    @Synchronized fun repository(context: Context, uid: String): ConnectionsRepository {
        if (uid != currentUid || current == null) { currentUid = uid; current = ConnectionsRepository(ConnectionsApi(uid), ConnectionsStore(context.applicationContext, uid)) { AndroidBackend.auth.currentUser()?.id == uid } }
        return checkNotNull(current)
    }
}
