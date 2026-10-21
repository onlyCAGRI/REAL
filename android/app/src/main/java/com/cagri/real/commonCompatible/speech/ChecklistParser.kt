package com.cagri.real.speech

data class ExtractedChecklist(
    val title: String,
    val items: List<String>
)

fun stripBoundaryFillerYa(text: String): String {
    var result = text.trim()
    if (result.isEmpty()) return ""

    

    val leadingYaRegex = Regex("""(?i)^\s*[,\s;:!?.…—–-]*ya(?!\p{L})(?!\s+(?:da|de)(?!\p{L}))[,\s;:!?.…—–-]*""")
    result = leadingYaRegex.replace(result, "")

    
    val trailingYaRegex = Regex("""(?i)(?<!\p{L})ya[,\s;:!?.…—–-]*$""")
    result = trailingYaRegex.replace(result, "")

    return result.trim()
}

/** Parsing rules are shared; language-sensitive casing is supplied by the platform. */
class ChecklistParser(
    private val lowercase: (String) -> String,
    private val titlecase: (String) -> String
) {

    fun formatChecklistTitle(prefix: String?): String {
        var cleanPrefix = prefix?.trim() ?: ""

        val leadingArticleRegex = Regex("""(?i)^(?:yeni\s+)?bir\s+""")
        cleanPrefix = leadingArticleRegex.replace(cleanPrefix, "").trim()

        if (cleanPrefix.isBlank()) {
            return "Liste"
        }

        val lower = lowercase(cleanPrefix)
        val title = when {
            lower.endsWith("listesi") -> cleanPrefix
            lower.endsWith("listem") -> cleanPrefix.dropLast(1) + "si"
            lower.endsWith("liste") -> cleanPrefix + "si"
            else -> "$cleanPrefix listesi"
        }

        return titlecase(title)
    }

    fun splitChecklistItems(raw: String): List<String> {
        val initialSegments = raw.split(Regex("""[,;]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (initialSegments.isEmpty()) return emptyList()

        val result = mutableListOf<String>()

        for (i in initialSegments.indices) {
            var seg = initialSegments[i].trim()
            if (lowercase(seg).startsWith("ve ")) {
                seg = seg.substring(3).trim()
            }
            if (seg.isEmpty()) continue

            if (i == initialSegments.lastIndex) {
                val subItems = seg.split(Regex("""(?i)\s+ve\s+"""))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                result.addAll(subItems)
            } else {
                result.add(seg)
            }
        }

        return result.mapNotNull { item ->
            var trimmed = item.trim().trimStart('-', '*', '•').trim()
            trimmed = stripBoundaryFillerYa(trimmed)
            if (trimmed.isEmpty()) null
            else {
                titlecase(trimmed)
            }
        }
    }

    fun extractChecklist(text: String): ExtractedChecklist? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        val pattern1 = Regex(
            """(?i)^(?:(.*?)\s+)?(listesi|listem|liste)\s+(oluştur|olustur|yap|hazırla|hazirla|kur)\b[:,\s\-]*(.+)$"""
        )
        pattern1.find(trimmed)?.let { match ->
            val prefix = match.groupValues[1]
            val rawItems = match.groupValues[4]
            val items = splitChecklistItems(rawItems)
            if (items.isNotEmpty()) {
                return ExtractedChecklist(
                    title = formatChecklistTitle(prefix),
                    items = items
                )
            }
        }

        val patternVerbFirst = Regex(
            """(?i)^(oluştur|olustur|yap|hazırla|hazirla|kur)\s+(?:bir\s+)?(?:(.*?)\s+)?(listesi|listem|liste)\b[:,\s\-]*(.+)$"""
        )
        patternVerbFirst.find(trimmed)?.let { match ->
            val prefix = match.groupValues[2]
            val rawItems = match.groupValues[4]
            val items = splitChecklistItems(rawItems)
            if (items.isNotEmpty()) {
                return ExtractedChecklist(
                    title = formatChecklistTitle(prefix),
                    items = items
                )
            }
        }

        val patternAddA = Regex(
            """(?i)^(?:(.*?)\s+)?(?:listeme|listesine)\s+(?:ekle|yaz)\b[:,\s\-]*(.+)$"""
        )
        patternAddA.find(trimmed)?.let { match ->
            val prefix = match.groupValues[1]
            val rawItems = match.groupValues[2]
            val items = splitChecklistItems(rawItems)
            if (items.isNotEmpty()) {
                return ExtractedChecklist(
                    title = formatChecklistTitle(prefix),
                    items = items
                )
            }
        }

        val patternAddB = Regex(
            """(?i)^(?:(.*?)\s+)?(?:listeme|listesine)\s+(.+?)\s+(?:ekle|yaz)$"""
        )
        patternAddB.find(trimmed)?.let { match ->
            val prefix = match.groupValues[1]
            val rawItems = match.groupValues[2]
            val items = splitChecklistItems(rawItems)
            if (items.isNotEmpty()) {
                return ExtractedChecklist(
                    title = formatChecklistTitle(prefix),
                    items = items
                )
            }
        }

        val patternColon = Regex(
            """(?i)^(?:(.*?)\s+)?(listesi|listem)\s*:\s*(.+)$"""
        )
        patternColon.find(trimmed)?.let { match ->
            val prefix = match.groupValues[1]
            val rawItems = match.groupValues[3]
            val items = splitChecklistItems(rawItems)
            if (items.isNotEmpty()) {
                return ExtractedChecklist(
                    title = formatChecklistTitle(prefix),
                    items = items
                )
            }
        }

        return null
    }

}
