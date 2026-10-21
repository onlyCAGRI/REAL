package com.cagri.real.platform.android.network

import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.speech.GeminiVoiceIntentClient
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/** Cancelling the screen disconnects in-flight probes; no background polling/job remains. */
internal suspend fun diagnosticPost(path: String, payload: JSONObject): JSONObject = coroutineScope {
    val parentScope = this
    suspendCancellableCoroutine { continuation ->
        val connectionRef = AtomicReference<HttpURLConnection?>(null)
        val job = parentScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val uid=checkNotNull(AndroidBackend.auth.currentUser()?.id)
                val feature = com.cagri.real.ai.aiFeatureForPath(path)
                com.cagri.real.platform.android.access.ProAccess.requirePath(path)
                feature?.let { com.cagri.real.platform.android.ai.AndroidAiConsent.require(it) }
                val authHeaders = AndroidBackend.diagnosticHeaders(uid)
                val base = GeminiVoiceIntentClient.ENDPOINT.substringBefore("/api/")
                connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 15000
                    instanceFollowRedirects = false
                    doOutput = true
                    authHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connectionRef.set(connection)
                if (!continuation.isActive) return@launch
                check(AndroidBackend.auth.currentUser()?.id == uid) { "Hesap değişti." }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status !in 200..299) error(when (status) {
                    401, 403 -> "Worker erişim anahtarı reddedildi ($status)"
                    404 -> "Tanılama endpoint’i yok; yeni Worker kodunu yükle"
                    else -> "Worker HTTP $status"
                })
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                check(AndroidBackend.auth.currentUser()?.id == uid) { "Hesap değişti." }
                if (feature != null) check(com.cagri.real.platform.android.ai.AndroidAiConsent.coordinator.allowed(uid, feature)) { "AI onayı kapatıldı; yanıt uygulanmadı." }
                val value = JSONObject(text)
                if (continuation.isActive) continuation.resumeWith(Result.success(value))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val safe = if (error is SocketTimeoutException) Exception("Bağlantı / yanıt süresi doldu")
                    else if (error is IllegalStateException || error is com.cagri.real.ai.AiConsentDeclined) error else Exception("Bağlantı kurulamadı veya geçersiz yanıt")
                if (continuation.isActive) continuation.resumeWith(Result.failure(safe))
            } finally {
                connectionRef.getAndSet(null)?.disconnect()
                connection?.disconnect()
            }
        }
        continuation.invokeOnCancellation {
            connectionRef.getAndSet(null)?.disconnect()
            job.cancel()
        }
    }
}
