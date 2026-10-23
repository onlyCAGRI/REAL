package com.cagri.real.platform.android.account

import com.cagri.real.i18n.t
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** UID scoped device approval, in noBackupFilesDir so Android backup cannot copy it to an old device. */
object CloudBackupEpochStore {
    @Volatile private var app: Context? = null
    private val mutex = Mutex()
    fun initialize(context: Context) { app = context.applicationContext }
    private fun file(uid: String): AtomicFile {
        val root = File(checkNotNull(app) { t("Uygulama hazırlanıyor.") }.noBackupFilesDir, "cloud_backup_approvals").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(root, hash))
    }
    suspend fun headers(uid: String): Map<String, String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stored = file(uid)
            if (!stored.baseFile.exists()) emptyMap() else {
                val epoch = String(stored.readFully(), Charsets.UTF_8)
                check(epoch.matches(Regex("[a-f0-9-]{36}"))) { t("Yedekleme onayı okunamadı.") }
                mapOf("X-REAL-Cloud-Epoch" to epoch)
            }
        }
    }
    suspend fun save(uid: String, epoch: String) = withContext(Dispatchers.IO) {
        require(epoch.matches(Regex("[a-f0-9-]{36}")))
        mutex.withLock {
            val stored = file(uid); val output = stored.startWrite()
            try { output.write(epoch.toByteArray(Charsets.UTF_8)); stored.finishWrite(output) }
            catch (failure: Exception) { stored.failWrite(output); throw failure }
        }
    }
    suspend fun remove(uid: String) = withContext(Dispatchers.IO) { mutex.withLock { file(uid).delete() } }
}
