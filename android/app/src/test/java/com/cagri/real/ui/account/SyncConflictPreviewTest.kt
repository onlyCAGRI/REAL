package com.cagri.real.ui.account

import org.junit.Assert.*
import org.junit.Test

class SyncConflictPreviewTest {
    @Test fun tombstoneIsNotShownAsAnEmptyCard() {
        assertEquals("Silinmiş kayıt", syncBranchPreview(null))
    }
    @Test fun privatePathsBlobIdsAndUnknownFieldsAreNeverRendered() {
        val preview = syncBranchPreview("""{"text":"Spor","attachments":[{"localPath":"/secret/file.jpg","blobId":"secret-blob"}],"uid":"secret-uid"}""")
        assertTrue(preview.contains("Spor")); assertTrue(preview.contains("1 ek"))
        assertFalse(preview.contains("secret")); assertFalse(preview.contains("localPath"))
    }
    @Test fun nativeRichContentGetsADistinguishableSummary() {
        val preview = syncBranchPreview("""{"text":"","checklistItems":[{"text":"Dambıl"}],"voiceRecordings":[{}],"musicBlocks":[{"title":"Song"}],"mediaBlocks":[{"title":"Silo"}],"cardStack":{"cards":[{},{}]},"mood":{},"location":{}}""")
        listOf("Dambıl", "1 ses kaydı", "Song", "Silo", "2 stack kartı", "Mood", "Konum").forEach { assertTrue(preview.contains(it)) }
    }
    @Test fun corruptPreviewDoesNotRenderRawContentOrCrash() {
        assertEquals("İçerik önizlenemedi. Sürümler korunuyor.", syncBranchPreview("secret raw malformed data"))
    }
}
