package com.cagri.bigboscagri.data

import com.cagri.bigboscagri.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class MoodSuggestionKind {
    TEXT,
    MESSAGE,
    CALL,
    OUTDOOR,
    ACTIVITY,
    READ,
    MUSIC,
    MOVIE,
    TV;

    companion object {
        fun fromRaw(raw: String?): MoodSuggestionKind? {
            if (raw.isNullOrBlank()) return null
            return try {
                valueOf(raw.trim().uppercase())
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class MoodSuggestionMusicCandidate(
    val artist: String,
    val track: String,
    val searchQuery: String
)

data class MoodSuggestionMediaCandidate(
    val title: String,
    val year: Int? = null,
    val searchQuery: String
)

enum class MoodSuggestionDirection {
    EXPRESS,
    SHARE,
    RECONNECT,
    REMEMBER,
    CREATE_REAL,
    DO,
    DISCOVER,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): MoodSuggestionDirection {
            if (raw.isNullOrBlank()) return UNKNOWN
            return try {
                valueOf(raw.trim().uppercase())
            } catch (_: Exception) {
                UNKNOWN
            }
        }
    }
}

data class MoodSuggestion(
    val kind: MoodSuggestionKind,
    val direction: MoodSuggestionDirection,
    val title: String,
    val subtitle: String,
    val music: MoodSuggestionMusicCandidate? = null,
    val media: MoodSuggestionMediaCandidate? = null
)

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
    const val ENDPOINT = "https://real-whatsapp-4c41.cagrigul1907.workers.dev/api/mood-suggestions"

    suspend fun fetchSuggestions(
        mood: JournalMood,
        details: MoodDetails,
        apiKey: String = BuildConfig.REAL_API_KEY,
        endpoint: String = ENDPOINT
    ): Result<List<MoodSuggestion>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("REAL_API_KEY is not configured"))
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val payload = JSONObject().apply {
                put("source", "mood_tracker")
                put("mood", mood.name)
                val descriptorsArray = JSONArray()
                details.descriptors.forEach { descriptorsArray.put(it) }
                put("descriptors", descriptorsArray)
                val factorsArray = JSONArray()
                details.factors.forEach { factorsArray.put(it) }
                put("factors", factorsArray)
            }.toString()

            connection.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP error $responseCode"))
            }

            val responseBody = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(responseBody)
            if (!json.optBoolean("ok", false)) {
                return@withContext Result.failure(Exception("Backend response not ok"))
            }

            val array = json.optJSONArray("suggestions")
                ?: return@withContext Result.failure(Exception("Missing suggestions in response"))

            if (array.length() != 3) {
                return@withContext Result.failure(Exception("Expected exactly 3 suggestions, received ${array.length()}"))
            }

            val suggestions = ArrayList<MoodSuggestion>(3)
            for (i in 0 until 3) {
                val item = array.optJSONObject(i)
                    ?: return@withContext Result.failure(Exception("Malformed suggestion entry at index $i"))
                val title = item.optString("title", "").trim()
                val subtitle = item.optString("subtitle", "").trim()
                if (title.isBlank()) {
                    return@withContext Result.failure(Exception("Empty title in suggestion at index $i"))
                }
                val direction = MoodSuggestionDirection.fromRaw(item.optString("direction"))
                val rawKind = item.optString("kind")
                val kind = MoodSuggestionKind.fromRaw(rawKind)
                    ?: return@withContext Result.failure(Exception("Unknown or unsupported suggestion kind: '$rawKind' at index $i"))

                val musicObj = item.optJSONObject("music")
                val music = if (kind == MoodSuggestionKind.MUSIC && musicObj != null) {
                    val mArtist = musicObj.optString("artist", "").trim()
                    val mTrack = musicObj.optString("track", "").trim()
                    val mQuery = musicObj.optString("searchQuery", "").trim().ifBlank {
                        listOf(mArtist, mTrack).filter { it.isNotBlank() }.joinToString(" ")
                    }
                    if (mArtist.isNotBlank() || mTrack.isNotBlank() || mQuery.isNotBlank()) {
                        MoodSuggestionMusicCandidate(
                            artist = mArtist,
                            track = mTrack,
                            searchQuery = mQuery
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }

                val mediaObj = item.optJSONObject("media")
                val media = if ((kind == MoodSuggestionKind.MOVIE || kind == MoodSuggestionKind.TV) && mediaObj != null) {
                    val mTitle = mediaObj.optString("title", "").trim()
                    val rawYear = if (mediaObj.has("year") && !mediaObj.isNull("year")) mediaObj.optInt("year", 0) else 0
                    val mYear = if (rawYear > 0) rawYear else null
                    val mQuery = mediaObj.optString("searchQuery", "").trim().ifBlank {
                        listOfNotNull(mTitle, mYear?.toString()).joinToString(" ")
                    }
                    if (mTitle.isNotBlank() || mQuery.isNotBlank()) {
                        MoodSuggestionMediaCandidate(
                            title = mTitle,
                            year = mYear,
                            searchQuery = mQuery
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }

                suggestions.add(
                    MoodSuggestion(
                        kind = kind,
                        direction = direction,
                        title = title,
                        subtitle = subtitle,
                        music = music,
                        media = media
                    )
                )
            }

            Result.success(suggestions)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {}
        }
    }
}
