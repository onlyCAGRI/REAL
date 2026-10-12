package com.cagri.real.speech

enum class VoiceAttachmentKind {
    NONE,
    MUSIC,
    MOVIE,
    TV;

    companion object {
        fun fromRaw(raw: String?): VoiceAttachmentKind = runCatching {
            valueOf(raw.orEmpty().trim().uppercase())
        }.getOrDefault(NONE)
    }
}

data class VoiceNewCategoryRequest(
    val name: String,
    val iconKey: String,
    val colorName: String?
)

