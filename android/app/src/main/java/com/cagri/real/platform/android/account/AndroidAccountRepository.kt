package com.cagri.real.platform.android.account

import com.cagri.real.i18n.t
import android.content.Context
import com.cagri.real.network.HttpRequest
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.platform.android.sync.SyncLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

/** UID-bound API and minimal usage cache; never stores auth credentials. */
class AndroidAccountRepository(context: Context, private val uid: String) : AccountRepository {
    private val appContext = context.applicationContext
    private val prefs = context.applicationContext.getSharedPreferences(cacheName(uid), Context.MODE_PRIVATE)
    private val transport = AndroidBackend.syncTransport()
    private fun ensureAccount() = check(AndroidBackend.auth.currentUser()?.id == uid) { t("Hesap değişti.") }
    override fun cachedUsage(): CloudUsage? = runCatching { prefs.getString("usage", null)?.let { decodeUsage(JSONObject(it)) } }.getOrNull()
    override suspend fun refresh(): AccountSnapshot = withContext(Dispatchers.IO) {
        val result = call("usage", "GET")
        val usage = result.getJSONObject("usage")
        check(prefs.edit().putString("usage", usage.toString()).commit()) { t("Kullanım bilgisi kaydedilemedi.") }
        val erasure = decodeErasure(result)
        val approved = result.optBoolean("backupDeviceApproved", true)
        if (erasure != null || !approved) SyncLocal.pauseCloud(uid)
        AccountSnapshot(decodeUsage(usage), erasure, approved)
    }
    override suspend fun start(mode: ErasureMode, operationId: String): ErasureStatus = withContext(Dispatchers.IO) {
        ensureAccount()
        // Durable local pause precedes the destructive request and survives process death.
        SyncLocal.pauseCloud(uid)
        decodeErasure(call("erasure", "POST", JSONObject().put("mode", mode.name)
            .put("operationId", operationId).put("confirmation", "DELETE_${mode.name}").put("deleteOwnedBoards", true)))
            ?: error(t("Silme durumu alınamadı."))
    }
    override suspend fun advance(operationId: String): ErasureStatus = withContext(Dispatchers.IO) {
        decodeErasure(call("erasure/continue", "POST", JSONObject().put("operationId", operationId)))
            ?: error(t("Silme durumu alınamadı."))
    }
    suspend fun resumeCloudIfNoErasure() = withContext(Dispatchers.IO) {
        val result = call("erasure", "GET")
        check(decodeErasure(result) == null) { t("Silme başladı; eşitleme yeniden açılamaz.") }
        check(result.optBoolean("backupDeviceApproved", true)) { t("Bu cihaz eski yedeğe bağlı. Otomatik yükleme güvenlik için kapalı.") }
        SyncLocal.resumeCloudAfterRejectedErasure(uid)
    }
    /** Manually removes already-deleted cloud files; active files are never touched. */
    suspend fun purgeRetained() = withContext(Dispatchers.IO) {
        ensureAccount()
        repeat(20) {
            if (call("storage/purge-retained", "POST").optInt("remaining", 0) == 0) return@withContext
        }
    }
    fun hasPendingRestart(): Boolean = prefs.contains("restart_operation")
    suspend fun restartBackup(erasureOperationId: String?) = withContext(Dispatchers.IO) {
        ensureAccount()
        SyncLocal.pauseCloud(uid)
        val deletion = prefs.getString("restart_erasure", null) ?: checkNotNull(erasureOperationId)
        val operation = prefs.getString("restart_operation", null) ?: java.util.UUID.randomUUID().toString()
        check(prefs.edit().putString("restart_erasure", deletion).putString("restart_operation", operation).commit())
        val result = call("backup/restart", "POST", JSONObject().put("operationId", operation)
            .put("erasureOperationId", deletion).put("confirmation", "REUPLOAD_THIS_DEVICE_LOCAL_DATA"))
        // Server may have completed while response/local persistence failed. Keep receipt for retry.
        val epoch = result.getString("epoch")
        CloudBackupEpochStore.save(uid, epoch)
        // Cloud erasure also removes push registrations. Do not let this device's
        // old success cache skip registration when notifications are enabled again.
        val pushPrefs = appContext.getSharedPreferences("pano_push", Context.MODE_PRIVATE)
        if (pushPrefs.getString("sent", null)?.startsWith("$uid:") == true) {
            check(pushPrefs.edit().remove("sent").commit()) { t("Bildirim kaydı yenilenemedi.") }
        }
        SyncLocal.prepareRestartedBackup(uid, epoch)
        SyncLocal.resumeCloudAfterRejectedErasure(uid)
        check(prefs.edit().remove("restart_operation").remove("restart_erasure").commit())
    }
    private suspend fun call(path: String, method: String, body: JSONObject = JSONObject()): JSONObject {
        ensureAccount()
        val response = transport.execute(HttpRequest("${AndroidBackend.ORIGIN}/api/account/v1/$path", method,
            mapOf("Content-Type" to "application/json"), if (method == "GET") "" else body.toString(), 10_000, 30_000))
        ensureAccount()
        if (response.statusCode !in 200..299) {
            val code = runCatching { JSONObject(response.body).optString("error") }.getOrDefault("")
            error(when {
                code == "RECENT_LOGIN_REQUIRED" -> t("Güvenlik için hesabını yeniden doğrula.")
                response.statusCode == 401 -> t("Hesabını yeniden doğrula.")
                response.statusCode == 429 -> t("Çok fazla istek gönderildi. Biraz sonra yeniden dene.")
                response.statusCode == 503 || response.statusCode == 404 -> t("Hesap yönetimi şu an kullanılamıyor. Staging güncellemesini kontrol et.")
                else -> t("İşlem tamamlanamadı. Yeniden deneyebilirsin.")
            })
        }
        return JSONObject(response.body)
    }
    companion object {
        fun cacheName(uid: String): String = "real_account_" + MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        private fun decodeUsage(o: JSONObject) = CloudUsage(o.getLong("usedBytes"), o.getLong("activeBytes"),
            o.getLong("retainedBytes"), o.getLong("quotaBytes"), o.getInt("fileCount"), o.getLong("measuredAt"))
        private fun decodeErasure(o: JSONObject): ErasureStatus? = o.optJSONObject("erasure")?.let {
            ErasureStatus(it.getString("operationId"), ErasureMode.valueOf(it.getString("mode")), it.getString("phase"))
        }
    }
}
