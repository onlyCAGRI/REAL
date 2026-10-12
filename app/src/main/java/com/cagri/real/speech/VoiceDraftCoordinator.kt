package com.cagri.real.speech

import com.cagri.real.data.ChecklistItem
import com.cagri.real.data.RecurrenceRule
import java.time.LocalDate

object VoiceDraftCoordinator {

    data class FinalizeResult(
        val text: String,
        val alertAt: Long?,
        val repeatRule: RecurrenceRule,
        val sendImmediateNotification: Boolean,
        val requiresTimeSelection: Boolean = false,
        val dateHint: LocalDate? = null,
        val timeOfDay: TimeOfDay? = null,
        val isChecklist: Boolean = false,
        val checklistItems: List<String> = emptyList()
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
            timeOfDay = parsed.timeOfDay,
            isChecklist = parsed.isChecklist,
            checklistItems = parsed.checklistItems
        )
    }
}

class VoiceDraftSession(
    initialText: String = "",
    initialAlertAt: Long? = null,
    initialRepeatRule: RecurrenceRule = RecurrenceRule.NONE,
    initialCategoryId: Long? = null,
    initialSendImmediate: Boolean = true,
    initialReferenceUrl: String? = null,
    initialIsShareDraft: Boolean = false,
    initialIsChecklistMode: Boolean = false,
    initialChecklistItems: List<ChecklistItem> = emptyList()
) {
    var text: String = initialText
    var alertAt: Long? = initialAlertAt
    var repeatRule: RecurrenceRule = initialRepeatRule
    var customCategoryId: Long? = initialCategoryId
    var sendImmediateNotification: Boolean = initialSendImmediate
    var draftReferenceUrl: String? = initialReferenceUrl
    var isShareDraft: Boolean = initialIsShareDraft
    var isChecklistMode: Boolean = initialIsChecklistMode
    var checklistItems: List<ChecklistItem> = initialChecklistItems
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
        if (result.isChecklist && result.checklistItems.isNotEmpty()) {
            isChecklistMode = true
            checklistItems = result.checklistItems.mapIndexed { idx, itmText ->
                ChecklistItem(id = 0L, text = itmText)
            }
        }
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
        if (text.isBlank() && (!isChecklistMode || checklistItems.isEmpty())) return false
        if (requiresTimeSelection && alertAt == null) return false
        val success = onAdd(
            text.trim().ifEmpty { if (isChecklistMode) "Yapılacaklar listesi" else "" },
            alertAt,
            if (alertAt != null) repeatRule else RecurrenceRule.NONE,
            customCategoryId,
            isChecklistMode,
            checklistItems,
            sendImmediateNotification
        )
        if (success) {
            reset()
        }
        return success
    }

    suspend fun saveExplicitlyWithReference(
        onAdd: suspend (
            text: String,
            alertAt: Long?,
            repeatRule: RecurrenceRule,
            categoryId: Long?,
            isChecklist: Boolean,
            checklistItems: List<ChecklistItem>,
            sendImmediate: Boolean,
            referenceUrl: String?
        ) -> Boolean
    ): Boolean {
        if (text.isBlank() && (!isChecklistMode || checklistItems.isEmpty())) return false
        if (requiresTimeSelection && alertAt == null) return false
        val success = onAdd(
            text.trim().ifEmpty { if (isChecklistMode) "Yapılacaklar listesi" else "" },
            alertAt,
            if (alertAt != null) repeatRule else RecurrenceRule.NONE,
            customCategoryId,
            isChecklistMode,
            checklistItems,
            sendImmediateNotification,
            draftReferenceUrl
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
        draftReferenceUrl = null
        isShareDraft = false
        isChecklistMode = false
        checklistItems = emptyList()
        isVoiceDraft = false
        requiresTimeSelection = false
        dateHint = null
        timeOfDay = null
    }

    enum class BackResult { DismissKeyboard, CancelDraft, Unhandled }
}
