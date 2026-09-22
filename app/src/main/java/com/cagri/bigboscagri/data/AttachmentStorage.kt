package com.cagri.bigboscagri.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object AttachmentStorage {
    private const val ATTACHMENTS_DIR = "reminder_attachments"

    fun getAttachmentsDir(context: Context): File {
        val dir = File(context.filesDir, ATTACHMENTS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun copyUriToAttachments(context: Context, uri: Uri): ReminderAttachment? {
        return try {
            val dir = getAttachmentsDir(context)
            val id = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
            val mimeType = context.contentResolver.getType(uri)
            val ext = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            val destinationFile = File(dir, "att_${id}.${ext}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            ReminderAttachment(id = id, localPath = destinationFile.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    fun deleteAttachmentFile(localPath: String): Boolean {
        return try {
            val file = File(localPath)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    fun deleteAttachmentFiles(attachments: List<ReminderAttachment>) {
        attachments.forEach { deleteAttachmentFile(it.localPath) }
    }

    fun deleteAttachmentFilePaths(paths: Collection<String>) {
        paths.forEach { deleteAttachmentFile(it) }
    }

    fun cleanupOrphanFiles(context: Context, referencedPaths: Set<String>) {
        try {
            val dir = File(context.filesDir, ATTACHMENTS_DIR)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.absolutePath !in referencedPaths) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
