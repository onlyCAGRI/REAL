package com.cagri.real.platform.android.share

import com.cagri.real.i18n.t
import com.cagri.real.share.validatedPublicShareUrl
import com.cagri.real.network.HttpRequest
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.platform.android.sync.SyncLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Sharing uses the existing account-bound, Firebase + App Check authenticated transport. */
class CardShareClient {
    private val expectedUid = AndroidBackend.auth.currentUser()?.id
    data class Options(
        val includeAttachments: Boolean = true,
        val includeLocation: Boolean = false,
        val includeVoice: Boolean = false,
        val includeJournal: Boolean = false,
        val selectedIds: Set<String>? = null
    )
    data class Link(val id: String, val url: String)
    private suspend fun request(path: String, method: String = "POST", body: JSONObject = JSONObject()): JSONObject {
        check(expectedUid != null && AndroidBackend.auth.currentUser()?.id == expectedUid) { t("Kartı paylaşmak için giriş yap.") }
        val transport = AndroidBackend.syncTransport()
        val response = transport.execute(HttpRequest(
            AndroidBackend.ORIGIN + path, method,
            mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            body.toString(), 15_000, 60_000
        ))
        check(AndroidBackend.auth.currentUser()?.id == expectedUid) { t("Oturum açık hesap değişti. Lütfen yeniden paylaş.") }
        if (response.statusCode !in 200..299) {
            val code = runCatching { JSONObject(response.body).optString("error") }.getOrDefault("")
            throw IllegalStateException(when {
                response.statusCode == 423 -> t("Bulut verileri silindi veya siliniyor; bu hesabın bulut paylaşımı kapalı.")
                response.statusCode == 401 -> t("Kartı paylaşmak için yeniden giriş yap.")
                response.statusCode == 404 && path in setOf("/api/share/v1/shares", "/api/share/v1/profile", "/api/share/v1/inbox") -> t("Paylaşım şu an kullanılamıyor. Lütfen daha sonra tekrar dene.")
                response.statusCode == 404 -> t("Bu paylaşılan kart artık mevcut değil.")
                code == "SHARING_NOT_ENABLED" || response.statusCode == 503 -> t("Paylaşım şu an kullanılamıyor. Lütfen daha sonra tekrar dene.")
                code == "SYNC_CARD_FIRST" -> t("Bu kartın eşitlemesi henüz bitmedi. Lütfen tekrar dene.")
                code.contains("STORAGE_LIMIT") -> t("Bu kartı kopyalamak için bulut depolama alanı yetersiz.")
                response.statusCode == 400 -> {
                    val safeCode = code.takeIf { Regex("[A-Z][A-Z0-9_]{0,63}").matches(it) } ?: "INVALID_REQUEST"
                    val stage = when {
                        path.endsWith("/send-connection") -> "Send to connection"
                        path.endsWith("/send") -> "Send to email"
                        path == "/api/share/v1/shares" -> "Create share"
                        else -> "Share request"
                    }
                    "Share HTTP 400 · $safeCode · $stage"
                }
                response.statusCode == 429 -> t("Günlük paylaşım sınırına ulaşıldı. Lütfen yarın tekrar dene.")
                else -> t("Paylaşım tamamlanamadı. Lütfen tekrar dene.")
            })
        }
        return JSONObject(response.body)
    }
    suspend fun create(cardId: Long, options: Options, requestId: String = UUID.randomUUID().toString()): Link = withContext(Dispatchers.IO) {
        // Server snapshots its authenticated owner's current cloud card, never arbitrary client JSON.
        SyncLocal.syncForSharing(cardId)
        val body = JSONObject().put("id", requestId).put("cardKey", cardId.toString()).put("options", JSONObject()
            .put("includeAttachments", options.includeAttachments).put("includeLocation", options.includeLocation)
            .put("includeVoice", options.includeVoice).put("includeJournal", options.includeJournal)
            .apply { options.selectedIds?.let { put("selectedIds", JSONArray(it.toList())) } })
        val result = request("/api/share/v1/shares", body = body)
        val id = result.getString("id")
        check(id == requestId) { t("Paylaşım linki doğrulanamadı. Lütfen tekrar dene.") }
        val url = validatedPublicShareUrl(id, result.getString("url"))
            ?: error(t("Paylaşım linki doğrulanamadı. Lütfen tekrar dene."))
        Link(id, url)
    }
    suspend fun createCalendar(cardId: Long, version: Long, options: Options, requestId: String): Link = withContext(Dispatchers.IO) {
        // Server snapshots its authenticated owner's current cloud card, never arbitrary client JSON.
        SyncLocal.syncForSharing(cardId)
        val body = JSONObject().put("id", requestId).put("cardKey", cardId.toString()).put("options", JSONObject()
            .put("includeAttachments", options.includeAttachments).put("includeLocation", options.includeLocation)
            .put("includeVoice", options.includeVoice).put("includeJournal", options.includeJournal)
            .apply { options.selectedIds?.let { put("selectedIds", JSONArray(it.toList())) } })
        val result = request("/api/share/v1/calendar-shares", body = body.put("version", version))
        val id = result.getString("id")
        check(id == requestId) { t("Paylaşım linki doğrulanamadı. Lütfen tekrar dene.") }
        val url = validatedPublicShareUrl(id, result.getString("url"))
            ?: error(t("Paylaşım linki doğrulanamadı. Lütfen tekrar dene."))
        Link(id, url)
    }
    suspend fun send(id: String, email: String) { request("/api/share/v1/shares/$id/send", body = JSONObject().put("email", email.trim())) }
    suspend fun sendConnection(id: String, uid: String) { request("/api/share/v1/shares/$id/send-connection", body = JSONObject().put("recipientUid", uid)) }
    data class SharedLink(val id: String, val title: String)
    suspend fun links(): List<SharedLink> {
        val array = request("/api/share/v1/shares", "GET").getJSONArray("shares")
        return (0 until array.length()).map { array.getJSONObject(it).let { obj -> SharedLink(obj.getString("id"), obj.getString("title")) } }
    }
    suspend fun revoke(id: String) { request("/api/share/v1/shares/$id", "DELETE") }
    suspend fun claim(id: String): Long {
        require(com.cagri.real.share.isSharedCardId(id))
        val result = request("/api/share/v1/shares/$id/claim")
        SyncLocal.requestSync()
        return result.getString("cardId").toLong()
    }
    suspend fun profile(receiveEnabled: Boolean? = null): Boolean {
        val result = request("/api/share/v1/profile", body = JSONObject().apply { receiveEnabled?.let { put("receiveEnabled", it) } })
        return result.getBoolean("receiveEnabled")
    }
    suspend fun receive(): Int { profile(); return request("/api/share/v1/inbox").optInt("delivered", 0).coerceAtLeast(0) }
}
