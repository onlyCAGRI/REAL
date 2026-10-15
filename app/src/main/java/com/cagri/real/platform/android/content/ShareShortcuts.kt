package com.cagri.real.platform.android.content

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.cagri.real.MainActivity
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderItem

/**
 * Direct Share: REAL categories in the top row of Android's share menu. Picking one opens the share window with that
 * category already chosen. Ayarlar → "Paylaş menüsü kısayolları" turns them on/off and picks which categories (up to 4);
 * with none picked, the most used categories are shown.
 */
object ShareShortcuts {
    const val SHARE_CATEGORY = "com.cagri.real.category.SHARE_TARGET"
    const val MAX = 4
    private const val PREFIX = "share_cat_"
    private const val PREFS = "real_share_shortcuts"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).remove("signature").apply()
    }
    /** Categories the person picked; empty = automatic. */
    fun selected(context: Context): List<Long> =
        prefs(context).getString("selected", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
    fun setSelected(context: Context, ids: List<Long>) {
        prefs(context).edit().putString("selected", ids.take(MAX).joinToString(",")).remove("signature").apply()
    }

    /** The category the share arrived through, if it came from one of REAL's shortcuts. */
    fun categoryId(intent: Intent): Long? =
        intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)?.takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)?.toLongOrNull()

    fun chosen(context: Context, categories: List<CustomCategory>, reminders: List<ReminderItem>): List<CustomCategory> {
        val usable = categories.filter { !it.isReadOnlyShared }
        val picked = selected(context).mapNotNull { id -> usable.firstOrNull { it.id == id } }
        if (picked.isNotEmpty()) return picked.take(MAX)
        val since = System.currentTimeMillis() - 60L * 86_400_000L
        val use = reminders.filter { it.updatedAt >= since }.groupingBy { it.customCategoryId }.eachCount()
        return usable.sortedWith(compareByDescending<CustomCategory> { use[it.id] ?: 0 }.thenBy { it.createdAt })
            .filter { (use[it.id] ?: 0) > 0 }.take(MAX)
    }

    /** Called when REAL opens and when the setting changes; does nothing when the result did not change. */
    fun publish(context: Context, categories: List<CustomCategory>, reminders: List<ReminderItem>) = runCatching {
        val app = context.applicationContext
        val ours = ShortcutManagerCompat.getDynamicShortcuts(app).map { it.id }.filter { it.startsWith(PREFIX) }
        val wanted = if (isEnabled(app)) chosen(app, categories, reminders)
            .take(minOf(MAX, ShortcutManagerCompat.getMaxShortcutCountPerActivity(app))) else emptyList()
        val signature = wanted.joinToString("|") { "${it.id}:${it.name}:${color(it)}" }
        if (signature == prefs(app).getString("signature", null) && ours.size == wanted.size) return@runCatching
        val stale = ours - wanted.map { PREFIX + it.id }.toSet()
        if (stale.isNotEmpty()) {
            ShortcutManagerCompat.removeDynamicShortcuts(app, stale)
            ShortcutManagerCompat.removeLongLivedShortcuts(app, stale)
        }
        wanted.forEachIndexed { rank, category ->
            ShortcutManagerCompat.pushDynamicShortcut(app, ShortcutInfoCompat.Builder(app, PREFIX + category.id)
                .setShortLabel(category.name.take(24).ifBlank { "REAL" })
                .setLongLabel(category.name.take(60).ifBlank { "REAL" })
                .setIcon(IconCompat.createWithAdaptiveBitmap(icon(category)))
                .setIntent(Intent(app, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
                .setCategories(setOf(SHARE_CATEGORY))
                .setLongLived(true)
                .setRank(rank)
                .build())
        }
        prefs(app).edit().putString("signature", signature).apply()
    }

    private fun color(category: CustomCategory): Int = category.customSolidColorArgb
        ?: com.cagri.real.ui.components.ReminderColorChoices.getOrNull(category.accentStart)?.color?.toArgb()
        ?: 0xFF3B82F6.toInt()

    /** Adaptive icon: the category colour with its first letter (the safe zone is the middle 2/3). */
    private fun icon(category: CustomCategory): Bitmap {
        val size = 216
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color(category))
        val letter = category.name.trim().firstOrNull()?.uppercase() ?: "R"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textSize = 78f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(letter, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
        return bitmap
    }
}
