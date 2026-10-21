package com.cagri.real.books

import android.os.Build
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Derived data only; lives in the book's existing removable .pages directory. Call on IO. */
internal class BookAnalysisCache(private val directory: File) {
    private fun fingerprint(source: File) = "${source.name}:${source.length()}:${source.lastModified()}:${Build.VERSION.SDK_INT >= 35}"
    private fun file(bookId: String, name: String) = AtomicFile(File(File(directory, "$bookId.pages"), name))
    private fun read(bookId: String, name: String, source: File, limit: Long): JSONObject? = runCatching {
        val cache = file(bookId, name)
        if (!cache.baseFile.isFile || cache.baseFile.length() > limit) return@runCatching null
        val root = JSONObject(cache.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        root.takeIf { it.optString("source") == fingerprint(source) }
    }.getOrNull()
    private fun write(bookId: String, name: String, source: File, root: JSONObject) {
        // A derived cache failure must not make an otherwise readable book unavailable.
        runCatching {
            val cache = file(bookId, name)
            cache.baseFile.parentFile?.mkdirs()
            val stream = cache.startWrite()
            try {
                stream.write(root.put("source", fingerprint(source)).toString().toByteArray(Charsets.UTF_8))
                cache.finishWrite(stream)
            } catch (failure: Exception) { cache.failWrite(stream); throw failure }
        }
    }
    fun header(bookId: String, page: Int, source: File): PdfRunningHeaders.Snapshot? = runCatching {
        val root = read(bookId, "$page.headers.v1.json", source, 256 * 1024L) ?: return@runCatching null
        val array = root.getJSONArray("marks")
        require(array.length() <= 4096 && root.getInt("page") == page)
        PdfRunningHeaders.Snapshot(page, (0 until array.length()).map { i ->
            val mark = array.getJSONObject(i)
            PdfRunningHeaders.Mark(mark.getString("signature"), mark.getInt("band"),
                mark.getDouble("y").toFloat(), mark.getDouble("center").toFloat())
        })
    }.getOrNull()
    fun saveHeader(bookId: String, source: File, snapshot: PdfRunningHeaders.Snapshot) {
        val marks = JSONArray().apply { snapshot.marks.forEach { mark -> put(JSONObject()
            .put("signature", mark.signature).put("band", mark.band).put("y", mark.y).put("center", mark.center)) } }
        write(bookId, "${snapshot.page}.headers.v1.json", source, JSONObject().put("page", snapshot.page).put("marks", marks))
    }
    fun epub(bookId: String, source: File): List<String>? = runCatching {
        val root = read(bookId, "epub.paragraphs.v1.json", source, 64 * 1024 * 1024L) ?: return@runCatching null
        val array = root.getJSONArray("paragraphs")
        (0 until array.length()).map { array.getString(it) }
    }.getOrNull()
    fun saveEpub(bookId: String, source: File, paragraphs: List<String>) {
        write(bookId, "epub.paragraphs.v1.json", source, JSONObject().put("paragraphs", JSONArray(paragraphs)))
    }
}
