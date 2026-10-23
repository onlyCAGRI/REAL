package com.cagri.real.data

import org.json.JSONArray
import org.json.JSONObject
import com.cagri.real.ui.components.ReminderColorChoices

/** The existing persistence codec, shared by repository and Pano card snapshots. */
object ReminderJson {
    fun decode(raw: String): List<ReminderItem> {
        return run {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val isChecklist = item.optBoolean("isChecklist", false)
                    val checklistItems = if (item.has("checklistItems")) {
                        val itemsArray = item.optJSONArray("checklistItems")
                        if (itemsArray != null) {
                            buildList {
                                for (i in 0 until itemsArray.length()) {
                                    val obj = itemsArray.getJSONObject(i)
                                    add(
                                        ChecklistItem(
                                            id = obj.getLong("id"),
                                            text = obj.getString("text"),
                                            completedAt = obj.optionalTimestamp("completedAt")
                                        )
                                    )
                                }
                            }
                        } else emptyList()
                    } else emptyList()
                    val attachments = AttachmentJson.decodeArray(item.optJSONArray("attachments"))
                    val voiceRecordings = VoiceRecordingJson.decodeArray(item.optJSONArray("voiceRecordings"))
                    val location = item.optJSONObject("location")?.let { place ->
                        val name = place.optString("placeName", "")
                        val latitude = place.optDouble("latitude", Double.NaN)
                        val longitude = place.optDouble("longitude", Double.NaN)
                        val mapsUrl = place.optString("mapsUrl", "").takeIf { it.startsWith("https://") }
                        val hasCoordinates = latitude.isFinite() && longitude.isFinite() &&
                            latitude in -90.0..90.0 && longitude in -180.0..180.0
                        if (name.isNotBlank() && (hasCoordinates || mapsUrl != null)) ReminderLocation(
                            placeId = if (place.isNull("placeId")) null else place.optString("placeId", "").takeIf { it.isNotBlank() },
                            placeName = name,
                            address = place.optString("address", ""),
                            latitude = if (hasCoordinates) latitude else null,
                            longitude = if (hasCoordinates) longitude else null,
                            mapsUrl = mapsUrl,
                            approximateCoordinates = place.optBoolean("approximateCoordinates", false),
                            mapZoom = place.optDouble("mapZoom", Double.NaN).takeIf { it.isFinite() }?.coerceIn(1.0, 19.0)
                        ) else null
                    }
                    val standaloneMood = if (item.has("mood") && !item.isNull("mood")) {
                        JournalMood.fromKey(item.optString("mood", "").takeIf { it.isNotBlank() })
                    } else null
                    val moodDetails = item.optJSONObject("moodDetails")?.let { details ->
                        fun stringList(name: String): List<String> {
                            val arr = details.optJSONArray(name) ?: return emptyList()
                            return (0 until arr.length())
                                .mapNotNull { arr.optString(it, "").takeIf { value -> value.isNotBlank() } }
                                .distinct()
                        }
                        MoodDetails(
                            scope = MoodScope.fromKey(details.optString("scope", "")) ?: MoodScope.DAILY,
                            descriptors = stringList("descriptors"),
                            energy = MoodEnergy.fromKey(details.optString("energy", "")),
                            factors = stringList("factors"),
                            loggedAt = details.optLong("loggedAt", item.optLong("updatedAt", item.getLong("createdAt")))
                        )
                    }
                    val journal = if (item.has("journal") && !item.isNull("journal")) {
                        val jObj = item.getJSONObject("journal")
                        val moodKey = if (jObj.has("mood") && !jObj.isNull("mood")) jObj.getString("mood") else null
                        val specificMoodKey = if (jObj.has("specificMood") && !jObj.isNull("specificMood")) jObj.getString("specificMood") else null
                        ReminderJournal(
                            
                            mood = JournalMood.fromKey(specificMoodKey) ?: JournalMood.fromKey(moodKey),
                            text = jObj.optString("text", ""),
                            createdAt = jObj.getLong("createdAt"),
                            updatedAt = jObj.optLong("updatedAt", jObj.getLong("createdAt"))
                        )
                    } else null
                    add(
                        ReminderItem(
                            id = item.getLong("id"),
                            text = item.getString("text"),
                            createdAt = item.getLong("createdAt"),
                            updatedAt = item.optLong("updatedAt", item.getLong("createdAt")),
                            completedAt = item.optionalTimestamp("completedAt"),
                            isImportant = item.optBoolean("isImportant", false),
                            alertAt = item.optionalTimestamp("alertAt"),
                            plannedDateEpochDay = item.optionalEpochDay("plannedDateEpochDay"),
                            quick24HourUntilAt = item.optionalTimestamp("quick24HourUntilAt"),
                            notifiedAlertAt = item.optionalTimestamp("notifiedAlertAt"),
                            accentStart = item.optionalAccent("accentStart"),
                            accentEnd = item.optionalAccent("accentEnd"),
                            repeatRule = RecurrenceRule.fromStorage(if (item.has("repeatRule")) item.optString("repeatRule") else null),
                            anchorAlertAt = item.optionalTimestamp("anchorAlertAt") ?: item.optionalTimestamp("alertAt"),
                            repeatWeekdaysMask = item.optInt("repeatWeekdaysMask", 0) and 0x7F,
                            repeatUntilEpochDay = item.optionalEpochDay("repeatUntilEpochDay"),
                            repeatSkippedEpochDays = item.optionalEpochDays("repeatSkippedEpochDays"),
                            endAt = item.optionalTimestamp("endAt"),
                            sharedCardId = item.optString("sharedCardId", "").takeIf { it.isNotBlank() && it != "null" },
                            sharedAuthorUid = item.optString("sharedAuthorUid", "").takeIf { it.isNotBlank() && it != "null" },
                            sharedAuthorName = item.optString("sharedAuthorName", "").takeIf { it.isNotBlank() && it != "null" },
                            earlyAlertMinutes = item.optInt("earlyAlertMinutes", 0).takeIf { it in 1..MAX_EARLY_ALERT_MINUTES },
                            isAlarm = item.optBoolean("isAlarm", false),
                            locationTrigger = item.optJSONObject("locationTrigger")?.let { t ->
                                if (!t.has("latitude") || !t.has("longitude")) null
                                else LocationTrigger(
                                    placeName = t.optString("placeName", ""),
                                    latitude = t.getDouble("latitude"),
                                    longitude = t.getDouble("longitude"),
                                    radiusMeters = t.optInt("radiusMeters", LocationTrigger.DEFAULT_RADIUS).coerceIn(50, 2000),
                                    onExit = t.optBoolean("onExit", false)
                                )
                            },
                            isDivider = item.optBoolean("isDivider", false),
                            completionNote = item.optString("completionNote", "").takeIf { !item.isNull("completionNote") && it.isNotBlank() },
                            checkRequest = item.optJSONObject("checkRequest")?.let { c ->
                                val to = c.optJSONArray("to")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
                                if (to.isEmpty() || !c.has("at")) null else CheckRequest(to, c.optString("from", "").takeIf { !c.isNull("from") && it.isNotBlank() },
                                    c.getLong("at"), if (c.isNull("startEpochDay") || !c.has("startEpochDay")) null else c.getLong("startEpochDay"),
                                    if (c.isNull("color") || !c.has("color")) null else c.getInt("color"))
                            },
                            customCategoryId = item.optionalTimestamp("customCategoryId"),
                            isChecklist = isChecklist,
                            checklistItems = checklistItems,
                            sendImmediateNotification = item.optBoolean("sendImmediateNotification", true),
                            referenceUrl = item.optString("referenceUrl").takeIf { !it.isNullOrBlank() && it != "null" },
                            starBorderColorIndex = item.optionalAccent("starBorderColorIndex")
                                ?: if (item.optBoolean("isImportant", false)) Math.floorMod(item.getLong("id"), 10) else null,
                            gradientBlendFraction = item.optionalBlendFraction(),
                            customSolidColorArgb = item.optionalColorArgb("customSolidColorArgb"),
                            useDarkText = item.optBoolean("useDarkText", false),
                            attachments = attachments,
                            voiceRecordings = voiceRecordings,
                            musicBlocks = MusicBlock.decodeArray(item.optJSONArray("musicBlocks")),
                            mediaBlocks = MediaBlock.decodeArray(item.optJSONArray("mediaBlocks")),
                            gameBlocks = CardGameBlock.decodeArray(item.optJSONArray("gameBlocks")),
                            cardStack = CardStackBlock.decode(item.optJSONObject("cardStack")) ?: CardStackBlock.decodeArray(item.optJSONArray("cardStackBlocks")).firstOrNull(),
                            sentBy = item.optString("sentBy", "").takeIf { it.isNotBlank() && it != "null" },
                            shareOrigin = item.optString("shareOrigin", "").takeIf { it.isNotBlank() && it != "null" },
                            journal = journal,
                            mood = standaloneMood,
                            moodDetails = moodDetails,
                            location = location,
                            contentBlockLayout = decodeCardContentLayout(item.optJSONObject("contentBlockLayout")),
                            contentBlockOrder = if (item.has("contentBlockOrder") && !item.isNull("contentBlockOrder")) {
                                val arr = item.getJSONArray("contentBlockOrder")
                                (0 until arr.length()).map { arr.getString(it) }
                            } else emptyList()
                        )
                    )
                }
            }.sortedByDescending { it.createdAt }
        }
    }
    fun encode(items: List<ReminderItem>): String {
        val array = JSONArray()
        items.forEach { reminder ->
            array.put(
                JSONObject()
                    .put("id", reminder.id)
                    .put("text", reminder.text)
                    .apply {
                        reminder.sentBy?.let { put("sentBy", it) }
                        reminder.shareOrigin?.let { put("shareOrigin", it) }
                    }
                    .put("createdAt", reminder.createdAt)
                    .put("updatedAt", reminder.updatedAt)
                    .put("completedAt", reminder.completedAt ?: JSONObject.NULL)
                    .put("isImportant", reminder.isImportant)
                    .put("starBorderColorIndex", reminder.starBorderColorIndex ?: JSONObject.NULL)
                    .put("alertAt", reminder.alertAt ?: JSONObject.NULL)
                    .put("plannedDateEpochDay", reminder.plannedDateEpochDay ?: JSONObject.NULL)
                    .put("quick24HourUntilAt", reminder.quick24HourUntilAt ?: JSONObject.NULL)
                    .put("notifiedAlertAt", reminder.notifiedAlertAt ?: JSONObject.NULL)
                    .put("accentStart", reminder.accentStart ?: JSONObject.NULL)
                    .put("accentEnd", reminder.accentEnd ?: JSONObject.NULL)
                    .put("gradientBlendFraction", reminder.gradientBlendFraction?.toDouble() ?: JSONObject.NULL)
                    .put("customSolidColorArgb", reminder.customSolidColorArgb ?: JSONObject.NULL)
                    .put("useDarkText", reminder.useDarkText)
                    .put("repeatRule", reminder.repeatRule.storageKey)
                    .put("anchorAlertAt", reminder.anchorAlertAt ?: JSONObject.NULL)
                    .put("repeatWeekdaysMask", reminder.repeatWeekdaysMask)
                    .put("repeatUntilEpochDay", reminder.repeatUntilEpochDay ?: JSONObject.NULL)
                    .also { obj ->
                        // Omitted when empty so ordinary cards keep their existing sync payload byte-for-byte.
                        if (reminder.repeatSkippedEpochDays.isNotEmpty()) {
                            obj.put("repeatSkippedEpochDays", JSONArray().also { arr -> reminder.repeatSkippedEpochDays.distinct().sorted().forEach { arr.put(it) } })
                        }
                        reminder.endAt?.let { obj.put("endAt", it) }
                        reminder.earlyAlertMinutes?.let { obj.put("earlyAlertMinutes", it) }
                        if (reminder.isAlarm) obj.put("isAlarm", true)
                        reminder.locationTrigger?.let { t ->
                            obj.put("locationTrigger", JSONObject()
                                .put("placeName", t.placeName).put("latitude", t.latitude).put("longitude", t.longitude)
                                .put("radiusMeters", t.radiusMeters).put("onExit", t.onExit))
                        }
                        if (reminder.isDivider) obj.put("isDivider", true)
                        reminder.completionNote?.let { obj.put("completionNote", it) }
                        reminder.checkRequest?.let { c -> obj.put("checkRequest", JSONObject().put("to", org.json.JSONArray(c.to)).put("at", c.at)
                            .apply { c.from?.let { put("from", it) }; c.startEpochDay?.let { put("startEpochDay", it) }; c.colorArgb?.let { put("color", it) } }) }
                        // Ortak kategori (device-local bookkeeping; stripped before personal sync).
                        reminder.sharedCardId?.let { obj.put("sharedCardId", it) }
                        reminder.sharedAuthorUid?.let { obj.put("sharedAuthorUid", it) }
                        reminder.sharedAuthorName?.let { obj.put("sharedAuthorName", it) }
                    }
                    .put("customCategoryId", reminder.customCategoryId ?: JSONObject.NULL)
                    .put("isChecklist", reminder.isChecklist)
                    .put("sendImmediateNotification", reminder.sendImmediateNotification)
                    .put("referenceUrl", reminder.referenceUrl ?: JSONObject.NULL)
                    .put("location", reminder.location?.let { place ->
                        JSONObject()
                            .put("placeId", place.placeId ?: JSONObject.NULL)
                            .put("placeName", place.placeName)
                            .put("address", place.address)
                            .put("latitude", place.latitude ?: JSONObject.NULL)
                            .put("longitude", place.longitude ?: JSONObject.NULL)
                            .put("mapsUrl", place.mapsUrl ?: JSONObject.NULL)
                            .put("approximateCoordinates", place.approximateCoordinates)
                            .put("mapZoom", place.mapZoom ?: JSONObject.NULL)
                    } ?: JSONObject.NULL)
                    .put("checklistItems", JSONArray().apply {
                        reminder.checklistItems.forEach { checkItem ->
                            put(
                                JSONObject()
                                    .put("id", checkItem.id)
                                    .put("text", checkItem.text)
                                    .put("completedAt", checkItem.completedAt ?: JSONObject.NULL)
                            )
                        }
                    })
                    .put("attachments", AttachmentJson.encodeArray(reminder.attachments))
                    .put("voiceRecordings", VoiceRecordingJson.encodeArray(reminder.voiceRecordings))
                    .put("musicBlocks", MusicBlock.encodeArray(reminder.musicBlocks))
                    .put("mediaBlocks", MediaBlock.encodeArray(reminder.mediaBlocks))
                    // Only when present: a server without gameBlocks support rejects the field.
                    .apply { if (reminder.gameBlocks.isNotEmpty()) put("gameBlocks", CardGameBlock.encodeArray(reminder.gameBlocks)) }
                    .put("cardStack", reminder.cardStack?.let { CardStackBlock.encode(it) } ?: JSONObject.NULL)
                    .put("mood", reminder.mood?.persistKey ?: JSONObject.NULL)
                    .put("moodDetails", reminder.moodDetails?.let { details ->
                        JSONObject()
                            .put("scope", details.scope.persistKey)
                            .put("descriptors", JSONArray().apply { details.descriptors.forEach { put(it) } })
                            .put("energy", details.energy?.persistKey ?: JSONObject.NULL)
                            .put("factors", JSONArray().apply { details.factors.forEach { put(it) } })
                            .put("loggedAt", details.loggedAt)
                    } ?: JSONObject.NULL)
                    .put("journal", reminder.journal?.let { j ->
                        JSONObject()
                            .put("mood", j.mood?.persistKey ?: JSONObject.NULL)
                            .put("text", j.text)
                            .put("createdAt", j.createdAt)
                            .put("updatedAt", j.updatedAt)
                    } ?: JSONObject.NULL)
                    .apply {
                        if (reminder.contentBlockLayout.isNotEmpty()) put("contentBlockLayout", encodeCardContentLayout(reminder.contentBlockLayout))
                    }
                    .put("contentBlockOrder", JSONArray().apply {
                        reminder.contentBlockOrder.forEach { put(it) }
                    })
            )
        }
        return array.toString()
    }
    private fun JSONObject.optionalTimestamp(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key).takeIf { it > 0L }

    private fun JSONObject.optionalEpochDays(key: String): List<Long> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            if (array.isNull(index)) null else array.optLong(index)
        }.distinct().sorted()
    }

    private fun JSONObject.optionalEpochDay(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.optionalAccent(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key).takeIf { it in ReminderColorChoices.indices }

    private fun JSONObject.optionalColorArgb(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.optionalBlendFraction(): Float? {
        if (has("gradientBlendFraction") && !isNull("gradientBlendFraction")) {
            val f = optDouble("gradientBlendFraction").toFloat()
            return if (f.isFinite()) f.coerceIn(0.0f, 1.0f) else null
        }
        if (has("gradientBlendIndex") && !isNull("gradientBlendIndex")) {
            val idx = optInt("gradientBlendIndex")
            if (idx in GRADIENT_BLEND_FRACTIONS.indices) {
                return GRADIENT_BLEND_FRACTIONS[idx]
            }
        }
        return null
    }

}
