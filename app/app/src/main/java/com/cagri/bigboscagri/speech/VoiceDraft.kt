package com.cagri.bigboscagri.speech

/** One editable draft, even when Android returns several speech segments. */
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
            hasSpokenText = true
        }
    }

    fun commitResult(words: String?) {
        val resolved = resolveBestResult(partial, words?.trim())
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
            val f = finalResult?.trim().orEmpty()
            val p = partial.trim()
            if (f.isEmpty()) return p
            if (p.isEmpty()) return f

            val trLocale = java.util.Locale.forLanguageTag("tr-TR")
            val pNorm = p.lowercase(trLocale)
            val fNorm = f.lowercase(trLocale)

            if (pNorm == fNorm) return f

            val pWords = pNorm.split(Regex("""\s+""")).filter { it.isNotBlank() }
            val fWords = fNorm.split(Regex("""\s+""")).filter { it.isNotBlank() }

            if (pWords.size > fWords.size && pWords.take(fWords.size) == fWords) {
                return p
            }

            if (fWords.size >= pWords.size && fWords.take(pWords.size) == pWords) {
                return f
            }

            return f
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
        val words = partial.trim().split(Regex("\\s+"))
        return if (words.size > 3) {
            val splitIdx = words.size - 2
            words.take(splitIdx).joinToString(" ") to words.drop(splitIdx).joinToString(" ")
        } else {
            "" to partial
        }
    }
}

enum class VoicePhase { Idle, Listening, Finishing }
