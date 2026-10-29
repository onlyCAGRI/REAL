package com.cagri.real.ui.components

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cagri.real.i18n.t

/** True while the phone has a validated internet connection; follows the system's default network. */
@Composable
fun rememberOnline(): Boolean {
    val context = LocalContext.current
    var online by remember { mutableStateOf(com.cagri.real.platform.android.sync.SyncLocal.hasInternet()) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val main = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                main.post { online = ok }
            }
            override fun onLost(network: Network) { main.post { online = false } }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return online
}

/** A small crossed Wi-Fi beside the home avatar while offline; tapping it opens the NoInternetSheet (ProAccessHost, full screen). */
@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    val online = rememberOnline()
    AnimatedVisibility(visible = !online, enter = fadeIn() + scaleIn(initialScale = 0.7f), exit = fadeOut() + scaleOut(targetScale = 0.7f)) {
        Box(
            modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .border(0.75.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable { com.cagri.real.platform.android.access.ProAccess.showOffline() }
                .semantics { contentDescription = t("İnternet bağlantın yok") },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(15.dp)) {
                val u = size.width / 24f
                val ink = Color.White.copy(alpha = 0.72f)
                for (i in 0..2) {
                    val r = (4.2f + i * 4.1f) * u
                    drawArc(ink, 225f, 90f, false, Offset(12f * u - r, 19f * u - r), Size(r * 2, r * 2),
                        style = Stroke(width = 2.2f * u, cap = StrokeCap.Round))
                }
                drawCircle(ink, 1.6f * u, Offset(12f * u, 19f * u))
                drawLine(ink, Offset(4f * u, 3.5f * u), Offset(20f * u, 21f * u), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
            }
        }
    }
}
