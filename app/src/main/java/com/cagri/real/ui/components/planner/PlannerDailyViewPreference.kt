package com.cagri.real.ui.components.planner

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cagri.real.platform.android.sync.SyncLocal
import kotlinx.coroutines.awaitCancellation

private const val DailyViewKey = "planner_daily_view"
private const val ViewModeKey = "planner_view_mode"

/** Uses REAL's existing appearance preference file; this choice is device-local like animation settings. */
@Composable
internal fun rememberPlannerDailyViewPreference(): Pair<PlannerDailyView, (PlannerDailyView) -> Unit> {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { SyncLocal.preferences(context, "real_ui", Context.MODE_PRIVATE) }
    val lifecycle = LocalLifecycleOwner.current
    var value by remember(preferences) {
        mutableStateOf(PlannerDailyView.fromPreference(preferences.getString(DailyViewKey, null)))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == DailyViewKey) {
                value = PlannerDailyView.fromPreference(preferences.getString(DailyViewKey, null))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(preferences, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value = PlannerDailyView.fromPreference(preferences.getString(DailyViewKey, null))
            awaitCancellation()
        }
    }
    return value to { choice: PlannerDailyView ->
        preferences.edit().putString(DailyViewKey, choice.preference).apply()
        value = choice
    }
}

/** Persists the last active Planner view mode (24 Hour, Daily, Monthly) in the existing device-local UI preferences. */
@Composable
internal fun rememberPlannerViewModePreference(): Pair<PlannerViewMode, (PlannerViewMode) -> Unit> {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { SyncLocal.preferences(context, "real_ui", Context.MODE_PRIVATE) }
    val lifecycle = LocalLifecycleOwner.current
    var value by remember(preferences) {
        mutableStateOf(PlannerViewMode.fromPreference(preferences.getString(ViewModeKey, null)))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ViewModeKey) {
                value = PlannerViewMode.fromPreference(preferences.getString(ViewModeKey, null))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(preferences, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value = PlannerViewMode.fromPreference(preferences.getString(ViewModeKey, null))
            awaitCancellation()
        }
    }
    return value to { choice: PlannerViewMode ->
        preferences.edit().putString(ViewModeKey, choice.preference).apply()
        value = choice
    }
}

