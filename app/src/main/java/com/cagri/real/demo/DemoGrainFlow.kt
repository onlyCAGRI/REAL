package com.cagri.real.demo

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.components.LocalLiquidReduceMotion
import kotlin.random.Random

/** Ek3 için tekrarlanan gren dokusu. Bir kez üretilir. */
private val grainBrush: Brush by lazy {
    val n = 128
    val rnd = Random(7)
    val px = IntArray(n * n) {
        val v = rnd.nextInt(256)
        (rnd.nextInt(0, 70) shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private fun Modifier.grain(alpha: Float = 0.45f): Modifier = drawWithContent {
    drawContent()
    drawRect(grainBrush, alpha = alpha)
}

private val questions = listOf(
    "Bugün seni en çok ne güldürdü?",
    "Aklında dönüp duran bir şey var mı?",
    "Yarın kendine bırakacağın not ne?",
    "Tek kelimeyle: bu hafta?"
)

/** Ek3: grenli gradyan üç ekran. Açılış, giriş, sorular. Yerel, veri bağımsız. */
@Composable
internal fun DemoGrainFlow(modifier: Modifier = Modifier) {
    var screen by rememberSaveable { mutableIntStateOf(0) }
    var selQ by rememberSaveable { mutableIntStateOf(0) }
    var doneMask by rememberSaveable { mutableIntStateOf(0) }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    val reduceMotion = LocalLiquidReduceMotion.current

    val drift = rememberInfiniteTransition(label = "drift")
    val phaseAnim by drift.animateFloat(
        0f, 6.2832f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "phase"
    )
    val phase = { if (reduceMotion) 0f else phaseAnim }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Gren, gradyan, üç adım.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        DemoSegments(labels = listOf("Açılış", "Giriş", "Sorular"), selected = screen, onSelect = { screen = it })

        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(36.dp))) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                    else fadeIn(tween(320)) togetherWith fadeOut(tween(220))
                },
                label = "grainScreens"
            ) { s ->
                when (s) {
                    0 -> SplashScreen(phase) { screen = 1 }
                    1 -> LoginScreen(user, { user = it }, pass, { pass = it }) { screen = 2 }
                    else -> QuestionsScreen(
                        selQ, doneMask, { selQ = it },
                        onRecord = {
                            doneMask = doneMask or (1 shl selQ)
                            selQ = (selQ + 1).coerceAtMost(questions.lastIndex)
                        },
                        onClose = { screen = 0 }
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashScreen(phase: () -> Float, onNext: () -> Unit) {
    val none = Color.Transparent
    Box(
        Modifier.fillMaxSize().background(Color(0xFFEDEDF0))
            .drawBehind {
                val p = phase()
                val w = size.width
                val h = size.height
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0A12), none), endY = h * 0.4f))
                drawRect(Brush.radialGradient(
                    listOf(Color(0xFF1B2BD8), Color(0xFF1B2BD8).copy(alpha = 0f)),
                    center = Offset(w * (0.15f + 0.05f * kotlin.math.sin(p)), h * (0.2f + 0.03f * kotlin.math.cos(p))), radius = w * 0.95f))
                drawRect(Brush.radialGradient(
                    listOf(Color(0xFF3A0B0B), none), center = Offset(w, h * 0.02f), radius = w * 0.55f))
                drawRect(Brush.radialGradient(
                    listOf(Color(0xFFEDEDF0), none),
                    center = Offset(w * (0.1f + 0.06f * kotlin.math.cos(p)), h * 0.55f), radius = w * 0.85f))
            }
            .grain()
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = "Devam et") { onNext() }
    ) {
        Text("REAL\nNOT", color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, modifier = Modifier.padding(24.dp))
        Canvas(Modifier.align(Alignment.TopEnd).padding(24.dp).size(56.dp)) {
            val circle = Path().apply { addOval(Rect(0f, 0f, size.width, size.height)) }
            clipPath(circle) {
                drawRect(Color.White)
                for (i in 1..5) {
                    drawRect(Color(0xFF1B1B26), Offset(0f, size.height * i / 6f - size.height * 0.035f), Size(size.width, size.height * 0.07f))
                }
            }
        }
        Text("2026", color = Color(0xFF6C6C78), fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(24.dp))
        Text(
            "SADECE\nBİR NOT\nDEĞİL", color = Color(0xFF0E0E16), fontSize = 44.sp, lineHeight = 44.sp,
            textAlign = TextAlign.End, letterSpacing = (-1).sp, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        )
    }
}

@Composable
private fun LoginScreen(user: String, onUser: (String) -> Unit, pass: String, onPass: (String) -> Unit, onSignIn: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color(0xFF05050B), 0.45f to Color(0xFF1B1668), 0.8f to Color(0xFF4B3FE0), 1f to Color(0xFFDAD6F5)
            )
        ).grain()
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text("REAL\nNOT", color = Color.White, fontSize = 12.sp, lineHeight = 14.sp)
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(18.dp)) {
                        drawLine(Color(0xFF111118), Offset(0f, size.height * 0.3f), Offset(size.width, size.height * 0.3f), 2.dp.toPx())
                        drawLine(Color(0xFF111118), Offset(size.width * 0.3f, size.height * 0.7f), Offset(size.width, size.height * 0.7f), 2.dp.toPx())
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            Text("Kendi sesini\nduyur", color = Color.White, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text("Dağınık not yok. Kısa ses, kısa video, hepsi tek yerde. Blabla blabla, havalı bir cümle buraya.",
                color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(30.dp))
            UnderlineField(user, onUser, "Kullanıcı adın")
            Spacer(Modifier.height(18.dp))
            UnderlineField(pass, onPass, "Şifren", password = true)
            Spacer(Modifier.height(30.dp))
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF5B4FF0).copy(alpha = 0.55f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(26.dp))
                    .clickable(role = Role.Button, onClick = onSignIn),
                contentAlignment = Alignment.Center
            ) { Text("GİRİŞ YAP", color = Color.White, fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(48.dp))
            Box(
                Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF3A3A5A).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) { Text("Hesabın yok mu?", color = Color(0xFF3A3A5A), fontSize = 12.sp) }
        }
    }
}

@Composable
private fun UnderlineField(value: String, onChange: (String) -> Unit, hint: String, password: Boolean = false) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
        cursorBrush = SolidColor(Color.White),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 10.dp)) {
                    if (value.isEmpty()) Text(hint, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
                    inner()
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.55f)))
            }
        }
    )
}

@Composable
private fun QuestionsScreen(
    selected: Int, doneMask: Int, onSelect: (Int) -> Unit, onRecord: () -> Unit, onClose: () -> Unit
) {
    val doneCount = Integer.bitCount(doneMask)
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color(0xFF08080F), 0.5f to Color(0xFF1E1A6E), 1f to Color(0xFF5546F0))
        ).grain(0.4f)
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text("Sıradaki\nSorular", color = Color.White, fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
                Box(
                    Modifier.size(34.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .semantics { contentDescription = "Kapat" }
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = Color.White, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                questions.forEachIndexed { i, q ->
                    val isSel = i == selected
                    val done = ((doneMask shr i) and 1) == 1
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = if (isSel) 0.14f else 0.07f))
                            .border(1.dp, Color.White.copy(alpha = if (isSel) 0.9f else 0.14f), RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { onSelect(i) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Soru ${i + 1} · 30 sn", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            Text(q, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            Modifier.size(22.dp).clip(CircleShape)
                                .background(if (done || isSel) Color.White else Color.Transparent)
                                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text(if (done) "✓" else "›", color = Color(0xFF1E1A6E), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Daha önce cevaplayanlar:", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tints = listOf(Color(0xFFF2A07B), Color(0xFF8E9BFF), Color(0xFF7BD0A5), Color(0xFFF2C94C), Color(0xFFE58AB4))
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    tints.forEach { Box(Modifier.size(30.dp).clip(CircleShape).background(it).border(2.dp, Color(0xFF2A2590), CircleShape)) }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.height(30.dp).weight(1f).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) { Text("✓  +54 kişi", color = Color.White, fontSize = 11.sp) }
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("İlerleme", color = Color.White, fontSize = 11.sp)
                Text("$doneCount / ${questions.size}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(questions.size) {
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (it < doneCount) Color.White else Color.White.copy(alpha = 0.22f))
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RecordButton("Video kaydet", video = true, modifier = Modifier.weight(1f), onClick = onRecord)
                RecordButton("Ses kaydet", video = false, modifier = Modifier.weight(1f), onClick = onRecord)
            }
        }
    }
}

@Composable
private fun RecordButton(label: String, video: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(23.dp)).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(23.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(16.dp)) {
            val s = Stroke(1.5.dp.toPx())
            if (video) {
                drawRoundRect(Color.White, Offset(size.width * 0.06f, size.height * 0.1f), Size(size.width * 0.88f, size.height * 0.8f), CornerRadius(size.width * 0.2f), style = s)
                drawCircle(Color.White, size.width * 0.14f, Offset(size.width / 2f, size.height / 2f))
            } else {
                drawRoundRect(Color.White, Offset(size.width * 0.32f, 0f), Size(size.width * 0.36f, size.height * 0.62f), CornerRadius(size.width * 0.18f), style = s)
                drawLine(Color.White, Offset(size.width / 2f, size.height * 0.75f), Offset(size.width / 2f, size.height), 1.5.dp.toPx())
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
