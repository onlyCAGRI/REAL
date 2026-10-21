package com.cagri.real.books

import org.junit.Assert.*
import org.junit.Test

class BookInlineTest {
    @Test fun overlappingBoldAndItalicKeepBothSpansAndReadableText() {
        val raw = "A " + BookInline.BOLD_ON + "bold " + ParagraphKind.ITALIC_ON + "both" + ParagraphKind.ITALIC_OFF + BookInline.BOLD_OFF + "."
        val parsed = BookInline.parse(raw)
        assertEquals("A bold both.", parsed.text)
        assertEquals(listOf(2..10), parsed.bold)
        assertEquals(listOf(7..10), parsed.italics)
        assertEquals("A bold both.", ParagraphKind.plain(raw))
    }
    @Test fun unclosedStyleDoesNotHideRemainingLetters() {
        val parsed = BookInline.parse("Hi " + BookInline.BOLD_ON + "there")
        assertEquals("Hi there", parsed.text)
        assertEquals(listOf(3..7), parsed.bold)
    }
    @Test fun aFigureDoesNotConsumeTheInitialAfterHeading() {
        assertEquals(setOf(2), ParagraphKind.sectionInitials(listOf(
            ParagraphKind.withKind("Title", true), "\u000bREAL_BLOCK:{}", ParagraphKind.withKind("Body", false))))
    }
}
