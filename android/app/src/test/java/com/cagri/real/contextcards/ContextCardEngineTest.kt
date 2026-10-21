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

    @Test fun earlyMorningShowsNothing() {
        val s = ContextCardSignals(at(7), waterTodayMl = 0)
        assertNull(ContextCardEngine.decide(s, ContextCardHistory()).card)
    }

    @Test fun commuteMorningOnlyOffersMusic() {
        val d = ContextCardEngine.decide(ContextCardSignals(at(9), waterTodayMl = 0), ContextCardHistory())
        assertEquals(listOf(ContextCardType.LISTEN), d.cards.map { it.type })
    }

    @Test fun onlyOneCardAtATimeBestFirst() {
        val s = ContextCardSignals(at(22), waterTodayMl = 2000, unfinishedBookTitle = "Kitap",
            seriesInProgress = "Dizi", lastEpisode = 2 to 4, journalWrittenToday = false)
        val d = ContextCardEngine.decide(s, ContextCardHistory())
        assertEquals(listOf(ContextCardType.MOOD), d.cards.map { it.type })
        assertEquals(listOf(ContextCardType.MOOD), d.activate)
    }

    @Test fun watchCardNamesTheNextEpisode() {
        val s = ContextCardSignals(at(21), seriesInProgress = "Dizi", lastEpisode = 2 to 4)
        assertEquals("Dizi · 2. sezon 5. bölüm seni bekliyor.", ContextCardEngine.build(ContextCardType.WATCH, s).message)
    }

    @Test fun secondCardComesSeparatelyAfterTheGapAndThenNoMore() {
        val first = ContextCardSignals(at(18), waterTodayMl = 2000, journalWrittenToday = false)
        val d1 = ContextCardEngine.decide(first, ContextCardHistory())
        assertEquals(listOf(ContextCardType.MOOD), d1.activate)
        var h = ContextCardHistory().activated(d1.activate, ms(first.now), first.now.toLocalDate().toEpochDay())
            .completed(ContextCardType.MOOD, ms(first.now))
        assertNull(ContextCardEngine.decide(first.copy(now = at(20)), h).card)
        val d2 = ContextCardEngine.decide(first.copy(now = at(21), moodLoggedToday = true), h)
        assertEquals(listOf(ContextCardType.JOURNAL), d2.activate)
        h = h.activated(d2.activate, ms(at(21)), at(21).toLocalDate().toEpochDay()).dismissed(ContextCardType.JOURNAL, ms(at(21)))
        val late = ContextCardSignals(at(23, day = 6).plusMinutes(59), waterTodayMl = 2000, moodLoggedToday = true, unfinishedBookTitle = "Kitap")
        assertNull(ContextCardEngine.decide(late, h).card)
    }

    @Test fun journalCardWaitsForJournalData() {
        val s = ContextCardSignals(at(22), moodLoggedToday = true)
        assertEquals(false, ContextCardEngine.decide(s, ContextCardHistory()).cards.any { it.type == ContextCardType.JOURNAL })
        assertEquals(false, ContextCardEngine.decide(s.copy(journalWrittenToday = true), ContextCardHistory()).cards.any { it.type == ContextCardType.JOURNAL })
    }

    @Test fun forceAllShowsEveryCardUntilEachIsActedOn() {
        var h = ContextCardHistory(forcedType = ContextCardEngine.FORCE_ALL)
        assertEquals(ContextCardType.entries.size, ContextCardEngine.decide(ContextCardSignals(at(8)), h).cards.size)
        h = h.completed(ContextCardType.WATER, ms(at(8)))
        assertEquals(ContextCardType.entries.size - 1, ContextCardEngine.decide(ContextCardSignals(at(8)), h).cards.size)
        ContextCardType.entries.forEach { h = h.dismissed(it, ms(at(8))) }
        assertNull(h.forcedType)
    }

    @Test fun lowWaterAfternoonShowsWaterFirst() {
        val s = ContextCardSignals(at(14), waterTodayMl = 0)
        val d = ContextCardEngine.decide(s, ContextCardHistory())
        assertEquals(ContextCardType.WATER, d.card?.type)
        assertEquals(listOf(ContextCardType.WATER), d.activate)
    }

    @Test fun activeCardIsStickyAndNotReactivated() {
        val first = ContextCardSignals(at(14), waterTodayMl = 0)
        val h = ContextCardHistory().activated(ContextCardType.WATER, ms(first.now), first.now.toLocalDate().toEpochDay())
        val d = ContextCardEngine.decide(ContextCardSignals(at(15), waterTodayMl = 0), h)
        assertEquals(ContextCardType.WATER, d.card?.type)
        assertEquals(emptyList<ContextCardType>(), d.activate)
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
        assertEquals(false, ContextCardEngine.decide(s, ContextCardHistory()).cards.any { it.type == ContextCardType.MOOD })
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
        assertEquals(emptyList<ContextCardType>(), d.activate)
        assertEquals("Kitaplığındaki kitap seni bekliyor.", d.card?.message)
    }

    @Test fun forcedCardClearsWhenCompletedOrDismissed() {
        val h = ContextCardHistory(forcedType = "mood")
        assertNull(h.completed(ContextCardType.MOOD, ms(at(14))).forcedType)
        assertNull(h.dismissed(ContextCardType.MOOD, ms(at(14))).forcedType)
        assertEquals("mood", h.completed(ContextCardType.WATER, ms(at(14))).forcedType)
    }

    @Test fun freshInstallStaysQuietForADay() {
        val installed = ms(at(13))
        assertNull(ContextCardEngine.decide(ContextCardSignals(at(14), waterTodayMl = 0, installedAt = installed), ContextCardHistory()).card)
        val nextDay = ContextCardEngine.decide(ContextCardSignals(at(14, day = 7), waterTodayMl = 0, installedAt = installed), ContextCardHistory())
        assertEquals(ContextCardType.WATER, nextDay.card?.type)
        // The Settings test hook still works right after install.
        val forced = ContextCardHistory(forcedType = "water")
        assertEquals(ContextCardType.WATER, ContextCardEngine.decide(ContextCardSignals(at(14), waterTodayMl = 0, installedAt = installed), forced).card?.type)
    }

    @Test fun disabledBeatsForced() {
        val h = ContextCardHistory(enabled = false, forcedType = "water")
        assertNull(ContextCardEngine.decide(ContextCardSignals(at(14), waterTodayMl = 0), h).card)
    }
}
