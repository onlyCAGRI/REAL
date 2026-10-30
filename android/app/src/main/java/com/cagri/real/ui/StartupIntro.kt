package com.cagri.real.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cagri.real.R
import com.cagri.real.ui.components.RealMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cold start: the splash's white "Real" waits in the middle, then slides into the home header while the
 * background fades and the home rows rise one after another (the avatar last). [clock] counts milliseconds of
 * the rise; it rests at [DONE] so screens opened later are not affected.
 */
object StartupIntro {
    const val DONE = 2000f
    private const val STEP = 55f
    private const val RISE = 440f
    const val AVATAR_AT = 620f
    private const val AVATAR_FADE = 320f
    var clock by mutableFloatStateOf(DONE)
    var wordmarkLanded by mutableStateOf(true)
    /** Set by the home header once it is laid out: the reveal waits for it, or it would uncover an empty screen. */
    var homeLaidOut by mutableStateOf(false)

    fun arm() {
        ColdStartTracker.markExecuted()   // the old whole-list fade would stack on the rise
        wordmarkLanded = false
        clock = 0f
    }

    fun settle() {
        clock = DONE
        wordmarkLanded = true
    }

    /** 0..1 for the row at [order] (0 = categories, then the cards). */
    fun rowProgress(order: Int): Float =
        FastOutSlowInEasing.transform(((clock - order.coerceAtMost(8) * STEP) / RISE).coerceIn(0f, 1f))

    fun avatarProgress(): Float = ((clock - AVATAR_AT) / AVATAR_FADE).coerceIn(0f, 1f)
}

fun Modifier.startupRise(order: Int): Modifier = graphicsLayer {
    val p = StartupIntro.rowProgress(order)
    if (p < 1f) {
        alpha = p
        translationY = (1f - p) * 18.dp.toPx()
    }
}

fun Modifier.startupAvatar(): Modifier = graphicsLayer {
    val p = StartupIntro.avatarProgress()
    if (p < 1f) {
        alpha = p
        val s = 0.9f + 0.1f * p
        scaleX = s; scaleY = s
    }
}

/** Splash wordmark size: matches drawable/splash_wordmark (132dp of the 288dp splash icon). */
private val SplashWordmarkWidth = 132.dp
private val HomeWordmarkWidth = 73.dp
private val HomeWordmarkHeight = 30.dp

/** The black screen with the centered wordmark, shown before the home exists (no motion). */
@Composable
fun StartupWordmarkScreen() {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.real_home_wordmark), null, contentScale = ContentScale.Fit,
            modifier = Modifier.size(SplashWordmarkWidth, SplashWordmarkWidth * HomeWordmarkHeight.value / HomeWordmarkWidth.value))
    }
}

/** Covers the home until [readyToReveal], then plays the landing. Replaces the skeleton shimmer. */
@Composable
fun StartupIntroOverlay(readyToReveal: Boolean, reduceMotion: Boolean) {
    remember { if (!reduceMotion) StartupIntro.arm(); true }
    val move = remember { Animatable(0f) }
    val backdrop = remember { Animatable(1f) }
    var finished by remember { mutableStateOf(false) }
    // Left early (offline screen, welcome, workspace switch): never leave the home hidden.
    DisposableEffect(Unit) { onDispose { StartupIntro.settle() } }
    val ready by rememberUpdatedState(readyToReveal)
    // Latched: once it starts it plays through, even if readiness flickers while the rows load.
    LaunchedEffect(Unit) {
        snapshotFlow { ready }.first { it }
        // A category screen opened from a link has no home header: do not wait for it forever.
        withTimeoutOrNull(800) { snapshotFlow { StartupIntro.homeLaidOut }.first { it } }
        if (reduceMotion) {
            backdrop.animateTo(0f, tween(180)); finished = true
            return@LaunchedEffect
        }
        // The home's first draw stalls the main thread for a few hundred ms; start only once frames come
        // smoothly again (3 in a row under ~24ms), or the slide would play half-frozen over an empty screen.
        withTimeoutOrNull(900) {
            var calm = 0
            var last = withFrameNanos { it }
            while (calm < 3) {
                val now = withFrameNanos { it }
                calm = if (now - last < 24_000_000L) calm + 1 else 0
                last = now
            }
        }
        // One frame-driven timeline (ms) for the slide, the fade and the rise. A frame step is capped at 20ms, so a
        // stall pauses the intro instead of skipping half of it.
        var t = 0f
        var last = withFrameNanos { it }
        while (t < StartupIntro.DONE + 120f) {
            val now = withFrameNanos { it }
            t += ((now - last) / 1_000_000f).coerceAtMost(20f)
            last = now
            move.snapTo(RealMotion.Emphasized.transform((t / 560f).coerceAtMost(1f)))
            backdrop.snapTo(1f - ((t - 100f) / 320f).coerceIn(0f, 1f))
            StartupIntro.clock = (t - 120f).coerceIn(0f, StartupIntro.DONE)
            if (t >= 560f && !finished) {
                StartupIntro.wordmarkLanded = true
                finished = true
            }
        }
    }
    if (finished) return
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    BoxWithConstraints(Modifier.fillMaxSize()
        .pointerInput(Unit) {
            // Taps must not reach home controls while they are still covered.
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        }) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = backdrop.value }.background(Color.Black))
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        Image(painterResource(R.drawable.real_home_wordmark), null, contentScale = ContentScale.Fit,
            modifier = Modifier.size(HomeWordmarkWidth, HomeWordmarkHeight).graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                val ww = HomeWordmarkWidth.toPx()
                val wh = HomeWordmarkHeight.toPx()
                // Header slot: 16dp start, status inset + 20dp top + (42dp - 30dp) / 2.
                val endX = 16.dp.toPx()
                val endY = statusTop + 26.dp.toPx()
                val s0 = SplashWordmarkWidth.toPx() / ww
                val startX = (w - ww * s0) / 2f
                val startY = (h - wh * s0) / 2f
                val p = move.value
                val s = s0 + (1f - s0) * p
                translationX = startX + (endX - startX) * p
                translationY = startY + (endY - startY) * p
                scaleX = s; scaleY = s
            })
    }
}
