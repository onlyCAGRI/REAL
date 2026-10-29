package com.cagri.real.ui

import com.cagri.real.i18n.t
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.cagri.real.startup.StartupIssue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cagri.real.ui.components.LocalLiquidReduceMotion

/** Keeps the skeleton above the ready screen while a soft edge reveals it left-to-right. */
@Composable
fun StartupSkeletonReveal(readyToReveal: Boolean, categoryCount: Int, reduceMotion: Boolean) {
    val wipe = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(readyToReveal, reduceMotion) {
        if (readyToReveal && !finished) {
            if (reduceMotion) wipe.snapTo(1f)
            else wipe.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
            finished = true
        }
    }
    if (!finished) {
        Box(Modifier.fillMaxSize()
            .pointerInput(Unit) {
                // Do not let taps reach home controls while they are still covered.
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val progress = wipe.value
                if (progress > 0f) {
                    val feather = size.width * 0.18f
                    val edge = -feather + progress * (size.width + feather * 2f)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            start = Offset(edge - feather, 0f),
                            end = Offset(edge + feather, 0f)
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
            }) {
            StartupLoadingScreen(false, {}, {}, categoryCount)
        }
    }
}

/** Only used when there is no usable local content in the resolved workspace. */
@Composable
fun StartupLoadingScreen(
    offline: Boolean, onRetry: () -> Unit, onUseLocal: () -> Unit, categoryCount: Int = 4,
    issue: StartupIssue = StartupIssue.UNKNOWN, issueDetail: String? = null, retrying: Boolean = false
) {
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        if (offline) {
            StartupIssueScreen(issue, issueDetail, retrying, onRetry, onUseLocal)
        } else {
            StartupCardSkeleton(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
                categoryCount
            )
        }
    }
}

/** What went wrong, in words, with one clear next step. No network: it retries by itself once the phone is online. */
@Composable
private fun StartupIssueScreen(
    issue: StartupIssue, detail: String?, retrying: Boolean, onRetry: () -> Unit, onUseLocal: () -> Unit
) {
    val (title, body) = when (issue) {
        StartupIssue.NO_NETWORK -> t("İnternet bağlantısı yok") to
            t("Kartların hesabında duruyor; yüklemek için telefonun internete bağlanması gerekiyor.")
        StartupIssue.TIMEOUT -> t("Bağlantı çok yavaş") to
            t("Sunucuya ulaştık ama cevap zamanında gelmedi. Daha güçlü bir ağda yeniden deneyebilirsin.")
        StartupIssue.SECURE_CONNECTION -> t("Güvenli bağlantı kurulamadı") to
            t("Telefonun tarih ve saati yanlışsa bu olur. Ayarlar'dan otomatik tarih ve saati açıp yeniden dene.")
        StartupIssue.SESSION -> t("Oturumun yenilenmeli") to
            t("Hesabına yeniden giriş yapman gerekiyor. Şimdilik internetsiz devam edip Ayarlar'dan tekrar giriş yapabilirsin.")
        StartupIssue.SERVER -> t("REAL şu an cevap vermiyor") to
            t("Sorun bizde, senin bağlantında değil. Birkaç dakika sonra yeniden dene.")
        StartupIssue.UNKNOWN -> t("Kartlar yüklenemedi") to
            t("Beklenmedik bir sorun oldu. Yeniden deneyebilir ya da şimdilik internetsiz devam edebilirsin.")
    }
    val noNetwork = issue == StartupIssue.NO_NETWORK
    val context = LocalContext.current
    val currentRetry by rememberUpdatedState(onRetry)
    val currentRetrying by rememberUpdatedState(retrying)
    if (noNetwork) DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val main = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                    main.post { if (!currentRetrying) currentRetry() }
            }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    val dim = Color(0xFFA0A0A8)
    Box(Modifier.fillMaxSize()
        .drawWithCache {
            val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(size.width / 2f, size.height * 0.36f), radius = size.width * 0.75f)
            onDrawBehind { drawRect(glow) }
        }
        .statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
        Column(Modifier.align(Alignment.Center).padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(76.dp).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .background(Color.White.copy(alpha = 0.06f), CircleShape),
                contentAlignment = Alignment.Center) {
                StartupIssueGlyph(issue, Modifier.size(32.dp))
            }
            Spacer(Modifier.height(26.dp))
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(body, color = dim, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            if (noNetwork) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val pulse by rememberInfiniteTransition(label = "waitNetwork")
                        .animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "waitNetworkDot")
                    Box(Modifier.size(7.dp).graphicsLayer { alpha = pulse }.background(Color(0xFF7FB2FF), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(t("İnternet gelince kendiliğinden denenecek"), color = dim, fontSize = 13.sp)
                }
            }
            // The technical line only where it helps a bug report (not for no network, slow, or the clock).
            if (!detail.isNullOrBlank() && issue in setOf(StartupIssue.SERVER, StartupIssue.SESSION, StartupIssue.UNKNOWN)) {
                Spacer(Modifier.height(14.dp))
                Text(detail, color = Color.White.copy(alpha = 0.32f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = onRetry, enabled = !retrying,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.85f), disabledContentColor = Color.Black)) {
                if (retrying) {
                    com.cagri.real.ui.components.RealSpinner(Modifier.size(18.dp), color = Color.Black)
                    Spacer(Modifier.width(10.dp))
                    Text(t("Deneniyor…"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                } else Text(t("Yeniden dene"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onUseLocal, enabled = !retrying) {
                Text(t("Şimdilik internetsiz devam et"), color = Color.White.copy(alpha = 0.78f), fontSize = 15.sp)
            }
            Text(t("Kartların bağlantı gelince yüklenir."), color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp)
        }
    }
}

/** Line glyphs drawn here (the app only has material-icons-core): wifi with a slash, clock, lock, person, cloud. */
@Composable
private fun StartupIssueGlyph(issue: StartupIssue, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val line = w * 0.075f
        val stroke = Stroke(width = line, cap = StrokeCap.Round)
        val c = Color.White
        when (issue) {
            StartupIssue.NO_NETWORK -> {
                for (i in 0..2) {
                    val r = w * (0.18f + i * 0.17f)
                    drawArc(c, 225f, 90f, false, Offset(w / 2 - r, w * 0.78f - r), Size(r * 2, r * 2), style = stroke)
                }
                drawCircle(c, w * 0.055f, Offset(w / 2, w * 0.78f))
                drawLine(c, Offset(w * 0.14f, w * 0.12f), Offset(w * 0.86f, w * 0.88f), line, StrokeCap.Round)
            }
            StartupIssue.TIMEOUT -> {
                drawCircle(c, w * 0.42f, center, style = stroke)
                drawLine(c, center, Offset(w / 2, w * 0.24f), line, StrokeCap.Round)
                drawLine(c, center, Offset(w * 0.68f, w * 0.62f), line, StrokeCap.Round)
            }
            StartupIssue.SECURE_CONNECTION -> {
                drawRoundRect(c, Offset(w * 0.18f, w * 0.44f), Size(w * 0.64f, w * 0.48f), CornerRadius(w * 0.08f), style = stroke)
                drawArc(c, 180f, 180f, false, Offset(w * 0.3f, w * 0.12f), Size(w * 0.4f, w * 0.5f), style = stroke)
                drawCircle(c, w * 0.05f, Offset(w / 2, w * 0.66f))
            }
            StartupIssue.SESSION -> {
                drawCircle(c, w * 0.18f, Offset(w / 2, w * 0.3f), style = stroke)
                drawArc(c, 180f, 180f, false, Offset(w * 0.16f, w * 0.6f), Size(w * 0.68f, w * 0.6f), style = stroke)
            }
            StartupIssue.SERVER, StartupIssue.UNKNOWN -> {
                // A cloud with an exclamation mark.
                drawArc(c, 150f, 220f, false, Offset(w * 0.08f, w * 0.34f), Size(w * 0.44f, w * 0.44f), style = stroke)
                drawArc(c, 200f, 230f, false, Offset(w * 0.34f, w * 0.18f), Size(w * 0.56f, w * 0.56f), style = stroke)
                drawLine(c, Offset(w * 0.22f, w * 0.78f), Offset(w * 0.8f, w * 0.78f), line, StrokeCap.Round)
                drawLine(c, Offset(w * 0.56f, w * 0.4f), Offset(w * 0.56f, w * 0.56f), line, StrokeCap.Round)
                drawCircle(c, w * 0.045f, Offset(w * 0.56f, w * 0.67f))
            }
        }
    }
}

/** One draw-phase clock for all placeholders; no startup delay or data reads. */
@Composable
private fun StartupCardSkeleton(modifier: Modifier = Modifier, categoryCount: Int) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val phase: State<Float> = if (foreground && !LocalLiquidReduceMotion.current) {
        val transition = rememberInfiniteTransition(label = "startupShimmer")
        transition.animateFloat(
            initialValue = -0.65f, targetValue = 1.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "startupShimmerPosition"
        )
    } else {
        remember { mutableStateOf(-1f) }
    }
    Spacer(
        modifier
            .semantics { contentDescription = t("Kartlar yükleniyor") }
            .drawWithCache {
                // Match ReminderScreen: status inset, 20dp header top, 42dp header,
                // 22dp gap, 16dp side margins and vertically overlapping categories.
                val cards = Path()
                val bones = Path()
                val widthDp = size.width / density
                fun Path.rounded(x: Float, y: Float, w: Float, h: Float, radius: Float) {
                    addRoundRect(RoundRect(
                        Rect(x * density, y * density, (x + w) * density, (y + h) * density),
                        CornerRadius(radius * density)
                    ))
                }
                // No wordmark: leave its header space empty; avatar stays at home position.
                bones.rounded(widthDp - 48f, 20f, 48f, 48f, 24f)
                val count = categoryCount.coerceIn(1, 12)
                repeat(count) { index ->
                    val y = 84f + index * 100f
                    cards.rounded(0f, y, widthDp, if (index == count - 1) 140f else 180f, 24f)
                    bones.rounded(32f, y + 20f, widthDp * 0.52f, 28f, 7f)
                    bones.rounded(widthDp - 46f, y + 24f, 16f, 16f, 6f)
                    bones.rounded(22f, y + 66f, 64f, 22f, 11f)
                }
                val categoriesBottom = 84f + (count - 1) * 100f + 140f
                bones.rounded(widthDp / 2f - 46f, categoriesBottom + 18f, 92f, 12f, 5f)
                val headingY = categoriesBottom + 72f
                bones.rounded(2f, headingY, 98f, 18f, 5f)
                bones.rounded(widthDp - 28f, headingY, 24f, 18f, 5f)
                val firstCardY = headingY + 38f
                repeat(3) { index ->
                    val y = firstCardY + index * 126f
                    cards.rounded(0f, y, widthDp, 112f, 22f)
                    bones.rounded(18f, y + 20f, widthDp * 0.58f, 16f, 5f)
                    bones.rounded(18f, y + 50f, widthDp - 48f, 10f, 4f)
                    bones.rounded(18f, y + 70f, widthDp * 0.62f, 10f, 4f)
                }
                onDrawBehind {
                    drawPath(cards, Color.White.copy(alpha = 0.025f))
                    val center = phase.value * size.width
                    val band = size.width * 0.55f
                    val base = Color.White.copy(alpha = 0.06f)
                    val brush = Brush.linearGradient(
                        colors = listOf(base, Color.White.copy(alpha = 0.12f), base),
                        start = Offset(center - band, 0f), end = Offset(center + band, 0f)
                    )
                    drawPath(bones, brush)
                }
            }
    )
}
