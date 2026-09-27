package com.cagri.bigboscagri.data

import android.content.Context
import java.io.File
import java.util.UUID

object VoiceRecordingStorage {
    private const val VOICE_DIR = "real_voice_recordings"

    fun getVoiceDir(context: Context): File = File(context.filesDir, VOICE_DIR).apply { mkdirs() }

    fun createOutput(context: Context): Pair<String, File> {
        val id = UUID.randomUUID().toString()
        return id to File(getVoiceDir(context), "voice_$id.m4a")
    }

    fun isOwnedPath(context: Context, path: String): Boolean = path.isNotBlank() && runCatching {
        File(path).canonicalFile.parentFile == getVoiceDir(context).canonicalFile
    }.getOrDefault(false)

    fun deleteRecordingFile(context: Context, path: String): Boolean =
        isOwnedPath(context, path) && runCatching { File(path).delete() }.getOrDefault(false)


    fun deleteRecordingPaths(context: Context, paths: Collection<String>) {
        paths.forEach { deleteRecordingFile(context, it) }
    }

}
