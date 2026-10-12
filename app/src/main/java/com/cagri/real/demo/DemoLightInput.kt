package com.cagri.real.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.components.LocalLiquidReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.sin

private val warm = Color(0xFFF0A08A)
private val warmCore = Color(0xFFFFD9C8)
private val litText = Color(0xFFF6D9CE)
private val baseText = Color(0xFFEAE4E2)
private val chipFill = Color.White.copy(alpha = 0.05f)
private val chipInk = Color(0xFF6E7585)

/** Ek4'teki gibi: "Let's make" + imleç + ışıkla beliren kelime. Işık sönerken kelime değişir. */
private val lightWords = listOf("a miracle", "magic", "real")

private fun fract(x: Float): Float = x - floor(x)

/**
 * Işık imleçten sağa koni gibi açılır, kelime ışıkla belirir. Işık sönerken (alttan ve üstten kapanır)
 * kelime değişir: a miracle, magic, real. Yerel, veri bağımsız.
 */
@Composable
internal fun DemoLightInput(modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableIntStateOf(0) }        // 0 = otomatik, 1 = elle
    var lit by rememberSaveable { mutableStateOf(true) }
    var wordIndex by rememberSaveable { mutableIntStateOf(0) }
    val reduceMotion = LocalLiquidReduceMotion.current
    val aperture = remember { Animatable(1f) }
    var time by remember { mutableFloatStateOf(0f) }
    val word = lightWords[wordIndex % lightWords.size]

    LaunchedEffect(mode, lit, reduceMotion) {
        if (mode == 0 && !reduceMotion) {
            while (true) {
                aperture.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
                delay(2400)
                aperture.animateTo(0f, tween(1300, easing = FastOutSlowInEasing))
                wordIndex = (wordIndex + 1) % lightWords.size      // ışık tamamen sönünce kelime değişir
                delay(700)
            }
        } else {
            val target = if (mode == 0 || lit) 1f else 0f
            if (reduceMotion) aperture.snapTo(target)
            else aperture.animateTo(target, tween(1100, easing = FastOutSlowInEasing))
            if (target == 0f) wordIndex = (wordIndex + 1) % lightWords.size
        }
    }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) time = (time + (now - last) / 1_000_000_000f) % 1000f
                    last = now
                }
            }
        }
    }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Işık açılır, kelime belirir.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Işık imleçten açılır, kelime ışıkla görünür. Işık sönerken kelime değişir: miracle, magic, real. " +
                "Elle modunda çubuğa dokunarak aç-kapa yap.",
            color = Color(0xFFAAAAB1), fontSize = 12.sp
        )

        BoxWithConstraints(
            Modifier.fillMaxWidth().height(400.dp).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1B2236), Color(0xFF0A0D15))))
        ) {
            val cardWidth = maxWidth
            Column(
                Modifier.align(Alignment.Center).padding(start = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Çubuk sağ kenardan taşar (görselde olduğu gibi).
                Row(
                    Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(cardWidth)
                        .height(100.dp)
                        .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(32.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.075f), RoundedCornerShape(32.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            enabled = mode == 1, role = Role.Switch, onClickLabel = "Işığı aç ya da kapat"
                        ) { lit = !lit }
                        .padding(start = 37.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Let's make", color = baseText, fontSize = 28.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(14.dp))
                    Box(
                        Modifier.height(64.dp).drawBehind { drawLight(aperture.value, time) },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(16.dp))
                            Box {
                                Text(word, color = baseText.copy(alpha = 0.10f), fontSize = 28.sp,
                                    fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
                                Text(word, color = litText, fontSize = 28.sp,
                                    fontWeight = FontWeight.Light, maxLines = 1, softWrap = false,
                                    modifier = Modifier.apertureReveal { aperture.value })
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFD9A08C), Color(0xFF9A6A5C)))),
                        contentAlignment = Alignment.Center
                    ) { Text("Ç", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium) }
                    Box(Modifier.size(52.dp).clip(CircleShape).background(chipFill), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(22.dp)) {
                            rotate(-45f) {
                                drawRoundRect(chipInk, Offset(size.width * 0.34f, size.height * 0.02f),
                                    Size(size.width * 0.32f, size.height * 0.96f), CornerRadius(size.width * 0.16f),
                                    style = Stroke(size.width * 0.09f))
                            }
                        }
                    }
                    Row(
                        Modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(chipFill).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(Modifier.size(22.dp)) {
                            val sw = Stroke(size.width * 0.07f)
                            drawCircle(chipInk, size.width * 0.46f, style = sw)
                            drawOval(chipInk, Offset(size.width * 0.3f, size.height * 0.04f), Size(size.width * 0.4f, size.height * 0.92f), style = sw)
                            drawLine(chipInk, Offset(size.width * 0.04f, size.height / 2f), Offset(size.width * 0.96f, size.height / 2f), size.width * 0.07f)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Search", color = chipInk, fontSize = 20.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
            Text("@real.demo", color = Color(0xFF3A4152), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
        }

        DemoSegments(labels = listOf("Otomatik", "Elle"), selected = mode, onSelect = { mode = it })
    }
}

/**
 * Ek4: imleçten sağa açılan koni ışık (sol kenarı imleçte keskin, sağa doğru sönen, aşağı doğru daha çok taşan),
 * çubuğun alt kenarında sıcak bir çizgi, imleç çizgisi ve imleç çevresinde süzülen parçacıklar.
 */
private fun DrawScope.drawLight(a: Float, t: Float) {
    val aa = a.coerceIn(0f, 1f)
    val cx = 1.5.dp.toPx()
    val cy = size.height / 2f
    if (aa > 0.002f) {
        clipRect(left = cx, top = -300.dp.toPx(), right = size.width + 700.dp.toPx(), bottom = size.height + 300.dp.toPx()) {
            // Geniş, çok yumuşak taşma (aşağıya doğru ağırlıklı).
            val c1 = Offset(cx + 70.dp.toPx(), cy + 24.dp.toPx())
            val r1 = 300.dp.toPx()
            val spill = Brush.radialGradient(
                0f to warm.copy(alpha = 0.34f), 0.45f to warm.copy(alpha = 0.14f), 1f to warm.copy(alpha = 0f),
                center = c1, radius = r1
            )
            scale(1f, 0.55f * aa, c1) { drawCircle(spill, r1, c1, alpha = aa) }

            // Ana koni gövdesi.
            val c2 = Offset(cx + 34.dp.toPx(), cy + 8.dp.toPx())
            val r2 = 190.dp.toPx()
            val body = Brush.radialGradient(
                0f to warm.copy(alpha = 0.62f), 0.35f to warm.copy(alpha = 0.34f),
                0.7f to warm.copy(alpha = 0.10f), 1f to warm.copy(alpha = 0f),
                center = c2, radius = r2
            )
            scale(1f, 0.34f * aa, c2) { drawCircle(body, r2, c2, alpha = aa) }

            // İmleğin hemen yanındaki sıcak çekirdek.
            val c3 = Offset(cx + 10.dp.toPx(), cy)
            val r3 = 90.dp.toPx()
            val hot = Brush.radialGradient(
                0f to warmCore.copy(alpha = 0.80f), 0.4f to warm.copy(alpha = 0.40f), 1f to warm.copy(alpha = 0f),
                center = c3, radius = r3
            )
            scale(1f, 0.48f * aa, c3) { drawCircle(hot, r3, c3, alpha = aa) }

            // Çubuğun alt kenarına vuran sıcak çizgi.
            val rimY = cy + 50.dp.toPx()
            drawLine(
                Brush.horizontalGradient(
                    listOf(warm.copy(alpha = 0.60f * aa), warm.copy(alpha = 0f)),
                    startX = cx, endX = cx + 230.dp.toPx()
                ),
                Offset(cx, rimY), Offset(cx + 230.dp.toPx(), rimY), strokeWidth = 1.2.dp.toPx()
            )
        }
        for (i in 0 until 24) {
            val r1 = fract(sin(i * 12.9898f) * 43758.547f)
            val r2 = fract(sin(i * 78.233f) * 12345.679f)
            val ph = (t * (0.10f + 0.16f * r1) + r2) % 1f
            val px = cx + 6.dp.toPx() + ph * 115.dp.toPx() * (0.45f + 0.55f * r1)
            val py = cy + (r2 - 0.5f) * 64.dp.toPx() * aa * (0.4f + ph) + sin(t * 1.3f + i) * 3.dp.toPx()
            val al = ((1f - ph) * aa * (0.30f + 0.70f * r2)).coerceIn(0f, 1f)
            drawCircle(warmCore.copy(alpha = al), (0.6f + r1 * 0.8f).dp.toPx(), Offset(px, py))
        }
    }
    // İmleç: yumuşak hale + ana çizgi.
    val half = 26.dp.toPx()
    val glow = 0.25f + 0.75f * aa
    drawLine(warm.copy(alpha = 0.08f * glow), Offset(cx, cy - half), Offset(cx, cy + half), 14.dp.toPx(), StrokeCap.Round)
    drawLine(warm.copy(alpha = 0.18f * glow), Offset(cx, cy - half), Offset(cx, cy + half), 8.dp.toPx(), StrokeCap.Round)
    drawLine(
        Color(0xFFFFF1E8).copy(alpha = 0.55f + 0.45f * aa), Offset(cx, cy - half), Offset(cx, cy + half),
        strokeWidth = 3.2.dp.toPx(), cap = StrokeCap.Round
    )
}

/** Yalnız açıklığın içindeki kısım görünür. Açıklık ortadan genişler, alttan ve üstten daralır. Sağa doğru söner. */
private fun Modifier.apertureReveal(a: () -> Float): Modifier = this
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        alpha = a().coerceIn(0f, 1f)
    }
    .drawWithContent {
        val half = size.height / 2f * a().coerceIn(0f, 1f)
        clipRect(top = size.height / 2f - half, bottom = size.height / 2f + half) {
            this@drawWithContent.drawContent()
        }
        drawRect(
            Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f))),
            blendMode = BlendMode.DstIn
        )
    }
