package com.cagri.real.platform.android.connections

import com.cagri.real.connections.*
import com.cagri.real.network.HttpRequest
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** Uses the existing account-bound Firebase + App Check transport. No directory lookup. */
class ConnectionsApi(private val uid: String) : ConnectionsGateway {
    private suspend fun request(path: String, method: String, body: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        check(AndroidBackend.auth.currentUser()?.id == uid) { "Hesap değişti. Yeniden açın." }
        val response = AndroidBackend.syncTransport().execute(HttpRequest(
            AndroidBackend.ORIGIN + "/api/connections/v1/" + path, method,
            mapOf("Content-Type" to "application/json", "Accept" to "application/json"), body.toString(), 15_000, 30_000))
        check(AndroidBackend.auth.currentUser()?.id == uid) { "Hesap değişti. Yeniden açın." }
        check(response.statusCode in 200..299) { when (response.statusCode) {
            401, 403 -> "REAL hesabınızla giriş yapın. Email daveti için doğrulanmış email gerekir."
            404 -> "Davet veya bağlantı artık kullanılamıyor."
            429 -> "Çok fazla deneme. Daha sonra tekrar deneyin."
            else -> "Bağlantılar şu anda güncellenemiyor. İnternet bağlantısını kontrol edin."
        } }
        JSONObject(response.body)
    }
    override suspend fun list(): ConnectionsSnapshot = ConnectionsCodec.decode(request("me", "GET").toString())
    override suspend fun invite(email: String?): ConnectionInviteLink {
        val obj = request("invites", "POST", JSONObject().apply { email?.trim()?.takeIf { it.isNotEmpty() }?.let { put("email", it) } })
        return ConnectionInviteLink(obj.getString("id"), validatedConnectionInviteUrl(obj.getString("url"))
            ?: error("Davet linki doğrulanamadı."), obj.getLong("expiresAt"))
    }
    suspend fun preview(token: String): String = request("open/$token", "GET").getString("name")
    suspend fun accept(token: String) { request("open/$token/accept", "POST") }
    override suspend fun revoke(id: String) { request("invites/$id", "DELETE") }
    override suspend fun remove(person: String) { request("people/${encode(person)}", "DELETE") }
    override suspend fun block(person: String) { request("people/${encode(person)}/block", "POST") }
    override suspend fun unblock(person: String) { request("people/${encode(person)}/block", "DELETE") }
    /** This account's picture for connected people / category co-members ("avatar" or "photo", base64 JPEG). */
    suspend fun putPicture(kind: String, base64: String) { request("me/picture", "PUT", JSONObject().put("kind", kind).put("image", base64)) }
    suspend fun deletePicture() { request("me/picture", "DELETE") }
    /** Another person's picture as base64 (only when connected or sharing a category), or null when they have none. */
    suspend fun picture(person: String): Pair<Long, String>? = runCatching { request("people/${encode(person)}/picture", "GET") }
        .getOrNull()?.let { it.getLong("version") to it.getString("image") }
    private fun encode(uid: String) = URLEncoder.encode(uid, "UTF-8").replace("+", "%20")
}
