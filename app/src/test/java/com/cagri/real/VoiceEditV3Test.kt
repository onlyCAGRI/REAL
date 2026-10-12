package com.cagri.real

import com.cagri.real.data.ReminderItem
import com.cagri.real.speech.VoiceEditPatch
import com.cagri.real.speech.VoiceEditResolver
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class VoiceEditV3Test {
    private val zone = ZoneId.of("Europe/Istanbul")
    private fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private val now = at("2026-09-30T12:00")
    private val card = ReminderItem(3L, "Toplantı", now, alertAt = at("2026-10-04T08:00"))
    private fun edit(date: String? = null, time: String? = null) = VoiceEditPatch<LocalDate, LocalTime>(
        date = date?.let(LocalDate::parse), time = time?.let(LocalTime::parse))

    @Test fun timeOnlyKeepsCardDayInsteadOfNow() {
        val patch = VoiceEditResolver.resolve(card, edit(time = "09:00"), emptyList(), now, zone).getOrThrow()
        assertEquals(at("2026-10-04T09:00"), patch.alertAt)
    }
    @Test fun namedDayKeepsClockAndRejectsPastWithoutRolling() {
        val tomorrow = VoiceEditResolver.resolve(card, edit(date = "2026-10-01"), emptyList(), now, zone).getOrThrow()
        assertEquals(at("2026-10-01T08:00"), tomorrow.alertAt)
        assertTrue(VoiceEditResolver.resolve(card, edit(date = "2026-09-30"), emptyList(), now, zone).isFailure)
        assertEquals(at("2026-10-04T08:00"), card.alertAt)
    }
    @Test fun absoluteRelativeMoveCanCrossMidnight() {
        val patch = VoiceEditResolver.resolve(card, edit("2026-10-03", "23:00"), emptyList(), now, zone).getOrThrow()
        assertEquals(at("2026-10-03T23:00"), patch.alertAt)
    }
    @Test fun undatedCardDoesNotInventTodayForBareTime() {
        assertTrue(VoiceEditResolver.resolve(card.copy(alertAt = null), edit(time = "09:00"), emptyList(), now, zone).isFailure)
    }
    @Test fun dateOnlyCardKeepsNoClock() {
        val patch = VoiceEditResolver.resolve(card.copy(alertAt = null), edit(date = "2026-10-02"), emptyList(), now, zone).getOrThrow()
        assertTrue(patch.setPlannedDate)
        assertEquals(LocalDate.parse("2026-10-02").toEpochDay(), patch.plannedEpochDay)
        assertFalse(patch.setAlert)
    }
    @Test fun dstGapAndOverlapRequireClarification() {
        val berlin = ZoneId.of("Europe/Berlin")
        for (date in listOf("2027-03-28", "2027-10-31")) {
            assertTrue(VoiceEditResolver.resolve(card, edit(date, "02:30"), emptyList(), now, berlin).isFailure)
        }
    }
}
