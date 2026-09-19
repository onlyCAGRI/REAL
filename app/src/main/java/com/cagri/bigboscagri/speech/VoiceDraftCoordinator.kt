package com.cagri.bigboscagri.speech

import com.cagri.bigboscagri.data.ChecklistItem
import com.cagri.bigboscagri.data.RecurrenceRule
import java.time.LocalDate

object VoiceDraftCoordinator {

    data class FinalizeResult(
        val text: String,
        val alertAt: Long?,
        val repeatRule: RecurrenceRule,
        val sendImmediateNotification: Boolean,
        val requiresTimeSelection: Boolean = false,
        val dateHint: LocalDate? = null,
        val timeOfDay: TimeOfDay? = null
    )

    fun finalizeVoice(
        rawVoiceText: String,
        currentAlertAt: Long?,
        currentRepeatRule: RecurrenceRule,
        currentSendImmediateNotification: Boolean
    ): FinalizeResult {
        val parsed = VoiceReminderParser.parse(rawVoiceText)
        val resolvedText = parsed.text.ifBlank { rawVoiceText.trim() }
        val resolvedAlertAt = parsed.alertAt ?: currentAlertAt
        val resolvedRepeatRule = if (parsed.repeatRule != RecurrenceRule.NONE) {
            parsed.repeatRule
        } else {
            currentRepeatRule
        }
        val resolvedSendImmediate = if (resolvedAlertAt != null || parsed.requiresTimeSelection) false else currentSendImmediateNotification

        return FinalizeResult(
            text = resolvedText,
            alertAt = resolvedAlertAt,
            repeatRule = resolvedRepeatRule,
            sendImmediateNotification = resolvedSendImmediate,
            requiresTimeSelection = parsed.requiresTimeSelection,
            dateHint = parsed.dateHint,
            timeOfDay = parsed.timeOfDay
        )
    }
}

class VoiceDraftSession(
    initialText: String = "",
    initialAlertAt: Long? = null,
    initialRepeatRule: RecurrenceRule = RecurrenceRule.NONE,
    initialCategoryId: Long? = null,
    initialSendImmediate: Boolean = true
) {
    var text: String = initialText
    var alertAt: Long? = initialAlertAt
    var repeatRule: RecurrenceRule = initialRepeatRule
    var customCategoryId: Long? = initialCategoryId
    var sendImmediateNotification: Boolean = initialSendImmediate
    var isVoiceDraft: Boolean = false
    var requiresTimeSelection: Boolean = false
    var dateHint: LocalDate? = null
    var timeOfDay: TimeOfDay? = null

    fun onVoiceFinalize(rawVoiceText: String): VoiceDraftCoordinator.FinalizeResult {
        val result = VoiceDraftCoordinator.finalizeVoice(
            rawVoiceText = rawVoiceText,
            currentAlertAt = alertAt,
            currentRepeatRule = repeatRule,
            currentSendImmediateNotification = sendImmediateNotification
        )
        text = result.text
        alertAt = result.alertAt
        repeatRule = result.repeatRule
        sendImmediateNotification = result.sendImmediateNotification
        isVoiceDraft = true
        requiresTimeSelection = result.requiresTimeSelection
        dateHint = result.dateHint
        timeOfDay = result.timeOfDay
        return result
    }

    suspend fun saveExplicitly(
        onAdd: suspend (
            text: String,
            alertAt: Long?,
            repeatRule: RecurrenceRule,
            categoryId: Long?,
            isChecklist: Boolean,
            checklistItems: List<ChecklistItem>,
            sendImmediate: Boolean
        ) -> Boolean
    ): Boolean {
        if (text.isBlank()) return false
        if (requiresTimeSelection && alertAt == null) return false
        val success = onAdd(
            text.trim(),
            alertAt,
            if (alertAt != null) repeatRule else RecurrenceRule.NONE,
            customCategoryId,
            false,
            emptyList(),
            sendImmediateNotification
        )
        if (success) {
            reset()
        }
        return success
    }

    fun handleBack(isEditing: Boolean): BackResult {
        return if (isEditing) {
            BackResult.DismissKeyboard
        } else if (isVoiceDraft) {
            reset()
            BackResult.CancelDraft
        } else {
            BackResult.Unhandled
        }
    }

    fun reset() {
        text = ""
        alertAt = null
        repeatRule = RecurrenceRule.NONE
        customCategoryId = null
        sendImmediateNotification = true
        isVoiceDraft = false
        requiresTimeSelection = false
        dateHint = null
        timeOfDay = null
    }

    enum class BackResult { DismissKeyboard, CancelDraft, Unhandled }
}
