package com.cagri.real.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cagri.real.ui.theme.RealType

private val EntryAccent = Color(0xFF2F6BFF)      // ışık + "izlesek?" + ana buton rengi
private val EntryGlowSoft = Color(0xFFBFD0FF)    // ışığın parlak çekirdeği

/** Referanstaki gibi: üstte renkli bulanık ışık, alta doğru siyaha akıyor. Blur gerektirmez, her Android sürümünde çalışır. */
@Composable
internal fun EntryGlow(reduceAnimations: Boolean) {
    val drift = if (reduceAnimations) 0f else {
        val transition = rememberInfiniteTransition(label = "entry-glow")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "drift").value
    }
    Box(Modifier.fillMaxSize().drawBehind {
        val w = size.width; val h = size.height
        drawRect(Brush.radialGradient(listOf(EntryAccent, Color.Transparent),
            center = Offset(w * (.18f + .10f * drift), h * .10f), radius = w * 1.15f))
        drawRect(Brush.radialGradient(listOf(EntryGlowSoft.copy(alpha = .9f), Color.Transparent),
            center = Offset(w * (.80f - .12f * drift), h * (.20f + .04f * drift)), radius = w * .75f))
        drawRect(Brush.verticalGradient(.28f to Color.Transparent, .68f to Color.Black, startY = 0f, endY = h))
    })
}

@Composable
internal fun EntryContent(posterUrls: List<String>, savedCount: Int, canContinue: Boolean, loading: Boolean, enabled: Boolean,
                         onPickGenre: () -> Unit, onContinue: () -> Unit, onWatchlist: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        // Işığın içinde: İzlenecek listendeki son afişler (liste boşsa sadece ışık kalır).
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            for (i in posterUrls.indices.reversed()) {
                AsyncImage(posterUrls[i], null,
                    Modifier.size(128.dp, 192.dp)
                        .graphicsLayer {
                            rotationZ = when (i) { 0 -> 0f; 1 -> -9f; else -> 9f }
                            translationX = when (i) { 0 -> 0f; 1 -> -62.dp.toPx(); else -> 62.dp.toPx() }
                            translationY = if (i == 0) 0f else 10.dp.toPx()
                            alpha = if (i == 0) 1f else .8f
                        }
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B1B20))
                        .border(.5.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(ActionGlyph.Video, Modifier.size(20.dp), Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Film & Dizi", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        val titleStyle = RealType.editorialTitle.copy(fontSize = 44.sp, lineHeight = 48.sp,
            fontWeight = FontWeight.Medium, letterSpacing = (-1.4).sp)
        Text("Bugün ne", style = titleStyle.copy(color = Color.White))
        Text("izlesek?", style = titleStyle.copy(color = EntryAccent))
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                loading -> "Önerilerin hazırlanıyor…"
                savedCount > 0 -> "İzlenecek listende $savedCount başlık var. Yeni bir tür seç ya da kaldığın yerden devam et."
                else -> "Türünü seç, sana uyan film ve dizi afişlerini keşfet."
            },
            color = Color.White.copy(alpha = .55f), fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(end = 24.dp))
        Spacer(Modifier.height(28.dp))
        if (canContinue) {
            EntryPrimaryButton("Keşfetmeye devam et", enabled, onContinue)
            Spacer(Modifier.height(10.dp))
            EntrySecondaryButton("Tür seç", enabled, onPickGenre)
        } else EntryPrimaryButton("Tür seç", enabled, onPickGenre)
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onWatchlist), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(ActionGlyph.Calendar, Modifier.size(18.dp), if (enabled) Color.White else Color(0xFF66666C))
                Spacer(Modifier.width(8.dp))
                Text("İzlenecek listem ($savedCount)", color = if (enabled) Color.White else Color(0xFF66666C), fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun EntryPrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().height(56.dp).clip(shape)
        .background(if (enabled) EntryAccent else EntryAccent.copy(alpha = .35f))
        .drawBehind {
            // Referanstaki gibi butonun ortasında hafif parlama.
            if (enabled) drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .38f), Color.Transparent),
                center = center, radius = size.width * .38f))
        }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun EntrySecondaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().height(56.dp).clip(shape).background(Color.White.copy(alpha = .07f))
        .border(1.dp, Color.White.copy(alpha = .18f), shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled) Color.White else Color(0xFF66666C), fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

