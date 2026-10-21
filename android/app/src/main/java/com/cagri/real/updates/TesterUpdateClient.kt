package com.cagri.real.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class TesterRelease(val versionCode: Long, val versionName: String, val sizeBytes: Long,
    val sha256: String, val notes: List<String>, val minSdk: Int) {
    companion object {
        fun parse(j: JSONObject): TesterRelease {
            val r = TesterRelease(j.getLong("versionCode"), j.getString("versionName"), j.getLong("sizeBytes"),
                j.getString("sha256"), j.getJSONArray("notes").let { a -> List(a.length()) { a.getString(it) } }, j.getInt("minSdk"))
            require(r.versionCode > 0 && r.sizeBytes in 1..314572800L && r.sha256.matches(Regex("[a-f0-9]{64}")))
            require(r.versionName.length <= 60 && r.notes.size <= 30 && r.notes.all { it.length <= 500 } && r.minSdk >= 26)
            return r
        }
    }
}

class TesterUpdateClient(private val context: Context) {
    companion object {
        private val checkMutex = kotlinx.coroutines.sync.Mutex()
        private var cachedUid: String? = null
        private var cachedAt = 0L
        private var cachedRelease: TesterRelease? = null
    }
    suspend fun cachedLatest(): TesterRelease? {
        checkMutex.lock()
        try {
            val uid = AndroidBackend.auth.currentUser()?.id ?: return null
            if (cachedUid == uid && System.currentTimeMillis() - cachedAt < 15*60*1000L) return cachedRelease
            val result = latest()
            check(AndroidBackend.auth.currentUser()?.id == uid)
            cachedUid = uid; cachedRelease = result; cachedAt = System.currentTimeMillis()
            return result
        } finally { checkMutex.unlock() }
    }
    private suspend fun connection(path: String): HttpURLConnection {
        val headers = AndroidBackend.diagnosticHeaders()
        return (URL(AndroidBackend.ORIGIN + path).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 30000
            headers.forEach { (k,v) -> setRequestProperty(k,v) }
        }
    }
    suspend fun latest(): TesterRelease? = withContext(Dispatchers.IO) {
        val c = connection("/api/updates/v1/latest")
        try {
            if (c.responseCode == 204 || c.responseCode == 403) return@withContext null
            check(c.responseCode == 200) { "Güncelleme bilgisi alınamadı." }
            val bytes = c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(2048)
                while (out.size() <= 20000) { val n = input.read(buffer); if (n < 0) break; out.write(buffer,0,n) }
                out.toByteArray()
            }
            check(bytes.size <= 20000)
            TesterRelease.parse(JSONObject(bytes.toString(Charsets.UTF_8)))
        } finally { c.disconnect() }
    }
    suspend fun download(r: TesterRelease, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir,"tester_updates").apply { mkdirs() }
        val file = File(dir,"update.apk")
        val part = File(dir,"update.partial")
        file.delete(); part.delete()
        val c = connection("/api/updates/v1/apk/${r.versionCode}/${r.sha256}")
        try {
            check(c.responseCode == 200) { "İndirme şu an kullanılamıyor." }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L; var lastPercent = -1
            c.inputStream.use { input -> part.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    total += count; check(total <= r.sizeBytes) { "Dosya boyutu uyuşmuyor." }
                    output.write(buffer,0,count); digest.update(buffer,0,count)
                    val percent = (total * 100 / r.sizeBytes).toInt()
                    if (percent != lastPercent) { lastPercent = percent; withContext(Dispatchers.Main) { progress(total.toFloat()/r.sizeBytes) } }
                }
            } }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            check(total == r.sizeBytes && hash == r.sha256) { "Dosya doğrulanamadı. Tekrar indir." }
            validateApk(part,r)
            check(part.renameTo(file)) { "Dosya kaydedilemedi." }
            file
        } finally { c.disconnect(); part.delete() }
    }
    @Suppress("DEPRECATION")
    private fun validateApk(file: File, r: TesterRelease) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = checkNotNull(pm.getPackageArchiveInfo(file.absolutePath,flags)) { "APK okunamadı." }
        val installed = pm.getPackageInfo(context.packageName,flags)
        val version = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val current = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        check(archive.packageName == context.packageName && version == r.versionCode && version > current) { "Paket veya sürüm uyuşmuyor." }
        val a = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
        val b = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        check(!a.isNullOrEmpty() && !b.isNullOrEmpty() && a.toSet() == b.toSet()) { "İmza uyuşmuyor. Mevcut uygulamanın imzasıyla derle." }
    }
    fun install(file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
