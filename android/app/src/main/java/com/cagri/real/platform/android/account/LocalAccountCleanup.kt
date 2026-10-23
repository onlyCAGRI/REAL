package com.cagri.real.platform.android.account

import com.cagri.real.i18n.t
import android.content.Context
import com.cagri.real.platform.android.sync.SyncLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Explicit account-deletion cleanup only; never invoked by normal sign-out. */
object LocalAccountCleanup {
    private const val QUEUE = "real_account_cleanup"
    suspend fun prepare(context: Context, uid: String) = withContext(Dispatchers.IO) {
        check(AndroidBackend.auth.currentUser()?.id == uid) { t("Hesap değişti.") }
        val paths = SyncLocal.accountFilePaths(uid)
        check(context.getSharedPreferences(QUEUE, 0).edit().putString(uid,
            JSONObject().put("paths", JSONArray(paths)).toString()).commit()) { t("Yerel silme durumu kaydedilemedi.") }
    }
    suspend fun finish(context: Context, uid: String) = withContext(Dispatchers.IO) {
        check(AndroidBackend.auth.currentUser()?.id != uid) { t("Hesap hâlâ açık.") }
        val queue = context.getSharedPreferences(QUEUE, 0)
        val raw = queue.getString(uid, null) ?: return@withContext
        val paths = JSONObject(raw).getJSONArray("paths")
        SyncLocal.eraseDeletedAccount(uid)
        val roots = listOf(File(context.filesDir, "reminder_attachments").canonicalFile,
            com.cagri.real.data.VoiceRecordingStorage.getVoiceDir(context).canonicalFile)
        for (i in 0 until paths.length()) {
            val path = paths.getString(i)
            if (path.startsWith("content://")) continue // Never delete the user's original selected document.
            val file = File(path).canonicalFile
            if (file.parentFile in roots && !SyncLocal.protects(path) && file.exists())
                check(file.delete()) { t("Yerel dosya temizlenemedi.") }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        for (parent in listOf("books", "pano", "pano_assets")) {
            val root = File(context.filesDir, parent).canonicalFile
            val directory = File(root, hash).canonicalFile
            check(directory.parentFile == root)
            if (directory.exists()) check(directory.deleteRecursively()) { t("Yerel hesap dosyaları temizlenemedi.") }
        }
        for (name in listOf("real_books_$hash", "real_connections_$hash", "real_announcements_$hash", "real_category_planner_$hash", AndroidAccountRepository.cacheName(uid)))
            check(context.getSharedPreferences(name, 0).edit().clear().commit()) { t("Yerel hesap önbelleği temizlenemedi.") }
        val sharedCacheRoot = File(context.cacheDir, "shared_categories").canonicalFile
        val sharedCache = File(sharedCacheRoot, hash).canonicalFile
        check(sharedCache.parentFile == sharedCacheRoot)
        if (sharedCache.exists()) check(sharedCache.deleteRecursively()) { t("Ortak kategori önbelleği temizlenemedi.") }
        com.cagri.real.profile.ProfileAccountStore.erase(context, uid)
        CloudBackupEpochStore.remove(uid)
        com.cagri.real.platform.android.ai.AndroidAiConsent.remove(uid)
        com.cagri.real.platform.android.ai.MoodSuggestionHistoryStore.remove(context, uid)
        check(queue.edit().remove(uid).commit()) { t("Silme kaydı temizlenemedi.") }
    }
    suspend fun resume(context: Context) = withContext(Dispatchers.IO) {
        val current = AndroidBackend.auth.currentUser()?.id
        context.getSharedPreferences(QUEUE, 0).all.keys.filter { it != current }.forEach { uid -> finish(context, uid) }
    }
}
