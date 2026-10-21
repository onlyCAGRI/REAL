package com.cagri.real.platform.android.connections

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import android.util.LruCache
import com.cagri.real.AvatarEditorActivity
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.profile.ProfileAccountStore
import com.cagri.real.profile.ProfilePhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Profile pictures between people (connected people and shared-category co-members only; never on public pages).
 * An avatar is always shared; a real photo only when "Fotoğrafımı kişilerimle paylaş" is on (off by default).
 */
object ProfilePictures {
    /** Per account (profile preferences): share the real photo with connected people. */
    const val KEY_SHARE_PHOTO = "share_photo_with_people"
    private const val KEY_PUSHED = "picture_pushed_state"
    private const val SIDE = 256
    private const val MAX_BYTES = 44_000

    private val pushLock = Mutex()
    private val memory = LruCache<String, Bitmap>(48)

    fun sharePhoto(context: Context): Boolean =
        ProfileAccountStore.preferences(context, ProfileAccountStore.currentOwner()).getBoolean(KEY_SHARE_PHOTO, false)

    fun setSharePhoto(context: Context, value: Boolean) {
        ProfileAccountStore.preferences(context, ProfileAccountStore.currentOwner()).edit().putBoolean(KEY_SHARE_PHOTO, value).apply()
    }

    /** Brings the server in line with this phone's picture; a no-op when nothing changed. Errors are left for next time. */
    suspend fun push(context: Context) = pushLock.withLock {
        withContext(Dispatchers.IO) {
            val user = AndroidBackend.auth.currentUser()?.takeIf { !it.isAnonymous } ?: return@withContext
            val prefs = ProfileAccountStore.preferences(context, user.id)
            val kind = prefs.getString(ProfilePhotoStorage.KEY_KIND, null)
            val file = ProfileAccountStore.image(context, user.id, prefs.getString(AvatarEditorActivity.KEY_IMAGE_PATH, null))
            val revision = prefs.getLong(AvatarEditorActivity.KEY_REVISION, 0L)
            val share = file != null && (kind == "avatar" || (kind == "photo" && prefs.getBoolean(KEY_SHARE_PHOTO, false)))
            val wanted = if (share) "$kind/$revision/${file!!.length()}" else "none"
            if (prefs.getString(KEY_PUSHED, null) == wanted) return@withContext
            runCatching {
                val api = ConnectionsApi(user.id)
                if (share) {
                    val base64 = encode(file!!) ?: return@runCatching
                    api.putPicture(kind!!, base64)
                } else api.deletePicture()
                prefs.edit().putString(KEY_PUSHED, wanted).apply()
            }
        }
    }

    /** Another person's picture, from memory, then this phone's cache, then the server (cached per version). */
    suspend fun load(context: Context, uid: String, version: Long): Bitmap? {
        val key = "$uid/$version"
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "people_pictures").apply { mkdirs() }
            val prefix = digest(uid)
            val file = File(dir, "${prefix}_$version.jpg")
            val cached = file.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }
            val bitmap = cached ?: runCatching {
                val me = AndroidBackend.auth.currentUser()?.id ?: return@runCatching null
                val (_, base64) = ConnectionsApi(me).picture(uid) ?: return@runCatching null
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                // Older versions of this person's picture go.
                dir.listFiles { f -> f.name.startsWith(prefix + "_") }?.forEach { it.delete() }
                file.writeBytes(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
            bitmap?.also { memory.put(key, it) }
        }
    }

    /** Centre-cropped 256 px JPEG on the app's dark background (avatars are transparent PNGs). */
    private fun encode(file: File): String? {
        val source = BitmapFactory.decodeFile(file.path) ?: return null
        val side = minOf(source.width, source.height)
        val crop = Rect((source.width - side) / 2, (source.height - side) / 2, (source.width + side) / 2, (source.height + side) / 2)
        val out = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(0xFF1C1D22.toInt())
            drawBitmap(source, crop, Rect(0, 0, SIDE, SIDE), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        }
        for (quality in intArrayOf(82, 70, 58, 46)) {
            val bytes = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            if (bytes.size <= MAX_BYTES) return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
        return null
    }

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(12)
        .joinToString("") { "%02x".format(it) }
}
