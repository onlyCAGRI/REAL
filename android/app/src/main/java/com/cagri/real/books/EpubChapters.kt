package com.cagri.real.books

import com.cagri.real.i18n.t

import android.text.Html
import android.util.Xml
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** XHTML reading order and local content only. No WebView, JavaScript or remote image fetches. */
internal class EpubChapters(private val directory: File) {
    data class Index(val title: String, val author: String, val chapters: List<String>, val cover: ByteArray?)
    private fun bytes(zip: ZipFile, path: String, limit: Int = 3 * 1024 * 1024): ByteArray {
        val entry = zip.getEntry(path) ?: throw IOException(t("EPUB içeriği eksik."))
        return zip.getInputStream(entry).use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break
                if (out.size() + n > limit) throw IOException(t("EPUB bölümü çok büyük."))
                out.write(buffer, 0, n)
            }; out.toByteArray()
        }
    }
    private fun xml(raw: ByteArray) = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        // XHTML commonly uses named HTML entities whose DTD is deliberately not fetched.
        val clean = raw.toString(Charsets.UTF_8).replace(Regex("&([A-Za-z][A-Za-z0-9]+);")) { match ->
            if (match.groupValues[1] in setOf("amp", "lt", "gt", "quot", "apos")) match.value
            else {
                val decoded = Html.fromHtml(match.value, Html.FROM_HTML_MODE_LEGACY).toString()
                if (decoded == match.value) "&amp;" + match.value.drop(1)
                else decoded.codePoints().toArray().joinToString("") { "&#$it;" }
            }
        }
        setInput(clean.reader())
    }
    private fun resolve(base: String, href: String): String? = runCatching {
        val target = URI("/$base").resolve(href).normalize()
        if (target.scheme != null || target.authority != null || !target.path.startsWith('/')) null
        else target.path.removePrefix("/").takeIf { it.isNotBlank() && ".." !in it.split('/') }
    }.getOrNull()
    fun index(file: File, includeCover: Boolean = false): Index = ZipFile(file).use { zip ->
        val container = xml(bytes(zip, "META-INF/container.xml")); var opf: String? = null
        while (container.eventType != XmlPullParser.END_DOCUMENT) {
            if (container.eventType == XmlPullParser.START_TAG && container.name == "rootfile") opf = container.getAttributeValue(null, "full-path")
            container.next()
        }
        val path = opf ?: throw IOException(t("EPUB kitap yapısı bulunamadı."))
        val parser = xml(bytes(zip, path)); val manifest = mutableMapOf<String, String>(); val spine = mutableListOf<String>()
        var title = ""; var author = ""; var coverId: String? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                "title" -> title = parser.nextText().take(400)
                "creator" -> if (author.isBlank()) author = parser.nextText().take(300)
                "meta" -> if (parser.getAttributeValue(null, "name") == "cover") coverId = parser.getAttributeValue(null, "content")
                "item" -> {
                    val id = parser.getAttributeValue(null, "id"); val href = parser.getAttributeValue(null, "href")
                    if (id != null && href != null) resolve(path, href)?.let { manifest[id] = it }
                    if (parser.getAttributeValue(null, "properties").orEmpty().split(' ').contains("cover-image")) coverId = id
                }
                "itemref" -> if (parser.getAttributeValue(null, "linear") != "no") parser.getAttributeValue(null, "idref")?.let { spine.add(it) }
            }; parser.next()
        }
        val chapters = spine.map { manifest[it] ?: throw IOException(t("EPUB bölümü eksik.")) }
        require(chapters.isNotEmpty() && chapters.size <= 1000) { t("EPUB bölüm sayısı desteklenmiyor.") }
        Index(title, author, chapters, if (includeCover) coverId?.let { manifest[it] }?.let { bytes(zip, it, 8 * 1024 * 1024) } else null)
    }
    fun legacyParagraphCount(file: File, index: Index, number: Int): Int = ZipFile(file).use { zip ->
        BookDocumentRepository.paragraphs(Html.fromHtml(bytes(zip, index.chapters[number]).toString(Charsets.UTF_8), Html.FROM_HTML_MODE_LEGACY).toString()).size
    }
    suspend fun chapter(book: Book, file: File, index: Index, number: Int): List<String> {
        require(number in index.chapters.indices)
        val cache = AtomicFile(File(File(directory, "${book.id}.pages"), "epub.$number.rich.v1.json"))
        val fingerprint = "${file.name}:${file.length()}:${file.lastModified()}"
        val hit = runCatching {
            if (!cache.baseFile.isFile || cache.baseFile.length() > 16 * 1024 * 1024) null else {
                val obj = JSONObject(cache.openRead().bufferedReader().use { it.readText() })
                if (obj.optString("source") != fingerprint) null else obj.getJSONArray("blocks").let { a -> (0 until a.length()).map { a.getString(it) } }
            }
        }.getOrNull()
        if (hit != null) return hit
        coroutineContext.ensureActive()
        val blocks = ZipFile(file).use { zip -> parse(zip, book, index.chapters[number]) }
        coroutineContext.ensureActive()
        runCatching {
            cache.baseFile.parentFile?.mkdirs(); val out = cache.startWrite()
            try { out.write(JSONObject().put("source", fingerprint).put("blocks", JSONArray(blocks)).toString().toByteArray()); cache.finishWrite(out) }
            catch (e: Exception) { cache.failWrite(out); throw e }
        }
        return blocks
    }
    private suspend fun parse(zip: ZipFile, book: Book, chapter: String): List<String> {
        val parser = xml(bytes(zip, chapter)); val result = mutableListOf<String>(); val text = StringBuilder()
        var heading = false; var skipped = 0; var body = false; var italic = 0; var bold = 0
        var table = false; val rows = mutableListOf<List<String>>(); val cells = mutableListOf<String>(); val cell = StringBuilder()
        fun flush() {
            val value = text.toString().trim()
            if (ParagraphKind.plain(value).isNotBlank()) result.add(ParagraphKind.withKind(value, heading))
            text.setLength(0)
            if (italic > 0) text.append(ParagraphKind.ITALIC_ON)
            if (bold > 0) text.append(BookInline.BOLD_ON)
        }
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            coroutineContext.ensureActive()
            val tag = parser.name.orEmpty().lowercase()
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (tag == "body") body = true
                    if (tag in setOf("script", "style", "head")) skipped++
                    if (body && skipped == 0) when {
                        tag == "table" -> { flush(); table = true; rows.clear() }
                        table && tag == "tr" -> cells.clear()
                        table && tag in setOf("td", "th") -> cell.setLength(0)
                        tag in setOf("p", "div", "li", "blockquote") && !table -> { flush(); heading = false }
                        tag.matches(Regex("h[1-6]")) && !table -> { flush(); heading = true }
                        tag == "br" -> if (table) cell.append('\n') else text.append('\n')
                        tag in setOf("em", "i") && !table -> { if (italic++ == 0) text.append(ParagraphKind.ITALIC_ON) }
                        tag in setOf("strong", "b") && !table -> { if (bold++ == 0) text.append(BookInline.BOLD_ON) }
                        tag in setOf("img", "image") -> {
                            val href = parser.getAttributeValue(null, "src") ?: parser.getAttributeValue("http://www.w3.org/1999/xlink", "href") ?: parser.getAttributeValue(null, "href")
                            if (href != null) {
                                flush()
                                val local = resolve(chapter, href)
                                if (local != null) {
                                    val extension = local.substringAfterLast('.', "").lowercase()
                                    if (extension in setOf("jpg", "jpeg", "png", "webp", "gif")) {
                                        val image = try { bytes(zip, local, 8 * 1024 * 1024) } catch (_: IOException) { null }
                                        if (image != null) {
                                        val id = MessageDigest.getInstance("SHA-256").digest(image).joinToString("") { "%02x".format(it) }
                                        val destination = File(File(directory, "${book.id}.pages/images"), "$id.$extension")
                                        destination.parentFile?.mkdirs()
                                        if (!destination.exists()) {
                                            val atomic = AtomicFile(destination); val out = atomic.startWrite()
                                            try { out.write(image); atomic.finishWrite(out) } catch(e: Exception) { atomic.failWrite(out); throw e }
                                        }
                                        result.add(BookRichContent.image(destination.absolutePath, parser.getAttributeValue(null, "alt").orEmpty()))
                                        } else parser.getAttributeValue(null, "alt")?.takeIf { it.isNotBlank() }?.let { result.add(ParagraphKind.withKind(it, false)) }
                                    }
                                }
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> if (body && skipped == 0) {
                    val value = parser.text.orEmpty().replace(Regex("\\s+"), " ")
                    if (table) cell.append(value) else text.append(value)
                }
                XmlPullParser.END_TAG -> {
                    if (body && skipped == 0) when {
                        table && tag in setOf("td", "th") -> cells.add(cell.toString().trim())
                        table && tag == "tr" -> if (cells.isNotEmpty()) rows.add(cells.toList())
                        tag == "table" -> { result.add(BookRichContent.table(rows.toList())); table = false }
                        tag in setOf("em", "i") && !table -> if (italic > 0 && --italic == 0) text.append(ParagraphKind.ITALIC_OFF)
                        tag in setOf("strong", "b") && !table -> if (bold > 0 && --bold == 0) text.append(BookInline.BOLD_OFF)
                        tag in setOf("p", "div", "li", "blockquote") || tag.matches(Regex("h[1-6]")) -> if (!table) { flush(); heading = false }
                    }
                    if (tag in setOf("script", "style", "head")) skipped--
                    if (tag == "body") { flush(); body = false }
                }
            }; parser.next()
        }
        return result
    }
}

internal object BookInline {
    const val BOLD_ON = '\u000e'; const val BOLD_OFF = '\u000f'
    data class Parsed(val text: String, val italics: List<IntRange>, val bold: List<IntRange>)
    fun parse(raw: String): Parsed {
        val text = StringBuilder(); val italics = mutableListOf<IntRange>(); val bold = mutableListOf<IntRange>()
        var i = -1; var b = -1
        for (c in raw) when(c) {
            ParagraphKind.ITALIC_ON -> if (i < 0) i = text.length
            ParagraphKind.ITALIC_OFF -> { if (i >= 0 && i < text.length) italics.add(i until text.length); i = -1 }
            BOLD_ON -> if (b < 0) b = text.length
            BOLD_OFF -> { if (b >= 0 && b < text.length) bold.add(b until text.length); b = -1 }
            else -> text.append(c)
        }
        if (i >= 0 && i < text.length) italics.add(i until text.length)
        if (b >= 0 && b < text.length) bold.add(b until text.length)
        return Parsed(text.toString(), italics, bold)
    }
}
internal object BookRichContent {
    private const val PREFIX = "\u000bREAL_BLOCK:"
    fun image(path: String, alt: String) = PREFIX + JSONObject().put("kind", "image").put("path", path).put("alt", alt).toString()
    fun table(rows: List<List<String>>) = PREFIX + JSONObject().put("kind", "table").put("rows", JSONArray(rows.map { JSONArray(it) })).toString()
    fun isBlock(raw: String) = raw.startsWith(PREFIX)
    fun decode(raw: String): JSONObject? = if (isBlock(raw)) runCatching { JSONObject(raw.removePrefix(PREFIX)) }.getOrNull() else null
}
