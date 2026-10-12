package com.cagri.real.speech


class VoiceDraft(initialText: String) {
    var committed: String = initialText.trim()
        private set
    var partial: String = ""
        private set
    var hasSpokenText: Boolean = false
        private set

    val text: String get() = join(committed, partial)

    fun updatePartial(words: String) {
        if (words.isNotBlank()) {
            partial = words.trim()
        }
    }

    fun commitResult(words: String?) {
        val resolved = words?.trim().orEmpty()
        // No final result means no commit. Keep the partial available for manual recovery.
        if (resolved.isBlank()) return
        if (resolved.isNotBlank()) {
            hasSpokenText = true
            if (committed.isNotBlank()) {
                val trLocale = java.util.Locale.forLanguageTag("tr-TR")
                val cNorm = committed.trim().lowercase(trLocale)
                val rNorm = resolved.trim().lowercase(trLocale)
                if (cNorm == rNorm) {
                    partial = ""
                    return
                } else if (rNorm.startsWith("$cNorm ")) {
                    committed = resolved.trim()
                    partial = ""
                    return
                }
            }
            committed = join(committed, resolved)
        }
        partial = ""
    }

    private fun join(first: String, second: String): String =
        listOf(first, second).filter { it.isNotBlank() }.joinToString(" ")

    companion object {
        fun resolveBestResult(partial: String, finalResult: String?): String {
            // Final recognition is authoritative, even when a partial was longer.
            return finalResult?.trim().orEmpty()
        }
    }
}

object VoiceTranscriptFormatter {
    fun splitCommittedAndPartial(
        value: String,
        committed: String,
        partial: String
    ): Pair<String, String> {
        if (value.isBlank()) return "" to ""
        if (committed.isNotBlank()) {
            return committed to partial
        }
        return "" to partial
    }
}

enum class VoicePhase { Idle, Listening, Finishing }
