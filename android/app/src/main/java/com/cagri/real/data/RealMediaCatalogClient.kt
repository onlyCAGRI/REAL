package com.cagri.real.data

import com.cagri.real.network.HttpTransport
import com.cagri.real.network.WorkerClient
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Client for Movie content through REAL Worker -> TheTVDB V4.
 * No TheTVDB secret is stored on Android; authenticated via Firebase ID token and App Check.
 */
object RealMediaCatalogClient {
    private const val ENDPOINT = "https://real-backend-staging.cagrigul1907.workers.dev/api/media-search"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    suspend fun searchMovies(
        query: String,
        title: String? = null,
        year: Int? = null,
        endpoint: String = ENDPOINT,
        transport: HttpTransport = AndroidBackend.workerTransport
    ): List<MediaBlock> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val payload = JSONObject().apply {
            put("type", "movie")
            put("query", trimmed)
            if (!title.isNullOrBlank()) {
                put("title", title.trim())
            }
            if (year != null && year > 0) {
                put("year", year)
            }
        }.toString()

        val json = executePost(endpoint, payload, transport)
        parseMovies(json)
    }

    suspend fun resolveCandidate(candidate: MoodSuggestionMediaCandidate): MediaBlock? = withContext(Dispatchers.IO) {
        val primaryQuery = candidate.searchQuery.trim().ifBlank { candidate.title.trim() }
        if (primaryQuery.isBlank()) return@withContext null

        val primaryResults = searchMovies(
            query = primaryQuery,
            title = candidate.title,
            year = candidate.year
        )
        val primaryMatch = findMovieMatch(primaryResults, candidate)
        if (primaryMatch != null) return@withContext primaryMatch

        val fallbackQuery = candidate.title.trim()
        if (fallbackQuery.isNotBlank() && !fallbackQuery.equals(primaryQuery, ignoreCase = true)) {
            val fallbackResults = searchMovies(
                query = fallbackQuery,
                title = candidate.title,
                year = candidate.year
            )
            val fallbackMatch = findMovieMatch(fallbackResults, candidate)
            if (fallbackMatch != null) return@withContext fallbackMatch
        }

        null
    }

    fun findMovieMatch(results: List<MediaBlock>, candidate: MoodSuggestionMediaCandidate): MediaBlock? {
        if (results.isEmpty()) return null
        val targetTitle = MediaTitleMatcher.normalize(candidate.title)
        if (targetTitle.isBlank()) return null
        val targetYear = candidate.year?.takeIf { it > 0 }

        val exactTitleCandidates = results.filter { block ->
            MediaTitleMatcher.normalize(block.title) == targetTitle
        }

        if (targetYear != null) {
            // Prefer the exact title only when its known year agrees (±1). A known mismatch must not pass.
            exactTitleCandidates.firstOrNull { block ->
                block.year != null && kotlin.math.abs(block.year - targetYear) <= 1
            }?.let { return it }

            // If TheTVDB omitted the year entirely, a single exact-title result is still safe enough.
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
            // Prefix/contains matches are accepted only when a single candidate survives the year check.
            if (closeYear.size == 1) return closeYear.single()
            return null
        }

        val prefixCandidates = strongCandidates.filter { block ->
            val norm = MediaTitleMatcher.normalize(block.title)
            norm.startsWith(targetTitle) || targetTitle.startsWith(norm)
        }
        return prefixCandidates.singleOrNull()
    }

    private fun parseMovies(json: String): List<MediaBlock> {
        val root = runCatching { JSONObject(json) }.getOrNull()
        val array = root?.optJSONArray("results")
            ?: runCatching { JSONArray(json) }.getOrNull()
            ?: return emptyList()

        val list = mutableListOf<MediaBlock>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title", "").trim()
            if (title.isBlank()) continue

            val providerId = item.opt("providerId")?.toString() ?: item.opt("id")?.toString()
            val year = if (item.isNull("year")) null else item.optInt("year").takeIf { it > 0 }
            val genre = item.optString("genre").takeIf { it.isNotBlank() }
            val description = item.optString("description").takeIf { it.isNotBlank() }
            val artworkUrl = secureUrl(item.optString("artworkUrl").takeIf { it.isNotBlank() })
            val provider = item.optString("provider", "TheTVDB").ifBlank { "TheTVDB" }
            val providerUrl = secureUrl(item.optString("providerUrl").takeIf { it.isNotBlank() })
                ?: item.optString("providerUrl", "")

            list.add(
                MediaBlock(
                    type = MediaType.MOVIE,
                    providerId = providerId,
                    title = title,
                    subtitle = year?.let { "$it • Film" } ?: "Film",
                    year = year,
                    genre = genre,
                    description = description,
                    artworkUrl = artworkUrl,
                    provider = provider,
                    providerUrl = providerUrl,
                    durationMillis = null
                )
            )
        }

        return list.distinctBy { it.providerId ?: it.providerUrl.ifBlank { it.title.lowercase() } }
    }

    private suspend fun executePost(endpoint: String, payload: String, transport: HttpTransport): String {
        val response = WorkerClient(transport).postJson(
            endpoint, payload, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, readErrorBody = false
        )
        if (response.statusCode !in 200..299) {
            throw IOException("HTTP ${response.statusCode} from REAL Worker media-search")
        }
        return response.body
    }

    private fun secureUrl(value: String?): String? = when {
        value.isNullOrBlank() -> null
        value.startsWith("https://") -> value
        value.startsWith("http://") -> "https://" + value.removePrefix("http://")
        else -> null
    }
}
