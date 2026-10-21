package com.cagri.real.ui

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
fun StartupLoadingScreen(offline: Boolean, onRetry: () -> Unit, onUseLocal: () -> Unit, categoryCount: Int = 4) {
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        if (offline) {
            Column(Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text("Kartlar henüz yüklenemedi. Bağlantını kontrol edebilirsin.", color = Color.LightGray)
                TextButton(onClick = onRetry) { Text("Yeniden dene") }
                TextButton(onClick = onUseLocal) { Text("Yerel olarak devam et") }
            }
        } else {
            StartupCardSkeleton(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
                categoryCount
            )
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
            .semantics { contentDescription = "Kartlar yükleniyor" }
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
