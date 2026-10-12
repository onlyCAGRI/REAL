package com.cagri.real.ai

import java.util.Locale

/** A bounded recency list of catalog identities, never mood text or private card content. */
object MoodMusicHistory {
    const val CAPACITY = 64
    const val REQUEST_LIMIT = 32
    fun key(value: String): String = value.lowercase(Locale.ROOT)
        .replace('ı', 'i').replace('ç', 'c').replace('ğ', 'g')
        .replace('ö', 'o').replace('ş', 's').replace('ü', 'u')
        .filter { it.isLetterOrDigit() }
    fun merge(previous: List<String>, added: List<String>): List<String> {
        val rows = LinkedHashMap<String, String>()
        (previous + added).forEach { raw ->
            val value = raw.trim().take(320)
            val identity = key(value)
            if (identity.isNotBlank()) {
                rows.remove(identity)
                rows[identity] = value
            }
        }
        return rows.values.toList().takeLast(CAPACITY)
    }
    fun exclusions(history: List<String>): List<String> = merge(emptyList(), history).takeLast(REQUEST_LIMIT)
}
