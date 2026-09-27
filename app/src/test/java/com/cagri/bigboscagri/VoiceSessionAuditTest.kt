package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.ChecklistItem
import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.speech.VoiceDraft
import com.cagri.bigboscagri.speech.VoiceDraftCoordinator
import com.cagri.bigboscagri.speech.VoiceDraftSession
import com.cagri.bigboscagri.speech.VoicePhase
import com.cagri.bigboscagri.speech.VoiceReminderParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test





class VoiceSessionAuditTest {

    


    private class SimulatedVoiceController(
        val onText: (String) -> Unit,
        val onSubmit: (String) -> Unit,
        val onErrorMessage: (String) -> Unit
    ) {
        var generation: Long = 0L
        var round: Long = 0L
        var phase: VoicePhase = VoicePhase.Idle
        var roundRunning: Boolean = false
        var draft: VoiceDraft? = null
        var newRoundsStarted: Int = 0
        var submitCallCount: Int = 0

        fun start(initialText: String = "") {
            generation++
            round = 0L
            draft = VoiceDraft(initialText)
            phase = VoicePhase.Listening
            roundRunning = true
            newRoundsStarted++
        }

        fun onPartial(session: Long, thisRound: Long, partialText: String) {
            if (!isValid(session, thisRound)) return
            draft?.updatePartial(partialText)
        }

        fun onResults(session: Long, thisRound: Long, finalText: String?) {
            if (!isValid(session, thisRound)) return
            roundRunning = false
            draft?.commitResult(finalText)
            if (phase == VoicePhase.Finishing) {
                submitOnce()
            } else {
                nextRound(session)
            }
        }

        fun onError(session: Long, thisRound: Long, errorMsg: String) =
            onError(session, thisRound, 1, errorMsg)

        fun onError(session: Long, thisRound: Long, errorCode: Int, errorMsg: String) {
            if (!isValid(session, thisRound)) return
            roundRunning = false
            if (phase == VoicePhase.Finishing) {
                submitOnce()
            } else when (errorCode) {
                1, 2 -> { 
                    draft?.commitResult(null)
                    nextRound(session)
                }
                else -> fail(errorMsg)
            }
        }

        fun finish(simulateActiveRecognizer: Boolean = false): Long {
            if (phase != VoicePhase.Listening) return generation
            phase = VoicePhase.Finishing
            val watchdogSession = generation
            if (!simulateActiveRecognizer || !roundRunning) {
                submitOnce()
            }
            return watchdogSession
        }

        fun fireWatchdog(session: Long) {
            if (generation == session && phase == VoicePhase.Finishing) {
                submitOnce()
            }
        }

        private fun nextRound(session: Long) {
            if (generation != session || phase != VoicePhase.Listening) return
            round++
            roundRunning = true
            newRoundsStarted++
        }

        fun cancel() {
            if (phase != VoicePhase.Idle) draft?.text?.let(onText)
            reset()
        }

        fun submitOnce() {
            if (phase != VoicePhase.Finishing) return
            submitCallCount++
            val currentDraft = draft
            currentDraft?.commitResult(null)
            val text = currentDraft?.text.orEmpty()
            val hasSpoken = currentDraft?.hasSpokenText == true
            reset()
            if (hasSpoken && text.isNotBlank()) {
                onSubmit(text)
            } else {
                currentDraft?.text?.let(onText)
                onErrorMessage("Konuşma algılanmadı. Tekrar deneyebilirsin.")
            }
        }

        private fun fail(message: String) {
            draft?.text?.let(onText)
            reset()
            onErrorMessage(message)
        }

        private fun reset() {
            generation++
            phase = VoicePhase.Idle
            roundRunning = false
            draft = null
        }

        private fun isValid(session: Long, thisRound: Long): Boolean {
            return generation == session && round == thisRound && phase != VoicePhase.Idle && roundRunning
        }
    }

    
    
    

    @Test
    fun staleSessionResultIgnored() {
        var submittedText: String? = null
        var errorOccurred: String? = null
        val controller = SimulatedVoiceController(
            onText = {},
            onSubmit = { submittedText = it },
            onErrorMessage = { errorOccurred = it }
        )

        controller.start("İlk metin")
        val session1 = controller.generation
        val round1 = controller.round

        controller.start("İkinci metin")
        val session2 = controller.generation
        val round2 = controller.round

        controller.onPartial(session1, round1, "geç gelen kısmi sonuç")
        controller.onResults(session1, round1, "geç gelen final sonuç")
        controller.onError(session1, round1, "geç gelen hata")

        assertEquals("İkinci metin", controller.draft?.text)
        assertNull(submittedText)
        assertNull(errorOccurred)

        controller.onPartial(session2, round2, "yeni ses")
        assertEquals("İkinci metin yeni ses", controller.draft?.text)
    }

    @Test
    fun rapidSessionReplacementPreservesNewestSession() {
        var submittedText: String? = null
        var restoredText: String? = null
        var errorMsg: String? = null

        val controller = SimulatedVoiceController(
            onText = { restoredText = it },
            onSubmit = { submittedText = it },
            onErrorMessage = { errorMsg = it }
        )

        controller.start("Taslak 1")
        val s1 = controller.generation
        val r1 = controller.round

        controller.cancel()
        assertEquals("Taslak 1", restoredText)
        assertEquals(VoicePhase.Idle, controller.phase)

        controller.start("Taslak 2")
        val s2 = controller.generation
        val r2 = controller.round

        controller.onResults(s1, r1, "Eski ses")

        controller.onPartial(s2, r2, "yarın toplantı")
        controller.onResults(s2, r2, "yarın toplantı")
        controller.finish()

        assertEquals("Taslak 2 yarın toplantı", submittedText)
        assertEquals(VoicePhase.Idle, controller.phase)
    }

    
    
    

    @Test
    fun finishingOnErrorNeverStartsNewRound() {
        var submittedText: String? = null
        val controller = SimulatedVoiceController(
            onText = {},
            onSubmit = { submittedText = it },
            onErrorMessage = {}
        )

        controller.start("")
        val s = controller.generation
        val r = controller.round
        controller.onPartial(s, r, "doktora git")

        val initialRounds = controller.newRoundsStarted
        controller.finish(simulateActiveRecognizer = true)
        assertEquals(VoicePhase.Finishing, controller.phase)

        
        controller.onError(s, r, 1, "timeout")

        
        assertEquals("doktora git", submittedText)
        assertEquals(VoicePhase.Idle, controller.phase)
        assertEquals(initialRounds, controller.newRoundsStarted)
    }

    @Test
    fun finishingWatchdogCannotDoubleSubmit() {
        var submitCount = 0
        val controller = SimulatedVoiceController(
            onText = {},
            onSubmit = { submitCount++ },
            onErrorMessage = {}
        )

        controller.start("")
        val s = controller.generation
        val r = controller.round
        controller.onPartial(s, r, "toplantı")

        val watchdogToken = controller.finish(simulateActiveRecognizer = true)
        assertEquals(VoicePhase.Finishing, controller.phase)

        
        controller.onResults(s, r, "toplantı")
        assertEquals(1, submitCount)
        assertEquals(VoicePhase.Idle, controller.phase)

        
        controller.fireWatchdog(watchdogToken)
        assertEquals(1, submitCount)
        assertEquals(1, controller.submitCallCount)
    }

    @Test
    fun staleWatchdogIsNoOpAfterResultArrives() {
        var submitCount = 0
        val controller = SimulatedVoiceController(
            onText = {},
            onSubmit = { submitCount++ },
            onErrorMessage = {}
        )

        controller.start("")
        val s1 = controller.finish() 

        
        controller.start("")
        val s2 = controller.finish(simulateActiveRecognizer = true)
        assertEquals(VoicePhase.Finishing, controller.phase)

        
        controller.fireWatchdog(s1)

        
        assertEquals(VoicePhase.Finishing, controller.phase)

        
        controller.fireWatchdog(s2)
        assertEquals(VoicePhase.Idle, controller.phase)
    }

    
    
    

    @Test
    fun shareDraftPreservedWhenVoiceUsed() {
        val session = VoiceDraftSession(
            initialText = "Haber başlığı",
            initialReferenceUrl = "https://example.com/article/123",
            initialIsShareDraft = true
        )

        
        session.onVoiceFinalize("yarın saat beşte oku")

        
        assertEquals("Oku", session.text)
        assertNotNull(session.alertAt)

        
        assertEquals("https://example.com/article/123", session.draftReferenceUrl)
        assertTrue(session.isShareDraft)
        assertTrue(session.isVoiceDraft)
    }

    @Test
    fun shareDraftPreservedOnSilenceOrCancel() {
        val initialUrl = "https://example.com/video"
        val initialTitle = "Video linki"
        var restoredText: String? = null
        val controller = SimulatedVoiceController(
            onText = { restoredText = it },
            onSubmit = {},
            onErrorMessage = {}
        )

        
        controller.start(initialTitle)
        controller.cancel()

        
        assertEquals(initialTitle, restoredText)
        assertEquals(VoicePhase.Idle, controller.phase)
    }

    
    
    

    @Test
    fun checklistModePreservedWhenVoiceUsed() {
        val items = listOf(
            ChecklistItem(1L, "Süt"),
            ChecklistItem(2L, "Ekmek")
        )
        val session = VoiceDraftSession(
            initialText = "Market",
            initialIsChecklistMode = true,
            initialChecklistItems = items
        )

        
        session.onVoiceFinalize("Pazar alışverişi yarın saat onda")

        assertEquals("Pazar alışverişi", session.text)
        assertNotNull(session.alertAt)

        
        assertTrue(session.isChecklistMode)
        assertEquals(2, session.checklistItems.size)
        assertEquals("Süt", session.checklistItems[0].text)
        assertEquals("Ekmek", session.checklistItems[1].text)
    }

    @Test
    fun checklistModePreservedOnSilenceOrCancel() {
        val items = listOf(ChecklistItem(1L, "Görev 1"))
        val session = VoiceDraftSession(
            initialText = "Yapılacaklar",
            initialIsChecklistMode = true,
            initialChecklistItems = items
        )

        var restoredText: String? = null
        val controller = SimulatedVoiceController(
            onText = { restoredText = it },
            onSubmit = {},
            onErrorMessage = {}
        )

        controller.start(session.text)
        controller.cancel()

        assertEquals("Yapılacaklar", restoredText)
        assertTrue(session.isChecklistMode)
        assertEquals(1, session.checklistItems.size)
    }

    
    
    

    @Test
    fun manualCategoryAndDatePreservedWhenVoiceHasNoDate() {
        val manualAlert = 1750000000000L
        val session = VoiceDraftSession(
            initialText = "Mevcut görev",
            initialAlertAt = manualAlert,
            initialRepeatRule = RecurrenceRule.DAILY,
            initialCategoryId = 99L
        )

        
        session.onVoiceFinalize("annemi ara")

        assertEquals("Annemi ara", session.text)
        assertEquals(manualAlert, session.alertAt) 
        assertEquals(RecurrenceRule.DAILY, session.repeatRule) 
        assertEquals(99L, session.customCategoryId) 
    }

    @Test
    fun voiceDateOverridesManualDateWhenVoiceHasExplicitTime() {
        val manualAlert = 1750000000000L
        val session = VoiceDraftSession(
            initialText = "Mevcut görev",
            initialAlertAt = manualAlert,
            initialRepeatRule = RecurrenceRule.DAILY,
            initialCategoryId = 99L
        )

        
        session.onVoiceFinalize("yarın saat beşte toplantı")

        assertEquals("Toplantı", session.text)
        assertNotNull(session.alertAt)
        
        assertTrue(session.alertAt != manualAlert)
        
        assertEquals(RecurrenceRule.DAILY, session.repeatRule)
        assertEquals(99L, session.customCategoryId)
    }

    @Test
    fun voiceRecurrenceOverridesManualRecurrence() {
        val session = VoiceDraftSession(
            initialRepeatRule = RecurrenceRule.DAILY
        )

        
        session.onVoiceFinalize("her hafta çiçekleri sula")

        assertEquals("Çiçekleri sula", session.text)
        assertEquals(RecurrenceRule.WEEKLY, session.repeatRule)
    }

    
    
    

    @Test
    fun deliberateRepetitionSingleRoundPreserved() {
        val draft = VoiceDraft("")
        draft.updatePartial("ara ara")
        draft.commitResult("ara ara")

        
        assertEquals("ara ara", draft.committed)
        assertEquals("ara ara", draft.text)
    }

    @Test
    fun validContinuationAcrossRoundsPreserved() {
        val draft = VoiceDraft("")

        
        draft.commitResult("annemi ara")
        assertEquals("annemi ara", draft.committed)

        
        draft.updatePartial("yarın tekrar ara")
        draft.commitResult("yarın tekrar ara")

        
        assertEquals("annemi ara yarın tekrar ara", draft.committed)
        assertEquals("annemi ara yarın tekrar ara", draft.text)
    }

    @Test
    fun cumulativeEngineResultDeduplicated() {
        val draft = VoiceDraft("")

        
        draft.commitResult("annemi ara")
        assertEquals("annemi ara", draft.committed)

        
        draft.updatePartial("annemi ara yarın")
        draft.commitResult("annemi ara yarın")

        
        assertEquals("annemi ara yarın", draft.committed)
        assertEquals("annemi ara yarın", draft.text)
    }

    @Test
    fun exactDuplicateFinalResultDeduplicated() {
        val draft = VoiceDraft("")

        draft.updatePartial("doktora git")
        draft.commitResult("doktora git")
        assertEquals("doktora git", draft.committed)

        
        draft.commitResult("doktora git")
        assertEquals("doktora git", draft.committed)
        assertEquals("doktora git", draft.text)
    }

    @Test
    fun blankResultDoesNotDestroyValidDraft() {
        val initialText = "Önemli randevu"
        val draft = VoiceDraft(initialText)

        draft.updatePartial("   ")
        assertEquals(initialText, draft.text)
        assertFalse(draft.hasSpokenText)

        draft.commitResult("   ")
        assertEquals(initialText, draft.text)
        assertFalse(draft.hasSpokenText)

        draft.commitResult(null)
        assertEquals(initialText, draft.text)
        assertFalse(draft.hasSpokenText)

        var submittedText: String? = null
        var restoredText: String? = null
        var errorMsg: String? = null
        val controller = SimulatedVoiceController(
            onText = { restoredText = it },
            onSubmit = { submittedText = it },
            onErrorMessage = { errorMsg = it }
        )

        controller.start(initialText)
        controller.finish()

        assertNull(submittedText)
        assertEquals(initialText, restoredText)
        assertNotNull(errorMsg)
        assertEquals(VoicePhase.Idle, controller.phase)
    }

    @Test
    fun finalResultBeatsPartialResult() {
        val draft = VoiceDraft("Toplantı")

        draft.updatePartial("yarın saat")
        assertEquals("Toplantı yarın saat", draft.text)
        assertEquals("yarın saat", draft.partial)
        assertTrue(draft.hasSpokenText)

        draft.commitResult("yarın saat beşte")
        assertEquals("Toplantı yarın saat beşte", draft.committed)
        assertEquals("", draft.partial)
        assertEquals("Toplantı yarın saat beşte", draft.text)
        assertTrue(draft.hasSpokenText)
    }

    @Test
    fun parserFailureSafelyFallsBack() {
        val weirdInputs = listOf(
            "",
            "   ",
            "??? !!! ...",
            "32 ağustos saat 99:99 da",
            "her 800 günde bir saat 25 te",
            "yarın saat",
            "dün akşam saat sekizde"
        )

        for (input in weirdInputs) {
            val result = VoiceDraftCoordinator.finalizeVoice(
                rawVoiceText = input,
                currentAlertAt = null,
                currentRepeatRule = RecurrenceRule.NONE,
                currentSendImmediateNotification = true
            )
            assertNotNull(result)
            assertNotNull(result.text)
            assertNotNull(result.repeatRule)
        }

        val fallbackResult = VoiceReminderParser.parse("??? !!!")
        assertEquals("??? !!!", fallbackResult.originalText)
        assertNull(fallbackResult.alertAt)
        assertEquals(RecurrenceRule.NONE, fallbackResult.repeatRule)
    }
}
