package com.cagri.real.contextcards

import com.cagri.real.data.JournalMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ContextCardEngineTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    // 2026-10-06 is a Tuesday.
    private fun at(hour: Int, day: Int = 6) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone)
    private fun ms(t: ZonedDateTime) = t.toInstant().toEpochMilli()

    @Test fun morningShowsNothing() {
        val s = ContextCardSignals(at(9), waterTodayMl = 0)
        assertNull(ContextCardEngine.decide(s, ContextCardHistory()).card)
    }

    @Test fun lowWaterAfternoonShowsWaterFirst() {
        val s = ContextCardSignals(at(14), waterTodayMl = 0)
        val d = ContextCardEngine.decide(s, ContextCardHistory())
        assertEquals(ContextCardType.WATER, d.card?.type)
        assertEquals(ContextCardType.WATER, d.activate)
    }

    @Test fun activeCardIsStickyAndNotReactivated() {
        val first = ContextCardSignals(at(14), waterTodayMl = 0)
        val h = ContextCardHistory().activated(ContextCardType.WATER, ms(first.now), first.now.toLocalDate().toEpochDay())
        val d = ContextCardEngine.decide(ContextCardSignals(at(15), waterTodayMl = 0), h)
        assertEquals(ContextCardType.WATER, d.card?.type)
        assertNull(d.activate)
    }

    @Test fun gapBlocksSecondCardWithinThreeHours() {
        val t = at(14)
        val h = ContextCardHistory().activated(ContextCardType.WATER, ms(t), t.toLocalDate().toEpochDay())
            .completed(ContextCardType.WATER, ms(t))
        assertNull(ContextCardEngine.decide(ContextCardSignals(at(16), waterTodayMl = 0), h).card)
        assertEquals(ContextCardType.MOOD, ContextCardEngine.decide(ContextCardSignals(at(18), waterTodayMl = 2000), h).card?.type)
    }

    @Test fun moodLoggedHidesMoodCard() {
        val s = ContextCardSignals(at(19), waterTodayMl = 2000, moodLoggedToday = true)
        assertNull(ContextCardEngine.decide(s, ContextCardHistory()).card)
    }

    @Test fun maxTwoCardsPerDay() {
        val h = ContextCardHistory()
            .activated(ContextCardType.WATER, ms(at(11)), at(11).toLocalDate().toEpochDay())
            .activated(ContextCardType.MOOD, ms(at(15)), at(15).toLocalDate().toEpochDay())
        val s = ContextCardSignals(at(21), waterTodayMl = 2000, moodLoggedToday = true, unfinishedBookTitle = "Kitap")
        assertNull(ContextCardEngine.decide(s, h).card)
    }

    @Test fun tiredMoodOffersMediaOnWeekdayEvening() {
        val s = ContextCardSignals(at(21), waterTodayMl = 2000, moodLoggedToday = true, lastMoodToday = JournalMood.TIRED)
        assertEquals(ContextCardType.MEDIA, ContextCardEngine.decide(s, ContextCardHistory()).card?.type)
    }

    @Test fun threeDismissalsSilenceTypeForDays() {
        var h = ContextCardHistory()
        repeat(3) { h = h.dismissed(ContextCardType.MOOD, ms(at(14))) }
        assertEquals(true, (h.snoozeUntil["mood"] ?: 0) - ms(at(14)) >= ContextCardEngine.STREAK_SNOOZE_MS)
    }

    @Test fun disabledShowsNothing() {
        val s = ContextCardSignals(at(14), waterTodayMl = 0)
        assertNull(ContextCardEngine.decide(s, ContextCardHistory(enabled = false)).card)
    }

    @Test fun forcedCardIgnoresTimeAndLimits() {
        val h = ContextCardHistory(forcedType = "book")
        val d = ContextCardEngine.decide(ContextCardSignals(at(8)), h)
        assertEquals(ContextCardType.BOOK, d.card?.type)
        assertNull(d.activate)
        assertEquals("Kitaplığındaki kitap seni bekliyor.", d.card?.message)
    }

    @Test fun forcedCardClearsWhenCompletedOrDismissed() {
        val h = ContextCardHistory(forcedType = "mood")
        assertNull(h.completed(ContextCardType.MOOD, ms(at(14))).forcedType)
        assertNull(h.dismissed(ContextCardType.MOOD, ms(at(14))).forcedType)
        assertEquals("mood", h.completed(ContextCardType.WATER, ms(at(14))).forcedType)
    }

    @Test fun disabledBeatsForced() {
        val h = ContextCardHistory(enabled = false, forcedType = "water")
        assertNull(ContextCardEngine.decide(ContextCardSignals(at(14), waterTodayMl = 0), h).card)
    }
}
