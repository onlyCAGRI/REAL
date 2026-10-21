package com.cagri.real.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ReminderVoiceRecording(
    val id: String,
    val localPath: String,
    val durationMillis: Long,
    val createdAt: Long
)

object VoiceRecordingJson {
    fun encode(item: ReminderVoiceRecording): JSONObject = JSONObject()
        .put("id", item.id)
        .put("localPath", item.localPath)
        .put("durationMillis", item.durationMillis)
        .put("createdAt", item.createdAt)

    fun decode(obj: JSONObject): ReminderVoiceRecording? {
        val id = obj.optString("id", "").takeIf { it.isNotBlank() } ?: return null
        val localPath = obj.optString("localPath", "").takeIf { it.isNotBlank() } ?: return null
        val durationMillis = obj.optLong("durationMillis", -1L).takeIf { it >= 0L } ?: return null
        val createdAt = obj.optLong("createdAt", -1L).takeIf { it > 0L } ?: return null
        return ReminderVoiceRecording(id, localPath, durationMillis, createdAt)
    }

    fun encodeArray(items: List<ReminderVoiceRecording>): JSONArray = JSONArray().apply {
        items.forEach { put(encode(it)) }
    }

    fun decodeArray(array: JSONArray?): List<ReminderVoiceRecording> = buildList {
        if (array != null) {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::decode)?.let(::add)
            }
        }
    }
}

fun voiceRecordingPresentationTitle(createdAt: Long): String {
    val time = Instant.ofEpochMilli(createdAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    return "Ses kaydı · $time"
}

fun formatVoiceDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
