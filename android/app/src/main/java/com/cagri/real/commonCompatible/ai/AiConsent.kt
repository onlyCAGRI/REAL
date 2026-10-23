package com.cagri.real.ai

import com.cagri.real.i18n.t
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AiFeature { VOICE, MOOD, DISCOVERY, DIAGNOSTIC }
const val AI_DISCLOSURE_VERSION = 4
interface AiConsentStore {
    suspend fun allowed(uid: String, feature: AiFeature): Boolean
    suspend fun save(uid: String, feature: AiFeature, allowed: Boolean)
}
data class AiConsentPrompt(val id: Long, val uid: String, val feature: AiFeature)
class AiConsentDeclined : Exception(t("AI için veri gönderilmedi. İstersen daha sonra yeniden deneyebilirsin."))

/** Serializes requests without timers. Cancellation/dismissal never counts as agreement. */
class AiConsentCoordinator(private val store: AiConsentStore) {
    private val lock = Mutex()
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision
    private val mutablePrompt = MutableStateFlow<AiConsentPrompt?>(null)
    val prompt: StateFlow<AiConsentPrompt?> = mutablePrompt
    @Volatile private var hostActive = false
    @Volatile private var answer: CompletableDeferred<Boolean>? = null
    private var nextId = 0L
    private val declines = MutableStateFlow<Map<Pair<String,AiFeature>,Long>>(emptyMap())
    fun setHostActive(active: Boolean) { hostActive = active; if (!active) answer?.complete(false) }
    fun decide(id: Long, allow: Boolean) { if (mutablePrompt.value?.id == id) answer?.complete(allow) }
    fun cancelPrompt() { answer?.complete(false) }
    suspend fun requireConsent(uid: String, feature: AiFeature, ensureAccount: () -> Unit) {
        val key=uid to feature
        val before=declines.value[key] ?: 0L
        lock.withLock {
        if ((declines.value[key] ?: 0L)!=before) throw AiConsentDeclined()

        ensureAccount()
        if (store.allowed(uid, feature)) return@withLock
        check(hostActive) { t("AI açıklamasını uygulama açıkken onayla.") }
        val pending = AiConsentPrompt(++nextId, uid, feature)
        val deferred = CompletableDeferred<Boolean>()
        answer = deferred; mutablePrompt.value = pending
        // Close the race with a host stopping between the check and publication.
        if (!hostActive) deferred.complete(false)
        try {
            if (!deferred.await()) {
                declines.value=declines.value + (key to (before+1))
                throw AiConsentDeclined()
            }
            ensureAccount()
            store.save(uid, feature, true) // Durable write must succeed before any transmission.
            mutableRevision.value++
            ensureAccount()
        } finally {
            mutablePrompt.value = null; answer = null
        }
        }
    }
    suspend fun revoke(uid: String, feature: AiFeature) {
        if (mutablePrompt.value?.let { it.uid == uid && it.feature == feature } == true) cancelPrompt()
        lock.withLock {
            val key=uid to feature
            declines.value=declines.value + (key to ((declines.value[key] ?: 0L)+1))
            store.save(uid, feature, false)
            mutableRevision.value++
        }
    }
    suspend fun allowed(uid: String, feature: AiFeature) = store.allowed(uid, feature)
}
