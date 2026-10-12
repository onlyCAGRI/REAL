package com.cagri.real.ui.pano

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.boards.PanoBoard
import com.cagri.real.boards.PanoMember
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/* ---------------------------------------------------------------------------------------------
 * Pano gallery — "poster" look: big black type on flat, saturated cards (yellow / purple / lime /
 * magenta / white). Colours are picked pseudo-randomly from the board id, so a board always keeps
 * its colour but the list looks random. Neighbouring cards never share a colour.
 * ------------------------------------------------------------------------------------------- */

internal val PanoSand = Color(0xFFE6D9C3)
internal val PanoSage = Color(0xFFB2BFA6)
internal val PanoClay = Color(0xFFD29B83)
internal val PanoMist = Color(0xFFA9B9C9)
internal val PanoLilac = Color(0xFFB9AEC9)
internal val PanoIvory = Color(0xFFEFEAE0)
internal val PanoAccentWarm = Color(0xFFC4704F)
private val PanoInk = Color(0xFF151517)
internal val PanoCardPalette = listOf(PanoSand, PanoSage, PanoClay, PanoMist, PanoLilac, PanoIvory)
private val PanoInkDark = Color(0xFF1E1E20)
private val PanoDisplay = FontFamily.SansSerif

/** Stable "random" colour per board; adjacent cards in [ids] never get the same colour. */
internal fun panoCardColors(ids: List<String>): Map<String, Color> {
    val out = LinkedHashMap<String, Color>()
    var prev = -1
    ids.forEach { id ->
        var i = (id.hashCode() and Int.MAX_VALUE) % PanoCardPalette.size
        if (i == prev) i = (i + 1) % PanoCardPalette.size
        out[id] = PanoCardPalette[i]
        prev = i
    }
    return out
}

/** Label + "is me". Me first (shown as ME), then first names of everybody else. */
internal fun boardPeople(board: PanoBoard, members: List<PanoMember>, selfUid: String): List<Pair<String, Boolean>> {
    val all = (listOf(PanoMember(board.ownerUid, board.ownerName, "OWNER")) + members)
        .distinctBy { it.uid }.filter { it.name.isNotBlank() }
    return all.sortedByDescending { it.uid == selfUid }
        .map { if (it.uid == selfUid) "ME" to true else it.name.trim().substringBefore(' ') to false }
}

@Composable
private fun BigText(
    text: String, size: Int, modifier: Modifier = Modifier, color: Color = PanoInk,
    maxLines: Int = 3, strike: Boolean = false, weight: FontWeight = FontWeight.Normal
) = Text(
    text, modifier = modifier, color = color, fontSize = size.sp, lineHeight = (size * 0.92f).sp,
    letterSpacing = (-0.025f * size).sp, fontFamily = PanoDisplay, fontWeight = weight,
    maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None
)

/* ----------------------------------------- glyphs ---------------------------------------- */

@Composable
private fun PlusGlyph(color: Color, size: Dp = 22.dp, stroke: Dp = 2.6.dp, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) {
        val w = stroke.toPx(); val m = w / 2
        drawLine(color, Offset(size.toPx() / 2, m), Offset(size.toPx() / 2, size.toPx() - m), w, StrokeCap.Round)
        drawLine(color, Offset(m, size.toPx() / 2), Offset(size.toPx() - m, size.toPx() / 2), w, StrokeCap.Round)
    }

@Composable
private fun ArrowGlyph(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.width; val w = 2.6.dp.toPx()
    drawLine(color, Offset(s * .18f, s * .82f), Offset(s * .80f, s * .20f), w, StrokeCap.Round)
    drawLine(color, Offset(s * .28f, s * .18f), Offset(s * .82f, s * .18f), w, StrokeCap.Round)
    drawLine(color, Offset(s * .82f, s * .18f), Offset(s * .82f, s * .72f), w, StrokeCap.Round)
}

@Composable
private fun BackGlyph(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.width; val w = 2.6.dp.toPx()
    drawLine(color, Offset(s * .88f, s * .5f), Offset(s * .12f, s * .5f), w, StrokeCap.Round)
    drawLine(color, Offset(s * .45f, s * .15f), Offset(s * .10f, s * .5f), w, StrokeCap.Round)
    drawLine(color, Offset(s * .45f, s * .85f), Offset(s * .10f, s * .5f), w, StrokeCap.Round)
}

@Composable
private fun CrossGlyph(color: Color, size: Dp = 12.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.width; val w = 2.2.dp.toPx()
    drawLine(color, Offset(0f, 0f), Offset(s, s), w, StrokeCap.Round)
    drawLine(color, Offset(s, 0f), Offset(0f, s), w, StrokeCap.Round)
}

@Composable
private fun TickGlyph(color: Color, size: Dp = 14.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.width; val w = 2.2.dp.toPx()
    drawLine(color, Offset(s * .08f, s * .55f), Offset(s * .38f, s * .85f), w, StrokeCap.Round)
    drawLine(color, Offset(s * .38f, s * .85f), Offset(s * .95f, s * .15f), w, StrokeCap.Round)
}

/* ----------------------------------------- header ---------------------------------------- */

/** Back chevron ········ big +   /   WEEKDAY DAY   /   PANO• */
@Composable
internal fun PanoGalleryHeader(onBack: () -> Unit, onAdd: () -> Unit) {
    val today = remember { LocalDate.now() }
    val dayName = remember {
        today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()).uppercase(Locale.getDefault())
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.cagri.real.ui.components.RealBackButton(onBack)
            Spacer(Modifier.weight(1f))
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val rot by animateFloatAsState(if (pressed) 90f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "plus")
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(source, null, onClick = onAdd), contentAlignment = Alignment.Center) {
                PlusGlyph(Color.White, 28.dp, 2.8.dp, Modifier.graphicsLayer { rotationZ = rot })
            }
        }
        Text("$dayName ${today.dayOfMonth}", color = Color.White, fontSize = 13.sp, letterSpacing = 0.4.sp,
            fontFamily = PanoDisplay, modifier = Modifier.padding(top = 14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BigText("PANO", 56, color = Color.White, maxLines = 1)
            Box(Modifier.padding(start = 5.dp, bottom = 15.dp).size(8.dp).clip(CircleShape).background(PanoAccentWarm))
        }
    }
}

@Composable
internal fun PanoSectionLabel(text: String) = Text(
    text.uppercase(), color = PanoMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.sp, modifier = Modifier.padding(start = 6.dp, top = 10.dp)
)

/* ------------------------------------------ card ----------------------------------------- */

/** Month (big) over day (small), exactly like the hour/minute stack in the reference. */
@Composable
private fun DateColumn(millis: Long, ink: Color) {
    val date = remember(millis) {
        if (millis > 0) Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() else LocalDate.now()
    }
    Column(Modifier.width(30.dp).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(date.monthValue.toString(), color = ink, fontSize = 22.sp, lineHeight = 22.sp, fontFamily = PanoDisplay)
        Text("%02d".format(date.dayOfMonth), color = ink.copy(alpha = 0.65f), fontSize = 11.sp, lineHeight = 12.sp, fontFamily = PanoDisplay)
        Box(Modifier.padding(top = 6.dp).width(1.dp).height(16.dp).background(ink.copy(alpha = 0.45f)))
    }
}

/** Tap = open. Long press = stacked options panel. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PanoBoardCard(
    board: PanoBoard, people: List<Pair<String, Boolean>>, color: Color, onOpen: () -> Unit, onPeek: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    val ink = PanoInk
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(30.dp)).background(color)
            .combinedClickable(interactionSource = source, indication = null, onClick = onOpen,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onPeek() })
            .padding(start = 16.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)
    ) {
        DateColumn(board.updatedAt, ink)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val title = board.title.uppercase()
            BigText(title, if (title.length > 14) 30 else 40, color = ink)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                people.take(3).forEach { (label, me) ->
                    Text(label.uppercase(), color = if (me) ink else ink.copy(alpha = 0.45f), fontSize = 11.sp,
                        fontWeight = if (me) FontWeight.SemiBold else FontWeight.Normal, letterSpacing = 0.3.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (people.size > 3) Text("+${people.size - 3}", color = ink.copy(alpha = 0.45f), fontSize = 11.sp)
            }
        }
    }
}

/* --------------------------------- long-press stacked panel ------------------------------ */

@Composable
private fun StackCard(top: Int, height: Int, bg: Color, progress: () -> Float, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().offset(y = top.dp).height(height.dp)
            .graphicsLayer { translationY = (1f - progress()) * 1400f }
            .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)).background(bg),
        content = content
    )
}

@Composable
private fun StackRowHead(title: String, ink: Color, strike: Boolean, onClick: () -> Unit, pill: @Composable BoxScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(46.dp).height(27.dp).border(2.dp, ink, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center, content = pill)
            ArrowGlyph(ink)
        }
        Spacer(Modifier.height(16.dp))
        BigText(title, 34, color = ink, maxLines = 1, strike = strike)
    }
}

/**
 * Full-screen panel shown on long press: board colour on top with the title, and three stacked,
 * overlapping cards (open / delete-or-leave / people) that slide up one after another.
 */
@Composable
internal fun PanoBoardPeek(
    board: PanoBoard, color: Color, peopleCount: Int,
    onOpen: () -> Unit, onPeople: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val anim = remember { List(3) { Animatable(0f) } }
        var headerShown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            headerShown = true
            anim.forEachIndexed { i, a ->
                launch { delay(120L + i * 90L); a.animateTo(1f, spring(0.82f, Spring.StiffnessMediumLow)) }
            }
        }
        val owner = board.role == "OWNER"
        val accent = if (color == PanoSand) PanoSage else PanoSand
        Box(Modifier.fillMaxSize().background(color)) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        com.cagri.real.ui.components.RealBackButton(onDismiss, tint = PanoInk)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.border(1.6.dp, PanoInk, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text("$peopleCount ${if (peopleCount == 1) "PERSON" else "PEOPLE"}", color = PanoInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    AnimatedVisibility(headerShown, enter = fadeIn(tween(300))) {
                        val t = board.title.uppercase()
                        BigText(t, if (t.length > 10) 54 else 72, maxLines = 3)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(if (board.shared) "SHARED" else "PRIVATE", color = PanoInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
                        Text(if (owner) "OWNER" else "MEMBER", color = PanoInk.copy(alpha = 0.45f), fontSize = 12.sp, letterSpacing = 0.4.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(480.dp)) {
                    // 1 — open (white)
                    StackCard(0, 230, Color.White, { anim[0].value }) {
                        StackRowHead("OPEN BOARD", PanoInk, false, onOpen) {}
                    }
                    // 2 — delete / leave (dark, struck through like a finished task)
                    StackCard(112, 230, PanoInkDark, { anim[1].value }) {
                        StackRowHead(if (owner) "DELETE BOARD" else "LEAVE BOARD", Color(0xFF8A8A90), true, onRemove) {
                            CrossGlyph(PanoDanger)
                        }
                    }
                    // 3 — people (accent) with the big black bar
                    StackCard(224, 256, accent, { anim[2].value }) {
                        StackRowHead("PEOPLE", PanoInk, false, onPeople) {}
                        Row(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 10.dp)
                                .navigationBarsPadding().height(58.dp).clip(RoundedCornerShape(29.dp)).background(PanoInk)
                                .clickable(onClick = onPeople),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (owner) "INVITE PERSON" else "SEE PEOPLE", color = Color.White, fontSize = 16.sp,
                                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Box(Modifier.width(1.5.dp).fillMaxHeight().background(accent))
                            Box(Modifier.width(66.dp).fillMaxHeight(), contentAlignment = Alignment.Center) { PlusGlyph(Color.White, 22.dp) }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------- "+" new board -------------------------------------- */

private val UpperCaseTransformation = VisualTransformation { t ->
    TransformedText(AnnotatedString(t.text.uppercase()), OffsetMapping.Identity)
}

/** Full-screen creator that blooms out of the "+" in the top-right corner. */
@Composable
internal fun PanoNewBoardOverlay(onDismiss: () -> Unit, onCreate: (title: String, shared: Boolean) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var shown by remember { mutableStateOf(false) }
        var title by remember { mutableStateOf("") }
        var shared by remember { mutableStateOf(false) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { shown = true; delay(380); runCatching { focus.requestFocus() } }
        val ready = title.isNotBlank()
        AnimatedVisibility(
            shown,
            enter = scaleIn(tween(460, easing = FastOutSlowInEasing), initialScale = 0.05f, transformOrigin = TransformOrigin(0.92f, 0.05f)) + fadeIn(tween(220))
        ) {
            Box(Modifier.fillMaxSize().background(PanoSand)) {
                Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        com.cagri.real.ui.components.RealBackButton(onDismiss, tint = PanoInk)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.border(1.6.dp, PanoInk, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text("NEW", color = PanoInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    BigText("NEW\nBOARD", 76, maxLines = 2)
                    Spacer(Modifier.height(26.dp))
                    Box(Modifier.fillMaxWidth()) {
                        if (title.isEmpty()) BigText("NAME IT", 36, color = PanoInk.copy(alpha = 0.22f), maxLines = 1)
                        BasicTextField(
                            title, { title = it.take(80) }, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            textStyle = TextStyle(color = PanoInk, fontSize = 36.sp, lineHeight = 34.sp,
                                letterSpacing = (-0.9).sp, fontFamily = PanoDisplay),
                            cursorBrush = SolidColor(PanoInk), visualTransformation = UpperCaseTransformation,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (ready) onCreate(title.trim(), shared) })
                        )
                    }
                    Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(1.5.dp).background(PanoInk.copy(alpha = 0.85f)))
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(false to "PRIVATE", true to "SHARED").forEach { (value, label) ->
                            val on = shared == value
                            val bg by animateColorAsState(if (on) PanoInk else Color.Transparent, tween(220), label = "pill")
                            val fg by animateColorAsState(if (on) Color.White else PanoInk, tween(220), label = "pillText")
                            Box(Modifier.clip(RoundedCornerShape(22.dp)).background(bg)
                                .border(1.6.dp, PanoInk, RoundedCornerShape(22.dp))
                                .clickable { shared = value }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                                Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    val rot by animateFloatAsState(if (ready) 90f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "rot")
                    val barAlpha by animateFloatAsState(if (ready) 1f else 0.35f, tween(200), label = "alpha")
                    Row(
                        Modifier.fillMaxWidth().height(60.dp).graphicsLayer { alpha = barAlpha }
                            .clip(RoundedCornerShape(30.dp)).background(PanoInk)
                            .clickable(enabled = ready) { onCreate(title.trim(), shared) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("CREATE BOARD", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        Box(Modifier.width(1.5.dp).fillMaxHeight().background(PanoAccentWarm))
                        Box(Modifier.width(68.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            PlusGlyph(Color.White, 22.dp, modifier = Modifier.graphicsLayer { rotationZ = rot })
                        }
                    }
                }
            }
        }
    }
}
