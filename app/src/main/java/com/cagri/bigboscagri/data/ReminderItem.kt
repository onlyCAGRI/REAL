package com.cagri.bigboscagri.data

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
    val referenceUrl: String? = null
) {
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
