package com.cagri.real.data

import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.util.UUID

data class IncomingContent(
    val attachments: List<ReminderAttachment> = emptyList(),
    val referenceUrl: String? = null,
    val warning: String? = null
) {
    fun encode(): String = JSONObject().put("attachments", AttachmentJson.encodeArray(attachments))
        .put("referenceUrl", referenceUrl ?: JSONObject.NULL).put("warning", warning ?: JSONObject.NULL).toString()
    companion object {
        fun decode(raw: String): IncomingContent {
            val obj = JSONObject(raw)
            return IncomingContent(AttachmentJson.decodeArray(obj.optJSONArray("attachments")),
                if (obj.isNull("referenceUrl")) null else obj.getString("referenceUrl"),
                if (obj.isNull("warning")) null else obj.getString("warning"))
        }
    }
}

object IncomingContentParser {
    const val MAX_STREAMS = 20
    const val MAX_TEXT_CHARS = 100_000

    
    fun text(text: String?, subject: String? = null): IncomingContent {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return IncomingContent()
        require(value.length <= MAX_TEXT_CHARS) { "Metin çok uzun (en fazla 100.000 karakter)" }
        ShareParser.parseUrlOnly(value)?.let { return IncomingContent(referenceUrl = it.url) }
        return IncomingContent(attachments = listOf(ReminderAttachment(
            id = UUID.randomUUID().toString(), type = AttachmentType.TEXT_SNIPPET,
            text = value, sourceTitle = subject?.trim()?.takeIf { it.isNotBlank() }?.take(500)
        )))
    }

    
    
    fun sharedText(intent: Intent): String? =
        intent.getCharSequenceExtra(if (intent.action == Intent.ACTION_PROCESS_TEXT) Intent.EXTRA_PROCESS_TEXT else Intent.EXTRA_TEXT)?.toString()

    @Suppress("DEPRECATION")
    fun streams(intent: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::addAll)
        } else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::add)
        intent.clipData?.let { clip ->
            require(clip.itemCount <= MAX_STREAMS) { "Bir seferde en fazla 20 dosya paylaşabilirsin" }
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add)
        }
        val result = uris.distinct()
        require(result.size <= MAX_STREAMS) { "Bir seferde en fazla 20 dosya paylaşabilirsin" }
        require(result.all { it.scheme == "content" }) { "Yalnızca güvenli içerik bağlantıları desteklenir" }
        return result
    }
}
