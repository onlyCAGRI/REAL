package com.cagri.real.ui

import com.cagri.real.i18n.t
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.textclassifier.TextClassifier
import com.cagri.real.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Yapıştır (card header): the last copied thing becomes the right block — music / place / link / WhatsApp, e-mail or
 * plain text / photo / file. Copied passwords are pasted too: the person decides what to keep in REAL (local-only
 * cards are planned). [peek] only reads the clipboard's TYPE (no content, no Android "pasted" notice); the content
 * is read in [read], only when the person taps.
 */
internal object ClipboardPaste {
    enum class Kind(private val trLabel: String) {
        Text("Metin"), Link("Bağlantı"), Photo("Fotoğraf"), File("Dosya");
        val label: String get() = t(trLabel)
    }

    private fun manager(context: Context) = context.getSystemService(ClipboardManager::class.java)

    fun peek(context: Context): Kind? = runCatching {
        val m = manager(context) ?: return null
        if (!m.hasPrimaryClip()) return null
        val d = m.primaryClipDescription ?: return null
        when {
            d.hasMimeType("image/*") -> Kind.Photo
            d.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) -> Kind.File
            d.hasMimeType("text/*") -> {
                // Android 12+: the system already classified the copied text; a link is labelled without reading it.
                if (Build.VERSION.SDK_INT >= 31 && d.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE &&
                    d.getConfidenceScore(TextClassifier.TYPE_URL) > 0.5f) Kind.Link else Kind.Text
            }
            else -> null
        }
    }.getOrNull()

    /** Copied in the last [windowMs] (the clipboard's own copy time; reading it shows no notice): the kind and that time. */
    fun fresh(context: Context, windowMs: Long = FRESH_WINDOW_MS): Pair<Kind, Long>? = runCatching {
        val kind = peek(context) ?: return null
        val copiedAt = manager(context)?.primaryClipDescription?.timestamp ?: return null
        val age = System.currentTimeMillis() - copiedAt
        if (copiedAt <= 0L || age < 0L || age > windowMs) null else kind to copiedAt
    }.getOrNull()

    const val FRESH_WINDOW_MS = 30_000L

    data class Pasted(val content: IncomingContent, val location: ReminderLocation?, val label: String)

    /** Reads and converts the clipboard (on tap). Null when there is nothing usable. */
    suspend fun read(context: Context): Pasted? = withContext(Dispatchers.IO) {
        val clip = runCatching { manager(context)?.primaryClip }.getOrNull() ?: return@withContext null
        val item = clip.getItemAt(0) ?: return@withContext null
        val uri = item.uri?.takeIf { it.scheme == "content" }
        if (uri != null) return@withContext fromUri(context, uri)
        val text = item.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@withContext null
        fromText(text)
    }

    /** A photo or file (the clipboard's, or one the keyboard sent to "Panodan ekle") as a card attachment. */
    suspend fun fromUri(context: Context, uri: android.net.Uri): Pasted? = withContext(Dispatchers.IO) {
        val attachment = runCatching {
            // "Panodan ekle" keeps keyboard images as REAL's own cache files; each card gets its own copy.
            if (uri.scheme == "file") AttachmentStorage.importCameraPhoto(context, java.io.File(requireNotNull(uri.path)))
            else AttachmentStorage.importUri(context, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.getOrNull() ?: return@withContext null
        Pasted(IncomingContent(attachments = listOf(attachment)), null,
            if (attachment.type == AttachmentType.IMAGE) t("Fotoğraf") else t("Dosya"))
    }

    /** Converts copied text into the right block (the clipboard's, or one taken from the keyboard's clipboard). */
    suspend fun fromText(text: String): Pasted = withContext(Dispatchers.IO) {
        GoogleMapsShareParser.sharedUrl(text)?.let { maps ->
            runCatching { GoogleMapsShareParser.parse(maps) }.getOrNull()?.let { place ->
                return@withContext Pasted(IncomingContent(), place, t("Konum"))
            }
        }
        var content = IncomingContentParser.smart(text)
        val source = SnippetSourceDetector.detect(null, text)
        content = content.copy(attachments = content.attachments.map {
            if (it.type == AttachmentType.TEXT_SNIPPET) it.copy(source = source) else it
        })
        val link = content.referenceUrl
        if (link != null) {
            val found = SharedLinkResolver.resolve(link)
            found.music?.let { music ->
                return@withContext Pasted(IncomingContent(musicBlocks = listOf(music)), null,
                    if (music.provider == "iTunes") t("Apple Music şarkısı") else t("{0} şarkısı", music.provider))
            }
            return@withContext Pasted(content, null, t("Bağlantı"))
        }
        Pasted(content, null, t(source.label))
    }
}
