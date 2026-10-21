package com.cagri.real.speech

import java.util.Locale

/** Bounded N-best beam. Every hypothesis contains every completed recognition round. */
class VoiceHypotheses(initialText: String) {
    private var paths = listOf(initialText.trim())
    val alternatives: List<String> get() = paths.drop(1)

    fun append(candidates: List<String>) {
        val chunks = candidates.map(String::trim).filter(String::isNotBlank)
            .distinctBy { it.lowercase(Locale.ROOT) }.take(5)
        if (chunks.isEmpty()) return
        val previous = paths
        // Keep the primary, then alternatives from previous rounds and the current round.
        val pairs = buildList {
            add(previous.first() to chunks.first())
            for (rank in 1..4) {
                previous.getOrNull(rank)?.let { add(it to chunks.first()) }
                chunks.getOrNull(rank)?.let { add(previous.first() to it) }
            }
            for (prefix in previous) for (chunk in chunks) add(prefix to chunk)
        }
        paths = pairs.map { (prefix, chunk) ->
            val primaryPrefix = previous.first()
            val normalized = chunk.lowercase(Locale.ROOT)
            val base = primaryPrefix.lowercase(Locale.ROOT)
            val continuation = when {
                base.isNotBlank() && normalized == base -> ""
                base.isNotBlank() && normalized.startsWith("$base ") -> chunk.substring(primaryPrefix.length).trim()
                else -> chunk
            }
            VoiceDraft(prefix).apply { commitResult(continuation) }.committed
        }.distinctBy { it.lowercase(Locale.ROOT) }.take(6)
    }
}
