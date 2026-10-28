package com.cagri.real.ui.poster

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cagri.real.R
import com.cagri.real.i18n.t
import com.cagri.real.ui.components.RealToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The 8K gallery save outlives the poster screen: it runs in the app's own scope, so closing the screen does not
 * cancel it. Finished with the screen still open → the usual short toast; screen already closed → a "Poster hazır"
 * notification with the poster's thumbnail that opens it in the gallery.
 */
object MapPosterJobs {
    private const val CHANNEL_ID = "map_posters"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** How many poster screens are open (set by the screen itself). */
    @Volatile var openScreens = 0

    fun save(context: Context, place: MapPosterPlace, fileName: String, render: suspend () -> Bitmap?, onDone: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            val bitmap = render()
            val uri = bitmap?.let { MapPosterRenderer.saveToGallery(app, it, fileName) }
            val thumb = if (uri != null && openScreens == 0) bitmap?.let { thumbnail(it) } else null
            bitmap?.recycle()
            onDone()
            if (openScreens > 0) RealToast.show(app, if (uri != null) t("Galeriye kaydedildi") else t("Poster kaydedilemedi"))
            else notify(app, place, uri, thumb)
        }
    }

    private suspend fun thumbnail(bitmap: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        runCatching { Bitmap.createScaledBitmap(bitmap, 960, 1200, true) }.getOrNull()
    }

    private fun notify(context: Context, place: MapPosterPlace, uri: Uri?, thumb: Bitmap?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, t("Harita posterleri"), NotificationManager.IMPORTANCE_DEFAULT))
        val title = place.title.uppercase(place.locale)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_r)
            .setAutoCancel(true)
        if (uri == null) {
            builder.setContentTitle(t("Poster kaydedilemedi")).setContentText(title)
        } else {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/png")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.setContentTitle(t("Poster hazır")).setContentText(t("{0} · galeriye kaydedildi", title))
                .setContentIntent(PendingIntent.getActivity(context, uri.hashCode(), view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            if (thumb != null) builder.setLargeIcon(thumb).setStyle(NotificationCompat.BigPictureStyle().bigPicture(thumb).bigLargeIcon(null as Bitmap?))
        }
        runCatching { NotificationManagerCompat.from(context).notify(uri?.hashCode() ?: title.hashCode(), builder.build()) }
    }
}
