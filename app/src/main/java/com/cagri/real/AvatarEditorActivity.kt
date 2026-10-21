package com.cagri.real

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.theme.RealTheme

class AvatarEditorActivity : ComponentActivity() {
    private var profileOwner: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileOwner = com.cagri.real.profile.ProfileAccountStore.currentOwner()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        setContent {
            RealTheme {
                AvatarEditorScreen(onBack = { finish() })
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun AvatarEditorScreen(onBack: () -> Unit) {
        var humation by rememberSaveable { mutableStateOf(false) }
        if(!humation) {
            com.cagri.real.profile.ProfilePhotoChooser(owner=profileOwner,onHumation={humation=true},onBack=onBack)
            return
        }
        val context = LocalContext.current
        val prefs = remember(context, profileOwner) { com.cagri.real.profile.ProfileAccountStore.preferences(context, profileOwner) }
        val savedState = prefs.getString(KEY_STATE_JSON, "") ?: ""
        val savedStateBase64 = Base64.encodeToString(savedState.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        // The page runs edge to edge (its avatar stage goes up behind the status bar); the glass back button sits on it.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val topInset = WindowInsets.statusBars.getTop(density) / density.density
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webChromeClient = WebChromeClient()
                        // No navigation away from REAL's own page: links and redirects are refused, so the
                        // "Android" bridge is never exposed to another site.
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?) = true
                        }
                        settings.setSupportMultipleWindows(false)
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        addJavascriptInterface(AvatarBridge(), "Android")
                        loadDataWithBaseURL(
                            "https://humation.app/",
                            buildAvatarHtml(savedStateBase64, topInset),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            com.cagri.real.ui.components.RealBackButton({ humation = false },
                modifier = Modifier.statusBarsPadding().padding(start = 14.dp, top = 8.dp))
        }
    }

    private inner class AvatarBridge {
        @JavascriptInterface
        fun saveAvatar(stateJson: String, pngDataUrl: String) {
            try {
                require(pngDataUrl.length <= 3 * 1024 * 1024 && stateJson.length <= 64 * 1024) { "Avatar verisi çok büyük" }
                val base64 = pngDataUrl.substringAfter("base64,", missingDelimiterValue = "")
                require(base64.isNotBlank()) { "PNG payload is empty" }
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                require(bytes.size <= 2 * 1024 * 1024) { "Avatar boyutu desteklenmiyor" }
                require(bytes.size >= 8 && bytes.take(8) == listOf(137,80,78,71,13,10,26,10).map { it.toByte() }) { "PNG gerekli" }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth in 1..512 && bounds.outHeight in 1..512) { "Avatar boyutu desteklenmiyor" }
                com.cagri.real.profile.ProfileAccountStore.save(this@AvatarEditorActivity,
                    profileOwner, "avatar", "png", stateJson) { file -> file.writeBytes(bytes) }
                runOnUiThread {
                    com.cagri.real.ui.components.RealToast.show(this@AvatarEditorActivity, "Avatar kaydedildi")
                    finish()
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    com.cagri.real.ui.components.RealToast.show(this@AvatarEditorActivity, "Avatar kaydedilemedi")
                }
            }
        }
    }

    /** The shared avatar editor page (assets/avatar/editor.html, the same file iOS ships) with the saved state in it. */
    private fun buildAvatarHtml(savedStateBase64: String, topInsetDp: Float): String =
        assets.open("avatar/editor.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
            .replace("__SAVED_STATE__", savedStateBase64)
            .replace("__TOP_INSET__", topInsetDp.toInt().toString())

    companion object {
        const val PROFILE_PREFS = "real_profile"
        const val KEY_STATE_JSON = "avatar_state_json"
        const val KEY_IMAGE_PATH = "avatar_image_path"
        const val KEY_REVISION = "avatar_revision"
    }
}
