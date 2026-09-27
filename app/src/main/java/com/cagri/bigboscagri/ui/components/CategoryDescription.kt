package com.cagri.bigboscagri.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.data.ReminderCategory

// Optional presentation metadata, separate from existing category/reminder storage.
internal fun categoryDescriptionKey(category: ReminderCategory?, custom: CustomCategory?): String? =
    custom?.let { "custom_${it.id}_${it.createdAt}" } ?: category?.let { "builtin_${it.name}" }

@Composable
internal fun rememberCategoryDescription(key: String?): State<String> {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("real_category_descriptions", Context.MODE_PRIVATE) }
    val value = remember(key) { mutableStateOf(key?.let { preferences.getString(it, "") }.orEmpty()) }
    DisposableEffect(preferences, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (key != null && (changed == key || changed == null)) value.value = preferences.getString(key, "").orEmpty()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

internal const val CATEGORY_DESCRIPTION_WORD_LIMIT = 30
private val descriptionWordPattern = Regex("\\S+")
internal fun categoryDescriptionWordCount(text: String): Int = descriptionWordPattern.findAll(text).count()
internal fun limitCategoryDescription(text: String): String {
    val overflow = descriptionWordPattern.findAll(text).drop(CATEGORY_DESCRIPTION_WORD_LIMIT).firstOrNull()
    return if (overflow == null) text else text.substring(0, overflow.range.first).trimEnd()
}
