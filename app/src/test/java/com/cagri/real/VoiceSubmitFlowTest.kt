package com.cagri.real

import com.cagri.real.data.ChecklistItem
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.speech.VoiceDraft
import com.cagri.real.speech.VoiceDraftSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class VoiceSubmitFlowTest {

    data class SaveCall(
        val text: String,
        val alertAt: Long?,
        val repeatRule: RecurrenceRule,
        val categoryId: Long?,
        val isChecklist: Boolean,
        val items: List<ChecklistItem>,
        val sendImmediate: Boolean
    )

    @Test
    fun secondMicTapDoesNotSaveReminder() {
        val saveCalls = mutableListOf<SaveCall>()
        val session = VoiceDraftSession()

        
        session.onVoiceFinalize("yarın saat beşte annemi ara")

        
        assertTrue(saveCalls.isEmpty())

        
        assertTrue(session.isVoiceDraft)
        assertEquals("Annemi ara", session.text)
        assertNotNull(session.alertAt)
    }

    @Test
    fun secondMicTapFillsDraftStateWithParsedValues() {
        val session = VoiceDraftSession()

        
        session.onVoiceFinalize("akşam saat yedi ilaçlarımı alıcam her gün")

        assertEquals("İlaçlarımı al", session.text)
        assertNotNull(session.alertAt)

        val localTime = Instant.ofEpochMilli(session.alertAt!!)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        assertEquals(LocalTime.of(19, 0), localTime)
        assertEquals(RecurrenceRule.DAILY, session.repeatRule)
        assertFalse(session.sendImmediateNotification)
        assertTrue(session.isVoiceDraft)
    }

    @Test
    fun categoryCanBeSelectedAfterVoiceFinalize() {
        val session = VoiceDraftSession()

        session.onVoiceFinalize("akşam yedi ilaç al")
        assertEquals("İlaç al", session.text)
        assertNull(session.customCategoryId)

        
        session.customCategoryId = 101L
        assertEquals(101L, session.customCategoryId)
        assertEquals("İlaç al", session.text)
        assertNotNull(session.alertAt)
    }

    @Test
    fun explicitCheckSavesReminderExactlyOnce() = runBlocking {
        val saveCalls = mutableListOf<SaveCall>()
        val session = VoiceDraftSession()

        session.onVoiceFinalize("akşam yedi ilaç al")
        session.customCategoryId = 101L

        val mockOnAdd: suspend (String, Long?, RecurrenceRule, Long?, Boolean, List<ChecklistItem>, Boolean) -> Boolean =
            { text, alertAt, rule, catId, isChecklist, items, sendImmediate ->
                saveCalls.add(SaveCall(text, alertAt, rule, catId, isChecklist, items, sendImmediate))
                true
            }

        
        val saved = session.saveExplicitly(mockOnAdd)
        assertTrue(saved)
        assertEquals(1, saveCalls.size)

        val call = saveCalls.first()
        assertEquals("İlaç al", call.text)
        assertNotNull(call.alertAt)
        assertEquals(101L, call.categoryId)
        assertFalse(call.isChecklist)
        assertTrue(call.items.isEmpty())

        
        assertFalse(session.isVoiceDraft)
        assertEquals("", session.text)
        assertNull(session.alertAt)
        assertEquals(RecurrenceRule.NONE, session.repeatRule)

        
        val savedAgain = session.saveExplicitly(mockOnAdd)
        assertFalse(savedAgain)
        assertEquals(1, saveCalls.size)
    }

    @Test
    fun reopeningMicPreservesExistingTranscriptAndAlert() {
        val session = VoiceDraftSession()

        
        session.onVoiceFinalize("yarın saat beşte annemi ara")
        assertEquals("Annemi ara", session.text)
        val initialAlert = session.alertAt
        assertNotNull(initialAlert)

        
        val voiceDraft = VoiceDraft(session.text)
        assertEquals("Annemi ara", voiceDraft.committed)

        
        voiceDraft.updatePartial("ve marketten süt al")
        voiceDraft.commitResult("ve marketten süt al")
        assertEquals("Annemi ara ve marketten süt al", voiceDraft.text)

        
        session.onVoiceFinalize(voiceDraft.text)
        assertEquals("Annemi ara ve marketten süt al", session.text)

        
        assertEquals(initialAlert, session.alertAt)
        assertTrue(session.isVoiceDraft)
    }

    @Test
    fun backCancelsDraftWithoutSaving() {
        val saveCalls = mutableListOf<SaveCall>()
        val session = VoiceDraftSession()

        session.onVoiceFinalize("yarın saat 5te toplantıya gir")
        assertTrue(session.isVoiceDraft)

        
        val result1 = session.handleBack(isEditing = true)
        assertEquals(VoiceDraftSession.BackResult.DismissKeyboard, result1)
        assertTrue(session.isVoiceDraft)
        assertTrue(saveCalls.isEmpty())

        
        val result2 = session.handleBack(isEditing = false)
        assertEquals(VoiceDraftSession.BackResult.CancelDraft, result2)
        assertFalse(session.isVoiceDraft)
        assertEquals("", session.text)
        assertNull(session.alertAt)

        
        assertTrue(saveCalls.isEmpty())
    }
}
