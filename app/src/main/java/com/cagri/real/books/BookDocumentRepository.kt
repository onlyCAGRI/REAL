package com.cagri.real.books

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Html
import android.util.Xml
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Only explicitly selected files are copied. OCR and PDF rendering never upload documents. */
class BookDocumentRepository(private val context: Context, private val store: BookShelfStore) {
    private val gate = kotlinx.coroutines.sync.Semaphore(1)
    private val pdfText by lazy { PdfBoxText(context) }
    private val pageWork = BookPageWork()
    private val analysisCache = BookAnalysisCache(store.directory)
    private val epubChapters = EpubChapters(store.directory)
    private val epubIndexes = object : LinkedHashMap<String, EpubChapters.Index>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EpubChapters.Index>?): Boolean = size > 4
    }
    private fun epubIndex(file: File): EpubChapters.Index = synchronized(epubIndexes) {
        val key = "${file.name}:${file.length()}:${file.lastModified()}"
        epubIndexes[key] ?: epubChapters.index(file).also { epubIndexes[key] = it }
    }
    suspend fun hasLocalContent(book: Book): Boolean = withContext(Dispatchers.IO) {
        store.file(book)?.isFile == true || legacyTextCache(book)?.isFile == true
    }
    suspend fun prepareReading(original: Book): Book = withContext(Dispatchers.IO) {
        val book = restoreLegacyLocalText(original)
        if (book.format != "epub") book else {
            val file = store.file(book) ?: throw IOException("Kitap dosyası bulunamadı.")
            val index = epubIndex(file)
            if (!store.epubPositionMigrated(book)) {
                // One-time mapping preserves the former all-book paragraph position.
                val legacy = store.readingPosition(book); var remaining = legacy.first; var chapter = 0
                if (remaining > 0) while (chapter < index.chapters.lastIndex) {
                    val count = epubChapters.legacyParagraphCount(file, index, chapter)
                    if (remaining < count) break
                    remaining -= count; chapter++
                }
                store.migrateEpubPosition(book, chapter)
            }
            book.copy(pageCount = index.chapters.size)
        }
    }
    suspend fun prefetchNext(book: Book, page: Int) {
        if (book.format != "pdf" || page + 1 >= (book.pageCount ?: 0)) return
        text(book, page + 1)
    }
    // Bounded, per repository; accessed only while holding gate. No repeated OCR on recomposition.
    private val headerPages = object : LinkedHashMap<String, PdfRunningHeaders.Snapshot>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PdfRunningHeaders.Snapshot>?): Boolean = size > 24
    }
    suspend fun importFile(uri: Uri): Book = withContext(Dispatchers.IO) {
        if (com.cagri.real.updates.AppUpdateGate.blocked()) throw IOException(com.cagri.real.updates.AppUpdateGate.BLOCKED_MESSAGE)
        store.directory.mkdirs()
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Kitap"
        val mime = context.contentResolver.getType(uri).orEmpty()
        val format = when {
            mime == "application/pdf" || name.endsWith(".pdf", true) -> "pdf"
            mime == "application/epub+zip" || name.endsWith(".epub", true) -> "epub"
            mime.startsWith("image/") -> "image"
            mime.startsWith("text/") || name.endsWith(".txt", true) -> "txt"
            else -> throw IOException("PDF, EPUB, TXT veya sayfa görseli seç.")
        }
        val temp = File(store.directory, "${UUID.randomUUID()}.tmp")
        var createdFile: File? = null
        var createdCover: File? = null
        var persisted = false
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(8192); var total = 0
                while (true) { coroutineContext.ensureActive(); val n = input.read(buffer); if (n < 0) break
                    total += n; if (total > 100 * 1024 * 1024) throw IOException("Dosya en fazla 100 MB olabilir.")
                    hash.update(buffer, 0, n); output.write(buffer, 0, n) }
            } } ?: throw IOException("Dosya açılamadı.")
            val id = "local_" + hash.digest().joinToString("") { "%02x".format(it) }
            val file = File(store.directory, "$id.$format")
            var title = name.substringBeforeLast('.', name).take(400); var author = ""; var pages: Int? = null
            var cover: String? = null
            val coverFile = File(store.directory, "$id.cover.png")
            if (!coverFile.exists()) createdCover = coverFile
            when (format) {
                "pdf" -> renderer(temp).use { pdf ->
                    pages = pdf.pageCount
                    if (pdf.pageCount == 0) throw IOException("PDF boş.")
                    pdf.openPage(0).use { page ->
                        val bitmap = render(page, 480)
                        try { cover = saveCover(id, bitmap).absolutePath } finally { bitmap.recycle() }
                    }
                }
                "epub" -> ZipFile(temp).use { zip ->
                    val content = epubChapters.index(temp, includeCover = true)
                    title = content.title.ifBlank { title }; author = content.author
                    if (content.chapters.isEmpty()) throw IOException("EPUB içeriği alınamadı.")
                    content.cover?.let { cover = it.let { bytes -> File(store.directory, "$id.cover.png").apply { writeBytes(bytes) }.absolutePath } }
                }
                "image" -> { val bitmap = decodeImage(temp, 480)
                    try { cover = saveCover(id, bitmap).absolutePath } finally { bitmap.recycle() }; pages = 1 }
                "txt" -> { if (temp.length() > 12 * 1024 * 1024) throw IOException("Metin dosyası çok büyük.") }
            }
            coroutineContext.ensureActive()
            if (!file.exists()) {
                createdFile = file
                if (!temp.renameTo(file)) throw IOException("Dosya kaydedilemedi.")
            }
            val book = Book(id, title, author, category = "Dosyalarım", pageCount = pages, coverUrl = cover,
                fileName = file.name, format = format)
            // Finish the small filesystem/library commit together even if the screen closes.
            withContext(NonCancellable) { store.add(book); persisted = true }
            book
        } finally {
            temp.delete()
            if (!persisted) { createdFile?.delete(); createdCover?.delete() }
        }
    }
    // Compatibility only: previously downloaded Gutenberg text may exist solely as a local JSON cache.
    // No catalog lookup, provider request or new download is allowed here.
    private fun legacyTextCache(book: Book): File? =
        if (book.source == BookSource.GUTENBERG && book.id.matches(Regex("pg_[1-9][0-9]*")))
            File(store.directory, "${book.id}.pages/all.json") else null

    private suspend fun restoreLegacyLocalText(book: Book): Book = gate.withPermit {
        if (store.file(book)?.isFile == true) return@withPermit book
        val existing = store.load().firstOrNull { it.id == book.id }
        if (existing != null && store.file(existing)?.isFile == true) return@withPermit existing
        val cache = legacyTextCache(book)?.takeIf { it.isFile }
            ?: throw IOException("Kitap dosyası cihazda bulunamadı. Dosyadan yeniden ekleyebilirsin.")
        val text = jsonParagraphs(cache.readText()).joinToString("\n\n")
        if (text.isBlank()) throw IOException("Yerel kitap metni boş. Orijinal dosyanı yeniden ekleyebilirsin.")
        val token = UUID.randomUUID().toString()
        val temp = File(store.directory, "$token.tmp")
        val target = File(store.directory, "${book.id}-$token.txt")
        var persisted = false
        try {
            temp.writeText(text)
            coroutineContext.ensureActive()
            if (!temp.renameTo(target)) throw IOException("Yerel kitap dosyası kaydedilemedi.")
            val restored = book.copy(fileName = target.name, format = "txt")
            withContext(NonCancellable) { store.saveDownloaded(restored); persisted = true }
            return@withPermit restored
        } finally {
            temp.delete()
            if (!persisted) target.delete()
        }
    }

    suspend fun text(book: Book, pageIndex: Int, rebuildLayout: Boolean = false): List<String> = withContext(Dispatchers.IO) {
        pageWork.withPage("${book.id}:$pageIndex") { loadText(book, pageIndex, rebuildLayout) }
    }
    private suspend fun loadText(book: Book, pageIndex: Int, rebuildLayout: Boolean): List<String> {
        val file = store.file(book) ?: throw IOException("Kitap dosyası bulunamadı.")
        return when (book.format) {
            "pdf", "image" -> {
                val dir = File(store.directory, "${book.id}.pages").apply { mkdirs() }
                val cached = File(dir, "$pageIndex.v8.json")
                if (!rebuildLayout && cached.exists()) return jsonParagraphs(cached.readText())
                // v8: a copyable PDF is read from its own text layer (words, bold, italic) on every Android version.
                // That text replaces older OCR caches of the page; only pages without usable text keep them.
                val layered: List<String>? = if (book.format == "pdf") gate.withPermit { textLayer(book.id, file, pageIndex) } else null
                if (layered != null) {
                    coroutineContext.ensureActive()
                    val temp = File(dir, "$pageIndex.tmp"); temp.writeText(JSONArray(layered).toString())
                    if (!temp.renameTo(cached)) { temp.delete(); throw IOException("Sayfa metni kaydedilemedi.") }
                    return layered
                }
                if (!rebuildLayout) {
                    File(dir, "$pageIndex.v7.json").takeIf { it.exists() }?.let { return jsonParagraphs(it.readText()) }
                    // Old manual paragraph deletions have no edit marker; keep the old cache until
                    // the user explicitly chooses to rebuild this page, rather than resurrecting text.
                    (File(dir, "$pageIndex.v6.json").takeIf { it.exists() }
                        ?: File(dir, "$pageIndex.v5.json").takeIf { it.exists() }
                        ?: File(dir, "$pageIndex.v4.json").takeIf { it.exists() })?.let {
                        return jsonParagraphs(it.readText())
                    }
                }
                // Metin katmanı uygunsa native; değilse konumlu OCR. Eski konumsuz OCR yeni analize sokulmaz.
                // Sayfa görselleri metin katmanı taşımaz; yalnız onlar ve metni alınamayan PDF sayfaları OCR'lanır.
                val result: List<String> = gate.withPermit {
                    if (book.format == "pdf") renderer(file).use { pdf ->
                        require(pageIndex in 0 until pdf.pageCount)
                        val headers = headerReferences(pdf, book.id, pageIndex, file)
                        pdf.openPage(pageIndex).use { page ->
                            val bitmap = render(page, 1800)
                            try {
                                val native = if (Build.VERSION.SDK_INT >= 35)
                                    runCatching { nativeRuns(page, bitmap.width.toFloat() / page.width) }.getOrNull() else null
                                val nativeText = native?.takeIf { it.complete && usableText(it.runs.joinToString("\n") { r -> r.text }) &&
                                    !looksSpaceless(it.runs.map { r -> r.text }) }?.let {
                                    PageLayout.withCancellation(coroutineContext[kotlinx.coroutines.Job]) {
                                        PageLayout.paragraphsFromNative(bitmap, it.runs, headers)
                                    }.takeIf { p -> p.isNotEmpty() }
                                }
                                nativeText ?: recognizeLayout(bitmap, headers,
                                    native?.let { nativeChapterTitles(it.runs, bitmap.width, bitmap.height) }.orEmpty())
                            } finally { bitmap.recycle() }
                        }
                    } else {
                        val bitmap = decodeImage(file, 1800)
                        try { recognizeLayout(bitmap) } finally { bitmap.recycle() }
                    }
                }
                coroutineContext.ensureActive()
                if (result.isNotEmpty()) {
                    val temp = File(dir, "$pageIndex.tmp"); temp.writeText(JSONArray(result).toString())
                    if (!temp.renameTo(cached)) { temp.delete(); throw IOException("Sayfa metni kaydedilemedi.") }
                    File(dir, "$pageIndex.v3.json").delete()   // v4 is retained as a pre-rebuild backup.
                    File(dir, "$pageIndex.v2.json").delete()
                    File(dir, "$pageIndex.json").delete()
                }
                result
            }
            "epub" -> epubChapters.chapter(book, file, epubIndex(file), pageIndex)
            else -> paragraphs(file.readText())
        }
    }
    /** OCR / PDF metninden istenmeyen paragrafı kalıcı siler (yalnız uygulamanın metin önbelleği; PDF dosyası değişmez). */
    suspend fun deleteParagraph(book: Book, pageIndex: Int, paragraphIndex: Int) = withContext(Dispatchers.IO) {
        pageWork.withPage("${book.id}:$pageIndex") {
        val dir = File(store.directory, "${book.id}.pages")
        val cached = File(dir, "$pageIndex.v8.json")
        val source = cached.takeIf { it.exists() } ?: File(dir, "$pageIndex.v7.json").takeIf { it.exists() }
            ?: File(dir, "$pageIndex.v6.json").takeIf { it.exists() }
            ?: File(dir, "$pageIndex.v5.json").takeIf { it.exists() }
            ?: File(dir, "$pageIndex.v4.json").takeIf { it.exists() }
            ?: throw IOException("Sayfa metni bulunamadı.")
        val list = jsonParagraphs(source.readText()).toMutableList()
        if (paragraphIndex !in list.indices) return@withPage
        list.removeAt(paragraphIndex)
        val temp = File(dir, "$pageIndex.tmp"); temp.writeText(JSONArray(list).toString())
        if (!temp.renameTo(cached)) { temp.delete(); throw IOException("Değişiklik kaydedilemedi.") }
        }
    }
    /** Paragrafı başlık / gövde olarak işaretler (kalıcı; metin değişmez). */
    suspend fun setHeading(book: Book, pageIndex: Int, paragraphIndex: Int, heading: Boolean) = withContext(Dispatchers.IO) {
        pageWork.withPage("${book.id}:$pageIndex") {
        val dir = File(store.directory, "${book.id}.pages")
        val cached = File(dir, "$pageIndex.v8.json")
        val source = cached.takeIf { it.exists() } ?: File(dir, "$pageIndex.v7.json").takeIf { it.exists() }
            ?: File(dir, "$pageIndex.v6.json").takeIf { it.exists() }
            ?: File(dir, "$pageIndex.v5.json").takeIf { it.exists() }
            ?: File(dir, "$pageIndex.v4.json").takeIf { it.exists() }
            ?: throw IOException("Sayfa metni bulunamadı.")
        val list = jsonParagraphs(source.readText()).toMutableList()
        if (paragraphIndex !in list.indices) return@withPage
        list[paragraphIndex] = ParagraphKind.withKind(list[paragraphIndex], heading)
        val temp = File(dir, "$pageIndex.tmp"); temp.writeText(JSONArray(list).toString())
        if (!temp.renameTo(cached)) { temp.delete(); throw IOException("Değişiklik kaydedilemedi.") }
        }
    }
    suspend fun original(book: Book, pageIndex: Int): Bitmap = withContext(Dispatchers.IO) {
        val file = store.file(book) ?: throw IOException("Dosya yok.")
        if (book.format == "pdf") renderer(file).use { pdf -> pdf.openPage(pageIndex).use { render(it, 1400) } }
        else decodeImage(file, 1400)
    }
    /**
     * Paragraphs from the PDF's text layer, or null when the page has none worth reading (scans, broken encodings,
     * missing word spaces): those go to OCR. The page bitmap is only used for page geometry.
     */
    private suspend fun textLayer(bookId: String, file: File, pageIndex: Int): List<String>? {
        val page = pdfText.page(file, pageIndex, 1800) ?: return null
        val runs = page.lines
        val texts = runs.map { it.text }
        if (!usableText(texts.joinToString(" ")) || looksSpaceless(texts)) return null
        // Running headers / page numbers are recognised against the neighbouring pages' own text layer (no OCR).
        val headers = listOf(pageIndex - 2, pageIndex - 1, pageIndex + 1, pageIndex + 2).mapNotNull { index ->
            coroutineContext.ensureActive()
            val key = "$bookId:L:$index:${file.length()}:${file.lastModified()}"
            headerPages[key] ?: pdfText.page(file, index, 1800)?.let { other ->
                PageLayout.headerSnapshot(index, other.lines, other.width, other.height).also { headerPages[key] = it }
            }
        }
        return renderer(file).use { pdf ->
            if (pageIndex !in 0 until pdf.pageCount) return@use null
            pdf.openPage(pageIndex).use { p ->
                val bitmap = render(p, 1800)
                try {
                    PageLayout.withCancellation(coroutineContext[kotlinx.coroutines.Job]) {
                        PageLayout.paragraphsFromNative(bitmap, runs, headers)
                    }.takeIf { it.isNotEmpty() }
                } finally { bitmap.recycle() }
            }
        }
    }

    private data class NativePage(val runs: List<PageLayout.NativeRun>, val complete: Boolean)

    @RequiresApi(35)
    private fun nativeRuns(page: PdfRenderer.Page, scale: Float): NativePage {
        val runs = ArrayList<PageLayout.NativeRun>()
        var complete = true
        for (content in page.textContents) {
            val text = normalizeSpaces(fixLigatures(content.text))
            if (text.isBlank()) continue
            val bounds = content.bounds.filter { !it.isEmpty }
            val lines = PdfTextLines.split(text, bounds.size)
            if (lines == null) { complete = false; continue }
            lines.forEachIndexed { i, value ->
                val bound = bounds[i]
                runs.add(PageLayout.NativeRun(value, android.graphics.Rect(
                    (bound.left * scale).toInt(), (bound.top * scale).toInt(),
                    (bound.right * scale).toInt(), (bound.bottom * scale).toInt())))
            }
        }
        return NativePage(runs, complete)
    }

    /** Tiny native chapter titles sometimes disappear from OCR; add only isolated, centered titles. */
    private fun nativeChapterTitles(runs: List<PageLayout.NativeRun>, width: Int, height: Int): List<PageLayout.NativeRun> {
        val lines = PageLayout.normalizedRuns(runs)
        return lines.filter { line ->
            val t = line.text.trim()
            val letters = t.filter { it.isLetter() }
            t.length in 1..24 && t.split(Regex("\\s+")).size <= 3 &&
                (letters.length >= 2 && letters.all { it.isUpperCase() } || t.all { it.isDigit() }) &&
                line.box.top > height * 0.105f && line.box.bottom < height * 0.9f &&
                kotlin.math.abs(line.box.exactCenterX() - width / 2f) < width * 0.05f &&
                line.box.width() < width * 0.35f && lines.none { other -> other !== line &&
                    other.box.top < line.box.bottom + line.box.height() &&
                    other.box.bottom > line.box.top - line.box.height() }
        }
    }

    /** Four nearby pages cover alternating left/right running headers. Never open two PDF pages at once. */
    private suspend fun headerReferences(pdf: PdfRenderer, bookId: String, pageIndex: Int, source: File): List<PdfRunningHeaders.Snapshot> {
        val references = ArrayList<PdfRunningHeaders.Snapshot>()
        for (index in listOf(pageIndex - 2, pageIndex - 1, pageIndex + 1, pageIndex + 2).filter { it in 0 until pdf.pageCount }) {
            coroutineContext.ensureActive()
            val key = "$bookId:$index:${source.length()}:${source.lastModified()}"
            val snapshot = headerPages[key] ?: analysisCache.header(bookId, index, source)?.also { headerPages[key] = it } ?: try {
                pdf.openPage(index).use { page ->
                    val native = if (Build.VERSION.SDK_INT >= 35)
                        runCatching { nativeRuns(page, 1f) }.getOrNull() else null
                    if (native != null && native.complete && usableText(native.runs.joinToString(" ") { it.text })) {
                        PageLayout.headerSnapshot(index, native.runs, page.width, page.height)
                    } else {
                        val bitmap = render(page, 1200)
                        try { PageLayout.headerSnapshot(index, PageLayout.ocrRuns(recognizeText(bitmap)), bitmap.width, bitmap.height) }
                        finally { bitmap.recycle() }
                    }
                }.also {
                    coroutineContext.ensureActive()
                    headerPages[key] = it
                    analysisCache.saveHeader(bookId, source, it)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
              catch (_: Exception) { null } // No evidence means keep the current page's text.
            if (snapshot != null) references.add(snapshot)
        }
        return references
    }

    private suspend fun recognizeLayout(bitmap: Bitmap, headers: List<PdfRunningHeaders.Snapshot> = emptyList(),
                                        preserved: List<PageLayout.NativeRun> = emptyList()): List<String> {
        val text = recognizeText(bitmap)
        coroutineContext.ensureActive()
        return try { PageLayout.withCancellation(coroutineContext[kotlinx.coroutines.Job]) {
            PageLayout.paragraphs(bitmap, text, headers, preserved)
        } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            // Retain short native headings even when OCR geometry cannot be assembled.
            val raw = paragraphs(text.text)
            val missing = preserved.map { it.text }.filter { title -> raw.none { it.contains(title) } }
            missing.map { ParagraphKind.withKind(it, true) } + raw
        }
    }

    private suspend fun recognizeText(bitmap: Bitmap): com.google.mlkit.vision.text.Text {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text: com.google.mlkit.vision.text.Text
        try {
            // Wait for ML Kit completion before recycling its bitmap even after screen cancellation.
            text = suspendCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
                    .addOnCanceledListener { continuation.resumeWithException(kotlinx.coroutines.CancellationException("OCR iptal edildi.")) }
            }
        } finally { recognizer.close() }
        return text
    }
    private fun renderer(file: File): PdfRenderer {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try { return PdfRenderer(descriptor) } catch (e: Exception) { descriptor.close(); throw e }
    }
    private fun render(page: PdfRenderer.Page, longest: Int): Bitmap {
        val ratio = longest.toFloat() / maxOf(page.width, page.height)
        val bitmap = Bitmap.createBitmap((page.width * ratio).toInt().coerceAtLeast(1), (page.height * ratio).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        try { page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
        catch (e: Exception) { bitmap.recycle(); throw e }
        return bitmap
    }
    private fun decodeImage(file: File, longest: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > longest) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw IOException("Görsel okunamadı.")
    }
    private fun saveCover(id: String, bitmap: Bitmap) = File(store.directory, "$id.cover.png").apply {
        outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Kapak kaydedilemedi.") }
    }
    companion object {
        private const val MIN_NATIVE_LETTERS = 12
        private val SoftHyphenBreak = Regex("(?<=\\p{L})-[ \\t]*\\n[ \\t]*(?=\\p{Ll})")
        /** "kitap-\nçı" → "kitapçı" (satır sonu tireleri). */
        internal fun dehyphenate(raw: String): String = raw.replace(SoftHyphenBreak, "")
        /** Ligatures and odd spaces fixed, as for the platform text layer. */
        internal fun cleanNativeText(raw: String): String = normalizeSpaces(fixLigatures(raw))
        private fun fixLigatures(raw: String): String = raw
            .replace("\uFB00", "ff").replace("\uFB01", "fi").replace("\uFB02", "fl")
            .replace("\uFB03", "ffi").replace("\uFB04", "ffl").replace("\uFB05", "st").replace("\uFB06", "st")
        private val UnicodeSpaces = Regex("[\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000]")
        /** Sert/ince boşlukları (NBSP vb.) normal boşluğa çevirir: satır kaydırma ve kelime ayırma tutarlı olsun. */
        internal fun normalizeSpaces(raw: String): String = raw.replace(UnicodeSpaces, " ")
        private val SpaceRun = Regex("\\s+")
        /**
         * Metin katmanında kelime boşlukları eksik mi? Boşluksuz 40+ harflik bir parça ya da anormal uzun kelime
         * ortalaması, boşlukların harf olarak yazılmadığını gösterir (doğal metinde olmaz).
         */
        internal fun looksSpaceless(texts: List<String>): Boolean {
            if (texts.any { t -> val s = t.trim(); s.count { it.isLetter() } >= 40 && s.none { it.isWhitespace() } }) return true
            val tokens = texts.flatMap { it.split(SpaceRun) }.map { t -> t.count { it.isLetter() } }.filter { it > 0 }
            if (tokens.size < 5) return false
            val avg = tokens.sum().toFloat() / tokens.size
            val longShare = tokens.count { it >= 25 }.toFloat() / tokens.size
            return avg > 13f || longShare > 0.12f
        }
        /** Metin gerçekten okunabilir mi? Çok kısa ya da kodlaması bozuk (�, özel alan karakterleri) metin OCR'a bırakılır. */
        internal fun usableText(raw: String): Boolean {
            val letters = raw.count { it.isLetter() }
            if (letters < MIN_NATIVE_LETTERS) return false
            val visible = raw.count { !it.isWhitespace() }.coerceAtLeast(1)
            val broken = raw.count { it == '\uFFFD' || it in '\uE000'..'\uF8FF' || (it.isISOControl() && !it.isWhitespace()) }
            return broken.toFloat() / visible <= 0.15f
        }
        internal fun paragraphs(raw: String): List<String> = raw.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
            .map { it.replace(Regex("[ \\t]*\n[ \\t]*"), " ").trim() }.filter { it.isNotEmpty() }
        private fun jsonParagraphs(raw: String): List<String> = JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } }
    }
}
