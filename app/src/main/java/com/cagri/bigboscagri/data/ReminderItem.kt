package com.cagri.bigboscagri.data

data class ReminderAttachment(
    val id: String,
    val localPath: String
)

enum class JournalMood(val persistKey: String) {
    VERY_GOOD("very_good"),
    GOOD("good"),
    NEUTRAL("neutral"),
    HARD("hard"),
    BAD("bad"),
    HAPPY("happy"),
    EXCITED("excited"),
    CALM("calm"),
    TIRED("tired"),
    BORED("bored"),
    STRESSED("stressed"),
    ANGRY("angry"),
    SAD("sad"),
    LOW("low");

    companion object {
        fun fromKey(key: String?): JournalMood? = entries.firstOrNull { it.persistKey == key }
    }
}

data class ReminderJournal(
    val mood: JournalMood?,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

data class ReminderItem(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val completedAt: Long? = null,
    val isImportant: Boolean = false,
    val alertAt: Long? = null,
    val notifiedAlertAt: Long? = null,
    val accentStart: Int? = null,
    val accentEnd: Int? = null,
    val repeatRule: RecurrenceRule = RecurrenceRule.NONE,
    val anchorAlertAt: Long? = alertAt,
    val customCategoryId: Long? = null,
    val isChecklist: Boolean = false,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val sendImmediateNotification: Boolean = true,
    val referenceUrl: String? = null,
    val starBorderColorIndex: Int? = null,
    val gradientBlendFraction: Float? = null,
    val customSolidColorArgb: Int? = null,
    val attachments: List<ReminderAttachment> = emptyList(),
    val journal: ReminderJournal? = null,
    val location: ReminderLocation? = null
) {
    val gradientBlendIndex: Int?
        get() = gradientBlendFraction?.let { frac ->
            GRADIENT_BLEND_FRACTIONS.indices.minByOrNull { kotlin.math.abs(GRADIENT_BLEND_FRACTIONS[it] - frac) }
        }
    val isCompleted: Boolean get() = completedAt != null
    val shouldPinNotification: Boolean get() = isImportant && !isCompleted
    val hasRepeat: Boolean get() = repeatRule != RecurrenceRule.NONE
    val isNoAlert: Boolean get() = !isCompleted && alertAt == null && customCategoryId == null

    fun toggleCompleted(now: Long): ReminderItem {
        if (isCompleted) {
            return copy(completedAt = null, notifiedAlertAt = null, updatedAt = now)
        }
        if (hasRepeat && alertAt != null) {
            if (notifiedAlertAt != null) {
                // An occurrence was delivered by deliverDue and alertAt was already advanced.
                // Acknowledging that delivered occurrence clears the marker without advancing alertAt again.
                return copy(notifiedAlertAt = null, updatedAt = now)
            }
            // No pending delivered occurrence: this is an intentional early skip/completion of the current scheduled occurrence.
            val anchor = anchorAlertAt ?: alertAt
            val nextOccurrence = computeNextOccurrence(
                baseAlertAt = anchor,
                rule = repeatRule,
                afterMillis = maxOf(now, alertAt)
            )
            return if (nextOccurrence != null) {
                copy(
                    alertAt = nextOccurrence,
                    anchorAlertAt = anchor,
                    notifiedAlertAt = null,
                    updatedAt = now
                )
            } else {
                copy(completedAt = now, updatedAt = now)
            }
        }
        return copy(completedAt = now, updatedAt = now)
    }
}

enum class ReminderSortOrder {
    RECENTLY_ADDED,
    REMINDER_TIME
}

const val COLOR_FILTER_DEFAULT = -1

fun filterAndSortReminders(
    reminders: List<ReminderItem>,
    colorFilter: Int? = null,
    sortOrder: ReminderSortOrder = ReminderSortOrder.RECENTLY_ADDED
): List<ReminderItem> {
    var result = reminders
    if (colorFilter != null) {
        result = if (colorFilter == COLOR_FILTER_DEFAULT) {
            result.filter { it.accentStart == null }
        } else {
            result.filter { it.accentStart == colorFilter || it.accentEnd == colorFilter }
        }
    }
    result = when (sortOrder) {
        ReminderSortOrder.RECENTLY_ADDED -> {
            result.sortedByDescending { it.createdAt }
        }
        ReminderSortOrder.REMINDER_TIME -> {
            result.sortedWith(
                compareBy<ReminderItem> { it.alertAt == null }
                    .thenBy { it.alertAt }
                    .thenByDescending { it.createdAt }
            )
        }
    }
    return result
}

const val HOME_COMPLETED_PREVIEW_LIMIT = 8
const val HOME_COMPLETED_GLASS_HEIGHT_DP = 108f

fun isHomeCompletedGlassActive(completedCount: Int): Boolean =
    completedCount >= HOME_COMPLETED_PREVIEW_LIMIT

fun buildReminderShareText(reminder: ReminderItem): String {
    if (!reminder.isChecklist) {
        return reminder.text
    }
    val validItems = reminder.checklistItems.filter { it.text.isNotBlank() }
    if (validItems.isEmpty()) {
        return reminder.text
    }
    val itemsText = validItems.joinToString("\n") { item ->
        val prefix = if (item.isCompleted) "☑ " else "☐ "
        prefix + item.text.trim()
    }
    return "${reminder.text.trim()}\n\n$itemsText"
}

val GRADIENT_BLEND_FRACTIONS = listOf(0.20f, 0.35f, 0.50f, 0.65f, 0.80f)
const val DEFAULT_GRADIENT_BLEND_INDEX = 2
const val DEFAULT_GRADIENT_BLEND_FRACTION = 0.50f

fun resolveGradientBlendFraction(fraction: Float?): Float {
    if (fraction == null) return DEFAULT_GRADIENT_BLEND_FRACTION
    return fraction.coerceIn(0.0f, 1.0f)
}

fun migrateLegacyGradientBlendIndex(index: Int?): Float {
    if (index == null || index !in GRADIENT_BLEND_FRACTIONS.indices) {
        return DEFAULT_GRADIENT_BLEND_FRACTION
    }
    return GRADIENT_BLEND_FRACTIONS[index]
}
