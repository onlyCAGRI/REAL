package com.cagri.real.books

import org.junit.Assert.*
import org.junit.Test

class PdfTextLayoutTest {
    @Test fun nativeLinesKeepTheirSeparateBounds() {
        assertEquals(listOf("Başlık", "Birinci satır", "İkinci satır"), PdfTextLines.split("Başlık\r\nBirinci satır\r\nİkinci satır", 3))
        assertNull(PdfTextLines.split("Birden fazla satırı kapsayan birleşik metin", 3))
        assertNull(PdfTextLines.split("a\nb", 1))
    }
    private fun line(text: String, x: Int, y: Int, right: Int = 393, block: Int = 0) =
        PdfParagraphLayout.Line(text, x, y, right, y + 12, block)

    @Test fun centeredThreeLetterChapterAndIndentedParagraphsRemainSeparate() {
        val lines = listOf(
            line("İKİ", 203, 132, 217),
            line("Birinci paragraf burada başlar ve", 28, 162),
            line("burada biter.", 28, 177, 383),
            line("İkinci paragrafın ilk satırı", 43, 192),
            line("devamı ve sonu.", 28, 207, 150),
            line("Üçüncü paragrafın ilk satırı", 43, 222),
            line("burada devam eder", 28, 237)
        )
        val groups = PdfParagraphLayout.groups(lines)
        assertEquals(listOf(0 to 1, 1 to 3, 3 to 5, 5 to 7), groups.map { it.from to it.until })
        assertTrue(groups.first().heading)
        assertTrue(groups.drop(1).none { it.heading })
        val paragraphs = groups.map { group ->
            val text = lines.subList(group.from, group.until).joinToString(" ") { it.text }
            if (group.heading) ParagraphKind.withKind(text, true) else text
        }
        assertEquals(setOf(1), ParagraphKind.sectionInitials(paragraphs))
    }
    @Test fun ordinaryWrappedLinesDoNotBecomeSeparateParagraphs() {
        val lines = (0..5).map { line("Devam eden uzun metin satırı", 28, 160 + it * 20) }
        assertEquals(1, PdfParagraphLayout.groups(lines).size)
    }
    @Test fun leftAlignedShortBodyIsNotAChapterHeading() {
        val lines = listOf(line("EVET", 28, 160, 70), line("Devam eden sıradan metin satırı", 28, 175), line("devamı", 28, 190))
        assertFalse(PdfParagraphLayout.groups(lines).first().heading)
    }
    @Test fun obviousBlankSpaceSeparatesParagraphs() {
        val lines = listOf(line("Devam eden satır", 28, 160), line("devamı", 28, 175), line("Boşluk sonrası paragraf", 28, 225), line("devamı", 28, 240))
        assertEquals(listOf(0 to 2, 2 to 4), PdfParagraphLayout.groups(lines).map { it.from to it.until })
    }
    @Test fun explicitOcrParagraphBlocksStillSeparate() {
        val lines = listOf(line("Birinci blok satırı", 28, 160, block = 0), line("Birinci bloğun devamı", 28, 175, block = 0), line("İkinci blok", 28, 190, block = 1))
        assertEquals(2, PdfParagraphLayout.groups(lines, blocksAreParagraphs = true).size)
    }
}
