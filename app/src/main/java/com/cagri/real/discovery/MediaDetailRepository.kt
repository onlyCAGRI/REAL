package com.cagri.real.discovery

import android.os.SystemClock
import android.text.Html
import com.cagri.real.data.MediaBlock
import com.cagri.real.data.MediaType
import com.cagri.real.network.WorkerClient
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.coroutines.coroutineContext

data class MediaCastMember(val id: String, val name: String, val role: String?, val imageUrl: String?)
data class MediaDetail(val description: String?, val durationMillis: Long?, val cast: List<MediaCastMember>)

/** Screen-scoped bounded catalog cache; details never alter or sync a REAL card. */
class MediaDetailRepository {
    private data class Cached(val detail: MediaDetail, val savedAt: Long)
    private val cache = LinkedHashMap<String, Cached>()
    suspend fun load(media: MediaBlock): MediaDetail = withContext(Dispatchers.IO) {
        val id = media.providerId?.takeIf { it.matches(Regex("[1-9]\\d{0,9}")) }
            ?: throw IOException("Bu yapım için oyuncu bilgisi bulunamadı.")
        val key = "${media.type}:${media.provider}:$id"
        synchronized(cache) { cache[key] }?.takeIf { SystemClock.elapsedRealtime() - it.savedAt < 3_600_000L }
            ?.let { return@withContext it.detail }
        val detail = when {
            media.type == MediaType.TV && media.provider.equals("TVmaze", true) -> parseTvmaze(JSONObject(getTvmaze(id)), id)
            media.type == MediaType.MOVIE && media.provider.equals("TheTVDB", true) -> {
                val body = JSONObject().put("provider", "TheTVDB").put("type", "MOVIE").put("providerId", id).toString()
                val response = WorkerClient(AndroidBackend.workerTransport).postJson(
                    "${AndroidBackend.ORIGIN}/api/media-details", body, 15_000, 18_000, readErrorBody = false)
                if (response.statusCode !in 200..299) throw IOException("Oyuncular şu anda alınamadı.")
                val result = JSONObject(response.body)
                if (!result.optBoolean("ok")) throw IOException("Oyuncular şu anda alınamadı.")
                parseMovie(result.getJSONObject("details"), id)
            }
            else -> throw IOException("Bu kaynağın oyuncu bilgisi desteklenmiyor.")
        }
        coroutineContext.ensureActive()
        synchronized(cache) {
            if (cache.size >= 24) cache.remove(cache.keys.first())
            cache[key] = Cached(detail, SystemClock.elapsedRealtime())
        }
        detail
    }
    private fun getTvmaze(id: String): String {
        val connection = URL("https://api.tvmaze.com/shows/$id?embed=cast").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000; connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "REAL-Android/MediaDetails")
            if (connection.responseCode !in 200..299) throw IOException("Oyuncular şu anda alınamadı.")
            return connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 1_048_576) throw IOException("Katalog yanıtı çok büyük.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray().toString(Charsets.UTF_8)
            }
        } finally { connection.disconnect() }
    }
    companion object {
        private fun string(json: JSONObject, key: String): String? = if (json.isNull(key)) null else
            json.optString(key).trim().takeIf { it.isNotBlank() }
        private fun image(value: String?): String? = runCatching {
            val uri = URI(value ?: return null)
            val host = uri.host.orEmpty().lowercase()
            value.takeIf { uri.scheme == "https" && uri.userInfo == null &&
                (host == "static.tvmaze.com" || host == "thetvdb.com" || host.endsWith(".thetvdb.com")) }
        }.getOrNull()
        internal fun parseTvmaze(json: JSONObject, id: String): MediaDetail {
            require(json.optLong("id").toString() == id)
            val entries = json.optJSONObject("_embedded")?.optJSONArray("cast") ?: JSONArray()
            val cast = buildList {
                for (i in 0 until minOf(entries.length(), 100)) {
                    val row = entries.optJSONObject(i) ?: continue
                    val person = row.optJSONObject("person") ?: continue
                    val name = string(person, "name")?.take(120) ?: continue
                    val photo = person.optJSONObject("image")
                    add(MediaCastMember(string(person, "id") ?: name, name,
                        row.optJSONObject("character")?.let { string(it, "name") }?.take(120),
                        image(photo?.let { string(it, "medium") ?: string(it, "original") })))
                }
            }.distinctBy { it.id }.take(24)
            val runtime = json.optInt("averageRuntime").takeIf { it in 1..1440 }
                ?: json.optInt("runtime").takeIf { it in 1..1440 }
            val description = string(json, "summary")?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim().take(6000) }
            return MediaDetail(description, runtime?.times(60_000L), cast)
        }
        internal fun parseMovie(json: JSONObject, id: String): MediaDetail {
            require(string(json, "provider") == "TheTVDB" && string(json, "providerId") == id)
            val entries = json.optJSONArray("cast") ?: JSONArray()
            val cast = buildList {
                for (i in 0 until minOf(entries.length(), 24)) {
                    val row = entries.optJSONObject(i) ?: continue
                    val name = string(row, "name")?.take(120) ?: continue
                    add(MediaCastMember(string(row, "id") ?: name, name, string(row, "role")?.take(120), image(string(row, "imageUrl"))))
                }
            }.distinctBy { it.id }
            val duration = json.optLong("durationMillis").takeIf { it in 60_000L..86_400_000L }
            return MediaDetail(string(json, "description")?.take(6000), duration, cast)
        }
    }
}
