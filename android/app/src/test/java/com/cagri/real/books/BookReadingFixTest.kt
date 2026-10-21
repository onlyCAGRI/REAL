package com.cagri.real.books

import org.junit.Assert.*
import org.junit.Test

class BookReadingFixTest {
    @Test fun initialIsFoundAfterOpeningQuotesAndPreservesPrefix() {
        assertEquals(1..1, ParagraphKind.initialRange("“Şimdi başlayalım.”"))
        assertEquals(0..0, ParagraphKind.initialRange("İlk paragraf."))
        assertNull(ParagraphKind.initialRange("123 numaralı kayıt"))
    }
    @Test fun headingsAndManualBodyOverridesRemainIntact() {
        assertTrue(ParagraphKind.isHeading("BÖLÜM 2"))
        assertFalse(ParagraphKind.isHeading(ParagraphKind.withKind("BÖLÜM 2", false)))
        assertEquals("Giriş", ParagraphKind.text(ParagraphKind.withKind("Giriş", true)))
    }
    @Test fun onlyFirstBodyAfterEachHeadingGetsInitial() {
        val heading = ParagraphKind.withKind("Bir başlık", true)
        val body = ParagraphKind.withKind("Bir paragraf.", false)
        assertEquals(setOf(2, 5), ParagraphKind.sectionInitials(listOf(heading, "", body, body, heading, body)))
        assertEquals(emptySet<Int>(), ParagraphKind.sectionInitials(listOf(body, body)))
    }
    @Test fun headingAtPageBoundaryCarriesToNextBodyOnly() {
        val body = ParagraphKind.withKind("Devam eden metin.", false)
        assertEquals(setOf(1), ParagraphKind.sectionInitials(listOf("", body, body), followsHeading = true))
        assertEquals(setOf(1), ParagraphKind.sectionInitials(listOf(ParagraphKind.withKind("Başlık", true), body)))
    }

}
