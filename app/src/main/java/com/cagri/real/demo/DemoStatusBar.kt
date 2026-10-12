package com.cagri.real.demo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.components.LocalLiquidReduceMotion

private enum class Presence(val label: String, val color: Color, val glyph: Color) {
    Online("Çevrimiçi", Color(0xFF34C759), Color.White),
    Idle("Boşta", Color(0xFFEDB007), Color(0xFF3A2C00)),
    Dnd("Rahatsız Etme", Color(0xFFFF3B30), Color.White),
    Invisible("Görünmez", Color(0xFFD6D6DB), Color(0xFF7C7C84))
}

private val tabLabels = listOf("Üyeler", "Sohbetler", "Etkinlik")
private val blue = Color(0xFF2E7CF6)
private val ink = Color(0xFF1C1C1E)
private val subInk = Color(0xFF8A8A92)
private val chipGrey = Color(0xFFF0F0F4)
private val chipH = 52.dp
private val chipGap = 8.dp

/** Bulanıklaşarak girip çıkan içerik: v = 0..1. 0'da görünmez ve bulanık, 1'de net. */
private fun Modifier.softVisible(v: Float, maxBlur: Dp = 8.dp): Modifier {
    val k = v.coerceIn(0f, 1f)
    return this
        .blur(maxBlur * (1f - k), BlurredEdgeTreatment.Unbounded)
        .graphicsLayer { alpha = k }
}

/**
 * Video 2: alt çubuk tek bir yapı olarak şekil değiştirir.
 *  0 = sekme çubuğu (+ sağda iki hesap çipi)
 *  1 = "Etkinlik"e uzun basınca: zil + açık hesap hapı
 *  2 = hesap hapına dokununca: hesap listesi açılır, çip daralıp daire olur, yeni hap genişler
 * Durum çipine dokununca durum menüsü çıkar. Yerel, veri bağımsız.
 */
@Composable
internal fun DemoStatusBar(modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(2) }
    var stage by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var statusIndex by rememberSaveable { mutableIntStateOf(3) }
    val status = Presence.values()[statusIndex]
    val reduceMotion = LocalLiquidReduceMotion.current
    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = stage > 0 || menuOpen) {
        if (menuOpen) menuOpen = false else stage = (stage - 1).coerceAtLeast(0)
    }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Basılı tut, çubuk açılsın.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Etkinlik sekmesine uzun bas. REAL Mag hapına dokun, hesaplar açılsın. Durum çipine dokun, durumunu seç. " +
                "Zile dokununca çubuk kapanır.",
            color = Color(0xFFAAAAB1), fontSize = 12.sp
        )

        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(36.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFF8F5F9), Color(0xFFE8E5EE))))
        ) {
            val listAlpha by animateFloatAsStateCompat(if (stage == 2) 0.45f else 1f, reduceMotion)
            BackdropList(Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = listAlpha })

            val scrim by animateColorAsState(
                if (menuOpen) Color.Black.copy(alpha = 0.10f) else Color.Transparent,
                if (reduceMotion) tween(0) else tween(180), label = "scrim"
            )
            if (menuOpen) {
                Box(
                    Modifier.matchParentSize().background(scrim)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { menuOpen = false }
                )
            }

            val containerColor by animateColorAsState(
                if (stage == 0) Color.White.copy(alpha = 0.94f) else Color(0xFFEAE8EE),
                if (reduceMotion) tween(0) else tween(260), label = "barContainer"
            )
            Column(
                Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x22000000), spotColor = Color(0x33000000))
                    .clip(RoundedCornerShape(32.dp)).background(containerColor)
            ) {
                AccountsSheet(visible = stage == 2, reduceMotion = reduceMotion)
                BarRow(
                    stage = stage, tab = tab, status = status, reduceMotion = reduceMotion,
                    onTab = { tab = it },
                    onOpenBar = {
                        tab = 2
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        stage = 1
                    },
                    onBell = { menuOpen = false; stage = 0 },
                    onAccountA = { menuOpen = false; stage = if (stage == 1) 2 else 1 },
                    onAccountB = { if (stage == 2) menuOpen = !menuOpen else stage = 2 }
                )
            }

            StatusMenu(
                visible = menuOpen && stage == 2,
                selectedIndex = statusIndex,
                reduceMotion = reduceMotion,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 84.dp),
                onPick = { index ->
                    statusIndex = index
                    menuOpen = false
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )
        }
    }
}

@Composable
private fun animateFloatAsStateCompat(target: Float, reduceMotion: Boolean): State<Float> =
    androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) tween(0) else tween(280),
        label = "statusFloat"
    )

/** Ayrı fonksiyon: AnimatedVisibility, Column/Box kapsamı karışmadan çağrılsın diye burada. */
@Composable
private fun AccountsSheet(visible: Boolean, reduceMotion: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) EnterTransition.None else
            expandVertically(spring(dampingRatio = 0.86f, stiffness = 300f)) + fadeIn(tween(220, delayMillis = 70)),
        exit = if (reduceMotion) ExitTransition.None else
            shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(120))
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
            Text("Diğer hesaplar", color = subInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            AccountRow("EM", "Ece Marie", Brush.linearGradient(listOf(Color(0xFFF2C46B), Color(0xFFD9954A))))
            Spacer(Modifier.height(10.dp))
            AccountRow("Ç", "Çağrı", Brush.linearGradient(listOf(Color(0xFF8E9BFF), Color(0xFF5B6CF0))))
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AccountRow(initials: String, name: String, brush: Brush) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(initials, brush, 30.dp)
        Spacer(Modifier.width(12.dp))
        Text(name, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Alt çubuğun tamamı: tüm çipler sürekli ekranda kalır, genişlikleri yaylanarak değişir. */
@Composable
private fun BarRow(
    stage: Int,
    tab: Int,
    status: Presence,
    reduceMotion: Boolean,
    onTab: (Int) -> Unit,
    onOpenBar: () -> Unit,
    onBell: () -> Unit,
    onAccountA: () -> Unit,
    onAccountB: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp, end = 8.dp)) {
        val total = maxWidth
        val c = chipH
        val g = chipGap
        val wide = total - c * 2 - g * 3                // sekme hapı ve durum hapı
        val wideA = total - c - g * 2                   // sadece zil + açık hesap hapı

        fun widthSpec() = if (reduceMotion) tween<Dp>(0) else spring<Dp>(dampingRatio = 0.84f, stiffness = 300f)

        val tabW by animateDpAsState(if (stage == 0) wide else 0.dp, widthSpec(), label = "tabW")
        val bellW by animateDpAsState(if (stage >= 1) c else 0.dp, widthSpec(), label = "bellW")
        val aW by animateDpAsState(
            when (stage) { 0 -> c; 1 -> wideA; else -> c }, widthSpec(), label = "aW"
        )
        val bW by animateDpAsState(
            when (stage) { 0 -> c; 1 -> 0.dp; else -> wide }, widthSpec(), label = "bW"
        )
        val chipColor by animateColorAsState(
            if (stage == 0) chipGrey else Color.White,
            if (reduceMotion) tween(0) else tween(260), label = "chipColor"
        )

        fun gapOf(w: Dp): Dp = g * (w / 14.dp).coerceIn(0f, 1f)

        Row(Modifier.fillMaxWidth().height(c)) {
            // 1) Sekme hapı
            val tw = tabW.coerceAtLeast(0.dp)
            Box(
                Modifier.padding(start = gapOf(tw)).width(tw).height(c)
                    .clip(RoundedCornerShape(26.dp)).background(chipColor)
            ) {
                TabsContent(
                    selected = tab, fullWidth = wide,
                    visible = (tw / wide).coerceIn(0f, 1f),
                    onSelect = onTab, onOpenBar = onOpenBar
                )
            }
            // 2) Zil
            val bw = bellW.coerceAtLeast(0.dp)
            Box(
                Modifier.padding(start = gapOf(bw)).width(bw).height(c).clip(CircleShape).background(chipColor)
                    .semantics { contentDescription = "Etkinlik, çubuğu kapat" }
                    .clickable(enabled = stage >= 1, role = Role.Button, onClick = onBell),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.softVisible((bw / c).coerceIn(0f, 1f))) { TabGlyph(2, ink, 22.dp) }
            }
            // 3) Hesap A (REAL Mag): hap <-> daire
            val aw = aW.coerceAtLeast(c)
            val pillA = ((aw - c) / (wideA - c)).coerceIn(0f, 1f)
            Box(
                Modifier.padding(start = gapOf(aw)).width(aw).height(c)
                    .clip(RoundedCornerShape(26.dp)).background(chipColor)
                    .semantics { contentDescription = "REAL Mag hesabı" }
                    .clickable(role = Role.Button, onClick = onAccountA)
            ) {
                Row(
                    Modifier.wrapContentWidth(Alignment.Start, unbounded = true).fillMaxHeight().padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MagAvatar(36.dp)
                    Column(Modifier.padding(start = 10.dp).softVisible((pillA - 0.45f) / 0.55f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("REAL Mag", color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            Spacer(Modifier.width(4.dp))
                            VerifiedBadge(13.dp)
                        }
                        Text("342 üye", color = subInk, fontSize = 11.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
            // 4) Hesap B (Çağrı): daire <-> durum hapı
            val bwB = bW.coerceAtLeast(0.dp)
            val shownB = (bwB / c).coerceIn(0f, 1f)
            val pillB = ((bwB - c) / (wide - c)).coerceIn(0f, 1f)
            Box(
                Modifier.padding(start = gapOf(bwB)).width(bwB).height(c)
                    .clip(RoundedCornerShape(26.dp)).background(chipColor)
                    .semantics { contentDescription = "Durum: ${status.label}. Değiştirmek için dokun" }
                    .clickable(enabled = shownB > 0.5f, role = Role.Button, onClick = onAccountB)
            ) {
                Row(
                    Modifier.wrapContentWidth(Alignment.Start, unbounded = true).fillMaxHeight().padding(start = 8.dp)
                        .softVisible(shownB),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Avatar("Ç", Brush.linearGradient(listOf(Color(0xFF8E9BFF), Color(0xFF5B6CF0))), 36.dp)
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(13.dp)
                                .graphicsLayer { alpha = 1f - pillB }
                                .clip(CircleShape).background(status.color).border(2.dp, chipColor, CircleShape)
                        )
                    }
                    Column(Modifier.padding(start = 10.dp).softVisible((pillB - 0.45f) / 0.55f)) {
                        Text("Çağrı", color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        BlurSwapText(status.label, subInk, 11.sp)
                    }
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).softVisible((pillB - 0.6f) / 0.4f)
                ) { StatusCircle(status, 36.dp) }
            }
        }
    }
}

@Composable
private fun TabsContent(
    selected: Int,
    fullWidth: Dp,
    visible: Float,
    onSelect: (Int) -> Unit,
    onOpenBar: () -> Unit
) {
    val itemW = (fullWidth - 8.dp) / 3
    val indicatorX by animateDpAsState(itemW * selected, spring(dampingRatio = 0.8f, stiffness = 420f), label = "tabIndicator")
    Box(
        Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(fullWidth).fillMaxHeight()
            .softVisible((visible - 0.55f) / 0.45f)
            .padding(4.dp)
    ) {
        Box(
            Modifier.offset(x = indicatorX).width(itemW).fillMaxHeight()
                .clip(RoundedCornerShape(24.dp)).background(blue.copy(alpha = 0.10f))
        )
        Row(Modifier.fillMaxSize()) {
            tabLabels.forEachIndexed { index, label ->
                val isSelected = index == selected
                val tint by animateColorAsState(if (isSelected) blue else Color(0xFF3C3C43), tween(180), label = "tabTint")
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                        .pointerInput(index) {
                            detectTapGestures(
                                onTap = { onSelect(index) },
                                onLongPress = { if (index == 2) onOpenBar() }
                            )
                        }
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            this.selected = isSelected
                            onClick(label = label) { onSelect(index); true }
                            if (index == 2) onLongClick(label = "Durum çubuğunu aç") { onOpenBar(); true }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    TabGlyph(index, tint, 22.dp)
                    Spacer(Modifier.height(2.dp))
                    Text(label, color = tint, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

/** Yazı değişirken eski yazı bulanıklaşıp söner, yenisi bulanıktan netleşir. */
@Composable
private fun BlurSwapText(text: String, color: Color, fontSize: TextUnit) {
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn(tween(240, delayMillis = 90)) togetherWith fadeOut(tween(140)) },
        label = "swapText"
    ) { value ->
        val k by transition.animateFloat(
            transitionSpec = { tween(260) }, label = "swapBlur"
        ) { state -> if (state == EnterExitState.Visible) 0f else 1f }
        Text(
            value, color = color, fontSize = fontSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            modifier = Modifier.blur(7.dp * k, BlurredEdgeTreatment.Unbounded)
        )
    }
}

/** Sağdaki durum dairesi: renk yumuşakça değişir, simge çapraz geçer, değişince küçük bir zıplama yapar. */
@Composable
private fun StatusCircle(status: Presence, size: Dp) {
    val fill by animateColorAsState(status.color, tween(260), label = "statusFill")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(status) {
        pop.snapTo(0.8f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 520f))
    }
    Box(
        Modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(CircleShape).background(fill),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = status, animationSpec = tween(220), label = "statusGlyph") { p ->
            Canvas(Modifier.size(size)) { drawGlyph(p) }
        }
    }
}

@Composable
private fun StatusMenu(
    visible: Boolean,
    selectedIndex: Int,
    reduceMotion: Boolean,
    modifier: Modifier,
    onPick: (Int) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduceMotion) EnterTransition.None else
            scaleIn(initialScale = 0.4f, transformOrigin = TransformOrigin(1f, 1f),
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f)) + fadeIn(tween(140)),
        exit = if (reduceMotion) ExitTransition.None else
            scaleOut(targetScale = 0.7f, transformOrigin = TransformOrigin(1f, 1f),
                animationSpec = tween(170)) + fadeOut(tween(140))
    ) {
        Column(
            Modifier.width(208.dp)
                .shadow(18.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x33000000), spotColor = Color(0x44000000))
                .clip(RoundedCornerShape(26.dp)).background(Color(0xFFFAFAFC)).padding(8.dp)
        ) {
            Presence.values().forEachIndexed { index, p ->
                val selected = index == selectedIndex
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(if (selected) Color(0x10000000) else Color.Transparent)
                        .selectable(selected, role = Role.RadioButton) { onPick(index) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(p.color)) {
                        Canvas(Modifier.size(26.dp)) { drawGlyph(p) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(p.label, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun BackdropList(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ROLLER", color = Color(0xFF8A8A92), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        listOf("Yönetici" to 2, "Yayıncı" to 3, "Müdür" to 3).forEach { (name, count) ->
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    val tints = listOf(Color(0xFFF2A07B), Color(0xFF8E9BFF), Color(0xFF7BD0A5))
                    repeat(count) { i ->
                        Box(Modifier.size(26.dp).clip(CircleShape).background(tints[i % 3]).border(2.dp, Color.White, CircleShape))
                    }
                }
            }
        }
        Text("+ Yeni ekle", color = blue, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
    }
}

@Composable
private fun Avatar(initials: String, brush: Brush, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Videodaki "nyt mag" yuvarlağı gibi: beyaz daire, sıkı dizilmiş siyah serif harfler. */
@Composable
private fun MagAvatar(size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.White).border(1.dp, Color(0xFFE2E2E7), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 10.sp,
                lineHeight = 9.sp, letterSpacing = (-0.3).sp, color = Color.Black
            )
            Text("real", style = style)
            Text("mag", style = style)
        }
    }
}

@Composable
private fun VerifiedBadge(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawCircle(blue)
        val check = Path().apply {
            moveTo(w * 0.28f, w * 0.52f); lineTo(w * 0.44f, w * 0.67f); lineTo(w * 0.74f, w * 0.34f)
        }
        drawPath(check, Color.White, style = Stroke(w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 0 = üyeler, 1 = sohbetler, 2 = etkinlik (zil). Hepsi Canvas ile çizilir. */
@Composable
private fun TabGlyph(kind: Int, tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        when (kind) {
            0 -> {
                drawCircle(tint, w * 0.2f, Offset(w * 0.5f, h * 0.32f))
                drawArc(tint, 180f, 180f, true, Offset(w * 0.15f, h * 0.58f), Size(w * 0.7f, h * 0.8f))
            }
            1 -> {
                drawRoundRect(tint, Offset(w * 0.08f, h * 0.14f), Size(w * 0.84f, h * 0.62f), CornerRadius(h * 0.3f))
                drawPath(Path().apply {
                    moveTo(w * 0.26f, h * 0.68f); lineTo(w * 0.18f, h * 0.92f); lineTo(w * 0.46f, h * 0.74f); close()
                }, tint)
            }
            else -> {
                drawArc(tint, 180f, 180f, true, Offset(w * 0.2f, h * 0.12f), Size(w * 0.6f, h * 0.6f))
                drawPath(Path().apply {
                    moveTo(w * 0.2f, h * 0.42f); lineTo(w * 0.8f, h * 0.42f)
                    lineTo(w * 0.92f, h * 0.74f); lineTo(w * 0.08f, h * 0.74f); close()
                }, tint)
                drawCircle(tint, w * 0.1f, Offset(w * 0.5f, h * 0.87f))
            }
        }
    }
}

/** Yalnız simge (zemin dışarıda çizilir). */
private fun DrawScope.drawGlyph(p: Presence) {
    val w = size.width
    val h = size.height
    val c = p.glyph
    when (p) {
        Presence.Online -> {
            drawCircle(c, w * 0.14f, Offset(w * 0.5f, h * 0.38f))
            drawArc(c, 180f, 180f, true, Offset(w * 0.27f, h * 0.55f), Size(w * 0.46f, h * 0.5f))
        }
        Presence.Idle -> {
            drawRoundRect(c, Offset(w * 0.3f, h * 0.27f), Size(w * 0.14f, h * 0.46f), CornerRadius(w * 0.04f))
            drawRoundRect(c, Offset(w * 0.56f, h * 0.27f), Size(w * 0.14f, h * 0.46f), CornerRadius(w * 0.04f))
        }
        Presence.Dnd -> drawRoundRect(c, Offset(w * 0.26f, h * 0.44f), Size(w * 0.48f, h * 0.12f), CornerRadius(w * 0.06f))
        Presence.Invisible -> {
            drawOval(c, Offset(w * 0.2f, h * 0.33f), Size(w * 0.6f, h * 0.34f), style = Stroke(w * 0.07f))
            drawCircle(c, w * 0.07f, Offset(w * 0.5f, h * 0.5f))
        }
    }
}
