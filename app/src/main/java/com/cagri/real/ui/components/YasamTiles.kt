package com.cagri.real.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import com.cagri.real.books.coverBaseColor
import com.cagri.real.books.CoverPalette
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.produceState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.books.BookCover
import com.cagri.real.data.MoodTrackerEntry
import com.cagri.real.ui.motion.RealEntranceEase
import com.cagri.real.ui.motion.RealEntranceMotion
import com.cagri.real.ui.motion.realEntrance
import com.cagri.real.ui.theme.RealFont
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/** Kutu ve hızlı eylem davranışları; ekran her seferinde yeniden kurar, state tutmaz. */
internal class YasamActions(
    val openWater: () -> Unit,
    val addWater: () -> Unit,
    val openMood: () -> Unit,
    val addMood: () -> Unit,
    val openBooks: () -> Unit,
    val resumeBook: () -> Unit,
    /** Film/dizi önerisi: yalnız film girişini açar. */
    val openFilm: () -> Unit,
    /** Oyun önerisi: yalnız oyun girişini açar. */
    val openGame: () -> Unit,
    /** İzlenecek listem: doğrudan kayıtlı izlenecek listesi. */
    val openFilmList: () -> Unit,
    /** Oynanacak listem: doğrudan kayıtlı oynanacak listesi. */
    val openGameList: () -> Unit,
    /** Yaşam içinde Günlüklerim moduna geçer (ayrı ekran açmaz). */
    val openJournals: () -> Unit,
    val newJournal: () -> Unit
)

/** Günlük satırı önizlemesi: en son girişin tarihi ve ilk satırı. */
internal class YasamJournalPreview(val whenLabel: String, val line: String) {
    /** Cümle içinde kullanılacak kısa hali (en çok 38 karakter). */
    val shortLine: String get() = if (line.length <= 38) line else line.take(37).trimEnd() + "…"
}

/** Zaman etiketi: BUGÜN · 14:20 / DÜN · 23:10 / 3 EKİ. [createdAt] epoch milisaniye; yoksa "SON GİRİŞ". */
internal fun yasamJournalWhenLabel(createdAt: Long?): String {
    if (createdAt == null) return "SON GİRİŞ"
    val zone = java.time.ZoneId.systemDefault()
    val at = java.time.Instant.ofEpochMilli(createdAt).atZone(zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(at.toLocalDate(), java.time.LocalDate.now(zone))
    val time = at.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    return when (days) {
        0L -> "BUGÜN · $time"
        1L -> "DÜN · $time"
        else -> at.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr-TR"))).uppercase(Locale.forLanguageTag("tr-TR"))
    }
}

/** Metinden ilk dolu satırı ve zamanı çıkarır; boşsa null. [createdAt] epoch milisaniye. */
internal fun yasamJournalPreview(text: String, createdAt: Long?): YasamJournalPreview? {
    val first = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    return YasamJournalPreview(yasamJournalWhenLabel(createdAt), first)
}

private val YasamHairline = Color.White.copy(alpha = 0.10f)
private val Mono = FontFamily.Monospace

/**
 * RealFont (Inter) yalnız Regular/Medium/SemiBold/Bold taşır; FontWeight.Light onda Regular'a düşer.
 * Gerçekten ince yazı için sistem sans-serif ailesi (Light) kullanılır. Birebir Inter Light istenirse
 * res/font'a inter_light.ttf ekleyip bu satırı FontFamily(Font(R.font.inter_light, FontWeight.Light)) yapın.
 */
internal val YasamThin: FontFamily = FontFamily.SansSerif

/**
 * Referans tasarım: cam kutular yerine ince çizgiyle ayrılmış satırlar. Üstte tek dev su rakamı + yükselen
 * çubuklar; satırın başlığına dokununca satır açılır (aynı anda tek satır) ve hızlı eylemleri gösterir.
 * Modüle gitmek için açılan satırdaki "AÇ ›" eylemi kullanılır.
 */
@Composable
internal fun YasamTiles(
    tab: Int,
    water: YasamWater?,
    book: YasamBook?,
    mood: YasamMoodView,
    mediaPrompt: String,
    reduceAnimations: Boolean,
    actions: YasamActions,
    entrance: RealEntranceMotion? = null,
    baseMs: Int = 0,
    journal: YasamJournalPreview? = null,
    moodEntries: List<MoodTrackerEntry> = emptyList(),
    watchlistCount: Int? = null,
    playlistCount: Int? = null,
    /** Bölüm değişiminde (Home → Journals) satırların kademeli çıkış saati (ms); null = çıkış yok. */
    leave: Animatable<Float, AnimationVector1D>? = null
) {
    val showRecords = tab != 2
    val showFun = tab != 1
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // Mood geçmişi satır içinde açılır; mood satırı kapanınca geçmiş de kapanır.
    var moodHistoryOpen by rememberSaveable { mutableStateOf(false) }
    var waterHistoryOpen by rememberSaveable { mutableStateOf(false) }
    fun toggle(key: String) {
        open = if (open == key) null else key
        if (open != "mood") moodHistoryOpen = false
        if (open != "water") waterHistoryOpen = false
    }

    Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 20.dp)) {
        if (showRecords) {
            YasamWaterHero(water, reduceAnimations, entrance, baseMs, Modifier.rowLeave(leave, 0))
            YasamRow(
                "SU", if (water == null) "—" else "/ ${YasamSentence.litres(water.goalMl)} L",
                open == "water", reduceAnimations, rowEnter(entrance, baseMs, 0).rowLeave(leave, 1), { toggle("water") },
                preview = {
                    // Çubuk zaten üstteki dev rakamın altında; burada tekrar yok, kısa bir durum cümlesi var.
                    Text(
                        when {
                            water == null -> "Su verisi yükleniyor"
                            water.progress >= 1f -> "Günlük hedefi tamamladın"
                            else -> "Hedefe ${YasamSentence.litres(water.goalMl - water.todayMl)} L kaldı"
                        },
                        color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Light, fontFamily = YasamThin
                    )
                }
            ) {
                Text(
                    if (water == null) "Veri yok" else "BUGÜN ${(water.progress * 100).roundToInt()}% · 250 ML",
                    color = YasamMuted, fontSize = 11.sp, fontFamily = Mono, letterSpacing = 1.sp
                )
                Spacer(Modifier.height(12.dp))
                YasamSlideToAdd("Kaydır · 250 ml su ekle", reduceAnimations, actions.addWater)
                YasamTextAction("SU EKRANINI AÇ  ›", YasamWaterTone, actions.openWater)
                YasamTextAction(
                    if (waterHistoryOpen) "GEÇMİŞİ GİZLE  ‹" else "GEÇMİŞİ AÇ  ›", YasamMuted,
                    { waterHistoryOpen = !waterHistoryOpen },
                    state = if (waterHistoryOpen) "Açık" else "Kapalı"
                )
                AnimatedVisibility(
                    visible = waterHistoryOpen,
                    enter = if (reduceAnimations) EnterTransition.None
                    else expandVertically(tween(300, easing = RealEntranceEase)) + fadeIn(tween(220, delayMillis = 60)),
                    exit = if (reduceAnimations) ExitTransition.None
                    else shrinkVertically(tween(220)) + fadeOut(tween(120))
                ) { YasamWaterHistory(water, reduceAnimations) }
            }
            YasamRow(
                "MOOD", mood.last?.label ?: "Henüz yok",
                open == "mood", reduceAnimations, rowEnter(entrance, baseMs, 1).rowLeave(leave, 2), { toggle("mood") },
                preview = { YasamSpark(mood.points, YasamMoodTone) }
            ) {
                YasamTextAction("MOOD EKLE  +", YasamMoodTone, actions.addMood)
                YasamTextAction(
                    if (moodHistoryOpen) "GEÇMİŞİ GİZLE  ‹" else "GEÇMİŞİ AÇ  ›", YasamMuted,
                    { moodHistoryOpen = !moodHistoryOpen },
                    state = if (moodHistoryOpen) "Açık" else "Kapalı"
                )
                AnimatedVisibility(
                    visible = moodHistoryOpen,
                    enter = if (reduceAnimations) EnterTransition.None
                    else expandVertically(tween(300, easing = RealEntranceEase)) + fadeIn(tween(220, delayMillis = 60)),
                    exit = if (reduceAnimations) ExitTransition.None
                    else shrinkVertically(tween(220)) + fadeOut(tween(120))
                ) {
                    YasamMoodHistory(moodEntries, reduceAnimations, actions.addMood, actions.openMood)
                }
            }
            YasamRow(
                "GÜNLÜKLERİM", "Bugün yaz",
                open == "journal", reduceAnimations, rowEnter(entrance, baseMs, 2).rowLeave(leave, 3), { toggle("journal") },
                preview = {
                    if (journal == null) Text(
                        "Bugün ne düşünüyorsun?", color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp, fontWeight = FontWeight.Light, fontFamily = YasamThin
                    ) else Column {
                        Text(journal.whenLabel, color = YasamMuted, fontSize = 10.sp, fontFamily = Mono, letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            journal.line, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 19.sp,
                            fontWeight = FontWeight.Light, fontFamily = YasamThin, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            ) {
                YasamTextAction("BUGÜN YAZ  +", YasamJournalTone, actions.newJournal)
                YasamTextAction("GÜNLÜKLERİ AÇ  ›", YasamMuted, actions.openJournals)
            }
        }
        if (showFun) {
            YasamRow(
                "KİTAP", book?.percent?.let { "%$it" } ?: book?.book?.title ?: "Rafını aç",
                open == "book", reduceAnimations, rowEnter(entrance, baseMs, 3).rowLeave(leave, 4), { toggle("book") },
                preview = {
                    if (book != null) YasamBookPreview(book, reduceAnimations)
                    else Text("Rafını aç", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = YasamThin, fontWeight = FontWeight.Light)
                }
            ) {
                YasamTextAction(
                    if (book != null) "OKUMAYA DEVAM ET  ›" else "RAFINI AÇ  ›", YasamBookTone,
                    if (book != null) actions.resumeBook else actions.openBooks
                )
                if (book != null) YasamTextAction("RAFI AÇ  ›", YasamMuted, actions.openBooks)
            }
            YasamRow(
                "FİLM & DİZİ · OYUN", "Öneri al",
                open == "media", reduceAnimations, rowEnter(entrance, baseMs, 4).rowLeave(leave, 5), { toggle("media") },
                preview = {
                    Text(mediaPrompt, color = Color.White, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Light, fontFamily = YasamThin)
                }
            ) {
                YasamTextAction("FİLM / DİZİ ÖNERİ AL  ›", YasamMediaTone, actions.openFilm)
                YasamTextAction(
                    "İZLENECEK LİSTEM" + (watchlistCount?.let { " · $it" } ?: "") + "  ›", YasamMuted, actions.openFilmList
                )
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(YasamHairline))
                YasamTextAction("OYUN ÖNERİ AL  ›", YasamMediaTone, actions.openGame)
                YasamTextAction(
                    "OYNANACAK LİSTEM" + (playlistCount?.let { " · $it" } ?: "") + "  ›", YasamMuted, actions.openGameList
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(YasamHairline).rowLeave(leave, 6))
    }
}

/**
 * Kitap satırı önizlemesi: kapak görseli yok; kitabın ana rengi (kitaplık sırtıyla aynı CoverPalette) küçük bir
 * kutu ve ince ilerleme barı olarak görünür. Renk kapak okunana kadar coverBaseColor yedeğiyle başlar.
 */
@Composable
private fun YasamBookPreview(book: YasamBook, reduce: Boolean) {
    val context = LocalContext.current
    val url = book.book.coverUrl
    val fallback = remember(book.book.id) { coverBaseColor(book.book) }
    val dominant by produceState<Color?>(url?.let { CoverPalette.cached(it) }?.let { Color(it) }, url) {
        if (url.isNullOrBlank()) { value = null; return@produceState }
        CoverPalette.dominant(context, url)?.let { value = Color(it) }
    }
    val base by animateColorAsState(dominant ?: fallback, if (reduce) snap() else tween(220), label = "yasamBookColor")
    // Siyah zeminde kaybolmasın: çok koyu renk beyazla açılır.
    val tone = if (base.luminance() < 0.25f) lerp(base, Color.White, 0.35f) else base
    val fraction by animateFloatAsState(
        ((book.percent ?: 0) / 100f).coerceIn(0f, 1f), if (reduce) snap() else tween(500), label = "yasamBookProgress"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp, 48.dp).clip(RoundedCornerShape(5.dp))
                .background(Brush.verticalGradient(listOf(tone, lerp(tone, Color.Black, 0.2f))))
        )
        Column(Modifier.weight(1f)) {
            Text(
                book.book.title, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                fontWeight = FontWeight.Light, fontFamily = YasamThin, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (book.percent != null) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.10f))) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(tone))
                }
            }
        }
    }
}

private fun rowEnter(entrance: RealEntranceMotion?, baseMs: Int, index: Int): Modifier =
    if (entrance == null) Modifier
    else Modifier.realEntrance(entrance, baseMs + 480 + index * 55, 460, slideY = 14.dp)

/**
 * Home → Journals geçişinde satırların kademeli çıkışı (55 ms arayla yukarı kayıp solar). Saat graphicsLayer
 * içinde okunur: kare başına recomposition yok. [leave] null ya da 0 iken kimliktir.
 */
internal fun Modifier.rowLeave(leave: Animatable<Float, AnimationVector1D>?, index: Int): Modifier =
    if (leave == null) this else graphicsLayer {
        val p = ((leave.value - index * YasamLeaveStaggerMs) / YasamLeaveItemMs).coerceIn(0f, 1f)
        if (p > 0f) {
            alpha = 1f - p
            translationY = -10.dp.toPx() * RealEntranceEase.transform(p)
        }
    }

/** [rowLeave] toplam süresi: en geç çıkan öğe (indeks 6) için. */
internal const val YasamLeaveStaggerMs = 55
internal const val YasamLeaveItemMs = 200
internal const val YasamLeaveTotalMs = 6 * YasamLeaveStaggerMs + YasamLeaveItemMs

@Composable
private fun rememberCountUp(target: Float, reduce: Boolean, delayMs: Int): Float {
    val anim = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target, reduce) {
        if (reduce) anim.snapTo(target)
        else anim.animateTo(target, tween(900, delayMillis = if (anim.value == 0f) delayMs else 0, easing = RealEntranceEase))
    }
    return anim.value
}

/** Referanstaki "net worth": ince dev rakam + sağa doğru yükselen çubuklar; yanan çubuk sayısı = günlük ilerleme. */
@Composable
private fun YasamWaterHero(water: YasamWater?, reduce: Boolean, entrance: RealEntranceMotion?, baseMs: Int, modifier: Modifier = Modifier) {
    val shownMl = rememberCountUp(water?.todayMl?.toFloat() ?: 0f, reduce, baseMs + 300)
    val progress = water?.progress ?: 0f
    Column(
        modifier.fillMaxWidth().padding(bottom = 12.dp)
            .then(if (entrance != null) Modifier.realEntrance(entrance, baseMs + 300, 520, slideY = 12.dp) else Modifier)
    ) {
        Text("SU · BUGÜN", color = YasamMuted, fontSize = 11.sp, fontFamily = Mono, letterSpacing = 1.6.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (water == null) "—" else String.format(Locale("tr"), "%.1f", shownMl / 1000f),
                color = Color.White, fontSize = 48.sp, lineHeight = 54.sp, fontWeight = FontWeight.Light, fontFamily = YasamThin
            )
            Text(
                " L", color = YasamMuted, fontSize = 20.sp, fontFamily = RealFont,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        Canvas(Modifier.fillMaxWidth().padding(top = 10.dp).height(44.dp)) {
            val n = 36
            val gap = size.width / n
            val lit = progress.coerceIn(0f, 1f) * n
            for (k in 0 until n) {
                val grow = if (entrance == null) 1f
                else RealEntranceEase.transform(entrance.slice(baseMs + 380 + k * 14, 520))
                val curve = 0.28f + 0.72f * (k / (n - 1f)).pow(1.6f)
                val x = gap * k + gap / 2f
                drawLine(
                    if (k < lit) YasamWaterTone else Color.White.copy(alpha = 0.14f),
                    Offset(x, size.height), Offset(x, size.height - size.height * curve * grow),
                    1.6.dp.toPx(), StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun YasamRow(
    label: String,
    value: String,
    expanded: Boolean,
    reduce: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
    preview: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f, if (reduce) snap() else tween(280), label = "yasamRowChevron"
    )
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(YasamHairline))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (expanded) "Açık" else "Kapalı" },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label, color = YasamMuted, fontSize = 12.sp, fontFamily = Mono, letterSpacing = 1.2.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Text(
                value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Light,
                fontFamily = YasamThin, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(14.dp))
            Canvas(Modifier.size(14.dp).graphicsLayer { rotationZ = rotation }) {
                val p = Path().apply {
                    moveTo(size.width * 0.15f, size.height * 0.35f)
                    lineTo(size.width * 0.5f, size.height * 0.7f)
                    lineTo(size.width * 0.85f, size.height * 0.35f)
                }
                drawPath(p, Color.White.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        // Kapalıyken de görünen önizleme: her satırın içeriği ilk bakışta okunur.
        if (preview != null) Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) { preview() }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduce) EnterTransition.None
            else expandVertically(tween(320, easing = RealEntranceEase)) + fadeIn(tween(240, delayMillis = 80)),
            exit = if (reduce) ExitTransition.None
            else shrinkVertically(tween(240)) + fadeOut(tween(140))
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) { content() }
        }
    }
}

/** Metin eylem; 48dp dokunma alanı. [state] verilirse TalkBack'e açık/kapalı durumu bildirilir. */
@Composable
internal fun YasamTextAction(text: String, tone: Color, onClick: () -> Unit, state: String? = null) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)
            .then(if (state != null) Modifier.semantics { stateDescription = state } else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, color = tone, fontSize = 12.sp, fontFamily = Mono, letterSpacing = 1.2.sp)
    }
}

/**
 * Referanstaki "Slide to Invest": fiziksel bir "içtim" jesti. Eşik %85; bırakınca geri yaylanır.
 * Erişilebilirlik: tek dokunuşluk onClick eylemi de vardır.
 */
@Composable
private fun YasamSlideToAdd(label: String, reduce: Boolean, onDone: () -> Unit) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val knob = 44.dp
    val pad = 4.dp
    val knobPx = with(density) { knob.toPx() }
    val padPx = with(density) { pad.toPx() }
    var trackPx by remember { mutableIntStateOf(0) }
    var drag by remember { mutableFloatStateOf(0f) }
    val maxPx = (trackPx - knobPx - 2 * padPx).coerceAtLeast(1f)
    val fraction = (drag / maxPx).coerceIn(0f, 1f)
    val dragState = rememberDraggableState { delta -> drag = (drag + delta).coerceIn(0f, maxPx) }
    Box(
        Modifier.fillMaxWidth().height(knob + pad * 2).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .onSizeChanged { trackPx = it.width }
            .semantics { contentDescription = label; onClick(label) { onDone(); true } }
            .padding(pad),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            label, color = Color.White.copy(alpha = 0.6f * (1f - fraction)), fontSize = 13.sp,
            fontFamily = RealFont, modifier = Modifier.align(Alignment.Center).padding(start = knob / 2)
        )
        Box(
            Modifier.offset { IntOffset(drag.roundToInt(), 0) }.size(knob).clip(CircleShape).background(Color.White)
                .draggable(
                    state = dragState, orientation = Orientation.Horizontal,
                    onDragStopped = {
                        val done = drag > maxPx * 0.85f
                        if (done) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDone() }
                        if (reduce) drag = 0f
                        else animate(drag, 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)) { v, _ -> drag = v }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(16.dp)) {
                val c = Color.Black
                val w = 1.8.dp.toPx()
                drawLine(c, Offset(size.width * 0.1f, size.height / 2), Offset(size.width * 0.9f, size.height / 2), w, StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.55f, size.height * 0.15f), Offset(size.width * 0.9f, size.height / 2), w, StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.55f, size.height * 0.85f), Offset(size.width * 0.9f, size.height / 2), w, StrokeCap.Round)
            }
        }
    }
}

/** Son mood puanlarından mini çizgi; 2'den az veri varsa yalnız sönük bir taban çizgisi. */
@Composable
private fun YasamSpark(points: List<Float>, tone: Color) {
    Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val pad = 3.dp.toPx()
        if (points.size < 2) {
            drawLine(tone.copy(alpha = 0.25f), Offset(2.dp.toPx(), size.height / 2f), Offset(size.width - 2.dp.toPx(), size.height / 2f), stroke.width, StrokeCap.Round)
            return@Canvas
        }
        val stepX = (size.width - 4.dp.toPx()) / (points.size - 1)
        val xs = points.indices.map { 2.dp.toPx() + it * stepX }
        val ys = points.map { pad + (1f - (it - 1f) / 4f) * (size.height - 2 * pad) }
        val path = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until points.size) {
                val mid = (xs[i - 1] + xs[i]) / 2f
                cubicTo(mid, ys[i - 1], mid, ys[i], xs[i], ys[i])
            }
        }
        drawPath(path, tone, style = stroke)
    }
}
