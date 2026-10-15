package com.cagri.real.sharedcategories

import com.cagri.real.network.HttpRequest
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One page of a shared category's change feed. */
internal data class SharedCardsPage(val cards: List<JSONObject>, val cursor: Long, val hasMore: Boolean, val access: String)

/** An HTTP answer the caller handles itself (409 / 410 carry the current card). */
internal class SharedCardHttp(val status: Int, val body: JSONObject)

internal data class SharedCategorySummary(val id: String, val category: JSONObject, val isOwner: Boolean, val collab: Boolean,
    val access: String, val ownerName: String?)

data class SharedMember(val uid: String, val name: String, val isOwner: Boolean, val access: String)

/** Ortak kategori card store client: /api/categories/v1/shared/{id}/cards, adopt, assets, access, release. */
internal class SharedCategoryCardsApi(val uid: String) {
    private val base = AndroidBackend.ORIGIN + "/api/categories/v1/"
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private fun checkAccount() { check(AndroidBackend.auth.currentUser()?.id == uid) { "Hesap değişti." } }

    private suspend fun call(path: String, method: String = "GET", body: JSONObject? = null): SharedCardHttp = withContext(Dispatchers.IO) {
        checkAccount()
        val response = AndroidBackend.syncTransport().execute(HttpRequest(base + path, method,
            mapOf("Content-Type" to "application/json", "Accept" to "application/json"), (body ?: JSONObject()).toString(), 15_000, 30_000))
        checkAccount()
        SharedCardHttp(response.statusCode, runCatching { JSONObject(response.body) }.getOrDefault(JSONObject()))
    }
    private suspend fun ok(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val r = call(path, method, body)
        check(r.status in 200..299) { failure(r.status, r.body.optString("error")) }
        return r.body
    }

    suspend fun list(): List<SharedCategorySummary> {
        val out = mutableListOf<SharedCategorySummary>()
        var after: String? = null
        val seen = mutableSetOf<String>()
        do {
            val page = ok("shared" + (after?.let { "?after=$it" } ?: ""))
            val items = page.getJSONArray("items")
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                out += SharedCategorySummary(o.getString("id"), o.getJSONObject("category"), o.getBoolean("isOwner"),
                    o.optBoolean("collab"), o.optString("access").ifBlank { if (o.getBoolean("isOwner")) "owner" else "full" }, null)
            }
            after = if (page.isNull("next")) null else page.getString("next")
            check(after == null || (uuid.matches(after!!) && seen.add(after!!))) { "Kategori listesi okunamadı." }
        } while (after != null)
        return out
    }
    suspend fun members(id: String): List<SharedMember> {
        val m = ok("shared/${checkId(id)}").getJSONArray("members")
        return (0 until m.length()).map { i -> val p = m.getJSONObject(i)
            SharedMember(p.getString("uid"), p.optString("name", "REAL user"), p.optBoolean("isOwner"),
                if (p.optBoolean("isOwner")) "owner" else p.optString("access", "full")) }
    }
    suspend fun collab(id: String) { ok("shared/${checkId(id)}/collab", "POST") }
    /** (owner's personal card key, shared card) pairs, and the cursor for the next page. */
    suspend fun adopt(id: String, after: String?): Pair<List<Pair<String, JSONObject>>, String?> {
        val r = ok("shared/${checkId(id)}/adopt", "POST", JSONObject().apply { after?.let { put("after", it) } })
        val a = r.getJSONArray("adopted")
        return (0 until a.length()).map { val o = a.getJSONObject(it); o.getString("sourceKey") to o.getJSONObject("card") } to
            (if (r.isNull("next")) null else r.getString("next"))
    }
    suspend fun cards(id: String, cursor: Long): SharedCardsPage {
        val r = ok("shared/${checkId(id)}/cards?cursor=$cursor")
        val a = r.getJSONArray("cards")
        return SharedCardsPage((0 until a.length()).map { a.getJSONObject(it) }, r.getLong("cursor"), r.optBoolean("hasMore"), r.optString("access", "full"))
    }
    suspend fun create(id: String, cardId: String, payload: JSONObject): SharedCardHttp =
        call("shared/${checkId(id)}/cards", "POST", JSONObject().put("id", checkId(cardId)).put("payload", payload))
    suspend fun update(id: String, cardId: String, baseRevision: Int, payload: JSONObject): SharedCardHttp =
        call("shared/${checkId(id)}/cards/${checkId(cardId)}", "PATCH", JSONObject().put("baseRevision", baseRevision).put("payload", payload))
    suspend fun delete(id: String, cardId: String, baseRevision: Int): SharedCardHttp =
        call("shared/${checkId(id)}/cards/${checkId(cardId)}", "DELETE", JSONObject().put("baseRevision", baseRevision))
    suspend fun addMember(id: String, personUid: String, access: String) {
        ok("shared/${checkId(id)}/members", "POST", JSONObject().put("uid", personUid).put("access", access))
    }
    suspend fun setAccess(id: String, personUid: String, access: String) {
        ok("shared/${checkId(id)}/members/" + enc(personUid), "PATCH", JSONObject().put("access", access))
    }
    suspend fun removeMember(id: String, personUid: String) { ok("shared/${checkId(id)}/members/" + enc(personUid), "DELETE") }
    suspend fun release(id: String) { ok("shared/${checkId(id)}", "DELETE") }

    /** Uploads one file into the shared category; returns its blob id. */
    suspend fun upload(id: String, file: File, kind: String, mime: String): String = withContext(Dispatchers.IO) {
        check(file.isFile && file.length() in 1..MAX_BYTES) { "Dosya yüklenemedi." }
        val blob = java.util.UUID.randomUUID().toString()
        val r = ok("shared/${checkId(id)}/assets/$blob", "POST", JSONObject().put("kind", kind).put("mime", mime).put("byte_size", file.length()))
        if (r.optString("state", r.optJSONObject("attachment")?.optString("state") ?: "") != "READY") exchange(id, blob, mime, file.readBytes())
        blob
    }
    suspend fun download(id: String, blob: String, target: File) = withContext(Dispatchers.IO) {
        if (target.isFile && target.length() > 0) return@withContext
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        try { part.writeBytes(exchange(id, blob, null, null)); check(part.renameTo(target)) } finally { part.delete() }
    }
    private suspend fun exchange(id: String, blob: String, mime: String?, bytes: ByteArray?): ByteArray = withContext(Dispatchers.IO) {
        checkId(id); checkId(blob); checkAccount()
        val headers = AndroidBackend.diagnosticHeaders().toMutableMap()
        headers["Authorization"] = "Bearer " + checkNotNull(AndroidBackend.auth.idTokenForUser(uid).getOrThrow()) { "Giriş gerekli." }
        val c = URL(base + "shared/$id/assets/$blob/content").openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false; c.connectTimeout = 10_000; c.readTimeout = 60_000
            c.requestMethod = if (bytes == null) "GET" else "PUT"
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (bytes != null) {
                c.doOutput = true; c.setRequestProperty("Content-Type", mime); c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            check(c.responseCode in 200..299) { "Dosya aktarılamadı." }
            val result = if (bytes != null) ByteArray(0) else c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val b = ByteArray(65536)
                while (true) { val n = input.read(b); if (n < 0) break; check(out.size() + n <= 16 * 1024 * 1024) { "Dosya çok büyük." }; out.write(b, 0, n) }
                out.toByteArray()
            }
            checkAccount(); result
        } finally { c.disconnect() }
    }

    private fun checkId(v: String): String { require(uuid.matches(v)) { "Geçersiz kimlik." }; return v }
    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
        fun failure(status: Int, code: String): String = when {
            code == "CATEGORY_MEMBER_LIMIT" -> "Bir kategoride en fazla 10 kişi olabilir."
            code == "CATEGORY_READ_ONLY" -> "Bu kategoride sadece görüntüleme iznin var."
            code == "CATEGORY_SYNC_REQUIRED" -> "Kategori henüz buluta eşitlenmedi. Biraz sonra tekrar dene."
            status == 401 || status == 403 -> "Bu işlem için iznin yok."
            status == 404 -> "Kategoriye artık erişimin yok."
            status == 429 -> "Çok fazla işlem. Biraz sonra tekrar dene."
            status == 503 -> "Ortak kategori servisi hazır değil."
            else -> "Ortak kategori güncellenemedi. İnternet bağlantını kontrol et."
        }
    }
}
