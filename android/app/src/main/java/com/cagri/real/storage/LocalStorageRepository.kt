package com.cagri.real.storage

import android.content.Context
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

/** Device-wide app files, not a guess from cloud usage. No user-file eviction. */
data class LocalStorageUsage(val books: Long, val photos: Long, val gifs: Long, val audio: Long,
    val attachments: Long, val boardData: Long, val databases: Long, val preferences: Long,
    val cache: Long, val other: Long) {
    val total: Long get() = books + photos + gifs + audio + attachments + boardData + databases + preferences + cache + other
}
class LocalStorageRepository(context: Context) {
    private val app = context.applicationContext
    suspend fun measure(): LocalStorageUsage = withContext(Dispatchers.IO) {
        var books = 0L; var photos = 0L; var gifs = 0L; var audio = 0L; var attachments = 0L
        var boardData = 0L; var other = 0L
        regularFiles(app.filesDir).forEach { file ->
            val path = file.relativeTo(app.filesDir).invariantSeparatorsPath
            val size = file.length()
            when {
                path.startsWith("books/") -> books += size
                path.startsWith("pano/") -> boardData += size
                path.startsWith("real_voice_recordings/") -> audio += size
                path.startsWith("pano_assets/") || path.startsWith("reminder_attachments/") || path.startsWith("profile/") -> {
                    when (mediaKind(file)) {
                        "gif" -> gifs += size
                        "photo" -> photos += size
                        "audio" -> audio += size
                        else -> attachments += size
                    }
                }
                else -> other += size
            }
        }
        LocalStorageUsage(books, photos, gifs, audio, attachments, boardData,
            bytes(File(app.applicationInfo.dataDir, "databases")), bytes(File(app.applicationInfo.dataDir, "shared_prefs")),
            bytes(app.cacheDir) + (app.externalCacheDir?.let(::bytes) ?: 0L), other + bytes(app.noBackupFilesDir))
    }
    suspend fun clearImageCache() = withContext(Dispatchers.IO) { app.imageLoader.diskCache?.clear() }
    private fun bytes(root: File) = regularFiles(root).sumOf { it.length() }
    private fun regularFiles(root: File): Sequence<File> = root.walkTopDown()
        .onEnter { !Files.isSymbolicLink(it.toPath()) }.filter { !Files.isSymbolicLink(it.toPath()) && it.isFile }
    private fun mediaKind(file: File): String {
        val head = ByteArray(16)
        val length = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(0)
        fun ascii(start: Int, count: Int) = if (start + count <= length) String(head, start, count, Charsets.US_ASCII) else ""
        if (ascii(0, 3) == "GIF") return "gif"
        if (length >= 3 && head[0] == 0xff.toByte() && head[1] == 0xd8.toByte() || ascii(1, 3) == "PNG" || ascii(8, 4) == "WEBP") return "photo"
        if (file.extension.lowercase() in setOf("m4a", "mp3", "aac", "wav", "ogg") || ascii(0, 3) == "ID3" || ascii(0, 4) == "OggS" || ascii(4, 4) == "ftyp") return "audio"
        return "other"
    }
}
