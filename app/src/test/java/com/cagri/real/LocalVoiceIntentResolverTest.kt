package com.cagri.real

import com.cagri.real.data.*
import com.cagri.real.speech.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

/** Focused regression sources; not run while preparing this package. */
class LocalVoiceIntentResolverTest {
    private val now=ZonedDateTime.of(2026,10,9,10,0,0,0,ZoneId.of("Europe/Istanbul"))
    private val day=now.toLocalDate()
    private val alarm=day.plusDays(1).atTime(20,0).atZone(now.zone).toInstant().toEpochMilli()
    private val call=ReminderItem(1,"Annemi ara",now.toInstant().toEpochMilli(),alertAt=alarm)
    private val sport=call.copy(id=2,text="Spora git",repeatRule=RecurrenceRule.DAILY,
        repeatUntilEpochDay=day.plusMonths(1).toEpochDay())
    private val gym=CustomCategory(3,"Spor","BOOKMARK",0)
    private fun parse(text:String,cards:List<ReminderItem> = listOf(call,sport),last:ReminderItem?=call,
        alternatives:List<String> = emptyList(),timeReply:Boolean=false)=LocalVoiceIntentResolver.resolve(
            text,cards,listOf(gym),last,now,alternatives,timeReply)

    @Test fun tomorrowEveningUsesDeviceDateAndNativeSchedule() {
        val intent=parse("yarın akşam saat sekizde spora gitcem")!!.intents.single()
        assertEquals("Spora git",intent.title)
        assertEquals(day.plusDays(1),intent.date)
        assertEquals(LocalTime.of(20,0),intent.time)
        assertFalse(intent.requiresTimeSelection)
        assertEquals(alarm,resolveVoiceCreationSchedule(intent,now.toInstant().toEpochMilli(),now.zone).alertAt)
    }
    @Test fun weekdayRoutineWithOneMonthHasMaskAndInclusiveEnd() {
        val intent=parse("pazartesi çarşamba cuma akşam saat sekizde spora gidicem sadece bir ay")!!.intents.single()
        assertEquals(RecurrenceRule.WEEKLY,intent.recurrence)
        assertEquals(21,intent.weekdaysMask)
        assertEquals(day.plusMonths(1).minusDays(1),intent.repeatUntil) // Friday is the first matching future occurrence.
        assertEquals(LocalTime.of(20,0),intent.time)
    }
    @Test fun weekdayCorrectionKeepsOriginalClockAndBoundedSeries() {
        val parsed=parse("hayır her çarşamba cuma",last=sport)!!
        assertEquals(sport.id,parsed.action!!.targetId)
        val patch=VoiceEditResolver.resolve(sport,parsed.edit!!,listOf(gym),now.toInstant().toEpochMilli(),now.zone).getOrThrow()
        assertEquals(20,patch.weekdaysMask)
        assertEquals(sport.repeatUntilEpochDay,patch.untilEpochDay)
        assertEquals(LocalTime.of(20,0),Instant.ofEpochMilli(patch.alertAt!!).atZone(now.zone).toLocalTime())
    }
    @Test fun ambiguousEightAsksInsteadOfChoosingAmPm() {
        val intent=parse("yarın saat sekizde spora gitcem")!!.intents.single()
        assertNull(intent.time)
        assertTrue(intent.requiresTimeSelection)
    }
    @Test fun localClockReplyUsesInheritedEveningAndKeepsNoOtherFields() {
        val intent=parse("akşam sekiz",timeReply=true)!!.intents.single()
        assertEquals(LocalTime.of(20,0),intent.time)
        assertNull(parse("akşam sekiz ayrıca film öner",timeReply=true))
    }
    @Test fun plainDateNoteDoesNotInventAClock() {
        val intent=parse("yarın annemi ara")!!.intents.single()
        assertEquals(day.plusDays(1),intent.date)
        assertNull(intent.time)
        assertFalse(intent.requiresTimeSelection)
    }
    @Test fun relativeTwoHoursReusesNativeRelativeParser() {
        val intent=parse("iki saat sonra annemi ara")!!.intents.single()
        assertEquals(day,intent.date)
        assertEquals(LocalTime.of(12,0),intent.time)
    }
    @Test fun pastExplicitDateRemainsLiteralAndNativeValidatorRejectsIt() {
        val intent=parse("2026-10-08 saat 20:00 annemi ara")!!.intents.single()
        assertEquals(day.minusDays(1),intent.date)
        assertTrue(runCatching { resolveVoiceCreationSchedule(intent,now.toInstant().toEpochMilli(),now.zone) }.isFailure)
    }
    @Test fun newCategoryCreatesOneNativeIntentPerGoal() {
        val parsed=parse("matematik kategorisi aç içine trigonometri, açılar, üçgenler ve problemler koy")!!
        assertEquals("Matematik",parsed.newCategory!!.name)
        assertEquals(listOf("Trigonometri","Açılar","Üçgenler","Problemler"),parsed.intents.map { it.title })
        assertTrue(parsed.intents.all { it.categoryName=="Matematik" })
    }
    @Test fun existingCategoryGetsNounNoteWithoutNewCategory() {
        val parsed=parse("spor kategorisine dambıl koy")!!
        assertNull(parsed.newCategory)
        assertEquals("Dambıl",parsed.intents.single().title)
        assertEquals("Spor",parsed.intents.single().categoryName)
    }
    @Test fun checklistCreationUsesExistingRows() {
        val parsed=parse("alışveriş listesi oluştur süt, ekmek ve yumurta")!!.intents.single()
        assertTrue(parsed.isChecklist)
        assertEquals(3,parsed.checklistItems.size)
    }
    @Test fun checklistAppendTargetsOnlyChecklistCard() {
        val list=call.copy(id=4,text="Alışveriş listesi",isChecklist=true)
        val parsed=parse("alışveriş listesine süt ve ekmek ekle",cards=listOf(list),last=null)!!
        assertEquals(VoiceActionType.ADD_CHECKLIST_ITEMS,parsed.action!!.type)
        assertEquals(list.id,parsed.action!!.targetId)
        assertEquals(listOf("Süt","Ekmek"),parsed.action!!.checklistItems)
    }
    @Test fun simpleParentCorrectionOnlyChangesTitle() {
        val parsed=parse("ay hayır babamı")!!
        assertEquals("Babamı ara",parsed.edit!!.newTitle)
        val patch=VoiceEditResolver.resolve(call,parsed.edit!!,emptyList(),now.toInstant().toEpochMilli(),now.zone).getOrThrow()
        assertFalse(patch.setAlert)
        assertFalse(patch.setRepeat)
    }
    @Test fun tomorrowCorrectionMustNotRenameParentToTomorrow() {
        val parsed=parse("hayır yarın")!!
        assertNull(parsed.edit!!.newTitle)
        assertEquals(day.plusDays(1),parsed.edit!!.date)
    }
    @Test fun clockOnlyEditKeepsOriginalCardDay() {
        val parsed=parse("annemi ara kartını saat 21:00 al")!!
        assertNull(parsed.edit!!.date)
        assertEquals(LocalTime.of(21,0),parsed.edit!!.time)
        val patch=VoiceEditResolver.resolve(call,parsed.edit!!,emptyList(),now.toInstant().toEpochMilli(),now.zone).getOrThrow()
        assertEquals(day.plusDays(1),Instant.ofEpochMilli(patch.alertAt!!).atZone(now.zone).toLocalDate())
    }
    @Test fun postponeIsRelativeToExistingAlarm() {
        val parsed=parse("annemi ara iki saat ertele")!!
        assertEquals(VoiceActionType.POSTPONE,parsed.action!!.type)
        assertEquals(LocalTime.of(22,0),parsed.edit!!.time)
        assertEquals(day.plusDays(1),parsed.edit!!.date)
    }
    @Test fun namedOldCardIsNotLimitedToFifteenAiCandidates() {
        val old=call.copy(id=100,text="Dambıl al")
        val cards=(1L..25L).map { call.copy(id=it,text="Başka görev $it") }+old
        assertEquals(old.id,parse("dambıl sil",cards=cards,last=null)!!.action!!.targetId)
    }
    @Test fun duplicateOrPartialCollisionFallsBackWithoutLocalMutation() {
        assertNull(parse("annemi ara sil",cards=listOf(call,call.copy(id=5)),last=null))
        assertNull(parse("dambıl sil",cards=listOf(call.copy(text="Dambıl al"),sport.copy(text="Dambıl sat")),last=null))
    }
    @Test fun completedUndoUsesExplicitNativeReopenAction() {
        val completed=call.copy(completedAt=now.toInstant().toEpochMilli())
        assertEquals(VoiceActionType.REOPEN,parse("annemi ara tamamlamayı geri al",cards=listOf(completed),last=null)!!.action!!.type)
    }
    @Test fun bulkRemainsAConfirmationPlan() {
        val parsed=parse("bugünkülerin hepsini yarına at")!!
        assertEquals(VoiceActionType.BULK,parsed.action!!.type)
        assertEquals(VoiceBulkScope.TODAY,parsed.action!!.bulk!!.scope)
        assertEquals(day.plusDays(1).toString(),parsed.action!!.bulk!!.newDate)
    }
    @Test fun explicitMultipleTasksAllResolveOrAllGoToAi() {
        assertEquals(2,parse("yarın saat 20:00 spora git ayrıca bugün saat 21:00 annemi ara")!!.intents.size)
        assertNull(parse("yarın spora git ayrıca annemi ara")) // Scope inheritance is ambiguous.
        assertNull(parse("yarın spora git ayrıca bana film öner")) // No partial local save.
    }
    @Test fun contradictoryRecognitionAlternativesCannotAuthorizeDeletion() {
        assertNull(parse("annemi ara sil",alternatives=listOf("spora git sil")))
        assertEquals(VoiceActionType.DELETE,parse("annemi ara sil",alternatives=listOf("Annemi ara sil."))!!.action!!.type)
    }
    @Test fun negationConditionalCreativeAndComplexTemporalPhrasesStayWithGroq() {
        listOf("annemi ara kartını silme","eğer yağmur yağarsa spora git","bana film önersene",
            "bu akşam saat sekizde Silo izleyeceğim","yarın akşam sekizi çeyrek geçe annemi ara",
            "yarın bugün annemi ara","yarın saat 25:00 annemi ara","yarın 45 dakika ders çalış",
            "yarın Ali ile buluşacağım","pazartesi spora git ve cuma annemi ara").forEach { assertNull(it,parse(it)) }
    }
    @Test fun unresolvedDaySuffixAndMidnightCannotBecomePlainOrWrongDayCards() {
        assertNull(parse("salıya annemi ara"))
        assertNull(parse("bu gece saat birde annemi ara"))
        assertNull(parse("gelecek çarşamba annemi ara"))
    }
    @Test fun buEveningIsNotLeftInTaskTitle() {
        assertEquals("Spora git",parse("bu akşam saat sekizde spora gitcem")!!.intents.single().title)
    }
    @Test fun explicitMonthDateSuffixIsRecognized() {
        assertEquals(LocalDate.of(2026,10,12),parse("12 Ekimde annemi ara")!!.intents.single().date)
    }
    @Test fun missingLastCardDoesNotExecuteBareDelete() { assertNull(parse("sil",last=null)) }
}
