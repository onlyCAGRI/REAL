package com.cagri.real.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import kotlin.random.Random
import com.cagri.real.ui.motion.RealEntranceMotion
import com.cagri.real.ui.motion.realWord
import com.cagri.real.ui.theme.RealFont

/**
 * Referanstaki orta ekranın karşılığı: mood tonuyla boyanan, günün saatine göre alt tonu değişen
 * gradient kart ve içinde tek, ince, büyük bir cümle. Cümle kelime kelime blur'dan netliğe açılır.
 * Gradient çok yavaş "nefes alır" (draw aşamasında okunur, recomposition yok); reduceAnimations'ta sabit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun YasamHeroSentence(
    greeting: String,
    sentence: String,
    tone: Color,
    hour: Int,
    reduceAnimations: Boolean,
    entrance: RealEntranceMotion?,
    wordsStartMs: Int,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    compact: Boolean = false
) {
    val ink = Color(0xFF05080C)
    val bottom = when {
        hour < 12 -> Color(0xFFE9B79B)   // sabah: şeftali
        hour < 18 -> Color(0xFF9AA7B4)   // öğleden sonra: gri-mavi
        else -> Color(0xFF6B5B95)        // akşam: mor
    }
    val breathe = rememberInfiniteTransition(label = "yasamHeroBreathe").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "yasamHeroBreatheValue"
    )
    val words = sentence.split(' ').filter { it.isNotBlank() }
    Box(
        modifier.fillMaxWidth().padding(horizontal = 14.dp).heightIn(min = if (compact) 110.dp else 220.dp)
            .clip(RoundedCornerShape(32.dp))
            .drawBehind {
                val b = if (reduceAnimations) 0.5f else breathe.value
                drawRect(
                    Brush.verticalGradient(
                        0f to ink,
                        (0.42f + 0.12f * b) to tone.copy(alpha = 0.50f).compositeOver(ink),
                        1f to bottom.copy(alpha = 0.55f + 0.1f * b).compositeOver(ink)
                    )
                )
            }
            .padding(start = 26.dp, end = 26.dp, top = (if (compact) 14.dp else 26.dp) + topInset, bottom = if (compact) 14.dp else 26.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!compact) {
            Text(
                greeting.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp,
                letterSpacing = 1.6.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                words.forEachIndexed { i, word ->
                    Text(
                        word, color = Color.White.copy(alpha = 0.94f), fontSize = 25.sp, lineHeight = 32.sp,
                        fontWeight = FontWeight.Light, fontFamily = YasamThin,
                        modifier = if (entrance != null) Modifier.realWord(entrance, wordsStartMs + i * 45) else Modifier
                    )
                }
            }
        }
    }
}

/** Yaşam da ortak geri düğmesini kullanır. */
@Composable
internal fun YasamBackButton(onBack: () -> Unit, modifier: Modifier = Modifier, description: String = "Geri") {
    RealBackButton(onBack, modifier, description)
}

/** Referanstaki üst bar: üç ikon, altında kayan ince çizgi. 0 = hepsi, 1 = kayıtlar, 2 = eğlence. */
@Composable
internal fun YasamIconTabs(tab: Int, onTab: (Int) -> Unit, reduceAnimations: Boolean, modifier: Modifier = Modifier) {
    val names = listOf("Hepsi", "Kayıtlar", "Keşfet")
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
        val cell = maxWidth / 3
        val indicatorX by animateDpAsState(
            cell * tab + (cell - 28.dp) / 2,
            if (reduceAnimations) snap() else spring(dampingRatio = 0.8f, stiffness = 400f),
            label = "yasamTabIndicator"
        )
        Row(Modifier.fillMaxWidth()) {
            names.forEachIndexed { i, name ->
                val alpha by animateFloatAsState(
                    if (i == tab) 1f else 0.4f, if (reduceAnimations) snap() else tween(220), label = "yasamTabAlpha"
                )
                Box(
                    Modifier.weight(1f).height(48.dp)
                        .clickable(role = Role.Tab) { onTab(i) }
                        .semantics { contentDescription = name; selected = i == tab },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        val c = Color.White.copy(alpha = alpha)
                        val w = 1.5.dp.toPx()
                        when (i) {
                            0 -> { // 2x2 nokta
                                val q = size.width / 4f
                                for (x in listOf(q, 3 * q)) for (y in listOf(q, 3 * q)) drawCircle(c, 2.2.dp.toPx(), Offset(x, y))
                            }
                            1 -> { // üç çubuk
                                val hs = listOf(0.45f, 0.95f, 0.65f)
                                hs.forEachIndexed { k, h ->
                                    val x = size.width * (0.2f + 0.3f * k)
                                    drawLine(c, Offset(x, size.height), Offset(x, size.height * (1f - h)), w, StrokeCap.Round)
                                }
                            }
                            else -> { // halka + merkez nokta
                                drawCircle(c, size.width * 0.42f, style = Stroke(w))
                                drawCircle(c, 2.dp.toPx())
                            }
                        }
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = indicatorX).width(28.dp).height(1.5.dp)
                .background(Color.White)
        )
    }
}

/** Her açılışta farklı konu: bir öncekiyle aynı konu üst üste gelmez; son konu SharedPreferences'ta kalıcıdır. */
internal object YasamHeroTopics {
    const val COUNT = 6
    private const val PREFS = "yasam_hero"
    private const val KEY_LAST = "last_topic"
    fun next(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getInt(KEY_LAST, -1)
        var t: Int
        do { t = Random.nextInt(COUNT) } while (t == last)
        prefs.edit().putInt(KEY_LAST, t).apply()
        return t
    }
}

/**
 * 0 su (bugün) · 1 dün özeti (su + mood) · 2 mood · 3 kitap · 4 film/dizi/oyun · 5 günlük.
 * Veri yoksa konu yine de geçerli, genel bir cümleye düşer; böylece veri sonradan gelince konu değişmez.
 */
internal fun yasamHeroSentence(
    topic: Int, water: YasamWater?, mood: YasamMoodView, book: YasamBook?, mediaPrompt: String,
    journal: YasamJournalPreview? = null
): String = when (topic) {
    0 -> when {
        water == null || water.progress <= 0f -> "Bugün bir bardak suyla başlamaya ne dersin?"
        water.progress >= 1f -> "Su hedefini tamamladın, harika gidiyorsun."
        else -> "Bugün ${YasamSentence.litres(water.todayMl)} L su içtin, hedefe ${YasamSentence.litres(water.goalMl - water.todayMl)} L kaldı."
    }
    1 -> YasamSentence.build(water?.yesterdayMl, mood.yesterday) ?: "Dün nasıl geçti? Bugün nasıl gidiyor?"
    2 -> mood.last?.let { "En son ruh halin ${it.label} idi. Şimdi nasılsın?" } ?: "Bugün ruh halin nasıl?"
    3 -> when {
        book?.percent != null -> "Kitabın %${book.percent}'de. Bu akşam birkaç sayfa okumaya ne dersin?"
        book != null -> "${book.book.title} seni bekliyor."
        else -> "Rafında yeni bir kitap için zaman var mı?"
    }
    4 -> "$mediaPrompt Sana bir film, dizi ya da oyun önerelim."
    else -> journal?.let { "En son \"${it.shortLine}\" yazmıştın. Bugün neler var?" }
        ?: "Bugünü iki satırla kayda geçirmek ister misin?"
}
