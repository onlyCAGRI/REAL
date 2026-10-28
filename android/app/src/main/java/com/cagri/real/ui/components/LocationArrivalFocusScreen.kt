package com.cagri.real.ui.components

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cagri.real.data.ChecklistItem
import com.cagri.real.data.LocationTrigger
import com.cagri.real.data.ReminderItem
import com.cagri.real.i18n.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * "Varınca / Ayrılınca" focus screen, opened from the arrival / departure notification.
 *
 * 1. Vardın anı: the real map (OpenFreeMap snapshot + the Worker route) fills the screen, the route draws in, the
 *    place pulses and its name rises. A tap skips it. The map then shrinks into a strip at the top.
 * 2. The card's checklist one item at a time, Tinder-style: swipe right (or Tamamla) completes, left (or Geç) skips;
 *    the next items wait behind, smaller and blurred. A card without a checklist shows its text as one item (Tamam).
 * 3. Finale: ring, tick and burst, then the screen shrinks into a dot and closes by itself.
 */
data class LocationTriggerNotificationContext(
    val reminderId: Long,
    val placeName: String,
    val onExit: Boolean
)

private enum class FocusPhase { Intro, List, Finale, Closing }

private val FocusBackground = Color(0xFF07080A)
private val ArrivalAccent = Color(0xFF5DCAA5)
private val DepartureAccent = Color(0xFFFFB86F)
private val FocusRise = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)
private val MapCollapse = CubicBezierEasing(0.65f, 0f, 0.25f, 1f)
private val CardFly = CubicBezierEasing(0.5f, 0f, 0.75f, 0.6f)

private class FocusItem(val id: Long, val text: String)

@Composable
fun LocationArrivalFocusScreen(
    reminder: ReminderItem,
    triggerContext: LocationTriggerNotificationContext,
    onToggleChecklistItem: (Long, Long) -> Unit,
    onOpenFullCard: () -> Unit,
    onDismiss: () -> Unit,
    /** The card's category colour: a soft glow from the bottom of the screen. */
    categoryColor: Color? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val onExit = triggerContext.onExit
    val accent = if (onExit) DepartureAccent else ArrivalAccent

    val noteMode = !(reminder.isChecklist && reminder.checklistItems.isNotEmpty())
    // Items done before the screen opened stay out of the deck (they still count in the progress line).
    val initiallyDone = remember(reminder.id) { reminder.checklistItems.filter { it.isCompleted }.mapTo(HashSet()) { it.id } }
    val noteText = remember(reminder.id) { reminder.cardStack?.cards?.firstOrNull { it.body.isNotBlank() }?.body?.trim() }
    val deck = if (noteMode) listOf(FocusItem(-1L, noteText ?: reminder.text))
    else reminder.checklistItems.filter { it.id !in initiallyDone }.map { FocusItem(it.id, it.text) }
    val currentDeck by rememberUpdatedState(deck)
    val toggle by rememberUpdatedState(onToggleChecklistItem)

    // true = completed in this session, false = skipped ("Geç": stays open on the card).
    val status = remember(reminder.id) { mutableStateMapOf<Long, Boolean>() }
    val history = remember(reminder.id) { mutableStateListOf<Long>() }
    val queue = deck.filter { it.id !in status }
    var phase by remember { mutableStateOf(FocusPhase.Intro) }
    val frontDrag = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }

    val placeLabel = triggerContext.placeName.ifBlank {
        reminder.locationTrigger?.placeName?.takeIf { it.isNotBlank() }
            ?: reminder.location?.placeName?.takeIf { it.isNotBlank() }
            ?: t("Seçilen Konum")
    }
    val title = if (noteMode && noteText == null) placeLabel else reminder.text
    val chipText = if (onExit) t("Ayrıldın · {0}", placeLabel) else t("Vardın · {0}", placeLabel)
    val eyebrow = if (onExit) t("AYRILDIN") else t("VARDIN")
    val extraSummary = remember(reminder) {
        val parts = mutableListOf<String>()
        if (reminder.attachments.isNotEmpty()) parts.add(t("{0} fotoğraf", reminder.attachments.size))
        if (reminder.voiceRecordings.isNotEmpty()) parts.add(t("{0} ses kaydı", reminder.voiceRecordings.size))
        if (!noteMode && reminder.cardStack != null && reminder.cardStack.cards.isNotEmpty()) parts.add(t("{0} alt kart", reminder.cardStack.cards.size))
        if (!reminder.referenceUrl.isNullOrBlank()) parts.add(t("bağlantı"))
        if (parts.isEmpty()) null else t("Kartta {0} var", parts.joinToString(" · "))
    }

    fun closeThen(action: () -> Unit) {
        if (phase == FocusPhase.Closing) return
        phase = FocusPhase.Closing
        scope.launch { delay(520); action() }
    }
    BackHandler {
        if (phase == FocusPhase.Intro) phase = FocusPhase.List else closeThen(onDismiss)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fullH = maxHeight
            val wPx = constraints.maxWidth
            val hPx = constraints.maxHeight
            val exitPx = wPx * 1.25f
            val thresholdPx = with(density) { 90.dp.toPx() }

            val commit: (Boolean) -> Unit = commit@{ done ->
                if (busy || phase != FocusPhase.List) return@commit
                val front = currentDeck.firstOrNull { it.id !in status } ?: return@commit
                busy = true
                armed = false
                if (done) RealHaptics.success(view) else RealHaptics.tick(view)
                scope.launch {
                    frontDrag.animateTo(if (done) exitPx else -exitPx, tween(320, easing = CardFly))
                    status[front.id] = done
                    history.add(front.id)
                    if (done && !noteMode) toggle(reminder.id, front.id)
                    frontDrag.snapTo(0f)
                    busy = false
                    if (currentDeck.none { it.id !in status }) {
                        delay(180)
                        phase = FocusPhase.Finale
                        delay(2_300)
                        closeThen(onDismiss)
                    }
                }
            }
            val latestCommit by rememberUpdatedState(commit)
            val undo: () -> Unit = undo@{
                if (busy || phase != FocusPhase.List) return@undo
                val last = history.removeLastOrNull() ?: return@undo
                if (status.remove(last) == true && !noteMode) toggle(reminder.id, last)
                RealHaptics.tick(view)
            }

            // Real map, rendered at the full screen size so the same picture fills the intro and then the strip.
            var realMap by remember(reminder.id, onExit) { mutableStateOf<RealIntroMap?>(null) }
            var mapResolved by remember(reminder.id, onExit) { mutableStateOf(false) }
            val mapProgress = remember { Animatable(0f) }
            LaunchedEffect(reminder.id, onExit, wPx, hPx) {
                realMap = withTimeoutOrNull(3_500L) { RealIntroMapLoader.load(context, reminder, onExit, wPx, hPx) }
                mapResolved = true
            }
            LaunchedEffect(realMap) {
                if (realMap != null) {
                    mapProgress.snapTo(0f)
                    mapProgress.animateTo(1f, tween(2_300, easing = LinearEasing))
                }
            }
            // The arrival moment waits up to 1.2 s for the map, then gives it time to draw; without one it is short.
            LaunchedEffect(reminder.id) {
                val start = System.currentTimeMillis()
                while (realMap == null && !mapResolved && System.currentTimeMillis() - start < 1_200L) delay(50)
                delay(if (realMap != null) 2_150L else 1_400L)
                if (phase == FocusPhase.Intro) phase = FocusPhase.List
            }

            val intro = phase == FocusPhase.Intro
            val closeP by animateFloatAsState(
                if (phase == FocusPhase.Closing) 1f else 0f,
                tween(500, easing = CubicBezierEasing(0.6f, 0f, 0.4f, 1f)), label = "focusClose"
            )
            val stripH = 230.dp
            val stripPx = with(density) { stripH.toPx() }
            val mapH by animateDpAsState(if (intro) fullH else stripH, tween(750, easing = MapCollapse), label = "mapH")
            // The full-size map slides up so the place sits in the middle of the strip.
            val destY = realMap?.destination?.y ?: (hPx / 2f)
            val shift by animateFloatAsState(
                if (intro) 0f else (stripPx / 2f - destY).coerceIn(stripPx - hPx, 0f),
                tween(750, easing = MapCollapse), label = "mapShift"
            )
            // Once the list is up the strip turns into the arrived place seen from close above.
            var closeUp by remember(reminder.id) { mutableStateOf<RealIntroMap?>(null) }
            LaunchedEffect(reminder.id, wPx) {
                closeUp = withTimeoutOrNull(8_000L) { RealIntroMapLoader.loadCloseUp(context, reminder, wPx, stripPx.toInt()) }
            }
            val closeShown by animateFloatAsState(
                if (!intro && closeUp != null) 1f else 0f,
                tween(520, delayMillis = if (!intro) 450 else 0), label = "closeUp"
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f - 0.98f * closeP
                        scaleX = s; scaleY = s
                        alpha = 1f - closeP
                        transformOrigin = TransformOrigin(0.5f, 0.62f)
                        shape = RoundedCornerShape(percent = (closeP * 50f).toInt())
                        clip = closeP > 0f
                    }
                    .background(FocusBackground)
            ) {
                if (categoryColor != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2f, size.height + size.width * 0.15f)
                        val r = size.width * 1.05f
                        drawCircle(Brush.radialGradient(listOf(categoryColor.copy(alpha = 0.22f), Color.Transparent), c, r), r, c)
                    }
                }

                // Map: full screen in the intro, then a strip at the top.
                Box(Modifier.fillMaxWidth().height(mapH).clip(RectangleShape)) {
                    Box(Modifier.fillMaxWidth().height(fullH).graphicsLayer { translationY = shift; alpha = 1f - closeShown }) {
                        val map = realMap
                        if (map != null) RealMapIntroCanvas(map, mapProgress.value, Modifier.fillMaxSize(), lineColor = accent)
                        else MapLoadingPulse(Modifier.fillMaxSize())
                    }
                    closeUp?.let {
                        PlaceCloseUpCanvas(it, accent, Modifier.fillMaxWidth().height(stripH).graphicsLayer { alpha = closeShown })
                    }
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                0f to FocusBackground.copy(alpha = 0.55f), 0.26f to Color.Transparent,
                                0.55f to Color.Transparent, 1f to FocusBackground
                            )
                        )
                    )
                    if (realMap != null || closeUp != null) Text(
                        "© OpenStreetMap", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 10.dp)
                    )
                }

                // Intro words over the full map.
                val introText by animateFloatAsState(
                    if (intro) 1f else 0f,
                    tween(if (intro) 520 else 220, delayMillis = if (intro) 750 else 0, easing = FocusRise), label = "introText"
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 28.dp, end = 28.dp, bottom = 110.dp)
                        .graphicsLayer { alpha = introText; translationY = (1f - introText) * 18.dp.toPx() * (if (intro) 1f else -1.6f) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(8.dp))
                        Text(eyebrow, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(placeLabel, color = Color.White, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (noteMode) title else t("{0} · {1} madde seni bekliyor", reminder.text, deck.size),
                        color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }

                // Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                        .riseIn(!intro, 0),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        Modifier
                            .weight(1f, fill = false)
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF14161A).copy(alpha = 0.7f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                            .padding(start = 10.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIcon(ActionGlyph.Pin, Modifier.size(16.dp), accent)
                        Spacer(Modifier.width(8.dp))
                        Text(chipText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF14161A).copy(alpha = 0.7f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                            .clickable { closeThen(onDismiss) }
                            .semantics { contentDescription = t("Kapat") },
                        contentAlignment = Alignment.Center
                    ) { ActionIcon(ActionGlyph.Close, Modifier.size(16.dp), Color.White) }
                }

                Column(Modifier.fillMaxSize().padding(top = 196.dp).navigationBarsPadding()) {
                    // Title + progress
                    Column(Modifier.padding(horizontal = 24.dp).riseIn(!intro, 1)) {
                        Text(eyebrow, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(title, color = Color.White, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!noteMode) {
                            Spacer(Modifier.height(14.dp))
                            val all = reminder.checklistItems
                            val frontId = queue.firstOrNull()?.id
                            val doneCount = all.count { it.isCompleted || status[it.id] == true }
                            val skipCount = all.count { status[it.id] == false }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    all.forEach { item ->
                                        val target = when {
                                            item.isCompleted || status[item.id] == true -> Color.White
                                            status[item.id] == false -> Color.White.copy(alpha = 0.3f)
                                            item.id == frontId -> accent
                                            else -> Color.White.copy(alpha = 0.14f)
                                        }
                                        val c by animateColorAsState(target, tween(300), label = "seg")
                                        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(c))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "${if (queue.isEmpty()) all.size else min(all.size, doneCount + skipCount + 1)}/${all.size}",
                                    color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Deck / finale
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp).riseIn(!intro, 2)) {
                        if (phase == FocusPhase.Finale || phase == FocusPhase.Closing) {
                            val skipped = status.values.count { !it }
                            FocusFinale(
                                accent = accent,
                                title = if (skipped > 0) t("Liste bitti") else t("Hepsi tamam"),
                                text = when {
                                    skipped > 0 -> t("{0} madde işaretlenmeden kartta bekliyor.", skipped)
                                    noteMode -> t("Hatırlatma kapandı.")
                                    else -> t("{0} listesi bitti.", placeLabel)
                                },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            deck.forEachIndexed { index, item ->
                                key(item.id) {
                                    val st = status[item.id]
                                    val pos = queue.indexOfFirst { it.id == item.id }
                                    val front = pos == 0 && phase == FocusPhase.List
                                    val baseX = remember { Animatable(0f) }
                                    LaunchedEffect(st) {
                                        if (st != null) baseX.snapTo(if (st) exitPx else -exitPx)
                                        else baseX.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 380f))
                                    }
                                    val slot by animateFloatAsState(
                                        if (pos < 0) 0f else pos.toFloat(),
                                        spring(dampingRatio = 0.62f, stiffness = 340f), label = "deckSlot"
                                    )
                                    val k = if (front) (frontDrag.value / thresholdPx).coerceIn(-1f, 1f) else 0f
                                    FocusDeckCard(
                                        text = item.text,
                                        foot = if (noteMode) (if (onExit) t("Ayrılınca hatırlat") else t("Varınca hatırlat")) else t("{0}. madde", index + 1),
                                        k = k,
                                        front = front,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 56.dp)
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .zIndex(if (pos < 0) 0f else 30f - pos)
                                            .graphicsLayer {
                                                val x = if (st != null) (if (st) exitPx else -exitPx)
                                                else baseX.value + (if (pos == 0) frontDrag.value else 0f)
                                                translationX = x
                                                rotationZ = x / this.density / 22f
                                                val a = slot.coerceIn(0f, 1f)
                                                val b = (slot - 1f).coerceIn(0f, 1f)
                                                val c = (slot - 2f).coerceIn(0f, 1f)
                                                transformOrigin = TransformOrigin(0.5f, 0f)
                                                translationY = -(26f * a + 22f * b + 12f * c) * this.density
                                                val sc = 1f - 0.08f * a - 0.08f * b - 0.06f * c
                                                scaleX = sc; scaleY = sc
                                                alpha = if (st != null) 0f else (1f - 0.4f * a - 0.3f * b - 0.3f * c).coerceAtLeast(0f)
                                                val blur = (2.5f * a + 2.5f * b) * this.density
                                                renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0.3f)
                                                    BlurEffect(blur, blur, TileMode.Decal) else null
                                            }
                                            .then(
                                                if (front) Modifier.pointerInput(item.id) {
                                                    fun release() {
                                                        val v = frontDrag.value
                                                        when {
                                                            v > thresholdPx -> latestCommit(true)
                                                            v < -thresholdPx && !noteMode -> latestCommit(false)
                                                            else -> {
                                                                armed = false
                                                                scope.launch { frontDrag.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
                                                            }
                                                        }
                                                    }
                                                    detectHorizontalDragGestures(
                                                        onDragEnd = { release() },
                                                        onDragCancel = { release() }
                                                    ) { change, amount ->
                                                        if (busy) return@detectHorizontalDragGestures
                                                        change.consume()
                                                        val next = frontDrag.value + amount * (if (noteMode && frontDrag.value + amount < 0f) 0.25f else 1f)
                                                        val nowArmed = abs(next) >= thresholdPx && (next > 0f || !noteMode)
                                                        if (nowArmed != armed) { armed = nowArmed; RealHaptics.tick(view) }
                                                        scope.launch { frontDrag.snapTo(next) }
                                                    }
                                                } else Modifier
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // Actions
                    if (phase == FocusPhase.Intro || phase == FocusPhase.List) {
                        Column(
                            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp).riseIn(!intro, 3),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (extraSummary != null) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                        .clickable { closeThen(onOpenFullCard) }
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ActionIcon(ActionGlyph.Photo, Modifier.size(16.dp), Color.White.copy(alpha = 0.8f))
                                    Spacer(Modifier.width(8.dp))
                                    Text(extraSummary, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(t("Kartı gör"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    Modifier
                                        .height(60.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                        .clickable { if (noteMode) closeThen(onOpenFullCard) else commit(false) }
                                        .padding(horizontal = 22.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!noteMode) {
                                        ActionIcon(ActionGlyph.ChevronRight, Modifier.size(18.dp), Color.White)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(if (noteMode) t("Kartı aç") else t("Geç"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable { commit(true) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    ActionIcon(ActionGlyph.Check, Modifier.size(20.dp), FocusBackground)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (noteMode) t("Tamam") else t("Tamamla"), color = FocusBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().height(44.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (history.isNotEmpty()) {
                                    Row(
                                        Modifier.fillMaxHeight().clip(RoundedCornerShape(12.dp)).clickable(onClick = undo).padding(horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ActionIcon(ActionGlyph.Undo, Modifier.size(16.dp), Color.White.copy(alpha = 0.72f))
                                        Spacer(Modifier.width(6.dp))
                                        Text(t("Geri al"), color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    Spacer(Modifier.width(1.dp))
                                }
                                if (!noteMode) Text(
                                    t("Kartı aç"), color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { closeThen(onOpenFullCard) }.padding(horizontal = 6.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }

                // During the arrival moment a tap anywhere goes straight to the list.
                if (intro) Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(50f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { phase = FocusPhase.List }
                        .semantics { contentDescription = t("Listeye geç") }
                )
            }
        }
    }
}

/** Fades and lifts a block in after the arrival moment, one after another by [index]. */
@Composable
private fun Modifier.riseIn(visible: Boolean, index: Int): Modifier {
    val r by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(if (visible) 520 else 150, delayMillis = if (visible) 350 + index * 70 else 0, easing = FocusRise),
        label = "riseIn"
    )
    return graphicsLayer { alpha = r; translationY = (1f - r) * 28.dp.toPx() }
}

/** One item of the deck: [k] is the swipe toward Tamamla (+1) or Geç (-1); past the line a white tick springs in. */
@Composable
private fun FocusDeckCard(text: String, foot: String, k: Float, front: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(30.dp)
    val border = when {
        front && k > 0.2f -> Color.White
        front && k < -0.2f -> Color.White.copy(alpha = 0.4f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val stamp by animateFloatAsState(
        if (front && k > 0.85f) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = 600f), label = "stamp"
    )
    Box(
        modifier
            .clip(shape)
            .background(if (front && k > 0.2f) Color(0xFF22242A) else Color(0xFF181A1F))
            .border(1.5.dp, border, shape)
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 22.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(t("GEÇ"), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp,
                    modifier = Modifier.alpha(if (front) max(0f, -k) else 0f))
                Text(t("TAMAMLA"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp,
                    modifier = Modifier.alpha(if (front) max(0f, k) else 0f))
            }
            Spacer(Modifier.height(10.dp))
            Text(text, color = Color.White, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text(foot, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(52.dp)
                .graphicsLayer { scaleX = stamp; scaleY = stamp }
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) { ActionIcon(ActionGlyph.Check, Modifier.size(28.dp), FocusBackground) }
    }
}

/** All handled: a white ring draws, the tick follows, dots burst out, then the words rise. */
@Composable
private fun FocusFinale(accent: Color, title: String, text: String, modifier: Modifier = Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1_450, easing = LinearEasing)) }
    val words by animateFloatAsState(if (p.value > 0.38f) 1f else 0f, tween(500, easing = FocusRise), label = "finaleWords")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(200.dp)) {
            val v = p.value
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = 48.dp.toPx()
            val stroke = 5.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.1f), r, c, style = Stroke(stroke))
            val ring = (v / 0.48f).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) * (1f - it) }
            drawArc(
                Color.White, -90f, 360f * ring, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            val tick = ((v - 0.34f) / 0.24f).coerceIn(0f, 1f)
            if (tick > 0f) {
                val u = 1.dp.toPx()
                val path = Path().apply {
                    moveTo(c.x - 20 * u, c.y + 1 * u); lineTo(c.x - 6 * u, c.y + 15 * u); lineTo(c.x + 21 * u, c.y - 14 * u)
                }
                val part = Path()
                PathMeasure().apply { setPath(path, false) }.let { it.getSegment(0f, it.length * tick, part, true) }
                drawPath(part, Color.White, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val burst = ((v - 0.38f) / 0.62f).coerceIn(0f, 1f)
            if (burst > 0f && burst < 1f) {
                val e = 1f - (1f - burst) * (1f - burst)
                for (n in 0 until 8) {
                    val a = n * PI.toFloat() / 4f
                    val dist = (if (n % 2 == 0) 94f else 74f).dp.toPx() * e
                    val alpha = if (burst < 0.2f) burst / 0.2f else 1f - (burst - 0.2f) / 0.8f
                    drawCircle(
                        (if (n % 2 == 0) accent else Color.White).copy(alpha = alpha),
                        4.dp.toPx() * (1f - 0.6f * burst),
                        Offset(c.x + cos(a) * dist, c.y + sin(a) * dist)
                    )
                }
            }
        }
        Column(
            Modifier.graphicsLayer { alpha = words; translationY = (1f - words) * 14.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(text, color = Color.White.copy(alpha = 0.66f), fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp))
        }
    }
}

@Composable
fun LocationArrivalDeveloperTestSection(
    existingReminders: List<ReminderItem>,
    onOpenCardDetail: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showStudioDialog by rememberSaveable { mutableStateOf(false) }
    var activeFocusSession by remember { mutableStateOf<Pair<Long, LocationTriggerNotificationContext>?>(null) }
    var localRemindersSnapshot by remember(existingReminders) { mutableStateOf(existingReminders) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showStudioDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                ActionIcon(ActionGlyph.Bell, Modifier.size(20.dp), color = Color.White)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = t("Varınca / Ayrılınca Odak Ekranı Testi"),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = t("Test kartı oluştur, Varmış veya Ayrılmış gibi seç ve yeni ekranı test et"),
                        color = Color(0xFFA5A5AF),
                        fontSize = 12.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = t("Test Et"),
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showStudioDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showStudioDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            var tabIndex by rememberSaveable { mutableIntStateOf(0) } // 0 = Yeni Kart Oluştur, 1 = Mevcut Kart Seç
            var onExitMode by rememberSaveable { mutableStateOf(false) } // false = Varınca, true = Ayrılınca
            var cardTitle by rememberSaveable { mutableStateOf("Akşam yemeği alışverişi") }
            var placeName by rememberSaveable { mutableStateOf("Migros Jet") }
            var draftItems by remember {
                mutableStateOf(
                    listOf(
                        "Avokado ve limon",
                        "Taze fesleğen",
                        "Parmesan peyniri",
                        "Tam buğday penne makarna"
                    )
                )
            }
            var newItemText by remember { mutableStateOf("") }
            val checklistCards = remember(localRemindersSnapshot) {
                localRemindersSnapshot.filter { !it.isDivider && it.isChecklist && it.checklistItems.isNotEmpty() }
            }
            var selectedExistingId by remember(checklistCards) {
                mutableStateOf(checklistCards.firstOrNull()?.id)
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "GELİŞTİRİCİ TEST ALANI",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Varınca / Ayrılınca Testi",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                .clickable { showStudioDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Trigger Mode Selector: Varmış Gibi vs Ayrılmış Gibi
                    Text(
                        text = "1. BİLDİRİM DURUMUNU SEÇ",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0D0D12))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val arrivalSelected = !onExitMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (arrivalSelected) Color.White else Color.Transparent)
                                .clickable {
                                    onExitMode = false
                                    if (placeName == "Ev") placeName = "Migros Jet"
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📍 Varmış Gibi (Varınca)",
                                color = if (arrivalSelected) Color.Black else Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val exitSelected = onExitMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (exitSelected) Color.White else Color.Transparent)
                                .clickable {
                                    onExitMode = true
                                    if (placeName == "Migros Jet") placeName = "Ev"
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🚶 Ayrılmış Gibi (Ayrılınca)",
                                color = if (exitSelected) Color.Black else Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Source Mode Selector: Create New vs Pick Existing
                    Text(
                        text = "2. KART KAYNAĞINI SEÇ",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0D0D12))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (tabIndex == 0) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { tabIndex = 0 }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "＋ Yeni Kart Oluştur",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (tabIndex == 1) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { tabIndex = 1 }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🗂 Mevcut Kartlardan Seç (${checklistCards.size})",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Scrollable Body
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Place Name Field
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0B0B0F))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "KONUM / MEKAN ADI",
                                color = Color.White.copy(alpha = 0.42f),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = placeName,
                                onValueChange = { placeName = it },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (tabIndex == 0) {
                            // Card Title Input
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0B0B0F))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "KART BAŞLIĞI",
                                    color = Color.White.copy(alpha = 0.42f),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = cardTitle,
                                    onValueChange = { cardTitle = it },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Checklist Items Builder
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0B0B0F))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "CHECKLIST MADDELERİ (TEK TEK GELECEK OLANLAR)",
                                    color = Color.White.copy(alpha = 0.42f),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                draftItems.forEachIndexed { index, itemText ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${index + 1}. $itemText",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (draftItems.size > 1) {
                                            Text(
                                                text = "✕",
                                                color = Color.White.copy(alpha = 0.45f),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable {
                                                        draftItems = draftItems.filterIndexed { i, _ -> i != index }
                                                    }
                                                    .padding(start = 10.dp)
                                            )
                                        }
                                    }
                                }

                                // Add new item row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (newItemText.isEmpty()) {
                                            Text(
                                                text = "Yeni checklist maddesi yaz...",
                                                color = Color.White.copy(alpha = 0.32f),
                                                fontSize = 13.5.sp
                                            )
                                        }
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = newItemText,
                                            onValueChange = { newItemText = it },
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                color = Color.White,
                                                fontSize = 14.sp
                                            ),
                                            cursorBrush = SolidColor(Color.White),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .clickable {
                                                val trimmed = newItemText.trim()
                                                if (trimmed.isNotEmpty()) {
                                                    draftItems = draftItems + trimmed
                                                    newItemText = ""
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "+ Ekle",
                                            color = Color.Black,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            // Tab 1: Pick from existing Checklist cards
                            if (checklistCards.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF0B0B0F))
                                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Henüz checklist içeren bir kart yok. Yan sekmeden hemen bir test kartı oluşturabilirsin.",
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                checklistCards.forEach { card ->
                                    val isSelected = selectedExistingId == card.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color(0xFF0B0B0F))
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.10f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                selectedExistingId = card.id
                                                card.locationTrigger?.placeName?.takeIf { it.isNotBlank() }?.let { placeName = it }
                                                    ?: card.location?.placeName?.takeIf { it.isNotBlank() }?.let { placeName = it }
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = card.text,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                text = "${card.checklistItems.size} checklist maddesi · ${card.checklistItems.count { !it.isCompleted }} bekliyor",
                                                color = Color.White.copy(alpha = 0.50f),
                                                fontSize = 12.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else Color.Transparent)
                                                .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Text("✓", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Bottom Launch Buttons
                    fun resolveOrCreateCardAndRun(sendNotificationOnly: Boolean) {
                        scope.launch {
                            val repo = com.cagri.real.data.ReminderRepository(context.applicationContext)
                            val targetCard: ReminderItem? = withContext(Dispatchers.IO) {
                                synchronized(com.cagri.real.data.ReminderRepository.mutationLock) {
                                    if (tabIndex == 0) {
                                        val finalItems = (if (newItemText.trim().isNotEmpty()) draftItems + newItemText.trim() else draftItems)
                                            .mapIndexed { idx, txt -> ChecklistItem(id = (idx + 1).toLong(), text = txt) }
                                        val created = repo.add(
                                            text = cardTitle.trim().ifBlank { "Test Alışveriş Listesi" },
                                            alertAt = null,
                                            isChecklist = true,
                                            checklistItems = finalItems,
                                            sendImmediateNotification = false,
                                            location = com.cagri.real.data.ReminderLocation(
                                                latitude = 41.0082,
                                                longitude = 28.9784,
                                                placeName = placeName.trim().ifBlank { "Migros Jet" }
                                            )
                                        )
                                        created
                                    } else {
                                        val id = selectedExistingId ?: return@synchronized null
                                        val all = repo.load()
                                        val found = all.firstOrNull { it.id == id } ?: return@synchronized null
                                        // If all items were already checked, uncheck them so the user can test the cascade fresh
                                        if (found.checklistItems.all { it.isCompleted }) {
                                            var refreshed = found
                                            for (itItem in found.checklistItems) {
                                                refreshed = repo.toggleChecklistItem(found.id, itItem.id).firstOrNull { r -> r.id == found.id } ?: refreshed
                                            }
                                            refreshed
                                        } else {
                                            found
                                        }
                                    }
                                }
                            }

                            if (targetCard == null) {
                                RealToast.show(context, "Lütfen önce bir kart seçin veya oluşturun")
                                return@launch
                            }

                            val allNow = withContext(Dispatchers.IO) { repo.load() }
                            localRemindersSnapshot = allNow

                            val triggerCtx = LocationTriggerNotificationContext(
                                reminderId = targetCard.id,
                                placeName = placeName.trim().ifBlank { "Migros Jet" },
                                onExit = onExitMode
                            )

                            if (sendNotificationOnly) {
                                val fakeTrigger = LocationTrigger(
                                    placeName = triggerCtx.placeName,
                                    latitude = 41.0082,
                                    longitude = 28.9784,
                                    radiusMeters = 150,
                                    onExit = triggerCtx.onExit
                                )
                                val posted = com.cagri.real.notifications.ReminderNotifications.show(
                                    context = context,
                                    reminder = targetCard,
                                    alerting = true,
                                    locationTriggerFired = fakeTrigger
                                )
                                if (posted) {
                                    RealToast.show(context, "Bildirim gönderildi! Üstten bildirime dokunarak test edebilirsin.")
                                } else {
                                    RealToast.show(context, "Bildirim izni kapalı; doğrudan ekran açılıyor.")
                                    activeFocusSession = targetCard.id to triggerCtx
                                }
                            } else {
                                activeFocusSession = targetCard.id to triggerCtx
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { resolveOrCreateCardAndRun(sendNotificationOnly = false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (onExitMode) "🚀 Ayrılmış Gibi Odak Ekranını Aç" else "🚀 Varmış Gibi Odak Ekranını Aç",
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                .clickable { resolveOrCreateCardAndRun(sendNotificationOnly = true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔔 Gerçek Bildirim Gönder (Bildirime Basıp Test Et)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Full-screen Focus Overlay when launched from Developer Studio
                val session = activeFocusSession
                if (session != null) {
                    val (cardId, triggerCtx) = session
                    val liveCard = localRemindersSnapshot.firstOrNull { it.id == cardId }
                    if (liveCard != null) {
                        LocationArrivalFocusScreen(
                            reminder = liveCard,
                            triggerContext = triggerCtx,
                            onToggleChecklistItem = { remId, itemId ->
                                scope.launch {
                                    val updatedList = withContext(Dispatchers.IO) {
                                        synchronized(com.cagri.real.data.ReminderRepository.mutationLock) {
                                            com.cagri.real.data.ReminderRepository(context.applicationContext)
                                                .toggleChecklistItem(remId, itemId)
                                        }
                                    }
                                    localRemindersSnapshot = updatedList
                                }
                            },
                            onOpenFullCard = {
                                activeFocusSession = null
                                showStudioDialog = false
                                onOpenCardDetail?.invoke(cardId)
                            },
                            onDismiss = {
                                activeFocusSession = null
                            }
                        )
                    }
                }
            }
        }
    }
}
