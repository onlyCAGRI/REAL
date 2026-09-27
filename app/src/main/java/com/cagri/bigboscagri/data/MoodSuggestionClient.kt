package com.cagri.bigboscagri.data

import com.cagri.bigboscagri.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class MoodSuggestionDirection {
    EXPRESS,
    SHARE,
    RECONNECT,
    REMEMBER,
    CREATE_REAL,
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
    val direction: MoodSuggestionDirection,
    val title: String,
    val subtitle: String
)

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
                suggestions.add(
                    MoodSuggestion(
                        direction = direction,
                        title = title,
                        subtitle = subtitle
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
