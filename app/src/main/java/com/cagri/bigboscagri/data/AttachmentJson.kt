package com.cagri.bigboscagri.data

import org.json.JSONArray
import org.json.JSONObject


object AttachmentJson {
    fun encode(item: ReminderAttachment): JSONObject = JSONObject()
        .put("id", item.id).put("localPath", item.localPath).put("type", item.type.name)
        .put("uri", item.uri ?: JSONObject.NULL).put("mimeType", item.mimeType ?: JSONObject.NULL)
        .put("displayName", item.displayName ?: JSONObject.NULL).put("sizeBytes", item.sizeBytes ?: JSONObject.NULL)
        .put("text", item.text ?: JSONObject.NULL).put("source", item.source.name)
        .put("sourceTitle", item.sourceTitle ?: JSONObject.NULL)

    fun decode(obj: JSONObject): ReminderAttachment? {
        fun optional(key: String) = if (obj.isNull(key)) null else obj.optString(key).takeIf { it.isNotBlank() }
        val id = optional("id") ?: return null
        val type = if (!obj.has("type")) AttachmentType.IMAGE
            else AttachmentType.entries.firstOrNull { it.name == obj.optString("type") } ?: return null
        val path = optional("localPath").orEmpty()
        val uri = optional("uri")?.takeIf { it.startsWith("content://") }
        val text = optional("text")
        if (type == AttachmentType.IMAGE && path.isBlank()) return null
        if (type == AttachmentType.FILE && path.isBlank() && uri == null) return null
        if (type == AttachmentType.TEXT_SNIPPET && text == null) return null
        return ReminderAttachment(id, path, type, uri, optional("mimeType"), optional("displayName"),
            if (obj.isNull("sizeBytes")) null else obj.optLong("sizeBytes", -1).takeIf { it >= 0 }, text,
            SnippetSource.entries.firstOrNull { it.name == obj.optString("source") } ?: SnippetSource.PLAIN_TEXT,
            optional("sourceTitle"))
    }
    fun encodeArray(items: List<ReminderAttachment>) = JSONArray().apply { items.forEach { put(encode(it)) } }
    fun decodeArray(array: JSONArray?): List<ReminderAttachment> = buildList {
        if (array != null) for (i in 0 until array.length()) array.optJSONObject(i)?.let { decode(it) }?.let(::add)
    }
}
