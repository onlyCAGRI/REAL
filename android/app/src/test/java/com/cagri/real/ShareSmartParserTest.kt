package com.cagri.real

import com.cagri.real.data.*
import org.junit.Assert.*
import org.junit.Test

class ShareSmartParserTest {
    @Test
    fun linkInsideWordsBecomesLinkAndTitle() {
        val r = IncomingContentParser.smart("Bu videoyu izle https://youtu.be/abc123")
        assertEquals("https://youtu.be/abc123", r.referenceUrl)
        assertEquals("Bu videoyu izle", r.suggestedTitle)
        assertTrue(r.attachments.isEmpty())
    }

    @Test
    fun trailingPunctuationIsNotPartOfTheLink() {
        val r = IncomingContentParser.smart("Şuna bak: https://example.org/a.")
        assertEquals("https://example.org/a", r.referenceUrl)
        assertEquals("Şuna bak", r.suggestedTitle)
    }

    @Test
    fun subjectWinsAsTitle() {
        val r = IncomingContentParser.smart("https://example.org/x", "Makale")
        assertEquals("https://example.org/x", r.referenceUrl)
        assertEquals("Makale", r.suggestedTitle)
    }

    @Test
    fun plainTextStaysSnippetWithFirstLineTitle() {
        val r = IncomingContentParser.smart("Market listesi\nsüt\nekmek")
        assertNull(r.referenceUrl)
        assertEquals("Market listesi\nsüt\nekmek", r.attachments.single().text)
        assertEquals("Market listesi", r.suggestedTitle)
    }

    @Test
    fun severalLinksStayText() {
        val r = IncomingContentParser.smart("https://a.example/1 ve https://b.example/2")
        assertNull(r.referenceUrl)
        assertEquals(1, r.attachments.size)
    }

    @Test
    fun oldParserUnchangedForPano() {
        val r = IncomingContentParser.text("Bu bağlantıyı oku: https://example.org")
        assertNull(r.referenceUrl)
    }

    @Test
    fun newFieldsSurviveTheRoundTrip() {
        val c = IncomingContent(referenceUrl = "https://example.org", suggestedTitle = "Başlık",
            musicBlocks = listOf(MusicBlock(id = "m1", title = "Şarkı", artist = "Sanatçı", provider = "Spotify",
                providerUrl = "https://open.spotify.com/track/x", createdAt = 1)),
            voiceRecordings = listOf(ReminderVoiceRecording("v1", "/x/voice_v1.m4a", 4200, 5)),
            previewTitle = "Sayfa", previewImageUrl = "https://example.org/i.jpg")
        assertEquals(c, IncomingContent.decode(c.encode()))
    }

    @Test
    fun extraPhotosBecomeFiles() {
        val photos = (1..5).map { ReminderAttachment("p$it", "/p$it.jpg", AttachmentType.IMAGE) }
        val limited = ReminderRepository.limitPhotos(photos, 1)
        assertEquals(2, limited.count { it.type == AttachmentType.IMAGE })
        assertEquals(3, limited.count { it.type == AttachmentType.FILE })
    }

    @Test
    fun openGraphTagsAreRead() {
        val html = """<head><meta property="og:title" content="Tom &amp; Jerry"><meta content='https://x.example/i.png' property='og:image'></head>"""
        val tags = SharedLinkResolver.metaTags(html)
        assertEquals("Tom &amp; Jerry", tags["og:title"])
        assertEquals("Tom & Jerry", SharedLinkResolver.decodeEntities(tags["og:title"]!!))
        assertEquals("https://x.example/i.png", tags["og:image"])
    }
}

class SnippetSourceDetectorTest {
    @Test fun senderAppDecides() {
        assertEquals(SnippetSource.WHATSAPP_TEXT, SnippetSourceDetector.detect("com.whatsapp", "merhaba"))
        assertEquals(SnippetSource.WHATSAPP_TEXT, SnippetSourceDetector.detect("com.whatsapp.w4b", "merhaba"))
        assertEquals(SnippetSource.EMAIL_TEXT, SnippetSourceDetector.detect("com.google.android.gm", "merhaba"))
    }
    @Test fun whatsappTextShape() {
        assertEquals(SnippetSource.WHATSAPP_TEXT, SnippetSourceDetector.detect("com.android.intentresolver",
            "[15.10.2026 14:32] Ahmet: Yarın 10'da buluşalım\n[15.10.2026 14:33] Ben: Tamam"))
        assertEquals(SnippetSource.WHATSAPP_TEXT, SnippetSourceDetector.detect(null, "15.10.2026 14:32 - Ayşe: geliyorum"))
        assertEquals(SnippetSource.WHATSAPP_TEXT, SnippetSourceDetector.detect(null, "[10/15/26, 2:32 PM] John: hi"))
    }
    @Test fun mailTextShape() {
        assertEquals(SnippetSource.EMAIL_TEXT, SnippetSourceDetector.detect(null, "Kimden: Ali <ali@x.com>\nKonu: Toplantı\n\nMerhaba"))
        assertEquals(SnippetSource.EMAIL_TEXT, SnippetSourceDetector.detect("com.chrome", "metin", hasEmailExtras = true))
    }
    @Test fun plainOtherwise() {
        assertEquals(SnippetSource.PLAIN_TEXT, SnippetSourceDetector.detect("com.android.chrome", "Saat 14:32'de: toplantı var"))
    }
}
