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
    val requestTimeListening: MutableState<Boolean>,
    /** "Doğru mu anladım?": shown instead of saving while it is set. */
    val understandingCheck: MutableState<VoiceUnderstandingCheck?> = mutableStateOf(null),
    /** The next finalize is a corrected sentence: do not ask again. */
    val skipNextUnderstandingCheck: MutableState<Boolean> = mutableStateOf(false),
    /** The composer holds a sentence the person is correcting by hand: sending it goes back through the AI. */
    val resubmitTypedCorrection: MutableState<Boolean> = mutableStateOf(false),
    /** "Yeri yaz": the composer holds the place; sending it finds the address and saves the card. */
    val placeFix: MutableState<((String) -> Unit)?> = mutableStateOf(null)
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
    val understandingCheck = remember { mutableStateOf<VoiceUnderstandingCheck?>(null) }
    val skipNextUnderstandingCheck = remember { mutableStateOf(false) }
    val resubmitTypedCorrection = remember { mutableStateOf(false) }
    val placeFix = remember { mutableStateOf<((String) -> Unit)?>(null) }
    return remember(voiceAiParsing, voiceAiRequestSerial, pendingAutoSubmit, pendingVoiceDurationMinutes, pendingVoicePeople, pendingVoiceLocation, pendingVoiceRepeatMask, pendingVoiceRepeatUntil, lastVoiceCardId, lastVoiceCardAt, pendingBulkPlan, pendingVoiceMusicBlocks, pendingVoiceMediaBlocks, voiceLineCount) {
        ReminderVoiceState(voiceAiParsing, voiceAiRequestSerial, pendingAutoSubmit, pendingVoiceDurationMinutes, pendingVoicePeople, pendingVoiceLocation, pendingVoiceRepeatMask, pendingVoiceRepeatUntil, lastVoiceCardId, lastVoiceCardAt, pendingBulkPlan, pendingVoiceMusicBlocks, pendingVoiceMediaBlocks, voiceLineCount, pendingDeletePlan, retryVoiceCreation, feedbackText, timeFollowUp, requestTimeListening,
            understandingCheck, skipNextUnderstandingCheck, resubmitTypedCorrection, placeFix)
    }
}

/** Display-only state; dismissing it never edits, saves or cancels a task. */
internal fun showVoiceFeedback(state: ReminderVoiceState, message: String) {
    state.feedbackText.value = message.takeIf { it.isNotBlank() }
}

/**
 * The sentence the AI was unsure about, waiting for the person: [confirm] saves with the final text (unchanged =
 * the AI's own reading), [correct] puts it into the composer to type the fix.
 */
internal data class VoiceUnderstandingCheck(
    val initial: VoiceUnderstandingText,
    val confirm: (String) -> Unit,
    val correct: (String) -> Unit,
    val cancel: () -> Unit,
    /** The card's place, when it has one: "Yeri yaz" lets the person type it instead. */
    val place: String? = null,
    val fixPlace: () -> Unit = {}
)

/** Native pending card/content retained while a short clock reply is dictated. */
internal data class VoiceTimeFollowUp(
    val prepared: PreparedVoiceIntent,
    val createdCategory: CustomCategory?,
    val autoSave: Boolean
)
