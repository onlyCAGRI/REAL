package com.cagri.real.speech

import java.text.Normalizer
import java.util.Locale

/** Match spoken spelling without changing REAL's stored names or permanent identities. */
fun voiceNameKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('ı', 'i')
    .replace(Regex("\\s+"), " ").trim()

private val voiceCommandWords = setOf("kart", "karti", "kartini", "kartlar", "sil", "tamamla", "tamamlandi", "bitti",
    "ertele", "yarina", "bugune", "haftaya", "onu", "bunu", "hayir", "her", "saat", "sadece", "kategori", "kategorisi", "liste", "listesi")

/** Ranking only: it widens the AI's candidate list; it never chooses or executes an action. */
fun voiceTitleRelevance(transcript: String, title: String, category: String?): Int {
    val words = voiceNameKey(transcript).split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 && it !in voiceCommandWords }.distinct()
    val target = voiceNameKey(title + " " + category.orEmpty()).split(Regex("[^a-z0-9]+"))
    return words.sumOf { spoken ->
        when {
            spoken in target -> 4
            target.any { word -> word.length >= 3 &&
                (spoken.startsWith(word) || word.startsWith(spoken) ||
                    (word.length >= 4 && spoken.length >= 4 && word.take(4) == spoken.take(4))) } -> 2
            else -> 0
        }
    }
}

fun findVoiceCategory(name: String, categories: List<com.cagri.real.data.CustomCategory>): com.cagri.real.data.CustomCategory? {
    categories.filter { it.name.trim().equals(name.trim(), ignoreCase = true) }.singleOrNull()?.let { return it }
    return categories.filter { voiceNameKey(it.name) == voiceNameKey(name) }.singleOrNull()
}
