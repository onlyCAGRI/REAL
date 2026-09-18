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
    val anchorAlertAt: Long? = alertAt
) {
    val isCompleted: Boolean get() = completedAt != null
    val shouldPinNotification: Boolean get() = isImportant && !isCompleted
    val hasRepeat: Boolean get() = repeatRule != RecurrenceRule.NONE

    fun toggleCompleted(now: Long): ReminderItem = copy(
        completedAt = if (isCompleted) null else now,
        updatedAt = now
    )
}
