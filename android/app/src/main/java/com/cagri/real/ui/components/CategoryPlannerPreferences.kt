package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderCategory
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.inCategory
import com.cagri.real.platform.android.sync.SyncLocal

internal fun categoryPlannerKey(category: ReminderCategory?, custom: CustomCategory?): String? =
    custom?.let { "custom_${it.id}_${it.createdAt}" } ?: category?.let { "builtin_${it.name}" }

/** Account-scoped local presentation settings, never a duplicate category/contact database. */
internal class CategoryPlannerPreferences(context: Context, val workspace: String) {
    private val identity = workspace.removePrefix("u:")
    private val hash = java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    val prefs = context.applicationContext.getSharedPreferences("real_category_planner_$hash", Context.MODE_PRIVATE)
    // Every category is in REAL's calendar unless the user switched it off. With the old opt-in default
    // (false) cards with a date/time silently never reached the calendar or "Next 24 hours".
    // An explicit choice stored earlier, on or off, is kept as it is.
    fun visible(key: String?): Boolean = key != null && prefs.getBoolean("calendar_$key", true)
    fun setVisible(key: String, visible: Boolean) {
        check(SyncLocal.workspaceToken() == workspace) { t("Hesap değişti.") }
        if (this.visible(key) != visible) prefs.edit().putBoolean("calendar_$key", visible).apply()
    }
    fun link(key: String): String = prefs.getString("link_$key", "").orEmpty()
    fun setLink(key: String, value: String) { check(SyncLocal.workspaceToken() == workspace) { t("Hesap değişti.") }; prefs.edit().putString("link_$key", value).apply() }
}

@Composable
internal fun rememberCategoryPlannerPreferences(): Pair<CategoryPlannerPreferences, Int> {
    val context = LocalContext.current.applicationContext
    val workspace = SyncLocal.workspaceToken()
    val store = remember(context, workspace) { CategoryPlannerPreferences(context, workspace) }
    var revision by remember(store) { mutableIntStateOf(0) }
    DisposableEffect(store) {
        var seen = store.prefs.all.toMap()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            val current = store.prefs.all.toMap()
            if (current != seen) { seen = current; revision++ }
        }
        store.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return store to revision
}

internal fun validPlannerLink(value: String): String? = runCatching {
    val text = value.trim()
    val uri = java.net.URI(text)
    text.takeIf { it.length <= 2048 && uri.scheme?.lowercase() in setOf("https", "http") &&
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null && text.none(Char::isISOControl) }
}.getOrNull()
