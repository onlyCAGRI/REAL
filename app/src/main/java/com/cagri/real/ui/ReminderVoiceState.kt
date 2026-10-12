package com.cagri.real.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.cagri.real.data.*
import com.cagri.real.speech.*

/** State slots retain their original remember/saveable policy; no repository or orchestration lives here. */
internal class ReminderVoiceState(
    val voiceAiParsing: MutableState<Boolean>,
    val voiceAiRequestSerial: MutableState<Long>,
    val pendingAutoSubmit: MutableState<Boolean>,
    val pendingVoiceDurationMinutes: MutableState<Int?>,
    val pendingVoicePeople: MutableState<List<String>>,
    val pendingVoiceLocation: MutableState<ReminderLocation?>,
    val pendingVoiceRepeatMask: MutableState<Int>,
    val pendingVoiceRepeatUntil: MutableState<Long?>,
    val lastVoiceCardId: MutableState<Long?>,
    val lastVoiceCardAt: MutableState<Long>,
    val pendingBulkPlan: MutableState<VoiceBulkPlan?>,
    val pendingVoiceMusicBlocks: MutableState<List<MusicBlock>>,
    val pendingVoiceMediaBlocks: MutableState<List<MediaBlock>>,
    val voiceLineCount: MutableState<Int>,
    val pendingDeletePlan: MutableState<VoiceDeletePlan?>,
    val retryVoiceCreation: MutableState<(() -> Unit)?>,
    val feedbackText: MutableState<String?>,
    val timeFollowUp: MutableState<VoiceTimeFollowUp?>,
    val requestTimeListening: MutableState<Boolean>
)

@Composable
internal fun rememberReminderVoiceState(): ReminderVoiceState {
    val voiceAiParsing = remember { mutableStateOf(false) }
    val voiceAiRequestSerial = remember { mutableStateOf(0L) }
    val pendingAutoSubmit = remember { mutableStateOf(false) }
    val pendingVoiceDurationMinutes = remember { mutableStateOf<Int?>(null) }
    val pendingVoicePeople = remember { mutableStateOf<List<String>>(emptyList()) }
    val pendingVoiceLocation = remember { mutableStateOf<ReminderLocation?>(null) }
    val pendingVoiceRepeatMask = remember { mutableStateOf(0) }
    val pendingVoiceRepeatUntil = remember { mutableStateOf<Long?>(null) }
    val lastVoiceCardId = rememberSaveable { mutableStateOf<Long?>(null) }
    val lastVoiceCardAt = rememberSaveable { mutableStateOf(0L) }
    val pendingBulkPlan = remember { mutableStateOf<VoiceBulkPlan?>(null) }
    val pendingVoiceMusicBlocks = remember { mutableStateOf<List<MusicBlock>>(emptyList()) }
    val pendingVoiceMediaBlocks = remember { mutableStateOf<List<MediaBlock>>(emptyList()) }
    val voiceLineCount = remember { mutableIntStateOf(1) }
    val pendingDeletePlan = remember { mutableStateOf<VoiceDeletePlan?>(null) }
    val retryVoiceCreation = remember { mutableStateOf<(() -> Unit)?>(null) }
    val feedbackText = remember { mutableStateOf<String?>(null) }
    val timeFollowUp = remember { mutableStateOf<VoiceTimeFollowUp?>(null) }
    val requestTimeListening = remember { mutableStateOf(false) }
    return remember(voiceAiParsing, voiceAiRequestSerial, pendingAutoSubmit, pendingVoiceDurationMinutes, pendingVoicePeople, pendingVoiceLocation, pendingVoiceRepeatMask, pendingVoiceRepeatUntil, lastVoiceCardId, lastVoiceCardAt, pendingBulkPlan, pendingVoiceMusicBlocks, pendingVoiceMediaBlocks, voiceLineCount) {
        ReminderVoiceState(voiceAiParsing, voiceAiRequestSerial, pendingAutoSubmit, pendingVoiceDurationMinutes, pendingVoicePeople, pendingVoiceLocation, pendingVoiceRepeatMask, pendingVoiceRepeatUntil, lastVoiceCardId, lastVoiceCardAt, pendingBulkPlan, pendingVoiceMusicBlocks, pendingVoiceMediaBlocks, voiceLineCount, pendingDeletePlan, retryVoiceCreation, feedbackText, timeFollowUp, requestTimeListening)
    }
}

/** Display-only state; dismissing it never edits, saves or cancels a task. */
internal fun showVoiceFeedback(state: ReminderVoiceState, message: String) {
    state.feedbackText.value = message.takeIf { it.isNotBlank() }
}

/** Native pending card/content retained while a short clock reply is dictated. */
internal data class VoiceTimeFollowUp(
    val prepared: PreparedVoiceIntent,
    val createdCategory: CustomCategory?,
    val autoSave: Boolean
)
