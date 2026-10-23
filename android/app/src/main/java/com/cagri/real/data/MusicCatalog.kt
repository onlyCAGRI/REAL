package com.cagri.real.data

import com.cagri.real.i18n.I18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The Apple Music catalog through the REAL Worker (/api/music-search: explicit originals too); the public iTunes
 * catalog if the Worker fails or is not set up. The iTunes Search API only returns the "cleaned" version of explicit songs.
 */
object MusicCatalog {
    private const val ENDPOINT = "https://real-backend-staging.cagrigul1907.workers.dev/api/music-search"

    suspend fun search(query: String): List<MusicBlock> =
        runCatching { appleMusic(query) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: itunes(query)

    private suspend fun appleMusic(query: String): List<MusicBlock> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val payload = JSONObject().put("query", trimmed.take(200)).put("storefront", storefront()).toString()
        val response = com.cagri.real.network.WorkerClient(com.cagri.real.platform.android.auth.AndroidBackend.workerTransport)
            .postJson(ENDPOINT, payload, 10_000, 10_000, readErrorBody = false)
        check(response.statusCode in 200..299) { "HTTP ${response.statusCode} from REAL Worker music-search" }
        val results = JSONObject(response.body).optJSONArray("results")
        buildList {
            for (index in 0 until (results?.length() ?: 0)) {
                val item = results?.optJSONObject(index) ?: continue
                val title = item.optString("title")
                val link = secureUrl(item.optString("providerUrl")) ?: continue
                if (title.isBlank()) continue
                add(MusicBlock(title = title, artist = item.optString("artist"),
                    album = item.optString("album").takeIf { it.isNotBlank() && it != "null" },
                    artworkUrl = secureUrl(item.optString("artworkUrl")),
                    provider = "iTunes", providerTrackId = item.optString("providerTrackId").takeIf { it.isNotBlank() && it != "null" },
                    providerUrl = link, previewUrl = secureUrl(item.optString("previewUrl")),
                    explicit = item.optBoolean("explicit", false)))
            }
        }
    }

    private suspend fun itunes(query: String): List<MusicBlock> = withContext(Dispatchers.IO) {
        val term = URLEncoder.encode(query.trim().take(200), "UTF-8")
        val connection = URL("https://itunes.apple.com/search?term=$term&entity=song&media=music&country=${storefront()}&limit=20")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode in 200..299) { "Music catalog unavailable" }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(json).optJSONArray("results")
            buildList {
                for (index in 0 until (results?.length() ?: 0)) {
                    val item = results?.optJSONObject(index) ?: continue
                    val title = item.optString("trackName")
                    val link = secureUrl(item.optString("trackViewUrl")) ?: continue
                    if (title.isBlank()) continue
                    add(MusicBlock(title = title, artist = item.optString("artistName"),
                        album = item.optString("collectionName").takeIf { it.isNotBlank() },
                        artworkUrl = secureUrl(item.optString("artworkUrl100")),
                        provider = "iTunes", providerTrackId = item.optString("trackId").takeIf { it.isNotBlank() },
                        providerUrl = link, previewUrl = secureUrl(item.optString("previewUrl"))))
                    // trackTimeMillis is the FULL song duration, not the preview duration.
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The Apple Music store of the person's country (phone region, e.g. DE in Germany), not always Turkey:
     * songs, prices and previews differ per country. Falls back to the US store.
     */
    fun storefront(): String = java.util.Locale.getDefault().country.uppercase()
        .takeIf { it.length == 2 && it.all(Char::isLetter) } ?: "US"

    /** "Almanya", "Türkiye"… in the app language. */
    fun storefrontName(): String =
        java.util.Locale("", storefront()).getDisplayCountry(I18n.locale).ifBlank { storefront() }

    private fun secureUrl(value: String): String? = when {
        value.startsWith("https://") -> value
        value.startsWith("http://") -> "https://" + value.removePrefix("http://")
        else -> null
    }
}
