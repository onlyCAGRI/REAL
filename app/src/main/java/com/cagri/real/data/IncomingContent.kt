package com.cagri.real.data

import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.util.UUID

data class IncomingContent(
    val attachments: List<ReminderAttachment> = emptyList(),
    val referenceUrl: String? = null,
    val warning: String? = null,
    /** A title taken from the shared text (the words around a link, the subject, or the first line). */
    val suggestedTitle: String? = null,
    /** Spotify / Apple Music / YouTube Music links become REAL music blocks. */
    val musicBlocks: List<MusicBlock> = emptyList(),
    /** Shared m4a / aac audio becomes a voice recording. */
    val voiceRecordings: List<ReminderVoiceRecording> = emptyList(),
    /** Link preview (page title and image) shown before saving. */
    val previewTitle: String? = null,
    val previewImageUrl: String? = null
) {
    val isEmpty: Boolean get() = attachments.isEmpty() && referenceUrl == null && musicBlocks.isEmpty() && voiceRecordings.isEmpty()

    fun encode(): String = JSONObject().put("attachments", AttachmentJson.encodeArray(attachments))
        .put("referenceUrl", referenceUrl ?: JSONObject.NULL).put("warning", warning ?: JSONObject.NULL)
        .put("suggestedTitle", suggestedTitle ?: JSONObject.NULL)
        .put("musicBlocks", MusicBlock.encodeArray(musicBlocks))
        .put("voiceRecordings", org.json.JSONArray().apply { voiceRecordings.forEach { put(VoiceRecordingJson.encode(it)) } })
        .put("previewTitle", previewTitle ?: JSONObject.NULL).put("previewImageUrl", previewImageUrl ?: JSONObject.NULL)
        .toString()
    companion object {
        fun decode(raw: String): IncomingContent {
            val obj = JSONObject(raw)
            fun optional(key: String) = if (obj.isNull(key) || !obj.has(key)) null else obj.getString(key)
            val voices = obj.optJSONArray("voiceRecordings")
            return IncomingContent(AttachmentJson.decodeArray(obj.optJSONArray("attachments")),
                optional("referenceUrl"), optional("warning"), optional("suggestedTitle"),
                MusicBlock.decodeArray(obj.optJSONArray("musicBlocks")),
                (0 until (voices?.length() ?: 0)).mapNotNull { voices?.optJSONObject(it)?.let(VoiceRecordingJson::decode) },
                optional("previewTitle"), optional("previewImageUrl"))
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

    
    
    private val URL_REGEX = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE)

    /**
     * Share flow: a link inside other words ("Title https://…", the usual YouTube / Spotify / news share) becomes the
     * card's link and the words its title. Several links or a long text stay a text snippet.
     */
    fun smart(text: String?, subject: String? = null): IncomingContent {
        val value = text?.trim().orEmpty()
        val cleanSubject = subject?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() }?.take(200)
        if (value.isEmpty()) return IncomingContent(suggestedTitle = cleanSubject)
        require(value.length <= MAX_TEXT_CHARS) { "Metin çok uzun (en fazla 100.000 karakter)" }
        ShareParser.parseUrlOnly(value)?.let { return IncomingContent(referenceUrl = it.url, suggestedTitle = cleanSubject) }
        val links = URL_REGEX.findAll(value).map { it.value.trimEnd('.', ',', ')', '!', '?', ';', ':', '»', '"', '\'') }.distinct().toList()
        val link = links.singleOrNull()?.takeIf { ShareParser.parseUrlOnly(it) != null }
        if (link != null) {
            val rest = value.replace(link, " ").trim { it.isWhitespace() || it in ":-|–—•.,;" }
            val words = rest.replace(Regex("\\s+"), " ")
            if (words.isEmpty()) return IncomingContent(referenceUrl = link, suggestedTitle = cleanSubject)
            if (words.length <= 200 && rest.lines().count { it.isNotBlank() } <= 3) {
                return IncomingContent(referenceUrl = link, suggestedTitle = cleanSubject ?: words)
            }
        }
        val firstLine = value.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return text(value, subject).copy(suggestedTitle = cleanSubject ?: firstLine.take(120))
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
