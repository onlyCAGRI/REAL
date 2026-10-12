package com.cagri.real.books

import org.junit.Assert.*
import org.junit.Test

class PdfQuoteLayoutTest {
    private fun line(t: String, x: Int, y: Int, r: Int = 390) = PdfParagraphLayout.Line(t, x, y, r, y + 12, 0)

    @Test fun italicQuoteFollowedByShortRightAlignedAuthorIsSeparated() {
        val lines = listOf(line("Alıntının ilk satırı", 30, 100), line("Alıntının ikinci satırı", 30, 115),
            line("Alıntının sonu.", 30, 130, 300), line("Prenses İrulan", 280, 154), line("Normal metin", 30, 200))
        assertEquals(setOf(3), PdfQuoteLayout.attributions(lines, listOf(1f, 1f, 1f, 0f, 0f)))
    }
    @Test fun ordinaryShortRightAlignedLineWithoutQuoteIsNotAnAuthor() {
        val lines = listOf(line("Normal paragraf", 30, 100), line("Bir Kişinin Adı", 260, 120), line("Devam eden metin", 30, 150))
        assertTrue(PdfQuoteLayout.attributions(lines, listOf(0f, 0f, 0f)).isEmpty())
    }
    @Test fun leftAlignedTextAfterQuoteIsNotAnAuthor() {
        val lines = listOf(line("Alıntı", 30, 100), line("Yeni paragraf", 30, 120), line("Devamı", 30, 135))
        assertTrue(PdfQuoteLayout.attributions(lines, listOf(1f, 0f, 0f)).isEmpty())
    }
    @Test fun authorFlagsPreserveItalicGapAndManualBodyDecision() {
        val italic = "${ParagraphKind.ITALIC_ON}Prenses İrulan${ParagraphKind.ITALIC_OFF}"
        val marked = ParagraphKind.withAttribution(italic)
        assertTrue(ParagraphKind.isAttribution(marked))
        assertFalse(ParagraphKind.isHeading(marked))
        assertEquals(1, ParagraphKind.gapLevel(marked))
        assertEquals("Prenses İrulan", ParagraphKind.plain(marked))
        assertEquals(listOf(0 until "Prenses İrulan".length), ParagraphKind.parseItalic(ParagraphKind.text(marked)).second)
        assertTrue(ParagraphKind.isAttribution(ParagraphKind.withKind(marked, false)))
    }
    @Test fun unresolvedRunningHeaderNeverConsumesChapterInitial() {
        val heading = ParagraphKind.withKind("ÜÇ", true)
        val header = ParagraphKind.withFurniture("Madeline Miller - Kitap 20")
        val body = ParagraphKind.withKind("İlk paragraf", false)
        assertFalse(ParagraphKind.isHeading(header))
        assertEquals(setOf(2), ParagraphKind.sectionInitials(listOf(heading, header, body)))
    }
    @Test fun explicitBodyCannotBeReinterpretedAsTitleCaseHeading() {
        val lines = listOf(line(ParagraphKind.withKind("Madeline Miller - Kitap 20", false), 139, 42),
            line("ÜÇ", 202, 132, 218), line("İlk paragraf", 30, 162))
        val groups = PdfParagraphLayout.groups(lines)
        assertFalse(groups.first().heading)
        assertTrue(groups.any { it.from == 1 && it.until == 2 && it.heading })
    }
}
