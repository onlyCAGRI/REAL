package com.cagri.real

import com.cagri.real.data.*
import com.cagri.real.speech.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

/** Regression sources supplied for local validation; not run when preparing the package. */
class VoiceUpgradeTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private val now = at("2026-10-04T10:00")
    private val card = ReminderItem(42L,"Spora git",now,alertAt=at("2026-10-04T20:00"),
        repeatRule=RecurrenceRule.DAILY,repeatUntilEpochDay=LocalDate.parse("2026-11-03").toEpochDay())

    @Test fun changingDaysKeepsClockAndEndAndMovesFirstAlarmToMatchingDay() {
        val edit = VoiceEditPatch<LocalDate,LocalTime>(recurrence=RecurrenceRule.WEEKLY,weekdaysMask=(1 shl 2) or (1 shl 4))
        val patch=VoiceEditResolver.resolve(card,edit,emptyList(),now,zone).getOrThrow()
        assertEquals(at("2026-10-07T20:00"),patch.alertAt)
        assertEquals(card.repeatUntilEpochDay,patch.untilEpochDay)
        assertEquals(edit.weekdaysMask,patch.weekdaysMask)
    }
    @Test fun invalidEndIsRejectedInsteadOfMakingAnUnlimitedRepeat() {
        val edit=VoiceEditPatch<LocalDate,LocalTime>(recurrence=RecurrenceRule.WEEKLY,weekdaysMask=1 shl 2,
            repeatUntil=LocalDate.parse("2026-10-01"))
        assertTrue(VoiceEditResolver.resolve(card,edit,emptyList(),now,zone).isFailure)
    }
    @Test fun missingClockForRepeatIsRejectedForAnExistingUndatedCard() {
        val edit=VoiceEditPatch<LocalDate,LocalTime>(recurrence=RecurrenceRule.WEEKLY,weekdaysMask=1 shl 2)
        assertTrue(VoiceEditResolver.resolve(card.copy(alertAt=null),edit,emptyList(),now,zone).isFailure)
    }
    @Test fun spokenCategorySpellingCanResolveWithoutCreatingADuplicate() {
        val category=CustomCategory(5,"Çalışma","WORK",0)
        assertEquals(category,findVoiceCategory("calisma",listOf(category)))
        assertNull(findVoiceCategory("calisma",listOf(category,category.copy(id=6))))
    }
    @Test fun partialTitleIsRankedAboveUnrelatedCardButDoesNotExecuteAnything() {
        assertTrue(voiceTitleRelevance("sporu sil","Spora git",null) > voiceTitleRelevance("sporu sil","Kitap oku",null))
    }
    @Test fun namedOldCardIsIncludedEvenWhenFifteenOtherCardsAreNearer() {
        val recent=(1L..20L).map { ReminderItem(it,"Diğer görev $it",now,alertAt=now+it*60000) } +
            card.copy(id=99,alertAt=null,text="Dambıl ve ip al")
        assertTrue(VoiceRecentCards.build(recent,emptyList(),null,now,zone,"dambıl kartını tamamla").any { it.id==99L })
    }
    @Test fun deleteConfirmationRefusesAChangedOrMissingCard() {
        val plan=voiceDeletePlan(card,now)
        assertTrue(plan.stillMatches(card))
        assertNotNull(plan.occurrenceMillis)
        assertFalse(plan.stillMatches(card.copy(updatedAt=now+1)))
        assertFalse(plan.stillMatches(null))
    }

    @Test fun weeklyCreationUsesNextMatchingDayAndRetainsRealRepeatRule() {
        val obj=JSONObject().put("title","Spora git").put("date","2026-10-04").put("time","20:00")
            .put("weekdays",JSONArray(listOf("WED","FRI"))).put("repeatUntil","2026-11-03").put("hasSchedule",false)
        val intent=GeminiVoiceIntentClient.parseIntentObject(obj,"spor",LocalDate.parse("2026-10-04"))
        val schedule=resolveVoiceCreationSchedule(intent,now,zone)
        assertEquals(at("2026-10-07T20:00"),schedule.alertAt)
        assertEquals(RecurrenceRule.WEEKLY,schedule.repeatRule)
        assertTrue(intent.hasSchedule)
    }
    @Test fun missingTimeKeepsTheRequestedRepeatAndRequiresAClockBeforeSave() {
        val obj=JSONObject().put("title","Spora git").put("weekdays",JSONArray(listOf("WED")))
        val schedule=resolveVoiceCreationSchedule(
            GeminiVoiceIntentClient.parseIntentObject(obj,"spor",LocalDate.parse("2026-10-04")),now,zone)
        assertTrue(schedule.needsTime)
        assertNull(schedule.alertAt)
        assertEquals(RecurrenceRule.WEEKLY,schedule.repeatRule)
    }
    @Test fun dailyCorrectionStartsAtTheNextClockRatherThanKeepingAnOldWeeklyDay() {
        val edit=VoiceEditPatch<LocalDate,LocalTime>(recurrence=RecurrenceRule.DAILY)
        val patch=VoiceEditResolver.resolve(card.copy(alertAt=at("2026-10-10T20:00")),edit,emptyList(),now,zone).getOrThrow()
        assertEquals(at("2026-10-04T20:00"),patch.alertAt)
        assertEquals(card.repeatUntilEpochDay,patch.untilEpochDay)
    }
    @Test fun semanticTitleCorrectionOnlyChangesTextAndKeepsTheExistingScheduleAndContent() {
        val edit=VoiceEditPatch<LocalDate,LocalTime>(newTitle="Babamı ara")
        val patch=VoiceEditResolver.resolve(card.copy(text="Annemi ara"),edit,emptyList(),now,zone).getOrThrow()
        assertEquals("Babamı ara",patch.text)
        assertFalse(patch.setAlert)
        assertFalse(patch.setPlannedDate)
        assertFalse(patch.setRepeat)
        assertFalse(patch.setCategory)
        assertFalse(patch.setLocation)
    }
}
