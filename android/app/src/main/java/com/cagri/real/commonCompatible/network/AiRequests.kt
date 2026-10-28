package com.cagri.real.network

import com.cagri.real.data.MoodSuggestionKind
import com.cagri.real.speech.VoiceLastCard
import com.cagri.real.speech.VoiceRecentCard

data class MoodSuggestionRequest(
    val mood: String,
    val descriptors: List<String>,
    val factors: List<String>,
    val replaceKind: MoodSuggestionKind? = null,
    val exclude: List<String> = emptyList(),
    val excludeMusic: List<String> = emptyList(),
    val excludeMedia: List<String> = emptyList(),
    val excludeIdeas: List<String> = emptyList(),
    val scope: String? = null,
    val localHour: Int? = null,
    val language: String? = null
) {
    val source: String get() = "mood_tracker"
    fun normalizedMusicExclusions(): List<String> = excludeMusic.map { it.trim().take(320) }
        .filter { it.isNotBlank() }.distinct().takeLast(32)
    fun normalizedMediaExclusions(): List<String> = excludeMedia.map { it.trim().take(320) }
        .filter { it.isNotBlank() }.distinct().takeLast(32)
    fun normalizedIdeaExclusions(): List<String> = excludeIdeas.map { it.trim().take(320) }
        .filter { it.isNotBlank() }.distinct().takeLast(24)
    fun normalizedExclusions(): List<String> = exclude.map { it.trim() }.filter { it.isNotBlank() }.take(16)
}

data class VoiceIntentRequest(
    val transcript: String,
    val transcriptAlternatives: List<String>,
    val nowIsoOffset: String,
    val timezone: String,
    val locale: String,
    val categories: List<String>,
    /** Card a spoken follow-up may edit ("onu 8'e al"). Null = nothing to edit; the key is then omitted. */
    val lastCard: VoiceLastCard? = null,
    /** Roughly 10-15 cards the AI may act on by identity. Empty = key omitted (old behaviour). */
    val recentCards: List<VoiceRecentCard> = emptyList(),
    val maxCreationItems: Int = 20
) {
    val source: String get() = "voice_composer"
    fun normalizedTranscript(): String = transcript.trim().take(2_000)
    fun normalizedAlternatives(): List<String> = transcriptAlternatives.asSequence()
        .map { it.trim().take(2_000) }
        .filter { it.isNotBlank() && !it.equals(transcript.trim(), ignoreCase = true) }
        .distinctBy { it.lowercase() }
        .take(5).toList()
    fun limitedCategories(): List<String> = categories.take(50)
    fun limitedRecentCards(): List<VoiceRecentCard> = recentCards.take(15)
}
