package com.cagri.real.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Public TVmaze API client for Series / TV content.
 * No API key required.
 */
object TvMazeCatalog {
    private const val SEARCH_ENDPOINT = "https://api.tvmaze.com/search/shows?q="
    private const val USER_AGENT = "REAL-Android/1.0"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    suspend fun searchShows(query: String): List<MediaBlock> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val encoded = URLEncoder.encode(trimmed.take(200), "UTF-8")
        val endpoint = "$SEARCH_ENDPOINT$encoded"

        val json = executeGet(endpoint)
        parseShows(json)
    }

    suspend fun resolveCandidate(candidate: MoodSuggestionMediaCandidate): MediaBlock? = withContext(Dispatchers.IO) {
        val primaryQuery = candidate.searchQuery.trim().ifBlank { candidate.title.trim() }
        if (primaryQuery.isBlank()) return@withContext null

        val primaryResults = searchShows(primaryQuery)
        val primaryMatch = findTvMatch(primaryResults, candidate)
        if (primaryMatch != null) return@withContext primaryMatch

        val fallbackQuery = candidate.title.trim()
        if (fallbackQuery.isNotBlank() && !fallbackQuery.equals(primaryQuery, ignoreCase = true)) {
            val fallbackResults = searchShows(fallbackQuery)
            val fallbackMatch = findTvMatch(fallbackResults, candidate)
            if (fallbackMatch != null) return@withContext fallbackMatch
        }

        null
    }

    fun findTvMatch(results: List<MediaBlock>, candidate: MoodSuggestionMediaCandidate): MediaBlock? {
        if (results.isEmpty()) return null
        val targetTitle = MediaTitleMatcher.normalize(candidate.title)
        if (targetTitle.isBlank()) return null
        val targetYear = candidate.year?.takeIf { it > 0 }

        val exactTitleCandidates = results.filter { block ->
            MediaTitleMatcher.normalize(block.title) == targetTitle
        }

        if (targetYear != null) {
            exactTitleCandidates.firstOrNull { block ->
                block.year != null && kotlin.math.abs(block.year - targetYear) <= 1
            }?.let { return it }

            // TVmaze can occasionally omit premiered/year. A single exact title without a year is still usable.
            if (exactTitleCandidates.size == 1 && exactTitleCandidates.single().year == null) {
                return exactTitleCandidates.single()
            }
        } else {
            exactTitleCandidates.firstOrNull()?.let { return it }
        }

        val strongCandidates = results.filter { block ->
            val norm = MediaTitleMatcher.normalize(block.title)
            norm.isNotBlank() && (
                norm.startsWith(targetTitle) ||
                    targetTitle.startsWith(norm) ||
                    norm.contains(targetTitle) ||
                    targetTitle.contains(norm)
                )
        }

        if (targetYear != null) {
            val closeYear = strongCandidates.filter { block ->
                block.year != null && kotlin.math.abs(block.year - targetYear) <= 1
            }
            if (closeYear.size == 1) return closeYear.single()
            return null
        }

        // No year: only accept a fuzzy/prefix match when it is genuinely unambiguous.
        val prefixCandidates = strongCandidates.filter { block ->
            val norm = MediaTitleMatcher.normalize(block.title)
            norm.startsWith(targetTitle) || targetTitle.startsWith(norm)
        }
        return prefixCandidates.singleOrNull()
    }

    private fun parseShows(json: String): List<MediaBlock> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val list = mutableListOf<MediaBlock>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val show = item.optJSONObject("show") ?: continue
            val id = show.opt("id")?.toString() ?: continue
            val name = show.optString("name", "").trim()
            if (name.isBlank()) continue

            val premiered = show.optString("premiered").takeIf { it.isNotBlank() }
            val year = premiered?.take(4)?.toIntOrNull()

            val genresArray = show.optJSONArray("genres")
            val genreList = buildList {
                if (genresArray != null) {
                    for (g in 0 until genresArray.length()) {
                        val gStr = genresArray.optString(g).trim()
                        if (gStr.isNotBlank()) add(gStr)
                    }
                }
            }
            val genre = genreList.joinToString(", ").takeIf { it.isNotBlank() }

            val runtimeMinutes = show.optInt("averageRuntime", 0).takeIf { it in 1..1440 }
                ?: show.optInt("runtime", 0).takeIf { it in 1..1440 }

            val rawSummary = show.optString("summary").takeIf { it.isNotBlank() }
            val description = stripHtml(rawSummary)

            val imageObj = show.optJSONObject("image")
            val originalImg = imageObj?.optString("original")?.takeIf { it.isNotBlank() }
            val mediumImg = imageObj?.optString("medium")?.takeIf { it.isNotBlank() }
            val artworkUrl = secureUrl(originalImg ?: mediumImg)

            val url = secureUrl(show.optString("url")) ?: show.optString("url")

            list.add(
                MediaBlock(
                    type = MediaType.TV,
                    providerId = id,
                    title = name,
                    subtitle = year?.let { "$it • Dizi" } ?: "Dizi",
                    year = year,
                    genre = genre,
                    description = description,
                    artworkUrl = artworkUrl,
                    provider = "TVmaze",
                    providerUrl = url,
                    durationMillis = runtimeMinutes?.let { it * 60_000L }
                )
            )
        }

        return list.distinctBy { it.providerId ?: it.title.lowercase() }
    }

    private fun executeGet(urlStr: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code from TVmaze")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun stripHtml(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return html
            .replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun secureUrl(value: String?): String? = when {
        value.isNullOrBlank() -> null
        value.startsWith("https://") -> value
        value.startsWith("http://") -> "https://" + value.removePrefix("http://")
        else -> null
    }
}
