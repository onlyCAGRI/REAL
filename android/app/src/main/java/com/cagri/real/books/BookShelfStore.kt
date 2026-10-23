package com.cagri.real.books

import com.cagri.real.i18n.t
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Private, per-account local library. Never card/outbox storage and never R2 uploads. */
class BookShelfStore(context: Context, uid: String) {
    private val key = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
    val directory = File(context.applicationContext.filesDir, "books/$key")
    private val prefs = context.applicationContext.getSharedPreferences("real_books_$key", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private fun readBooks(): List<Book> {
        val raw = prefs.getString("books", "[]") ?: "[]"
        val array = JSONArray(raw) // A corrupt library must not be silently overwritten.
        return (0 until array.length()).map { i -> decode(array.getJSONObject(i)) }
    }
    suspend fun load(): List<Book> = withContext(Dispatchers.IO) { mutex.withLock { readBooks() } }
    suspend fun add(book: Book): List<Book> = withContext(Dispatchers.IO) { mutex.withLock {
        val old = readBooks()
        if (old.any { it.id == book.id }) return@withLock old
        require(old.size < 200) { t("Kitaplık sınırına ulaşıldı (200 kitap).") }
        val updated = old + book
        checkSave(prefs.edit().putString("books", JSONArray().apply { updated.forEach { put(encode(it)) } }.toString()).commit())
        updated
    } }
    /** Upgrade a catalog-only entry after the validated full file has been committed. */
    suspend fun saveDownloaded(book: Book): List<Book> = withContext(Dispatchers.IO) { mutex.withLock {
        require(file(book)?.isFile == true) { t("Kitap dosyası bulunamadı.") }
        val old = readBooks()
        val previous = old.firstOrNull { it.id == book.id }
        require(previous != null || old.size < 200) { t("Kitaplık sınırına ulaşıldı (200 kitap).") }
        val updated = if (previous == null) old + book else old.map { if (it.id == book.id) book else it }
        checkSave(prefs.edit().putString("books", JSONArray().apply { updated.forEach { put(encode(it)) } }.toString()).commit())
        if (previous?.fileName != book.fileName) previous?.let { file(it)?.delete() }
        updated
    } }
    /** Kitabın görünen adını değiştirir (kapak, sırt ve detayda yeni ad kullanılır). */
    suspend fun rename(book: Book, title: String): List<Book> = withContext(Dispatchers.IO) { mutex.withLock {
        val clean = title.trim()
        require(clean.isNotEmpty()) { t("Kitap adı boş olamaz.") }
        val updated = readBooks().map { if (it.id == book.id) it.copy(title = clean) else it }
        checkSave(prefs.edit().putString("books", JSONArray().apply { updated.forEach { put(encode(it)) } }.toString()).commit())
        updated
    } }
    suspend fun remove(book: Book): List<Book> = withContext(Dispatchers.IO) { mutex.withLock {
        val updated = readBooks().filterNot { it.id == book.id }
        checkSave(prefs.edit()
            .remove("epub_legacy_index_${book.id}")
            .remove("epub_legacy_offset_${book.id}")
            .remove("epub_chapters_${book.id}")
            .remove("page_${book.id}")
            .remove("reader_index_${book.id}")
            .remove("reader_offset_${book.id}")
            .putString("books", JSONArray().apply { updated.forEach { put(encode(it)) } }.toString())
            .commit())
        file(book)?.delete()
        File(directory, "${book.id}.cover.png").delete()
        File(directory, "${book.id}.pages").deleteRecursively()
        updated
    } }
    private fun writeBooks(list: List<Book>) =
        checkSave(prefs.edit().putString("books", JSONArray().apply { list.forEach { put(encode(it)) } }.toString()).commit())
    private fun readSections(): List<String> = runCatching {
        val a = JSONArray(prefs.getString("sections", "[]") ?: "[]"); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())
    private fun writeSections(list: List<String>) =
        checkSave(prefs.edit().putString("sections", JSONArray(list).toString()).commit())

    /** Bölümler: kitapların [Book.category] alanı. Burada yalnız kişinin açtığı (boş da kalabilen) bölümler tutulur. */
    suspend fun sections(): List<String> = withContext(Dispatchers.IO) { mutex.withLock { readSections() } }
    suspend fun addSection(name: String): List<String> = withContext(Dispatchers.IO) { mutex.withLock {
        val clean = name.trim().take(40)
        require(clean.isNotEmpty()) { t("Bölüm adı boş olamaz.") }
        val taken = readSections() + readBooks().map { it.category }
        require(taken.none { it.equals(clean, ignoreCase = true) }) { t("Bu adla bir bölüm zaten var.") }
        require(readSections().size < 30) { t("En fazla 30 bölüm açılabilir.") }
        (readSections() + clean).also(::writeSections)
    } }
    /** Kitabı sürükleyip başka bölüme bırakmak. */
    suspend fun moveBook(bookId: String, section: String): List<Book> = withContext(Dispatchers.IO) { mutex.withLock {
        readBooks().map { if (it.id == bookId) it.copy(category = section) else it }.also(::writeBooks)
    } }
    suspend fun renameSection(old: String, new: String): Pair<List<Book>, List<String>> = withContext(Dispatchers.IO) { mutex.withLock {
        val clean = new.trim().take(40)
        require(clean.isNotEmpty()) { t("Bölüm adı boş olamaz.") }
        val taken = (readSections() + readBooks().map { it.category }).filterNot { it == old }
        require(taken.none { it.equals(clean, ignoreCase = true) }) { t("Bu adla bir bölüm zaten var.") }
        val books = readBooks().map { if (it.category == old) it.copy(category = clean) else it }.also(::writeBooks)
        val sections = readSections().let { s -> if (old in s) s.map { if (it == old) clean else it } else s + clean }.also(::writeSections)
        books to sections
    } }
    /** Bölüm silinir; içindeki kitaplar Dosyalarım'a döner (kitap silinmez). */
    suspend fun deleteSection(name: String): Pair<List<Book>, List<String>> = withContext(Dispatchers.IO) { mutex.withLock {
        val books = readBooks().map { if (it.category == name) it.copy(category = DEFAULT_SECTION) else it }.also(::writeBooks)
        books to readSections().filterNot { it == name }.also(::writeSections)
    } }
    suspend fun readerFont(): ReaderFont = withContext(Dispatchers.IO) {
        runCatching { ReaderFont.valueOf(prefs.getString("reader_font", "SANS") ?: "SANS") }.getOrDefault(ReaderFont.SANS)
    }
    suspend fun saveReaderFont(font: ReaderFont) = withContext(Dispatchers.IO) { checkSave(prefs.edit().putString("reader_font", font.name).commit()) }
    /** "Taranmış kitap" paneli bir kitap için bir kez gösterilir. */
    suspend fun scanNoticeShown(book: Book): Boolean = withContext(Dispatchers.IO) { prefs.getBoolean("scan_notice_${book.id}", false) }
    suspend fun markScanNoticeShown(book: Book) = withContext(Dispatchers.IO) { checkSave(prefs.edit().putBoolean("scan_notice_${book.id}", true).commit()) }

    fun file(book: Book): File? =book.fileName?.takeIf { it.matches(Regex("[a-zA-Z0-9_.-]+")) }?.let { File(directory, it) }
    suspend fun theme(): ReaderTheme = withContext(Dispatchers.IO) {
        runCatching { ReaderTheme.valueOf(prefs.getString("theme", "AMOLED") ?: "AMOLED") }.getOrDefault(ReaderTheme.AMOLED)
    }
    suspend fun saveTheme(theme: ReaderTheme) = withContext(Dispatchers.IO) { checkSave(prefs.edit().putString("theme", theme.name).commit()) }
    suspend fun fontSize(): Int = withContext(Dispatchers.IO) { prefs.getInt("font_sp", ReaderDefaults.FONT_SP).coerceIn(ReaderDefaults.MIN_SP, ReaderDefaults.MAX_SP) }
    suspend fun saveFontSize(sp: Int) = withContext(Dispatchers.IO) { checkSave(prefs.edit().putInt("font_sp", sp).commit()) }
    suspend fun readMode(): ReadMode = withContext(Dispatchers.IO) {
        runCatching { ReadMode.valueOf(prefs.getString("read_mode", "VERTICAL") ?: "VERTICAL") }.getOrDefault(ReadMode.VERTICAL)
    }
    suspend fun saveReadMode(mode: ReadMode) = withContext(Dispatchers.IO) { checkSave(prefs.edit().putString("read_mode", mode.name).commit()) }
    suspend fun page(book: Book): Int = withContext(Dispatchers.IO) { prefs.getInt("page_${book.id}", 0).coerceAtLeast(0) }
    suspend fun savePage(book: Book, page: Int) = withContext(Dispatchers.IO) { checkSave(prefs.edit().putInt("page_${book.id}", page).commit()) }
    suspend fun readingPosition(book: Book): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val index = prefs.getInt("reader_index_${book.id}", 0).coerceAtLeast(0)
        val offset = prefs.getInt("reader_offset_${book.id}", 0).coerceAtLeast(0)
        index to offset
    }
    suspend fun saveReadingPosition(book: Book, index: Int, offset: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (readBooks().none { it.id == book.id }) return@withLock
            checkSave(prefs.edit()
                .putInt("reader_index_${book.id}", index.coerceAtLeast(0))
                .putInt("reader_offset_${book.id}", offset.coerceAtLeast(0)).putString("last_read_id", book.id).commit())
        }
    }
    /** Yaşam ekranı için: konumu en son kaydedilen kitabın kimliği (yoksa null). */
    suspend fun lastReadId(): String? = withContext(Dispatchers.IO) { prefs.getString("last_read_id", null) }
    suspend fun epubPositionMigrated(book: Book): Boolean = withContext(Dispatchers.IO) { prefs.getBoolean("epub_chapters_${book.id}", false) }
    suspend fun migrateEpubPosition(book: Book, chapter: Int) = withContext(Dispatchers.IO) {
        checkSave(prefs.edit()
            .putInt("epub_legacy_index_${book.id}", prefs.getInt("reader_index_${book.id}", 0))
            .putInt("epub_legacy_offset_${book.id}", prefs.getInt("reader_offset_${book.id}", 0))
            .putBoolean("epub_chapters_${book.id}", true).putInt("page_${book.id}", chapter)
            .putInt("reader_index_${book.id}", chapter).putInt("reader_offset_${book.id}", 0).commit())
    }
    suspend fun savePagedPosition(book: Book, chapter: Int, offset: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // A queued final scroll save must never resurrect progress after book deletion.
            if (readBooks().none { it.id == book.id }) return@withLock
            checkSave(prefs.edit().putInt("page_${book.id}", chapter).putInt("reader_index_${book.id}", chapter)
                .putInt("reader_offset_${book.id}", offset.coerceAtLeast(0)).putString("last_read_id", book.id).commit())
        }
    }
    private fun checkSave(ok: Boolean) { if (!ok) throw IOException(t("Kitaplık kaydedilemedi.")) }
    companion object {
        const val DEFAULT_SECTION = "Dosyalarım"
        internal fun encode(b: Book) = JSONObject().put("id", b.id).put("title", b.title).put("author", b.author)
            .put("category", b.category).put("pageCount", b.pageCount).put("coverUrl", b.coverUrl)
            .put("licenseUrl", b.licenseUrl).put("rights", b.rights)
            .put("source", b.source.name).put("infoUrl", b.infoUrl).put("description", b.description)
            .put("downloadable", b.downloadable).put("accessCountry", b.accessCountry).put("fileName", b.fileName).put("format", b.format).put("contentUrl", b.contentUrl)
            .put("freeAccess", b.freeAccess)
        internal fun decode(j: JSONObject) = Book(j.getString("id"), j.getString("title"), j.optString("author"),
            category = j.optString("category", "Genel"), pageCount = j.optInt("pageCount").takeIf { it > 0 },
            coverUrl = j.nullString("coverUrl"), source = BookSource.valueOf(j.getString("source")),
            infoUrl = j.nullString("infoUrl"), description = j.nullString("description"),
            downloadable = j.optBoolean("downloadable"), accessCountry = j.nullString("accessCountry"), fileName = j.nullString("fileName"), format = j.nullString("format"), contentUrl = j.nullString("contentUrl"),
            licenseUrl = j.nullString("licenseUrl"), rights = j.nullString("rights"),
            freeAccess = j.optBoolean("freeAccess"))
        private fun JSONObject.nullString(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}
