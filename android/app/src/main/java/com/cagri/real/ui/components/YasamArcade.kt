package com.cagri.real.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.theme.RealFont
import kotlin.math.roundToInt

/*
 * SAYI KULESİ — Ayarlar > Geliştirici araçları'ndan açılan mini oyun.
 * Gelen sayıyı soldaki 10 yuvaya küçükten büyüğe diz (dokun + sürükle).
 *
 * Kabuk: tema, cam yüzey dili, üst bar, sonuç katmanı.
 * Süre yok. Fail yalnızca yuvaya dokununca gelir.
 *
 * GÖRSEL DİL — liquid glass:
 *   - Yüzeyler yarı saydam cam: dikey yumuşak dolgu, üstte ince parlak rim, altta ikinci rim.
 *   - Çukur (boş yuva / sönük hücre) camın koyu, içe basık hali; kabartma (dolu) aynı camın parlak hali.
 *   - Yazı: uygulama fontu (RealFont/Inter), sentence case, mono / büyük harf etiket yok.
 *   - Temalar: BUZ (kurşuni gri-mavi zemin) ve AMOLED (saf siyah zemin, soğuk gri-mavi cam).
 *   - Tüm renk/ışık ayarı ArcadTheme içinde.
 *
 * Oyun yüzeyi tam ekrandır ve tüm dokunuşları yakalar.
 */

private const val ARCAD_SLOTS = 10
private const val ARCAD_MAX = 50
private const val ARCAD_PREF = "yasam_arcade"
private const val ARCAD_THEME_KEY = "theme"

/**
 * Tema: zemin (3 duraklı dikey geçiş + parıltı + parçacık), cam yüzey dolguları, rim ışıkları,
 * gölge, yanan hücre ve metin renkleri.
 */
private class ArcadTheme(
    val label: String,
    // Zemin
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val glow: Color,
    val glowX: Float,
    val glowY: Float,
    // Cam yüzeyler
    val glassTop: Color,
    val glassBottom: Color,
    val wellTop: Color,
    val wellBottom: Color,
    val panelTop: Color,
    val panelBottom: Color,
    val sheen: Color,
    val rimHi: Color,
    val rimMid: Color,
    val rimLo: Color,
    val shadow: Color,
    // Metin ve vurgu
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accentGlow: Color,
    val litTop: Color,
    val litBottom: Color,
    val scrim: Color
)

private val ArcadThemes = listOf(
    // BUZ — kurşuni gri-mavi zemin, ince beyaz rimli cam.
    ArcadTheme(
        label = "BUZ",
        bgTop = Color(0xFF7D8892), bgMid = Color(0xFF626D78), bgBottom = Color(0xFF454F59),
        glow = Color(0x55FFFFFF), glowX = 0.22f, glowY = 0.26f,
        glassTop = Color(0x1FFFFFFF), glassBottom = Color(0x38FFFFFF),
        wellTop = Color(0x3A1B222A), wellBottom = Color(0x121B222A),
        panelTop = Color(0x26FFFFFF), panelBottom = Color(0x12FFFFFF),
        sheen = Color(0x1AFFFFFF),
        rimHi = Color(0xE6FFFFFF), rimMid = Color(0x33FFFFFF), rimLo = Color(0x66222B33),
        shadow = Color(0x80262E36),
        ink = Color(0xFFF3F6F9), muted = Color(0xFFBAC4CD),
        accent = Color(0xFFE3F0FA), accentGlow = Color(0xFFBFDDF3),
        litTop = Color(0xCCEAF4FC), litBottom = Color(0x99B5D6EE),
        scrim = Color(0x99141A20)
    ),
    // AMOLED — saf siyah zemin; aynı cam dili, soğuk gri-mavi tonlarla. Parıltı/parçacık yok.
    ArcadTheme(
        label = "AMOLED",
        bgTop = Color(0xFF000000), bgMid = Color(0xFF000000), bgBottom = Color(0xFF000000),
        glow = Color(0x00000000), glowX = 0.5f, glowY = 0.2f,
        glassTop = Color(0x26FFFFFF), glassBottom = Color(0x0AFFFFFF),
        wellTop = Color(0x0FFFFFFF), wellBottom = Color(0x05FFFFFF),
        panelTop = Color(0x12FFFFFF), panelBottom = Color(0x06FFFFFF),
        sheen = Color(0x12FFFFFF),
        rimHi = Color(0x66FFFFFF), rimMid = Color(0x1AFFFFFF), rimLo = Color(0x24FFFFFF),
        shadow = Color(0xCC000000),
        ink = Color(0xFFEAEEF2), muted = Color(0xFF8B939E),
        accent = Color(0xFFB9C6D0), accentGlow = Color(0xFF8FA3B3),
        litTop = Color(0xD9E3EBF2), litBottom = Color(0xA6AEBFCC),
        scrim = Color(0xE6000000)
    )
)

private enum class ArcadStatus { Playing, Won, Lost }

private enum class ArcadGlass { Raised, Well, Panel }

/**
 * Cam yüzey.
 *  - Raised: kabartma cam (düğme, dolu yuva, disk, kart).
 *  - Well:   içe basık koyu cam (boş yuva, sönük hücre, direk).
 *  - Panel:  büyük, daha şeffaf zemin levhası.
 * Üstte ve altta parlak rim, üstte yumuşak ışık yansıması, altta ikinci bir yansıma; kabartmada gölge.
 */
private fun Modifier.glass(
    corner: Dp,
    theme: ArcadTheme,
    elevation: Dp = 0.dp,
    kind: ArcadGlass = ArcadGlass.Raised,
    tint: Color = Color.Transparent
): Modifier {
    val shape = RoundedCornerShape(corner)
    val fill = when (kind) {
        ArcadGlass.Raised -> Brush.verticalGradient(listOf(theme.glassTop, theme.glassBottom))
        ArcadGlass.Well -> Brush.verticalGradient(listOf(theme.wellTop, theme.wellBottom))
        ArcadGlass.Panel -> Brush.verticalGradient(listOf(theme.panelTop, theme.panelBottom))
    }
    val rim = when (kind) {
        ArcadGlass.Well -> Brush.verticalGradient(
            0f to theme.rimLo,
            0.55f to Color.Transparent,
            1f to theme.rimHi.copy(alpha = theme.rimHi.alpha * 0.55f)
        )
        else -> Brush.verticalGradient(
            0f to theme.rimHi,
            0.5f to theme.rimMid,
            1f to theme.rimHi.copy(alpha = theme.rimHi.alpha * 0.75f)
        )
    }
    val lifted = kind != ArcadGlass.Well && elevation > 0.dp
    val base = if (lifted) {
        shadow(elevation, shape, clip = false, ambientColor = theme.shadow, spotColor = theme.shadow)
    } else this
    return base
        .clip(shape)
        .background(fill)
        .then(if (tint.alpha > 0f) Modifier.background(tint) else Modifier)
        .drawBehind {
            if (kind == ArcadGlass.Well) {
                // İçe basık: üst kenardan düşen iç gölge.
                drawRect(Brush.verticalGradient(listOf(Color(0x40000000), Color.Transparent), endY = size.height * 0.4f))
            } else {
                // Üstte yumuşak ışık yansıması + altta ikinci yansıma (camın kalınlığı hissi).
                drawRect(Brush.verticalGradient(listOf(theme.sheen, Color.Transparent), endY = size.height * 0.45f))
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, theme.sheen.copy(alpha = theme.sheen.alpha * 0.8f)),
                        startY = size.height * 0.62f,
                        endY = size.height
                    )
                )
            }
        }
        .border(1.dp, rim, shape)
}

/** Uygulama hissiyle basınca hafif küçülen dokunma alanı. */
@Composable
private fun Modifier.arcadClickable(enabled: Boolean, reduceAnimations: Boolean, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    if (!enabled) return this
    val source = remember { MutableInteractionSource() }
    return this
        .pressScale(source, reduceAnimations = reduceAnimations, targetScale = 0.96f)
        .clickable(interactionSource = source, indication = null, role = role, onClick = onClick)
}

/* --------------------------------------------------------------------------------------------- */
/* Zemin + ikonlar                                                                               */
/* --------------------------------------------------------------------------------------------- */

/** Tam ekran zemin. Kendi katmanında çizilir (graphicsLayer): oyun animasyonları yeniden çizdirmesin. */
@Composable
private fun ArcadBackdrop(theme: ArcadTheme) {
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { }
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to theme.bgTop,
                        0.45f to theme.bgMid,
                        0.85f to theme.bgBottom,
                        1f to theme.bgBottom
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(theme.glow, Color.Transparent),
                        center = Offset(size.width * theme.glowX, size.height * theme.glowY),
                        radius = size.maxDimension * 0.7f
                    )
                )
            }
    )
}

/** Tema düğmesi: yarısı dolu daire. */
@Composable
private fun ArcadThemeIcon(color: Color, iconSize: Dp = 18.dp) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        drawCircle(color, radius = w * 0.36f, style = Stroke(width = w * 0.09f))
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.14f, w * 0.14f),
            size = Size(w * 0.72f, w * 0.72f)
        )
    }
}

/* --------------------------------------------------------------------------------------------- */
/* SAYI KULESİ                                                                                   */
/* --------------------------------------------------------------------------------------------- */

/** 1–50 arasından 10 farklı sayı; gelen sayı uygun yuvaya bırakılır. Fail yalnızca dokunuşta. */
private class ArcadNumbers {
    val slots = mutableStateListOf<Int?>().apply { repeat(ARCAD_SLOTS) { add(null) } }
    var current by mutableIntStateOf(0)
    var queue by mutableStateOf<List<Int>>(emptyList())
    var placed by mutableIntStateOf(0)
    var status by mutableStateOf(ArcadStatus.Playing)

    init { deal() }

    /** 1–50 arasından 10 farklı sayı çeker; ilki büyük kutuya, kalanı kuyruğa gider. */
    fun deal() {
        val nums = (1..ARCAD_MAX).shuffled().take(ARCAD_SLOTS)
        for (i in 0 until ARCAD_SLOTS) slots[i] = null
        current = nums.first()
        queue = nums.drop(1)
        placed = 0
        status = ArcadStatus.Playing
    }

    fun restart() = deal()

    /** [index] yuvasına [value] konabilir mi: en yakın dolu komşular sırayı korumalı. */
    fun canPlaceAt(index: Int, value: Int): Boolean {
        if (slots[index] != null) return false
        var l = index - 1
        while (l >= 0 && slots[l] == null) l--
        var r = index + 1
        while (r < ARCAD_SLOTS && slots[r] == null) r++
        val leftOk = l < 0 || slots[l]!! < value
        val rightOk = r >= ARCAD_SLOTS || value < slots[r]!!
        return leftOk && rightOk
    }

    /** Yalnızca oyuncu bir yuvaya bastığında çağrılır. Yanlışsa el burada kaybedilir; otomatik fail yok. */
    fun place(index: Int) {
        if (status != ArcadStatus.Playing || slots[index] != null) return
        val v = current
        if (!canPlaceAt(index, v)) {
            status = ArcadStatus.Lost
            return
        }
        slots[index] = v
        placed++
        if (placed >= ARCAD_SLOTS) {
            status = ArcadStatus.Won
            return
        }
        current = queue.first()
        queue = queue.drop(1)
    }
}

@Composable
private fun NumbersBoard(
    game: ArcadNumbers,
    theme: ArcadTheme,
    reduceAnimations: Boolean,
    shake: Animatable<Float, *>,
    modifier: Modifier
) {
    val slotBounds = remember { Array(ARCAD_SLOTS) { Rect.Zero } }
    var bigBounds by remember { mutableStateOf(Rect.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    // Yuvaya bırakılınca kutu eve anında döner (yeni sayı zaten yerinde belirir); boşa bırakılınca yaylanarak döner.
    var homeInstant by remember { mutableStateOf(false) }

    // Parmağın/kutunun altındaki yuva.
    val hover = if (dragging) {
        val p = bigBounds.center + dragOffset
        slotBounds.indexOfFirst { it != Rect.Zero && it.contains(p) }
    } else -1

    val homeSpec: AnimationSpec<Float> =
        if (dragging || homeInstant || reduceAnimations) snap() else spring(dampingRatio = 0.62f, stiffness = 380f)
    val offX by animateFloatAsState(if (dragging) dragOffset.x else 0f, homeSpec, label = "arcadBigX")
    val offY by animateFloatAsState(if (dragging) dragOffset.y else 0f, homeSpec, label = "arcadBigY")

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text(
                "${game.placed}/$ARCAD_SLOTS", color = theme.ink, fontSize = 13.sp,
                fontFamily = RealFont, fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().weight(1f)
                .graphicsLayer { translationX = shake.value * 12.dp.toPx() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.width(112.dp).fillMaxHeight().padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                for (i in 0 until ARCAD_SLOTS) {
                    val canPlace = game.slots[i] == null && game.canPlaceAt(i, game.current)
                    ArcadSlot(
                        index = i,
                        value = game.slots[i],
                        theme = theme,
                        dropTarget = dragging && canPlace,
                        hovered = hover == i && canPlace,
                        enabled = game.status == ArcadStatus.Playing,
                        reduceAnimations = reduceAnimations,
                        onTap = { game.place(i) },
                        onBounds = { slotBounds[i] = it },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ArcadBigBox(
                    value = game.current,
                    dragging = dragging,
                    theme = theme,
                    reduceAnimations = reduceAnimations,
                    modifier = Modifier
                        .onGloballyPositioned { bigBounds = it.boundsInRoot() }
                        .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
                        .pointerInput(game.status) {
                            if (game.status != ArcadStatus.Playing) return@pointerInput
                            detectDragGestures(
                                onDragStart = { homeInstant = false; dragging = true },
                                onDrag = { change, delta ->
                                    change.consume()
                                    dragOffset += delta
                                },
                                onDragEnd = {
                                    val pointer = bigBounds.center + dragOffset
                                    val idx = slotBounds.indexOfFirst { it != Rect.Zero && it.contains(pointer) }
                                    if (idx >= 0 && game.slots[idx] == null) {
                                        homeInstant = true
                                        game.place(idx)
                                    }
                                    dragging = false
                                    dragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    dragging = false
                                    dragOffset = Offset.Zero
                                }
                            )
                        }
                )
            }
        }
    }
}

@Composable
private fun ArcadSlot(
    index: Int,
    value: Int?,
    theme: ArcadTheme,
    dropTarget: Boolean,
    hovered: Boolean,
    enabled: Boolean,
    reduceAnimations: Boolean,
    onTap: () -> Unit,
    onBounds: (Rect) -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val filled = value != null
    // Yerleşen sayı küçük bir "pop" ile büyür; yuva bir an parlayıp söner.
    val pop = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(value, reduceAnimations) {
        if (value == null || reduceAnimations) { pop.snapTo(1f); return@LaunchedEffect }
        pop.snapTo(0.5f)
        pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f))
    }
    LaunchedEffect(value, reduceAnimations) {
        if (value == null || reduceAnimations) { flash.snapTo(0f); return@LaunchedEffect }
        flash.snapTo(1f)
        flash.animateTo(0f, tween(560))
    }
    val tintAlpha = when {
        hovered -> 0.32f
        dropTarget -> 0.12f
        else -> flash.value * 0.4f
    }
    Box(
        modifier
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            // Boş yuva çukur cam, dolu yuva kabartma cam; geçerli hedefler parlar, parmağın altındaki en çok.
            .glass(
                corner = 20.dp,
                theme = theme,
                elevation = if (filled) 6.dp else 0.dp,
                kind = if (filled) ArcadGlass.Raised else ArcadGlass.Well,
                tint = if (tintAlpha > 0.005f) theme.accent.copy(alpha = tintAlpha) else Color.Transparent
            )
            .then(
                when {
                    hovered -> Modifier.border(1.6.dp, theme.accent, shape)
                    dropTarget -> Modifier.border(1.dp, theme.accent.copy(alpha = 0.5f), shape)
                    else -> Modifier
                }
            )
            .arcadClickable(enabled = enabled, reduceAnimations = reduceAnimations) { onTap() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}",
                color = theme.muted.copy(alpha = if (filled) 0.9f else 0.65f),
                fontSize = 12.sp, fontFamily = RealFont
            )
            Spacer(Modifier.weight(1f))
            Text(
                value?.toString() ?: "–",
                color = if (filled) theme.ink else theme.muted.copy(alpha = 0.5f),
                fontSize = if (filled) 20.sp else 14.sp,
                fontFamily = RealFont,
                fontWeight = if (filled) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            )
        }
    }
}

/** Sıradaki sayı: sağdaki büyük cam kutu. Yukarıdan yaylanarak gelir, sürüklenirken hafif büyür. */
@Composable
private fun ArcadBigBox(value: Int, dragging: Boolean, theme: ArcadTheme, reduceAnimations: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(40.dp)

    // Sayı gelişi: hafif yukarıdan, yay (overshoot) ile yerine oturur.
    val appear = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    LaunchedEffect(value, reduceAnimations) {
        if (reduceAnimations) { appear.snapTo(1f); return@LaunchedEffect }
        appear.snapTo(0f)
        appear.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f))
    }
    // Sürekli çok hafif süzülme (nefes gibi yukarı-aşağı).
    val float = rememberInfiniteTransition(label = "arcadBigFloat").animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "arcadBigFloatValue"
    )
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1.08f else 1f,
        animationSpec = if (reduceAnimations) tween(0) else spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "arcadBigLift"
    )

    // Dış kutu genişliğe uyar (dar telefonda taşmaz); iç cam kutu onun %86'sı.
    Box(
        modifier.widthIn(max = 176.dp).fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize(0.86f)
                .graphicsLayer { scaleX = lift; scaleY = lift }
                .glass(40.dp, theme, if (dragging) 22.dp else 16.dp, tint = if (dragging) theme.accent.copy(alpha = 0.16f) else Color.Transparent)
                .then(if (dragging) Modifier.border(1.6.dp, theme.accent, shape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p.coerceAtMost(1.25f)) * -18.dp.toPx() +
                        (if (reduceAnimations) 0f else float.value * 3.dp.toPx())
                    val sc = 0.72f + 0.28f * p
                    scaleX = sc
                    scaleY = sc
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Sıradaki", color = theme.muted, fontSize = 13.sp, fontFamily = RealFont)
                Spacer(Modifier.height(6.dp))
                Text(
                    "$value", color = theme.ink, fontSize = 54.sp,
                    fontFamily = RealFont, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Kabuk — üst bar, sonuç katmanı                                                                */
/* --------------------------------------------------------------------------------------------- */

/**
 * Tam ekran Sayı Kulesi: üst bar, oyun, sonuç katmanı.
 * Dokunuşları yakalar; arkadaki içeriğe sızmaz.
 */
@Composable
internal fun YasamArcadeHost(reduceAnimations: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(ARCAD_PREF, Context.MODE_PRIVATE) }
    var themeIndex by rememberSaveable { mutableIntStateOf(prefs.getInt(ARCAD_THEME_KEY, 0)) }
    val theme = ArcadThemes[((themeIndex % ArcadThemes.size) + ArcadThemes.size) % ArcadThemes.size]

    val active = remember { ArcadNumbers() }

    // Yenilgide oyun alanı kısa bir titreme yapar (sönümlü).
    val shake = remember { Animatable(0f) }
    LaunchedEffect(active.status, reduceAnimations) {
        if (active.status == ArcadStatus.Lost && !reduceAnimations) {
            for (k in listOf(1f, -0.85f, 0.6f, -0.35f, 0.15f, 0f)) shake.animateTo(k, tween(70))
        } else if (active.status == ArcadStatus.Playing) {
            shake.snapTo(0f)
        }
    }

    Box(
        Modifier.fillMaxSize()
            // Tam ekran hedef: boş alanlara dokunma alttaki Yaşam kartlarına sızmasın.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Final)
                }
            }
    ) {
        ArcadBackdrop(theme)

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            // Üst bar: geri · oyun adı · tema düğmesi (yuvarlak cam düğmeler)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).glass(24.dp, theme, 8.dp)
                        .arcadClickable(enabled = true, reduceAnimations = reduceAnimations) { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    YasamIcon(YasamGlyph.Back, theme.ink, 18.dp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Sayı Kulesi", color = theme.ink,
                    fontSize = 17.sp, fontFamily = RealFont, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(48.dp).glass(24.dp, theme, 8.dp)
                        .arcadClickable(enabled = true, reduceAnimations = reduceAnimations) {
                            val next = (themeIndex + 1) % ArcadThemes.size
                            themeIndex = next
                            prefs.edit().putInt(ARCAD_THEME_KEY, next).apply()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    ArcadThemeIcon(theme.ink, 18.dp)
                }
            }
            Spacer(Modifier.height(20.dp))

            NumbersBoard(active, theme, reduceAnimations, shake, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(20.dp))
        }

        // Sonuç katmanı
        AnimatedVisibility(
            visible = active.status != ArcadStatus.Playing,
            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(220)),
            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(160)),
            modifier = Modifier.fillMaxSize()
        ) {
            val won = active.status == ArcadStatus.Won
            val label = if (won) "Tamamlandı" else "Sıra bozuldu"
            val headline = if (won) "Kule ayakta" else "Kule yine düşmedi"
            Box(Modifier.fillMaxSize().background(theme.scrim), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(horizontal = 34.dp)
                        .glass(34.dp, theme, 18.dp)
                        .padding(horizontal = 28.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, color = theme.muted, fontSize = 14.sp, fontFamily = RealFont)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        headline, color = theme.ink, fontSize = 24.sp,
                        fontWeight = FontWeight.Medium, fontFamily = RealFont
                    )
                    Spacer(Modifier.height(26.dp))
                    ArcadButton("Tekrar dene", primary = true, theme = theme, reduceAnimations = reduceAnimations) { active.restart() }
                    Spacer(Modifier.height(12.dp))
                    ArcadButton("Çık", primary = false, theme = theme, reduceAnimations = reduceAnimations, onClick = onClose)
                }
            }
        }
    }
}

@Composable
private fun ArcadButton(
    text: String,
    primary: Boolean,
    theme: ArcadTheme,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .glass(
                corner = 50.dp,
                theme = theme,
                elevation = if (primary) 8.dp else 0.dp,
                tint = if (primary) theme.accent.copy(alpha = 0.26f) else Color.Transparent
            )
            .then(if (primary) Modifier.border(1.2.dp, theme.accent.copy(alpha = 0.85f), shape) else Modifier)
            .arcadClickable(enabled = enabled, reduceAnimations = reduceAnimations, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = theme.ink,
            fontSize = 16.sp, fontFamily = RealFont, fontWeight = FontWeight.Medium
        )
    }
}
