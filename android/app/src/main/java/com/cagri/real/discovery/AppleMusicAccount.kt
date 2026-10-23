package com.cagri.real.discovery

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cagri.real.network.WorkerClient
import com.cagri.real.platform.android.auth.AndroidBackend
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Apple Music hesabı (Ayarlar → Apple Music). The REAL Worker's /music/connect page signs in with Apple's MusicKit and
 * sends the Music User Token back as real://apple-music?state=…#token=…; it stays on this phone. With it, Müzik keşfi
 * can offer "Sana özel" songs and a right swipe also adds the song to a "REAL" playlist in Apple Music.
 */
object AppleMusicAccount {
    private const val CONNECT_PAGE = "https://real.realapp.workers.dev/music/connect"
    private const val WORKER = "https://real-backend-staging.cagrigul1907.workers.dev"
    private const val ME_ENDPOINT = "$WORKER/api/music-me"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("real_apple_music", Context.MODE_PRIVATE)
    private fun token(context: Context) = prefs(context).getString("token", null)?.takeIf { it.isNotBlank() }

    fun isConnected(context: Context) = token(context) != null

    /** The sign-in page; a fresh state ties the answer to this request. */
    fun connect(context: Context) {
        val state = ByteArray(24).also { SecureRandom().nextBytes(it) }
            .let { android.util.Base64.encodeToString(it, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP) }
        prefs(context).edit().putString("state", state).apply()
        // Inside REAL (AppleMusicConnectWebActivity): in Chrome Apple's "Allow" popup cannot answer the page.
        context.startActivity(Intent(context, com.cagri.real.ui.AppleMusicConnectWebActivity::class.java)
            .setData(Uri.parse("$CONNECT_PAGE?state=$state")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The page's answer; false when it does not belong to the request [connect] made. */
    fun complete(context: Context, link: Uri?): Boolean {
        val expected = prefs(context).getString("state", null) ?: return false
        if (link?.scheme != "real" || link.host != "apple-music" || link.getQueryParameter("state") != expected) return false
        val token = link.fragment?.split('&')?.firstOrNull { it.startsWith("token=") }?.removePrefix("token=")
            ?.takeIf { it.isNotBlank() && it.length <= 4096 } ?: return false
        prefs(context).edit().putString("token", token).remove("state").apply()
        return true
    }

    fun disconnect(context: Context) { prefs(context).edit().clear().apply() }

    private suspend fun call(context: Context, payload: JSONObject): JSONObject? {
        val userToken = token(context) ?: return null
        val response = WorkerClient(AndroidBackend.workerTransport)
            .postJson(ME_ENDPOINT, payload.put("userToken", userToken).toString(), 10_000, 25_000, readErrorBody = false)
        // Apple no longer accepts the token (signed out, subscription ended…): back to "not connected".
        if (response.statusCode == 401) { disconnect(context); return null }
        check(response.statusCode in 200..299) { "HTTP ${response.statusCode} from REAL Worker music-me" }
        return JSONObject(response.body)
    }

    /** Songs Apple picks for the person, within these iTunes genre ids; the Worker's song objects. */
    suspend fun personalSongs(context: Context, genreIds: List<Int>): JSONArray? =
        call(context, JSONObject().put("action", "discover").put("genres", JSONArray(genreIds)))?.optJSONArray("songs")

    /**
     * Once per connection: the songs already in "Müzik listem" go to the "REAL" playlist too (100 per call, in the
     * list's order). Marked done only when every part went through, so a failure is tried again next time.
     */
    suspend fun copyListOnce(context: Context, songIds: List<String>) {
        val p = prefs(context)
        if (token(context) == null || p.getBoolean("list_copied", false)) return
        for (part in songIds.filter { id -> id.isNotEmpty() && id.all(Char::isDigit) }.distinct().chunked(100)) {
            val payload = JSONObject().put("action", "add").put("songIds", JSONArray(part))
            p.getString("playlist", null)?.let { payload.put("playlistId", it) }
            val result = call(context, payload) ?: return
            result.optString("playlistId").takeIf { it.startsWith("p.") }?.let { p.edit().putString("playlist", it).apply() }
            if (!result.optBoolean("ok")) return
        }
        p.edit().putBoolean("list_copied", true).apply()
    }

    /** Adds an Apple Music catalog song to the person's "REAL" playlist (made on the first save; its id is kept here);
     *  false when not connected or Apple refused. */
    suspend fun addToLibrary(context: Context, songId: String): Boolean {
        if (!songId.all(Char::isDigit)) return false
        val payload = JSONObject().put("action", "add").put("songId", songId)
        prefs(context).getString("playlist", null)?.let { payload.put("playlistId", it) }
        val result = call(context, payload) ?: return false
        result.optString("playlistId").takeIf { it.startsWith("p.") }?.let { prefs(context).edit().putString("playlist", it).apply() }
        return result.optBoolean("ok")
    }
}
