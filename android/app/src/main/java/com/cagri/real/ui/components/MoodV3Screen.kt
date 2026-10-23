package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.cagri.real.data.JournalMood
import com.cagri.real.data.MoodDetails
import com.cagri.real.data.MoodScope
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

/** Moods V3: the whole screen takes the mood's colour; [ink] is the face, text and controls on it. */
private class MoodV3Tone(val paper: Color, val ink: Color)

private val MoodV3Tones = listOf(
    MoodV3Tone(Color(0xFFFF8A7A), Color(0xFF5A1712)),   // Kötü: salmon
    MoodV3Tone(Color(0xFFFFB45A), Color(0xFF5A2E05)),   // Zor: orange
    MoodV3Tone(Color(0xFFF2D96B), Color(0xFF4A3C08)),   // Normal: sand
    MoodV3Tone(Color(0xFFC8E86A), Color(0xFF2E3D07)),   // İyi: lime
    MoodV3Tone(Color(0xFF8EE6A0), Color(0xFF0E3B1C)),   // Çok iyi: mint
)

private fun toneAt(progress: Float): Pair<Color, Color> {
    val p = progress.coerceIn(0f, 4f)
    val left = floor(p).toInt().coerceIn(0, 4)
    val right = (left + 1).coerceAtMost(4)
    val t = p - left
    return lerp(MoodV3Tones[left].paper, MoodV3Tones[right].paper, t) to lerp(MoodV3Tones[left].ink, MoodV3Tones[right].ink, t)
}

/**
 * Moods V3 (same data as V2, same save): one full-colour screen — a big face that morphs as the slider moves, the
 * feeling in large letters, and "Kaydet" right there. Details (what describes it, what caused it) are one optional
 * step away under "Ayrıntı ekle". "Şu an / Bugün" is a small switch instead of a first screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodV3FullScreen(
    selectedMood: JournalMood,
    onMoodSelected: (JournalMood) -> Unit,
    onDismiss: () -> Unit,
    onSave: (JournalMood, MoodDetails) -> Unit,
    initialDetails: MoodDetails? = null,
    reminderContextLabel: String? = null,
    reduceAnimations: Boolean = false
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scopeCoroutine = rememberCoroutineScope()
    val isReminderMoodFlow = reminderContextLabel != null
    val progress = remember { Animatable(selectedMood.familyIndexV2().toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var lastTick by remember { mutableIntStateOf(selectedMood.familyIndexV2()) }
    var details by remember(initialDetails?.loggedAt) { mutableStateOf(false) }
    var scope by remember(initialDetails?.loggedAt, reminderContextLabel) {
        mutableStateOf(initialDetails?.scope ?: MoodScope.MOMENT)
    }
    var descriptorKeys by remember(initialDetails?.loggedAt) {
        val restored = initialDetails?.descriptors?.toSet() ?: emptySet()
        mutableStateOf(if (selectedMood == JournalMood.FUCK_OFF) restored + "fuck_off" else restored)
    }
    var factorKeys by remember(initialDetails?.loggedAt) { mutableStateOf(initialDetails?.factors?.toSet() ?: emptySet()) }
    val originalLoggedAt = remember(initialDetails?.loggedAt) { initialDetails?.loggedAt ?: System.currentTimeMillis() }

    LaunchedEffect(selectedMood) {
        if (!dragging) progress.snapTo(selectedMood.familyIndexV2().toFloat())
    }

    val family = progress.value.roundToInt().coerceIn(0, 4)
    val (paperTarget, inkTarget) = toneAt(progress.value)
    val paper by animateColorAsState(paperTarget, if (reduceAnimations) snap() else tween(90), label = "moodPaper")
    val ink by animateColorAsState(inkTarget, if (reduceAnimations) snap() else tween(90), label = "moodInk")

    // Dark status bar icons on the bright paper; restored on the way out.
    val activity = context as? Activity ?: (view.context as? Activity)
    val insets = remember(activity, view) { activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) } }
    DisposableEffect(insets) {
        val prevStatus = insets?.isAppearanceLightStatusBars ?: false
        val prevNav = insets?.isAppearanceLightNavigationBars ?: false
        insets?.isAppearanceLightStatusBars = true
        insets?.isAppearanceLightNavigationBars = true
        onDispose {
            insets?.isAppearanceLightStatusBars = prevStatus
            insets?.isAppearanceLightNavigationBars = prevNav
        }
    }

    fun settle() {
        val anchor = progress.value.roundToInt().coerceIn(0, 4)
        scopeCoroutine.launch {
            if (reduceAnimations) progress.snapTo(anchor.toFloat())
            else progress.animateTo(anchor.toFloat(), spring(dampingRatio = 0.7f, stiffness = 380f))
        }
        val target = MoodFamiliesV2[anchor]
        val valid = MoodDescriptorsV2[anchor].orEmpty().map { it.key }.toSet()
        descriptorKeys = descriptorKeys.intersect(valid)
        if (selectedMood !in target.moods) onMoodSelected(target.defaultMood)
    }

    fun save() = onSave(
        if (selectedMood == JournalMood.FUCK_OFF) JournalMood.BAD else selectedMood,
        MoodDetails(
            scope = scope,
            descriptors = descriptorKeys.toList(),
            // Energy is not asked; an older value is kept when editing.
            energy = initialDetails?.energy,
            factors = factorKeys.toList(),
            loggedAt = originalLoggedAt
        )
    )

    BackHandler { if (details) details = false else onDismiss() }

    Box(Modifier.fillMaxSize().background(paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Top: close on the left, the Şu an / Bugün switch on the right (not in a card's mood flow).
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = t("Kapat")) {
                    if (details) details = false else onDismiss()
                }, contentAlignment = Alignment.Center) {
                    Text(if (details) "‹" else "×", color = ink, fontSize = 28.sp, lineHeight = 28.sp)
                }
                Spacer(Modifier.weight(1f))
                if (!isReminderMoodFlow) {
                    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(ink.copy(alpha = 0.10f)).padding(3.dp)) {
                        listOf(MoodScope.MOMENT to t("Şu an"), MoodScope.DAILY to t("Bugün")).forEach { (value, label) ->
                            val on = scope == value
                            Text(label, color = if (on) paper else ink.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.clip(RoundedCornerShape(17.dp)).background(if (on) ink else Color.Transparent)
                                    .clickable(role = Role.Button) { scope = value; RealHaptics.tick(view) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp))
                        }
                    }
                }
            }

            AnimatedContent(details, modifier = Modifier.weight(1f), label = "moodV3Step",
                transitionSpec = {
                    if (reduceAnimations) fadeIn(snap()) togetherWith fadeOut(snap())
                    else (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 10 }) togetherWith fadeOut(tween(160))
                }) { showDetails ->
                if (!showDetails) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                isReminderMoodFlow -> t("Bu REAL nasıl hissettirdi?")
                                scope == MoodScope.MOMENT -> t("Şu an nasılsın?")
                                else -> t("Bugün genel olarak nasıldı?")
                            },
                            color = ink, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 18.dp)
                        )
                        Spacer(Modifier.weight(0.6f))
                        val showHand = family == 0 && "fuck_off" in descriptorKeys
                        Crossfade(showHand, animationSpec = if (reduceAnimations) snap() else tween(200), label = "moodV3Hand") { hand ->
                            if (hand) Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                                ActionIcon(ActionGlyph.MoodFuckOff, Modifier.size(200.dp), ink)
                            } else MoodV3Face(faceParamsAtV2(progress.value, if (dragging) null else selectedMood), ink, 300.dp,
                                t("{0} mood yüzü", MoodFamiliesV2[family].title))
                        }
                        Spacer(Modifier.weight(0.4f))
                        // The feeling in big, soft letters; a new word slides in from the side the mood moved to.
                        AnimatedContent(family, label = "moodV3Title", transitionSpec = {
                            if (reduceAnimations) fadeIn(snap()) togetherWith fadeOut(snap())
                            else {
                                val dir = if (targetState > initialState) 1 else -1
                                (slideInHorizontally(tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it * dir / 2 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(tween(260)) { -it * dir / 2 } + fadeOut(tween(160)))
                            }
                        }) { index ->
                            Text(MoodFamiliesV2[index].title.uppercase(com.cagri.real.i18n.I18n.locale), color = ink.copy(alpha = 0.82f),
                                fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1,
                                modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(26.dp))
                        MoodV3Slider(progress.value, ink, paper,
                            onTap = { target ->
                                // Kötü → Çok iyi in one tap: glide through the faces in between, ticking at each stop.
                                val anchor = target.roundToInt().coerceIn(0, 4)
                                dragging = true
                                scopeCoroutine.launch {
                                    val distance = kotlin.math.abs(anchor - progress.value)
                                    if (reduceAnimations) progress.snapTo(anchor.toFloat())
                                    else progress.animateTo(anchor.toFloat(), tween((260 + distance * 170).toInt(),
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing)) {
                                        val passed = value.roundToInt().coerceIn(0, 4)
                                        if (passed != lastTick) { lastTick = passed; RealHaptics.tick(view) }
                                    }
                                    dragging = false
                                    settle()
                                }
                            },
                            onStart = { dragging = true },
                            onMove = { value ->
                                scopeCoroutine.launch { progress.snapTo(value) }
                                val anchor = value.roundToInt().coerceIn(0, 4)
                                if (anchor != lastTick) { lastTick = anchor; RealHaptics.tick(view) }
                            },
                            onEnd = { dragging = false; settle() })
                        Spacer(Modifier.height(30.dp))
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            MoodV3Face(faceParamsAtV2(family.toFloat(), selectedMood), ink, 54.dp, t("{0} mood yüzü", MoodFamiliesV2[family].title))
                            Spacer(Modifier.width(12.dp))
                            Text(MoodFamiliesV2[family].title, color = ink, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        }
                        Text(t("Bunu en iyi ne anlatır?"), color = ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 26.dp))
                        Text(t("En fazla 3 tane"), color = ink.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MoodDescriptorsV2[family].orEmpty().forEach { o ->
                                val on = o.key in descriptorKeys
                                MoodV3Chip(o.label, on, ink, paper) {
                                    descriptorKeys = when { on -> descriptorKeys - o.key; descriptorKeys.size < 3 -> descriptorKeys + o.key; else -> descriptorKeys }
                                    RealHaptics.tick(view)
                                }
                            }
                        }
                        Text(t("Ne etkiledi?"), color = ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 30.dp))
                        Text(t("Hissin kaynağı; birden fazla olabilir"), color = ink.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        MoodFactorGroupsV2.forEach { group ->
                            Text(group.title, color = ink.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.options.forEach { o ->
                                    val on = o.key in factorKeys
                                    MoodV3Chip(o.label, on, ink, paper) { factorKeys = if (on) factorKeys - o.key else factorKeys + o.key; RealHaptics.tick(view) }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // Bottom: "Ayrıntı ekle" (optional) and "Kaydet", always one tap away.
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!details) {
                    val count = descriptorKeys.size + factorKeys.size
                    Box(Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).border(1.5.dp, ink.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                        .clickable(role = Role.Button) { details = true }, contentAlignment = Alignment.Center) {
                        Text(if (count > 0) t("Ayrıntılar · {0}", count) else t("Ayrıntı ekle"), color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Box(Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(ink)
                    .clickable(role = Role.Button) { RealHaptics.tick(view); save() }, contentAlignment = Alignment.Center) {
                    Text(t("Kaydet"), color = paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The V2 face drawn chunkier and in ink: bigger eyes, a heavier mouth. */
@Composable
private fun MoodV3Face(params: FaceV2Params, ink: Color, size: Dp, description: String) {
    Canvas(Modifier.size(size).semantics { contentDescription = description }) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.075f
        val eyeW = w * params.eyeWidth * 1.25f
        val eyeH = h * params.eyeHeight * 1.25f
        val eyeY = h * params.eyeY
        // Wide flat eyes (Normal, Zor) never touch: they keep a clear gap instead of reading as one brow.
        val spacing = maxOf(w * params.eyeSpacing * 1.12f, eyeW * 1.45f)
        val cx = w / 2f
        fun eye(x: Float) = drawRoundRect(ink, Offset(x - eyeW / 2f, eyeY - eyeH / 2f), Size(eyeW, eyeH), CornerRadius(eyeH / 2f, eyeH / 2f))
        eye(cx - spacing / 2f); eye(cx + spacing / 2f)
        if (params.browStrength > 0.05f) {
            val browY = eyeY - eyeH * 1.1f
            val half = eyeW * 0.5f
            val tilt = eyeH * 0.7f * params.browStrength
            drawLine(ink, Offset(cx - spacing / 2f - half, browY - tilt), Offset(cx - spacing / 2f + half, browY + tilt), strokeWidth = stroke * 0.6f, cap = StrokeCap.Round)
            drawLine(ink, Offset(cx + spacing / 2f - half, browY + tilt), Offset(cx + spacing / 2f + half, browY - tilt), strokeWidth = stroke * 0.6f, cap = StrokeCap.Round)
        }
        val my = h * params.mouthY
        val half = w * params.mouthWidth / 2f
        val curve = h * 0.13f * params.mouthCurve
        val mouth = Path().apply {
            moveTo(cx - half, my)
            cubicTo(cx - half * 0.45f, my + curve, cx + half * 0.45f, my + curve, cx + half, my)
        }
        drawPath(mouth, ink, style = Stroke(width = stroke * params.mouthStrokeScale, cap = StrokeCap.Round))
    }
}

/** Five stops, Kötü → Çok iyi; the knob is a small face that follows the mood. */
@Composable
private fun MoodV3Slider(value: Float, ink: Color, paper: Color, onTap: (Float) -> Unit, onStart: () -> Unit, onMove: (Float) -> Unit, onEnd: () -> Unit) {
    val density = LocalDensity.current
    val knob = 44.dp
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(knob)) {
            val width = constraints.maxWidth.toFloat()
            val knobPx = with(density) { knob.toPx() }
            val usable = (width - knobPx).coerceAtLeast(1f)
            fun at(x: Float) = (((x - knobPx / 2f) / usable) * 4f).coerceIn(0f, 4f)
            Box(Modifier.fillMaxSize()
                .pointerInput(usable) { detectTapGestures { onTap(at(it.x)) } }
                .pointerInput(usable) {
                    var current = value
                    detectHorizontalDragGestures(
                        onDragStart = { onStart(); current = at(it.x); onMove(current) },
                        onDragEnd = onEnd, onDragCancel = onEnd
                    ) { change, delta -> change.consume(); current = (current + delta / usable * 4f).coerceIn(0f, 4f); onMove(current) }
                }) {
                // Track with five dots.
                Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = knob / 2).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.22f)))
                Row(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = knob / 2 - 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(5) { Box(Modifier.size(6.dp).clip(CircleShape).background(ink.copy(alpha = 0.35f))) }
                }
                Box(Modifier.offset { IntOffset((value / 4f * usable).roundToInt(), 0) }.size(knob).clip(CircleShape).background(ink),
                    contentAlignment = Alignment.Center) {
                    MoodV3Face(faceParamsAtV2(value, null), paper, 30.dp, "")
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(t("Kötü"), color = ink.copy(alpha = 0.6f), fontSize = 12.sp)
            Text(t("Normal"), color = ink.copy(alpha = 0.6f), fontSize = 12.sp)
            Text(t("Çok iyi"), color = ink.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun MoodV3Chip(label: String, selected: Boolean, ink: Color, paper: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(label, color = if (selected) paper else ink, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(shape)
            .then(if (selected) Modifier.background(ink) else Modifier.border(1.dp, ink.copy(alpha = 0.35f), shape))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp))
}
