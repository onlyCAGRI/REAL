package com.cagri.real.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Public iTunes catalog: no account, token, backend, or audio download. */
object MusicCatalog {
    suspend fun search(query: String): List<MusicBlock> = withContext(Dispatchers.IO) {
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

    /** "Almanya", "Türkiye"… in Turkish. */
    fun storefrontName(): String =
        java.util.Locale("", storefront()).getDisplayCountry(java.util.Locale("tr")).ifBlank { storefront() }

    private fun secureUrl(value: String): String? = when {
        value.startsWith("https://") -> value
        value.startsWith("http://") -> "https://" + value.removePrefix("http://")
        else -> null
    }
}
