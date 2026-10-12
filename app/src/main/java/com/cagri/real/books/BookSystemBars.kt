package com.cagri.real.books

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Keep system time/battery readable over the reader's glass; restore the REAL chrome on exit. */
@Composable
internal fun BookSystemBars(theme: ReaderTheme) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.bookActivity() ?: return
    val darkIcons = theme == ReaderTheme.LIGHT
    val windowColor = theme.background.toArgb()
    DisposableEffect(activity.window, view, theme, windowColor) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val window = activity.window
        @Suppress("DEPRECATION") val oldNavigationColor = window.navigationBarColor
        // Navigation bar şeffaf olunca altında ne varsa o görünür: uygulamanın içeriği alta uzanmıyorsa pencerenin
        // kendi zemini (genelde siyah) çıkar. Okuyucu açıkken pencere zeminini sayfa rengine boyuyoruz.
        val oldWindowBackground = window.decorView.background
        window.setBackgroundDrawable(ColorDrawable(windowColor))
        val oldContrast = if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else null
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false) // içerik navigation bar'ın altına kadar insin
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val oldStatus = controller.isAppearanceLightStatusBars
        val oldNavigation = controller.isAppearanceLightNavigationBars
        val wasVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.statusBars())
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkIcons
        controller.show(WindowInsetsCompat.Type.statusBars())
        onDispose {
            @Suppress("DEPRECATION")
            window.navigationBarColor = oldNavigationColor
            if (oldWindowBackground != null) window.setBackgroundDrawable(oldWindowBackground)
            if (Build.VERSION.SDK_INT >= 29 && oldContrast != null) window.isNavigationBarContrastEnforced = oldContrast
            controller.isAppearanceLightStatusBars = oldStatus
            controller.isAppearanceLightNavigationBars = oldNavigation
            if (wasVisible == false) controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }
}
private fun Context.bookActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}
