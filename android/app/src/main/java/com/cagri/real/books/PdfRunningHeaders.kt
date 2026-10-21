package com.cagri.real.books

import java.util.Locale
import java.text.Normalizer
import kotlin.math.abs

/** Removal needs matching geometry/text on TWO other pages. Uncertain text is retained. */
internal object PdfRunningHeaders {
    data class Mark(val signature: String, val band: Int, val y: Float, val center: Float)
    data class Snapshot(val page: Int, val marks: List<Mark>)

    fun mark(line: PdfParagraphLayout.Line, width: Int, height: Int, bodyHeight: Float): Mark? {
        if (width <= 0 || height <= 0 || bodyHeight <= 0f) return null
        val text = ParagraphKind.plain(line.text).trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty() || text.length > 120) return null
        val band = when { line.bottom < height * 0.105f -> 0; line.top > height * 0.90f -> 1; else -> return null }
        if (text.matches(Regex("^(?:bölüm|kısım|chapter|part|section)\\s+.*", RegexOption.IGNORE_CASE))) return null
        val edgeNumber = Regex("^(?:\\d{1,4}\\s+)|(?:\\s+\\d{1,4})$")
        val numeric = text.matches(Regex("[-–—\\s]*\\d{1,4}[-–—\\s]*"))
        val hasNumber = edgeNumber.containsMatchIn(text)
        // Numbered textual furniture can have the same OCR box height as body glyphs.
        // Body-sized standalone numbers remain protected chapter candidates.
        val maximumHeight = if (hasNumber && !numeric) bodyHeight * 1.15f else bodyHeight * .95f
        if (line.bottom - line.top > maximumHeight) return null
        val signature = if (numeric) "#" else normalize(text.replace(edgeNumber, " # "))
        if (signature != "#" && !signature.contains('#') && text.split(' ').size < 3) return null
        return Mark(signature, band, (line.top + line.bottom) / (2f * height), (line.left + line.right) / (2f * width))
    }

    private fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT).replace('ı', 'i'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}#]+"), " ").trim()

    /** Allow small OCR letter errors only for long numbered headers, with positional evidence. */
    internal fun sameSignature(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 20 || b.length < 20 || '#' !in a || '#' !in b) return false
        val limit = minOf(a.length, b.length) / 12
        if (abs(a.length - b.length) > limit) return false
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1); current[0] = i + 1
            for (j in b.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1,
                previous[j] + if (a[i] == b[j]) 0 else 1)
            previous = current
        }
        return previous[b.length] <= limit
    }

    fun snapshot(page: Int, lines: List<PdfParagraphLayout.Line>, width: Int, height: Int): Snapshot {
        val heights = lines.map { it.bottom - it.top }.filter { it > 0 }.sorted()
        val bodyHeight = heights.getOrNull(heights.size / 2)?.toFloat() ?: 0f
        return Snapshot(page, lines.mapNotNull { mark(it, width, height, bodyHeight) })
    }

    fun repeated(mark: Mark?, references: List<Snapshot>): Boolean {
        if (mark == null) return false
        return references.filter { page -> page.marks.any {
            sameSignature(it.signature, mark.signature) && it.band == mark.band &&
                abs(it.y - mark.y) < 0.015f && abs(it.center - mark.center) < 0.12f
        } }.map { it.page }.distinct().size >= 2
    }
}
