package com.cagri.real.data

data class MoodTrackerEntry(
    val id: Long,
    val mood: JournalMood,
    val details: MoodDetails
)

