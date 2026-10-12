package com.cagri.real.ui

import android.widget.Toast
import androidx.compose.runtime.*
import com.cagri.real.data.*
import com.cagri.real.speech.*
import com.cagri.real.ui.components.*
import com.cagri.real.ui.components.planner.PlannerEventDetails
import java.time.ZoneId
import kotlinx.coroutines.*

/** Persistence/notification orchestration; deliberately not a composable. */
internal fun reminderSubmitAction(
    input: ReminderComposerSnapshot,
    composerState: ReminderComposerState,
    voiceState: ReminderVoiceState,
    callbacks: ReminderComposerCallbacks,
    services: ReminderComposerServices,
    requestPermissionForAction: (PendingNotificationAction) -> Unit
): (String) -> Unit = with(input) { with(callbacks) { with(services) {
    var isChecklistMode by composerState.isChecklistMode
    var isVoiceDraft by composerState.isVoiceDraft
    var requiresTimeSelection by composerState.requiresTimeSelection
    var pendingDateHintEpochDay by composerState.pendingDateHintEpochDay
    var pendingTimeOfDay by composerState.pendingTimeOfDay
    var checklistDraftItems by composerState.checklistDraftItems
    var sendImmediateNotification by composerState.sendImmediateNotification
    var voiceCategoryExplicit by composerState.voiceCategoryExplicit
    var draftCategoryId by composerState.draftCategoryId
    var showDatePicker by composerState.showDatePicker
    var saving by composerState.saving
    var isWhatsAppArmed by composerState.isWhatsAppArmed
    var isLabsOpen by composerState.isLabsOpen
    var composerDurationMinutes by composerState.composerDurationMinutes
    var copyingPhotos by composerState.copyingPhotos
    var voiceAiParsing by voiceState.voiceAiParsing
    var voiceAiRequestSerial by voiceState.voiceAiRequestSerial
    var pendingVoiceDurationMinutes by voiceState.pendingVoiceDurationMinutes
    var pendingVoicePeople by voiceState.pendingVoicePeople
    var pendingVoiceLocation by voiceState.pendingVoiceLocation
    var pendingVoiceRepeatMask by voiceState.pendingVoiceRepeatMask
    var pendingVoiceRepeatUntil by voiceState.pendingVoiceRepeatUntil
    var lastVoiceCardId by voiceState.lastVoiceCardId
    var lastVoiceCardAt by voiceState.lastVoiceCardAt
    var pendingVoiceMusicBlocks by voiceState.pendingVoiceMusicBlocks
    var pendingVoiceMediaBlocks by voiceState.pendingVoiceMediaBlocks
    val submit: (String) -> Unit = { text ->
        if (voiceState.retryVoiceCreation.value != null) {
            showVoiceFeedback(voiceState, "Kalan kartları kaydetmek için Kalanları tekrar dene’ye bas.")
        } else if (alertAt != null && pendingVoiceRepeatUntil != null &&
            java.time.Instant.ofEpochMilli(alertAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() > pendingVoiceRepeatUntil!!) {
            if (isVoiceDraft) showVoiceFeedback(voiceState, "Seçilen gün tekrarın bitiş tarihinden sonra. Tarihi düzelt.")
            else Toast.makeText(context, "Seçilen gün tekrarın bitiş tarihinden sonra. Tarihi düzelt.", Toast.LENGTH_LONG).show()
            requestPermissionForAction(PendingNotificationAction.OPEN_DATE_PICKER)
        } else if (requiresTimeSelection && alertAt == null) {
            if (isVoiceDraft) showVoiceFeedback(voiceState, "Saat kaçta olsun?")
            else Toast.makeText(context, "Lütfen bir saat seç.", Toast.LENGTH_SHORT).show()
            requestPermissionForAction(PendingNotificationAction.OPEN_DATE_PICKER)
        } else if (isChecklistMode) {
            val validItems = checklistDraftItems.map { it.trim() }.filter { it.isNotEmpty() }
            if (text.isNotBlank() && validItems.isNotEmpty() && !saving && !copyingPhotos) {
                if (alertAt != null && alertAt <= System.currentTimeMillis()) {
                    if (isVoiceDraft) showVoiceFeedback(voiceState, "Gelecekte bir tarih ve saat seç.")
                    else Toast.makeText(context, "Gelecekte bir tarih ve saat seç.", Toast.LENGTH_LONG).show()
                    showDatePicker = true
                } else {
                    if (voiceAiParsing) {
                        // User explicitly chose to save before Gemini finished. Persist the visible
                        // REAL immediately and invalidate the late AI result so it cannot mutate
                        // the next composer draft after this save.
                        voiceAiRequestSerial += 1L
                        voiceAiParsing = false
                    }
                    val savedFromVoice = isVoiceDraft
                    saving = true
                    val wasWhatsAppArmed = isWhatsAppArmed
                    isWhatsAppArmed = false
                    scope.launch {
                        try {
                            val items = validItems.mapIndexed { idx, itmText -> ChecklistItem(id = 0L, text = itmText) }
                            val resolvedTitle = text.trim()
                            val quick24HourUntilAt = if (
                                category == ReminderCategory.Today && alertAt == null && pendingDateHintEpochDay == null
                            ) {
                                System.currentTimeMillis() + ROLLING_24_HOURS_MILLIS
                            } else null
                            val plannedDateForSave = if (quick24HourUntilAt != null) null
                                else if (alertAt == null) pendingDateHintEpochDay else null
                            val createdId = onAddReminder(
                                resolvedTitle, alertAt, if (alertAt != null) repeatRule else RecurrenceRule.NONE,
                                draftCategoryId, true, items, sendImmediateNotification, draftReferenceUrl,
                                draftAttachments, emptyList(), pendingVoiceLocation,
                                plannedDateForSave, quick24HourUntilAt, pendingVoiceMusicBlocks, pendingVoiceMediaBlocks
                            )
                            if (createdId != null) {
                                if (savedFromVoice) {
                                    voiceState.feedbackText.value = null
                                    voiceState.timeFollowUp.value = null
                                    voiceState.requestTimeListening.value = false
                                }
                                lastVoiceCardId = createdId
                                lastVoiceCardAt = System.currentTimeMillis()
                                if (alertAt != null && (pendingVoiceRepeatMask != 0 || pendingVoiceRepeatUntil != null)) {
                                    onSetRepeatPattern(createdId, pendingVoiceRepeatMask, pendingVoiceRepeatUntil)
                                }
                                val durToSave = composerDurationMinutes ?: pendingVoiceDurationMinutes
                                if (durToSave != null || pendingVoicePeople.isNotEmpty()) {
                                    runCatching {
                                        plannerRepository.put(
                                            createdId,
                                            PlannerEventDetails(
                                                durationMinutes = durToSave,
                                                people = pendingVoicePeople,
                                                colorIndex = 0
                                            )
                                        )
                                    }
                                }
                                composerDurationMinutes = null
                                pendingVoiceDurationMinutes = null
                                pendingVoicePeople = emptyList()
                                pendingVoiceLocation = null; pendingVoiceRepeatMask = 0; pendingVoiceRepeatUntil = null
                                pendingVoiceMusicBlocks = emptyList()
                                pendingVoiceMediaBlocks = emptyList()
                                onDraftAttachmentsChange(emptyList())
                                entryTracker.onReminderCreated(createdId, reduceAnimations)
                                RealHaptics.success(view)
                                RealPetKeywords.onReminderSaved(
                                    savedText = (listOf(resolvedTitle) + validItems).joinToString("\n"),
                                    isKeywordsEnabled = RealPetRuntime.keywordsEnabled.value
                                )
                                RealPetKeywords.onComposerClosedOrSubmitted()
                                draftCategoryId = if (customCategory != null) customCategory.id else null
                                voiceCategoryExplicit = false
                                isChecklistMode = false
                                checklistDraftItems = listOf("")
                                isLabsOpen = false
                                sendImmediateNotification = false
                                onDraftChange("")
                                isVoiceDraft = false
                                requiresTimeSelection = false
                                pendingDateHintEpochDay = null
                                pendingTimeOfDay = null
                                onShareDraftChange(false)
                                onDraftReferenceUrlChange(null)
                                focusManager.clearFocus(); keyboard?.hide()

                                if (wasWhatsAppArmed) {
                                    scope.launch {
                                        val recipientPhone = whatsAppRecipientRepo.getRecipient()
                                        val sendSuccess = WhatsAppSender.sendReminder(
                                            id = createdId,
                                            text = resolvedTitle,
                                            isChecklist = true,
                                            checklistItems = validItems,
                                            recipient = recipientPhone
                                        )
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                if (sendSuccess) "WhatsApp'a gönderildi" else "WhatsApp gönderilemedi",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        } finally { saving = false }
                    }
                }
            }
        } else {
            if (text.isNotBlank() && !saving && !copyingPhotos) {
                if (alertAt != null && alertAt <= System.currentTimeMillis()) {
                    if (isVoiceDraft) showVoiceFeedback(voiceState, "Gelecekte bir tarih ve saat seç.")
                    else Toast.makeText(context, "Gelecekte bir tarih ve saat seç.", Toast.LENGTH_LONG).show()
                    showDatePicker = true
                } else {
                    if (voiceAiParsing) {
                        // User explicitly chose to save before Gemini finished. Persist the visible
                        // REAL immediately and invalidate the late AI result so it cannot mutate
                        // the next composer draft after this save.
                        voiceAiRequestSerial += 1L
                        voiceAiParsing = false
                    }
                    val savedFromVoice = isVoiceDraft
                    saving = true
                    val wasWhatsAppArmed = isWhatsAppArmed
                    isWhatsAppArmed = false
                    scope.launch {
                        try {
                            val reminderText = text.trim()
                            val quick24HourUntilAt = if (
                                category == ReminderCategory.Today && alertAt == null && pendingDateHintEpochDay == null
                            ) {
                                System.currentTimeMillis() + ROLLING_24_HOURS_MILLIS
                            } else null
                            val plannedDateForSave = if (quick24HourUntilAt != null) null
                                else if (alertAt == null) pendingDateHintEpochDay else null
                            val createdId = onAddReminder(
                                reminderText, alertAt, if (alertAt != null) repeatRule else RecurrenceRule.NONE,
                                draftCategoryId, false, emptyList(), sendImmediateNotification, draftReferenceUrl,
                                draftAttachments, emptyList(), pendingVoiceLocation,
                                plannedDateForSave, quick24HourUntilAt, pendingVoiceMusicBlocks, pendingVoiceMediaBlocks
                            )
                            if (createdId != null) {
                                if (savedFromVoice) {
                                    voiceState.feedbackText.value = null
                                    voiceState.timeFollowUp.value = null
                                    voiceState.requestTimeListening.value = false
                                }
                                lastVoiceCardId = createdId
                                lastVoiceCardAt = System.currentTimeMillis()
                                if (alertAt != null && (pendingVoiceRepeatMask != 0 || pendingVoiceRepeatUntil != null)) {
                                    onSetRepeatPattern(createdId, pendingVoiceRepeatMask, pendingVoiceRepeatUntil)
                                }
                                val durToSave = composerDurationMinutes ?: pendingVoiceDurationMinutes
                                if (durToSave != null || pendingVoicePeople.isNotEmpty()) {
                                    runCatching {
                                        plannerRepository.put(
                                            createdId,
                                            PlannerEventDetails(
                                                durationMinutes = durToSave,
                                                people = pendingVoicePeople,
                                                colorIndex = 0
                                            )
                                        )
                                    }
                                }
                                composerDurationMinutes = null
                                pendingVoiceDurationMinutes = null
                                pendingVoicePeople = emptyList()
                                pendingVoiceLocation = null; pendingVoiceRepeatMask = 0; pendingVoiceRepeatUntil = null
                                pendingVoiceMusicBlocks = emptyList()
                                pendingVoiceMediaBlocks = emptyList()
                                onDraftAttachmentsChange(emptyList())
                                entryTracker.onReminderCreated(createdId, reduceAnimations)
                                RealHaptics.success(view)
                                RealPetKeywords.onReminderSaved(
                                    savedText = reminderText,
                                    isKeywordsEnabled = RealPetRuntime.keywordsEnabled.value
                                )
                                RealPetKeywords.onComposerClosedOrSubmitted()
                                draftCategoryId = if (customCategory != null) customCategory.id else null
                                voiceCategoryExplicit = false
                                isLabsOpen = false
                                sendImmediateNotification = false
                                onDraftChange("")
                                isVoiceDraft = false
                                requiresTimeSelection = false
                                pendingDateHintEpochDay = null
                                pendingTimeOfDay = null
                                onShareDraftChange(false)
                                onDraftReferenceUrlChange(null)
                                focusManager.clearFocus(); keyboard?.hide()

                                if (wasWhatsAppArmed) {
                                    scope.launch {
                                        val recipientPhone = whatsAppRecipientRepo.getRecipient()
                                        val sendSuccess = WhatsAppSender.sendReminder(
                                            id = createdId,
                                            text = reminderText,
                                            isChecklist = false,
                                            checklistItems = emptyList(),
                                            recipient = recipientPhone
                                        )
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                if (sendSuccess) "WhatsApp'a gönderildi" else "WhatsApp gönderilemedi",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        } finally { saving = false }
                    }
                }
            }
        }
    }
    submit
} } }
