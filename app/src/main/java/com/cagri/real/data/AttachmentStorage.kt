package com.cagri.real.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object AttachmentStorage {
    private const val ATTACHMENTS_DIR = "reminder_attachments"
    const val MAX_COPY_BYTES = 100L * 1024L * 1024L

    fun getAttachmentsDir(context: Context): File = File(context.filesDir, ATTACHMENTS_DIR).apply { mkdirs() }

    
    fun copyUriToAttachments(context: Context, uri: Uri): ReminderAttachment? =
        runCatching { importUri(context, uri, 0, "image/jpeg", forceImage = true) }.getOrNull()

    
    fun importUri(context: Context, uri: Uri, flags: Int, mimeHint: String? = null, forceImage: Boolean = false, maxCopyBytes: Long = MAX_COPY_BYTES, requireLocalCopy: Boolean = false): ReminderAttachment {
        require(maxCopyBytes in 1..MAX_COPY_BYTES)
        require(uri.scheme == "content" && uri.authority != "${context.packageName}.attachments") { "Dosya bağlantısı desteklenmiyor" }
        val resolver = context.contentResolver
        var name: String? = null
        var size: Long? = null
        runCatching { resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).takeIf { it >= 0 }
            }
        }
        }
        val displayName = name?.replace(Regex("[\\p{Cntrl}]"), "")?.take(250)?.takeIf { it.isNotBlank() } ?: "Dosya"
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val mime = resolver.getType(uri)?.takeIf { it != "application/octet-stream" }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: mimeHint?.takeIf { it.contains('/') && !it.contains('*') } ?: "application/octet-stream"
        val image = forceImage || mime.startsWith("image/")
        val id = UUID.randomUUID().toString()
        if (!image && !requireLocalCopy) {
            if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 && flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
                resolver.openFileDescriptor(uri, "r")?.use { } ?: error("Dosya okunamadı")
                return ReminderAttachment(id, type = AttachmentType.FILE, uri = uri.toString(), mimeType = mime,
                    displayName = displayName, sizeBytes = size)
            }
        }
        require(size == null || size!! <= maxCopyBytes) { "Kopyalanacak dosya en fazla ${maxCopyBytes / (1024 * 1024)} MB olabilir" }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: extension.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: if (image) "jpg" else "bin"
        var target = File(getAttachmentsDir(context), "att_$id.$ext")
        val part = File(target.parentFile, "${target.name}.part")
        val optimized = File(target.parentFile, "att_$id.optimized.part")
        try {
            var copied = 0L
            resolver.openInputStream(uri)?.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= maxCopyBytes) { "Kopyalanacak dosya en fazla ${maxCopyBytes / (1024 * 1024)} MB olabilir" }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            } ?: error("Dosya okunamadı")
            var resultMime = mime
            var resultName = displayName
            if (forceImage) {
                val result = com.cagri.real.storage.PhotoOptimizer.optimize(part, optimized)
                if (result != null) {
                    require(optimized.length() in 1..maxCopyBytes) { "Fotoğraf küçültülemedi." }
                    check(part.delete())
                    check(optimized.renameTo(part))
                    target = File(target.parentFile, "att_$id.${result.extension}")
                    resultMime = result.mime
                    resultName = displayName.substringBeforeLast('.', displayName) + ".${result.extension}"
                    copied = part.length()
                }
            }
            check(part.renameTo(target)) { "Dosya kaydedilemedi" }
            return ReminderAttachment(id, target.absolutePath, if (image) AttachmentType.IMAGE else AttachmentType.FILE,
                mimeType = resultMime, displayName = resultName, sizeBytes = copied)
        } catch (e: Exception) {
            part.delete(); optimized.delete(); target.delete(); throw e
        }
    }

    /** Private CameraX cache file -> the same optimized IMAGE attachment model/storage. */
    fun importCameraPhoto(context: Context, source: File): ReminderAttachment {
        val cameraRoot = File(context.cacheDir, "real_camera")
        require(isOwnedPath(cameraRoot, source.absolutePath) && source.length() in 1..MAX_COPY_BYTES)
        val id = UUID.randomUUID().toString()
        val root = getAttachmentsDir(context)
        val part = File(root, "att_$id.camera.part")
        val optimized = File(root, "att_$id.optimized.part")
        var target = File(root, "att_$id.jpg")
        try {
            source.copyTo(part, overwrite = false)
            val output = com.cagri.real.storage.PhotoOptimizer.optimize(part, optimized)
            if (output != null) {
                check(part.delete())
                check(optimized.renameTo(part))
                target = File(root, "att_$id.${output.extension}")
            }
            require(part.length() in 1..MAX_COPY_BYTES)
            check(part.renameTo(target))
            return ReminderAttachment(id, target.absolutePath, AttachmentType.IMAGE,
                mimeType = output?.mime ?: "image/jpeg", displayName = "Fotoğraf.${output?.extension ?: "jpg"}",
                sizeBytes = target.length())
        } catch (e: Exception) {
            part.delete(); optimized.delete(); target.delete(); throw e
        }
    }

    internal fun isOwnedPath(root: File, path: String): Boolean = path.isNotBlank() && runCatching {
        File(path).canonicalFile.parentFile == root.canonicalFile
    }.getOrDefault(false)

    internal fun deleteOwnedFile(root: File, path: String): Boolean =
        !com.cagri.real.platform.android.sync.SyncLocal.protects(path) && isOwnedPath(root, path) && runCatching { File(path).delete() }.getOrDefault(false)

    fun deleteAttachmentFile(context: Context, path: String): Boolean = deleteOwnedFile(getAttachmentsDir(context), path)
    fun deleteAttachmentFiles(context: Context, attachments: List<ReminderAttachment>) =
        deleteAttachmentFilePaths(context, attachments.map { it.localPath })
    fun deleteAttachmentFilePaths(context: Context, paths: Collection<String>) {
        paths.forEach { deleteAttachmentFile(context, it) }
    }
    fun cleanupOrphanFiles(context: Context, referencedPaths: Set<String>) {
        getAttachmentsDir(context).listFiles()?.filter { it.isFile && it.absolutePath !in referencedPaths }
            ?.forEach { deleteAttachmentFile(context, it.absolutePath) }
    }

    
    fun open(context: Context, item: ReminderAttachment) {
        val uri = item.uri?.let(Uri::parse) ?: run {
            require(isOwnedPath(getAttachmentsDir(context), item.localPath)) { "Dosya artık mevcut değil" }
            check(File(item.localPath).isFile) { "Dosya artık mevcut değil" }
            FileProvider.getUriForFile(context, "${context.packageName}.attachments", File(item.localPath))
        }
        openContentUri(context, uri, item.mimeType, item.displayName)
    }

    fun openContentUri(context: Context, uri: Uri, mimeType: String?, displayName: String?) {
        require(uri.scheme == "content")
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "application/octet-stream")
            clipData = ClipData.newRawUri(displayName ?: "Dosya", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
