package com.cagri.real.profile

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import com.cagri.real.AvatarEditorActivity
import com.cagri.real.platform.android.auth.AndroidBackend
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Device-local profile, separated from card sync. No raw UID is used in paths. */
object ProfileAccountStore {
    private val lock = Any()
    fun currentOwner(): String? = AndroidBackend.auth.currentUser()?.id
    fun scope(owner: String?): String = if (owner == null) "guest" else MessageDigest.getInstance("SHA-256")
        .digest(owner.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun preferences(context: Context, owner: String?): SharedPreferences =
        context.applicationContext.getSharedPreferences("real_profile_${scope(owner)}", Context.MODE_PRIVATE)
    private fun directory(context: Context, owner: String?) = File(context.filesDir, "profiles/${scope(owner)}")
    fun image(context: Context, owner: String?, path: String?): File? = path?.let {
        val root = directory(context, owner).canonicalFile
        File(it).canonicalFile.takeIf { file -> file.parentFile == root && file.isFile }
    }
    private fun ensureOwner(owner: String?) = check(currentOwner() == owner) { "Hesap değişti. Profilini yeniden aç." }
    private fun migrationFile(context: Context) = AtomicFile(File(context.noBackupFilesDir, "profile_migration.json"))
    private fun readMigration(context: Context): JSONObject? {
        val file = migrationFile(context)
        return try { file.openRead().use { JSONObject(String(it.readBytes(), Charsets.UTF_8)) } }
        catch (missing: java.io.FileNotFoundException) {
            if (file.baseFile.exists()) throw missing
            null
        }
    }
    private fun writeMigration(context: Context, value: JSONObject) {
        val file = migrationFile(context)
        val stream = file.startWrite()
        try { stream.write(value.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }

    /** Called once on the existing IO startup path, not from composition.
     * A pre-scope profile has no historical owner. Bind it only to the session present
     * at upgrade; if signed out, quarantine it rather than give it to a later account.
     * Persist that decision before copying, so retries cannot adopt it into another UID.
     */
    fun migrateLegacy(context: Context, initialOwner: String?) = synchronized(lock) {
        val marker = readMigration(context) ?: JSONObject().put("version", 1)
            .put("owner", initialOwner ?: JSONObject.NULL).put("complete", false)
            .also { writeMigration(context, it) }
        if (marker.optBoolean("complete")) return@synchronized
        if (marker.isNull("owner")) {
            writeMigration(context, marker.put("complete", true))
            return@synchronized
        }
        val owner = marker.getString("owner")
        if (currentOwner() != owner) return@synchronized
        val old = context.getSharedPreferences(AvatarEditorActivity.PROFILE_PREFS, Context.MODE_PRIVATE)
        val prefs = preferences(context, owner)
        if (!prefs.contains(AvatarEditorActivity.KEY_IMAGE_PATH)) {
            val legacyRoot = File(context.filesDir, "profile").canonicalFile
            val source = old.getString(AvatarEditorActivity.KEY_IMAGE_PATH, null)?.let { File(it).canonicalFile }
                ?.takeIf { it.parentFile == legacyRoot && it.isFile }
            if (source != null) {
                val root = directory(context, owner).apply { check(isDirectory || mkdirs()) }
                // Deterministic retry target; nothing from the gallery is moved or deleted.
                val target = File(root, "legacy_${source.name}")
                source.copyTo(target, overwrite = true)
                ensureOwner(owner)
                check(prefs.edit().putString(AvatarEditorActivity.KEY_IMAGE_PATH, target.path)
                    .putString(AvatarEditorActivity.KEY_STATE_JSON, old.getString(AvatarEditorActivity.KEY_STATE_JSON, null))
                    .putString(ProfilePhotoStorage.KEY_KIND, old.getString(ProfilePhotoStorage.KEY_KIND, null))
                    .putLong(AvatarEditorActivity.KEY_REVISION, old.getLong(AvatarEditorActivity.KEY_REVISION, 0L)).commit()) {
                    "Profil geçişi kaydedilemedi."
                }
            }
        }
        writeMigration(context, marker.put("complete", true))
    }

    /** Caller decodes/resizes off the main thread. Owner is captured when editor opens. */
    fun save(context: Context, owner: String?, kind: String, suffix: String, stateJson: String? = null,
             writeImage: (File) -> Unit) = synchronized(lock) {
        ensureOwner(owner)
        val root = directory(context, owner).apply { check(isDirectory || mkdirs()) }
        val target = File(root, "image_${UUID.randomUUID()}.$suffix")
        val prefs = preferences(context, owner)
        val previous = image(context, owner, prefs.getString(AvatarEditorActivity.KEY_IMAGE_PATH, null))
        val oldPath = prefs.getString(AvatarEditorActivity.KEY_IMAGE_PATH, null)
        val oldKind = prefs.getString(ProfilePhotoStorage.KEY_KIND, null)
        val oldState = prefs.getString(AvatarEditorActivity.KEY_STATE_JSON, null)
        val oldRevision = prefs.getLong(AvatarEditorActivity.KEY_REVISION, 0L)
        try {
            writeImage(target)
            check(target.isFile && target.length() > 0) { "Profil görseli boş." }
            ensureOwner(owner)
            val editor = prefs.edit().putString(AvatarEditorActivity.KEY_IMAGE_PATH, target.path)
                .putString(ProfilePhotoStorage.KEY_KIND, kind)
                .putLong(AvatarEditorActivity.KEY_REVISION, System.currentTimeMillis())
            if (stateJson != null) editor.putString(AvatarEditorActivity.KEY_STATE_JSON, stateJson)
            if (!editor.commit()) {
                // SharedPreferences updates its memory before a failed disk commit.
                prefs.edit().putString(AvatarEditorActivity.KEY_IMAGE_PATH, oldPath)
                    .putString(ProfilePhotoStorage.KEY_KIND, oldKind)
                    .putString(AvatarEditorActivity.KEY_STATE_JSON, oldState)
                    .putLong(AvatarEditorActivity.KEY_REVISION, oldRevision).apply()
                error("Profil kaydedilemedi.")
            }
        } catch (failure: Exception) { target.delete(); throw failure }
        previous?.takeIf { it != target }?.let {
            if (!it.delete() && it.exists()) android.util.Log.w("REAL_PROFILE", "PROFILE_FILE_CLEANUP_PENDING")
        }
    }

    /** Account deletion only. Sign-out and CLOUD deletion preserve the local profile. */
    fun erase(context: Context, uid: String) = synchronized(lock) {
        check(currentOwner() != uid) { "Önce hesap kapatılmalı." }
        val root = directory(context, uid).canonicalFile
        check(root.parentFile == File(context.filesDir, "profiles").canonicalFile)
        if (root.exists()) check(root.deleteRecursively()) { "Profil dosyaları temizlenemedi." }
        check(preferences(context, uid).edit().clear().commit()) { "Profil kaydı temizlenemedi." }
        val migration = readMigration(context)
        if (migration != null && !migration.isNull("owner") && migration.optString("owner") == uid) {
            // Only the legacy directory whose owner was recorded at upgrade is deleted.
            val legacy = File(context.filesDir, "profile").canonicalFile
            check(legacy.parentFile == context.filesDir.canonicalFile)
            if (legacy.exists()) check(legacy.deleteRecursively()) { "Eski profil temizlenemedi." }
            check(context.getSharedPreferences(AvatarEditorActivity.PROFILE_PREFS, 0).edit().clear().commit())
            writeMigration(context, migration.put("complete", true).put("erased", true))
        }
    }
}
