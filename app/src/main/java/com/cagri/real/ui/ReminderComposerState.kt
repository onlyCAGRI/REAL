package com.cagri.real.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.cagri.real.data.*
import com.cagri.real.speech.*

/** State slots retain their original remember/saveable policy; no repository or orchestration lives here. */
internal class ReminderComposerState(
    val editing: MutableState<Boolean>,
    val isChecklistMode: MutableState<Boolean>,
    val isVoiceDraft: MutableState<Boolean>,
    val requiresTimeSelection: MutableState<Boolean>,
    val pendingDateHintEpochDay: MutableState<Long?>,
    val pendingTimeOfDay: MutableState<TimeOfDay?>,
    val manualDateClearedOrChanged: MutableState<Boolean>,
    val checklistDraftItems: MutableState<List<String>>,
    val sendImmediateNotification: MutableState<Boolean>,
    val voiceCategoryExplicit: MutableState<Boolean>,
    val draftCategoryId: MutableState<Long?>,
    val showDatePicker: MutableState<Boolean>,
    val refocus: MutableState<Boolean>,
    val saving: MutableState<Boolean>,
    val isWhatsAppArmed: MutableState<Boolean>,
    val isLabsOpen: MutableState<Boolean>,
    val composerDurationMinutes: MutableState<Int?>,
    val copyingPhotos: MutableState<Boolean>,
    val photoImportGeneration: MutableState<Int>
)

@Composable
internal fun rememberReminderComposerState(category: ReminderCategory?): ReminderComposerState {
    val editing = remember { mutableStateOf(false) }
    val isChecklistMode = rememberSaveable { mutableStateOf(false) }
    val isVoiceDraft = rememberSaveable { mutableStateOf(false) }
    val requiresTimeSelection = rememberSaveable { mutableStateOf(false) }
    val pendingDateHintEpochDay = rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingTimeOfDay = rememberSaveable { mutableStateOf<TimeOfDay?>(null) }
    val manualDateClearedOrChanged = rememberSaveable(category) { mutableStateOf(false) }
    val checklistDraftItems = rememberSaveable { mutableStateOf(listOf("")) }
    val sendImmediateNotification = rememberSaveable { mutableStateOf(false) }
    val voiceCategoryExplicit = rememberSaveable { mutableStateOf(false) }
    val draftCategoryId = rememberSaveable { mutableStateOf<Long?>(null) }
    val showDatePicker = rememberSaveable { mutableStateOf(false) }
    val refocus = remember { mutableStateOf(false) }
    val saving = remember { mutableStateOf(false) }
    val isWhatsAppArmed = rememberSaveable { mutableStateOf(false) }
    val isLabsOpen = rememberSaveable { mutableStateOf(false) }
    val composerDurationMinutes = rememberSaveable { mutableStateOf<Int?>(null) }
    val copyingPhotos = remember { mutableStateOf(false) }
    val photoImportGeneration = remember { mutableStateOf(0) }
    return remember(editing, isChecklistMode, isVoiceDraft, requiresTimeSelection, pendingDateHintEpochDay, pendingTimeOfDay, manualDateClearedOrChanged, checklistDraftItems, sendImmediateNotification, voiceCategoryExplicit, draftCategoryId, showDatePicker, refocus, saving, isWhatsAppArmed, isLabsOpen, composerDurationMinutes, copyingPhotos, photoImportGeneration) {
        ReminderComposerState(editing, isChecklistMode, isVoiceDraft, requiresTimeSelection, pendingDateHintEpochDay, pendingTimeOfDay, manualDateClearedOrChanged, checklistDraftItems, sendImmediateNotification, voiceCategoryExplicit, draftCategoryId, showDatePicker, refocus, saving, isWhatsAppArmed, isLabsOpen, composerDurationMinutes, copyingPhotos, photoImportGeneration)
    }
}
