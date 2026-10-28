package com.cagri.real

import com.cagri.real.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CardBodyStyleTest {
    private val b = BodyMark.BOLD
    private val i = BodyMark.ITALIC

    @Test fun toggleAddsThenRemovesAcrossTheSelection() {
        val on = BodySpans.toggle(emptyList(), b, 2, 6, 10)
        assertEquals(listOf(BodySpan(2, 6, b)), on)
        assertEquals(listOf(BodySpan(2, 3, b), BodySpan(5, 6, b)), BodySpans.toggle(on, b, 3, 5, 10))
        assertEquals(listOf(BodySpan(1, 6, b)), BodySpans.toggle(on, b, 1, 4, 10))
    }

    @Test fun typingInsideOrAtTheEndOfASpanExtendsIt() {
        val spans = listOf(BodySpan(0, 4, b))
        assertEquals(listOf(BodySpan(0, 5, b)), BodySpans.applyEdit(spans, "kalı", "kalın"))
        assertEquals(listOf(BodySpan(0, 5, b)), BodySpans.applyEdit(spans, "kalı", "kaXlı"))
    }

    @Test fun typingBeforeASpanShiftsItAndStaysPlain() {
        val spans = listOf(BodySpan(2, 4, b))
        assertEquals(listOf(BodySpan(5, 7, b)), BodySpans.applyEdit(spans, "a bc", "XYZa bc"))
    }

    @Test fun deletingStyledTextShrinksOrDropsTheSpan() {
        val spans = listOf(BodySpan(2, 6, b), BodySpan(7, 9, i))
        assertEquals(listOf(BodySpan(2, 3, b), BodySpan(4, 6, i)), BodySpans.applyEdit(spans, "0123456789", "0126789"))
        assertEquals(listOf(BodySpan(2, 4, i)), BodySpans.applyEdit(spans, "0123456789", "01789"))
    }

    @Test fun explicitTypingMarksOverrideTheCaret() {
        val spans = listOf(BodySpan(0, 3, b))
        assertEquals(listOf(BodySpan(0, 3, b)), BodySpans.applyEdit(spans, "abc", "abcd", typingMarks = emptySet()))
        assertEquals(listOf(BodySpan(0, 3, b), BodySpan(3, 4, i)), BodySpans.applyEdit(spans, "abc", "abcd", typingMarks = setOf(i)))
    }

    @Test fun sliceCutsAndShiftsForAutoFlow() {
        val spans = listOf(BodySpan(2, 8, b), BodySpan(9, 12, i))
        assertEquals(listOf(BodySpan(2, 5, b)), BodySpans.slice(spans, 0, 5))
        assertEquals(listOf(BodySpan(0, 3, b), BodySpan(4, 7, i)), BodySpans.slice(spans, 5, 12))
    }

    @Test fun stylesSurviveJsonAndOldCardsHaveNone() {
        val card = CardStackCard(body = "Merhaba dünya", bodyStyles = listOf(BodySpan(0, 7, b), BodySpan(8, 13, BodyMark.STRIKE)))
        assertEquals(card.bodyStyles, CardStackCard.decode(CardStackCard.encode(card))!!.bodyStyles)
        val legacy = JSONObject().put("id", "x").put("body", "düz")
        assertTrue(CardStackCard.decode(legacy)!!.bodyStyles.isEmpty())
        assertFalse(CardStackCard.encode(CardStackCard(body = "düz")).has("bodyStyles"))
    }

    @Test fun sizeAndColourReplaceTheirOwnGroupOnly() {
        val spans = listOf(BodySpan(0, 5, BodyMark.SMALL), BodySpan(0, 5, b), BodySpan(0, 5, BodyMark.ORANGE))
        val next = BodySpans.toggle(spans, BodyMark.TITLE, 0, 5, 5)
        assertEquals(setOf(BodyMark.TITLE, b, BodyMark.ORANGE), next.map { it.mark }.toSet())
        val blue = BodySpans.toggle(next, BodyMark.BLUE, 2, 4, 5)
        assertEquals(listOf(BodySpan(0, 2, BodyMark.ORANGE), BodySpan(4, 5, BodyMark.ORANGE)), blue.filter { it.mark == BodyMark.ORANGE })
        assertTrue(BodySpans.clearGroup(blue, BodyMarkGroup.COLOR, 0, 5).none { it.mark.group == BodyMarkGroup.COLOR })
    }

    @Test fun paragraphRangeCoversWholeLinesWithTheirBreak() {
        val text = "Başlık\ngövde\nson"
        assertEquals(0 until 7, BodySpans.paragraphRange(text, 3, 3))
        assertEquals(7 until 13, BodySpans.paragraphRange(text, 7, 7))
        assertEquals(0 until 13, BodySpans.paragraphRange(text, 2, 9))
        assertEquals(13 until 16, BodySpans.paragraphRange(text, 16, 16))
        assertTrue(BodySpans.paragraphRange("ab\n", 3, 3).isEmpty())
    }

    @Test fun newLineAfterAHeadingStartsAsBody() {
        val spans = listOf(BodySpan(0, 6, BodyMark.TITLE), BodySpan(0, 6, b))
        val next = BodySpans.applyEdit(spans, "Başlık", "Başlık\n")
        assertEquals(listOf(BodySpan(0, 6, BodyMark.TITLE)), next.filter { it.mark == BodyMark.TITLE })
        assertEquals(listOf(BodySpan(0, 7, b)), next.filter { it.mark == b })
    }

    @Test fun stackFormatSurvivesJsonAndDefaultIsNotStored() {
        val format = BodyFormat(BodySize.LARGE, indent = true, gap = BodyGap.SMALL)
        assertEquals(format, CardStackCard.decode(CardStackCard.encode(CardStackCard(body = "x", bodyFormat = format)))!!.bodyFormat)
        assertFalse(CardStackCard.encode(CardStackCard(body = "x")).has("bodyFormat"))
    }

    @Test fun paragraphGapLinesMapBackToTheBody() {
        val layout = com.cagri.real.ui.components.CardBodyLayout("ab\ncd\ne", emptyList(), BodyFormat(gap = BodyGap.SMALL), onDark = true)
        assertEquals("ab\n\ncd\n\ne", layout.annotated.text)
        assertEquals(4, layout.toTransformed(3))
        assertEquals(3, layout.toOriginal(4))
        assertEquals(3, layout.toOriginal(3))
        assertEquals(7, layout.toOriginal(9))
    }

    @Test fun journalStylesSurviveJson() {
        val card = ReminderItem(id = 9, text = "Günlük", createdAt = 1,
            journal = ReminderJournal(null, "Bugün iyiydi", 1, styles = listOf(BodySpan(6, 12, b))))
        assertEquals(listOf(BodySpan(6, 12, b)), ReminderJson.decode(ReminderJson.encode(listOf(card))).single().journal?.styles)
    }

    @Test fun storedRangesAreClampedToTheBody() {
        val raw = JSONObject().put("body", "kısa").put("bodyStyles", org.json.JSONArray("[[0,99,\"b\"],[3,1,\"i\"],[0,2,\"?\"]]"))
        assertEquals(listOf(BodySpan(0, 4, b)), CardStackCard.decode(raw)!!.bodyStyles)
    }
}
