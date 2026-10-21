package com.cagri.real.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified Media Catalog entry point.
 *
 * Provider Architecture:
 * - TV / Series: Direct to public TVmaze API (no API key, attribution: "TVmaze")
 * - Movie: REAL Cloudflare Worker -> TheTVDB V4 (authenticated via Firebase ID token and App Check, attribution: "TheTVDB")
 * - Legacy Apple-backed MediaBlocks continue to decode and render properly.
 */
object MediaCatalog {
    private val memoryCache = ConcurrentHashMap<String, MediaBlock>()
    private val negativeResolveCache = ConcurrentHashMap.newKeySet<String>()
    private val rawSearchCache = ConcurrentHashMap<String, List<MediaBlock>>()
    private const val MAX_CACHE_SIZE = 120

    suspend fun search(query: String, type: MediaType): List<MediaBlock> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        when (type) {
            MediaType.TV -> {
                val norm = MediaTitleMatcher.normalize(trimmed)
                val cacheKey = "tvmaze:tv:$norm"
                rawSearchCache[cacheKey]?.let { return@withContext it }

                // Network errors throw IOException, which caller catches without caching
                val results = TvMazeCatalog.searchShows(trimmed)
                putRawCache(cacheKey, results)
                results
            }
            MediaType.MOVIE -> {
                val norm = MediaTitleMatcher.normalize(trimmed)
                val cacheKey = "worker:movie:$norm"
                rawSearchCache[cacheKey]?.let { return@withContext it }

                // Network errors throw IOException, which caller catches without caching
                val results = RealMediaCatalogClient.searchMovies(trimmed)
                putRawCache(cacheKey, results)
                results
            }
        }
    }

    suspend fun resolveTv(candidate: MoodSuggestionMediaCandidate): MediaBlock? = withContext(Dispatchers.IO) {
        val primaryQuery = candidate.searchQuery.trim().ifBlank { candidate.title.trim() }
        if (primaryQuery.isBlank()) return@withContext null

        val norm = MediaTitleMatcher.normalize(primaryQuery)
        val cacheKey = "tvmaze:resolve:tv:$norm:${candidate.year ?: 0}"
        memoryCache[cacheKey]?.let { return@withContext it }
        if (negativeResolveCache.contains(cacheKey)) return@withContext null

        try {
            val resolved = TvMazeCatalog.resolveCandidate(candidate)
            putCache(cacheKey, resolved)
            resolved
        } catch (_: Exception) {
            // Do not cache network/service failures as negative results
            null
        }
    }

    suspend fun resolveMovie(candidate: MoodSuggestionMediaCandidate): MediaBlock? = withContext(Dispatchers.IO) {
        val primaryQuery = candidate.searchQuery.trim().ifBlank { candidate.title.trim() }
        if (primaryQuery.isBlank()) return@withContext null

        val norm = MediaTitleMatcher.normalize(primaryQuery)
        val cacheKey = "worker:resolve:movie:$norm:${candidate.year ?: 0}"
        memoryCache[cacheKey]?.let { return@withContext it }
        if (negativeResolveCache.contains(cacheKey)) return@withContext null

        try {
            val resolved = RealMediaCatalogClient.resolveCandidate(candidate)
            putCache(cacheKey, resolved)
            resolved
        } catch (_: Exception) {
            // Do not cache network/service failures as negative results
            null
        }
    }

    private fun putCache(key: String, block: MediaBlock?) {
        if (memoryCache.size + negativeResolveCache.size >= MAX_CACHE_SIZE) {
            memoryCache.clear()
            negativeResolveCache.clear()
        }
        if (block != null) {
            negativeResolveCache.remove(key)
            memoryCache[key] = block
        } else {
            memoryCache.remove(key)
            negativeResolveCache.add(key)
        }
    }

    private fun putRawCache(key: String, list: List<MediaBlock>) {
        if (rawSearchCache.size >= MAX_CACHE_SIZE) {
            rawSearchCache.clear()
        }
        rawSearchCache[key] = list
    }
}
