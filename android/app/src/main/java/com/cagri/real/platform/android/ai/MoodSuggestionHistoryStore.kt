package com.cagri.real.platform.android.ai

import android.content.Context
import android.util.AtomicFile
import com.cagri.real.ai.MoodMusicHistory
import com.cagri.real.data.MoodSuggestion
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Local, per-account, outside backup/sync. Disk work runs on IO, not composition. */
class MoodSuggestionHistoryStore(context: Context, private val uid: String) {
    private val app = context.applicationContext
    suspend fun exclusions(): List<String> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (AndroidBackend.auth.currentUser()?.id != uid) return@synchronized emptyList()
            MoodMusicHistory.exclusions(read(file(app, uid)))
        }
    }
    suspend fun record(suggestions: List<MoodSuggestion>): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (AndroidBackend.auth.currentUser()?.id != uid) return@synchronized false
            val songs = suggestions.mapNotNull { it.music }.mapNotNull {
                if (it.artist.isBlank() || it.track.isBlank()) null else "${it.artist} ${it.track}"
            }
            if (songs.isEmpty()) return@synchronized true
            val target = file(app, uid)
            if (!target.baseFile.parentFile!!.let { it.isDirectory || it.mkdirs() }) return@synchronized false
            val merged = MoodMusicHistory.merge(read(target), songs)
            try {
                val output = target.startWrite()
                try {
                    output.write(JSONArray(merged).toString().toByteArray(Charsets.UTF_8))
                    target.finishWrite(output)
                    true
                } catch (error: IOException) {
                    target.failWrite(output)
                    false
                }
            } catch (_: IOException) { false }
        }
    }
    companion object {
        private val lock = Any()
        private fun file(context: Context, uid: String): AtomicFile {
            val hash = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return AtomicFile(File(File(context.noBackupFilesDir, "mood_music_history"), "$hash.json"))
        }
        private fun read(file: AtomicFile): List<String> {
            return try {
                val array = JSONArray(String(file.readFully(), Charsets.UTF_8))
                (0 until array.length().coerceAtMost(MoodMusicHistory.CAPACITY)).map { array.optString(it) }
            } catch (_: IOException) { emptyList() }
              catch (_: org.json.JSONException) { emptyList() }
        }
        suspend fun remove(context: Context, uid: String) = withContext(Dispatchers.IO) {
            synchronized(lock) { file(context.applicationContext, uid).delete() }
        }
    }
}
