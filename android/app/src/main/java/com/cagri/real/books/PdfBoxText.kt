package com.cagri.real.books

import android.content.Context
import android.graphics.Rect
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/**
 * The PDF's own text layer on every Android version (PdfRenderer only exposes text from Android 15, and never the
 * font). Each word comes with its box in page-bitmap pixels and whether its font is bold or italic, so copyable PDFs
 * are read as written and only pages without a usable text layer go to OCR. Nothing leaves the device.
 */
internal class PdfBoxText(context: Context) {
    init { PDFBoxResourceLoader.init(context.applicationContext) }

    private var openPath: String? = null
    private var document: PDDocument? = null

    /** One line of words, in reading order. */
    class Page(val lines: List<PageLayout.NativeRun>, val width: Int, val height: Int)

    @Synchronized
    fun close() {
        runCatching { document?.close() }
        document = null; openPath = null
    }

    @Synchronized
    private fun open(file: File): PDDocument {
        if (openPath == file.absolutePath) document?.let { return it }
        close()
        return PDDocument.load(file).also { document = it; openPath = file.absolutePath }
    }

    /** Lines of the page scaled to a bitmap [bitmapWidth] px wide; null when the file cannot be read this way. */
    @Synchronized
    fun page(file: File, pageIndex: Int, bitmapWidth: Int): Page? = runCatching {
        val doc = open(file)
        if (doc.isEncrypted && !doc.currentAccessPermission.canExtractContent()) return null
        if (pageIndex !in 0 until doc.numberOfPages) return null
        val box = doc.getPage(pageIndex).cropBox
        val scale = bitmapWidth / box.width.coerceAtLeast(1f)
        val collector = Collector(scale)
        collector.sortByPosition = true
        collector.startPage = pageIndex + 1
        collector.endPage = pageIndex + 1
        collector.getText(doc)
        Page(collector.finish(), bitmapWidth, (box.height * scale).toInt())
    }.getOrNull()

    private class Collector(private val scale: Float) : PDFTextStripper() {
        private val lines = ArrayList<PageLayout.NativeRun>()
        private val current = ArrayList<PageLayout.StyledWord>()

        override fun writeString(text: String, positions: MutableList<TextPosition>) {
            val clean = BookDocumentRepository.cleanNativeText(text).trim()
            if (clean.isEmpty() || positions.isEmpty()) return
            var left = Float.MAX_VALUE; var right = 0f; var top = Float.MAX_VALUE; var bottom = 0f
            var boldLetters = 0; var italicLetters = 0; var letters = 0
            for (p in positions) {
                left = minOf(left, p.xDirAdj); right = maxOf(right, p.xDirAdj + p.widthDirAdj)
                top = minOf(top, p.yDirAdj - p.heightDir); bottom = maxOf(bottom, p.yDirAdj)
                if (p.unicode.orEmpty().none { it.isLetterOrDigit() }) continue
                letters++
                if (isBold(p)) boldLetters++
                if (isItalic(p)) italicLetters++
            }
            if (right <= left || bottom <= top) return
            // Words split by an inline space inside one writeString call keep one box per token.
            val tokens = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val total = tokens.sumOf { it.length }.coerceAtLeast(1).toFloat()
            var x = left
            for (t in tokens) {
                val w = (right - left) * t.length / total
                current.add(PageLayout.StyledWord(t,
                    Rect((x * scale).toInt(), (top * scale).toInt(), ((x + w) * scale).toInt().coerceAtLeast((x * scale).toInt() + 1),
                        (bottom * scale).toInt().coerceAtLeast((top * scale).toInt() + 1)),
                    italic = letters > 0 && italicLetters * 2 > letters,
                    bold = letters > 0 && boldLetters * 2 > letters))
                x += w
            }
        }

        override fun writeLineSeparator() { flushLine() }
        override fun writeParagraphEnd() { flushLine() }
        override fun endPage(page: com.tom_roush.pdfbox.pdmodel.PDPage?) { flushLine(); super.endPage(page) }

        private fun flushLine() {
            if (current.isEmpty()) return
            val words = current.toList(); current.clear()
            val box = Rect(words.minOf { it.box.left }, words.minOf { it.box.top }, words.maxOf { it.box.right }, words.maxOf { it.box.bottom })
            lines.add(PageLayout.NativeRun(words.joinToString(" ") { it.text }, box, words))
        }

        fun finish(): List<PageLayout.NativeRun> { flushLine(); return lines }

        private fun fontName(p: TextPosition): String = p.font?.name.orEmpty().substringAfter('+').lowercase()

        private fun isBold(p: TextPosition): Boolean {
            val d = p.font?.fontDescriptor
            if (d != null && (d.isForceBold || d.fontWeight >= 600f)) return true
            val n = fontName(p)
            return Bold.any { it in n }
        }

        private fun isItalic(p: TextPosition): Boolean {
            val d = p.font?.fontDescriptor
            if (d != null && (d.isItalic || kotlin.math.abs(d.italicAngle) >= 4f)) return true
            val n = fontName(p)
            return "italic" in n || "oblique" in n || n.endsWith("-it") || n.endsWith(",italic")
        }

        companion object {
            private val Bold = listOf("bold", "black", "heavy", "semibold", "demibold", "extrabold", "ultrabold")
        }
    }
}
