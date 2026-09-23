package com.cagri.bigboscagri.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.app.ActivityCompat
import com.cagri.bigboscagri.MainActivity
import com.cagri.bigboscagri.R
import com.cagri.bigboscagri.data.CategoryRepository
import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.ReminderRepository
import com.cagri.bigboscagri.ui.components.CategoryIcon

object ReminderNotifications {
    const val CHANNEL_ID = "real_reminders_v4"
    // Legacy channels: "real_reminders", "real_reminders_v2", "real_reminders_v3" (retained to avoid aggressive deletion of user channel state)
    private const val PINNED_CHANNEL_ID = "important_reminders"
    const val EXTRA_REMINDER_ID = "reminder_id"

    @Volatile private var channelsCreated = false

    fun ensureChannel(context: Context) {
        if (channelsCreated) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hatırlatıcılar",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Zamanlanmış ve anlık hatırlatıcı bildirimleri."
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(NotificationChannel(
            PINNED_CHANNEL_ID, "Sabit önemli hatırlatıcılar", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Yıldızladığın aktif notlar. Uygulamadan yıldızı kaldırınca bildirim de kaldırılır."
            enableVibration(false)
            setShowBadge(false)
        })
        channelsCreated = true
    }

    fun getReminderChannel(context: Context): NotificationChannel? {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager?.getNotificationChannel(CHANNEL_ID)
    }

    private fun canNotify(context: Context, channelId: String): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(context: Context, reminder: ReminderItem, alerting: Boolean = false, categoryRepository: CategoryRepository? = null): Boolean {
        if (reminder.isCompleted) return false
        if (reminder.isChecklist && reminder.alertAt == null && !alerting) return false
        if (reminder.alertAt == null && !reminder.sendImmediateNotification && !alerting && !reminder.shouldPinNotification) return false
        val id = reminder.id
        val text = reminder.text
        val pinned = reminder.shouldPinNotification
        val channelId = if (pinned && !alerting) PINNED_CHANNEL_ID else CHANNEL_ID
        ensureChannel(context)
        if (!canNotify(context, channelId)) return false
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("bigboscagri://reminder/$id")
            putExtra(EXTRA_REMINDER_ID, id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subText = when {
            pinned -> "Önemli"
            reminder.hasRepeat -> reminder.repeatRule.displayName
            else -> null
        }

        val categoryRepo = categoryRepository ?: CategoryRepository(context)
        val metadata = resolveNotificationMetadata(
            reminder = reminder,
            categoryRepository = categoryRepo,
            fallbackTitle = context.getString(R.string.app_name)
        )

        val builder = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_r)
            .setContentTitle(metadata.title)
            .setContentText(metadata.text)
            .setContentIntent(pendingIntent)
            .setCategory(android.app.Notification.CATEGORY_REMINDER)
            .setOngoing(pinned)
            .setSubText(subText)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setOnlyAlertOnce(!alerting)
            .setWhen(if (alerting) reminder.alertAt ?: reminder.createdAt else reminder.createdAt)

        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE
            data = Uri.parse("bigboscagri://reminder/$id/complete")
            putExtra(EXTRA_REMINDER_ID, id)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode() * 31 + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_10
            data = Uri.parse("bigboscagri://reminder/$id/snooze_10")
            putExtra(EXTRA_REMINDER_ID, id)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode() * 31 + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeAction = android.app.Notification.Action.Builder(
            null,
            "Tamamla",
            completePendingIntent
        ).build()

        val snoozeAction = android.app.Notification.Action.Builder(
            null,
            "10 dk ertele",
            snoozePendingIntent
        ).build()

        builder.addAction(completeAction)
        if (alerting) {
            builder.addAction(snoozeAction)
        }

        if (!reminder.isChecklist && needsBigTextStyle(metadata.text)) {
            builder.setStyle(android.app.Notification.BigTextStyle().bigText(metadata.text))
        }

        if (metadata.accentColor != null) {
            builder.setColor(metadata.accentColor)
        }

        if (metadata.category != null) {
            val largeIcon = createCategoryNotificationLargeIcon(metadata.category)
            if (largeIcon != null) {
                builder.setLargeIcon(largeIcon)
            }
        }

        val notification = builder.build()

        return try {
            val manager = context.getSystemService(NotificationManager::class.java)
            val previous = manager.activeNotifications.firstOrNull { it.id == id.hashCode() }
            if (previous != null && previous.notification.channelId != channelId) {
                manager.cancel(id.hashCode())
            }
            manager.notify(id.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false // Permission can change after the preflight check.
        }
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
    }

    fun refreshEdited(context: Context, reminder: ReminderItem) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (reminder.isCompleted) cancel(context, reminder.id)
        else if (reminder.shouldPinNotification || manager.activeNotifications.any { it.id == reminder.id.hashCode() }) {
            show(context, reminder)
        }
    }

    fun restorePinned(context: Context, reminders: List<ReminderItem>) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val saved = reminders.filter { it.shouldPinNotification }
        val savedIds = saved.map { it.id.hashCode() }.toSet()
        val visible = manager.activeNotifications
            .filter { it.isOngoing && ownsPinnedNotification(it.notification.channelId, it.notification.category) }
            .map { it.id }
            .toSet()
        // Reconcile with saved notes if the process stopped between a save and cancellation.
        visible.filter { it !in savedIds }.forEach { manager.cancel(it) }
        if (!canNotify(context, PINNED_CHANNEL_ID)) return
        val categoryRepo = CategoryRepository(context)
        saved.filter { it.id.hashCode() !in visible }.forEach {
            show(context, it, categoryRepository = categoryRepo)
        }
    }

    internal fun ownsPinnedNotification(channelId: String?, category: String?): Boolean {
        return channelId == PINNED_CHANNEL_ID ||
            (channelId == CHANNEL_ID && category == android.app.Notification.CATEGORY_REMINDER)
    }

    fun needsBigTextStyle(text: String): Boolean = com.cagri.bigboscagri.notifications.needsBigTextStyle(text)
}

fun needsBigTextStyle(text: String): Boolean = text.length > 80 || text.contains('\n')

fun canShowImmediateNotification(reminder: ReminderItem): Boolean =
    !reminder.isCompleted &&
    (!reminder.isChecklist || reminder.alertAt != null) &&
    (reminder.alertAt != null || reminder.sendImmediateNotification)

data class ReminderNotificationMetadata(
    val title: String,
    val text: String,
    val accentColor: Int? = null,
    val category: CustomCategory? = null
)

val CATEGORY_ACCENT_COLORS_ARGB = intArrayOf(
    0xFF233F73.toInt(), // Gece Mavisi
    0xFF2C5696.toInt(), // Kobalt
    0xFF246C7E.toInt(), // Buzul Cyan
    0xFF276B53.toInt(), // Zümrüt
    0xFF3C6A48.toInt(), // Yosun Yeşili
    0xFF8B5D34.toInt(), // Amber
    0xFF8C4638.toInt(), // Terracotta
    0xFF873858.toInt(), // Mürdüm
    0xFF6B3A79.toInt(), // Erik Moru
    0xFF4C3C82.toInt()  // İris Moru
)

fun getCategoryAccentColorInt(index: Int?): Int? =
    if (index != null && index in CATEGORY_ACCENT_COLORS_ARGB.indices) CATEGORY_ACCENT_COLORS_ARGB[index] else null

fun resolveNotificationMetadata(
    reminder: ReminderItem,
    categoryRepository: CategoryRepository?,
    fallbackTitle: String = "Real"
): ReminderNotificationMetadata {
    val category = reminder.customCategoryId?.let { catId ->
        categoryRepository?.load()?.firstOrNull { it.id == catId }
    }
    return if (category != null) {
        val color = getCategoryAccentColorInt(category.accentStart)
        ReminderNotificationMetadata(
            title = category.name,
            text = reminder.text,
            accentColor = color,
            category = category
        )
    } else {
        ReminderNotificationMetadata(
            title = fallbackTitle,
            text = reminder.text,
            accentColor = null,
            category = null
        )
    }
}

fun createCategoryNotificationLargeIcon(
    category: CustomCategory,
    sizePx: Int = 128
): Bitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val startColor = getCategoryAccentColorInt(category.accentStart) ?: 0xFF233F73.toInt()
        val endColor = category.accentEnd?.let { getCategoryAccentColorInt(it) } ?: startColor

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
                startColor, endColor,
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, bgPaint)

        val glyphSize = sizePx * 0.52f
        val offset = (sizePx - glyphSize) / 2f
        canvas.save()
        canvas.translate(offset, offset)
        canvas.scale(glyphSize / 24f, glyphSize / 24f)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.9f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val icon = CategoryIcon.fromKey(category.iconKey)
        drawCategoryGlyphOnAndroidCanvas(canvas, icon, strokePaint, fillPaint)
        canvas.restore()

        bitmap
    } catch (_: Throwable) {
        null
    }
}

private fun drawCategoryGlyphOnAndroidCanvas(
    canvas: Canvas,
    icon: CategoryIcon,
    strokePaint: Paint,
    fillPaint: Paint
) {
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        canvas.drawLine(x1, y1, x2, y2, strokePaint)
    }
    when (icon) {
        CategoryIcon.WORK -> {
            canvas.drawRoundRect(3.5f, 7.5f, 20.5f, 20f, 2.5f, 2.5f, strokePaint)
            canvas.drawRoundRect(8.5f, 4f, 15.5f, 7.5f, 1.5f, 1.5f, strokePaint)
            line(3.5f, 12f, 20.5f, 12f)
            line(11f, 12f, 13f, 12f)
        }
        CategoryIcon.HOME -> {
            val roof = Path().apply {
                moveTo(3.5f, 11f); lineTo(12f, 4f); lineTo(20.5f, 11f)
            }
            canvas.drawPath(roof, strokePaint)
            val house = Path().apply {
                moveTo(5.5f, 10.5f); lineTo(5.5f, 20f); lineTo(18.5f, 20f); lineTo(18.5f, 10.5f)
            }
            canvas.drawPath(house, strokePaint)
            val door = Path().apply {
                moveTo(9.5f, 20f); lineTo(9.5f, 14.5f); lineTo(14.5f, 14.5f); lineTo(14.5f, 20f)
            }
            canvas.drawPath(door, strokePaint)
        }
        CategoryIcon.SHOPPING -> {
            canvas.drawCircle(8.5f, 19.5f, 1.3f, fillPaint)
            canvas.drawCircle(17.5f, 19.5f, 1.3f, fillPaint)
            val basket = Path().apply {
                moveTo(3.5f, 4.5f); lineTo(6.5f, 4.5f); lineTo(8.5f, 15.5f); lineTo(18.5f, 15.5f)
                lineTo(20.5f, 7.5f); lineTo(7f, 7.5f)
            }
            canvas.drawPath(basket, strokePaint)
        }
        CategoryIcon.HEALTH -> {
            val pharmacyCross = Path().apply {
                moveTo(9f, 3.5f); lineTo(15f, 3.5f); lineTo(15f, 9f); lineTo(20.5f, 9f)
                lineTo(20.5f, 15f); lineTo(15f, 15f); lineTo(15f, 20.5f); lineTo(9f, 20.5f)
                lineTo(9f, 15f); lineTo(3.5f, 15f); lineTo(3.5f, 9f); lineTo(9f, 9f); close()
            }
            canvas.drawPath(pharmacyCross, strokePaint)
        }
        CategoryIcon.SPORT -> {
            line(6.5f, 12f, 17.5f, 12f)
            line(6.5f, 8.5f, 6.5f, 15.5f)
            line(4f, 9.5f, 4f, 14.5f)
            line(17.5f, 8.5f, 17.5f, 15.5f)
            line(20f, 9.5f, 20f, 14.5f)
        }
        CategoryIcon.MONEY -> {
            line(12f, 3.5f, 12f, 20.5f)
            val money = Path().apply {
                moveTo(16.8f, 6.5f)
                cubicTo(15.4f, 5.2f, 13.8f, 4.7f, 12f, 4.7f)
                cubicTo(8.6f, 4.7f, 6.8f, 6.4f, 6.8f, 8.7f)
                cubicTo(6.8f, 11.3f, 8.9f, 12.1f, 12.2f, 12.7f)
                cubicTo(15.5f, 13.3f, 17.2f, 14.2f, 17.2f, 16.5f)
                cubicTo(17.2f, 18.9f, 15.3f, 20.1f, 12f, 20.1f)
                cubicTo(9.8f, 20.1f, 7.9f, 19.4f, 6.4f, 17.9f)
            }
            canvas.drawPath(money, strokePaint)
        }
        CategoryIcon.SCHOOL -> {
            val book = Path().apply {
                moveTo(12f, 6.5f)
                cubicTo(9.5f, 5f, 5.5f, 5f, 3.5f, 6f)
                lineTo(3.5f, 18.5f)
                cubicTo(5.5f, 17.5f, 9.5f, 17.5f, 12f, 19f)
                cubicTo(14.5f, 17.5f, 18.5f, 17.5f, 20.5f, 18.5f)
                lineTo(20.5f, 6f)
                cubicTo(18.5f, 5f, 14.5f, 5f, 12f, 6.5f)
            }
            canvas.drawPath(book, strokePaint)
            line(12f, 6.5f, 12f, 19f)
        }
        CategoryIcon.TRAVEL -> {
            canvas.drawRoundRect(5.5f, 7.5f, 18.5f, 20.5f, 2.5f, 2.5f, strokePaint)
            canvas.drawRoundRect(9.5f, 4f, 14.5f, 7.5f, 1.2f, 1.2f, strokePaint)
            line(9.5f, 7.5f, 9.5f, 20.5f)
            line(14.5f, 7.5f, 14.5f, 20.5f)
        }
        CategoryIcon.FAMILY -> {
            canvas.drawCircle(12f, 7f, 3.2f, strokePaint)
            val body = Path().apply {
                moveTo(5.5f, 19.5f)
                cubicTo(5.5f, 15f, 8.5f, 13.5f, 12f, 13.5f)
                cubicTo(15.5f, 13.5f, 18.5f, 15f, 18.5f, 19.5f)
            }
            canvas.drawPath(body, strokePaint)
        }
        CategoryIcon.PET -> {
            canvas.drawOval(8.15f, 3.6f, 11.55f, 8.6f, strokePaint)
            canvas.drawOval(12.45f, 3.6f, 15.85f, 8.6f, strokePaint)
            canvas.drawOval(3.25f, 8.1f, 6.65f, 13.1f, strokePaint)
            canvas.drawOval(17.35f, 8.1f, 20.75f, 13.1f, strokePaint)
            val pawPad = Path().apply {
                moveTo(14.7f, 13.5f)
                cubicTo(13.6f, 11.5f, 13.25f, 11f, 12f, 11f)
                cubicTo(10.7f, 11f, 10.25f, 11.75f, 9.16f, 13.75f)
                cubicTo(8.2f, 15.45f, 5.85f, 15.6f, 5.7f, 18f)
                cubicTo(5.7f, 19.2f, 6.5f, 20f, 7.5f, 20f)
                cubicTo(8.75f, 20f, 10.5f, 19f, 12f, 19f)
                cubicTo(13.5f, 19f, 15.25f, 20f, 16.5f, 20f)
                cubicTo(17.5f, 20f, 18.3f, 19.2f, 18.3f, 18f)
                cubicTo(18.15f, 15.6f, 15.65f, 15.45f, 14.7f, 13.5f)
            }
            canvas.drawPath(pawPad, strokePaint)
        }
        CategoryIcon.FOOD -> {
            val cup = Path().apply {
                moveTo(5f, 7.5f); lineTo(16f, 7.5f); lineTo(15f, 15.5f)
                cubicTo(15f, 17.5f, 6f, 17.5f, 6f, 15.5f); close()
            }
            canvas.drawPath(cup, strokePaint)
            val handle = Path().apply {
                moveTo(15.5f, 9.5f); cubicTo(18.5f, 9.5f, 18.5f, 14f, 14.5f, 14f)
            }
            canvas.drawPath(handle, strokePaint)
            line(4f, 20f, 17f, 20f)
        }
        CategoryIcon.IDEA -> {
            val bulb = Path().apply {
                moveTo(8.5f, 14.5f)
                cubicTo(6f, 12f, 6f, 7.5f, 12f, 5.5f)
                cubicTo(18f, 7.5f, 18f, 12f, 15.5f, 14.5f)
            }
            canvas.drawPath(bulb, strokePaint)
            line(9.5f, 17f, 14.5f, 17f)
            line(10.5f, 19.5f, 13.5f, 19.5f)
        }
        CategoryIcon.HEART -> {
            val heart = Path().apply {
                moveTo(12f, 19.5f)
                cubicTo(6.5f, 15f, 4f, 11f, 4f, 8f)
                cubicTo(4f, 5f, 6.5f, 4f, 9f, 4.5f)
                cubicTo(10.5f, 5.2f, 11.5f, 6.5f, 12f, 7.5f)
                cubicTo(12.5f, 6.5f, 13.5f, 5.2f, 15f, 4.5f)
                cubicTo(17.5f, 4f, 20f, 5f, 20f, 8f)
                cubicTo(20f, 11f, 17.5f, 15f, 12f, 19.5f)
                close()
            }
            canvas.drawPath(heart, strokePaint)
        }
        CategoryIcon.STAR -> {
            val star = Path().apply {
                moveTo(12f, 2.5f); lineTo(14.8f, 8.5f); lineTo(21.5f, 9.4f)
                lineTo(16.6f, 14.2f); lineTo(17.8f, 20.9f); lineTo(12f, 17.7f)
                lineTo(6.2f, 20.9f); lineTo(7.4f, 14.2f); lineTo(2.5f, 9.4f)
                lineTo(9.2f, 8.5f); close()
            }
            canvas.drawPath(star, strokePaint)
        }
        CategoryIcon.FILE -> {
            val doc = Path().apply {
                moveTo(6f, 3.5f); lineTo(14f, 3.5f); lineTo(18.5f, 8f)
                lineTo(18.5f, 20.5f); lineTo(6f, 20.5f); close()
            }
            canvas.drawPath(doc, strokePaint)
            line(14f, 3.5f, 14f, 8f)
            line(14f, 8f, 18.5f, 8f)
            line(9f, 12f, 15.5f, 12f)
            line(9f, 16f, 13.5f, 16f)
        }
        CategoryIcon.LOCATION -> {
            val pin = Path().apply {
                moveTo(12f, 21f)
                cubicTo(10.1f, 18.7f, 5.5f, 14.1f, 5.5f, 9.5f)
                cubicTo(5.5f, 5.8f, 8.4f, 3f, 12f, 3f)
                cubicTo(15.6f, 3f, 18.5f, 5.8f, 18.5f, 9.5f)
                cubicTo(18.5f, 14.1f, 13.9f, 18.7f, 12f, 21f)
                close()
            }
            canvas.drawPath(pin, strokePaint)
            canvas.drawCircle(12f, 9.5f, 2.2f, strokePaint)
        }
        CategoryIcon.BOOKMARK -> {
            val bookmark = Path().apply {
                moveTo(7f, 3.5f)
                lineTo(17f, 3.5f)
                lineTo(17f, 20.5f)
                lineTo(12f, 17f)
                lineTo(7f, 20.5f)
                close()
            }
            canvas.drawPath(bookmark, strokePaint)
        }
        CategoryIcon.GIFT -> {
            canvas.drawRoundRect(3f, 8f, 21f, 12f, 1.2f, 1.2f, strokePaint)
            val giftBody = Path().apply {
                moveTo(5f, 12f); lineTo(5f, 19f)
                cubicTo(5f, 20.1f, 5.9f, 21f, 7f, 21f)
                lineTo(17f, 21f)
                cubicTo(18.1f, 21f, 19f, 20.1f, 19f, 19f)
                lineTo(19f, 12f)
            }
            canvas.drawPath(giftBody, strokePaint)
            line(12f, 8f, 12f, 21f)
            val leftBow = Path().apply {
                moveTo(12f, 8f)
                cubicTo(10.6f, 5f, 9.1f, 3f, 7.5f, 3f)
                cubicTo(5.9f, 3f, 5f, 4.1f, 5f, 5.5f)
                cubicTo(5f, 7f, 6.1f, 8f, 7.5f, 8f)
                close()
            }
            canvas.drawPath(leftBow, strokePaint)
            val rightBow = Path().apply {
                moveTo(12f, 8f)
                cubicTo(13.4f, 5f, 14.9f, 3f, 16.5f, 3f)
                cubicTo(18.1f, 3f, 19f, 4.1f, 19f, 5.5f)
                cubicTo(19f, 7f, 17.9f, 8f, 16.5f, 8f)
                close()
            }
            canvas.drawPath(rightBow, strokePaint)
        }
    }
}
