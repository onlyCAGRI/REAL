package com.cagri.real.speech

import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.MoodSuggestionMusicCandidate
import com.cagri.real.data.MoodSuggestionMediaCandidate

/** Calendar types are supplied by the platform until a shared date library is selected. */
data class VoiceIntent<Date, Time>(
    val title: String,
    val date: Date?,
    val time: Time?,
    val recurrence: RecurrenceRule,
    val hasSchedule: Boolean,
    val requiresTimeSelection: Boolean,
    val timeOfDay: TimeOfDay?,
    val durationMinutes: Int?,
    val people: List<String>,
    val locationName: String?,
    val categoryName: String?,
    val isChecklist: Boolean,
    val checklistItems: List<String>,
    val attachmentKind: VoiceAttachmentKind = VoiceAttachmentKind.NONE,
    val music: MoodSuggestionMusicCandidate? = null,
    val media: MoodSuggestionMediaCandidate? = null,
    /** Weekday set for "her pazartesi, carsamba, cuma" (bit0 = Monday ... bit6 = Sunday). 0 = none. */
    val weekdaysMask: Int = 0,
    /** Inclusive last day of the repeat series ("sadece 1 ay"). Null = no end. */
    val repeatUntil: Date? = null
)

/**
 * A spoken follow-up that changes the most recently created/edited card instead of making a new one
 * ("onu 8'e al", "konum olarak ev ekle", "her pazar tekrarlasin", "bunu kisisel kategorisine tasi").
 * Null / false fields mean "leave that part untouched".
 */
data class VoiceEditPatch<Date, Time>(
    val newTitle: String? = null,
    val date: Date? = null,
    val time: Time? = null,
    /** Null = keep the current repeat rule. */
    val recurrence: RecurrenceRule? = null,
    val weekdaysMask: Int = 0,
    val clearRepeat: Boolean = false,
    val repeatUntil: Date? = null,
    val locationName: String? = null,
    val clearLocation: Boolean = false,
    val categoryName: String? = null
)

/** Compact description of the card a follow-up may refer to; sent to the backend with the transcript. */
data class VoiceLastCard(
    val title: String,
    val alertAtIso: String?,
    val plannedDate: String?,
    val categoryName: String?,
    val locationName: String?,
    val repeatSummary: String?
)

/** One entry of the compact card list sent to the backend so the AI can pick a target by identity. */
data class VoiceRecentCard(
    val id: Long,
    val title: String,
    val alertAtIso: String?,
    val plannedDate: String?,
    val categoryName: String?,
    val locationName: String?,
    val repeatSummary: String?,
    val isChecklist: Boolean,
    val completed: Boolean,
    val isLast: Boolean
)

enum class VoiceActionType {
    NONE, EDIT, POSTPONE, COMPLETE, REOPEN, DELETE, SKIP_OCCURRENCE, ADD_CHECKLIST_ITEMS, BULK, UNRESOLVED;

    companion object {
        fun fromRaw(raw: String?): VoiceActionType =
            values().firstOrNull { it.name == raw?.trim()?.uppercase(java.util.Locale.ROOT) } ?: NONE
    }
}

enum class VoiceBulkOperation {
    NONE, SHIFT, COMPLETE, DELETE;
    companion object {
        fun fromRaw(raw: String?) = values().firstOrNull { it.name == raw?.trim()?.uppercase(java.util.Locale.ROOT) } ?: NONE
    }
}

enum class VoiceBulkScope {
    NONE, TODAY, TOMORROW, OVERDUE, DATE, CATEGORY, LISTED;
    companion object {
        fun fromRaw(raw: String?) = values().firstOrNull { it.name == raw?.trim()?.uppercase(java.util.Locale.ROOT) } ?: NONE
    }
}

/** One operation on many cards. Dates are ISO yyyy-MM-dd strings ("" = unused); the platform parses them. */
data class VoiceBulk(
    val operation: VoiceBulkOperation,
    val scope: VoiceBulkScope,
    val date: String = "",
    val categoryName: String = "",
    val targetIds: List<Long> = emptyList(),
    val shiftDays: Int = 0,
    val newDate: String = ""
)

/** Backend verdict about an EXISTING card. [targetId] is always one of the ids sent in recentCards. */
data class VoiceAction(
    val type: VoiceActionType,
    val targetId: Long? = null,
    val checklistItems: List<String> = emptyList(),
    val bulk: VoiceBulk? = null,
    /** SKIP_OCCURRENCE only: local epoch day of the occurrence to skip; null means the upcoming occurrence. */
    val occurrenceEpochDay: Long? = null,
    val clarification: String? = null
)

data class VoiceParseResult<Date, Time>(
    val intents: List<VoiceIntent<Date, Time>>,
    val newCategory: VoiceNewCategoryRequest? = null,
    val edit: VoiceEditPatch<Date, Time>? = null,
    /** Null when the utterance only creates new cards (action NONE). */
    val action: VoiceAction? = null,
    /** What the AI understood; null = not reported (local parse, older backend) and treated as understood. */
    val heard: VoiceHeard? = null
)

/** A word of the transcript the AI could not resolve: [heard] occurs literally in the transcript, [meant] is its guess. */
data class VoiceUnsureWord(val heard: String, val meant: String?)

/**
 * [understood]: the whole sentence as the AI read it (foreign words corrected). [confident] false = ask the person
 * "Doğru mu anladım?" before saving anything.
 */
data class VoiceHeard(val understood: String, val unsure: List<VoiceUnsureWord>, val confident: Boolean)

