package com.cagri.real

import com.cagri.real.data.*
import com.cagri.real.speech.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Existing action executor must retain native confirmation and idempotent completion semantics. */
class LocalVoiceActionSafetyTest {
    private val card=ReminderItem(91,"Dambıl al",1)
    @Test fun localDeleteOnlyPreparesConfirmation() = runBlocking {
        var plan:VoiceDeletePlan?=null
        var toggles=0
        runVoiceCardAction(VoiceAction(VoiceActionType.DELETE,card.id),null,null,listOf(card),emptyList(),
            VoiceCardCallbacks(),{_,_->fail("Delete must not edit")},{toggles++},{fail("Use native single-card delete prompt")},{},
            onDeletePlan={plan=it})
        assertNotNull(plan)
        assertEquals(0,toggles)
        assertTrue(plan!!.stillMatches(card))
        assertFalse(plan!!.stillMatches(card.copy(updatedAt=2)))
    }
    @Test fun reopeningCompletedCardTogglesOnceButOpenCardDoesNot() = runBlocking {
        var toggles=0
        suspend fun apply(item:ReminderItem)=runVoiceCardAction(VoiceAction(VoiceActionType.REOPEN,item.id),null,null,
            listOf(item),emptyList(),VoiceCardCallbacks(),{_,_->fail("No content edit")},{toggles++},{},{})
        assertTrue(apply(card.copy(completedAt=2)).success)
        assertEquals(1,toggles)
        assertFalse(apply(card).success)
        assertEquals(1,toggles)
    }
    @Test fun unknownIdCannotMutateAnything() = runBlocking {
        var toggles=0
        val result=runVoiceCardAction(VoiceAction(VoiceActionType.COMPLETE,999),null,null,listOf(card),emptyList(),
            VoiceCardCallbacks(),{_,_->fail("No edit")},{toggles++},{},{})
        assertFalse(result.success)
        assertEquals(0,toggles)
    }
}
