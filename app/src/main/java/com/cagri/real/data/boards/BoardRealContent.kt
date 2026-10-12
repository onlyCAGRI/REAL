package com.cagri.real.data.boards

import com.cagri.real.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Wire adapters only. Content behavior and presentation use REAL's immutable models. */
object BoardRealContent {
    fun musicPayload(value: MusicBlock) = JSONObject().put("music", MusicBlock.encodeArray(listOf(value)).getJSONObject(0))
    fun music(payload: JSONObject) = MusicBlock.decodeArray(JSONArray().put(payload.getJSONObject("music"))).single()
    fun mediaPayload(value: MediaBlock) = JSONObject().put("media", MediaBlock.encodeArray(listOf(value)).getJSONObject(0))
    fun media(payload: JSONObject) = MediaBlock.decodeArray(JSONArray().put(payload.getJSONObject("media"))).single()
    fun attachmentPayload(value: ReminderAttachment) = JSONObject().put("attachment", AttachmentJson.encode(value))
    fun attachment(payload: JSONObject) = checkNotNull(AttachmentJson.decode(payload.getJSONObject("attachment")))
    fun voicePayload(value: ReminderVoiceRecording) = JSONObject().put("voice", VoiceRecordingJson.encode(value))
    fun voice(payload: JSONObject) = checkNotNull(VoiceRecordingJson.decode(payload.getJSONObject("voice")))

    // Use the existing Mood codec, including all V2 detail semantics.
    fun moodPayload(mood: JournalMood, details: MoodDetails): JSONObject {
        val encoded = JSONArray(ReminderJson.encode(listOf(ReminderItem(1, "", details.loggedAt,
            mood = mood, moodDetails = details)))).getJSONObject(0)
        return JSONObject().put("mood", encoded.get("mood")).put("moodDetails", encoded.get("moodDetails"))
    }
    fun mood(payload: JSONObject): Pair<JournalMood, MoodDetails?> {
        val encoded = JSONObject(payload.toString()).put("id", 1).put("text", "").put("createdAt", 1)
        val decoded = ReminderJson.decode(JSONArray().put(encoded).toString()).single()
        return checkNotNull(decoded.mood) to decoded.moodDetails
    }
    fun locationPayload(place: ReminderLocation): JSONObject = JSONObject()
        .put("placeName", place.placeName).put("address", place.address)
        .put("latitude", place.latitude ?: JSONObject.NULL).put("longitude", place.longitude ?: JSONObject.NULL)
        .put("mapsUrl", place.mapsUrl ?: JSONObject.NULL)
        .put("placeId", place.placeId ?: JSONObject.NULL).put("approximateCoordinates", place.approximateCoordinates)

    fun location(payload: JSONObject) = ReminderLocation(
        placeName = payload.optString("placeName"), address = payload.optString("address"),
        latitude = if (payload.isNull("latitude")) null else payload.getDouble("latitude"),
        longitude = if (payload.isNull("longitude")) null else payload.getDouble("longitude"),
        mapsUrl = if (payload.isNull("mapsUrl")) null else payload.getString("mapsUrl"),
        placeId = if (payload.isNull("placeId")) null else payload.getString("placeId"),
        approximateCoordinates = payload.optBoolean("approximateCoordinates")
    )

    // Old board rows have UUID strings, REAL has Long IDs. Preserve every original wire ID
    // through edits; deterministic local IDs allow the existing REAL checklist to address rows.
    fun checklist(payload: JSONObject): List<ChecklistItem> {
        val rows = payload.optJSONArray("items") ?: JSONArray()
        val used = mutableSetOf<Long>()
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val wire = row.getString("id")
            var id = wire.toLongOrNull() ?: (UUID.nameUUIDFromBytes(wire.toByteArray(Charsets.UTF_8)).mostSignificantBits and Long.MAX_VALUE)
            while (!used.add(id)) id = if (id == Long.MAX_VALUE) 0 else id + 1
            ChecklistItem(id, row.optString("text"), if (row.optBoolean("done")) 1L else null)
        }
    }

    fun checklistPayload(previous: JSONObject, items: List<ChecklistItem>): JSONObject {
        val old = previous.optJSONArray("items") ?: JSONArray()
        val wireIds = checklist(previous).mapIndexed { index, entry -> entry.id to old.getJSONObject(index).getString("id") }.toMap()
        return JSONObject(previous.toString()).put("items", JSONArray().apply {
            items.forEach { entry -> put(JSONObject().put("id", wireIds[entry.id] ?: entry.id.toString())
                .put("text", entry.text).put("done", entry.isCompleted)) }
        })
    }

    fun referencedCard(payload: JSONObject, authorUid: String, currentUid: String, reminders: List<ReminderItem>): ReminderItem? =
        if (authorUid == currentUid) reminders.firstOrNull { it.id.toString() == payload.optString("sourceCardKey") } else null

    /** Payload flag: show the card's card stack directly instead of the collapsed card. */
    fun isStackView(payload: JSONObject) = payload.optString("view") == "stack"

    fun cardSnapshot(source: ReminderItem, includePrivate: Boolean, stackView: Boolean = false): JSONObject {
        val snapshot = source.copy(sentBy = null, shareOrigin = null,
            location = if (includePrivate) source.location else null,
            journal = if (includePrivate) source.journal else null,
            mood = if (includePrivate) source.mood else null,
            moodDetails = if (includePrivate) source.moodDetails else null,
            voiceRecordings = if (includePrivate) source.voiceRecordings else emptyList())
        return JSONObject().put("card", JSONArray(ReminderJson.encode(listOf(snapshot))).getJSONObject(0))
            .put("sourceCardKey", source.id.toString()).put("expanded", false)
            .apply { if (stackView) put("view", "stack") }
    }
}
