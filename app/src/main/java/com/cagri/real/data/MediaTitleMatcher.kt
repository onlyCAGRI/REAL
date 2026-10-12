package com.cagri.real.data

/**
 * Normalization helper for TV and Movie title matching.
 * Handles Turkish characters, punctuation, apostrophes, colon/subtitles, and casing.
 */
object MediaTitleMatcher {
    fun normalize(input: String): String {
        val lower = input.lowercase()
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            when (ch) {
                'ı', 'i', 'İ', 'I' -> sb.append('i')
                'ç' -> sb.append('c')
                'ğ' -> sb.append('g')
                'ö' -> sb.append('o')
                'ş' -> sb.append('s')
                'ü' -> sb.append('u')
                'é', 'è', 'ê', 'ë' -> sb.append('e')
                'á', 'à', 'â', 'ä', 'ã' -> sb.append('a')
                'ó', 'ò', 'ô', 'ö', 'õ' -> sb.append('o')
                'ú', 'ù', 'û', 'ü' -> sb.append('u')
                in 'a'..'z', in '0'..'9' -> sb.append(ch)
                ' ', '-', '_', '\'', '’', ':', ';', ',', '.', '!', '?', '/', '\\', '&' -> sb.append(' ')
                else -> {}
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}
