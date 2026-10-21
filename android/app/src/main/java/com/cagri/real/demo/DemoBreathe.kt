package com.cagri.real.demo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.components.LocalLiquidReduceMotion
import kotlin.math.abs

private class Mood(val word: String, val desc: String, val deep: Color, val mid: Color, val soft: Color)

private val moods = listOf(
    Mood("aktif", "kısa, net, enerjik bir başlangıç\n1 dk · 2 set", Color(0xFF0A6CF0), Color(0xFF1FC8FF), Color(0xFF9BE6FF)),
    Mood("rahatla", "omuzlar düşsün, çene gevşesin\n2 dk · 1 set", Color(0xFF6B35E8), Color(0xFFA877FF), Color(0xFFDCCBFF)),
    Mood("kaygı", "zihni yavaşça yere indir\n3 dk · 1 set", Color(0xFFD93A73), Color(0xFFFF8CA6), Color(0xFFFFCFD9)),
    Mood("uyan", "ışığı içeri al, güne ağır ağır gir\n1 dk · 3 set", Color(0xFFE8820C), Color(0xFFFFC04A), Color(0xFFFFE6A8)),
    Mood("uyku", "ekranı kapat, gün burada bitsin\n5 dk · 1 set", Color(0xFF2C2FB8), Color(0xFF6B72F5), Color(0xFFBFC3FF)),
    Mood("denge", "iki ayak yerde, bir nefes içeride\n2 dk · 2 set", Color(0xFF0E9A63), Color(0xFF4FD99A), Color(0xFFC0F5DD)),
    Mood("huzur", "hiçbir şey yapma, çok iyi gidiyorsun\n4 dk · 1 set", Color(0xFF0B8C98), Color(0xFF3FD2D8), Color(0xFFBDF3F4))
)
private val topTabs = listOf("senin için", "keşfet", "favoriler")
private val soft = TextStyle(shadow = Shadow(Color(0x26000000), Offset(0f, 2f), 10f))
private val dark = Color(0xFF1C1C22)

/** Ek2: nefes alan renk lekesi ve kelime listesi. Yerel, veri bağımsız. */
@Composable
internal fun DemoBreathe(modifier: Modifier = Modifier) {
    var sel by rememberSaveable { mutableIntStateOf(0) }
    var started by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var inhale by remember { mutableStateOf(true) }
    val reduceMotion = LocalLiquidReduceMotion.current
    val mood = moods[sel]

    val deep by animateColorAsState(mood.deep, tween(800), label = "deep")
    val mid by animateColorAsState(mood.mid, tween(800), label = "mid")
    val softC by animateColorAsState(mood.soft, tween(800), label = "soft")

    val breath = remember { Animatable(0.5f) }
    LaunchedEffect(started, reduceMotion) {
        if (reduceMotion) {
            breath.snapTo(0.5f)
        } else if (started) {
            while (true) {
                inhale = true
                breath.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
                inhale = false
                breath.animateTo(0f, tween(4000, easing = FastOutSlowInEasing))
            }
        } else {
            while (true) {
                breath.animateTo(0.65f, tween(3500, easing = FastOutSlowInEasing))
                breath.animateTo(0.35f, tween(3500, easing = FastOutSlowInEasing))
            }
        }
    }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Bir kelime seç, nefes al.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text("Kelimelere dokun ya da yukarı aşağı kaydır. Başla'ya basınca leke seninle nefes alır.", color = Color(0xFFAAAAB1), fontSize = 12.sp)

        Box(
            Modifier.fillMaxWidth().height(820.dp).clip(RoundedCornerShape(36.dp)).background(Color(0xFFF6EADD))
        ) {
            Box(Modifier.matchParentSize().drawBehind {
                val pulse = if (started) 0.22f else 0.25f
                val s = 1f + (breath.value - 0.5f) * 2f * pulse
                val c = Offset(size.width * 0.5f, size.height * 0.46f)
                val r = size.width * 0.9f * s
                drawCircle(
                    Brush.radialGradient(
                        0f to deep, 0.35f to mid, 0.75f to softC.copy(alpha = 0.55f), 1f to softC.copy(alpha = 0f),
                        center = c, radius = r
                    ),
                    radius = r, center = c
                )
            })

            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 28.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                topTabs.forEachIndexed { i, label ->
                    Text(
                        label, color = Color.White.copy(alpha = if (i == tab) 1f else 0.5f), fontSize = 26.sp,
                        style = soft, maxLines = 1, softWrap = false,
                        modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null,
                            role = Role.Tab) { tab = i }
                    )
                }
            }

            Box(Modifier.align(Alignment.TopCenter).padding(top = 120.dp)) {
                AnimatedContent(
                    targetState = if (started) (if (inhale) "nefes al" else "nefes ver") else "",
                    transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(400)) },
                    label = "breathLabel"
                ) { text -> Text(text, color = Color.White, fontSize = 22.sp, style = soft) }
            }

            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 100.dp)
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectVerticalDragGestures(
                            onDragStart = { acc = 0f }, onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }
                        ) { _, dy ->
                            acc += dy
                            val step = 84.dp.toPx()
                            if (acc > step) { sel = (sel - 1).coerceAtLeast(0); acc = 0f }
                            else if (acc < -step) { sel = (sel + 1).coerceAtMost(moods.lastIndex); acc = 0f }
                        }
                    }
            ) {
                moods.forEachIndexed { i, m ->
                    val alpha by animateFloatAsState(
                        if (i == sel) 1f else (0.85f - 0.12f * abs(i - sel)).coerceAtLeast(0.3f),
                        tween(250), label = "wordAlpha"
                    )
                    Column(
                        Modifier.clickable(remember { MutableInteractionSource() }, indication = null,
                            role = Role.Button) { sel = i }.padding(vertical = 9.dp)
                    ) {
                        Text(
                            m.word, color = Color.White.copy(alpha = alpha), fontSize = 40.sp, lineHeight = 48.sp, style = soft,
                            fontWeight = if (i == sel) FontWeight.Medium else FontWeight.Normal
                        )
                        AnimatedVisibility(i == sel, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Text(m.desc, color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp, lineHeight = 17.sp,
                                style = soft, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                        }
                    }
                }
            }

            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f))
                        .semantics { contentDescription = "Karıştır" }
                        .clickable(role = Role.Button) { sel = (sel + 3) % moods.size },
                    contentAlignment = Alignment.Center
                ) { Text("···", color = dark, fontSize = 18.sp, fontWeight = FontWeight.Bold) }

                Row(
                    Modifier.height(54.dp).clip(RoundedCornerShape(27.dp)).background(Color.White)
                        .clickable(role = Role.Button) { started = !started }
                        .padding(start = 22.dp, end = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (started) "durdur" else "başla", color = dark, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFEDEDF1)), contentAlignment = Alignment.Center) {
                        Text("›", color = dark, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}
