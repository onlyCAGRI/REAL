package com.cagri.real.data

data class ChecklistItem(
    val id: Long,
    val text: String,
    val completedAt: Long? = null
) {
    val isCompleted: Boolean get() = completedAt != null

    fun toggleCompleted(now: Long = System.currentTimeMillis()): ChecklistItem =
        copy(completedAt = if (isCompleted) null else now)
}
