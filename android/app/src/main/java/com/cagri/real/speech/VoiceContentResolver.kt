package com.cagri.real.speech

import com.cagri.real.data.ReminderLocation
import com.cagri.real.data.MusicBlock
import com.cagri.real.data.MediaBlock
import com.cagri.real.data.MusicCandidateMatcher
import com.cagri.real.data.MediaCatalog

fun voiceIntentLocation(intent: GeminiVoiceIntent): ReminderLocation? =
    intent.locationName?.trim()?.takeIf { it.isNotBlank() }?.let { placeName ->
        val encoded = java.net.URLEncoder.encode(placeName, Charsets.UTF_8.name())
        ReminderLocation(
            placeName = placeName,
            mapsUrl = "https://www.google.com/maps/search/?api=1&query=$encoded"
        )
    }

class VoiceContentUnavailableException(message: String) : IllegalStateException(message)

suspend fun resolveVoiceContentBlocks(intent: GeminiVoiceIntent): Pair<List<MusicBlock>, List<MediaBlock>> {
    val resolved = when (intent.attachmentKind) {
        VoiceAttachmentKind.MUSIC -> {
            val block = intent.music?.let { MusicCandidateMatcher.resolve(it) }
            (block?.let(::listOf) ?: emptyList()) to emptyList()
        }
        VoiceAttachmentKind.MOVIE -> {
            val block = intent.media?.let { MediaCatalog.resolveMovie(it) }
            emptyList<MusicBlock>() to (block?.let(::listOf) ?: emptyList())
        }
        VoiceAttachmentKind.TV -> {
            val block = intent.media?.let { MediaCatalog.resolveTv(it) }
            emptyList<MusicBlock>() to (block?.let(::listOf) ?: emptyList())
        }
        VoiceAttachmentKind.NONE -> emptyList<MusicBlock>() to emptyList()
    }
    if (intent.attachmentKind != VoiceAttachmentKind.NONE && resolved.first.isEmpty() && resolved.second.isEmpty()) {
        throw VoiceContentUnavailableException("“${intent.title}” katalogda doğrulanamadı veya bağlantı kurulamadı. İçerik kutusu olmadan kaydedilmedi.")
    }
    return resolved
}

