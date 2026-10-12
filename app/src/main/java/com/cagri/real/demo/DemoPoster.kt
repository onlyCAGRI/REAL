package com.cagri.real.demo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.cagri.real.ui.components.LocalLiquidReduceMotion

/**
 * Başlık fontu. Görseldeki yumuşak, kalın serif için gerçek font dosyasını res/font altına
 * koyup şu tek satırı değiştir:  FontFamily(Font(R.font.dosya_adin, FontWeight.Black))
 */
private val displayFont: FontFamily = FontFamily.Serif
private val bodyFont: FontFamily = FontFamily.SansSerif

private val headlineStyle = TextStyle(
    fontFamily = displayFont, fontWeight = FontWeight.Black, fontSize = 46.sp,
    lineHeight = 44.sp, letterSpacing = (-1.5).sp
)
private val smallStyle = TextStyle(fontFamily = bodyFont, fontSize = 13.sp, lineHeight = 16.sp)
private val logoStyle = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Medium, fontSize = 24.sp, letterSpacing = (-0.5).sp)

private val defaultTop = 0xFF2DB84B.toInt()
private val swatches = listOf(
    0xFF2DB84B.toInt(), 0xFFFF7A1A.toInt(), 0xFFFF5C93.toInt(),
    0xFF3D7BFF.toInt(), 0xFFFFC928.toInt(), 0xFFB48CFF.toInt()
)
private val swatchNames = listOf("Yeşil", "Turuncu", "Pembe", "Mavi", "Sarı", "Lila")

private class PosterColors(val top: Color, val bottom: Color, val ink: Color, val bottomInk: Color)

/** Üst rengi seçilen renk. Alt renk aynı tonun koyusu. Yazı renkleri kontrasta göre otomatik. */
private fun derive(topArgb: Int): PosterColors {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(topArgb, hsl)
    val top = Color(topArgb)
    val lum = top.luminance()
    val bottom = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1].coerceIn(0.45f, 1f), (hsl[2] * 0.3f).coerceIn(0.04f, 0.16f))))
    val tint = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], (hsl[1] * 0.6f).coerceIn(0.2f, 0.8f), 0.92f)))
    return PosterColors(
        top = top,
        bottom = bottom,
        ink = if (lum > 0.25f) bottom else tint,
        bottomInk = if (lum > 0.12f) top else tint
    )
}

/** Poster: üst rengi sen seç, alt renk otomatik gelir, yazılar poster üzerinde düzenlenir. Yerel, veri bağımsız. */
@Composable
internal fun DemoPoster(modifier: Modifier = Modifier) {
    var topArgb by rememberSaveable { mutableIntStateOf(defaultTop) }
    var headline by rememberSaveable { mutableStateOf("Fresh by\nChoice,\nPure by\nNature.") }
    var small1 by rememberSaveable { mutableStateOf("Green Goods\nfor your Plate") }
    var small2 by rememberSaveable { mutableStateOf("Grown Close\nto Home,\nPicked Fresh") }
    var contact by rememberSaveable { mutableStateOf("hello@\nfreshfield.co") }
    var brand by rememberSaveable { mutableStateOf("FreshField") }
    val reduceMotion = LocalLiquidReduceMotion.current

    val target = remember(topArgb) { derive(topArgb) }
    val spec = if (reduceMotion) tween<Color>(0) else tween<Color>(450)
    val top by animateColorAsState(target.top, spec, label = "posterTop")
    val bottom by animateColorAsState(target.bottom, spec, label = "posterBottom")
    val ink by animateColorAsState(target.ink, spec, label = "posterInk")
    val bottomInk by animateColorAsState(target.bottomInk, spec, label = "posterBottomInk")

    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(topArgb, it) }

    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rengini seç, yazını yaz.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("Üst rengi sen seç, alt renk otomatik gelir. Posterdeki her yazıya dokunup düzenle.",
                        color = Color(0xFFAAAAB1), fontSize = 12.sp)
                }
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(bottom)) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 44.dp, bottomEnd = 44.dp))
                        .background(top).padding(start = 26.dp, end = 26.dp, top = 30.dp, bottom = 26.dp)
                ) {
                    PosterField(headline, { headline = it }, headlineStyle, ink, "Başlık")
                    Spacer(Modifier.height(34.dp))
                    Row(Modifier.fillMaxWidth().height(176.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                            PosterField(small1, { small1 = it }, smallStyle, ink, "Alt başlık")
                            PosterField(small2, { small2 = it }, smallStyle, ink, "Not")
                        }
                        ProduceArt(Modifier.size(176.dp).clip(RoundedCornerShape(36.dp)))
                    }
                }
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 190.dp).padding(26.dp),
                    horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween
                ) {
                    PosterField(contact, { contact = it }, smallStyle.copy(textAlign = TextAlign.End), bottomInk, "iletişim", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(18.dp)) {
                            rotate(-35f) {
                                drawOval(bottomInk, Offset(size.width * 0.2f, 0f), Size(size.width * 0.6f, size.height))
                            }
                            drawLine(bottomInk, Offset(size.width * 0.15f, size.height), Offset(size.width * 0.4f, size.height * 0.6f),
                                size.width * 0.08f, StrokeCap.Round)
                        }
                        Spacer(Modifier.width(4.dp))
                        PosterField(brand, { brand = it }, logoStyle, bottomInk, "Marka")
                    }
                }
            }
        }

        // Renk paneli: üst renk = senin seçimin, alt renk otomatik.
        Column(Modifier.fillMaxWidth().background(Color(0xFF111113)).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                swatches.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                            .border(2.dp, if (c == topArgb) Color.White else Color.Transparent, CircleShape)
                            .semantics { contentDescription = "${swatchNames[i]} üst renk" }
                            .selectable(c == topArgb, role = Role.RadioButton) { topArgb = c }
                    )
                }
                Box(Modifier.width(1.dp).height(24.dp).background(Color(0xFF3A3A3F)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(bottom).border(1.dp, Color(0xFF3A3A3F), CircleShape))
                }
            }
            Text("Solda üst renk · sağdaki daire otomatik alt renk", color = Color(0xFF8E8E96), fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp))
            val sliderColors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF3A3A3F)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ton", color = Color(0xFFAAAAB1), fontSize = 12.sp, modifier = Modifier.width(64.dp))
                Slider(
                    value = hsl[0], valueRange = 0f..360f, colors = sliderColors,
                    onValueChange = { topArgb = ColorUtils.HSLToColor(floatArrayOf(it, hsl[1], hsl[2])) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Açıklık", color = Color(0xFFAAAAB1), fontSize = 12.sp, modifier = Modifier.width(64.dp))
                Slider(
                    value = hsl[2].coerceIn(0.25f, 0.75f), valueRange = 0.25f..0.75f, colors = sliderColors,
                    onValueChange = { topArgb = ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1], it)) }
                )
            }
        }
    }
}

@Composable
private fun PosterField(
    value: String, onChange: (String) -> Unit, style: TextStyle, color: Color, hint: String,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value, onValueChange = onChange, textStyle = style.copy(color = color),
        cursorBrush = SolidColor(color), modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, style = style, color = color.copy(alpha = 0.35f))
                inner()
            }
        }
    )
}

/** Kutudaki ürün görseli yerine çizilmiş brokoli. Kendi fotoğrafın için bu composable'ı Image ile değiştir. */
@Composable
private fun ProduceArt(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFFD2A06A), Color(0xFFC08A55))))
        drawRoundRect(Color(0xFFEFEFEA), Offset(w * 0.33f, h * 0.55f), Size(w * 0.36f, h * 0.45f), CornerRadius(w * 0.02f))
        drawRoundRect(Color(0xFFD9D9D2), Offset(w * 0.33f, h * 0.55f), Size(w * 0.07f, h * 0.45f), CornerRadius(w * 0.02f))
        drawRoundRect(Color(0xFFA8C26A), Offset(w * 0.455f, h * 0.34f), Size(w * 0.1f, h * 0.26f), CornerRadius(w * 0.04f))
        val shades = listOf(Color(0xFF2F5A22), Color(0xFF3F6B2A), Color(0xFF4C7A33), Color(0xFF35622A))
        val pts = listOf(
            0.50f to 0.24f, 0.40f to 0.28f, 0.60f to 0.28f, 0.45f to 0.18f, 0.56f to 0.19f,
            0.35f to 0.22f, 0.65f to 0.23f, 0.50f to 0.32f, 0.42f to 0.34f, 0.58f to 0.34f
        )
        pts.forEachIndexed { i, (x, y) -> drawCircle(shades[i % 4], w * 0.085f, Offset(w * x, h * y)) }
        drawLine(Color(0xFF8DB04A), Offset(w * 0.82f, h * 0.58f), Offset(w * 0.86f, h * 0.85f), w * 0.012f, StrokeCap.Round)
        drawOval(Color(0xFFC98A3C), Offset(w * 0.72f, h * 0.8f), Size(w * 0.2f, h * 0.24f))
        drawCircle(Color(0xFF8FB35A), w * 0.09f, Offset(w * 0.27f, h * 0.88f))
    }
}
