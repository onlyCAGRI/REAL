package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.speech.TimeOfDay
import com.cagri.bigboscagri.speech.VoiceReminderParser
import com.cagri.bigboscagri.speech.interpretHour
import com.cagri.bigboscagri.speech.normalizeVerb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class VoiceReminderParserTest {

    private val istanbulZone = ZoneId.of("Europe/Istanbul")
    // Friday, September 18, 2026, 14:00:00
    private val baseFridayNow = ZonedDateTime.of(2026, 9, 18, 14, 0, 0, 0, istanbulZone)

    @Test
    fun bareHourHeuristicDirectTesting() {
        // 1..7 -> PM equivalent (13..19)
        assertEquals(13, interpretHour(1))
        assertEquals(17, interpretHour(5))
        assertEquals(19, interpretHour(7))

        // 8..11 -> AM equivalent (08..11)
        assertEquals(8, interpretHour(8))
        assertEquals(9, interpretHour(9))
        assertEquals(11, interpretHour(11))

        // 12 -> 12:00
        assertEquals(12, interpretHour(12))

        // Explicit 24-hr preserved
        assertEquals(17, interpretHour(17))
        assertEquals(22, interpretHour(22))

        // Overrides with TimeOfDay
        assertEquals(5, interpretHour(5, TimeOfDay.Morning))
        assertEquals(9, interpretHour(9, TimeOfDay.Morning))
        assertEquals(13, interpretHour(1, TimeOfDay.Noon))
        assertEquals(17, interpretHour(5, TimeOfDay.Evening))
        assertEquals(20, interpretHour(8, TimeOfDay.Evening))
        assertEquals(23, interpretHour(11, TimeOfDay.Night))
        assertEquals(1, interpretHour(1, TimeOfDay.Night))
    }

    @Test
    fun tomorrowBareHourFive() {
        val input = "yarın saat 5te annemi ara"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Annemi ara", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 19, 17, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun tomorrowBareHourFiveWithColloquialVerb() {
        val input = "yarın saat 5'te annemi arıcam"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Annemi ara", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 19, 17, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun tomorrowExplicitTimeSeventeenThirty() {
        val input = "yarın 17:30'da annemi ara"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Annemi ara", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 19, 17, 30, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun todayMorningEight() {
        // Morning at 07:00
        val morningNow = ZonedDateTime.of(2026, 9, 18, 7, 0, 0, 0, istanbulZone)
        val input = "bugün saat 8'de markete git"
        val parsed = VoiceReminderParser.parse(input, morningNow)
        assertEquals("Markete git", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 18, 8, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun todayEveningEight() {
        val input = "bugün akşam 8'de markete git"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Markete git", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 18, 20, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun tomorrowMorningFive() {
        val input = "yarın sabah 5te koşuya çık"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Koşuya çık", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 19, 5, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun tomorrowMorningNine() {
        val input = "yarın sabah 9'da doktoru ara"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Doktoru ara", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 19, 9, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun tomorrowNoonOne() {
        val input = "yarın öğlen 1'de yemek ye"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Yemek ye", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 19, 13, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun tomorrowEveningSeven() {
        val input = "yarın akşam 7'de eve git"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Eve git", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 19, 19, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun tomorrowEveningEight() {
        val input = "yarın akşam 8de markete git"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Markete git", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 19, 20, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun tomorrowNightEleven() {
        val input = "yarın gece 11'de ilacı al"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("İlacı al", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 19, 23, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun relativeTenMinutesLater() {
        val input = "10 dakika sonra çamaşırı çıkar"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Çamaşırı çıkar", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 18, 14, 10, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun relativeHalfHourLater() {
        val input = "yarım saat sonra fırını kapat"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Fırını kapat", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 18, 14, 30, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun relativeTwoHoursLater() {
        val input = "2 saat sonra toplantıya gir"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Toplantıya gir", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 18, 16, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun weekdayUpcomingMondayTen() {
        val input = "pazartesi saat 10'da raporu gönder"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Raporu gönder", parsed.text)
        assertNotNull(parsed.alertAt)

        // Friday 18 Sept -> next Monday is 21 Sept
        val expected = ZonedDateTime.of(2026, 9, 21, 10, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun weekdayFridaySameDayFutureTime() {
        // Base is Friday 14:00. Evening 18:00 is later today.
        val input = "cuma akşam 6'da annemi ara"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Annemi ara", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 18, 18, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun weekdayFridaySameDayPastTimeAdvancesToNextWeek() {
        // It's Friday 19:00. Saying "cuma akşam 6'da" means NEXT Friday because 18:00 has already passed today.
        val lateFridayNow = ZonedDateTime.of(2026, 9, 18, 19, 0, 0, 0, istanbulZone)
        val input = "cuma akşam 6da annemi ara"
        val parsed = VoiceReminderParser.parse(input, lateFridayNow)
        assertEquals("Annemi ara", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 25, 18, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
        assertTrue("Produced timestamp must be in the future", parsed.alertAt!! > lateFridayNow.toInstant().toEpochMilli())
    }

    @Test
    fun specificDateSeptemberTwentyThreeFourteen() {
        val input = "23 eylül saat 14'te toplantıya git"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Toplantıya git", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 23, 14, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun specificDateSeptemberTwentyThreeFourteenThirty() {
        val input = "23 eylül 14:30'da toplantıya git"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Toplantıya git", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 23, 14, 30, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun specificDateRollsOverToNextYearIfPassed() {
        // Base is Sep 18, 2026. "15 mayıs" has passed in 2026, so it must be scheduled for May 15, 2027!
        val input = "15 mayıs saat 10'da vize başvurusu yap"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Vize başvurusu yap", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2027, 5, 15, 10, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
        assertTrue(parsed.alertAt!! > baseFridayNow.toInstant().toEpochMilli())
    }

    @Test
    fun monthEndRollover() {
        // Base is March 31, 2026, 10:00. "yarın saat 11'de" -> April 1, 2026
        val endOfMonth = ZonedDateTime.of(2026, 3, 31, 10, 0, 0, 0, istanbulZone)
        val input = "yarın saat 11'de fatura öde"
        val parsed = VoiceReminderParser.parse(input, endOfMonth)
        assertEquals("Fatura öde", parsed.text)

        val expected = ZonedDateTime.of(2026, 4, 1, 11, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun noDateTimeExpressionPreservesTextWithoutAlert() {
        val input = "annemi ara"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Annemi ara", parsed.text)
        assertNull(parsed.alertAt)
    }

    @Test
    fun variousApostropheAndSpaceFormats() {
        val formats = listOf(
            "yarın saat 5'te annemi ara",
            "yarın saat 5te annemi ara",
            "yarın saat 5 te annemi ara",
            "yarın saat 5 de annemi ara",
            "yarın saat 5'de annemi ara"
        )
        val expected = ZonedDateTime.of(2026, 9, 19, 17, 0, 0, 0, istanbulZone).toInstant().toEpochMilli()
        for (f in formats) {
            val parsed = VoiceReminderParser.parse(f, baseFridayNow)
            assertEquals("Annemi ara", parsed.text)
            assertEquals(expected, parsed.alertAt)
        }
    }

    @Test
    fun differentTimezoneNewYork() {
        val nyZone = ZoneId.of("America/New_York")
        val nyNow = ZonedDateTime.of(2026, 9, 18, 14, 0, 0, 0, nyZone)
        val input = "yarın saat 5te annemi ara"
        val parsed = VoiceReminderParser.parse(input, nyNow)
        assertEquals("Annemi ara", parsed.text)

        val expected = ZonedDateTime.of(2026, 9, 19, 17, 0, 0, 0, nyZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun verbNormalizations() {
        assertEquals("ara", normalizeVerb("arıcam"))
        assertEquals("ara", normalizeVerb("arayacağım"))
        assertEquals("kapat", normalizeVerb("kapatıcam"))
        assertEquals("al", normalizeVerb("alıcam"))
        assertEquals("git", normalizeVerb("gidicem"))
        assertEquals("git", normalizeVerb("gideceğim"))
        assertEquals("ye", normalizeVerb("yiyeceğim"))
        // Unknown or regular imperative preserved
        assertEquals("ara", normalizeVerb("ara"))
        assertEquals("kitap", normalizeVerb("kitap"))
    }

    @Test
    fun dailyRecurrenceWithNormalizingVerb() {
        val input = "akşam saat 7 ilaçlarımı alıcam her gün"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("İlaçlarımı al", parsed.text)
        assertEquals(RecurrenceRule.DAILY, parsed.repeatRule)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 18, 19, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun weeklyRecurrenceWithWeekdayAndDaypartBareDigit() {
        val input = "cuma akşam 6 annemi ara her hafta"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Annemi ara", parsed.text)
        assertEquals(RecurrenceRule.WEEKLY, parsed.repeatRule)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 18, 18, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun weeklyRecurrenceWithSpecificWeekdayPrefix() {
        val input = "her pazartesi saat 9 toplantıya gir"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Toplantıya gir", parsed.text)
        assertEquals(RecurrenceRule.WEEKLY, parsed.repeatRule)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun monthlyRecurrenceWithDayOfMonth() {
        val input = "her ayın 5inde faturayı öde"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Faturayı öde", parsed.text)
        assertEquals(RecurrenceRule.MONTHLY, parsed.repeatRule)
        assertNotNull(parsed.alertAt)

        // Sept 5 is in the past relative to baseFridayNow (Sept 18), so rolls to Oct 5, 2026 at 09:00
        val expected = ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun eveningBareWordHourWithoutSaatOrSuffix() {
        val input = "akşam yedi ilaç al"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("İlaç al", parsed.text)
        assertNotNull(parsed.alertAt)

        val expected = ZonedDateTime.of(2026, 9, 18, 19, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }

    @Test
    fun morningBareWordHourWithoutSaatOrSuffix() {
        val input = "sabah sekiz doktora git"
        val parsed = VoiceReminderParser.parse(input, baseFridayNow)
        assertEquals("Doktora git", parsed.text)
        assertNotNull(parsed.alertAt)

        // It is 14:00 today, so 08:00 has passed today -> rolls to tomorrow morning 08:00
        val expected = ZonedDateTime.of(2026, 9, 19, 8, 0, 0, 0, istanbulZone)
        assertEquals(expected.toInstant().toEpochMilli(), parsed.alertAt)
    }
}
