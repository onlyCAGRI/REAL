package com.cagri.real.platform.android.announcements

import android.content.Context
import com.cagri.real.network.HttpRequest
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Local, non-synced, UID-bound inbox. A read retry never contains card/user content. */
data class RealAnnouncement(val id: String, val title: String, val body: String,
    val expiresAt: Long, val read: Boolean)
class AnnouncementRepository(context: Context, private val uid: String) {
    private val lock = Mutex()
    private val app = context.applicationContext
    private val name = "real_announcements_" + MessageDigest.getInstance("SHA-256")
        .digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
    private val prefs get() = app.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun checkAccount() = check(AndroidBackend.auth.currentUser()?.id == uid) { "Account changed" }
    private suspend fun request(path: String, body: JSONObject? = null): JSONObject {
        checkAccount()
        val result = AndroidBackend.syncTransport().execute(HttpRequest(
            AndroidBackend.ORIGIN + path, if (body == null) "GET" else "POST",
            if (body == null) emptyMap() else mapOf("Content-Type" to "application/json"),
            body?.toString().orEmpty(), 10000, 15000))
        checkAccount()
        if (result.statusCode !in 200..299) throw InboxUnavailable(result.statusCode)
        return JSONObject(result.body).also { check(it.optBoolean("ok")) }
    }
    private fun decode(raw: String): List<RealAnnouncement> {
        val pending = prefs.getStringSet("pending", emptySet()).orEmpty()
        return runCatching {
            val items = JSONArray(raw)
            (0 until minOf(items.length(), 100)).map { i -> items.getJSONObject(i).let {
                RealAnnouncement(it.getString("id"), it.getString("title"), it.getString("body"),
                    it.getLong("expiresAt"), !it.isNull("readAt") || it.getString("id") in pending)
            } }.filter { it.expiresAt > System.currentTimeMillis() }
        }.getOrDefault(emptyList())
    }
    suspend fun cached(): List<RealAnnouncement> = withContext(Dispatchers.IO) { lock.withLock {
        checkAccount(); decode(prefs.getString("feed", "[]") ?: "[]")
    } }
    suspend fun refresh(): List<RealAnnouncement> = withContext(Dispatchers.IO) { lock.withLock {
        checkAccount()
        val acknowledged = mutableSetOf<String>()
        // One bounded pass per foreground/manual refresh; no observer or repeating timer.
        for (id in prefs.getStringSet("pending", emptySet()).orEmpty().take(8)) {
            try { request("/api/announcements/v1/read", JSONObject().put("id", id)); acknowledged.add(id) }
            catch (e: InboxUnavailable) { if (e.status == 404) acknowledged.add(id) else throw e }
        }
        val raw = request("/api/announcements/v1/feed").getJSONArray("items").toString()
        checkAccount(); prefs.edit().putString("feed", raw).apply()
        acknowledged.forEach(::removePending)
        // Bound local added-marker storage to the current server feed.
        val active = JSONArray(raw).let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") }.toSet() }
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("added_") && it.removePrefix("added_") !in active }.forEach { editor.remove(it) }
        editor.apply()
        decode(raw)
    } }
    private fun removePending(id: String) {
        val ids = prefs.getStringSet("pending", emptySet()).orEmpty().toMutableSet()
        ids.remove(id); prefs.edit().putStringSet("pending", ids).apply()
    }
    suspend fun read(id: String) = withContext(Dispatchers.IO) { lock.withLock {
        checkAccount()
        val ids = prefs.getStringSet("pending", emptySet()).orEmpty().toMutableSet()
        ids.add(id); prefs.edit().putStringSet("pending", ids.take(100).toSet()).commit()
        // Keep the pending receipt until the refreshed server feed also records it.
        request("/api/announcements/v1/read", JSONObject().put("id", id))
    } }
    suspend fun added(id: String): Boolean = withContext(Dispatchers.IO) { lock.withLock {
        checkAccount(); prefs.getBoolean("added_$id", false)
    } }
    suspend fun rememberAdded(id: String) = withContext(Dispatchers.IO) { lock.withLock {
        checkAccount(); prefs.edit().putBoolean("added_$id", true).commit(); Unit
    } }
}
private class InboxUnavailable(val status: Int) : Exception("Inbox unavailable")
