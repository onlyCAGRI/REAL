package com.cagri.real.speech

import com.cagri.real.network.HttpTransport
import com.cagri.real.network.WorkerClient
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.network.VoiceIntentRequest
import com.cagri.real.platform.android.network.toJson
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.MoodSuggestionMediaCandidate
import com.cagri.real.data.MoodSuggestionMusicCandidate
import com.cagri.real.data.RecurrenceRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Android compatibility aliases preserve all existing call sites.
typealias GeminiVoiceIntent = VoiceIntent<LocalDate, LocalTime>
typealias GeminiVoiceParseResult = VoiceParseResult<LocalDate, LocalTime>
typealias GeminiVoiceEdit = VoiceEditPatch<LocalDate, LocalTime>

object GeminiVoiceIntentClient {
    const val ENDPOINT = "https://real-backend-staging.cagrigul1907.workers.dev/api/voice-intent"

    suspend fun parse(
        transcript: String,
        categories: List<CustomCategory>,
        transcriptAlternatives: List<String> = emptyList(),
        endpoint: String = ENDPOINT,
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
        transport: HttpTransport = AndroidBackend.workerTransport,
        lastCard: VoiceLastCard? = null,
        recentCards: List<VoiceRecentCard> = emptyList()
    ): Result<GeminiVoiceParseResult> = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Transcript is blank"))
        }

        try {
            val payload = VoiceIntentRequest(
                transcript = transcript,
                transcriptAlternatives = transcriptAlternatives,
                nowIsoOffset = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                timezone = now.zone.id,
                locale = Locale.getDefault().toLanguageTag(),
                categories = categories.map { it.name },
                lastCard = lastCard,
                recentCards = recentCards
            ).toJson()
            val response = WorkerClient(transport).postJson(endpoint, payload, 5_000, 12_000)
            val responseCode = response.statusCode
            val responseBody = response.body

            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception(com.cagri.real.ai.aiServiceFailureMessage(responseCode,
                    runCatching { JSONObject(responseBody).optString("error") }.getOrDefault(""))))
            }

            val root = JSONObject(responseBody)
            if (!root.optBoolean("ok", false)) {
                return@withContext Result.failure(Exception(root.optString("error", "Backend response not ok")))
            }

            val intents = buildList {
                val array = root.optJSONArray("intents")
                if (array != null) {
                    require(array.length() <= 20) { "Tek seferde en fazla 20 kart oluşturulabilir. İsteği bölerek söyle." }
                    for (index in 0 until array.length()) {
                        array.optJSONObject(index)?.let { add(parseIntentObject(it, transcript, now.toLocalDate())) }
                    }
                } else {
                    root.optJSONObject("intent")?.let { add(parseIntentObject(it, transcript, now.toLocalDate())) }
                }
            }

            val categoryRequest = root.optJSONObject("newCategory")?.let { obj ->
                if (!obj.optBoolean("create", false)) return@let null
                val name = obj.optString("name", "").trim().take(40)
                if (name.isBlank()) return@let null
                VoiceNewCategoryRequest(
                    name = name,
                    iconKey = obj.optString("iconKey", "BOOKMARK").trim().uppercase(Locale.ROOT).ifBlank { "BOOKMARK" },
                    colorName = obj.optString("colorName", "").trim().takeIf { it.isNotBlank() }
                )
            }

            val canTarget = lastCard != null || recentCards.isNotEmpty()
            val editPatch = if (canTarget) root.optJSONObject("edit")?.let { parseEditObject(it) } else null
            val rawAction = root.optJSONObject("action")?.let { parseActionObject(it) }
            val allowedIds = recentCards.map { it.id }.toSet()
            val action = rawAction?.let { candidate ->
                if (candidate.targetId != null && candidate.targetId !in allowedIds) VoiceAction(VoiceActionType.UNRESOLVED)
                else if (candidate.bulk?.targetIds?.any { it !in allowedIds } == true) VoiceAction(VoiceActionType.UNRESOLVED)
                else candidate
            }

            if (intents.isEmpty() && categoryRequest == null && editPatch == null && action == null) {
                return@withContext Result.failure(Exception("Missing voice intent"))
            }

            Result.success(
                GeminiVoiceParseResult(intents = intents, newCategory = categoryRequest, edit = editPatch, action = action,
                    heard = root.optJSONObject("heard")?.let { parseHeardObject(it, transcript) })
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            Result.failure(error)
        }
    }

    /** Unsure words are kept only when they really occur in the transcript, so they can be underlined. */
    internal fun parseHeardObject(obj: JSONObject, transcript: String): VoiceHeard {
        val lower = transcript.lowercase(Locale.forLanguageTag("tr"))
        val unsure = buildList<VoiceUnsureWord> {
            val array = obj.optJSONArray("unsure")
            for (index in 0 until (array?.length() ?: 0)) {
                val entry = array?.optJSONObject(index) ?: continue
                val heard = entry.optString("heard", "").trim().take(80)
                if (heard.isBlank() || !lower.contains(heard.lowercase(Locale.forLanguageTag("tr")))) continue
                if (any { it.heard.equals(heard, ignoreCase = true) }) continue
                add(VoiceUnsureWord(heard, entry.optString("meant", "").trim().take(80).takeIf { it.isNotBlank() && !it.equals(heard, true) }))
                if (size >= 6) break
            }
        }
        return VoiceHeard(
            understood = obj.optString("understood", "").trim().take(2000),
            unsure = unsure,
            confident = unsure.isEmpty() && obj.optBoolean("confident", true)
        )
    }

    /** MON..SUN -> bit0..bit6 (same layout as DayOfWeek.value - 1). */
    internal fun weekdaysMaskFrom(array: JSONArray?): Int {
        var mask = 0
        for (index in 0 until (array?.length() ?: 0)) {
            val bit = when (array?.optString(index)?.trim()?.uppercase(Locale.ROOT)) {
                "MON" -> 0
                "TUE" -> 1
                "WED" -> 2
                "THU" -> 3
                "FRI" -> 4
                "SAT" -> 5
                "SUN" -> 6
                else -> -1
            }
            if (bit >= 0) mask = mask or (1 shl bit)
        }
        return mask
    }

    private fun parseIsoDate(raw: String?): LocalDate? = raw?.trim()?.takeIf { it.isNotEmpty() }?.let {
        runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }

    /** Returns null for action NONE (plain card creation). targetId is a decimal string chosen by the backend. */
    internal fun parseActionObject(obj: JSONObject): VoiceAction? {
        val type = VoiceActionType.fromRaw(obj.optString("type", "NONE"))
        if (type == VoiceActionType.NONE) return null
        val targetId = obj.optString("targetId", "").trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
        val items = buildList {
            val array = obj.optJSONArray("checklistItems")
            for (index in 0 until (array?.length() ?: 0)) {
                array?.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }.take(30)
        val bulk = if (type == VoiceActionType.BULK) obj.optJSONObject("bulk")?.let { parseBulkObject(it) } else null
        val occurrenceEpochDay = if (type == VoiceActionType.SKIP_OCCURRENCE) parseIsoDate(obj.optString("occurrenceDate", ""))?.toEpochDay() else null
        return VoiceAction(type = type, targetId = targetId, checklistItems = items, bulk = bulk, occurrenceEpochDay = occurrenceEpochDay,
            clarification = obj.optString("clarification", "").trim().take(180).takeIf { it.isNotBlank() })
    }

    internal fun parseBulkObject(obj: JSONObject): VoiceBulk? {
        val operation = VoiceBulkOperation.fromRaw(obj.optString("operation", "NONE"))
        val scope = VoiceBulkScope.fromRaw(obj.optString("scope", "NONE"))
        if (operation == VoiceBulkOperation.NONE || scope == VoiceBulkScope.NONE) return null
        val ids = buildList {
            val array = obj.optJSONArray("targetIds")
            for (index in 0 until (array?.length() ?: 0)) {
                array?.optString(index)?.trim()?.toLongOrNull()?.let(::add)
            }
        }.distinct().take(15)
        return VoiceBulk(
            operation = operation,
            scope = scope,
            date = obj.optString("date", "").trim(),
            categoryName = obj.optString("categoryName", "").trim(),
            targetIds = ids,
            shiftDays = obj.optInt("shiftDays", 0).coerceIn(0, 365),
            newDate = obj.optString("newDate", "").trim()
        )
    }

    internal fun parseEditObject(obj: JSONObject): GeminiVoiceEdit? {
        if (!obj.optBoolean("isEdit", false)) return null
        val time = obj.optString("time", "").trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
        }
        val recurrence = when (obj.optString("recurrence", "KEEP").uppercase(Locale.ROOT)) {
            "DAILY" -> RecurrenceRule.DAILY
            "WEEKLY" -> RecurrenceRule.WEEKLY
            "MONTHLY" -> RecurrenceRule.MONTHLY
            "YEARLY" -> RecurrenceRule.YEARLY
            "NONE" -> RecurrenceRule.NONE
            else -> null
        }
        val clearRepeat = obj.optBoolean("clearRepeat", false) || recurrence == RecurrenceRule.NONE
        return GeminiVoiceEdit(
            newTitle = obj.optString("newTitle", "").trim().takeIf { it.isNotBlank() },
            date = parseIsoDate(obj.optString("date", "")),
            time = time,
            recurrence = recurrence?.takeIf { it != RecurrenceRule.NONE },
            weekdaysMask = weekdaysMaskFrom(obj.optJSONArray("weekdays")),
            clearRepeat = clearRepeat,
            repeatUntil = parseIsoDate(obj.optString("repeatUntil", "")),
            locationName = obj.optString("locationName", "").trim().takeIf { it.isNotBlank() },
            clearLocation = obj.optBoolean("clearLocation", false),
            categoryName = obj.optString("categoryName", "").trim().takeIf { it.isNotBlank() }
        )
    }

    internal fun parseIntentObject(obj: JSONObject, fallbackTranscript: String, today: LocalDate = LocalDate.now()): GeminiVoiceIntent {
        val title = obj.optString("title", "").trim().ifBlank { fallbackTranscript.trim() }
        val weekdaysMask = weekdaysMaskFrom(obj.optJSONArray("weekdays"))
        val repeatUntil = parseIsoDate(obj.optString("repeatUntil", ""))
        val parsedDate = obj.optString("date", "").trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        }
        // "her pazartesi ... 7'de" with no explicit start day: start searching from today.
        val date = parsedDate ?: if (weekdaysMask != 0) today else null
        val time = obj.optString("time", "").trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
        }
        val recurrence = if (weekdaysMask != 0) RecurrenceRule.WEEKLY else when (obj.optString("recurrence", "NONE").uppercase(Locale.ROOT)) {
            "DAILY" -> RecurrenceRule.DAILY
            "WEEKLY" -> RecurrenceRule.WEEKLY
            "MONTHLY" -> RecurrenceRule.MONTHLY
            "YEARLY" -> RecurrenceRule.YEARLY
            else -> RecurrenceRule.NONE
        }
        val timeOfDay = when (obj.optString("timeOfDay", "NONE").uppercase(Locale.ROOT)) {
            "MORNING" -> TimeOfDay.Morning
            "NOON" -> TimeOfDay.Noon
            "EVENING" -> TimeOfDay.Evening
            "NIGHT" -> TimeOfDay.Night
            else -> null
        }
        val durationMinutes = obj.optInt("durationMinutes", 0).takeIf { it in 1..1440 }
        val peopleArray = obj.optJSONArray("people")
        val people = buildList {
            for (index in 0 until (peopleArray?.length() ?: 0)) {
                peopleArray?.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct().take(8)
        val locationName = obj.optString("locationName", "").trim().takeIf { it.isNotBlank() }
        val categoryName = obj.optString("categoryName", "").trim().takeIf { it.isNotBlank() }
        val checklistArray = obj.optJSONArray("checklistItems")
        val checklistItems = buildList {
            for (index in 0 until (checklistArray?.length() ?: 0)) {
                checklistArray?.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }.take(30)
        val isChecklist = obj.optBoolean("isChecklist", false) && checklistItems.isNotEmpty()

        val requestedAttachmentKind = VoiceAttachmentKind.fromRaw(obj.optString("attachKind", "NONE"))
        val musicObj = obj.optJSONObject("music")
        val music = if (requestedAttachmentKind == VoiceAttachmentKind.MUSIC && musicObj != null) {
            val artist = musicObj.optString("artist", "").trim()
            val track = musicObj.optString("track", "").trim()
            val query = musicObj.optString("searchQuery", "").trim().ifBlank {
                listOf(artist, track).filter { it.isNotBlank() }.joinToString(" ")
            }
            if (track.isNotBlank() || query.isNotBlank()) {
                MoodSuggestionMusicCandidate(artist = artist, track = track, searchQuery = query)
            } else null
        } else null

        val mediaObj = obj.optJSONObject("media")
        val media = if (
            requestedAttachmentKind == VoiceAttachmentKind.MOVIE ||
            requestedAttachmentKind == VoiceAttachmentKind.TV
        ) {
            mediaObj?.let {
                val mediaTitle = it.optString("title", "").trim()
                val rawYear = if (it.has("year") && !it.isNull("year")) it.optInt("year", 0) else 0
                val mediaYear = rawYear.takeIf { year -> year > 0 }
                val query = it.optString("searchQuery", "").trim().ifBlank {
                    listOfNotNull(mediaTitle.takeIf { it.isNotBlank() }, mediaYear?.toString()).joinToString(" ")
                }
                if (mediaTitle.isNotBlank() || query.isNotBlank()) {
                    MoodSuggestionMediaCandidate(title = mediaTitle, year = mediaYear, searchQuery = query)
                } else null
            }
        } else null

        require(requestedAttachmentKind == VoiceAttachmentKind.NONE || music != null || media != null) {
            "İçerik için katalog adayı eksik; düz metin olarak kaydedilmedi."
        }
        val attachmentKind = when (requestedAttachmentKind) {
            VoiceAttachmentKind.MUSIC -> if (music != null) VoiceAttachmentKind.MUSIC else VoiceAttachmentKind.NONE
            VoiceAttachmentKind.MOVIE -> if (media != null) VoiceAttachmentKind.MOVIE else VoiceAttachmentKind.NONE
            VoiceAttachmentKind.TV -> if (media != null) VoiceAttachmentKind.TV else VoiceAttachmentKind.NONE
            VoiceAttachmentKind.NONE -> VoiceAttachmentKind.NONE
        }

        return GeminiVoiceIntent(
            title = title,
            date = date,
            time = time,
            recurrence = recurrence,
            hasSchedule = obj.optBoolean("hasSchedule", false) || date != null || time != null || recurrence != RecurrenceRule.NONE,
            requiresTimeSelection = obj.optBoolean("requiresTimeSelection", false),
            timeOfDay = timeOfDay,
            durationMinutes = durationMinutes,
            people = people,
            locationName = locationName,
            categoryName = categoryName,
            isChecklist = isChecklist,
            checklistItems = checklistItems,
            attachmentKind = attachmentKind,
            music = music,
            media = media,
            weekdaysMask = weekdaysMask,
            repeatUntil = repeatUntil
        )
    }
}
