package com.cagri.real.ui

import android.content.Context
import android.view.View
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import com.cagri.real.data.*
import com.cagri.real.speech.*
import com.cagri.real.ui.components.ReminderEntryTracker
import com.cagri.real.ui.components.planner.PlannerRepository
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal typealias AddReminderCallback = suspend (String, Long?, RecurrenceRule, Long?, Boolean, List<ChecklistItem>, Boolean, String?, List<ReminderAttachment>, List<ReminderVoiceRecording>, ReminderLocation?, Long?, Long?, List<MusicBlock>, List<MediaBlock>) -> Long?

/** Current render inputs. Recreated at the integration boundary, never persisted. */
internal data class ReminderComposerSnapshot(
    val draft: String,
    val alertAt: Long?,
    val repeatRule: RecurrenceRule,
    val category: ReminderCategory?,
    val customCategory: CustomCategory?,
    val customCategories: List<CustomCategory>,
    val reminders: List<ReminderItem>,
    val draftAttachments: List<ReminderAttachment>,
    val draftReferenceUrl: String?,
    val reduceAnimations: Boolean
)

/** Existing screen callback API, bundled internally without changing MainActivity. */
internal class ReminderComposerCallbacks(
    val onAddReminder: AddReminderCallback,
    val onDraftChange: (String) -> Unit,
    val onAlertChange: (Long?, RecurrenceRule) -> Unit,
    val onDraftAttachmentsChange: (List<ReminderAttachment>) -> Unit,
    val onDraftReferenceUrlChange: (String?) -> Unit,
    val onShareDraftChange: (Boolean) -> Unit,
    val onSetRepeatPattern: (Long, Int, Long?) -> Unit,
    val onSetTiming: (Long, Int?, Long?) -> Unit = { _, _, _ -> },
    val onSetAlarm: (Long, Boolean) -> Unit = { _, _ -> },
    val onSetLocationTrigger: (Long, com.cagri.real.data.LocationTrigger?) -> Unit = { _, _ -> }
)

/** Early warning, end and alarm picked in the composer, applied once the card exists. */
internal object ComposerTimingDraft {
    var earlyAlertMinutes by androidx.compose.runtime.mutableStateOf<Int?>(null)
    var endAt by androidx.compose.runtime.mutableStateOf<Long?>(null)
    /** Bell tapped a second time: the card rings as an alarm (needs a clock time). */
    var alarm by androidx.compose.runtime.mutableStateOf(false)
    /** "Konuma varınca hatırlat" picked from the composer pin. */
    var locationTrigger by androidx.compose.runtime.mutableStateOf<com.cagri.real.data.LocationTrigger?>(null)
    fun clear() { clearTiming(); locationTrigger = null }
    /** The date was removed: what hangs on a clock time goes, a place reminder stays. */
    fun clearTiming() { earlyAlertMinutes = null; endAt = null; alarm = false }
}

internal class ReminderVoiceActions(
    val onCreateVoiceCategory: suspend (String, String, Int) -> CustomCategory?,
    val onVoiceEdit: (Long, ReminderEditPatch) -> Unit,
    val onToggleCompleted: (Long) -> Unit,
    val voiceCardCallbacks: VoiceCardCallbacks
)

/** UI-scoped services only; no state or feature behavior. Lifetime remains the list composition. */
internal class ReminderComposerServices(
    val context: Context,
    val view: View,
    val focusManager: FocusManager,
    val keyboard: SoftwareKeyboardController?,
    val scope: CoroutineScope,
    val plannerRepository: PlannerRepository,
    val entryTracker: ReminderEntryTracker,
    val whatsAppRecipientRepo: WhatsAppRecipientRepository,
    val liveReminders: (() -> List<ReminderItem>)? = null
)
