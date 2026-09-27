package com.cagri.bigboscagri.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Public Apple/iTunes Search API for Movie & TV content in Turkey (country=TR).
 * No API key, secret, or account required.
 */
object MediaCatalog {
    private val memoryCache = ConcurrentHashMap<String, MediaBlock?>()
    private const val MAX_CACHE_SIZE = 120

    suspend fun resolveMovie(candidate: MoodSuggestionMediaCandidate): MediaBlock? = withContext(Dispatchers.IO) {
        val primaryQuery = candidate.searchQuery.trim().ifBlank {
            listOfNotNull(candidate.title, candidate.year?.toString()).joinToString(" ")
        }
        if (primaryQuery.isBlank()) return@withContext null

        val cacheKey = "movie:" + MusicCandidateMatcher.normalize(primaryQuery)
        if (memoryCache.containsKey(cacheKey)) {
            return@withContext memoryCache[cacheKey]
        }

        val primaryResults = searchMovies(primaryQuery)
        val match = findMovieMatch(primaryResults, candidate)
        if (match != null) {
            putCache(cacheKey, match)
            return@withContext match
        }

        // At most ONE fallback search if primary query had extra keywords
        val fallbackQuery = candidate.title.trim()
        if (fallbackQuery.isNotBlank() && !fallbackQuery.equals(primaryQuery, ignoreCase = true)) {
            val fallbackResults = searchMovies(fallbackQuery)
            val fallbackMatch = findMovieMatch(fallbackResults, candidate)
            if (fallbackMatch != null) {
                putCache(cacheKey, fallbackMatch)
                return@withContext fallbackMatch
            }
        }

        putCache(cacheKey, null)
        null
    }

    suspend fun resolveTv(candidate: MoodSuggestionMediaCandidate): MediaBlock? = withContext(Dispatchers.IO) {
        val primaryQuery = candidate.searchQuery.trim().ifBlank {
            listOfNotNull(candidate.title, candidate.year?.toString()).joinToString(" ")
        }
        if (primaryQuery.isBlank()) return@withContext null

        val cacheKey = "tv:" + MusicCandidateMatcher.normalize(primaryQuery)
        if (memoryCache.containsKey(cacheKey)) {
            return@withContext memoryCache[cacheKey]
        }

        val seasonResults = searchTvSeasons(primaryQuery)
        val match = findTvMatch(seasonResults, candidate)
        if (match != null) {
            putCache(cacheKey, match)
            return@withContext match
        }

        // At most ONE fallback search using entity=tvEpisode
        val fallbackQuery = candidate.title.trim()
        if (fallbackQuery.isNotBlank()) {
            val episodeResults = searchTvEpisodes(fallbackQuery)
            val fallbackMatch = findTvMatch(episodeResults, candidate)
            if (fallbackMatch != null) {
                putCache(cacheKey, fallbackMatch)
                return@withContext fallbackMatch
            }
        }

        putCache(cacheKey, null)
        null
    }

    suspend fun search(query: String, type: MediaType): List<MediaBlock> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        when (type) {
            MediaType.MOVIE -> searchMovies(trimmed).distinctBy { it.providerId ?: it.providerUrl }
            MediaType.TV -> {
                val seasons = searchTvSeasons(trimmed)
                val results = if (seasons.isNotEmpty()) seasons else searchTvEpisodes(trimmed)
                results.distinctBy { it.providerId ?: it.title.lowercase() }
            }
        }
    }

    private fun searchMovies(query: String): List<MediaBlock> = runCatching {
        val term = URLEncoder.encode(query.trim().take(200), "UTF-8")
        val endpoint = "https://itunes.apple.com/search?term=$term&media=movie&entity=movie&country=TR&limit=20"
        val json = executeGet(endpoint) ?: return emptyList()
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()

        buildList {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val title = item.optString("trackName", "").trim()
                val url = secureUrl(item.optString("trackViewUrl")) ?: continue
                if (title.isBlank()) continue

                val artwork = posterArtworkUrl(item.optString("artworkUrl100"))
                val releaseDate = item.optString("releaseDate")
                val year = releaseDate.take(4).toIntOrNull()
                val genre = item.optString("primaryGenreName").takeIf { it.isNotBlank() }
                val description = item.optString("shortDescription").takeIf { it.isNotBlank() }
                    ?: item.optString("longDescription").takeIf { it.isNotBlank() }
                val duration = item.optLong("trackTimeMillis", 0).takeIf { it > 0 }
                val trackId = item.opt("trackId")?.toString()

                add(
                    MediaBlock(
                        type = MediaType.MOVIE,
                        providerId = trackId,
                        title = title,
                        subtitle = year?.let { "$it • Film" } ?: "Film",
                        year = year,
                        genre = genre,
                        description = description,
                        artworkUrl = artwork,
                        provider = "Apple",
                        providerUrl = url,
                        durationMillis = duration
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun searchTvSeasons(query: String): List<MediaBlock> = runCatching {
        val term = URLEncoder.encode(query.trim().take(200), "UTF-8")
        val endpoint = "https://itunes.apple.com/search?term=$term&media=tvShow&entity=tvSeason&country=TR&limit=20"
        val json = executeGet(endpoint) ?: return emptyList()
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()

        buildList {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val rawTitle = item.optString("collectionName", "").trim()
                val url = secureUrl(item.optString("collectionViewUrl")) ?: continue
                if (rawTitle.isBlank()) continue

                val cleanTitle = cleanTvSeriesTitle(rawTitle)
                val artwork = posterArtworkUrl(item.optString("artworkUrl100"))
                val releaseDate = item.optString("releaseDate")
                val year = releaseDate.take(4).toIntOrNull()
                val genre = item.optString("primaryGenreName").takeIf { it.isNotBlank() }
                val description = item.optString("longDescription").takeIf { it.isNotBlank() }
                    ?: item.optString("shortDescription").takeIf { it.isNotBlank() }
                val id = item.opt("collectionId")?.toString()

                add(
                    MediaBlock(
                        type = MediaType.TV,
                        providerId = id,
                        title = cleanTitle.ifBlank { rawTitle },
                        subtitle = year?.let { "$it • Dizi" } ?: "Dizi",
                        year = year,
                        genre = genre,
                        description = description,
                        artworkUrl = artwork,
                        provider = "Apple",
                        providerUrl = url,
                        durationMillis = null
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun searchTvEpisodes(query: String): List<MediaBlock> = runCatching {
        val term = URLEncoder.encode(query.trim().take(200), "UTF-8")
        val endpoint = "https://itunes.apple.com/search?term=$term&media=tvShow&entity=tvEpisode&country=TR&limit=20"
        val json = executeGet(endpoint) ?: return emptyList()
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()

        buildList {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val series = item.optString("collectionName").takeIf { it.isNotBlank() }
                    ?: item.optString("seriesName").takeIf { it.isNotBlank() }
                    ?: continue
                val url = secureUrl(item.optString("collectionViewUrl"))
                    ?: secureUrl(item.optString("trackViewUrl"))
                    ?: continue

                val cleanTitle = cleanTvSeriesTitle(series)
                val artwork = posterArtworkUrl(item.optString("artworkUrl100"))
                val releaseDate = item.optString("releaseDate")
                val year = releaseDate.take(4).toIntOrNull()
                val genre = item.optString("primaryGenreName").takeIf { it.isNotBlank() }
                val id = item.opt("collectionId")?.toString() ?: item.opt("trackId")?.toString()

                add(
                    MediaBlock(
                        type = MediaType.TV,
                        providerId = id,
                        title = cleanTitle.ifBlank { series },
                        subtitle = year?.let { "$it • Dizi" } ?: "Dizi",
                        year = year,
                        genre = genre,
                        description = item.optString("shortDescription").takeIf { it.isNotBlank() },
                        artworkUrl = artwork,
                        provider = "Apple",
                        providerUrl = url,
                        durationMillis = null
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun findMovieMatch(results: List<MediaBlock>, candidate: MoodSuggestionMediaCandidate): MediaBlock? {
        if (results.isEmpty()) return null
        val targetTitle = MusicCandidateMatcher.normalize(candidate.title)
        if (targetTitle.isBlank()) return results.firstOrNull()

        // 1. Exact title + exact/close year
        if (candidate.year != null && candidate.year > 0) {
            val exactWithYear = results.firstOrNull { block ->
                val normTitle = MusicCandidateMatcher.normalize(block.title)
                normTitle == targetTitle && block.year != null && kotlin.math.abs(block.year - candidate.year) <= 1
            }
            if (exactWithYear != null) return exactWithYear
        }

        // 2. Exact title
        val exactTitle = results.firstOrNull { block ->
            MusicCandidateMatcher.normalize(block.title) == targetTitle
        }
        if (exactTitle != null) return exactTitle

        // 3. Strong contains match with year match
        if (candidate.year != null && candidate.year > 0) {
            val containsWithYear = results.firstOrNull { block ->
                val normTitle = MusicCandidateMatcher.normalize(block.title)
                (normTitle.contains(targetTitle) || targetTitle.contains(normTitle)) &&
                        block.year != null && kotlin.math.abs(block.year - candidate.year) <= 1
            }
            if (containsWithYear != null) return containsWithYear
        }

        // 4. Strong contains match unambiguous
        val containsMatch = results.firstOrNull { block ->
            val normTitle = MusicCandidateMatcher.normalize(block.title)
            normTitle.startsWith(targetTitle) || targetTitle.startsWith(normTitle)
        }
        return containsMatch
    }

    private fun findTvMatch(results: List<MediaBlock>, candidate: MoodSuggestionMediaCandidate): MediaBlock? {
        if (results.isEmpty()) return null
        val targetTitle = MusicCandidateMatcher.normalize(candidate.title)
        if (targetTitle.isBlank()) return results.firstOrNull()

        // 1. Exact cleaned series title
        val exact = results.firstOrNull { block ->
            MusicCandidateMatcher.normalize(block.title) == targetTitle
        }
        if (exact != null) return exact

        // 2. Contains match on cleaned series title
        val contains = results.firstOrNull { block ->
            val normTitle = MusicCandidateMatcher.normalize(block.title)
            normTitle.contains(targetTitle) || targetTitle.contains(normTitle)
        }
        return contains
    }

    private fun cleanTvSeriesTitle(raw: String): String {
        val regex = Regex("[,:\\-]\\s*(Season|Sezon|Part|Kısım)\\s*\\d+.*$", RegexOption.IGNORE_CASE)
        return raw.replace(regex, "").trim()
    }

    private fun posterArtworkUrl(raw: String?): String? {
        val secure = secureUrl(raw) ?: return null
        // High-res Apple poster artwork resolution
        return if (secure.contains("100x100bb.")) {
            secure.replace("100x100bb.", "600x600bb.")
        } else {
            secure
        }
    }

    private fun executeGet(urlStr: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun secureUrl(value: String?): String? = when {
        value.isNullOrBlank() -> null
        value.startsWith("https://") -> value
        value.startsWith("http://") -> "https://" + value.removePrefix("http://")
        else -> null
    }

    private fun putCache(key: String, block: MediaBlock?) {
        if (memoryCache.size >= MAX_CACHE_SIZE) {
            memoryCache.clear()
        }
        memoryCache[key] = block
    }
}
