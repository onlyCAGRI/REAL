package com.cagri.real.platform.android.access

import com.cagri.real.access.RealFeature
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProRequired : IllegalStateException("Bu özellik REAL Pro ile kullanılabilir.")
/** No transcript, card data or pending mutation is retained by the paywall. */
object ProAccess {
    data class Prompt(val uid: String?, val feature: RealFeature)
    private val mutablePrompt = MutableStateFlow<Prompt?>(null)
    val prompt = mutablePrompt.asStateFlow()
    private val lock = Mutex()
    fun dismiss() { mutablePrompt.value = null }
    suspend fun allowed(feature: RealFeature, showPrompt: Boolean = true): Boolean = lock.withLock {
        val uid = AndroidBackend.auth.currentUser()?.id
        if (uid != null) {
            try {
                val access = RealAccessRepository.cached(uid) ?: RealAccessRepository.refresh(uid)
                if (AndroidBackend.auth.currentUser()?.id != uid) return@withLock false
                if (access.snapshot.allows(feature)) return@withLock true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Fail closed, but existing content remains readable. */ }
        }
        if (showPrompt && AndroidBackend.auth.currentUser()?.id == uid) mutablePrompt.value = Prompt(uid, feature)
        false
    }
    suspend fun requirePath(path: String) {
        val feature = when (path) {
            "/api/voice-intent", "/api/ai-diagnostic" -> RealFeature.VoiceAi
            "/api/mood-suggestions" -> RealFeature.MoodAi
            "/api/media-recommendations" -> RealFeature.MediaDiscovery
            "/api/game-recommendations" -> RealFeature.GameDiscovery
            else -> return
        }
        if (!allowed(feature)) throw ProRequired()
    }
}
