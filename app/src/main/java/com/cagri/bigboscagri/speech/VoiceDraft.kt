package com.cagri.bigboscagri.speech

/** One editable draft, even when Android returns several speech segments. */
class VoiceDraft(initialText: String) {
    private var committed = initialText.trim()
    private var partial = ""
    val text: String get() = join(committed, partial)

    fun updatePartial(words: String) {
        if (words.isNotBlank()) partial = words.trim()
    }

    fun commitResult(words: String?) {
        committed = join(committed, words?.trim()?.takeIf { it.isNotEmpty() } ?: partial)
        partial = ""
    }

    private fun join(first: String, second: String): String =
        listOf(first, second).filter { it.isNotBlank() }.joinToString(" ")
}

enum class VoicePhase { Idle, Listening, Finishing }
