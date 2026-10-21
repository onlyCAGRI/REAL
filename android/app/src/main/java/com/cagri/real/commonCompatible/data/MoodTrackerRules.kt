package com.cagri.real.data

/** Storage and wall-clock access belong to the platform adapter. */
interface MoodEntryRepository {
    fun load(): List<MoodTrackerEntry>
    fun add(mood: JournalMood, details: MoodDetails): List<MoodTrackerEntry>
    fun clearAll()
}

object MoodTrackerRules {
    const val MAX_ENTRIES = 730

    fun add(previous: List<MoodTrackerEntry>, mood: JournalMood, details: MoodDetails, now: Long): List<MoodTrackerEntry> {
        val loggedAt = if (details.loggedAt > 0L) details.loggedAt else now
        val id = maxOf(now, (previous.maxOfOrNull { it.id } ?: 0L) + 1L)
        return (listOf(MoodTrackerEntry(id, mood, details.copy(loggedAt = loggedAt))) + previous)
            .sortedByDescending { it.details.loggedAt }
            .take(MAX_ENTRIES)
    }
}
