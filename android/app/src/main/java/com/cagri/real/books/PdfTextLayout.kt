package com.cagri.real.books

/** Native PDF bounds describe individual lines, not a single paragraph/page envelope. */
internal object PdfTextLines {
    // Keep paragraph/italic markers intact; collapse only horizontal whitespace.
    fun readableSpacing(text: String): String = text.replace(
        Regex("[ \\t\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000]+"), " ")
    fun split(text: String, boundsCount: Int): List<String>? {
        if (boundsCount < 1) return null
        val lines = text.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        // No guessed word wrapping: ambiguous geometry uses the existing on-device OCR path.
        return lines.takeIf { it.size == boundsCount }
    }
}

/** Shared native/OCR paragraph boundaries, independent of Android so the geometry can be tested. */
internal object PdfParagraphLayout {
    data class Line(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val block: Int)
    data class Group(val from: Int, val until: Int, val gap: Int, val heading: Boolean)

    fun groups(lines: List<Line>, heightOverride: Int? = null, blocksAreParagraphs: Boolean = false): List<Group> {
        if (lines.isEmpty()) return emptyList()
        val heights = lines.map { it.bottom - it.top }.sorted()
        val height = (heightOverride ?: heights[heights.size / 2]).coerceAtLeast(8).toFloat()
        val lefts = lines.map { it.left }.sorted()
        val rights = lines.map { it.right }.sorted()
        val bodyLeft = lefts[lefts.size / 4]
        val bodyRight = rights[rights.size * 3 / 4]
        val bodyWidth = (bodyRight - bodyLeft).coerceAtLeast(1)
        val ordinaryGaps = lines.zipWithNext().map { (a, b) -> b.top - a.bottom }
            .filter { it >= 0 && it < height * 1.5f }.sorted()
        val normalGap = if (ordinaryGaps.isEmpty()) 0f else ordinaryGaps[ordinaryGaps.size / 2].toFloat()
        fun separated(gap: Int) = gap > normalGap + height * 0.5f
        val headings = lines.mapIndexed { i, line ->
            if (line.text.startsWith(ParagraphKind.HEADING)) return@mapIndexed true
            if (line.text.startsWith(ParagraphKind.BODY)) return@mapIndexed false
            val text = ParagraphKind.plain(line.text).trim()
            val short = text.length in 1..70 && text.split(Regex("\\s+")).size <= 10
            val centered = line.right - line.left < bodyWidth * 0.65f &&
                line.left - bodyLeft > height * 0.7f && bodyRight - line.right > height * 0.7f &&
                kotlin.math.abs((line.left + line.right) / 2f - (bodyLeft + bodyRight) / 2f) <= height * 1.5f
            val isolated = (i == 0 || separated(line.top - lines[i - 1].bottom)) ||
                (i + 1 < lines.size && separated(lines[i + 1].top - line.bottom))
            val letters = text.filter { it.isLetter() }
            short && isolated && ((ParagraphKind.isHeading(text) &&
                (centered || letters.any { it.isLowerCase() } || text.any { it.isDigit() })) ||
                (centered && letters.length >= 2 && letters.all { it.isUpperCase() }) ||
                (centered && text.length <= 4 && text.all { it.isDigit() }))
        }
        val groups = mutableListOf<Group>()
        var from = 0
        var groupGap = 0
        for (i in 1 until lines.size) {
            val line = lines[i]; val prev = lines[i - 1]
            val gapPx = line.top - prev.bottom
            val gap = gapPx / height
            val indent = (line.left - bodyLeft) / height
            val prevShort = prev.right < bodyRight - height * 2.5f
            val prevEnds = prev.text.trimEnd().lastOrNull()?.let { it in ".!?…\"”»’)" } == true
            val boundary = line.text.startsWith(ParagraphKind.BODY) || prev.text.startsWith(ParagraphKind.BODY) ||
                headings[i] || headings[i - 1] ||
                (blocksAreParagraphs && line.block != prev.block) ||
                (gap > 0.6f && separated(gapPx)) ||
                (line.block != prev.block && ((gap > 0.25f && separated(gapPx)) || prevShort)) ||
                (indent > 0.65f && (prevEnds || prevShort)) || (prevShort && prevEnds)
            if (boundary) {
                groups.add(Group(from, i, groupGap, headings[from] && i == from + 1))
                from = i
                groupGap = when { gap > 2.2f -> 2; gap > 1.05f -> 1; else -> 0 }
            }
        }
        groups.add(Group(from, lines.size, groupGap, headings[from] && lines.size == from + 1))
        return groups
    }
}

/** Attribution is inferred from italic context AND right alignment, never a person's name alone. */
internal object PdfQuoteLayout {
    fun attributions(lines: List<PdfParagraphLayout.Line>, italicShares: List<Float>): Set<Int> {
        if (lines.size < 2 || italicShares.size != lines.size) return emptySet()
        val lefts = lines.map { it.left }.sorted()
        val rights = lines.map { it.right }.sorted()
        val left = lefts[lefts.size / 4]
        val right = rights[rights.size * 3 / 4]
        val width = (right - left).coerceAtLeast(1)
        val heights = lines.map { it.bottom - it.top }.sorted()
        val h = heights[heights.size / 2].coerceAtLeast(1)
        val found = mutableSetOf<Int>()
        for (i in 1 until lines.size) {
            val line = lines[i]; val previous = lines[i - 1]
            val text = ParagraphKind.plain(line.text)
            val rightAligned = line.left > left + width * .22f &&
                kotlin.math.abs(line.right - right) <= h * 2.5f && line.right - line.left < width * .75f
            val nearQuote = line.top - previous.bottom in 0..(h * 4) &&
                (italicShares[i - 1] >= .6f || i - 1 in found)
            if (rightAligned && nearQuote && text.length in 2..100 && text.any { it.isLetter() } &&
                text.split(Regex("\\s+")).size <= 12 && !text.endsWith('?') && !text.endsWith('!')) found.add(i)
        }
        return found
    }
}
