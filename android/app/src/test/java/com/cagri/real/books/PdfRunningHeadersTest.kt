package com.cagri.real.books

import org.junit.Assert.*
import org.junit.Test

class PdfRunningHeadersTest {
    private fun line(text: String, y: Int = 42, x: Int = 139, right: Int = 283, h: Int = 9) =
        PdfParagraphLayout.Line(text, x, y, right, y + h, 0)
    private fun mark(text: String) = PdfRunningHeaders.mark(line(text), 420, 595, 12f)!!
    private fun page(index: Int, text: String) = PdfRunningHeaders.Snapshot(index, listOf(mark(text)))

    @Test fun changingPageNumberStillMatchesRepeatedHeader() {
        val header = mark("Madeline Miller - Kitap 20")
        assertTrue(PdfRunningHeaders.repeated(header, listOf(page(19, "Madeline Miller - Kitap 19"), page(21, "Madeline Miller - Kitap 21"))))
    }
    @Test fun smallOcrErrorInLongNumberedHeaderStillRequiresTwoOtherPages() {
        val current = mark("Madaline Miller - Akhilleus’un Şarkısı 20")
        val other = listOf(page(19, "Madeline Miller - Akhilleus’un Şarkısı 19"), page(21, "Madeline Miller - Akhilleus’un Şarkısı 21"))
        assertTrue(PdfRunningHeaders.repeated(current, other))
        assertFalse(PdfRunningHeaders.repeated(current, other.take(1)))
    }
    @Test fun bodyHeightNumberedHeaderMayMatchButChapterLabelCannot() {
        assertNotNull(PdfRunningHeaders.mark(line("Yazar - Kitap 20", h = 12), 420, 595, 12f))
        assertNull(PdfRunningHeaders.mark(line("Bölüm 2", h = 9), 420, 595, 12f))
    }
    @Test fun onePageOrRepeatedSameReferenceIsNotEnoughEvidence() {
        val header = mark("Madeline Miller - Kitap 20")
        val reference = page(19, "Madeline Miller - Kitap 19")
        assertFalse(PdfRunningHeaders.repeated(header, listOf(reference)))
        assertFalse(PdfRunningHeaders.repeated(header, listOf(reference, reference)))
    }
    @Test fun unrelatedTextAndDifferentBandAreKept() {
        val header = mark("Madeline Miller - Kitap 20")
        assertFalse(PdfRunningHeaders.repeated(header, listOf(page(19, "Diğer Yazar - Kitap 19"), page(21, "Diğer Yazar - Kitap 21"))))
        val footer = header.copy(band = 1, y = .96f)
        assertFalse(PdfRunningHeaders.repeated(header, listOf(PdfRunningHeaders.Snapshot(19, listOf(footer)), PdfRunningHeaders.Snapshot(21, listOf(footer)))))
    }
    @Test fun shortChapterAndBodySizedNumericChapterAreNeverHeaderCandidates() {
        assertNull(PdfRunningHeaders.mark(line("ÜÇ", y = 132, x = 202, right = 218, h = 12), 420, 595, 12f))
        assertNull(PdfRunningHeaders.mark(line("ÜÇ", h = 9), 420, 595, 12f))
        assertNull(PdfRunningHeaders.mark(line("3", h = 12), 420, 595, 12f))
    }
    @Test fun noGeometryCannotDiscardChapterNumber() {
        assertEquals(listOf("III", "Bölümün ilk paragrafı burada."), PageLayout.stripEdgeNumbers(listOf("III", "Bölümün ilk paragrafı burada.")))
    }
    @Test fun uppercaseTwoLetterChapterIsExplicitHeading() {
        val lines = listOf(line("ÜÇ", 132, 202, 218, 12), line("İlk paragraf burada başlar", 162, 28, 393, 12), line("ve burada sürer.", 177, 28, 220, 12))
        val groups = PdfParagraphLayout.groups(lines)
        assertTrue(groups.first().heading)
        assertEquals(0 to 1, groups.first().from to groups.first().until)
    }
    @Test fun centeredNumericChapterGetsHeadingInsteadOfBeingADeletedPageNumber() {
        val lines = listOf(line("3", 132, 205, 215, 12), line("Bölümün ilk paragrafı", 162, 28, 393, 12), line("burada devam eder.", 177, 28, 220, 12))
        assertTrue(PdfParagraphLayout.groups(lines).first().heading)
    }
    @Test fun preservedNativeTitleRemainsHeadingOnAnOtherwiseEmptyPage() {
        val lines = listOf(line(ParagraphKind.withKind("ÜÇ", true), 132, 202, 218, 12))
        assertTrue(PdfParagraphLayout.groups(lines).single().heading)
    }
    @Test fun horizontalSpaceCleanupPreservesItalicAndParagraphFlags() {
        val input = "${ParagraphKind.HEADING}Bir\u00a0\u00a0${ParagraphKind.ITALIC_ON}iki\t  üç${ParagraphKind.ITALIC_OFF}"
        assertEquals("${ParagraphKind.HEADING}Bir ${ParagraphKind.ITALIC_ON}iki üç${ParagraphKind.ITALIC_OFF}", PdfTextLines.readableSpacing(input))
    }
}
