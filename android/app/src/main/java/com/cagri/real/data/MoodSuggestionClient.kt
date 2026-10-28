package com.cagri.real.data

import com.cagri.real.network.HttpTransport
import com.cagri.real.network.WorkerClient
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.network.MoodSuggestionRequest
import com.cagri.real.platform.android.network.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar

object MusicCandidateMatcher {
    fun normalize(input: String): String {
        val lower = input.lowercase()
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            when (ch) {
                'ı', 'i', 'İ', 'I' -> sb.append('i')
                'ç' -> sb.append('c')
                'ğ' -> sb.append('g')
                'ö' -> sb.append('o')
                'ş' -> sb.append('s')
                'ü' -> sb.append('u')
                'é', 'è', 'ê', 'ë' -> sb.append('e')
                'á', 'à', 'â', 'ä', 'ã' -> sb.append('a')
                'ó', 'ò', 'ô', 'ö', 'õ' -> sb.append('o')
                'ú', 'ù', 'û', 'ü' -> sb.append('u')
                in 'a'..'z', in '0'..'9' -> sb.append(ch)
                ' ', '-', '_', '\'', '’' -> sb.append(' ')
                else -> {}
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun findMatch(results: List<MusicBlock>, candidate: MoodSuggestionMusicCandidate): MusicBlock? {
        if (results.isEmpty()) return null
        val targetArtist = normalize(candidate.artist)
        val targetTrack = normalize(candidate.track)

        if (targetArtist.isBlank() && targetTrack.isBlank()) {
            return results.firstOrNull()
        }

        // 1. Exact normalized artist + exact normalized track
        val exactMatch = results.firstOrNull { block ->
            val blockArtist = normalize(block.artist)
            val blockTrack = normalize(block.title)
            blockArtist == targetArtist && blockTrack == targetTrack
        }
        if (exactMatch != null) return exactMatch

        // 2. Strong match: exact artist + contains track (or exact track + contains artist)
        val strongMatch = results.firstOrNull { block ->
            val blockArtist = normalize(block.artist)
            val blockTrack = normalize(block.title)
            (blockArtist == targetArtist && (blockTrack.contains(targetTrack) || targetTrack.contains(blockTrack))) ||
            (blockTrack == targetTrack && (blockArtist.contains(targetArtist) || targetArtist.contains(blockArtist)))
        }
        if (strongMatch != null) return strongMatch

        // 3. Relaxed contains match on both
        val containsMatch = results.firstOrNull { block ->
            val blockArtist = normalize(block.artist)
            val blockTrack = normalize(block.title)
            val artistOk = targetArtist.isBlank() || blockArtist.contains(targetArtist) || targetArtist.contains(blockArtist)
            val trackOk = targetTrack.isBlank() || blockTrack.contains(targetTrack) || targetTrack.contains(blockTrack)
            artistOk && trackOk
        }
        if (containsMatch != null) return containsMatch

        return null
    }

    suspend fun resolve(candidate: MoodSuggestionMusicCandidate): MusicBlock? {
        val primaryQuery = candidate.searchQuery.trim().ifBlank {
            "${candidate.artist} ${candidate.track}".trim()
        }
        if (primaryQuery.isBlank()) return null

        val firstResults = runCatching { MusicCatalog.search(primaryQuery) }.getOrDefault(emptyList())
        val firstMatch = findMatch(firstResults, candidate)
        if (firstMatch != null) return firstMatch

        val fallbackQuery = "${candidate.artist} ${candidate.track}".trim()
        if (fallbackQuery.isNotBlank() && !fallbackQuery.equals(primaryQuery, ignoreCase = true)) {
            val fallbackResults = runCatching { MusicCatalog.search(fallbackQuery) }.getOrDefault(emptyList())
            val fallbackMatch = findMatch(fallbackResults, candidate)
            if (fallbackMatch != null) return fallbackMatch
        }

        return null
    }
}

object MoodSuggestionClient {
    const val ENDPOINT = "https://real-backend-staging.cagrigul1907.workers.dev/api/mood-suggestions"

    suspend fun fetchSuggestions(
        mood: JournalMood,
        details: MoodDetails,
        endpoint: String = ENDPOINT,
        transport: HttpTransport = AndroidBackend.workerTransport,
        excludeMusic: List<String> = emptyList(),
        excludeMedia: List<String> = emptyList(),
        excludeIdeas: List<String> = emptyList(),
        language: String = if (com.cagri.real.i18n.I18n.isEnglish) "en" else "tr"
    ): Result<List<MoodSuggestion>> = requestSuggestions(
        mood = mood,
        details = details,
        endpoint = endpoint,
        transport = transport,
        replaceKind = null,
        exclude = emptyList(),
        excludeMusic = excludeMusic,
        excludeMedia = excludeMedia,
        excludeIdeas = excludeIdeas,
        language = language
    ).mapCatching { suggestions ->
        if (suggestions.size != 4) {
            throw Exception("Expected exactly 4 suggestions, received ${suggestions.size}")
        }
        suggestions
    }

    suspend fun fetchReplacement(
        mood: JournalMood,
        details: MoodDetails,
        kind: MoodSuggestionKind,
        exclude: List<String>,
        endpoint: String = ENDPOINT,
        transport: HttpTransport = AndroidBackend.workerTransport,
        excludeMusic: List<String> = emptyList(),
        excludeMedia: List<String> = emptyList(),
        excludeIdeas: List<String> = emptyList(),
        language: String = if (com.cagri.real.i18n.I18n.isEnglish) "en" else "tr"
    ): Result<MoodSuggestion> = requestSuggestions(
        mood = mood,
        details = details,
        endpoint = endpoint,
        transport = transport,
        replaceKind = kind,
        exclude = exclude,
        excludeMusic = excludeMusic,
        excludeMedia = excludeMedia,
        excludeIdeas = excludeIdeas,
        language = language
    ).mapCatching { suggestions ->
        val replacement = suggestions.singleOrNull()
            ?: throw Exception("Expected exactly 1 replacement suggestion, received ${suggestions.size}")
        if (replacement.kind != kind) {
            throw Exception("Replacement kind mismatch: expected $kind, received ${replacement.kind}")
        }
        replacement
    }

    private suspend fun requestSuggestions(
        mood: JournalMood,
        details: MoodDetails,
        endpoint: String,
        replaceKind: MoodSuggestionKind?,
        exclude: List<String>,
        transport: HttpTransport,
        excludeMusic: List<String>,
        excludeMedia: List<String>,
        excludeIdeas: List<String>,
        language: String
    ): Result<List<MoodSuggestion>> = withContext(Dispatchers.IO) {
        try {
            val localHour = runCatching { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }.getOrNull()
            val payload = MoodSuggestionRequest(
                mood = mood.name,
                descriptors = details.descriptors,
                factors = details.factors,
                replaceKind = replaceKind,
                exclude = exclude,
                excludeMusic = excludeMusic,
                excludeMedia = excludeMedia,
                excludeIdeas = excludeIdeas,
                scope = details.scope.persistKey,
                localHour = localHour,
                language = language
            ).toJson()
            val response = WorkerClient(transport).postJson(endpoint, payload, 15000, 18000)
            val responseCode = response.statusCode
            val responseBody = response.body
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception(com.cagri.real.ai.aiServiceFailureMessage(responseCode,
                    runCatching { JSONObject(responseBody).optString("error") }.getOrDefault(""))))
            }

            val json = JSONObject(responseBody)
            if (!json.optBoolean("ok", false)) {
                return@withContext Result.failure(Exception(json.optString("error", "Backend response not ok")))
            }
            val array = json.optJSONArray("suggestions")
                ?: return@withContext Result.failure(Exception("Missing suggestions in response"))

            val suggestions = ArrayList<MoodSuggestion>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                    ?: return@withContext Result.failure(Exception("Malformed suggestion entry at index $i"))
                suggestions += parseSuggestion(item, i)
            }
            Result.success(suggestions)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSuggestion(item: JSONObject, index: Int): MoodSuggestion {
        val title = item.optString("title", "").trim()
        val subtitle = item.optString("subtitle", "").trim()
        if (title.isBlank()) throw Exception("Empty title in suggestion at index $index")
        val direction = MoodSuggestionDirection.fromRaw(item.optString("direction"))
        val rawKind = item.optString("kind")
        val kind = MoodSuggestionKind.fromRaw(rawKind)
            ?: throw Exception("Unknown or unsupported suggestion kind: '$rawKind' at index $index")

        val musicObj = item.optJSONObject("music")
        val music = if (kind == MoodSuggestionKind.MUSIC && musicObj != null) {
            val artist = musicObj.optString("artist", "").trim()
            val track = musicObj.optString("track", "").trim()
            val query = musicObj.optString("searchQuery", "").trim().ifBlank {
                listOf(artist, track).filter { it.isNotBlank() }.joinToString(" ")
            }
            if (artist.isNotBlank() || track.isNotBlank() || query.isNotBlank()) {
                MoodSuggestionMusicCandidate(artist = artist, track = track, searchQuery = query)
            } else null
        } else null

        val mediaObj = item.optJSONObject("media")
        val media = if ((kind == MoodSuggestionKind.MOVIE || kind == MoodSuggestionKind.TV) && mediaObj != null) {
            val mediaTitle = mediaObj.optString("title", "").trim()
            val rawYear = if (mediaObj.has("year") && !mediaObj.isNull("year")) mediaObj.optInt("year", 0) else 0
            val mediaYear = rawYear.takeIf { it > 0 }
            val query = mediaObj.optString("searchQuery", "").trim().ifBlank {
                listOfNotNull(mediaTitle.takeIf { it.isNotBlank() }, mediaYear?.toString()).joinToString(" ")
            }
            if (mediaTitle.isNotBlank() || query.isNotBlank()) {
                MoodSuggestionMediaCandidate(title = mediaTitle, year = mediaYear, searchQuery = query)
            } else null
        } else null

        return MoodSuggestion(
            kind = kind,
            direction = direction,
            title = title,
            subtitle = subtitle,
            music = music,
            media = media
        )
    }
}
