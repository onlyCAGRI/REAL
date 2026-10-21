package com.cagri.real.data

/** All time inputs are supplied by the caller; no implicit clock or timezone. */
object CategoryRules {
    const val ROLLING_24_HOURS_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun isQuick24HourActive(completed: Boolean, until: Long?, nowMillis: Long): Boolean =
        !completed && until?.let { it > nowMillis } == true

    fun isNoAlert(completed: Boolean, alertAt: Long?, plannedDay: Long?, quickUntil: Long?, categoryId: Long?, nowMillis: Long): Boolean =
        !completed && alertAt == null && plannedDay == null &&
            quickUntil?.let { it > nowMillis } != true && categoryId == null

    fun isIn24HourWindow(completed: Boolean, alertAt: Long?, plannedDay: Long?, quickUntil: Long?, nowMillis: Long, todayEpochDay: Long): Boolean {
        if (completed) return false
        if (isQuick24HourActive(completed, quickUntil, nowMillis)) return true
        if (alertAt != null && alertAt >= nowMillis && alertAt <= nowMillis + ROLLING_24_HOURS_MILLIS) return true
        return plannedDay != null && plannedDay == todayEpochDay
    }

    fun normalizedName(name: String): String {
        require(name.isNotBlank()) { "Kategori adı boş olamaz" }
        return name.trim()
    }

    fun nextId(now: Long, existingIds: List<Long>): Long = maxOf(now, (existingIds.maxOrNull() ?: 0L) + 1L)
}
