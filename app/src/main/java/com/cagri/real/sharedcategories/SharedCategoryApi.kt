package com.cagri.real.sharedcategories

import android.content.Context
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderJson
import com.cagri.real.data.ReminderItem
import com.cagri.real.network.HttpRequest
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.platform.android.sync.SyncLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** References only: nothing is inserted into ReminderRepository or the recipient's home list. */
class SharedCategoryApi(private val context: Context, val uid: String) {
    private val base = AndroidBackend.ORIGIN + "/api/categories/v1/"
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private fun checkAccount() { check(AndroidBackend.auth.currentUser()?.id == uid) { "Hesap değişti. Ekranı yeniden aç." } }
    private fun cacheDirectory(): File {
        val key = java.security.MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(context.cacheDir, "shared_categories/$key").apply { mkdirs() }
    }
    private suspend fun request(path: String, method: String = "GET", body: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        checkAccount()
        val response = AndroidBackend.syncTransport().execute(HttpRequest(base + path, method,
            mapOf("Content-Type" to "application/json", "Accept" to "application/json"), body.toString(), 15_000, 30_000))
        checkAccount()
        check(response.statusCode in 200..299) { when (response.statusCode) {
            409 -> "Kategori henüz eşitlenmedi veya başka cihazda değişti. Yenile ve tekrar dene."
            401, 403 -> "Bu işlem için REAL hesabınla giriş yap."
            404 -> "Kategoriye erişimin yok veya davet artık geçerli değil."
            429 -> "Çok fazla işlem. Biraz sonra tekrar dene."
            503 -> "Ortak kategori servisi hazır değil. Staging migration ve Worker güncellemesini kontrol et."
            else -> "Kategori güncellenemedi. İnternet bağlantısını kontrol et."
        } }
        JSONObject(response.body)
    }
    suspend fun list(): List<SharedCategory> = withContext(Dispatchers.IO) {
        val array = JSONArray()
        var after: String? = null
        val seen = mutableSetOf<String>()
        do {
            val page = request("shared" + (after?.let { "?after=$it" } ?: ""))
            val entries = page.getJSONArray("items")
            for (i in 0 until entries.length()) array.put(entries.getJSONObject(i))
            after = if (page.isNull("next")) null else page.getString("next")
            check(after == null || (uuid.matches(after!!) && seen.add(after!!))) { "Kategori listesi okunamadı." }
        } while (after != null)
        val response = JSONObject().put("items", array)
        val result = (0 until array.length()).map { decodeSharedCategory(array.getJSONObject(it)) }
        checkAccount()
        val target = File(cacheDirectory(), "catalog.json")
        val temp = File(cacheDirectory(), "catalog.tmp")
        temp.writeText(response.toString())
        check(temp.renameTo(target)) { "Kategori önbelleği kaydedilemedi." }
        result
    }
    suspend fun cachedList(): List<SharedCategory> = withContext(Dispatchers.IO) {
        checkAccount()
        runCatching { val a = JSONObject(File(cacheDirectory(), "catalog.json").readText()).getJSONArray("items")
            (0 until a.length()).map { decodeSharedCategory(a.getJSONObject(it)) } }.getOrDefault(emptyList())
    }
    suspend fun ensure(category: CustomCategory, link: String): SharedCategory {
        SyncLocal.syncForSharing()
        return decodeSharedCategory(request("shared", "POST", JSONObject().put("categoryKey", category.id.toString())
            .put("generation", category.createdAt).put("link", link)).getJSONObject("item"))
    }
    suspend fun metadata(id: String): SharedCategoryDetail {
        require(uuid.matches(id))
        val meta = request("shared/$id")
        val m = meta.getJSONArray("members")
        val people = (0 until m.length()).map { val p = m.getJSONObject(it)
            SharedCategoryMember(p.getString("uid"), p.getString("name"), p.getBoolean("isOwner"),
                p.optLong("pictureVersion", 0L).takeIf { it > 0 && !p.isNull("pictureVersion") }) }
        return SharedCategoryDetail(decodeSharedCategory(meta.getJSONObject("item")), people, emptyList())
    }
    suspend fun detail(id: String): SharedCategoryDetail = withContext(Dispatchers.IO) {
        val meta = metadata(id)
        val goals = mutableListOf<SharedCategoryGoal>()
        var after: String? = null
        val seen = mutableSetOf<String>()
        do {
            val page = request("shared/$id/goals" + (after?.let { "?after=$it" } ?: ""))
            val a = page.getJSONArray("goals")
            for (i in 0 until a.length()) {
                val item = a.getJSONObject(i); val raw = item.getJSONObject("card").toString()
                goals += SharedCategoryGoal(item.getString("recordId"), ReminderJson.decode(JSONArray().put(JSONObject(raw)).toString()).single(), raw)
            }
            after = if (page.isNull("next")) null else page.getString("next")
            check(after == null || (uuid.matches(after!!) && seen.add(after!!))) { "Kategori sayfaları okunamadı." }
        } while (after != null)
        checkAccount()
        meta.copy(goals = goals)
    }
    suspend fun add(id: String, personUid: String) { request("shared/$id/members", "POST", JSONObject().put("uid", personUid)) }
    suspend fun remove(id: String, personUid: String) {
        request("shared/$id/members/" + URLEncoder.encode(personUid, "UTF-8").replace("+", "%20"), "DELETE")
    }
    suspend fun link(item: SharedCategory, value: String): Int = request("shared/${item.id}", "PATCH",
        JSONObject().put("link", value).put("revision", item.revision)).getInt("revision")
    suspend fun invite(id: String): String {
        val url = request("shared/$id/invites", "POST").getString("url")
        val u = java.net.URI(url)
        check(u.scheme == "https" && u.rawUserInfo == null && u.port == -1 && u.rawQuery == null && u.rawFragment == null &&
            Regex("/g/[0-9a-f]{64}").matches(u.rawPath)) { "Davet bağlantısı geçersiz." }
        return url
    }
    suspend fun preview(token: String): JSONObject { require(Regex("[0-9a-f]{64}").matches(token)); return request("invites/$token") }
    suspend fun accept(token: String) { require(Regex("[0-9a-f]{64}").matches(token)); request("invites/$token/accept", "POST") }

    suspend fun hydrate(categoryId: String, goal: SharedCategoryGoal): Pair<ReminderItem, Boolean> = withContext(Dispatchers.IO) {
        val copy = JSONObject(goal.raw)
        var unavailable = false
        suspend fun file(o: JSONObject, pathKey: String = "localPath") {
            val blob = o.optString("blobId"); if (blob.isBlank()) return
            try { o.put(pathKey, asset(categoryId, goal.recordId, blob).absolutePath) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { unavailable = true }
        }
        for (key in listOf("attachments", "voiceRecordings")) {
            val a = copy.optJSONArray(key) ?: continue
            for (i in 0 until a.length()) file(a.getJSONObject(i))
        }
        copy.optJSONObject("cardStack")?.optJSONArray("cards")?.let { a ->
            for (i in 0 until a.length()) file(a.getJSONObject(i), "imageUri")
        }
        ReminderJson.decode(JSONArray().put(copy).toString()).single() to unavailable
    }
    fun open(attachment: com.cagri.real.data.ReminderAttachment) {
        checkAccount()
        val file = File(attachment.localPath)
        require(file.isFile && file.canonicalFile.parentFile == cacheDirectory().canonicalFile) { "Dosya henüz indirilemedi." }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.attachments", file)
        com.cagri.real.data.AttachmentStorage.openContentUri(context, uri, attachment.mimeType, attachment.displayName)
    }
    private suspend fun asset(category: String, record: String, blob: String): File {
        require(uuid.matches(category) && uuid.matches(record) && uuid.matches(blob)); checkAccount()
        val target = File(cacheDirectory(), "$category-$record-$blob")
        if (target.exists() && target.length() > 0) return target
        val credentials = AndroidBackend.diagnosticHeaders(uid); checkAccount()
        val connection = URL(base + "shared/$category/goals/$record/assets/$blob").openConnection() as HttpURLConnection
        val temp = File.createTempFile("asset-", ".tmp", cacheDirectory())
        try {
            connection.instanceFollowRedirects = false; connection.connectTimeout = 10_000; connection.readTimeout = 30_000
            credentials.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            check(connection.responseCode == 200) { "Dosya alınamadı." }
            var length = 0L
            connection.inputStream.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break
                    length += n; check(length <= 16 * 1024 * 1024) { "Dosya çok büyük." }; output.write(buffer, 0, n) }
            } }
            checkAccount(); check(length > 0 && temp.renameTo(target)); return target
        } finally { connection.disconnect(); temp.delete() }
    }
}
