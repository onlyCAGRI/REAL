package com.cagri.real.data

enum class MoodSuggestionKind {
    TEXT,
    MESSAGE,
    CALL,
    OUTDOOR,
    ACTIVITY,
    READ,
    MUSIC,
    MOVIE,
    TV;

    companion object {
        fun fromRaw(raw: String?): MoodSuggestionKind? {
            if (raw.isNullOrBlank()) return null
            return try {
                valueOf(raw.trim().uppercase())
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class MoodSuggestionMusicCandidate(
    val artist: String,
    val track: String,
    val searchQuery: String
)

data class MoodSuggestionMediaCandidate(
    val title: String,
    val year: Int? = null,
    val searchQuery: String
)

enum class MoodSuggestionDirection {
    EXPRESS,
    SHARE,
    RECONNECT,
    REMEMBER,
    CREATE_REAL,
    DO,
    DISCOVER,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): MoodSuggestionDirection {
            if (raw.isNullOrBlank()) return UNKNOWN
            return try {
                valueOf(raw.trim().uppercase())
            } catch (_: Exception) {
                UNKNOWN
            }
        }
    }
}

data class MoodSuggestion(
    val kind: MoodSuggestionKind,
    val direction: MoodSuggestionDirection,
    val title: String,
    val subtitle: String,
    val music: MoodSuggestionMusicCandidate? = null,
    val media: MoodSuggestionMediaCandidate? = null
)

