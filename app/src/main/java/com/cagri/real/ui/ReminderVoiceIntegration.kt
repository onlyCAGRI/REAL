package com.cagri.real.ui

import android.widget.Toast
import androidx.compose.runtime.*
import com.cagri.real.data.*
import com.cagri.real.speech.*
import com.cagri.real.ui.components.*
import com.cagri.real.ui.components.planner.PlannerEventDetails
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

internal class ReminderVoiceFlow(
    val voice: VoiceInputHandle,
    val saveRawText: (String) -> Unit
)

/** Small Compose adapter; the command implementation is in ReminderVoiceCoordinator. */
@Composable
internal fun rememberReminderVoiceFlow(
    input: ReminderComposerSnapshot,
    composerState: ReminderComposerState,
    voiceState: ReminderVoiceState,
    callbacks: ReminderComposerCallbacks,
    services: ReminderComposerServices,
    actions: ReminderVoiceActions
): ReminderVoiceFlow = with(input) { with(callbacks) { with(services) {
    var editing by composerState.editing
    var isChecklistMode by composerState.isChecklistMode
    var isVoiceDraft by composerState.isVoiceDraft
    var requiresTimeSelection by composerState.requiresTimeSelection
    var pendingDateHintEpochDay by composerState.pendingDateHintEpochDay
    var pendingTimeOfDay by composerState.pendingTimeOfDay
    var manualDateClearedOrChanged by composerState.manualDateClearedOrChanged
    var sendImmediateNotification by composerState.sendImmediateNotification
    var voiceCategoryExplicit by composerState.voiceCategoryExplicit
    var draftCategoryId by composerState.draftCategoryId
    var saving by composerState.saving
    var composerDurationMinutes by composerState.composerDurationMinutes
    var pendingVoiceDurationMinutes by voiceState.pendingVoiceDurationMinutes
    var pendingVoicePeople by voiceState.pendingVoicePeople
    var pendingVoiceLocation by voiceState.pendingVoiceLocation
    var pendingVoiceRepeatMask by voiceState.pendingVoiceRepeatMask
    var pendingVoiceRepeatUntil by voiceState.pendingVoiceRepeatUntil
    var lastVoiceCardId by voiceState.lastVoiceCardId
    var lastVoiceCardAt by voiceState.lastVoiceCardAt
    var pendingVoiceMusicBlocks by voiceState.pendingVoiceMusicBlocks
    var pendingVoiceMediaBlocks by voiceState.pendingVoiceMediaBlocks
    val onVoiceFinalize = reminderVoiceFinalize(input, composerState, voiceState, callbacks, services, actions)
    val directVoiceSave = rememberDirectVoiceSave(
        onText = onDraftChange,
        onSave = { text ->
            val selectedAlert = alertAt.takeIf { manualDateClearedOrChanged }
            val selectedDay = pendingDateHintEpochDay.takeIf { manualDateClearedOrChanged && selectedAlert == null }
            onAddReminder(text, selectedAlert, if (selectedAlert != null) repeatRule else RecurrenceRule.NONE,
                draftCategoryId.takeIf { voiceCategoryExplicit } ?: customCategory?.id, false, emptyList(), sendImmediateNotification, draftReferenceUrl,
                draftAttachments, emptyList(), null, selectedDay, null, emptyList(), emptyList()).also { id ->
                if (id != null && composerDurationMinutes != null) runCatching {
                    plannerRepository.put(id, PlannerEventDetails(durationMinutes = composerDurationMinutes, people = emptyList(), colorIndex = 0))
                }
            }
        },
        onSaved = { id ->
            lastVoiceCardId = id; lastVoiceCardAt = System.currentTimeMillis()
            entryTracker.onReminderCreated(id, reduceAnimations)
            onAlertChange(null, RecurrenceRule.NONE)
            draftCategoryId = customCategory?.id; voiceCategoryExplicit = false; pendingDateHintEpochDay = null; pendingTimeOfDay = null
            requiresTimeSelection = false; isVoiceDraft = false; isChecklistMode = false
            onDraftAttachmentsChange(emptyList()); onDraftReferenceUrlChange(null); onShareDraftChange(false)
            sendImmediateNotification = false; composerDurationMinutes = null
            pendingVoiceDurationMinutes = null; pendingVoicePeople = emptyList(); pendingVoiceLocation = null
            pendingVoiceRepeatMask = 0; pendingVoiceRepeatUntil = null
            pendingVoiceMusicBlocks = emptyList(); pendingVoiceMediaBlocks = emptyList()
            editing = false; focusManager.clearFocus(); keyboard?.hide()
            RealHaptics.success(view)
            voiceState.feedbackText.value = null
        },
        onBusy = { saving = it },
        onRecover = { editing = true; isVoiceDraft = true }
    )
    val saveRawText: (String) -> Unit = { text ->
        if (text.isNotBlank() && !saving) {
            // Synchronous invalidation before the async repository write: late AI cannot edit/save.
            voiceState.voiceAiRequestSerial.value += 1L
            voiceState.voiceAiParsing.value = false
            voiceState.pendingAutoSubmit.value = false
            voiceState.pendingBulkPlan.value = null
            voiceState.pendingDeletePlan.value = null
            voiceState.retryVoiceCreation.value = null
            voiceState.feedbackText.value = null
            voiceState.timeFollowUp.value = null
            voiceState.requestTimeListening.value = false
            VoiceRecognitionAlternatives.clear()
            directVoiceSave(text)
        }
    }
    val voice = rememberVoiceInput(draft, onDraftChange, onVoiceFinalize,
        observeLevel = !reduceAnimations, onDirectSubmit = saveRawText)
    ReminderVoiceFlow(voice, saveRawText)
} } }

@Composable
internal fun ReminderVoiceEffects(
    voice: VoiceInputHandle,
    voiceState: ReminderVoiceState,
    composerState: ReminderComposerState,
    voiceOnHome: Boolean,
    draft: String,
    submit: (String) -> Unit
) {
    var saving by composerState.saving
    var voiceAiParsing by voiceState.voiceAiParsing
    var pendingAutoSubmit by voiceState.pendingAutoSubmit
    var voiceLineCount by voiceState.voiceLineCount
    LaunchedEffect(voice.phase, voiceAiParsing, pendingAutoSubmit, voiceState.feedbackText.value) {
        if (voice.phase == VoicePhase.Idle && !voiceAiParsing && !pendingAutoSubmit && voiceState.feedbackText.value == null) {
            voiceLineCount = 1
        }
    }
    LaunchedEffect(voiceState.requestTimeListening.value, voice.phase, voiceAiParsing, saving) {
        if (voiceState.requestTimeListening.value && voice.phase == VoicePhase.Idle && !voiceAiParsing && !saving) {
            voiceState.requestTimeListening.value = false // One automatic attempt per question.
            voice.startWithText("") // Fresh clock reply; the native pending task stays in timeFollowUp.
        }
    }
    HomeVoiceAutoFinish(voice, voiceOnHome || voiceState.timeFollowUp.value != null)
    // The AI answer has been applied to the draft: save it without another tap.
    val latestSubmit by rememberUpdatedState(submit)
    val latestDraftText by rememberUpdatedState(draft)
    LaunchedEffect(pendingAutoSubmit) {
        if (!pendingAutoSubmit) return@LaunchedEffect
        delay(450L) // let the recomposed AI result settle and stay visible for a beat
        latestSubmit(latestDraftText)
        delay(60L)
        if (saving) snapshotFlow { saving }.first { !it }
        pendingAutoSubmit = false
    }
}
