package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.ChecklistItem
import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.speech.VoiceDraft
import com.cagri.bigboscagri.speech.VoiceDraftSession
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

        // User speaks and taps mic second time to finalize
        session.onVoiceFinalize("yarın saat beşte annemi ara")

        // 1. Reminder must NOT be saved to repository
        assertTrue(saveCalls.isEmpty())

        // 2. Draft state must be open as voice draft
        assertTrue(session.isVoiceDraft)
        assertEquals("Annemi ara", session.text)
        assertNotNull(session.alertAt)
    }

    @Test
    fun secondMicTapFillsDraftStateWithParsedValues() {
        val session = VoiceDraftSession()

        // "akşam saat yedi ilaçlarımı alıcam her gün"
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

        // User picks category from UI
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

        // Explicit ✓ button clicked
        val saved = session.saveExplicitly(mockOnAdd)
        assertTrue(saved)
        assertEquals(1, saveCalls.size)

        val call = saveCalls.first()
        assertEquals("İlaç al", call.text)
        assertNotNull(call.alertAt)
        assertEquals(101L, call.categoryId)
        assertFalse(call.isChecklist)
        assertTrue(call.items.isEmpty())

        // Draft state is reset
        assertFalse(session.isVoiceDraft)
        assertEquals("", session.text)
        assertNull(session.alertAt)
        assertEquals(RecurrenceRule.NONE, session.repeatRule)

        // Subsequent clicks do not save again
        val savedAgain = session.saveExplicitly(mockOnAdd)
        assertFalse(savedAgain)
        assertEquals(1, saveCalls.size)
    }

    @Test
    fun reopeningMicPreservesExistingTranscriptAndAlert() {
        val session = VoiceDraftSession()

        // 1st speech
        session.onVoiceFinalize("yarın saat beşte annemi ara")
        assertEquals("Annemi ara", session.text)
        val initialAlert = session.alertAt
        assertNotNull(initialAlert)

        // User taps mic again to resume speech
        val voiceDraft = VoiceDraft(session.text)
        assertEquals("Annemi ara", voiceDraft.committed)

        // User says "ve marketten süt al"
        voiceDraft.updatePartial("ve marketten süt al")
        voiceDraft.commitResult("ve marketten süt al")
        assertEquals("Annemi ara ve marketten süt al", voiceDraft.text)

        // 2nd speech finalize
        session.onVoiceFinalize(voiceDraft.text)
        assertEquals("Annemi ara ve marketten süt al", session.text)

        // Alert from round 1 is preserved and not duplicated or reset to null
        assertEquals(initialAlert, session.alertAt)
        assertTrue(session.isVoiceDraft)
    }

    @Test
    fun backCancelsDraftWithoutSaving() {
        val saveCalls = mutableListOf<SaveCall>()
        val session = VoiceDraftSession()

        session.onVoiceFinalize("yarın saat 5te toplantıya gir")
        assertTrue(session.isVoiceDraft)

        // 1. If keyboard is open, Back dismisses keyboard first
        val result1 = session.handleBack(isEditing = true)
        assertEquals(VoiceDraftSession.BackResult.DismissKeyboard, result1)
        assertTrue(session.isVoiceDraft)
        assertTrue(saveCalls.isEmpty())

        // 2. Second Back closes the voice draft without saving
        val result2 = session.handleBack(isEditing = false)
        assertEquals(VoiceDraftSession.BackResult.CancelDraft, result2)
        assertFalse(session.isVoiceDraft)
        assertEquals("", session.text)
        assertNull(session.alertAt)

        // Reminder repository is NEVER called
        assertTrue(saveCalls.isEmpty())
    }
}
