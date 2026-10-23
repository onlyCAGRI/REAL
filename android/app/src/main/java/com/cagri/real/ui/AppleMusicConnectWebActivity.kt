package com.cagri.real.ui

import com.cagri.real.i18n.t
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.cagri.real.discovery.AppleMusicAccount

/**
 * Apple Music sign-in inside REAL. MusicKit JS opens Apple's "Allow" page as a popup and gets the token back from it
 * by window messaging; in Chrome that popup is a separate tab and the answer never arrives (AUTHORIZATION_ERROR).
 * Here the popup is a second WebView over the first, so the two pages stay linked. The page's real://apple-music
 * answer is caught before it leaves.
 */
class AppleMusicConnectWebActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private var popup: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(web: WebView) {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            // Apple's sign-in pages refuse some embedded browsers; present as the phone's Chrome.
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.setBackgroundColor(0xFF0B0B0D.toInt())
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = handle(request.url)
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val child = WebView(this@AppleMusicConnectWebActivity).also(::configure)
                popup?.let { root.removeView(it); it.destroy() }
                popup = child
                root.addView(child, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                (resultMsg.obj as WebView.WebViewTransport).webView = child
                resultMsg.sendToTarget()
                return true
            }
            override fun onCloseWindow(window: WebView) { closePopup() }
        }
    }

    private fun closePopup() {
        popup?.let { root.removeView(it); it.destroy() }
        popup = null
    }

    /** real://apple-music…: store the token and close; other non-web links stay inside. */
    private fun handle(url: Uri): Boolean {
        if (url.scheme == "real") {
            val ok = AppleMusicAccount.complete(this, url)
            Toast.makeText(this, if (ok) t("Apple Music bağlandı") else t("Apple Music bağlantısı tamamlanmadı"), Toast.LENGTH_SHORT).show()
            finish()
            return true
        }
        return url.scheme != "https"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = intent.dataString?.takeIf { it.startsWith("https://real.realapp.workers.dev/music/connect?") } ?: run { finish(); return }
        root = FrameLayout(this)
        setContentView(root)
        val main = WebView(this).also(::configure)
        root.addView(main, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        main.loadUrl(page)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val p = popup
                when {
                    p != null && p.canGoBack() -> p.goBack()
                    p != null -> closePopup()
                    main.canGoBack() -> main.goBack()
                    else -> finish()
                }
            }
        })
    }
}
