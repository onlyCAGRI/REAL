package com.cagri.real.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.components.LocalLiquidReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Videodan ölçüldü (720x900 kare, 30 fps):
 *  - Pill 392x117 px (3.35:1). Kenar çizgisi ~1.3 px, iç ışık ~8 px, dış ışık ~8 px ve çok sönük.
 *  - Işık iki parça: beyaz çekirdek (yaklaşık ±40° geniş) ve onun önünde giden macenta/mor kuyruk.
 *    Dururken kuyruk beyazın ~100° önünde, hızlanınca yay boyunca geride kalıp ~50°'ye yaklaşır.
 *  - Hareket (5.4 sn döngü): 0.3 sn duruş, 1.9 sn tam tur, 1.8 sn ikinci tam tur (hızlı çıkış, uzun yavaşlama),
 *    ~1.4 sn çok yavaş sürüklenme. Duruş noktası üst kenar (~-90°..-72°).
 */
private class GlowPalette(val name: String, val edge: Color, val mid: Color, val core: Color)

private val glowPalettes = listOf(
    GlowPalette("Mor", Color(0xFF4A35F0), Color(0xFFE060FA), Color.White),
    GlowPalette("Turuncu", Color(0xFFFF3B1F), Color(0xFFFFA11F), Color(0xFFFFF3D6)),
    GlowPalette("Yeşil", Color(0xFF14B8A6), Color(0xFF7CFF6B), Color(0xFFF0FFF0))
)

private const val PeakFraction = 0.38f

private fun frac(rel: Float): Float = PeakFraction + rel / 360f

/** Beyaz çekirdek: tepe noktası PeakFraction'da. Arkada kısa, önde biraz daha uzun sönüm. */
private fun whiteStops(p: GlowPalette): Array<Pair<Float, Color>> = arrayOf(
    0f to p.edge.copy(alpha = 0f),
    frac(-70f) to p.edge.copy(alpha = 0f),
    frac(-50f) to p.edge.copy(alpha = 0.22f),
    frac(-40f) to lerp(p.edge, p.core, 0.5f).copy(alpha = 0.45f),
    frac(-20f) to lerp(p.edge, p.core, 0.8f).copy(alpha = 0.80f),
    frac(-8f) to p.core,
    frac(8f) to p.core,
    frac(20f) to p.core.copy(alpha = 0.96f),
    frac(40f) to lerp(p.edge, p.core, 0.55f).copy(alpha = 0.45f),
    frac(60f) to p.edge.copy(alpha = 0f),
    1f to p.edge.copy(alpha = 0f)
)

/** Renkli kuyruk: tepe noktası PeakFraction'da (macenta), iki yanında mor-mavi. */
private fun colorStops(p: GlowPalette): Array<Pair<Float, Color>> = arrayOf(
    0f to p.edge.copy(alpha = 0f),
    frac(-90f) to p.edge.copy(alpha = 0f),
    frac(-55f) to p.edge.copy(alpha = 0.45f),
    frac(-25f) to lerp(p.edge, p.mid, 0.55f).copy(alpha = 0.82f),
    frac(0f) to p.mid,
    frac(22f) to lerp(p.mid, p.edge, 0.5f).copy(alpha = 0.72f),
    frac(55f) to p.edge.copy(alpha = 0.38f),
    frac(90f) to p.edge.copy(alpha = 0f),
    1f to p.edge.copy(alpha = 0f)
)

private fun ringPath(rect: Rect, outer: Float, inner: Float): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    addRoundRect(RoundRect(rect.inflate(outer), CornerRadius(rect.height / 2f + outer)))
    addRoundRect(RoundRect(rect.deflate(inner), CornerRadius((rect.height / 2f - inner).coerceAtLeast(0f))))
}

private const val RestAngle = -90f
private val spinA = CubicBezierEasing(0.45f, 0.05f, 0.55f, 0.95f)   // yumuşak başla, yumuşak bit
private val spinB = CubicBezierEasing(0.22f, 0.12f, 0.12f, 1f)      // hızlı çıkış, uzun yavaşlama

/** Video 1: kenarında ışık gezen "Let me real" butonu. Yerel, veri bağımsız. */
@Composable
internal fun DemoGlowButton(modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableIntStateOf(0) }       // 0 = sürekli, 1 = basınca
    var paletteIndex by rememberSaveable { mutableIntStateOf(0) }
    var taps by rememberSaveable { mutableIntStateOf(0) }
    val reduceMotion = LocalLiquidReduceMotion.current
    val palette = glowPalettes[paletteIndex]
    val whiteBrush = remember(paletteIndex) { whiteStops(palette) }
    val colorBrush = remember(paletteIndex) { colorStops(palette) }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()

    // Beyaz çekirdeğin açısı. 0° = sağ, saat yönünde artar. Üst kenar = -90°.
    val angle = remember { Animatable(RestAngle) }
    // Renkli kuyruk beyazı yaylı biçimde takip eder: dururken +100° önde, hızlanınca geride kalıp ~+50°'ye iner.
    val tail = remember { Animatable(RestAngle + 100f) }

    LaunchedEffect(Unit) {
        snapshotFlow { angle.value }.collectLatest { target ->
            if (reduceMotion) tail.snapTo(target + 100f)
            else tail.animateTo(target + 100f, spring(dampingRatio = 1f, stiffness = 140f))
        }
    }

    LaunchedEffect(mode, reduceMotion) {
        angle.snapTo(RestAngle + 18f)
        if (mode == 0 && !reduceMotion) {
            while (true) {
                delay(300)
                angle.animateTo(angle.value + 360f, tween(1900, easing = spinA))
                angle.animateTo(angle.value + 342f, tween(1800, easing = spinB))
                angle.animateTo(angle.value + 18f, tween(1400, easing = LinearEasing))
            }
        }
    }
    LaunchedEffect(pressed) {
        if (mode == 1 && pressed && !reduceMotion) {
            angle.animateTo(angle.value + 360f, tween(1800, easing = spinB))
        }
    }

    val boost by animateFloatAsState(
        targetValue = when {
            pressed -> 1f
            mode == 0 -> 0.85f
            else -> 0.5f
        },
        animationSpec = if (reduceMotion) tween(0) else spring(stiffness = 260f), label = "glowBoost"
    )
    val scale by animateFloatAsState(
        if (pressed) 0.965f else 1f,
        if (reduceMotion) tween(0) else spring(dampingRatio = 0.6f, stiffness = 500f), label = "glowScale"
    )

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Kenarında ışık gezen buton.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Sürekli modda ışık videodaki gibi iki tur atıp durur, yavaşça süzülür ve tekrar başlar. Basınca modunda dokununca bir tur atar.",
            color = Color(0xFFAAAAB1), fontSize = 12.sp
        )

        Box(
            Modifier.fillMaxWidth().height(360.dp)
                .clip(RoundedCornerShape(28.dp)).background(Color(0xFF121318)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "@real.demo", color = Color(0xFF6C6C74), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            )
            Box(
                Modifier.size(292.dp, 126.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .drawBehind {
                        val pad = 28.dp.toPx()
                        val rect = Rect(pad, pad, size.width - pad, size.height - pad)
                        val k = boost
                        val reach = size.maxDimension
                        val brushW = Brush.sweepGradient(*whiteBrush, center = rect.center)
                        val brushC = Brush.sweepGradient(*colorBrush, center = rect.center)
                        val half = 360f * PeakFraction

                        drawRoundRect(Color.Black, rect.topLeft, rect.size, CornerRadius(rect.height / 2f))
                        drawRoundRect(
                            Color.White.copy(alpha = 0.07f), rect.topLeft, rect.size,
                            CornerRadius(rect.height / 2f), style = Stroke(1.dp.toPx())
                        )
                        fun spin(path: Path, alpha: Float) {
                            clipPath(path) {
                                rotate(tail.value - half, rect.center) {
                                    drawCircle(brushC, radius = reach, center = rect.center, alpha = alpha)
                                }
                                rotate(angle.value - half, rect.center) {
                                    drawCircle(brushW, radius = reach, center = rect.center, alpha = alpha)
                                }
                            }
                        }
                        // Dış ışık: çok sönük, kenara yakın biraz daha belirgin (videoda ~8 px).
                        spin(ringPath(rect, 6.dp.toPx(), 1.dp.toPx()), 0.05f * k)
                        spin(ringPath(rect, 3.5.dp.toPx(), 1.dp.toPx()), 0.09f * k)
                        spin(ringPath(rect, 1.8.dp.toPx(), 1.dp.toPx()), 0.16f * k)
                        // İç ışık: ~5 dp içeri doğru sönen.
                        spin(ringPath(rect, 0f, 6.dp.toPx()), 0.07f * k)
                        spin(ringPath(rect, 0f, 3.dp.toPx()), 0.12f * k)
                        // Keskin kenar çizgisi (~1.3 px).
                        spin(ringPath(rect, 0.4.dp.toPx(), 1.2.dp.toPx()), 0.78f + 0.22f * k)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(236.dp, 70.dp).clip(RoundedCornerShape(50))
                        .semantics { contentDescription = "Parlayan buton" }
                        .clickable(interactionSource = source, indication = null, role = Role.Button) { taps++ },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Let me real", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Normal)
                }
            }
            Text(
                if (taps == 0) "Dokun" else "$taps dokunuş", color = Color(0xFF6C6C74), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
            )
        }

        DemoSegments(labels = listOf("Sürekli", "Basınca"), selected = mode, onSelect = { mode = it })

        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            glowPalettes.forEachIndexed { index, p ->
                val selected = index == paletteIndex
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(p.edge, p.mid)))
                        .border(2.dp, if (selected) Color.White else Color.Transparent, CircleShape)
                        .semantics { contentDescription = "${p.name} renk" }
                        .selectable(selected, role = Role.RadioButton, onClick = { paletteIndex = index })
                )
            }
        }
    }
}
